package ykws.android.maro.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.config.TrackRenderMode

/**
 * The mode-to-path decision (D1, D10) and the fade derivation (D8) as pure contracts — the history
 * and pinned loops both read these two functions, so the mapping is pinned here rather than observed
 * on a device.
 *
 * The mode owns the strokes and the arrows in one value: Simple is the default colours with no
 * arrows, Dir & Speed keeps those colours and adds the arrows, and Colours bands every stored track.
 * The drawer eye is the selected track's own override and never the mode itself.
 */
class TrackRenderModePathTest {

    private fun plan(mode: TrackRenderMode, selected: Boolean = false, eye: Boolean? = null) =
        trackRenderPlan(mode, selected, eye)

    // ── The three modes, unselected ──────────────────────────────────────

    @Test
    fun simpleDrawsTheDefaultColoursAndNoArrows() {
        val plan = plan(TrackRenderMode.SIMPLE)

        assertEquals(TrackRenderPath.PLAIN, plan.path)
        assertFalse(plan.drawArrows)
        assertFalse(plan.selected)
    }

    @Test
    fun dirSpeedKeepsThoseColoursAndAddsTheArrows() {
        val plan = plan(TrackRenderMode.DIR_SPEED)

        assertEquals(TrackRenderPath.PLAIN, plan.path)
        assertTrue(plan.drawArrows)
    }

    @Test
    fun coloursBandsEveryStoredTrackAndDrawsItsArrows() {
        val plan = plan(TrackRenderMode.HEATMAP)

        assertEquals(TrackRenderPath.BANDED, plan.path)
        assertTrue(plan.drawArrows)
    }

    // ── The selected track ───────────────────────────────────────────────

    @Test
    fun selectionKeepsTheGoldCoreWhereverTheTrackIsNotBanded() {
        assertEquals(
            TrackRenderPath.GOLD_HIGHLIGHT,
            plan(TrackRenderMode.SIMPLE, selected = true).path
        )
        assertFalse("Simple draws no arrows on the selected track either", plan(TrackRenderMode.SIMPLE, selected = true).drawArrows)

        val dirSpeed = plan(TrackRenderMode.DIR_SPEED, selected = true)
        assertEquals(TrackRenderPath.GOLD_HIGHLIGHT, dirSpeed.path)
        assertTrue("Dir & Speed arrows reach the selected track too", dirSpeed.drawArrows)

        val colours = plan(TrackRenderMode.HEATMAP, selected = true)
        assertEquals(TrackRenderPath.BANDED, colours.path)
        assertTrue(colours.selected)
        assertTrue(colours.drawArrows)
    }

    // ── The drawer eye's scoped override ─────────────────────────────────

    @Test
    fun theEyeBandsTheSelectedTrackWhateverTheModeSays() {
        TrackRenderMode.entries.forEach { mode ->
            val plan = plan(mode, selected = true, eye = true)

            assertEquals("$mode with the ramp forced on", TrackRenderPath.BANDED, plan.path)
            assertTrue(plan.drawArrows)
        }
    }

    @Test
    fun theEyeTurnsTheRampOffForTheSelectedTrackAlone() {
        val fromColours = plan(TrackRenderMode.HEATMAP, selected = true, eye = false)

        assertEquals(TrackRenderPath.GOLD_HIGHLIGHT, fromColours.path)
        assertFalse(fromColours.drawArrows)
    }

    @Test
    fun theEyeNeverMovesANonSelectedTrackOrTheMode() {
        TrackRenderMode.entries.forEach { mode ->
            listOf(true, false).forEach { eye ->
                val other = plan(mode, selected = false, eye = eye)

                assertEquals(
                    "$mode must be unmoved by the eye on another track",
                    plan(mode, selected = false, eye = null).path,
                    other.path
                )
                assertEquals(plan(mode, selected = false, eye = null).drawArrows, other.drawArrows)
            }
        }
    }

    // ── D8's fade, read the way the appearance factory reads it ──────────

    @Test
    fun theFadeRunsFromTheNewestTrackToTheOldest() {
        val newest = trackFadeAlpha(index = 0, total = 4, transparencyNewest = 0, transparencyOldest = 60)
        val oldest = trackFadeAlpha(index = 3, total = 4, transparencyNewest = 0, transparencyOldest = 60)

        assertEquals(1f, newest, 0.001f)
        assertEquals(0.4f, oldest, 0.001f)
        // Monotone between the two ends.
        val middle = trackFadeAlpha(index = 1, total = 4, transparencyNewest = 0, transparencyOldest = 60)
        assertTrue(middle < newest && middle > oldest)
    }

    @Test
    fun aSingleTrackTakesTheNewestAlphaWhateverTheRange() {
        assertEquals(
            0.75f,
            trackFadeAlpha(index = 0, total = 1, transparencyNewest = 25, transparencyOldest = 90),
            0.001f
        )
    }

    @Test
    fun anInvertedRangeStillFadesNewestToOldest() {
        // The factory has always normalised the pair, so a range written backwards behaves the same.
        assertEquals(
            trackFadeAlpha(index = 2, total = 3, transparencyNewest = 0, transparencyOldest = 50),
            trackFadeAlpha(index = 2, total = 3, transparencyNewest = 50, transparencyOldest = 0),
            0.001f
        )
    }
}
