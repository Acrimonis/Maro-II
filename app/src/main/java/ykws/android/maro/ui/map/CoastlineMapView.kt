package ykws.android.maro.ui.map

import ykws.android.maro.R
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.depth.DepthConstants
import ykws.android.maro.data.model.BoundingBox
import ykws.android.maro.data.model.CoastlineSegment
import ykws.android.maro.data.model.GenerationProgress
import ykws.android.maro.data.model.Isobath
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.Zone300Data
import ykws.android.maro.data.regulation.RegulatedZoneSet
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

/**
 * The map's own zoom range — one home for the pair. The view enforces it on its `MapView`, and the
 * marker focus framing clamps the zoom it asks for to the same bounds, so the two cannot disagree.
 */
internal const val MAP_MIN_ZOOM = 11.0
internal const val MAP_MAX_ZOOM = 20.0

/**
 * The card face of [MapBanner] for first-run generation: the banner family's container with this
 * card's own interior — spinner, title, phase and the progress bar. Full width, the family's border
 * colour and the band's clearance, all read from `docs/ui-component-guidelines.md` §5.7.
 */
@Composable
internal fun LoadingOverlay(
    progress: GenerationProgress,
    tagsDrawn: Boolean,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.map_loading_coastline)
) {
    MapBanner(
        borderColor = ComposeColor(AppConfig.uiDashboardBackground),
        tagsDrawn = tagsDrawn,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.5.dp,
                color = ComposeColor(AppConfig.uiProgressAccent)
            )

            Text(
                text = title,
                color = ComposeColor.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )

            // NOTE: the per-phase label below is still emitted as French literals by the
            // data/spatial generators (CoastlineGenerator/DepthGenerator/Zone300Builder).
            // Localising it requires threading a phase enum through onProgress — tracked as
            // a follow-up; it only shows during first-run generation.
            if (progress.phase.isNotEmpty()) {
                Text(
                    text = progress.phase,
                    color = ComposeColor.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress = { progress.progress / 100f },
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp),
                    color = ComposeColor(AppConfig.uiProgressAccent),
                    trackColor = ComposeColor(AppConfig.uiProgressTrack)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${progress.progress}%",
                    color = ComposeColor.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * The card face of [MapBanner] for a failed coastline load or a failed track-info populate: the
 * family's container with this card's own interior — title, message and Retry. Full width, the danger
 * border colour and the band's clearance, all read from `docs/ui-component-guidelines.md` §5.7.
 */
@Composable
internal fun ErrorOverlay(
    message: String,
    onRetry: () -> Unit,
    tagsDrawn: Boolean,
    modifier: Modifier = Modifier
) {
    MapBanner(
        borderColor = ComposeColor(AppConfig.uiDashboardZoneDanger),
        tagsDrawn = tagsDrawn,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.error_title),
                color = ComposeColor(AppConfig.uiErrorButtonText),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = message,
                color = ComposeColor(AppConfig.uiToastText),
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ComposeColor(AppConfig.uiErrorButtonBackground)
                )
            ) {
                Text(
                    text = stringResource(R.string.retry),
                    color = ComposeColor(AppConfig.uiErrorButtonText),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
internal fun CoastlineMapView(
    segments: List<CoastlineSegment>,
    coastlineMainlandColor: Int,
    coastlineIslandColor: Int,
    coastlineWidthPx: Float,
    coastlineTransparencyPct: Int,
    regulatedZones: RegulatedZoneSet?,
    regulatedZoneFillTransparencyPct: Int,
    regulatedZoneBoundaryTransparencyPct: Int,
    regulatedZoneOutlineWidthPx: Float,
    zone300: Zone300Data?,
    zone300Color: Int,
    zone300FillTransparencyPct: Int,
    zone300BoundaryTransparencyPct: Int,
    zone300BoundaryWidthPx: Float,
    depthBitmap: Bitmap?,
    lowDepthWarningBitmap: Bitmap?,
    depthBox: BoundingBox?,
    isobaths: List<Isobath>,
    zoomLevel: Double,
    center: LatLng,
    initialZoom: Double,
    onCenterChanged: (Double, Double) -> Unit = { _, _ -> },
    onZoomChanged: (Double) -> Unit = {},
    onMapViewReady: (MapView) -> Unit = {},
    modifier: Modifier = Modifier,
    centerOffsetYPx: Int = 0,
) {
    val context = LocalContext.current
    val localMapView = remember { mutableStateOf<MapView?>(null) }
    // Per-layer persistent overlay tracker — survives recompositions so we can
    // selectively rebuild only layers whose input data actually changed.
    val tracker = remember { OverlayTracker() }

    // ── Zoom gates, never the zoom value ─────────────────────────────────────
    // Every layer below draws the same geometry at each level above its floor, so the effects key on
    // these gates: a zoom step inside a band rebuilds nothing. The floors live with the drawing code
    // (`DepthConstants`, [ZONE_MIN_ZOOM], [REGULATED_ZONE_MIN_ZOOM]) and are never restated here.
    val depthRasterDraws = zoomLevel >= DepthConstants.DEPTH_MAP_MIN_DRAW_ZOOM
    val isobathDraws = zoomLevel >= DepthConstants.ISOBATH_MIN_DRAW_ZOOM
    val shallowIsobathDraws = zoomLevel >= DepthConstants.SHALLOW_ISOBATH_MIN_ZOOM
    val zone300Draws = zoomLevel >= ZONE_MIN_ZOOM
    val regulatedZoneDraws = zoomLevel >= REGULATED_ZONE_MIN_ZOOM
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            Configuration.getInstance().apply {
                userAgentValue = ctx.packageName
                osmdroidTileCache = java.io.File(ctx.cacheDir, "tiles").also { it.mkdirs() }
            }

            @Suppress("DEPRECATION")
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                setBuiltInZoomControls(false)
                minZoomLevel = MAP_MIN_ZOOM
                maxZoomLevel = MAP_MAX_ZOOM
                controller.setZoom(initialZoom)
                controller.setCenter(GeoPoint(center.latitude, center.longitude))
                if (centerOffsetYPx != 0) {
                    setMapCenterOffset(0, centerOffsetYPx)
                }
                // Draw all layers bottom-to-top; each appends to both mapView.overlays
                // and the corresponding tracker list for later selective rebuild.
                drawDepthMap(this, depthBitmap, depthBox, zoomLevel, tracker.depth)
                drawLowDepthWarning(this, lowDepthWarningBitmap, depthBox, zoomLevel, tracker.lowDepth)
                // Every contour is attached once here; the two floors then ride on each polyline's
                // `enabled` flag, so a gate crossing never rebuilds the set.
                drawIsobaths(this, isobaths, tracker.isobaths, tracker.isobathsShallow)
                applyIsobathGates(tracker, isobathDraws, shallowIsobathDraws)
                drawRegulatedZones(this, regulatedZones, zoomLevel, regulatedZoneFillTransparencyPct, regulatedZoneBoundaryTransparencyPct, regulatedZoneOutlineWidthPx, tracker.regulatedZones)
                drawZone300(this, zone300, zoomLevel, zone300Color, zone300FillTransparencyPct, zone300BoundaryTransparencyPct, zone300BoundaryWidthPx, tracker.zone300)
                drawCoastline(this, segments, coastlineMainlandColor, coastlineIslandColor, coastlineWidthPx, coastlineTransparencyPct, tracker.coastline)

                // Seed per-layer last-known state so LaunchedEffects don't fire on first composition.
                tracker.lastDepthBitmap = depthBitmap
                tracker.lastDepthBox = depthBox
                tracker.lastDepthDraws = depthRasterDraws
                tracker.lastLowDepthBitmap = lowDepthWarningBitmap
                tracker.lastLowDepthDraws = depthRasterDraws
                tracker.lastIsobaths = isobaths
                tracker.lastRegulatedZones = regulatedZones
                tracker.lastRegZoneDraws = regulatedZoneDraws
                tracker.lastRegZoneFillTransparencyPct = regulatedZoneFillTransparencyPct
                tracker.lastRegZoneBoundaryTransparencyPct = regulatedZoneBoundaryTransparencyPct
                tracker.lastRegZoneOutlineWidthPx = regulatedZoneOutlineWidthPx
                tracker.lastZone300 = zone300
                tracker.lastZone300Draws = zone300Draws
                tracker.lastZone300Color = zone300Color
                tracker.lastZone300FillTransparencyPct = zone300FillTransparencyPct
                tracker.lastZone300BoundaryTransparencyPct = zone300BoundaryTransparencyPct
                tracker.lastZone300BoundaryWidthPx = zone300BoundaryWidthPx
                tracker.lastSegments = segments
                tracker.lastCoastlineMainlandColor = coastlineMainlandColor
                tracker.lastCoastlineIslandColor = coastlineIslandColor
                tracker.lastCoastlineWidthPx = coastlineWidthPx
                tracker.lastCoastlineTransparencyPct = coastlineTransparencyPct

                // Force-sync the ViewModel zoom level to match the actual MapView
                // zoom right after construction, so the boat marker immediately
                // renders at the correct size — no matter what the StateFlow held.
                onZoomChanged(this@apply.zoomLevelDouble)

                // Listen for map pan/zoom to report new center & zoom in real time
                addMapListener(object : MapListener {
                    override fun onScroll(event: ScrollEvent): Boolean {
                        val geo = this@apply.mapCenter
                        onCenterChanged(geo.latitude, geo.longitude)
                        return false
                    }

                    override fun onZoom(event: ZoomEvent): Boolean {
                        val geo = this@apply.mapCenter
                        onCenterChanged(geo.latitude, geo.longitude)
                        onZoomChanged(this@apply.zoomLevelDouble)
                        // No invalidate here: the map already repaints a zoom, and the measured median
                        // during a continuous gesture is unchanged by asking for a second frame's work.
                        // A gate crossing rebuilds its own layer and invalidates with it.
                        return false
                    }
                })
            }.also { mv ->
                localMapView.value = mv
                onMapViewReady(mv)
            }
        }
    )

    // ── Per-layer LaunchedEffect blocks ──────────────────────────────────────
    // Each keyed on only its own data + its zoom gate, with an early-return guard comparing against
    // the tracker's per-layer last-known state. The raw level still reaches the draw call, which gates
    // on it internally — the gate decides *when* to rebuild, the level decides *what* is drawn.

    // Zone300 layer
    LaunchedEffect(zone300, zone300Draws, zone300Color, zone300FillTransparencyPct, zone300BoundaryTransparencyPct, zone300BoundaryWidthPx) {
        val mv = localMapView.value ?: return@LaunchedEffect
        if (zone300 === tracker.lastZone300 &&
            zone300Draws == tracker.lastZone300Draws &&
            zone300Color == tracker.lastZone300Color &&
            zone300FillTransparencyPct == tracker.lastZone300FillTransparencyPct &&
            zone300BoundaryTransparencyPct == tracker.lastZone300BoundaryTransparencyPct &&
            zone300BoundaryWidthPx == tracker.lastZone300BoundaryWidthPx
        ) return@LaunchedEffect
        mv.overlays.removeAll(tracker.zone300)
        tracker.zone300.clear()
        drawZone300(mv, zone300, zoomLevel, zone300Color, zone300FillTransparencyPct, zone300BoundaryTransparencyPct, zone300BoundaryWidthPx, tracker.zone300)
        tracker.lastZone300 = zone300
        tracker.lastZone300Draws = zone300Draws
        tracker.lastZone300Color = zone300Color
        tracker.lastZone300FillTransparencyPct = zone300FillTransparencyPct
        tracker.lastZone300BoundaryTransparencyPct = zone300BoundaryTransparencyPct
        tracker.lastZone300BoundaryWidthPx = zone300BoundaryWidthPx
        OverlayZOrder.reorder(mv)
        mv.invalidate()
    }

    // Regulated zones layer
    LaunchedEffect(regulatedZones, regulatedZoneDraws, regulatedZoneFillTransparencyPct, regulatedZoneBoundaryTransparencyPct, regulatedZoneOutlineWidthPx) {
        val mv = localMapView.value ?: return@LaunchedEffect
        // The appearance values belong in the guard, not only in the keys: a slider commit leaves
        // the zone set and the gate untouched, so without these three the effect would return early
        // and the setting would look dead until the next gate crossing.
        if (regulatedZones === tracker.lastRegulatedZones &&
            regulatedZoneDraws == tracker.lastRegZoneDraws &&
            regulatedZoneFillTransparencyPct == tracker.lastRegZoneFillTransparencyPct &&
            regulatedZoneBoundaryTransparencyPct == tracker.lastRegZoneBoundaryTransparencyPct &&
            regulatedZoneOutlineWidthPx == tracker.lastRegZoneOutlineWidthPx
        ) return@LaunchedEffect
        mv.overlays.removeAll(tracker.regulatedZones)
        tracker.regulatedZones.clear()
        drawRegulatedZones(mv, regulatedZones, zoomLevel, regulatedZoneFillTransparencyPct, regulatedZoneBoundaryTransparencyPct, regulatedZoneOutlineWidthPx, tracker.regulatedZones)
        tracker.lastRegulatedZones = regulatedZones
        tracker.lastRegZoneDraws = regulatedZoneDraws
        tracker.lastRegZoneFillTransparencyPct = regulatedZoneFillTransparencyPct
        tracker.lastRegZoneBoundaryTransparencyPct = regulatedZoneBoundaryTransparencyPct
        tracker.lastRegZoneOutlineWidthPx = regulatedZoneOutlineWidthPx
        OverlayZOrder.reorder(mv)
        mv.invalidate()
    }

    // Depth colour raster layer
    LaunchedEffect(depthBitmap, depthBox, depthRasterDraws) {
        val mv = localMapView.value ?: return@LaunchedEffect
        if (depthBitmap === tracker.lastDepthBitmap &&
            depthBox === tracker.lastDepthBox &&
            depthRasterDraws == tracker.lastDepthDraws
        ) return@LaunchedEffect
        mv.overlays.removeAll(tracker.depth)
        tracker.depth.clear()
        drawDepthMap(mv, depthBitmap, depthBox, zoomLevel, tracker.depth)
        tracker.lastDepthBitmap = depthBitmap
        tracker.lastDepthBox = depthBox
        tracker.lastDepthDraws = depthRasterDraws
        OverlayZOrder.reorder(mv)
        mv.invalidate()
    }

    // Low-depth warning layer
    LaunchedEffect(lowDepthWarningBitmap, depthBox, depthRasterDraws) {
        val mv = localMapView.value ?: return@LaunchedEffect
        if (lowDepthWarningBitmap === tracker.lastLowDepthBitmap &&
            depthRasterDraws == tracker.lastLowDepthDraws
        ) return@LaunchedEffect
        mv.overlays.removeAll(tracker.lowDepth)
        tracker.lowDepth.clear()
        drawLowDepthWarning(mv, lowDepthWarningBitmap, depthBox, zoomLevel, tracker.lowDepth)
        tracker.lastLowDepthBitmap = lowDepthWarningBitmap
        tracker.lastLowDepthDraws = depthRasterDraws
        OverlayZOrder.reorder(mv)
        mv.invalidate()
    }

    // Isobaths layer — attach once, gate by flag. A crossing used to drop and re-create every
    // contour polyline and re-stack the overlay list, which measured about 0.7 s a crossing: seven
    // such frames in one fifteen-second wide sweep, the pause felt mid-stroke. Now the set is built
    // only when the data changes, and a crossing writes one boolean per polyline.
    LaunchedEffect(isobaths, isobathDraws, shallowIsobathDraws) {
        val mv = localMapView.value ?: return@LaunchedEffect
        if (isobaths !== tracker.lastIsobaths) {
            mv.overlays.removeAll(tracker.isobaths)
            tracker.isobaths.clear()
            tracker.isobathsShallow.clear()
            drawIsobaths(mv, isobaths, tracker.isobaths, tracker.isobathsShallow)
            tracker.lastIsobaths = isobaths
            OverlayZOrder.reorder(mv)
        }
        applyIsobathGates(tracker, isobathDraws, shallowIsobathDraws)
        mv.invalidate()
    }

    // Coastline layer
    LaunchedEffect(segments, coastlineMainlandColor, coastlineIslandColor, coastlineWidthPx, coastlineTransparencyPct) {
        val mv = localMapView.value ?: return@LaunchedEffect
        // All four appearance values are guarded, not only keyed: the segment list is untouched by a
        // colour or width change, so a guard on `segments` alone would draw the new look once and
        // then ignore every later edit.
        if (segments === tracker.lastSegments &&
            coastlineMainlandColor == tracker.lastCoastlineMainlandColor &&
            coastlineIslandColor == tracker.lastCoastlineIslandColor &&
            coastlineWidthPx == tracker.lastCoastlineWidthPx &&
            coastlineTransparencyPct == tracker.lastCoastlineTransparencyPct
        ) return@LaunchedEffect
        mv.overlays.removeAll(tracker.coastline)
        tracker.coastline.clear()
        drawCoastline(mv, segments, coastlineMainlandColor, coastlineIslandColor, coastlineWidthPx, coastlineTransparencyPct, tracker.coastline)
        tracker.lastSegments = segments
        tracker.lastCoastlineMainlandColor = coastlineMainlandColor
        tracker.lastCoastlineIslandColor = coastlineIslandColor
        tracker.lastCoastlineWidthPx = coastlineWidthPx
        tracker.lastCoastlineTransparencyPct = coastlineTransparencyPct
        OverlayZOrder.reorder(mv)
        mv.invalidate()
    }

    // ── Map center offset: reactively update when speed changes ────────────
    LaunchedEffect(centerOffsetYPx, localMapView.value) {
        localMapView.value?.setMapCenterOffset(0, centerOffsetYPx)
    }

    // ── Cone + dashed line: DISABLED — see more-dedebug subfeature ──────────────
}

/**
 * Applies the two isobath floors to the already-attached polylines — a flag write, never a rebuild.
 * The shallow group is written twice on purpose, so the shallower floor wins for the 2 m lines.
 */
private fun applyIsobathGates(
    tracker: OverlayTracker,
    isobathDraws: Boolean,
    shallowIsobathDraws: Boolean
) {
    val shallow = isobathDraws && shallowIsobathDraws
    for (poly in tracker.isobaths) poly.isEnabled = isobathDraws
    for (poly in tracker.isobathsShallow) poly.isEnabled = shallow
}
