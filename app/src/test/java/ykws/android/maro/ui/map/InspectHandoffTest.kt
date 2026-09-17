package ykws.android.maro.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the inspect landing rule (plan §5, §8): a landing is the successor's own provenance
 * and nothing else.
 *
 * That is the whole point of the rule. A cross-type step holds its predecessor on screen while its
 * successor opens, so at the step itself a card of the *other* kind is still up, and a same-type step
 * is read from a card of its *own* kind still showing the previous id — both of which "some card is
 * open" would take for the arrival, spending the in-flight mark, disarming the mode early and then
 * reading the successor's real arrival as the user having closed the card.
 */
class InspectHandoffTest {

    private val trackSuccessor = InspectTarget("T1", InspectKind.TRACK)
    private val markerSuccessor = InspectTarget("M2", InspectKind.MARKER)

    @Test
    fun `a marker successor lands on the card showing its own id`() {
        assertTrue(
            inspectLanded(markerSuccessor, viewingMarkerId = "M2", trackOpen = false, trackId = null)
        )
    }

    @Test
    fun `a same-type step is not landed by the predecessor still showing its own id`() {
        assertFalse(
            inspectLanded(markerSuccessor, viewingMarkerId = "M1", trackOpen = false, trackId = null)
        )
    }

    @Test
    fun `no marker card is no landing`() {
        assertFalse(
            inspectLanded(markerSuccessor, viewingMarkerId = null, trackOpen = false, trackId = null)
        )
    }

    @Test
    fun `a track successor is not landed by the drawer still showing the predecessor`() {
        assertFalse(
            inspectLanded(trackSuccessor, viewingMarkerId = null, trackOpen = true, trackId = "T0")
        )
    }

    @Test
    fun `a track successor lands on the drawer opened for its own id`() {
        assertTrue(
            inspectLanded(trackSuccessor, viewingMarkerId = null, trackOpen = true, trackId = "T1")
        )
    }

    @Test
    fun `a track that has not landed yet is no landing`() {
        assertFalse(
            inspectLanded(trackSuccessor, viewingMarkerId = null, trackOpen = false, trackId = "T1")
        )
    }

    @Test
    fun `the other kind's card is never a landing`() {
        // The predecessor held for a marker successor: a track drawer open beside it, on any id.
        assertFalse(
            inspectLanded(markerSuccessor, viewingMarkerId = null, trackOpen = true, trackId = "M2")
        )
        // And the mirror: a marker card up while a track successor is in flight.
        assertFalse(
            inspectLanded(trackSuccessor, viewingMarkerId = "T1", trackOpen = false, trackId = null)
        )
    }

    // ── The predecessor an open holds (plan §5) ─────────────────────────────

    /** A held-decision call with everything off but the flags under test. */
    private fun heldCard(
        opening: InspectKind,
        markerCardOpen: Boolean = false,
        markerCardInspectSourced: Boolean = false,
        trackCardOpen: Boolean = false,
        trackCardInspectSourced: Boolean = false
    ) = inspectHeldCard(
        opening = opening,
        markerCardOpen = markerCardOpen,
        markerCardInspectSourced = markerCardInspectSourced,
        trackCardOpen = trackCardOpen,
        trackCardInspectSourced = trackCardInspectSourced
    )

    @Test
    fun `the mode's own other-kind card is held for the successor`() {
        assertEquals(
            InspectKind.MARKER,
            heldCard(InspectKind.TRACK, markerCardOpen = true, markerCardInspectSourced = true)
        )
        assertEquals(
            InspectKind.TRACK,
            heldCard(InspectKind.MARKER, trackCardOpen = true, trackCardInspectSourced = true)
        )
    }

    @Test
    fun `a card the mode did not open is never held`() {
        // A list-opened track card: its close restores the frame it was opened on, and deferring that
        // restore to the landing would run it after the successor's own camera and yank that frame.
        assertNull(heldCard(InspectKind.MARKER, trackCardOpen = true, trackCardInspectSourced = false))
        // A list- or map-opened marker card, for the same reason.
        assertNull(heldCard(InspectKind.TRACK, markerCardOpen = true, markerCardInspectSourced = false))
    }

    @Test
    fun `a card of the successor's own kind is never held`() {
        // A same-type step's predecessor is the same kind of card, still showing the previous id: the
        // slot stays filled by the successor itself, so there is nothing to hold behind it.
        assertNull(heldCard(InspectKind.MARKER, markerCardOpen = true, markerCardInspectSourced = true))
        assertNull(heldCard(InspectKind.TRACK, trackCardOpen = true, trackCardInspectSourced = true))
    }

    @Test
    fun `nothing on screen holds nothing`() {
        assertNull(heldCard(InspectKind.TRACK))
        assertNull(heldCard(InspectKind.MARKER))
    }
}
