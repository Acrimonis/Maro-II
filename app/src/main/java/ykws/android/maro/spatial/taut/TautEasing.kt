package ykws.android.maro.spatial.taut

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.spatial.RoutePlanTiming
import ykws.android.maro.spatial.RouteTurnGeometry
import ykws.android.maro.spatial.Units

/**
 * **The corner, in one home, called by both the price and the drawing.**
 *
 * The retired engine kept three copies of a turn's formula and shipped a fillet that never drew a curve;
 * here a corner has exactly one description — [Turn] — and both the search's price and the emitted line
 * are read off it. Nothing else in the tracer computes a radius, a cutback or a chord.
 *
 * **What a corner is, in the order the design settled it.** An **easing spiral** where the water allows:
 * the curvature ramps from zero to the corner's own peak and back, so the turn reads as a trajectory
 * rather than as a round turn. A **circular arc** where it does not — the same construction with the
 * transitions shortened to nothing, which is why the two are one mechanism rather than two. And the
 * **sharp vertex** the search drew, where neither fits. The fit test **shortens the transition before it
 * reduces the radius**, so a corner gives up its easing before it gives up its speed; the radius it
 * finally holds is what sets the speed, `v = √(a·r)`, from the tightest point of the turn.
 *
 * **A sharp corner is not a free corner** (§17 item 2). A corner no curve fitted still has a speed: the
 * boat has to be slow enough to round it, and the price of that slowdown is §12.3's brake-and-accelerate
 * pair. That speed is written on [Turn.speedMps] — the one field the price and the assembly both read —
 * so the corner the water forced tightest is charged like any other instead of being the cheapest step
 * in the search.
 *
 * **The geometry closes exactly rather than approximately.** The curve is integrated from the entry
 * tangent point in the vertex's own frame and then slid back along the incoming leg until its end lands
 * on the outgoing one — a linear solve, so no residual is left to hide a turn that does not meet its own
 * legs. What is approximate is only the **emission**: the curve reaches the line as chords stepping the
 * shorter of [CHORD_STEP_M] and [CHORD_STEP_RAD] of arc, about nine points a corner at any radius.
 *
 * **The price is the same geometry, read off the drawn clock.** [`RoutePlanTiming.drawnLegSeconds`] is
 * the app's one home for what a polyline costs, so a corner's price is that clock's own reading of the
 * sub-polyline `previous → entry tangent → chords → exit tangent → next`, less the two leg times it
 * replaces. The search therefore accumulates the very quantity the drawn line is timed at, instead of a
 * second model that could disagree with it.
 *
 * **The two stubs are charged at the world's own nominal, and the corner is sized at the limit in force
 * at its vertex.** A leg carries limits the caller's world resolved over each piece of it, so the clock
 * is handed `∞` at both ends and the splitter finds what is in force over the metres a stub really
 * covers ([`TautGraph.drawnPieces`]); charging the stub at the leg's dearest limit prices metres
 * standing in open water at a zone's own value — the phantom price item 1 names. And a corner is sized
 * from [`TautGraph.limitAt`], never from the dearest limit anywhere along either leg: a leg may clip a
 * zone and be priced over that clip alone, while the corner at its end stands in the water the boat is
 * really in. Only the emitted chords carry a limit of this file's own.
 */
internal object TautEasing {

    /** The chord step's length (m): the shorter of this and [CHORD_STEP_RAD] sets each chord. */
    const val CHORD_STEP_M = 10.0

    /** The chord step's deflection (rad) — 10° of arc, the other half of "the shorter of the two". */
    val CHORD_STEP_RAD: Double = 10.0 * PI / 180.0

    /**
     * Below this radius no arc is drawn at all and the corner keeps its vertex — **the ladder's floor**.
     *
     * It bounds the ladder and not the water, which is why the speed a sharp corner is charged is an
     * **upper** bound on the real corner speed for the water case alone: a radius that failed *because
     * the water refused it* is somewhere below this floor and unknown, so `√(a·r_floor)` understates the
     * slowdown, and the charge is a floor rather than a claim about the geometry (§17 item 2). For the
     * other three causes it is a bare number and is named as one. A different corner speed wants a third
     * key, and that is the user's.
     */
    const val MIN_RADIUS_M = 2.0

    /** How many times the radius may be halved before the corner is left sharp. */
    private const val RADIUS_HALVINGS = 6

    /**
     * The transition lengths tried at each radius, as a fraction of the longest one the deflection
     * allows. **The order is the fit ladder's rule**: the fullest easing first, then shortened, and only
     * then — with `0.0`, which is the plain arc — does the ladder move on to a smaller radius.
     */
    private val TRANSITION_FRACTIONS = doubleArrayOf(1.0, 0.6, 0.3, 0.0)

    /** The integration's own step (m) — far below the chord step, so only the thresholds set the chords. */
    private const val MICRO_STEP_M = 0.25

    /** How a corner was drawn. */
    enum class Kind { SPIRAL, ARC, SHARP }

    /**
     * **Why no arc fitted** — the causes counted apart (§17 item 2, five of them since item 2 of the
     * level above).
     *
     * The causes are not one number: [WATER] is the water's own answer, [RULE] is the run's own promise
     * and **not** the water's, [CUTBACK] is a geometry the graph should not have chosen, [BUILD] is a turn
     * whose curve cannot meet its own legs, and [GUARD] is the angle the ladder never even tried.
     * Counting two of them together would misattribute the others, and only [WATER] lets a speed be read
     * off the failed radius at all — which is why a rule refusal charged at that same floor was a
     * slowdown the water never asked for.
     */
    enum class SharpCause {

        /** The angle is degenerate or the speeds or the ceiling left nothing to try. */
        GUARD,

        /** The curve cannot be built between the two legs at all — a leg with no length, or no fit. */
        BUILD,

        /** Every candidate cut back further than the shorter leg allows. */
        CUTBACK,

        /** A curve was built and the fit test refused it: the water is the answer. */
        WATER,

        /**
         * **A curve the run's own rule refused** — the first run forbids a priced zone's interior, and a
         * rounding that entered one is refused by the promise rather than by the water.
         *
         * The water at the vertex allows the turn; only the search's policy does not, so the corner is
         * driven at the speed in force there and the rule's refusal is **counted and charged apart** from
         * the water's (item 2).
         */
        RULE
    }

    /**
     * One corner, as the price and the drawing both read it.
     *
     * [emitted] is the curve's own points — the entry tangent point first and the exit tangent point
     * last, chords between them — and [limitsKn] is the limit in force over each of those chords, one
     * per chord. A sharp corner emits its vertex alone with no chords, which is the line the search drew.
     */
    class Turn(
        val kind: Kind,
        val emitted: List<RoutePoint>,
        /** The limit (kn) in force over each emitted chord — the corner's own speed, or none when sharp. */
        val limitsKn: List<Double>,
        /** The peak radius the turn holds (m) — `0` on a sharp corner, where no arc fitted. */
        val radiusM: Double,
        /**
         * The speed at the tightest point (m/s) — `√(a·r)` for an eased corner, the ladder's own floor,
         * `√(a·r_floor)`, for a sharp one, and **the corner's own reference speed where the refusal was
         * the run's rule rather than the water** (item 2).
         *
         * It is **never zero**: a sharp corner is driven at the speed that made it sharp, so the price and
         * the drawn clock both charge §12.3's brake-and-accelerate pair for it — except for a rule
         * refusal, where no slowdown was forced and none is charged. Zero here was the defect §17 item 2
         * corrects — the tightest corner in the route came out the cheapest step in the search.
         */
        val speedMps: Double,
        val limitKn: Double,
        /** The emitted chords' own length (m) — the approximating polyline, not the arc it stands for. */
        val lengthM: Double,
        /** The curve's own length (m), which the chords approximate — the difference is the sampling's. */
        val arcLengthM: Double,
        /**
         * The largest deviation between an emitted chord and the curve it describes (m) — the chord's own
         * sagitta, read at the tightest chord. Printed with the seconds sampling costs, so the density is
         * a reading rather than a hidden number.
         */
        val maxSagittaM: Double,
        /** How far the turn reaches back along each leg (m) — zero on a sharp corner. */
        val cutM: Double,
        val chords: Int,
        /** Why no arc fitted, or null when one did — the four counts §17 item 2 asks for. */
        val sharpCause: SharpCause? = null
    ) {

        /**
         * **The drawn clock's own charge for this corner, less the two leg times the turn replaces —
         * and never below zero.**
         *
         * The subtraction is what makes the term the *corner's* price rather than the corner's time: the
         * search charges the full leg and takes back what the turn cut off it. An arc is shorter than the
         * legs it replaces, so the difference can come out negative, and a corner cheaper than free is
         * exactly what the sharp case's own charge refuses (`RouteTurnGeometry.turnPriceSec` is the
         * arc-minus-chord for the same reason). Clamping is not a fudge: it keeps every step's cost above
         * the distance it covers over the cruise speed, which is what the search's straight-line estimate
         * rests on — an admissible heuristic is a promise, and a negative step would break it.
         *
         * [inPriceSec] and [outPriceSec] are the **same two legs' prices as the search charged them** —
         * an edge's time summed over its own limit regions (see `LegLimits.priceSec`) — and [clock] is the
         * search's own reading of a sub-polyline, so the difference above is one computation with two
         * readings of one line rather than two computations that must be reconciled. Their defaults price
         * the legs as open water, which is all a caller holding no world can honestly say; the search,
         * which holds one, always passes the world's own sums.
         *
         * **The stubs take the world's own nominal — `∞` at both ends** (item 1). The splitter then finds
         * what is in force over the metres each stub really covers, exactly as the leg's own price summed
         * it, and only the emitted chords carry a limit this file names. Charging a stub at the leg's
         * dearest limit instead was ≈ 280 s of phantom corner price on a 1 000 m leg with a 100 m 5 kn
         * clip. [referenceLimitKn] is the limit **in force at the vertex**, read here for one thing only:
         * §12.3's brake-and-accelerate pair, whose two speeds are the one there and the corner's own.
         */
        fun priceSec(
            previous: RoutePoint,
            vertex: RoutePoint,
            next: RoutePoint,
            referenceLimitKn: Double,
            cruiseSpeedKn: Double,
            lateralAccelMps2: Double,
            longitudinalAccelMps2: Double = 0.0,
            inPriceSec: Double = RoutePlanTiming.legSeconds(
                metresBetween(previous, vertex), Double.MAX_VALUE, cruiseSpeedKn
            ),
            outPriceSec: Double = RoutePlanTiming.legSeconds(
                metresBetween(vertex, next), Double.MAX_VALUE, cruiseSpeedKn
            ),
            clock: (List<RoutePoint>, List<Double>) -> Double = { drawn, limits ->
                RoutePlanTiming.drawnLegSeconds(drawn, limits, cruiseSpeedKn, lateralAccelMps2).sum()
            }
        ): Double {
            val points = ArrayList<RoutePoint>(emitted.size + 2)
            points.add(previous)
            points.addAll(emitted)
            points.add(next)
            // `∞` at both ends: the legs' own limits are the caller's world, resolved over each piece by
            // the splitter. Only the corner's chords carry a limit of their own.
            val limits = ArrayList<Double>(emitted.size + 2)
            limits.add(Double.MAX_VALUE)
            limits.addAll(limitsKn)
            limits.add(Double.MAX_VALUE)
            val drawn = clock(points, limits)
            val price = drawn - (inPriceSec + outPriceSec)
            // §12.3's pair rides with the arc — and with the sharp vertex too, at the speed that made it
            // sharp. The boat has to be at this corner's speed when it gets there, and the braking and the
            // acceleration that costs are charged to the corner that forces them — the same term the drawn
            // clock is charged, through the clock's own home, so the search's price and the ETA cannot
            // disagree about a corner again. The speed on either side of the corner is the one in force at
            // its vertex: the corner's own reference limit.
            val referenceSpeedMps = min(cruiseSpeedKn, referenceLimitKn) * Units.MPS_PER_KNOT
            val extra = RoutePlanTiming.longitudinalSec(
                inSpeedMps = referenceSpeedMps,
                cornerSpeedMps = speedMps,
                outSpeedMps = referenceSpeedMps,
                longitudinalAccelMps2 = longitudinalAccelMps2
            )
            val total = (if (price > 0.0) price else 0.0) + extra
            return total
        }
    }

    /**
     * The turn at [vertex] between the legs `previous → vertex → next`, as the water allows.
     *
     * @param cruiseSpeedKn the free-water pace; a corner never speeds the boat above it.
     * @param referenceLimitKn the limit **in force at the vertex** — the corner's own reference speed,
     *        never the dearest limit anywhere along either leg (item 2). It sizes the corner's ideal
     *        radius and caps the speed it holds, so a corner at the end of a leg that merely clipped a
     *        zone eases at the water it stands in rather than at the zone's own value.
     * @param lateralAccelMps2 the ceiling a turn may hold — `AppConfig.routeTurnLateralAccelMps2`.
     * @param fits the fit test, asked of the emitted points: they must stand on water the route may use,
     *        and the chords between them must not cross a wall. It is the caller's, because only it knows
     *        where the walls are — and it answers with the **cause** of a refusal rather than a boolean
     *        (item 2), because a curve the run's own rule forbade is not the water's answer and must not be
     *        counted or charged as one. `null` means the curve fits.
     */
    fun fit(
        frame: Frame,
        previous: RoutePoint,
        vertex: RoutePoint,
        next: RoutePoint,
        cruiseSpeedKn: Double,
        referenceLimitKn: Double,
        lateralAccelMps2: Double,
        fits: (List<RoutePoint>) -> SharpCause?,
        /**
         * **Where the fit's own milliseconds go** (§19.2 item 2) — called once per candidate the ladder
         * builds, with the nanoseconds of the curve's own integration and of the water test that judged it.
         *
         * The fit is 93 % of a search and about 95 µs a call, and the two halves inside it lead to
         * different fixes: a dear integration wants a cheaper curve, a dear test wants a cheaper judge. So
         * the split is measured rather than assumed — the discipline that found the tolerance a weak lever.
         */
        onCandidate: (buildNanos: Long, waterNanos: Long) -> Unit = { _, _ -> }
    ): Turn {
        val vertexPt = frame.pt(LatLng(vertex.latitude, vertex.longitude))
        val speed = min(cruiseSpeedKn, referenceLimitKn) * Units.MPS_PER_KNOT
        val limitKn = referenceLimitKn
        val turnRad = RouteTurnGeometry.turnRadians(previous, vertex, next)
        if (turnRad < RouteTurnGeometry.MIN_TURN_RAD ||
            turnRad > RouteTurnGeometry.MAX_TURN_RAD ||
            speed <= 0.0 ||
            lateralAccelMps2 <= 0.0
        ) {
            return sharp(vertex, limitKn, SharpCause.GUARD, speed, lateralAccelMps2)
        }

        val legInM = metresBetween(previous, vertex)
        val legOutM = metresBetween(vertex, next)
        val cutLimit = RouteTurnGeometry.cutLimitM(min(legInM, legOutM))
        // The boat arrives at the corner's own speed — the one in force at the vertex — so the radius
        // the ceiling implies is read from that speed alone: a corner is neither eased nor slowed by a
        // limit the water at the corner does not hold it to.
        val idealRadiusM = RouteTurnGeometry.radiusM(speed, lateralAccelMps2)

        var sawCutback = false
        var sawBuild = false
        var sawRule = false
        var radius = idealRadiusM
        var halvings = 0
        while (radius >= MIN_RADIUS_M && halvings <= RADIUS_HALVINGS) {
            for (fraction in TRANSITION_FRACTIONS) {
                val buildAt = System.nanoTime()
                val candidate = build(
                    frame = frame,
                    previous = previous,
                    vertexPt = vertexPt,
                    next = next,
                    radiusM = radius,
                    transitionFraction = fraction,
                    turnRad = turnRad,
                    legSpeedMps = speed,
                    lateralAccelMps2 = lateralAccelMps2
                )
                val buildNanos = System.nanoTime() - buildAt
                if (candidate == null) {
                    sawBuild = true
                    onCandidate(buildNanos, 0L)
                    continue
                }
                if (candidate.cutM > cutLimit) {
                    sawCutback = true
                    onCandidate(buildNanos, 0L)
                    continue
                }
                val waterAt = System.nanoTime()
                val refusal = fits(candidate.emitted)
                onCandidate(buildNanos, System.nanoTime() - waterAt)
                if (refusal != null) {
                    // The cause rides out of the fit test rather than collapsing into one boolean: the
                    // run's own rule and the water are two different answers, and only the water's is a
                    // number read off a radius it refused (item 2).
                    if (refusal == SharpCause.RULE) sawRule = true
                    continue
                }
                return candidate
            }
            radius /= 2.0
            halvings++
        }
        // The five causes, in the order that attributes the ladder's last refusal: a cutback the legs
        // forbade is a geometry the graph should not have chosen and it outranks a curve that failed the
        // water's own test, while the run's rule sits between them — its own promise rather than the
        // water, and named so that the charge it carries can be read apart from the water's.
        val cause = when {
            sawCutback -> SharpCause.CUTBACK
            sawBuild -> SharpCause.BUILD
            sawRule -> SharpCause.RULE
            else -> SharpCause.WATER
        }
        return sharp(vertex, limitKn, cause, speed, lateralAccelMps2)
    }

    /**
     * **A corner no arc fitted: the search's own vertex, emitted alone, and driven at the speed that
     * made it sharp.**
     *
     * The speed is the ladder's own floor, `√(a · r_floor)` — an **upper** bound on the corner speed for
     * the water case (a radius the water refused is somewhere below that floor, so the real slowdown is
     * at least this) and a bare number for the other causes, named as such rather than dressed as
     * geometry. It is never above the corner's own reference speed: a corner cannot be driven faster
     * than the water at its vertex allows. The limit the corner is *named* at is that same limit in
     * force at the vertex, because that is where the boat is when it turns.
     *
     * **A rule refusal is the one case that pays nothing.** The curve was refused by the run's own
     * promise, not by the water, so the corner is driven at the water's own speed and §12.3's pair is
     * zero: charging the ladder's floor there was ≈ 30 s a corner of a slowdown nothing had forced, and
     * the reading claimed it was the water's own bound (item 2).
     */
    private fun sharp(
        vertex: RoutePoint,
        limitKn: Double,
        cause: SharpCause,
        referenceSpeedMps: Double,
        lateralAccelMps2: Double
    ): Turn {
        val floorSpeed = if (lateralAccelMps2 > 0.0) {
            min(sqrt(lateralAccelMps2 * MIN_RADIUS_M), referenceSpeedMps)
        } else {
            0.0
        }
        val cornerSpeed = if (cause == SharpCause.RULE) referenceSpeedMps else floorSpeed
        return Turn(
            kind = Kind.SHARP,
            emitted = listOf(vertex),
            limitsKn = emptyList(),
            radiusM = 0.0,
            speedMps = cornerSpeed,
            limitKn = limitKn,
            lengthM = 0.0,
            arcLengthM = 0.0,
            maxSagittaM = 0.0,
            cutM = 0.0,
            chords = 0,
            sharpCause = cause
        )
    }

    /**
     * The curve itself, integrated in the vertex's own frame and slid back until its end lands on the
     * outgoing leg — or null when the geometry cannot meet its own legs at all.
     */
    private fun build(
        frame: Frame,
        previous: RoutePoint,
        vertexPt: Pt,
        next: RoutePoint,
        radiusM: Double,
        transitionFraction: Double,
        turnRad: Double,
        /** The speed in force at the corner — the corner's own reference limit, in m/s. */
        legSpeedMps: Double,
        lateralAccelMps2: Double
    ): Turn? {
        val inPt = frame.pt(LatLng(previous.latitude, previous.longitude))
        val nextPt = frame.pt(LatLng(next.latitude, next.longitude))
        val ux = vertexPt.x - inPt.x
        val uy = vertexPt.y - inPt.y
        val inLength = hypot(ux, uy)
        if (inLength <= 0.0) return null
        val uInX = ux / inLength
        val uInY = uy / inLength
        val vx = nextPt.x - vertexPt.x
        val vy = nextPt.y - vertexPt.y
        val outLength = hypot(vx, vy)
        if (outLength <= 0.0) return null
        val uOutX = vx / outLength
        val uOutY = vy / outLength

        // A left turn bends towards the incoming leg's left normal, a right turn towards its right.
        val left = uInX * uOutY - uInY * uOutX > 0.0
        val side = if (left) 1.0 else -1.0

        // θ = (l + m) / R with l the length of **each** ramp and m the circular middle's, so the longest
        // transition the deflection allows is θ·R and the rest of the turn is the constant-curvature arc.
        val transitionM = transitionFraction * turnRad * radiusM
        val middleM = turnRad * radiusM - transitionM
        if (middleM < -1e-9) return null
        val totalM = 2.0 * transitionM + middleM
        if (totalM <= 0.0) return null

        val points = ArrayList<Pt>(16)
        val stepRad = ArrayList<Double>(16)
        points.add(Pt(0.0, 0.0))
        var x = 0.0
        var y = 0.0
        var heading = 0.0
        var s = 0.0
        var chordM = 0.0
        var chordRad = 0.0
        var guard = 0
        while (s < totalM - 1e-9 && guard < MAX_STEPS) {
            guard++
            val nextBreak = nextBreakpoint(s, transitionM, middleM, totalM)
            val curvature = curvatureAt(s, transitionM, middleM, radiusM, side)
            val byChord = CHORD_STEP_M - chordM
            val byTurn = if (abs(curvature) > 1e-12) (CHORD_STEP_RAD - chordRad) / abs(curvature)
            else Double.MAX_VALUE
            var step = min(min(byChord, byTurn), min(nextBreak - s, totalM - s))
            if (step < MICRO_STEP_M) step = min(MICRO_STEP_M, min(nextBreak - s, totalM - s))
            if (step <= 0.0) step = totalM - s
            val midHeading = heading + curvature * step / 2.0
            x += cos(midHeading) * step
            y += sin(midHeading) * step
            heading += curvature * step
            s += step
            chordM += step
            chordRad += abs(curvature) * step
            if (s >= nextBreak - 1e-9 || chordM >= CHORD_STEP_M - 1e-9 ||
                chordRad >= CHORD_STEP_RAD - 1e-9 || s >= totalM - 1e-9
            ) {
                points.add(Pt(x, y))
                stepRad.add(chordRad)
                chordM = 0.0
                chordRad = 0.0
            }
        }
        if (points.size < 2) return null

        // Slide the curve back along the incoming leg until its end stands on the outgoing one: the end
        // is at (x − cut, y) in the (uIn, left-normal) basis, and it must be parallel to uOut.
        val sinus = sin(turnRad)
        if (abs(sinus) < 1e-9) return null
        val cut = x - side * y * cos(turnRad) / sinus
        if (cut < 0.0 || cut > inLength || cut > outLength) return null

        val nx = -uInY
        val ny = uInX
        val emitted = points.map { point ->
            val along = point.x - cut
            val pointX = vertexPt.x + along * uInX + point.y * nx
            val pointY = vertexPt.y + along * uInY + point.y * ny
            val latLng = frame.latLng(Pt(pointX, pointY))
            RoutePoint(latLng.latitude, latLng.longitude)
        }

        val speed = min(sqrt(lateralAccelMps2 * radiusM), legSpeedMps)
        val limitKn = Units.mpsToKnots(speed)
        var length = 0.0
        var sagitta = 0.0
        for (i in 0 until emitted.size - 1) {
            val chord = metresBetween(emitted[i], emitted[i + 1])
            length += chord
            val deflection = stepRad.getOrElse(i) { 0.0 }
            val chordSagitta = chord / 2.0 * tan(deflection / 4.0)
            if (chordSagitta > sagitta) sagitta = chordSagitta
        }
        val kind = if (transitionM > 1e-6) Kind.SPIRAL else Kind.ARC
        return Turn(
            kind = kind,
            emitted = emitted,
            limitsKn = List(emitted.size - 1) { limitKn },
            radiusM = radiusM,
            speedMps = speed,
            limitKn = limitKn,
            lengthM = length,
            arcLengthM = totalM,
            maxSagittaM = sagitta,
            cutM = cut,
            chords = emitted.size - 1
        )
    }

    /** The signed curvature at arc length [s] — `0 → 1/R` over each ramp, `1/R` across the middle. */
    private fun curvatureAt(
        s: Double,
        transitionM: Double,
        middleM: Double,
        radiusM: Double,
        side: Double
    ): Double {
        val peak = side / radiusM
        if (transitionM <= 1e-12) return peak
        val ramp = if (s < transitionM) {
            s / transitionM
        } else if (s < transitionM + middleM) {
            1.0
        } else {
            ((2.0 * transitionM + middleM - s) / transitionM).coerceIn(0.0, 1.0)
        }
        return peak * ramp
    }

    /** The next arc length at which the curvature profile changes — a boundary point is always emitted. */
    private fun nextBreakpoint(
        s: Double,
        transitionM: Double,
        middleM: Double,
        totalM: Double
    ): Double {
        val breaks = doubleArrayOf(transitionM, transitionM + middleM, totalM)
        for (value in breaks) if (value > s + 1e-12) return value
        return totalM
    }

    private fun abs(value: Double): Double = if (value < 0.0) -value else value

    /** A guard on the integration: a corner that cannot be walked in this many steps is refused. */
    private const val MAX_STEPS = 4000
}
