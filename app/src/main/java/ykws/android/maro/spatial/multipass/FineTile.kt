package ykws.android.maro.spatial.multipass

/** The side of one fine tile, in fine cells — the knob that sets how finely the map grows. */
internal const val FINE_TILE_CELLS = 64

/**
 * **One immutable, sparse tile of the fine layer — the hybrid solved here.**
 *
 * A tile names a fixed `tileCells × tileCells` block of the anchored fine lattice, so its bounds are
 * pure arithmetic on the anchor: `tileCells × cellSizeDegLat` north–south and the degenerate pair
 * east–west. It holds **only its collar members** — the cells the plan's fine mask keeps as water — as
 * parallel primitive arrays over that member list, never a per-cell box and never a dense box:
 *
 * - a [state] of the stored [`MultipassCellState`] ordinal, one byte a member,
 * - six doubles a member — [sourceCostSec], [zoneLimitKn], [collarLimitKn], [bandLimitKn],
 *   [bandCollarLimitKn] and [depthPriceCoef],
 * - an [localIndex] of each member's **local** cell index within the tile (`row * cols + col`), so a
 *   dense box is never materialised to hold one and the member's position is recovered by arithmetic.
 *
 * The tile is the same water a per-arm fine window used to carry — its contents come from the existing
 * [`rasterizeWindow`] with the plan's `fineMask` applied — so a shared tile is the marking work done
 * once. The per-arm berth carve and the end discs are per-acquisition writes a tile must **not** carry,
 * which is why [writeInto] copies a tile's members into the arm's own mutable [`MultipassGrid`].
 */
internal class FineTile(
    /** The tile's row on the fixed fine tile grid — the block index, never a lattice cell row. */
    val tileRow: Int,
    /** The tile's column on the fixed fine tile grid. */
    val tileCol: Int,
    /** The block's side, in fine cells — kept beside the indices so a lone tile is self-describing. */
    val tileCells: Int,
    /** The block's own row count, always [tileCells] for a tile the extractor built. */
    val rows: Int,
    /** The block's own column count — what [writeInto] decodes a local index with, `row * cols + col`. */
    val cols: Int,
    /** The block's south-west origin, on the anchored fine lattice. */
    val latSouth: Double,
    /** The block's west origin, on the anchored fine lattice. */
    val lonWest: Double,
    /** The fine lattice's own cell-size pair, so a member's centre is arithmetic on the origin. */
    val cellSizeDegLat: Double,
    /** The fine lattice's own longitude cell size. */
    val cellSizeDegLon: Double,
    /** The fine lattice's cell size in metres. */
    val cellM: Double,
    /** The base cost (s) a cell of this lattice opens at — one `baseCostSec(cellM, pace)`. */
    val baseCostSec: Double,
    /** The stored tag ordinal of each member, one byte each. */
    val state: ByteArray,
    /** The source cost (s) of each member. */
    val sourceCostSec: DoubleArray,
    /** The interior zone limit (kn) of each member. */
    val zoneLimitKn: DoubleArray,
    /** The zone outside-margin limit (kn) of each member. */
    val collarLimitKn: DoubleArray,
    /** The 300 m band's width limit (kn) of each member. */
    val bandLimitKn: DoubleArray,
    /** The 300 m band's outside-margin limit (kn) of each member. */
    val bandCollarLimitKn: DoubleArray,
    /** The λ-free depth-price coefficient of each member. */
    val depthPriceCoef: DoubleArray,
    /** Each member's local cell index within the block: `row * cols + col`. */
    val localIndex: IntArray
) {
    /** How many members this tile keeps. */
    val memberCount: Int get() = localIndex.size

    /** The tile's own bytes — one byte a tag, six doubles a member and one int a local index. */
    val byteSize: Long get() = memberCount.toLong() * (1L + 6L * 8L + 4L)

    /**
     * **Copies every member that falls inside [target]** — the per-arm materialisation, the one place a
     * shared immutable tile becomes writable water. [windowRowOffset] and [windowColOffset] are the
     * target grid's own lattice origin, so a member's block-local index becomes the target's local one by
     * arithmetic; a member outside the target's box is skipped, which is how a window narrower than the
     * tile (clamped to the corridor) is filled from the same tile.
     */
    fun writeInto(target: MultipassGrid, windowRowOffset: Int, windowColOffset: Int) {
        val firstRow = tileRow * tileCells
        val firstCol = tileCol * tileCells
        for (m in localIndex.indices) {
            val local = localIndex[m]
            val row = firstRow + local / cols - windowRowOffset
            val col = firstCol + local % cols - windowColOffset
            if (!target.inBounds(row, col)) continue
            target.writeMember(
                row, col, state[m], sourceCostSec[m], zoneLimitKn[m], collarLimitKn[m],
                bandLimitKn[m], bandCollarLimitKn[m], depthPriceCoef[m]
            )
        }
    }

    companion object {
        /**
         * **Extracts a rasterised tile's water into the sparse form.** The rasteriser ran over [grid] —
         * the block grown by [haloCells] on every side so its depth bound saw the collar's wall — and
         * only the **inner** block is kept. Every inner cell the fill left passable is a member, and its
         * stored tag, its cost, its four limits and its depth coefficient are read straight into the
         * parallel arrays. The member's local index is its position in the block, `row * tileCells + col`,
         * so [writeInto] recovers it without a map.
         *
         * The block is **pinned to exactly [tileCells] × [tileCells] by construction**, never inferred by
         * subtracting the halo from the rasterised box: the rasteriser's `ceil` can round the grown outer
         * box one cell wide, so the subtraction would yield 64 or 65 depending on the tile's offset and
         * would shift every member silently on a rounding change (D45). The block is read instead as the
         * **first** [tileCells] rows and columns past the halo — the stray extra cell always lands last,
         * on the north or east edge, so it is dropped — and a raster too small for the block `require`s
         * loudly.
         *
         * That "last" rests on a **premise** (D58): [`rasterizeFrame`] lays the raster's origin at the
         * outer box's own south-west with **no outward snap**, and `ceil` can only add at the far edge, so
         * the extra row or column is always the northernmost or easternmost one. A future rasteriser that
         * snapped its origin outward would move the stray cell to the *first* row instead, and this read
         * would silently take it — the premise is stated here so that change cannot be invisible.
         */
        fun extract(
            grid: MultipassGrid,
            tileRow: Int,
            tileCol: Int,
            tileCells: Int,
            haloCells: Int
        ): FineTile {
            val availableRows = grid.rows - 2 * haloCells
            val availableCols = grid.cols - 2 * haloCells
            require(availableRows >= tileCells && availableCols >= tileCells) {
                "a tile's inner block must hold at least tileCells × tileCells, but the rasteriser's " +
                    "${grid.rows}×${grid.cols} box minus a $haloCells-cell halo is " +
                    "${availableRows}×${availableCols}"
            }
            var members = 0
            for (row in haloCells until haloCells + tileCells) {
                for (col in haloCells until haloCells + tileCells) if (grid.passable(row, col)) members++
            }
            val state = ByteArray(members)
            val sourceCostSec = DoubleArray(members)
            val zoneLimitKn = DoubleArray(members)
            val collarLimitKn = DoubleArray(members)
            val bandLimitKn = DoubleArray(members)
            val bandCollarLimitKn = DoubleArray(members)
            val depthPriceCoef = DoubleArray(members)
            val localIndex = IntArray(members)
            var m = 0
            for (row in haloCells until haloCells + tileCells) {
                for (col in haloCells until haloCells + tileCells) {
                    if (!grid.passable(row, col)) continue
                    state[m] = grid.stateOrdinal(row, col)
                    sourceCostSec[m] = grid.sourceCostSec(row, col)
                    zoneLimitKn[m] = grid.zoneLimitKn(row, col)
                    collarLimitKn[m] = grid.collarLimitKn(row, col)
                    bandLimitKn[m] = grid.bandLimitKn(row, col)
                    bandCollarLimitKn[m] = grid.bandCollarLimitKn(row, col)
                    depthPriceCoef[m] = grid.depthPriceCoef(row, col)
                    localIndex[m] = (row - haloCells) * tileCells + (col - haloCells)
                    m++
                }
            }
            return FineTile(
                tileRow = tileRow, tileCol = tileCol, tileCells = tileCells,
                rows = tileCells, cols = tileCells,
                latSouth = grid.latSouth + haloCells * grid.cellSizeDegLat,
                lonWest = grid.lonWest + haloCells * grid.cellSizeDegLon,
                cellSizeDegLat = grid.cellSizeDegLat, cellSizeDegLon = grid.cellSizeDegLon,
                cellM = grid.cellM, baseCostSec = grid.baseCostSec,
                state = state, sourceCostSec = sourceCostSec, zoneLimitKn = zoneLimitKn,
                collarLimitKn = collarLimitKn, bandLimitKn = bandLimitKn,
                bandCollarLimitKn = bandCollarLimitKn, depthPriceCoef = depthPriceCoef,
                localIndex = localIndex
            )
        }
    }
}
