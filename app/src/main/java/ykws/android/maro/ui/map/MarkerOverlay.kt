package ykws.android.maro.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.MarkerGeometry
import ykws.android.maro.data.model.markers.UserMarker
import ykws.android.maro.spatial.SpatialOperations
import ykws.android.maro.spatial.MatchResult
import ykws.android.maro.spatial.TieredMatchResult
import kotlin.math.*

// ── Constants ─────────────────────────────────────────────────────────────────

/** Earth radius in metres (WGS84 mean radius). */
private const val EARTH_RADIUS_M = 6_371_000.0

/** Number of sample points on a circle circumference. */
private const val CIRCLE_SAMPLES = 72

/** Number of sample points along a corridor centerline. */
private const val CORRIDOR_SAMPLES = 20

/** OSMdroid overlay title prefix for identification/cleanup. */
private const val OVERLAY_PREFIX = "marker_"

/** Marker dot radius in dp (converted to px at ~48dp density). */
private const val DOT_RADIUS_DP = 6

/** Geo-distance threshold (metres) for tap-to-select on map. */
private const val TAP_THRESHOLD_M = 50.0

/** Confirmed marker colour (semantic.info blue). */
private val COLOR_CONFIRMED = AppConfig.semanticInfo

/** Unconfirmed marker colour (semantic.caution amber). */
private val COLOR_UNCONFIRMED = AppConfig.semanticCaution

/** Proximity preview colour — cyan #4FC3F7 at ~50% alpha (was 30%, too faint with dash). */
private val COLOR_PROXIMITY_PREVIEW = 0x804FC3F7.toInt()

/** Alpha for dimmed (non-matched) markers during match-result highlighting (30%). */
private const val DIMMED_ALPHA_FRACTION = 0.30f

/** Brighter stroke multiplier for matched markers (3dp → 5dp ≈ 1.67×). */
private const val MATCHED_STROKE_MULTIPLIER = 1.67f

// ── Public composable ─────────────────────────────────────────────────────────

/**
 * Composable that renders user-defined markers as OSMdroid native overlays
 * ([Polyline] + [Marker]) added to [mapView.overlays] via [LaunchedEffect].
 *
 * Marker rendering:
 * - **Pin:** [Marker] at geo position with a filled-circle icon.
 * - **Circle:** [Polyline] as closed polygon (72 pts) + center [Marker].
 * - **Corridor:** Two [Polyline]s for parallel lines at ±width/2,
 *   one [Polyline] for centerline (thinner), [Marker] at p1 and p2.
 *
 * **Proximity preview:** thinner [Polyline] for unconfirmed markers only,
 * drawn in cyan at lower alpha.
 *
 * **Marker tap (P10):** Registers a [MapEventsOverlay] with
 * [onSingleTapConfirmedHelper]; on tap, finds the nearest confirmed Pin marker
 * within [TAP_THRESHOLD_M] and calls [onMarkerTap] with its ID.
 *
 * **Match result highlighting (P6):** When [matchResult] is non-null, matched
 * markers render brighter/thicker; non-matched markers render dimmed (lower alpha).
 *
 * Lifecycle: [LaunchedEffect] removes old overlays tagged with [OVERLAY_PREFIX],
 * adds new ones, then calls [MapView.invalidate].
 *
 * @param markers                List of all confirmed user markers to render.
 * @param mapView                The OSMdroid [MapView]; null → nothing drawn.
 * @param proximityZoneMultiplier Multiplier for proximity range preview.
 * @param modifier               Compose modifier (unused — overlays go to mapView).
 * @param unconfirmedMarker      Optional unconfirmed marker being created/edited.
 * @param onMarkerTap            Called when a confirmed marker is tapped on the map.
 * @param matchResult            Optional tiered match result for marker highlighting.
 */
@Composable
fun MarkerOverlay(
    markers: List<UserMarker>,
    mapView: MapView?,
    proximityZoneMultiplier: Double = 3.0,
    modifier: Modifier = Modifier,
    unconfirmedMarker: UserMarker? = null,
    onMarkerTap: (String) -> Unit = {},
    matchResult: TieredMatchResult? = null,
    markerZonesVisible: Boolean = true
) {
    val mv = mapView ?: return
    val context = LocalContext.current

    // Helper to remove all marker overlays
    fun removeAllMarkerOverlays() {
        val toRemove = mv.overlays.filter { overlay ->
            (overlay as? Polyline)?.title?.startsWith(OVERLAY_PREFIX) == true ||
            (overlay as? Marker)?.title?.startsWith(OVERLAY_PREFIX) == true
        }
        mv.overlays.removeAll(toRemove)
    }

    // ── P6: Build set of matched marker IDs for highlighting ──────────────────
    val matchedIds: Set<String> = if (matchResult != null) {
        val ids = mutableSetOf<String>()
        fun collectIds(results: List<MatchResult>) {
            for (r in results) {
                when (r) {
                    is MatchResult.ZoneMatch -> {
                        ids.add(r.marker.id)
                        collectIds(r.children)
                    }
                    is MatchResult.ProximityMatch -> ids.add(r.marker.id)
                    is MatchResult.NoMatch -> {}
                }
            }
        }
        collectIds(matchResult.matches)
        ids
    } else emptySet()

    DisposableEffect(markers, unconfirmedMarker, mv, matchResult) {
        // ── Remove old marker overlays, then add new ones ─────────────────────
        removeAllMarkerOverlays()

        // Build combined marker list: unconfirmed first (renders underneath)
        // so the original confirmed marker stays visible on top during edit.
        val allMarkers = if (unconfirmedMarker != null) {
            listOf(unconfirmedMarker) + markers
        } else {
            markers
        }

        val dotBitmap = createDotBitmap(COLOR_CONFIRMED)

        for (marker in allMarkers) {
            val confirmed = marker.confirmed
            val isMatched = matchResult != null && matchedIds.contains(marker.id)
            // Unconfirmed: always unconfirmed colour. Confirmed + not matched + has result: dim.
            val baseColor = when {
                !confirmed -> COLOR_UNCONFIRMED
                matchResult != null && !isMatched -> dimColor(COLOR_CONFIRMED, DIMMED_ALPHA_FRACTION)
                else -> COLOR_CONFIRMED
            }
            val strokeMultiplier = if (matchResult != null && isMatched) MATCHED_STROKE_MULTIPLIER else 1.0f

            // Zone shapes gated by markerZonesVisible for confirmed markers;
            // unconfirmed (creating/editing) always show full geometry.
            val drawZones = !confirmed || markerZonesVisible

            when (val geom = marker.geometry) {
                is MarkerGeometry.Pin -> {
                    addPinOverlay(mv, geom, marker.id, baseColor, dotBitmap)

                    // Proximity range preview for unconfirmed pins
                    if (!confirmed) {
                        val previewRadiusM = marker.proximityOverrideM
                            ?: AppConfig.markerProximityPinM
                        addCirclePolyline(
                            mv, geom.position, previewRadiusM,
                            "$OVERLAY_PREFIX${marker.id}_prox",
                            COLOR_PROXIMITY_PREVIEW, 2f,
                            dashed = false
                        )
                    }
                }

                is MarkerGeometry.Circle -> {
                    if (drawZones) {
                        addCircleOverlay(mv, geom, marker.id, baseColor, dotBitmap, strokeMultiplier)
                    } else {
                        // Center dot only
                        addPinOverlay(mv, MarkerGeometry.Pin(geom.center), marker.id, baseColor, dotBitmap)
                    }

                    // Proximity range preview for unconfirmed circles
                    if (!confirmed) {
                        val previewRadiusM = marker.proximityOverrideM
                            ?: (geom.radiusM * proximityZoneMultiplier)
                        addCirclePolyline(
                            mv, geom.center, previewRadiusM,
                            "$OVERLAY_PREFIX${marker.id}_prox",
                            COLOR_PROXIMITY_PREVIEW, 2f,
                            dashed = false
                        )
                    }
                }

                is MarkerGeometry.Corridor -> {
                    if (drawZones) {
                        addCorridorOverlay(mv, geom, marker.id, baseColor, dotBitmap, confirmed, strokeMultiplier)
                    } else {
                        // p1/p2 dots only
                        addPinOverlay(mv, MarkerGeometry.Pin(geom.p1), "${marker.id}_p1", baseColor, dotBitmap)
                        addPinOverlay(mv, MarkerGeometry.Pin(geom.p2), "${marker.id}_p2", baseColor, dotBitmap)
                    }

                    // Proximity range preview for unconfirmed corridors
                    if (!confirmed) {
                        val proximityM = marker.proximityOverrideM
                            ?: (geom.widthM * proximityZoneMultiplier)
                        val halfProx = proximityM / 2.0
                        // Parallel lines at ±halfProx (dashed stadium shape)
                        addCorridorParallels(
                            mv, geom.p1, geom.p2, halfProx,
                            "$OVERLAY_PREFIX${marker.id}_prox",
                            COLOR_PROXIMITY_PREVIEW, 2f,
                            dashed = true
                        )
                        // Circular endcaps at p1 and p2 (rounded ends of the proximity stadium)
                        addCirclePolyline(
                            mv, geom.p1, halfProx,
                            "$OVERLAY_PREFIX${marker.id}_prox_cap_p1",
                            COLOR_PROXIMITY_PREVIEW, 2f,
                            dashed = true
                        )
                        addCirclePolyline(
                            mv, geom.p2, halfProx,
                            "$OVERLAY_PREFIX${marker.id}_prox_cap_p2",
                            COLOR_PROXIMITY_PREVIEW, 2f,
                            dashed = true
                        )
                    }
                }
            }
        }

        // ── P10: MapEventsOverlay for tap-to-select confirmed markers ──────────
        val tapOverlay = MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                val tapPoint = LatLng(p.latitude, p.longitude)
                var nearestId: String? = null
                var nearestDist = Double.MAX_VALUE

                for (marker in markers) {
                    if (!marker.confirmed) continue
                    val markerPos = when (val g = marker.geometry) {
                        is MarkerGeometry.Pin -> g.position
                        is MarkerGeometry.Circle -> g.center
                        is MarkerGeometry.Corridor -> LatLng(
                            (g.p1.latitude + g.p2.latitude) / 2.0,
                            (g.p1.longitude + g.p2.longitude) / 2.0
                        )
                    }
                    val dist = SpatialOperations.haversine(tapPoint, markerPos)
                    if (dist < nearestDist && dist < TAP_THRESHOLD_M) {
                        nearestDist = dist
                        nearestId = marker.id
                    }
                }

                nearestId?.let { onMarkerTap(it) }
                return nearestId != null // true = consumed, false = propagate
            }

            override fun longPressHelper(p: GeoPoint): Boolean = false
        })
        mv.overlays.add(tapOverlay)

        mv.invalidate()

        onDispose {
            removeAllMarkerOverlays()
            // Remove the tap overlay
            mv.overlays.removeAll { it is MapEventsOverlay }
            mv.invalidate()
        }
    }
}

// ── Overlay builders ──────────────────────────────────────────────────────────

/** Add a pin [Marker] at [geom.position]. */
private fun addPinOverlay(
    mv: MapView,
    geom: MarkerGeometry.Pin,
    markerId: String,
    color: Int,
    dotBitmap: Bitmap
) {
    val geo = GeoPoint(geom.position.latitude, geom.position.longitude)
    val marker = Marker(mv).apply {
        position = geo
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        icon = BitmapDrawable(mv.context.resources, if (color == COLOR_CONFIRMED) dotBitmap else createDotBitmap(color))
        title = "${OVERLAY_PREFIX}pin_$markerId"
    }
    mv.overlays.add(marker)
}

/** Add a circle [Polyline] (closed polygon) + center [Marker]. */
private fun addCircleOverlay(
    mv: MapView,
    geom: MarkerGeometry.Circle,
    markerId: String,
    color: Int,
    dotBitmap: Bitmap,
    strokeMultiplier: Float = 1.0f
) {
    // Circle outline as closed Polyline
    val strokeW = 4f * strokeMultiplier
    addCirclePolyline(mv, geom.center, geom.radiusM, "${OVERLAY_PREFIX}circle_$markerId", color, strokeW)

    // Center dot
    val centerGeo = GeoPoint(geom.center.latitude, geom.center.longitude)
    val centerMarker = Marker(mv).apply {
        position = centerGeo
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        icon = BitmapDrawable(mv.context.resources, if (color == COLOR_CONFIRMED) dotBitmap else createDotBitmap(color))
        title = "${OVERLAY_PREFIX}circle_center_$markerId"
    }
    mv.overlays.add(centerMarker)
}

/** Add corridor overlays: two parallel lines, centerline, p1/p2 markers. */
private fun addCorridorOverlay(
    mv: MapView,
    geom: MarkerGeometry.Corridor,
    markerId: String,
    color: Int,
    dotBitmap: Bitmap,
    confirmed: Boolean,
    strokeMultiplier: Float = 1.0f
) {
    val halfW = geom.widthM / 2.0

    // Centerline (thinner, solid for confirmed, dashed for unconfirmed)
    val centerline = buildPolyline(
        sampleCenterline(geom.p1, geom.p2, CORRIDOR_SAMPLES),
        "${OVERLAY_PREFIX}corr_center_$markerId",
        color and 0x00FFFFFF or 0x80000000.toInt(), // 50% alpha
        2f * strokeMultiplier
    )
    if (confirmed) {
        centerline.outlinePaint.pathEffect = null // solid
    }
    mv.overlays.add(centerline)

    // Two parallel lines at ±halfW
    addCorridorParallels(mv, geom.p1, geom.p2, halfW, "${OVERLAY_PREFIX}corr_$markerId", color, 4f * strokeMultiplier)

    // p1 Marker
    val p1Geo = GeoPoint(geom.p1.latitude, geom.p1.longitude)
    mv.overlays.add(Marker(mv).apply {
        position = p1Geo
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        icon = BitmapDrawable(mv.context.resources, if (color == COLOR_CONFIRMED) dotBitmap else createDotBitmap(color))
        title = "${OVERLAY_PREFIX}corr_p1_$markerId"
    })

    // p2 Marker
    val p2Geo = GeoPoint(geom.p2.latitude, geom.p2.longitude)
    mv.overlays.add(Marker(mv).apply {
        position = p2Geo
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        icon = BitmapDrawable(mv.context.resources, if (color == COLOR_CONFIRMED) dotBitmap else createDotBitmap(color))
        title = "${OVERLAY_PREFIX}corr_p2_$markerId"
    })
}

// ── Polyline helpers ──────────────────────────────────────────────────────────

/** Add a circle as a closed [Polyline] with [radiusM] around [center]. */
private fun addCirclePolyline(
    mv: MapView,
    center: LatLng,
    radiusM: Double,
    title: String,
    color: Int,
    strokeWidth: Float,
    dashed: Boolean = true
) {
    val points = sampleCircle(center, radiusM, CIRCLE_SAMPLES)
    if (points.size < 3) return
    // Close the polygon by appending the first point
    val closed = points + points.first()
    val polyline = buildPolyline(closed, title, color, strokeWidth, dashed)
    mv.overlays.add(polyline)
}

/** Add two parallel [Polyline]s offset by [halfWidthM] from the centerline. */
private fun addCorridorParallels(
    mv: MapView,
    p1: LatLng,
    p2: LatLng,
    halfWidthM: Double,
    titleBase: String,
    color: Int,
    strokeWidth: Float,
    dashed: Boolean = true
) {
    val centerPts = sampleCenterline(p1, p2, CORRIDOR_SAMPLES)
    if (centerPts.size < 2) return

    val bearing = SpatialOperations.initialBearing(p1, p2)
    val perpLeft = (bearing + 90.0) % 360.0
    val perpRight = (bearing - 90.0 + 360.0) % 360.0

    val leftPts = centerPts.map { destinationPoint(it, halfWidthM, perpLeft) }
    val rightPts = centerPts.map { destinationPoint(it, halfWidthM, perpRight) }

    mv.overlays.add(buildPolyline(leftPts, "${titleBase}_left", color, strokeWidth, dashed))
    mv.overlays.add(buildPolyline(rightPts, "${titleBase}_right", color, strokeWidth, dashed))
}

/** Build a [Polyline] with the given [geoPoints], [title], [color], and [strokeWidth].
 *  [dashed] = true → DashPathEffect; false → solid line (used for proximity previews). */
private fun buildPolyline(
    geoPoints: List<LatLng>,
    title: String,
    color: Int,
    strokeWidth: Float,
    dashed: Boolean = true
): Polyline {
    return Polyline().apply {
        this.title = title
        outlinePaint.color = color
        outlinePaint.strokeWidth = strokeWidth
        if (dashed) {
            outlinePaint.pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
        }
        outlinePaint.strokeCap = Paint.Cap.ROUND
        outlinePaint.isAntiAlias = true
        setPoints(geoPoints.map { GeoPoint(it.latitude, it.longitude) })
    }
}

// ── Geometry sampling ─────────────────────────────────────────────────────────

/** Sample [count] points on a circle of radius [radiusM] around [center]. */
private fun sampleCircle(
    center: LatLng,
    radiusM: Double,
    count: Int
): List<LatLng> {
    val result = mutableListOf<LatLng>()
    for (i in 0 until count) {
        val bearingDeg = i * 360.0 / count
        result.add(destinationPoint(center, radiusM, bearingDeg))
    }
    return result
}

/** Sample [count] evenly-spaced points along the centerline from [p1] to [p2] (inclusive). */
private fun sampleCenterline(
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

/** Compute the destination point given start, distance in metres, and bearing in degrees. */
private fun destinationPoint(start: LatLng, distanceM: Double, bearingDeg: Double): LatLng {
    val dR = distanceM / EARTH_RADIUS_M
    val br = Math.toRadians(bearingDeg)
    val lat1 = Math.toRadians(start.latitude)
    val lon1 = Math.toRadians(start.longitude)

    val lat2 = asin(sin(lat1) * cos(dR) + cos(lat1) * sin(dR) * cos(br))
    val lon2 = lon1 + atan2(sin(br) * sin(dR) * cos(lat1), cos(dR) - sin(lat1) * sin(lat2))

    return LatLng(Math.toDegrees(lat2), Math.toDegrees(lon2))
}

// ── Bitmap helpers ────────────────────────────────────────────────────────────

/**
 * Create a simple filled-circle [Bitmap] to use as a marker icon.
 * Cached statically for the confirmed color to avoid repeated allocations.
 */
private fun createDotBitmap(color: Int): Bitmap {
    val radiusPx = (DOT_RADIUS_DP * 3f).toInt() // scale for density ~3x
    val size = radiusPx * 2 + 4 // padding
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint().apply {
        this.color = color
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    canvas.drawCircle(size / 2f, size / 2f, radiusPx.toFloat(), paint)
    return bitmap
}

/**
 * Dim an ARGB colour by reducing its alpha channel to [alphaFraction] of the original.
 * E.g. alphaFraction=0.25f → 25% alpha, other channels unchanged.
 */
private fun dimColor(color: Int, alphaFraction: Float): Int {
    val newAlpha = ((color ushr 24) * alphaFraction).toInt().coerceIn(0, 255)
    return (newAlpha shl 24) or (color and 0x00FFFFFF)
}
