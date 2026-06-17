package ykws.android.maro.data.track

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ykws.android.maro.data.location.GpsFix
import ykws.android.maro.data.settings.AppSettings

/**
 * ViewModel bridge between [TrackRecorder] / [TrackRepository] and the Compose UI.
 *
 * Observes settings changes, manages recorder lifecycle, and exposes StateFlows
 * for the UI layer.
 */
class TrackViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TrackRepository(application)

    private var recorder: TrackRecorder? = null

    private val _uiState = MutableStateFlow(TrackRecorderUiState())
    val uiState: StateFlow<TrackRecorderUiState> = _uiState.asStateFlow()

    private val _summaries = MutableStateFlow<List<TrackSummary>>(emptyList())
    val summaries: StateFlow<List<TrackSummary>> = _summaries.asStateFlow()

    // Recovery state — non-null when an orphaned checkpoint is found
    private val _recoveryTrack = MutableStateFlow<Track?>(null)
    val recoveryTrack: StateFlow<Track?> = _recoveryTrack.asStateFlow()

    init {
        viewModelScope.launch {
            recoverOrphanedCheckpoints()
        }
        refreshSummaries()
    }

    /**
     * Start the recorder with a GPS fix flow and current settings.
     * Call once when the GPS pipeline is ready.
     */
    fun startRecorder(gpsFlow: Flow<GpsFix>, settings: AppSettings) {
        stopRecorder()
        val rec = TrackRecorder(
            repository = repository,
            geofenceOriginLat = settings.trackOriginLat,
            geofenceOriginLon = settings.trackOriginLon,
            geofenceRadiusM = settings.trackGeofenceRadiusM,
            geofenceEnabled = settings.trackGeofenceEnabled,
            adaptiveWindowMs = settings.adaptiveWindowSec * 1000L,
            adaptiveThresholdM = settings.adaptiveDistanceM.toDouble()
        )
        recorder = rec
        rec.start(gpsFlow)
        viewModelScope.launch {
            rec.uiState.collect { state ->
                _uiState.value = state
            }
        }
    }

    /** Stop the recorder and release resources. */
    fun stopRecorder() {
        recorder?.dispose()
        recorder = null
    }

    /** Manually start recording. */
    fun startRecording() {
        recorder?.startManual()
    }

    /** Manually stop recording (finalizes current track). */
    fun stopRecording() {
        recorder?.stop()
        viewModelScope.launch {
            // Small delay to let the recorder finalize before refreshing
            kotlinx.coroutines.delay(500)
            refreshSummaries()
        }
    }

    /** Reload track summaries from repository. */
    fun refreshSummaries() {
        viewModelScope.launch {
            _summaries.value = repository.listTracks()
        }
    }

    /** Load a full track by ID for detail view. */
    suspend fun loadTrackDetail(id: String): Track? = repository.load(id)

    /** Delete a track by ID. */
    fun deleteTrack(id: String) {
        viewModelScope.launch {
            repository.delete(id)
            refreshSummaries()
        }
    }

    /** Update track metadata. */
    fun updateTrack(id: String, name: String? = null, comment: String? = null, visibleOnMap: Boolean? = null) {
        viewModelScope.launch {
            repository.updateMetadata(id, name, comment, visibleOnMap)
            refreshSummaries()
        }
    }

    /** Resolve orphaned checkpoint: resume recording. */
    fun resumeOrphanedCheckpoint(track: Track) {
        _recoveryTrack.value = null
        // The existing recorder will pick up — the checkpoint data is intact
        viewModelScope.launch {
            repository.deleteCheckpoint(track.id)
            refreshSummaries()
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

    override fun onCleared() {
        super.onCleared()
        stopRecorder()
    }
}
