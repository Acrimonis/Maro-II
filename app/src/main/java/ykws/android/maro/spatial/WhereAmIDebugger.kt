package ykws.android.maro.spatial

import ykws.android.maro.data.model.LatLng

// ─────────────────────────────────────────────────────────────────────────────
// WhereAmI visual debug — segment testing instrumentation
// ─────────────────────────────────────────────────────────────────────────────

/** A single boat→target line-of-sight test captured during [MarkerMatcher.closestUnblockedPoint]. */
data class DebugSegment(
    val boat: LatLng,
    val target: LatLng,
    val blocked: Boolean
)

/**
 * Contract for recording the segment tests one resolution makes.
 *
 * One instance belongs to one resolution — the caller passes it in and reads it back, so two
 * concurrent resolutions can no longer overwrite each other's capture.
 */
interface WhereAmIDebugger {
    fun onSegmentTested(boat: LatLng, target: LatLng, blocked: Boolean)

    /** The segments collected so far, in test order. */
    fun getSegments(): List<DebugSegment>
}

/**
 * No-op sink — the instance a caller passes when it wants no capture at all: the recorder and the
 * service resolutions, and the UI runs taken while the ray setting is off.
 */
object NoOpWhereAmIDebugger : WhereAmIDebugger {
    override fun onSegmentTested(boat: LatLng, target: LatLng, blocked: Boolean) {}
    override fun getSegments(): List<DebugSegment> = emptyList()
}

/** Stores every segment it is handed, for rendering on the map. */
class VisualWhereAmIDebugger : WhereAmIDebugger {
    private val _segments = mutableListOf<DebugSegment>()

    override fun onSegmentTested(boat: LatLng, target: LatLng, blocked: Boolean) {
        _segments.add(DebugSegment(boat, target, blocked))
    }

    override fun getSegments(): List<DebugSegment> = _segments.toList()
}
