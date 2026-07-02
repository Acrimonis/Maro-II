package ykws.android.maro.spatial

import android.util.Log
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.MarkerGeometry
import ykws.android.maro.data.model.markers.MarkerOrigin
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
     * Boat is outside the marker's zone but has a clear sea line-of-sight
     * to the closest unblocked boundary point.  Pins use a proximity-distance
     * gate instead of a land check (see [resolveMatch]).
     */
    data class LineOfSightMatch(
        val marker: UserMarker,
        val seaDistanceM: Double,        // sea-path distance to closest unblocked boundary point
        val bearingDeg: Double           // bearing from boat to closest unblocked point (0-360)
    ) : WhereAmIMatch()
}

/** Flat result container — depth-first, leaves-first. */
data class WhereAmIResult(val allMatches: List<WhereAmIMatch>)

// ─────────────────────────────────────────────────────────────────────────────
// MarkerMatcher object
// ─────────────────────────────────────────────────────────────────────────────

object MarkerMatcher {

    /** Active debugger for visual segment testing — replace with [VisualWhereAmIDebugger]
     *  to capture line-of-sight segments for map rendering. */
    var debugger: WhereAmIDebugger = NoOpWhereAmIDebugger

    /** Intersection within this distance (metres) of a coastline vertex is
     *  considered grazing and ignored. */
    private const val GRAZING_TOLERANCE_M = 10.0

    /** Distance threshold (metres) at which the boat is considered "at" the
     *  marker — skip the expensive land-blocking search. */
    private const val AT_MARKER_THRESHOLD_M = 1.0

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
        debugger.beginCapture(marker.name)
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
            // Debug: capture all boundary sample segments for matched ZoneMatch
            if (AppConfig.markerDebugRaysEnabled) testDebugSamples(boat, marker, spatialIndex)
            if (AppConfig.markerDebugRaysEnabled) {
                closestBoundaryPoint(boat, marker.geometry)?.let { bp ->
                    debugger.onSegmentTested(boat, bp, false)
                }
            }
            return WhereAmIMatch.ZoneMatch(marker, zoneSize, dist, bearing)
        }

        // ── 2. Boat at marker → always match ──
        val directDist = distanceToClosestGeometryPoint(boat, marker.geometry)

        if (directDist <= AT_MARKER_THRESHOLD_M) {
            val center = zoneCenterOf(marker.geometry)
            debugger.onSegmentTested(boat, center, false)
            return WhereAmIMatch.LineOfSightMatch(marker, directDist, SpatialOperations.initialBearing(center, boat))
        }

        // ── 3. Pin markers: proximity-distance gate, no coastline check ──
        if (marker.geometry is MarkerGeometry.Pin) {
            val range = proximityRange(marker)
            if (directDist > range) return null
            debugger.onSegmentTested(boat, marker.geometry.position, false)
            return WhereAmIMatch.LineOfSightMatch(marker, directDist,
                SpatialOperations.initialBearing(marker.geometry.position, boat))
        }

        // ── 4. Range pre-filter: skip markers beyond their proximity zone ──
        val range = proximityRange(marker)
        val minBoundaryDist = when (marker.geometry) {
            is MarkerGeometry.Pin -> directDist  // unreachable — Pin handled above
            is MarkerGeometry.Circle ->
                (directDist - marker.geometry.radiusM).coerceAtLeast(0.0)
            is MarkerGeometry.Corridor -> {
                val halfW = marker.geometry.widthM / 2.0
                val dSeg = SpatialOperations.pointToSegmentDistance(boat, marker.geometry.p1, marker.geometry.p2)
                (dSeg - halfW).coerceAtLeast(0.0)
            }
        }
        if (minBoundaryDist > range) return null

        // ── 5. Find closest unblocked boundary point ──
        val unblocked = closestUnblockedPoint(boat, marker, spatialIndex)

        // Debug: capture all boundary sample segments
        if (AppConfig.markerDebugRaysEnabled) testDebugSamples(boat, marker, spatialIndex)

        if (unblocked == null) return null

        // ── 5. Unblocked point found → match (no distance gate) ──
        val dist = SpatialOperations.haversine(boat, unblocked)
        if (AppConfig.markerDebugRaysEnabled) debugger.onSegmentTested(boat, unblocked, false)
        Log.d("WIA", "  dist=${"%.0f".format(dist)} MATCH")
        return WhereAmIMatch.LineOfSightMatch(marker, dist, SpatialOperations.initialBearing(unblocked, boat))
    }

    /**
     * Resolves all [markers] against the boat position.
     *
     * Algorithm:
     * 1. Resolve each marker via [resolveMatch]; discard null
     * 2. Build spatial containment tree (large zones contain smaller ones)
     * 3. Depth-first, leaves-first traversal sorted by size asc at each level
     */
    fun resolveAllMarkers(
        boat: LatLng,
        markers: List<UserMarker>,
        spatialIndex: CoastlineSpatialIndex
    ): WhereAmIResult {
        if (markers.isEmpty()) return WhereAmIResult(emptyList())

        // ── 1. Resolve all markers ──
        val results = markers.mapNotNull { marker ->
            resolveMatch(boat, marker, spatialIndex)
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
        val display = depthFirstLeavesFirst(roots)

        return WhereAmIResult(display)
    }

    // ─────────────────────────────────────────────────────────────────────
    // Land-blocking engine — boundary sampling + segment intersection
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
     * Algorithm:
     * 1. Direct-line fast path: if the geometrically-closest boundary point
     *    has a clear sea line-of-sight, return it immediately.
     * 2. Sample candidate points on the zone boundary at evenly-spaced bearings.
     * 3. Test each with [segmentIntersectsLand]; return the closest clear one.
     * 4. If all blocked → null.
     */
    fun closestUnblockedPoint(
        boat: LatLng,
        marker: UserMarker,
        spatialIndex: CoastlineSpatialIndex
    ): LatLng? {
        // ── Direct-line fast path ──
        val closestGeom = closestGeometricBoundaryPoint(boat, marker.geometry)
        if (closestGeom != null) {
            if (!segmentIntersectsLand(boat, closestGeom, spatialIndex)) {
                return closestGeom
            }
        }

        // ── Sample boundary points ──
        val candidates = sampleBoundaryPoints(boat, marker.geometry)
        var best: LatLng? = null
        var bestDist = Double.MAX_VALUE
        var clearCount = 0
        
        for (c in candidates) {
            if (!segmentIntersectsLand(boat, c, spatialIndex)) {
                clearCount++
                val d = SpatialOperations.haversine(boat, c)
                if (d < bestDist) { best = c; bestDist = d }
            }
        }

        Log.d("WIA", "  ${marker.name}: samples=${candidates.size} clear=$clearCount")
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

    /** Closest point on [geometry] boundary to [boat], even when boat is inside.
     *  For debug visualization of ZoneMatch segments. */
    private fun closestBoundaryPoint(boat: LatLng, geometry: MarkerGeometry): LatLng? {
        return when (geometry) {
            is MarkerGeometry.Pin -> geometry.position
            is MarkerGeometry.Circle -> {
                val dist = SpatialOperations.haversine(boat, geometry.center)
                val bearing = SpatialOperations.initialBearing(boat, geometry.center)
                if (dist <= geometry.radiusM) {
                    // Inside: closest boundary point is away from center
                    SpatialOperations.pointAlongBearing(boat.latitude, boat.longitude, bearing, geometry.radiusM - dist)
                } else {
                    // Outside: closest boundary point is toward center
                    SpatialOperations.pointAlongBearing(boat.latitude, boat.longitude, bearing, dist - geometry.radiusM)
                }
            }
            is MarkerGeometry.Corridor -> {
                val halfW = geometry.widthM / 2.0
                val closestOnCL = SpatialOperations.projectPointOntoSegment(boat, geometry.p1, geometry.p2)
                val distToCL = SpatialOperations.pointToSegmentDistance(boat, geometry.p1, geometry.p2)
                if (distToCL <= halfW) {
                    // Inside: closest boundary point is perpendicular away from CL
                    val bearing = SpatialOperations.initialBearing(closestOnCL, boat)
                    SpatialOperations.pointAlongBearing(boat.latitude, boat.longitude, bearing, halfW - distToCL)
                } else {
                    // Outside: closest boundary point is perpendicular toward CL
                    val bearing = SpatialOperations.initialBearing(boat, closestOnCL)
                    SpatialOperations.pointAlongBearing(boat.latitude, boat.longitude, bearing, distToCL - halfW)
                }
            }
        }
    }

    /**
     * Generates candidate points on the zone boundary for land-blocking tests.
     *
     * Points are generated directly on the geometry boundary (via
     * [SpatialOperations.pointAlongBearing]) — never via ray-intersection from
     * the boat — so tangent samples at the visible-cone extremes are always
     * present.
     *
     * | Geometry  | Condition     | Samples | Spacing               |
     * |-----------|---------------|---------|-----------------------|
     * | Pin       | always        | 1       | N/A                   |
     * | Circle    | boat inside   | 16      | 22.5° (full 360°)     |
     * | Circle    | boat outside  | 16      | adaptive across the   |
     * |           |               |         | visible arc from      |
     * |           |               |         | centre perspective    |
     * | Corridor  | end-cap ×2    | 16 each | 22.5° (full 360°)     |
     * | Corridor  | edge line ×2  | 5 each  | 25% along length      |
     */
    private fun sampleBoundaryPoints(boat: LatLng, geometry: MarkerGeometry): List<LatLng> {
        return when (geometry) {
            is MarkerGeometry.Pin -> listOf(geometry.position)

            is MarkerGeometry.Circle -> {
                val dist = SpatialOperations.haversine(boat, geometry.center)
                val bearing = SpatialOperations.initialBearing(boat, geometry.center)
                val radiusM = geometry.radiusM
                val inside = dist <= radiusM
                // Visible arc half-width from the centre's perspective:
                //   inside  → 180° (full circle)
                //   outside → 90° − asin(r/d)  (right-triangle: boat–tangent–centre)
                val centerHalfArc = if (inside) 180.0
                    else 90.0 - Math.toDegrees(asin((radiusM / dist).coerceIn(-1.0, 1.0)))
                val centerToBoat = ((bearing + 180.0) % 360.0 + 360.0) % 360.0
                val n = 16
                val step = if (inside) 360.0 / n else (2.0 * centerHalfArc) / (n - 1)
                val startAngle = if (inside) 0.0 else centerToBoat - centerHalfArc
                val cLat = geometry.center.latitude
                val cLon = geometry.center.longitude
                (0 until n).map { i ->
                    val angleDeg = ((startAngle + i * step) % 360.0 + 360.0) % 360.0
                    SpatialOperations.pointAlongBearing(cLat, cLon, angleDeg, radiusM)
                }
            }

            is MarkerGeometry.Corridor -> {
                val halfW = geometry.widthM / 2.0
                val points = mutableListOf<LatLng>()
                // P1 end-cap: 16 points full circle (22.5° spacing) from centre
                val p1Lat = geometry.p1.latitude
                val p1Lon = geometry.p1.longitude
                for (i in 0 until 16) {
                    points.add(SpatialOperations.pointAlongBearing(p1Lat, p1Lon, i * 22.5, halfW))
                }
                // P2 end-cap: 16 points full circle
                val p2Lat = geometry.p2.latitude
                val p2Lon = geometry.p2.longitude
                for (i in 0 until 16) {
                    points.add(SpatialOperations.pointAlongBearing(p2Lat, p2Lon, i * 22.5, halfW))
                }
                // Edge lines: 5 samples per edge (25% spacing)
                val segBearing = SpatialOperations.initialBearing(geometry.p1, geometry.p2)
                for (sign in listOf(1.0, -1.0)) {
                    val perpBearing = ((segBearing + sign * 90.0) % 360.0 + 360.0) % 360.0
                    for (t in listOf(0.0, 0.25, 0.5, 0.75, 1.0)) {
                        val midLat = geometry.p1.latitude + (geometry.p2.latitude - geometry.p1.latitude) * t
                        val midLon = geometry.p1.longitude + (geometry.p2.longitude - geometry.p1.longitude) * t
                        points.add(SpatialOperations.pointAlongBearing(midLat, midLon, perpBearing, halfW))
                    }
                }
                points
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
        is WhereAmIMatch.LineOfSightMatch -> match.marker
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

    /** Proximity range from marker — reads the stored override set by the creation wizard.
     *  Falls back to AppConfig.boatMarkerAutoMarkerProximityM for IDLE_AUTO markers
     *  that lack a stored override (e.g., markers created before the wizard stored it). */
    private fun proximityRange(marker: UserMarker): Double =
        marker.proximityOverrideM
            ?: if (marker.origin == MarkerOrigin.IDLE_AUTO) AppConfig.boatMarkerAutoMarkerProximityM
               else Double.MAX_VALUE

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
     * ZoneMatch:         0.0 + typeWeight × (distanceToCenterM / zoneSizeM)
     * LineOfSightMatch:  1.0 + typeWeight × (seaDistanceM / proximityRange)
     *
     * typeWeight: Pin=0.5 / Circle=1.0 / Corridor=2.0 (from maro.properties)
     *   ZoneMatch always beats LineOfSightMatch (0.0–2.0 < 1.0–3.0).
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
            is WhereAmIMatch.LineOfSightMatch -> {
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
    // Debug — sample all boundary points for visual ray rendering
    // ─────────────────────────────────────────────────────────────────────

    private fun testDebugSamples(boat: LatLng, marker: UserMarker, spatialIndex: CoastlineSpatialIndex) {
        val candidates = sampleBoundaryPoints(boat, marker.geometry)
        for (c in candidates) {
            val blocked = segmentIntersectsLand(boat, c, spatialIndex)
            debugger.onSegmentTested(boat, c, blocked)
        }
    }
}
