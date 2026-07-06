package ykws.android.maro.data.track

/**
 * In-memory event bus between [TrackRecorder] and the UI layer.
 * Not serialized — these are live state machine events only.
 */
sealed class TrackEvent {
    /** Recording has started (OFF → ON). */
    data object Started : TrackEvent()

    /** Recording resumed from a checkpoint — points list is restored track data. */
    data class Resumed(val points: List<TrackPoint>) : TrackEvent()

    /** Recording has stopped and track was saved (ON → OFF). */
    data object Stopped : TrackEvent()

    /** A new point was captured during recording. */
    data class PointCaptured(val point: TrackPoint) : TrackEvent()

    /** An idle period has started — boat has been stationary >= threshold. */
    data class IdlePeriodStarted(
        val entryLat: Double,
        val entryLon: Double,
        val startTimeMs: Long
    ) : TrackEvent()

    /** The marker drawer should auto-open (emitted after idle threshold reached). */
    data object DrawerAutoOpenRequested : TrackEvent()

    /** The marker drawer should auto-close (emitted on idle exit if auto-opened). */
    data object DrawerAutoCloseRequested : TrackEvent()

    /** An idle period has completed. */
    data class IdlePeriodCompleted(
        val entryLat: Double,
        val entryLon: Double,
        val startTimeMs: Long,
        val endTimeMs: Long,          // 0 = track finalized during idle
        val durationSec: Long,        // delta for this period, NOT cumulative
        val autoMarkerId: String?     // ID of 🕐 pin, null if none
    ) : TrackEvent()
}
