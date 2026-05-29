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
import org.osmdroid.views.overlay.Polyline
import ykws.android.maro.data.model.CoastlinePoint
import ykws.android.maro.data.model.CoastlineSegment
import ykws.android.maro.data.model.CoastlineState
import ykws.android.maro.data.model.GenerationProgress
import ykws.android.maro.data.model.LatLng

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
    var mapView by mutableStateOf<MapView?>(null)

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
    mapView: MapView?,
    onCenterChanged: (Double, Double) -> Unit,
    onZoomChanged: (Double) -> Unit,
    onMapViewReady: (MapView) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.clipToBounds()) {
        val segments = when (state) {
            is CoastlineState.Ready -> (state as CoastlineState.Ready).polylines
            else -> emptyList()
        }
        CoastlineMapView(
            segments = segments,
            center = mapCenter,
            onCenterChanged = onCenterChanged,
            onZoomChanged = onZoomChanged,
            onMapViewReady = onMapViewReady,
            modifier = Modifier.fillMaxSize()
        )

        // ── Zoom buttons (bottom-right of map) ────────────────────────────
        if (mapView != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = 12.dp),
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

        // ── Center position marker ────────────────────────────────────────
        CenterMarkerOverlay(
            isWater = isWater,
            zoomLevel = zoomLevel,
            distanceToShore = distanceToShore,
            modifier = Modifier.align(Alignment.Center)
        )

        // ── Loading progress overlay ──────────────────────────────────────
        if (state is CoastlineState.Loading) {
            LoadingOverlay(
                progress = progress,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 56.dp)
            )
        }

        // ── Error overlay ─────────────────────────────────────────────────
        if (state is CoastlineState.Error) {
            ErrorOverlay(
                message = (state as CoastlineState.Error).message,
                onRetry = onRetry,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 56.dp)
            )
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
                controller.setZoom(11.0)
                controller.setCenter(GeoPoint(center.latitude, center.longitude))
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
            mapView.overlays.removeAll { it is Polyline }
            drawCoastline(mapView, segments)
            mapView.invalidate()
        }
    )
}

// ── Center marker overlay ────────────────────────────────────────────────────

// ── Tuning constants for dynamic marker sizing ────────────────────────────────
// Zoom→dp anchors: piecewise-linear interpolation across [minZoom..maxZoom].
// The marker grows as you zoom in so it represents a roughly constant ground
// footprint rather than shrinking into insignificance at street level.

/** Map's minimum zoom (must match [MapView.minZoomLevel]). */
private const val MIN_ZOOM = 8.0
/** Map's maximum zoom (must match [MapView.maxZoomLevel]). */
private const val MAX_ZOOM = 18.0
/** Reference zoom used as the "normal" sizing baseline. */
private const val REF_ZOOM = 11.0

// Water (boat) marker dp at [MIN_ZOOM], [REF_ZOOM], [MAX_ZOOM]
private const val BOAT_DP_AT_MIN_ZOOM  = 24.0
private const val BOAT_DP_AT_REF_ZOOM  = 48.0
private const val BOAT_DP_AT_MAX_ZOOM  = 96.0

// Land (dot) marker dp at [MIN_ZOOM], [REF_ZOOM], [MAX_ZOOM]
private const val DOT_DP_AT_MIN_ZOOM   = 16.0
private const val DOT_DP_AT_REF_ZOOM   = 32.0
private const val DOT_DP_AT_MAX_ZOOM   = 64.0

// ── Distance-to-coast shrink ramp ─────────────────────────────────────────────
// When the map center is close to the coastline, the marker shrinks so it
// doesn't visually overlap the ground ("run aground"). The multiplier ramps
// linearly from [DIST_SHRINK_MIN_MULT] at 0 m up to 1.0 at [DIST_SHRINK_RAMP_M].

/** Minimum size multiplier when exactly on the coastline. */
private const val DIST_SHRINK_MIN_MULT = 0.5
/** Distance in meters at which the marker reaches full (1.0×) size. */
private const val DIST_SHRINK_RAMP_M   = 2000.0

// ───────────────────────────────────────────────────────────────────────────────

/**
 * A fixed icon drawn at the center of the screen, indicating the current
 * GPS position. Stays in place while the map moves beneath it.
 *
 * Sizing is dynamic:
 * - Follows the map zoom level: bigger when zoomed in (representing constant
 *   ground distance), smaller when zoomed out.
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

    // ── Base size: scales with zoom (grows when zoomed in) ────────────────
    val sizeByZoom = if (isWater) {
        lerpDp(zoomLevel,
            MIN_ZOOM to BOAT_DP_AT_MIN_ZOOM,
            REF_ZOOM to BOAT_DP_AT_REF_ZOOM,
            MAX_ZOOM to BOAT_DP_AT_MAX_ZOOM)
    } else {
        lerpDp(zoomLevel,
            MIN_ZOOM to DOT_DP_AT_MIN_ZOOM,
            REF_ZOOM to DOT_DP_AT_REF_ZOOM,
            MAX_ZOOM to DOT_DP_AT_MAX_ZOOM)
    }

    // ── Distance-to-coast multiplier: [DIST_SHRINK_MIN_MULT] on the coast
    //    → 1.0 at [DIST_SHRINK_RAMP_M] m ───────────────────────────────────
    val distMultiplier = if (distanceToShore != null) {
        (DIST_SHRINK_MIN_MULT +
         (1.0 - DIST_SHRINK_MIN_MULT) * (distanceToShore / DIST_SHRINK_RAMP_M).coerceIn(0.0, 1.0))
            .toFloat()
    } else {
        1.0f  // no coastline data → full size
    }

    // Apply the distance multiplier directly to the dp value.
    val finalSizeDp = (sizeByZoom.value * distMultiplier).dp

    Image(
        painter = painterResource(id = drawableId),
        contentDescription = description,
        modifier = modifier.size(finalSizeDp),
        contentScale = ContentScale.Fit
    )
}

/**
 * Linear interpolation in dp-space across a piecewise [zoom]→dp mapping.
 *
 * Clamps [zoom] to the range defined by the first and last key pairs,
 * then interpolates between the nearest two anchors.
 */
private fun lerpDp(
    zoom: Double,
    vararg anchors: Pair<Double, Double>
): androidx.compose.ui.unit.Dp {
    val z = zoom.coerceIn(anchors.first().first, anchors.last().first)

    for (i in 0 until anchors.size - 1) {
        val (z1, s1) = anchors[i]
        val (z2, s2) = anchors[i + 1]
        if (z in z1..z2) {
            val t = ((z - z1) / (z2 - z1)).toFloat()
            val dp = s1 + (s2 - s1) * t
            return dp.dp
        }
    }
    // fallback (shouldn't reach here with clamping)
    return anchors.last().second.dp
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
                color = if (segment.isMainland) Color.parseColor("#FF1565C0")
                        else Color.parseColor("#FF42A5F5")
                strokeWidth = if (segment.isMainland) 7f else 5f
                alpha = if (segment.isMainland) 200 else 160
                isAntiAlias = true
            }
        }
        mapView.overlays.add(polyline)
    }
}
