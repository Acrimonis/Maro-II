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

/** Contract for recording segment tests during WhereAmI resolution. */
interface WhereAmIDebugger {
    fun beginCapture(markerName: String)
    fun onSegmentTested(boat: LatLng, target: LatLng, blocked: Boolean)
    fun endCapture(keep: Boolean)
    fun getSegments(): List<DebugSegment>
    fun clear()
}

/** No-op debugger — zero overhead when visual debugging is off. */
object NoOpWhereAmIDebugger : WhereAmIDebugger {
    override fun beginCapture(markerName: String) {}
    override fun onSegmentTested(boat: LatLng, target: LatLng, blocked: Boolean) {}
    override fun endCapture(keep: Boolean) {}
    override fun getSegments(): List<DebugSegment> = emptyList()
    override fun clear() {}
}

/** Stores every tested segment in a mutable list for later rendering on the map.
 *  Segments are only kept when [endCapture] is called with keep=true. */
class VisualWhereAmIDebugger : WhereAmIDebugger {
    private val _segments = mutableListOf<DebugSegment>()
    private var _capturing = false
    private var _captureStart = 0

    override fun beginCapture(markerName: String) {
        _capturing = true
        _captureStart = _segments.size
    }

    override fun onSegmentTested(boat: LatLng, target: LatLng, blocked: Boolean) {
        if (_capturing) {
            _segments.add(DebugSegment(boat, target, blocked))
        }
    }

    override fun endCapture(keep: Boolean) {
        _capturing = false
        // Always keep segments for visual debugging
    }

    override fun getSegments(): List<DebugSegment> = _segments.toList()

    override fun clear() {
        _segments.clear()
        _capturing = false
    }
}
