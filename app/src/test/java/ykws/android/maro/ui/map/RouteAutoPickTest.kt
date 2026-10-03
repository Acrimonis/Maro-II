package ykws.android.maro.ui.map

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.spatial.SpatialOperations

/**
 * **The fan's *Route (auto)* child** (R80) — arming with the intent to take the first answer, read the
 * way the machine sees it: the one-shot keys on the **main line existing** (`Choosing.plan != null`)
 * and never on "a non-empty page set". Index 0 of the page set *is* the main, which the arming puts
 * first; the seat may have followed the first landing onto a candidate (R94), so the child names
 * **index 0** through `selectMainRoute()` rather than riding the seat — no candidate landing is ever
 * needed, or allowed, to move the line it takes.
 */
class RouteAutoPickTest {

    private val computedAt = 1_700_000_000_000L
    private val start = RoutePoint(43.5000, 7.0000)
    private val aim = RoutePoint(43.5200, 7.0100)

    private fun settled() = RoutePlan(
        start = start,
        destination = aim,
        destinationMoved = false,
        points = listOf(start, aim),
        legTimesSec = listOf(300.0),
        distanceM = SpatialOperations.haversine(
            LatLng(start.latitude, start.longitude),
            LatLng(aim.latitude, aim.longitude)
        ),
        durationSec = 300.0,
        computedAtMs = computedAt
    )

    private fun choosing(plan: RoutePlan? = null) = RouteState.Choosing(
        start = start,
        plan = plan,
        searching = plan == null,
        asked = plan == null
    )

    /** The instant that arms the one-shot: the main line landing. */
    @Test
    fun theAutoPickFiresTheInstantTheMainLineExists() {
        assertFalse("with nothing landed yet, the intent waits", routeAutoPickReady(true, choosing()))
        assertTrue("the main line is the trigger", routeAutoPickReady(true, choosing(settled())))
    }

    /** The intent is the fan's own child: without it, the ordinary Route arming takes nothing. */
    @Test
    fun theIntentAloneOpensNothing() {
        assertFalse("Route arms without the flag and keeps the panel", routeAutoPickReady(false, choosing(settled())))
    }

    /** A mode that has ended, or one already following, is no acquisition for the flag to fire in. */
    @Test
    fun anEndedOrFollowingModeFiresNothing() {
        assertFalse("an ended acquisition takes nothing", routeAutoPickReady(true, RouteState.Idle))
        assertFalse(
            "and neither does a route already followed",
            routeAutoPickReady(true, RouteState.Following(settled()))
        )
    }
}
