package ykws.android.maro.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.R
import ykws.android.maro.ui.components.ConfirmActionRole

/**
 * The acquisition panel's ending door, pinned per state. A route asks no dialog on leaving (R59, R100), so
 * the panel's one ending door is all that is left of the old exit doors.
 *
 * The one axis is *is this line already a track?*: while it is not, the door reads the acquisition's own
 * short word and is red; once it is, nothing is lost and the door reads *Leave* under the accent. The word
 * follows the cost and the act does not — every ending leaves through the deferred window (R92) — so what is
 * pinned here is the door's word, role and enabled state, and `discards` is read as the cost it names rather
 * than as the deferral.
 */
class RouteExitDoorsTest {

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
        assertTrue("the press would throw the selected line away, so the ending is the discard act", door.discards)
    }

    @Test
    fun thePanelEndingDoorReadsLeaveUnderTheAccentOnceTheLineIsWritten() {
        val door = routeAcquisitionEndingDoor(written = true)

        assertEquals("one cost, one word", R.string.route_exit_leave, door.labelRes)
        assertEquals("nothing is lost, so the accent replaces the red", ConfirmActionRole.PRIMARY, door.role)
        assertTrue("the ending can be taken", door.enabled)
        assertFalse("nothing is thrown away, so the ending is the leave act", door.discards)
    }
}
