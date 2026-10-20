package ykws.android.maro.spatial.multipass

import kotlinx.coroutines.ensureActive
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.spatial.SpatialOperations
import java.util.PriorityQueue
import kotlin.coroutines.coroutineContext
import kotlin.math.min
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
     * @param berthK the pass's own λ for the **gate berth** — the grid stores its distance λ-free, so
     *   the cursor is what turns a cell's stored distance into the rung's own. `0.0` reads no berth at
     *   all, which is `avoid`'s case and every pass that prices none.
     * @param berth the berth's law — the rate at each distance off the measured edge — or `null` where
     *   the pass prices none.
     * @param stepGuard the depth gate's step check, or `null` where the gate is out of force: a step whose
     *   straight segment between the two cell centres comes within the gate's wall is no step.
     * @param waiveEndSlots whether [start] and [aim] are a route's own ends, whose own steps the guard reads past
     *   the gate's waiver, or waives where an end is boxed in — `false` for a local re-solve between two
     *   vertices of a line, whose cells are bound like any other.
     */
    suspend fun search(
        grid: MultipassGrid,
        start: CellIndex,
        aim: CellIndex,
        paceMps: Double,
        zonePriceSec: (cellM: Double, interiorLimitKn: Double, collarLimitKn: Double, bandCollarLimitKn: Double) -> Double =
            { _, _, _, _ -> 0.0 },
        checkCancelled: suspend () -> Unit = { coroutineContext.ensureActive() },
        berthK: Double = 0.0,
        berth: BerthLaw? = null,
        stepGuard: StepGuard? = null,
        waiveEndSlots: Boolean = true
    ): SearchOutcome =
        searchWalk(
            WalkWindows.of(grid), start, aim, paceMps, zonePriceSec, checkCancelled,
            berthK, berth, stepGuard, waiveEndSlots
        )

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
     *
     * **The ends' own steps keep the wall past the gate's waiver** wherever an end has a way out that does,
     * each end on its own: the step out of the start's cell is read with the raw start's own leg to that cell,
     * the step into the aim's with the aim's, and a step with a centre in an end's waiver is read past it
     * rather than waived whole. An end the shallows box in — no exit of its cell keeps the wall — walks as the
     * gate always let it, the other end still held; and a held walk that finds nothing is walked again with
     * both ends waived where the held rule refused a step the waiver would have let through into a cell the
     * held walk never reached, so the guard never turns a route into no route and a search with no way at
     * all is walked once.
     * [SearchOutcome.strictStart] and [SearchOutcome.strictAim] say how the answering walk read each end.
     */
    internal suspend fun searchWalk(
        walk: WalkWindows,
        start: CellIndex,
        aim: CellIndex,
        paceMps: Double,
        zonePriceSec: (cellM: Double, interiorLimitKn: Double, collarLimitKn: Double, bandCollarLimitKn: Double) -> Double =
            { _, _, _, _ -> 0.0 },
        checkCancelled: suspend () -> Unit = { coroutineContext.ensureActive() },
        berthK: Double = 0.0,
        berth: BerthLaw? = null,
        stepGuard: StepGuard? = null,
        waiveEndSlots: Boolean = true
    ): SearchOutcome {
        val startIdx = walk.slotOfEnd(start)
        val aimIdx = walk.slotOfEnd(aim)
        if (startIdx < 0 || aimIdx < 0) return SearchOutcome(null, 0, 0, aimClosed = false)
        // The start's own reading, for the trace: its layer and how many of its steps lead to water.
        var startOpen = 0
        forEachStep(walk, startIdx, { !walk.passable(it) }) { _, _ -> startOpen++ }
        val startLayer = walk.layerOf(startIdx)
        // A local re-solve between two line vertices binds its ends like any other cell.
        if (stepGuard == null || !waiveEndSlots) {
            return walkOnce(
                walk, startIdx, aimIdx, paceMps, zonePriceSec, checkCancelled, berthK, berth, stepGuard,
                EndRule.BOUND
            ).copy(startLayer = startLayer, startOpenSteps = startOpen)
        }
        val waived = EndRule(startIdx, aimIdx, strictStart = false, strictAim = false)
        val rule = if (startIdx == aimIdx) waived else EndRule(
            startIdx, aimIdx,
            strictStart = hasExit(
                walk, stepGuard, EndRule(startIdx, aimIdx, strictStart = true, strictAim = false), startIdx, leaving = true
            ),
            strictAim = hasExit(
                walk, stepGuard, EndRule(startIdx, aimIdx, strictStart = false, strictAim = true), aimIdx, leaving = false
            )
        )
        if (!rule.strictStart && !rule.strictAim) {
            return walkOnce(
                walk, startIdx, aimIdx, paceMps, zonePriceSec, checkCancelled, berthK, berth, stepGuard, waived
            ).copy(startLayer = startLayer, startOpenSteps = startOpen)
        }
        stepGuard.heldRefused.clear()
        // A held walk that finds nothing is walked again only where it refused a step the old waiver lets
        // through **into a cell it never reached**: every other refused step leads nowhere new.
        var reopens = false
        val held = walkOnce(
            walk, startIdx, aimIdx, paceMps, zonePriceSec, checkCancelled, berthK, berth, stepGuard, rule
        ) { closed -> reopens = stepGuard.heldRefused.any { !closed[it] } }
            .copy(
                strictStart = rule.strictStart, strictAim = rule.strictAim,
                startLayer = startLayer, startOpenSteps = startOpen
            )
        if (held.path != null || !reopens) return held
        val again = walkOnce(
            walk, startIdx, aimIdx, paceMps, zonePriceSec, checkCancelled, berthK, berth, stepGuard, waived
        )
        return again.copy(
            expansions = held.expansions + again.expansions,
            startLayer = startLayer, startOpenSteps = startOpen, rewalked = true
        )
    }

    /**
     * Whether the end at [endIdx] has one exit the guard lets through under [ends] — a step out of it where
     * [leaving], into it otherwise — the boxed end's test, read before any walk. The aim's entries are read
     * as its own steps reversed, which the seam's one-way crossings make approximate; the waived re-walk
     * covers what it misses.
     */
    private fun hasExit(walk: WalkWindows, guard: StepGuard, ends: EndRule, endIdx: Int, leaving: Boolean): Boolean {
        var found = false
        forEachStep(walk, endIdx, { found || !walk.passable(it) }) { other, edgeM ->
            if (!found) {
                found = if (leaving) {
                    guard.allows(walk, endIdx, other, edgeM, ends)
                } else {
                    guard.allows(walk, other, endIdx, edgeM, ends)
                }
            }
        }
        return found
    }

    /**
     * **Every step out of [idx]** — the same layer's eight neighbours, the fine cell superseding a coarse
     * one, and the cells across the seam — handed to [action] with the step's own length, in the one order
     * the walk's tie-breaks rely on. A destination [skip] names is passed over before its length is read.
     */
    private inline fun forEachStep(
        walk: WalkWindows,
        idx: Int,
        skip: (toIdx: Int) -> Boolean,
        action: (toIdx: Int, edgeM: Double) -> Unit
    ) {
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
            if (nIdx >= 0 && over < 0 && !skip(nIdx)) action(nIdx, step.multiplier * walk.cellSizeM(layer))
            // The superseding fine cell over the same water — the crossing the coarse copy may not
            // make, priced from the two cell centres exactly as a seam crossing is.
            if (over >= 0 && !skip(over)) action(over, SpatialOperations.haversine(here, walk.centerOf(over)))
            // The seam: the other resolution's cells meeting this cell's face or corner, priced from
            // the two cell centres — the crossing no same-layer step can make.
            val cross = walk.crossLayerSlots(layer, row, col, step.dr, step.dc)
            for (t in 0 until cross.size) {
                val cIdx = cross[t]
                if (!skip(cIdx)) action(cIdx, SpatialOperations.haversine(here, walk.centerOf(cIdx)))
            }
        }
    }

    /**
     * One A\* walk from [startIdx] to [aimIdx], the guard reading the ends through [ends]. A walk that finds
     * nothing hands [onExhausted] the cells it closed.
     */
    private suspend fun walkOnce(
        walk: WalkWindows,
        startIdx: Int,
        aimIdx: Int,
        paceMps: Double,
        zonePriceSec: (Double, Double, Double, Double) -> Double,
        checkCancelled: suspend () -> Unit,
        berthK: Double,
        berth: BerthLaw?,
        stepGuard: StepGuard?,
        ends: EndRule,
        onExhausted: ((closed: BooleanArray) -> Unit)? = null
    ): SearchOutcome {
        val n = walk.size
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

            forEachStep(walk, idx, { closed[it] }) { toIdx, edgeM ->
                relax(
                    walk, idx, toIdx, edgeM, g, h, cameFrom, open, aimCenter, paceMps, zonePriceSec,
                    berthK, berth, stepGuard, ends
                )
            }
        }

        val passable = walk.passableCount()
        if (!closed[aimIdx]) {
            onExhausted?.invoke(closed)
            return SearchOutcome(null, expansions, passable, aimClosed = false)
        }
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
        berthK: Double,
        berth: BerthLaw?,
        stepGuard: StepGuard?,
        ends: EndRule
    ) {
        if (!walk.passable(toIdx)) return
        val destCellM = walk.cellSizeM(walk.layerOf(toIdx))
        if (destCellM <= 0.0) return
        val interiorLimitKn = walk.limitKn(toIdx)
        val collarLimitKn = walk.collarLimitKn(toIdx)
        val bandCollarLimitKn = walk.bandCollarLimitKn(toIdx)
        var cellSec = walk.sourceCostSec(toIdx)
        if (interiorLimitKn > 0.0 || collarLimitKn > 0.0 || bandCollarLimitKn > 0.0) {
            cellSec += zonePriceSec(destCellM, interiorLimitKn, collarLimitKn, bandCollarLimitKn)
        }
        // The berth's own read: the grid stores its distance, and the pass's cursor turns it into the
        // rung's seconds — the very law the pull's guard prices a point at.
        if (berthK > 0.0 && berth != null) {
            val berthSec = berth.priceSecAt(walk.gateDistanceM(toIdx), destCellM)
            if (berthSec > 0.0) cellSec += berthK * berthSec
        }
        val newG = g[fromIdx] + (cellSec / destCellM) * edgeM
        if (newG < g[toIdx]) {
            // The depth gate's own step, asked only of a step that would improve the path: a step whose
            // straight segment comes within the wall is no step, the ends' own read through [ends].
            if (stepGuard != null && !stepGuard.allows(walk, fromIdx, toIdx, edgeM, ends)) return
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
    val costSec: Double = 0.0,
    /**
     * Whether the answering walk held the route's start to the gate's wall past its waiver — `false` where
     * the start was boxed in, the waived re-walk answered, or no guard ran.
     */
    val strictStart: Boolean = false,
    /** The same for the route's aim. */
    val strictAim: Boolean = false,
    /** The start slot's layer (0 the coarse interior), `-1` where no start slot was found — a trace reading. */
    val startLayer: Int = -1,
    /** How many of the start slot's own steps lead into passable water — `0` is a start with no way out. */
    val startOpenSteps: Int = -1,
    /** Whether the answering walk was the waived re-walk after a held walk found nothing. */
    val rewalked: Boolean = false
)

/**
 * **How the step guard reads a route's own ends** — their two slots, and for each whether its own steps keep
 * the wall past the waiver ([strictStart], [strictAim]) or are waived whole, the gate's rule before the ends
 * were held to it. [BOUND] names no end: a local re-solve, whose every step is read alike.
 */
internal class EndRule(val startIdx: Int, val aimIdx: Int, val strictStart: Boolean, val strictAim: Boolean) {
    companion object {
        val BOUND = EndRule(-1, -1, strictStart = true, strictAim = true)
    }
}

/**
 * **The depth gate's check on one search step** — whether the straight segment between two cell centres
 * keeps the gate's wall ([GateRule.hardM]) off the measured edge.
 *
 * It is what lets every raw leg the A\* hands the pull be trusted: the pull keeps a refused immediate
 * step rather than lose the path, so a leg between two legal centres that grazes a rock would otherwise
 * reach the drawn line. **Most steps read nothing**: a step whose two cells both stand at least
 * `hardM + edgeM / 2` off the edge is clear by the distance's own 1-Lipschitz bound, read from the grid's
 * stored field — trusted only where the raster wrote it exactly ([GateRule.seedReachM] at the cell's own
 * size), which every same-layer step is. Only a step near the edge or a step across the seam pays a measured
 * read: one segment read, or a read cut at the waiver's rim where a centre stands in it.
 *
 * A step with a centre inside the gate's waiver — within [GateRule.waiverM] of a raw end — is read past
 * the waiver ([GateRule.keepsWall]), and a step out of the start's own cell or into
 * the aim's is read with the raw end's own leg to that cell, so the drawn line keeps the wall from the
 * waiver's rim on. Where an end is boxed in, the search walks that end under the old rule — a centre in its
 * waiver waives the whole step and its own steps are free — so the end may stand in shallow-side water and
 * still be left. **A guard serves one search at a time**: it records where the held rule refused a step the
 * old waiver would have let through ([heldRefused]), which the search reads to decide a re-walk.
 */
class StepGuard(
    private val gate: GateRule,
    private val start: LatLng,
    private val aim: LatLng
) {
    /** The destinations of the steps the held rule refused that the old waiver would have let through. */
    internal val heldRefused = ArrayList<Int>()

    /** The raw end's own leg to its cell's centre, read once per slot: `[slot, verdict]` for each end. */
    private val startLeg = intArrayOf(-1, 0)
    private val aimLeg = intArrayOf(-1, 0)

    /** Whether the step [fromIdx] → [toIdx] may be taken, the route's own ends read through [ends]. */
    internal fun allows(walk: WalkWindows, fromIdx: Int, toIdx: Int, edgeM: Double, ends: EndRule): Boolean {
        val leavesStart = fromIdx == ends.startIdx
        val entersAim = toIdx == ends.aimIdx
        if ((leavesStart && !ends.strictStart) || (entersAim && !ends.strictAim)) return true
        val allowed = (!leavesStart || endLegClear(walk, fromIdx, start, startLeg)) &&
            (!entersAim || endLegClear(walk, toIdx, aim, aimLeg)) &&
            clear(walk, fromIdx, toIdx, edgeM, ends)
        if (!allowed && ends.startIdx >= 0 &&
            (leavesStart || entersAim || gate.waives(walk.centerOf(fromIdx), start, aim) ||
                gate.waives(walk.centerOf(toIdx), start, aim))
        ) {
            heldRefused.add(toIdx)
        }
        return allowed
    }

    /**
     * Whether the straight segment between the two cell centres keeps the wall — the stored distances'
     * proof first, then one measured read. An end [ends] does not hold keeps the old rule: a step with a
     * centre in that end's waiver is waived whole rather than read past it.
     */
    internal fun clear(walk: WalkWindows, fromIdx: Int, toIdx: Int, edgeM: Double, ends: EndRule = EndRule.BOUND): Boolean {
        val needM = gate.hardM + edgeM / 2.0
        val trustedM = min(
            gate.seedReachM(walk.cellSizeM(walk.layerOf(fromIdx))),
            gate.seedReachM(walk.cellSizeM(walk.layerOf(toIdx)))
        )
        if (needM <= trustedM && min(walk.gateDistanceM(fromIdx), walk.gateDistanceM(toIdx)) >= needM) {
            return true
        }
        val a = walk.centerOf(fromIdx)
        val b = walk.centerOf(toIdx)
        if (!ends.strictStart && (gate.waivesNear(a, start) || gate.waivesNear(b, start))) {
            return true
        }
        if (!ends.strictAim && (gate.waivesNear(a, aim) || gate.waivesNear(b, aim))) {
            return true
        }
        return gate.keepsWall(a, b, start, aim)
    }

    private fun endLegClear(walk: WalkWindows, slot: Int, end: LatLng, memo: IntArray): Boolean {
        if (memo[0] != slot) {
            memo[0] = slot
            memo[1] = if (gate.keepsWall(end, walk.centerOf(slot), start, aim)) 1 else 0
        }
        return memo[1] == 1
    }
}
