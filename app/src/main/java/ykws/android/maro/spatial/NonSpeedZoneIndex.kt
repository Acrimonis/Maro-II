package ykws.android.maro.spatial

/**
 * Non-speed regulated zone index (REGULATED kind) — thin holder over [PolygonIndexBase]
 * with the directional + point primitives already bound to [ZoneKind.REGULATED].
 */
class NonSpeedZoneIndex(zones: List<IndexedZone>, cellSizeM: Double = 100.0) {

    private val base = PolygonIndexBase(zones, cellSizeM)

    val hasData: Boolean get() = base.hasData

    fun boundaryInCone(lat: Double, lon: Double, headingDeg: Double, halfAngleDeg: Double, maxM: Double): BoundaryHit? =
        base.boundaryInCone(lat, lon, headingDeg, halfAngleDeg, maxM, ZoneKind.REGULATED)

    fun zoneStatus(lat: Double, lon: Double): ZoneStatus =
        base.zoneStatus(lat, lon, ZoneKind.REGULATED, forceNullStrictest = true)
}
