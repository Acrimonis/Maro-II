package ykws.android.maro.data.track

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ykws.android.maro.data.location.AcquisitionMode
import ykws.android.maro.data.location.AdaptiveGpsPolicy
import ykws.android.maro.data.location.GpsFix
import ykws.android.maro.data.track.TrackEvent.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Speed threshold in m/s (~1.55 kn) — below this the GPS wakespeed considers the boat stationary. */
private const val WAKE_SPEED_MPS = 0.8f

/** Duration (ms) speed must be above threshold before auto-start. */
private const val DEBOUNCE_MS = 10_000L

/** Interval (ms) between checkpoint saves during recording. */
private const val CHECKPOINT_INTERVAL_MS = 30_000L

/** The track recorder state machine states. */
enum class TrackRecorderState { IDLE, RECORDING, PAUSED, FINALIZING }

/**
 * UI-friendly representation of the recorder's current status.
 */
data class TrackRecorderUiState(
    val state: TrackRecorderState = TrackRecorderState.IDLE,
    val currentTrackId: String? = null,
    val currentTrackName: String? = null,
    val elapsedSeconds: Long = 0L,
    val pointCount: Int = 0,
    val maxSpeedKn: Float = 0f,
    val avgSpeedKn: Float = 0f,
    val distanceNm: Float = 0f,
    val recordingPoints: List<TrackPoint> = emptyList()
)

/**
 * Coroutine-based track recording state machine.
 *
 * Uses [AdaptiveGpsPolicy] for movement/still detection with configurable
 * time window and distance threshold. Collects [GpsFix] flow, detects geofence
 * crossing, and manages the IDLE ↔ RECORDING ↔ PAUSED → FINALIZING → IDLE cycle.
 *
 * @param repository              Track persistence layer.
 * @param geofenceOriginLat       Port Salis geofence origin latitude.
 * @param geofenceOriginLon       Port Salis geofence origin longitude.
 * @param geofenceRadiusM         Geofence radius in metres.
 * @param geofenceEnabled         When false, recording starts on movement alone.
 * @param adaptiveWindowMs        Adaptive idle window (ms) — how long the boat must stay still before pausing.
 * @param adaptiveThresholdM      Adaptive displacement threshold (m) — max movement still counted as stationary.
 * @param dispatcher              Coroutine dispatcher for the state machine loop.
 */
class TrackRecorder(
    private val repository: TrackRepository,
    private val geofenceOriginLat: Double = 43.55,
    private val geofenceOriginLon: Double = 7.00,
    private val geofenceRadiusM: Double = 500.0,
    private val geofenceEnabled: Boolean = true,
    private val adaptiveWindowMs: Long = 30_000L,
    private val adaptiveThresholdM: Double = 20.0,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val _uiState = MutableStateFlow(TrackRecorderUiState())
    val uiState: StateFlow<TrackRecorderUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<TrackEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<TrackEvent> = _events.asSharedFlow()

    // Internal mutable state
    @Volatile
    private var state: TrackRecorderState = TrackRecorderState.IDLE
    private var currentTrack: Track? = null
    private var pointsSinceLastCheckpoint = 0
    private var recordingStartTimeMs = 0L
    private var totalPausedDurationMs = 0L
    private var pausedStartTimeMs: Long? = null
    private var cumulativeDistanceNm = 0f
    private var lastPointLat: Double? = null
    private var lastPointLon: Double? = null
    private var speedSumMps: Float = 0f
    private var speedCount: Int = 0
    private var debounceStartTime: Long? = null

    private val policy = AdaptiveGpsPolicy()
    private var scope: CoroutineScope? = null
    private var collectingJob: Job? = null
    private var checkpointJob: Job? = null
    private var elapsedTimerJob: Job? = null

    /**
     * Start collecting [GpsFix] events and driving the state machine.
     * Call from viewModelScope or similar.
     */
    fun start(gpsFlow: Flow<GpsFix>) {
        collectingJob?.cancel()
        scope = CoroutineScope(dispatcher + SupervisorJob())
        collectingJob = scope?.launch {
            gpsFlow.collect { fix -> processFix(fix) }
        }
        startElapsedTimer()
    }

    /** Stop the recorder — finalizes the current track if recording/paused. */
    fun stop() {
        if (state == TrackRecorderState.RECORDING || state == TrackRecorderState.PAUSED) {
            finalizeTrack()
        } else {
            cleanup()
        }
    }

    /** Manually start recording (bypasses auto-detection). */
    fun startManual() {
        if (state != TrackRecorderState.IDLE) return
        policy.reset()
        beginRecording(isManual = true)
    }

    private fun processFix(fix: GpsFix) {
        val insideGeofence = geofenceEnabled && TrackGeofenceChecker.isInsideGeofence(
            posLat = fix.position.latitude,
            posLon = fix.position.longitude,
            originLat = geofenceOriginLat,
            originLon = geofenceOriginLon,
            radiusM = geofenceRadiusM
        )

        when (state) {
            TrackRecorderState.IDLE -> {
                // Auto-start: outside geofence AND moving
                if (!insideGeofence && geofenceEnabled) {
                    val mode = policy.onFix(
                        nowMs = System.currentTimeMillis(),
                        pos = fix.position,
                        speedMps = fix.speedMps,
                        windowMs = adaptiveWindowMs,
                        thresholdM = adaptiveThresholdM,
                        wakeSpeedMps = WAKE_SPEED_MPS
                    )
                    if (mode == AcquisitionMode.ACTIVE) {
                        // Debounce: must stay ACTIVE for DEBOUNCE_MS
                        val now = System.currentTimeMillis()
                        if (debounceStartTime == null) {
                            debounceStartTime = now
                        } else if (now - debounceStartTime!! >= DEBOUNCE_MS) {
                            debounceStartTime = null
                            beginRecording(isManual = false, startFix = fix)
                        }
                    } else {
                        debounceStartTime = null
                    }
                }
                // If geofence disabled: start on movement alone
                if (!geofenceEnabled) {
                    val mode = policy.onFix(
                        nowMs = System.currentTimeMillis(),
                        pos = fix.position,
                        speedMps = fix.speedMps,
                        windowMs = adaptiveWindowMs,
                        thresholdM = adaptiveThresholdM,
                        wakeSpeedMps = WAKE_SPEED_MPS
                    )
                    if (mode == AcquisitionMode.ACTIVE) {
                        val now = System.currentTimeMillis()
                        if (debounceStartTime == null) {
                            debounceStartTime = now
                        } else if (now - debounceStartTime!! >= DEBOUNCE_MS) {
                            debounceStartTime = null
                            beginRecording(isManual = false, startFix = fix)
                        }
                    } else {
                        debounceStartTime = null
                    }
                }
            }

            TrackRecorderState.RECORDING -> {
                addPoint(fix)
                // Auto-pause: inside geofence AND policy says IDLE
                if (insideGeofence && geofenceEnabled) {
                    val mode = policy.onFix(
                        nowMs = System.currentTimeMillis(),
                        pos = fix.position,
                        speedMps = fix.speedMps,
                        windowMs = adaptiveWindowMs,
                        thresholdM = adaptiveThresholdM,
                        wakeSpeedMps = WAKE_SPEED_MPS
                    )
                    if (mode == AcquisitionMode.IDLE) {
                        // Inside geofence + still for window → finalize
                        finalizeTrack()
                    }
                }
                // Also check raw speed for pause (policy might not catch instant drops)
                if (fix.speedMps != null && fix.speedMps < WAKE_SPEED_MPS && !insideGeofence) {
                    val mode = policy.onFix(
                        nowMs = System.currentTimeMillis(),
                        pos = fix.position,
                        speedMps = fix.speedMps,
                        windowMs = adaptiveWindowMs,
                        thresholdM = adaptiveThresholdM,
                        wakeSpeedMps = WAKE_SPEED_MPS
                    )
                    if (mode == AcquisitionMode.IDLE) {
                        pauseRecording()
                    }
                }
            }

            TrackRecorderState.PAUSED -> {
                // Auto-resume: outside geofence AND moving
                if (!insideGeofence && geofenceEnabled) {
                    val mode = policy.onFix(
                        nowMs = System.currentTimeMillis(),
                        pos = fix.position,
                        speedMps = fix.speedMps,
                        windowMs = adaptiveWindowMs,
                        thresholdM = adaptiveThresholdM,
                        wakeSpeedMps = WAKE_SPEED_MPS
                    )
                    if (mode == AcquisitionMode.ACTIVE) {
                        resumeRecording()
                    }
                }
                if (!geofenceEnabled && fix.speedMps != null && fix.speedMps > WAKE_SPEED_MPS) {
                    resumeRecording()
                }
            }

            TrackRecorderState.FINALIZING -> {
                // No-op: waiting for finalize to complete
            }
        }
    }

    private fun beginRecording(isManual: Boolean, startFix: GpsFix? = null) {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val name = formatTimestamp(now)
        currentTrack = Track(
            id = id,
            name = name,
            startTimeMs = now,
            trackPoints = mutableListOf()
        )
        recordingStartTimeMs = now
        totalPausedDurationMs = 0L
        cumulativeDistanceNm = 0f
        speedSumMps = 0f
        speedCount = 0
        lastPointLat = null
        lastPointLon = null
        pointsSinceLastCheckpoint = 0
        transitionTo(TrackRecorderState.RECORDING)
        _uiState.update {
            TrackRecorderUiState(
                state = TrackRecorderState.RECORDING,
                currentTrackId = id,
                currentTrackName = name
            )
        }
        _events.tryEmit(Started)
        startCheckpointJob()

        startFix?.let { addPoint(it) }
    }

    private fun pauseRecording() {
        if (state != TrackRecorderState.RECORDING) return
        pausedStartTimeMs = System.currentTimeMillis()
        transitionTo(TrackRecorderState.PAUSED)
        _uiState.update { it.copy(state = TrackRecorderState.PAUSED) }
        _events.tryEmit(Paused)
    }

    private fun resumeRecording() {
        if (state != TrackRecorderState.PAUSED) return
        pausedStartTimeMs?.let {
            totalPausedDurationMs += System.currentTimeMillis() - it
        }
        pausedStartTimeMs = null
        transitionTo(TrackRecorderState.RECORDING)
        _uiState.update { it.copy(state = TrackRecorderState.RECORDING) }
        _events.tryEmit(Resumed)
    }

    private fun addPoint(fix: GpsFix) {
        val track = currentTrack ?: return
        val now = System.currentTimeMillis()
        val timeOffsetSec = ((now - recordingStartTimeMs - totalPausedDurationMs) / 1000).toInt()
        val point = TrackPoint(
            lat = fix.position.latitude,
            lon = fix.position.longitude,
            speedMps = fix.speedMps,
            bearingDeg = fix.bearingDeg,
            timeOffsetSec = timeOffsetSec
        )

        // Accumulate stats
        fix.speedMps?.let { mps ->
            speedSumMps += mps
            speedCount++
            val speedKn = mps * 1.94384f
            if (speedKn > _uiState.value.maxSpeedKn) {
                _uiState.update { it.copy(maxSpeedKn = speedKn) }
            }
        }

        // Haversine distance increment
        if (lastPointLat != null && lastPointLon != null) {
            val distM = TrackGeofenceChecker.distanceM(
                lastPointLat!!, lastPointLon!!, point.lat, point.lon
            )
            cumulativeDistanceNm += (distM / 1852.0).toFloat()
        }
        lastPointLat = point.lat
        lastPointLon = point.lon

        // Rebuild track with new point appended (immutable list)
        currentTrack = track.copy(
            trackPoints = track.trackPoints + point,
            fastestSpeedMps = maxOf(track.fastestSpeedMps, fix.speedMps ?: 0f)
        )

        pointsSinceLastCheckpoint++
        _events.tryEmit(PointCaptured(point))
        val avgKn = if (speedCount > 0) {
            (speedSumMps / speedCount) * 1.94384f
        } else 0f
        _uiState.update {
            it.copy(
                pointCount = it.pointCount + 1,
                distanceNm = cumulativeDistanceNm,
                avgSpeedKn = avgKn,
                recordingPoints = track.trackPoints
            )
        }
    }

    private fun transitionTo(newState: TrackRecorderState) {
        state = newState
        if (newState != TrackRecorderState.FINALIZING) {
            _uiState.update { it.copy(state = newState) }
        }
    }

    private fun startCheckpointJob() {
        checkpointJob?.cancel()
        checkpointJob = scope?.launch {
            while (true) {
                delay(CHECKPOINT_INTERVAL_MS)
                val track = currentTrack ?: continue
                repository.saveCheckpoint(track)
            }
        }
    }

    private fun stopCheckpointJob() {
        checkpointJob?.cancel()
        checkpointJob = null
    }

    private fun startElapsedTimer() {
        elapsedTimerJob?.cancel()
        elapsedTimerJob = scope?.launch {
            var lastTick = System.currentTimeMillis()
            while (true) {
                delay(1_000L)
                val now = System.currentTimeMillis()
                val elapsed = (now - lastTick) / 1000
                lastTick = now
                if (state == TrackRecorderState.RECORDING) {
                    _uiState.update { it.copy(elapsedSeconds = it.elapsedSeconds + elapsed) }
                }
            }
        }
    }

    private fun finalizeTrack() {
        val track = currentTrack ?: return
        transitionTo(TrackRecorderState.FINALIZING)
        stopCheckpointJob()

        val avgMps = if (speedCount > 0) speedSumMps / speedCount else 0f
        val totalElapsedSec = if (recordingStartTimeMs > 0) {
            (System.currentTimeMillis() - recordingStartTimeMs) / 1000
        } else 0L
        val finalized = track.copy(
            endTimeMs = System.currentTimeMillis(),
            pausedDurationSec = totalPausedDurationMs / 1000,
            averageSpeedMps = avgMps,
            distanceNm = cumulativeDistanceNm,
            navigatingDurationSec = totalElapsedSec - totalPausedDurationMs / 1000
        )
        currentTrack = finalized

        scope?.launch {
            repository.deleteCheckpoint(finalized.id)
            repository.save(finalized)
            _events.tryEmit(Stopped)
        }

        transitionTo(TrackRecorderState.IDLE)
        currentTrack = null
        _uiState.update { TrackRecorderUiState() }
    }

    private fun formatTimestamp(epochMs: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        return sdf.format(Date(epochMs))
    }

    private fun cleanup() {
        collectingJob?.cancel()
        checkpointJob?.cancel()
        elapsedTimerJob?.cancel()
        collectingJob = null
        checkpointJob = null
        elapsedTimerJob = null
        scope = null
        policy.reset()
    }

    /** Release all resources. */
    fun dispose() {
        cleanup()
    }
}
