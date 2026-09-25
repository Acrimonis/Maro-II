package ykws.android.maro.ui.map

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.MarkerGeometry
import ykws.android.maro.data.model.markers.MarkerOrigin
import ykws.android.maro.data.model.markers.UserMarker

/**
 * The 🧭 compass rule of the marker card header — [coordinateHeader].
 *
 * Asserted on the glyphs rather than the coordinates, because the numbers are formatted with the
 * default locale.
 */
class MarkerRoutingCostHeaderTest {

    private val pinGlyph = "\uD83D\uDCCD"
    private val compass = "\uD83E\uDDED"

    private fun marker(cost: Int?) = UserMarker(
        id = "m",
        name = "m",
        geometry = MarkerGeometry.Pin(LatLng(43.5, 7.2)),
        origin = MarkerOrigin.USER,
        routingCost = cost
    )

    @Test
    fun `no cost draws the geometry line alone`() {
        val header = coordinateHeader(marker(null))
        assertTrue(header.startsWith("["))
        assertTrue(header.endsWith(pinGlyph))
        assertFalse(header.contains(compass))
    }

    @Test
    fun `a cost appends the compass after the geometry glyph`() {
        assertTrue(coordinateHeader(marker(3)).endsWith("$pinGlyph $compass"))
    }

    @Test
    fun `an unset-reading value draws no compass`() {
        assertFalse(coordinateHeader(marker(0)).contains(compass))
        assertFalse(coordinateHeader(marker(10)).contains(compass))
    }
}
