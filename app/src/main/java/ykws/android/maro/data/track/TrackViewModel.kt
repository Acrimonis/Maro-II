package ykws.android.maro.data.track

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ykws.android.maro.data.model.ListFilter
import ykws.android.maro.data.model.ListSortField
import ykws.android.maro.data.model.ListSortState
import ykws.android.maro.data.model.matchesFilter
import ykws.android.maro.data.model.todayMidnightMs
import ykws.android.maro.data.settings.AppSettings
import ykws.android.maro.data.settings.SettingsManager

/**
 * ViewModel bridge between [TrackRecorder] / [TrackRepository] and the Compose UI.
 *
 * Observes settings changes, manages recorder lifecycle, and exposes StateFlows
 * for the UI layer.
 */
class TrackViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TrackRepository(application)
    private val settingsManager = ykws.android.maro.data.settings.SettingsManager(
        application, ykws.android.maro.config.AppConfig.zoneAutoRevealDistanceM,
        ykws.android.maro.config.AppConfig.zoneAutoRevealTimeS,
        ykws.android.maro.config.AppConfig.overlayLowDepthMinOpacity
    )

    private var recorder: TrackRecorder? = null

    private val _uiState = MutableStateFlow(TrackRecorderUiState())
    val uiState: StateFlow<TrackRecorderUiState> = _uiState.asStateFlow()

    /** Incoming track sample stream — MapScreen pushes position data here. */
    private val _trackSample = MutableSharedFlow<TrackSample>(extraBufferCapacity = 8)
    val trackSample: SharedFlow<TrackSample> = _trackSample.asSharedFlow()

    /** Recorder event stream — MapScreen observes for idle/marker events.
     *  Persistent flow that forwards from whatever recorder is active. */
    private val _events = MutableSharedFlow<TrackEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<TrackEvent> = _events.asSharedFlow()
    private var eventsForwardingJob: kotlinx.coroutines.Job? = null

    /** Unfiltered source of truth — reloaded from repository. */
    private val _allSummaries = MutableStateFlow<List<TrackSummary>>(emptyList())

    private val _summaries = MutableStateFlow<List<TrackSummary>>(emptyList())
    val summaries: StateFlow<List<TrackSummary>> = _summaries.asStateFlow()

    /** Accessor for the recorder's incremental new-point stream (null when recorder isn't active). */
    val newPointStream: SharedFlow<TrackPoint>?
        get() = recorder?.newPoint

    // Recovery state — non-null when an orphaned checkpoint is found
    private val _recoveryTrack = MutableStateFlow<Track?>(null)
    val recoveryTrack: StateFlow<Track?> = _recoveryTrack.asStateFlow()

    /** Source of truth for isStopped — set once by MapScreen from NavigationViewModel. */
    private var stoppedSource: StateFlow<Boolean> = MutableStateFlow(false)

    /** Cached sample flow reference for use by resumeOrphanedCheckpoint. */
    private var cachedSampleFlow: Flow<TrackSample>? = null
    /** Cached settings from last startRecorder call, for use by resumeOrphanedCheckpoint. */
    private var cachedSettings: AppSettings? = null

    init {
        viewModelScope.launch {
            recoverOrphanedCheckpoints()
        }
        refreshSummaries()
        viewModelScope.launch {
            settingsManager.settings.collect { settings ->
                val midnightMs = todayMidnightMs()
                val filtered = _allSummaries.value.filter { it.matchesFilter(settings.trackListFilter, midnightMs) }
                _summaries.value = sortSummaries(filtered, settings.trackListSort)
            }
        }
    }

    /**
     * Push a position sample into the recording pipeline.
     * Called by MapScreen from the TrackSample combine flow.
     */
    fun pushTrackSample(sample: TrackSample) {
        _trackSample.tryEmit(sample)
    }

    /**
     * Set the source-of-truth for boat-stopped state.
     * Called once by MapScreen from NavigationViewModel.isStopped.
     */
    fun setStoppedSource(flow: StateFlow<Boolean>) {
        stoppedSource = flow
        recorder?.let { rec ->
            // Re-create recorder with updated isStopped source (only happens before pipeline start)
        }
    }

    /**
     * Start the recorder with a TrackSample flow and current settings.
     * Call once when the GPS pipeline is ready.
     */
    fun startRecorder(
        sampleFlow: Flow<TrackSample>,
        settings: AppSettings,
        idleThresholdCallback: IdleThresholdCallback? = null,
        whereAmI: ((ykws.android.maro.data.model.LatLng) -> ykws.android.maro.spatial.WhereAmIResult)? = null,
        markerChangeNotifier: Flow<Unit>? = null
    ) {
        cachedSampleFlow = sampleFlow
        cachedSettings = settings
        stopRecorder()
        val rec = TrackRecorder(
            repository = repository,
            gpsMode = settings.gpsMode,
            geofenceOriginLat = settings.trackOriginLat,
            geofenceOriginLon = settings.trackOriginLon,
            geofenceRadiusM = settings.trackGeofenceRadiusM,
            geofenceEnabled = settings.trackGeofenceEnabled,
            isStopped = stoppedSource,
            simplifyEnabled = settings.trackSimplifyEnabled,
            simplifyEpsilonM = settings.trackSimplifyEpsilonM,
            simplifySpeedDeltaKn = settings.trackSimplifySpeedDeltaKn,
            idleThresholdSec = ykws.android.maro.config.AppConfig.boatMarkerIdleThresholdSec,
            idleThresholdCallback = idleThresholdCallback,
            whereAmI = whereAmI,
            markerChangeNotifier = markerChangeNotifier
        )
        recorder = rec
        rec.start(sampleFlow)
        // Wire activeRecorder for notification stop action
        TrackRecordingService.activeRecorder = rec
        viewModelScope.launch {
            rec.uiState.collect { state ->
                _uiState.value = state
            }
        }
        // Forward recorder events to persistent flow so MapScreen sees them
        eventsForwardingJob?.cancel()
        eventsForwardingJob = viewModelScope.launch {
            rec.events.collect { _events.emit(it) }
        }
    }

    /** Stop the recorder and release resources. */
    fun stopRecorder() {
        eventsForwardingJob?.cancel()
        eventsForwardingJob = null
        TrackRecordingService.activeRecorder = null
        recorder?.dispose()
        recorder = null
    }

    /** Manually start recording. Lazily creates the recorder if not yet initialised. */
    fun startRecording() {
        if (recorder == null) {
            initRecorder()
        }
        recorder?.startManual()
    }

    /** Manually stop recording (finalizes current track). */
    fun stopRecording() {
        if (recorder == null) {
            initRecorder()
        }
        val trackId = _uiState.value.currentTrackId
        recorder?.stop()
        TrackRecordingService.activeRecorder = null
        if (trackId != null) invalidateTrackCache(trackId)
        viewModelScope.launch {
            kotlinx.coroutines.delay(500)
            refreshSummaries()
        }
    }

    private fun initRecorder(settings: ykws.android.maro.data.settings.AppSettings? = null) {
        val rec = TrackRecorder(
            repository = repository,
            gpsMode = settings?.gpsMode ?: true,
            geofenceOriginLat = settings?.trackOriginLat ?: 43.55,
            geofenceOriginLon = settings?.trackOriginLon ?: 7.00,
            geofenceRadiusM = settings?.trackGeofenceRadiusM ?: 500.0,
            geofenceEnabled = settings?.trackGeofenceEnabled ?: true,
            isStopped = stoppedSource,
            simplifyEnabled = settings?.trackSimplifyEnabled ?: true,
            simplifyEpsilonM = settings?.trackSimplifyEpsilonM ?: 3.0,
            simplifySpeedDeltaKn = settings?.trackSimplifySpeedDeltaKn ?: 3.0
        )
        rec.start(kotlinx.coroutines.flow.emptyFlow())
        recorder = rec
        TrackRecordingService.activeRecorder = rec
        viewModelScope.launch {
            rec.uiState.collect { state ->
                _uiState.value = state
            }
        }
    }

    /** Reload track summaries, mark active track as [ListableItem.isLive]. */
    fun refreshSummaries(sortState: ListSortState? = null) {
        viewModelScope.launch {
            val settings = settingsManager.settings.value
            val effectiveSort = sortState ?: settings.trackListSort
            val summaries = repository.listTracks()
            // Mark the active track: most recent summary with no endTimeMs
            summaries.firstOrNull { it.endTimeMs == null }?.isLive = true
            _allSummaries.value = summaries
            val midnightMs = todayMidnightMs()
            val filtered = summaries.filter { it.matchesFilter(settings.trackListFilter, midnightMs) }
            _summaries.value = sortSummaries(filtered, effectiveSort)
        }
    }

    /** Apply [ListSortOrder] to a list of [TrackSummary]. */
    private fun sortSummaries(
        summaries: List<TrackSummary>,
        state: ListSortState
    ): List<TrackSummary> {
        val nowMs = System.currentTimeMillis()
        return state.applySort(summaries) { key ->
            when (key) {
                "distanceNm" -> compareBy { it.distanceNm }
                "totalTimeSec" -> compareBy { (it.endTimeMs ?: nowMs) - it.startTimeMs }
                "movingTimeSec" -> compareBy { s ->
                    val totalMs = (s.endTimeMs ?: nowMs) - s.startTimeMs
                    totalMs - s.idleDurationSec * 1000L
                }
                else -> null  // fallback to updatedAtEpochMs
            }
        }
    }

    // ── LRU track detail cache for overlay rendering ────────────────────
    // Prevents repeated protobuf deserialization when refreshing the map overlay.
    private val trackDetailCache = object : LinkedHashMap<String, Track>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Track>): Boolean =
            size > 30
    }

    /** Load a full track by ID with LRU caching. */
    suspend fun loadTrackDetailCached(id: String): Track? {
        synchronized(trackDetailCache) {
            trackDetailCache[id]?.let { return it }
        }
        val track = loadTrackDetail(id)
        if (track != null) {
            synchronized(trackDetailCache) {
                trackDetailCache[id] = track
            }
        }
        return track
    }

    /** Invalidate a single cache entry (call after update/delete). */
    fun invalidateTrackCache(id: String) {
        synchronized(trackDetailCache) { trackDetailCache.remove(id) }
    }

    /** Load a full track by ID for detail view. */
    suspend fun loadTrackDetail(id: String): Track? = repository.load(id)

    /** Delete a track by ID. */
    fun deleteTrack(id: String) {
        invalidateTrackCache(id)
        viewModelScope.launch {
            repository.delete(id)
            refreshSummaries()
        }
    }

    /** Update track metadata. */
    fun updateTrack(id: String, name: String? = null, comment: String? = null) {
        invalidateTrackCache(id)
        viewModelScope.launch {
            repository.updateMetadata(id, name, comment)
            refreshSummaries()
        }
    }

    /** Toggle the pinned flag on a track. */
    fun setPinned(id: String, pinned: Boolean) {
        invalidateTrackCache(id)
        viewModelScope.launch {
            repository.setPinned(id, pinned)
            refreshSummaries()
        }
    }

    /** Add manual BoatMarker snapshots to the current track. */
    fun addManualBoatMarker(snapshots: List<MarkerSnapshot>) {
        recorder?.addManualBoatMarker(snapshots)
    }

    /** Set the auto-marker ID on the active idle session. */
    fun setActiveSessionAutoMarkerId(id: String) {
        recorder?.setActiveSessionAutoMarkerId(id)
    }

    /** Store the confirmed auto-marker ID in the track's BoatMarker entry. */
    fun setBoatMarkerAutoMarkerId(id: String) {
        recorder?.setBoatMarkerAutoMarkerId(id)
    }

    /**
     * Resume an orphaned checkpoint as a live recording (Continue button in recovery dialog).
     *
     * Re-initializes the TrackRecorder with the checkpointed track data,
     * restarts the sampleFlow pipeline, and appends new points to the existing track.
     * If the time/distance gap exceeds the threshold, a GAP marker is inserted.
     */
    fun resumeOrphanedCheckpoint(track: Track) {
        _recoveryTrack.value = null
        val sampleFlow = cachedSampleFlow ?: return
        stopRecorder()

        // Load gap thresholds from AppConfig (falls back to defaults)
        val gapDistM = ykws.android.maro.config.AppConfig.trackingGapDistanceThresholdM
        val gapTimeSec = ykws.android.maro.config.AppConfig.trackingGapTimeThresholdSec

        val s = cachedSettings
        val rec = TrackRecorder(
            repository = repository,
            gpsMode = s?.gpsMode ?: true,
            geofenceOriginLat = s?.trackOriginLat ?: 43.55,
            geofenceOriginLon = s?.trackOriginLon ?: 7.00,
            geofenceRadiusM = s?.trackGeofenceRadiusM ?: 500.0,
            geofenceEnabled = s?.trackGeofenceEnabled ?: true,
            isStopped = stoppedSource,
            simplifyEnabled = s?.trackSimplifyEnabled ?: true,
            simplifyEpsilonM = s?.trackSimplifyEpsilonM ?: 3.0,
            simplifySpeedDeltaKn = s?.trackSimplifySpeedDeltaKn ?: 3.0,
            idleThresholdSec = ykws.android.maro.config.AppConfig.boatMarkerIdleThresholdSec,
            gapDistanceThresholdM = gapDistM,
            gapTimeThresholdSec = gapTimeSec
        )
        recorder = rec
        // Set up collectors BEFORE resume() so Resumed event is captured
        eventsForwardingJob?.cancel()
        eventsForwardingJob = viewModelScope.launch {
            rec.events.collect { _events.emit(it) }
        }
        viewModelScope.launch {
            rec.uiState.collect { state ->
                _uiState.value = state
            }
        }
        TrackRecordingService.activeRecorder = rec
        rec.resume(track, sampleFlow)
        // Emit directly to ViewModel events so MapScreen restores polyline
        if (track.trackPoints.isNotEmpty()) {
            _events.tryEmit(ykws.android.maro.data.track.TrackEvent.Resumed(track.trackPoints))
        }
        viewModelScope.launch {
            kotlinx.coroutines.delay(500)
            refreshSummaries()
        }
    }

    /**
     * Resume a finalized track as a live recording.
     *
     * Loads the finalized track, forces visibleOnMap, re-initializes the TrackRecorder
     * with fromCheckpoint=false, and appends new points. The inter-session gap is
     * tracked (resumeGapDurationSec) and excluded from navigating duration at finalize.
     */
    fun resumeTrack(trackId: String) {
        // Guard: cannot resume while already recording
        if (_uiState.value.state == TrackRecorderState.ON) {
            android.util.Log.w("MaroII_TrackVM", "resumeTrack: already recording")
            return
        }
        viewModelScope.launch {
            // 1. Load and validate the track
            val track = repository.load(trackId) ?: return@launch
            if (track.endTimeMs == null) {
                android.util.Log.w("MaroII_TrackVM", "resumeTrack: track $trackId is not finalized")
                return@launch
            }
            // Force visible (D7)
            if (!track.visibleOnMap) {
                repository.save(track.copy(visibleOnMap = true))
            }

            // 2. Stop any existing recorder
            val sampleFlow = cachedSampleFlow ?: return@launch
            stopRecorder()

            // 3-4. Build new recorder (same params as resumeOrphanedCheckpoint)
            val s = cachedSettings
            val gapDistM = ykws.android.maro.config.AppConfig.trackingGapDistanceThresholdM
            val gapTimeSec = ykws.android.maro.config.AppConfig.trackingGapTimeThresholdSec
            val rec = TrackRecorder(
                repository = repository,
                gpsMode = s?.gpsMode ?: true,
                geofenceOriginLat = s?.trackOriginLat ?: 43.55,
                geofenceOriginLon = s?.trackOriginLon ?: 7.00,
                geofenceRadiusM = s?.trackGeofenceRadiusM ?: 500.0,
                geofenceEnabled = s?.trackGeofenceEnabled ?: true,
                isStopped = stoppedSource,
                simplifyEnabled = s?.trackSimplifyEnabled ?: true,
                simplifyEpsilonM = s?.trackSimplifyEpsilonM ?: 3.0,
                simplifySpeedDeltaKn = s?.trackSimplifySpeedDeltaKn ?: 3.0,
                idleThresholdSec = ykws.android.maro.config.AppConfig.boatMarkerIdleThresholdSec,
                gapDistanceThresholdM = gapDistM,
                gapTimeThresholdSec = gapTimeSec
            )
            recorder = rec

            // 5-6. Wire events + UI state
            eventsForwardingJob?.cancel()
            eventsForwardingJob = viewModelScope.launch {
                rec.events.collect { _events.emit(it) }
            }
            viewModelScope.launch {
                rec.uiState.collect { state ->
                    _uiState.value = state
                }
            }

            // 7. Register with foreground service
            TrackRecordingService.activeRecorder = rec

            // 8. Resume with fromCheckpoint=false
            rec.resume(track, sampleFlow, fromCheckpoint = false)

            // 9. Emit Resumed + refresh summaries
            if (track.trackPoints.isNotEmpty()) {
                _events.tryEmit(ykws.android.maro.data.track.TrackEvent.Resumed(track.trackPoints))
            }
            viewModelScope.launch {
                kotlinx.coroutines.delay(500)
                refreshSummaries()
            }
        }
    }

    /**
     * Merge multiple finalized tracks into a single new track.
     *
     * Loads all tracks by ID, validates they are finalized, merges via [TrackMerger],
     * saves the result, and optionally deletes the originals.
     */
    fun mergeTracks(trackIds: Set<String>, mergedName: String, keepOriginals: Boolean) {
        viewModelScope.launch {
            val tracks = trackIds.mapNotNull { repository.load(it) }
                .filter { it.endTimeMs != null && it.trackPoints.isNotEmpty() }
                .sortedBy { it.startTimeMs }
            if (tracks.size < 2) return@launch

            val merger = TrackMerger()
            val merged = merger.merge(tracks, mergedName)

            repository.save(merged)

            if (!keepOriginals) {
                trackIds.forEach { id ->
                    invalidateTrackCache(id)
                    repository.delete(id)
                }
            }

            refreshSummaries()
            _events.emit(TrackEvent.TracksMerged(merged.id, merged.name))
        }
    }

    /** Resolve orphaned checkpoint: save as completed track. */
    fun saveOrphanedCheckpoint(track: Track) {
        _recoveryTrack.value = null
        viewModelScope.launch {
            repository.finalizeOrphanedCheckpoint(track)
            refreshSummaries()
        }
    }

    /** Resolve orphaned checkpoint: discard. */
    fun discardOrphanedCheckpoint(track: Track) {
        _recoveryTrack.value = null
        viewModelScope.launch {
            repository.deleteCheckpoint(track.id)
        }
    }

    private suspend fun recoverOrphanedCheckpoints() {
        val orphans = repository.recoverOrphanedCheckpoints()
        if (orphans.isNotEmpty()) {
            _recoveryTrack.value = orphans.first()
        }
    }

    /** Update the active recording track's name and/or comment. Persisted to checkpoint. */
    fun updateLiveTrackMeta(name: String? = null, comment: String? = null) {
        recorder?.updateCurrentTrackMeta(name, comment)
    }

    /** Clear the track info error — dismisses the ErrorOverlay. */
    fun clearInfoError() {
        recorder?.clearInfoError()
    }

    /**
     * Import tracks from a GPX file or ZIP archive.
     * Saves each imported track via [TrackRepository.save], applying anti-collision
     * name resolution against existing track names.
     *
     * @param input     InputStream from the file picker URI.
     * @param extension "gpx" or "zip".
     * @return Number of tracks imported.
     */
    suspend fun importTracks(input: java.io.InputStream, extension: String): Int {
        val existingNames = _allSummaries.value.map { it.name }.toSet()
        val tracks = GpxImporter.import(input, extension, existingNames)
        for (track in tracks) {
            repository.save(track)
        }
        refreshSummaries()
        return tracks.size
    }

    override fun onCleared() {
        super.onCleared()
        stopRecorder()
    }
}
