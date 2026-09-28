package ykws.android.maro.spatial.avoid

import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.spatial.SpatialOperations
import ykws.android.maro.spatial.Units
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * **The curve fitter (phase 6) — the settled line's bends faired into steerable curves, on water.**
 *
 * A post-search stage over the settled polyline, run between the fine re-search and the clock: it
 * flattens the residual staircase, turning each **bend** into a curvature-continuous curve the boat can
 * steer — a vertex turning under the 10° floor is no corner and ends the run beside it. The A\* is
 * untouched, and the faired line is the route **drawn and saved**.
 *
 * **A run, not a corner.** One curve spans a run of consecutive corners, from the bend's entry tangent
 * to its exit tangent. A run ends where the turn's **sign changes** (an S-bend is two bends) or where
 * the separating leg is longer than twice the transition — so the join never depends on the radius it
 * would need. The transition used to split a run is the **floor speed's** own `v_min · transitionSec`:
 * the bend's speed is not known until its radius is, and the floor is the shortest transition any bend
 * may have, so a run never spans a leg a bend's own transition could not bridge. Where a run's single
 * curve cannot meet the legs, its corners are faired **individually** rather than the whole run dropped.
 *
 * **Curvature-continuous.** The curve runs straight → **spiral** → arc → spiral → straight, built in a
 * local frame with the entry tangent along +x and then placed so both straight ends meet the legs
 * tangentially: the spiral ramps curvature `0 → 1/r` over `L = v · transitionSec` and consumes
 * `L/(2r)` radians at each end, the circular middle sweeping what is left. Where the turn is at or
 * under `L/r`, the two spirals would cross, so the bend is a **spiral-only** curve with each spiral
 * `θ · r` long; a turn too small to leave a curve is kept as its vertex. A bare arc, or a trimmed one
 * with a heading kink, is never emitted.
 *
 * **The radius comes from the water, capped by the pace.** A bend is drawn at the largest radius that
 * clears the walls, never wider than the pace's own `v_pace² / a_lat`, with `a_lat` from
 * [`AppConfig.routeTurnLateralAccelMps2`]. The largest clearing speed — hence radius, `r = v² / a_lat`
 * — is found by **bisection, 24 steps**, over the legal speed span `[minSpeed, v_pace]`: a slower bend
 * has a smaller radius and so bows less far inside the corner, which is the one lever. The radius is
 * never reduced below the final speed's own `v² / a_lat`; the **speed** is. A curve the relaxation has
 * **moved** re-derives its cap from its own minimum radius ([minRadiusM]), so a moved bend can never
 * claim a speed its geometry no longer holds — a moved curve too tight for the floor stays sharp.
 *
 * **The corner speed is a cap with floors under it.** `v = min(cap, max(decelFloor, minSpeed))` with
 * `cap = min(v_pace, sqrt(a_lat · r_fit))`, `decelFloor` read off the clock's own ramp
 * ([`decelSpeedMps`], the one home of `sqrt(v² − 2·a_long·d)`) over `L_approach`, the distance from the
 * previous **resolved** bend's exit to this bend's entry. A floor above the cap means the radius is too
 * tight for any legal speed and the bend stays sharp. The corner speed never raises the boat above the
 * limit in force — the clock applies it beside the zone limit.
 *
 * **The walls are absolute.** Every segment and every sample of the emitted curve is water-tested at
 * the pull's step (`marginM / 2`) against the coast's `route.avoid.obstacle.marginM`, the depth gate's
 * `minM` **and** its `route.avoid.depthGate.marginM` standoff, the grid's own blocked set and the two
 * carved approaches, with the ends' disc waived as the pull waives it. The engine hands the fitter the
 * **coarse search grid** — it carries its own origin and box — so a point resolves to a cell and the
 * test sees what the search saw; the depth standoff is read as the distance from the sampled cell's
 * **centre** to the nearest cell under the gate, which is what makes a 20 m offset representable
 * without a finer walk. The **soft price is never read**: a bow into a priced band or zone is accepted,
 * the app cueing zone proximity elsewhere.
 *
 * **Inward relaxation, outside walls only.** Where the slowed ladder still grazes a wall on the
 * **outside** of the turn, the intermediate control points move **inward, toward the vertex** — which
 * tightens the curve away from that outside wall — bounded by the corridor box and re-tested. An
 * **inside** wall is never answered by relaxation: the radius is the lever there, and where both sides
 * are walls the speed floor answers and the bend stays sharp. The verdict reads the blocked sample
 * **nearest the vertex** ([Walls.deepestBlocked]), so an inside graze behind a flank block is not
 * misread as outside. Where no radius clears even at `minSpeedKn`, that same slow bend is the answer,
 * the search's vertices taken at the floor.
 *
 * **The clock carries the bend's speed.** A [CurveCap] is emitted for **every curve point** of a
 * resolved bend, strictest-wins, and the clock charges it beside the longitudinal ramp; the search
 * stays turn-blind. The emitted line is collapsed of exact duplicates only, so every keyed point
 * survives. Pure geometry: no coroutine, no Android, no soft price.
 */

/** The faired line and its instrument: the drawn polyline, the per-point caps, and the bend verdicts. */
data class FairedLine(
    val points: List<LatLng>,
    val caps: List<CurveCap>,
    /** How many bends the line held — a run, or each corner of a run its own curve could not span. */
    val bends: Int,
    /** How many were faired into a curve. */
    val resolved: Int,
    /** How many were kept sharp because no radius cleared even at the floor. */
    val keptSharp: Int
)

object RouteCurveFitter {

    /** Length (m) of one emitted chord — the study's 10 m, also capped at 10° of arc. */
    private const val CHORD_STEP_M = 10.0

    /** A vertex turning less than this is no corner; it ends the run beside it and is left in place. */
    private val MIN_BEND_RAD = Math.toRadians(10.0)

    /** The clearing-radius search's own resolution — the plan's 24 steps. */
    private const val BISECTION_STEPS = 24

    /** How many inward moves the relaxation tries before it gives up. */
    private const val RELAX_STEPS = 3

    /**
     * Fairs [line] (raw start first, raw aim last) into a navigable line, water-testing the emitted
     * curve against [grid]'s blocked set, the world's coast and gate, and the two [approaches].
     */
    fun fit(
        line: List<LatLng>,
        grid: AvoidGrid,
        box: BBox,
        approaches: EndApproaches,
        world: AvoidWorld,
        paceKn: Double,
        marginM: Double,
        start: LatLng,
        aim: LatLng,
        depthGateActive: Boolean,
        minDepthM: Double
    ): FairedLine {
        if (line.size < 3) return FairedLine(line, emptyList(), 0, 0, 0)
        val runs = detectRuns(line)
        if (runs.isEmpty()) return FairedLine(line, emptyList(), 0, 0, 0)
        val walls = Walls(
            grid = grid,
            world = world,
            marginM = marginM,
            depthGateActive = depthGateActive,
            minDepthM = minDepthM,
            standoffM = AppConfig.routeAvoidDepthGateMarginM,
            start = start,
            aim = aim,
            approaches = approaches,
            box = box
        )
        val out = ArrayList<LatLng>(line.size + 24)
        val caps = ArrayList<CurveCap>()
        var idx = 0
        var prevExit = line.first()
        var resolved = 0
        var kept = 0
        var bends = 0
        for (run in runs) {
            val f = run.first
            val l = run.last
            while (idx <= f - 1) {
                out.add(line[idx])
                idx++
            }
            val fitted = fitBend(line, f, l, prevExit, paceKn, walls)
            if (fitted != null) {
                for (p in fitted.points) if (out.lastOrNull() != p) out.add(p)
                for (p in fitted.points) caps.add(CurveCap(p, fitted.capKn))
                prevExit = fitted.points.last()
                resolved++
                bends++
                idx = l + 1
            } else {
                // The run's single curve could not meet the legs or the walls: fair its corners on
                // their own rather than dropping the whole run to the search's sharp vertices.
                var corner = f
                while (corner <= l) {
                    while (idx <= corner - 1) {
                        out.add(line[idx])
                        idx++
                    }
                    val one = fitBend(line, corner, corner, prevExit, paceKn, walls)
                    if (one != null) {
                        for (p in one.points) if (out.lastOrNull() != p) out.add(p)
                        for (p in one.points) caps.add(CurveCap(p, one.capKn))
                        prevExit = one.points.last()
                        resolved++
                    } else {
                        if (out.lastOrNull() != line[corner]) out.add(line[corner])
                        kept++
                    }
                    bends++
                    idx = corner + 1
                    corner++
                }
            }
        }
        while (idx < line.size) {
            out.add(line[idx])
            idx++
        }
        return collapse(out, caps, bends, resolved, kept)
    }

    /** The faired curve's own points and the one speed (kn) its every arc point carries. */
    private class FittedBend(val points: List<LatLng>, val capKn: Double)

    /**
     * One bend: its wall-clear radius and the corner speed the floors leave, or `null` where no radius
     * clears even at the floor — the search's vertices then stand. A curve the relaxation moved carries
     * a cap **re-derived** from its own minimum radius, so a moved bend can never outrun its geometry.
     */
    private fun fitBend(
        line: List<LatLng>,
        f: Int,
        l: Int,
        prevExit: LatLng,
        paceKn: Double,
        walls: Walls
    ): FittedBend? {
        val bend = Bend(line[f - 1], line[f], line[l], line[l + 1]) ?: return null
        if (bend.theta < MIN_BEND_RAD) return null
        val aLat = AppConfig.routeTurnLateralAccelMps2
        val transitionSec = AppConfig.routeTurnTransitionSec
        val vPace = Units.knotsToMps(paceKn)
        val vMin = Units.knotsToMps(AppConfig.routeTurnMinSpeedKn)
        val aLong = AppConfig.routeSpeedAccelMps2
        val rPace = vPace * vPace / aLat
        val approachM = SpatialOperations.haversine(prevExit, bend.entryVertex)
        val decelFloorMps = decelSpeedMps(vPace, approachM, aLong)
        val floor = max(decelFloorMps, vMin)

        fun fits(v: Double): Boolean {
            val r = min(rPace, v * v / aLat)
            val curve = bend.curve(r, v * transitionSec) ?: return false
            return walls.clears(curve)
        }

        /**
         * The cap a **moved** curve may carry: the moved geometry's own minimum radius re-derives the
         * speed, so the relaxation can never leave a curve tighter than its cap claims. A moved curve
         * whose radius cannot hold even the floor keeps the bend sharp, as the ladder's own end does.
         */
        fun capped(moved: List<LatLng>): FittedBend? {
            val capMoved = min(vPace, sqrt(aLat * minRadiusM(moved)))
            if (floor > capMoved + 1e-9) return null
            return FittedBend(moved, Units.mpsToKnots(min(capMoved, floor)))
        }

        val vFit: Double = when {
            fits(vPace) -> vPace
            // No radius clears even at the floor: the outer side of the turn is relaxed away from its
            // wall, and the search's own vertices stand only where even that leaves it blocked.
            !fits(vMin) -> {
                val atPace = bend.curve(rPace, vPace * transitionSec) ?: return null
                val relaxed = walls.relax(bend, atPace) ?: return null
                return capped(relaxed)
            }
            else -> {
                var lo = vMin
                var hi = vPace
                repeat(BISECTION_STEPS) {
                    val mid = (lo + hi) / 2.0
                    if (fits(mid)) lo = mid else hi = mid
                }
                lo
            }
        }
        val rFit = min(rPace, vFit * vFit / aLat)
        val capV = min(vPace, sqrt(aLat * rFit))
        // A floor above the cap: the radius is too tight for any legal speed, so the bend stays sharp.
        if (floor > capV + 1e-9) return null
        val vFinal = min(capV, floor)
        val curve = bend.curve(rFit, vFinal * transitionSec) ?: return null
        if (walls.clears(curve)) return FittedBend(curve, Units.mpsToKnots(vFinal))
        val relaxed = walls.relax(bend, curve) ?: return null
        return capped(relaxed)
    }

    /** One run of consecutive same-sign corners, by the index of its first and last corner vertex. */
    private class Run(val first: Int, val last: Int)

    /**
     * The maximal runs of corners: a vertex joins the run while it turns at least [MIN_BEND_RAD], its
     * sign matches the run's, and the leg separating it from the previous corner is at most twice the
     * **floor** speed's transition. The floor is the slowest any bend may be taken, hence the shortest
     * transition a bend can have — the bend's own speed is not known until its radius is — so a run
     * never spans a leg a bend's own transition could not bridge.
     */
    private fun detectRuns(line: List<LatLng>): List<Run> {
        val n = line.size
        if (n < 3) return emptyList()
        val legSplitM =
            2.0 * Units.knotsToMps(AppConfig.routeTurnMinSpeedKn) * AppConfig.routeTurnTransitionSec
        val turns = DoubleArray(n) { i ->
            if (i == 0 || i == n - 1) 0.0
            else norm(
                Math.toRadians(SpatialOperations.initialBearing(line[i], line[i + 1])) -
                    Math.toRadians(SpatialOperations.initialBearing(line[i - 1], line[i]))
            )
        }
        val runs = ArrayList<Run>()
        var i = 1
        while (i <= n - 2) {
            if (abs(turns[i]) < MIN_BEND_RAD) {
                i++
                continue
            }
            val sign = if (turns[i] >= 0.0) 1 else -1
            var j = i
            while (j + 1 <= n - 2 && abs(turns[j + 1]) >= MIN_BEND_RAD &&
                (if (turns[j + 1] >= 0.0) 1 else -1) == sign
            ) {
                if (SpatialOperations.haversine(line[j], line[j + 1]) > legSplitM) break
                j++
            }
            runs.add(Run(i, j))
            i = j + 1
        }
        return runs
    }

    /** Collapses exact duplicate vertices, transferring any cap onto the survivor, strictest-wins. */
    private fun collapse(
        points: List<LatLng>,
        caps: List<CurveCap>,
        bends: Int,
        resolved: Int,
        keptSharp: Int
    ): FairedLine {
        val capByPoint = HashMap<LatLng, Double>()
        for (cap in caps) capByPoint.merge(cap.point, cap.capKn) { a, b -> min(a, b) }
        val collapsed = ArrayList<LatLng>(points.size)
        for (p in points) if (collapsed.lastOrNull() != p) collapsed.add(p)
        val capsOut = collapsed.mapNotNull { p -> capByPoint[p]?.let { CurveCap(p, it) } }
        return FairedLine(collapsed, capsOut, bends, resolved, keptSharp)
    }

    /**
     * **One bend's geometry, in a local metric plane anchored at its entry vertex.** The two legs'
     * directions, the signed turn and the vertex `V` — the intersection of the entry and exit tangent
     * lines — are read once; [curve] then builds the spiral-arc-spiral (or spiral-only) shape in a
     * canonical frame and rigidly places it so both straight ends meet the legs tangentially.
     */
    private class Bend(
        val entryVertex: LatLng,
        val firstCorner: LatLng,
        val lastCorner: LatLng,
        val exitVertex: LatLng
    ) {
        private val refLat = entryVertex.latitude
        private val refLon = entryVertex.longitude
        private val mPerDegLat = SpatialOperations.EARTH_RADIUS_M * PI / 180.0
        private val mPerDegLon = mPerDegLat * cos(Math.toRadians(refLat))

        private fun local(p: LatLng) = Vec2((p.longitude - refLon) * mPerDegLon, (p.latitude - refLat) * mPerDegLat)
        private fun world(v: Vec2) = LatLng(refLat + v.y / mPerDegLat, refLon + v.x / mPerDegLon)

        private val entryLocal = local(entryVertex)
        private val firstLocal = local(firstCorner)
        private val lastLocal = local(lastCorner)
        private val exitLocal = local(exitVertex)

        private val uIn = (firstLocal - entryLocal).unit()
        private val uOut = (exitLocal - lastLocal).unit()

        val psiIn = atan2(uIn.y, uIn.x)
        val psiOut = atan2(uOut.y, uOut.x)
        val theta = abs(norm(psiOut - psiIn))
        private val s = if (norm(psiOut - psiIn) >= 0.0) 1.0 else -1.0

        private val lenEntry = (firstLocal - entryLocal).length()
        private val lenExit = (exitLocal - lastLocal).length()

        /** The two tangent lines' own intersection — the corner the curve cuts inside of. */
        val vertexWorld: LatLng? = intersect(entryLocal, uIn, lastLocal, uOut)?.let { world(it) }

        /**
         * The bend's curve at radius [r] and transition [L], or `null` where the shape cannot meet the
         * legs (a tangent point past a corner, or a degenerate turn).
         */
        fun curve(r: Double, L: Double): List<LatLng>? {
            if (theta < 1e-6 || r <= 0.0 || L < 0.0) return null
            val localPts = localShape(r, L) ?: return null
            val q = localPts.last()
            val psiInCos = cos(psiIn)
            val psiInSin = sin(psiIn)
            val rotatedQ = Vec2(q.x * psiInCos - q.y * psiInSin, q.x * psiInSin + q.y * psiInCos)
            // The exit tangent line through the last corner; solve where the curve's own start lands.
            val nOut = Vec2(-uOut.y, uOut.x)
            val denom = uIn.x * nOut.x + uIn.y * nOut.y
            if (abs(denom) < 1e-9) return null
            val a = (lastLocal.x * nOut.x + lastLocal.y * nOut.y -
                (entryLocal.x * nOut.x + entryLocal.y * nOut.y) -
                (rotatedQ.x * nOut.x + rotatedQ.y * nOut.y)) / denom
            if (a < -1e-6 || a > lenEntry + 1e-6) return null
            val t = entryLocal + uIn * a
            val worldPts = localPts.map { p ->
                world(Vec2(t.x + p.x * psiInCos - p.y * psiInSin, t.y + p.x * psiInSin + p.y * psiInCos))
            }
            // The exit tangent point must still fall between the last corner and the exit vertex.
            val exitEnd = local(worldPts.last()) - lastLocal
            val b = exitEnd.x * uOut.x + exitEnd.y * uOut.y
            val perp = abs(exitEnd.x * (-uOut.y) + exitEnd.y * uOut.x)
            if (b < -1e-6 || b > lenExit + 1e-6 || perp > 1.0) return null
            return worldPts
        }

        /**
         * The curve in a canonical frame: start at the origin with heading +x, ending at `Q` with
         * heading `s · theta`. The local `x` axis is the entry tangent.
         */
        private fun localShape(r: Double, l: Double): List<Vec2>? {
            val pts = ArrayList<Vec2>()
            pts.add(Vec2(0.0, 0.0))
            if (theta > l / r + 1e-12) {
                val arcAngle = theta - l / r
                val sEnd = spiralFrom(Vec2(0.0, 0.0), 0.0, l, r, s, increasing = true, out = pts)
                val psiS = s * l / (2.0 * r)
                val centre = Vec2(sEnd.x - s * r * sin(psiS), sEnd.y + s * r * cos(psiS))
                val arcLen = r * arcAngle
                val arcSteps = max(2, ceil(arcLen / chordStep(r)).toInt())
                for (k in 1..arcSteps) {
                    val phi = s * arcAngle * k / arcSteps
                    val rel = sEnd - centre
                    val cp = cos(phi)
                    val sp = sin(phi)
                    pts.add(Vec2(centre.x + rel.x * cp - rel.y * sp, centre.y + rel.x * sp + rel.y * cp))
                }
                val e = pts.last()
                spiralFrom(e, psiS + s * arcAngle, l, r, s, increasing = false, out = pts)
            } else {
                // Spiral-only: each spiral consumes half the turn, so both meet at the apex without
                // crossing. A turn too small to leave any curve is refused.
                val spiralLen = theta * r
                if (spiralLen < 1e-3) return null
                spiralFrom(Vec2(0.0, 0.0), 0.0, spiralLen, r, s, increasing = true, out = pts)
                // The exit spiral starts at the apex, heading s·theta/2 with curvature 1/r falling to 0.
                val apex = pts.last()
                spiralFrom(apex, s * theta / 2.0, spiralLen, r, s, increasing = false, out = pts)
            }
            return pts
        }

        /**
         * Walks one spiral of length [len] from [start] at heading [heading0], appending points; the
         * curvature ramps `[increasing] 0 → 1/r`, or `1/r → 0`. Returns the spiral's end point.
         */
        private fun spiralFrom(
            start: Vec2,
            heading0: Double,
            len: Double,
            r: Double,
            sign: Double,
            increasing: Boolean,
            out: MutableList<Vec2>
        ): Vec2 {
            val steps = max(2, ceil(len / chordStep(r)).toInt())
            val ds = len / steps
            var x = start.x
            var y = start.y
            for (k in 1..steps) {
                val tau = (k - 0.5) * ds
                val heading = if (increasing) {
                    heading0 + sign * tau * tau / (2.0 * r * len)
                } else {
                    heading0 + sign * (tau - tau * tau / (2.0 * len)) / r
                }
                x += cos(heading) * ds
                y += sin(heading) * ds
                out.add(Vec2(x, y))
            }
            return Vec2(x, y)
        }

        /** The chord's own length: [CHORD_STEP_M], narrowed to 10° of the arc. */
        private fun chordStep(r: Double): Double =
            max(1.0, min(CHORD_STEP_M, r * Math.toRadians(10.0)))

        private fun intersect(p: Vec2, d: Vec2, q: Vec2, e: Vec2): Vec2? {
            val denom = d.x * e.y - d.y * e.x
            if (abs(denom) < 1e-12) return null
            val t = ((q.x - p.x) * e.y - (q.y - p.y) * e.x) / denom
            return Vec2(p.x + t * d.x, p.y + t * d.y)
        }
    }

    /** A local-plane 2-vector in metres, with the small algebra the curve build needs. */
    private data class Vec2(val x: Double, val y: Double) {
        operator fun minus(o: Vec2) = Vec2(x - o.x, y - o.y)
        operator fun plus(o: Vec2) = Vec2(x + o.x, y + o.y)
        operator fun times(k: Double) = Vec2(x * k, y * k)
        fun length() = sqrt(x * x + y * y)
        fun unit(): Vec2 {
            val len = length()
            return if (len < 1e-12) Vec2(1.0, 0.0) else Vec2(x / len, y / len)
        }
    }

    /** The wrap of an angle in radians to `(-π, π]`. */
    private fun norm(a: Double): Double = a - 2.0 * PI * round(a / (2.0 * PI))

    /**
     * The smallest radius of curvature a curve's own vertices imply — the circumradius of each
     * consecutive triple, read in a local metric plane — or [Double.MAX_VALUE] where no triple bends.
     * It is what re-derives a **moved** curve's cap, so a relaxed bend can never claim a speed its own
     * geometry does not hold; `internal` so the fitter's own test can check a cap against it.
     */
    internal fun minRadiusM(curve: List<LatLng>): Double {
        if (curve.size < 3) return Double.MAX_VALUE
        val refLat = curve.first().latitude
        val refLon = curve.first().longitude
        val mPerDegLat = SpatialOperations.EARTH_RADIUS_M * PI / 180.0
        val mPerDegLon = mPerDegLat * cos(Math.toRadians(refLat))
        fun local(p: LatLng) = Vec2((p.longitude - refLon) * mPerDegLon, (p.latitude - refLat) * mPerDegLat)
        var minR = Double.MAX_VALUE
        for (i in 1 until curve.size - 1) {
            val a = local(curve[i - 1])
            val b = local(curve[i])
            val c = local(curve[i + 1])
            val ab = (b - a).length()
            val bc = (c - b).length()
            val ca = (a - c).length()
            val twiceArea = abs((b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x))
            if (twiceArea < 1e-6) continue
            val r = ab * bc * ca / (2.0 * twiceArea)
            if (r < minR) minR = r
        }
        return minR
    }

    /**
     * **The walls the fitter tests every curve sample against.** The end-disc and the carved approaches
     * waive the coast's margin exactly as they do in the pull; the grid's own blocked set is read
     * cell-side, so the fitter sees what the search saw; and the depth gate is read as its own rule plus
     * its standoff, which the grid's coarse blocked set cannot express.
     */
    private class Walls(
        private val grid: AvoidGrid,
        private val world: AvoidWorld,
        private val marginM: Double,
        private val depthGateActive: Boolean,
        private val minDepthM: Double,
        private val standoffM: Double,
        private val start: LatLng,
        private val aim: LatLng,
        private val approaches: EndApproaches,
        private val box: BBox
    ) {

        /** True when the whole [curve] stands on water, sampled at the pull's own step. */
        fun clears(curve: List<LatLng>): Boolean = firstBlocked(curve) == null

        /** The first sample of [curve] a wall refuses, or `null` where every sample clears. */
        fun firstBlocked(curve: List<LatLng>): LatLng? = samples(curve).firstOrNull { !clear(it) }

        /**
         * The blocked sample of [curve] standing **deepest inside the corner** — the one nearest
         * [vertex] — which is the sample the relaxation's inside/outside verdict must read. Reading the
         * first block instead would misread an inside-wall graze behind a flank block as outside; this
         * read is order-independent by construction.
         */
        fun deepestBlocked(curve: List<LatLng>, vertex: LatLng): LatLng? =
            samples(curve).filter { !clear(it) }.minByOrNull { SpatialOperations.haversine(it, vertex) }

        /** The samples of [curve] the wall walk reads — the pull's own step, one walk for both reads. */
        private fun samples(curve: List<LatLng>): Sequence<LatLng> = sequence {
            val step = AvoidPull.clearanceStep(marginM)
            for (i in 0 until curve.size - 1) {
                val a = curve[i]
                val b = curve[i + 1]
                val dist = SpatialOperations.haversine(a, b)
                val steps = max(2, ceil(dist / step).toInt())
                for (k in 1 until steps) {
                    val t = k.toDouble() / steps
                    yield(
                        LatLng(
                            a.latitude + (b.latitude - a.latitude) * t,
                            a.longitude + (b.longitude - a.longitude) * t
                        )
                    )
                }
            }
        }

        /** One sample's verdict: the pull's own margin waiver, then the grid, the coast and the gate. */
        private fun clear(p: LatLng): Boolean {
            if (AvoidPull.marginWaived(p, marginM, start, aim, approaches, AvoidPull.clearanceStep(marginM))) {
                return true
            }
            val cell = grid.cellOf(p.latitude, p.longitude)
            if (!grid.cell(cell.row, cell.col).passable) return false
            if (world.distanceToCoastM(p.latitude, p.longitude) < marginM) return false
            if (depthGateActive && world.depthReady) {
                if (!depthClearsGate(world.depthAt(p.latitude, p.longitude), true, minDepthM)) return false
                if (!standoffClear(cell)) return false
            }
            return true
        }

        /**
         * The depth standoff: the sampled cell's centre to the nearest cell whose centre is gated. It
         * is a **cell**'s own reading, so it is memoised per cell — the wall walk asks it once per
         * sample, and a bend's many samples share a handful of cells.
         */
        private val standoffCache = HashMap<Int, Boolean>()

        private fun standoffClear(cell: CellIndex): Boolean =
            standoffCache.getOrPut(grid.index(cell.row, cell.col)) { standoffClearUncached(cell) }

        private fun standoffClearUncached(cell: CellIndex): Boolean {
            val reach = ceil(standoffM / grid.cellM).toInt().coerceAtLeast(1)
            val centre = grid.center(cell.row, cell.col)
            for (dRow in -reach..reach) {
                for (dCol in -reach..reach) {
                    val row = cell.row + dRow
                    val col = cell.col + dCol
                    if (!grid.inBounds(row, col)) continue
                    val other = grid.center(row, col)
                    if (depthClearsGate(
                            world.depthAt(other.latitude, other.longitude), true, minDepthM
                        )
                    ) {
                        continue
                    }
                    if (SpatialOperations.haversine(centre, other) <= standoffM) return false
                }
            }
            return true
        }

        /**
         * **Inward relaxation, outside walls only.** Where the curve fails and its deepest blocked
         * sample stands on the **outside** of the turn, the intermediate control points move **inward,
         * toward the vertex** — tightening the curve away from that outside wall — by up to one cell,
         * bounded by the corridor box, and the curve is re-tested. An inside wall, or one that no move
         * clears, answers `null` (the speed floor is the lever there and the bend stays sharp). The
         * caller re-derives the cap from the moved curve's own radius; this function decides the
         * geometry alone.
         */
        fun relax(bend: Bend, curve: List<LatLng>?): List<LatLng>? {
            if (curve == null) return null
            val vertex = bend.vertexWorld ?: return null
            if (clears(curve)) return curve
            val blocked = deepestBlocked(curve, vertex) ?: return null
            val apex = curve.minByOrNull { SpatialOperations.haversine(it, vertex) } ?: return null
            // A wall at least as deep as the arc's own apex is an inside wall: never relaxed.
            if (SpatialOperations.haversine(blocked, vertex) <=
                SpatialOperations.haversine(apex, vertex) + 1e-9
            ) {
                return null
            }
            val cellM = grid.cellM
            for (step in 1..RELAX_STEPS) {
                val shiftM = cellM * step / RELAX_STEPS
                val moved = curve.mapIndexed { i, p ->
                    if (i == 0 || i == curve.lastIndex) p else toward(p, vertex, shiftM)
                }
                if (moved.any { !inBox(it) }) continue
                if (clears(moved)) return moved
            }
            return null
        }

        private fun toward(p: LatLng, target: LatLng, metres: Double): LatLng {
            val dist = SpatialOperations.haversine(p, target)
            if (dist < 1e-9) return p
            val k = min(metres, dist) / dist
            return LatLng(p.latitude + (target.latitude - p.latitude) * k, p.longitude + (target.longitude - p.longitude) * k)
        }

        private fun inBox(p: LatLng): Boolean =
            p.latitude in box.latSouth..box.latNorth && p.longitude in box.lonWest..box.lonEast
    }
}
