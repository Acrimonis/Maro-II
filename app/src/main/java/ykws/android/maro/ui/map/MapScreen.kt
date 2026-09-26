
package ykws.android.maro.ui.map
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.track.TrackRecordingService
import ykws.android.maro.data.model.matchesFilter
import ykws.android.maro.data.track.toGpx
import ykws.android.maro.data.track.ImportMode
import ykws.android.maro.spatial.RouteEngineChoice
import ykws.android.maro.spatial.RouteEngineState
import ykws.android.maro.spatial.avoid.AvoidWorld
import ykws.android.maro.spatial.avoid.LiveAvoidWorld

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import android.view.MotionEvent
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.semantics.contentDescription
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.core.content.ContextCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
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
import ykws.android.maro.data.power.BatteryExemption
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
import ykws.android.maro.ui.components.OptionRow
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.data.track.TrackFromCourse
import ykws.android.maro.spatial.SpatialOperations
import ykws.android.maro.ui.map.MarkersViewModel
import ykws.android.maro.ui.map.MarkerDrawer
import ykws.android.maro.ui.map.toMarkerSnapshot

/** Animation duration per GPS-follow scroll (ms). Must be < min GPS fix interval (1s). */
private const val GPS_ANIMATION_DURATION_MS = 600L

/** Right-edge control column width (12 gap + 64 button + 6 end). Paint-only reserve for transient overlays; the map itself is never padded by this. */
internal val RIGHT_CONTROL_COLUMN_INSET = 82.dp

/** Gutter (dp) between two of those squares — the row's start inset too, and the legend's, the
 *  regulated-zone column's and the bottom-left strip's with it. Read from the palette's
 *  `ui.map.toggle.gutter` (default 6 dp) — the one reader of that key. */
internal val TOP_TOGGLE_GUTTER: Dp get() = AppConfig.uiMapToggleGutter.dp

/** 0-based slot of the lock square in the top-left row: GPS, record, earth/water, inspect, lock —
 *  one less while the earth/water square is hidden by its setting, which [lockSlot] decides. */
private const val TOP_TOGGLE_LOCK_SLOT = 4

/**
 * The lock square's slot in a row drawn with or without the earth/water square. The row's order is
 * this value's dependency: hiding that square takes the slot down by one, so the locked-screen mirror
 * stays over the original. [topToggleSlotOffset] still owns the arithmetic.
 */
private fun lockSlot(earthWaterShown: Boolean): Int =
    if (earthWaterShown) TOP_TOGGLE_LOCK_SLOT else TOP_TOGGLE_LOCK_SLOT - 1

/**
 * Start offset (dp) of the square at [slot] in that row — the row's own start gutter, then one
 * square plus one gutter per slot before it. Derived from the row's order rather than repeated as a
 * literal, which is why inserting the inspect square moved the locked-screen mirror by one constant.
 */
internal fun topToggleSlotOffset(slot: Int): Dp =
    TOP_TOGGLE_GUTTER + (TOP_TOGGLE_SQUARE + TOP_TOGGLE_GUTTER) * slot
/** Height (dp) of the map's top-left toggle-button row — the icon squares the chrome stacks on,
 *  so it is `ui.map.toggle.square` itself. */
private val TOP_TOGGLE_ROW_HEIGHT: Dp get() = TOP_TOGGLE_SQUARE
/** How much tighter (dp) the status-bar inset is taken in portrait than in landscape. */
private val PORTRAIT_CHROME_TIGHTENING = 6.dp

/**
 * Top of the map's chrome (dp): the status bar's inset in full in landscape, pulled
 * [PORTRAIT_CHROME_TIGHTENING] tighter in portrait. The toggle row starts here, and the chrome that
 * stacks below it adds [TOP_TOGGLE_ROW_HEIGHT] plus its own gap — [legendTopOffset] takes the row's
 * own [TOP_TOGGLE_GUTTER], since the strip sits as far below the row as its buttons do from each other.
 */
@Composable
private fun chromeTopInset(isLandscape: Boolean): Dp = with(LocalDensity.current) {
    val statusBar = WindowInsets.statusBars.getTop(this).toDp()
    if (isLandscape) statusBar else (statusBar - PORTRAIT_CHROME_TIGHTENING).coerceAtLeast(0.dp)
}

/**
 * The legend's own top offset (dp): the chrome inset, the toggle row it sits under, and the row's own
 * gutter. The strip must sit as far below the row as the row's buttons sit from each other, so the gap
 * is [TOP_TOGGLE_GUTTER] rather than one of its own. Both orientations share [chromeTopInset] — the
 * landscape split only pads the *start* of the map column — so the legend clears the row by exactly
 * that gutter in either one.
 */
private fun legendTopOffset(chromeTop: Dp): Dp = chromeTop + TOP_TOGGLE_ROW_HEIGHT + TOP_TOGGLE_GUTTER

/**
 * Computed polyline rendering appearance: ARGB colour plus [strokeWidth], which is dp like every
 * other stored width — the paint site multiplies it by its own density before osmdroid sees px.
 */
data class TrackPolylineAppearance(val argb: Int, val strokeWidth: Float)

/** Uniform on-screen spacing (dp) between direction arrows. */
internal const val DIRECTION_ARROW_SPACING_DP = 48f

/** Log-scale gap slider bounds (dp). */
internal const val DIRECTION_GAP_MIN_DP = 4f
internal const val DIRECTION_GAP_MAX_DP = 640f
/** Log-scale speed slider bounds (kn). */
internal const val DIRECTION_SPEED_MIN_KN = 2f
internal const val DIRECTION_SPEED_MAX_KN = 64f

/**
 * One-shot target for click-N-move navigation: dismiss list → animate map → open drawer.
 *
 * [worldIds] is the walk world the card is opened on — the frozen inspect ladder when the mode hands
 * one over, null for the list world — and [source] says which world that is, so a card opened from a
 * list keeps walking the list even while the mode is armed (plan §5).
 */
private data class NavigateTarget(
    val geoPoint: GeoPoint,
    val markerId: String,
    val worldIds: List<String>? = null,
    val source: DrawerSource = DrawerSource.LIST
)

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
    val mapWasInteracted: Boolean = false,
    /**
     * Inspect mode's provenance flag (plan §5): the frozen distance ladder, in walk order, when this
     * card was opened by an inspect pick, and null when it was opened anywhere else. It decides
     * whether the drawer's Prev/Next read the ladder or the list world, so a list-opened track keeps
     * walking the list even while the mode is armed.
     */
    val inspectLadder: List<String>? = null
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
    onTimeout: () -> Unit,
    /** False for a message with nothing to reverse — a failure says what happened and no more. */
    showUndo: Boolean = true
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
            if (showUndo) {
                Spacer(Modifier.width(12.dp))
                androidx.compose.material3.TextButton(onClick = onUndo) {
                    Text(stringResource(R.string.action_undo), color = ComposeColor(0xFF80CBC4), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
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
/**
 * The track's own fade value as an alpha fraction (D8) — the index-over-total reading between the
 * two transparency ranges that the appearance factory has always taken, extracted so the banded path
 * reuses it instead of re-deriving it. A history track fades newest to oldest, a pinned one across
 * its own range, and both multiply the ramp's core alpha on the banded path.
 */
internal fun trackFadeAlpha(
    index: Int,
    total: Int,
    transparencyNewest: Int,
    transparencyOldest: Int
): Float {
    val newest = minOf(transparencyNewest, transparencyOldest)
    val oldest = maxOf(transparencyNewest, transparencyOldest)
    val alphaNewest = (100 - newest) / 100f   // newest (index 0) -> lower transparency = higher alpha
    val alphaOldest = (100 - oldest) / 100f   // oldest -> higher transparency = lower alpha
    val t = if (total <= 1) 0f else index.toFloat() / (total - 1).toFloat()
    return (alphaNewest - t * (alphaNewest - alphaOldest)).coerceIn(0f, 1f)
}

internal fun computeTrackPolylineAppearance(
    index: Int,
    total: Int,
    transparencyNewest: Int,
    transparencyOldest: Int,
    colorFrom: Int,
    colorTo: Int,
    /** The stroke the appearance carries, in dp — [TrackPolylineAppearance.strokeWidth]. */
    strokeWidth: Float = 2f
): TrackPolylineAppearance {
    val t = if (total <= 1) 0f else index.toFloat() / (total - 1).toFloat()
    val alphaInt = (trackFadeAlpha(index, total, transparencyNewest, transparencyOldest) * 255)
        .toInt().coerceIn(0, 255)

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
    val markerInZone300 by viewModel.markerInZone300.collectAsState()
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

    // ── Selected-track rendering override ──────────────────────────────
    // Both axes are stored state (`appSettings.trackArrows`, `appSettings.trackColours`), written by
    // the menu's twin box, so the map only reads them. The drawer eye's own value (D10) is stored
    // beside them in `appSettings.trackSelectionBanded`, on the selection rather than on any track id:
    // null means the eye has never been tapped and the selection mirrors the colours flag, true bands
    // that one track, false paints it gold. The first tap writes it and from then on it is the user's
    // own value, so it outlives the session; it moves that track's fill alone, since the chevrons
    // follow the arrows flag.
    var preNavigationState by remember { mutableStateOf<PreNavigationState?>(null) }
    var trackNavigateState by remember { mutableStateOf<TrackNavigateState?>(null) }
    var trackDrawerState by remember { mutableStateOf(TrackDrawerState()) }

    // ── Inspect mode (plan §1) ───────────────────────────────────────────
    // Session-lived and never persisted. The sleuth square arms a viewport pick; the state below is
    // the mode's own — the sweep's highlighted candidate, the merged ladder's cursor, the demo centre
    // captured at arming, and the map's touch boundaries the trigger clock reads its lifts from.
    var inspectArmed by rememberSaveable { mutableStateOf(false) }
    var inspectCandidate by remember { mutableStateOf<InspectRank?>(null) }
    var inspectCursor by remember { mutableStateOf<InspectCursor?>(null) }
    // The cursor as it stood before a step, kept for the whole of that step's open: the cursor
    // advances before the opener is called, so an open that dies must be able to put it back — left
    // on the successor's slot, the pills of the predecessor still on screen would describe a card
    // that never arrived (plan §5).
    var inspectCursorBeforeStep by remember { mutableStateOf<InspectCursor?>(null) }
    var inspectCapturedDemoCenter by remember { mutableStateOf<GeoPoint?>(null) }
    // The canonical guard (plan §6): true once the user moved the map themselves after the card
    // opened, so the close leaves the frame where they put it — no demo centre back, no GPS re-pin —
    // exactly as a list-opened card leaves it. The mode's own camera never counts, and the flag is
    // cleared at every new open and at the mode's exit.
    var inspectMapMovedByUser by remember { mutableStateOf(false) }
    // True from the moment an inspect pick — or an armed tap on a marker — opens a card until that card
    // closes. It is the *intent* rather than the fact, which is why it is set before an opener is called
    // and survives the whole of that asynchronous open, including the window a cross-type step keeps the
    // predecessor on screen for. The armed half ends when this card lands, so it is also the flag the
    // follow gates are keyed on (§5) — the card, never the armed flag, is what holds the centre.
    var inspectCardOpen by remember { mutableStateOf(false) }
    // The one owner of "an open is in flight" (plan §5): the successor that open is flying towards plus
    // the predecessor card held on screen until it lands. One value rather than an in-flight flag beside
    // a separate target, so the mode can never be left waiting for an open that will not land — clearing
    // this *is* the landing, and nothing else has to agree about what is expected.
    var inspectHandoff by remember { mutableStateOf<InspectHandoff?>(null) }
    // Bumped at every genuine finger lift on the map: the only event that may start the trigger
    // clock, so the arming seed and a resting finger can never pick (plan §5).
    var mapLiftId by remember { mutableIntStateOf(0) }
    // Bumped for every user action that is neither map motion nor a panel change — the lock square,
    // a layer chip, a settings write. One bump site rather than a reset scattered through each
    // handler: the mode folds it with the map's own motion into its single activity tick (plan §5).
    var mapActivityId by remember { mutableIntStateOf(0) }
    LaunchedEffect(screenLocked, appSettings) { mapActivityId++ }
    // Bumped at the end of every canonical track-rebuild pass: the mode re-stacks its own candidate
    // overlay on each bump, because a rebuild can float other tracks above it.
    val trackRebuildGeneration = remember { mutableStateOf(0) }

    // ── Route destination mode (FEAT_DSC_Route, destination-ui) ──────────────
    // Session-lived like inspect's own state. The ViewModel owns every piece of route runtime state;
    // what lives here is the mode's *switch* — the one flag the toggle writes — so the two modes can
    // be mutually exclusive in one place.
    //
    // The **selection** is built here from the setting and the registry: one live engine instance for
    // the chosen id, rebuilt whenever the setting moves. The ViewModel resolves it at arm time (D5), so
    // a change to the setting while a route runs cannot touch the line already drawn. What matters at
    // this line is the **seam**: a new algorithm is one row in the registry and nothing else in the
    // feature.
    // The avoid engine's world provider, built over the repositories the map already holds — the same
    // instances the water, band and depth reads go through. It always answers a live world over them,
    // so a layer that lands after the engine is built is read on the next search.
    val avoidWorldProvider: () -> AvoidWorld = {
        LiveAvoidWorld(
            viewModel.coastlineRepository,
            depthViewModel.depthRepository,
            zonesProvider = { viewModel.speedZones.value },
            excludedZoneIds = { appSettings.excludedSpeedZoneIds }
        )
    }
    val routeEngineSelection = remember {
        MutableStateFlow(
            RouteEngineChoice.resolve(appSettings.routeEngineId)
                .factory({ appSettings.routeFreeWaterPaceKn.toDouble() }, avoidWorldProvider)
        )
    }
    LaunchedEffect(appSettings.routeEngineId) {
        routeEngineSelection.value =
            RouteEngineChoice.resolve(appSettings.routeEngineId)
                .factory({ appSettings.routeFreeWaterPaceKn.toDouble() }, avoidWorldProvider)
    }
    val routeViewModel: RouteViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel(
            factory = RouteViewModel.factory(routeEngineSelection)
        )
    val routeState by routeViewModel.state.collectAsState()
    val routeEngineState by routeViewModel.engineState.collectAsState()
    val routePaceKn by routeViewModel.paceKn.collectAsState()
    var routeArmed by rememberSaveable { mutableStateOf(false) }
    // The toggle's gate: the mode exists only where the route engine is ready, exactly as inspect's
    // square exists only where something is inspectable. Which engine that is — and what makes it
    // ready — is the engine's own answer; this reads the readiness and nothing else.
    val routeAvailable = routeEngineState.ready
    val routeSaveScope = rememberCoroutineScope()
    // The pin the saves start with and the one exit dialog's open flag. Held by the screen rather than
    // by the panel because the dialog's **three doors** — the toggle's off, the panel's own Exit and
    // the back key — all reach them from outside the panel's own composition (R23), and one dialog
    // reached by three doors needs one set of those values.
    var routePinned by remember { mutableStateOf(false) }
    var routeExitRequested by remember { mutableStateOf(false) }
    // **The session's link table and the running stage**, read reactively: the first is the one fact
    // both `Save track` actions grey themselves on (R16, R17) and the second is the acquisition's own
    // progress (R15).
    val routeSessionLinks by routeViewModel.sessionLinks.collectAsState()
    val routeStage by routeViewModel.stage.collectAsState()
    // **Is the front route already written?** — the one fact both `Save track` actions grey themselves
    // on (R16, R17). Read through the link table rather than a null check at each call site.
    val routeFrontSaved = routeState.plan?.let { routeSessionLinks[it] != null } == true
    // **The phase the mode is in**, as one value: the machine's own state while the mode is on, and
    // IDLE the moment the switch is off. It is what the couplings key on (R20, R21) — the demo
    // suspension, the camera's hold and the toggle's two on-phases all read this rather than the
    // toggle, because the toggle cannot tell choosing from following.
    val routePhase = if (routeArmed) routeState.phase else RoutePhase.IDLE
    // **The refusal, held as the id of the line a user reads** (§17 item 3): the engine's closed-set
    // reason carries a `@StringRes`, and the surface that shows it resolves it — so no engine holds
    // user-facing text and both locales carry the key. It is transient, like the import's own feedback,
    // and clears itself so a second tap is never read against a stale sentence.
    var routeRefusalResId by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(routeRefusalResId) {
        if (routeRefusalResId != null) {
            delay(2_000L)
            routeRefusalResId = null
        }
    }

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
    // ── Selected-item dashboards — the two close rules ───────────────────
    // R1: a control that wants the dashboard's own slot closes it — the marker/track wizard (same size,
    // same position, slid in over the dashboard) or the other selected-item dashboard (one selected item
    // at a time). R2 (see `closeDashboardsForScopeChange`): a control that rewrites the referential the
    // open item's Prev/Next walk reads closes it. Everything else leaves it open, so the menu, the
    // settings page, both lists, the layer fan, the chips and every display-only control keep the
    // selection — which is what makes R2 a live rule rather than a dead guard.

    /** R1: closes the open marker detail dashboard (Viewing / MatchResult) — never the wizard. */
    fun closeMarkerDashboard() {
        val state = markersViewModel.drawerState.value
        if (state is MarkerDrawerState.Viewing || state is MarkerDrawerState.MatchResult) {
            markersViewModel.closeDrawer()
        }
    }

    /**
     * The pan's close: a drag on the map dismisses only the Where-Am-I card, and through the same
     * [MarkersViewModel.closeDrawer] call [closeMarkerDashboard] uses. The reason is the card's own
     * content — the list it shows is a snapshot of the boat's position, and a drag leaves that snapshot
     * behind. The guard is MatchResult alone: a marker card being read (Viewing) and the wizard keep
     * their own rules.
     */
    fun closeWhereAmICard() {
        if (markersViewModel.drawerState.value == MarkerDrawerState.MatchResult) {
            markersViewModel.closeDrawer()
        }
    }

    /**
     * Closes the track detail drawer, restoring the pre-navigation camera when the map was untouched.
     *
     * Inspect (plan §5): while the mode owns the card, the card's own restore stands down and the disarm
     * capture is the single owner of the frame, so Back cannot fight it. The ladder on the state is what
     * tells the two apart — a card opened from a list carries none and restores exactly as it always did.
     */
    fun closeTrackDrawer() {
        if (trackDrawerState.inspectLadder == null && !trackDrawerState.mapWasInteracted) {
            preNavigationState?.let { pre ->
                mapView?.controller?.setZoom(pre.zoom)
                mapView?.controller?.setCenter(GeoPoint(pre.centerLat, pre.centerLon))
            }
        }
        highlightedTrackId = null
        trackDrawerState = TrackDrawerState()
        preNavigationState = null
    }

    /** R1 entry point: the wizard or the other dashboard wants the slot, so both dashboards stand down. */
    fun closeSelectedItemDashboards() {
        closeMarkerDashboard()
        closeTrackDrawer()
    }

    /**
     * R2 entry point: a referential the open item's Prev/Next walk reads was rewritten, so that dashboard
     * closes. Each caller passes the world its own change landed in — the list filter, the list sort and
     * the list reset pass [markerListWorld] / [trackListWorld]; the map filter and the map reset pass
     * [markerMapWorld] and, for a track, [trackListWorld] exactly when `trackFilterLinked` carried the
     * write into the list world the walk reads. A write to the other world is display-only.
     */
    fun closeDashboardsForScopeChange(
        markerListWorld: Boolean = false,
        markerMapWorld: Boolean = false,
        trackListWorld: Boolean = false
    ) {
        if (scopeClosed(markersViewModel.drawerSource, inListWorld = markerListWorld, inMapWorld = markerMapWorld)) {
            closeMarkerDashboard()
        }
        if (trackListWorld) closeTrackDrawer()
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
                // An inspect source would reopen the card with the mode's merged walk already stood down
                // and no cursor seated on it, leaving both buttons dead; the drawer's own map world keeps
                // them live (plan §8, folded review fix).
                val source = if (snack.source == DrawerSource.INSPECT) DrawerSource.MAP else snack.source
                markersViewModel.openEditDrawer(snack.selection, selectedId = snack.id, source = source)
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
    /**
     * The position the dashboard reads — the GPS fix in GPS mode, the same seam in demo mode — and
     * therefore the route's start. It is deliberately not the map centre: that is the aim, and a
     * route measured from it would have the boat chasing the point it is trying to choose.
     */
    val routeStart = dashboardPositionFor(mapCenter, gpsPosition, appSettings.gpsMode)

    /**
     * **The anchor's own reading** (R3): the boat's position with the course and speed the lead is
     * projected from, or **null wherever they cannot be trusted**.
     *
     * This is the freshness gate the plan leaves to the surface that owns the fix, and it carries
     * demo mode's exclusion with it: a demo position is the map centre and its pan-derived speed is
     * suspended while aiming, so there is no boat to project and no lead to take. A null here is read
     * as "the live fix and no lead" by the host and by the two acquisitions the panel opens, all of
     * which fall back rather than inventing a start.
     */
    val routeLeadFix: RouteFix? = if (appSettings.gpsMode && !gpsStale) {
        RouteFix(
            position = RoutePoint(routeStart.latitude, routeStart.longitude),
            courseDeg = navigationState.bearingDeg.toDouble(),
            speedKn = navigationState.speedKnots?.toDouble()
        )
    } else null
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
            if (BatteryExemption.shouldPrompt(context, appSettings)) {
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
        autoFollowSuppressed = autoFollowSuppressed,
        // The mode's clock may only start on a genuine finger lift, counted here at the map's own
        // touch boundaries. osmdroid owns the gesture, so the hook rides on the listener that already
        // exists and never consumes rather than installing a second one, which would replace it.
        onMapTouch = { action ->
            if (action == MotionEvent.ACTION_UP) mapLiftId++
        },
        // The drag's own close, not the recorder's idle exit's: a card the user opened by their own tap
        // has no recording behind it, so nothing else would ever dismiss it on a drag. A pinch, a
        // double-tap and the zoom buttons never reach this callback — the gate is one finger and slop.
        onMapPan = { closeWhereAmICard() }
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
    // The marker card's own id (plan §5): the landing rule reads it to recognise the successor of an
    // inspect open, so the one declaration sits above the overlay it is also painted from.
    val selectedMarkerId by markersViewModel.selectedMarkerId.collectAsState()
    val markerLayerState by markersViewModel.markerLayerState.collectAsState()
    val markerLayerVisible = markerLayerState != MarkerLayerState.HIDDEN
    // Map-referential markers: the map overlay renders the MAP filter world (list uses markers).
    val mapMarkersState by markersViewModel.mapMarkers.collectAsState()

    // Wire coastline spatial index into MarkersViewModel for land-blocking when ready
    if (coastlineReady) {
        markersViewModel.coastlineIndex = viewModel.spatialIndex
    }

    // Wire the same coastline into the track repository's position classifier, once it can answer:
    // the tracks recorded before it loaded then get their sampled counts on the reload that follows,
    // which is what the track list's On water / On land filter reads.
    LaunchedEffect(coastlineReady) {
        if (!coastlineReady) return@LaunchedEffect
        val bounds = viewModel.coastlineRegionBounds ?: return@LaunchedEffect
        trackViewModel.attachPositionClassifier(
            waterTest = { lat, lon -> viewModel.isWaterOrNull(lat, lon) },
            region = bounds
        )
    }

    // ── Inspect mode's inspectable set (plan §3) ─────────────────────────
    // One source for the sweep and the ladder: the map-filtered items whose layer is showing. The
    // render cap is not a restriction — a filtered track beyond it is a legitimate pick and the mode's
    // own overlay draws it — while a hidden layer contributes nothing, so the mode can never
    // resurrect what the user switched off. The live recording has no card behind it and is excluded.
    val inspectMarkerCandidates = if (markerLayerVisible) mapMarkersState else emptyList()
    val inspectTrackIds = if (appSettings.tracksVisible) {
        val midnightMs = ykws.android.maro.data.model.todayMidnightMs()
        allTrackSummaries
            .filter { !it.isLive && it.matchesFilter(appSettings.trackMapFilter, midnightMs) }
            .map { it.id }
    } else emptyList()

    /** Whether anything at all is inspectable — the sleuth square's disabled gate while disarmed. */
    val inspectAvailable = inspectMarkerCandidates.isNotEmpty() || inspectTrackIds.isNotEmpty()

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
                        // R1: the Where-Am-I dashboard is the other selected-item dashboard, so a live
                        // track dashboard closes first (one selected item at a time).
                        closeTrackDrawer()
                        val pos = gpsPosition ?: mapCenter
                        markersViewModel.whereAmI(pos)
                    }
                }
                is ykws.android.maro.data.track.TrackEvent.DrawerAutoCloseRequested -> {
                    // The recorder's idle exit dismisses the Where-Am-I card, a drag included: a drag
                    // moves the boat in demo mode, and reading real travel is the exit's own reason.
                    if (markersViewModel.drawerState.value == MarkerDrawerState.MatchResult) {
                        markersViewModel.closeDrawer()
                    }
                }
                is ykws.android.maro.data.track.TrackEvent.Resumed -> {
                    // Restore checkpoint points to the live polyline on Continue.
                    // The polyline may not exist yet (Compose hasn't recomposed after state→ON),
                    // so create it directly if needed.
                    val mv = mapView ?: return@collect
                    // The live line's widths and its GAP dash are dp, like the whole stored table:
                    // the map's own density is what turns them into the px osmdroid paints with.
                    val density = mv.paintDensity
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
                                        outlinePaint.strokeWidth = dpToPx(AppConfig.trackWidthLiveDp, density)
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
                                outlinePaint.strokeWidth = dpToPx(AppConfig.trackWidthLiveDp, density)
                                outlinePaint.pathEffect = android.graphics.DashPathEffect(
                                    floatArrayOf(
                                        dpToPx(TRACK_GAP_DASH_ON_DP, density),
                                        dpToPx(TRACK_GAP_DASH_OFF_DP, density)
                                    ),
                                    0f
                                )
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
                                outlinePaint.strokeWidth = dpToPx(AppConfig.trackWidthLiveDp, density)
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
    // The ids that effect actually painted — history and pinned — read by the legend gate below rather
    // than recomputed there: the effect alone knows which summaries settled into an overlay.
    val paintedTrackIds = remember { mutableStateOf(setOf<String>()) }
    MapTrackOverlayHistoryDiff(
        mapView = mapView,
        showSettings = showSettings,
        highlightedTrackId = highlightedTrackId,
        trackArrows = appSettings.trackArrows,
        trackColours = appSettings.trackColours,
        eyeOverride = appSettings.trackSelectionBanded,
        allTrackSummaries = allTrackSummaries,
        focus = trackViewModel.renderFocus,
        appSettings = appSettings,
        paintedTrackIds = paintedTrackIds,
        trackViewModel = trackViewModel,
        rebuildGeneration = trackRebuildGeneration
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

            // ── The bottom-left tag stack's own set, and the band's one tag answer ─────────────────
            // The raw zones filtered by boat size and per-category visibility: one home, because the
            // band's banner clearance reads the very same answer, and the locked-screen banner is drawn
            // outside MapContent — where it would otherwise have to filter a second time.
            val tagRegulatedZones = remember(regulatedZones, appSettings) {
                filterRegulatedZones(regulatedZones, appSettings.boatSizeM) { appSettings.isCategoryVisible(it) }
            }
            // Whether that stack draws at all — the Boolean every banner in the band reads, derived here
            // once from the set beside it rather than re-derived by each caller around `isNotEmpty()`.
            val bandTagsDrawn = remember(tagRegulatedZones, mapCenter, markerInZone300) {
                regulatedZoneTags(tagRegulatedZones, mapCenter, markerInZone300).isNotEmpty()
            }

            // ── Inspect mode: arming, disarming and the quiet openers (plan §5, §6) ──
            // The offset in pixels is what the anchor needs: `mapView.mapCenter` is the *plain* screen
            // centre, so the geo point under the marker is read at `centre + offset` — the check behind
            // that is recorded on `inspectAnchor`, whose fallback is the implementation.
            val inspectOffsetPx = with(LocalDensity.current) { mapCenterOffsetDp.roundToPx() }

            // ── The selected-item opener, above the mode that calls it ─────────────────────────────
            // The mode's pick and every step call it directly and a local function cannot be
            // forward-referenced, so it is declared here rather than beside the menu shortcuts that also
            // use it. Nothing in it is inspect-specific: the world to walk is the one parameter the mode
            // differs by, which is what makes the camera, the capture and the look the selection's own.
            val mgmtMarkers by markersViewModel.markers.collectAsState()

            /**
             * The inspect ladder's own resolver (plan §5): the map-filtered markers — the very
             * collection the ranking was built from — read live off its flow rather than snapshotted,
             * because a snapshot would answer with the item as it stood when the ladder froze, so an
             * edit would show a stale card and a delete would keep resurrecting one.
             *
             * Handing this to the cursor is what makes the walk and the lookup incapable of disagreeing:
             * resolved against the list world instead, an id the map filter keeps but a narrower list
             * filter drops misses, and the step dies with nothing on screen. The line half needs no
             * counterpart — the ranking and the opener both load through `loadTrackDetailCached`.
             */
            val inspectMarkerLookup: (String) -> UserMarker? = { id ->
                markersViewModel.mapMarkers.value.find { it.id == id }
            }

            /**
             * Drops the in-flight hand-off when an inspect open lands no card at all (plan §5). The mode
             * itself stays armed, because nothing was opened and so nothing was exited.
             *
             * A predecessor held for that open is still on screen, so the mode's intent and its hold on
             * the centre stay with it — cleared here they would hand the centre back under a card that is
             * still up. Only an open that lands nothing with no card beneath it spends the intent.
             *
             * A step that dies also hands the cursor back to the slot the standing card shows, so the
             * walk keeps describing the card the user is actually looking at.
             */
            fun abandonInspectOpen() {
                inspectHandoff = null
                val cardOnScreen = markersViewModel.drawerState.value is MarkerDrawerState.Viewing ||
                    trackDrawerState.isOpen
                if (cardOnScreen) {
                    inspectCursorBeforeStep?.let { inspectCursor = it }
                } else {
                    inspectCardOpen = false
                    viewModel.setInspectCardOpen(false)
                }
                inspectCursorBeforeStep = null
            }

            /**
             * The one selected-item opener for a track, parameterised by the world its card will walk.
             *
             * [walkWorld] is that world, in walk order: the frozen inspect ladder when the mode hands one
             * over — a pick or a step — and null everywhere else, where the drawer derives the list world
             * itself, so a list-opened card keeps walking the list even while the mode is armed.
             * [candidates] is the run this call may open, in the same order: the one id a pick, a step or
             * a list selection seats on, or the slice a drawer's own Prev/Next walks, so an entry whose
             * geometry cannot load is skipped rather than swallowing the step. [freshSelection] is what a
             * selection earns and a step of an already-open card's walk does not — the layer forced on,
             * the list let go, and the pre-navigation frame captured at the map as it stands — so Back
             * returns to the frame the selection was made on and not to the last step's. Nothing here
             * reaches for the list once [walkWorld] is the ladder: the card carries the world it walks,
             * and that world alone is what its Prev/Next reads.
             *
             * [closeMarkerCard] is that R1 close, left standing for every caller but a cross-type step:
             * the step holds the marker card — it is the predecessor, and holding it is what keeps the
             * one selected-item slot filled while this open is in flight (plan §5).
             */
            fun openSelectedTrack(
                candidates: List<String>,
                walkWorld: List<String>? = null,
                freshSelection: Boolean = true,
                closeMarkerCard: Boolean = true,
                onNone: () -> Unit = {}
            ) {
                // R1: one selected item at a time — opening a track detail closes the marker detail
                // drawer. A walk step is a selection too in this one sense, but it defers that close to
                // its own landing instead, so the slot never empties between the two cards (plan §5).
                if (closeMarkerCard) closeMarkerDashboard()
                if (freshSelection) {
                    if (!appSettings.tracksVisible) {
                        viewModel.updateSettings { it.copy(tracksVisible = true) }
                    }
                    showTrackHistory = false
                }
                if (candidates.isEmpty()) {
                    onNone()
                    return
                }
                trackScope.launch {
                    var opened = false
                    for (candidateId in candidates) {
                        try {
                            val track = trackViewModel.loadTrackDetailCached(candidateId)
                            if (track == null || track.trackPoints.isEmpty()) continue

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

                            if (freshSelection) {
                                preNavigationState = mapView?.let { mv ->
                                    val c = mv.mapCenter
                                    PreNavigationState(mv.zoomLevelDouble, c.latitude, c.longitude)
                                }
                            }

                            highlightedTrackId = candidateId
                            trackDrawerState = TrackDrawerState(
                                isOpen = true,
                                track = track,
                                mapWasInteracted = false,
                                inspectLadder = walkWorld
                            )

                            if (bbox != null) {
                                trackNavigateState = TrackNavigateState(geoPoint, bbox, candidateId)
                            } else {
                                // Single-point track: just animate, no bounding box zoom
                                mapView?.controller?.animateTo(geoPoint, null, GPS_ANIMATION_DURATION_MS)
                            }
                            opened = true
                            break
                        } catch (_: Exception) {
                            // Silently fail on this candidate — track data unavailable — and let the next
                            // one have its turn.
                        }
                    }
                    // An open whose card can never arrive must not leave the inspect exit rule waiting
                    // for one that will not land.
                    if (!opened) onNone()
                }
            }

            /**
             * The canonical marker selection: the camera through [navigateToTarget] — animate for a pin,
             * zoom-to-fit for a circle or a corridor — then the drawer, on the walk world the target
             * carries. [walkWorld] hands over the frozen ladder and the inspect source; null keeps the
             * list world, so a list-opened marker card behaves exactly as it does today.
             */
            fun openMarkerDetail(
                id: String,
                walkWorld: List<String>? = null,
                /** False while a cross-type step holds the track card for this open's landing (§5). */
                closeTrackCard: Boolean = true,
                /**
                 * The world this id is resolved in. An inspect step hands over the ladder's own
                 * resolver, so the id is looked up in the collection its walk was ranked from; the
                 * default is the list world, which is what a list, menu or map route has always used.
                 */
                markerLookup: ((String) -> UserMarker?)? = null,
                /**
                 * Which world a non-null [walkWorld] is: the frozen ladder ([DrawerSource.INSPECT]) or
                 * the map-filtered collection an armed tap came from ([DrawerSource.MAP]). Ignored when
                 * there is no world, where the list world is the only world there is.
                 */
                walkWorldSource: DrawerSource = DrawerSource.INSPECT
            ) {
                val marker = (markerLookup ?: { lookupId -> mgmtMarkers.find { it.id == lookupId } })(id)
                if (marker == null) {
                    // Guarded on the in-flight open itself, never on the world this open walks: an
                    // armed tap's open walks the map world and is just as capable of landing nothing,
                    // and a hand-off left standing here would swallow every close and hold the centre
                    // until the toggle (plan §5).
                    if (inspectHandoff != null) abandonInspectOpen()
                    return
                }
                // R1: one selected item at a time — opening a marker detail closes the track detail
                // drawer, unless a cross-type step is holding it until this card lands (plan §5).
                if (closeTrackCard) closeTrackDrawer()
                markersViewModel.showLayer()
                showMarkerManagement = false
                navigateToTarget = NavigateTarget(
                    geoPoint = GeoPoint(marker.centerPoint.latitude, marker.centerPoint.longitude),
                    markerId = id,
                    worldIds = walkWorld,
                    source = if (walkWorld != null) walkWorldSource else DrawerSource.LIST
                )
            }

            /** Arms the mode. Demo's captured centre is the map's own, i.e. the point under the marker. */
            fun armInspectMode() {
                if (inspectArmed) return
                // The two modes are mutually exclusive: entering one leaves the other.
                if (routeArmed) routeArmed = false
                inspectMapMovedByUser = false
                inspectCapturedDemoCenter = if (appSettings.gpsMode) null else {
                    mapView?.let { mv ->
                        inspectAnchor(mv, inspectOffsetPx)?.let { GeoPoint(it.latitude, it.longitude) }
                    }
                }
                viewModel.armInspect()
                inspectArmed = true
            }

            /**
             * The demo half of the mode's single exit (plan §6): the centre captured at arming is put
             * back, and a map the user moved after the card opened is left alone, exactly as a
             * list-opened card leaves it. The capture is spent either way, so a later open and close
             * cannot resurrect it.
             */
            fun applyInspectDemoExit() {
                if (!inspectMapMovedByUser) {
                    inspectCapturedDemoCenter?.let { mapView?.controller?.setCenter(it) }
                }
                inspectCapturedDemoCenter = null
                inspectMapMovedByUser = false
            }

            /**
             * Disarms the mode and hands the feed back. The armed half stands down here; the retained
             * capture lands at the mode's *single* exit (plan §6), so with a card still standing this
             * only clears the walk state and leaves that card's close to apply the capture — this path
             * and the close can therefore never both restore. The GPS half is the ViewModel's
             * (`disarmInspect`, whose verdict this call reads); demo's is [applyInspectDemoExit],
             * because nothing else can put the demo centre back — the demo position is fed *from* the
             * centre, so the sweep has carried the boat across the map with it.
             */
            fun disarmInspectMode() {
                if (!inspectArmed) return
                inspectArmed = false
                inspectCandidate = null
                inspectCursor = null
                inspectCursorBeforeStep = null
                inspectHandoff = null
                if (viewModel.disarmInspect(mapMovedByUser = inspectMapMovedByUser)) {
                    applyInspectDemoExit()
                }
            }

            /**
             * The route mode's two edges, beside inspect's own.
             *
             * Off is the whole of the exit: ending the route and cancelling an unconfirmed draft are
             * one act, which is why this only turns the switch off and lets the ViewModel's own
             * `end` follow from the edge.
             *
             * **Entry is the engine's own gate, and a tap that finds it shut is the user's retry**
             * (§17 item 3). The engine prepares once at construction, and that one preparation can land
             * inside the coastline's own load — where it answers `Unavailable` and used to leave a square
             * that did nothing for the rest of the session. A tap therefore asks for **one** more
             * preparation, and an engine that still cannot arm has said so: its reason's own id is what
             * the surface shows, and nothing is asked a third time.
             */
            fun armRouteMode() {
                if (routeArmed) return
                // **The selection leaves before the mode arms** — the brief's own first item, and the
                // dashboard slot's R1 rule: the route panel wants the slot, so whatever selected-item
                // card held it stands down first rather than being raced by the panel's composition.
                fun arm() {
                    closeSelectedItemDashboards()
                    if (inspectArmed) disarmInspectMode()
                    routeArmed = true
                }
                if (!routeAvailable) {
                    routeSaveScope.launch {
                        val reached = routeViewModel.prepareAgain()
                        if (reached.ready) {
                            arm()
                        } else {
                            routeRefusalResId =
                                (reached as? RouteEngineState.Unavailable)?.reason?.labelResId
                        }
                    }
                    return
                }
                arm()
            }

            /**
             * The **silent** ending: the draft's Cancel, the back key while the destination is being
             * chosen, and every other leaving that is not a followed route's. Leaving the draft asks
             * nothing (R23) — the pin and the dialog's own state go with the mode.
             */
            fun endRouteMode() {
                if (!routeArmed) return
                routeArmed = false
                routePinned = false
                routeExitRequested = false
            }

            /**
             * **The one exit dialog** (R23), raised by each of its three doors: the toggle's off, the
             * panel's own **Exit** while a route is followed, and the back key.
             */
            fun requestRouteExit() {
                if (!routeArmed) return
                routeExitRequested = true
            }

            /**
             * **The panel's Exit and the back key: one rule, one meaning** (R23).
             *
             * While a route is followed this is the dialog. Inside the acquisition it is a **phase
             * move** — back to the route that stood behind it, which the machine restores — and only
             * when the acquisition stands on nothing is it an ending. Back and the panel's Exit cannot
             * diverge, which is why both come through here.
             */
            fun leaveRouteMode() {
                val choosing = routeState as? RouteState.Choosing
                when {
                    choosing == null -> requestRouteExit()
                    choosing.enteredFromRoute -> routeViewModel.exitAcquisition()
                    else -> endRouteMode()
                }
            }

            /**
             * **The toggle's own door** (R23). It is not the panel's Exit: turning the toggle off would
             * *end* the mode, so wherever a route stands behind the acquisition or a line has been
             * acquired and not yet confirmed, it asks the same dialog first — nothing a door would lose
             * goes silently. An acquisition standing on nothing ends on the spot.
             */
            fun toggleRouteOff() {
                val choosing = routeState as? RouteState.Choosing
                val holdsSomething = choosing == null || choosing.enteredFromRoute || choosing.plan != null
                if (holdsSomething) requestRouteExit() else endRouteMode()
            }

            /**
             * **Route**, and **Save as Track and Route** (R18): the phase turns to following and the
             * draft's two couplings are released **in that same frame** — the camera is handed back,
             * to the current fix in GPS mode and to the origin coordinate in demo mode, while the
             * phase change is what ends the demo suspension and the draft's hold on the resume
             * deadline.
             */
            fun followRoute() {
                val origin = (routeState as? RouteState.Choosing)?.start
                routeViewModel.confirm()
                if (appSettings.gpsMode) {
                    viewModel.recenterNow()
                } else {
                    origin?.let {
                        mapView?.controller?.setCenter(GeoPoint(it.latitude, it.longitude))
                    }
                }
            }

            /**
             * **Acquire route** — one acquisition from the aim **at that instant**, which is the screen
             * centre the ring is drawn at (R2).
             *
             * Nothing throttles it: the button is never disabled, a second press asks again, and the
             * newer answer becomes the front line. The aim is read here rather than held by the host,
             * because a drag no longer means anything to the machine.
             */
            fun routeAcquireAim() {
                val mv = mapView ?: return
                val anchor = inspectAnchor(mv, inspectOffsetPx) ?: return
                routeViewModel.acquire(RoutePoint(anchor.latitude, anchor.longitude))
            }

            /**
             * **Reroute** (R17): back into the acquisition from a **fresh anchor** and to the **same
             * destination**, with one acquisition fired at once so the line lands without a second
             * press. The session set survives, so the line being replaced stays drawn on the ladder.
             */
            fun rerouteRoute() {
                routeSaveScope.launch { routeViewModel.reroute(routeLeadFix) }
            }

            /**
             * **New route** (R17): the same move with the destination **cleared**, computing nothing
             * until `Acquire route` is pressed. The session set survives, so the earlier lines stay
             * drawn and stay offered.
             */
            fun newRoute() {
                routeSaveScope.launch { routeViewModel.newRoute(routeLeadFix) }
            }

            /**
             * Writes **one** route as an ordinary track, through `data/track`'s own repository. The
             * vertices carry the plan's own pace and cumulative time, so distance, duration and both
             * speed figures come out right with no second code path.
             *
             * The instant handed to the builder is the route's **generation and finalisation**, not
             * the save's (R40): one value dates the header and names the track. **Every save names
             * it** (R25): the name defaults to the route's own [`RoutePlan.trackName`] — `Route
             * <instant>`, the fixed prefix a name-as-data token rather than a localised string — and
             * an all-scope write hands in the same base with `· n/N`. The track's id is remembered
             * against the route, in the mode's own session, which is what lets a second save **rename**
             * it instead of writing it again.
             */
            fun saveRouteTrack(plan: RoutePlan, pin: Boolean, name: String? = null) {
                val points = plan.points
                if (points.size < 2) return
                val legs = points.drop(1).mapIndexed { index, point ->
                    TrackFromCourse.legBetween(
                        from = points[index],
                        to = point,
                        durationSec = plan.legTimesSec.getOrElse(index) { 0.0 }
                    )
                }
                val track = TrackFromCourse.build(
                    start = points.first(),
                    legs = legs,
                    pinned = pin,
                    createdAtMs = plan.computedAtMs,
                    name = name ?: plan.trackName()
                )
                routeSaveScope.launch {
                    val writtenId = trackViewModel.saveBuiltTrack(track)
                    routeViewModel.noteRouteSaved(plan, writtenId)
                }
            }

            /**
             * Opens or steps a card the inspect way (plan §5): the one selected-item opener, with the
             * frozen ladder as the world to walk in place of the list world, so any future change to how
             * a selection is framed or painted reaches the mode for free. A pick opens the way a list tap
             * does; a step walks the way a drawer's own Prev/Next does — both through the one opener,
             * which differs by nothing but the world handed over.
             *
             * This is also where the mode spends its capture: whatever camera the open goes on to set is
             * the frame that stands, so the captured follow state and the captured demo centre are
             * dropped here rather than restored by a later close. A step of an already-open card finds
             * the mode disarmed and spends nothing.
             */
            fun openInspectCard(
                id: String,
                kind: InspectKind,
                cursor: InspectCursor?,
                picked: Boolean,
                /**
                 * The collection an armed tap came from, already seated on the tapped marker. It is
                 * handed over only when no ladder is seated: before the first pick there is no frozen
                 * pass, and the map-filtered set the tap itself came from is the one world certain to
                 * hold the marker under the finger (plan §5).
                 */
                tapWorld: List<String>? = null
            ) {
                // The capture taken at arming is retained: a pick no longer spends it, and the follow
                // gates keep the centre held while this card stands. It lands at the mode's single
                // exit — this card's close (plan §6) — and the guard starts afresh with the new card.
                inspectMapMovedByUser = false
                // What the open must do about a card already on screen: the mode's own other-kind card
                // is held until this successor lands, so the slot's visibility OR never goes false and
                // the swap reads as an in-place content change (plan §5). The rule is pure and
                // unit-tested beside `inspectLanded`.
                val held = inspectHeldCard(
                    opening = kind,
                    markerCardOpen = markersViewModel.drawerState.value is MarkerDrawerState.Viewing,
                    markerCardInspectSourced = markersViewModel.drawerSource == DrawerSource.INSPECT,
                    trackCardOpen = trackDrawerState.isOpen,
                    trackCardInspectSourced = trackDrawerState.inspectLadder != null
                )
                // Set before the opener is called, because the landing rule reads it: this is the one
                // record of what this open is flying towards and of what must not go until it arrives.
                inspectHandoff = InspectHandoff(InspectTarget(id, kind), held)
                inspectCardOpen = true
                viewModel.setInspectCardOpen(true)
                when (kind) {
                    InspectKind.TRACK -> openSelectedTrack(
                        candidates = listOf(id),
                        walkWorld = cursor?.ladderIds,
                        freshSelection = picked,
                        closeMarkerCard = held == null,
                        onNone = { abandonInspectOpen() }
                    )
                    InspectKind.MARKER -> {
                        // With no ladder the walk is the map-filtered collection the tap came from,
                        // resolved through that collection's own lookup and walked by the map's
                        // clamped walk — never the list world, which may have filtered the tapped
                        // marker out and would seat the card on whichever marker happened to be
                        // first, with both pills pointing at a walk that cannot move (plan §5).
                        openMarkerDetail(
                            id = id,
                            walkWorld = cursor?.ladderIds ?: tapWorld,
                            closeTrackCard = held == null,
                            markerLookup = cursor?.resolveMarker
                                ?: if (tapWorld != null) inspectMarkerLookup else null,
                            walkWorldSource = if (cursor != null) DrawerSource.INSPECT else DrawerSource.MAP
                        )
                    }
                }
            }

            /**
             * A step of the merged walk: the cursor moves and the one card follows. A step landing on the
             * other type swaps the panel inside the same selected-item slot rather than closing one card
             * and opening the other, which is what makes the swap read as a content change (plan §5).
             */
            fun applyInspectStep(cursor: InspectCursor) {
                // One step at a time: while an open is in flight its successor owns the slot, and a
                // second step taken now would rewrite the navigate target, cancel the running open and
                // leave the first card never landing (plan §5). The buttons are greyed for that same
                // window; this is the guard that cannot be reached around.
                if (inspectHandoff != null) return
                val next = cursor.current ?: return
                // The slot stepped away from is kept for the whole of this open: the cursor advances
                // before the opener is called, and a step that dies must hand it back rather than
                // leave the predecessor's pills describing the successor's slot (plan §5).
                inspectCursorBeforeStep = inspectCursor
                inspectCursor = cursor
                inspectCandidate = null
                openInspectCard(next.id, next.kind, cursor, picked = false)
            }

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

            // ── Inspect card: the landing stands the mode down, the close is the whole exit ──
            // Back, the card's own close and a referential change all land here. The pick disarms the
            // mode the moment its card is on screen (plan §5), so nothing is left for the close to
            // restore: the capture was spent at the pick, and the ladder keeps the track card's own
            // pre-navigation restore stood down. Two gates keep the asynchronous open honest — nothing
            // is read as a close until a card has actually landed, and the window a cross-type step
            // holds open (the predecessor still on screen, the successor in flight) is not a close
            // either. Both gates read the one hand-off, because the only thing a landing can be is its
            // successor's own provenance: the marker card showing the id it opens, the track drawer open
            // on the new id. Left as "some card is open", the rule fires at the step itself, spends the
            // in-flight mark, disarms early and then reads the successor's arrival as a close.
            LaunchedEffect(
                drawerState, selectedMarkerId, trackDrawerState, inspectCardOpen, inspectHandoff, inspectArmed
            ) {
                if (!inspectCardOpen) return@LaunchedEffect
                val handoff = inspectHandoff
                if (handoff != null) {
                    val landed = inspectLanded(
                        target = handoff.target,
                        viewingMarkerId = selectedMarkerId.takeIf { drawerState is MarkerDrawerState.Viewing },
                        trackOpen = trackDrawerState.isOpen,
                        trackId = trackDrawerState.track?.id
                    )
                    if (!landed) return@LaunchedEffect
                    // The successor is on screen, so the slot can no longer empty: the predecessor held
                    // for it closes now, in that same frame, and the slot's visibility OR never goes
                    // false — the step reads as an in-place content change rather than a close followed
                    // by a reopen (plan §5).
                    inspectHandoff = null
                    // The step landed, so the slot it stepped away from is no longer needed.
                    inspectCursorBeforeStep = null
                    when (handoff.held) {
                        InspectKind.MARKER -> closeMarkerDashboard()
                        InspectKind.TRACK -> closeTrackDrawer()
                        null -> {}
                    }
                    // The open this mode made has landed, so the armed half stands down here — but the
                    // card is still on screen, so it keeps the hold on the centre and the capture that
                    // lands when it closes (plan §6).
                    if (inspectArmed) {
                        inspectArmed = false
                        viewModel.disarmInspect(mapMovedByUser = inspectMapMovedByUser)
                    }
                    return@LaunchedEffect
                }
                if (drawerState is MarkerDrawerState.Viewing || trackDrawerState.isOpen) {
                    // A card is on screen with nothing in flight: the settled state, until it closes.
                    return@LaunchedEffect
                }
                // The card is gone. This is the mode's single exit (plan §6): the capture retained
                // since arming is applied here, once — the demo half riding on the ViewModel's own
                // verdict, so both halves land together or not at all.
                val applied = viewModel.setInspectCardOpen(
                    open = false,
                    mapMovedByUser = inspectMapMovedByUser
                )
                inspectCardOpen = false
                inspectCandidate = null
                inspectCursor = null
                inspectCursorBeforeStep = null
                if (applied) applyInspectDemoExit()
            }

            // ── The marker card's Prev/Next while it is inspect-opened ──
            // The cursor above the drawers owns the merged walk, so the card takes these callbacks
            // rather than reading the ViewModel's own marker walk. Null the moment the card is not
            // inspect-opened, which is what keeps a list- or map-opened card on its own world.
            val inspectWalk: InspectWalk? =
                if (markersViewModel.drawerSource == DrawerSource.INSPECT) {
                    inspectCursor?.let { cursor ->
                        // A step surface is inert while an open is in flight: the held card's two arms
                        // both read as at their end and grey out, because a step taken now would cancel
                        // the open that is about to land on this very slot (plan §5).
                        val held = inspectHandoff != null
                        InspectWalk(
                            atFirst = !cursor.canPrev || held,
                            atLast = !cursor.canNext || held,
                            // The card's own controls stand down for that same window: Delete and Edit
                            // would interleave with the open about to land on this very slot (plan §5).
                            held = held,
                            onPrev = { cursor.step(-1)?.let { applyInspectStep(it) } },
                            onNext = { cursor.step(1)?.let { applyInspectStep(it) } }
                        )
                    }
                } else null

            // ── Inspect mode: warm, sweep, trigger, candidate overlay ──
            // "A panel owns the screen": the menu, settings, the two lists and the layer fan. While
            // one of them is open the trigger clock is suspended and its close starts a fresh wait
            // (§5); the fan is in the set because its own scrim owns the map for as long as it shows.
            val inspectPanelOpen = showSettings || showTrackDrawer || showTrackHistory ||
                showMarkerManagement || anyFanExpanded
            MapInspectEffects(
                mapView = mapView,
                armed = inspectArmed,
                centerOffsetPx = inspectOffsetPx,
                markers = inspectMarkerCandidates,
                trackIds = inspectTrackIds,
                trackViewModel = trackViewModel,
                liftId = mapLiftId,
                activityId = mapActivityId,
                panelOpen = inspectPanelOpen,
                highlightedTrackId = highlightedTrackId,
                rebuildGeneration = trackRebuildGeneration.value,
                // The same plan inputs the canonical rebuild paints the selection from, so the gold the
                // sweep shows is gold only where a selection would be gold (plan §4).
                trackArrows = appSettings.trackArrows,
                trackColours = appSettings.trackColours,
                eyeOverride = appSettings.trackSelectionBanded,
                onSweep = { rank -> inspectCandidate = rank },
                onPick = { picked, ladder ->
                    // The pick opens the card through the canonical opener — the one the mode spends its
                    // capture in — and the pass lands almost at once, being a sort over a few dozen warm
                    // entries.
                    inspectCandidate = null
                    // The ladder's own resolver travels with the cursor: every slot this pass opens, and
                    // every step after it, is looked up in the collection the ranking was built from, so
                    // the walk and the lookup cannot disagree (plan §5).
                    inspectCursor = InspectCursor.at(ladder, picked.id, inspectMarkerLookup)
                    openInspectCard(picked.id, picked.kind, inspectCursor, picked = true)
                }
            )

            // ── Process death: the two halves of the arming are put back in step ──
            // The armed flag is `rememberSaveable`, so a restored screen comes back armed, while the
            // ViewModel is a fresh instance whose own armed flag is false and whose feed was never
            // frozen: its disarm and its release would both no-op, so toggling the mode off would hand
            // nothing back. Re-arming it once on restore is the cheap correct half of that asymmetry —
            // it captures the follow state as it now stands and freezes, exactly as the toggle does, so
            // the exit is the ordinary one. The demo centre the arm captures is session-lived state that
            // process death cannot carry, so the restore has it as nothing — and nothing is precisely
            // what that disarm path would have to hand back (plan §6).
            LaunchedEffect(Unit) {
                if (inspectArmed && !viewModel.inspectModeArmed) viewModel.armInspect()
            }

            // The demo sailing's suspension follows the route mode's own switch: while it is on, the
            // pan-derived speed is derived no more and the dashboard's readout reads stationary. It is
            // keyed on the **phase**, not the toggle (R20): the suspension belongs to the destination
            // being placed, so following a route sails the map exactly as it does with no route at all.
            LaunchedEffect(routePhase) { viewModel.setRouteAiming(routePhase) }

            // ── F2c: Freeze auto-follow when entering marker creation/editing wizard ──
            // The disarm comes first, because the wizard's freeze is the one that must survive: a
            // disarm from a following map clears the suppression to recentre, and that clear would
            // cancel the wizard's own freeze and let the boat retake the centre mid-wizard. Taken in
            // this order the wizard's freeze is the last write and stands for the whole wizard.
            LaunchedEffect(drawerState) {
                if (drawerState is MarkerDrawerState.Creating || drawerState is MarkerDrawerState.Editing) {
                    // The wizard owns the map centre and puts its crosshair there, so entering it
                    // disarms the mode (plan §1, Panels).
                    disarmInspectMode()
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
                tagRegulatedZones = tagRegulatedZones,
                bandTagsDrawn = bandTagsDrawn,
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
                gpsStale = gpsStale,
                onGpsModeToggle = { onGpsModeChange(!appSettings.gpsMode) },
                // The tag stack's marker point is the map centre (§5.6 of the tag-stack plan),
                // and the band sign it shows is the marker's own band result.
                markerInZone300 = markerInZone300,
                headingDeg = effectiveHeadingDeg,
                onCenterChanged = onCenterChanged,
                onZoomChanged = viewModel::updateZoomLevel,
                onMapViewReady = { mapView = it },
                markerLayerState = markerLayerState,
                onToggleMarkerLayer = { markersViewModel.toggleMarkerLayer() },
                onAddZone = { center ->
                    // R1: the wizard takes the dashboard slot. The other dashboard is the close that
                    // matters; the same-kind half is what clears a selection the wizard's state cannot.
                    closeSelectedItemDashboards()
                    markersViewModel.startWizard(initialPos = center)
                },
                onWhereAmI = {
                    // R1: the Where-Am-I dashboard is the other selected-item dashboard, so a live
                    // track dashboard closes first (one selected item at a time).
                    closeTrackDrawer()
                    val boatPos = gpsPosition ?: mapCenter
                    // C3: one resolution per tap, run once and handed to both. The recording gets its
                    // MANUAL snapshot from the run's completion, so the note survives a close — the
                    // run may be superseded for the dashboard, but it never loses what it owes (§10).
                    markersViewModel.whereAmI(boatPos) { result ->
                        val snapshots = result.allMatches.map { it.toMarkerSnapshot() }
                        if (snapshots.isNotEmpty()) {
                            trackViewModel.addManualBoatMarker(snapshots)
                        }
                    }
                },
                onRetry = { viewModel.loadCoastline() },
                // R1 keep: the menu is a panel over the map, not an occupant of the dashboard slot, so the
                // selection survives it and returns when the menu closes (OverlayLayer stands the detail
                // slots down while a panel is open).
                onOpenTrackDrawer = { showTrackDrawer = !showTrackDrawer },
                showTrackDrawer = showTrackDrawer,
                showTrackHistory = showTrackHistory,
                trackRecorderState = trackRecorderState,
                trackSummaries = trackSummaries,
                recoveryTrack = recoveryTrack,
                onStartRecording = {
                    val startRecordingWithBatteryCheck: () -> Unit = {
                        if (BatteryExemption.shouldPrompt(context, appSettings)) {
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
                inspectArmed = inspectArmed,
                inspectEnabled = inspectAvailable,
                onToggleInspect = { if (inspectArmed) disarmInspectMode() else armInspectMode() },
                routeArmed = routeArmed,
                routeFollowing = routeState is RouteState.Following,
                onToggleRoute = { if (routeArmed) toggleRouteOff() else armRouteMode() },
                routeHost = {
                    Box(modifier = Modifier.fillMaxSize()) {
                        RouteHost(
                            mapView = mapView,
                            boatPosition = routeStart,
                            leadFix = routeLeadFix,
                            state = routeState,
                            armed = routeArmed,
                            gpsMode = appSettings.gpsMode,
                            speedKn = navigationState.speedKnots,
                            // A reading taken inside a zone or the band measures the limit, not the
                            // boat, and RoutePace drops it for that reason.
                            positionRestricted = inZone300 || zoneSituation?.currentZone != null,
                            setPaceKn = appSettings.routeFreeWaterPaceKn,
                            // The aim's own offset — **the same value** `inspectAnchor` reads the aim
                            // with, so the ring the host paints and the point the press asks from
                            // cannot drift apart. One conversion, one home.
                            mapCenterOffsetPx = inspectOffsetPx,
                            viewModel = routeViewModel,
                            onEndRoute = { leaveRouteMode() }
                            // The host composes nothing of its own and raises no panel: the aim ring is
                            // an osmdroid overlay it owns, drawn in the track band under the markers,
                            // so the boat paints over it; the route's confirmation is composed in the
                            // dashboard slot below, from the same state the line and pin are drawn from.
                        )
                        // **Where the refusal is shown** (§17 item 3): the mode's own slot, at the map's
                        // foot beside the import's feedback — the place a transient line already lives,
                        // so a refusal costs no screen and no panel. The line is the id the engine's
                        // closed set carries, resolved by the surface that reads it.
                        routeRefusalResId?.let { resId ->
                            MapStatusBanner(
                                message = stringResource(resId),
                                // The band's one answer to "is the tag column there", so this line
                                // clears it the way every other banner in the band does.
                                tagsDrawn = bandTagsDrawn,
                                modifier = Modifier.align(Alignment.BottomStart)
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        if (isLandscape) PaddingValues(start = landscapeDashboardWidth, top = 0.dp, end = 0.dp, bottom = 0.dp)
                        else PaddingValues(start = 0.dp, top = 0.dp, end = 0.dp, bottom = portraitDashboardHeight)
                    ),
                mapCenterOffsetDp = mapCenterOffsetDp
        )

            // ── Dashboard (always rendered, Layer 0) ────────────────────────
            // While the mode is armed the slot belongs to the route, in **either** phase: the panel is
            // where the outcomes are taken from, so it needs no floating surface to be reached — and it
            // tracks the aim through the very state the lines and the pin are drawn from.
            val routeOwnsSlot = routeArmed && routeState.phase != RoutePhase.IDLE
            val routeSearching = (routeState as? RouteState.Choosing)?.searching == true
            // The drawer's summary speaks only when the mode has something to say — while a search
            // runs or a plan stands (a followed route being the latter); with neither, no card stands.
            val routeSummaryVisible = routeOwnsSlot && (routeSearching || routeState.plan != null)
            val routeTrip = (routeState as? RouteState.Following)?.let { following ->
                routeTripFigure(
                    plan = following.plan,
                    from = RoutePoint(routeStart.latitude, routeStart.longitude),
                    paceKn = routePaceKn,
                    nowMs = System.currentTimeMillis()
                )
            }
            if (isLandscape) {
                if (routeOwnsSlot) {
                    RouteConfirmationPanel(
                        state = routeState,
                        stage = routeStage,
                        pinned = routePinned,
                        frontSaved = routeFrontSaved,
                        onPinnedChange = { routePinned = it },
                        onAcquire = { routeAcquireAim() },
                        onConfirm = { followRoute() },
                        onSaveTrack = { routeState.plan?.let { saveRouteTrack(it, routePinned) } },
                        onReroute = { rerouteRoute() },
                        onNewRoute = { newRoute() },
                        onExit = { leaveRouteMode() },
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
                        routeTrip = routeTrip,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .width(landscapeDashboardWidth)
                            .fillMaxHeight()
                            .windowInsetsPadding(WindowInsets.statusBars)
                    )
                }
            } else {
                if (routeOwnsSlot) {
                    RouteConfirmationPanel(
                        state = routeState,
                        stage = routeStage,
                        pinned = routePinned,
                        frontSaved = routeFrontSaved,
                        onPinnedChange = { routePinned = it },
                        onAcquire = { routeAcquireAim() },
                        onConfirm = { followRoute() },
                        onSaveTrack = { routeState.plan?.let { saveRouteTrack(it, routePinned) } },
                        onReroute = { rerouteRoute() },
                        onNewRoute = { newRoute() },
                        onExit = { leaveRouteMode() },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(portraitDashboardHeight)
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
                        routeTrip = routeTrip,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(portraitDashboardHeight)
                    )
                }
            }

            // ── Speed legend (Compose chrome, the map's top-left) ──
            // Drawn while the map carries a banded stroke: Colours paints every recorded stored track
            // from the ramp, a route bands on its own colour gate alone (R37), and the eye bands the
            // selection in the other modes. So the gate reads the ids the track effect actually painted
            // — unselecting leaves the scale up in Colours, and a painted set holding no banded stroke
            // takes it down — asking the same planner the map renders by for each of them, and telling
            // it which of them are routes so a route is not read as a recorded track. The selection
            // policy is never rerun here: the effect owns it, and recomputing it inside composition
            // would repeat a stateful mutation. Anchored below the
            // top-left toggle-button row on that row's own 6 dp gutter — itself offset by the landscape
            // dashboard when there is one — and drawn as Compose chrome rather than an osmdroid
            // overlay, so no polyline can ever paint over it.
            // The gate's input, derived rather than read in this scope: the painted set moves without the
            // boolean moving, so only a change of the boolean re-reads this body. Keyed on the settings
            // object, whose fields are plain values no recomposition alone can invalidate.
            val legendVisible by remember(appSettings) {
                derivedStateOf {
                    legendVisibleForState(
                        paintedIds = paintedTrackIds.value,
                        trackArrows = appSettings.trackArrows,
                        trackColours = appSettings.trackColours,
                        highlightedTrackId = highlightedTrackId,
                        eyeOverride = appSettings.trackSelectionBanded,
                        tracksVisible = appSettings.tracksVisible,
                        // The painted routes, so the planner reads each of them as the role it is; read
                        // inside the derived block, where the summaries state is a tracked input.
                        routeIds = allTrackSummaries.filter { it.route }.map { it.id }.toSet(),
                        routeSpeedColour = appSettings.routeSpeedColor,
                        routeSpeedArrows = appSettings.routeSpeedArrows
                    )
                }
            }
            if (legendVisible) {
                // D1–D3, D6: one control, two faces, one anchor. The gate above keeps deciding whether the
                // control exists at all — it is untouched by this toggle — while the persisted flag decides
                // only which face it wears, so the card is on screen while the scale is expanded and the
                // row's own square while it is collapsed: never both at once, and the square carries no
                // active styling because "active" is this very card. The two arms share one anchor value
                // and differ only in the width the card asserts over the square's own size.
                val legendAnchor = Modifier
                    .align(Alignment.TopStart)
                    .padding(
                        start = (if (isLandscape) landscapeDashboardWidth else 0.dp) + TOP_TOGGLE_GUTTER,
                        top = legendTopOffset(chromeTopInset(isLandscape))
                    )
                if (appSettings.trackLegendExpanded) {
                    // The strip is one toggle button wide and its left edge is the row's own gutter, so its
                    // two vertical edges are the leftmost button's two edges, with the recenter button never
                    // entering the arithmetic. Its 6 dp *start* padding insets the bar from that edge.
                    TrackSpeedLegend(
                        ramp = AppConfig.trackHeatmapRamp,
                        ticks = AppConfig.trackHeatmapScaleTicks,
                        minKn = AppConfig.trackHeatmapScaleMinKn,
                        onToggle = { viewModel.updateSettings { it.copy(trackLegendExpanded = false) } },
                        modifier = legendAnchor.width(TOP_TOGGLE_SQUARE)
                    )
                } else {
                    // The shared anchor, its own size being the card's width: the square lands on the GPS
                    // square's left edge with the row's own 6 dp gap below it. It is the shared square read
                    // directly — no `LegendToggleButton` of its own — with the surface's inactive face:
                    // "active" is the card itself being on screen, so the two faces never appear together,
                    // and the square types neither a fill nor a corner. The glyph is the stopwatch a
                    // tachymeter is engraved on, `\u23F1\uFE0F`: U+23F1 is Emoji_Presentation=No, so the
                    // selector is what asks for the colour emoji the row's four glyphs wear.
                    MapToggleSquare(
                        face = mapSurfaceFaceInactive(),
                        onClick = { viewModel.updateSettings { it.copy(trackLegendExpanded = true) } },
                        contentDescription = stringResource(R.string.cd_expand_speed_scale),
                        modifier = legendAnchor
                    ) {
                        Text(
                            text = "\u23F1\uFE0F",
                            fontSize = TOP_TOGGLE_ICON_SIZE
                        )
                    }
                }
            }

            // ── Marker overlays (OSMdroid native, via LaunchedEffect) ─────
            if (markerLayerVisible) {
                val matchResult by markersViewModel.matchResult.collectAsState()
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
                            if (inspectArmed) {
                                // While armed a tap opens through the canonical inspect opener. A ladder
                                // already frozen is the mode's own walk and wins; before the first pick
                                // there is none to seat, and the tap's world is then the map-filtered
                                // collection the tap itself came from — the very set the sweep ranks —
                                // and never the list world, which may have filtered this marker out and
                                // would seat the card on whichever marker came first (plan §5).
                                val seated = inspectCursor?.ladder
                                    ?.let { InspectCursor.at(it, sel, inspectMarkerLookup) }
                                if (seated != null) inspectCursor = seated
                                val tapWorld = if (seated != null) null else {
                                    val mapIds = mapMarkersState.map { it.id }
                                    if (mapIds.contains(sel)) mapIds else listOf(sel) + mapIds
                                }
                                openInspectCard(
                                    sel, InspectKind.MARKER, seated, picked = false, tapWorld = tapWorld
                                )
                            } else {
                                // R1: one selected item at a time — a map tap closes the track detail drawer.
                                closeTrackDrawer()
                                val worldIds = mapMarkersState.map { it.id }
                                val navIds = if (worldIds.contains(sel)) worldIds else listOf(sel) + worldIds
                                markersViewModel.openEditDrawer(navIds, selectedId = sel, source = DrawerSource.MAP)
                            }
                        }
                    },
                    matchResult = if (drawerState is MarkerDrawerState.MatchResult) matchResult else null,
                    // The selected marker forces its own zones visible inside MarkerOverlay
                    // (folded navigationZonesVisible); this flag stays the global toggle.
                    markerZonesVisible = appSettings.markerZonesVisible,
                    // An inspect candidate wears the same gold the selection does: a marker candidate
                    // simply joins this overlay's existing input rather than being painted separately.
                    selectedMarkerId = inspectCandidate?.takeIf { it.kind == InspectKind.MARKER }?.id
                        ?: selectedMarkerId,
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

        // ── Marker focus: one framing rule for every select that shows the card ─────────
        // The click-N-move flow frames its marker inside the flow itself; every other select — a map
        // tap, a dashboard Prev/Next step — is framed here. `lastFramedMarkerId` is what stops the
        // two from flying the same camera twice: the navigate flow records the id it framed and this
        // effect steps aside for it. The selection going null (the card closed) clears the record, so
        // re-opening the same marker frames it again.
        val focusedMarkerId by markersViewModel.selectedMarkerId.collectAsState()
        var lastFramedMarkerId by remember { mutableStateOf<String?>(null) }

        fun frameMarker(mv: MapView, marker: UserMarker) {
            val focus = markerFocusTarget(
                marker = marker,
                viewportWidthPx = mv.width,
                viewportHeightPx = mv.height,
                currentZoom = mv.zoomLevelDouble,
                corridorShare = AppConfig.markerFocusCorridorShare,
                zoneShare = AppConfig.markerFocusZoneShare,
                pinFootprintM = AppConfig.markerFocusPinFootprintM
            )
            if (focus == null) {
                mv.controller.animateTo(
                    GeoPoint(marker.centerPoint.latitude, marker.centerPoint.longitude),
                    null,
                    GPS_ANIMATION_DURATION_MS
                )
            } else {
                mv.controller.animateTo(
                    GeoPoint(focus.centre.latitude, focus.centre.longitude),
                    focus.zoom,
                    GPS_ANIMATION_DURATION_MS
                )
            }
        }

        LaunchedEffect(focusedMarkerId, drawerState) {
            if (focusedMarkerId == null) {
                lastFramedMarkerId = null
                return@LaunchedEffect
            }
            if (drawerState !is MarkerDrawerState.Viewing) return@LaunchedEffect
            // While an inspect open is landing, the camera belongs to that open's own hand-off.
            if (inspectHandoff != null) return@LaunchedEffect
            if (focusedMarkerId == lastFramedMarkerId) return@LaunchedEffect
            val mv = mapView ?: return@LaunchedEffect
            val marker = userMarkers.find { it.id == focusedMarkerId } ?: return@LaunchedEffect
            lastFramedMarkerId = focusedMarkerId
            frameMarker(mv, marker)
        }

        // ── Click-N-Move: sequential navigate flow ──────────────────────────
        LaunchedEffect(navigateToTarget) {
            val target = navigateToTarget ?: return@LaunchedEffect
            val mv = mapView
            if (mv != null) {
                // 1. Focus the marker through the one framing rule, recording the id so the select
                //    effect above does not fly the same camera a second time.
                val focusMarker = userMarkers.find { it.id == target.markerId }
                if (focusMarker == null) {
                    mv.controller.animateTo(target.geoPoint, null, GPS_ANIMATION_DURATION_MS)
                } else {
                    lastFramedMarkerId = target.markerId
                    frameMarker(mv, focusMarker)
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

                // 5. Open the drawer on its own walk world: the full filtered list for a list selection,
                // the frozen inspect ladder when the mode handed one over, the map-filtered collection an
                // armed tap came from (plan §5). The world decides what Prev/Next steps through, and the
                // source says which world that is.
                val worldIds = target.worldIds ?: markersViewModel.markers.value.map { it.id }
                markersViewModel.openEditDrawer(worldIds, selectedId = target.markerId, source = target.source)
            }
            // The tail runs on every exit of this effect, the null-mapView one included, where the
            // camera never flew and no drawer was ever asked for: the hand-off clears either way, and
            // the navigate target is spent so the effect cannot re-run on a stale one. The drawer opens
            // synchronously, so "still not Viewing" means this open seated nothing — a marker deleted
            // while the camera was flying, an empty world, or no map to fly it (plan §5). The guard is
            // the in-flight open itself and not the world it walks, because an armed tap's open walks
            // the map world and can land nothing just as easily.
            val handoff = inspectHandoff
            if (handoff != null && handoff.target.id == target.markerId &&
                markersViewModel.drawerState.value !is MarkerDrawerState.Viewing
            ) {
                abandonInspectOpen()
            }

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
            // The inspect card's own guard (plan §6), the same canonical rule: once the open's camera
            // has settled — the open landed, no navigate target in flight and no zoom-to-fit pending —
            // a centre change is the user's own move, and the close leaves the frame alone.
            if (inspectCardOpen && inspectHandoff == null &&
                trackNavigateState == null && navigateToTarget == null
            ) {
                inspectMapMovedByUser = true
            }
        }

        // ── Track drawer: BackHandler close ──────────────────────────────
        if (trackDrawerState.isOpen) {
            // Routed through the one close helper — same camera-restore semantics. While an inspect
            // open is in flight this card is the predecessor held for it, so Back is swallowed rather
            // than allowed to close the card out from under the landing successor (plan §5).
            BackHandler { if (inspectHandoff == null) closeTrackDrawer() }
        }

        // ── Layer 1: Overlay (transient drawers, Wizard, Settings, scrim) ──
        val showWizard = drawerState is MarkerDrawerState.Creating || drawerState is MarkerDrawerState.Editing

        // ── Menu chevron shortcuts: first item of the current filtered/sorted list ──
        val firstTrackId = trackSummaries.firstOrNull { !it.isLive && "t:${it.id}" !in pendingDeleteIds }?.id
        val firstMarkerId = mgmtMarkers.firstOrNull()?.id

        // Menu (map-referential) track counter: stored non-live tracks matching the map filter —
        // pinned included (they always render). Render-cap divergence is acceptable.
        val trackMapVisibleCount = allTrackSummaries.count {
            !it.isLive && it.matchesFilter(appSettings.trackMapFilter, ykws.android.maro.data.model.todayMidnightMs())
        }

        // The track drawer's walk world: the frozen inspect ladder when the card was opened by a pick,
        // the list world everywhere else — which is what keeps a list-opened track on the list even
        // while the mode is armed, and what the drawer's own pill ends are read from.
        val trackListIds = trackDrawerState.inspectLadder
            ?: trackSummaries.filter { !it.isLive && "t:${it.id}" !in pendingDeleteIds }.map { it.id }

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
                // the two dim layers never stack. The route's confirmation is not one of these: it
                // lives in the dashboard slot, so it paints no scrim and blocks nothing.
                dialogScrimActive = anyConfirmDialogOpen,
                // The drawer's route summary stands while a search runs or a plan stands, and not otherwise.
                routeSummaryVisible = routeSummaryVisible,
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
                // While an inspect open is in flight this card is the predecessor held for it: its
                // close — Back or the header cross — would empty the slot the successor is about to
                // fill, so the surface is inert for that window and closes normally once the swap has
                // landed (plan §5).
                if (inspectHandoff == null) markersViewModel.closeDrawer()
            },
            // The marker card's merged walk while it is inspect-opened; null leaves it on its own world.
            markerInspectWalk = inspectWalk,
            // R1: the drawer Edit path needs only the track half closed — the marker half is replaced
            // by the wizard's own MarkerDrawerState, so the marker being edited is never closed.
            onMarkerWizardEntry = { closeTrackDrawer() },
            onOpenTrackHistoryFromMenu = { showTrackHistory = true },
            onOpenMarkerManagementFromMenu = { showMarkerManagement = true },
            onOpenSettingsFromMenu = { showSettings = true },
            onOpenFirstTrack = { id -> openSelectedTrack(listOf(id)) },
            onOpenFirstMarker = { id -> openMarkerDetail(id) },
            markersViewModel = markersViewModel,
            trackViewModel = trackViewModel,
            menu = MenuOverlayData(
                gpsMode = appSettings.gpsMode,
                autoShowMasterVisible = if (appSettings.gpsMode) appSettings.approachAutoShowGps else appSettings.approachAutoShowDemo,
                autoShowMasterOverride = appSettings.autoShowMasterOverride,
                gpsToggleColor = gpsToggleColor,
                markerZonesVisible = appSettings.markerZonesVisible,
                trackArrows = appSettings.trackArrows,
                trackColours = appSettings.trackColours,
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
            onTrackArrowsChange = { arrows ->
                // D3: one writer for the pair; the map reads the axes and the eye's own override never
                // touches either of them. Each chip folds into its own `copy`, so a tap never rewrites
                // the axis the user did not touch.
                viewModel.updateSettings { it.copy(trackArrows = arrows) }
                mapView?.invalidate()
            },
            onTrackColoursChange = { colours ->
                viewModel.updateSettings { it.copy(trackColours = colours) }
                mapView?.invalidate()
            },
            onTrackAction = { action ->
                when (action) {
                    is ykws.android.maro.data.model.ListAction.NavigateToItem -> openSelectedTrack(listOf(action.id))
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
                // R2: the sort rewrites the list world the open track walk reads.
                closeDashboardsForScopeChange(trackListWorld = true)
                viewModel.updateSettings { it.copy(trackListSort = newState) }
                trackViewModel.refreshSummaries(newState, reloadFromDisk = false)
                mapView?.invalidate()
            },
            onTrackFilterChange = { newFilter ->
                // R2: the list filter rewrites the list world the open track walk reads.
                closeDashboardsForScopeChange(trackListWorld = true)
                viewModel.updateSettings { s ->
                    if (s.trackFilterLinked) s.copy(trackListFilter = newFilter, trackMapFilter = newFilter)
                    else s.copy(trackListFilter = newFilter)
                }
                trackViewModel.refreshSummaries(filter = newFilter, reloadFromDisk = false)
            },
            onTrackReset = {
                // R2: the reset rewrites the list world the open track walk reads.
                closeDashboardsForScopeChange(trackListWorld = true)
                val resetFilter = ykws.android.maro.data.model.ListFilter()
                viewModel.updateSettings { s ->
                    if (s.trackFilterLinked) s.copy(trackListSort = ykws.android.maro.data.model.ListSortState(), trackListFilter = resetFilter, trackMapFilter = resetFilter)
                    else s.copy(trackListSort = ykws.android.maro.data.model.ListSortState(), trackListFilter = resetFilter)
                }
                trackViewModel.refreshSummaries(filter = resetFilter, reloadFromDisk = false)
                // List reset clears the session boost only when the map filter is linked (it moved too).
                if (appSettings.trackFilterLinked) trackViewModel.clearRenderBoost()
                mapView?.invalidate()
            },
            // ── Track map referential (menu filter) + link ────────────────
            onTrackMapFilterChange = { newFilter ->
                val linked = appSettings.trackFilterLinked
                // R2: while the link is on, the map write moves the list world the track walk reads with
                // it; unlinked it is display-only and the open track dashboard stays.
                closeDashboardsForScopeChange(trackListWorld = linked)
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
                // R2: a linked map reset moves the list world the track walk reads with it.
                closeDashboardsForScopeChange(trackListWorld = linked)
                viewModel.updateSettings { s ->
                    if (s.trackFilterLinked) s.copy(trackListFilter = resetFilter, trackMapFilter = resetFilter)
                    else s.copy(trackMapFilter = resetFilter)
                }
                if (linked) trackViewModel.refreshSummaries(filter = resetFilter, reloadFromDisk = false)
                // The map reset always invalidates the session boost.
                trackViewModel.clearRenderBoost()
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
            routeSummary = RouteSummaryData(
                searching = routeSearching,
                stageRes = routeStage?.labelResId,
                plannedDistanceNm = routeState.plan?.distanceNm,
                plannedEtaSeconds = routeState.plan?.let { it.remainingFrom(it.start).durationSec },
                remaining = routeTrip,
            ),
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
                openSelectedTrack(listOf(trackId))
            },
            onMarkerAction = { action ->
                when (action) {
                    is ykws.android.maro.data.model.ListAction.NavigateToItem -> openMarkerDetail(action.id)
                    is ykws.android.maro.data.model.ListAction.EditItem -> {
                        // R1: the wizard takes the dashboard slot — a dashboard left open behind the list
                        // stands down, so no stale selection survives into the wizard.
                        closeSelectedItemDashboards()
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
                trackListIds = trackListIds,
                currentTrackIndex = trackListIds.indexOf(trackDrawerState.track?.id ?: "").coerceAtLeast(0),
                // The provenance flag the drawer's close path and this bundle both read.
                inspectLadder = trackDrawerState.inspectLadder,
                // An in-flight inspect open holds this card as its predecessor: both walk buttons grey
                // out for that window rather than letting a second step cancel the pending landing (§5).
                walkHeld = inspectHandoff != null,
                trackColours = appSettings.trackColours,
                eyeOverride = appSettings.trackSelectionBanded,
                onToggleEyeOverride = {
                    // D10: the eye moves the selected track's fill alone, never the colours flag every
                    // other track renders by. With Colours off it turns the ramp on for this one track,
                    // with Colours on it turns this track off it, and from the first tap the value is
                    // the user's own: the flag stops reaching it. The tap's algebra lives in
                    // `selectionBandedAfterTap`, where it is unit-tested.
                    viewModel.updateSettings {
                        it.copy(
                            trackSelectionBanded = selectionBandedAfterTap(
                                appSettings.trackSelectionBanded,
                                appSettings.trackColours
                            )
                        )
                    }
                    mapView?.invalidate()
                },
            ),
            onTrackDrawerClose = { closeTrackDrawer() },
            onNavigateToTrack = { id -> openSelectedTrack(listOf(id)) },
            onResumeRequest = { id, fromList -> pendingResume = PendingTrackResume(id, fromList) },
            onMarkerSortStateChange = { newState ->
                // R2: the sort rewrites the list world a list-opened marker walk reads; a map-opened one
                // reads the map world and stays open.
                closeDashboardsForScopeChange(markerListWorld = true)
                viewModel.updateSettings { it.copy(markerListSort = newState) }
                markersViewModel.refreshSort(newState)
            },
            onMarkerFilterChange = { newFilter ->
                android.util.Log.d("MaroMapRefresh", "onMarkerFilterChange: $newFilter")
                // R2: the list filter rewrites the list world a list-opened marker walk reads.
                closeDashboardsForScopeChange(markerListWorld = true)
                viewModel.updateSettings { s ->
                    if (s.markerFilterLinked) s.copy(markerListFilter = newFilter, markerMapFilter = newFilter)
                    else s.copy(markerListFilter = newFilter)
                }
                markersViewModel.refreshSort(filter = newFilter)
            },
            onMarkerReset = {
                android.util.Log.d("MaroMapRefresh", "onMarkerReset")
                // R2: the reset rewrites the list world a list-opened marker walk reads.
                closeDashboardsForScopeChange(markerListWorld = true)
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
                // R2: a map-opened marker walk reads the map world, so this write closes it. The view
                // model then re-tests the linked list world too — a map write need not pass through
                // `refreshSort`, which is why the control reports here as well.
                closeDashboardsForScopeChange(markerMapWorld = true)
                viewModel.updateSettings { s ->
                    if (s.markerFilterLinked) s.copy(markerListFilter = newFilter, markerMapFilter = newFilter)
                    else s.copy(markerMapFilter = newFilter)
                }
                markersViewModel.onMapReferentialChanged()
                if (linked) markersViewModel.refreshSort(filter = newFilter)
            },
            onMarkerMapReset = {
                // R2: a map-opened marker walk reads the map world, so this reset closes it.
                closeDashboardsForScopeChange(markerMapWorld = true)
                val resetFilter = ykws.android.maro.data.model.ListFilter()
                viewModel.updateSettings { s ->
                    if (s.markerFilterLinked) s.copy(markerListFilter = resetFilter, markerMapFilter = resetFilter)
                    else s.copy(markerMapFilter = resetFilter)
                }
                markersViewModel.onMapReferentialChanged()
                if (appSettings.markerFilterLinked) markersViewModel.refreshSort(filter = resetFilter)
            },
            markerFilterLinked = appSettings.markerFilterLinked,
            onToggleMarkerLink = {
                // Pure flip: no filter carry-over. Next linked edit writes both.
                viewModel.updateSettings { s -> s.copy(markerFilterLinked = !s.markerFilterLinked) }
            },
            onCreateFirst = {
                // R1: the wizard takes the dashboard slot — the other dashboard closes first.
                closeSelectedItemDashboards()
                showMarkerManagement = false
                markersViewModel.startWizard(initialPos = mapCenter)
            },
            onSetIcon = { id, icon -> markersViewModel.setMarkerIcon(id, icon) },
            onSetPin = { id, pinned -> markersViewModel.setMarkerPinned(id, pinned) },
            onUpdateMarkerText = { id, name, desc -> markersViewModel.updateMarkerText(id, name, desc) },
            // ── List-detail navigation ──────────────────────────────────
            onTrackPrev = {
                // Inspect: the merged ladder's cursor owns the walk, so the list walk stands down.
                if (trackDrawerState.inspectLadder != null) {
                    inspectCursor?.step(-1)?.let { applyInspectStep(it) }
                } else {
                    val ids = trackSummaries.filter { !it.isLive && "t:${it.id}" !in pendingDeleteIds }.map { it.id }
                    val idx = ids.indexOf(trackDrawerState.track?.id ?: "")
                    if (idx > 0) {
                        // The list walk hands the list world itself over, and a step keeps the frame the
                        // open captured: only a selection captures a fresh one.
                        openSelectedTrack(ids.subList(0, idx).asReversed(), freshSelection = false) { }
                    }
                }
            },
            onTrackNext = {
                // Inspect: as above — the cursor walks the merged ladder.
                if (trackDrawerState.inspectLadder != null) {
                    inspectCursor?.step(1)?.let { applyInspectStep(it) }
                } else {
                    val ids = trackSummaries.filter { !it.isLive && "t:${it.id}" !in pendingDeleteIds }.map { it.id }
                    val idx = ids.indexOf(trackDrawerState.track?.id ?: "")
                    if (idx >= 0 && idx < ids.lastIndex) {
                        openSelectedTrack(ids.subList(idx + 1, ids.size), freshSelection = false) { }
                    }
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
                openSelectedTrack(after + before, freshSelection = false) { closeTrackDrawer() }
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
                    // The deleted marker is the slot the merged walk was sitting on, so the advance
                    // re-seats the cursor on its neighbour — and drops it when the frozen ladder holds
                    // no such slot, falling back to the map world exactly as the undo path does,
                    // because an inspect-sourced card with no cursor carries a walk that can never
                    // move (plan §5).
                    val reSeated = inspectCursor?.ladder
                        ?.let { InspectCursor.at(it, targetId, inspectMarkerLookup) }
                    if (source == DrawerSource.INSPECT) {
                        inspectCursor = reSeated
                        markersViewModel.openEditDrawer(
                            filtered,
                            selectedId = targetId,
                            source = if (reSeated != null) DrawerSource.INSPECT else DrawerSource.MAP
                        )
                    } else {
                        markersViewModel.openEditDrawer(filtered, selectedId = targetId, source = source)
                    }
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
            closeBatteryOptDialog = { showBatteryOptDialog = false },
            onBatteryOptPrompted = {
                viewModel.updateSettings { it.copy(batteryOptimizationPrompted = true) }
            }
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

        // ── The route's one exit dialog (R23) — hosted here, asked by its three doors ─────────────
        // The toggle's off, the panel's own **Exit** and the back key all raise this same dialog, and
        // it reads in the order every action surface takes (ui-component-guidelines §5.6): the
        // affirmative first, the neutral stay, the loss last — **Save track and Exit** · **Continue** ·
        // **Discard route**. Its save writes the **front route** and is **disabled when nothing is
        // unwritten**; the all-scope option it used to carry is withdrawn, so one save path and one
        // name remain.
        if (routeExitRequested) {
            val front = routeState.plan
            val frontUnwritten = front != null && !routeViewModel.isRouteSaved(front)
            ConfirmDialog(
                title = stringResource(R.string.route_exit_title),
                visible = true,
                onDismiss = { routeExitRequested = false },
                message = null,
                options = null,
                actions = listOf(
                    // The accent is the dialog's own outcome: it writes the front route — the one the
                    // panel's own table describes, save what you see.
                    ConfirmAction(
                        label = stringResource(R.string.route_action_save_only),
                        role = ConfirmActionRole.PRIMARY,
                        enabled = frontUnwritten
                    ) {
                        routeExitRequested = false
                        if (front != null) saveRouteTrack(front, routePinned)
                        endRouteMode()
                    },
                    ConfirmAction(
                        label = stringResource(R.string.route_exit_continue),
                        role = ConfirmActionRole.SECONDARY
                    ) { routeExitRequested = false },
                    ConfirmAction(
                        label = stringResource(R.string.route_exit_discard),
                        role = ConfirmActionRole.DANGER
                    ) {
                        routeExitRequested = false
                        endRouteMode()
                    }
                )
            )
        }

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
                    OptionRow(
                        label = stringResource(R.string.resume_confirm_backup),
                        checked = backup,
                        onCheckedChange = { backup = it },
                        modifier = Modifier.fillMaxWidth()
                    )
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
        val lockTopInset = chromeTopInset(isLandscape)
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
                        .padding(
                            top = lockTopInset,
                            // The arithmetic's one home, over the slot the row actually drew: the
                            // earth/water square is the one the setting can take away.
                            start = topToggleSlotOffset(lockSlot(appSettings.showLandWaterIcon))
                        )
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
                    tagsDrawn = bandTagsDrawn,
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
    /** The bottom-left tag stack's own set — the raw zones filtered by the boat size and the
     *  per-category visibility. Hoisted to the caller, which needs the same tag answer for the
     *  locked-screen banner it draws outside this composable. */
    tagRegulatedZones: RegulatedZoneSet?,
    /** Whether that stack draws at least one tag — the band's one answer, derived by the caller beside
     *  [tagRegulatedZones] and read by every banner here instead of being re-derived per caller. */
    bandTagsDrawn: Boolean,
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
    /** True while the last fix is considered stale — the cap arrow's colour-mode tell. */
    gpsStale: Boolean = false,
    markerInZone300: Boolean = false,
    headingDeg: Double = -1.0,
    onCenterChanged: (Double, Double) -> Unit,
    onZoomChanged: (Double) -> Unit,
    onMapViewReady: (MapView) -> Unit,
    markerLayerState: MarkerLayerState = MarkerLayerState.SHOW_ALL,
    onToggleMarkerLayer: () -> Unit = {},
    onAddZone: (LatLng) -> Unit = {},
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
    /** True while inspect mode is armed: the sleuth square's active face. */
    inspectArmed: Boolean = false,
    /** False while the mode is disarmed and nothing is inspectable — the square carries no tap. */
    inspectEnabled: Boolean = true,
    onToggleInspect: () -> Unit = {},
    /** True while the destination mode is aiming or following: the compass square's active face. */
    routeArmed: Boolean = false,
    /** True while a route is **followed** — the toggle's second on-phase carries the pulsing dot (R19). */
    routeFollowing: Boolean = false,
    onToggleRoute: () -> Unit = {},
    /**
     * The route mode's own slot, composed by the shell so this file keeps **one** new parameter
     * rather than a dozen: the single `RouteHost(mapView, boatPosition)` call lives in the shell,
     * which is the seam the isolation design names, and everything the host needs is already in
     * scope there.
     */
    routeHost: (@Composable () -> Unit)? = null,
) {
    Box(modifier = modifier.clipToBounds()) {
        // ── Top inset: one home for the arithmetic, so the toggle row, the lock button and the
        // legend all start from the same place. ──
        val density = LocalDensity.current
        val topInset = chromeTopInset(isLandscape)
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
        // The band's tag answer arrives from the caller — one derivation for the stack, the clearance
        // and the locked-screen banner alike — so nothing here re-reads the tag list for `isNotEmpty()`.
        // Apply low-depth (<1.5 m) warning visibility toggle
        val visibleLowDepthWarning = if (appSettings.lowDepthWarningVisible) lowDepthWarningBitmap else null
        // Apply depth layer colour map + isobath contours visibility toggle
        val visibleDepthBitmap = if (appSettings.depthLayerVisible) depthBitmap else null
        val visibleIsobaths = if (appSettings.depthLayerVisible) isobaths else emptyList()

        // ── Layer 0: OSMdroid map (fills entire Box) ───────────────────────
        // The shoreline, band and outline widths are dp, like every stored width: this layer holds the
        // density, so it is the one that converts them into the px the renderer's …Px parameters take.
        val paintDensity = LocalDensity.current.density
        CoastlineMapView(
            segments = segments,
            coastlineMainlandColor = appSettings.coastlineMainlandColor,
            coastlineIslandColor = appSettings.coastlineIslandColor,
            coastlineWidthPx = dpToPx(appSettings.coastlineWidthDp, paintDensity),
            coastlineTransparencyPct = appSettings.coastlineTransparencyPct,
            regulatedZones = visibleRegulatedZones,
            regulatedZoneFillTransparencyPct = appSettings.regulatedZoneFillTransparencyPct,
            regulatedZoneBoundaryTransparencyPct = appSettings.regulatedZoneBoundaryTransparencyPct,
            regulatedZoneOutlineWidthPx = dpToPx(appSettings.regulatedZoneOutlineWidthDp, paintDensity),
            zone300 = visibleZone300,
            zone300Color = appSettings.zone300Color,
            zone300FillTransparencyPct = appSettings.zone300FillTransparencyPct,
            zone300BoundaryTransparencyPct = appSettings.zone300BoundaryTransparencyPct,
            zone300BoundaryWidthPx = dpToPx(appSettings.zone300BoundaryWidthDp, paintDensity),
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
                strokeWidthDp = appSettings.navigationLineWidthDp,
                color = appSettings.navigationLineColor,
                transparencyPct = appSettings.navigationLineTransparencyPct,
                modifier = Modifier.fillMaxSize(),
                centerOffsetYDp = mapCenterOffsetDp
            )
        }

        CapArrowOverlay(
            zoomLevel = zoomLevel,
            navigationState = navigationState,
            showCapArrow = appSettings.capArrowVisible,
            shaftWidthDp = appSettings.navigationArrowWidthDp,
            color = appSettings.navigationArrowColor,
            transparencyPct = appSettings.navigationArrowTransparencyPct,
            followSpeedColour = appSettings.navigationArrowFollowSpeedColour,
            gpsStale = gpsStale,
            modifier = Modifier.fillMaxSize(),
            centerOffsetYDp = mapCenterOffsetDp
        )

        CenterMarkerOverlay(
            isWater = isWater,
            zoomLevel = zoomLevel,
            distanceToShore = distanceToShore,
            showCrosshair = showCrosshair,
            // While armed the mode owns this point: running the query would race the trigger for the
            // same card slot, so the tap stands down and the map's own markers keep their route.
            // The return is the acceptance itself: the boat flashes its gold ring only for the tap
            // that reaches onWhereAmI, never for one that stood down here (R21).
            onClick = {
                if (inspectArmed) false
                else { onWhereAmI(); true }
            },
            modifier = Modifier.align(Alignment.Center),
            centerOffsetYDp = mapCenterOffsetDp
        )

        // ── The route mode's own chrome and map objects, in the shell's one call ──
        routeHost?.invoke()

        // ── Layer 1: 2-column overlay row (left fills, right content-sized) ──
        Row(modifier = Modifier.fillMaxSize()) {

            // ── LEFT COLUMN: top + middle + btm ──────────────────────────
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {

                // top zone: GPS, tracking, land/water, inspect, lock, + recenter while auto-follow is paused (statusBars minus 6dp)
                Row(
                    modifier = Modifier
                        .padding(top = topInset, start = TOP_TOGGLE_GUTTER),
                    horizontalArrangement = Arrangement.spacedBy(TOP_TOGGLE_GUTTER),
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
                    if (appSettings.showLandWaterIcon) {
                        EarthWaterIcon(
                            emoji = if (isWater) "🌊" else "🏔️",
                            color = if (isWater) ComposeColor(AppConfig.statusEarthWaterWater) else ComposeColor(AppConfig.statusEarthWaterLand),
                        )
                    }
                    InspectToggleButton(
                        armed = inspectArmed,
                        enabled = inspectEnabled,
                        onToggle = onToggleInspect
                    )
                    // **Never dead** (§17 item 3): a tap while the engine is not ready asks for one more
                    // preparation and the refusal is shown where the feature's own chrome lives, so this
                    // square carries its tap always — there is no `enabled` to take it away.
                    RouteToggleButton(
                        armed = routeArmed,
                        following = routeFollowing,
                        onToggle = onToggleRoute
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
                // In landscape, clear the nav bar; in portrait, match the row's own `ui.map.toggle.gutter`.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isLandscape) Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                            else Modifier.padding(bottom = TOP_TOGGLE_GUTTER)
                        )
                ) {
                    // Behind layer: regulated zone icons + info text
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = TOP_TOGGLE_GUTTER)
                    ) {
                        RegulatedZoneWarningStrip(
                            regulatedZones = tagRegulatedZones,
                            markerPosition = mapCenter,
                            inZone300 = markerInZone300,
                            modifier = Modifier.align(Alignment.Bottom)
                        )
                        if (appSettings.regulationInfoVisible) {
                            RegulatedZoneInfoText(
                                regulatedZones = tagRegulatedZones,
                                markerPosition = mapCenter,
                                inZone300 = markerInZone300,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = AppConfig.uiMapOverlayGap.dp)
                                    .align(Alignment.Bottom)
                            )
                        }
                    }

                    // Middle layer: loading/error overlay (conditional). The cards take the band's own
                    // clearance as MapBanner's start inset; only their matching end gap stays here.
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(end = 6.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (rasterProgress != null && rasterProgress!!.globalProgress < 100) {
                            val rp = rasterProgress!!
                            LoadingOverlay(
                                progress = GenerationProgress(rp.phase, rp.globalProgress),
                                tagsDrawn = bandTagsDrawn,
                                title = stringResource(R.string.map_generating_layers)
                            )
                        }
                        if (state is CoastlineState.Error) {
                            ErrorOverlay(
                                message = (state as CoastlineState.Error).message,
                                onRetry = onRetry,
                                tagsDrawn = bandTagsDrawn
                            )
                        }
                        // Track info error (from populate-track-info)
                        val trackInfoError = trackRecorderState.infoError
                        if (trackInfoError != null) {
                            ErrorOverlay(
                                message = trackInfoError,
                                onRetry = onClearTrackInfoError,
                                tagsDrawn = bandTagsDrawn
                            )
                        }
                    }

                    // Top layer: exit toast (conditional) — MapBanner's first caller. A child of the
                    // map's left overlay column, so it adds no end reserve of its own: that column
                    // already excludes the right control column, and the tag column's width is what the
                    // band's start inset clears (docs/ui-drawer-guidelines.md §1).
                    if (showExitBanner) {
                        val isRecording = trackRecorderState.state == ykws.android.maro.data.track.TrackRecorderState.ON
                        val borderColor = if (isRecording)
                            ComposeColor(AppConfig.uiDashboardZoneDanger)
                        else
                            ComposeColor(AppConfig.uiDashboardBackground)
                        MapBanner(
                            borderColor = borderColor,
                            tagsDrawn = bandTagsDrawn,
                            modifier = Modifier.align(Alignment.BottomStart)
                        ) {
                            MapBannerText(
                                text = if (isRecording)
                                    stringResource(R.string.exit_press_back_again_recording)
                                else
                                    stringResource(R.string.exit_press_back_again)
                            )
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

        // ── Import feedback banner (bottom band, centred in the space the map leaves free) ──
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
                tagsDrawn = bandTagsDrawn,
                modifier = Modifier.align(Alignment.BottomStart)
            )
        }

        // ── Transient track-operation status banner (export/import in progress) ──
        trackOpStatus?.let { message ->
            MapStatusBanner(
                message = message,
                tagsDrawn = bandTagsDrawn,
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




