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
                    mapView = mapView,
                    onCenterChanged = viewModel::updateMapCenter,
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
                    mapView = mapView,
                    onCenterChanged = viewModel::updateMapCenter,
                    onMapViewReady = { mapView = it },
                    onRetry = { viewModel.loadCoastline() },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )

                DashboardPanel(
                    state = state,
                    isWater = isWater,
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
    onGenerate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(ComposeColor(0xFF1A1A2E))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
        ) {
            // ── Generate button ───────────────────────────────────────────
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

            // ── Earth / Water toggle ──────────────────────────────────────
            EarthWaterIcon(
                emoji = if (isWater) "\uD83C\uDF0A" else "\uD83C\uDFD4\uFE0F",
                isActive = true,
                activeColor = if (isWater) ComposeColor(0xFF1565C0) else ComposeColor(0xFF2E7D32),
                contentDescription = if (isWater) "Eau" else "Terre"
            )
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
    mapView: MapView?,
    onCenterChanged: (Double, Double) -> Unit,
    onMapViewReady: (MapView) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.clipToBounds()) {
        val polylines = when (state) {
            is CoastlineState.Ready -> (state as CoastlineState.Ready).polylines.map { it.points }
            else -> emptyList()
        }
        CoastlineMapView(
            polylines = polylines,
            center = mapCenter,
            onCenterChanged = onCenterChanged,
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
        CenterMarkerOverlay(modifier = Modifier.align(Alignment.Center))

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
    polylines: List<List<LatLng>>,
    center: LatLng,
    onCenterChanged: (Double, Double) -> Unit = { _, _ -> },
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
                drawCoastline(this, polylines)

                // Listen for map pan/zoom to report new center in real time
                addMapListener(object : MapListener {
                    override fun onScroll(event: ScrollEvent): Boolean {
                        val geo = mapCenter
                        onCenterChanged(geo.latitude, geo.longitude)
                        return false
                    }

                    override fun onZoom(event: ZoomEvent): Boolean {
                        val geo = mapCenter
                        onCenterChanged(geo.latitude, geo.longitude)
                        return false
                    }
                })
            }.also { onMapViewReady(it) }
        },
        update = { mapView ->
            mapView.overlays.removeAll { it is Polyline }
            drawCoastline(mapView, polylines)
            mapView.invalidate()
        }
    )
}

// ── Center marker overlay ────────────────────────────────────────────────────

/**
 * A fixed Maro icon drawn at the center of the screen, simulating the
 * current GPS position. Stays in place while the map moves beneath it.
 *
 * Uses [R.drawable.maro_marker] — the Maro logo with transparent background.
 * Swap the PNG file to update the marker appearance.
 */
@Composable
private fun CenterMarkerOverlay(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(id = R.drawable.maro_marker),
        contentDescription = "Position actuelle",
        modifier = modifier.size(48.dp),
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
 * Draws the coastline polylines on the OSMdroid [MapView].
 *
 * Mainland: solid blue (#1565C0), 7px width
 * Islands:  blue-leaning purple (#4A70B0), 7px width — slightly distinct from mainland
 */
private fun drawCoastline(
    mapView: MapView,
    polylines: List<List<LatLng>>
) {
    val mainlandIdx = if (polylines.size > 1) {
        polylines.indices.maxByOrNull { polylines[it].size }
    } else null

    for ((idx, points) in polylines.withIndex()) {
        if (points.size < 2) continue
        val isMainland = idx == mainlandIdx
        val osmPoints = points.map { GeoPoint(it.latitude, it.longitude) }

        val polyline = Polyline().apply {
            setPoints(osmPoints)
            outlinePaint.apply {
                color = if (isMainland) Color.parseColor("#FF1565C0")
                        else Color.parseColor("#FF4A70B0") // Blue-leaning purple (more blue than purple)
                strokeWidth = 7f  // same width for mainland and islands
                alpha = 200       // same opacity for both
                isAntiAlias = true
            }
        }
        mapView.overlays.add(polyline)
    }
}
