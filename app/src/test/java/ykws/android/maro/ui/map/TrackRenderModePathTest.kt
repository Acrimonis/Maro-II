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

    // ── The legend keys banded strokes, and only them ────────────────────

    @Test
    fun theLegendNeedsABandedStrokeOnTheMap() {
        // The caller's own gate: MapScreen's derived input and this call are the same entry point —
        // legendVisibleForState over the raw state — so which input goes where cannot drift between
        // them, and the banded-stroke derivation stays the one bandedStrokeOnMap makes.
        fun gate(
            mode: TrackRenderMode,
            paintedIds: List<String>,
            selectedId: String?,
            eye: Boolean?,
            tracksVisible: Boolean = true
        ): Boolean = legendVisibleForState(
            paintedIds = paintedIds.toSet(),
            mode = mode,
            highlightedTrackId = selectedId,
            eyeOverride = eye,
            tracksVisible = tracksVisible
        )

        // Colours bands every stored track, so the others keep the scale up under a selection the eye has
        // flipped gold — and unselecting does not take it away either: it is the ramp on the map the
        // scale keys, not the selection's own fill.
        assertTrue(
            "Colours, the eye's gold selection with the others still banded",
            gate(TrackRenderMode.HEATMAP, listOf("a", "b"), selectedId = "a", eye = false)
        )
        assertTrue(
            "Colours with nothing selected",
            gate(TrackRenderMode.HEATMAP, listOf("a", "b"), selectedId = null, eye = null)
        )

        // The shipped bug: the eye flips the selection gold and it is the only track painted, so the map
        // carries no banded stroke at all.
        assertFalse(
            "Colours, the eye's gold selection alone",
            gate(TrackRenderMode.HEATMAP, listOf("a"), selectedId = "a", eye = false)
        )

        // An empty painted set hides it in every mode — count 0, or every summary's detail failed to
        // load — and so does the tracks layer being off, whatever else says otherwise.
        assertFalse(
            "Colours with nothing painted",
            gate(TrackRenderMode.HEATMAP, emptyList(), selectedId = "a", eye = null)
        )
        // A selection the eye has banded hides too while it never landed a stroke: the eye bands a
        // stroke that exists, and an empty painted set holds none.
        assertFalse(
            "Simple, the eye banding a selection that never landed a stroke",
            gate(TrackRenderMode.SIMPLE, emptyList(), selectedId = "a", eye = true)
        )
        assertFalse(
            "Colours with the tracks layer off",
            gate(TrackRenderMode.HEATMAP, listOf("a"), selectedId = "a", eye = null, tracksVisible = false)
        )

        // The eye is the only thing banding in the other two modes, and a selection it has banded is a
        // banded stroke on the map.
        assertTrue(
            "Simple, the eye banding the selection",
            gate(TrackRenderMode.SIMPLE, listOf("a"), selectedId = "a", eye = true)
        )
        assertTrue(
            "Dir & Speed, the eye banding the selection",
            gate(TrackRenderMode.DIR_SPEED, listOf("a"), selectedId = "a", eye = true)
        )

        // The value is about a selection, and there is none: a persisted eye bands nothing on its own.
        assertFalse(
            "Simple, the eye set with nothing selected",
            gate(TrackRenderMode.SIMPLE, emptyList(), selectedId = null, eye = true)
        )
        assertFalse(
            "Dir & Speed, the eye set with nothing selected",
            gate(TrackRenderMode.DIR_SPEED, emptyList(), selectedId = null, eye = true)
        )
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
