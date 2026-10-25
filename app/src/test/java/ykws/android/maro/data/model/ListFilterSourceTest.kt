package ykws.android.maro.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.track.TrackSummary

/**
 * The **Source** axis: GPS vs Demo on recorded tracks — a track is GPS when any point carried an
 * accuracy, demo when none did — with routes and the live recording exempt.
 */
class ListFilterSourceTest {

    private fun summary(gps: Boolean = false, route: Boolean = false, live: Boolean = false) =
        TrackSummary(id = "s", name = "s", startTimeMs = 0L, route = route, gpsSourced = gps).also { it.isLive = live }

    private fun filter(value: String) = ListFilter(mapOf("source" to value))

    @Test
    fun gpsKeepsOnlyTracksWithAnAccuracyedPoint() {
        assertTrue(summary(gps = true).matchesFilter(filter("GPS"), 0L))
        assertFalse(summary(gps = false).matchesFilter(filter("GPS"), 0L))
    }

    @Test
    fun demoKeepsOnlyTracksWithNoAccuracyAnywhere() {
        assertTrue(summary(gps = false).matchesFilter(filter("DEMO"), 0L))
        assertFalse(summary(gps = true).matchesFilter(filter("DEMO"), 0L))
    }

    @Test
    fun allAndAnUnknownValueKeepEverything() {
        assertTrue(summary(gps = true).matchesFilter(filter("ALL"), 0L))
        assertTrue(summary(gps = false).matchesFilter(filter("ALL"), 0L))
    }

    @Test
    fun aRoutePassesWhateverTheValue() {
        assertTrue(summary(route = true).matchesFilter(filter("GPS"), 0L))
        assertTrue(summary(route = true).matchesFilter(filter("DEMO"), 0L))
    }

    @Test
    fun theLiveRecordingPassesWhateverTheValue() {
        assertTrue(summary(gps = false, live = true).matchesFilter(filter("GPS"), 0L))
        assertTrue(summary(gps = true, live = true).matchesFilter(filter("DEMO"), 0L))
    }

    @Test
    fun theRouteListOffersNeitherSourceNorPositionAndTheTrackListOffersBoth() {
        assertTrue(trackFilterAxes().any { it.key == "source" })
        assertTrue(trackFilterAxes().any { it.key == "position" })
        assertEquals(listOf("dateRange", "pinned"), routeFilterAxes().map { it.key })
    }
}
