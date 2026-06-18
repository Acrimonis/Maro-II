package ykws.android.maro.data.track

/**
 * In-memory event bus between [TrackRecorder] and the UI layer.
 * Not serialized — these are live state machine events only.
 */
sealed class TrackEvent {
    /** Recording has started (OFF → ON). */
    data object Started : TrackEvent()

    /** Recording has stopped and track was saved (ON → OFF). */
    data object Stopped : TrackEvent()

    /** A new point was captured during recording. */
    data class PointCaptured(val point: TrackPoint) : TrackEvent()
}
