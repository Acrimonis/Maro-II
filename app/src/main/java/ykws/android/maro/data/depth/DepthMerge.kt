package ykws.android.maro.data.depth

import ykws.android.maro.data.depth.raster.SourceRaster
import ykws.android.maro.data.model.DepthGrid
import ykws.android.maro.data.model.DepthSource
import ykws.android.maro.data.model.MutableDepthGrid

/**
 * Pure merge functions that fold heterogeneous sources onto one common grid.
 *
 * Two depth-tier rules (see DepthMappingDesign.md):
 * - **Deep (best-resolution-wins):** the finest-resolution source wins → sharp ridges/pinnacles.
 * - **Shallow (shoalest-wins):** in the collision band a source is authoritative only
 *   ≤ `shallowTierMaxM`, and the *shallowest* candidate wins → conservative, never deeper
 *   than the most pessimistic source.
 *
 * A NaN source value never overwrites a good cell.
 */
object DepthMerge {

    /**
     * Deep merge: sample [src] at each target cell centre. Write where the target cell is
     * empty, or where [src] is finer-resolution than the source currently in that cell.
     */
    fun mergeDeep(target: MutableDepthGrid, src: SourceRaster) {
        for (r in 0 until target.rows) {
            for (c in 0 until target.cols) {
                val v = src.sampleAt(target.cellCenterLat(r), target.cellCenterLon(c))
                if (v.isNaN()) continue
                val cur = target.get(r, c)
                val curRes = if (cur.isNaN()) Double.MAX_VALUE else target.sourceAt(r, c).nominalResM
                if (cur.isNaN() || src.resM < curRes) {
                    target.set(r, c, v, src.source, src.source.seedConfidence)
                }
            }
        }
    }

    /**
     * Shallow shoalest-wins merge. Iterates the [shallow] grid (e.g. baked Litto3D); each
     * shallow cell with depth ≤ [shallowTierMaxM] maps to the target cell containing it and
     * keeps the **shoalest** value. Conservative within the footprint (collision-safe).
     */
    fun mergeShallowShoalest(
        target: MutableDepthGrid,
        shallow: DepthGrid,
        shallowTierMaxM: Double,
        tag: DepthSource = DepthSource.LITTO3D
    ) {
        for (sr in 0 until shallow.rows) {
            for (sc in 0 until shallow.cols) {
                val v = shallow.depthRaw(sr, sc)
                if (v.isNaN() || v > shallowTierMaxM) continue
                val lat = shallow.cellCenterLat(sr)
                val lon = shallow.cellCenterLon(sc)
                val tr = ((lat - target.boundingBox.latSouth) / target.cellSizeDegLat).toInt()
                val tc = ((lon - target.boundingBox.lonWest) / target.cellSizeDegLon).toInt()
                if (tr < 0 || tr >= target.rows || tc < 0 || tc >= target.cols) continue
                val cur = target.get(tr, tc)
                if (cur.isNaN() || v < cur) {
                    val conf = shallow.confidenceAt(sr, sc).let { if (it > 0) it else tag.seedConfidence }
                    target.set(tr, tc, v, tag, conf)
                }
            }
        }
    }

    /** Fill only NoData cells from [src] (e.g. GEBCO fallback). Reduced confidence. */
    fun fillGaps(target: MutableDepthGrid, src: SourceRaster) {
        for (r in 0 until target.rows) {
            for (c in 0 until target.cols) {
                if (!target.get(r, c).isNaN()) continue
                val v = src.sampleAt(target.cellCenterLat(r), target.cellCenterLon(c))
                if (v.isNaN()) continue
                target.set(r, c, v, src.source, src.source.seedConfidence / 2)
            }
        }
    }
}
