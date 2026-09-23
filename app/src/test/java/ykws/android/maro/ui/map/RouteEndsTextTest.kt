package ykws.android.maro.ui.map

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import ykws.android.maro.data.model.RoutePoint

/**
 * **The panel's data table: how a coordinate is printed.**
 *
 * The two ends share one row, so that row's own width is what sets the precision — three decimals — and
 * the separator stays a dot whatever the device's locale says. The pair's print is the contract: a
 * coordinate that gets cut is wrong data rather than a shortened one, which is why the value is sized
 * to fit its row and the destination's note is drawn under the table instead of inside the value.
 */
class RouteEndsTextTest {

    private val start = RoutePoint(43.1234, 7.1234)
    private val destination = RoutePoint(43.5678, 7.5678)

    @Test
    fun `a coordinate is printed to three decimals whatever the locale`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.FRANCE)
            assertEquals("43.123, 7.123", routeCoordinate(start))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `three decimals are kept as three, padded rather than trimmed`() {
        assertEquals("43.120, 7.000", routeCoordinate(RoutePoint(43.12, 7.0)))
    }

    @Test
    fun `the destination is printed exactly as the start is`() {
        assertEquals("43.568, 7.568", routeCoordinate(destination))
    }
}
