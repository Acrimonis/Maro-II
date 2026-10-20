package ykws.android.maro.spatial.multipass

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.config.AppConfig
import ykws.android.maro.spatial.Units
import ykws.android.maro.spatial.multipass.GateTestFixtures.at

/**
 * The gate berth, pinned at its homes.
 *
 * The law is [BerthLaw] — an inner ramp from the wall's floor to the gate's margin, an outer ramp from the
 * margin to the outer edge, one rate per metre of water that never rises with the distance off the
 * measured edge. The rasterizer writes [MultipassGrid.gateDistanceM] off the traced 3 m line; the search
 * scales the stored distance by the pass's cursor; and [berthLawOf] rides the plan so `avoid` prices none
 * and `evolutive` prices the `route.evolutive.berth.*` keys.
 */
class BerthCostTest {

    private val marginM = AppConfig.routeAvoidDepthGateMarginM
    private val outerM = AppConfig.routeEvolutiveBerthOuterM
    private val secPerM = AppConfig.routeEvolutiveBerthSecPerM
    private val paceKn = 25.0

    private val law = BerthLaw(marginM = 20.0, outerM = 50.0, secPerM = 0.1, floorM = 10.0, innerSecPerM = 0.5)

    @Test
    fun theTwoRampsFormOneFallingRate() {
        assertEquals("the floor pays the inner rate", 0.5, law.rateSecPerM(10.0), 1e-12)
        assertEquals("inside the floor too", 0.5, law.rateSecPerM(4.0), 1e-12)
        assertEquals("half way up the inner ramp", 0.3, law.rateSecPerM(15.0), 1e-12)
        assertEquals("the margin joins the outer ramp", 0.1, law.rateSecPerM(20.0), 1e-12)
        assertEquals("half way down the outer ramp", 0.05, law.rateSecPerM(35.0), 1e-12)
        assertEquals("nothing at the outer edge", 0.0, law.rateSecPerM(50.0), 1e-12)
        assertEquals("nor beyond it", 0.0, law.rateSecPerM(90.0), 1e-12)
        var previous = Double.MAX_VALUE
        var d = 0.0
        while (d <= 60.0) {
            val rate = law.rateSecPerM(d)
            assertTrue("the rate never rises as the distance grows (at $d m)", rate <= previous + 1e-12)
            previous = rate
            d += 0.5
        }
    }

    /** A symmetric passage's cheapest lateral position is its middle. */
    @Test
    fun aSymmetricPassageIsCheapestInItsMiddle() {
        val widthM = 50.0
        val middle = law.rateSecPerM(widthM / 2.0)
        var offset = 1.0
        while (offset < widthM / 2.0 - law.floorM) {
            val nearer = minOf(widthM / 2.0 - offset, widthM / 2.0 + offset)
            assertTrue("$offset m off the middle costs more", law.rateSecPerM(nearer) > middle)
            offset += 1.0
        }
    }

    @Test
    fun aLawWithoutAFloorPricesTheMarginAsTheBerthAlwaysDid() {
        val plain = BerthLaw(marginM = 20.0, outerM = 50.0, secPerM = 0.1)
        assertFalse(plain.hasInnerRamp)
        assertEquals("full inside the margin", 0.1, plain.rateSecPerM(12.0), 1e-12)
        assertEquals("a cell 40 m off pays the remaining span's share", 0.1 * 10.0 / 30.0, plain.rateSecPerM(40.0), 1e-12)
    }

    @Test
    fun aSpanlessBerthPricesNothing() {
        val spanless = BerthLaw(marginM = 50.0, outerM = 50.0, secPerM = 0.1)
        assertFalse(spanless.pricesAnything)
        assertEquals("a margin on the outer edge prices nothing anywhere", 0.0, spanless.rateSecPerM(20.0), 0.0)
    }

    @Test
    fun theRateIsTheSamePerMetreAtEveryCellSize() {
        assertEquals(
            "a 20 m cell and a 100 m cell price the metre alike",
            law.priceSecAt(15.0, 20.0) / 20.0, law.priceSecAt(15.0, 100.0) / 100.0, 1e-12
        )
    }

    @Test
    fun theInnerRampProvesNoGroupAndTheOuterRampKeepsItsBreakpoints() {
        assertEquals("inside the inner ramp a group is read mark by mark", 0.0, law.clearanceM(15.0), 0.0)
        assertEquals("on the outer ramp the clearance is the nearer breakpoint", 10.0, law.clearanceM(30.0), 1e-12)
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
        assertEquals("its margin is the gate's own", marginM, berth!!.marginM, 1e-12)
        assertEquals("its outer edge is the plan's key", outerM, berth.outerM, 1e-12)
        assertEquals("its rate the plan's own", secPerM, berth.secPerM, 1e-12)
        assertEquals("its floor the plan's key, never above the margin",
            AppConfig.routeEvolutiveBerthFloorM.coerceAtMost(marginM), berth.floorM, 1e-12)
        assertEquals("its inner rate never under the margin's",
            AppConfig.routeEvolutiveBerthInnerSecPerM.coerceAtLeast(secPerM), berth.innerSecPerM, 1e-12)
    }

    @Test
    fun theSearchScalesTheStoredDistanceByItsCursor() = runTest {
        val cellM = 20.0
        val grid = MultipassGrid(
            43.500, 7.000, 1.8e-4, 2.2e-4, rows = 1, cols = 5, cellM = cellM, baseCostSec = 1.0
        )
        grid.writeGateDistanceM(0, 2, marginM) // a cell on the margin, on the only row
        val priced = MultipassSearch.search(
            grid, CellIndex(0, 0), CellIndex(0, 4), Units.knotsToMps(paceKn),
            berthK = 1.0, berth = BerthLaw(marginM, outerM, secPerM)
        )
        val free = MultipassSearch.search(
            grid, CellIndex(0, 0), CellIndex(0, 4), Units.knotsToMps(paceKn),
            berthK = 1.0, berth = BerthLaw(marginM, outerM, 0.0)
        )
        assertNotNull(priced.path)
        // Four open cells cost 1 s each; the margin cell adds secPerM × cellM = 0.1 × 20 = 2 s.
        assertEquals("the margin cell charges the full rate", 4.0 + secPerM * cellM, priced.costSec, 1e-9)
        assertEquals("without the berth the same path is the base alone", 4.0, free.costSec, 1e-9)
    }

    @Test
    fun theMaskKeepsFineWaterWithinTheCollarOfTheMeasuredEdge() {
        // A shallow patch around (200 m, 200 m) on a 2 m raster, deep elsewhere.
        val raster = GateTestFixtures.raster(200, 200, 2.0) { n, e ->
            if ((n - 200.0) * (n - 200.0) + (e - 200.0) * (e - 200.0) <= 40.0 * 40.0) 2.0 else 100.0
        }
        val world = GateTestFixtures.RasterWorld(raster)
        val gate = GateTestFixtures.gateOver(raster, hardM = marginM)
        val box = GateTestFixtures.box(0.0, 400.0, 0.0, 400.0)
        val collarM = 170.0
        val grid = rasterize(
            box, 20.0, paceKn, 0.0, emptyList(), emptyList(), box.latNorth,
            field = GateTestFixtures.gateField(world), gate = gate, collarReachM = collarM
        )
        val masked = rasterizeWindow(
            box, WalkLattice.of(box, 20.0), paceKn = paceKn, marginM = 0.0, edges = emptyList(),
            openCoast = emptyList(), capLatNorth = box.latNorth, field = GateTestFixtures.gateField(world),
            gate = gate, collarReachM = collarM,
            fineMask = FineMask(coastBandsM = emptyList(), depthCollarM = collarM)
        )
        val near = masked.cellOf(at(200.0, 300.0).latitude, at(200.0, 300.0).longitude)
        assertTrue("water 60 m off the patch is kept fine", masked.passable(near.row, near.col))
        assertFalse("water beyond the collar is painted land", masked.passable(0, 0))
        assertTrue("the unmasked raster keeps that water", grid.passable(0, 0))
    }
}
