package ykws.android.maro.spatial.multipass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.markers.BBox

/**
 * **The end disc's own two reads, pinned at their own level**: the reach is read off the margin and the
 * cell, and a disc cell takes the base cost and **keeps its zone limit**, which is the one read the disc
 * waives and the price it does not.
 */
class BerthCarveTest {

    private val marginM = 50.0

    /** The reach is read off the two shipped sizes, never preferred: `ceil(marginM / cellM) + 1`. */
    @Test
    fun theReachIsDerivedFromTheMarginAndTheCell() {
        assertEquals("one cell of margin is two cells of reach", 2, endDiscReachCells(50.0, 50.0))
        assertEquals("a margin under one cell still reaches one cell out", 2, endDiscReachCells(10.0, 50.0))
        assertEquals("a two-cell margin reaches three", 3, endDiscReachCells(100.0, 50.0))
    }

    /**
     * The disc's own write: the cell opens at the grid's base cost and **keeps its zone limit**, so a zone
     * standing in the disc still prices the cell the A\* reads — the margin alone is waived.
     */
    @Test
    fun openingADiscCellKeepsItsZoneLimitAndResetsItsCost() {
        val box = BBox(43.49, 43.51, 6.99, 7.01)
        val grid = rasterize(box, 50.0, 28.0, marginM, emptyList(), emptyList(), box.latNorth)
        val cell = CellIndex(3, 3)
        // The limit first — `applyZoneLimit` never writes a LAND cell — then the sweep's own bar.
        grid.applyZoneLimit(cell.row, cell.col, 10.0)
        grid.markLand(cell.row, cell.col)
        assertFalse("the cell starts barred", grid.passable(cell.row, cell.col))

        grid.openEndCell(cell.row, cell.col)

        assertTrue("the disc opens it", grid.passable(cell.row, cell.col))
        assertEquals("at the grid's base cost", grid.baseCostSec, grid.sourceCostSec(cell.row, cell.col), 1e-9)
        assertEquals("and it keeps its zone limit", 10.0, grid.zoneLimitKn(cell.row, cell.col), 1e-9)
    }
}
