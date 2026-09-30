package ykws.android.maro.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.track.TrackSummary

/**
 * **The stored-route match** (R82, R86): a route-flagged summary whose two persisted end ids are the
 * armed pair — the **exact pair** first, the **return trip** second — newest-first within a pass, with
 * every near-miss refused.
 *
 * The predicate is the whole of the acquire half's decision, so it is pinned away from the screen: an
 * ordinary recording, a missing id and a route saved before the ids existed must all answer nothing,
 * two matches within a pass must resolve to the most recently saved line, and the return trip must be
 * answered **reversed** rather than confused with the exact pair. Which pairs are armable at all is the
 * drawer's rule, pinned in `RouteEndSelectionTest`.
 */
class RouteStoredMatchTest {

    private fun summary(
        id: String,
        route: Boolean = true,
        startMarkerId: String = "m-start",
        destinationMarkerId: String = "m-dest",
        startTimeMs: Long = 0L
    ) = TrackSummary(
        id = id,
        name = id,
        startTimeMs = startTimeMs,
        route = route,
        routeStartMarkerId = startMarkerId,
        routeDestinationMarkerId = destinationMarkerId
    )

    /** The exact pair on a route-flagged summary is the one the acquisition pulls back, unreversed. */
    @Test
    fun theExactDirectionalPairMatches() {
        val hit = summary("route-1", startTimeMs = 5L)
        val other = summary("route-2", startMarkerId = "m-other", startTimeMs = 9L)

        val match = storedRouteMatch(listOf(other, hit), "m-start", "m-dest")

        assertEquals("the stored line is answered", hit, match?.summary)
        assertFalse("and it is the line as saved, not mirrored", match!!.reversed)
    }

    /** Two matches resolve to the newest `startTimeMs`, whatever order the summaries arrive in. */
    @Test
    fun theNewestRouteWins() {
        val older = summary("old", startTimeMs = 10L)
        val newer = summary("new", startTimeMs = 20L)

        assertEquals("new", storedRouteMatch(listOf(older, newer), "m-start", "m-dest")?.summary?.id)
        assertEquals("new", storedRouteMatch(listOf(newer, older), "m-start", "m-dest")?.summary?.id)
    }

    /** The everyday out-and-back (R86): arming B→A finds the stored A→B line and mirrors it. */
    @Test
    fun theReversedPairIsMatchedAsTheReturnTrip() {
        val stored = summary("route-1")

        val match = storedRouteMatch(listOf(stored), "m-dest", "m-start")

        assertEquals("the stored line is answered for the return trip", stored, match?.summary)
        assertTrue("and it is flagged mirrored", match!!.reversed)
    }

    /** Where both directions are stored, the exact pair wins — the truer answer for the arm in hand. */
    @Test
    fun theExactPairWinsWhereBothDirectionsAreStored() {
        val forward = summary("forward")
        val backward = summary("backward", startMarkerId = "m-dest", destinationMarkerId = "m-start")

        val match = storedRouteMatch(listOf(backward, forward), "m-start", "m-dest")

        assertEquals("forward", match?.summary?.id)
        assertFalse("answered as saved, not mirrored", match!!.reversed)
    }

    /** The reverse pass is newest-first exactly as the forward pass is. */
    @Test
    fun theReversePassIsNewestFirst() {
        val older = summary("old-back", startMarkerId = "m-dest", destinationMarkerId = "m-start", startTimeMs = 10L)
        val newer = summary("new-back", startMarkerId = "m-dest", destinationMarkerId = "m-start", startTimeMs = 20L)

        assertEquals(
            "new-back",
            storedRouteMatch(listOf(older, newer), "m-start", "m-dest")?.summary?.id
        )
    }

    /** The `route` flag gates both passes, so an ordinary recording can never be pulled back. */
    @Test
    fun anOrdinaryRecordingNeverMatches() {
        assertNull(storedRouteMatch(listOf(summary("recording", route = false)), "m-start", "m-dest"))
        assertNull(storedRouteMatch(listOf(summary("recording", route = false)), "m-dest", "m-start"))
    }

    /** Either id missing is the search's path, never a match — and the reverse pass needs both too. */
    @Test
    fun aMissingIdNeverMatches() {
        val stored = summary("route-1")

        assertNull(storedRouteMatch(listOf(stored), null, "m-dest"))
        assertNull(storedRouteMatch(listOf(stored), "m-start", null))
        assertNull(storedRouteMatch(listOf(stored), null, null))
    }

    /** A route saved before the ids existed carries the empty sentinel and is never matched. */
    @Test
    fun aRouteSavedWithoutEndsNeverMatches() {
        val legacy = summary("legacy", startMarkerId = "", destinationMarkerId = "")

        assertNull(storedRouteMatch(listOf(legacy), "m-start", "m-dest"))
        assertNull(storedRouteMatch(listOf(legacy), "m-dest", "m-start"))
    }
}
