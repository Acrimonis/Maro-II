package ykws.android.maro.ui.map

import ykws.android.maro.data.track.PointType
import ykws.android.maro.data.track.TrackPoint

/**
 * One drawable polyline segment: the source-point indices to plot and whether it is a GAP bridge.
 *
 * @property pointIndices indices into the source point list, in draw order (a GAP bridge repeats its
 *                        endpoint when the gap is the final point, mirroring the legacy shell loop).
 * @property dashed       true for a GAP bridge (drawn with a dashed path effect).
 */
internal data class TrackSegment(val pointIndices: List<Int>, val dashed: Boolean)

/**
 * Split [points] at GAP markers into drawable segments: solid runs between gaps plus a dashed
 * two-point bridge for each gap. Pure (no Android dependencies) so the solid/dashed contract is
 * unit-testable; the osmdroid drawing itself stays in the Compose shell.
 */
internal fun splitTrackSegments(points: List<TrackPoint>): List<TrackSegment> {
    val segments = mutableListOf<TrackSegment>()
    var segmentStart = 0
    for (i in points.indices) {
        if (points[i].type != PointType.GAP) continue
        if (i > segmentStart && i - segmentStart >= 2) {
            segments += TrackSegment((segmentStart until i).toList(), dashed = false)
        }
        val gapEnd = if (i + 1 < points.size) i + 1 else i
        segments += TrackSegment(listOf(i, gapEnd), dashed = true)
        segmentStart = i + 1
    }
    if (segmentStart < points.size && points.size - segmentStart >= 2) {
        segments += TrackSegment((segmentStart until points.size).toList(), dashed = false)
    }
    return segments
}

/**
 * Build the osmdroid polylines for one track appearance from its solid/dashed segment plan — solid
 * for runs of real points, dashed for GAP bridges. The shell only decides *which* ids to draw.
 */
internal fun buildSegmentOverlays(
    points: List<TrackPoint>,
    appearance: TrackPolylineAppearance,
    title: String
): List<org.osmdroid.views.overlay.Overlay> = splitTrackSegments(points).map { segment ->
    org.osmdroid.views.overlay.Polyline().apply {
        this.title = title
        outlinePaint.color = appearance.argb
        outlinePaint.strokeWidth = appearance.strokeWidth
        if (segment.dashed) {
            outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(20f, 10f), 0f)
        }
        setPoints(segment.pointIndices.map { org.osmdroid.util.GeoPoint(points[it].lat, points[it].lon) })
    }
}
