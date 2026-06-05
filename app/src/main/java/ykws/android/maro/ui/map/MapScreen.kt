package ykws.android.maro.ui.map

import android.graphics.Color
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import kotlin.math.abs
import kotlin.math.pow
import ykws.android.maro.data.model.CoastlinePoint
import ykws.android.maro.data.model.CoastlineSegment
import ykws.android.maro.data.model.CoastlineState
import ykws.android.maro.data.model.GenerationProgress
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.Zone300Data

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
    val zone300 by viewModel.zone300.collectAsState()
    val inZone300 by viewModel.inZone300.collectAsState()
    val distanceToZone by viewModel.distanceToZone.collectAsState()
    var mapView by remember { mutableStateOf<MapView?>(null) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isLandscape = maxWidth > maxHeight
        val landscapeDashboardWidth = maxHeight * 2 / 3 // dashboard width = ⅔ screen height
        val portraitDashboardHeight = maxWidth / 3

        if (isLandscape) {
            // ── LANDSCAPE: Dashboard (left) + Map (right) ──────────────────
            Row(modifier = Modifier.fillMaxSize()) {
                DashboardPanel(
                    state = state,
                    isWater = isWater,
                    distanceToShore = distanceToShore,
                    inZone300 = inZone300,
                    distanceToZone = distanceToZone,
                    onGenerate = { viewModel.loadCoastline() },
                    modifier = Modifier
                        .width(landscapeDashboardWidth)
                        .fillMaxHeight()
                )

                // Map fills the remaining horizontal space
                MapContent(
                    state = state,
                    progress = progress,
                    mapCenter = mapCenter,
                    isWater = isWater,
                    zoomLevel = zoomLevel,
                    distanceToShore = distanceToShore,
                    zone300 = zone300,
                    mapView = mapView,
                    onCenterChanged = viewModel::updateMapCenter,
                    onZoomChanged = viewModel::updateZoomLevel,
                    onMapViewReady = { mapView = it },
                    onRetry = { viewModel.loadCoastline() },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
        } else {
            // ── PORTRAIT: Map (top) + Dashboard (bottom) ───────────────────
            Column(modifier = Modifier.fillMaxSize()) {
                MapContent(
                    state = state,
                    progress = progress,
                    mapCenter = mapCenter,
                    isWater = isWater,
                    zoomLevel = zoomLevel,
                    distanceToShore = distanceToShore,
                    zone300 = zone300,
                    mapView = mapView,
                    onCenterChanged = viewModel::updateMapCenter,
                    onZoomChanged = viewModel::updateZoomLevel,
                    onMapViewReady = { mapView = it },
                    onRetry = { viewModel.loadCoastline() },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )

                DashboardPanel(
                    state = state,
                    isWater = isWater,
                    distanceToShore = distanceToShore,
                    inZone300 = inZone300,
                    distanceToZone = distanceToZone,
                    onGenerate = { viewModel.loadCoastline() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(portraitDashboardHeight)
                )
            }
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
    inZone300: Boolean,
    distanceToZone: Double?,
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

            // \u2500\u2500 300 m zone status \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500
            if (state is CoastlineState.Ready && distanceToZone != null) {
                val zoneM = abs(distanceToZone)
                val zoneText = if (zoneM >= 1000.0) "%.1f km".format(zoneM / 1000.0)
                               else "%.0f m".format(zoneM)
                val (msg, tint) = if (inZone300) {
                    "\u26A0 Zone des 300 m \u2014 5 n\u0153uds ($zoneText avant la sortie)" to 0xFFEF5350
                } else {
                    "\u00C0 $zoneText de la zone des 300 m" to 0xFF90A4AE
                }
                Text(
                    text = msg,
                    color = ComposeColor(tint),
                    fontSize = 13.sp,
                    fontWeight = if (inZone300) FontWeight.Bold else FontWeight.Medium
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

                // ── Earth / Water toggle ──────────────────────────────────
                EarthWaterIcon(
                    emoji = if (isWater) "\uD83C\uDF0A" else "\uD83C\uDFD4\uFE0F",
                    isActive = true,
                    activeColor = if (isWater) ComposeColor(0xFF1565C0) else ComposeColor(0xFF2E7D32),
                    contentDescription = if (isWater) "Eau" else "Terre"
                )
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
    zone300: Zone300Data?,
    mapView: MapView?,
    onCenterChanged: (Double, Double) -> Unit,
    onZoomChanged: (Double) -> Unit,
    onMapViewReady: (MapView) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.clipToBounds()) {
        // Memoize per state instance so panning (which does not change state) keeps a
        // stable list identity → no spurious overlay rebuilds.
        val segments = remember(state) {
            when (state) {
                is CoastlineState.Ready -> (state as CoastlineState.Ready).polylines
                else -> emptyList()
            }
        }
        CoastlineMapView(
            segments = segments,
            zone300 = zone300,
            zoomLevel = zoomLevel,
            center = mapCenter,
            onCenterChanged = onCenterChanged,
            onZoomChanged = onZoomChanged,
            onMapViewReady = onMapViewReady,
            modifier = Modifier.fillMaxSize()
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
                        onClick = { mapView?.controller?.zoomIn() }
                    )
                    ZoomButton(
                        icon = null,
                        desc = "Zoom arri\u00E8re",
                        label = "\u2212",
                        onClick = { mapView?.controller?.zoomOut() }
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
    zoomLevel: Double,
    center: LatLng,
    onCenterChanged: (Double, Double) -> Unit = { _, _ -> },
    onZoomChanged: (Double) -> Unit = {},
    onMapViewReady: (MapView) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // Rebuild overlays only when the data or zone visibility changes — never on a
    // center pan (osmdroid pans internally; a per-frame removeAll+redraw would jank).
    val zoneVisible = zoomLevel >= ZONE_MIN_ZOOM
    val overlayKey = remember(segments, zone300, zoneVisible) { Any() }
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
                controller.setZoom(11.0)
                controller.setCenter(GeoPoint(center.latitude, center.longitude))
                drawZone300(this, zone300, zoomLevel)
                drawCoastline(this, segments)

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
                // Band overlays are Polygons + Polylines; remove both so the band's
                // red line is not orphaned and stale polygons don't accumulate.
                mapView.overlays.removeAll { it is Polyline || it is Polygon }
                drawZone300(mapView, zone300, zoomLevel)   // fill (bottom)
                drawCoastline(mapView, segments)            // coastline
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
