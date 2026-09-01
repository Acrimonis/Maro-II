package ykws.android.maro.data.track

import kotlinx.coroutines.Job

/**
 * Per-idle-period context — created on idle entry, consumed on exit.
 *
 * Tracks the auto-marker ID, open BoatMarker index, and drawer state
 * for the current idle session. Owned by [TrackRecorder].
 *
 * @property startTimeMs       Epoch millis when idle began.
 * @property entryLat          Boat latitude at idle entry.
 * @property entryLon          Boat longitude at idle entry.
 * @property boatMarkerIndex   Index into the track's boatMarkers list
 *                             for the open BoatMarker, or null.
 * @property drawerAutoOpened  True if the drawer was auto-opened during
 *                             this idle session.
 * @property autoMarkerId      ID of the temporary 🕐 pin, or null.
 * @property autoMarkerJob     In-flight createTemp job; joined before resolve.
 */
class IdleSessionContext(
    val startTimeMs: Long,
    val entryLat: Double,
    val entryLon: Double,
    var boatMarkerIndex: Int? = null,
    var drawerAutoOpened: Boolean = false,
    var autoMarkerId: String? = null,
    var boatMarkerMerged: Boolean = false,
    var autoMarkerJob: Job? = null
)
