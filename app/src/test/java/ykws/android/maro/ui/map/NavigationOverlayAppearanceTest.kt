package ykws.android.maro.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.config.HeatmapFamily
import ykws.android.maro.config.HeatmapRamp
import kotlin.math.sin

/**
 * The appearance primitives the heading line and the cap arrow now render from: the head derived from
 * the shaft and capped against the arrow it caps, the shaft's tangency inset, the ramp band lookup the
 * Speed Colour mode reads on every draw, and the Compose alpha sibling of the shared transparency
 * helper.
 *
 * Pure functions only, so the overlay maths is covered without a Compose or osmdroid harness.
 */
class NavigationOverlayAppearanceTest {

    private val green = 0xFF4CAF50.toInt()
    private val blue = 0xFF1E88E5.toInt()

    /** The beige the ramp carries for a speed it cannot quantify — the tint the stale arrow wears. */
    private val unknown = 0xFFF5F5DC.toInt()

    /** A pinned fixture: two flat families and one gradient, so a band read is distinguishable. */
    private val ramp = HeatmapRamp(
        families = listOf(
            HeatmapFamily(5f, green, green, 0.5f),   // flat compliant green to the 5 kn limit
            HeatmapFamily(10f, blue, blue, 0.5f),    // flat blue to the 10 kn limit
            HeatmapFamily(15f, green, blue, 1f)      // a gradient whose step makes the band visible
        ),
        unknownArgb = unknown
    )

    @Test
    fun theHeadIsDerivedFromTheShaftAtTheShippedRatio() {
        // 2.25 dp of shaft is the shipped 9 dp head, and the ratio holds at both ends of the 1–8 span.
        assertEquals(9f, capArrowHeadDp(2.25f), 0.0001f)
        assertEquals(4f, capArrowHeadDp(1f), 0.0001f)
        assertEquals(32f, capArrowHeadDp(8f), 0.0001f)
        assertEquals(4f, CAP_ARROW_HEAD_RATIO, 0f)
    }

    @Test
    fun theShaftInsetPutsTheCapInsideTheFlanksAtEveryWidth() {
        // The cap's centre stops the tangency depth below the apex, and the distance from that centre to
        // a flank is `inset × sin(halfSpread)`: the radius at exact tangency, above it by the margin.
        for (widthDp in listOf(1f, 2.25f, 8f)) {
            val insetDp = capArrowShaftInsetDp(widthDp)
            val radiusDp = widthDp / 2f
            val clearanceDp = insetDp * sin(CAP_ARROW_HALF_SPREAD)
            assertTrue(
                "cap of $widthDp dp shows beside the flanks: clearance $clearanceDp < radius $radiusDp",
                clearanceDp >= radiusDp
            )
            // The depth is `radius / sin(halfSpread)`, grown by the margin — never the tan form, which
            // at the shipped 2.25 dp stopped at 2.06 dp and left 0.14 dp of the cap outside.
            assertEquals(
                radiusDp / sin(CAP_ARROW_HALF_SPREAD) * CAP_ARROW_TANGENCY_MARGIN, insetDp, 0.0001f
            )
        }
        assertEquals(2.4638f, capArrowShaftInsetDp(2.25f), 0.0001f)
        // The inset does not move the head it is inscribed in: still the shipped 9 dp at 2.25 dp.
        assertEquals(9f, capArrowHeadDp(2.25f), 0.0001f)
    }

    @Test
    fun theHeadIsCappedAgainstTheArrowItCarries() {
        // The thickest shaft on the slowest drawn arrow (just past the 2.5 kn gate): 4 × 8 = 32 dp of
        // head against a ~5.6 dp arrow, a base far below the screen centre.
        val slowestDrawnDp = ((CAP_MIN_SPEED_KNOTS + 0.1f) * CAP_DP_PER_KNOT).toFloat()
        assertEquals(5.6333f, slowestDrawnDp, 0.001f)
        assertEquals(32f, capArrowHeadDp(8f), 0.0001f)
        // Capped, the head answers half the arrow instead of a head taller than it.
        assertEquals(slowestDrawnDp / 2f, capArrowHeadDp(8f, slowestDrawnDp), 0.0001f)
        assertTrue(capArrowHeadDp(8f, slowestDrawnDp) < slowestDrawnDp)
        // The ratio still governs wherever it fits — the shipped 9 dp at 2.25 dp on a full-length arrow.
        assertEquals(9f, capArrowHeadDp(2.25f, 65f), 0.0001f)
        // And the cap never answers more than half of the length it is given.
        assertEquals(1f, capArrowHeadDp(8f, 2f), 0.0001f)
    }

    @Test
    fun theBandLookupPaintsTheFamilyThatOwnsTheSpeed() {
        assertEquals(green, rampColorForSpeed(3f, ramp))
        assertEquals(blue, rampColorForSpeed(9f, ramp))
        // Past the ramp's domain the last family's colour stands, exactly as it does for the tracks.
        assertEquals(blue, rampColorForSpeed(40f, ramp))
    }

    @Test
    fun theBandLookupAnswersTheNeutralTintWhereNoBandApplies() {
        assertEquals(unknown, rampColorForSpeed(null, ramp))
        assertEquals(unknown, rampColorForSpeed(Float.NaN, ramp))
    }

    @Test
    fun theLookupReadsTheQuantisedBandRatherThanTheRawSpeed() {
        // 10.4 kn sits in the gradient family (10→15, step 1): the band snaps it up to 11, so the
        // lookup answers the band's colour and not a gradient sample of the raw speed.
        assertEquals(colorAt(11f, ramp), rampColorForSpeed(10.4f, ramp))
        assertNotEquals(colorAt(10.4f, ramp), rampColorForSpeed(10.4f, ramp))
    }

    @Test
    fun theComposeAlphaSiblingMirrorsTheSharedTransparencyHelper() {
        assertEquals(1f, transparencyPctToAlphaFraction(0), 0.0001f)
        assertEquals(0.3f, transparencyPctToAlphaFraction(70), 0.0001f)
        assertEquals(0f, transparencyPctToAlphaFraction(100), 0.0001f)

        // The same two ends as the packed-Int helper the osmdroid paints use, which is the point of
        // sitting the sibling beside it: one percentage semantics, two representations.
        assertEquals(255, transparencyPctToAlpha(0))
        assertEquals(0, transparencyPctToAlpha(100))
        assertEquals(transparencyPctToAlpha(50) / 255f, transparencyPctToAlphaFraction(50), 0.005f)

        // Both clamp, so a stored out-of-range percentage cannot paint past the ends.
        assertEquals(0f, transparencyPctToAlphaFraction(150), 0.0001f)
        assertEquals(1f, transparencyPctToAlphaFraction(-10), 0.0001f)
    }
}
