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
 * Call [initCache] once at startup to load coastline (from cache or OSM).
 */
class CoastlineViewModel(
    private val repository: CoastlineRepository = CoastlineRepository()
) : ViewModel() {

    /** Initialise the cache directory and load coastline via cache-aside pattern. */
    fun initCache(context: Context) {
        repository.setCacheDir(context)
        viewModelScope.launch {
            repository.loadCoastline()

            // Compute initial map center from loaded data
            val data = repository.getCoastlineData()
            if (data != null) {
                val allPoints = data.allSegments.flatMap { it.points }
                if (allPoints.isNotEmpty()) {
                    val avgLat = allPoints.sumOf { it.lat.toDouble() } / allPoints.size
                    val avgLon = allPoints.sumOf { it.lon.toDouble() } / allPoints.size
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

    /** Distance (meters) from the current map center to the nearest coastline point.
     * `null` when no coastline is loaded or distance cannot be computed. */
    private val _distanceToShore = MutableStateFlow<Double?>(null)
    val distanceToShore: StateFlow<Double?> = _distanceToShore.asStateFlow()

    /**
     * "Régénérer" button handler.
     * Forces a fresh OSM fetch by deleting the cache file first, then loading.
     */
    fun loadCoastline() {
        viewModelScope.launch {
            repository.refreshCoastline()

            // After loading, recompute water/land for the current map center
            val data = repository.getCoastlineData()
            if (data != null) {
                val allPoints = data.allSegments.flatMap { it.points }
                if (allPoints.isNotEmpty()) {
                    val avgLat = allPoints.sumOf { it.lat.toDouble() } / allPoints.size
                    val avgLon = allPoints.sumOf { it.lon.toDouble() } / allPoints.size
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
        val result = repository.distanceToCoast(latitude, longitude)
        _distanceToShore.value = if (result.distanceMeters == Double.MAX_VALUE) null
                                else result.distanceMeters
    }

    /**
     * Checks if a GPS position is on the water side of the coastline.
     */
    fun isOnWater(latitude: Double, longitude: Double): Boolean =
        repository.isOnWater(latitude, longitude)

    /**
     * Distance and closest point from a GPS position to the coastline.
     */
    fun distanceToCoast(latitude: Double, longitude: Double) =
        repository.distanceToCoast(latitude, longitude)

    /**
     * Distance from a GPS position to the nearest coastline point (meters).
     */
    fun distanceToCoastMeters(latitude: Double, longitude: Double): Double =
        repository.distanceToCoastMeters(latitude, longitude)
}
