package ykws.android.maro.spatial.avoid

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
 * The tagged cell state. A cell is never a bare blocked boolean: it carries one of the four tags
 * plus a source cost **in seconds**, so stage 2's band and stage 3's zones add a tag and a cost
 * without reworking the rasterizer or the A*. Stage 1 uses [FREE] and [LAND] only.
 */
enum class AvoidCellState { FREE, LAND, BAND, ZONE }

/**
 * A tagged, costed cell — [sourceCostSec] is the **time** (s) the cell's own prices add to its base.
 *
 * **Neither property has a default, and that is the invariant rather than the style.** The grid always
 * writes a base cost ([AvoidGrid.baseCostSec], one cell of water at the pace) and every source may
 * only *add* to it, so no passable cell is ever cheaper than the base and no price can pay the A*'s
 * search back. A defaulted `sourceCostSec = 0.0` is the trap this signature closes: a cell built
 * without a cost would read as free water and quietly break the shortest-path guarantee the whole
 * field rests on.
 *
 * A speed zone is **not** in here: the grid stores its limit and the A\* prices it at read time, which
 * is why the field's own prices are all this number carries.
 */
data class AvoidCell(
    val state: AvoidCellState,
    val sourceCostSec: Double
) {
    /** [LAND] is impassable; every other tag is passable, priced by its source cost. */
    val passable: Boolean get() = state != AvoidCellState.LAND
}

/**
 * The corridor grid: a row-major field of [AvoidCell] over a lat/lon box, with the cell-centre
 * geometry the rasterizer, the A* and the pull all read.
 *
 * **The base is time and a zone is a limit — the two halves of the unit change.** Every cell opens at
 * [baseCostSec], one cell of open water crossed at the pace; the field's own soft sources *add* their
 * seconds through [addSourceCost]; and a speed zone stores only its **limit**, which the A\* prices at
 * read time through the engine's price function. That is what makes a re-price per cell one multiply
 * instead of the zone fill, and it is why [cell] carries no zone price of its own.
 */
class AvoidGrid(
    val latSouth: Double,
    val lonWest: Double,
    val cellSizeDegLat: Double,
    val cellSizeDegLon: Double,
    val rows: Int,
    val cols: Int,
    val cellM: Double,
    val baseCostSec: Double
) {
    private val cells = Array(rows * cols) { AvoidCell(AvoidCellState.FREE, baseCostSec) }

    /**
     * The speed zone's **limit** (kn) standing on each cell, 0.0 where none does, kept apart from
     * [cells]' own `sourceCostSec` so overlapping zones keep the **strictest** limit rather than
     * summing — and so the A\* can price it, per expansion, however the engine says a slow cell costs.
     */
    private val zoneLimitKn = DoubleArray(rows * cols)

    fun index(row: Int, col: Int): Int = row * cols + col

    fun inBounds(row: Int, col: Int): Boolean = row in 0 until rows && col in 0 until cols

    /** The limit a speed zone imposes at this cell (kn), or 0.0 where none does. */
    fun zoneLimitKn(row: Int, col: Int): Double = zoneLimitKn[index(row, col)]

    /** The cell's own cost and tag: the base plus the field's prices, and `ZONE` where a limit stands. */
    fun cell(row: Int, col: Int): AvoidCell {
        val i = index(row, col)
        val base = cells[i]
        return if (base.state != AvoidCellState.LAND && zoneLimitKn[i] > 0.0) {
            base.copy(state = AvoidCellState.ZONE)
        } else {
            base
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
        cells[i] = cells[i].copy(state = AvoidCellState.LAND)
    }

    /**
     * Writes one zone's **limit** onto a passable cell, keeping the strictest in force — the slowest
     * limit wins where zones overlap, and a cell already [AvoidCellState.LAND] stays land, never zoned.
     */
    fun applyZoneLimit(row: Int, col: Int, limitKn: Double) {
        require(limitKn > 0.0) { "a zone always carries a positive limit" }
        val i = index(row, col)
        if (cells[i].state == AvoidCellState.LAND) return
        val current = zoneLimitKn[i]
        zoneLimitKn[i] = if (current <= 0.0) limitKn else min(current, limitKn)
    }

    /**
     * Adds one source's price (s) to a passable cell and raises its tag to [tag] where that tag is the
     * dearest in force — the **only** way a cost reaches a cell, so a source can add and can never
     * replace the base. A cell already land keeps its state: a wall is not priced.
     */
    fun addSourceCost(row: Int, col: Int, extraSec: Double, tag: AvoidCellState) {
        require(extraSec >= 0.0) { "a source may only add to the base cost, never take from it" }
        val i = index(row, col)
        val cell = cells[i]
        if (!cell.passable) return
        val state = if (tag.ordinal > cell.state.ordinal) tag else cell.state
        cells[i] = AvoidCell(state, cell.sourceCostSec + extraSec)
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
        cells[i] = AvoidCell(AvoidCellState.FREE, baseCostSec)
        zoneLimitKn[i] = 0.0
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
 */
fun rasterize(
    box: BBox,
    cellM: Double,
    paceKn: Double,
    marginM: Double,
    edges: List<AvoidEdge>,
    openCoast: List<List<LatLng>>,
    capLatNorth: Double,
    field: RouteCostField = RouteCostField.EMPTY,
    zones: List<ZoneRing> = emptyList(),
    blockZones: Boolean = false
): AvoidGrid {
    val midLat = (box.latSouth + box.latNorth) / 2.0
    val mPerDegLat = SpatialOperations.EARTH_RADIUS_M * PI / 180.0
    val mPerDegLon = mPerDegLat * cos(Math.toRadians(midLat))
    val cellSizeDegLat = cellM / mPerDegLat
    val cellSizeDegLon = cellM / mPerDegLon
    val cols = ceil((box.lonEast - box.lonWest) / cellSizeDegLon).toInt().coerceAtLeast(1)
    val rows = ceil((box.latNorth - box.latSouth) / cellSizeDegLat).toInt().coerceAtLeast(1)
    val grid = AvoidGrid(
        box.latSouth, box.lonWest, cellSizeDegLat, cellSizeDegLon, rows, cols, cellM,
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
            val edge = AvoidEdge(polyline[i], polyline[i + 1], LandRingOrientation.OPEN_COAST)
            forEachCellNear(grid, edge, marginM, mPerDegLat, mPerDegLon) { r, c, _ ->
                grid.markLand(r, c)
            }
        }
    }

    // 2. Interior fill for CCW rings.
    val ringEdges = ArrayList<AvoidEdge>()
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
    //    the dearest price; a cell the sweep or the field already sealed stays land either way.
    if (zones.isNotEmpty()) {
        fillZonesEvenOdd(grid, zones, blockZones)
    }

    return grid
}

/**
 * Walks the cells whose centre is within [radiusM] of [edge], in metres, via a degrees-expanded bbox,
 * handing [action] the cell-centre-to-segment distance it computed — the reading the margin band
 * decides on.
 */
private fun forEachCellNear(
    grid: AvoidGrid,
    edge: AvoidEdge,
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
private fun fillRingsEvenOdd(grid: AvoidGrid, ringEdges: List<AvoidEdge>) {
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
private fun fillClosedRingEvenOdd(grid: AvoidGrid, ring: List<LatLng>) {
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
 * action writes the zone's **limit** by default and a land mark when [blockZones] is set (the
 * forced-crossing probe). There is no collar here: the standoff leaves the grid for the pull's own
 * clearance, so a cell beside a ring is open water in the field the search reads.
 */
private fun fillZonesEvenOdd(grid: AvoidGrid, zones: List<ZoneRing>, blockZones: Boolean) {
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
    }
}

/** Shared even-odd scanline: [crossingsFor] returns the boundary crossings for one row's latitude. */
private fun fillScanlineEvenOdd(
    grid: AvoidGrid,
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
