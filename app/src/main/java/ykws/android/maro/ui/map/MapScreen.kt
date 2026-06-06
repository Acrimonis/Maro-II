package ykws.android.maro.ui.map

import android.graphics.Color
import androidx.activity.compose.BackHandler
import ykws.android.maro.R
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
import org.osmdroid.views.overlay.Polyline
import kotlin.math.pow
import ykws.android.maro.data.model.CoastlinePoint
import ykws.android.maro.data.model.CoastlineSegment
import ykws.android.maro.data.model.CoastlineState
import ykws.android.maro.data.model.GenerationProgress
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.settings.AppSettings

/**
 * Compose screen rendering the coastline on an OSMdroid map.
 *
 * Landscape: dashboard panel anchored to the left edge
 * (width = screen height ÷ 2, full height), map fills the right area.
 *
 * Portrait: map on top, dashboard bar at the bottom.
 */
@Composable
fun MapScreen(
    viewModel: CoastlineViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val mapCenter by viewModel.mapCenter.collectAsState()
    val isWater by viewModel.isWater.collectAsState()
    val distanceToShore by viewModel.distanceToShore.collectAsState()
    val zoomLevel by viewModel.zoomLevel.collectAsState()
    val appSettings by viewModel.settings.collectAsState()
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var showSettings by remember { mutableStateOf(false) }

    // ── Force marker to match MapView zoom once the view is ready ────────
    // Even though _zoomLevel is seeded from persisted settings, there can be
    // a frame where collectAsState() captures the initial default before the
    // seeded value propagates.  This LaunchedEffect re-applies the real zoom
    // from the MapView after it's created, guaranteeing the marker is correct.
    LaunchedEffect(mapView) {
        val mv = mapView ?: return@LaunchedEffect
        viewModel.updateZoomLevel(mv.zoomLevelDouble)
    }

    // ── Save map position on pause (covers kill, background, minimize) ────
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                viewModel.savePosition()
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
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isLandscape = maxWidth > maxHeight
            val landscapeDashboardWidth = maxHeight * 2 / 3
            val portraitDashboardHeight = maxWidth / 3

            if (isLandscape) {
                Row(modifier = Modifier.fillMaxSize()) {
                    DashboardPanel(
                        state = state,
                        isWater = isWater,
                        distanceToShore = distanceToShore,
                        onGenerate = { viewModel.loadCoastline() },
                        modifier = Modifier
                            .width(landscapeDashboardWidth)
                            .fillMaxHeight()
                    )

                    MapContent(
                        state = state,
                        progress = progress,
                        mapCenter = mapCenter,
                        isWater = isWater,
                        zoomLevel = zoomLevel,
                        distanceToShore = distanceToShore,
                        appSettings = appSettings,
                        mapView = mapView,
                        onCenterChanged = viewModel::updateMapCenter,
                        onZoomChanged = viewModel::updateZoomLevel,
                        onMapViewReady = { mapView = it },
                        onRetry = { viewModel.loadCoastline() },
                        onOpenSettings = { showSettings = true },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    MapContent(
                        state = state,
                        progress = progress,
                        mapCenter = mapCenter,
                        isWater = isWater,
                        zoomLevel = zoomLevel,
                        distanceToShore = distanceToShore,
                        appSettings = appSettings,
                        mapView = mapView,
                        onCenterChanged = viewModel::updateMapCenter,
                        onZoomChanged = viewModel::updateZoomLevel,
                        onMapViewReady = { mapView = it },
                        onRetry = { viewModel.loadCoastline() },
                        onOpenSettings = { showSettings = true },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )

                    DashboardPanel(
                        state = state,
                        isWater = isWater,
                        distanceToShore = distanceToShore,
                        onGenerate = { viewModel.loadCoastline() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(portraitDashboardHeight)
                    )
                }
            }
        }

        // ── Settings overlay (full-screen, covers dashboard too) ──────────
        if (showSettings) {
            SettingsOverlay(
                settings = appSettings,
                onUpdateSettings = viewModel::updateSettings,
                onDismiss = { showSettings = false }
            )
        }
    }
}

// ── Dashboard panel ──────────────────────────────────────────────────────────

/**
 * Dashboard panel containing the generate button and earth/water toggle.
 *
 * Uses a [FlowRow] anchored to the bottom of the panel so the button and
 * icon sit side-by-side when horizontal space allows, wrapping to a second
 * line without clipping when the dashboard is too narrow.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DashboardPanel(
    state: CoastlineState,
    isWater: Boolean,
    distanceToShore: Double?,
    onGenerate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(ComposeColor(0xFF1A1A2E))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Distance to shore ────────────────────────────────────────
            if (state is CoastlineState.Ready && distanceToShore != null) {
                val distM = distanceToShore
                val distKm = distM / 1000.0
                val displayText = if (distM >= 1000.0) {
                    "%.1f km".format(distKm)
                } else {
                    "%.0f m".format(distM)
                }
                val label = if (isWater) "de la c\u00F4te" else "de la mer"
                Text(
                    text = "\u00C0 $displayText $label",
                    color = ComposeColor(0xFFB0BEC5),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
            ) {
                // ── Generate button ───────────────────────────────────────
                Button(
                    onClick = onGenerate,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ComposeColor(0xFF1565C0),
                        disabledContainerColor = ComposeColor(0x401565C0),
                        disabledContentColor = ComposeColor(0x99B0BEC5)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    enabled = state !is CoastlineState.Loading
                ) {
                    Text(
                        text = when (state) {
                            is CoastlineState.Idle -> "G\u00E9n\u00E9rer la c\u00F4te"
                            is CoastlineState.Loading -> "G\u00E9n\u00E9ration en cours\u2026"
                            else -> "R\u00E9g\u00E9n\u00E9rer la c\u00F4te"
                        },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

            }
        }
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
        val allSegments = when (state) {
            is CoastlineState.Ready -> (state as CoastlineState.Ready).polylines
            else -> emptyList()
        }
        // Apply coastline visibility toggle
        val segments = if (appSettings.coastlineVisible) allSegments else emptyList()
        CoastlineMapView(
            segments = segments,
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
                            // Push the new zoom to the ViewModel immediately so the
                            // boat marker resizes on the same frame as the map zoom.
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
    center: LatLng,
    initialZoom: Double,
    onCenterChanged: (Double, Double) -> Unit = { _, _ -> },
    onZoomChanged: (Double) -> Unit = {},
    onMapViewReady: (MapView) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

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
                drawCoastline(this, segments)

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
            mapView.overlays.removeAll { it is Polyline }
            drawCoastline(mapView, segments)
            mapView.invalidate()
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

            Spacer(modifier = Modifier.height(24.dp))

            // ── Position par d\u00E9faut (Default position) section ──────
            SectionHeader(title = "Position par d\u00E9faut")

            Spacer(modifier = Modifier.height(8.dp))

            // ── Latitude ──────────────────────────────────────────────────
            SettingsTextFieldRow(
                label = "Latitude",
                value = settings.defaultLatitude,
                onValueChange = { lat ->
                    onUpdateSettings { it.copy(defaultLatitude = lat) }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // ── Longitude ─────────────────────────────────────────────────
            SettingsTextFieldRow(
                label = "Longitude",
                value = settings.defaultLongitude,
                onValueChange = { lon ->
                    onUpdateSettings { it.copy(defaultLongitude = lon) }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ── Footer ────────────────────────────────────────────────────
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "Maro II \u2014 v1.0",
                color = ComposeColor(0xFF546E7A),
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

// ── Settings sub-components ─────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        color = ComposeColor(0xFF1565C0),
        fontSize = 13.sp,
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
