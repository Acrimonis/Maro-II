package ykws.android.maro.spatial

import ykws.android.maro.data.model.LatLng
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
        spatialIndex: CoastlineSpatialIndex,
        config: ProximityConfig
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
        val range = proximityRange(marker, config)

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
        val dist = SpatialOperations.haversine(boat, unblocked)
        return if (dist <= range)
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
        spatialIndex: CoastlineSpatialIndex,
        config: ProximityConfig
    ): WhereAmIResult {
        if (markers.isEmpty()) return WhereAmIResult(emptyList())

        // ── 1. BBox pre-filter (1 km fence) + resolve ──
        val results = mutableListOf<WhereAmIMatch>()
        for (marker in markers) {
            if (!boatInExpandedBbox(boat, marker, MAX_SEARCH_RADIUS_M)) continue
            val match = resolveMatch(boat, marker, spatialIndex, config)
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
    // Land-blocking engine
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
     * Sampling:
     * - **Pin**: the pin position itself (1 candidate)
     * - **Circle**: 36 points on the boundary at 10° steps
     * - **Corridor**: ~20 evenly-spaced points along the centreline
     */
    fun closestUnblockedPoint(
        boat: LatLng,
        marker: UserMarker,
        spatialIndex: CoastlineSpatialIndex
    ): LatLng? {
        val candidates = sampleGeometry(marker.geometry)
        var best: LatLng? = null
        var bestDist = Double.MAX_VALUE

        for (candidate in candidates) {
            if (!spatialIndex.segmentIntersectsLand(boat, candidate)) {
                val d = SpatialOperations.haversine(boat, candidate)
                if (d < bestDist) {
                    best = candidate
                    bestDist = d
                }
            }
        }

        return best
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
    private fun zoneCenterOf(geometry: MarkerGeometry): LatLng = when (geometry) {
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

    /**
     * Computes the proximity range for [marker].
     * User override takes priority; otherwise formula from [config].
     * No cap — the 1 km fence is applied in [resolveAllMarkers] BBox only.
     */
    private fun proximityRange(marker: UserMarker, config: ProximityConfig): Double {
        marker.proximityOverrideM?.let { return it }
        return when (val g = marker.geometry) {
            is MarkerGeometry.Pin -> config.pinM
            is MarkerGeometry.Circle -> g.radiusM * config.zoneMultiplier
            is MarkerGeometry.Corridor -> g.widthM * config.zoneMultiplier
        }
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

    /** Depth-first, children-before-parent traversal. Siblings sorted by size asc. */
    private fun depthFirstLeavesFirst(nodes: List<WhereAmIMatch>): List<WhereAmIMatch> {
        val result = mutableListOf<WhereAmIMatch>()
        for (node in nodes.sortedBy { sizeOf(it) }) {
            if (node is WhereAmIMatch.ZoneMatch && node.children.isNotEmpty()) {
                result.addAll(depthFirstLeavesFirst(node.children))
            }
            result.add(node)
        }
        return result
    }

    /**
     * Size metric for sorting. Smaller = more specific.
     * ZoneMatch: zone spatial extent (radius/width). ProximityMatch: sea distance.
     */
    private fun sizeOf(match: WhereAmIMatch): Double = when (match) {
        is WhereAmIMatch.ZoneMatch -> match.zoneSizeM
        is WhereAmIMatch.ProximityMatch -> match.seaDistanceM
    }

    // ─────────────────────────────────────────────────────────────────────
    // Geometry sampling
    // ─────────────────────────────────────────────────────────────────────

    private fun sampleGeometry(geometry: MarkerGeometry): List<LatLng> = when (geometry) {
        is MarkerGeometry.Pin -> listOf(geometry.position)
        is MarkerGeometry.Circle -> sampleCircle(geometry.center, geometry.radiusM)
        is MarkerGeometry.Corridor -> sampleCorridor(geometry.p1, geometry.p2)
    }

    /** 36 boundary points at 10° steps. */
    private fun sampleCircle(center: LatLng, radiusM: Double): List<LatLng> =
        (0..350 step 10).map { angleDeg ->
            SpatialOperations.pointAlongBearing(
                center.latitude, center.longitude, angleDeg.toDouble(), radiusM
            )
        }

    /** ~20 evenly-spaced points along the centreline p1→p2. */
    private fun sampleCorridor(p1: LatLng, p2: LatLng): List<LatLng> {
        val dist = SpatialOperations.haversine(p1, p2)
        val bearing = SpatialOperations.initialBearing(p1, p2)
        val numSamples = 20
        val step = dist / (numSamples - 1)
        return (0 until numSamples).map { i ->
            SpatialOperations.pointAlongBearing(p1.latitude, p1.longitude, bearing, step * i)
        }
    }
}
