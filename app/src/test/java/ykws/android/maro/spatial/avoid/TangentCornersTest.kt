package ykws.android.maro.spatial.avoid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.spatial.LandRingOrientation

/**
 * The tangent-corner collection, pinned at the algorithm level: the convex corners of an open coast
 * and of a CCW ring come out offset by the margin, and concave vertices or clear water yield nothing.
 */
class TangentCornersTest {

    private val margin = 25.0

    @Test
    fun clearWaterYieldsNoCorners() {
        assertEquals(emptyList<LatLng>(), TangentCorners.corners(emptyList(), emptyList(), margin))
    }

    @Test
    fun aUshapedHeadlandYieldsItsTwoTipCorners() {
        val coast = listOf(
            LatLng(43.51, 7.00), LatLng(43.51, 7.02),
            LatLng(43.49, 7.02), LatLng(43.49, 7.04),
            LatLng(43.51, 7.04), LatLng(43.51, 7.06)
        )

        val corners = TangentCorners.corners(emptyList(), listOf(coast), margin)

        assertEquals("the two convex tip corners, nothing else", 2, corners.size)
        for (c in corners) {
            assertTrue("the tip corners stand south of the tip", c.latitude < 43.49)
        }
        assertTrue("one tip corner is west", corners.any { it.longitude < 7.03 })
        assertTrue("one tip corner is east", corners.any { it.longitude > 7.03 })
    }

    @Test
    fun aCcwRingYieldsAllFourCorners() {
        val southWest = LatLng(43.498, 7.024)
        val northEast = LatLng(43.502, 7.036)
        val ring = listOf(
            southWest,
            LatLng(southWest.latitude, northEast.longitude),
            northEast,
            LatLng(northEast.latitude, southWest.longitude)
        )
        val edges = ring.zipWithNext().map { (a, b) -> AvoidEdge(a, b, LandRingOrientation.CCW_RING) } +
            AvoidEdge(ring.last(), ring.first(), LandRingOrientation.CCW_RING)

        val corners = TangentCorners.corners(edges, emptyList(), margin)

        assertEquals("a rectangle ring contributes its four corners", 4, corners.size)
        for (c in corners) {
            assertTrue("every corner stands outside the ring", c.latitude < 43.498 || c.latitude > 43.502 || c.longitude < 7.024 || c.longitude > 7.036)
        }
    }
}
