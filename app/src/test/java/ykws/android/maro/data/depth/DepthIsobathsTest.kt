package ykws.android.maro.data.depth

import org.junit.Assert.*
import org.junit.Test
import ykws.android.maro.data.model.BoundingBox
import ykws.android.maro.data.model.DepthDatum
import ykws.android.maro.data.model.DepthGrid
import ykws.android.maro.data.model.DepthSource
import ykws.android.maro.data.model.MutableDepthGrid

class DepthIsobathsTest {

    /** Square basin: [src]/[conf] everywhere, a deeper [centreDepthM] block in the middle. */
    private fun basin(src: DepthSource, conf: Int, centreDepthM: Float): DepthGrid {
        val bbox = BoundingBox(latSouth = 43.50, latNorth = 43.60, lonWest = 7.00, lonEast = 7.10)
        val m = MutableDepthGrid.empty("t", bbox, 2000.0, DepthDatum.LAT)
        for (r in 0 until m.rows) for (c in 0 until m.cols) m.set(r, c, 2f, src, conf)
        for (r in 1 until m.rows - 1) for (c in 1 until m.cols - 1) m.set(r, c, centreDepthM, src, conf)
        return m.toImmutable(null, 0L, "t")
    }

    @Test
    fun `builds an isobath around a central basin and maps it inside the bbox`() {
        // Fine (5 m) contour over fine Litto3D data → drawn, tagged Litto3D.
        val grid = basin(DepthSource.LITTO3D, 90, centreDepthM = 10f)
        val isobaths = DepthIsobaths.build(grid, levels = listOf(5f), epsilonM = 1.0)
        assertEquals(1, isobaths.size)
        val iso = isobaths.first()
        assertEquals(5f, iso.depthM, 0f)
        assertTrue("has at least one polyline", iso.lines.isNotEmpty())
        assertEquals(DepthSource.LITTO3D, iso.lines.first().source)
        // Every vertex lies within the grid's actual covered extent (which may exceed the
        // requested bbox by up to one cell due to ceil rounding).
        val gb = grid.boundingBox
        for (line in iso.lines) for (p in line.points) {
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

    @Test
    fun `fine contours need fine data, coarse contours keep coarse data`() {
        val emod = basin(DepthSource.EMODNET, 60, centreDepthM = 20f)   // coarse 115 m source
        val litto = basin(DepthSource.LITTO3D, 90, centreDepthM = 20f)  // fine 1 m source

        // Fine 5 m level: suppressed over coarse EMODnet, drawn (tagged Litto3D) over fine Litto3D.
        assertTrue(
            "fine contour must be suppressed over coarse EMODnet",
            DepthIsobaths.build(emod, levels = listOf(5f), epsilonM = 1.0).isEmpty()
        )
        val fineLitto = DepthIsobaths.build(litto, levels = listOf(5f), epsilonM = 1.0)
        assertEquals(1, fineLitto.size)
        assertEquals(DepthSource.LITTO3D, fineLitto.first().lines.first().source)

        // Coarse 15 m level: kept even on EMODnet, tagged EMODNET (legitimate at depth).
        val coarseEmod = DepthIsobaths.build(emod, levels = listOf(15f), epsilonM = 1.0)
        assertEquals(1, coarseEmod.size)
        assertEquals(DepthSource.EMODNET, coarseEmod.first().lines.first().source)
    }
}
