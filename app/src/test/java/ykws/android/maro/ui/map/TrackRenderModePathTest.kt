package ykws.android.maro.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.config.TrackRenderMode

/**
 * The mode-to-path decision (D1, D10), the legend's own condition and the fade derivation (D8) as pure
 * contracts — the history and pinned loops both read these functions, so the mapping is pinned here
 * rather than observed on a device.
 *
 * The mode owns the arrows alone: Simple is the default colours with no arrows, and Dir & Speed and
 * Colours always draw them. The strokes split in two — the mode decides which of the three paths a
 * track takes, and the drawer eye moves the *selected* track's fill between the banded one and the
 * gold one, leaving the casing, the widths and the chevron geometry alone either way.
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

    // ── The drawer eye's scoped override: the fill, and nothing else ──────

    @Test
    fun theEyeBandsTheSelectedTrackWhateverTheModeSays() {
        TrackRenderMode.entries.forEach { mode ->
            val plan = plan(mode, selected = true, eye = true)

            assertEquals("$mode with the ramp forced on", TrackRenderPath.BANDED, plan.path)
        }
    }

    @Test
    fun theEyeTurnsTheRampOffForTheSelectedTrackAlone() {
        val fromColours = plan(TrackRenderMode.HEATMAP, selected = true, eye = false)

        assertEquals(TrackRenderPath.GOLD_HIGHLIGHT, fromColours.path)
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

    // ── The arrows follow the mode alone ─────────────────────────────────

    @Test
    fun theArrowsFollowTheModeAlone() {
        TrackRenderMode.entries.forEach { mode ->
            listOf(null, true, false).forEach { eye ->
                assertEquals(
                    "$mode draws its arrows on the mode alone — selected track, eye=$eye",
                    mode != TrackRenderMode.SIMPLE,
                    plan(mode, selected = true, eye = eye).drawArrows
                )
                assertEquals(
                    "$mode draws its arrows on the mode alone — every other track, eye=$eye",
                    mode != TrackRenderMode.SIMPLE,
                    plan(mode, selected = false, eye = eye).drawArrows
                )
            }
        }
    }

    @Test
    fun theEyeAcceptsBothCostsOfItsOwnRule() {
        // Pinned where the plan says they land: a banded selection in Simple carries the ramp with no
        // direction, and a gold selection in Colours keeps its chevrons because the mode draws them.
        val bandedInSimple = plan(TrackRenderMode.SIMPLE, selected = true, eye = true)
        assertEquals(TrackRenderPath.BANDED, bandedInSimple.path)
        assertFalse(bandedInSimple.drawArrows)

        val goldInColours = plan(TrackRenderMode.HEATMAP, selected = true, eye = false)
        assertEquals(TrackRenderPath.GOLD_HIGHLIGHT, goldInColours.path)
        assertTrue(goldInColours.drawArrows)
    }

    // ── The legend follows the focused track's fill ──────────────────────

    @Test
    fun theLegendNeedsASelectionWhoseFillIsTheRamp() {
        // No selection hides it, whatever the mode and the eye say: with nothing focused the map draws
        // no banded stroke to key — a persisted eye is a value *about* a selection, never one itself,
        // and Colours with the tracks layer off has no stored track left to band.
        assertFalse(
            "Colours with nothing selected",
            legendVisibleFor(TrackRenderMode.HEATMAP, selected = false, eyeOverride = null)
        )
        assertFalse(
            "a persisted eye with nothing selected",
            legendVisibleFor(TrackRenderMode.HEATMAP, selected = false, eyeOverride = true)
        )
        assertFalse(
            "Simple with nothing selected",
            legendVisibleFor(TrackRenderMode.SIMPLE, selected = false, eyeOverride = true)
        )

        // A selection the eye has flipped to gold hides it, in Colours too: the ramp is no longer what
        // the focused track is drawn as, and it is that one track the scale is on the map for.
        assertFalse(
            "a selection the eye has flipped to gold",
            legendVisibleFor(TrackRenderMode.HEATMAP, selected = true, eyeOverride = false)
        )
        assertFalse(
            "a gold selection in Dir & Speed",
            legendVisibleFor(TrackRenderMode.DIR_SPEED, selected = true, eyeOverride = false)
        )

        // The eye banding a selection shows it, in both modes that do not band on their own.
        assertTrue(
            "the eye banding a selection in Simple",
            legendVisibleFor(TrackRenderMode.SIMPLE, selected = true, eyeOverride = true)
        )
        assertTrue(
            "the eye banding a selection in Dir & Speed",
            legendVisibleFor(TrackRenderMode.DIR_SPEED, selected = true, eyeOverride = true)
        )

        // Colours with a selection shows it: the mode's own answer, the eye untouched.
        assertTrue(
            "Colours with a selection",
            legendVisibleFor(TrackRenderMode.HEATMAP, selected = true, eyeOverride = null)
        )
    }

    @Test
    fun theLegendAgreesWithTheSelectedTracksOwnPath() {
        // The gate is the selection's fill and nothing else, so it cannot drift from the path the
        // selected track is actually drawn by — the ramp on the map is exactly a BANDED selection.
        TrackRenderMode.entries.forEach { mode ->
            listOf(null, true, false).forEach { eye ->
                assertEquals(
                    "$mode, eye=$eye",
                    trackRenderPlan(mode, selected = true, eye).path == TrackRenderPath.BANDED,
                    legendVisibleFor(mode, selected = true, eyeOverride = eye)
                )
            }
        }
    }

    // ── The eye's persisted value: what a tap writes ─────────────────────

    @Test
    fun theFirstTapTurnsTheModesOwnReadingAround() {
        // Null is the untouched eye — the selection mirrors the mode — so the tap reverses that rather
        // than flipping a value nobody wrote. Its answer is never null, which is what persists the key.
        assertTrue("Simple: the first tap bands the selection", selectionBandedAfterTap(null, TrackRenderMode.SIMPLE))
        assertTrue(
            "Dir & Speed: the selection is gold there, so the first tap bands it",
            selectionBandedAfterTap(null, TrackRenderMode.DIR_SPEED)
        )
        assertFalse(
            "Colours: the selection is already banded, so the first tap turns the ramp off",
            selectionBandedAfterTap(null, TrackRenderMode.HEATMAP)
        )
    }

    @Test
    fun afterTheFirstTapTheModeStopsReachingTheEye() {
        // A written value flips its own value, whatever the stored mode has become in the meantime.
        TrackRenderMode.entries.forEach { mode ->
            assertTrue("false under $mode", selectionBandedAfterTap(false, mode))
            assertFalse("true under $mode", selectionBandedAfterTap(true, mode))
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
