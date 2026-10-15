package ykws.android.maro.spatial.multipass

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.BBox
import java.util.concurrent.atomic.AtomicInteger

/**
 * **The outward warm-up walk'** own contracts — the seed's own order, growth by whole tiles, the two stops
 * D3 settled (the store's ceiling and the baked region's edges), and the one busy window a walk holds.
 *
 * The walk is driven with a synthetic derivation, so the growth arithmetic, the stops and the window are
 * pinned without a world, a lattice or a device.
 */
class FineTileWarmTest {

    /** A store whose names and ceiling a test drives — a listing and a fullness verdict are the whole API. */
    private class WarmStore(initial: List<TileKey> = emptyList()) : TileStore {
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

    /** One row a box stands for — a box's south edge, so the walk's order reads off the keys it built. */
    private fun rowOf(box: BBox): Int = ((box.latSouth - 43.0) * 1_000).toInt()

    /** The seed and the region of the arithmetic: a 0.02° box inside a 0.04° region, one tile = 0.01°. */
    private val seed = BBox(43.50, 43.52, 7.00, 7.02)
    private val region = BBox(43.49, 43.53, 6.99, 7.03)

    private fun area(region: BBox? = this.region, growthStepTiles: Int = 1) = WarmArea(
        seed = seed,
        region = region,
        growthStepTiles = growthStepTiles,
        tileSideLatDeg = 0.01,
        tileSideLonDeg = 0.01
    )

    /** A derivation that answers one tile a box, keyed and built off the box's own south edge. */
    private fun oneTileABox(built: MutableList<TileKey>, boxes: MutableList<BBox>): (BBox) -> List<FineTileRequest> =
        { box ->
            boxes += box
            val row = rowOf(box)
            val key = TileTestFixtures.baseKey(row = row)
            listOf(FineTileRequest(key, LatLng(box.latSouth, box.lonWest)) {
                built += key
                TileTestFixtures.tile(2, row = row)
            })
        }

    /** A derivation that answers three tiles a box — enough for the store's ceiling to end a pass part-way. */
    private fun threeTilesABox(built: MutableList<TileKey>, boxes: MutableList<BBox>): (BBox) -> List<FineTileRequest> =
        { box ->
            boxes += box
            val row = rowOf(box)
            (1..3).map { col ->
                val key = TileTestFixtures.baseKey(row = row, col = col)
                FineTileRequest(key, LatLng(box.latSouth, box.lonWest)) {
                    built += key
                    TileTestFixtures.tile(2, row = row, col = col)
                }
            }
        }

    @Test
    fun theWalkStartsAtTheSeedAndOpensOutwardUntilTheRegionStopsIt() = runBlocking {
        val map = FineTileMap(store = WarmStore())
        val built = mutableListOf<TileKey>()
        val boxes = mutableListOf<BBox>()
        val outcome = warmOutward(
            pass = FineTilePrewarm(map),
            area = area(),
            derive = oneTileABox(built, boxes),
            storedNames = emptySet(),
            orderFrom = LatLng(43.50, 7.00),
            maxTiles = 100,
            maxTimeMs = 10_000
        )

        assertEquals("the seed is walked first, then one ring out", 2, boxes.size)
        assertEquals("the seed's own box is the first walked", seed, boxes[0])
        assertTrue("the ring beyond it stays inside the region", boxInsideRegion(boxes[1], region))
        assertTrue(
            "and it is strictly wider than the seed",
            boxes[1].latSouth < seed.latSouth && boxes[1].lonEast > seed.lonEast
        )
        assertEquals("the seed's own tile first, the ring beyond it second", listOf(rowOf(seed), rowOf(region)), built.map { it.tileRow })
        assertEquals("the walk grew once before the region's edges stopped it", 1, outcome.rings)
        assertEquals(WarmStop.REGION, outcome.stop)
    }

    @Test
    fun aWarmSeedOpensTheAreaOutwardRatherThanEndingTheWalk() = runBlocking {
        val warmKey = TileTestFixtures.baseKey(row = rowOf(seed))
        val map = FineTileMap(store = WarmStore(initial = listOf(warmKey)))
        val built = mutableListOf<TileKey>()
        val boxes = mutableListOf<BBox>()
        val outcome = warmOutward(
            pass = FineTilePrewarm(map),
            area = area(),
            derive = oneTileABox(built, boxes),
            storedNames = map.storedNames(),
            orderFrom = LatLng(43.50, 7.00),
            maxTiles = 100,
            maxTimeMs = 10_000
        )

        assertEquals("the seed's tile was already warm, so only the ring beyond it is built", 1, built.size)
        assertEquals(listOf(rowOf(region)), built.map { it.tileRow })
        assertEquals("the warm tile is reported warm, not rebuilt", 1, outcome.warm)
    }

    @Test
    fun theWalkStopsWhereTheStoreAnswersItIsFull() = runBlocking {
        val map = FineTileMap(store = WarmStore())
        val built = mutableListOf<TileKey>()
        val boxes = mutableListOf<BBox>()
        var probes = 0
        val outcome = warmOutward(
            pass = FineTilePrewarm(map),
            area = area(),
            derive = threeTilesABox(built, boxes),
            storedNames = emptySet(),
            orderFrom = LatLng(43.50, 7.00),
            maxTiles = 100,
            maxTimeMs = 10_000,
            storeFullness = {
                probes++
                // The walk's own probe first, the seed ring's tiles next: room for one tile, then full.
                if (probes > 2) StoreFullness(bytesHeld = 1L, ceilingBytes = 1L, atCeiling = true) else null
            }
        )

        assertEquals("the one tile the store had room for lands, the ceiling then ends the walk", 1, outcome.built)
        assertEquals("the area is not opened out past a full store", 1, boxes.size)
        assertEquals(WarmStop.CEILING, outcome.stop)
    }

    @Test
    fun aWorldNamingNoRegionStillStopsTheWalk() = runBlocking {
        val map = FineTileMap(store = WarmStore())
        val built = mutableListOf<TileKey>()
        val boxes = mutableListOf<BBox>()
        val outcome = warmOutward(
            pass = FineTilePrewarm(map),
            area = area(region = null),
            derive = oneTileABox(built, boxes),
            storedNames = emptySet(),
            orderFrom = LatLng(43.50, 7.00),
            maxTiles = 4,
            maxTimeMs = 10_000
        )

        assertEquals("the tile budget is also the ring bound, so the walk cannot spin", 4, outcome.built)
        assertEquals(WarmStop.CAPS, outcome.stop)
    }

    @Test
    fun aSeedWithNoFineWaterIsNotGrownOutward() = runBlocking {
        val map = FineTileMap(store = WarmStore())
        val boxes = mutableListOf<BBox>()
        val outcome = warmOutward(
            pass = FineTilePrewarm(map),
            area = area(),
            derive = { box ->
                boxes += box
                emptyList()
            },
            storedNames = emptySet(),
            orderFrom = LatLng(43.50, 7.00),
            maxTiles = 100,
            maxTimeMs = 10_000
        )

        assertEquals("the seed alone is asked for", 1, boxes.size)
        assertEquals(WarmStop.NOTHING_TO_DO, outcome.stop)
    }

    /**
     * **A fully warm area builds nothing and shows no dot** — the assertion the walk was missing. It must
     * work out what a ring will actually *build* before it holds: the derivation here answers a tile the
     * store already names for every ring, so the tile is never built and no window may open. A walk that
     * held on the derivation's own answer alone lit the dot for water it could never prepare, against the
     * pass's own contract.
     */
    @Test
    fun aFullyWarmAreaOpensNoWindowAtAll() = runBlocking {
        val lines = mutableListOf<String>()
        FineTileCache.traceTo { lines += it }
        val warmKey = TileTestFixtures.baseKey(row = rowOf(seed))
        val map = FineTileMap(store = WarmStore(initial = listOf(warmKey)))
        val boxes = mutableListOf<BBox>()
        val outcome = warmOutward(
            pass = FineTilePrewarm(map),
            area = area(),
            derive = { box ->
                boxes += box
                listOf(
                    FineTileRequest(warmKey, LatLng(box.latSouth, box.lonWest)) {
                        throw AssertionError("a tile the store already names must never be built")
                    }
                )
            },
            storedNames = map.storedNames(),
            orderFrom = LatLng(43.50, 7.00),
            maxTiles = 100,
            maxTimeMs = 10_000
        )

        assertEquals("nothing was built", 0, outcome.built)
        assertEquals("the ring's tile is credited warm", 1, outcome.warm)
        assertTrue("the walk still opened the area outward", boxes.size > 1)
        assertEquals("and it ended on the region's own edges", WarmStop.REGION, outcome.stop)
        assertTrue("nothing was built, so no window opened: the dot stays dark", lines.isEmpty())
    }

    /**
     * **Two walks never build at once** (the review's defect 7). A recreated Activity restarts the host's
     * effect while the walk it replaced is only **cooperatively** cancelled, so without a slot the successor
     * runs beside it — the same water built twice, the window held twice. The holder's one walk slot makes
     * the second walk wait, so the peak of simultaneous builds is one and the first walk's tiles land first.
     */
    @Test
    fun twoWalksNeverBuildAtOnce() = runBlocking {
        val map = FineTileMap(store = WarmStore())
        val live = AtomicInteger(0)
        val peak = AtomicInteger(0)
        val builds = mutableListOf<String>()
        fun walk(tag: String, rowBase: Int) = launch {
            warmOutward(
                pass = FineTilePrewarm(map),
                area = area(),
                derive = { box ->
                    val row = rowBase + rowOf(box)
                    val key = TileTestFixtures.baseKey(row = row)
                    listOf(
                        FineTileRequest(key, LatLng(box.latSouth, box.lonWest)) {
                            val now = live.incrementAndGet()
                            if (now > peak.get()) peak.set(now)
                            // A suspension inside the build, so a second walk could provably be here at the
                            // same moment were the slot not holding it out.
                            delay(5)
                            builds += "$tag:$row"
                            live.decrementAndGet()
                            TileTestFixtures.tile(2, row = row)
                        }
                    )
                },
                storedNames = emptySet(),
                orderFrom = LatLng(43.50, 7.00),
                maxTiles = 2,
                maxTimeMs = 10_000
            )
        }
        val first = walk("a", 0)
        val second = walk("b", 1_000)
        first.join()
        second.join()

        assertEquals("no two builds overlapped", 1, peak.get())
        assertEquals("each walk built the two tiles of its own water", 4, builds.size)
        assertTrue("the second walk ran after the first, not beside it", builds.take(2).all { it.startsWith("a:") })
    }

    /**
     * **One window brackets the walk's tiles, however many rings it takes.** The pass marks each tile it
     * really builds and the grace merges them, so the dot stands for the walk rather than flickering at a
     * ring boundary. The virtual clock is what lets the grace be watched elapsing.
     */
    @Test
    fun theGraceMergesTheWalksRingsIntoOneWindow() = runTest {
        FineTileCache.reset(this)
        FineTileCache.install(TileStore.NoOp)
        val lines = mutableListOf<String>()
        FineTileCache.traceTo { lines += it }
        val map = FineTileCache.shared!!
        val built = mutableListOf<TileKey>()
        val boxes = mutableListOf<BBox>()
        val outcome = warmOutward(
            pass = FineTilePrewarm(map),
            area = area(),
            derive = oneTileABox(built, boxes),
            storedNames = emptySet(),
            orderFrom = LatLng(43.50, 7.00),
            maxTiles = 100,
            maxTimeMs = 10_000
        )

        assertEquals(2, outcome.built)
        assertEquals("two rings, one window: the grace merges them", listOf(FineTileActivity.BUSY_ON), lines)
        advanceTimeBy(FineTileCache.BUSY_GRACE_MS + 1)
        runCurrent()
        assertEquals(
            "the window is the walk: one busy on and one busy off, whatever the ring boundaries",
            listOf(FineTileActivity.BUSY_ON, FineTileActivity.BUSY_OFF),
            lines
        )
    }

    /**
     * **The window is lit only while the walk really builds.** A tile the walk builds raises it, and the
     * moment the walk settles at its gate — no tile in flight — the grace lets it fall, so a route armed
     * mid-walk reads as *waiting*, never as work. The window rises again at the next tile the gate lets
     * through. This is the behaviour the user narrowed the dot to: the walk's own building, nothing else.
     */
    @Test
    fun theWindowRisesForATileTheWalkBuildsAndFallsWhileItWaitsAtItsGate() = runTest {
        FineTileCache.reset(this)
        FineTileCache.install(TileStore.NoOp)
        val map = FineTileCache.shared!!
        val built = mutableListOf<TileKey>()
        val boxes = mutableListOf<BBox>()
        val busyAtBuild = mutableListOf<Boolean>()
        val gate = CompletableDeferred<Unit>()
        var asks = 0

        val walk = launch {
            warmOutward(
                pass = FineTilePrewarm(map),
                area = area(),
                derive = { box ->
                    boxes += box
                    val row = rowOf(box)
                    val key = TileTestFixtures.baseKey(row = row)
                    listOf(
                        FineTileRequest(key, LatLng(box.latSouth, box.lonWest)) {
                            busyAtBuild += FineTileCache.busy.value
                            built += key
                            TileTestFixtures.tile(2, row = row)
                        }
                    )
                },
                storedNames = emptySet(),
                orderFrom = LatLng(43.50, 7.00),
                maxTiles = 100,
                maxTimeMs = 10_000,
                awaitMayProceed = {
                    asks++
                    // The seed's own ring is let through; the second ring's ask waits at the gate.
                    if (asks <= 2) true else { gate.await(); true }
                }
            )
        }

        runCurrent()
        assertEquals("the walk built the seed's tile", 1, built.size)
        assertEquals("and the window was lit while it built", listOf(true), busyAtBuild)

        // The walk settles at the gate: nothing in flight, so the grace lets the window fall.
        advanceTimeBy(FineTileCache.BUSY_GRACE_MS + 1)
        runCurrent()
        assertFalse("waiting at the gate is not work: the dot is dark", FineTileCache.busy.value)

        gate.complete(Unit)
        advanceUntilIdle()
        walk.join()
        assertEquals("the walk resumed and built the ring beyond", 2, built.size)
        assertEquals("the window was lit again for the tile it built", listOf(true, true), busyAtBuild)
    }
}
