package ykws.android.maro.data.depth

import org.junit.Assert.*
import org.junit.Test
import ykws.android.maro.data.model.BoundingBox
import ykws.android.maro.data.model.DepthDatum
import ykws.android.maro.data.model.DepthSource
import ykws.android.maro.data.model.MutableDepthGrid

class DepthIsobathsTest {

    @Test
    fun `builds an isobath around a central basin and maps it inside the bbox`() {
        val bbox = BoundingBox(latSouth = 43.50, latNorth = 43.60, lonWest = 7.00, lonEast = 7.10)
        val m = MutableDepthGrid.empty("t", bbox, 2000.0, DepthDatum.LAT)
        // Background 2 m, central block 10 m → the 5 m isobath encircles the centre.
        for (r in 0 until m.rows) for (c in 0 until m.cols)
            m.set(r, c, 2f, DepthSource.EMODNET, 60)
        for (r in 1 until m.rows - 1) for (c in 1 until m.cols - 1)
            m.set(r, c, 10f, DepthSource.EMODNET, 60)
        val grid = m.toImmutable(null, 0L, "t")

        val isobaths = DepthIsobaths.build(grid, levels = listOf(5f), epsilonM = 1.0)
        assertEquals(1, isobaths.size)
        val iso = isobaths.first()
        assertEquals(5f, iso.depthM, 0f)
        assertTrue("has at least one polyline", iso.lines.isNotEmpty())
        // Every vertex lies within the grid's actual covered extent (which may exceed the
        // requested bbox by up to one cell due to ceil rounding).
        val gb = grid.boundingBox
        for (line in iso.lines) for (p in line) {
            assertTrue(p.latitude in gb.latSouth..gb.latNorth)
            assertTrue(p.longitude in gb.lonWest..gb.lonEast)
        }
    }

    @Test
    fun `levels with no crossing are omitted`() {
        val bbox = BoundingBox(latSouth = 43.50, latNorth = 43.60, lonWest = 7.00, lonEast = 7.10)
        val m = MutableDepthGrid.empty("t", bbox, 2000.0, DepthDatum.LAT)
        for (r in 0 until m.rows) for (c in 0 until m.cols)
            m.set(r, c, 2f, DepthSource.EMODNET, 60)
        val grid = m.toImmutable(null, 0L, "t")
        // No cell reaches 50 m → no contour.
        assertTrue(DepthIsobaths.build(grid, levels = listOf(50f)).isEmpty())
    }
}
