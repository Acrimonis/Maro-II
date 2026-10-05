package ykws.android.maro.spatial.multipass

import ykws.android.maro.data.model.LatLng
import ykws.android.maro.spatial.SpatialOperations
import kotlin.math.ceil
import kotlin.math.floor

/**
 * The clearance taut pull: collapses the coarse cell path into straight waypoints that stay clear of
 * the field's walls. The classic two-pointer string-pull — the anchor stands while the probe advances,
 * and on a failed extension the probe's predecessor becomes the new anchor — walked over the
 * **unified cost field** [RouteCostField] rather than a bare distance, so the same walk serves the
 * coastline of stage 1 and every source the later phases add.
 *
 * A chord is clear when every sample along it stands at least [marginM] from the field's nearest hard
 * wall, sampled at `≤ marginM / 2` so the margin is a guarantee rather than an approximation. The
 * clearance is **exempt where the margin itself is waived** — within a margin-radius disc around each
 * forced-free end (the raw start and aim), and on the **carved approach** each end was granted
 * ([EndApproaches]), which is how the first and last legs reconcile with the margin predicate even
 * when the berth runs deeper than that disc. A rejected chord is re-walked once from its
 * predecessor's predecessor — the concave-coast retry, bounded rather than looped.
 *
 * **The field's two hard shapes are read apart.** A mark pays the rastered walls' own step
 * ([RouteCostField.hardBlocked]) and its distance to the materialized walls
 * ([RouteCostField.hardDistanceM]) — the live coastline query whose per-mark price this walk exists to
 * bound. Beside the fine step the walk takes a **coarse step**, and it is a required parameter for the
 * reason a caller-owned step always is: a defaulted one would skip a read no caller chose to skip.
 *
 * **The coarse marks are what spares a read.** They are placed first, one per coarse step, at the
 * midpoints of the equal divisions, and every one of them pays the distance read; a fine mark then pays
 * it only where the coarse mark covering it failed to prove its half-step clear. The proof is the
 * distance's own 1-Lipschitz bound — a reading `d` puts every point within half a coarse step at least
 * `d − coarseStep / 2` off the wall — so **no verdict can move**: a chord refused today is refused, one
 * pulled taut today is taut, and the marks themselves are exactly the ones the fine walk would have made.
 *
 * **No ring clearance.** The pull carries no standoff: a speed zone's outside margin is a **price** in
 * the search and in the field's own price guard, never a clearance here. The two walls that still bind
 * are the land margin and the price guard.
 *
 * **The priced half of the guarantee.** Where the field carries a price, a chord is accepted only
 * while its own summed price stays within the A* cell path's over the span it would replace — so the
 * pull can never shortcut a corner through a priced band the search just went around. The price is a
 * **time** here as it is in the search, so the two sides of that comparison are the same unit. The
 * path's price is a prefix sum, so each candidate costs one walk of the chord alone, and a field with
 * no price skips the whole reading.
 *
 * **The price walk's own reads are coarsened behind a proof its sources declare.** A soft source
 * answers `priceClearanceM(p)` — the distance to the nearest place its own price arm changes — and the
 * walk prices a whole group of fine intervals from one reading exactly where that clearance is at least
 * the group's half-length: no boundary stands inside, so the arm is constant and the group's product is
 * identically the fine sum it replaces. It reads the field's price alone for it, never the hard walls'
 * `blocked` test, since that flag is discarded. A source that declares no boundary proves nothing, and
 * every unproved group keeps today's reads.
 */
private const val MIN_SAMPLE_STEP_M = 1.0

/**
 * **The marks of one sampled walk, on a fixed step from the chord's own anchor.** A walk's marks stand
 * at `anchor + (j + 0.5) × step`, one per whole step along the chord, and the **last interval is sized
 * to the chord's own remainder** rather than left over — so the intervals' lengths sum to the chord's
 * haversine exactly and a one-interval error cannot hide in an uncovered tail.
 *
 * The fixed anchor is the whole point: two attempts that share an anchor share every mark but the tail,
 * which is the property a memo of the reads already paid needs to hit **exactly** — the same point, the
 * same pure function, the same double. Chord-relative marks never do, because their own step
 * `dist / ceil(dist / step)` carries the chord's length into every mark and the two attempts' marks fall
 * apart.
 *
 * The step is the walk's own — the clearance walk's **coarse** step, the price walk's **sampling** step —
 * and it is floored here, at the one home that floors it, so no degenerate step can turn the partition
 * into a runaway loop.
 */
internal class MarkLattice(val lengthM: Double, stepM: Double) {

    /** The step in force, floored at the walk's own floor. */
    val stepM: Double = stepM.coerceAtLeast(MIN_SAMPLE_STEP_M)

    /** The interval count: the whole steps, plus the remainder's own interval where there is one. */
    val count: Int = ceil(lengthM / this.stepM).toInt().coerceAtLeast(1)

    /** The width (m) of interval [j] — one step, the last one sized to the chord's remainder. */
    fun widthM(j: Int): Double = minOf((j + 1) * stepM, lengthM) - minOf(j * stepM, lengthM)

    /** The fraction of the chord at interval [j]'s own midpoint — the mark a walk reads there. */
    fun markT(j: Int): Double = fraction(j * stepM + widthM(j) / 2.0)

    /** The length (m) the interval run `[i0, i1)` covers — never left over at the tail. */
    fun spanLengthM(i0: Int, i1: Int): Double =
        minOf(i1 * stepM, lengthM) - minOf(i0 * stepM, lengthM)

    /** The fraction of the chord at the run `[i0, i1)`'s own midpoint — the point a span proof reads. */
    fun spanMidT(i0: Int, i1: Int): Double =
        fraction((minOf(i0 * stepM, lengthM) + minOf(i1 * stepM, lengthM)) / 2.0)

    /** The interval covering fraction [t] of the chord — the coarse mark that answers for a fine one. */
    fun indexOf(t: Double): Int = minOf((t * lengthM / stepM).toInt(), count - 1)

    /** [metres] from the anchor as a fraction of the chord, or a half for a degenerate empty chord. */
    private fun fraction(metres: Double): Double = if (lengthM > 0.0) metres / lengthM else 0.5
}

/**
 * **The reads one walk has already paid, keyed on the mark's own point.** One memo per walk, shared by
 * the clearance walk and the price walk.
 *
 * **The key is the point itself, never a rounded cell index.** The marks stand on the fixed lattice, so
 * two attempts that share a stretch land on the very same [LatLng] — the same double coordinates — and a
 * `HashMap` keyed by the point answers them with the same value the field would. A cell-rounded key
 * would answer a neighbouring mark's price while every fixture on the walk's own points still passed.
 *
 * **It is bound to its field, and a different field wipes it.** Every accessor checks the field's own
 * **identity** before it answers: a memo that outlived its walk — the field is rebuilt per λ pass — can
 * only ever re-read under the new field, never answer it with the previous field's numbers, so the
 * plan's one-walk lifetime is structural rather than assumed.
 *
 * **Both walks share it, but in the pull's own shape the win is the price half's.** The pull threads one
 * memo through the clearance walk and the price walk alike, and a hit answers the field's own double.
 * The clearance half is **inert** in that walk — every chord evaluation is a fresh `(anchor, probe)`
 * pair, so its marks are its own and none is re-visited — while the price half's hits come from
 * [MultipassPull.softPricePrefix] walking each raw segment before an attempt walks the chord that
 * retraces it.
 *
 * Nothing here is static, shared or cached across walks.
 */
internal class MarkMemo {

    /** The field the two tables were filled under; a different identity wipes them. */
    private var boundField: RouteCostField? = null

    /** The materialized walls' distance already read, by the point it was read at. */
    private val hard = HashMap<LatLng, Double>()

    /** The summed soft price already read, by the point it was read at. */
    private val price = HashMap<LatLng, Double>()

    /** The materialized walls' distance at [p] under [field] — read once, then answered from here. */
    fun hardDistance(field: RouteCostField, p: LatLng): Double {
        bind(field)
        return hard.getOrPut(p) { field.hardDistanceM(p) }
    }

    /** The soft price at [p] under [field] — read once, then answered, the same double each time. */
    fun price(field: RouteCostField, p: LatLng): Double {
        bind(field)
        return price.getOrPut(p) { field.softPriceSecAt(p) }
    }

    /** True where [p]'s price is already held under [field] — a hit, so no read is paid for it. */
    fun hasPrice(field: RouteCostField, p: LatLng): Boolean {
        bind(field)
        return price.containsKey(p)
    }

    /** Wipes both tables when the field's identity changes, so no answer can cross a rebuild. */
    private fun bind(field: RouteCostField) {
        if (boundField !== field) {
            hard.clear()
            price.clear()
            boundField = field
        }
    }
}

object MultipassPull {

    /**
     * Pulls [path] (raw start first, raw aim last) taut into the ordered waypoint list, as direct as
     * the field's margin allows and independent of grid orientation.
     *
     * @param coarseStepM the **clearance walk's** coarse sampling step — the walk's own cell on the
     *   water it was resolved on, handed in by the caller and never defaulted.
     * @param priceStepM the **price walk's** own step — the walk's interior cell, the same step and the
     *   same grouping rule the prefix is built from, so the guard compares two walks of one law and one
     *   unit. The anchors are not shared: the prefix lattices each raw segment from its own start while a
     *   chord lattices the whole line from its own, so the two meet mark for mark on a straight run and
     *   part where the path bends.
     * @param field the unified cost field — its materialized walls' distance is the clearance read
     *   here, and its rastered walls' step is tested at every mark beside it.
     * @param approaches the two ends' carved approaches, whose stretches the margin does not bind.
     * @param refusals the tally the walk counts its refused chords into, or `null` where none is read.
     * @param timing the walk's own tally of what it spent, or `null` where no reader wants it.
     * @param memo the walk's own read memo — **one per walk**, keyed on the mark's own point and bound to
     *   the field it fills under, so a rebuild wipes it rather than letting it answer stale. In this
     *   walk's own shape the shared points are the price walk's: the prefix walks each raw segment before
     *   an attempt walks the chord that retraces it, while the clearance walk never re-visits a point. A
     *   fresh memo is made here, at the walk's own start. A caller passing `null` walks as before.
     */
    internal fun pull(
        path: List<LatLng>,
        start: LatLng,
        aim: LatLng,
        marginM: Double,
        coarseStepM: Double,
        priceStepM: Double,
        field: RouteCostField,
        approaches: EndApproaches = EndApproaches.NONE,
        refusals: PullRefusals? = null,
        timing: PullTiming? = null,
        memo: MarkMemo? = MarkMemo()
    ): List<LatLng> {
        if (path.size <= 2) return path
        val result = ArrayList<LatLng>(path.size)
        result.add(path.first())
        val prefixStartNs = System.nanoTime()
        val pathPriceSec =
            if (field.hasSoft) softPricePrefix(path, marginM, priceStepM, field, timing, memo) else null
        timing?.addPrice(System.nanoTime() - prefixStartNs)
        var anchor = 0
        var probe = 1
        while (probe < path.size) {
            val decision = chordDecision(
                pathPriceSec, path, anchor, probe, marginM, coarseStepM, priceStepM, field, start, aim, approaches,
                timing, memo
            )
            val refused = decision.refusal
            when {
                refused == null -> probe++
                // The immediate step grazes land in a corner: it cannot be pulled, so it is accepted
                // once and the walk moves on — the bounded form of the concave re-walk.
                probe == anchor + 1 -> {
                    refusals?.record(refused)
                    result.add(path[probe])
                    anchor = probe
                    probe++
                }
                else -> {
                    refusals?.record(refused)
                    result.add(path[probe - 1])
                    anchor = probe - 1
                    // probe stands; the loop re-walks the chord from the new anchor exactly once.
                }
            }
        }
        if (result.last() != path.last()) result.add(path.last())
        return result
    }

    /** The chord's own verdict: the geometry stands, then the price guard alone decides — the same
     *  short circuit keeps a refused chord from costing a walk of its own price. Its two halves are
     *  timed apart where a [timing] is handed in, so the clearance walk and the price walk are told
     *  apart rather than inferred from the gap between two stage lines. */
    private fun chordDecision(
        pathPriceSec: DoubleArray?,
        path: List<LatLng>,
        anchor: Int,
        probe: Int,
        marginM: Double,
        coarseStepM: Double,
        priceStepM: Double,
        field: RouteCostField,
        start: LatLng,
        aim: LatLng,
        approaches: EndApproaches,
        timing: PullTiming?,
        memo: MarkMemo?
    ): ChordDecision {
        val a = path[anchor]
        val b = path[probe]
        val clearanceStartNs = System.nanoTime()
        val cause = legClearCause(a, b, marginM, coarseStepM, field, start, aim, approaches, memo)
        timing?.addClearance(System.nanoTime() - clearanceStartNs)
        if (cause == null) {
            val priceStartNs = System.nanoTime()
            val refusal = priceRefusal(pathPriceSec, path, anchor, probe, marginM, priceStepM, field, timing, memo)
            timing?.addPrice(System.nanoTime() - priceStartNs)
            return ChordDecision(refusal)
        }
        return ChordDecision(cause)
    }

    /** One evaluation's own verdict: the refusal, or `null` where the chord stands. */
    private data class ChordDecision(val refusal: ChordRefusal?)

    /** The wall walk's own sampling step for [marginM], floored — the density and its floor, one home. */
    internal fun clearanceStep(marginM: Double): Double = (marginM / 2.0).coerceAtLeast(MIN_SAMPLE_STEP_M)

    /**
     * Whether the margin is **waived** at [p]: within a disc of [marginM] around either raw end, or on
     * either carved approach. It is the pull's own exemption, read here and by the curve fitter's wall
     * test from one home, so the two can never disagree about where the shore clearance binds.
     */
    internal fun marginWaived(
        p: LatLng,
        marginM: Double,
        start: LatLng,
        aim: LatLng,
        approaches: EndApproaches,
        toleranceM: Double
    ): Boolean =
        SpatialOperations.haversine(p, start) < marginM ||
            SpatialOperations.haversine(p, aim) < marginM ||
            approachCovers(p, approaches.start, toleranceM) ||
            approachCovers(p, approaches.aim, toleranceM)

    /**
     * Whether [p] stands on one of the approach's own segments — within [toleranceM], the walk's own
     * sampling step, so a sample that cannot be told apart from the stretch reads as on it.
     */
    internal fun approachCovers(p: LatLng, approach: List<LatLng>, toleranceM: Double): Boolean {
        if (approach.size < 2) return false
        for (i in 0 until approach.size - 1) {
            if (SpatialOperations.pointToSegmentDistance(p, approach[i], approach[i + 1]) <= toleranceM) {
                return true
            }
        }
        return false
    }

    internal fun legClear(
        a: LatLng,
        b: LatLng,
        marginM: Double,
        coarseStepM: Double,
        field: RouteCostField,
        start: LatLng,
        aim: LatLng,
        approaches: EndApproaches = EndApproaches.NONE
    ): Boolean =
        legClearCause(a, b, marginM, coarseStepM, field, start, aim, approaches) == null

    /**
     * The same walk [legClear] answers with a boolean, read for its **cause** — so the instrument can
     * count which of the pull's tests refused a chord instead of leaving the three to guesswork.
     *
     * **The exemption is one rule with two halves**, and it is the margin's own scope: the shore
     * clearance binds the **route**, never the ends. A sample standing within [marginM] of either end
     * is skipped, and so is one standing on that end's **carved approach** — the stretch the berth
     * carve opened, which the disc cannot cover once the berth runs deeper than [marginM]. Without
     * that second half the pull re-closes the very channel it just used, and the drawn line becomes a
     * staircase inside the berth it escaped.
     *
     * @param coarseStepM the coarse sampling step — the walk's own cell on the water it was resolved on.
     *   The coarse marks stand on a **fixed lattice from the chord's own start**, one per step with the
     *   last interval sized to the chord's remainder, each at its own interval's midpoint; each pays the
     *   distance read, and a fine mark pays it only where the coarse mark covering it read under
     *   `marginM + coarseStepM / 2`, so a step at or under the fine one spares nothing at all.
     */
    internal fun legClearCause(
        a: LatLng,
        b: LatLng,
        marginM: Double,
        coarseStepM: Double,
        field: RouteCostField,
        start: LatLng,
        aim: LatLng,
        approaches: EndApproaches = EndApproaches.NONE,
        memo: MarkMemo? = null
    ): ChordRefusal? {
        val dist = SpatialOperations.haversine(a, b)
        val sampleStep = clearanceStep(marginM)
        val steps = ceil(dist / sampleStep).toInt().coerceAtLeast(2)
        // The coarse marks, on a **fixed lattice from the chord's own start**: one per coarse step, its
        // last interval sized to the chord's remainder, each standing at its interval's own midpoint —
        // so every point of the chord, its two ends included, is within half a coarse step of one, no
        // boundary lattice can test the chord's own ends, and the mark set is the same for every attempt
        // that shares an anchor. The step is floored at the fine one — a finer "coarse" pass could only
        // pay more reads than it saves, and a degenerate zero would never terminate.
        val coarse = MarkLattice(dist, coarseStepM.coerceAtLeast(sampleStep))
        val coarseStep = coarse.stepM
        val coarseCount = coarse.count
        // The trigger the 1-Lipschitz bound gives: a reading `d` proves every point within half a
        // coarse step stands at least `d − coarseStep / 2` off the wall, so a mark at or above it
        // proves its own half-step clear — and the fine mark it covers along with it.
        val provedClearM = marginM + coarseStep / 2.0
        val coarseClear = BooleanArray(coarseCount)
        for (k in 0 until coarseCount) {
            coarseClear[k] = readHard(memo, field, chordPoint(a, b, coarse.markT(k))) >= provedClearM
        }
        for (i in 1 until steps) {
            val t = i.toDouble() / steps
            val p = chordPoint(a, b, t)
            // The end-disc and the carved approach, one rule: the margin binds the path, never the ends.
            if (marginWaived(p, marginM, start, aim, approaches, sampleStep)) continue
            // The rastered walls are a **step**, so every mark tests them: no bound could prove a
            // half-step holds no shallow cell, and a coarse-only walk would sail one.
            if (field.hardBlocked(p)) return ChordRefusal.LAND
            // The materialized distance is the one read the proof may spare, and it is skipped exactly
            // where the coarse mark covering this fine mark has already answered for its half-step.
            val covering = coarse.indexOf(t)
            if (!coarseClear[covering] && readHard(memo, field, p) < marginM) return ChordRefusal.LAND
        }
        return null
    }

    /**
     * The point [t] of the way from [a] to [b] — the chord's own parameterisation, one home for the
     * coarse marks, the fine ones and the price walk's interval midpoints, so no two of them can be
     * placed off the same line.
     */
    private fun chordPoint(a: LatLng, b: LatLng, t: Double): LatLng = LatLng(
        a.latitude + (b.latitude - a.latitude) * t,
        a.longitude + (b.longitude - a.longitude) * t
    )

    /**
     * The price guard's own cause: `PRICE` where the chord costs more than the span it would replace.
     * Both sides are read on the **same step and the same grouping rule** — the prefix's per-segment
     * walks and the chord's own one-line walk obey one law — so the guard compares like with like. They
     * are not one partition: a segment is anchored at its own start and the chord at its own, so the two
     * coincide mark for mark on a straight run and diverge where the path bends.
     */
    private fun priceRefusal(
        pathPriceSec: DoubleArray?,
        path: List<LatLng>,
        anchor: Int,
        probe: Int,
        marginM: Double,
        priceStepM: Double,
        field: RouteCostField,
        timing: PullTiming?,
        memo: MarkMemo?
    ): ChordRefusal? {
        val replacedPriceSec = pathPriceSec?.let { it[probe] - it[anchor] } ?: return null
        return if (softPriceSec(path[anchor], path[probe], marginM, priceStepM, field, timing, memo) > replacedPriceSec) {
            ChordRefusal.PRICE
        } else {
            null
        }
    }

    /**
     * The field's prices summed along one straight segment, in **seconds** — the price is the written
     * law's own quantity, a cell's excess in seconds multiplied by the interval's **metres**, read at
     * the **midpoint of each interval**. The intervals are a [MarkLattice] on the walk's own sampling
     * step `clearanceStep(marginM)`, its last interval sized to the chord's remainder, so the whole
     * segment is covered exactly and the midpoints — and every group's own midpoint — stand at the same
     * points for every attempt that shares an anchor. That step is the one home the clearance walk
     * shares, so the two walks can never disagree about the fine grid's own length; the price step below
     * coarsens the **grouping** alone and never the interval the sum is taken over.
     *
     * **The mark set does not change; which marks pay a read of their own does.** The walk is read as a
     * recursion over the chord's own fine intervals, from the whole chord down: one clearance read at a
     * span's midpoint proves the span where it is at least the span's half-length — no boundary of any
     * soft source can then lie inside, so the arm is constant and the span's product is **identically**
     * the fine sum it replaces, the same value added the same number of times in the same order, not a
     * near miss — an unproved span splits in half and each half is tested in turn, and a span at or
     * under the **floor** falls to the group path below. So the walk settles on the largest provable
     * spans and keeps the group and fine marks only where a boundary actually lives.
     *
     * The proof's own read is not a price read and is never counted in [PullTiming.priceReads]: a proved
     * span pays one price read whatever its length, beside the clearance read that proved it. Where the
     * walk cannot group at all — a price step at or under the fine one — the span proof is skipped and
     * the walk reads exactly as today, so only a coarse pass changes.
     *
     * The midpoint is what makes two segments comparable: an end-excluding walk discounts a short
     * segment by half a sample and a long one by almost nothing, so a chord would have looked dearer
     * than the cell path it replaces and no line would ever be pulled taut. A field with no price
     * reads 0 and the guard is inert.
     *
     * @param priceStepM the price walk's own step — a required parameter, the walk's interior cell and
     *   never its fine one: a defaulted step would re-arm the collapse this step exists to close.
     */
    internal fun softPriceSec(
        a: LatLng,
        b: LatLng,
        marginM: Double,
        priceStepM: Double,
        field: RouteCostField,
        timing: PullTiming? = null,
        memo: MarkMemo? = null
    ): Double {
        val dist = SpatialOperations.haversine(a, b)
        val sampleStep = clearanceStep(marginM)
        // The interval partition is a [MarkLattice] on the walk's own sampling step, its last interval
        // sized to the chord's remainder — so the midpoints, and every group's own midpoint, stand at
        // the same points whatever the chord's length, which is what a memo of the reads needs to hit.
        val marks = MarkLattice(dist, sampleStep)
        val steps = marks.count
        val stepM = marks.stepM
        // The price step is floored at the fine one, exactly as the clearance walk floors its own, so
        // a coarse pass can only ever save reads.
        val priceStep = priceStepM.coerceAtLeast(sampleStep)
        val groupStep = maxOf(1, floor(priceStep / stepM).toInt())
        // A walk whose step cannot group reads as today: the span proof exists to spare the reads a
        // group pays, so a step at or under the fine one leaves the fine midpoints alone.
        if (groupStep <= 1) {
            val acc = doubleArrayOf(0.0)
            groupPriceSec(a, b, marks, 0, steps, marginM, priceStep, field, timing, memo, acc)
            return acc[0]
        }
        // The recursion's floor: twice the price step, the length the group walk prices in two reads,
        // so a span at or under it goes straight to the group path and a short chord never pays more.
        val floorCount = maxOf(groupStep, floor(2.0 * priceStep / stepM).toInt())
        val acc = doubleArrayOf(0.0)
        spanPriceSec(a, b, marks, 0, steps, marginM, priceStep, floorCount, field, timing, memo, acc)
        return acc[0]
    }

    /**
     * [softPriceSec] read as a recursion over the chord's own intervals `[i0, i1)`. A span at or under
     * [floorCount] intervals is the group walk's own; a longer span is tested by one clearance read at
     * its midpoint and, proved, priced from one price read there. An unproved span splits in half and
     * each half is tested in turn, so the walk finds the largest provable spans.
     *
     * **The sum is one accumulator, visited left to right**, never a tree of `left + right` pairs: a
     * proved span adds its reading once per interval in the same places the fine walk would, so the
     * total is bit-identical to the fine sum — the same double, not a near miss — which a floating
     * point re-association across a split would break.
     */
    private fun spanPriceSec(
        a: LatLng,
        b: LatLng,
        marks: MarkLattice,
        i0: Int,
        i1: Int,
        marginM: Double,
        priceStepM: Double,
        floorCount: Int,
        field: RouteCostField,
        timing: PullTiming?,
        memo: MarkMemo?,
        acc: DoubleArray
    ) {
        val count = i1 - i0
        if (count <= floorCount) {
            groupPriceSec(a, b, marks, i0, i1, marginM, priceStepM, field, timing, memo, acc)
            return
        }
        val halfM = marks.spanLengthM(i0, i1) / 2.0
        val mid = chordPoint(a, b, marks.spanMidT(i0, i1))
        val clearance = field.priceClearanceM(mid)
        if (clearance < Double.MAX_VALUE && clearance >= halfM) {
            val price = field.softPriceSecAt(mid)
            timing?.addPriceReads(1)
            for (j in i0 until i1) acc[0] += price * marks.widthM(j)
            return
        }
        val midIndex = i0 + count / 2
        spanPriceSec(a, b, marks, i0, midIndex, marginM, priceStepM, floorCount, field, timing, memo, acc)
        spanPriceSec(a, b, marks, midIndex, i1, marginM, priceStepM, floorCount, field, timing, memo, acc)
    }

    /**
     * The group walk over the interval run `[i0, i1)`, adding into [acc]: successive intervals of
     * [priceStepM] worth are grouped, a group is priced from **one** read where the field's declaration
     * proves no boundary stands inside it, and every unproved group keeps the fine midpoints, at their
     * lattice positions, in order. A group of one interval is its own fine interval and is never asked
     * for a proof, so a walk handed the fine step reads exactly as today.
     */
    private fun groupPriceSec(
        a: LatLng,
        b: LatLng,
        marks: MarkLattice,
        i0: Int,
        i1: Int,
        marginM: Double,
        priceStepM: Double,
        field: RouteCostField,
        timing: PullTiming?,
        memo: MarkMemo?,
        acc: DoubleArray
    ) {
        val groupStep =
            maxOf(1, floor(priceStepM.coerceAtLeast(clearanceStep(marginM)) / marks.stepM).toInt())
        var i = i0
        while (i < i1) {
            val k = minOf(groupStep, i1 - i)
            val halfM = marks.spanLengthM(i, i + k) / 2.0
            val proved = if (k > 1) {
                // `MAX_VALUE` is the declaration's own "no boundary named": a source that names none
                // can never be proved, and a group of one interval is never asked.
                val clearance = field.priceClearanceM(chordPoint(a, b, marks.spanMidT(i, i + k)))
                clearance < Double.MAX_VALUE && clearance >= halfM
            } else {
                false
            }
            if (proved) {
                val price = readPrice(memo, field, chordPoint(a, b, marks.spanMidT(i, i + k)), timing)
                for (j in i until i + k) acc[0] += price * marks.widthM(j)
            } else {
                for (j in i until i + k) {
                    acc[0] += readPrice(memo, field, chordPoint(a, b, marks.markT(j)), timing) * marks.widthM(j)
                }
            }
            i += k
        }
    }

    /** [softPriceSec] accumulated along [path], so one span's price is a single subtraction. */
    internal fun softPricePrefix(
        path: List<LatLng>,
        marginM: Double,
        priceStepM: Double,
        field: RouteCostField,
        timing: PullTiming? = null,
        memo: MarkMemo? = null
    ): DoubleArray {
        val prefix = DoubleArray(path.size)
        for (i in 1 until path.size) {
            prefix[i] = prefix[i - 1] + softPriceSec(path[i - 1], path[i], marginM, priceStepM, field, timing, memo)
        }
        return prefix
    }

    /**
     * The materialized walls' distance at [p], from the walk's memo where it holds the point and from
     * the field otherwise — one home for the read the clearance walk pays, so the memo is consulted at
     * every mark and no caller can forget it.
     */
    private fun readHard(memo: MarkMemo?, field: RouteCostField, p: LatLng): Double =
        memo?.hardDistance(field, p) ?: field.hardDistanceM(p)

    /**
     * The soft price at [p], from the walk's memo where it holds the point and from the field otherwise —
     * and it is the **one** home that decides whether a price read was paid, incrementing
     * [PullTiming.priceReads] on a miss alone, so a memo hit lowers the count without touching the sum.
     */
    private fun readPrice(memo: MarkMemo?, field: RouteCostField, p: LatLng, timing: PullTiming?): Double {
        val fresh = memo == null || !memo.hasPrice(field, p)
        val price = memo?.price(field, p) ?: field.softPriceSecAt(p)
        if (fresh) timing?.addPriceReads(1)
        return price
    }
}

/**
 * **The two ends' carved approaches, as the pull reads them** — the stretch each end may travel on
 * beside the margin-radius disc around it. One value rather than two lists, because the exemption is
 * one rule read twice: a berth channel longer than the margin is not covered by the disc, so a chord
 * standing on the stretch must be exempt exactly as a sample within the disc already is.
 *
 * [NONE] is what a solve without a carve passes — no stretch exempt, which is the shipped behaviour.
 */
data class EndApproaches(
    val start: List<LatLng> = emptyList(),
    val aim: List<LatLng> = emptyList()
) {
    companion object {
        val NONE = EndApproaches()
    }
}

/** The pull's own causes of refusal: the land margin and the price guard. */
internal enum class ChordRefusal { LAND, PRICE }

/**
 * **Which of the pull's two refusals fired, counted per answer** — the land margin and the price — so
 * a line that collapses nothing names the test that refused it instead of leaving candidates to
 * guesswork.
 *
 * Counters are updated where [MultipassPull.pull] decides and read once per answer: nothing is emitted per
 * chord, nothing is measured twice, and a chord re-walked from a new anchor counts once per evaluation.
 */
class PullRefusals {

    /** Chords refused because a sample stood nearer than `marginM` to a hard wall. */
    var land = 0
        private set

    /** Chords refused because their own price exceeded the cell path's over the span replaced. */
    var price = 0
        private set

    internal fun record(cause: ChordRefusal) {
        when (cause) {
            ChordRefusal.LAND -> land++
            ChordRefusal.PRICE -> price++
        }
    }
}

/**
 * **What the walk spent, and where** — the clearance walk and the price walk, in nanoseconds, told
 * apart because the two answer different questions: the clearance half is the live coastline read a
 * sampled walk exists to cut down, and the price half is the priced water's own read, which no
 * clearance proof covers — so a win must be read on the first and never on their sum.
 *
 * Handed in where a reader wants it and left `null` where none does, exactly like [PullRefusals]: the
 * walk's verdicts do not depend on carrying one, and nothing is emitted per chord.
 */
class PullTiming {

    /** Nanoseconds spent in the clearance walk — the marks' own hard readings. */
    var clearanceNanos: Long = 0L
        private set

    /** Nanoseconds spent in the price walk — the prefix sum and each chord's own price. */
    var priceNanos: Long = 0L
        private set

    /**
     * The price reads the price walk actually paid — one per proved group and one per fine midpoint a
     * refused group keeps. Beside [priceMs] it is how the coarsening's saving is read rather than
     * inferred from the clock.
     */
    var priceReads: Long = 0L
        private set

    /** [clearanceNanos] in the log's own unit. */
    val clearanceMs: Double get() = clearanceNanos / NANOS_PER_MS

    /** [priceNanos] in the log's own unit. */
    val priceMs: Double get() = priceNanos / NANOS_PER_MS

    internal fun addClearance(nanos: Long) {
        clearanceNanos += nanos
    }

    internal fun addPrice(nanos: Long) {
        priceNanos += nanos
    }

    internal fun addPriceReads(count: Int) {
        priceReads += count
    }
}
