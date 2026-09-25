package ykws.android.maro.spatial.avoid

import ykws.android.maro.data.model.LatLng

/**
 * **One world source on the corridor grid, as the unified cost field reads it.**
 *
 * A source is either a [Hard] wall the route may never cross — land, islands, hazard rings, the 3 m
 * depth gate — or a [Soft] price the route may pay — the 300 m band, a speed zone, a marker weight.
 * Both answer in **metres-equivalent** ([costM]), and both answer [blocked] in the same vocabulary, so
 * the rasterizer, the A*, the pull and (later) the curve fitter read one model instead of three.
 *
 * **Two shapes, two materializations, and that is deliberate.** A [Hard] wall is either *rastered* —
 * [blocked] is asked once per cell centre and the rasterizer paints the cell land (the depth gate,
 * phase 2) — or *materialized* by the rasterizer's geometry sweep, which is how the coastline's own
 * wall is built: the sweep paints its margin band and fills its rings in one pass over the harvested
 * edges, where a per-cell water query would walk the index ~33 000 times. Such a wall declares itself
 * by answering [Hard.distanceM], which is what the pull's margin reads, and leaves [blocked] `false`.
 */
sealed interface RouteCostSource {

    /** The metres-equivalent this source adds at [p]; a [Hard] wall answers 0 — its effect is [blocked]. */
    fun costM(p: LatLng): Double

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

        override fun costM(p: LatLng): Double = 0.0

        override fun blocked(p: LatLng): Boolean = blockedAt?.invoke(p) ?: false

        /** Distance (m) from [p] to this source's nearest wall — the pull's margin reading. */
        fun distanceM(p: LatLng): Double = distanceAt(p)
    }

    /**
     * A price — passable, and the metres standing inside it cost [costM] metres-equivalent more. Its
     * [tag] is the cell state the rasterizer writes, the dearest price winning where two overlap.
     */
    class Soft(
        private val priceM: (LatLng) -> Double,
        val tag: AvoidCellState
    ) : RouteCostSource {

        override fun costM(p: LatLng): Double = priceM(p)

        override fun blocked(p: LatLng): Boolean = false
    }
}

/**
 * What the field answers at one point: the hard block, the summed soft price and the tag the dearest
 * price writes. [softCostM] is **added** to the base cost every cell already carries — never a
 * replacement for it, which is the invariant that keeps the A*'s cost well-posed.
 */
data class RouteCostAtPoint(
    val blocked: Boolean,
    val softCostM: Double,
    val tag: AvoidCellState
)

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
fun depthGateSource(minDepthM: Double, depthMAt: (LatLng) -> Double): RouteCostSource.Hard {
    val belowGate: (LatLng) -> Boolean = { p ->
        val depthM = depthMAt(p)
        !depthM.isNaN() && depthM < minDepthM
    }
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
 * the same [evaluate] for the price. The A* is unchanged in structure: it reads the `sourceCostM` the
 * rasterizer wrote, which is the grid's base cost plus this field's prices.
 *
 * **The base cost is the grid's, and a source may only add to it.** A passable cell is never cheaper
 * than the base, so no price can pay the search back — the shortest path stays defined and the closed
 * set the A* keeps stays valid. This is why [RouteCostSource] has no negative arm: a marker makes the
 * sea dearer or leaves it alone, and a genuine *go through this place* is a via, not a price.
 */
class RouteCostField(private val sources: List<RouteCostSource> = emptyList()) {

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
        var costM = 0.0
        var tag = AvoidCellState.FREE
        for (source in soft) {
            val cost = source.costM(p)
            if (cost > 0.0) {
                costM += cost
                if (source.tag.ordinal > tag.ordinal) tag = source.tag
            }
        }
        return RouteCostAtPoint(blocked, costM, tag)
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
