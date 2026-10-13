package ykws.android.maro.spatial.multipass

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * **The store's contract inside [`FineTileMap`] — a hit is served, a miss falls back to the build.**
 *
 * The map's single-flight, ceiling and LRU are unmoved; the store is only consulted on a **miss**, before
 * the build, and a built tile is written back. Every store failure degrades to a build — the one property
 * that keeps a broken or absent store from ever failing a lookup.
 */
class FineTileStoreFallbackTest {

    private val key = TileTestFixtures.baseKey()

    /** A hand-driven store: an optional pre-stored tile, and switches to make a call throw. */
    private class RecordingStore(
        var stored: FineTile? = null,
        private val failLoad: Boolean = false,
        private val failSave: Boolean = false
    ) : TileStore {
        var saved: FineTile? = null
        var savedKey: TileKey? = null

        override suspend fun load(key: TileKey): FineTile? {
            if (failLoad) throw IllegalStateException("load failed")
            return stored
        }

        override suspend fun save(key: TileKey, tile: FineTile) {
            if (failSave) throw IllegalStateException("save failed")
            saved = tile
            savedKey = key
        }

        override suspend fun delete(key: TileKey) = Unit

        override suspend fun list(): List<String> = emptyList()
    }

    @Test
    fun aStoredTileIsServedWithoutBuilding() = runBlocking {
        val store = RecordingStore(stored = TileTestFixtures.tile(9))
        val map = FineTileMap(store = store)
        var builds = 0
        val served = map.get(key) { builds++; TileTestFixtures.tile(3) }
        assertEquals("a stored tile must skip the build entirely", 0, builds)
        assertEquals(9, served.memberCount)
        assertNull("a store hit is not written back", store.saved)
    }

    /**
     * **D10 — a store-served lookup counts as a hit.** The builder's fresh slot is counted a miss up
     * front, but when the [TileStore] serves the tile no build ran, so the request is a reuse, not a
     * computation, and the counters must say so.
     */
    @Test
    fun aStoreServedLookupCountsAsAHit() = runBlocking {
        val map = FineTileMap(store = RecordingStore(stored = TileTestFixtures.tile(9)))
        map.get(key) { TileTestFixtures.tile(3) }
        assertEquals("a store-served lookup is a hit", 1L, map.hits)
        assertEquals("and it is not also counted a miss", 0L, map.misses)
        assertEquals("so the hit rate reads one", 1.0, map.hitRate, 1e-12)
    }

    @Test
    fun aMissBuildsAndWritesBack() = runBlocking {
        val store = RecordingStore()
        val map = FineTileMap(store = store)
        var builds = 0
        map.get(key) { builds++; TileTestFixtures.tile(3) }
        assertEquals(1, builds)
        assertEquals(3, store.saved?.memberCount)
        assertEquals("the built tile is stored under the key that named it", key, store.savedKey)
    }

    @Test
    fun aThrowingLoadFallsBackToBuilding() = runBlocking {
        val store = RecordingStore(failLoad = true)
        val map = FineTileMap(store = store)
        var builds = 0
        map.get(key) { builds++; TileTestFixtures.tile(3) }
        assertEquals("a store that cannot be read degrades to the build", 1, builds)
    }

    @Test
    fun aFailingSaveStillServesTheBuiltTile() = runBlocking {
        val store = RecordingStore(failSave = true)
        val map = FineTileMap(store = store)
        val served = map.get(key) { TileTestFixtures.tile(4) }
        assertEquals("a store that cannot write must not fail the lookup", 4, served.memberCount)
    }

    @Test
    fun aStoredTileAndAFreshlyBuiltOneAreFieldIdentical() = runBlocking {
        val built = TileTestFixtures.tile(17)
        val stored = FineTileCodec.decode(key, FineTileCodec.encode(key, built))!!
        val map = FineTileMap(store = RecordingStore(stored = stored))
        var builds = 0
        val served = map.get(key) { builds++; TileTestFixtures.tile(99) }
        assertEquals("the store served the built tile, so nothing was rebuilt", 0, builds)
        assertArrayEquals(built.state, served.state)
        assertArrayEquals(built.sourceCostFactor, served.sourceCostFactor, 0.0)
        assertArrayEquals(built.zoneLimitKn, served.zoneLimitKn, 0.0)
        assertArrayEquals(built.depthPriceCoef, served.depthPriceCoef, 0.0)
        assertArrayEquals(built.localIndex, served.localIndex)
        assertEquals(built.byteSize, served.byteSize)
    }
}
