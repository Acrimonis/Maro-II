
package ykws.android.maro.ui.map
import ykws.android.maro.config.AppConfig

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
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
import ykws.android.maro.data.model.DepthGrid
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

    /**
     * The depth grid's own reader, so a consumer that needs the grid reads **the one the app loaded**
     * rather than decoding a second copy of it — the route tracer's own need, which is triggered by the
     * tracer's readiness rather than by this screen.
     */
    val depthRepository: DepthRepository get() = repository

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

    private val _rasterLayers = MutableStateFlow<Map<RasterCache.Step, Bitmap>>(emptyMap())
    /**
     * The display rasters this view model holds — at most one bitmap per step, filled by a cache
     * read and a live build alike, so the screen never rasterises on its own. A step is absent
     * until produced, and dropped the moment a request for another key supersedes it.
     */
    val rasterLayers: StateFlow<Map<RasterCache.Step, Bitmap>> = _rasterLayers.asStateFlow()

    /** A raster held for display, with the key it was built or read for. */
    private class ProducedRaster(val key: RasterCache.Key, val bitmap: Bitmap)

    /**
     * A build in flight: the key the step will produce (none while the grid it keys on is not
     * loaded), the coroutine carrying it, whether it is the Settings path (which owns the progress
     * overlay and the hidden layer), and how to requeue the steps that shared the coroutine when one
     * of them is superseded.
     */
    private class RasterJob(
        val key: RasterCache.Key?,
        val job: Job,
        val forced: Boolean,
        val requeue: (List<RasterCache.Step>) -> Unit
    )

    // Single-flight bookkeeping, touched on the main thread only: every entry point runs there and
    // every job returns there before it reads or writes these maps.
    private val produced = mutableMapOf<RasterCache.Step, ProducedRaster>()
    private val running = mutableMapOf<RasterCache.Step, RasterJob>()

    /**
     * The loaded coastline's identity the warning raster's key carries, recorded by
     * [ensureRasterLayers], so a Settings regeneration writes the file the lazy path looks for.
     */
    private var coastStamp = 0L

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

    // ── Raster production: this view model is the single owner ─────────────

    /**
     * The screen's lazy path: make sure each of [steps] is held in [rasterLayers] for the current
     * key — reading the cache when a file is there, building silently (no overlay, no hide/show)
     * when it is not, and writing the build to the cache. A step already held or already being
     * produced for that key is left alone; a different key supersedes the step's running job
     * and drops the raster it holds. [coastStamp] is the loaded coastline's identity (0 when none).
     */
    fun ensureRasterLayers(
        context: Context,
        steps: List<RasterCache.Step>,
        settings: AppSettings,
        isWater: (Double, Double) -> Boolean,
        coastStamp: Long
    ) {
        this.coastStamp = coastStamp
        val grid = repository.getGrid() ?: return
        for (step in steps) {
            val key = keyFor(step, grid, settings)
            if (produced[step]?.key == key || running[step]?.key == key) continue
            launchSingleFlight(mapOf(step to key), forced = false, requeue = {}) {
                produceStep(context, step, key, grid, settings, isWater, forced = false)
            }
        }
    }

    /**
     * The Settings "Regenerate" path: the 4-step pipeline executed sequentially. For each selected step:
     * 1. Set [generatingStep] → UI hides that layer's bitmap
     * 2. Execute the step (load grid / derive isobaths / build raster, the cache evicted first)
     * 3. Write cache and hand the bitmap to [rasterLayers] (raster steps only)
     * 4. Clear [generatingStep] → UI shows new bitmap
     *
     * Reports weighted progress across all selected steps via [_rasterProgress]. A new call
     * supersedes whatever is running for its steps, lazy build or earlier regeneration.
     */
    fun generateRasterLayers(
        context: Context,
        steps: List<RasterCache.Step>,
        settings: AppSettings,
        isWater: (Double, Double) -> Boolean
    ) {
        if (steps.isEmpty()) return
        // One job carries every step, each registered under its own key, so a lazy request for a
        // step's current key waits on it and a request for another key supersedes that step alone.
        val grid = repository.getGrid()
        val keys = steps.associateWith { step -> grid?.let { keyFor(step, it, settings) } }
        launchSingleFlight(
            keys,
            forced = true,
            // The steps that shared this coroutine and were not superseded run again on their own.
            requeue = { rest -> generateRasterLayers(context, rest, settings, isWater) }
        ) {
            totalSteps = steps.size
            stepIndex = 0
            _rasterProgress.value = RasterProgress("", 0, 0)
            val loaded = withContext(Dispatchers.IO) { repository.loadGridFromAssets() } ?: return@launchSingleFlight
            for (step in steps) {
                stepIndex++
                produceStep(context, step, keyFor(step, loaded, settings), loaded, settings, isWater, forced = true)
            }
        }
    }

    /**
     * Single flight per step: supersedes whatever is running for any step in [keys], registers one
     * job under all of them, runs [body], and on completion releases the entries that are still its
     * own and — for the Settings path — the progress overlay. Superseding is explicit about what the
     * replaced job owned: the hidden layer and the progress are cleared or handed to the requeued
     * siblings, so a silent replacement can never leave a layer hidden behind a stale step mark.
     * Lazily started so the entries exist before the body runs.
     */
    private fun launchSingleFlight(
        keys: Map<RasterCache.Step, RasterCache.Key?>,
        forced: Boolean,
        requeue: (List<RasterCache.Step>) -> Unit,
        body: suspend () -> Unit
    ) {
        val steps = keys.keys.toList()
        supersede(steps)
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            val self = coroutineContext[Job]
            try {
                body()
            } finally {
                val owned = steps.filter { running[it]?.job === self }
                owned.forEach { running.remove(it) }
                if (forced && owned.isNotEmpty()) {
                    _rasterProgress.value = null
                    _generatingStep.value = null
                }
            }
        }
        steps.forEach { running[it] = RasterJob(keys[it], job, forced, requeue) }
        job.start()
    }

    /**
     * Take [steps] away from whatever jobs hold them. A job that loses a step is cancelled whole; the
     * steps it still held and that are not being taken are requeued through the job's own
     * [RasterJob.requeue]; and what the job owned on screen is released here, not in its `finally`
     * (which no longer owns anything): the hidden-layer mark for a taken step, and the progress
     * overlay when nothing of the job survives.
     */
    private fun supersede(steps: List<RasterCache.Step>) {
        val taken = steps.mapNotNull { running.remove(it) }.distinctBy { it.job }
        for (previous in taken) {
            previous.job.cancel()
            val survivors = running.filterValues { it.job === previous.job }.keys.toList()
            survivors.forEach { running.remove(it) }
            if (previous.forced) {
                if (_generatingStep.value?.let { it in steps } == true) _generatingStep.value = null
                if (survivors.isEmpty()) _rasterProgress.value = null
            }
            if (survivors.isNotEmpty()) previous.requeue(survivors)
        }
    }

    /**
     * Runs one step to completion: the in-memory steps report only; a raster step is read from
     * the cache (lazy path) or built, written to the cache and handed to [rasterLayers] — one
     * bitmap per layer, the built one kept for display rather than decoded back. A corrupt cache
     * file is evicted by the reader and simply falls through to the build. [forced] is the
     * Settings path: evict first, report progress, hide the layer while it is rebuilt.
     */
    private suspend fun produceStep(
        context: Context,
        step: RasterCache.Step,
        key: RasterCache.Key,
        grid: DepthGrid,
        settings: AppSettings,
        isWater: (Double, Double) -> Boolean,
        forced: Boolean
    ) {
        if (forced) _generatingStep.value = step
        if (forced || produced[step]?.key != key) dropProduced(step)
        val cutoffM = settings.emodnetShallowCutoffM
        val bitmap = withContext(Dispatchers.Default) {
            when (step) {
                RasterCache.Step.GRID -> {
                    // Loaded by the caller — the step only reports.
                    progressSink(forced, RasterStep.GRID_LOAD, "Depth grid")?.let { it(0); it(100) }
                    null
                }
                RasterCache.Step.ISOBATH -> {
                    val onProgress = progressSink(forced, RasterStep.ISOBATH, "Isobath contours")
                    onProgress?.invoke(0)
                    DepthIsobaths.build(grid, emodnetCutoffM = cutoffM)
                    onProgress?.invoke(100)
                    null
                }
                RasterCache.Step.DEPTH_COLOUR -> {
                    val onProgress = progressSink(forced, RasterStep.COLOUR_RASTER, "Depth colour map")
                    cachedOrBuilt(context, step, key, grid, forced) {
                        onProgress?.invoke(0)
                        DepthBitmap.build(grid, cutoffM, AppConfig.mapDepthNodataColor, onProgress)
                    }
                }
                RasterCache.Step.LOW_DEPTH_WARNING -> {
                    val onProgress = progressSink(forced, RasterStep.WARNING_RASTER, "Shallow warning overlay")
                    cachedOrBuilt(context, step, key, grid, forced) {
                        onProgress?.invoke(0)
                        LowDepthWarningBitmap.build(
                            grid, settings.lowDepthCrashDepthM, settings.lowDepthStartWarningM,
                            isWater, cutoffM, onProgress
                        )
                    }
                }
            }
        }
        if (bitmap != null) {
            produced[step] = ProducedRaster(key, bitmap)
            publishRasters()
        }
        if (forced) _generatingStep.value = null
    }

    /** The cache's bitmap for [key] unless [forced] (which evicts the step first); else [build], written to the cache. */
    private suspend fun cachedOrBuilt(
        context: Context,
        step: RasterCache.Step,
        key: RasterCache.Key,
        grid: DepthGrid,
        forced: Boolean,
        build: () -> Bitmap
    ): Bitmap {
        val cached = withContext(Dispatchers.IO) {
            if (forced) { RasterCache.evict(context, step); null } else RasterCache.read(context, step, key)
        }
        if (cached != null) return cached
        val bmp = build()
        val pixels = IntArray(grid.cols * grid.rows)
        bmp.getPixels(pixels, 0, grid.cols, 0, 0, grid.cols, grid.rows)
        RasterCache.write(context, step, key, pixels, grid.cols, grid.rows)
        return bmp
    }

    /** A builder's progress sink on the Settings path; none on the lazy path. */
    private fun progressSink(forced: Boolean, step: RasterStep, phase: String): ((Int) -> Unit)? {
        if (!forced) return null
        return { p -> report(step, phase, p) }
    }

    private fun keyFor(step: RasterCache.Step, grid: DepthGrid, settings: AppSettings): RasterCache.Key =
        RasterCache.keyFor(
            step, grid, settings.emodnetShallowCutoffM,
            settings.lowDepthCrashDepthM, settings.lowDepthStartWarningM, coastStamp
        )

    private fun dropProduced(step: RasterCache.Step) {
        if (produced.remove(step) != null) publishRasters()
    }

    private fun publishRasters() {
        _rasterLayers.value = produced.mapValues { it.value.bitmap }
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
