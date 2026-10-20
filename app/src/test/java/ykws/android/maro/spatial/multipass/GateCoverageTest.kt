package ykws.android.maro.spatial.multipass

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.DepthGrid
import ykws.android.maro.spatial.SpatialOperations
import ykws.android.maro.spatial.Units
import ykws.android.maro.spatial.multipass.GateTestFixtures.at
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * **The gate's remaining guarantees, each pinned on its own** — a wall marker's body keeps its standoff and
 * its berth, the step guard's trust and its reads, a passage at an awkward angle, the price walk inside the
 * steep ramp, and an end the shallows enclose.
 */
class GateCoverageTest {

    private val floorM = 10.0

    private fun deep(): DepthGrid = GateTestFixtures.raster(150, 150, 2.0) { _, _ -> 10.0 }

    /** A marker body keeps the centre-based standoff it always had, and the berth still reaches past it. */
    @Test
    fun aWallBodyKeepsItsStandoffAndItsBerth() {
        val raster = deep()
        val world = GateTestFixtures.RasterWorld(raster)
        val gate = GateTestFixtures.gateOver(raster, hardM = floorM)
        val body = at(150.0, 150.0)
        val field = RouteCostField(
            listOf(RouteCostSource.Hard(blockedAt = { p -> SpatialOperations.haversine(p, body) <= 8.0 }))
        )
        // The grid's lines pass 10 m off the body's centre, so the body is the one cell it stands in.
        val box = GateTestFixtures.box(40.0, 260.0, 40.0, 260.0)
        val grid = rasterize(
            box, 20.0, 25.0, 0.0, emptyList(), emptyList(), box.latNorth,
            field = field, gate = gate
        )
        val bodyCell = grid.cellOf(body.latitude, body.longitude)
        assertFalse("the body is a wall", grid.passable(bodyCell.row, bodyCell.col))
        for ((dr, dc) in listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) {
            assertFalse("the body's standoff ring is land", grid.passable(bodyCell.row + dr, bodyCell.col + dc))
        }
        val beyond = grid.gateDistanceM(bodyCell.row + 2, bodyCell.col)
        assertTrue("the water past the ring still carries its berth distance (read $beyond)", beyond <= 40.0 + 1e-9)
        assertTrue("and it is open", grid.passable(bodyCell.row + 2, bodyCell.col))
    }

    /** The guard trusts what the raster wrote, reads where it must, waives the ends and refuses a graze. */
    @Test
    fun theStepGuardTrustsReadsWaivesAndRefuses() {
        // A shelf: shallow south of 100 m.
        val raster = GateTestFixtures.raster(150, 150, 2.0) { n, _ -> if (n < 100.0) 2.0 else 10.0 }
        val gate = GateTestFixtures.gateOver(raster, hardM = floorM)
        val box = GateTestFixtures.box(100.0, 200.0, 0.0, 200.0)
        val grid = MultipassGrid(box.latSouth, box.lonWest, 20.0 / GateTestFixtures.M_PER_DEG_LAT,
            20.0 / GateTestFixtures.M_PER_DEG_LON, rows = 5, cols = 10, cellM = 20.0, baseCostSec = 1.0)
        val walk = WalkWindows.of(grid)
        val far = StepGuard(gate, at(400.0, 0.0), at(400.0, 300.0))
        // Row 0's centres stand 10 m off the edge. Stored distances that prove the step are trusted outright,
        // even here, where they are deliberately wrong: the guard reads nothing it can prove.
        grid.writeGateDistanceM(0, 3, 100.0)
        grid.writeGateDistanceM(0, 4, 100.0)
        assertTrue("a proved step reads nothing", far.clear(walk, walk.slotOf(0, 3), walk.slotOf(0, 4), 20.0))
        // Unproved, the same step is read, and 10 m off the edge less the pad is inside the wall.
        grid.writeGateDistanceM(0, 5, 5.0)
        grid.writeGateDistanceM(0, 6, 5.0)
        assertFalse("an unproved graze is refused", far.clear(walk, walk.slotOf(0, 5), walk.slotOf(0, 6), 20.0))
        // Row 2's centres stand 50 m off: read and cleared.
        grid.writeGateDistanceM(2, 5, 5.0)
        grid.writeGateDistanceM(2, 6, 5.0)
        assertTrue("an unproved step clear of the wall passes", far.clear(walk, walk.slotOf(2, 5), walk.slotOf(2, 6), 20.0))
        // Inside an end's own disc the gate waives itself.
        val near = StepGuard(gate, grid.center(0, 5), at(400.0, 300.0))
        assertTrue("a step inside the end's disc is waived to its rim", near.clear(walk, walk.slotOf(0, 5), walk.slotOf(0, 6), 20.0))
    }

    /** A 50 m passage at 26.6° — the angle no straight or diagonal row runs along — still holds a legal line. */
    @Test
    fun aFiftyMetrePassageAtAnAwkwardAngleIsOpen() = runTest {
        val widthM = 50.0
        val norm = sqrt(5.0)
        // The axis runs two east for one north through (200, 200); u is the distance across it.
        val raster = GateTestFixtures.raster(200, 220, 2.0) { n, e ->
            val u = (2.0 * (n - 200.0) - (e - 200.0)) / norm
            if (abs(u) < widthM / 2.0) 10.0 else 2.0
        }
        val gate = GateTestFixtures.gateOver(raster, hardM = floorM)
        val world = GateTestFixtures.RasterWorld(raster)
        val box = GateTestFixtures.box(80.0, 320.0, 20.0, 420.0)
        val grid = rasterize(
            box, 20.0, 25.0, 0.0, emptyList(), emptyList(), box.latNorth,
            field = GateTestFixtures.gateField(world), gate = gate
        )
        val start = at(200.0 - 80.0, 200.0 - 160.0)
        val aim = at(200.0 + 90.0, 200.0 + 180.0)
        val path = MultipassSearch.search(
            grid, grid.cellOf(start.latitude, start.longitude), grid.cellOf(aim.latitude, aim.longitude),
            Units.knotsToMps(25.0), stepGuard = StepGuard(gate, start, aim)
        ).path
        assertNotNull("the 26.6° passage holds a legal line", path)
    }

    /** The price walk's proof: nothing inside the steep ramp, the breakpoint off it, and a bounded span far out. */
    @Test
    fun theBerthsProofIsZeroInTheRampAndBoundedFarOut() {
        val raster = GateTestFixtures.raster(250, 300, 2.0) { n, _ -> if (n < 100.0) 2.0 else 10.0 }
        val world = GateTestFixtures.RasterWorld(raster)
        val gate = GateTestFixtures.gateOver(raster, hardM = floorM)
        val law = BerthLaw(marginM = 20.0, outerM = 50.0, secPerM = 0.1, floorM = 10.0, innerSecPerM = 0.5)
        val ctx = PullSetup(
            50.0, 20.0, 100.0, GateTestFixtures.gateField(world), at(480.0, 0.0), at(480.0, 590.0),
            gate = gate, berth = law, berthLambda = 1.0, paceMps = Units.knotsToMps(5.0)
        ).context()
        assertEquals("inside the ramp nothing is proved", 0.0, ctx.berthClearanceM(at(116.0, 300.0)), 0.0)
        val off = ctx.berthClearanceM(at(135.0, 300.0))
        assertTrue("off the ramp the clearance is the nearer breakpoint ($off)", off in 10.0..20.0)
        assertEquals("far out the proof reaches its bound and no further", 150.0, ctx.berthClearanceM(at(480.0, 300.0)), 0.0)
    }

    /** Inside the steep inner ramp the price walk reads every mark; far off the edge one read prices a span. */
    @Test
    fun theInnerRampIsPricedMarkByMark() {
        val raster = GateTestFixtures.raster(250, 300, 2.0) { n, _ -> if (n < 100.0) 2.0 else 10.0 }
        val world = GateTestFixtures.RasterWorld(raster)
        val gate = GateTestFixtures.gateOver(raster, hardM = floorM)
        val law = BerthLaw(marginM = 20.0, outerM = 50.0, secPerM = 0.1, floorM = 10.0, innerSecPerM = 0.5)
        fun reads(northM: Double): Pair<Long, Long> {
            val timing = PullTiming()
            val ctx = PullSetup(
                50.0, 20.0, 100.0, GateTestFixtures.gateField(world), at(480.0, 0.0), at(480.0, 590.0),
                gate = gate, berth = law, berthLambda = 1.0, paceMps = Units.knotsToMps(5.0)
            ).context(timing = timing)
            MultipassPull.softPriceSec(at(northM, 50.0), at(northM, 550.0), ctx)
            return timing.priceReads to timing.marks
        }
        val (rampReads, rampMarks) = reads(116.0)
        assertEquals("inside the ramp every mark is read", rampMarks, rampReads)
        val (openReads, openMarks) = reads(400.0)
        assertTrue("far off the edge spans are priced from one read ($openReads of $openMarks)", openReads < openMarks)
    }

    @Test
    fun theReadingPadHoldsTheFiftyMetreGuarantee() {
        // The live raster's own cell, about 19 m, split into its sub-cells.
        val gate = GateTestFixtures.gateOver(GateTestFixtures.raster(11, 11, 19.0) { _, _ -> 10.0 }, hardM = floorM)
        // Worst case: a reading two pads under the truth on each bank, plus one 20 m row.
        val guaranteedM = 2.0 * (floorM + 2.0 * gate.padM) + 20.0
        assertTrue("a 50 m passage always keeps a row (needs $guaranteedM m)", guaranteedM <= 50.0)
    }
}
