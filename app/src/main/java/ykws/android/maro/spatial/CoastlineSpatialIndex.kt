package ykws.android.maro.spatial

import ykws.android.maro.data.model.CoastlineDistanceResult
import ykws.android.maro.data.model.CoastlineSegment
import ykws.android.maro.data.model.LatLng
import kotlin.math.*

/**
 * Uniform-grid spatial index for O(1) nearest-coastline distance queries.
 *
 * ## Algorithm
 *
 * **Build phase** (once after coastline is loaded):
 * 1. Compute the bounding box of all coastline segments.
 * 2. Partition it into a uniform grid of [cellSizeM] × [cellSizeM] cells.
 * 3. For each segment a→b, compute its axis-aligned bounding box and insert
 *    its index into every grid cell that overlaps that AABB.
 * 4. Store as a sparse `HashMap<GridCell, List<Int>>` — only occupied cells
 *    consume memory (~30 KB for the Nice–Fréjus coastline).
 *
 * **Query phase** (per GPS fix):
 * 1. Hash `(lat, lon)` → `(row, col)`.
 * 2. Collect candidate segment indices from the cell and its 8 Moore
 *    neighbours (ring 1). If empty, expand outward ring by ring.
 * 3. For each candidate, compute [`SpatialOperations.pointToSegmentDistance`]
 *    and [`SpatialOperations.projectPointOntoSegment`].
 * 4. Return the minimum distance and the corresponding closest point.
 *
 * ## Performance (Nice–Fréjus, ≈15 000 segments)
 *
 * | Metric        | Value          |
 * |---------------|----------------|
 * | Per-query CPU | ~0.03–0.1 ms   |
 * | Speedup vs. brute-force | 80–150× |
 * | Memory        | ~30–40 KB      |
 * | Build time    | ~1–3 ms        |
 *
 * @param segments  The coastline polylines loaded from [CoastlineRepository].
 *                  Index 0 is always the mainland; indices 1..N are islands.
 * @param cellSizeM Grid cell size in meters (default 500 m). Tune for the
 *                  memory/performance trade-off.
 */
class CoastlineSpatialIndex(
    segments: List<CoastlineSegment>,
    private val cellSizeM: Double = 500.0
) {

    // ── Internal types ───────────────────────────────────────────────────────

    /** Flat descriptor for a single segment a→b. */
    private data class SegmentRef(
        val polylineIdx: Int,
        val vertexIdx: Int,
        val a: LatLng,
        val b: LatLng,
        val minLat: Double,
        val maxLat: Double,
        val minLon: Double,
        val maxLon: Double
    )

    /** Row-major grid cell key. */
    private data class GridCell(val row: Int, val col: Int)

    // ── State ────────────────────────────────────────────────────────────────

    /** All segments flattened from all polylines, in order. */
    private val segmentRefs: List<SegmentRef>

    /** Sparse map: grid cell → indices into [segmentRefs]. */
    private val grid: Map<GridCell, List<Int>>

    /** The original coastline segments (needed for segment IDs). */
    private val segmentsById: List<CoastlineSegment>

    // Bounding box
    private val minLat: Double
    private val maxLat: Double
    private val minLon: Double
    private val maxLon: Double

    // Cell dimensions in degrees
    private val cellSizeLat: Double
    private val cellSizeLon: Double

    // Grid dimensions
    private val rowCount: Int
    private val colCount: Int

    /**
     * Per-polyline closed-ring flag, indexed by `polylineIdx`. A polyline is a **ring**
     * (island) when it is geometrically closed (first vertex ≈ last vertex); otherwise
     * it is **open mainland coast**. This is derived from geometry rather than the
     * [CoastlineSegment.isClosed] flag on purpose: the generator currently marks
     * reclassified open mainland fragments as `isClosed = true`, and treating such a
     * fragment as a ring is exactly what leaves a land-side band ("land-mirror"). The
     * geometric test classifies every genuinely-open coast piece as mainland.
     */
    private val isRingPoly: BooleanArray

    /**
     * Longitude span of the **open mainland coast** (all non-ring polylines combined) —
     * the extent of the virtual inland "cap" edge that closes the open coast into a land
     * polygon for [isWater]. `min > max` (empty range) ⇒ no mainland present.
     */
    private val mainlandLonMin: Double
    private val mainlandLonMax: Double

    /** `true` when the index has coastline data to query. */
    val hasData: Boolean get() = segmentRefs.isNotEmpty()

    // ── Constructor (build phase) ────────────────────────────────────────────

    init {
        segmentsById = segments

        if (segments.isEmpty() || segments.all { it.points.size < 2 }) {
            // No valid coastline data — index is inert
            segmentRefs = emptyList()
            grid = emptyMap()
            minLat = 0.0; maxLat = 0.0; minLon = 0.0; maxLon = 0.0
            cellSizeLat = 1.0; cellSizeLon = 1.0
            rowCount = 0; colCount = 0
            mainlandLonMin = 1.0; mainlandLonMax = -1.0   // empty range ⇒ no cap
            isRingPoly = BooleanArray(0)
        } else {
            // — 1. Global bounding box —
            var bMinLat = Double.MAX_VALUE
            var bMaxLat = Double.MIN_VALUE
            var bMinLon = Double.MAX_VALUE
            var bMaxLon = Double.MIN_VALUE

            for (seg in segments) {
                for (pt in seg.points) {
                    val lat = pt.lat.toDouble()
                    val lon = pt.lon.toDouble()
                    if (lat < bMinLat) bMinLat = lat
                    if (lat > bMaxLat) bMaxLat = lat
                    if (lon < bMinLon) bMinLon = lon
                    if (lon > bMaxLon) bMaxLon = lon
                }
            }

            // 0.5 % padding to avoid floating-point boundary misses
            val padLat = (bMaxLat - bMinLat) * 0.005
            val padLon = (bMaxLon - bMinLon) * 0.005
            minLat = bMinLat - padLat
            maxLat = bMaxLat + padLat
            minLon = bMinLon - padLon
            maxLon = bMaxLon + padLon

            // — 2. Cell size in degrees —
            val midLat = (minLat + maxLat) / 2.0
            val mPerDegLat = SpatialOperations.EARTH_RADIUS_M * PI / 180.0
            val mPerDegLon = mPerDegLat * cos(Math.toRadians(midLat))
            cellSizeLat = cellSizeM / mPerDegLat
            cellSizeLon = cellSizeM / mPerDegLon

            rowCount = max(1, ceil((maxLat - minLat) / cellSizeLat).toInt())
            colCount = max(1, ceil((maxLon - minLon) / cellSizeLon).toInt())

            // — 3. Flatten segments —
            val refs = mutableListOf<SegmentRef>()
            for ((polyIdx, seg) in segments.withIndex()) {
                val pts = seg.points
                for (i in 0 until pts.size - 1) {
                    val a = LatLng(pts[i].lat.toDouble(), pts[i].lon.toDouble())
                    val b = LatLng(pts[i + 1].lat.toDouble(), pts[i + 1].lon.toDouble())
                    refs.add(
                        SegmentRef(
                            polylineIdx = polyIdx,
                            vertexIdx = i,
                            a = a, b = b,
                            minLat = min(a.latitude,  b.latitude),
                            maxLat = max(a.latitude,  b.latitude),
                            minLon = min(a.longitude, b.longitude),
                            maxLon = max(a.longitude, b.longitude)
                        )
                    )
                }
            }
            segmentRefs = refs

            // Per-polyline ring detection (island = geometrically closed) + the cap's
            // longitude extent (the combined span of all OPEN mainland-coast polylines).
            isRingPoly = BooleanArray(segments.size) { polyIdx ->
                val pts = segments[polyIdx].points
                pts.size >= 3 &&
                    abs(pts.first().lat - pts.last().lat) < RING_CLOSE_EPS_DEG &&
                    abs(pts.first().lon - pts.last().lon) < RING_CLOSE_EPS_DEG
            }
            var mlMin = Double.MAX_VALUE; var mlMax = -Double.MAX_VALUE
            for (r in refs) if (!isRingPoly[r.polylineIdx]) {
                if (r.minLon < mlMin) mlMin = r.minLon
                if (r.maxLon > mlMax) mlMax = r.maxLon
            }
            mainlandLonMin = mlMin; mainlandLonMax = mlMax

            // — 4. Build sparse grid —
            val gridBuilder = HashMap<GridCell, MutableList<Int>>()
            for ((segIdx, ref) in refs.withIndex()) {
                val minRow = ((ref.minLat - minLat) / cellSizeLat).toInt().coerceIn(0, rowCount - 1)
                val maxRow = ((ref.maxLat - minLat) / cellSizeLat).toInt().coerceIn(0, rowCount - 1)
                val minCol = ((ref.minLon - minLon) / cellSizeLon).toInt().coerceIn(0, colCount - 1)
                val maxCol = ((ref.maxLon - minLon) / cellSizeLon).toInt().coerceIn(0, colCount - 1)

                for (r in minRow..maxRow) {
                    for (c in minCol..maxCol) {
                        gridBuilder.getOrPut(GridCell(r, c)) { mutableListOf() }.add(segIdx)
                    }
                }
            }
            grid = gridBuilder
        }
    }

    // ── Query ────────────────────────────────────────────────────────────────

    /**
     * Finds the closest coastline point to `(latitude, longitude)`.
     *
     * Expands the search box outward ring by ring, accumulating the running nearest
     * segment, and **stops only when it is provably safe**: once a candidate is found
     * AND `bestDistance ≤ ring × cellSize`, no segment in any still-unexplored cell
     * can be closer (the nearest point of a cell beyond `ring` is at least
     * `ring × cellSize` away). Stopping at merely the first non-empty ring — as an
     * earlier version did — could miss a closer segment one ring further out, causing
     * over-estimates and discontinuous "jumps" in the value as the query point moves.
     *
     * @return [CoastlineDistanceResult] with distance, closest point, segment ID, and
     *         whether it's on the mainland. `distanceMeters = Double.MAX_VALUE` when
     *         no coastline is loaded.
     */
    fun query(latitude: Double, longitude: Double): CoastlineDistanceResult {
        val ref = nearestRef(latitude, longitude) ?: return noResult(latitude, longitude)
        val point = LatLng(latitude, longitude)
        val polyline = segmentsById[ref.polylineIdx]
        return CoastlineDistanceResult(
            distanceMeters = SpatialOperations.pointToSegmentDistance(point, ref.a, ref.b),
            closestPoint = SpatialOperations.projectPointOntoSegment(point, ref.a, ref.b),
            segmentId = polyline.id,
            isMainland = ref.polylineIdx == 0,
            polylineIdx = ref.polylineIdx,
            vertexIndex = ref.vertexIdx
        )
    }

    /**
     * Water/land by **closed-polygon containment** — winding-independent and corner-safe.
     *
     * The mainland arrives as an OPEN polyline (clipped at the region's east/west
     * longitudes). We model the **land** as the polygon bounded by that coastline plus a
     * virtual **cap** edge along the inland (north) side of the data — an edge that lies
     * entirely on land for this region, so the closure is geometrically valid. A point is
     * on **land** iff it lies inside that polygon, or inside any island ring.
     *
     * Containment is decided by an even-odd ray cast going **north** (toward the cap):
     *  1. count crossings of every **open mainland-coast** polyline (`!isRingPoly`) in the
     *     point's longitude column, north of the point ([SpatialOperations.rayCrossesSegmentNorth]);
     *  2. add **+1** for the cap edge — a northward ray always crosses it once when the
     *     point's longitude lies within the mainland span ([mainlandLonMin]..[mainlandLonMax]);
     *  3. **odd** total ⇒ inside the land polygon ⇒ **land**.
     * Each island (closed ring) is then tested separately: an odd crossing count ⇒ the
     * point is inside that island ⇒ **land**.
     *
     * Counting **all** open polylines (not just `polylineIdx == 0`) means a mainland that
     * assembled into several pieces is still closed into one land polygon by the shared
     * cap — so a fragment running ~north–south can no longer leave a land-side band.
     *
     * Unlike the previous open-polyline **south**-ray (which it replaces), this cannot
     * mislabel land beside ~north–south coast stretches or harbours: the cap closes the
     * polygon, so even-odd parity is a true topological invariant (Jordan curve theorem),
     * independent of coastline winding/orientation. `true` = water (default: no data).
     */
    fun isWater(latitude: Double, longitude: Double): Boolean {
        if (!hasData) return true

        val rayLatEnd = maxLat                       // north of all coastline (padded bbox top)
        val col = ((longitude - minLon) / cellSizeLon).toInt().coerceIn(0, colCount - 1)
        val startRow = ((latitude - minLat) / cellSizeLat).toInt().coerceIn(0, rowCount - 1)

        var mainlandCrossings = 0
        val islandCrossings = HashMap<Int, Int>()    // ring polylineIdx → crossing count
        val seen = HashSet<Int>()
        for (r in startRow until rowCount) {          // scan the column northward
            val cell = grid[GridCell(r, col)] ?: continue
            for (segIdx in cell) {
                if (!seen.add(segIdx)) continue
                val ref = segmentRefs[segIdx]
                if (!SpatialOperations.rayCrossesSegmentNorth(longitude, latitude, rayLatEnd, ref.a, ref.b)) continue
                if (isRingPoly[ref.polylineIdx]) {
                    islandCrossings[ref.polylineIdx] = (islandCrossings[ref.polylineIdx] ?: 0) + 1
                } else {
                    mainlandCrossings++              // any open coast piece (incl. fragments)
                }
            }
        }

        // Mainland land = coastline crossings + the inland cap edge (+1 within the span).
        val capCrossing = if (longitude in mainlandLonMin..mainlandLonMax) 1 else 0
        if ((mainlandCrossings + capCrossing) % 2 == 1) return false   // inside the mainland

        // Inside any island ring ⇒ land.
        for (c in islandCrossings.values) if (c % 2 == 1) return false
        return true
    }

    /** Ring-expanding nearest-segment search used by [query]. */
    private fun nearestRef(latitude: Double, longitude: Double): SegmentRef? {
        if (!hasData) return null

        val point = LatLng(latitude, longitude)
        val row = ((latitude  - minLat) / cellSizeLat).toInt()
        val col = ((longitude - minLon) / cellSizeLon).toInt()
        val maxRing = max(rowCount, colCount)

        val processed = HashSet<Int>()
        var bestDist = Double.MAX_VALUE
        var bestSegIdx = -1

        for (ring in 0..maxRing) {
            for (segIdx in collectRing(row, col, ring)) {
                if (!processed.add(segIdx)) continue   // already evaluated in an inner box
                val ref = segmentRefs[segIdx]
                val d = SpatialOperations.pointToSegmentDistance(point, ref.a, ref.b)
                if (d < bestDist) {
                    bestDist = d
                    bestSegIdx = segIdx
                }
            }

            // Provably safe to stop: nothing unexplored can be closer than bestDist.
            if (bestSegIdx >= 0 && bestDist <= ring * cellSizeM * SAFE_STOP_FACTOR) break

            // The box already spans the whole grid — nothing left to explore.
            if (row - ring <= 0 && row + ring >= rowCount - 1 &&
                col - ring <= 0 && col + ring >= colCount - 1
            ) break
        }

        return if (bestSegIdx >= 0) segmentRefs[bestSegIdx] else null
    }

    private fun noResult(latitude: Double, longitude: Double) = CoastlineDistanceResult(
        distanceMeters = Double.MAX_VALUE,
        closestPoint = LatLng(latitude, longitude),
        segmentId = "",
        isMainland = true
    )

    // ── Grid helpers ─────────────────────────────────────────────────────────

    /**
     * Collects all unique segment indices from the cells at the given
     * [ring] distance from `(centerRow, centerCol)`.
     *
     * Ring 0 = just the centre cell. Ring 1 = centre + 8 neighbours (9 cells).
     * Ring N = all cells within Manhattan-distance N of the centre.
     */
    private fun collectRing(centerRow: Int, centerCol: Int, ring: Int): Set<Int> {
        val result = mutableSetOf<Int>()
        val rMin = (centerRow - ring).coerceAtLeast(0)
        val rMax = (centerRow + ring).coerceAtMost(rowCount - 1)
        val cMin = (centerCol - ring).coerceAtLeast(0)
        val cMax = (centerCol + ring).coerceAtMost(colCount - 1)

        for (r in rMin..rMax) {
            for (c in cMin..cMax) {
                grid[GridCell(r, c)]?.let { result.addAll(it) }
            }
        }
        return result
    }

    private companion object {
        /**
         * Safety margin on the ring-distance stop bound. The grid's longitude cell
         * width in metres varies slightly with latitude (projected at mid-latitude),
         * so we require `bestDist ≤ ring·cellSize·0.95` before stopping to be sure no
         * closer segment hides just outside the searched box.
         */
        const val SAFE_STOP_FACTOR = 0.95

        /**
         * A polyline is treated as a closed ring (island) when its first and last vertex
         * coincide within this tolerance (degrees, ≈ a few metres). Used by [isWater] to
         * tell genuine island rings from open mainland coast — see [isRingPoly].
         */
        const val RING_CLOSE_EPS_DEG = 1e-5
    }
}
