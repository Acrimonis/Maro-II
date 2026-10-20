package ykws.android.maro.spatial.multipass

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.DepthGrid
import ykws.android.maro.spatial.Units
import ykws.android.maro.spatial.multipass.GateTestFixtures.at
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * **The depth gate's wall on the grid and in the search, measured.** The raster paints the wall from the
 * traced 3 m line — so a rock no cell centre stands on still walls the water around it — and writes each
 * near cell's distance exactly; the search's step guard keeps every raw leg off the wall; and a passage
 * of 50 m holds a legal line wherever the grid happens to fall on it, straight or diagonal.
 */
class GateRasterTest {

    private val paceKn = 25.0
    private val fineCellM = 20.0
    private val floorM = 10.0

    /** The La Grenille shape: one 2.2 m node at (110 m, 110 m) among 5 m nodes, on a 20 m raster. */
    private fun rock(): DepthGrid = GateTestFixtures.raster(11, 11, 20.0) { n, e ->
        if (n == 110.0 && e == 110.0) 2.2 else 5.0
    }

    private fun gridOver(raster: DepthGrid, box: ykws.android.maro.data.model.markers.BBox, gate: GateRule): MultipassGrid {
        val world = GateTestFixtures.RasterWorld(raster)
        return rasterize(
            box, fineCellM, paceKn, 0.0, emptyList(), emptyList(), box.latNorth,
            field = GateTestFixtures.gateField(world), gate = gate
        )
    }

    @Test
    fun aRockNoCellCentreStandsOnStillWallsTheWaterAroundIt() {
        val raster = rock()
        val gate = GateTestFixtures.gateOver(raster, hardM = floorM)
        // The grid's lines pass through the node, so the node stands at the corner of four cells.
        val grid = gridOver(raster, GateTestFixtures.box(50.0, 170.0, 50.0, 170.0), gate)
        for (r in 2..3) {
            for (c in 2..3) {
                assertFalse("the cell centre ($r, $c) is not itself shallow", gate.isShallow(grid.center(r, c)))
                assertFalse("but the measured wall paints it land", grid.passable(r, c))
            }
        }
        assertTrue("water 60 m off stays open", grid.passable(0, 0))
        // The control: the cell-centre gate alone — what the grid read before the edge was measured —
        // sees no shallow centre there and leaves all four cells open.
        val world = GateTestFixtures.RasterWorld(raster)
        val box = GateTestFixtures.box(50.0, 170.0, 50.0, 170.0)
        val blind = rasterize(
            box, fineCellM, paceKn, 0.0, emptyList(), emptyList(), box.latNorth,
            field = GateTestFixtures.gateField(world)
        )
        for (r in 2..3) for (c in 2..3) assertTrue("a centre-sampled grid misses the rock at ($r, $c)", blind.passable(r, c))
    }

    @Test
    fun theStoredDistanceIsExactNearTheEdgeAndNeverAboveTheTruth() {
        // A straight shelf: shallow south of 100 m, deep north of it, on a 2 m raster.
        val raster = GateTestFixtures.raster(150, 60, 2.0) { n, _ -> if (n < 100.0) 2.0 else 10.0 }
        val gate = GateTestFixtures.gateOver(raster, hardM = floorM)
        val grid = gridOver(raster, GateTestFixtures.box(100.0, 280.0, 20.0, 100.0), gate)
        val c = grid.cols / 2
        for (r in 0 until grid.rows) {
            val centre = grid.center(r, c)
            val truth = (centre.latitude - GateTestFixtures.LAT0) * GateTestFixtures.M_PER_DEG_LAT - 100.0
            if (truth > 45.0) continue
            val stored = grid.gateDistanceM(r, c)
            assertTrue("never above the truth at ${"%.1f".format(truth)} m (stored $stored)", stored <= truth + 0.01)
            assertTrue("and within the trace's error of it (stored $stored)", stored >= truth - 2.0 * gate.padM - 2.0)
            if (truth < floorM - 0.01) assertFalse("a cell inside the wall is land", grid.passable(r, c))
            if (truth > floorM + 2.0 * gate.padM + 2.0) assertTrue("a cell clear of it is open", grid.passable(r, c))
        }
    }

    @Test
    fun everyStepOfAGuardedPathKeepsTheWall() = runTest {
        val raster = rock()
        val gate = GateTestFixtures.gateOver(raster, hardM = floorM)
        val box = GateTestFixtures.box(0.0, 220.0, 0.0, 220.0)
        val grid = gridOver(raster, box, gate)
        val start = at(100.0, 10.0)
        val aim = at(120.0, 210.0)
        val path = MultipassSearch.search(
            grid, grid.cellOf(start.latitude, start.longitude), grid.cellOf(aim.latitude, aim.longitude),
            Units.knotsToMps(paceKn),
            stepGuard = StepGuard(gate, start, aim)
        ).path
        assertNotNull("the open water round the rock is crossed", path)
        val centres = path!!.map { grid.center(it.row, it.col) }
        for (k in 0 until centres.size - 1) {
            val clear = gate.segmentEdgeM(centres[k], centres[k + 1], floorM)
            assertTrue("step $k keeps the wall (read $clear)", clear >= floorM - 1e-6)
        }
    }

    /**
     * **A 50 m passage is always open** — two shallow banks 50 m apart, the grid laid at four offsets
     * across them: the search finds a legal line through every one.
     */
    @Test
    fun aFiftyMetrePassageIsOpenAtEveryOffset() = runTest {
        val widthM = 50.0
        for (shiftM in listOf(0.0, 5.0, 10.0, 15.0)) {
            val midN = 200.0 + shiftM
            val raster = GateTestFixtures.raster(200, 200, 2.0) { n, _ -> if (abs(n - midN) < widthM / 2.0) 10.0 else 2.0 }
            val gate = GateTestFixtures.gateOver(raster, hardM = floorM)
            val grid = gridOver(raster, GateTestFixtures.box(100.0, 300.0, 0.0, 400.0), gate)
            val start = at(midN, 15.0)
            val aim = at(midN, 385.0)
            val path = MultipassSearch.search(
                grid, grid.cellOf(start.latitude, start.longitude), grid.cellOf(aim.latitude, aim.longitude),
                Units.knotsToMps(paceKn),
                stepGuard = StepGuard(gate, start, aim)
            ).path
            assertNotNull("the 50 m passage holds a legal line at offset $shiftM m", path)
        }
    }

    @Test
    fun aFiftyMetreDiagonalPassageIsOpen() = runTest {
        val widthM = 50.0
        val half = sqrt(2.0) / 2.0
        for (shiftM in listOf(0.0, 7.0)) {
            // The passage's axis runs north-east through (200 + shift, 200); u is the distance across it.
            val raster = GateTestFixtures.raster(200, 200, 2.0) { n, e ->
                val u = ((n - 200.0 - shiftM) - (e - 200.0)) * half
                if (abs(u) < widthM / 2.0) 10.0 else 2.0
            }
            val gate = GateTestFixtures.gateOver(raster, hardM = floorM)
            val grid = gridOver(raster, GateTestFixtures.box(20.0, 380.0, 20.0, 380.0), gate)
            val start = at(80.0 + shiftM, 80.0)
            val aim = at(320.0 + shiftM, 320.0)
            val path = MultipassSearch.search(
                grid, grid.cellOf(start.latitude, start.longitude), grid.cellOf(aim.latitude, aim.longitude),
                Units.knotsToMps(paceKn),
                stepGuard = StepGuard(gate, start, aim)
            ).path
            assertNotNull("the diagonal 50 m passage holds a legal line at offset $shiftM m", path)
        }
    }
}
