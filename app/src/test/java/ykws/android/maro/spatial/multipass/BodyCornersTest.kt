package ykws.android.maro.spatial.multipass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.spatial.SpatialOperations
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil

/**
 * **The body corners of a costed marker** — the outline of a circle or a corridor's stadium, stood the
 * offset off the body and sampled by the cell, so a bend inside the collar can be offered the body's own
 * edge. A pin has no body and yields nothing; a wall is a body like any other; the sample count is floored
 * and capped; the set is the same for the same marker twice.
 */
class BodyCornersTest {

    private val centre = LatLng(43.5, 7.0)
    private val offsetM = 25.0
    private val cellM = 20.0

    @Test
    fun aPinHasNoBodyAndYieldsNoCorner() {
        val pin = RouteMarker("pin", RouteMarkerGeometry.Circle(centre, 0.0, 200.0), step = 2, isWall = false)
        assertEquals(emptyList<BodyCorner>(), TangentCorners.bodyCorners(listOf(pin), offsetM, cellM))
    }

    @Test
    fun aCircleIsSampledOnItsOffsetRingFirstPointDueNorth() {
        val circle = RouteMarker("c", RouteMarkerGeometry.Circle(centre, 100.0, 300.0), step = 5, isWall = false)
        val corners = TangentCorners.bodyCorners(listOf(circle), offsetM, cellM)
        val ringM = 125.0
        assertEquals(ceil(2.0 * PI * ringM / cellM).toInt(), corners.size)
        for (c in corners) {
            assertTrue(abs(SpatialOperations.haversine(c.point, centre) - ringM) < 0.5)
            assertEquals("each corner carries its marker's collar", 300.0, c.reachM, 0.0)
        }
        val first = corners.first().point
        assertTrue("the first sample stands due north", first.latitude > centre.latitude)
        assertEquals(centre.longitude, first.longitude, 1e-9)
    }

    @Test
    fun aCorridorIsSampledOnItsStadiumFirstPointAtTheFirstCapApex() {
        val p1 = centre
        val p2 = LatLng(43.5, 7.0 + 400.0 / (SpatialOperations.EARTH_RADIUS_M * PI / 180.0 * Math.cos(Math.toRadians(43.5))))
        val corridor = RouteMarker(
            "k", RouteMarkerGeometry.Corridor(p1, p2, widthM = 100.0, proximityM = 150.0), step = 3, isWall = false
        )
        val corners = TangentCorners.bodyCorners(listOf(corridor), offsetM, cellM)
        val ringM = 75.0
        assertEquals("the stadium's perimeter asks more than the cap allows", 64, corners.size)
        for (c in corners) {
            val d = SpatialOperations.pointToSegmentDistance(c.point, p1, p2)
            assertTrue("every corner stands the offset off the body: $d", abs(d - ringM) < 1.0)
            assertEquals(150.0, c.reachM, 0.0)
        }
        val apex = corners.first().point
        assertTrue("the first sample is behind p1, on the axis", apex.longitude < p1.longitude)
        assertEquals(p1.latitude, apex.latitude, 1e-9)
        assertTrue(abs(SpatialOperations.haversine(apex, p1) - ringM) < 1.0)
    }

    @Test
    fun theCapBoundsAWideBodyAndTheFloorAStubbyOne() {
        val wide = RouteMarker("w", RouteMarkerGeometry.Circle(centre, 1000.0, 300.0), step = 5, isWall = false)
        assertEquals(64, TangentCorners.bodyCorners(listOf(wide), offsetM, cellM).size)
        val stubby = RouteMarker("s", RouteMarkerGeometry.Circle(centre, 1.0, 300.0), step = 5, isWall = false)
        assertEquals("a 26 m ring at a 50 m cell asks four and is floored", 8, TangentCorners.bodyCorners(listOf(stubby), offsetM, 50.0).size)
    }

    @Test
    fun aWallIsABodyLikeAnyOtherAndTheSetIsDeterministic() {
        val wall = RouteMarker("x", RouteMarkerGeometry.Circle(centre, 100.0, 300.0), step = 0, isWall = true)
        val priced = RouteMarker("x", RouteMarkerGeometry.Circle(centre, 100.0, 300.0), step = 9, isWall = false)
        val once = TangentCorners.bodyCorners(listOf(wall), offsetM, cellM)
        assertEquals(TangentCorners.bodyCorners(listOf(priced), offsetM, cellM), once)
        assertEquals(TangentCorners.bodyCorners(listOf(wall), offsetM, cellM), once)
    }

    @Test
    fun theReachBoxCoversBodyAndCollar() {
        val circle = RouteMarker("c", RouteMarkerGeometry.Circle(centre, 100.0, 300.0), step = 5, isWall = false)
        val box = circle.reachBox()
        val north = LatLng(box.latNorth, centre.longitude)
        assertTrue(abs(SpatialOperations.haversine(north, centre) - 400.0) < 1.0)
        val east = LatLng(centre.latitude, box.lonEast)
        assertTrue(abs(SpatialOperations.haversine(east, centre) - 400.0) < 1.0)
    }
}
