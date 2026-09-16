package ykws.android.maro.data.track

import ykws.android.maro.data.model.LatLng
import ykws.android.maro.spatial.SpatialOperations

/**
 * Pure-function geofence checker. Its distance comes from [SpatialOperations.haversine] — the app's
 * single haversine implementation — so this object keeps no second copy of the formula.
 */
object TrackGeofenceChecker {

    /**
     * Haversine distance between two WGS84 points in metres.
     */
    fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double =
        SpatialOperations.haversine(LatLng(lat1, lon1), LatLng(lat2, lon2))

    /**
     * Returns true when [posLat]/[posLon] is within [radiusM] of [originLat]/[originLon].
     */
    fun isInsideGeofence(
        posLat: Double, posLon: Double,
        originLat: Double, originLon: Double,
        radiusM: Double
    ): Boolean = distanceM(posLat, posLon, originLat, originLon) <= radiusM
}
