package ykws.android.maro.spatial.multipass

import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.data.regulation.SpeedZone

/**
 * **One walk's grid, and the values that are its own.** A walk is the grid the A\* expands, the two
 * cells it starts and aims at and the cell size it was rasterized at; carrying them as one value is
 * what lets the coarse walk and the two-layer first walk travel through the same door.
 */
internal data class GridWalk(
    val grid: MultipassGrid,
    val startCell: CellIndex,
    val aimCell: CellIndex,
    val cellM: Double,
    /**
     * The water this walk may use where it is **several windows on one lattice** — `null` for the uniform
     * pass, whose single grid is its own walk. The path answers the walk's own coordinates either way: the
     * grid's for one window, the lattice's for a chain, and this is what resolves a path back to points.
     */
    val windows: WalkWindows? = null
)

/**
 * One tangent corner set: the offset points and, **per point**, the radius within which that corner may
 * move a bend. The radius is a function of the point because a two-layer walk resolves different water at
 * different sizes, and each corner's reach is the **local** cell's own (Phase 6) — a fine 20 m corner near
 * the coast moves a bend 40 m, a coarse one in open water 200 m — while a single grid answers one radius.
 */
internal data class CornerSet(val points: List<LatLng>, val radiusM: (LatLng) -> Double)

/**
 * The λ-free solve context the ladder's rungs share: the corridor, the grid and every reading the
 * solve-at-λ tail needs. Built once per arm, so three rungs cost one rasterise and three A* passes.
 *
 * **[windows] is the two-layer first walk's seam**, added with named construction rather than a 27th
 * positional field: it is `null` for the uniform pass, whose single grid is its own walk, and a family of
 * windows where the plan answered more than one tile. [grid], [startCell], [aimCell] and [cellM] stay the
 * **interior** layer's, so every single-grid read site keeps its own answer.
 */
internal data class GridContext(
    val world: MultipassWorld,
    val from: RoutePoint,
    val to: RoutePoint,
    val box: BBox,
    val edges: List<MultipassEdge>,
    val openCoast: List<List<LatLng>>,
    val capLatNorth: Double,
    val cellM: Double,
    /**
     * The **fine cell (m)** — the plan's metres answer for this engine, carried on the context so the
     * refinement and the clock read one value instead of deriving a size each.
     */
    val fineCellM: Double,
    val marginM: Double,
    val zoneOutsideMarginM: Double,
    val pace: Double,
    val grid: MultipassGrid,
    val startCell: CellIndex,
    val aimCell: CellIndex,
    val start: LatLng,
    val aim: LatLng,
    val sets: List<CornerSet>,
    val limitAt: (LatLng) -> Double?,
    val zones: List<SpeedZone>,
    val priced: List<ZoneRing>,
    val approaches: EndApproaches,
    val refusals: PullRefusals,
    val depthGateActive: Boolean,
    val minDepthM: Double,
    val regionSaturated: Boolean,
    /**
     * Whether the plan prices the shallow wall — `selective` alone. The pass prices the depth band on the
     * search and the pull's guard only where it holds, so `avoid` and `evolutive` never price it.
     */
    val depthBandActive: Boolean = false,
    /**
     * The two-layer first walk's windows, or `null` where the plan answered one tile — the uniform pass's
     * case, whose single grid is its own walk.
     */
    val windows: WalkWindows? = null
)

/**
 * One pass's own readings, returned rather than logged inside it: the caller that owns the decision
 * the pass leads to — the λ loop's correction, the offers' keep-or-drop — emits the pass's one line
 * where that decision is made, so the line carries the correction beside the pass that needed it.
 */
internal data class PassReading(
    /** The A\*'s own outcome: the path, or the exhaustion reading that says why there is none. */
    val search: SearchOutcome,
    /**
     * The pass's own line — the **last pull's** waypoints, the very list the pipeline hands on and
     * never the clock's split copy beside it, so the pass's answer is the one every later stage
     * reads. `emptyList()` where the A\* answered nothing.
     */
    val line: List<LatLng>,
    /** That same line already timed under the limits in force, or `null` where the A\* answered nothing. */
    val timed: TimedLine?,
    /**
     * The line's slow time split by what slowed it; the **zone** share is the λ loop's own quantity,
     * the band's and the ramps' read beside it so neither can drive a ring's correction. Zeroes on a
     * failing pass.
     */
    val shares: SlowShares,
    /** How many waypoints the first pull left — the pass's own pulled count. */
    val pulledCount: Int,
    /** How many the corner snap left. */
    val snappedCount: Int
) {
    /** The A\*'s own cell path length, 0 where it found nothing. */
    val pathCells: Int get() = search.path?.size ?: 0

    /** The settled line's own waypoint count, 0 where the pass failed. */
    val finalCount: Int get() = line.size
}
