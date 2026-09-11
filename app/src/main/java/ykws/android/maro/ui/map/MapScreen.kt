
package ykws.android.maro.ui.map
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.track.TrackRecordingService
import ykws.android.maro.data.model.matchesFilter
import ykws.android.maro.data.track.toGpx
import ykws.android.maro.data.track.ImportMode

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
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import androidx.compose.ui.res.pluralStringResource
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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import ykws.android.maro.data.depth.DepthConstants
import ykws.android.maro.data.model.BoundingBox
import ykws.android.maro.data.model.CoastlinePoint
import ykws.android.maro.data.model.CoastlineSegment
import ykws.android.maro.data.model.CoastlineState
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
import ykws.android.maro.data.model.markers.MarkerOrigin
import ykws.android.maro.data.model.markers.UserMarker
import ykws.android.maro.data.markers.UserMarkerRepository
import ykws.android.maro.ui.components.ConfirmAction
import ykws.android.maro.ui.components.ConfirmActionRole
import ykws.android.maro.ui.components.ConfirmDialog
import ykws.android.maro.ui.components.ConfirmDialogHostState
import ykws.android.maro.ui.components.ConfirmRequestHost
import ykws.android.maro.ui.components.DrawerHeader
import ykws.android.maro.ui.components.LocalConfirmDialogHost
import ykws.android.maro.spatial.SpatialOperations
import ykws.android.maro.ui.map.MarkersViewModel
import ykws.android.maro.ui.map.MarkerDrawer
import ykws.android.maro.ui.map.toMarkerSnapshot

/** Animation duration per GPS-follow scroll (ms). Must be < min GPS fix interval (1s). */
private const val GPS_ANIMATION_DURATION_MS = 600L

/** Right-edge control column width (12 gap + 64 button + 6 end). Paint-only reserve for transient overlays; the map itself is never padded by this. */
internal val RIGHT_CONTROL_COLUMN_INSET = 82.dp

/** Computed polyline rendering appearance: ARGB color + stroke width. */
data class TrackPolylineAppearance(val argb: Int, val strokeWidth: Float)

/** Uniform on-screen spacing (dp) between direction arrows. */
internal const val DIRECTION_ARROW_SPACING_DP = 48f

/** Log-scale gap slider bounds (dp). */
internal const val DIRECTION_GAP_MIN_DP = 4f
internal const val DIRECTION_GAP_MAX_DP = 640f
/** Log-scale speed slider bounds (kn). */
internal const val DIRECTION_SPEED_MIN_KN = 2f
internal const val DIRECTION_SPEED_MAX_KN = 64f

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

/** Held while the user decides how to import a single GPX that matches an existing track. */
internal data class PendingTrackImport(
    val bytes: ByteArray,
    val extension: String,
    val matchName: String
)

/** Held while the user confirms resuming a stored track (with or without a backup copy). */
internal data class PendingTrackResume(
    val trackId: String,
    val fromList: Boolean
)

/** Transient import feedback: result counts or a hard failure. */
internal sealed interface ImportBannerState {
    data class Result(val imported: Int, val ignored: Int) : ImportBannerState
    data object Failed : ImportBannerState
}

/** Snackbar entry for the vertical stack — track/marker deletes + marker-created undo. */
internal sealed class ActiveSnack(val id: String, val name: String) {
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
internal fun SnackRow(
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
    val newest = minOf(transparencyNewest, transparencyOldest)
    val oldest = maxOf(transparencyNewest, transparencyOldest)
    val alphaNewest = (100 - newest) / 100f   // newest (index 0) -> lower transparency = higher alpha
    val alphaOldest = (100 - oldest) / 100f   // oldest -> higher transparency = lower alpha
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
    // Monotonic timestamp key: each show/refresh bumps this so the 2s timeout restarts (no queue).
    var lockBannerAt by remember { mutableStateOf(0L) }

    // ── Click-N-Move state ─────────────────────────────────────────────
    var highlightedTrackId by remember { mutableStateOf<String?>(null) }
    var preNavigationState by remember { mutableStateOf<PreNavigationState?>(null) }
    var trackNavigateState by remember { mutableStateOf<TrackNavigateState?>(null) }
    var trackDrawerState by remember { mutableStateOf(TrackDrawerState()) }

    // ── List state ───────────────────────────────────────────────────────
    val trackListState = rememberLazyListState()
    val markerListState = rememberLazyListState()
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
    val allTrackSummaries by trackViewModel.allSummaries.collectAsState()
    val recoveryTrack by trackViewModel.recoveryTrack.collectAsState()
    val trackScope = rememberCoroutineScope()
    // ── Close selected-item dashboards (marker + track) when menu/fan opens ──
    fun closeSelectedItemDashboards() {
        if (markersViewModel.drawerState.value is MarkerDrawerState.Viewing ||
            markersViewModel.drawerState.value is MarkerDrawerState.MatchResult
        ) {
            markersViewModel.closeDrawer()
        }
        highlightedTrackId = null
        trackDrawerState = TrackDrawerState()
        preNavigationState = null
    }
    // Single-GPX import whose match dialog is pending a Duplicate / Override / Cancel choice.
    var pendingTrackImport by remember { mutableStateOf<PendingTrackImport?>(null) }
    // Import feedback banner: null = hidden; Result / Failed shows briefly at the map bottom.
    var importBanner by remember { mutableStateOf<ImportBannerState?>(null) }
    var importBannerAt by remember { mutableStateOf(0L) }
    fun showImportBanner(banner: ImportBannerState) {
        importBanner = banner
        importBannerAt = SystemClock.elapsedRealtime()
    }
    // Transient track-operation status banner (export/import in progress): null = hidden.
    var trackOpStatus by remember { mutableStateOf<String?>(null) }

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

    val anyFanExpanded = expandedFanId != null
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val displayScrollState = rememberScrollState()
    val navigationScrollState = rememberScrollState()
    val positionScrollState = rememberScrollState()
    val systemScrollState = rememberScrollState()

    val context = LocalContext.current
    // ── Screen-lock toggle: flip state + toast ──────────────────────────
    val onToggleScreenLock = {
        val newLocked = !screenLocked
        screenLocked = newLocked
        lockBanner = newLocked
        lockBannerAt = SystemClock.elapsedRealtime()
    }
    LaunchedEffect(lockBannerAt) {
        if (lockBanner != null) {
            delay(2_000L)
            lockBanner = null
        }
    }
    LaunchedEffect(importBannerAt) {
        if (importBanner != null) {
            delay(2_000L)
            importBanner = null
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
    // Demo mode → 0° = north. demoBearingDeg is never set; panning derives speed only.
    //             demoHeadingUp + two-finger rotation only rotate the map (bearingDeg);
    //             they do not feed the nav heading used by the auto-show cone.
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
            trackOpStatus = context.getString(R.string.importing_tracks)
            trackScope.launch(Dispatchers.IO) {
                try {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (bytes == null) {
                        withContext(Dispatchers.Main) { trackOpStatus = null }
                        return@launch
                    }
                    val isZip = bytes.size >= 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
                    val extension = if (isZip) "zip" else "gpx"
                    if (isZip) {
                        val result = trackViewModel.importTracks(bytes, extension, ImportMode.SKIP_EXISTING)
                        withContext(Dispatchers.Main) {
                            trackOpStatus = null
                            showImportBanner(ImportBannerState.Result(result.imported, result.ignored))
                        }
                    } else {
                        val match = trackViewModel.peekImportMatch(bytes, extension)
                        if (match != null) {
                            withContext(Dispatchers.Main) {
                                trackOpStatus = null
                                pendingTrackImport = PendingTrackImport(bytes, extension, match.name)
                            }
                        } else {
                            val result = trackViewModel.importTracks(bytes, extension, ImportMode.IMPORT_NEW)
                            withContext(Dispatchers.Main) {
                                trackOpStatus = null
                                showImportBanner(ImportBannerState.Result(result.imported, result.ignored))
                            }
                        }
                    }
                } catch (_: Exception) {
                    withContext(Dispatchers.Main) {
                        trackOpStatus = null
                        showImportBanner(ImportBannerState.Failed)
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

    // ── GPS-follow orchestration effects (extracted to MapGpsFollowEffects) ──
    MapGpsFollowEffects(
        mapView = mapView,
        viewModel = viewModel,
        depthViewModel = depthViewModel,
        appSettings = appSettings,
        autoFollowSuppressed = autoFollowSuppressed
    )

    // Coastline classifier is needed for both the low-depth warning and the depth colour map
    // (to keep NoData colour off land).
    val coastlineReady = state is CoastlineState.Ready

    // ── Depth layer + silent raster lazy-init (extracted to MapDepthRasterEffects) ──
    val depthRaster = MapDepthRasterEffects(
        context = context,
        viewModel = viewModel,
        depthViewModel = depthViewModel,
        appSettings = appSettings,
        coastlineReady = coastlineReady
    )

    // ── Regulated zones overlay: load prebaked asset (extracted) ──
    val regulatedZones = MapRegulatedZonesLoader(context)

    // ── User markers: from MarkersViewModel ─────────────────────────────────────────
    val userMarkers by markersViewModel.markers.collectAsState()
    val markerLayerState by markersViewModel.markerLayerState.collectAsState()
    val markerLayerVisible = markerLayerState != MarkerLayerState.HIDDEN
    // Map-referential markers: the map overlay renders the MAP filter world (list uses markers).
    val mapMarkersState by markersViewModel.mapMarkers.collectAsState()

    // Wire coastline spatial index into MarkersViewModel for land-blocking when ready
    if (coastlineReady) {
        markersViewModel.coastlineIndex = viewModel.spatialIndex
    }

    // ── Marker wiring effects (extracted to MapMarkerEffects) ──
    MapMarkerEffects(
        viewModel = viewModel,
        markersViewModel = markersViewModel,
        trackViewModel = trackViewModel,
        userMarkers = userMarkers
    )

    // ── Track info error auto-dismiss after 8 seconds ──
    LaunchedEffect(trackRecorderState.infoError) {
        val error = trackRecorderState.infoError
        if (error != null) {
            delay(8_000L)
            trackViewModel.clearInfoError()
        }
    }

    // ── Track event observation: drawer auto-open/close + live polyline restore ──
    LaunchedEffect(Unit) {
        trackViewModel.events.collect { event ->
            when (event) {
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
                    OverlayZOrder.reorder(mv)
                    mv.invalidate()
                }
                else -> { /* Started, Stopped, PointCaptured — handled elsewhere */ }
            }
        }
    }

    // ── Marker debug effects (extracted to MapMarkerEffects) ──
    MapMarkerDebugEffects(
        mapView = mapView,
        appSettings = appSettings,
        debugSegments = debugSegments
    )


    // ── Service intents effects (extracted to MapServiceEffects) ──
    // Demo feed host is UNCONDITIONAL: LaunchedEffect(Unit), not keyed on gpsMode.
    MapServiceEffects(
        context = context,
        viewModel = viewModel,
        navigationState = navigationState,
        appSettings = appSettings,
        boatIsWater = boatIsWater,
        trackRecorderState = trackRecorderState
    )

    // ── History/pinned track overlay diff (extracted to MapTrackOverlayEffects) ──
    MapTrackOverlayHistoryDiff(
        mapView = mapView,
        showSettings = showSettings,
        highlightedTrackId = highlightedTrackId,
        allTrackSummaries = allTrackSummaries,
        appSettings = appSettings,
        trackViewModel = trackViewModel
    )

    // ── Live-recording overlay effects (extracted to MapTrackOverlayEffects) ──
    MapTrackOverlayLiveEffects(
        mapView = mapView,
        viewModel = viewModel,
        trackViewModel = trackViewModel,
        trackRecorderState = trackRecorderState,
        appSettings = appSettings
    )




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
    // Resume confirmation: non-null while the dialog awaits the Resume/Cancel choice.
    var pendingResume by remember { mutableStateOf<PendingTrackResume?>(null) }

    // ── Ladder confirm-dialog host ───────────────────────────────────────
    // Sink for ConfirmRequests raised by drawer-hosted surfaces (batch delete, merge). Their own
    // scrim would be clipped to the drawer, so they hand the dialog to this host, which paints it
    // on the overlay ladder — full-screen scrim over the drawers and the map.
    val confirmDialogHost = remember { ConfirmDialogHostState() }

    // ── Confirm-dialog-open flag (hoisted for the exit guard) ────────────
    // Each ConfirmDialog owns its own dismissal (scrim tap / back / Cancel), so the double-back
    // exit guard must stand down while one is offered. Covers the exit, stop-recording, recovery,
    // import-conflict, GPS source-switch and resume dialogs; the hoisted list-drawer
    // confirmations (batch delete, merge) stay behind their open drawer, which the guard excludes.
    val anyConfirmDialogOpen = showExitDialog || showStopRecordingSheet ||
        pendingGpsModeToggle != null || recoveryTrack != null ||
        pendingTrackImport != null || pendingResume != null

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
        BackHandler(enabled = !showSettings && !showTrackHistory && !showMarkerManagement && !anyFanExpanded && !trackDrawerState.isOpen && !anyConfirmDialogOpen) {
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

        // ── Exit / stop-recording sheets + windowed dialogs rendered by MapDialogHost ──
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
                depthBitmap = depthRaster.effectiveDepthBitmap,
                lowDepthWarningBitmap = depthRaster.effectiveLowDepthWarning,
                depthBox = depthRaster.depthBox,
                isobaths = depthRaster.isobaths,
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
                onAddZone = { center ->
                    closeSelectedItemDashboards()
                    markersViewModel.startWizard(initialPos = center)
                },
                onMarkerTap = { id ->
                    val worldIds = mapMarkersState.map { it.id }
                    val navIds = if (worldIds.contains(id)) worldIds else listOf(id) + worldIds
                    markersViewModel.openEditDrawer(navIds, selectedId = id, source = DrawerSource.MAP)
                },
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
                onOpenTrackDrawer = {
                    if (!showTrackDrawer) closeSelectedItemDashboards()
                    showTrackDrawer = !showTrackDrawer
                },
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
                    if (name != null || comment != null) trackViewModel.updateTrack(id, name, comment)
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
                importBanner = importBanner,
                trackOpStatus = trackOpStatus,
                rasterProgress = depthRaster.rasterProgress,
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
                    depthSample = depthRaster.depthReadout,
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
                    depthSample = depthRaster.depthReadout,
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
                // Reveal-on-select: a marker opened from a list but excluded by the map filter is
                // force-drawn while its detail panel is open.
                val revealMarker = if (drawerState is MarkerDrawerState.Viewing || drawerState is MarkerDrawerState.MatchResult) {
                    selectedMarkerId?.let { id -> markersViewModel.allMarkers.value.firstOrNull { it.id == id } }
                } else null
                val overlayMarkers = if (revealMarker != null && mapMarkersState.none { it.id == revealMarker.id }) {
                    mapMarkersState + revealMarker
                } else mapMarkersState
                MarkerOverlay(
                    markers = overlayMarkers,
                    mapView = mapView,
                    proximityZoneMultiplier = AppConfig.markerProximityZoneMultiplier,
                    unconfirmedMarker = unconfirmedMarker,
                    onMarkerTap = { ids ->
                        ids.firstOrNull()?.let { sel ->
                            val worldIds = mapMarkersState.map { it.id }
                            val navIds = if (worldIds.contains(sel)) worldIds else listOf(sel) + worldIds
                            markersViewModel.openEditDrawer(navIds, selectedId = sel, source = DrawerSource.MAP)
                        }
                    },
                    matchResult = if (drawerState is MarkerDrawerState.MatchResult) matchResult else null,
                    // The selected marker forces its own zones visible inside MarkerOverlay
                    // (folded navigationZonesVisible); this flag stays the global toggle.
                    markerZonesVisible = appSettings.markerZonesVisible,
                    selectedMarkerId = selectedMarkerId,
                    markerLayerState = markerLayerState,
                    markerHaloSize = appSettings.markerHaloSize,
                    markerPointIconZoom = appSettings.markerPointIconZoom,
                    markerHaloPinnedColor = appSettings.markerHaloPinnedColor,
                    markerHaloUnpinnedColor = appSettings.markerHaloUnpinnedColor,
                    markerHaloPinnedFillTransparencyPct = appSettings.markerHaloPinnedFillTransparencyPct,
                    markerHaloPinnedBorderTransparencyPct = appSettings.markerHaloPinnedBorderTransparencyPct,
                    markerHaloUnpinnedFillTransparencyPct = appSettings.markerHaloUnpinnedFillTransparencyPct,
                    markerHaloUnpinnedBorderTransparencyPct = appSettings.markerHaloUnpinnedBorderTransparencyPct
                )
            }

        // ── Click-N-Move: sequential navigate flow ──────────────────────────
        LaunchedEffect(navigateToTarget) {
            val target = navigateToTarget ?: return@LaunchedEffect
            val mv = mapView ?: return@LaunchedEffect

            // 1. Focus the marker: corridor/circle zoom-to-fit the whole zone (bbox);
            //    pin is a single point — centre only.
            val focusMarker = userMarkers.find { it.id == target.markerId }
            if (focusMarker == null ||
                (focusMarker.geometry !is ykws.android.maro.data.model.markers.MarkerGeometry.Circle &&
                    focusMarker.geometry !is ykws.android.maro.data.model.markers.MarkerGeometry.Corridor)
            ) {
                mv.controller.animateTo(target.geoPoint, null, GPS_ANIMATION_DURATION_MS)
            } else {
                val b = focusMarker.bbox
                mv.zoomToBoundingBox(
                    org.osmdroid.util.BoundingBox(b.latNorth, b.lonEast, b.latSouth, b.lonWest),
                    true, 64
                )
            }

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

        fun openTrackDetail(id: String) {
            if (!appSettings.tracksVisible) {
                viewModel.updateSettings { it.copy(tracksVisible = true) }
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

        fun openMarkerDetail(id: String) {
            val marker = mgmtMarkers.find { it.id == id } ?: return
            markersViewModel.showLayer()
            showMarkerManagement = false
            navigateToTarget = NavigateTarget(
                geoPoint = GeoPoint(marker.centerPoint.latitude, marker.centerPoint.longitude),
                markerId = id
            )
        }

        // Menu (map-referential) track counter: stored non-live tracks that would be rendered under the
        // map filter — pinned always counted when matching, individually hidden (visibleOnMap=false)
        // excluded. Render-cap divergence is acceptable.
        val trackMapVisibleCount = allTrackSummaries.count {
            !it.isLive && it.matchesFilter(appSettings.trackMapFilter, ykws.android.maro.data.model.todayMidnightMs()) && (it.pinned || it.visibleOnMap)
        }

        // Close the track detail drawer (restores the pre-navigation camera when the map was untouched).
        val closeTrackDrawer: () -> Unit = {
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

        CompositionLocalProvider(LocalConfirmDialogHost provides confirmDialogHost) {
        OverlayLayer(
            chrome = OverlayChrome(
                showSettings = showSettings,
                showTrackDrawer = showTrackDrawer,
                showTrackHistory = showTrackHistory,
                showMarkerManagement = showMarkerManagement,
                showWizard = showWizard,
                wizardStep = wizardStep,
                drawerState = drawerState,
                // Suppress the ladder scrim while any ConfirmDialog is up (its own scrim wins), so
                // the two dim layers never stack. Includes the hoisted merge / batch-delete host.
                dialogScrimActive = anyConfirmDialogOpen || confirmDialogHost.request != null,
            ),
            isLandscape = isLandscape,
            portraitDashboardHeight = portraitDashboardHeight,
            landscapeDashboardWidth = landscapeDashboardWidth,
            onDismissSettings = { showSettings = false },
            onDismissMenu = { showTrackDrawer = false },
            onDismissTrackHistory = { showTrackHistory = false },
            onDismissMarkerManagement = { showMarkerManagement = false },
            onWizardCancel = { markersViewModel.wizardCancel() },
            onMarkerDrawerClose = {
                markersViewModel.closeDrawer()
            },
            onOpenTrackHistoryFromMenu = { showTrackHistory = true },
            onOpenMarkerManagementFromMenu = { showMarkerManagement = true },
            onOpenSettingsFromMenu = { showSettings = true },
            onOpenFirstTrack = { id -> openTrackDetail(id) },
            onOpenFirstMarker = { id -> openMarkerDetail(id) },
            markersViewModel = markersViewModel,
            trackViewModel = trackViewModel,
            menu = MenuOverlayData(
                gpsMode = appSettings.gpsMode,
                autoShowMasterVisible = if (appSettings.gpsMode) appSettings.approachAutoShowGps else appSettings.approachAutoShowDemo,
                autoShowMasterOverride = appSettings.autoShowMasterOverride,
                gpsToggleColor = gpsToggleColor,
                markerZonesVisible = appSettings.markerZonesVisible,
                tracksDirectionVisible = appSettings.tracksDirectionVisible,
                firstTrackId = firstTrackId,
                firstMarkerId = firstMarkerId,
                trackMapFilterState = appSettings.trackMapFilter,
                trackMapCount = trackMapVisibleCount,
                markerMapFilterState = appSettings.markerMapFilter,
                markerMapCount = mapMarkersState.size,
            ),
            onGpsModeChange = onGpsModeChange,
            onAutoShowMasterChange = { v -> viewModel.updateSettings { it.copy(autoShowMasterOverride = v) } },
            onToggleMarkerZones = {
                Log.d("MaroMapRefresh", "MenuDrawer toggle: markerZonesVisible ${appSettings.markerZonesVisible} -> ${!appSettings.markerZonesVisible}")
                viewModel.updateSettings { it.copy(markerZonesVisible = !appSettings.markerZonesVisible) }
                mapView?.invalidate()
            },
            onToggleTracksDirection = {
                viewModel.updateSettings { it.copy(tracksDirectionVisible = !appSettings.tracksDirectionVisible) }
                mapView?.invalidate()
            },
            onTrackAction = { action ->
                when (action) {
                    is ykws.android.maro.data.model.ListAction.NavigateToItem -> openTrackDetail(action.id)
                    is ykws.android.maro.data.model.ListAction.ExportGpx -> shareTrackGpx(context, trackViewModel, action.id, trackScope, onProgress = { trackOpStatus = it })
                    is ykws.android.maro.data.model.ListAction.BatchExportGpx -> shareTracksZip(context, trackViewModel, action.ids, trackScope, onProgress = { trackOpStatus = it })
                    is ykws.android.maro.data.model.ListAction.ImportTracks -> importLauncher?.launch(arrayOf("application/gpx+xml", "application/zip", "*/*"))
                    is ykws.android.maro.data.model.ListAction.PermanentDelete -> trackViewModel.deleteTrack(action.id)
                    is ykws.android.maro.data.model.ListAction.RefreshList -> trackViewModel.refreshSummaries(action.sortState, reloadFromDisk = false)
                    is ykws.android.maro.data.model.ListAction.RefreshLayer -> mapView?.invalidate()
                    else -> {}
                }
            },
            trackList = TrackListOverlayData(
                trackSortState = appSettings.trackListSort,
                trackFilterState = appSettings.trackListFilter,
                trackListState = trackListState,
            ),
            onTrackSortStateChange = { newState ->
                viewModel.updateSettings { it.copy(trackListSort = newState) }
                trackViewModel.refreshSummaries(newState, reloadFromDisk = false)
                mapView?.invalidate()
            },
            onTrackFilterChange = { newFilter ->
                viewModel.updateSettings { s ->
                    if (s.trackFilterLinked) s.copy(trackListFilter = newFilter, trackMapFilter = newFilter)
                    else s.copy(trackListFilter = newFilter)
                }
                trackViewModel.refreshSummaries(filter = newFilter, reloadFromDisk = false)
            },
            onTrackReset = {
                val resetFilter = ykws.android.maro.data.model.ListFilter()
                viewModel.updateSettings { s ->
                    if (s.trackFilterLinked) s.copy(trackListSort = ykws.android.maro.data.model.ListSortState(), trackListFilter = resetFilter, trackMapFilter = resetFilter)
                    else s.copy(trackListSort = ykws.android.maro.data.model.ListSortState(), trackListFilter = resetFilter)
                }
                trackViewModel.refreshSummaries(filter = resetFilter, reloadFromDisk = false)
                mapView?.invalidate()
            },
            // ── Track map referential (menu filter) + link ────────────────
            onTrackMapFilterChange = { newFilter ->
                val linked = appSettings.trackFilterLinked
                viewModel.updateSettings { s ->
                    if (s.trackFilterLinked) s.copy(trackListFilter = newFilter, trackMapFilter = newFilter)
                    else s.copy(trackMapFilter = newFilter)
                }
                if (linked) trackViewModel.refreshSummaries(filter = newFilter, reloadFromDisk = false)
                mapView?.invalidate()
            },
            onTrackMapReset = {
                val resetFilter = ykws.android.maro.data.model.ListFilter()
                val linked = appSettings.trackFilterLinked
                viewModel.updateSettings { s ->
                    if (s.trackFilterLinked) s.copy(trackListFilter = resetFilter, trackMapFilter = resetFilter)
                    else s.copy(trackMapFilter = resetFilter)
                }
                if (linked) trackViewModel.refreshSummaries(filter = resetFilter, reloadFromDisk = false)
                mapView?.invalidate()
            },
            trackFilterLinked = appSettings.trackFilterLinked,
            onToggleTrackLink = {
                // Pure flip: no filter carry-over. Next linked edit writes both.
                viewModel.updateSettings { s -> s.copy(trackFilterLinked = !s.trackFilterLinked) }
            },
            appSettings = appSettings,
            onUpdateSettings = viewModel::updateSettings,
            settings = SettingsOverlayData(
                selectedTab = selectedTab,
                displayScrollState = displayScrollState,
                navigationScrollState = navigationScrollState,
                positionScrollState = positionScrollState,
                systemScrollState = systemScrollState,
            ),
            onTabChange = { selectedTab = it },
            onRegenerateRasters = { steps ->
                val waterTest: (Double, Double) -> Boolean =
                    if (state is CoastlineState.Ready) viewModel::isOnWater else { _, _ -> false }
                depthViewModel.generateRasterLayers(context, steps, appSettings, waterTest)
            },
            boatPosition = gpsPosition ?: mapCenter,
            markerList = MarkerListOverlayData(
                markers = mgmtMarkers,
                markerSortState = appSettings.markerListSort,
                markerFilterState = appSettings.markerListFilter,
                markerListState = markerListState,
            ),
            trackTitleLookup = { id -> allTrackSummaries.firstOrNull { it.id == id }?.name },
            onOpenMarkerTrack = { trackId ->
                // Switch from a marker surface to the owning track's detail drawer.
                navigateToTarget = null
                openTrackDetail(trackId)
            },
            onMarkerAction = { action ->
                when (action) {
                    is ykws.android.maro.data.model.ListAction.NavigateToItem -> openMarkerDetail(action.id)
                    is ykws.android.maro.data.model.ListAction.EditItem -> {
                        showMarkerManagement = false
                        markersViewModel.startWizard(action.id)
                    }
                    is ykws.android.maro.data.model.ListAction.PermanentDelete -> markersViewModel.deleteMarker(action.id)
                    is ykws.android.maro.data.model.ListAction.RefreshList -> markersViewModel.refreshSort(action.sortState)
                    else -> {}
                }
            },
            // ── Track info drawer ─────────────────────────────────────────
            trackInfo = TrackInfoOverlayData(
                showTrackInfoDrawer = trackDrawerState.isOpen,
                trackInfoDrawerData = trackDrawerState.track,
                trackListIds = trackSummaries.filter { !it.isLive && "t:${it.id}" !in pendingDeleteIds }.map { it.id },
                currentTrackIndex = trackSummaries.filter { !it.isLive && "t:${it.id}" !in pendingDeleteIds }.map { it.id }.indexOf(trackDrawerState.track?.id ?: "").coerceAtLeast(0),
            ),
            onTrackDrawerClose = closeTrackDrawer,
            onNavigateToTrack = { id -> openTrackDetail(id) },
            onResumeRequest = { id, fromList -> pendingResume = PendingTrackResume(id, fromList) },
            onMarkerSortStateChange = { newState ->
                viewModel.updateSettings { it.copy(markerListSort = newState) }
                markersViewModel.refreshSort(newState)
            },
            onMarkerFilterChange = { newFilter ->
                android.util.Log.d("MaroMapRefresh", "onMarkerFilterChange: $newFilter")
                viewModel.updateSettings { s ->
                    if (s.markerFilterLinked) s.copy(markerListFilter = newFilter, markerMapFilter = newFilter)
                    else s.copy(markerListFilter = newFilter)
                }
                markersViewModel.refreshSort(filter = newFilter)
            },
            onMarkerReset = {
                android.util.Log.d("MaroMapRefresh", "onMarkerReset")
                val resetFilter = ykws.android.maro.data.model.ListFilter()
                viewModel.updateSettings { s ->
                    if (s.markerFilterLinked) s.copy(markerListSort = ykws.android.maro.data.model.ListSortState(), markerListFilter = resetFilter, markerMapFilter = resetFilter)
                    else s.copy(markerListSort = ykws.android.maro.data.model.ListSortState(), markerListFilter = resetFilter)
                }
                markersViewModel.refreshSort(filter = resetFilter)
            },
            // ── Marker map referential (menu filter) + link ───────────────
            onMarkerMapFilterChange = { newFilter ->
                val linked = appSettings.markerFilterLinked
                viewModel.updateSettings { s ->
                    if (s.markerFilterLinked) s.copy(markerListFilter = newFilter, markerMapFilter = newFilter)
                    else s.copy(markerMapFilter = newFilter)
                }
                if (linked) markersViewModel.refreshSort(filter = newFilter)
            },
            onMarkerMapReset = {
                val resetFilter = ykws.android.maro.data.model.ListFilter()
                viewModel.updateSettings { s ->
                    if (s.markerFilterLinked) s.copy(markerListFilter = resetFilter, markerMapFilter = resetFilter)
                    else s.copy(markerMapFilter = resetFilter)
                }
                if (appSettings.markerFilterLinked) markersViewModel.refreshSort(filter = resetFilter)
            },
            markerFilterLinked = appSettings.markerFilterLinked,
            onToggleMarkerLink = {
                // Pure flip: no filter carry-over. Next linked edit writes both.
                viewModel.updateSettings { s -> s.copy(markerFilterLinked = !s.markerFilterLinked) }
            },
            onCreateFirst = {
                showMarkerManagement = false
                closeSelectedItemDashboards()
                markersViewModel.startWizard(initialPos = mapCenter)
            },
            onSetIcon = { id, icon -> markersViewModel.setMarkerIcon(id, icon) },
            onSetPin = { id, pinned -> markersViewModel.setMarkerPinned(id, pinned) },
            onUpdateMarkerText = { id, name, desc -> markersViewModel.updateMarkerText(id, name, desc) },
            // ── List-detail navigation ──────────────────────────────────
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
            onShareTrack = { id -> shareTrackGpx(context, trackViewModel, id, trackScope, onProgress = { trackOpStatus = it }) },
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
                } else {
                    markersViewModel.closeDrawer()
                }
            },
        )
        }

        // ── Post-save undo → stack entry ────────────────────────────────
        val lastSavedId by markersViewModel.lastSavedMarkerId.collectAsState()
        LaunchedEffect(lastSavedId) {
            val id = lastSavedId ?: return@LaunchedEffect
            val savedMarker = userMarkers.find { it.id == id }
            enqueueSnack(ActiveSnack.CreateUndo(id, savedMarker?.name ?: "Unknown"))
        }

        // ── Vertical snackbar stack at the bottom of the map area (render in MapSnackbarHost) ──
        MapSnackbarHost(
            activeSnacks = activeSnacks,
            isLandscape = isLandscape,
            portraitDashboardHeight = portraitDashboardHeight,
            landscapeDashboardWidth = landscapeDashboardWidth,
            onUndo = { onSnackUndo(it) },
            onTimeout = { onSnackTimeout(it) }
        )

        // ── Windowed sheets + dialogs (exit/stop, recovery, permission, source-switch, battery) ──
        MapDialogHost(
            context = context,
            trackViewModel = trackViewModel,
            // ── Exit / stop-recording sheets ──
            showExitDialog = showExitDialog,
            onExitSheetSave = {
                showExitDialog = false
                trackViewModel.stopRecording()
                kotlinx.coroutines.MainScope().launch {
                    kotlinx.coroutines.delay(300)
                    context.stopService(Intent(context, ykws.android.maro.data.track.TrackRecordingService::class.java))
                    context.findActivity()?.finishAffinity()
                }
            },
            onExitSheetContinue = {
                showExitDialog = false
                context.findActivity()?.moveTaskToBack(true)
            },
            onExitSheetDiscard = {
                showExitDialog = false
                trackViewModel.discardRecording()
                kotlinx.coroutines.MainScope().launch {
                    kotlinx.coroutines.delay(300)
                    context.stopService(Intent(context, ykws.android.maro.data.track.TrackRecordingService::class.java))
                    context.findActivity()?.finishAffinity()
                }
            },
            onExitSheetDismiss = { showExitDialog = false },
            showStopRecordingSheet = showStopRecordingSheet,
            onStopSheetSave = {
                showStopRecordingSheet = false
                trackViewModel.stopRecording()
            },
            onStopSheetContinue = { showStopRecordingSheet = false },
            onStopSheetDiscard = {
                showStopRecordingSheet = false
                trackViewModel.discardRecording()
            },
            onStopSheetDismiss = { showStopRecordingSheet = false },
            // ── Process-death recovery ──
            recoveryTrack = recoveryTrack,
            // ── Background location permission (A2) ──
            showBgLocationDialog = showBgLocationDialog,
            closeBgLocationDialog = { showBgLocationDialog = false },
            // ── GPS permission-missing (once per episode) ──
            gpsPermissionMissing = gpsPermissionMissing,
            gpsPermissionDialogDismissed = gpsPermissionDialogDismissed,
            gpsMode = appSettings.gpsMode,
            dismissGpsPermissionDialog = { gpsPermissionDialogDismissed = true },
            // ── GPS source-switch confirmation ──
            pendingGpsModeToggle = pendingGpsModeToggle,
            clearPendingGpsModeToggle = { pendingGpsModeToggle = null },
            applyGpsMode = applyGpsMode,
            // ── Battery optimization (A4) ──
            showBatteryOptDialog = showBatteryOptDialog,
            closeBatteryOptDialog = { showBatteryOptDialog = false }
        )

        // ── Single-GPX import conflict sheet (Duplicate / Override / Cancel) — host in MapImportConflictHost ──
        MapImportConflictHost(
            pendingTrackImport = pendingTrackImport,
            context = context,
            trackViewModel = trackViewModel,
            trackScope = trackScope,
            clearPending = { pendingTrackImport = null },
            setTrackOpStatus = { trackOpStatus = it },
            showImportBanner = { b -> showImportBanner(b) }
        )

        // ── Resume confirmation dialog (optional backup) — hosted outside the drawers so closing the
        //    source surface cannot drop it. `resumeTarget` drives dismissal; the retained copy keeps
        //    the dialog mounted while it animates out. ──
        val resumeBackupSuffix = stringResource(R.string.track_backup_suffix)
        val resumeTarget = pendingResume
        var resumeRetained by remember { mutableStateOf<PendingTrackResume?>(null) }
        LaunchedEffect(resumeTarget) {
            if (resumeTarget != null) resumeRetained = resumeTarget
        }
        if (resumeRetained != null) {
            var backup by remember(resumeTarget?.trackId) { mutableStateOf(true) }
            ConfirmDialog(
                title = stringResource(R.string.resume_confirm_title),
                visible = resumeTarget != null,
                onDismiss = { pendingResume = null },
                message = stringResource(R.string.resume_confirm_message),
                options = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = backup,
                                role = Role.Checkbox,
                                onValueChange = { backup = it }
                            )
                            .semantics(mergeDescendants = true) {},
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = backup,
                            onCheckedChange = null,
                            colors = CheckboxDefaults.colors(
                                checkedColor = ComposeColor(AppConfig.uiAccent)
                            )
                        )
                        Text(
                            stringResource(R.string.resume_confirm_backup),
                            color = ComposeColor(AppConfig.uiTextPrimary),
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                },
                actions = listOf(
                    ConfirmAction(stringResource(R.string.action_resume), ConfirmActionRole.PRIMARY) {
                        resumeRetained?.let { pending ->
                            trackViewModel.resumeTrack(
                                pending.trackId,
                                if (backup) resumeBackupSuffix else null
                            )
                            if (pending.fromList) showTrackHistory = false else closeTrackDrawer()
                        }
                        pendingResume = null
                    },
                    ConfirmAction(stringResource(R.string.action_cancel), ConfirmActionRole.SECONDARY) {
                        pendingResume = null
                    }
                )
            )
        }

        // ── Hoisted list-drawer confirmations (merge / batch delete) ──────────
        // Painted on the ladder so their scrim covers the drawers and the map, while the source
        // drawer stays open behind them.
        ConfirmRequestHost(state = confirmDialogHost)

        // ── Screen lock: full-screen input scrim + top-most unlock button ──
        //     The scrim consumes every pointer event so nothing below it (map,
        //     dashboard, drawers, controls) receives touch while locked. The
        //     duplicate button sits above the scrim so the lock can be toggled off.
        val lockTopInset = with(LocalDensity.current) {
            val raw = WindowInsets.statusBars.getTop(this).toDp()
            if (isLandscape) raw else (raw - 6.dp).coerceAtLeast(0.dp)
        }
        if (screenLocked) {
            LockScrim(
                onInterceptedTap = {
                    lockBanner = true
                    lockBannerAt = SystemClock.elapsedRealtime()
                }
            )
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
    importBanner: ImportBannerState? = null,
    trackOpStatus: String? = null,
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
            val base = filterRegulatedZones(regulatedZones, appSettings.boatSizeM) { appSettings.isCategoryVisible(it) }
            if (appSettings.regulatedZonesVisible || base == null) {
                base
            } else {
                val boat = mapCenter
                val radius = appSettings.zoneAutoRevealDistanceM.toDouble()
                val nearby = remember(boat, radius, base, appSettings.speedZoneAutoShow, appSettings.regulatedZoneAutoShow) {
                    base.zones.filter { z ->
                        z.isNear(boat, radius) && (
                            (z.speedLimitKn != null && appSettings.speedZoneAutoShow) ||
                            (z.hasNonSpeedCategory() && appSettings.regulatedZoneAutoShow)
                        )
                    }
                }
                if (nearby.isEmpty()) null else base.copy(zones = nearby)
            }
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
            zone300Color = appSettings.zone300Color,
            zone300FillTransparencyPct = appSettings.zone300FillTransparencyPct,
            zone300BoundaryTransparencyPct = appSettings.zone300BoundaryTransparencyPct,
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
                                .padding(start = 6.dp, end = RIGHT_CONTROL_COLUMN_INSET),
                            contentAlignment = Alignment.Center
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
                                    color = ComposeColor(AppConfig.uiToastText),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Start,
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
                    // Add Zone button (same size/style as FanLayout buttons, opens wizard at TypeSelect)
                    MapControlButton(
                        onClick = { onAddZone(mapCenter) },
                        modifier = Modifier.alpha(if (anyFanOpen) 0f else 1f)
                    ) {
                        AddLocationAltIcon()
                    }
                    Spacer(modifier = Modifier.height(6.dp))

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

        // ── Import feedback banner (bottom, centered left of the zoom column) ──
        importBanner?.let { banner ->
            val message = when (banner) {
                is ImportBannerState.Result ->
                    if (banner.imported == 0 && banner.ignored == 0) {
                        stringResource(R.string.import_failed)
                    } else {
                        val importedText = pluralStringResource(
                            R.plurals.import_result_imported, banner.imported, banner.imported
                        )
                        val ignoredText = pluralStringResource(
                            R.plurals.import_result_ignored, banner.ignored, banner.ignored
                        )
                        stringResource(R.string.import_result_format, importedText, ignoredText)
                    }
                ImportBannerState.Failed -> stringResource(R.string.import_failed)
            }
            MapStatusBanner(
                message = message,
                modifier = Modifier.align(Alignment.BottomStart)
            )
        }

        // ── Transient track-operation status banner (export/import in progress) ──
        trackOpStatus?.let { message ->
            MapStatusBanner(
                message = message,
                modifier = Modifier.align(Alignment.BottomStart)
            )
        }
    }
}

// ── Settings overlay (full-screen page) ─────────────────────────────────────

// Tab definitions for the settings page.
internal val settingsTabLabels = listOf(
    R.string.settings_tab_layers,
    R.string.settings_tab_navigation,
    R.string.settings_tab_position,
    R.string.settings_tab_system
)




