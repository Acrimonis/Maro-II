package ykws.android.maro.data.temperature

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** The two things the temperature tile reads. */
enum class TemperatureSource { AIR, WATER }

/**
 * One cell centre of a source's grids: the key a value is cached under and the point it is fetched at.
 *
 * [grid] is 0 for grid A and 1 for grid B; [col] and [row] are the centre's place on that grid, counted
 * from the home port.
 */
data class SamplePoint(
    val source: TemperatureSource,
    val grid: Int,
    val col: Int,
    val row: Int,
    val latitude: Double,
    val longitude: Double
) {
    /** The cache key: unique per source, grid and place. */
    val key: String get() = "${source.name.lowercase()}:${if (grid == 0) 'A' else 'B'}:$col:$row"
}

/**
 * The cache geometry of one source: two grids of square cells of side [sideM], grid A anchored at the
 * home port ([originLat], [originLon]) and grid B the same grid shifted half a side on both axes.
 *
 * Every cell's four corners are centres of the other grid, so the two grids' centres together form a
 * lattice whose farthest point from its nearest centre is half a side. A position is answered by the
 * centre nearest to it, across both grids.
 *
 * Positions are projected flat around the home port: a few tens of kilometres of boating stay well inside
 * what a cell key needs, and the same projection always gives the same key for the same place.
 */
class TemperatureGrid(
    val source: TemperatureSource,
    private val originLat: Double,
    private val originLon: Double,
    val sideM: Double
) {
    private val metresPerDegLat = METRES_PER_DEG
    private val metresPerDegLon = METRES_PER_DEG * cos(Math.toRadians(originLat))

    /**
     * The [count] centres nearest to ([latitude], [longitude]), nearest first. Ties break on grid, then
     * column, then row, so the answer never depends on iteration order.
     */
    fun nearest(latitude: Double, longitude: Double, count: Int): List<SamplePoint> {
        require(count >= 1) { "count must be at least 1" }
        val x = (longitude - originLon) * metresPerDegLon
        val y = (latitude - originLat) * metresPerDegLat
        // Two grids share the plane, so about two centres per cell: this many cells each way hold `count`.
        val reach = ceil(sqrt(count / (2.0 * PI))).toInt() + 2
        val candidates = buildList {
            for (grid in 0..1) {
                val shift = if (grid == 0) 0.0 else 0.5
                val c0 = (x / sideM - shift).roundToInt()
                val r0 = (y / sideM - shift).roundToInt()
                for (col in (c0 - reach)..(c0 + reach)) {
                    for (row in (r0 - reach)..(r0 + reach)) {
                        val cx = (col + shift) * sideM
                        val cy = (row + shift) * sideM
                        val d2 = (cx - x) * (cx - x) + (cy - y) * (cy - y)
                        add(Ranked(d2, grid, col, row, cx, cy))
                    }
                }
            }
        }
        return candidates
            .sortedWith(compareBy<Ranked> { it.d2 }.thenBy { it.grid }.thenBy { it.col }.thenBy { it.row })
            .take(count)
            .map {
                SamplePoint(
                    source = source,
                    grid = it.grid,
                    col = it.col,
                    row = it.row,
                    latitude = originLat + it.cy / metresPerDegLat,
                    longitude = originLon + it.cx / metresPerDegLon
                )
            }
    }

    private class Ranked(val d2: Double, val grid: Int, val col: Int, val row: Int, val cx: Double, val cy: Double)

    /** Distance in metres from ([latitude], [longitude]) to [point], on the same flat projection. */
    fun distanceM(latitude: Double, longitude: Double, point: SamplePoint): Double {
        val dx = (point.longitude - longitude) * metresPerDegLon
        val dy = (point.latitude - latitude) * metresPerDegLat
        return sqrt(dx * dx + dy * dy)
    }

    private companion object {
        const val METRES_PER_DEG = 111_320.0
    }
}
