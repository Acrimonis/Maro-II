package ykws.android.maro.data.regulation

import ykws.android.maro.data.model.BoundingBox
import ykws.android.maro.data.model.LatLng
import kotlin.math.abs
import kotlin.math.cos

/**
 * Aggregates [RegulatedZone] instances from SHOM WFS and seed sources into a
 * single deduplicated, validated, sorted [RegulatedZoneSet].
 *
 * Processing pipeline:
 * 1. **Collect** — start with SHOM zones, append all seed zones
 * 2. **Deduplicate** — seed zones whose centroid falls within any SHOM zone's bounding
 *    box are discarded (SHOM attributes are authoritative)
 * 3. **Validate** — reject zones whose centroid falls outside the lookup [BoundingBox]
 * 4. **Sort** — by zone type ordinal, then approximate area descending
 * 5. **Build metadata** — [RegulationMetadata] with timestamp and source counts
 */
object RegulationAggregator {

    /**
     * Aggregate SHOM and seed zones into a single authoritative [RegulatedZoneSet].
     *
     * @param shomZones Zones fetched from the SHOM WFS (may be empty if fetch failed).
     * @param seedZones Hardcoded fallback zones guaranteed to cover known regulations.
     * @param bbox      Geographic bounding box; zones with centroids outside are rejected.
     * @param nowMs     Timestamp for [RegulationMetadata.fetchTimestampMs] (epoch millis).
     * @return A validated, deduplicated, sorted [RegulatedZoneSet] with metadata.
     */
    fun aggregate(
        shomZones: List<RegulatedZone>,
        seedZones: List<RegulatedZone>,
        bbox: BoundingBox,
        nowMs: Long = System.currentTimeMillis()
    ): RegulatedZoneSet {
        // ── Step a: Collect ─────────────────────────────────────────────────────
        // Start with all SHOM zones, then append all seed zones. Seeds that overlap
        // SHOM zones are discarded in the deduplication step.
        val collected = mutableListOf<RegulatedZone>()
        collected.addAll(shomZones)
        collected.addAll(seedZones)

        // ── Step b: Deduplicate ─────────────────────────────────────────────────
        // For each non-SHOM zone, check if its centroid falls within any SHOM zone's
        // bounding box. If yes, discard it (SHOM attributes are authoritative).
        val deduplicated = deduplicate(collected)

        // ── Step c: Validate ────────────────────────────────────────────────────
        // Reject any zone whose centroid falls outside the bounding box.
        val validated = deduplicated.filter { zone ->
            isInBbox(centroid(zone), bbox)
        }

        // ── Step d: Sort ────────────────────────────────────────────────────────
        // By zoneType ordinal, then by approximate area descending.
        val sorted = validated.sortedWith(
            compareBy<RegulatedZone> { it.zoneType.ordinal }
                .thenByDescending { approximateArea(it) }
        )

        // ── Step e: Build metadata ──────────────────────────────────────────────
        val sourceCount = sorted.map { it.source }.distinct().size
        val metadata = RegulationMetadata(
            fetchTimestampMs = nowMs,
            sourceCount = sourceCount,
            totalZones = sorted.size
        )

        return RegulatedZoneSet(zones = sorted, metadata = metadata)
    }

    // ── Deduplication ──────────────────────────────────────────────────────────────

    /**
     * Deduplicate zones by discarding non-SHOM zones whose centroids fall within
     * any SHOM zone's axis-aligned bounding box.
     *
     * SHOM zones are retained as-is. Non-SHOM zones are kept only if their centroid
     * lies outside all SHOM zone bounding boxes.
     */
    private fun deduplicate(zones: List<RegulatedZone>): List<RegulatedZone> {
        val shomZones = zones.filter { it.source == "SHOM" }
        val nonShom = zones.filter { it.source != "SHOM" }
        val result = shomZones.toMutableList()

        for (candidate in nonShom) {
            val isOverlappingShom = shomZones.any { shom ->
                centroidInBbox(centroid(candidate), shom.outerRing)
            }
            if (!isOverlappingShom) {
                result.add(candidate)
            }
        }

        return result
    }

    // ── Geometry helpers ──────────────────────────────────────────────────────────

    /**
     * Compute the centroid of a [RegulatedZone] as the arithmetic mean of its
     * outer ring vertices.
     */
    private fun centroid(zone: RegulatedZone): LatLng {
        val ring = zone.outerRing
        if (ring.size < 2) return LatLng(0.0, 0.0)
        // Drop the closing vertex (last == first) for the mean calculation
        val points = if (ring.first() == ring.last()) ring.dropLast(1) else ring
        val n = points.size
        if (n == 0) return LatLng(0.0, 0.0)
        val latSum = points.sumOf { it.latitude }
        val lonSum = points.sumOf { it.longitude }
        return LatLng(latitude = latSum / n, longitude = lonSum / n)
    }

    /**
     * Check whether [point] falls within the axis-aligned bounding box of [ring].
     * A simple bounds check — sufficient for overlap detection in the
     * Nice–Fréjus corridor which does not cross the antimeridian.
     */
    private fun centroidInBbox(point: LatLng, ring: List<LatLng>): Boolean {
        if (ring.isEmpty()) return false
        val minLat = ring.minOf { it.latitude }
        val maxLat = ring.maxOf { it.latitude }
        val minLon = ring.minOf { it.longitude }
        val maxLon = ring.maxOf { it.longitude }
        return point.latitude in minLat..maxLat &&
                point.longitude in minLon..maxLon
    }

    /**
     * Check whether a [LatLng] point falls within a [BoundingBox].
     *
     * Performs a simple axis-aligned bounds check (sufficient for the
     * Nice–Fréjus corridor which does not cross the antimeridian).
     */
    private fun isInBbox(point: LatLng, bbox: BoundingBox): Boolean {
        return point.latitude in bbox.latSouth..bbox.latNorth &&
                point.longitude in bbox.lonWest..bbox.lonEast
    }

    /**
     * Approximate area of a zone polygon in square metres using the Shoelace
     * formula on projected coordinates.
     *
     * Projects outer ring vertices to metres via a simple cylindrical projection
     * at the zone centroid latitude:
     * - X = longitude * 111_320.0 * cos(centroidLat)
     * - Y = latitude * 111_320.0
     *
     * This is not exact for large polygons, but sufficient for comparative sorting.
     *
     * @return Area in square metres, or 0.0 if the ring has fewer than 3 vertices.
     */
    private fun approximateArea(zone: RegulatedZone): Double {
        val ring = zone.outerRing
        if (ring.size < 3) return 0.0

        val c = centroid(zone)
        val metersPerDegLon = 111_320.0 * cos(Math.toRadians(c.latitude))
        val metersPerDegLat = 111_320.0

        // Ensure a closed polygon (last == first) for the Shoelace loop
        val points = if (ring.first() == ring.last()) ring else ring + ring.first()

        var sum = 0.0
        for (i in 0 until points.size - 1) {
            val xi = points[i].longitude * metersPerDegLon
            val yi = points[i].latitude * metersPerDegLat
            val xj = points[i + 1].longitude * metersPerDegLon
            val yj = points[i + 1].latitude * metersPerDegLat
            sum += xi * yj - xj * yi
        }

        return abs(sum) / 2.0
    }
}
