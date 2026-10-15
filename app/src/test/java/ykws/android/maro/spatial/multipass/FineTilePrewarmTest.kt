package ykws.android.maro.spatial.multipass

import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.BBox
import java.util.concurrent.Executors

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
        override suspend fun fullness(): StoreFullness? = null
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

    /**
     * **The gate's truth table** (D4, D6): no route armed, off battery-saver, and no competing heavy job.
     * The idle gate is deliberately absent — the pass starts with the app, so a moving boat no longer keeps
     * the water cold.
     */
    @Test
    fun theGateRunsOnlyUnarmedOffBatterySaverAndWithNoHeavyJob() {
        assertTrue(prewarmGate(routeArmed = false, batterySaver = false, heavyJob = false))
        assertFalse("a live arm owns the store", prewarmGate(routeArmed = true, batterySaver = false, heavyJob = false))
        assertFalse("battery-saver outranks the warm", prewarmGate(routeArmed = false, batterySaver = true, heavyJob = false))
        assertFalse(
            "a competing foreground job outranks the warm",
            prewarmGate(routeArmed = false, batterySaver = false, heavyJob = true)
        )
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

    /**
     * **One window brackets the pass's tiles, however many there are.** The pass marks each tile it really
     * builds and the grace merges them: a cold pass of three tiles opens one `busy on` and closes one
     * `busy off`, never a pair per tile. The virtual clock is what lets the grace be watched elapsing.
     */
    @Test
    fun theGraceMergesThePassesTilesIntoOneWindow() = runTest {
        FineTileCache.reset(this)
        FineTileCache.install(TileStore.NoOp)
        val lines = mutableListOf<String>()
        FineTileCache.traceTo { lines += it }
        val map = FineTileCache.shared!!
        val built = mutableListOf<TileKey>()
        val requests = (1..3).map { request(key(1, it), LatLng(43.50, 7.0 + it * 0.001), built) }
        val outcome = FineTilePrewarm(map).run(
            requests = requests,
            storedNames = emptySet(),
            orderFrom = LatLng(43.50, 7.0),
            maxTiles = 10,
            maxTimeMs = 10_000
        )
        assertEquals(3, outcome.built)
        assertEquals("three tiles, one window: the grace merges them", listOf(FineTileActivity.BUSY_ON), lines)

        advanceTimeBy(FineTileCache.BUSY_GRACE_MS + 1)
        runCurrent()
        assertEquals(
            "and one busy off a grace after the last tile — never a pair per tile",
            listOf(FineTileActivity.BUSY_ON, FineTileActivity.BUSY_OFF),
            lines
        )
    }

    /**
     * **The store's own ceiling ends the pass** (D3, step 3): the fullness probe is read before every tile,
     * and a store that answers it is full stops the pass where it stands — building on would only evict the
     * water the pass has just prepared.
     */
    @Test
    fun theStoresCeilingEndsThePass() = runBlocking {
        val map = FineTileMap(store = MemoryStore())
        val built = mutableListOf<TileKey>()
        val requests = (1..5).map { request(key(1, it), LatLng(43.50, 7.0 + it * 0.001), built) }
        var probes = 0
        val outcome = FineTilePrewarm(map).run(
            requests = requests,
            storedNames = map.storedNames(),
            orderFrom = LatLng(43.50, 7.0),
            maxTiles = 100,
            maxTimeMs = 10_000,
            storeFullness = {
                probes++
                if (probes >= 3) StoreFullness(bytesHeld = 1L, ceilingBytes = 1L, atCeiling = true) else null
            }
        )
        assertEquals("the two tiles the store had room for are built, the third is not", 2, outcome.built)
        assertTrue(outcome.stoppedByCapacity)
        assertFalse("the ceiling, not a cap, ended it", outcome.stoppedByCaps)
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
                awaitMayProceed = {
                    while (!gateOpen) delay(1)
                    true
                }
            )
        }
        runCurrent()
        assertTrue("the pass yields at the gate before building, a route armed", built.isEmpty())
        gateOpen = true
        job.join()
        assertEquals("it resumes and fills the holes once free", 3, built.size)
    }

    /**
     * **A shut gate ends the pass, and opens no window on the way.** The seam is asked before every tile —
     * and before the window opens — so battery-saver switched on mid-walk stands the pass down rather than
     * letting it run to its cap, and a pass stopped before its first tile shows no dot at all.
     */
    @Test
    fun aPassToldToStandDownStopsWithoutBuildingOrOpeningTheWindow() = runBlocking {
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
            maxTimeMs = 10_000,
            awaitMayProceed = { false }
        )

        assertEquals("the shut gate stops the pass before its first tile", 0, outcome.built)
        assertTrue("the gate, not a cap, ended it", outcome.stoppedByGate)
        assertFalse(outcome.stoppedByCaps)
        assertTrue("nothing was built", built.isEmpty())
        assertTrue("a pass that built nothing opens no window: the dot stays dark", lines.isEmpty())
    }

    /**
     * **No build runs on a thread of the fine layer's own choosing.** The pass and the walk add no dispatcher
     * hop of their own — every build runs on whichever dispatcher its caller handed it — which is what makes
     * the app edge's one hop the whole of "the fine layer is never rasterised on the screen's thread". The
     * previous pin read the host's source text, so a rename of the hop passed it; this one asks the running
     * code, and a hidden `withContext` inside the layer would answer a worker of its own and fail.
     */
    @Test
    fun everyBuildRunsOnTheDispatcherItsCallerHandedIt() = runBlocking {
        val executor = Executors.newSingleThreadExecutor { task -> Thread(task, WARM_PROBE_THREAD) }
        try {
            val threads = mutableListOf<String>()
            val map = FineTileMap(store = MemoryStore())
            withContext(executor.asCoroutineDispatcher()) {
                FineTilePrewarm(map).run(
                    requests = (1..2).map { i ->
                        FineTileRequest(key(1, i), LatLng(43.50, 7.0 + i * 0.001)) {
                            threads += Thread.currentThread().name
                            TileTestFixtures.tile(2, row = 1, col = i)
                        }
                    },
                    storedNames = map.storedNames(),
                    orderFrom = LatLng(43.50, 7.0),
                    maxTiles = 10,
                    maxTimeMs = 10_000
                )
            }

            assertEquals("both tiles were built", 2, threads.size)
            // The dispatcher appends the coroutine's own id to the thread's name, so the worker is matched
            // by the name it was created with — a hop inside the layer would answer another worker entirely.
            assertTrue(
                "every build ran on the caller's own worker ($threads)",
                threads.all { it.startsWith(WARM_PROBE_THREAD) }
            )
        } finally {
            executor.shutdown()
        }
    }
}

/** The name the probe's single worker carries, so a build's own thread is readable from the assertion. */
private const val WARM_PROBE_THREAD = "warm-probe"
