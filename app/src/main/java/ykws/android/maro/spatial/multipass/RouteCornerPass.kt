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
import kotlin.math.tan

/**
 * **The corner pass (geometry)** — the settled search line's snapped corners rounded into a racing
 * line: a single bend whose curvature peaks at the corner and tapers to zero, never inverting sign.
 *
 * Each corner becomes a clothoid → arc → clothoid: the lateral G ramps up from the straight leg to its
 * maximum at the apex, holds it across a constant-radius arc, and ramps back to straight. The boat
 * leans one way only, easing to straight at both ends.
 *
 * The radius is fixed by the reach: the curve reaches a fraction ([AppConfig.routeTurnReachFraction])
 * of the shorter leg along each leg, which sets the widest radius that fits and, through
 * `v ≤ √(a_lat · r)`, the corner speed. `route.turn.minSpeedKn` is the speed the drawing **aims**
 * for: the room may take the curve below it, down to this pass's own 1 kn minimum, and only a bend
 * under that bottom is left standing. The clothoid length is `v · route.turn.transitionSec`. The
 * clearance test (coast, depth, zone) then shrinks the radius where the inward bow would foul.
 *
 * The pass emits the drawn polyline and, for every curve point, the corner speed it may not exceed —
 * the ceiling the speed pass then reads beside the enforced limit.
 *
 * **Every corner leaves a verdict behind.** [`RoundedLine.verdicts`] carries one [`CornerVerdict`] per
 * bent vertex: the answer it took, the angle it turned, both legs, the reach the policy asked and what
 * the last bow had already spent of it, and — where a bow fouled — which of the three walls it met.
 * Nothing in the drawn line or in the ceilings depends on it, so the pass's own answer is unchanged:
 * the verdicts exist so a run can **say** why a corner stayed standing instead of leaving that to be
 * inferred from the shape of the drawn line.
 */
object RouteCornerPass {

    /** The clothoid's Fresnel argument runs at the heading's half-rate, so the pair is read at `τ/√2`. */
    private val SQRT2 = sqrt(2.0)

    /** A corner worth rounding turns by at least this, in degrees. */
    private const val MIN_TURN_DEG = 10.0

    /** Below this tangent length (m) the legs are too short to round at all. */
    private const val MIN_TANGENT_M = 2.0

    /** The pass's hard bottom (kn): under it a fitted bend is left standing, never drawn near-static. */
    private const val MIN_CORNER_KN = 1.0


    /**
     * The most a bend's own turn may weigh when two bends share one leg — `tan(delta / 2)` capped, so a
     * near turn-back cannot claim the leg the bend beside it needs.
     */
    private const val SHARE_WEIGHT_CAP = 8.0

    /** The drawn, rounded line and the curvature ceiling each point carries (kn, or null). */
    data class RoundedLine(
        val points: List<LatLng>,
        val ceilingKnAt: (LatLng) -> Double?,
        /**
         * One reading per bent vertex, in the line's own order — why each corner was rounded or left
         * standing. **Report only**: nothing in [points] or in [ceilingKnAt] is computed from it.
         */
        val verdicts: List<CornerVerdict> = emptyList()
    )

    /** **Why one corner was rounded or left standing** — the answer a [`CornerVerdict`] carries. */
    enum class CornerOutcome {
        /** A clothoid → arc → clothoid was drawn. */
        ROUNDED,

        /** The turn is under [MIN_TURN_DEG]. */
        SHALLOW,

        /** The two legs are near opposite, where the fit's own `sin(delta)` guard refuses. */
        REVERSAL,

        /** A zero-length leg, or two legs exactly collinear. */
        DEGENERATE,

        /** The room the last bow left is under [MIN_TANGENT_M]. */
        NO_ROOM,

        /** The room fits no radius above the pass's own 1 kn bottom, so the bend is left standing. */
        FLOOR_ROOM,

        /** The bow at the floor's radius would cross the coast. */
        BOW_COAST,

        /** The bow at the floor's radius would enter water the depth gate blocks. */
        BOW_DEPTH,

        /** The bow at the floor's radius would enter a speed zone the corner itself stands outside. */
        BOW_ZONE
    }

    /**
     * Whether a corner left standing for [outcome] is capped at the turn floor. A refusal about the
     * turn — no room, or a bow meeting a wall — is; a bend too flat ([CornerOutcome.SHALLOW]) or too
     * degenerate ([CornerOutcome.DEGENERATE]) to have asked a radius question keeps its enforced limit.
     */
    private fun capsAtFloor(outcome: CornerOutcome): Boolean =
        outcome != CornerOutcome.SHALLOW && outcome != CornerOutcome.DEGENERATE

    /**
     * **One corner's reading** — the geometry the pass read and the answer it took, carried out on
     * [`RoundedLine.verdicts`]. It reports; it never decides, so the pass's own output is unmoved.
     */
    data class CornerVerdict(
        /** The vertex's own index in the line handed to [round]. */
        val index: Int,
        val at: LatLng,
        val outcome: CornerOutcome,
        /** The turn the two legs make there, in degrees. */
        val turnDeg: Double,
        val legInM: Double,
        val legOutM: Double,
        /** The reach the policy allows: `route.turn.reachFraction × min(legIn, legOut)`. */
        val reachAskM: Double,
        /** How far the last emitted point already stood from this corner — the reach's own cap. */
        val emittedInM: Double,
        /**
         * The reach this bend's own share of the leg leaves it where another bend the pass will curve
         * stands on that leg — `null` where it has no bend to its right, so the share never binds.
         */
        val sharedM: Double?,
        /** The room actually worked with: the least of `reachAskM`, `emittedInM` and `sharedM`. */
        val roomM: Double,
        /** Whether the vertex itself stands in a speed zone, where the zone test does not apply. */
        val cornerInZone: Boolean,
        /**
         * The speed the corner is taken at (kn) — the curve's own, the floor where a turn refusal left
         * it standing, or the pace where it never asked a radius question.
         */
        val speedKn: Double
    )

    /**
     * Rounds every corner of [line] and returns the drawn polyline with its curvature ceilings.
     *
     * [gate] is the depth gate's rule, `null` where it is out of force: a bow keeps the gate's preferred
     * standoff off the measured 3 m edge, or the corner's own where that is less. [marginM] is the coast's
     * own clearance, read the same relative way.
     */
    fun round(
        line: List<LatLng>,
        paceKn: Double,
        world: MultipassWorld,
        gate: GateRule?,
        marginM: Double
    ): RoundedLine {
        if (line.size < 3) return RoundedLine(line, { null })
        val paceMps = Units.knotsToMps(paceKn)
        val aLat = AppConfig.routeTurnLateralAccelMps2
        val vFloorMps = Units.knotsToMps(AppConfig.routeTurnMinSpeedKn)
        val transitionSec = AppConfig.routeTurnTransitionSec
        val reachFraction = AppConfig.routeTurnReachFraction
        // **A close pair of bends is one turn**: the line is read with each such pair replaced by the
        // crossing of its outer legs, so what follows rounds one corner over the pair's whole span.
        val corners = compoundLine(line, paceMps, aLat)
        if (corners.size < 3) return RoundedLine(corners, { null })
        // **A kink too flat to be a corner keeps its point but not the room.** The room a bend may use is
        // the straight to the nearest **significant** vertex on each side — a corner the pass will consider,
        // or the line's own end — so a 3° kink a few metres ahead no longer caps the bend beside it, while
        // the kink itself stays a drawn point of the line, the pass never churning a point it was given.
        val legInM = DoubleArray(corners.size) { i -> if (i == 0) 0.0 else significantLegM(corners, i, -1) }
        val legOutM = DoubleArray(corners.size) { i ->
            if (i == corners.lastIndex) 0.0 else significantLegM(corners, i, 1)
        }
        // **The shared leg**: two bends the pass will curve on one leg take a share each of it, so the
        // pair meets as one continuous S rather than the first taking the whole leg and leaving the
        // second a micro-curve. A stub between long straights shares nothing.
        val shareM = sharedLegReach(corners)

        val out = ArrayList<LatLng>(corners.size + 16)
        val ceilings = HashMap<LatLng, Double>()
        val verdicts = ArrayList<CornerVerdict>(corners.size)
        out.add(corners.first())
        // **The tail of the last bow actually drawn** — the curve a new bow may not overlap. Only a drawn
        // bow sets it; a bend left standing is a point of the line, not a curve, so it never becomes the
        // tail, and with no bow drawn yet the line's own origin stands.
        var tail = corners.first()
        for (i in 1 until corners.lastIndex) {
            val at = corners[i]
            val prev = corners[i - 1]
            val next = corners[i + 1]
            // How far the last bow's own tail already stands from this corner, so the new bow never
            // overlaps the one before it. It measures to that curve, not to the last drawn point: a kink
            // too flat to be a corner is a point of the line, and nothing for a new bow to overlap.
            val emittedIn = SpatialOperations.haversine(tail, at)
            val attempt = attemptCorner(
                prev, at, next, emittedIn, legInM[i], legOutM[i], shareM[i], paceMps, aLat, vFloorMps,
                transitionSec, reachFraction, world, gate, marginM
            )
            verdicts.add(attempt.verdict(i, at))
            val rounded = attempt.rounded
            if (rounded == null) {
                // Every bend left standing keeps its own point on the line — a gentle kink is where the
                // walk put it — and only a refusal about the turn carries a corner ceiling beside it.
                if (out.last() != at) out.add(at)
                if (attempt.capsAtFloor) ceilings[at] = attempt.speedKn
            } else {
                val ceilingKn = Units.mpsToKnots(rounded.cornerSpeedMps)
                // The bow reaches `tangentM` either side of its corner, and the room it took was measured
                // past any kink too flat to be one: a point the bow has now passed is taken out before the
                // bow goes in, so the drawn line can never fold back over it. The line's own start is kept,
                // being the leg's origin rather than a corner's point.
                while (out.size > 1 && SpatialOperations.haversine(out.last(), at) < rounded.tangentM - 1e-6) {
                    ceilings.remove(out.removeAt(out.lastIndex))
                }
                for (p in rounded.points) {
                    if (out.last() != p) out.add(p)
                    ceilings[p] = ceilingKn
                }
                tail = out.last()
            }
        }
        if (out.last() != corners.last()) out.add(corners.last())
        return RoundedLine(out, { p -> ceilings[p] }, verdicts)
    }

    /**
     * **A close pair of bends is one turn.** Two bends the pass will curve whose span is shorter than the
     * tangent a **pace-speed** curve needs for their summed angle cannot host two curves: each takes a
     * fraction of a leg too short for either, and the pair reads as a micro-curve beside a wide one however
     * the leg between them is shared. The pair is therefore replaced by the **crossing of its two outer
     * legs** — the corner a single turn over that span has — so the summed angle is drawn once, over the
     * outer legs as its room, and through the same clearance tests as every other bend. The water still
     * has its say: where the wider bow would foul the coast, the shallows or a zone, the shrink refuses it
     * and the bend is left standing with its own reason on the verdict.
     *
     * Only a pair turning the **same way** is merged — opposite turns are an S the line means, not one
     * corner — and the crossing is skipped where the outer legs are near parallel or where it falls beyond
     * those legs, there being no corner to make.
     */
    private fun compoundLine(line: List<LatLng>, paceMps: Double, aLat: Double): List<LatLng> {
        if (line.size < 4) return line
        val paceRadiusM = paceMps * paceMps / aLat
        val out = ArrayList<LatLng>(line.size)
        out.add(line.first())
        var i = 1
        while (i < line.lastIndex) {
            val turn = turnDegreesAt(line[i - 1], line[i], line[i + 1])
            if (abs(turn) < MIN_TURN_DEG) {
                // A kink too flat to be a corner stays a point of the line: the room a bend may use is
                // measured past it, never by dropping it.
                out.add(line[i])
                i++
                continue
            }
            val j = nextBendIndex(line, i)
            val crossing = if (j > i) mergeCrossing(line, i, j, turn, paceRadiusM) else null
            if (crossing == null) {
                out.add(line[i])
                i++
            } else {
                out.add(crossing)
                i = j + 1
            }
        }
        out.add(line.last())
        return out
    }

    /** The next bend after [i] that the pass will curve, or `-1` where the line holds none. */
    private fun nextBendIndex(line: List<LatLng>, i: Int): Int {
        for (k in i + 1 until line.lastIndex) {
            if (abs(turnDegreesAt(line[k - 1], line[k], line[k + 1])) >= MIN_TURN_DEG) return k
        }
        return -1
    }

    /**
     * The crossing of the two outer legs of the bend pair `[i, j]`, or `null` where the pair is not one
     * corner: opposite turns, a span at or above the pace's own tangent for the summed angle, near
     * parallel legs, or a crossing standing beyond either outer leg.
     */
    private fun mergeCrossing(
        line: List<LatLng>,
        i: Int,
        j: Int,
        turnAtI: Double,
        paceRadiusM: Double
    ): LatLng? {
        val turnAtJ = turnDegreesAt(line[j - 1], line[j], line[j + 1])
        if (abs(turnAtJ) < MIN_TURN_DEG || turnAtI * turnAtJ < 0.0) return null
        var spanM = 0.0
        for (k in i + 1..j) spanM += SpatialOperations.haversine(line[k - 1], line[k])
        val summedDeg = abs(turnAtI) + abs(turnAtJ)
        if (spanM >= paceRadiusM * tan(Math.toRadians(summedDeg) / 2.0)) return null

        val frame = Frame(line[i])
        val a = frame.xy(line[i - 1])
        val b = frame.xy(line[i])
        val c = frame.xy(line[j])
        val d = frame.xy(line[j + 1])
        val d1x = b.x - a.x
        val d1y = b.y - a.y
        val d2x = d.x - c.x
        val d2y = d.y - c.y
        val denom = d1x * d2y - d1y * d2x
        if (abs(denom) < 1e-9) return null
        val ex = c.x - b.x
        val ey = c.y - b.y
        val s = (ex * d2y - ey * d2x) / denom
        val t = (ex * d1y - ey * d1x) / denom
        if (abs(s) > 1.0 || abs(t) > 1.0) return null
        return frame.toLatLng(b.x + s * d1x, b.y + s * d1y)
    }

    /**
     * **The reach a bend may take on a leg it shares.** A bow consumes its own tangent either side of its
     * corner, so two bends the pass will curve on one leg compete for that leg: each is allowed a share of
     * it, weighted by its own turn — `tan(delta / 2)`, capped by [SHARE_WEIGHT_CAP] so a near turn-back
     * cannot claim the leg the bend beside it needs. Weighting by the tangent makes the pair's own radii
     * equal, which is the radius at which the two curves meet and the line reads as one continuous S.
     *
     * A vertex the pass leaves alone — a stub under [MIN_TURN_DEG] — is **not** a bend and shares nothing,
     * so a stub between two long straights leaves the whole leg to the corner beside it, which is what the
     * whole-leg reach exists for.
     *
     * `null` where a bend has no bend to its right, so the share never binds there.
     */
    private fun sharedLegReach(line: List<LatLng>): Array<Double?> {
        val share: Array<Double?> = arrayOfNulls(line.size)
        var prevBend = -1
        var prevWeight = 0.0
        var legM = 0.0
        for (i in 1 until line.lastIndex) {
            legM += SpatialOperations.haversine(line[i - 1], line[i])
            val turnDeg = abs(turnDegreesAt(line[i - 1], line[i], line[i + 1]))
            if (turnDeg < MIN_TURN_DEG) continue
            val weight = min(tan(Math.toRadians(turnDeg) / 2.0), SHARE_WEIGHT_CAP)
            if (prevBend >= 0 && prevWeight + weight > 0.0) {
                share[prevBend] = legM * prevWeight / (prevWeight + weight)
            }
            prevBend = i
            prevWeight = weight
            legM = 0.0
        }
        return share
    }

    /**
     * The straight a bend at [i] actually has on one side: the distance along the line to the nearest
     * **significant** vertex — one whose own turn the pass will consider — with the line's own end counting
     * as one. A kink too flat to be a corner therefore does not end the straight, which is the whole point:
     * the bend beside it takes its room from the real corner beyond, or from the end of the leg.
     */
    private fun significantLegM(line: List<LatLng>, i: Int, step: Int): Double {
        var total = 0.0
        var k = i
        while (true) {
            val next = k + step
            if (next < 0 || next > line.lastIndex) return total
            total += SpatialOperations.haversine(line[k], line[next])
            k = next
            if (k == 0 || k == line.lastIndex) return total
            if (abs(turnDegreesAt(line[k - 1], line[k], line[k + 1])) >= MIN_TURN_DEG) return total
        }
    }

    /**
     * The turn at [at] between its two legs, in degrees and **signed** by the side it turns to — positive
     * left, negative right — read in the same local frame the fit uses. The attempt reads its own vectors
     * while it fits; this is the pass's read of the angle alone, taken where only the value is wanted: the
     * shares two bends take from one leg, and whether a pair turns the one way.
     */
    private fun turnDegreesAt(prev: LatLng, at: LatLng, next: LatLng): Double {
        val frame = Frame(at)
        val uIn = frame.direction(prev, at) ?: return 0.0
        val uOut = frame.direction(at, next) ?: return 0.0
        val cross = uIn.x * uOut.y - uIn.y * uOut.x
        val delta = acos((uIn.x * uOut.x + uIn.y * uOut.y).coerceIn(-1.0, 1.0))
        return Math.toDegrees(if (cross >= 0.0) delta else -delta)
    }

    /**
     * One drawn bow: its points, the corner speed its ceiling carries, and the **tangent** it reaches
     * either side of the corner — the last being what tells the drawing tail which points the bow has
     * passed and therefore supersedes.
     */
    private data class RoundedCorner(
        val points: List<LatLng>,
        val cornerSpeedMps: Double,
        val tangentM: Double
    )

    /**
     * **One corner's attempt** — the answer it took, the geometry behind that answer and the curve where
     * one was drawn. The drawing tail of [round] reads [rounded] for the curve and [capsAtFloor] with
     * [speedKn] for a bend left standing, and the verdict is folded out of the rest, so the pass decides
     * exactly as it did before the tally existed.
     */
    private class Attempt(
        val outcome: CornerOutcome,
        val turnDeg: Double,
        val legInM: Double,
        val legOutM: Double,
        val reachAskM: Double,
        val emittedInM: Double,
        val sharedM: Double?,
        val roomM: Double,
        val cornerInZone: Boolean,
        val speedKn: Double,
        val rounded: RoundedCorner?,
        /** Whether a corner left standing this way is capped at the turn floor (see [capsAtFloor]). */
        val capsAtFloor: Boolean = false
    ) {
        fun verdict(index: Int, at: LatLng) = CornerVerdict(
            index, at, outcome, turnDeg, legInM, legOutM, reachAskM, emittedInM, sharedM, roomM,
            cornerInZone, speedKn
        )
    }

    private fun attemptCorner(
        prev: LatLng,
        at: LatLng,
        next: LatLng,
        emittedIn: Double,
        legIn: Double,
        legOut: Double,
        sharedM: Double?,
        paceMps: Double,
        aLat: Double,
        vFloorMps: Double,
        transitionSec: Double,
        reachFraction: Double,
        world: MultipassWorld,
        gate: GateRule?,
        marginM: Double
    ): Attempt {
        // The legs are the caller's **significant** ones — the straight to the nearest vertex the pass will
        // consider, or the line's own end — never the distance to the adjacent point, so a kink too flat to
        // be a corner cannot cap the bend beside it.
        // The reach: the fraction of the shorter leg the policy allows, never overlapping the previous
        // bow — whose own emitted distance stays the cap — and never taking more than the share the leg
        // leaves this bend where another bend the pass will curve stands on it.
        val reachAsk = reachFraction * min(legIn, legOut)
        val room = min(min(reachAsk, emittedIn), sharedM ?: Double.MAX_VALUE)
        val paceKn = Units.mpsToKnots(paceMps)
        val oneKnMps = Units.knotsToMps(MIN_CORNER_KN)

        // A refused corner is taken at the turn floor only where the refusal was about the turn — one
        // home for the ceiling, in both the line and the verdict. A bend too flat or too degenerate to
        // ask a radius question reports the pace and caps nothing.
        fun refused(outcome: CornerOutcome, turnDeg: Double, cornerInZone: Boolean = false): Attempt {
            val caps = capsAtFloor(outcome)
            return Attempt(
                outcome, turnDeg, legIn, legOut, reachAsk, emittedIn, sharedM, room, cornerInZone,
                if (caps) min(Units.mpsToKnots(vFloorMps), paceKn) else paceKn, null, caps
            )
        }

        // Whether the vertex itself stands in a speed zone, read before any refusal so every verdict —
        // the shallow, degenerate and reversal exits here, the no-room and bow-wall exits below —
        // reports where the bend stood rather than a default `false`.
        val cornerInZone = world.zoneLimitKnAt(at.latitude, at.longitude) != null
        val frame = Frame(at)
        val uIn = frame.direction(prev, at) ?: return refused(CornerOutcome.DEGENERATE, 0.0, cornerInZone)
        val uOut = frame.direction(at, next) ?: return refused(CornerOutcome.DEGENERATE, 0.0, cornerInZone)
        val cosDelta = (uIn.x * uOut.x + uIn.y * uOut.y).coerceIn(-1.0, 1.0)
        val delta = acos(cosDelta)
        val turnDeg = Math.toDegrees(delta)
        if (delta < Math.toRadians(MIN_TURN_DEG)) return refused(CornerOutcome.SHALLOW, turnDeg, cornerInZone)
        val sinDelta = sin(delta)
        if (sinDelta < 1e-6) return refused(CornerOutcome.REVERSAL, turnDeg, cornerInZone)
        val cross = uIn.x * uOut.y - uIn.y * uOut.x
        if (abs(cross) < 1e-9) return refused(CornerOutcome.DEGENERATE, turnDeg, cornerInZone)
        val turnSign = if (cross > 0.0) 1.0 else -1.0
        val signedDelta = turnSign * delta

        if (room < MIN_TANGENT_M) return refused(CornerOutcome.NO_ROOM, turnDeg, cornerInZone)

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

        // Which wall a single drawn point meets, if any — the three tests the clearance gate runs, in
        // the order it has always run them, named rather than collapsed into `false`.
        //
        // The coast test is **relative to the corner it replaces**: a bow may not come closer to the shore
        // than the corner itself, so a bend the walk placed inside the margin can still be rounded — a curve
        // can never make the line worse than it already is — while every bow from a corner standing clear of
        // the margin keeps the margin whole. This is the zone test's own idea, a corner inside a zone being
        // excused the zone. The gate's standoff reads the same way off the measured 3 m edge, at the gate's
        // preferred standoff, while its blocked water itself stays absolute.
        val coastFloorM = min(marginM, world.distanceToCoastM(at.latitude, at.longitude))
        val gateFloorM = gate?.let { min(it.preferredM, it.edgeM(at, it.preferredM)) } ?: 0.0
        fun blockerAt(p: LatLng): CornerOutcome? {
            if (world.distanceToCoastM(p.latitude, p.longitude) < coastFloorM - 1e-6) return CornerOutcome.BOW_COAST
            // **The gate's own standoff, measured** — a bow is refused where it stands in blocked water, or
            // nearer the measured edge than the corner it replaces (never nearer than the preferred standoff
            // where the corner itself stands clear of it).
            if (gate != null &&
                (gate.isShallow(p) || gate.edgeM(p, gate.preferredM) < gateFloorM - 1e-6)
            ) {
                return CornerOutcome.BOW_DEPTH
            }
            if (!cornerInZone && world.zoneLimitKnAt(p.latitude, p.longitude) != null) {
                return CornerOutcome.BOW_ZONE
            }
            return null
        }
        fun firstBlocker(v: Double): CornerOutcome? {
            val pts = build(v)
            for (i in pts.indices) {
                blockerAt(pts[i])?.let { return it }
                if (i > 0) {
                    val a = pts[i - 1]
                    val mid = LatLng(
                        (a.latitude + pts[i].latitude) / 2.0,
                        (a.longitude + pts[i].longitude) / 2.0
                    )
                    blockerAt(mid)?.let { return it }
                }
            }
            return null
        }

        // The largest speed whose bow still fits the reach.
        var v = paceMps
        // The lowest speed the bow-clearance shrink may reach: the floor where the floor's own radius
        // fits the room, the pass's hard bottom where the room has already drawn the curve under it.
        var vBottom = lo
        if (tangentOf(paceMps) > room) {
            if (tangentOf(lo) > room) {
                // The floor's radius cannot fit the room: rather than leave the angle, draw the largest
                // radius whose **drawn** curve still reaches no further than the room — the composite
                // tangent the rest of the pass reads, spirals and all, never the circular arc's shorter
                // one — so the curve drawn here is fitted to the room it was given. The drawn tangent
                // shrinks to zero with the radius, so the search runs below the floor; under the pass's
                // own hard bottom the bend is left standing as it always was, so no pathological
                // geometry draws a static curve.
                var a = 0.0
                var b = lo
                repeat(24) {
                    val mid = (a + b) / 2.0
                    if (tangentOf(mid) <= room) a = mid else b = mid
                }
                val vFitted = min(a, paceMps)
                if (vFitted < oneKnMps) return refused(CornerOutcome.FLOOR_ROOM, turnDeg, cornerInZone)
                v = vFitted
                vBottom = oneKnMps
            } else {
                var a = lo
                var b = paceMps
                repeat(24) {
                    val mid = (a + b) / 2.0
                    if (tangentOf(mid) <= room) a = mid else b = mid
                }
                v = a
            }
        }
        // Shrink where the inward bow would foul — the same bisection, down to [vBottom].
        if (firstBlocker(v) != null) {
            val bottomBlocker = firstBlocker(vBottom)
            if (bottomBlocker != null) return refused(bottomBlocker, turnDeg, cornerInZone)
            var a = vBottom
            var b = v
            repeat(24) {
                val mid = (a + b) / 2.0
                if (firstBlocker(mid) == null) a = mid else b = mid
            }
            v = a
        }
        // The bow's own tangent — how far it reaches either side of the corner — travels out with it, since
        // the drawing tail uses it to take back the points the bow has passed.
        val (rDrawn, lDrawn) = rAndL(v)
        return Attempt(
            CornerOutcome.ROUNDED, turnDeg, legIn, legOut, reachAsk, emittedIn, sharedM, room,
            cornerInZone, Units.mpsToKnots(v),
            RoundedCorner(build(v), v, composite(rDrawn, lDrawn, signedDelta).tangent)
        )
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
