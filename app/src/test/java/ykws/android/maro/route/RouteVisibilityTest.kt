package ykws.android.maro.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.data.regulation.SpeedZone

/**
 * Visibility over the obstacle set — plan §10.3 step 3 and the one rule the raw
 * `segmentsIntersect()` primitive cannot express: a leg may **touch** a boundary vertex (that is how
 * a taut path bends there) but may not cross one.
 */
class RouteVisibilityTest {

    private val gridBox = BBox(latSouth = 43.50, latNorth = 43.52, lonWest = 7.00, lonEast = 7.02)

    /** A north–south wall of two collinear segments meeting at 43.5110. */
    private val wall = listOf(
        RouteSegment(LatLng(43.5050, 7.0100), LatLng(43.5110, 7.0100)),
        RouteSegment(LatLng(43.5110, 7.0100), LatLng(43.5170, 7.0100)),
    )

    @Test
    fun `a leg crossing a barrier is blocked`() {
        val index = RouteBarrierIndex(gridBox, wall)
        assertTrue(index.blocked(LatLng(43.5050, 7.0050), LatLng(43.5150, 7.0150)))
        assertFalse(index.blocked(LatLng(43.5050, 7.0040), LatLng(43.5050, 7.0060)))
    }

    @Test
    fun `a leg leaving a shared endpoint is a touch, not a crossing`() {
        val index = RouteBarrierIndex(gridBox, wall)
        val vertex = LatLng(43.5110, 7.0100)
        assertTrue(index.blocked(LatLng(43.5050, 7.0110), LatLng(43.5150, 7.0090)))
        assertFalse(
            "a taut path bends exactly on the boundary",
            index.blocked(vertex, LatLng(43.5160, 7.0105)),
        )
        assertFalse(
            "and leaves a contour vertex without being blocked by the segments that meet there",
            index.blocked(LatLng(43.5050, 7.0100), LatLng(43.5060, 7.0150)),
        )
    }

    @Test
    fun `a leg running along a boundary is not blocked - collinear means a hug`() {
        val index = RouteBarrierIndex(gridBox, wall)
        assertFalse(index.blocked(LatLng(43.5070, 7.0100), LatLng(43.5090, 7.0100)))
    }

    @Test
    fun `the harvest turns a crossing zone into rings and drops the far ones`() {
        val near = SpeedZone(
            id = "near",
            name = "Test 5 kn",
            speedLimitKn = 5.0,
            outerRing = rect(43.5100, 7.0100, 0.0015),
            holes = listOf(rect(43.5100, 7.0100, 0.0005)),
        )
        val far = SpeedZone(
            id = "far",
            name = "Far 10 kn",
            speedLimitKn = 10.0,
            outerRing = rect(43.6000, 7.1000, 0.0015),
        )
        val assets = RouteHarness.syntheticAssets(
            gridBox,
            depthAt = { _, _ -> 20f },
            zones = listOf(near, far),
        )
        val config = RouteConfig()
        val exclusion = RouteExclusion.build(assets.grid, assets.coast, config, gridBox)
        val obstacles = RouteObstacles.harvest(exclusion, assets.zones, config)

        val rings = obstacles.rings.map { it.name }
        assertTrue("the crossing zone keeps its outer ring", rings.any { it.contains("Test 5 kn · 5.0 kn") })
        assertTrue("its hole is a ring of its own", obstacles.rings.any { it.kind == RingKind.ZONE_HOLE })
        assertEquals("the far zone never reaches the corridor", 0, obstacles.rings.count { it.name.startsWith("Far") })

        val outer = obstacles.rings.first { it.kind == RingKind.ZONE_OUTER && it.name.startsWith("Test") }
        assertEquals("the ring is cut into as many pieces as it has vertices", outer.points.size, outer.segments.size)
        assertTrue("the hint cleared the corridor is dropped", obstacles.vertices.isNotEmpty())
        assertTrue("rings join the barrier set", obstacles.barriers.size > exclusion.segments.size)
    }

    private fun rect(lat: Double, lon: Double, halfDeg: Double): List<LatLng> = listOf(
        LatLng(lat - halfDeg, lon - halfDeg),
        LatLng(lat - halfDeg, lon + halfDeg),
        LatLng(lat + halfDeg, lon + halfDeg),
        LatLng(lat + halfDeg, lon - halfDeg),
    )
}
