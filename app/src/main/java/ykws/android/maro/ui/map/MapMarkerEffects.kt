package ykws.android.maro.ui.map

import android.graphics.Color
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableSharedFlow
import org.osmdroid.views.MapView
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.MarkerOrigin
import ykws.android.maro.data.model.markers.UserMarker
import ykws.android.maro.data.settings.AppSettings
import ykws.android.maro.data.track.IdleCaptureResult
import ykws.android.maro.data.track.IdleThresholdCallback
import ykws.android.maro.data.track.WhereAmIProvider
import ykws.android.maro.spatial.DebugSegment

/** The Where-Am-I debug ray's stroke, in dp — the 3 px of the 3× reference, over three. */
private const val MARKER_DEBUG_RAY_STROKE_DP = 1f

/**
 * Marker wiring effects (extracted from MapScreen): shared-settings bridge to child
 * ViewModels, crash-orphan cleanup, marker-change watcher, BoatMarker idle callback,
 * and the WhereAmIProvider process-scoped bridge.
 */
@Composable
internal fun MapMarkerEffects(
    viewModel: NavigationViewModel,
    markersViewModel: MarkersViewModel,
    trackViewModel: ykws.android.maro.data.track.TrackViewModel,
    userMarkers: List<UserMarker>
) {
    val markerChangeFlow = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }

    // ── Wire shared settings into child ViewModels (framework fix) ──
    LaunchedEffect(Unit) {
        markersViewModel.observeSettings(viewModel.settings, viewModel::updateSettings)
    }
    LaunchedEffect(Unit) {
        trackViewModel.observeSettings(viewModel.settings)
    }

    // ── Startup: clean up crash-orphaned auto-markers (IDLE_AUTO && !keepable) ──
    LaunchedEffect(Unit) {
        val crashOrphans = userMarkers.filter { it.origin == MarkerOrigin.IDLE_AUTO && !it.keepable }
        for (m in crashOrphans) {
            markersViewModel.deleteMarker(m.id)
        }
        if (crashOrphans.isNotEmpty()) {
            Log.d("MaroII_Map", "Startup cleanup: removed ${crashOrphans.size} crash-orphan auto-markers")
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
}

/**
 * Marker debug effects (extracted from MapScreen): the WhereAmI debug-segment
 * visual overlay render.
 */
@Composable
internal fun MapMarkerDebugEffects(
    mapView: MapView?,
    appSettings: AppSettings,
    debugSegments: List<DebugSegment>
) {
    // ── WhereAmI debug segments: visual overlay on the map ─────────────────
    // Green = clear line-of-sight, Red = blocked by land.
    // The flag is a key as well as a condition: a toggle-off re-runs this effect so the polylines
    // it owns are withdrawn, while a merely stale non-empty list would redraw them (CH3).
    LaunchedEffect(mapView, debugSegments, appSettings.markerDebugRays) {
        val mv = mapView ?: run { Log.d("WIA", "DEBUGGER: mapView null, skipping render"); return@LaunchedEffect }
        val raysOn = appSettings.markerDebugRays
        Log.d("WIA", "DEBUGGER: rays=$raysOn segments=${debugSegments.size}")
        // Remove previous debug polylines
        mv.overlays.removeAll {
            (it as? org.osmdroid.views.overlay.Polyline)?.title?.startsWith("wia_debug_") == true
        }
        // Render current segments
        if (raysOn && debugSegments.isNotEmpty()) {
            // The debug rays are the map's own strokes, dp like every other length the pass converted:
            // 3 px on the 3× reference is 1 dp, and the map's density is what the paint below takes.
            val density = mv.paintDensity
            debugSegments.forEachIndexed { index, segment ->
                val color = if (segment.blocked) Color.RED else Color.GREEN
                val polyline = org.osmdroid.views.overlay.Polyline().apply {
                    title = "wia_debug_$index"
                    setPoints(listOf(
                        org.osmdroid.util.GeoPoint(segment.boat.latitude, segment.boat.longitude),
                        org.osmdroid.util.GeoPoint(segment.target.latitude, segment.target.longitude)
                    ))
                    outlinePaint.color = color
                    outlinePaint.strokeWidth = dpToPx(MARKER_DEBUG_RAY_STROKE_DP, density)
                }
                mv.overlays.add(polyline)
            }
        }
        mv.invalidate()
    }
}
