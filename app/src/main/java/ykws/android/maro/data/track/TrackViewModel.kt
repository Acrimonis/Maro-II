package ykws.android.maro.data.track

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ykws.android.maro.data.markers.UserMarkerRepository
import ykws.android.maro.data.model.ListFilter
import ykws.android.maro.data.model.ListSortState
import ykws.android.maro.data.model.matchesFilter
import ykws.android.maro.data.model.todayMidnightMs
import ykws.android.maro.data.settings.AppSettings

/**
 * ViewModel bridge between the [TrackRecordingService]-owned [TrackRecorder] /
 * [TrackRepository] and the Compose UI.
 *
 * The recorder and its GPS sample assembly live in [TrackRecordingService] so
 * recording survives Activity destruction / task removal. This ViewModel is a
 * pure observer of service state: it mirrors [TrackRecordingService.uiState],
 * forwards [TrackRecordingService.events]/[TrackRecordingService.newPoint], and
 * routes recording control (start/stop/discard/resume) through service intents.
 */
class TrackViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TrackRepository(application)
    private val markerRepository = UserMarkerRepository(application)

    // ── Settings injection (set via observeSettings from MapScreen) ──

    private var settingsFlow: StateFlow<AppSettings>? = null

    private val _uiState = MutableStateFlow(TrackRecorderUiState())
    val uiState: StateFlow<TrackRecorderUiState> = _uiState.asStateFlow()

    /** Recorder event stream — MapScreen observes for idle/marker events.
     *  Forwards from the service-owned recorder. */
    private val _events = MutableSharedFlow<TrackEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<TrackEvent> = _events.asSharedFlow()

    /** Unfiltered source of truth — reloaded from repository. */
    private val _allSummaries = MutableStateFlow<List<TrackSummary>>(emptyList())
    val allSummaries: StateFlow<List<TrackSummary>> = _allSummaries.asStateFlow()

    private val _summaries = MutableStateFlow<List<TrackSummary>>(emptyList())
    val summaries: StateFlow<List<TrackSummary>> = _summaries.asStateFlow()

    /** Accessor for the service-owned recorder's incremental new-point stream. */
    val newPointStream: SharedFlow<TrackPoint> = TrackRecordingService.newPoint

    // Recovery state — non-null when an orphaned checkpoint is found
    private val _recoveryTrack = MutableStateFlow<Track?>(null)
    val recoveryTrack: StateFlow<Track?> = _recoveryTrack.asStateFlow()

    private var isLoaded = false

    /**
     * Injects shared settings flow from NavigationViewModel.
     * Must be called once by MapScreen before the ViewModel is used.
     */
    fun observeSettings(flow: StateFlow<AppSettings>) {
        this.settingsFlow = flow
        viewModelScope.launch {
            flow.collect { settings ->
                if (!isLoaded) return@collect
                val midnightMs = todayMidnightMs()
                val filtered = _allSummaries.value.filter { it.matchesFilter(settings.trackListFilter, midnightMs) }
                _summaries.value = sortSummaries(filtered, settings.trackListSort)
            }
        }
    }

    init {
        // Mirror service-owned recorder state (survives Activity destruction).
        viewModelScope.launch {
            TrackRecordingService.uiState.collect { state ->
                _uiState.value = state
            }
        }
        // Forward service recorder events to the persistent flow MapScreen observes.
        viewModelScope.launch {
            TrackRecordingService.events.collect { _events.emit(it) }
        }
        viewModelScope.launch {
            runTrackSchemaMigration()
            runMarkerLinkMigration()
            recoverOrphanedCheckpoints()
            refreshSummaries()
            if (TrackRecordingService.isRecording.value) {
                startService(TrackRecordingService.ACTION_REPLAY_LIVE_TRACK)
            }
        }
    }

    // ── Recording control — routed to TrackRecordingService ──────────────

    private fun startService(action: String, extras: Intent.() -> Unit = {}) {
        val intent = Intent(getApplication<Application>(), TrackRecordingService::class.java).apply {
            this.action = action
            extras()
        }
        getApplication<Application>().startService(intent)
    }

    /** Manually start recording. */
    fun startRecording() {
        startService(TrackRecordingService.ACTION_START_RECORDING)
    }

    /** Manually stop recording (finalizes current track). */
    fun stopRecording() {
        val trackId = _uiState.value.currentTrackId
        startService(TrackRecordingService.ACTION_STOP_RECORDING)
        if (trackId != null) invalidateTrackCache(trackId)
        viewModelScope.launch {
            delay(500)
            refreshSummaries()
        }
    }

    /** Discard the in-progress recording without finalizing (deletes track + checkpoint). */
    fun discardRecording() {
        val trackId = _uiState.value.currentTrackId
        startService(TrackRecordingService.ACTION_DISCARD_RECORDING)
        if (trackId != null) invalidateTrackCache(trackId)
        viewModelScope.launch {
            delay(500)
            refreshSummaries()
        }
    }

    /** Add manual BoatMarker snapshots to the current track. */
    fun addManualBoatMarker(snapshots: List<MarkerSnapshot>) {
        val json = Json.encodeToString(snapshots)
        startService(TrackRecordingService.ACTION_ADD_MANUAL_BOAT_MARKER) {
            putExtra(TrackRecordingService.EXTRA_MARKER_SNAPSHOTS_JSON, json)
        }
    }

    /** Update the active recording track's name and/or comment. Persisted to checkpoint. */
    fun updateLiveTrackMeta(name: String? = null, comment: String? = null) {
        startService(TrackRecordingService.ACTION_UPDATE_LIVE_TRACK_META) {
            if (name != null) putExtra(TrackRecordingService.EXTRA_TRACK_NAME, name)
            if (comment != null) putExtra(TrackRecordingService.EXTRA_TRACK_COMMENT, comment)
        }
    }

    /** Clear the track info error — dismisses the ErrorOverlay. */
    fun clearInfoError() {
        startService(TrackRecordingService.ACTION_CLEAR_INFO_ERROR)
    }

    /**
     * Resume an orphaned checkpoint as a live recording (Continue button in recovery dialog).
     * Routes to the service which restores the checkpointed track and resumes recording.
     */
    fun resumeOrphanedCheckpoint(track: Track) {
        _recoveryTrack.value = null
        startService(TrackRecordingService.ACTION_RESUME_ORPHANED_CHECKPOINT) {
            putExtra(TrackRecordingService.EXTRA_TRACK_ID, track.id)
        }
        viewModelScope.launch {
            delay(500)
            refreshSummaries()
        }
    }

    /**
     * Resume a finalized track as a live recording.
     * Routes to the service which loads the track, forces visibleOnMap, and resumes.
     */
    fun resumeTrack(trackId: String) {
        // Guard: cannot resume while already recording
        if (_uiState.value.state == TrackRecorderState.ON) {
            Log.w("MaroII_TrackVM", "resumeTrack: already recording")
            return
        }
        startService(TrackRecordingService.ACTION_RESUME_TRACK) {
            putExtra(TrackRecordingService.EXTRA_TRACK_ID, trackId)
        }
        viewModelScope.launch {
            delay(500)
            refreshSummaries()
        }
    }

    /** Reload track summaries, mark active track as [ListableItem.isLive]. */
    fun refreshSummaries(sortState: ListSortState? = null, reloadFromDisk: Boolean = true, filter: ListFilter? = null) {
        viewModelScope.launch {
            val settings = settingsFlow?.value
            val effectiveSort = sortState ?: settings?.trackListSort ?: ListSortState()
            val effectiveFilter = filter ?: settings?.trackListFilter ?: ListFilter()
            if (!reloadFromDisk && _allSummaries.value.isNotEmpty()) {
                // Filter/sort change — filter in memory
                val midnightMs = todayMidnightMs()
                val filtered = _allSummaries.value.filter { it.matchesFilter(effectiveFilter, midnightMs) }
                _summaries.value = sortSummaries(filtered, effectiveSort)
                return@launch
            }
            val summaries = repository.listTracks()
            // Mark the active track: most recent summary with no endTimeMs
            summaries.firstOrNull { it.endTimeMs == null }?.isLive = true
            _allSummaries.value = summaries
            isLoaded = true
            val midnightMs = todayMidnightMs()
            val filtered = summaries.filter { it.matchesFilter(effectiveFilter, midnightMs) }
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
                "totalTimeSec" -> compareBy { s ->
                    val end = s.lastPointTimeMs.takeIf { it != 0L } ?: s.endTimeMs ?: nowMs
                    end - s.startTimeMs
                }
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

    /** Delete a track by ID — cascade removes its IDLE_AUTO markers first. */
    fun deleteTrack(id: String) {
        invalidateTrackCache(id)
        viewModelScope.launch {
            repository.delete(id, excludeActiveTrackId = _uiState.value.currentTrackId)
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

            // Reassign source markers → merged.id BEFORE deleting originals, so the
            // delete cascade never sweeps the very markers being reassigned.
            // Applies for both keepOriginals=true and false.
            reassignMarkersToTrack(trackIds, merged.id)

            if (!keepOriginals) {
                trackIds.forEach { id ->
                    invalidateTrackCache(id)
                    repository.delete(id, excludeActiveTrackId = _uiState.value.currentTrackId)
                }
            }

            refreshSummaries()
            _events.emit(TrackEvent.TracksMerged(merged.id, merged.name))
        }
    }

    /** Re-point every marker owned by a source track at [mergedTrackId] (single saveAll). */
    private suspend fun reassignMarkersToTrack(sourceTrackIds: Set<String>, mergedTrackId: String) {
        val all = markerRepository.loadAll()
        if (all.none { it.trackId != null && it.trackId in sourceTrackIds }) return
        val updated = all.map { m ->
            if (m.trackId != null && m.trackId in sourceTrackIds) m.copy(trackId = mergedTrackId) else m
        }
        markerRepository.saveAll(updated)
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
        // The service-owned recorder's active checkpoint is not orphaned — skip the
        // recovery prompt while a recording is live (state is mirrored via uiState).
        if (TrackRecordingService.isRecording.value) return
        val orphans = repository.recoverOrphanedCheckpoints()
        if (orphans.isNotEmpty()) {
            _recoveryTrack.value = orphans.first()
        }
    }

    /**
     * Peek at a single GPX input and report whether it matches an existing track.
     * Returns the matched track's id + name, or null when there is no match or the
     * input is a ZIP (batch imports never prompt).
     *
     * @param input     File bytes from the file picker URI (read once, reused for import).
     * @param extension "gpx" or "zip".
     */
    suspend fun peekImportMatch(input: ByteArray, extension: String): ImportMatch? {
        val summaries = _allSummaries.value
        val existingIds = summaries.map { it.id }.toSet()
        val existingIdByName = summaries.associate { it.name to it.id }
        return GpxImporter.peekImportMatch(input, extension, existingIds, existingIdByName)
    }

    /**
     * Import tracks from a GPX file or ZIP archive.
     * Saves each imported track via [TrackRepository.saveOrReplace]: UPDATE keeps the
     * matched id (replacing in place), NEW/foreign imports get fresh ids.
     *
     * @param input     File bytes from the file picker URI (read once).
     * @param extension "gpx" or "zip".
     * @param mode      How to treat matches. ZIP input is always SKIP_EXISTING.
     * @return Aggregate counts: tracks imported vs. duplicates ignored.
     */
    suspend fun importTracks(input: ByteArray, extension: String, mode: ImportMode): ImportResult {
        val summaries = _allSummaries.value
        val existingNames = summaries.map { it.name }.toSet()
        val existingIds = summaries.map { it.id }.toSet()
        val existingIdByName = summaries.associate { it.name to it.id }
        val batch = GpxImporter.import(input, extension, existingNames, existingIds, existingIdByName, mode)
        for (track in batch.tracks) {
            repository.saveOrReplace(track)
        }
        refreshSummaries()
        return batch.result
    }

    /** One-time schema migration: repair stats + lastPointTimeMs on tracks saved before those fields existed. */
    private suspend fun runTrackSchemaMigration() {
        val prefs = getApplication<Application>()
            .getSharedPreferences("maro_track_schema", android.content.Context.MODE_PRIVATE)
        if (prefs.getInt(KEY_TRACK_SCHEMA_VERSION, 0) < TRACK_SCHEMA_VERSION) {
            repository.migrateTrackStats()
            prefs.edit().putInt(KEY_TRACK_SCHEMA_VERSION, TRACK_SCHEMA_VERSION).apply()
        }
    }

    /**
     * One-time backfill: map legacy BoatMarker.autoMarkerId → UserMarker.trackId.
     * Must run before the persisted field is fully superseded; idempotent, version-gated.
     */
    private suspend fun runMarkerLinkMigration() {
        val prefs = getApplication<Application>()
            .getSharedPreferences("maro_marker_link_schema", android.content.Context.MODE_PRIVATE)
        if (prefs.getInt(KEY_MARKER_LINK_SCHEMA_VERSION, 0) < MARKER_LINK_SCHEMA_VERSION) {
            repository.migrateMarkerTrackLink()
            prefs.edit().putInt(KEY_MARKER_LINK_SCHEMA_VERSION, MARKER_LINK_SCHEMA_VERSION).apply()
        }
    }

    companion object {
        private const val TRACK_SCHEMA_VERSION = 4
        private const val KEY_TRACK_SCHEMA_VERSION = "track_schema_version"

        private const val MARKER_LINK_SCHEMA_VERSION = 1
        private const val KEY_MARKER_LINK_SCHEMA_VERSION = "marker_link_schema_version"
    }
}
