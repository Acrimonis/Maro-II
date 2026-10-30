package ykws.android.maro.spatial.multipass

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.spatial.SpatialOperations
import ykws.android.maro.spatial.Units
import ykws.android.maro.spatial.multipass.GateTestFixtures.at
import kotlin.math.hypot

/**
 * **The any-angle walk — the Lazy Theta\* the Evolutive + row runs**
 * (`261029_FEAT_PLN_Route_any-angle-search.md` §4.2, §13): open water is one straight line; an island is rounded
 * on tangent cells with no vertex standing on open water; the reach cap bounds every chord; a rock the straight
 * would graze is kept off by the gate; a pair is one chain from either end; and a walk handed no rule is today's.
 */
class AnyAngleWalkTest {

    private val paceKn = 12.0
    private val paceMps = Units.knotsToMps(paceKn)
    private val rows = 41
    private val cols = 61

    private fun newGrid() = MultipassGrid(
        GateTestFixtures.LAT0, GateTestFixtures.LON0,
        100.0 / GateTestFixtures.M_PER_DEG_LAT, 100.0 / GateTestFixtures.M_PER_DEG_LON,
        rows, cols, 100.0, baseCostSec(100.0, paceKn)
    )

    private val deepGate =
        GateTestFixtures.gateOver(GateTestFixtures.raster(rows, cols, 100.0) { _, _ -> 20.0 }, hardM = 10.0)

    private fun islandWalk(islandRows: IntRange, islandCols: IntRange): WalkWindows {
        val grid = newGrid()
        for (r in islandRows) for (c in islandCols) grid.markLand(r, c)
        return WalkWindows.of(grid)
    }

    private fun WalkWindows.point(cell: CellIndex): LatLng = center(cell.layer, cell.row, cell.col)

    private suspend fun walk(
        walk: WalkWindows, from: CellIndex, to: CellIndex, reach: Int = 0, gate: GateRule? = deepGate
    ): SearchOutcome = MultipassSearch.searchWalk(
        walk, from, to, paceMps,
        stepGuard = gate?.let { StepGuard(it, walk.point(from), walk.point(to)) },
        anyAngle = AnyAngleRule(reach)
    )

    private fun cells(a: CellIndex, b: CellIndex): Double =
        hypot((a.row - b.row).toDouble(), (a.col - b.col).toDouble())

    @Test
    fun openWaterIsOneStraightLine() = runTest {
        val walk = WalkWindows.of(newGrid())
        val from = CellIndex(20, 5)
        val to = CellIndex(23, 55)
        val outcome = walk(walk, from, to)
        assertEquals("the chain is the two ends", listOf(from, to), outcome.path)
        val straight = SpatialOperations.haversine(walk.point(from), walk.point(to)) / paceMps
        assertEquals("priced as the straight line at the pace", straight, outcome.costSec, straight * 0.01)
        assertTrue("one lazy pop, the aim's", outcome.lazyPops >= 1)
        assertEquals("no fallback", 0, outcome.fallbacks)
    }

    @Test
    fun anIslandIsRoundedOnTangentCellsAndNoVertexStandsInOpenWater() = runTest {
        val island = islandWalk(18..22, 27..33)
        val from = CellIndex(20, 5)
        val to = CellIndex(20, 55)
        val outcome = walk(island, from, to)
        val path = outcome.path
        assertNotNull(path)
        assertTrue("a short chain, not a staircase: ${path!!.size}", path.size in 3..5)
        val grid = island.windows[0].grid
        for (vertex in path.subList(1, path.size - 1)) {
            val touchesLand =
                (-1..1).any { dr -> (-1..1).any { dc -> !grid.passable(vertex.row + dr, vertex.col + dc) } }
            assertTrue("vertex $vertex is a tangent cell against the island", touchesLand)
        }
        val plain = MultipassSearch.searchWalk(
            island, from, to, paceMps, stepGuard = StepGuard(deepGate, island.point(from), island.point(to))
        )
        assertTrue("no dearer than the grid's own staircase", outcome.costSec <= plain.costSec + 1e-6)
        assertTrue("and cheaper, the taut line cutting the staircase's corners", outcome.costSec < plain.costSec)
    }

    @Test
    fun theReachCapBoundsEveryChord() = runTest {
        val walk = WalkWindows.of(newGrid())
        val from = CellIndex(20, 5)
        val to = CellIndex(23, 55)
        val outcome = walk(walk, from, to, reach = 8)
        val path = outcome.path
        assertNotNull(path)
        assertTrue("several anchors over fifty cells: ${path!!.size}", path.size >= 6)
        for ((a, b) in path.zipWithNext()) assertTrue("$a -> $b within 8 cells", cells(a, b) <= 8.0 + 1e-9)
        assertTrue("the reach reading reads the cap", outcome.reachMaxCells <= 8.0 + 1e-9)
        val straight = SpatialOperations.haversine(walk.point(from), walk.point(to)) / paceMps
        assertTrue("and the chain is within a cell's quantum of the straight line", outcome.costSec < straight * 1.02)
    }

    /** The shelf of [GateEndStepTest] with a rock the straight between the ends would pass 19 m off. */
    @Test
    fun aRockTheStraightWouldGrazeIsKeptOffByTheGate() = runTest {
        val raster = GateTestFixtures.raster(200, 220, 2.0) { n, e ->
            if (n < 100.0 || hypot(n - 227.0, e - 200.0) < 3.0) 2.0 else 10.0
        }
        val gate = GateTestFixtures.gateOver(raster, hardM = 20.0)
        val world = GateTestFixtures.RasterWorld(raster)
        val box = GateTestFixtures.box(60.0, 340.0, 0.0, 420.0)
        val grid = rasterize(
            box, 20.0, 25.0, 0.0, emptyList(), emptyList(), box.latNorth,
            field = GateTestFixtures.gateField(world), gate = gate
        )
        val start = at(250.0, 30.0)
        val aim = at(250.0, 390.0)
        val outcome = MultipassSearch.searchWalk(
            WalkWindows.of(grid), grid.cellOf(start.latitude, start.longitude),
            grid.cellOf(aim.latitude, aim.longitude), Units.knotsToMps(25.0), stepGuard = StepGuard(gate, start, aim), anyAngle = AnyAngleRule(0)
        )
        val path = outcome.path
        assertNotNull(path)
        assertTrue("the chain bends round the rock: ${path!!.size}", path.size >= 3)
        val drawn = path.map { grid.center(it.row, it.col) }
        for ((a, b) in drawn.zipWithNext()) {
            val clearOfEnds = SpatialOperations.haversine(a, start) > 25.0 && SpatialOperations.haversine(b, aim) > 25.0
            if (clearOfEnds) assertTrue("the leg keeps the wall", gate.segmentEdgeM(a, b, 100.0) >= 20.0 - 1e-6)
        }
    }

    @Test
    fun aPairIsOneChainFromEitherEnd() = runTest {
        for (half in listOf(1, 2, 4)) {
            val island = islandWalk(20 - half..20 + half, 27..33)
            val from = CellIndex(20, 5)
            val to = CellIndex(20, 55)
            suspend fun canonical(a: CellIndex, b: CellIndex): SearchOutcome =
                RoutePassRunner().searchFromCanonicalEnd(
                    island, a, b, island.point(a), island.point(b), paceMps,
                    zonePriceSec = { _, _, _, _ -> 0.0 }, berthK = 0.0, berth = null, gate = deepGate,
                    anyAngle = AnyAngleRule(0)
                )
            val there = canonical(from, to)
            val back = canonical(to, from)
            assertEquals("half $half: one chain", there.path, back.path?.asReversed())
            assertEquals(there.costSec, back.costSec, 1e-9)
        }
    }

    @Test
    fun theSwitchOffIsTodaysWalk() = runTest {
        val island = islandWalk(18..22, 27..33)
        val from = CellIndex(20, 5)
        val to = CellIndex(20, 55)
        val guard = StepGuard(deepGate, island.point(from), island.point(to))
        val off = MultipassSearch.searchWalk(island, from, to, paceMps, stepGuard = guard, anyAngle = null)
        val plain = MultipassSearch.searchWalk(island, from, to, paceMps, stepGuard = guard)
        assertEquals(plain.path, off.path)
        assertEquals(plain.costSec, off.costSec, 0.0)
        assertEquals(false, off.anyAngle)
        assertTrue("a staircase, many cells", off.path!!.size > 40)
    }

    @Test
    fun theReadingsAreSummedOverTwoWalks() {
        val a = SearchOutcome(null, 3, 10, false, lazyPops = 2, fallbacks = 1, reachSumCells = 5.0, reachMaxCells = 4.0)
        val b = SearchOutcome(null, 4, 10, true, lazyPops = 5, fallbacks = 2, reachSumCells = 7.0, reachMaxCells = 6.0)
        val sum = b.summing(a)
        assertEquals(7, sum.expansions)
        assertEquals(7, sum.lazyPops)
        assertEquals(3, sum.fallbacks)
        assertEquals(12.0, sum.reachSumCells, 0.0)
        assertEquals(6.0, sum.reachMaxCells, 0.0)
    }
}
