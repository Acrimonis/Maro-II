package ykws.android.maro.ui.map

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
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
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ykws.android.maro.data.coastline.CoastlineRepository
import ykws.android.maro.data.model.CoastlineState
import ykws.android.maro.data.model.GenerationProgress
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.settings.AppSettings
import ykws.android.maro.data.settings.SettingsManager

/**
 * ViewModel for the coastline map screen.
 *
 * Bridges [CoastlineRepository] to the Compose UI layer.
 * Call [initCache] once at startup to load coastline (from cache or OSM).
 *
 * Persisted settings (map center, zoom) are loaded synchronously in [init] so that
 * the Compose UI sees the correct position **before** the MapView is created — fixing
 * the race where `LaunchedEffect`-initiated restore ran after the MapView factory.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class CoastlineViewModel(
    application: Application,
    private val repository: CoastlineRepository = CoastlineRepository()
) : AndroidViewModel(application) {

    /** Persisted settings — initialised eagerly so StateFlows are seeded directly. */
    private val settingsManager: SettingsManager = SettingsManager(application)

    /** Initial settings snapshot — used to seed StateFlow initial values. */
    private val initialAppSettings: AppSettings = settingsManager.settings.value

    /** Reactive settings StateFlow — bridged to [SettingsManager.settings] after init. */
    val settings: StateFlow<AppSettings> = MutableStateFlow(AppSettings())

    /** Initialise the cache directory and load coastline via cache-aside pattern. */
    fun initCache(context: Context) {
        // Bridge: forward every emission from the manager's flow to our public flow.
        viewModelScope.launch {
            settingsManager.settings.collect { updated ->
                (settings as MutableStateFlow<AppSettings>).value = updated
            }
        }

        repository.setCacheDir(context)
        viewModelScope.launch {
            repository.loadCoastline()

            // Only fall back to coastline centroid if no persisted position was ever saved
            if (initialAppSettings.mapCenterLat.isNaN()) {
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
    }

    /**
     * Apply a settings change: persist to disk and update the reactive [settings] flow.
     */
    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        settingsManager.update(transform) ?: run {
            // Fallback: update the local StateFlow even without persistence available
            val current = settings.value
            (settings as MutableStateFlow<AppSettings>).value = transform(current)
        }
    }

    /** UI state: Idle → Loading → Ready | Error. */
    val state: StateFlow<CoastlineState> = repository.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CoastlineState.Idle)

    /** Progress (phase name + 0–100) during generation. */
    val progress: StateFlow<GenerationProgress> = repository.progress
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GenerationProgress("", 0))

    /** Center point of the map — seeded from persisted settings or default (Cannes). */
    private val _mapCenter: MutableStateFlow<LatLng> = MutableStateFlow(
        if (initialAppSettings.mapCenterLat.isNaN().not())
            LatLng(initialAppSettings.mapCenterLat, initialAppSettings.mapCenterLon)
        else
            LatLng(43.55, 7.00)
    )
    val mapCenter: StateFlow<LatLng> = _mapCenter.asStateFlow()

    /** True if the current map center is on the water side of the coastline. */
    private val _isWater: MutableStateFlow<Boolean> = MutableStateFlow(initialAppSettings.isWater)
    val isWater: StateFlow<Boolean> = _isWater.asStateFlow()

    /** Distance (meters) from the current map center to the nearest coastline point.
     * `null` when no coastline is loaded or distance cannot be computed. */
    private val _distanceToShore: MutableStateFlow<Double?> = MutableStateFlow(
        if (initialAppSettings.distanceToShore.isNaN()) null
        else initialAppSettings.distanceToShore
    )
    val distanceToShore: StateFlow<Double?> = _distanceToShore.asStateFlow()

    /** Current map zoom level (8.0–18.0) — seeded from persisted settings or default 11.0. */
    private val _zoomLevel: MutableStateFlow<Double> = MutableStateFlow(
        if (initialAppSettings.zoomLevel > 0.0) initialAppSettings.zoomLevel else 11.0
    )
    val zoomLevel: StateFlow<Double> = _zoomLevel.asStateFlow()

    init {
        // ── Shore recompute pipeline (throttled) ───────────────────────────
        // osmdroid fires a scroll event on every frame of a pan/fling (30–60/s);
        // recomputing and emitting on each one floods Compose with recompositions
        // and runs CPU work on the UI thread, causing visible map jank.
        // sample() collapses the stream to ~6–7 updates/s — imperceptible for
        // on-screen text — flowOn moves the work off the main thread, and
        // mapLatest cancels a stale computation when the center moves again.
        _mapCenter
            .sample(SHORE_SAMPLE_INTERVAL_MS)
            .mapLatest { center ->
                val result = repository.distanceToCoast(center.latitude, center.longitude)
                val distance = if (result.distanceMeters == Double.MAX_VALUE) null
                               else result.distanceMeters
                // Reuse the distance just computed instead of querying again.
                val water = repository.isOnWater(
                    center.latitude, center.longitude, result.distanceMeters
                )
                ShoreState(distance, water)
            }
            .flowOn(Dispatchers.Default)
            .onEach { shore ->
                // Only update when we have valid distance data — don't overwrite
                // the persisted initial value with null while coastline loads.
                if (shore.distanceMeters != null) {
                    _distanceToShore.value = shore.distanceMeters
                    _isWater.value = shore.isWater
                }
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
     * Called whenever the user pans the map.
     * Records the new center cheaply on the UI thread and persists it
     * so the position survives rotation / app restart.
     */
    fun updateMapCenter(latitude: Double, longitude: Double) {
        _mapCenter.value = LatLng(latitude, longitude)
        // Persist position immediately (no throttling needed for disk writes)
        settingsManager.update { it.copy(mapCenterLat = latitude, mapCenterLon = longitude) }
    }

    /**
     * Called whenever the user zooms the map.
     * Captures the zoom level and persists it.
     */
    fun updateZoomLevel(zoom: Double) {
        _zoomLevel.value = zoom
        settingsManager.update { it.copy(zoomLevel = zoom) }
    }

    /**
     * Persists the current map center and zoom to SharedPreferences.
     *
     * Called automatically on every pan/zoom, but also exposed as a
     * lifecycle-aware safety net — call from [androidx.lifecycle.Lifecycle.Event.ON_PAUSE]
     * to guarantee the position survives process kill.
     */
    fun savePosition() {
        val center = _mapCenter.value
        val zoom = _zoomLevel.value
        val water = _isWater.value
        val dist = _distanceToShore.value
        settingsManager.update {
            it.copy(
                mapCenterLat = center.latitude,
                mapCenterLon = center.longitude,
                zoomLevel = zoom,
                isWater = water,
                distanceToShore = dist ?: Double.NaN
            )
        }
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

    /** Result of one throttled recompute: distance to shore (m, null if none) + water flag. */
    private data class ShoreState(val distanceMeters: Double?, val isWater: Boolean)

    companion object {
        /** Sampling interval for the map-center recompute pipeline (~6–7 Hz). */
        private const val SHORE_SAMPLE_INTERVAL_MS = 150L

        /**
         * Factory for [CoastlineViewModel] — required because the primary constructor
         * has two parameters (`Application`, `CoastlineRepository` with default), but
         * [AndroidViewModelFactory] only matches single-`Application`-param constructors.
         */
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                modelClass: Class<T>,
                extras: CreationExtras
            ): T {
                val application = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    ?: error("Cannot create CoastlineViewModel without APPLICATION_KEY")
                return CoastlineViewModel(application as Application) as T
            }
        }
    }
}
