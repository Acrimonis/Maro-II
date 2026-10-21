package ykws.android.maro.data.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ykws.android.maro.data.track.TrackPoint
import ykws.android.maro.spatial.Units

class SpeedHistorySeederTest {

    private val noDistance: (Double, Double, Double, Double) -> Double = { _, _, _, _ -> 0.0 }

    private fun point(offsetMs: Long, kn: Double) = TrackPoint(
        lat = 43.5,
        lon = 7.0,
        speedMps = (kn / Units.KNOTS_PER_MPS).toFloat(),
        timeOffsetMs = offsetMs
    )

    @Test
    fun seedCapsToTrackCountAndMedians() {
        val result = SpeedHistorySeeder.seed(
            speeds = listOf(20.0, 21.0, 22.0, 23.0, 24.0),
            trackCount = 3,
            recentWeightPct = 0.0
        )
        assertEquals(listOf(22.0, 23.0, 24.0), result.window)
        assertEquals(23.0, result.medianKn!!, 1e-9)
        assertEquals(3, result.usedTracks)
    }

    @Test
    fun trackCruiseSpeedIsTheWeightedQuantile() {
        // Speeds 10,20,25,30,35 → plain 75th percentile = 30.
        val points = listOf(
            point(0L, 10.0), point(1_000L, 20.0), point(2_000L, 25.0),
            point(3_000L, 30.0), point(4_000L, 35.0)
        )
        val cruise = SpeedHistorySeeder.trackCruiseSpeedKn(points, 0.0, noDistance)
        // Stored speeds round-trip through Float, so the tolerance is the float's, not the math's.
        assertEquals(30.0, cruise!!, 1e-3)
    }

    @Test
    fun tooFewPointsYieldNull() {
        val points = listOf(point(0L, 20.0), point(1_000L, 25.0))
        assertNull(SpeedHistorySeeder.trackCruiseSpeedKn(points, 0.0, noDistance))
    }
}
