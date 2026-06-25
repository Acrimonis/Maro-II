package ykws.android.maro.spatial

import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.CoastlineData
import ykws.android.maro.data.model.CoastlinePoint
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.MarkerGeometry
import ykws.android.maro.data.model.markers.UserMarker
import kotlin.math.*

// ─────────────────────────────────────────────────────────────────────────────
// Match result sealed hierarchy
// ─────────────────────────────────────────────────────────────────────────────

/** Outcome of a single-marker match resolution. */
sealed class MatchResult {

    /**
     * Boat is geometrically inside the marker's zone — purely geometric check,
     * no land test. Pins never produce a [ZoneMatch] (they have no zone).
     *
     * @property marker    The matched marker.
     * @property distanceM Distance from boat to the closest geometry point (metres),
     *                     or `null` if not computed.
     * @property children  More-precise results spatially contained within this zone
     *                     (populated during tiered assembly).
     */
    data class ZoneMatch(
        val marker: UserMarker,
        val distanceM: Double? = null,
        val children: List<MatchResult> = emptyList()
    ) : MatchResult()

    /**
     * Boat is outside the marker's zone but within the derived proximity range
     * of the closest unblocked boundary point (sea path clear of land).
     *
     * @property marker    The matched marker.
     * @property distanceM Sea-path distance from boat to the closest unblocked
     *                     boundary point (metres).
     */
    data class ProximityMatch(
        val marker: UserMarker,
        val distanceM: Double
    ) : MatchResult()

    /** Boat is too far away or land blocks all sea paths to the marker geometry. */
    data object NoMatch : MatchResult()
}

/**
 * Tiered, sorted, and spatially nested collection of match results.
 *
 * @property matches Top-level results after sorting by precision and nesting
 *                  by spatial containment. Results that are nested as children
 *                  of a parent zone do not appear at the top level.
 */
data class TieredMatchResult(val matches: List<MatchResult>)

// ─────────────────────────────────────────────────────────────────────────────
// Proximity configuration
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Tunables that control proximity-range computation for user-marker matching.
 *
 * Defaults mirror [AppConfig.markerProximityPinM] and
 * [AppConfig.markerProximityZoneMultiplier]; construct from those values at the
 * call site to stay in sync with user preferences.
 *
 * @property pinM           Proximity range (metres) for Pin-type markers.
 * @property zoneMultiplier Multiplier applied to a zone geometry's native
 *                          dimension (radius for Circle, width for Corridor)
 *                          to derive its proximity range.
 */
data class ProximityConfig(
    val pinM: Double = 200.0,
    val zoneMultiplier: Double = 3.0
)

// ─────────────────────────────────────────────────────────────────────────────
// MarkerMatcher object
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Land-blocking engine and on-demand match resolver for user-defined markers.
 *
 * Tests whether a straight-line segment from boat to a marker boundary point
 * crosses land (mainland or island coastline). Used by the on-demand
 * "where am I?" match resolution to compute sea-distance-gated proximity.
 *
 * All functions are stateless; coastline data is passed in explicitly.
 */
object MarkerMatcher {

    /** Intersection within this distance (metres) of a coastline vertex is
     *  considered grazing and ignored. */
    private const val GRAZING_TOLERANCE_M = 10.0

    /** Distance threshold (metres) at which the boat is considered "at" the
     *  marker — skip the expensive land-blocking search. */
    private const val AT_MARKER_THRESHOLD_M = 1.0

    // ─────────────────────────────────────────────────────────────────────
    // Public API — Phase C: Match resolution
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Resolves a single marker against the current boat position.
     *
     * Algorithm (§3.3, §6.2):
     * 1. Zone check (purely geometric, no land test): if the boat is inside the
     *    marker geometry → [MatchResult.ZoneMatch].
     * 2. Compute proximity range (override or formula from [config]).
     * 3. Boat at marker (≤ 1 m) → skip land check, return
     *    [MatchResult.ProximityMatch] if within range, else [MatchResult.NoMatch].
     * 4. Find the closest unblocked boundary point via [closestUnblockedPoint].
     * 5. If the sea-path distance ≤ range → [MatchResult.ProximityMatch],
     *    else [MatchResult.NoMatch].
     *
     * @param boat   Current boat position.
     * @param marker The user marker to test.
     * @param coastline Coastline data for land-blocking tests.
     * @param config Proximity-range tunables.
     * @return [MatchResult.ZoneMatch], [MatchResult.ProximityMatch], or
     *         [MatchResult.NoMatch].
     */
    fun resolveMatch(
        boat: LatLng,
        marker: UserMarker,
        coastline: CoastlineData,
        config: ProximityConfig
    ): MatchResult {
        // ── 1. Zone check (purely geometric, no land test) ──
        if (isInsideGeometry(boat, marker.geometry)) {
            val dist = distanceToClosestGeometryPoint(boat, marker.geometry)
            return MatchResult.ZoneMatch(marker, dist)
        }

        // ── 2. Compute proximity range ──
        val range = proximityRange(marker, config)

        // ── 3. Boat at marker → skip land check, always match if within range ──
        val directDist = distanceToClosestGeometryPoint(boat, marker.geometry)
        if (directDist <= AT_MARKER_THRESHOLD_M) {
            return if (directDist <= range)
                MatchResult.ProximityMatch(marker, directDist)
            else
                MatchResult.NoMatch
        }

        // ── 4. Find closest unblocked boundary point ──
        val unblocked = closestUnblockedPoint(boat, marker, coastline)
            ?: return MatchResult.NoMatch

        // ── 5. D = sea-path distance ≤ range? ──
        val dist = SpatialOperations.haversine(boat, unblocked)
        return if (dist <= range)
            MatchResult.ProximityMatch(marker, dist)
        else
            MatchResult.NoMatch
    }

    /**
     * Resolves all [markers] against the boat position and returns a tiered,
     * sorted, spatially nested result set.
     *
     * Algorithm (§6.4):
     * 1. BBox pre-filter: skip markers whose expanded bounding box does not
     *    contain the boat (avoids expensive [closestUnblockedPoint] calls).
     * 2. Call [resolveMatch] for each surviving marker; discard NoMatch.
     * 3. Sort by precision: Pin (distance asc) → Circle (radius asc, distance) →
     *    Corridor (width asc, length asc, distance).
     * 4. Nest by spatial containment: if a more-precise result lies inside a
     *    less-precise zone's geometry, nest it as a child.
     *
     * @param boat      Current boat position.
     * @param markers   All user markers to test.
     * @param coastline Coastline data for land-blocking tests.
     * @param config    Proximity-range tunables.
     * @return [TieredMatchResult] with sorted, nested matches (may be empty).
     */
    fun resolveAllMarkers(
        boat: LatLng,
        markers: List<UserMarker>,
        coastline: CoastlineData,
        config: ProximityConfig
    ): TieredMatchResult {
        if (markers.isEmpty()) return TieredMatchResult(emptyList())

        // ── 1. BBox pre-filter + resolve ──
        val results = mutableListOf<MatchResult>()
        for (marker in markers) {
            // Compute range for bbox expansion
            val range = proximityRange(marker, config)
            if (!boatInExpandedBbox(boat, marker, range)) continue
            val match = resolveMatch(boat, marker, coastline, config)
            if (match !is MatchResult.NoMatch) {
                results.add(match)
            }
        }

        if (results.isEmpty()) return TieredMatchResult(emptyList())

        // ── 2. Sort by precision ──
        val sorted = results.sortedWith(precisionComparator)

        // ── 3. Nest by spatial containment (§6.4) ──
        // Outer loop: iterate from least precise (end) to most precise (start).
        // For each outer result, check if any more-precise result is spatially
        // inside it; if so, move the more-precise result into outer's children.
        val nested = sorted.toMutableList()
        for (outerIdx in nested.indices.reversed()) {
            val outer = nested[outerIdx]
            // Only ZoneMatch can contain others
            if (outer !is MatchResult.ZoneMatch) continue

            val innerChildren = mutableListOf<MatchResult>()
            val remaining = mutableListOf<MatchResult>()

            for (innerIdx in nested.indices) {
                if (innerIdx == outerIdx) {
                    remaining.add(nested[innerIdx])
                    continue
                }
                val inner = nested[innerIdx]
                if (isMatchInsideZone(inner, outer.marker.geometry)) {
                    innerChildren.add(inner)
                } else {
                    remaining.add(inner)
                }
            }

            if (innerChildren.isNotEmpty()) {
                // Replace outer with a copy that has children; update the list
                nested.clear()
                nested.addAll(remaining)
                // Find outer again in remaining (it was added)
                val outerPos = nested.indexOfFirst { it === outer }
                if (outerPos >= 0) {
                    nested[outerPos] = outer.copy(children = innerChildren)
                }
            }
        }

        return TieredMatchResult(nested)
    }

    // ─────────────────────────────────────────────────────────────────────
    // Phase B: Land-blocking engine (public API)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Tests whether the segment **A→B** crosses any land edge in [coastline].
     *
     * Iterates every edge of the mainland and every island polyline. An
     * intersection that falls within [GRAZING_TOLERANCE_M] of either endpoint
     * of the coastline edge is treated as a grazing touch (peninsula tip) and
     * ignored.
     *
     * @return `true` if at least one non-grazing intersection exists (land
     *         blocks the segment), `false` otherwise (clear sea path).
     */
    fun segmentIntersectsLand(
        a: LatLng,
        b: LatLng,
        coastline: CoastlineData
    ): Boolean {
        if (segmentIntersectsPointList(a, b, coastline.mainland.points)) return true
        for (island in coastline.islands) {
            if (segmentIntersectsPointList(a, b, island.points)) return true
        }
        return false
    }

    /**
     * Finds the closest point on [marker]'s geometry boundary that has a
     * clear sea line-of-sight to [boat] — i.e. the segment boat→candidate
     * does not intersect land.
     *
     * Sampling strategy by geometry type:
     * - **Pin**: the pin position itself (1 candidate).
     * - **Circle**: 36 points on the circle boundary at 10° steps.
     * - **Corridor**: ~20 evenly-spaced points along the centreline.
     *
     * @return The closest unblocked boundary point, or `null` if every
     *         candidate is land-blocked.
     */
    fun closestUnblockedPoint(
        boat: LatLng,
        marker: UserMarker,
        coastline: CoastlineData
    ): LatLng? {
        val candidates = sampleGeometry(marker.geometry)
        var best: LatLng? = null
        var bestDist = Double.MAX_VALUE

        for (candidate in candidates) {
            if (!segmentIntersectsLand(boat, candidate, coastline)) {
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

    /**
     * Tests whether [boat] is geometrically inside [geometry] (§6.3).
     *
     * | Geometry  | Boat is inside if                                            |
     * |-----------|--------------------------------------------------------------|
     * | Pin       | Never (radius = 0)                                           |
     * | Circle    | `distanceM(boat, center) ≤ radiusM`                          |
     * | Corridor  | `distanceToSegment(boat, p1, p2) ≤ widthM/2` OR              |
     * |           | `distanceM(boat, p1) ≤ widthM/2` OR                           |
     * |           | `distanceM(boat, p2) ≤ widthM/2` (rounded end caps)          |
     */
    private fun isInsideGeometry(boat: LatLng, geometry: MarkerGeometry): Boolean {
        return when (geometry) {
            is MarkerGeometry.Pin -> false // pin has no zone
            is MarkerGeometry.Circle -> {
                SpatialOperations.haversine(boat, geometry.center) <= geometry.radiusM
            }
            is MarkerGeometry.Corridor -> {
                val halfW = geometry.widthM / 2.0
                SpatialOperations.pointToSegmentDistance(boat, geometry.p1, geometry.p2) <= halfW ||
                SpatialOperations.haversine(boat, geometry.p1) <= halfW ||
                SpatialOperations.haversine(boat, geometry.p2) <= halfW
            }
        }
    }

    /**
     * Distance from [boat] to the closest point on [geometry].
     *
     * | Geometry  | Closest point                     |
     * |-----------|-----------------------------------|
     * | Pin       | The pin position                  |
     * | Circle    | The circle centre                 |
     * | Corridor  | Projection onto centreline, capped to segment + endpoints |
     */
    private fun distanceToClosestGeometryPoint(
        boat: LatLng,
        geometry: MarkerGeometry
    ): Double {
        return when (geometry) {
            is MarkerGeometry.Pin -> SpatialOperations.haversine(boat, geometry.position)
            is MarkerGeometry.Circle -> SpatialOperations.haversine(boat, geometry.center)
            is MarkerGeometry.Corridor -> {
                val dSeg = SpatialOperations.pointToSegmentDistance(boat, geometry.p1, geometry.p2)
                val dP1 = SpatialOperations.haversine(boat, geometry.p1)
                val dP2 = SpatialOperations.haversine(boat, geometry.p2)
                minOf(dSeg, dP1, dP2)
            }
        }
    }

    /**
     * Tests whether a match result's marker position is inside [zoneGeometry].
     * Used for spatial-containment nesting.
     *
     * - Pin: its position is inside the zone geometry
     * - Circle: its centre is inside the zone geometry
     * - Corridor: its midpoint (average of p1/p2) is inside the zone geometry
     */
    /**
     * Extracts the [UserMarker] from a non-[MatchResult.NoMatch] result.
     * Safe to call only after NoMatch filtering.
     */
    private fun markerOf(match: MatchResult): UserMarker = when (match) {
        is MatchResult.ZoneMatch -> match.marker
        is MatchResult.ProximityMatch -> match.marker
        is MatchResult.NoMatch -> throw IllegalStateException("NoMatch has no marker")
    }

    /**
     * Tests whether a match result's marker position is inside [zoneGeometry].
     * Used for spatial-containment nesting.
     *
     * - Pin: its position is inside the zone geometry
     * - Circle: its centre is inside the zone geometry
     * - Corridor: its midpoint (average of p1/p2) is inside the zone geometry
     */
    private fun isMatchInsideZone(
        match: MatchResult,
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
     * Computes the proximity range for [marker] (§5.1).
     *
     * If [UserMarker.proximityOverrideM] is non-null it is used directly;
     * otherwise the formula from [config] applies:
     * - Pin → [ProximityConfig.pinM]
     * - Circle → `radiusM × [ProximityConfig.zoneMultiplier]`
     * - Corridor → `widthM × [ProximityConfig.zoneMultiplier]`
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

    /**
     * True if [boat] falls within [marker]'s axis-aligned bounding box
     * expanded by [rangeM] in all directions (metres → degrees).
     *
     * Cheap 4-float-comparison gate before the expensive [closestUnblockedPoint]
     * search.  A false positive here only costs one extra [resolveMatch] call;
     * a false negative would skip a valid match, so the expansion must cover
     * the worst-case proximity range.
     */
    private fun boatInExpandedBbox(
        boat: LatLng,
        marker: UserMarker,
        rangeM: Double
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
    // Precision comparator
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Comparator that orders [MatchResult]s by precision (§3.2, §6.4):
     *
     * 1. Pin: distance asc
     * 2. Circle: radius asc, then distance asc
     * 3. Corridor: width asc, then length asc, then distance asc
     * 4. Within same geometry type: [ZoneMatch] before [ProximityMatch]
     */
    private val precisionComparator = Comparator<MatchResult> { a, b ->
        val typeA = geometryTypeRank(a)
        val typeB = geometryTypeRank(b)
        if (typeA != typeB) return@Comparator typeA.compareTo(typeB)

        // Same geometry type — compare by precision metric
        when (val ga = markerOf(a).geometry) {
            is MarkerGeometry.Pin -> {
                val distA = (a as? MatchResult.ZoneMatch)?.distanceM
                    ?: (a as? MatchResult.ProximityMatch)?.distanceM ?: Double.MAX_VALUE
                val distB = (b as? MatchResult.ZoneMatch)?.distanceM
                    ?: (b as? MatchResult.ProximityMatch)?.distanceM ?: Double.MAX_VALUE
                distA.compareTo(distB)
            }
            is MarkerGeometry.Circle -> {
                val gb = markerOf(b).geometry as MarkerGeometry.Circle
                val rCmp = (ga as MarkerGeometry.Circle).radiusM.compareTo(gb.radiusM)
                if (rCmp != 0) return@Comparator rCmp
                distanceFromResult(a).compareTo(distanceFromResult(b))
            }
            is MarkerGeometry.Corridor -> {
                val gb = markerOf(b).geometry as MarkerGeometry.Corridor
                val wCmp = (ga as MarkerGeometry.Corridor).widthM.compareTo(gb.widthM)
                if (wCmp != 0) return@Comparator wCmp
                val lenA = SpatialOperations.haversine(
                    (ga as MarkerGeometry.Corridor).p1, (ga as MarkerGeometry.Corridor).p2)
                val lenB = SpatialOperations.haversine(gb.p1, gb.p2)
                val lCmp = lenA.compareTo(lenB)
                if (lCmp != 0) return@Comparator lCmp
                distanceFromResult(a).compareTo(distanceFromResult(b))
            }
        }
    }

    /** Ordinal for precision ordering: Pin=0, Circle=1, Corridor=2. */
    private fun geometryTypeRank(match: MatchResult): Int = when (markerOf(match).geometry) {
        is MarkerGeometry.Pin -> 0
        is MarkerGeometry.Circle -> 1
        is MarkerGeometry.Corridor -> 2
    }

    /** Extracts distanceM from any [MatchResult] (ZoneMatch or ProximityMatch). */
    private fun distanceFromResult(match: MatchResult): Double = when (match) {
        is MatchResult.ZoneMatch -> match.distanceM ?: 0.0
        is MatchResult.ProximityMatch -> match.distanceM
        is MatchResult.NoMatch -> Double.MAX_VALUE
    }

    // ─────────────────────────────────────────────────────────────────────
    // Geometry sampling
    // ─────────────────────────────────────────────────────────────────────

    private fun sampleGeometry(geometry: MarkerGeometry): List<LatLng> {
        return when (geometry) {
            is MarkerGeometry.Pin -> listOf(geometry.position)
            is MarkerGeometry.Circle -> sampleCircle(geometry.center, geometry.radiusM)
            is MarkerGeometry.Corridor -> sampleCorridor(geometry.p1, geometry.p2)
        }
    }

    /** 36 boundary points at 10° steps (0°, 10°, …, 350°). */
    private fun sampleCircle(center: LatLng, radiusM: Double): List<LatLng> {
        return (0..350 step 10).map { angleDeg ->
            SpatialOperations.pointAlongBearing(
                center.latitude, center.longitude,
                angleDeg.toDouble(), radiusM
            )
        }
    }

    /** ~20 evenly-spaced points along the centreline p1→p2 (includes endpoints). */
    private fun sampleCorridor(p1: LatLng, p2: LatLng): List<LatLng> {
        val dist = SpatialOperations.haversine(p1, p2)
        val bearing = SpatialOperations.initialBearing(p1, p2)
        val numSamples = 20
        val step = dist / (numSamples - 1)
        return (0 until numSamples).map { i ->
            SpatialOperations.pointAlongBearing(
                p1.latitude, p1.longitude,
                bearing, step * i
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Coastline edge iteration + grazing check
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Iterates edges of a single polyline (consecutive points) and tests
     * segment A→B against each. Applies grazing tolerance.
     */
    private fun segmentIntersectsPointList(
        a: LatLng,
        b: LatLng,
        points: List<CoastlinePoint>
    ): Boolean {
        for (i in 0 until points.size - 1) {
            val cp1 = points[i]
            // Skip terminal points (no outgoing edge)
            if (cp1.isTerminal) continue
            val cp2 = points[i + 1]

            // CoastlinePoint(Float) → LatLng(Double) on the fly
            val q1 = LatLng(cp1.lat.toDouble(), cp1.lon.toDouble())
            val q2 = LatLng(cp2.lat.toDouble(), cp2.lon.toDouble())

            if (SpatialOperations.segmentsIntersect(a, b, q1, q2)) {
                // Compute intersection point for grazing-tolerance check
                val ip = intersectionPoint(a, b, q1, q2) ?: continue
                val distToQ1 = SpatialOperations.haversine(ip, q1)
                val distToQ2 = SpatialOperations.haversine(ip, q2)
                // Non-grazing = intersection is > 10 m from BOTH vertices
                if (distToQ1 > GRAZING_TOLERANCE_M && distToQ2 > GRAZING_TOLERANCE_M) {
                    return true // land blocks
                }
                // else: grazing — continue checking other edges
            }
        }
        return false
    }

    /**
     * Computes the intersection point of segments **a→b** and **q1→q2**.
     *
     * Uses the same local planar projection as [SpatialOperations.segmentsIntersect].
     * Returns `null` if the segments are parallel (or the cross product is
     * vanishingly small).
     */
    private fun intersectionPoint(
        a: LatLng, b: LatLng,
        q1: LatLng, q2: LatLng
    ): LatLng? {
        val midLat = (a.latitude + b.latitude + q1.latitude + q2.latitude) / 4.0
        val mPerDegLat = SpatialOperations.EARTH_RADIUS_M * PI / 180.0
        val mPerDegLon = mPerDegLat * cos(Math.toRadians(midLat))

        fun toProj(p: LatLng) = Pair(p.longitude * mPerDegLon, p.latitude * mPerDegLat)

        val (ax, ay) = toProj(a)
        val (bx, by) = toProj(b)
        val (q1x, q1y) = toProj(q1)
        val (q2x, q2y) = toProj(q2)

        val rx = bx - ax
        val ry = by - ay
        val sx = q2x - q1x
        val sy = q2y - q1y

        val crossRS = rx * sy - ry * sx
        if (abs(crossRS) < 1e-12) return null

        val qpx = q1x - ax
        val qpy = q1y - ay

        val t = (qpx * sy - qpy * sx) / crossRS
        // val u = (qpx * ry - qpy * rx) / crossRS  // not needed for point

        val ix = ax + t * rx
        val iy = ay + t * ry

        return LatLng(iy / mPerDegLat, ix / mPerDegLon)
    }
}
