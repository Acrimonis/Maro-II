package ykws.android.maro.spatial

import ykws.android.maro.data.model.LatLng
import kotlin.math.*

/**
 * Reusable grid spatial index over [IndexedZone] polygons (speed AND non-speed).
 * Holds the shared geometry: edge flattening, sparse grid, forward ray-cast, and
 * point-in-polygon. Zone identity is tracked by index so wrappers can map results
 * back to their original zone objects without reconstruction.
 */
class PolygonIndexBase(
    private val zones: List<IndexedZone>,
    private val cellSizeM: Double = 100.0
) {

    private data class EdgeRef(
        val zoneIdx: Int,
        val a: LatLng,
        val b: LatLng,
        val minLat: Double, val maxLat: Double,
        val minLon: Double, val maxLon: Double
    )

    private data class GridCell(val row: Int, val col: Int)

    private val edges: List<EdgeRef>
    private val grid: Map<GridCell, List<Int>>
    private val minLat: Double
    private val maxLat: Double
    private val minLon: Double
    private val maxLon: Double
    private val cellSizeLat: Double
    private val cellSizeLon: Double
    private val rowCount: Int
    private val colCount: Int

    val hasData: Boolean get() = zones.isNotEmpty() && edges.isNotEmpty()

    /** Result of a point query: inside zone indices + nearest boundary. */
    data class PolygonStatus(
        val insideZoneIdxs: List<Int>,
        val nearestZoneIdx: Int,
        val nearestBoundaryM: Double?
    )

    init {
        if (zones.isEmpty()) {
            edges = emptyList()
            grid = emptyMap()
            minLat = 0.0; maxLat = 0.0; minLon = 0.0; maxLon = 0.0
            cellSizeLat = 1.0; cellSizeLon = 1.0
            rowCount = 0; colCount = 0
        } else {
            val edgeList = mutableListOf<EdgeRef>()
            for ((zoneIdx, zone) in zones.withIndex()) {
                for (i in 0 until zone.outerRing.size - 1) {
                    val a = zone.outerRing[i]
                    val b = zone.outerRing[i + 1]
                    edgeList.add(
                        EdgeRef(zoneIdx, a, b,
                            min(a.latitude, b.latitude), max(a.latitude, b.latitude),
                            min(a.longitude, b.longitude), max(a.longitude, b.longitude))
                    )
                }
                for (hole in zone.holes) {
                    for (i in 0 until hole.size - 1) {
                        val a = hole[i]
                        val b = hole[i + 1]
                        edgeList.add(
                            EdgeRef(zoneIdx, a, b,
                                min(a.latitude, b.latitude), max(a.latitude, b.latitude),
                                min(a.longitude, b.longitude), max(a.longitude, b.longitude))
                        )
                    }
                }
            }
            edges = edgeList

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

            val midLat = (minLat + maxLat) / 2.0
            val mPerDegLat = SpatialOperations.EARTH_RADIUS_M * PI / 180.0
            val mPerDegLon = mPerDegLat * cos(Math.toRadians(midLat))
            cellSizeLat = cellSizeM / mPerDegLat
            cellSizeLon = cellSizeM / mPerDegLon

            rowCount = max(1, ceil((maxLat - minLat) / cellSizeLat).toInt())
            colCount = max(1, ceil((maxLon - minLon) / cellSizeLon).toInt())

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
        }
    }

    fun zoneAt(idx: Int): IndexedZone = zones[idx]

    /** Forward ray-cast along heading; returns (zoneIdx, along-heading distance) or null. */
    fun firstAhead(lat: Double, lon: Double, headingDeg: Double, maxSearch: Double = 2000.0): Pair<Int, Double>? {
        if (!hasData) return null
        val rayEnd = SpatialOperations.pointAlongBearing(lat, lon, headingDeg, maxSearch)
        val rayOrigin = LatLng(lat, lon)

        val originCell = gridCell(lat, lon)
        val maxRings = (maxSearch / cellSizeM).toInt().coerceIn(1, 20) + 1
        val candidateEdgeIndices = mutableSetOf<Int>()
        for (ring in 0..maxRings) {
            for (dr in -ring..ring) {
                for (dc in -ring..ring) {
                    if (abs(dr) < ring && abs(dc) < ring) continue
                    val r = originCell.row + dr
                    val c = originCell.col + dc
                    if (r in 0 until rowCount && c in 0 until colCount) {
                        grid[GridCell(r, c)]?.let { candidateEdgeIndices.addAll(it) }
                    }
                }
            }
            if (ring > 0 && ring * cellSizeM > maxSearch * 1.5) break
        }

        var bestDistance = Double.MAX_VALUE
        var bestZoneIdx = -1
        for (edgeIdx in candidateEdgeIndices) {
            val ref = edges[edgeIdx]
            val d = segmentIntersectionDistance(rayOrigin, rayEnd, ref.a, ref.b)
            if (d != null && d in 0.0..maxSearch && d < bestDistance) {
                bestDistance = d
                bestZoneIdx = ref.zoneIdx
            }
        }
        return if (bestZoneIdx >= 0) bestZoneIdx to bestDistance else null
    }

    /** Point query: inside zones (by index) + nearest boundary distance. */
    fun status(lat: Double, lon: Double): PolygonStatus {
        if (!hasData) return PolygonStatus(emptyList(), -1, null)

        val cell = gridCell(lat, lon)
        val candidateEdgeIndices = collectCandidateEdges(cell.row, cell.col)

        var nearestDistance = Double.MAX_VALUE
        var nearestZoneIdx = -1
        for (edgeIdx in candidateEdgeIndices) {
            val ref = edges[edgeIdx]
            val dist = SpatialOperations.pointToSegmentDistance(LatLng(lat, lon), ref.a, ref.b)
            if (dist < nearestDistance) {
                nearestDistance = dist
                nearestZoneIdx = ref.zoneIdx
            }
        }

        val insideZoneIdxs = zones.indices.filter { pointInsidePolygon(lat, lon, zones[it]) }

        val resolvedDistance = if (nearestDistance < Double.MAX_VALUE) {
            nearestDistance
        } else if (insideZoneIdxs.isNotEmpty()) {
            bruteForceNearestEdge(lat, lon, zones[insideZoneIdxs.first()])
        } else {
            Double.MAX_VALUE
        }
        val signedDistance = if (insideZoneIdxs.isNotEmpty() && resolvedDistance < Double.MAX_VALUE) {
            -resolvedDistance
        } else {
            resolvedDistance.takeIf { it < Double.MAX_VALUE }
        }

        return PolygonStatus(
            insideZoneIdxs = insideZoneIdxs,
            nearestZoneIdx = nearestZoneIdx,
            nearestBoundaryM = signedDistance
        )
    }

    private fun bruteForceNearestEdge(lat: Double, lon: Double, zone: IndexedZone): Double {
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

    private fun gridCell(lat: Double, lon: Double): GridCell {
        val row = ((lat - minLat) / cellSizeLat).toInt().coerceIn(0, rowCount - 1)
        val col = ((lon - minLon) / cellSizeLon).toInt().coerceIn(0, colCount - 1)
        return GridCell(row, col)
    }

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

    private fun pointInsidePolygon(lat: Double, lon: Double, zone: IndexedZone): Boolean {
        val ring = zone.outerRing
        if (ring.size < 3) return false
        var inside = false
        var j = ring.size - 1
        for (i in ring.indices) {
            val yi = ring[i].latitude; val xi = ring[i].longitude
            val yj = ring[j].latitude; val xj = ring[j].longitude
            if (((yi > lat) != (yj > lat)) && (lon < (xj - xi) * (lat - yi) / (yj - yi) + xi)) inside = !inside
            j = i
        }
        if (!inside) return false
        for (hole in zone.holes) {
            if (hole.size < 3) continue
            var inHole = false
            var k = hole.size - 1
            for (i in hole.indices) {
                val yi = hole[i].latitude; val xi = hole[i].longitude
                val yj = hole[k].latitude; val xj = hole[k].longitude
                if (((yi > lat) != (yj > lat)) && (lon < (xj - xi) * (lat - yi) / (yj - yi) + xi)) inHole = !inHole
                k = i
            }
            if (inHole) return false
        }
        return true
    }

    private fun segmentIntersectionDistance(p1: LatLng, p2: LatLng, q1: LatLng, q2: LatLng): Double? {
        val midLat = (p1.latitude + p2.latitude + q1.latitude + q2.latitude) / 4.0
        val mPerDegLat = SpatialOperations.EARTH_RADIUS_M * PI / 180.0
        val mPerDegLon = mPerDegLat * cos(Math.toRadians(midLat))
        fun toProj(p: LatLng) = Pair(p.longitude * mPerDegLon, p.latitude * mPerDegLat)
        val (p1x, p1y) = toProj(p1)
        val (p2x, p2y) = toProj(p2)
        val (q1x, q1y) = toProj(q1)
        val (q2x, q2y) = toProj(q2)
        val rx = p2x - p1x; val ry = p2y - p1y
        val sx = q2x - q1x; val sy = q2y - q1y
        val crossRS = rx * sy - ry * sx
        if (abs(crossRS) < 1e-12) return null
        val qpx = q1x - p1x; val qpy = q1y - p1y
        val t = (qpx * sy - qpy * sx) / crossRS
        val u = (qpx * ry - qpy * rx) / crossRS
        if (t < 0.0 || t > 1.0 || u < 0.0 || u > 1.0) return null
        val ix = p1x + t * rx
        val iy = p1y + t * ry
        val iLat = iy / mPerDegLat
        val iLon = ix / mPerDegLon
        return SpatialOperations.haversine(p1, LatLng(iLat, iLon))
    }

    // ── Directional + point primitives ────────────────────────────────────────

    /** First zone wall inside the forward cone; halfAngle 0 degenerates to the exact heading ray. */
    fun boundaryInCone(
        lat: Double, lon: Double,
        headingDeg: Double,
        halfAngleDeg: Double,
        maxM: Double,
        kind: ZoneKind
    ): BoundaryHit? {
        if (!hasData) return null
        if (halfAngleDeg <= 0.0) {
            return firstAhead(lat, lon, headingDeg, maxM)?.let { (idx, d) ->
                BoundaryHit(zones[idx], kind, d, SpatialOperations.pointAlongBearing(lat, lon, headingDeg, d))
            }
        }
        val origin = LatLng(lat, lon)
        val rayEnd = SpatialOperations.pointAlongBearing(lat, lon, headingDeg, maxM)
        var bestZoneIdx = -1
        var bestDistance = Double.MAX_VALUE
        var bestPos: LatLng? = null
        for (edgeIdx in collectRingEdges(lat, lon, maxM)) {
            val ref = edges[edgeIdx]
            val mid = LatLng((ref.a.latitude + ref.b.latitude) / 2.0, (ref.a.longitude + ref.b.longitude) / 2.0)
            if (angleDiff(SpatialOperations.initialBearing(origin, mid), headingDeg) > halfAngleDeg) continue
            val hit = segmentIntersectionDistance(origin, rayEnd, ref.a, ref.b)
            val dist: Double
            val pos: LatLng
            if (hit != null && hit in 0.0..maxM) {
                dist = hit
                pos = SpatialOperations.pointAlongBearing(lat, lon, headingDeg, hit)
            } else {
                val dA = SpatialOperations.haversine(origin, ref.a)
                val dB = SpatialOperations.haversine(origin, ref.b)
                if (dA <= dB) { dist = dA; pos = ref.a } else { dist = dB; pos = ref.b }
            }
            if (dist in 0.0..maxM && dist < bestDistance) {
                bestDistance = dist
                bestZoneIdx = ref.zoneIdx
                bestPos = pos
            }
        }
        return if (bestZoneIdx >= 0) BoundaryHit(zones[bestZoneIdx], kind, bestDistance, bestPos) else null
    }

    /** "Where am I relative to this kind?" — no direction. */
    fun zoneStatus(lat: Double, lon: Double, kind: ZoneKind, forceNullStrictest: Boolean = false): ZoneStatus {
        val s = status(lat, lon)
        val inside = s.insideZoneIdxs.map { zones[it] }
        return ZoneStatus(
            insideAny = inside.isNotEmpty(),
            nearestBoundaryM = s.nearestBoundaryM,
            insideZones = inside,
            strictestSpeedKn = if (forceNullStrictest) null else inside.mapNotNull { it.speedLimitKn }.minOrNull()
        )
    }

    private fun collectRingEdges(lat: Double, lon: Double, maxM: Double): Set<Int> {
        val originCell = gridCell(lat, lon)
        val maxRings = (maxM / cellSizeM).toInt().coerceIn(1, 20) + 1
        val out = mutableSetOf<Int>()
        for (ring in 0..maxRings) {
            for (dr in -ring..ring) {
                for (dc in -ring..ring) {
                    if (abs(dr) < ring && abs(dc) < ring) continue
                    val r = originCell.row + dr
                    val c = originCell.col + dc
                    if (r in 0 until rowCount && c in 0 until colCount) {
                        grid[GridCell(r, c)]?.let { out.addAll(it) }
                    }
                }
            }
            if (ring > 0 && ring * cellSizeM > maxM * 1.5) break
        }
        return out
    }

    private fun angleDiff(a: Double, b: Double): Double = abs(((a - b + 540.0) % 360.0) - 180.0)
}
