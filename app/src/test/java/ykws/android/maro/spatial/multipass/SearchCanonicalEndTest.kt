package ykws.android.maro.spatial.multipass

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.spatial.Units
import ykws.android.maro.spatial.multipass.GateTestFixtures.at
import kotlin.math.hypot

/**
 * **A pair is one search from either end** — `RoutePassRunner.searchFromCanonicalEnd`, the last repair of
 * `261020_FEAT_PLN_Route_direction-symmetry.md`.
 *
 * The A\* breaks a cost tie by arrival order from wherever it starts, so an island on the line, with two
 * equal detours, can be taken on opposite sides by A→B and B→A. The fixture is that island on a single
 * grid, with a gate over deep water so the guard's two raw points are in play (a `null` gate skips them).
 * **The test is red-first by its own precondition:** a tie is not guaranteed by symmetry alone, so it first
 * requires that the plain search — what the pass ran before — really does disagree with itself on at least
 * one of the fixtures, and only then that the canonical search never does.
 *
 * Every walk runs on both coarse walks — the A\* and the Evolutive + row's any-angle walk (the any-angle plan's
 * P2). The red-first precondition is the A\*'s alone: its ties are what the repair answers, and the any-angle
 * walk's taut chains need not tie the same way (the plan's §4.4 re-reads it).
 */
class SearchCanonicalEndTest {

    private val paceKn = 12.0
    private val paceMps = Units.knotsToMps(paceKn)
    private val rows = 41
    private val cols = 61

    private fun newGrid() = MultipassGrid(
        GateTestFixtures.LAT0, GateTestFixtures.LON0,
        100.0 / GateTestFixtures.M_PER_DEG_LAT, 100.0 / GateTestFixtures.M_PER_DEG_LON,
        rows, cols, 100.0, baseCostSec(100.0, paceKn)
    )

    private val gate = GateTestFixtures.gateOver(GateTestFixtures.raster(rows, cols, 100.0) { _, _ -> 20.0 }, hardM = 10.0)

    /** One fixture: the two ends and a rectangular island, all in grid cells. */
    private class Config(val from: CellIndex, val to: CellIndex, val islandRows: IntRange, val islandCols: IntRange)

    private val configs: List<Config> = buildList {
        for (dRow in listOf(0, 2, 5)) for (half in listOf(1, 2, 4)) for (dCol in listOf(0, 3)) {
            val midRow = 20 + dRow / 2
            add(Config(CellIndex(20, 5), CellIndex(20 + dRow, 55), midRow - half..midRow + half, 27 + dCol..33 + dCol))
        }
    }

    /** The walk over a fresh grid with [config]'s island marked land. */
    private fun walkOf(config: Config): WalkWindows {
        val grid = newGrid()
        for (r in config.islandRows) for (c in config.islandCols) grid.markLand(r, c)
        return WalkWindows.of(grid)
    }

    private fun WalkWindows.point(cell: CellIndex): LatLng = center(cell.layer, cell.row, cell.col)

    private suspend fun plain(walk: WalkWindows, from: CellIndex, to: CellIndex, rule: AnyAngleRule?): List<CellIndex>? =
        MultipassSearch.searchWalk(
            walk, from, to, paceMps, stepGuard = StepGuard(gate, walk.point(from), walk.point(to)), anyAngle = rule
        ).path

    private suspend fun canonical(walk: WalkWindows, from: CellIndex, to: CellIndex, rule: AnyAngleRule?): SearchOutcome =
        RoutePassRunner().searchFromCanonicalEnd(
            walk, from, to, walk.point(from), walk.point(to), paceMps,
            zonePriceSec = { _, _, _, _ -> 0.0 }, berthK = 0.0, berth = null, gate = gate, anyAngle = rule
        )

    @Test
    fun theCanonicalSearchGivesOneChainFromEitherEndWhereThePlainOneDoesNot() = runTest {
        for ((name, rule) in bothWalks()) {
            var plainDisagrees = 0
            for (config in configs) {
                val walk = walkOf(config)
                val there = plain(walk, config.from, config.to, rule)
                val back = plain(walk, config.to, config.from, rule)
                assertNotNull("$name: the plain search lands a path", there)
                assertNotNull(back)
                if (there != back!!.reversed()) plainDisagrees++

                val a = canonical(walk, config.from, config.to, rule).path
                val b = canonical(walk, config.to, config.from, rule).path
                assertNotNull("$name: the canonical search lands a path", a)
                assertEquals("$name: one chain from either end", a, b!!.reversed())
                assertEquals("$name: and it runs from the caller's start", config.from, a!!.first())
                assertEquals(config.to, a.last())
            }
            if (rule == null) {
                assertTrue(
                    "the fixtures hold at least one tie the plain search takes two ways ($plainDisagrees of ${configs.size}) — " +
                        "otherwise this test is not red on the code it replaces",
                    plainDisagrees > 0
                )
            }
        }
    }

    @Test
    fun theOutcomeSaysWhichEndTheWalkLeft() = runTest {
        val config = configs.first()
        val walk = walkOf(config)
        for ((name, rule) in bothWalks()) {
            // The two ends stand on one row, so they tie on latitude and the western end sorts first: the call that
            // starts there walks from its own start, the call that starts at the eastern end walks from its aim.
            val forward = canonical(walk, config.from, config.to, rule)
            val backward = canonical(walk, config.to, config.from, rule)
            assertEquals("$name: the western start leaves from itself", false, forward.reversed)
            assertEquals("$name: the eastern start leaves from its aim", true, backward.reversed)
        }
    }

    @Test
    fun aCopyWalledInByLandIsRoutedBothWaysThroughTheSwap() = runTest {
        val repairs = DirectionSymmetryRepairsTest()
        val walk = repairs.walledCopyWalk()
        val copy = CellIndex(7, 17, layer = 1)
        val far = CellIndex(1, 9)
        val copyPoint: LatLng = walk.center(1, 7, 17)
        val farPoint: LatLng = walk.center(0, 1, 9)
        for ((name, rule) in bothWalks()) {
            suspend fun run(from: CellIndex, to: CellIndex, a: LatLng, b: LatLng) = RoutePassRunner().searchFromCanonicalEnd(
                walk, from, to, a, b, paceMps, zonePriceSec = { _, _, _, _ -> 0.0 }, berthK = 0.0, berth = null, gate = null,
                anyAngle = rule
            ).path
            val there = run(copy, far, copyPoint, farPoint)
            val back = run(far, copy, farPoint, copyPoint)
            assertNotNull("$name: the pair routes from the copy", there)
            assertNotNull("$name: and from the far end", back)
            assertEquals("$name: as one chain", there, back!!.reversed())
        }
    }

    /**
     * **The guard's two raw points swap with the cells.** `GateEndStepTest`'s shelf with a rock the start's shortest
     * exit grazes past the 20 m disc, armed so the end beside the rock is the caller's **aim** and sorts first: the
     * canonical walk leaves from it, and the guard it reads must name that end as the walk's start. Held to the wall
     * there, every drawn leg keeps it past the discs; the precondition shows the same walk handed the caller's
     * unswapped guard does not — its start leg runs from the far end, so the end's own step is never read as its own.
     */
    @Test
    fun theGuardsRawPointsSwapWithTheEndTheWalkLeaves() = runTest {
        val ends = GateEndStepTest()
        val raster = ends.shelf { n, e -> hypot(n - 204.4, e - 235.6) < 3.0 }
        val shelfGate = GateTestFixtures.gateOver(raster, hardM = 20.0)
        val grid = ends.gridOver(raster, shelfGate)
        val walk = WalkWindows.of(grid)
        val byRock = at(200.0, 210.0)
        val far = at(330.0, 400.0)
        val rockCell = grid.cellOf(byRock.latitude, byRock.longitude)
        val farCell = grid.cellOf(far.latitude, far.longitude)
        val pace = Units.knotsToMps(25.0)
        assertTrue("the end beside the rock sorts first", sortsFirst(byRock, far))
        fun drawn(path: List<CellIndex>, start: LatLng, aim: LatLng) =
            listOf(start) + path.map { grid.center(it.row, it.col) } + listOf(aim)
        for ((name, rule) in bothWalks()) {
            val unswapped = MultipassSearch.searchWalk(
                walk, rockCell, farCell, pace, stepGuard = StepGuard(shelfGate, far, byRock), anyAngle = rule
            )
            assertFalse(
                "$name: the precondition — the walk from the rock under the caller's own guard is not held to the wall there",
                unswapped.strictStart && unswapped.path?.let {
                    ends.keepsWallPast(shelfGate, drawn(it, byRock, far), byRock, far, 20.0, 22.0)
                } == true
            )

            val armedToRock = RoutePassRunner().searchFromCanonicalEnd(
                walk, farCell, rockCell, far, byRock, pace,
                zonePriceSec = { _, _, _, _ -> 0.0 }, berthK = 0.0, berth = null, gate = shelfGate, anyAngle = rule
            )
            val path = armedToRock.path
            assertNotNull("$name: the pair routes", path)
            assertTrue("$name: the walk left the caller's aim", armedToRock.reversed)
            assertTrue("$name: and held that end — its own start — to the wall", armedToRock.strictStart)
            assertTrue(
                "$name: every leg keeps the wall from the discs' rims",
                ends.keepsWallPast(shelfGate, drawn(path!!, far, byRock), far, byRock, 20.0, 22.0)
            )
            val armedFromRock = RoutePassRunner().searchFromCanonicalEnd(
                walk, rockCell, farCell, byRock, far, pace,
                zonePriceSec = { _, _, _, _ -> 0.0 }, berthK = 0.0, berth = null, gate = shelfGate, anyAngle = rule
            )
            assertEquals("$name: one chain from either end", path, armedFromRock.path?.reversed())
        }
    }

    /**
     * **A canonical walk that lands nothing is walked once the other way.** One end's own cell is priced past any
     * step, so it may be left but never entered: the pair routes from that end only — the one-way water a seam
     * crossing makes, in one cell. That end sorts last, so the canonical walk aims at it and exhausts, and the
     * fallback walks from it; both arming directions make the same two walks, land one chain, and read both walks'
     * expansions.
     */
    @Test
    fun aCanonicalWalkThatFindsNothingIsWalkedTheOtherWay() = runTest {
        val grid = newGrid()
        val oneWay = CellIndex(30, 30)
        val open = CellIndex(10, 30)
        grid.addSourceCost(oneWay.row, oneWay.col, Double.POSITIVE_INFINITY, MultipassCellState.BAND)
        val walk = WalkWindows.of(grid)
        assertTrue("the open end sorts first, so the canonical walk aims at the one-way end", sortsFirst(walk.point(open), walk.point(oneWay)))
        for ((name, rule) in bothWalks()) {
            val into = MultipassSearch.searchWalk(walk, open, oneWay, paceMps, anyAngle = rule)
            val outOf = MultipassSearch.searchWalk(walk, oneWay, open, paceMps, anyAngle = rule)
            assertNull("$name: the precondition — no walk enters the one-way end", into.path)
            assertNotNull("$name: while a walk leaves it", outOf.path)

            val fromOneWay = canonicalUngated(walk, oneWay, open, rule)
            val toOneWay = canonicalUngated(walk, open, oneWay, rule)
            assertNotNull("$name: the pair routes from the one-way end", fromOneWay.path)
            assertNotNull("$name: and to it", toOneWay.path)
            assertFalse("$name: armed from the one-way end, the fallback leaves the caller's start", fromOneWay.reversed)
            assertTrue("$name: armed toward it, the fallback leaves the caller's aim", toOneWay.reversed)
            assertEquals("$name: one chain from either end", fromOneWay.path, toOneWay.path!!.reversed())
            assertEquals("$name: and from the caller's start", oneWay, fromOneWay.path!!.first())
            assertEquals(
                "$name: the outcome reads both walks' expansions",
                into.expansions + outOf.expansions, fromOneWay.expansions
            )
            assertEquals(fromOneWay.expansions, toOneWay.expansions)
        }
    }

    private suspend fun canonicalUngated(walk: WalkWindows, from: CellIndex, to: CellIndex, rule: AnyAngleRule?) =
        RoutePassRunner().searchFromCanonicalEnd(
            walk, from, to, walk.point(from), walk.point(to), paceMps,
            zonePriceSec = { _, _, _, _ -> 0.0 }, berthK = 0.0, berth = null, gate = null, anyAngle = rule
        )
}
