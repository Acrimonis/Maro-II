
package ykws.android.maro.ui.map
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.track.toGpx

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
import androidx.compose.material3.RangeSlider
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.border
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
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
import ykws.android.maro.data.regulation.RegulatedZonesRepository
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
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
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
    val zone300Overlay by viewModel.zone300OverlayVisible.collectAsState()
    val regulatedZoneOverlay by viewModel.regulatedZoneOverlayVisible.collectAsState()
    val appSettings by viewModel.settings.collectAsState()
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var expandedFanId by remember { mutableStateOf<ControlId?>(null) }
    var showTrackDrawer by remember { mutableStateOf(false) }
    var showTrackHistory by remember { mutableStateOf(false) }
    val trackViewModel: ykws.android.maro.data.track.TrackViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel()
    val trackRecorderState by trackViewModel.uiState.collectAsState()
    val trackSummaries by trackViewModel.summaries.collectAsState()
    val recoveryTrack by trackViewModel.recoveryTrack.collectAsState()
    val trackScope = rememberCoroutineScope()
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

    // GPS toggle color matches the GPS status icon color for consistency.
    val gpsToggleColor = remember(gpsIconState) {
        val raw = when (gpsIconState) {
            GpsIconState.DEMO -> AppConfig.statusGpsDemo
            GpsIconState.ACQUIRING -> AppConfig.statusGpsAcquiring
            GpsIconState.HEALTHY -> AppConfig.statusGpsHealthy
            GpsIconState.IDLE -> AppConfig.statusGpsIdle
            GpsIconState.STALE -> AppConfig.statusGpsStale
        }
        ComposeColor(raw)
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

    // ── Virtual GpsFix flow: feed TrackRecorder from existing ViewModel state ──
    // In demo mode, a 1 Hz ticker keeps the AdaptiveGpsPolicy timer advancing even
    // when the map is stationary (no pan events → stall without ticker).
    // In GPS mode, the real location listener provides periodic fixes — no ticker needed.
    // NOTE: LaunchedEffect(Unit) — NOT keyed on gpsMode. Toggling the position source
    // must NOT tear down / restart the recorder (would kill active track recording).
    // The ticker adapts dynamically via flatMapLatest; the combine lambda reads
    // appSettings.gpsMode at each emission for position/speed/bearing selection.
    LaunchedEffect(Unit) {
        val ticker = snapshotFlow { appSettings.gpsMode }
            .flatMapLatest { isGps ->
                if (!isGps) {
                    kotlinx.coroutines.flow.flow {
                        while (true) {
                            emit(System.currentTimeMillis())
                            kotlinx.coroutines.delay(1_000L)
                        }
                    }
                } else {
                    kotlinx.coroutines.flow.flowOf(0L)
                }
            }
        val gpsFlow = kotlinx.coroutines.flow.combine(
            viewModel.gpsPosition,
            viewModel.mapCenter,
            viewModel.navigationState,
            ticker
        ) { gpsPos, center, nav, _ ->
            val isGps = appSettings.gpsMode
            val pos = gpsPos ?: center
            val speedKn = if (isGps) nav.speedKnots else nav.demoSpeedKnots
            val speedMs = speedKn?.let { it * 0.514444f }
            val bearing = if (isGps) nav.bearingDeg else nav.demoBearingDeg
            android.util.Log.d("MaroII_Track",
                "GPSflow: gpsPos=${gpsPos != null} demoSpeed=${nav.demoSpeedKnots} speedKn=$speedKn speedMs=$speedMs")
            ykws.android.maro.data.location.GpsFix(
                position = pos,
                bearingDeg = bearing,
                hasCourse = speedMs != null && speedMs > 0.5f,
                speedMps = speedMs,
                hasLock = true,
                timestampEpochMs = System.currentTimeMillis()
            )
        }
        trackViewModel.startRecorder(gpsFlow, appSettings)
    }

    // ── Track overlay: incremental diff for history tracks with fading transparency ──
    // Track the set of currently-rendered track IDs to avoid full teardown+rebuild.
    val renderedTrackIds = remember { mutableStateOf(setOf<String>()) }

    LaunchedEffect(mapView, appSettings.tracksVisible, appSettings.trackingRenderNb, appSettings.trackingColorPastFrom, appSettings.trackingColorPastTo, appSettings.trackingTransparencyFrom, appSettings.trackingTransparencyTo, trackSummaries) {
        val mv = mapView ?: return@LaunchedEffect

        // Determine desired track ID set
        val desiredIds = if (appSettings.tracksVisible) {
            val nbToRender = appSettings.trackingRenderNb.coerceIn(0, 20)
            if (nbToRender > 0) {
                trackSummaries
                    .filter { it.visibleOnMap }
                    .sortedByDescending { it.startTimeMs }
                    .take(nbToRender)
                    .map { it.id }
                    .toSet()
            } else emptySet()
        } else emptySet()

        val currentIds = renderedTrackIds.value
        if (currentIds == desiredIds) return@LaunchedEffect // nothing changed

        // Remove stale overlays
        val toRemove = mv.overlays.filter { overlay ->
            (overlay as? org.osmdroid.views.overlay.Polyline)?.title?.startsWith("track_hist_") == true
        }.filter { overlay ->
            val id = (overlay as org.osmdroid.views.overlay.Polyline).title?.removePrefix("track_hist_") ?: ""
            id !in desiredIds
        }
        mv.overlays.removeAll(toRemove)

        // Determine which new overlays to add
        val existingIds = mv.overlays.filterIsInstance<org.osmdroid.views.overlay.Polyline>()
            .mapNotNull { it.title?.removePrefix("track_hist_") }
            .toSet()

        val sortedDesired = if (appSettings.tracksVisible) {
            val nbToRender = appSettings.trackingRenderNb.coerceIn(0, 20)
            if (nbToRender > 0) {
                trackSummaries
                    .filter { it.visibleOnMap }
                    .sortedByDescending { it.startTimeMs }
                    .take(nbToRender)
            } else emptyList()
        } else emptyList()

        val total = sortedDesired.size

        for ((index, summary) in sortedDesired.withIndex()) {
            if (summary.id in existingIds) continue // already rendered

            val track = trackViewModel.loadTrackDetailCached(summary.id) ?: continue
            if (track.trackPoints.isEmpty()) continue

            val alphaMin = appSettings.trackingTransparencyFrom / 100f
            val alphaMax = appSettings.trackingTransparencyTo / 100f
            val t = if (total <= 1) 0f else index.toFloat() / (total - 1).toFloat()
            val alphaFraction = alphaMax - t * (alphaMax - alphaMin)
            val alphaInt = (alphaFraction * 255).toInt().coerceIn(0, 255)

            // Color interpolation: pastFrom (newest) → pastTo (oldest)
            val startColor = appSettings.trackingColorPastFrom
            val endColor = appSettings.trackingColorPastTo
            val r = ((startColor shr 16 and 0xFF) * (1f - t) + (endColor shr 16 and 0xFF) * t).toInt().coerceIn(0, 255)
            val g = ((startColor shr 8 and 0xFF) * (1f - t) + (endColor shr 8 and 0xFF) * t).toInt().coerceIn(0, 255)
            val b = ((startColor and 0xFF) * (1f - t) + (endColor and 0xFF) * t).toInt().coerceIn(0, 255)
            val colorWithAlpha = (alphaInt shl 24) or (r shl 16) or (g shl 8) or b
            val strokeWidth = if (index == 0) 8f else 6f

            val polyline = org.osmdroid.views.overlay.Polyline().apply {
                title = "track_hist_${summary.id}"
                outlinePaint.color = colorWithAlpha
                outlinePaint.strokeWidth = strokeWidth
                setPoints(track.trackPoints.map { pt ->
                    org.osmdroid.util.GeoPoint(pt.lat, pt.lon)
                })
            }
            mv.overlays.add(polyline)
        }
        renderedTrackIds.value = desiredIds
        mv.invalidate()
    }

    // ── Active recording trace: real-time Polyline during RECORDING ─────────
    LaunchedEffect(mapView, appSettings.trackingColorActive) {
        val mv = mapView ?: return@LaunchedEffect
        androidx.compose.runtime.snapshotFlow { trackRecorderState }
            .collect { state ->
                val recState = state.state
                val points = state.recordingPoints

                if (recState == ykws.android.maro.data.track.TrackRecorderState.ON && points.isNotEmpty()) {
                    val existing = mv.overlays.firstOrNull {
                        (it as? org.osmdroid.views.overlay.Polyline)?.title == "track_recording"
                    } as? org.osmdroid.views.overlay.Polyline
                    if (existing != null) {
                        existing.setPoints(points.map { org.osmdroid.util.GeoPoint(it.lat, it.lon) })
                    } else {
                        val polyline = org.osmdroid.views.overlay.Polyline().apply {
                            title = "track_recording"
                            outlinePaint.color = appSettings.trackingColorActive
                            outlinePaint.strokeWidth = 10f
                            isVisible = true
                            setPoints(points.map { org.osmdroid.util.GeoPoint(it.lat, it.lon) })
                        }
                        mv.overlays.add(polyline)
                    }
                    mv.invalidate()
                } else {
                    val removed = mv.overlays.removeAll {
                        (it as? org.osmdroid.views.overlay.Polyline)?.title == "track_recording"
                    }
                    if (removed) mv.invalidate()
                }
            }
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

        // ── Intercept system back when track history is open ──────────────
        if (showTrackHistory) {
            BackHandler { showTrackHistory = false }
        }

        // ── Otherwise require a second back press within 2 s to exit ───────
        BackHandler(enabled = !showSettings && !showTrackHistory && !anyFanExpanded) {
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
                zone300OverlayVisible = zone300Overlay,
                regulatedZoneOverlayVisible = regulatedZoneOverlay,
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
                onOpenTrackDrawer = { showTrackDrawer = !showTrackDrawer },
                showTrackDrawer = showTrackDrawer,
                showTrackHistory = showTrackHistory,
                trackRecorderState = trackRecorderState,
                trackSummaries = trackSummaries,
                recoveryTrack = recoveryTrack,
                onStartRecording = { trackViewModel.startRecording() },
                onStopRecording = { trackViewModel.stopRecording() },
                onViewTrackList = { showTrackHistory = true },
                onDismissTrackHistory = { showTrackHistory = false },
                onUpdateTrack = { id, name, comment, visible ->
                    trackViewModel.updateTrack(id, name, comment, visible)
                },
                onDeleteTrack = { id -> trackViewModel.deleteTrack(id) },
                onShareGpx = { id ->
                    trackScope.launch {
                        val track = trackViewModel.loadTrackDetail(id)
                        if (track != null) {
                            val gpx = track.toGpx()
                            val file = java.io.File(context.cacheDir, "${id}.gpx")
                            file.writeText(gpx)
                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file
                            )
                            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "application/gpx+xml"
                                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(
                                android.content.Intent.createChooser(intent, "Share GPX")
                            )
                        }
                    }
                },
                onDiscardRecovery = { recoveryTrack?.let { trackViewModel.discardOrphanedCheckpoint(it) } },
                onSaveRecovery = { recoveryTrack?.let { trackViewModel.saveOrphanedCheckpoint(it) } },
                onToggleLowDepthWarning = viewModel::toggleLowDepthWarningVisibility,
                onToggleDepthLayer = viewModel::toggleDepthLayerVisibility,
                onToggleRegulatedZones = viewModel::toggleRegulatedZonesVisibility,
                onToggleZone300 = viewModel::toggleZone300Visibility,
                onToggleTracks = viewModel::toggleTracksVisibility,
                isLandscape = isLandscape,
                expandedFanId = expandedFanId,
                onToggleFan = { id -> expandedFanId = if (expandedFanId == id) null else id },
                onDismissFan = { expandedFanId = null },
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

        // ── TrackDrawer overlay (layer on top, like SettingsOverlay) ─────
        if (showTrackDrawer) {
            TrackDrawerOverlay(
                isOpen = true,
                gpsMode = appSettings.gpsMode,
                onGpsModeChange = onGpsModeChange,
                gpsToggleColor = gpsToggleColor,
                recorderState = trackRecorderState,
                onStartRecording = { trackViewModel.startRecording() },
                onStopRecording = { trackViewModel.stopRecording() },
                onViewTrackList = { showTrackDrawer = false; showTrackHistory = true },
                onDismiss = { showTrackDrawer = false },
                onOpenSettings = { showTrackDrawer = false; showSettings = true }
            )
        }

        // ── TrackHistory overlay ──────────────────────────────────────────
        if (showTrackHistory) {
            TrackHistoryOverlay(
                trackSummaries = trackSummaries,
                liveTrackState = trackRecorderState,
                onUpdateTrack = { id, name, comment, visible ->
                    trackViewModel.updateTrack(id, name, comment, visible)
                },
                onUpdateLiveTrack = { name, comment ->
                    trackViewModel.updateLiveTrackMeta(name, comment)
                },
                onDeleteTrack = { id -> trackViewModel.deleteTrack(id) },
                onUndoDeleteTrack = { /* no-op: track was never actually removed from repo,
                                          only visually hidden until Snackbar timeout */ },
                onShareGpx = { id -> shareTrackGpx(context, trackViewModel, id, trackScope) },
                onDismiss = { showTrackHistory = false }
            )
        }

        // ── Process-death recovery dialog ─────────────────────────────
        recoveryTrack?.let { track ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { trackViewModel.discardOrphanedCheckpoint(track) },
                title = { androidx.compose.material3.Text("Unfinished Recording") },
                text = { androidx.compose.material3.Text(
                    "Found an unfinished recording from ${track.name}."
                ) },
                confirmButton = {
                    androidx.compose.material3.TextButton(
                        onClick = { trackViewModel.saveOrphanedCheckpoint(track) }
                    ) { androidx.compose.material3.Text("Save") }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(
                        onClick = { trackViewModel.discardOrphanedCheckpoint(track) }
                    ) { androidx.compose.material3.Text("Discard") }
                }
            )
        }
    }
}

/**
 * Share a track as a GPX file via Android's share intent.
 */
private fun shareTrackGpx(
    context: android.content.Context,
    trackViewModel: ykws.android.maro.data.track.TrackViewModel,
    trackId: String,
    scope: kotlinx.coroutines.CoroutineScope
) {
    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
        val track = trackViewModel.loadTrackDetail(trackId) ?: return@launch
        val gpx = track.toGpx()
        val gpxFile = java.io.File(context.filesDir, "tracks/${trackId}.gpx")
        gpxFile.parentFile?.mkdirs()
        gpxFile.writeText(gpx)
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            gpxFile
        )
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "application/gpx+xml"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(intent, "Share GPX"))
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
private enum class ControlId { SETTINGS, LAYER_FAN, ZOOM, MENU }

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
    onOpenTrackDrawer: () -> Unit = {},
    showTrackDrawer: Boolean = false,
    showTrackHistory: Boolean = false,
    trackRecorderState: ykws.android.maro.data.track.TrackRecorderUiState = ykws.android.maro.data.track.TrackRecorderUiState(),
    trackSummaries: List<ykws.android.maro.data.track.TrackSummary> = emptyList(),
    recoveryTrack: ykws.android.maro.data.track.Track? = null,
    onStartRecording: () -> Unit = {},
    onStopRecording: () -> Unit = {},
    onViewTrackList: () -> Unit = {},
    onDismissTrackHistory: () -> Unit = {},
    onUpdateTrack: (String, String?, String?, Boolean?) -> Unit = { _, _, _, _ -> },
    onDeleteTrack: (String) -> Unit = {},
    onShareGpx: (String) -> Unit = {},
    onDiscardRecovery: () -> Unit = {},
    onSaveRecovery: () -> Unit = {},
    onToggleZone300: () -> Unit,
    zone300OverlayVisible: Boolean = false,
    regulatedZoneOverlayVisible: Boolean = false,
    onToggleRegulatedZones: () -> Unit,
    onToggleLowDepthWarning: () -> Unit,
    onToggleDepthLayer: () -> Unit,
    onToggleTracks: () -> Unit = {},
    isLandscape: Boolean = false,
    expandedFanId: ControlId? = null,
    onDismissFan: () -> Unit = {},
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
        // Apply zone300 visibility: user toggle OR auto-show overlay
        val showZone300 = appSettings.zone300Visible || zone300OverlayVisible
        val visibleZone300 = if (showZone300) zone300 else null
        // Apply regulated zones visibility: user toggle OR auto-show overlay
        val showRegZones = appSettings.regulatedZonesVisible || regulatedZoneOverlayVisible
        val visibleRegulatedZones = if (showRegZones) {
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

        // ── Scrim: transparent full-screen tap catcher when any fan is expanded ──
        //     Placed between MapView and overlay Row so it catches taps on empty
        //     areas of the screen (passing through non-clickable overlays above),
        //     but fan children, settings, and zoom buttons (in the Row above) still
        //     consume their own taps. Generic — dismisses whatever fan is open via
        //     onDismissFan(), works for any number of future fans.
        if (expandedFanId != null) {
            Box(modifier = Modifier.fillMaxSize().clickable { onDismissFan() })
        }

        // ── Layer 0 overlays: cap (bottom), arrow (middle), marker (top) ──
        // ── Layer 0 overlays: direction line + center marker ─────────────
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
                    TrackStatusIcon(
                        recorderState = trackRecorderState,
                        onClick = if (trackRecorderState.state == ykws.android.maro.data.track.TrackRecorderState.ON)
                            onStopRecording
                        else
                            onStartRecording
                    )
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
                                    color = ComposeColor(AppConfig.uiSettingsToastText),
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
                    // Menu (hamburger) button
                    MapControlButton(
                        onClick = onOpenTrackDrawer,
                        icon = { HamburgerIcon() }
                    )
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
                                maxCount = 6,
                                currentCount = 5,
                                direction = FanDirection.LEFT,
                                isOpen = isExpanded,
                                toggleChildren = true,
                                showActiveBadge = true,
                                activeChildCount = listOf(
                                    appSettings.tracksVisible,
                                    appSettings.depthLayerVisible,
                                    appSettings.regulatedZonesVisible,
                                    appSettings.zone300Visible,
                                    appSettings.lowDepthWarningVisible
                                ).count { it }
                            ),
                            parent = { _: Boolean, _: Int -> ThreeStripeLayerIcon(alpha = 1f) },
                            onParentClick = { onToggleFan(ControlId.LAYER_FAN) },
                            children = listOf<@Composable (Boolean) -> Unit>(
                                { isActive -> TrackLayerIcon(alpha = if (isActive) ButtonColors.activeAlpha else ButtonColors.inactiveAlpha) },
                                { isActive -> DepthBarIcon(alpha = if (isActive) ButtonColors.activeAlpha else ButtonColors.inactiveAlpha) },
                                { isActive -> RegulatedZoneIcon(alpha = if (isActive) ButtonColors.activeAlpha else ButtonColors.inactiveAlpha) },
                                { isActive -> DoubleCircleIcon(alpha = if (isActive) ButtonColors.activeAlpha else ButtonColors.inactiveAlpha) },
                                { isActive -> WarningTriangleIcon(alpha = if (isActive) ButtonColors.activeAlpha else ButtonColors.inactiveAlpha) }
                            ),
                            activeStates = listOf(
                                appSettings.tracksVisible,
                                appSettings.depthLayerVisible,
                                appSettings.regulatedZonesVisible,
                                appSettings.zone300Visible,
                                appSettings.lowDepthWarningVisible
                            ),
                            onChildClick = { index: Int, _: Boolean ->
                                when (index) {
                                    0 -> onToggleTracks()
                                    1 -> onToggleDepthLayer()
                                    2 -> onToggleRegulatedZones()
                                    3 -> onToggleZone300()
                                    4 -> onToggleLowDepthWarning()
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
            color = ComposeColor(AppConfig.uiErrorText),
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


/**
 * Hamburger menu icon: three horizontal lines (classic menu button).
 * Drawn with Canvas to match the app's icon family without material-icons-extended.
 */
@Composable
private fun HamburgerIcon() {
    androidx.compose.foundation.Canvas(modifier = Modifier.size(36.dp)) {
        val stroke = size.width * 0.15f
        val inset = size.width * 0.22f
        val cy = size.height / 2f
        val gap = size.height * 0.16f
        // Top line
        drawLine(
            color = ButtonColors.icon,
            start = Offset(inset, cy - gap),
            end = Offset(size.width - inset, cy - gap),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        // Middle line
        drawLine(
            color = ButtonColors.icon,
            start = Offset(inset, cy),
            end = Offset(size.width - inset, cy),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        // Bottom line
        drawLine(
            color = ButtonColors.icon,
            start = Offset(inset, cy + gap),
            end = Offset(size.width - inset, cy + gap),
            strokeWidth = stroke,
            cap = StrokeCap.Round
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
                            tint = ComposeColor(AppConfig.uiSettingsTextPrimary),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = stringResource(R.string.settings_title),
                        color = ComposeColor(AppConfig.uiSettingsTextPrimary),
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
                    2 -> SystemSettings(settings, onUpdateSettings, onGpsModeChange, onRegenerateRasters, onDismiss, systemScrollState)
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
                        color = ComposeColor(AppConfig.uiSettingsTextPrimary),
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
                        .background(ComposeColor(AppConfig.uiSettingsDivider))
                )
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    var warningExpanded by remember { mutableStateOf(false) }
                    SettingsExpander(
                        label = "Warning settings",
                        expanded = warningExpanded,
                        onToggle = { warningExpanded = !warningExpanded },
                        labelStyle = TextStyle(
                            color = ComposeColor(AppConfig.uiSettingsTextPrimary),
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
                        color = ComposeColor(AppConfig.uiSettingsTextPrimary),
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
                        .background(ComposeColor(AppConfig.uiSettingsDivider))
                )
                // Regulation info — collapsible toggle for info text panel
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    SettingsExpander(
                        label = "Regulation info",
                        expanded = settings.regulationInfoExpanded,
                        onToggle = { onUpdateSettings { it.copy(regulationInfoExpanded = !it.regulationInfoExpanded) } },
                        labelStyle = TextStyle(
                            color = ComposeColor(AppConfig.uiSettingsTextPrimary),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    ) {
                        Spacer(Modifier.height(8.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(ComposeColor(AppConfig.uiSettingsCardBackground))
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
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
                                    color = ComposeColor(AppConfig.uiSettingsTextPrimary),
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
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .background(ComposeColor(AppConfig.uiSettingsDivider))
                )
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    SettingsExpander(
                        label = "Categories",
                        expanded = settings.categoryFilterExpanded,
                        onToggle = { onUpdateSettings { it.copy(categoryFilterExpanded = !it.categoryFilterExpanded) } },
                        labelStyle = TextStyle(
                            color = ComposeColor(AppConfig.uiSettingsTextPrimary),
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
                        .background(ComposeColor(AppConfig.uiSettingsDivider))
                )
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    SettingsExpander(
                        label = "Boat size",
                        expanded = settings.boatSizeFilterExpanded,
                        onToggle = { onUpdateSettings { it.copy(boatSizeFilterExpanded = !it.boatSizeFilterExpanded) } },
                        labelStyle = TextStyle(
                            color = ComposeColor(AppConfig.uiSettingsTextPrimary),
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

        // ── Tracks layer toggle ────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ComposeColor(AppConfig.uiSettingsCardBackground))
        ) {
            // Master toggle row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Tracks",
                        color = ComposeColor(AppConfig.uiSettingsTextPrimary),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Show recorded tracks on the map",
                        color = ComposeColor(AppConfig.uiSettingsTextMuted),
                        fontSize = 13.sp
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Switch(
                    checked = settings.tracksVisible,
                    onCheckedChange = { on -> onUpdateSettings { it.copy(tracksVisible = on) } },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ComposeColor(AppConfig.uiSettingsAccent),
                        checkedTrackColor = ComposeColor(AppConfig.uiSettingsAccent).copy(alpha = 0.4f),
                        uncheckedThumbColor = ComposeColor(AppConfig.uiSettingsTextMuted),
                        uncheckedTrackColor = ComposeColor(AppConfig.uiSettingsSwitchTrackInactive)
                    )
                )
            }

            if (settings.tracksVisible) {
                // Number of tracks
                Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(ComposeColor(AppConfig.uiSettingsDivider)))
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    var nbExpanded by remember { mutableStateOf(false) }
                    SettingsExpander(
                        label = "Number of tracks",
                        expanded = nbExpanded,
                        onToggle = { nbExpanded = !nbExpanded },
                        labelStyle = TextStyle(
                            color = ComposeColor(AppConfig.uiSettingsTextPrimary),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    ) {
                        Spacer(Modifier.height(8.dp))
                        SettingsSliderRow(
                            label = "Number of tracks",
                            description = "Recent tracks to render (0-20)",
                            valueLabel = "%d".format(settings.trackingRenderNb),
                            value = settings.trackingRenderNb.toFloat(),
                            valueRange = 0f..20f,
                            steps = 20,
                            onValueChange = { v ->
                                onUpdateSettings { it.copy(trackingRenderNb = v.roundToInt().coerceIn(0, 20)) }
                            }
                        )
                    }
                }

                // Transparency
                Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(ComposeColor(AppConfig.uiSettingsDivider)))
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    var transExpanded by remember { mutableStateOf(false) }
                    SettingsExpander(
                        label = "Transparency",
                        expanded = transExpanded,
                        onToggle = { transExpanded = !transExpanded },
                        labelStyle = TextStyle(
                            color = ComposeColor(AppConfig.uiSettingsTextPrimary),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    ) {
                        Spacer(Modifier.height(8.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(ComposeColor(AppConfig.uiSettingsCardBackground))
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = "%d%% - %d%%".format(settings.trackingTransparencyFrom, settings.trackingTransparencyTo),
                                color = ComposeColor(AppConfig.uiSettingsAccent),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            RangeSlider(
                                value = settings.trackingTransparencyFrom.toFloat()..settings.trackingTransparencyTo.toFloat(),
                                onValueChange = { range: ClosedFloatingPointRange<Float> ->
                                    onUpdateSettings {
                                        it.copy(
                                            trackingTransparencyFrom = range.start.roundToInt(),
                                            trackingTransparencyTo = range.endInclusive.roundToInt()
                                        )
                                    }
                                },
                                valueRange = 0f..100f,
                                steps = 19,
                                colors = SliderDefaults.colors(
                                    thumbColor = ComposeColor(AppConfig.uiSettingsAccent),
                                    activeTrackColor = ComposeColor(AppConfig.uiSettingsAccent),
                                    inactiveTrackColor = ComposeColor(AppConfig.uiSettingsSwitchTrackInactive)
                                )
                            )
                        }
                    }
                }

                // Colors
                Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(ComposeColor(AppConfig.uiSettingsDivider)))
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    var colorsExpanded by remember { mutableStateOf(false) }
                    SettingsExpander(
                        label = "Colors",
                        expanded = colorsExpanded,
                        onToggle = { colorsExpanded = !colorsExpanded },
                        labelStyle = TextStyle(
                            color = ComposeColor(AppConfig.uiSettingsTextPrimary),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    ) {
                        Spacer(Modifier.height(8.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(ComposeColor(AppConfig.uiSettingsCardBackground))
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            ColorSwatchRow(
                                label = "Active track",
                                color = settings.trackingColorActive,
                                onColorSelected = { c -> onUpdateSettings { it.copy(trackingColorActive = c) } },
                                showPickLabel = false
                            )
                            Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(ComposeColor(AppConfig.uiSettingsDivider)))
                            ColorSwatchPairRow(
                                label = "Past tracks",
                                fromColor = settings.trackingColorPastFrom,
                                toColor = settings.trackingColorPastTo,
                                onFromColorSelected = { c -> onUpdateSettings { it.copy(trackingColorPastFrom = c) } },
                                onToColorSelected = { c -> onUpdateSettings { it.copy(trackingColorPastTo = c) } }
                            )
                            Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(ComposeColor(AppConfig.uiSettingsDivider)))
                            ColorSwatchPairRow(
                                label = "Pinned tracks",
                                fromColor = settings.trackingColorPinnedFrom,
                                toColor = settings.trackingColorPinnedTo,
                                onFromColorSelected = { c -> onUpdateSettings { it.copy(trackingColorPinnedFrom = c) } },
                                onToColorSelected = { c -> onUpdateSettings { it.copy(trackingColorPinnedTo = c) } }
                            )
                        }
                    }
                }
            }
        }

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
                        color = ComposeColor(AppConfig.uiSettingsTextPrimary),
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
                    .background(ComposeColor(AppConfig.uiSettingsDivider))
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
                        color = ComposeColor(AppConfig.uiSettingsTextPrimary),
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
                        .background(ComposeColor(AppConfig.uiSettingsDivider))
                )
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    var alertExpanded by remember { mutableStateOf(false) }
                    SettingsExpander(
                        label = "Alert settings",
                        expanded = alertExpanded,
                        onToggle = { alertExpanded = !alertExpanded },
                        labelStyle = TextStyle(
                            color = ComposeColor(AppConfig.uiSettingsTextPrimary),
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
                        color = ComposeColor(AppConfig.uiSettingsTextPrimary),
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
                    .background(ComposeColor(AppConfig.uiSettingsDivider))
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
                        color = ComposeColor(AppConfig.uiSettingsTextPrimary),
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
                    .background(ComposeColor(AppConfig.uiSettingsDivider))
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
                        color = ComposeColor(AppConfig.uiSettingsTextPrimary),
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
                    .background(ComposeColor(AppConfig.uiSettingsDivider))
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
                        color = ComposeColor(AppConfig.uiSettingsTextPrimary),
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

    }
}

// ── System tab ───────────────────────────────────────────────────────────

@Composable
private fun SystemSettings(
    settings: AppSettings,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    onGpsModeChange: (Boolean) -> Unit,
    onRegenerateRasters: (List<RasterCache.Step>) -> Unit,
    onDismiss: () -> Unit,
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
                        color = ComposeColor(AppConfig.uiSettingsTextPrimary),
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
                    onCheckedChange = { checked ->
                        onGpsModeChange(checked)
                        onDismiss()
                    },
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
                        .background(ComposeColor(AppConfig.uiSettingsDivider))
                )
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    var gpsTuningExpanded by remember { mutableStateOf(false) }
                    SettingsExpander(
                        label = "GPS tuning",
                        expanded = gpsTuningExpanded,
                        onToggle = { gpsTuningExpanded = !gpsTuningExpanded },
                        labelStyle = TextStyle(
                            color = ComposeColor(AppConfig.uiSettingsTextPrimary),
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

        // ── Stop detection ──────────────────────────────────────────────
        SectionHeader(title = stringResource(R.string.settings_idle_section_label))
        Spacer(modifier = Modifier.height(8.dp))

        // Grouped card: enable toggle + delay toggle + thresholds expander
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ComposeColor(AppConfig.uiSettingsCardBackground))
        ) {
            // Enable stop detection toggle
            SettingsToggleRow(
                label = stringResource(R.string.settings_stop_enable_label),
                description = stringResource(R.string.settings_stop_enable_desc),
                checked = settings.stopDetectionEnabled,
                onCheckedChange = { on -> onUpdateSettings { it.copy(stopDetectionEnabled = on) } }
            )

            // Conditional content: only shown when stop detection is enabled
            if (settings.stopDetectionEnabled) {
                // Thin divider
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .background(ComposeColor(AppConfig.uiSettingsDivider))
                )

                // Detection thresholds expander
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    var adaptiveAdvanced by remember { mutableStateOf(false) }
                    SettingsExpander(
                        label = stringResource(R.string.settings_stop_thresholds_label),
                        expanded = adaptiveAdvanced,
                        onToggle = { adaptiveAdvanced = !adaptiveAdvanced }
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))
                        SettingsSliderGroup {
                            SliderRowContent(
                                label = stringResource(R.string.settings_window_label),
                                description = stringResource(R.string.settings_window_desc),
                                valueLabel = stringResource(R.string.settings_value_seconds, settings.stopDetectionTimeSec),
                                value = settings.stopDetectionTimeSec.toFloat(),
                                valueRange = 10f..90f,
                                steps = 15,
                                onValueChange = { v -> onUpdateSettings { it.copy(stopDetectionTimeSec = (v / 5f).roundToInt() * 5) } }
                            )
                            SliderRowDivider()
                            SliderRowContent(
                                label = stringResource(R.string.settings_adaptive_dist_label),
                                description = stringResource(R.string.settings_adaptive_dist_desc),
                                valueLabel = stringResource(R.string.settings_value_meters, settings.stopDetectionDistanceM),
                                value = settings.stopDetectionDistanceM.toFloat(),
                                valueRange = 10f..30f,
                                steps = 3,
                                onValueChange = { v -> onUpdateSettings { it.copy(stopDetectionDistanceM = (v / 5f).roundToInt() * 5) } }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Thin divider
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .background(ComposeColor(AppConfig.uiSettingsDivider))
                )

                // Delay GPS when still toggle
                SettingsToggleRow(
                    label = stringResource(R.string.settings_stop_delay_label),
                    description = stringResource(R.string.settings_stop_delay_desc),
                    checked = settings.stopDetectionDelayGps,
                    onCheckedChange = { on -> onUpdateSettings { it.copy(stopDetectionDelayGps = on) } }
                )
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
                Text("Regenerate", color = ComposeColor(AppConfig.uiSettingsTextPrimary))
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
                    color = if (selected) ComposeColor(AppConfig.uiSettingsTextPrimary) else ComposeColor(AppConfig.uiSettingsTextMuted),
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
                color = ComposeColor(AppConfig.uiSettingsTextPrimary),
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
                color = ComposeColor(AppConfig.uiSettingsTextPrimary),
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
 * A row showing a color swatch with a label and a "pick" button.
 * Tapping the swatch opens a simple color dialog with preset palette.
 * TODO: Replace with Canvas-based HSV color picker for richer selection.
 */
@Composable
private fun ColorSwatchRow(
    label: String,
    color: Int,
    onColorSelected: (Int) -> Unit,
    showPickLabel: Boolean = true
) {
    var showPicker by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = ComposeColor(AppConfig.uiSettingsTextPrimary),
            fontSize = 14.sp
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Color swatch
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(ComposeColor(color))
                    .clickable { showPicker = true }
                    .border(1.dp, ComposeColor(AppConfig.uiSettingsDivider), RoundedCornerShape(6.dp))
            )
            if (showPickLabel) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Pick",
                    color = ComposeColor(AppConfig.uiSettingsAccent),
                    fontSize = 13.sp,
                    modifier = Modifier.clickable { showPicker = true }
                )
            }
        }
    }

    if (showPicker) {
        ColorPickerDialog(
            currentColor = color,
            onColorSelected = { c -> onColorSelected(c); showPicker = false },
            onDismiss = { showPicker = false }
        )
    }
}

/**
 * A reusable color picker dialog showing a grid of preset colors.
 */
@Composable
private fun ColorPickerDialog(
    currentColor: Int,
    onColorSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val presets = remember {
        listOf(
            0xFF1565C0.toInt(), // Blue
            0xFFD32F2F.toInt(), // Red
            0xFF388E3C.toInt(), // Green
            0xFFF57C00.toInt(), // Orange
            0xFF7B1FA2.toInt(), // Purple
            0xFF00796B.toInt(), // Teal
            0xFF5D4037.toInt(), // Brown
            0xFF000000.toInt(), // Black
            0xFFFFFFFF.toInt(), // White
            0xFFBDBDBD.toInt(), // Grey
            0xFFFFF176.toInt(), // Yellow
            0xFF4FC3F7.toInt()  // Light Blue
        )
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pick color") },
        text = {
            Column {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.height(150.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(presets) { presetColor ->
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(ComposeColor(presetColor))
                                .border(
                                    width = if (presetColor == currentColor) 3.dp else 1.dp,
                                    color = if (presetColor == currentColor) ComposeColor(AppConfig.uiSettingsAccent)
                                        else ComposeColor(AppConfig.uiSettingsDivider),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    onColorSelected(presetColor)
                                }
                        )
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * A single-row color selector showing a label and two clickable color swatches
 * (from → to) with a color picker dialog for each.
 */
@Composable
private fun ColorSwatchPairRow(
    label: String,
    fromColor: Int,
    toColor: Int,
    onFromColorSelected: (Int) -> Unit,
    onToColorSelected: (Int) -> Unit
) {
    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = ComposeColor(AppConfig.uiSettingsTextPrimary),
            fontSize = 14.sp
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            // From swatch
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(ComposeColor(fromColor))
                    .clickable { showFromPicker = true }
                    .border(1.dp, ComposeColor(AppConfig.uiSettingsDivider), RoundedCornerShape(4.dp))
            )
            Spacer(Modifier.width(6.dp))
            Text("→", color = ComposeColor(AppConfig.uiSettingsTextMuted), fontSize = 14.sp)
            Spacer(Modifier.width(6.dp))
            // To swatch
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(ComposeColor(toColor))
                    .clickable { showToPicker = true }
                    .border(1.dp, ComposeColor(AppConfig.uiSettingsDivider), RoundedCornerShape(4.dp))
            )
        }
    }

    if (showFromPicker) {
        ColorPickerDialog(
            currentColor = fromColor,
            onColorSelected = { c -> onFromColorSelected(c); showFromPicker = false },
            onDismiss = { showFromPicker = false }
        )
    }
    if (showToPicker) {
        ColorPickerDialog(
            currentColor = toColor,
            onColorSelected = { c -> onToColorSelected(c); showToPicker = false },
            onDismiss = { showToPicker = false }
        )
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
            color = ComposeColor(AppConfig.uiSettingsTextPrimary),
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
            color = ComposeColor(AppConfig.uiSettingsTextPrimary),
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
                color = ComposeColor(AppConfig.uiSettingsTextPrimary),
                fontSize = 14.sp
            ),
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ComposeColor(AppConfig.uiSettingsAccent),
                unfocusedBorderColor = ComposeColor(AppConfig.uiSettingsInputBorder),
                cursorColor = ComposeColor(AppConfig.uiSettingsTextPrimary)
            )
        )
    }
}


