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
 * ([Search.searchSplit]); the window takes the cheaper of its slide and its split. A split the corner pass would draw
 * back as the one corner it splits is refused, and a point a split made is not split again in the same pass.
 *
 * **Two turns standing for one are merged at their crossing.** The pull commits the grid path's own point at each
 * refused chord, so where a leg leaves one feature and the next reaches another the line can carry an S — a turn one
 * way, then the other — on open water, each point kept because the chord skipping it is priced, and no slow water on
 * the legs for the gate above to open (the 2026-10-28 La Salis → Le Village Englouti "Balanced" line, between La
 * Fourmigue's band and the 10 kn zone off Cap d'Antibes). A three-span window whose two inner points turn opposite ways
 * is offered one candidate, the crossing of its outer legs, whatever its legs' water; where the slow gate opened the
 * window too, the crossing is read after the window's own search, against its best, so that search is never moved.
 *
 * **The crossing then slides along its own legs.** The crossing is right in its legs' directions, not in where along them
 * it turns: the outer leg a feature pins at its far end is right, and the turn belongs where the other leg just clears
 * the feature at its own end — on the device pair at La Fourmigue's band edge, 1.1 km back along the crossing's own leg
 * (2026-10-28 10:28). So every crossing offered — won or lost, a crossing losing by metres while a point on its leg wins —
 * is searched along each leg in one dimension ([Search.searchAlongLegs]), last, a point kept only where it beats the window.
 *
 * **A lone turn slides along its own legs too.** One price turn whose two legs are clean, the chord skipping it through
 * slow water, no S around it: the slow gate opens nothing and no S is offered, yet the turn may stand far from where its
 * legs would graze the water — "Around"'s 43° turn on the 2026-10-28 18:16 device run, 1.5 km back along its incoming leg
 * (lone-turns plan). Such a two-span window, after its three- and four-span windows, is searched along its legs from its
 * own cost, a point kept only where it beats the window.
 */
internal object RouteApproachPass {
    private const val MAX_SPAN = 4
    private const val MAX_PASSES = 2
    private const val MAX_SCALES = 6
    private const val DIRECTIONS = 8

    /** A leg's two directions — toward its far end and back — for the one-dimensional search along it. */
    private const val LEG_DIRECTIONS = 2

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
        keepLandTurns: Boolean = true,
        /**
         * **Whether a lone turn is slid along its legs** — `true` on every shipped path. A fixture passes `false` to pin
         * another search's answer without the lone slide that may follow it in the next pass.
         */
        slideLoneTurns: Boolean = true
    ): List<LatLng> {
        if (line.size < 3 || !setup.field.hasSoft || setup.paceMps <= 0.0) {
            trace { "APPROACH skipped=no-candidates" }
            return line
        }
        // Every search here halves its steps down to this floor; the slide along a crossing's legs has no other end.
        require(stepM > 0.0) { "the approach pass's step must be positive: $stepM" }
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
        val search = Search(ctx, bounds, stepM, paceRadiusM, tally, incumbentFirst, checkCancelled)

        // **A lone turn** — [v] turning at least the corner pass's floor, the chord skipping it through slow water and not
        // refused for land: a price turn on legs the caller has read clean, which neither the slow gate nor the S read
        // reaches. Read cheapest first.
        fun isLone(a: LatLng, v: LatLng, e: LatLng): Boolean =
            abs(RouteCornerPass.turnDegreesAt(a, v, e)) >= RouteCornerPass.MIN_TURN_DEG && slow(a, e) && !landTurn(a, e)

        // **What the window `work[from] … work[from + span]` is offered, read before any read of its own**, or `null`
        // where it is not searched. A crossing and adjoining open water, not an unrelated all-slow or all-free run, is
        // searched around its seed; an S — two opposite turns standing for the one their outer legs' crossing makes —
        // is offered that crossing, whatever its legs' water; and a lone turn — a two-span window both of whose legs are
        // clean — slides along its own legs ([isLone]), unless a split just made its point. A merge of a land turn is never
        // searched (P1), nor a window a pass already searched without a move (F1).
        fun offerFor(from: Int, span: Int, firstSlow: Boolean, lastSlow: Boolean, splitThisPass: Set<LatLng>): Offer? {
            val to = from + span
            val slowOpen = firstSlow != lastSlow
            if (!slowOpen) tally.slowSkipped++
            val seed = work[if (firstSlow) from + 1 else to - 1]
            val near = if (firstSlow) work[from] else work[to]
            val radiusM = 2.0 * SpatialOperations.haversine(near, seed)
            val searched = slowOpen && radiusM >= stepM
            val sWindow = span == 3 && isS(work, from)
            val loneTurn = slideLoneTurns && span == 2 && !firstSlow && !lastSlow && work[from + 1] !in splitThisPass &&
                isLone(work[from], work[from + 1], work[to])
            if (!searched && !sWindow && !loneTurn) return null
            if (keepLandTurns && span >= 3 && (from + 1 until to).any { landTurn(work[it - 1], work[it + 1]) }) {
                tally.landTurnWindows++
                return null
            }
            val window = work.subList(from, to + 1).toList()
            if (skipRepeats && window in unmoved) {
                tally.repeats++
                return null
            }
            when {
                searched -> tally.windows++
                sWindow -> tally.sWindows++
                else -> tally.loneWindows++
            }
            // One whose seed a split just made searches its slide alone, so it is never recorded as searched.
            return Offer(
                window, seed, radiusM, searched, sWindow, splitHeld = span == 2 && seed in splitThisPass, loneTurn = loneTurn
            )
        }

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
                val firstSlow = slow(work[from], work[from + 1])
                for (span in minOf(MAX_SPAN, work.lastIndex - from) downTo 2) {
                    val to = from + span
                    val offer = offerFor(from, span, firstSlow, slow(work[to - 1], work[to]), splitThisPass) ?: continue
                    val oldStartNs = System.nanoTime()
                    val oldSec = (from until to).sumOf {
                        MultipassPull.totalSec(work[it], work[it + 1], ctx)
                    }
                    tally.priceNs += System.nanoTime() - oldStartNs
                    val answer = search.answerFor(offer, oldSec)
                    if (answer == null) {
                        if (!offer.splitHeld) unmoved += offer.window
                        continue
                    }
                    savedSec += oldSec - answer.sec
                    // **The move's own line**, each replaced point named with the reason it stood — the window's own
                    // points, captured before the splice, so the line says what the move replaced whenever it is
                    // written. A slide's one point is replaced, not deleted, so `LAND` there is no breach of the rule;
                    // a split's `at=` names its two points in the line's order.
                    val windowFrom = from
                    val windowSavedSec = oldSec - answer.sec
                    trace {
                        val kind = when {
                            answer.kind == AnswerKind.SPLIT -> "split"
                            answer.kind == AnswerKind.SLID_LONE -> "lone"
                            span == 2 -> "slide"
                            else -> "merge"
                        }
                        val at = if (answer.points.size == 2) {
                            "[${MultipassPull.atText(answer.points[0])} ${MultipassPull.atText(answer.points[1])}]"
                        } else {
                            MultipassPull.atText(answer.points.single())
                        }
                        "APPROACH move pass=${pass + 1} from=$windowFrom span=$span kind=$kind replaced=[" +
                            offer.window.windowed(3).joinToString(" ") { (u, v, w) ->
                                "${MultipassPull.atText(v)}:${keepCause(u, v, w, untallied)}"
                            } +
                            "] at=$at savedSec=${fmt(windowSavedSec, 3)}"
                    }
                    work.subList(from + 1, to).clear()
                    work.addAll(from + 1, answer.points)
                    when (answer.kind) {
                        AnswerKind.SPLIT -> {
                            // Only `q` is ever a later window's seed in this pass: `p` stands at an index it has passed.
                            splitThisPass += answer.points.last()
                            tally.splits++
                        }
                        AnswerKind.SLID_CROSSING -> tally.slidCrossings++
                        AnswerKind.SLID_LONE -> tally.slidLones++
                        else -> Unit
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
        // **The lone turns left (P0)** — read on the line the pass hands on, each point once ([isLone] on clean legs):
        // what the slide along the legs left — a leg already grazing the water, a slide saving under
        // `route.approach.lone.minSavedSec`, a split's point, or a turn a second-pass move made after the last pass. Read
        // on the pass's own reads, mostly memoised by the windows', so every route pays them. Each is named on its own
        // line with its **slack** — how far along either leg the turn could slide while the rotated leg stays
        // clear and free of slow water, halved to the step (the leg's far end is the skipping chord, slow by the count's
        // own test), and the travel that slide would save — so a reader can tell a cell's quantisation from a misplaced
        // turn. The slack is read inside the trace, on the untallied context, so a route that is not traced pays nothing
        // for it and the tally reads alike either way. It moves nothing.
        fun slackToward(end: LatLng, v: LatLng, far: LatLng): Double {
            val legM = SpatialOperations.haversine(v, end)
            var freeM = 0.0
            var blockedM = legM
            while (blockedM - freeM >= stepM) {
                val midM = (freeM + blockedM) / 2.0
                val p = along(v, end, midM / legM)
                val free = MultipassPull.legClear(p, far, untallied) &&
                    !hasSlowWater(p, far, untallied, stepM, checkCancelled)
                if (free) freeM = midM else blockedM = midM
            }
            return freeM
        }
        for (i in 1 until work.lastIndex) {
            val a = work[i - 1]
            val v = work[i]
            val e = work[i + 1]
            if (slow(a, v) || slow(v, e) || !isLone(a, v, e)) continue
            tally.loneTurns++
            trace {
                val turnDeg = RouteCornerPass.turnDegreesAt(a, v, e)
                val backM = slackToward(a, v, e)
                val onM = slackToward(e, v, a)
                // The travel a slide to the longer reach saves — the two legs' length less the new pair's, at the pace.
                val p = if (backM >= onM) along(v, a, backM / SpatialOperations.haversine(v, a)) else
                    along(v, e, onM / SpatialOperations.haversine(v, e))
                val savedM = SpatialOperations.haversine(a, v) + SpatialOperations.haversine(v, e) -
                    SpatialOperations.haversine(a, p) - SpatialOperations.haversine(p, e)
                "APPROACH lone at=${MultipassPull.atText(v)} turn=${fmt(turnDeg, 1)} " +
                    "slackM=${fmt(maxOf(backM, onM), 0)} toward=${if (backM >= onM) "back" else "on"} " +
                    "travelSec=${fmt(savedM / ctx.paceMps, 2)}"
            }
        }
        // One line: what the pass found, then where its time and its reads went — the clearance proofs (the
        // seeds' included in `clearMs`) and the priced chords apart, the slow-water test of the line's legs and P0's
        // chords beside them, and the pull's own read counts on the pass's context. `refusedClear` counts candidates
        // and `seedsRefused` the windows' seeds; `edgeReads` and `farEdgeReads` are the near and the far edge
        // reads actually paid, and like the memo's hits they read zero where the pass holds no memo. `repeats`
        // are the windows the second pass skipped, `landTurnWindows` the merges never searched for holding a land
        // turn (its `landProofs` inside `clearMs`), and `passMs`/`passEvals` each pass's own share. `splitWindows`
        // are the slide windows a split was searched in, `splitEvals` its candidates (inside `candidates` and
        // `evals`), `splits` the splits spliced and `splitFloor` the cuts refused under the corner pass's span.
        // `slowSkipped` are the windows the slow-water gate closed, `sWindows` the S windows the slow gate or the radius
        // left unsearched and the S read opened, and `crossings` the crossing candidates of every S window, searched or
        // not (inside `candidates` and `evals`) — the count of S windows a reader of the pass's cost wants;
        // `crossingSlides` the candidates the slide along those crossings' legs read (inside `candidates` and `evals`)
        // and `slidCrossings` the moves it placed; `loneWindows`, `loneSlides` and `slidLones` the same for the lone turns
        // slid along their legs (inside `candidates` and `evals`). `loneTurns` are the lone turns left on the line (P0),
        // each with its own `APPROACH lone` line — their reads inside `slowMs`/`slowLegs` and `landProofs`, the slack's
        // in none. The read
        // clocks — `nearEdgeMs`, `farEdgeMs`, `fieldPriceMs`, `fieldClearMs` — are each the paid side of one
        // read, inside `priceMs` or `clearMs`; `berthEdgeNone`/`wallEdgeNone` the near reads that found no edge.
        // Each accepted move has its own `APPROACH move` line before this one.
        trace {
            "APPROACH moved=$moved candidates=${tally.candidates} savedSec=${fmt(savedSec, 3)} " +
                "ms=${fmt(msSince(startNs))} windows=${tally.windows} repeats=${tally.repeats} " +
                "slowSkipped=${tally.slowSkipped} loneTurns=${tally.loneTurns} " +
                "landTurnWindows=${tally.landTurnWindows} landProofs=${tally.landProofs} " +
                "splitWindows=${tally.splitWindows} splitEvals=${tally.splitEvals} splits=${tally.splits} " +
                "splitFloor=${tally.splitFloor} sWindows=${tally.sWindows} crossings=${tally.crossings} " +
                "crossingSlides=${tally.crossingSlides} slidCrossings=${tally.slidCrossings} " +
                "loneWindows=${tally.loneWindows} loneSlides=${tally.loneSlides} slidLones=${tally.slidLones} " +
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

    /**
     * Source-owned price arms identify slow water; marker/depth prices alone do not open the slow gate (an S window is
     * offered its crossing whatever its water).
     */
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

    /**
     * **What a window is offered** — its points, the seed and radius its slide would search, whether the slow gate
     * opened it ([searched]), whether it is an S ([sWindow]), whether its seed is a point a split just made, so it
     * searches its slide alone ([splitHeld]), and whether it is a lone turn ([loneTurn]).
     */
    private data class Offer(
        val window: List<LatLng>,
        val seed: LatLng,
        val radiusM: Double,
        val searched: Boolean,
        val sWindow: Boolean,
        val splitHeld: Boolean,
        val loneTurn: Boolean
    )

    /** Which search placed a window's answer — the move's line and the tally read it. */
    private enum class AnswerKind { SLIDE, SPLIT, CROSSING, SLID_CROSSING, SLID_LONE }

    /**
     * **A window's answer** — the point or points that would replace its inner points, in the line's order (one, or a
     * split's two), and the window's cost through them (`+∞` for a slide whose seed the proof refused).
     */
    private data class Answer(val points: List<LatLng>, val sec: Double, val kind: AnswerKind)

    /**
     * **The best so far, and the one rule that replaces it** — a cost and the answer that holds it, taken over only by
     * a strictly cheaper one ([takes]). Every search keeps its best in one: the slide's from its seed, the split's from
     * its incumbent, the window's from its own cost, each leg's from the crossing's cost — or `+∞` where it lost.
     */
    private class Incumbent(sec: Double, answer: Answer? = null) {
        var sec: Double = sec
            private set
        var answer: Answer? = answer
            private set

        /** Takes [points] at [sec] where it is strictly cheaper; whether it did. */
        fun takes(sec: Double, points: List<LatLng>, kind: AnswerKind): Boolean =
            sec < this.sec && takes(Answer(points, sec, kind))

        /** Takes [candidate] where there is one and it is strictly cheaper; whether it did. */
        fun takes(candidate: Answer?): Boolean {
            if (candidate == null || !(candidate.sec < sec)) return false
            sec = candidate.sec
            answer = candidate
            return true
        }
    }

    /**
     * **What every search of a window reads with** — the context, the corridor, the pass's step, the corner pass's
     * pace-speed radius (the split's floor), the tally, the evaluation order and the cancel read — the one evaluation
     * every candidate takes ([evaluate]) and the window's answer ([answerFor]).
     */
    private class Search(
        val ctx: PullContext,
        val bounds: BBox,
        val stepM: Double,
        val paceRadiusM: Double,
        val tally: ApproachTally,
        val incumbentFirst: Boolean,
        val checkCancelled: () -> Unit
    ) {
        /**
         * **The window's answer, or `null` where nothing beats [oldSec]** — each search read after the one before,
         * against the best so far, so a later one never moves an earlier one (§11 R1): the slide round the seed; in a
         * two-span window the split of the slide's own answer (a split of the seed keeps part of legs the slide moved off
         * for their price, and the slid window may no longer touch slow water, so the next pass never offers it — the
         * 2026-10-27 "Balanced" run); in an S, its crossing, then that crossing slid along its own legs, whether it won or
         * lost (P5b); a lone turn, slid along its own legs (lone-turns P1). An S the slow gate or the radius left
         * unsearched starts from the window's own cost, and so does a lone turn.
         */
        fun answerFor(offer: Offer, oldSec: Double): Answer? {
            val window = offer.window
            val a = window.first()
            val e = window.last()
            val best = Incumbent(oldSec)
            if (offer.searched) {
                val slide = searchSlide(a, offer.seed, e, offer.radiusM)
                best.takes(slide)
                if (window.size == 3 && !offer.splitHeld) {
                    tally.splitWindows++
                    best.takes(searchSplit(a, slide.points.single(), e, best.sec))
                }
            }
            if (offer.sWindow) {
                RouteCornerPass.linesCrossing(a, window[1], window[2], e)?.let { crossing ->
                    tally.crossings++
                    val sec = evaluate(listOf(a, crossing, e), best.sec, SLIDE_PROOFS)
                    // Only a winning crossing's cost is the same in both evaluation orders; a losing one seeds no leg.
                    val won = best.takes(sec, listOf(crossing), AnswerKind.CROSSING)
                    best.takes(
                        searchAlongLegs(
                            a, crossing, e, if (won) sec else Double.POSITIVE_INFINITY, best.sec, AnswerKind.SLID_CROSSING
                        )
                    )
                }
            }
            // A lone turn slides along its own legs from the window's own cost — the same number in both orders — and is
            // kept only where it saves `route.approach.lone.minSavedSec`: a smaller saving is the grid's rounding.
            // A turn whose skipping chord's travel alone cannot save the floor is not searched: every point on its legs
            // costs at least that travel, the price never negative — the same bound in both orders.
            val loneIncumbent = best.sec - AppConfig.routeApproachLoneMinSavedSec
            if (offer.loneTurn && SpatialOperations.haversine(a, e) / ctx.paceMps < loneIncumbent) {
                best.takes(searchAlongLegs(a, window[1], e, oldSec, loneIncumbent, AnswerKind.SLID_LONE))
            }
            return best.answer
        }

        /**
         * **One candidate's cost where it can beat [incumbent]** — [candidateSec] pricing first, or [replacementSec]
         * proving first where a fixture asks the order this replaced — so both orders decide alike. Each call is one
         * of the tally's `candidates`.
         */
        fun evaluate(route: List<LatLng>, incumbent: Double, proofs: List<Int>): Double {
            tally.candidates++
            return if (incumbentFirst) {
                candidateSec(route, ctx, bounds, incumbent, tally, proofs)
            } else {
                replacementSec(route, ctx, bounds, tally, proofs)
            }
        }

        /** A window's seed — the line's own point — proved first and priced, never one of the `candidates`. */
        fun seedSec(route: List<LatLng>): Double = replacementSec(route, ctx, bounds, tally, SLIDE_PROOFS, seed = true)

        /**
         * **The window's one point, searched around its [seed]** — the seed's own cost first ([seedSec], so a seed
         * the pass's proof refuses starts the slide with no finite best), then eight directions at each scale from half
         * [radiusM] down to the pass's step, halving, the centre moving to each winner and no candidate further than
         * [radiusM] from the seed.
         */
        private fun searchSlide(a: LatLng, seed: LatLng, b: LatLng, radiusM: Double): Answer {
            val seedSec = seedSec(listOf(a, seed, b))
            val best = Incumbent(seedSec, Answer(listOf(seed), seedSec, AnswerKind.SLIDE))
            patternSearch(
                seed, doubleArrayOf(radiusM / 2.0), DIRECTIONS, stepM, MAX_SCALES, checkCancelled,
                candidateAt = { centre, steps, angle -> offset(centre, steps[0] * cos(angle), steps[0] * sin(angle)) }
            ) { candidate ->
                SpatialOperations.haversine(seed, candidate) <= radiusM && best.takes(
                    evaluate(listOf(a, candidate, b), best.sec, SLIDE_PROOFS), listOf(candidate), AnswerKind.SLIDE
                )
            }
            return best.answer!!
        }

        /**
         * **A point [x] slid along its own legs, or `null` where no point on them beats [incumbent]** — an S's crossing
         * (P5b) or a lone turn (lone-turns P1), their cases in the class KDoc. Each leg is searched in one dimension — a
         * point `backM` from [x] toward [a], then toward [e], the route `a → p → e` — by [patternSearch] with a leg's
         * two directions, from half the leg down to the pass's step,
         * **with no scale cap**, so a leg of any length is read to the step. A point off its leg is refused before any read
         * and counted nowhere.
         *
         * **Each leg's search follows its own best, not the window's.** Its centre moves to every point cheaper than the best
         * found on that leg — seeded with [crossingSec], the crossing's own cost where it was priced in full (it won), `+∞`
         * where it lost or was refused, a lone turn's own window cost — so a leg whose middle loses to the window's best is
         * still read past its middle, where the turn may belong (the device's turn stood 1.1 km along a 1.3 km leg). A
         * point is kept only where it beats
         * [incumbent] too, and every evaluation ([evaluate]) is priced against the leg's best, so a crossing that lost
         * pays more reads than one that won.
         */
        private fun searchAlongLegs(
            a: LatLng,
            x: LatLng,
            e: LatLng,
            crossingSec: Double,
            incumbent: Double,
            /** The kind a slid point is placed as — [AnswerKind.SLID_CROSSING] or [AnswerKind.SLID_LONE] — and its count. */
            kind: AnswerKind
        ): Answer? {
            val best = Incumbent(incumbent)
            for (end in listOf(a, e)) {
                val legM = SpatialOperations.haversine(x, end)
                // An unbounded loop ends on its floor: a leg it could never halve under is not searched.
                if (!legM.isFinite() || legM <= 0.0) continue
                val leg = Incumbent(crossingSec)
                patternSearch(
                    0.0, doubleArrayOf(legM / 2.0), LEG_DIRECTIONS, stepM, Int.MAX_VALUE, checkCancelled,
                    candidateAt = { centre, steps, angle -> centre + steps[0] * cos(angle) }
                ) { backM ->
                    if (backM <= 0.0 || backM >= legM) return@patternSearch false
                    val p = along(x, end, backM / legM)
                    if (kind == AnswerKind.SLID_LONE) tally.loneSlides++ else tally.crossingSlides++
                    val sec = evaluate(listOf(a, p, e), leg.sec, SLIDE_PROOFS)
                    if (!leg.takes(sec, listOf(p), kind)) return@patternSearch false
                    best.takes(leg.answer)
                    true
                }
            }
            return best.answer
        }

        /**
         * **The best split of [v]'s turn that beats [incumbent], or `null`** — two points in place of [v], `p` on the leg
         * from [a] and `q` on the leg to [b], searched by [patternSearch] in the plane of their distances from [v]:
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
         * one evaluation ([evaluate]), the new chord proved before the two legs it joins.
         */
        private fun searchSplit(
            a: LatLng,
            v: LatLng,
            b: LatLng,
            incumbent: Double
        ): Answer? {
            val inM = SpatialOperations.haversine(a, v)
            val outM = SpatialOperations.haversine(v, b)
            val best = Incumbent(incumbent)
            fun wins(cuts: Pair<Double, Double>): Boolean {
                val (cutInM, cutOutM) = cuts
                if (cutInM <= 0.0 || cutInM >= inM || cutOutM <= 0.0 || cutOutM >= outM) return false
                val p = along(v, a, cutInM / inM)
                val q = along(v, b, cutOutM / outM)
                val summedDeg = abs(RouteCornerPass.turnDegreesAt(a, p, q)) + abs(RouteCornerPass.turnDegreesAt(p, q, b))
                if (RouteCornerPass.pairIsOneTurn(SpatialOperations.haversine(p, q), summedDeg, paceRadiusM)) {
                    tally.splitFloor++
                    return false
                }
                tally.splitEvals++
                return best.takes(evaluate(listOf(a, p, q, b), best.sec, SPLIT_PROOFS), listOf(p, q), AnswerKind.SPLIT)
            }
            val halfCut = Pair(inM / 2.0, outM / 2.0)
            // The seed is a candidate like any other: the search centres on it whether or not it wins.
            wins(halfCut)
            patternSearch(
                halfCut, doubleArrayOf(inM / 4.0, outM / 4.0), DIRECTIONS, stepM, MAX_SCALES, checkCancelled,
                candidateAt = { (centreIn, centreOut), steps, angle ->
                    Pair(centreIn + steps[0] * cos(angle), centreOut + steps[1] * sin(angle))
                }
            ) { wins(it) }
            return best.answer
        }
    }

    /**
     * **The pass's one pattern search** — from [start], [directions] candidates at each scale round a centre held for
     * that scale ([candidateAt], from the centre, the scale's per-axis steps and the direction's angle), the centre
     * moving to each candidate [tries] accepts; the steps start at [firstSteps] and halve, and the search ends once
     * every step is under [floorM] or after [maxScales] scales. [tries] decides what a candidate is worth and counts it;
     * one it refuses unread is simply not accepted. Returns the last accepted point, [start] where none was.
     */
    private inline fun <T> patternSearch(
        start: T,
        firstSteps: DoubleArray,
        directions: Int,
        floorM: Double,
        maxScales: Int,
        checkCancelled: () -> Unit,
        candidateAt: (centre: T, steps: DoubleArray, angle: Double) -> T,
        tries: (T) -> Boolean
    ): T {
        var best = start
        val steps = firstSteps.copyOf()
        for (scale in 0 until maxScales) {
            if (steps.all { it < floorM }) break
            val centre = best
            for (direction in 0 until directions) {
                checkCancelled()
                val angle = direction * 2.0 * PI / directions
                val candidate = candidateAt(centre, steps, angle)
                if (tries(candidate)) best = candidate
            }
            for (i in steps.indices) steps[i] /= 2.0
        }
        return best
    }

    /**
     * **Whether the window `work[from] … work[from + 3]` is an S** — its two inner points turning opposite ways, each
     * by at least the turn the corner pass rounds ([RouteCornerPass.MIN_TURN_DEG]), read with that pass's own signed
     * turn ([RouteCornerPass.turnDegreesAt]). Bearings alone: it reads no field value.
     */
    private fun isS(work: List<LatLng>, from: Int): Boolean {
        val first = RouteCornerPass.turnDegreesAt(work[from], work[from + 1], work[from + 2])
        val second = RouteCornerPass.turnDegreesAt(work[from + 1], work[from + 2], work[from + 3])
        return first * second < 0.0 &&
            abs(first) >= RouteCornerPass.MIN_TURN_DEG && abs(second) >= RouteCornerPass.MIN_TURN_DEG
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
         * three for a split), the seeds' included, and the land-turn proofs that decide a merge window's eligibility and
         * a lone turn's (P0).
         */
        var clearNs = 0L

        /**
         * Nanoseconds pricing chords — each window's own legs, each cleared seed's two, and as much of each
         * candidate's legs as its bounds let it reach (the travel bound included).
         */
        var priceNs = 0L

        /**
         * Nanoseconds in the slow-water test ([hasSlowWater]) — the line's legs, and a lone turn's skipping chord — each
         * chord read once.
         */
        var slowNs = 0L

        /** The chords the slow-water test read — its cache's misses. */
        var slowLegs = 0

        /** The windows searched: a crossing beside open water, wide enough for the pass's step. */
        var windows = 0

        /** The windows the second pass skipped — searched by the first without a move, their points unchanged. */
        var repeats = 0

        /**
         * The windows the slow-water gate closed — both end legs slow or both free, so no crossing beside open water —
         * counted once per window offered, before any read of its own, whether or not the S read then opens it.
         */
        var slowSkipped = 0

        /**
         * The lone turns left on the line the pass hands on — points whose two legs are clean, which turn at least
         * [RouteCornerPass.MIN_TURN_DEG], and whose skipping chord holds slow water and is not refused for land — what the
         * slide along the legs ([slidLones]) left: under the saving floor, a split's point, or made after the last pass.
         */
        var loneTurns = 0

        /**
         * The S windows the slow gate or the radius left unsearched and the S read opened — read at their crossing
         * alone. An S the slow gate opens is a searched window ([windows]); [crossings] counts both.
         */
        var sWindows = 0

        /** The crossing candidates evaluated, in every S window — inside [candidates] and [evals]. */
        var crossings = 0

        /** The candidates the slide along a crossing's legs evaluated — inside [candidates] and [evals]. */
        var crossingSlides = 0

        /** The moves whose point the slide along a crossing's legs placed. */
        var slidCrossings = 0

        /** The lone-turn windows offered their slide along their legs (lone-turns P1) — read from their own cost. */
        var loneWindows = 0

        /** The candidates the slide along a lone turn's legs evaluated — inside [candidates] and [evals]. */
        var loneSlides = 0

        /** The moves whose point the slide along a lone turn's legs placed. */
        var slidLones = 0

        /** The merge windows skipped because an inner point is a land turn — never searched. */
        var landTurnWindows = 0

        /** The land-turn proofs paid — one per chord the merge eligibility or P0 had not read yet. */
        var landProofs = 0

        /** The slide windows a split was searched in. */
        var splitWindows = 0

        /** The split candidates evaluated — inside [candidates] and [evals]. */
        var splitEvals = 0

        /** The splits spliced. */
        var splits = 0

        /** The split cuts refused before any read, their points closer than the corner pass's span. */
        var splitFloor = 0

        /**
         * The candidates evaluated: the slides' and merges' around their seed, the splits' on their legs, the S
         * windows' crossings and the points along those crossings' legs.
         */
        var candidates = 0

        /** The replacements evaluated: each window's seed and every candidate. */
        var evals = 0

        /** The evaluations refused before any read — a new point outside the corridor. */
        var outside = 0

        /** The candidates a clearance proof refused — would-be winners in the price-first order. */
        var refusedClear = 0

        /**
         * The windows' seeds a clearance proof refused — the slide then starts with no finite best, the window from its
         * own cost.
         */
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
