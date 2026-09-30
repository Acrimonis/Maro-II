package ykws.android.maro.spatial.avoid

import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.spatial.SpatialOperations
import ykws.android.maro.spatial.Units
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The zone- and curve-aware ETA: the drawn line re-vertexed where the limit in force changes, then
 * timed leg by leg — every transition a constant-acceleration ramp at [AppConfig.routeSpeedAccelMps2],
 * whose one home is the key (this file holds no rate of its own).
 *
 * Inside a zone the boat obeys the strictest limit (never above the configured pace); outside it rides
 * the pace. Leaving a slower stretch it accelerates back to the pace **after** the boundary, a limit
 * never being exceeded; entering one it **starts slowing `(v0² − v1²) / 2a` before the boundary**, so
 * the profile shows the boat easing down outside the ring and those slow metres are paid before the
 * zone rather than inside it. A line whose first leg is already inside a zone opens at that zone's
 * limit, the boat being there already.
 *
 * **The curve caps sit beside the longitudinal ramp.** A [CurveCap] names a speed the clock may not
 * exceed at one of the faired line's own vertices — the fitter emits one per arc point of a resolved
 * bend, and the strictest in force wins where a cap and a zone limit meet. A cap is a **point**, so it
 * needs no boundary vertex of its own: every leg whose endpoint carries one is bound by it, and the
 * ramp does the rest — the boat eases to the bend's speed arriving at its first arc point, holds it
 * across the arc and climbs back after. The caps are read off the same profile as the limits, so the
 * cap's own delta (the faired line timed with and without them) is exactly the seconds the slowdown
 * costs — never a hidden term and never a re-cost of the geometry.
 *
 * This is a **clock**, never a price: nothing here reads λ or the A\*'s own cost, so the reported time
 * is λ-free however the search was priced.
 */

/** Sampling step (m) the boundary splitter walks each leg at — half the shipped grid cell. */
private const val BOUNDARY_SAMPLE_M = 25.0

/**
 * One **curve cap**: the speed (kn) the clock must not exceed at a point on the faired line — the
 * fitter's per-arc-point emission for a resolved bend. The same point carrying two caps keeps the
 * strictest (lowest) one.
 */
data class CurveCap(val point: LatLng, val capKn: Double)

/** A polyline split at limit changes, with one planned time and one made-good speed per split leg. */
data class TimedLine(
    val points: List<LatLng>,
    val legTimesSec: List<Double>,
    /**
     * The **pace made good** over each leg, in m/s — `distance / time`, so a leg carrying a ramp
     * reports the average of its own profile rather than either end of it and the figure can never
     * disagree with the clock beside it.
     */
    val legSpeedsMps: List<Double> = emptyList()
) {
    val durationSec: Double get() = legTimesSec.sum()
}

/**
 * Splits [waypoints] where [limitKnAt] changes and times each split leg: inside a zone the strictest
 * limit binds, outside the pace binds, a limit rise is climbed after the boundary and a limit fall is
 * reached **at** it. Any [caps] stand beside the limit — a leg whose endpoint carries one is bound by
 * it, strictest-wins, so a rounded bend is taken at its corner speed between the ramps.
 */
fun timeLineWithLimits(
    waypoints: List<LatLng>,
    paceKn: Double,
    limitKnAt: (LatLng) -> Double?,
    caps: List<CurveCap> = emptyList(),
    accelMps2: Double = AppConfig.routeSpeedAccelMps2
): TimedLine {
    if (waypoints.size < 2) return TimedLine(waypoints, emptyList())
    val paceMps = Units.knotsToMps(paceKn)
    // One home for the strictest cap at a point: the same vertex carrying two caps keeps the slower.
    val capKnAt: Map<LatLng, Double> = caps
        .groupBy { it.point }
        .mapValues { (_, shared) -> shared.minOf { it.capKn } }
    // The cap at an endpoint binds the leg that ends on it and the one that leaves it, which is what
    // places the spiral's slowdown beside the longitudinal ramp rather than inside the arc alone.
    fun capBoundMps(p: LatLng): Double =
        capKnAt[p]?.let { min(Units.knotsToMps(it), paceMps) } ?: paceMps
    val points = splitAtLimitChanges(waypoints, limitKnAt)
    val legs = points.size - 1
    // One target per leg: the strictest of the limit in force inside it and any cap on its ends,
    // never above the pace.
    val targets = DoubleArray(legs) { i ->
        val limitKn = limitKnAt(midpoint(points[i], points[i + 1]))
        val limitMps = if (limitKn != null) min(Units.knotsToMps(limitKn), paceMps) else paceMps
        min(limitMps, min(capBoundMps(points[i]), capBoundMps(points[i + 1])))
    }
    val times = ArrayList<Double>(legs)
    val speeds = ArrayList<Double>(legs)
    // The boat is where the line starts, so a line opening inside a zone opens at that zone's limit.
    var carriedMps = if (legs == 0) paceMps else min(paceMps, targets[0])
    for (i in 0 until legs) {
        val dist = SpatialOperations.haversine(points[i], points[i + 1])
        // The next leg's limit is what the boundary ahead asks for: this leg arrives at it, which is
        // what puts the decel's metres outside the ring it leads into.
        val arriveMps = if (i + 1 < legs) min(targets[i], targets[i + 1]) else targets[i]
        val timed = segmentTimeM(dist, carriedMps, targets[i], arriveMps, accelMps2)
        times.add(timed.first)
        // The pace made good: the same numbers the clock just produced, divided the other way, so a
        // zero-time leg reads zero rather than an infinity.
        speeds.add(if (timed.first > 0.0) dist / timed.first else 0.0)
        carriedMps = timed.second
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

/**
 * The speed (m/s) the boat still carries after decelerating [distanceM] from [vStartMps] at
 * [accelMps2] — the clock's own deceleration ramp, with the radicand clamped at 0. One home, read by
 * [segmentTimeM] and by the curve fitter's deceleration floor, so the two can never disagree.
 */
internal fun decelSpeedMps(vStartMps: Double, distanceM: Double, accelMps2: Double): Double =
    sqrt((vStartMps * vStartMps - 2.0 * accelMps2 * distanceM).coerceAtLeast(0.0))

/**
 * Time one segment (m) that starts at [vStart], may ride up to its own leg's [vTop], and arrives at
 * [vArrive] — the lower of this leg's limit and the one the boundary ahead asks for.
 *
 * A leg the boat ends slower on is the **entering** shape: it decelerates over
 * `(vStart² − vArrive²) / 2a` metres and then cruises at [vArrive], so the decel is paid before the
 * boundary the leg ends on. Every other leg rides up to [vTop] and cruises at it, which leaves the
 * exit ramp where it was: a limit is never exceeded before the boundary it belongs to.
 *
 * Returns (seconds, the speed the boat carries into the next leg, m/s).
 */
private fun segmentTimeM(
    distanceM: Double,
    vStart: Double,
    vTop: Double,
    vArrive: Double,
    accelMps2: Double
): Pair<Double, Double> {
    if (distanceM <= 0.0) return 0.0 to vArrive
    if (vArrive < vStart) {
        val rampDist = (vStart * vStart - vArrive * vArrive) / (2.0 * accelMps2)
        if (distanceM <= rampDist) {
            val endMps = decelSpeedMps(vStart, distanceM, accelMps2)
            return ((vStart - endMps) / accelMps2) to endMps
        }
        return ((vStart - vArrive) / accelMps2 + (distanceM - rampDist) / vArrive) to vArrive
    }
    val rampDist = (vTop * vTop - vStart * vStart) / (2.0 * accelMps2)
    if (distanceM <= rampDist) {
        val endMps = sqrt(vStart * vStart + 2.0 * accelMps2 * distanceM)
        return ((endMps - vStart) / accelMps2) to endMps
    }
    return ((vTop - vStart) / accelMps2 + (distanceM - rampDist) / vTop) to vTop
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

private fun midpoint(a: LatLng, b: LatLng): LatLng =
    LatLng((a.latitude + b.latitude) / 2.0, (a.longitude + b.longitude) / 2.0)

private fun interpolate(a: LatLng, b: LatLng, t: Double): LatLng =
    LatLng(
        a.latitude + (b.latitude - a.latitude) * t,
        a.longitude + (b.longitude - a.longitude) * t
    )
