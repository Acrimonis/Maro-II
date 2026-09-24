package ykws.android.maro.spatial.avoid

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.spatial.LandRingOrientation
import ykws.android.maro.spatial.SpatialOperations
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The stage-1 rasterizer, A* and taut pull, pinned at the algorithm level — the pieces
 * [RouteAvoidEngine] assembles. The rasterizer tests use synthetic geometry so the margin band,
 * the ring fill and the open-coast closure each read from a controlled oracle.
 */
class AvoidStage1Test {

    private val box = BBox(43.50, 43.52, 7.00, 7.02)

    // ── Rasterizer ────────────────────────────────────────────────────────────

    @Test
    fun anEmptyCorridorIsFreeEverywhere() {
        val grid = rasterize(box, 50.0, 25.0, emptyList(), emptyList(), box.latNorth)

        for (r in 0 until grid.rows) {
            for (c in 0 until grid.cols) {
                val cell = grid.cell(r, c)
                assertTrue(cell.passable)
                assertEquals(grid.cellM, cell.sourceCostM, 1e-9)
            }
        }
    }

    @Test
    fun aCcwRingFillsItsInteriorLandAndLeavesTheExteriorFree() {
        val ring = circleRing(LatLng(43.510, 7.010), radiusM = 300.0)
        val grid = rasterize(box, 50.0, 25.0, ring, emptyList(), box.latNorth)

        val centre = grid.cellOf(43.510, 7.010)
        assertFalse("the ring's interior is land", grid.cell(centre.row, centre.col).passable)

        val outside = grid.cellOf(43.5005, 7.0005)
        assertTrue("the open water outside the ring stays free", grid.cell(outside.row, outside.col).passable)
    }

    @Test
    fun aCwBasinKeepsItsInteriorWater() {
        val basin = circleRing(LatLng(43.510, 7.010), radiusM = 300.0, orientation = LandRingOrientation.CW_BASIN)
        val grid = rasterize(box, 50.0, 25.0, basin, emptyList(), box.latNorth)

        val centre = grid.cellOf(43.510, 7.010)
        assertTrue("a CW basin's interior stays water", grid.cell(centre.row, centre.col).passable)
    }

    @Test
    fun theOpenCoastLandSideIsClosedAndTheWaterSideStaysOpen() {
        val mPerDegLat = SpatialOperations.EARTH_RADIUS_M * PI / 180.0
        val coast = listOf(
            LatLng(43.51, 7.00),
            LatLng(43.51, 7.02)
        )
        val grid = rasterize(box, 50.0, 25.0, emptyList(), listOf(coast), box.latNorth)

        // A cell centred ~35 m north of the coast — the land side, sealed by the cap.
        val land = grid.cellOf(43.51 + 35.0 / mPerDegLat, 7.01)
        assertFalse("the land side of the open coast is sealed", grid.cell(land.row, land.col).passable)

        // A cell centred ~35 m south of the coast — beyond the margin, open water.
        val water = grid.cellOf(43.51 - 35.0 / mPerDegLat, 7.01)
        assertTrue("the water side of the open coast stays free", grid.cell(water.row, water.col).passable)
    }

    @Test
    fun twoTouchingHazardRingsReadAsOneBlockedMass() {
        val west = rectangleRing(LatLng(43.505, 7.005), LatLng(43.515, 7.010))
        val east = rectangleRing(LatLng(43.505, 7.010), LatLng(43.515, 7.015))
        val grid = rasterize(box, 50.0, 25.0, west + east, emptyList(), box.latNorth)

        assertFalse("the west ring's interior is land", passable(grid, 43.510, 7.0075))
        assertFalse("the shared edge is land — one continuous mass", passable(grid, 43.510, 7.010))
        assertFalse("the east ring's interior is land", passable(grid, 43.510, 7.0125))
        assertTrue("the water west of the mass stays free", passable(grid, 43.510, 7.0005))
        assertTrue("the water east of the mass stays free", passable(grid, 43.510, 7.0195))
    }

    /** The fix: a U-shaped open coast whose land interior is wider than the margin must be sealed, so the route rounds the tip — both directions. */
    @Test
    fun aUshapedOpenCoastFillsItsWideInteriorAndTheRouteRoundsTheTipBothWays() = runTest {
        val coast = listOf(
            LatLng(43.51, 7.00),
            LatLng(43.51, 7.02),
            LatLng(43.49, 7.02),
            LatLng(43.49, 7.04),
            LatLng(43.51, 7.04),
            LatLng(43.51, 7.06)
        )
        val wide = BBox(43.47, 43.53, 6.99, 7.07)
        val grid = rasterize(wide, 50.0, 25.0, emptyList(), listOf(coast), wide.latNorth)

        assertFalse("the open coast's wide interior is sealed", passable(grid, 43.50, 7.03))
        assertTrue("the water south of the tip stays free", passable(grid, 43.485, 7.03))

        val start = grid.cellOf(43.50, 7.01)
        val aim = grid.cellOf(43.50, 7.05)
        for (path in listOf(AvoidSearch.search(grid, start, aim), AvoidSearch.search(grid, aim, start))) {
            assertTrue("a route around the tip exists", path != null)
            assertTrue(
                "the route rounds the tip rather than cutting across",
                path!!.any { grid.center(it.row, it.col).latitude < 43.49 }
            )
            for (cell in path) {
                val centre = grid.center(cell.row, cell.col)
                val insidePeninsula = centre.latitude in 43.49..43.51 && centre.longitude in 7.02..7.04
                assertFalse("the route never crosses the peninsula interior", insidePeninsula)
            }
        }
    }

    // ── A* ─────────────────────────────────────────────────────────────────────

    @Test
    fun theSearchFindsAPathAcrossFreeWater() = runTest {
        val grid = rasterize(box, 50.0, 25.0, emptyList(), emptyList(), box.latNorth)
        val path = AvoidSearch.search(grid, CellIndex(0, 0), CellIndex(grid.rows - 1, grid.cols - 1))

        assertTrue("a free grid always has a path", path != null)
        assertEquals(CellIndex(0, 0), path!!.first())
        assertEquals(CellIndex(grid.rows - 1, grid.cols - 1), path.last())
        for (cell in path) {
            assertTrue("every cell on the path is passable", grid.cell(cell.row, cell.col).passable)
        }
    }

    @Test
    fun aLandWallAcrossTheCorridorAnswersNull() = runTest {
        val grid = rasterize(box, 50.0, 25.0, emptyList(), emptyList(), box.latNorth)
        for (r in 0 until grid.rows) grid.markLand(r, grid.cols / 2)

        assertNull(AvoidSearch.search(grid, CellIndex(0, 0), CellIndex(grid.rows - 1, grid.cols - 1)))
    }

    @Test
    fun theSearchIsDeterministic() = runTest {
        val grid = rasterize(box, 50.0, 25.0, circleRing(LatLng(43.510, 7.010), radiusM = 300.0), emptyList(), box.latNorth)
        val start = CellIndex(0, 0)
        val aim = CellIndex(grid.rows - 1, grid.cols - 1)

        val first = AvoidSearch.search(grid, start, aim)
        val second = AvoidSearch.search(grid, start, aim)

        assertEquals("the same grid yields the same path", first, second)
    }

    @Test
    fun theSearchHonoursCancellation() = runTest {
        val wide = BBox(43.50, 43.62, 7.00, 7.14)
        val grid = rasterize(wide, 50.0, 25.0, emptyList(), emptyList(), wide.latNorth)
        var checks = 0

        val outcome = runCatching {
            AvoidSearch.search(grid, CellIndex(0, 0), CellIndex(grid.rows - 1, grid.cols - 1)) {
                checks++
                throw CancellationException("abandoned drag")
            }
        }

        assertTrue("the cadence hook ran inside the search", checks > 0)
        assertTrue("its cancellation aborts the search", outcome.exceptionOrNull() is CancellationException)
    }

    // ── Taut pull ──────────────────────────────────────────────────────────────

    @Test
    fun thePullCollapsesAClearPathToItsEnds() {
        val start = LatLng(43.50, 7.00)
        val aim = LatLng(43.50, 7.02)
        val path = listOf(start, LatLng(43.505, 7.005), LatLng(43.503, 7.012), aim)

        val waypoints = AvoidPull.pull(path, start, aim, 25.0) { Double.MAX_VALUE }

        assertEquals(listOf(start, aim), waypoints)
    }

    @Test
    fun thePullKeepsTheMarginAroundAnObstacle() {
        val start = LatLng(43.50, 7.00)
        val aim = LatLng(43.50, 7.02)
        val obstacle = LatLng(43.50, 7.01)
        val bulge = LatLng(43.505, 7.01)
        val path = listOf(start, bulge, aim)
        val margin = 25.0

        val waypoints = AvoidPull.pull(path, start, aim, margin) { p ->
            SpatialOperations.haversine(p, obstacle)
        }

        assertEquals("the bulge is kept because the straight chord grazes the obstacle", listOf(start, bulge, aim), waypoints)
        for (waypoint in waypoints) {
            assertTrue(
                "every waypoint keeps the clearance",
                SpatialOperations.haversine(waypoint, obstacle) >= margin - 1e-6
            )
        }
    }

    /** The pull samples at `marginM / 2`, so a dip a coarser step would miss is still caught. */
    @Test
    fun thePullSamplesAtMarginOverTwoSoADipIsNotMissed() {
        val start = LatLng(43.50, 7.00)
        val aim = LatLng(43.50, 7.02)
        val mid = LatLng(43.50, 7.01)
        val margin = 25.0
        val mPerDegLat = SpatialOperations.EARTH_RADIUS_M * PI / 180.0
        // The obstacle sits 22.5 m off the straight chord, at its midpoint: inside the margin, but
        // between two samples of a margin-sized step (which would miss it) and on a sample of the
        // margin/2 step (which must catch it and keep the midpoint).
        val obstacle = LatLng(43.50 - 22.5 / mPerDegLat, 7.01)

        val waypoints = AvoidPull.pull(listOf(start, mid, aim), start, aim, margin) { p ->
            SpatialOperations.haversine(p, obstacle)
        }

        assertEquals("the chord is rejected and the midpoint kept", listOf(start, mid, aim), waypoints)
    }

    // ── Geometry helpers ───────────────────────────────────────────────────────

    private fun circleRing(
        center: LatLng,
        radiusM: Double,
        n: Int = 32,
        orientation: LandRingOrientation = LandRingOrientation.CCW_RING
    ): List<AvoidEdge> {
        val mPerDegLat = SpatialOperations.EARTH_RADIUS_M * PI / 180.0
        val mPerDegLon = mPerDegLat * cos(Math.toRadians(center.latitude))
        val polygon = (0 until n).map { i ->
            val theta = 2.0 * PI * i / n
            LatLng(
                center.latitude + radiusM * sin(theta) / mPerDegLat,
                center.longitude + radiusM * cos(theta) / mPerDegLon
            )
        }
        return polygon.zipWithNext().map { (a, b) -> AvoidEdge(a, b, orientation) } +
            AvoidEdge(polygon.last(), polygon.first(), orientation)
    }

    private fun rectangleRing(southWest: LatLng, northEast: LatLng): List<AvoidEdge> {
        val southEast = LatLng(southWest.latitude, northEast.longitude)
        val northWest = LatLng(northEast.latitude, southWest.longitude)
        val points = listOf(southWest, southEast, northEast, northWest)
        return points.zipWithNext().map { (a, b) -> AvoidEdge(a, b, LandRingOrientation.CCW_RING) } +
            AvoidEdge(northWest, southWest, LandRingOrientation.CCW_RING)
    }

    private fun passable(grid: AvoidGrid, lat: Double, lon: Double): Boolean {
        val c = grid.cellOf(lat, lon)
        return grid.cell(c.row, c.col).passable
    }
}
