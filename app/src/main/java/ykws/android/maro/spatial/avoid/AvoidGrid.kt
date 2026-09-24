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
 * The tagged cell state. A cell is never a bare blocked boolean: it carries one of the four tags
 * plus a source cost in metres, so stage 2's band and stage 3's zones add a tag and a cost without
 * reworking the rasterizer or the A*. Stage 1 uses [FREE] and [LAND] only.
 */
enum class AvoidCellState { FREE, LAND, BAND, ZONE }

/** A tagged, costed cell — [sourceCostM] is the metres-equivalent cost of entering the cell. */
data class AvoidCell(
    val state: AvoidCellState = AvoidCellState.FREE,
    val sourceCostM: Double = 0.0
) {
    /** [LAND] is impassable; every other tag is passable, priced by its source cost. */
    val passable: Boolean get() = state != AvoidCellState.LAND
}

/**
 * The corridor grid: a row-major field of [AvoidCell] over a lat/lon box, with the cell-centre
 * geometry the rasterizer, the A* and the pull all read.
 */
class AvoidGrid(
    val latSouth: Double,
    val lonWest: Double,
    val cellSizeDegLat: Double,
    val cellSizeDegLon: Double,
    val rows: Int,
    val cols: Int,
    val cellM: Double
) {
    private val cells = Array(rows * cols) { AvoidCell(sourceCostM = cellM) }

    fun index(row: Int, col: Int): Int = row * cols + col

    fun inBounds(row: Int, col: Int): Boolean = row in 0 until rows && col in 0 until cols

    fun cell(row: Int, col: Int): AvoidCell = cells[index(row, col)]

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
        cells[index(row, col)] = AvoidCell(AvoidCellState.FREE, cellM)
    }
}

/**
 * Rasterizes the harvested edges and open-coast polylines into a tagged, costed corridor grid.
 *
 * - Every edge and open-coast segment paints a **margin band**: cells whose centre is within
 *   [marginM] of the segment are land — never stepped point discs, which would leave holes where
 *   two discs of a 50 m step stand tangent.
 * - A **CCW ring** (islands and hazard rings) has its interior filled land by an even-odd scanline,
 *   while a **CW basin** keeps its interior water and only its breakwater edge takes the margin.
 * - The **open coast** is closed into a land polygon: each ordered polyline is capped on its land
 *   side at [capLatNorth] and even-odd filled as land, so a wide landmass's interior — which sits
 *   far from any coast edge — is sealed without a single water query.
 */
fun rasterize(
    box: BBox,
    cellM: Double,
    marginM: Double,
    edges: List<AvoidEdge>,
    openCoast: List<List<LatLng>>,
    capLatNorth: Double
): AvoidGrid {
    val midLat = (box.latSouth + box.latNorth) / 2.0
    val mPerDegLat = SpatialOperations.EARTH_RADIUS_M * PI / 180.0
    val mPerDegLon = mPerDegLat * cos(Math.toRadians(midLat))
    val cellSizeDegLat = cellM / mPerDegLat
    val cellSizeDegLon = cellM / mPerDegLon
    val cols = ceil((box.lonEast - box.lonWest) / cellSizeDegLon).toInt().coerceAtLeast(1)
    val rows = ceil((box.latNorth - box.latSouth) / cellSizeDegLat).toInt().coerceAtLeast(1)
    val grid = AvoidGrid(box.latSouth, box.lonWest, cellSizeDegLat, cellSizeDegLon, rows, cols, cellM)

    // 1. Margin band over every edge — CCW ring and CW basin — and every open-coast segment.
    for (edge in edges) {
        forEachCellNear(grid, edge, marginM, mPerDegLat, mPerDegLon) { r, c -> grid.markLand(r, c) }
    }
    for (polyline in openCoast) {
        for (i in 0 until polyline.size - 1) {
            val edge = AvoidEdge(polyline[i], polyline[i + 1], LandRingOrientation.OPEN_COAST)
            forEachCellNear(grid, edge, marginM, mPerDegLat, mPerDegLon) { r, c -> grid.markLand(r, c) }
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

    return grid
}

/** Walks the cells whose centre is within [radiusM] of [edge], in metres, via a degrees-expanded bbox. */
private fun forEachCellNear(
    grid: AvoidGrid,
    edge: AvoidEdge,
    radiusM: Double,
    mPerDegLat: Double,
    mPerDegLon: Double,
    action: (row: Int, col: Int) -> Unit
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
            if (SpatialOperations.pointToSegmentDistance(centre, edge.a, edge.b) <= radiusM) {
                action(r, c)
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
    fillScanlineEvenOdd(grid) { _, lat ->
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
    }
}

/**
 * Even-odd scanline fill of one arbitrary closed ring given as an ordered vertex list whose last
 * vertex closes to its first. The open coast uses it once it is capped into a land polygon.
 */
private fun fillClosedRingEvenOdd(grid: AvoidGrid, ring: List<LatLng>) {
    if (ring.size < 3) return
    fillScanlineEvenOdd(grid) { _, lat ->
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
    }
}

/** Shared even-odd scanline: [crossingsFor] returns the boundary crossings for one row's latitude. */
private fun fillScanlineEvenOdd(grid: AvoidGrid, crossingsFor: (row: Int, lat: Double) -> List<Double>) {
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
            if (inside) grid.markLand(row, col)
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
