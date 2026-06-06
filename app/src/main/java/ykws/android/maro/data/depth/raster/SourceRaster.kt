package ykws.android.maro.data.depth.raster

import ykws.android.maro.data.model.BoundingBox
import ykws.android.maro.data.model.DepthSource
import kotlin.math.floor

/**
 * A neutral in-memory raster returned by a source client (EMODnet, GEBCO, a baked
 * Litto3D tile, …) before it is merged onto the common [ykws.android.maro.data.model.DepthGrid].
 *
 * [values] are **metres below chart datum (positive-down)**, NaN = NoData; row 0 = south,
 * col 0 = west, row-major. Clients normalise their native sign/datum to this convention.
 *
 * @property resM nominal native resolution in metres (drives best-resolution merges).
 * @property source provenance tag stamped onto merged cells.
 */
class SourceRaster(
    val bbox: BoundingBox,
    val rows: Int,
    val cols: Int,
    val cellSizeDegLat: Double,
    val cellSizeDegLon: Double,
    val values: FloatArray,
    val resM: Double,
    val source: DepthSource
) {
    fun idx(r: Int, c: Int): Int = r * cols + c

    /** Bilinear sample at a geographic point; skips NaN neighbours; NaN if no coverage. */
    fun sampleAt(lat: Double, lon: Double): Float {
        if (rows == 0 || cols == 0) return Float.NaN
        val fr = (lat - bbox.latSouth) / cellSizeDegLat - 0.5
        val fc = (lon - bbox.lonWest) / cellSizeDegLon - 0.5
        val r0 = floor(fr).toInt()
        val c0 = floor(fc).toInt()
        val tr = fr - r0
        val tc = fc - c0
        var wSum = 0.0
        var vSum = 0.0
        for (dr in 0..1) for (dc in 0..1) {
            val r = r0 + dr
            val c = c0 + dc
            if (r < 0 || r >= rows || c < 0 || c >= cols) continue
            val v = values[idx(r, c)]
            if (v.isNaN()) continue
            val w = (if (dr == 0) 1.0 - tr else tr) * (if (dc == 0) 1.0 - tc else tc)
            if (w <= 0.0) continue
            wSum += w
            vSum += w * v
        }
        return if (wSum <= 0.0) Float.NaN else (vSum / wSum).toFloat()
    }
}
