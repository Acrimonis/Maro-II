package ykws.android.maro.spatial

import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.regulation.SpeedZone
import ykws.android.maro.data.regulation.SpeedZoneQuery
import kotlin.math.*

/**
 * Grid spatial index for speed zone polygon edges.
 *
 * Reuses the same pattern as [CoastlineSpatialIndex]: collect all polygon
 * segments (outer rings + holes) from speed zones, bin them into a sparse grid
 * (cell size ~100 m), then query by hashing the query point's grid cell.
 *
 * **Why not prebaked?** Runtime build is ~1-2 ms for ~20-30 speed zones
 * (~800-1200 edges) — imperceptible and avoids bake pipeline changes.
 *
 * **Memory:** ~20-40 KB for the target region.
 *
 * @param zones     Speed zones to index (speed-related only, filtered by [SpeedZoneBuilder]).
 * @param cellSizeM Grid cell size in meters (default 100 m). Smaller cells = faster queries,
 *                  more memory. Tune for your region's zone density.
 */
class SpeedZoneIndex(
    private val zones: List<SpeedZone>,
    private val cellSizeM: Double = 100.0
) {

    // ── Edge descriptor ──────────────────────────────────────────────────────
    private data class EdgeRef(
        val zoneIdx: Int,
        val a: LatLng,
        val b: LatLng,
        val minLat: Double, val maxLat: Double,
        val minLon: Double, val maxLon: Double
    )

    private data class GridCell(val row: Int, val col: Int)

    // ── State ────────────────────────────────────────────────────────────────

    /** All polygon edges flattened from all speed zones (outer rings + holes). */
    private val edges: List<EdgeRef>

    /** Sparse grid: cell → list of edge indices into [edges]. */
    private val grid: Map<GridCell, List<Int>>

    /** Bounding box of all edges. */
    private val minLat: Double
    private val maxLat: Double
    private val minLon: Double
    private val maxLon: Double

    /** Grid dimensions in degrees. */
    private val cellSizeLat: Double
    private val cellSizeLon: Double
    private val rowCount: Int
    private val colCount: Int

    /** True when the index has data to query. */
    val hasData: Boolean get() = zones.isNotEmpty() && edges.isNotEmpty()

    init {
        if (zones.isEmpty()) {
            edges = emptyList()
            grid = emptyMap()
            minLat = 0.0; maxLat = 0.0; minLon = 0.0; maxLon = 0.0
            cellSizeLat = 1.0; cellSizeLon = 1.0
            rowCount = 0; colCount = 0
        } else {
            // — 1. Flatten all polygon edges (outer rings + holes) —
        val edgeList = mutableListOf<EdgeRef>()
        for ((zoneIdx, zone) in zones.withIndex()) {
            // Edges from the outer ring
            for (i in 0 until zone.outerRing.size - 1) {
                val a = zone.outerRing[i]
                val b = zone.outerRing[i + 1]
                edgeList.add(
                    EdgeRef(
                        zoneIdx = zoneIdx,
                        a = a, b = b,
                        minLat = min(a.latitude, b.latitude),
                        maxLat = max(a.latitude, b.latitude),
                        minLon = min(a.longitude, b.longitude),
                        maxLon = max(a.longitude, b.longitude)
                    )
                )
            }
            // Edges from holes
            for (hole in zone.holes) {
                for (i in 0 until hole.size - 1) {
                    val a = hole[i]
                    val b = hole[i + 1]
                    edgeList.add(
                        EdgeRef(
                            zoneIdx = zoneIdx,
                            a = a, b = b,
                            minLat = min(a.latitude, b.latitude),
                            maxLat = max(a.latitude, b.latitude),
                            minLon = min(a.longitude, b.longitude),
                            maxLon = max(a.longitude, b.longitude)
                        )
                    )
                }
            }
        }
        edges = edgeList

        // — 2. Compute bounding box with 0.5 % padding —
        var bMinLat = Double.MAX_VALUE; var bMaxLat = -Double.MAX_VALUE
        var bMinLon = Double.MAX_VALUE; var bMaxLon = -Double.MAX_VALUE
        for (zone in zones) {
            for (pt in zone.outerRing) {
                if (pt.latitude < bMinLat) bMinLat = pt.latitude
                if (pt.latitude > bMaxLat) bMaxLat = pt.latitude
                if (pt.longitude < bMinLon) bMinLon = pt.longitude
                if (pt.longitude > bMaxLon) bMaxLon = pt.longitude
            }
        }
        val padLat = (bMaxLat - bMinLat).coerceAtLeast(0.001) * 0.005
        val padLon = (bMaxLon - bMinLon).coerceAtLeast(0.001) * 0.005
        minLat = bMinLat - padLat
        maxLat = bMaxLat + padLat
        minLon = bMinLon - padLon
        maxLon = bMaxLon + padLon

        // — 3. Cell size in degrees —
        val midLat = (minLat + maxLat) / 2.0
        val mPerDegLat = SpatialOperations.EARTH_RADIUS_M * PI / 180.0
        val mPerDegLon = mPerDegLat * cos(Math.toRadians(midLat))
        cellSizeLat = cellSizeM / mPerDegLat
        cellSizeLon = cellSizeM / mPerDegLon

        rowCount = max(1, ceil((maxLat - minLat) / cellSizeLat).toInt())
        colCount = max(1, ceil((maxLon - minLon) / cellSizeLon).toInt())

        // — 4. Build sparse grid —
        val gridBuilder = HashMap<GridCell, MutableList<Int>>()
        for ((edgeIdx, ref) in edges.withIndex()) {
            val minRow = ((ref.minLat - minLat) / cellSizeLat).toInt().coerceIn(0, rowCount - 1)
            val maxRow = ((ref.maxLat - minLat) / cellSizeLat).toInt().coerceIn(0, rowCount - 1)
            val minCol = ((ref.minLon - minLon) / cellSizeLon).toInt().coerceIn(0, colCount - 1)
            val maxCol = ((ref.maxLon - minLon) / cellSizeLon).toInt().coerceIn(0, colCount - 1)

            for (r in minRow..maxRow) {
                for (c in minCol..maxCol) {
                    gridBuilder.getOrPut(GridCell(r, c)) { mutableListOf() }.add(edgeIdx)
                }
            }
        }
        grid = gridBuilder
        } // end else (non-empty zones)
    }

    // ── Public query API ─────────────────────────────────────────────────────

    /**
     * Find the first speed zone boundary crossed when traveling along [headingDeg]
     * from (lat, lon). Uses ray-polygon intersection: represents the search ray as a
     * long segment from the origin to [pointAlongBearing], then tests segment-segment
     * intersection against all grid-indexed polygon edges.
     *
     * Returns the zone and distance (meters from origin to the intersection point),
     * or null if no zone edge is hit within [maxSearch] meters.
     *
     * @param lat         Origin latitude (WGS84).
     * @param lon         Origin longitude (WGS84).
     * @param headingDeg  Forward bearing (degrees, clockwise from true north).
     * @param maxSearch   Maximum search distance (meters).
     * @return The first [SpeedZone] hit and the distance to the intersection, or null.
     */
    fun firstSpeedZoneAhead(
        lat: Double, lon: Double,
        headingDeg: Double,
        maxSearch: Double = 2000.0
    ): Pair<SpeedZone, Double>? {
        if (!hasData) return null

        // 1. Build the search ray as a segment from origin to far end
        val rayEnd = SpatialOperations.pointAlongBearing(lat, lon, headingDeg, maxSearch)
        val rayOrigin = LatLng(lat, lon)

        // 2. Collect candidate edges by expanding ring-by-ring from origin.
        //    The origin cell + 8 Moore neighbours (~300m) might not cover zones
        //    further ahead — expand outward until we find an intersection or
        //    exhaust the practical search radius.
        val originCell = gridCell(lat, lon)
        // Estimate how many rings to cover maxSearch (~cellSizeM per ring)
        val maxRings = (maxSearch / cellSizeM).toInt().coerceIn(1, 20) + 1
        val candidateEdgeIndices = mutableSetOf<Int>()
        for (ring in 0..maxRings) {
            for (dr in -ring..ring) {
                for (dc in -ring..ring) {
                    if (abs(dr) < ring && abs(dc) < ring) continue // only ring boundary
                    val r = originCell.row + dr
                    val c = originCell.col + dc
                    if (r in 0 until rowCount && c in 0 until colCount) {
                        grid[GridCell(r, c)]?.let { candidateEdgeIndices.addAll(it) }
                    }
                }
            }
            // Early exit: if we've collected enough and ring extends past the max search
            if (ring > 0 && ring * cellSizeM > maxSearch * 1.5) break
        }

        // 3. Test each candidate edge for intersection with the ray
        var bestDistance = Double.MAX_VALUE
        var bestZoneIdx = -1

        for (edgeIdx in candidateEdgeIndices) {
            val ref = edges[edgeIdx]
            val intersectDist = segmentIntersectionDistance(
                rayOrigin, rayEnd, ref.a, ref.b
            )
            if (intersectDist != null && intersectDist < bestDistance) {
                // Only accept intersections in the forward direction (t > 0)
                if (intersectDist in 0.0..maxSearch) {
                    bestDistance = intersectDist
                    bestZoneIdx = ref.zoneIdx
                }
            }
        }

        android.util.Log.d("SpeedZoneIndex",
            "firstSpeedZoneAhead lat=$lat lon=$lon heading=$headingDeg " +
            "rings=$maxRings candidates=${candidateEdgeIndices.size} " +
            "result=${if (bestZoneIdx >= 0) "${zones[bestZoneIdx].name}@${"%.0f".format(bestDistance)}m" else "null"}")
        return if (bestZoneIdx >= 0) zones[bestZoneIdx] to bestDistance else null
    }

    /**
     * Query the speed zone index at a geographic point.
     *
     * @param lat Query latitude (WGS84).
     * @param lon Query longitude (WGS84).
     * @return [SpeedZoneQuery] describing all speed zones containing the point,
     *         the nearest zone boundary, and the most restrictive speed limit.
     */
    fun query(lat: Double, lon: Double): SpeedZoneQuery {
        if (!hasData) return SpeedZoneQuery()

        // 1. Hash grid cell
        val cell = gridCell(lat, lon)

        // 2. Collect candidate edge indices from this cell + 8 Moore neighbours
        val candidateEdgeIndices = collectCandidateEdges(cell.row, cell.col)

        // 3. Find nearest edge → closest zone + signed distance (grid-based, approximate)
        var nearestDistance = Double.MAX_VALUE
        var nearestZoneIdx = -1

        for (edgeIdx in candidateEdgeIndices) {
            val ref = edges[edgeIdx]
            val dist = SpatialOperations.pointToSegmentDistance(
                LatLng(lat, lon), ref.a, ref.b
            )
            if (dist < nearestDistance) {
                nearestDistance = dist
                nearestZoneIdx = ref.zoneIdx
            }
        }

        // 4. Check containment EXHAUSTIVELY against ALL zones (not just grid-proximate ones).
        //    The grid-only approach fails for large zones (>~300m) where no edge falls within
        //    the 3×3 cell neighbourhood — the boat could be deep inside a kilometre-wide zone
        //    and query() would report insideAny=false.
        //    Point-in-polygon for ~20-30 zones (~800-1200 edges) is <1ms — negligible.
        val insideZones = zones.filter { pointInsidePolygon(lat, lon, it) }
            .sortedBy { it.speedLimitKn }

        // 5. Build result
        val insideAny = insideZones.isNotEmpty()
        val nearestZone = if (nearestZoneIdx >= 0) zones[nearestZoneIdx] else null
        // If inside but no edge found in neighbour grid, try brute-force nearest edge
        val resolvedDistance = if (nearestDistance < Double.MAX_VALUE) {
            nearestDistance
        } else if (insideAny) {
            // Fallback: brute-force nearest edge for the first inside zone
            bruteForceNearestEdge(lat, lon, insideZones.first())
        } else {
            nearestDistance // stays MAX_VALUE → null
        }
        val signedDistance = if (insideAny && resolvedDistance < Double.MAX_VALUE) {
            -resolvedDistance.coerceAtMost(resolvedDistance)
        } else {
            resolvedDistance.takeIf { it < Double.MAX_VALUE }
        }

        return SpeedZoneQuery(
            allInsideZones = insideZones,
            nearestZone = nearestZone,
            distanceToBoundaryM = signedDistance,
            insideAnyZone = insideAny,
            mostRestrictiveSpeedKn = insideZones.minOfOrNull { it.speedLimitKn },
            approaching = false // caller tracks temporal direction
        )
    }

    /** Brute-force nearest-edge distance for a single zone (fallback when grid misses all edges). */
    private fun bruteForceNearestEdge(lat: Double, lon: Double, zone: SpeedZone): Double {
        val p = LatLng(lat, lon)
        var best = Double.MAX_VALUE
        val ring = zone.outerRing
        for (i in 0 until ring.size - 1) {
            val d = SpatialOperations.pointToSegmentDistance(p, ring[i], ring[i + 1])
            if (d < best) best = d
        }
        for (hole in zone.holes) {
            for (i in 0 until hole.size - 1) {
                val d = SpatialOperations.pointToSegmentDistance(p, hole[i], hole[i + 1])
                if (d < best) best = d
            }
        }
        return best
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    /** Hash a geographic point to its grid cell. */
    private fun gridCell(lat: Double, lon: Double): GridCell {
        val row = ((lat - minLat) / cellSizeLat).toInt().coerceIn(0, rowCount - 1)
        val col = ((lon - minLon) / cellSizeLon).toInt().coerceIn(0, colCount - 1)
        return GridCell(row, col)
    }

    /** Collect candidate edge indices from the given cell and its 8 Moore neighbours. */
    private fun collectCandidateEdges(row: Int, col: Int): Set<Int> {
        val candidates = mutableSetOf<Int>()
        for (dr in -1..1) {
            for (dc in -1..1) {
                val r = row + dr
                val c = col + dc
                if (r in 0 until rowCount && c in 0 until colCount) {
                    grid[GridCell(r, c)]?.let { candidates.addAll(it) }
                }
            }
        }
        return candidates
    }

    /**
     * Even-odd ray casting point-in-polygon test for a [SpeedZone].
     *
     * Returns true if (lat, lon) is inside the zone's outer ring and not
     * inside any hole. Reuses the same algorithm as [RegulatedZone.contains]
     * but operates on the runtime [SpeedZone] model.
     */
    private fun pointInsidePolygon(lat: Double, lon: Double, zone: SpeedZone): Boolean {
        val ring = zone.outerRing
        if (ring.size < 3) return false

        // Even-odd ray casting (PNPOLY)
        var inside = false
        var j = ring.size - 1
        for (i in ring.indices) {
            val yi = ring[i].latitude; val xi = ring[i].longitude
            val yj = ring[j].latitude; val xj = ring[j].longitude
            if (((yi > lat) != (yj > lat)) &&
                (lon < (xj - xi) * (lat - yi) / (yj - yi) + xi)
            ) inside = !inside
            j = i
        }
        if (!inside) return false

        // Check holes
        for (hole in zone.holes) {
            if (hole.size < 3) continue
            var inHole = false
            var k = hole.size - 1
            for (i in hole.indices) {
                val yi = hole[i].latitude; val xi = hole[i].longitude
                val yj = hole[k].latitude; val xj = hole[k].longitude
                if (((yi > lat) != (yj > lat)) &&
                    (lon < (xj - xi) * (lat - yi) / (yj - yi) + xi)
                ) inHole = !inHole
                k = i
            }
            if (inHole) return false
        }

        return true
    }

    /**
     * Compute the distance from segment origin [p1] to the intersection point
     * of segment [p1]→[p2] and segment [q1]→[q2], using a local planar projection.
     *
     * Returns null if the segments do not intersect (including collinear overlap).
     *
     * The distance is measured in meters along the great-circle path from [p1]
     * to the intersection point, using [SpatialOperations.haversine].
     */
    private fun segmentIntersectionDistance(
        p1: LatLng, p2: LatLng,
        q1: LatLng, q2: LatLng
    ): Double? {
        // Local planar projection centred at the midpoint of all four points
        val midLat = (p1.latitude + p2.latitude + q1.latitude + q2.latitude) / 4.0
        val mPerDegLat = SpatialOperations.EARTH_RADIUS_M * PI / 180.0
        val mPerDegLon = mPerDegLat * cos(Math.toRadians(midLat))

        fun toProj(p: LatLng) = Pair(p.longitude * mPerDegLon, p.latitude * mPerDegLat)

        val (p1x, p1y) = toProj(p1)
        val (p2x, p2y) = toProj(p2)
        val (q1x, q1y) = toProj(q1)
        val (q2x, q2y) = toProj(q2)

        val rx = p2x - p1x
        val ry = p2y - p1y
        val sx = q2x - q1x
        val sy = q2y - q1y

        val crossRS = rx * sy - ry * sx

        // Parallel or collinear — treat as no intersection (avoids division by zero)
        if (abs(crossRS) < 1e-12) return null

        val qpx = q1x - p1x
        val qpy = q1y - p1y

        val t = (qpx * sy - qpy * sx) / crossRS
        val u = (qpx * ry - qpy * rx) / crossRS

        // Intersection within both segments (0 ≤ t ≤ 1, 0 ≤ u ≤ 1)
        if (t < 0.0 || t > 1.0 || u < 0.0 || u > 1.0) return null

        // Intersection point in projected coordinates
        val ix = p1x + t * rx
        val iy = p1y + t * ry

        // Convert back to LatLng
        val iLat = iy / mPerDegLat
        val iLon = ix / mPerDegLon

        // Compute haversine distance from origin to intersection
        return SpatialOperations.haversine(p1, LatLng(iLat, iLon))
    }
}
