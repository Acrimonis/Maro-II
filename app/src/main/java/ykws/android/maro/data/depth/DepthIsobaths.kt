package ykws.android.maro.data.depth

import ykws.android.maro.data.model.DepthGrid
import ykws.android.maro.data.model.Isobath
import ykws.android.maro.spatial.SpatialOperations

/**
 * Derives isobath polylines from a [DepthGrid] via scalar marching squares + Douglas-Peucker
 * simplification. Pure (JVM-testable); the result is rendered as zoom-gated polylines.
 */
object DepthIsobaths {

    fun build(
        grid: DepthGrid,
        levels: List<Float> = DepthConstants.ISOBATH_LEVELS,
        epsilonM: Double = DepthConstants.ISOBATH_EPSILON_M
    ): List<Isobath> {
        val out = ArrayList<Isobath>(levels.size)
        for (level in levels) {
            val raw = SpatialOperations.marchingSquaresScalar(
                field = grid.depths, cols = grid.cols, rows = grid.rows, level = level.toDouble()
            )
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
                if (simplified.size >= 2) simplified else null
            }
            if (lines.isNotEmpty()) out.add(Isobath(level, lines))
        }
        return out
    }
}
