package ykws.android.maro.spatial.multipass

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.spatial.Units

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

    private suspend fun plain(walk: WalkWindows, from: CellIndex, to: CellIndex): List<CellIndex>? =
        MultipassSearch.searchWalk(
            walk, from, to, paceMps, stepGuard = StepGuard(gate, walk.point(from), walk.point(to))
        ).path

    private suspend fun canonical(walk: WalkWindows, from: CellIndex, to: CellIndex): SearchOutcome =
        RoutePassRunner().searchFromCanonicalEnd(
            walk, from, to, walk.point(from), walk.point(to), paceMps,
            zonePriceSec = { _, _, _, _ -> 0.0 }, berthK = 0.0, berth = null, gate = gate
        )

    @Test
    fun theCanonicalSearchGivesOneChainFromEitherEndWhereThePlainOneDoesNot() = runTest {
        var plainDisagrees = 0
        for (config in configs) {
            val walk = walkOf(config)
            val there = plain(walk, config.from, config.to)
            val back = plain(walk, config.to, config.from)
            assertNotNull("the plain search lands a path", there)
            assertNotNull(back)
            if (there != back!!.reversed()) plainDisagrees++

            val a = canonical(walk, config.from, config.to).path
            val b = canonical(walk, config.to, config.from).path
            assertNotNull("the canonical search lands a path", a)
            assertEquals("one chain from either end", a, b!!.reversed())
            assertEquals("and it runs from the caller's start", config.from, a!!.first())
            assertEquals(config.to, a.last())
        }
        assertTrue(
            "the fixtures hold at least one tie the plain search takes two ways ($plainDisagrees of ${configs.size}) — " +
                "otherwise this test is not red on the code it replaces",
            plainDisagrees > 0
        )
    }

    @Test
    fun theOutcomeSaysWhichEndTheWalkLeft() = runTest {
        val config = configs.first()
        val walk = walkOf(config)
        // The two ends stand on one row, so they tie on latitude and the western end sorts first: the call that
        // starts there walks from its own start, the call that starts at the eastern end walks from its aim.
        val forward = canonical(walk, config.from, config.to)
        val backward = canonical(walk, config.to, config.from)
        assertEquals("the western start leaves from itself", false, forward.reversed)
        assertEquals("the eastern start leaves from its aim", true, backward.reversed)
    }

    @Test
    fun aCopyWalledInByLandIsRoutedBothWaysThroughTheSwap() = runTest {
        val repairs = DirectionSymmetryRepairsTest()
        val walk = repairs.walledCopyWalk()
        val copy = CellIndex(7, 17, layer = 1)
        val far = CellIndex(1, 9)
        val copyPoint: LatLng = walk.center(1, 7, 17)
        val farPoint: LatLng = walk.center(0, 1, 9)
        suspend fun run(from: CellIndex, to: CellIndex, a: LatLng, b: LatLng) = RoutePassRunner().searchFromCanonicalEnd(
            walk, from, to, a, b, paceMps, zonePriceSec = { _, _, _, _ -> 0.0 }, berthK = 0.0, berth = null, gate = null
        ).path
        val there = run(copy, far, copyPoint, farPoint)
        val back = run(far, copy, farPoint, copyPoint)
        assertNotNull("the pair routes from the copy", there)
        assertNotNull("and from the far end", back)
        assertEquals("as one chain", there, back!!.reversed())
    }
}
