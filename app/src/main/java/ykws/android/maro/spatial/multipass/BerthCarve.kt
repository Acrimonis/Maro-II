package ykws.android.maro.spatial.multipass

import ykws.android.maro.data.model.DepthSample
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.spatial.SpatialOperations
import kotlin.math.ceil

/**
 * **The water each end is granted, and the depth gate's one rule.**
 *
 * The shore margin is a rule for the **route**, not for the destination: it is waived in a disc around each
 * end and nowhere else. [openEndDisc] opens the water that disc needs on the grid, and the search chooses
 * the way out of it by cost, every step past the depth gate's own disc measured against the gate's wall.
 * An end too near the coast or the shallows is first moved clear by the engine's end repair, so the disc is
 * the only allowance an end carries.
 *
 * **What still binds, in the disc as everywhere else:** the coastline's water, a hazard ring's filled
 * interior and the depth gate, whose single home is [depthClearsGate].
 */

/**
 * How far an end's disc reaches, in cells: one step per margin's own width, plus one —
 * `ceil(marginM / cellM) + 1`, a constant read off the two sizes, so the scan's cost is bounded by the
 * geometry it waives.
 */
fun endDiscReachCells(marginM: Double, cellM: Double): Int =
    if (cellM <= 0.0) 1 else ceil(marginM / cellM).toInt() + 1

/**
 * **The ends' disc — the pull's own exemption, restored grid-side.**
 *
 * The pull exempts a disc of [marginM] around each end while the grid freed only the end's own cell, so
 * an end standing in margin-water with a ring of margin-land around it is freed and still **enclosed**:
 * its one cell open and every neighbour barred, so this function frees the cells whose **centre** stands
 * within [marginM] of [end], reading the world afresh because a barred cell records *that* it is land,
 * never *why*: a cell opens only where the water is real, the depth clears the gate, and the shore
 * margin is the only rule it breaks. The cell opens with [MultipassGrid.openEndCell], so it takes the base
 * cost and **keeps its zone limit** — the depth gate is never cleared, only the shore margin is waived.
 *
 * **[reachCellM] is the cell the reach is measured in, defaulting to the grid's own.** A window onto a
 * finer layer carries a coarser reach through this argument while its own cell sizes its geometry, so the
 * disc lands in whichever layer holds the end without a second reach rule.
 *
 * **The depth gate's own disc is narrower** ([GateRule.waiverM]). Where a [gate] is in force, a cell its
 * wall painted opens only inside that disc — the gate binds the route past it — and inside it such a cell
 * opens even off the coast's margin, so an end standing beside shallow water is never enclosed by them. Every
 * cell it opens takes its measured distance off the edge, so the search proves its steps on a true reading.
 */
fun openEndDisc(
    grid: MultipassGrid,
    world: MultipassWorld,
    end: LatLng,
    marginM: Double,
    depthGateActive: Boolean,
    minDepthM: Double,
    reachCellM: Double = grid.cellM,
    gate: GateRule? = null
): Int {
    val reach = endDiscReachCells(marginM, reachCellM)
    val origin = grid.cellOf(end.latitude, end.longitude)
    var opened = 0
    for (dRow in -reach..reach) {
        for (dCol in -reach..reach) {
            val row = origin.row + dRow
            val col = origin.col + dCol
            if (!grid.inBounds(row, col)) continue
            if (grid.passable(row, col)) continue
            val centre = grid.center(row, col)
            val fromEndM = SpatialOperations.haversine(centre, end)
            if (fromEndM > marginM) continue
            if (!world.isWater(centre.latitude, centre.longitude)) continue
            if (!depthClearsGate(world.depthAt(centre.latitude, centre.longitude), depthGateActive, minDepthM)) continue
            val coastMarginLand = world.distanceToCoastM(centre.latitude, centre.longitude) < marginM
            if (gate != null) {
                val gateLand = gate.edgeM(centre, gate.hardM) < gate.hardM
                val insideGateDisc = fromEndM <= gate.waiverM
                if (gateLand && !insideGateDisc) continue
                if (!coastMarginLand && !gateLand) continue
            } else if (!coastMarginLand) {
                continue
            }
            grid.openEndCell(row, col)
            // A cell the coast's margin barred took no seed of the gate's distance; it is open water now, so
            // it takes its measured distance, which the search's step guard and the berth read.
            if (gate != null) grid.writeGateDistanceM(row, col, gate.edgeM(centre, gate.seedReachM(grid.cellM)))
            opened++
        }
    }
    return opened
}

/**
 * **The depth gate's own rule, one home for every reader** — the rasterizer's per-cell wall, the end
 * disc's read and the ring's validity question alike: a **known** depth below [minDepthM] blocks, and
 * NoData blocks nothing.
 *
 * [gateActive] is the switch-and-readiness pair the rasterizer itself tests, so a gate out of force
 * clears every cell here exactly as it paints none there. The ring reads the same rule rather than a
 * twin of it, which is what keeps a green target and a barred cell from ever disagreeing.
 */
fun depthClearsGate(sample: DepthSample, gateActive: Boolean, minDepthM: Double): Boolean =
    !gateActive || depthClearsGate(sample.depthM.toDouble(), minDepthM)

/**
 * The same rule for a bare sounding: `NaN` is NoData and clears the gate, and any other depth clears
 * it at or above [minDepthM]. The rasterizer's own source reads this form.
 */
fun depthClearsGate(depthM: Double, minDepthM: Double): Boolean =
    depthM.isNaN() || depthM >= minDepthM
