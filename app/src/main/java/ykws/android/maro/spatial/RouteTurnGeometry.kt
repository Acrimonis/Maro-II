package ykws.android.maro.spatial

import ykws.android.maro.data.model.RoutePoint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * **The one home of the turn's own geometry.**
 *
 * A corner carries exactly four numbers that every pass has to agree on: the **angle** the deflection
 * makes, the **radius** the comfort cap implies at a speed, the **cutback** the legs allow, and the
 * **centre** the arc is drawn about. Three copies of them existed — in [`RouteFillet`], in the mesh
 * search (`ykws.android.maro.spatial.mesh.RouteSearch`) and in the bake-time trajectory probe — and
 * the copies disagreed, which is how a fillet that had never once drawn a curve passed a green suite.
 *
 * For a deflection `θ` at a vertex the fillet's circle is tangent to both legs `R · tan(θ / 2)` back
 * along each of them, and its centre stands `R / cos(θ / 2)` from the vertex on the corner's bisector.
 * The fillet built the centre at `R / sin(θ / 2)` and the tangent point at `R / tan(θ / 2)`; the two
 * mistakes cancel at a right angle — where `sin`, `cos` and `tan` of 45° all read 1 — and nowhere
 * else, so a suite whose every case sat at 90° could not see it. Every caller reads this file now.
 *
 * The angle is read in the **vertex's own frame**, and that matters: measuring a leg in the frame of
 * the point beyond it disagrees with the vertex's reading by about `tan(vertexLat) × leg`, a
 * milliradian over a 500 m leg. The arc is built in that same frame, so the sweep it computes and the
 * turn it is compared with must come from the same place.
 */
object RouteTurnGeometry {

    /**
     * A tangent point may take at most this share of either leg it cuts back along.
     *
     * It is what keeps two neighbouring arcs from meeting in the middle of a leg: at most 0.45 of a
     * leg from each of its two ends still leaves a tenth of it straight.
     */
    const val MAX_CUTBACK_FRACTION = 0.45

    /** Below this heading change a vertex is not a corner at all — the search's merge reading too. */
    val MIN_TURN_RAD = 2.0 * PI / 180.0

    /** Above this the turn is a reversal, which no single arc should be asked to round. */
    val MAX_TURN_RAD = 170.0 * PI / 180.0

    /** The largest radius the legs allow at a corner: `R · tan(θ / 2) ≤ cutLimitM(L)`. */
    fun cutLimitM(shorterLegM: Double): Double = MAX_CUTBACK_FRACTION * shorterLegM

    /** The radius the cap implies at a speed: `R = v² / a`. */
    fun radiusM(speedMps: Double, lateralAccelMps2: Double): Double =
        speedMps * speedMps / lateralAccelMps2

    /**
     * The largest radius a corner can take at all, the cap aside — the cutback rule read backwards:
     * `R ≤ cutLimitM(L) / tan(θ / 2)`. A cap whose ideal radius is above this can never be met there,
     * whatever it is set to, which is what separates "the cap refused" from "the legs had no room".
     */
    fun radiusWithinCutbackM(shorterLegM: Double, turnRad: Double): Double =
        cutLimitM(shorterLegM) / tan(turnRad / 2.0)

    /** The tangent point's distance from the vertex, along each leg: `R · tan(θ / 2)`. */
    fun cutM(radiusM: Double, turnRad: Double): Double = radiusM * tan(turnRad / 2.0)

    /** The arc centre's distance from the vertex, along the bisector: `R / cos(θ / 2)`. */
    fun centreDistanceM(radiusM: Double, turnRad: Double): Double = radiusM / cos(turnRad / 2.0)

    /** The deflection (rad) at [v], between the leg a→v and the leg v→b. */
    fun turnRadians(a: RoutePoint, v: RoutePoint, b: RoutePoint): Double =
        turnRadians(a.latitude, a.longitude, v.latitude, v.longitude, b.latitude, b.longitude)

    /**
     * The same deflection from raw positions, so the search's hot loop needs no [RoutePoint] to ask
     * for it — an allocation per edge relaxation is exactly what the packed-array search avoids.
     */
    fun turnRadians(
        aLat: Double,
        aLon: Double,
        vLat: Double,
        vLon: Double,
        bLat: Double,
        bLon: Double
    ): Double {
        val metresPerDegreeLat = SpatialOperations.EARTH_RADIUS_M * PI / 180.0
        val metresPerDegreeLon = metresPerDegreeLat * cos(vLat * PI / 180.0)
        val ax = (aLon - vLon) * metresPerDegreeLon
        val ay = (aLat - vLat) * metresPerDegreeLat
        val bx = (bLon - vLon) * metresPerDegreeLon
        val by = (bLat - vLat) * metresPerDegreeLat
        if (ax == 0.0 && ay == 0.0) return 0.0
        if (bx == 0.0 && by == 0.0) return 0.0
        // North-up compass headings: the leg arriving (a→v) and the leg leaving (v→b).
        val incoming = atan2(-ax, -ay)
        val outgoing = atan2(bx, by)
        return abs(normalisedRadians(outgoing - incoming))
    }

    /**
     * The seconds a turn costs, as the search prices it: the **arc-minus-chord** of the arc that will
     * replace the corner, `(v / a) · (θ − 2 · sin(θ / 2))`.
     *
     * It is a **price, not the geometry**: the two legs the fillet cuts total `2 · R · tan(θ / 2)`
     * from the vertex to the tangent points while the arc is `R · θ`, so the drawn line is *shorter*
     * than the corner it replaces by `R · (2 · tan(θ / 2) − θ)` — inserting an arc never lengthens a
     * line. Charging that difference would make a turn cheaper than free, so the search charges the
     * arc-minus-chord instead: the extra distance the arc's own chord costs, never negative, which is
     * what the heuristic's admissibility rests on.
     */
    fun turnPriceSec(speedMps: Double, lateralAccelMps2: Double, turnRad: Double): Double {
        if (speedMps <= 0.0 || lateralAccelMps2 <= 0.0 || turnRad <= 0.0) return 0.0
        return speedMps / lateralAccelMps2 * (turnRad - 2.0 * sin(turnRad / 2.0))
    }

    private fun normalisedRadians(angle: Double): Double {
        var a = angle
        while (a > PI) a -= 2.0 * PI
        while (a < -PI) a += 2.0 * PI
        return a
    }
}
