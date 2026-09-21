package ykws.android.maro.spatial

import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.RoutePoint
import kotlin.math.min

/**
 * **The plan's own clock: the real seconds of the line the app draws.**
 *
 * A plan's time has one home, and it is the polyline on the screen. Every pass the search runs over
 * its chain — the collinear merge, the shortcut, the fillet — changes that line, so a time read off
 * the search's own accumulators describes a path that is no longer drawn: the heap key carries the
 * berth's courtesy, and a shortcut cuts seconds the search never priced. Reading the time from the
 * drawn polyline is what makes the trip figure, the confirm panel and the saved course agree to the
 * second, and it is the quantity the search asserts its own cost against.
 *
 * **The limits are the engine's, and the clock never asks where they came from.** One per drawn leg:
 * an engine that builds its line from a chain of priced edges inherits each leg's limit from the edge
 * that leg was cut from — the most restrictive one where a leg spans several, and the band exact
 * because the chain edge already carries its in-band mark — while an engine whose path is not a chain
 * produces limits of its own. Nothing here asks the live layer about a point: the sampled point query
 * that would stand in for it is the shape the trajectory study measured at 10,893 ms.
 *
 * The turn at each drawn corner is charged the way the search charges it, from [`RouteTurnGeometry`]
 * — the arc-minus-chord at that corner's own speed — so the plan's time is the search's own *model*
 * read off the drawn line rather than a second model that could disagree with it.
 */
object RoutePlanTiming {

    /**
     * The seconds a leg of [lengthM] takes at [limitKn]: its length over `min(cruise, limit)`, both in
     * metres per second.
     *
     * [Double.POSITIVE_INFINITY] when no speed is left, which is how a leg carrying
     * [`ykws.android.maro.spatial.mesh.RouteSearch.FORBIDDEN_LIMIT_KN`] reads: a closed leg is a cost
     * no route survives rather than a division by zero. The mesh search's own hot loop prices an edge
     * with the same arithmetic, and its `shortcutNoWorse` re-prices a candidate shortcut with this
     * function, so admission and reporting cannot drift apart on what a leg costs.
     */
    fun legSeconds(lengthM: Double, limitKn: Double, cruiseSpeedKn: Double): Double {
        val speedMps = min(cruiseSpeedKn, limitKn) * Units.MPS_PER_KNOT
        return if (speedMps > 0.0) lengthM / speedMps else Double.POSITIVE_INFINITY
    }

    /**
     * **The one function.** The drawn line's own seconds, one entry per drawn leg — each at the limit
     * [legLimitKn] names for it — with the turn at the corner a leg leaves charged to that leg, exactly
     * as the engine charged it. Summing the result is the plan's duration.
     *
     * [legLimitKn] is **an input the engine produces**, one value per drawn leg: the clock takes what
     * it is handed and holds no rule about where a limit came from, so an engine that prices its line
     * some other way answers through this same function.
     *
     * A leg with no limit named for it is priced at the cruise speed alone, which is what an empty
     * `legLimitKn` means and what the fillet's own callers pass when they price no limits at all.
     */
    fun drawnLegSeconds(
        points: List<RoutePoint>,
        legLimitKn: List<Double>,
        cruiseSpeedKn: Double,
        lateralAccelMps2: Double
    ): List<Double> {
        val count = (points.size - 1).coerceAtLeast(0)
        val seconds = ArrayList<Double>(count)
        for (i in 0 until count) {
            val limitKn = legLimitKn.getOrElse(i) { Double.MAX_VALUE }
            val legSec = legSeconds(metresBetween(points[i], points[i + 1]), limitKn, cruiseSpeedKn)
            val turnSec = if (i == 0 || !legSec.isFinite()) {
                0.0
            } else {
                RouteTurnGeometry.turnPriceSec(
                    min(cruiseSpeedKn, limitKn) * Units.MPS_PER_KNOT,
                    lateralAccelMps2,
                    RouteTurnGeometry.turnRadians(points[i - 1], points[i], points[i + 1])
                )
            }
            seconds.add(legSec + turnSec)
        }
        return seconds
    }

    /**
     * **The brake-and-accelerate pair a corner costs, in seconds** — §12.3's term, in the one home of
     * the clock.
     *
     * A corner is not only its arc: the boat must be at the corner's speed when it gets there, so it
     * brakes along the incoming leg and accelerates again along the outgoing one, and the distance that
     * costs is slower than the leg it stands on. The extra time is the difference between the time
     * actually spent and the time the same distance would have taken at the leg's own speed, which for a
     * constant deceleration `a` is `(v_in − v_corner)² / (2 · a · v_corner)` — and the same again on the
     * way out. Nothing is charged where the corner is not slower than its leg: the geometry gives the
     * speed, and this never invents a slowdown the corner does not force.
     *
     * It is the **second property the feature adds** (§12.3), read through
     * `AppConfig.routeTurnLongitudinalAccelMps2`, and it lives beside the clock rather than beside the
     * search because the ETA and the price must be the same number: a corner priced with it and timed
     * without it is exactly the disagreement the design's own resolution forbids.
     */
    fun longitudinalSec(
        inSpeedMps: Double,
        cornerSpeedMps: Double,
        outSpeedMps: Double,
        longitudinalAccelMps2: Double
    ): Double {
        if (longitudinalAccelMps2 <= 0.0 || cornerSpeedMps <= 0.0) return 0.0
        val braking = inSpeedMps - cornerSpeedMps
        val accelerating = outSpeedMps - cornerSpeedMps
        var seconds = 0.0
        if (braking > 0.0) {
            seconds += braking * braking / (2.0 * longitudinalAccelMps2 * cornerSpeedMps)
        }
        if (accelerating > 0.0) {
            seconds += accelerating * accelerating / (2.0 * longitudinalAccelMps2 * cornerSpeedMps)
        }
        return seconds
    }

    /**
     * **What the drawn clock will charge for one filleted corner** — the arc's own length at the limit
     * it inherits, plus the turn its chords make, in the same file as the clock it models.
     *
     * A fillet draws its arc as [chords] equal chords, each deflecting `turnRad / chords`, and
     * [`drawnLegSeconds`] charges the drawn corner of every one of them its own deflection's price off
     * the polyline it is handed. The fillet's own admission
     * ([`ykws.android.maro.spatial.mesh.RouteSearch.arcNoWorse`]) has to compare an arc against the
     * corner it would replace with **the very quantity the clock will charge**, so
     * its arithmetic lives here rather than beside its caller: a second spelling of it is a second
     * answer waiting to disagree, and this one is the price the drawn line is admitted on.
     *
     * **It is deliberately no cheaper than the line, which is what makes it safe to admit on.** The leg
     * term prices the whole arc where the drawing charges its chords, and a chord is shorter than the
     * arc it subtends, so the leg term can only over-charge. The turn term is [chords] whole
     * deflections where the drawing reads one per drawn corner and a half at each tangent point, and
     * `x ↦ x − 2·sin(x / 2)` is convex with `f(0) = 0`, so the whole deflections sum to more than the
     * halves do. An arc admitted on this price is therefore an arc the clock finds no dearer.
     */
    fun arcPriceSec(
        arcLengthM: Double,
        arcLimitKn: Double,
        cruiseSpeedKn: Double,
        lateralAccelMps2: Double,
        turnRad: Double,
        chords: Int
    ): Double {
        val count = if (chords > 0) chords else 1
        val arcSpeedMps = min(cruiseSpeedKn, arcLimitKn) * Units.MPS_PER_KNOT
        return legSeconds(arcLengthM, arcLimitKn, cruiseSpeedKn) +
            count * RouteTurnGeometry.turnPriceSec(arcSpeedMps, lateralAccelMps2, turnRad / count)
    }

    /** The geodesic metres between two drawn points — the length the clock is charged for. */
    private fun metresBetween(from: RoutePoint, to: RoutePoint): Double =
        SpatialOperations.haversine(
            LatLng(from.latitude, from.longitude),
            LatLng(to.latitude, to.longitude)
        )
}
