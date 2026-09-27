package ykws.android.maro.spatial.avoid

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.spatial.SpatialOperations
import ykws.android.maro.spatial.Units
import kotlin.math.min

/**
 * The 300 m band as the first **price** through the field: the field's own soft source prices the
 * cells inside the band's reach and tags them, a wall inside the margin is never priced, and both the
 * A* and the pull are steered by the price — which is the whole of what a soft source promises.
 *
 * The band price reaches the grid through the same soft source `RouteAvoidEngine.costField()` builds,
 * never through a rasterize sweep argument.
 */
class AvoidBandCostTest {

    private val box = BBox(43.50, 43.52, 7.00, 7.02)
    private val cellM = 50.0
    private val bandM = 300.0
    private val marginM = 25.0

    /** The band's outside margin, wide enough that the margin band holds whole cell centres. */
    private val bandOutsideMarginM = 100.0

    /** The pace every cost here is built at — and the pace the A* bounds its time with. */
    private val paceKn = 28.0

    private val paceMps = Units.knotsToMps(paceKn)

    /** The band's per-cell **excess in seconds**, at a soft-cost aversion of 1.5. */
    private val priceSec = bandPriceSec(cellM, paceKn, 1.5)

    /** An east-west coast at 43.510 with the land north of it and the band south of it. */
    private val coast = listOf(LatLng(43.510, 7.00), LatLng(43.510, 7.02))

    @Test
    fun theBandPricesTheWaterInsideItsReachAndTagsIt() {
        val grid = rasterize(
            box, cellM, paceKn, marginM, emptyList(), listOf(coast), box.latNorth, bandField(priceSec)
        )

        val inBand = grid.cellOf(43.510 - 200.0 / mPerDegLat(), 7.010)
        assertEquals(
            "a cell inside the band carries the base plus the price, in the same seconds",
            grid.baseCostSec + priceSec, grid.cell(inBand.row, inBand.col).sourceCostSec, 1e-9
        )
        assertEquals(AvoidCellState.BAND, grid.cell(inBand.row, inBand.col).state)

        val outside = grid.cellOf(43.510 - 600.0 / mPerDegLat(), 7.010)
        assertEquals(
            "a cell beyond the band's reach carries the base alone",
            grid.baseCostSec, grid.cell(outside.row, outside.col).sourceCostSec, 1e-9
        )
        assertEquals(AvoidCellState.FREE, grid.cell(outside.row, outside.col).state)

        for (row in 0 until grid.rows) {
            for (col in 0 until grid.cols) {
                assertTrue(
                    "no cell is ever cheaper than the base, band or not",
                    grid.cell(row, col).sourceCostSec >= grid.baseCostSec - 1e-9
                )
            }
        }
    }

    @Test
    fun theMarginStaysLandAndIsNeverPriced() {
        val grid = rasterize(
            box, cellM, paceKn, marginM, emptyList(), listOf(coast), box.latNorth, bandField(priceSec)
        )

        val land = grid.cellOf(43.510 + 35.0 / mPerDegLat(), 7.010)
        assertFalse("the land side of the coast is sealed", grid.cell(land.row, land.col).passable)
    }

    @Test
    fun theBandTurnsOnOnlyWithAPrice() {
        val unpriced = rasterize(
            box, cellM, paceKn, marginM, emptyList(), listOf(coast), box.latNorth, bandField(0.0)
        )

        val inBand = unpriced.cellOf(43.510 - 200.0 / mPerDegLat(), 7.010)
        assertEquals(
            "no price means no band write at all",
            unpriced.baseCostSec, unpriced.cell(inBand.row, inBand.col).sourceCostSec, 1e-9
        )
        assertEquals(AvoidCellState.FREE, unpriced.cell(inBand.row, inBand.col).state)
    }

    /** The price reaches the A*: a band priced out of proportion is walked around. */
    @Test
    fun theSearchSteersOutOfADearlyPricedBand() = runTest {
        val path = AvoidSearch.search(bandedGrid(10_000.0), CellIndex(5, 0), CellIndex(5, 10), paceMps).path

        assertTrue("the corridor is still connected round the band", path != null)
        assertTrue("no priced cell is stepped on", path!!.none { pricedCell(it) })
    }

    /** Its control: the same band, priced a hair, is worth crossing — a price is a dial, not a wall. */
    @Test
    fun aCheaplyPricedBandIsWorthCrossing() = runTest {
        val path = AvoidSearch.search(bandedGrid(1.0), CellIndex(5, 0), CellIndex(5, 10), paceMps).path

        assertTrue("the cheap band is crossed rather than rounded", path!!.any { pricedCell(it) })
    }

    /** The pull refuses a chord dearer than the cell path it would replace, so a priced corner holds. */
    @Test
    fun thePullRefusesAChordDearerThanThePathItReplaces() {
        val start = LatLng(43.50, 7.00)
        val aim = LatLng(43.50, 7.02)
        val detour = LatLng(43.503, 7.01)
        val path = listOf(start, detour, aim)
        val banded = RouteCostField(
            listOf(
                RouteCostSource.Soft(
                    priceSec = { p -> if (p.latitude < 43.5015) 120.0 else 0.0 },
                    tag = AvoidCellState.BAND
                )
            )
        )

        val kept = AvoidPull.pull(path, start, aim, marginM, banded)

        assertEquals("the priced chord is refused and the detour kept", path, kept)
    }

    /** Its control: the same walk over the same water with no price collapses to the two ends. */
    @Test
    fun thePullCollapsesTheSamePathWhenNothingIsPriced() {
        val start = LatLng(43.50, 7.00)
        val aim = LatLng(43.50, 7.02)
        val path = listOf(start, LatLng(43.503, 7.01), aim)

        val pulled = AvoidPull.pull(path, start, aim, marginM, RouteCostField.EMPTY)

        assertEquals(listOf(start, aim), pulled)
    }

    /**
     * The band's outside margin, priced at the fraction: the core band pays [priceSec] in full and the
     * ring between the band's own width and its outside margin pays [priceSec] × the fraction — the
     * split [bandPriceAt] carries, read off the same cells the rasterizer wrote.
     */
    @Test
    fun theBandsOutsideMarginPricesAtTheFraction() {
        val grid = rasterize(
            box, cellM, paceKn, marginM, emptyList(), listOf(coast), box.latNorth, bandField(priceSec)
        )
        var coreCell: CellIndex? = null
        var marginCell: CellIndex? = null
        for (row in 0 until grid.rows) {
            for (col in 0 until grid.cols) {
                val cell = grid.cell(row, col)
                if (!cell.passable) continue
                val d = distanceToCoastM(grid.center(row, col))
                when {
                    d <= bandM && coreCell == null -> coreCell = CellIndex(row, col)
                    d > bandM && d <= bandReachM(bandM, bandOutsideMarginM) && marginCell == null ->
                        marginCell = CellIndex(row, col)
                }
            }
        }

        assertTrue("a core cell inside the band exists", coreCell != null)
        assertTrue("a margin cell inside the outside margin exists", marginCell != null)
        assertEquals(
            "the core band prices at the full cost",
            grid.baseCostSec + priceSec,
            grid.cell(coreCell!!.row, coreCell.col).sourceCostSec,
            1e-9
        )
        assertEquals(
            "the outside margin prices at the fraction of the core",
            grid.baseCostSec + priceSec * 0.66,
            grid.cell(marginCell!!.row, marginCell.col).sourceCostSec,
            1e-9
        )
    }

    /**
     * The band as `RouteAvoidEngine.costField()` builds it: a single soft source priced [priceSec]
     * seconds inside the band's own width, a fraction of it in the outside margin, and 0 beyond — the
     * split [bandPriceAt] carries.
     */
    private fun bandField(priceSec: Double, costFraction: Double = 0.66): RouteCostField =
        RouteCostField(
            listOf(
                RouteCostSource.Soft(
                    priceSec = { p ->
                        bandPriceAt(
                            bandM, bandOutsideMarginM, priceSec, costFraction, distanceToCoastM(p)
                        )
                    },
                    tag = AvoidCellState.BAND
                )
            )
        )

    /** A band of priced cells in column 5, leaving the top and bottom rows open. */
    private fun bandedGrid(priceSec: Double): AvoidGrid {
        val grid = AvoidGrid(
            box.latSouth, box.lonWest, 0.0005, 0.0007, 11, 11, cellM, baseCostSec(cellM, paceKn)
        )
        for (r in 1..9) grid.addSourceCost(r, 5, priceSec, AvoidCellState.BAND)
        return grid
    }

    private fun pricedCell(cell: CellIndex): Boolean = cell.col == 5 && cell.row in 1..9

    private fun distanceToCoastM(p: LatLng): Double {
        var best = Double.MAX_VALUE
        for (i in 0 until coast.size - 1) {
            best = min(best, SpatialOperations.pointToSegmentDistance(p, coast[i], coast[i + 1]))
        }
        return best
    }

    private fun mPerDegLat(): Double = SpatialOperations.EARTH_RADIUS_M * Math.PI / 180.0
}
