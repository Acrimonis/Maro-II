package ykws.android.maro.ui.map

import org.junit.Assert.*
import org.junit.Test
import ykws.android.maro.data.model.LatLng

/**
 * Unit tests for [dashboardPositionFor] — the single position the dashboard is computed for.
 *
 * Rule: GPS mode uses the boat; demo mode, or GPS before the first fix, uses the marker.
 * Design: `xTrack/Navigation/260916_FEAT_PLN_Navigation_dashboard-position-source.md` §4.7.
 */
class DashboardPositionTest {

    private val marker = LatLng(43.5, 7.0)
    private val boat = LatLng(43.6, 7.1)

    @Test
    fun gpsModeUsesTheBoat() {
        assertEquals(boat, dashboardPositionFor(marker = marker, boat = boat, gpsMode = true))
    }

    @Test
    fun gpsModeWithoutFixUsesTheMarker() {
        assertEquals(marker, dashboardPositionFor(marker = marker, boat = null, gpsMode = true))
    }

    @Test
    fun demoModeUsesTheMarkerEvenWithAFix() {
        assertEquals(marker, dashboardPositionFor(marker = marker, boat = boat, gpsMode = false))
    }
}
