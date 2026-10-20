package ykws.android.maro.spatial.multipass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.DepthSample
import ykws.android.maro.data.model.DepthSource
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.BBox

/** The two gate readings the `LINE` carries: the shallowest known depth and the nearest edge. */
class RouteDepthReadingTest {

    private val gateMinM = 3.0
    private val edgeLat = 43.5005

    /** Shallow (2 m, under the 3 m gate) south of [edgeLat], deep (20 m) north — a straight shelf edge. */
    private class ShelfAt(private val edgeLat: Double) : MultipassWorld {
        override val coastlineReady: Boolean get() = true
        override val depthReady: Boolean get() = true
        override val regionBounds: BBox? get() = null
        override val bandWidthM: Double get() = 0.0
        override fun segmentsIn(box: BBox): List<MultipassEdge> = emptyList()
        override fun openCoastIn(box: BBox): List<List<LatLng>> = emptyList()
        override fun isWater(latitude: Double, longitude: Double): Boolean = true
        override fun distanceToCoastM(latitude: Double, longitude: Double): Double = Double.MAX_VALUE
        override fun depthAt(latitude: Double, longitude: Double): DepthSample =
            if (latitude < edgeLat) {
                DepthSample(2.0f, DepthSource.LITTO3D, 90, true)
            } else {
                DepthSample(20.0f, DepthSource.LITTO3D, 90, true)
            }
    }

    private fun lineAt(lat: Double) = listOf(LatLng(lat, 7.000), LatLng(lat, 7.002))

    @Test
    fun theShallowestKnownDepthIsTheLinesOwn() {
        val world = ShelfAt(edgeLat)
        assertEquals(20.0, RouteDepthReading.minDepthM(world, lineAt(43.5010)), 1e-9)
        assertEquals(2.0, RouteDepthReading.minDepthM(world, lineAt(43.5000)), 1e-9)
    }

    @Test
    fun theGateEdgeIsTheNearerBlockedWater() {
        val world = ShelfAt(edgeLat)
        val off = RouteDepthReading.gateEdgeM(world, lineAt(43.5010), gateActive = true, minDepthM = gateMinM)
        assertTrue("the edge stands about 55-65 m south (read $off)", off in 55.0..65.0)

        val inside = RouteDepthReading.gateEdgeM(world, lineAt(43.5000), gateActive = true, minDepthM = gateMinM)
        assertEquals("a line inside the shallow is one ring from the edge", 5.0, inside, 0.0)
    }

    /**
     * **The measured edge keeps the margin, not just the step** — a point ten metres off blocked water reads
     * inside a twenty-metre wall, one thirty metres off clears it, and neither reads further than the truth.
     */
    @Test
    fun theMeasuredEdgeKeepsItsOwnMargin() {
        val world = ShelfAt(edgeLat)
        val gate = GateRule(world.shallowEdges(gateMinM), hardM = 20.0, preferredM = 20.0, waiverM = 20.0)
        val mPerDegLat = ykws.android.maro.spatial.SpatialOperations.EARTH_RADIUS_M * Math.PI / 180.0
        val tenM = LatLng(edgeLat + 10.0 / mPerDegLat, 7.001)
        val thirtyM = LatLng(edgeLat + 30.0 / mPerDegLat, 7.001)

        assertTrue("ten metres off the blocked water is inside a twenty-metre wall", gate.edgeM(tenM, 50.0) < 20.0)
        assertTrue("never read further than it stands", gate.edgeM(tenM, 50.0) <= 10.0)
        assertTrue("thirty metres off it clears", gate.edgeM(thirtyM, 50.0) >= 20.0)
    }

    /** The index's own reading rides the `LINE` beside the independent scan, and they agree on a shelf. */
    @Test
    fun theIndexReadingAgreesWithTheScan() {
        val world = ShelfAt(edgeLat)
        val gate = GateRule(world.shallowEdges(gateMinM), hardM = 10.0, preferredM = 20.0, waiverM = 20.0)
        val reading = RouteDepthReading.edgeReading(
            world, lineAt(43.5010), gateActive = true, minDepthM = gateMinM, excludeEndsM = 20.0, gate = gate
        )
        assertTrue("the scan reads about 55-65 m (read ${reading.distanceM})", reading.distanceM in 55.0..65.0)
        assertTrue(
            "the index reads under it by no more than the trace's error (read ${reading.indexDistanceM})",
            reading.indexDistanceM <= reading.distanceM + 0.5 && reading.indexDistanceM >= reading.distanceM - 10.0
        )
        assertEquals("a line 55 m off spends no metre inside the preferred standoff", 0.0, reading.innerM, 0.0)
    }

    @Test
    fun aDisarmedGateReadsNothing() {
        val world = ShelfAt(edgeLat)
        val edge = RouteDepthReading.gateEdgeM(world, lineAt(43.5010), gateActive = false, minDepthM = gateMinM)
        assertTrue("no gate, no edge reading", edge.isNaN())
    }

    @Test
    fun anUnsurveyedLineHasNoDepthReading() {
        val world = object : MultipassWorld {
            override val coastlineReady: Boolean get() = true
            override val depthReady: Boolean get() = true
            override val regionBounds: BBox? get() = null
            override val bandWidthM: Double get() = 0.0
            override fun segmentsIn(box: BBox): List<MultipassEdge> = emptyList()
            override fun openCoastIn(box: BBox): List<List<LatLng>> = emptyList()
            override fun isWater(latitude: Double, longitude: Double): Boolean = true
            override fun distanceToCoastM(latitude: Double, longitude: Double): Double = Double.MAX_VALUE
            override fun depthAt(latitude: Double, longitude: Double): DepthSample = DepthSample.NONE
        }
        assertTrue(
            "an unsurveyed line answers no depth",
            RouteDepthReading.minDepthM(world, lineAt(43.5010)).isNaN()
        )
    }
}
