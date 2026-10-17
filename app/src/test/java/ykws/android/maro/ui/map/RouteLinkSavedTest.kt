package ykws.android.maro.ui.map

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.RoutePoint

/**
 * **The session link read as one fact** — [`routeLinkSaved`] is the only home of "this line is already a
 * track", shared by the selected line's and the followed line's save facts. The read direction is not an
 * identity (R99): a plan saved in its armed orientation answers true when the loss mirror has since
 * reversed the line, so the mirror never resurrects the save door.
 */
class RouteLinkSavedTest {

    private val a = RoutePoint(43.5000, 7.0000)
    private val b = RoutePoint(43.5100, 7.0000)
    private val c = RoutePoint(43.5200, 7.0000)
    private val plan = partialPlanOf(listOf(a, b, c), null, 1_234L)!!

    @Test
    fun noPlanIsNeverWritten() {
        assertFalse(routeLinkSaved(mapOf(plan to "track-1"), null))
    }

    @Test
    fun anEmptyLinkTableAnswersUnwritten() {
        assertFalse(routeLinkSaved(emptyMap(), plan))
    }

    @Test
    fun aLandedButUnsavedEntryAnswersUnwritten() {
        assertFalse("a session entry with no track id is not a save", routeLinkSaved(mapOf(plan to null), plan))
    }

    @Test
    fun theArmedOrientationAnswersItsOwnTrack() {
        assertTrue(routeLinkSaved(mapOf(plan to "track-1"), plan))
    }

    /**
     * **The mirrored read** (R99) — the loss mirror republishes the plan reversed while the link stays
     * keyed on the orientation the route was armed in, so the driven line still reads as written.
     */
    @Test
    fun theMirroredOrientationAnswersTheArmedTracksOwnTrack() {
        assertTrue(routeLinkSaved(mapOf(plan to "track-1"), plan.reversed()))
    }
}
