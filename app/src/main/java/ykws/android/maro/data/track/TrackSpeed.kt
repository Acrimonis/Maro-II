package ykws.android.maro.data.track

import ykws.android.maro.data.model.LatLng
import ykws.android.maro.spatial.SpatialOperations

/**
 * Derive a speed for the point at [index] from its stored value or, when that is absent, from the
 * time delta and haversine distance to its neighbour.
 *
 * Pure and stateless by contract: it reads [points] and [index] and returns a speed or null. It
 * never carries a value forward and never substitutes a zero, because an underivable point is a
 * fact each caller must answer for itself — the arrow path answers zero (its historic spacing
 * behaviour), the speed heatmap answers the ramp's neutral tint.
 *
 * The distance comes from [SpatialOperations.haversine], the app's shared implementation, so this
 * primitive keeps no copy of the formula; a few older call sites still hold their own.
 *
 * @return the pair's distance over Δt in m/s, or null when the point has no partner, when the pair
 *         is a GAP seam, or when the time delta is zero or negative — the three cases where a
 *         derived speed would be invented rather than measured.
 */
fun deriveSpeedMps(points: List<TrackPoint>, index: Int): Float? {
    if (index !in points.indices) return null
    val j = if (index + 1 < points.size) index + 1 else index - 1
    if (j !in points.indices) return null
    val a = points[minOf(index, j)]
    val b = points[maxOf(index, j)]
    if (a.type == PointType.GAP || b.type == PointType.GAP) return null
    val dtSec = (b.timeOffsetMs - a.timeOffsetMs) / 1000.0
    if (dtSec <= 0.0) return null
    val distanceM = SpatialOperations.haversine(LatLng(a.lat, a.lon), LatLng(b.lat, b.lon))
    return (distanceM / dtSec).toFloat()
}
