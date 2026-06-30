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
            return WhereAmIMatch.ZoneMatch(marker, zoneSize, dist, bearing)
        }

        // ── 2. Compute proximity range ──
        val range = proximityRange(marker)

        // ── 3. Boat at marker → skip land check ──
        val directDist = distanceToClosestGeometryPoint(boat, marker.geometry)

        // ── Early exit: boat clearly too far → skip expensive land checks ──
        if (directDist > range + 200.0) return null

        // Debug: capture segments for all markers within range
        testDebugSamples(boat, marker, spatialIndex)

        if (directDist <= AT_MARKER_THRESHOLD_M) {
            return if (directDist <= range) {
                val center = zoneCenterOf(marker.geometry)
                WhereAmIMatch.ProximityMatch(marker, directDist, SpatialOperations.initialBearing(center, boat))
            } else null
        }

        // ── 4. Pin markers: proximity is enough, no coastline check ──
        if (marker.geometry is MarkerGeometry.Pin) {
            return WhereAmIMatch.ProximityMatch(marker, directDist,
                SpatialOperations.initialBearing(marker.geometry.position, boat))
        }

        // ── 5. Find closest unblocked boundary point (Circle/Corridor) ──
        val unblocked = closestUnblockedPoint(boat, marker, spatialIndex)
            ?: return null

        // ── 6. Unblocked point found → match (zone gate already ensured proximity) ──
        val dist = SpatialOperations.haversine(boat, unblocked)
        Log.d("WIA", "  range=${"%.0f".format(range)} dist=${"%.0f".format(dist)} MATCH")
        return WhereAmIMatch.ProximityMatch(marker, dist, SpatialOperations.initialBearing(unblocked, boat))
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

        // ── 1b. Post-filter: line-of-sight for ALL matches ──
        val verified = results.mapNotNull { match ->
            if (hasLineOfSight(boat, markerOf(match), spatialIndex)) match else null
        }

        if (verified.isEmpty()) return WhereAmIResult(emptyList())

        // ── 2. Build containment tree ──
        val nestedIds = mutableSetOf<String>()
        val zoneMatches = verified.filterIsInstance<WhereAmIMatch.ZoneMatch>().toMutableList()

        for (i in zoneMatches.indices) {
            val outer = zoneMatches[i]
            val tempChildren = mutableListOf<WhereAmIMatch>()
            for (other in verified) {
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
        val finalResults = verified.map { match ->
            if (match is WhereAmIMatch.ZoneMatch) {
                zoneMatches.find { it.marker.id == match.marker.id } ?: match
            } else match
        }

        val roots = finalResults.filter { markerOf(it).id !in nestedIds }

        // ── 3. Depth-first, leaves-first traversal ──
        val display = depthFirstLeavesFirst(roots).take(MAX_RESULTS)

        return WhereAmIResult(display)
    }

    /** Two-tier check: points first (cheap), then zone boundary. */
    private fun hasLineOfSight(
        boat: LatLng, marker: UserMarker, spatialIndex: CoastlineSpatialIndex
    ): Boolean {
        // Tier 1: check point(s) — cheap, O(1-2) segmentIntersectsLand calls
        if (pointsVisible(boat, marker, spatialIndex)) return true

        // Tier 2: check zone boundary — sampling approach
        return closestUnblockedPoint(boat, marker, spatialIndex) != null
    }

    /** Direct line-of-sight to the marker's defining point(s). */
    private fun pointsVisible(
        boat: LatLng, marker: UserMarker, spatialIndex: CoastlineSpatialIndex
    ): Boolean = when (marker.geometry) {
        is MarkerGeometry.Pin ->
            !spatialIndex.segmentIntersectsLand(boat, marker.geometry.position)
        is MarkerGeometry.Circle ->
            !spatialIndex.segmentIntersectsLand(boat, marker.geometry.center)
        is MarkerGeometry.Corridor ->
            !spatialIndex.segmentIntersectsLand(boat, marker.geometry.p1) ||
            !spatialIndex.segmentIntersectsLand(boat, marker.geometry.p2)
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

    /** Steps along boat→target in 20 m increments, checking [CoastlineSpatialIndex.isWater].
     *  Returns true if ANY step is on land (catches coastline near the ray, closing joint gaps). */
    private fun segmentIntersectsLandStepped(
        boat: LatLng, target: LatLng, spatialIndex: CoastlineSpatialIndex, stepM: Double = 20.0
    ): Boolean {
        val dist = SpatialOperations.haversine(boat, target)
        val brg = SpatialOperations.initialBearing(boat, target)
        var d = stepM
        while (d < dist) {
            val pt = SpatialOperations.pointAlongBearing(boat.latitude, boat.longitude, brg, d)
            if (!spatialIndex.isWater(pt.latitude, pt.longitude)) return true
            d += stepM
        }
        // Also check the target point itself
        return !spatialIndex.isWater(target.latitude, target.longitude)
    }

    /** Debug-only: tests all boundary samples for visual rendering, even for zone matches. */
    private fun testDebugSamples(boat: LatLng, marker: UserMarker, spatialIndex: CoastlineSpatialIndex) {
        val candidates = sampleBoundaryPoints(boat, marker.geometry)
        for (c in candidates) {
            val blocked = segmentIntersectsLandStepped(boat, c, spatialIndex)
            debugger.onSegmentTested(boat, c, blocked)
        }
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
            val blocked = segmentIntersectsLandStepped(boat, closestGeom, spatialIndex)
            if (!blocked) {
                return closestGeom
            }
        }

        // ── Sample boundary points ──
        val candidates = sampleBoundaryPoints(boat, marker.geometry)
        var best: LatLng? = null
        var bestDist = Double.MAX_VALUE
        var clearCount = 0
        
        for (c in candidates) {
            val blocked = segmentIntersectsLandStepped(boat, c, spatialIndex)
            if (!blocked) {
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

    /**
     * Generates candidate points on the zone boundary for land-blocking tests.
     *
     * | Geometry  | Condition     | Samples | Spacing               |
     * |-----------|---------------|---------|-----------------------|
     * | Pin       | always        | 1       | N/A                   |
     * | Circle    | boat inside   | 16      | 22.5°                 |
     * | Circle    | boat outside  | 16      | adaptive (~1-15°)     |
     * | Corridor  | end-cap ×2    | 8 each  | 45°                   |
     * | Corridor  | edge line ×2  | 5 each  | 25% along length      |
     */
    private fun sampleBoundaryPoints(boat: LatLng, geometry: MarkerGeometry): List<LatLng> {
        return when (geometry) {
            is MarkerGeometry.Pin -> listOf(geometry.position)

            is MarkerGeometry.Circle -> {
                val dist = SpatialOperations.haversine(boat, geometry.center)
                val bearing = SpatialOperations.initialBearing(boat, geometry.center)
                val inside = dist <= geometry.radiusM
                val step = if (inside) 360.0 / 16.0 else {
                    val halfAngle = Math.toDegrees(asin((geometry.radiusM / dist).coerceIn(-1.0, 1.0)))
                    (2.0 * halfAngle) / 15.0
                }
                val startBearing = if (inside) 0.0 else bearing - (step * 7.5)
                (0 until 16).mapNotNull { i ->
                    val b = (startBearing + i * step).let { (it % 360 + 360) % 360 }
                    circlePointAtBearing(boat, geometry.center, geometry.radiusM, b)
                }
            }

            is MarkerGeometry.Corridor -> {
                val halfW = geometry.widthM / 2.0
                val points = mutableListOf<LatLng>()
                // P1 end-cap disc: 8 samples (45° spacing)
                for (i in 0 until 8) {
                    val b = i * 45.0
                    val pt = circlePointAtBearing(boat, geometry.p1, halfW, b)
                    if (pt != null) points.add(pt)
                }
                // P2 end-cap disc: 8 samples
                for (i in 0 until 8) {
                    val b = i * 45.0
                    val pt = circlePointAtBearing(boat, geometry.p2, halfW, b)
                    if (pt != null) points.add(pt)
                }
                // Edge lines: 5 samples per edge (25% spacing)
                val segBearing = SpatialOperations.initialBearing(geometry.p1, geometry.p2)
                for (sign in listOf(1.0, -1.0)) {
                    val perpBearing = (segBearing + sign * 90.0).let { (it % 360 + 360) % 360 }
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
    // Circle boundary point (ray-circle intersection, inlined)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * The point on the circle boundary where the ray from [boat] at [bearing]
     * first intersects. Returns null if the ray misses the circle.
     */
    private fun circlePointAtBearing(
        boat: LatLng, center: LatLng, radiusM: Double, bearing: Double
    ): LatLng? {
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

        if (disc < 0.0) return null
        val sqrtDisc = sqrt(disc)
        val t1 = (-b - sqrtDisc) / (2.0 * a)
        val t2 = (-b + sqrtDisc) / (2.0 * a)
        val t = when {
            t1 > 0.0 -> t1
            t2 > 0.0 -> t2
            else -> return null
        }
        return SpatialOperations.pointAlongBearing(boat.latitude, boat.longitude, bearing, t)
    }
}
