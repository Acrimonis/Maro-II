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

    /** The child is the arming when nothing is armed, and the intent alone while a search runs. */
    @Test
    fun theAutoChildIsLiveWhenIdleAndWhileASearchRuns() {
        assertTrue(
            "nothing armed: it is the arming",
            routeAutoChildEnabled(displayArmed = false, state = RouteState.Idle, autoPickSet = false)
        )
        assertTrue(
            "a search running: it sets the intent",
            routeAutoChildEnabled(displayArmed = true, state = choosing(), autoPickSet = false)
        )
    }

    /** It is off once there is nothing left for it to decide. */
    @Test
    fun theAutoChildIsOffOnceTheIntentStandsOrTheSearchHasEnded() {
        assertFalse(
            "the intent already stands",
            routeAutoChildEnabled(displayArmed = true, state = choosing(), autoPickSet = true)
        )
        assertFalse(
            "a settled set is for Follow",
            routeAutoChildEnabled(displayArmed = true, state = choosing(settled()), autoPickSet = false)
        )
        assertFalse(
            "an early select is committed",
            routeAutoChildEnabled(
                displayArmed = true,
                state = choosing().copy(committed = true),
                autoPickSet = false
            )
        )
        assertFalse(
            "a followed route has no search to follow the winner of",
            routeAutoChildEnabled(displayArmed = true, state = RouteState.Following(settled()), autoPickSet = false)
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
