package ykws.android.maro.ui.map

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import ykws.android.maro.data.depth.RasterCache
import ykws.android.maro.data.model.BoundingBox
import ykws.android.maro.data.model.CoastlineState
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
 * Depth layer + silent raster lazy-init (extracted from MapScreen). The rasters themselves are
 * produced and held by [DepthViewModel] — one bitmap per layer, read from the cache or built
 * once — so this seam only asks for them and merges what the view model holds into
 * [DepthRasterOutputs].
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

    // The coastline the warning overlay classifies water with, named by its fetch timestamp: the
    // stamp the warning raster's cache key carries, so a replaced coastline over the same grid
    // misses the stale overlay. 0 until the coastline is ready.
    val coastlineState by viewModel.state.collectAsState()
    val coastStamp = if (coastlineReady)
        (coastlineState as? CoastlineState.Ready)?.data?.metadata?.fetchTimestampMs ?: 0L
    else 0L

    // ── Raster layers, owned by the view model ──
    val rasterLayers by depthViewModel.rasterLayers.collectAsState()
    val rasterProgress by depthViewModel.rasterProgress.collectAsState()
    val generatingStep by depthViewModel.generatingStep.collectAsState()

    // ── Silent lazy-init: ask the owner for the layers the current key needs — a cache read when
    //    the file is there, a background build (no LoadingOverlay) when it is not. The warning
    //    layer waits for the coastline, whose isWater it paints by. ──
    LaunchedEffect(depthGrid, appSettings.emodnetShallowCutoffM, appSettings.lowDepthCrashDepthM,
                   appSettings.lowDepthStartWarningM, coastlineReady, coastStamp) {
        if (depthGrid == null) return@LaunchedEffect
        val steps = if (coastlineReady) listOf(RasterCache.Step.DEPTH_COLOUR, RasterCache.Step.LOW_DEPTH_WARNING)
            else listOf(RasterCache.Step.DEPTH_COLOUR)
        val waterTest: (Double, Double) -> Boolean =
            if (coastlineReady) viewModel::isOnWater else { _, _ -> false }
        depthViewModel.ensureRasterLayers(context, steps, appSettings, waterTest, coastStamp)
    }

    // Hide a layer entirely while it's being regenerated (generatingStep signals the active step).
    val effectiveDepthBitmap = if (generatingStep == RasterCache.Step.DEPTH_COLOUR) null
        else rasterLayers[RasterCache.Step.DEPTH_COLOUR]
    val effectiveLowDepthWarning = if (generatingStep == RasterCache.Step.LOW_DEPTH_WARNING) null
        else rasterLayers[RasterCache.Step.LOW_DEPTH_WARNING]

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
