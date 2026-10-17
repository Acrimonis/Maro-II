package ykws.android.maro.spatial.multipass

import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.BBox

/**
 * **The fine layer's water, as the plan answers it — before any lattice snapping.**
 *
 * A plan states three things about its own fine water: the **cut reaches** the builder grows the coast's
 * harvested segments by, the **membership bands** a cell's own coast distance must fall in to be fine
 * water, and the **low-depth collar** — the metres around a shallow cell that are kept fine so the
 * 20 m standoff and the 20→50 m weighted band exist around an offshore bank, not only along the shore.
 * The cut may over-cover (the rasterizer's mask paints any cell the rules do not keep as land, which
 * costs memory and never an answer) while a missed cell would move one: the cut is the safe superset,
 * the rules are the exact water.
 */
data class FineWater(
    /** The cut reaches (m): each grows the coast's harvested segments' boxes when the tiles are marked. */
    val coastReachesM: List<Double> = emptyList(),
    /** The coast-distance membership bands (m): a cell whose coast distance lies in one is fine water. */
    val coastBandsM: List<ClosedFloatingPointRange<Double>> = emptyList(),
    /**
     * **The low-depth collar's reach (m off a shallow cell)** — a cell whose gate distance is at or
     * inside it is fine water, so the fine 20 m cells follow the shallow water wherever it lies. `0.0`
     * where the plan asks no collar, which is every plan but `evolutive` with its gate armed.
     */
    val depthCollarM: Double = 0.0
) {

    /** True where this answer asks for **no** fine layer at all — the walk is the coarse grid alone. */
    val isEmpty: Boolean
        get() = coastReachesM.isEmpty() && coastBandsM.isEmpty() && depthCollarM <= 0.0
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
 * **The fine layer's membership, as the rasterizer applies it** — the coast bands and the low-depth
 * collar, in the geometry the window sweep can measure. It is [FineWater]'s mask half: the cut already
 * chose the tiles, and this decides, cell by cell, which of them the fine layer keeps.
 */
internal data class FineMask(
    val coastBandsM: List<ClosedFloatingPointRange<Double>>,
    /** The gate distance at or inside which a cell is fine water; `0.0` where no collar is asked. */
    val depthCollarM: Double = 0.0
) {

    /** True where no membership rule stands — the window keeps every passable cell, as it always did. */
    val isTrivial: Boolean
        get() = coastBandsM.isEmpty() && depthCollarM <= 0.0
}

/** [FineWater]'s mask, over the coast bands and the collar the plan answered. */
internal fun FineWater.toMask(): FineMask = FineMask(coastBandsM, depthCollarM)
