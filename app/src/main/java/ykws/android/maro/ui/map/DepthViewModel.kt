package ykws.android.maro.ui.map

import android.content.Context
import android.graphics.Bitmap
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
import kotlinx.coroutines.withContext
import ykws.android.maro.data.depth.DepthIsobaths
import ykws.android.maro.data.depth.DepthRepository
import ykws.android.maro.data.depth.RasterCache
import ykws.android.maro.data.model.DepthRenderModel
import ykws.android.maro.data.model.DepthSample
import ykws.android.maro.data.model.DepthState
import ykws.android.maro.data.model.GenerationProgress
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.RasterProgress
import ykws.android.maro.data.model.RasterStep
import ykws.android.maro.data.model.RasterTimings
import ykws.android.maro.data.settings.AppSettings

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

    /** Initialise the cache dir and start the one-time lazy load (cache → else bake from assets). */
    fun initCache(context: Context) {
        repository.setCacheDir(context)
        viewModelScope.launch { repository.loadDepth() }
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

    // ── Raster generation / caching ────────────────────────────────────────

    private val _rasterProgress = MutableStateFlow<RasterProgress?>(null)
    /** Non-null while raster layers are being generated; null when idle or done. */
    val rasterProgress: StateFlow<RasterProgress?> = _rasterProgress.asStateFlow()

    /** Which pipeline step is currently being regenerated (null = idle). UI hides this layer's bitmap. */
    private val _generatingStep = MutableStateFlow<RasterCache.Step?>(null)
    val generatingStep: StateFlow<RasterCache.Step?> = _generatingStep.asStateFlow()

    /** Incremented after each successful cache write; triggers produceState re-read. */
    private val _rasterCacheVersion = MutableStateFlow(0)
    val rasterCacheVersion: StateFlow<Int> = _rasterCacheVersion.asStateFlow()

    private var stepIndex = 0
    private var totalSteps = 0

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

    /** Force a fresh bake (deletes cache first). */
    fun refresh() {
        viewModelScope.launch { repository.refreshDepth() }
    }

    // ── Raster generation with caching ─────────────────────────────────────

    /**
     * Self-contained 4-step pipeline executed sequentially. For each selected step:
     * 1. Set [generatingStep] → UI hides that layer's bitmap
     * 2. Execute the step (load grid / derive isobaths / build raster)
     * 3. Write cache (raster steps only)
     * 4. Increment [rasterCacheVersion] → UI re-reads cache
     * 5. Clear [generatingStep] → UI shows new bitmap
     *
     * Reports weighted progress across all selected steps via [_rasterProgress].
     * Only called from the settings "Regenerate" button — no lazy auto-trigger.
     */
    /**
     * @param silent when true, suppresses progress reporting and hide/show — used for
     *               background lazy-init where no LoadingOverlay should appear.
     */
    fun generateRasterLayers(
        context: Context,
        steps: List<RasterCache.Step>,
        settings: AppSettings,
        isWater: (Double, Double) -> Boolean,
        silent: Boolean = false
    ) {
        if (steps.isEmpty()) return
        viewModelScope.launch {
            totalSteps = steps.size
            stepIndex = 0
            if (!silent) _rasterProgress.value = RasterProgress("", 0, 0)
            val grid = withContext(Dispatchers.IO) {
                repository.loadGridFromAssets()
            } ?: run { _rasterProgress.value = null; return@launch }

            val cutoffM = settings.emodnetShallowCutoffM
            val nodataColor = ZoneConfig.nodataColor
            val key = RasterCache.Key(
                gridTimestampMs = grid.metadata.fetchTimestampMs,
                emodnetCutoffM = cutoffM,
                lowDepthMaxM = settings.lowDepthWarningMaxM,
                lowDepthMinOpacityPct = settings.lowDepthWarningMinOpacityPct,
                nodataColor = nodataColor
            )

            withContext(Dispatchers.Default) {
                for (step in steps) {
                    stepIndex++
                    if (!silent) _generatingStep.value = step

                    when (step) {
                        RasterCache.Step.GRID -> {
                            report(RasterStep.GRID_LOAD, "Depth grid", 0)
                            // grid already loaded above — no-op
                            report(RasterStep.GRID_LOAD, "Depth grid", 100)
                        }
                        RasterCache.Step.ISOBATH -> {
                            report(RasterStep.ISOBATH, "Isobath contours", 0)
                            DepthIsobaths.build(grid, emodnetCutoffM = cutoffM)
                            report(RasterStep.ISOBATH, "Isobath contours", 100)
                        }
                        RasterCache.Step.DEPTH_COLOUR -> {
                            RasterCache.evict(context, step) // force rebuild
                            report(RasterStep.COLOUR_RASTER, "Depth colour map", 0)
                            val bmp = DepthBitmap.build(grid, cutoffM, nodataColor) { stepProgress ->
                                report(RasterStep.COLOUR_RASTER, "Depth colour map", stepProgress)
                            }
                            val pixels = IntArray(grid.cols * grid.rows)
                            bmp.getPixels(pixels, 0, grid.cols, 0, 0, grid.cols, grid.rows)
                            RasterCache.write(context, step, key, pixels, grid.cols, grid.rows)
                            bmp.recycle()
                        }
                        RasterCache.Step.LOW_DEPTH_WARNING -> {
                            RasterCache.evict(context, step)
                            report(RasterStep.WARNING_RASTER, "Shallow warning overlay", 0)
                            val maxM = settings.lowDepthWarningMaxM
                            val minOpacity = settings.lowDepthWarningMinOpacityPct / 100f
                            val bmp = LowDepthWarningBitmap.build(grid, maxM, isWater, minOpacity, cutoffM) { stepProgress ->
                                report(RasterStep.WARNING_RASTER, "Shallow warning overlay", stepProgress)
                            }
                            val pixels = IntArray(grid.cols * grid.rows)
                            bmp.getPixels(pixels, 0, grid.cols, 0, 0, grid.cols, grid.rows)
                            RasterCache.write(context, step, key, pixels, grid.cols, grid.rows)
                            bmp.recycle()
                        }
                    }

                    _rasterCacheVersion.value++   // trigger produceState re-read
                    if (!silent) _generatingStep.value = null
                }
            }
            if (!silent) _rasterProgress.value = null
        }
    }

    /** Best-effort cache read for a single raster step; returns the cached bitmap or null. */
    fun readCached(context: Context, step: RasterCache.Step, settings: AppSettings): Bitmap? {
        val grid = repository.getGrid() ?: return null
        val key = RasterCache.Key(
            gridTimestampMs = grid.metadata.fetchTimestampMs,
            emodnetCutoffM = settings.emodnetShallowCutoffM,
            lowDepthMaxM = settings.lowDepthWarningMaxM,
            lowDepthMinOpacityPct = settings.lowDepthWarningMinOpacityPct,
            nodataColor = ZoneConfig.nodataColor
        )
        return RasterCache.read(context, step, key)
    }

    /** Progress title: "Generating Layer" (1 step) or "Generating Layers (x/y)" (multiple). */
    fun progressTitle(): String =
        if (totalSteps <= 1) "Generating Layer"
        else "Generating Layers ($stepIndex/$totalSteps)"

    private fun report(step: RasterStep, phase: String, stepProgress: Int) {
        _rasterProgress.value = RasterProgress(
            phase = phase,
            stepProgress = stepProgress,
            globalProgress = RasterTimings.globalProgress(step, stepProgress)
        )
    }

    private companion object {
        private const val SAMPLE_INTERVAL_MS = 333L
    }
}
