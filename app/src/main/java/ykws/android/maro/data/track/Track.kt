package ykws.android.maro.data.track

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * A recorded boat track — one journey from Port Salis → Port Salis (or manual start/stop).
 *
 * Persisted as protobuf binary via kotlinx-serialization-protobuf.
 *
 * @property id                   Unique identifier (UUID).
 * @property name                 Auto-generated name ("yyyy-MM-dd HH:mm") or user-edited.
 * @property comment              Optional user comment.
 * @property startTimeMs          Epoch millis of the first track point.
 * @property endTimeMs            Epoch millis when recording was finalised, or null if incomplete.
 * @property pausedDurationSec    Total seconds spent in PAUSED state.
 * @property fastestSpeedMps      Maximum instantaneous speed (m/s).
 * @property averageSpeedMps      Average speed (m/s) over the entire track (excluding pauses).
 * @property trackColorArgb       ARGB colour for the polyline on the map (default amber 0xFFFF6F00).
 * @property trackPoints          The GPS polyline points.
 * @property visibleOnMap         Whether this track's polyline is drawn on the map.
 * @property distanceNm           Cumulative distance in nautical miles.
 * @property navigatingDurationSec Total time actually under way (computed = elapsedWallClockSec - pausedDurationSec).
 */
@Serializable
data class Track(
    @ProtoNumber(1) val id: String,
    @ProtoNumber(2) val name: String,
    @ProtoNumber(3) val comment: String = "",
    @ProtoNumber(4) val startTimeMs: Long,
    @ProtoNumber(5) val endTimeMs: Long? = null,
    @ProtoNumber(6) val pausedDurationSec: Long = 0,
    @ProtoNumber(7) val fastestSpeedMps: Float = 0f,
    @ProtoNumber(8) val averageSpeedMps: Float = 0f,
    @ProtoNumber(9) val trackColorArgb: Int = 0xFFFF6F00.toInt(),
    @ProtoNumber(10) val trackPoints: List<TrackPoint> = emptyList(),
    @ProtoNumber(11) val visibleOnMap: Boolean = true,
    @ProtoNumber(12) val distanceNm: Float = 0f,
    @ProtoNumber(13) val navigatingDurationSec: Long = 0
)

/**
 * Lightweight summary of a [Track] for list display — no polyline points.
 * Stored in the index file for fast listing without loading full tracks.
 */
@Serializable
data class TrackSummary(
    @ProtoNumber(1) val id: String,
    @ProtoNumber(2) val name: String,
    @ProtoNumber(3) val comment: String = "",
    @ProtoNumber(4) val startTimeMs: Long,
    @ProtoNumber(5) val endTimeMs: Long? = null,
    @ProtoNumber(6) val fastestSpeedMps: Float = 0f,
    @ProtoNumber(7) val distanceNm: Float = 0f,
    @ProtoNumber(8) val visibleOnMap: Boolean = true,
    @ProtoNumber(9) val navigatingDurationSec: Long = 0,
    @ProtoNumber(10) val pausedDurationSec: Long = 0,
    @ProtoNumber(11) val averageSpeedMps: Float = 0f
)

/**
 * Wrapper for the index file — a list of [TrackSummary] entries.
 */
@Serializable
data class TrackSummaryList(
    @ProtoNumber(1) val tracks: List<TrackSummary>
)
