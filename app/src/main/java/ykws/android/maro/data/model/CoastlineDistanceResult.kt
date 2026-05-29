package ykws.android.maro.data.model

/**
 * Result of a nearest-coastline distance query.
 *
 * Returned by [CoastlineSpatialIndex.query] and exposed through
 * [CoastlineRepository.distanceToCoast].
 *
 * @property distanceMeters Straight-line distance from the query point to the
 *                          closest point on ANY coastline polyline (mainland
 *                          or island). [Double.MAX_VALUE] if no coastline is
 *                          loaded.
 * @property closestPoint   The exact geographic point on the coastline that is
 *                          closest to the query position.
 * @property segmentId      Identifier of the [CoastlineSegment] containing
 *                          [closestPoint]. Empty string if no coastline loaded.
 * @property isMainland     `true` when [closestPoint] lies on the mainland
 *                          polyline (index 0), `false` for an island.
 */
data class CoastlineDistanceResult(
    val distanceMeters: Double,
    val closestPoint: LatLng,
    val segmentId: String,
    val isMainland: Boolean
)
