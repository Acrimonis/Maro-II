package ykws.android.maro.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.MarkerGeometry
import ykws.android.maro.data.model.markers.UserMarker
import ykws.android.maro.spatial.SpatialOperations
import ykws.android.maro.spatial.WhereAmIMatch
import ykws.android.maro.spatial.WhereAmIResult
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

/** Confirmed marker colour (semantic.info blue). */
private val COLOR_CONFIRMED = AppConfig.semanticInfo

/** Highlight colour for click-n-move (gold). */
private val COLOR_HIGHLIGHT = 0xFFFFD700.toInt()

/** Dark under-stroke for highlighted markers (dual-outline consistency with track). */
private val COLOR_HIGHLIGHT_UNDER = 0xCC000000.toInt()
/** Extra stroke width added to under-stroke for highlighted markers. */
private const val HIGHLIGHT_UNDER_STROKE_ADD = 6f

/** Unconfirmed marker colour (semantic.caution amber). */
private val COLOR_UNCONFIRMED = AppConfig.semanticCaution

/** Alpha for dimmed (non-matched) markers during match-result highlighting (30%). */
private const val DIMMED_ALPHA_FRACTION = 0.30f

/** Alpha fraction for zone fill (20% — subtle transparent background). */
private const val ZONE_FILL_ALPHA_FRACTION = 0.20f

/** Alpha fraction for proximity preview — 50% for strokes, fills use ZONE_FILL_ALPHA_FRACTION/2 (10%). */
private const val PROXIMITY_ALPHA_FRACTION = 0.50f

/** Brighter stroke multiplier for matched markers (3dp → 5dp ≈ 1.67×). */
private const val MATCHED_STROKE_MULTIPLIER = 1.67f

/** Stroke multiplier for pulse-highlighted selected marker. */
private const val SELECTED_STROKE_MULTIPLIER = 2.5f

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
 * @param onMarkerTap            Called with the list of tapped marker IDs (one or more for overlapping markers).
 * @param matchResult            Optional tiered match result for marker highlighting.
 */
@Composable
fun MarkerOverlay(
    markers: List<UserMarker>,
    mapView: MapView?,
    proximityZoneMultiplier: Double = 3.0,
    modifier: Modifier = Modifier,
    unconfirmedMarker: UserMarker? = null,
    onMarkerTap: (List<String>) -> Unit = {},
    matchResult: WhereAmIResult? = null,
    markerZonesVisible: Boolean = true,
    selectedMarkerId: String? = null,
    markerLayerState: MarkerLayerState = MarkerLayerState.SHOW_ALL,
    highlightedMarkerId: String? = null
) {
    val mv = mapView ?: return
    val context = LocalContext.current

    // Helper to remove all marker overlays
    fun removeAllMarkerOverlays() {
        val toRemove = mv.overlays.filter { overlay ->
            (overlay as? Polyline)?.title?.startsWith(OVERLAY_PREFIX) == true ||
            (overlay as? Polygon)?.title?.startsWith(OVERLAY_PREFIX) == true ||
            (overlay as? Marker)?.title?.startsWith(OVERLAY_PREFIX) == true
        }
        mv.overlays.removeAll(toRemove)
    }

    // ── P6: Build set of matched marker IDs for highlighting ──────────────────
    val matchedIds: Set<String> = matchResult?.allMatches?.mapNotNull { match ->
        when (match) {
            is WhereAmIMatch.ZoneMatch -> match.marker.id
            is WhereAmIMatch.LineOfSightMatch -> match.marker.id
        }
    }?.toSet() ?: emptySet()

    DisposableEffect(markers, unconfirmedMarker, mv, matchResult, selectedMarkerId, markerZonesVisible, highlightedMarkerId) {
        Log.d("MaroMapRefresh", "MarkerOverlay DisposableEffect restart: markers=${markers.size} mv=${mv.hashCode()} zonesVisible=$markerZonesVisible")
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
            val markerColor = MarkerColors.of(marker.colorIndex)
            // Unconfirmed: unconfirmed colour. Confirmed + not matched + has result: dim.
            val baseColor = if (marker.id == highlightedMarkerId) {
                COLOR_HIGHLIGHT
            } else when {
                !confirmed -> COLOR_UNCONFIRMED
                matchResult != null && !isMatched -> dimColor(markerColor, DIMMED_ALPHA_FRACTION)
                else -> markerColor
            }
            val strokeMultiplier = when {
                marker.id == selectedMarkerId -> SELECTED_STROKE_MULTIPLIER
                matchResult != null && isMatched -> MATCHED_STROKE_MULTIPLIER
                else -> 1.0f
            }
            val isHighlighted = marker.id == highlightedMarkerId
            val proxColor = dimColor(markerColor, PROXIMITY_ALPHA_FRACTION)
            val proxFillColor = dimColor(markerColor, ZONE_FILL_ALPHA_FRACTION / 2.0f)

            // Always render full geometry — only SHOW_ALL state now (no SHOW_PINNED).
            val drawGeometry = true

            // Zone shapes gated by markerZonesVisible for confirmed markers;
            // unconfirmed (creating/editing) always show full geometry.
            val drawZones = drawGeometry && (!confirmed || markerZonesVisible || marker.id == highlightedMarkerId)

            // Suppress center/p1/p2 dots when pinned — icon replaces the point marker.
            // Only applies to confirmed markers; unconfirmed always shows dots.
            val skipDots = marker.pinned && confirmed

            // Auto-markers (IDLE_AUTO) never show proximity rings — they keep
            // proximity for whereAmI matching but suppress visual clutter.
            val showProximity = drawZones && marker.origin != ykws.android.maro.data.model.markers.MarkerOrigin.IDLE_AUTO

            when (val geom = marker.geometry) {
                is MarkerGeometry.Pin -> {
                    if (drawGeometry && !skipDots) {
                        addPinOverlay(mv, geom, marker.id, baseColor, dotBitmap,
                            confirmed = confirmed, onMarkerTap = onMarkerTap,
                            isHighlighted = isHighlighted)
                    }

                    // Proximity range preview (fill + stroke)
                    if (showProximity) {
                        val previewRadiusM = marker.proximityOverrideM
                            ?: AppConfig.markerProximityPinM
                        // Fill
                        val proxFill = Polygon().apply {
                            title = "$OVERLAY_PREFIX${marker.id}_prox_fill"
                            fillPaint.color = proxFillColor
                            fillPaint.isAntiAlias = true
                            outlinePaint.strokeWidth = 0f
                            points = Polygon.pointsAsCircle(
                                GeoPoint(geom.position.latitude, geom.position.longitude),
                                previewRadiusM
                            )
                        }
                        mv.overlays.add(proxFill)
                        // Stroke
                        addCirclePolyline(
                            mv, geom.position, previewRadiusM,
                            "$OVERLAY_PREFIX${marker.id}_prox",
                            proxColor, 2f,
                            dashed = false
                        )
                    }
                }

                is MarkerGeometry.Circle -> {
                    if (drawZones) {
                        addCircleOverlay(mv, geom, marker.id, baseColor, dotBitmap, strokeMultiplier,
                            confirmed = confirmed, onMarkerTap = onMarkerTap, skipDots = skipDots,
                            isHighlighted = isHighlighted)
                    } else if (drawGeometry && !skipDots) {
                        // Center dot only
                        addPinOverlay(mv, MarkerGeometry.Pin(geom.center), marker.id, baseColor, dotBitmap,
                            confirmed = confirmed, onMarkerTap = onMarkerTap,
                            isHighlighted = isHighlighted)
                    }

                    // Proximity range preview (fill + stroke) — drawn from zone boundary outward
                    if (showProximity) {
                        val proximityM = marker.proximityOverrideM
                            ?: (geom.radiusM * proximityZoneMultiplier)
                        val totalRadiusM = geom.radiusM + proximityM
                        // Fill
                        val proxFill = Polygon().apply {
                            title = "$OVERLAY_PREFIX${marker.id}_prox_fill"
                            fillPaint.color = proxFillColor
                            fillPaint.isAntiAlias = true
                            outlinePaint.strokeWidth = 0f
                            points = Polygon.pointsAsCircle(
                                GeoPoint(geom.center.latitude, geom.center.longitude),
                                totalRadiusM
                            )
                        }
                        mv.overlays.add(proxFill)
                        // Stroke
                        addCirclePolyline(
                            mv, geom.center, totalRadiusM,
                            "$OVERLAY_PREFIX${marker.id}_prox",
                            proxColor, 2f,
                            dashed = false
                        )
                    }
                }

                is MarkerGeometry.Corridor -> {
                    if (drawZones) {
                        addCorridorOverlay(mv, geom, marker.id, baseColor, dotBitmap, confirmed, strokeMultiplier,
                            onMarkerTap = onMarkerTap, skipDots = skipDots,
                            isHighlighted = isHighlighted)
                    } else if (drawGeometry && !skipDots) {
                        // p1/p2 dots only
                        addPinOverlay(mv, MarkerGeometry.Pin(geom.p1), "${marker.id}_p1", baseColor, dotBitmap,
                            confirmed = confirmed, onMarkerTap = onMarkerTap,
                            isHighlighted = isHighlighted)
                        addPinOverlay(mv, MarkerGeometry.Pin(geom.p2), "${marker.id}_p2", baseColor, dotBitmap,
                            confirmed = confirmed, onMarkerTap = onMarkerTap,
                            isHighlighted = isHighlighted)
                    }

                    // Proximity range preview (fill + stroke) — drawn from zone boundary outward
                    if (showProximity) {
                        val proximityM = marker.proximityOverrideM
                            ?: (geom.widthM * proximityZoneMultiplier)
                        val halfProx = geom.widthM / 2.0 + proximityM
                        // Fill
                        val proxBearing = SpatialOperations.initialBearing(geom.p1, geom.p2)
                        val proxFillPts = buildCorridorFillPoints(geom.p1, geom.p2, halfProx, proxBearing)
                        val proxFill = Polygon().apply {
                            title = "$OVERLAY_PREFIX${marker.id}_prox_fill"
                            fillPaint.color = proxFillColor
                            fillPaint.isAntiAlias = true
                            outlinePaint.strokeWidth = 0f
                            points = proxFillPts
                        }
                        mv.overlays.add(proxFill)
                        // Parallel lines at ±halfProx (dashed stadium shape)
                        addCorridorParallels(
                            mv, geom.p1, geom.p2, halfProx,
                            "$OVERLAY_PREFIX${marker.id}_prox",
                            proxColor, 2f,
                            dashed = true,
                            isHighlighted = false
                        )
                        // Semi-circle endcaps at p1 and p2 (matching main corridor band)
                        addSemiCircleCaps(
                            mv, geom.p1, geom.p2, halfProx, proxBearing,
                            "$OVERLAY_PREFIX${marker.id}_prox",
                            proxColor, 2f,
                            dashed = true,
                            isHighlighted = false
                        )
                    }
                }
            }
        }

        // ── Icon markers for pinned markers ──────────────────────────────
        for (marker in allMarkers) {
            val iconText = marker.icon ?: if (marker.pinned) "\uD83D\uDCCD" else null ?: continue
            val positions = when (marker.geometry) {
                is MarkerGeometry.Pin -> listOf(marker.geometry.position)
                is MarkerGeometry.Circle -> listOf(marker.geometry.center)
                is MarkerGeometry.Corridor -> listOf(
                    marker.geometry.p1,
                    LatLng(
                        (marker.geometry.p1.latitude + marker.geometry.p2.latitude) / 2.0,
                        (marker.geometry.p1.longitude + marker.geometry.p2.longitude) / 2.0
                    ),
                    marker.geometry.p2
                )
            }
            for (pos in positions) {
                val iconMarker = Marker(mv).apply {
                    position = GeoPoint(pos.latitude, pos.longitude)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    title = "${OVERLAY_PREFIX}icon_${marker.id}_${pos.latitude}_${pos.longitude}"
                    val bitmap = android.graphics.Bitmap.createBitmap(64, 64, android.graphics.Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(bitmap)
                    val paint = android.graphics.Paint().apply {
                        color = MarkerColors.of(marker.colorIndex)
                        textSize = 48f
                        textAlign = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                    }
                    // Auto-marker icons at reduced opacity
                    if (marker.origin == ykws.android.maro.data.model.markers.MarkerOrigin.IDLE_AUTO) {
                        paint.alpha = 255 * ykws.android.maro.config.AppConfig.boatMarkerIdleOpacityPct / 100
                    }
                    canvas.drawText(iconText, 32f, 44f, paint)
                    icon = android.graphics.drawable.BitmapDrawable(mv.context.resources, bitmap)
                    setOnMarkerClickListener { _, _ -> true }
                }
                mv.overlays.add(iconMarker)
            }
        }

        // ── MapEventsOverlay for area-based tap detection ──────────────────
        val confirmedMarkers = markers.filter { it.confirmed }
        val tapOverlay = MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                if (p == null || confirmedMarkers.isEmpty()) return false
                val tapPoint = LatLng(p.latitude, p.longitude)
                val tappedIds = mutableListOf<String>()

                for (marker in confirmedMarkers) {
                    val range = proximityRangeForTap(marker, AppConfig.markerProximityPinM, proximityZoneMultiplier)
                    val dist = closestPointOnGeometry(tapPoint, marker.geometry)
                    if (dist <= range) {
                        tappedIds.add(marker.id)
                    }
                }

                if (tappedIds.isNotEmpty()) {
                    onMarkerTap(tappedIds)
                    return true
                }
                return false
            }

            override fun longPressHelper(p: GeoPoint?): Boolean = false
        })
        mv.overlays.add(tapOverlay)

        mv.invalidate()

        onDispose {
            removeAllMarkerOverlays()
            mv.overlays.remove(tapOverlay)
            mv.invalidate()
        }
    }
}

// ── Overlay builders ──────────────────────────────────────────────────────────

/** Add a pin [Marker] at [geom.position]. When [confirmed] and [onMarkerTap] is
 *  provided, a click listener is set that returns true (suppressing default popup). */
private fun addPinOverlay(
    mv: MapView,
    geom: MarkerGeometry.Pin,
    markerId: String,
    color: Int,
    dotBitmap: Bitmap,
    confirmed: Boolean = true,
    onMarkerTap: (List<String>) -> Unit = {},
    isHighlighted: Boolean = false
) {
    val geo = GeoPoint(geom.position.latitude, geom.position.longitude)

    // Dark under-stroke dot for highlighted markers (rendered before gold dot)
    if (isHighlighted) {
        val underDot = createDotBitmap(COLOR_HIGHLIGHT_UNDER, radiusMultiplier = 1.5f)
        mv.overlays.add(Marker(mv).apply {
            position = geo
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            icon = BitmapDrawable(mv.context.resources, underDot)
            title = "${OVERLAY_PREFIX}pin_${markerId}_ul"
            // No click listener on under-stroke dot
        })
    }

    val marker = Marker(mv).apply {
        position = geo
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        icon = BitmapDrawable(mv.context.resources, if (color == COLOR_CONFIRMED) dotBitmap else createDotBitmap(color))
        title = "${OVERLAY_PREFIX}pin_$markerId"
        if (confirmed) {
            setOnMarkerClickListener { _, _ ->
                onMarkerTap(listOf(markerId))
                true
            }
        }
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
    strokeMultiplier: Float = 1.0f,
    confirmed: Boolean = true,
    onMarkerTap: (List<String>) -> Unit = {},
    skipDots: Boolean = false,
    isHighlighted: Boolean = false
) {
    // Fill: subtle transparent background
    val fillColor = dimColor(color, ZONE_FILL_ALPHA_FRACTION)
    val fillPoly = Polygon().apply {
        title = "${OVERLAY_PREFIX}circle_fill_$markerId"
        fillPaint.color = fillColor
        fillPaint.isAntiAlias = true
        outlinePaint.strokeWidth = 0f
        points = Polygon.pointsAsCircle(
            GeoPoint(geom.center.latitude, geom.center.longitude),
            geom.radiusM
        )
    }
    mv.overlays.add(fillPoly)

    // Dark under-stroke circle for highlighted markers
    if (isHighlighted) {
        val underStrokeW = 4f * strokeMultiplier + HIGHLIGHT_UNDER_STROKE_ADD
        addCirclePolyline(mv, geom.center, geom.radiusM, "${OVERLAY_PREFIX}circle_${markerId}_ul", COLOR_HIGHLIGHT_UNDER, underStrokeW)
    }

    // Circle outline as closed Polyline
    val strokeW = 4f * strokeMultiplier
    addCirclePolyline(mv, geom.center, geom.radiusM, "${OVERLAY_PREFIX}circle_$markerId", color, strokeW)

    // Center dot (suppressed when skipDots — icon replaces it)
    if (!skipDots) {
        addPinOverlay(mv, MarkerGeometry.Pin(geom.center), markerId, color, dotBitmap,
            confirmed = confirmed, onMarkerTap = onMarkerTap,
            isHighlighted = isHighlighted)
    }
}

/** Add corridor overlays: two parallel lines, centerline, p1/p2 markers.
 *  @param skipDots When true, p1/p2 center dots are suppressed (icon replaces them). */
private fun addCorridorOverlay(
    mv: MapView,
    geom: MarkerGeometry.Corridor,
    markerId: String,
    color: Int,
    dotBitmap: Bitmap,
    confirmed: Boolean,
    strokeMultiplier: Float = 1.0f,
    onMarkerTap: (List<String>) -> Unit = {},
    skipDots: Boolean = false,
    isHighlighted: Boolean = false
) {
    val halfW = geom.widthM / 2.0

    // Dark under-stroke centerline for highlighted markers
    if (isHighlighted) {
        val underCenterline = buildPolyline(
            sampleCenterline(geom.p1, geom.p2, CORRIDOR_SAMPLES),
            "${OVERLAY_PREFIX}corr_center_${markerId}_ul",
            COLOR_HIGHLIGHT_UNDER,
            2f * strokeMultiplier + HIGHLIGHT_UNDER_STROKE_ADD
        )
        if (confirmed) {
            underCenterline.outlinePaint.pathEffect = null
        }
        mv.overlays.add(underCenterline)
    }

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

    // Fill: subtle transparent background as closed pill polygon
    val fillColor = dimColor(color, ZONE_FILL_ALPHA_FRACTION)
    val bearing = SpatialOperations.initialBearing(geom.p1, geom.p2)
    val corridorFillPts = buildCorridorFillPoints(geom.p1, geom.p2, halfW, bearing)
    val fillPoly = Polygon().apply {
        title = "${OVERLAY_PREFIX}corr_fill_$markerId"
        fillPaint.color = fillColor
        fillPaint.isAntiAlias = true
        outlinePaint.strokeWidth = 0f
        points = corridorFillPts
    }
    mv.overlays.add(fillPoly)

    // Two parallel lines at ±halfW
    addCorridorParallels(mv, geom.p1, geom.p2, halfW, "${OVERLAY_PREFIX}corr_$markerId", color, 4f * strokeMultiplier,
        isHighlighted = isHighlighted)

    // Semi-circle caps at each end (close the band into a pill shape)
    addSemiCircleCaps(mv, geom.p1, geom.p2, halfW, bearing, "${OVERLAY_PREFIX}corr_$markerId", color, 4f * strokeMultiplier,
        isHighlighted = isHighlighted)

    // p1/p2 dots suppressed when skipDots — icon replaces them
    if (!skipDots) {
        addPinOverlay(mv, MarkerGeometry.Pin(geom.p1), "${markerId}_p1", color, dotBitmap,
            confirmed = confirmed, onMarkerTap = onMarkerTap,
            isHighlighted = isHighlighted)
        addPinOverlay(mv, MarkerGeometry.Pin(geom.p2), "${markerId}_p2", color, dotBitmap,
            confirmed = confirmed, onMarkerTap = onMarkerTap,
            isHighlighted = isHighlighted)
    }
}

/** Build a closed polygon tracing the corridor pill shape (fill):
 *  left edge p1→p2, p2 cap arc, right edge reversed p2→p1, p1 cap arc. */
private fun buildCorridorFillPoints(
    p1: LatLng,
    p2: LatLng,
    halfWidthM: Double,
    bearingDeg: Double
): List<GeoPoint> {
    val result = mutableListOf<GeoPoint>()
    val perpLeft = (bearingDeg + 90.0 + 360.0) % 360.0
    val perpRight = (bearingDeg - 90.0 + 360.0) % 360.0

    // Sample centerline
    val centerPts = sampleCenterline(p1, p2, CORRIDOR_SAMPLES)

    // Left edge (p1 → p2)
    centerPts.forEach { pt ->
        val left = destinationPoint(pt, halfWidthM, perpLeft)
        result.add(GeoPoint(left.latitude, left.longitude))
    }

    // p2 cap arc (left → right, through forward/B — outward at p2)
    val capSamples = 18
    val startAngle = bearingDeg + 90.0
    val step = 180.0 / capSamples
    for (i in 1 until capSamples) {
        val a = ((startAngle - i * step) + 360.0) % 360.0
        val pt = destinationPoint(p2, halfWidthM, a)
        result.add(GeoPoint(pt.latitude, pt.longitude))
    }

    // Right edge reversed (p2 → p1)
    for (i in centerPts.indices.reversed()) {
        val right = destinationPoint(centerPts[i], halfWidthM, perpRight)
        result.add(GeoPoint(right.latitude, right.longitude))
    }

    // p1 cap arc (right → left, through back/+180° — outward)
    for (i in 1 until capSamples) {
        val a = ((startAngle + 180.0) - i * step + 360.0) % 360.0
        val pt = destinationPoint(p1, halfWidthM, a)
        result.add(GeoPoint(pt.latitude, pt.longitude))
    }

    return result
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
    dashed: Boolean = true,
    isHighlighted: Boolean = false
) {
    val centerPts = sampleCenterline(p1, p2, CORRIDOR_SAMPLES)
    if (centerPts.size < 2) return

    val bearing = SpatialOperations.initialBearing(p1, p2)
    val perpLeft = (bearing + 90.0) % 360.0
    val perpRight = (bearing - 90.0 + 360.0) % 360.0

    val leftPts = centerPts.map { destinationPoint(it, halfWidthM, perpLeft) }
    val rightPts = centerPts.map { destinationPoint(it, halfWidthM, perpRight) }

    // Dark under-stroke parallels for highlighted markers
    if (isHighlighted) {
        val underStrokeW = strokeWidth + HIGHLIGHT_UNDER_STROKE_ADD
        mv.overlays.add(buildPolyline(leftPts, "${titleBase}_left_ul", COLOR_HIGHLIGHT_UNDER, underStrokeW, dashed, Paint.Cap.BUTT))
        mv.overlays.add(buildPolyline(rightPts, "${titleBase}_right_ul", COLOR_HIGHLIGHT_UNDER, underStrokeW, dashed, Paint.Cap.BUTT))
    }

    mv.overlays.add(buildPolyline(leftPts, "${titleBase}_left", color, strokeWidth, dashed, Paint.Cap.BUTT))
    mv.overlays.add(buildPolyline(rightPts, "${titleBase}_right", color, strokeWidth, dashed, Paint.Cap.BUTT))
}

/** Add semi-circle caps at p1 and p2 to close the corridor band.
 *  Each cap is an 18-point arc connecting the left and right parallel edges,
 *  curving outward (away from the corridor center). */
private fun addSemiCircleCaps(
    mv: MapView,
    p1: LatLng,
    p2: LatLng,
    halfWidthM: Double,
    bearingDeg: Double,
    titleBase: String,
    color: Int,
    strokeWidth: Float,
    dashed: Boolean = true,
    isHighlighted: Boolean = false
) {
    val capSamples = 18
    // Arc: from left-edge (+90°) to right-edge (-90°), sweeping +180° through back (+180°)
    val startAngle = bearingDeg + 90.0
    val sweep = 180.0
    val angleStep = sweep / capSamples

    // p1 cap: left → back → right (bulges outward)
    val p1Arc = (0..capSamples).map { i ->
        val a = (startAngle + i * angleStep) % 360.0
        destinationPoint(p1, halfWidthM, a)
    }

    // p2 cap: left → forward (B) → right (sweeps −180°, outward at p2)
    val p2Arc = (0..capSamples).map { i ->
        val a = ((startAngle - i * angleStep) + 360.0) % 360.0
        destinationPoint(p2, halfWidthM, a)
    }

    // Dark under-stroke caps for highlighted markers
    if (isHighlighted) {
        val underStrokeW = strokeWidth + HIGHLIGHT_UNDER_STROKE_ADD
        mv.overlays.add(buildPolyline(p1Arc, "${titleBase}_cap_p1_ul", COLOR_HIGHLIGHT_UNDER, underStrokeW, dashed, Paint.Cap.BUTT))
        mv.overlays.add(buildPolyline(p2Arc, "${titleBase}_cap_p2_ul", COLOR_HIGHLIGHT_UNDER, underStrokeW, dashed, Paint.Cap.BUTT))
    }

    mv.overlays.add(buildPolyline(p1Arc, "${titleBase}_cap_p1", color, strokeWidth, dashed, Paint.Cap.BUTT))
    mv.overlays.add(buildPolyline(p2Arc, "${titleBase}_cap_p2", color, strokeWidth, dashed, Paint.Cap.BUTT))
}

/** Build a [Polyline] with the given [geoPoints], [title], [color], and [strokeWidth].
 *  [dashed] = true → DashPathEffect; false → solid line (used for proximity previews). */
private fun buildPolyline(
    geoPoints: List<LatLng>,
    title: String,
    color: Int,
    strokeWidth: Float,
    dashed: Boolean = true,
    strokeCap: Paint.Cap = Paint.Cap.ROUND
): Polyline {
    return Polyline().apply {
        this.title = title
        outlinePaint.color = color
        outlinePaint.strokeWidth = strokeWidth
        if (dashed) {
            outlinePaint.pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
        }
        outlinePaint.strokeCap = strokeCap
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
 * @param radiusMultiplier Scale factor for the dot radius (1.0 = normal, 1.5 = under-stroke).
 */
private fun createDotBitmap(color: Int, radiusMultiplier: Float = 1.0f): Bitmap {
    val radiusPx = (DOT_RADIUS_DP * 3f * radiusMultiplier).toInt()
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

// ── Tap hit-test helpers ───────────────────────────────────────────────────

/** Compute the proximity range for tap detection on [marker].
 *  Uses [proximityOverrideM] if set, otherwise the config-based formula. */
private fun proximityRangeForTap(
    marker: UserMarker,
    pinDefaultM: Double,
    zoneMultiplier: Double
): Double {
    marker.proximityOverrideM?.let { return it }
    return when (val g = marker.geometry) {
        is MarkerGeometry.Pin -> pinDefaultM
        is MarkerGeometry.Circle -> g.radiusM * zoneMultiplier
        is MarkerGeometry.Corridor -> g.widthM * zoneMultiplier
    }
}

/** Closest distance (metres) from [tap] to any part of [geom].
 *  Returns 0.0 if the point lies inside the geometry. */
private fun closestPointOnGeometry(tap: LatLng, geom: MarkerGeometry): Double {
    return when (geom) {
        is MarkerGeometry.Pin -> SpatialOperations.haversine(tap, geom.position)
        is MarkerGeometry.Circle -> {
            val distToCenter = SpatialOperations.haversine(tap, geom.center)
            max(0.0, distToCenter - geom.radiusM)
        }
        is MarkerGeometry.Corridor -> {
            val bearing = SpatialOperations.initialBearing(geom.p1, geom.p2)
            val distP1P2 = SpatialOperations.haversine(geom.p1, geom.p2)
            // Project tap onto the p1→p2 segment
            val distP1Tap = SpatialOperations.haversine(geom.p1, tap)
            val bearingP1Tap = SpatialOperations.initialBearing(geom.p1, tap)
            val angleDiff = Math.toRadians(((bearingP1Tap - bearing + 540.0) % 360.0) - 180.0)
            val alongDist = distP1Tap * cos(angleDiff)   // signed distance along segment from p1
            val lateralDist = abs(distP1Tap * sin(angleDiff))  // perpendicular distance

            val halfW = geom.widthM / 2.0
            val clampedAlong = alongDist.coerceIn(0.0, distP1P2)
            // Recompute lateral at the clamped projection point
            val distToSegmentEnd = if (alongDist < 0.0) {
                distP1Tap  // closer to p1
            } else if (alongDist > distP1P2) {
                SpatialOperations.haversine(tap, geom.p2)  // closer to p2
            } else {
                lateralDist  // between the endpoints
            }

            when {
                alongDist < 0.0 -> max(0.0, distP1Tap - halfW)       // near p1 cap
                alongDist > distP1P2 -> max(0.0, SpatialOperations.haversine(tap, geom.p2) - halfW)  // near p2 cap
                else -> max(0.0, lateralDist - halfW)                 // along the side
            }
        }
    }
}
