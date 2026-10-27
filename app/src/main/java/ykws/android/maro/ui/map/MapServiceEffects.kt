package ykws.android.maro.ui.map

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import ykws.android.maro.data.settings.AppSettings
import ykws.android.maro.data.track.TrackRecordingService
import ykws.android.maro.spatial.Units

/**
 * Service intents orchestration effects (extracted from MapScreen):
 * foreground notification updates, water-state push, and the demo-mode sample feed.
 *
 * The demo feed host must stay UNCONDITIONALLY composed (LaunchedEffect(Unit),
 * deliberately NOT keyed on gpsMode): toggling the position source must not
 * tear down / restart the feed.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Composable
internal fun MapServiceEffects(
    context: Context,
    viewModel: NavigationViewModel,
    appSettings: AppSettings
) {
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
            val speedMs = speedKn?.let { Units.knotsToMps(it.toDouble()).toFloat() }
            val bearing = if (isGps) nav.bearingDeg else nav.demoBearingDeg
            ykws.android.maro.data.track.TrackSample(
                position = pos,
                speedMps = speedMs,
                bearingDeg = bearing,
                hasLock = isGps,
                timestampEpochMs = System.currentTimeMillis(),
                accuracyM = if (isGps) viewModel.gpsAccuracy.value else null,
                isDemo = !isGps
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
                viewModel.feedDemoPosition(center.latitude, center.longitude)
            }
            kotlinx.coroutines.delay(1_000L)
        }
    }

    // Read current flows even while the Activity's Compose frame clock is paused.
    // Water/movement/mode/recording transitions send immediately; statistics keep the ticker stable.
    LaunchedEffect(viewModel) {
        val keys = combine(
            TrackRecordingService.uiState.map { it.state == ykws.android.maro.data.track.TrackRecorderState.ON }.distinctUntilChanged(),
            viewModel.settings.map { it.gpsMode }.distinctUntilChanged(),
            viewModel.boatIsWater,
            viewModel.isStopped,
            TrackRecordingService.isStopped
        ) { recording, gps, water, displayStopped, recordingStopped ->
            NotificationTickerKey(recording, gps, water, if (recording && gps) !recordingStopped else !displayStopped)
        }
        notificationUpdates(keys) { key ->
            val state = TrackRecordingService.uiState.value
            val nav = viewModel.navigationState.value
            val intent = Intent(context, TrackRecordingService::class.java).apply {
                action = TrackRecordingService.ACTION_UPDATE
                putExtra(TrackRecordingService.EXTRA_IS_DEMO, !key.gpsMode)
                if (!key.recording || !key.gpsMode) {
                    putExtra(TrackRecordingService.EXTRA_IS_MOVING, key.moving)
                    putExtra(TrackRecordingService.EXTRA_SPEED_KN, nav.speedKnots ?: nav.demoSpeedKnots)
                }
                putExtra(TrackRecordingService.EXTRA_ON_WATER, key.onWater)
                putExtra(TrackRecordingService.EXTRA_RECORDING, key.recording)
                if (key.recording) {
                    putExtra(TrackRecordingService.EXTRA_DISTANCE_NM, state.distanceNm)
                    putExtra(TrackRecordingService.EXTRA_ELAPSED_SEC, state.elapsedSeconds)
                    putExtra(TrackRecordingService.EXTRA_IDLE_SEC, state.idleDurationSec)
                    putExtra(TrackRecordingService.EXTRA_AVG_SPEED_KN, state.avgSpeedKn)
                    putExtra(TrackRecordingService.EXTRA_MAX_SPEED_KN, state.maxSpeedKn)
                    putExtra(TrackRecordingService.EXTRA_POINT_COUNT, state.pointCount)
                }
            }
            // This also carries the immediate water toggle; the service owns its Tasker broadcast.
            context.startService(intent)
        }
    }
}
