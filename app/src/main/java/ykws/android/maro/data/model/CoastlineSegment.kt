package ykws.android.maro.data.model

import kotlinx.serialization.Serializable

/**
 * A contiguous, oriented coastline polyline.
 *
 * Orientation convention: **water is on the RIGHT side** of the direction of travel.
 * For the Nice–Fréjus zone (west → east), this means water is to the south.
 *
 * @property id Unique identifier (usually the OSM way ID).
 * @property points Ordered list of vertices forming the polyline. Size >= 2.
 */
@Serializable
data class CoastlineSegment(
    val id: String,
    val points: List<LatLng>
)
