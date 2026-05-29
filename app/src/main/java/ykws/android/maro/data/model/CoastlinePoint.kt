package ykws.android.maro.data.model

/**
 * An enriched coastline point with pre-computed edge vector.
 *
 * The edge vector (dxM, dyM) is the Cartesian offset in meters from this point
 * to the next point in the polyline, projected onto a local plane centered at
 * this point. Pre-computing this avoids repeated projection math in spatial
 * queries (distance, side-of-line, buffer generation).
 *
 * For the **last point** of a polyline, edge vectors are (0f, 0f) since there
 * is no next point.
 *
 * @property lat Latitude in WGS84 decimal degrees (Float is sufficient for ~1m precision).
 * @property lon Longitude in WGS84 decimal degrees.
 * @property edgeDxM Cartesian X-offset to the next point, in meters. East = positive.
 * @property edgeDyM Cartesian Y-offset to the next point, in meters. North = positive.
 */
data class CoastlinePoint(
    val lat: Float,
    val lon: Float,
    val edgeDxM: Float = 0f,
    val edgeDyM: Float = 0f
) {
    /** True if this is the last point in its polyline (no outgoing edge). */
    val isTerminal: Boolean get() = edgeDxM == 0f && edgeDyM == 0f
}
