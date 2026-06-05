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
import ykws.android.maro.data.depth.DepthRepository
import ykws.android.maro.data.model.DepthGrid
import ykws.android.maro.data.model.DepthRenderModel
import ykws.android.maro.data.model.DepthSample
import ykws.android.maro.data.model.DepthState
import ykws.android.maro.data.model.GenerationProgress
import ykws.android.maro.data.model.LatLng

/**
 * ViewModel for the depth map layer. Mirrors [CoastlineViewModel]: one-time lazy load via
 * [initCache], reactive [state]/[progress], a derived [renderModel] (isobaths), and a live
 * [depthAtCenter] fed by the same throttled `sample(150 ms)` pipeline pattern.
 *
 * Kept separate from [CoastlineViewModel] for single-responsibility; both observe the map
 * centre independently (the screen calls [updateMapCenter] on each).
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class DepthViewModel(
    private val repository: DepthRepository = DepthRepository()
) : ViewModel() {

    /** Initialise the cache dir and start the one-time lazy load (cache → else fetch). */
    fun initCache(context: Context, preloadedShallow: DepthGrid? = null) {
        repository.setCacheDir(context)
        viewModelScope.launch { repository.loadDepth(preloadedShallow = preloadedShallow) }
    }

    val state: StateFlow<DepthState> = repository.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DepthState.Idle)

    val progress: StateFlow<GenerationProgress> = repository.progress
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GenerationProgress("", 0))

    /** Derived render geometry (isobaths). Emitted once the grid is Ready. */
    val renderModel: StateFlow<DepthRenderModel?> = repository.state
        .map { if (it is DepthState.Ready) repository.getRenderModel() else null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _mapCenter = MutableStateFlow(LatLng(43.55, 7.00))

    private val _depthAtCenter = MutableStateFlow<DepthSample?>(null)
    /** Live depth under the map centre (depth/datum/source/confidence), null until loaded. */
    val depthAtCenter: StateFlow<DepthSample?> = _depthAtCenter.asStateFlow()

    init {
        _mapCenter
            .sample(SAMPLE_INTERVAL_MS)
            .mapLatest { c -> repository.depthAt(c.latitude, c.longitude) }
            .flowOn(Dispatchers.Default)
            .onEach { _depthAtCenter.value = it }
            .launchIn(viewModelScope)
    }

    /** Record the map centre cheaply; the throttled pipeline drives the depth recompute. */
    fun updateMapCenter(latitude: Double, longitude: Double) {
        _mapCenter.value = LatLng(latitude, longitude)
    }

    /** Force a fresh fetch (deletes cache first). */
    fun refresh(preloadedShallow: DepthGrid? = null) {
        viewModelScope.launch { repository.refreshDepth(preloadedShallow = preloadedShallow) }
    }

    private companion object {
        private const val SAMPLE_INTERVAL_MS = 150L
    }
}
