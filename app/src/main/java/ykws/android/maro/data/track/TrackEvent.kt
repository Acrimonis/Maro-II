package ykws.android.maro.data.track

/**
 * In-memory event bus between [TrackRecorder] and the UI layer.
 * Not serialized — these are live state machine events only.
 */
sealed class TrackEvent {
    /** Recording has started (IDLE → RECORDING). */
    data object Started : TrackEvent()

    /** Recording was paused (RECORDING → PAUSED). */
    data object Paused : TrackEvent()

    /** Recording was resumed (PAUSED → RECORDING). */
    data object Resumed : TrackEvent()

    /** Recording has stopped and track was saved (FINALIZING → IDLE). */
    data object Stopped : TrackEvent()

    /** A new point was captured during recording. */
    data class PointCaptured(val point: TrackPoint) : TrackEvent()
}
