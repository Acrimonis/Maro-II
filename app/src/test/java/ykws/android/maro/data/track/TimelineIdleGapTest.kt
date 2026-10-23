package ykws.android.maro.data.track

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The idle timeline never reads idle across a GAP seam: the two real points either side of one are a
 * resume after the recorder was away, not a stretch the boat sat through.
 */
class TimelineIdleGapTest {

    private fun at(offsetMs: Long, type: PointType = PointType.NORMAL) =
        TrackPoint(lat = 43.5, lon = 7.0, speedMps = 0f, timeOffsetMs = offsetMs, type = type)

    @Test
    fun aStillStretchWithoutASeamIsIdle() {
        assertEquals(600L, timelineIdleSec(listOf(at(0), at(600_000))))
    }

    @Test
    fun theSameStretchAcrossAGapSeamIsNot() {
        assertEquals(0L, timelineIdleSec(listOf(at(0), at(1, PointType.GAP), at(600_000))))
    }

    @Test
    fun idleResumesCountingAfterTheSeam() {
        val points = listOf(at(0), at(1, PointType.GAP), at(600_000), at(660_000))
        assertEquals(60L, timelineIdleSec(points))
    }
}
