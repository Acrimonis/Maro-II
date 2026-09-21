package ykws.android.maro.route

import ykws.android.maro.data.model.DepthGrid
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.spatial.CoastlineSpatialIndex
import ykws.android.maro.spatial.SpatialOperations
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** A straight piece of an obstacle boundary, in geographic coordinates. */
data class RouteSegment(val a: LatLng, val b: LatLng)

/**
 * The exclusion set of plan §10.2, §10.3 and §11.2, built over a corridor.
 *
 * The corridor is a **window onto the depth grid** — the same 25 m cells, the same row-major order,
 * no resampling — so the depth gate, the per-cell source trust and the standoff dilation are all
 * exact in the grid's own terms (plan §4: the 2 m gate must inherit `gatedForEmondnetShallow`, not
 * contradict it).
 *
 * Two masks are kept apart on purpose:
 *  - [blocked] is feasibility before any standoff: land, NoData, and water shallower than
 *    `config.minDepthM`. This is what §4 calls "an impassable cell is not a very expensive cell".
 *  - the forbidden set the search actually honours is [blocked] dilated by `standoffCells` when D3
 *    says the standoff is hard (`config.hardStandoff`), and [blocked] itself when it says the
 *    standoff is priced — the two variants §13.4 asks the harness to compare.
 *
 * Dilating the raster rather than offsetting vectors is §10.2's own wording — "dilate the exclusion
 * set by the clearance … **before** extracting the contours" — and it reuses
 * [`SpatialOperations.marchingSquares`](app/src/main/java/ykws/android/maro/spatial/SpatialOperations.kt:550)
 * for the boundary, so the only new geometry is the window and the kernel.
 */
class RouteExclusion private constructor(
    val grid: DepthGrid,
    val coast: CoastlineSpatialIndex,
    val config: RouteConfig,
    /** First depth-grid row / column of the window. */
    val r0: Int,
    val c0: Int,
    val rows: Int,
    val cols: Int,
    /** Dilation radius in cells; 0 when the standoff is priced rather than hard. */
    val standoffCells: Int,
    /** Metres one cell covers on its shorter axis — what [standoffCells] was derived from. */
    val cellMetres: Double,
    private val blocked: BooleanArray,
    private val forbidden: BooleanArray,
    /** Boundary of the forbidden set, as straight segments. */
    val segments: List<RouteSegment>,
    /** Simplified boundary vertices — the harvest of §10.3 step 2. */
    val vertices: List<LatLng>,
    /** Simplification tolerance the harvest actually ended at (it grows to fit `config.maxVertices`). */
    val epsilonM: Double,
) {

    /** Geographic extent of the window. */
    val bbox: BBox = BBox(
        latSouth = grid.boundingBox.latSouth + r0 * grid.cellSizeDegLat,
        latNorth = grid.boundingBox.latSouth + (r0 + rows) * grid.cellSizeDegLat,
        lonWest = grid.boundingBox.lonWest + c0 * grid.cellSizeDegLon,
        lonEast = grid.boundingBox.lonWest + (c0 + cols) * grid.cellSizeDegLon,
    )

    /** Centre latitude of window row [i]. */
    fun latOf(i: Int): Double = grid.cellCenterLat(r0 + i)

    /** Centre longitude of window column [j]. */
    fun lonOf(j: Int): Double = grid.cellCenterLon(c0 + j)

    private fun rowOf(lat: Double): Int =
        Math.floor((lat - grid.boundingBox.latSouth) / grid.cellSizeDegLat).toInt() - r0

    private fun colOf(lon: Double): Int =
        Math.floor((lon - grid.boundingBox.lonWest) / grid.cellSizeDegLon).toInt() - c0

    private fun inWindow(r: Int, c: Int): Boolean = r in 0 until rows && c in 0 until cols

    /**
     * Feasibility, before any standoff: true outside the window, so a query that falls off the
     * raster fails closed rather than reading as open water (plan §4, "a route that leaves the grid
     * must say so rather than assume deep water").
     */
    fun isBlocked(lat: Double, lon: Double): Boolean {
        val r = rowOf(lat)
        val c = colOf(lon)
        if (!inWindow(r, c)) return true
        return blocked[r * cols + c]
    }

    /** True where the search may not pass: [isBlocked] plus the hard standoff when D3 asks for one. */
    fun isForbidden(lat: Double, lon: Double): Boolean {
        val r = rowOf(lat)
        val c = colOf(lon)
        if (!inWindow(r, c)) return true
        return forbidden[r * cols + c]
    }

    /** True when the point sits inside the window at all — the corridor's own bounds test. */
    fun isInside(lat: Double, lon: Double): Boolean = inWindow(rowOf(lat), colOf(lon))

    /**
     * Raw depth at the cell holding the point (m, positive down), or NaN off-grid. Read through
     * [DepthGrid.depthGated], so a coarse EMODnet cell in the shallow band reads as NoData.
     */
    fun depthAt(lat: Double, lon: Double): Float {
        val r = rowOf(lat) + r0
        val c = colOf(lon) + c0
        if (r < 0 || r >= grid.rows || c < 0 || c >= grid.cols) return Float.NaN
        return grid.depthGated(r, c, config.emodnetCutoffM)
    }

    /**
     * Chebyshev clearance to the nearest blocked cell, in cells, capped at [maxCells]. Cheap (a
     * single ring scan at the shipped 25 m) and used by the priced-standoff variant, where the
     * standoff is a penalty rising as the boundary nears rather than a wall (§11.2).
     */
    fun clearanceCells(lat: Double, lon: Double, maxCells: Int = 4): Int {
        val r0q = rowOf(lat)
        val c0q = colOf(lon)
        for (k in 0..maxCells) {
            for (r in (r0q - k)..(r0q + k)) {
                for (c in (c0q - k)..(c0q + k)) {
                    // Ring only: the interior was checked at a smaller k.
                    if (k > 0 && max(abs(r - r0q), abs(c - c0q)) != k) continue
                    if (!inWindow(r, c)) return k
                    if (blocked[r * cols + c]) return k
                }
            }
        }
        return maxCells + 1
    }

    /** Fraction of the standoff still available at a point: 1 well clear, 0 at the blocked set. */
    fun standoffRatio(lat: Double, lon: Double): Double {
        if (standoffCells <= 0) return 1.0
        val k = clearanceCells(lat, lon, standoffCells)
        return (k.toDouble() / standoffCells.toDouble()).coerceIn(0.0, 1.0)
    }

    companion object {

        /**
         * Builds the exclusion set for [bbox], clipped to the depth grid.
         *
         * @param standoffM clearance the hard variant dilates by; the caller passes a fallback value
         *                  when it re-runs a closed passage (§11.2), so the two tiers never blend.
         */
        fun build(
            grid: DepthGrid,
            coast: CoastlineSpatialIndex,
            config: RouteConfig,
            bbox: BBox,
            standoffM: Double = config.standoffM,
        ): RouteExclusion {
            val container = RouteCorridor.gridBbox(grid)
            val window = RouteCorridor.clipTo(bbox, container)
                ?: throw IllegalArgumentException("corridor $bbox does not overlap the depth grid")

            // A cell's metre size: the shorter axis decides the kernel radius, so the dilation is
            // never smaller than the standoff asked for.
            val cellLatM = grid.cellSizeDegLat * RouteConfig.M_PER_DEG_LAT
            val cellLonM = grid.cellSizeDegLon * RouteConfig.M_PER_DEG_LAT *
                Math.cos(Math.toRadians((window.latSouth + window.latNorth) / 2.0))
            val cellMetres = min(cellLatM, cellLonM)
            val standoffCells = if (config.hardStandoff && standoffM > 0.0) {
                ceil(standoffM / cellMetres).toInt()
            } else 0

            // The window is widened so the dilation kernel and its contour have room inside it.
            val extra = standoffCells + 1
            val rowRange = RouteCorridor.rowsFor(grid, window, extra)
            val colRange = RouteCorridor.colsFor(grid, window, extra)
            val rows = rowRange.last - rowRange.first + 1
            val cols = colRange.last - colRange.first + 1
            val r0 = rowRange.first
            val c0 = colRange.first

            val blocked = BooleanArray(rows * cols)
            for (i in 0 until rows) {
                val lat = grid.cellCenterLat(r0 + i)
                for (j in 0 until cols) {
                    val lon = grid.cellCenterLon(c0 + j)
                    val depth = grid.depthGated(r0 + i, c0 + j, config.emodnetCutoffM)
                    var isBlocked = depth.isNaN() || depth < config.minDepthM.toFloat()
                    // The depth gate already covers land (above-datum cells read ≤ 0); the coastline
                    // is the backstop where a land cell carries no depth at all.
                    if (!isBlocked && coast.hasData && !coast.isWater(lat, lon)) isBlocked = true
                    blocked[i * cols + j] = isBlocked
                }
            }

            val forbidden = if (standoffCells > 0) dilate(blocked, rows, cols, standoffCells) else blocked

            fun latOfRaw(i: Int): Double = grid.cellCenterLat(r0 + i)

            fun lonOfRaw(j: Int): Double = grid.cellCenterLon(c0 + j)

            val contours = SpatialOperations
                .marchingSquares(forbidden, cols, rows)
                .map { line ->
                    SpatialOperations.gridLineToLatLng(
                        line,
                        latSouth = grid.boundingBox.latSouth + r0 * grid.cellSizeDegLat,
                        lonWest = grid.boundingBox.lonWest + c0 * grid.cellSizeDegLon,
                        cellSizeDegLat = grid.cellSizeDegLat,
                        cellSizeDegLon = grid.cellSizeDegLon,
                    )
                }
                .filter { it.size >= 2 }

            val cellForClosing = max(cellLatM, cellLonM) * 1.5
            var epsilon = config.harvestEpsilonM
            var simplified = simplify(contours, epsilon)
            var guard = 0
            while (simplified.sumOf { it.size } > config.maxVertices && guard++ < 12) {
                epsilon *= 2.0
                simplified = simplify(contours, epsilon)
            }

            // A contour vertex sits exactly on the dilated boundary, where a point test on a cell mask
            // is half a cell ambiguous: floor() would put roughly half of them inside the band and the
            // taut path could not hug the margin at all. Graph nodes are therefore snapped to the
            // nearest free cell centre, so every node is genuinely outside the forbidden set and the
            // effective clearance is cell-rounded rather than undefined.
            fun freeCellNear(lat: Double, lon: Double): LatLng? {
                val rq = Math.floor((lat - grid.boundingBox.latSouth) / grid.cellSizeDegLat).toInt() - r0
                val cq = Math.floor((lon - grid.boundingBox.lonWest) / grid.cellSizeDegLon).toInt() - c0
                for (ring in 0..2) {
                    for (r in (rq - ring)..(rq + ring)) {
                        for (c in (cq - ring)..(cq + ring)) {
                            if (ring > 0 && max(abs(r - rq), abs(c - cq)) != ring) continue
                            if (r !in 0 until rows || c !in 0 until cols) continue
                            if (!forbidden[r * cols + c]) return LatLng(latOfRaw(r), lonOfRaw(c))
                        }
                    }
                }
                return null
            }

            val segments = ArrayList<RouteSegment>()
            val vertices = LinkedHashSet<LatLng>()
            for (chain in simplified) {
                for (k in 0 until chain.size - 1) {
                    segments.add(RouteSegment(chain[k], chain[k + 1]))
                }
                // A ring that marched squares returned without returning to its first point closes
                // itself; the ends of such a chain are neighbours and the gap is one cell wide.
                if (chain.size > 3 &&
                    SpatialOperations.haversine(chain.first(), chain.last()) <= cellForClosing
                ) {
                    segments.add(RouteSegment(chain.last(), chain.first()))
                }
                for (point in chain) {
                    freeCellNear(point.latitude, point.longitude)?.let { vertices.add(it) }
                }
            }

            return RouteExclusion(
                grid = grid,
                coast = coast,
                config = config,
                r0 = r0,
                c0 = c0,
                rows = rows,
                cols = cols,
                standoffCells = standoffCells,
                cellMetres = cellMetres,
                blocked = blocked,
                forbidden = forbidden,
                segments = segments,
                vertices = vertices.toList(),
                epsilonM = epsilon,
            )
        }

        private fun simplify(contours: List<List<LatLng>>, epsilonM: Double): List<List<LatLng>> =
            contours.map { SpatialOperations.douglasPeucker(it, epsilonM) }.filter { it.size >= 2 }

        /**
         * Binary max-filter over a `rows × cols` mask, by square window of radius [k] — a box
         * kernel, so the clearance is at least [k] cells on the tight axis and up to `k·√2` on the
         * diagonal. Stated rather than hidden: the contour it produces is where a taut path bends.
         */
        private fun dilate(mask: BooleanArray, rows: Int, cols: Int, k: Int): BooleanArray {
            val prefix = IntArray((rows + 1) * (cols + 1))
            for (i in 0 until rows) {
                var rowSum = 0
                for (j in 0 until cols) {
                    if (mask[i * cols + j]) rowSum++
                    prefix[(i + 1) * (cols + 1) + (j + 1)] = prefix[i * (cols + 1) + (j + 1)] + rowSum
                }
            }
            val out = BooleanArray(rows * cols)
            for (i in 0 until rows) {
                val iLo = max(0, i - k)
                val iHi = min(rows - 1, i + k)
                for (j in 0 until cols) {
                    val jLo = max(0, j - k)
                    val jHi = min(cols - 1, j + k)
                    val sum = prefix[(iHi + 1) * (cols + 1) + (jHi + 1)] -
                        prefix[iLo * (cols + 1) + (jHi + 1)] -
                        prefix[(iHi + 1) * (cols + 1) + jLo] +
                        prefix[iLo * (cols + 1) + jLo]
                    out[i * cols + j] = sum > 0
                }
            }
            return out
        }
    }
}
