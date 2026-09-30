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
 * The 300 m band as a **limit on the grid**, priced by the A\* per expansion: the rasterizer writes the
 * band's own limit on the water inside its width and the band's collar limit on the strip beyond it,
 * the A\* prices them at the pass's cursor, and the pull is steered by the same law. A band cell and a
 * ring cell of the same limit are proven to cost the search the same **through the A\*'s own read**,
 * never by comparing one function's output to itself.
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

    /** The band's own limit (kn), as the law states it — the price's only source. */
    private val bandLimitKn = 5.0

    /** The price cursor λ the band's price is keyed on. */
    private val lambda = 1.5

    /** The one outside-margin fraction the shipped keys carry; both arms use it here. */
    private val outsideMarginFraction = 0.66

    /**
     * The A\*'s own price read, built here exactly as the engine builds it: the strictest limit in force
     * in full, the ring's collar and the band's at their own fractions. Both the band and a ring reach it
     * through [AvoidSearch], so the invariant below proves the search rather than one function twice.
     */
    private val priceAt: (Double, Double, Double) -> Double = { interiorKn, collarKn, bandCollarKn ->
        slowWaterPriceAt(
            cellM, paceKn, lambda, interiorKn, collarKn, bandCollarKn,
            outsideMarginFraction, outsideMarginFraction
        )
    }

    /** An east-west coast at 43.510 with the land north of it and the band south of it. */
    private val coast = listOf(LatLng(43.510, 7.00), LatLng(43.510, 7.02))

    private fun bandLaw() = BandLaw(bandM, bandLimitKn, bandOutsideMarginM)

    @Test
    fun theBandWritesItsLimitInsideItsWidthAndTagsIt() {
        val grid = rasterize(
            box, cellM, paceKn, marginM, emptyList(), listOf(coast), box.latNorth, band = bandLaw()
        )

        val inBand = grid.cellOf(43.510 - 200.0 / mPerDegLat(), 7.010)
        val cell = grid.cell(inBand.row, inBand.col)
        assertEquals("a cell inside the width carries the band's limit", bandLimitKn, grid.limitKn(inBand.row, inBand.col), 1e-9)
        assertEquals("the price is the A*'s read, never the grid's base", grid.baseCostSec, cell.sourceCostSec, 1e-9)
        assertEquals("and the band's law water wears the band tag", AvoidCellState.BAND, cell.state)

        val outside = grid.cellOf(43.510 - 600.0 / mPerDegLat(), 7.010)
        assertEquals(
            "a cell beyond the band's reach carries no limit",
            0.0, grid.limitKn(outside.row, outside.col), 1e-9
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
    fun theBandsOutsideMarginCarriesTheCollarLimitAndNoTag() {
        val grid = rasterize(
            box, cellM, paceKn, marginM, emptyList(), listOf(coast), box.latNorth, band = bandLaw()
        )
        var widthCell: CellIndex? = null
        var collarCell: CellIndex? = null
        for (row in 0 until grid.rows) {
            for (col in 0 until grid.cols) {
                if (!grid.cell(row, col).passable) continue
                val d = distanceToCoastM(grid.center(row, col))
                when {
                    d <= bandM && widthCell == null -> widthCell = CellIndex(row, col)
                    d > bandM && d <= bandReachM(bandM, bandOutsideMarginM) && collarCell == null ->
                        collarCell = CellIndex(row, col)
                }
            }
        }

        assertTrue("a width cell exists", widthCell != null)
        assertTrue("a collar cell exists", collarCell != null)
        val width = widthCell!!
        val collar = collarCell!!
        assertEquals(
            "the width carries the band's limit, priced in full by the A*",
            bandLimitKn, grid.limitKn(width.row, width.col), 1e-9
        )
        assertEquals(
            "the collar carries the band's limit too, priced at the band's own fraction by the A*",
            bandLimitKn, grid.bandCollarLimitKn(collar.row, collar.col), 1e-9
        )
        assertEquals(
            "and the collar wears no law tag, since the limit in force is the width's",
            AvoidCellState.FREE, grid.cell(collar.row, collar.col).state
        )
        assertEquals(
            "the width is not also a collar, so the two prices never double up on one cell",
            0.0, grid.bandCollarLimitKn(width.row, width.col), 1e-9
        )
    }

    @Test
    fun theMarginStaysLandAndIsNeverPriced() {
        val grid = rasterize(
            box, cellM, paceKn, marginM, emptyList(), listOf(coast), box.latNorth, band = bandLaw()
        )

        val land = grid.cellOf(43.510 + 35.0 / mPerDegLat(), 7.010)
        assertFalse("the land side of the coast is sealed", grid.cell(land.row, land.col).passable)
        assertEquals(
            "and the band's law never lands on the shore",
            0.0, grid.bandLimitKn(land.row, land.col), 1e-9
        )
    }

    @Test
    fun theBandTurnsOnOnlyWithItsLaw() {
        val unpriced = rasterize(
            box, cellM, paceKn, marginM, emptyList(), listOf(coast), box.latNorth, band = null
        )

        val inBand = unpriced.cellOf(43.510 - 200.0 / mPerDegLat(), 7.010)
        assertEquals(
            "no band law means no limit written at all",
            0.0, unpriced.limitKn(inBand.row, inBand.col), 1e-9
        )
        assertEquals(AvoidCellState.FREE, unpriced.cell(inBand.row, inBand.col).state)
    }

    /** The limit reaches the A*: a band priced out of proportion is walked around. */
    @Test
    fun theSearchSteersOutOfADearlyPricedBand() = runTest {
        val path = AvoidSearch.search(bandedGrid(bandLimitKn), CellIndex(5, 0), CellIndex(5, 10), paceMps, priceAt).path

        assertTrue("the corridor is still connected round the band", path != null)
        assertTrue("no band cell is stepped on", path!!.none { bandCell(it) })
    }

    /** Its control: the same band, its limit at the pace, costs nothing — a price is a dial, not a wall. */
    @Test
    fun aBandLimitAtThePaceIsWorthCrossing() = runTest {
        val path = AvoidSearch.search(bandedGrid(paceKn), CellIndex(5, 0), CellIndex(5, 10), paceMps, priceAt).path

        assertTrue("a limit at the pace prices as open water", path!!.any { bandCell(it) })
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
     * **The law's invariant, proven through the search.** A 5 kn band limit and a 5 kn ring limit price
     * identically: the two grids differ only in **which array carries the limit**, and the A\* — reading
     * its own per-expansion price closure over [AvoidGrid.limitKn] — takes the very same way round both.
     * The limit is the same, the cursor is one, and the search's own decisions agree.
     */
    @Test
    fun aBandCellAndARingCellOfTheSameLimitCostTheSearchIdentically() = runTest {
        val banded = AvoidGrid(
            box.latSouth, box.lonWest, 0.0005, 0.0007, 11, 11, cellM, baseCostSec(cellM, paceKn)
        )
        val ringed = AvoidGrid(
            box.latSouth, box.lonWest, 0.0005, 0.0007, 11, 11, cellM, baseCostSec(cellM, paceKn)
        )
        for (r in 1..9) {
            banded.applyBandLimit(r, 5, bandLimitKn)
            ringed.applyZoneLimit(r, 5, bandLimitKn)
        }
        val start = CellIndex(5, 0)
        val aim = CellIndex(5, 10)

        val bandPath = AvoidSearch.search(banded, start, aim, paceMps, priceAt).path
        val ringPath = AvoidSearch.search(ringed, start, aim, paceMps, priceAt).path

        assertEquals(
            "the A* takes the same way round a band limit as round a ring limit of the same slowness",
            ringPath, bandPath
        )
        assertTrue("and the price was read at all: both routes round the slow column", bandPath!!.none { bandCell(it) })
    }

    /** A column of band-limit cells, leaving the top and bottom rows open. */
    private fun bandedGrid(limitKn: Double): AvoidGrid {
        val grid = AvoidGrid(
            box.latSouth, box.lonWest, 0.0005, 0.0007, 11, 11, cellM, baseCostSec(cellM, paceKn)
        )
        for (r in 1..9) grid.applyBandLimit(r, 5, limitKn)
        return grid
    }

    private fun bandCell(cell: CellIndex): Boolean = cell.col == 5 && cell.row in 1..9

    private fun distanceToCoastM(p: LatLng): Double {
        var best = Double.MAX_VALUE
        for (i in 0 until coast.size - 1) {
            best = min(best, SpatialOperations.pointToSegmentDistance(p, coast[i], coast[i + 1]))
        }
        return best
    }

    private fun mPerDegLat(): Double = SpatialOperations.EARTH_RADIUS_M * Math.PI / 180.0
}
