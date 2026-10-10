package ykws.android.maro.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.R
import ykws.android.maro.ui.components.ConfirmActionRole

/**
 * The route exit dialog's three doors, pinned per state — the family's fixed order (the forward outcome,
 * the stay, the ending), the accent on the enabled forward outcome, and no red where nothing is lost.
 *
 * The one axis is *is this line already a track?*: while it is not, the save door carries the accent and
 * the loss door is red; once it is, the save door disables, nothing is lost, and the accent moves to the
 * forward outcome *Leave*. The acquisition panel's own ending door is pinned here too, both of its states
 * reached through the same kind of pure function rather than through a composable.
 */
class RouteExitDoorsTest {

    @Test
    fun theUnwrittenLineOffersSaveDiscardAndNoLeave() {
        val doors = routeExitDoors(written = false)

        assertEquals("the forward outcome", R.string.route_exit_save, doors.save.labelRes)
        assertEquals("the save carries the accent", ConfirmActionRole.PRIMARY, doors.save.role)
        assertTrue("the save has work to do", doors.save.enabled)

        assertEquals("the stay", R.string.route_exit_continue, doors.stay.labelRes)
        assertEquals("the stay is secondary", ConfirmActionRole.SECONDARY, doors.stay.role)

        assertEquals("the loss door names what is lost", R.string.route_exit_discard, doors.loss.labelRes)
        assertEquals("the loss door is red", ConfirmActionRole.DANGER, doors.loss.role)
        assertTrue("the loss door can be taken", doors.loss.enabled)
        assertTrue("the press would throw the line away, so the ending defers", doors.loss.discards)
    }

    @Test
    fun theWrittenLineDisablesTheSaveAndMovesTheAccentToLeave() {
        val doors = routeExitDoors(written = true)

        assertEquals("the save keeps its place", R.string.route_exit_save, doors.save.labelRes)
        assertFalse("the save has nothing left to write", doors.save.enabled)

        assertEquals("the stay", R.string.route_exit_continue, doors.stay.labelRes)
        assertEquals("the stay is secondary", ConfirmActionRole.SECONDARY, doors.stay.role)

        assertEquals("the third door reads Leave", R.string.route_exit_leave, doors.loss.labelRes)
        assertEquals("the accent falls to the enabled forward outcome", ConfirmActionRole.PRIMARY, doors.loss.role)
        assertTrue("nothing is lost, so no door greys", doors.loss.enabled)
        assertFalse("nothing is thrown away, so the ending is silent", doors.loss.discards)
    }

    @Test
    fun thePanelEndingDoorIsShortAndRedWhileTheLineIsUnwritten() {
        val door = routeAcquisitionEndingDoor(written = false)

        assertEquals("the acquisition's own short word", R.string.route_acq_discard, door.labelRes)
        assertEquals(
            "the press would throw the line away, so the door is red",
            ConfirmActionRole.DANGER,
            door.role
        )
        assertTrue("the ending can be taken", door.enabled)
        assertTrue("the press would throw the selected line away, so the ending defers", door.discards)
    }

    @Test
    fun thePanelEndingDoorHandsOverToLeaveOnceTheLineIsWritten() {
        val door = routeAcquisitionEndingDoor(written = true)

        assertEquals("one cost, one word — the exit dialog's own Leave", R.string.route_exit_leave, door.labelRes)
        assertEquals("nothing is lost, so the accent replaces the red", ConfirmActionRole.PRIMARY, door.role)
        assertTrue("the ending can be taken", door.enabled)
        assertFalse("nothing is thrown away, so the ending is silent", door.discards)
        assertEquals(
            "the written door is the dialog's own loss door, derived rather than copied",
            routeExitDoors(written = true).loss,
            door
        )
    }

    @Test
    fun theOrderIsForwardThenStayThenEnding() {
        assertEquals(
            "the family's fixed door order",
            listOf(R.string.route_exit_save, R.string.route_exit_continue, R.string.route_exit_leave),
            routeExitDoors(written = true).ordered.map { it.labelRes }
        )
        assertEquals(
            "the family's fixed door order",
            listOf(R.string.route_exit_save, R.string.route_exit_continue, R.string.route_exit_discard),
            routeExitDoors(written = false).ordered.map { it.labelRes }
        )
    }
}
