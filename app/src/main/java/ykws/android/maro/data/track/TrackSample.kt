package ykws.android.maro.data.track

import ykws.android.maro.data.model.LatLng

/**
 * A lightweight position sample for track recording, carrying the essential fields
 * needed by [TrackRecorder] to build [TrackPoint] instances.
 *
 * Replaces the virtual [ykws.android.maro.data.location.GpsFix] reconstruction in
 * [ykws.android.maro.ui.map.MapScreen] — no more round-trip through a data type
 * designed for the raw GPS provider flow.
 */
data class TrackSample(
    val position: LatLng,
    val speedMps: Float?,
    val bearingDeg: Float?,
    val hasLock: Boolean,
    val timestampEpochMs: Long
)
