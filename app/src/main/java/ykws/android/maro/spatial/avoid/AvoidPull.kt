package ykws.android.maro.spatial.avoid

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
object AvoidPull {

    /**
     * Pulls [path] (raw start first, raw aim last) taut into the ordered waypoint list, as direct as
     * the field's margin allows and independent of grid orientation.
     *
     * @param field the unified cost field — its nearest hard wall is the clearance read here.
     * @param approaches the two ends' carved approaches, whose stretches the margin does not bind.
     * @param refusals the tally the walk counts its refused chords into, or `null` where none is read.
     */
    fun pull(
        path: List<LatLng>,
        start: LatLng,
        aim: LatLng,
        marginM: Double,
        field: RouteCostField,
        approaches: EndApproaches = EndApproaches.NONE,
        refusals: PullRefusals? = null
    ): List<LatLng> {
        if (path.size <= 2) return path
        val result = ArrayList<LatLng>(path.size)
        result.add(path.first())
        val pathPriceSec = if (field.hasSoft) softPricePrefix(path, marginM, field) else null
        var anchor = 0
        var probe = 1
        while (probe < path.size) {
            val decision = chordDecision(
                pathPriceSec, path, anchor, probe, marginM, field, start, aim, approaches
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
     *  short circuit keeps a refused chord from costing a walk of its own price. */
    private fun chordDecision(
        pathPriceSec: DoubleArray?,
        path: List<LatLng>,
        anchor: Int,
        probe: Int,
        marginM: Double,
        field: RouteCostField,
        start: LatLng,
        aim: LatLng,
        approaches: EndApproaches
    ): ChordDecision {
        val a = path[anchor]
        val b = path[probe]
        val cause = legClearCause(a, b, marginM, field, start, aim, approaches)
        if (cause == null) {
            return ChordDecision(priceRefusal(pathPriceSec, path, anchor, probe, marginM, field))
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
        field: RouteCostField,
        start: LatLng,
        aim: LatLng,
        approaches: EndApproaches = EndApproaches.NONE
    ): Boolean =
        legClearCause(a, b, marginM, field, start, aim, approaches) == null

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
     */
    internal fun legClearCause(
        a: LatLng,
        b: LatLng,
        marginM: Double,
        field: RouteCostField,
        start: LatLng,
        aim: LatLng,
        approaches: EndApproaches = EndApproaches.NONE
    ): ChordRefusal? {
        val dist = SpatialOperations.haversine(a, b)
        val sampleStep = clearanceStep(marginM)
        val steps = ceil(dist / sampleStep).toInt().coerceAtLeast(2)
        for (i in 1 until steps) {
            val t = i.toDouble() / steps
            val p = LatLng(
                a.latitude + (b.latitude - a.latitude) * t,
                a.longitude + (b.longitude - a.longitude) * t
            )
            // The end-disc and the carved approach, one rule: the margin binds the path, never the ends.
            if (marginWaived(p, marginM, start, aim, approaches, sampleStep)) continue
            if (field.hardDistanceM(p) < marginM) return ChordRefusal.LAND
        }
        return null
    }

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
            val t = (i + 0.5) / steps
            val p = LatLng(
                a.latitude + (b.latitude - a.latitude) * t,
                a.longitude + (b.longitude - a.longitude) * t
            )
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
 * Counters are updated where [AvoidPull.pull] decides and read once per answer: nothing is emitted per
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
