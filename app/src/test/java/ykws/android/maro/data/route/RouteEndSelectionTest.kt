package ykws.android.maro.data.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The stored shape of a route's end — [RouteEndSelection.encode], [RouteEndSelection.decode] and the
 * fallback [RouteEndSelection.resolve] performs — and the **eligible sets** the two selectors are built
 * from.
 *
 * Two rules carry the weight: **the list's order is the contract**, so an unresolved selection falls
 * back to the end's own first entry, and **the two ends do not offer the same entries**, so the boat's
 * position is unresolved at the destination even though it is the start's own first entry. The flags
 * the eligible sets are read from are **independent of each other** (R44), which is the one fact the
 * sets themselves have to pin.
 */
class RouteEndSelectionTest {

    @Test
    fun `the boat's position round-trips`() {
        val stored = RouteEndSelection.encode(RouteEndSelection.CurrentPosition)

        assertEquals(RouteEndSelection.CURRENT, stored)
        assertEquals(RouteEndSelection.CurrentPosition, RouteEndSelection.decode(stored))
    }

    @Test
    fun `the standing marker position round-trips`() {
        val stored = RouteEndSelection.encode(RouteEndSelection.MarkerPosition)

        assertEquals(RouteEndSelection.POSITION, stored)
        assertEquals(RouteEndSelection.MarkerPosition, RouteEndSelection.decode(stored))
    }

    @Test
    fun `a flagged marker round-trips`() {
        val stored = RouteEndSelection.encode(RouteEndSelection.Marker("m-42"))

        assertEquals("marker:m-42", stored)
        assertEquals(RouteEndSelection.Marker("m-42"), RouteEndSelection.decode(stored))
    }

    @Test
    fun `a value nothing writes reads as nothing`() {
        assertNull(RouteEndSelection.decode("somewhere-else"))
    }

    @Test
    fun `a blank reads as nothing`() {
        assertNull(RouteEndSelection.decode(null))
        assertNull(RouteEndSelection.decode("   "))
    }

    @Test
    fun `a marker recorded with no id reads as nothing`() {
        assertNull(RouteEndSelection.decode(RouteEndSelection.MARKER_PREFIX))
        assertNull(RouteEndSelection.decode("${RouteEndSelection.MARKER_PREFIX}   "))
    }

    @Test
    fun `a dead marker falls back to the start's first entry`() {
        val resolved = RouteEndSelection.resolve(
            "marker:gone",
            RouteEndSelection.End.START,
            eligibleMarkerIds = setOf("kept")
        )

        assertEquals(RouteEndSelection.CurrentPosition, resolved)
    }

    @Test
    fun `a dead marker falls back to the destination's first entry`() {
        val resolved = RouteEndSelection.resolve(
            "marker:gone",
            RouteEndSelection.End.DESTINATION,
            eligibleMarkerIds = setOf("kept")
        )

        assertEquals(RouteEndSelection.MarkerPosition, resolved)
    }

    @Test
    fun `the boat's position is not a destination`() {
        val resolved = RouteEndSelection.resolve(
            RouteEndSelection.CURRENT,
            RouteEndSelection.End.DESTINATION,
            eligibleMarkerIds = emptySet()
        )

        assertEquals(RouteEndSelection.MarkerPosition, resolved)
    }

    @Test
    fun `a live selection stands as stored`() {
        val resolved = RouteEndSelection.resolve(
            "marker:kept",
            RouteEndSelection.End.START,
            eligibleMarkerIds = setOf("kept")
        )

        assertEquals(RouteEndSelection.Marker("kept"), resolved)
    }

    @Test
    fun `a fresh install starts on each end's first entry`() {
        assertEquals(
            RouteEndSelection.CurrentPosition,
            RouteEndSelection.resolve(null, RouteEndSelection.End.START, emptySet())
        )
        assertEquals(
            RouteEndSelection.MarkerPosition,
            RouteEndSelection.resolve(null, RouteEndSelection.End.DESTINATION, emptySet())
        )
    }

    // ── The eligible sets, read from the flags alone (R44, R45) ─────────────

    @Test
    fun `the two ends are offered the markers their own flag names`() {
        val markers = listOf(
            MarkerRouteFlags("origin-only", origin = true, destination = false),
            MarkerRouteFlags("destination-only", origin = false, destination = true),
            MarkerRouteFlags("both", origin = true, destination = true),
            MarkerRouteFlags("neither", origin = false, destination = false)
        )

        assertEquals(
            "the start offers the origins, the both-flagged marker included",
            setOf("origin-only", "both"),
            RouteEndSelection.eligibleMarkerIds(RouteEndSelection.End.START, markers)
        )
        assertEquals(
            "and the destination the destinations — the flags are independent",
            setOf("destination-only", "both"),
            RouteEndSelection.eligibleMarkerIds(RouteEndSelection.End.DESTINATION, markers)
        )
    }

    @Test
    fun `a marker flagged for one end alone never reaches the other`() {
        val markers = listOf(MarkerRouteFlags("origin-only", origin = true, destination = false))

        assertEquals(
            "so a stored destination naming it is unresolved and falls back",
            RouteEndSelection.MarkerPosition,
            RouteEndSelection.resolve(
                RouteEndSelection.encode(RouteEndSelection.Marker("origin-only")),
                RouteEndSelection.End.DESTINATION,
                RouteEndSelection.eligibleMarkerIds(RouteEndSelection.End.DESTINATION, markers)
            )
        )
        assertEquals(
            "while the same marker resolves at the end it was flagged for",
            RouteEndSelection.Marker("origin-only"),
            RouteEndSelection.resolve(
                RouteEndSelection.encode(RouteEndSelection.Marker("origin-only")),
                RouteEndSelection.End.START,
                RouteEndSelection.eligibleMarkerIds(RouteEndSelection.End.START, markers)
            )
        )
    }

    @Test
    fun `a marker flagged for neither end is offered nowhere`() {
        val markers = listOf(MarkerRouteFlags("neither", origin = false, destination = false))

        assertEquals(
            emptySet<String>(),
            RouteEndSelection.eligibleMarkerIds(RouteEndSelection.End.START, markers)
        )
        assertEquals(
            emptySet<String>(),
            RouteEndSelection.eligibleMarkerIds(RouteEndSelection.End.DESTINATION, markers)
        )
    }

    /**
     * **The reverse pair is the flags' call, not the match's** (R86): the return trip is armable only
     * when **each** of the two markers carries **both** flags, because a selector names a marker only
     * while that end's own flag stands. The usual one-origin/one-destination setup therefore refuses the
     * swap here, before the trigger, which is why the match needs no legality gate of its own.
     */
    @Test
    fun `the reverse pair is unarmable unless both markers carry both flags`() {
        val markers = listOf(
            MarkerRouteFlags("origin-only", origin = true, destination = false),
            MarkerRouteFlags("destination-only", origin = false, destination = true)
        )
        val startEligible = RouteEndSelection.eligibleMarkerIds(RouteEndSelection.End.START, markers)
        val destinationEligible =
            RouteEndSelection.eligibleMarkerIds(RouteEndSelection.End.DESTINATION, markers)

        assertEquals("the usual pair arms one way", setOf("origin-only"), startEligible)
        assertEquals("and the other end offers only its own", setOf("destination-only"), destinationEligible)
        assertEquals(
            "so the start cannot name the destination-only marker — the swap dies here",
            RouteEndSelection.CurrentPosition,
            RouteEndSelection.resolve(
                RouteEndSelection.encode(RouteEndSelection.Marker("destination-only")),
                RouteEndSelection.End.START,
                startEligible
            )
        )
        assertEquals(
            "nor the destination the origin-only one",
            RouteEndSelection.MarkerPosition,
            RouteEndSelection.resolve(
                RouteEndSelection.encode(RouteEndSelection.Marker("origin-only")),
                RouteEndSelection.End.DESTINATION,
                destinationEligible
            )
        )
    }
}
