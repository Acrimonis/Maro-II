package ykws.android.maro.data.depth

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ykws.android.maro.data.depth.raster.SourceRaster
import ykws.android.maro.data.depth.validation.ControlPoint
import ykws.android.maro.data.depth.validation.ControlPoints
import ykws.android.maro.data.depth.validation.DepthValidator
import ykws.android.maro.data.model.BoundingBox
import ykws.android.maro.data.model.DepthDatum
import ykws.android.maro.data.model.DepthGrid
import ykws.android.maro.data.model.MutableDepthGrid

/**
 * Builds the depth grid for a region by merging pre-parsed open-source rasters, then validating.
 *
 * Sources are **baked offline** for this fixed zone (see docs/depthMappingSources.md +
 * DepthMappingBake.md) and supplied already parsed — the repository reads them from assets. The
 * generator itself is **pure**: no network, no IO, so it is deterministic and unit-testable.
 *
 * Sequenced tiers (complementary, not a race):
 *   empty grid → deep sources (EMODnet E5, best-resolution merge) → shallow source (Litto3D,
 *   shoalest ≤ ceiling) → validate against control points → immutable grid (with embedded report).
 *
 * With no sources supplied the result is a valid (empty) grid rather than an error — the layer
 * stays inert until the data is baked in. GEBCO gap-fill and the dive-detail tiers (SHOM survey
 * lots, Sentinel-2 SDB) are added later (see DepthMappingPlan.md § 12).
 */
class DepthGenerator(
    private val gridResM: Double = DepthConstants.GRID_RES_M,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    suspend fun generate(
        regionId: String = DepthConstants.REGION_ID,
        bbox: BoundingBox = DepthConstants.WATER_BBOX,
        deepSources: List<SourceRaster> = emptyList(),
        shallowSource: SourceRaster? = null,
        controlPoints: List<ControlPoint> = ControlPoints.NICE_FREJUS,
        nowMs: Long = System.currentTimeMillis(),
        onProgress: (phase: String, pct: Int) -> Unit = { _, _ -> }
    ): DepthGrid = withContext(ioDispatcher) {
        onProgress("Préparation grille", 2)
        val grid = MutableDepthGrid.empty(regionId, bbox, gridResM, DepthDatum.LAT)

        // 1. Deep backbone — best-resolution-wins across all deep sources (EMODnet E5, …).
        val deepSpan = deepSources.size.coerceAtLeast(1)
        deepSources.forEachIndexed { i, src ->
            onProgress("Fusion profonde", 10 + i * 50 / deepSpan)
            DepthMerge.mergeDeep(grid, src)
        }

        // 2. Shallow precision — Litto3D, shoalest-wins ≤ ceiling (collision-safe).
        if (shallowSource != null) {
            onProgress("Fusion littorale", 70)
            DepthMerge.mergeShallowShoalest(grid, shallowSource, DepthConstants.SHALLOW_TIER_MAX_M)
        }

        // 3. Validate (provisional immutable shares arrays with `grid`, unchanged by validate).
        val label = buildLabel(deepSources, shallowSource)
        val provisional = grid.toImmutable(null, nowMs, label)
        val report = DepthValidator.validate(provisional, controlPoints, nowMs = nowMs)
        onProgress("Validation", 95)

        val finalGrid = grid.toImmutable(report, nowMs, label)
        onProgress("Terminé", 100)
        finalGrid
    }

    private fun buildLabel(deep: List<SourceRaster>, shallow: SourceRaster?): String {
        val parts = (deep.map { it.source } + listOfNotNull(shallow?.source)).distinct()
        return if (parts.isEmpty()) "Aucune source" else parts.joinToString(" + ") { it.name }
    }
}
