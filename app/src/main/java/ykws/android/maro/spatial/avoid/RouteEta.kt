package ykws.android.maro.spatial.avoid

import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.RouteSlowLimit
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

/** Sampling step (m) the boundary splitter walks each leg at — half the shipped grid cell. */
private const val BOUNDARY_SAMPLE_M = 25.0

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
 */
fun timeLineWithLimits(
    waypoints: List<LatLng>,
    paceKn: Double,
    limitKnAt: (LatLng) -> Double?
): TimedLine {
    if (waypoints.size < 2) return TimedLine(waypoints, emptyList())
    val paceMps = Units.knotsToMps(paceKn)
    val points = splitAtLimitChanges(waypoints, limitKnAt)
    val legs = points.size - 1
    val times = ArrayList<Double>(legs)
    val speeds = ArrayList<Double>(legs)
    for (i in 0 until legs) {
        val limitKn = limitKnAt(midpoint(points[i], points[i + 1]))
        val speedMps = if (limitKn != null) min(Units.knotsToMps(limitKn), paceMps) else paceMps
        val dist = SpatialOperations.haversine(points[i], points[i + 1])
        times.add(dist / speedMps)
        speeds.add(speedMps)
    }
    return TimedLine(points, times, speeds)
}

/** Walks every leg and inserts the boundary vertices where [limitKnAt] changes. */
private fun splitAtLimitChanges(
    waypoints: List<LatLng>,
    limitKnAt: (LatLng) -> Double?
): List<LatLng> {
    val out = ArrayList<LatLng>(waypoints.size + 8)
    out.add(waypoints.first())
    for (i in 0 until waypoints.size - 1) {
        val sub = splitLeg(waypoints[i], waypoints[i + 1], limitKnAt)
        for (j in 1 until sub.size) out.add(sub[j])
    }
    return out
}

/** Splits one leg into `[a, ...crossings..., b]` by sampling then bisecting at each limit change. */
private fun splitLeg(a: LatLng, b: LatLng, limitKnAt: (LatLng) -> Double?): List<LatLng> {
    val dist = SpatialOperations.haversine(a, b)
    if (dist <= 1e-9) return listOf(a, b)
    val out = ArrayList<LatLng>(4)
    out.add(a)
    var prevT = 0.0
    var prevLimit = limitKnAt(a)
    val steps = ceil(dist / BOUNDARY_SAMPLE_M).toInt().coerceAtLeast(2)
    for (s in 1..steps) {
        val t = s.toDouble() / steps
        val p = interpolate(a, b, t)
        val limit = limitKnAt(p)
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
        val limit = limitKnAt(interpolate(a, b, mid))
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

/**
 * **The time a route spends in slow water, summed per speed limit** — the companion of [slowShares],
 * keyed by the limit rather than by the water's kind.
 *
 * Each slow leg's own seconds are charged to the water standing at its midpoint: a leg inside a priced
 * ring answers the strictest limit in force there ([limitKnAt], the clock's own read), and a leg inside
 * the 300 m band answers [bandLimitKn] as its own entry. A slow leg outside both is a **ramp** — the
 * deceleration before a limit or the acceleration after it — and is folded into the **nearest** entry by
 * walking outward from the leg, the forward one winning a tie, so a limit's figure counts its ramp up and
 * down. The zone wins over the band where both hold, so the entries never double-count. Entries come
 * band-first, then by ascending limit. Empty when nothing slowed the route.
 */
fun slowTimeByLimit(
    timed: TimedLine,
    paceKn: Double,
    limitKnAt: (LatLng) -> Double?,
    inZone: (LatLng) -> Boolean,
    inBand: (LatLng) -> Boolean,
    bandLimitKn: Double
): List<RouteSlowLimit> {
    val legs = timed.legTimesSec.size
    if (legs == 0 || paceKn <= 0.0) return emptyList()
    val paceMps = Units.knotsToMps(paceKn)
    val slow = BooleanArray(legs)
    val keyed = BooleanArray(legs)
    val keyLimit = DoubleArray(legs)
    val keyBand = BooleanArray(legs)
    for (i in 0 until legs) {
        val dist = SpatialOperations.haversine(timed.points[i], timed.points[i + 1])
        if (timed.legTimesSec[i] - dist / paceMps <= 0.0) continue
        slow[i] = true
        val mid = midpoint(timed.points[i], timed.points[i + 1])
        when {
            inZone(mid) -> {
                keyed[i] = true
                keyLimit[i] = limitKnAt(mid) ?: bandLimitKn
            }
            inBand(mid) -> {
                keyed[i] = true
                keyLimit[i] = bandLimitKn
                keyBand[i] = true
            }
        }
    }
    val origKeyed = keyed.copyOf()
    val ok = BooleanArray(legs)
    val limitOf = DoubleArray(legs)
    val bandOf = BooleanArray(legs)
    for (i in 0 until legs) {
        if (!slow[i]) continue
        if (origKeyed[i]) {
            ok[i] = true
            limitOf[i] = keyLimit[i]
            bandOf[i] = keyBand[i]
            continue
        }
        var chosen = -1
        for (d in 1..legs) {
            val after = i + d
            if (after < legs && origKeyed[after]) { chosen = after; break }
            val before = i - d
            if (before >= 0 && origKeyed[before]) { chosen = before; break }
        }
        if (chosen >= 0) {
            ok[i] = true
            limitOf[i] = keyLimit[chosen]
            bandOf[i] = keyBand[chosen]
        }
    }
    val totals = LinkedHashMap<Pair<Double, Boolean>, Double>()
    for (i in 0 until legs) {
        if (!ok[i]) continue
        val key = roundHalfKn(limitOf[i]) to bandOf[i]
        totals[key] = (totals[key] ?: 0.0) + timed.legTimesSec[i]
    }
    return totals.entries
        .map { RouteSlowLimit(it.key.first, it.value, it.key.second) }
        .sortedWith(compareBy({ if (it.isBand) 0 else 1 }, { it.limitKn }))
}

/** The limit key rounded to the nearest half knot, so float noise never splits one bucket in two. */
private fun roundHalfKn(limitKn: Double): Double = kotlin.math.round(limitKn * 2.0) / 2.0

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
    accelMps2: Double = AppConfig.routeSpeedAccelMps2
): TimedLine {
    if (waypoints.size < 2) return TimedLine(waypoints, emptyList())
    val paceMps = Units.knotsToMps(paceKn)
    val points = splitAtLimitChanges(waypoints, limitKnAt)
    val n = points.size - 1
    fun limitMpsAt(p: LatLng): Double =
        limitKnAt(p)?.let { min(Units.knotsToMps(it), paceMps) } ?: paceMps
    fun ceilingMpsAt(p: LatLng): Double =
        ceilingKnAt(p)?.let { min(Units.knotsToMps(it), paceMps) } ?: paceMps
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
