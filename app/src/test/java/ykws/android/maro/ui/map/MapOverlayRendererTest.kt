package ykws.android.maro.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.regulation.RegulatedZoneType

/**
 * Unit tests for [transparencyPctToAlpha] and [regulatedZoneColor].
 *
 * Rule: transparency is 0 = opaque, 100 = invisible, and the derivation is shared by the 300 m band
 * and the regulated zones — the alpha pair is user-owned, so nothing is baked per zone type.
 * Design: `xTrack/Ui_Settings/260918_FEAT_PLN_Ui_Settings_regulated-zones-transparency.md`.
 */
class MapOverlayRendererTest {

    @Test
    fun transparencyZeroIsFullyOpaque() {
        assertEquals(255, transparencyPctToAlpha(0))
    }

    @Test
    fun transparencyHundredIsFullyInvisible() {
        assertEquals(0, transparencyPctToAlpha(100))
    }

    @Test
    fun shippedDefaultsMatchTheThreeHundredMetreBand() {
        assertEquals(204, transparencyPctToAlpha(20)) // boundary default
        assertEquals(51, transparencyPctToAlpha(80)) // fill default
    }

    @Test
    fun transparencyIsClampedOutsideThePercentageRange() {
        assertEquals(255, transparencyPctToAlpha(-5))
        assertEquals(0, transparencyPctToAlpha(140))
    }

    // ── The dp → px conversion the map's osmdroid paint sites go through ──

    @Test
    fun densityOneLeavesTheDpValueUntouched() {
        assertEquals(4f, dpToPx(4f, 1f), 0f)
    }

    @Test
    fun theThreeTimesReferenceDensityReproducesTheOldPxNumber() {
        // The shipped px numbers were chosen on a 3× device, so multiplying the dp back by 3 has to
        // land on them: 10 px of coastline is 3.333 dp, and 3.333 dp at 3× is 10 px again.
        assertEquals(10f, dpToPx(10f / 3f, 3f), 1e-4f)
        assertEquals(12f, dpToPx(4f, 3f), 1e-4f)
        assertEquals(16f, dpToPx(16f / 3f, 3f), 1e-4f)
    }

    @Test
    fun theConversionIsLinearInTheDensity() {
        // The whole point of the pass: a second density draws the same stroke at its own scale.
        assertEquals(4f, dpToPx(2f, 2f), 0f)
        assertEquals(2f, dpToPx(2f, 1f), 0f)
        assertEquals(dpToPx(3f, 2f) * 2f, dpToPx(3f, 4f), 0f)
    }

    @Test
    fun everyZoneTypeResolvesAColour() {
        for (type in RegulatedZoneType.values()) {
            val color = regulatedZoneColor(type)
            // Only the RGB part reaches the map, so that is what must be present.
            assertTrue("$type resolved an empty colour", (color and 0x00FFFFFF) != 0)
        }
    }
}
