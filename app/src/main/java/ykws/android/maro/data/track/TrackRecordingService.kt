package ykws.android.maro.data.track

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import ykws.android.maro.MainActivity
import ykws.android.maro.R
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.location.AcquisitionMode
import ykws.android.maro.data.location.AdaptiveGpsPolicy
import ykws.android.maro.data.location.GpsFix
import ykws.android.maro.data.location.GpsLocationSource
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.settings.SettingsManager
import ykws.android.maro.spatial.WhereAmIResult
import java.io.File

private const val TAG = "MaroII_TrackService"

/**
 * Foreground service that keeps Maro II alive when backgrounded.
 *
 * Lifecycle:
 * - Started via [startForegroundService] when the app opens ([MainActivity.onCreate]).
 * - Stopped via [stopService] when the user explicitly exits (double-back).
 * - Always shows a low-importance notification with a 5-segment collapsed title
 *   and an InboxStyle expanded view with stat rows.
 *
 * ## Recording ownership
 * The [TrackRecorder] and its GPS [TrackSample] assembly live here — not in
 * [TrackViewModel] — so recording survives Activity destruction / task removal.
 * [TrackViewModel] observes the companion [uiState]/[events]/[newPoint] flows.
 * Start/stop/discard/resume routing arrives via service intents.
 *
 * ## Tasker integration
 * Stores the boat's water-state ([lastKnownOnWater]) from incoming [ACTION_UPDATE]
 * intents. On toggle, fires [ACTION_WATER_STATE_CHANGED] so Tasker can react
 * immediately. Also answers [ACTION_QUERY_WATER_STATE] on demand.
 */
class TrackRecordingService : Service() {

    /** Last known boat water state — persisted across Activity lifecycle for query support. */
    private var lastKnownOnWater: Boolean = false

    /** True when orphaned checkpoint files exist in filesDir/tracks/ (lightweight scan, no protobuf I/O). */
    private var hasOrphans: Boolean = false

    /** Dynamically registered receiver for [ACTION_QUERY_WATER_STATE]. */
    private var waterQueryReceiver: BroadcastReceiver? = null

    /** Independent settings reader (same SharedPreferences as NavigationViewModel). */
    private val settingsManager by lazy { SettingsManager(this) }

    /** Track persistence layer. */
    private val repository by lazy { TrackRepository(this) }

    /** Process-scoped recording engine coroutines (GPS sampling + flow forwarding). */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Raw GPS source for the recorder — independent of the UI's NavigationViewModel pipeline. */
    private val gpsSource by lazy { GpsLocationSource(this) }

    /** Stationary detector for the service-owned GPS stream. */
    private val adaptivePolicy = AdaptiveGpsPolicy()

    /** True when the UI reports demo (non-GPS) mode. Gates the service GPS listener. */
    private val demoMode = MutableStateFlow(false)

    /** Service-owned recorder (null until [ensureRecorder] runs). */
    private var recorder: TrackRecorder? = null

    /** GPS sampling job (cancelled on destroy or when demo mode activates). */
    private var gpsJob: Job? = null

    /** Authoritative recording flag — mirrors the recorder's live state synchronously. */
    private val recordingNow: Boolean
        get() = recorder?.uiState?.value?.state == TrackRecorderState.ON

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // Restore persisted water state (survives process death)
        val prefs = getSharedPreferences("maro_service_prefs", Context.MODE_PRIVATE)
        lastKnownOnWater = prefs.getBoolean("pref_last_water_state", false)

        // Lightweight orphan checkpoint scan (no protobuf deserialization)
        val trackDir = File(filesDir, "tracks")
        hasOrphans = trackDir.listFiles(java.io.FileFilter { it.extension == "checkpoint" })?.isNotEmpty() == true

        // Register query receiver so Tasker can poll water state on demand
        waterQueryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == ACTION_QUERY_WATER_STATE) {
                    val result = Intent(ACTION_WATER_STATE_RESULT).apply {
                        putExtra(EXTRA_ON_WATER, lastKnownOnWater)
                    }
                    sendBroadcast(result)
                }
            }
        }
        registerReceiver(waterQueryReceiver, IntentFilter(ACTION_QUERY_WATER_STATE),
            Context.RECEIVER_EXPORTED)

        demoMode.value = !settingsManager.settings.value.gpsMode
        ensureRecorder()
    }

    override fun onDestroy() {
        waterQueryReceiver?.let { unregisterReceiver(it) }
        waterQueryReceiver = null
        serviceScope.cancel()
        recorder?.dispose()
        recorder = null
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Rule A: notification disappears when the task is gone and nothing is recording.
        if (!recordingNow) stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_RECORDING -> {
                ensureRecorder()
                recorder?.startManual()
            }
            ACTION_STOP_RECORDING -> recorder?.stop()
            ACTION_DISCARD_RECORDING -> recorder?.discard()
            ACTION_RESUME_ORPHANED_CHECKPOINT -> resumeOrphanedCheckpoint(intent)
            ACTION_RESUME_TRACK -> resumeTrack(intent)
            ACTION_ADD_MANUAL_BOAT_MARKER -> addManualBoatMarker(intent)
            ACTION_SET_ACTIVE_SESSION_AUTO_MARKER_ID ->
                recorder?.setActiveSessionAutoMarkerId(intent.getStringExtra(EXTRA_AUTO_MARKER_ID).orEmpty())
            ACTION_SET_BOAT_MARKER_AUTO_MARKER_ID ->
                recorder?.setBoatMarkerAutoMarkerId(intent.getStringExtra(EXTRA_BOAT_MARKER_AUTO_MARKER_ID).orEmpty())
            ACTION_UPDATE_LIVE_TRACK_META -> recorder?.updateCurrentTrackMeta(
                name = if (intent.hasExtra(EXTRA_TRACK_NAME)) intent.getStringExtra(EXTRA_TRACK_NAME) else null,
                comment = if (intent.hasExtra(EXTRA_TRACK_COMMENT)) intent.getStringExtra(EXTRA_TRACK_COMMENT) else null
            )
            ACTION_CLEAR_INFO_ERROR -> recorder?.clearInfoError()
        }

        // ── Water state update (may ride along with notification update or arrive standalone) ──
        if (intent != null && intent.hasExtra(EXTRA_ON_WATER)) {
            val newOnWater = intent.getBooleanExtra(EXTRA_ON_WATER, false)
            if (newOnWater != lastKnownOnWater) {
                lastKnownOnWater = newOnWater
                // Persist across process death
                getSharedPreferences("maro_service_prefs", Context.MODE_PRIVATE)
                    .edit().putBoolean("pref_last_water_state", newOnWater).apply()
                // Push broadcast to Tasker on every land↔water toggle
                sendBroadcast(Intent(ACTION_WATER_STATE_CHANGED).apply {
                    putExtra(EXTRA_ON_WATER, newOnWater)
                })
            }
        }

        // ── Demo-mode tracking: gate the service GPS listener on/off ────────
        if (intent != null && intent.hasExtra(EXTRA_IS_DEMO)) {
            demoMode.value = intent.getBooleanExtra(EXTRA_IS_DEMO, false)
        }

        val notification = buildNotification(intent, lastKnownOnWater, recording = recordingNow)
        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Recorder lifecycle (service-owned) ────────────────────────────────

    /** Create and start the recorder (OFF state, collecting samples) if not already active. */
    private fun ensureRecorder() {
        if (recorder != null) return
        val s = settingsManager.settings.value
        val rec = TrackRecorder(
            repository = repository,
            gpsMode = s.gpsMode,
            maxRecordingAccuracyM = s.maxRecordingAccuracyM,
            geofenceOriginLat = s.trackOriginLat,
            geofenceOriginLon = s.trackOriginLon,
            geofenceRadiusM = s.trackGeofenceRadiusM,
            geofenceEnabled = s.trackGeofenceEnabled,
            isStopped = _stopped,
            simplifyEnabled = s.trackSimplifyEnabled,
            simplifyEpsilonM = s.trackSimplifyEpsilonM,
            simplifySpeedDeltaKn = s.trackSimplifySpeedDeltaKn,
            idleThresholdSec = AppConfig.boatMarkerIdleThresholdSec,
            idleThresholdCallback = object : IdleThresholdCallback {
                override suspend fun onIdleThresholdReached(position: LatLng): IdleCaptureResult =
                    WhereAmIProvider.idleCapture?.invoke(position)
                        ?: IdleCaptureResult(emptyList(), false)
            },
            whereAmI = { pos ->
                WhereAmIProvider.whereAmI?.invoke(pos) ?: WhereAmIResult(emptyList())
            },
            markerChangeNotifier = WhereAmIProvider.markerChanges,
            gapDistanceThresholdM = AppConfig.trackingGapDistanceThresholdM,
            gapTimeThresholdSec = AppConfig.trackingGapTimeThresholdSec
        )
        recorder = rec
        rec.start(_sampleInput)
        serviceScope.launch {
            rec.uiState.collect { state ->
                _uiState.value = state
                _isRecording.value = state.state == TrackRecorderState.ON
            }
        }
        serviceScope.launch { rec.events.collect { _events.tryEmit(it) } }
        serviceScope.launch { rec.newPoint.collect { _newPoint.tryEmit(it) } }
        startGpsSampling()
    }

    /** Assemble GPS [TrackSample]s from a service-owned LocationManager listener. */
    private fun startGpsSampling() {
        gpsJob?.cancel()
        gpsJob = serviceScope.launch {
            demoMode
                .flatMapLatest { demo ->
                    if (demo) emptyFlow<GpsFix>() else gpsSource.locationUpdates(1_000L, 1f)
                }
                .catch { e -> Log.w(TAG, "GPS sampling failed", e) }
                .collect { fix ->
                    val s = settingsManager.settings.value
                    val mode = adaptivePolicy.onFix(
                        SystemClock.elapsedRealtime(),
                        fix.position,
                        s.stopDetectionTimeSec * 1_000L,
                        s.stopDetectionDistanceM.toDouble(),
                        fix.speedMps,
                        fix.accuracyM
                    )
                    _stopped.value = mode == AcquisitionMode.IDLE
                    _sampleInput.tryEmit(
                        TrackSample(
                            position = fix.position,
                            speedMps = fix.speedMps,
                            bearingDeg = fix.bearingDeg,
                            hasLock = fix.hasLock,
                            timestampEpochMs = fix.timestampEpochMs,
                            accuracyM = fix.accuracyM
                        )
                    )
                }
        }
    }

    private fun resumeOrphanedCheckpoint(intent: Intent) {
        ensureRecorder()
        val id = intent.getStringExtra(EXTRA_TRACK_ID) ?: return
        serviceScope.launch {
            val track = repository.recoverOrphanedCheckpoints().firstOrNull { it.id == id } ?: return@launch
            recorder?.resume(track, _sampleInput, fromCheckpoint = true)
        }
    }

    private fun resumeTrack(intent: Intent) {
        ensureRecorder()
        val id = intent.getStringExtra(EXTRA_TRACK_ID) ?: return
        serviceScope.launch {
            val track = repository.load(id) ?: return@launch
            if (!track.visibleOnMap) {
                repository.save(track.copy(visibleOnMap = true))
            }
            recorder?.resume(track, _sampleInput, fromCheckpoint = false)
        }
    }

    private fun addManualBoatMarker(intent: Intent) {
        val json = intent.getStringExtra(EXTRA_MARKER_SNAPSHOTS_JSON) ?: return
        try {
            val snapshots: List<MarkerSnapshot> = Json.decodeFromString<List<MarkerSnapshot>>(json)
            recorder?.addManualBoatMarker(snapshots)
        } catch (e: Exception) {
            Log.w(TAG, "addManualBoatMarker decode failed", e)
        }
    }

    // ── Notification building ──────────────────────────────────────────────

    private fun buildNotification(intent: Intent?, isOnWater: Boolean, recording: Boolean? = null): Notification {
        val isRecording = recording ?: (intent?.getBooleanExtra(EXTRA_RECORDING, false) ?: false)
        val isDemo = intent?.getBooleanExtra(EXTRA_IS_DEMO, false) ?: false
        val isMoving = intent?.getBooleanExtra(EXTRA_IS_MOVING, false) ?: false
        val speedKn = intent?.getFloatExtra(EXTRA_SPEED_KN, 0f) ?: 0f
        val elapsedSec = intent?.getLongExtra(EXTRA_ELAPSED_SEC, 0L) ?: 0L
        val distanceNm = intent?.getFloatExtra(EXTRA_DISTANCE_NM, 0f) ?: 0f
        val idleSec = intent?.getLongExtra(EXTRA_IDLE_SEC, 0L) ?: 0L
        val avgSpeedKn = intent?.getFloatExtra(EXTRA_AVG_SPEED_KN, 0f) ?: 0f
        val maxSpeedKn = intent?.getFloatExtra(EXTRA_MAX_SPEED_KN, 0f) ?: 0f
        val pointCount = intent?.getIntExtra(EXTRA_POINT_COUNT, 0) ?: 0

        // 5-segment title: "Maro II • [GPS|Demo] • [Navigating|Idle|Moving] • [Recording|Ready] • [On Water|On Land]"
        val modeLabel = if (isDemo) "Demo" else "GPS"
        val recLabel = if (isRecording) "Recording" else if (hasOrphans) "Recovery available" else "Ready"
        val navLabel = when {
            !isMoving -> "Idle"
            isOnWater -> "Navigating"
            else -> "Moving"
        }
        val waterLabel = if (isOnWater) "On Water" else "On Land"
        val title = "Maro II • $modeLabel • $navLabel • $recLabel • $waterLabel"

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Maro II")
            .setContentText(title)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)

        // ── Stop action when recording ──────────────────────────────────────
        if (isRecording) {
            val stopIntent = Intent(this@TrackRecordingService, StopRecordingReceiver::class.java).apply {
                action = ACTION_STOP_RECORDING
            }
            val stopPendingIntent = PendingIntent.getBroadcast(
                this, 0, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                NotificationCompat.Action.Builder(
                    0,
                    "Stop",
                    stopPendingIntent
                ).build()
            )
        }

        // Tap notification → open MainActivity (SINGLE_TOP | CLEAR_TOP avoids duplicate)
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.setContentIntent(pendingIntent)

        return builder.build()
    }

    // ── Channel / formatting ───────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = CHANNEL_DESC
                setSound(null, null)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    // ── Constants ──────────────────────────────────────────────────────────

    companion object {
        private const val CHANNEL_ID = "maro_persistent"
        private const val CHANNEL_NAME = "Maro II"
        private const val CHANNEL_DESC = "Persistent notification while Maro II is running"
        private const val NOTIFICATION_ID = 1001

        /** Intent action: update the foreground notification with current recording stats. */
        const val ACTION_UPDATE = "ykws.android.maro.action.UPDATE_NOTIFICATION"

        /** Intent action: stop recording from notification action button. */
        const val ACTION_STOP_RECORDING = "ykws.android.maro.action.STOP_RECORDING"

        /** Intent action: start recording. */
        const val ACTION_START_RECORDING = "ykws.android.maro.action.START_RECORDING"

        /** Intent action: discard the in-progress recording. */
        const val ACTION_DISCARD_RECORDING = "ykws.android.maro.action.DISCARD_RECORDING"

        /** Intent action: resume an orphaned checkpoint. */
        const val ACTION_RESUME_ORPHANED_CHECKPOINT = "ykws.android.maro.action.RESUME_ORPHANED_CHECKPOINT"

        /** Intent action: resume a finalized track. */
        const val ACTION_RESUME_TRACK = "ykws.android.maro.action.RESUME_TRACK"

        /** Intent action: append manual BoatMarker snapshots to the live track. */
        const val ACTION_ADD_MANUAL_BOAT_MARKER = "ykws.android.maro.action.ADD_MANUAL_BOAT_MARKER"

        /** Intent action: set the 🕐 auto-marker ID on the active idle session. */
        const val ACTION_SET_ACTIVE_SESSION_AUTO_MARKER_ID = "ykws.android.maro.action.SET_ACTIVE_SESSION_AUTO_MARKER_ID"

        /** Intent action: store the confirmed auto-marker ID in the track's BoatMarker entry. */
        const val ACTION_SET_BOAT_MARKER_AUTO_MARKER_ID = "ykws.android.maro.action.SET_BOAT_MARKER_AUTO_MARKER_ID"

        /** Intent action: update the live track's name/comment. */
        const val ACTION_UPDATE_LIVE_TRACK_META = "ykws.android.maro.action.UPDATE_LIVE_TRACK_META"

        /** Intent action: clear the track info error. */
        const val ACTION_CLEAR_INFO_ERROR = "ykws.android.maro.action.CLEAR_INFO_ERROR"

        // ── Service-owned recording state (observed by TrackViewModel/UI) ──
        private val _uiState = MutableStateFlow(TrackRecorderUiState())
        val uiState: StateFlow<TrackRecorderUiState> = _uiState.asStateFlow()

        private val _isRecording = MutableStateFlow(false)
        val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

        private val _stopped = MutableStateFlow(false)

        private val _events = MutableSharedFlow<TrackEvent>(extraBufferCapacity = 64)
        val events: SharedFlow<TrackEvent> = _events.asSharedFlow()

        private val _newPoint = MutableSharedFlow<TrackPoint>(extraBufferCapacity = 64)
        val newPoint: SharedFlow<TrackPoint> = _newPoint.asSharedFlow()

        private val _sampleInput = MutableSharedFlow<TrackSample>(extraBufferCapacity = 64)

        /** Push a UI-assembled demo-mode sample into the service-owned recorder. */
        fun pushSample(sample: TrackSample) {
            _sampleInput.tryEmit(sample)
        }

        /** Update the stationary flag from the UI (demo mode only). */
        fun updateStopped(value: Boolean) {
            _stopped.value = value
        }

        const val EXTRA_RECORDING = "recording"
        const val EXTRA_IS_DEMO = "is_demo"

        // Always-sent extras
        const val EXTRA_IS_MOVING = "is_moving"
        const val EXTRA_SPEED_KN = "speed_kn"
        const val EXTRA_ON_WATER = "on_water"

        // Recording-only extras
        const val EXTRA_ELAPSED_SEC = "elapsed_sec"
        const val EXTRA_DISTANCE_NM = "distance_nm"
        const val EXTRA_IDLE_SEC = "idle_sec"
        const val EXTRA_AVG_SPEED_KN = "avg_speed_kn"
        const val EXTRA_MAX_SPEED_KN = "max_speed_kn"
        const val EXTRA_POINT_COUNT = "point_count"

        // Recording-control extras
        const val EXTRA_TRACK_ID = "track_id"
        const val EXTRA_MARKER_SNAPSHOTS_JSON = "marker_snapshots_json"
        const val EXTRA_AUTO_MARKER_ID = "auto_marker_id"
        const val EXTRA_BOAT_MARKER_AUTO_MARKER_ID = "boat_marker_auto_marker_id"
        const val EXTRA_TRACK_NAME = "track_name"
        const val EXTRA_TRACK_COMMENT = "track_comment"

        // ── Tasker water-state integration ─────────────────────────────────────

        /** Push broadcast: fired when boat water state toggles (land↔water). */
        const val ACTION_WATER_STATE_CHANGED = "ykws.android.maro.action.WATER_STATE_CHANGED"

        /** Query broadcast: Tasker sends this to poll current water state. */
        const val ACTION_QUERY_WATER_STATE = "ykws.android.maro.action.QUERY_WATER_STATE"

        /** Query response: Maro II answers with [EXTRA_ON_WATER]. */
        const val ACTION_WATER_STATE_RESULT = "ykws.android.maro.action.WATER_STATE_RESULT"
    }
}
