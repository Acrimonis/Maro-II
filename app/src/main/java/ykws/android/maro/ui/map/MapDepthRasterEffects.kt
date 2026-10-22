package ykws.android.maro.ui.map

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.depth.RasterCache
import ykws.android.maro.data.model.BoundingBox
import ykws.android.maro.data.model.DepthGrid
import ykws.android.maro.data.model.DepthSample
import ykws.android.maro.data.model.DepthState
import ykws.android.maro.data.model.Isobath
import ykws.android.maro.data.model.RasterProgress
import ykws.android.maro.data.regulation.RegulatedZoneSet
import ykws.android.maro.data.regulation.RegulatedZonesRepository
import ykws.android.maro.data.settings.AppSettings

/**
 * Output model produced by the depth/raster seam (extracted from MapScreen). Feeds the
 * MapContent depth params and the DashboardPanel depth sample.
 */
internal data class DepthRasterOutputs(
    val effectiveDepthBitmap: Bitmap?,
    val effectiveLowDepthWarning: Bitmap?,
    val depthReadout: DepthSample?,
    val depthBox: BoundingBox?,
    val isobaths: List<Isobath>,
    val rasterProgress: RasterProgress?
)

/**
 * Depth layer + silent raster lazy-init (extracted from MapScreen). Both live
 * produceState builds, both cached reads, and the effective-priority merge move
 * together as one unit and are RETURNED via [DepthRasterOutputs].
 */
@Composable
internal fun MapDepthRasterEffects(
    context: Context,
    viewModel: NavigationViewModel,
    depthViewModel: DepthViewModel,
    appSettings: AppSettings,
    coastlineReady: Boolean
): DepthRasterOutputs {
    // ── Depth layer ─────────────────────────────────────────────────────────────
    val depthState by depthViewModel.state.collectAsState()
    val depthRender by depthViewModel.renderModel.collectAsState()
    val depthAtCenter by depthViewModel.depthAtCenter.collectAsState()
    // Gate coarse EMODnet shallow readings (unreliable near rocks/coast) → no-data in the readout.
    val depthReadout = depthAtCenter?.gatedForEmodnetShallow(appSettings.emodnetShallowCutoffM)
    val depthGrid = (depthState as? DepthState.Ready)?.grid
    val isobaths = depthRender?.isobaths ?: emptyList()

    // Coastline classifier is needed for the low-depth warning water test.
    val waterTest: (Double, Double) -> Boolean =
        if (coastlineReady) viewModel::isOnWater else { _, _ -> false }

    // Per step: each raster's key carries its own palette hash, so one layer's colour change
    // never discards the other layer's cache file.
    fun cached(grid: DepthGrid, step: RasterCache.Step): Boolean = RasterCache.has(
        context, step,
        RasterCache.keyFor(
            step, grid, appSettings.emodnetShallowCutoffM,
            appSettings.lowDepthCrashDepthM, appSettings.lowDepthStartWarningM
        )
    )

    // Bumped by a cached producer that found a cache file it could not decode (the reader evicts
    // it): the live producers and the lazy-init below are keyed on it, so a corrupt cache falls
    // through to a live build and a regeneration instead of leaving the layer absent.
    var corruptCacheTick by remember { mutableIntStateOf(0) }

    // Rasterise the colour map once per grid, off the main thread (~7 M cells).
    val depthBitmap by produceState<Bitmap?>(initialValue = null, depthGrid,
        appSettings.emodnetShallowCutoffM, corruptCacheTick) {
        // A cache file on disk means the cached producer below supplies this layer: skip the live build
        // rather than decode the same raster twice.
        val g = depthGrid
        if (g == null || withContext(Dispatchers.IO) { cached(g, RasterCache.Step.DEPTH_COLOUR) }) {
            value = null; return@produceState
        }
        value = withContext(Dispatchers.Default) {
            DepthBitmap.build(g, appSettings.emodnetShallowCutoffM, AppConfig.mapDepthNodataColor)
        }
    }

    // Second raster: cells shallower than the user's warning threshold, on water only, painted bright.
    // Re-rasterises when the grid, the threshold, or coastline readiness changes.
    val lowDepthWarningBitmap by produceState<Bitmap?>(
        initialValue = null, depthGrid, appSettings.lowDepthCrashDepthM,
        appSettings.lowDepthStartWarningM, coastlineReady,
        appSettings.emodnetShallowCutoffM, corruptCacheTick
    ) {
        // Same skip as the colour map: a cache file on disk means the cached producer supplies it.
        val g = depthGrid
        if (g == null || withContext(Dispatchers.IO) { cached(g, RasterCache.Step.LOW_DEPTH_WARNING) }) {
            value = null; return@produceState
        }
        val crashM = appSettings.lowDepthCrashDepthM
        val startM = appSettings.lowDepthStartWarningM
        value = withContext(Dispatchers.Default) {
            LowDepthWarningBitmap.build(g, crashM, startM, waterTest,
                appSettings.emodnetShallowCutoffM)
        }
    }

    // ── Raster cache reads (no lazy auto-trigger; only settings button triggers generation) ──
    val rasterProgress by depthViewModel.rasterProgress.collectAsState()
    val generatingStep by depthViewModel.generatingStep.collectAsState()
    val rasterCacheVersion by depthViewModel.rasterCacheVersion.collectAsState()

    // ── Silent lazy-init: on cache miss, generate rasters in background (no LoadingOverlay).
    //    Warning layer is deferred until coastline is ready (needs accurate isWater). ──
    LaunchedEffect(depthGrid, appSettings.lowDepthCrashDepthM,
                   appSettings.lowDepthStartWarningM, coastlineReady,
                   appSettings.emodnetShallowCutoffM, corruptCacheTick) {
        val grid = depthGrid ?: return@LaunchedEffect
        val missing = mutableListOf<RasterCache.Step>()
        if (!cached(grid, RasterCache.Step.DEPTH_COLOUR))
            missing.add(RasterCache.Step.DEPTH_COLOUR)
        if (coastlineReady && !cached(grid, RasterCache.Step.LOW_DEPTH_WARNING))
            missing.add(RasterCache.Step.LOW_DEPTH_WARNING)
        if (missing.isNotEmpty()) {
            val waterTest: (Double, Double) -> Boolean =
                if (coastlineReady) viewModel::isOnWater else { _, _ -> false }
            depthViewModel.generateRasterLayers(context, missing, appSettings, waterTest, silent = true)
        }
    }

    // Read cached rasters; hide a layer when it's being regenerated.
    val depthBitmapCached by produceState<Bitmap?>(initialValue = null, depthGrid,
        appSettings.lowDepthCrashDepthM, appSettings.lowDepthStartWarningM,
        rasterCacheVersion, generatingStep) {
        if (generatingStep == RasterCache.Step.DEPTH_COLOUR) { value = null; return@produceState }
        val g = depthGrid
        if (g == null) { value = null; return@produceState }
        val had = withContext(Dispatchers.IO) { cached(g, RasterCache.Step.DEPTH_COLOUR) }
        val read = depthViewModel.readCached(context, RasterCache.Step.DEPTH_COLOUR, appSettings)
        value = read
        // A file that was there and did not decode has just been evicted: hand the layer back to
        // the live build and ask for a regeneration.
        if (had && read == null) corruptCacheTick++
    }
    val lowDepthWarningCached by produceState<Bitmap?>(initialValue = null, depthGrid,
        appSettings.lowDepthCrashDepthM, appSettings.lowDepthStartWarningM,
        rasterCacheVersion, generatingStep) {
        if (generatingStep == RasterCache.Step.LOW_DEPTH_WARNING) { value = null; return@produceState }
        val g = depthGrid
        if (g == null) { value = null; return@produceState }
        val had = withContext(Dispatchers.IO) { cached(g, RasterCache.Step.LOW_DEPTH_WARNING) }
        val read = depthViewModel.readCached(context, RasterCache.Step.LOW_DEPTH_WARNING, appSettings)
        value = read
        if (had && read == null) corruptCacheTick++
    }

    // Prefer cached rasters; fall back to live-built ones. Hide a layer entirely
    // while it's being regenerated (generatingStep signals the active step).
    val effectiveDepthBitmap = if (generatingStep == RasterCache.Step.DEPTH_COLOUR) null
        else (depthBitmapCached ?: depthBitmap)
    val effectiveLowDepthWarning = if (generatingStep == RasterCache.Step.LOW_DEPTH_WARNING) null
        else (lowDepthWarningCached ?: lowDepthWarningBitmap)

    return DepthRasterOutputs(
        effectiveDepthBitmap = effectiveDepthBitmap,
        effectiveLowDepthWarning = effectiveLowDepthWarning,
        depthReadout = depthReadout,
        depthBox = depthGrid?.boundingBox,
        isobaths = isobaths,
        rasterProgress = rasterProgress
    )
}

/**
 * Regulated zones overlay: load prebaked asset on first composition (extracted from MapScreen).
 *
 * The repository is **hoisted to the caller** and handed in, so the one instance that loads the
 * asset is the one its readers watch — no second instance's state is ever watched. The load itself
 * is `suspend` and runs off the main thread inside the repository; this effect only starts it once.
 */
@Composable
internal fun MapRegulatedZonesLoader(
    context: Context,
    repository: RegulatedZonesRepository
): RegulatedZoneSet? {
    val regulatedZones by produceState<RegulatedZoneSet?>(initialValue = null, repository) {
        repository.load(context)
        value = repository.zoneSet.value
    }
    return regulatedZones
}
