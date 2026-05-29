package ykws.android.maro.data.model

/**
 * A geographic coordinate in WGS84 (latitude / longitude).
 *
 * This is the core spatial primitive used throughout the coastline system.
 * Latitude: -90..+90 (south..north)
 * Longitude: -180..+180 (west..east)
 */
data class LatLng(
    val latitude: Double,
    val longitude: Double
)
