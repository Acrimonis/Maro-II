package ykws.android.maro.spatial.multipass

import kotlinx.coroutines.ensureActive
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.spatial.SpatialOperations
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
 * The heuristic is a **time bound** — `haversine / paceMps`, scaled by the walk's own
 * [WalkWindows.heuristicScale] so a nominal cell sized at one reference latitude can never charge a step
 * less than the heuristic reads for it — admissible however dear a cell becomes, since the base is one
 * cell of water at that same pace. [LAND] is impassable; everything else is priced.
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
/**
 * **The caller's own price of a cell carrying limits** — the seconds a cell of `cellM` costs over its
 * open-water base for the strictest limit in force, the ring's collar limit and the band's collar limit.
 * A `fun interface` rather than a function type on purpose: a `(Double, Double, Double, Double) -> Double`
 * is a generic `Function4` whose every call boxes four arguments and the result, and the search asks it on
 * most relaxations of a coastal corridor; this keeps the call a primitive JVM signature. A Kotlin lambda
 * converts to it at the call site unchanged.
 */
fun interface ZonePrice {
    fun secAt(cellM: Double, interiorLimitKn: Double, collarLimitKn: Double, bandCollarLimitKn: Double): Double

    companion object {
        /** A grid with no limit ever priced — the default that answers nothing. */
        val NONE: ZonePrice = ZonePrice { _, _, _, _ -> 0.0 }
    }
}

/**
 * **What one cell of the walk costs, whole, at its own size** — its `sourceCostSec`, the zone price the caller reads
 * for its limits at that size, and the gate berth at the pass's λ. The step's relaxation and the chord's read
 * ([ChordReader]) both price a cell here, so a chord can bypass no price a step pays.
 */
internal class CellPrice(
    private val zonePriceSec: ZonePrice,
    private val berthK: Double,
    private val berth: BerthLaw?
) {
    /** The seconds the cell at [slot], of [cellM], costs whole. */
    fun cellSec(walk: WalkWindows, slot: Int, cellM: Double): Double {
        val interiorLimitKn = walk.limitKn(slot)
        val collarLimitKn = walk.collarLimitKn(slot)
        val bandCollarLimitKn = walk.bandCollarLimitKn(slot)
        var cellSec = walk.sourceCostSec(slot)
        if (interiorLimitKn > 0.0 || collarLimitKn > 0.0 || bandCollarLimitKn > 0.0) {
            cellSec += zonePriceSec.secAt(cellM, interiorLimitKn, collarLimitKn, bandCollarLimitKn)
        }
        // The berth's own read: the grid stores its distance, and the pass's cursor turns it into the
        // rung's seconds — the very law the pull's guard prices a point at.
        if (berthK > 0.0 && berth != null) {
            val berthSec = berth.priceSecAt(walk.gateDistanceM(slot), cellM)
            if (berthSec > 0.0) cellSec += berthK * berthSec
        }
        return cellSec
    }
}

/**
 * **A reading of a walk's expansions, in order** — each expanded slot and how many steps out of it the walk read.
 * An instrument's door (the any-angle plan's P1 cost gate reads chords over a walk's own expansions): the walk
 * neither consults nor waits on it.
 */
internal fun interface ExpansionProbe {
    fun expanded(slot: Int, steps: Int)
}

/**
 * **What a push prices a chord at**, before the pop reads it — the prototype's own knob
 * (`route.search.anyAngle.estimate`), each a different search order:
 * - [STEP] — the grid's own step price from the expander: pessimistic, the heap's order the staircase's, the
 *   chord credited only at pop — so a cell closed before its neighbour's credit arrives keeps the dearer way;
 * - [BOUND] — the straight distance at the pace, scaled as the heuristic is (§13.1): a lower bound on any chord,
 *   optimistic through priced water, the chord read at pop;
 * - [FULL] — the chord itself, read at push: exact, no lazy pop, one read per neighbour instead of per expansion.
 */
internal enum class ChordEstimate { STEP, BOUND, FULL }

/**
 * **The any-angle walk's rule** (the Evolutive + row's walk): the Theta\* over the walk, with [reachCells] — how far
 * back, in coarse cells, a node may take its parent, `0` no cap — the push's [estimate], and whether a lazy pop that
 * lowers a node's cost **reopens** the closed neighbours it would now reach cheaper (`route.search.anyAngle.reopen`).
 * A walk handed `null` is today's A\*.
 */
internal class AnyAngleRule(
    val reachCells: Int,
    val estimate: ChordEstimate = ChordEstimate.STEP,
    val reopen: Boolean = false
)

object MultipassSearch {

    private val SQRT2 = sqrt(2.0)

    /** The eight steps, in the one order the tie-breaks rely on, as primitive arrays: no iterator per expansion. */
    private val STEP_DR = intArrayOf(-1, -1, 0, 1, 1, 1, 0, -1)
    private val STEP_DC = intArrayOf(0, 1, 1, 1, 0, -1, -1, -1)
    private val STEP_MULTIPLIER = doubleArrayOf(1.0, SQRT2, 1.0, SQRT2, 1.0, SQRT2, 1.0, SQRT2)

    /** Cancellation cadence: [checkCancelled] runs once every [CADENCE] expansions. */
    private const val CADENCE = 256

    /** The relative margin inside which a chord and the steps it equals are one cost — rounding, not a price. */
    private const val CHORD_TIE = 1e-9

    /**
     * **The open set, unboxed** — a binary min-heap over three primitive arrays ordered by `f`, then `g`,
     * then slot index: exactly the `(f, g, index)` order the walk's tie-breaks have always used, so the
     * same walk still yields the same path, without one `Node` object per push and two boxed doubles per
     * comparison. Lazy deletion stays the caller's: a popped entry whose `g` is stale is skipped there.
     */
    private class OpenHeap(capacity: Int) {
        private var f = DoubleArray(capacity)
        private var g = DoubleArray(capacity)
        private var slot = IntArray(capacity)
        var size = 0
            private set

        /** The `g` of the entry the last [pop] answered. */
        var poppedG = 0.0
            private set

        fun isNotEmpty(): Boolean = size > 0

        private fun before(fa: Double, ga: Double, sa: Int, fb: Double, gb: Double, sb: Int): Boolean =
            fa < fb || (fa == fb && (ga < gb || (ga == gb && sa < sb)))

        fun push(f0: Double, g0: Double, s0: Int) {
            if (size == f.size) {
                f = f.copyOf(size * 2)
                g = g.copyOf(size * 2)
                slot = slot.copyOf(size * 2)
            }
            var i = size++
            while (i > 0) {
                val parent = (i - 1) shr 1
                if (!before(f0, g0, s0, f[parent], g[parent], slot[parent])) break
                f[i] = f[parent]
                g[i] = g[parent]
                slot[i] = slot[parent]
                i = parent
            }
            f[i] = f0
            g[i] = g0
            slot[i] = s0
        }

        /** Removes the least entry and answers its slot; its `g` is left in [poppedG]. */
        fun pop(): Int {
            val top = slot[0]
            poppedG = g[0]
            val lastF = f[--size]
            val lastG = g[size]
            val lastSlot = slot[size]
            var i = 0
            while (true) {
                var child = 2 * i + 1
                if (child >= size) break
                if (child + 1 < size && before(f[child + 1], g[child + 1], slot[child + 1], f[child], g[child], slot[child])) child++
                if (!before(f[child], g[child], slot[child], lastF, lastG, lastSlot)) break
                f[i] = f[child]
                g[i] = g[child]
                slot[i] = slot[child]
                i = child
            }
            f[i] = lastF
            g[i] = lastG
            slot[i] = lastSlot
            return top
        }
    }

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
        zonePriceSec: ZonePrice = ZonePrice.NONE,
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
     * bypassed by a free coarse copy of the same water. **That step has its way back**: from a block's fine
     * copy the coarse neighbours that step into it are stepped to, so an end standing on a copy walled in by
     * land on the fine layer can be left as well as entered.
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
        zonePriceSec: ZonePrice = ZonePrice.NONE,
        checkCancelled: suspend () -> Unit = { coroutineContext.ensureActive() },
        berthK: Double = 0.0,
        berth: BerthLaw? = null,
        stepGuard: StepGuard? = null,
        waiveEndSlots: Boolean = true,
        probe: ExpansionProbe? = null,
        anyAngle: AnyAngleRule? = null
    ): SearchOutcome {
        val startIdx = walk.slotOfEnd(start)
        val aimIdx = walk.slotOfEnd(aim)
        if (startIdx < 0 || aimIdx < 0) return SearchOutcome(null, 0, 0, aimClosed = false)
        // The start's own reading, for the trace: its layer and how many of its steps lead to water.
        val scratch = IntArray(walk.crossLayerCapacity)
        var startOpen = 0
        forEachStep(walk, startIdx, scratch, { !walk.passable(it) }) { _, _ -> startOpen++ }
        val startLayer = walk.layerOf(startIdx)
        val aimLayer = walk.layerOf(aimIdx)
        var aimOpen = 0
        forEachStep(walk, aimIdx, scratch, { !walk.passable(it) }) { _, _ -> aimOpen++ }
        // A local re-solve between two line vertices binds its ends like any other cell.
        if (stepGuard == null || !waiveEndSlots) {
            return walkOnce(
                walk, startIdx, aimIdx, paceMps, zonePriceSec, checkCancelled, berthK, berth, stepGuard,
                EndRule.BOUND, probe, anyAngle
            ).copy(startLayer = startLayer, startOpenSteps = startOpen, aimLayer = aimLayer, aimOpenSteps = aimOpen)
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
                walk, startIdx, aimIdx, paceMps, zonePriceSec, checkCancelled, berthK, berth, stepGuard, waived, probe,
                anyAngle
            ).copy(startLayer = startLayer, startOpenSteps = startOpen, aimLayer = aimLayer, aimOpenSteps = aimOpen)
        }
        stepGuard.heldRefused.clear()
        // A held walk that finds nothing is walked again only where it refused a step the old waiver lets
        // through **into a cell it never reached**: every other refused step leads nowhere new.
        var reopens = false
        val held = walkOnce(
            walk, startIdx, aimIdx, paceMps, zonePriceSec, checkCancelled, berthK, berth, stepGuard, rule, probe, anyAngle
        ) { closed -> reopens = stepGuard.heldRefused.any { !closed[it] } }
            .copy(
                strictStart = rule.strictStart, strictAim = rule.strictAim,
                startLayer = startLayer, startOpenSteps = startOpen,
                aimLayer = aimLayer, aimOpenSteps = aimOpen
            )
        if (held.path != null || !reopens) return held
        val again = walkOnce(
            walk, startIdx, aimIdx, paceMps, zonePriceSec, checkCancelled, berthK, berth, stepGuard, waived, probe, anyAngle
        )
        return again.summing(held).copy(
            startLayer = startLayer, startOpenSteps = startOpen,
            aimLayer = aimLayer, aimOpenSteps = aimOpen, rewalked = true
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
        val scratch = IntArray(walk.crossLayerCapacity)
        forEachStep(walk, endIdx, scratch, { found || !walk.passable(it) }) { other, edgeM ->
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
     * one, the cells across the seam, and — from a block's fine copy — the coarse neighbours that step into
     * it — handed to [action] with the step's own length, in the one order the walk's tie-breaks rely on. A
     * destination [skip] names is passed over before its length is read.
     */
    private inline fun forEachStep(
        walk: WalkWindows,
        idx: Int,
        scratch: IntArray,
        skip: (toIdx: Int) -> Boolean,
        action: (toIdx: Int, edgeM: Double) -> Unit
    ) {
        val row = walk.rowOf(idx)
        val col = walk.colOf(idx)
        val layer = walk.layerOf(idx)
        // The cell's own centre is read only by a crossing priced from two centres — never by a same-layer
        // step — so a single-grid walk, which makes no crossing, allocates no point per expansion.
        var here: LatLng? = null
        val layered = walk.layerCount > 1
        val isCopy = layered && walk.isFineCopyCell(layer, row, col)
        val cellM = walk.cellSizeM(layer)
        for (k in 0 until 8) {
            val dr = STEP_DR[k]
            val dc = STEP_DC[k]
            val tr = row + dr
            val tc = col + dc
            // The same layer's own neighbour: one cell of that layer's water, at that layer's cell
            // size — **unless a passable fine cell supersedes it.** On the coarse layer the fine copy
            // owns that water, so the coarse cell is not read and the superseding fine cell below
            // carries the crossing; where the fine grid is land the coarse neighbour keeps its role.
            val nIdx = walk.rawSlotOf(layer, tr, tc)
            val over = if (layered) walk.supersedingSlot(layer, tr, tc) else -1
            if (nIdx >= 0 && over < 0 && !skip(nIdx)) action(nIdx, STEP_MULTIPLIER[k] * cellM)
            if (!layered) continue
            // The superseding fine cell over the same water — the crossing the coarse copy may not
            // make, priced from the two cell centres exactly as a seam crossing is.
            if (over >= 0 && !skip(over)) {
                val h = here ?: walk.centerOf(idx).also { here = it }
                action(over, SpatialOperations.haversine(h, walk.centerOf(over)))
            }
            // The seam: the other resolution's cells meeting this cell's face or corner, priced from
            // the two cell centres — the crossing no same-layer step can make.
            val crossed = walk.crossLayerSlotsInto(layer, row, col, dr, dc, scratch)
            for (t in 0 until crossed) {
                val cIdx = scratch[t]
                if (!skip(cIdx)) {
                    val h = here ?: walk.centerOf(idx).also { here = it }
                    action(cIdx, SpatialOperations.haversine(h, walk.centerOf(cIdx)))
                }
            }
            // The way back out of a block's fine copy: the coarse neighbour that steps into it is stepped to,
            // priced from the same two centres, so an end standing on a copy can be left as well as entered.
            if (isCopy) {
                val back = walk.coarseNeighbourOfCopy(layer, row, col, dr, dc)
                if (back >= 0 && !skip(back)) {
                    val h = here ?: walk.centerOf(idx).also { here = it }
                    action(back, SpatialOperations.haversine(h, walk.centerOf(back)))
                }
            }
        }
    }

    /**
     * One A\* walk from [startIdx] to [aimIdx], the guard reading the ends through [ends]. A walk that finds
     * nothing hands [onExhausted] the cells it closed; [probe] is told of each expansion.
     */
    private suspend fun walkOnce(
        walk: WalkWindows,
        startIdx: Int,
        aimIdx: Int,
        paceMps: Double,
        zonePriceSec: ZonePrice,
        checkCancelled: suspend () -> Unit,
        berthK: Double,
        berth: BerthLaw?,
        stepGuard: StepGuard?,
        ends: EndRule,
        probe: ExpansionProbe? = null,
        anyAngle: AnyAngleRule? = null,
        onExhausted: ((closed: BooleanArray) -> Unit)? = null
    ): SearchOutcome {
        if (anyAngle != null) {
            return walkAnyAngle(
                walk, startIdx, aimIdx, paceMps, zonePriceSec, checkCancelled, berthK, berth, stepGuard, ends,
                anyAngle, probe, onExhausted
            )
        }
        val n = walk.size
        val price = CellPrice(zonePriceSec, berthK, berth)
        val aimCenter = walk.centerOf(aimIdx)
        // The heuristic's admissibility factor over this walk's latitude span ([WalkWindows.heuristicScale]).
        val hScale = walk.heuristicScale

        val g = DoubleArray(n) { Double.POSITIVE_INFINITY }
        val h = DoubleArray(n) { Double.NaN }
        val cameFrom = IntArray(n) { -1 }
        val closed = BooleanArray(n)

        g[startIdx] = 0.0
        h[startIdx] = SpatialOperations.haversine(walk.centerOf(startIdx), aimCenter) / paceMps * hScale
        h[aimIdx] = 0.0

        val open = OpenHeap(1024)
        open.push(g[startIdx] + h[startIdx], 0.0, startIdx)
        val scratch = IntArray(walk.crossLayerCapacity)

        var expansions = 0
        while (open.isNotEmpty()) {
            val idx = open.pop()
            if (closed[idx]) continue
            if (open.poppedG != g[idx]) continue   // stale heap entry, superseded by a shorter g
            closed[idx] = true
            if (idx == aimIdx) break

            expansions++
            if (expansions % CADENCE == 0) checkCancelled()

            var steps = 0
            forEachStep(walk, idx, scratch, { closed[it] }) { toIdx, edgeM ->
                steps++
                relax(walk, idx, toIdx, edgeM, g, h, cameFrom, open, aimCenter, paceMps, price, stepGuard, ends, hScale)
            }
            probe?.expanded(idx, steps)
        }

        val passable = walk.passableCells
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

    /** The seconds a step of [edgeM] into [toIdx], of [destCellM], costs: the destination's rate over the metres. */
    private fun stepSec(walk: WalkWindows, price: CellPrice, toIdx: Int, destCellM: Double, edgeM: Double): Double =
        (price.cellSec(walk, toIdx, destCellM) / destCellM) * edgeM

    /**
     * **The any-angle walk — a Lazy Theta\* over the walk** (`261029_FEAT_PLN_Route_any-angle-search.md` §4.2,
     * §13; the Evolutive + row's walk, every other row keeping today's A\*).
     *
     * Beside `g`, `h`, the heap, the closed set, the cadence and the tie order — all today's — every node keeps a
     * **parent** that may be any earlier node a clear, no-dearer straight line reaches, so the chain the walk hands
     * back is already taut; where the straight line is refused (land, the wall, a cell no window holds) or dearer
     * than the grid's own way (a chord through a collar the staircase skirts), the node takes the best closed
     * neighbour's step — the pull's commit, made at every frontier cell with the aim's cost in hand.
     *
     * **A push goes through the grid's own step** — [relax]'s price and gate check ([StepGuard.allows], the one
     * writer of [StepGuard.heldRefused], so the held/waived re-walk stands) — and **names** the expanding node's
     * parent as the neighbour's own where that parent stands within [AnyAngleRule.reachCells], the expander kept
     * beside it as the grid parent with its step's own value. What the push *prices* the chord at is the rule's
     * [ChordEstimate]: the step itself, the straight-line bound, or the chord read there and then. Under the two lazy
     * estimates **the one chord read is at pop**: the node's chord from its named parent is priced for real
     * ([ChordReader]) — the aim's included, so its cost is checked — and the cheaper of that chord and the grid
     * parent's step is taken, a tie within [CHORD_TIE] the chord's. The grid step is already the best closed
     * neighbour's allowed step, since every closed neighbour relaxed the node with its final `g` before this pop, so
     * no neighbour is enumerated again. Where the pop lowers the node's cost and the rule says so, the closed
     * neighbours that cost now reaches cheaper are **reopened** — pushed again as if never closed — so the credit
     * travels on; without it a cell closed before the credit arrives keeps its dearer way, which the first batch read
     * as a line 1–3 % dearer on the field.
     *
     * **The reach cap** bounds every chord's length and so the search's cost; a node farther than the cap from its
     * expander's parent takes the expander itself as parent and becomes the next anchor — the chain bends there by
     * the grid's quantum, which the pull takes out as it takes out any near-straight vertex today.
     *
     * The readings beside [SearchOutcome.expansions]: the chord tally, the lazy pops and how many fell back, and
     * the reach the chords used.
     */
    private suspend fun walkAnyAngle(
        walk: WalkWindows,
        startIdx: Int,
        aimIdx: Int,
        paceMps: Double,
        zonePriceSec: ZonePrice,
        checkCancelled: suspend () -> Unit,
        berthK: Double,
        berth: BerthLaw?,
        stepGuard: StepGuard?,
        ends: EndRule,
        rule: AnyAngleRule,
        probe: ExpansionProbe?,
        onExhausted: ((closed: BooleanArray) -> Unit)?
    ): SearchOutcome {
        val n = walk.size
        val price = CellPrice(zonePriceSec, berthK, berth)
        val tally = ChordTally()
        val chords = ChordReader(walk, price, stepGuard, ends, tally)
        val aimCenter = walk.centerOf(aimIdx)
        val hScale = walk.heuristicScale
        val ratio = walk.layerRatio
        val coarseLayer = walk.coarseLayerIndex

        val g = DoubleArray(n) { Double.POSITIVE_INFINITY }
        val h = DoubleArray(n) { Double.NaN }
        // The node the chain runs through — a far parent where the chord stands, the grid parent otherwise — and
        // the grid parent itself: the expander that pushed the node's `g`, kept for the lazy check's fallback.
        val parent = IntArray(n) { -1 }
        val cameFrom = IntArray(n) { -1 }
        // The grid parent's own step value, the fallback a lazy pop falls to; equal to `g` under the step estimate.
        val gridG = DoubleArray(n) { Double.POSITIVE_INFINITY }
        val lazy = BooleanArray(n)
        val closed = BooleanArray(n)
        val estimate = rule.estimate

        // The reach cap in the finest layer's cells, squared — a coarse cell is `ratio` of them a side.
        val reachUnits = if (rule.reachCells > 0) rule.reachCells.toDouble() * ratio else Double.POSITIVE_INFINITY
        val reachSq = reachUnits * reachUnits
        fun unitX(slot: Int): Double =
            (walk.colOf(slot) + 0.5) * (if (coarseLayer >= 0 && walk.layerOf(slot) == coarseLayer) ratio else 1)
        fun unitY(slot: Int): Double =
            (walk.rowOf(slot) + 0.5) * (if (coarseLayer >= 0 && walk.layerOf(slot) == coarseLayer) ratio else 1)

        g[startIdx] = 0.0
        gridG[startIdx] = 0.0
        h[startIdx] = SpatialOperations.haversine(walk.centerOf(startIdx), aimCenter) / paceMps * hScale
        h[aimIdx] = 0.0

        val open = OpenHeap(1024)
        open.push(g[startIdx] + h[startIdx], 0.0, startIdx)
        val scratch = IntArray(walk.crossLayerCapacity)

        var expansions = 0
        var lazyPops = 0
        var fallbacks = 0
        var reopens = 0
        var reachSum = 0.0
        var reachMax = 0.0

        fun reachOf(a: Int, b: Int): Double {
            val dx = unitX(b) - unitX(a)
            val dy = unitY(b) - unitY(a)
            return sqrt(dx * dx + dy * dy)
        }

        fun chordTaken(far: Int, idx: Int) {
            val reach = reachOf(far, idx) / ratio
            reachSum += reach
            if (reach > reachMax) reachMax = reach
        }

        /**
         * One step out of [idx] into [toIdx] — a push, or a reopen where [reopening] — the grid pair recorded and the
         * rule's estimate pushed where it improves the node; `false` where the step is refused or improves nothing.
         */
        fun push(idx: Int, toIdx: Int, edgeM: Double, far: Int, reopening: Boolean) {
            if (!walk.passable(toIdx)) return
            val destCellM = walk.cellSizeM(walk.layerOf(toIdx))
            if (destCellM <= 0.0) return
            val stepG = g[idx] + stepSec(walk, price, toIdx, destCellM, edgeM)
            val inReach = far >= 0 && reachOf(far, toIdx).let { it * it <= reachSq }
            var candidate = stepG
            var via = idx
            if (inReach) {
                when (estimate) {
                    ChordEstimate.STEP -> via = far
                    ChordEstimate.BOUND -> {
                        val bound =
                            g[far] + SpatialOperations.haversine(walk.centerOf(far), walk.centerOf(toIdx)) / paceMps * hScale
                        if (bound < candidate) candidate = bound
                        via = far
                    }
                    ChordEstimate.FULL -> {
                        val chord = chords.costSec(far, toIdx)
                        if (!chord.isNaN() && g[far] + chord <= stepG * (1.0 + CHORD_TIE)) {
                            candidate = g[far] + chord
                            via = far
                        }
                    }
                }
            }
            val improvesGrid = stepG < gridG[toIdx]
            val improves = candidate < g[toIdx] * (if (reopening) 1.0 - CHORD_TIE else 1.0)
            if (!improvesGrid && !improves) return
            // The depth gate's own step, asked only of a step that would improve the path — [relax]'s rule.
            if (stepGuard != null && !stepGuard.allows(walk, idx, toIdx, edgeM, ends)) return
            if (improvesGrid) {
                gridG[toIdx] = stepG
                cameFrom[toIdx] = idx
            }
            if (!improves) return
            if (reopening) {
                closed[toIdx] = false
                reopens++
            }
            g[toIdx] = candidate
            parent[toIdx] = via
            lazy[toIdx] = via != idx && estimate != ChordEstimate.FULL
            if (via != idx && estimate == ChordEstimate.FULL) chordTaken(via, toIdx)
            if (h[toIdx].isNaN()) {
                h[toIdx] = SpatialOperations.haversine(walk.centerOf(toIdx), aimCenter) / paceMps * hScale
            }
            open.push(candidate + h[toIdx], candidate, toIdx)
        }

        while (open.isNotEmpty()) {
            val idx = open.pop()
            if (closed[idx]) continue
            if (open.poppedG != g[idx]) continue   // stale heap entry, superseded by a shorter g
            var lowered = false
            if (lazy[idx]) {
                // The lazy check, the aim's included: the chord from the named parent priced for real; refused or
                // dearer than the grid parent's step — the best closed neighbour's — the node takes that parent. A
                // chord's half-cell pieces sum to within an ulp of the steps they equal, and that tie is the chord's.
                lazyPops++
                val far = parent[idx]
                val chord = chords.costSec(far, idx)
                val chordG = if (chord.isNaN()) Double.POSITIVE_INFINITY else g[far] + chord
                lazy[idx] = false
                val before = g[idx]
                if (chordG <= gridG[idx] * (1.0 + CHORD_TIE)) {
                    g[idx] = chordG
                    chordTaken(far, idx)
                } else {
                    fallbacks++
                    g[idx] = gridG[idx]
                    parent[idx] = cameFrom[idx]
                }
                lowered = g[idx] < before * (1.0 - CHORD_TIE)
            }
            closed[idx] = true
            if (idx == aimIdx) break

            expansions++
            if (expansions % CADENCE == 0) checkCancelled()

            // The parent a neighbour may name: this node's own, where it stands within reach of the neighbour.
            val far = parent[idx]
            var steps = 0
            forEachStep(walk, idx, scratch, { closed[it] }) { toIdx, edgeM ->
                steps++
                push(idx, toIdx, edgeM, far, reopening = false)
            }
            // The credit travels on: the closed neighbours this node's lowered cost reaches cheaper are reopened.
            if (lowered && rule.reopen) {
                forEachStep(walk, idx, scratch, { !closed[it] }) { toIdx, edgeM ->
                    push(idx, toIdx, edgeM, far, reopening = true)
                }
            }
            probe?.expanded(idx, steps)
        }

        val passable = walk.passableCells
        val readings = SearchOutcome(
            null, expansions, passable, aimClosed = false, anyAngle = true, chordTally = tally,
            lazyPops = lazyPops, fallbacks = fallbacks, reopens = reopens,
            reachSumCells = reachSum, reachMaxCells = reachMax
        )
        if (!closed[aimIdx]) {
            onExhausted?.invoke(closed)
            return readings
        }
        val path = ArrayList<CellIndex>()
        var cur = aimIdx
        while (cur != -1) {
            path.add(CellIndex(walk.rowOf(cur), walk.colOf(cur), walk.layerOf(cur)))
            cur = parent[cur]
        }
        path.reverse()
        return readings.copy(path = path, aimClosed = true, costSec = g[aimIdx])
    }

    /**
     * **One relaxation**: the destination's own rate over the metres the step covers.
     *
     * The destination cell's cost is its `sourceCostSec` plus the zone price the caller reads for **its own
     * size** and the berth ([CellPrice]); divided by that size it is a seconds-per-metre rate, and multiplied by
     * the edge's own length it prices the step. For equal cells `edgeM` is `cellM × multiplier`, so the charge is exactly the
     * `cellSec × multiplier` the uniform walk always paid — `avoid`'s answers cannot move. Across the seam
     * `edgeM` is the two centres' own distance, so a 100 m interior cell and a 20 m band cell meet at a cost
     * neither cell's size alone could name. The heuristic stays admissible because every edge costs at least
     * its **scaled** distance at the pace, layer by layer — the scale ([WalkWindows.heuristicScale]) absorbing
     * the nominal cell's east–west shortfall towards the equator.
     */
    private fun relax(
        walk: WalkWindows,
        fromIdx: Int,
        toIdx: Int,
        edgeM: Double,
        g: DoubleArray,
        h: DoubleArray,
        cameFrom: IntArray,
        open: OpenHeap,
        aimCenter: LatLng,
        paceMps: Double,
        price: CellPrice,
        stepGuard: StepGuard?,
        ends: EndRule,
        hScale: Double
    ) {
        if (!walk.passable(toIdx)) return
        val destCellM = walk.cellSizeM(walk.layerOf(toIdx))
        if (destCellM <= 0.0) return
        val newG = g[fromIdx] + stepSec(walk, price, toIdx, destCellM, edgeM)
        if (newG < g[toIdx]) {
            // The depth gate's own step, asked only of a step that would improve the path: a step whose
            // straight segment comes within the wall is no step, the ends' own read through [ends].
            if (stepGuard != null && !stepGuard.allows(walk, fromIdx, toIdx, edgeM, ends)) return
            g[toIdx] = newG
            cameFrom[toIdx] = fromIdx
            if (h[toIdx].isNaN()) {
                h[toIdx] = SpatialOperations.haversine(walk.centerOf(toIdx), aimCenter) / paceMps * hScale
            }
            open.push(newG + h[toIdx], newG, toIdx)
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
    /** The aim slot's layer, `-1` where no aim slot was found — the aim's own cell, for the trace. */
    val aimLayer: Int = -1,
    /** How many of the aim slot's own steps lead into passable water — `0` is an aim with no way in. */
    val aimOpenSteps: Int = -1,
    /** Whether the answering walk was the waived re-walk after a held walk found nothing. */
    val rewalked: Boolean = false,
    /**
     * Whether the pass ran this walk **from the aim's end** (`RoutePassRunner.searchFromCanonicalEnd`), the path
     * handed back already turned to the caller's direction. [strictStart], [strictAim], [startLayer] and
     * [startOpenSteps] stay in the walk's own frame — the end it left is the one they name.
     */
    val reversed: Boolean = false,
    /** Whether the answering walk was the any-angle one (the Evolutive + row's). */
    val anyAngle: Boolean = false,
    /** The any-angle walk's chord reads, counted — `null` for today's walk. */
    val chordTally: ChordTally? = null,
    /** How many pops priced a chord from a named parent (the any-angle walk's lazy check). */
    val lazyPops: Int = 0,
    /** How many of those took the grid parent's step instead — the chord refused or dearer. */
    val fallbacks: Int = 0,
    /** How many closed cells a lazy pop's lowered cost reopened (`route.search.anyAngle.reopen`). */
    val reopens: Int = 0,
    /** The reach (coarse cells) of the chords taken, summed, and the longest — the reading the cap is set on. */
    val reachSumCells: Double = 0.0,
    val reachMaxCells: Double = 0.0
) {
    /** How many chords were taken — the lazy pops that kept theirs, or the pushes that read one in full. */
    val chordsTaken: Int
        get() = if (lazyPops > 0) lazyPops - fallbacks else ((chordTally?.reads ?: 0L) - (chordTally?.refusals ?: 0L)).toInt()

    /** The mean reach (coarse cells) of the chords taken, `0.0` where none was. */
    val reachMeanCells: Double
        get() = if (chordsTaken > 0) reachSumCells / chordsTaken else 0.0

    /**
     * This outcome with [prior]'s readings added to its own — the walk before it, as a held walk precedes its waived
     * re-walk and a canonical walk its fallback — so every count a pass reads is both walks'.
     */
    fun summing(prior: SearchOutcome): SearchOutcome = copy(
        expansions = prior.expansions + expansions,
        chordTally = when {
            chordTally == null -> prior.chordTally
            prior.chordTally == null -> chordTally
            else -> ChordTally().also { it.add(prior.chordTally); it.add(chordTally) }
        },
        lazyPops = prior.lazyPops + lazyPops,
        fallbacks = prior.fallbacks + fallbacks,
        reopens = prior.reopens + reopens,
        reachSumCells = prior.reachSumCells + reachSumCells,
        reachMaxCells = maxOf(prior.reachMaxCells, reachMaxCells)
    )
}

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

    /**
     * Whether a chord's piece inside a cell of [cellM] whose stored distance is [gateDistanceM] keeps the wall by
     * the bound alone: the piece lies up to `cellM / √2` from the cell's centre, so the proof is
     * `hardM + cellM / √2`, trusted only within [GateRule.seedReachM] at that size, as [clear] trusts a step's.
     */
    internal fun provesPiece(gateDistanceM: Double, cellM: Double): Boolean {
        val needM = gate.hardM + cellM * HALF_DIAGONAL
        return needM <= gate.seedReachM(cellM) && gateDistanceM >= needM
    }

    /**
     * Whether the segment [a]–[b] keeps the wall — the measured read, cut at the waiver's rim
     * ([GateRule.keepsWall]) and never waived whole, whichever way the walk holds its ends.
     */
    internal fun keepsWall(a: LatLng, b: LatLng): Boolean = gate.keepsWall(a, b, start, aim)

    /** Whether the raw end's own leg to its slot [slot] keeps the wall — the start's where [ofStart], the aim's else. */
    internal fun endLegKeepsWall(walk: WalkWindows, slot: Int, ofStart: Boolean): Boolean =
        if (ofStart) endLegClear(walk, slot, start, startLeg) else endLegClear(walk, slot, aim, aimLeg)

    private fun endLegClear(walk: WalkWindows, slot: Int, end: LatLng, memo: IntArray): Boolean {
        if (memo[0] != slot) {
            memo[0] = slot
            memo[1] = if (gate.keepsWall(end, walk.centerOf(slot), start, aim)) 1 else 0
        }
        return memo[1] == 1
    }

    private companion object {
        /** Half a unit square's diagonal: the farthest a point of a cell stands from its centre, in cells. */
        val HALF_DIAGONAL = 1.0 / sqrt(2.0)
    }
}
