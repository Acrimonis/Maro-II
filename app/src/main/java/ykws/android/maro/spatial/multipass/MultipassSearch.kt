package ykws.android.maro.spatial.multipass

import kotlinx.coroutines.ensureActive
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.spatial.SpatialOperations
import java.util.PriorityQueue
import kotlin.coroutines.coroutineContext
import kotlin.math.sqrt

/**
 * Corridor-bounded A\* over the water one walk may use: eight neighbours, a diagonal costing `√2` of its
 * own cell — a diagonal covering `√2` cells of distance and time alike — and a g-cost in **seconds**.
 *
 * A cell costs its own `sourceCostSec` plus, where the grid stores a **limit in force**, the seconds
 * that limit costs, which the caller prices through [zonePriceSec]. The grid stores the **strictest**
 * limit per cell — a ring's own interior or the band's own width, whichever is slower — beside the two
 * outside-margin limits, so a band and a ring over the same slow water cost it once and the band's price
 * follows the pass's cursor exactly as a ring's. The price is the caller's because the unit's scaling is
 * the engine's: one multiplier over the time excess a slow cell carries, so re-pricing a whole grid is
 * one multiply per expansion and the search holds no scaling of its own.
 * The heuristic is a **time bound** — `haversine / paceMps` — admissible however dear a cell becomes,
 * since the base is one cell of water at that same pace. [LAND] is impassable; everything else is
 * priced.
 *
 * **One loop, whatever the walk is**: the water arrives as a [WalkWindows], so a uniform pass's single
 * grid and the adaptive grid's chain of windows cross the same door. The single-window case is arithmetic
 * — its slots are that grid's own indices and its centres its own — so every existing answer, tie-break
 * and reading stays where the suite already proves it, and the chain's case is the sparse lattice id the
 * walk itself keeps.
 *
 * Ties break by shorter g-so-far, then by a deterministic slot order — never by heap insertion order — so
 * the same walk always yields the same path. [checkCancelled] is consulted every few hundred expansions
 * (the coroutine's own `ensureActive` by default), so an abandoned drag stops inside the search instead of
 * after it. Exhaustion is a [SearchOutcome] whose `path` is `null`, and the reading beside it says how much
 * water the search walked before it gave up.
 */
object MultipassSearch {

    private val SQRT2 = sqrt(2.0)

    private data class Step(val dr: Int, val dc: Int, val multiplier: Double)

    private val STEPS = listOf(
        Step(-1, 0, 1.0), Step(-1, 1, SQRT2), Step(0, 1, 1.0), Step(1, 1, SQRT2),
        Step(1, 0, 1.0), Step(1, -1, SQRT2), Step(0, -1, 1.0), Step(-1, -1, SQRT2)
    )

    private data class Node(val index: Int, val f: Double, val g: Double)

    /** Cancellation cadence: [checkCancelled] runs once every [CADENCE] expansions. */
    private const val CADENCE = 256

    /**
     * The shortest passable path across [grid] from [start] to [aim] as an ordered list of cell indices,
     * the two ends included — or a `null` path when no free corridor connects them, with the exhaustion
     * reading beside it.
     *
     * **The reading is aggregates, taken once the search has ended**, never per expansion: how many
     * cells were expanded, how many of the walk's own cells were passable at all, and whether the aim's
     * own cell was ever closed. It is what tells a caller *why* the answer is nothing — an aim whose
     * own cell was barred by the grid against water the ring's question accepted, or a way round that
     * lies outside the corridor — without either side keeping a dossier of its own.
     *
     * @param paceMps the pace the heuristic bounds time with — the same pace the grid's base cost was
     *   built from, so the bound stays admissible.
     * @param zonePriceSec the seconds a cell of size `cellM` carrying these limits costs over its
     *   open-water base: the strictest limit in force priced in full, the ring's outside-margin limit and
     *   the band's each at its own fraction — the caller's one price, so re-pricing is one multiply per
     *   expansion and the search holds no scaling of its own. It is handed the **destination cell's own
     *   size**, so a fine band cell is priced at 20 m and a coarse interior cell at 100 m. A grid with no
     *   limit ever calls it, the default answering nothing.
     */
    suspend fun search(
        grid: MultipassGrid,
        start: CellIndex,
        aim: CellIndex,
        paceMps: Double,
        zonePriceSec: (cellM: Double, interiorLimitKn: Double, collarLimitKn: Double, bandCollarLimitKn: Double) -> Double =
            { _, _, _, _ -> 0.0 },
        checkCancelled: suspend () -> Unit = { coroutineContext.ensureActive() },
        depthK: Double = 0.0
    ): SearchOutcome =
        searchWalk(WalkWindows.of(grid), start, aim, paceMps, zonePriceSec, checkCancelled, depthK)

    /**
     * The same search over **[walk]**, one window or several on one lattice: the cell indices the path
     * answers are the walk's own lattice coordinates and carry their layer, and a neighbour no window
     * holds is simply no step.
     *
     * **A two-layer walk crosses its seam.** Beside each layer's own eight neighbours the search asks the
     * walk for the cells **across the seam** — the fixed many-to-one relation the exact `1 : ratio` nesting
     * makes arithmetic instead of a search — and prices that edge **from the two cell centres at the pace**,
     * never from the destination cell's own size. Every other rule of [search] holds verbatim.
     *
     * **The walk reads the fine cell over the coarse copy.** A coarse neighbour a passable fine cell
     * supersedes is no same-layer step: the fine cell over the same water is relaxed instead, priced from
     * the two cell centres exactly as a seam crossing is, so the price the fine grid carries can never be
     * bypassed by a free coarse copy of the same water.
     */
    internal suspend fun searchWalk(
        walk: WalkWindows,
        start: CellIndex,
        aim: CellIndex,
        paceMps: Double,
        zonePriceSec: (cellM: Double, interiorLimitKn: Double, collarLimitKn: Double, bandCollarLimitKn: Double) -> Double =
            { _, _, _, _ -> 0.0 },
        checkCancelled: suspend () -> Unit = { coroutineContext.ensureActive() },
        depthK: Double = 0.0
    ): SearchOutcome {
        val n = walk.size
        val startIdx = walk.slotOf(start.row, start.col)
        val aimIdx = walk.slotOf(aim.row, aim.col)
        if (startIdx < 0 || aimIdx < 0) return SearchOutcome(null, 0, 0, aimClosed = false)
        val aimCenter = walk.centerOf(aimIdx)

        val g = DoubleArray(n) { Double.POSITIVE_INFINITY }
        val h = DoubleArray(n) { Double.NaN }
        val cameFrom = IntArray(n) { -1 }
        val closed = BooleanArray(n)

        g[startIdx] = 0.0
        h[startIdx] = SpatialOperations.haversine(walk.centerOf(startIdx), aimCenter) / paceMps
        h[aimIdx] = 0.0

        val open = PriorityQueue<Node>(compareBy({ it.f }, { it.g }, { it.index }))
        open.add(Node(startIdx, g[startIdx] + h[startIdx], 0.0))

        var expansions = 0
        while (open.isNotEmpty()) {
            val node = open.poll() ?: break
            val idx = node.index
            if (closed[idx]) continue
            if (node.g != g[idx]) continue   // stale heap entry, superseded by a shorter g
            closed[idx] = true
            if (idx == aimIdx) break

            expansions++
            if (expansions % CADENCE == 0) checkCancelled()

            val row = walk.rowOf(idx)
            val col = walk.colOf(idx)
            val layer = walk.layerOf(idx)
            val here = walk.centerOf(idx)
            for (step in STEPS) {
                val tr = row + step.dr
                val tc = col + step.dc
                // The same layer's own neighbour: one cell of that layer's water, at that layer's cell
                // size — **unless a passable fine cell supersedes it.** On the coarse layer the fine copy
                // owns that water, so the coarse cell is not read and the superseding fine cell below
                // carries the crossing; where the fine grid is land the coarse neighbour keeps its role.
                val nIdx = walk.rawSlotOf(layer, tr, tc)
                val over = walk.supersedingSlot(layer, tr, tc)
                if (nIdx >= 0 && over < 0 && !closed[nIdx]) {
                    relax(
                        walk, idx, nIdx, step.multiplier * walk.cellSizeM(layer),
                        g, h, cameFrom, open, aimCenter, paceMps, zonePriceSec, depthK
                    )
                }
                // The superseding fine cell over the same water — the crossing the coarse copy may not
                // make, priced from the two cell centres exactly as a seam crossing is.
                if (over >= 0 && !closed[over]) {
                    relax(
                        walk, idx, over, SpatialOperations.haversine(here, walk.centerOf(over)),
                        g, h, cameFrom, open, aimCenter, paceMps, zonePriceSec, depthK
                    )
                }
                // The seam: the other resolution's cells meeting this cell's face or corner, priced from
                // the two cell centres — the crossing no same-layer step can make.
                val cross = walk.crossLayerSlots(layer, row, col, step.dr, step.dc)
                for (t in 0 until cross.size) {
                    val cIdx = cross[t]
                    if (closed[cIdx]) continue
                    relax(
                        walk, idx, cIdx, SpatialOperations.haversine(here, walk.centerOf(cIdx)),
                        g, h, cameFrom, open, aimCenter, paceMps, zonePriceSec, depthK
                    )
                }
            }
        }

        val passable = walk.passableCount()
        if (!closed[aimIdx]) return SearchOutcome(null, expansions, passable, aimClosed = false)
        val path = ArrayList<CellIndex>()
        var cur = aimIdx
        while (cur != -1) {
            path.add(CellIndex(walk.rowOf(cur), walk.colOf(cur), walk.layerOf(cur)))
            cur = cameFrom[cur]
        }
        path.reverse()
        return SearchOutcome(path, expansions, passable, aimClosed = true, costSec = g[aimIdx])
    }

    /**
     * **One relaxation**: the destination's own rate over the metres the step covers.
     *
     * The destination cell's cost is its `sourceCostSec` plus the zone price the caller reads for **its own
     * size**; divided by that size it is a seconds-per-metre rate, and multiplied by the edge's own length it
     * prices the step. For equal cells `edgeM` is `cellM × multiplier`, so the charge is exactly the
     * `cellSec × multiplier` the uniform walk always paid — `avoid`'s answers cannot move. Across the seam
     * `edgeM` is the two centres' own distance, so a 100 m interior cell and a 20 m band cell meet at a cost
     * neither cell's size alone could name. The heuristic stays admissible because every edge costs at least
     * its distance at the pace, layer by layer.
     */
    private fun relax(
        walk: WalkWindows,
        fromIdx: Int,
        toIdx: Int,
        edgeM: Double,
        g: DoubleArray,
        h: DoubleArray,
        cameFrom: IntArray,
        open: PriorityQueue<Node>,
        aimCenter: LatLng,
        paceMps: Double,
        zonePriceSec: (Double, Double, Double, Double) -> Double,
        depthK: Double
    ) {
        val cell = walk.cell(toIdx)
        if (!cell.passable) return
        val destCellM = walk.cellSizeM(walk.layerOf(toIdx))
        if (destCellM <= 0.0) return
        val interiorLimitKn = walk.limitKn(toIdx)
        val collarLimitKn = walk.collarLimitKn(toIdx)
        val bandCollarLimitKn = walk.bandCollarLimitKn(toIdx)
        var cellSec = cell.sourceCostSec
        if (interiorLimitKn > 0.0 || collarLimitKn > 0.0 || bandCollarLimitKn > 0.0) {
            cellSec += zonePriceSec(destCellM, interiorLimitKn, collarLimitKn, bandCollarLimitKn)
        }
        // The depth band's λ-scaled read: the grid stores the λ-free coefficient, and the pass's own
        // cursor turns it into seconds — the same law the pull's guard prices at its own λ.
        if (depthK > 0.0) {
            val depthCoef = walk.depthCoef(toIdx)
            if (depthCoef > 0.0) cellSec += DepthBandLaw.priceSec(destCellM, depthCoef, depthK)
        }
        val newG = g[fromIdx] + (cellSec / destCellM) * edgeM
        if (newG < g[toIdx]) {
            g[toIdx] = newG
            cameFrom[toIdx] = fromIdx
            if (h[toIdx].isNaN()) {
                h[toIdx] = SpatialOperations.haversine(walk.centerOf(toIdx), aimCenter) / paceMps
            }
            open.add(Node(toIdx, newG + h[toIdx], newG))
        }
    }
}

/**
 * **What one search came to: the path it found, and the water it walked to reach it.**
 *
 * The reading is the search's own refusal account, and it is deliberately aggregates rather than a
 * per-expansion trace: [expansions] is how many cells the search closed and stepped out of,
 * [passableCells] how much of the walk's own water was walkable at all, and [aimClosed] whether the aim's
 * own cell was ever reached. A `null` [path] with `aimClosed = false` is the exhaustion case, and its
 * two counts are what separate an aim the grid barred from a way round lying outside the corridor.
 *
 * Nothing here is consulted by the search itself: it is built once, at the end, and it changes
 * neither the neighbour order nor the heap's tie-breaks, so the same walk still yields the same path.
 */
data class SearchOutcome(
    /** The shortest passable path, the two ends included, or `null` when no free corridor connects them. */
    val path: List<CellIndex>?,
    /** How many cells the search expanded — closed and stepped out of, the aim's own pop excluded. */
    val expansions: Int,
    /** How many of the walk's own cells were passable when the search ran — the water it could walk. */
    val passableCells: Int,
    /** Whether the aim's own cell was ever closed. `false` on an exhaustion is the headline reading. */
    val aimClosed: Boolean,
    /**
     * The aim's own `g` when the search reached it — the path's accumulated cost in seconds, the reading a
     * seam-crossing test compares against that path's own timed length. `0.0` where no path was found.
     */
    val costSec: Double = 0.0
)
