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
 */
object DepthBitmap {

    fun build(grid: DepthGrid): Bitmap {
        val bmp = Bitmap.createBitmap(grid.cols, grid.rows, Bitmap.Config.ARGB_8888)
        for (r in 0 until grid.rows) {
            val y = grid.rows - 1 - r  // flip south-up grid → top-down bitmap
            for (c in 0 until grid.cols) {
                bmp.setPixel(c, y, DepthColorRamp.argb(grid.depthRaw(r, c)))
            }
        }
        return bmp
    }
}
