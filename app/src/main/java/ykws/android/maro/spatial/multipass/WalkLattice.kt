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
 * The tolerance a point→cell read adds before its `floor`, in **cell units**. A fixed anchor's cell lines
 * are reached by adding an integer multiple of the cell size to the anchor, and that round trip leaves the
 * ratio a hair below its integer; the addend recovers the line the point stands on rather than the cell
 * below it. It is far below any real offset — one billionth of a cell, about `2×10⁻⁸ m` (a few tens of
 * nanometres) at a 20 m cell — so it moves no honest read.
 */
private const val LATTICE_SNAP_EPS = 1e-9

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
 * **The one derivation of a lattice pair's integer ratio (D19)** — the coarse cell over the fine one,
 * rounded to the nearest integer and floored at one. It is the single home of the convention:
 * [`LatticeFamily.of`]'s own `ratio`, [`WalkWindows.layerRatio`] and the seam's neighbour arithmetic in
 * [`WalkWindows.crossLayerSlots`] all read this expression, so the exact `1 : ratio` nesting they share
 * cannot drift apart. The floor at one is what makes a permitted pair a read rather than a crash; the seam
 * guards a ratio of one separately, since it names no crossing.
 */
internal fun latticeRatioOf(coarseCellM: Double, fineCellM: Double): Int =
    (coarseCellM / fineCellM).roundToInt().coerceAtLeast(1)

/**
 * **One layer of a lattice family: an origin and one cell-size pair.** A window is built with these, so
 * two rectangles on the same layer line up by arithmetic and a neighbour across the seam between them is an
 * index relation rather than a search. The pair is derived **once** — at [LatticeAnchor]'s fixed reference
 * latitude for a family, at the corridor's own mid-latitude for the single-grid lattice: the degrees a cell
 * spans fall with `cos φ`, so a pair derived per box would stand two boxes on two lattices.
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
    fun rowOf(latitude: Double): Int = floor((latitude - latSouth) / cellSizeDegLat + LATTICE_SNAP_EPS).toInt()

    /** The lattice column a longitude stands on. */
    fun colOf(longitude: Double): Int = floor((longitude - lonWest) / cellSizeDegLon + LATTICE_SNAP_EPS).toInt()

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
 * **The fixed anchor the walk lattices are drawn from** — the south-west corner the family's grid lines pass
 * through, and the latitude its metre→degree pair is derived at. Where the world names a depth raster it is
 * read from that raster's own region, so the same water carries the same lattice indices on every arm and
 * the fine layer can later be cached tile by tile. A world naming no raster falls back to [wholeDegree]: the
 * **whole degree** at or below the corridor's south-west — still one origin per degree cell rather than per
 * arm, since any two corridors in the same degree share it, but snapping the corridor's own corner is what
 * the fallback does. Both of [`LatticeFamily`]'s lattices share the one anchor, because the seam's exact
 * `1 : ratio` nesting is arithmetic on that single origin.
 */
data class LatticeAnchor(
    val latSouth: Double,
    val lonWest: Double,
    val referenceLat: Double
) {
    companion object {
        /**
         * **The whole-degree fallback**, for a world that names no depth raster. The origin is the whole
         * degree at or below [box]'s own south-west — deterministic for any two corridors whose south-west
         * falls in the same degree cell — and the reference latitude is that whole degree's centre.
         */
        fun wholeDegree(box: BBox): LatticeAnchor = LatticeAnchor(
            floor(box.latSouth), floor(box.lonWest), floor(box.latSouth) + 0.5
        )
    }
}

/**
 * **The lattice family: one origin and one cell-size pair per resolution, in an exact integer ratio.**
 *
 * The one-lattice precondition, strengthened for two resolutions: the coarse pair is derived as **exactly
 * `ratio ×`** the fine pair on the same origin, so each coarse cell covers an integer `ratio × ratio` block
 * of fine cells and the seam's neighbourhood is a fixed relation rather than a search. The layers are
 * ordered **coarse first** — the interior is layer 0 and the band layer 1 — so the family's own indices
 * read coarse-then-fine; **which layer the walk reads is a separate decision**, and it is the priority
 * [`WalkWindows`] applies: a passable fine cell standing over the same water supersedes the coarse cell.
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
         * The family on the **fixed [anchor]**, never on the corridor: the fine pair at [fineCellM] and the
         * coarse pair derived as exactly `ratio ×` it on the **same** anchor origin and reference latitude,
         * `ratio` being the two cells' integer ratio. Both pairs are therefore exact in metres and the fine
         * cells nest exactly inside the coarse ones — the nesting the seam's neighbourhood arithmetic and
         * [`fineCopyOf`] rest on, and the reason one anchor must serve both layers.
         *
         * The ratio is any integer from 1 up, **even ones included**: a shipped clamp settles one (a 100 m
         * coarse cell against a 10 m fine cell is a ratio of 10, and 40 against 20 a ratio of 2), so refusing
         * an even one would be a crash a permitted setting reaches. The priority's fine copy still names a
         * coarse cell by the fine cell at its centre, and [`fineCopyOf`] resolves the even ratio's centre tie
         * on a **stated convention** instead of assuming an odd ratio.
         */
        fun of(anchor: LatticeAnchor, coarseCellM: Double, fineCellM: Double): LatticeFamily {
            require(coarseCellM > 0.0 && fineCellM > 0.0) { "a lattice cell is a positive size" }
            require(coarseCellM >= fineCellM) { "the coarse cell is never finer than the fine one" }
            val ratio = latticeRatioOf(coarseCellM, fineCellM)
            val mPerDegLat = SpatialOperations.EARTH_RADIUS_M * PI / 180.0
            val mPerDegLon = mPerDegLat * cos(Math.toRadians(anchor.referenceLat))
            val fine = WalkLattice(
                anchor.latSouth, anchor.lonWest, fineCellM,
                fineCellM / mPerDegLat, fineCellM / mPerDegLon, mPerDegLat, mPerDegLon
            )
            val coarse = WalkLattice(
                anchor.latSouth, anchor.lonWest, fine.cellM * ratio,
                fine.cellSizeDegLat * ratio, fine.cellSizeDegLon * ratio, mPerDegLat, mPerDegLon
            )
            return LatticeFamily(coarse, fine, ratio)
        }
    }
}

/** The empty cross-layer answer — a walk with one lattice, or a cell with no cell across the seam. */
private val NO_SLOTS = IntArray(0)

/**
 * **The many-to-one seam neighbourhood** — the fixed relation the exact `1 : ratio` nesting makes
 * arithmetic instead of a search.
 *
 * A coarse cell's **face** meets `ratio` fine cells and its **corner** one, so a step off the interior
 * lands in the fine band; a fine cell reaches a coarse cell across the seam only where it stands on its
 * own block's face, so a step off the band lands in the interior. Both directions describe the **same
 * edge set**, which is what lets a path cross the seam either way.
 */
internal object SeamNeighbours {

    /** The fine coordinates across the seam from coarse `(row, col)` in direction `(dr, dc)`. */
    fun acrossFromCoarse(
        row: Int, col: Int, dr: Int, dc: Int, ratio: Int, fineLayer: Int
    ): List<CellIndex> {
        if (dr == 0 && dc == 0) return emptyList()
        val r0 = row * ratio
        val c0 = col * ratio
        val rows = when {
            dr < 0 -> intArrayOf(r0 - 1)
            dr > 0 -> intArrayOf(r0 + ratio)
            else -> IntArray(ratio) { r0 + it }
        }
        val cols = when {
            dc < 0 -> intArrayOf(c0 - 1)
            dc > 0 -> intArrayOf(c0 + ratio)
            else -> IntArray(ratio) { c0 + it }
        }
        val out = ArrayList<CellIndex>(rows.size * cols.size)
        for (r in rows) for (c in cols) out.add(CellIndex(r, c, fineLayer))
        return out
    }

    /**
     * The coarse coordinate across the seam from fine `(row, col)` in direction `(dr, dc)`, or `null`
     * where the fine cell stands **inside** its block rather than on the face that step leaves by — a
     * same-water move that is not a crossing.
     */
    fun acrossFromFine(
        row: Int, col: Int, dr: Int, dc: Int, ratio: Int, coarseLayer: Int
    ): CellIndex? {
        if (dr == 0 && dc == 0) return null
        val r = floorDiv(row, ratio)
        val c = floorDiv(col, ratio)
        val onSouth = row - r * ratio == 0           // the block's low face
        val onNorth = row - r * ratio == ratio - 1   // the block's high face
        val onWest = col - c * ratio == 0
        val onEast = col - c * ratio == ratio - 1
        val tr = when {
            dr < 0 -> if (onSouth) r - 1 else return null
            dr > 0 -> if (onNorth) r + 1 else return null
            else -> r
        }
        val tc = when {
            dc < 0 -> if (onWest) c - 1 else return null
            dc > 0 -> if (onEast) c + 1 else return null
            else -> c
        }
        return CellIndex(tr, tc, coarseLayer)
    }

    /** Floor division that rounds toward negative infinity, so a chain reaching south of the origin works. */
    private fun floorDiv(a: Int, b: Int): Int {
        val q = a / b
        return if (a % b != 0 && (a xor b) < 0) q - 1 else q
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
 * **A walk's slot index — the three pieces `onLattice` derives**: the packed `(layer, row, col)` → slot map
 * and its two mirror reads, slot → window tile and slot → packed identity.
 */
internal class WalkSlotIndex(
    val slots: HashMap<Long, Int>,
    val tiles: IntArray,
    val ids: LongArray
)

/**
 * **The walk's slot index, one entry keyed on the windows' shape (D18).**
 *
 * The index — `(layer, row, col)` → slot and its two mirror arrays — is a pure function of the windows'
 * **shape** (each window's layer, its lattice offset and its extent), never of their contents, so an arm
 * whose windows carry the same shape reuses the previous arm's index instead of walking every cell again.
 * The contents differ per arm (the berth carve writes each arm's own cells), so the [`WalkWindows`] itself
 * is rebuilt over the caller's own windows; only the index is shared, and it is read-only once built. One
 * entry, newest wins, the [`SelectiveMaskCache`] shape; [`clear`] is the test's own door.
 */
internal object WalkIndexCache {

    private var shape: IntArray? = null
    private var index: WalkSlotIndex? = null

    /** How many times the index has actually been built — the reading a reuse is pinned by. */
    @Volatile
    var buildCount: Int = 0
        private set

    /** The index for [windows], reused where the shape is unchanged and rebuilt otherwise. */
    @Synchronized
    fun getOrBuild(windows: List<WalkWindow>): WalkSlotIndex {
        val requested = shapeOf(windows)
        val cached = index
        if (cached != null && requested.contentEquals(shape)) return cached
        val built = build(windows)
        shape = requested
        index = built
        buildCount++
        return built
    }

    /** Drops the one entry — the test's own door. */
    @Synchronized
    fun clear() {
        shape = null
        index = null
        buildCount = 0
    }

    /** The windows' **shape**, flattened: each window's layer, offset and extent, in order. */
    private fun shapeOf(windows: List<WalkWindow>): IntArray {
        val shape = IntArray(windows.size * 5)
        for ((i, window) in windows.withIndex()) {
            shape[i * 5] = window.layer
            shape[i * 5 + 1] = window.rowOffset
            shape[i * 5 + 2] = window.colOffset
            shape[i * 5 + 3] = window.grid.rows
            shape[i * 5 + 4] = window.grid.cols
        }
        return shape
    }

    /**
     * The index itself: slots allocated row-major per window, so a coordinate two windows share takes the
     * first one's slot — one slot per **lattice** cell, never per window's copy of it — with the layer in
     * the key so a coarse and a fine cell over the same water each keep their own.
     */
    private fun build(windows: List<WalkWindow>): WalkSlotIndex {
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
        return WalkSlotIndex(slots, tiles.toIntArray(), ids.toLongArray())
    }
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
 *
 * **The walk reads the fine cell over the coarse copy.** Where a passable fine cell stands over the same
 * water as a coarse cell, that fine cell is the one the walk reads and the coarse cell is not read at all;
 * where the fine grid is land (or holds no cell there), the coarse cell's role is its own. It is a rule of
 * the reading alone — no grid's content moves — and a single-grid walk, whose lattices are null, reads
 * exactly as it always did.
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

    /** The **coarse** layer's index — the larger cell — or `-1` where the walk spans fewer than two lattices. */
    private val coarseLayerIndex: Int
        get() {
            val l = lattices ?: return -1
            if (l.size < 2) return -1
            return if (l[0].cellM >= l[1].cellM) 0 else 1
        }

    /** The **fine** layer's index, or `-1` where the walk spans one lattice. */
    private val fineLayerIndex: Int get() = if (coarseLayerIndex < 0) -1 else 1 - coarseLayerIndex

    /** The exact `1 : ratio` nesting between the two layers, or `1` where the walk spans one lattice. */
    private val layerRatio: Int
        get() {
            val coarse = coarseLayerIndex
            if (coarse < 0) return 1
            return latticeRatioOf(lattices!![coarse].cellM, lattices!![fineLayerIndex].cellM)
        }

    /**
     * **The fine copy of a coarse-layer cell** — the fine cell the rule reads over the coarse cell, named by
     * the coarse cell's own point read: a cell is read by its centre, and the centre of a coarse cell is the
     * **central fine cell** of its `ratio × ratio` block. For an **odd** ratio that centre falls in the
     * middle fine cell, which `layerRatio / 2` names exactly. For an **even** ratio it falls on a
     * fine-lattice **vertex** shared by four fine cells, and the tie resolves the way [`WalkLattice.rowOf`]
     * and [`WalkLattice.colOf`] resolve any point standing on a lattice line — `floor` — taking the cell
     * whose low (south-west) corner is that vertex, the cell **north-east of the centre**. That is a **stated
     * convention**, not the same water: the four cells share only the vertex, so the reading stays
     * deterministic and a permitted even ratio is a read rather than a crash. `-1` where no such fine cell is
     * held or the one held is land — there the coarse cell has no copy and its role is its own.
     */
    private fun fineCopyOf(coarseRow: Int, coarseCol: Int): Int {
        val fine = fineLayerIndex
        if (fine < 0) return -1
        val half = layerRatio / 2
        val slot = rawSlotOf(fine, coarseRow * layerRatio + half, coarseCol * layerRatio + half)
        return if (slot >= 0 && passable(slot)) slot else -1
    }

    /**
     * The fine cell that **supersedes** the coarse-layer cell `(row, col)` for a step on [layer], or `-1`
     * where the coarse cell is read as it stands: the fine copy on the coarse layer, and never one on the
     * fine layer, whose cells are superseded by nothing.
     */
    fun supersedingSlot(layer: Int, row: Int, col: Int): Int =
        if (layer == coarseLayerIndex) fineCopyOf(row, col) else -1

    /**
     * **The raw address of `(row, col)` on one named [layer]** — the layer's own answer, `-1` where no
     * window holds it. It **bypasses the reading rule**, which is why its name says *raw*: it is never the
     * coordinate's read, and a reader must not mistake it for one.
     *
     * A caller that wants the cell the walk *reads* at a coordinate wants [`slotOf(row, col)`] instead: that
     * one asks whether a passable fine copy supersedes a coarse cell and answers the fine copy where one
     * does — the coordinate's own point read. A caller that wants a **named layer's own slot** wants this
     * one, and none of them may take the reading's choice: the walk's own layer-local neighbour step, the
     * seam enumerating the other layer's coordinates, the rule-resolved lookup asking whether a layer holds
     * a cell, and [`cellSizeAt`] probing one named layer. Where a caller does want a **coarse-layer** slot as
     * the cell to read, it must first ask [`supersedingSlot`] (or [`fineCopyOf`] directly) and take the fine
     * copy where one supersedes; [`MultipassSearch`]'s neighbour step guards exactly that way, and
     * [`crossLayerSlots`] redirects a superseded coarse target to its fine copy.
     */
    fun rawSlotOf(layer: Int, row: Int, col: Int): Int {
        val map = slots ?: run {
            if (layer != 0) return -1
            val grid = windows[0].grid
            return if (row in 0 until grid.rows && col in 0 until grid.cols) row * grid.cols + col else -1
        }
        return map[packCell(layer, row, col)] ?: -1
    }

    /**
     * The slot a lattice coordinate takes, resolved through the walk's own reading rule. Where the
     * coordinate is a **coarse-layer** cell and a passable fine cell stands over the same water, the fine
     * cell is the one the walk reads and the coarse copy is not; every other coordinate resolves through
     * the **first layer that holds it** — the coarse interior first, so an end standing in open corridor
     * water anchors on the layer the first walk runs on.
     */
    fun slotOf(row: Int, col: Int): Int {
        if (slots == null) return rawSlotOf(0, row, col)
        val coarse = coarseLayerIndex
        if (coarse >= 0 && rawSlotOf(coarse, row, col) >= 0) {
            val copy = fineCopyOf(row, col)
            if (copy >= 0) return copy
        }
        for (layer in 0 until layerCount) {
            val slot = rawSlotOf(layer, row, col)
            if (slot >= 0) return slot
        }
        return -1
    }

    /**
     * **The walk's own lattice coordinate for a cell of [grid] named locally by `(row, col)`** — the
     * translation a two-layer walk needs because its seeds and its carve arrive in a window's **local**
     * index space while [`slotOf`] reads the family's lattice coordinates. The window that holds [grid]
     * supplies its offset; a single-window walk holds its grid as window zero, so that offset is zero there
     * and the coordinate is the grid's own. The corridor-anchored family made the two coincide everywhere; a
     * fixed anchor does not, which is the P4.1 case this exists for.
     *
     * [grid] **must be one of this walk's own windows**: a grid that is not is a caller asking for a
     * coordinate this walk cannot name, and answering the local index unchanged would hand back a wrong
     * coordinate silently, so a non-window grid is rejected rather than answered.
     */
    fun latticeCell(grid: MultipassGrid, row: Int, col: Int): CellIndex {
        val window = requireNotNull(windows.firstOrNull { it.grid === grid }) {
            "latticeCell wants a cell of a grid this walk holds as a window"
        }
        return CellIndex(row + window.rowOffset, col + window.colOffset, window.layer)
    }

    /** The lattice row a slot stands on. */
    fun rowOf(slot: Int): Int = ids?.let { keyRow(it[slot]) } ?: (slot / windows[0].grid.cols)

    /** The lattice column a slot stands on. */
    fun colOf(slot: Int): Int = ids?.let { keyCol(it[slot]) } ?: (slot % windows[0].grid.cols)

    /** The layer a slot stands on — 0 for a uniform grid or a one-lattice chain. */
    fun layerOf(slot: Int): Int = ids?.let { keyLayer(it[slot]) } ?: 0

    /** The cell size (m) a layer carries — the one size a single grid answers, whatever the layer. */
    fun cellSizeM(layer: Int): Double = lattices?.getOrNull(layer)?.cellM ?: windows[0].grid.cellM

    /**
     * The **local** cell size the water under [point] was resolved at — the size the drawn tail reads, so
     * the band's 20 m survives into the points. The finest layer whose own window holds passable water where
     * the point stands answers; where none does the coarsest does, and a single grid answers its own cell.
     */
    fun cellSizeAt(point: LatLng): Double {
        val layers = lattices ?: return windows[0].grid.cellM
        var local = layers[0]
        for (layer in layers) if (layer.cellM > local.cellM) local = layer
        for (index in layers.indices) {
            val lattice = layers[index]
            if (lattice.cellM >= local.cellM) continue
            val slot = rawSlotOf(index, lattice.rowOf(point.latitude), lattice.colOf(point.longitude))
            if (slot >= 0 && passable(slot)) local = lattice
        }
        return local.cellM
    }

    /**
     * The slots **across the seam** from the cell on [layer] at `(row, col)` in direction `(dr, dc)` — the
     * other resolution's cells that meet this cell's face, or its corner. Empty where the walk is one
     * lattice, where the pair is not an integer ratio above one, or where a fine cell stands inside its
     * block rather than on the face the step leaves by.
     */
    fun crossLayerSlots(layer: Int, row: Int, col: Int, dr: Int, dc: Int): IntArray {
        val layers = lattices ?: return NO_SLOTS
        if (layers.size != 2) return NO_SLOTS
        val coarseLayer = if (layers[0].cellM >= layers[1].cellM) 0 else 1
        val fineLayer = 1 - coarseLayer
        val ratio = latticeRatioOf(layers[coarseLayer].cellM, layers[fineLayer].cellM)
        if (ratio <= 1) return NO_SLOTS
        val targets: List<CellIndex> = if (layer == coarseLayer) {
            SeamNeighbours.acrossFromCoarse(row, col, dr, dc, ratio, fineLayer)
        } else {
            listOfNotNull(SeamNeighbours.acrossFromFine(row, col, dr, dc, ratio, coarseLayer))
        }
        if (targets.isEmpty()) return NO_SLOTS
        val out = IntArray(targets.size)
        var n = 0
        for (t in targets) {
            val slot = rawSlotOf(t.layer, t.row, t.col)
            if (slot < 0) continue
            // A coarse target a passable fine cell supersedes is not read — but its **fine copy** over the
            // same water is, so the crossing lands there, exactly as the same-layer neighbour step redirects
            // ([`MultipassSearch`]). Dropping it instead would lose every path whose only bridge is that
            // crossing: a face fine cell whose neighbouring block is superseded on its central cell alone
            // keeps no fine step into the block and no read coarse cell borders it, so the redirect is the
            // only edge that reaches the block's water. `aSupersededCoarseSeamTargetRedirectsToItsFineCopy`
            // in [`FinePriorityWalkTest`] pins that case, and the redirect never re-reads the coarse cell.
            val copy = if (t.layer == coarseLayer) fineCopyOf(t.row, t.col) else -1
            out[n++] = if (copy >= 0) copy else slot
        }
        return if (n == out.size) out else out.copyOf(n)
    }

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

    /** The slot's own cell read as a scalar: whether it is passable, from the window that holds it. */
    fun passable(slot: Int): Boolean {
        val window = windowOf(slot)
        return window.grid.passable(rowOf(slot) - window.rowOffset, colOf(slot) - window.colOffset)
    }

    /** The slot's own source cost (s), read from the window that holds it. */
    fun sourceCostSec(slot: Int): Double {
        val window = windowOf(slot)
        return window.grid.sourceCostSec(rowOf(slot) - window.rowOffset, colOf(slot) - window.colOffset)
    }

    /** The window a slot stands on — its own grid and its lattice offset. */
    fun windowOf(slot: Int): WalkWindow = windows[tiles?.get(slot) ?: 0]

    /** The slot's own cell in the **local** index space of the window it stands on — what a grid open takes. */
    fun localCell(slot: Int): CellIndex {
        val window = windowOf(slot)
        return CellIndex(rowOf(slot) - window.rowOffset, colOf(slot) - window.colOffset, layerOf(slot))
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

    /** The slot's **λ-free depth-price coefficient**, or 0.0 where the depth band does not stand. */
    fun depthCoef(slot: Int): Double {
        val tile = tiles?.get(slot) ?: 0
        val window = windows[tile]
        return window.grid.depthPriceCoef(rowOf(slot) - window.rowOffset, colOf(slot) - window.colOffset)
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
        for (slot in 0 until size) if (passable(slot)) count++
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
         *
         * The index is a pure function of the windows' shape, so it is reused across arms whose windows carry
         * the same shape ([`WalkIndexCache`], D18); the walk still holds **this** arm's own windows, so its
         * read cells are the ones the caller has just built and carved.
         */
        fun onLattice(lattices: List<WalkLattice>, windows: List<WalkWindow>): WalkWindows {
            val index = WalkIndexCache.getOrBuild(windows)
            return WalkWindows(windows, lattices, index.slots, index.tiles, index.ids)
        }
    }
}
