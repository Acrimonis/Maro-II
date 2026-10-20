package ykws.android.maro.spatial.multipass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.DepthSource
import ykws.android.maro.spatial.multipass.GateTestFixtures.at
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * **The 3 m edge as a line, pinned on a real raster.** The La Grenille shape is one shallow node among
 * deep ones: its bilinear dip falls under the gate only within metres of the node, between every cell
 * centre a 20 m grid would sample. The index must trace it, answer a distance that is never above the
 * truth (the pad's bound), and stay silent over water that holds no edge.
 */
class ShallowEdgeIndexTest {

    private val cellM = 20.0

    /** The node at row 5, column 5 — 110 m north and east of the corner. */
    private val rockN = 110.0
    private val rockE = 110.0

    /** One 2.2 m node among 5 m nodes. */
    private fun rock() = GateTestFixtures.raster(11, 11, cellM) { n, e ->
        if (n == rockN && e == rockE) 2.2 else 5.0
    }

    @Test
    fun aSingleShallowNodeIsTracedAsAClosedEdge() {
        val edges = GateTestFixtures.edgesOf(rock())
        assertTrue("the node itself stands in blocked water", edges.isShallow(at(rockN, rockE).latitude, at(rockN, rockE).longitude))
        for (k in 0 until 8) {
            val angle = k * Math.PI / 4.0
            val p = at(rockN + 15.0 * sin(angle), rockE + 15.0 * cos(angle))
            val d = edges.distanceM(p.latitude, p.longitude, 50.0)
            assertTrue("an edge stands between the node and 15 m off it on bearing $k (read $d)", d < 15.0)
        }
    }

    /**
     * **The bound, pinned against brute force.** The shallow water is sampled on a 0.25 m lattice; for a
     * thousand random points the index's reading minus the pad never exceeds that sampled distance, and is
     * never looser than two pads under it.
     */
    @Test
    fun theReadingMinusThePadIsALowerBoundOnTheTrueDistance() {
        val grid = rock()
        val gate = GateTestFixtures.gateOver(grid, hardM = 10.0)
        val depth = GateTestFixtures.depthOf(grid)
        val shallow = ArrayList<Pair<Double, Double>>()
        var dn = -14.0
        while (dn <= 14.0) {
            var de = -14.0
            while (de <= 14.0) {
                val p = at(rockN + dn, rockE + de)
                if (depth(p.latitude, p.longitude) < GateTestFixtures.MIN_DEPTH_M) shallow.add(rockN + dn to rockE + de)
                de += 0.25
            }
            dn += 0.25
        }
        assertTrue("the dip holds shallow water", shallow.isNotEmpty())
        val random = Random(42)
        repeat(1000) {
            val n = rockN + random.nextDouble(-60.0, 60.0)
            val e = rockE + random.nextDouble(-60.0, 60.0)
            val truth = shallow.minOf { (sn, se) -> hypot(sn - n, se - e) }
            if (truth < 0.5) return@repeat
            val read = gate.edgeM(at(n, e), 200.0)
            assertTrue("never above the truth at ($n, $e): read $read, truth $truth", read <= truth + 0.2)
            assertTrue("never looser than two pads at ($n, $e): read $read, truth $truth", read >= truth - 2.0 * gate.padM - 0.2)
        }
    }

    @Test
    fun waterThatHoldsNoEdgeAnswersNone() {
        val deep = GateTestFixtures.edgesOf(GateTestFixtures.raster(11, 11, cellM) { _, _ -> 5.0 })
        val shallow = GateTestFixtures.edgesOf(GateTestFixtures.raster(11, 11, cellM) { _, _ -> 2.0 })
        val p = at(rockN, rockE)
        assertEquals(Double.MAX_VALUE, deep.distanceM(p.latitude, p.longitude, 100.0), 0.0)
        assertEquals(Double.MAX_VALUE, shallow.distanceM(p.latitude, p.longitude, 100.0), 0.0)
        assertTrue("all-shallow water is still blocked where it stands", shallow.isShallow(p.latitude, p.longitude))
        assertFalse(deep.isShallow(p.latitude, p.longitude))
    }

    /** NoData stands with the deep water, so a shallow node beside it is still ringed by an edge. */
    @Test
    fun aNoDataNodeBesideAShallowNodeStillLeavesItsEdge() {
        val grid = GateTestFixtures.raster(11, 11, cellM) { n, e ->
            when {
                n == rockN && e == rockE -> 2.2
                n == rockN && e == rockE + cellM -> Double.NaN
                else -> 5.0
            }
        }
        val edges = GateTestFixtures.edgesOf(grid)
        val west = at(rockN, rockE - 15.0)
        assertTrue("the edge stands west of the node", edges.distanceM(west.latitude, west.longitude, 50.0) < 15.0)
        val node = at(rockN, rockE)
        assertTrue("and within a cell of the node", edges.distanceM(node.latitude, node.longitude, 50.0) < cellM)
        val east = at(rockN, rockE + 15.0)
        assertTrue("and on the NoData side too", edges.distanceM(east.latitude, east.longitude, 50.0) < cellM)
    }

    /**
     * **An excluded EMODnet node shapes nothing.** A 1.5 m EMODnet node beside 3.5 m Litto3D nodes, the
     * chart's cutoff at 2.0: the router reads it at the gate depth, so neither the node nor the band beside it
     * is blocked water and no edge is traced — it can no longer drag its Litto3D neighbours under the gate.
     */
    @Test
    fun anExcludedEmodnetNodeShapesNoEdge() {
        val grid = GateTestFixtures.raster(
            11, 11, cellM,
            source = { n, e -> if (n == rockN && e == rockE) DepthSource.EMODNET else DepthSource.LITTO3D }
        ) { n, e -> if (n == rockN && e == rockE) 1.5 else 3.5 }
        val edges = GateTestFixtures.edgesOf(grid, cutoffM = 2.0f)
        val node = at(rockN, rockE)
        assertFalse("the node itself is not blocked", edges.isShallow(node.latitude, node.longitude))
        val band = at(rockN, rockE + 12.0)
        assertFalse("nor the band beside it, which the old read walled", edges.isShallow(band.latitude, band.longitude))
        assertEquals("and no edge stands near it", Double.MAX_VALUE, edges.distanceM(node.latitude, node.longitude, 50.0), 0.0)
    }

    /**
     * **A real shoal beside an excluded node keeps its edge, the line at the excluded node's side.** A 2.0 m
     * Litto3D node beside an excluded 0.4 m EMODnet node: the blend between them is shallow on the Litto3D
     * side, and the 3 m line lands within one pad of the excluded node, which reads the gate depth.
     */
    @Test
    fun aRealShoalBesideAnExcludedNodeKeepsItsEdge() {
        val grid = GateTestFixtures.raster(
            11, 11, cellM,
            source = { n, e -> if (n == rockN && e == rockE + cellM) DepthSource.EMODNET else DepthSource.LITTO3D }
        ) { n, e ->
            when {
                n == rockN && e == rockE -> 2.0
                n == rockN && e == rockE + cellM -> 0.4
                else -> 5.0
            }
        }
        val edges = GateTestFixtures.edgesOf(grid, cutoffM = 3.0f)
        val shoal = at(rockN, rockE)
        assertTrue("the Litto3D shoal is blocked", edges.isShallow(shoal.latitude, shoal.longitude))
        val excluded = at(rockN, rockE + cellM)
        assertFalse("the excluded node is not", edges.isShallow(excluded.latitude, excluded.longitude))
        assertTrue(
            "the 3 m line lands within one pad of the excluded node",
            edges.distanceM(excluded.latitude, excluded.longitude, 50.0) <= edges.padM + 1e-6
        )
    }

    /** A plateau of excluded EMODnet reads the gate depth exactly: no blocked water and nothing traced. */
    @Test
    fun aPlateauOfExcludedEmodnetIsNotShallow() {
        val grid = GateTestFixtures.raster(11, 11, cellM, source = { _, _ -> DepthSource.EMODNET }) { _, _ -> 1.0 }
        val edges = GateTestFixtures.edgesOf(grid, cutoffM = 3.0f)
        val p = at(rockN + 7.0, rockE + 3.0)
        assertFalse("the plateau is not blocked", edges.isShallow(p.latitude, p.longitude))
        assertEquals(3.0f, grid.depthAtFilled(p.latitude, p.longitude, 3.0f, 3.0f).depthM)
        assertEquals("no edge is traced", Double.MAX_VALUE, edges.distanceM(p.latitude, p.longitude, 100.0), 0.0)
    }

    /** With the cutoff under the gate, EMODnet between the two still walls: only what the cutoff excludes fills. */
    @Test
    fun emodnetBetweenACutoffAndTheGateStillWalls() {
        val grid = GateTestFixtures.raster(
            11, 11, cellM,
            source = { n, e -> if (n == rockN && e == rockE) DepthSource.EMODNET else DepthSource.LITTO3D }
        ) { n, e -> if (n == rockN && e == rockE) 2.5 else 5.0 }
        val node = at(rockN, rockE)
        assertTrue("a 2.5 m EMODnet node walls under a 2.0 cutoff", GateTestFixtures.edgesOf(grid, cutoffM = 2.0f).isShallow(node.latitude, node.longitude))
        assertTrue("and under no cutoff", GateTestFixtures.edgesOf(grid, cutoffM = 0f).isShallow(node.latitude, node.longitude))
        assertFalse("and not under a 3.0 cutoff", GateTestFixtures.edgesOf(grid, cutoffM = 3.0f).isShallow(node.latitude, node.longitude))
    }

    @Test
    fun aSegmentOverTheRockMeetsItsEdgeAndOneFarOffDoesNot() {
        val edges = GateTestFixtures.edgesOf(rock())
        assertEquals(
            "a segment straight over the node crosses its edge",
            0.0, edges.segmentDistanceM(at(rockN, rockE - 40.0), at(rockN, rockE + 40.0), 30.0), 1e-9
        )
        val far = edges.segmentDistanceM(at(rockN + 40.0, rockE - 40.0), at(rockN + 40.0, rockE + 40.0), 60.0)
        assertTrue("a segment 40 m north stands well off it (read $far)", far > 25.0)
    }
}
