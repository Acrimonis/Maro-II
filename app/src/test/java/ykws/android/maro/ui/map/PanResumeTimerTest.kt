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
 * the inspect mode's own hold outranks the pan. The hold is the **armed flag alone** (plan §4): the
 * landing no longer stands the armed half down, so no card half stands beside it.
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
                inspectArmed = false
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
                inspectArmed = false
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
                inspectArmed = false
            )
        )
        assertEquals(
            PanResumeAction.NONE,
            panResumeOnDrawerChange(
                open = false,
                autoFollowSuppressed = false,
                inspectLoaned = false,
                inspectArmed = false
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
                inspectArmed = false
            )
        )
    }

    @Test
    fun `the armed flag is the whole hold, so a close inside it resumes nothing`() {
        // The landing no longer stands the armed half down, so a card standing is still an armed mode:
        // the centre is held for the whole armed time and this close must not hand it back.
        assertEquals(
            PanResumeAction.NONE,
            panResumeOnDrawerChange(
                open = false,
                autoFollowSuppressed = true,
                inspectLoaned = false,
                inspectArmed = true
            )
        )
    }

    @Test
    fun `the loan outranks the armed hold on a close`() {
        // The mode's own loaned frame is handed back on the delay even while the armed flag stands.
        assertEquals(
            PanResumeAction.RESTART,
            panResumeOnDrawerChange(
                open = false,
                autoFollowSuppressed = false,
                inspectLoaned = true,
                inspectArmed = true
            )
        )
    }
}
