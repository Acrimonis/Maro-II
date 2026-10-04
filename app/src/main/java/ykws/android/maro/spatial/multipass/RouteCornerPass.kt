package ykws.android.maro.spatial.multipass

import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.spatial.SpatialOperations
import ykws.android.maro.spatial.Units
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * **The corner pass (geometry)** — the settled search line's snapped corners rounded into a racing
 * line: a single bend whose curvature peaks at the corner and tapers to zero, never inverting sign.
 *
 * Each corner becomes a clothoid → arc → clothoid: the lateral G ramps up from the straight leg to its
 * maximum at the apex, holds it across a constant-radius arc, and ramps back to straight. The boat
 * leans one way only, easing to straight at both ends.
 *
 * The radius is fixed by the reach: the curve reaches a fraction ([AppConfig.routeTurnReachFraction])
 * of the shorter half-segment along each leg, which sets the widest radius that fits and, through
 * `v ≤ √(a_lat · r)`, the corner speed. The clothoid length is `v · route.turn.transitionSec`. The
 * clearance test (coast, depth, zone) then shrinks the radius where the inward bow would foul, and a
 * corner no radius clears even at `route.turn.minSpeedKn` stays sharp.
 *
 * The pass emits the drawn polyline and, for every curve point, the corner speed it may not exceed —
 * the ceiling the speed pass then reads beside the enforced limit.
 */
object RouteCornerPass {

    /** The clothoid's Fresnel argument runs at the heading's half-rate, so the pair is read at `τ/√2`. */
    private val SQRT2 = sqrt(2.0)

    /** A corner worth rounding turns by at least this, in degrees. */
    private const val MIN_TURN_DEG = 10.0

    /** Below this tangent length (m) the legs are too short to round at all. */
    private const val MIN_TANGENT_M = 2.0

    /** The drawn, rounded line and the curvature ceiling each point carries (kn, or null). */
    data class RoundedLine(
        val points: List<LatLng>,
        val ceilingKnAt: (LatLng) -> Double?
    )

    /** Rounds every corner of [line] and returns the drawn polyline with its curvature ceilings. */
    fun round(
        line: List<LatLng>,
        paceKn: Double,
        world: MultipassWorld,
        depthGateActive: Boolean,
        minDepthM: Double,
        marginM: Double
    ): RoundedLine {
        if (line.size < 3) return RoundedLine(line) { null }
        val paceMps = Units.knotsToMps(paceKn)
        val aLat = AppConfig.routeTurnLateralAccelMps2
        val vFloorMps = Units.knotsToMps(AppConfig.routeTurnMinSpeedKn)
        val transitionSec = AppConfig.routeTurnTransitionSec
        val reachFraction = AppConfig.routeTurnReachFraction

        val out = ArrayList<LatLng>(line.size + 16)
        val ceilings = HashMap<LatLng, Double>()
        out.add(line.first())
        for (i in 1 until line.lastIndex) {
            val at = line[i]
            val prev = line[i - 1]
            val next = line[i + 1]
            // How far the previous emitted point already stands from this corner, so the new bow never
            // overlaps the one before it.
            val emittedIn = SpatialOperations.haversine(out.last(), at)
            val rounded = roundCorner(
                prev, at, next, emittedIn, paceMps, aLat, vFloorMps, transitionSec, reachFraction,
                world, depthGateActive, minDepthM, marginM
            )
            if (rounded == null) {
                if (out.last() != at) out.add(at)
                ceilings[at] = min(Units.mpsToKnots(vFloorMps), paceKn)
            } else {
                val ceilingKn = Units.mpsToKnots(rounded.cornerSpeedMps)
                for (p in rounded.points) {
                    if (out.last() != p) out.add(p)
                    ceilings[p] = ceilingKn
                }
            }
        }
        if (out.last() != line.last()) out.add(line.last())
        return RoundedLine(out, { p -> ceilings[p] })
    }

    private data class RoundedCorner(val points: List<LatLng>, val cornerSpeedMps: Double)

    private fun roundCorner(
        prev: LatLng,
        at: LatLng,
        next: LatLng,
        emittedIn: Double,
        paceMps: Double,
        aLat: Double,
        vFloorMps: Double,
        transitionSec: Double,
        reachFraction: Double,
        world: MultipassWorld,
        depthGateActive: Boolean,
        minDepthM: Double,
        marginM: Double
    ): RoundedCorner? {
        val frame = Frame(at)
        val uIn = frame.direction(prev, at) ?: return null
        val uOut = frame.direction(at, next) ?: return null
        val cosDelta = (uIn.x * uOut.x + uIn.y * uOut.y).coerceIn(-1.0, 1.0)
        val delta = acos(cosDelta)
        if (delta < Math.toRadians(MIN_TURN_DEG)) return null
        val sinDelta = sin(delta)
        if (sinDelta < 1e-6) return null
        val cross = uIn.x * uOut.y - uIn.y * uOut.x
        if (abs(cross) < 1e-9) return null
        val turnSign = if (cross > 0.0) 1.0 else -1.0
        val signedDelta = turnSign * delta

        val legIn = SpatialOperations.haversine(prev, at)
        val legOut = SpatialOperations.haversine(at, next)
        // The reach: a fraction of the shorter half-segment, never overlapping the previous bow.
        val halfSegment = min(legIn, legOut) / 2.0
        val tTarget = min(reachFraction * halfSegment, emittedIn)
        if (tTarget < MIN_TANGENT_M) return null

        val lo = vFloorMps

        fun rAndL(v: Double): Pair<Double, Double> {
            val r = v * v / aLat
            // The comfort taper, capped so the two clothoids never exceed the turn: below the cap the
            // curve is spiral-only and the apex just touches the lateral-G max.
            val l = min(v * transitionSec, r * delta)
            return r to l
        }

        fun tangentOf(v: Double): Double {
            val (r, l) = rAndL(v)
            return composite(r, l, signedDelta).tangent
        }

        fun build(v: Double): List<LatLng> {
            val (r, l) = rAndL(v)
            val c = composite(r, l, signedDelta)
            val pts = ArrayList<LatLng>(c.points.size)
            for (p in c.points) {
                val x = p.first
                val y = p.second
                val ux = uIn.x * x - uIn.y * y
                val uy = uIn.y * x + uIn.x * y
                pts.add(frame.toLatLng(-uIn.x * c.tangent + ux, -uIn.y * c.tangent + uy))
            }
            return pts
        }

        val cornerInZone = world.zoneLimitKnAt(at.latitude, at.longitude) != null
        fun clearPoint(p: LatLng): Boolean {
            if (world.distanceToCoastM(p.latitude, p.longitude) < marginM - 1e-6) return false
            if (!depthClearsGate(world.depthAt(p.latitude, p.longitude), depthGateActive, minDepthM)) return false
            if (!cornerInZone && world.zoneLimitKnAt(p.latitude, p.longitude) != null) return false
            return true
        }
        fun clears(v: Double): Boolean {
            val pts = build(v)
            for (i in pts.indices) {
                if (!clearPoint(pts[i])) return false
                if (i > 0) {
                    val a = pts[i - 1]
                    val mid = LatLng(
                        (a.latitude + pts[i].latitude) / 2.0,
                        (a.longitude + pts[i].longitude) / 2.0
                    )
                    if (!clearPoint(mid)) return false
                }
            }
            return true
        }

        // The largest speed whose bow still fits the reach.
        var v = paceMps
        if (tangentOf(paceMps) > tTarget) {
            if (tangentOf(lo) > tTarget) return null
            var a = lo
            var b = paceMps
            repeat(24) {
                val mid = (a + b) / 2.0
                if (tangentOf(mid) <= tTarget) a = mid else b = mid
            }
            v = a
        }
        // Shrink where the inward bow would foul.
        if (!clears(v)) {
            if (!clears(lo)) return null
            var a = lo
            var b = v
            repeat(24) {
                val mid = (a + b) / 2.0
                if (clears(mid)) a = mid else b = mid
            }
            v = a
        }
        return RoundedCorner(build(v), v)
    }

    /** The single-bend curve in a canonical frame: start (0,0) heading 0, end heading [signedDelta]. */
    private data class Composite(val points: List<Pair<Double, Double>>, val tangent: Double)

    private fun composite(r: Double, l: Double, signedDelta: Double): Composite {
        // The clothoid's own argument: the heading runs at `h = τ²/2`, so the position integrates
        // `∫cos(σ²/2)dσ` and `∫sin(σ²/2)dσ` — the Fresnel pair at `τ/√2`, scaled by `√2`. Evaluating
        // them at `τ` outright bows the spiral twice as sharply as its own heading, and the too-deep bow
        // is what made the fit reject corners a true clothoid fits.
        val pts = ArrayList<Pair<Double, Double>>(96)
        var x = 0.0
        var y = 0.0
        var h = 0.0
        val sign = if (signedDelta >= 0.0) 1.0 else -1.0
        val thetaC = l / (2.0 * r)
        val thetaArc = abs(signedDelta) - 2.0 * thetaC

        fun appendSpiral(kEnd: Double, steps: Int) {
            val a = sqrt(r * l)
            val h0 = h
            val sgn = if (kEnd > 0.0) 1.0 else -1.0
            for (k in 1..steps) {
                val s = l * k / steps
                val tau = s / a
                val lx = a * SQRT2 * fresnelC(tau / SQRT2)
                val ly = sgn * a * SQRT2 * fresnelS(tau / SQRT2)
                val gx = x + lx * cos(h0) - ly * sin(h0)
                val gy = y + lx * sin(h0) + ly * cos(h0)
                pts.add(gx to gy)
            }
            val tauEnd = l / a
            val lxEnd = a * SQRT2 * fresnelC(tauEnd / SQRT2)
            val lyEnd = sgn * a * SQRT2 * fresnelS(tauEnd / SQRT2)
            x += lxEnd * cos(h0) - lyEnd * sin(h0)
            y += lxEnd * sin(h0) + lyEnd * cos(h0)
            h += sgn * tauEnd * tauEnd / 2.0
        }

        fun appendArc(kappa: Double, sweep: Double, steps: Int) {
            val radius = 1.0 / abs(kappa)
            val sgn = if (kappa > 0.0) 1.0 else -1.0
            val cx = x - sin(h) * radius * sgn
            val cy = y + cos(h) * radius * sgn
            val theta0 = atan2(y - cy, x - cx)
            for (k in 1..steps) {
                val frac = k.toDouble() / steps
                val theta = theta0 + sweep * frac
                pts.add((cx + radius * cos(theta)) to (cy + radius * sin(theta)))
            }
            x = cx + radius * cos(theta0 + sweep)
            y = cy + radius * sin(theta0 + sweep)
            h += sweep
        }

        appendSpiral(sign / r, 16)
        if (thetaArc > 0.0) appendArc(sign / r, sign * thetaArc, 16)
        appendSpiral(sign / r, 16)

        val tangent = x - y * cos(signedDelta) / sin(signedDelta)
        return Composite(pts, tangent)
    }

    /** Fresnel cosine integral C(t), by its power series. */
    private fun fresnelC(t: Double): Double {
        var term = t
        var sum = t
        var n = 0
        while (n < 16) {
            val k = 4.0 * n + 1.0
            term *= -t * t * t * t * k / ((k + 4.0) * (2.0 * n + 2.0) * (2.0 * n + 1.0))
            sum += term
            n++
            if (abs(term) < 1e-15) break
        }
        return sum
    }

    /** Fresnel sine integral S(t), by its power series. */
    private fun fresnelS(t: Double): Double {
        var term = t * t * t / 6.0
        var sum = term
        var n = 0
        while (n < 16) {
            val k = 4.0 * n + 3.0
            term *= -t * t * t * t * k / ((k + 4.0) * (2.0 * n + 3.0) * (2.0 * n + 2.0))
            sum += term
            n++
            if (abs(term) < 1e-15) break
        }
        return sum
    }

    /** A local metric frame centred on one corner: x east (m), y north (m). */
    private class Frame(val at: LatLng) {
        val mLat = SpatialOperations.EARTH_RADIUS_M * Math.PI / 180.0
        val mLon = mLat * cos(Math.toRadians(at.latitude))

        fun xy(p: LatLng): Vec2 =
            Vec2((p.longitude - at.longitude) * mLon, (p.latitude - at.latitude) * mLat)

        fun direction(from: LatLng, to: LatLng): Vec2? {
            val a = xy(from)
            val b = xy(to)
            val dx = b.x - a.x
            val dy = b.y - a.y
            val len = sqrt(dx * dx + dy * dy)
            if (len < 1e-9) return null
            return Vec2(dx / len, dy / len)
        }

        fun toLatLng(x: Double, y: Double): LatLng =
            LatLng(at.latitude + y / mLat, at.longitude + x / mLon)
    }

    private data class Vec2(val x: Double, val y: Double)
}
