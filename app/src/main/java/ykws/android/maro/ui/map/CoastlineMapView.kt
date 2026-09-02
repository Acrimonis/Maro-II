package ykws.android.maro.ui.map

import ykws.android.maro.R
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.BoundingBox
import ykws.android.maro.data.model.CoastlineSegment
import ykws.android.maro.data.model.GenerationProgress
import ykws.android.maro.data.model.Isobath
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.Zone300Data
import ykws.android.maro.data.regulation.RegulatedZoneSet
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
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

@Composable
internal fun LoadingOverlay(
    progress: GenerationProgress,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.map_loading_coastline)
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = ComposeColor(AppConfig.buttonActionBgColor),
        shadowElevation = 8.dp,
        modifier = modifier
            .fillMaxWidth()
            .border(2.dp, ComposeColor(AppConfig.uiDashboardBackground), RoundedCornerShape(14.dp))
    ) {
        Box(modifier = Modifier.background(ComposeColor(AppConfig.uiCardBackground))) {
            Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
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
}

@Composable
internal fun ErrorOverlay(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = ComposeColor(AppConfig.buttonActionBgColor),
        shadowElevation = 8.dp,
        modifier = modifier
            .fillMaxWidth()
            .border(2.dp, ComposeColor(AppConfig.uiDashboardZoneDanger), RoundedCornerShape(14.dp))
    ) {
        Box(modifier = Modifier.background(ComposeColor(AppConfig.uiCardBackground))) {
            Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
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
                color = ComposeColor(AppConfig.uiSettingsToastText),
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
}

@Composable
internal fun CoastlineMapView(
    segments: List<CoastlineSegment>,
    regulatedZones: RegulatedZoneSet?,
    zone300: Zone300Data?,
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
                minZoomLevel = 8.0
                maxZoomLevel = 18.0
                controller.setZoom(initialZoom)
                controller.setCenter(GeoPoint(center.latitude, center.longitude))
                if (centerOffsetYPx != 0) {
                    setMapCenterOffset(0, centerOffsetYPx)
                }
                // Draw all layers bottom-to-top; each appends to both mapView.overlays
                // and the corresponding tracker list for later selective rebuild.
                drawDepthMap(this, depthBitmap, depthBox, zoomLevel, tracker.depth)
                drawLowDepthWarning(this, lowDepthWarningBitmap, depthBox, zoomLevel, tracker.lowDepth)
                drawIsobaths(this, isobaths, zoomLevel, tracker.isobaths)
                drawRegulatedZones(this, regulatedZones, zoomLevel, tracker.regulatedZones)
                drawZone300(this, zone300, zoomLevel, tracker.zone300)
                drawCoastline(this, segments, tracker.coastline)

                // Seed per-layer last-known state so LaunchedEffects don't fire on first composition.
                tracker.lastDepthBitmap = depthBitmap
                tracker.lastDepthBox = depthBox
                tracker.lastDepthZoom = zoomLevel
                tracker.lastLowDepthBitmap = lowDepthWarningBitmap
                tracker.lastLowDepthZoom = zoomLevel
                tracker.lastIsobaths = isobaths
                tracker.lastIsobathZoom = zoomLevel
                tracker.lastRegulatedZones = regulatedZones
                tracker.lastRegZoneZoom = zoomLevel
                tracker.lastZone300 = zone300
                tracker.lastZone300Zoom = zoomLevel
                tracker.lastSegments = segments

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
    // Each keyed on only its own data + zoomLevel, with an early-return guard
    // comparing against the tracker's per-layer last-known state.

    // Zone300 layer
    LaunchedEffect(zone300, zoomLevel) {
        val mv = localMapView.value ?: return@LaunchedEffect
        if (zone300 === tracker.lastZone300 && zoomLevel == tracker.lastZone300Zoom) return@LaunchedEffect
        mv.overlays.removeAll(tracker.zone300)
        tracker.zone300.clear()
        drawZone300(mv, zone300, zoomLevel, tracker.zone300)
        tracker.lastZone300 = zone300
        tracker.lastZone300Zoom = zoomLevel
        mv.invalidate()
    }

    // Regulated zones layer
    LaunchedEffect(regulatedZones, zoomLevel) {
        val mv = localMapView.value ?: return@LaunchedEffect
        if (regulatedZones === tracker.lastRegulatedZones && zoomLevel == tracker.lastRegZoneZoom) return@LaunchedEffect
        mv.overlays.removeAll(tracker.regulatedZones)
        tracker.regulatedZones.clear()
        drawRegulatedZones(mv, regulatedZones, zoomLevel, tracker.regulatedZones)
        tracker.lastRegulatedZones = regulatedZones
        tracker.lastRegZoneZoom = zoomLevel
        mv.invalidate()
    }

    // Depth colour raster layer
    LaunchedEffect(depthBitmap, depthBox, zoomLevel) {
        val mv = localMapView.value ?: return@LaunchedEffect
        if (depthBitmap === tracker.lastDepthBitmap &&
            depthBox === tracker.lastDepthBox &&
            zoomLevel == tracker.lastDepthZoom
        ) return@LaunchedEffect
        mv.overlays.removeAll(tracker.depth)
        tracker.depth.clear()
        drawDepthMap(mv, depthBitmap, depthBox, zoomLevel, tracker.depth)
        tracker.lastDepthBitmap = depthBitmap
        tracker.lastDepthBox = depthBox
        tracker.lastDepthZoom = zoomLevel
        mv.invalidate()
    }

    // Low-depth warning layer
    LaunchedEffect(lowDepthWarningBitmap, depthBox, zoomLevel) {
        val mv = localMapView.value ?: return@LaunchedEffect
        if (lowDepthWarningBitmap === tracker.lastLowDepthBitmap &&
            zoomLevel == tracker.lastLowDepthZoom
        ) return@LaunchedEffect
        mv.overlays.removeAll(tracker.lowDepth)
        tracker.lowDepth.clear()
        drawLowDepthWarning(mv, lowDepthWarningBitmap, depthBox, zoomLevel, tracker.lowDepth)
        tracker.lastLowDepthBitmap = lowDepthWarningBitmap
        tracker.lastLowDepthZoom = zoomLevel
        mv.invalidate()
    }

    // Isobaths layer
    LaunchedEffect(isobaths, zoomLevel) {
        val mv = localMapView.value ?: return@LaunchedEffect
        if (isobaths === tracker.lastIsobaths && zoomLevel == tracker.lastIsobathZoom) return@LaunchedEffect
        mv.overlays.removeAll(tracker.isobaths)
        tracker.isobaths.clear()
        drawIsobaths(mv, isobaths, zoomLevel, tracker.isobaths)
        tracker.lastIsobaths = isobaths
        tracker.lastIsobathZoom = zoomLevel
        mv.invalidate()
    }

    // Coastline layer
    LaunchedEffect(segments) {
        val mv = localMapView.value ?: return@LaunchedEffect
        if (segments === tracker.lastSegments) return@LaunchedEffect
        mv.overlays.removeAll(tracker.coastline)
        tracker.coastline.clear()
        drawCoastline(mv, segments, tracker.coastline)
        tracker.lastSegments = segments
        mv.invalidate()
    }

    // ── Map center offset: reactively update when speed changes ────────────
    LaunchedEffect(centerOffsetYPx, localMapView.value) {
        localMapView.value?.setMapCenterOffset(0, centerOffsetYPx)
    }

    // ── Cone + dashed line: DISABLED — see more-dedebug subfeature ──────────────
}
