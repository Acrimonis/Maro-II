package ykws.android.maro.spatial.multipass

import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.spatial.LandRingOrientation
import ykws.android.maro.spatial.SpatialOperations
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** One grid position, row-major: `row` is the latitude band, `col` the longitude band. */
data class CellIndex(val row: Int, val col: Int)

/**
 * One speed zone the rasterizer even-odd-fills: its polygon and the **limit** (kn) it carries. The
 * grid stores the limit and never a finished price — the A\* prices it at read time, so a change of
 * the price's own scaling costs one multiply per cell instead of walking this fill again — and the
 * strictest (lowest) limit wins where two zones overlap because the fill keeps the strictest in force.
 */
data class ZoneRing(
    val outerRing: List<LatLng>,
    val holes: List<List<LatLng>>,
    val limitKn: Double
)

/**
 * **The 300 m band's law as the rasterizer writes it**: the width (m) inside which the band's limit is
 * in force, the limit (kn) itself, and the outside margin (m) beyond that width — the strip priced at
 * the band's own fraction. It is a law, never a price: the rasterizer writes the limit and nothing of
 * the price cursor, so the grid stays λ-free and the A\* prices every band cell at read time exactly
 * as it prices a ring's.
 */
data class BandLaw(
    val widthM: Double,
    val limitKn: Double,
    val outsideMarginM: Double
)

/**
 * The tagged cell state. A cell is never a bare blocked boolean: it carries one of the four tags
 * plus a source cost **in seconds**, so stage 2's band and stage 3's zones add a tag and a cost
 * without reworking the rasterizer or the A*. Stage 1 uses [FREE] and [LAND] only.
 *
 * [BAND] marks the band's **law water** — the cells inside its own width, where the band's limit is in
 * force — and never the outside margin, which carries a price but no limit. [ZONE] marks a ring's
 * interior. A cell inside both wears the dearest tag, [ZONE].
 */
enum class MultipassCellState { FREE, LAND, BAND, ZONE }

/**
 * A tagged, costed cell — [sourceCostSec] is the **time** (s) the cell's own prices add to its base.
 *
 * **Neither property has a default, and that is the invariant rather than the style.** The grid always
 * writes a base cost ([MultipassGrid.baseCostSec], one cell of water at the pace) and every source may
 * only *add* to it, so no passable cell is ever cheaper than the base and no price can pay the A*'s
 * search back. A defaulted `sourceCostSec = 0.0` is the trap this signature closes: a cell built
 * without a cost would read as free water and quietly break the shortest-path guarantee the whole
 * field rests on.
 *
 * A speed zone is **not** in here: the grid stores its limit and the A\* prices it at read time, which
 * is why the field's own prices are all this number carries.
 */
data class MultipassCell(
    val state: MultipassCellState,
    val sourceCostSec: Double
) {
    /** [LAND] is impassable; every other tag is passable, priced by its source cost. */
    val passable: Boolean get() = state != MultipassCellState.LAND
}

/**
 * The corridor grid: a row-major field of [MultipassCell] over a lat/lon box, with the cell-centre
 * geometry the rasterizer, the A* and the pull all read.
 *
 * **The base is time and a zone is a limit — the two halves of the unit change.** Every cell opens at
 * [baseCostSec], one cell of open water crossed at the pace; the field's own soft sources *add* their
 * seconds through [addSourceCost]; and a speed zone stores only its **limit**, which the A\* prices at
 * read time through the engine's price function. That is what makes a re-price per cell one multiply
 * instead of the zone fill, and it is why [cell] carries no zone price of its own.
 */
class MultipassGrid(
    val latSouth: Double,
    val lonWest: Double,
    val cellSizeDegLat: Double,
    val cellSizeDegLon: Double,
    val rows: Int,
    val cols: Int,
    val cellM: Double,
    val baseCostSec: Double
) {
    private val cells = Array(rows * cols) { MultipassCell(MultipassCellState.FREE, baseCostSec) }

    /**
     * The speed zone's **interior limit** (kn) standing on each cell, 0.0 where none does, kept apart
     * from [cells]' own `sourceCostSec` so overlapping zones keep the **strictest** limit rather than
     * summing — and so the A\* can price it, per expansion, however the engine says a slow cell costs.
     */
    private val zoneLimitKn = DoubleArray(rows * cols)

    /**
     * The speed zone's **outside-margin (collar) limit** (kn) standing on each cell, 0.0 where none
     * does: the strictest limit of every zone whose outer ring lies within the outside margin of the
     * cell's centre. Kept beside [zoneLimitKn] rather than folded into it, so the A\* can tell an
     * interior cell (full price) from a margin cell (fraction price) and the forced-crossing probe can
     * block interiors alone.
     */
    private val collarLimitKn = DoubleArray(rows * cols)

    /**
     * The 300 m band's **own width limit** (kn) standing on each cell, 0.0 where none does. Kept beside
     * [zoneLimitKn] so the cell's tag can say which law stands on it, while [limitKn] answers the
     * **strictest of the two** — the one limit the A\* prices in full, so a band and a ring over the
     * same slow water never charge it twice.
     */
    private val bandLimitKn = DoubleArray(rows * cols)

    /**
     * The 300 m band's **outside-margin limit** (kn) standing on each cell, 0.0 where none does: the
     * water beyond the band's width and within its reach, priced at the band's own fraction.
     */
    private val bandCollarLimitKn = DoubleArray(rows * cols)

    fun index(row: Int, col: Int): Int = row * cols + col

    fun inBounds(row: Int, col: Int): Boolean = row in 0 until rows && col in 0 until cols

    /** The limit a speed zone imposes at this cell (kn), or 0.0 where none does. */
    fun zoneLimitKn(row: Int, col: Int): Double = zoneLimitKn[index(row, col)]

    /** The outside-margin limit standing on this cell (kn), or 0.0 where none does. */
    fun collarLimitKn(row: Int, col: Int): Double = collarLimitKn[index(row, col)]

    /** The band's own width limit at this cell (kn), or 0.0 where the width does not cover it. */
    fun bandLimitKn(row: Int, col: Int): Double = bandLimitKn[index(row, col)]

    /** The band's outside-margin limit at this cell (kn), or 0.0 where the collar does not reach it. */
    fun bandCollarLimitKn(row: Int, col: Int): Double = bandCollarLimitKn[index(row, col)]

    /**
     * **The strictest limit in force at this cell** (kn), or 0.0 where none stands: a ring's own interior
     * limit or the band's own width limit, whichever is slower. This is the one limit the A\* prices at
     * read time, so two sources over the same slow water cost it once and the band's price follows the
     * pass's λ exactly as a ring's does.
     */
    fun limitKn(row: Int, col: Int): Double {
        val i = index(row, col)
        val zone = zoneLimitKn[i]
        val band = bandLimitKn[i]
        return when {
            zone <= 0.0 -> band
            band <= 0.0 -> zone
            else -> min(zone, band)
        }
    }

    /** The cell's own cost and tag: `ZONE` where a ring's limit stands, else `BAND` on the band's law water. */
    fun cell(row: Int, col: Int): MultipassCell {
        val i = index(row, col)
        val base = cells[i]
        if (base.state == MultipassCellState.LAND) return base
        return when {
            zoneLimitKn[i] > 0.0 -> base.copy(state = MultipassCellState.ZONE)
            bandLimitKn[i] > 0.0 -> base.copy(state = MultipassCellState.BAND)
            else -> base
        }
    }

    fun center(row: Int, col: Int): LatLng =
        LatLng(
            latSouth + (row + 0.5) * cellSizeDegLat,
            lonWest + (col + 0.5) * cellSizeDegLon
        )

    /** Marks a cell land, keeping its source cost (irrelevant while impassable) untouched. */
    fun markLand(row: Int, col: Int) {
        val i = index(row, col)
        cells[i] = cells[i].copy(state = MultipassCellState.LAND)
    }

    /**
     * Writes one zone's **limit** onto a passable cell, keeping the strictest in force — the slowest
     * limit wins where zones overlap, and a cell already [MultipassCellState.LAND] stays land, never zoned.
     */
    fun applyZoneLimit(row: Int, col: Int, limitKn: Double) {
        require(limitKn > 0.0) { "a zone always carries a positive limit" }
        val i = index(row, col)
        if (cells[i].state == MultipassCellState.LAND) return
        val current = zoneLimitKn[i]
        zoneLimitKn[i] = if (current <= 0.0) limitKn else min(current, limitKn)
    }

    /**
     * Writes one zone's **outside-margin limit** onto a passable cell, keeping the strictest in force.
     * A cell already [MultipassCellState.LAND] stays land; a cell that is also inside a zone keeps both
     * limits, and the A\* prefers the interior's full price over this margin's fraction.
     */
    fun applyCollarLimit(row: Int, col: Int, limitKn: Double) {
        require(limitKn > 0.0) { "a zone always carries a positive limit" }
        val i = index(row, col)
        if (cells[i].state == MultipassCellState.LAND) return
        val current = collarLimitKn[i]
        collarLimitKn[i] = if (current <= 0.0) limitKn else min(current, limitKn)
    }

    /**
     * Writes the band's **own width limit** onto a passable cell, keeping the strictest in force. A cell
     * already [MultipassCellState.LAND] stays land — the band is a law on water, never on the shore.
     */
    fun applyBandLimit(row: Int, col: Int, limitKn: Double) {
        require(limitKn > 0.0) { "the band always carries a positive limit" }
        val i = index(row, col)
        if (cells[i].state == MultipassCellState.LAND) return
        val current = bandLimitKn[i]
        bandLimitKn[i] = if (current <= 0.0) limitKn else min(current, limitKn)
    }

    /**
     * Writes the band's **outside-margin limit** onto a passable cell, keeping the strictest in force.
     * A cell already [MultipassCellState.LAND] stays land, and a cell inside the band's width keeps both, the
     * width's full price winning over this margin's fraction.
     */
    fun applyBandCollarLimit(row: Int, col: Int, limitKn: Double) {
        require(limitKn > 0.0) { "the band always carries a positive limit" }
        val i = index(row, col)
        if (cells[i].state == MultipassCellState.LAND) return
        val current = bandCollarLimitKn[i]
        bandCollarLimitKn[i] = if (current <= 0.0) limitKn else min(current, limitKn)
    }

    /**
     * Adds one source's price (s) to a passable cell and raises its tag to [tag] where that tag is the
     * dearest in force — the **only** way a cost reaches a cell, so a source can add and can never
     * replace the base. A cell already land keeps its state: a wall is not priced.
     */
    fun addSourceCost(row: Int, col: Int, extraSec: Double, tag: MultipassCellState) {
        require(extraSec >= 0.0) { "a source may only add to the base cost, never take from it" }
        val i = index(row, col)
        val cell = cells[i]
        if (!cell.passable) return
        val state = if (tag.ordinal > cell.state.ordinal) tag else cell.state
        cells[i] = MultipassCell(state, cell.sourceCostSec + extraSec)
    }

    /** The cell a point falls in, clamped to the grid edge so an end outside the box still anchors. */
    fun cellOf(latitude: Double, longitude: Double): CellIndex = CellIndex(
        floor((latitude - latSouth) / cellSizeDegLat).toInt().coerceIn(0, rows - 1),
        floor((longitude - lonWest) / cellSizeDegLon).toInt().coerceIn(0, cols - 1)
    )

    /**
     * Forces the cell holding a point free — the margin binds the path, not where the boat already
     * is, so a start or aim standing inside the clearance band still anchors a search.
     */
    fun forceFree(latitude: Double, longitude: Double) {
        val (row, col) = cellOf(latitude, longitude)
        val i = index(row, col)
        cells[i] = MultipassCell(MultipassCellState.FREE, baseCostSec)
        zoneLimitKn[i] = 0.0
        collarLimitKn[i] = 0.0
        bandLimitKn[i] = 0.0
        bandCollarLimitKn[i] = 0.0
    }

    /**
     * Opens a **carved** cell — the berth channel's own write, beside [forceFree] and deliberately not
     * the same one: the cell becomes [MultipassCellState.FREE] at the grid's **base cost**, and it
     * **keeps its zone limit**.
     *
     * The distinction is the rule the carve waives: only the shore margin is a courtesy, so a zone
     * standing in the berth must still price the cell the A\* reads, while [forceFree] — which answers
     * the end itself — clears the limit as well. The channel is water the margin took, nothing more.
     */
    fun openCarve(row: Int, col: Int) {
        cells[index(row, col)] = MultipassCell(MultipassCellState.FREE, baseCostSec)
    }

    /**
     * A copy of this grid with every **restrictive** zone interior marked impassable: a cell carrying
     * a limit slower than [paceKn] becomes land, a cell at or above the pace stays open water.
     *
     * It is the forced-crossing probe's own question — "does a way around exist at all?" — taken off
     * the grid the search already built rather than off a second raster sweep of the corridor, which
     * was the dearer half of a solve. The copy leaves this grid untouched, so the answer's own grid
     * still carries its limits, and a zone slower than the pace is exactly a zone the search prices.
     */
    fun blockedCopy(paceKn: Double): MultipassGrid {
        val copy = MultipassGrid(latSouth, lonWest, cellSizeDegLat, cellSizeDegLon, rows, cols, cellM, baseCostSec)
        for (i in 0 until rows * cols) {
            val cell = cells[i]
            val limit = zoneLimitKn[i]
            copy.cells[i] = if (cell.passable && limit > 0.0 && limit < paceKn) {
                MultipassCell(MultipassCellState.LAND, cell.sourceCostSec)
            } else {
                cell
            }
            copy.zoneLimitKn[i] = limit
            copy.collarLimitKn[i] = collarLimitKn[i]
            copy.bandLimitKn[i] = bandLimitKn[i]
            copy.bandCollarLimitKn[i] = bandCollarLimitKn[i]
        }
        return copy
    }
}

/**
 * Rasterizes the harvested edges and open-coast polylines into a tagged, costed corridor grid, then
 * applies [field]'s own sources over it.
 *
 * - Every edge and open-coast segment paints a **margin band**: cells whose centre is within
 *   [marginM] of the segment are land — never stepped point discs, which would leave holes where
 *   two discs of a 50 m step stand tangent.
 * - A **CCW ring** (islands and hazard rings) has its interior filled land by an even-odd scanline,
 *   while a **CW basin** keeps its interior water and only its breakwater edge takes the margin.
 * - The **open coast** is closed into a land polygon: each ordered polyline is capped on its land
 *   side at [capLatNorth] and even-odd filled as land, so a wide landmass's interior — which sits
 *   far from any coast edge — is sealed without a single water query.
 * - **The field's remaining sources are then applied once per cell centre**: a hard source that
 *   blocks paints the cell land, **ANDed with the coastline's own water** through the cell's
 *   passability — a cell the sweep sealed stays blocked whatever the field says about it, which is
 *   how a NoData cell the depth mask erased on the land side reads as land rather than as
 *   unsurveyed water. A soft source only *adds* its price to the base cost the cell already carries.
 *
 * The three passes above are the coastline's own hard source *materialized* — one sweep over the
 * harvested geometry rather than a water query per cell — which is what keeps a ~33 000-cell corridor
 * inside its budget.
 *
 * The band and the zones are written as **limits**, never as a price: the A\* prices them at read time,
 * so the grid is λ-free and built once, and a band cell follows the pass's cursor exactly as a ring's.
 */
fun rasterize(
    box: BBox,
    cellM: Double,
    paceKn: Double,
    marginM: Double,
    edges: List<MultipassEdge>,
    openCoast: List<List<LatLng>>,
    capLatNorth: Double,
    field: RouteCostField = RouteCostField.EMPTY,
    zones: List<ZoneRing> = emptyList(),
    blockZones: Boolean = false,
    zoneOutsideMarginM: Double = 0.0,
    band: BandLaw? = null
): MultipassGrid {
    val midLat = (box.latSouth + box.latNorth) / 2.0
    val mPerDegLat = SpatialOperations.EARTH_RADIUS_M * PI / 180.0
    val mPerDegLon = mPerDegLat * cos(Math.toRadians(midLat))
    return rasterizeFrame(
        box, box.latSouth, box.lonWest, cellM / mPerDegLat, cellM / mPerDegLon, mPerDegLat, mPerDegLon,
        cellM, paceKn, marginM, edges, openCoast, capLatNorth, field, zones, blockZones,
        zoneOutsideMarginM, band
    )
}

/**
 * **One window of a lattice's raster** — the same fill as [rasterize], laid out from a frame the caller
 * owns: an origin already on the lattice and the lattice's own cell-size pair, so two windows' cells line up
 * by arithmetic. The pair is deliberately **not** derived here; deriving it per box is exactly how two
 * rectangles come to stand on two lattices, and it is what [rasterize] alone still does.
 *
 * [bandMask] carries the layer's own membership where the walk is two-layer: `true` keeps only the band's
 * water (the coast and the depth gate dilated by [bandMaskWidthM]) and `false` keeps only the water beyond
 * it, while `null` — the uniform pass — masks nothing. The predicate is the margin's own sweep at the
 * larger radius, so the band's water and the land's can never disagree about where the coast is.
 */
internal fun rasterizeWindow(
    box: BBox,
    lattice: WalkLattice,
    paceKn: Double,
    marginM: Double,
    edges: List<MultipassEdge>,
    openCoast: List<List<LatLng>>,
    capLatNorth: Double,
    field: RouteCostField = RouteCostField.EMPTY,
    zones: List<ZoneRing> = emptyList(),
    blockZones: Boolean = false,
    zoneOutsideMarginM: Double = 0.0,
    band: BandLaw? = null,
    bandMask: Boolean? = null,
    bandMaskWidthM: Double = 0.0
): MultipassGrid {
    val (mPerDegLat, mPerDegLon) = lattice.metresPerDegree()
    return rasterizeFrame(
        box, box.latSouth, box.lonWest, lattice.cellSizeDegLat, lattice.cellSizeDegLon,
        mPerDegLat, mPerDegLon, lattice.cellM, paceKn, marginM, edges, openCoast, capLatNorth, field,
        zones, blockZones, zoneOutsideMarginM, band, bandMask, bandMaskWidthM
    )
}

/** The rasterizer's own body over a frame the caller owns — the one home of the fill's own passes. */
private fun rasterizeFrame(
    box: BBox,
    latSouth: Double,
    lonWest: Double,
    cellSizeDegLat: Double,
    cellSizeDegLon: Double,
    mPerDegLat: Double,
    mPerDegLon: Double,
    cellM: Double,
    paceKn: Double,
    marginM: Double,
    edges: List<MultipassEdge>,
    openCoast: List<List<LatLng>>,
    capLatNorth: Double,
    field: RouteCostField,
    zones: List<ZoneRing>,
    blockZones: Boolean,
    zoneOutsideMarginM: Double,
    band: BandLaw?,
    bandMask: Boolean? = null,
    bandMaskWidthM: Double = 0.0
): MultipassGrid {
    val cols = ceil((box.lonEast - lonWest) / cellSizeDegLon).toInt().coerceAtLeast(1)
    val rows = ceil((box.latNorth - latSouth) / cellSizeDegLat).toInt().coerceAtLeast(1)
    val grid = MultipassGrid(
        latSouth, lonWest, cellSizeDegLat, cellSizeDegLon, rows, cols, cellM,
        baseCostSec(cellM, paceKn)
    )

    // 1. One sweep per edge — CCW ring and CW basin — and per open-coast segment, reaching as far as
    //    the clearance margin. A cell whose centre is inside the margin is land; the field's own soft
    //    sources price cells in the per-cell pass below.
    for (edge in edges) {
        forEachCellNear(grid, edge, marginM, mPerDegLat, mPerDegLon) { r, c, _ ->
            grid.markLand(r, c)
        }
    }
    for (polyline in openCoast) {
        for (i in 0 until polyline.size - 1) {
            val edge = MultipassEdge(polyline[i], polyline[i + 1], LandRingOrientation.OPEN_COAST)
            forEachCellNear(grid, edge, marginM, mPerDegLat, mPerDegLon) { r, c, _ ->
                grid.markLand(r, c)
            }
        }
    }

    // 2. Interior fill for CCW rings.
    val ringEdges = ArrayList<MultipassEdge>()
    for (edge in edges) {
        if (edge.orientation == LandRingOrientation.CCW_RING) ringEdges.add(edge)
    }
    fillRingsEvenOdd(grid, ringEdges)

    // 3. Open-coast closure: cap each ordered polyline on its land side and even-odd fill as land.
    for (polyline in openCoast) {
        fillClosedRingEvenOdd(grid, closeOpenCoast(polyline, capLatNorth))
    }

    // 4. The field's own sources, once per cell centre. Sources only ever add a block or a price —
    //    a field with neither a rastered wall nor a price writes nothing and costs a single test.
    if (field.hasBlocking || field.hasSoft) {
        for (row in 0 until grid.rows) {
            for (col in 0 until grid.cols) {
                if (!grid.cell(row, col).passable) continue
                val at = field.evaluate(grid.center(row, col))
                when {
                    at.blocked -> grid.markLand(row, col)
                    at.softCostSec > 0.0 -> grid.addSourceCost(row, col, at.softCostSec, at.tag)
                }
            }
        }
    }

    // 5. Speed zones, one even-odd fill per zone — the outer ring and its holes as one ring set, so a
    //    hole flips back to water. The strictest limit wins where zones overlap because the fill keeps
    //    the dearest price; a cell the sweep or the field already sealed stays land either way. The
    //    outside margin is then walked per outer-ring edge, its cells carrying the collar limit the A*
    //    prices at the configured fraction.
    if (zones.isNotEmpty()) {
        fillZonesEvenOdd(grid, zones, blockZones, zoneOutsideMarginM, mPerDegLat, mPerDegLon)
    }

    // 6. The band's law, written as limits: the water inside the band's width carries the band's own
    //    limit, the strip between that width and its reach carries the band's collar limit. The A*
    //    prices the width's limit in full and the collar's at the band's fraction, so the band's price
    //    follows the pass's cursor exactly as a ring's does, and the strictest limit wins where the band
    //    and a ring overlap.
    if (band != null && band.widthM > 0.0 && band.limitKn > 0.0) {
        writeBandLaw(grid, edges, openCoast, band, mPerDegLat, mPerDegLon)
    }

    // 7. The layer's own membership, where the walk is two-layer: the same sweep the margin runs, at the
    //    band's larger radius, so a cell's layer is read off the coast the margin already measured.
    if (bandMask != null) {
        applyBandMask(grid, edges, openCoast, marginM + bandMaskWidthM, bandMask, mPerDegLat, mPerDegLon)
    }

    return grid
}

/**
 * **The band's membership, laid on the margin's own sweep** — every passable cell whose centre stands
 * within [radiusM] of a harvested edge is a band member, and [bandOnly] then keeps either the band's water
 * alone (the fine layer) or the water beyond it (the interior layer). The measure is the margin's own
 * (`pointToSegmentDistance`), so the band's edge and the land's cannot disagree about where the coast is.
 */
private fun applyBandMask(
    grid: MultipassGrid,
    edges: List<MultipassEdge>,
    openCoast: List<List<LatLng>>,
    radiusM: Double,
    bandOnly: Boolean,
    mPerDegLat: Double,
    mPerDegLon: Double
) {
    val member = BooleanArray(grid.rows * grid.cols)
    fun mark(edge: MultipassEdge) {
        forEachCellNear(grid, edge, radiusM, mPerDegLat, mPerDegLon) { r, c, _ ->
            member[grid.index(r, c)] = true
        }
    }
    for (edge in edges) mark(edge)
    for (polyline in openCoast) {
        for (i in 0 until polyline.size - 1) {
            mark(MultipassEdge(polyline[i], polyline[i + 1], LandRingOrientation.OPEN_COAST))
        }
    }
    for (r in 0 until grid.rows) {
        for (c in 0 until grid.cols) {
            if (!grid.cell(r, c).passable) continue
            val isBand = member[grid.index(r, c)]
            if (bandOnly == isBand) continue
            grid.markLand(r, c)
        }
    }
}

/**
 * Writes the band's law onto the grid, swept over the harvested coastline edges — the very geometry the
 * margin band uses, so the band's water and the land's can never disagree about where the coast is. A
 * cell whose centre stands inside the band's width carries the band's limit; a cell inside the outside
 * margin beyond it carries the band's collar limit; everything further carries nothing.
 */
private fun writeBandLaw(
    grid: MultipassGrid,
    edges: List<MultipassEdge>,
    openCoast: List<List<LatLng>>,
    band: BandLaw,
    mPerDegLat: Double,
    mPerDegLon: Double
) {
    val reachM = bandReachM(band.widthM, band.outsideMarginM)
    fun paint(edge: MultipassEdge) {
        forEachCellNear(grid, edge, reachM, mPerDegLat, mPerDegLon) { r, c, distanceM ->
            if (insideBandWidthM(distanceM, band.widthM)) {
                grid.applyBandLimit(r, c, band.limitKn)
            } else {
                grid.applyBandCollarLimit(r, c, band.limitKn)
            }
        }
    }
    for (edge in edges) paint(edge)
    for (polyline in openCoast) {
        for (i in 0 until polyline.size - 1) {
            paint(MultipassEdge(polyline[i], polyline[i + 1], LandRingOrientation.OPEN_COAST))
        }
    }
}

/**
 * Walks the cells whose centre is within [radiusM] of [edge], in metres, via a degrees-expanded bbox,
 * handing [action] the cell-centre-to-segment distance it computed — the reading the margin band
 * decides on.
 */
private fun forEachCellNear(
    grid: MultipassGrid,
    edge: MultipassEdge,
    radiusM: Double,
    mPerDegLat: Double,
    mPerDegLon: Double,
    action: (row: Int, col: Int, distanceM: Double) -> Unit
) {
    val degLat = radiusM / mPerDegLat
    val degLon = radiusM / mPerDegLon
    val minLat = min(edge.a.latitude, edge.b.latitude) - degLat
    val maxLat = max(edge.a.latitude, edge.b.latitude) + degLat
    val minLon = min(edge.a.longitude, edge.b.longitude) - degLon
    val maxLon = max(edge.a.longitude, edge.b.longitude) + degLon
    val rMin = floor((minLat - grid.latSouth) / grid.cellSizeDegLat).toInt().coerceIn(0, grid.rows - 1)
    val rMax = ceil((maxLat - grid.latSouth) / grid.cellSizeDegLat).toInt().coerceIn(0, grid.rows - 1)
    val cMin = floor((minLon - grid.lonWest) / grid.cellSizeDegLon).toInt().coerceIn(0, grid.cols - 1)
    val cMax = ceil((maxLon - grid.lonWest) / grid.cellSizeDegLon).toInt().coerceIn(0, grid.cols - 1)
    for (r in rMin..rMax) {
        for (c in cMin..cMax) {
            val centre = grid.center(r, c)
            val distanceM = SpatialOperations.pointToSegmentDistance(centre, edge.a, edge.b)
            if (distanceM <= radiusM) {
                action(r, c, distanceM)
            }
        }
    }
}

/**
 * Even-odd scanline fill of the CCW rings' interiors. Disjoint rings fill as their union; a shared
 * vertex is counted once by the half-open latitude rule, so two touching hazard rings read as one
 * blocked mass.
 */
private fun fillRingsEvenOdd(grid: MultipassGrid, ringEdges: List<MultipassEdge>) {
    if (ringEdges.isEmpty()) return
    fillScanlineEvenOdd(grid, { _, lat ->
        val crossings = ArrayList<Double>()
        for (edge in ringEdges) {
            val y1 = edge.a.latitude
            val y2 = edge.b.latitude
            val spans = (y1 <= lat && lat < y2) || (y2 <= lat && lat < y1)
            if (!spans) continue
            val t = (lat - y1) / (y2 - y1)
            crossings.add(edge.a.longitude + t * (edge.b.longitude - edge.a.longitude))
        }
        crossings
    }) { r, c -> grid.markLand(r, c) }
}

/**
 * Even-odd scanline fill of one arbitrary closed ring given as an ordered vertex list whose last
 * vertex closes to its first. The open coast uses it once it is capped into a land polygon.
 */
private fun fillClosedRingEvenOdd(grid: MultipassGrid, ring: List<LatLng>) {
    if (ring.size < 3) return
    fillScanlineEvenOdd(grid, { _, lat ->
        val crossings = ArrayList<Double>()
        var prev = ring[ring.size - 1]
        for (v in ring) {
            val y1 = prev.latitude
            val y2 = v.latitude
            val spans = (y1 <= lat && lat < y2) || (y2 <= lat && lat < y1)
            if (spans) {
                val t = (lat - y1) / (y2 - y1)
                crossings.add(prev.longitude + t * (v.longitude - prev.longitude))
            }
            prev = v
        }
        crossings
    }) { r, c -> grid.markLand(r, c) }
}

/**
 * Even-odd fill of each speed zone: its outer ring and holes together form the ring set, so a cell
 * inside the outer ring but inside a hole crosses an even number of boundaries and stays water. The
 * action writes the zone's **interior limit** by default and a land mark when [blockZones] is set (the
 * forced-crossing probe). The **outside margin** is then walked per outer-ring edge — never per cell —
 * writing the zone's collar limit onto cells whose centre stands within [zoneOutsideMarginM] of the
 * ring; the A\* prices those at the configured fraction, and an interior cell keeps both limits so its
 * full price wins.
 */
private fun fillZonesEvenOdd(
    grid: MultipassGrid,
    zones: List<ZoneRing>,
    blockZones: Boolean,
    zoneOutsideMarginM: Double,
    mPerDegLat: Double,
    mPerDegLon: Double
) {
    for (zone in zones) {
        val rings = buildList {
            add(zone.outerRing)
            addAll(zone.holes)
        }
        fillScanlineEvenOdd(grid, { _, lat ->
            val crossings = ArrayList<Double>()
            for (ring in rings) {
                if (ring.size < 3) continue
                var prev = ring[ring.size - 1]
                for (v in ring) {
                    val y1 = prev.latitude
                    val y2 = v.latitude
                    val spans = (y1 <= lat && lat < y2) || (y2 <= lat && lat < y1)
                    if (spans) {
                        val t = (lat - y1) / (y2 - y1)
                        crossings.add(prev.longitude + t * (v.longitude - prev.longitude))
                    }
                    prev = v
                }
            }
            crossings
        }) { r, c ->
            if (blockZones) grid.markLand(r, c) else grid.applyZoneLimit(r, c, zone.limitKn)
        }
        if (!blockZones && zoneOutsideMarginM > 0.0) {
            for (i in 0 until zone.outerRing.size - 1) {
                val edge = MultipassEdge(
                    zone.outerRing[i],
                    zone.outerRing[i + 1],
                    LandRingOrientation.CCW_RING
                )
                forEachCellNear(grid, edge, zoneOutsideMarginM, mPerDegLat, mPerDegLon) { r, c, _ ->
                    grid.applyCollarLimit(r, c, zone.limitKn)
                }
            }
        }
    }
}

/** Shared even-odd scanline: [crossingsFor] returns the boundary crossings for one row's latitude. */
private fun fillScanlineEvenOdd(
    grid: MultipassGrid,
    crossingsFor: (row: Int, lat: Double) -> List<Double>,
    action: (row: Int, col: Int) -> Unit
) {
    for (row in 0 until grid.rows) {
        val lat = grid.latSouth + (row + 0.5) * grid.cellSizeDegLat
        val crossings = crossingsFor(row, lat)
        if (crossings.isEmpty()) continue
        val sorted = crossings.sorted()
        var cursor = 0
        var inside = false
        for (col in 0 until grid.cols) {
            val lon = grid.lonWest + (col + 0.5) * grid.cellSizeDegLon
            while (cursor < sorted.size && sorted[cursor] <= lon) {
                inside = !inside
                cursor++
            }
            if (inside) action(row, col)
        }
    }
}

/** Closes an open coast polyline with a landward cap: north at the last vertex's longitude, west along [capLatNorth], then south back to the first vertex. */
private fun closeOpenCoast(polyline: List<LatLng>, capLatNorth: Double): List<LatLng> {
    if (polyline.size < 2) return emptyList()
    val first = polyline.first()
    val last = polyline.last()
    return polyline +
        LatLng(capLatNorth, last.longitude) +
        LatLng(capLatNorth, first.longitude)
}
