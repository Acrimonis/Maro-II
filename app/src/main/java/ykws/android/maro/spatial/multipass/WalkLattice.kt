package ykws.android.maro.spatial.multipass

import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.spatial.SpatialOperations
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt

/** The bits a packed identity gives the layer; the row and the column carry twenty-four each. */
private const val LAYER_SHIFT = 48
private const val FIELD_BITS = 24
private const val FIELD_MASK = 0xFF_FFFFL
private const val FIELD_SIGN = 0x80_0000
private const val FIELD_SIGN_EXTEND = 0x1_00_0000

/**
 * Packs an identity into one key: **the layer, the row and the column**. It carries the layer because a
 * coarse cell and a fine cell of the same `(row, col)` are different squares, and a key that ignored the
 * layer would keep the first and silently drop the second — the quiet cell-eater Phase 4's (b) exists for.
 */
private fun packCell(layer: Int, row: Int, col: Int): Long =
    (layer.toLong() shl LAYER_SHIFT) or
        ((row.toLong() and FIELD_MASK) shl FIELD_BITS) or
        (col.toLong() and FIELD_MASK)

/** The layer a packed key carries. */
private fun keyLayer(key: Long): Int = (key ushr LAYER_SHIFT).toInt()

/** The row a packed key carries, sign-extended from its twenty-four bits — a chain may reach negative. */
private fun keyRow(key: Long): Int = signExtend(((key ushr FIELD_BITS) and FIELD_MASK).toInt())

/** The column a packed key carries, sign-extended. */
private fun keyCol(key: Long): Int = signExtend((key and FIELD_MASK).toInt())

private fun signExtend(value: Int): Int =
    if (value and FIELD_SIGN != 0) value - FIELD_SIGN_EXTEND else value

/**
 * **One layer of a lattice family: an origin and one cell-size pair.** A window is built with these, so
 * two rectangles on the same layer line up by arithmetic and a neighbour across the seam between them is an
 * index relation rather than a search. The pair is derived **once**, from the corridor's own mid-latitude:
 * the degrees a cell spans fall with `cos φ`, so a pair derived per box would stand two boxes on two lattices.
 */
internal class WalkLattice(
    val latSouth: Double,
    val lonWest: Double,
    val cellM: Double,
    val cellSizeDegLat: Double,
    val cellSizeDegLon: Double,
    private val mPerDegLat: Double,
    private val mPerDegLon: Double
) {
    /** The lattice row a latitude stands on — negative south of the origin, which a chain may reach. */
    fun rowOf(latitude: Double): Int = floor((latitude - latSouth) / cellSizeDegLat).toInt()

    /** The lattice column a longitude stands on. */
    fun colOf(longitude: Double): Int = floor((longitude - lonWest) / cellSizeDegLon).toInt()

    /** The centre of one lattice cell — the walk's own point read, whichever window holds it. */
    fun center(row: Int, col: Int): LatLng =
        LatLng(latSouth + (row + 0.5) * cellSizeDegLat, lonWest + (col + 0.5) * cellSizeDegLon)

    /**
     * The axis-aligned box of half-side [halfWidthM] centred on [centre] — the corridor chain's own
     * square, written in the degrees this lattice's cells are, so `2 × w` is exact in metres and the box
     * lands on the same arithmetic the cells do.
     */
    fun boxAround(centre: LatLng, halfWidthM: Double): BBox = BBox(
        centre.latitude - halfWidthM / mPerDegLat,
        centre.latitude + halfWidthM / mPerDegLat,
        centre.longitude - halfWidthM / mPerDegLon,
        centre.longitude + halfWidthM / mPerDegLon
    )

    /** [box] snapped **outward** to the lattice's own lines, so a window never cuts a cell in half. */
    fun snapOutward(box: BBox): BBox = BBox(
        latSouth + floor((box.latSouth - latSouth) / cellSizeDegLat) * cellSizeDegLat,
        latSouth + ceil((box.latNorth - latSouth) / cellSizeDegLat) * cellSizeDegLat,
        lonWest + floor((box.lonWest - lonWest) / cellSizeDegLon) * cellSizeDegLon,
        lonWest + ceil((box.lonEast - lonWest) / cellSizeDegLon) * cellSizeDegLon
    )

    /** The degrees-per-metre pair the rasterizer's own sweeps expand in — the lattice's, not a box's. */
    fun metresPerDegree(): Pair<Double, Double> = mPerDegLat to mPerDegLon

    companion object {
        /**
         * The lattice a corridor walks on: the origin at the corridor's own south-west and the pair derived
         * from its mid-latitude. Two boxes snapped onto it therefore share every cell centre they overlap.
         */
        fun of(corridor: BBox, cellM: Double): WalkLattice {
            val midLat = (corridor.latSouth + corridor.latNorth) / 2.0
            val mPerDegLat = SpatialOperations.EARTH_RADIUS_M * PI / 180.0
            val mPerDegLon = mPerDegLat * cos(Math.toRadians(midLat))
            return WalkLattice(
                corridor.latSouth, corridor.lonWest, cellM,
                cellM / mPerDegLat, cellM / mPerDegLon, mPerDegLat, mPerDegLon
            )
        }
    }
}

/**
 * **The lattice family: one origin and one cell-size pair per resolution, in an exact integer ratio.**
 *
 * The one-lattice precondition, strengthened for two resolutions: the coarse pair is derived as **exactly
 * `ratio ×`** the fine pair on the same origin, so each coarse cell covers an integer `ratio × ratio` block
 * of fine cells and the seam's neighbourhood is a fixed relation rather than a search. The layers are
 * ordered **coarse first** — the interior is layer 0 and the band layer 1 — so a layer-agnostic lookup
 * resolves to the interior, which is the layer the first walk's own answers are found on.
 */
internal class LatticeFamily(
    val coarse: WalkLattice,
    val fine: WalkLattice,
    val ratio: Int
) {
    /** The layers, **coarse first**: the interior is layer 0 and the fine band layer 1. */
    val layers: List<WalkLattice> get() = listOf(coarse, fine)

    companion object {
        /**
         * The family from a corridor's mid-latitude: the fine pair at [fineCellM] and the coarse pair
         * derived as exactly `ratio ×` it on the same origin, `ratio` being the two cells' integer ratio.
         * Both pairs are therefore exact in metres and the fine cells nest exactly inside the coarse ones.
         */
        fun of(corridor: BBox, coarseCellM: Double, fineCellM: Double): LatticeFamily {
            require(coarseCellM > 0.0 && fineCellM > 0.0) { "a lattice cell is a positive size" }
            require(coarseCellM >= fineCellM) { "the coarse cell is never finer than the fine one" }
            val ratio = (coarseCellM / fineCellM).roundToInt().coerceAtLeast(1)
            val fine = WalkLattice.of(corridor, fineCellM)
            val (mPerDegLat, mPerDegLon) = fine.metresPerDegree()
            val coarse = WalkLattice(
                corridor.latSouth, corridor.lonWest, fine.cellM * ratio,
                fine.cellSizeDegLat * ratio, fine.cellSizeDegLon * ratio, mPerDegLat, mPerDegLon
            )
            return LatticeFamily(coarse, fine, ratio)
        }
    }
}

/** **One window onto one lattice layer**: a dense rectangle, and where its first cell stands on the layer. */
internal data class WalkWindow(
    val grid: MultipassGrid,
    val rowOffset: Int,
    val colOffset: Int,
    /** Which lattice layer this window stands on — the family's index, 0 where there is one lattice. */
    val layer: Int = 0
) {
    /** Whether this window carries the lattice cell at [row], [col]. */
    fun holds(row: Int, col: Int): Boolean =
        row - rowOffset in 0 until grid.rows && col - colOffset in 0 until grid.cols
}

/**
 * **The water one walk may use, as windows on one lattice family** — the uniform pass's is one grid, a
 * chain's several on one layer, and the adaptive grid's two on two layers; the A\* sees none of that: it
 * asks this for a slot, a cell and a centre.
 *
 * The single-window case is deliberately **arithmetic and allocation-free**: the lattices are null, a slot
 * is the grid's own `row * cols + col` and a centre is the grid's own — so every existing answer, tie-break
 * and reading is the one the suite already proves. The multi-window case is the sparse id map the adaptive
 * grid pins: a packed `(layer, row, col)` to a slot, and the slot back, built once per walk — the layer in
 * the key so a coarse cell and a fine cell over the same water each keep their own slot.
 */
internal class WalkWindows private constructor(
    val windows: List<WalkWindow>,
    private val lattices: List<WalkLattice>?,
    private val slots: HashMap<Long, Int>?,
    private val tiles: IntArray?,
    private val ids: LongArray?
) {
    /** How many walkable slots the walk holds — one per **unique** lattice cell, overlaps counted once. */
    val size: Int get() = tiles?.size ?: windows[0].grid.rows * windows[0].grid.cols

    /** How many layers this walk spans — 1 for a uniform grid or a one-lattice chain. */
    val layerCount: Int get() = lattices?.size ?: 1

    /**
     * The slot the lattice coordinate `(row, col)` takes **on [layer]**, or `-1` where no window holds it —
     * the walk's own domain, so a neighbour standing outside every rectangle is simply not a step.
     */
    fun slotOf(layer: Int, row: Int, col: Int): Int {
        val map = slots ?: run {
            if (layer != 0) return -1
            val grid = windows[0].grid
            return if (row in 0 until grid.rows && col in 0 until grid.cols) row * grid.cols + col else -1
        }
        return map[packCell(layer, row, col)] ?: -1
    }

    /**
     * The slot a lattice coordinate takes, resolved through the **first layer that holds it** — the coarse
     * interior first, so an end standing in the corridor anchors on the layer the first walk runs on.
     */
    fun slotOf(row: Int, col: Int): Int {
        if (slots == null) return slotOf(0, row, col)
        for (layer in 0 until layerCount) {
            val slot = slotOf(layer, row, col)
            if (slot >= 0) return slot
        }
        return -1
    }

    /** The lattice row a slot stands on. */
    fun rowOf(slot: Int): Int = ids?.let { keyRow(it[slot]) } ?: (slot / windows[0].grid.cols)

    /** The lattice column a slot stands on. */
    fun colOf(slot: Int): Int = ids?.let { keyCol(it[slot]) } ?: (slot % windows[0].grid.cols)

    /** The layer a slot stands on — 0 for a uniform grid or a one-lattice chain. */
    fun layerOf(slot: Int): Int = ids?.let { keyLayer(it[slot]) } ?: 0

    /** The centre of a lattice coordinate on [layer] — the layer's own read, or the one grid's otherwise. */
    fun center(layer: Int, row: Int, col: Int): LatLng =
        lattices?.getOrNull(layer)?.center(row, col) ?: windows[0].grid.center(row, col)

    /** The centre of a slot — its own layer and coordinate, so a two-layer walk resolves each exactly. */
    fun centerOf(slot: Int): LatLng = center(layerOf(slot), rowOf(slot), colOf(slot))

    /** The centre of a lattice coordinate, resolved through the first layer that holds it. */
    fun center(row: Int, col: Int): LatLng {
        val slot = slotOf(row, col)
        return if (slot >= 0) centerOf(slot) else center(0, row, col)
    }

    /** The cell's own data, read from the window that holds it. */
    fun cell(slot: Int): MultipassCell {
        val tile = tiles?.get(slot) ?: 0
        val window = windows[tile]
        return window.grid.cell(rowOf(slot) - window.rowOffset, colOf(slot) - window.colOffset)
    }

    /** The strictest limit in force on the slot's cell, in knots. */
    fun limitKn(slot: Int): Double {
        val tile = tiles?.get(slot) ?: 0
        val window = windows[tile]
        return window.grid.limitKn(rowOf(slot) - window.rowOffset, colOf(slot) - window.colOffset)
    }

    /** The slot's outside-margin limit, in knots. */
    fun collarLimitKn(slot: Int): Double {
        val tile = tiles?.get(slot) ?: 0
        val window = windows[tile]
        return window.grid.collarLimitKn(rowOf(slot) - window.rowOffset, colOf(slot) - window.colOffset)
    }

    /** The slot's band-outside-margin limit, in knots. */
    fun bandCollarLimitKn(slot: Int): Double {
        val tile = tiles?.get(slot) ?: 0
        val window = windows[tile]
        return window.grid.bandCollarLimitKn(rowOf(slot) - window.rowOffset, colOf(slot) - window.colOffset)
    }

    /** How many of the walk's unique cells are passable — the search's own reading, taken once. */
    fun passableCount(): Int {
        var count = 0
        for (slot in 0 until size) if (cell(slot).passable) count++
        return count
    }

    companion object {
        /** One window over [grid], its slots the grid's own indices and its centres the grid's own. */
        fun of(grid: MultipassGrid): WalkWindows =
            WalkWindows(listOf(WalkWindow(grid, 0, 0)), null, null, null, null)

        /**
         * Windows on **one** lattice, their slots allocated row-major per window so a coordinate two windows
         * share takes the first one's slot — one slot per **lattice** cell, never per window's copy of it.
         */
        fun onLattice(lattice: WalkLattice, windows: List<WalkWindow>): WalkWindows =
            onLattice(listOf(lattice), windows)

        /**
         * Windows on the layers of a **lattice family**, their slots keyed by `(layer, row, col)` so two
         * layers over the same water each keep their own cells — the identity a coordinate-only key collapses.
         */
        fun onLattice(lattices: List<WalkLattice>, windows: List<WalkWindow>): WalkWindows {
            val slots = HashMap<Long, Int>()
            val tiles = ArrayList<Int>()
            val ids = ArrayList<Long>()
            for ((index, window) in windows.withIndex()) {
                for (row in 0 until window.grid.rows) {
                    for (col in 0 until window.grid.cols) {
                        val id = packCell(window.layer, row + window.rowOffset, col + window.colOffset)
                        if (slots.containsKey(id)) continue
                        slots[id] = tiles.size
                        tiles.add(index)
                        ids.add(id)
                    }
                }
            }
            return WalkWindows(windows, lattices, slots, tiles.toIntArray(), ids.toLongArray())
        }
    }
}
