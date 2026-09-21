package ykws.android.maro.spatial.taut

import java.util.PriorityQueue
import kotlin.math.max
import kotlin.math.min
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.spatial.RoutePlanTiming
import ykws.android.maro.spatial.SpatialOperations
import ykws.android.maro.spatial.Units

/**
 * **The search: A\* over the corridor's own corners, priced by the clock and nothing else.**
 *
 * The state is **a vertex and the direction the boat arrived from**, because a corner's cost is a
 * property of the two directions meeting at it and of nothing else — without the arrival direction the
 * search would have to charge every corner at the worst possible radius, which is the mistake the design
 * names as the one place the price would quietly invert the priority. The direction is carried as the
 * predecessor's own index rather than as a bucket: the graph's edges *are* the directions it offers, so
 * quantising them would only add an error where there is none to remove.
 *
 * **Two runs over the one graph.** The first refuses every edge that enters a priced zone's **interior**
 * — the ring minus its holes, read through the zone's own predicate — which is how "a zone is not
 * traversable while a way around exists" is enforced as a *rule* rather than as a price; when it answers,
 * that is the route. Only when it finds nothing does the second run price the zones instead of
 * forbidding them — it may cross, at the zone's own limit — and the crossing is then read off the drawn
 * line and reported by the zone's name. Neither run forbids the **margin**: the berth is priced, in
 * seconds, so it stays a preference and never a wall (§11.2, §15).
 *
 * **One home for the price and for the accumulation.** A step costs the **edge's own price** — its time
 * summed over its limit regions — plus the berth's courtesy plus the turn, read once in [stepSec]; the
 * accumulated price of a state lives in [best] alone and is read back at the end rather than summed a
 * second time from the chain, which is what stops the price the search paid and the price it reports from
 * drifting apart.
 *
 * **The drawn line is cut where the limit changes** (§17 item 1). The splitter lives on the graph
 * ([`TautGraph.drawnPieces`] → `LegLimits.regionsOf`), and the assembly calls it for every leg it emits —
 * straight leg and emitted chord alike — so each drawn piece carries the limit in force over it and the
 * drawn clock's sum is the price the search paid, by construction rather than by reconciliation.
 *
 * **A corner's two quantities are read where they belong.** Its reference limit is the one **in force at
 * its vertex** ([`TautGraph.limitAt`]), never the dearest limit along a leg that merely clipped a zone —
 * which eased and slowed corners the water never held them to (item 2) — and the clock it is priced
 * against hands its stubs the world's own nominal, so a stub's metres are priced by the water they
 * actually stand in rather than by the leg's most restrictive limit (item 1). The margin's own reading
 * runs over the same legs its price is paid on: the chain's edges, never the drawn pieces (item 4).
 *
 * **Ties break deterministically**: equal cost, then the shorter line, then the fewer turns, then the
 * vertex and the arrival's own indices — never insertion order, and never a hash's own. The same request
 * therefore returns the same line, which is what makes a navigation aid testable.
 */
internal class TautSearch(
    private val world: TautWorld,
    private val obstacles: TautObstacles,
    private val graph: TautGraph,
    private val cruiseSpeedKn: Double,
    private val lateralAccelMps2: Double,
    private val warn: (String) -> Unit,
    private val longitudinalAccelMps2: Double = 0.0,
    /**
     * **Asked once per corner fitted, so an abandoned search stops at once** (§19.2 item 4).
     *
     * A cancel seen only between phases makes a drag wait for the whole search — and a cancel is not a join,
     * so the abandoned work keeps burning cores beside the new one. One check per fit is a nanosecond on a
     * ~95 µs step, and it puts the abort inside a fraction of a millisecond of the newest aim arriving.
     */
    private val cancelCheck: () -> Unit = {}
) {

    /** What a search answered: the drawn line, its clock, and the engine's own counts. */
    class Outcome(
        val points: List<RoutePoint>,
        val legTimesSec: List<Double>,
        val legLimitKn: List<Double>,
        val distanceM: Double,
        val durationSec: Double,
        val inBand: Boolean,
        val forcedCrossingZoneNames: List<String>,
        val nodesExpanded: Int,
        /** The seconds the search itself accumulated — the ceiling the drawn line may not exceed. */
        val pricedSec: Double,
        /** How much of [pricedSec] is the berth's courtesy rather than the clock (§15). */
        val berthPriceSec: Double,
        /** The seconds §12.3's brake-and-accelerate pair cost over the whole route. */
        val longitudinalSec: Double,
        /** How many drawn legs entered a priced zone's interior — §16 step 3's crossing count. */
        val crossings: Int,
        /** Metres of the route's own legs inside a zone's margin — §16 step 3's reading (item 4). */
        val inMarginM: Double,
        val spiralCorners: Int,
        val arcCorners: Int,
        val sharpCorners: Int,
        /**
         * **The sharp corners' causes, counted apart** (§17 item 2, and item 2 of the level above).
         *
         * A sharp corner is reached by the angle or degenerate guard, by the curve failing to meet its own
         * legs, by the cutback against a leg too short, by the water, **and** by the run's own rule — and
         * only the water's is a water answer. Counting two of them together would misattribute the
         * others, so each has its own number here and the reading can say which of them a route met.
         */
        val sharpByGuard: Int,
        val sharpByBuild: Int,
        val sharpByCutback: Int,
        val sharpByWater: Int,
        /** The corners the **run's own rule** refused, apart from the water's — see `SharpCause.RULE`. */
        val sharpByRule: Int,
        /** The seconds the sharp corners' own slowdown came to — the charge item 2 exists to make. */
        val sharpChargeSec: Double,
        val largestRadiusM: Double,
        val chordDeviationM: Double,
        val samplingSec: Double,
        /**
         * **The inside of a station, priced** (§19.2 item 1) — the corner curves the search actually built
         * and the milliseconds they cost, the drawn-clock evaluations each candidate paid for and what those
         * cost.
         *
         * The four together answer the one question a phase figure cannot: whether the search's seconds are
         * in *doing less work* (stations, candidate turns) or in *making the work cheaper* (the fit and the
         * clock). The vertex levers and the per-corner lever are chosen on that answer, not on a guess.
         */
        val fitsBuilt: Int,
        val fitMillis: Long,
        val clockCalls: Int,
        val clockMillis: Long,
        /**
         * **And the fit's own inside** — the candidates the ladder built, and the milliseconds spent drawing
         * them against the milliseconds spent judging them against the water.
         *
         * The two halves point at different fixes, so they are counted apart: a dear draw wants a cheaper
         * curve, a dear judge wants a cheaper water test.
         */
        val candidatesBuilt: Int,
        val candidateBuildMillis: Long,
        val candidateWaterMillis: Long,
        val zonePricedRun: Boolean
    )

    /** The turn at each corner of the path, keyed by the vertex pair it is built between. */
    private val turnCache = HashMap<Long, TautEasing.Turn>()

    /**
     * **The turn's price, keyed the same way** — the splitter's own cost carried with it.
     *
     * A corner's price is a pure function of the triple it sits in: the two edges are what name the
     * limits, and the two edges' prices are the graph's own. So the answer is computed once per triple and
     * read back, which is what keeps the summed price and the splitter that measures it off the search's
     * hot loop while leaving the arithmetic itself in one home.
     */
    private val turnPriceCache = HashMap<Long, Double>()

    /**
     * **The search's own counters for the work inside a station** (§19.2 item 1).
     *
     * Counts are cheap ints, and the two nanosecond totals wrap only the two expensive things — the easing
     * fit and the drawn clock — rather than timing every call in the hot loop, so measuring cannot become
     * the cost being measured. A search instance serves one run, so they need no reset.
     */
    private var fitsBuilt = 0
    private var fitNanos = 0L
    private var clockCalls = 0
    private var clockNanos = 0L

    /** The fit's own inside: the candidates the ladder built, and what building and judging them cost. */
    private var candidatesBuilt = 0
    private var candidateBuildNanos = 0L
    private var candidateWaterNanos = 0L

    /** The search's own record of where a state was reached from, for the assembly's walk back. */
    private val cameFrom = HashMap<Long, Long>()

    /** The price of every state the last run reached — the accumulation's one home. */
    private val best = HashMap<Long, Double>()

    /** How much of each state's price is the berth's courtesy — read back so the clock can be compared. */
    private val berthTotals = HashMap<Long, Double>()

    /** The number of states the last run settled — the engine's own cost reading. */
    var nodesExpanded: Int = 0
        private set

    /**
     * The route, or null when neither run finds one.
     *
     * The first run's rule is the feature's own invariant; the second exists so that a zone's interior
     * never makes a harbour unreachable, and its answer always carries the crossing it took.
     */
    fun run(): Outcome? {
        val strict = search(forbidZoneInteriors = true)
        if (strict != null) return assemble(strict, zonePricedRun = false)
        val priced = search(forbidZoneInteriors = false) ?: return null
        return assemble(priced, zonePricedRun = true)
    }

    // ── The search ────────────────────────────────────────────────────────────

    private class Entry(
        val state: Long,
        val f: Double,
        val distance: Double,
        val hops: Int
    )

    /**
     * The comparator is the tie-break rule, spelled out in one place: **cost**, then the distance the
     * path has covered, then how many turns it has taken, then the state's own indices.
     */
    private val order = Comparator<Entry> { first, second ->
        var comparison = first.f.compareTo(second.f)
        if (comparison == 0) comparison = first.distance.compareTo(second.distance)
        if (comparison == 0) comparison = first.hops.compareTo(second.hops)
        if (comparison == 0) comparison = first.state.compareTo(second.state)
        comparison
    }

    private fun search(forbidZoneInteriors: Boolean): Long? {
        val start = stateKey(graph.startIndex, graph.startIndex)
        // The fit test the easing is held to depends on which run is asking — a curve may cut a zone's
        // corner when the zone is priced and may not when it is forbidden — so the cached turns belong to
        // one run and are dropped with it.
        zoneInteriorsForbidden = forbidZoneInteriors
        turnCache.clear()
        turnPriceCache.clear()
        best.clear()
        berthTotals.clear()
        distances.clear()
        hops.clear()
        cameFrom.clear()
        val queue = PriorityQueue(order)
        best[start] = 0.0
        berthTotals[start] = 0.0
        distances[start] = 0.0
        hops[start] = 0
        queue.add(Entry(start, heuristic(graph.startIndex), 0.0, 0))
        var expanded = 0

        while (queue.isNotEmpty()) {
            val entry = queue.poll()
            val state = entry.state
            val vertex = vertexOf(state)
            val previous = previousOf(state)
            val known = best[state] ?: continue
            if (entry.f > known + heuristic(vertex) + TIE_EPS_SEC) continue
            expanded++
            if (vertex == graph.aimIndex) {
                nodesExpanded = expanded
                return state
            }
            for (edge in graph.edgesFrom(vertex)) {
                val next = graph.other(edge, vertex)
                if (next == previous && previous != vertex) continue
                if (forbidZoneInteriors && graph.zoneOf(edge) >= 0) continue
                // The edge's own price: its time summed over its own limit regions, the same number the
                // drawn clock charges for the pieces the splitter emits (§17 item 1).
                val legSec = graph.priceSec(edge)
                if (!legSec.isFinite()) continue
                val berthSec = berthPriceSec(legSec, graph.berthFraction(edge))
                val nextState = stateKey(next, vertex)
                val current = best[nextState]
                // A relaxation that cannot beat what the state already holds, before the turn is fitted.
                // The turn's price is never negative, so a leg whose own seconds already exceed the
                // incumbent cannot win — and fitting the easing costs far more than the comparison, so
                // this is where the search's own bill is paid. It is exact, not a heuristic: a candidate
                // above the incumbent by more than the tie's tolerance is one `better` would refuse.
                if (current != null && known + legSec + berthSec > current + TIE_EPS_SEC) continue
                val turnSec = turnPriceAt(previous, vertex, next)
                val candidate = known + legSec + berthSec + turnSec
                val nextDistance = (distances[state] ?: 0.0) + graph.lengthM(edge)
                val nextHops = (hops[state] ?: 0) + 1
                if (current != null) {
                    val currentDistance = distances[nextState] ?: 0.0
                    val currentHops = hops[nextState] ?: 0
                    if (!better(
                            candidate, nextDistance, nextHops,
                            current, currentDistance, currentHops
                        )
                    ) {
                        continue
                    }
                }
                best[nextState] = candidate
                berthTotals[nextState] = (berthTotals[state] ?: 0.0) + berthSec
                distances[nextState] = nextDistance
                hops[nextState] = nextHops
                cameFrom[nextState] = state
                queue.add(Entry(nextState, candidate + heuristic(next), nextDistance, nextHops))
            }
        }
        nodesExpanded = expanded
        return null
    }

    private val distances = HashMap<Long, Double>()
    private val hops = HashMap<Long, Int>()

    private fun better(
        candidateTime: Double,
        candidateDistance: Double,
        candidateHops: Int,
        currentTime: Double,
        currentDistance: Double,
        currentHops: Int
    ): Boolean {
        if (candidateTime < currentTime - TIE_EPS_SEC) return true
        if (candidateTime > currentTime + TIE_EPS_SEC) return false
        if (candidateDistance < currentDistance - 1e-6) return true
        if (candidateDistance > currentDistance + 1e-6) return false
        return candidateHops < currentHops
    }

    /**
     * **The berth's own seconds** — the one place its arithmetic lives.
     *
     * A leg inside the margin pays extra time rising as the boundary nears, `legSeconds × BERTH_MAX_PRICE
     * × fraction`, so the courtesy is expressed in the clock's own unit and never as a second quantity
     * beside it (§11.2, §15). The fraction comes from the margin's one home, which is also what the
     * in-margin reading is computed from — so the price and the reading measure one thing.
     */
    private fun berthPriceSec(legSec: Double, fraction: Double): Double =
        if (fraction > 0.0) legSec * TautZoneSet.BERTH_MAX_PRICE * fraction else 0.0

    /** The straight-line lower bound: at no time does the boat cover ground faster than [cruiseSpeedKn]. */
    private fun heuristic(vertex: Int): Double {
        val aim = graph.vertices[graph.aimIndex]
        val point = graph.vertices[vertex]
        val metres = SpatialOperations.haversine(
            LatLng(point.latitude, point.longitude),
            LatLng(aim.latitude, aim.longitude)
        )
        return metres / (cruiseSpeedKn * Units.MPS_PER_KNOT)
    }

    /**
     * **The drawn clock, as the search sees it** — the one home's own door; see [drawnClockOf], which the
     * readings are handed too so no second clock can be priced beside this one (item 5).
     */
    private fun drawnClock(points: List<RoutePoint>, limits: List<Double>): Double {
        val clockAt = System.nanoTime()
        val seconds = drawnClockOf(graph, points, limits, cruiseSpeedKn, lateralAccelMps2)
        // Counted here, at the search's own door: every price that reads the clock comes through this call,
        // so the count is the evaluations the run really paid for and the milliseconds are what they cost
        // (§19.2 item 1). The readings' own uses of [drawnClockOf] are outside this door and outside the
        // count, which is the honest way round — they are the instrument, not the search.
        clockCalls++
        clockNanos += System.nanoTime() - clockAt
        return seconds
    }

    /**
     * **The corner's price at one triple of vertices — computed once, read back thereafter.**
     *
     * Every argument the price reads is a property of the triple: the two edges carry their own prices,
     * the reference limit is the one in force at the vertex, and the clock is the search's own. The cache
     * is dropped with the run, because the fit test the easing is held to depends on which run is asking.
     */
    private fun turnPriceAt(previous: Int, vertex: Int, next: Int): Double {
        if (previous == vertex) return 0.0
        val key = turnKey(previous, vertex, next)
        turnPriceCache[key]?.let { return it }
        val inEdge = graph.edgeBetween(previous, vertex)
        val outEdge = graph.edgeBetween(vertex, next)
        // **The corner's reference limit is the one in force at the vertex** (item 2), never the dearest
        // limit anywhere along either leg: a leg that merely clips a zone is priced over the clip alone,
        // and the corner at its end stands in the water the boat is really in.
        val referenceLimit = graph.limitAt(vertex)
        val price = turnAt(previous, vertex, next, referenceLimit).priceSec(
            previous = graph.vertices[previous],
            vertex = graph.vertices[vertex],
            next = graph.vertices[next],
            referenceLimitKn = referenceLimit,
            cruiseSpeedKn = cruiseSpeedKn,
            lateralAccelMps2 = lateralAccelMps2,
            longitudinalAccelMps2 = longitudinalAccelMps2,
            inPriceSec = if (inEdge >= 0) graph.priceSec(inEdge) else Double.MAX_VALUE,
            outPriceSec = if (outEdge >= 0) graph.priceSec(outEdge) else Double.MAX_VALUE,
            clock = ::drawnClock
        )
        turnPriceCache[key] = price
        return price
    }

    private fun turnAt(
        previous: Int,
        vertex: Int,
        next: Int,
        referenceLimitKn: Double
    ): TautEasing.Turn {
        val key = turnKey(previous, vertex, next)
        turnCache[key]?.let { return it }
        cancelCheck()
        val fitAt = System.nanoTime()
        val turn = TautEasing.fit(
            frame = obstacles.frame,
            previous = graph.vertices[previous],
            vertex = graph.vertices[vertex],
            next = graph.vertices[next],
            cruiseSpeedKn = cruiseSpeedKn,
            referenceLimitKn = referenceLimitKn,
            lateralAccelMps2 = lateralAccelMps2,
            fits = { emitted -> fits(previous, vertex, emitted, next) },
            onCandidate = { buildNanos, waterNanos ->
                candidatesBuilt++
                candidateBuildNanos += buildNanos
                candidateWaterNanos += waterNanos
            }
        )
        fitsBuilt++
        fitNanos += System.nanoTime() - fitAt
        turnCache[key] = turn
        return turn
    }

    /**
     * The fit test the easing is held to: every emitted point stands on water the boat may use, and no
     * chord leaves it.
     *
     * The test is asked of the **undilated** obstacles, and that is the whole difference between an
     * easing that fires and one that cannot: a turn cuts *towards* the inside of its corner, which is
     * where the berth lies, so a fit against the dilated wall would refuse every candidate and leave
     * every corner sharp. A rounded corner therefore spends the berth — and is held to the water itself,
     * which it may never spend.
     *
     * **It answers with the refusal's cause rather than with a boolean** (item 2). Two different things
     * refuse a curve here — the water, and the run's own rule when the zone's interior is forbidden — and
     * one boolean made the second indistinguishable from the first, so a rule-refused corner was counted
     * in `sharpByWater` and charged at the ladder's floor from a cruise reference: a slowdown the water
     * never forced, on a curve the water had allowed.
     */
    private fun fits(
        previous: Int,
        vertex: Int,
        emitted: List<RoutePoint>,
        next: Int
    ): TautEasing.SharpCause? = fitsCurve(
        obstacles = obstacles,
        zones = graph.zones,
        forbidZoneInteriors = zoneInteriorsForbidden,
        previous = graph.vertices[previous],
        emitted = emitted,
        next = graph.vertices[next]
    )

    /** Whether the run in progress forbids zone interiors — read by [fits], set by the run that asks. */
    private var zoneInteriorsForbidden = false

    // ── The drawn line ────────────────────────────────────────────────────────

    /**
     * Turns the search's state chain into the line the app draws: the straight legs it found, with the
     * easing's own chords written in at every corner, **each leg cut where the limit in force changes**,
     * and the limit named for every drawn piece.
     *
     * The times are not computed here: [`RoutePlanTiming.drawnLegSeconds`] reads them off the polyline it
     * is handed, at the limits this function names, so the trip figure, the panel and the saved course
     * are one clock — the app's own. The one term that clock cannot see is §12.3's brake-and-accelerate
     * pair, because it is not a leg at all but the price of the corner's speed being lower than its
     * legs' — so it is added here from the clock's own home, once per corner, on the first drawn leg the
     * corner owns.
     */
    private fun assemble(goalState: Long, zonePricedRun: Boolean): Outcome {
        val chain = ArrayList<Int>()
        var state = goalState
        while (true) {
            chain.add(vertexOf(state))
            val previous = previousOf(state)
            if (previous == vertexOf(state)) break
            state = cameFrom[state] ?: break
        }
        chain.reverse()

        // The straight line the search found, before it is cut: one point list, one nominal limit per leg
        // (∞ wherever the world supplies the limit, the corner's own speed over an emitted chord) and the
        // corner that owns each leg, so the brake-and-accelerate charge lands where the corner is.
        val rawPoints = ArrayList<RoutePoint>()
        val rawLimits = ArrayList<Double>()
        val rawOwners = ArrayList<Int>()
        rawPoints.add(graph.vertices[chain.first()])

        var spirals = 0
        var arcs = 0
        var sharp = 0
        var byGuard = 0
        var byBuild = 0
        var byCutback = 0
        var byWater = 0
        var byRule = 0
        var sharpChargeSec = 0.0
        var largestRadiusM = 0.0
        var chordDeviationM = 0.0
        var samplingSec = 0.0
        val extraByCorner = HashMap<Int, Double>()

        for (k in 1 until chain.size - 1) {
            val previous = chain[k - 1]
            val vertex = chain[k]
            val next = chain[k + 1]
            // The corner's reference limit: the water at the vertex, not the dearest limit its legs ever
            // met (item 2) — the same reading the corner's price was charged on.
            val referenceLimit = graph.limitAt(vertex)
            val turn = turnAt(previous, vertex, next, referenceLimit)
            when (turn.kind) {
                TautEasing.Kind.SPIRAL -> spirals++
                TautEasing.Kind.ARC -> arcs++
                TautEasing.Kind.SHARP -> sharp++
            }
            when (turn.sharpCause) {
                TautEasing.SharpCause.GUARD -> byGuard++
                TautEasing.SharpCause.BUILD -> byBuild++
                TautEasing.SharpCause.CUTBACK -> byCutback++
                TautEasing.SharpCause.WATER -> byWater++
                TautEasing.SharpCause.RULE -> byRule++
                null -> Unit
            }
            if (turn.radiusM > largestRadiusM) largestRadiusM = turn.radiusM
            if (turn.maxSagittaM > chordDeviationM) chordDeviationM = turn.maxSagittaM
            if (turn.speedMps > 0.0 && turn.arcLengthM > turn.lengthM) {
                // The chords stand for the curve, and the difference is what the emission costs.
                samplingSec += (turn.arcLengthM - turn.lengthM) / turn.speedMps
            }
            turn.emitted.forEachIndexed { index, point ->
                // The first emitted point ends the leg arriving at the corner, so that stretch keeps the
                // world's own limits; every later one ends a chord, charged the corner's own speed.
                rawLimits.add(if (index == 0) Double.MAX_VALUE else turn.limitKn)
                rawOwners.add(k)
                rawPoints.add(point)
            }
            // §12.3: the corner's own slowdown, charged on the first drawn leg the corner owns — the same
            // term and the same home the corner's price reads, so the accumulation and the drawn clock
            // carry it once each and they agree. A sharp corner pays it too: its speed is the ladder's own
            // floor rather than zero (§17 item 2).
            val referenceSpeedMps = min(cruiseSpeedKn, referenceLimit) * Units.MPS_PER_KNOT
            val extra = RoutePlanTiming.longitudinalSec(
                inSpeedMps = referenceSpeedMps,
                cornerSpeedMps = turn.speedMps,
                outSpeedMps = referenceSpeedMps,
                longitudinalAccelMps2 = longitudinalAccelMps2
            )
            if (extra > 0.0) {
                extraByCorner[k] = extra
                // The sharp corners' own share of it, told apart from the eased ones': this is the charge
                // item 2 exists to make, and its size is what the re-read of item 4 is judged against.
                if (turn.kind == TautEasing.Kind.SHARP) sharpChargeSec += extra
            }
        }
        if (chain.size >= 2) {
            rawLimits.add(Double.MAX_VALUE)
            rawOwners.add(-1)
            rawPoints.add(graph.vertices[chain.last()])
        }

        // The splitter: every leg — straight leg and emitted chord alike — cut at each boundary the one
        // predicate reports, so a boundary falling inside an arc is re-priced rather than left on its
        // corner's single value (§17 item 1, second half). The splitter itself lives on the graph.
        val points = ArrayList<RoutePoint>(rawPoints.size + 8)
        val limits = ArrayList<Double>(rawPoints.size + 8)
        val owners = ArrayList<Int>(rawPoints.size + 8)
        points.add(rawPoints.first())
        for (i in rawLimits.indices) {
            for (piece in graph.drawnPieces(rawPoints[i], rawPoints[i + 1], rawLimits[i])) {
                points.add(piece.to)
                limits.add(piece.limitKn)
                owners.add(rawOwners[i])
            }
        }

        val legTimes = RoutePlanTiming
            .drawnLegSeconds(points, limits, cruiseSpeedKn, lateralAccelMps2)
            .toMutableList()
        var longitudinalSec = 0.0
        val placed = HashSet<Int>()
        for ((index, corner) in owners.withIndex()) {
            val extra = extraByCorner[corner] ?: continue
            if (!placed.add(corner)) continue
            if (index in legTimes.indices) legTimes[index] = legTimes[index] + extra
            longitudinalSec += extra
        }

        var distance = 0.0
        var inBand = false
        for (i in 0 until points.size - 1) {
            distance += SpatialOperations.haversine(
                LatLng(points[i].latitude, points[i].longitude),
                LatLng(points[i + 1].latitude, points[i + 1].longitude)
            )
            if (!inBand && inCoastalBand(world, points[i], points[i + 1])) inBand = true
        }

        // **The margin's own reading runs over the price's own legs** (item 4): the chain's edges, each
        // `length × berthFraction` — the very fraction `berthPriceSec` multiplies its seconds by. The
        // drawn pieces are a different, shorter set the price was never paid on, which is the second
        // quantity the review found standing beside the first.
        val marginLegs = ArrayList<Pair<RoutePoint, RoutePoint>>(max(chain.size - 1, 0))
        for (i in 0 until chain.size - 1) {
            marginLegs.add(graph.vertices[chain[i]] to graph.vertices[chain[i + 1]])
        }
        val inMarginM = graph.zones.inMarginM(marginLegs, obstacles.berthM)

        val crossings = crossZoneNames(points)
        return Outcome(
            points = points,
            legTimesSec = legTimes,
            legLimitKn = limits,
            distanceM = distance,
            durationSec = legTimes.sum(),
            inBand = inBand,
            forcedCrossingZoneNames = if (zonePricedRun) crossings.first else emptyList(),
            nodesExpanded = nodesExpanded,
            pricedSec = best[goalState] ?: 0.0,
            berthPriceSec = berthTotals[goalState] ?: 0.0,
            longitudinalSec = longitudinalSec,
            crossings = crossings.second,
            inMarginM = inMarginM,
            spiralCorners = spirals,
            arcCorners = arcs,
            sharpCorners = sharp,
            sharpByGuard = byGuard,
            sharpByBuild = byBuild,
            sharpByCutback = byCutback,
            sharpByWater = byWater,
            sharpByRule = byRule,
            sharpChargeSec = sharpChargeSec,
            largestRadiusM = largestRadiusM,
            chordDeviationM = chordDeviationM,
            samplingSec = samplingSec,
            fitsBuilt = fitsBuilt,
            fitMillis = fitNanos / 1_000_000,
            clockCalls = clockCalls,
            clockMillis = clockNanos / 1_000_000,
            candidatesBuilt = candidatesBuilt,
            candidateBuildMillis = candidateBuildNanos / 1_000_000,
            candidateWaterMillis = candidateWaterNanos / 1_000_000,
            zonePricedRun = zonePricedRun
        )
    }

    /**
     * The priced zones the drawn line enters, by name, and how many drawn legs did it.
     *
     * It is read through the zone's own predicate — the interval a leg shares with the interior, ring
     * minus holes — rather than by sampling the leg at intervals: a clip shorter than the sampling step
     * used to be able to go unreported, and a crossing presented as an ordinary route is exactly what the
     * report exists to prevent. A leg clipping a zone's **corner** is now one of the legs it reports,
     * where the midpoint-and-parity reading counted it zero.
     */
    private fun crossZoneNames(points: List<RoutePoint>): Pair<List<String>, Int> {
        val names = ArrayList<String>()
        var count = 0
        for (i in 0 until points.size - 1) {
            val zone = graph.zones.enteredZone(points[i], points[i + 1])
            if (zone < 0) continue
            count++
            val name = graph.zones.zone(zone).shape.name
            if (!names.contains(name)) names.add(name)
        }
        return names to count
    }

    private fun stateKey(vertex: Int, previous: Int): Long =
        (vertex.toLong() shl 32) or (previous.toLong() and 0xFFFFFFFFL)

    private fun vertexOf(state: Long): Int = (state shr 32).toInt()

    private fun previousOf(state: Long): Int = state.toInt()

    private fun turnKey(previous: Int, vertex: Int, next: Int): Long {
        val count = max(graph.vertices.size, 1).toLong()
        return (previous.toLong() * count + vertex) * count + next
    }

    private companion object {

        /** Seconds within which two accumulations count as equal — the tie-break's own tolerance. */
        const val TIE_EPS_SEC = 1e-9
    }
}

/**
 * **The drawn clock as the search sees it: the graph's splitter first, then the app's own clock.**
 *
 * It is the same reading the assembly takes of the finished line — the same splitter, the same arithmetic
 * — so a corner's price and the corner's drawn seconds are one quantity with two readers rather than two
 * models that must agree. It is a **top-level** function rather than a private method so the readings can
 * be handed *this* clock: the Dijkstra reference that checks the A\*'s arithmetic used to price its
 * corners through `Turn.priceSec`'s default clock, a different quantity from the one the search
 * accumulates, and the two could agree only on a world whose legs carry no limits (item 5).
 */
internal fun drawnClockOf(
    graph: TautGraph,
    points: List<RoutePoint>,
    limits: List<Double>,
    cruiseSpeedKn: Double,
    lateralAccelMps2: Double
): Double {
    val drawn = ArrayList<RoutePoint>(points.size + 4)
    val drawnLimits = ArrayList<Double>(limits.size + 4)
    drawn.add(points.first())
    for (i in 0 until points.size - 1) {
        val nominal = limits.getOrElse(i) { Double.MAX_VALUE }
        for (piece in graph.drawnPieces(points[i], points[i + 1], nominal)) {
            drawn.add(piece.to)
            drawnLimits.add(piece.limitKn)
        }
    }
    return RoutePlanTiming.drawnLegSeconds(drawn, drawnLimits, cruiseSpeedKn, lateralAccelMps2).sum()
}

/**
 * **Whether the water lets a corner be rounded — and, when it does not, whose answer the refusal is.**
 *
 * Two different things refuse a curve here: the water, and the run's own rule when a zone's interior is
 * forbidden. The curve **is** the line the boat will sail, so it is held to the run's own rule and not
 * only to the water — a chord that rounds the corner *into* the zone breaches the promise the search has
 * just made, its legs never entering the interior and the rounding of their corner not either. The corner
 * then stays sharp, which is the design's own answer wherever the water does not allow the curve (§11.5).
 *
 * It returns the **cause** rather than a boolean, so a rule refusal is counted apart from the water's and
 * charged at the water's own reference speed instead of at a floor read off a radius the water never
 * refused (item 2 of the level above). And it is **one function**, read by the search and by the readings
 * alike, so a reference can no longer re-implement the rule this pass unified (item 5).
 */
internal fun fitsCurve(
    obstacles: TautObstacles,
    zones: TautZoneSet,
    forbidZoneInteriors: Boolean,
    previous: RoutePoint,
    emitted: List<RoutePoint>,
    next: RoutePoint
): TautEasing.SharpCause? {
    if (emitted.isEmpty()) return null
    val drawn = listOf(previous) + emitted + listOf(next)
    if (!obstacles.curveOnWater(drawn)) return TautEasing.SharpCause.WATER
    if (forbidZoneInteriors) {
        for (i in 0 until drawn.size - 1) {
            if (zones.enteredZone(drawn[i], drawn[i + 1]) >= 0) return TautEasing.SharpCause.RULE
        }
    }
    return null
}
