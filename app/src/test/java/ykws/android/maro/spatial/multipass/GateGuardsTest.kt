package ykws.android.maro.spatial.multipass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.DepthGrid
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.spatial.multipass.GateTestFixtures.at

/**
 * **The guards that read the measured edge** — the pull's chord, the corner pass's bow and the ends' disc.
 * Each is pinned on a straight shelf (shallow south of 100 m, deep north of it, on a 2 m raster), so
 * every distance a test names is metres off the edge.
 */
class GateGuardsTest {

    private val floorM = 10.0
    private val edgeN = 100.0

    private fun shelf(): DepthGrid =
        GateTestFixtures.raster(200, 250, 2.0) { n, _ -> if (n < edgeN) 2.0 else 10.0 }

    private fun pullContext(gate: GateRule, start: LatLng, aim: LatLng): PullContext {
        val world = GateTestFixtures.RasterWorld(shelf())
        return PullSetup(
            50.0, 20.0, 100.0, GateTestFixtures.gateField(world), start, aim, gate = gate
        ).context()
    }

    @Test
    fun aChordFiveMetresOffTheEdgeIsRefusedAndOneFifteenOffIsNot() {
        val gate = GateTestFixtures.gateOver(shelf(), hardM = floorM)
        val farStart = at(350.0, 0.0)
        val farAim = at(350.0, 490.0)
        val ctx = pullContext(gate, farStart, farAim)
        assertEquals(
            "five metres off the measured edge is inside the ten-metre wall",
            ChordRefusal.LAND, MultipassPull.legClearCause(at(edgeN + 5.0, 100.0), at(edgeN + 5.0, 300.0), ctx)
        )
        assertNull(
            "fifteen metres off it clears",
            MultipassPull.legClearCause(at(edgeN + 15.0, 100.0), at(edgeN + 15.0, 300.0), ctx)
        )
    }

    /** **Q1**: the gate waives itself within 20 m of an end — the coast's own 50 m disc no longer covers it. */
    @Test
    fun theGateWaivesItselfOnlyWithinTwentyMetresOfAnEnd() {
        val gate = GateTestFixtures.gateOver(shelf(), hardM = floorM)
        val start = at(edgeN + 5.0, 100.0)
        val shortAim = at(edgeN + 5.0, 130.0)
        assertNull(
            "a 30 m chord along the edge stands inside the two ends' discs",
            MultipassPull.legClearCause(start, shortAim, pullContext(gate, start, shortAim))
        )
        val longAim = at(edgeN + 5.0, 170.0)
        assertEquals(
            "a 70 m chord leaves them, and the wall binds again past 20 m",
            ChordRefusal.LAND, MultipassPull.legClearCause(start, longAim, pullContext(gate, start, longAim))
        )
    }

    /** **Q2**: a bow keeps the gate's 20 m — not the coast's 50 m — and never comes nearer than its corner. */
    @Test
    fun aBowKeepsThePreferredStandoffAndNoMore() {
        val gate = GateTestFixtures.gateOver(shelf(), hardM = floorM, preferredM = 20.0)
        val world = GateTestFixtures.RasterWorld(shelf())
        // An inverted V whose apex stands 35 m off the edge: the turn's inside — the side a bow cuts — faces
        // the shelf, and the old 50 m depth ring reached it from the apex.
        val corner = at(edgeN + 35.0, 220.0)
        val rounded = RouteCornerPass.round(
            listOf(at(edgeN + 12.0, 120.0), corner, at(edgeN + 12.0, 320.0)), 5.0, world, gate, 50.0
        )
        assertEquals(
            "the bow fits 35 m off the edge, which the old 50 m depth ring refused",
            RouteCornerPass.CornerOutcome.ROUNDED, rounded.verdicts.single().outcome
        )
        for (p in rounded.points.subList(1, rounded.points.size - 1)) {
            assertTrue("every bow point keeps 20 m", gate.edgeM(p, 50.0) >= 20.0 - 1e-6)
        }
        // A tighter inverted V whose apex stands 14 m off the edge: no bow point may come nearer than it.
        val nearCorner = at(edgeN + 14.0, 220.0)
        val near = RouteCornerPass.round(
            listOf(at(edgeN + 11.0, 200.0), nearCorner, at(edgeN + 11.0, 240.0)), 5.0, world, gate, 50.0
        )
        val cornerEdge = gate.edgeM(nearCorner, 50.0)
        assertEquals(
            "a bow cutting toward shallows nearer than its corner is refused",
            RouteCornerPass.CornerOutcome.BOW_DEPTH, near.verdicts.single().outcome
        )
        for (p in near.points.subList(1, near.points.size - 1)) {
            assertTrue("a bow never comes nearer than its corner", gate.edgeM(p, 50.0) >= cornerEdge - 1e-6)
        }
    }

    /** **Q1 at the grid**: the end disc reopens the gate's painted water only within the gate's own disc. */
    @Test
    fun theEndDiscReopensTheGatesWallOnlyWithinTwentyMetres() {
        val raster = shelf()
        val gate = GateTestFixtures.gateOver(raster, hardM = floorM)
        val world = GateTestFixtures.RasterWorld(raster)
        val box = GateTestFixtures.box(edgeN - 40.0, edgeN + 160.0, 100.0, 300.0)
        val grid = rasterize(
            box, 20.0, 25.0, 0.0, emptyList(), emptyList(), box.latNorth,
            field = GateTestFixtures.gateField(world), gate = gate
        )
        val end = at(edgeN + 1.0, 200.0)
        val opened = openEndDisc(grid, world, end, 50.0, true, GateTestFixtures.MIN_DEPTH_M, gate = gate)
        assertTrue("the end beside the shallow is not enclosed", opened > 0)
        for (r in 0 until grid.rows) {
            for (c in 0 until grid.cols) {
                val centre = grid.center(r, c)
                if (!grid.passable(r, c) || gate.edgeM(centre, floorM) >= floorM) continue
                val fromEnd = ykws.android.maro.spatial.SpatialOperations.haversine(centre, end)
                assertTrue("a reopened wall cell stands within 20 m of the end ($fromEnd m)", fromEnd <= gate.waiverM)
            }
        }
    }
}
