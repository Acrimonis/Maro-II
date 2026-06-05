package ykws.android.maro.ui.map

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ykws.android.maro.data.coastline.CoastlineRepository
import ykws.android.maro.data.model.CoastlineState
import ykws.android.maro.data.model.GenerationProgress
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.Zone300Data

/**
 * ViewModel for the coastline map screen.
 *
 * Bridges [CoastlineRepository] to the Compose UI layer.
 * Call [initCache] once at startup to load coastline (from cache or OSM).
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
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

    /** Current map zoom level (8.0–18.0). Used for dynamic marker sizing. */
    private val _zoomLevel = MutableStateFlow(11.0) // matches initial controller.setZoom(11.0)
    val zoomLevel: StateFlow<Double> = _zoomLevel.asStateFlow()

    /** True when the current map center is inside the 300 m regulatory band. */
    private val _inZone300 = MutableStateFlow(false)
    val inZone300: StateFlow<Boolean> = _inZone300.asStateFlow()

    /** Signed distance (m) to the 300 m boundary (+ outside, − inside); null if unknown. */
    private val _distanceToZone = MutableStateFlow<Double?>(null)
    val distanceToZone: StateFlow<Double?> = _distanceToZone.asStateFlow()

    /** Precomputed 300 m band geometry for the overlay (null until built). */
    val zone300: StateFlow<Zone300Data?> = repository.state
        .map { (it as? CoastlineState.Ready)?.data?.zone300 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        // Throttle the expensive water/distance recompute. osmdroid fires a
        // scroll event on every frame of a pan/fling (30–60/s); recomputing and
        // emitting on each one floods Compose with recompositions and runs CPU
        // work on the UI thread, causing visible map jank. sample() collapses
        // the stream to ~6–7 updates/s — imperceptible for on-screen text —
        // flowOn moves the work off the main thread, and mapLatest cancels a
        // stale computation when the center moves again before it finishes.
        _mapCenter
            .sample(SHORE_SAMPLE_INTERVAL_MS)
            .mapLatest { center ->
                val result = repository.distanceToCoast(center.latitude, center.longitude)
                val hasDist = result.distanceMeters != Double.MAX_VALUE
                val distance = if (hasDist) result.distanceMeters else null
                // Reuse the distance just computed instead of querying again.
                val water = repository.isOnWater(
                    center.latitude, center.longitude, result.distanceMeters
                )
                // Zone status derives from the SAME distance — no extra spatial query.
                val inZone = water && hasDist &&
                    result.distanceMeters <= CoastlineRepository.ZONE_DISTANCE_M
                val distToZone = if (hasDist) result.distanceMeters - CoastlineRepository.ZONE_DISTANCE_M else null
                ShoreState(distance, water, inZone, distToZone)
            }
            .flowOn(Dispatchers.Default)
            .onEach { shore ->
                _distanceToShore.value = shore.distanceMeters
                _isWater.value = shore.isWater
                _inZone300.value = shore.inZone
                _distanceToZone.value = shore.distToZone
            }
            .launchIn(viewModelScope)
    }

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
     * Records the new center cheaply on the UI thread; the water/distance
     * recompute is driven by the throttled pipeline in [init] to keep heavy
     * work off the high-frequency scroll path.
     */
    fun updateMapCenter(latitude: Double, longitude: Double) {
        _mapCenter.value = LatLng(latitude, longitude)
    }

    /** Captures the current map zoom level for dynamic marker sizing. */
    fun updateZoomLevel(zoom: Double) {
        _zoomLevel.value = zoom
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

    /** Result of one throttled recompute: shore distance + water flag + 300 m zone status. */
    private data class ShoreState(
        val distanceMeters: Double?,
        val isWater: Boolean,
        val inZone: Boolean,
        val distToZone: Double?
    )

    private companion object {
        /** Sampling interval for the map-center recompute pipeline (~6–7 Hz). */
        private const val SHORE_SAMPLE_INTERVAL_MS = 150L
    }
}
