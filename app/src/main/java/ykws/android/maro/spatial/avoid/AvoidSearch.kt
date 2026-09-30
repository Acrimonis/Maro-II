package ykws.android.maro.spatial.avoid

import kotlinx.coroutines.ensureActive
import ykws.android.maro.spatial.SpatialOperations
import java.util.PriorityQueue
import kotlin.coroutines.coroutineContext
import kotlin.math.sqrt

/**
 * Corridor-bounded A* over [AvoidGrid]: eight neighbours, a diagonal costing `√2` of its own cell — a
 * diagonal covering `√2` cells of distance and time alike — and a g-cost in **seconds**.
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
 * Ties break by shorter g-so-far, then by a deterministic row-major cell order — never by heap
 * insertion order — so the same grid always yields the same path. [checkCancelled] is consulted
 * every few hundred expansions (the coroutine's own `ensureActive` by default), so an abandoned
 * drag stops inside the search instead of after it. Exhaustion is a [SearchOutcome] whose `path` is
 * `null`, and the reading beside it says how much water the search walked before it gave up.
 */
object AvoidSearch {

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
     * The shortest passable path from [start] to [aim] as an ordered list of cell indices, the two
     * ends included — or a `null` path when no free corridor connects them, with the exhaustion
     * reading beside it.
     *
     * **The reading is aggregates, taken once the search has ended**, never per expansion: how many
     * cells were expanded, how many of the grid's cells were passable at all, and whether the aim's
     * own cell was ever closed. It is what tells a caller *why* the answer is nothing — an aim whose
     * own cell was barred by the grid against water the ring's question accepted, or a way round that
     * lies outside the corridor — without either side keeping a dossier of its own.
     *
     * @param paceMps the pace the heuristic bounds time with — the same pace the grid's base cost was
     *   built from, so the bound stays admissible.
     * @param zonePriceSec the seconds a cell carrying these limits costs over its open-water base: the
     *   strictest limit in force priced in full, the ring's outside-margin limit and the band's each at
     *   its own fraction — the caller's one price, so re-pricing is one multiply per expansion and the
     *   search holds no scaling of its own. A grid with no limit ever calls it, the default answering
     *   nothing.
     */
    suspend fun search(
        grid: AvoidGrid,
        start: CellIndex,
        aim: CellIndex,
        paceMps: Double,
        zonePriceSec: (interiorLimitKn: Double, collarLimitKn: Double, bandCollarLimitKn: Double) -> Double =
            { _, _, _ -> 0.0 },
        checkCancelled: suspend () -> Unit = { coroutineContext.ensureActive() }
    ): SearchOutcome {
        val cols = grid.cols
        val rows = grid.rows
        val n = rows * cols
        val startIdx = grid.index(start.row, start.col)
        val aimIdx = grid.index(aim.row, aim.col)
        val aimCenter = grid.center(aim.row, aim.col)

        val g = DoubleArray(n) { Double.POSITIVE_INFINITY }
        val h = DoubleArray(n) { Double.NaN }
        val cameFrom = IntArray(n) { -1 }
        val closed = BooleanArray(n)

        g[startIdx] = 0.0
        h[startIdx] = SpatialOperations.haversine(grid.center(start.row, start.col), aimCenter) / paceMps
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

            val row = idx / cols
            val col = idx % cols
            for (step in STEPS) {
                val nr = row + step.dr
                val nc = col + step.dc
                if (nr !in 0 until rows || nc !in 0 until cols) continue
                val nIdx = nr * cols + nc
                if (closed[nIdx]) continue
                val cell = grid.cell(nr, nc)
                if (!cell.passable) continue
                val interiorLimitKn = grid.limitKn(nr, nc)
                val collarLimitKn = grid.collarLimitKn(nr, nc)
                val bandCollarLimitKn = grid.bandCollarLimitKn(nr, nc)
                val cellSec =
                    if (interiorLimitKn > 0.0 || collarLimitKn > 0.0 || bandCollarLimitKn > 0.0) {
                        cell.sourceCostSec + zonePriceSec(interiorLimitKn, collarLimitKn, bandCollarLimitKn)
                    } else {
                        cell.sourceCostSec
                    }
                val newG = g[idx] + cellSec * step.multiplier
                if (newG < g[nIdx]) {
                    g[nIdx] = newG
                    cameFrom[nIdx] = idx
                    if (h[nIdx].isNaN()) {
                        h[nIdx] = SpatialOperations.haversine(grid.center(nr, nc), aimCenter) / paceMps
                    }
                    open.add(Node(nIdx, newG + h[nIdx], newG))
                }
            }
        }

        val passable = passableCellCount(grid)
        if (!closed[aimIdx]) return SearchOutcome(null, expansions, passable, aimClosed = false)
        val path = ArrayList<CellIndex>()
        var cur = aimIdx
        while (cur != -1) {
            path.add(CellIndex(cur / cols, cur % cols))
            cur = cameFrom[cur]
        }
        path.reverse()
        return SearchOutcome(path, expansions, passable, aimClosed = true)
    }

    /** One linear read of the grid's passable cells — taken once, when the search ends, never per expansion. */
    private fun passableCellCount(grid: AvoidGrid): Int {
        var count = 0
        for (row in 0 until grid.rows) {
            for (col in 0 until grid.cols) {
                if (grid.cell(row, col).passable) count++
            }
        }
        return count
    }
}

/**
 * **What one search came to: the path it found, and the water it walked to reach it.**
 *
 * The reading is the search's own refusal account, and it is deliberately aggregates rather than a
 * per-expansion trace: [expansions] is how many cells the search closed and stepped out of,
 * [passableCells] how much of the grid was walkable at all, and [aimClosed] whether the aim's own
 * cell was ever reached. A `null` [path] with `aimClosed = false` is the exhaustion case, and its
 * two counts are what separate an aim the grid barred from a way round lying outside the corridor.
 *
 * Nothing here is consulted by the search itself: it is built once, at the end, and it changes
 * neither the neighbour order nor the heap's tie-breaks, so the same grid still yields the same path.
 */
data class SearchOutcome(
    /** The shortest passable path, the two ends included, or `null` when no free corridor connects them. */
    val path: List<CellIndex>?,
    /** How many cells the search expanded — closed and stepped out of, the aim's own pop excluded. */
    val expansions: Int,
    /** How many of the grid's cells were passable when the search ran — the water it could walk. */
    val passableCells: Int,
    /** Whether the aim's own cell was ever closed. `false` on an exhaustion is the headline reading. */
    val aimClosed: Boolean
)
