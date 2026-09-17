package ykws.android.maro.ui.map

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the mode's single exit (plan §6, §8): when the retained capture lands, and what it
 * does to the centre when it does.
 *
 * Two windows must not restore. A cross-type step closes its predecessor while the successor is
 * already on screen, and the toggle may be pressed with a card still standing — both leave an inspect
 * card up, so the capture waits for the last close. An abandonment (a step that dies, an open that
 * lands nothing) leaves the predecessor standing with the mode armed, so it applies nothing at all.
 * The guard is the canonical one: a map the user moved after the card opened is left where they put
 * it, exactly as a list-opened card leaves it.
 */
class InspectExitTest {

    // ── Is this the last inspect exit? (plan §6) ────────────────────────────

    @Test
    fun `the card's close with the armed half down is the exit`() {
        assertTrue(inspectLastExit(inspectArmed = false, inspectCardOpen = false))
    }

    @Test
    fun `the toggle with no card standing is the exit`() {
        // The armed half has just stood down and nothing else of the mode is left.
        assertTrue(inspectLastExit(inspectArmed = false, inspectCardOpen = false))
    }

    @Test
    fun `a card still standing is not the exit however the armed half reads`() {
        // The toggle pressed while the card stands: the close that follows is the one that restores.
        assertFalse(inspectLastExit(inspectArmed = false, inspectCardOpen = true))
        // The settled pick, before its armed half stands down at the landing.
        assertFalse(inspectLastExit(inspectArmed = true, inspectCardOpen = true))
    }

    @Test
    fun `an armed mode with no card is not the exit`() {
        // The abandonment window: the predecessor close of a step that never landed, and an open that
        // lands nothing with nothing beneath it, both leave the mode armed and the capture intact.
        assertFalse(inspectLastExit(inspectArmed = true, inspectCardOpen = false))
    }

    // ── What the exit does (plan §6) ────────────────────────────────────────

    @Test
    fun `a map that was following at arming re-pins`() {
        assertTrue(
            inspectExitRePins(
                capturedSuppressed = false,
                capturedTimerLive = false,
                mapMovedByUser = false
            )
        )
    }

    @Test
    fun `a map already panned at arming keeps its delay-return`() {
        // Either half of the captured panned state is enough: the suppression flag, or a timer that
        // was live when the mode armed.
        assertFalse(
            inspectExitRePins(
                capturedSuppressed = true,
                capturedTimerLive = false,
                mapMovedByUser = false
            )
        )
        assertFalse(
            inspectExitRePins(
                capturedSuppressed = false,
                capturedTimerLive = true,
                mapMovedByUser = false
            )
        )
    }

    @Test
    fun `a map the user moved after the card opened is left alone`() {
        // The canonical guard outranks the capture: even a map that was following at arming keeps the
        // frame the user put it on, and the boat takes the centre back on the ordinary delay.
        assertFalse(
            inspectExitRePins(
                capturedSuppressed = false,
                capturedTimerLive = false,
                mapMovedByUser = true
            )
        )
    }
}
