package ykws.android.maro.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.data.model.RouteResult
import ykws.android.maro.spatial.SpatialOperations
import ykws.android.maro.spatial.Units

/**
 * The aim's rule, the trip figure and the following phase's own threshold reading.
 *
 * The trip figure is the reading the epic hangs arrival on — reaching the destination *is* the cell
 * reading zero — so the central test is that what is left of the route is zero at the destination
 * and the whole route at the start, with the ETA being that remainder over the pace rather than a
 * figure carried over from the plan.
 *
 * **What is deliberately absent is the old `stale` reading** (R13): a failed refresh changes nothing
 * on the map and is said by a toast, so the figure has nothing to mark and there is no state here to
 * read — its return would be the defect, not its absence.
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
        computedAtMs = computedAt
    )

    /**
     * **A route's own save name carries the fixed `Route ` prefix** (R25).
     *
     * The naming rule has one home, [`RoutePlan.trackName`], and **every** save that writes a route
     * reads it — the draft's single doors included — so no single save falls back on the bare auto-name
     * a recorded journey carries. The prefix is a fixed token rather than a localised string, a name
     * being data; `· n/N` rides on the same base when one action writes several.
     */
    @Test
    fun aRoutesSaveNameCarriesTheFixedRoutePrefix() {
        val single = plan().trackName()
        val inSet = plan().trackName(index = 2, total = 3)

        assertTrue("the prefix is the fixed token, not a localised string", single.startsWith("Route "))
        assertFalse("a single save has no index to print", single.contains("·"))
        assertEquals("and one of a set keeps that same base", "$single · 2/3", inSet)
    }

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
     * **What is left of the route is measured in the plan's own leg times, and nothing else.**
     *
     * `remainingFrom` walks `legTimesSec`, so the seconds the trip cell counts down are the line's own —
     * the per-leg times the engine answered with — and never the total it also carries. An engine that
     * prices one line and answers another makes the two disagree, and a remainder taken from the wrong
     * one would count down to arrival at the wrong moment; nothing pinned which of the two it reads.
     */
    @Test
    fun whatIsLeftIsCountedInTheLegTimesNotTheTotal() {
        val drawn = RouteResult.Success(
            points = listOf(p0, p1, p2),
            legTimesSec = listOf(120.0, 240.0),
            distanceM = d0 + d1,
            durationSec = 360.0,
            destinationMoved = false
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

    /**
     * The plan's own date is the instant it was generated and finalised (R40) — the value that feeds
     * both the route's header and the name of the track it becomes — so it is the caller's own clock
     * reading and never the engine's answer's.
     */
    @Test
    fun thePlanIsDatedTheInstantItWasGeneratedAndNotTheSave() {
        val drawn = RouteResult.Success(
            points = listOf(p0, p1),
            legTimesSec = listOf(120.0),
            distanceM = d0,
            durationSec = 120.0,
            destinationMoved = false
        )

        val plan = RoutePlan.of(start = p0, result = drawn, nowMs = computedAt)

        assertEquals(computedAt, plan.computedAtMs)
    }

    /**
     * **The aim's rule: the configured ground move is what makes an aim worth a search.**
     *
     * The threshold is read from `AppConfig` where the caller passes none, so the number the properties
     * file holds is the number the rule uses — and a session with no baseline at all lets the first
     * real aim through, there being nothing to measure it against.
     */
    @Test
    fun theFirstAimAlwaysPassesAndTheRestNeedTheConfiguredMove() {
        val near = RoutePoint(43.5000, 7.00001)
        val far = RoutePoint(43.5000, 7.00100)
        val configured = AppConfig.routeAskMinTargetMoveM

        assertTrue(routeAimPassed(previous = null, next = p0))
        assertFalse(routeAimPassed(previous = p0, next = near))
        assertTrue(routeAimPassed(previous = p0, next = far))
        assertTrue(
            "and the default threshold is the file's own value",
            routeAimPassed(previous = p0, next = far, thresholdM = configured)
        )
        assertFalse(
            "a threshold below the configured move is the rule's own argument, not a second default",
            routeAimPassed(previous = p0, next = near, thresholdM = configured)
        )
    }

    @Test
    fun theTripTimeIsTheRemainderOverThePaceInForce() {
        val route = plan()
        val figure = routeTripFigure(
            plan = route,
            from = p1,
            paceKn = 6.0,
            nowMs = computedAt + 5_000L
        )

        assertEquals(Units.metresToNauticalMiles(d1), figure.distanceNm, 1e-6)
        assertEquals(d1 / Units.knotsToMps(6.0), figure.etaSeconds, 1e-6)
        assertEquals(computedAt, figure.computedAtMs)
    }

    /** With no pace in force the figure falls back on the plan's own drawn seconds. */
    @Test
    fun withNoPaceInForceTheFigureKeepsThePlansOwnSeconds() {
        val figure = routeTripFigure(
            plan = plan(),
            from = p1,
            paceKn = 0.0,
            nowMs = computedAt
        )

        assertEquals(240.0, figure.etaSeconds, 1e-9)
    }

    /**
     * **The distance off the route is the gate's own other reading** (R10), and it is taken to the
     * nearest vertex — the same snap the remainder reads — so the two can never disagree about where
     * on the line the boat stands. A position a kilometre off the line reads a kilometre, whatever the
     * line's shape.
     */
    @Test
    fun theDistanceOffTheRouteIsTheNearestVertexsOwn() {
        val offLine = RoutePoint(43.5100, 7.0200)

        val measured = routeDistanceOffRouteM(plan(), offLine)
        val expected = SpatialOperations.haversine(
            LatLng(offLine.latitude, offLine.longitude),
            LatLng(p1.latitude, p1.longitude)
        )

        assertEquals(expected, measured, 1e-6)
        assertEquals(
            "a boat standing on the line stands off it by nothing",
            0.0,
            routeDistanceOffRouteM(plan(), p1),
            1e-6
        )
    }
}
