package ykws.android.maro.data.track

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * Type of a track point — distinguishes normal GPS fixes from synthetic gap markers.
 */
enum class PointType { NORMAL, GAP }

/**
 * A single GPS point in a recorded track.
 *
 * @property lat           WGS84 latitude (°N).
 * @property lon           WGS84 longitude (°E).
 * @property speedMps      Speed over ground (m/s) at this point, or null if unknown.
 * @property bearingDeg    Course over ground (degrees, 0–360) at this point, or null.
 * @property timeOffsetSec Seconds since track start (relative offset, kept for backward compat).
 * @property timeOffsetMs  Milliseconds since track start, monotonically increasing (guaranteed unique).
 * @property type          Point type: NORMAL (default, backward-compatible) or GAP (synthetic marker).
 */
@Serializable
data class TrackPoint(
    @ProtoNumber(1) val lat: Double,
    @ProtoNumber(2) val lon: Double,
    @ProtoNumber(3) val speedMps: Float? = null,
    @ProtoNumber(4) val bearingDeg: Float? = null,
    @ProtoNumber(5) val timeOffsetSec: Int = 0,
    @ProtoNumber(15) val timeOffsetMs: Long = 0L,
    @ProtoNumber(10) val type: PointType = PointType.NORMAL,
    @ProtoNumber(11) val accuracyM: Float? = null
)
