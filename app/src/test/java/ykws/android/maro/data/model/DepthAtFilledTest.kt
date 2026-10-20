package ykws.android.maro.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.spatial.multipass.GateTestFixtures
import ykws.android.maro.spatial.multipass.GateTestFixtures.at

/**
 * **The route search's depth read** — an excluded EMODnet node and a node with no data take part in the
 * bilinear blend at the gate depth, so neither drags a real sounding under the gate nor walls anything itself.
 */
class DepthAtFilledTest {

    private val cellM = 20.0
    private val gate = 3.0f

    /** Node (r, c) stands at ((r + 0.5) x 20 m, (c + 0.5) x 20 m). */
    private fun grid(depth: (Int, Int) -> Double, emodnet: (Int, Int) -> Boolean) = GateTestFixtures.raster(
        4, 4, cellM,
        source = { n, e -> if (emodnet((n / cellM).toInt(), (e / cellM).toInt())) DepthSource.EMODNET else DepthSource.LITTO3D }
    ) { n, e -> depth((n / cellM).toInt(), (e / cellM).toInt()) }

    @Test
    fun anExcludedEmodnetNodeNoLongerDragsALitto3dNeighbourUnderTheGate() {
        val g = grid({ _, c -> if (c == 2) 0.4 else 4.0 }, { _, c -> c == 2 })
        val nearer = at(30.0, 36.0) // between column 1 (Litto3D 4.0) and column 2 (EMODnet 0.4), nearer column 1
        val filled = g.depthAtFilled(nearer.latitude, nearer.longitude, 3.0f, gate)
        assertTrue("reads at or above the gate (read ${filled.depthM})", filled.depthM >= gate)
        assertEquals(DepthSource.LITTO3D, filled.source)
        assertTrue(
            "where the old read — blend, then exclude — walled it",
            g.depthAt(nearer.latitude, nearer.longitude).gatedForEmodnetShallow(3.0f).depthM < gate
        )
    }

    @Test
    fun aSampleWithAllFourNodesMissingIsNoData() {
        val g = grid({ _, _ -> Double.NaN }, { _, _ -> false })
        val p = at(30.0, 30.0)
        assertFalse(g.depthAtFilled(p.latitude, p.longitude, 3.0f, gate).hasData)
    }

    @Test
    fun aSampleOfExcludedNodesOnlyReadsTheGateDepthExactlyWithNoSource() {
        val g = grid({ _, _ -> 1.2 }, { _, _ -> true })
        val p = at(33.0, 27.0)
        val filled = g.depthAtFilled(p.latitude, p.longitude, 3.0f, gate)
        assertEquals(gate, filled.depthM)
        assertEquals(DepthSource.NONE, filled.source)
        assertTrue(filled.hasData)
    }

    @Test
    fun theNegativeEmodnetArtefactStaysExcluded() {
        val g = grid({ _, _ -> -1.7 }, { _, _ -> true })
        val p = at(30.0, 30.0)
        assertEquals(gate, g.depthAtFilled(p.latitude, p.longitude, 3.0f, gate).depthM)
        assertTrue(DepthSource.isExcludedEmodnet(-1.7f, DepthSource.EMODNET.id, 3.0f))
    }

    @Test
    fun aMissingNodeBesideARealShoalLeavesItShallow() {
        val g = grid({ _, c -> if (c == 2) Double.NaN else 2.0 }, { _, _ -> false })
        val onShoal = at(30.0, 30.0) // column 1's own node, a 2.0 m Litto3D sounding
        assertTrue(g.depthAtFilled(onShoal.latitude, onShoal.longitude, 3.0f, gate).depthM < gate)
    }
}
