package ykws.android.maro.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.data.model.RouteResult
import ykws.android.maro.spatial.SpatialOperations
import ykws.android.maro.spatial.Units
import ykws.android.maro.spatial.mesh.RouteMeshDetails

/**
 * The aim's two rules and the trip figure.
 *
 * The trip figure is the reading the epic hangs arrival on — reaching the destination *is* the cell
 * reading zero — so the central test is that what is left of the route is zero at the destination
 * and the whole route at the start, with the ETA being that remainder over the pace rather than a
 * figure carried over from the plan.
 */
class RoutePlanTest {

    private val computedAt = 1_700_000_000_000L
    private val p0 = RoutePoint(43.5000, 7.0000)
    private val p1 = RoutePoint(43.5100, 7.0000)
    private val p2 = RoutePoint(43.5200, 7.0000)

    private val d0 = SpatialOperations.haversine(
        LatLng(p0.latitude, p0.longitude),
        LatLng(p1.latitude, p1.longitude)
    )
    private val d1 = SpatialOperations.haversine(
        LatLng(p1.latitude, p1.longitude),
        LatLng(p2.latitude, p2.longitude)
    )

    private fun plan() = RoutePlan(
        start = p0,
        destination = p2,
        destinationMoved = false,
        points = listOf(p0, p1, p2),
        legTimesSec = listOf(120.0, 240.0),
        distanceM = d0 + d1,
        durationSec = 360.0,
        inBand = false,
        computedAtMs = computedAt
    )

    @Test
    fun whatIsLeftReachesZeroAtTheDestination() {
        val remaining = plan().remainingFrom(p2)

        assertEquals(0.0, remaining.distanceM, 1e-6)
        assertEquals(0.0, remaining.durationSec, 1e-9)
    }

    @Test
    fun whatIsLeftFromTheStartIsTheWholeRoute() {
        val remaining = plan().remainingFrom(p0)

        assertEquals(d0 + d1, remaining.distanceM, 1e-6)
        assertEquals(360.0, remaining.durationSec, 1e-9)
    }

    @Test
    fun whatIsLeftMidRouteIsTheRestOfItOnly() {
        val remaining = plan().remainingFrom(p1)

        assertEquals(d1, remaining.distanceM, 1e-6)
        assertEquals(240.0, remaining.durationSec, 1e-9)
    }

    /**
     * **What is left of the route is measured in the clock the plan reports, and nothing else.**
     *
     * `remainingFrom` walks the plan's own `legTimesSec`, so the seconds the trip cell counts down are
     * the seconds of the line on the screen — the drawn line's own, read off the polyline — and never
     * the seconds the search accumulated on the chain it priced. On a plan whose two disagree (which
     * is every plan a pass has touched) a remainder taken from the wrong list would count down to
     * arrival at the wrong moment, and nothing pinned which list it reads.
     */
    @Test
    fun whatIsLeftIsCountedInTheDrawnSecondsNotTheSearchedOnes() {
        val drawn = RouteResult.Success(
            points = listOf(p0, p1, p2),
            legTimesSec = listOf(120.0, 240.0),
            distanceM = d0 + d1,
            durationSec = 360.0,
            inBand = false,
            destinationMoved = false,
            // The two clocks the case turns on: the drawn line's own 360 s, and the 300 s the mesh
            // search accumulated on the chain behind it. The second is the engine's own reading now,
            // which is exactly why a plan must be read from the first. Every counter this dossier
            // does not name is the empty dossier's own zero — the rule's one home is
            // `RouteMeshDetailsReadings`, in `spatial/mesh`.
            details = RouteMeshDetails(pricedSec = 300.0)
        )

        val plan = RoutePlan.of(start = p0, result = drawn, nowMs = computedAt)

        assertEquals("the plan carries the drawn seconds", listOf(120.0, 240.0), plan.legTimesSec)
        assertEquals(
            "and the remainder is those seconds, not the searched ones",
            240.0,
            plan.remainingFrom(p1).durationSec,
            1e-9
        )
    }

    @Test
    fun theFirstAimAlwaysPassesAndTheRestNeedTheThreshold() {
        val near = RoutePoint(43.5000, 7.00001)
        val far = RoutePoint(43.5000, 7.00100)

        assertTrue(routeAimPassed(previous = null, next = p0))
        assertFalse(routeAimPassed(previous = p0, next = near, thresholdM = 25.0))
        assertTrue(routeAimPassed(previous = p0, next = far, thresholdM = 25.0))
    }

    @Test
    fun theTripTimeIsTheRemainderOverThePaceInForce() {
        val route = plan()
        val figure = routeTripFigure(
            plan = route,
            from = p1,
            paceKn = 6.0,
            nowMs = computedAt + 5_000L,
            stale = false
        )

        assertEquals(Units.metresToNauticalMiles(d1), figure.distanceNm, 1e-6)
        assertEquals(d1 / Units.knotsToMps(6.0), figure.etaSeconds, 1e-6)
        assertEquals(computedAt, figure.computedAtMs)
        assertFalse(figure.stale)
    }

    @Test
    fun aStalePlanKeepsItsFiguresAndSaysSo() {
        val route = plan()
        val figure = routeTripFigure(
            plan = route,
            from = p1,
            paceKn = 6.0,
            nowMs = computedAt,
            stale = true
        )

        assertTrue(figure.stale)
        assertEquals(Units.metresToNauticalMiles(d1), figure.distanceNm, 1e-6)
    }
}
