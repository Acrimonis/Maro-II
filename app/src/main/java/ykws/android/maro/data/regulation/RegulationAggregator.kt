package ykws.android.maro.data.regulation

import ykws.android.maro.data.model.BoundingBox
import ykws.android.maro.data.model.LatLng
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Aggregates [RegulatedZone] instances from SHOM WFS and seed sources into a
 * single deduplicated, validated, sorted [RegulatedZoneSet].
 *
 * Processing pipeline:
 * 1. **Collect** — start with SHOM zones, append seed zones not near any SHOM zone
 * 2. **Deduplicate** — zones within [DUP_RADIUS_M] + same [RegulatedZoneType] → merge
 * 3. **Validate** — reject zones whose centroid falls outside the lookup [BoundingBox]
 * 4. **Sort** — by zone type ordinal, then approximate area descending
 * 5. **Build metadata** — [RegulationMetadata] with timestamp and source counts
 */
object RegulationAggregator {

    /** Maximum centroid distance (metres) to consider two zones a duplicate. */
    private const val DUP_RADIUS_M = 25.0

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
        // Start with all SHOM zones, then append seed zones that are NOT near any
        // SHOM zone (within 25 m centroid distance).
        val collected = mutableListOf<RegulatedZone>()
        collected.addAll(shomZones)

        for (seed in seedZones) {
            val isNearShom = shomZones.any { shom ->
                zoneDistanceM(shom, seed) < DUP_RADIUS_M
            }
            if (!isNearShom) {
                collected.add(seed)
            }
        }

        // ── Step b: Deduplicate ─────────────────────────────────────────────────
        // Zones within 25 m centroid distance + same RegulatedZoneType → merge.
        // Keep SHOM attributes, append "+SEED" to source if one zone is a seed.
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
     * Deduplicate zones by grouping same-type zones whose centroids are within
     * [DUP_RADIUS_M] and merging each group into a single zone.
     */
    private fun deduplicate(zones: List<RegulatedZone>): List<RegulatedZone> {
        val remaining = zones.toMutableList()
        val result = mutableListOf<RegulatedZone>()

        while (remaining.isNotEmpty()) {
            val current = remaining.removeAt(0)
            val group = mutableListOf(current)

            val iter = remaining.iterator()
            while (iter.hasNext()) {
                val other = iter.next()
                if (other.zoneType == current.zoneType &&
                    zoneDistanceM(current, other) < DUP_RADIUS_M
                ) {
                    group.add(other)
                    iter.remove()
                }
            }

            result.add(mergeZones(group))
        }

        return result
    }

    /**
     * Merge a group of duplicate zones into a single authoritative zone.
     *
     * - If a SHOM zone exists in the group, its attributes are kept as the base.
     * - If any zone in the group is a seed, the source becomes "SHOM+SEED"
     *   (or just "SEED" if no SHOM zone is present).
     * - Otherwise the first zone's attributes are used as-is.
     */
    private fun mergeZones(duplicates: List<RegulatedZone>): RegulatedZone {
        val shomZone = duplicates.find { it.source == "SHOM" }
        val primary = shomZone ?: duplicates.first()
        val hasSeed = duplicates.any { it.source == "SEED" }

        val mergedSource = when {
            hasSeed && shomZone != null -> "SHOM+SEED"
            else -> primary.source
        }

        return primary.copy(source = mergedSource)
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
     * Approximate Euclidean distance between two zone centroids in metres.
     *
     * Uses a degree-to-metre conversion that is accurate enough at mid-latitudes:
     * ```
     * dLat = (aCentroid.latitude - bCentroid.latitude) * 111_320.0
     * dLon = (aCentroid.longitude - bCentroid.longitude) * 111_320.0 * cos(refLat)
     * ```
     * where `refLat` is the mean latitude of the two centroids.
     */
    private fun zoneDistanceM(a: RegulatedZone, b: RegulatedZone): Double {
        val aCentroid = centroid(a)
        val bCentroid = centroid(b)
        val refLat = Math.toRadians((aCentroid.latitude + bCentroid.latitude) / 2.0)
        val dLat = (aCentroid.latitude - bCentroid.latitude) * 111_320.0
        val dLon = (aCentroid.longitude - bCentroid.longitude) * 111_320.0 * cos(refLat)
        return sqrt(dLat * dLat + dLon * dLon)
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
