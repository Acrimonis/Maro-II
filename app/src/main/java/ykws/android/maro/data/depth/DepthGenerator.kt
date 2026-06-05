package ykws.android.maro.data.depth

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ykws.android.maro.data.depth.raster.EmodnetWcsClient
import ykws.android.maro.data.depth.validation.ControlPoint
import ykws.android.maro.data.depth.validation.ControlPoints
import ykws.android.maro.data.depth.validation.DepthValidator
import ykws.android.maro.data.model.BoundingBox
import ykws.android.maro.data.model.DepthDatum
import ykws.android.maro.data.model.DepthGrid
import ykws.android.maro.data.model.MutableDepthGrid

/**
 * Builds the depth grid for a region by fetching + merging open sources, then validating.
 *
 * Sequenced tiers (not a race — sources are complementary):
 *   empty grid → EMODnet WCS (best-resolution deep merge) → preloaded Litto3D (shoalest ≤10 m)
 *   → validate against control points → immutable grid (with embedded report).
 *
 * GEBCO gap-fill and the Sentinel-2 SDB tier are deferred (see DepthMappingPlan.md § 12).
 */
class DepthGenerator(
    private val gridResM: Double = DepthConstants.GRID_RES_M,
    private val wcsClient: EmodnetWcsClient = EmodnetWcsClient(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    suspend fun generate(
        regionId: String = DepthConstants.REGION_ID,
        bbox: BoundingBox = DepthConstants.WATER_BBOX,
        preloadedShallow: DepthGrid? = null,
        controlPoints: List<ControlPoint> = ControlPoints.NICE_FREJUS,
        nowMs: Long = System.currentTimeMillis(),
        onProgress: (phase: String, pct: Int) -> Unit = { _, _ -> }
    ): DepthGrid = withContext(ioDispatcher) {
        onProgress("Préparation grille", 2)
        val grid = MutableDepthGrid.empty(regionId, bbox, gridResM, DepthDatum.LAT)

        // 1. Deep backbone — EMODnet DTM (best-resolution merge).
        onProgress("EMODnet", 5)
        val emodnet = wcsClient.fetchCoverage(bbox) { p -> onProgress("EMODnet", 5 + p * 45 / 100) }
        DepthMerge.mergeDeep(grid, emodnet)
        onProgress("Fusion profonde", 55)

        // 2. Shallow precision — preloaded Litto3D (shoalest-wins, ≤ ceiling).
        if (preloadedShallow != null) {
            DepthMerge.mergeShallowShoalest(grid, preloadedShallow, DepthConstants.SHALLOW_TIER_MAX_M)
            onProgress("Fusion littorale", 75)
        }

        // 3. Validate (provisional immutable shares arrays with `grid`, unchanged by validate).
        val label = if (preloadedShallow != null) "EMODnet + Litto3D" else "EMODnet Bathymetry DTM 2024"
        val provisional = grid.toImmutable(null, nowMs, label)
        val report = DepthValidator.validate(provisional, controlPoints, nowMs = nowMs)
        onProgress("Validation", 95)

        val finalGrid = grid.toImmutable(report, nowMs, label)
        onProgress("Terminé", 100)
        finalGrid
    }
}
