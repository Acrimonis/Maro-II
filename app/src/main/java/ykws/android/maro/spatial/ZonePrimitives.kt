package ykws.android.maro.spatial

import ykws.android.maro.data.model.LatLng

/** The three zone kinds handled by the re-display primitives. */
enum class ZoneKind { BAND_300, SPEED, REGULATED }

/** Minimal zone shape the spatial index operates on (speed and non-speed both map to this). */
data class IndexedZone(
    val id: String,
    val name: String,
    val outerRing: List<LatLng>,
    val holes: List<List<LatLng>> = emptyList(),
    val speedLimitKn: Double?
)

/** "Where am I relative to this kind?" — no direction involved. */
data class ZoneStatus(
    val insideAny: Boolean,
    val nearestBoundaryM: Double?,
    val insideZones: List<IndexedZone> = emptyList(),
    val strictestSpeedKn: Double? = null
)

/** "First wall inside the forward cone?" */
data class BoundaryHit(
    val zone: IndexedZone,
    val kind: ZoneKind,
    val distanceM: Double,
    val boundaryPosition: LatLng?
)
