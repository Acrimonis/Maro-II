
package ykws.android.maro.ui.map
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.track.TrackRecordingService
import ykws.android.maro.data.model.matchesFilter
import ykws.android.maro.data.track.toGpx

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
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
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.ime
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
import androidx.compose.material.icons.filled.Menu
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import android.util.Log
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import androidx.compose.ui.unit.Dp
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterNotNull
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
import ykws.android.maro.data.model.markers.MarkerGeometry
import ykws.android.maro.data.model.markers.UserMarker
import ykws.android.maro.data.markers.UserMarkerRepository
import ykws.android.maro.ui.components.ConfirmSheet
import ykws.android.maro.ui.components.SavedScrollState
import ykws.android.maro.spatial.SpatialOperations
import ykws.android.maro.spatial.DebugSegment
import ykws.android.maro.spatial.MarkerMatcher
import ykws.android.maro.spatial.NoOpWhereAmIDebugger
import ykws.android.maro.spatial.VisualWhereAmIDebugger
import ykws.android.maro.ui.map.MarkersViewModel
import ykws.android.maro.ui.map.MarkerDrawer
import ykws.android.maro.ui.map.toMarkerSnapshot
import ykws.android.maro.data.track.IdleThresholdCallback
import ykws.android.maro.data.track.IdleCaptureResult
import ykws.android.maro.data.track.WhereAmIProvider

/** Animation duration per GPS-follow scroll (ms). Must be < min GPS fix interval (1s). */
private const val GPS_ANIMATION_DURATION_MS = 600L

/** Right-edge control column width (12 gap + 64 button + 6 end). Paint-only reserve for transient overlays; the map itself is never padded by this. */
private val RIGHT_CONTROL_COLUMN_INSET = 82.dp

/** Computed polyline rendering appearance: ARGB color + stroke width. */
data class TrackPolylineAppearance(val argb: Int, val strokeWidth: Float)

/** One-shot target for click-N-move navigation: dismiss list → animate map → open drawer. */
private data class NavigateTarget(val geoPoint: GeoPoint, val markerId: String)

/** Pre-navigation snapshot — captured before zooming to track bounding box. */
private data class PreNavigationState(val zoom: Double, val centerLat: Double, val centerLon: Double)

/** One-shot trigger for track click-N-move: zoom-to-fit the track bounding box. */
private data class TrackNavigateState(
    val geoPoint: GeoPoint,
    val bbox: org.osmdroid.util.BoundingBox,
    val trackId: String
)

/** Track info drawer state — no scrim, map stays interactive. */
private data class TrackDrawerState(
    val isOpen: Boolean = false,
    val track: ykws.android.maro.data.track.Track? = null,
    val mapWasInteracted: Boolean = false
)

/** Snackbar entry for the vertical stack — track/marker deletes + marker-created undo. */
private sealed class ActiveSnack(val id: String, val name: String) {
    val uid: Int = nextUid()

    private companion object {
        private var uidCounter = 0
        private fun nextUid() = uidCounter++
    }

    class TrackDelete(id: String, name: String) : ActiveSnack(id, name)
    class MarkerDelete(
        id: String,
        name: String,
        val selection: List<String>,
        val source: DrawerSource
    ) : ActiveSnack(id, name)
    class CreateUndo(id: String, name: String) : ActiveSnack(id, name)
}

@Composable
private fun SnackRow(
    message: String,
    snackKey: Int,
    onUndo: () -> Unit,
    onTimeout: () -> Unit
) {
    LaunchedEffect(snackKey) {
        kotlinx.coroutines.delay(4000L)
        onTimeout()
    }
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    androidx.compose.animation.AnimatedVisibility(
        visible = entered,
        enter = androidx.compose.animation.slideInVertically(initialOffsetY = { it }) + androidx.compose.animation.fadeIn()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ComposeColor(0xE62A2A2A))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                color = ComposeColor.White,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(12.dp))
            androidx.compose.material3.TextButton(onClick = onUndo) {
                Text("Undo", color = ComposeColor(0xFF80CBC4), fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}

/**
 * Compute a track polyline's ARGB color and stroke width from its position
 * in a same-type group and the current rendering settings.
 *
 * @param index 0-based position (newest = 0) among same-type tracks being rendered.
 * @param total total tracks of this type being rendered.
 * @param transparencyNewest 0..100 (0 = opaque, 100 = invisible).
 * @param transparencyOldest 0..100.
 * @param colorFrom start color (0xRRGGBB, no alpha) for newest track.
 * @param colorTo end color (0xRRGGBB, no alpha) for oldest track.
 * @param strokeWidth polyline stroke width in px (default 6f).
 */
internal fun computeTrackPolylineAppearance(
    index: Int,
    total: Int,
    transparencyNewest: Int,
    transparencyOldest: Int,
    colorFrom: Int,
    colorTo: Int,
    strokeWidth: Float = 6f
): TrackPolylineAppearance {
    val alphaNewest = (100 - transparencyNewest) / 100f
    val alphaOldest = (100 - transparencyOldest) / 100f
    val t = if (total <= 1) 0f else index.toFloat() / (total - 1).toFloat()
    val alphaFraction = alphaNewest - t * (alphaNewest - alphaOldest)
    val alphaInt = (alphaFraction * 255).toInt().coerceIn(0, 255)

    val r = ((colorFrom shr 16 and 0xFF) * (1f - t) + (colorTo shr 16 and 0xFF) * t).toInt().coerceIn(0, 255)
    val g = ((colorFrom shr 8 and 0xFF) * (1f - t) + (colorTo shr 8 and 0xFF) * t).toInt().coerceIn(0, 255)
    val b = ((colorFrom and 0xFF) * (1f - t) + (colorTo and 0xFF) * t).toInt().coerceIn(0, 255)

    val argb = (alphaInt shl 24) or (r shl 16) or (g shl 8) or b
    return TrackPolylineAppearance(argb, strokeWidth)
}

/**
 * Compute the navigation target for a track.
 * Priority: longest-idle BoatMarker position, fallback to last track point.
 */
private fun computeTrackNavigateTarget(track: ykws.android.maro.data.track.Track): Pair<Double, Double> {
    val idleMarkers = track.boatMarkers.filter {
        it.trigger == ykws.android.maro.data.track.BoatMarkerTrigger.IDLE
    }
    if (idleMarkers.isNotEmpty()) {
        val longest = idleMarkers.maxBy {
            (it.endTimeMs ?: System.currentTimeMillis()) - it.startTimeMs
        }
        return Pair(longest.boatLat, longest.boatLon)
    }
    val last = track.trackPoints.last()
    return Pair(last.lat, last.lon)
}

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
    viewModel: NavigationViewModel,
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
    var showMarkerManagement by remember { mutableStateOf(false) }
    var navigateToTarget by remember { mutableStateOf<NavigateTarget?>(null) }
    // ── Screen-lock state (splash-proof touch guard) ────────────────────
    var screenLocked by rememberSaveable { mutableStateOf(false) }
    // Lock/unlock transient banner: null = hidden, true = locked, false = unlocked.
    var lockBanner by remember { mutableStateOf<Boolean?>(null) }

    // ── Click-N-Move state ─────────────────────────────────────────────
    var highlightedTrackId by remember { mutableStateOf<String?>(null) }
    var highlightedMarkerId by remember { mutableStateOf<String?>(null) }
    var navigationZonesVisible by remember { mutableStateOf(false) }
    var preNavigationState by remember { mutableStateOf<PreNavigationState?>(null) }
    var trackNavigateState by remember { mutableStateOf<TrackNavigateState?>(null) }
    var trackDrawerState by remember { mutableStateOf(TrackDrawerState()) }

    // ── List-detail navigation state ─────────────────────────────────────
    val trackListState = rememberLazyListState()
    val markerListState = rememberLazyListState()
    var trackListScrollState by remember { mutableStateOf<SavedScrollState?>(null) }
    var markerListScrollState by remember { mutableStateOf<SavedScrollState?>(null) }
    var trackOpenedFromList by remember { mutableStateOf(false) }
    var markerOpenedFromList by remember { mutableStateOf(false) }
    val activeSnacks = remember { androidx.compose.runtime.mutableStateListOf<ActiveSnack>() }
    val queuedSnacks = remember { androidx.compose.runtime.mutableStateListOf<ActiveSnack>() }
    val pendingDeleteIds = remember { androidx.compose.runtime.mutableStateListOf<String>() }
    val trackViewModel: ykws.android.maro.data.track.TrackViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel()
    val markersViewModel: MarkersViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel(factory = MarkersViewModel.Factory)
    val debugSegments by markersViewModel.debugSegments.collectAsState()
    val trackRecorderState by trackViewModel.uiState.collectAsState()
    val trackSummaries by trackViewModel.summaries.collectAsState()
    val recoveryTrack by trackViewModel.recoveryTrack.collectAsState()
    val trackScope = rememberCoroutineScope()

    // ── Vertical snackbar stack state helpers ────────────────────────────
    fun enqueueSnack(snack: ActiveSnack) {
        if (activeSnacks.size < 3) activeSnacks.add(snack)
        else queuedSnacks.add(snack)
    }

    fun promoteQueued() {
        while (activeSnacks.size < 3 && queuedSnacks.isNotEmpty()) {
            activeSnacks.add(queuedSnacks.removeAt(0))
        }
    }

    fun onSnackUndo(snack: ActiveSnack) {
        activeSnacks.remove(snack)
        when (snack) {
            is ActiveSnack.TrackDelete -> {
                pendingDeleteIds.remove("t:${snack.id}")
                trackScope.launch {
                    val track = trackViewModel.loadTrackDetailCached(snack.id)
                    if (track != null && track.trackPoints.isNotEmpty()) {
                        highlightedTrackId = snack.id
                        trackDrawerState = TrackDrawerState(isOpen = true, track = track, mapWasInteracted = false)
                        val tp = computeTrackNavigateTarget(track)
                        val gp = GeoPoint(tp.first, tp.second)
                        val bbox = if (track.trackPoints.size >= 2) org.osmdroid.util.BoundingBox(
                            track.trackPoints.maxOf { it.lat }, track.trackPoints.maxOf { it.lon },
                            track.trackPoints.minOf { it.lat }, track.trackPoints.minOf { it.lon }
                        ) else null
                        if (bbox != null) trackNavigateState = TrackNavigateState(gp, bbox, snack.id)
                        else mapView?.controller?.animateTo(gp, null, GPS_ANIMATION_DURATION_MS)
                    }
                }
            }
            is ActiveSnack.MarkerDelete -> {
                pendingDeleteIds.remove("m:${snack.id}")
                markersViewModel.openEditDrawer(snack.selection, selectedId = snack.id, source = snack.source)
                highlightedMarkerId = snack.id
            }
            is ActiveSnack.CreateUndo -> markersViewModel.undoCreateMarker()
        }
        promoteQueued()
    }

    fun onSnackTimeout(snack: ActiveSnack) {
        activeSnacks.remove(snack)
        when (snack) {
            is ActiveSnack.TrackDelete -> {
                pendingDeleteIds.remove("t:${snack.id}")
                trackViewModel.deleteTrack(snack.id)
            }
            is ActiveSnack.MarkerDelete -> {
                pendingDeleteIds.remove("m:${snack.id}")
                markersViewModel.deleteMarker(snack.id, closeDrawer = false)
            }
            is ActiveSnack.CreateUndo -> markersViewModel.dismissLastSaved()
        }
        promoteQueued()
    }

    fun openFirstValidTrack(candidateIds: List<String>, onNone: () -> Unit) {
        if (candidateIds.isEmpty()) {
            onNone()
            return
        }
        trackScope.launch {
            var opened = false
            for (candidateId in candidateIds) {
                val track = trackViewModel.loadTrackDetailCached(candidateId)
                if (track != null && track.trackPoints.isNotEmpty()) {
                    highlightedTrackId = candidateId
                    trackDrawerState = TrackDrawerState(isOpen = true, track = track, mapWasInteracted = false)
                    val tp = computeTrackNavigateTarget(track)
                    val gp = GeoPoint(tp.first, tp.second)
                    val bbox = if (track.trackPoints.size >= 2) org.osmdroid.util.BoundingBox(
                        track.trackPoints.maxOf { it.lat }, track.trackPoints.maxOf { it.lon },
                        track.trackPoints.minOf { it.lat }, track.trackPoints.minOf { it.lon }
                    ) else null
                    if (bbox != null) trackNavigateState = TrackNavigateState(gp, bbox, candidateId)
                    else mapView?.controller?.animateTo(gp, null, GPS_ANIMATION_DURATION_MS)
                    opened = true
                    break
                }
            }
            if (!opened) onNone()
        }
    }

    val markerChangeFlow = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }
    val anyFanExpanded = expandedFanId != null
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val displayScrollState = rememberScrollState()
    val navigationScrollState = rememberScrollState()
    val systemScrollState = rememberScrollState()

    val context = LocalContext.current
    // ── Screen-lock toggle: flip state + toast ──────────────────────────
    val onToggleScreenLock = {
        val newLocked = !screenLocked
        screenLocked = newLocked
        lockBanner = newLocked
    }
    LaunchedEffect(lockBanner) {
        if (lockBanner != null) {
            delay(2_000L)
            lockBanner = null
        }
    }
    // ── Background location permission dialog state (A2) ─────────────────
    var showBgLocationDialog by remember { mutableStateOf(false) }
    // ── Battery optimization dialog state (A4, triggered on recording start) ──
    var showBatteryOptDialog by remember { mutableStateOf(false) }
    val autoFollowSuppressed by viewModel.autoFollowSuppressed.collectAsState()
    val navigationState by viewModel.navigationState.collectAsState()
    val gpsPosition by viewModel.gpsPosition.collectAsState()
    val gpsStale by viewModel.gpsStale.collectAsState()
    val acquisitionMode by viewModel.acquisitionMode.collectAsState()
    val isEstimating by viewModel.isEstimating.collectAsState()
    val boatIsWater by viewModel.boatIsWater.collectAsState()
    // ── Service GPS permission signal (missing → re-prompt once per episode) ──
    val gpsPermissionMissing by TrackRecordingService.gpsPermissionMissing.collectAsState()
    var gpsPermissionDialogDismissed by remember { mutableStateOf(false) }
    LaunchedEffect(gpsPermissionMissing) {
        if (!gpsPermissionMissing) gpsPermissionDialogDismissed = false
    }

    // Pending GPS-mode toggle awaiting confirmation while a track is recording.
    var pendingGpsModeToggle by remember { mutableStateOf<Boolean?>(null) }
    // Effective heading for the zone-ahead cone:
    // GPS mode → GPS bearing (COG/compass, boat faces direction of travel)
    // Demo mode → 0° = north (boat marker always points up/top of map).
    //             When panning actively, demoBearingDeg tracks pan direction.
    val effectiveHeadingDeg = if (appSettings.gpsMode) navigationState.bearingDeg.toDouble()
        else navigationState.demoBearingDeg?.toDouble() ?: 0.0

    // Derive the GPS icon state from ViewModel state (7-state model).
    val gpsAccuracy by viewModel.gpsAccuracy.collectAsState()
    val gpsIconState = remember(appSettings.gpsMode, gpsPosition, gpsStale, acquisitionMode, isEstimating, gpsAccuracy) {
        val acc = gpsAccuracy
        when {
            !appSettings.gpsMode -> GpsIconState.DEMO
            gpsPosition == null -> GpsIconState.ACQUIRING
            isEstimating -> GpsIconState.ESTIMATING
            gpsStale -> GpsIconState.STALE
            acc != null && acc > ykws.android.maro.BuildConfig.GPS_ACCURACY_GOOD_THRESHOLD_M -> GpsIconState.WEAK
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
            GpsIconState.ESTIMATING -> AppConfig.statusGpsEstimating
            GpsIconState.WEAK -> AppConfig.statusGpsAcquiring
        }
        ComposeColor(raw)
    }

    // GPS permission launcher: on grant, enable GPS mode; on deny, stay in demo mode.
    val gpsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.updateSettings { it.copy(gpsMode = true) }
    }

    // Background location permission launcher (A2): native system dialog with "Allow all the time" option.
    val bgLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            // Proceed: battery prompt → recording
            val prefs = context.getSharedPreferences("maro_battery_prefs", Context.MODE_PRIVATE)
            if (!prefs.getBoolean("battery_opt_prompted", false)) {
                showBatteryOptDialog = true
            } else {
                trackViewModel.startRecording()
            }
        } else {
            // Denied — show fallback dialog with Settings link
            showBgLocationDialog = true
        }
    }

    // Track import file picker launcher
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            trackScope.launch(Dispatchers.IO) {
                try {
                    val extension = uri.toString().substringAfterLast('.').lowercase().take(10)
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val count = trackViewModel.importTracks(input, extension)
                        withContext(Dispatchers.Main) {
                            android.widget.Toast.makeText(context,
                                if (count > 0) "Imported $count track${if (count != 1) "s" else ""}"
                                else "Import failed — no valid tracks found",
                                android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (_: Exception) {
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Import failed", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
    // Permission-aware handler wired to the GPS settings switch.
    val applyGpsMode: (Boolean) -> Unit = { enable ->
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
    val onGpsModeChange: (Boolean) -> Unit = { enable ->
        if (enable != appSettings.gpsMode) {
            if (trackRecorderState.state == ykws.android.maro.data.track.TrackRecorderState.ON) {
                // Recording in progress — confirm before switching the sample source.
                pendingGpsModeToggle = enable
            } else {
                applyGpsMode(enable)
            }
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

    // ── GPS auto-follow: continuous DR + heading-up ────────────────────────
    // One throttled stream (≤ appSettings.mapRefreshFps) drives BOTH position and
    // orientation. Position comes from _displayPosition — a continuous 20 Hz dead-
    // reckoning stream that extrapolates between GPS fixes (gated <3 kn, capped 30m).
    // setCenter is instant (smoothness from DR, not animation). Re-engage uses
    // animateTo for smooth scroll-back after panning. Manual pinch/pan/fling keep
    // osmdroid's own full-rate path — the cap governs only this GPS-follow flow.
    LaunchedEffect(appSettings.gpsMode, appSettings.demoHeadingUp, autoFollowSuppressed, mapView) {
        val mv = mapView ?: return@LaunchedEffect
        if (!appSettings.gpsMode && !appSettings.demoHeadingUp) { mv.mapOrientation = 0f; mv.invalidate(); return@LaunchedEffect }
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

    // ── Demo heading-up: apply pan-derived bearing to map orientation ─────────
    // When demoHeadingUp is enabled (and we're in demo mode), the bearing is
    // computed from the pan direction in NavigationViewModel.computeDemoSpeed().
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
        if (coastlineReady) viewModel::isOnWater else { _, _ -> false }

    // Rasterise the colour map once per grid, off the main thread (~7 M cells).
    val depthBitmap by produceState<Bitmap?>(initialValue = null, depthGrid,
        appSettings.emodnetShallowCutoffM) {
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

    // ── User markers: from MarkersViewModel ─────────────────────────────────────────
    val userMarkers by markersViewModel.markers.collectAsState()
    val markerLayerState by markersViewModel.markerLayerState.collectAsState()
    val markerLayerVisible = markerLayerState != MarkerLayerState.HIDDEN

    // Wire coastline spatial index into MarkersViewModel for land-blocking when ready
    if (coastlineReady) {
        markersViewModel.coastlineIndex = viewModel.spatialIndex
    }

    // ── Wire shared settings into child ViewModels (framework fix) ──
    LaunchedEffect(Unit) {
        markersViewModel.observeSettings(viewModel.settings, viewModel::updateSettings)
    }
    LaunchedEffect(Unit) {
        trackViewModel.observeSettings(viewModel.settings)
    }

    // ── Startup: clean up non-keepable auto-markers (orphaned from crash) ──
    LaunchedEffect(Unit) {
        val nonKeepable = userMarkers.filter { !it.keepable }
        for (m in nonKeepable) {
            markersViewModel.deleteMarker(m.id)
        }
        if (nonKeepable.isNotEmpty()) {
            Log.d("MaroII_Map", "Startup cleanup: removed ${nonKeepable.size} non-keepable markers")
        }
    }

    // ── Marker change watcher: emit to markerChangeFlow when user markers are saved/edited/deleted ──
    val markerCount = userMarkers.size
    var skipFirstComposition by remember { mutableStateOf(true) }
    LaunchedEffect(markerCount) {
        if (skipFirstComposition) {
            skipFirstComposition = false
            return@LaunchedEffect
        }
        markerChangeFlow.tryEmit(Unit)
    }

    // ── BoatMarker idle callback — wired to marker matching engine ──
    val idleCallback = remember {
        object : IdleThresholdCallback {
            override suspend fun onIdleThresholdReached(position: LatLng): IdleCaptureResult {
                return try {
                    val result = markersViewModel.whereAmISync(position)
                    val snapshots = result.allMatches.map { it.toMarkerSnapshot() }
                    IdleCaptureResult(
                        entries = snapshots,
                        shouldOpenDrawer = snapshots.isNotEmpty()
                    )
                } catch (e: Exception) {
                    Log.w("MaroII_Map", "IdleThresholdCallback failed", e)
                    IdleCaptureResult(emptyList(), false)
                }
            }
        }
    }

    // ── Register process-scoped bridge for the service-owned recorder ──
    // The service builds TrackRecorder with lazy delegating callbacks, so it
    // reads these registrations at invocation time (survives Activity recreation).
    LaunchedEffect(Unit) {
        WhereAmIProvider.whereAmI = markersViewModel::whereAmISync
        WhereAmIProvider.idleCapture = { pos -> idleCallback.onIdleThresholdReached(pos) }
        markerChangeFlow.collect { WhereAmIProvider.markerChanges.tryEmit(Unit) }
    }

    // ── Track info error auto-dismiss after 8 seconds ──
    LaunchedEffect(trackRecorderState.infoError) {
        val error = trackRecorderState.infoError
        if (error != null) {
            delay(8_000L)
            trackViewModel.clearInfoError()
        }
    }

    // ── Track event observation: idle auto-marker lifecycle ──
    var pendingAutoMarkerId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        trackViewModel.events.collect { event ->
            when (event) {
                is ykws.android.maro.data.track.TrackEvent.IdlePeriodStarted -> {
                    val markerId = markersViewModel.addTempAutoMarker(
                        event.entryLat, event.entryLon, event.startTimeMs)
                    if (markerId.isNotEmpty()) {
                        pendingAutoMarkerId = markerId
                        trackViewModel.setActiveSessionAutoMarkerId(markerId)
                    }
                }
                is ykws.android.maro.data.track.TrackEvent.DrawerAutoOpenRequested -> {
                    if (markersViewModel.drawerState.value == MarkerDrawerState.Hidden) {
                        val pos = gpsPosition ?: mapCenter
                        markersViewModel.whereAmI(pos)
                    }
                }
                is ykws.android.maro.data.track.TrackEvent.DrawerAutoCloseRequested -> {
                    if (markersViewModel.drawerState.value == MarkerDrawerState.MatchResult) {
                        markersViewModel.closeDrawer()
                    }
                }
                is ykws.android.maro.data.track.TrackEvent.IdlePeriodCompleted -> {
                    val id = event.autoMarkerId ?: return@collect
                    try {
                        val minDuration = AppConfig.boatMarkerAutoMarkerMinDurationSec
                        if (event.durationSec >= minDuration || event.endTimeMs == 0L) {
                            val title = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                                .format(java.util.Date(event.startTimeMs))
                            val startFmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US)
                                .format(java.util.Date(event.startTimeMs))
                            val durMin = event.durationSec / 60
                            val desc = if (event.endTimeMs == 0L) {
                                "@ $startFmt -> ?"
                            } else {
                                val endFmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US)
                                    .format(java.util.Date(event.endTimeMs))
                                "@ $startFmt -> $endFmt ($durMin min)"
                            }
                            markersViewModel.confirmAutoMarker(id, title, desc)
                            trackViewModel.setBoatMarkerAutoMarkerId(id)
                            markerChangeFlow.tryEmit(Unit)
                        } else {
                            markersViewModel.deleteMarker(id)
                            markerChangeFlow.tryEmit(Unit)
                        }
                    } catch (e: Exception) {
                        Log.w("MaroII_Map", "autoMarker finalize failed", e)
                    }
                    pendingAutoMarkerId = null
                }
                is ykws.android.maro.data.track.TrackEvent.Resumed -> {
                    // Restore checkpoint points to the live polyline on Continue.
                    // The polyline may not exist yet (Compose hasn't recomposed after state→ON),
                    // so create it directly if needed.
                    val mv = mapView ?: return@collect
                    // Clear any existing live-track polylines (from polyline creation LaunchedEffect)
                    mv.overlays.removeAll {
                        (it as? org.osmdroid.views.overlay.Polyline)?.title == "track_recording"
                    }
                    val points = event.points
                    if (points.isEmpty()) return@collect
                    // Build solid polyline segments, splitting at GAP markers
                    var segmentStart = 0
                    for (i in points.indices) {
                        if (points[i].type == ykws.android.maro.data.track.PointType.GAP) {
                            // Finalize solid segment before the gap
                            if (i > segmentStart) {
                                val solidPts = points.subList(segmentStart, i).map {
                                    org.osmdroid.util.GeoPoint(it.lat, it.lon)
                                }
                                if (solidPts.size >= 2) {
                                    val solid = org.osmdroid.views.overlay.Polyline().apply {
                                        title = "track_recording"
                                        outlinePaint.color = appSettings.trackingColorActive
                                        outlinePaint.strokeWidth = 10f
                                        setPoints(solidPts)
                                    }
                                    mv.overlays.add(solid)
                                }
                            }
                            // Add dashed gap segment
                            val gapPts = listOf(
                                org.osmdroid.util.GeoPoint(points[i].lat, points[i].lon),
                                if (i + 1 < points.size)
                                    org.osmdroid.util.GeoPoint(points[i + 1].lat, points[i + 1].lon)
                                else org.osmdroid.util.GeoPoint(points[i].lat, points[i].lon)
                            )
                            val gap = org.osmdroid.views.overlay.Polyline().apply {
                                title = "track_recording"
                                outlinePaint.color = appSettings.trackingColorActive
                                outlinePaint.strokeWidth = 10f
                                outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(20f, 10f), 0f)
                                setPoints(gapPts)
                            }
                            mv.overlays.add(gap)
                            segmentStart = i + 1
                        }
                    }
                    // Final solid segment after the last gap (or the whole track if no gaps)
                    if (segmentStart < points.size) {
                        val finalPts = points.subList(segmentStart, points.size).map {
                            org.osmdroid.util.GeoPoint(it.lat, it.lon)
                        }
                        if (finalPts.size >= 2) {
                            val finalSolid = org.osmdroid.views.overlay.Polyline().apply {
                                title = "track_recording"
                                outlinePaint.color = appSettings.trackingColorActive
                                outlinePaint.strokeWidth = 10f
                                setPoints(finalPts)
                            }
                            mv.overlays.add(finalSolid)
                        }
                    }
                    mv.invalidate()
                }
                else -> { /* Started, Stopped, PointCaptured — handled elsewhere */ }
            }
        }
    }

    // Wire debug ray tracer + sync persisted setting → AppConfig
    LaunchedEffect(Unit) {
        AppConfig.markerDebugRaysEnabled = appSettings.markerDebugRays
        if (AppConfig.markerDebugRaysEnabled) {
            MarkerMatcher.debugger = VisualWhereAmIDebugger()
            Log.d("WIA", "DEBUGGER: VisualWhereAmIDebugger activated")
        }
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
                if (coastlineReady) viewModel::isOnWater else { _, _ -> false }
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

    // ── Demo-mode sample feed → service-owned recorder ───────────────────
    // GPS mode: TrackRecordingService owns its own LocationManager sampling,
    // so recording survives Activity destruction / task removal.
    // Demo mode has no real GPS — the UI assembles TrackSamples from the
    // virtual position (map center) and pushes them into the service via
    // TrackRecordingService.pushSample. The UI also mirrors its stationary
    // flag into the service (GPS mode self-computes it from its own stream).
    // NOTE: LaunchedEffect(Unit) — NOT keyed on gpsMode. Toggling the position
    // source must NOT tear down / restart this feed.
    LaunchedEffect(Unit) {
        // Mirror the UI's stationary flag into the service (demo mode only).
        launch {
            viewModel.isStopped.collect { stopped ->
                if (!appSettings.gpsMode) {
                    TrackRecordingService.updateStopped(stopped)
                }
            }
        }

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
        val sampleFlow = kotlinx.coroutines.flow.combine(
            viewModel.gpsPosition,
            viewModel.mapCenter,
            viewModel.navigationState,
            viewModel.isEstimating,
            ticker
        ) { gpsPos, center, nav, estimating, _ ->
            // Dead reckoning extrapolates display position only —
            // never feed extrapolated positions into track recording.
            if (estimating) return@combine null
            val isGps = appSettings.gpsMode
            val pos = gpsPos ?: center
            val speedKn = if (isGps) nav.speedKnots else nav.demoSpeedKnots
            val speedMs = speedKn?.let { it * 0.514444f }
            val bearing = if (isGps) nav.bearingDeg else nav.demoBearingDeg
            ykws.android.maro.data.track.TrackSample(
                position = pos,
                speedMps = speedMs,
                bearingDeg = bearing,
                hasLock = isGps,
                timestampEpochMs = System.currentTimeMillis(),
                accuracyM = if (isGps) viewModel.gpsAccuracy.value else null
            )
        }.filterNotNull()

        launch {
            sampleFlow.collect { sample ->
                // Only demo-mode samples are UI-assembled; GPS samples arrive
                // from the service's own LocationManager listener.
                if (!appSettings.gpsMode) {
                    TrackRecordingService.pushSample(sample)
                }
            }
        }

        // Periodic demo position feed: when GPS is off, re-feed the map center
        // every second so the adaptive policy timer advances toward IDLE even
        // when the user has stopped dragging (feedDemoPosition from onCenterChanged
        // only fires on actual scroll events).
        while (true) {
            if (!appSettings.gpsMode) {
                val center = viewModel.mapCenter.value
                if (center != null) {
                    viewModel.feedDemoPosition(center.latitude, center.longitude)
                }
            }
            kotlinx.coroutines.delay(1_000L)
        }
    }

    // ── Track overlay: incremental diff for history tracks with fading transparency ──
    // Track the set of currently-rendered track IDs to avoid full teardown+rebuild.
    val renderedTrackIds = remember { mutableStateOf(setOf<String>()) }

    LaunchedEffect(mapView, showSettings, appSettings.tracksVisible, appSettings.trackingRenderNb,
        appSettings.trackingColorPastFrom, appSettings.trackingColorPastTo,
        appSettings.trackingTransparencyNewest, appSettings.trackingTransparencyOldest,
        appSettings.trackingColorPinnedFrom, appSettings.trackingColorPinnedTo,
        appSettings.trackingTransparencyPinnedNewest, appSettings.trackingTransparencyPinnedOldest,
        appSettings.trackListFilter, trackSummaries, highlightedTrackId) {
        val mv = mapView ?: return@LaunchedEffect

        // Apply filter before display split
        val midnightMs = ykws.android.maro.data.model.todayMidnightMs()
        val filteredSummaries = trackSummaries.filter { it.matchesFilter(appSettings.trackListFilter, midnightMs) }

        // Determine desired track ID set (history = non-pinned only)
        val desiredIds = if (appSettings.tracksVisible) {
            val nbToRender = appSettings.trackingRenderNb.coerceIn(0, 20)
            if (nbToRender > 0) {
                filteredSummaries
                    .filter { it.visibleOnMap && !it.pinned }
                    .sortedByDescending { it.startTimeMs }
                    .take(nbToRender)
                    .map { it.id }
                    .toSet()
            } else emptySet()
        } else emptySet()

        // Remove all existing track history overlays + auto-marker pins — rebuild from scratch
        val toRemove = mv.overlays.filter { overlay ->
            (overlay as? org.osmdroid.views.overlay.Polyline)?.title?.startsWith("track_hist_") == true ||
            (overlay as? org.osmdroid.views.overlay.Marker)?.title?.startsWith("track_auto_hist_") == true
        }
        mv.overlays.removeAll(toRemove)

        val sortedDesired = if (appSettings.tracksVisible) {
            val nbToRender = appSettings.trackingRenderNb.coerceIn(0, 20)
            if (nbToRender > 0) {
                filteredSummaries
                    .filter { it.visibleOnMap && !it.pinned }
                    .sortedByDescending { it.startTimeMs }
                    .take(nbToRender)
            } else emptyList()
        } else emptyList()

        val total = sortedDesired.size

        val historyOverlays = mutableListOf<List<org.osmdroid.views.overlay.Overlay>>()
        for ((index, summary) in sortedDesired.withIndex()) {
            val trackOverlays = mutableListOf<org.osmdroid.views.overlay.Overlay>()
            val track = trackViewModel.loadTrackDetailCached(summary.id) ?: continue
            if (track.trackPoints.isEmpty()) continue

            val appearances = if (summary.id == highlightedTrackId) {
                listOf(
                    TrackPolylineAppearance(0xCC000000.toInt(), 16f),
                    TrackPolylineAppearance(0xFFFFD700.toInt() or (0xFF shl 24), 8f)
                )
            } else {
                listOf(computeTrackPolylineAppearance(
                    index = index,
                    total = total,
                    transparencyNewest = appSettings.trackingTransparencyNewest,
                    transparencyOldest = appSettings.trackingTransparencyOldest,
                    colorFrom = appSettings.trackingColorPastFrom,
                    colorTo = appSettings.trackingColorPastTo,
                    strokeWidth = if (index == 0) 8f else 6f
                ))
            }

            for (appearance in appearances) {
                // Split at GAP markers: solid segments between gaps, dashed for gap segments
                val points = track.trackPoints
                var segmentStart = 0
                for (i in points.indices) {
                    if (points[i].type == ykws.android.maro.data.track.PointType.GAP) {
                        if (i > segmentStart) {
                            val solidPoints = points.subList(segmentStart, i).map { pt ->
                                org.osmdroid.util.GeoPoint(pt.lat, pt.lon)
                            }
                            if (solidPoints.size >= 2) {
                                val solidPolyline = org.osmdroid.views.overlay.Polyline().apply {
                                    title = "track_hist_${summary.id}"
                                    outlinePaint.color = appearance.argb
                                    outlinePaint.strokeWidth = appearance.strokeWidth
                                    setPoints(solidPoints)
                                }
                                trackOverlays.add(solidPolyline)
                            }
                        }
                        val gapFrom = org.osmdroid.util.GeoPoint(points[i].lat, points[i].lon)
                        val gapTo = if (i + 1 < points.size)
                            org.osmdroid.util.GeoPoint(points[i + 1].lat, points[i + 1].lon)
                        else gapFrom
                        val gapLine = org.osmdroid.views.overlay.Polyline().apply {
                            title = "track_hist_${summary.id}"
                            outlinePaint.color = appearance.argb
                            outlinePaint.strokeWidth = appearance.strokeWidth
                            outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(20f, 10f), 0f)
                            setPoints(listOf(gapFrom, gapTo))
                        }
                        trackOverlays.add(gapLine)
                        segmentStart = i + 1
                    }
                }
                if (segmentStart < points.size && points.size - segmentStart >= 2) {
                    val solidPoints = points.subList(segmentStart, points.size).map { pt ->
                        org.osmdroid.util.GeoPoint(pt.lat, pt.lon)
                    }
                    val solidPolyline = org.osmdroid.views.overlay.Polyline().apply {
                        title = "track_hist_${summary.id}"
                        outlinePaint.color = appearance.argb
                        outlinePaint.strokeWidth = appearance.strokeWidth
                        setPoints(solidPoints)
                    }
                    trackOverlays.add(solidPolyline)
                }
            }

            // ── Auto-marker 🕐 pins for this history track ──
            for (bm in track.boatMarkers) {
                val markerId = bm.autoMarkerId ?: continue
                val iconMarker = org.osmdroid.views.overlay.Marker(mv).apply {
                    position = org.osmdroid.util.GeoPoint(bm.boatLat, bm.boatLon)
                    setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER, org.osmdroid.views.overlay.Marker.ANCHOR_CENTER)
                    title = "track_auto_hist_${summary.id}_${bm.sequenceIndex}"
                    val bitmap = android.graphics.Bitmap.createBitmap(48, 48, android.graphics.Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(bitmap)
                    val paint = android.graphics.Paint().apply {
                        color = appearances.last().argb
                        textSize = 36f
                        textAlign = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                    }
                    canvas.drawText("\uD83D\uDD50", 24f, 34f, paint)
                    icon = android.graphics.drawable.BitmapDrawable(mv.context.resources, bitmap)
                }
                trackOverlays.add(iconMarker)
            }
            historyOverlays.add(trackOverlays)
        }
        historyOverlays.reverse()
        for (trackOverlays in historyOverlays) {
            mv.overlays.addAll(trackOverlays)
        }

        // ── Pinned tracks: always render all, separate colors/transparency ──
        val toRemovePinned = mv.overlays.filter { overlay ->
            (overlay as? org.osmdroid.views.overlay.Polyline)?.title?.startsWith("track_pin_") == true ||
            (overlay as? org.osmdroid.views.overlay.Marker)?.title?.startsWith("track_auto_pin_") == true
        }
        mv.overlays.removeAll(toRemovePinned)

        val pinnedSummaries = if (appSettings.tracksVisible) {
            filteredSummaries.filter { it.pinned }.sortedByDescending { it.startTimeMs }
        } else emptyList()

        val pinnedTotal = pinnedSummaries.size
        val pinnedOverlays = mutableListOf<List<org.osmdroid.views.overlay.Overlay>>()
        for ((index, summary) in pinnedSummaries.withIndex()) {
            val trackOverlays = mutableListOf<org.osmdroid.views.overlay.Overlay>()
            val track = trackViewModel.loadTrackDetailCached(summary.id) ?: continue
            if (track.trackPoints.isEmpty()) continue

            val appearances = if (summary.id == highlightedTrackId) {
                listOf(
                    TrackPolylineAppearance(0xCC000000.toInt(), 16f),
                    TrackPolylineAppearance(0xFFFFD700.toInt() or (0xFF shl 24), 8f)
                )
            } else {
                listOf(computeTrackPolylineAppearance(
                    index = index,
                    total = pinnedTotal,
                    transparencyNewest = appSettings.trackingTransparencyPinnedNewest,
                    transparencyOldest = appSettings.trackingTransparencyPinnedOldest,
                    colorFrom = appSettings.trackingColorPinnedFrom,
                    colorTo = appSettings.trackingColorPinnedTo,
                    strokeWidth = 6f
                ))
            }

            for (appearance in appearances) {
                // Split at GAP markers: solid segments between gaps, dashed for gap segments
                val points = track.trackPoints
                var segmentStart = 0
                for (i in points.indices) {
                    if (points[i].type == ykws.android.maro.data.track.PointType.GAP) {
                        if (i > segmentStart) {
                            val solidPoints = points.subList(segmentStart, i).map { pt ->
                                org.osmdroid.util.GeoPoint(pt.lat, pt.lon)
                            }
                            if (solidPoints.size >= 2) {
                                val solidPolyline = org.osmdroid.views.overlay.Polyline().apply {
                                    title = "track_pin_${summary.id}"
                                    outlinePaint.color = appearance.argb
                                    outlinePaint.strokeWidth = appearance.strokeWidth
                                    setPoints(solidPoints)
                                }
                                trackOverlays.add(solidPolyline)
                            }
                        }
                        val gapFrom = org.osmdroid.util.GeoPoint(points[i].lat, points[i].lon)
                        val gapTo = if (i + 1 < points.size)
                            org.osmdroid.util.GeoPoint(points[i + 1].lat, points[i + 1].lon)
                        else gapFrom
                        val gapLine = org.osmdroid.views.overlay.Polyline().apply {
                            title = "track_pin_${summary.id}"
                            outlinePaint.color = appearance.argb
                            outlinePaint.strokeWidth = appearance.strokeWidth
                            outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(20f, 10f), 0f)
                            setPoints(listOf(gapFrom, gapTo))
                        }
                        trackOverlays.add(gapLine)
                        segmentStart = i + 1
                    }
                }
                if (segmentStart < points.size && points.size - segmentStart >= 2) {
                    val solidPoints = points.subList(segmentStart, points.size).map { pt ->
                        org.osmdroid.util.GeoPoint(pt.lat, pt.lon)
                    }
                    val solidPolyline = org.osmdroid.views.overlay.Polyline().apply {
                        title = "track_pin_${summary.id}"
                        outlinePaint.color = appearance.argb
                        outlinePaint.strokeWidth = appearance.strokeWidth
                        setPoints(solidPoints)
                    }
                    trackOverlays.add(solidPolyline)
                }
            }

            // ── Auto-marker 🕐 pins for this pinned track ──
            for (bm in track.boatMarkers) {
                val markerId = bm.autoMarkerId ?: continue
                val iconMarker = org.osmdroid.views.overlay.Marker(mv).apply {
                    position = org.osmdroid.util.GeoPoint(bm.boatLat, bm.boatLon)
                    setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER, org.osmdroid.views.overlay.Marker.ANCHOR_CENTER)
                    title = "track_auto_pin_${summary.id}_${bm.sequenceIndex}"
                    val bitmap = android.graphics.Bitmap.createBitmap(48, 48, android.graphics.Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(bitmap)
                    val paint = android.graphics.Paint().apply {
                        color = appearances.last().argb
                        textSize = 36f
                        textAlign = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                    }
                    canvas.drawText("\uD83D\uDD50", 24f, 34f, paint)
                    icon = android.graphics.drawable.BitmapDrawable(mv.context.resources, bitmap)
                }
                trackOverlays.add(iconMarker)
            }
            pinnedOverlays.add(trackOverlays)
        }
        pinnedOverlays.reverse()
        for (trackOverlays in pinnedOverlays) {
            mv.overlays.addAll(trackOverlays)
        }

        // Ensure active track stays on top of pinned (z-order: history → pinned → active)
        val activePolyline = mv.overlays.firstOrNull {
            (it as? org.osmdroid.views.overlay.Polyline)?.title == "track_recording"
        }
        if (activePolyline != null) {
            mv.overlays.remove(activePolyline)
            mv.overlays.add(activePolyline)
        }

        // Move highlighted track above active (z-order: ... → active → highlighted)
        if (highlightedTrackId != null) {
            val highlightedOverlays = mv.overlays.filter { overlay ->
                val polyTitle = (overlay as? org.osmdroid.views.overlay.Polyline)?.title
                if (polyTitle != null) {
                    polyTitle == "track_hist_$highlightedTrackId" ||
                    polyTitle == "track_pin_$highlightedTrackId"
                } else {
                    val markerTitle = (overlay as? org.osmdroid.views.overlay.Marker)?.title
                    markerTitle?.startsWith("track_auto_hist_${highlightedTrackId}_") == true ||
                    markerTitle?.startsWith("track_auto_pin_${highlightedTrackId}_") == true
                }
            }
            mv.overlays.removeAll(highlightedOverlays)
            mv.overlays.addAll(highlightedOverlays)
        }

        renderedTrackIds.value = desiredIds
        mv.invalidate()
    }

    // ── Active recording trace: incremental polyline via newPoint stream ────
    // Polyline lifecycle (create/remove) driven by recorder state snapshot.
    LaunchedEffect(mapView, appSettings.trackingColorActive) {
        val mv = mapView ?: return@LaunchedEffect
        androidx.compose.runtime.snapshotFlow { trackRecorderState.state }
            .collect { recState ->
                if (recState == ykws.android.maro.data.track.TrackRecorderState.ON) {
                    val existing = mv.overlays.firstOrNull {
                        (it as? org.osmdroid.views.overlay.Polyline)?.title == "track_recording"
                    }
                    if (existing == null) {
                        val polyline = org.osmdroid.views.overlay.Polyline().apply {
                            title = "track_recording"
                            outlinePaint.color = appSettings.trackingColorActive
                            outlinePaint.strokeWidth = 10f
                            isVisible = true
                        }
                        mv.overlays.add(polyline)
                        mv.invalidate()
                    }
                } else {
                    val removed = mv.overlays.removeAll {
                        (it as? org.osmdroid.views.overlay.Polyline)?.title == "track_recording"
                    }
                    if (removed) mv.invalidate()
                }
            }
    }

    // ── Incremental point appending: observe newPoint stream for live polyline ─┐
    // Keyed on recorder state so a stop→restart cycle re-obtains the new SharedFlow.
    // GAP markers split the live polyline: solid for normal segments, dashed for gaps.
    LaunchedEffect(mapView, trackRecorderState.state) {
        val mv = mapView ?: return@LaunchedEffect
        val stream = trackViewModel.newPointStream ?: return@LaunchedEffect
        stream.collect { point ->
            if (point.type == ykws.android.maro.data.track.PointType.GAP) {
                // Find the last active solid polyline and finalize it
                val lastSolid = mv.overlays.filter {
                    (it as? org.osmdroid.views.overlay.Polyline)?.title == "track_recording" &&
                    (it as org.osmdroid.views.overlay.Polyline).outlinePaint.pathEffect == null
                }.lastOrNull() as? org.osmdroid.views.overlay.Polyline
                val lastPt = lastSolid?.actualPoints?.lastOrNull()
                if (lastPt != null) {
                    val gapLine = org.osmdroid.views.overlay.Polyline().apply {
                        title = "track_recording"
                        outlinePaint.color = appSettings.trackingColorActive
                        outlinePaint.strokeWidth = 10f
                        outlinePaint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(20f, 10f), 0f)
                        isVisible = true
                        setPoints(listOf(lastPt, org.osmdroid.util.GeoPoint(point.lat, point.lon)))
                    }
                    mv.overlays.add(gapLine)
                }
                val resumedLine = org.osmdroid.views.overlay.Polyline().apply {
                    title = "track_recording"
                    outlinePaint.color = appSettings.trackingColorActive
                    outlinePaint.strokeWidth = 10f
                    isVisible = true
                    addPoint(org.osmdroid.util.GeoPoint(point.lat, point.lon))
                }
                mv.overlays.add(resumedLine)
                mv.invalidate()
            } else {
                val polyline = mv.overlays.filter {
                    (it as? org.osmdroid.views.overlay.Polyline)?.title == "track_recording" &&
                    (it as org.osmdroid.views.overlay.Polyline).outlinePaint.pathEffect == null
                }.lastOrNull() as? org.osmdroid.views.overlay.Polyline ?: return@collect
                polyline.addPoint(org.osmdroid.util.GeoPoint(point.lat, point.lon))
                mv.invalidate()
            }
        }
    }

    // ── Trailing polyline: interpolated segment from last accepted point ──
    // to the dead-reckoned display position, updated at 20 Hz. Display only —
    // never recorded. Semi-transparent solid (not dashed) to distinguish from
    // GAP markers. Cleaned up on recorder OFF.
    LaunchedEffect(mapView, trackRecorderState.state) {
        val mv = mapView ?: return@LaunchedEffect
        if (trackRecorderState.state != ykws.android.maro.data.track.TrackRecorderState.ON) {
            mv.overlays.removeAll { (it as? org.osmdroid.views.overlay.Polyline)?.title == "track_trailing" }
            mv.invalidate()
            return@LaunchedEffect
        }
        snapshotFlow { viewModel.displayPosition.value }
            .collect { displayPos ->
                // Remove previous trailing polyline
                mv.overlays.removeAll { (it as? org.osmdroid.views.overlay.Polyline)?.title == "track_trailing" }
                if (displayPos == null) return@collect
                // Find the last accepted point from the recording polyline
                val recordingLine = mv.overlays.filter {
                    (it as? org.osmdroid.views.overlay.Polyline)?.title == "track_recording" &&
                    (it as org.osmdroid.views.overlay.Polyline).outlinePaint.pathEffect == null
                }.lastOrNull() as? org.osmdroid.views.overlay.Polyline
                val lastPt = recordingLine?.actualPoints?.lastOrNull() ?: return@collect
                // Draw trailing segment: last accepted point → display position
                val trailing = org.osmdroid.views.overlay.Polyline().apply {
                    title = "track_trailing"
                    outlinePaint.color = (appSettings.trackingColorActive and 0x00FFFFFF) or (0x66000000.toInt())  // ~40% alpha
                    outlinePaint.strokeWidth = 10f
                    isVisible = true
                    setPoints(listOf(lastPt, org.osmdroid.util.GeoPoint(displayPos.latitude, displayPos.longitude)))
                }
                mv.overlays.add(trailing)
                mv.invalidate()
            }
    }

    // ── WhereAmI debug segments: visual overlay on the map ─────────────────
    // Green = clear line-of-sight, Red = blocked by land.
    LaunchedEffect(mapView, debugSegments) {
        val mv = mapView ?: run { Log.d("WIA", "DEBUGGER: mapView null, skipping render"); return@LaunchedEffect }
        Log.d("WIA", "DEBUGGER: rendering ${debugSegments.size} segments")
        // Remove previous debug polylines
        mv.overlays.removeAll {
            (it as? org.osmdroid.views.overlay.Polyline)?.title?.startsWith("wia_debug_") == true
        }
        // Render current segments
        if (debugSegments.isNotEmpty()) {
            debugSegments.forEachIndexed { index, segment ->
                val color = if (segment.blocked) Color.RED else Color.GREEN
                val polyline = org.osmdroid.views.overlay.Polyline().apply {
                    title = "wia_debug_$index"
                    setPoints(listOf(
                        org.osmdroid.util.GeoPoint(segment.boat.latitude, segment.boat.longitude),
                        org.osmdroid.util.GeoPoint(segment.target.latitude, segment.target.longitude)
                    ))
                    outlinePaint.color = color
                    outlinePaint.strokeWidth = 3f
                }
                mv.overlays.add(polyline)
            }
        }
        mv.invalidate()
    }

    // ── Foreground notification updates ────────────────────────────────────
    // Sends recording stats to TrackRecordingService every ~5s while recording.
    // When recording stops, sends one final update to revert to "Ready".
    LaunchedEffect(trackRecorderState, appSettings.gpsMode, boatIsWater) {
        val state = trackRecorderState
        val isDemo = !appSettings.gpsMode
        val isMoving = !viewModel.isStopped.value
        val speedKn = navigationState.speedKnots ?: navigationState.demoSpeedKnots
        val intent = Intent(context, TrackRecordingService::class.java).apply {
            action = TrackRecordingService.ACTION_UPDATE
            putExtra(TrackRecordingService.EXTRA_IS_DEMO, isDemo)
            // Always-sent extras
            putExtra(TrackRecordingService.EXTRA_IS_MOVING, isMoving)
            putExtra(TrackRecordingService.EXTRA_SPEED_KN, speedKn)
            putExtra(TrackRecordingService.EXTRA_ON_WATER, boatIsWater)
        }
        if (state.state == ykws.android.maro.data.track.TrackRecorderState.ON) {
            // Send updates periodically while recording
            while (true) {
                intent.putExtra(TrackRecordingService.EXTRA_RECORDING, true)
                // Recording-only extras
                intent.putExtra(TrackRecordingService.EXTRA_DISTANCE_NM, state.distanceNm)
                intent.putExtra(TrackRecordingService.EXTRA_ELAPSED_SEC, state.elapsedSeconds)
                intent.putExtra(TrackRecordingService.EXTRA_IDLE_SEC, state.idleDurationSec)
                intent.putExtra(TrackRecordingService.EXTRA_AVG_SPEED_KN, state.avgSpeedKn)
                intent.putExtra(TrackRecordingService.EXTRA_MAX_SPEED_KN, state.maxSpeedKn)
                intent.putExtra(TrackRecordingService.EXTRA_POINT_COUNT, state.pointCount)
                context.startService(intent)
                kotlinx.coroutines.delay(5_000L)
            }
        } else {
            // Not recording — one update to show "Ready"
            intent.putExtra(TrackRecordingService.EXTRA_RECORDING, false)
            context.startService(intent)
        }
    }

    // ── Water state push to TrackRecordingService ─────────────────────────
    // Sends a one-shot intent every time boatIsWater toggles, so the service
    // can fire the WATER_STATE_CHANGED broadcast to Tasker immediately.
    LaunchedEffect(boatIsWater) {
        val intent = Intent(context, TrackRecordingService::class.java).apply {
            action = TrackRecordingService.ACTION_UPDATE
            putExtra(TrackRecordingService.EXTRA_ON_WATER, boatIsWater)
            putExtra(TrackRecordingService.EXTRA_IS_DEMO, !appSettings.gpsMode)
        }
        context.startService(intent)
    }

    // The map centre drives BOTH layers: coastline (distance/zone) and depth-at-centre.
    val onCenterChanged: (Double, Double) -> Unit = remember(viewModel, depthViewModel, appSettings) {
        { lat, lon ->
            // In GPS auto-follow mode, scroll events are artifacts of setCenter +
            // setMapCenterOffset — they carry the offset map-center and would
            // contaminate _mapCenter. The GPS fix handler (NavVM:746) already
            // sets _mapCenter to the correct GPS position on each fix.
            // Only accept scroll updates when the user is manually panning
            // (autoFollowSuppressed) or in demo mode (no GPS driving the map).
            if (!appSettings.gpsMode || viewModel.autoFollowSuppressed.value) {
                viewModel.updateMapCenter(lat, lon)
                depthViewModel.updateMapCenter(lat, lon)
                if (!appSettings.gpsMode) {
                    viewModel.feedDemoPosition(lat, lon)
                }
            }
        }
    }

    // ── Save map position on pause (covers kill, background, minimize) ────
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    viewModel.savePosition()
                    if (trackRecorderState.state != ykws.android.maro.data.track.TrackRecorderState.ON) {
                        viewModel.setGpsActive(false)
                    }
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
    var showExitDialog by remember { mutableStateOf(false) }
    // Stop-recording confirmation (🐾 icon + menu drawer stop) — same 3-way sheet as exit.
    var showStopRecordingSheet by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = 10.dp)
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

        // ── Intercept system back when marker management is open ───────────
        if (showMarkerManagement) {
            BackHandler { showMarkerManagement = false }
        }

        // ── Otherwise require a second back press within 2 s to exit ───────
        BackHandler(enabled = !showSettings && !showTrackHistory && !showMarkerManagement && !anyFanExpanded && !trackDrawerState.isOpen) {
            val now = SystemClock.elapsedRealtime()
            val isRecording = trackRecorderState.state == ykws.android.maro.data.track.TrackRecorderState.ON
            if (now - lastBackAt <= 2_000L) {
                if (isRecording) {
                    showExitDialog = true
                } else {
                    context.stopService(Intent(context, ykws.android.maro.data.track.TrackRecordingService::class.java))
                    context.findActivity()?.finishAffinity()
                }
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

        // ── Recording-aware exit sheet (shown on double-back while recording) ──
        if (showExitDialog) {
            RecordingExitSheet(
                onSave = {
                    showExitDialog = false
                    trackViewModel.stopRecording()
                    kotlinx.coroutines.MainScope().launch {
                        kotlinx.coroutines.delay(300)
                        context.stopService(Intent(context, ykws.android.maro.data.track.TrackRecordingService::class.java))
                        context.findActivity()?.finishAffinity()
                    }
                },
                onContinue = {
                    showExitDialog = false
                    context.findActivity()?.moveTaskToBack(true)
                },
                onDiscard = {
                    showExitDialog = false
                    trackViewModel.discardRecording()
                    kotlinx.coroutines.MainScope().launch {
                        kotlinx.coroutines.delay(300)
                        context.stopService(Intent(context, ykws.android.maro.data.track.TrackRecordingService::class.java))
                        context.findActivity()?.finishAffinity()
                    }
                },
                onDismiss = { showExitDialog = false }
            )
        }

        // ── Stop-recording confirmation (🐾 icon toggle / menu drawer stop) ──
        // Same 3-way sheet as exit-while-recording; "Continue" just dismisses.
        if (showStopRecordingSheet) {
            RecordingExitSheet(
                onSave = {
                    showStopRecordingSheet = false
                    trackViewModel.stopRecording()
                },
                onContinue = {
                    showStopRecordingSheet = false
                },
                onDiscard = {
                    showStopRecordingSheet = false
                    trackViewModel.discardRecording()
                },
                onDismiss = { showStopRecordingSheet = false }
            )
        }

        // ── Main content (map + dashboard) ────────────────────────────────
        // Note: MapContent is kept at a STABLE composition slot (always a direct child of Box)
        // so the underlying MapView (AndroidView) is never recreated on orientation switch.
        // The dashboard panel is overlaid via Modifier.align() in the orientation branch.
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isLandscape = maxWidth > maxHeight
            val portraitDashboardHeight = maxWidth * 3 / 5
            val landscapeDashboardWidth = maxHeight * 100 / 100

            // ── Dynamic map offset from speed — configurable via maro.properties ──
            //     MapContent already has bottom padding equal to dashboard height,
            //     so the MapView only fills the visible area. Offset is relative
            //     to that visible height (full height in landscape).
            val visibleMapHeightDp = if (isLandscape) maxHeight
                else maxHeight - portraitDashboardHeight
            val boatFromBottomPct = appSettings.mapOffsetBoatFromBottomPct
            val maxMapShift = ((50 - boatFromBottomPct) / 100.0).coerceIn(0.0, 0.45)
            val fullOffsetSpeedKn = AppConfig.mapOffsetLookaheadMaxSpeedKn

            // Gate effective speed by mode toggle: only apply offset if the
            // corresponding toggle (GPS or Demo) is enabled for the active mode.
            val effectiveSpeedKn = when {
                appSettings.gpsMode && appSettings.mapOffsetGps -> navigationState.speedKnots
                !appSettings.gpsMode && appSettings.mapOffsetDemo -> navigationState.demoSpeedKnots
                else -> null  // offset disabled
            }

            val targetFraction = if (effectiveSpeedKn != null)
                ((effectiveSpeedKn / fullOffsetSpeedKn.toFloat()).coerceIn(0f, 1f))
            else 0f
            val animatedFraction by animateFloatAsState(
                targetValue = targetFraction,
                animationSpec = tween(durationMillis = 2000, easing = FastOutSlowInEasing),
                label = "mapOffsetFraction"
            )
            val mapCenterOffsetDp = (animatedFraction * visibleMapHeightDp.value * maxMapShift.toFloat()).dp

            // ── F2: Build synthetic unconfirmed marker for overlay preview ─────
            val createForm by markersViewModel.createForm.collectAsState()
            val drawerState by markersViewModel.drawerState.collectAsState()
            val wizardStep by markersViewModel.wizardStep.collectAsState()
            // Wizard open exits list context — back from wizard goes to map
            LaunchedEffect(wizardStep) {
                if (wizardStep != null) markerOpenedFromList = false
            }
            val unconfirmedMarker: UserMarker? = when (drawerState) {
                is MarkerDrawerState.Creating, is MarkerDrawerState.Editing -> {
                    val pos = createForm.position
                    if (pos != null) {
                        val geometry = when (createForm.type) {
                            MarkerType.PIN -> MarkerGeometry.Pin(pos)
                            MarkerType.CIRCLE -> MarkerGeometry.Circle(pos, createForm.radiusM.coerceAtLeast(1.0))
                            MarkerType.CORRIDOR -> {
                                val p2 = createForm.corridorP2
                                if (p2 != null) MarkerGeometry.Corridor(pos, p2, createForm.widthM.coerceAtLeast(1.0))
                                // During PositionP2 step, show synthetic corridor using p1 + mapCenter as p2
                                else if (wizardStep is WizardStep.PositionP2)
                                    MarkerGeometry.Corridor(pos, mapCenter, createForm.widthM.coerceAtLeast(1.0))
                                else MarkerGeometry.Pin(pos) // P1 phase: show as pin until p2 set
                            }
                        }
                        UserMarker(
                            id = "__unconfirmed__",
                            name = createForm.name.ifBlank { "New Marker" },
                            geometry = geometry,
                            description = createForm.description,
                            proximityOverrideM = createForm.proximityOverrideM.toDoubleOrNull(),
                            confirmed = false
                        )
                    } else null
                }
                else -> null
            }

            // ── F2: On edit start, animate map to the existing marker position ──
            val mapCenterRequest by markersViewModel.mapCenterRequest.collectAsState()
            LaunchedEffect(mapCenterRequest) {
                val target = mapCenterRequest ?: return@LaunchedEffect
                val mv = mapView ?: return@LaunchedEffect
                mv.controller.animateTo(org.osmdroid.util.GeoPoint(target.latitude, target.longitude))
            }

            // ── F2b: Pause auto-follow timer while any drawer is open ──
            val anyDrawerOpen = showSettings || showTrackDrawer || showTrackHistory ||
                showMarkerManagement || trackDrawerState.isOpen || trackNavigateState != null ||
                drawerState !is MarkerDrawerState.Hidden
            LaunchedEffect(anyDrawerOpen) {
                viewModel.setDrawerOpen(anyDrawerOpen)
            }

            // ── F2c: Freeze auto-follow when entering marker creation/editing wizard ──
            LaunchedEffect(drawerState) {
                if (drawerState is MarkerDrawerState.Creating || drawerState is MarkerDrawerState.Editing) {
                    viewModel.freezeFollow()
                }
            }

            // ── F2c2: Freeze auto-follow while viewing/editing a track (map is navigated away) ──
            LaunchedEffect(trackDrawerState.isOpen, trackNavigateState) {
                if (trackDrawerState.isOpen || trackNavigateState != null) {
                    viewModel.freezeFollow()
                }
            }

            // ── F3: During Creating/Editing, track map center for marker position ──
            // wizardStep intentionally NOT a key — prevents premature form overwrite
            // when recenterMapOnStep triggers map animation (mapCenter hasn't moved yet).
            // suspendTracking gate prevents animateTo intermediate values from
            // corrupting the restored marker position during edit recenter.
            val suspendTracking by markersViewModel.suspendTracking.collectAsState()
            LaunchedEffect(drawerState, mapCenter, suspendTracking) {
                if (suspendTracking) return@LaunchedEffect
                if (drawerState is MarkerDrawerState.Creating || drawerState is MarkerDrawerState.Editing) {
                    val ws = markersViewModel.wizardStep.value
                    // Track position during Position step, or set corridor P2 during PositionP2
                    when (ws) {
                        is WizardStep.Position -> {
                            markersViewModel.updateForm { it.copy(position = mapCenter) }
                        }
                        is WizardStep.PositionP2 -> {
                            markersViewModel.updateForm { it.copy(corridorP2 = mapCenter) }
                        }
                        else -> { /* no-op: position frozen outside position steps */ }
                    }
                }
            }

            // Crosshair removed — boat marker stays visible during position steps
            val showCrosshair = false

            // Map fills the box, padded to leave room for the dashboard overlay.
            // Stable composition slot — never inside an if/else branch.
            MapContent(
                state = state,
                progress = progress,
                mapCenter = mapCenter,
                isWater = isWater,
                zoomLevel = zoomLevel,
                distanceToShore = distanceToShore,
                showCrosshair = showCrosshair,
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
                onGpsModeToggle = { onGpsModeChange(!appSettings.gpsMode) },
                // Demo mode (gpsPosition == null): use mapCenter as fallback so
                // geo-fence still works when panning the map in demo/manual mode.
                boatPosition = gpsPosition ?: mapCenter,
                headingDeg = effectiveHeadingDeg,
                onCenterChanged = onCenterChanged,
                onZoomChanged = viewModel::updateZoomLevel,
                onMapViewReady = { mapView = it },
                markerLayerState = markerLayerState,
                onToggleMarkerLayer = { markersViewModel.toggleMarkerLayer() },
                onAddZone = { center -> markersViewModel.startWizard(initialPos = center) },
                onMarkerTap = { id -> markersViewModel.openEditDrawer(id) },
                onWhereAmI = {
                    val boatPos = gpsPosition ?: mapCenter
                    markersViewModel.whereAmI(boatPos)
                    // Also snapshot for track recording (MANUAL trigger)
                    val result = markersViewModel.whereAmISync(boatPos)
                    val snapshots = result.allMatches.map { it.toMarkerSnapshot() }
                    if (snapshots.isNotEmpty()) {
                        trackViewModel.addManualBoatMarker(snapshots)
                    }
                },
                onRetry = { viewModel.loadCoastline() },
                onOpenTrackDrawer = { showTrackDrawer = !showTrackDrawer },
                showTrackDrawer = showTrackDrawer,
                showTrackHistory = showTrackHistory,
                trackRecorderState = trackRecorderState,
                trackSummaries = trackSummaries,
                recoveryTrack = recoveryTrack,
                onStartRecording = {
                    val startRecordingWithBatteryCheck: () -> Unit = {
                        val prefs = context.getSharedPreferences("maro_battery_prefs", Context.MODE_PRIVATE)
                        if (!prefs.getBoolean("battery_opt_prompted", false)) {
                            showBatteryOptDialog = true
                        } else {
                            trackViewModel.startRecording()
                        }
                    }
                    // A2: Check background location permission before recording
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        val bgGranted = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                        if (!bgGranted) {
                            // Launch native permission dialog (shows "Allow all the time" option)
                            bgLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        } else {
                            startRecordingWithBatteryCheck()
                        }
                    } else {
                        startRecordingWithBatteryCheck()
                    }
                },
                onStopRecording = { showStopRecordingSheet = true },
                onViewTrackList = { showTrackHistory = true },
                onDismissTrackHistory = { showTrackHistory = false },
                onUpdateTrack = { id, name, comment, pinned ->
                    pinned?.let { trackViewModel.setPinned(id, it) }
                    trackViewModel.updateTrack(id, name, comment)
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
                autoFollowSuppressed = autoFollowSuppressed,
                onRecenter = { viewModel.recenterNow() },
                onClearTrackInfoError = { trackViewModel.clearInfoError() },
                screenLocked = screenLocked,
                onToggleScreenLock = onToggleScreenLock,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        if (isLandscape) PaddingValues(start = landscapeDashboardWidth, top = 0.dp, end = 0.dp, bottom = 0.dp)
                        else PaddingValues(start = 0.dp, top = 0.dp, end = 0.dp, bottom = portraitDashboardHeight)
                    ),
                mapCenterOffsetDp = mapCenterOffsetDp
        )

            // ── Dashboard (always rendered, Layer 0) ────────────────────────
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
                        .windowInsetsPadding(WindowInsets.statusBars)
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

            // ── Marker overlays (OSMdroid native, via LaunchedEffect) ─────
            if (markerLayerVisible) {
                val matchResult by markersViewModel.matchResult.collectAsState()
                val selectedMarkerId by markersViewModel.selectedMarkerId.collectAsState()
                MarkerOverlay(
                    markers = userMarkers,
                    mapView = mapView,
                    proximityZoneMultiplier = AppConfig.markerProximityZoneMultiplier,
                    unconfirmedMarker = unconfirmedMarker,
                    onMarkerTap = { ids -> markersViewModel.openEditDrawer(ids) },
                    matchResult = if (drawerState is MarkerDrawerState.MatchResult) matchResult else null,
                    markerZonesVisible = appSettings.markerZonesVisible || navigationZonesVisible,
                    selectedMarkerId = selectedMarkerId,
                    markerLayerState = markerLayerState,
                    highlightedMarkerId = highlightedMarkerId
                )
            }

        // ── Click-N-Move: sequential navigate flow ──────────────────────────
        LaunchedEffect(navigateToTarget) {
            val target = navigateToTarget ?: return@LaunchedEffect
            val mv = mapView ?: return@LaunchedEffect

            // 1. Animate map to marker centre
            mv.controller.animateTo(target.geoPoint, null, GPS_ANIMATION_DURATION_MS)

            // 2. Wait for animation to settle
            delay(GPS_ANIMATION_DURATION_MS + 50L)

            // 3. Run whereAmI synchronously on background thread
            val boatPos = gpsPosition ?: mapCenter
            val result = withContext(Dispatchers.Default) {
                markersViewModel.whereAmISync(boatPos)
            }

            // 4. Build navigation set: whereAmI matches + clicked marker (always included)
            val whereAmIIds = result.allMatches.map { match ->
                when (match) {
                    is ykws.android.maro.spatial.WhereAmIMatch.ZoneMatch -> match.marker.id
                    is ykws.android.maro.spatial.WhereAmIMatch.LineOfSightMatch -> match.marker.id
                }
            }
            val matchedIds = (whereAmIIds + target.markerId).distinct()

            // 5. Open drawer — use full filtered list for prev/next when opened from list
            val filteredMarkerIds = markersViewModel.markers.value.map { it.id }
            markersViewModel.openEditDrawer(filteredMarkerIds, selectedId = target.markerId, source = DrawerSource.LIST)

            navigateToTarget = null
        }

        // ── Track Click-N-Move: zoom-to-fit flow ─────────────────────────
        LaunchedEffect(trackNavigateState) {
            val state = trackNavigateState ?: return@LaunchedEffect
            val mv = mapView ?: return@LaunchedEffect

            delay(100L) // small settle after list dismiss
            mv.zoomToBoundingBox(state.bbox, true, 64)
            delay(GPS_ANIMATION_DURATION_MS + 50L)

            trackNavigateState = null
        }

        // ── Track drawer: refresh on metadata change ────────────────────
        LaunchedEffect(trackSummaries) {
            val current = trackDrawerState.track ?: return@LaunchedEffect
            val updatedSummary = trackSummaries.find { it.id == current.id } ?: return@LaunchedEffect
            if (updatedSummary.name != current.name || updatedSummary.comment != current.comment || updatedSummary.pinned != current.pinned) {
                val updated = trackViewModel.loadTrackDetailCached(current.id)
                if (updated != null) {
                    trackDrawerState = trackDrawerState.copy(track = updated)
                }
            }
        }

        // ── Track drawer: map interaction detection ──────────────────────
        val mapCenterState by viewModel.mapCenter.collectAsState()
        LaunchedEffect(mapCenterState) {
            if (trackDrawerState.isOpen && trackNavigateState == null) {
                trackDrawerState = trackDrawerState.copy(mapWasInteracted = true)
                trackOpenedFromList = false  // map interaction exits list context
            }
        }
        // ── Marker drawer: map interaction exits list context ─────────
        LaunchedEffect(mapCenterState) {
            if (drawerState is MarkerDrawerState.Viewing) {
                markerOpenedFromList = false
            }
        }

        // ── Track drawer: BackHandler close ──────────────────────────────
        if (trackDrawerState.isOpen) {
            BackHandler {
                if (!trackDrawerState.mapWasInteracted) {
                    preNavigationState?.let { pre ->
                        mapView?.controller?.setZoom(pre.zoom)
                        mapView?.controller?.setCenter(GeoPoint(pre.centerLat, pre.centerLon))
                    }
                }
                highlightedTrackId = null
                trackDrawerState = TrackDrawerState()
                preNavigationState = null
            }
        }

        // ── Layer 1: Overlay (transient drawers, Wizard, Settings, scrim) ──
        val showWizard = drawerState is MarkerDrawerState.Creating || drawerState is MarkerDrawerState.Editing
        val mgmtMarkers by markersViewModel.markers.collectAsState()

        // ── Menu chevron shortcuts: first item of the current filtered/sorted list ──
        val firstTrackId = trackSummaries.firstOrNull { !it.isLive && "t:${it.id}" !in pendingDeleteIds }?.id
        val firstMarkerId = mgmtMarkers.firstOrNull()?.id

        fun openTrackDetail(id: String, fromList: Boolean) {
            if (!appSettings.tracksVisible) {
                viewModel.updateSettings { it.copy(tracksVisible = true) }
            }
            if (fromList) {
                trackOpenedFromList = true
                trackListScrollState = SavedScrollState(
                    trackListState.firstVisibleItemIndex,
                    trackListState.firstVisibleItemScrollOffset
                )
            }
            showTrackHistory = false
            trackScope.launch {
                try {
                    val track = trackViewModel.loadTrackDetailCached(id)
                    if (track == null || track.trackPoints.isEmpty()) return@launch

                    val targetPoint = computeTrackNavigateTarget(track)
                    val geoPoint = GeoPoint(targetPoint.first, targetPoint.second)

                    val bbox = if (track.trackPoints.size >= 2) {
                        org.osmdroid.util.BoundingBox(
                            track.trackPoints.maxOf { it.lat },
                            track.trackPoints.maxOf { it.lon },
                            track.trackPoints.minOf { it.lat },
                            track.trackPoints.minOf { it.lon }
                        )
                    } else null

                    preNavigationState = mapView?.let { mv ->
                        val c = mv.mapCenter
                        PreNavigationState(mv.zoomLevelDouble, c.latitude, c.longitude)
                    }

                    highlightedTrackId = id
                    trackDrawerState = TrackDrawerState(
                        isOpen = true,
                        track = track,
                        mapWasInteracted = false
                    )

                    if (bbox != null) {
                        trackNavigateState = TrackNavigateState(geoPoint, bbox, id)
                    } else {
                        // Single-point track: just animate, no bounding box zoom
                        mapView?.controller?.animateTo(geoPoint, null, GPS_ANIMATION_DURATION_MS)
                    }
                } catch (_: Exception) {
                    // Silently fail — track data unavailable
                }
            }
        }

        fun openMarkerDetail(id: String, fromList: Boolean) {
            val marker = mgmtMarkers.find { it.id == id } ?: return
            navigationZonesVisible = true
            markersViewModel.showLayer()
            highlightedMarkerId = id
            if (fromList) {
                markerOpenedFromList = true
                markerListScrollState = SavedScrollState(
                    markerListState.firstVisibleItemIndex,
                    markerListState.firstVisibleItemScrollOffset
                )
            }
            showMarkerManagement = false
            navigateToTarget = NavigateTarget(
                geoPoint = GeoPoint(marker.centerPoint.latitude, marker.centerPoint.longitude),
                markerId = id
            )
        }

        OverlayLayer(
            showSettings = showSettings,
            showTrackDrawer = showTrackDrawer,
            showTrackHistory = showTrackHistory,
            showMarkerManagement = showMarkerManagement,
            showWizard = showWizard,
            wizardStep = wizardStep,
            drawerState = drawerState,
            isLandscape = isLandscape,
            portraitDashboardHeight = portraitDashboardHeight,
            landscapeDashboardWidth = landscapeDashboardWidth,
            onDismissSettings = { showSettings = false },
            onDismissMenu = { showTrackDrawer = false },
            onDismissTrackHistory = { showTrackHistory = false },
            onDismissMarkerManagement = { showMarkerManagement = false },
            onWizardCancel = { markersViewModel.wizardCancel() },
            onMarkerDrawerClose = {
                navigationZonesVisible = false
                highlightedMarkerId = null
                markersViewModel.closeDrawer()
                if (markerOpenedFromList) {
                    markerOpenedFromList = false
                    showMarkerManagement = true
                }
            },
            onOpenTrackHistoryFromMenu = { trackOpenedFromList = false; showTrackHistory = true },
            onOpenMarkerManagementFromMenu = { markerOpenedFromList = false; showMarkerManagement = true },
            onOpenSettingsFromMenu = { showSettings = true },
            onOpenFirstTrack = { id -> openTrackDetail(id, fromList = false) },
            onOpenFirstMarker = { id -> openMarkerDetail(id, fromList = false) },
            markersViewModel = markersViewModel,
            trackViewModel = trackViewModel,
            gpsMode = appSettings.gpsMode,
            onGpsModeChange = onGpsModeChange,
            gpsToggleColor = gpsToggleColor,
            markerZonesVisible = appSettings.markerZonesVisible,
            onToggleMarkerZones = {
                Log.d("MaroMapRefresh", "MenuDrawer toggle: markerZonesVisible ${appSettings.markerZonesVisible} -> ${!appSettings.markerZonesVisible}")
                viewModel.updateSettings { it.copy(markerZonesVisible = !appSettings.markerZonesVisible) }
                mapView?.invalidate()
            },
            firstTrackId = firstTrackId,
            firstMarkerId = firstMarkerId,
            onTrackAction = { action ->
                when (action) {
                    is ykws.android.maro.data.model.ListAction.NavigateToItem -> openTrackDetail(action.id, fromList = true)
                    is ykws.android.maro.data.model.ListAction.ExportGpx -> shareTrackGpx(context, trackViewModel, action.id, trackScope)
                    is ykws.android.maro.data.model.ListAction.BatchExportGpx -> shareTracksZip(context, trackViewModel, action.ids, trackScope)
                    is ykws.android.maro.data.model.ListAction.ImportTracks -> importLauncher?.launch(arrayOf("application/gpx+xml", "application/zip", "*/*"))
                    is ykws.android.maro.data.model.ListAction.PermanentDelete -> trackViewModel.deleteTrack(action.id)
                    is ykws.android.maro.data.model.ListAction.RefreshList -> trackViewModel.refreshSummaries(action.sortState, reloadFromDisk = false)
                    is ykws.android.maro.data.model.ListAction.RefreshLayer -> mapView?.invalidate()
                    else -> {}
                }
            },
            trackSortState = appSettings.trackListSort,
            onTrackSortStateChange = { newState ->
                viewModel.updateSettings { it.copy(trackListSort = newState) }
                trackViewModel.refreshSummaries(newState, reloadFromDisk = false)
                mapView?.invalidate()
            },
            trackFilterState = appSettings.trackListFilter,
            onTrackFilterChange = { newFilter ->
                viewModel.updateSettings { it.copy(trackListFilter = newFilter) }
                trackViewModel.refreshSummaries(filter = newFilter, reloadFromDisk = false)
            },
            onTrackReset = {
                val resetFilter = ykws.android.maro.data.model.ListFilter()
                viewModel.updateSettings { it.copy(trackListSort = ykws.android.maro.data.model.ListSortState(), trackListFilter = resetFilter) }
                trackViewModel.refreshSummaries(filter = resetFilter, reloadFromDisk = false)
                mapView?.invalidate()
            },
            appSettings = appSettings,
            onUpdateSettings = viewModel::updateSettings,
            selectedTab = selectedTab,
            onTabChange = { selectedTab = it },
            displayScrollState = displayScrollState,
            navigationScrollState = navigationScrollState,
            systemScrollState = systemScrollState,
            onRegenerateRasters = { steps ->
                val waterTest: (Double, Double) -> Boolean =
                    if (state is CoastlineState.Ready) viewModel::isOnWater else { _, _ -> false }
                depthViewModel.generateRasterLayers(context, steps, appSettings, waterTest)
            },
            boatPosition = gpsPosition ?: mapCenter,
            markers = mgmtMarkers,
            onMarkerAction = { action ->
                when (action) {
                    is ykws.android.maro.data.model.ListAction.NavigateToItem -> openMarkerDetail(action.id, fromList = true)
                    is ykws.android.maro.data.model.ListAction.EditItem -> {
                        showMarkerManagement = false
                        markerOpenedFromList = false
                        markersViewModel.startWizard(action.id)
                    }
                    is ykws.android.maro.data.model.ListAction.PermanentDelete -> markersViewModel.deleteMarker(action.id)
                    is ykws.android.maro.data.model.ListAction.RefreshList -> markersViewModel.refreshSort(action.sortState)
                    else -> {}
                }
            },
            // ── Track info drawer ─────────────────────────────────────────
            showTrackInfoDrawer = trackDrawerState.isOpen,
            trackInfoDrawerData = trackDrawerState.track,
            onTrackDrawerClose = {
                if (!trackDrawerState.mapWasInteracted) {
                    preNavigationState?.let { pre ->
                        mapView?.controller?.setZoom(pre.zoom)
                        mapView?.controller?.setCenter(GeoPoint(pre.centerLat, pre.centerLon))
                    }
                }
                highlightedTrackId = null
                trackDrawerState = TrackDrawerState()
                preNavigationState = null
                if (trackOpenedFromList) {
                    trackOpenedFromList = false
                    showTrackHistory = true
                }
            },
            onNavigateToTrack = { id -> openTrackDetail(id, fromList = true) },
            markerSortState = appSettings.markerListSort,
            onMarkerSortStateChange = { newState ->
                viewModel.updateSettings { it.copy(markerListSort = newState) }
                markersViewModel.refreshSort(newState)
            },
            markerFilterState = appSettings.markerListFilter,
            onMarkerFilterChange = { newFilter ->
                android.util.Log.d("MaroMapRefresh", "onMarkerFilterChange: $newFilter")
                viewModel.updateSettings { it.copy(markerListFilter = newFilter) }
                markersViewModel.refreshSort(filter = newFilter)
            },
            onMarkerReset = {
                android.util.Log.d("MaroMapRefresh", "onMarkerReset")
                val resetFilter = ykws.android.maro.data.model.ListFilter()
                viewModel.updateSettings { it.copy(markerListSort = ykws.android.maro.data.model.ListSortState(), markerListFilter = resetFilter) }
                markersViewModel.refreshSort(filter = resetFilter)
            },
            onCreateFirst = {
                showMarkerManagement = false
                markersViewModel.startWizard(initialPos = mapCenter)
            },
            onSetIcon = { id, icon -> markersViewModel.setMarkerIcon(id, icon) },
            onToggleMarkerPin = { id, _ -> markersViewModel.togglePin(id) },
            onUpdateMarkerText = { id, name, desc -> markersViewModel.updateMarkerText(id, name, desc) },
            onMergeMarkers = { ids, name, keep -> markersViewModel.mergeAutoMarkers(ids, name, keep) },
            // ── List-detail navigation ──────────────────────────────────
            trackListIds = trackSummaries.filter { !it.isLive && "t:${it.id}" !in pendingDeleteIds }.map { it.id },
            currentTrackIndex = trackSummaries.filter { !it.isLive && "t:${it.id}" !in pendingDeleteIds }.map { it.id }.indexOf(trackDrawerState.track?.id ?: "").coerceAtLeast(0),
            onTrackPrev = {
                val ids = trackSummaries.filter { !it.isLive && "t:${it.id}" !in pendingDeleteIds }.map { it.id }
                val idx = ids.indexOf(trackDrawerState.track?.id ?: "")
                if (idx > 0) {
                    openFirstValidTrack(ids.subList(0, idx).asReversed()) { }
                }
            },
            onTrackNext = {
                val ids = trackSummaries.filter { !it.isLive && "t:${it.id}" !in pendingDeleteIds }.map { it.id }
                val idx = ids.indexOf(trackDrawerState.track?.id ?: "")
                if (idx >= 0 && idx < ids.lastIndex) {
                    openFirstValidTrack(ids.subList(idx + 1, ids.size)) { }
                }
            },
            onShareTrack = { id -> shareTrackGpx(context, trackViewModel, id, trackScope) },
            onDeleteTrack = { id ->
                val track = trackDrawerState.track
                enqueueSnack(ActiveSnack.TrackDelete(id, track?.name ?: "Unknown"))
                pendingDeleteIds.add("t:$id")
                // Advance to adjacent track: next → previous, skipping pending + empty tracks.
                val fullIds = trackSummaries.filter { !it.isLive }.map { it.id }
                val i = fullIds.indexOf(id)
                val after = if (i >= 0) {
                    fullIds.subList(i + 1, fullIds.size).filter { "t:$it" !in pendingDeleteIds }
                } else emptyList()
                val before = if (i >= 0) {
                    fullIds.subList(0, i).asReversed().filter { "t:$it" !in pendingDeleteIds }
                } else emptyList()
                openFirstValidTrack(after + before) {
                    highlightedTrackId = null
                    trackDrawerState = TrackDrawerState()
                    preNavigationState = null
                }
            },
            onTrackMetadataChanged = {},
            onRequestMarkerDelete = { id, name ->
                val selection = markersViewModel.selectedMarkerIds.value
                val source = markersViewModel.drawerSource
                enqueueSnack(ActiveSnack.MarkerDelete(id, name, selection, source))
                pendingDeleteIds.add("m:$id")
                // Advance to adjacent marker: next → previous → close. Map highlight follows the target.
                val i = selection.indexOf(id)
                var targetIdx = -1
                var j = i + 1
                while (j < selection.size && targetIdx < 0) {
                    if ("m:${selection[j]}" !in pendingDeleteIds) targetIdx = j else j++
                }
                if (targetIdx < 0) {
                    j = i - 1
                    while (j >= 0 && targetIdx < 0) {
                        if ("m:${selection[j]}" !in pendingDeleteIds) targetIdx = j else j--
                    }
                }
                if (targetIdx >= 0) {
                    val targetId = selection[targetIdx]
                    val filtered = selection.filter { "m:$it" !in pendingDeleteIds }
                    markersViewModel.openEditDrawer(filtered, selectedId = targetId, source = source)
                    highlightedMarkerId = targetId
                } else {
                    navigationZonesVisible = false
                    highlightedMarkerId = null
                    markersViewModel.closeDrawer()
                }
            },
            trackListState = trackListState,
            markerListState = markerListState,
            trackRestoredScrollState = trackListScrollState,
            markerRestoredScrollState = markerListScrollState,
        )

        // ── Post-save undo → stack entry ────────────────────────────────
        val lastSavedId by markersViewModel.lastSavedMarkerId.collectAsState()
        LaunchedEffect(lastSavedId) {
            val id = lastSavedId ?: return@LaunchedEffect
            val savedMarker = userMarkers.find { it.id == id }
            enqueueSnack(ActiveSnack.CreateUndo(id, savedMarker?.name ?: "Unknown"))
        }

        // ── Vertical snackbar stack at the bottom of the map area ────────
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(
                        bottom = if (isLandscape) 0.dp else portraitDashboardHeight,
                        start = if (isLandscape) landscapeDashboardWidth else 0.dp
                    )
                    .padding(start = 12.dp, end = RIGHT_CONTROL_COLUMN_INSET),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                activeSnacks.forEach { snack ->
                    androidx.compose.runtime.key(snack.uid) {
                        SnackRow(
                            message = when (snack) {
                                is ActiveSnack.TrackDelete -> "Track '${snack.name}' deleted"
                                is ActiveSnack.MarkerDelete -> "Marker '${snack.name}' deleted"
                                is ActiveSnack.CreateUndo -> "Marker \"${snack.name}\" created"
                            },
                            snackKey = snack.uid,
                            onUndo = { onSnackUndo(snack) },
                            onTimeout = { onSnackTimeout(snack) }
                        )
                    }
                }
            }
        }

        // ── Process-death recovery dialog ─────────────────────────────
        recoveryTrack?.let { track ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { trackViewModel.saveOrphanedCheckpoint(track) },
                title = { androidx.compose.material3.Text(stringResource(R.string.recovery_title)) },
                text = { androidx.compose.material3.Text(
                    stringResource(R.string.recovery_found, track.name)
                ) },
                confirmButton = {
                    androidx.compose.material3.TextButton(
                        onClick = { trackViewModel.resumeOrphanedCheckpoint(track) }
                    ) { androidx.compose.material3.Text(stringResource(R.string.recovery_continue)) }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(
                        onClick = { trackViewModel.saveOrphanedCheckpoint(track) }
                    ) { androidx.compose.material3.Text(stringResource(R.string.recovery_save)) }
                }
            )
        }

        // ── Background location permission dialog (A2) ──────────────────
        if (showBgLocationDialog) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showBgLocationDialog = false },
                title = { androidx.compose.material3.Text(stringResource(R.string.bg_location_title)) },
                text = { androidx.compose.material3.Text(stringResource(R.string.bg_location_message)) },
                confirmButton = {
                    androidx.compose.material3.TextButton(
                        onClick = {
                            showBgLocationDialog = false
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        }
                    ) { androidx.compose.material3.Text(stringResource(R.string.bg_location_open_settings)) }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(
                        onClick = { showBgLocationDialog = false }
                    ) { androidx.compose.material3.Text(stringResource(R.string.bg_location_not_now)) }
                }
            )
        }

        // ── GPS permission-missing dialog (shown once per missing-permission episode) ──
        if (gpsPermissionMissing && !gpsPermissionDialogDismissed && appSettings.gpsMode) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { gpsPermissionDialogDismissed = true },
                title = { androidx.compose.material3.Text(stringResource(R.string.gps_permission_title)) },
                text = { androidx.compose.material3.Text(stringResource(R.string.gps_permission_message)) },
                confirmButton = {
                    androidx.compose.material3.TextButton(
                        onClick = {
                            gpsPermissionDialogDismissed = true
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        }
                    ) { androidx.compose.material3.Text(stringResource(R.string.bg_location_open_settings)) }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(
                        onClick = { gpsPermissionDialogDismissed = true }
                    ) { androidx.compose.material3.Text(stringResource(R.string.bg_location_not_now)) }
                }
            )
        }

        // ── GPS source-switch confirmation while recording (bottom sheet, dashboard space) ──
        pendingGpsModeToggle?.let { enable ->
            ConfirmSheet(
                title = stringResource(R.string.gps_switch_confirm_title),
                message = stringResource(R.string.gps_switch_confirm_message),
                confirmLabel = stringResource(R.string.gps_switch_confirm_action),
                isDestructive = false,
                onConfirm = {
                    pendingGpsModeToggle = null
                    applyGpsMode(enable)
                },
                onDismiss = { pendingGpsModeToggle = null }
            )
        }

        // ── Battery optimization dialog (A4, triggered on recording start) ──
        if (showBatteryOptDialog) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = {
                    showBatteryOptDialog = false
                    context.getSharedPreferences("maro_battery_prefs", Context.MODE_PRIVATE)
                        .edit().putBoolean("battery_opt_prompted", true).apply()
                },
                title = { androidx.compose.material3.Text(stringResource(R.string.battery_opt_title)) },
                text = { androidx.compose.material3.Text(stringResource(R.string.battery_opt_message)) },
                confirmButton = {
                    androidx.compose.material3.TextButton(
                        onClick = {
                            showBatteryOptDialog = false
                            context.getSharedPreferences("maro_battery_prefs", Context.MODE_PRIVATE)
                                .edit().putBoolean("battery_opt_prompted", true).apply()
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                            trackViewModel.startRecording()
                        }
                    ) { androidx.compose.material3.Text(stringResource(R.string.battery_opt_open_settings)) }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(
                        onClick = {
                            showBatteryOptDialog = false
                            context.getSharedPreferences("maro_battery_prefs", Context.MODE_PRIVATE)
                                .edit().putBoolean("battery_opt_prompted", true).apply()
                            trackViewModel.startRecording()
                        }
                    ) { androidx.compose.material3.Text(stringResource(R.string.battery_opt_not_now)) }
                }
            )
        }

        // ── Screen lock: full-screen input scrim + top-most unlock button ──
        //     The scrim consumes every pointer event so nothing below it (map,
        //     dashboard, drawers, controls) receives touch while locked. The
        //     duplicate button sits above the scrim so the lock can be toggled off.
        val lockTopInset = with(LocalDensity.current) {
            val raw = WindowInsets.statusBars.getTop(this).toDp()
            if (isLandscape) raw else (raw - 6.dp).coerceAtLeast(0.dp)
        }
        if (screenLocked) {
            LockScrim()
        }
        // Locked-overlay controls sit inside the map area: mirror MapContent's
        // dashboard padding (portrait: bottom; landscape: start) so the duplicate
        // lock button, zoom controls, and banner align over the originals.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    if (isLandscape)
                        PaddingValues(start = landscapeDashboardWidth, top = 0.dp, end = 0.dp, bottom = 0.dp)
                    else
                        PaddingValues(start = 0.dp, top = 0.dp, end = 0.dp, bottom = portraitDashboardHeight)
                )
        ) {
            if (screenLocked) {
                LockScreenButton(
                    locked = true,
                    onClick = onToggleScreenLock,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = lockTopInset, start = 6.dp + (44.dp + 6.dp) * 3)
                )
                ZoomControls(
                    onZoomIn = {
                        mapView?.let { mv ->
                            mv.controller.zoomIn()
                            viewModel.updateZoomLevel(mv.zoomLevelDouble)
                        }
                    },
                    onZoomOut = {
                        mapView?.let { mv ->
                            mv.controller.zoomOut()
                            viewModel.updateZoomLevel(mv.zoomLevelDouble)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 6.dp, bottom = 6.dp),
                    doubleTap = true
                )
            }
            if (lockBanner != null) {
                LockBanner(
                    locked = lockBanner == true,
                    modifier = Modifier.align(Alignment.BottomStart)
                )
            }
        }
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
        val safeName = track.name.replace(Regex("""[/\\:*?"<>|]"""), "_").take(100)
        val gpxFile = java.io.File(context.filesDir, "tracks/$safeName.gpx")
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

/**
 * Zip multiple track GPX files and share via Android's share intent.
 */
private fun shareTracksZip(
    context: android.content.Context,
    trackViewModel: ykws.android.maro.data.track.TrackViewModel,
    trackIds: Set<String>,
    scope: kotlinx.coroutines.CoroutineScope
) {
    scope.launch {
        val timestamp = java.text.SimpleDateFormat("yyyy_MM_dd_HHmmss", java.util.Locale.US).format(java.util.Date())
        val zipFile = java.io.File(context.filesDir, "tracks/maro-tracks-$timestamp.zip")
        zipFile.parentFile?.mkdirs()
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            java.util.zip.ZipOutputStream(java.io.BufferedOutputStream(java.io.FileOutputStream(zipFile))).use { zos ->
                trackIds.forEach { id ->
                    val track = trackViewModel.loadTrackDetail(id)
                    if (track != null) {
                        val gpx = track.toGpx()
                        val safeName = track.name.replace(Regex("""[/\\:*?"<>|]"""), "_").take(100)
                        val entry = java.util.zip.ZipEntry("$safeName.gpx")
                        entry.time = track.startTimeMs
                        zos.putNextEntry(entry)
                        zos.write(gpx.toByteArray())
                        zos.closeEntry()
                    }
                }
            }
        }
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            zipFile
        )
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(intent, "Share Tracks"))
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
    markerLayerState: MarkerLayerState = MarkerLayerState.SHOW_ALL,
    onToggleMarkerLayer: () -> Unit = {},
    onAddZone: (LatLng) -> Unit = {},
    onMarkerTap: (String) -> Unit = {},
    onWhereAmI: () -> Unit = {},
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
    onGpsModeToggle: () -> Unit = {},
    isLandscape: Boolean = false,
    expandedFanId: ControlId? = null,
    onDismissFan: () -> Unit = {},
    onToggleFan: (ControlId) -> Unit = {},
    showExitBanner: Boolean,
    rasterProgress: RasterProgress? = null,
    showCrosshair: Boolean = false,
    autoFollowSuppressed: Boolean = false,
    onRecenter: () -> Unit = {},
    onClearTrackInfoError: () -> Unit = {},
    screenLocked: Boolean = false,
    onToggleScreenLock: () -> Unit = {},
    modifier: Modifier = Modifier,
    mapCenterOffsetDp: Dp = 0.dp,
) {
    Box(modifier = modifier.clipToBounds()) {
        // ── Compute top inset: full statusBars in landscape, -6dp in portrait ──
        val density = LocalDensity.current
        val topInset = with(density) {
            val raw = WindowInsets.statusBars.getTop(this).toDp()
            if (isLandscape) raw else (raw - 6.dp).coerceAtLeast(0.dp)
        }
        val centerOffsetYPx = with(density) { mapCenterOffsetDp.roundToPx() }

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
            onCenterChanged = onCenterChanged,
            onZoomChanged = onZoomChanged,
            onMapViewReady = onMapViewReady,
            modifier = Modifier.fillMaxSize(),
            centerOffsetYPx = centerOffsetYPx
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
            DirectionLine(
                modifier = Modifier.fillMaxSize(),
                centerOffsetYDp = mapCenterOffsetDp
            )
        }

        CapArrowOverlay(
            zoomLevel = zoomLevel,
            navigationState = navigationState,
            showCapArrow = appSettings.capArrowVisible,
            modifier = Modifier.fillMaxSize(),
            centerOffsetYDp = mapCenterOffsetDp
        )

        CenterMarkerOverlay(
            isWater = isWater,
            zoomLevel = zoomLevel,
            distanceToShore = distanceToShore,
            showCrosshair = showCrosshair,
            onClick = { onWhereAmI() },
            modifier = Modifier.align(Alignment.Center),
            centerOffsetYDp = mapCenterOffsetDp
        )

        // ── Layer 1: 2-column overlay row (left fills, right content-sized) ──
        Row(modifier = Modifier.fillMaxSize()) {

            // ── LEFT COLUMN: top + middle + btm ──────────────────────────
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {

                // top zone: Earth, Track, GPS, Recenter (statusBars minus 6dp)
                Row(
                    modifier = Modifier
                        .padding(top = topInset, start = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GpsStatusIcon(
                        state = gpsIconState,
                        onClick = onGpsModeToggle
                    )
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
                    LockScreenButton(
                        locked = screenLocked,
                        onClick = onToggleScreenLock
                    )
                    if (appSettings.gpsMode && autoFollowSuppressed) {
                        RecenterButton(onClick = onRecenter)
                    }
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
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(start = 6.dp, end = 6.dp),
                        contentAlignment = Alignment.CenterStart
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
                        // Track info error (from populate-track-info)
                        val trackInfoError = trackRecorderState.infoError
                        if (trackInfoError != null) {
                            ErrorOverlay(
                                message = trackInfoError,
                                onRetry = onClearTrackInfoError
                            )
                        }
                    }

                    // Top layer: exit toast (conditional)
                    if (showExitBanner) {
                        val isRecording = trackRecorderState.state == ykws.android.maro.data.track.TrackRecorderState.ON
                        val borderColor = if (isRecording)
                            ComposeColor(AppConfig.uiDashboardZoneDanger)
                        else
                            ComposeColor(AppConfig.uiDashboardBackground)
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .padding(start = 6.dp, end = 6.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = ComposeColor(AppConfig.buttonActionBgColor),
                                shadowElevation = 8.dp,
                                modifier = Modifier.border(2.dp, borderColor, RoundedCornerShape(14.dp))
                            ) {
                                Box(modifier = Modifier.background(ComposeColor(AppConfig.uiCardBackground))) {
                                    Text(
                                    text = if (isRecording)
                                        stringResource(R.string.exit_press_back_again_recording)
                                    else
                                        stringResource(R.string.exit_press_back_again),
                                    color = ComposeColor(AppConfig.uiSettingsToastText),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                                    )
                                }
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
                                currentCount = 6,
                                direction = FanDirection.LEFT,
                                isOpen = isExpanded,
                                toggleChildren = true,
                                showActiveBadge = true,
                                activeChildCount = listOf(
                                    markerLayerState != MarkerLayerState.HIDDEN,
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
                                { isActive -> LocationOnIcon(alpha = if (isActive) ButtonColors.activeAlpha else ButtonColors.inactiveAlpha) },
                                { isActive -> TrackLayerIcon(alpha = if (isActive) ButtonColors.activeAlpha else ButtonColors.inactiveAlpha) },
                                { isActive -> DepthBarIcon(alpha = if (isActive) ButtonColors.activeAlpha else ButtonColors.inactiveAlpha) },
                                { isActive -> RegulatedZoneIcon(alpha = if (isActive) ButtonColors.activeAlpha else ButtonColors.inactiveAlpha) },
                                { isActive -> DoubleCircleIcon(alpha = if (isActive) ButtonColors.activeAlpha else ButtonColors.inactiveAlpha) },
                                { isActive -> WarningTriangleIcon(alpha = if (isActive) ButtonColors.activeAlpha else ButtonColors.inactiveAlpha) }
                            ),
                            activeStates = listOf(
                                markerLayerState != MarkerLayerState.HIDDEN,
                                appSettings.tracksVisible,
                                appSettings.depthLayerVisible,
                                appSettings.regulatedZonesVisible,
                                appSettings.zone300Visible,
                                appSettings.lowDepthWarningVisible
                            ),
                            onChildClick = { index: Int, _: Boolean ->
                                when (index) {
                                    0 -> onToggleMarkerLayer()
                                    1 -> onToggleTracks()
                                    2 -> onToggleDepthLayer()
                                    3 -> onToggleRegulatedZones()
                                    4 -> onToggleZone300()
                                    5 -> onToggleLowDepthWarning()
                                }
                            }
                        )
                    }

                    // Add Zone button (same size/style as FanLayout buttons, opens wizard at TypeSelect)
                    Spacer(modifier = Modifier.height(6.dp))
                    MapControlButton(
                        onClick = { onAddZone(mapCenter) },
                        modifier = Modifier.alpha(if (anyFanOpen) 0f else 1f)
                    ) {
                        AddLocationAltIcon()
                    }
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
                        ZoomControls(
                            onZoomIn = {
                                mapView?.let { mv ->
                                    mv.controller.zoomIn()
                                    onZoomChanged(mv.zoomLevelDouble)
                                }
                            },
                            onZoomOut = {
                                mapView?.let { mv ->
                                    mv.controller.zoomOut()
                                    onZoomChanged(mv.zoomLevelDouble)
                                }
                            }
                        )
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

// ── Error overlay ───────────────────────────────────────────────────────────

@Composable
private fun ErrorOverlay(
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
    showCrosshair: Boolean = false,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    centerOffsetYDp: Dp = 0.dp,
) {
    // ── Crosshair mode: replace boat/dot with a target icon during position-step wizard ──
    if (showCrosshair) {
        val baseDp = 32.0
        val scaleFactor = 2.0.pow(ZOOM_EXPONENT * (zoomLevel - REF_ZOOM))
        val finalSizeDp = (baseDp * scaleFactor).dp

        Box(
            modifier = modifier
                .size(if (finalSizeDp < 48.dp) 48.dp else finalSizeDp)
                .offset(y = centerOffsetYDp)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "\u2295",
                fontSize = (finalSizeDp.value / 1.5f).sp,
                color = ComposeColor(AppConfig.uiSettingsAccent),
                textAlign = TextAlign.Center,
            )
        }
        return
    }

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
    //
    // Touch target is always at least 48dp (button-sized) even when the visual
    // marker is small at low zoom levels.
    val touchSizeDp = if (finalSizeDp < 48.dp) 48.dp else finalSizeDp
    Box(
        modifier = modifier
            .size(touchSizeDp)
            .offset(y = centerOffsetYDp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // ── Boat/land marker ──────────────────────────────────────────────
        val yOffset = if (isWater) finalSizeDp / 2 else 0.dp
        Image(
            painter = painterResource(id = drawableId),
            contentDescription = description,
            modifier = Modifier
                .size(finalSizeDp)
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
    modifier: Modifier = Modifier,
    centerOffsetYDp: Dp = 0.dp,
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
        val midY = size.height / 2 + centerOffsetYDp.toPx()
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
    modifier: Modifier = Modifier,
    centerOffsetYDp: Dp = 0.dp,
) {
    val lineColor = ComposeColor(AppConfig.mapNavigationLineColor)
    Canvas(modifier = modifier) {
        val cX = size.width / 2
        val cY = size.height / 2 + centerOffsetYDp.toPx()

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
 */
@Composable
private fun HamburgerIcon() {
    Icon(
        imageVector = Icons.Filled.Menu,
        contentDescription = stringResource(R.string.cd_menu),
        tint = ButtonColors.icon,
        modifier = Modifier.size(36.dp)
    )
}

/**
 * 5-state GPS indicator icon — leftmost in the top-left status row.
 *
 * States match the derived [GpsIconState] enum:
 * - [DEMO]: GPS toggle off, gray satellite outline
 * - [ACQUIRING]: GPS on but no fix yet, amber background + pulsing dot
 * - [HEALTHY]: GPS fix good, green background
 * - [IDLE]: GPS fix but stationary (reduced cadence), cyan background
 * - [STALE]: GPS lost / hasLock false / error, red background
 */
private enum class GpsIconState { DEMO, ACQUIRING, HEALTHY, IDLE, STALE, ESTIMATING, WEAK }

@Composable
private fun GpsStatusIcon(
    state: GpsIconState,
    onClick: () -> Unit,
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
        GpsIconState.ESTIMATING -> { baseColor = ComposeColor(AppConfig.statusGpsEstimating); bgAlpha = AppConfig.statusGpsAlphaActive; contentAlpha = 1f }
        GpsIconState.WEAK -> { baseColor = ComposeColor(AppConfig.statusGpsAcquiring); bgAlpha = AppConfig.statusGpsAlphaActive; contentAlpha = 1f }
    }
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(baseColor.copy(alpha = bgAlpha))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "📡",
            fontSize = 22.sp,
            modifier = if (contentAlpha < 1f) Modifier.alpha(contentAlpha) else Modifier
        )
    }
}

/**
 * Recenter button — appears in the top-left status row when the map is frozen
 * (auto-follow suppressed by pan, drawer, or wizard). Tapping immediately
 * smooth-scrolls back to the GPS position.
 */
@Composable
private fun RecenterButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(ComposeColor(0xFF2196F3).copy(alpha = 0.30f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "📍",
            fontSize = 22.sp
        )
    }
}

/**
 * Screen-lock toggle — leftmost in the top-left status row. 📱 (unlocked,
 * dimmed inactive) ↔ 🔒 (locked, caution/amber active). Matches the
 * GpsStatusIcon / TrackStatusIcon recipe: 44dp box, 8dp radius, 22sp emoji.
 */
@Composable
private fun LockScreenButton(
    locked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val baseColor = if (locked) ComposeColor(AppConfig.statusLockOn) else ComposeColor(AppConfig.statusLockOff)
    val bgAlpha = if (locked) AppConfig.statusLockAlphaActive else AppConfig.statusLockAlphaDimmed
    val contentAlpha = if (locked) 1f else 0.50f
    val cd = stringResource(if (locked) R.string.cd_unlock_screen else R.string.cd_lock_screen)
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(baseColor.copy(alpha = bgAlpha))
            .clickable(onClick = onClick)
            .semantics { contentDescription = cd }
            .alpha(contentAlpha),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "\uD83D\uDCF5",
            fontSize = 22.sp
        )
    }
}

/**
 * Full-screen transparent input blocker shown when the screen is locked.
 * Consumes every pointer event (tap, drag, pinch) so nothing below it —
 * osmdroid map, dashboard, drawers, controls — receives touch.
 */
@Composable
private fun LockScrim(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                    }
                }
            }
    )
}

/**
 * Zoom +/− control pair — two 64dp buttons with a 6dp gap. Shared by the normal
 * right column and the locked-screen overlay so both stay identical.
 *
 * @param doubleTap When true, each button zooms only on a double-tap (single
 *                  splash taps are ignored); when false, normal single-tap.
 */
@Composable
private fun ZoomControls(
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    modifier: Modifier = Modifier,
    doubleTap: Boolean = false
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ZoomButton(onClick = onZoomIn, doubleTap = doubleTap) { PlusIcon() }
        ZoomButton(onClick = onZoomOut, doubleTap = doubleTap) { MinusIcon() }
    }
}

@Composable
private fun ZoomButton(
    onClick: () -> Unit,
    doubleTap: Boolean,
    icon: @Composable () -> Unit
) {
    if (doubleTap) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(ButtonColors.bg)
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { onClick() })
                },
            contentAlignment = Alignment.Center
        ) {
            icon()
        }
    } else {
        MapControlButton(onClick = onClick) { icon() }
    }
}

/**
 * Lock/unlock feedback banner — reuses the generic exit-toast style (rounded
 * Surface, 2dp border, card background) at the bottom-left of the map, left of
 * the right-edge control column. Non-interactive.
 */
@Composable
private fun LockBanner(
    locked: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 6.dp, end = RIGHT_CONTROL_COLUMN_INSET),
        contentAlignment = Alignment.CenterStart
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = ComposeColor(AppConfig.buttonActionBgColor),
            shadowElevation = 8.dp,
            modifier = Modifier.border(2.dp, ComposeColor(AppConfig.uiDashboardBackground), RoundedCornerShape(14.dp))
        ) {
            Box(modifier = Modifier.background(ComposeColor(AppConfig.uiCardBackground))) {
                Text(
                    text = stringResource(
                        if (locked) R.string.toast_screen_locked else R.string.toast_screen_unlocked
                    ),
                    color = ComposeColor(AppConfig.uiSettingsToastText),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }
    }
}

// ── Settings overlay (full-screen page) ─────────────────────────────────────

// Tab definitions for the settings page.
private val settingsTabLabels = listOf("General", "Navigation", "System")

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SettingsOverlay(
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
                .background(ComposeColor(AppConfig.uiCardBackground))
                .padding(vertical = 8.dp)
        ) {
            // Toggle row (inline)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
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

            // Warning sliders — always visible, persisted expander
            Spacer(Modifier.height(8.dp))
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                SettingsExpander(
                    label = "Warning settings",
                    expanded = settings.lowDepthWarningSettingsExpanded,
                    onToggle = { onUpdateSettings { it.copy(lowDepthWarningSettingsExpanded = !it.lowDepthWarningSettingsExpanded) } }
                ) {
                        Spacer(modifier = Modifier.height(8.dp))
                        SettingsSliderGroup(nested = true) {
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
            Spacer(Modifier.height(16.dp))
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
                .background(ComposeColor(AppConfig.uiCardBackground))
                .padding(vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
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

            Spacer(Modifier.height(8.dp))
                // Regulation info — collapsible toggle for info text panel
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    SettingsExpander(
                        label = "Regulated zones settings",
                        expanded = settings.regulationInfoExpanded,
                        onToggle = { onUpdateSettings { it.copy(regulationInfoExpanded = !it.regulationInfoExpanded) } }
                    ) {
                        Spacer(Modifier.height(8.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(ComposeColor(0x0DFFFFFF))
                                .border(1.dp, ComposeColor(0x40FFFFFF), RoundedCornerShape(12.dp))
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.settings_reg_info_desc),
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
                                    text = stringResource(R.string.settings_reg_info_visible),
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
                            Spacer(Modifier.height(4.dp))
                            Box(Modifier.fillMaxWidth().height(1.dp).background(ComposeColor(AppConfig.uiSettingsDivider)))
                            Spacer(Modifier.height(4.dp))
                            BoatSizeSlider(settings, onUpdateSettings, nested = true)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    SettingsExpander(
                        label = "Categories",
                        expanded = settings.categoryFilterExpanded,
                        onToggle = { onUpdateSettings { it.copy(categoryFilterExpanded = !it.categoryFilterExpanded) } }
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))
                        RegulatedZoneCategoryToggles(settings, onUpdateSettings)
                    }
                }
            Spacer(Modifier.height(16.dp))
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Depth layer toggle — grouped card ─────────────────────
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(ComposeColor(AppConfig.uiCardBackground)).padding(vertical = 8.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_depth_label), color = ComposeColor(AppConfig.uiSettingsTextPrimary), fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text(stringResource(R.string.settings_depth_desc), color = ComposeColor(AppConfig.uiSettingsTextMuted), fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Switch(checked = settings.depthLayerVisible,
                    onCheckedChange = { on -> onUpdateSettings { it.copy(depthLayerVisible = on) } },
                    colors = SwitchDefaults.colors(checkedThumbColor = ComposeColor(AppConfig.uiSettingsAccent), checkedTrackColor = ComposeColor(AppConfig.uiSettingsAccent).copy(alpha = 0.4f), uncheckedThumbColor = ComposeColor(AppConfig.uiSettingsTextMuted), uncheckedTrackColor = ComposeColor(AppConfig.uiSettingsSwitchTrackInactive))
                )
            }
            Spacer(Modifier.height(8.dp))
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                SettingsExpander(label = "EMODnet shallow filter", expanded = settings.depthSettingsExpanded,
                    onToggle = { onUpdateSettings { it.copy(depthSettingsExpanded = !it.depthSettingsExpanded) } }
                ) {
                    Spacer(Modifier.height(8.dp))
                    SettingsSliderGroup(nested = true) {
                        SliderRowContent(
                            label = stringResource(R.string.settings_emodnet_cutoff_label),
                            description = stringResource(R.string.settings_emodnet_cutoff_desc),
                            valueLabel = stringResource(R.string.settings_value_depth, settings.emodnetShallowCutoffM),
                            value = settings.emodnetShallowCutoffM, valueRange = 0f..5f, steps = 9,
                            onValueChange = { v -> onUpdateSettings { it.copy(emodnetShallowCutoffM = (v * 2f).roundToInt() / 2f) } }
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        Spacer(modifier = Modifier.height(12.dp))

        // ── Tracks layer toggle ────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ComposeColor(AppConfig.uiCardBackground))
                .padding(vertical = 8.dp)
        ) {
            // Master toggle row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_section_tracks),
                        color = ComposeColor(AppConfig.uiSettingsTextPrimary),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(R.string.settings_tracks_desc),
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

            Spacer(Modifier.height(8.dp))

            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                SettingsExpander(
                    label = "Track settings",
                    expanded = settings.trackSettingsExpanded,
                    onToggle = { onUpdateSettings { it.copy(trackSettingsExpanded = !it.trackSettingsExpanded) } }
                ) {
                        Spacer(Modifier.height(4.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(ComposeColor(0x0DFFFFFF))
                                .border(1.dp, ComposeColor(0x40FFFFFF), RoundedCornerShape(12.dp))
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            // Number of tracks
                            Text(
                                text = stringResource(R.string.settings_tracks_count_label),
                                color = ComposeColor(AppConfig.uiSettingsTextPrimary),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_tracks_count_desc),
                                    color = ComposeColor(AppConfig.uiSettingsTextMuted),
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "%d".format(settings.trackingRenderNb),
                                    color = ComposeColor(AppConfig.uiSettingsAccent),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Slider(
                                value = settings.trackingRenderNb.toFloat(),
                                onValueChange = { v ->
                                    onUpdateSettings { it.copy(trackingRenderNb = v.roundToInt().coerceIn(0, 20)) }
                                },
                                valueRange = 0f..20f,
                                steps = 20,
                                colors = SliderDefaults.colors(
                                    thumbColor = ComposeColor(AppConfig.uiSettingsAccent),
                                    activeTrackColor = ComposeColor(AppConfig.uiSettingsAccent),
                                    inactiveTrackColor = ComposeColor(AppConfig.uiSettingsSwitchTrackInactive)
                                )
                            )

                            Spacer(Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(ComposeColor(AppConfig.uiSettingsDivider))
                            )
                            Spacer(Modifier.height(6.dp))

                            // Transparency
                            Text(
                                text = stringResource(R.string.settings_transparency_label),
                                color = ComposeColor(AppConfig.uiSettingsTextPrimary),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = stringResource(R.string.settings_transparency_desc),
                                color = ComposeColor(AppConfig.uiSettingsTextMuted),
                                fontSize = 12.sp
                            )
                            Text(
                                text = stringResource(R.string.settings_transparency_value_fmt, settings.trackingTransparencyNewest, settings.trackingTransparencyOldest),
                                color = ComposeColor(AppConfig.uiSettingsAccent),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.End,
                                modifier = Modifier.fillMaxWidth()
                            )
                            RangeSlider(
                                value = settings.trackingTransparencyNewest.toFloat()..settings.trackingTransparencyOldest.toFloat(),
                                onValueChange = { range: ClosedFloatingPointRange<Float> ->
                                    onUpdateSettings {
                                        it.copy(
                                            trackingTransparencyNewest = range.start.roundToInt(),
                                            trackingTransparencyOldest = range.endInclusive.roundToInt()
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

                            Spacer(Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(ComposeColor(AppConfig.uiSettingsDivider))
                            )
                            Spacer(Modifier.height(6.dp))

                            // Pinned tracks transparency
                            Text(
                                text = stringResource(R.string.settings_pinned_transparency_label),
                                color = ComposeColor(AppConfig.uiSettingsTextPrimary),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = stringResource(R.string.settings_pinned_transparency_desc),
                                color = ComposeColor(AppConfig.uiSettingsTextMuted),
                                fontSize = 12.sp
                            )
                            Text(
                                text = stringResource(R.string.settings_transparency_value_fmt,
                                    settings.trackingTransparencyPinnedNewest,
                                    settings.trackingTransparencyPinnedOldest
                                ),
                                color = ComposeColor(AppConfig.uiSettingsAccent),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.End,
                                modifier = Modifier.fillMaxWidth()
                            )
                            RangeSlider(
                                value = settings.trackingTransparencyPinnedNewest.toFloat()
                                    ..settings.trackingTransparencyPinnedOldest.toFloat(),
                                onValueChange = { range: ClosedFloatingPointRange<Float> ->
                                    onUpdateSettings {
                                        it.copy(
                                            trackingTransparencyPinnedNewest = range.start.roundToInt(),
                                            trackingTransparencyPinnedOldest = range.endInclusive.roundToInt()
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

                            Spacer(Modifier.height(8.dp))

                            // Colors
                            Text(
                                text = stringResource(R.string.settings_colors_label),
                                color = ComposeColor(AppConfig.uiSettingsTextPrimary),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = stringResource(R.string.settings_colors_desc),
                                color = ComposeColor(AppConfig.uiSettingsTextMuted),
                                fontSize = 12.sp
                            )
                            ColorSwatchRow(
                                label = "Active track",
                                color = settings.trackingColorActive,
                                onColorSelected = { c -> onUpdateSettings { it.copy(trackingColorActive = c) } },
                                showPickLabel = false
                            )
                            ColorSwatchPairRow(
                                label = "Past tracks",
                                fromColor = settings.trackingColorPastFrom,
                                toColor = settings.trackingColorPastTo,
                                onFromColorSelected = { c -> onUpdateSettings { it.copy(trackingColorPastFrom = c) } },
                                onToColorSelected = { c -> onUpdateSettings { it.copy(trackingColorPastTo = c) } }
                            )
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
            Spacer(Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Markers overlay toggle — grouped card ───────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ComposeColor(AppConfig.uiCardBackground))
                .padding(vertical = 8.dp)
        ) {
            // Inline toggle row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_section_markers),
                        color = ComposeColor(AppConfig.uiSettingsTextPrimary),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(R.string.settings_markers_desc),
                        color = ComposeColor(AppConfig.uiSettingsTextMuted),
                        fontSize = 13.sp
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Switch(
                    checked = settings.markerLayerState != MarkerLayerState.HIDDEN,
                    onCheckedChange = { visible ->
                        onUpdateSettings { it.copy(markerLayerState = if (visible) MarkerLayerState.SHOW_ALL else MarkerLayerState.HIDDEN) }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ComposeColor(AppConfig.uiSettingsAccent),
                        checkedTrackColor = ComposeColor(AppConfig.uiSettingsAccent).copy(alpha = 0.4f),
                        uncheckedThumbColor = ComposeColor(AppConfig.uiSettingsTextMuted),
                        uncheckedTrackColor = ComposeColor(AppConfig.uiSettingsSwitchTrackInactive)
                    )
                )
            }

            // Appearance settings — always visible, persisted expander
            Spacer(Modifier.height(8.dp))
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                SettingsExpander(
                    label = "Appearance",
                    expanded = settings.markerAppearanceExpanded,
                    onToggle = { onUpdateSettings { it.copy(markerAppearanceExpanded = !it.markerAppearanceExpanded) } }
                ) {
                        Spacer(Modifier.height(8.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(ComposeColor(0x0DFFFFFF))
                                .border(1.dp, ComposeColor(0x40FFFFFF), RoundedCornerShape(12.dp))
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.settings_zone_shapes_desc),
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
                                    text = stringResource(R.string.settings_zone_shapes_label),
                                    color = ComposeColor(AppConfig.uiSettingsTextPrimary),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Switch(
                                    checked = settings.markerZonesVisible,
                                    onCheckedChange = { visible ->
                                        onUpdateSettings { it.copy(markerZonesVisible = visible) }
                                    },
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
            Spacer(Modifier.height(16.dp))
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
                .background(ComposeColor(AppConfig.uiCardBackground))
                .padding(vertical = 8.dp)
        ) {
            // Auto-show GPS mode toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
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
            Spacer(Modifier.height(8.dp))

            // Auto-show Demo mode toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
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
                Spacer(Modifier.height(8.dp))
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    var alertExpanded by remember { mutableStateOf(false) }
                    SettingsExpander(
                        label = "Alert settings",
                        expanded = alertExpanded,
                        onToggle = { alertExpanded = !alertExpanded }
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))
                        SettingsSliderGroup(nested = true) {
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
                Spacer(Modifier.height(16.dp))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Speed zone alert (auto-show on approach) — grouped card ─────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ComposeColor(AppConfig.uiCardBackground))
                .padding(vertical = 8.dp)
        ) {
            // Auto-show GPS mode toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_speed_alert_gps_label),
                        color = ComposeColor(AppConfig.uiSettingsTextPrimary),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(R.string.settings_speed_alert_gps_desc),
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
            Spacer(Modifier.height(8.dp))

            // Auto-show Demo mode toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_speed_alert_demo_label),
                        color = ComposeColor(AppConfig.uiSettingsTextPrimary),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(R.string.settings_speed_alert_demo_desc),
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
            Spacer(Modifier.height(8.dp))

            // Regulated zone overlay auto-show GPS toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_reg_alert_gps_label),
                        color = ComposeColor(AppConfig.uiSettingsTextPrimary),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(R.string.settings_reg_alert_gps_desc),
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
            Spacer(Modifier.height(8.dp))

            // Regulated zone overlay auto-show Demo toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_reg_alert_demo_label),
                        color = ComposeColor(AppConfig.uiSettingsTextPrimary),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(R.string.settings_reg_alert_demo_desc),
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

    Spacer(modifier = Modifier.height(12.dp))

    // ── Automatic map offset ──────────────────────────────────────────────
    SectionHeader(title = "Automatic map offset")

    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(ComposeColor(AppConfig.uiCardBackground)).padding(vertical = 8.dp)
    ) {
        // GPS mode toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "GPS mode",
                    color = ComposeColor(AppConfig.uiSettingsTextPrimary),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Shift map center ahead when navigating with GPS",
                    color = ComposeColor(AppConfig.uiSettingsTextMuted),
                    fontSize = 13.sp
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Switch(
                checked = settings.mapOffsetGps,
                onCheckedChange = { on -> onUpdateSettings { it.copy(mapOffsetGps = on) } },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = ComposeColor(AppConfig.uiSettingsAccent),
                    checkedTrackColor = ComposeColor(AppConfig.uiSettingsAccent).copy(alpha = 0.4f),
                    uncheckedThumbColor = ComposeColor(AppConfig.uiSettingsTextMuted),
                    uncheckedTrackColor = ComposeColor(AppConfig.uiSettingsSwitchTrackInactive)
                )
            )
        }

        Spacer(Modifier.height(4.dp))

        // Demo mode toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Demo mode",
                    color = ComposeColor(AppConfig.uiSettingsTextPrimary),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Shift map center ahead when panning in free mode",
                    color = ComposeColor(AppConfig.uiSettingsTextMuted),
                    fontSize = 13.sp
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Switch(
                checked = settings.mapOffsetDemo,
                onCheckedChange = { on -> onUpdateSettings { it.copy(mapOffsetDemo = on) } },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = ComposeColor(AppConfig.uiSettingsAccent),
                    checkedTrackColor = ComposeColor(AppConfig.uiSettingsAccent).copy(alpha = 0.4f),
                    uncheckedThumbColor = ComposeColor(AppConfig.uiSettingsTextMuted),
                    uncheckedTrackColor = ComposeColor(AppConfig.uiSettingsSwitchTrackInactive)
                )
            )
        }

        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(ComposeColor(0x26FFFFFF))
        )
        Spacer(Modifier.height(6.dp))

        // Boat-from-bottom slider
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            SliderRowContent(
                label = "Boat position from bottom",
                description = "Where the boat sits at speed. 50% = disabled (centered). Lower = more ahead.",
                valueLabel = "${settings.mapOffsetBoatFromBottomPct}%",
                value = settings.mapOffsetBoatFromBottomPct.toFloat(),
                valueRange = 5f..50f,
                steps = 9,
                onValueChange = { v -> onUpdateSettings { it.copy(mapOffsetBoatFromBottomPct = v.roundToInt()) } }
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
                .background(ComposeColor(AppConfig.uiCardBackground))
                .padding(vertical = 8.dp)
        ) {
            // GPS mode toggle row (inline, no separate card background)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
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
                Spacer(Modifier.height(8.dp))
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    var gpsTuningExpanded by remember { mutableStateOf(false) }
                    SettingsExpander(
                        label = "GPS tuning",
                        expanded = gpsTuningExpanded,
                        onToggle = { gpsTuningExpanded = !gpsTuningExpanded }
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
                            onValueChange = { v -> onUpdateSettings { it.copy(recenterDelaySeconds = v.roundToInt()) } },
                            nested = true
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

        SettingsToggleRow(
            label = "Debug rays (WhereAmI)",
            description = "Show green/red line-of-sight rays on the map when tapping the boat marker.",
            checked = settings.markerDebugRays,
            onCheckedChange = { on ->
                onUpdateSettings { it.copy(markerDebugRays = on) }
                AppConfig.markerDebugRaysEnabled = on
                MarkerMatcher.debugger = if (on) VisualWhereAmIDebugger() else NoOpWhereAmIDebugger
            }
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
                .background(ComposeColor(AppConfig.uiCardBackground))
                .padding(vertical = 8.dp)
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
                Spacer(Modifier.height(8.dp))

                // Detection thresholds expander
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    var adaptiveAdvanced by remember { mutableStateOf(false) }
                    SettingsExpander(
                        label = stringResource(R.string.settings_stop_thresholds_label),
                        expanded = adaptiveAdvanced,
                        onToggle = { adaptiveAdvanced = !adaptiveAdvanced }
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))
                        SettingsSliderGroup(nested = true) {
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
                Spacer(Modifier.height(8.dp))

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

        Spacer(modifier = Modifier.height(24.dp))

        // ── Regenerate Layers ─────────────────────────────────────────
        SectionHeader(title = stringResource(R.string.settings_regenerate_layers))
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
                Text(stringResource(R.string.action_regenerate), color = ComposeColor(AppConfig.uiSettingsTextPrimary))
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
        "en" to stringResource(R.string.settings_language_english),
        "fr" to "Français"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ComposeColor(AppConfig.uiCardBackground))
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
            .background(ComposeColor(AppConfig.uiCardBackground))
            .padding(horizontal = 16.dp, vertical = 8.dp),
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
    onValueChange: (Float) -> Unit,
    nested: Boolean = false
) {
    SettingsSliderGroup(nested = nested) {
        SliderRowContent(label, description, valueLabel, value, valueRange, steps, onValueChange)
    }
}

/** Rounded settings "box" hosting one or more [SliderRowContent]s — groups related sliders together. */
@Composable
private fun SettingsSliderGroup(
    nested: Boolean = false,
    content: @Composable () -> Unit
) {
    val bgColor = if (nested) 0x0DFFFFFF else AppConfig.uiCardBackground
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ComposeColor(bgColor))
            .then(
                if (nested) Modifier.border(1.dp, ComposeColor(0x40FFFFFF), RoundedCornerShape(12.dp))
                else Modifier
            )
            .padding(horizontal = 16.dp, vertical = if (nested) 2.dp else 8.dp)
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
            .background(ComposeColor(0x26FFFFFF))
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
        color = ComposeColor(AppConfig.uiSettingsTextPrimary),
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium
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
            .padding(vertical = 8.dp),
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
                    .size(24.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(ComposeColor(color))
                    .clickable { showPicker = true }
                    .border(1.dp, ComposeColor(AppConfig.uiSettingsDivider), RoundedCornerShape(6.dp))
            )
            if (showPickLabel) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.color_picker_pick),
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
            0xFF4FC3F7.toInt(), // Light Blue
            0xFF00BCD4.toInt(), // Cyan
            0xFFCDDC39.toInt(), // Lime
            0xFFE91E63.toInt(), // Pink
            0xFF3F51B5.toInt()  // Indigo
        )
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.color_picker_title)) },
        text = {
            Column {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.height(216.dp),
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
            .padding(vertical = 8.dp),
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
            .background(ComposeColor(AppConfig.uiCardBackground))
            .padding(horizontal = 16.dp, vertical = 8.dp)
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
            .background(ComposeColor(AppConfig.uiCardBackground))
            .padding(horizontal = 16.dp, vertical = 8.dp),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordingExitSheet(
    onSave: () -> Unit,
    onContinue: () -> Unit,
    onDiscard: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        containerColor = ComposeColor(AppConfig.uiSettingsBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Recording in progress",
                color = ComposeColor(AppConfig.uiSettingsTextPrimary),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "A track is being recorded. What would you like to do?",
                color = ComposeColor(AppConfig.uiSettingsTextPrimary),
                fontSize = 14.sp
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ComposeColor(AppConfig.uiSettingsAccent)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save track", color = ComposeColor.White, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Continue recording", color = ComposeColor(AppConfig.uiSettingsAccent))
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onDiscard,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ComposeColor(AppConfig.semanticDanger)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Discard track", color = ComposeColor.White, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}


