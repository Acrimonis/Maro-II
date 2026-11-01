package ykws.android.maro.spatial.multipass

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.spatial.SpatialOperations
import ykws.android.maro.spatial.Units
import ykws.android.maro.spatial.multipass.GateTestFixtures.at
import kotlin.math.hypot

/**
 * **The rule the Evolutive + row's runner walks under** — the shipped `route.search.anyAngle.*` keys, read as
 * `RoutePassRunner.runPass` reads them, so a suite that runs "as Evolutive +" walks that row's own search.
 */
internal fun evolutivePlusRule(): AnyAngleRule = AnyAngleRule.shipped()

/**
 * **The two coarse walks a suite runs on** (the any-angle plan's P2): the eight-direction A\* every other row walks,
 * `null`, and the Evolutive + row's any-angle walk — each named for the assertion messages.
 */
internal fun bothWalks(): List<Pair<String, AnyAngleRule?>> = listOf("A*" to null, "Evolutive +" to evolutivePlusRule())

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

    // ── The lone-turn fixture (the plan's P2) ──────────────────────────────────

    /**
     * The water of `RouteApproachPassTest`'s lone turn "far off a wide disc", in [at]'s metres: two ends on one row
     * and a slow disc whose rim the straight between them crosses, so the line turns once, round the disc's south.
     */
    private val loneStart = at(900.0, 1600.0)
    private val loneAim = at(900.0, 2800.0)
    private val discCentre = at(1100.0, 2200.0)
    private val discM = 300.0
    private val discSecPerM = 0.35
    private val loneCellM = 20.0
    private val lonePaceKn = 25.0

    /** The disc rastered on a 20 m grid: a cell whose centre stands inside it pays the disc's rate over its size. */
    private fun loneGrid(): MultipassGrid {
        val south = 600.0
        val west = 1500.0
        val corner = at(south, west)
        val grid = MultipassGrid(
            corner.latitude, corner.longitude,
            loneCellM / GateTestFixtures.M_PER_DEG_LAT, loneCellM / GateTestFixtures.M_PER_DEG_LON,
            45, 70, loneCellM, baseCostSec(loneCellM, lonePaceKn)
        )
        for (r in 0 until grid.rows) for (c in 0 until grid.cols) {
            if (inDisc(grid.center(r, c))) grid.addSourceCost(r, c, discSecPerM * loneCellM, MultipassCellState.BAND)
        }
        return grid
    }

    /** A point's north and east metres in [at]'s frame. */
    private fun metres(p: LatLng): Pair<Double, Double> = Pair(
        (p.latitude - GateTestFixtures.LAT0) * GateTestFixtures.M_PER_DEG_LAT,
        (p.longitude - GateTestFixtures.LON0) * GateTestFixtures.M_PER_DEG_LON
    )

    private fun inDisc(p: LatLng): Boolean {
        val (n, e) = metres(p)
        val (cn, ce) = metres(discCentre)
        return hypot(n - cn, e - ce) < discM
    }

    /** Whether the straight [a]–[b] stays out of the slow disc — its least distance to the centre, read exactly. */
    private fun clearOfDisc(a: LatLng, b: LatLng): Boolean {
        val (an, ae) = metres(a)
        val (bn, be) = metres(b)
        val (cn, ce) = metres(discCentre)
        val dn = bn - an
        val de = be - ae
        val t = (((cn - an) * dn + (ce - ae) * de) / (dn * dn + de * de)).coerceIn(0.0, 1.0)
        return hypot(an + t * dn - cn, ae + t * de - ce) >= discM
    }

    /** The slide a turn's slack allows: how far it could move along one leg, and the travel that move would save. */
    private data class Slide(val slackM: Double, val savedSec: Double)

    /**
     * **The slide of the turn at [v]**, read independently of the approach pass's own probe and in its sense: how far
     * [v] could slide along its leg toward [toward] while the rotated leg to [far] stays out of the slow water —
     * bisected to a metre — and the travel the slide to that point saves at [paceMps].
     */
    private fun slideOf(v: LatLng, toward: LatLng, far: LatLng, paceMps: Double): Slide {
        val legM = SpatialOperations.haversine(v, toward)
        var freeM = 0.0
        var blockedM = legM
        if (clearOfDisc(toward, far)) {
            freeM = legM
        } else {
            while (blockedM - freeM > 1.0) {
                val midM = (freeM + blockedM) / 2.0
                if (clearOfDisc(chordPointAt(v, toward, midM / legM), far)) freeM = midM else blockedM = midM
            }
        }
        val p = chordPointAt(v, toward, freeM / legM)
        val savedM = SpatialOperations.haversine(toward, v) + SpatialOperations.haversine(v, far) -
            SpatialOperations.haversine(toward, p) - SpatialOperations.haversine(p, far)
        return Slide(freeM, savedM / paceMps)
    }

    /** Each inner vertex of [line]'s dearer-saving slide, along either of its legs. */
    private fun slides(line: List<LatLng>, paceMps: Double): List<Slide> = (1 until line.lastIndex).map { i ->
        listOf(
            slideOf(line[i], line[i - 1], line[i + 1], paceMps),
            slideOf(line[i], line[i + 1], line[i - 1], paceMps)
        ).maxBy { it.savedSec }
    }

    /**
     * **On the lone-turn fixture the any-angle search's own chain has no vertex with slack** (the plan's P2), read as
     * the lone-turns plan reads slack (its §3): no turn the chain hands on could slide along a leg to a saving the
     * shipped lone slide would take (`route.approach.lone.minSavedSec`). What geometric slack it shows is the
     * raster's own rim — a tangent cell centre stands up to half a cell off the true disc — the grid's quantisation,
     * not a misplaced turn.
     */
    @Test
    fun onTheLoneTurnFixtureTheAnyAngleChainHasNoVertexWithSlack() = runTest {
        val grid = loneGrid()
        val walk = WalkWindows.of(grid)
        val from = grid.cellOf(loneStart.latitude, loneStart.longitude)
        val to = grid.cellOf(loneAim.latitude, loneAim.longitude)
        val paceMps = Units.knotsToMps(lonePaceKn)
        assertTrue("the straight between the ends crosses the slow disc", !clearOfDisc(walk.point(from), walk.point(to)))
        val chain = RoutePassRunner().searchFromCanonicalEnd(
            walk, from, to, walk.point(from), walk.point(to), paceMps,
            zonePriceSec = { _, _, _, _ -> 0.0 }, berthK = 0.0, berth = null, gate = null, anyAngle = evolutivePlusRule()
        ).path!!.map { walk.point(it) }
        val floorSec = AppConfig.routeApproachLoneMinSavedSec

        assertTrue("the chain turns round the disc: ${chain.size} vertices", chain.size >= 3)
        for ((a, b) in chain.zipWithNext()) assertTrue("each straight keeps out of the slow water", clearOfDisc(a, b))
        for ((i, slide) in slides(chain, paceMps).withIndex()) {
            assertTrue(
                "vertex ${i + 1} of the any-angle chain slides to a saving the lone slide would take: $slide " +
                    "(floor $floorSec s)",
                slide.savedSec < floorSec
            )
        }
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
