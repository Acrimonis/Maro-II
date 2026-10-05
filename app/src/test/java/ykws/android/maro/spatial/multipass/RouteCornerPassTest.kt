package ykws.android.maro.spatial.multipass

import ykws.android.maro.data.model.DepthSample
import ykws.android.maro.data.model.DepthSource
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.data.regulation.SpeedZone
import ykws.android.maro.spatial.SpatialOperations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The corner pass's own contract: convex corners are rounded outward and clear, the rest stays sharp. */
class RouteCornerPassTest {

    @Test
    fun aStraightLineStaysWhole() {
        val a = LatLng(43.5, 7.0)
        val b = LatLng(43.502, 7.0)
        val c = LatLng(43.504, 7.0)
        val rounded = RouteCornerPass.round(
            listOf(a, b, c), 28.0, CoastWorld(emptyList()), false, 3.0, 50.0
        )
        assertEquals(listOf(a, b, c), rounded.points)
    }

    @Test
    fun aConvexCornerIsRoundedAndStaysClear() {
        val corner = LatLng(43.5, 7.0)
        val prev = LatLng(43.505, 7.0)
        val next = LatLng(43.5, 7.005)
        val coast = listOf(LatLng(43.494, 6.995), LatLng(43.494, 7.005))
        val world = CoastWorld(coast)
        val margin = 50.0
        val rounded = RouteCornerPass.round(
            listOf(prev, corner, next), 28.0, world, false, 3.0, margin
        )
        assertTrue("the corner gains arc points", rounded.points.size > 3)
        assertTrue("the corner vertex is replaced by the arc", corner !in rounded.points)
        for (p in rounded.points) {
            assertTrue(
                "every drawn point clears the coast",
                world.distanceToCoastM(p.latitude, p.longitude) >= margin - 1e-6
            )
        }
        assertTrue(
            "some arc point carries a corner-speed ceiling",
            rounded.points.any { rounded.ceilingKnAt(it) != null }
        )
    }

    @Test
    fun aCornerNextToAZoneDoesNotBowIntoIt() {
        val corner = LatLng(43.5, 7.0)
        val prev = LatLng(43.505, 7.0)
        val next = LatLng(43.5, 7.005)
        val zone = SpeedZone(
            "z", "Cap", 5.0,
            listOf(
                LatLng(43.501, 7.001),
                LatLng(43.501, 7.020),
                LatLng(43.520, 7.020),
                LatLng(43.520, 7.001),
                LatLng(43.501, 7.001)
            )
        )
        val world = CoastWorld(emptyList(), listOf(zone))
        val rounded = RouteCornerPass.round(
            listOf(prev, corner, next), 28.0, world, false, 3.0, 50.0
        )
        for (p in rounded.points) {
            assertFalse("no drawn point bows into the zone", zone.contains(p.latitude, p.longitude))
        }
    }

    @Test
    fun aCornerWithShortLegsStaysSharp() {
        val corner = LatLng(43.5, 7.0)
        val prev = LatLng(43.50001, 7.0)
        val next = LatLng(43.5, 7.00001)
        val rounded = RouteCornerPass.round(
            listOf(prev, corner, next), 28.0, CoastWorld(emptyList()), false, 3.0, 50.0
        )
        assertTrue("the corner stands sharp", corner in rounded.points)
        assertEquals("taken at the turn floor", 5.0, rounded.ceilingKnAt(corner)!!, 0.01)
    }

    /** A world whose coast is one polyline; optional zones; depth always clear. */
    private class CoastWorld(
        private val coast: List<LatLng>,
        private val zones: List<SpeedZone> = emptyList()
    ) : MultipassWorld {
        override val coastlineReady = true
        override val depthReady = true
        override val bandWidthM = 0.0
        override val regionBounds: BBox? = null

        override fun segmentsIn(box: BBox): List<MultipassEdge> = emptyList()
        override fun openCoastIn(box: BBox): List<List<LatLng>> = emptyList()
        override fun isWater(latitude: Double, longitude: Double) = true
        override fun speedZonesIn(box: BBox): List<SpeedZone> = speedZonesInBox(zones, box, emptySet())
        override fun zoneLimitKnAt(latitude: Double, longitude: Double): Double? =
            strictestLimitKnAt(zones, emptySet(), latitude, longitude)
        override fun depthAt(latitude: Double, longitude: Double): DepthSample =
            DepthSample(20f, DepthSource.LITTO3D, 100, true)

        override fun distanceToCoastM(latitude: Double, longitude: Double): Double {
            val p = LatLng(latitude, longitude)
            var best = Double.MAX_VALUE
            for (i in 0 until coast.size - 1) {
                val d = SpatialOperations.pointToSegmentDistance(p, coast[i], coast[i + 1])
                if (d < best) best = d
            }
            return best
        }
    }
}
