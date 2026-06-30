package ykws.android.maro.spatial

import android.util.Log
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.data.model.markers.MarkerGeometry
import ykws.android.maro.data.model.markers.UserMarker
import kotlin.math.*

// ─────────────────────────────────────────────────────────────────────────────
// WhereAmI result types
// ─────────────────────────────────────────────────────────────────────────────

/** Outcome of a single-marker match resolution. */
sealed class WhereAmIMatch {

    /**
     * Boat is geometrically inside the marker's zone — purely geometric check,
     * no land test. Pins never produce a [ZoneMatch] (they have no zone).
     */
    data class ZoneMatch(
        val marker: UserMarker,
        val zoneSizeM: Double,           // radius for Circle, width for Corridor
        val distanceToCenterM: Double,   // boat to zone center
        val bearingDeg: Double,          // bearing from boat to zone center (0-360)
        val children: List<WhereAmIMatch> = emptyList()
    ) : WhereAmIMatch()

    /**
     * Boat is outside the marker's zone but within the derived proximity range
     * of the closest unblocked boundary point (sea path clear of land).
     */
    data class ProximityMatch(
        val marker: UserMarker,
        val seaDistanceM: Double,        // sea-path distance to closest unblocked boundary point
        val bearingDeg: Double           // bearing from boat to closest unblocked point (0-360)
    ) : WhereAmIMatch()
}

/** Flat result container — depth-first, leaves-first, capped at 8. */
data class WhereAmIResult(val allMatches: List<WhereAmIMatch>)

// ─────────────────────────────────────────────────────────────────────────────
// Proximity configuration
// ─────────────────────────────────────────────────────────────────────────────

data class ProximityConfig(
    val pinM: Double = 200.0,
    val zoneMultiplier: Double = 3.0
)

// ─────────────────────────────────────────────────────────────────────────────
// Angular interval helpers (unified shadow projection)
// ─────────────────────────────────────────────────────────────────────────────

/** A bearing interval [start, end] in degrees, guaranteed non-wrapping (start < end). */
private data class AngularInterval(val start: Double, val end: Double) {
    val mid: Double get() = (start + end) / 2.0
    /** Intersects this interval with [outer]. Returns null if they don't overlap. */
    fun clampTo(outer: AngularInterval): AngularInterval? {
        val s = maxOf(start, outer.start)
        val e = minOf(end, outer.end)
        return if (s < e) AngularInterval(s, e) else null
    }
}

/**
 * The view cone from the boat to the zone — the angular sector the zone
 * occupies from the boat's perspective, plus a bounding box for spatial queries.
 */
private data class ViewCone(
    val interval: AngularInterval,
    val bbox: BBox,
    /** Distance from boat to zone boundary at a given bearing (used for depth checks). */
    val zoneDistanceAt: (Double) -> Double
)

// ─────────────────────────────────────────────────────────────────────────────
// MarkerMatcher object
// ─────────────────────────────────────────────────────────────────────────────

object MarkerMatcher {

    /** Intersection within this distance (metres) of a coastline vertex is
     *  considered grazing and ignored. */
    private const val GRAZING_TOLERANCE_M = 10.0

    /** Distance threshold (metres) at which the boat is considered "at" the
     *  marker — skip the expensive land-blocking search. */
    private const val AT_MARKER_THRESHOLD_M = 1.0

    /** Maximum search radius (metres) — BBox pre-filter fence. Markers whose
     *  expanded bounding box doesn't overlap the boat's 1 km circle are skipped. */
    private const val MAX_SEARCH_RADIUS_M = 1000.0

    /** Maximum number of results in the display list. */
    private const val MAX_RESULTS = 8

    // ─────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Resolves a single marker against the current boat position.
     *
     * 1. Zone check (purely geometric, no land test)
     * 2. Compute proximity range (override or formula, no cap)
     * 3. Boat at marker (≤ 1 m) → skip land check
     * 4. Find closest unblocked boundary point via [closestUnblockedPoint]
     * 5. Sea-path distance ≤ range → [WhereAmIMatch.ProximityMatch], else null
     */
    fun resolveMatch(
        boat: LatLng,
        marker: UserMarker,
        spatialIndex: CoastlineSpatialIndex
    ): WhereAmIMatch? {
        // ── 1. Zone check ──
        if (isInsideGeometry(boat, marker.geometry)) {
            val center = zoneCenterOf(marker.geometry)
            val dist = distanceToClosestGeometryPoint(boat, marker.geometry)
            val zoneSize = when (marker.geometry) {
                is MarkerGeometry.Circle -> marker.geometry.radiusM
                is MarkerGeometry.Corridor -> marker.geometry.widthM
                is MarkerGeometry.Pin -> 0.0
            }
            val bearing = SpatialOperations.initialBearing(center, boat)
            return WhereAmIMatch.ZoneMatch(marker, zoneSize, dist, bearing)
        }

        // ── 2. Compute proximity range ──
        val range = proximityRange(marker)

        // ── 3. Boat at marker → skip land check ──
        val directDist = distanceToClosestGeometryPoint(boat, marker.geometry)
        if (directDist <= AT_MARKER_THRESHOLD_M) {
            return if (directDist <= range) {
                val center = zoneCenterOf(marker.geometry)
                WhereAmIMatch.ProximityMatch(marker, directDist, SpatialOperations.initialBearing(center, boat))
            } else null
        }

        // ── 4. Find closest unblocked boundary point ──
        val unblocked = closestUnblockedPoint(boat, marker, spatialIndex)
            ?: return null

        // ── 5. Sea-path distance ≤ range? ──
        // closestUnblockedPoint now returns actual boundary points (not centreline)
        val dist = SpatialOperations.haversine(boat, unblocked)
        val match = dist <= range
        Log.d("WIA", "  range=${"%.0f".format(range)} dist=${"%.0f".format(dist)} ${if (match) "MATCH" else "REJECTED"}")
        return if (match)
            WhereAmIMatch.ProximityMatch(marker, dist, SpatialOperations.initialBearing(unblocked, boat))
        else null
    }

    /**
     * Resolves all [markers] against the boat position.
     *
     * Algorithm:
     * 1. BBox pre-filter with 1 km search fence — skip markers outside range
     * 2. Resolve each surviving marker via [resolveMatch]; discard null
     * 3. Build spatial containment tree (large zones contain smaller ones)
     * 4. Depth-first, leaves-first traversal sorted by size asc at each level
     * 5. Cap at [MAX_RESULTS]
     */
    fun resolveAllMarkers(
        boat: LatLng,
        markers: List<UserMarker>,
        spatialIndex: CoastlineSpatialIndex
    ): WhereAmIResult {
        if (markers.isEmpty()) return WhereAmIResult(emptyList())

        // ── 1. BBox pre-filter (1 km fence) + resolve ──
        val results = mutableListOf<WhereAmIMatch>()
        for (marker in markers) {
            if (!boatInExpandedBbox(boat, marker, MAX_SEARCH_RADIUS_M)) continue
            val match = resolveMatch(boat, marker, spatialIndex)
            if (match != null) results.add(match)
        }

        if (results.isEmpty()) return WhereAmIResult(emptyList())

        // ── 2. Build containment tree ──
        val nestedIds = mutableSetOf<String>()
        val zoneMatches = results.filterIsInstance<WhereAmIMatch.ZoneMatch>().toMutableList()

        for (i in zoneMatches.indices) {
            val outer = zoneMatches[i]
            val tempChildren = mutableListOf<WhereAmIMatch>()
            for (other in results) {
                if (other === outer) continue
                if (isMatchInsideZone(other, outer.marker.geometry)) {
                    // Prevent mutual nesting: a ZoneMatch should only nest
                    // inside a strictly larger zone — never a same-size or smaller one.
                    if (other is WhereAmIMatch.ZoneMatch && other.zoneSizeM >= outer.zoneSizeM) continue
                    tempChildren.add(other)
                    nestedIds.add(markerOf(other).id)
                }
            }
            if (tempChildren.isNotEmpty()) {
                zoneMatches[i] = outer.copy(children = tempChildren.toList())
            }
        }

        // Rebuild list with updated zone matches
        val finalResults = results.map { match ->
            if (match is WhereAmIMatch.ZoneMatch) {
                zoneMatches.find { it.marker.id == match.marker.id } ?: match
            } else match
        }

        val roots = finalResults.filter { markerOf(it).id !in nestedIds }

        // ── 3. Depth-first, leaves-first traversal ──
        val display = depthFirstLeavesFirst(roots).take(MAX_RESULTS)

        return WhereAmIResult(display)
    }

    // ─────────────────────────────────────────────────────────────────────
    // Land-blocking engine — unified tangent-guided angular shadow projection
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Tests whether the segment A→B crosses any land edge, using the spatial
     * index for grid-pre-filtered queries. Applies 10 m grazing tolerance.
     */
    fun segmentIntersectsLand(
        a: LatLng,
        b: LatLng,
        spatialIndex: CoastlineSpatialIndex
    ): Boolean {
        return spatialIndex.segmentIntersectsLand(a, b)
    }

    /**
     * Finds the closest point on [marker]'s geometry boundary that has a
     * clear sea line-of-sight to [boat].
     *
     * Unified tangent-guided algorithm — one code path for Pin, Circle, Corridor:
     * 1. Compute the view cone(s) from boat to the zone
     * 2. Gather coastline edges in the cone's bbox
     * 3. Compute angular shadows → merge → unblocked intervals
     * 4. Find the closest zone boundary point in each unblocked interval
     */
    fun closestUnblockedPoint(
        boat: LatLng,
        marker: UserMarker,
        spatialIndex: CoastlineSpatialIndex
    ): LatLng? {
        // ── Direct-line fast path: if the geometrically-closest boundary
        //     point has a clear sea line-of-sight, return it immediately.
        //     This avoids angular-shadow over-blocking (e.g. Sainte Marguerite). ──
        val closestGeom = closestGeometricBoundaryPoint(boat, marker.geometry)
        if (closestGeom != null && !spatialIndex.segmentIntersectsLand(boat, closestGeom)) {
            Log.d("WIA", "  DIRECT: clear path, skipping angular analysis")
            return closestGeom
        }

        // ── Fall back to angular shadow analysis ──
        val cones = viewCones(boat, marker.geometry)
        var best: LatLng? = null
        var bestDist = Double.MAX_VALUE

        for (cone in cones) {
            val segments = spatialIndex.segmentsInBbox(cone.bbox)
            val shadows = segments.mapNotNull { angularShadow(boat, it, cone) }
            val unblocked = mergeAndComplement(shadows, cone.interval)
            Log.d("WIA", "  ${marker.name}: cone=[${cone.interval.start}°,${cone.interval.end}°] segs=${segments.size} shadows=${shadows.size} unblocked=${unblocked.size}")
            if (unblocked.isEmpty() && shadows.isNotEmpty()) {
                for (s in shadows.take(3)) {
                    val zd = cone.zoneDistanceAt(s.mid)
                    Log.d("WIA", "    shadow [${"%.1f".format(s.start)}°,${"%.1f".format(s.end)}°] zd=$zd")
                }
            }
            val candidate = bestBoundaryPoint(boat, marker.geometry, unblocked)
            if (candidate != null) {
                val d = SpatialOperations.haversine(boat, candidate)
                Log.d("WIA", "    candidate dist=$d")
                if (d < bestDist) { best = candidate; bestDist = d }
            } else {
                Log.d("WIA", "    NO candidate (unblocked=${unblocked.size})")
            }
        }

        Log.d("WIA", "  RESULT bestDist=$bestDist ${if (best == null) "NULL" else ""}")
        return best
    }

    /** Closest point on [geometry] boundary to [boat], ignoring land. Returns
     *  null if the boat is inside the geometry (zone check handled upstream). */
    private fun closestGeometricBoundaryPoint(
        boat: LatLng, geometry: MarkerGeometry
    ): LatLng? {
        return when (geometry) {
            is MarkerGeometry.Pin -> geometry.position
            is MarkerGeometry.Circle -> {
                val dist = SpatialOperations.haversine(boat, geometry.center)
                if (dist <= geometry.radiusM) return null  // inside
                val bearing = SpatialOperations.initialBearing(boat, geometry.center)
                val t = dist - geometry.radiusM
                SpatialOperations.pointAlongBearing(boat.latitude, boat.longitude, bearing, t)
            }
            is MarkerGeometry.Corridor -> {
                val halfW = geometry.widthM / 2.0
                if (isInsideGeometry(boat, geometry)) return null  // inside
                val closestOnCL = SpatialOperations.projectPointOntoSegment(boat, geometry.p1, geometry.p2)
                val distToCL = SpatialOperations.pointToSegmentDistance(boat, geometry.p1, geometry.p2)
                val bearing = SpatialOperations.initialBearing(boat, closestOnCL)
                val distToBoundary = (distToCL - halfW).coerceAtLeast(0.0)
                SpatialOperations.pointAlongBearing(boat.latitude, boat.longitude, bearing, distToBoundary)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Geometry containment checks (purely geometric)
    // ─────────────────────────────────────────────────────────────────────

    private fun isInsideGeometry(boat: LatLng, geometry: MarkerGeometry): Boolean {
        return when (geometry) {
            is MarkerGeometry.Pin -> false
            is MarkerGeometry.Circle ->
                SpatialOperations.haversine(boat, geometry.center) <= geometry.radiusM
            is MarkerGeometry.Corridor -> {
                val halfW = geometry.widthM / 2.0
                SpatialOperations.pointToSegmentDistance(boat, geometry.p1, geometry.p2) <= halfW ||
                SpatialOperations.haversine(boat, geometry.p1) <= halfW ||
                SpatialOperations.haversine(boat, geometry.p2) <= halfW
            }
        }
    }

    /** Returns the geometric center of a marker's zone. */
    internal fun zoneCenterOf(geometry: MarkerGeometry): LatLng = when (geometry) {
        is MarkerGeometry.Pin -> geometry.position
        is MarkerGeometry.Circle -> geometry.center
        is MarkerGeometry.Corridor -> LatLng(
            (geometry.p1.latitude + geometry.p2.latitude) / 2.0,
            (geometry.p1.longitude + geometry.p2.longitude) / 2.0
        )
    }

    private fun distanceToClosestGeometryPoint(
        boat: LatLng, geometry: MarkerGeometry
    ): Double = when (geometry) {
        is MarkerGeometry.Pin -> SpatialOperations.haversine(boat, geometry.position)
        is MarkerGeometry.Circle -> SpatialOperations.haversine(boat, geometry.center)
        is MarkerGeometry.Corridor -> {
            val dSeg = SpatialOperations.pointToSegmentDistance(boat, geometry.p1, geometry.p2)
            val dP1 = SpatialOperations.haversine(boat, geometry.p1)
            val dP2 = SpatialOperations.haversine(boat, geometry.p2)
            minOf(dSeg, dP1, dP2)
        }
    }

    /** Extracts the [UserMarker] from a [WhereAmIMatch]. */
    private fun markerOf(match: WhereAmIMatch): UserMarker = when (match) {
        is WhereAmIMatch.ZoneMatch -> match.marker
        is WhereAmIMatch.ProximityMatch -> match.marker
    }

    /** Tests whether [match]'s marker position is geometrically inside [zoneGeometry]. */
    private fun isMatchInsideZone(
        match: WhereAmIMatch,
        zoneGeometry: MarkerGeometry
    ): Boolean {
        val point = when (val g = markerOf(match).geometry) {
            is MarkerGeometry.Pin -> g.position
            is MarkerGeometry.Circle -> g.center
            is MarkerGeometry.Corridor -> LatLng(
                (g.p1.latitude + g.p2.latitude) / 2.0,
                (g.p1.longitude + g.p2.longitude) / 2.0
            )
        }
        return isInsideGeometry(point, zoneGeometry)
    }

    // ─────────────────────────────────────────────────────────────────────
    // Proximity range
    // ─────────────────────────────────────────────────────────────────────

    /** Proximity range from marker, with safe fallback to default formula. */
    private fun proximityRange(marker: UserMarker): Double =
        marker.proximityOverrideM ?: when (marker.geometry) {
            is MarkerGeometry.Pin -> 200.0
            is MarkerGeometry.Circle -> marker.geometry.radiusM * 3.0
            is MarkerGeometry.Corridor -> marker.geometry.widthM * 3.0
        }

    // ─────────────────────────────────────────────────────────────────────
    // BBox pre-filter
    // ─────────────────────────────────────────────────────────────────────

    private fun boatInExpandedBbox(
        boat: LatLng, marker: UserMarker, rangeM: Double
    ): Boolean {
        val bbox = marker.bbox
        val degPerMeterLat = 1.0 / (SpatialOperations.EARTH_RADIUS_M * PI / 180.0)
        val degPerMeterLon = degPerMeterLat / cos(Math.toRadians(boat.latitude))
        val marginLat = rangeM * degPerMeterLat
        val marginLon = rangeM * degPerMeterLon
        return boat.latitude >= bbox.latSouth - marginLat &&
               boat.latitude <= bbox.latNorth + marginLat &&
               boat.longitude >= bbox.lonWest - marginLon &&
               boat.longitude <= bbox.lonEast + marginLon
    }

    // ─────────────────────────────────────────────────────────────────────
    // Traversal
    // ─────────────────────────────────────────────────────────────────────

    /** Depth-first, children-before-parent traversal. Siblings sorted by sortScore + zoneSize. */
    private fun depthFirstLeavesFirst(nodes: List<WhereAmIMatch>): List<WhereAmIMatch> {
        val result = mutableListOf<WhereAmIMatch>()
        for (node in nodes.sortedWith(compareBy(
            { sortScore(it) },
            { zoneSizeOf(it) }
        ))) {
            if (node is WhereAmIMatch.ZoneMatch && node.children.isNotEmpty()) {
                result.addAll(depthFirstLeavesFirst(node.children))
            }
            result.add(node)
        }
        return result
    }

    /**
     * Composite sort score — lower = displayed first.
     * Formula: categoryBase + typeWeight × percentage
     *
     * ZoneMatch:     0.0 + typeWeight × (distanceToCenterM / zoneSizeM)
     * ProximityMatch: 1.0 + typeWeight × (seaDistanceM / proximityRange)
     *
     * typeWeight: Pin=0.5 / Circle=1.0 / Corridor=2.0 (from maro.properties)
     *   ZoneMatch always beats ProximityMatch (0.0–2.0 < 1.0–3.0).
     *   Tie-breaker: smaller zoneSize wins.
     */
    private fun sortScore(match: WhereAmIMatch): Double {
        val typeWeight = when (markerOf(match).geometry) {
            is MarkerGeometry.Pin -> AppConfig.markerSortTypeWeightPin
            is MarkerGeometry.Circle -> AppConfig.markerSortTypeWeightCircle
            is MarkerGeometry.Corridor -> AppConfig.markerSortTypeWeightCorridor
        }
        val (categoryBase, percentage) = when (match) {
            is WhereAmIMatch.ZoneMatch ->
                0.0 to (match.distanceToCenterM / match.zoneSizeM).coerceAtMost(1.0)
            is WhereAmIMatch.ProximityMatch -> {
                val range = proximityRange(markerOf(match))
                1.0 to (match.seaDistanceM / range).coerceAtMost(1.0)
            }
        }
        return categoryBase + typeWeight * percentage
    }

    /** Zone size for tie-breaking. Pin=0, Circle=radius, Corridor=width. */
    private fun zoneSizeOf(match: WhereAmIMatch): Double = when (val g = markerOf(match).geometry) {
        is MarkerGeometry.Pin -> 0.0
        is MarkerGeometry.Circle -> g.radiusM
        is MarkerGeometry.Corridor -> g.widthM
    }

    // ─────────────────────────────────────────────────────────────────────
    // View cone computation (geometry-specific)
    // ─────────────────────────────────────────────────────────────────────

    /** Returns 1 or 2 non-wrapping view cones from [boat] to [geometry]. */
    private fun viewCones(boat: LatLng, geometry: MarkerGeometry): List<ViewCone> {
        return when (geometry) {
            is MarkerGeometry.Pin -> {
                val bearing = SpatialOperations.initialBearing(boat, geometry.position)
                listOf(pointCone(boat, geometry.position, bearing))
            }
            is MarkerGeometry.Circle -> {
                val dist = SpatialOperations.haversine(boat, geometry.center)
                val bearing = SpatialOperations.initialBearing(boat, geometry.center)
                val halfAngle = if (dist <= geometry.radiusM) 180.0
                    else Math.toDegrees(asin((geometry.radiusM / dist).coerceIn(-1.0, 1.0)))
                val rawStart = bearing - halfAngle
                val rawEnd = bearing + halfAngle
                val maxDist = SpatialOperations.haversine(boat, geometry.center) + geometry.radiusM
                splitWrappingCone(boat, rawStart, rawEnd, maxDist) { b ->
                    circleDistanceAtBearing(boat, geometry.center, geometry.radiusM, b)
                }
            }
            is MarkerGeometry.Corridor -> {
                val halfW = geometry.widthM / 2.0
                // Compute bearings to all extreme points of the corridor silhouette
                val extremes = mutableListOf<Double>()
                // P1 disc
                val bP1 = SpatialOperations.initialBearing(boat, geometry.p1)
                val dP1 = SpatialOperations.haversine(boat, geometry.p1)
                val aP1 = if (dP1 <= halfW) 180.0
                    else Math.toDegrees(asin((halfW / dP1).coerceIn(-1.0, 1.0)))
                extremes.add(bP1 - aP1); extremes.add(bP1 + aP1)
                // P2 disc
                val bP2 = SpatialOperations.initialBearing(boat, geometry.p2)
                val dP2 = SpatialOperations.haversine(boat, geometry.p2)
                val aP2 = if (dP2 <= halfW) 180.0
                    else Math.toDegrees(asin((halfW / dP2).coerceIn(-1.0, 1.0)))
                extremes.add(bP2 - aP2); extremes.add(bP2 + aP2)
                // Perpendicular offsets for rectangular edges
                val segBearing = SpatialOperations.initialBearing(geometry.p1, geometry.p2)
                val perpBearing1 = (segBearing + 90.0) % 360.0
                val perpBearing2 = (segBearing - 90.0).let { if (it < 0) it + 360 else it }
                for (pt in listOf(geometry.p1, geometry.p2)) {
                    for (perp in listOf(perpBearing1, perpBearing2)) {
                        val offset = SpatialOperations.pointAlongBearing(
                            pt.latitude, pt.longitude, perp, halfW)
                        extremes.add(SpatialOperations.initialBearing(boat, offset))
                    }
                }
                // Normalize all bearings to [0, 360)
                val norm = extremes.map { (it % 360 + 360) % 360 }
                val minB = norm.min()
                val maxB = norm.max()
                val maxDist = maxOf(
                    SpatialOperations.haversine(boat, geometry.p1),
                    SpatialOperations.haversine(boat, geometry.p2),
                    SpatialOperations.pointToSegmentDistance(boat, geometry.p1, geometry.p2)
                ) + halfW
                splitWrappingCone(boat, minB, maxB, maxDist) { b ->
                    corridorDistanceAtBearing(boat, geometry.p1, geometry.p2, halfW, b)
                }
            }
        }
    }

    /** Cone for a single point (Pin). */
    private fun pointCone(boat: LatLng, point: LatLng, bearing: Double): ViewCone {
        val dist = SpatialOperations.haversine(boat, point)
        val margin = dist * 0.1 + 50.0  // small bbox around the point
        val degPerMeterLat = 1.0 / (SpatialOperations.EARTH_RADIUS_M * PI / 180.0)
        val degPerMeterLon = degPerMeterLat / cos(Math.toRadians(boat.latitude))
        val dLat = margin * degPerMeterLat
        val dLon = margin * degPerMeterLon
        val bbox = BBox(
            minOf(boat.latitude, point.latitude) - dLat,
            maxOf(boat.latitude, point.latitude) + dLat,
            minOf(boat.longitude, point.longitude) - dLon,
            maxOf(boat.longitude, point.longitude) + dLon
        )
        return ViewCone(AngularInterval(bearing, bearing), bbox) { dist }
    }

    /**
     * Splits a raw [start, end] bearing range into 1 or 2 non-wrapping cones.
     * If the range wraps across 0°, returns two cones: [start, 360] and [0, end].
     */
    private fun splitWrappingCone(
        boat: LatLng, rawStart: Double, rawEnd: Double,
        maxDist: Double,
        zoneDistFn: (Double) -> Double
    ): List<ViewCone> {
        val start = (rawStart % 360 + 360) % 360
        val end = (rawEnd % 360 + 360) % 360

        if (start <= end) {
            return listOf(buildCone(boat, start, end, maxDist, zoneDistFn))
        }
        // Wraps: split into [start, 360] and [0, end]
        return listOf(
            buildCone(boat, start, 360.0, maxDist, zoneDistFn),
            buildCone(boat, 0.0, end, maxDist, zoneDistFn)
        )
    }

    private fun buildCone(
        boat: LatLng, start: Double, end: Double,
        maxDist: Double,
        zoneDistFn: (Double) -> Double
    ): ViewCone {
        val degPerMeterLat = 1.0 / (SpatialOperations.EARTH_RADIUS_M * PI / 180.0)
        val degPerMeterLon = degPerMeterLat / cos(Math.toRadians(boat.latitude))
        val paddedDist = maxDist + 100.0
        val margin = paddedDist * degPerMeterLat
        val marginLon = paddedDist * degPerMeterLon
        val bbox = BBox(
            boat.latitude - margin, boat.latitude + margin,
            boat.longitude - marginLon, boat.longitude + marginLon
        )
        return ViewCone(AngularInterval(start, end), bbox, zoneDistFn)
    }

    /**
     * Distance from [boat] to the corridor boundary at the given [bearing] (0° = north).
     * Intersects the ray with the two end-cap circles and the two offset edge lines,
     * returning the minimum positive intersection distance.
     */
    private fun corridorDistanceAtBearing(
        boat: LatLng, p1: LatLng, p2: LatLng, halfW: Double, bearing: Double,
        debug: Boolean = false
    ): Double {
        // Local planar projection centred at the midpoint of boat + corridor endpoints
        val midLat = (boat.latitude + p1.latitude + p2.latitude) / 3.0
        val mPerDegLat = SpatialOperations.EARTH_RADIUS_M * PI / 180.0
        val mPerDegLon = mPerDegLat * cos(Math.toRadians(midLat))

        fun toLocal(p: LatLng): Pair<Double, Double> =
            Pair(p.longitude * mPerDegLon, p.latitude * mPerDegLat)

        val (ox, oy) = toLocal(boat)
        val rad = Math.toRadians(bearing)
        val dx = sin(rad)   // east component
        val dy = cos(rad)   // north component

        var minDist = Double.MAX_VALUE

        // ── End-cap circles (P1, P2) ──
        for (center in listOf(p1, p2)) {
            val (cx, cy) = toLocal(center)
            val vx = ox - cx
            val vy = oy - cy
            // |(ox,oy) + t*(dx,dy) - (cx,cy)| = halfW
            // (vx + t*dx)² + (vy + t*dy)² = halfW²
            // t² + 2t*(vx*dx + vy*dy) + |v|² - halfW² = 0
            val a = dx * dx + dy * dy  // = 1 (unit direction)
            val b = 2.0 * (vx * dx + vy * dy)
            val c = vx * vx + vy * vy - halfW * halfW
            val disc = b * b - 4.0 * a * c
            if (disc >= 0.0) {
                val sqrtDisc = sqrt(disc)
                val t1 = (-b - sqrtDisc) / (2.0 * a)
                val t2 = (-b + sqrtDisc) / (2.0 * a)
                if (t1 > 0.0) minDist = minOf(minDist, t1)
                else if (t2 > 0.0) minDist = minOf(minDist, t2)
            }
        }

        // ── Offset edge lines ──
        val (p1x, p1y) = toLocal(p1)
        val (p2x, p2y) = toLocal(p2)
        val segDx = p2x - p1x
        val segDy = p2y - p1y
        val segLen = sqrt(segDx * segDx + segDy * segDy)
        if (segLen > 0.0) {
            // Perpendicular unit vector (rotate segDir 90° CW)
            val perpDx = -segDy / segLen
            val perpDy = segDx / segLen

            for (sign in listOf(1.0, -1.0)) {
                val offsetX = sign * halfW * perpDx
                val offsetY = sign * halfW * perpDy
                // Edge line: A = p1+offset, B = p2+offset
                val ax = p1x + offsetX; val ay = p1y + offsetY
                val bx = p2x + offsetX; val by = p2y + offsetY
                val edgeDx = bx - ax; val edgeDy = by - ay

                // Ray: O + t*D = A + s*edgeD
                // Solve: t*dx - s*edgeDx = ax - ox
                //        t*dy - s*edgeDy = ay - oy
                val det = dx * (-edgeDy) - dy * (-edgeDx)
                if (abs(det) < 1e-12) continue  // parallel
                val t = ((ax - ox) * (-edgeDy) - (ay - oy) * (-edgeDx)) / det
                val s = (dx * (ay - oy) - dy * (ax - ox)) / det
                if (t > 0.0 && s >= 0.0 && s <= 1.0) {
                    minDist = minOf(minDist, t)
                }
            }
        }

        val result = if (minDist == Double.MAX_VALUE) {
            // Ray missed all components — use far-boundary distance as safe upper bound
            maxOf(
                SpatialOperations.haversine(boat, p1),
                SpatialOperations.haversine(boat, p2),
                SpatialOperations.pointToSegmentDistance(boat, p1, p2)
            ) + halfW
        } else minDist
        return result
    }

    /**
     * Intersection point of the ray from [boat] at [bearing] with the corridor's
     * offset edge lines. Returns null if the ray misses both edges or hits outside
     * segment bounds.
     */
    private fun corridorEdgePointAtBearing(
        boat: LatLng, p1: LatLng, p2: LatLng, halfW: Double, bearing: Double
    ): LatLng? {
        val midLat = (boat.latitude + p1.latitude + p2.latitude) / 3.0
        val mPerDegLat = SpatialOperations.EARTH_RADIUS_M * PI / 180.0
        val mPerDegLon = mPerDegLat * cos(Math.toRadians(midLat))

        fun toLocal(p: LatLng): Pair<Double, Double> =
            Pair(p.longitude * mPerDegLon, p.latitude * mPerDegLat)

        val (ox, oy) = toLocal(boat)
        val rad = Math.toRadians(bearing)
        val dx = sin(rad)
        val dy = cos(rad)

        val (p1x, p1y) = toLocal(p1)
        val (p2x, p2y) = toLocal(p2)
        val segDx = p2x - p1x
        val segDy = p2y - p1y
        val segLen = sqrt(segDx * segDx + segDy * segDy)
        if (segLen == 0.0) return null

        val perpDx = -segDy / segLen
        val perpDy = segDx / segLen
        var bestT = Double.MAX_VALUE

        for (sign in listOf(1.0, -1.0)) {
            val offsetX = sign * halfW * perpDx
            val offsetY = sign * halfW * perpDy
            val ax = p1x + offsetX; val ay = p1y + offsetY
            val bx = p2x + offsetX; val by = p2y + offsetY
            val edgeDx = bx - ax; val edgeDy = by - ay

            val det = dx * (-edgeDy) - dy * (-edgeDx)
            if (abs(det) < 1e-12) continue
            val t = ((ax - ox) * (-edgeDy) - (ay - oy) * (-edgeDx)) / det
            val s = (dx * (ay - oy) - dy * (ax - ox)) / det
            if (t > 0.0 && s >= 0.0 && s <= 1.0 && t < bestT) {
                bestT = t
            }
        }

        if (bestT == Double.MAX_VALUE) return null
        return SpatialOperations.pointAlongBearing(boat.latitude, boat.longitude, bearing, bestT)
    }

    /**
     * Distance from [boat] to the boundary of a circle (center, radius) along
     * the ray at the given [bearing] (0° = north from boat). Uses ray-circle
     * intersection — finds the entry point where the ray first hits the circle.
     */
    private fun circleDistanceAtBearing(
        boat: LatLng, center: LatLng, radiusM: Double, bearing: Double
    ): Double {
        val midLat = (boat.latitude + center.latitude) / 2.0
        val mPerDegLat = SpatialOperations.EARTH_RADIUS_M * PI / 180.0
        val mPerDegLon = mPerDegLat * cos(Math.toRadians(midLat))

        val ox = boat.longitude * mPerDegLon
        val oy = boat.latitude * mPerDegLat
        val cx = center.longitude * mPerDegLon
        val cy = center.latitude * mPerDegLat

        val rad = Math.toRadians(bearing)
        val dx = sin(rad)
        val dy = cos(rad)

        val vx = ox - cx
        val vy = oy - cy
        val a = dx * dx + dy * dy
        val b = 2.0 * (vx * dx + vy * dy)
        val c = vx * vx + vy * vy - radiusM * radiusM
        val disc = b * b - 4.0 * a * c

        if (disc < 0.0) return Double.MAX_VALUE
        val sqrtDisc = sqrt(disc)
        val t1 = (-b - sqrtDisc) / (2.0 * a)
        val t2 = (-b + sqrtDisc) / (2.0 * a)
        return when {
            t1 > 0.0 -> t1
            t2 > 0.0 -> t2
            else -> Double.MAX_VALUE
        }
    }

    /**
     * The point on the circle boundary where the ray from [boat] at [bearing]
     * first intersects. Returns null if the ray misses the circle.
     */
    private fun circlePointAtBearing(
        boat: LatLng, center: LatLng, radiusM: Double, bearing: Double
    ): LatLng? {
        val t = circleDistanceAtBearing(boat, center, radiusM, bearing)
        if (t == Double.MAX_VALUE) return null
        return SpatialOperations.pointAlongBearing(boat.latitude, boat.longitude, bearing, t)
    }

    // ─────────────────────────────────────────────────────────────────────
    // Angular shadow projection
    // ─────────────────────────────────────────────────────────────────────

    /** Computes the angular shadow (bearing range) a coastline edge casts from [boat]. */
    private fun angularShadow(
        boat: LatLng,
        seg: CoastlineSpatialIndex.BboxSegment,
        cone: ViewCone,
        debug: Boolean = false
    ): AngularInterval? {
        val bA = SpatialOperations.initialBearing(boat, seg.a).let { (it % 360 + 360) % 360 }
        val bB = SpatialOperations.initialBearing(boat, seg.b).let { (it % 360 + 360) % 360 }
        val interval = AngularInterval(minOf(bA, bB), maxOf(bA, bB))

        // Depth check: is this edge between boat and zone boundary?
        // Use zoneDist at the CLAMPED interval midpoint — the bearings where this
        // segment would actually block. Raw interval edges may be at bearings where
        // the zone is far away, causing false shadows.
        val clamped = interval.clampTo(cone.interval) ?: return null
        val segDist = SpatialOperations.pointToSegmentDistance(boat, seg.a, seg.b)
        val zoneDist = cone.zoneDistanceAt(clamped.mid)
        if (debug) {
            Log.d("WIA", "      sd=${"%.0f".format(segDist)} zd=${"%.0f".format(zoneDist)} → shadow [${"%.1f".format(clamped.start)}°,${"%.1f".format(clamped.end)}°]")
        }
        if (segDist >= zoneDist) return null  // behind zone → no shadow

        return clamped
    }

    // ─────────────────────────────────────────────────────────────────────
    // Interval merge
    // ─────────────────────────────────────────────────────────────────────

    /** Merges overlapping angular intervals (sort by start, sweep). */
    private fun mergeOverlapping(intervals: List<AngularInterval>): List<AngularInterval> {
        if (intervals.isEmpty()) return emptyList()
        val sorted = intervals.sortedBy { it.start }
        val merged = mutableListOf(sorted.first())
        for (i in 1 until sorted.size) {
            val cur = sorted[i]
            val last = merged.last()
            if (cur.start <= last.end) {
                merged[merged.lastIndex] = AngularInterval(last.start, maxOf(last.end, cur.end))
            } else {
                merged.add(cur)
            }
        }
        return merged
    }

    /** Returns the unblocked angular intervals (complement of [blocked] within [cone]). */
    private fun mergeAndComplement(
        blocked: List<AngularInterval>,
        cone: AngularInterval
    ): List<AngularInterval> {
        if (blocked.isEmpty()) return listOf(cone)
        val merged = mergeOverlapping(blocked)
        val unblocked = mutableListOf<AngularInterval>()
        var cursor = cone.start
        for (b in merged) {
            if (cursor < b.start) unblocked.add(AngularInterval(cursor, b.start))
            cursor = maxOf(cursor, b.end)
        }
        if (cursor < cone.end) unblocked.add(AngularInterval(cursor, cone.end))
        return unblocked
    }

    // ─────────────────────────────────────────────────────────────────────
    // Best boundary point (geometry-specific)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Finds the closest point on [geometry]'s boundary within the given
     * unblocked angular intervals.
     */
    private fun bestBoundaryPoint(
        boat: LatLng,
        geometry: MarkerGeometry,
        unblocked: List<AngularInterval>
    ): LatLng? {
        if (unblocked.isEmpty()) return null
        return when (geometry) {
            is MarkerGeometry.Pin -> {
                // Pin only matches if its bearing falls within an unblocked interval
                val bearingToPin = SpatialOperations.initialBearing(boat, geometry.position)
                    .let { (it % 360 + 360) % 360 }
                val isUnblocked = unblocked.any { bearingToPin in it.start..it.end }
                if (isUnblocked) geometry.position else null
            }
            is MarkerGeometry.Circle -> {
                var best: LatLng? = null
                var bestDist = Double.MAX_VALUE
                val bearingToCenter = SpatialOperations.initialBearing(boat, geometry.center)
                    .let { (it % 360 + 360) % 360 }
                for (interval in unblocked) {
                    val clamped = bearingToCenter.coerceIn(interval.start, interval.end)
                    val pt = circlePointAtBearing(boat, geometry.center, geometry.radiusM, clamped)
                    if (pt != null) {
                        val d = SpatialOperations.haversine(boat, pt)
                        if (d < bestDist) { best = pt; bestDist = d }
                    }
                }
                best
            }
            is MarkerGeometry.Corridor -> {
                val halfW = geometry.widthM / 2.0
                val segBearing = SpatialOperations.initialBearing(geometry.p1, geometry.p2)
                val perpB = (segBearing + 90.0) % 360.0
                var best: LatLng? = null
                var bestDist = Double.MAX_VALUE

                Log.d("WIA", "    corridor bestBP: unblocked=${unblocked.size} halfW=$halfW")
                for (interval in unblocked) {
                    // Test end-cap P1 (same ray-circle pattern as Circle)
                    val bToP1 = SpatialOperations.initialBearing(boat, geometry.p1)
                        .let { (it % 360 + 360) % 360 }
                    val clampedP1 = bToP1.coerceIn(interval.start, interval.end)
                    val ptP1 = circlePointAtBearing(boat, geometry.p1, halfW, clampedP1)
                    Log.d("WIA", "    P1: bToP1=$bToP1 clamped=$clampedP1 pt=${ptP1 != null} dP1=${SpatialOperations.haversine(boat, geometry.p1)}")
                    if (ptP1 != null) {
                        val d = SpatialOperations.haversine(boat, ptP1)
                        Log.d("WIA", "    P1 dist=$d")
                        if (d < bestDist) { best = ptP1; bestDist = d }
                    }
                    // Test end-cap P2
                    val bToP2 = SpatialOperations.initialBearing(boat, geometry.p2)
                        .let { (it % 360 + 360) % 360 }
                    val clampedP2 = bToP2.coerceIn(interval.start, interval.end)
                    val ptP2 = circlePointAtBearing(boat, geometry.p2, halfW, clampedP2)
                    Log.d("WIA", "    P2: bToP2=$bToP2 clamped=$clampedP2 pt=${ptP2 != null} dP2=${SpatialOperations.haversine(boat, geometry.p2)}")
                    if (ptP2 != null) {
                        val d = SpatialOperations.haversine(boat, ptP2)
                        Log.d("WIA", "    P2 dist=$d")
                        if (d < bestDist) { best = ptP2; bestDist = d }
                    }
                    // Test edge lines: sample N bearings across the interval
                    val edgeSamples = 5
                    for (si in 0 until edgeSamples) {
                        val t = si.toDouble() / (edgeSamples - 1)
                        val sampleBearing = interval.start + (interval.end - interval.start) * t
                        val ptEdge = corridorEdgePointAtBearing(
                            boat, geometry.p1, geometry.p2, halfW, sampleBearing)
                        if (ptEdge != null) {
                            val d = SpatialOperations.haversine(boat, ptEdge)
                            if (d < bestDist) { best = ptEdge; bestDist = d }
                        }
                    }
                    Log.d("WIA", "    edge samples=$edgeSamples bestDist=$bestDist")
                }
                Log.d("WIA", "    corridor result: bestDist=$bestDist")
                best
            }
        }
    }
}
