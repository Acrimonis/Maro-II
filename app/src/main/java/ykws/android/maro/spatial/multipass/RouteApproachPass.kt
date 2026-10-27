package ykws.android.maro.spatial.multipass

import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.spatial.SpatialOperations
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Relaxes a slow-water approach together with its adjoining legs. A deletion-only pull can leave an
 * open-water bend pinned by an old crossing point: its skipping chord spends too long in slow water.
 * Here a bounded window may become two legs through a NEW point, moving that crossing at the same time.
 *
 * Windows keep their ends and use the existing field, berth, pace and clearance proofs. Only a strict
 * total-cost improvement is spliced. The search may explore a replacement costlier than the original
 * while moving its pivot; that provisional geometry is never emitted. This is a bounded local search,
 * not a guarantee of the globally cheapest approach. It runs before straightening and corner rounding.
 *
 * **A candidate is priced before it is proved, and only as far as it can still win** ([candidateSec]). Its
 * cost is only ever compared against the window's best so far, so a candidate that cannot beat that best
 * is rejected whatever its clearance — exactly as an unclear one is — and the proof is paid only by the few
 * that would win. The price walk stops as soon as a lower bound reaches the best: the two legs' travel at the
 * pace, then the first leg's full cost plus the second's travel, since no price is ever negative — the field's
 * price sums only terms above zero (`RouteCostField.priceAt` adds a source's cost only where it is positive),
 * and the berth's rate is non-negative by its config clamps (`route.evolutive.berth.secPerM` at zero or above,
 * the inner rate at least that, and a λ above zero to price it at all). Each decision is the one the
 * proof-first order took; only the reads that cannot change it are skipped (the 2026-10-27 device split:
 * clearance and price were even, and most candidates lose).
 *
 * **A window the first pass searched without a move is not searched again.** Its outcome is a function of its
 * own points, the context and the bounds alone, so the second pass — which runs only after a move — searches
 * only the windows a move gave a new point.
 *
 * **A merge never deletes a land turn.** A window of three spans or more replaces its inner points with one; where
 * one of them is a turn the land forces — the chord skipping it refused for land — that one point would stand for
 * two turns, where their outer legs cross, off both (the 2026-10-27 Lérins → La Salis "Closest" line, a turn at
 * sea in place of Sainte-Marguerite's tip and the 10 kn zone's corner). Such a window is not searched. A turn kept
 * for price — a bend pinned by an old crossing, the reason this pass exists — may still be merged away, and a
 * two-span window's slide only moves its one point.
 *
 * **A turn standing for two is split in two.** The pull places a turn on the grid path's own point, so where that
 * path bent once between two features the line keeps one point where their tangent legs cross, at sea and off
 * both (the 2026-10-27 "Balanced" line, east of Sainte-Marguerite) — and no slide or merge adds a point. A two-span
 * window therefore also tries two points in place of the point its slide found, each on one of that point's legs
 * ([searchSplit]); the window takes the cheaper of its slide and its split. A split the corner pass would draw
 * back as the one corner it splits is refused, and a point a split made is not split again in the same pass.
 */
internal object RouteApproachPass {
    private const val MAX_SPAN = 4
    private const val MAX_PASSES = 2
    private const val MAX_SCALES = 6
    private const val DIRECTIONS = 8

    /** A slide's legs are proved in their own order, the one the replacement's two proofs have always had. */
    private val SLIDE_PROOFS = listOf(0, 1)

    /** A split's new chord is proved first — the leg a split is refused on; its two others lie on the line's legs. */
    private val SPLIT_PROOFS = listOf(1, 0, 2)
    private val metresPerDegree = SpatialOperations.EARTH_RADIUS_M * PI / 180.0

    fun refine(
        setup: PullSetup,
        line: List<LatLng>,
        bounds: BBox,
        /**
         * **This pass's own step (m)** — the smallest radius a window is tried at, the floor of the scale
         * loop and the lattice its slow-water read samples a leg on: a *geometry* step, the fine cell the
         * line was resolved at. It is deliberately apart from [PullSetup.coarseStepM], the clearance walk's
         * *proof* step, which may move without this pass's windows moving with it.
         */
        stepM: Double,
        /** The pass's own memo, so a chord the pull already proved is answered rather than re-read. */
        memo: MarkMemo? = null,
        checkCancelled: () -> Unit = {},
        trace: (() -> String) -> Unit = {},
        /**
         * **Whether a candidate is priced first and proved only where it can win** — `true` on every shipped
         * path. A fixture passes `false` to run the proof-first order this replaced, which is how the two are
         * pinned to answer the same line: the reordering skips reads, never a decision.
         */
        incumbentFirst: Boolean = true,
        /**
         * **Whether the second pass skips a window the first searched without a move** — `true` on every shipped
         * path. A fixture passes `false` to search it again, which is how the two are pinned to draw one line.
         */
        skipRepeats: Boolean = true,
        /**
         * **Whether a merge window holding a land turn is skipped** — `true` on every shipped path. A fixture
         * passes `false` to show the merge the rule refuses.
         */
        keepLandTurns: Boolean = true
    ): List<LatLng> {
        if (line.size < 3 || !setup.field.hasSoft || setup.paceMps <= 0.0) {
            trace { "APPROACH skipped=no-candidates" }
            return line
        }
        // Use the same orientation even when this pass is called directly rather than by finePass.
        val reversed = !sortsFirst(line.first(), line.last())
        val work = (if (reversed) line.asReversed() else line).toMutableList()
        // **The pass's own instrument** — the pull's read tally on the pass's context and the clocks around the
        // calls the pass already makes ([ApproachTally]). It reads and never steers: the tally only counts, so
        // the context answers every proof and price exactly as it would without it.
        val startNs = System.nanoTime()
        val timing = PullTiming()
        val tally = ApproachTally()
        val priceHitsBefore = memo?.priceHits ?: 0L
        val hardHitsBefore = memo?.hardHits ?: 0L
        val edgeHitsBefore = memo?.edgeHits ?: 0L
        val edgeMissesBefore = memo?.edgeMisses ?: 0L
        val farEdgeMissesBefore = memo?.farEdgeMisses ?: 0L
        val ctx = (if (reversed) setup.reversed() else setup).context(timing = timing, memo = memo)
        val slowLegs = mutableMapOf<Pair<LatLng, LatLng>, Boolean>()
        fun slow(a: LatLng, b: LatLng): Boolean = slowLegs.getOrPut(a to b) {
            val slowStartNs = System.nanoTime()
            hasSlowWater(a, b, ctx, stepM, checkCancelled).also {
                tally.slowNs += System.nanoTime() - slowStartNs
                tally.slowLegs++
            }
        }
        // **A land turn** — a point whose skipping chord, its two neighbours joined, the pull's clearance proof refuses
        // for land: the coast, the rastered walls and the measured shallow-water wall alike. Keyed on the chord, like
        // `slow()`; the proof is a clearance proof, so its time is the clearance clock's.
        val landChords = mutableMapOf<Pair<LatLng, LatLng>, Boolean>()
        fun landTurn(u: LatLng, w: LatLng): Boolean = landChords.getOrPut(u to w) {
            val landStartNs = System.nanoTime()
            (MultipassPull.legClearCause(u, w, ctx) == ChordRefusal.LAND).also {
                tally.clearNs += System.nanoTime() - landStartNs
                tally.landProofs++
            }
        }
        // The move's own line reads its causes on a context with no memo and no tally: where the trace is live it
        // pays a clearance proof and up to three prices per replaced point, inside `ms` and `passMs` but never in
        // the read clocks, the read counts or the memo's.
        val untallied = (if (reversed) setup.reversed() else setup).context()
        // The split's floor reads the corner pass's own pace-speed radius, so a split it accepts is one that pass
        // draws as two bends.
        val paceRadiusM = ctx.paceMps * ctx.paceMps / AppConfig.routeTurnLateralAccelMps2
        var moved = 0
        var savedSec = 0.0
        // The windows searched without a move, by a copy of their points — `subList` is a view the next splice
        // would change inside the set.
        val unmoved = HashSet<List<LatLng>>()
        val passMs = mutableListOf<Double>()
        val passEvals = mutableListOf<Int>()
        for (pass in 0 until MAX_PASSES) {
            val passStartNs = System.nanoTime()
            val evalsBefore = tally.evals
            var changed = false
            // The points this pass's splits made that a later window could seed — never split again before the next
            // pass, so a pass ends.
            val splitThisPass = HashSet<LatLng>()
            var from = 0
            while (from < work.size - 2) {
                checkCancelled()
                val a = work[from]
                val firstSlow = slow(a, work[from + 1])
                for (span in minOf(MAX_SPAN, work.lastIndex - from) downTo 2) {
                    val to = from + span
                    val e = work[to]
                    val lastSlow = slow(work[to - 1], e)
                    // A crossing and adjoining open water, not an unrelated all-slow or all-free run.
                    if (firstSlow == lastSlow) continue
                    val seedIndex = if (firstSlow) from + 1 else to - 1
                    val seed = work[seedIndex]
                    val near = if (firstSlow) a else e
                    val radiusM = 2.0 * SpatialOperations.haversine(near, seed)
                    if (radiusM < stepM) continue
                    if (keepLandTurns && span >= 3 && (from + 1 until to).any { landTurn(work[it - 1], work[it + 1]) }) {
                        tally.landTurnWindows++
                        continue
                    }
                    val window = work.subList(from, to + 1).toList()
                    if (skipRepeats && window in unmoved) {
                        tally.repeats++
                        continue
                    }
                    tally.windows++
                    val oldStartNs = System.nanoTime()
                    val oldSec = (from until to).sumOf {
                        MultipassPull.totalSec(work[it], work[it + 1], ctx)
                    }
                    tally.priceNs += System.nanoTime() - oldStartNs
                    var pivot = seed
                    var bestSec = replacementSec(listOf(a, pivot, e), ctx, bounds, tally, SLIDE_PROOFS, seed = true)
                    var scaleM = radiusM / 2.0
                    for (scale in 0 until MAX_SCALES) {
                        if (scaleM < stepM) break
                        val centre = pivot
                        for (direction in 0 until DIRECTIONS) {
                            checkCancelled()
                            val angle = direction * 2.0 * PI / DIRECTIONS
                            val candidate = offset(centre, scaleM * cos(angle), scaleM * sin(angle))
                            if (SpatialOperations.haversine(seed, candidate) > radiusM) continue
                            tally.candidates++
                            val route = listOf(a, candidate, e)
                            val sec = if (incumbentFirst) {
                                candidateSec(route, ctx, bounds, bestSec, tally, SLIDE_PROOFS)
                            } else {
                                replacementSec(route, ctx, bounds, tally, SLIDE_PROOFS)
                            }
                            if (sec < bestSec) {
                                bestSec = sec
                                pivot = candidate
                            }
                        }
                        scaleM /= 2.0
                    }
                    // A slide's window also tries a split — of the slide's own answer, on the legs it found, against
                    // that answer where it beats the window. A split of the seed keeps part of legs the slide moved off
                    // for their price, and the slid window may no longer touch slow water, so the next pass never
                    // offers it (the 2026-10-27 "Balanced" run). Where the slide found nothing the pivot is the seed.
                    // One whose seed a split just made searched its slide alone, so it is never recorded as searched.
                    val splitHeld = span == 2 && seed in splitThisPass
                    val split = if (span == 2 && !splitHeld) {
                        tally.splitWindows++
                        searchSplit(
                            a, pivot, e, ctx, bounds, stepM, minOf(oldSec, bestSec), paceRadiusM, tally,
                            incumbentFirst, checkCancelled
                        )
                    } else {
                        null
                    }
                    val newSec = split?.sec ?: bestSec
                    if (newSec >= oldSec || !newSec.isFinite()) {
                        if (!splitHeld) unmoved += window
                        continue
                    }
                    savedSec += oldSec - newSec
                    // **The move's own line**, each replaced point named with the reason it stood — the window's own
                    // points, captured before the splice, so the line says what the move replaced whenever it is
                    // written. A slide's one point is replaced, not deleted, so `LAND` there is no breach of the rule;
                    // a split's `at=` names its two points in the line's order.
                    val windowFrom = from
                    val windowPivot = pivot
                    val windowSplit = split
                    val windowSavedSec = oldSec - newSec
                    trace {
                        val kind = if (windowSplit != null) "split" else if (span == 2) "slide" else "merge"
                        val at = windowSplit?.let { "[${MultipassPull.atText(it.p)} ${MultipassPull.atText(it.q)}]" }
                            ?: MultipassPull.atText(windowPivot)
                        "APPROACH move pass=${pass + 1} from=$windowFrom span=$span kind=$kind replaced=[" +
                            window.windowed(3).joinToString(" ") { (u, v, w) ->
                                "${MultipassPull.atText(v)}:${keepCause(u, v, w, untallied)}"
                            } +
                            "] at=$at savedSec=${fmt(windowSavedSec, 3)}"
                    }
                    work.subList(from + 1, to).clear()
                    if (split != null) {
                        work.addAll(from + 1, listOf(split.p, split.q))
                        // Only `q` is ever a later window's seed in this pass: `p` stands at an index it has passed.
                        splitThisPass += split.q
                        tally.splits++
                    } else {
                        work.add(from + 1, pivot)
                    }
                    moved++
                    changed = true
                    break
                }
                from++
            }
            passMs += msSince(passStartNs)
            passEvals += tally.evals - evalsBefore
            if (!changed) break
        }
        // One line: what the pass found, then where its time and its reads went — the clearance proofs (the
        // seeds' included in `clearMs`) and the priced chords apart, the slow-water test of the line's legs
        // beside them, and the pull's own read counts on the pass's context. `refusedClear` counts candidates
        // and `seedsRefused` the windows' seeds; `edgeReads` and `farEdgeReads` are the near and the far edge
        // reads actually paid, and like the memo's hits they read zero where the pass holds no memo. `repeats`
        // are the windows the second pass skipped, `landTurnWindows` the merges never searched for holding a land
        // turn (its `landProofs` inside `clearMs`), and `passMs`/`passEvals` each pass's own share. `splitWindows`
        // are the slide windows a split was searched in, `splitEvals` its candidates (inside `candidates` and
        // `evals`), `splits` the splits spliced and `splitFloor` the cuts refused under the corner pass's span. The read
        // clocks — `nearEdgeMs`, `farEdgeMs`, `fieldPriceMs`, `fieldClearMs` — are each the paid side of one
        // read, inside `priceMs` or `clearMs`; `berthEdgeNone`/`wallEdgeNone` the near reads that found no edge.
        // Each accepted move has its own `APPROACH move` line before this one.
        trace {
            "APPROACH moved=$moved candidates=${tally.candidates} savedSec=${fmt(savedSec, 3)} " +
                "ms=${fmt(msSince(startNs))} windows=${tally.windows} repeats=${tally.repeats} " +
                "landTurnWindows=${tally.landTurnWindows} landProofs=${tally.landProofs} " +
                "splitWindows=${tally.splitWindows} splitEvals=${tally.splitEvals} splits=${tally.splits} " +
                "splitFloor=${tally.splitFloor} " +
                "passMs=${passMs.joinToString("/") { fmt(it) }} passEvals=${passEvals.joinToString("/")} " +
                "evals=${tally.evals} " +
                "outside=${tally.outside} cutTravel=${tally.cutTravel} cutLeg=${tally.cutLeg} " +
                "lost=${tally.lost} proofs=${tally.proofs} refusedClear=${tally.refusedClear} " +
                "seedsRefused=${tally.seedsRefused} " +
                "clearMs=${fmt(tally.clearNs / NANOS_PER_MS)} priceMs=${fmt(tally.priceNs / NANOS_PER_MS)} " +
                "slowMs=${fmt(tally.slowNs / NANOS_PER_MS)} slowLegs=${tally.slowLegs} " +
                "nearEdgeMs=${fmt(timing.nearEdgeNanos / NANOS_PER_MS)} " +
                "farEdgeMs=${fmt(timing.farEdgeNanos / NANOS_PER_MS)} " +
                "fieldPriceMs=${fmt(timing.fieldPriceNanos / NANOS_PER_MS)} " +
                "fieldClearMs=${fmt(timing.fieldClearNanos / NANOS_PER_MS)} fieldClears=${timing.fieldClearReads} " +
                "berthEdgeNone=${timing.berthEdgeNone} wallEdgeNone=${timing.wallEdgeNone} " +
                "clearMarks=${timing.clearanceMarks} hardReads=${timing.hardReads} " +
                "priceMarks=${timing.marks} priceReads=${timing.priceReads} " +
                "edgeReads=${(memo?.edgeMisses ?: 0L) - edgeMissesBefore} " +
                "farEdgeReads=${(memo?.farEdgeMisses ?: 0L) - farEdgeMissesBefore} " +
                "memoPriceHits=${(memo?.priceHits ?: 0L) - priceHitsBefore} " +
                "memoHardHits=${(memo?.hardHits ?: 0L) - hardHitsBefore} " +
                "memoEdgeHits=${(memo?.edgeHits ?: 0L) - edgeHitsBefore}"
        }
        return if (reversed) work.asReversed().toList() else work.toList()
    }

    /** Source-owned price arms identify slow water; marker/depth prices alone do not trigger a search. */
    private fun hasSlowWater(
        a: LatLng,
        b: LatLng,
        ctx: PullContext,
        stepM: Double,
        checkCancelled: () -> Unit
    ): Boolean {
        fun slowAt(p: LatLng) = ctx.field.armPricesAt(p).any {
            (it.first == PriceArm.BAND || it.first == PriceArm.ZONE) && it.second > 0.0
        }
        if (slowAt(a) || slowAt(b)) return true
        val marks = MarkLattice(SpatialOperations.haversine(a, b), stepM)
        for (i in 0 until marks.count) {
            if (i % 64 == 0) checkCancelled()
            if (slowAt(along(a, b, marks.markT(i)))) return true
        }
        return false
    }

    /**
     * **A replacement proved first, then priced** — [route] its window's two ends with the new point or points
     * between, its legs proved in [proofOrder] and each later one only where the earlier cleared. The proof-first
     * order every window's seed takes, and every candidate's where a fixture asks for it.
     */
    private fun replacementSec(
        route: List<LatLng>,
        ctx: PullContext,
        bounds: BBox,
        tally: ApproachTally,
        proofOrder: List<Int>,
        /** Whether this is a window's seed — the line's own point — rather than a candidate around it. */
        seed: Boolean = false
    ): Double {
        tally.evals++
        if (anyOutside(bounds, route, tally)) return Double.POSITIVE_INFINITY
        // Only the real route ends retain waivers: a window end or a new pivot earns no exemption.
        if (!proveClear(route, proofOrder, ctx, tally)) {
            if (seed) tally.seedsRefused++ else tally.refusedClear++
            return Double.POSITIVE_INFINITY
        }
        val priceStartNs = System.nanoTime()
        var sec = 0.0
        for (leg in 0 until route.lastIndex) sec += MultipassPull.totalSec(route[leg], route[leg + 1], ctx)
        tally.priceNs += System.nanoTime() - priceStartNs
        return sec
    }

    /**
     * **Why the line held [v]**, for the move's line — `LAND` where the chord skipping it is refused for land,
     * `PRICE` where that chord clears but costs more than the two legs through [v], `NONE` otherwise. The price
     * read is the plain comparison, not the sweep's allowance-weighted one, so it names the reason rather than
     * replaying the sweep's verdict.
     */
    private fun keepCause(u: LatLng, v: LatLng, w: LatLng, ctx: PullContext): String = when {
        MultipassPull.legClearCause(u, w, ctx) == ChordRefusal.LAND -> "LAND"
        MultipassPull.totalSec(u, w, ctx) > MultipassPull.totalSec(u, v, ctx) + MultipassPull.totalSec(v, w, ctx) -> "PRICE"
        else -> "NONE"
    }

    /**
     * Whether a new point of [route] — every point but its two ends — stands outside the corridor: no read is paid
     * for it, and the tally counts the replacement once.
     */
    private fun anyOutside(bounds: BBox, route: List<LatLng>, tally: ApproachTally): Boolean {
        if ((1 until route.lastIndex).all { inBox(bounds, route[it]) }) return false
        tally.outside++
        return true
    }

    /**
     * **Whether every leg of [route] is clear** — proved in [proofOrder], each only where the ones before it
     * cleared, with the proofs' time in the tally.
     */
    private fun proveClear(route: List<LatLng>, proofOrder: List<Int>, ctx: PullContext, tally: ApproachTally): Boolean {
        val clearStartNs = System.nanoTime()
        val clear = proofOrder.all { MultipassPull.legClear(route[it], route[it + 1], ctx) }
        tally.clearNs += System.nanoTime() - clearStartNs
        return clear
    }

    /**
     * **A candidate's cost where it can beat [incumbent], and `+∞` wherever it cannot** — the window's own
     * evaluation of every candidate after its seed, a slide's two legs or a split's three. The caller keeps a
     * candidate only where its cost is below the best so far and the replacement is clear, so the order this takes
     * leaves each decision as the proof-first [replacementSec] made it and only skips reads:
     *
     * 1. **Outside the corridor** — `+∞` before any read, as before.
     * 2. **The travel bound** — every leg's travel at the pace (the very `haversine / pace` term
     *    [MultipassPull.totalSec] adds its price to) already at or above the incumbent: no price, no proof.
     * 3. **The legs' bounds** — the legs priced so far plus the rest's travel at or above it, read after each leg
     *    but the last: the rest are never priced.
     * 4. **The full cost** at or above it: no proof is run.
     * 5. **The proof** — only a candidate that would win is proved clear, and a refusal is `+∞` as before.
     *
     * Each bound is sound under rounding: a leg's cost is its travel plus a price that is never negative, and
     * rounding to the nearest is monotone, so a bound at or above the incumbent leaves the full sum there too.
     * The cost returned is the same left-to-right sum the proof-first order makes. The window's seed is still
     * evaluated by [replacementSec], so its own cost — the first incumbent — is the value it always was.
     */
    private fun candidateSec(
        route: List<LatLng>,
        ctx: PullContext,
        bounds: BBox,
        incumbent: Double,
        tally: ApproachTally,
        proofOrder: List<Int>
    ): Double {
        tally.evals++
        if (anyOutside(bounds, route, tally)) return Double.POSITIVE_INFINITY
        val priceStartNs = System.nanoTime()
        val legs = route.lastIndex
        val travel = DoubleArray(legs) { SpatialOperations.haversine(route[it], route[it + 1]) / ctx.paceMps }
        var sec = 0.0
        for (priced in 0 until legs) {
            var bound = sec
            for (leg in priced until legs) bound += travel[leg]
            if (bound >= incumbent) {
                tally.priceNs += System.nanoTime() - priceStartNs
                if (priced == 0) tally.cutTravel++ else tally.cutLeg++
                return Double.POSITIVE_INFINITY
            }
            sec += MultipassPull.totalSec(route[priced], route[priced + 1], ctx)
        }
        tally.priceNs += System.nanoTime() - priceStartNs
        if (!(sec < incumbent)) {
            tally.lost++
            return Double.POSITIVE_INFINITY
        }
        tally.proofs++
        if (!proveClear(route, proofOrder, ctx, tally)) {
            tally.refusedClear++
            return Double.POSITIVE_INFINITY
        }
        return sec
    }

    /** A split's two points, in the line's order, and the window's cost through them. */
    private class Split(val p: LatLng, val q: LatLng, val sec: Double)

    /**
     * **The best split of [v]'s turn that beats [incumbent], or `null`** — two points in place of [v], `p` on the leg
     * from [a] and `q` on the leg to [b], searched by the slide's own loop in the plane of their distances from [v]:
     * seeded at the half cut, eight directions, each axis's step a quarter of its own leg and halving, the centre
     * moving to each winner. [v] is the window's slide answer — the line's own point where the slide found nothing —
     * so `a→p` and `q→b` lie on legs already priced, and `p→q` is shorter than `p→v→q`: a split loses only where its
     * new chord costs or is refused — yet every leg is proved on its own lattice.
     *
     * A cut off either leg is refused before any read and counted nowhere, and so is one whose points stand closer
     * than the corner pass's span for their summed turn ([RouteCornerPass.pairIsOneTurn], counted in `splitFloor`):
     * that pass would draw such a pair back over the crossing of their outer legs, which is [v] itself. The floor
     * reads the span test alone — not that pass's flat-kink and crossing-within-the-legs checks — so it also refuses
     * some pairs that pass would keep as two: it errs toward the one point, never the other way. Every other cut is
     * one candidate, evaluated in the window's own order — [candidateSec] pricing first, [replacementSec] proving
     * first, the new chord proved before the two legs it joins — so both orders decide alike.
     */
    private fun searchSplit(
        a: LatLng,
        v: LatLng,
        b: LatLng,
        ctx: PullContext,
        bounds: BBox,
        stepM: Double,
        incumbent: Double,
        paceRadiusM: Double,
        tally: ApproachTally,
        incumbentFirst: Boolean,
        checkCancelled: () -> Unit
    ): Split? {
        val inM = SpatialOperations.haversine(a, v)
        val outM = SpatialOperations.haversine(v, b)
        var best: Split? = null
        var bestSec = incumbent
        fun wins(cutInM: Double, cutOutM: Double): Boolean {
            if (cutInM <= 0.0 || cutInM >= inM || cutOutM <= 0.0 || cutOutM >= outM) return false
            val p = along(v, a, cutInM / inM)
            val q = along(v, b, cutOutM / outM)
            val summedDeg = abs(RouteCornerPass.turnDegreesAt(a, p, q)) + abs(RouteCornerPass.turnDegreesAt(p, q, b))
            if (RouteCornerPass.pairIsOneTurn(SpatialOperations.haversine(p, q), summedDeg, paceRadiusM)) {
                tally.splitFloor++
                return false
            }
            tally.candidates++
            tally.splitEvals++
            val route = listOf(a, p, q, b)
            val sec = if (incumbentFirst) {
                candidateSec(route, ctx, bounds, bestSec, tally, SPLIT_PROOFS)
            } else {
                replacementSec(route, ctx, bounds, tally, SPLIT_PROOFS)
            }
            if (!(sec < bestSec)) return false
            bestSec = sec
            best = Split(p, q, sec)
            return true
        }
        var cutIn = inM / 2.0
        var cutOut = outM / 2.0
        // The seed is a candidate like any other: the search centres on it whether or not it wins.
        wins(cutIn, cutOut)
        var scaleIn = inM / 4.0
        var scaleOut = outM / 4.0
        for (scale in 0 until MAX_SCALES) {
            if (scaleIn < stepM && scaleOut < stepM) break
            val centreIn = cutIn
            val centreOut = cutOut
            for (direction in 0 until DIRECTIONS) {
                checkCancelled()
                val angle = direction * 2.0 * PI / DIRECTIONS
                val candidateIn = centreIn + scaleIn * cos(angle)
                val candidateOut = centreOut + scaleOut * sin(angle)
                if (wins(candidateIn, candidateOut)) {
                    cutIn = candidateIn
                    cutOut = candidateOut
                }
            }
            scaleIn /= 2.0
            scaleOut /= 2.0
        }
        return best
    }

    /** The point a share [t] of the way from [from] to [to]. */
    private fun along(from: LatLng, to: LatLng, t: Double) = LatLng(
        from.latitude + (to.latitude - from.latitude) * t,
        from.longitude + (to.longitude - from.longitude) * t
    )

    /**
     * **What one refinement spent, and where** — the clocks and counts its `APPROACH` line carries. It reads
     * and never steers: the clocks wrap calls the pass makes anyway, so the line and the answer are unmoved
     * by carrying it.
     */
    private class ApproachTally {
        /**
         * Nanoseconds in every clearance proof — each evaluation's ([MultipassPull.legClear], up to two for a slide and
         * three for a split), the seeds' included, and the land-turn proofs that decide a merge window's eligibility.
         */
        var clearNs = 0L

        /**
         * Nanoseconds pricing chords — each window's own legs, each cleared seed's two, and as much of each
         * candidate's legs as its bounds let it reach (the travel bound included).
         */
        var priceNs = 0L

        /** Nanoseconds in the slow-water test of the line's legs ([hasSlowWater]), each leg read once. */
        var slowNs = 0L

        /** The legs the slow-water test read — its cache's misses. */
        var slowLegs = 0

        /** The windows searched: a crossing beside open water, wide enough for the pass's step. */
        var windows = 0

        /** The windows the second pass skipped — searched by the first without a move, their points unchanged. */
        var repeats = 0

        /** The merge windows skipped because an inner point is a land turn — never searched. */
        var landTurnWindows = 0

        /** The land-turn proofs paid — one per chord the eligibility test had not read yet. */
        var landProofs = 0

        /** The slide windows a split was searched in. */
        var splitWindows = 0

        /** The split candidates evaluated — inside [candidates] and [evals]. */
        var splitEvals = 0

        /** The splits spliced. */
        var splits = 0

        /** The split cuts refused before any read, their points closer than the corner pass's span. */
        var splitFloor = 0

        /** The candidates evaluated: the slides' and merges' around their seed, and the splits' on their legs. */
        var candidates = 0

        /** The replacements evaluated: each window's seed and every candidate. */
        var evals = 0

        /** The evaluations refused before any read — a new point outside the corridor. */
        var outside = 0

        /** The candidates a clearance proof refused — would-be winners in the price-first order. */
        var refusedClear = 0

        /** The windows' seeds a clearance proof refused — the window then starts with no finite best. */
        var seedsRefused = 0

        /** Candidates rejected on their legs' travel alone — no price read, no proof. */
        var cutTravel = 0

        /** Candidates rejected on the legs priced so far plus the rest's travel — the rest unpriced, no proof. */
        var cutLeg = 0

        /** Candidates priced in full that did not beat the best so far — no proof. */
        var lost = 0

        /** Candidates proved — the ones that would have won on price. */
        var proofs = 0
    }

    private fun offset(p: LatLng, eastM: Double, northM: Double): LatLng = LatLng(
        p.latitude + northM / metresPerDegree,
        p.longitude + eastM / (metresPerDegree * cos(Math.toRadians(p.latitude)))
    )
}
