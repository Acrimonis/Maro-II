package ykws.android.maro.ui.map

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.MarkerGeometry
import ykws.android.maro.data.model.markers.MarkerOrigin
import ykws.android.maro.data.model.markers.UserMarker
import ykws.android.maro.spatial.SpatialOperations
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

/** Dark under-stroke for selected markers (dual-outline consistency with track). */
private val COLOR_HIGHLIGHT_UNDER = 0xCC000000.toInt()
/** Extra stroke width added to under-stroke for selected markers, in dp — 6 px on the 3× reference. */
private const val HIGHLIGHT_UNDER_STROKE_ADD_DP = 2f

/** Alpha for dimmed (non-matched) markers during match-result highlighting (30%). */
private const val DIMMED_ALPHA_FRACTION = 0.30f

// The zone's and the proximity ring's outline and fill opacities are not constants here: they are
// `marker.zone.outlineAlpha` / `.fillAlpha` and `marker.proximity.outlineAlpha` / `.fillAlpha` in
// maro.properties, read through AppConfig, so that file is the one home for the four figures. The
// routing-only hatch's stripes take their colour from whichever fill figure paints them.

/** Extra stroke width added to the corridor under-line halo beneath the connecting line, in dp. */
private const val CORRIDOR_UNDERLINE_HALO_ADD_DP = 2f

/** Confirmed marker circle and corridor band stroke, in dp — the 4 px of the 3× reference. */
private const val MARKER_STROKE_DP = 4f / 3f

/** The always-on corridor connecting line's stroke, in dp — the 2 px of the 3× reference. */
private const val CORRIDOR_CONNECTING_STROKE_DP = 2f / 3f

/**
 * The marker's one dash rhythm, in dp — the 12 px on and 8 px off of the 3× reference. Shared with the
 * halo's dashed border ring ([MarkerHalo]), so a routing-only marker's rings read as one hand.
 */
internal const val MARKER_DASH_ON_DP = 4f
internal const val MARKER_DASH_OFF_DP = 8f / 3f

/** Icon glyph bitmap edge and text size at marker zoom 100 %. */
private const val ICON_BITMAP_PX = 64
private const val ICON_TEXT_PX = 48f

// ── Public composable ─────────────────────────────────────────────────────────

/**
 * Composable that renders user-defined markers as OSMdroid native overlays
 * ([Polyline] + [Marker]) added to [mapView.overlays] via two [DisposableEffect]s.
 *
 * Marker rendering:
 * - **Pin:** [Marker] at geo position with a filled-circle icon.
 * - **Circle:** [Polyline] as closed polygon (72 pts) + center [Marker].
 * - **Corridor:** Two [Polyline]s for parallel lines at ±width/2, an always-on
 *   colored connecting line, [Marker] at p1 and p2.
 *
 * **Halo (pinned/unpinned):** confirmed markers render a static halo ring behind
 * their centre dot/icon, sized to the anchor (dot vs icon). The halo colour/opacity
 * come from settings; pinned markers get a strong halo, unpinned a faint/none one.
 * No halo on unconfirmed markers; the selected marker's gold replaces its halo.
 *
 * **Selected marker:** driven by [selectedMarkerId] — renders a gold dual-outline
 * on top and forces its zone shapes visible.
 *
 * **Proximity preview:** thinner [Polyline] for unconfirmed markers only,
 * drawn in cyan at lower alpha.
 *
 * **Match result highlighting (P6):** When [matchResult] is non-null, matched
 * markers render brighter/thicker; non-matched markers render dimmed (lower alpha).
 *
 * Lifecycle — two passes (C05). The **structural pass** is keyed on the markers and the marker-wide
 * settings: it removes the overlays tagged with [OVERLAY_PREFIX], builds every marker's overlays and
 * records them per marker with the [MarkerPaintState] they were drawn with. The **selection pass** is
 * keyed on [selectedMarkerId] and [matchResult] alone: it rebuilds only the markers whose paint state
 * moved — the one unselected and the one selected, or the match's own set — and splices them where
 * their old overlays stood. A tap on one marker therefore no longer re-rasterises every marker. Both
 * passes end on [OverlayZOrder.reorder] and one [MapView.invalidate]; the bitmaps every pass draws from
 * are pooled ([MarkerBitmaps]), so a selection toggle swaps a dot or halo rather than drawing it.
 *
 * @param markers                List of all confirmed user markers to render.
 * @param mapView                The OSMdroid [MapView]; null → nothing drawn.
 * @param proximityZoneMultiplier Multiplier for proximity range preview.
 * @param unconfirmedMarker      Optional unconfirmed marker being created/edited.
 * @param matchResult            Optional tiered match result for marker highlighting.
 * @param markerZonesVisible     Whether marker zone shapes render (selected markers force theirs).
 * @param selectedMarkerId       The currently selected/viewed marker id (single driver for gold + force-zones).
 * @param markerHaloSize         Halo size % (0-100) scaling the ring radius.
 * @param markerPointIconZoom    Marker point/icon rendering zoom % (50-150); 100 = current
 *                               size. Scales the rendered dot radius, the icon glyph and the
 *                               halo ring by the same factor (rule of three).
 * @param markerHaloPinnedColor / Unpinned color — per-state halo colours.
 * @param markerHaloPinnedFillTransparencyPct / Border / Unpinned fill / Border — halo transparency pairs.
 */
@Composable
fun MarkerOverlay(
    markers: List<UserMarker>,
    mapView: MapView?,
    proximityZoneMultiplier: Double = 3.0,
    unconfirmedMarker: UserMarker? = null,
    matchResult: WhereAmIResult? = null,
    markerZonesVisible: Boolean = true,
    selectedMarkerId: String? = null,
    markerHaloSize: Int = 50,
    markerPointIconZoom: Int = 100,
    markerHaloPinnedColor: Int = 0xFFFFFFFF.toInt(),
    markerHaloUnpinnedColor: Int = 0xFF81D4FA.toInt(),
    markerHaloPinnedFillTransparencyPct: Int = 75,
    markerHaloPinnedBorderTransparencyPct: Int = 20,
    markerHaloUnpinnedFillTransparencyPct: Int = 90,
    markerHaloUnpinnedBorderTransparencyPct: Int = 60
) {
    val mv = mapView ?: return

    // The marker-wide inputs, bundled: the structural pass keys on this one value and the builders read it.
    val settings = MarkerDrawSettings(
        proximityZoneMultiplier = proximityZoneMultiplier,
        zonesVisible = markerZonesVisible,
        haloSize = markerHaloSize,
        pointIconZoom = markerPointIconZoom,
        haloPinnedColor = markerHaloPinnedColor,
        haloUnpinnedColor = markerHaloUnpinnedColor,
        haloPinnedFillPct = markerHaloPinnedFillTransparencyPct,
        haloPinnedBorderPct = markerHaloPinnedBorderTransparencyPct,
        haloUnpinnedFillPct = markerHaloUnpinnedFillTransparencyPct,
        haloUnpinnedBorderPct = markerHaloUnpinnedBorderTransparencyPct
    )

    // ── P6: Build set of matched marker IDs for highlighting ──────────────────
    // The match-marker rule has one home (plan §3), read here through it rather than a `when` of its
    // own at each of the four readers.
    val matchActive = matchResult != null
    val matchedIds: Set<String> =
        matchResult?.allMatches?.map { it.matchedMarker().id }?.toSet() ?: emptySet()

    // Build combined marker list: unconfirmed first (renders underneath)
    // so the original confirmed marker stays visible on top during edit.
    // Confirmed markers are ordered by pinned so pinned render last (top of the
    // marker band; OverlayZOrder preserves intra-band order).
    val allMarkers = remember(markers, unconfirmedMarker) {
        if (unconfirmedMarker != null) {
            listOf(unconfirmedMarker) + markers.sortedBy { it.pinned }
        } else {
            markers.sortedBy { it.pinned }
        }
    }

    // What the structural pass built, kept across selection passes — per MapView, like the overlays.
    val cache = remember(mv) { MarkerOverlayCache(mv) }

    // ── The structural pass: every marker, rebuilt when the markers or the settings move ──────────
    // Selection and the match result are read here but are not keys (C05): the pass below owns them.
    DisposableEffect(allMarkers, mv, settings) {
        Log.d("MaroMapRefresh", "MarkerOverlay structural pass: markers=${allMarkers.size} mv=${mv.hashCode()} zonesVisible=${settings.zonesVisible}")
        removeAllMarkerOverlays(mv)
        cache.built.clear()
        for (marker in allMarkers) {
            val paint = paintStateOf(marker, selectedMarkerId, matchActive, matchedIds)
            cache.built[entryKeyOf(marker)] = buildMarkerEntry(mv, cache.bitmaps, marker, paint, settings, isPreview = marker === unconfirmedMarker)
        }
        // Every marker's shapes first, then every icon group, so an icon sits above all zone shapes.
        cache.built.values.forEach { mv.overlays.addAll(it.shapes) }
        cache.built.values.forEach { mv.overlays.addAll(it.icons) }
        cache.bitmaps.prune()

        OverlayZOrder.reorder(mv)
        mv.invalidate()

        onDispose {
            removeAllMarkerOverlays(mv)
            cache.clear()
            mv.invalidate()
        }
    }

    // ── The selection pass: only the markers whose selection or match standing moved ─────────────
    DisposableEffect(mv, selectedMarkerId, matchResult) {
        val wanted = allMarkers.associate {
            entryKeyOf(it) to paintStateOf(it, selectedMarkerId, matchActive, matchedIds)
        }
        val moved = changedPaintKeys(cache.built.mapValues { it.value.paint }, wanted)
        if (moved.isNotEmpty()) {
            for (marker in allMarkers) {
                val key = entryKeyOf(marker)
                if (key !in moved) continue
                val fresh = buildMarkerEntry(mv, cache.bitmaps, marker, wanted.getValue(key), settings, isPreview = marker === unconfirmedMarker)
                val old = cache.built[key]
                // A marker that had no shapes (zones hidden, an icon alone) and gains them on selection
                // has nothing to stand on: its shapes then go below the band's first icon, never after
                // the icons, so the structural order — every shape under every icon — survives.
                splice(mv, old?.shapes.orEmpty(), fresh.shapes, fallbackAt = firstIconIndex(mv, cache))
                splice(mv, old?.icons.orEmpty(), fresh.icons, fallbackAt = mv.overlays.size)
                cache.built[key] = fresh
            }
            OverlayZOrder.reorder(mv)
            mv.invalidate()
        }
        onDispose { }
    }
}

// ── The two passes' state ─────────────────────────────────────────────────────

/**
 * The selection-and-match half of a marker's look — the inputs the selection pass alone changes. The
 * structural pass records the one each marker was drawn with; the selection pass rebuilds only the
 * markers whose state moved ([changedPaintKeys]).
 */
internal data class MarkerPaintState(
    val isSelected: Boolean,
    val matchActive: Boolean,
    val isMatched: Boolean
)

/** The paint state [marker] wants under [selectedMarkerId] and the match ([matchedIds] when active). */
internal fun paintStateOf(
    marker: UserMarker,
    selectedMarkerId: String?,
    matchActive: Boolean,
    matchedIds: Set<String>
): MarkerPaintState = MarkerPaintState(
    isSelected = marker.id == selectedMarkerId,
    matchActive = matchActive,
    isMatched = matchActive && marker.id in matchedIds
)

/**
 * The keys of [wanted] whose state differs from what [drawn] holds — a key the last build never drew
 * counts as moved. Pure, so a JVM test pins the diff a selection or a match result produces.
 */
internal fun changedPaintKeys(
    drawn: Map<String, MarkerPaintState>,
    wanted: Map<String, MarkerPaintState>
): Set<String> = wanted.filter { (key, state) -> drawn[key] != state }.keys

/**
 * The marker-wide inputs every marker is drawn with — the structural pass's key, short of the markers
 * themselves, and the one parameter the builders read them through.
 */
private data class MarkerDrawSettings(
    val proximityZoneMultiplier: Double,
    val zonesVisible: Boolean,
    val haloSize: Int,
    val pointIconZoom: Int,
    val haloPinnedColor: Int,
    val haloUnpinnedColor: Int,
    val haloPinnedFillPct: Int,
    val haloPinnedBorderPct: Int,
    val haloUnpinnedFillPct: Int,
    val haloUnpinnedBorderPct: Int
)

/**
 * One marker's overlays as built: its zone shapes, dots and rings ([shapes]) and the icon group — halo
 * ring plus glyph per position — that every marker's shapes paint under ([icons]), with the paint state
 * they were drawn in.
 */
private class MarkerEntry(val shapes: List<Overlay>, val icons: List<Overlay>, val paint: MarkerPaintState)

/** What the structural pass leaves behind for the selection pass: the entries, and the bitmaps they share. */
private class MarkerOverlayCache(mv: MapView) {
    /** Entry key ([entryKeyOf]) → the marker's last build, in the marker band's own order. */
    val built = LinkedHashMap<String, MarkerEntry>()
    val bitmaps = MarkerBitmaps(mv)

    fun clear() {
        built.clear()
        bitmaps.clear()
    }
}

/**
 * The entry key of [marker]: its id, except for the unconfirmed copy drawn under the confirmed one during
 * an edit, which shares that id and takes its own slot.
 */
private fun entryKeyOf(marker: UserMarker): String =
    if (marker.confirmed) marker.id else "unconfirmed:${marker.id}"

/**
 * Remove every `marker_*` overlay — except the route's destination pin. That pin wears the prefix only to
 * ride the same band (`OverlayZOrder.isMarkerOverlay`) while its object belongs to `RouteHost`; sweeping
 * it left the route's lines undrawn, because the host's repaint asked for the pin and bailed once it was
 * gone (the device's own log, 2026-09-23). A title another home owns is not this pass's to remove.
 */
private fun removeAllMarkerOverlays(mv: MapView) {
    val toRemove = mv.overlays.filter { overlay ->
        val ownedByThisPass = (overlay as? Marker)?.title != ROUTE_PIN_TITLE
        ownedByThisPass && (
            (overlay as? Polyline)?.title?.startsWith(OVERLAY_PREFIX) == true ||
                (overlay as? Polygon)?.title?.startsWith(OVERLAY_PREFIX) == true ||
                (overlay as? Marker)?.title?.startsWith(OVERLAY_PREFIX) == true
            )
    }
    mv.overlays.removeAll(toRemove)
}

/**
 * Swap one marker's [old] overlays for its [fresh] ones where the old ones stood, so the band's intra-order
 * — unconfirmed under confirmed, pinned on top — survives the swap; with nothing to stand on, they go at
 * [fallbackAt], the caller's answer to where the group belongs (below the icons for shapes, last for icons).
 */
private fun splice(mv: MapView, old: List<Overlay>, fresh: List<Overlay>, fallbackAt: Int) {
    val at = old.firstOrNull()?.let { mv.overlays.indexOf(it) }?.takeIf { it >= 0 }
        ?: fallbackAt.coerceIn(0, mv.overlays.size)
    if (old.isNotEmpty()) mv.overlays.removeAll(old.toSet())
    fresh.forEachIndexed { i, overlay -> mv.overlays.add(at + i, overlay) }
}

/** The index of the band's first icon overlay as it stands on the map, or the end when no icon is drawn. */
private fun firstIconIndex(mv: MapView, cache: MarkerOverlayCache): Int =
    cache.built.values.asSequence()
        .flatMap { it.icons.asSequence() }
        .map { mv.overlays.indexOf(it) }
        .filter { it >= 0 }
        .minOrNull() ?: mv.overlays.size

// ── Per-marker build ──────────────────────────────────────────────────────────

/** Build one marker's overlays — zone shapes, dots or icons, halo rings, proximity preview — into a [MarkerEntry]. */
private fun buildMarkerEntry(
    mv: MapView,
    bitmaps: MarkerBitmaps,
    marker: UserMarker,
    paint: MarkerPaintState,
    settings: MarkerDrawSettings,
    /** True for the wizard's synthetic placement marker, which draws the form's own colour (upstream). */
    isPreview: Boolean = false
): MarkerEntry {
    val shapes = mutableListOf<Overlay>()
    val icons = mutableListOf<Overlay>()

    // Marker point/icon rendering zoom % (50-150, 100 = current). Uniform scale applied
    // to the dot radius, the icon glyph and the halo ring (rule of three).
    val zoomFactor = settings.pointIconZoom.coerceIn(50, 150) / 100f

    val confirmed = marker.confirmed
    val markerColor = MarkerColors.of(marker.colorIndex)
    val appearance = MarkerAppearance.of(
        marker = marker,
        markerColor = markerColor,
        isPreview = isPreview,
        isMatched = paint.isMatched,
        matchActive = paint.matchActive,
        // `of` derives the selection from the id; the paint state already holds the answer.
        selectedMarkerId = marker.id.takeIf { paint.isSelected },
        pinnedColor = settings.haloPinnedColor,
        unpinnedColor = settings.haloUnpinnedColor,
        pinnedFillPct = settings.haloPinnedFillPct,
        pinnedBorderPct = settings.haloPinnedBorderPct,
        unpinnedFillPct = settings.haloUnpinnedFillPct,
        unpinnedBorderPct = settings.haloUnpinnedBorderPct
    )
    val baseColor = appearance.baseColor
    val strokeMultiplier = appearance.strokeMultiplier
    val isSelected = appearance.isSelected
    val haloSpec = appearance.halo
    // Halo fades together with a search non-match.
    val haloDimFraction = if (appearance.isDimmed) DIMMED_ALPHA_FRACTION else 1f
    val proxColor = dimColor(markerColor, AppConfig.markerProximityOutlineAlpha)
    val proxFillColor = dimColor(markerColor, AppConfig.markerProximityFillAlpha)

    // Zone shapes gated by markerZonesVisible for confirmed markers;
    // unconfirmed (creating/editing) always show full geometry; the selected
    // marker forces its zones visible.
    val drawZones = !confirmed || settings.zonesVisible || isSelected

    // Suppress center/p1/p2 dots when an icon is set — icon replaces the point marker.
    // Only applies to confirmed markers; unconfirmed always shows dots.
    val skipDots = marker.icon != null && confirmed

    // Auto-markers (IDLE_AUTO) never show proximity rings — they keep
    // proximity for whereAmI matching but suppress visual clutter.
    val showProximity = drawZones && marker.origin != MarkerOrigin.IDLE_AUTO

    // The strokes the builder hands its helpers are px: the conversion happens once, here, from
    // the dp constants above at the map's own density.
    val density = mv.paintDensity
    val markerStrokePx = dpToPx(MARKER_STROKE_DP, density)
    val corridorConnectingStrokePx = dpToPx(CORRIDOR_CONNECTING_STROKE_DP, density)
    val highlightUnderStrokeAddPx = dpToPx(HIGHLIGHT_UNDER_STROKE_ADD_DP, density)
    val corridorUnderlineHaloAddPx = dpToPx(CORRIDOR_UNDERLINE_HALO_ADD_DP, density)

    when (val geom = marker.geometry) {
        is MarkerGeometry.Pin -> {
            if (!skipDots) {
                addPinOverlay(mv, shapes, bitmaps, geom, marker.id, baseColor,
                    isSelected = isSelected, haloSpec = haloSpec,
                    haloSizePct = settings.haloSize,
                    haloDimFraction = haloDimFraction,
                    markerPointIconZoom = settings.pointIconZoom)
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
                    if (appearance.isRoutingOnly) {
                        fillPaint.shader = hatchShader(proxFillColor, density)
                    }
                    outlinePaint.strokeWidth = 0f
                    points = Polygon.pointsAsCircle(
                        GeoPoint(geom.position.latitude, geom.position.longitude),
                        previewRadiusM
                    )
                }
                shapes.add(proxFill)
                // Stroke
                addCirclePolyline(
                    mv, shapes, geom.position, previewRadiusM,
                    "$OVERLAY_PREFIX${marker.id}_prox",
                    proxColor, corridorConnectingStrokePx,
                    dashed = appearance.isRoutingOnly
                )
            }
        }

        is MarkerGeometry.Circle -> {
            if (drawZones) {
                addCircleOverlay(mv, shapes, bitmaps, geom, marker.id, baseColor, strokeMultiplier,
                    strokePx = markerStrokePx,
                    underStrokeAddPx = highlightUnderStrokeAddPx,
                    dashed = appearance.isRoutingOnly,
                    skipDots = skipDots,
                    isSelected = isSelected, haloSpec = haloSpec,
                    haloSizePct = settings.haloSize,
                    haloDimFraction = haloDimFraction,
                    markerPointIconZoom = settings.pointIconZoom)
            } else if (!skipDots) {
                // Center dot only
                addPinOverlay(mv, shapes, bitmaps, MarkerGeometry.Pin(geom.center), marker.id, baseColor,
                    isSelected = isSelected, haloSpec = haloSpec,
                    haloSizePct = settings.haloSize,
                    haloDimFraction = haloDimFraction,
                    markerPointIconZoom = settings.pointIconZoom)
            }

            // Proximity range preview (fill + stroke) — drawn from zone boundary outward
            if (showProximity) {
                val proximityM = marker.proximityOverrideM
                    ?: (geom.radiusM * settings.proximityZoneMultiplier)
                val totalRadiusM = geom.radiusM + proximityM
                // Fill
                val proxFill = Polygon().apply {
                    title = "$OVERLAY_PREFIX${marker.id}_prox_fill"
                    fillPaint.color = proxFillColor
                    fillPaint.isAntiAlias = true
                    if (appearance.isRoutingOnly) {
                        fillPaint.shader = hatchShader(proxFillColor, density)
                    }
                    outlinePaint.strokeWidth = 0f
                    points = Polygon.pointsAsCircle(
                        GeoPoint(geom.center.latitude, geom.center.longitude),
                        totalRadiusM
                    )
                }
                shapes.add(proxFill)
                // Stroke
                addCirclePolyline(
                    mv, shapes, geom.center, totalRadiusM,
                    "$OVERLAY_PREFIX${marker.id}_prox",
                    proxColor, corridorConnectingStrokePx,
                    dashed = appearance.isRoutingOnly
                )
            }
        }

        is MarkerGeometry.Corridor -> {
            // Always-on colored connecting line (both zone states, regardless of icon).
            addCorridorConnectingLine(
                mv, shapes, geom, marker.id, appearance, haloSpec, haloDimFraction,
                strokePx = corridorConnectingStrokePx,
                underStrokeAddPx = highlightUnderStrokeAddPx,
                haloAddPx = corridorUnderlineHaloAddPx
            )

            // Full pill band (fill + parallels + caps) only when zones shown.
            if (drawZones) {
                addCorridorBand(mv, shapes, geom, marker.id, baseColor, strokeMultiplier, isSelected, markerStrokePx, highlightUnderStrokeAddPx,
                    dashed = appearance.isRoutingOnly)
            }

            // Endpoint dots (or icons) with halo rings.
            if (!skipDots) {
                addPinOverlay(mv, shapes, bitmaps, MarkerGeometry.Pin(geom.p1), "${marker.id}_p1", baseColor,
                    isSelected = isSelected, haloSpec = haloSpec,
                    haloSizePct = settings.haloSize,
                    haloDimFraction = haloDimFraction,
                    markerPointIconZoom = settings.pointIconZoom)
                addPinOverlay(mv, shapes, bitmaps, MarkerGeometry.Pin(geom.p2), "${marker.id}_p2", baseColor,
                    isSelected = isSelected, haloSpec = haloSpec,
                    haloSizePct = settings.haloSize,
                    haloDimFraction = haloDimFraction,
                    markerPointIconZoom = settings.pointIconZoom)
            }

            // Proximity range preview (fill + stroke) — drawn from zone boundary outward
            if (showProximity) {
                val proximityM = marker.proximityOverrideM
                    ?: (geom.widthM * settings.proximityZoneMultiplier)
                val halfProx = geom.widthM / 2.0 + proximityM
                // Fill
                val proxBearing = SpatialOperations.initialBearing(geom.p1, geom.p2)
                val proxFillPts = buildCorridorFillPoints(geom.p1, geom.p2, halfProx, proxBearing)
                val proxFill = Polygon().apply {
                    title = "$OVERLAY_PREFIX${marker.id}_prox_fill"
                    fillPaint.color = proxFillColor
                    fillPaint.isAntiAlias = true
                    if (appearance.isRoutingOnly) {
                        fillPaint.shader = hatchShader(proxFillColor, density)
                    }
                    outlinePaint.strokeWidth = 0f
                    points = proxFillPts
                }
                shapes.add(proxFill)
                // Parallel lines at ±halfProx (dashed stadium shape)
                addCorridorParallels(
                    mv, shapes, geom.p1, geom.p2, halfProx,
                    "$OVERLAY_PREFIX${marker.id}_prox",
                    proxColor, corridorConnectingStrokePx,
                    dashed = true,
                    isSelected = false
                )
                // Semi-circle endcaps at p1 and p2 (matching main corridor band)
                addSemiCircleCaps(
                    mv, shapes, geom.p1, geom.p2, halfProx, proxBearing,
                    "$OVERLAY_PREFIX${marker.id}_prox",
                    proxColor, corridorConnectingStrokePx,
                    dashed = true,
                    isSelected = false
                )
            }
        }
    }

    // ── Icon group for a marker with an icon (halo rings the icon) ───────────
    val iconText = marker.icon
    if (iconText != null) {
        val positions = when (val geom = marker.geometry) {
            is MarkerGeometry.Pin -> listOf(geom.position)
            is MarkerGeometry.Circle -> listOf(geom.center)
            is MarkerGeometry.Corridor -> listOf(
                geom.p1,
                LatLng(
                    (geom.p1.latitude + geom.p2.latitude) / 2.0,
                    (geom.p1.longitude + geom.p2.longitude) / 2.0
                ),
                geom.p2
            )
        }
        // Auto-marker icons at reduced opacity
        val iconAlpha = if (marker.origin == MarkerOrigin.IDLE_AUTO) {
            255 * (100 - AppConfig.boatMarkerIdleTransparencyPct) / 100
        } else {
            255
        }
        for (pos in positions) {
            val geo = GeoPoint(pos.latitude, pos.longitude)
            // Halo ring behind the icon (sized to the larger icon anchor).
            if (haloSpec != null) {
                addHaloOverlay(mv, icons, bitmaps, geo, haloSpec, dpToPx(MarkerHalo.ICON_ANCHOR_RADIUS_DP, density),
                    settings.haloSize, haloDimFraction, marker.id, "icon", settings.pointIconZoom)
            }
            icons.add(Marker(mv).apply {
                position = geo
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                title = "${OVERLAY_PREFIX}icon_${marker.id}_${pos.latitude}_${pos.longitude}"
                icon = BitmapDrawable(mv.context.resources, bitmaps.icon(iconText, markerColor, iconAlpha, zoomFactor))
            })
        }
    }

    return MarkerEntry(shapes, icons, paint)
}

// ── Overlay builders ──────────────────────────────────────────────────────────

/** Add a halo ring [Marker] at [geo] behind a dot/icon anchor. */
private fun addHaloOverlay(
    mv: MapView,
    out: MutableList<Overlay>,
    bitmaps: MarkerBitmaps,
    geo: GeoPoint,
    haloSpec: MarkerHaloSpec,
    anchorRadiusPx: Float,
    sizePct: Int,
    dimFraction: Float,
    markerId: String,
    suffix: String,
    zoomPct: Int = 100
) {
    val bitmap = bitmaps.halo(haloSpec, anchorRadiusPx, sizePct, zoomPct, dimFraction)
    out.add(Marker(mv).apply {
        position = geo
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        icon = BitmapDrawable(mv.context.resources, bitmap)
        title = "${OVERLAY_PREFIX}halo_${markerId}_$suffix"
        // No click listener on the halo ring
    })
}

/**
 * Add a pin [Marker] at [geom.position]. No click listener is set: a tap reaches osmdroid's own handler,
 * which may open its default info window on the marker.
 */
private fun addPinOverlay(
    mv: MapView,
    out: MutableList<Overlay>,
    bitmaps: MarkerBitmaps,
    geom: MarkerGeometry.Pin,
    markerId: String,
    color: Int,
    isSelected: Boolean = false,
    haloSpec: MarkerHaloSpec? = null,
    haloSizePct: Int = 50,
    haloDimFraction: Float = 1f,
    markerPointIconZoom: Int = 100
) {
    val geo = GeoPoint(geom.position.latitude, geom.position.longitude)
    // Marker point/icon rendering zoom as a factor (50-150 %, 100 = current).
    val markerZoom = markerPointIconZoom.coerceIn(50, 150) / 100f

    // Halo ring behind the dot (sized to the dot anchor)
    if (haloSpec != null) {
        addHaloOverlay(mv, out, bitmaps, geo, haloSpec, dpToPx(MarkerHalo.DOT_ANCHOR_RADIUS_DP, mv.paintDensity), haloSizePct, haloDimFraction, markerId, "dot", markerPointIconZoom)
    }

    // Dark under-stroke dot for selected markers (rendered before gold dot)
    if (isSelected) {
        val underDot = bitmaps.dot(COLOR_HIGHLIGHT_UNDER, radiusMultiplier = 1.5f * markerZoom)
        out.add(Marker(mv).apply {
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
        icon = BitmapDrawable(mv.context.resources, bitmaps.dot(color, radiusMultiplier = markerZoom))
        title = "${OVERLAY_PREFIX}pin_$markerId"
    }
    out.add(marker)
}

/** Add a circle [Polyline] (closed polygon) + center [Marker]. */
private fun addCircleOverlay(
    mv: MapView,
    out: MutableList<Overlay>,
    bitmaps: MarkerBitmaps,
    geom: MarkerGeometry.Circle,
    markerId: String,
    color: Int,
    strokeMultiplier: Float = 1.0f,
    /** The circle's own stroke in px, and the selection's under-stroke add in px. */
    strokePx: Float,
    underStrokeAddPx: Float,
    /** The rings dash when the marker's routing weight is its only purpose (a routing-only marker). */
    dashed: Boolean = false,
    skipDots: Boolean = false,
    isSelected: Boolean = false,
    haloSpec: MarkerHaloSpec? = null,
    haloSizePct: Int = 50,
    haloDimFraction: Float = 1f,
    markerPointIconZoom: Int = 100
) {
    // Fill: subtle transparent background — hatched when the marker is routing-weight-only.
    val fillColor = dimColor(color, AppConfig.markerZoneFillAlpha)
    // The outline's own opacity, `marker.zone.outlineAlpha`; the centre dot keeps the full colour.
    val outlineColor = dimColor(color, AppConfig.markerZoneOutlineAlpha)
    val fillPoly = Polygon().apply {
        title = "${OVERLAY_PREFIX}circle_fill_$markerId"
        fillPaint.color = fillColor
        fillPaint.isAntiAlias = true
        if (dashed) fillPaint.shader = hatchShader(fillColor, mv.paintDensity)
        outlinePaint.strokeWidth = 0f
        points = Polygon.pointsAsCircle(
            GeoPoint(geom.center.latitude, geom.center.longitude),
            geom.radiusM
        )
    }
    out.add(fillPoly)

    // Dark under-stroke circle for selected markers
    if (isSelected) {
        val underStrokeW = strokePx * strokeMultiplier + underStrokeAddPx
        addCirclePolyline(mv, out, geom.center, geom.radiusM, "${OVERLAY_PREFIX}circle_${markerId}_ul", COLOR_HIGHLIGHT_UNDER, underStrokeW, dashed = dashed)
    }

    // Circle outline as closed Polyline
    val strokeW = strokePx * strokeMultiplier
    addCirclePolyline(mv, out, geom.center, geom.radiusM, "${OVERLAY_PREFIX}circle_$markerId", outlineColor, strokeW, dashed = dashed)

    // Center dot (suppressed when skipDots — icon replaces it)
    if (!skipDots) {
        addPinOverlay(mv, out, bitmaps, MarkerGeometry.Pin(geom.center), markerId, color,
            isSelected = isSelected, haloSpec = haloSpec,
            haloSizePct = haloSizePct,
            haloDimFraction = haloDimFraction,
            markerPointIconZoom = markerPointIconZoom)
    }
}

/**
 * Draw the corridor's always-on colored connecting line between p1 and p2.
 * Rendered in both zone states and regardless of icon presence.
 *
 * - Selected: dark under-stroke + gold line (dual outline).
 * - Pinned (halo present): thicker under-line halo beneath the colored line.
 * - Otherwise: plain colored line.
 */
private fun addCorridorConnectingLine(
    mv: MapView,
    out: MutableList<Overlay>,
    geom: MarkerGeometry.Corridor,
    markerId: String,
    appearance: MarkerAppearance,
    haloSpec: MarkerHaloSpec?,
    haloDimFraction: Float,
    /** The line's own stroke and the two adds, all in px. */
    strokePx: Float,
    underStrokeAddPx: Float,
    haloAddPx: Float
) {
    val pts = sampleCenterline(geom.p1, geom.p2, CORRIDOR_SAMPLES)
    val mainW = strokePx * appearance.strokeMultiplier

    if (appearance.isSelected) {
        // Dark under-stroke + gold line
        out.add(buildPolyline(pts, "${OVERLAY_PREFIX}corr_line_${markerId}_ul",
            COLOR_HIGHLIGHT_UNDER, mainW + underStrokeAddPx, false, density = mv.paintDensity))
        out.add(buildPolyline(pts, "${OVERLAY_PREFIX}corr_line_$markerId",
            appearance.baseColor, mainW, false, density = mv.paintDensity))
    } else if (haloSpec != null) {
        // Pinned under-line halo (thicker colored line beneath) + colored line on top
        val underColor = MarkerHalo.colorWithTransparency(haloSpec.color, haloSpec.borderTransparencyPct, haloDimFraction)
        out.add(buildPolyline(pts, "${OVERLAY_PREFIX}corr_line_${markerId}_halo",
            underColor, mainW + haloAddPx, false, density = mv.paintDensity))
        out.add(buildPolyline(pts, "${OVERLAY_PREFIX}corr_line_$markerId",
            appearance.baseColor, mainW, false, density = mv.paintDensity))
    } else {
        out.add(buildPolyline(pts, "${OVERLAY_PREFIX}corr_line_$markerId",
            appearance.baseColor, mainW, false, density = mv.paintDensity))
    }
}

/** Add the corridor pill band (fill + parallels + caps) when zones are shown. */
private fun addCorridorBand(
    mv: MapView,
    out: MutableList<Overlay>,
    geom: MarkerGeometry.Corridor,
    markerId: String,
    color: Int,
    strokeMultiplier: Float,
    isSelected: Boolean,
    /** The band's own stroke in px, and the selection's under-stroke add in px. */
    strokePx: Float,
    underStrokeAddPx: Float,
    /** The band's outline dashes when the marker's routing weight is its only purpose. */
    dashed: Boolean = false
) {
    val halfW = geom.widthM / 2.0

    // Fill: subtle transparent background as closed pill polygon — hatched when routing-weight-only.
    val fillColor = dimColor(color, AppConfig.markerZoneFillAlpha)
    // The band's own outline opacity, `marker.zone.outlineAlpha`; the endpoint dots keep full colour.
    val outlineColor = dimColor(color, AppConfig.markerZoneOutlineAlpha)
    val bearing = SpatialOperations.initialBearing(geom.p1, geom.p2)
    val corridorFillPts = buildCorridorFillPoints(geom.p1, geom.p2, halfW, bearing)
    val fillPoly = Polygon().apply {
        title = "${OVERLAY_PREFIX}corr_fill_$markerId"
        fillPaint.color = fillColor
        fillPaint.isAntiAlias = true
        if (dashed) fillPaint.shader = hatchShader(fillColor, mv.paintDensity)
        outlinePaint.strokeWidth = 0f
        points = corridorFillPts
    }
    out.add(fillPoly)

    // Two parallel lines at ±halfW
    addCorridorParallels(mv, out, geom.p1, geom.p2, halfW, "${OVERLAY_PREFIX}corr_$markerId", outlineColor,
        strokePx * strokeMultiplier, dashed = dashed, isSelected = isSelected, underStrokeAddPx = underStrokeAddPx)

    // Semi-circle caps at each end (close the band into a pill shape)
    addSemiCircleCaps(mv, out, geom.p1, geom.p2, halfW, bearing, "${OVERLAY_PREFIX}corr_$markerId", outlineColor,
        strokePx * strokeMultiplier, dashed = dashed, isSelected = isSelected, underStrokeAddPx = underStrokeAddPx)
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
    out: MutableList<Overlay>,
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
    val polyline = buildPolyline(closed, title, color, strokeWidth, dashed, density = mv.paintDensity)
    out.add(polyline)
}

/** Add two parallel [Polyline]s offset by [halfWidthM] from the centerline. */
private fun addCorridorParallels(
    mv: MapView,
    out: MutableList<Overlay>,
    p1: LatLng,
    p2: LatLng,
    halfWidthM: Double,
    titleBase: String,
    color: Int,
    strokeWidth: Float,
    dashed: Boolean = true,
    isSelected: Boolean = false,
    /** The selection's under-stroke add in px; zero when the caller inherits none. */
    underStrokeAddPx: Float = 0f
) {
    val centerPts = sampleCenterline(p1, p2, CORRIDOR_SAMPLES)
    if (centerPts.size < 2) return

    val bearing = SpatialOperations.initialBearing(p1, p2)
    val perpLeft = (bearing + 90.0) % 360.0
    val perpRight = (bearing - 90.0 + 360.0) % 360.0

    val leftPts = centerPts.map { destinationPoint(it, halfWidthM, perpLeft) }
    val rightPts = centerPts.map { destinationPoint(it, halfWidthM, perpRight) }

    // Dark under-stroke parallels for selected markers
    if (isSelected) {
        val underStrokeW = strokeWidth + underStrokeAddPx
        out.add(buildPolyline(leftPts, "${titleBase}_left_ul", COLOR_HIGHLIGHT_UNDER, underStrokeW, dashed, Paint.Cap.BUTT, mv.paintDensity))
        out.add(buildPolyline(rightPts, "${titleBase}_right_ul", COLOR_HIGHLIGHT_UNDER, underStrokeW, dashed, Paint.Cap.BUTT, mv.paintDensity))
    }

    out.add(buildPolyline(leftPts, "${titleBase}_left", color, strokeWidth, dashed, Paint.Cap.BUTT, mv.paintDensity))
    out.add(buildPolyline(rightPts, "${titleBase}_right", color, strokeWidth, dashed, Paint.Cap.BUTT, mv.paintDensity))
}

/** Add semi-circle caps at p1 and p2 to close the corridor band.
 *  Each cap is an 18-point arc connecting the left and right parallel edges,
 *  curving outward (away from the corridor center). */
private fun addSemiCircleCaps(
    mv: MapView,
    out: MutableList<Overlay>,
    p1: LatLng,
    p2: LatLng,
    halfWidthM: Double,
    bearingDeg: Double,
    titleBase: String,
    color: Int,
    strokeWidth: Float,
    dashed: Boolean = true,
    isSelected: Boolean = false,
    /** The selection's under-stroke add in px; zero when the caller inherits none. */
    underStrokeAddPx: Float = 0f
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

    // Dark under-stroke caps for selected markers
    if (isSelected) {
        val underStrokeW = strokeWidth + underStrokeAddPx
        out.add(buildPolyline(p1Arc, "${titleBase}_cap_p1_ul", COLOR_HIGHLIGHT_UNDER, underStrokeW, dashed, Paint.Cap.BUTT, mv.paintDensity))
        out.add(buildPolyline(p2Arc, "${titleBase}_cap_p2_ul", COLOR_HIGHLIGHT_UNDER, underStrokeW, dashed, Paint.Cap.BUTT, mv.paintDensity))
    }

    out.add(buildPolyline(p1Arc, "${titleBase}_cap_p1", color, strokeWidth, dashed, Paint.Cap.BUTT, mv.paintDensity))
    out.add(buildPolyline(p2Arc, "${titleBase}_cap_p2", color, strokeWidth, dashed, Paint.Cap.BUTT, mv.paintDensity))
}

/** Build a [Polyline] with the given [geoPoints], [title], [color], and [strokeWidth].
 *  [dashed] = true → DashPathEffect; false → solid line (used for proximity previews). */
private fun buildPolyline(
    geoPoints: List<LatLng>,
    title: String,
    color: Int,
    strokeWidth: Float,
    dashed: Boolean = true,
    strokeCap: Paint.Cap = Paint.Cap.ROUND,
    /**
     * The map's density — required, because the dashed case's dash is dp and the paint takes px.
     * Every caller names it: a `1f` default here once let a dashed caller draw the 3× reference's
     * dash unscaled rather than at the map's own density.
     */
    density: Float
): Polyline {
    return Polyline().apply {
        this.title = title
        outlinePaint.color = color
        outlinePaint.strokeWidth = strokeWidth
        if (dashed) {
            outlinePaint.pathEffect = DashPathEffect(
                floatArrayOf(
                    dpToPx(MARKER_DASH_ON_DP, density),
                    dpToPx(MARKER_DASH_OFF_DP, density)
                ),
                0f
            )
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
 * A keyed bitmap store: [take] rasterises once per key and hands the same [Bitmap] back after, and
 * [prune] drops every key no [take] touched since the last prune — the structural pass's way of letting
 * a settings change retire the old sizes without a build re-drawing what it already holds.
 */
private class BitmapPool<K> {
    private val built = HashMap<K, Bitmap>()
    private val touched = HashSet<K>()

    fun take(key: K, build: () -> Bitmap): Bitmap {
        touched.add(key)
        return built.getOrPut(key, build)
    }

    fun prune() {
        built.keys.retainAll(touched)
        touched.clear()
    }

    fun clear() {
        built.clear()
        touched.clear()
    }
}

/**
 * The bitmaps one map's markers share. A dot, a halo ring or an icon glyph is rasterised once per distinct
 * shape — colour and size, not marker — and every marker wearing it gets its own [BitmapDrawable] over the
 * same pixels, so a selection toggle swaps a marker's gold dot or its halo for one already drawn. The
 * pools are per [MapView] because the density is.
 */
private class MarkerBitmaps(private val mv: MapView) {
    private data class DotKey(val color: Int, val radiusMultiplier: Float)
    private data class HaloKey(
        val spec: MarkerHaloSpec,
        val anchorRadiusPx: Float,
        val sizePct: Int,
        val zoomPct: Int,
        val dimFraction: Float
    )
    private data class IconKey(val text: String, val color: Int, val alpha: Int, val zoomFactor: Float)

    private val dots = BitmapPool<DotKey>()
    private val halos = BitmapPool<HaloKey>()
    private val icons = BitmapPool<IconKey>()

    fun dot(color: Int, radiusMultiplier: Float): Bitmap =
        dots.take(DotKey(color, radiusMultiplier)) { createDotBitmap(color, mv.paintDensity, radiusMultiplier) }

    fun halo(spec: MarkerHaloSpec, anchorRadiusPx: Float, sizePct: Int, zoomPct: Int, dimFraction: Float): Bitmap =
        halos.take(HaloKey(spec, anchorRadiusPx, sizePct, zoomPct, dimFraction)) {
            MarkerHalo.createBitmap(spec, anchorRadiusPx, mv.paintDensity, sizePct, zoomPct, dimFraction)
        }

    fun icon(text: String, color: Int, alpha: Int, zoomFactor: Float): Bitmap =
        icons.take(IconKey(text, color, alpha, zoomFactor)) { createIconBitmap(text, color, alpha, zoomFactor) }

    /** Drop what the structural build just finished did not use. */
    fun prune() {
        dots.prune()
        halos.prune()
        icons.prune()
    }

    fun clear() {
        dots.clear()
        halos.clear()
        icons.clear()
    }
}

/**
 * Create a simple filled-circle [Bitmap] to use as a marker icon.
 * Pooled by colour and radius in [MarkerBitmaps] to avoid repeated allocations.
 * @param radiusMultiplier Scale factor for the dot radius (1.0 = normal, 1.5 = under-stroke).
 */
private fun createDotBitmap(color: Int, density: Float, radiusMultiplier: Float = 1.0f): Bitmap {
    val radiusPx = (DOT_RADIUS_DP * density * radiusMultiplier).toInt()
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
 * Rasterise an icon glyph [text] in [color] at [alpha] (255 = opaque; an auto-marker's reduced opacity
 * is below it). Icon bitmap and glyph scale with the marker zoom (100 % = 64px / 48f glyph).
 */
private fun createIconBitmap(text: String, color: Int, alpha: Int, zoomFactor: Float): Bitmap {
    val iconSizePx = (ICON_BITMAP_PX * zoomFactor).roundToInt().coerceAtLeast(8)
    val iconCenter = iconSizePx / 2f
    val bitmap = Bitmap.createBitmap(iconSizePx, iconSizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint().apply {
        this.color = color
        textSize = ICON_TEXT_PX * zoomFactor
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }
    if (alpha < 255) paint.alpha = alpha
    // Center the glyph vertically on the bitmap center. drawText's y is the text
    // BASELINE, not the glyph center, so measure the glyph bounds and shift the
    // baseline so the glyph's visual center == iconCenter.
    val bounds = Rect()
    paint.getTextBounds(text, 0, text.length, bounds)
    val baseline = iconCenter - (bounds.top + bounds.bottom) / 2f
    canvas.drawText(text, iconCenter, baseline, paint)
    return bitmap
}

/**
 * The routing-weight-only marker's fill texture: a tiled diagonal hatch in [color] — the fill's own
 * colour and alpha, so the difference from a plain marker is a stripe pattern rather than a tint.
 *
 * The tile carries the colour at full alpha because a paint's colour is ignored once a shader is set, and
 * the tile is built per routing-only marker per rebuild: the count of those is small, and the pattern is
 * colour-specific, so one shared tile would have to be re-tinted anyway.
 *
 * Its two dp figures are `marker.routingOnly.stripeSpacingDp` and `.stripeWidthDp` in maro.properties,
 * read through [AppConfig], so the file and the map agree on the texture the stripes draw.
 */
private fun hatchShader(color: Int, density: Float): BitmapShader {
    val periodPx = dpToPx(AppConfig.markerRoutingOnlyStripeSpacingDp, density).roundToInt().coerceAtLeast(2)
    val stripePx = dpToPx(AppConfig.markerRoutingOnlyStripeWidthDp, density).coerceAtLeast(1f)
    val bitmap = Bitmap.createBitmap(periodPx, periodPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val stripePaint = Paint().apply {
        this.color = color
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = stripePx
    }
    // Three passes of the same 45° line, so the tile repeats seamlessly on both axes.
    val p = periodPx.toFloat()
    canvas.drawLine(0f, p, p, 0f, stripePaint)
    canvas.drawLine(-p / 2f, p / 2f, p / 2f, -p / 2f, stripePaint)
    canvas.drawLine(p / 2f, p * 1.5f, p * 1.5f, p / 2f, stripePaint)
    return BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
}

/**
 * Dim an ARGB colour by reducing its alpha channel to [alphaFraction] of the original.
 * E.g. alphaFraction=0.25f → 25% alpha, other channels unchanged.
 */
private fun dimColor(color: Int, alphaFraction: Float): Int {
    val newAlpha = ((color ushr 24) * alphaFraction).toInt().coerceIn(0, 255)
    return (newAlpha shl 24) or (color and 0x00FFFFFF)
}
