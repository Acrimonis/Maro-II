package ykws.android.maro.data.depth

import ykws.android.maro.data.model.DepthGrid
import ykws.android.maro.data.model.DepthSource
import ykws.android.maro.data.model.Isobath
import ykws.android.maro.data.model.IsobathLine
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.spatial.SpatialOperations
import kotlin.math.roundToInt

/**
 * Derives isobath polylines from a [DepthGrid] via scalar marching squares + Douglas-Peucker
 * simplification, each line tagged with the **data source + confidence** under it so the UI can
 * reflect data precision. Two precision rules:
 *  - **Suppress fine-over-coarse:** "fine" levels (≤ [DepthConstants.ISOBATH_FINE_LEVEL_MAX_M]) are
 *    traced on a copy of the field with **coarse-source cells masked to NaN**, so a 2 m contour is
 *    never faked from 115 m EMODnet — fine contours form only where the source resolution is fine
 *    (≤ [DepthConstants.ISOBATH_FINE_MAX_RES_M], i.e. Litto3D / SDB).
 *  - **Tag by source/confidence:** every surviving line carries its dominant source + median
 *    confidence (sampled from the cells it crosses) for colour-by-source styling.
 * Pure (JVM-testable); the result is rendered as zoom-gated polylines.
 */
object DepthIsobaths {

    fun build(
        grid: DepthGrid,
        levels: List<Float> = DepthConstants.ISOBATH_LEVELS,
        epsilonM: Double = DepthConstants.ISOBATH_EPSILON_M
    ): List<Isobath> {
        // Field for the FINE levels: coarse-source cells erased so fine contours only form over fine
        // data. Built once (lazily) and shared across every fine level.
        val fineField = lazy { maskCoarseSources(grid) }
        val out = ArrayList<Isobath>(levels.size)
        for (level in levels) {
            val field =
                if (level <= DepthConstants.ISOBATH_FINE_LEVEL_MAX_M) fineField.value else grid.depths
            val raw = SpatialOperations.marchingSquaresScalar(field, grid.cols, grid.rows, level.toDouble())
            if (raw.isEmpty()) continue
            val lines = raw.mapNotNull { line ->
                val ll = SpatialOperations.gridLineToLatLng(
                    line,
                    latSouth = grid.boundingBox.latSouth,
                    lonWest = grid.boundingBox.lonWest,
                    cellSizeDegLat = grid.cellSizeDegLat,
                    cellSizeDegLon = grid.cellSizeDegLon
                )
                val simplified = SpatialOperations.douglasPeucker(ll, epsilonM)
                if (simplified.size < 2) return@mapNotNull null
                val (source, confidence) = sampleSourceConfidence(line, grid)
                // Smooth only fine-source lines (Litto3D/SDB); coarse contours stay angular, which
                // honestly signals their coarseness.
                val geom = if (source.nominalResM <= DepthConstants.ISOBATH_FINE_MAX_RES_M)
                    smoothLine(simplified, DepthConstants.ISOBATH_SMOOTH_ITERATIONS)
                else simplified
                IsobathLine(geom, source, confidence)
            }
            if (lines.isNotEmpty()) out.add(Isobath(level, lines))
        }
        return out
    }

    /** Copy of the depth field with every cell whose source is coarser than the fine threshold set
     *  to NaN, so marching squares skips it (NaN = nodata boundary). */
    private fun maskCoarseSources(grid: DepthGrid): FloatArray {
        val out = grid.depths.copyOf()
        val n = grid.rows * grid.cols
        for (i in 0 until n) {
            if (out[i].isNaN()) continue
            if (DepthSource.fromId(grid.source[i].toInt()).nominalResM > DepthConstants.ISOBATH_FINE_MAX_RES_M) {
                out[i] = Float.NaN
            }
        }
        return out
    }

    /** Dominant source + median confidence of the cells a grid-space contour line crosses. */
    private fun sampleSourceConfidence(
        line: List<SpatialOperations.GridPt>,
        grid: DepthGrid
    ): Pair<DepthSource, Int> {
        val counts = HashMap<DepthSource, Int>()
        val confs = ArrayList<Int>(line.size)
        for (p in line) {
            val r = p.row.roundToInt().coerceIn(0, grid.rows - 1)
            val c = p.col.roundToInt().coerceIn(0, grid.cols - 1)
            if (!grid.hasData(r, c)) continue
            counts[grid.sourceAt(r, c)] = (counts[grid.sourceAt(r, c)] ?: 0) + 1
            confs.add(grid.confidenceAt(r, c))
        }
        val source = counts.maxByOrNull { it.value }?.key ?: DepthSource.NONE
        val confidence = if (confs.isEmpty()) 0 else confs.sorted()[confs.size / 2]
        return source to confidence
    }

    /** Chaikin-smooth an isobath polyline; closed rings (first == last) keep their closure. */
    private fun smoothLine(line: List<LatLng>, iterations: Int): List<LatLng> {
        if (line.size < 3 || iterations <= 0) return line
        val closed = line.first().latitude == line.last().latitude &&
            line.first().longitude == line.last().longitude
        return if (closed) {
            val ring = SpatialOperations.chaikin(line.dropLast(1), iterations, closed = true)
            ring + ring.first()
        } else {
            SpatialOperations.chaikin(line, iterations, closed = false)
        }
    }
}
