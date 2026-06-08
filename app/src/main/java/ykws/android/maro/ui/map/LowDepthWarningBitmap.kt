package ykws.android.maro.ui.map

import android.graphics.Bitmap
import ykws.android.maro.data.depth.DepthConstants
import ykws.android.maro.data.model.DepthGrid
import kotlin.math.roundToInt

/**
 * Rasterises a [DepthGrid] into a high-contrast **low-depth warning** [Bitmap]: every water cell
 * shallower than [maxDepthM] (default 1.5 m) is painted bright magenta with **depth-graded
 * opacity** — ~100 % at the shoreline (depth → 0) fading to ~50 % at the threshold (depth → max),
 * so the shallowest, most dangerous water reads loudest. Everything else (deeper, NoData, above
 * datum, or on land) is fully transparent.
 *
 * **Sub-cell coast test:** a cell is painted only when all four of its corners are on water
 * ([isWater]); cells that straddle the shoreline are dropped, so the band stops at the waterline
 * instead of lapping the ~½-cell (~12 m) footprint of a water-centre cell onto land.
 *
 * Pure (no per-frame work) — a runtime threshold over the shipped grid, no rebake. Built off the
 * main thread alongside [DepthBitmap]; same south-up grid → top-down row flip.
 */
object LowDepthWarningBitmap {

    fun build(
        grid: DepthGrid,
        maxDepthM: Float = DepthConstants.LOW_DEPTH_WARNING_MAX_M.toFloat(),
        isWater: (lat: Double, lon: Double) -> Boolean = { _, _ -> true }
    ): Bitmap {
        val w = grid.cols
        val h = grid.rows
        val colors = IntArray(w * h)
        val hLat = grid.cellSizeDegLat * 0.5   // half-cell, for the corner sampling
        val hLon = grid.cellSizeDegLon * 0.5
        for (r in 0 until h) {
            val outRow = (h - 1 - r) * w   // flip south-up grid → top-down bitmap
            for (c in 0 until w) {
                val d = grid.depthRaw(r, c)
                if (d.isNaN() || d < 0f || d >= maxDepthM) {
                    colors[outRow + c] = 0
                    continue
                }
                // Sub-cell coast test (cheap: shallow cells only): keep the cell only if all four
                // corners are water, so a cell straddling the shore doesn't lap its footprint onto land.
                val clat = grid.cellCenterLat(r)
                val clon = grid.cellCenterLon(c)
                val fullyWater =
                    isWater(clat - hLat, clon - hLon) && isWater(clat - hLat, clon + hLon) &&
                    isWater(clat + hLat, clon - hLon) && isWater(clat + hLat, clon + hLon)
                colors[outRow + c] = if (fullyWater) warningArgb(d, maxDepthM) else 0
            }
        }
        return Bitmap.createBitmap(colors, w, h, Bitmap.Config.ARGB_8888)
    }

    /**
     * Bright-magenta ARGB with depth-graded alpha: ~100 % at the surface (depth 0) fading to ~50 %
     * at [maxDepthM]. Shallower water = more opaque = louder hazard cue. Only alpha varies; hue fixed.
     */
    private fun warningArgb(depthM: Float, maxDepthM: Float): Int {
        val frac = (depthM / maxDepthM).coerceIn(0f, 1f)                       // 0 at surface … 1 at threshold
        val alpha = (255f * (1f - 0.5f * frac)).roundToInt().coerceIn(0, 255)  // 255 (100 %) → ~127 (50 %)
        return (alpha shl 24) or (255 shl 16) or 229                          // A | R=255 | G=0 | B=229
    }
}
