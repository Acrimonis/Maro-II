package ykws.android.maro.ui.map

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ykws.android.maro.data.coastline.CoastlineRepository
import ykws.android.maro.data.model.CoastlineState
import ykws.android.maro.data.model.GenerationProgress
import ykws.android.maro.data.model.LatLng

/**
 * ViewModel for the coastline map screen.
 *
 * Bridges [CoastlineRepository] to the Compose UI layer.
 * Call [initCache] once at startup to restore persisted coastline data.
 */
class CoastlineViewModel(
    private val repository: CoastlineRepository = CoastlineRepository()
) : ViewModel() {

    /** Initialise the cache directory and try restoring persisted coastline. */
    fun initCache(context: Context) {
        repository.setCacheDir(context)
        viewModelScope.launch {
            val cache = repository.loadCache()
            if (cache != null) {
                val segments = cache.segments
                repository.restoreFromCache(segments, cache.metadata)

                // Compute initial map center from cached data
                val allPoints = segments.flatMap { it.points }
                if (allPoints.isNotEmpty()) {
                    val avgLat = allPoints.sumOf { it.latitude } / allPoints.size
                    val avgLon = allPoints.sumOf { it.longitude } / allPoints.size
                    _mapCenter.value = LatLng(avgLat, avgLon)
                    _isWater.value = repository.isOnWater(avgLat, avgLon)
                }
            }
        }
    }

    /** UI state: Idle → Loading → Ready | Error. */
    val state: StateFlow<CoastlineState> = repository.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CoastlineState.Idle)

    /** Progress (phase name + 0–100) during generation. */
    val progress: StateFlow<GenerationProgress> = repository.progress
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GenerationProgress("", 0))

    /** Center point of the map — updated live as user pans. */
    private val _mapCenter = MutableStateFlow(LatLng(43.55, 7.00)) // Default: Cannes
    val mapCenter: StateFlow<LatLng> = _mapCenter.asStateFlow()

    /** True if the current map center is on the water side of the coastline. */
    private val _isWater = MutableStateFlow(true)
    val isWater: StateFlow<Boolean> = _isWater.asStateFlow()

    /**
     * (Re)load the coastline data from the Overpass API.
     * Clears the local cache first so the next launch fetches fresh data.
     */
    fun loadCoastline() {
        viewModelScope.launch {
            repository.clearCache()
            repository.generate()

            // After loading, recompute water/land for the current map center
            val currentState = repository.state.value
            if (currentState is CoastlineState.Ready && currentState.polylines.isNotEmpty()) {
                val allPoints = currentState.polylines.flatMap { seg -> seg.points }
                if (allPoints.isNotEmpty()) {
                    val avgLat = allPoints.sumOf { it.latitude } / allPoints.size
                    val avgLon = allPoints.sumOf { it.longitude } / allPoints.size
                    _mapCenter.value = LatLng(avgLat, avgLon)
                    _isWater.value = repository.isOnWater(avgLat, avgLon)
                }
            }
        }
    }

    /**
     * Called whenever the user pans/zooms the map.
     * Updates the map center and recomputes water/land in real time.
     */
    fun updateMapCenter(latitude: Double, longitude: Double) {
        _mapCenter.value = LatLng(latitude, longitude)
        _isWater.value = repository.isOnWater(latitude, longitude)
    }

    /**
     * Checks if a GPS position is on the water side of the coastline.
     */
    fun isOnWater(latitude: Double, longitude: Double): Boolean =
        repository.isOnWater(latitude, longitude)

    /**
     * Distance from a GPS position to the nearest coastline point (meters).
     */
    fun distanceToCoastMeters(latitude: Double, longitude: Double): Double =
        repository.distanceToCoastMeters(latitude, longitude)
}
