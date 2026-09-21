package ykws.android.maro.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.BBox

/**
 * The corridor of §10.3 step 1 and §10.5 — the bound the design requires be **stated** rather than
 * implied, because a route needing to leave it is not found and the only honest answer is to say so.
 */
class RouteCorridorTest {

    private val gridBox = BBox(latSouth = 43.50, latNorth = 43.52, lonWest = 7.00, lonEast = 7.02)

    private fun grid() = RouteHarness.syntheticAssets(gridBox, { _, _ -> 20f }).grid

    @Test
    fun `corridor inflates the A to B segment by the margin on all four sides`() {
        val a = LatLng(43.510, 7.005)
        val b = LatLng(43.515, 7.015)
        val box = RouteCorridor.bboxOf(a, b, 1_000.0)

        assertEquals(43.510 - RouteConfig.latDegForM(1_000.0), box.latSouth, 1e-12)
        assertEquals(43.515 + RouteConfig.latDegForM(1_000.0), box.latNorth, 1e-12)
        val dLon = RouteConfig.lonDegForM(1_000.0, (a.latitude + b.latitude) / 2.0)
        assertEquals(7.005 - dLon, box.lonWest, 1e-12)
        assertEquals(7.015 + dLon, box.lonEast, 1e-12)
    }

    @Test
    fun `clip restricts a corridor to the grid and reports a miss as null`() {
        val wide = RouteCorridor.bboxOf(LatLng(43.49, 6.98), LatLng(43.53, 7.04), 500.0)
        val clipped = RouteCorridor.clipTo(wide, gridBox)
        assertNotNull(clipped)
        assertEquals(43.50, clipped!!.latSouth, 1e-12)
        assertEquals(43.52, clipped.latNorth, 1e-12)
        assertEquals(7.00, clipped.lonWest, 1e-12)
        assertEquals(7.02, clipped.lonEast, 1e-12)

        val far = RouteCorridor.bboxOf(LatLng(43.60, 7.10), LatLng(43.61, 7.11), 100.0)
        assertNull(RouteCorridor.clipTo(far, gridBox))
    }

    @Test
    fun `the grid window covers the corridor plus the dilation kernel`() {
        val grid = grid()
        val box = BBox(43.505, 43.507, 7.005, 7.007)
        val rows = RouteCorridor.rowsFor(grid, box, extraCells = 2)
        val cols = RouteCorridor.colsFor(grid, box, extraCells = 2)

        val southRow = RouteCorridor.rowsFor(grid, box, 0).first
        val northRow = RouteCorridor.rowsFor(grid, box, 0).last
        assertEquals(2, southRow - rows.first)
        assertEquals(2, rows.last - northRow)
        val westCol = RouteCorridor.colsFor(grid, box, 0).first
        assertEquals(2, westCol - cols.first)
        assertTrue(rows.first >= 0 && rows.last < grid.rows)
        assertTrue(cols.first >= 0 && cols.last < grid.cols)
    }

    @Test
    fun `contains is exact at the edge and honours the margin`() {
        val box = BBox(43.50, 43.52, 7.00, 7.02)
        assertTrue(RouteCorridor.contains(box, LatLng(43.51, 7.01)))
        assertTrue(RouteCorridor.contains(box, LatLng(43.50, 7.00)))
        assertFalse(RouteCorridor.contains(box, LatLng(43.4999, 7.01)))
        assertTrue(RouteCorridor.contains(box, LatLng(43.4999, 7.01), marginDeg = 0.001))
    }
}
