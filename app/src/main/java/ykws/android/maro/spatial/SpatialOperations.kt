package ykws.android.maro.spatial

import ykws.android.maro.data.model.LatLng
import kotlin.math.*

/**
 * Pure geometric algorithms for coastline processing.
 *
 * All functions are stateless and have no Android dependencies,
 * making them fully testable on JVM.
 */
object SpatialOperations {

    /**
     * Earth radius in meters (WGS84 mean radius).
     */
    const val EARTH_RADIUS_M = 6_371_000.0

    // ─────────────────────────────────────────────────────────────────────────
    // Distance helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Haversine distance between two geographic points in meters.
     * Accurate to ~0.5% for any distance on Earth.
     */
    fun haversine(p1: LatLng, p2: LatLng): Double {
        val dLat = Math.toRadians(p2.latitude - p1.latitude)
        val dLon = Math.toRadians(p2.longitude - p1.longitude)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(p1.latitude)) * cos(Math.toRadians(p2.latitude)) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(a))
    }

    /**
     * Minimum distance from point [p] to line segment [a]→[b], in meters.
     *
     * Uses a local planar projection (Lambert-like) centered at the midpoint
     * of the three points. Accurate to < 1% for distances < 50 km at these latitudes.
     */
    fun pointToSegmentDistance(p: LatLng, a: LatLng, b: LatLng): Double {
        val midLat = (p.latitude + a.latitude + b.latitude) / 3.0
        val mPerDegLat = EARTH_RADIUS_M * PI / 180.0
        val mPerDegLon = mPerDegLat * cos(Math.toRadians(midLat))

        val px = p.longitude * mPerDegLon
        val py = p.latitude * mPerDegLat
        val ax = a.longitude * mPerDegLon
        val ay = a.latitude * mPerDegLat
        val bx = b.longitude * mPerDegLon
        val by = b.latitude * mPerDegLat

        val abx = bx - ax
        val aby = by - ay
        val apx = px - ax
        val apy = py - ay
        val abLenSq = abx * abx + aby * aby

        if (abLenSq == 0.0) {
            // Degenerate segment (a == b)
            return sqrt((px - ax).pow(2) + (py - ay).pow(2))
        }

        val t = ((apx * abx + apy * aby) / abLenSq).coerceIn(0.0, 1.0)
        val cx = ax + t * abx
        val cy = ay + t * aby
        return sqrt((px - cx).pow(2) + (py - cy).pow(2))
    }

    /**
     * Projects point [p] onto line segment [a]→[b] and returns the closest
     * point *on the segment* in geographic coordinates.
     *
     * Uses the same local planar projection as [pointToSegmentDistance].
     * When a==b (degenerate segment), returns [a].
     *
     * This is the companion to [pointToSegmentDistance] — use this when
     * you need the *where* in addition to the *how far*.
     */
    fun projectPointOntoSegment(p: LatLng, a: LatLng, b: LatLng): LatLng {
        val midLat = (p.latitude + a.latitude + b.latitude) / 3.0
        val mPerDegLat = EARTH_RADIUS_M * PI / 180.0
        val mPerDegLon = mPerDegLat * cos(Math.toRadians(midLat))

        val px = p.longitude * mPerDegLon
        val py = p.latitude * mPerDegLat
        val ax = a.longitude * mPerDegLon
        val ay = a.latitude * mPerDegLat
        val bx = b.longitude * mPerDegLon
        val by = b.latitude * mPerDegLat

        val abx = bx - ax
        val aby = by - ay
        val abLenSq = abx * abx + aby * aby

        if (abLenSq == 0.0) return a

        val t = (((px - ax) * abx + (py - ay) * aby) / abLenSq).coerceIn(0.0, 1.0)
        val cx = ax + t * abx
        val cy = ay + t * aby

        return LatLng(
            latitude = cy / mPerDegLat,
            longitude = cx / mPerDegLon
        )
    }

    /**
     * Minimum distance between two polylines in meters.
     *
     * Uses a local planar projection centered on the bounding box of both
     * polylines. All points are projected to meters **once** upfront, then
     * distance checks are simple 2D Cartesian math — avoiding per-check
     * re-projection.
     *
     * Includes a bounding box pre-filter: if the bounding boxes of the two
     * polylines don't overlap within [maxDistance], returns [maxDistance]
     * immediately (no per-point checks needed).
     *
     * O(N × M) worst case, but bounding box pre-filter makes it O(1) for
     * distant polylines.
     *
     * @param poly1 First polyline (e.g. mainland).
     * @param poly2 Second polyline (e.g. island).
     * @param maxDistance If the result is known to exceed this value, it can
     *                    be returned early. Default: [Double.MAX_VALUE].
     */
    fun polylinesMinDistance(
        poly1: List<LatLng>,
        poly2: List<LatLng>,
        maxDistance: Double = Double.MAX_VALUE
    ): Double {
        if (poly1.isEmpty() || poly2.size < 2) return Double.MAX_VALUE

        // ── Bounding box pre-filter (Option B) ──────────────────────────
        // If the bounding boxes are farther apart than maxDistance, skip.
        val bbox1 = computeBoundingBox(poly1)
        val bbox2 = computeBoundingBox(poly2)

        val bboxLatDist = maxOf(0.0, bbox2.latSouth - bbox1.latNorth, bbox1.latSouth - bbox2.latNorth)
        val bboxLonDist = maxOf(0.0, bbox2.lonWest - bbox1.lonEast, bbox1.lonWest - bbox2.lonEast)

        if (bboxLatDist > 0.0 || bboxLonDist > 0.0) {
            // Bounding boxes don't overlap — approximate distance from gap
            val approxMeters = sqrt(
                (bboxLatDist * DEG_TO_M_LAT).pow(2) +
                (bboxLonDist * DEG_TO_M_LON_AT_MID_LAT(bbox1, bbox2)).pow(2)
            )
            if (approxMeters > maxDistance) return maxDistance
        }

        // ── Project all points to local Cartesian (Option A) ────────────
        // Use the midpoint of both bounding boxes as projection center
        val centerLat = (bbox1.centerLat + bbox2.centerLat) / 2.0
        val mPerDegLat = EARTH_RADIUS_M * PI / 180.0
        val mPerDegLon = mPerDegLat * cos(Math.toRadians(centerLat))

        // Project poly1 points
        val proj1 = poly1.map { p ->
            Pair(p.longitude * mPerDegLon, p.latitude * mPerDegLat)
        }

        // Project poly2 points
        val proj2 = poly2.map { p ->
            Pair(p.longitude * mPerDegLon, p.latitude * mPerDegLat)
        }

        // ── Brute-force 2D distance check ───────────────────────────────
        // (Still O(N×M) but no re-projection per check)
        var minDist = Double.MAX_VALUE
        for ((px, py) in proj1) {
            for (i in 0 until proj2.size - 1) {
                val (ax, ay) = proj2[i]
                val (bx, by) = proj2[i + 1]

                val abx = bx - ax
                val aby = by - ay
                val apx = px - ax
                val apy = py - ay
                val abLenSq = abx * abx + aby * aby

                val dist = if (abLenSq == 0.0) {
                    sqrt((px - ax).pow(2) + (py - ay).pow(2))
                } else {
                    val t = ((apx * abx + apy * aby) / abLenSq).coerceIn(0.0, 1.0)
                    val cx = ax + t * abx
                    val cy = ay + t * aby
                    sqrt((px - cx).pow(2) + (py - cy).pow(2))
                }

                if (dist < minDist) {
                    minDist = dist
                    if (minDist <= maxDistance) {
                        // Can't get shorter than maxDistance when we already
                        // know we need to be within it — but we can't skip
                        // entirely because we need the actual minimum.
                    }
                }
            }
        }

        return minDist
    }

    /**
     * Quick bounding box for a polyline. Returns (latSouth, latNorth, lonWest, lonEast).
     */
    private fun computeBoundingBox(points: List<LatLng>): BoundingBox {
        var latSouth = Double.MAX_VALUE
        var latNorth = -Double.MAX_VALUE
        var lonWest = Double.MAX_VALUE
        var lonEast = -Double.MAX_VALUE
        for (p in points) {
            if (p.latitude < latSouth) latSouth = p.latitude
            if (p.latitude > latNorth) latNorth = p.latitude
            if (p.longitude < lonWest) lonWest = p.longitude
            if (p.longitude > lonEast) lonEast = p.longitude
        }
        return BoundingBox(latSouth, latNorth, lonWest, lonEast)
    }

    private data class BoundingBox(
        val latSouth: Double,
        val latNorth: Double,
        val lonWest: Double,
        val lonEast: Double
    ) {
        val centerLat: Double get() = (latSouth + latNorth) / 2.0
        val centerLon: Double get() = (lonWest + lonEast) / 2.0
    }

    // Approximate meters per degree at these latitudes
    private val DEG_TO_M_LAT = EARTH_RADIUS_M * PI / 180.0

    private fun DEG_TO_M_LON_AT_MID_LAT(b1: BoundingBox, b2: BoundingBox): Double {
        val midLat = (b1.centerLat + b2.centerLat) / 2.0
        return DEG_TO_M_LAT * cos(Math.toRadians(midLat))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Ray casting (water / land classification)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Tests whether a vertical ray going SOUTH crosses the segment A→B.
     *
     * The ray starts at (rayLon, rayLatStart) and extends southward to
     * (rayLon, rayLatEnd). rayLatEnd must be < rayLatStart (south = smaller latitude).
     *
     * ## Usage
     *
     * Called by [CoastlineRepository.isOnWater] for every candidate segment
     * returned by the spatial index's column query. The crossing count (mod 2)
     * determines water (even) vs. land (odd).
     *
     * ## Vertex de-duplication
     *
     * A ray passing exactly through a vertex shared by two adjacent coastline
     * segments would be double-counted — once by each incident segment — unless
     * we assign each vertex to exactly one of its two segments. The standard
     * fix is **strict inequality on the upper longitude bound**:
     *
     * ```
     * (aLon <= rayLon && rayLon < bLon) || (bLon <= rayLon && rayLon < aLon)
     * ```
     *
     * A vertex at longitude L is counted by the segment where it is the
     * **lower-longitude** endpoint (aLon <= rayLon) and skipped by the
     * segment where it is the **higher-longitude** endpoint (rayLon < aLon).
     * This guarantees every shared vertex contributes exactly 1 crossing.
     *
     * @param rayLon       Longitude of the vertical ray (query point's longitude).
     * @param rayLatStart  Latitude the ray starts at (query point's latitude).
     * @param rayLatEnd    Latitude the ray ends at (typically 6 NM south).
     * @param a            Coastline segment start point.
     * @param b            Coastline segment end point.
     * @return true if the ray crosses this segment within the latitude band.
     */
    fun rayCrossesSegmentSouth(
        rayLon: Double,
        rayLatStart: Double,
        rayLatEnd: Double,
        a: LatLng,
        b: LatLng
    ): Boolean {
        val aLon = a.longitude
        val bLon = b.longitude
        val aLat = a.latitude
        val bLat = b.latitude

        // 1. Segment must span the ray's longitude.
        //    Strict '<' on the upper bound de-duplicates shared vertices.
        val crossesLon = (aLon <= rayLon && rayLon < bLon) ||
                         (bLon <= rayLon && rayLon < aLon)
        if (!crossesLon) return false

        // 2. Compute the latitude where the ray intersects the segment's line.
        val dLon = bLon - aLon
        val intersectLat: Double = if (dLon == 0.0) {
            // Vertical segment, collinear with the ray.
            // Count as crossed if the segment extends south of the ray start.
            minOf(aLat, bLat)
        } else {
            aLat + (rayLon - aLon) * (bLat - aLat) / dLon
        }

        // 3. Crossing counts only if the intersection is strictly south of the
        //    query point and at-or-south of the 6 NM limit.
        return intersectLat < rayLatStart && intersectLat >= rayLatEnd
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Douglas-Peucker simplification
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Ramer-Douglas-Peucker polyline simplification.
     *
     * Recursively removes points that are within [epsilonM] meters of the
     * simplified line. The result always includes the first and last point.
     */
    fun douglasPeucker(
        points: List<LatLng>,
        epsilonM: Double
    ): List<LatLng> {
        if (points.size <= 2) return points

        var maxDist = 0.0
        var maxIdx = 0
        for (i in 1 until points.size - 1) {
            val d = pointToSegmentDistance(points[i], points.first(), points.last())
            if (d > maxDist) {
                maxDist = d
                maxIdx = i
            }
        }

        return if (maxDist > epsilonM) {
            val left = douglasPeucker(points.subList(0, maxIdx + 1), epsilonM)
            val right = douglasPeucker(points.subList(maxIdx, points.size), epsilonM)
            left.dropLast(1) + right
        } else {
            listOf(points.first(), points.last())
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Polyline assembly (segment stitching)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Maximum distance (meters) for two segment endpoints to be considered
     * "matching" when stitching polylines together.
     */
    private const val MATCH_THRESHOLD_M = 25.0

    /**
     * Stitches raw OSM way segments into continuous polylines by matching
     * endpoints within [MATCH_THRESHOLD_M].
     *
     * @param segments List of raw coordinate arrays from OSM ways.
     * @return List of assembled, oriented polylines.
     */
    fun assemblePolylines(segments: List<List<LatLng>>): List<List<LatLng>> {
        if (segments.isEmpty()) return emptyList()

        val remaining = segments.toMutableList()
        val polylines = mutableListOf<List<LatLng>>()

        while (remaining.isNotEmpty()) {
            val chain = buildSinglePolyline(remaining)
            if (chain.isNotEmpty()) {
                polylines.add(chain)
            }
        }

        return polylines
    }

    /**
     * Builds one continuous polyline by greedily matching endpoints.
     * Removes consumed segments from [remaining].
     */
    private fun buildSinglePolyline(
        remaining: MutableList<List<LatLng>>
    ): List<LatLng> {
        if (remaining.isEmpty()) return emptyList()

        val chain = remaining.removeAt(0).toMutableList()
        var changed = true

        while (remaining.isNotEmpty() && changed) {
            changed = false
            val tail = chain.last()

            // Try to append: chain.tail → segment.head
            var bestIdx: Int? = null
            var minDist = MATCH_THRESHOLD_M
            for (i in remaining.indices) {
                val d = haversine(tail, remaining[i].first())
                if (d < minDist) {
                    minDist = d
                    bestIdx = i
                }
            }

            if (bestIdx != null) {
                chain.addAll(remaining.removeAt(bestIdx).drop(1))
                changed = true
                continue
            }

            // Try to prepend: segment.tail → chain.head
            val head = chain.first()
            bestIdx = null
            minDist = MATCH_THRESHOLD_M
            for (i in remaining.indices) {
                val d = haversine(remaining[i].last(), head)
                if (d < minDist) {
                    minDist = d
                    bestIdx = i
                }
            }

            if (bestIdx != null) {
                val seg = remaining.removeAt(bestIdx)
                chain.addAll(0, seg.dropLast(1))
                changed = true
            }
        }

        return chain
    }
}
