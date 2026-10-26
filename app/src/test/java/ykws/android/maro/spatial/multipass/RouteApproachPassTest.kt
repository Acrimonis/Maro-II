package ykws.android.maro.spatial.multipass

import java.util.concurrent.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.spatial.SpatialOperations
import kotlin.math.abs
import kotlin.math.hypot

/** A rock's slow disc pins an exit north of the destination and a needless bend far out at sea. */
class RouteApproachPassTest {
    private val rock = point(0.0, 0.0)
    private val original = listOf(point(0.0, 150.0), point(0.0, 310.0), point(2500.0, 200.0), point(3000.0, -900.0))
    private val bounds = GateTestFixtures.box(200.0, 2200.0, 0.0, 3600.0)

    @Test
    fun movesTheSlowCrossingAndRemovesTheOpenWaterBendTogether() {
        val setup = setup(field())
        val swept = MultipassPull.sweep(setup, original, allowanceShare = 0.1, traceLive = false)
        assertEquals("deletion alone cannot improve this approach", original, swept)
        val result = RouteApproachPass.refine(setup, swept, bounds)

        assertTrue("the offshore bend is gone", result.size < swept.size)
        assertTrue("a new approach point was found", result.any { it !in original })
        assertTrue("moving the approach saves meaningful time", cost(setup, result) < cost(setup, original) - 5.0)
        assertSafeImprovement(setup, result)
    }

    @Test
    fun keepsCoastClearanceEvenWhenACheaperApproachWouldCrossAnotherRock() {
        val freeSetup = setup(field())
        val free = RouteApproachPass.refine(freeSetup, original, bounds)
        val obstacle = between(free[1], free.last(), 0.3)
        val guarded = setup(field(extraHard = RouteCostSource.Hard(distanceAt = {
            (SpatialOperations.haversine(it, obstacle) - 60.0).coerceAtLeast(0.0)
        })))
        assertClear(guarded, original)
        assertFalse("the tempting replacement crosses the added rock", clear(guarded, free))

        assertSafeImprovement(guarded, RouteApproachPass.refine(guarded, original, bounds))
    }

    @Test
    fun keepsTheMeasuredDepthWall() {
        val free = RouteApproachPass.refine(setup(field()), original, bounds)
        val shoal = between(free[1], free.last(), 0.3)
        val shoalNorth = (shoal.latitude - GateTestFixtures.LAT0) * GateTestFixtures.M_PER_DEG_LAT
        val shoalEast = (shoal.longitude - GateTestFixtures.LON0) * GateTestFixtures.M_PER_DEG_LON
        val raster = GateTestFixtures.raster(130, 190, 20.0) { n, e ->
            if (hypot(n - shoalNorth, e - shoalEast) < 80.0) 2.0 else 15.0
        }
        val depth = GateTestFixtures.depthOf(raster)
        val gate = GateTestFixtures.gateOver(raster, hardM = 20.0)
        val guarded = setup(field(extraHard = depthGateSource(3.0) { depth(it.latitude, it.longitude) }), gate)
        assertClear(guarded, original)
        assertFalse("the tempting replacement crosses the measured shoal", clear(guarded, free))

        assertSafeImprovement(guarded, RouteApproachPass.refine(guarded, original, bounds))
    }

    @Test
    fun preservesBothEndsAndFindsTheSameApproachInEitherDirection() {
        val setup = setup(field())
        val result = RouteApproachPass.refine(setup, original, bounds)
        assertEquals(result, RouteApproachPass.refine(setup, original, bounds))
        assertEquals(result.reversed(), RouteApproachPass.refine(setup.reversed(), original.reversed(), bounds))
        assertEquals(original.first(), result.first())
        assertEquals(original.last(), result.last())
        assertTrue(result.all { inBox(bounds, it) })
    }

    @Test
    fun keepsCandidateMovesInsideTheCorridor() {
        // Candidates north of the old approach are outside this tighter corridor.
        val tight = bounds.copy(latNorth = original[1].latitude)
        val setup = setup(field())
        val result = RouteApproachPass.refine(setup, original, tight)
        assertTrue(result.all { inBox(tight, it) })
        assertSafeImprovement(setup, result)
    }

    @Test
    fun markerPricesAloneDoNotTriggerSlowWaterRefinement() {
        val setup = setup(field(arm = PriceArm.MARKER))
        val trace = mutableListOf<String>()
        assertEquals(original, RouteApproachPass.refine(setup, original, bounds, trace = { trace += it() }))
        assertTrue(trace.single().contains("candidates=0"))
    }

    @Test(expected = CancellationException::class)
    fun cancellationInterruptsCandidateExploration() {
        var checks = 0
        RouteApproachPass.refine(setup(field()), original, bounds, checkCancelled = {
            if (++checks == 8) throw CancellationException("cancelled")
        })
    }

    private fun field(extraHard: RouteCostSource.Hard? = null, arm: PriceArm = PriceArm.BAND): RouteCostField {
        fun price(p: LatLng) = if (SpatialOperations.haversine(p, rock) <= 300.0) 0.35 else 0.0
        return RouteCostField(listOfNotNull(
            RouteCostSource.Hard(distanceAt = { (SpatialOperations.haversine(it, rock) - 30.0).coerceAtLeast(0.0) }),
            extraHard,
            RouteCostSource.Soft(
                priceSec = ::price, tag = MultipassCellState.BAND,
                clearanceAt = { abs(SpatialOperations.haversine(it, rock) - 300.0) },
                armAt = { arm to price(it) }
            )
        ))
    }

    private fun setup(field: RouteCostField, gate: GateRule? = null) = PullSetup(
        marginM = 25.0, coarseStepM = 20.0, priceStepM = 100.0,
        field = field, start = original.first(), aim = original.last(), gate = gate, paceMps = 15.0
    )

    private fun assertSafeImprovement(setup: PullSetup, result: List<LatLng>) {
        assertEquals(original.first(), result.first())
        assertEquals(original.last(), result.last())
        assertTrue("the complete replacement is no dearer", cost(setup, result) <= cost(setup, original))
        assertClear(setup, result)
    }

    private fun assertClear(setup: PullSetup, line: List<LatLng>) = assertTrue("all legs keep clearance", clear(setup, line))

    private fun clear(setup: PullSetup, line: List<LatLng>): Boolean {
        val ctx = setup.context()
        return line.zipWithNext().all { (a, b) -> MultipassPull.legClear(a, b, ctx) }
    }

    private fun cost(setup: PullSetup, line: List<LatLng>) = RouteFinePass().lineCostSec(setup, line)

    private fun point(eastM: Double, northM: Double) = GateTestFixtures.at(northM + 1400.0, eastM + 200.0)

    private fun between(a: LatLng, b: LatLng, t: Double) = LatLng(
        a.latitude + (b.latitude - a.latitude) * t, a.longitude + (b.longitude - a.longitude) * t
    )
}
