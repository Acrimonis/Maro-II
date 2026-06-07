package ykws.android.maro.ui.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.view.MotionEvent
import android.graphics.Bitmap
import android.graphics.Color
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import ykws.android.maro.R
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.core.content.ContextCompat
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
import org.osmdroid.views.overlay.GroundOverlay
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ykws.android.maro.data.depth.DepthConstants
import ykws.android.maro.data.model.BoundingBox
import ykws.android.maro.data.model.CoastlinePoint
import ykws.android.maro.data.model.CoastlineSegment
import ykws.android.maro.data.model.CoastlineState
import ykws.android.maro.data.model.DepthSample
import ykws.android.maro.data.model.DepthState
import ykws.android.maro.data.model.GenerationProgress
import ykws.android.maro.data.model.Isobath
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.ValidationReport
import ykws.android.maro.data.model.Zone300Data
import ykws.android.maro.data.settings.AppSettings

/**
 * Compose screen rendering the coastline on an OSMdroid map.
 *
 * Landscape: dashboard panel anchored to the left edge
 * (width = screen height ÷ 2, full height), map fills the right area.
 *
 * Portrait: map on top, dashboard bar at the bottom.
 */
@SuppressLint("ClickableViewAccessibility")
@Composable
fun MapScreen(
    viewModel: CoastlineViewModel,
    depthViewModel: DepthViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val mapCenter by viewModel.mapCenter.collectAsState()
    val isWater by viewModel.isWater.collectAsState()
    val distanceToShore by viewModel.distanceToShore.collectAsState()
    val zoomLevel by viewModel.zoomLevel.collectAsState()
    val zone300 by viewModel.zone300.collectAsState()
    val inZone300 by viewModel.inZone300.collectAsState()
    val distanceToZone by viewModel.distanceToZone.collectAsState()
    val appSettings by viewModel.settings.collectAsState()
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var showSettings by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val autoFollowSuppressed by viewModel.autoFollowSuppressed.collectAsState()
    val speedKnots by viewModel.speedKnots.collectAsState()

    // GPS permission launcher: on grant, enable GPS mode; on deny, stay in demo mode.
    val gpsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.updateSettings { it.copy(gpsMode = true) }
    }
    // Permission-aware handler wired to the GPS settings switch.
    val onGpsModeChange: (Boolean) -> Unit = { enable ->
        if (enable) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) viewModel.updateSettings { it.copy(gpsMode = true) }
            else gpsPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            viewModel.updateSettings { it.copy(gpsMode = false) }
        }
    }

    // ── Force marker to match MapView zoom once the view is ready ────────
    // Even though _zoomLevel is seeded from persisted settings, there can be
    // a frame where collectAsState() captures the initial default before the
    // seeded value propagates.  This LaunchedEffect re-applies the real zoom
    // from the MapView after it's created, guaranteeing the marker is correct.
    LaunchedEffect(mapView) {
        val mv = mapView ?: return@LaunchedEffect
        viewModel.updateZoomLevel(mv.zoomLevelDouble)
        // Detect user pan/zoom touches so GPS mode can pause auto-follow while the user explores.
        mv.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> viewModel.notifyUserInteraction()
            }
            false // don't consume — the map still pans/zooms normally
        }
    }

    // ── GPS auto-follow: capped recenter + heading-up (rules 1–3) ─────────
    // One throttled stream (≤ appSettings.mapRefreshFps) drives BOTH position and
    // orientation via a single setCenter + mapOrientation per tick — replacing the
    // per-fix animateTo, whose scroll animation repainted the whole map at ~60 fps.
    // The effect is keyed on gpsMode/suppression/mapView, so it restarts each time
    // auto-follow re-engages (GPS on, or the recenter delay expiring after a pan):
    // the FIRST target then animates (smooth scroll back), and every subsequent fix
    // uses the cheap capped setCenter. Manual pinch/pan/fling keep osmdroid's own
    // full-rate path — the cap governs only this GPS-follow flow.
    LaunchedEffect(appSettings.gpsMode, autoFollowSuppressed, mapView) {
        val mv = mapView ?: return@LaunchedEffect
        if (!appSettings.gpsMode) { mv.mapOrientation = 0f; mv.invalidate(); return@LaunchedEffect }
        if (autoFollowSuppressed) return@LaunchedEffect
        var reengage = true
        viewModel.cameraUpdates.collect { target ->
            val point = GeoPoint(target.position.latitude, target.position.longitude)
            if (reengage) {
                // Scroll smoothly back to the GPS position when follow resumes (no snap).
                mv.controller.animateTo(point)
                reengage = false
            } else {
                mv.controller.setCenter(point)
            }
            mv.mapOrientation = -target.bearingDeg
            mv.invalidate()
            // Keep depth-at-center following the GPS fix at the same capped cadence.
            depthViewModel.updateMapCenter(target.position.latitude, target.position.longitude)
        }
    }

    // ── Depth layer ─────────────────────────────────────────────────────────────
    val depthState by depthViewModel.state.collectAsState()
    val depthRender by depthViewModel.renderModel.collectAsState()
    val depthAtCenter by depthViewModel.depthAtCenter.collectAsState()
    val depthGrid = (depthState as? DepthState.Ready)?.grid
    val depthValidation = depthGrid?.metadata?.validation
    val isobaths = depthRender?.isobaths ?: emptyList()

    // Rasterise the colour map once per grid, off the main thread (~3 M cells).
    val depthBitmap by produceState<Bitmap?>(initialValue = null, depthGrid) {
        value = depthGrid?.let { g -> withContext(Dispatchers.Default) { DepthBitmap.build(g) } }
    }

    // The map centre drives BOTH layers: coastline (distance/zone) and depth-at-centre.
    val onCenterChanged: (Double, Double) -> Unit = remember(viewModel, depthViewModel) {
        { lat, lon ->
            viewModel.updateMapCenter(lat, lon)
            depthViewModel.updateMapCenter(lat, lon)
        }
    }

    // ── Save map position on pause (covers kill, background, minimize) ────
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    viewModel.savePosition()
                    viewModel.setGpsActive(false)
                }
                Lifecycle.Event.ON_RESUME -> viewModel.setGpsActive(true)
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // ── Intercept system back when settings are open ──────────────────
        if (showSettings) {
            BackHandler { showSettings = false }
        }

        // ── Main content (map + dashboard) ────────────────────────────────
        // Note: MapContent is kept at a STABLE composition slot (always a direct child of Box)
        // so the underlying MapView (AndroidView) is never recreated on orientation switch.
        // The dashboard panel is overlaid via Modifier.align() in the orientation branch.
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isLandscape = maxWidth > maxHeight
            val landscapeDashboardWidth = maxHeight * 2 / 3 // dashboard width = ⅔ screen height
            val portraitDashboardHeight = maxWidth * 2 / 3  // mirror landscape: ⅔ of the short side

            // Map fills the box, padded to leave room for the dashboard overlay.
            // Stable composition slot — never inside an if/else branch.
            MapContent(
                state = state,
                progress = progress,
                mapCenter = mapCenter,
                isWater = isWater,
                zoomLevel = zoomLevel,
                distanceToShore = distanceToShore,
                zone300 = zone300,
                depthBitmap = depthBitmap,
                depthBox = depthGrid?.boundingBox,
                isobaths = isobaths,
                appSettings = appSettings,
                mapView = mapView,
                onCenterChanged = onCenterChanged,
                onZoomChanged = viewModel::updateZoomLevel,
                onMapViewReady = { mapView = it },
                onRetry = { viewModel.loadCoastline() },
                onOpenSettings = { showSettings = true },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        if (isLandscape) PaddingValues(start = landscapeDashboardWidth, top = 0.dp, end = 0.dp, bottom = 0.dp)
                        else PaddingValues(start = 0.dp, top = 0.dp, end = 0.dp, bottom = portraitDashboardHeight)
                    )
            )

            // Dashboard overlaid on top, positioned via alignment.
            if (isLandscape) {
                DashboardPanel(
                    state = state,
                    isWater = isWater,
                    distanceToShore = distanceToShore,
                    inZone300 = inZone300,
                    distanceToZone = distanceToZone,
                    depthSample = depthAtCenter,
                    validation = depthValidation,
                    speedKnots = speedKnots,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .width(landscapeDashboardWidth)
                        .fillMaxHeight()
                )
            } else {
                DashboardPanel(
                    state = state,
                    isWater = isWater,
                    distanceToShore = distanceToShore,
                    inZone300 = inZone300,
                    distanceToZone = distanceToZone,
                    depthSample = depthAtCenter,
                    validation = depthValidation,
                    speedKnots = speedKnots,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(portraitDashboardHeight)
                )
            }
        }

        // ── Settings overlay (full-screen, covers dashboard too) ──────────
        if (showSettings) {
            SettingsOverlay(
                settings = appSettings,
                onUpdateSettings = viewModel::updateSettings,
                onGpsModeChange = onGpsModeChange,
                onDismiss = { showSettings = false }
            )
        }
    }
}

// ── Map content area (shared by landscape & portrait) ────────────────────────

/**
 * Map view with overlays: zoom buttons, center marker, loading & error states.
 */
@Composable
private fun MapContent(
    state: CoastlineState,
    progress: GenerationProgress,
    mapCenter: LatLng,
    isWater: Boolean,
    zoomLevel: Double,
    distanceToShore: Double?,
    zone300: Zone300Data?,
    depthBitmap: Bitmap?,
    depthBox: BoundingBox?,
    isobaths: List<Isobath>,
    appSettings: AppSettings,
    mapView: MapView?,
    onCenterChanged: (Double, Double) -> Unit,
    onZoomChanged: (Double) -> Unit,
    onMapViewReady: (MapView) -> Unit,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.clipToBounds()) {
        // Memoize per state instance so panning (which does not change state) keeps a
        // stable list identity → no spurious overlay rebuilds.
        val allSegments = remember(state) {
            when (state) {
                is CoastlineState.Ready -> (state as CoastlineState.Ready).polylines
                else -> emptyList()
            }
        }
        // Apply coastline visibility toggle
        val segments = if (appSettings.coastlineVisible) allSegments else emptyList()
        // Apply zone300 visibility toggle
        val visibleZone300 = if (appSettings.zone300Visible) zone300 else null
        CoastlineMapView(
            segments = segments,
            zone300 = visibleZone300,
            depthBitmap = depthBitmap,
            depthBox = depthBox,
            isobaths = isobaths,
            zoomLevel = zoomLevel,
            center = mapCenter,
            initialZoom = zoomLevel,
            onCenterChanged = onCenterChanged,
            onZoomChanged = onZoomChanged,
            onMapViewReady = onMapViewReady,
            modifier = Modifier.fillMaxSize()
        )

        // ── Earth / Water toggle (top-left) ───────────────────────────────
        EarthWaterIcon(
            emoji = if (isWater) "\uD83C\uDF0A" else "\uD83C\uDFD4\uFE0F",
            isActive = true,
            activeColor = if (isWater) ComposeColor(0xFF1565C0) else ComposeColor(0xFF2E7D32),
            contentDescription = if (isWater) "Eau" else "Terre",
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
        )

        // ── Settings button (top-right) ───────────────────────────────────
        SettingsButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
        )

        // ── Center position marker ────────────────────────────────────────
        CenterMarkerOverlay(
            isWater = isWater,
            zoomLevel = zoomLevel,
            distanceToShore = distanceToShore,
            modifier = Modifier.align(Alignment.Center)
        )

        // ── Bottom bar: overlay centered in leftover space + zoom btns ────
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            // Overlay centered in the space left of the zoom buttons
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                if (state is CoastlineState.Loading) {
                    LoadingOverlay(progress = progress)
                }
                if (state is CoastlineState.Error) {
                    ErrorOverlay(
                        message = (state as CoastlineState.Error).message,
                        onRetry = onRetry
                    )
                }
            }

            // Zoom buttons fixed on the right (only when mapView is ready)
            if (mapView != null) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ZoomButton(
                        icon = Icons.Default.Add,
                        desc = "Zoom avant",
                        onClick = {
                            val mv = mapView ?: return@ZoomButton
                            mv.controller.zoomIn()
                            onZoomChanged(mv.zoomLevelDouble)
                        }
                    )
                    ZoomButton(
                        icon = null,
                        desc = "Zoom arri\u00E8re",
                        label = "\u2212",
                        onClick = {
                            val mv = mapView ?: return@ZoomButton
                            mv.controller.zoomOut()
                            onZoomChanged(mv.zoomLevelDouble)
                        }
                    )
                }
            }
        }
    }
}

// ── Loading overlay ─────────────────────────────────────────────────────────

@Composable
private fun LoadingOverlay(
    progress: GenerationProgress,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth(0.66f)
            .padding(horizontal = 8.dp, vertical = 0.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.5.dp,
            color = ComposeColor(0xFF1565C0)
        )

        Text(
            text = "Chargement de la c\u00F4te\u2026",
            color = ComposeColor(0xFF1565C0),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )

        if (progress.phase.isNotEmpty()) {
            Text(
                text = progress.phase,
                color = ComposeColor(0xFF1565C0),
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
                color = ComposeColor(0xFF1565C0),
                trackColor = ComposeColor(0x401565C0)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "${progress.progress}%",
                color = ComposeColor(0xFF1565C0),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ── Error overlay ───────────────────────────────────────────────────────────

@Composable
private fun ErrorOverlay(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(ComposeColor(0xCCC62828))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Erreur",
            color = ComposeColor.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = message,
            color = ComposeColor(0xEEFFFFFF),
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(
                containerColor = ComposeColor.White
            )
        ) {
            Text(
                text = "R\u00E9essayer",
                color = ComposeColor(0xFFC62828),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ── OSMdroid map view ───────────────────────────────────────────────────────

@Composable
private fun CoastlineMapView(
    segments: List<CoastlineSegment>,
    zone300: Zone300Data?,
    depthBitmap: Bitmap?,
    depthBox: BoundingBox?,
    isobaths: List<Isobath>,
    zoomLevel: Double,
    center: LatLng,
    initialZoom: Double,
    onCenterChanged: (Double, Double) -> Unit = { _, _ -> },
    onZoomChanged: (Double) -> Unit = {},
    onMapViewReady: (MapView) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // Rebuild overlays only when the data or a zoom-gate crossing changes — never on a
    // center pan (osmdroid pans internally; a per-frame removeAll+redraw would jank).
    val zoneVisible = zoomLevel >= ZONE_MIN_ZOOM
    val depthVisible = zoomLevel >= DepthConstants.DEPTH_MAP_MIN_DRAW_ZOOM
    val isobathVisible = zoomLevel >= DepthConstants.ISOBATH_MIN_DRAW_ZOOM
    val shallowIsobathVisible = zoomLevel >= DepthConstants.SHALLOW_ISOBATH_MIN_ZOOM
    val overlayKey = remember(
        segments, zone300, zoneVisible,
        depthBitmap, isobaths, depthVisible, isobathVisible, shallowIsobathVisible
    ) { Any() }
    val lastOverlayKey = remember { mutableStateOf<Any?>(null) }

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
                drawDepthMap(this, depthBitmap, depthBox, zoomLevel)   // bottom: colour raster
                drawIsobaths(this, isobaths, zoomLevel)                // contours above raster
                drawZone300(this, zone300, zoomLevel)                  // 300 m band fill + line
                drawCoastline(this, segments)                          // coastline on top

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
            }.also { onMapViewReady(it) }
        },
        update = { mapView ->
            // Only when data/visibility changed — not on every pan recomposition.
            if (lastOverlayKey.value !== overlayKey) {
                lastOverlayKey.value = overlayKey
                // Remove every data overlay (depth raster GroundOverlay, isobath/zone/coast
                // Polylines, zone fill Polygons) so nothing is orphaned or accumulates, then
                // rebuild bottom-to-top in z-order.
                mapView.overlays.removeAll { it is Polyline || it is Polygon || it is GroundOverlay }
                drawDepthMap(mapView, depthBitmap, depthBox, zoomLevel)
                drawIsobaths(mapView, isobaths, zoomLevel)
                drawZone300(mapView, zone300, zoomLevel)
                drawCoastline(mapView, segments)
                mapView.invalidate()
            }
        }
    )
}

// ── Center marker overlay ────────────────────────────────────────────────────

// ── Tuning constants for dynamic marker sizing ────────────────────────────────
// The marker resizes like the map itself — exponentially with zoom — but with
// a mitigating factor so it doesn't grow/shrink as aggressively as ground coverage.
//
// Formula:  dp = baseDp × 2^(ZOOM_EXPONENT × (zoomLevel − REF_ZOOM))
//
// Map ground coverage doubles every +1 zoom level (exponent = 1.0).
// At [ZOOM_EXPONENT] = 0.3 the marker grows ~23 % per zoom level instead of 100 %.

/** Reference zoom where the marker is at its [BOAT_BASE_DP] / [DOT_BASE_DP]. */
private const val REF_ZOOM = 12.0 // 11.0 -to 18.0

/** Base dp for the boat marker at [REF_ZOOM]. */
private const val BOAT_BASE_DP = 32.0
/** Base dp for the land-dot marker at [REF_ZOOM]. */
private const val DOT_BASE_DP  = 8.0

/**
 * Mitigating exponent applied to the zoom delta.
 * 1.0 = resize exactly like the map (doubles every zoom level).
 * 0.3 = gentler curve (~23 % growth per zoom, ~8× over the full 8–18 range).
 */
private const val ZOOM_EXPONENT = 0.45

// ── Distance-to-coast shrink ramp ─────────────────────────────────────────────
// When the map center is close to the coastline, the marker shrinks so it
// doesn't visually overlap the ground ("run aground"). The multiplier ramps
// linearly from [DIST_SHRINK_MIN_MULT] at 0 m up to 1.0 at [DIST_SHRINK_RAMP_M].

/** Minimum size multiplier when exactly on the coastline. */
private const val DIST_SHRINK_MIN_MULT = 0.3
/** Distance in meters at which the marker reaches full (1.0×) size. */
private const val DIST_SHRINK_RAMP_M   = 2000.0

/** Below this zoom the 300 m band is sub-pixel and meaningless; skip drawing it.
 *  Matches the map's default zoom (11) so the band is visible on launch. */
private const val ZONE_MIN_ZOOM = 11.0

// ───────────────────────────────────────────────────────────────────────────────

/**
 * A fixed icon drawn at the center of the screen, indicating the current
 * GPS position. Stays in place while the map moves beneath it.
 *
 * Sizing is dynamic:
 * - Follows the map zoom level exponentially with mitigating factor
 *   [ZOOM_EXPONENT]: bigger when zoomed in, smaller when zoomed out.
 * - Shrinks near the coast (≤ [DIST_SHRINK_RAMP_M] m) to avoid visual
 *   "running aground".
 *
 * - On water: displays the Maro boat logo ([R.drawable.maro_marker]).
 * - On land:  displays a blue dot ([R.drawable.maro_dot_marker]).
 *
 * @param zoomLevel      Current map zoom (8.0–18.0).
 * @param distanceToShore Distance from map center to nearest coast in meters,
 *                        or `null` when unavailable.
 */
@Composable
private fun CenterMarkerOverlay(
    isWater: Boolean,
    zoomLevel: Double,
    distanceToShore: Double?,
    modifier: Modifier = Modifier
) {
    val drawableId = if (isWater) R.drawable.maro_marker else R.drawable.maro_dot_marker
    val description = if (isWater) "Position (eau)" else "Position (terre)"

    // ── Base size: exponential zoom scaling ───────────────────────────────
    // dp = baseDp × 2^(ZOOM_EXPONENT × (zoom − REF_ZOOM))
    val baseDp = if (isWater) BOAT_BASE_DP else DOT_BASE_DP
    val scaleFactor = 2.0.pow(ZOOM_EXPONENT * (zoomLevel - REF_ZOOM))

    // ── Distance-to-coast multiplier: [DIST_SHRINK_MIN_MULT] on the coast
    //    → 1.0 at [DIST_SHRINK_RAMP_M] m ───────────────────────────────────
    val distMultiplier = if (distanceToShore != null) {
        (DIST_SHRINK_MIN_MULT +
         (1.0 - DIST_SHRINK_MIN_MULT) * (distanceToShore / DIST_SHRINK_RAMP_M).coerceIn(0.0, 1.0))
            .toFloat()
    } else {
        1.0f  // no coastline data → full size
    }

    val finalSizeDp = ((baseDp * scaleFactor) * distMultiplier).dp

    Image(
        painter = painterResource(id = drawableId),
        contentDescription = description,
        modifier = modifier.size(finalSizeDp),
        contentScale = ContentScale.Fit
    )
}

// ── Earth / Water icon control ───────────────────────────────────────────────

/**
 * A 44×44 dp icon square representing either water (🌊) or earth (🏔️).
 *
 * No text caption — icon only. The background tint indicates whether this
 * side is currently active based on the map center position.
 */
@Composable
private fun EarthWaterIcon(
    emoji: String,
    isActive: Boolean,
    activeColor: ComposeColor,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isActive) activeColor.copy(alpha = 0.30f)
                else ComposeColor(0xEEFFFFFF)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = emoji,
            fontSize = 22.sp
        )
    }
}

// ── Settings button (top-right of map, matching zoom button style) ──────────

@Composable
private fun SettingsButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.size(64.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = ComposeColor(0xCCFFFFFF)
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = "Param\u00E8tres",
            tint = ComposeColor(0xFF1565C0),
            modifier = Modifier.size(32.dp)
        )
    }
}

// ── Settings overlay (full-screen page) ─────────────────────────────────────

@Composable
private fun SettingsOverlay(
    settings: AppSettings,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    onGpsModeChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ComposeColor(0xFF1A1A2E))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            // ── Header row: title + close (back) button ───────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Back arrow button (returns to map)
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ComposeColor(0x33FFFFFF)
                        ),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = "\u2190",  // ←
                            fontSize = 22.sp,
                            color = ComposeColor.White
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Param\u00E8tres",
                        color = ComposeColor.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Scrollable settings body — the header above stays pinned so the back
            // button is always reachable. weight(1f) bounds the height so verticalScroll works.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {

            // -- Source de position (Demo <-> GPS) section --
            SectionHeader(title = "Source de position")

            Spacer(modifier = Modifier.height(8.dp))

            SettingsToggleRow(
                label = "Mode GPS",
                description = "Suivre votre position GPS et orienter la carte (sinon mode démo)",
                checked = settings.gpsMode,
                onCheckedChange = onGpsModeChange
            )

            Spacer(modifier = Modifier.height(12.dp))

            SettingsSliderRow(
                label = "Délai de recentrage",
                description = "Temps avant recentrage GPS après un déplacement manuel",
                valueLabel = "${settings.recenterDelaySeconds} s",
                value = settings.recenterDelaySeconds.toFloat(),
                valueRange = 1f..10f,
                steps = 8,
                onValueChange = { v -> onUpdateSettings { it.copy(recenterDelaySeconds = v.roundToInt()) } }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ── Affichage (Display) section ──────────────────────────────
            SectionHeader(title = "Affichage")

            Spacer(modifier = Modifier.height(8.dp))

            // ── Coastline overlay toggle ──────────────────────────────────
            SettingsToggleRow(
                label = "Trait de c\u00F4te",
                description = "Afficher la ligne de c\u00F4te sur la carte",
                checked = settings.coastlineVisible,
                onCheckedChange = { visible ->
                    onUpdateSettings { it.copy(coastlineVisible = visible) }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ── Zone 300 m overlay toggle ─────────────────────────────────
            SettingsToggleRow(
                label = "Zone 300 m",
                description = "Afficher la zone des 300 m sur la carte",
                checked = settings.zone300Visible,
                onCheckedChange = { visible ->
                    onUpdateSettings { it.copy(zone300Visible = visible) }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ── Économie d'énergie section (battery) — grouped by function ──
            SectionHeader(title = "Économie d'énergie")

            Spacer(modifier = Modifier.height(12.dp))

            // Group 1: GPS acquisition cadence while moving.
            SubSectionHeader(
                title = "Acquisition GPS",
                description = "Mise à jour de votre position pendant la navigation"
            )
            Spacer(modifier = Modifier.height(8.dp))
            SettingsFrequencyRow(
                intervalSec = settings.gpsActiveIntervalSec,
                onSelect = { ivl, dist ->
                    onUpdateSettings { it.copy(gpsActiveIntervalSec = ivl, gpsActiveMinDistanceM = dist) }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Group 2: map re-render ceiling.
            SubSectionHeader(
                title = "Rendu carte",
                description = "Nombre de redessins de la carte par seconde en suivi GPS"
            )
            Spacer(modifier = Modifier.height(8.dp))
            SettingsSliderRow(
                label = "Fréquence de rafraîchissement",
                description = "Plus bas = moins de batterie, animation moins fluide",
                valueLabel = "${settings.mapRefreshFps} fps",
                value = settings.mapRefreshFps.toFloat(),
                valueRange = 5f..50f,
                steps = 8,
                onValueChange = { v -> onUpdateSettings { it.copy(mapRefreshFps = (v / 5f).roundToInt() * 5) } }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Group 3: movement-adaptive idle behaviour.
            SubSectionHeader(
                title = "Économie à l'arrêt",
                description = "Quand le bateau ne bouge plus, les positions GPS s'espacent pour économiser la batterie ; tout revient à la normale dès que vous repartez"
            )
            Spacer(modifier = Modifier.height(8.dp))
            // Primary lever (always visible): how slow GPS goes once stopped.
            SettingsSliderRow(
                label = "Intervalle au repos",
                description = "Délai entre deux positions GPS à l'arrêt · plus long = moins de batterie",
                valueLabel = "${settings.adaptiveIdleIntervalSec} s",
                value = settings.adaptiveIdleIntervalSec.toFloat(),
                valueRange = 4f..15f,
                steps = 10,
                onValueChange = { v -> onUpdateSettings { it.copy(adaptiveIdleIntervalSec = v.roundToInt()) } }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Advanced stop-detection thresholds, collapsed by default (progressive disclosure).
            var adaptiveAdvanced by remember { mutableStateOf(false) }
            SettingsExpander(
                label = "Avancé — détection de l'arrêt",
                expanded = adaptiveAdvanced,
                onToggle = { adaptiveAdvanced = !adaptiveAdvanced }
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                SettingsSliderRow(
                    label = "Fenêtre adaptative",
                    description = "Temps sans bouger avant de réduire la fréquence GPS",
                    valueLabel = "${settings.adaptiveWindowSec} s",
                    value = settings.adaptiveWindowSec.toFloat(),
                    valueRange = 15f..60f,
                    steps = 8,
                    onValueChange = { v -> onUpdateSettings { it.copy(adaptiveWindowSec = (v / 5f).roundToInt() * 5) } }
                )

                Spacer(modifier = Modifier.height(12.dp))

                SettingsSliderRow(
                    label = "Distance adaptative",
                    description = "En dessous de ce déplacement, vous êtes considéré « à l'arrêt »",
                    valueLabel = "${settings.adaptiveDistanceM} m",
                    value = settings.adaptiveDistanceM.toFloat(),
                    valueRange = 10f..30f,
                    steps = 3,
                    onValueChange = { v -> onUpdateSettings { it.copy(adaptiveDistanceM = (v / 5f).roundToInt() * 5) } }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Footer ────────────────────────────────────────────────────
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "Maro II \u2014 v1.0",
                color = ComposeColor(0xFF546E7A),
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            } // end scrollable settings body
        }
    }
}

// ── Settings sub-components ─────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        color = ComposeColor(0xFF1565C0),
        fontSize = 17.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp
    )
}

@Composable
private fun SettingsToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ComposeColor(0x1AFFFFFF))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = ComposeColor.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                color = ComposeColor(0xFFB0BEC5),
                fontSize = 13.sp
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = ComposeColor(0xFF1565C0),
                checkedTrackColor = ComposeColor(0xFF1565C0).copy(alpha = 0.4f),
                uncheckedThumbColor = ComposeColor(0xFFB0BEC5),
                uncheckedTrackColor = ComposeColor(0x33FFFFFF)
            )
        )
    }
}

@Composable
private fun SettingsSliderRow(
    label: String,
    description: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ComposeColor(0x1AFFFFFF))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    color = ComposeColor.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    color = ComposeColor(0xFFB0BEC5),
                    fontSize = 13.sp
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = valueLabel,
                color = ComposeColor(0xFF1565C0),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = ComposeColor(0xFF1565C0),
                activeTrackColor = ComposeColor(0xFF1565C0),
                inactiveTrackColor = ComposeColor(0x33FFFFFF)
            )
        )
    }
}

/** Dimmer sub-heading with an optional one-line description, for grouping settings in a section. */
@Composable
private fun SubSectionHeader(title: String, description: String? = null) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            color = ComposeColor(0xFF90A4AE),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
        if (description != null) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                color = ComposeColor(0xFF78909C),
                fontSize = 13.sp
            )
        }
    }
}

/**
 * Tappable "Avancé" disclosure row that reveals [content] with a chevron + slide animation.
 * Progressive disclosure — keeps advanced/fiddly controls out of the way until asked for.
 */
@Composable
private fun SettingsExpander(
    label: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "expanderChevron"
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { onToggle() }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = ComposeColor(0xFF90A4AE),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "Réduire" else "Développer",
                tint = ComposeColor(0xFF90A4AE),
                modifier = Modifier.rotate(rotation)
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}

/**
 * GPS frequency as a 3-stop labelled slider. Each stop writes the matching (interval, min-distance)
 * pair — Haute 1 s/1 m · Équilibrée 2 s/5 m · Économie 4 s/10 m. Équilibrée's label is bold to mark
 * it as the default; the active stop is highlighted in blue.
 */
@Composable
private fun SettingsFrequencyRow(
    intervalSec: Int,
    onSelect: (intervalSec: Int, minDistanceM: Float) -> Unit
) {
    // (label, intervalSec, minDistanceM) at slider index 0, 1, 2
    val stops = listOf(
        Triple("Haute", 1, 1f),
        Triple("Équilibrée", 2, 5f),
        Triple("Économie", 4, 10f)
    )
    val currentIdx = stops.indexOfFirst { it.second == intervalSec }.let { if (it < 0) 1 else it }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ComposeColor(0x1AFFFFFF))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = "Fréquence GPS",
            color = ComposeColor.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = "Intervalle entre les positions GPS en mouvement · plus lent = moins de batterie",
            color = ComposeColor(0xFFB0BEC5),
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Slider(
            value = currentIdx.toFloat(),
            onValueChange = { v ->
                val idx = v.roundToInt().coerceIn(0, stops.lastIndex)
                onSelect(stops[idx].second, stops[idx].third)
            },
            valueRange = 0f..2f,
            steps = 1,
            colors = SliderDefaults.colors(
                thumbColor = ComposeColor(0xFF1565C0),
                activeTrackColor = ComposeColor(0xFF1565C0),
                inactiveTrackColor = ComposeColor(0x33FFFFFF)
            )
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            stops.forEachIndexed { idx, stop ->
                val selected = idx == currentIdx
                val accent = if (selected) ComposeColor(0xFF1565C0) else ComposeColor(0xFFB0BEC5)
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = when (idx) {
                        0 -> Alignment.Start
                        stops.lastIndex -> Alignment.End
                        else -> Alignment.CenterHorizontally
                    }
                ) {
                    Text(
                        text = stop.first,
                        color = accent,
                        fontSize = 13.sp,
                        // Default (Équilibrée) stays bold to flag the recommended setting.
                        fontWeight = if (idx == 1) FontWeight.Bold else FontWeight.Normal
                    )
                    Text(
                        text = "${stop.second}s/${stop.third.roundToInt()}m",
                        color = accent,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsTextFieldRow(
    label: String,
    value: Double,
    onValueChange: (Double) -> Unit
) {
    var textValue by remember { mutableStateOf(value.toString()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ComposeColor(0x1AFFFFFF))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = ComposeColor.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(100.dp)
        )
        androidx.compose.material3.OutlinedTextField(
            value = textValue,
            onValueChange = { newText ->
                textValue = newText
                newText.toDoubleOrNull()?.let { onValueChange(it) }
            },
            singleLine = true,
            modifier = Modifier
                .width(160.dp)
                .height(48.dp),
            textStyle = androidx.compose.ui.text.TextStyle(
                color = ComposeColor.White,
                fontSize = 14.sp
            ),
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ComposeColor(0xFF1565C0),
                unfocusedBorderColor = ComposeColor(0x66FFFFFF),
                cursorColor = ComposeColor.White
            )
        )
    }
}

// ── Zoom button (used for map +/- controls) ─────────────────────────────────

@Composable
private fun ZoomButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    desc: String,
    onClick: () -> Unit,
    label: String? = null
) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(64.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = ComposeColor(0xCCFFFFFF)
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = desc,
                tint = ComposeColor(0xFF1565C0),
                modifier = Modifier.size(32.dp)
            )
        } else if (label != null) {
            Text(
                text = label,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = ComposeColor(0xFF1565C0)
            )
        }
    }
}

/**
 * Draws the coastline segments on the OSMdroid [MapView].
 *
 * Mainland: solid blue (#1565C0), 7px width
 * Islands:  blue (#42A5F5), 5px width — slightly distinct from mainland
 */
private fun drawCoastline(
    mapView: MapView,
    segments: List<CoastlineSegment>
) {
    for (segment in segments) {
        val points = segment.points
        if (points.size < 2) continue

        val osmPoints = points.map { GeoPoint(it.lat.toDouble(), it.lon.toDouble()) }

        val polyline = Polyline().apply {
            setPoints(osmPoints)
            outlinePaint.apply {
                color = if (segment.isMainland) Color.parseColor("#1545c0")
                        else Color.parseColor("#08805c")
                strokeWidth = 10f // if (segment.isMainland) 7f else 5f
                alpha = 128 // if (segment.isMainland) 200 else 160
                isAntiAlias = true
            }
        }
        mapView.overlays.add(polyline)
    }
}

/**
 * Draws the precomputed 300 m band: translucent red fill (water only, island land
 * cut out as holes) plus the red seaward boundary line. Zoom-gated — nothing is
 * drawn below [ZONE_MIN_ZOOM] (the band would be sub-pixel) or before the band has
 * been built ([zone] == null).
 *
 * Must be drawn **before** [drawCoastline] so the coastline reads on top of the fill.
 */
private fun drawZone300(mapView: MapView, zone: Zone300Data?, zoomLevel: Double) {
    if (zone == null || zoomLevel < ZONE_MIN_ZOOM) return

    // Fill (water only) — translucent red, no outline on the polygon itself.
    for (poly in zone.fillPolygons) {
        if (poly.outer.size < 3) continue
        val fill = Polygon().apply {
            setPoints(poly.outer.map { GeoPoint(it.latitude, it.longitude) })
            val validHoles = poly.holes.filter { it.size >= 3 }
            if (validHoles.isNotEmpty()) {
                setHoles(validHoles.map { hole -> hole.map { GeoPoint(it.latitude, it.longitude) } })
            }
            fillPaint.color = Color.argb(48, 229, 57, 53)   // ~19% red
            outlinePaint.color = Color.TRANSPARENT
            outlinePaint.strokeWidth = 0f
        }
        mapView.overlays.add(fill)
    }

    // Red seaward boundary line (above the fill).
    for (line in zone.seawardLines) {
        if (line.size < 2) continue
        val redLine = Polyline().apply {
            setPoints(line.map { GeoPoint(it.latitude, it.longitude) })
            outlinePaint.apply {
                color = Color.parseColor("#E53935")
                strokeWidth = 6f
                alpha = 220
                isAntiAlias = true
            }
        }
        mapView.overlays.add(redLine)
    }
}

// ── Depth overlays ────────────────────────────────────────────────────────────

/**
 * Draws the hypsometric depth colour map as a single bottom-most [GroundOverlay].
 * Zoom-gated below [DepthConstants.DEPTH_MAP_MIN_DRAW_ZOOM] and skipped until the bitmap has
 * been built ([bitmap] == null). The bitmap already carries per-pixel alpha (NaN cells
 * transparent, water semi-opaque), so it is added at full overlay opacity.
 *
 * Added FIRST so the isobaths, 300 m band and coastline read on top.
 */
private fun drawDepthMap(mapView: MapView, bitmap: Bitmap?, box: BoundingBox?, zoomLevel: Double) {
    if (bitmap == null || box == null || zoomLevel < DepthConstants.DEPTH_MAP_MIN_DRAW_ZOOM) return
    val overlay = GroundOverlay().apply {
        setImage(bitmap)
        // Axis-aligned placement: top-left = NW corner, bottom-right = SE corner.
        setPosition(
            GeoPoint(box.latNorth, box.lonWest),
            GeoPoint(box.latSouth, box.lonEast)
        )
    }
    mapView.overlays.add(overlay)
}

/**
 * Draws depth contour [isobaths] as polylines, above the colour map but below the 300 m band
 * and coastline. Zoom-gated: nothing below [DepthConstants.ISOBATH_MIN_DRAW_ZOOM]; the dense
 * 2 m contour appears only at [DepthConstants.SHALLOW_ISOBATH_MIN_ZOOM]+. "Round" contours
 * (10/20/30…m) read slightly bolder than the in-between lines.
 */
private fun drawIsobaths(mapView: MapView, isobaths: List<Isobath>, zoomLevel: Double) {
    if (zoomLevel < DepthConstants.ISOBATH_MIN_DRAW_ZOOM) return
    for (iso in isobaths) {
        if (iso.depthM <= 2f && zoomLevel < DepthConstants.SHALLOW_ISOBATH_MIN_ZOOM) continue
        val isMajor = iso.depthM.toInt() % 10 == 0
        for (line in iso.lines) {
            if (line.size < 2) continue
            val poly = Polyline().apply {
                setPoints(line.map { GeoPoint(it.latitude, it.longitude) })
                outlinePaint.apply {
                    color = Color.parseColor("#37474F")   // muted blue-grey
                    strokeWidth = if (isMajor) 3f else 2f
                    alpha = if (isMajor) 180 else 120
                    isAntiAlias = true
                }
            }
            mapView.overlays.add(poly)
        }
    }
}
