package ykws.android.maro.spatial.multipass

import ykws.android.maro.data.model.markers.BBox

/**
 * **The pre-P4.1 corridor anchor, kept for a fixture that pins its lattice relative to its own box.**
 *
 * Production draws the walk family on the world's **fixed** [`LatticeAnchor`] (P4.1 of the selective-perf
 * plan), never on the corridor, so the same water keeps its indices on every arm. A fixture that builds its
 * own windows from `family.coarse`/`family.fine` and marks cells by **local** indices — the seam and
 * fine-priority fixtures — instead wants the origin at its own box's south-west, exactly as it stood before
 * the change, so its absolute geometry is unmoved. This overload restores that origin for tests alone and is
 * never used by shipped code.
 */
internal fun LatticeFamily.Companion.of(
    corridor: BBox,
    coarseCellM: Double,
    fineCellM: Double
): LatticeFamily = of(
    LatticeAnchor(corridor.latSouth, corridor.lonWest, (corridor.latSouth + corridor.latNorth) / 2.0),
    coarseCellM,
    fineCellM
)
