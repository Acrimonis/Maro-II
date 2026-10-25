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
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ykws.android.maro.BuildConfig
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.location.GpsLocationSource
import ykws.android.maro.data.markers.AutoMarkerManager
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.MarkerOrigin
import ykws.android.maro.data.model.markers.UserMarker
import ykws.android.maro.data.track.TrackEvent.*
import ykws.android.maro.spatial.MarkerMatcher
import ykws.android.maro.spatial.SpatialOperations
import ykws.android.maro.spatial.Units
import ykws.android.maro.spatial.WhereAmIMatch
import ykws.android.maro.spatial.WhereAmIResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.IOException
import java.util.UUID
import kotlin.math.abs

private const val TAG = "MaroII_Track"

/** Duration (ms) speed must be above threshold before auto-start. */
private const val DEBOUNCE_MS = 10_000L

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
private val MIN_MOVEMENT_SPEED_FOR_ACCURACY_MPS = Units.MPS_PER_KNOT.toFloat()

/** Interval (ms) between title poll ticks. */
private const val TITLE_POLL_INTERVAL_MS = 180_000L

/**
 * One worker's worth of [dispatcher]. `Dispatchers.Unconfined` cannot be limited and needs no
 * limiting — it runs each coroutine on the caller's own thread until it suspends, which is the
 * eager behaviour the recorder's unit tests lean on — so it is used as it is.
 */
private fun serialLaneOf(dispatcher: CoroutineDispatcher): CoroutineDispatcher =
    if (dispatcher === Dispatchers.Unconfined) dispatcher else dispatcher.limitedParallelism(1)

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
 * The recorder's user-tunable parameters, read from [ykws.android.maro.data.settings.AppSettings]
 * and replaced as one immutable snapshot whenever a setting moves (see [TrackRecorder.applyConfig]),
 * so a threshold changed in Settings reaches the live recorder rather than waiting for the next
 * process.
 */
data class TrackRecorderConfig(
    /** Port Salis geofence origin latitude. */
    val geofenceOriginLat: Double = 43.55,
    /** Port Salis geofence origin longitude. */
    val geofenceOriginLon: Double = 7.00,
    /** Geofence radius in metres. */
    val geofenceRadiusM: Double = 500.0,
    /** When false, recording starts on movement alone. */
    val geofenceEnabled: Boolean = true,
    /** GPS mode arms the spike-rejection gates; demo mode bypasses them. */
    val gpsMode: Boolean = true,
    /** Maximum self-reported accuracy (m) for a fix to be recorded. */
    val maxRecordingAccuracyM: Float = 30f,
    /** When true, simplify track points on finalize (Douglas-Peucker + speed-aware). */
    val simplifyEnabled: Boolean = true,
    /** Douglas-Peucker tolerance (metres). Lower = more points kept. */
    val simplifyEpsilonM: Double = 3.0,
    /** Speed deviation threshold (knots) to re-insert a point during simplification. */
    val simplifySpeedDeltaKn: Double = 3.0,
    val idleThresholdSec: Long = 60,
    val autoMarkerMinDurationSec: Long = AppConfig.boatMarkerAutoMarkerMinDurationSec,
    val autoMarkerDedupRadiusM: Double = AppConfig.boatMarkerAutoMarkerDedupRadiusM
) {
    companion object {
        /** The snapshot a settings value maps to — the one place the two shapes meet. */
        fun from(s: ykws.android.maro.data.settings.AppSettings): TrackRecorderConfig = TrackRecorderConfig(
            geofenceOriginLat = s.trackOriginLat,
            geofenceOriginLon = s.trackOriginLon,
            geofenceRadiusM = s.trackGeofenceRadiusM,
            geofenceEnabled = s.trackGeofenceEnabled,
            gpsMode = s.gpsMode,
            maxRecordingAccuracyM = s.maxRecordingAccuracyM,
            simplifyEnabled = s.trackSimplifyEnabled,
            simplifyEpsilonM = s.trackSimplifyEpsilonM,
            simplifySpeedDeltaKn = s.trackSimplifySpeedDeltaKn,
            idleThresholdSec = s.boatMarkerIdleThresholdSec,
            autoMarkerMinDurationSec = s.boatMarkerAutoMarkerMinDurationSec,
            autoMarkerDedupRadiusM = s.boatMarkerAutoMarkerDedupRadiusM
        )
    }
}

/**
 * Coroutine-based track recording state machine.
 *
 * Two states: OFF (not tracking) and ON (tracking). Within ON, point capture
 * is gated by [isStopped] — when the boat is stationary,
 * GPS fixes are received but not saved to the track.
 *
 * Auto-start on geofence exit (Port Salis) with 10s movement debounce.
 * Arrival at the home port idles into a three-door prompt rather than a silent auto-stop.
 *
 * **Threading.** Every mutation of the recorder's state runs on one serial dispatcher
 * ([dispatcher] limited to a parallelism of one): the sample collector, the timers, the idle
 * session and every public command — which only *post* their work there and return at once. Nothing
 * blocks a caller's thread, and no two mutations ever interleave. Disk work hops to IO inside that
 * confinement and comes back to it.
 *
 * @param repository              Track persistence layer.
 * @param config                  The tunables, replaced live through [applyConfig].
 * @param isStopped               True when the boat is stationary (from NavigationVM adaptive policy). Default false.
 * @param dispatcher              Coroutine dispatcher the state machine's serial lane is cut from.
 * @param whereAmI                Synchronous whereAmI lookup — nullable; when null the feature is dormant.
 * @param markerChangeNotifier    Flow that emits when external markers change — nullable.
 * @param gapDistanceThresholdM   Distance threshold (metres) to insert a GAP marker on resume.
 * @param gapTimeThresholdSec     Time threshold (seconds) to insert a GAP marker on resume.
 */
class TrackRecorder(
    private val repository: TrackRepository,
    config: TrackRecorderConfig = TrackRecorderConfig(),
    val isStopped: StateFlow<Boolean> = MutableStateFlow(false),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    /** The home port's display name for the comment's departure/return line (upstream). */
    private val homePortDisplayName: String = BuildConfig.TRACK_DISPLAY_NAME,
    private val idleThresholdCallback: IdleThresholdCallback? = null,
    private val whereAmI: ((LatLng) -> WhereAmIResult)? = null,
    private val markerChangeNotifier: Flow<Unit>? = null,
    private val nameConnector: String = "to",
    private val nameMaxLength: Int = AppConfig.trackNameMaxLength,
    private val gapDistanceThresholdM: Double = 200.0,
    private val gapTimeThresholdSec: Long = 120L,
    private val autoMarkerManager: AutoMarkerManager? = null
) {
    /** The live tunables — an immutable snapshot swapped whole, so a reader never sees a half-applied change. */
    @Volatile
    private var config: TrackRecorderConfig = config

    /** Replace the tunables; the next sample, finalize or idle timer reads the new values. */
    fun applyConfig(newConfig: TrackRecorderConfig) {
        config = newConfig
    }

    private val geofenceOriginLat: Double get() = config.geofenceOriginLat
    private val geofenceOriginLon: Double get() = config.geofenceOriginLon
    private val geofenceRadiusM: Double get() = config.geofenceRadiusM
    private val geofenceEnabled: Boolean get() = config.geofenceEnabled
    private val gpsMode: Boolean get() = config.gpsMode
    private val maxRecordingAccuracyM: Float get() = config.maxRecordingAccuracyM
    private val simplifyEnabled: Boolean get() = config.simplifyEnabled
    private val simplifyEpsilonM: Double get() = config.simplifyEpsilonM
    private val simplifySpeedDeltaKn: Double get() = config.simplifySpeedDeltaKn
    private val idleThresholdSec: Long get() = config.idleThresholdSec
    private val autoMarkerMinDurationSec: Long get() = config.autoMarkerMinDurationSec
    private val autoMarkerDedupRadiusM: Double get() = config.autoMarkerDedupRadiusM

    private val _uiState = MutableStateFlow(TrackRecorderUiState())
    val uiState: StateFlow<TrackRecorderUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<TrackEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<TrackEvent> = _events.asSharedFlow()

    /** Incremental point stream — emits each captured point for polyline appending. */
    private val _newPoint = MutableSharedFlow<TrackPoint>(extraBufferCapacity = 64)
    val newPoint: SharedFlow<TrackPoint> = _newPoint.asSharedFlow()

    // Internal mutable state — written only on the serial lane; the two volatiles are the fields
    // a caller may read from another thread ([snapshotPoints], the service's recording flag).
    @Volatile
    private var state: TrackRecorderState = TrackRecorderState.OFF
    @Volatile
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
    /** Tracks whether the previous fix was inside the geofence, for exit detection. */
    private var wasInsideGeofence: Boolean = false
    /** The boat's latest inside-geofence reading, across both recorder states (home-port line). */
    private var lastInsideGeofence: Boolean = false
    /** Whether the trip ended at the home port — geofence entrance, or a manual stop inside it. */
    private var endedAtHomePort: Boolean = false
    /** The arrival prompt fires once per entry; only a re-exit re-arms it. */
    private var arrivalPromptArmed: Boolean = true

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

    /**
     * The one lane every mutation runs on: [dispatcher] cut down to a single worker, so coroutines
     * on it never run in parallel. Lives from construction to [dispose]. A coroutine that dies on
     * it is logged, never allowed to take the process down mid-trip.
     */
    private val scope: CoroutineScope = CoroutineScope(
        serialLaneOf(dispatcher) + SupervisorJob() +
            CoroutineExceptionHandler { _, e -> Log.e(TAG, "recorder coroutine failed", e) }
    )

    /**
     * The lifecycle lock: a single worker keeps coroutines from running in parallel but not from
     * interleaving at suspension points, so a Stop, a Discard, a start and every sample hold this
     * for their whole transition — a finalize that is waiting on disk is never overlapped by a
     * second command that would clear or delete the track it is about to write. Never reentrant:
     * [finalizeTrack] and [beginRecording] are called only by holders.
     */
    private val lifecycleMutex = Mutex()
    private var collectingJob: Job? = null
    private var checkpointJob: Job? = null
    private var elapsedTimerJob: Job? = null
    private var titlePollJob: Job? = null
    private var markerObserverJob: Job? = null

    /**
     * Manually append a BoatMarker to the current track.
     * Used by the boat-marker button in MapScreen (MANUAL trigger).
     */
    fun addManualBoatMarker(snapshots: List<MarkerSnapshot>) {
        scope.launch { lifecycleMutex.withLock { addManualBoatMarkerOnLane(snapshots) } }
    }

    /** A checkpoint write that cannot fail the caller: the next one retries, and the finalize reports. */
    private suspend fun checkpointQuietly(track: Track) {
        try {
            checkpointQuietly(track)
        } catch (e: IOException) {
            Log.w(TAG, "checkpoint write failed for ${track.id}", e)
        }
    }

    private fun addManualBoatMarkerOnLane(snapshots: List<MarkerSnapshot>) {
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
        scope.launch {
            currentTrack?.let { checkpointQuietly(it) }
        }
        recomputeDescription()
        pollTitle()
    }

    /**
     * Start collecting [TrackSample] events and driving the state machine.
     * Call from viewModelScope or similar.
     */
    fun start(sampleFlow: Flow<TrackSample>) {
        scope.launch {
            collectingJob?.cancel()
            collectingJob = scope.launch {
                sampleFlow.collect { sample -> processSample(sample) }
            }
            startElapsedTimer()
        }
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
        scope.launch { lifecycleMutex.withLock { resumeOnLane(track, sampleFlow, fromCheckpoint) } }
    }

    private fun resumeOnLane(track: Track, sampleFlow: Flow<TrackSample>, fromCheckpoint: Boolean) {
        if (state != TrackRecorderState.OFF) {
            Log.w(TAG, "resume: ignored — state=$state (not OFF)")
            return
        }
        isFinalizing = false

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
        idleDurationSec = track.idleDurationSec
        idleStartMs = 0L
        wasStopped = false
        endedAtHomePort = false
        arrivalPromptArmed = true
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
                avgSpeedKn = resumedTrack.averageSpeedMps * Units.KNOTS_PER_MPS.toFloat(),
                maxSpeedKn = resumedTrack.fastestSpeedMps * Units.KNOTS_PER_MPS.toFloat(),
                elapsedSeconds = (now - resumedTrack.startTimeMs - resumeGapDurationSec) / 1000
            )
        }

        Log.d(TAG, "resume: id=${track.id} name=${track.name} existingPoints=${points.size} isResuming=$isResuming")

        collectingJob?.cancel()
        // Emit Resumed event with existing points so MapScreen restores the polyline
        if (points.isNotEmpty()) {
            _events.tryEmit(Resumed(points))
        }
        collectingJob = scope.launch {
            sampleFlow.collect { sample -> processSample(sample) }
        }
        startElapsedTimer()
        startCheckpointJob()
        startPolling()

        _events.tryEmit(Started)
    }

    /**
     * Stop the recorder — finalizes the current track if recording. Returns at once; the finalize
     * runs on the lane and the recorder reads ON until the track is on disk. A stop while OFF is a
     * no-op: sample collection stays live, so a later start still records (it used to tear the
     * collector down and leave the next recording deaf).
     */
    fun stop() {
        scope.launch {
            lifecycleMutex.withLock {
                if (state == TrackRecorderState.ON) {
                    finalizeTrack()
                } else {
                    Log.d(TAG, "stop: ignored — state=$state (not ON)")
                }
            }
        }
    }

    /** Snapshot of the current track's points — used to restore the live polyline after UI re-attach. */
    fun snapshotPoints(): List<TrackPoint> = currentTrack?.trackPoints.orEmpty()

    /**
     * Discard the in-progress recording without finalizing.
     * Deletes the current track file and its checkpoint, then returns to OFF.
     */
    fun discard() {
        scope.launch {
            lifecycleMutex.withLock {
                if (state != TrackRecorderState.ON) {
                    Log.d(TAG, "discard: ignored — state=$state (not ON)")
                    return@withLock
                }
                val trackId = currentTrack?.id
                stopCheckpointJob()
                stopPolling()
                cancelIdleTimer()
                // The delete lands before the recorder reads OFF, so an exit that waits for OFF
                // cannot tear the service down with the discarded files still on disk; and it is
                // not cancellable once begun, for the same reason as the finalize's write.
                if (trackId != null) {
                    withContext(NonCancellable) {
                        try {
                            repository.delete(trackId)
                            repository.deleteCheckpoint(trackId)
                        } catch (e: IOException) {
                            Log.w(TAG, "discard: delete failed for $trackId", e)
                        }
                    }
                }
                transitionTo(TrackRecorderState.OFF)
                currentTrack = null
                _uiState.update { TrackRecorderUiState() }
                _events.tryEmit(Stopped)
            }
        }
    }

    /** Update the current track's name and/or comment in memory and checkpoint. */
    fun updateCurrentTrackMeta(name: String? = null, comment: String? = null) {
        scope.launch { lifecycleMutex.withLock { updateCurrentTrackMetaOnLane(name, comment) } }
    }

    private fun updateCurrentTrackMetaOnLane(name: String?, comment: String?) {
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
            scope.launch {
                currentTrack?.let { checkpointQuietly(it) }
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
        scope.launch {
            lifecycleMutex.withLock {
                if (state != TrackRecorderState.OFF) {
                    Log.d(TAG, "startManual: ignored — state=$state (not OFF)")
                    return@withLock
                }
                Log.d(TAG, "startManual: → beginRecording")
                beginRecording(isManual = true)
            }
        }
    }

    /** One sample, under the lifecycle lock: a sample never lands while a transition is in flight. */
    private suspend fun processSample(sample: TrackSample) = lifecycleMutex.withLock {
        processSampleLocked(sample)
    }

    private suspend fun processSampleLocked(sample: TrackSample) {
        // A finalize in flight owns the track: a sample landing during its IO hop is dropped rather
        // than appended to a track that is being written.
        if (isFinalizing) return
        val insideGeofence = geofenceEnabled && TrackGeofenceChecker.isInsideGeofence(
            posLat = sample.position.latitude,
            posLon = sample.position.longitude,
            originLat = geofenceOriginLat,
            originLon = geofenceOriginLon,
            radiusM = geofenceRadiusM
        )
        lastInsideGeofence = insideGeofence

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
            }

            TrackRecorderState.ON -> {
                Log.v(TAG, "processSample(ON): speed=${sample.speedMps} pos=(${sample.position.latitude},${sample.position.longitude})")
                markDemoIfSimulated(sample)
                addPoint(sample)

                // The silent 15 s auto-stop is gone; the arrival prompt re-arms on a re-exit.
                if (wasInsideGeofence && !insideGeofence) {
                    arrivalPromptArmed = true
                }
            }
        }
        wasInsideGeofence = insideGeofence
    }

    /** A trip that ever takes a simulated sample is a demo trip, for good: a pure GPS trip never is. */
    private fun markDemoIfSimulated(sample: TrackSample) {
        currentTrack = currentTrack?.markedFor(sample)
    }

    private fun beginRecording(isManual: Boolean, startSample: TrackSample? = null) {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val name = formatTimestamp(now)
        currentTrack = Track(
            id = id,
            name = name,
            startTimeMs = now,
            trackPoints = mutableListOf(),
            startedAtHomePort = if (isManual) lastInsideGeofence else true
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
        idleDurationSec = 0L
        idleStartMs = 0L
        wasStopped = false
        cancelIdleTimer()
        activeSession = null
        isResuming = false
        checkpointFileDeleted = false
        isFinalizing = false
        endedAtHomePort = false
        arrivalPromptArmed = true
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

        startSample?.let {
            // The auto-start's own sample reaches the track here rather than through the ON branch.
            markDemoIfSimulated(it)
            addPoint(it)
        }
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
                scope.launch {
                    try {
                        repository.deleteCheckpoint(track.id)
                    } catch (e: IOException) {
                        Log.w(TAG, "resume: checkpoint delete failed for ${track.id}", e)
                    }
                }
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

        val speedKn = sample.speedMps?.let { it * Units.KNOTS_PER_MPS.toFloat() }
        Log.d(TAG, "addPoint: speed=${speedKn} kn isStopped=$stopped state=$state")

        // ── Idle duration accumulation + BoatMarker session lifecycle ──
        val now = System.currentTimeMillis()
        if (stopped && !wasStopped) {
            // Transition: moving → idle
            idleStartMs = now
            val lat = sample.position.latitude
            val lon = sample.position.longitude
            val session = IdleSessionContext(startTimeMs = now, entryLat = lat, entryLon = lon)
            activeSession = session
            session.autoMarkerJob = scope.launch {
                val id = autoMarkerManager?.createTemp(lat, lon, now, currentTrack?.id, autoMarkerDedupRadiusM).orEmpty()
                session.autoMarkerId = id.ifEmpty { null }
            }
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
                    scope.launch {
                        session.autoMarkerJob?.join()
                        resolveAutoMarker(session, now, delta, finalized = false)
                    }
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
                val gpsSpeedKn = sample.speedMps?.let { it.toDouble() * Units.KNOTS_PER_MPS } ?: 0.0
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
                    val gpsSpeedKn = sample.speedMps?.let { it.toDouble() * Units.KNOTS_PER_MPS } ?: 0.0
                    if (gpsSpeedKn < 2.0 && distM > MAX_STATIONARY_DRIFT_M) {
                        logRejection("stale drift", distM, MAX_STATIONARY_DRIFT_M.toDouble())
                        return
                    }
                    val dtSec = (sample.timestampEpochMs - lastValidPointTimeMs) / 1000.0
                    if (dtSec > 0.0) {
                        val impliedKn = (distM / dtSec) * Units.KNOTS_PER_MPS
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
                val gpsSpeedKn = sample.speedMps?.let { it.toDouble() * Units.KNOTS_PER_MPS } ?: 0.0
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
                val gpsSpeedKn = sample.speedMps?.let { it.toDouble() * Units.KNOTS_PER_MPS } ?: 0.0
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
                    val impliedSpeedKn = (distM / timeDeltaSec) * Units.KNOTS_PER_MPS

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
                    val currentSpeedKn = sample.speedMps?.let { it.toDouble() * Units.KNOTS_PER_MPS } ?: impliedSpeedKn
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
            val speedKn = mps * Units.KNOTS_PER_MPS.toFloat()
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
            cumulativeDistanceNm += Units.metresToNauticalMiles(distM).toFloat()
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
            (speedSumMps / speedCount) * Units.KNOTS_PER_MPS.toFloat()
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
        val acceptedSpeedKn = sample.speedMps?.let { it.toDouble() * Units.KNOTS_PER_MPS } ?: _uiState.value.currentSpeedKn.toDouble()
        lastValidSpeedKn = acceptedSpeedKn

        // Land-to-sea recovery: count consecutive sea-speed fixes while on land
        if (isOnLand) {
            val sampleSpeedKn = (sample.speedMps ?: 0f).toDouble() * Units.KNOTS_PER_MPS
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
        checkpointJob = scope.launch {
            while (true) {
                delay(CHECKPOINT_INTERVAL_MS)
                val track = currentTrack ?: continue
                checkpointQuietly(track)
            }
        }
    }

    private fun stopCheckpointJob() {
        checkpointJob?.cancel()
        checkpointJob = null
    }

    private fun startElapsedTimer() {
        elapsedTimerJob?.cancel()
        elapsedTimerJob = scope.launch {
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

    /**
     * Finalize the current track on the lane: simplify, title, then persist on IO and only then
     * leave ON. A save that fails keeps the checkpoint on disk and reports through [TrackRecorderUiState.infoError],
     * so the next launch offers the recording for recovery instead of the recorder claiming it saved.
     */
    private suspend fun finalizeTrack() {
        isFinalizing = true
        val track = currentTrack ?: run { isFinalizing = false; return }
        stopCheckpointJob()
        stopPolling()
        cancelIdleTimer()

        val finalizeTimeMs = System.currentTimeMillis()
        endedAtHomePort = lastInsideGeofence

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

        // Flush idle session — close open BoatMarker (final open idle is kept, §9)
        val session = activeSession
        if (wasStopped && idleStartMs > 0) {
            val delta = (System.currentTimeMillis() - idleStartMs) / 1000
            idleDurationSec += delta
            if (session != null) {
                closeOpenBoatMarker(System.currentTimeMillis())
            }
        }
        activeSession = null

        // Re-read currentTrack after closeOpenBoatMarker to include updated boatMarkers
        val trackAfterClose = currentTrack ?: track

        val reconciledIdleSec = maxOf(idleDurationSec, timelineIdleSec(simplifiedPoints))
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
            updatedAtEpochMs = finalizeTimeMs
        ).let { it.copy(lastPointTimeMs = it.lastRealPointTimeMsOrNull() ?: 0L) }

        // Only auto-rename if the current name matches the auto-generated pattern (D6), and the
        // markers the trip met name it ahead of a stop's own title: the fusion is read on the raw
        // points, before simplification thins the detail a short visit lives in.
        val isAutoName = trackAfterClose.name.matches(Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}"))
        val markerName = if (isAutoName) composeTripName(trackAfterClose.trackPoints) else null
        val finalName = when {
            !isAutoName -> null
            markerName != null -> markerName
            else -> computeFinalTitle(finalized)
        }
        val finalizedWithTitle = if (finalName != null) finalized.copy(name = finalName) else finalized
        currentTrack = finalizedWithTitle

        // Refresh description so sweep-closed IDLE durations appear in the comment (Fix 3).
        recomputeDescription()
        val finalizedTrack = currentTrack ?: finalizedWithTitle

        // D: transactional finalize — persist the finalized track and remove the checkpoint on IO,
        // suspending the lane (never blocking a thread) so the Stop tap is durable before the
        // recorder leaves ON state. A: save before delete, so a crash leaves a complete copy
        // (finalized .bin or checkpoint). Resolve the final open idle's auto-marker: confirm on
        // success, fallback delete on failure (§4). The marker's trackId was set at creation.
        // NonCancellable: a service torn down mid-finalize (Save-and-exit, task removal) must not
        // cut the write short once it has begun.
        // Null when the track landed; the failure's own words otherwise.
        val saveError: String? = withContext(NonCancellable + Dispatchers.IO) {
            val mgr = autoMarkerManager
            if (mgr != null && session != null) {
                session.autoMarkerJob?.join()
                val id = session.autoMarkerId
                if (id != null) {
                    try {
                        mgr.confirm(id, session.startTimeMs, 0L, 0L)
                    } catch (e: Exception) {
                        Log.w(TAG, "finalizeTrack: confirm failed — fallback delete", e)
                        try {
                            mgr.delete(id)
                        } catch (e2: Exception) {
                            Log.w(TAG, "finalizeTrack: fallback delete also failed", e2)
                        }
                    }
                }
            }
            try {
                repository.save(finalizedTrack)
                repository.deleteCheckpoint(finalizedTrack.id)
                null
            } catch (e: IOException) {
                Log.e(TAG, "finalizeTrack: save failed — recording retained for retry", e)
                // Best effort: put the complete snapshot on disk as a checkpoint, so a crash before
                // the retry still leaves the whole recording recoverable. A failure here is the same
                // disk failing twice; the in-memory copy below is then the only one, and it is kept.
                checkpointQuietly(finalizedTrack)
                e.message ?: e.javaClass.simpleName
            }
        }

        if (saveError != null) {
            // Retain: the recorder stays ON holding the finalized snapshot, the checkpoint job
            // resumes so the disk is retried every interval, and the error channel says why. The
            // next Stop finalizes again from this snapshot; only a successful save or an explicit
            // Discard ever clears it. The idle clock restarts so a retry does not count the same
            // idle time twice.
            idleStartMs = if (wasStopped) System.currentTimeMillis() else 0L
            isFinalizing = false
            startCheckpointJob()
            _uiState.update { it.copy(infoError = saveError) }
            return
        }

        _events.tryEmit(Finalized(finalizedTrack.id, finalizedTrack.name))
        _events.tryEmit(Stopped)

        transitionTo(TrackRecorderState.OFF)
        currentTrack = null
        isFinalizing = false
        _uiState.update { TrackRecorderUiState() }
    }

    /** The new-recording name, from its one home beside [Track] — see [trackAutoName]. */
    private fun formatTimestamp(epochMs: Long): String = trackAutoName(epochMs)

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
            val speedKn = sample.speedMps?.let { it.toDouble() * Units.KNOTS_PER_MPS } ?: 0.0
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
        idleTimerJob = scope.launch {
            delay(idleThresholdSec * 1000)
            // The home-port arrival owns the same threshold: the latest fix decides, not the
            // idle-entry position, so a boat already stopped that drifts in still asks — with
            // the checkpoint persisted so a crash after the prompt still has the trip.
            if (arrivalPromptArmed && lastInsideGeofence) {
                arrivalPromptArmed = false
                currentTrack?.let { checkpointQuietly(it) }
                _events.tryEmit(HomePortArrivalPrompt)
                return@launch
            }
            val callback = idleThresholdCallback ?: return@launch
            try {
                val result = callback.onIdleThresholdReached(LatLng(lat, lon))
                val session = activeSession ?: return@launch
                if (result.entries.isNotEmpty()) {
                    val track = currentTrack ?: return@launch

                    // ── BoatMarker merge: check for nearby existing IDLE marker ──
                    val dedupRadiusM = autoMarkerDedupRadiusM
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
                            checkpointQuietly(currentTrack!!)
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
                    checkpointQuietly(currentTrack!!)
                    recomputeDescription()
                    pollTitle()
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
            scope.launch {
                currentTrack?.let { checkpointQuietly(it) }
            }
        }
    }

    /**
     * Resolve a completed idle session's auto-marker: confirm (duration ≥ min, or
     * finalize) or delete when too short. The marker's trackId is set at creation,
     * so no BoatMarker link needs to be persisted here.
     */
    private suspend fun resolveAutoMarker(
        session: IdleSessionContext,
        endTimeMs: Long,
        durationSec: Long,
        finalized: Boolean
    ) {
        val mgr = autoMarkerManager ?: return
        val id = session.autoMarkerId ?: return
        val minDuration = autoMarkerMinDurationSec
        if (finalized || durationSec >= minDuration) {
            try {
                mgr.confirm(id, session.startTimeMs, endTimeMs, durationSec)
            } catch (e: Exception) {
                Log.w(TAG, "resolveAutoMarker: confirm failed — fallback delete", e)
                try {
                    mgr.delete(id)
                } catch (e2: Exception) {
                    Log.w(TAG, "resolveAutoMarker: fallback delete also failed", e2)
                }
                return
            }
        } else {
            try {
                mgr.delete(id)
            } catch (e: Exception) {
                Log.w(TAG, "resolveAutoMarker: delete failed", e)
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
        activeSession = null
        wasInsideGeofence = false
    }

    /** Release all resources — the lane itself is cancelled, so the recorder is finished for good. */
    fun dispose() {
        cleanup()
        scope.cancel()
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

    /** Check if a WhereAmIResult contains a marker with the 🤿 diving icon (pure icon semantics). */
    private fun hasDivingPinnedMarker(result: WhereAmIResult): Boolean {
        return result.allMatches.any { match ->
            val m = markerOf(match)
            m.icon == "\uD83E\uDD3F"  // 🤿
        }
    }

    /** Extract the name of the first 🤿 marker in the result, or null. */
    private fun divingLocationName(result: WhereAmIResult): String? {
        return result.allMatches
            .firstOrNull { match ->
                val m = markerOf(match)
                m.icon == "\uD83E\uDD3F"
            }
            ?.let { markerOf(it).name }
    }

    /** Extract the icon from the first non-IDLE_AUTO match whose marker carries an icon, or null. */
    private fun topLocationIcon(result: WhereAmIResult): String? {
        return result.allMatches
            .firstOrNull { match ->
                val m = markerOf(match)
                m.origin != MarkerOrigin.IDLE_AUTO && m.icon != null
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

    /** Wall-clock "HH:mm" for a comment line. */
    private fun formatClock(epochMs: Long): String =
        SimpleDateFormat("HH:mm", Locale.US).format(Date(epochMs))

    /** Format a single description line for one BoatMarker: "HH:mm: Zone for Xmin". */
    private fun formatStopLine(bm: BoatMarker, zoneNames: List<String>): String {
        val time = formatClock(bm.startTimeMs)
        val zones = zoneNames.joinToString(", ")
        val durSuffix = if (bm.endTimeMs != null) {
            val dur = (bm.endTimeMs - bm.startTimeMs) / 1000
            " for ${formatDuration(dur)}"
        } else {
            ""
        }
        return when {
            zones.isNotEmpty() -> "$time: $zones$durSuffix"
            else -> "$time$durSuffix"
        }
    }

    /** Whether the given position stands inside the home-port geofence (settled 2026-10-24). */
    private fun isHomePortPosition(lat: Double, lon: Double): Boolean =
        TrackNaming.namesHomePort(lat, lon, geofenceOriginLat, geofenceOriginLon, geofenceRadiusM)

    /** The comment's home-port line, or null when neither end is the port — the shape owned by
     *  [TrackNaming.homePortCommentLine], the recorder supplying the times and the name read from
     *  the one `maro.properties` block (settled 2026-10-24). */
    private fun homePortLine(track: Track): String? =
        TrackNaming.homePortCommentLine(
            startedAtHomePort = track.startedAtHomePort,
            endedAtHomePort = endedAtHomePort,
            startTimeMs = track.startTimeMs,
            endTimeMs = track.endTimeMs,
            displayName = homePortDisplayName
        ) { formatClock(it) }

    /**
     * Rebuild the track description from the full BoatMarker history.
     * Called on every trigger: idle start, idle end, manual marker, title poll, marker change.
     * Reuses existing updateCurrentTrackMeta() for comment persistence.
     */
    private fun recomputeDescription() {
        try {
            val wia = whereAmI ?: return
            val track = currentTrack ?: return

            val portLine = homePortLine(track)
            val stopLines = track.boatMarkers.mapNotNull { bm ->
                // The port's own line carries its entry/exit times; an idle stop there would print
                // the time-spent duration the user does not need (settled 2026-10-24).
                if (bm.trigger == BoatMarkerTrigger.IDLE && isHomePortPosition(bm.boatLat, bm.boatLon)) {
                    null
                } else {
                    val zones = when (bm.trigger) {
                        BoatMarkerTrigger.IDLE -> {
                            val result = wia(LatLng(bm.boatLat, bm.boatLon))
                            topZoneNames(result)
                        }
                        BoatMarkerTrigger.MANUAL -> topSnapshotNames(bm.markers)
                    }
                    formatStopLine(bm, zones)
                }
            }
            val lines = if (portLine != null) listOf(portLine) + stopLines else stopLines

            // No marker strong enough to name the trip: the list of markers traversed is written
            // into the comment instead, so the journey is still described (settled 2026-10-10).
            val traversed = if (composeTripName(track.trackPoints) == null) {
                traversedMarkers(track.trackPoints)
            } else emptyList()
            val newComment = (if (traversed.isEmpty()) lines else lines + traversed.joinToString(", "))
                .joinToString("\n")
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
        titlePollJob = scope.launch {
            while (true) {
                delay(TITLE_POLL_INTERVAL_MS)
                recomputeDescription()
                pollTitle()
            }
        }
        markerObserverJob?.cancel()
        markerObserverJob = markerChangeNotifier?.let { flow ->
            scope.launch {
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
            // The markers the trip has met name it live, updated as the trip goes (settled
            // 2026-10-10); the stop tiers below are the fallback when nothing fused yet.
            composeTripName(track.trackPoints)?.let { composed ->
                if (track.name != composed) updateCurrentTrackMeta(name = composed)
                return
            }
            // A stop at the home port names nothing, whatever the tier (settled 2026-10-24).
            val markers = track.boatMarkers.filterNot { isHomePortPosition(it.boatLat, it.boatLon) }
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

    // ── Marker-zone naming (settled 2026-10-10) ──────────────────────────────

    /** The markers one point met, with the matcher's own score for each — the identity a dwell is
     *  credited to and the number the fusion weighs it by. An auto marker never names a trip. */
    private fun contactsAt(point: TrackPoint): List<MarkerContact> {
        val wia = whereAmI ?: return emptyList()
        return wia(LatLng(point.lat, point.lon)).allMatches.mapNotNull { match ->
            val marker = MarkerMatcher.markerOf(match)
            if (marker.origin == MarkerOrigin.IDLE_AUTO) null
            else MarkerContact(
                marker.id,
                markerLabel(marker.name, marker.icon),
                MarkerMatcher.sortScore(match)
            )
        }
    }

    /** The marker identifying one end: the smallest zone it stands inside or, failing that, the
     *  smallest zone it stands near — the one rule both kinds' ends share (2026-10-18). */
    private fun endAt(point: TrackPoint): TripEnd? {
        val wia = whereAmI ?: return null
        val top = MarkerMatcher.topNamingMatch(wia(LatLng(point.lat, point.lon)).allMatches)
            ?: return null
        val marker = MarkerMatcher.markerOf(top)
        return TripEnd(marker.id, markerLabel(marker.name, marker.icon))
    }

    /** The markers an idle stop stood in, ranked for the title's body: a dive marker leads, then the
     *  longest idle, and only an IDLE stop counts — a manual tap names the fallback title, never the
     *  body (2026-10-18). Each stop resolves through the ends' own smallest-zone rule. */
    private fun idleBodyMarkers(track: Track): List<TimedMarker> {
        val wia = whereAmI ?: return emptyList()
        data class IdleStop(val marker: TimedMarker, val dive: Boolean, val durationSec: Long)
        val now = System.currentTimeMillis()
        return track.boatMarkers
            .filter { it.trigger == BoatMarkerTrigger.IDLE }
            .mapNotNull { bm ->
                // A stop at the home port names nothing: the port's own marker never reaches the
                // body (settled 2026-10-24).
                if (isHomePortPosition(bm.boatLat, bm.boatLon)) return@mapNotNull null
                val result = wia(LatLng(bm.boatLat, bm.boatLon))
                val match = MarkerMatcher.topNamingMatch(result.allMatches)
                    ?: return@mapNotNull null
                val marker = MarkerMatcher.markerOf(match)
                IdleStop(
                    marker = TimedMarker(marker.id, markerLabel(marker.name, marker.icon), 0.0),
                    dive = hasDivingPinnedMarker(result),
                    durationSec = ((bm.endTimeMs ?: now) - bm.startTimeMs) / 1000
                )
            }
            .sortedWith(compareBy({ if (it.dive) 0 else 1 }, { -it.durationSec }))
            .map { it.marker }
    }

    /** The markers the trip met, in the fusion's own order — what the comment falls back to when
     *  no marker was strong enough to name the trip. A point inside the home-port circle names
     *  nothing, so the port's own marker never lands here (settled 2026-10-24). */
    private fun traversedMarkers(points: List<TrackPoint>): List<String> =
        TrackNaming.timedScores(points.filterNot { isHomePortPosition(it.lat, it.lon) }) { contactsAt(it) }
            .map { it.label }
            .filter { it.isNotBlank() }

    /**
     * The trip's name from its two ends and the idle stops between them, or null where nothing names
     * it (settled 2026-10-11). The body carries only markers an idle stop stood in — a marker merely
     * passed never names the title (2026-10-18) — while both ends stay positional: a shared marked
     * endpoint is not repeated, a distinct marked destination closes the name whatever its score, an
     * identified origin leads it, and an end that is the boat's own home port names nothing.
     */
    private fun composeTripName(points: List<TrackPoint>): String? {
        if (whereAmI == null) return null
        if (points.size < 2) return null
        val first = points.first()
        val last = points.last()
        val ranked = currentTrack?.let { idleBodyMarkers(it) } ?: emptyList()
        val samePlace = SpatialOperations.haversine(
            LatLng(first.lat, first.lon),
            LatLng(last.lat, last.lon)
        ) <= geofenceRadiusM
        // An end names the trip only when identified and not the home port. A home-port end keeps
        // its id — so the body still drops it as an end — but contributes no label (settled
        // 2026-10-11).
        val origin = endAt(first)?.unlessHomePort(first)
        val destination = if (samePlace) null else endAt(last)?.unlessHomePort(last)
        if (ranked.isEmpty() && origin == null && destination == null) return null
        val shape = TrackNaming.select(
            ranked = ranked,
            origin = origin ?: TripEnd(null, ""),
            destination = destination,
            leading = null
        )
        return TrackNaming.compose(
            origin = shape.origin,
            body = shape.body,
            destination = shape.destination,
            connector = nameConnector,
            maxLength = nameMaxLength
        ).takeIf { it.isNotBlank() }
    }

    /** The end blanked of its label when it is the home port, its id kept so the body drops it. */
    private fun TripEnd.unlessHomePort(point: TrackPoint): TripEnd =
        if (isHomePortPosition(point.lat, point.lon)) TripEnd(markerId, "") else this

    /**
     * Compute the final track title from BoatMarker history at finalize time.
     * Uses the 3-tier priority: 🤿 diving > MANUAL > IDLE duration.
     */
    private fun computeFinalTitle(track: Track): String? {
        try {
            val wia = whereAmI ?: return null
            // A stop at the home port names nothing, whatever the tier (settled 2026-10-24).
            val stops = track.boatMarkers.filterNot { isHomePortPosition(it.boatLat, it.boatLon) }
            if (stops.isEmpty()) return null

            data class NamedStop(val name: String, val tier: Int, val durationSec: Long, val icon: String?)
            // tier: 1 = diving, 2 = manual, 3 = idle

            val now = System.currentTimeMillis()
            val namedStops = stops.mapNotNull { bm ->
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
