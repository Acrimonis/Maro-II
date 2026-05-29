package ykws.android.maro.data.model

/**
 * An enriched coastline point with pre-computed edge vector and projected coordinates.
 *
 * The edge vector (edgeDxM, edgeDyM) is the Cartesian offset in meters from this point
 * to the next point in the polyline. The projected coordinates (xM, yM) are this point's
 * position in a local Cartesian grid centered on the coastline's bounding box center.
 *
 * Pre-computing both the edge vector and projected position eliminates the need to
 * convert lat/lon to meters during spatial queries (distance, side-of-line, buffer).
 *
 * For the **last point** of a polyline, edge vectors are (0f, 0f) since there
 * is no next point.
 *
 * @property lat Latitude in WGS84 decimal degrees (Float, ~1m precision).
 * @property lon Longitude in WGS84 decimal degrees.
 * @property xM Projected X coordinate in meters (east = positive) from local origin.
 * @property yM Projected Y coordinate in meters (north = positive) from local origin.
 * @property edgeDxM Cartesian X-offset to the next point, in meters.
 * @property edgeDyM Cartesian Y-offset to the next point, in meters.
 */
data class CoastlinePoint(
    val lat: Float,
    val lon: Float,
    val xM: Float = 0f,
    val yM: Float = 0f,
    val edgeDxM: Float = 0f,
    val edgeDyM: Float = 0f
) {
    /** True if this is the last point in its polyline (no outgoing edge). */
    val isTerminal: Boolean get() = edgeDxM == 0f && edgeDyM == 0f
}
