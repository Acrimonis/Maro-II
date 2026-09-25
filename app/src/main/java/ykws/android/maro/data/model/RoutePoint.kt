package ykws.android.maro.data.model

/**
 * One navigation-mesh vertex: a water point the route search can stand on, beside `LatLng` and
 * `BoundingBox` in the app's shared model package.
 *
 * Kept separate from [LatLng] so the route's own types read as the route's, while a conversion in
 * either direction stays explicit and allocation is never hidden inside a spatial query.
 */
data class RoutePoint(
    val latitude: Double,
    val longitude: Double
) {
    /** @return this point as the app's shared coordinate type. */
    fun toLatLng(): LatLng = LatLng(latitude, longitude)

    companion object {
        /** @return [point] as a route point. */
        fun of(point: LatLng): RoutePoint = RoutePoint(point.latitude, point.longitude)
    }
}
