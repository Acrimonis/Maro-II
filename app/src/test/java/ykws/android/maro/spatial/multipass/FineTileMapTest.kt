package ykws.android.maro.spatial.multipass

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.LatLng
import java.util.concurrent.atomic.AtomicInteger

/**
 * **The tile map's two contracts — single-flight and an in-flight protection an eviction honours.**
 *
 * A tile is built once however many callers ask for it, and the byte ceiling's LRU eviction never drops a
 * tile a builder or an awaiting caller still holds. Both are structural, so they are pinned without a
 * device, and the [FineTileMap.hitRate] reading counts only requests served from a completed cache entry.
 */
class FineTileMapTest {

    private fun key(row: Int) = TileKey(
        anchorLatSouth = 43.0, anchorLonWest = 6.70, referenceLat = 43.5,
        fineCellM = 20.0, tileRow = row, tileCol = 0, tileCells = FINE_TILE_CELLS,
        capLatNorth = 43.6, depthGenerationStamp = 1L, coastlineGenerationStamp = 2L,
        emodnetCutoffM = 2.0f, obstacleMarginM = 50.0, gateMinDepthM = 3.0, gateMarginM = 20.0,
        depthGateEnabled = true, bandWidthM = 300.0, bandLimitKn = 5.0, bandOutsideMarginM = 50.0,
        bandExtraM = 25.0, bandEnabled = true, pricesDepthBand = true,
        zoneOutsideMarginM = 100.0, zonesEnabled = true,
        coastBandsM = listOf(0.0..100.0), zoneRimM = 100.0, depthCollarM = 100.0, depthStepM = 20.0,
        paceKn = 25.0, excludedZoneIdSet = emptySet(), zoneGenerationStamp = 3L,
        zoneRings = listOf(ZoneRing(listOf(LatLng(43.51, 7.02), LatLng(43.51, 7.03)), emptyList(), 4.0))
    )

    /** A tile of [members] dummies — its byte figure is `members * 53`, what the ceiling counts. */
    private fun tile(members: Int) = FineTile(
        tileRow = 0, tileCol = 0, tileCells = FINE_TILE_CELLS, rows = 1, cols = members,
        latSouth = 43.0, lonWest = 6.7, cellSizeDegLat = 1.8e-4, cellSizeDegLon = 2.5e-4,
        cellM = 20.0, baseCostSec = 1.0,
        state = ByteArray(members), sourceCostSec = DoubleArray(members),
        zoneLimitKn = DoubleArray(members), collarLimitKn = DoubleArray(members),
        bandLimitKn = DoubleArray(members), bandCollarLimitKn = DoubleArray(members),
        depthPriceCoef = DoubleArray(members), localIndex = IntArray(members) { it }
    )

    @Test
    fun concurrentRequestsForOneTileBuildOnce() = runBlocking {
        val map = FineTileMap()
        val key = key(0)
        val builds = AtomicInteger(0)
        val open = CompletableDeferred<Unit>()
        val jobs = (1..8).map {
            async(Dispatchers.Default) {
                map.get(key) {
                    builds.incrementAndGet()
                    open.await()
                    tile(1000)
                }
            }
        }
        // Let every caller arrive on the one key while the first build is held open.
        delay(100)
        open.complete(Unit)
        val tiles = jobs.awaitAll()
        assertEquals("the single-flight shares one build across every concurrent caller", 1, builds.get())
        assertTrue("every caller holds the one tile", tiles.all { it === tiles[0] })
        // D49: a caller that waits on an in-flight build is not a reuse, so only a completed entry is a hit.
        assertEquals("the builder and its seven awaiting callers are all misses", 8L, map.misses)
        assertEquals("none was served from a completed cache entry", 0L, map.hits)
        map.get(key) { builds.incrementAndGet(); tile(1000) }
        assertEquals("a completed entry is a hit", 1L, map.hits)
        assertEquals("and it builds nothing new", 1, builds.get())
        assertEquals("the hit rate counts served-from-cache only", 1.0 / 9.0, map.hitRate, 1e-12)
    }

    @Test
    fun anEvictionNeverDropsATileAnInFlightCallerHolds() = runBlocking {
        val bytesPerTile = 1000L * 53L
        val map = FineTileMap(maxBytes = 3L * bytesPerTile)
        val builds = AtomicInteger(0)
        val inFlightKey = key(0)
        val held = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        val builder = async(Dispatchers.Default) {
            map.get(inFlightKey) {
                builds.incrementAndGet()
                started.complete(Unit)
                held.await()
                tile(1000)
            }
        }
        // Wait until the build is provably **in flight** — a latch set inside the build lambda, not a
        // `delay` margin (D55) — then blow the byte ceiling with completed tiles: the in-flight entry is
        // neither completed nor free of a holder, so the LRU must pass over it.
        started.await()
        for (row in 1..10) map.get(key(row)) { builds.incrementAndGet(); tile(1000) }
        held.complete(Unit)
        builder.await()

        // The in-flight tile was never a victim, so asking again serves it from the cache and builds nothing.
        val before = builds.get()
        map.get(inFlightKey) { builds.incrementAndGet(); tile(1000) }
        assertEquals("the tile a live caller held was never evicted, so asking again builds nothing", before, builds.get())
        assertTrue("the ceiling holds", map.heldBytes <= map.size.toLong() * bytesPerTile)
        assertTrue("a hit rate is reported", map.hitRate > 0.0)
    }
}
