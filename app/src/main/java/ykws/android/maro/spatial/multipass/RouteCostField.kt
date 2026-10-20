package ykws.android.maro.spatial.multipass

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
     *
     * @param clearanceAt the distance (m) from a point to this source's nearest price boundary — the
     *   place its own arm changes — or `Double.MAX_VALUE` where it names none. The declaration is the
     *   source's own law: a boundary must be a **true distance** to a real arm change, and **every**
     *   arm change belongs in it — a hole in a ring and a collar's edge included. `MAX_VALUE` is the
     *   honest default and can never prove a group (see [priceClearanceM]).
     */
    class Soft(
        private val priceSec: (LatLng) -> Double,
        val tag: MultipassCellState,
        private val clearanceAt: (LatLng) -> Double = { Double.MAX_VALUE },
        /**
         * **The arm this source's price belongs to, as the money it charges** — `null` where the source
         * does not decompose, in which case [RouteCostField.armPricesAt] reports it under
         * [PriceArm.OTHER] rather than guessing.
         *
         * A source whose own price is the **max** of several arms (the band against the rings) returns
         * the winner alone, so the arms a point reports always sum to the price [costSec] answers — a
         * decomposition that could exceed its own total would be worse than none.
         */
        val armAt: ((LatLng) -> Pair<PriceArm, Double>)? = null
    ) : RouteCostSource {

        override fun costSec(p: LatLng): Double = priceSec(p)

        override fun blocked(p: LatLng): Boolean = false

        /**
         * The metres from [p] to the nearest point where this source's price arm changes —
         * `Double.MAX_VALUE` where the source names no boundary, which never proves a group.
         */
        fun priceClearanceM(p: LatLng): Double = clearanceAt(p)
    }
}

/**
 * **Which arm charged a point** — the identity a price report needs and a [RouteCostSource.Soft] alone
 * does not carry.
 *
 * It exists because a cell's own [MultipassCellState] tag cannot serve as one: the band and the rings
 * share a single source whose price is their **max**, tagged `ZONE` for both. The tag is what the
 * rasterizer writes for a cell; this is what a reader asking *who charged me* needs. [DEPTH] is the
 * gate berth's own arm, so the seconds it charges are never lumped into the residue with anything
 * else, and [OTHER] is the honest residue — a source that declares no arm is reported under it rather
 * than guessed at.
 */
enum class PriceArm { BAND, ZONE, MARKER, DEPTH, OTHER }

/**
 * What the field answers at one point: the hard block, the summed soft price and the tag the dearest
 * price writes. [softCostSec] is **added** to the base cost every cell already carries — never a
 * replacement for it, which is the invariant that keeps the A*'s cost well-posed.
 */
data class RouteCostAtPoint(
    val blocked: Boolean,
    val softCostSec: Double,
    val tag: MultipassCellState
)

/**
 * **The base a cell of open water costs, in seconds** — one cell of water crossed at the pace. The
 * unit's own home: every other price is an **excess** over this one, so the search costs in time and
 * nothing has to convert.
 */
fun baseCostSec(cellM: Double, paceKn: Double): Double = cellM / Units.knotsToMps(paceKn)

/**
 * **The one member cost law** — a fine tile's stored **pace-free factor** re-applied to a target grid's
 * own base: `base × factor`. It is the single home the tile's materialisation reads, so the factor a tile
 * carries becomes seconds **exactly once**, from the base the arm's grid opens at, and a tile can be
 * shared across paces.
 */
fun memberCostAtBase(baseCostSec: Double, factor: Double): Double = baseCostSec * factor

/**
 * **Whether a point [distanceM] off the coast stands inside the band's own width** — the water the
 * band's limit governs, and the water the band's price charges in full. One home for that boundary:
 * the band's price asks it for its full-price arm and the clock asks it for the band's limit in
 * force, so the two can never disagree about which water the band is. The strip between the width and
 * the band's reach is the **collar** — priced at a fraction, and never carrying the band's limit.
 */
fun insideBandWidthM(distanceM: Double, bandWidthM: Double): Boolean = distanceM <= bandWidthM

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
 * **The one slow-water price, read by the search and the pull's guard alike, over every source.** A cell
 * carrying the strictest limit in force pays that limit's **full** time excess; a ring's outside margin
 * pays its own limit's excess times [collarFraction], the band's its own times [bandCollarFraction]; and
 * the dearest of the three is the price.
 *
 * [interiorLimitKn] is the **strictest limit in force** — a ring's own limit or the band's own width,
 * whichever is slower, or 0.0 where none stands — so a band and a ring over the same slow water cost it
 * once instead of twice. The limits are handed in by whoever knows them: the A\* reads them off the
 * grid's stored limits, the pull's guard off the same point geometry, and both hand this function the
 * same values, which is what keeps the search and the guard from ever disagreeing.
 */
fun slowWaterPriceAt(
    cellM: Double,
    paceKn: Double,
    k: Double,
    interiorLimitKn: Double,
    collarLimitKn: Double,
    bandCollarLimitKn: Double,
    collarFraction: Double,
    bandCollarFraction: Double
): Double = max(
    if (interiorLimitKn > 0.0) zonePriceSec(cellM, paceKn, interiorLimitKn, k) else 0.0,
    max(
        if (collarLimitKn > 0.0) zoneCollarPriceSec(cellM, paceKn, collarLimitKn, k, collarFraction) else 0.0,
        if (bandCollarLimitKn > 0.0) {
            zoneCollarPriceSec(cellM, paceKn, bandCollarLimitKn, k, bandCollarFraction)
        } else {
            0.0
        }
    )
)

/**
 * The 300 m band's price at one distance: the band's own width pays the full [fullSec], the outside
 * margin between that width and the band's reach pays [fullSec] × [costFraction], and everything
 * beyond pays nothing. One home for the split, read by the field's band source and by its tests; the
 * band's own water is [insideBandWidthM]'s call, never a second test written here.
 */
fun bandPriceAt(
    bandWidthM: Double,
    outsideMarginM: Double,
    fullSec: Double,
    costFraction: Double,
    distanceM: Double
): Double = when {
    insideBandWidthM(distanceM, bandWidthM) -> fullSec
    distanceM <= bandReachM(bandWidthM, outsideMarginM) -> fullSec * costFraction
    else -> 0.0
}

/**
 * How far off the coast the band's price reaches: the band's own width plus its outside margin, so the
 * priced strip covers the margin land already took. One home, read by the rasterizer's sweep and by
 * the pull's own band source.
 */
fun bandReachM(bandWidthM: Double, marginM: Double): Double = bandWidthM + marginM

/**
 * **The depth gate as a hard source** — one home for the rule, so the engine and its tests read the
 * same wall.
 *
 * A known depth below [minDepthM] is a wall; everything else is ignored — deeper water, a coarse
 * source's reading at or above the threshold and **NoData alike**, with no confidence floor and no
 * penalty. It is a coarse guard on the route being written, not a fine sounding: [depthMAt] answers
 * `NaN` for an unsurveyed point, which this gate does not block.
 *
 * **It is the bare step alone** — the interior of blocked water. The standoff off its edge is measured
 * on the traced 3 m line ([`GateRule`], [`ShallowEdgeIndex`]), which the rasterizer paints from, the
 * search checks its steps against, and the pull and the corner pass read for their chords and bows.
 *
 * The clearance it declares is 0 m where it blocks and nothing everywhere else, so the pull refuses a
 * chord through a shallow cell while the rest of the field's clearance stays the coastline's own.
 */
fun depthGateSource(
    minDepthM: Double,
    depthMAt: (LatLng) -> Double
): RouteCostSource.Hard {
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
 *
 * **The hard walls come in the two shapes the pull reads apart.** A *rastered* wall answers a per-cell
 * **step** — the depth gate's 0-or-nothing — and is tested at every mark by [hardBlocked]; a
 * *materialized* wall answers a true **distance** — the coastline's live index query — by
 * [hardDistanceM], and that distance is the metric the pull's margin stands on. A third hard source
 * must declare which of the two it is: a wall that is neither a metric distance nor a clean step makes
 * the walk's own soundness argument untrue, and nothing here can tell the difference for it.
 *
 * **The gate berth is a price of its own road, and never a source.** Its law is [`BerthLaw`] — one rate
 * at every distance off the measured 3 m edge — and it reaches the search through the **grid's stored
 * distance field** ([MultipassGrid.gateDistanceM], written exactly near the edge by the rasterizer) and
 * the pull's guard through the edge index at the point itself, rather than through this field. Both
 * scale it by the pass's λ, so the two price one law; and [armPricesAt]'s reader reports it as
 * [PriceArm.DEPTH] rather than lumping its seconds into the residue.
 */
class RouteCostField(
    private val sources: List<RouteCostSource> = emptyList(),
    /**
     * **The cell this field's prices were built for** — the size `costField` priced every arm at, and
     * the divisor that turns a per-cell price into the **seconds per metre** a walk over a length
     * needs. It defaults to `1.0` so a field built without it prices a metre exactly as it prices a
     * cell, which is the shape every fixture that never asked for a rate already had.
     */
    val cellM: Double = 1.0
) {

    private val hard: List<RouteCostSource.Hard> = sources.filterIsInstance<RouteCostSource.Hard>()

    private val soft: List<RouteCostSource.Soft> = sources.filterIsInstance<RouteCostSource.Soft>()

    /** The rastered walls — the depth gate — whose reading is a step and whose test is per mark. */
    private val rasteredHard: List<RouteCostSource.Hard> = hard.filter { it.rastered }

    /** The materialized walls — the coastline — whose reading is the true distance the margin tests. */
    private val materializedHard: List<RouteCostSource.Hard> = hard.filterNot { it.rastered }

    /** True when a price is in force — the rasterizer's price pass is skipped when it is not. */
    val hasSoft: Boolean get() = soft.isNotEmpty()

    /** True when a wall is rastered per cell — a field whose walls are all swept writes no block. */
    val hasBlocking: Boolean get() = rasteredHard.isNotEmpty()

    /**
     * The one evaluator: [priceAt] plus the hard walls' own test. The price law keeps one home here,
     * so the walk that only wants the seconds a cell costs can ask [softPriceSecAt] and pay no
     * `blocked` read at all — the verdict it would discard.
     */
    fun evaluate(p: LatLng): RouteCostAtPoint {
        val price = priceAt(p)
        for (source in sources) {
            if (source.blocked(p)) {
                return RouteCostAtPoint(true, price.softCostSec, price.tag)
            }
        }
        return price
    }

    /**
     * **The price law read alone** — the summed soft price at [p], no hard source asked. It is the body
     * of [evaluate] without its hard loop, so a walk that reads the price and discards the `blocked`
     * flag stops paying for it, and the two can never disagree about the price.
     */
    fun softPriceSecAt(p: LatLng): Double = priceAt(p).softCostSec

    /**
     * **The price law as a rate** — [softPriceSecAt] per metre, the cell's own cost divided by
     * [cellM]. A walk that multiplies a price by a length must read this and never the per-cell value:
     * seconds times metres is not seconds, and the whole point of the rate is that the same field can
     * price a cell for the A\* and a stretch of line for a pull.
     */
    fun softPriceSecPerMAt(p: LatLng): Double = softPriceSecAt(p) / cellM

    /**
     * **Which arms charged [p], and how much** — the decomposition of [softPriceSecAt], for a report
     * that has to name a reason rather than print a number. Each soft source answers its own winner
     * through [RouteCostSource.Soft.armAt], and a source that declares none is reported under
     * [PriceArm.OTHER], so the arms' prices always sum to the total the field would charge.
     */
    fun armPricesAt(p: LatLng): List<Pair<PriceArm, Double>> {
        var other = 0.0
        val arms = ArrayList<Pair<PriceArm, Double>>(soft.size)
        for (source in soft) {
            val arm = source.armAt?.invoke(p)
            if (arm != null) {
                if (arm.second > 0.0) arms.add(arm)
            } else {
                other += source.costSec(p)
            }
        }
        if (other > 0.0) arms.add(PriceArm.OTHER to other)
        return arms
    }

    /**
     * Distance (m) to the nearest **soft price boundary** — the minimum over the field's soft sources
     * of their own declaration ([RouteCostSource.Soft.priceClearanceM]), the shape [hardDistanceM]
     * already has for the walls, so the walk asks one number and the sources stay the only ones who
     * know their own law. `Double.MAX_VALUE` where no source names one, which proves nothing.
     */
    fun priceClearanceM(p: LatLng): Double {
        var nearest = Double.MAX_VALUE
        for (source in soft) {
            val clearance = source.priceClearanceM(p)
            if (clearance < nearest) nearest = clearance
        }
        return nearest
    }

    /** The price law's one home: the summed soft price at [p] and the dearest tag, no wall asked. */
    private fun priceAt(p: LatLng): RouteCostAtPoint {
        var costSec = 0.0
        var tag = MultipassCellState.FREE
        for (source in soft) {
            val cost = source.costSec(p)
            if (cost > 0.0) {
                costSec += cost
                if (source.tag.ordinal > tag.ordinal) tag = source.tag
            }
        }
        return RouteCostAtPoint(false, costSec, tag)
    }

    /**
     * The cheap per-mark wall test: the **rastered** walls alone, whose reading is a *step* — the gate
     * answers `true` on the shallow cell and `false` everywhere else. It is the half of the hard read
     * that may never be coarsened, a step carrying no bound that a nearby reading could prove.
     */
    fun hardBlocked(p: LatLng): Boolean {
        for (source in rasteredHard) {
            if (source.blocked(p)) return true
        }
        return false
    }

    /**
     * Distance (m) to the nearest **materialized** hard wall — the pull's margin reading, taken **along
     * the emitted line** and never per grid cell: the coastline's own wall answers it by a live index
     * query, which is the one read this field pays per mark and the one a sampled walk may skip.
     *
     * A rastered wall is **not** read here: its reading is a step rather than a distance, so it is asked
     * through [hardBlocked] instead — a step folds into the walk's per-mark test, never into its margin.
     */
    fun hardDistanceM(p: LatLng): Double {
        var nearest = Double.MAX_VALUE
        for (source in materializedHard) {
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
