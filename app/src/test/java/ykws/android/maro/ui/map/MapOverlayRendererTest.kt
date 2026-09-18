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

    @Test
    fun everyZoneTypeResolvesAColour() {
        for (type in RegulatedZoneType.values()) {
            val color = regulatedZoneColor(type)
            // Only the RGB part reaches the map, so that is what must be present.
            assertTrue("$type resolved an empty colour", (color and 0x00FFFFFF) != 0)
        }
    }
}
