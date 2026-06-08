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

    /** A real island ring (interior = land) with its bbox, for point-in-polygon containment. */
    private class IslandRing(
        val pts: List<LatLng>,
        val minLat: Double, val maxLat: Double,
        val minLon: Double, val maxLon: Double
    )

    // ── State ────────────────────────────────────────────────────────────────

    /** All segments flattened from all polylines, in order. */
    private val segmentRefs: List<SegmentRef>

    /** Sparse map: grid cell → indices into [segmentRefs]. */
    private val grid: Map<GridCell, List<Int>>

    /** The original coastline segments (needed for segment IDs). */
    private val segmentsById: List<CoastlineSegment>

    /**
     * The segments the index actually uses — degenerate rings and tiny open fragments removed.
     * Exposed so the 300 m band builder rasterises the **same cleaned coastline**; otherwise a
     * dropped scrap (e.g. the 13 m seg4323 sliver) still corrupts the band's flood barrier / ribbon.
     */
    val usableSegments: List<CoastlineSegment>

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
     * Precomputed **real island** rings (closed, non-degenerate, CCW = interior land), each with its
     * point list and bounding box, for fast point-in-island containment ([insideRealIsland]). Marina
     * basins (CW) and degenerate slivers are excluded — they must not turn open water into land.
     */
    private val islandRings: List<IslandRing>

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
            islandRings = emptyList()
            usableSegments = emptyList()
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

            // — 3. Per-polyline ring detection (island = geometrically closed), then DROP
            //   degenerate rings: ≤3 points or ~zero area are noise slivers mis-closed into
            //   "rings" by the first/last≈ heuristic. They are not real polygons and corrupt the
            //   nearest-feature water/land test — a zero-area sliver has a meaningless "side", so
            //   points on either side of it flip arbitrarily (the residual marina bands). —
            isRingPoly = BooleanArray(segments.size) { polyIdx ->
                val pts = segments[polyIdx].points
                pts.size >= 3 &&
                    abs(pts.first().lat - pts.last().lat) < RING_CLOSE_EPS_DEG &&
                    abs(pts.first().lon - pts.last().lon) < RING_CLOSE_EPS_DEG
            }
            val polyUsable = BooleanArray(segments.size) { polyIdx ->
                val pts = segments[polyIdx].points
                if (isRingPoly[polyIdx]) {
                    // Ring: keep only non-degenerate polygons (≥3 distinct vertices + real area).
                    if (pts.size < 4) return@BooleanArray false
                    var area2 = 0.0
                    for (i in 0 until pts.size - 1) {
                        area2 += pts[i].lon.toDouble() * pts[i + 1].lat.toDouble() -
                            pts[i + 1].lon.toDouble() * pts[i].lat.toDouble()
                    }
                    abs(area2) >= RING_MIN_AREA_DEG2
                } else {
                    // Open coast: drop tiny detached fragments (e.g. the 13 m seg4323 sliver). A
                    // sub-threshold scrap carries no real sea/land boundary and only hijacks the
                    // nearest-feature side test for the surrounding open water.
                    val mPerDegLat = SpatialOperations.EARTH_RADIUS_M * PI / 180.0
                    var lenM = 0.0
                    for (i in 0 until pts.size - 1) {
                        val midLat = (pts[i].lat + pts[i + 1].lat) / 2.0
                        val dy = (pts[i + 1].lat - pts[i].lat).toDouble() * mPerDegLat
                        val dx = (pts[i + 1].lon - pts[i].lon).toDouble() *
                            mPerDegLat * cos(Math.toRadians(midLat.toDouble()))
                        lenM += sqrt(dx * dx + dy * dy)
                    }
                    lenM >= MIN_OPEN_POLYLINE_M
                }
            }
            usableSegments = segments.filterIndexed { i, _ -> polyUsable[i] }

            // — 4. Flatten segments (skipping degenerate rings) —
            val refs = mutableListOf<SegmentRef>()
            for ((polyIdx, seg) in segments.withIndex()) {
                if (!polyUsable[polyIdx]) continue
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

            // Cap longitude extent (combined span of all OPEN mainland-coast polylines).
            var mlMin = Double.MAX_VALUE; var mlMax = -Double.MAX_VALUE
            for (r in refs) if (!isRingPoly[r.polylineIdx]) {
                if (r.minLon < mlMin) mlMin = r.minLon
                if (r.maxLon > mlMax) mlMax = r.maxLon
            }
            mainlandLonMin = mlMin; mainlandLonMax = mlMax

            // — Real island rings (usable + CCW = interior land) for point-in-island containment.
            //   CW basins and degenerate slivers are excluded so they never turn open water to land. —
            val islands = mutableListOf<IslandRing>()
            for (polyIdx in segments.indices) {
                if (!isRingPoly[polyIdx]) continue
                val pts0 = segments[polyIdx].points
                if (pts0.size < 4) continue
                var area2 = 0.0
                for (i in 0 until pts0.size - 1) {
                    area2 += pts0[i].lon.toDouble() * pts0[i + 1].lat.toDouble() -
                        pts0[i + 1].lon.toDouble() * pts0[i].lat.toDouble()
                }
                if (area2 < RING_MIN_AREA_DEG2) continue   // CW basin / degenerate ⇒ not a land island
                val ll = pts0.map { LatLng(it.lat.toDouble(), it.lon.toDouble()) }
                var nLat = Double.MAX_VALUE; var xLat = -Double.MAX_VALUE
                var nLon = Double.MAX_VALUE; var xLon = -Double.MAX_VALUE
                for (p in ll) {
                    if (p.latitude < nLat) nLat = p.latitude
                    if (p.latitude > xLat) xLat = p.latitude
                    if (p.longitude < nLon) nLon = p.longitude
                    if (p.longitude > xLon) xLon = p.longitude
                }
                islands.add(IslandRing(ll, nLat, xLat, nLon, xLon))
            }
            islandRings = islands

            // — 5. Build sparse grid —
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
        val closest = SpatialOperations.projectPointOntoSegment(point, ref.a, ref.b)
        return CoastlineDistanceResult(
            distanceMeters = SpatialOperations.pointToSegmentDistance(point, ref.a, ref.b),
            closestPoint = closest,
            segmentId = polyline.id,
            isMainland = ref.polylineIdx == 0,
            polylineIdx = ref.polylineIdx,
            vertexIndex = ref.vertexIdx
        )
    }

    /**
     * Collects every coastline segment in the query point's longitude **column**, from the
     * point down to [maxDistM] metres south — the candidate set for a southward ray-cast
     * water/land classification (used by the off-device Zone300 prebake, which mirrors the
     * runtime south-ray parity).
     *
     * Only cells in the query longitude's column are scanned, so this is far cheaper than a
     * full ring-expansion query: a vertical ray meets an (approximately) horizontal coastline
     * only 1–3 times.
     *
     * @param latitude   WGS84 latitude of the query point.
     * @param longitude  WGS84 longitude of the query point (the ray's column).
     * @param maxDistM   Maximum distance south to search (e.g. 6 NM = 11 112 m).
     * @return Candidates, each carrying the segment endpoints and polyline index
     *         (0 = mainland, >0 = island). Empty when no coastline is loaded.
     */
    fun queryColumn(
        latitude: Double,
        longitude: Double,
        maxDistM: Double
    ): List<ColumnCandidate> {
        if (!hasData) return emptyList()

        val startRow = ((latitude - minLat) / cellSizeLat).toInt().coerceIn(0, rowCount - 1)
        val endLat = latitude - (maxDistM / (SpatialOperations.EARTH_RADIUS_M * PI / 180.0))
        val endRow = ((endLat - minLat) / cellSizeLat).toInt().coerceIn(0, rowCount - 1)
        val col = ((longitude - minLon) / cellSizeLon).toInt().coerceIn(0, colCount - 1)

        val seen = mutableSetOf<Int>()
        val result = mutableListOf<ColumnCandidate>()

        val rowRange = if (startRow <= endRow) startRow..endRow else endRow..startRow
        for (r in rowRange) {
            grid[GridCell(r, col)]?.let { indices ->
                for (idx in indices) {
                    if (seen.add(idx)) {
                        val ref = segmentRefs[idx]
                        result.add(ColumnCandidate(ref.a, ref.b, ref.polylineIdx))
                    }
                }
            }
        }

        return result
    }

    /**
     * A coastline segment candidate for ray-cast intersection testing.
     *
     * @property a            Segment start point in WGS84.
     * @property b            Segment end point in WGS84.
     * @property polylineIdx  0 = mainland coastline, >0 = island.
     */
    data class ColumnCandidate(
        val a: LatLng,
        val b: LatLng,
        val polylineIdx: Int
    )

    /**
     * Water/land by a **mainland-primary** test:
     *  - **Base** = nearest-**MAINLAND**-segment **side test** ([classifyWater]) — the open coast is
     *    what actually separates sea from land; this is immune to the vertical-ray degeneracy and is
     *    not hijacked by tiny marina rings (breakwaters/pontoons) deciding the open sea.
     *  - **Override** = inside a **real island** ([insideRealIsland]) ⇒ land. Real = closed,
     *    non-degenerate, CCW (interior land); marina basins (CW) and zero-area slivers are excluded,
     *    so they can never flip open water to land.
     *
     * `land = mainland-side-says-land OR inside-a-real-island`. `true` = water (default: no data).
     * The legacy all-ray containment is kept as [isWaterByRayCast] for rollback.
     */
    fun isWater(latitude: Double, longitude: Double): Boolean {
        if (!hasData) return true
        val mainRef = nearestRef(latitude, longitude, mainlandOnly = true)
        val mainLand = if (mainRef != null) {
            val point = LatLng(latitude, longitude)
            val closest = SpatialOperations.projectPointOntoSegment(point, mainRef.a, mainRef.b)
            !classifyWater(point, mainRef, closest)
        } else false
        return !(mainLand || insideRealIsland(latitude, longitude))
    }

    /** True when (lat,lon) is inside any real island ring (bbox-filtered even-odd PNPOLY). */
    private fun insideRealIsland(latitude: Double, longitude: Double): Boolean {
        for (ring in islandRings) {
            if (latitude < ring.minLat || latitude > ring.maxLat ||
                longitude < ring.minLon || longitude > ring.maxLon
            ) continue
            if (pointInPolygon(latitude, longitude, ring.pts)) return true
        }
        return false
    }

    /** Even-odd point-in-polygon (horizontal-ray PNPOLY; the straddle guard handles axis-aligned
     *  edges and vertices and prevents divide-by-zero). */
    private fun pointInPolygon(lat: Double, lon: Double, poly: List<LatLng>): Boolean {
        var inside = false
        var j = poly.size - 1
        for (i in poly.indices) {
            val yi = poly[i].latitude; val xi = poly[i].longitude
            val yj = poly[j].latitude; val xj = poly[j].longitude
            if (((yi > lat) != (yj > lat)) &&
                (lon < (xj - xi) * (lat - yi) / (yj - yi) + xi)
            ) inside = !inside
            j = i
        }
        return inside
    }

    /**
     * Water/land for [point] from its nearest segment [ref] and the [closest] point on it. When
     * [closest] is interior to the segment a single side test suffices; when it coincides with an
     * endpoint the point is nearest a shared vertex and the corner rule ([cornerWater]) applies.
     */
    private fun classifyWater(point: LatLng, ref: SegmentRef, closest: LatLng): Boolean {
        val atA = abs(closest.latitude - ref.a.latitude) < VERTEX_EPS_DEG &&
            abs(closest.longitude - ref.a.longitude) < VERTEX_EPS_DEG
        val atB = abs(closest.latitude - ref.b.latitude) < VERTEX_EPS_DEG &&
            abs(closest.longitude - ref.b.longitude) < VERTEX_EPS_DEG
        return when {
            atA -> cornerWater(point, ref.polylineIdx, ref.vertexIdx)
            atB -> cornerWater(point, ref.polylineIdx, ref.vertexIdx + 1)
            else -> SpatialOperations.signedSide(point, ref.a, ref.b) < 0.0   // right of travel = water
        }
    }

    /**
     * Water/land when the nearest point is polyline vertex [v] (a corner shared by two edges).
     * Resolves the ambiguity from both incident edges and the turn direction, keeping the
     * water-on-the-right convention. Handles open-polyline endpoints (one edge) and ring wrap.
     */
    private fun cornerWater(point: LatLng, polyIdx: Int, v: Int): Boolean {
        val poly = segmentsById[polyIdx].points   // CoastlinePoint (Float lat/lon)
        val n = poly.size
        val isRing = isRingPoly[polyIdx]
        val prevIdx = if (v > 0) v - 1 else if (isRing) n - 2 else -1
        val nextIdx = if (v < n - 1) v + 1 else if (isRing) 1 else -1
        fun at(i: Int): LatLng? =
            if (i in 0 until n) LatLng(poly[i].lat.toDouble(), poly[i].lon.toDouble()) else null
        val vtx = at(v) ?: return true
        val prev = at(prevIdx)
        val next = at(nextIdx)
        if (prev == null && next == null) return true
        if (prev == null) return SpatialOperations.signedSide(point, vtx, next!!) < 0.0
        if (next == null) return SpatialOperations.signedSide(point, prev, vtx) < 0.0
        val waterIn = SpatialOperations.signedSide(point, prev, vtx) < 0.0
        val waterOut = SpatialOperations.signedSide(point, vtx, next) < 0.0
        // Turn at the vertex (sign only), land on the left (water-on-right ⇒ CCW interior):
        //  > 0 left turn = CONVEX land corner (small land wedge) ⇒ water if on the water side of
        //                  EITHER edge; <= 0 reflex ⇒ water only if on the water side of BOTH.
        val turn = (vtx.longitude - prev.longitude) * (next.latitude - vtx.latitude) -
            (vtx.latitude - prev.latitude) * (next.longitude - vtx.longitude)
        return if (turn > 0.0) (waterIn || waterOut) else (waterIn && waterOut)
    }

    /**
     * Legacy water/land by closed-polygon containment via a vertical NORTH ray (even-odd parity;
     * mainland closed by a virtual inland cap; islands tested as rings). Retained for A/B
     * comparison and rollback only — [isWater] now uses the nearest-segment side test, which is
     * not degenerate against near-vertical coast. `true` = water (default: no data).
     */
    @Suppress("unused")
    private fun isWaterByRayCast(latitude: Double, longitude: Double): Boolean {
        if (!hasData) return true

        val rayLatEnd = maxLat
        val col = ((longitude - minLon) / cellSizeLon).toInt().coerceIn(0, colCount - 1)
        val startRow = ((latitude - minLat) / cellSizeLat).toInt().coerceIn(0, rowCount - 1)

        var mainlandCrossings = 0
        val islandCrossings = HashMap<Int, Int>()
        val seen = HashSet<Int>()
        for (r in startRow until rowCount) {
            val cell = grid[GridCell(r, col)] ?: continue
            for (segIdx in cell) {
                if (!seen.add(segIdx)) continue
                val ref = segmentRefs[segIdx]
                if (!SpatialOperations.rayCrossesSegmentNorth(longitude, latitude, rayLatEnd, ref.a, ref.b)) continue
                if (isRingPoly[ref.polylineIdx]) {
                    islandCrossings[ref.polylineIdx] = (islandCrossings[ref.polylineIdx] ?: 0) + 1
                } else {
                    mainlandCrossings++
                }
            }
        }

        val capCrossing = if (longitude in mainlandLonMin..mainlandLonMax) 1 else 0
        if ((mainlandCrossings + capCrossing) % 2 == 1) return false
        for (c in islandCrossings.values) if (c % 2 == 1) return false
        return true
    }

    /** Ring-expanding nearest-segment search used by [query]. */
    private fun nearestRef(latitude: Double, longitude: Double, mainlandOnly: Boolean = false): SegmentRef? {
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
                if (mainlandOnly && isRingPoly[ref.polylineIdx]) continue
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
        /** Degrees within which a projected closest point is treated as coincident with a vertex (~1 cm). */
        const val VERTEX_EPS_DEG = 1e-7

        /** Min |signed area| (deg², shoelace ×2) for a ring to count as a real polygon (~a few m²);
         *  below this it is a degenerate sliver and is dropped from the index. */
        const val RING_MIN_AREA_DEG2 = 1e-9

        /** Min total length (m) for an OPEN coast polyline to be kept; shorter detached fragments are
         *  digitisation scraps that corrupt the nearest-feature water/land test, so they are dropped. */
        const val MIN_OPEN_POLYLINE_M = 30.0

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
