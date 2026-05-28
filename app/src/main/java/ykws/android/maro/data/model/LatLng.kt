package ykws.android.maro.data.model

import kotlinx.serialization.Serializable

/**
 * A geographic coordinate in WGS84 (latitude / longitude).
 *
 * This is the core spatial primitive used throughout the coastline system.
 * Latitude: -90..+90 (south..north)
 * Longitude: -180..+180 (west..east)
 */
@Serializable
data class LatLng(
    val latitude: Double,
    val longitude: Double
)
