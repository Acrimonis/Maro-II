package ykws.android.maro.ui.map

import android.graphics.Bitmap
import ykws.android.maro.data.model.DepthGrid

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
 */
object DepthBitmap {

    fun build(grid: DepthGrid): Bitmap {
        val w = grid.cols
        val h = grid.rows
        val colors = IntArray(w * h)
        for (r in 0 until h) {
            val outRow = (h - 1 - r) * w   // flip south-up grid → top-down bitmap
            for (c in 0 until w) {
                colors[outRow + c] = DepthColorRamp.argb(grid.depthRaw(r, c))
            }
        }
        return Bitmap.createBitmap(colors, w, h, Bitmap.Config.ARGB_8888)
    }
}
