package ykws.android.maro.ui.map

import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.track.PointType
import ykws.android.maro.data.track.TrackPoint
import ykws.android.maro.spatial.SpatialOperations
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow

// ─────────────────────────────────────────────────────────────────────────────
// Inspect mode — the ranking core
//
// Inspect mode answers "what is this line near me?" by ranking the inspectable items by distance
// from the marker point. This file owns the whole of that ranking and nothing that paints it:
// the metric, the ordering, the hysteresis, the ladder and the radius derivation are pure Kotlin,
// so ring and gate read one derivation and both are unit-testable without a device.
//
// Two consumers, one metric (plan §3):
//  - the sweep needs only the nearest item → [nearest], an O(N) minimum scan per frame;
//  - the ladder needs the whole order → [rank], a full sort, run once at the pick.
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
 * One inspectable item: its id, what kind it is — the ladder's tie-break reads that — and the
 * geometry the metric measures. A marker's kind and its geometry are independent: a corridor is a
 * MARKER measured as a LINE.
 */
internal data class InspectCandidate(
    val id: String,
    val kind: InspectKind,
    val geometry: InspectGeometry
) {
    companion object {
        /** A pin, or a circle at its centre. */
        fun point(id: String, kind: InspectKind, point: LatLng): InspectCandidate =
            InspectCandidate(id, kind, InspectGeometry.Point(point))

        /** A polyline: a track, or a corridor's centre line. */
        fun line(id: String, kind: InspectKind, points: List<TrackPoint>): InspectCandidate =
            InspectCandidate(id, kind, InspectGeometry.Line(points))
    }
}

/** One ranked entry: the item's id, its kind and its exact distance from the anchor (m). */
internal data class InspectRank(
    val id: String,
    val kind: InspectKind,
    val distanceM: Double
)

/**
 * The inspect ranking: the metric, the order, the hysteresis and the ladder.
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
     */
    fun rank(anchor: LatLng, candidates: List<InspectCandidate>): List<InspectRank> =
        candidates
            .map { InspectRank(it.id, it.kind, distance(anchor, it)) }
            .sortedWith(compareBy({ it.distanceM }, { if (it.kind == InspectKind.MARKER) 0 else 1 }, { it.id }))

    /**
     * The sweep's O(N) minimum scan: the nearest candidate strictly inside [radiusM], or null when
     * nothing is in range — which is what clears the gold.
     */
    fun nearest(anchor: LatLng, candidates: List<InspectCandidate>, radiusM: Double): InspectRank? {
        var best: InspectRank? = null
        for (candidate in candidates) {
            val d = distance(anchor, candidate)
            if (d > radiusM) continue
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

    /**
     * The hysteresis winner: [current] keeps the gold unless [challenger] is closer by
     * [hysteresisPct] per cent, or [current] has itself left the radius. A null [challenger] clears
     * the candidate outright, which is the case that removes the gold.
     */
    fun winner(
        current: InspectRank?,
        challenger: InspectRank?,
        radiusM: Double,
        hysteresisPct: Float
    ): InspectRank? {
        if (challenger == null) return null
        if (current == null) return challenger
        if (current.id == challenger.id) return challenger
        // The incumbent has left the radius: the challenger takes the gold whatever the margin.
        if (current.distanceM > radiusM) return challenger
        val margin = (1.0 - hysteresisPct.coerceIn(0f, 100f) / 100.0)
        return if (challenger.distanceM < current.distanceM * margin) challenger else current
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// The movement gate (plan §5) — the clock may not run until the gesture has moved the map
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The movement gate: has the gesture that is running moved the map by at least [minMoveDp]?
 *
 * The number is the anchor's **own point** travelling across the screen, not the finger's travel:
 * [downX]/[downY] is where that point sat when the finger went down and [nowX]/[nowY] is where the
 * same point is projected now, so a pinch — which scales the map and carries every point but its
 * focus across the screen — counts through the same measurement as a pan, and a touch that leaves
 * the map exactly where it was cannot open the gate however long it rests. The comparison is in dp
 * ([density] px per dp), so the key reads in the same units as the rest of the map chrome.
 *
 * Pure and Android-free, like the rest of the ranking core, so the behaviour is unit-testable
 * without a device; the caller owns the two projections.
 */
internal fun inspectArmGateOpen(
    downX: Float,
    downY: Float,
    nowX: Float,
    nowY: Float,
    density: Float,
    minMoveDp: Float
): Boolean {
    val dx = nowX - downX
    val dy = nowY - downY
    return hypot(dx.toDouble(), dy.toDouble()) >= minMoveDp.coerceAtLeast(0f) * density
}

// ─────────────────────────────────────────────────────────────────────────────
// The one radius derivation (plan §2) — the drawn ring and the pick gate both read it
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The ring's radius in dp: the boat icon's own size at this zoom, times [factor], floored at
 * [minDp] and capped at [maxViewportPct] of the smaller viewport side.
 *
 * Always the boat formula — never the land dot's — so water and land cannot flip the range, and
 * the distance-to-shore multiplier is part of it so tightening near the coast is shared by ring
 * and gate alike.
 */
internal fun inspectRadiusDp(
    zoomLevel: Double,
    distMultiplier: Float,
    viewportMinDp: Float,
    factor: Float,
    minDp: Float,
    maxViewportPct: Float
): Float {
    val boatDp = BOAT_BASE_DP * 2.0.pow(ZOOM_EXPONENT * (zoomLevel - REF_ZOOM))
    val raw = boatDp * factor * distMultiplier
    val ceiling = maxViewportPct.coerceAtLeast(0f) * viewportMinDp
    return raw.toFloat().coerceIn(minDp, maxOf(minDp, ceiling))
}

/**
 * The distance-to-shore multiplier the boat icon itself wears: [DIST_SHRINK_MIN_MULT] on the coast,
 * ramping to 1.0 at [DIST_SHRINK_RAMP_M], and 1.0 whenever the shore distance is unknown. Read from
 * the marker's own constants so the ring can never breathe differently from the icon it circles.
 */
internal fun inspectDistMultiplier(distanceToShore: Double?): Float =
    if (distanceToShore == null) 1.0f
    else (DIST_SHRINK_MIN_MULT +
        (1.0 - DIST_SHRINK_MIN_MULT) * (distanceToShore / DIST_SHRINK_RAMP_M).coerceIn(0.0, 1.0)).toFloat()

/**
 * Ground resolution (m per screen pixel) at [latitude] and [zoomLevel] — the Web Mercator
 * resolution osmdroid's TileSystem computes, written here so the conversion stays pure and testable
 * rather than reaching into the library from the ranking.
 */
internal fun groundResolutionMetersPerPx(latitude: Double, zoomLevel: Double): Double {
    val clampedLat = latitude.coerceIn(-MER_MAX_LAT, MER_MAX_LAT)
    val mapSizePx = MER_TILE_SIZE_PX * 2.0.pow(zoomLevel)
    return cos(Math.toRadians(clampedLat)) * 2.0 * Math.PI * MER_EARTH_RADIUS_M / mapSizePx
}

/**
 * The ring's radius in metres — the dp value through the screen's own dp→px scale and the ground
 * resolution at the anchor. One function, so the drawn ring and the pick gate cannot drift.
 */
internal fun inspectRadiusMeters(
    radiusDp: Float,
    pxPerDp: Float,
    anchorLat: Double,
    zoomLevel: Double
): Double = pxPerDp.toDouble() * radiusDp.toDouble() * groundResolutionMetersPerPx(anchorLat, zoomLevel)

/** WGS84 mean radius used by the Web Mercator resolution (osmdroid's TileSystem radius). */
private const val MER_EARTH_RADIUS_M = 6_378_137.0
/** Tile edge in pixels — the Mercator map size is this times 2^zoom. */
private const val MER_TILE_SIZE_PX = 256.0
/** Mercator's own latitude clamp. */
private const val MER_MAX_LAT = 85.05112878

/** The track point's geo pair, so the metric never re-derives one. */
private fun TrackPoint.latLng(): LatLng = LatLng(lat, lon)
