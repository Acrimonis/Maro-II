package ykws.android.maro.spatial.multipass

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.spatial.SpatialOperations
import ykws.android.maro.spatial.Units

/**
 * The gate berth, pinned at its four homes.
 *
 * The law is [berthShare] / [berthPriceSecAt] — a straight fall from the gate's margin to the outer
 * edge, per metre of water. The rasterizer's BFS fills [MultipassGrid.gateDistanceM] and paints the
 * margin land; the search scales the stored distance by the pass's cursor; and [berthLawOf] rides the
 * plan so `avoid` prices none and `evolutive` prices the `route.evolutive.berth.*` keys.
 */
class BerthCostTest {

    private val marginM = AppConfig.routeAvoidDepthGateMarginM
    private val outerM = AppConfig.routeEvolutiveBerthOuterM
    private val secPerM = AppConfig.routeEvolutiveBerthSecPerM
    private val paceKn = 25.0

    @Test
    fun theShareFallsLinearlyFromTheMarginToTheOuterEdge() {
        assertEquals("full at the margin", 1.0, berthShare(marginM, marginM, outerM), 1e-12)
        assertEquals("zero at the outer edge", 0.0, berthShare(outerM, marginM, outerM), 1e-12)
        assertEquals("zero beyond it", 0.0, berthShare(outerM + 40.0, marginM, outerM), 1e-12)
        assertEquals(
            "a cell 40 m off pays the remaining span's share",
            (outerM - 40.0) / (outerM - marginM),
            berthShare(40.0, marginM, outerM),
            1e-12
        )
        var previous = Double.MAX_VALUE
        for (d in listOf(0.0, 15.0, 20.0, 30.0, 40.0, 49.0, 50.0, 60.0)) {
            val share = berthShare(d, marginM, outerM)
            assertTrue("the share never rises as the distance grows (at $d m)", share <= previous)
            previous = share
        }
    }

    @Test
    fun aSpanlessBerthPricesNothing() {
        assertEquals("a margin on the outer edge has no span", 0.0, berthShare(20.0, 50.0, 50.0), 0.0)
        assertEquals("and prices nothing anywhere", 0.0, berthPriceSecAt(20.0, 20.0, 50.0, 50.0, secPerM), 0.0)
    }

    @Test
    fun theRateIsTheSamePerMetreAtEveryCellSize() {
        val fine = berthPriceSecAt(20.0, 20.0, marginM, outerM, secPerM) / 20.0
        val coarse = berthPriceSecAt(20.0, 100.0, marginM, outerM, secPerM) / 100.0
        assertEquals("a 20 m cell and a 100 m cell price the metre alike", fine, coarse, 1e-12)
    }

    @Test
    fun theUniformPlanPricesNoBerthAndTheEvolutivePlanDoes() {
        assertNull("`avoid` prices no berth", berthLawOf(UniformGridPlan, depthGateActive = true))
        assertNull(
            "and a gate-off evolutive prices none either",
            berthLawOf(EvolutiveGridPlan, depthGateActive = false)
        )
        val berth = berthLawOf(EvolutiveGridPlan, depthGateActive = true)
        assertNotNull("evolutive with the gate on prices the berth", berth)
        assertEquals("its inner end is the gate's own margin", marginM, berth!!.marginM, 1e-12)
        assertEquals("its outer edge is the plan's key", outerM, berth.outerM, 1e-12)
        assertEquals("and its rate the plan's own", secPerM, berth.secPerM, 1e-12)
    }

    @Test
    fun theSearchScalesTheStoredDistanceByItsCursor() = runTest {
        val cellM = 20.0
        val grid = MultipassGrid(
            43.500, 7.000, 1.8e-4, 2.2e-4, rows = 1, cols = 5, cellM = cellM, baseCostSec = 1.0
        )
        grid.writeGateDistanceM(0, 2, marginM) // a full-share cell on the only row
        val priced = MultipassSearch.search(
            grid, CellIndex(0, 0), CellIndex(0, 4), Units.knotsToMps(paceKn),
            berthK = 1.0, berthMarginM = marginM, berthOuterM = outerM, berthSecPerM = secPerM
        )
        val free = MultipassSearch.search(
            grid, CellIndex(0, 0), CellIndex(0, 4), Units.knotsToMps(paceKn),
            berthK = 1.0, berthMarginM = marginM, berthOuterM = outerM, berthSecPerM = 0.0
        )
        assertNotNull(priced.path)
        // Four open cells cost 1 s each; the berth cell adds secPerM × share × cellM = 0.1 × 1 × 20 = 2 s.
        assertEquals("the berth cell charges its full share", 6.0, priced.costSec, 1e-9)
        assertEquals("without the berth the same path is the base alone", 4.0, free.costSec, 1e-9)
    }

    @Test
    fun theMaskKeepsFineWaterWithinTheCollar() {
        val box = BBox(43.500, 43.505, 7.000, 7.005)
        val lattice = WalkLattice.of(box, 20.0)
        // A shallow patch at the box centre, deep elsewhere.
        val shallow = LatLng((box.latSouth + box.latNorth) / 2.0, (box.lonWest + box.lonEast) / 2.0)
        val field = RouteCostField(
            listOf(depthGateSource(3.0) { p -> if (SpatialOperations.haversine(p, shallow) <= 40.0) 2.0 else 100.0 })
        )
        val collarM = 170.0
        val grid = rasterizeWindow(
            box, lattice, paceKn = paceKn, marginM = 0.0, edges = emptyList(), openCoast = emptyList(),
            capLatNorth = box.latNorth, field = field,
            gateMarginM = marginM, berthOuterM = outerM, collarReachM = collarM,
            fineMask = FineMask(coastBandsM = emptyList(), depthCollarM = collarM)
        )
        val midRow = grid.rows / 2
        val midCol = grid.cols / 2
        assertTrue("water 100 m from the patch is kept fine", grid.passable(midRow, midCol + 5))
        assertFalse("water beyond the collar is painted land", grid.passable(0, 0))
    }

    @Test
    fun theRasterizerFillsTheDistanceFieldAndPaintsTheMarginLand() {
        val cellM = 10.0
        val box = BBox(43.500, 43.5022, 7.000, 7.0009)
        val shallowNorth = 43.5010
        val field = RouteCostField(
            listOf(depthGateSource(3.0) { p -> if (p.latitude < shallowNorth) 2.0 else 100.0 })
        )
        val grid = rasterize(
            box, cellM, paceKn, marginM = 0.0, edges = emptyList(), openCoast = emptyList(),
            capLatNorth = box.latNorth, field = field,
            gateMarginM = marginM, berthOuterM = outerM
        )
        val c = grid.cols / 2
        val shallowRows = (0 until grid.rows).filter { grid.gateDistanceM(it, c) == 0.0 }
        assertTrue("some shallow row must be a wall", shallowRows.isNotEmpty())
        val sr = shallowRows.max()
        assertEquals(
            "the first deep cell sits one step off the wall",
            cellM, grid.gateDistanceM(sr + 1, c), 1e-9
        )
        assertFalse("and it is painted land (inside the margin)", grid.passable(sr + 1, c))
        assertEquals(
            "the second deep cell stands at two steps — exactly the margin",
            2 * cellM, grid.gateDistanceM(sr + 2, c), 1e-9
        )
        assertFalse("and it is painted land too (the margin is inclusive)", grid.passable(sr + 2, c))
        assertEquals(
            "the third deep cell stands beyond the margin",
            3 * cellM, grid.gateDistanceM(sr + 3, c), 1e-9
        )
        assertTrue("and it is open water", grid.passable(sr + 3, c))
        assertTrue(
            "a cell beyond the berth's outer edge reads unreachable",
            grid.gateDistanceM(sr + 6, c) >= outerM
        )
    }
}
