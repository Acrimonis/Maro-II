package ykws.android.maro.ui.map

import android.graphics.Bitmap
import ykws.android.maro.data.model.DepthGrid
import ykws.android.maro.data.model.DepthSource

/**
 * Rasterises a [DepthGrid] into an ARGB [Bitmap] using [DepthColorRamp], one pixel per cell.
 * Drawn on the map as a single osmdroid `GroundOverlay` — far cheaper than per-cell polygons
 * (osmdroid repaints overlays every pan frame).
 *
 * The grid is south-up (row 0 = south); bitmaps are top-down, so rows are flipped vertically.
 * Position the overlay using [DepthGrid.boundingBox] (the actual covered extent).
 *
 * Fills an `IntArray` and builds the bitmap in a single [Bitmap.createBitmap] call rather than
 * millions of `setPixel` calls — the full-zone grid is ~3 M cells, where per-pixel writes are
 * prohibitively slow. Call off the main thread.
 *
 * Water/land discrimination for NoData cells uses the grid's own [DepthSource] array:
 * cells with `source == NONE` are land (masked by [DepthZoneMask]) → transparent;
 * cells with a valid source are water → [nodataColor]. This avoids expensive coastline
 * spatial index queries for millions of NaN cells.
 *
 * @param emodnetCutoffM EMODnet shallow-water gate: cells from EMODNET shallower than this
 *                       render as NoData (transparent). 0 disables. Default 0.
 * @param nodataColor    ARGB colour for NoData / above-datum cells **on water only**.
 *                       Land cells remain transparent. Default light grey `#CCCCCC`.
 */
object DepthBitmap {

    /**
     * Rasterises the grid into an ARGB bitmap.
     *
     * @param emodnetCutoffM EMODnet shallow-water gate (0 = disabled).
     * @param nodataColor    ARGB colour for NoData / above-datum cells on water only.
     * @param onProgress optional callback receiving 0–100 intra-step progress (row/h %), called
     *                   roughly every 256 rows to keep overhead negligible.
     */
    fun build(
        grid: DepthGrid,
        emodnetCutoffM: Float = 0f,
        nodataColor: Int = 0xFFCCCCCC.toInt(),
        onProgress: ((Int) -> Unit)? = null
    ): Bitmap {
        val w = grid.cols
        val h = grid.rows
        val colors = IntArray(w * h)

        // Fast path: nodataColor is transparent → skip water/land check entirely.
        // DepthZoneMask already sets land-side cells to DepthSource.NONE, so we use the
        // grid's own source array as a water/land oracle — orders of magnitude faster
        // than calling the coastline spatial index per cell (~2.5 M spatial queries).
        val sourceNoneId = DepthSource.NONE.id.toByte()
        val nodataOnWater = nodataColor != 0

        for (r in 0 until h) {
            val outRow = (h - 1 - r) * w   // flip south-up grid → top-down bitmap
            for (c in 0 until w) {
                val d = grid.depthGated(r, c, emodnetCutoffM)
                val color = if (d.isNaN() || d < 0f) {
                    // NoData or above-datum:
                    //   - nodataColor on water cells (grid.source != NONE)
                    //   - transparent on land cells (grid.source == NONE, set by DepthZoneMask)
                    if (nodataOnWater && grid.source[r * w + c] != sourceNoneId) nodataColor else 0
                } else {
                    DepthColorRamp.argb(d)
                }
                colors[outRow + c] = color
            }
            if (onProgress != null && (r and 0xFF) == 0) {
                onProgress(r * 100 / h)
            }
        }
        onProgress?.invoke(100)
        return Bitmap.createBitmap(colors, w, h, Bitmap.Config.ARGB_8888)
    }
}
