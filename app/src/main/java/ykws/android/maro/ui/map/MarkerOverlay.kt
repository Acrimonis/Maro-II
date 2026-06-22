package ykws.android.maro.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.MarkerGeometry
import ykws.android.maro.data.model.markers.UserMarker
import ykws.android.maro.spatial.SpatialOperations
import kotlin.math.*

/**
 * Proximity range preview colour — cyan #4FC3F7 at ~30% alpha.
 * Only shown for unconfirmed markers during creation/edit.
 */
private val PROXIMITY_PREVIEW_COLOR = Color(0x4D4FC3F7)

/** Earth radius in metres (WGS84 mean radius) — used for destination-point calculation. */
private const val EARTH_RADIUS_M = 6_371_000.0

/** Number of sample points on a circle circumference for dashed-circle rendering. */
private const val CIRCLE_SAMPLES = 72

/** Number of sample points along a corridor centerline for dashed parallel lines. */
private const val CORRIDOR_SAMPLES = 20

/**
 * Composable overlay that renders all user-defined markers as a Canvas layer
 * on the map. Renders **below** the boat marker and heading/speed arrow in z-order.
 *
 * Marker rendering (no name labels — UI review R7):
 * - **Pin:** filled dot at position
 * - **Circle:** dashed circle at [MarkerGeometry.Circle.radiusM], filled dot at center
 * - **Corridor:** two parallel dashed lines at ±[MarkerGeometry.Corridor.widthM]/2
 *   from the p1→p2 centerline, filled dots at p1 and p2
 *
 * Colour: [AppConfig.semanticInfo] when confirmed, [AppConfig.semanticCaution] when unconfirmed.
 *
 * **Proximity range preview** (UI review G4): during creation/edit ([UserMarker.confirmed] == false):
 * - Circle: second thinner dashed circle at radiusM × [proximityZoneMultiplier], cyan ~30%
 * - Corridor: parallel lines at ±(widthM × [proximityZoneMultiplier])/2, cyan ~30%
 * - Pin: no proximity preview (pin is a point)
 *
 * @param markers           List of all user markers to render.
 * @param mapView           The OSMdroid MapView for geo→pixel projection; null → nothing drawn.
 * @param proximityZoneMultiplier  Multiplier for proximity range preview (default 3.0 from maro.properties).
 * @param modifier          Compose modifier — should be [Modifier.fillMaxSize()].
 */
@Composable
fun MarkerOverlay(
    markers: List<UserMarker>,
    mapView: MapView?,
    proximityZoneMultiplier: Double = 3.0,
    modifier: Modifier = Modifier
) {
    val mv = mapView ?: return
    val projection = mv.projection

    Canvas(modifier = modifier) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        // Pixel density for dp→px conversion
        val density = 1f // Canvas already in px space

        for (marker in markers) {
            val confirmed = marker.confirmed
            val baseColor = if (confirmed)
                Color(AppConfig.semanticInfo)
            else
                Color(AppConfig.semanticCaution)

            when (val geom = marker.geometry) {
                is MarkerGeometry.Pin -> {
                    val dot = geoToPixel(geom.position, projection) ?: continue
                    // Skip if off-screen
                    if (!isOnScreen(dot, canvasWidth, canvasHeight)) continue

                    drawCircle(
                        color = baseColor,
                        radius = 5.dp.toPx(),
                        center = dot
                    )
                }

                is MarkerGeometry.Circle -> {
                    val centerPx = geoToPixel(geom.center, projection) ?: continue

                    // Draw dashed circle at radiusM
                    val circlePoints = sampleCircle(
                        geom.center, geom.radiusM, projection, CIRCLE_SAMPLES
                    )
                    if (circlePoints.size >= 2) {
                        drawCirclePolyline(circlePoints, baseColor, 2.dp.toPx())
                    }

                    // Filled dot at center (if on screen)
                    if (isOnScreen(centerPx, canvasWidth, canvasHeight)) {
                        drawCircle(
                            color = baseColor,
                            radius = 5.dp.toPx(),
                            center = centerPx
                        )
                    }

                    // Proximity range preview for unconfirmed circles
                    if (!confirmed) {
                        val previewRadiusM = geom.radiusM * proximityZoneMultiplier
                        val previewPoints = sampleCircle(
                            geom.center, previewRadiusM, projection, CIRCLE_SAMPLES
                        )
                        if (previewPoints.size >= 2) {
                            drawCirclePolyline(
                                previewPoints,
                                PROXIMITY_PREVIEW_COLOR,
                                1.dp.toPx()
                            )
                        }
                    }
                }

                is MarkerGeometry.Corridor -> {
                    val p1Px = geoToPixel(geom.p1, projection) ?: continue
                    val p2Px = geoToPixel(geom.p2, projection) ?: continue

                    val halfW = geom.widthM / 2.0

                    // Compute the perpendicular offset direction in screen space
                    val dx = p2Px.x - p1Px.x
                    val dy = p2Px.y - p1Px.y
                    val segLenPx = sqrt(dx * dx + dy * dy)
                    if (segLenPx == 0f) continue

                    // Unit perpendicular (rotated 90° CCW)
                    val perpX = -dy / segLenPx
                    val perpY = dx / segLenPx

                    // Sample points along centerline in geo space, convert to pixel
                    val geoPoints = sampleCorridorCenterline(geom.p1, geom.p2, CORRIDOR_SAMPLES)
                    val centerPxList = geoPoints.mapNotNull { geoToPixel(it, projection) }
                    if (centerPxList.size < 2) continue

                    // Compute offset for half-width in pixels (approximate: use midpoint scale)
                    val midIdx = centerPxList.size / 2
                    val midPx = centerPxList[midIdx]
                    val midGeo = geoPoints[midIdx]
                    val widthPx = metersToPixels(geom.widthM, midGeo, projection, segLenPx,
                        SpatialOperations.haversine(geom.p1, geom.p2))
                    val halfWPx = widthPx / 2f

                    // Draw two parallel dashed lines at ±half-width
                    val line1 = centerPxList.map { Offset(it.x + perpX * halfWPx, it.y + perpY * halfWPx) }
                    val line2 = centerPxList.map { Offset(it.x - perpX * halfWPx, it.y - perpY * halfWPx) }

                    drawPolyline(line1, baseColor, 2.dp.toPx())
                    drawPolyline(line2, baseColor, 2.dp.toPx())

                    // Filled dots at p1 and p2
                    if (isOnScreen(p1Px, canvasWidth, canvasHeight)) {
                        drawCircle(color = baseColor, radius = 5.dp.toPx(), center = p1Px)
                    }
                    if (isOnScreen(p2Px, canvasWidth, canvasHeight)) {
                        drawCircle(color = baseColor, radius = 5.dp.toPx(), center = p2Px)
                    }

                    // Proximity range preview for unconfirmed corridors
                    if (!confirmed) {
                        val previewHalfWPx = halfWPx * proximityZoneMultiplier.toFloat()
                        val previewLine1 = centerPxList.map {
                            Offset(it.x + perpX * previewHalfWPx, it.y + perpY * previewHalfWPx)
                        }
                        val previewLine2 = centerPxList.map {
                            Offset(it.x - perpX * previewHalfWPx, it.y - perpY * previewHalfWPx)
                        }
                        drawPolyline(previewLine1, PROXIMITY_PREVIEW_COLOR, 1.dp.toPx())
                        drawPolyline(previewLine2, PROXIMITY_PREVIEW_COLOR, 1.dp.toPx())
                    }
                }
            }
        }
    }
}

// ── Geo ↔ pixel helpers ─────────────────────────────────────────────────────────

/** Convert a [LatLng] to a Compose [Offset] (pixel position on the Canvas). */
private fun geoToPixel(point: LatLng, projection: org.osmdroid.views.Projection): Offset? {
    return try {
        val pt = android.graphics.Point()
        projection.toPixels(GeoPoint(point.latitude, point.longitude), pt)
        Offset(pt.x.toFloat(), pt.y.toFloat())
    } catch (_: Exception) {
        null
    }
}

/** True if [pt] is within the screen bounds (with some margin). */
private fun isOnScreen(pt: Offset, canvasW: Float, canvasH: Float): Boolean {
    val margin = 50f
    return pt.x >= -margin && pt.x <= canvasW + margin &&
           pt.y >= -margin && pt.y <= canvasH + margin
}

/**
 * Approximate conversion from metres to pixels at a given geographic position.
 * Uses the inverse of the scale at [refGeo] computed from the projection.
 */
private fun metersToPixels(
    meters: Double,
    refGeo: LatLng,
    projection: org.osmdroid.views.Projection,
    fallbackSegLenPx: Float,
    fallbackSegLenM: Double
): Float {
    // Use the ratio: pixelsPerMeter = segmentLengthPx / segmentLengthM
    return if (fallbackSegLenM > 0.0) {
        (meters * fallbackSegLenPx / fallbackSegLenM).toFloat()
    } else {
        // Fallback: use projection scale at reference point
        val pt = android.graphics.Point()
        val dLat = meters / (EARTH_RADIUS_M * PI / 180.0)
        try {
            projection.toPixels(
                GeoPoint(refGeo.latitude + dLat, refGeo.longitude),
                pt
            )
            val pt0 = android.graphics.Point()
            projection.toPixels(
                GeoPoint(refGeo.latitude, refGeo.longitude),
                pt0
            )
            abs(pt.y - pt0.y).toFloat()
        } catch (_: Exception) {
            0f
        }
    }
}

// ── Geometry sampling ────────────────────────────────────────────────────────────

/**
 * Sample [count] points on a circle of radius [radiusM] around [center].
 * Returns screen-space [Offset] list.
 */
private fun sampleCircle(
    center: LatLng,
    radiusM: Double,
    projection: org.osmdroid.views.Projection,
    count: Int
): List<Offset> {
    val result = mutableListOf<Offset>()
    for (i in 0 until count) {
        val bearingDeg = i * 360.0 / count
        val pt = destinationPoint(center, radiusM, bearingDeg)
        val px = geoToPixel(pt, projection) ?: continue
        result.add(px)
    }
    return result
}

/**
 * Sample [count] evenly-spaced points along the centerline from [p1] to [p2]
 * (inclusive of endpoints). Returns geographic [LatLng] positions.
 */
private fun sampleCorridorCenterline(
    p1: LatLng,
    p2: LatLng,
    count: Int
): List<LatLng> {
    val result = mutableListOf<LatLng>()
    for (i in 0 until count) {
        val t = i.toDouble() / (count - 1)
        result.add(LatLng(
            p1.latitude + (p2.latitude - p1.latitude) * t,
            p1.longitude + (p2.longitude - p1.longitude) * t
        ))
    }
    return result
}

/**
 * Compute the destination point given a start point, distance in metres,
 * and bearing in degrees (0° = north, clockwise).
 */
private fun destinationPoint(start: LatLng, distanceM: Double, bearingDeg: Double): LatLng {
    val dR = distanceM / EARTH_RADIUS_M
    val br = Math.toRadians(bearingDeg)
    val lat1 = Math.toRadians(start.latitude)
    val lon1 = Math.toRadians(start.longitude)

    val lat2 = asin(sin(lat1) * cos(dR) + cos(lat1) * sin(dR) * cos(br))
    val lon2 = lon1 + atan2(sin(br) * sin(dR) * cos(lat1), cos(dR) - sin(lat1) * sin(lat2))

    return LatLng(Math.toDegrees(lat2), Math.toDegrees(lon2))
}

// ── Canvas drawing helpers ──────────────────────────────────────────────────────

/** Draw a polyline from a list of pixel offsets with a dashed stroke. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPolyline(
    points: List<Offset>,
    color: Color,
    strokeWidth: Float
) {
    if (points.size < 2) return
    val path = androidx.compose.ui.graphics.Path().apply {
        moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) {
            lineTo(points[i].x, points[i].y)
        }
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
        )
    )
}

/** Draw a closed circular polyline (for dashed circle rendering). */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCirclePolyline(
    points: List<Offset>,
    color: Color,
    strokeWidth: Float
) {
    if (points.size < 3) return
    val path = androidx.compose.ui.graphics.Path().apply {
        moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) {
            lineTo(points[i].x, points[i].y)
        }
        close()
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
        )
    )
}

