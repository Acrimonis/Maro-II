package ykws.android.maro.spatial.multipass

import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **The process-level holder — installed once, shared whole, and cleanly absent.**
 *
 * A builder prefers the installed map so an engine switch keeps the cache and the store has one reader and
 * writer; until the app edge installs one the holder is `null`, so a storeless JVM test keeps its own map
 * and two fixtures can never share a tile.
 */
class FineTileCacheTest {

    @After
    fun clear() {
        FineTileCache.reset()
    }

    @Test
    fun theHolderIsAbsentUntilInstalledAndClearedByReset() {
        assertNull("no map is shared before the app edge installs a store", FineTileCache.shared)
        FineTileCache.install(TileStore.NoOp)
        val installed = FineTileCache.shared
        assertNotNull(installed)
        assertSame("the holder answers the one installed map", installed, FineTileCache.shared)
        FineTileCache.reset()
        assertNull("a reset returns a builder to its own map", FineTileCache.shared)
    }

    @Test
    fun aSecondInstallReplacesTheMapWhole() {
        FineTileCache.install(TileStore.NoOp)
        val first = FineTileCache.shared
        FineTileCache.install(TileStore.NoOp)
        assertNotSame("a fresh install drops the in-memory tiles", first, FineTileCache.shared)
    }

    /**
     * **The busy window is the holder's own, exposed for the app edge to collect.** It is idle before the
     * warm-up builds anything, and an install leaves it so — nothing has marked a tile yet. This is the
     * signal the routing square's dot reads instead of the zone-load flag, and only the warm-up feeds it.
     */
    @Test
    fun theBusyWindowIsExposedAndStartsIdle() {
        FineTileCache.reset()
        assertFalse("nothing is running before an install", FineTileCache.busy.value)
        FineTileCache.install(TileStore.NoOp)
        assertFalse("an install leaves the window idle until work reaches the store", FineTileCache.busy.value)
    }

    /**
     * **An install never zeroes a live walk's building** (the review's defect 7). The app edge installs the
     * store from `MainActivity.onCreate`, so a recreation lands beside a walk that is only **cooperatively**
     * cancelled: the window must stay standing for the tile that walk is still building rather than fall
     * while it is in flight. Only the walk's own `end` closes it, a grace later.
     */
    @Test
    fun anInstallLeavesALiveWalksBuildingStanding() = runTest {
        FineTileCache.reset(this)
        FineTileCache.install(TileStore.NoOp)
        FineTileCache.beginWarmTile()
        assertTrue("the tile the walk is building keeps the window up", FineTileCache.busy.value)

        FineTileCache.install(TileStore.NoOp)

        assertTrue("the install did not zero the tile in flight under the walk", FineTileCache.busy.value)
        FineTileCache.endWarmTile()
        advanceTimeBy(FineTileCache.BUSY_GRACE_MS + 1)
        runCurrent()
        assertFalse("the walk's own end is what closes the window, a grace later", FineTileCache.busy.value)
    }

    /** A store that records every protection set it is told, and answers nothing else. */
    private class RecordingStore : TileStore {
        val protectedSets = mutableListOf<Set<String>>()
        override suspend fun load(key: TileKey): FineTile? = null
        override suspend fun save(key: TileKey, tile: FineTile) {}
        override suspend fun delete(key: TileKey) {}
        override suspend fun list(): List<String> = emptyList()
        override fun protect(names: Set<String>) {
            protectedSets += names
        }

        override suspend fun fullness(): StoreFullness? = null
    }

    /**
     * **A re-install re-applies the remembered home area** (D7). The app edge installs a fresh store in
     * `MainActivity.onCreate`, so protection kept only in the store would be stripped by a recreation;
     * the holder keeps the last set and tells the new store at once.
     */
    @Test
    fun reInstallingReappliesTheRememberedSet() {
        FineTileCache.reset()
        val first = RecordingStore()
        FineTileCache.install(first)
        val names = setOf("home-a", "home-b")
        FineTileCache.protect(names)
        assertEquals("the installed store is told at once", listOf(names), first.protectedSets)

        val second = RecordingStore()
        FineTileCache.install(second)
        assertEquals(
            "and a recreation's store is told the remembered set",
            listOf(names),
            second.protectedSets
        )
    }
}
