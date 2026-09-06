
package ykws.android.maro.ui.map
import ykws.android.maro.config.AppConfig

import android.graphics.Bitmap
import ykws.android.maro.data.depth.DepthConstants
import ykws.android.maro.data.model.DepthGrid
import kotlin.math.roundToInt

/**
 * Rasterises a [DepthGrid] into a high-contrast **low-depth warning** [Bitmap] using a
 * two-depth model: every water cell shallower than [startWarningM] (default 1.5 m) is painted
 * bright magenta with a **linear alpha ramp** between the crash depth and the start-warning
 * depth. From the surface down to [crashDepthM] (default 0.5 m) the overlay is fully opaque
 * (alpha 255 — the crash zone reads loudest); between [crashDepthM] and [startWarningM] alpha
 * ramps linearly 255 → 0; at and beyond [startWarningM] it is fully transparent. Everything
 * else (deeper, NoData, above datum, or on land) is fully transparent.
 *
 * **Sub-cell coast test:** a cell is painted only when all four of its corners are on water
 * ([isWater]); cells that straddle the shoreline are dropped, so the band stops at the waterline
 * instead of lapping the ~½-cell (~12 m) footprint of a water-centre cell onto land.
 *
 * Pure (no per-frame work) — a runtime threshold over the shipped grid, no rebake.
 * Built off the main thread alongside [DepthBitmap]; same south-up grid → top-down row flip.
 */
object LowDepthWarningBitmap {

    /**
     * @param emodnetCutoffM EMODnet shallow-water gate: cells from EMODNET shallower than this
     *                       render as NoData (transparent). 0 disables. Default 0.
     */
    fun build(
        grid: DepthGrid,
        crashDepthM: Float = DepthConstants.LOW_DEPTH_CRASH_DEPTH_M.toFloat(),
        startWarningM: Float = DepthConstants.LOW_DEPTH_START_WARNING_M.toFloat(),
        isWater: (lat: Double, lon: Double) -> Boolean = { _, _ -> true },
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
                if (d.isNaN() || d < 0f || d >= startWarningM) {
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
                colors[outRow + c] = if (fullyWater) warningArgb(d, crashDepthM, startWarningM) else 0
            }
            if (onProgress != null && (r and 0xFF) == 0) {
                onProgress(r * 100 / h)
            }
        }
        onProgress?.invoke(100)
        return Bitmap.createBitmap(colors, w, h, Bitmap.Config.ARGB_8888)
    }

    /**
     * Two-depth-graded ARGB from the configured [AppConfig.lowDepthWarningColor]:
     * alpha 255 from the surface down to [crashDepthM], linear 255 → 0 between
     * [crashDepthM] and [startWarningM], and 0 at/beyond [startWarningM]. Only alpha
     * varies; hue is taken from the property file.
     */
    private fun warningArgb(depthM: Float, crashDepthM: Float, startWarningM: Float): Int {
        // Guard against a degenerate/inverted range (startWarningM <= crashDepthM): the ramp
        // denominator would be <= 0 → NaN/Infinity. Treat the whole band as fully opaque up to
        // startWarningM (no gradient) so any input is safe.
        val alpha = if (startWarningM <= crashDepthM) {
            if (depthM < startWarningM) 255 else 0
        } else {
            when {
                depthM <= crashDepthM -> 255
                depthM >= startWarningM -> 0
                else -> {
                    val frac = (depthM - crashDepthM) / (startWarningM - crashDepthM)   // 0 at crash … 1 at start
                    (255f * (1f - frac)).roundToInt().coerceIn(0, 255)                  // 255 at crash → 0 at start
                }
            }
        }
        val rgb = AppConfig.overlayLowDepthColor and 0x00FFFFFF                     // strip any configured alpha
        return (alpha shl 24) or rgb                                                // A (depth-graded) | R | G | B
    }
}
