package ykws.android.maro.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.track.PointType
import ykws.android.maro.data.track.TrackPoint

class TrackDirectionOverlayTest {

    private fun pt(
        lon: Double,
        lat: Double = 0.0,
        type: PointType = PointType.NORMAL,
        speedMps: Float? = null,
        bearingDeg: Float? = null
    ) = TrackPoint(
        lat = lat,
        lon = lon,
        speedMps = speedMps,
        bearingDeg = bearingDeg,
        timeOffsetSec = 0,
        timeOffsetMs = 0L,
        type = type
    )

    private val identityProject: (TrackPoint) -> ScreenPt = { p -> ScreenPt(p.lon.toFloat(), p.lat.toFloat()) }

    @Test
    fun uniformSpacingOnStraightLine() {
        val points = listOf(pt(0.0), pt(1000.0))
        val anchors = sampleArrowAnchors(points, identityProject, { 100f }, maxArrows = 100)
        assertEquals(10, anchors.size)
        assertTrue(anchors.all { it.segmentIndex == 0 })
        assertEquals(0.1f, anchors[0].t, 0.001f)
        assertEquals(1.0f, anchors[9].t, 0.001f)
    }

    @Test
    fun gapSegmentsAreSkipped() {
        val points = listOf(
            pt(0.0), pt(100.0),
            pt(100.0, type = PointType.GAP),
            pt(200.0), pt(300.0)
        )
        val anchors = sampleArrowAnchors(points, identityProject, { 50f }, maxArrows = 100)
        assertEquals(4, anchors.size)
        assertTrue(anchors.all { it.segmentIndex == 0 || it.segmentIndex == 3 })
    }

    @Test
    fun bearingFallsBackToSegmentVector() {
        val north = sampleArrowAnchors(listOf(pt(0.0, 0.0), pt(0.0, 1.0)), identityProject, { 1f }, 10)
        assertEquals(1, north.size)
        assertEquals(0f, north[0].bearingDeg, 0.001f)

        val east = sampleArrowAnchors(listOf(pt(0.0, 0.0), pt(1.0, 0.0)), identityProject, { 1f }, 10)
        assertEquals(1, east.size)
        assertEquals(90f, east[0].bearingDeg, 0.001f)
    }

    @Test
    fun recordedBearingIsPreferred() {
        val points = listOf(pt(0.0, 0.0, bearingDeg = 45f), pt(1.0, 0.0))
        val anchors = sampleArrowAnchors(points, identityProject, { 1f }, 10)
        assertEquals(1, anchors.size)
        assertEquals(45f, anchors[0].bearingDeg, 0.001f)
    }

    @Test
    fun spacingPxClampsAndInterpolates() {
        val floor = 3f
        val ceiling = 35f
        val min = 24f
        val max = 120f
        assertEquals(min, spacingPxForSpeed(0f, floor, ceiling, min, max), 0.001f)
        assertEquals(min, spacingPxForSpeed(3f, floor, ceiling, min, max), 0.001f)
        assertEquals(max, spacingPxForSpeed(35f, floor, ceiling, min, max), 0.001f)
        assertEquals(max, spacingPxForSpeed(50f, floor, ceiling, min, max), 0.001f)
        // Exponential midpoint: t = 0.5 → min × (max/min)^0.5
        val mid = min * kotlin.math.sqrt(max / min)
        assertEquals(mid, spacingPxForSpeed(19f, floor, ceiling, min, max), 0.001f)
    }

    @Test
    fun maxArrowsCapsOutput() {
        val points = listOf(pt(0.0), pt(1000.0))
        val anchors = sampleArrowAnchors(points, identityProject, { 10f }, maxArrows = 5)
        assertEquals(5, anchors.size)
    }
}
