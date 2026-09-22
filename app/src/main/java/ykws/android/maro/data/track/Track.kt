package ykws.android.maro.data.track

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import ykws.android.maro.data.model.ListableItem

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
 * @property pinned               Whether this track is pinned (always renders on map regardless of history count).
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
    // @ProtoNumber(11) reserved — was visibleOnMap; never reuse (legacy blobs may still carry it).
    @ProtoNumber(12) val distanceNm: Float = 0f,
    @ProtoNumber(13) val navigatingDurationSec: Long = 0,
    @ProtoNumber(14) val pinned: Boolean = false,
    @ProtoNumber(15) val idleDurationSec: Long = 0,
    @ProtoNumber(16) val boatMarkers: List<BoatMarker> = emptyList(),
    @ProtoNumber(17) val updatedAtEpochMs: Long = 0L,
    @ProtoNumber(18) val lastPointTimeMs: Long = 0L,
    /**
     * True when this track's vertices are a *plan* rather than a recording: the speeds are the
     * speeds the router intended, taken at search time, and the times are the times it allotted.
     *
     * A fresh number with a default, so an old blob reads unchanged and an older build still reads
     * a new one — which is why the flag lives here rather than as a new `PointType`, where an older
     * reader would refuse the whole file. It is the only thing that distinguishes a saved route;
     * every downstream reader treats the track as an ordinary one.
     */
    @ProtoNumber(19) val plannedCourse: Boolean = false
)

/**
 * The Tracks feature's standard auto-name for a newly saved track — `yyyy-MM-dd HH:mm`, US locale.
 *
 * One home for it: the recorder names a new recording with this and `TrackFromCourse` names a saved
 * route with it, so the two kinds of track sort and read alike in the list.
 */
fun trackAutoName(epochMs: Long): String =
    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
        .format(java.util.Date(epochMs))

/**
 * Absolute epoch millis of the last real (non-GAP) track point, or null if there are none.
 * GAP markers carry a synthetic `timeOffsetMs` one ms after the preceding point, so they are skipped.
 */
fun Track.lastRealPointTimeMsOrNull(): Long? =
    trackPoints.lastOrNull { it.type != PointType.GAP }?.let { startTimeMs + it.timeOffsetMs }

/**
 * Lightweight summary of a [Track] for list display — no polyline points.
 * Stored in the index file for fast listing without loading full tracks.
 *
 * [waterPointCount] and [landPointCount] are the sampled position counts the track filter reads, **each
 * biased by one**. A count of zero is a legitimate count, protocol buffers drop a field equal to its
 * type's default, and an unbiased zero therefore came back as `0` — which is also what an absent field
 * means, so a wholly-land track read as never classified and the verdict turned it into water. Storing
 * `count + 1` makes the two meanings disjoint: `0` is the sentinel, any stored value ≥ 1 is a real count,
 * and the fields' own default (`0`) is the one value that is *correct* for an absent field — an index
 * written before this bias decodes as never classified and is re-sampled rather than trusted.
 * [sampledWaterPoints] and [sampledLandPoints] read the counts back out, and [positionClassified] is
 * derived from the stored pair so it cannot drift from it. They live here rather than on the points
 * because the list and the map both read summaries, and because the index is a cache that may be rebuilt.
 */
@Serializable
data class TrackSummary(
    @ProtoNumber(1) override val id: String,
    @ProtoNumber(2) val name: String,
    @ProtoNumber(3) val comment: String = "",
    @ProtoNumber(4) val startTimeMs: Long,
    @ProtoNumber(5) val endTimeMs: Long? = null,
    @ProtoNumber(6) val fastestSpeedMps: Float = 0f,
    @ProtoNumber(7) val distanceNm: Float = 0f,
    // @ProtoNumber(8) reserved — was visibleOnMap; never reuse (legacy index blobs may still carry it).
    @ProtoNumber(9) val navigatingDurationSec: Long = 0,
    @ProtoNumber(10) val pausedDurationSec: Long = 0,
    @ProtoNumber(11) val averageSpeedMps: Float = 0f,
    @ProtoNumber(12) val pinned: Boolean = false,
    @ProtoNumber(13) val pointCount: Int = 0,
    @ProtoNumber(14) val idleDurationSec: Long = 0,
    @ProtoNumber(15) override val updatedAtEpochMs: Long = 0L,
    @ProtoNumber(16) val lastPointTimeMs: Long = 0L,
    /**
     * Sampled points that were on water, **biased by one** — `0` means never classified, any value ≥ 1
     * means `value - 1` points. The bias is the whole reason this field can tell the two apart; see the
     * class note.
     */
    @ProtoNumber(17) val waterPointCount: Int = 0,
    /** Sampled points that were on land, biased by one exactly as [waterPointCount] is. */
    @ProtoNumber(18) val landPointCount: Int = 0
) : ListableItem {
    override val title: String get() = name
    override val description: String get() = comment
    override val createdAtEpochMs: Long get() = startTimeMs
    override val isPinned: Boolean get() = pinned
    /** Mutable backing for [ListableItem.isLive] — set by ViewModel, never persisted. */
    override var isLive: Boolean = false

    /**
     * Whether the two stored counts were ever filled. Derived from the pair rather than stored beside it,
     * so no third field can drift from the two it describes.
     */
    val positionClassified: Boolean get() = waterPointCount > 0 && landPointCount > 0

    /** Sampled points that were on water, or null when the track was never classified. */
    val sampledWaterPoints: Int? get() = waterPointCount.takeIf { positionClassified }?.minus(1)

    /** Sampled points that were on land, or null when the track was never classified. */
    val sampledLandPoints: Int? get() = landPointCount.takeIf { positionClassified }?.minus(1)

    /**
     * The position filter's reading of this track: water wins the tie, and a track nothing could be
     * classified for — open sea beyond the baked region included — counts as water. The bias leaves the
     * comparison untouched, a uniform shift preserving the order, so the tie is read off the stored pair
     * exactly as it always was.
     */
    val positionIsWater: Boolean
        get() = !positionClassified || waterPointCount >= landPointCount
}

/**
 * Wrapper for the index file — a list of [TrackSummary] entries.
 */
@Serializable
data class TrackSummaryList(
    @ProtoNumber(1) val tracks: List<TrackSummary>
)
