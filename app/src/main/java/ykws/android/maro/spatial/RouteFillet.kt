package ykws.android.maro.spatial

import ykws.android.maro.data.model.RoutePoint
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * **The fillet.** One geometric pass that rounds each corner of a searched route with the arc the
 * comfort cap implies, where the water has room for it.
 *
 * The radius is `R = v² / a` — `a` being [`ykws.android.maro.config.AppConfig.routeTurnLateralAccelMps2`],
 * the lateral acceleration a turn may hold, and `v` the speed of the legs meeting at the vertex. That
 * speed is the leg's **own** — the length over the time the geometry alone gives it — handed in by the
 * search as [apply]'s `legSpeedsMps`, never read back off the leg times: those have already been
 * charged the turn's comfort price at the same vertex, so on a 60 m leg at 28 kn the charge alone
 * dragged the derived speed down to 9.3 m/s and the arc's radius to 86 m where the cap asks 207. The
 * cap is held at the **faster** of the two legs, because the boat arrives at that speed and the arc is
 * where the cap has to hold; that is the conservative reading of the pair, and it can never allow a
 * tighter turn than either leg with its own speed would.
 *
 * **Nothing is assumed about the water, and nothing about the price.** Every candidate arc is
 * verified point by point against [insideWater] — supplied by the search as the mesh's own exact
 * containment test plus the water oracle it prices with — and against [apply]'s `arcAdmissible`,
 * which is the search's own cost model: an arc that would be dearer than the corner it replaces is
 * refused, because the drawn line's own cost must never exceed the seconds the plan was built on. The
 * ideal radius is halved until a candidate passes both, and **the three tests run in one order —
 * geometry, then water, then price** — so the count that ends up naming a refusal names a constraint
 * the pass really consulted. A vertex no candidate passes at keeps its corner and is counted, split by
 * which test refused the largest candidate the pass could draw, never silently drawn as a curve it is
 * not.
 *
 * **The pass is pure and it is geometric, and it reads no clock.** It returns the line, the limit each
 * drawn sub-leg inherits from the chain leg it was cut back along, and the counts — nothing else. It
 * does not touch times: the plan's own seconds are read off the line it returns, by the one home that
 * owns them ([`RoutePlanTiming.drawnLegSeconds`]), so the times that used to be redistributed here
 * were a second clock for the same line, dead in production and free to disagree. It runs inside the
 * search's own result construction, so the preview, the confirmed route and the saved course are one
 * line.
 */
object RouteFillet {

    /** What the pass produced: the smoothed line, the limit each sub-leg carries, and each corner's fate. */
    class Result(
        val points: List<RoutePoint>,
        /**
         * The limit (kn) in force over each output leg, in the same order as [points] — so the drawn
         * line carries the price it was cut at as it leaves this pass.
         *
         * **The rule is inheritance, not a query.** A straight run takes the limit of the chain leg it
         * lies on, and an arc's own chords — which belong to neither leg alone — take the **most
         * restrictive** of the two legs they cut back along, because a boat on that span is bound by
         * the lowest limit in force. Nothing here asks the live layer about a point: the chain edge
         * already carries its limit, and that is what makes the plan's clock exact on the band.
         */
        val legLimitKn: List<Double> = emptyList(),
        val smoothed: Int,
        val reduced: Int,
        val sharp: Int,
        /**
         * The largest radius the water actually allowed, in metres — 0 when no corner took an arc.
         *
         * Reported because "sharp everywhere" and "smoothed at a radius too small to see" read
         * identically off the three counts alone: the radius is what says whether the cap was the
         * limit or the mesh's own spacing was.
         */
        val largestRadiusM: Double = 0.0,
        /**
         * Why the sharp corners stayed sharp, split by what refused them; the counts add up to
         * [sharp].
         *
         * **One refusal per corner, and it is the largest candidate's** — the tests run in this order,
         * geometry, water, price, and the first candidate that fails one of them is the corner's
         * verdict. A corner refused by the price at the ideal radius and by the water at a halved one
         * is therefore counted as the **price's**: naming it the water's would name a constraint the
         * price never let be consulted, which is a bug report rather than a measurement.
         *
         * [sharpNoRoom] is the count where no candidate radius was ever within the tangent cutback
         * the two legs allow — no test was reached at all, because the **legs**, hence the mesh's own
         * spacing, are the limit.
         * [sharpNoWater] is the count where an arc was geometrically sound and a sample of it lay
         * outside the kept water — the **water beside the corner** is the limit.
         * [sharpNoGeometry] is the count where no arc could even be drawn at that corner at all — a
         * degenerate leg, or two that double back on each other — so there is no radius to bisect: the
         * chain the search drew, not the pass, is what refused it. It is reported as a count rather
         * than waved through because a large one *is* a finding: it read 425 of 444 while the arc was
         * built with its centre at `R / sin(θ / 2)`, and a number anywhere near the corner count after
         * this fix would say the same defect had come back.
         *
         * The split is what makes "the fillet buys nothing here" a finding instead of a shrug: a
         * hundred corners refused for water and none for leg room says the mesh's coverage is the
         * limit, not the cap and not the spacing.
         */
        val sharpNoRoom: Int = 0,
        val sharpNoWater: Int = 0,
        val sharpNoGeometry: Int = 0,
        /**
         * The count where the arc was **built, found to stand on water, and dearer than the corner it
         * would replace** — a price refusal, reported apart from the other three for the same reason
         * they are reported apart from each other. Both of those first two facts are established here
         * rather than assumed: the geometry and the water are consulted before the price is, so a
         * corner counted under this name really was sound and really was on water.
         *
         * It is not a rare shape: a corner between a fast leg and a slow one pays its whole arc at the
         * slow limit where the chain paid only that side's cutback, so the arc costs
         * `R·θ/2·(1/v_slow − 1/v_fast)` more than the corner and the corner stays sharp. That is P0
         * read as a policy — a rounded corner between two speeds is a lie about the clock, and the
         * invariant in [`ykws.android.maro.spatial.mesh.RouteSearch`] refuses to let one be drawn.
         */
        val sharpNoPrice: Int = 0
    )

    /**
     * Rounds every corner of [points] that the water allows.
     *
     * @param legSpeedsMps each leg's own speed (m/s), one shorter than [points] and free of any turn
     *        price: the radius is read from these.
     * @param lateralAccelMps2 the comfort cap (m/s²); at or below zero the pass is a no-op.
     * @param arcAdmissible the **price** side of the same decision the water test makes, asked once
     *        per candidate radius with the corner's own numbers: a candidate is taken only where this
     *        and the water agree. It defaults to admitting everything, which is the pass as it stood
     *        before the cost model was handed to it — see
     *        [`ykws.android.maro.spatial.mesh.RouteSearch.arcNoWorse`], its caller.
     * @param insideWater the exact test a smoothed point must pass — mesh containment plus water.
     */
    fun apply(
        points: List<RoutePoint>,
        legSpeedsMps: List<Double>,
        lateralAccelMps2: Double,
        /**
         * The limit (kn) in force over each chain leg, one shorter than [points] — handed in so the
         * drawn sub-legs can carry the limit each of them inherits; see [Result.legLimitKn]. An empty
         * list means the caller prices no limits, and every output leg then inherits none.
         */
        legLimitKn: List<Double> = emptyList(),
        arcAdmissible: (vertex: Int, radiusM: Double, cutM: Double, turnRad: Double) -> Boolean =
            { _, _, _, _ -> true },
        insideWater: (latitude: Double, longitude: Double) -> Boolean
    ): Result {
        val n = points.size
        if (n < 3 || lateralAccelMps2 <= 0.0 || legSpeedsMps.size < n - 1) {
            return Result(points, legLimitKn, 0, 0, 0, 0.0)
        }

        val legLengths = DoubleArray(n - 1)
        for (i in 0 until n - 1) legLengths[i] = metresBetween(points[i], points[i + 1])

        // ── Pass one: the arc each corner can take ────────────────────────────────
        val arcs = arrayOfNulls<Arc>(n)
        var smoothed = 0
        var reduced = 0
        var sharp = 0
        var sharpNoRoom = 0
        var sharpNoWater = 0
        var sharpNoGeometry = 0
        var sharpNoPrice = 0
        var largestRadiusM = 0.0

        for (i in 1 until n - 1) {
            val turn = RouteTurnGeometry.turnRadians(points[i - 1], points[i], points[i + 1])
            if (turn < RouteTurnGeometry.MIN_TURN_RAD || turn > RouteTurnGeometry.MAX_TURN_RAD) {
                continue
            }
            val speedMps = turnSpeedMps(legSpeedsMps, i)
            if (speedMps <= 0.0) continue
            val ideal = RouteTurnGeometry.radiusM(speedMps, lateralAccelMps2)
            // A tangent point may not eat more than this share of a leg, so two neighbouring arcs
            // can never meet in the middle: at least a tenth of the leg stays straight between them.
            val cutLimit = RouteTurnGeometry.cutLimitM(min(legLengths[i - 1], legLengths[i]))

            var candidate = ideal
            var accepted: Arc? = null
            // **The largest candidate's own refusal, and only the first one.** The three tests run in
            // the order the counts are read off — geometry, then water, then price — so whichever one
            // refused the largest arc the corner could carry is the name this corner is counted under,
            // and no count claims a constraint the pass never consulted. Reading the price last is not
            // free: a candidate the price would refuse is geometry-built and sampled first, which is
            // the cost of a count that tells the truth about what refused it.
            var refusal: Refusal? = null
            while (candidate >= MIN_RADIUS_M) {
                val cut = RouteTurnGeometry.cutM(candidate, turn)
                if (cut <= cutLimit) {
                    val geometry =
                        geometryFor(points[i - 1], points[i], points[i + 1], candidate, cut, turn)
                    if (geometry == null) {
                        if (refusal == null) refusal = Refusal.GEOMETRY
                    } else if (!fits(geometry, insideWater)) {
                        if (refusal == null) refusal = Refusal.WATER
                    } else if (!arcAdmissible(i, candidate, cut, turn)) {
                        if (refusal == null) refusal = Refusal.PRICE
                    } else {
                        accepted = Arc(samplesOf(geometry), geometry.lengthM, cut)
                        break
                    }
                }
                candidate /= 2.0
            }
            if (accepted == null) {
                sharp++
                when (refusal) {
                    Refusal.WATER -> sharpNoWater++
                    Refusal.GEOMETRY -> sharpNoGeometry++
                    Refusal.PRICE -> sharpNoPrice++
                    null -> sharpNoRoom++
                }
                continue
            }
            arcs[i] = accepted
            if (candidate > largestRadiusM) largestRadiusM = candidate
            if (candidate >= ideal * SMOOTHED_TOLERANCE) smoothed++ else reduced++
        }

        if (smoothed + reduced == 0) {
            // Nothing to smooth: hand the search's own line back untouched, without a copy — and the
            // limits with it, since no sub-leg was made and each leg is still the chain's own.
            return Result(
                points, legLimitKn, 0, 0, sharp, largestRadiusM,
                sharpNoRoom, sharpNoWater, sharpNoGeometry, sharpNoPrice
            )
        }

        // ── Pass two: the line, and the limit each sub-leg inherits ───────────────
        val outPoints = ArrayList<RoutePoint>(n)
        val outLimits = ArrayList<Double>(n)
        outPoints.add(points[0])

        for (i in 1 until n - 1) {
            val arc = arcs[i]
            // The leg this run lies on, and the limit in force over it: the run reaches vertex i, so
            // the leg it was cut from is the one arriving there.
            val incomingLimitKn = legLimitKn.getOrElse(i - 1) { Double.MAX_VALUE }
            if (arc == null) {
                outPoints.add(points[i])
                outLimits.add(incomingLimitKn)
                continue
            }

            outPoints.add(arc.points.first())
            outLimits.add(incomingLimitKn)
            // The arc's own chords span both legs, so they carry the more restrictive of the two.
            val arcLimitKn = min(incomingLimitKn, legLimitKn.getOrElse(i) { Double.MAX_VALUE })
            for (k in 1 until arc.points.size) {
                outPoints.add(arc.points[k])
                outLimits.add(arcLimitKn)
            }
        }

        outPoints.add(points[n - 1])
        outLimits.add(legLimitKn.getOrElse(n - 2) { Double.MAX_VALUE })

        return Result(
            outPoints, outLimits, smoothed, reduced, sharp, largestRadiusM,
            sharpNoRoom, sharpNoWater, sharpNoGeometry, sharpNoPrice
        )
    }

    /** What refused the largest candidate arc at a corner, in the order the pass consults them. */
    private enum class Refusal { GEOMETRY, WATER, PRICE }

    // ── The arc ───────────────────────────────────────────────────────────────

    /** One rounded corner: its samples, its own length, and how far back it cuts along each leg. */
    private class Arc(val points: List<RoutePoint>, val lengthM: Double, val cutM: Double)

    /**
     * The arc of [radiusM] that would round the turn at [v], in the vertex's own frame — or null when
     * the two legs cannot carry one at all: a degenerate leg, or two that double back on each other.
     *
     * The circle is tangent to both legs at [cutM] from the vertex, its centre on the inside of the
     * corner at `radius / cos(θ / 2)` — see [`RouteTurnGeometry`], which owns that reading and the
     * cutback above it. Building it allocates nothing — the samples come later, and only once a
     * candidate has been found to fit — so a refused candidate costs arithmetic alone.
     */
    private fun geometryFor(
        a: RoutePoint,
        v: RoutePoint,
        b: RoutePoint,
        radiusM: Double,
        cutM: Double,
        turnRad: Double
    ): Geometry? = geometryOf(Frame(v.latitude, v.longitude), a, v, b, radiusM, cutM, turnRad)

    private class Frame(val lat0: Double, val lon0: Double) {
        private val metresPerDegreeLat = SpatialOperations.EARTH_RADIUS_M * PI / 180.0
        private val metresPerDegreeLon = metresPerDegreeLat * cos(lat0 * PI / 180.0)

        fun x(point: RoutePoint): Double = (point.longitude - lon0) * metresPerDegreeLon
        fun y(point: RoutePoint): Double = (point.latitude - lat0) * metresPerDegreeLat

        fun point(x: Double, y: Double): RoutePoint =
            RoutePoint(lat0 + y / metresPerDegreeLat, lon0 + x / metresPerDegreeLon)
    }

    private class Geometry(
        val frame: Frame,
        val radiusM: Double,
        val centreX: Double,
        val centreY: Double,
        val startAngle: Double,
        val sweep: Double
    ) {
        val lengthM: Double get() = radiusM * abs(sweep)

        fun pointAt(angle: Double): RoutePoint =
            frame.point(centreX + radiusM * cos(angle), centreY + radiusM * sin(angle))
    }

    private fun geometryOf(
        frame: Frame,
        a: RoutePoint,
        v: RoutePoint,
        b: RoutePoint,
        radiusM: Double,
        cutM: Double,
        turnRad: Double
    ): Geometry? {
        val ax = frame.x(a)
        val ay = frame.y(a)
        val bx = frame.x(b)
        val by = frame.y(b)
        val lengthA = sqrt(ax * ax + ay * ay)
        val lengthB = sqrt(bx * bx + by * by)
        if (lengthA <= 0.0 || lengthB <= 0.0) return null

        // Directions away from the vertex towards each end, and the bisector that points into the
        // corner — the side the arc is drawn on.
        val uax = ax / lengthA
        val uay = ay / lengthA
        val ubx = bx / lengthB
        val uby = by / lengthB
        val diagonal = sqrt((uax + ubx) * (uax + ubx) + (uay + uby) * (uay + uby))
        if (diagonal <= REVERSAL_EPSILON) return null
        val distance = RouteTurnGeometry.centreDistanceM(radiusM, turnRad)
        val centreX = (uax + ubx) / diagonal * distance
        val centreY = (uay + uby) / diagonal * distance

        val startAngle = atan2(uay * cutM - centreY, uax * cutM - centreX)
        val endAngle = atan2(uby * cutM - centreY, ubx * cutM - centreX)
        val sweep = normalisedRadians(endAngle - startAngle)
        // The sweep must be the turn itself: anything else means the two legs were read wrongly,
        // and a wrong arc is worse than a sharp corner.
        if (abs(abs(sweep) - turnRad) > SWEEP_TOLERANCE_RAD) return null

        return Geometry(frame, radiusM, centreX, centreY, startAngle, sweep)
    }

    /** Walks the arc's samples without allocating, so a refused candidate costs only arithmetic. */
    private fun fits(geometry: Geometry, insideWater: (Double, Double) -> Boolean): Boolean {
        val steps = sampleSteps(geometry)
        for (k in 0..steps) {
            val angle = geometry.startAngle + geometry.sweep * k / steps
            val point = geometry.pointAt(angle)
            if (!insideWater(point.latitude, point.longitude)) return false
        }
        return true
    }

    private fun samplesOf(geometry: Geometry): List<RoutePoint> {
        val steps = sampleSteps(geometry)
        val out = ArrayList<RoutePoint>(steps + 1)
        for (k in 0..steps) {
            out.add(geometry.pointAt(geometry.startAngle + geometry.sweep * k / steps))
        }
        return out
    }

    /** One sample every [SAMPLE_M], never fewer than two — so the drawn line reads smooth. */
    private fun sampleSteps(geometry: Geometry): Int = chordCount(geometry.lengthM)

    // ── Geometry ──────────────────────────────────────────────────────────────

    /**
     * The speed a boat turns at: the faster of the two legs meeting at the vertex, in m/s.
     *
     * The speeds arrive from the search, which knows each leg's own time before the turn's price is
     * added to it. Deriving them here from [RouteFillet.apply]'s leg times instead would read the
     * price back as if it were geometry, and a short leg is all price: the arc would shrink exactly
     * where the corner is sharpest.
     */
    private fun turnSpeedMps(legSpeedsMps: List<Double>, vertex: Int): Double =
        max(legSpeedsMps[vertex - 1], legSpeedsMps[vertex])

    private fun normalisedRadians(angle: Double): Double {
        var a = angle
        while (a > PI) a -= 2.0 * PI
        while (a < -PI) a += 2.0 * PI
        return a
    }

    private fun metresBetween(from: RoutePoint, to: RoutePoint): Double {
        val dLat = (to.latitude - from.latitude) * PI / 180.0
        val dLon = (to.longitude - from.longitude) * PI / 180.0
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(from.latitude * PI / 180.0) * cos(to.latitude * PI / 180.0) * sin(dLon / 2) * sin(dLon / 2)
        return 2.0 * SpatialOperations.EARTH_RADIUS_M * asin(min(1.0, sqrt(a)))
    }

    /**
     * An arc point every this many metres, so the line reads smooth without turning fat.
     *
     * Public because [chordCount] is read from it, and the price test has to charge an arc the chords
     * this pass will draw it as: see [`RoutePlanTiming.arcPriceSec`] and its caller
     * [`ykws.android.maro.spatial.mesh.RouteSearch.arcNoWorse`].
     */
    const val SAMPLE_M = 10.0

    /**
     * How many chords an arc of [arcLengthM] is drawn as — one per [SAMPLE_M], never fewer than one.
     *
     * Public because the price test has to charge the arc the **chords this pass will draw**, and a
     * second spelling of the count would be a second answer waiting to disagree with the line it
     * prices.
     */
    fun chordCount(arcLengthM: Double): Int = max(1, ceil(arcLengthM / SAMPLE_M).toInt())

    /** The smallest radius worth drawing; below it the bisection stops and the corner stays. */
    private const val MIN_RADIUS_M = 1.0

    /** A radius within this fraction of the ideal one counts as the ideal one. */
    private const val SMOOTHED_TOLERANCE = 0.999

    /** Two legs that double back on each other: no bisector exists, so no arc does either. */
    private const val REVERSAL_EPSILON = 1e-6

    /** How far the sweep may differ from the turn before the arc is refused as misread. */
    private const val SWEEP_TOLERANCE_RAD = 1e-6
}
