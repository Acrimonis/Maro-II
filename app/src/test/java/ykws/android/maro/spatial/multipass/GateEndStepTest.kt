package ykws.android.maro.spatial.multipass

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.DepthGrid
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.spatial.SpatialOperations
import ykws.android.maro.spatial.Units
import ykws.android.maro.spatial.multipass.GateTestFixtures.at
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot

/**
 * **A route's own ends keep the wall from the waiver's rim** — the step out of the start's cell and into
 * the aim's is read past the gate's 20 m disc, each end on its own, and an end the shallows box in still
 * walks out as the gate always let it. On a straight shelf, shallow south of 100 m, under 20 m cells.
 */
class GateEndStepTest {

    private val paceKn = 25.0
    private val cellM = 20.0

    private fun shelf(extra: (Double, Double) -> Boolean = { _, _ -> false }): DepthGrid =
        GateTestFixtures.raster(200, 220, 2.0) { n, e -> if (n < 100.0 || extra(n, e)) 2.0 else 10.0 }

    private fun gridOver(raster: DepthGrid, gate: GateRule): MultipassGrid {
        val world = GateTestFixtures.RasterWorld(raster)
        val box = GateTestFixtures.box(60.0, 340.0, 0.0, 420.0)
        return rasterize(
            box, cellM, paceKn, 0.0, emptyList(), emptyList(), box.latNorth,
            field = GateTestFixtures.gateField(world), gate = gate
        )
    }

    private fun slotAt(grid: MultipassGrid, walk: WalkWindows, p: LatLng): Int {
        val cell = grid.cellOf(p.latitude, p.longitude)
        return walk.slotOf(cell.row, cell.col)
    }

    private suspend fun search(grid: MultipassGrid, gate: GateRule, start: LatLng, aim: LatLng, waiveEndSlots: Boolean = true) =
        MultipassSearch.search(
            grid, grid.cellOf(start.latitude, start.longitude), grid.cellOf(aim.latitude, aim.longitude),
            Units.knotsToMps(paceKn), stepGuard = StepGuard(gate, start, aim),
            waiveEndSlots = waiveEndSlots
        )

    /**
     * Whether every straight of [drawn] keeps [hardM] wherever it stands more than [clearM] from both ends —
     * read on one-metre pieces, independently of the guard's own cut.
     */
    private fun keepsWallPast(gate: GateRule, drawn: List<LatLng>, start: LatLng, aim: LatLng, hardM: Double, clearM: Double): Boolean {
        fun near(p: LatLng) = SpatialOperations.haversine(p, start) < clearM || SpatialOperations.haversine(p, aim) < clearM
        for (k in 0 until drawn.size - 1) {
            val pieces = ceil(SpatialOperations.haversine(drawn[k], drawn[k + 1])).toInt().coerceAtLeast(1)
            for (j in 0 until pieces) {
                val p = chordPointAt(drawn[k], drawn[k + 1], j.toDouble() / pieces)
                val q = chordPointAt(drawn[k], drawn[k + 1], (j + 1).toDouble() / pieces)
                if (near(p) || near(q)) continue
                if (gate.segmentEdgeM(p, q, hardM + 1.0) < hardM - 1e-6) return false
            }
        }
        return true
    }

    /** The trace's reading of the waiver: an end's disc, or nothing — no stretch past the disc is waived. */
    @Test
    fun theWaiverNamesItsKind() {
        val gate = GateTestFixtures.gateOver(shelf(), hardM = 20.0)
        val start = at(150.0, 0.0)
        val aim = at(150.0, 400.0)
        assertEquals("disc", gate.waiverOf(at(150.0, 10.0), start, aim))
        assertEquals("none", gate.waiverOf(at(150.0, 40.0), start, aim))
        assertEquals("none", gate.waiverOf(at(150.0, 200.0), start, aim))
    }

    @Test
    fun anEndsOwnStepIsReadPastTheWaiver() {
        val raster = shelf()
        val gate = GateTestFixtures.gateOver(raster, hardM = 20.0)
        val grid = gridOver(raster, gate)
        val walk = WalkWindows.of(grid)
        // One metre off the shelf: the step north out of the start's cell leaves the 20 m disc while still
        // under 20 m off the edge.
        val start = at(101.0, 200.0)
        val aim = at(300.0, 380.0)
        val guard = StepGuard(gate, start, aim)
        val startSlot = slotAt(grid, walk, start)
        val north = slotAt(grid, walk, at(130.0, 200.0))
        val aimSlot = slotAt(grid, walk, aim)
        assertFalse(
            "held to the wall, the step out of the start's cell is refused",
            guard.allows(walk, startSlot, north, cellM, EndRule(startSlot, aimSlot, strictStart = true, strictAim = true))
        )
        assertEquals("and recorded as one the old waiver let through", listOf(north), guard.heldRefused)
        assertTrue(
            "under the old waiver it was free",
            guard.allows(walk, startSlot, north, cellM, EndRule(startSlot, aimSlot, strictStart = false, strictAim = true))
        )
    }

    /**
     * The start's shortest exit toward the aim — the diagonal out of its cell — passes a rock 17 m off past
     * the 20 m disc, both cell centres standing clear of the wall; the start leaves by an exit that keeps it.
     */
    @Test
    fun aStartWhoseShortestExitGrazesTheWallLeavesByAnother() = runTest {
        val raster = shelf { n, e -> hypot(n - 204.4, e - 235.6) < 3.0 }
        val gate = GateTestFixtures.gateOver(raster, hardM = 20.0)
        val grid = gridOver(raster, gate)
        val start = at(200.0, 210.0)
        val aim = at(330.0, 400.0)
        assertTrue(
            "the diagonal out of the start's cell grazes the rock",
            gate.segmentEdgeM(at(210.0, 210.0), at(230.0, 230.0), 50.0) < 20.0
        )
        assertTrue("while its far cell stands clear of the wall", gate.edgeM(at(230.0, 230.0), 50.0) >= 20.0)
        val outcome = search(grid, gate, start, aim)
        val path = outcome.path
        assertNotNull("the start walks out", path)
        assertTrue("held to the wall", outcome.strictStart)
        val drawn = listOf(start) + path!!.map { grid.center(it.row, it.col) } + listOf(aim)
        assertTrue("every leg keeps the wall from the disc's rim", keepsWallPast(gate, drawn, start, aim, 20.0, 22.0))
    }

    @Test
    fun aBoxedStartReleasesOnlyItself() = runTest {
        val raster = shelf()
        // A 25 m wall: no exit of the start's cell keeps it past the 20 m disc; the aim stands in open water.
        val gate = GateRule(GateTestFixtures.edgesOf(raster), hardM = 25.0, preferredM = 25.0, waiverM = 20.0)
        val grid = gridOver(raster, gate)
        val start = at(101.0, 200.0)
        val aim = at(125.0, 390.0)
        val outcome = search(grid, gate, start, aim)
        assertNotNull("the boxed start still walks out", outcome.path)
        assertFalse("under the old waiver", outcome.strictStart)
        assertTrue("while the open aim stays held", outcome.strictAim)
    }

    /** An aim in a pool no legal step reaches: the walk finds nothing, refused no end step, and is not walked again. */
    @Test
    fun aSearchWithNoWayIsWalkedOnce() = runTest {
        val raster = shelf { n, e -> hypot(n - 250.0, e - 330.0).let { it >= 30.0 && it < 60.0 } }
        val gate = GateTestFixtures.gateOver(raster, hardM = 20.0)
        val grid = gridOver(raster, gate)
        val start = at(150.0, 50.0)
        val aim = at(250.0, 330.0)
        val ends = search(grid, gate, start, aim)
        val bound = search(grid, gate, start, aim, waiveEndSlots = false)
        assertNull("no way reaches the pool", ends.path)
        assertNull(bound.path)
        assertEquals("one walk's expansions, not two", bound.expansions, ends.expansions)
    }

    /**
     * A wall across the water with one door, whose only step grazes the door's edges past the start's 20 m
     * disc. The start stands in the door's own cell, so the held walk refuses that step and finds nothing;
     * the step was the old waiver's, into water the held walk never reached, so the search walks again
     * waived and crosses — never turning a route into no route.
     */
    @Test
    fun aHeldWalkThatFindsNothingIsWalkedAgainWaived() = runTest {
        val raster = GateTestFixtures.raster(200, 220, 2.0) { n, e ->
            if (abs(n + e - 440.0) < 3.0 && hypot(n - 220.0, e - 220.0) > 21.0) 2.0 else 10.0
        }
        val gate = GateTestFixtures.gateOver(raster, hardM = 20.0)
        val grid = gridOver(raster, gate)
        val start = at(205.0, 205.0)
        val aim = at(300.0, 300.0)
        assertTrue("the door's step grazes the wall", gate.segmentEdgeM(at(210.0, 210.0), at(230.0, 230.0), 50.0) < 20.0)
        assertTrue(
            "while both its cells stand clear of it",
            gate.edgeM(at(210.0, 210.0), 50.0) >= 20.0 && gate.edgeM(at(230.0, 230.0), 50.0) >= 20.0
        )
        val bound = search(grid, gate, start, aim, waiveEndSlots = false)
        assertNull("held to the wall, nothing crosses", bound.path)

        val outcome = search(grid, gate, start, aim)

        assertNotNull("the waived re-walk crosses the door", outcome.path)
        assertFalse("and says the start was not held", outcome.strictStart)
        assertTrue("the outcome says the re-walk answered", outcome.rewalked)
        assertEquals("the start slot is the single grid's own", 0, outcome.startLayer)
        assertTrue("and has a way out", outcome.startOpenSteps > 0)
        assertTrue("its expansions add to the held walk's", outcome.expansions > bound.expansions)
    }

    @Test
    fun anOpenEndKeepsTheWallFromTheWaiversRim() = runTest {
        val raster = shelf()
        val gate = GateTestFixtures.gateOver(raster, hardM = 20.0)
        val grid = gridOver(raster, gate)
        val start = at(125.0, 30.0)
        val aim = at(125.0, 390.0)
        val outcome = search(grid, gate, start, aim)
        val path = outcome.path
        assertNotNull("the open end walks", path)
        assertTrue("held to the wall", outcome.strictStart && outcome.strictAim)
        val drawn = listOf(start) + path!!.map { grid.center(it.row, it.col) } + listOf(aim)
        assertTrue("every leg keeps the wall from the disc's rim", keepsWallPast(gate, drawn, start, aim, 20.0, 22.0))
    }
}
