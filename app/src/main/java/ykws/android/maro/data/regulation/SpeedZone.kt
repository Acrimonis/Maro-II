package ykws.android.maro.data.regulation

import ykws.android.maro.data.model.LatLng

/**
 * A runtime speed zone — created from a [RegulatedZone] filtered to
 * [RegulatedZone.speedLimitKn] != null.
 *
 * This is a lightweight runtime model (not serialized) used for spatial queries
 * and the unified speed limit engine. The 300m band is NOT represented here;
 * it is handled separately via [distanceToCoast] in [CoastlineViewModel].
 *
 * @property id          Source reference ID (e.g. SHOM sourceRef).
 * @property name        Display name (e.g. "Cap d'Antibes").
 * @property speedLimitKn Speed limit in knots (always non-null).
 * @property outerRing   Polygon outer boundary as a closed LatLng ring.
 * @property holes       Zero or more interior holes (islands exempt).
 * @property source      Data source identifier ("SHOM", "SEED", etc.).
 */
data class SpeedZone(
    val id: String,
    val name: String,
    val speedLimitKn: Double,
    val outerRing: List<LatLng>,
    val holes: List<List<LatLng>> = emptyList(),
    val source: String = "SHOM"
)

/**
 * Result of a speed zone spatial query at a geographic point.
 *
 * @property allInsideZones        All speed zones containing the current position,
 *                                  sorted by speed limit ascending (most restrictive first).
 * @property nearestZone           Closest speed zone boundary (any direction), null if none found.
 * @property distanceToBoundaryM   Signed distance to the nearest zone boundary:
 *                                  + outside the zone, - inside. Null if [nearestZone] is null.
 * @property insideAnyZone         True if the position is inside at least one speed zone.
 * @property mostRestrictiveSpeedKn Minimum of all inside zone speed limits; null if outside all.
 * @property approaching           True if distance decreased over the last two samples.
 */
data class SpeedZoneQuery(
    val allInsideZones: List<SpeedZone> = emptyList(),
    val nearestZone: SpeedZone? = null,
    val distanceToBoundaryM: Double? = null,
    val insideAnyZone: Boolean = false,
    val mostRestrictiveSpeedKn: Double? = null,
    val approaching: Boolean = false
)
