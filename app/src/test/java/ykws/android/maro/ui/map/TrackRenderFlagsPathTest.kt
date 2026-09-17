package ykws.android.maro.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The axes-to-path decision (D1, D10), the legend's own condition and the fade derivation (D8) as pure
 * contracts — the history and pinned loops both read these functions, so the mapping is pinned here
 * rather than observed on a device.
 *
 * The two axes are independent, which is what gives four combinations where the retired triple gave
 * three: neither (the default colours, no chevrons), arrows alone (those colours with chevrons),
 * colours alone (the ramp with no chevrons — the combination the triple forbade) and both. The strokes
 * split in two — the colours flag decides whether a track takes the banded path, and the drawer eye
 * moves the *selected* track's fill between the banded one and the gold one, leaving the casing, the
 * widths and the chevron geometry alone either way.
 */
class TrackRenderFlagsPathTest {

    private fun plan(
        arrows: Boolean,
        colours: Boolean,
        selected: Boolean = false,
        eye: Boolean? = null
    ) = trackRenderPlan(arrows, colours, selected, eye)

    /** The four combinations, as `arrows to colours` pairs, for the loops below. */
    private val combinations = listOf(
        false to false,
        true to false,
        false to true,
        true to true
    )

    // ── The four combinations, unselected ────────────────────────────────

    @Test
    fun neitherAxisDrawsTheDefaultColoursAndNoChevrons() {
        val plan = plan(arrows = false, colours = false)

        assertEquals(TrackRenderPath.PLAIN, plan.path)
        assertFalse(plan.drawArrows)
        assertFalse(plan.selected)
    }

    @Test
    fun theArrowsAloneKeepThoseColoursAndAddTheChevrons() {
        val plan = plan(arrows = true, colours = false)

        assertEquals(TrackRenderPath.PLAIN, plan.path)
        assertTrue(plan.drawArrows)
    }

    @Test
    fun coloursAloneBandEveryStoredTrackWithoutChevrons() {
        val plan = plan(arrows = false, colours = true)

        assertEquals(TrackRenderPath.BANDED, plan.path)
        assertFalse("independence: the ramp no longer implies chevrons", plan.drawArrows)
    }

    @Test
    fun bothAxesBandEveryStoredTrackAndDrawTheirChevrons() {
        val plan = plan(arrows = true, colours = true)

        assertEquals(TrackRenderPath.BANDED, plan.path)
        assertTrue(plan.drawArrows)
    }

    // ── The selected track ───────────────────────────────────────────────

    @Test
    fun selectionKeepsTheGoldCoreWhereverTheTrackIsNotBanded() {
        val neither = plan(arrows = false, colours = false, selected = true)
        assertEquals(TrackRenderPath.GOLD_HIGHLIGHT, neither.path)
        assertFalse("no chevrons on the selected track while the arrows flag is off", neither.drawArrows)

        val arrowsOnly = plan(arrows = true, colours = false, selected = true)
        assertEquals(TrackRenderPath.GOLD_HIGHLIGHT, arrowsOnly.path)
        assertTrue("the chevrons reach the selected track too", arrowsOnly.drawArrows)

        val colours = plan(arrows = true, colours = true, selected = true)
        assertEquals(TrackRenderPath.BANDED, colours.path)
        assertTrue(colours.selected)
        assertTrue(colours.drawArrows)
    }

    // ── The drawer eye's scoped override: the fill, and nothing else ──────

    @Test
    fun theEyeBandsTheSelectedTrackWhateverTheFlagsSay() {
        combinations.forEach { (arrows, colours) ->
            val plan = plan(arrows, colours, selected = true, eye = true)

            assertEquals(
                "arrows=$arrows colours=$colours with the ramp forced on",
                TrackRenderPath.BANDED,
                plan.path
            )
        }
    }

    @Test
    fun theEyeTurnsTheRampOffForTheSelectedTrackAlone() {
        val fromColours = plan(arrows = true, colours = true, selected = true, eye = false)

        assertEquals(TrackRenderPath.GOLD_HIGHLIGHT, fromColours.path)
    }

    @Test
    fun theEyeNeverMovesANonSelectedTrackOrTheFlags() {
        combinations.forEach { (arrows, colours) ->
            listOf(true, false).forEach { eye ->
                val other = plan(arrows, colours, selected = false, eye = eye)

                assertEquals(
                    "arrows=$arrows colours=$colours must be unmoved by the eye on another track",
                    plan(arrows, colours, selected = false, eye = null).path,
                    other.path
                )
                assertEquals(plan(arrows, colours, selected = false, eye = null).drawArrows, other.drawArrows)
            }
        }
    }

    // ── The chevrons follow the arrows flag alone ────────────────────────

    @Test
    fun theChevronsFollowTheArrowsFlagAlone() {
        combinations.forEach { (arrows, colours) ->
            listOf(null, true, false).forEach { eye ->
                assertEquals(
                    "arrows=$arrows colours=$colours — selected track, eye=$eye",
                    arrows,
                    plan(arrows, colours, selected = true, eye = eye).drawArrows
                )
                assertEquals(
                    "arrows=$arrows colours=$colours — every other track, eye=$eye",
                    arrows,
                    plan(arrows, colours, selected = false, eye = eye).drawArrows
                )
            }
        }
    }

    @Test
    fun theEyeAcceptsBothCostsOfItsOwnRule() {
        // Pinned where the plan says they land: a banded selection with the chevrons off carries the
        // ramp without direction, and a gold selection with them on keeps its chevrons because the
        // arrows flag draws them.
        val bandedWithoutArrows = plan(arrows = false, colours = true, selected = true, eye = true)
        assertEquals(TrackRenderPath.BANDED, bandedWithoutArrows.path)
        assertFalse(bandedWithoutArrows.drawArrows)

        val goldWithArrows = plan(arrows = true, colours = true, selected = true, eye = false)
        assertEquals(TrackRenderPath.GOLD_HIGHLIGHT, goldWithArrows.path)
        assertTrue(goldWithArrows.drawArrows)
    }

    // ── The legend keys the ramp's fill, and the open track's own ────────

    @Test
    fun theLegendNeedsABandedStrokeOnTheMap() {
        // The caller's own gate: MapScreen's derived input and this call are the same entry point —
        // legendVisibleForState over the raw state — so which input goes where cannot drift between
        // them, and the banded-stroke derivation stays the one bandedStrokeOnMap makes.
        fun gate(
            arrows: Boolean,
            colours: Boolean,
            paintedIds: List<String>,
            selectedId: String?,
            eye: Boolean?,
            tracksVisible: Boolean = true
        ): Boolean = legendVisibleForState(
            paintedIds = paintedIds.toSet(),
            trackArrows = arrows,
            trackColours = colours,
            highlightedTrackId = selectedId,
            eyeOverride = eye,
            tracksVisible = tracksVisible
        )

        // Colours on bands every stored track, and with a track open the scale follows *that* track's
        // fill: a gold selection takes the key away even though the others stay banded, while unselecting
        // gives it back. The chevrons do not enter this gate, so both arrows settings answer alike.
        listOf(true, false).forEach { arrows ->
            assertFalse(
                "Colours on (arrows=$arrows), the eye's gold selection — the open track's fill decides",
                gate(arrows, true, listOf("a", "b"), selectedId = "a", eye = false)
            )
            assertTrue(
                "Colours on (arrows=$arrows) with nothing selected",
                gate(arrows, true, listOf("a", "b"), selectedId = null, eye = null)
            )
            assertFalse(
                "Colours on (arrows=$arrows), the eye's gold selection alone",
                gate(arrows, true, listOf("a"), selectedId = "a", eye = false)
            )
            assertFalse(
                "Colours on (arrows=$arrows) with the tracks layer off",
                gate(arrows, true, listOf("a"), selectedId = "a", eye = null, tracksVisible = false)
            )
        }

        // An empty painted set hides it whatever the flags say — count 0, or every summary's detail
        // failed to load — and a selection the eye has banded hides too while it never landed a stroke:
        // the eye bands a stroke that exists, and an empty painted set holds none.
        assertFalse(
            "Colours on with nothing painted",
            gate(false, true, emptyList(), selectedId = "a", eye = null)
        )
        assertFalse(
            "Colours off, the eye banding a selection that never landed a stroke",
            gate(false, false, emptyList(), selectedId = "a", eye = true)
        )

        // With Colours off the eye is the only thing banding, and with that selection open the gate asks
        // for its fill alone: banded, so the scale is raised — the rule keys the open track's fill, not
        // the flag on its own.
        listOf(true, false).forEach { arrows ->
            assertTrue(
                "Colours off (arrows=$arrows), the eye banding the selection",
                gate(arrows, false, listOf("a"), selectedId = "a", eye = true)
            )
            assertFalse(
                "Colours off (arrows=$arrows), the eye set with nothing selected",
                gate(arrows, false, emptyList(), selectedId = null, eye = true)
            )
        }
    }

    // ── The eye's persisted value: what a tap writes ─────────────────────

    @Test
    fun theFirstTapTurnsTheFlagsOwnReadingAround() {
        // Null is the untouched eye — the selection mirrors the colours flag — so the tap reverses that
        // rather than flipping a value nobody wrote. Its answer is never null, which is what persists
        // the key.
        assertFalse(
            "Colours on: the selection is already banded, so the first tap turns the ramp off",
            selectionBandedAfterTap(null, true)
        )
        assertTrue(
            "Colours off: the selection is gold there, so the first tap bands it",
            selectionBandedAfterTap(null, false)
        )
    }

    @Test
    fun afterTheFirstTapTheFlagStopsReachingTheEye() {
        // A written value flips its own value, whatever the stored flag has become in the meantime.
        listOf(true, false).forEach { colours ->
            assertTrue("false under colours=$colours", selectionBandedAfterTap(false, colours))
            assertFalse("true under colours=$colours", selectionBandedAfterTap(true, colours))
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
