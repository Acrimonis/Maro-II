package ykws.android.maro.spatial.multipass

import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.spatial.SpatialOperations
import kotlin.math.PI
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
 * pace, then the first leg's full cost plus the second's travel, since no price is ever negative (every
 * source only adds, and every input to it is clamped at zero or above). Each decision is the one the
 * proof-first order took; only the reads that cannot change it are skipped (the 2026-10-27 device split:
 * clearance and price were even, and most candidates lose).
 */
internal object RouteApproachPass {
    private const val MAX_SPAN = 4
    private const val MAX_PASSES = 2
    private const val MAX_SCALES = 6
    private const val DIRECTIONS = 8
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
        incumbentFirst: Boolean = true
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
        val ctx = (if (reversed) setup.reversed() else setup).context(timing = timing, memo = memo)
        val slowLegs = mutableMapOf<Pair<LatLng, LatLng>, Boolean>()
        fun slow(a: LatLng, b: LatLng): Boolean = slowLegs.getOrPut(a to b) {
            val slowStartNs = System.nanoTime()
            hasSlowWater(a, b, ctx, stepM, checkCancelled).also {
                tally.slowNs += System.nanoTime() - slowStartNs
                tally.slowLegs++
            }
        }
        var moved = 0
        var candidates = 0
        var savedSec = 0.0
        for (pass in 0 until MAX_PASSES) {
            var changed = false
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
                    tally.windows++
                    val oldStartNs = System.nanoTime()
                    val oldSec = (from until to).sumOf {
                        MultipassPull.totalSec(work[it], work[it + 1], ctx)
                    }
                    tally.priceNs += System.nanoTime() - oldStartNs
                    var pivot = seed
                    var bestSec = replacementSec(a, pivot, e, ctx, bounds, tally)
                    var scaleM = radiusM / 2.0
                    for (scale in 0 until MAX_SCALES) {
                        if (scaleM < stepM) break
                        val centre = pivot
                        for (direction in 0 until DIRECTIONS) {
                            checkCancelled()
                            val angle = direction * 2.0 * PI / DIRECTIONS
                            val candidate = offset(centre, scaleM * cos(angle), scaleM * sin(angle))
                            if (SpatialOperations.haversine(seed, candidate) > radiusM) continue
                            candidates++
                            val sec = if (incumbentFirst) {
                                candidateSec(a, candidate, e, ctx, bounds, bestSec, tally)
                            } else {
                                replacementSec(a, candidate, e, ctx, bounds, tally)
                            }
                            if (sec < bestSec) {
                                bestSec = sec
                                pivot = candidate
                            }
                        }
                        scaleM /= 2.0
                    }
                    if (bestSec >= oldSec || !bestSec.isFinite()) continue
                    savedSec += oldSec - bestSec
                    work.subList(from + 1, to).clear()
                    work.add(from + 1, pivot)
                    moved++
                    changed = true
                    break
                }
                from++
            }
            if (!changed) break
        }
        // One line: what the pass found, then where its time and its reads went — the clearance proofs and the
        // priced chords apart, the slow-water test of the line's legs beside them, and the pull's own read
        // counts on the pass's context (the memo's hits are this pass's share of the memo the fine pass holds).
        trace {
            "APPROACH moved=$moved candidates=$candidates savedSec=${fmt(savedSec, 3)} " +
                "ms=${fmt(msSince(startNs))} windows=${tally.windows} evals=${tally.evals} " +
                "outside=${tally.outside} cutTravel=${tally.cutTravel} cutLeg=${tally.cutLeg} " +
                "lost=${tally.lost} proofs=${tally.proofs} refusedClear=${tally.refusedClear} " +
                "clearMs=${fmt(tally.clearNs / NANOS_PER_MS)} priceMs=${fmt(tally.priceNs / NANOS_PER_MS)} " +
                "slowMs=${fmt(tally.slowNs / NANOS_PER_MS)} slowLegs=${tally.slowLegs} " +
                "clearMarks=${timing.clearanceMarks} hardReads=${timing.hardReads} " +
                "priceMarks=${timing.marks} priceReads=${timing.priceReads} " +
                "edgeReads=${(memo?.edgeMisses ?: 0L) - edgeMissesBefore} farEdgeReads=${timing.farEdgeReads} " +
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
            val t = marks.markT(i)
            if (slowAt(LatLng(
                    a.latitude + (b.latitude - a.latitude) * t,
                    a.longitude + (b.longitude - a.longitude) * t
                ))) return true
        }
        return false
    }

    private fun replacementSec(
        a: LatLng,
        p: LatLng,
        b: LatLng,
        ctx: PullContext,
        bounds: BBox,
        tally: ApproachTally
    ): Double {
        tally.evals++
        if (!inBox(bounds, p)) {
            tally.outside++
            return Double.POSITIVE_INFINITY
        }
        // Only the real route ends retain waivers: a window end or a new pivot earns no exemption. The second
        // leg is proved only where the first clears, exactly as the `||` it replaces short-circuited.
        val clearStartNs = System.nanoTime()
        val clear = MultipassPull.legClear(a, p, ctx) && MultipassPull.legClear(p, b, ctx)
        tally.clearNs += System.nanoTime() - clearStartNs
        if (!clear) {
            tally.refusedClear++
            return Double.POSITIVE_INFINITY
        }
        val priceStartNs = System.nanoTime()
        val sec = MultipassPull.totalSec(a, p, ctx) + MultipassPull.totalSec(p, b, ctx)
        tally.priceNs += System.nanoTime() - priceStartNs
        return sec
    }

    /**
     * **A candidate's cost where it can beat [incumbent], and `+∞` wherever it cannot** — the window's own
     * evaluation of every candidate after its seed. The caller keeps a candidate only where its cost is below
     * the best so far and the replacement is clear, so the order this takes leaves each decision as the
     * proof-first [replacementSec] made it and only skips reads:
     *
     * 1. **Outside the corridor** — `+∞` before any read, as before.
     * 2. **The travel bound** — both legs' travel at the pace (the very `haversine / pace` term
     *    [MultipassPull.totalSec] adds its price to) already at or above the incumbent: no price, no proof.
     * 3. **The first leg's bound** — the first leg's full cost plus the second's travel at or above it: the
     *    second leg is never priced.
     * 4. **The full cost** at or above it: no proof is run.
     * 5. **The proof** — only a candidate that would win is proved clear, and a refusal is `+∞` as before.
     *
     * Each bound is exact under rounding: a leg's cost is its travel plus a price that is never negative, and
     * rounding to the nearest is monotone, so a bound at or above the incumbent leaves the full sum there too.
     * The cost returned is the same `first + second` the proof-first order summed. The window's seed is still
     * evaluated by [replacementSec], so its own cost — the first incumbent — is the value it always was.
     */
    private fun candidateSec(
        a: LatLng,
        p: LatLng,
        b: LatLng,
        ctx: PullContext,
        bounds: BBox,
        incumbent: Double,
        tally: ApproachTally
    ): Double {
        tally.evals++
        if (!inBox(bounds, p)) {
            tally.outside++
            return Double.POSITIVE_INFINITY
        }
        val priceStartNs = System.nanoTime()
        val secondTravel = SpatialOperations.haversine(p, b) / ctx.paceMps
        if (SpatialOperations.haversine(a, p) / ctx.paceMps + secondTravel >= incumbent) {
            tally.priceNs += System.nanoTime() - priceStartNs
            tally.cutTravel++
            return Double.POSITIVE_INFINITY
        }
        val first = MultipassPull.totalSec(a, p, ctx)
        if (first + secondTravel >= incumbent) {
            tally.priceNs += System.nanoTime() - priceStartNs
            tally.cutLeg++
            return Double.POSITIVE_INFINITY
        }
        val sec = first + MultipassPull.totalSec(p, b, ctx)
        tally.priceNs += System.nanoTime() - priceStartNs
        if (!(sec < incumbent)) {
            tally.lost++
            return Double.POSITIVE_INFINITY
        }
        tally.proofs++
        val clearStartNs = System.nanoTime()
        val clear = MultipassPull.legClear(a, p, ctx) && MultipassPull.legClear(p, b, ctx)
        tally.clearNs += System.nanoTime() - clearStartNs
        if (!clear) {
            tally.refusedClear++
            return Double.POSITIVE_INFINITY
        }
        return sec
    }

    /**
     * **What one refinement spent, and where** — the clocks and counts its `APPROACH` line carries. It reads
     * and never steers: the clocks wrap calls the pass makes anyway, so the line and the answer are unmoved
     * by carrying it.
     */
    private class ApproachTally {
        /** Nanoseconds in the evaluations' clearance proofs ([MultipassPull.legClear], up to two each). */
        var clearNs = 0L

        /** Nanoseconds pricing chords — each window's own legs and each cleared evaluation's two. */
        var priceNs = 0L

        /** Nanoseconds in the slow-water test of the line's legs ([hasSlowWater]), each leg read once. */
        var slowNs = 0L

        /** The legs the slow-water test read — its cache's misses. */
        var slowLegs = 0

        /** The windows searched: a crossing beside open water, wide enough for the pass's step. */
        var windows = 0

        /** The replacements evaluated: each window's seed and every candidate. */
        var evals = 0

        /** The evaluations refused before any read — a pivot outside the corridor. */
        var outside = 0

        /** The evaluations a clearance proof refused. */
        var refusedClear = 0

        /** Candidates rejected on the two legs' travel alone — no price read, no proof. */
        var cutTravel = 0

        /** Candidates rejected on the first leg's cost plus the second's travel — one leg priced, no proof. */
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
