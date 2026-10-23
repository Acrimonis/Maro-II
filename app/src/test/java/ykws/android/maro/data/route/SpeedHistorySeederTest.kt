package ykws.android.maro.data.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.track.PointType
import ykws.android.maro.data.track.TrackPoint
import ykws.android.maro.spatial.Units

class SpeedHistorySeederTest {

    private val noDistance: (Double, Double, Double, Double) -> Double = { _, _, _, _ -> 0.0 }
    private val nowhereRestricted: (Double, Double) -> Boolean = { _, _ -> false }

    private fun point(offsetMs: Long, kn: Double, lat: Double = 43.5, type: PointType = PointType.NORMAL) = TrackPoint(
        lat = lat,
        lon = 7.0,
        speedMps = (kn / Units.KNOTS_PER_MPS).toFloat(),
        timeOffsetMs = offsetMs,
        type = type
    )

    private fun tripOf(
        points: List<TrackPoint>,
        restricted: (Double, Double) -> Boolean = nowhereRestricted,
        stopKn: Double = 4.0,
        maxCoverSec: Double = 120.0
    ) = SpeedHistorySeeder.tripOf(points, 1_000L, false, noDistance, restricted, stopKn, maxCoverSec)

    @Test
    fun aTripIsItsNinetiethPercentileCruiseAndItsOpenWaterMinutes() {
        // Speeds 10,20,25,30,35 → the 90th percentile is 35; four 1-second intervals are covered.
        val trip = tripOf(
            listOf(
                point(0L, 10.0), point(1_000L, 20.0), point(2_000L, 25.0),
                point(3_000L, 30.0), point(4_000L, 35.0)
            )
        )
        assertNotNull(trip)
        // Stored speeds round-trip through Float, so the tolerance is the float's, not the math's.
        assertEquals(35.0, trip!!.speedKn, 1e-3)
        assertEquals(4.0 / 60.0, trip.openWaterMin, 1e-9)
        assertEquals(1_000L, trip.startTimeMs)
    }

    @Test
    fun tooFewReadingsYieldNull() {
        assertNull(tripOf(listOf(point(0L, 20.0), point(1_000L, 25.0))))
    }

    @Test
    fun readingsUnderTheStopSpeedAreNotCruising() {
        // Four fast readings and a long harbour crawl: the crawl leaves too few eligible readings.
        val points = (0 until 4).map { point(it * 1_000L, 30.0) } + (4 until 40).map { point(it * 1_000L, 3.2) }
        assertNull(tripOf(points))
        // And what the crawl would have covered is no open water.
        val withCruise = tripOf(points + (40 until 46).map { point(it * 1_000L, 30.0) })!!
        assertEquals(30.0, withCruise.speedKn, 1e-3)
        assertTrue(withCruise.openWaterMin < 10.0 / 60.0 + 1e-9)
    }

    @Test
    fun restrictedPointsAreDropped() {
        val points = (0 until 6).map { point(it * 1_000L, 30.0, lat = 43.5) } +
            (6 until 30).map { point(it * 1_000L, 5.0, lat = 43.6) }
        val trip = tripOf(points, restricted = { lat, _ -> lat > 43.55 })!!
        assertEquals(30.0, trip.speedKn, 1e-3)
    }

    @Test
    fun aGapSeamCoversNothing() {
        val points = (0 until 5).map { point(it * 1_000L, 30.0) } +
            point(5_000L, 30.0, type = PointType.GAP) + point(60_000L, 30.0)
        val trip = tripOf(points)!!
        // Four covered intervals; the seam and the point after it add none.
        assertEquals(4.0 / 60.0, trip.openWaterMin, 1e-9)
    }

    @Test
    fun aReadingNeverSpeaksPastTheCover() {
        val points = (0 until 5).map { point(it * 1_000L, 30.0) } + point(3_600_000L, 30.0)
        val trip = tripOf(points, maxCoverSec = 120.0)!!
        // Four 1-second intervals plus the last real interval, capped at two minutes.
        assertEquals((4.0 + 120.0) / 60.0, trip.openWaterMin, 1e-9)
    }

    @Test
    fun theKindFlowsThroughFromTheTrack() {
        val trip = SpeedHistorySeeder.tripOf(
            (0 until 6).map { point(it * 1_000L, 30.0) }, 7L, true, noDistance, nowhereRestricted
        )!!
        assertTrue(trip.demo)
        assertEquals(7L, trip.startTimeMs)
    }
}
