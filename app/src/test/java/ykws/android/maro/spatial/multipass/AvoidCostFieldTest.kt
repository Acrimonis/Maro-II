package ykws.android.maro.spatial.multipass

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.spatial.Units

/**
 * The unified cost field where it meets the grid and the A*: the base cost is always written, a source
 * may only add to it, the dearest price buys the tag, and the price actually steers the search — which
 * is the whole reason a source is a *price* rather than a tag.
 */
class AvoidCostFieldTest {

    private val box = BBox(43.50, 43.52, 7.00, 7.02)
    private val cellM = 50.0

    /** The pace every cost here is built at, and the pace the A* bounds its time with. */
    private val paceKn = 28.0

    private val paceMps = Units.knotsToMps(paceKn)

    @Test
    fun anEmptyFieldStillWritesTheBaseCostToEveryCell() {
        val grid = rasterize(
            box, cellM, paceKn, 25.0, emptyList(), emptyList(), box.latNorth, RouteCostField.EMPTY
        )

        for (r in 0 until grid.rows) {
            for (c in 0 until grid.cols) {
                assertEquals(
                    "every cell carries the base cost in seconds, never a defaulted zero",
                    grid.baseCostSec, grid.cell(r, c).sourceCostSec, 1e-9
                )
            }
        }
        assertEquals(
            "and the base is one cell of water at the pace, not a metre count",
            baseCostSec(cellM, paceKn),
            grid.baseCostSec,
            1e-9
        )
    }

    @Test
    fun aSoftSourceAddsToTheBaseAndNeverReplacesIt() {
        val priced = RouteCostSource.Soft(
            priceSec = { p -> if (inPatch(p)) 300.0 else 0.0 },
            tag = MultipassCellState.BAND
        )
        val grid = rasterize(
            box, cellM, paceKn, 25.0, emptyList(), emptyList(), box.latNorth, RouteCostField(listOf(priced))
        )

        val inside = grid.cellOf(43.510, 7.010)
        assertEquals(
            "the price is added to the base, in the same seconds",
            grid.baseCostSec + 300.0, grid.cell(inside.row, inside.col).sourceCostSec, 1e-9
        )
        assertEquals(
            "the price's own tag is written",
            MultipassCellState.BAND, grid.cell(inside.row, inside.col).state
        )

        val outside = grid.cellOf(43.5005, 7.0005)
        assertEquals(
            "unpriced water keeps the base alone",
            grid.baseCostSec, grid.cell(outside.row, outside.col).sourceCostSec, 1e-9
        )
        assertEquals(MultipassCellState.FREE, grid.cell(outside.row, outside.col).state)

        for (r in 0 until grid.rows) {
            for (c in 0 until grid.cols) {
                assertTrue(
                    "no passable cell is ever cheaper than the base",
                    grid.cell(r, c).sourceCostSec >= grid.baseCostSec - 1e-9
                )
            }
        }
    }

    /** A wall is not a price: pricing a land cell neither lights it up nor moves its cost. */
    @Test
    fun aWallIsNeverPriced() {
        val grid = rasterize(
            box, cellM, paceKn, 25.0, emptyList(), emptyList(), box.latNorth, RouteCostField.EMPTY
        )
        grid.markLand(3, 4)

        grid.addSourceCost(3, 4, 500.0, MultipassCellState.ZONE)

        assertFalse("a priced land cell stays land", grid.cell(3, 4).passable)
        assertEquals("and keeps the cost it had", grid.baseCostSec, grid.cell(3, 4).sourceCostSec, 1e-9)
    }

    @Test
    fun aHardSourcePaintsItsCellsLandAndTheOthersStayWater() {
        val wall = RouteCostSource.Hard(
            blockedAt = { p -> inPatch(p) },
            distanceAt = { p -> if (inPatch(p)) 0.0 else Double.MAX_VALUE }
        )
        val grid = rasterize(
            box, cellM, paceKn, 25.0, emptyList(), emptyList(), box.latNorth, RouteCostField(listOf(wall))
        )

        val inside = grid.cellOf(43.510, 7.010)
        assertFalse("a rastered wall paints its cells land", grid.cell(inside.row, inside.col).passable)

        val outside = grid.cellOf(43.5005, 7.0005)
        assertTrue("and leaves the rest of the water alone", grid.cell(outside.row, outside.col).passable)
    }

    /** The price reaches the A*: a wall priced out of all proportion is walked around. */
    @Test
    fun aDearlyPricedWallSteersTheSearchAroundIt() = runTest {
        val path = MultipassSearch.search(walledGrid(10_000.0), CellIndex(5, 0), CellIndex(5, 10), paceMps).path

        assertTrue("the corridor is still connected round the wall", path != null)
        assertTrue("no priced cell is stepped on", path!!.none { pricedCell(it) })
    }

    /** Its control: the same wall, priced a hair, is worth crossing — the price is a dial, not a wall. */
    @Test
    fun aCheaplyPricedWallIsWorthCrossing() = runTest {
        val path = MultipassSearch.search(walledGrid(1.0), CellIndex(5, 0), CellIndex(5, 10), paceMps).path

        assertTrue("the cheap wall is crossed rather than rounded", path!!.any { pricedCell(it) })
    }

    /** The priced wall: six cells tall in the middle column, leaving the top and bottom rows free. */
    private fun pricedCell(cell: CellIndex): Boolean = cell.col == 5 && cell.row in 1..9

    /** A vertical wall of priced cells in column 5, leaving the top and bottom rows open. */
    private fun walledGrid(priceSec: Double): MultipassGrid {
        val grid = MultipassGrid(
            box.latSouth, box.lonWest, 0.0005, 0.0007, 11, 11, cellM, baseCostSec(cellM, paceKn)
        )
        for (r in 1..9) grid.addSourceCost(r, 5, priceSec, MultipassCellState.ZONE)
        return grid
    }

    private fun inPatch(p: LatLng): Boolean =
        p.latitude in 43.5095..43.5105 && p.longitude in 7.009..7.011
}
