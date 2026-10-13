package ykws.android.maro.spatial.multipass

import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertNotSame
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
}
