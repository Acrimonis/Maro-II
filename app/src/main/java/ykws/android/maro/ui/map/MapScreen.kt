
package ykws.android.maro.ui.map
import ykws.android.maro.config.AppConfig

import android.graphics.DashPathEffect

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.SystemClock
import android.view.MotionEvent
import android.graphics.Bitmap
import android.graphics.Color
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import ykws.android.maro.R
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ScrollState
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.core.content.ContextCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import kotlin.math.cos
import kotlin.math.sin
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import ykws.android.maro.data.depth.DepthConstants
import ykws.android.maro.data.depth.RasterCache
import ykws.android.maro.data.model.BoundingBox
import ykws.android.maro.data.model.CoastlinePoint
import ykws.android.maro.data.model.CoastlineSegment
import ykws.android.maro.data.model.CoastlineState
import ykws.android.maro.data.model.DepthSample
import ykws.android.maro.data.model.DepthState
import ykws.android.maro.data.model.GenerationProgress
import ykws.android.maro.data.model.Isobath
import ykws.android.maro.data.model.RasterProgress
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.ValidationReport
import ykws.android.maro.data.model.Zone300Data
import ykws.android.maro.data.regulation.RegulatedZoneSet
import ykws.android.maro.data.regulation.RegulatedZoneType
import ykws.android.maro.data.regulation.RegulatedZonesRepository
import ykws.android.maro.data.regulation.RegulatedZone
import ykws.android.maro.data.regulation.ZoneDisplayCategory
import ykws.android.maro.data.regulation.displayCategories
import ykws.android.maro.data.regulation.contains
import ykws.android.maro.ui.map.CircleRingIcon
import ykws.android.maro.ui.map.FanConfig
import ykws.android.maro.ui.map.FanDirection
import ykws.android.maro.ui.map.FanLayout
import ykws.android.maro.ui.map.GearIcon
import ykws.android.maro.ui.map.MapControlButton
import ykws.android.maro.ui.map.MinusIcon
import ykws.android.maro.ui.map.PlusIcon
import ykws.android.maro.ui.map.WarningTriangleIcon
import ykws.android.maro.data.settings.AppSettings
import ykws.android.maro.spatial.SpatialOperations

/** GPS-follow animation: minimum displacement (m) to trigger a glide instead of snap. */
private const val GPS_ANIMATION_MIN_MOVE_M = 3.0
/** Animation duration per GPS-follow scroll (ms). Must be < min GPS fix interval (1s). */
private const val GPS_ANIMATION_DURATION_MS = 600L

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
    val mapCenter by viewModel.uiMapCenter.collectAsState()
    val isWater by viewModel.isWater.collectAsState()
    val distanceToShore by viewModel.distanceToShore.collectAsState()
    val zoomLevel by viewModel.zoomLevel.collectAsState()
    val inZone300 by viewModel.inZone300.collectAsState()
    val distanceToZone by viewModel.distanceToZone.collectAsState()
    val zone300 by viewModel.zone300.collectAsState()
    val zoneSituation by viewModel.zoneSituation.collectAsState()
    val appSettings by viewModel.settings.collectAsState()
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var expandedFanId by remember { mutableStateOf<ControlId?>(null) }
    val anyFanExpanded = expandedFanId != null
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val displayScrollState = rememberScrollState()
    val navigationScrollState = rememberScrollState()
    val systemScrollState = rememberScrollState()

    val context = LocalContext.current
    val autoFollowSuppressed by viewModel.autoFollowSuppressed.collectAsState()
    val navigationState by viewModel.navigationState.collectAsState()
    val gpsPosition by viewModel.gpsPosition.collectAsState()
    val gpsStale by viewModel.gpsStale.collectAsState()
    val acquisitionMode by viewModel.acquisitionMode.collectAsState()
    // Effective heading for the zone-ahead cone:
    // GPS mode → GPS bearing (COG/compass, boat faces direction of travel)
    // Demo mode → 0° = north (boat marker always points up/top of map).
    //             When panning actively, demoBearingDeg tracks pan direction.
    val effectiveHeadingDeg = if (appSettings.gpsMode) navigationState.bearingDeg.toDouble()
        else navigationState.demoBearingDeg?.toDouble() ?: 0.0

    // Derive the GPS icon state from ViewModel state (5-state model).
    val gpsIconState = remember(appSettings.gpsMode, gpsPosition, gpsStale, acquisitionMode) {
        when {
            !appSettings.gpsMode -> GpsIconState.DEMO
            gpsPosition == null -> GpsIconState.ACQUIRING
            gpsStale -> GpsIconState.STALE
            acquisitionMode == ykws.android.maro.data.location.AcquisitionMode.IDLE -> GpsIconState.IDLE
            else -> GpsIconState.HEALTHY
        }
    }

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
        // Two-finger rotation tracking state.
        var rotating = false
        var lastAngleDeg = 0f
        mv.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    viewModel.notifyUserInteraction()
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    // Second finger touched — start tracking rotation.
                    if (ev.pointerCount == 2 && viewModel.settings.value.demoHeadingUp) {
                        val dx = ev.getX(1) - ev.getX(0)
                        val dy = ev.getY(1) - ev.getY(0)
                        lastAngleDeg = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
                        rotating = true
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (rotating && ev.pointerCount >= 2) {
                        val dx = ev.getX(1) - ev.getX(0)
                        val dy = ev.getY(1) - ev.getY(0)
                        val angleDeg = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
                        val delta = angleDeg - lastAngleDeg
                        // Normalise delta to [-180, 180] to avoid wraparound jumps.
                        val normalisedDelta = ((delta + 180f) % 360f + 360f) % 360f - 180f
                        if (kotlin.math.abs(normalisedDelta) >= 1f) {
                            val current = viewModel.navigationState.value.bearingDeg
                            viewModel.setDemoBearing((current + normalisedDelta + 360f) % 360f)
                            lastAngleDeg = angleDeg
                        }
                    } else {
                        // Single-finger pan — notify GPS to pause auto-follow.
                        viewModel.notifyUserInteraction()
                    }
                }
                MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP -> {
                    rotating = false
                }
            }
            false // don't consume — the map still pans/zooms normally
        }
    }

    // ── GPS auto-follow: smooth glide + heading-up ─────────────────────────
    // One throttled stream (≤ appSettings.mapRefreshFps) drives BOTH position and
    // orientation. Re-engage uses the default animateTo (smooth scroll back);
    // subsequent GPS fixes use animateTo with a 600 ms bounded duration so the
    // boat glides smoothly instead of stepping. The haversine guard (> 3 m) skips
    // sub-threshold GPS noise. Manual pinch/pan/fling keep osmdroid's own full-rate
    // path — the cap governs only this GPS-follow flow.
    LaunchedEffect(appSettings.gpsMode, appSettings.demoHeadingUp, autoFollowSuppressed, mapView) {
        val mv = mapView ?: return@LaunchedEffect
        if (!appSettings.gpsMode && !appSettings.demoHeadingUp) { mv.mapOrientation = 0f; mv.invalidate(); return@LaunchedEffect }
        if (autoFollowSuppressed) return@LaunchedEffect
        var reengage = true
        var lastPosition: LatLng? = null
        viewModel.cameraUpdates.collect { target ->
            val point = GeoPoint(target.position.latitude, target.position.longitude)
            if (reengage) {
                // Scroll smoothly back to the GPS position when follow resumes (no snap).
                mv.controller.animateTo(point)
                reengage = false
            } else {
                val prev = lastPosition
                if (prev == null ||
                    SpatialOperations.haversine(prev, target.position) > GPS_ANIMATION_MIN_MOVE_M
                ) {
                // Smoothly animate to the new GPS fix so the boat glides instead of stepping.
                    mv.controller.animateTo(point, null, GPS_ANIMATION_DURATION_MS)
                }
            }
            mv.mapOrientation = -target.bearingDeg
            mv.invalidate()
            lastPosition = target.position
            // Keep depth-at-center following the GPS fix at the same capped cadence.
            depthViewModel.updateMapCenter(target.position.latitude, target.position.longitude)
        }
    }

    // ── Demo heading-up: apply pan-derived bearing to map orientation ─────────
    // When demoHeadingUp is enabled (and we're in demo mode), the bearing is
    // computed from the pan direction in CoastlineViewModel.computeDemoSpeed().
    // Watch navigationState.bearingDeg and apply it to the MapView directly.
    // This effect runs separately from the GPS auto-follow effect above.
    LaunchedEffect(appSettings.demoHeadingUp, appSettings.gpsMode, mapView) {
        val mv = mapView ?: return@LaunchedEffect
        if (appSettings.gpsMode || !appSettings.demoHeadingUp) return@LaunchedEffect
        viewModel.navigationState.collect { nav ->
            mv.mapOrientation = -nav.bearingDeg
            mv.invalidate()
        }
    }

    // ── Depth layer ─────────────────────────────────────────────────────────────
    val depthState by depthViewModel.state.collectAsState()
    val depthRender by depthViewModel.renderModel.collectAsState()
    val depthAtCenter by depthViewModel.depthAtCenter.collectAsState()
    // Gate coarse EMODnet shallow readings (unreliable near rocks/coast) → no-data in the readout.
    val depthReadout = depthAtCenter?.gatedForEmodnetShallow(appSettings.emodnetShallowCutoffM)
    val depthGrid = (depthState as? DepthState.Ready)?.grid
    val isobaths = depthRender?.isobaths ?: emptyList()

    // Coastline classifier is needed for both the low-depth warning and the depth colour map
    // (to keep NoData colour off land).
    val coastlineReady = state is CoastlineState.Ready
    val waterTest: (Double, Double) -> Boolean =
        if (coastlineReady) viewModel::isOnWater else { _, _ -> true }

    // Rasterise the colour map once per grid, off the main thread (~7 M cells).
    val depthBitmap by produceState<Bitmap?>(initialValue = null, depthGrid,
        appSettings.lowDepthWarningMaxM, appSettings.lowDepthWarningMinOpacityPct,
        appSettings.emodnetShallowCutoffM, coastlineReady) {
        // If cache exists, skip the expensive live build
        val cached = depthGrid?.let {
            depthViewModel.readCached(context, RasterCache.Step.DEPTH_COLOUR, appSettings)
        }
        if (cached != null) { value = cached; return@produceState }
        value = depthGrid?.let { g ->
            withContext(Dispatchers.Default) {
                DepthBitmap.build(g, appSettings.emodnetShallowCutoffM, AppConfig.mapDepthNodataColor)
            }
        }
    }

    // Second raster: cells shallower than the user's warning threshold, on water only, painted bright.
    // Re-rasterises when the grid, the threshold, or coastline readiness changes.
    val lowDepthWarningBitmap by produceState<Bitmap?>(
        initialValue = null, depthGrid, appSettings.lowDepthWarningMaxM,
        appSettings.lowDepthWarningMinOpacityPct, coastlineReady,
        appSettings.emodnetShallowCutoffM
    ) {
        // If cache exists, skip the expensive live build
        val cached = depthGrid?.let {
            depthViewModel.readCached(context, RasterCache.Step.LOW_DEPTH_WARNING, appSettings)
        }
        if (cached != null) { value = cached; return@produceState }
        val maxM = appSettings.lowDepthWarningMaxM
        value = depthGrid?.let { g ->
            withContext(Dispatchers.Default) {
                LowDepthWarningBitmap.build(g, maxM, waterTest,
                    appSettings.lowDepthWarningMinOpacityPct / 100f,
                    appSettings.emodnetShallowCutoffM)
            }
        }
    }

    // ── Regulated zones overlay: load prebaked asset on first composition ──────────
    val regulatedZones by produceState<RegulatedZoneSet?>(initialValue = null) {
        val repo = RegulatedZonesRepository()
        repo.load(context)
        value = repo.zoneSet.value
    }

    // ── Raster cache reads (no lazy auto-trigger; only settings button triggers generation) ──
    val rasterProgress by depthViewModel.rasterProgress.collectAsState()
    val generatingStep by depthViewModel.generatingStep.collectAsState()
    val rasterCacheVersion by depthViewModel.rasterCacheVersion.collectAsState()

    // ── Silent lazy-init: on cache miss, generate rasters in background (no LoadingOverlay).
    //    Warning layer is deferred until coastline is ready (needs accurate isWater). ──
    LaunchedEffect(depthGrid, appSettings.lowDepthWarningMaxM,
                   appSettings.lowDepthWarningMinOpacityPct, coastlineReady,
                   appSettings.emodnetShallowCutoffM) {
        val grid = depthGrid ?: return@LaunchedEffect
        val key = RasterCache.Key(
            gridTimestampMs = grid.metadata.fetchTimestampMs,
            emodnetCutoffM = appSettings.emodnetShallowCutoffM,
            lowDepthMaxM = appSettings.lowDepthWarningMaxM,
            lowDepthMinOpacityPct = appSettings.lowDepthWarningMinOpacityPct,
            nodataColor = AppConfig.mapDepthNodataColor,
            colorsHash = AppConfig.rasterColorsHash
        )
        val missing = mutableListOf<RasterCache.Step>()
        if (!RasterCache.has(context, RasterCache.Step.DEPTH_COLOUR, key))
            missing.add(RasterCache.Step.DEPTH_COLOUR)
        if (coastlineReady && !RasterCache.has(context, RasterCache.Step.LOW_DEPTH_WARNING, key))
            missing.add(RasterCache.Step.LOW_DEPTH_WARNING)
        if (missing.isNotEmpty()) {
            val waterTest: (Double, Double) -> Boolean =
                if (coastlineReady) viewModel::isOnWater else { _, _ -> true }
            depthViewModel.generateRasterLayers(context, missing, appSettings, waterTest, silent = true)
        }
    }

    // Read cached rasters; hide a layer when it's being regenerated.
    val depthBitmapCached by produceState<Bitmap?>(initialValue = null, depthGrid,
        appSettings.lowDepthWarningMaxM, appSettings.lowDepthWarningMinOpacityPct,
        rasterCacheVersion, generatingStep) {
        if (generatingStep == RasterCache.Step.DEPTH_COLOUR) { value = null; return@produceState }
        value = depthGrid?.let {
            depthViewModel.readCached(context, RasterCache.Step.DEPTH_COLOUR, appSettings)
        }
    }
    val lowDepthWarningCached by produceState<Bitmap?>(initialValue = null, depthGrid,
        appSettings.lowDepthWarningMaxM, appSettings.lowDepthWarningMinOpacityPct,
        rasterCacheVersion, generatingStep) {
        if (generatingStep == RasterCache.Step.LOW_DEPTH_WARNING) { value = null; return@produceState }
        value = depthGrid?.let {
            depthViewModel.readCached(context, RasterCache.Step.LOW_DEPTH_WARNING, appSettings)
        }
    }

    // Prefer cached rasters; fall back to live-built ones. Hide a layer entirely
    // while it's being regenerated (generatingStep signals the active step).
    val effectiveDepthBitmap = if (generatingStep == RasterCache.Step.DEPTH_COLOUR) null
        else (depthBitmapCached ?: depthBitmap)
    val effectiveLowDepthWarning = if (generatingStep == RasterCache.Step.LOW_DEPTH_WARNING) null
        else (lowDepthWarningCached ?: lowDepthWarningBitmap)

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

    // ── (keepScreenOn moved to MainActivity — window flag, avoids compose toggle glitch) ──

    // ── Double-back-to-exit state ─────────────────────────────────────────
    var lastBackAt by remember { mutableStateOf(0L) }
    var showExitBanner by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
    ) {
        // ── Intercept system back when any fan is open ────────────────────
        if (anyFanExpanded) {
            BackHandler { expandedFanId = null }
        }

        // ── Intercept system back when settings are open ──────────────────
        if (showSettings) {
            BackHandler { showSettings = false }
        }

        // ── Otherwise require a second back press within 2 s to exit ───────
        BackHandler(enabled = !showSettings && !anyFanExpanded) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastBackAt <= 2_000L) {
                context.findActivity()?.finishAffinity()
            } else {
                lastBackAt = now
                showExitBanner = true
            }
        }
        if (showExitBanner) {
            LaunchedEffect(lastBackAt) {
                delay(2_000L)
                showExitBanner = false
            }
        }

        // ── Main content (map + dashboard) ────────────────────────────────
        // Note: MapContent is kept at a STABLE composition slot (always a direct child of Box)
        // so the underlying MapView (AndroidView) is never recreated on orientation switch.
        // The dashboard panel is overlaid via Modifier.align() in the orientation branch.
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isLandscape = maxWidth > maxHeight
            val portraitDashboardHeight = maxWidth * 3 / 5
            val landscapeDashboardWidth = maxHeight * 100 / 100

            // Map fills the box, padded to leave room for the dashboard overlay.
            // Stable composition slot — never inside an if/else branch.
            MapContent(
                state = state,
                progress = progress,
                mapCenter = mapCenter,
                isWater = isWater,
                zoomLevel = zoomLevel,
                distanceToShore = distanceToShore,
                regulatedZones = regulatedZones,
                zone300 = zone300,
                inZone300 = inZone300,
                depthBitmap = effectiveDepthBitmap,
                lowDepthWarningBitmap = effectiveLowDepthWarning,
                depthBox = depthGrid?.boundingBox,
                isobaths = isobaths,
                appSettings = appSettings,
                mapView = mapView,
                navigationState = navigationState,
                gpsIconState = gpsIconState,
                // Demo mode (gpsPosition == null): use mapCenter as fallback so
                // geo-fence still works when panning the map in demo/manual mode.
                boatPosition = gpsPosition ?: mapCenter,
                headingDeg = effectiveHeadingDeg,
                onCenterChanged = onCenterChanged,
                onZoomChanged = viewModel::updateZoomLevel,
                onMapViewReady = { mapView = it },
                onRetry = { viewModel.loadCoastline() },
                onOpenSettings = { showSettings = true },
                onToggleLowDepthWarning = viewModel::toggleLowDepthWarningVisibility,
                onToggleDepthLayer = viewModel::toggleDepthLayerVisibility,
                onToggleRegulatedZones = viewModel::toggleRegulatedZonesVisibility,
                onToggleZone300 = viewModel::toggleZone300Visibility,
                isLandscape = isLandscape,
                expandedFanId = expandedFanId,
                onToggleFan = { id -> expandedFanId = if (expandedFanId == id) null else id },
                showExitBanner = showExitBanner,
                rasterProgress = rasterProgress,
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
                depthSample = depthReadout,
                speedKnots = navigationState.speedKnots ?: navigationState.demoSpeedKnots,
                zoneSituation = zoneSituation,
                autoRevealDistanceM = appSettings.zoneAutoRevealDistanceM,
                autoRevealTimeS = appSettings.zoneAutoRevealTimeS.toFloat(),
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
                depthSample = depthReadout,
                speedKnots = navigationState.speedKnots ?: navigationState.demoSpeedKnots,
                zoneSituation = zoneSituation,
                autoRevealDistanceM = appSettings.zoneAutoRevealDistanceM,
                autoRevealTimeS = appSettings.zoneAutoRevealTimeS.toFloat(),
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
                onDismiss = { showSettings = false },
                selectedTab = selectedTab,
                onTabChange = { selectedTab = it },
                displayScrollState = displayScrollState,
                navigationScrollState = navigationScrollState,
                systemScrollState = systemScrollState,
                onRegenerateRasters = { steps ->
                    val waterTest: (Double, Double) -> Boolean =
                        if (state is CoastlineState.Ready) viewModel::isOnWater else { _, _ -> true }
                    depthViewModel.generateRasterLayers(context, steps, appSettings, waterTest)
                }
            )
        }
    }
}

/** Unwraps the (possibly localisation-wrapped) [Context] chain to the host [Activity]. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

// ── Map content area (shared by landscape & portrait) ────────────────────────

/**
 * Map view with overlays: zoom buttons, center marker, loading & error states.
 */
// ── Right-edge control stack types ─────────────────────────────────────────

/** Identifies a control in the right-edge stack. Add new entries when adding controls. */
private enum class ControlId { SETTINGS, LAYER_FAN, ZOOM }

@Composable
private fun MapContent(
    state: CoastlineState,
    progress: GenerationProgress,
    mapCenter: LatLng,
    isWater: Boolean,
    zoomLevel: Double,
    distanceToShore: Double?,
    regulatedZones: RegulatedZoneSet?,
    zone300: Zone300Data?,
    inZone300: Boolean,
    depthBitmap: Bitmap?,
    lowDepthWarningBitmap: Bitmap?,
    depthBox: BoundingBox?,
    isobaths: List<Isobath>,
    appSettings: AppSettings,
    mapView: MapView?,
    navigationState: NavigationState = NavigationState(),
    gpsIconState: GpsIconState = GpsIconState.DEMO,
    boatPosition: LatLng? = null,
    headingDeg: Double = -1.0,
    onCenterChanged: (Double, Double) -> Unit,
    onZoomChanged: (Double) -> Unit,
    onMapViewReady: (MapView) -> Unit,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleZone300: () -> Unit,
    onToggleRegulatedZones: () -> Unit,
    onToggleLowDepthWarning: () -> Unit,
    onToggleDepthLayer: () -> Unit,
    isLandscape: Boolean = false,
    expandedFanId: ControlId? = null,
    onToggleFan: (ControlId) -> Unit = {},
    showExitBanner: Boolean,
    rasterProgress: RasterProgress? = null,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.clipToBounds()) {
        // ── Compute top inset: full statusBars in landscape, -6dp in portrait ──
        val density = LocalDensity.current
        val topInset = with(density) {
            val raw = WindowInsets.statusBars.getTop(this).toDp()
            if (isLandscape) raw else (raw - 6.dp).coerceAtLeast(0.dp)
        }

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
        // Apply regulated zones visibility + boat size + category toggles
        val visibleRegulatedZones = if (appSettings.regulatedZonesVisible) {
            filterRegulatedZones(regulatedZones, appSettings.boatSizeM) { appSettings.isCategoryVisible(it) }
        } else null
        // Apply low-depth (<1.5 m) warning visibility toggle
        val visibleLowDepthWarning = if (appSettings.lowDepthWarningVisible) lowDepthWarningBitmap else null
        // Apply depth layer colour map + isobath contours visibility toggle
        val visibleDepthBitmap = if (appSettings.depthLayerVisible) depthBitmap else null
        val visibleIsobaths = if (appSettings.depthLayerVisible) isobaths else emptyList()

        // ── Layer 0: OSMdroid map (fills entire Box) ───────────────────────
        CoastlineMapView(
            segments = segments,
            regulatedZones = visibleRegulatedZones,
            zone300 = visibleZone300,
            depthBitmap = visibleDepthBitmap,
            lowDepthWarningBitmap = visibleLowDepthWarning,
            depthBox = depthBox,
            isobaths = visibleIsobaths,
            zoomLevel = zoomLevel,
            center = mapCenter,
            initialZoom = zoomLevel,
            boatPosition = boatPosition,
            headingDeg = headingDeg,
            onCenterChanged = onCenterChanged,
            onZoomChanged = onZoomChanged,
            onMapViewReady = onMapViewReady,
            modifier = Modifier.fillMaxSize()
        )

        // ── Layer 0 overlays: cap (bottom), arrow (middle), marker (top) ──
        val moving = navigationState.speedKnots != null || navigationState.demoSpeedKnots != null
        if (moving && appSettings.headingLineVisible) {
            DirectionLine(modifier = Modifier.fillMaxSize())
        }

        CapArrowOverlay(
            zoomLevel = zoomLevel,
            navigationState = navigationState,
            showCapArrow = appSettings.capArrowVisible,
            modifier = Modifier.fillMaxSize()
        )

        CenterMarkerOverlay(
            isWater = isWater,
            zoomLevel = zoomLevel,
            distanceToShore = distanceToShore,
            modifier = Modifier.align(Alignment.Center)
        )

        // ── Layer 1: 2-column overlay row (left fills, right content-sized) ──
        Row(modifier = Modifier.fillMaxSize()) {

            // ── LEFT COLUMN: top + middle + btm ──────────────────────────
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {

                // top zone: GPS status + EarthWater (statusBars minus 6dp)
                Row(
                    modifier = Modifier
                        .padding(top = topInset, start = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GpsStatusIcon(state = gpsIconState)
                    EarthWaterIcon(
                        emoji = if (isWater) "🌊" else "🏔️",
                        isActive = true,
                        activeColor = if (isWater) ComposeColor(AppConfig.statusEarthWaterWater) else ComposeColor(AppConfig.statusEarthWaterLand),
                        contentDescription = if (isWater) stringResource(R.string.side_water) else stringResource(R.string.side_land),
                    )
                }

                // middle zone: no overlay — map fills here
                Spacer(modifier = Modifier.weight(1f))

                // btm zone: tags + txt + overlays
                // In landscape, clear the nav bar; in portrait, match cb's 6dp gap.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isLandscape) Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                            else Modifier.padding(bottom = 6.dp)
                        )
                ) {
                    // Behind layer: regulated zone icons + info text
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 6.dp)
                    ) {
                        RegulatedZoneWarningStrip(
                            regulatedZones = visibleRegulatedZones,
                            boatPosition = boatPosition,
                            inZone300 = inZone300,
                            modifier = Modifier.align(Alignment.Bottom)
                        )
                        if (appSettings.regulationInfoVisible) {
                            RegulatedZoneInfoText(
                                regulatedZones = visibleRegulatedZones,
                                boatPosition = boatPosition,
                                inZone300 = inZone300,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 4.dp)
                                    .align(Alignment.Bottom)
                            )
                        }
                    }

                    // Middle layer: loading/error overlay (conditional)
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(start = 56.dp, end = 76.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (rasterProgress != null && rasterProgress!!.globalProgress < 100) {
                            val rp = rasterProgress!!
                            LoadingOverlay(
                                progress = GenerationProgress(rp.phase, rp.globalProgress),
                                title = "Generating Layers"
                            )
                        }
                        if (state is CoastlineState.Error) {
                            ErrorOverlay(
                                message = (state as CoastlineState.Error).message,
                                onRetry = onRetry
                            )
                        }
                    }

                    // Top layer: exit toast (conditional)
                    if (showExitBanner) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(start = 56.dp, end = 76.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = ComposeColor(AppConfig.uiSettingsToastBackground),
                                shadowElevation = 8.dp
                            ) {
                                Text(
                                    text = stringResource(R.string.exit_press_back_again),
                                    color = ComposeColor.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ── RIGHT COLUMN (ctrls): ct + cm + cb ───────────────────────
            //   Width sized to content by the Row; horizontal padding only.
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(start = 12.dp, end = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val anyFanOpen = expandedFanId != null

                // ct (controls top): Settings button + future
                // statusBars inset minus 6dp — sits closer to the bar.
                val ctAlpha by animateFloatAsState(
                    targetValue = if (anyFanOpen) 0f else 1f,
                    animationSpec = tween(300)
                )
                Column(
                    modifier = Modifier
                        .padding(top = topInset)
                        .alpha(ctAlpha),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    SettingsButton(onClick = onOpenSettings)
                    // Future top controls can be added here
                }

                // cm (controls middle): fan buttons, fills remaining height
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Fan layout with built-in alpha fade for children
                    val isExpanded = expandedFanId == ControlId.LAYER_FAN
                    // Only the fan anchor fades when another fan is expanded;
                    // the fan itself is always visible when it's the expanded one.
                    val cmAlpha by animateFloatAsState(
                        targetValue = if (anyFanOpen && !isExpanded) 0f else 1f,
                        animationSpec = tween(300)
                    )
                    Box(modifier = Modifier.alpha(cmAlpha)) {
                        FanLayout(
                            config = FanConfig(
                                maxCount = 5,
                                currentCount = 4,
                                direction = FanDirection.LEFT,
                                isOpen = isExpanded,
                                toggleChildren = true,
                                showActiveBadge = true,
                                activeChildCount = listOf(
                                    appSettings.depthLayerVisible,
                                    appSettings.regulatedZonesVisible,
                                    appSettings.zone300Visible,
                                    appSettings.lowDepthWarningVisible
                                ).count { it }
                            ),
                            parent = { _: Boolean, _: Int -> ThreeStripeLayerIcon(alpha = 1f) },
                            onParentClick = { onToggleFan(ControlId.LAYER_FAN) },
                            children = listOf<@Composable (Boolean) -> Unit>(
                                { isActive -> DepthBarIcon(alpha = if (isActive) ButtonColors.activeAlpha else ButtonColors.inactiveAlpha) },
                                { isActive -> RegulatedZoneIcon(alpha = if (isActive) ButtonColors.activeAlpha else ButtonColors.inactiveAlpha) },
                                { isActive -> DoubleCircleIcon(alpha = if (isActive) ButtonColors.activeAlpha else ButtonColors.inactiveAlpha) },
                                { isActive -> WarningTriangleIcon(alpha = if (isActive) ButtonColors.activeAlpha else ButtonColors.inactiveAlpha) }
                            ),
                            activeStates = listOf(
                                appSettings.depthLayerVisible,
                                appSettings.regulatedZonesVisible,
                                appSettings.zone300Visible,
                                appSettings.lowDepthWarningVisible
                            ),
                            onChildClick = { index: Int, _: Boolean ->
                                when (index) {
                                    0 -> onToggleDepthLayer()
                                    1 -> onToggleRegulatedZones()
                                    2 -> onToggleZone300()
                                    3 -> onToggleLowDepthWarning()
                                }
                            }
                        )
                    }
                    // Future middle controls (2nd fan, etc.) can be added here
                }

                // cb (controls bottom): Zoom +/- buttons + future
                // Bottom gap matches the right padding (6.dp).
                val cbAlpha by animateFloatAsState(
                    targetValue = if (anyFanOpen) 0f else 1f,
                    animationSpec = tween(300)
                )
                Column(
                    modifier = Modifier
                        .padding(bottom = 6.dp)
                        .alpha(cbAlpha),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (mapView != null) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            MapControlButton(
                                onClick = {
                                    val mv = mapView ?: return@MapControlButton
                                    mv.controller.zoomIn()
                                    onZoomChanged(mv.zoomLevelDouble)
                                }
                            ) { PlusIcon() }
                            MapControlButton(
                                onClick = {
                                    val mv = mapView ?: return@MapControlButton
                                    mv.controller.zoomOut()
                                    onZoomChanged(mv.zoomLevelDouble)
                                }
                            ) { MinusIcon() }
                        }
                    }
                    // Future bottom controls can be added here
                }
            }
        }
    }
}

// ── Loading overlay ─────────────────────────────────────────────────────────

@Composable
private fun LoadingOverlay(
    progress: GenerationProgress,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.map_loading_coastline)
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
            color = ComposeColor(AppConfig.uiProgressAccent)
        )

        Text(
            text = title,
            color = ComposeColor(AppConfig.uiProgressAccent),
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
                color = ComposeColor(AppConfig.uiProgressAccent),
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
                color = ComposeColor(AppConfig.uiProgressAccent),
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
            .background(ComposeColor(AppConfig.uiErrorCard))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.error_title),
            color = ComposeColor.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = message,
            color = ComposeColor(AppConfig.uiErrorText),
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

// ── Per-layer overlay reference tracking ──────────────────────────────────────

/**
 * Holds persistent references to all [MapView] overlays per layer, plus the
 * last-known data state for dirty checking. Used by [CoastlineMapView] to
 * rebuild only the layer whose data actually changed — no blanket removeAll.
 */
private class OverlayTracker {
    val depth = mutableListOf<GroundOverlay>()
    val lowDepth = mutableListOf<GroundOverlay>()
    val isobaths = mutableListOf<Polyline>()
    val regulatedZones = mutableListOf<Polygon>()
    val zone300 = mutableListOf<Any>()
    val coastline = mutableListOf<Any>()

    var lastDepthBitmap: Bitmap? = null
    var lastLowDepthBitmap: Bitmap? = null
    var lastIsobaths: List<Isobath> = emptyList()
    var lastRegulatedZones: RegulatedZoneSet? = null
    var lastZone300: Zone300Data? = null
    var lastSegments: List<CoastlineSegment> = emptyList()
    var lastDepthBox: BoundingBox? = null
    var lastZoom: Double = -1.0
}

// ── OSMdroid map view ───────────────────────────────────────────────────────

@Composable
private fun CoastlineMapView(
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
    boatPosition: LatLng? = null,
    headingDeg: Double = -1.0,
    onCenterChanged: (Double, Double) -> Unit = { _, _ -> },
    onZoomChanged: (Double) -> Unit = {},
    onMapViewReady: (MapView) -> Unit = {},
    modifier: Modifier = Modifier
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
                // Draw all layers bottom-to-top; each appends to both mapView.overlays
                // and the corresponding tracker list for later selective rebuild.
                drawDepthMap(this, depthBitmap, depthBox, zoomLevel, tracker.depth)
                drawLowDepthWarning(this, lowDepthWarningBitmap, depthBox, zoomLevel, tracker.lowDepth)
                drawIsobaths(this, isobaths, zoomLevel, tracker.isobaths)
                drawRegulatedZones(this, regulatedZones, zoomLevel, tracker.regulatedZones)
                drawZone300(this, zone300, zoomLevel, tracker.zone300)
                drawCoastline(this, segments, tracker.coastline)

                // Seed last-known state so the first update call sees no changes.
                tracker.lastDepthBitmap = depthBitmap
                tracker.lastLowDepthBitmap = lowDepthWarningBitmap
                tracker.lastIsobaths = isobaths
                tracker.lastRegulatedZones = regulatedZones
                tracker.lastZone300 = zone300
                tracker.lastSegments = segments
                tracker.lastDepthBox = depthBox
                tracker.lastZoom = zoomLevel

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
        },
        update = { mapView ->
            var dirty = false

            // ── Zone300 layer ──────────────────────────────────────────────
            if (zone300 !== tracker.lastZone300 || zoomLevel != tracker.lastZoom) {
                mapView.overlays.removeAll(tracker.zone300)
                tracker.zone300.clear()
                drawZone300(mapView, zone300, zoomLevel, tracker.zone300)
                tracker.lastZone300 = zone300
                dirty = true
            }

            // ── Regulated zones layer ──────────────────────────────────────
            if (regulatedZones !== tracker.lastRegulatedZones || zoomLevel != tracker.lastZoom) {
                mapView.overlays.removeAll(tracker.regulatedZones)
                tracker.regulatedZones.clear()
                drawRegulatedZones(mapView, regulatedZones, zoomLevel, tracker.regulatedZones)
                tracker.lastRegulatedZones = regulatedZones
                dirty = true
            }

            // ── Depth colour raster layer ──────────────────────────────────
            if (depthBitmap !== tracker.lastDepthBitmap ||
                depthBox !== tracker.lastDepthBox ||
                zoomLevel != tracker.lastZoom
            ) {
                mapView.overlays.removeAll(tracker.depth)
                tracker.depth.clear()
                drawDepthMap(mapView, depthBitmap, depthBox, zoomLevel, tracker.depth)
                tracker.lastDepthBitmap = depthBitmap
                tracker.lastDepthBox = depthBox
                dirty = true
            }

            // ── Low-depth warning layer ────────────────────────────────────
            if (lowDepthWarningBitmap !== tracker.lastLowDepthBitmap ||
                depthBox !== tracker.lastDepthBox ||
                zoomLevel != tracker.lastZoom
            ) {
                mapView.overlays.removeAll(tracker.lowDepth)
                tracker.lowDepth.clear()
                drawLowDepthWarning(mapView, lowDepthWarningBitmap, depthBox, zoomLevel, tracker.lowDepth)
                tracker.lastLowDepthBitmap = lowDepthWarningBitmap
                dirty = true
            }

            // ── Isobaths layer ─────────────────────────────────────────────
            if (isobaths !== tracker.lastIsobaths || zoomLevel != tracker.lastZoom) {
                mapView.overlays.removeAll(tracker.isobaths)
                tracker.isobaths.clear()
                drawIsobaths(mapView, isobaths, zoomLevel, tracker.isobaths)
                tracker.lastIsobaths = isobaths
                dirty = true
            }

            // ── Coastline layer ────────────────────────────────────────────
            if (segments !== tracker.lastSegments || zoomLevel != tracker.lastZoom) {
                mapView.overlays.removeAll(tracker.coastline)
                tracker.coastline.clear()
                drawCoastline(mapView, segments, tracker.coastline)
                tracker.lastSegments = segments
                dirty = true
            }

            tracker.lastZoom = zoomLevel
            if (dirty) mapView.invalidate()
        }
    )

    // ── Cone + dashed line: DISABLED — see more-dedebug subfeature ──────────────
    // These were causing excessive mapView.invalidate() calls during drag.
    // Re-enable once the decoupled rendering is implemented.
    // LaunchedEffect(headingAheadResult, zoomLevel, boatPosition, headingDeg) {
    //     val mv = localMapView.value ?: return@LaunchedEffect
    //     drawZoneAheadCone(mv, boatPosition, headingAheadResult, zoomLevel, headingDeg)
    //     val hitPt = headingAheadResult?.intersectionLatLng
    //     drawZoneAheadLine(mv, boatPosition, hitPt, zoomLevel)
    //     mv.invalidate()
    // }
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

/** Arrow length in dp at [REF_ZOOM] per knot of speed (65 dp ÷ 30 kn ≈ 2.17). */
private const val CAP_DP_PER_KNOT = 65.0 / 30.0
/** Minimum arrow length in dp at [REF_ZOOM] (barely visible nub at 3 kn). */
private const val CAP_MIN_DP = 1.0
/** Maximum arrow length in dp at [REF_ZOOM] (30+ kn capped). */
private const val CAP_MAX_DP = 65.0
/** Below this speed (knots) the arrow is hidden. */
private const val CAP_MIN_SPEED_KNOTS = 2.5f

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
    val description = if (isWater) stringResource(R.string.marker_position_water)
                      else stringResource(R.string.marker_position_land)

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

    // The marker Box stays at Alignment.Center (map center) in the parent.
    // On water: the boat image is shifted down by half its height so its top-center
    // aligns with the map center (GPS position at the boat's bow).
    // On land:   the dot stays centered (no offset — a dot has no direction).
    Box(modifier = modifier.size(finalSizeDp)) {
        // ── Boat/land marker ──────────────────────────────────────────────
        val yOffset = if (isWater) finalSizeDp / 2 else 0.dp
        Image(
            painter = painterResource(id = drawableId),
            contentDescription = description,
            modifier = Modifier
                .fillMaxSize()
                .offset(y = yOffset),
            contentScale = ContentScale.Fit
        )
    }
}

// ── Cap arrow overlay ────────────────────────────────────────────────────────

/**
 * Speed indicator arrow drawn from the screen centre upward, above the direction
 * line but below the boat/dot marker. Length scales with speed (knots) and zoom
 * level, matching the marker's exponential zoom factor. Hidden below
 * [CAP_MIN_SPEED_KNOTS] or when the user disables it via [showCapArrow].
 */
@Composable
private fun CapArrowOverlay(
    zoomLevel: Double,
    navigationState: NavigationState,
    showCapArrow: Boolean,
    modifier: Modifier = Modifier
) {
    val effectiveSpeedKn = navigationState.speedKnots ?: navigationState.demoSpeedKnots
    val hasSpeed = effectiveSpeedKn != null && effectiveSpeedKn > CAP_MIN_SPEED_KNOTS
    if (!hasSpeed || !showCapArrow) return

    val scaleFactor = 2.0.pow(ZOOM_EXPONENT * (zoomLevel - REF_ZOOM))
    val baseArrowDp = (effectiveSpeedKn!! * CAP_DP_PER_KNOT).coerceIn(CAP_MIN_DP, CAP_MAX_DP)
    val arrowDp = (baseArrowDp * scaleFactor).dp

    val arrowColor = ComposeColor(AppConfig.mapNavigationArrowColor)
    Canvas(modifier = modifier) {
        val arrowLenPx = arrowDp.toPx()
        val cX = size.width / 2
        val midY = size.height / 2
        val endY = midY - arrowLenPx

        drawLine(
            color = arrowColor,
            start = Offset(cX, midY),
            end = Offset(cX, endY),
            strokeWidth = 2.25.dp.toPx(),
            cap = StrokeCap.Round
        )
        val headLen = 9.dp.toPx()
        val headSpread = 0.5f
        val path = Path().apply {
            moveTo(cX, endY)
            lineTo(
                cX - (headLen * sin(headSpread)).toFloat(),
                endY + (headLen * cos(headSpread)).toFloat()
            )
            lineTo(
                cX + (headLen * sin(headSpread)).toFloat(),
                endY + (headLen * cos(headSpread)).toFloat()
            )
            close()
        }
        drawPath(path, color = arrowColor)
    }
}

// ── Direction line overlay ───────────────────────────────────────────────────

/**
 * Thin dashed line drawn from the screen center (boat position) outward in the
 * heading direction, extending to the edge of the map.
 */
@Composable
private fun DirectionLine(
    modifier: Modifier = Modifier
) {
    val lineColor = ComposeColor(AppConfig.mapNavigationLineColor)
    Canvas(modifier = modifier) {
        val cX = size.width / 2
        val cY = size.height / 2

        drawLine(
            color = lineColor,
            start = Offset(cX, cY),
            end = Offset(cX, 0f),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 6f), 0f),
            cap = StrokeCap.Round
        )
    }
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
                if (isActive) activeColor.copy(alpha = AppConfig.statusGpsAlphaActive)
                else ComposeColor(AppConfig.statusEarthWaterInactive)
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
            containerColor = ButtonColors.bg
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = stringResource(R.string.map_settings),
            tint = ButtonColors.icon,
            modifier = Modifier.size(32.dp)
        )
    }
}



/**
 * 5-state GPS indicator icon — placed top-left to the left of [EarthWaterIcon].
 *
 * States match the derived [GpsIconState] enum:
 * - [DEMO]: GPS toggle off, gray satellite outline
 * - [ACQUIRING]: GPS on but no fix yet, amber background + pulsing dot
 * - [HEALTHY]: GPS fix good, green background
 * - [IDLE]: GPS fix but stationary (reduced cadence), cyan background
 * - [STALE]: GPS lost / hasLock false / error, red background
 */
private enum class GpsIconState { DEMO, ACQUIRING, HEALTHY, IDLE, STALE }

@Composable
private fun GpsStatusIcon(
    state: GpsIconState,
    modifier: Modifier = Modifier
) {
    val baseColor: ComposeColor
    val bgAlpha: Float
    val contentAlpha: Float
    when (state) {
        GpsIconState.DEMO -> {
            baseColor = ComposeColor(AppConfig.statusGpsDemo)
            bgAlpha = AppConfig.statusGpsAlphaDimmed
            contentAlpha = 0.50f
        }
        GpsIconState.ACQUIRING -> { baseColor = ComposeColor(AppConfig.statusGpsAcquiring); bgAlpha = AppConfig.statusGpsAlphaActive; contentAlpha = 1f }
        GpsIconState.HEALTHY -> { baseColor = ComposeColor(AppConfig.statusGpsHealthy); bgAlpha = AppConfig.statusGpsAlphaActive; contentAlpha = 1f }
        GpsIconState.IDLE -> { baseColor = ComposeColor(AppConfig.statusGpsIdle); bgAlpha = AppConfig.statusGpsAlphaActive; contentAlpha = 1f }
        GpsIconState.STALE -> { baseColor = ComposeColor(AppConfig.statusGpsStale); bgAlpha = AppConfig.statusGpsAlphaActive; contentAlpha = 1f }
    }
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(baseColor.copy(alpha = bgAlpha)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "📡",
            fontSize = 22.sp,
            modifier = if (contentAlpha < 1f) Modifier.alpha(contentAlpha) else Modifier
        )
    }
}

// ── Settings overlay (full-screen page) ─────────────────────────────────────

// Tab definitions for the settings page.
private val settingsTabLabels = listOf("General", "Navigation", "System")

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SettingsOverlay(
    settings: AppSettings,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    onGpsModeChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    selectedTab: Int,
    onTabChange: (Int) -> Unit,
    onRegenerateRasters: (List<RasterCache.Step>) -> Unit = {},
    displayScrollState: ScrollState,
    navigationScrollState: ScrollState,
    systemScrollState: ScrollState,
) {
    val pagerState = rememberPagerState(pageCount = { 3 })

    // Sync tab selection <-> pager position (bidirectional).
    // The pagerSyncSettled flag prevents the initial pager→tab sync from
    // overwriting selectedTab before animateScrollToPage has a chance to
    // restore the persisted tab selection.
    val pagerSyncSettled = remember { mutableStateOf(false) }
    LaunchedEffect(selectedTab) {
        pagerState.animateScrollToPage(selectedTab)
        pagerSyncSettled.value = true
    }
    LaunchedEffect(pagerState.currentPage) {
        if (pagerSyncSettled.value) {
            onTabChange(pagerState.currentPage)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ComposeColor(AppConfig.uiSettingsBackground))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 24.dp, vertical = 3.dp)
        ) {
            // ── Header row: title + close (back) button ───────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ComposeColor(AppConfig.uiSettingsSwitchTrackInactive)
                        ),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back),
                            tint = ComposeColor.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = stringResource(R.string.settings_title),
                        color = ComposeColor.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Tab bar (manual Row + indicator instead of TabRow) ─────────
            val tabColor = ComposeColor(AppConfig.uiSettingsAccent)
            val tabCount = settingsTabLabels.size
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ComposeColor(AppConfig.uiSettingsBackground))
                    .drawBehind {
                        // Draw the selected tab indicator line at the bottom
                        val tabWidth = size.width / tabCount
                        val indicatorLeft = tabWidth * selectedTab
                        drawRect(
                            color = tabColor,
                            topLeft = Offset(indicatorLeft, size.height - 3.dp.toPx()),
                            size = Size(tabWidth, 3.dp.toPx())
                        )
                    }
            ) {
                settingsTabLabels.forEachIndexed { index, label ->
                    val isSelected = selectedTab == index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onTabChange(index) }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            color = if (isSelected) tabColor else ComposeColor(AppConfig.uiSettingsTextSecondary)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Tab content ───────────────────────────────────────────────
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                when (page) {
                    0 -> GeneralSettings(settings, onUpdateSettings, displayScrollState)
                    1 -> NavigationSettings(settings, onUpdateSettings, navigationScrollState)
                    2 -> SystemSettings(settings, onUpdateSettings, onGpsModeChange, onRegenerateRasters, systemScrollState)
                }
            }

            // ── Footer ────────────────────────────────────────────────────
            Text(
                text = stringResource(R.string.app_version_footer),
                color = ComposeColor(AppConfig.uiSettingsFooterText),
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

// ── General tab ───────────────────────────────────────────────────────────

@Composable
private fun GeneralSettings(
    settings: AppSettings,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    scrollState: ScrollState
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        // ── Coastline overlay toggle ──────────────────────────────────
        SectionHeader(title = stringResource(R.string.settings_section_display))
        Spacer(modifier = Modifier.height(8.dp))

        SubSectionHeader(
            title = "Layers",
            description = null
        )
        // ── Low-depth warning overlay toggle — grouped card ────────────
        //   (placed before zone 300 and regulated zones so the two spatial
        //    overlays are grouped together)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ComposeColor(AppConfig.uiSettingsCardBackground))
        ) {
            // Toggle row (inline)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_low_depth_warning_label),
                        color = ComposeColor.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(R.string.settings_low_depth_warning_desc),
                        color = ComposeColor(AppConfig.uiSettingsTextMuted),
                        fontSize = 13.sp
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Switch(
                    checked = settings.lowDepthWarningVisible,
                    onCheckedChange = { visible ->
                        onUpdateSettings { it.copy(lowDepthWarningVisible = visible) }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ComposeColor(AppConfig.uiSettingsAccent),
                        checkedTrackColor = ComposeColor(AppConfig.uiSettingsAccent).copy(alpha = 0.4f),
                        uncheckedThumbColor = ComposeColor(AppConfig.uiSettingsTextMuted),
                        uncheckedTrackColor = ComposeColor(AppConfig.uiSettingsSwitchTrackInactive)
                    )
                )
            }

            // Warning sliders — collapsible, only visible when warning is on
            if (settings.lowDepthWarningVisible) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .background(ComposeColor.White.copy(alpha = 0.1f))
                )
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    var warningExpanded by remember { mutableStateOf(false) }
                    SettingsExpander(
                        label = "Warning settings",
                        expanded = warningExpanded,
                        onToggle = { warningExpanded = !warningExpanded },
                        labelStyle = TextStyle(
                            color = ComposeColor.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))
                        SettingsSliderGroup {
                            SliderRowContent(
                                label = stringResource(R.string.settings_low_depth_threshold_label),
                                description = stringResource(R.string.settings_low_depth_threshold_desc),
                                valueLabel = stringResource(R.string.settings_value_depth, settings.lowDepthWarningMaxM),
                                value = settings.lowDepthWarningMaxM,
                                valueRange = 0.5f..5f,
                                steps = 8,
                                onValueChange = { v ->
                                    onUpdateSettings { it.copy(lowDepthWarningMaxM = (v * 2f).roundToInt() / 2f) }
                                }
                            )
                            SliderRowDivider()
                            SliderRowContent(
                                label = stringResource(R.string.settings_low_depth_opacity_label),
                                description = stringResource(R.string.settings_low_depth_opacity_desc),
                                valueLabel = stringResource(R.string.settings_value_percent, settings.lowDepthWarningMinOpacityPct),
                                value = settings.lowDepthWarningMinOpacityPct.toFloat(),
                                valueRange = 0f..100f,
                                steps = 19,
                                onValueChange = { v ->
                                    onUpdateSettings { it.copy(lowDepthWarningMinOpacityPct = (v / 5f).roundToInt() * 5) }
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Zone 300 m overlay toggle ─────────────────────────────────
        SettingsToggleRow(
            label = stringResource(R.string.settings_zone300_label),
            description = stringResource(R.string.settings_zone300_desc),
            checked = settings.zone300Visible,
            onCheckedChange = { visible ->
                onUpdateSettings { it.copy(zone300Visible = visible) }
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // ── Regulated zones overlay toggle — grouped card ──────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ComposeColor(AppConfig.uiSettingsCardBackground))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_regulated_zones_label),
                        color = ComposeColor.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(R.string.settings_regulated_zones_desc),
                        color = ComposeColor(AppConfig.uiSettingsTextMuted),
                        fontSize = 13.sp
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Switch(
                    checked = settings.regulatedZonesVisible,
                    onCheckedChange = { visible ->
                        onUpdateSettings { it.copy(regulatedZonesVisible = visible) }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ComposeColor(AppConfig.uiSettingsAccent),
                        checkedTrackColor = ComposeColor(AppConfig.uiSettingsAccent).copy(alpha = 0.4f),
                        uncheckedThumbColor = ComposeColor(AppConfig.uiSettingsTextMuted),
                        uncheckedTrackColor = ComposeColor(AppConfig.uiSettingsSwitchTrackInactive)
                    )
                )
            }

            if (settings.regulatedZonesVisible) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .background(ComposeColor.White.copy(alpha = 0.1f))
                )
                // Regulation info — collapsible toggle for info text panel
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    SettingsExpander(
                        label = "Regulation info",
                        expanded = settings.regulationInfoExpanded,
                        onToggle = { onUpdateSettings { it.copy(regulationInfoExpanded = !it.regulationInfoExpanded) } },
                        labelStyle = TextStyle(
                            color = ComposeColor.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    ) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Show zone info text beside the icon strip",
                            color = ComposeColor(AppConfig.uiDashboardTextMuted),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Info text visible",
                                color = ComposeColor.White,
                                fontSize = 14.sp
                            )
                            Switch(
                                checked = settings.regulationInfoVisible,
                                onCheckedChange = { visible ->
                                    onUpdateSettings { it.copy(regulationInfoVisible = visible) }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = ComposeColor(AppConfig.uiSettingsAccent),
                                    checkedTrackColor = ComposeColor(AppConfig.uiSettingsAccent).copy(alpha = 0.4f)
                                )
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .background(ComposeColor.White.copy(alpha = 0.1f))
                )
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    SettingsExpander(
                        label = "Categories",
                        expanded = settings.categoryFilterExpanded,
                        onToggle = { onUpdateSettings { it.copy(categoryFilterExpanded = !it.categoryFilterExpanded) } },
                        labelStyle = TextStyle(
                            color = ComposeColor.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))
                        RegulatedZoneCategoryToggles(settings, onUpdateSettings)
                    }
                }
                // Boat size in its own collapsible
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .background(ComposeColor.White.copy(alpha = 0.1f))
                )
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    SettingsExpander(
                        label = "Boat size",
                        expanded = settings.boatSizeFilterExpanded,
                        onToggle = { onUpdateSettings { it.copy(boatSizeFilterExpanded = !it.boatSizeFilterExpanded) } },
                        labelStyle = TextStyle(
                            color = ComposeColor.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))
                        BoatSizeSlider(settings, onUpdateSettings)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Coastline overlay toggle ──────────────────────────────────
        SettingsToggleRow(
            label = stringResource(R.string.settings_coastline_label),
            description = stringResource(R.string.settings_coastline_desc),
            checked = settings.coastlineVisible,
            onCheckedChange = { visible ->
                onUpdateSettings { it.copy(coastlineVisible = visible) }
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // ── Heading direction line toggle ────────────────────────────
        SubSectionHeader(
            title = "Navigation",
            description = null
        )
        Spacer(modifier = Modifier.height(8.dp))

        SettingsToggleRow(
            label = stringResource(R.string.settings_heading_line_label),
            description = stringResource(R.string.settings_heading_line_desc),
            checked = settings.headingLineVisible,
            onCheckedChange = { visible ->
                onUpdateSettings { it.copy(headingLineVisible = visible) }
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // ── Variable cap arrow toggle ─────────────────────────────────
        SettingsToggleRow(
            label = stringResource(R.string.settings_cap_arrow_label),
            description = stringResource(R.string.settings_cap_arrow_desc),
            checked = settings.capArrowVisible,
            onCheckedChange = { visible ->
                onUpdateSettings { it.copy(capArrowVisible = visible) }
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // ── Demo heading-up toggle ──────────────────────────────────────
        SettingsToggleRow(
            label = stringResource(R.string.settings_demo_heading_label),
            description = stringResource(R.string.settings_demo_heading_desc),
            checked = settings.demoHeadingUp,
            onCheckedChange = { headingUp ->
                onUpdateSettings { it.copy(demoHeadingUp = headingUp) }
            }
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ── Navigation tab ────────────────────────────────────────────────────────

@Composable
private fun NavigationSettings(
    settings: AppSettings,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    scrollState: ScrollState
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        // ── 300 m zone alert (auto-show on approach) — grouped card ─────
        SectionHeader(title = stringResource(R.string.settings_alert_label))
        Spacer(modifier = Modifier.height(8.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ComposeColor(AppConfig.uiSettingsCardBackground))
        ) {
            // Auto-show GPS mode toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_alert_gps_label),
                        color = ComposeColor.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(R.string.settings_alert_gps_desc),
                        color = ComposeColor(AppConfig.uiSettingsTextMuted),
                        fontSize = 13.sp
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Switch(
                    checked = settings.zone300AutoShowGps,
                    onCheckedChange = { on -> onUpdateSettings { it.copy(zone300AutoShowGps = on) } },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ComposeColor(AppConfig.uiSettingsAccent),
                        checkedTrackColor = ComposeColor(AppConfig.uiSettingsAccent).copy(alpha = 0.4f),
                        uncheckedThumbColor = ComposeColor(AppConfig.uiSettingsTextMuted),
                        uncheckedTrackColor = ComposeColor(AppConfig.uiSettingsSwitchTrackInactive)
                    )
                )
            }

            // Thin divider between the two toggles
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(ComposeColor.White.copy(alpha = 0.1f))
            )

            // Auto-show Demo mode toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_alert_demo_label),
                        color = ComposeColor.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(R.string.settings_alert_demo_desc),
                        color = ComposeColor(AppConfig.uiSettingsTextMuted),
                        fontSize = 13.sp
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Switch(
                    checked = settings.zone300AutoShowDemo,
                    onCheckedChange = { on -> onUpdateSettings { it.copy(zone300AutoShowDemo = on) } },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ComposeColor(AppConfig.uiSettingsAccent),
                        checkedTrackColor = ComposeColor(AppConfig.uiSettingsAccent).copy(alpha = 0.4f),
                        uncheckedThumbColor = ComposeColor(AppConfig.uiSettingsTextMuted),
                        uncheckedTrackColor = ComposeColor(AppConfig.uiSettingsSwitchTrackInactive)
                    )
                )
            }

            // Alert sliders — collapsible, only visible when either auto-show is on
            if (settings.zone300AutoShowGps || settings.zone300AutoShowDemo) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .background(ComposeColor.White.copy(alpha = 0.1f))
                )
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    var alertExpanded by remember { mutableStateOf(false) }
                    SettingsExpander(
                        label = "Alert settings",
                        expanded = alertExpanded,
                        onToggle = { alertExpanded = !alertExpanded },
                        labelStyle = TextStyle(
                            color = ComposeColor.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))
                        SettingsSliderGroup {
                            SliderRowContent(
                                label = stringResource(R.string.settings_alert_dist_label),
                                description = stringResource(R.string.settings_alert_dist_desc),
                                valueLabel = stringResource(R.string.settings_value_meters, settings.zoneAutoRevealDistanceM.roundToInt()),
                                value = settings.zoneAutoRevealDistanceM,
                                valueRange = 50f..500f,
                                steps = 17,
                                onValueChange = { v -> onUpdateSettings { it.copy(zoneAutoRevealDistanceM = (v / 25f).roundToInt() * 25f) } }
                            )
                            SliderRowDivider()
                            SliderRowContent(
                                label = stringResource(R.string.settings_alert_time_label),
                                description = stringResource(R.string.settings_alert_time_desc),
                                valueLabel = stringResource(R.string.settings_value_seconds, settings.zoneAutoRevealTimeS),
                                value = settings.zoneAutoRevealTimeS.toFloat(),
                                valueRange = 5f..120f,
                                steps = 22,
                                onValueChange = { v -> onUpdateSettings { it.copy(zoneAutoRevealTimeS = (v / 5f).roundToInt() * 5) } }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Speed zone alert (auto-show on approach) — grouped card ─────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ComposeColor(AppConfig.uiSettingsCardBackground))
        ) {
            // Auto-show GPS mode toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Speed zone alert (GPS)",
                        color = ComposeColor.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Auto-show speed zones when approaching in GPS mode",
                        color = ComposeColor(AppConfig.uiSettingsTextMuted),
                        fontSize = 13.sp
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Switch(
                    checked = settings.speedZoneAutoShowGps,
                    onCheckedChange = { on -> onUpdateSettings { it.copy(speedZoneAutoShowGps = on) } },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ComposeColor(AppConfig.uiSettingsAccent),
                        checkedTrackColor = ComposeColor(AppConfig.uiSettingsAccent).copy(alpha = 0.4f),
                        uncheckedThumbColor = ComposeColor(AppConfig.uiSettingsTextMuted),
                        uncheckedTrackColor = ComposeColor(AppConfig.uiSettingsSwitchTrackInactive)
                    )
                )
            }

            // Thin divider between the two toggles
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(ComposeColor.White.copy(alpha = 0.1f))
            )

            // Auto-show Demo mode toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Speed zone alert (Demo)",
                        color = ComposeColor.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Auto-show speed zones when panning the map toward a zone",
                        color = ComposeColor(AppConfig.uiSettingsTextMuted),
                        fontSize = 13.sp
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Switch(
                    checked = settings.speedZoneAutoShowDemo,
                    onCheckedChange = { on -> onUpdateSettings { it.copy(speedZoneAutoShowDemo = on) } },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ComposeColor(AppConfig.uiSettingsAccent),
                        checkedTrackColor = ComposeColor(AppConfig.uiSettingsAccent).copy(alpha = 0.4f),
                        uncheckedThumbColor = ComposeColor(AppConfig.uiSettingsTextMuted),
                        uncheckedTrackColor = ComposeColor(AppConfig.uiSettingsSwitchTrackInactive)
                    )
                )
            }

            // Thin divider between speed zone section and regulated zone section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(ComposeColor.White.copy(alpha = 0.1f))
            )

            // Regulated zone overlay auto-show GPS toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Regulated zone alert (GPS)",
                        color = ComposeColor.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Auto-show regulated zones when approaching a speed-enforced zone",
                        color = ComposeColor(AppConfig.uiSettingsTextMuted),
                        fontSize = 13.sp
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Switch(
                    checked = settings.regulatedZoneAutoShowGps,
                    onCheckedChange = { on -> onUpdateSettings { it.copy(regulatedZoneAutoShowGps = on) } },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ComposeColor(AppConfig.uiSettingsAccent),
                        checkedTrackColor = ComposeColor(AppConfig.uiSettingsAccent).copy(alpha = 0.4f),
                        uncheckedThumbColor = ComposeColor(AppConfig.uiSettingsTextMuted),
                        uncheckedTrackColor = ComposeColor(AppConfig.uiSettingsSwitchTrackInactive)
                    )
                )
            }

            // Thin divider between the two toggles
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(ComposeColor.White.copy(alpha = 0.1f))
            )

            // Regulated zone overlay auto-show Demo toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Regulated zone alert (Demo)",
                        color = ComposeColor.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Auto-show regulated zones when panning toward a speed-enforced zone",
                        color = ComposeColor(AppConfig.uiSettingsTextMuted),
                        fontSize = 13.sp
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Switch(
                    checked = settings.regulatedZoneAutoShowDemo,
                    onCheckedChange = { on -> onUpdateSettings { it.copy(regulatedZoneAutoShowDemo = on) } },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ComposeColor(AppConfig.uiSettingsAccent),
                        checkedTrackColor = ComposeColor(AppConfig.uiSettingsAccent).copy(alpha = 0.4f),
                        uncheckedThumbColor = ComposeColor(AppConfig.uiSettingsTextMuted),
                        uncheckedTrackColor = ComposeColor(AppConfig.uiSettingsSwitchTrackInactive)
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ── System tab ───────────────────────────────────────────────────────────

@Composable
private fun SystemSettings(
    settings: AppSettings,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    onGpsModeChange: (Boolean) -> Unit,
    onRegenerateRasters: (List<RasterCache.Step>) -> Unit,
    scrollState: ScrollState
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        // ── Language ──────────────────────────────────────────────────
        SectionHeader(title = stringResource(R.string.settings_section_language))
        Spacer(modifier = Modifier.height(8.dp))

        SettingsLanguageRow(
            languageCode = settings.languageCode,
            onSelect = { code -> onUpdateSettings { it.copy(languageCode = code) } }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ── Position source (Demo <-> GPS) — moved from Navigation tab ─
        SectionHeader(title = stringResource(R.string.settings_section_position))
        Spacer(modifier = Modifier.height(8.dp))

        // Grouped card: GPS toggle + optional GPS tuning expander
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ComposeColor(AppConfig.uiSettingsCardBackground))
        ) {
            // GPS mode toggle row (inline, no separate card background)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_gps_mode_label),
                        color = ComposeColor.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(R.string.settings_gps_mode_desc),
                        color = ComposeColor(AppConfig.uiSettingsTextMuted),
                        fontSize = 13.sp
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Switch(
                    checked = settings.gpsMode,
                    onCheckedChange = onGpsModeChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ComposeColor(AppConfig.uiSettingsAccent),
                        checkedTrackColor = ComposeColor(AppConfig.uiSettingsAccent).copy(alpha = 0.4f),
                        uncheckedThumbColor = ComposeColor(AppConfig.uiSettingsTextMuted),
                        uncheckedTrackColor = ComposeColor(AppConfig.uiSettingsSwitchTrackInactive)
                    )
                )
            }

            // GPS sub-settings — collapsible, only visible when GPS mode is on
            if (settings.gpsMode) {
                // Thin divider separating the toggle from the expander
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .background(ComposeColor.White.copy(alpha = 0.1f))
                )
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    var gpsTuningExpanded by remember { mutableStateOf(false) }
                    SettingsExpander(
                        label = "GPS tuning",
                        expanded = gpsTuningExpanded,
                        onToggle = { gpsTuningExpanded = !gpsTuningExpanded },
                        labelStyle = TextStyle(
                            color = ComposeColor.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))
                        SettingsFrequencyRow(
                            intervalSec = settings.gpsActiveIntervalSec,
                            onSelect = { ivl, dist ->
                                onUpdateSettings { it.copy(gpsActiveIntervalSec = ivl, gpsActiveMinDistanceM = dist) }
                            }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        SettingsSliderRow(
                            label = stringResource(R.string.settings_recenter_label),
                            description = stringResource(R.string.settings_recenter_desc),
                            valueLabel = stringResource(R.string.settings_value_seconds, settings.recenterDelaySeconds),
                            value = settings.recenterDelaySeconds.toFloat(),
                            valueRange = 1f..10f,
                            steps = 8,
                            onValueChange = { v -> onUpdateSettings { it.copy(recenterDelaySeconds = v.roundToInt()) } }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── Keep screen on ────────────────────────────────────────────
        SectionHeader(title = stringResource(R.string.settings_section_screen))
        Spacer(modifier = Modifier.height(8.dp))

        SettingsToggleRow(
            label = stringResource(R.string.settings_keep_screen_on_label),
            description = stringResource(R.string.settings_keep_screen_on_desc),
            checked = settings.keepScreenOn,
            onCheckedChange = { on -> onUpdateSettings { it.copy(keepScreenOn = on) } }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Map rendering FPS ceiling — moved from Navigation tab
        SettingsSliderRow(
            label = stringResource(R.string.settings_fps_label),
            description = stringResource(R.string.settings_fps_desc),
            valueLabel = stringResource(R.string.settings_value_fps, settings.mapRefreshFps),
            value = settings.mapRefreshFps.toFloat(),
            valueRange = 5f..50f,
            steps = 8,
            onValueChange = { v -> onUpdateSettings { it.copy(mapRefreshFps = (v / 5f).roundToInt() * 5) } }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ── Idle saving — moved from Navigation tab ─────────────────────
        SectionHeader(title = stringResource(R.string.settings_idle_section_label))
        Spacer(modifier = Modifier.height(8.dp))

        // Grouped card: idle interval slider + advanced expander
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ComposeColor(AppConfig.uiSettingsCardBackground))
        ) {
            // Adaptive idle interval slider (inline, no separate card bg)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_idle_interval_label),
                        color = ComposeColor.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(R.string.settings_idle_interval_desc),
                        color = ComposeColor(AppConfig.uiSettingsTextMuted),
                        fontSize = 13.sp
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = stringResource(R.string.settings_value_seconds, settings.adaptiveIdleIntervalSec),
                    color = ComposeColor(AppConfig.uiSettingsAccent),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Slider(
                value = settings.adaptiveIdleIntervalSec.toFloat(),
                onValueChange = { v -> onUpdateSettings { it.copy(adaptiveIdleIntervalSec = v.roundToInt()) } },
                valueRange = 4f..15f,
                steps = 10,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp),
                colors = SliderDefaults.colors(
                    thumbColor = ComposeColor(AppConfig.uiSettingsAccent),
                    activeTrackColor = ComposeColor(AppConfig.uiSettingsAccent),
                    inactiveTrackColor = ComposeColor(AppConfig.uiSettingsSwitchTrackInactive)
                )
            )

            // Thin divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(ComposeColor.White.copy(alpha = 0.1f))
            )

            // Advanced stop-detection thresholds expander
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                var adaptiveAdvanced by remember { mutableStateOf(false) }
                SettingsExpander(
                    label = stringResource(R.string.settings_advanced_stop_label),
                    expanded = adaptiveAdvanced,
                    onToggle = { adaptiveAdvanced = !adaptiveAdvanced }
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsSliderGroup {
                        SliderRowContent(
                            label = stringResource(R.string.settings_window_label),
                            description = stringResource(R.string.settings_window_desc),
                            valueLabel = stringResource(R.string.settings_value_seconds, settings.adaptiveWindowSec),
                            value = settings.adaptiveWindowSec.toFloat(),
                            valueRange = 15f..60f,
                            steps = 8,
                            onValueChange = { v -> onUpdateSettings { it.copy(adaptiveWindowSec = (v / 5f).roundToInt() * 5) } }
                        )
                        SliderRowDivider()
                        SliderRowContent(
                            label = stringResource(R.string.settings_adaptive_dist_label),
                            description = stringResource(R.string.settings_adaptive_dist_desc),
                            valueLabel = stringResource(R.string.settings_value_meters, settings.adaptiveDistanceM),
                            value = settings.adaptiveDistanceM.toFloat(),
                            valueRange = 10f..30f,
                            steps = 3,
                            onValueChange = { v -> onUpdateSettings { it.copy(adaptiveDistanceM = (v / 5f).roundToInt() * 5) } }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── EMODnet shallow filter — moved from General tab ────────────
        SectionHeader(title = stringResource(R.string.settings_emodnet_section_label))
        Spacer(modifier = Modifier.height(8.dp))
        SettingsSliderGroup {
            SliderRowContent(
                label = stringResource(R.string.settings_emodnet_cutoff_label),
                description = stringResource(R.string.settings_emodnet_cutoff_desc),
                valueLabel = stringResource(R.string.settings_value_depth, settings.emodnetShallowCutoffM),
                value = settings.emodnetShallowCutoffM,
                valueRange = 0f..5f,
                steps = 9,
                onValueChange = { v -> onUpdateSettings { it.copy(emodnetShallowCutoffM = (v * 2f).roundToInt() / 2f) } }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── Regenerate Layers ─────────────────────────────────────────
        SectionHeader(title = "Regenerate Layers")
        Spacer(modifier = Modifier.height(4.dp))
        SettingsToggleRow(
            label = "Depth grid",
            description = "Reload the prebaked depth grid from assets",
            checked = settings.regenGrid,
            onCheckedChange = { v -> onUpdateSettings { it.copy(regenGrid = v) } }
        )
        SettingsToggleRow(
            label = "Isobath contours",
            description = "Re-derive contour lines from the grid",
            checked = settings.regenIsobaths,
            onCheckedChange = { v -> onUpdateSettings { it.copy(regenIsobaths = v) } }
        )
        SettingsToggleRow(
            label = "Depth colour map",
            description = "Rebuild the depth-coloured raster overlay",
            checked = settings.regenColour,
            onCheckedChange = { v -> onUpdateSettings { it.copy(regenColour = v) } }
        )
        SettingsToggleRow(
            label = "Low-depth warning overlay",
            description = "Rebuild the shallow-water magenta hazard overlay",
            checked = settings.regenWarning,
            onCheckedChange = { v -> onUpdateSettings { it.copy(regenWarning = v) } }
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Button(
                onClick = {
                    val selected = buildList {
                        if (settings.regenGrid) add(RasterCache.Step.GRID)
                        if (settings.regenIsobaths) add(RasterCache.Step.ISOBATH)
                        if (settings.regenColour) add(RasterCache.Step.DEPTH_COLOUR)
                        if (settings.regenWarning) add(RasterCache.Step.LOW_DEPTH_WARNING)
                    }
                    onRegenerateRasters(selected)
                },
                colors = ButtonDefaults.buttonColors(containerColor = ComposeColor(AppConfig.uiSettingsAccent)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Regenerate", color = ComposeColor.White)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ── Settings sub-components ─────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        color = ComposeColor(AppConfig.uiSettingsAccent),
        fontSize = 17.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp
    )
}

/**
 * Language picker as a 3-segment selector: System / English / Français.
 * The active segment is highlighted in blue. "System" follows the device locale
 * (English default, French on a fr device); the other two force the app language.
 */
@Composable
private fun SettingsLanguageRow(
    languageCode: String,
    onSelect: (String) -> Unit
) {
    // (code, label) — endonyms (English/Français) are shown the same in every locale.
    val options = listOf(
        "system" to stringResource(R.string.settings_language_system),
        "en" to "English",
        "fr" to "Français"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ComposeColor(AppConfig.uiSettingsCardBackground))
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        options.forEach { (code, label) ->
            val selected = code == languageCode
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) ComposeColor(AppConfig.uiSettingsAccent) else ComposeColor(AppConfig.uiSettingsDivider))
                    .clickable { onSelect(code) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (selected) ComposeColor.White else ComposeColor(AppConfig.uiSettingsTextMuted),
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
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
            .background(ComposeColor(AppConfig.uiSettingsCardBackground))
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
                color = ComposeColor(AppConfig.uiSettingsTextMuted),
                fontSize = 13.sp
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = ComposeColor(AppConfig.uiSettingsAccent),
                checkedTrackColor = ComposeColor(AppConfig.uiSettingsAccent).copy(alpha = 0.4f),
                uncheckedThumbColor = ComposeColor(AppConfig.uiSettingsTextMuted),
                uncheckedTrackColor = ComposeColor(AppConfig.uiSettingsSwitchTrackInactive)
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
    SettingsSliderGroup {
        SliderRowContent(label, description, valueLabel, value, valueRange, steps, onValueChange)
    }
}

/** Rounded settings "box" hosting one or more [SliderRowContent]s — groups related sliders together. */
@Composable
private fun SettingsSliderGroup(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ComposeColor(AppConfig.uiSettingsCardBackground))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        content()
    }
}

/** A label + value + slider WITHOUT its own box — placed inside a [SettingsSliderGroup]. */
@Composable
private fun SliderRowContent(
    label: String,
    description: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit
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
                color = ComposeColor(AppConfig.uiSettingsTextMuted),
                fontSize = 13.sp
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = valueLabel,
            color = ComposeColor(AppConfig.uiSettingsAccent),
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
            thumbColor = ComposeColor(AppConfig.uiSettingsAccent),
            activeTrackColor = ComposeColor(AppConfig.uiSettingsAccent),
            inactiveTrackColor = ComposeColor(AppConfig.uiSettingsSwitchTrackInactive)
        )
    )
}

/** Thin divider between sliders that share a [SettingsSliderGroup]. */
@Composable
private fun SliderRowDivider() {
    Spacer(modifier = Modifier.height(10.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(ComposeColor(AppConfig.uiSettingsDivider))
    )
    Spacer(modifier = Modifier.height(2.dp))
}

/** Dimmer sub-heading with an optional one-line description, for grouping settings in a section. */
@Composable
private fun SubSectionHeader(title: String, description: String? = null) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            color = ComposeColor(AppConfig.uiDashboardTextMuted),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
        if (description != null) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                color = ComposeColor(AppConfig.uiSettingsTextSecondary),
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
    labelStyle: TextStyle = TextStyle(
        color = ComposeColor(AppConfig.uiDashboardTextMuted),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold
    ),
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
                style = labelStyle,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) stringResource(R.string.collapse) else stringResource(R.string.expand),
                tint = ComposeColor(AppConfig.uiDashboardTextMuted),
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
        Triple(stringResource(R.string.settings_freq_high), 1, 1f),
        Triple(stringResource(R.string.settings_freq_balanced), 2, 5f),
        Triple(stringResource(R.string.settings_freq_eco), 4, 10f)
    )
    val currentIdx = stops.indexOfFirst { it.second == intervalSec }.let { if (it < 0) 1 else it }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ComposeColor(AppConfig.uiSettingsCardBackground))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_freq_label),
            color = ComposeColor.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = stringResource(R.string.settings_freq_desc),
            color = ComposeColor(AppConfig.uiSettingsTextMuted),
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
                thumbColor = ComposeColor(AppConfig.uiSettingsAccent),
                activeTrackColor = ComposeColor(AppConfig.uiSettingsAccent),
                inactiveTrackColor = ComposeColor(AppConfig.uiSettingsSwitchTrackInactive)
            )
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            stops.forEachIndexed { idx, stop ->
                val selected = idx == currentIdx
                val accent = if (selected) ComposeColor(AppConfig.uiSettingsAccent) else ComposeColor(AppConfig.uiSettingsTextMuted)
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
                        text = stringResource(R.string.settings_freq_stop_fmt, stop.second, stop.third.roundToInt()),
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
            .background(ComposeColor(AppConfig.uiSettingsCardBackground))
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
                focusedBorderColor = ComposeColor(AppConfig.uiSettingsAccent),
                unfocusedBorderColor = ComposeColor(AppConfig.uiSettingsInputBorder),
                cursorColor = ComposeColor.White
            )
        )
    }
}


/**
 * Draws the coastline segments on the OSMdroid [MapView].
 *
 * Mainland: solid blue (#1545C0), 10 px
 * Islands:  green (#08805C), 10 px
 * Hazards:  vivid yellow disc + black outline + outer black ring + black cross — isolated offshore point dangers
 *
 * A segment is treated as a hazard primarily via its explicit [CoastlineSegment.isHazard] flag
 * (set by [HazardRings.toSegment], persisted in the proto cache). As a fallback for pre-feature
 * cached/bundled data that predates the flag, a closed island with no real OSM way id
 * (`!isMainland && isClosed && osmWayId == 0L`) is also treated as a hazard — hazard rings always
 * carry `osmWayId = 0L`, genuine OSM islands have positive ids — so hazards still render correctly
 * without re-baking the bundled asset.
 */
private fun drawCoastline(
    mapView: MapView,
    segments: List<CoastlineSegment>,
    sink: MutableList<Any>
) {
    sink.clear()
    for (segment in segments) {
        val points = segment.points
        if (points.size < 2) continue

        val osmPoints = points.map { GeoPoint(it.lat.toDouble(), it.lon.toDouble()) }

        // Explicit flag first (robust, incl. unnamed dangers); heuristic fallback for
        // pre-feature cached/bundled data that lacks the flag.
        val isHazard = segment.isHazard ||
            (!segment.isMainland && segment.isClosed && segment.osmWayId == 0L)

        if (isHazard) {
            // Isolated offshore danger → vivid filled yellow disc with a black outline,
            // plus a black cross spreading a little past the circle. Distinct from green
            // islands / blue mainland and the magenta low-depth overlay.
            val disc = Polygon().apply {
                setPoints(osmPoints)
                fillPaint.color = AppConfig.mapHazardDiscFill   // vivid yellow (#FFE800) — full circle
                fillPaint.isAntiAlias = true
                outlinePaint.color = AppConfig.mapHazardOutline  // black circle around it
                outlinePaint.strokeWidth = 6f
                outlinePaint.isAntiAlias = true
            }
            mapView.overlays.add(disc)
            sink.add(disc)
            val cLat = (osmPoints.minOf { it.latitude } + osmPoints.maxOf { it.latitude }) / 2.0
            val cLon = (osmPoints.minOf { it.longitude } + osmPoints.maxOf { it.longitude }) / 2.0
            // Outer black ring, concentric with the disc (~1.6× radius).
            val outerRing = Polyline().apply {
                setPoints(osmPoints.map {
                    GeoPoint(cLat + (it.latitude - cLat) * 1.6, cLon + (it.longitude - cLon) * 1.6)
                })
                outlinePaint.apply { color = Color.BLACK; strokeWidth = 5f; isAntiAlias = true }
            }
            mapView.overlays.add(outerRing)
            sink.add(outerRing)
            // Black cross centred on the marker, arms ~80% past the radius (just past the outer ring).
            val hLat = (osmPoints.maxOf { it.latitude } - osmPoints.minOf { it.latitude }) / 2.0 * 1.8
            val hLon = (osmPoints.maxOf { it.longitude } - osmPoints.minOf { it.longitude }) / 2.0 * 1.8
            val crossH = Polyline().apply {
                setPoints(listOf(GeoPoint(cLat, cLon - hLon), GeoPoint(cLat, cLon + hLon)))
                outlinePaint.apply { color = Color.BLACK; strokeWidth = 5f; isAntiAlias = true }
            }
            mapView.overlays.add(crossH)
            sink.add(crossH)
            val crossV = Polyline().apply {
                setPoints(listOf(GeoPoint(cLat - hLat, cLon), GeoPoint(cLat + hLat, cLon)))
                outlinePaint.apply { color = Color.BLACK; strokeWidth = 5f; isAntiAlias = true }
            }
            mapView.overlays.add(crossV)
            sink.add(crossV)
            continue
        }

        val polyline = Polyline().apply {
            setPoints(osmPoints)
            outlinePaint.apply {
                color = if (segment.isMainland) AppConfig.mapCoastlineMainlandColor
                        else AppConfig.mapCoastlineIslandColor
                strokeWidth = AppConfig.mapCoastlineMainlandWidth.toFloat()
                alpha = 128
                isAntiAlias = true
            }
        }
        mapView.overlays.add(polyline)
        sink.add(polyline)
    }
}

/**
 * ISOLATED: Draws a dashed line from the boat position to the zone boundary
 * intersection point, showing the distance path computed by [HeadingAheadResult].
 *
 * Uses a two-layer approach: dark inner dash + white outer dash for high-contrast
 * visibility over both water and land backgrounds. Only draws on zoom ≥ [ZONE_MIN_ZOOM].
 *
 * To remove this feature entirely:
 *   1. Delete this function
 *   2. Remove its calls from the factory and update blocks in [CoastlineMapView]
 *   3. Remove `intersectionLatLng` from [HeadingAheadResult]
 *   4. Remove the `headingAheadResult` param from [MapContent] and [CoastlineMapView]
 *   5. Remove the `headingAheadResult` param from the [MapScreen] → [MapContent] call
 *   6. Delete the `import android.graphics.DashPathEffect`
 */
private fun drawZoneAheadLine(
    mapView: MapView,
    boatPosition: LatLng?,
    stableIntersectionLatLng: LatLng?,
    zoomLevel: Double
) {
    mapView.overlays.removeAll { it is Polyline && (it as Polyline).title == "zoneAheadOuter" }

    val boat = boatPosition ?: return

    val geoBoat = GeoPoint(boat.latitude, boat.longitude)
    geoBoat.altitude = 0.0

    // End point: zone intersection if detected, otherwise a short stub in heading direction
    val endPt = if (stableIntersectionLatLng != null) {
        GeoPoint(stableIntersectionLatLng!!.latitude, stableIntersectionLatLng!!.longitude).also { it.altitude = 0.0 }
    } else {
        // Short stub (50m) in north direction when no zone detected
        val stub = SpatialOperations.pointAlongBearing(boat.latitude, boat.longitude, 0.0, 50.0)
        GeoPoint(stub.latitude, stub.longitude).also { it.altitude = 0.0 }
    }

    mapView.overlays.add(Polyline().apply {
        title = "zoneAheadOuter"
        setPoints(listOf(geoBoat, endPt))
        outlinePaint.apply {
            color = AppConfig.mapZoneAheadLine // green, 86% alpha
            strokeWidth = 12f
            style = android.graphics.Paint.Style.STROKE
            isAntiAlias = true
        }
    })
}

/**
 * ISOLATED: Draws a flashy yellow cone (wedge) on the map showing the heading-ahead
 * search area. The cone radius matches the detected zone distance (or 2000m max if
 * no zone detected), so its size represents the actual search range to the target.
 *
 * Always rendered when heading is known. To remove: delete this function + calls.
 */
private fun drawZoneAheadCone(
    mapView: MapView,
    boatPosition: LatLng?,
    zoneDistanceAheadM: Double?,
    zoomLevel: Double,
    headingDeg: Double = -1.0
) {
    mapView.overlays.removeAll { it is Polygon && (it as Polygon).title == "zoneAheadCone" }

    val boat = boatPosition ?: return
    val heading = if (headingDeg >= 0.0) headingDeg else 0.0

    val coneRadius = zoneDistanceAheadM?.coerceAtMost(2000.0) ?: 2000.0
    val coneHalfAngle = 15.0
    val segments = 10

    val pts = mutableListOf<GeoPoint>()
    pts.add(GeoPoint(boat.latitude, boat.longitude))
    for (i in 0..segments) {
        val angle = heading - coneHalfAngle + (2.0 * coneHalfAngle * i / segments)
        val pt = SpatialOperations.pointAlongBearing(boat.latitude, boat.longitude, angle, coneRadius)
        pts.add(GeoPoint(pt.latitude, pt.longitude))
    }

    mapView.overlays.add(Polygon().apply {
        title = "zoneAheadCone"
        setPoints(pts)
        fillPaint.apply {
            color = AppConfig.mapZoneAheadConeFill
            isAntiAlias = true
        }
        outlinePaint.apply {
            color = AppConfig.mapZoneAheadConeOutline
            strokeWidth = 2f
            isAntiAlias = true
        }
    })
}

/**
 * Draws the precomputed 300 m band: translucent red fill (water only, island land
 * cut out as holes) plus the red seaward boundary line. Zoom-gated — nothing is
 * drawn below [ZONE_MIN_ZOOM] (the band would be sub-pixel) or before the band has
 * been built ([zone] == null).
 *
 * Must be drawn **before** [drawCoastline] so the coastline reads on top of the fill.
 */
private fun drawZone300(
    mapView: MapView,
    zone: Zone300Data?,
    zoomLevel: Double,
    sink: MutableList<Any>
) {
    sink.clear()
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
            fillPaint.color = AppConfig.mapZone300Fill   // ~19% red
            outlinePaint.color = Color.TRANSPARENT
            outlinePaint.strokeWidth = 0f
        }
        mapView.overlays.add(fill)
        sink.add(fill)
    }

    // Red seaward boundary line (above the fill).
    for (line in zone.seawardLines) {
        if (line.size < 2) continue
        val redLine = Polyline().apply {
            setPoints(line.map { GeoPoint(it.latitude, it.longitude) })
            outlinePaint.apply {
                color = AppConfig.mapZone300Boundary
                strokeWidth = 6f
                alpha = 220
                isAntiAlias = true
            }
        }
        mapView.overlays.add(redLine)
        sink.add(redLine)
    }
}

// ── Regulated zones overlay ────────────────────────────────────────────────────

/** Minimum zoom level to draw regulated zone polygons (below this they'd be sub-pixel). */
private const val REGULATED_ZONE_MIN_ZOOM = 10.0

/**
 * Per-type colour configuration for regulated zone overlays.
 *
 * @property fillARGB ARGB colour int for the translucent polygon fill (alpha pre-applied).
 * @property strokeARGB Fully opaque ARGB colour int for the polygon outline.
 */
private data class RegulationZoneColor(val fillARGB: Int, val strokeARGB: Int)

/** Map each [RegulatedZoneType] to a distinct translucent fill + opaque outline colour. */
private fun regulatedZoneColor(type: RegulatedZoneType): RegulationZoneColor = when (type) {
    // Fill uses 0x30 alpha (~19 %, matching zone300 fill opacity) applied via Color.argb.
    // Stroke uses full-opacity ARGB with .toInt() for values > Int.MAX_VALUE (0xFF prefix).
    RegulatedZoneType.SPEED_LIMIT           -> RegulationZoneColor((AppConfig.regulatedZoneTypeSpeedLimit and 0x00FFFFFF) or 0x30000000, AppConfig.regulatedZoneTypeSpeedLimit)  // Blue
    RegulatedZoneType.ANCHORING_PROHIBITED  -> RegulationZoneColor((AppConfig.regulatedZoneTypeAnchoringProhibited and 0x00FFFFFF) or 0x30000000, AppConfig.regulatedZoneTypeAnchoringProhibited)  // Amber
    RegulatedZoneType.ACCESS_PROHIBITED     -> RegulationZoneColor((AppConfig.regulatedZoneTypeAccessProhibited and 0x00FFFFFF) or 0x30000000, AppConfig.regulatedZoneTypeAccessProhibited)  // Red
    RegulatedZoneType.ENVIRONMENTAL         -> RegulationZoneColor((AppConfig.regulatedZoneTypeEnvironmental and 0x00FFFFFF) or 0x30000000, AppConfig.regulatedZoneTypeEnvironmental)  // Green
    RegulatedZoneType.MOORING               -> RegulationZoneColor((AppConfig.regulatedZoneTypeMooring and 0x00FFFFFF) or 0x30000000, AppConfig.regulatedZoneTypeMooring)  // Teal
    RegulatedZoneType.FISHING_PROHIBITED    -> RegulationZoneColor((AppConfig.regulatedZoneTypeFishingProhibited and 0x00FFFFFF) or 0x30000000, AppConfig.regulatedZoneTypeFishingProhibited)  // Yellow
    RegulatedZoneType.NAVIGATION_RESTRICTION -> RegulationZoneColor((AppConfig.regulatedZoneTypeNavigationRestriction and 0x00FFFFFF) or 0x30000000, AppConfig.regulatedZoneTypeNavigationRestriction) // Purple
    RegulatedZoneType.OTHER                 -> RegulationZoneColor((AppConfig.regulatedZoneTypeOther and 0x00FFFFFF) or 0x30000000, AppConfig.regulatedZoneTypeOther)  // Blue Grey
}

/**
 * Draws regulated zones as translucent filled polygons with coloured outlines, one per
 * [RegulatedZone] in the set. Each [RegulatedZoneType] gets a distinct colour (see
 * [regulatedZoneColor]). Polygon holes (island interiors) are supported.
 *
 * Zoom-gated below [REGULATED_ZONE_MIN_ZOOM] and skipped when [zones] is null.
 *
 * Drawn between isobaths and the 300 m band (see [CoastlineMapView] factory / update).
 */
private fun drawRegulatedZones(
    mapView: MapView,
    zones: RegulatedZoneSet?,
    zoomLevel: Double,
    sink: MutableList<Polygon>
) {
    sink.clear()
    if (zones == null || zoomLevel < REGULATED_ZONE_MIN_ZOOM) return
    for (zone in zones.zones) {
        if (zone.outerRing.size < 3) continue

        val colors = regulatedZoneColor(zone.zoneType)
        val fill = Polygon().apply {
            setPoints(zone.outerRing.map { GeoPoint(it.latitude, it.longitude) })
            val validHoles = zone.holes.filter { it.size >= 3 }
            if (validHoles.isNotEmpty()) {
                setHoles(validHoles.map { hole -> hole.map { GeoPoint(it.latitude, it.longitude) } })
            }
            fillPaint.color = colors.fillARGB
            outlinePaint.color = colors.strokeARGB
            outlinePaint.strokeWidth = 3f
            outlinePaint.alpha = 200
            outlinePaint.isAntiAlias = true
        }
        mapView.overlays.add(fill)
        sink.add(fill)
    }
}

// ── Depth overlays ────────────────────────────────────────────────────────────

/** Number of horizontal strips per depth raster overlay — see [addBandedOverlay]. ~8 ⇒ sub-metre. */
private const val DEPTH_OVERLAY_BANDS = 8

/**
 * Adds [bitmap] as [bands] stacked horizontal `GroundOverlay` strips instead of one, each pinned
 * at its own true latitudes. osmdroid stretches every overlay linearly in Web-Mercator, but the
 * grid's rows are equal *latitude* steps — which are NOT equal Mercator steps — so one full-height
 * overlay bows by ~tens of metres mid-grid. Splitting resets that error to zero at every strip
 * edge; it then falls with the square of the strip height (~8 bands ⇒ sub-metre). Longitude is
 * already linear in Mercator, so only latitude is split. Adjacent strips share pixel-row AND
 * latitude boundaries, so they tile exactly — no seam, no gap.
 */
private fun addBandedOverlay(
    mapView: MapView,
    bitmap: Bitmap,
    box: BoundingBox,
    bands: Int,
    sink: MutableList<GroundOverlay>
) {
    val w = bitmap.width
    val h = bitmap.height
    val n = bands.coerceIn(1, h)
    val latSpan = box.latNorth - box.latSouth
    for (i in 0 until n) {
        val y0 = (i.toLong() * h / n).toInt()
        val y1 = ((i + 1).toLong() * h / n).toInt()
        val sliceH = y1 - y0
        if (sliceH <= 0) continue
        // Bitmap row 0 = north; latitude is linear in pixel row, so split proportionally.
        val latNorthStrip = box.latNorth - latSpan * (y0.toDouble() / h)
        val latSouthStrip = box.latNorth - latSpan * (y1.toDouble() / h)
        val overlay = GroundOverlay().apply {
            setImage(Bitmap.createBitmap(bitmap, 0, y0, w, sliceH))
            setPosition(
                GeoPoint(latNorthStrip, box.lonWest),
                GeoPoint(latSouthStrip, box.lonEast)
            )
        }
        mapView.overlays.add(overlay)
        sink.add(overlay)
    }
}

/**
 * Draws the hypsometric depth colour map as a stack of [DEPTH_OVERLAY_BANDS] `GroundOverlay` strips
 * (see [addBandedOverlay] for why it is banded). Zoom-gated below
 * [DepthConstants.DEPTH_MAP_MIN_DRAW_ZOOM] and skipped until the bitmap is built ([bitmap] == null).
 * The bitmap carries per-pixel alpha (NaN cells transparent), so it draws at full overlay opacity.
 *
 * Added FIRST so the isobaths, 300 m band and coastline read on top.
 */
private fun drawDepthMap(
    mapView: MapView,
    bitmap: Bitmap?,
    box: BoundingBox?,
    zoomLevel: Double,
    sink: MutableList<GroundOverlay>
) {
    sink.clear()
    if (bitmap == null || box == null || zoomLevel < DepthConstants.DEPTH_MAP_MIN_DRAW_ZOOM) return
    addBandedOverlay(mapView, bitmap, box, DEPTH_OVERLAY_BANDS, sink)
}

/**
 * Draws the low-depth warning raster as banded `GroundOverlay` strips (see [addBandedOverlay])
 * directly above the depth colour map but below the isobaths, 300 m band and coastline. Same zoom
 * gate as [drawDepthMap]; the bitmap is transparent except the shallow cells, so it only tints
 * genuine grounding hazards.
 */
private fun drawLowDepthWarning(
    mapView: MapView,
    bitmap: Bitmap?,
    box: BoundingBox?,
    zoomLevel: Double,
    sink: MutableList<GroundOverlay>
) {
    sink.clear()
    if (bitmap == null || box == null || zoomLevel < DepthConstants.DEPTH_MAP_MIN_DRAW_ZOOM) return
    addBandedOverlay(mapView, bitmap, box, DEPTH_OVERLAY_BANDS, sink)
}

/**
 * Draws depth contour [isobaths] as polylines, above the colour map but below the 300 m band
 * and coastline. Zoom-gated: nothing below [DepthConstants.ISOBATH_MIN_DRAW_ZOOM]; the dense
 * 2 m contour appears only at [DepthConstants.SHALLOW_ISOBATH_MIN_ZOOM]+. "Round" contours
 * (10/20/30…m) read slightly bolder than the in-between lines.
 */
private fun drawIsobaths(
    mapView: MapView,
    isobaths: List<Isobath>,
    zoomLevel: Double,
    sink: MutableList<Polyline>
) {
    sink.clear()
    if (zoomLevel < DepthConstants.ISOBATH_MIN_DRAW_ZOOM) return
    for (iso in isobaths) {
        if (iso.depthM <= 2f && zoomLevel < DepthConstants.SHALLOW_ISOBATH_MIN_ZOOM) continue
        val isMajor = iso.depthM.toInt() % 10 == 0
        for (line in iso.lines) {
            if (line.points.size < 2) continue
            val poly = Polyline().apply {
                setPoints(line.points.map { GeoPoint(it.latitude, it.longitude) })
                outlinePaint.apply {
                    color = AppConfig.isobarColor(line.source)   // colour by data source
                    strokeWidth = ((if (isMajor) 3f else 2f) + AppConfig.isobarWidthBonus(line.source)).coerceAtLeast(1f)
                    alpha = if (isMajor) 180 else 120
                    isAntiAlias = true
                    // Dash genuinely low-confidence fill (GEBCO/interpolated) → reads as "approximate".
                    pathEffect = if (line.confidence <= DepthConstants.ISOBATH_LOWCONF_DASH_MAX)
                        android.graphics.DashPathEffect(floatArrayOf(8f, 6f), 0f) else null
                }
            }
            mapView.overlays.add(poly)
            sink.add(poly)
        }
    }
}

// ── Regulated zone warning strip (vertical stack) ───────────────────────────

/**
 * Priority for vertical stack ordering — most restrictive (lowest index)
 * placed at the bottom, informational (highest index) at the top.
 */
private val CATEGORY_PRIORITY: Map<ZoneDisplayCategory, Int> = mapOf(
    ZoneDisplayCategory.SPEED_LIMIT to 0,
    ZoneDisplayCategory.NO_ACCESS to 1,
    ZoneDisplayCategory.NO_ANCHOR to 2,
    ZoneDisplayCategory.NO_DIVING to 3,
    ZoneDisplayCategory.FISHING_PROHIBITED to 4,
    ZoneDisplayCategory.MOORING to 5,
    ZoneDisplayCategory.SEAPLANE to 6,
    ZoneDisplayCategory.ENVIRONMENTAL to 7,
    ZoneDisplayCategory.INFORMATION to 8,
)

/**
 * Bottom-left warning strip showing icons as a vertical stack.
 *
 * Icons are 44×44 dp, ordered from most restrictive (SPEED_LIMIT at the
 * bottom) to informational (INFORMATION at the top). Deduplicates by
 * (displayCategory, speedLimitKn).
 *
 * When [inZone300] is true, the 300m zone is injected as a SPEED_LIMIT entry
 * at the highest priority (bottom of stack), and regulated SPEED_LIMIT icons
 * are suppressed to avoid duplicating speed limit info.
 */
@Composable
private fun RegulatedZoneWarningStrip(
    regulatedZones: RegulatedZoneSet?,
    boatPosition: LatLng? = null,
    inZone300: Boolean = false,
    modifier: Modifier = Modifier
) {
    val categories = remember(regulatedZones, boatPosition, inZone300) {
        val base = if (regulatedZones != null && regulatedZones.zones.isNotEmpty()) {
            val zones = if (boatPosition != null) {
                regulatedZones.zones.filter { it.contains(boatPosition) }
            } else {
                regulatedZones.zones
            }
            if (zones.isEmpty()) {
                emptyList()
            } else {
                zones
                    .flatMap { zone ->
                        val speed = zone.speedLimitKn
                            ?: parseSpeedFromDescription(zone.description)
                        zone.displayCategories().map { cat -> cat to speed }
                    }
                    .filter { (cat, speed) -> cat != ZoneDisplayCategory.SPEED_LIMIT || speed != null }
                    // When in 300m zone, suppress regulated speed limit icons (300m replaces them)
                    .filter { (cat, _) -> !(inZone300 && cat == ZoneDisplayCategory.SPEED_LIMIT) }
                    .distinct()
            }
        } else {
            emptyList()
        }

        // When in the 300m zone, inject it as the highest-priority SPEED_LIMIT entry
        val withZone300 = if (inZone300) {
            val zoneSpeed = AppConfig.zoneRegulatorySpeedKn.toDouble()
            base + (ZoneDisplayCategory.SPEED_LIMIT to zoneSpeed)
        } else {
            base
        }

        if (withZone300.isEmpty()) return@remember emptyList()

        // Sort by priority — most restrictive first (bottom of stack)
        withZone300.sortedBy { (cat, _) -> CATEGORY_PRIORITY[cat] ?: Int.MAX_VALUE }
    }

    if (categories.isEmpty()) return

    // Vertical column: first item at bottom (most restrictive), last at top
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // Render in reverse so the first sorted item (most restrictive)
        // appears at the bottom of the stack
        categories.reversed().forEach { (category, speedKn) ->
            RegulationZoneCategoryIcon(category = category, speedKn = speedKn)
        }
    }
}

/**
 * A single 44×44 dp icon for a [ZoneDisplayCategory], displaying the category's
 * emoji (or speed number) on a coloured rounded-square background, with a thin
 * Canvas-drawn red diagonal strike overlay for prohibition categories.
 *
 * Speed limit zones render the knot value as bold white text instead of an emoji
 * (e.g. "5" or "10") so the user can distinguish different speed limits at a glance.
 *
 * Background alpha is sourced from [RegulatedZoneIconProvider.alphaForCategory]:
 * prohibition/warning icons use [AppConfig.iconBackActiveAlpha] (75 %),
 * informational icons use [AppConfig.iconBackInactiveAlpha] (50 %).
 *
 * Categories requiring a strike (NO_ANCHOR, NO_DIVING, NO_ACCESS) render the emoji
 * Text first, then overlay a thin red diagonal line via Canvas on top.
 */
@Composable
private fun RegulationZoneCategoryIcon(
    category: ZoneDisplayCategory,
    speedKn: Double? = null,
    modifier: Modifier = Modifier
) {
    val bgColor = RegulatedZoneIconProvider.colorForCategory(category)
    val alpha = RegulatedZoneIconProvider.alphaForCategory(category)
    val hasStrike = category == ZoneDisplayCategory.NO_ANCHOR ||
            category == ZoneDisplayCategory.NO_DIVING ||
            category == ZoneDisplayCategory.NO_ACCESS ||
            category == ZoneDisplayCategory.FISHING_PROHIBITED

    Box(
        modifier = modifier
            .size(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor.copy(alpha = alpha)),
        contentAlignment = Alignment.Center
    ) {
        if (category == ZoneDisplayCategory.SPEED_LIMIT) {
            Text(
                text = if (speedKn != null) "${speedKn.toInt()}" else "",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = ComposeColor.White
            )
        } else {
            // Emoji Text for all non-speed categories
            Text(
                text = RegulatedZoneIconProvider.emojiForCategory(category),
                fontSize = 24.sp
            )
        }

        // Thin red diagonal strike overlay for prohibition categories
        if (hasStrike) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val sw = size.width * 0.04f // thinner strike
                drawLine(
                    color = ComposeColor.Red,
                    start = Offset(size.width * 0.08f, size.height * 0.08f),
                    end = Offset(size.width * 0.92f, size.height * 0.92f),
                    strokeWidth = sw
                )
            }
        }
    }
}


/**
 * Zone info text panel — shows zone info text beside the vertical icon stack.
 *
 * Builds the same deduplicated category list as [RegulatedZoneWarningStrip] so
 * text lines match the icon stack exactly — same emoji, same priority order.
 *
 * Format per line: {category_emoji} {zone.name or fallback} — {speed or desc}
 * Ordered by [CATEGORY_PRIORITY] (most restrictive at bottom, matching icons).
 * Auto-wraps within the available space.
 */
@Composable
private fun RegulatedZoneInfoText(
    regulatedZones: RegulatedZoneSet?,
    boatPosition: LatLng? = null,
    inZone300: Boolean = false,
    modifier: Modifier = Modifier
) {
    // Derive the same deduplicated (category, speedKn) pairs as the warning strip
    val categoryLines = remember(regulatedZones, boatPosition, inZone300) {
        val base = if (regulatedZones != null && regulatedZones.zones.isNotEmpty()) {
            val zones = if (boatPosition != null) {
                regulatedZones.zones.filter { it.contains(boatPosition) }
            } else {
                regulatedZones.zones
            }
            if (zones.isEmpty()) {
                emptyList()
            } else {
                zones
                    .flatMap { zone ->
                        val speed = zone.speedLimitKn
                            ?: parseSpeedFromDescription(zone.description)
                        // Pair each display category with the zone it came from
                        zone.displayCategories().map { cat -> Triple(cat, speed, zone) }
                    }
                    .filter { (cat, speed, _) -> cat != ZoneDisplayCategory.SPEED_LIMIT || speed != null }
                    // When in 300m zone, suppress regulated speed limit info text (300m replaces it)
                    .filter { (cat, _, _) -> !(inZone300 && cat == ZoneDisplayCategory.SPEED_LIMIT) }
                    .distinctBy { (cat, speed, _) -> cat to speed }
            }
        } else {
            emptyList()
        }

        // When in the 300m zone, inject it as the highest-priority SPEED_LIMIT info line
        val withZone300 = if (inZone300) {
            val zoneSpeed = AppConfig.zoneRegulatorySpeedKn.toDouble()
            base + Triple(ZoneDisplayCategory.SPEED_LIMIT, zoneSpeed, null)
        } else {
            base
        }

        if (withZone300.isEmpty()) return@remember emptyList()

        withZone300.sortedBy { (cat, _, _) -> CATEGORY_PRIORITY[cat] ?: Int.MAX_VALUE }
    }

    if (categoryLines.isEmpty()) return

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Bottom
    ) {
        // Render in reverse so most restrictive (first in sorted order) is at bottom
        categoryLines.reversed().forEach { (category, speedKn, zone) ->
            val emoji = if (category == ZoneDisplayCategory.SPEED_LIMIT) {
                "\uD83D\uDD34"  // 🔴 red dot for speed info
            } else {
                RegulatedZoneIconProvider.emojiForCategory(category)
            }
            // 300m zone synthetic entry (zone == null) uses hardcoded name
            val name = if (zone != null) {
                val rawName = zone.name
                if (rawName.isNullOrBlank() || rawName.equals("null", ignoreCase = true)) {
                    zone.zoneType.name.lowercase().replace('_', ' ')
                } else {
                    rawName
                }
            } else {
                "300 m Zone"
            }
            val keyInfo = when {
                speedKn != null -> "${speedKn.toInt()} nds"
                zone != null && zone.description.isNotBlank() -> zone.description.replace("\n", " ")
                else -> ""
            }
            Text(
                text = if (keyInfo.isNotBlank()) "$emoji $name — $keyInfo" else "$emoji $name",
                fontSize = 9.sp,
                lineHeight = 14.sp,
                color = ComposeColor.White,
            )
        }
    }
}

/**
 * Category icon visibility toggles for the regulated zone warning strip.
 * Each icon type can be individually hidden.
 */
@Composable
private fun RegulatedZoneCategoryToggles(
    settings: AppSettings,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ComposeColor(AppConfig.uiSettingsCardBackground))
    ) {
        categoryToggleItems.forEachIndexed { index, item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (item.iconIsRedBox) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(ComposeColor(AppConfig.uiSettingsDanger)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("10", color = ComposeColor.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Text(item.emoji, fontSize = 18.sp)
                        }
                        // Red diagonal strike overlay for prohibition categories
                        if (item.hasStrike) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val sw = size.width * 0.06f
                                drawLine(
                                    color = ComposeColor.Red,
                                    start = Offset(size.width * 0.1f, size.height * 0.1f),
                                    end = Offset(size.width * 0.9f, size.height * 0.9f),
                                    strokeWidth = sw
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = item.label, color = ComposeColor.White, fontSize = 14.sp)
                }
                Switch(
                    checked = item.isVisible(settings),
                    onCheckedChange = { visible ->
                        onUpdateSettings { item.setter(it, visible) }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ComposeColor(AppConfig.uiSettingsAccent),
                        checkedTrackColor = ComposeColor(AppConfig.uiSettingsAccent).copy(alpha = 0.4f)
                    )
                )
            }
            if (index < categoryToggleItems.size - 1) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .padding(horizontal = 16.dp)
                        .background(ComposeColor(AppConfig.uiSettingsCardBackground))
                )
            }
        }
    }
}

/**
 * Boat size slider (3–25m) for filtering regulated zones by vessel length.
 * Shown in its own collapsible expander within the regulated zones card.
 */
@Composable
private fun BoatSizeSlider(
    settings: AppSettings,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ComposeColor(AppConfig.uiSettingsCardBackground))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "\uD83D\uDEA4 Boat length",
                color = ComposeColor.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${settings.boatSizeM.toInt()} m",
                color = ComposeColor(AppConfig.uiSettingsAccent),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Slider(
            value = settings.boatSizeM.toFloat(),
            onValueChange = { v ->
                onUpdateSettings { it.copy(boatSizeM = v.toDouble().coerceIn(3.0, 25.0)) }
            },
            valueRange = 3f..25f,
            steps = 21,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = ComposeColor(AppConfig.uiSettingsAccent),
                activeTrackColor = ComposeColor(AppConfig.uiSettingsAccent),
                inactiveTrackColor = ComposeColor(AppConfig.uiSettingsAccent).copy(alpha = 0.3f)
            )
        )
    }
}

private data class CategoryToggleItem(
    val emoji: String,
    val label: String,
    val iconIsRedBox: Boolean = false,
    val hasStrike: Boolean = false,
    val isVisible: (AppSettings) -> Boolean,
    val setter: (AppSettings, Boolean) -> AppSettings,
)

private val categoryToggleItems = listOf(
    CategoryToggleItem("", "Speed limit", iconIsRedBox = true,
        isVisible = { it.showCategorySpeedLimit },
        setter = { s, v -> s.copy(showCategorySpeedLimit = v) }),
    CategoryToggleItem("\uD83D\uDEA4", "No access",
        hasStrike = true,
        isVisible = { it.showCategoryNoAccess },
        setter = { s, v -> s.copy(showCategoryNoAccess = v) }),
    CategoryToggleItem("\u2693\uFE0F", "No anchor",
        hasStrike = true,
        isVisible = { it.showCategoryNoAnchor },
        setter = { s, v -> s.copy(showCategoryNoAnchor = v) }),
    CategoryToggleItem("\uD83E\uDD3F", "No diving",
        hasStrike = true,
        isVisible = { it.showCategoryNoDiving },
        setter = { s, v -> s.copy(showCategoryNoDiving = v) }),
    CategoryToggleItem("\uD83D\uDC1F", "Fishing prohibited",
        hasStrike = true,
        isVisible = { it.showCategoryFishingProhibited },
        setter = { s, v -> s.copy(showCategoryFishingProhibited = v) }),
    CategoryToggleItem("\uD83D\uDEA4", "Mooring",
        isVisible = { it.showCategoryMooring },
        setter = { s, v -> s.copy(showCategoryMooring = v) }),
    CategoryToggleItem("\u2708\uFE0F", "Seaplane",
        isVisible = { it.showCategorySeaplane },
        setter = { s, v -> s.copy(showCategorySeaplane = v) }),
    CategoryToggleItem("\uD83C\uDF3F", "Environmental",
        isVisible = { it.showCategoryEnvironmental },
        setter = { s, v -> s.copy(showCategoryEnvironmental = v) }),
    CategoryToggleItem("\u2139\uFE0F", "Information",
        isVisible = { it.showCategoryInformation },
        setter = { s, v -> s.copy(showCategoryInformation = v) }),
)

/**
 * Extract a speed limit (knots) from a zone description string as fallback
 * when [RegulatedZone.speedLimitKn] is null. Handles "speed is limited to
 * 10 knots", "speed limit of 5 knots", "3 kn", "10 noeuds", etc.
 */
private fun parseSpeedFromDescription(desc: String?): Double? {
    if (desc == null) return null
    // Handle "5 knots", "10 kn", "3 noeuds" — plural 's' is optional so
    // "10 knots" doesn't get blocked by the \b word boundary after "knot".
    return Regex("""(\d+[.]?\d*)\s*(?:knots?|noeuds?|nds|kn)\b""", RegexOption.IGNORE_CASE)
        .find(desc.lowercase())
        ?.groupValues?.get(1)?.toDoubleOrNull()
}

/**
 * Filter regulated zones by boat size and per-category visibility.
 *
 * Pipeline:
 * 1. Remove zones that don't apply to the configured [boatSizeM] (e.g. "≥ 24m" with a 6m boat)
 * 2. Remove zones whose display categories are all toggled off in settings
 * 3. Return null if no zones remain (layer auto-hides)
 */
private fun filterRegulatedZones(
    zones: RegulatedZoneSet?,
    boatSizeM: Double,
    isCategoryVisible: (ZoneDisplayCategory) -> Boolean
): RegulatedZoneSet? {
    if (zones == null) return null
    val filtered = zones.zones.filter { zone ->
        if (!zone.appliesTo(boatSizeM)) return@filter false
        val cats = zone.displayCategories()
        if (cats.isEmpty()) return@filter false
        cats.any { isCategoryVisible(it) }
    }
    if (filtered.isEmpty()) return null
    return zones.copy(zones = filtered)
}
