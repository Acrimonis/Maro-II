package ykws.android.maro.spatial.avoid

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.BBox

/**
 * The 300 m band as the first **price** through the field: the rasterizer's single sweep prices the
 * cells inside the band's reach and tags them, a wall inside the margin is never priced, and both the
 * A* and the pull are steered by the price — which is the whole of what a soft source promises.
 */
class AvoidBandCostTest {

    private val box = BBox(43.50, 43.52, 7.00, 7.02)
    private val cellM = 50.0
    private val bandM = 300.0
    private val marginM = 25.0
    private val priceM = bandPriceM(cellM, 1.5)

    /** An east-west coast at 43.510 with the land north of it and the band south of it. */
    private val coast = listOf(LatLng(43.510, 7.00), LatLng(43.510, 7.02))

    @Test
    fun theBandPricesTheWaterInsideItsReachAndTagsIt() {
        val grid = rasterize(
            box, cellM, marginM, emptyList(), listOf(coast), box.latNorth,
            RouteCostField.EMPTY, bandM, priceM
        )

        val inBand = grid.cellOf(43.510 - 200.0 / mPerDegLat(), 7.010)
        assertEquals(
            "a cell inside the band carries the base plus the price",
            grid.baseCostM + priceM, grid.cell(inBand.row, inBand.col).sourceCostM, 1e-9
        )
        assertEquals(AvoidCellState.BAND, grid.cell(inBand.row, inBand.col).state)

        val outside = grid.cellOf(43.510 - 600.0 / mPerDegLat(), 7.010)
        assertEquals(
            "a cell beyond the band's reach carries the base alone",
            grid.baseCostM, grid.cell(outside.row, outside.col).sourceCostM, 1e-9
        )
        assertEquals(AvoidCellState.FREE, grid.cell(outside.row, outside.col).state)

        for (row in 0 until grid.rows) {
            for (col in 0 until grid.cols) {
                assertTrue(
                    "no cell is ever cheaper than the base, band or not",
                    grid.cell(row, col).sourceCostM >= grid.baseCostM - 1e-9
                )
            }
        }
    }

    @Test
    fun theMarginStaysLandAndIsNeverPriced() {
        val grid = rasterize(
            box, cellM, marginM, emptyList(), listOf(coast), box.latNorth,
            RouteCostField.EMPTY, bandM, priceM
        )

        val land = grid.cellOf(43.510 + 35.0 / mPerDegLat(), 7.010)
        assertFalse("the land side of the coast is sealed", grid.cell(land.row, land.col).passable)
    }

    @Test
    fun theBandTurnsOnOnlyWithAPrice() {
        val unpriced = rasterize(
            box, cellM, marginM, emptyList(), listOf(coast), box.latNorth,
            RouteCostField.EMPTY, bandM, 0.0
        )

        val inBand = unpriced.cellOf(43.510 - 200.0 / mPerDegLat(), 7.010)
        assertEquals(
            "no price means no band write at all",
            unpriced.baseCostM, unpriced.cell(inBand.row, inBand.col).sourceCostM, 1e-9
        )
        assertEquals(AvoidCellState.FREE, unpriced.cell(inBand.row, inBand.col).state)
    }

    /** The price reaches the A*: a band priced out of proportion is walked around. */
    @Test
    fun theSearchSteersOutOfADearlyPricedBand() = runTest {
        val path = AvoidSearch.search(bandedGrid(10_000.0), CellIndex(5, 0), CellIndex(5, 10))

        assertTrue("the corridor is still connected round the band", path != null)
        assertTrue("no priced cell is stepped on", path!!.none { pricedCell(it) })
    }

    /** Its control: the same band, priced a hair, is worth crossing — a price is a dial, not a wall. */
    @Test
    fun aCheaplyPricedBandIsWorthCrossing() = runTest {
        val path = AvoidSearch.search(bandedGrid(1.0), CellIndex(5, 0), CellIndex(5, 10))

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
                    priceM = { p -> if (p.latitude < 43.5015) 200.0 else 0.0 },
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

    /** A band of priced cells in column 5, leaving the top and bottom rows open. */
    private fun bandedGrid(price: Double): AvoidGrid {
        val grid = AvoidGrid(box.latSouth, box.lonWest, 0.0005, 0.0007, 11, 11, cellM)
        for (r in 1..9) grid.addSourceCost(r, 5, price, AvoidCellState.BAND)
        return grid
    }

    private fun pricedCell(cell: CellIndex): Boolean = cell.col == 5 && cell.row in 1..9

    private fun mPerDegLat(): Double = ykws.android.maro.spatial.SpatialOperations.EARTH_RADIUS_M * Math.PI / 180.0
}
