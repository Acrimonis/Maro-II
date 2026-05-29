package ykws.android.maro.data.model

import kotlinx.serialization.Serializable

/**
 * A contiguous, oriented coastline polyline.
 *
 * Orientation convention: **water is on the RIGHT side** of the direction of travel.
 * For the Nice–Fréjus zone (west → east), this means water is to the south.
 *
 * @property osmWayId The original OSM way ID (e.g., 12345678). 0 if unknown.
 * @property points Ordered list of vertices forming the polyline. Size >= 2.
 * @property isMainland True if this is part of the mainland coastline.
 * @property isClosed True if this is a closed ring (island). False for open polylines (mainland).
 */
@Serializable
data class CoastlineSegment(
    val osmWayId: Long = 0L,
    val points: List<CoastlinePoint>,
    val isMainland: Boolean = false,
    val isClosed: Boolean = false
) {
    /** Human-readable segment ID derived from OSM way ID. */
    val id: String get() = if (osmWayId != 0L) "osm:$osmWayId" else "coast-unknown"
}
