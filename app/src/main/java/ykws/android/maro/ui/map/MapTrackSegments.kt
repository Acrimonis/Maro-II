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
 * The GAP bridge's dash, in dp: two values rather than a ratio of the stroke, because the stroke it
 * rides varies by track class — a ratio would change the drawing as the class changed. The caller
 * converts them with the same density it converts the stroke with.
 */
internal const val TRACK_GAP_DASH_ON_DP = 20f / 3f
internal const val TRACK_GAP_DASH_OFF_DP = 10f / 3f

/**
 * Build the osmdroid polylines for one track appearance from its solid/dashed segment plan — solid
 * for runs of real points, dashed for GAP bridges. The shell only decides *which* ids to draw.
 *
 * [appearance] carries its width in dp, as the stored table does, so [density] is what turns it — and
 * the dash above — into the px osmdroid's paint takes.
 */
internal fun buildSegmentOverlays(
    points: List<TrackPoint>,
    appearance: TrackPolylineAppearance,
    title: String,
    density: Float
): List<org.osmdroid.views.overlay.Overlay> =
    segmentOverlays(points, splitTrackSegments(points), appearance, title, density)

/**
 * Build the osmdroid polylines for **one speed band's own geometry** — the banded twin of
 * [buildSegmentOverlays], so the banded path never loops an appearance over a full point list.
 *
 * GAP points inside the band still split into a dashed bridge, so a seam is drawn once, by the band
 * that owns it. The [band]'s own indices are all this reads: a band is a set of runs, and the caller
 * keeps one title per track across every band.
 */
internal fun buildBandSegmentOverlays(
    points: List<TrackPoint>,
    band: SpeedBand,
    title: String,
    density: Float
): List<org.osmdroid.views.overlay.Overlay> {
    val bandPoints = band.pointIndices.map { points[it] }
    return segmentOverlays(bandPoints, drawableBandSegments(bandPoints), band.appearance, title, density)
}

/**
 * The banded path's segment plan: [splitTrackSegments] with its zero-length dashed bridges dropped.
 *
 * A band whose geometry ends on its GAP point splits into a bridge repeating that single point, and
 * the point it would bridge to belongs to the next band — so the band with the real continuation
 * draws the visible seam and this one skips the slot instead of adding an invisible polyline.
 */
internal fun drawableBandSegments(points: List<TrackPoint>): List<TrackSegment> =
    splitTrackSegments(points)
        .filterNot { it.dashed && it.pointIndices.first() == it.pointIndices.last() }

/** Shared polyline construction: one polyline per segment, over [source], at [density]'s px. */
private fun segmentOverlays(
    source: List<TrackPoint>,
    segments: List<TrackSegment>,
    appearance: TrackPolylineAppearance,
    title: String,
    density: Float
): List<org.osmdroid.views.overlay.Overlay> = segments.map { segment ->
    org.osmdroid.views.overlay.Polyline().apply {
        this.title = title
        outlinePaint.color = appearance.argb
        // The appearance's width is dp, like every stored width since 2026-09-19: the conversion is
        // the caller's density applied here, where the paint is written.
        outlinePaint.strokeWidth = dpToPx(appearance.strokeWidth, density)
        if (segment.dashed) {
            outlinePaint.pathEffect = android.graphics.DashPathEffect(
                floatArrayOf(
                    dpToPx(TRACK_GAP_DASH_ON_DP, density),
                    dpToPx(TRACK_GAP_DASH_OFF_DP, density)
                ),
                0f
            )
        }
        setPoints(segment.pointIndices.map { org.osmdroid.util.GeoPoint(source[it].lat, source[it].lon) })
    }
}
