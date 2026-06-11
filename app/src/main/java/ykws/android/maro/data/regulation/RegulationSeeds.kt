package ykws.android.maro.data.regulation

import ykws.android.maro.data.model.LatLng
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import java.lang.Math

/**
 * Hardcoded seed zones that are always available, even when the SHOM WFS is unreachable.
 *
 * These provide baseline coverage for known regulated zones in the Nice–Fréjus corridor.
 * Seeds are merged with WFS-fetched data by [RegulationAggregator] at bake time.
 */
object RegulationSeeds {

    private const val EARTH_RADIUS_M = 6_371_000.0

    /**
     * Returns the complete list of hardcoded seed zones.
     *
     * Each zone is approximated as a regular polygon ring around a known centroid.
     */
    fun getSeeds(): List<RegulatedZone> = listOf(
        // ── a. Cap d'Antibes speed zone ────────────────────────────────────
        RegulatedZone(
            outerRing = buildSeedPolygon(
                centerLat = 43.560,
                centerLon = 7.130,
                radiusM = 1_500.0
            ),
            zoneType = RegulatedZoneType.SPEED_LIMIT,
            speedLimitKn = 10.0,
            name = "Cap d'Antibes — 10 nœuds",
            source = "SEED",
            description = "Zone de vitesse limitée à 10 nœuds autour du Cap d'Antibes"
        ),

        // ── b. Îles de Lérins inter-îles zone ──────────────────────────────
        RegulatedZone(
            outerRing = buildSeedPolygon(
                centerLat = 43.523,
                centerLon = 7.045,
                radiusM = 800.0
            ),
            zoneType = RegulatedZoneType.NAVIGATION_RESTRICTION,
            name = "Îles de Lérins — Circulation réglementée",
            source = "SEED",
            description = "Zone réglementée entre les îles de Lérins (taille des navires, vitesse)"
        ),

        // ── c. Baie des Anges (Nice) ───────────────────────────────────────
        RegulatedZone(
            outerRing = buildSeedPolygon(
                centerLat = 43.620,
                centerLon = 7.240,
                radiusM = 2_000.0
            ),
            zoneType = RegulatedZoneType.SPEED_LIMIT,
            speedLimitKn = 10.0,
            name = "Baie des Anges — 10 nœuds",
            source = "SEED",
            description = "Zone de vitesse limitée à 10 nœuds dans la Baie des Anges"
        ),

        // ── d. Parc National de Port-Cros (référence) ──────────────────────
        // Stub: empty polygon, will be refined when detailed geometry is available.
        RegulatedZone(
            outerRing = emptyList(),
            zoneType = RegulatedZoneType.ENVIRONMENTAL,
            name = "Parc National de Port-Cros (référence)",
            source = "SEED",
            description = "Zone environnementale du Parc National de Port-Cros (en attente de géométrie précise)"
        )
    )

    /**
     * Builds a closed regular-polygon ring approximating a circular zone.
     *
     * Uses the same local planar projection formula as [HazardRings]:
     * degrees-per-metre ratios are computed at [centerLat], then each vertex
     * is placed at angle [2π·k/vertices] around the centre.
     *
     * @param centerLat  Centroid latitude (WGS84, degrees)
     * @param centerLon  Centroid longitude (WGS84, degrees)
     * @param radiusM    Circle radius in metres
     * @param vertices   Number of polygon vertices (default 8)
     * @return Closed ring — first vertex repeated as last element,
     *         suitable for use as [RegulatedZone.outerRing].
     */
    private fun buildSeedPolygon(
        centerLat: Double,
        centerLon: Double,
        radiusM: Double,
        vertices: Int = 8
    ): List<LatLng> {
        val mPerDegLat = EARTH_RADIUS_M * PI / 180.0
        val mPerDegLon = mPerDegLat * cos(Math.toRadians(centerLat))
        val dLat = radiusM / mPerDegLat
        val dLon = radiusM / mPerDegLon

        val ring = ArrayList<LatLng>(vertices + 1)
        for (k in 0 until vertices) {
            val angle = 2.0 * PI * k / vertices
            ring.add(
                LatLng(
                    latitude = centerLat + dLat * sin(angle),
                    longitude = centerLon + dLon * cos(angle)
                )
            )
        }
        // Close the ring — first vertex repeated as last element
        ring.add(ring.first())
        return ring
    }
}
