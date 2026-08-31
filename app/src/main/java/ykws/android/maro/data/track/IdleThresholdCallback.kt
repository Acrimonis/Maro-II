package ykws.android.maro.data.track

import ykws.android.maro.data.model.LatLng

/**
 * Result from the idle-threshold callback.
 *
 * @property entries            Marker snapshots captured at the idle position.
 * @property shouldOpenDrawer   True if the marker drawer should auto-open
 *                              (typically when snapshots are non-empty).
 */
data class IdleCaptureResult(
    val entries: List<MarkerSnapshot>,
    val shouldOpenDrawer: Boolean
)

/**
 * Callback invoked by [TrackRecorder] when the idle threshold is reached.
 *
 * TrackRecorder owns the timer and idle-detection logic. This fun interface
 * abstracts *what happens* at threshold — the recorder has zero knowledge of
 * markers, matching engines, or drawer UI state.
 *
 * Implemented in [MapScreen] (the composition root) which wires together
 * TrackViewModel → MarkersViewModel → MarkerMatcher.
 */
interface IdleThresholdCallback {
    /**
     * Called when idle persists >= threshold seconds.
     *
     * @param position  Boat position at the idle moment.
     * @return          Capture result with marker snapshots and drawer flag.
     */
    suspend fun onIdleThresholdReached(position: LatLng): IdleCaptureResult
}
