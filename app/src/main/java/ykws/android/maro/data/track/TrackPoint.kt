package ykws.android.maro.data.track

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * A single GPS point in a recorded track.
 *
 * @property lat           WGS84 latitude (°N).
 * @property lon           WGS84 longitude (°E).
 * @property speedMps      Speed over ground (m/s) at this point, or null if unknown.
 * @property bearingDeg    Course over ground (degrees, 0–360) at this point, or null.
 * @property timeOffsetSec Seconds since track start (relative offset saves ~5 bytes/point vs absolute timestamp).
 */
@Serializable
data class TrackPoint(
    @ProtoNumber(1) val lat: Double,
    @ProtoNumber(2) val lon: Double,
    @ProtoNumber(3) val speedMps: Float? = null,
    @ProtoNumber(4) val bearingDeg: Float? = null,
    @ProtoNumber(5) val timeOffsetSec: Int = 0
)
