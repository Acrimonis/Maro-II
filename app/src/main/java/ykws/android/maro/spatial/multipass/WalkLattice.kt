package ykws.android.maro.spatial.multipass

import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.spatial.SpatialOperations
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor

/** Packs a lattice coordinate into one key: the row in the high half, the column in the low half. */
private fun packCell(row: Int, col: Int): Long = (row.toLong() shl 32) or (col.toLong() and 0xFFFF_FFFFL)

/**
 * **One lattice: an origin and one cell-size pair every rectangle on it shares.** A window is built with
 * these, so two rectangles' cells line up by arithmetic and a neighbour across the seam between them is an
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

/** **One window onto a lattice**: a dense rectangle, and where its first cell stands on the lattice. */
internal data class WalkWindow(
    val grid: MultipassGrid,
    val rowOffset: Int,
    val colOffset: Int
) {
    /** Whether this window carries the lattice cell at [row], [col]. */
    fun holds(row: Int, col: Int): Boolean =
        row - rowOffset in 0 until grid.rows && col - colOffset in 0 until grid.cols
}

/**
 * **The water one walk may use, as windows on one lattice** — the uniform pass's is one grid, the chain's
 * several, and the A\* sees neither: it asks this for a slot, a cell and a centre.
 *
 * The single-window case is deliberately **arithmetic and allocation-free**: the lattice is null, a slot is
 * the grid's own `row * cols + col` and a centre is the grid's own — so every existing answer, tie-break and
 * reading is the one the suite already proves. The multi-window case is the sparse id map the adaptive grid
 * pins: a packed lattice coordinate to a slot, and the slot back, built once per walk.
 */
internal class WalkWindows private constructor(
    val windows: List<WalkWindow>,
    private val lattice: WalkLattice?,
    private val slots: HashMap<Long, Int>?,
    private val tiles: IntArray?,
    private val ids: LongArray?
) {
    /** How many walkable slots the walk holds — one per **unique** lattice cell, overlaps counted once. */
    val size: Int get() = tiles?.size ?: windows[0].grid.rows * windows[0].grid.cols

    /**
     * The slot a lattice coordinate takes, or `-1` where no window holds it — the walk's own domain, so a
     * neighbour standing outside every rectangle is simply not a step.
     */
    fun slotOf(row: Int, col: Int): Int {
        val map = slots ?: run {
            val grid = windows[0].grid
            return if (row in 0 until grid.rows && col in 0 until grid.cols) row * grid.cols + col else -1
        }
        return map[packCell(row, col)] ?: -1
    }

    /** The lattice row a slot stands on. */
    fun rowOf(slot: Int): Int = ids?.let { (it[slot] shr 32).toInt() } ?: (slot / windows[0].grid.cols)

    /** The lattice column a slot stands on. */
    fun colOf(slot: Int): Int = ids?.let { it[slot].toInt() } ?: (slot % windows[0].grid.cols)

    /** The centre of a lattice coordinate — the grid's own read for one window, the lattice's otherwise. */
    fun center(row: Int, col: Int): LatLng = lattice?.center(row, col) ?: windows[0].grid.center(row, col)

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
         * Windows on one lattice, their slots allocated row-major per window so a coordinate two windows
         * share takes the first one's slot — one slot per **lattice** cell, never per window's copy of it.
         */
        fun onLattice(lattice: WalkLattice, windows: List<WalkWindow>): WalkWindows {
            val slots = HashMap<Long, Int>()
            val tiles = ArrayList<Int>()
            val ids = ArrayList<Long>()
            for ((index, window) in windows.withIndex()) {
                for (row in 0 until window.grid.rows) {
                    for (col in 0 until window.grid.cols) {
                        val id = packCell(row + window.rowOffset, col + window.colOffset)
                        if (slots.containsKey(id)) continue
                        slots[id] = tiles.size
                        tiles.add(index)
                        ids.add(id)
                    }
                }
            }
            return WalkWindows(windows, lattice, slots, tiles.toIntArray(), ids.toLongArray())
        }
    }
}
