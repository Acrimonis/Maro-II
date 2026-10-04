package ykws.android.maro.spatial.multipass

import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.spatial.SpatialOperations
import ykws.android.maro.spatial.Units
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The route's clock, in two strengths, both λ-free:
 *
 * [timeLineWithLimits] is the **enforced per-point ETA** — the line re-vertexed where the limit in
 * force changes and timed leg by leg at that limit, capped by the pace. No ramp, no anticipation, no
 * corner-speed cap; a leg's made-good speed **is** the enforced limit. The λ loop and the fine-splice
 * comparison read this one.
 *
 * [timeLineWithProfile] is the **smooth profile** the final answer carries — the same enforced limit as
 * a hard ceiling, with the corner pass's curvature ceiling beside it and a comfortable acceleration
 * ramp ([AppConfig.routeSpeedAccelMps2]) through every transition: the boat eases down **before** a
 * limit drop and climbs back **after** a rise, never exceeding the limit in force.
 *
 * Inside a zone or the 300 m band the boat obeys the strictest limit (never above the configured pace);
 * outside it rides the pace. This file is a **clock**, never a price: nothing here reads λ or the A\*'s
 * own cost.
 */

/**
 * The shortest step (m) the boundary splitter will walk a leg at, whatever a caller asks for — the floor
 * that keeps a degenerate ask from turning the walk into a loop.
 *
 * The step itself is the **caller's**, because only the caller knows the cell its engine walks, and
 * `sampleM` is required everywhere rather than defaulted: a constant here is exactly how a leg came to
 * carry a limit that was not its own the day the grid cell changed.
 */
private const val MIN_BOUNDARY_SAMPLE_M = 1.0

/**
 * **The clock's own sampling step (m)** for an engine whose coarse cell is [cellM] and whose second pass
 * runs at [fineRatio] of it — half the finest cell the engine walks, so the boundary splitter can see
 * every limit regime the grid itself can produce, and so the step can never fall out of step with the
 * grid the day the cell moves. Floored at [MIN_BOUNDARY_SAMPLE_M], which is the splitter's own floor.
 */
internal fun clockSampleM(cellM: Double, fineRatio: Double): Double =
    (min(cellM, cellM * fineRatio) / 2.0).coerceAtLeast(MIN_BOUNDARY_SAMPLE_M)

/** A polyline split at limit changes, with one planned time and one made-good speed per split leg. */
data class TimedLine(
    val points: List<LatLng>,
    val legTimesSec: List<Double>,
    /**
     * The **speed made good** over each leg, in m/s — the enforced limit in force at the leg's
     * midpoint, capped by the pace, so the figure always equals the clock beside it.
     */
    val legSpeedsMps: List<Double> = emptyList()
) {
    val durationSec: Double get() = legTimesSec.sum()
}

/**
 * Splits [waypoints] where [limitKnAt] changes and times each split leg at the limit in force at its
 * midpoint, capped by the pace — no ramp, no corner-speed caps. A leg's made-good speed is therefore
 * exactly the enforced limit, and the reported time is `distance / speed`.
 *
 * **[sampleM] is the step the splitter walks each leg at, and the caller owns it.** The legs come out
 * **regime-pure** — each stands inside one limit, so its midpoint read *is* the limit in force there —
 * and a regime narrower than the step can still hide between two samples, which is why an engine passes
 * half the finest cell it walks rather than a constant.
 */
fun timeLineWithLimits(
    waypoints: List<LatLng>,
    paceKn: Double,
    limitKnAt: (LatLng) -> Double?,
    sampleM: Double
): TimedLine {
    if (waypoints.size < 2) return TimedLine(waypoints, emptyList())
    val paceMps = Units.knotsToMps(paceKn)
    val points = splitAtLimitChanges(waypoints, limitKnAt, sampleM)
    val legs = points.size - 1
    val times = ArrayList<Double>(legs)
    val speeds = ArrayList<Double>(legs)
    for (i in 0 until legs) {
        val limitKn = readLimit(limitKnAt, midpoint(points[i], points[i + 1]))
        val speedMps = if (limitKn != null) min(Units.knotsToMps(limitKn), paceMps) else paceMps
        val dist = SpatialOperations.haversine(points[i], points[i + 1])
        times.add(dist / speedMps)
        speeds.add(speedMps)
    }
    return TimedLine(points, times, speeds)
}

/**
 * One sample's own read — [limitKnAt] at [p], with a **non-finite** answer read as no limit. One home for
 * that rule, so **every** reader agrees about what a sample says: the splitter's walk, its bisect, the
 * enforced clock's leg speed and the profile's two ceilings. Without it one NaN would make every sample
 * look like a change while the leg the clock times off the same sample came out with a NaN duration.
 */
private fun readLimit(limitKnAt: (LatLng) -> Double?, p: LatLng): Double? =
    limitKnAt(p)?.takeIf { it.isFinite() }

/** Walks every leg and inserts the boundary vertices where [limitKnAt] changes, at [sampleM]. */
private fun splitAtLimitChanges(
    waypoints: List<LatLng>,
    limitKnAt: (LatLng) -> Double?,
    sampleM: Double
): List<LatLng> {
    val out = ArrayList<LatLng>(waypoints.size + 8)
    out.add(waypoints.first())
    for (i in 0 until waypoints.size - 1) {
        val sub = splitLeg(waypoints[i], waypoints[i + 1], limitKnAt, sampleM)
        for (j in 1 until sub.size) out.add(sub[j])
    }
    return out
}

/** Splits one leg into `[a, ...crossings..., b]` by sampling at [sampleM] then bisecting each change. */
private fun splitLeg(
    a: LatLng,
    b: LatLng,
    limitKnAt: (LatLng) -> Double?,
    sampleM: Double
): List<LatLng> {
    val dist = SpatialOperations.haversine(a, b)
    if (dist <= 1e-9) return listOf(a, b)
    val step = sampleM.coerceAtLeast(MIN_BOUNDARY_SAMPLE_M)
    val out = ArrayList<LatLng>(4)
    out.add(a)
    var prevT = 0.0
    var prevLimit = readLimit(limitKnAt, a)
    val steps = ceil(dist / step).toInt().coerceAtLeast(2)
    for (s in 1..steps) {
        val t = s.toDouble() / steps
        val p = interpolate(a, b, t)
        val limit = readLimit(limitKnAt, p)
        if (limit != prevLimit) {
            val crossing = bisectLimitChange(a, b, prevT, t, prevLimit, limit, limitKnAt)
            if (out.last() != crossing) out.add(crossing)
            prevLimit = limit
        }
        prevT = t
    }
    if (out.last() != b) out.add(b)
    return out
}

/** Bisects the limit change between two samples to a vertex standing on the boundary. */
private fun bisectLimitChange(
    a: LatLng,
    b: LatLng,
    lo: Double,
    hi: Double,
    loLimit: Double?,
    hiLimit: Double?,
    limitKnAt: (LatLng) -> Double?
): LatLng {
    var l = lo
    var h = hi
    repeat(24) {
        val mid = (l + h) / 2.0
        val limit = readLimit(limitKnAt, interpolate(a, b, mid))
        if (limit == loLimit) l = mid else h = mid
    }
    return interpolate(a, b, (l + h) / 2.0)
}

/** A line's slow time split by what slowed it — a ring's interior, the band's width, and the ramps. */
data class SlowShares(val zone: Double, val band: Double, val ramp: Double)

/**
 * **The share of a route's own time it spends slowed, every source together** — the seconds its legs
 * take beyond what the same distance costs at [paceKn], summed, over the line's whole time.
 *
 * The read is taken off the **timed legs** and never off the rings, which is what makes a leg on the
 * way into a zone count too: any metre run below the pace is slow water, whether it lies inside a ring
 * or on the approach to one. It is the **total**; [slowShares] splits it by the water that slowed the
 * line, which is the reading the zone budget is keyed on.
 */
fun zoneSlowShare(timed: TimedLine, paceKn: Double): Double {
    val total = timed.durationSec
    if (total <= 0.0 || timed.legTimesSec.isEmpty()) return 0.0
    val paceMps = Units.knotsToMps(paceKn)
    var slow = 0.0
    for (i in 0 until timed.legTimesSec.size) {
        val dist = SpatialOperations.haversine(timed.points[i], timed.points[i + 1])
        val excess = timed.legTimesSec[i] - dist / paceMps
        if (excess > 0.0) slow += excess
    }
    return (slow / total).coerceIn(0.0, 1.0)
}

/**
 * **The shares of a route's own time it spends slowed, split by what slowed it** — a ring's interior
 * ([SlowShares.zone]), the 300 m band's own width ([SlowShares.band]), and the approach and exit ramps
 * standing outside both ([SlowShares.ramp]).
 *
 * The walk is the one [zoneSlowShare] makes, per timed leg: a leg's seconds beyond its own distance at
 * [paceKn] are charged to the water standing at the leg's **midpoint** — [inZone] or [inBand], the
 * caller's own reads of the ring interior and the band's width — and every other slow leg is a ramp.
 * The band's own slow time therefore no longer drives the zone budget, and a ramp is told apart from
 * the slow water it leads into.
 */
fun slowShares(
    timed: TimedLine,
    paceKn: Double,
    inZone: (LatLng) -> Boolean,
    inBand: (LatLng) -> Boolean
): SlowShares {
    val total = timed.durationSec
    if (total <= 0.0 || timed.legTimesSec.isEmpty()) return SlowShares(0.0, 0.0, 0.0)
    val paceMps = Units.knotsToMps(paceKn)
    var zone = 0.0
    var band = 0.0
    var ramp = 0.0
    for (i in 0 until timed.legTimesSec.size) {
        val dist = SpatialOperations.haversine(timed.points[i], timed.points[i + 1])
        val excess = timed.legTimesSec[i] - dist / paceMps
        if (excess <= 0.0) continue
        val mid = midpoint(timed.points[i], timed.points[i + 1])
        when {
            inZone(mid) -> zone += excess
            inBand(mid) -> band += excess
            else -> ramp += excess
        }
    }
    return SlowShares(
        (zone / total).coerceIn(0.0, 1.0),
        (band / total).coerceIn(0.0, 1.0),
        (ramp / total).coerceIn(0.0, 1.0)
    )
}

/** The band the budget loop stops inside: a share this close to the budget is left alone (±20 %). */
const val ZONE_BUDGET_BAND = 0.20

/**
 * True when [share] sits inside the budget's ±[ZONE_BUDGET_BAND] band — the loop's own **exit**.
 *
 * It is a predicate rather than a condition inside the loop so the exit can be read, and reverted,
 * on its own: outside the band the loop corrects λ once and solves again, and inside it it stops.
 */
fun withinBudgetBand(share: Double, budgetPct: Double): Boolean {
    val budget = budgetPct / 100.0
    return share in (budget * (1.0 - ZONE_BUDGET_BAND))..(budget * (1.0 + ZONE_BUDGET_BAND))
}

/**
 * True when the budget is **met** — [share] stands inside the band or **below** it, so no correction
 * is owed and the loop stops.
 *
 * The loop's own exit, and the reason it is not the band alone: the correction raises λ to buy slow
 * water *out* of a line, so a share under the budget — a route already spending less slow water than
 * it may — is a line to keep, never one to chase. Chasing it would lower λ until the only way to
 * spend more slow water is to cross a zone the search had rounded, which is the opposite of what the
 * route is for. So the loop corrects only above the band's top edge, and the band's own reading stays
 * [withinBudgetBand] — this predicate is that one widened by the under-budget case, its single
 * reader.
 */
fun budgetMet(share: Double, budgetPct: Double): Boolean =
    withinBudgetBand(share, budgetPct) || share < (budgetPct / 100.0) * (1.0 - ZONE_BUDGET_BAND)

/**
 * The **smooth speed profile** — [waypoints] re-vertexed at limit changes, then solved point by point
 * under two ceilings: the limit in force along each leg (constant, since a limit change is a boundary
 * vertex) and the corner pass's curvature ceiling at each vertex. A backward pass starts every
 * deceleration as early as comfort needs, so the boat reaches a lower limit **at** its boundary; a
 * forward pass bounds the acceleration after it. The limit is the hard rule: a vertex never exceeds
 * either leg it touches, and where the run-up is too short for a comfortable brake the profile brakes
 * harder rather than exceed the limit in force.
 */
fun timeLineWithProfile(
    waypoints: List<LatLng>,
    paceKn: Double,
    limitKnAt: (LatLng) -> Double?,
    ceilingKnAt: (LatLng) -> Double?,
    sampleM: Double,
    accelMps2: Double = AppConfig.routeSpeedAccelMps2
): TimedLine {
    if (waypoints.size < 2) return TimedLine(waypoints, emptyList())
    val paceMps = Units.knotsToMps(paceKn)
    val points = splitAtLimitChanges(waypoints, limitKnAt, sampleM)
    val n = points.size - 1
    fun limitMpsAt(p: LatLng): Double =
        readLimit(limitKnAt, p)?.let { min(Units.knotsToMps(it), paceMps) } ?: paceMps
    fun ceilingMpsAt(p: LatLng): Double =
        readLimit(ceilingKnAt, p)?.let { min(Units.knotsToMps(it), paceMps) } ?: paceMps
    // The limit in force along each leg — constant, because a limit change is a boundary vertex.
    val legLimit = DoubleArray(n) { i -> limitMpsAt(midpoint(points[i], points[i + 1])) }
    // A vertex's speed may not exceed either leg it touches, nor its own curvature ceiling.
    val cap = DoubleArray(n + 1) { j ->
        val base = when (j) {
            0 -> legLimit[0]
            n -> legLimit[n - 1]
            else -> min(legLimit[j - 1], legLimit[j])
        }
        min(base, ceilingMpsAt(points[j]))
    }
    // Backward: the latest-possible deceleration that still respects every limit ahead.
    val speed = DoubleArray(n + 1)
    speed[n] = cap[n]
    for (j in n - 1 downTo 0) {
        val d = SpatialOperations.haversine(points[j], points[j + 1])
        val decelCap = sqrt(speed[j + 1] * speed[j + 1] + 2.0 * accelMps2 * d)
        speed[j] = min(cap[j], decelCap)
    }
    // Forward: acceleration from behind is bounded by comfort and never above the limit.
    for (j in 1..n) {
        val d = SpatialOperations.haversine(points[j - 1], points[j])
        val accelCap = sqrt(speed[j - 1] * speed[j - 1] + 2.0 * accelMps2 * d)
        speed[j] = min(speed[j], accelCap)
    }
    val times = ArrayList<Double>(n)
    val speeds = ArrayList<Double>(n)
    for (i in 0 until n) {
        val d = SpatialOperations.haversine(points[i], points[i + 1])
        val v0 = speed[i]
        val v1 = speed[i + 1]
        val t = if (abs(v1 - v0) < 1e-9) d / v0 else 2.0 * d / (v0 + v1)
        times.add(t)
        speeds.add(d / t)
    }
    return TimedLine(points, times, speeds)
}

private fun midpoint(a: LatLng, b: LatLng): LatLng =
    LatLng((a.latitude + b.latitude) / 2.0, (a.longitude + b.longitude) / 2.0)

private fun interpolate(a: LatLng, b: LatLng, t: Double): LatLng =
    LatLng(
        a.latitude + (b.latitude - a.latitude) * t,
        a.longitude + (b.longitude - a.longitude) * t
    )
