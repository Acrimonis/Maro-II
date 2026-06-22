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
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.track.TrackEvent.*
import ykws.android.maro.spatial.SpatialOperations
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

private const val TAG = "MaroII_Track"

/** Duration (ms) speed must be above threshold before auto-start. */
private const val DEBOUNCE_MS = 10_000L

/** Duration (ms) inside geofence before auto-stop. */
private const val STOP_DEBOUNCE_MS = 15_000L

/** Interval (ms) between checkpoint saves during recording. */
private const val CHECKPOINT_INTERVAL_MS = 30_000L

/** Maximum hull speed for a recreational boat (kn). */
private const val BOAT_MAX_SPEED_KN = 32.0
/** Maximum plausible speed on land (kn) — covers highway driving. */
private const val LAND_MAX_SPEED_KN = 120.0
/** Max acceleration at sea (kn/s) — boats don't teleport. */
private const val MAX_ACCEL_KN_PER_SEC_SEA = 10.0
/** Max acceleration on land (kn/s) — cars can launch harder. */
private const val MAX_ACCEL_KN_PER_SEC_LAND = 30.0
/** Course deviation considered "aligned" with bearing (degrees). */
private const val COURSE_ALIGNED_DEG = 30.0
/** Speed cap multiplier when bearing is aligned with recent course. */
private const val COURSE_ALIGNED_MULTIPLIER = 1.5
/** Speed cap multiplier when bearing is sideways to recent course. */
private const val COURSE_SIDEWAYS_MULTIPLIER = 0.5
/** Consecutive rejections before switching to land mode. */
private const val LAND_DETECTION_REJECTIONS = 5
/** Consecutive accepted fixes ≤32 kn before switching back to sea. */
private const val SEA_RECOVERY_CONSECUTIVE = 10

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
    val currentSpeedKn: Float = 0f,
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
    private val gpsMode: Boolean = true,
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
    /** Tracks the last *recorded* point for implied-speed spike rejection (separate from Haversine accumulators). */
    private var lastValidPointLat: Double? = null
    private var lastValidPointLon: Double? = null
    private var lastValidPointTimeMs: Long = 0L
    private var speedSumMps: Float = 0f
    private var speedCount: Int = 0
    private var debounceStartTime: Long? = null
    private var stopDebounceStartTime: Long? = null
    /** Tracks whether the previous fix was inside the geofence, for exit detection. */
    private var wasInsideGeofence: Boolean = false

    // ── Spike rejection v2 fields ──
    /** Course over ground computed from last 2–3 accepted positions (degrees). */
    private var lastValidCourseDeg: Double? = null
    /** Sliding window of last 3 accepted positions for course calculation. */
    private val courseHistory = ArrayDeque<Pair<Double, Double>>(3)
    /** Speed (kn) of the last accepted fix — used for acceleration gate. */
    private var lastValidSpeedKn: Double = 0.0
    /** Consecutive GPS fixes rejected by any gate. */
    private var consecutiveRejections: Int = 0
    /** Counter of consecutive accepted fixes at sea speed — builds confidence for sea recovery. */
    private var seaConfidenceCounter: Int = 0
    /** True when auto-detection determines we're on land (car mode). */
    private var isOnLand: Boolean = false
    /** Previous fix's lock state — used to detect GPS recovery transitions. */
    private var lastHadLock: Boolean = true

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
        lastValidPointLat = null
        lastValidPointLon = null
        lastValidPointTimeMs = 0L
        lastValidCourseDeg = null
        courseHistory.clear()
        lastValidSpeedKn = 0.0
        consecutiveRejections = 0
        seaConfidenceCounter = 0
        isOnLand = false
        lastHadLock = true
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
        if (!moving && gpsMode) return

        // ── Spike rejection v2: four-gate algorithm (GPS mode only) ────
        if (gpsMode) {
            // Gate 0: GPS recovery — skip all checks when lock transitions false→true
            if (!lastHadLock && fix.hasLock) {
                lastHadLock = true
                captureAcceptedPoint(fix)
                return
            }
            lastHadLock = fix.hasLock

            // Need at least one valid point for gates 1-3
            if (lastValidPointLat != null && lastValidPointLon != null && lastValidPointTimeMs > 0L) {
                val lastValidPos = LatLng(lastValidPointLat!!, lastValidPointLon!!)
                val distM = SpatialOperations.haversine(lastValidPos, fix.position)
                val timeDeltaSec = (fix.timestampEpochMs - lastValidPointTimeMs) / 1000.0
                if (timeDeltaSec > 0.0) {
                    val impliedSpeedKn = (distM / timeDeltaSec) * 1.94384

                    // Gate 1: Context speed cap
                    val baseCap = if (isOnLand) LAND_MAX_SPEED_KN else BOAT_MAX_SPEED_KN

                    // Gate 2: Direction (sea only)
                    val effectiveCap = if (!isOnLand && lastValidCourseDeg != null) {
                        val bearingToFix = SpatialOperations.initialBearing(lastValidPos, fix.position)
                        val delta = angularDistance(bearingToFix, lastValidCourseDeg!!)
                        when {
                            delta <= COURSE_ALIGNED_DEG -> baseCap * COURSE_ALIGNED_MULTIPLIER
                            else -> baseCap * COURSE_SIDEWAYS_MULTIPLIER
                        }
                    } else {
                        baseCap  // land mode or no course history → neutral
                    }

                    if (impliedSpeedKn > effectiveCap) {
                        logRejection("speed cap", impliedSpeedKn, effectiveCap)
                        consecutiveRejections++
                        checkLandDetection(fix)
                        return
                    }

                    // Gate 3: Acceleration
                    val currentSpeedKn = fix.speedMps?.let { it * 1.94384 } ?: impliedSpeedKn
                    val accelKnPerSec = abs(currentSpeedKn - lastValidSpeedKn) / timeDeltaSec
                    val accelLimit = if (isOnLand) MAX_ACCEL_KN_PER_SEC_LAND else MAX_ACCEL_KN_PER_SEC_SEA

                    if (accelKnPerSec > accelLimit) {
                        logRejection("acceleration", accelKnPerSec, accelLimit)
                        consecutiveRejections++
                        return
                    }

                    // Accepted — reset rejection counter
                    consecutiveRejections = 0
                }
            }
        }

        // ── Capture accepted point ──
        captureAcceptedPoint(fix)
    }

    /** Record an accepted fix into the track (stats, UI, course history). */
    private fun captureAcceptedPoint(fix: GpsFix) {
        val track = currentTrack ?: return

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
        val latestSpeedKn = fix.speedMps?.let { mps ->
            speedSumMps += mps
            speedCount++
            val speedKn = mps * 1.94384f
            if (speedKn > _uiState.value.maxSpeedKn) {
                _uiState.update { it.copy(maxSpeedKn = speedKn) }
            }
            speedKn
        } ?: _uiState.value.currentSpeedKn

        // Haversine distance increment
        if (lastPointLat != null && lastPointLon != null) {
            val distM = TrackGeofenceChecker.distanceM(
                lastPointLat!!, lastPointLon!!, point.lat, point.lon
            )
            cumulativeDistanceNm += (distM / 1852.0).toFloat()
        }
        lastPointLat = point.lat
        lastPointLon = point.lon

        // Update valid-point tracker for spike rejection
        lastValidPointLat = point.lat
        lastValidPointLon = point.lon
        lastValidPointTimeMs = fix.timestampEpochMs

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
                currentSpeedKn = latestSpeedKn,
                distanceNm = cumulativeDistanceNm,
                avgSpeedKn = avgKn,
                recordingPoints = currentTrack!!.trackPoints
            )
        }

        // Update spike-rejection trackers
        val acceptedSpeedKn = fix.speedMps?.let { it * 1.94384 } ?: _uiState.value.currentSpeedKn.toDouble()
        lastValidSpeedKn = acceptedSpeedKn
        updateCourseHistory(fix.position)
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

    // ── Spike rejection v2 helpers ──

    /** Angular distance between two bearings in degrees (0–180). */
    private fun angularDistance(a: Double, b: Double): Double {
        val d = abs(a - b) % 360.0
        return if (d > 180.0) 360.0 - d else d
    }

    /** Log a rejection with formatted values. */
    private fun logRejection(gate: String, value: Double, limit: Double) {
        Log.w(TAG, "Spike rejected ($gate): value=${"%.1f".format(value)} limit=${"%.1f".format(limit)}")
    }

    /** Auto-detect land/sea context from rejection patterns. */
    private fun checkLandDetection(fix: GpsFix) {
        if (consecutiveRejections >= LAND_DETECTION_REJECTIONS) {
            val gpsSpeedKn = fix.speedMps?.let { it * 1.94384 } ?: 0.0
            if (gpsSpeedKn > BOAT_MAX_SPEED_KN) {
                isOnLand = true
                consecutiveRejections = 0
                lastValidCourseDeg = null  // reset course — direction check now off
            }
        }
        if (isOnLand && consecutiveRejections == 0) {
            val gpsSpeedKn = fix.speedMps?.let { it * 1.94384 } ?: 0.0
            if (gpsSpeedKn <= BOAT_MAX_SPEED_KN) {
                seaConfidenceCounter++
                if (seaConfidenceCounter >= SEA_RECOVERY_CONSECUTIVE) {
                    isOnLand = false
                    seaConfidenceCounter = 0
                }
            } else {
                seaConfidenceCounter = 0
            }
        }
    }

    /** Update sliding course history from an accepted position. */
    private fun updateCourseHistory(pos: LatLng) {
        if (isOnLand) return  // don't track course on land
        courseHistory.addLast(pos.latitude to pos.longitude)
        if (courseHistory.size > 3) courseHistory.removeFirst()
        if (courseHistory.size >= 2) {
            val first = courseHistory.first()
            val last = courseHistory.last()
            lastValidCourseDeg = SpatialOperations.initialBearing(
                LatLng(first.first, first.second),
                LatLng(last.first, last.second)
            )
        }
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
