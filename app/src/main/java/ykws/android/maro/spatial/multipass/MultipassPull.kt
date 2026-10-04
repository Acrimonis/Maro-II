package ykws.android.maro.spatial.multipass

import ykws.android.maro.data.model.LatLng
import ykws.android.maro.spatial.SpatialOperations
import kotlin.math.ceil

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
 */
object MultipassPull {

    /**
     * Pulls [path] (raw start first, raw aim last) taut into the ordered waypoint list, as direct as
     * the field's margin allows and independent of grid orientation.
     *
     * @param coarseStepM the coarse sampling step — the walk's own cell on the water it was resolved
     *   on, handed in by the caller and never defaulted. Every caller hands the fine step until the
     *   sampling phase lands, so the marks and the reads are exactly the ones a fine walk makes.
     * @param field the unified cost field — its materialized walls' distance is the clearance read
     *   here, and its rastered walls' step is tested at every mark beside it.
     * @param approaches the two ends' carved approaches, whose stretches the margin does not bind.
     * @param refusals the tally the walk counts its refused chords into, or `null` where none is read.
     * @param timing the walk's own tally of what it spent, or `null` where no reader wants it.
     */
    fun pull(
        path: List<LatLng>,
        start: LatLng,
        aim: LatLng,
        marginM: Double,
        coarseStepM: Double,
        field: RouteCostField,
        approaches: EndApproaches = EndApproaches.NONE,
        refusals: PullRefusals? = null,
        timing: PullTiming? = null
    ): List<LatLng> {
        if (path.size <= 2) return path
        val result = ArrayList<LatLng>(path.size)
        result.add(path.first())
        val prefixStartNs = System.nanoTime()
        val pathPriceSec = if (field.hasSoft) softPricePrefix(path, marginM, field) else null
        timing?.addPrice(System.nanoTime() - prefixStartNs)
        var anchor = 0
        var probe = 1
        while (probe < path.size) {
            val decision = chordDecision(
                pathPriceSec, path, anchor, probe, marginM, coarseStepM, field, start, aim, approaches, timing
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
        field: RouteCostField,
        start: LatLng,
        aim: LatLng,
        approaches: EndApproaches,
        timing: PullTiming?
    ): ChordDecision {
        val a = path[anchor]
        val b = path[probe]
        val clearanceStartNs = System.nanoTime()
        val cause = legClearCause(a, b, marginM, coarseStepM, field, start, aim, approaches)
        timing?.addClearance(System.nanoTime() - clearanceStartNs)
        if (cause == null) {
            val priceStartNs = System.nanoTime()
            val refusal = priceRefusal(pathPriceSec, path, anchor, probe, marginM, field)
            timing?.addPrice(System.nanoTime() - priceStartNs)
            return ChordDecision(refusal)
        }
        return ChordDecision(cause)
    }

    /** One evaluation's own verdict: the refusal, or `null` where the chord stands. */
    private data class ChordDecision(val refusal: ChordRefusal?)

    /**
     * The shortest sampling step the clearance walk will take, in metres — the floor that keeps a
     * degenerate margin from turning the walk into a two-billion-iteration loop. A step is a property of
     * the walk, so no caller's margin may drive it to zero.
     */
    private const val MIN_SAMPLE_STEP_M = 1.0

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
     *   The coarse marks stand one per step, at the midpoints of its equal divisions, and each pays the
     *   distance read; a fine mark pays it only where the coarse mark covering it read under
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
        approaches: EndApproaches = EndApproaches.NONE
    ): ChordRefusal? {
        val dist = SpatialOperations.haversine(a, b)
        val sampleStep = clearanceStep(marginM)
        val steps = ceil(dist / sampleStep).toInt().coerceAtLeast(2)
        // The coarse marks, one per step along the chord, at the **midpoints** of the equal divisions:
        // so every point of the chord, its two ends included, stands within half a coarse step of one,
        // and no boundary lattice can test the chord's own ends. The step is floored at the fine one —
        // a finer "coarse" pass could only pay more reads than it saves, and a degenerate zero would
        // never terminate.
        val coarseStep = coarseStepM.coerceAtLeast(sampleStep)
        val coarseCount = ceil(dist / coarseStep).toInt().coerceAtLeast(1)
        // The trigger the 1-Lipschitz bound gives: a reading `d` proves every point within half a
        // coarse step stands at least `d − coarseStep / 2` off the wall, so a mark at or above it
        // proves its own half-step clear — and the fine mark it covers along with it.
        val provedClearM = marginM + coarseStep / 2.0
        val coarseClear = BooleanArray(coarseCount)
        for (k in 0 until coarseCount) {
            val t = (k + 0.5) / coarseCount
            coarseClear[k] = field.hardDistanceM(chordPoint(a, b, t)) >= provedClearM
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
            val covering = minOf((t * coarseCount).toInt(), coarseCount - 1)
            if (!coarseClear[covering] && field.hardDistanceM(p) < marginM) return ChordRefusal.LAND
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

    /** The price guard's own cause: `PRICE` where the chord costs more than the span it would replace. */
    private fun priceRefusal(
        pathPriceSec: DoubleArray?,
        path: List<LatLng>,
        anchor: Int,
        probe: Int,
        marginM: Double,
        field: RouteCostField
    ): ChordRefusal? {
        val replacedPriceSec = pathPriceSec?.let { it[probe] - it[anchor] } ?: return null
        return if (softPriceSec(path[anchor], path[probe], marginM, field) > replacedPriceSec) {
            ChordRefusal.PRICE
        } else {
            null
        }
    }

    /**
     * The field's prices summed along one straight segment, in **seconds** — the seconds a metre of
     * the price costs, times the metres — read at the **midpoint of each interval** so the whole
     * segment is covered, at `≤ marginM / 2` intervals so a price narrower than the step cannot slip
     * between two readings.
     *
     * The midpoint is what makes two segments comparable: an end-excluding walk discounts a short
     * segment by half a sample and a long one by almost nothing, so a chord would have looked dearer
     * than the cell path it replaces and no line would ever be pulled taut. A field with no price
     * reads 0 and the guard is inert.
     */
    internal fun softPriceSec(a: LatLng, b: LatLng, marginM: Double, field: RouteCostField): Double {
        val dist = SpatialOperations.haversine(a, b)
        val sampleStep = marginM / 2.0
        val steps = ceil(dist / sampleStep).toInt().coerceAtLeast(1)
        val stepM = dist / steps
        var sum = 0.0
        for (i in 0 until steps) {
            val p = chordPoint(a, b, (i + 0.5) / steps)
            sum += field.evaluate(p).softCostSec * stepM
        }
        return sum
    }

    /** [softPriceSec] accumulated along [path], so one span's price is a single subtraction. */
    private fun softPricePrefix(
        path: List<LatLng>,
        marginM: Double,
        field: RouteCostField
    ): DoubleArray {
        val prefix = DoubleArray(path.size)
        for (i in 1 until path.size) {
            prefix[i] = prefix[i - 1] + softPriceSec(path[i - 1], path[i], marginM, field)
        }
        return prefix
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
}
