package ykws.android.maro.ui.map

import ykws.android.maro.config.PathClass
import ykws.android.maro.config.PathKind
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.data.track.PointType
import ykws.android.maro.data.track.TrackPoint
import ykws.android.maro.data.track.deriveSpeedMps
import ykws.android.maro.spatial.SpatialOperations
import ykws.android.maro.spatial.Units
import ykws.android.maro.data.model.LatLng

/**
 * **The seam is a value type, not an interface (D1).** The painter needs data, not polymorphism, so a
 * neutral [RenderPoint] and one [LineRenderSpec] are adapted to at the UI edge; `RoutePoint` and
 * `TrackPoint` stay pure domain models and implement nothing.
 *
 * A point of a line the engine draws. The speed is resolved at the adapter — a stored speed wins, a
 * derivable one is derived there (from a track's timestamps, or a route's leg times), and a value that
 * is still absent stays null, which the ramp paints as its neutral tint. A GAP seam is a break, whose
 * own speed is null for the same reason.
 */
internal data class RenderPoint(
    val lat: Double,
    val lon: Double,
    val speedKn: Float? = null,
    val bearingDeg: Float? = null,
    val isBreak: Boolean = false
)

/**
 * One line to paint: its **kind** (which `path.track.*` / `path.route.*` prefix it reads), its
 * **class** (the role it plays, if any), its points, whether its whole stroke is dashed, and whether it
 * draws chevrons. The painter reads this and nothing else.
 */
internal data class LineRenderSpec(
    val kind: PathKind,
    val pathClass: PathClass?,
    val points: List<RenderPoint>,
    val dashed: Boolean = false,
    val drawArrows: Boolean = false
)

/**
 * The stored speed of a [TrackPoint] in knots, or null when it carries none. The derivation is the
 * adapter's business, never the painter's, because it needs the domain's own timestamps.
 */
private fun storedSpeedKn(point: TrackPoint): Float? =
    point.speedMps?.let { Units.mpsToKnots(it.toDouble()).toFloat() }

/**
 * A recorded track's points as the seam's values: the stored speed wins, an absent one is derived from
 * the neighbouring timestamps, and a GAP seam is a break whose speed stays null.
 */
@JvmName("trackPointsToRenderPoints")
internal fun List<TrackPoint>.toRenderPoints(): List<RenderPoint> = mapIndexed { index, point ->
    if (point.type == PointType.GAP) {
        RenderPoint(point.lat, point.lon, speedKn = null, bearingDeg = point.bearingDeg, isBreak = true)
    } else {
        val speed = storedSpeedKn(point)
            ?: deriveSpeedMps(this, index)?.let { Units.mpsToKnots(it.toDouble()).toFloat() }
        RenderPoint(point.lat, point.lon, speed, point.bearingDeg, isBreak = false)
    }
}

/**
 * A bare route polyline as the seam's values — no speed, no bearing: a provisional line is geometry
 * alone, so its chevrons (if any) read the floor and its ramp paints the neutral tint.
 */
@JvmName("routePointsToRenderPoints")
internal fun List<RoutePoint>.toRenderPoints(): List<RenderPoint> =
    map { RenderPoint(it.latitude, it.longitude) }

/**
 * A resolved route plan as the seam's values, with a **per-point speed derived from the leg times**
 * (S10): a leg of a plan is timed, so the speed over it is its haversine length over its seconds, and
 * the point leaving a leg carries that leg's speed. The last point carries the last leg's speed, and a
 * plan whose legs do not line up (a partial or draft plan, or a zero-length leg) leaves the speed null,
 * which the ramp paints neutral rather than wrong.
 */
internal fun toRenderPoints(
    points: List<RoutePoint>,
    legTimesSec: List<Double>
): List<RenderPoint> {
    if (points.isEmpty()) return emptyList()
    val legSpeeds = ArrayList<Float?>(points.size)
    for (index in points.indices) {
        val to = index + 1
        val speed = if (to < points.size) {
            legSpeedKn(points[index], points[to], legTimesSec.getOrElse(index) { 0.0 })
        } else {
            // The last point reads the leg arriving at it, which is the leg before it.
            legSpeeds.lastOrNull() ?: null
        }
        legSpeeds += speed
    }
    return points.mapIndexed { index, point ->
        RenderPoint(point.latitude, point.longitude, speedKn = legSpeeds.getOrNull(index))
    }
}

/** One leg's speed in knots: its haversine length over its seconds, or null where either is unusable. */
private fun legSpeedKn(a: RoutePoint, b: RoutePoint, seconds: Double): Float? {
    if (seconds <= 0.0) return null
    val metres = SpatialOperations.haversine(
        LatLng(a.latitude, a.longitude),
        LatLng(b.latitude, b.longitude)
    )
    if (metres <= 0.0) return null
    return Units.mpsToKnots(metres / seconds).toFloat()
}
