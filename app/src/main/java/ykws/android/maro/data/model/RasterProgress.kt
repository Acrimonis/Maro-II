package ykws.android.maro.data.model

/**
 * Progress state during raster layer generation (caching pipeline).
 *
 * @property phase   Human-readable phase name (e.g. "Depth colour map").
 * @property stepProgress  0–100 within the current step.
 * @property globalProgress  0–100 weighted across all steps (grid → isobaths → colour → warning).
 */
data class RasterProgress(
    val phase: String,
    val stepProgress: Int,
    val globalProgress: Int
)

/** Steps in the raster generation pipeline, in order. */
enum class RasterStep { GRID_LOAD, ISOBATH, COLOUR_RASTER, WARNING_RASTER, DONE }

/**
 * Weighted progress computation using measured median timings (2 cold launches, 2026-06-09).
 * Total baseline: 8,922 ms.
 */
object RasterTimings {
    const val GRID_LOAD_MS = 877L
    const val ISOBATH_MS = 1887L
    const val COLOUR_RASTER_MS = 980L
    const val WARNING_RASTER_MS = 5178L
    val TOTAL_MS: Long = GRID_LOAD_MS + ISOBATH_MS + COLOUR_RASTER_MS + WARNING_RASTER_MS // 8922

    private val GRID_WEIGHT = GRID_LOAD_MS.toFloat() / TOTAL_MS   // ~9.8%
    private val ISOBATH_WEIGHT = ISOBATH_MS.toFloat() / TOTAL_MS   // ~21.2%
    private val COLOUR_WEIGHT = COLOUR_RASTER_MS.toFloat() / TOTAL_MS // ~11.0%

    /** Cumulative global-progress offset at the start of each step. */
    private val stepStart: Map<RasterStep, Int> = mapOf(
        RasterStep.GRID_LOAD to 0,
        RasterStep.ISOBATH to (GRID_WEIGHT * 100).toInt(),
        RasterStep.COLOUR_RASTER to ((GRID_WEIGHT + ISOBATH_WEIGHT) * 100).toInt(),
        RasterStep.WARNING_RASTER to ((GRID_WEIGHT + ISOBATH_WEIGHT + COLOUR_WEIGHT) * 100).toInt(),
        RasterStep.DONE to 100
    )

    /** Band width (percentage points) allocated to each step. */
    private val stepBand: Map<RasterStep, Int> = mapOf(
        RasterStep.GRID_LOAD to (GRID_WEIGHT * 100).toInt(),
        RasterStep.ISOBATH to (ISOBATH_WEIGHT * 100).toInt(),
        RasterStep.COLOUR_RASTER to (COLOUR_WEIGHT * 100).toInt(),
        RasterStep.WARNING_RASTER to (100 - ((GRID_WEIGHT + ISOBATH_WEIGHT + COLOUR_WEIGHT) * 100).toInt()),
        RasterStep.DONE to 0
    )

    /**
     * Compute global progress (0–100) from a step and its intra-step progress.
     * @param step        current pipeline step.
     * @param stepProgress 0–100 within the step.
     */
    fun globalProgress(step: RasterStep, stepProgress: Int): Int {
        val base = stepStart[step] ?: 0
        val band = stepBand[step] ?: 0
        if (band <= 0) return base
        return (base + band * stepProgress / 100).coerceIn(0, 100)
    }
}
