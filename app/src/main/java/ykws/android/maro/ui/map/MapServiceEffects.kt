package ykws.android.maro.ui.map

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import ykws.android.maro.data.settings.AppSettings
import ykws.android.maro.data.track.TrackRecordingService

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
    navigationState: NavigationState,
    appSettings: AppSettings,
    boatIsWater: Boolean,
    trackRecorderState: ykws.android.maro.data.track.TrackRecorderUiState
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
                viewModel.feedDemoPosition(center.latitude, center.longitude)
            }
            kotlinx.coroutines.delay(1_000L)
        }
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
}
