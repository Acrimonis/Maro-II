package ykws.android.maro.ui.map

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the pan-resume timer's drawer rule: the deadline that returns the camera to the boat
 * is held for as long as a drawer is open — the Where-Am-I dashboard included — and re-armed from the
 * close, so a pan survives a reading of the dashboard and the return is the one the pan would have had
 * on its own.
 *
 * The two exceptions are pinned here as well: a close with nothing to return to resumes nothing, and
 * the centre the inspect mode holds outranks the pan.
 */
class PanResumeTimerTest {

    // ── The dashboard's lifetime (the pan outlives it) ──────────────────────

    @Test
    fun `the panel opening holds the deadline`() {
        // The user has panned — there is a boat to return to — and the dashboard opens over it.
        assertEquals(
            PanResumeAction.HOLD,
            panResumeOnDrawerChange(
                open = true,
                autoFollowSuppressed = true,
                inspectLoaned = false,
                inspectArmed = false,
                inspectCardOpen = false
            )
        )
    }

    @Test
    fun `the panel closing restarts a full delay`() {
        // The pan is still outstanding, so the boat takes the centre back — from the close, and on
        // the ordinary delay rather than in the close's own frame.
        assertEquals(
            PanResumeAction.RESTART,
            panResumeOnDrawerChange(
                open = false,
                autoFollowSuppressed = true,
                inspectLoaned = false,
                inspectArmed = false,
                inspectCardOpen = false
            )
        )
    }

    @Test
    fun `a fresh open with the map on the boat has nothing to return to`() {
        // Holding is the same as clearing a deadline that was never armed: this close resumes nothing.
        assertEquals(
            PanResumeAction.HOLD,
            panResumeOnDrawerChange(
                open = true,
                autoFollowSuppressed = false,
                inspectLoaned = false,
                inspectArmed = false,
                inspectCardOpen = false
            )
        )
        assertEquals(
            PanResumeAction.NONE,
            panResumeOnDrawerChange(
                open = false,
                autoFollowSuppressed = false,
                inspectLoaned = false,
                inspectArmed = false,
                inspectCardOpen = false
            )
        )
    }

    // ── The inspect mode's own hold ─────────────────────────────────────────

    @Test
    fun `the loaned frame is handed back on the ordinary delay`() {
        // The mode's exit left a frame of its own: it is returned on the delay, never in this frame.
        assertEquals(
            PanResumeAction.RESTART,
            panResumeOnDrawerChange(
                open = false,
                autoFollowSuppressed = false,
                inspectLoaned = true,
                inspectArmed = false,
                inspectCardOpen = false
            )
        )
    }

    @Test
    fun `a card still standing keeps the hold`() {
        // Loaned, but the mode's card is up: this close is not the loan's hand-back.
        assertEquals(
            PanResumeAction.NONE,
            panResumeOnDrawerChange(
                open = false,
                autoFollowSuppressed = false,
                inspectLoaned = true,
                inspectArmed = false,
                inspectCardOpen = true
            )
        )
    }

    // ── The route draft's own hold ──────────────────────────────────────────

    @Test
    fun `the route draft's hold outranks the pan on a close`() {
        // The destination is still being placed: the frame the user is aiming across must not be
        // handed back to the boat, so a drawer's close inside the draft resumes nothing.
        assertEquals(
            PanResumeAction.NONE,
            panResumeOnDrawerChange(
                open = false,
                autoFollowSuppressed = true,
                inspectLoaned = false,
                inspectArmed = false,
                inspectCardOpen = false,
                routeDraftArmed = true
            )
        )
    }

    @Test
    fun `once the draft is released the pan resumes as it always did`() {
        // Confirming a route leaves the phase, so the hold is off and the deadline is re-armed.
        assertEquals(
            PanResumeAction.RESTART,
            panResumeOnDrawerChange(
                open = false,
                autoFollowSuppressed = true,
                inspectLoaned = false,
                inspectArmed = false,
                inspectCardOpen = false,
                routeDraftArmed = false
            )
        )
    }

    @Test
    fun `the inspect hold outranks the pan on a close`() {
        // Either half of the mode's hold beats the pan: an in-frame recentre would yank the anchor the
        // sweep reads or the frame the pick's own camera just set.
        assertEquals(
            PanResumeAction.NONE,
            panResumeOnDrawerChange(
                open = false,
                autoFollowSuppressed = true,
                inspectLoaned = false,
                inspectArmed = true,
                inspectCardOpen = false
            )
        )
        assertEquals(
            PanResumeAction.NONE,
            panResumeOnDrawerChange(
                open = false,
                autoFollowSuppressed = true,
                inspectLoaned = false,
                inspectArmed = false,
                inspectCardOpen = true
            )
        )
    }
}
