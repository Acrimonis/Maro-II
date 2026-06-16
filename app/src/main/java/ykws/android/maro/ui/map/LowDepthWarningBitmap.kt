package ykws.android.maro.ui.map

import android.graphics.Bitmap
import ykws.android.maro.data.depth.DepthConstants
import ykws.android.maro.data.model.DepthGrid
import kotlin.math.roundToInt

/**
 * Rasterises a [DepthGrid] into a high-contrast **low-depth warning** [Bitmap]: every water cell
 * shallower than [maxDepthM] (default 1.5 m) is painted bright magenta with **depth-graded
 * opacity** — 100 % at the shoreline (depth → 0) fading to [minOpacity] at the threshold
 * (depth → max), so the shallowest, most dangerous water reads loudest. Everything else (deeper,
 * NoData, above datum, or on land) is fully transparent.
 *
 * **Sub-cell coast test:** a cell is painted only when all four of its corners are on water
 * ([isWater]); cells that straddle the shoreline are dropped, so the band stops at the waterline
 * instead of lapping the ~½-cell (~12 m) footprint of a water-centre cell onto land.
 *
 * Pure (no per-frame work) — a runtime threshold over the shipped grid, no rebake. The opacity
 * floor is tunable via `zone.properties` (`lowDepthWarningMinOpacityPct`, loaded by `ZoneConfig`).
 * Built off the main thread alongside [DepthBitmap]; same south-up grid → top-down row flip.
 */
object LowDepthWarningBitmap {

    /**
     * @param emodnetCutoffM EMODnet shallow-water gate: cells from EMODNET shallower than this
     *                       render as NoData (transparent). 0 disables. Default 0.
     */
    fun build(
        grid: DepthGrid,
        maxDepthM: Float = DepthConstants.LOW_DEPTH_WARNING_MAX_M.toFloat(),
        isWater: (lat: Double, lon: Double) -> Boolean = { _, _ -> true },
        minOpacity: Float = 0.25f,
        emodnetCutoffM: Float = 0f,
        onProgress: ((Int) -> Unit)? = null
    ): Bitmap {
        val w = grid.cols
        val h = grid.rows
        val colors = IntArray(w * h)
        val hLat = grid.cellSizeDegLat * 0.5   // half-cell, for the corner sampling
        val hLon = grid.cellSizeDegLon * 0.5
        for (r in 0 until h) {
            val outRow = (h - 1 - r) * w   // flip south-up grid → top-down bitmap
            for (c in 0 until w) {
                val d = grid.depthGated(r, c, emodnetCutoffM)
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
                colors[outRow + c] = if (fullyWater) warningArgb(d, maxDepthM, minOpacity) else 0
            }
            if (onProgress != null && (r and 0xFF) == 0) {
                onProgress(r * 100 / h)
            }
        }
        onProgress?.invoke(100)
        return Bitmap.createBitmap(colors, w, h, Bitmap.Config.ARGB_8888)
    }

    /**
     * Depth-graded ARGB from the configured [ZoneConfig.lowDepthWarningColor]:
     * 100 % alpha at the surface (depth 0) fading to [minOpacity] at [maxDepthM].
     * Shallower water = more opaque = louder hazard cue. Only alpha varies; hue is
     * taken from the property file.
     */
    private fun warningArgb(depthM: Float, maxDepthM: Float, minOpacity: Float): Int {
        val frac = (depthM / maxDepthM).coerceIn(0f, 1f)                              // 0 at surface … 1 at threshold
        val floor = minOpacity.coerceIn(0f, 1f)                                       // opacity at the threshold (e.g. 0.25)
        val alpha = (255f * (1f - (1f - floor) * frac)).roundToInt().coerceIn(0, 255) // 100 % at surface → floor at threshold
        val rgb = ZoneConfig.overlayLowDepthColor and 0x00FFFFFF                       // strip any configured alpha
        return (alpha shl 24) or rgb                                                  // A (depth-graded) | R | G | B
    }
}
