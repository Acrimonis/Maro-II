package ykws.android.maro.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.RouteOffer
import ykws.android.maro.data.model.RouteOfferSource
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.spatial.SpatialOperations

/**
 * **The fan's *Route (auto)* child** (R80) — arming with the intent to take the first answer, read the
 * way the machine and the drawing see it.
 *
 * The two readings that matter here are the ones the plan's D6 turns on: the one-shot keys on the
 * **settled line existing** (`Choosing.plan != null`) and never on "a non-empty candidate set" — the two
 * are the same thing one emission later, and waiting would make the promise depend on the engine's own
 * offers lane. And the line it takes is **index 0 of the drawn set**, which `routeCandidateLines` puts
 * the settled answer at, so the panel's own `selectRoute()` is the whole selection path and no offer is
 * ever needed for the child to keep its word.
 *
 * What a JVM test cannot reach is the arc itself — the parent's faces, the disabled child and the close
 * on press are Compose and device facts — so what is pinned here is the state predicate the shell's own
 * one-shot is wired to.
 */
class RouteAutoPickTest {

    private val computedAt = 1_700_000_000_000L
    private val start = RoutePoint(43.5000, 7.0000)
    private val aim = RoutePoint(43.5200, 7.0100)

    /** The offer's own end, for a candidate that saves time. */
    private val shortcut = RoutePoint(43.5150, 7.0080)

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

    private fun anOffer(to: RoutePoint, savingSec: Double) = RouteOffer(
        source = RouteOfferSource.SPEED_ZONES,
        points = listOf(start, to),
        legTimesSec = listOf(240.0),
        legSpeedsMps = listOf(1.0),
        distanceM = 1_000.0,
        durationSec = 240.0,
        savingSec = savingSec
    )

    private fun choosing(plan: RoutePlan? = null) = RouteState.Choosing(
        start = start,
        plan = plan,
        searching = plan == null,
        asked = plan == null
    )

    /**
     * The instant that arms the one-shot: the settled line landing. Before it the acquisition is still
     * searching and the flag waits — which is what holds the panel on screen while the search runs.
     */
    @Test
    fun theAutoPickFiresTheInstantTheSettledLineExists() {
        assertFalse("with nothing landed yet, the intent waits", routeAutoPickReady(true, choosing()))
        assertTrue("the settled line is the trigger", routeAutoPickReady(true, choosing(settled())))
    }

    /** The intent is the fan's own child: without it, the ordinary Route arming takes nothing. */
    @Test
    fun theIntentAloneOpensNothing() {
        assertFalse(
            "Route arms without the flag and keeps the panel",
            routeAutoPickReady(false, choosing(settled()))
        )
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

    /**
     * **The line taken is the settled one, and no offer is needed**: index 0 of the drawn set is the
     * settled answer the moment it lands — the set growing only as the engine's own offers arrive — so
     * `selectRoute()`'s own index takes exactly that line with nothing waited for.
     */
    @Test
    fun theLineTakenIsTheSettledOneAndNoOfferIsNeeded() {
        val settledPlan = settled()

        val noOffers = routeCandidateLines(settledPlan, emptyList())
        assertEquals("the set opens at one — the settled answer alone", listOf(settledPlan), noOffers)
        assertEquals("and index 0, which selectRoute() takes, is that line", settledPlan, noOffers[0])

        val withOffers = routeCandidateLines(settledPlan, listOf(anOffer(shortcut, 60.0)))
        assertEquals("an offer lands behind it and moves nothing", settledPlan, withOffers[0])
        assertEquals("so the promise was kept before any offer existed", 2, withOffers.size)
    }
}
