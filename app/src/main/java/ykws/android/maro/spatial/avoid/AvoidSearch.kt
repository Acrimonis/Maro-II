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
 * A cell costs its own `sourceCostSec` plus, where the grid stores a zone **limit**, the seconds that
 * limit costs, which the caller prices through [zonePriceSec]. The price is the caller's because the
 * unit's scaling is the engine's: one multiplier over the time excess a slow cell carries, so
 * re-pricing a whole grid is one multiply per expansion and the search holds no scaling of its own.
 * The heuristic is a **time bound** — `haversine / paceMps` — admissible however dear a cell becomes,
 * since the base is one cell of water at that same pace. [LAND] is impassable; everything else is
 * priced.
 *
 * Ties break by shorter g-so-far, then by a deterministic row-major cell order — never by heap
 * insertion order — so the same grid always yields the same path. [checkCancelled] is consulted
 * every few hundred expansions (the coroutine's own `ensureActive` by default), so an abandoned
 * drag stops inside the search instead of after it. Exhaustion is `null`.
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
     * ends included; `null` when no free corridor connects them.
     *
     * @param paceMps the pace the heuristic bounds time with — the same pace the grid's base cost was
     *   built from, so the bound stays admissible.
     * @param zonePriceSec the seconds a cell carrying this limit costs over its open-water base; a
     *   grid with no zone ever calls it, the default answering nothing.
     */
    suspend fun search(
        grid: AvoidGrid,
        start: CellIndex,
        aim: CellIndex,
        paceMps: Double,
        zonePriceSec: (limitKn: Double) -> Double = { 0.0 },
        checkCancelled: suspend () -> Unit = { coroutineContext.ensureActive() }
    ): List<CellIndex>? {
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
                val limitKn = grid.zoneLimitKn(nr, nc)
                val cellSec =
                    if (limitKn > 0.0) cell.sourceCostSec + zonePriceSec(limitKn) else cell.sourceCostSec
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

        if (!closed[aimIdx]) return null
        val path = ArrayList<CellIndex>()
        var cur = aimIdx
        while (cur != -1) {
            path.add(CellIndex(cur / cols, cur % cols))
            cur = cameFrom[cur]
        }
        path.reverse()
        return path
    }
}
