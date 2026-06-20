package ykws.android.maro.data.track

import android.util.Log

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

private const val TAG = "MaroII_Track"

/** Duration (ms) speed must be above threshold before auto-start. */
private const val DEBOUNCE_MS = 10_000L

/** Duration (ms) inside geofence before auto-stop. */
private const val STOP_DEBOUNCE_MS = 15_000L

/** Interval (ms) between checkpoint saves during recording. */
private const val CHECKPOINT_INTERVAL_MS = 30_000L

/** The track recorder state machine states. */
enum class TrackRecorderState { OFF, ON }

/**
 * UI-friendly representation of the recorder's current status.
 */
data class TrackRecorderUiState(
    val state: TrackRecorderState = TrackRecorderState.OFF,
    val currentTrackId: String? = null,
    val currentTrackName: String? = null,
    val currentTrackComment: String? = null,
    val elapsedSeconds: Long = 0L,
    val pointCount: Int = 0,
    val maxSpeedKn: Float = 0f,
    val avgSpeedKn: Float = 0f,
    val distanceNm: Float = 0f,
    val recordingPoints: List<TrackPoint> = emptyList(),
    val isMoving: Boolean = false
)

/**
 * Coroutine-based track recording state machine.
 *
 * Two states: OFF (not tracking) and ON (tracking). Within ON, point capture
 * is gated by [AdaptiveGpsPolicy.isStill] — when the boat is stationary,
 * GPS fixes are received but not saved to the track.
 *
 * Auto-start on geofence exit (Port Salis) with 10s movement debounce.
 * Auto-stop on geofence entrance with 15s debounce.
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
    private var state: TrackRecorderState = TrackRecorderState.OFF
    private var currentTrack: Track? = null
    private var pointsSinceLastCheckpoint = 0
    private var recordingStartTimeMs = 0L
    private var cumulativeDistanceNm = 0f
    private var lastPointLat: Double? = null
    private var lastPointLon: Double? = null
    private var speedSumMps: Float = 0f
    private var speedCount: Int = 0
    private var debounceStartTime: Long? = null
    private var stopDebounceStartTime: Long? = null
    /** Tracks whether the previous fix was inside the geofence, for exit detection. */
    private var wasInsideGeofence: Boolean = false

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

    /** Stop the recorder — finalizes the current track if recording. */
    fun stop() {
        if (state == TrackRecorderState.ON) {
            finalizeTrack()
        } else {
            cleanup()
        }
    }

    /** Update the current track's name and/or comment in memory and checkpoint. */
    fun updateCurrentTrackMeta(name: String? = null, comment: String? = null) {
        val track = currentTrack ?: return
        currentTrack = track.copy(
            name = name ?: track.name,
            comment = comment ?: track.comment
        )
        _uiState.update {
            it.copy(
                currentTrackName = currentTrack?.name,
                currentTrackComment = currentTrack?.comment
            )
        }
        scope?.launch {
            currentTrack?.let { repository.saveCheckpoint(it) }
        }
        Log.d(TAG, "updateCurrentTrackMeta: name=${currentTrack?.name} comment=${currentTrack?.comment}")
    }

    /** Manually start recording (bypasses auto-detection). */
    fun startManual() {
        if (state != TrackRecorderState.OFF) {
            Log.d(TAG, "startManual: ignored — state=$state (not OFF)")
            return
        }
        policy.reset()
        Log.d(TAG, "startManual: → beginRecording")
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
            TrackRecorderState.OFF -> {
                // Auto-start only on geofence exit (inside→outside transition).
                // Manual toggle is the other start trigger (via startManual()).
                if (geofenceEnabled && wasInsideGeofence && !insideGeofence) {
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
                wasInsideGeofence = insideGeofence
            }

            TrackRecorderState.ON -> {
                Log.v(TAG, "processFix(ON): speed=${fix.speedMps} pos=(${fix.position.latitude},${fix.position.longitude})")
                addPoint(fix)

                // Auto-stop: inside geofence with 15s debounce
                if (geofenceEnabled && insideGeofence) {
                    val now = System.currentTimeMillis()
                    if (stopDebounceStartTime == null) {
                        stopDebounceStartTime = now
                    } else if (now - stopDebounceStartTime!! >= STOP_DEBOUNCE_MS) {
                        stopDebounceStartTime = null
                        Log.d(TAG, "processFix(ON): inside geofence for 15s → auto-stop")
                        finalizeTrack()
                    }
                } else {
                    stopDebounceStartTime = null
                }
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
        cumulativeDistanceNm = 0f
        speedSumMps = 0f
        speedCount = 0
        lastPointLat = null
        lastPointLon = null
        pointsSinceLastCheckpoint = 0
        stopDebounceStartTime = null
        transitionTo(TrackRecorderState.ON)
        _uiState.update {
            TrackRecorderUiState(
                state = TrackRecorderState.ON,
                currentTrackId = id,
                currentTrackName = name,
                isMoving = false
            )
        }
        Log.d(TAG, "beginRecording: id=$id name=$name isManual=$isManual startFix=${startFix != null}")
        _events.tryEmit(Started)
        startCheckpointJob()

        startFix?.let { addPoint(it) }
    }

    private fun addPoint(fix: GpsFix) {
        val track = currentTrack ?: return

        // Feed the fix to the policy and let isStill() determine movement.
        // No separate speed threshold — the policy handles it internally.
        policy.onFix(
            nowMs = System.currentTimeMillis(),
            pos = fix.position,
            windowMs = adaptiveWindowMs,
            thresholdM = adaptiveThresholdM
        )
        val moving = !policy.isStill()
        _uiState.update { it.copy(isMoving = moving) }

        val speedKn = fix.speedMps?.let { it * 1.94384f }
        Log.d(TAG, "addPoint: speed=${speedKn} kn isStill=${policy.isStill()} moving=$moving state=$state")

        // Skip point capture when stationary (still tracking, just not recording points)
        if (!moving) return

        val now = System.currentTimeMillis()
        val timeOffsetSec = ((now - recordingStartTimeMs) / 1000).toInt()
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
        _uiState.update { it.copy(state = newState) }
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
                if (state == TrackRecorderState.ON) {
                    _uiState.update { it.copy(elapsedSeconds = it.elapsedSeconds + elapsed) }
                }
            }
        }
    }

    private fun finalizeTrack() {
        val track = currentTrack ?: return
        stopCheckpointJob()
        stopDebounceStartTime = null

        val avgMps = if (speedCount > 0) speedSumMps / speedCount else 0f
        val totalElapsedSec = if (recordingStartTimeMs > 0) {
            (System.currentTimeMillis() - recordingStartTimeMs) / 1000
        } else 0L
        val finalized = track.copy(
            endTimeMs = System.currentTimeMillis(),
            pausedDurationSec = 0,
            averageSpeedMps = avgMps,
            distanceNm = cumulativeDistanceNm,
            navigatingDurationSec = totalElapsedSec
        )
        currentTrack = finalized

        scope?.launch {
            repository.deleteCheckpoint(finalized.id)
            repository.save(finalized)
            _events.tryEmit(Stopped)
        }

        transitionTo(TrackRecorderState.OFF)
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
        wasInsideGeofence = false
        policy.reset()
    }

    /** Release all resources. */
    fun dispose() {
        cleanup()
    }
}
