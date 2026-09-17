package ykws.android.maro.ui.map

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.map
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.track.PointType
import ykws.android.maro.data.track.TrackPoint
import ykws.android.maro.spatial.SpatialOperations

// ─────────────────────────────────────────────────────────────────────────────
// Inspect mode — the ranking core
//
// Inspect mode answers "what is this line near me?" by ranking the inspectable items by distance
// from the marker point. This file owns the whole of that ranking and nothing that paints it:
// the metric, the viewport test, the ordering, the ladder and the sweep pipeline are pure Kotlin,
// so the whole behaviour is unit-testable without a device.
//
// Two consumers, one metric (plan §2, §3):
//  - the sweep needs only the nearest eligible item → [nearest], an O(N) minimum scan per frame over
//    the candidates whose cached bbox overlaps the viewport;
//  - the ladder needs the whole order → [rank], a full sort, run once at the pick and deliberately
//    not viewport-filtered, so a Next may walk to an off-screen item.
// Both read the same loaded geometry, so the gold is always the ladder's first entry.
// ─────────────────────────────────────────────────────────────────────────────

/** What kind of item a candidate is. The ordering's tie-break reads it — markers before tracks. */
internal enum class InspectKind { MARKER, TRACK }

/**
 * What is measured: a point, or a line. Nothing else — no shape contributes inside-ness.
 *
 * The geometry is measured exactly and once. A pin is its position, a circle its centre; a stored
 * track is its polyline and a corridor its centre line, both segment-wise and GAP-aware. A
 * corridor's band width and a circle's radius are ignored, so being *inside* a zone never yields 0
 * and no zone can own the ladder merely by containing the marker point (plan §3).
 */
internal sealed interface InspectGeometry {

    /** A single point: a pin at its position, a circle at its centre. */
    data class Point(val point: LatLng) : InspectGeometry

    /** A line: a track's polyline or a corridor's centre line. */
    data class Line(val points: List<TrackPoint>) : InspectGeometry
}

/**
 * A rectangle in degrees, edges inclusive.
 *
 * The inspect mode's eligibility test is one of these against the viewport: overlap first, distance
 * second, so a nearer item off screen loses to a farther one on it (plan §2). A candidate's own box
 * is derived once at warm time, so the per-frame test is a rectangle comparison and never a
 * geometry walk. Pure and Android-free: the caller converts the projection's own bounds into one.
 */
internal data class InspectBounds(
    val north: Double,
    val east: Double,
    val south: Double,
    val west: Double
) {

    /** True when the two rectangles share ground, an edge alone counting as shared. */
    fun overlaps(other: InspectBounds): Boolean =
        south <= other.north && other.south <= north && west <= other.east && other.west <= east

    companion object {

        /** A point's own degenerate box. */
        fun of(point: LatLng): InspectBounds =
            InspectBounds(point.latitude, point.longitude, point.latitude, point.longitude)

        /**
         * The extent of a line's stored points — its own box, seams included, since this is a
         * containment test and not a measurement. An empty list yields an inverted box, which
         * overlaps nothing; the warm pass's own non-empty rule makes that case unreachable.
         */
        fun of(points: List<TrackPoint>): InspectBounds {
            var north = Double.NEGATIVE_INFINITY
            var east = Double.NEGATIVE_INFINITY
            var south = Double.POSITIVE_INFINITY
            var west = Double.POSITIVE_INFINITY
            for (point in points) {
                if (point.lat > north) north = point.lat
                if (point.lat < south) south = point.lat
                if (point.lon > east) east = point.lon
                if (point.lon < west) west = point.lon
            }
            return InspectBounds(north, east, south, west)
        }
    }
}

/**
 * One inspectable item: its id, what kind it is — the ladder's tie-break reads that — the geometry
 * the metric measures, and the bbox that decides eligibility.
 *
 * A marker's kind and its geometry are independent: a corridor is a MARKER measured as a LINE. The
 * bbox follows the geometry: a point's own position, or a line's extent, both derived here so warm
 * time is the only time the geometry is walked for it (plan §2).
 */
internal data class InspectCandidate(
    val id: String,
    val kind: InspectKind,
    val geometry: InspectGeometry,
    val bounds: InspectBounds
) {
    companion object {
        /** A pin, or a circle at its centre. */
        fun point(id: String, kind: InspectKind, point: LatLng): InspectCandidate =
            InspectCandidate(id, kind, InspectGeometry.Point(point), InspectBounds.of(point))

        /** A polyline: a track, or a corridor's centre line. */
        fun line(id: String, kind: InspectKind, points: List<TrackPoint>): InspectCandidate =
            InspectCandidate(id, kind, InspectGeometry.Line(points), InspectBounds.of(points))
    }
}

/** One ranked entry: the item's id, its kind and its exact distance from the anchor (m). */
internal data class InspectRank(
    val id: String,
    val kind: InspectKind,
    val distanceM: Double
)

/**
 * The inspect ranking: the metric, the viewport test, the order and the ladder.
 *
 * Every function here is pure and side-effect free; the sweep and the ladder both go through
 * [distance], so the two can never disagree about how far an item is.
 */
internal object InspectRanking {

    /**
     * Exact distance (m) from [anchor] to [candidate].
     *
     * A marker measures to its point. A track measures to its nearest segment, pairwise and
     * resuming after every GAP: a seam between two recorded fragments is not a line the user
     * drew, so the metric never measures across it. A single-point track measures to its point.
     */
    fun distance(anchor: LatLng, candidate: InspectCandidate): Double =
        when (val geometry = candidate.geometry) {
            is InspectGeometry.Point -> SpatialOperations.haversine(anchor, geometry.point)
            is InspectGeometry.Line -> lineDistance(anchor, geometry.points)
        }

    private fun lineDistance(anchor: LatLng, points: List<TrackPoint>): Double {
        if (points.isEmpty()) return Double.MAX_VALUE
        if (points.size == 1) return SpatialOperations.haversine(anchor, points[0].latLng())
        var best = Double.MAX_VALUE
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            // GAP-aware: a seam is a break in the stored line, never a segment to measure against.
            if (a.type == PointType.GAP || b.type == PointType.GAP) continue
            val d = SpatialOperations.pointToSegmentDistance(anchor, a.latLng(), b.latLng())
            if (d < best) best = d
        }
        // A line whose every segment spans a seam still has its own points to measure to, so fall
        // back to the nearest vertex rather than reporting "infinitely far".
        if (best == Double.MAX_VALUE) {
            for (p in points) {
                val d = SpatialOperations.haversine(anchor, p.latLng())
                if (d < best) best = d
            }
        }
        return best
    }

    /**
     * The full ladder over [candidates], ascending by distance, ties broken markers before tracks
     * and then by id ascending — deterministic, so the walk is reproducible and testable.
     *
     * Deliberately **not** viewport-filtered (plan §5): the ladder is the walk world a Next steps
     * through, and a step may land on an item the screen has since left behind.
     */
    fun rank(anchor: LatLng, candidates: List<InspectCandidate>): List<InspectRank> =
        candidates
            .map { InspectRank(it.id, it.kind, distance(anchor, it)) }
            .sortedWith(compareBy({ it.distanceM }, { if (it.kind == InspectKind.MARKER) 0 else 1 }, { it.id }))

    /**
     * The sweep's O(N) minimum scan: the closest candidate whose own box overlaps [viewport], or
     * null when the viewport holds none — which is what clears the gold and lets the scan stand
     * idle until the map moves again.
     *
     * Overlap first and distance second, so the answer is never an item the screen does not show,
     * and no incumbent is held: the true closest of this scan wins on every re-rank (plan §2, §3).
     */
    fun nearest(anchor: LatLng, candidates: List<InspectCandidate>, viewport: InspectBounds): InspectRank? {
        var best: InspectRank? = null
        for (candidate in candidates) {
            if (!viewport.overlaps(candidate.bounds)) continue
            val d = distance(anchor, candidate)
            val current = best
            if (current == null ||
                d < current.distanceM ||
                (d == current.distanceM && beatsOnTieBreak(candidate, current))
            ) {
                best = InspectRank(candidate.id, candidate.kind, d)
            }
        }
        return best
    }

    private fun beatsOnTieBreak(candidate: InspectCandidate, current: InspectRank): Boolean =
        when {
            candidate.kind != current.kind -> candidate.kind == InspectKind.MARKER
            else -> candidate.id < current.id
        }
}

// ─────────────────────────────────────────────────────────────────────────────
// The sweep pipeline (plan §3) — conflate stale ticks, never cancel a scan
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The sweep's pipeline over [ticks]: stale motion ticks are conflated, and a scan already in flight
 * is **never** cancelled, because the collector is a plain one — `collect`, not `collectLatest`.
 *
 * So every tick that survives the conflation publishes its own result, and the previous highlight
 * stands until its successor lands; no result is ever dropped in favour of a newer tick. The
 * conflation is what keeps a fast drag from queueing a scan per frame, and the reason each scan is
 * a cheap O(N) minimum over warm geometry off the UI thread.
 *
 * Android-free, so the conflate-and-never-cancel behaviour is unit-tested without a device. [scan]
 * takes the tick it was started for, which is the caller's own bookkeeping rather than an input to
 * the scan itself.
 */
internal fun <T> inspectSweep(ticks: Flow<Int>, scan: suspend (Int) -> T): Flow<T> =
    ticks.conflate().map { scan(it) }

/** The track point's geo pair, so the metric never re-derives one. */
private fun TrackPoint.latLng(): LatLng = LatLng(lat, lon)
