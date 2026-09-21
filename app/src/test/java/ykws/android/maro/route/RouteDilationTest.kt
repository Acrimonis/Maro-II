package ykws.android.maro.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.BBox

/**
 * The exclusion set and its standoff — plan §10.2 and §11.2.
 *
 * Two things are asserted here and they are the two ways the design says a standoff behaves: on the
 * exclusion geometry it is a **hard dilation** of the obstacle, and when D3 says it is priced instead
 * the raster is left exactly as the depth gate wrote it.
 */
class RouteDilationTest {

    private val gridBox = BBox(latSouth = 43.50, latNorth = 43.52, lonWest = 7.00, lonEast = 7.02)

    /** A north–south wall of 1 m water across the region, with a wide gap around 43.5060. */
    private fun wallAssets() = RouteHarness.syntheticAssets(
        gridBox,
        depthAt = { lat, lon ->
            if (lon >= 7.0095 && lon <= 7.0105 && lat !in 43.5050..43.5090) 1.0f else 20f
        },
    )

    private fun exclusion(config: RouteConfig = RouteConfig()): RouteExclusion {
        val assets = wallAssets()
        return RouteExclusion.build(assets.grid, assets.coast, config, gridBox)
    }

    @Test
    fun `the raw gate blocks shallow water and leaves deep water free`() {
        val ex = exclusion()
        val insideWall = LatLng(43.515, 7.0100)
        assertTrue("1 m water blocks", ex.isBlocked(insideWall.latitude, insideWall.longitude))

        val deepWater = LatLng(43.515, 7.0050)
        assertFalse("20 m water passes", ex.isBlocked(deepWater.latitude, deepWater.longitude))

        val inGap = LatLng(43.5070, 7.0100)
        assertFalse("the gap is navigable", ex.isBlocked(inGap.latitude, inGap.longitude))
    }

    @Test
    fun `the hard standoff dilates the excluded set by one cell at the shipped 25 m`() {
        val ex = exclusion()
        assertEquals("25 m of a 25 m cell is one cell", 1, ex.standoffCells)

        // Find the westernmost blocked cell of the wall on a row where the wall is solid.
        val row = (0 until ex.rows).first { i ->
            ex.isBlocked(ex.latOf(i), 7.0100)
        }
        val lat = ex.latOf(row)
        val edgeLon = (0 until ex.cols).map { ex.lonOf(it) }.first { lon ->
            ex.isBlocked(lat, lon) && !ex.isBlocked(lat, lon - ex.grid.cellSizeDegLon)
        }

        // Offsets are stated in cells of the column axis, because that is the grid the mask was built
        // on: three quarters of a cell west of the westernmost blocked centre is the next cell out —
        // free of the raw gate, one cell out and therefore inside the dilation. Two and a half cells
        // out clears it.
        val cellLon = ex.grid.cellSizeDegLon
        val justWest = LatLng(lat, edgeLon - cellLon * 0.75)
        assertFalse("the next cell out is free of the gate", ex.isBlocked(justWest.latitude, justWest.longitude))
        assertTrue("…but inside the standoff", ex.isForbidden(justWest.latitude, justWest.longitude))

        val clearOf = LatLng(lat, edgeLon - cellLon * 2.5)
        assertFalse("two and a half cells out is free", ex.isForbidden(clearOf.latitude, clearOf.longitude))
    }

    @Test
    fun `a priced standoff leaves the raster undilated - the two variants of the acceptance run`() {
        val priced = exclusion(RouteConfig(hardStandoff = false))
        assertEquals(0, priced.standoffCells)
        assertEquals(1.0, priced.standoffRatio(43.515, 7.0090), 1e-9)

        val somewhere = LatLng(43.515, 7.0100)
        assertEquals(
            priced.isBlocked(somewhere.latitude, somewhere.longitude),
            priced.isForbidden(somewhere.latitude, somewhere.longitude),
        )
    }

    @Test
    fun `outside the window the verdict is fail-closed`() {
        val ex = exclusion()
        val offGrid = LatLng(43.4995, 7.0100)
        assertTrue("a point south of the grid is not open water", ex.isForbidden(offGrid.latitude, offGrid.longitude))
        assertFalse(ex.isInside(offGrid.latitude, offGrid.longitude))
        assertTrue(ex.isInside(43.510, 7.0100))
    }

    @Test
    fun `the contour carries the standoff and its vertices clear the blocked set`() {
        val ex = exclusion()
        assertTrue("the wall must produce a boundary", ex.segments.isNotEmpty())
        assertEquals("the tolerance stays tied to the standoff", 6.25, ex.epsilonM, 1e-9)

        // Every contour vertex sits on the dilated boundary, so its own clearance is at least the
        // standoff minus the cell it was rounded to — never inside the raw blocked set.
        for (vertex in ex.vertices) {
            assertFalse(
                "vertex ${vertex.latitude},${vertex.longitude} is inside the gate",
                ex.isBlocked(vertex.latitude, vertex.longitude),
            )
        }
    }
}
