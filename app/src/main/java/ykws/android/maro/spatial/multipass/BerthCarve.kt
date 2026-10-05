package ykws.android.maro.spatial.multipass

import ykws.android.maro.data.model.DepthSample
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.spatial.SpatialOperations
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos

/**
 * **The berth carve (F7) — the approach each end is granted, and the one rule that grants it.**
 *
 * The shore margin is a rule for the **route**, not for the destination: it is waived along the first
 * and last leg — the approach — and nowhere else. In a grid where no leg exists yet the water the
 * approach needs must therefore *exist*, so this file finds it: from an end standing in margin-water
 * it walks the eight compass directions in a [CarveDirection]-declared order and stops at the first
 * straight segment whose **destination cell's centre is legal water** while **every intermediate cell
 * is margin-land**.
 *
 * **Why the reads are re-taken here rather than read off the grid.** A post-sweep `LAND` cell records
 * *that* it is land, never *why*: the coastline's margin, a hazard ring's interior and the depth
 * gate all paint the same state. The margin's own contribution is therefore re-read per cell — the
 * three reads [legalWater] and [marginLand] share — which is why the carve belongs **after** the
 * rasterisation, beside the grid's own `forceFree`, and never inside the sweep.
 *
 * **The one rule, three readers.** *The margin is waived on the approach to an end* is read by the
 * grid (the carved cells opened), by the pull's exemption (the stretch's samples) and by the ring's
 * own validity question ([carveBerth] run for the point being aimed) — one statement, one home.
 *
 * **What still binds, in the carve as everywhere else:** the coastline's water, a hazard ring's filled
 * interior (never water, so never margin-land) and the depth gate, whose single home is
 * [depthClearsGate]. A carve therefore never crosses a rock or a shallow patch, and it **fails
 * honestly** rather than blunting the fence: an end walled by land or by the gate answers
 * [CARVE_NO_LEGAL_WATER].
 */

/**
 * **The eight compass steps a berth carve tries, in this fixed deterministic order** — north first,
 * then clockwise, so two runs over one world choose the same direction.
 *
 * [dRow] is the lattice's latitude step and [dCol] its longitude step, both `+1` toward the north and
 * the east: the same pair serves the grid's own rows and columns and the metric lattice the ring's
 * question builds, so neither reader owns a direction table of its own.
 */
enum class CarveDirection(val label: String, val dRow: Int, val dCol: Int) {
    N("N", 1, 0),
    NE("NE", 1, 1),
    E("E", 0, 1),
    SE("SE", -1, 1),
    S("S", -1, 0),
    SW("SW", -1, -1),
    W("W", 0, -1),
    NW("NW", 1, -1)
}

/** The end already stands in legal water — there is nothing to carve, and the approach is trivial. */
const val CARVE_END_CLEAR = "end-clear"

/** No direction reached legal water through margin-water alone within the carve's own reach. */
const val CARVE_NO_LEGAL_WATER = "no-legal-water"

/**
 * **One end's carve, kept as a value**: the direction that qualified, how many lattice steps it took
 * to reach legal water, the stretch itself (the end's own point to the legal cell), the failure's
 * reason and the stretch's length in metres.
 *
 * The stretch is a **polyline**, not a radius and not a cell count, because it is read twice: the grid
 * opens its cells, and the pull exempts the samples standing on it. A carve longer than `marginM` is
 * exactly the case the pull's end-disc exemption cannot cover, so the shape has to travel.
 */
data class BerthCarve(
    /** The direction whose segment qualified, or `null` where none did. */
    val direction: CarveDirection?,
    /** How many lattice steps to the legal cell, 0 where none was reached. */
    val stepsToWater: Int,
    /** The stretch — the end's point to the legal cell's centre; empty when the carve found none. */
    val points: List<LatLng>,
    /** Why it failed, `""` on success and [CARVE_END_CLEAR] where the end was legal already. */
    val reason: String,
    /** The stretch's own length (m), 0.0 where there is no stretch. */
    val lengthM: Double
) {

    /**
     * Whether the end has an approach at all — the ring's own question, and the reason a red target
     * can never be raised for a berth the carve can enter: true on a found direction, and true
     * trivially where the end itself already stands in legal water.
     */
    val reachable: Boolean get() = direction != null || reason == CARVE_END_CLEAR
}

/**
 * How far a berth carve may reach before it fails, in cells: one step per margin's own width, plus the
 * one that lands in legal water — `ceil(marginM / cellM) + 1`.
 *
 * It is a **constant read off the two sizes** rather than a preference of its own, so the scan's cost
 * is bounded by the geometry it waives: at the shipped 50 m margin and 50 m cell it is two cells
 * (100 m), and a coarser cell or a tighter margin shortens it.
 */
fun carveReachCells(marginM: Double, cellM: Double): Int =
    if (cellM <= 0.0) 1 else ceil(marginM / cellM).toInt() + 1

/**
 * **The ends' disc (F7's second half) — the pull's own exemption, restored grid-side.**
 *
 * The pull exempts a disc of [marginM] around each end while the grid freed only the end's own cell, so
 * an end standing in margin-water with a ring of margin-land around it is freed and still **enclosed**:
 * its one cell open and every neighbour barred. The carve cannot see that shape — its own read can call
 * the end's cell legal water and open nothing — so this function frees the cells whose **centre** stands
 * within [marginM] of [end], reading the world afresh because a barred cell records *that* it is land,
 * never *why*: a cell opens only where the water is real, the depth clears the gate, and the shore
 * margin is the only rule it breaks. The cell opens with [MultipassGrid.openCarve], so it takes the base
 * cost and **keeps its zone limit** — the depth gate is never cleared, only the shore margin is waived.
 *
 * **[reachCellM] is the cell the reach is measured in, defaulting to the grid's own.** A window onto a
 * finer layer carries a coarser reach through this argument while its own cell sizes its geometry, so the
 * disc lands in whichever layer holds the end without a second reach rule.
 */
fun openEndDisc(
    grid: MultipassGrid,
    world: MultipassWorld,
    end: LatLng,
    marginM: Double,
    depthGateActive: Boolean,
    minDepthM: Double,
    reachCellM: Double = grid.cellM
): Int {
    val reach = carveReachCells(marginM, reachCellM)
    val origin = grid.cellOf(end.latitude, end.longitude)
    var opened = 0
    for (dRow in -reach..reach) {
        for (dCol in -reach..reach) {
            val row = origin.row + dRow
            val col = origin.col + dCol
            if (!grid.inBounds(row, col)) continue
            if (grid.cell(row, col).passable) continue
            val centre = grid.center(row, col)
            if (SpatialOperations.haversine(centre, end) > marginM) continue
            if (!world.isWater(centre.latitude, centre.longitude)) continue
            if (!depthClearsGate(world.depthAt(centre.latitude, centre.longitude), depthGateActive, minDepthM)) continue
            if (world.distanceToCoastM(centre.latitude, centre.longitude) >= marginM) continue
            grid.openCarve(row, col)
            opened++
        }
    }
    return opened
}

/**
 * **The depth gate's own rule, one home for every reader** — the rasterizer's per-cell wall, the
 * berth carve's two reads and the ring's validity question alike: a **known** depth below [minDepthM]
 * blocks, and NoData blocks nothing.
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

/**
 * The lattice a carve walks when there is **no grid to walk** — the ring's own question at aiming
 * time: [cellM]-metre steps from [anchor], converted at [anchor]'s own latitude. The grid's reader
 * hands [carveBerth] its cell centres instead, so both read the same eight directions off their own
 * lattice and neither invents a second scan.
 */
fun metricCarveLattice(anchor: LatLng, cellM: Double): (Int, CarveDirection) -> LatLng {
    val mPerDegLat = SpatialOperations.EARTH_RADIUS_M * PI / 180.0
    val mPerDegLon = mPerDegLat * cos(Math.toRadians(anchor.latitude))
    val degLat = cellM / mPerDegLat
    val degLon = cellM / mPerDegLon
    return { steps, direction ->
        LatLng(
            anchor.latitude + steps * direction.dRow * degLat,
            anchor.longitude + steps * direction.dCol * degLon
        )
    }
}

/**
 * **The bounded eight-direction scan**, run for one end: the first direction whose straight segment
 * reaches legal water through margin-land alone wins, and its stretch is returned.
 *
 * [anchor] is the lattice's own origin — the end's cell centre when a grid is read, the point itself
 * when the ring asks — and [stepAt] answers the lattice point [steps] cells along [direction], or
 * `null` where that point lies off the lattice (the grid's own bounds), which fails the direction.
 *
 * A direction fails as soon as one of its cells is neither margin-land nor legal water — true land, a
 * ring's interior (never water) and a depth-gated cell all stop it — and every direction failing
 * answers [CARVE_NO_LEGAL_WATER]. An [anchor] that is legal water already is [CARVE_END_CLEAR]: there
 * is nothing to carve.
 */
fun carveBerth(
    world: MultipassWorld,
    anchor: LatLng,
    marginM: Double,
    reachCells: Int,
    depthGateActive: Boolean,
    minDepthM: Double,
    stepAt: (steps: Int, direction: CarveDirection) -> LatLng?
): BerthCarve {
    if (legalWater(world, anchor, marginM, depthGateActive, minDepthM)) {
        return BerthCarve(null, 0, emptyList(), CARVE_END_CLEAR, 0.0)
    }
    for (direction in CarveDirection.entries) {
        var steps = 0
        while (steps < reachCells) {
            val at = stepAt(steps + 1, direction) ?: break
            if (legalWater(world, at, marginM, depthGateActive, minDepthM)) {
                return BerthCarve(
                    direction,
                    steps + 1,
                    listOf(anchor, at),
                    "",
                    SpatialOperations.haversine(anchor, at)
                )
            }
            if (!marginLand(world, at, marginM, depthGateActive, minDepthM)) break
            steps++
        }
    }
    return BerthCarve(null, 0, emptyList(), CARVE_NO_LEGAL_WATER, 0.0)
}

/** The destination's test: water, deep enough, and clear of the margin the approach waives. */
private fun legalWater(
    world: MultipassWorld,
    at: LatLng,
    marginM: Double,
    depthGateActive: Boolean,
    minDepthM: Double
): Boolean =
    world.isWater(at.latitude, at.longitude) &&
        world.distanceToCoastM(at.latitude, at.longitude) >= marginM &&
        depthClearsGate(world.depthAt(at.latitude, at.longitude), depthGateActive, minDepthM)

/**
 * The intermediate's test — **margin-land**: water the margin has taken that is not the depth gate's
 * own land, which is the only water a carve may cross.
 */
private fun marginLand(
    world: MultipassWorld,
    at: LatLng,
    marginM: Double,
    depthGateActive: Boolean,
    minDepthM: Double
): Boolean =
    world.isWater(at.latitude, at.longitude) &&
        world.distanceToCoastM(at.latitude, at.longitude) < marginM &&
        depthClearsGate(world.depthAt(at.latitude, at.longitude), depthGateActive, minDepthM)
