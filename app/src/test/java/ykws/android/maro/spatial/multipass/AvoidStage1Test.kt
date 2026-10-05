package ykws.android.maro.spatial.multipass

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
import ykws.android.maro.spatial.Units
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

    /** The pace this class builds every cost at — the same pace the A* bounds its time with. */
    private val paceKn = 28.0

    private val paceMps = Units.knotsToMps(paceKn)

    // ── Rasterizer ────────────────────────────────────────────────────────────

    @Test
    fun anEmptyCorridorIsFreeEverywhere() {
        val grid = rasterize(box, 50.0, paceKn, 25.0, emptyList(), emptyList(), box.latNorth)

        for (r in 0 until grid.rows) {
            for (c in 0 until grid.cols) {
                val cell = grid.cell(r, c)
                assertTrue(cell.passable)
                assertEquals(
                    "a free cell costs one cell of water at the pace, in seconds",
                    baseCostSec(50.0, paceKn),
                    cell.sourceCostSec,
                    1e-9
                )
            }
        }
    }

    @Test
    fun aCcwRingFillsItsInteriorLandAndLeavesTheExteriorFree() {
        val ring = circleRing(LatLng(43.510, 7.010), radiusM = 300.0)
        val grid = rasterize(box, 50.0, paceKn, 25.0, ring, emptyList(), box.latNorth)

        val centre = grid.cellOf(43.510, 7.010)
        assertFalse("the ring's interior is land", grid.cell(centre.row, centre.col).passable)

        val outside = grid.cellOf(43.5005, 7.0005)
        assertTrue("the open water outside the ring stays free", grid.cell(outside.row, outside.col).passable)
    }

    @Test
    fun aCwBasinKeepsItsInteriorWater() {
        val basin = circleRing(LatLng(43.510, 7.010), radiusM = 300.0, orientation = LandRingOrientation.CW_BASIN)
        val grid = rasterize(box, 50.0, paceKn, 25.0, basin, emptyList(), box.latNorth)

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
        val grid = rasterize(box, 50.0, paceKn, 25.0, emptyList(), listOf(coast), box.latNorth)

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
        val grid = rasterize(box, 50.0, paceKn, 25.0, west + east, emptyList(), box.latNorth)

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
        val grid = rasterize(wide, 50.0, paceKn, 25.0, emptyList(), listOf(coast), wide.latNorth)

        assertFalse("the open coast's wide interior is sealed", passable(grid, 43.50, 7.03))
        assertTrue("the water south of the tip stays free", passable(grid, 43.485, 7.03))

        val start = grid.cellOf(43.50, 7.01)
        val aim = grid.cellOf(43.50, 7.05)
        for (path in listOf(
            MultipassSearch.search(grid, start, aim, paceMps).path,
            MultipassSearch.search(grid, aim, start, paceMps).path
        )) {
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
        val grid = rasterize(box, 50.0, paceKn, 25.0, emptyList(), emptyList(), box.latNorth)
        val path = MultipassSearch.search(grid, CellIndex(0, 0), CellIndex(grid.rows - 1, grid.cols - 1), paceMps).path

        assertTrue("a free grid always has a path", path != null)
        assertEquals(CellIndex(0, 0), path!!.first())
        assertEquals(CellIndex(grid.rows - 1, grid.cols - 1), path.last())
        for (cell in path) {
            assertTrue("every cell on the path is passable", grid.cell(cell.row, cell.col).passable)
        }
    }

    @Test
    fun aLandWallAcrossTheCorridorAnswersNull() = runTest {
        val grid = rasterize(box, 50.0, paceKn, 25.0, emptyList(), emptyList(), box.latNorth)
        for (r in 0 until grid.rows) grid.markLand(r, grid.cols / 2)

        assertNull(MultipassSearch.search(grid, CellIndex(0, 0), CellIndex(grid.rows - 1, grid.cols - 1), paceMps).path)
    }

    /**
     * **The exhaustion reading names the water the search walked.** A wall across the corridor leaves
     * the cells beyond it unvisited, so the aim's own cell is never closed and the two counts say how
     * far the search got against how much water there was — the reading that separates an aim the grid
     * barred from a way round lying outside the corridor.
     */
    @Test
    fun theExhaustionReadingNamesTheWaterItWalked() = runTest {
        val grid = rasterize(box, 50.0, paceKn, 25.0, emptyList(), emptyList(), box.latNorth)
        for (r in 0 until grid.rows) grid.markLand(r, grid.cols / 2)
        val aim = CellIndex(grid.rows - 1, grid.cols - 1)

        val outcome = MultipassSearch.search(grid, CellIndex(0, 0), aim, paceMps)

        assertNull("the wall closes the corridor", outcome.path)
        assertFalse("and the aim's own cell was never closed", outcome.aimClosed)
        assertTrue("the search did expand cells before it exhausted", outcome.expansions > 0)
        assertEquals(
            "and it counted every passable cell, the wall's column alone excluded",
            grid.rows * (grid.cols - 1),
            outcome.passableCells
        )
        assertTrue(
            "the aim's own cell is open water — the wall across the corridor is what exhausted the search",
            grid.cell(aim.row, aim.col).passable
        )
    }

    /** Its control: a found path reports the aim closed and the whole grid's water as its own count. */
    @Test
    fun aFoundPathReportsTheAimClosedAndTheWaterCounted() = runTest {
        val grid = rasterize(box, 50.0, paceKn, 25.0, emptyList(), emptyList(), box.latNorth)

        val outcome =
            MultipassSearch.search(grid, CellIndex(0, 0), CellIndex(grid.rows - 1, grid.cols - 1), paceMps)

        assertEquals("the free grid is passable whole", grid.rows * grid.cols, outcome.passableCells)
        assertTrue("the path reaches the aim's own cell", outcome.aimClosed)
        assertTrue("and the reading carries the path itself", outcome.path != null)
    }

    @Test
    fun theSearchIsDeterministic() = runTest {
        val grid = rasterize(
            box, 50.0, paceKn, 25.0, circleRing(LatLng(43.510, 7.010), radiusM = 300.0), emptyList(), box.latNorth
        )
        val start = CellIndex(0, 0)
        val aim = CellIndex(grid.rows - 1, grid.cols - 1)

        val first = MultipassSearch.search(grid, start, aim, paceMps)
        val second = MultipassSearch.search(grid, start, aim, paceMps)

        assertEquals("the same grid yields the same path", first, second)
    }

    @Test
    fun theSearchHonoursCancellation() = runTest {
        val wide = BBox(43.50, 43.62, 7.00, 7.14)
        val grid = rasterize(wide, 50.0, paceKn, 25.0, emptyList(), emptyList(), wide.latNorth)
        var checks = 0

        val outcome = runCatching {
            MultipassSearch.search(grid, CellIndex(0, 0), CellIndex(grid.rows - 1, grid.cols - 1), paceMps) {
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
        val openWater = RouteCostField.ofHard { Double.MAX_VALUE }

        val waypoints = MultipassPull.pull(
            path, start, aim, 25.0, MultipassPull.clearanceStep(25.0),
            MultipassPull.clearanceStep(25.0), openWater
        )

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
        val field = RouteCostField.ofHard { p -> SpatialOperations.haversine(p, obstacle) }

        val waypoints = MultipassPull.pull(
            path, start, aim, margin, MultipassPull.clearanceStep(margin),
            MultipassPull.clearanceStep(margin), field
        )

        assertEquals("the bulge is kept because the straight chord grazes the obstacle", listOf(start, bulge, aim), waypoints)
        for (waypoint in waypoints) {
            assertTrue(
                "every waypoint keeps the clearance",
                SpatialOperations.haversine(waypoint, obstacle) >= margin - 1e-6
            )
        }
    }

    /**
     * **The carve's exemption (F7), read by the pull.** A chord standing on the berth's own stretch is
     * not refused by the margin the approach waives — a berth longer than the margin is exactly what
     * the end disc cannot cover — while the same chord standing off that stretch still is, and the
     * tally names the margin as the cause.
     *
     * The stretch is the one a carve would return: from the end's own point out to the channel's mouth.
     */
    @Test
    fun aChordOnTheCarvedApproachStandsAndOneOffItDoesNot() {
        val mPerDegLat = SpatialOperations.EARTH_RADIUS_M * PI / 180.0
        val coastLat = 43.5000
        val channelLat = coastLat - 10.0 / mPerDegLat
        // Two walls twenty metres apart whose east ends are the channel's mouth at lon 7.00; east of it
        // the hard distance is open water's.
        val northWall = listOf(LatLng(coastLat, 6.99), LatLng(coastLat, 7.00))
        val southWall = listOf(LatLng(coastLat - 20.0 / mPerDegLat, 6.99), LatLng(coastLat - 20.0 / mPerDegLat, 7.00))
        val margin = 25.0
        val field = RouteCostField.ofHard { p ->
            minOf(
                SpatialOperations.pointToSegmentDistance(p, northWall[0], northWall[1]),
                SpatialOperations.pointToSegmentDistance(p, southWall[0], southWall[1])
            )
        }
        val start = LatLng(channelLat, 6.995)
        val mid = LatLng(channelLat, 6.998)
        val aim = LatLng(channelLat, 7.02)
        val stretch = listOf(start, LatLng(channelLat, 7.0006))
        val tally = PullRefusals()

        val onStretch = MultipassPull.pull(
            listOf(start, mid, aim), start, aim, margin, MultipassPull.clearanceStep(margin),
            MultipassPull.clearanceStep(margin), field,
            approaches = EndApproaches(start = stretch), refusals = tally
        )

        assertEquals("the chord on the carved approach stands", listOf(start, aim), onStretch)
        assertEquals("and nothing was refused by the land margin", 0, tally.land)
        assertEquals("nor by the price", 0, tally.price)

        // The same stretch, twenty metres to the north of the chord: outside the exemption's own width.
        val shifted = stretch.map { LatLng(it.latitude + 20.0 / mPerDegLat, it.longitude) }
        val refused = PullRefusals()
        val offStretch = MultipassPull.pull(
            listOf(start, mid, aim), start, aim, margin, MultipassPull.clearanceStep(margin),
            MultipassPull.clearanceStep(margin), field,
            approaches = EndApproaches(start = shifted), refusals = refused
        )

        assertEquals(
            "a chord off the stretch is refused and the staircase is kept",
            listOf(start, mid, aim),
            offStretch
        )
        assertTrue("and the tally names the land margin", refused.land > 0)
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
        val field = RouteCostField.ofHard { p -> SpatialOperations.haversine(p, obstacle) }

        val waypoints = MultipassPull.pull(
            listOf(start, mid, aim), start, aim, margin, MultipassPull.clearanceStep(margin),
            MultipassPull.clearanceStep(margin), field
        )

        assertEquals("the chord is rejected and the midpoint kept", listOf(start, mid, aim), waypoints)
    }

    // ── Geometry helpers ───────────────────────────────────────────────────────

    private fun circleRing(
        center: LatLng,
        radiusM: Double,
        n: Int = 32,
        orientation: LandRingOrientation = LandRingOrientation.CCW_RING
    ): List<MultipassEdge> {
        val mPerDegLat = SpatialOperations.EARTH_RADIUS_M * PI / 180.0
        val mPerDegLon = mPerDegLat * cos(Math.toRadians(center.latitude))
        val polygon = (0 until n).map { i ->
            val theta = 2.0 * PI * i / n
            LatLng(
                center.latitude + radiusM * sin(theta) / mPerDegLat,
                center.longitude + radiusM * cos(theta) / mPerDegLon
            )
        }
        return polygon.zipWithNext().map { (a, b) -> MultipassEdge(a, b, orientation) } +
            MultipassEdge(polygon.last(), polygon.first(), orientation)
    }

    private fun rectangleRing(southWest: LatLng, northEast: LatLng): List<MultipassEdge> {
        val southEast = LatLng(southWest.latitude, northEast.longitude)
        val northWest = LatLng(northEast.latitude, southWest.longitude)
        val points = listOf(southWest, southEast, northEast, northWest)
        return points.zipWithNext().map { (a, b) -> MultipassEdge(a, b, LandRingOrientation.CCW_RING) } +
            MultipassEdge(northWest, southWest, LandRingOrientation.CCW_RING)
    }

    private fun passable(grid: MultipassGrid, lat: Double, lon: Double): Boolean {
        val c = grid.cellOf(lat, lon)
        return grid.cell(c.row, c.col).passable
    }
}
