package ykws.android.maro.data.regulation

import ykws.android.maro.data.model.BoundingBox
import ykws.android.maro.data.model.LatLng
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Aggregates [RegulatedZone] instances from SHOM WFS, INPN WFS, and seed sources
 * into a single deduplicated, validated, sorted [RegulatedZoneSet].
 *
 * Processing pipeline:
 * 1. **Collect** — start with SHOM zones, then INPN zones, then append all seed zones
 * 2. **Deduplicate** — authority-based dedup with 50 m centroid threshold:
 *    - SHOM zones are authoritative over INPN and SEED
 *    - INPN zones are authoritative over SEED
 *    - Seeds are discarded if overlapping SHOM or INPN zones
 * 3. **Validate** — reject zones whose centroid falls outside the lookup [BoundingBox]
 * 4. **Sort** — by zone type ordinal, then approximate area descending
 * 5. **Build metadata** — [RegulationMetadata] with timestamp and source counts
 */
object RegulationAggregator {

    /** Centroid distance threshold for dedup (metres). */
    private const val DEDUP_THRESHOLD_M = 50.0

    /**
     * Aggregate SHOM, INPN, and seed zones into a single authoritative [RegulatedZoneSet].
     *
     * @param shomZones Zones fetched from the SHOM WFS (may be empty if fetch failed).
     * @param inpnZones Zones fetched from the INPN WFS (may be empty if fetch failed).
     * @param seedZones Hardcoded fallback zones guaranteed to cover known regulations.
     * @param bbox      Geographic bounding box; zones with centroids outside are rejected.
     * @param nowMs     Timestamp for [RegulationMetadata.fetchTimestampMs] (epoch millis).
     * @return A validated, deduplicated, sorted [RegulatedZoneSet] with metadata.
     */
    fun aggregate(
        shomZones: List<RegulatedZone>,
        inpnZones: List<RegulatedZone> = emptyList(),
        seedZones: List<RegulatedZone> = emptyList(),
        bbox: BoundingBox,
        nowMs: Long = System.currentTimeMillis()
    ): RegulatedZoneSet {
        // ── Step a: Collect ─────────────────────────────────────────────────────
        // Order matters: SHOM first (authoritative), then INPN, then seeds.
        val collected = mutableListOf<RegulatedZone>()
        collected.addAll(shomZones)
        collected.addAll(inpnZones)
        collected.addAll(seedZones)

        // ── Step b: Deduplicate ─────────────────────────────────────────────────
        // Authority-based dedup with centroid proximity threshold.
        // SHOM > INPN > SEED — lower-authority zones within threshold of higher
        // are discarded.
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
     * Deduplicate zones using authority-based rules with centroid proximity threshold.
     *
     * Authority hierarchy: SHOM > INPN > SEED > (any other source)
     *
     * For each zone in order of authority:
     * - If its centroid is within [DEDUP_THRESHOLD_M] of any higher-authority zone,
     *   it is discarded.
     * - Kept zones are added to the accepted list and serve as "authority" for
     *   subsequent lower-authority zones.
     * - Unknown sources (not in the authority list) are treated as lowest priority,
     *   processed after all known sources.
     */
    private fun deduplicate(zones: List<RegulatedZone>): List<RegulatedZone> {
        val authorityOrder = listOf("SHOM", "INPN", "SEED")
        val grouped = zones.groupBy { it.source }
        val accepted = mutableListOf<RegulatedZone>()

        // Process known sources in authority order
        for (source in authorityOrder) {
            val srcZones = grouped[source] ?: continue
            for (zone in srcZones) {
                val zoneCentroid = centroid(zone)
                val isDuplicate = accepted.any { acceptedZone ->
                    val dist = haversineDistance(zoneCentroid, centroid(acceptedZone))
                    dist < DEDUP_THRESHOLD_M
                }
                if (!isDuplicate) {
                    accepted.add(zone)
                }
            }
        }

        // Process unknown sources as lowest priority
        val knownSources = authorityOrder.toSet()
        for ((source, srcZones) in grouped) {
            if (source in knownSources) continue
            for (zone in srcZones) {
                val zoneCentroid = centroid(zone)
                val isDuplicate = accepted.any { acceptedZone ->
                    val dist = haversineDistance(zoneCentroid, centroid(acceptedZone))
                    dist < DEDUP_THRESHOLD_M
                }
                if (!isDuplicate) {
                    accepted.add(zone)
                }
            }
        }

        return accepted
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
     * Haversine distance between two WGS84 points in metres.
     */
    private fun haversineDistance(a: LatLng, b: LatLng): Double {
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)

        val sinHalfDlat = kotlin.math.sin(dLat / 2.0)
        val sinHalfDlon = kotlin.math.sin(dLon / 2.0)
        val aVal = sinHalfDlat * sinHalfDlat +
                sinHalfDlon * sinHalfDlon * kotlin.math.cos(lat1) * kotlin.math.cos(lat2)
        val c = 2.0 * kotlin.math.atan2(kotlin.math.sqrt(aVal), kotlin.math.sqrt(1.0 - aVal))
        return EARTH_RADIUS_M * c
    }

    private const val EARTH_RADIUS_M = 6_371_000.0

    /**
     * Check whether a [LatLng] point falls within a [BoundingBox].
     */
    private fun isInBbox(point: LatLng, bbox: BoundingBox): Boolean {
        return point.latitude in bbox.latSouth..bbox.latNorth &&
                point.longitude in bbox.lonWest..bbox.lonEast
    }

    /**
     * Approximate area of a zone polygon in square metres using the Shoelace
     * formula on projected coordinates.
     */
    private fun approximateArea(zone: RegulatedZone): Double {
        val ring = zone.outerRing
        if (ring.size < 3) return 0.0

        val c = centroid(zone)
        val metersPerDegLon = 111_320.0 * cos(Math.toRadians(c.latitude))
        val metersPerDegLat = 111_320.0

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
