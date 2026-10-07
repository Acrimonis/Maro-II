package ykws.android.maro.ui.map

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.spatial.SpatialOperations

/**
 * **The fan's *Route (auto)* child** (R80) — arming with the intent to take the **final best** of the
 * settled set, read the way the machine sees it: the one-shot keys on the set being **settled** — nothing
 * still searching, at least one line landed — so it waits for the ladder's last rung rather than for the
 * main's line alone. The selection then seats the running best, so no candidate that landed first may
 * move the line it takes.
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

    /** The instant that arms the one-shot: the set settling with at least one line landed. */
    @Test
    fun theAutoPickFiresOnceTheSetSettlesWithALine() {
        assertFalse(
            "with nothing landed yet, the intent waits",
            routeAutoPickReady(true, choosing(), anyLanded = false)
        )
        assertTrue(
            "a settled set carrying a line is the trigger",
            routeAutoPickReady(true, choosing(settled()), anyLanded = true)
        )
        assertFalse(
            "a set still searching is not settled",
            routeAutoPickReady(true, choosing(), anyLanded = true)
        )
    }

    /** The intent is the fan's own child: without it, the ordinary Route arming takes nothing. */
    @Test
    fun theIntentAloneOpensNothing() {
        assertFalse(
            "Route arms without the flag and keeps the panel",
            routeAutoPickReady(false, choosing(settled()), anyLanded = true)
        )
    }

    /** A mode that has ended, or one already following, is no acquisition for the flag to fire in. */
    @Test
    fun anEndedOrFollowingModeFiresNothing() {
        assertFalse(
            "an ended acquisition takes nothing",
            routeAutoPickReady(true, RouteState.Idle, anyLanded = true)
        )
        assertFalse(
            "and neither does a route already followed",
            routeAutoPickReady(true, RouteState.Following(settled()), anyLanded = true)
        )
    }
}
