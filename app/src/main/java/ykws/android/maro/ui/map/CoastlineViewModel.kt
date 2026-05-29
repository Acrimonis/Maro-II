package ykws.android.maro.ui.map

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
import ykws.android.maro.data.model.LatLng

/**
 * ViewModel for the coastline map screen.
 *
 * Bridges [CoastlineRepository] to the Compose UI layer.
 * Computes map center from loaded coastline data.
 */
class CoastlineViewModel(
    private val repository: CoastlineRepository = CoastlineRepository()
) : ViewModel() {

    /** UI state: Loading / Ready / Error. */
    val state: StateFlow<CoastlineState> = repository.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CoastlineState.Loading)

    /** Progress 0–100 during generation. */
    val progress: StateFlow<Int> = repository.progress
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Center point of the loaded coastline for map centering. */
    private val _mapCenter = MutableStateFlow(LatLng(43.55, 7.00)) // Default: Cannes
    val mapCenter: StateFlow<LatLng> = _mapCenter.asStateFlow()

    /** Typical water test point (seaward from the coastline center). */
    private val _waterTestPoint = MutableStateFlow(LatLng(43.50, 7.00))
    val waterTestPoint: StateFlow<LatLng> = _waterTestPoint.asStateFlow()

    /** True if [waterTestPoint] is on the water side of the coastline. */
    private val _isWater = MutableStateFlow(true)
    val isWater: StateFlow<Boolean> = _isWater.asStateFlow()

    init {
        loadCoastline()
    }

    /**
     * (Re)load the coastline data from the Overpass API.
     */
    fun loadCoastline() {
        viewModelScope.launch {
            repository.generate()

            // After loading, compute map center from the coastline data
            val currentState = repository.state.value
            if (currentState is CoastlineState.Ready && currentState.polylines.isNotEmpty()) {
                val allPoints = currentState.polylines.flatMap { seg -> seg.points }
                if (allPoints.isNotEmpty()) {
                    val avgLat = allPoints.sumOf { it.latitude } / allPoints.size
                    val avgLon = allPoints.sumOf { it.longitude } / allPoints.size
                    _mapCenter.value = LatLng(avgLat, avgLon)

                    // Set the water test point ~5 km south of center
                    _waterTestPoint.value = LatLng(avgLat - 0.05, avgLon)

                    // Check if the test point is on water
                    _isWater.value = repository.isOnWater(avgLat - 0.05, avgLon)
                }
            }
        }
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
