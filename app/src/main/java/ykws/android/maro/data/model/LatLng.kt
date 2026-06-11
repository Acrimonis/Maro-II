package ykws.android.maro.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * A geographic coordinate in WGS84 (latitude / longitude).
 *
 * This is the core spatial primitive used throughout the coastline system.
 * Latitude: -90..+90 (south..north)
 * Longitude: -180..+180 (west..east)
 */
@Serializable
data class LatLng(
    @ProtoNumber(1) val latitude: Double,
    @ProtoNumber(2) val longitude: Double
)
