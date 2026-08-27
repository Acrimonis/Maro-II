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
import kotlinx.coroutines.runBlocking
import ykws.android.maro.BuildConfig
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.location.GpsLocationSource
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.MarkerOrigin
import ykws.android.maro.data.model.markers.UserMarker
import ykws.android.maro.data.track.TrackEvent.*
import ykws.android.maro.spatial.SpatialOperations
import ykws.android.maro.spatial.WhereAmIMatch
import ykws.android.maro.spatial.WhereAmIResult
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
/** Timeout (ms) after last accepted fix before spike rejection resets — prevents lock-in on sharp turns and silent GPS recovery. */
private const val STALE_FIX_TIMEOUT_MS = 10_000L

/** Maximum hull speed for a recreational boat (kn). Gate 1 sea-mode cap. */
private val boatMaxSpeedKn: Double get() = BuildConfig.TRACKING_BOAT_MAX_SPEED_KN
/** Maximum plausible speed on land (kn). Gate 1 land-mode cap. */
private val landMaxSpeedKn: Double get() = BuildConfig.TRACKING_LAND_MAX_SPEED_KN
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
private const val LAND_DETECTION_REJECTIONS = 2
/** Consecutive accepted fixes ≤32 kn before switching back to sea. */
private const val SEA_RECOVERY_CONSECUTIVE = 10
/** Window (ms) to skip identical positions when moving — dedup gate (Fix D). */
private const val MOVING_DEDUP_WINDOW_MS = 500L
/** Window (ms) to skip identical positions when stationary — dedup gate (Fix D). */
private const val STATIONARY_DEDUP_WINDOW_MS = 5000L
/** GPS speed below this (m/s, ~2 kn) triggers low-speed tier in stale timeout (Fix A). */
private const val LOW_SPEED_MPS = 1.0
/** Multiplier × BOAT_MAX_SPEED_KN when GPS speed < LOW_SPEED_MPS (Fix A). */
private const val STALE_CAP_LOW_SPEED = 1.5
/** Multiplier × BOAT_MAX_SPEED_KN when GPS speed ≥ LOW_SPEED_MPS (Fix A). */
private const val STALE_CAP_NORMAL = 3.0
/** Max plausible drift distance (m) while stationary (Fix C). */
private const val MAX_STATIONARY_DRIFT_M = 150

/** GPS speed (m/s, ~10 kn) above which sideways course changes are trusted as real navigation. */
private const val SIDEWAYS_SPEED_THRESHOLD_MPS = 5.0f
/** Multiplier applied to accuracy threshold when GPS speed indicates movement (>0.5 kn). */
private const val ACCURACY_MOVING_MULTIPLIER = 1.7f
/** GPS speed (m/s, ~0.5 kn) below which the stationary accuracy threshold applies. */
private const val MIN_MOVEMENT_SPEED_FOR_ACCURACY_MPS = (1.0 / 1.94384).toFloat()

/** Interval (ms) between title poll ticks. */
private const val TITLE_POLL_INTERVAL_MS = 180_000L

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
    val isMoving: Boolean = false,
    val idleDurationSec: Long = 0L,
    val infoError: String? = null
)

/**
 * Coroutine-based track recording state machine.
 *
 * Two states: OFF (not tracking) and ON (tracking). Within ON, point capture
 * is gated by [isStopped] — when the boat is stationary,
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
 * @param isStopped               True when the boat is stationary (from NavigationVM adaptive policy). Default false.
 * @param simplifyEnabled         When true, simplify track points on finalize (Douglas-Peucker + speed-aware).
 * @param simplifyEpsilonM        Douglas-Peucker tolerance (metres). Lower = more points kept.
 * @param simplifySpeedDeltaKn    Speed deviation threshold (knots) to re-insert a point during simplification.
 * @param dispatcher              Coroutine dispatcher for the state machine loop.
 * @param whereAmI                Synchronous whereAmI lookup — nullable; when null the feature is dormant.
 * @param markerChangeNotifier    Flow that emits when external markers change — nullable.
 * @param gapDistanceThresholdM   Distance threshold (metres) to insert a GAP marker on resume.
 * @param gapTimeThresholdSec     Time threshold (seconds) to insert a GAP marker on resume.
 */
class TrackRecorder(
    private val repository: TrackRepository,
    private val geofenceOriginLat: Double = 43.55,
    private val geofenceOriginLon: Double = 7.00,
    private val geofenceRadiusM: Double = 500.0,
    private val geofenceEnabled: Boolean = true,
    private val gpsMode: Boolean = true,
    private val maxRecordingAccuracyM: Float = 30f,
    val isStopped: StateFlow<Boolean> = MutableStateFlow(false),
    private val simplifyEnabled: Boolean = true,
    private val simplifyEpsilonM: Double = 3.0,
    private val simplifySpeedDeltaKn: Double = 3.0,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val idleThresholdSec: Long = 60,
    private val idleThresholdCallback: IdleThresholdCallback? = null,
    private val whereAmI: ((LatLng) -> WhereAmIResult)? = null,
    private val markerChangeNotifier: Flow<Unit>? = null,
    private val gapDistanceThresholdM: Double = 200.0,
    private val gapTimeThresholdSec: Long = 120L
) {
    private val _uiState = MutableStateFlow(TrackRecorderUiState())
    val uiState: StateFlow<TrackRecorderUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<TrackEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<TrackEvent> = _events.asSharedFlow()

    /** Incremental point stream — emits each captured point for polyline appending. */
    private val _newPoint = MutableSharedFlow<TrackPoint>(extraBufferCapacity = 64)
    val newPoint: SharedFlow<TrackPoint> = _newPoint.asSharedFlow()

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
    /** Counter of consecutive accepted fixes at sea speed while on land — builds confidence for sea recovery. */
    private var landModeAcceptCounter: Int = 0
    /** True when auto-detection determines we're on land (car mode). */
    private var isOnLand: Boolean = false
    /** Previous fix's lock state — used to detect GPS recovery transitions. */
    private var lastHadLock: Boolean = true
    /** Timestamp of the last accepted fix (ms) — used for stale-fix timeout reset. */
    private var lastAcceptedTimeMs: Long = 0L
    /** Last assigned timeOffsetMs — ensures monotonic uniqueness even when fixes share the same ms. */
    private var lastTimeOffsetMs: Long = 0L

    // ── Still-spike fix fields ──
    /** Last position confirmed by GPS speed ≥ 2kn — anchor for drift detection. */
    private var lastGenuineLat: Double? = null
    private var lastGenuineLon: Double? = null
    /** Sliding window of last 5 accepted bearings (degrees) for trajectory sanity. */
    private val bearingWindow = ArrayDeque<Double>(5)

    // ── Fix D: dedup fields ──
    /** Latitude of last accepted point — for same-position dedup (Fix D). */
    private var lastAcceptedLat: Double? = null
    /** Longitude of last accepted point — for same-position dedup (Fix D). */
    private var lastAcceptedLon: Double? = null
    /** Wall-clock time (ms) of last accepted point — for dedup window (Fix D). */
    private var lastAcceptedTimeWallMs: Long = 0L

    // ── Idle duration accumulator ──
    private var idleDurationSec: Long = 0L
    private var idleStartMs: Long = 0L
    private var wasStopped: Boolean = false

    // ── BoatMarker idle-session tracking ──
    private var activeSession: IdleSessionContext? = null
    private var idleTimerJob: Job? = null

    // ── Resume gap detection ──
    private var isResuming: Boolean = false
    private var checkpointFileDeleted: Boolean = false
    /** Guards against checkpoint re-writes racing the finalize checkpoint delete + save. */
    private var isFinalizing = false
    /** Inter-session gap in seconds when resuming a finalized track (D10). */
    private var resumeGapDurationSec: Long = 0L

    private var scope: CoroutineScope? = null
    private var collectingJob: Job? = null
    private var checkpointJob: Job? = null
    private var elapsedTimerJob: Job? = null
    private var titlePollJob: Job? = null
    private var markerObserverJob: Job? = null

    /** Called by MapScreen to set the 🕐 auto-marker ID on the active idle session. */
    fun setActiveSessionAutoMarkerId(id: String) {
        activeSession?.autoMarkerId = id
    }

    /** Called by MapScreen when an auto-marker is confirmed — stores the ID in the track's BoatMarker. */
    fun setBoatMarkerAutoMarkerId(id: String) {
        val session = activeSession ?: return
        val idx = session.boatMarkerIndex ?: return
        val track = currentTrack ?: return
        if (idx in track.boatMarkers.indices) {
            val bm = track.boatMarkers[idx]
            val updated = track.boatMarkers.toMutableList().also { it[idx] = bm.copy(autoMarkerId = id) }
            currentTrack = track.copy(boatMarkers = updated)
            scope?.launch { currentTrack?.let { repository.saveCheckpoint(it) } }
        }
    }

    /**
     * Manually append a BoatMarker to the current track.
     * Used by the boat-marker button in MapScreen (MANUAL trigger).
     */
    fun addManualBoatMarker(snapshots: List<MarkerSnapshot>) {
        val track = currentTrack ?: return
        if (snapshots.isEmpty()) return
        val now = System.currentTimeMillis()
        val lastLat = lastPointLat ?: return
        val lastLon = lastPointLon ?: return
        val seqIdx = (track.boatMarkers.lastOrNull()?.sequenceIndex ?: -1) + 1
        val bm = BoatMarker(
            trigger = BoatMarkerTrigger.MANUAL,
            startTimeMs = now,
            markers = snapshots,
            boatLat = lastLat,
            boatLon = lastLon,
            sequenceIndex = seqIdx
        )
        currentTrack = track.copy(boatMarkers = track.boatMarkers + bm)
        scope?.launch {
            currentTrack?.let { repository.saveCheckpoint(it) }
        }
        recomputeDescription()
        pollTitle()
    }

    /**
     * Start collecting [TrackSample] events and driving the state machine.
     * Call from viewModelScope or similar.
     */
    fun start(sampleFlow: Flow<TrackSample>) {
        collectingJob?.cancel()
        scope = CoroutineScope(dispatcher + SupervisorJob())
        collectingJob = scope?.launch {
            sampleFlow.collect { sample -> processSample(sample) }
        }
        startElapsedTimer()
    }

    /**
     * Resume recording from a checkpointed track (crash/force-stop recovery).
     *
     * Restores the recorder state from a pre-existing [Track] (loaded from checkpoint),
     * then starts processing [sampleFlow]. On the first new point, if the gap between
     * the last checkpointed point and the first new point exceeds [gapDistanceThresholdM]
     * or [gapTimeThresholdSec], a GAP marker point is inserted.
     *
     * @param track        The checkpointed track to resume (from repository).
     * @param sampleFlow   The sample flow to start processing.
     */
    fun resume(track: Track, sampleFlow: Flow<TrackSample>, fromCheckpoint: Boolean = true) {
        if (state != TrackRecorderState.OFF) {
            Log.w(TAG, "resume: ignored — state=$state (not OFF)")
            return
        }

        val now = System.currentTimeMillis()
        val points = track.trackPoints
        val lastPoint = points.lastOrNull()

        // Compute inter-session gap BEFORE clearing endTimeMs (D10)
        resumeGapDurationSec = if (!fromCheckpoint && track.endTimeMs != null) {
            (now - track.endTimeMs) / 1000
        } else 0L

        // If resuming a finalized track, clear endTimeMs (it's being recorded again)
        val resumedTrack = if (!fromCheckpoint && track.endTimeMs != null) {
            track.copy(endTimeMs = null)
        } else {
            track
        }

        // Restore track state
        currentTrack = resumedTrack
        recordingStartTimeMs = resumedTrack.startTimeMs
        cumulativeDistanceNm = track.distanceNm
        speedSumMps = track.averageSpeedMps * track.trackPoints.size.coerceAtLeast(1)
        speedCount = track.trackPoints.size
        lastPointLat = lastPoint?.lat
        lastPointLon = lastPoint?.lon
        lastValidPointLat = lastPoint?.lat
        lastValidPointLon = lastPoint?.lon
        lastValidPointTimeMs = if (lastPoint != null) track.startTimeMs + lastPoint.timeOffsetMs else 0L
        lastValidCourseDeg = null
        courseHistory.clear()
        lastValidSpeedKn = 0.0
        consecutiveRejections = 0
        landModeAcceptCounter = 0
        isOnLand = false
        lastHadLock = true
        lastAcceptedTimeMs = System.currentTimeMillis()
        lastTimeOffsetMs = lastPoint?.timeOffsetMs ?: 0L
        // Fix D: reset dedup fields
        lastAcceptedLat = null
        lastAcceptedLon = null
        lastAcceptedTimeWallMs = 0L
        // Still-spike fix reset
        lastGenuineLat = null
        lastGenuineLon = null
        bearingWindow.clear()
        pointsSinceLastCheckpoint = 0
        stopDebounceStartTime = null
        idleDurationSec = track.idleDurationSec
        idleStartMs = 0L
        wasStopped = false
        cancelIdleTimer()
        activeSession = null
        isResuming = lastPoint != null
        checkpointFileDeleted = !fromCheckpoint

        transitionTo(TrackRecorderState.ON)
        _uiState.update {
            TrackRecorderUiState(
                state = TrackRecorderState.ON,
                currentTrackId = resumedTrack.id,
                currentTrackName = resumedTrack.name,
                currentTrackComment = resumedTrack.comment,
                isMoving = false,
                pointCount = points.size,
                distanceNm = resumedTrack.distanceNm,
                avgSpeedKn = resumedTrack.averageSpeedMps * 1.94384f,
                maxSpeedKn = resumedTrack.fastestSpeedMps * 1.94384f,
                elapsedSeconds = (now - resumedTrack.startTimeMs - resumeGapDurationSec) / 1000
            )
        }

        Log.d(TAG, "resume: id=${track.id} name=${track.name} existingPoints=${points.size} isResuming=$isResuming")

        collectingJob?.cancel()
        scope = CoroutineScope(dispatcher + SupervisorJob())
        // Emit Resumed event with existing points so MapScreen restores the polyline
        if (points.isNotEmpty()) {
            _events.tryEmit(Resumed(points))
        }
        collectingJob = scope?.launch {
            sampleFlow.collect { sample -> processSample(sample) }
        }
        startElapsedTimer()
        startCheckpointJob()
        startPolling()

        _events.tryEmit(Started)
    }

    /** Stop the recorder — finalizes the current track if recording. */
    fun stop() {
        if (state == TrackRecorderState.ON) {
            finalizeTrack()
        } else {
            cleanup()
        }
    }

    /** Snapshot of the current track's points — used to restore the live polyline after UI re-attach. */
    fun snapshotPoints(): List<TrackPoint> = currentTrack?.trackPoints.orEmpty()

    /**
     * Discard the in-progress recording without finalizing.
     * Deletes the current track file and its checkpoint, then returns to OFF.
     */
    fun discard() {
        if (state == TrackRecorderState.ON) {
            val trackId = currentTrack?.id
            stopCheckpointJob()
            stopPolling()
            cancelIdleTimer()
            stopDebounceStartTime = null
            scope?.launch {
                if (trackId != null) {
                    repository.delete(trackId)
                    repository.deleteCheckpoint(trackId)
                }
                _events.tryEmit(Stopped)
            }
            transitionTo(TrackRecorderState.OFF)
            currentTrack = null
            _uiState.update { TrackRecorderUiState() }
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
        if (!isFinalizing) {
            scope?.launch {
                currentTrack?.let { repository.saveCheckpoint(it) }
            }
        }
        Log.d(TAG, "updateCurrentTrackMeta: name=${currentTrack?.name} comment=${currentTrack?.comment}")
    }

    /** Clear the track info error — dismisses the ErrorOverlay. */
    fun clearInfoError() {
        _uiState.update { it.copy(infoError = null) }
    }

    /** Manually start recording (bypasses auto-detection). */
    fun startManual() {
        if (state != TrackRecorderState.OFF) {
            Log.d(TAG, "startManual: ignored — state=$state (not OFF)")
            return
        }
        Log.d(TAG, "startManual: → beginRecording")
        beginRecording(isManual = true)
    }

    private fun processSample(sample: TrackSample) {
        val insideGeofence = geofenceEnabled && TrackGeofenceChecker.isInsideGeofence(
            posLat = sample.position.latitude,
            posLon = sample.position.longitude,
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
                        beginRecording(isManual = false, startSample = sample)
                    }
                } else {
                    debounceStartTime = null
                }
                wasInsideGeofence = insideGeofence
            }

            TrackRecorderState.ON -> {
                Log.v(TAG, "processSample(ON): speed=${sample.speedMps} pos=(${sample.position.latitude},${sample.position.longitude})")
                addPoint(sample)

                // Auto-stop: inside geofence with 15s debounce
                if (geofenceEnabled && insideGeofence) {
                    val now = System.currentTimeMillis()
                    if (stopDebounceStartTime == null) {
                        stopDebounceStartTime = now
                    } else if (now - stopDebounceStartTime!! >= STOP_DEBOUNCE_MS) {
                        stopDebounceStartTime = null
                        Log.d(TAG, "processSample(ON): inside geofence for 15s → auto-stop")
                        finalizeTrack()
                    }
                } else {
                    stopDebounceStartTime = null
                }
            }
        }
    }

    private fun beginRecording(isManual: Boolean, startSample: TrackSample? = null) {
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
        landModeAcceptCounter = 0
        isOnLand = false
        lastHadLock = true
        lastAcceptedTimeMs = System.currentTimeMillis()
        lastTimeOffsetMs = 0L
        // Fix D: reset dedup fields
        lastAcceptedLat = null
        lastAcceptedLon = null
        lastAcceptedTimeWallMs = 0L
        // Still-spike fix reset
        lastGenuineLat = null
        lastGenuineLon = null
        bearingWindow.clear()
        pointsSinceLastCheckpoint = 0
        stopDebounceStartTime = null
        idleDurationSec = 0L
        idleStartMs = 0L
        wasStopped = false
        cancelIdleTimer()
        activeSession = null
        isResuming = false
        checkpointFileDeleted = false
        transitionTo(TrackRecorderState.ON)
        _uiState.update {
            TrackRecorderUiState(
                state = TrackRecorderState.ON,
                currentTrackId = id,
                currentTrackName = name,
                isMoving = false
            )
        }
        Log.d(TAG, "beginRecording: id=$id name=$name isManual=$isManual startSample=${startSample != null}")
        _events.tryEmit(Started)
        startCheckpointJob()
        startPolling()

        startSample?.let { addPoint(it) }
    }

    /**
     * Detect gap between the last checkpointed point and the incoming sample.
     * Inserts a GAP marker if distance > [gapDistanceThresholdM] or time gap > [gapTimeThresholdSec].
     */
    private fun detectAndInsertGap(sample: TrackSample, force: Boolean = false) {
        val track = currentTrack ?: return
        val lastPt = track.trackPoints.lastOrNull() ?: return
        if (lastPt.type == PointType.GAP) return  // already has a gap marker

        val lastTimeMs = recordingStartTimeMs + lastPt.timeOffsetMs
        val dtSec = (sample.timestampEpochMs - lastTimeMs) / 1000.0
        val distM = SpatialOperations.haversine(
            LatLng(lastPt.lat, lastPt.lon),
            sample.position
        )

        if (force || distM > gapDistanceThresholdM || dtSec > gapTimeThresholdSec) {
            Log.d(TAG, "GAP detected: dist=${"%.0f".format(distM)}m dt=${"%.0f".format(dtSec)}s — inserting GAP marker")
            val gapPoint = TrackPoint(
                lat = lastPt.lat,
                lon = lastPt.lon,
                speedMps = null,
                bearingDeg = null,
                timeOffsetSec = lastPt.timeOffsetSec,
                timeOffsetMs = lastPt.timeOffsetMs + 1,
                type = PointType.GAP
            )
            currentTrack = track.copy(
                trackPoints = track.trackPoints + gapPoint
            )
            pointsSinceLastCheckpoint++
            _events.tryEmit(PointCaptured(gapPoint))
            _newPoint.tryEmit(gapPoint)
            _uiState.update { it.copy(pointCount = it.pointCount + 1) }
        }
    }

    private fun addPoint(sample: TrackSample) {
        val track = currentTrack ?: return

        // ── Resume gap detection: first point after resume ──
        if (isResuming) {
            isResuming = false
            detectAndInsertGap(sample, force = true)
            // Delete checkpoint after first new point is captured (resume is confirmed)
            if (!checkpointFileDeleted) {
                checkpointFileDeleted = true
                scope?.launch { repository.deleteCheckpoint(track.id) }
            }
        }

        // ── Fix D: dedup identical positions — dynamic window ──
        val nowWall = System.currentTimeMillis()
        val dedupWindowMs = if (sample.speedMps != null && sample.speedMps < GpsLocationSource.MIN_SPEED_MPS) {
            STATIONARY_DEDUP_WINDOW_MS
        } else {
            MOVING_DEDUP_WINDOW_MS
        }
        if (lastAcceptedLat != null && lastAcceptedLon != null &&
            sample.position.latitude == lastAcceptedLat &&
            sample.position.longitude == lastAcceptedLon &&
            nowWall - lastAcceptedTimeWallMs < dedupWindowMs) {
            Log.v(TAG, "Dedup: skipped identical position within ${nowWall - lastAcceptedTimeWallMs}ms")
            return
        }
        lastAcceptedLat = sample.position.latitude
        lastAcceptedLon = sample.position.longitude
        lastAcceptedTimeWallMs = nowWall

        val stopped = isStopped.value
        _uiState.update { it.copy(isMoving = !stopped) }

        val speedKn = sample.speedMps?.let { it * 1.94384f }
        Log.d(TAG, "addPoint: speed=${speedKn} kn isStopped=$stopped state=$state")

        // ── Idle duration accumulation + BoatMarker session lifecycle ──
        val now = System.currentTimeMillis()
        if (stopped && !wasStopped) {
            // Transition: moving → idle
            idleStartMs = now
            val lat = sample.position.latitude
            val lon = sample.position.longitude
            activeSession = IdleSessionContext(startTimeMs = now, entryLat = lat, entryLon = lon)
            _events.tryEmit(IdlePeriodStarted(entryLat = lat, entryLon = lon, startTimeMs = now))
            startIdleTimer(lat, lon, now)
        } else if (!stopped && wasStopped) {
            // Transition: idle → moving
            cancelIdleTimer()
            val session = activeSession
            if (idleStartMs > 0) {
                val delta = (now - idleStartMs) / 1000
                idleDurationSec += delta
                idleStartMs = 0L
                _uiState.update { it.copy(idleDurationSec = idleDurationSec) }
                if (session != null) {
                    closeOpenBoatMarker(now)
                    recomputeDescription()
                    pollTitle()
                    if (session.drawerAutoOpened) {
                        _events.tryEmit(DrawerAutoCloseRequested)
                    }
                    _events.tryEmit(IdlePeriodCompleted(
                        entryLat = session.entryLat,
                        entryLon = session.entryLon,
                        startTimeMs = session.startTimeMs,
                        endTimeMs = now,
                        durationSec = delta,
                        autoMarkerId = session.autoMarkerId
                    ))
                }
            }
            activeSession = null
        }
        wasStopped = stopped

        if (stopped && idleStartMs > 0) {
            _uiState.update { it.copy(idleDurationSec = idleDurationSec + (now - idleStartMs) / 1000) }
        }

        // Skip point capture when stationary (same gate for GPS and demo mode)
        if (stopped) return

        // Skip points without speed data — transient GPS state at recording start / post-pause.
        // These create gaps in GPX export (missing <speed> elements) with no tracking value.
        if (sample.speedMps == null) return

        // ── Accuracy gate: speed-aware threshold ──
        // When moving (GPS speed ≥ ~0.5 kn), the speed sensor validates the position —
        // relax the accuracy threshold. When stationary, keep tight guard against drift.
        val effectiveMaxAccuracy = if (sample.speedMps != null && sample.speedMps >= MIN_MOVEMENT_SPEED_FOR_ACCURACY_MPS) {
            maxRecordingAccuracyM * ACCURACY_MOVING_MULTIPLIER
        } else {
            maxRecordingAccuracyM
        }
        if (sample.accuracyM != null && sample.accuracyM > effectiveMaxAccuracy) {
            Log.v(TAG, "Accuracy gate: rejected fix with accuracy=${"%.1f".format(sample.accuracyM)}m > ${effectiveMaxAccuracy}m")
            return
        }

        // ── Spike rejection v2: four-gate algorithm (GPS mode only) ────
        if (gpsMode) {
            // ── Still-spike gate: contradiction check (speed=0 + position jump) ──
            if (lastGenuineLat != null && lastGenuineLon != null) {
                val gpsSpeedKn = sample.speedMps?.let { it * 1.94384 } ?: 0.0
                if (gpsSpeedKn < 2.0) {
                    val distFromGenuine = SpatialOperations.haversine(
                        LatLng(lastGenuineLat!!, lastGenuineLon!!), sample.position
                    )
                    if (distFromGenuine > MAX_STATIONARY_DRIFT_M) {
                        logRejection("still-spike", distFromGenuine, MAX_STATIONARY_DRIFT_M.toDouble())
                        return
                    }
                    // ── Trajectory consistency: bearing sanity (sea only) ──
                    if (!isOnLand && bearingWindow.size >= 3) {
                        val bearingFromGenuine = SpatialOperations.initialBearing(
                            LatLng(lastGenuineLat!!, lastGenuineLon!!), sample.position
                        )
                        val medianBearing = bearingWindow.sorted().let { it[it.size / 2] }
                        if (angularDistance(bearingFromGenuine, medianBearing) > 90.0) {
                            logRejection("bearing sanity", angularDistance(bearingFromGenuine, medianBearing), 90.0)
                            return
                        }
                    }
                }
            }
            // Timeout reset: if no sample accepted for >STALE_FIX_TIMEOUT_MS, accept with relaxed check.
            if (!stopped && lastAcceptedTimeMs > 0L && System.currentTimeMillis() - lastAcceptedTimeMs > STALE_FIX_TIMEOUT_MS) {
                Log.w(TAG, "Spike reset: ${(System.currentTimeMillis() - lastAcceptedTimeMs) / 1000}s since last accepted sample")
                lastHadLock = sample.hasLock
                // Fix C: stationary drift check — reject implausible position jumps while stationary
                if (lastValidPointLat != null && lastValidPointLon != null && lastValidPointTimeMs > 0L) {
                    val refPos = LatLng(lastValidPointLat!!, lastValidPointLon!!)
                    val distM = SpatialOperations.haversine(refPos, sample.position)
                    val gpsSpeedKn = sample.speedMps?.let { it * 1.94384 } ?: 0.0
                    if (gpsSpeedKn < 2.0 && distM > MAX_STATIONARY_DRIFT_M) {
                        logRejection("stale drift", distM, MAX_STATIONARY_DRIFT_M.toDouble())
                        return
                    }
                    val dtSec = (sample.timestampEpochMs - lastValidPointTimeMs) / 1000.0
                    if (dtSec > 0.0) {
                        val impliedKn = (distM / dtSec) * 1.94384
                        val isLowSpeed = (sample.speedMps?.toDouble() ?: 0.0) < LOW_SPEED_MPS
                        val staleCap = boatMaxSpeedKn * if (isLowSpeed) STALE_CAP_LOW_SPEED else STALE_CAP_NORMAL
                        if (impliedKn > staleCap) {
                            Log.w(TAG, "Spike reset REJECTED (${if (isLowSpeed) "low-speed" else "normal"}): implied=${"%.1f".format(impliedKn)}kn cap=${"%.1f".format(staleCap)}kn dist=${"%.0f".format(distM)}m")
                            return
                        }
                    }
                }
                consecutiveRejections = 0
                captureAcceptedPoint(sample)
                // Clear course history — don't trust spike position for direction
                lastValidCourseDeg = null
                courseHistory.clear()
                return
            }

            // Gate 0: GPS recovery — skip all checks when lock transitions false→true
            if (!lastHadLock && sample.hasLock) {
                lastHadLock = true
                consecutiveRejections = 0
                captureAcceptedPoint(sample)
                return
            }
            lastHadLock = sample.hasLock

            // Fix B: Gate 0.5 — GPS-reported speed cap (sea mode only)
            if (!isOnLand) {
                val gpsSpeedKn = sample.speedMps?.let { it * 1.94384 } ?: 0.0
                val gpsSpeedCap = boatMaxSpeedKn * 1.25  // 40 kn
                if (gpsSpeedKn > gpsSpeedCap) {
                    logRejection("gps speed", gpsSpeedKn, gpsSpeedCap)
                    consecutiveRejections++
                    checkLandDetection(sample)
                    return
                }
            }

            // Need at least one valid point for gates 1-3
            if (lastValidPointLat != null && lastValidPointLon != null && lastValidPointTimeMs > 0L) {
                val lastValidPos = LatLng(lastValidPointLat!!, lastValidPointLon!!)
                val distM = SpatialOperations.haversine(lastValidPos, sample.position)
                // Fix C: absolute distance cap when stationary — anchored to last genuine position
                val gpsSpeedKn = sample.speedMps?.let { it * 1.94384 } ?: 0.0
                if (lastGenuineLat != null && lastGenuineLon != null && gpsSpeedKn < 2.0) {
                    val distFromGenuine = SpatialOperations.haversine(LatLng(lastGenuineLat!!, lastGenuineLon!!), sample.position)
                    if (distFromGenuine > MAX_STATIONARY_DRIFT_M) {
                        logRejection("stationary drift", distFromGenuine, MAX_STATIONARY_DRIFT_M.toDouble())
                        consecutiveRejections++
                        checkLandDetection(sample)
                        return
                    }
                } else if (gpsSpeedKn < 2.0 && distM > MAX_STATIONARY_DRIFT_M) {
                    // Fallback to lastValidPoint when no genuine anchor yet
                    logRejection("stationary drift", distM, MAX_STATIONARY_DRIFT_M.toDouble())
                    consecutiveRejections++
                    checkLandDetection(sample)
                    return
                }
                val timeDeltaSec = (sample.timestampEpochMs - lastValidPointTimeMs) / 1000.0
                if (timeDeltaSec > 0.0) {
                    val impliedSpeedKn = (distM / timeDeltaSec) * 1.94384

                    // Gate 1: Context speed cap
                    val baseCap = if (isOnLand) landMaxSpeedKn else boatMaxSpeedKn

                    // Gate 2: Direction (sea only)
                    val effectiveCap = if (!isOnLand && lastValidCourseDeg != null) {
                        val bearingToSample = SpatialOperations.initialBearing(lastValidPos, sample.position)
                        val delta = angularDistance(bearingToSample, lastValidCourseDeg!!)
                        when {
                            delta <= COURSE_ALIGNED_DEG -> baseCap * COURSE_ALIGNED_MULTIPLIER
                            // At meaningful GPS speed, a bearing change is real navigation, not drift
                            sample.speedMps != null && sample.speedMps >= SIDEWAYS_SPEED_THRESHOLD_MPS -> baseCap
                            else -> baseCap * COURSE_SIDEWAYS_MULTIPLIER
                        }
                    } else {
                        baseCap  // land mode or no course history → neutral
                    }

                    if (impliedSpeedKn > effectiveCap) {
                        logRejection("speed cap", impliedSpeedKn, effectiveCap)
                        consecutiveRejections++
                        checkLandDetection(sample)
                        return
                    }

                    // Gate 3: Acceleration
                    val currentSpeedKn = sample.speedMps?.let { it * 1.94384 } ?: impliedSpeedKn
                    val accelKnPerSec = abs(currentSpeedKn - lastValidSpeedKn) / timeDeltaSec
                    val accelLimit = if (isOnLand) MAX_ACCEL_KN_PER_SEC_LAND else MAX_ACCEL_KN_PER_SEC_SEA

                    if (accelKnPerSec > accelLimit) {
                        logRejection("acceleration", accelKnPerSec, accelLimit)
                        consecutiveRejections++
                        checkLandDetection(sample)
                        return
                    }

                    // Accepted — reset rejection counter
                    consecutiveRejections = 0
                } else {
                    // Same-ms or out-of-order GPS timestamps: reject position changes >30 m.
                    // Consumer GPS CEP is ~5–10 m; 30 m is 3× worst case and prevents
                    // coordinate teleports from bypassing all gates when dt = 0.
                    if (distM > 30.0) {
                        logRejection("same-ms jump", distM, 30.0)
                        consecutiveRejections++
                        checkLandDetection(sample)
                        return
                    }
                    // Accepted — reset rejection counter (mirrors line 403 in dt>0 path)
                    consecutiveRejections = 0
                }
            }
        }

        // ── Capture accepted point ──
        captureAcceptedPoint(sample)
    }

    /** Record an accepted sample into the track (stats, UI, course history). */
    private fun captureAcceptedPoint(sample: TrackSample) {
        val track = currentTrack ?: return

        val now = System.currentTimeMillis()
        val timeOffsetSec = ((now - recordingStartTimeMs) / 1000).toInt()
        val rawMs = now - recordingStartTimeMs
        val timeOffsetMs = if (rawMs <= lastTimeOffsetMs) lastTimeOffsetMs + 1 else rawMs
        lastTimeOffsetMs = timeOffsetMs
        val point = TrackPoint(
            lat = sample.position.latitude,
            lon = sample.position.longitude,
            speedMps = sample.speedMps,
            bearingDeg = sample.bearingDeg,
            timeOffsetSec = timeOffsetSec,
            timeOffsetMs = timeOffsetMs,
            accuracyM = sample.accuracyM
        )

        // Accumulate stats
        val latestSpeedKn = sample.speedMps?.let { mps ->
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
        lastValidPointTimeMs = sample.timestampEpochMs
        lastAcceptedTimeMs = System.currentTimeMillis()

        // Rebuild track with new point appended (immutable list)
        currentTrack = track.copy(
            trackPoints = track.trackPoints + point,
            fastestSpeedMps = maxOf(track.fastestSpeedMps, sample.speedMps ?: 0f)
        )

        pointsSinceLastCheckpoint++
        _events.tryEmit(PointCaptured(point))
        _newPoint.tryEmit(point)
        val avgKn = if (speedCount > 0) {
            (speedSumMps / speedCount) * 1.94384f
        } else 0f
        _uiState.update {
            it.copy(
                pointCount = it.pointCount + 1,
                currentSpeedKn = latestSpeedKn,
                distanceNm = cumulativeDistanceNm,
                avgSpeedKn = avgKn
            )
        }

        // Update spike-rejection trackers
        val acceptedSpeedKn = sample.speedMps?.let { it * 1.94384 } ?: _uiState.value.currentSpeedKn.toDouble()
        lastValidSpeedKn = acceptedSpeedKn

        // Land-to-sea recovery: count consecutive sea-speed fixes while on land
        if (isOnLand) {
            val sampleSpeedKn = (sample.speedMps ?: 0f) * 1.94384
            if (sampleSpeedKn <= boatMaxSpeedKn) {
                landModeAcceptCounter++
                if (landModeAcceptCounter >= SEA_RECOVERY_CONSECUTIVE) {
                    isOnLand = false
                    landModeAcceptCounter = 0
                }
            } else {
                landModeAcceptCounter = 0
            }
        }

        // ── Update genuine anchor + bearing window ──
        if (acceptedSpeedKn >= 2.0) {
            // Compute bearing of this movement segment
            if (lastGenuineLat != null && lastGenuineLon != null) {
                val bearing = SpatialOperations.initialBearing(
                    LatLng(lastGenuineLat!!, lastGenuineLon!!), LatLng(point.lat, point.lon)
                )
                if (bearingWindow.size >= 5) bearingWindow.removeFirst()
                bearingWindow.addLast(bearing)
            }
            lastGenuineLat = point.lat
            lastGenuineLon = point.lon
        }

        updateCourseHistory(sample.position)
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
        isFinalizing = true
        val track = currentTrack ?: return
        stopCheckpointJob()
        stopPolling()
        cancelIdleTimer()
        stopDebounceStartTime = null

        val finalizeTimeMs = System.currentTimeMillis()

        val avgMps = if (speedCount > 0) speedSumMps / speedCount else 0f
        val totalElapsedSec = if (recordingStartTimeMs > 0) {
            (finalizeTimeMs - recordingStartTimeMs) / 1000
        } else 0L
        val simplifiedPoints = if (simplifyEnabled && track.trackPoints.size >= 3) {
            try {
                val result = TrackSimplifier.simplify(track.trackPoints, simplifyEpsilonM, simplifySpeedDeltaKn)
                result
            } catch (e: Exception) {
                Log.w(TAG, "finalizeTrack: simplification failed — saving raw points (${track.trackPoints.size})", e)
                track.trackPoints
            }
        } else {
            track.trackPoints
        }

        // Flush idle session — close open BoatMarker + emit IdlePeriodCompleted
        val session = activeSession
        if (wasStopped && idleStartMs > 0) {
            val delta = (System.currentTimeMillis() - idleStartMs) / 1000
            idleDurationSec += delta
            if (session != null) {
                closeOpenBoatMarker(System.currentTimeMillis())
                _events.tryEmit(IdlePeriodCompleted(
                    entryLat = session.entryLat,
                    entryLon = session.entryLon,
                    startTimeMs = session.startTimeMs,
                    endTimeMs = 0L,
                    durationSec = 0L,
                    autoMarkerId = session.autoMarkerId
                ))
            }
        }
        activeSession = null

        // Re-read currentTrack after closeOpenBoatMarker to include updated boatMarkers
        val trackAfterClose = currentTrack ?: track

        val reconciledIdleSec = maxOf(idleDurationSec, timelineIdleSec(track.trackPoints))
            .coerceAtMost(totalElapsedSec)

        // Sweep-close any open IDLE BoatMarkers (defensive — active marker already closed above).
        val finalMarkers = trackAfterClose.boatMarkers.map { bm ->
            if (bm.trigger == BoatMarkerTrigger.IDLE && bm.endTimeMs == null) {
                bm.copy(endTimeMs = finalizeTimeMs)
            } else bm
        }

        val finalized = trackAfterClose.copy(
            trackPoints = simplifiedPoints,
            boatMarkers = finalMarkers,
            endTimeMs = finalizeTimeMs,
            pausedDurationSec = 0,
            idleDurationSec = reconciledIdleSec,
            averageSpeedMps = avgMps,
            distanceNm = cumulativeDistanceNm,
            navigatingDurationSec = (totalElapsedSec - reconciledIdleSec - resumeGapDurationSec).coerceAtLeast(0),
            updatedAtEpochMs = finalizeTimeMs,
            visibleOnMap = true
        ).let { it.copy(lastPointTimeMs = it.lastRealPointTimeMsOrNull() ?: 0L) }

        // Only auto-rename if the current name matches the auto-generated pattern (D6)
        val isAutoName = trackAfterClose.name.matches(Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}"))
        val finalName = if (isAutoName) computeFinalTitle(finalized) else null
        val finalizedWithTitle = if (finalName != null) finalized.copy(name = finalName) else finalized
        currentTrack = finalizedWithTitle

        // Refresh description so sweep-closed IDLE durations appear in the comment (Fix 3).
        recomputeDescription()
        val finalizedTrack = currentTrack ?: finalizedWithTitle

        // D: transactional finalize — persist the finalized track and remove the checkpoint
        // synchronously on IO so the Stop tap is durable before the recorder leaves ON state.
        // A: save before delete, so a crash leaves a complete copy (finalized .bin or checkpoint).
        runBlocking(Dispatchers.IO) {
            repository.save(finalizedTrack)
            repository.deleteCheckpoint(finalizedTrack.id)
        }
        _events.tryEmit(Stopped)

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
    private fun checkLandDetection(sample: TrackSample) {
        if (consecutiveRejections >= LAND_DETECTION_REJECTIONS) {
            val speedKn = sample.speedMps?.let { it * 1.94384 } ?: 0.0
            if (speedKn > boatMaxSpeedKn) {
                isOnLand = true
                consecutiveRejections = 0
                lastValidCourseDeg = null  // reset course — direction check now off
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

    // ── Idle threshold timer + BoatMarker helpers ──

    /** Merge two lists of [MarkerSnapshot] by [MarkerSnapshot.markerId], keeping newer entries when both exist. */
    private fun unionMarkerSnapshots(old: List<MarkerSnapshot>, new: List<MarkerSnapshot>): List<MarkerSnapshot> {
        val map = LinkedHashMap<String, MarkerSnapshot>()
        for (s in old) map[s.markerId] = s
        for (s in new) map[s.markerId] = s  // overwrite with newer
        return map.values.toList()
    }

    private fun startIdleTimer(lat: Double, lon: Double, startMs: Long) {
        idleTimerJob?.cancel()
        val callback = idleThresholdCallback ?: return
        idleTimerJob = scope?.launch {
            delay(idleThresholdSec * 1000)
            try {
                val result = callback.onIdleThresholdReached(LatLng(lat, lon))
                val session = activeSession ?: return@launch
                if (result.entries.isNotEmpty()) {
                    val track = currentTrack ?: return@launch

                    // ── BoatMarker merge: check for nearby existing IDLE marker ──
                    val dedupRadiusM = AppConfig.boatMarkerAutoMarkerDedupRadiusM
                    val nowMs = System.currentTimeMillis()
                    val existingNearby = track.boatMarkers.findLast { bm ->
                        bm.trigger == BoatMarkerTrigger.IDLE &&
                        SpatialOperations.haversine(LatLng(lat, lon), LatLng(bm.boatLat, bm.boatLon)) <= dedupRadiusM
                    }
                    if (existingNearby != null) {
                        // Was there real travel between the two idle periods?
                        val trackStartMs = currentTrack!!.startTimeMs
                        val pointsBetween = track.trackPoints.filter { pt ->
                            val absTimeMs = trackStartMs + pt.timeOffsetMs
                            absTimeMs > existingNearby.startTimeMs && absTimeMs < nowMs
                        }
                        var cumulativeDist = 0.0
                        for (i in 1 until pointsBetween.size) {
                            cumulativeDist += SpatialOperations.haversine(
                                LatLng(pointsBetween[i - 1].lat, pointsBetween[i - 1].lon),
                                LatLng(pointsBetween[i].lat, pointsBetween[i].lon)
                            )
                        }
                        if (cumulativeDist < AppConfig.boatMarkerMinTravelBetweenStopsM) {
                            // No real travel — GPS noise → merge by reopening
                            val idx = track.boatMarkers.indexOf(existingNearby)
                            val mergedMarkers = unionMarkerSnapshots(existingNearby.markers, result.entries)
                            val reopened = existingNearby.copy(endTimeMs = null, markers = mergedMarkers)
                            currentTrack = track.copy(
                                boatMarkers = track.boatMarkers.toMutableList().also { it[idx] = reopened }
                            )
                            session.boatMarkerIndex = idx
                            session.boatMarkerMerged = true
                            repository.saveCheckpoint(currentTrack!!)
                            recomputeDescription()
                            pollTitle()
                            return@launch
                        }
                        // else: real travel → fall through, create new BoatMarker
                    }

                    val seqIdx = (track.boatMarkers.lastOrNull()?.sequenceIndex ?: -1) + 1
                    val bm = BoatMarker(
                        trigger = BoatMarkerTrigger.IDLE,
                        startTimeMs = startMs,
                        markers = result.entries,
                        boatLat = lat,
                        boatLon = lon,
                        sequenceIndex = seqIdx
                    )
                    currentTrack = track.copy(boatMarkers = track.boatMarkers + bm)
                    session.boatMarkerIndex = currentTrack!!.boatMarkers.lastIndex
                    repository.saveCheckpoint(currentTrack!!)
                    recomputeDescription()
                    pollTitle()
                }
                if (result.autoMarkerId != null) {
                    session.autoMarkerId = result.autoMarkerId
                }
                if (result.shouldOpenDrawer) {
                    session.drawerAutoOpened = true
                    _events.tryEmit(DrawerAutoOpenRequested)
                }
            } catch (e: Exception) {
                Log.w(TAG, "IdleThresholdCallback failed", e)
            }
        }
    }

    private fun cancelIdleTimer() {
        idleTimerJob?.cancel()
        idleTimerJob = null
    }

    /** Close the open BoatMarker (set endTimeMs) for the current idle session. */
    private fun closeOpenBoatMarker(nowMs: Long) {
        val session = activeSession ?: return
        val idx = session.boatMarkerIndex ?: return
        val track = currentTrack ?: return
        if (idx in track.boatMarkers.indices) {
            val closed = track.boatMarkers[idx].copy(endTimeMs = nowMs)
            currentTrack = track.copy(
                boatMarkers = track.boatMarkers.toMutableList().also { it[idx] = closed }
            )
            scope?.launch {
                currentTrack?.let { repository.saveCheckpoint(it) }
            }
        }
    }

    private fun cleanup() {
        stopPolling()
        cancelIdleTimer()
        collectingJob?.cancel()
        checkpointJob?.cancel()
        elapsedTimerJob?.cancel()
        collectingJob = null
        checkpointJob = null
        elapsedTimerJob = null
        scope = null
        activeSession = null
        wasInsideGeofence = false
    }

    /** Release all resources. */
    fun dispose() {
        cleanup()
    }

    // ── Track info: description + title ──────────────────────────────────

    /** Extract UserMarker from a WhereAmIMatch (local helper, mirrors MarkerMatcher.markerOf). */
    private fun markerOf(match: WhereAmIMatch): UserMarker = when (match) {
        is WhereAmIMatch.ZoneMatch -> match.marker
        is WhereAmIMatch.LineOfSightMatch -> match.marker
    }

    /** Extract top 2 non-IDLE_AUTO zone names from a WhereAmIResult, in whereAmI sort order. */
    private fun topZoneNames(result: WhereAmIResult): List<String> {
        return result.allMatches
            .filter { markerOf(it).origin != MarkerOrigin.IDLE_AUTO }
            .take(2)
            .map { markerOf(it).name }
    }

    /** Extract top 2 non-IDLE_AUTO names from pre-captured MarkerSnapshots. */
    private fun topSnapshotNames(snapshots: List<MarkerSnapshot>): List<String> {
        return snapshots
            .filter { it.name.isNotBlank() }
            .take(2)
            .map { it.name }
    }

    /** Check if a WhereAmIResult has at least one named (non-IDLE_AUTO) match. */
    private fun isNamed(result: WhereAmIResult): Boolean {
        return result.allMatches.any { markerOf(it).origin != MarkerOrigin.IDLE_AUTO }
    }

    /** Extract top named location from WhereAmIResult, or null if unnamed. */
    private fun topLocationName(result: WhereAmIResult): String? {
        return result.allMatches
            .firstOrNull { markerOf(it).origin != MarkerOrigin.IDLE_AUTO }
            ?.let { markerOf(it).name }
    }

    /** Check if a WhereAmIResult contains a pinned marker with the 🤿 diving icon. */
    private fun hasDivingPinnedMarker(result: WhereAmIResult): Boolean {
        return result.allMatches.any { match ->
            val m = markerOf(match)
            m.pinned && m.icon == "\uD83E\uDD3F"  // 🤿
        }
    }

    /** Extract the name of the first pinned 🤿 marker in the result, or null. */
    private fun divingLocationName(result: WhereAmIResult): String? {
        return result.allMatches
            .firstOrNull { match ->
                val m = markerOf(match)
                m.pinned && m.icon == "\uD83E\uDD3F"
            }
            ?.let { markerOf(it).name }
    }

    /** Extract the icon from the first non-IDLE_AUTO match whose marker is pinned with an icon, or null. */
    private fun topLocationIcon(result: WhereAmIResult): String? {
        return result.allMatches
            .firstOrNull { match ->
                val m = markerOf(match)
                m.origin != MarkerOrigin.IDLE_AUTO && m.pinned && m.icon != null
            }
            ?.let { markerOf(it).icon }
    }

    /** Human-readable duration: "3min" or "1h 15min". */
    private fun formatDuration(totalSec: Long): String {
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        return when {
            h > 0 && m > 0 -> "${h}h ${m}min"
            h > 0 -> "${h}h"
            else -> "${m}min"
        }
    }

    /** Format a single description bullet line for one BoatMarker. */
    private fun formatStopLine(bm: BoatMarker, zoneNames: List<String>): String {
        val time = SimpleDateFormat("HH:mm", Locale.US).format(Date(bm.startTimeMs))
        val zones = if (zoneNames.isEmpty()) "" else " at [${zoneNames.joinToString(", ")}]"
        val durSuffix = if (bm.endTimeMs != null) {
            val dur = (bm.endTimeMs - bm.startTimeMs) / 1000
            " for ${formatDuration(dur)}"
        } else {
            ""
        }
        return "  - stopped$zones @ $time$durSuffix"
    }

    /**
     * Rebuild the track description from the full BoatMarker history.
     * Called on every trigger: idle start, idle end, manual marker, title poll, marker change.
     * Reuses existing updateCurrentTrackMeta() for comment persistence.
     */
    private fun recomputeDescription() {
        try {
            val wia = whereAmI ?: return
            val track = currentTrack ?: return

            val lines = track.boatMarkers.map { bm ->
                val zones = when (bm.trigger) {
                    BoatMarkerTrigger.IDLE -> {
                        val result = wia(LatLng(bm.boatLat, bm.boatLon))
                        topZoneNames(result)
                    }
                    BoatMarkerTrigger.MANUAL -> {
                        topSnapshotNames(bm.markers)
                    }
                }
                formatStopLine(bm, zones)
            }

            val newComment = lines.joinToString("\n")
            if (track.comment != newComment) {
                updateCurrentTrackMeta(comment = newComment)
            }
        } catch (e: Exception) {
            Log.w(TAG, "recomputeDescription failed", e)
            _uiState.update { it.copy(infoError = "Track info: ${e.message}") }
        }
    }

    // ── Title polling ────────────────────────────────────────────────────

    private fun startPolling() {
        val wia = whereAmI ?: return
        titlePollJob?.cancel()
        titlePollJob = scope?.launch {
            while (true) {
                delay(TITLE_POLL_INTERVAL_MS)
                recomputeDescription()
                pollTitle()
            }
        }
        markerObserverJob?.cancel()
        markerObserverJob = markerChangeNotifier?.let { flow ->
            scope?.launch {
                flow.collect {
                    if (state == TrackRecorderState.ON && currentTrack != null) {
                        recomputeDescription()
                        pollTitle()
                    }
                }
            }
        }
    }

    private fun stopPolling() {
        titlePollJob?.cancel()
        titlePollJob = null
        markerObserverJob?.cancel()
        markerObserverJob = null
    }

    private fun pollTitle() {
        try {
            val wia = whereAmI ?: return
            val track = currentTrack ?: return
            val markers = track.boatMarkers
            if (markers.isEmpty()) return

            // ── Tier 1: 🤿 diving pinned marker (highest priority) ──
            for (bm in markers) {
                val result = wia(LatLng(bm.boatLat, bm.boatLon))
                if (hasDivingPinnedMarker(result)) {
                    val name = divingLocationName(result) ?: continue
                    val icon = topLocationIcon(result)
                    val displayName = if (icon != null) "$icon$name" else name
                    if (track.name != displayName) updateCurrentTrackMeta(name = displayName)
                    return
                }
            }

            // ── Tier 2: MANUAL priority ──
            val manualMarkers = markers.filter { it.trigger == BoatMarkerTrigger.MANUAL }
            if (manualMarkers.isNotEmpty()) {
                val latest = manualMarkers.maxByOrNull { it.startTimeMs } ?: return
                val result = wia(LatLng(latest.boatLat, latest.boatLon))
                val name = topLocationName(result) ?: return
                val icon = topLocationIcon(result)
                val displayName = if (icon != null) "$icon$name" else name
                if (track.name != displayName) updateCurrentTrackMeta(name = displayName)
                return
            }

            // ── Tier 3: IDLE longest duration ──
            val now = System.currentTimeMillis()
            val longest = markers
                .filter { it.trigger == BoatMarkerTrigger.IDLE }
                .maxByOrNull { (it.endTimeMs ?: now) - it.startTimeMs } ?: return

            val result = wia(LatLng(longest.boatLat, longest.boatLon))
            val name = topLocationName(result) ?: return
            val icon = topLocationIcon(result)
            val displayName = if (icon != null) "$icon$name" else name

            if (track.name != displayName) updateCurrentTrackMeta(name = displayName)
        } catch (e: Exception) {
            Log.w(TAG, "pollTitle failed", e)
            _uiState.update { it.copy(infoError = "Track info: ${e.message}") }
        }
    }

    /**
     * Compute the final track title from BoatMarker history at finalize time.
     * Uses the 3-tier priority: 🤿 diving > MANUAL > IDLE duration.
     */
    private fun computeFinalTitle(track: Track): String? {
        try {
            val wia = whereAmI ?: return null
            if (track.boatMarkers.isEmpty()) return null

            data class NamedStop(val name: String, val tier: Int, val durationSec: Long, val icon: String?)
            // tier: 1 = diving, 2 = manual, 3 = idle

            val now = System.currentTimeMillis()
            val namedStops = track.boatMarkers.mapNotNull { bm ->
                val result = wia(LatLng(bm.boatLat, bm.boatLon))
                val name = when {
                    hasDivingPinnedMarker(result) -> divingLocationName(result)
                    else -> topLocationName(result)
                }
                if (name != null) {
                    val dur = ((bm.endTimeMs ?: now) - bm.startTimeMs) / 1000
                    val tier = when {
                        hasDivingPinnedMarker(result) -> 1
                        bm.trigger == BoatMarkerTrigger.MANUAL -> 2
                        else -> 3
                    }
                    val icon = topLocationIcon(result)
                    NamedStop(name, tier, dur, icon)
                } else null
            }.sortedWith(compareBy({ it.tier }, { -it.durationSec }))

            val diving = namedStops.filter { it.tier == 1 }
            val manuals = namedStops.filter { it.tier == 2 }
            val idles = namedStops.filter { it.tier == 3 }

            fun display(stop: NamedStop) = "${stop.icon.orEmpty()}${stop.name}"

            return when {
                diving.size >= 2 -> "${display(diving[0])} -> ${display(diving[1])}"
                diving.size == 1 && manuals.isNotEmpty() -> "${display(diving[0])} -> ${display(manuals[0])}"
                diving.size == 1 && idles.isNotEmpty() -> "${display(diving[0])} -> ${display(idles[0])}"
                diving.size == 1 -> display(diving[0])
                manuals.size >= 2 -> "${display(manuals[0])} -> ${display(manuals[1])}"
                manuals.size == 1 && idles.isNotEmpty() -> "${display(manuals[0])} -> ${display(idles[0])}"
                manuals.size == 1 -> display(manuals[0])
                idles.size >= 2 -> "${display(idles[0])} -> ${display(idles[1])}"
                idles.size == 1 -> display(idles[0])
                else -> null
            }
        } catch (e: Exception) {
            Log.w(TAG, "computeFinalTitle failed", e)
            _uiState.update { it.copy(infoError = "Track info: ${e.message}") }
            return null
        }
    }
}
