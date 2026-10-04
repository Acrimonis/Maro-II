package ykws.android.maro.spatial.multipass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.spatial.LandRingOrientation
import ykws.android.maro.spatial.SpatialOperations
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * **The fine layer's own cut** — the windows over the band's water, and the one property the reshape rests
 * on: every fine cell the mask would keep must be held by some window, while the allocation collapses to the
 * ribbon's own area.
 *
 * The mask is what keeps the water, so the cut is free to over-cover — every extra cell a window gains
 * outside the ribbon the mask paints land — and it is never free to miss one. That asymmetry is what these
 * fixtures assert: coverage is the correctness, and the cell count is the saving.
 */
class RouteFineWindowShapeTest {

    /** A long coastal corridor, the shape the crash's route makes: 20 km by 6.4 km. */
    private val corridor = BBox(43.500, 43.680, 7.000, 7.080)

    private val fineCellM = 20.0

    /** The reach the mask keeps water within, as the builder reads it on the shipped band. */
    private val reachM = 475.0

    private val lattice = WalkLattice.of(corridor, fineCellM)

    private val builder = RouteGridBuilder(EvolutiveGridPlan)

    /** A coast running the corridor's own length near its western side, as both harvested shapes. */
    private val coastPoints = listOf(
        LatLng(43.501, 7.004), LatLng(43.546, 7.006), LatLng(43.590, 7.008),
        LatLng(43.635, 7.010), LatLng(43.679, 7.012)
    )

    private val edges =
        listOf(MultipassEdge(coastPoints[0], coastPoints[1], LandRingOrientation.OPEN_COAST))

    private val openCoast = listOf(coastPoints.subList(1, coastPoints.size))

    private val coastSegments = coastPoints.zipWithNext()

    private val cellsDown = ceil((corridor.latNorth - corridor.latSouth) / lattice.cellSizeDegLat).toInt()

    private val cellsAcross = ceil((corridor.lonEast - corridor.lonWest) / lattice.cellSizeDegLon).toInt()

    /** The tile side the cut uses, in cells — a tile is exactly this many lattice cells on each axis. */
    private val sideCells = ceil(reachM / lattice.cellM).toInt()

    /**
     * **The equivalence the reshape is allowed on.** Every fine cell standing inside the ribbon — within the
     * reach of the coast, which is the water the mask keeps — is held by some window, so no verdict of the
     * walk can move.
     */
    @Test
    fun everyFineCellWithinTheReachIsHeldBySomeWindow() {
        val windows = builder.fineWindowBoxes(lattice, corridor, edges, openCoast, reachM)
        val (mPerDegLat, mPerDegLon) = lattice.metresPerDegree()
        val (nearRows, nearCols) = ribbonWindow(mPerDegLat, mPerDegLon)
        var inTheRibbon = 0
        for (row in nearRows) {
            for (col in nearCols) {
                val centre = lattice.center(row, col)
                val near = coastSegments.minOf {
                    SpatialOperations.pointToSegmentDistance(centre, it.first, it.second)
                }
                if (near > reachM) continue
                inTheRibbon++
                assertTrue(
                    "the window set holds the ribbon's cell at ($row, $col)",
                    windows.any { holds(it, centre) }
                )
            }
        }
        assertTrue("the fixture puts cells inside the ribbon", inTheRibbon > 500)
    }

    /** The saving: the ribbon's own tiles cost a fraction of the corridor-sized tile they replace. */
    @Test
    fun theWindowedFineLayerIsFarSmallerThanTheCorridorTile() {
        val windows = builder.fineWindowBoxes(lattice, corridor, edges, openCoast, reachM)
        val corridorCells = cellsDown * cellsAcross
        val windowCells = windows.sumOf { box ->
            ceil((box.latNorth - box.latSouth) / lattice.cellSizeDegLat).toInt() *
                ceil((box.lonEast - box.lonWest) / lattice.cellSizeDegLon).toInt()
        }

        assertTrue(
            "the ribbon costs a fraction of the corridor's own tile ($windowCells of $corridorCells)",
            windowCells * 3 < corridorCells
        )
    }

    /**
     * **The merge saves windows without moving a cell.** The merged windows hold exactly the lattice cells the
     * unmerged tiles held — the same set, at the estimator's own cell arithmetic — while there are strictly
     * fewer of them. That pair is the whole claim: the coverage is unchanged and the window count is not.
     */
    @Test
    fun theMergedWindowsHoldExactlyTheTilesCellsWithFewerWindows() {
        val tiles = builder.fineTileBoxes(lattice, corridor, edges, openCoast, reachM)
        val windows = builder.fineWindowBoxes(lattice, corridor, edges, openCoast, reachM)
        val tileCells = latticeCells(tiles)
        val windowCells = latticeCells(windows)
        val onlyTiles = tileCells - windowCells
        val onlyWindows = windowCells - tileCells

        assertTrue(
            "the merged windows hold exactly the tiles' own lattice cells " +
                "(${tiles.size} tiles vs ${windows.size} windows, onlyTiles=${onlyTiles.size}" +
                "${onlyTiles.take(6).map { cell(it) }}, onlyWindows=${onlyWindows.size}" +
                "${onlyWindows.take(6).map { cell(it) }})",
            onlyTiles.isEmpty() && onlyWindows.isEmpty()
        )
        assertTrue(
            "the merge returns strictly fewer windows (${windows.size} of ${tiles.size} tiles)",
            windows.size < tiles.size
        )
    }

    /** No coast beside the corridor is no fine water at all: the walk is the coarse grid alone. */
    @Test
    fun aCorridorWithNoCoastBesideItAsksForNoWindows() {
        val windows = builder.fineWindowBoxes(lattice, corridor, emptyList(), emptyList(), reachM)

        assertTrue("no coast, no ribbon, no windows", windows.isEmpty())
    }

    /** The estimator is the frame's own ceiling: a box's cells, asked before anything is built. */
    @Test
    fun theEstimatorCountsTheCellsTheFrameWouldAllocate() {
        val box = BBox(43.500, 43.510, 7.000, 7.020)
        val rows = ceil((box.latNorth - box.latSouth) / lattice.cellSizeDegLat).toInt()
        val cols = ceil((box.lonEast - box.lonWest) / lattice.cellSizeDegLon).toInt()

        assertEquals(
            "the estimate is the frame's own rows times its own columns",
            (rows * cols).toLong(),
            builder.cellsOf(box, lattice.cellSizeDegLat, lattice.cellSizeDegLon).toLong()
        )
    }

    /**
     * **The ceiling against the crash's own numbers.** The plain corridor of 2026-10-04 held 476 700 fine
     * cells and survived; the 1 178 555 its grown retry asked for is the walk the heap could not hold — so
     * the shipped ceiling sits between them, and the guard refuses exactly what killed the app.
     */
    @Test
    fun theShippedCeilingSitsBetweenTheTwoCorridorsTheCrashMeasured() {
        val ceiling = AppConfig.routeWalkMaxCells

        assertTrue(
            "the plain corridor's fine layer stands under the ceiling",
            builder.withinBudget(476_700, ceiling)
        )
        assertTrue(
            "and the grown corridor's does not",
            !builder.withinBudget(1_178_555, ceiling)
        )
    }

    /**
     * The cells the ribbon may stand on, as row and column ranges: the coast's own span grown by the reach —
     * so the fixture is walked where the answer can be wrong, and not over the whole corridor.
     */
    private fun ribbonWindow(mPerDegLat: Double, mPerDegLon: Double): Pair<IntRange, IntRange> {
        val south = coastPoints.minOf { it.latitude } - reachM / mPerDegLat
        val north = coastPoints.maxOf { it.latitude } + reachM / mPerDegLat
        val west = coastPoints.minOf { it.longitude } - reachM / mPerDegLon
        val east = coastPoints.maxOf { it.longitude } + reachM / mPerDegLon
        val rows = lattice.rowOf(south).coerceAtLeast(0)..lattice.rowOf(north).coerceAtMost(cellsDown - 1)
        val cols = lattice.colOf(west).coerceAtLeast(0)..lattice.colOf(east).coerceAtMost(cellsAcross - 1)
        return rows to cols
    }

    /** Whether a window's box holds a point — the coverage question, asked of the box itself. */
    private fun holds(box: BBox, point: LatLng): Boolean =
        point.latitude >= box.latSouth && point.latitude <= box.latNorth &&
            point.longitude >= box.lonWest && point.longitude <= box.lonEast

    /** One packed `(row, col)` key printed as a pair, for a bounded assertion message. */
    private fun cell(key: Long): String = "(${(key shr 32).toInt()},${key.toInt()})"

    /**
     * The lattice cells a box set holds, at the estimator's own arithmetic — a box counts whole lattice cells,
     * and on the fine layer's tile grid a tile is exactly [sideCells] cells a side. Every window stands on the
     * tile grid's own lines, so its south-west rounds to a tile corner and its extent to a whole number of
     * tiles; the box's cells are those tiles × the lattice's cell, clamped to the corridor's own cell counts.
     */
    private fun latticeCells(boxes: List<BBox>): Set<Long> {
        val cells = HashSet<Long>()
        for (box in boxes) {
            val row0 = tileIndex(box.latSouth - lattice.latSouth, lattice.cellSizeDegLat) * sideCells
            val col0 = tileIndex(box.lonWest - lattice.lonWest, lattice.cellSizeDegLon) * sideCells
            val rowTiles = tileIndex(box.latNorth - box.latSouth, lattice.cellSizeDegLat)
            val colTiles = tileIndex(box.lonEast - box.lonWest, lattice.cellSizeDegLon)
            val rowEnd = minOf(row0 + rowTiles * sideCells, cellsDown)
            val colEnd = minOf(col0 + colTiles * sideCells, cellsAcross)
            for (row in row0 until rowEnd) {
                for (col in col0 until colEnd) {
                    cells.add((row.toLong() shl 32) or (col.toLong() and 0xFFFF_FFFFL))
                }
            }
        }
        return cells
    }

    /** How many whole tiles [extentDeg] spans at [cellSizeDeg] — a tile being [sideCells] of those cells. */
    private fun tileIndex(extentDeg: Double, cellSizeDeg: Double): Int =
        (extentDeg / (cellSizeDeg * sideCells)).roundToInt().coerceAtLeast(0)
}
