package ykws.android.maro.spatial.multipass

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot

/**
 * The shortest piece (m) a chord reads a cell for. A chord through a lattice vertex touches the two cells beside
 * it over no length at all — the rounding leaves a hair — and a cell it crosses over nothing is neither priced nor
 * a refusal, exactly as a diagonal step reads neither of the cells it passes between.
 */
private const val CHORD_PIECE_EPS_M = 1e-6

/** [ChordReader]'s per-slot reading of the gate's stored bound: unread, proved, or unproved. */
private const val PROOF_UNREAD: Byte = 0
private const val PROOF_PROVED: Byte = 1
private const val PROOF_UNPROVED: Byte = 2

/** [ChordReader]'s per-block reading of the fine layer: unread, no fine cell held, or some held. */
private const val BLOCK_UNREAD: Byte = 0
private const val BLOCK_OPEN: Byte = 1
private const val BLOCK_HELD: Byte = 2

/**
 * **What one reader's chords cost, counted** — reads and refusals, the pieces priced (a block skipped at the
 * coarse rate is one piece), the rate reads that missed the cache, and the gate's reads: unproved runs, measured
 * segment reads and the ends' own legs. The any-angle search's own reading, and the harness's instrument.
 */
class ChordTally {
    var reads = 0L
    var refusals = 0L
    var pieces = 0L
    var finePieces = 0L
    var coarsePieces = 0L
    var blockPieces = 0L
    var rateMisses = 0L
    var unprovedRuns = 0L
    var measuredReads = 0L
    var endLegReads = 0L

    fun add(other: ChordTally) {
        reads += other.reads
        refusals += other.refusals
        pieces += other.pieces
        finePieces += other.finePieces
        coarsePieces += other.coarsePieces
        blockPieces += other.blockPieces
        rateMisses += other.rateMisses
        unprovedRuns += other.unprovedRuns
        measuredReads += other.measuredReads
        endLegReads += other.endLegReads
    }
}

/**
 * **The any-angle search's chord: the seconds the straight segment between two slot centres costs, or `NaN` where
 * it is no link** (`261029_FEAT_PLN_Route_any-angle-search.md` §4.1, §13). One reader serves one walk — its
 * [price] is the pass's λ, its [guard] the pass's gate, its [ends] the walk's own — and remembers what it read.
 *
 * **Priced per cell crossed.** The segment is walked across the lattice cells it crosses, each priced as a step
 * prices a cell ([CellPrice]) over the metres the segment runs inside it: a cell's `cellSec / cellM × metres`.
 * The metres are **lattice metres**, the cell's own size per cell side — the unit a step already pays — so the
 * search's scaled heuristic stays a bound. A chord along a row therefore costs half of each end cell and the
 * whole of every cell between: the sum of its one-cell links priced the same way. A cell the segment only
 * touches at a vertex is crossed over no length and read for nothing.
 *
 * **Two layers.** The segment is walked on the fine lattice. Wherever a fine window holds the cell it stands in,
 * that fine cell is read and the coarse cell over it is not; where no fine window holds it, the coarse cell of
 * that block is read at the coarse rate — a block whose fine tile covers only part of it included, since the
 * step graph passes coarse → copy → coarse there. **A block no fine window touches is read in one piece**, the
 * segment skipped to the block's exit at the coarse rate rather than walked in fine steps; a block a fine window
 * touches is walked in fine steps, each resolved. An end standing on the coarse layer reads its own whole cell:
 * it is the node the walk stands on, even where a fine window holds its block around a land copy.
 *
 * **Refusals.** A cell no window holds, or one that is not passable (land, a Blocked marker's body), refuses the
 * chord — a route end's own cell ([ends]) aside, which the walk may stand in where the raster walled it, as a
 * step never asks the cell it leaves. Under [guard] the gate is read per cell: a piece the stored distance
 * proves ([StepGuard.provesPiece]) reads nothing, and each run of unproved pieces is read once, measured
 * ([StepGuard.keepsWall]) — never the whole chord. **An end is never waived whole**: a chord touching one of
 * [ends]' slots reads that end's raw leg to its cell and is otherwise cut at the waiver's rim, however the walk
 * holds that end. A refused chord records nothing — the one-cell steps keep the held/waived re-walk's account.
 *
 * **Remembered.** A cell's rate and its proof are read once per reader and kept in two arrays over the walk's
 * slots, allocated on the first chord; a block's fine-window touch likewise, per coarse slot. The arrays are the
 * reader's own and die with the walk.
 *
 * **One answer either way.** The segment is always walked from the lower slot, so `a → b` and `b → a` cross the
 * same pieces in the same order and sum to the same double. The price is read first and the measured gate last,
 * so a chord the water refuses pays no measured read.
 */
internal class ChordReader(
    private val walk: WalkWindows,
    private val price: CellPrice,
    private val guard: StepGuard?,
    private val ends: EndRule,
    val tally: ChordTally? = null
) {
    private val coarse = walk.coarseLayerIndex
    private val fine = walk.fineLayerIndex
    private val ratio = walk.layerRatio

    /** The walk's finest layer, the unit the segment is walked in: a coarse cell is [ratio] units a side. */
    private val unit = if (fine >= 0) fine else 0

    private var rate: DoubleArray? = null
    private var proof: ByteArray? = null
    private var block: ByteArray? = null

    /** The unproved runs of the chord being read, as `[t0, t1]` pairs along the segment. */
    private var runs = DoubleArray(8)

    /** The seconds the chord between the centres of [fromIdx] and [toIdx] costs, or `NaN` where it is no link. */
    fun costSec(fromIdx: Int, toIdx: Int): Double {
        if (fromIdx == toIdx) return 0.0
        tally?.let { it.reads++ }
        val cost = read(minOf(fromIdx, toIdx), maxOf(fromIdx, toIdx))
        if (cost.isNaN()) tally?.let { it.refusals++ }
        return cost
    }

    private fun read(a: Int, b: Int): Double {
        val aCoarse = walk.layerOf(a) == coarse
        val bCoarse = walk.layerOf(b) == coarse
        val aRow = walk.rowOf(a)
        val aCol = walk.colOf(a)
        val bRow = walk.rowOf(b)
        val bCol = walk.colOf(b)
        val ax = unitCentre(aCol, aCoarse)
        val ay = unitCentre(aRow, aCoarse)
        val dx = unitCentre(bCol, bCoarse) - ax
        val dy = unitCentre(bRow, bCoarse) - ay
        val lengthM = hypot(dx, dy) * walk.cellSizeM(unit)
        var col = floor(ax).toInt()
        var row = floor(ay).toInt()
        val stepCol = if (dx > 0.0) 1 else -1
        val stepRow = if (dy > 0.0) 1 else -1
        val tDeltaCol = if (dx != 0.0) 1.0 / abs(dx) else Double.POSITIVE_INFINITY
        val tDeltaRow = if (dy != 0.0) 1.0 / abs(dy) else Double.POSITIVE_INFINITY
        var tMaxCol = when {
            dx > 0.0 -> (col + 1 - ax) / dx
            dx < 0.0 -> (ax - col) / -dx
            else -> Double.POSITIVE_INFINITY
        }
        var tMaxRow = when {
            dy > 0.0 -> (row + 1 - ay) / dy
            dy < 0.0 -> (ay - row) / -dy
            else -> Double.POSITIVE_INFINITY
        }
        var cost = 0.0
        var lastSlot = -1
        var lastRate = 0.0
        var lastProved = true
        var runCount = 0
        var runFrom = Double.NaN
        var runTo = 0.0
        var t = 0.0
        val tally = tally
        while (true) {
            // The cell this piece stands in, and how far the piece runs: to the unit cell's exit, or — a coarse
            // cell no fine window touches, or a coarse end's own cell — to the block's exit.
            val slot = slotAt(row, col, a, aCoarse, aRow, aCol, b, bCoarse, bRow, bCol)
            var colsToExit = 1
            var rowsToExit = 1
            var skipsBlock = false
            if (slot >= 0 && coarse >= 0 && walk.layerOf(slot) == coarse &&
                (slot == a || slot == b || blockOpen(slot))
            ) {
                val blockCol = Math.floorDiv(col, ratio) * ratio
                val blockRow = Math.floorDiv(row, ratio) * ratio
                colsToExit = if (stepCol > 0) blockCol + ratio - col else col - blockCol + 1
                rowsToExit = if (stepRow > 0) blockRow + ratio - row else row - blockRow + 1
            }
            // An axis the segment does not move along has an infinite step, which a zero multiple would turn to NaN.
            val tExitCol = if (colsToExit > 1) tMaxCol + (colsToExit - 1) * tDeltaCol else tMaxCol
            val tExitRow = if (rowsToExit > 1) tMaxRow + (rowsToExit - 1) * tDeltaRow else tMaxRow
            val tNext = minOf(tExitCol, tExitRow, 1.0)
            // A skip is a piece that crossed more than one unit cell on the axis it leaves by.
            skipsBlock = (tExitCol <= tExitRow && colsToExit > 1) || (tExitRow <= tExitCol && rowsToExit > 1)
            val pieceM = (tNext - t) * lengthM
            if (pieceM > CHORD_PIECE_EPS_M) {
                // A route end's own walled cell is left or entered, never crossed: the exemption holds for the
                // chord's own ends alone.
                val ownEnd = (slot == a || slot == b) && (slot == ends.startIdx || slot == ends.aimIdx)
                if (slot < 0 || (!ownEnd && !walk.passable(slot))) return Double.NaN
                if (slot != lastSlot) {
                    lastRate = rateOf(slot)
                    lastProved = guard == null || proofOf(slot) == PROOF_PROVED
                    lastSlot = slot
                }
                cost += lastRate * pieceM
                if (tally != null) {
                    tally.pieces++
                    when {
                        skipsBlock -> tally.blockPieces++
                        walk.layerOf(slot) == coarse && coarse >= 0 -> tally.coarsePieces++
                        else -> tally.finePieces++
                    }
                }
                if (!lastProved) {
                    if (runFrom.isNaN()) runFrom = t
                    runTo = tNext
                } else if (!runFrom.isNaN()) {
                    pushRun(runCount++, runFrom, runTo)
                    runFrom = Double.NaN
                }
            }
            if (tNext >= 1.0) break
            // Step to the cell past the exit — on the exit's axis by the cells skipped, on the other axis by the
            // boundaries the skipped piece crossed (none on a still axis, whose infinite step a zero multiple would
            // turn to NaN); through a vertex both advance, the cells beside it crossed over nothing.
            when {
                tExitCol < tExitRow -> {
                    col += stepCol * colsToExit
                    tMaxCol = tExitCol + tDeltaCol
                    val crossed = crossedBefore(tMaxRow, tDeltaRow, tExitCol)
                    if (crossed > 0) {
                        row += stepRow * crossed
                        tMaxRow += crossed * tDeltaRow
                    }
                }
                tExitRow < tExitCol -> {
                    row += stepRow * rowsToExit
                    tMaxRow = tExitRow + tDeltaRow
                    val crossed = crossedBefore(tMaxCol, tDeltaCol, tExitRow)
                    if (crossed > 0) {
                        col += stepCol * crossed
                        tMaxCol += crossed * tDeltaCol
                    }
                }
                else -> {
                    col += stepCol * colsToExit
                    row += stepRow * rowsToExit
                    tMaxCol = tExitCol + tDeltaCol
                    tMaxRow = tExitRow + tDeltaRow
                }
            }
            t = tNext
        }
        if (guard == null) return cost
        if (!runFrom.isNaN()) pushRun(runCount++, runFrom, runTo)
        if (a == ends.startIdx || b == ends.startIdx) {
            tally?.let { it.endLegReads++ }
            if (!guard.endLegKeepsWall(walk, ends.startIdx, ofStart = true)) return Double.NaN
        }
        if (a == ends.aimIdx || b == ends.aimIdx) {
            tally?.let { it.endLegReads++ }
            if (!guard.endLegKeepsWall(walk, ends.aimIdx, ofStart = false)) return Double.NaN
        }
        tally?.let { it.unprovedRuns += runCount }
        for (k in 0 until runCount) {
            val from = walk.latticePoint(unit, ax + runs[2 * k] * dx, ay + runs[2 * k] * dy)
            val to = walk.latticePoint(unit, ax + runs[2 * k + 1] * dx, ay + runs[2 * k + 1] * dy)
            tally?.let { it.measuredReads++ }
            if (!guard.keepsWall(from, to)) return Double.NaN
        }
        return cost
    }

    /** How many boundaries at `tMax + k × tDelta` lie at or before [tExit] — the other axis's crossings in a skip. */
    private fun crossedBefore(tMax: Double, tDelta: Double, tExit: Double): Int =
        if (tMax > tExit) 0 else floor((tExit - tMax) / tDelta).toInt() + 1

    /** The centre of lattice index [index] in unit cells — [ratio] units a side on the coarse layer. */
    private fun unitCentre(index: Int, onCoarse: Boolean): Double = (index + 0.5) * (if (onCoarse) ratio else 1)

    /**
     * The slot a chord reads for the unit cell `(row, col)`: an end standing on the coarse layer for its own whole
     * cell, else the fine cell where a fine window holds it, else the coarse cell of its block; `-1` where nothing
     * holds it. A one-layer walk reads its own cell.
     */
    private fun slotAt(
        row: Int, col: Int,
        a: Int, aCoarse: Boolean, aRow: Int, aCol: Int,
        b: Int, bCoarse: Boolean, bRow: Int, bCol: Int
    ): Int {
        if (coarse < 0) return walk.rawSlotOf(0, row, col)
        val coarseRow = Math.floorDiv(row, ratio)
        val coarseCol = Math.floorDiv(col, ratio)
        if (aCoarse && aRow == coarseRow && aCol == coarseCol) return a
        if (bCoarse && bRow == coarseRow && bCol == coarseCol) return b
        val fineSlot = walk.rawSlotOf(fine, row, col)
        return if (fineSlot >= 0) fineSlot else walk.rawSlotOf(coarse, coarseRow, coarseCol)
    }

    /** The slot's seconds per lattice metre, read once. */
    private fun rateOf(slot: Int): Double {
        val rates = rate ?: DoubleArray(walk.size) { Double.NaN }.also { rate = it }
        val known = rates[slot]
        if (!known.isNaN()) return known
        tally?.let { it.rateMisses++ }
        val cellM = walk.cellSizeM(walk.layerOf(slot))
        val r = price.cellSec(walk, slot, cellM) / cellM
        rates[slot] = r
        return r
    }

    /** Whether the slot's stored gate distance proves a chord's piece inside it, read once. */
    private fun proofOf(slot: Int): Byte {
        val proofs = proof ?: ByteArray(walk.size).also { proof = it }
        val known = proofs[slot]
        if (known != PROOF_UNREAD) return known
        val guard = guard ?: return PROOF_PROVED
        val cellM = walk.cellSizeM(walk.layerOf(slot))
        val p = if (guard.provesPiece(walk.gateDistanceM(slot), cellM)) PROOF_PROVED else PROOF_UNPROVED
        proofs[slot] = p
        return p
    }

    /** Whether no fine window touches the block of the coarse [slot], read once. */
    private fun blockOpen(slot: Int): Boolean {
        val blocks = block ?: ByteArray(walk.size).also { block = it }
        val known = blocks[slot]
        if (known != BLOCK_UNREAD) return known == BLOCK_OPEN
        val r0 = walk.rowOf(slot) * ratio
        val c0 = walk.colOf(slot) * ratio
        var touched = false
        for (window in walk.windows) {
            if (window.layer != fine) continue
            val rows = window.rowOffset until window.rowOffset + window.grid.rows
            val cols = window.colOffset until window.colOffset + window.grid.cols
            if (r0 + ratio > rows.first && r0 <= rows.last && c0 + ratio > cols.first && c0 <= cols.last) {
                touched = true
                break
            }
        }
        blocks[slot] = if (touched) BLOCK_HELD else BLOCK_OPEN
        return !touched
    }

    /** The run `[from, to]` written at [index], the array grown where it is full. */
    private fun pushRun(index: Int, from: Double, to: Double) {
        if (2 * index + 1 >= runs.size) runs = runs.copyOf(runs.size * 2)
        runs[2 * index] = from
        runs[2 * index + 1] = to
    }
}
