package ykws.android.maro.data.model

/**
 * Geographic bounding box in WGS84 decimal degrees.
 *
 * @property latSouth Southern latitude bound (-90..+90).
 * @property latNorth Northern latitude bound (-90..+90).
 * @property lonWest Western longitude bound (-180..+180).
 * @property lonEast Eastern longitude bound (-180..+180).
 */
data class BoundingBox(
    val latSouth: Double,
    val latNorth: Double,
    val lonWest: Double,
    val lonEast: Double
) {
    /** Width of the bounding box in degrees longitude. */
    val widthDeg: Double get() = lonEast - lonWest

    /** Height of the bounding box in degrees latitude. */
    val heightDeg: Double get() = latNorth - latSouth

    /** Central latitude. */
    val centerLat: Double get() = (latSouth + latNorth) / 2.0

    /** Central longitude. */
    val centerLon: Double get() = (lonWest + lonEast) / 2.0

    companion object {
        /** Empty bounding box (no area). */
        val EMPTY = BoundingBox(0.0, 0.0, 0.0, 0.0)
    }
}
