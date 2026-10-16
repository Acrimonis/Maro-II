package ykws.android.maro.spatial.multipass

import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.BBox

/**
 * **The fine layer's water, as the plan answers it — before any lattice snapping.**
 *
 * A plan states two things about its own fine water: the **cut reaches** the builder grows the coast's
 * harvested segments by, and the **membership bands** a cell's own coast distance must fall in to be fine
 * water. The two are deliberately separate, because the cut may over-cover (the rasterizer's mask paints
 * any cell the bands do not keep as land, which costs memory and never an answer) while a missed cell
 * would move one: the cut is the safe superset, the bands are the exact water.
 */
data class FineWater(
    /** The cut reaches (m): each grows the coast's harvested segments' boxes when the tiles are marked. */
    val coastReachesM: List<Double> = emptyList(),
    /** The coast-distance membership bands (m): a cell whose coast distance lies in one is fine water. */
    val coastBandsM: List<ClosedFloatingPointRange<Double>> = emptyList()
) {

    /** True where this answer asks for **no** fine layer at all — the walk is the coarse grid alone. */
    val isEmpty: Boolean
        get() = coastReachesM.isEmpty() && coastBandsM.isEmpty()
}

/**
 * **What a plan is handed to answer [FineWater]** — the corridor, the harvested coast, the margin and the
 * plan's own base cell. It carries no price and no cursor: the cut and the membership are geography, and
 * the plan's answer may not move with a rung. [box], [edges] and [openCoast] are the corridor's own
 * harvest, kept for a plan that reads the coast beside its widths.
 */
data class FineWaterQuery(
    val world: MultipassWorld,
    val box: BBox,
    val edges: List<MultipassEdge>,
    val openCoast: List<List<LatLng>>,
    val marginM: Double,
    val baseCellM: Double
)

/**
 * **The fine layer's membership, as the rasterizer applies it** — the coast bands, in the geometry the
 * window sweep can measure. It is [FineWater]'s mask half: the cut already chose the tiles, and this
 * decides, cell by cell, which of them the fine layer keeps.
 */
internal data class FineMask(
    val coastBandsM: List<ClosedFloatingPointRange<Double>>
) {

    /** True where no membership rule stands — the window keeps every passable cell, as it always did. */
    val isTrivial: Boolean
        get() = coastBandsM.isEmpty()
}

/** [FineWater]'s mask, over the coast bands the plan answered. */
internal fun FineWater.toMask(): FineMask = FineMask(coastBandsM)
