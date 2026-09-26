package ykws.android.maro.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.data.model.RouteResult
import ykws.android.maro.spatial.SpatialOperations
import ykws.android.maro.spatial.Units

/**
 * The anchor's lead, the trip figure and the naming a saved route takes.
 *
 * The trip figure is the reading the epic hangs arrival on — reaching the destination *is* the cell
 * reading zero — so the central test is that what is left of the route is zero at the destination
 * and the whole route at the start, with the ETA being that remainder over the pace rather than a
 * figure carried over from the plan.
 *
 * **The anchor's lead is the one new rule here** (R3): every entry into the acquisition projects the
 * boat's own position forward along its course and speed, and it stands down — answering the live fix
 * — wherever there is nothing trustworthy to project from, which is the fallback the acquisition's own
 * judgement is asked of.
 *
 * **What is deliberately absent is the old `stale` reading** (R13): an answer that cannot be given
 * changes nothing on the map, so the figure has nothing to mark and there is no state here to read.
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
     * **A route's own save name carries the fixed `Route ` prefix, and one shape only** (R25).
     *
     * The naming rule has one home, [`RoutePlan.trackName`], and **every** save that writes a route
     * reads it, so no save falls back on the bare auto-name a recorded journey carries. The prefix is a
     * fixed token rather than a localised string, a name being data — and the `· n/N` suffix the
     * withdrawn all-scope save once needed is gone, so one save path names a route one way.
     */
    @Test
    fun aRoutesSaveNameCarriesTheFixedRoutePrefixAndNothingElse() {
        val name = plan().trackName()

        assertTrue("the prefix is the fixed token, not a localised string", name.startsWith("Route "))
        assertFalse("and no index suffix survives the withdrawn all-scope save", name.contains("·"))
        assertEquals(
            "and two saves of one route name it the same way",
            name,
            plan().trackName()
        )
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
     * **A fix halfway along a leg is halfway through that leg** — the reading the projection buys, where
     * the retired vertex snap answered the whole leg from the vertex behind the boat.
     *
     * The projection is planar and its own KDoc states it accurate to < 1 % below 50 km, so the case
     * pins a **short leg** and asserts at that stated tolerance — never at the 1e-6/1e-9 the vertex
     * cases use.
     */
    @Test
    fun aFixHalfwayAlongALegAnswersHalfThatLegAndHalfItsSeconds() {
        val a = RoutePoint(43.5000, 7.0000)
        val b = RoutePoint(43.5010, 7.0000)
        val legM = SpatialOperations.haversine(
            LatLng(a.latitude, a.longitude),
            LatLng(b.latitude, b.longitude)
        )
        val half = RoutePoint((a.latitude + b.latitude) / 2.0, a.longitude)
        val route = RoutePlan(
            start = a,
            destination = b,
            destinationMoved = false,
            points = listOf(a, b),
            legTimesSec = listOf(120.0),
            distanceM = legM,
            durationSec = 120.0,
            computedAtMs = computedAt
        )

        val remaining = route.remainingFrom(half)

        assertEquals("half the leg is still to run", legM / 2.0, remaining.distanceM, legM * 0.01)
        assertEquals("and half its own seconds", 60.0, remaining.durationSec, 120.0 * 0.01)
    }

    /**
     * **What is left of the route is measured in the plan's own leg times, and nothing else.**
     *
     * `remainingFrom` prices each leg from `legTimesSec`: the chosen leg keeps the **fraction of its own
     * time** it has not yet travelled and every following leg keeps its own — never the total the plan
     * also carries; that fraction is pinned by the halfway case above, this one standing on a vertex
     * where nothing fractional is left. An engine that prices one line and answers another makes the two
     * disagree, and a remainder taken from the wrong one would count down to arrival at the wrong
     * moment; nothing pinned which of the two it reads.
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
     * **The anchor's lead: the boat's own position, projected forward along its own course and speed**
     * (R3).
     *
     * The projection is one pure arithmetic over the fix, and this reads it against its own
     * derivation — the great-circle point `speed × lead` metres along the course — rather than against
     * the helper's own answer. The pace is the boat's **speed over ground**, not the set free-water
     * pace: the quantity is where the boat will be, not where it might sail.
     */
    @Test
    fun theAnchorIsTheLiveFixLedByItsOwnSpeedAndCourse() {
        val leadSec = 10
        val speedKn = 12.0
        val course = 90.0
        val metres = Units.knotsToMps(speedKn) * leadSec

        val led = routeAnchorLead(
            RouteFix(position = p0, courseDeg = course, speedKn = speedKn),
            leadSec = leadSec
        )
        val expected = SpatialOperations.pointAlongBearing(p0.latitude, p0.longitude, course, metres)

        assertEquals(expected.latitude, led.latitude, 1e-9)
        assertEquals(expected.longitude, led.longitude, 1e-9)
        assertNotEquals("and a moving boat's anchor is not the fix itself", p0, led)
    }

    /**
     * **The lead is best-effort and never binding** (R3): wherever there is nothing trustworthy to
     * project from, the anchor is the live fix, and the acquisition proceeds from there.
     *
     * Every stand-down is read: no course, no speed, a non-finite course or speed (a device answering
     * nothing reads as zero), a speed under the floor at which a reported course is jitter rather than
     * a heading, and a lead of zero — which is also the demo mode's own case, its position being the
     * map centre and its pan-derived speed suspended while aiming.
     */
    @Test
    fun theLeadStandsDownAndAnswersTheLiveFixWhereverItCannotProject() {
        val led = { fix: RouteFix -> routeAnchorLead(fix, leadSec = 10) }

        assertEquals("no course", p0, led(RouteFix(p0, null, 12.0)))
        assertEquals("no speed", p0, led(RouteFix(p0, 90.0, null)))
        assertEquals("a course that is not a number reads as none", p0, led(RouteFix(p0, Double.NaN, 12.0)))
        assertEquals("a speed that is not a number reads as none", p0, led(RouteFix(p0, 90.0, Double.NaN)))
        assertEquals("a speed under the heading floor is jitter, not a course", p0, led(RouteFix(p0, 90.0, 0.2)))
        assertEquals("zero speed", p0, led(RouteFix(p0, 90.0, 0.0)))
        assertEquals(
            "and a lead of zero is the plain live fix",
            p0,
            routeAnchorLead(RouteFix(p0, 90.0, 12.0), leadSec = 0)
        )
    }

    /**
     * **The lead is a horizon, not a latency budget** (R3): the distance is the speed over the
     * configured time and nothing else, so the value stays engine-independent — a 500 ms desktop search
     * and a 7.3 s device reading are the same horizon.
     */
    @Test
    fun theLeadCoversTheConfiguredHorizonAndNothingElse() {
        val speedKn = 10.0
        val horizonM = Units.knotsToMps(speedKn) * 10

        val ten = routeAnchorLead(RouteFix(p0, 0.0, speedKn), leadSec = 10)
        val twenty = routeAnchorLead(RouteFix(p0, 0.0, speedKn), leadSec = 20)

        assertEquals(
            "the ten-second lead is ten seconds of the boat's own speed",
            horizonM,
            SpatialOperations.haversine(LatLng(p0.latitude, p0.longitude), LatLng(ten.latitude, ten.longitude)),
            1e-6
        )
        assertEquals(
            "and the horizon doubles with the lead it is asked for",
            2 * horizonM,
            SpatialOperations.haversine(LatLng(p0.latitude, p0.longitude), LatLng(twenty.latitude, twenty.longitude)),
            1e-3
        )
        assertEquals(
            "north is straight north at this scale, so the longitude holds",
            p0.longitude,
            ten.longitude,
            1e-9
        )
        assertTrue("and the latitude rises with a northward course", ten.latitude > p0.latitude)
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
}
