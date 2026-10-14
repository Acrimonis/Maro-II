package ykws.android.maro.spatial.multipass

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.BBox

/**
 * **The pre-warm's own contracts — the gates, the two caps, own-ship ordering and the skip-when-warm.**
 *
 * The pass reaches the same map the arm reaches, so its tiles are the arm's tiles; a warm cache is skipped
 * before a window opens, a warm pass shows no dot, one busy window brackets a whole cold pass, and a route
 * armed mid-pass pauses it rather than stealing its tiles. Each is pinned without a device or a world.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class FineTilePrewarmTest {

    /** The store's own names, driven by the entry-name rule the pass also reads — a listing is the whole API. */
    private class MemoryStore(initial: List<TileKey> = emptyList()) : TileStore {
        private val names = linkedSetOf<String>().apply { initial.forEach { add(FineTileCodec.entryName(it)) } }
        override suspend fun load(key: TileKey): FineTile? = null
        override suspend fun save(key: TileKey, tile: FineTile) {
            names += FineTileCodec.entryName(key)
        }
        override suspend fun delete(key: TileKey) {
            names -= FineTileCodec.entryName(key)
        }
        override suspend fun list(): List<String> = names.toList()
    }

    @After
    fun clear() {
        FineTileCache.traceTo {}
        FineTileCache.reset()
    }

    private fun key(row: Int, col: Int) = TileTestFixtures.baseKey(row = row, col = col)

    /** A request whose build records the key it built, so the pass's order and skips read off one list. */
    private fun request(k: TileKey, center: LatLng, built: MutableList<TileKey>) =
        FineTileRequest(k, center) {
            built += k
            TileTestFixtures.tile(4, row = k.tileRow, col = k.tileCol)
        }

    @Test
    fun theGateRunsOnlyWhenIdleUnarmedAndOffBatterySaver() {
        assertTrue(prewarmGate(idle = true, routeArmed = false, batterySaver = false))
        assertFalse("a moving boat is no time to warm", prewarmGate(idle = false, routeArmed = false, batterySaver = false))
        assertFalse("a live arm owns the store", prewarmGate(idle = true, routeArmed = true, batterySaver = false))
        assertFalse("battery-saver outranks the warm", prewarmGate(idle = true, routeArmed = false, batterySaver = true))
    }

    @Test
    fun theBoxIsInsideTheRegionOnlyWhenItFits() {
        val box = BBox(43.513, 43.664, 7.013, 7.148)
        val region = BBox(43.30, 43.80, 6.70, 7.55)
        assertTrue(boxInsideRegion(box, region))
        assertFalse("a box south of the region is outside", boxInsideRegion(box, BBox(43.55, 43.80, 6.70, 7.55)))
        assertFalse("a box north of the region is outside", boxInsideRegion(box, BBox(43.30, 43.60, 6.70, 7.55)))
        assertFalse("an unloaded region answers no containment", boxInsideRegion(box, null))
    }

    @Test
    fun thePassBuildsTheMissingTilesAndSkipsTheStoredOnes() = runBlocking {
        val warmKey = key(1, 1)
        val coldKey = key(1, 2)
        val map = FineTileMap(store = MemoryStore(initial = listOf(warmKey)))
        val built = mutableListOf<TileKey>()
        val requests = listOf(
            request(warmKey, LatLng(43.55, 7.05), built),
            request(coldKey, LatLng(43.56, 7.06), built)
        )
        val outcome = FineTilePrewarm(map).run(
            requests = requests,
            storedNames = map.storedNames(),
            orderFrom = LatLng(43.55, 7.05),
            maxTiles = 10,
            maxTimeMs = 10_000
        )
        assertEquals("only the tile the store lacks is built", listOf(coldKey), built)
        assertEquals(1, outcome.built)
        assertEquals(1, outcome.warm)
        assertFalse(outcome.stoppedByCaps)
    }

    @Test
    fun anInMemoryWarmTileIsSkipped() = runBlocking {
        val map = FineTileMap(store = MemoryStore())
        val warmKey = key(1, 1)
        map.get(warmKey) { TileTestFixtures.tile(2) }
        val built = mutableListOf<TileKey>()
        val outcome = FineTilePrewarm(map).run(
            requests = listOf(request(warmKey, LatLng(43.55, 7.05), built)),
            storedNames = map.storedNames(),
            orderFrom = LatLng(43.55, 7.05),
            maxTiles = 10,
            maxTimeMs = 10_000
        )
        assertEquals(0, outcome.built)
        assertTrue("a tile the map already holds is not rebuilt", built.isEmpty())
    }

    @Test
    fun aFullyWarmCacheBuildsNothingAndShowsNoDot() = runBlocking {
        val k1 = key(1, 1)
        val k2 = key(1, 2)
        val map = FineTileMap(store = MemoryStore(initial = listOf(k1, k2)))
        val built = mutableListOf<TileKey>()
        val requests = listOf(
            request(k1, LatLng(43.55, 7.05), built),
            request(k2, LatLng(43.56, 7.06), built)
        )
        val outcome = FineTilePrewarm(map).run(
            requests = requests,
            storedNames = map.storedNames(),
            orderFrom = LatLng(43.55, 7.05),
            maxTiles = 10,
            maxTimeMs = 10_000
        )
        assertEquals(0, outcome.built)
        assertEquals(2, outcome.warm)
        assertTrue(built.isEmpty())
        assertFalse("a warm pass opens no window, so the dot stays dark", FineTileCache.busy.value)
    }

    @Test
    fun thePassBuildsOwnShipOutward() = runBlocking {
        val map = FineTileMap(store = MemoryStore())
        val nearKey = key(1, 1)
        val farKey = key(1, 9)
        val built = mutableListOf<TileKey>()
        // Handed far-first: the pass must reorder so the boat's own water is built first.
        val requests = listOf(
            request(farKey, LatLng(43.60, 7.10), built),
            request(nearKey, LatLng(43.51, 7.01), built)
        )
        FineTilePrewarm(map).run(
            requests = requests,
            storedNames = map.storedNames(),
            orderFrom = LatLng(43.51, 7.01),
            maxTiles = 10,
            maxTimeMs = 10_000
        )
        assertEquals(listOf(nearKey, farKey), built)
    }

    @Test
    fun theTileBudgetEndsThePass() = runBlocking {
        val map = FineTileMap(store = MemoryStore())
        val built = mutableListOf<TileKey>()
        val requests = (1..5).map { request(key(1, it), LatLng(43.50, 7.0 + it * 0.001), built) }
        val outcome = FineTilePrewarm(map).run(
            requests = requests,
            storedNames = map.storedNames(),
            orderFrom = LatLng(43.50, 7.0),
            maxTiles = 2,
            maxTimeMs = 10_000
        )
        assertEquals("the tile budget stops the pass at two", 2, outcome.built)
        assertTrue(outcome.stoppedByCaps)
    }

    @Test
    fun theTimeBudgetEndsThePass() = runBlocking {
        val map = FineTileMap(store = MemoryStore())
        var clock = 0L
        val built = mutableListOf<TileKey>()
        val requests = (1..5).map { i ->
            FineTileRequest(key(1, i), LatLng(43.50, 7.0 + i * 0.001)) {
                built += key(1, i)
                clock += 1_000
                TileTestFixtures.tile(2)
            }
        }
        val outcome = FineTilePrewarm(map).run(
            requests = requests,
            storedNames = map.storedNames(),
            orderFrom = LatLng(43.50, 7.0),
            maxTiles = 100,
            maxTimeMs = 2_500,
            nowMs = { clock }
        )
        // start 0; tile1 0<2500 → 1000; tile2 1000<2500 → 2000; tile3 2000<2500 → 3000; tile4 3000≥2500 stops.
        assertEquals("the time budget stops the pass at three", 3, outcome.built)
        assertTrue(outcome.stoppedByCaps)
    }

    @Test
    fun theWindowIsHeldForTheWholePass() = runBlocking {
        val lines = mutableListOf<String>()
        FineTileCache.traceTo { lines += it }
        val map = FineTileMap(store = MemoryStore())
        val built = mutableListOf<TileKey>()
        val requests = (1..3).map { request(key(1, it), LatLng(43.50, 7.0 + it * 0.001), built) }
        val outcome = FineTilePrewarm(map).run(
            requests = requests,
            storedNames = map.storedNames(),
            orderFrom = LatLng(43.50, 7.0),
            maxTiles = 10,
            maxTimeMs = 10_000
        )
        assertEquals(3, outcome.built)
        assertEquals(
            "one busy on and one busy off bracket the whole pass, not a pair per tile",
            listOf(FineTileActivity.BUSY_ON, FineTileActivity.BUSY_OFF),
            lines
        )
    }

    @Test
    fun thePassWaitsAtTheGateBeforeEachTile() = runTest {
        val map = FineTileMap(store = MemoryStore())
        val built = mutableListOf<TileKey>()
        val requests = (1..3).map { request(key(1, it), LatLng(43.50, 7.0 + it * 0.001), built) }
        var gateOpen = false
        val job = launch {
            FineTilePrewarm(map).run(
                requests = requests,
                storedNames = map.storedNames(),
                orderFrom = LatLng(43.50, 7.0),
                maxTiles = 10,
                maxTimeMs = 10_000,
                awaitMayProceed = { while (!gateOpen) delay(1) }
            )
        }
        runCurrent()
        assertTrue("the pass yields at the gate before building, a route armed", built.isEmpty())
        gateOpen = true
        job.join()
        assertEquals("it resumes and fills the holes once free", 3, built.size)
    }
}
