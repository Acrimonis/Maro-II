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
 * forward outcome *Leave*.
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
