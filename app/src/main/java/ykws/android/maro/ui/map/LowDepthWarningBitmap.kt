package ykws.android.maro.ui.map

import android.graphics.Bitmap
import ykws.android.maro.data.depth.DepthConstants
import ykws.android.maro.data.model.DepthGrid

/**
 * Rasterises a [DepthGrid] into a high-contrast **low-depth warning** [Bitmap]: every cell
 * shallower than [DepthConstants.LOW_DEPTH_WARNING_MAX_M] (1.5 m) is painted bright magenta,
 * every other cell (deeper, NoData, or above datum) is fully transparent. Drawn as a second
 * `GroundOverlay` stacked directly above the depth colour raster so genuine grounding hazards
 * read as an unmistakable bright patch.
 *
 * A deliberately distinct hue from the depth ramp's red-orange 0–5 m tint, so the <1.5 m zone
 * pops *out* of the collision band rather than blending into it. Pure (no per-frame work): the
 * grid already ships continuous per-cell depth, so this is a runtime threshold — no rebake.
 *
 * Built off the main thread alongside [DepthBitmap]; same south-up grid → top-down row flip.
 */
object LowDepthWarningBitmap {

    /** ARGB: ~78 % opaque bright magenta (A=200, R=255, G=0, B=229) — louder than the depth ramp. */
    private const val WARNING_ARGB = (200 shl 24) or (255 shl 16) or 229

    fun build(
        grid: DepthGrid,
        maxDepthM: Float = DepthConstants.LOW_DEPTH_WARNING_MAX_M.toFloat()
    ): Bitmap {
        val w = grid.cols
        val h = grid.rows
        val colors = IntArray(w * h)
        for (r in 0 until h) {
            val outRow = (h - 1 - r) * w   // flip south-up grid → top-down bitmap
            for (c in 0 until w) {
                val d = grid.depthRaw(r, c)
                colors[outRow + c] = if (!d.isNaN() && d >= 0f && d < maxDepthM) WARNING_ARGB else 0
            }
        }
        return Bitmap.createBitmap(colors, w, h, Bitmap.Config.ARGB_8888)
    }
}
