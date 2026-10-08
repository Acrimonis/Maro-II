package ykws.android.maro.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for the live acquire, its reset boundary and its own mark (plan §1, §3, §7).
 *
 * The three rules are pure, so they are pinned here without a map, a drawer or a clock:
 * - **the live swap** — the panel's presence is the target's presence, an answer already on screen is
 *   no change, and an open in flight owns the slot until it lands;
 * - **the reset boundary** — a drag forgets everything only *after* the mode's own recentre has landed,
 *   never before;
 * - **the mark** — the mode's own recentre is not the user's activity, so the key the quiet clock
 *   debounces is left untouched and the quiet it just spent cannot start again.
 */
class InspectAcquireTest {

    // ── The live swap (plan §1) ─────────────────────────────────────────────

    @Test
    fun `the nearest opens the panel when nothing stands`() {
        assertEquals(
            InspectAcquire.OPEN,
            inspectAcquireAction(rankId = "M1", cardOpen = false, openInFlight = false, showingId = null)
        )
    }

    @Test
    fun `a change of nearest swaps the panel`() {
        assertEquals(
            InspectAcquire.OPEN,
            inspectAcquireAction(rankId = "T2", cardOpen = true, openInFlight = false, showingId = "M1")
        )
    }

    @Test
    fun `an answer already on screen is no change`() {
        // The sweep republishes the same nearest on every motion tick: without this the live acquire
        // would reopen the very panel it opened.
        assertEquals(
            InspectAcquire.NONE,
            inspectAcquireAction(rankId = "M1", cardOpen = true, openInFlight = false, showingId = "M1")
        )
    }

    @Test
    fun `losing the target closes the panel while the mode stays armed`() {
        assertEquals(
            InspectAcquire.CLOSE,
            inspectAcquireAction(rankId = null, cardOpen = true, openInFlight = false, showingId = "M1")
        )
    }

    @Test
    fun `nothing to close when no panel stands`() {
        assertEquals(
            InspectAcquire.NONE,
            inspectAcquireAction(rankId = null, cardOpen = false, openInFlight = false, showingId = null)
        )
    }

    @Test
    fun `an open in flight owns the slot however the answer moves`() {
        // A cross-type swap's predecessor is still on screen while its successor lands, so the slot
        // belongs to that open: a new answer or a lost target must both wait for the landing.
        assertEquals(
            InspectAcquire.NONE,
            inspectAcquireAction(rankId = "T2", cardOpen = true, openInFlight = true, showingId = "M1")
        )
        assertEquals(
            InspectAcquire.NONE,
            inspectAcquireAction(rankId = null, cardOpen = true, openInFlight = true, showingId = "M1")
        )
    }

    // ── The reset boundary (plan §1) ────────────────────────────────────────

    @Test
    fun `a drag after the recentre is the reset`() {
        assertEquals(
            InspectUserMove.RESET,
            inspectUserMoveAction(
                armed = true,
                recentring = false,
                handoffInFlight = false,
                cameraBusy = false,
                recentreLanded = true
            )
        )
    }

    @Test
    fun `a drag before the recentre only remembers the move`() {
        // The quiet restarts and the exit leaves the frame where the user put it — nothing is forgotten.
        assertEquals(
            InspectUserMove.REMEMBER,
            inspectUserMoveAction(
                armed = true,
                recentring = false,
                handoffInFlight = false,
                cameraBusy = false,
                recentreLanded = false
            )
        )
    }

    @Test
    fun `the mode's own recentre is never the user's move`() {
        // Even with a recentre already landed, the mark holds for the whole of the move: its scroll
        // events must not read as the drag the reset exists for.
        assertEquals(
            InspectUserMove.NONE,
            inspectUserMoveAction(
                armed = true,
                recentring = true,
                handoffInFlight = false,
                cameraBusy = false,
                recentreLanded = true
            )
        )
    }

    @Test
    fun `an open or a busy camera swallows the move`() {
        assertEquals(
            InspectUserMove.NONE,
            inspectUserMoveAction(
                armed = true,
                recentring = false,
                handoffInFlight = true,
                cameraBusy = false,
                recentreLanded = true
            )
        )
        assertEquals(
            InspectUserMove.NONE,
            inspectUserMoveAction(
                armed = true,
                recentring = false,
                handoffInFlight = false,
                cameraBusy = true,
                recentreLanded = true
            )
        )
    }

    @Test
    fun `a disarmed mode reads no move at all`() {
        assertEquals(
            InspectUserMove.NONE,
            inspectUserMoveAction(
                armed = false,
                recentring = false,
                handoffInFlight = false,
                cameraBusy = false,
                recentreLanded = true
            )
        )
    }

    // ── The mark keeps the quiet from restarting (plan §3) ──────────────────

    @Test
    fun `the user's own motion bumps the activity tick`() {
        assertEquals(6, inspectActivityAfterMotion(activity = 5, recentring = false))
    }

    @Test
    fun `the mode's own recentre leaves the tick still, so the quiet cannot restart`() {
        // The key the clock debounces is unchanged while the mark holds — which is the whole of the
        // rule: an unchanged key never restarts the wait, so the quiet the recentre just spent stands
        // and the clock cannot fire a second recentre off the mode's own camera move.
        assertEquals(5, inspectActivityAfterMotion(activity = 5, recentring = true))
    }

    // ── The margin that damps the panel's churn (plan §1, 2026-10-07) ──────

    private val margin = 0.15

    private fun rank(id: String, distanceM: Double) =
        InspectRank(id = id, kind = InspectKind.MARKER, distanceM = distanceM)

    @Test
    fun `nothing eligible closes the acquisition`() {
        assertNull(
            inspectHoldSelection(
                held = rank("M1", 10.0),
                heldDistanceM = 10.0,
                nearest = null,
                marginFraction = margin
            )
        )
    }

    @Test
    fun `the first acquisition is whatever is closest`() {
        assertEquals(
            rank("M1", 40.0),
            inspectHoldSelection(held = null, heldDistanceM = null, nearest = rank("M1", 40.0), marginFraction = margin)
        )
    }

    @Test
    fun `the held item stays when the newcomer is inside the margin`() {
        // 9.0 is closer than 10.0, but by less than 15 %: the panel does not swap on that jitter.
        assertEquals(
            rank("M1", 10.0),
            inspectHoldSelection(
                held = rank("M1", 10.0),
                heldDistanceM = 10.0,
                nearest = rank("T2", 9.0),
                marginFraction = margin
            )
        )
    }

    @Test
    fun `a newcomer beyond the margin takes the panel over`() {
        assertEquals(
            rank("T2", 8.0),
            inspectHoldSelection(
                held = rank("M1", 10.0),
                heldDistanceM = 10.0,
                nearest = rank("T2", 8.0),
                marginFraction = margin
            )
        )
    }

    @Test
    fun `the margin is judged against the held item's distance as it stands now`() {
        // The panel is on a marker a drag has left 100 m behind while a route sits 50 m away: the held
        // figure is the one this very scan refreshed, so the hand-over is judged fairly and the panel
        // cannot stick to a target the map has left behind.
        assertEquals(
            rank("T2", 50.0),
            inspectHoldSelection(
                held = rank("M1", 3.0),
                heldDistanceM = 100.0,
                nearest = rank("T2", 50.0),
                marginFraction = margin
            )
        )
    }

    @Test
    fun `the held item keeps the panel and carries its refreshed distance`() {
        assertEquals(
            rank("M1", 120.0),
            inspectHoldSelection(
                held = rank("M1", 3.0),
                heldDistanceM = 120.0,
                nearest = rank("T2", 110.0),
                marginFraction = margin
            )
        )
    }

    @Test
    fun `an item the world has dropped hands the panel over`() {
        assertEquals(
            rank("T2", 400.0),
            inspectHoldSelection(
                held = rank("M1", 5.0),
                heldDistanceM = null,
                nearest = rank("T2", 400.0),
                marginFraction = margin
            )
        )
    }

    @Test
    fun `a margin of zero is the true closest on every sweep`() {
        assertEquals(
            rank("T2", 9.9),
            inspectHoldSelection(
                held = rank("M1", 10.0),
                heldDistanceM = 10.0,
                nearest = rank("T2", 9.9),
                marginFraction = 0.0
            )
        )
    }
}
