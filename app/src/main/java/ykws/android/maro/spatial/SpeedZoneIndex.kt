package ykws.android.maro.spatial

import ykws.android.maro.data.regulation.SpeedZone
import ykws.android.maro.data.regulation.SpeedZoneQuery

/**
 * Grid spatial index for speed zone polygons — thin wrapper over [PolygonIndexBase]
 * that maps results back to the original [SpeedZone] objects (no reconstruction).
 */
class SpeedZoneIndex(
    private val zones: List<SpeedZone>,
    cellSizeM: Double = 100.0
) {

    private val base = PolygonIndexBase(
        zones.map { IndexedZone(it.id, it.name, it.outerRing, it.holes, it.speedLimitKn) },
        cellSizeM
    )

    val hasData: Boolean get() = base.hasData

    /** First speed-zone boundary crossed along [headingDeg]; returns (zone, along-heading distance). */
    fun firstSpeedZoneAhead(
        lat: Double, lon: Double,
        headingDeg: Double,
        maxSearch: Double = 2000.0
    ): Pair<SpeedZone, Double>? =
        base.firstAhead(lat, lon, headingDeg, maxSearch)?.let { (idx, d) -> zones[idx] to d }

    /** Directional cone primitive bound to SPEED. */
    fun boundaryInCone(lat: Double, lon: Double, headingDeg: Double, halfAngleDeg: Double, maxM: Double): BoundaryHit? =
        base.boundaryInCone(lat, lon, headingDeg, halfAngleDeg, maxM, ZoneKind.SPEED)

    /** Point primitive bound to SPEED. */
    fun zoneStatus(lat: Double, lon: Double): ZoneStatus =
        base.zoneStatus(lat, lon, ZoneKind.SPEED)

    /** Point query: inside zones (most restrictive first) + nearest boundary + strictest limit. */
    fun query(lat: Double, lon: Double): SpeedZoneQuery {
        if (!hasData) return SpeedZoneQuery()
        val s = base.status(lat, lon)
        val insideZones = s.insideZoneIdxs.map { zones[it] }.sortedBy { it.speedLimitKn }
        val nearestZone = if (s.nearestZoneIdx >= 0) zones[s.nearestZoneIdx] else null
        return SpeedZoneQuery(
            allInsideZones = insideZones,
            nearestZone = nearestZone,
            distanceToBoundaryM = s.nearestBoundaryM,
            insideAnyZone = insideZones.isNotEmpty(),
            mostRestrictiveSpeedKn = insideZones.minOfOrNull { it.speedLimitKn },
            approaching = false
        )
    }
}
