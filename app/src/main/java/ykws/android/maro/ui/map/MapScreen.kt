package ykws.android.maro.ui.map

import android.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import ykws.android.maro.data.model.CoastlineState
import ykws.android.maro.data.model.LatLng

/**
 * Compose screen rendering the coastline on an OSMdroid map.
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

    Box(modifier = modifier.fillMaxSize()) {
        when (val s = state) {
            is CoastlineState.Loading -> {
                // Show map background even while loading
                CoastlineMapView(
                    polylines = emptyList(),
                    center = mapCenter,
                    modifier = Modifier.fillMaxSize()
                )
                LoadingOverlay(progress = progress)
            }
            is CoastlineState.Ready -> {
                CoastlineMapView(
                    polylines = s.polylines.map { segment -> segment.points },
                    center = mapCenter,
                    modifier = Modifier.fillMaxSize()
                )
            }
            is CoastlineState.Error -> {
                CoastlineMapView(
                    polylines = emptyList(),
                    center = mapCenter,
                    modifier = Modifier.fillMaxSize()
                )
                ErrorOverlay(
                    message = s.message,
                    onRetry = { viewModel.loadCoastline() },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // ── Top status bar ─────────────────────────────────────────────────
        StatusBar(
            state = state,
            progress = progress,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp, start = 8.dp, end = 8.dp)
        )

        // ── Water/Shore indicator ──────────────────────────────────────────
        WaterShoreIndicator(
            isWater = isWater,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 64.dp, end = 8.dp)
        )

        // ── Regenerate button ──────────────────────────────────────────────
        Button(
            onClick = { viewModel.loadCoastline() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
                .fillMaxWidth(0.7f),
            colors = ButtonDefaults.buttonColors(
                containerColor = ComposeColor(0xFF1565C0)
            ),
            shape = RoundedCornerShape(12.dp),
            enabled = state !is CoastlineState.Loading
        ) {
            Text(
                text = if (state is CoastlineState.Loading) "Génération en cours…"
                       else "Régénérer la côte",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ── Status bar ──────────────────────────────────────────────────────────────

@Composable
private fun StatusBar(
    state: CoastlineState,
    progress: Int,
    modifier: Modifier = Modifier
) {
    val bgColor = when (state) {
        is CoastlineState.Loading -> ComposeColor(0xCC1565C0) // Blue
        is CoastlineState.Ready -> ComposeColor(0xCC2E7D32)   // Green
        is CoastlineState.Error -> ComposeColor(0xCCC62828)   // Red
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (state is CoastlineState.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = ComposeColor.White
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            val statusText = when (state) {
                is CoastlineState.Loading -> "Génération de la côte…"
                is CoastlineState.Ready -> {
                    val totalPoints = state.metadata.pointCount
                    val polyCount = state.polylines.size
                    "$polyCount polylignes, $totalPoints points"
                }
                is CoastlineState.Error -> "Erreur : ${state.message}"
            }
            Text(
                text = statusText,
                color = ComposeColor.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }

        if (state is CoastlineState.Loading) {
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = ComposeColor.White,
                trackColor = ComposeColor(0x60FFFFFF)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$progress%",
                color = ComposeColor(0xCCFFFFFF),
                fontSize = 11.sp
            )
        }
    }
}

// ── Water / Shore indicator ─────────────────────────────────────────────────

@Composable
private fun WaterShoreIndicator(
    isWater: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(ComposeColor(0xEEFFFFFF))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (isWater) "🌊" else "🏔️",
            fontSize = 24.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (isWater) "EAU" else "TERRE",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (isWater) ComposeColor(0xFF1565C0) else ComposeColor(0xFF2E7D32)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = if (isWater) "(au large)" else "(à terre)",
            fontSize = 10.sp,
            color = ComposeColor(0xFF666666)
        )
    }
}

// ── Loading overlay ─────────────────────────────────────────────────────────

@Composable
private fun LoadingOverlay(progress: Int) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Chargement de la côte…",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .padding(horizontal = 32.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$progress%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Erreur",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry) {
                Text("Réessayer")
            }
        }
    }
}

// ── OSMdroid map view ───────────────────────────────────────────────────────

@Composable
private fun CoastlineMapView(
    polylines: List<List<LatLng>>,
    center: LatLng,
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

            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                minZoomLevel = 8.0
                maxZoomLevel = 18.0
                controller.setZoom(11.0)
                controller.setCenter(GeoPoint(center.latitude, center.longitude))
                drawCoastline(this, polylines)
            }
        },
        update = { mapView ->
            mapView.overlays.removeAll { it is Polyline }
            drawCoastline(mapView, polylines)
            // Re-center if coastline data loaded
            if (polylines.isNotEmpty()) {
                mapView.controller.setCenter(GeoPoint(center.latitude, center.longitude))
            }
            mapView.invalidate()
        }
    )
}

/**
 * Draws the coastline polylines on the OSMdroid [MapView].
 *
 * Mainland: solid blue (#1565C0), 7px width
 * Islands: lighter blue (#42A5F5), 5px width
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
                        else Color.parseColor("#FF42A5F5")
                strokeWidth = if (isMainland) 7f else 5f
                alpha = if (isMainland) 200 else 160
                isAntiAlias = true
            }
        }
        mapView.overlays.add(polyline)
    }
}
