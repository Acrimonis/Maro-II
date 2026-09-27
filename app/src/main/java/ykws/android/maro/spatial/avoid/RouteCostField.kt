package ykws.android.maro.spatial.avoid

import ykws.android.maro.data.model.LatLng
import ykws.android.maro.spatial.Units
import kotlin.math.max

/**
 * **One world source on the corridor grid, as the unified cost field reads it.**
 *
 * A source is either a [Hard] wall the route may never cross — land, islands, hazard rings, the 3 m
 * depth gate — or a [Soft] price the route may pay — the 300 m band, a speed zone, a marker weight.
 * Both answer [blocked] in the same vocabulary, so the rasterizer, the A*, the pull and (later) the
 * curve fitter read one model instead of three.
 *
 * **The soft price is time, in seconds** ([costSec]): the A\* costs in seconds, so what a slow cell
 * costs is the **excess** its limit adds over the pace, and the price is a time like the base it adds
 * to. The walls stay geometry: a clearance is metres, and only the search and the pull's own price
 * read seconds.
 *
 * **Two shapes, two materializations, and that is deliberate.** A [Hard] wall is either *rastered* —
 * [blocked] is asked once per cell centre and the rasterizer paints the cell land (the depth gate,
 * phase 2) — or *materialized* by the rasterizer's geometry sweep, which is how the coastline's own
 * wall is built: the sweep paints its margin band and fills its rings in one pass over the harvested
 * edges, where a per-cell water query would walk the index ~33 000 times. Such a wall declares itself
 * by answering [Hard.distanceM], which is what the pull's margin reads, and leaves [blocked] `false`.
 */
sealed interface RouteCostSource {

    /** The seconds this source adds at [p]; a [Hard] wall answers 0 — its effect is [blocked]. */
    fun costSec(p: LatLng): Double

    /** True when [p] is impassable for this source. A [Soft] price is never blocking. */
    fun blocked(p: LatLng): Boolean

    /**
     * A wall — impassable where [blocked], and answering the distance the pull's margin tests against.
     *
     * @param blockedAt the per-cell wall test the rasterizer asks; `false` when the geometry sweep is
     *   what materializes this wall instead (the coastline's own).
     * @param distanceAt the distance (m) from a point to this source's nearest wall; the pull's read.
     */
    class Hard(
        private val blockedAt: ((LatLng) -> Boolean)? = null,
        private val distanceAt: (LatLng) -> Double = { Double.MAX_VALUE }
    ) : RouteCostSource {

        /** True when this wall is rastered per cell rather than materialized by the geometry sweep. */
        val rastered: Boolean get() = blockedAt != null

        override fun costSec(p: LatLng): Double = 0.0

        override fun blocked(p: LatLng): Boolean = blockedAt?.invoke(p) ?: false

        /** Distance (m) from [p] to this source's nearest wall — the pull's margin reading. */
        fun distanceM(p: LatLng): Double = distanceAt(p)
    }

    /**
     * A price — passable, and the metres standing inside it cost [costSec] seconds more. Its [tag] is
     * the cell state the rasterizer writes, the dearest price winning where two overlap.
     */
    class Soft(
        private val priceSec: (LatLng) -> Double,
        val tag: AvoidCellState
    ) : RouteCostSource {

        override fun costSec(p: LatLng): Double = priceSec(p)

        override fun blocked(p: LatLng): Boolean = false
    }
}

/**
 * What the field answers at one point: the hard block, the summed soft price and the tag the dearest
 * price writes. [softCostSec] is **added** to the base cost every cell already carries — never a
 * replacement for it, which is the invariant that keeps the A*'s cost well-posed.
 */
data class RouteCostAtPoint(
    val blocked: Boolean,
    val softCostSec: Double,
    val tag: AvoidCellState
)

/**
 * **The base a cell of open water costs, in seconds** — one cell of water crossed at the pace. The
 * unit's own home: every other price is an **excess** over this one, so the search costs in time and
 * nothing has to convert.
 */
fun baseCostSec(cellM: Double, paceKn: Double): Double = cellM / Units.knotsToMps(paceKn)

/**
 * **The depth gate as a hard source** — one home for the rule, so the engine and its tests read the
 * same wall.
 *
 * A known depth below [minDepthM] is a wall; everything else is ignored — deeper water, a coarse
 * source's reading at or above the threshold and **NoData alike**, with no confidence floor and no
 * penalty. It is a coarse guard on the route being written, not a fine sounding: [depthMAt] answers
 * `NaN` for an unsurveyed point, which this gate does not block.
 *
 * The clearance it declares is 0 m where it blocks and nothing everywhere else, so the pull refuses a
 * chord through a shallow cell while the rest of the field's clearance stays the coastline's own.
 */
/**
 * **The priced band's per-cell price, in seconds** — the base time one cell of open water costs,
 * scaled by how much dearer the time spent inside the band is ([softCostAversion]; 1.0 prices it as
 * open water). One home, read by the rasterizer's band sweep and by the pull's own band source alike,
 * so the grid and the chord guard can never disagree about what a metre in the band is worth.
 */
fun bandPriceSec(cellM: Double, paceKn: Double, softCostAversion: Double): Double =
    baseCostSec(cellM, paceKn) * (softCostAversion - 1.0)

/**
 * The price of standing in one speed-zone cell, in seconds: the cell's **time excess** over the limit
 * it carries, scaled by [k]. The excess is `cellM × (1 / v(limit) − 1 / v(pace))` — the same cell
 * covered at the limit instead of at the pace — so at `k = 1` the cell costs its true travel time and
 * the search minimises real time, while a slower limit or a higher [k] makes it dearer. Clamped at 0
 * so a zone can only make the sea dearer, never cheaper. The pace is the boat's own configured pace,
 * never the limit in force, so a zone whose limit reaches the pace costs nothing.
 */
fun zonePriceSec(cellM: Double, paceKn: Double, limitKn: Double, k: Double): Double {
    val excess = cellM * (1.0 / Units.knotsToMps(limitKn) - 1.0 / Units.knotsToMps(paceKn))
    return max(0.0, excess * k)
}

/**
 * The outside-margin price at one cell: the interior's [zonePriceSec], scaled by [costFraction] —
 * strictly below the interior's while the fraction is below 1, which is the gradient the search reads
 * to prefer the margin's outer edge over the zone's interior.
 */
fun zoneCollarPriceSec(
    cellM: Double,
    paceKn: Double,
    limitKn: Double,
    k: Double,
    costFraction: Double
): Double = zonePriceSec(cellM, paceKn, limitKn, k) * costFraction

/**
 * **The one zone price, read by both the search and the pull's guard.** An interior limit prices at
 * the zone's full time excess; a collar limit — a cell outside every ring but within the outside
 * margin — prices at that excess times [costFraction]. A cell carrying both (inside one zone, near
 * another's ring) takes the dearer. The search reads the two limits off the grid; the pull's guard
 * reads them off the same point geometry, and both hand this function the same two values, which is
 * what keeps the two prices from ever disagreeing.
 */
fun zonePriceAtLimits(
    cellM: Double,
    paceKn: Double,
    interiorLimitKn: Double,
    collarLimitKn: Double,
    k: Double,
    costFraction: Double
): Double = max(
    if (interiorLimitKn > 0.0) zonePriceSec(cellM, paceKn, interiorLimitKn, k) else 0.0,
    if (collarLimitKn > 0.0) zoneCollarPriceSec(cellM, paceKn, collarLimitKn, k, costFraction) else 0.0
)

/**
 * The 300 m band's price at one distance: the band's own width pays the full [fullSec], the outside
 * margin between that width and the band's reach pays [fullSec] × [costFraction], and everything
 * beyond pays nothing. One home for the split, read by the field's band source and by its tests.
 */
fun bandPriceAt(
    bandWidthM: Double,
    outsideMarginM: Double,
    fullSec: Double,
    costFraction: Double,
    distanceM: Double
): Double = when {
    distanceM <= bandWidthM -> fullSec
    distanceM <= bandReachM(bandWidthM, outsideMarginM) -> fullSec * costFraction
    else -> 0.0
}

/**
 * How far off the coast the band's price reaches: the band's own width plus its outside margin, so the
 * priced strip covers the margin land already took. One home, read by the rasterizer's sweep and by
 * the pull's own band source.
 */
fun bandReachM(bandWidthM: Double, marginM: Double): Double = bandWidthM + marginM

fun depthGateSource(minDepthM: Double, depthMAt: (LatLng) -> Double): RouteCostSource.Hard {
    // The rule itself is [depthClearsGate]'s, read by the ring's validity question and by the berth
    // carve as well: one home, so a walled cell, a red target and a carve can never disagree.
    val belowGate: (LatLng) -> Boolean = { p -> !depthClearsGate(depthMAt(p), minDepthM) }
    return RouteCostSource.Hard(
        blockedAt = belowGate,
        distanceAt = { p -> if (belowGate(p)) 0.0 else Double.MAX_VALUE }
    )
}

/**
 * **The unified cost field: every world source in one list, read through one evaluator.**
 *
 * [evaluate] is that evaluator — the summed soft price at a point and the hard block — and it is what
 * the rasterizer asks once per cell centre; the pull asks [hardDistanceM] along the emitted line and
 * the same [evaluate] for the price. The A* is unchanged in structure: it reads the `sourceCostSec`
 * the rasterizer wrote, which is the grid's base cost plus this field's prices, and adds the zone's
 * own excess from the two **limits** the grid stores per cell — interior and outside margin.
 *
 * **The base cost is the grid's, and a source may only add to it.** A passable cell is never cheaper
 * than the base, so no price can pay the search back — the shortest path stays defined and the closed
 * set the A* keeps stays valid. This is why [RouteCostSource] has no negative arm: a marker makes the
 * sea dearer or leaves it alone, and a genuine *go through this place* is a via, not a price.
 */
class RouteCostField(
    private val sources: List<RouteCostSource> = emptyList()
) {

    private val hard: List<RouteCostSource.Hard> = sources.filterIsInstance<RouteCostSource.Hard>()

    private val soft: List<RouteCostSource.Soft> = sources.filterIsInstance<RouteCostSource.Soft>()

    /** True when a priced source exists — the rasterizer's price pass is skipped when it is not. */
    val hasSoft: Boolean get() = soft.isNotEmpty()

    /** True when a wall is rastered per cell — a field whose walls are all swept writes no block. */
    val hasBlocking: Boolean get() = hard.any { it.rastered }

    /** The one evaluator: the hard block at [p], its summed soft price, and the tag that price writes. */
    fun evaluate(p: LatLng): RouteCostAtPoint {
        var blocked = false
        for (source in sources) {
            if (source.blocked(p)) {
                blocked = true
                break
            }
        }
        var costSec = 0.0
        var tag = AvoidCellState.FREE
        for (source in soft) {
            val cost = source.costSec(p)
            if (cost > 0.0) {
                costSec += cost
                if (source.tag.ordinal > tag.ordinal) tag = source.tag
            }
        }
        return RouteCostAtPoint(blocked, costSec, tag)
    }

    /**
     * Distance (m) to the nearest hard wall — the pull's margin reading, taken **along the emitted
     * line** and never per grid cell: the coastline's own wall answers it by a live index query.
     */
    fun hardDistanceM(p: LatLng): Double {
        var nearest = Double.MAX_VALUE
        for (source in hard) {
            val distance = source.distanceM(p)
            if (distance < nearest) nearest = distance
        }
        return nearest
    }

    companion object {

        /** No source at all — open water at the base price. */
        val EMPTY = RouteCostField()

        /** A field whose only wall is described by [distanceAt] — the clearance-only shape, for tests. */
        fun ofHard(distanceAt: (LatLng) -> Double): RouteCostField =
            RouteCostField(listOf(RouteCostSource.Hard(distanceAt = distanceAt)))
    }
}
