package ykws.android.maro.spatial.multipass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.LatLng

/**
 * **The marker cost into the route** — the projection's own law: a Circle and a Corridor answer
 * containment and a boundary distance, the price is the linear `base × step × strength` that only ever
 * adds, and the marker sources price only inside a zone while a wall blocks only inside one and ignores
 * the price switch.
 */
class RouteMarkerCostTest {

    private val center = LatLng(43.5, 7.0)

    /**
     * A point [metresNorth] of [center] — the haversine's own metres-per-degree latitude
     * (`R·π/180` = 111 194.93 at WGS84's mean radius), so a pure latitude offset reads the exact metres.
     */
    private fun north(metresNorth: Double) = LatLng(43.5 + metresNorth / 111_194.93, 7.0)

    /** A point [metresEast] of [center] at 43.5° — the same planar read, on the long axis. */
    private fun east(metresEast: Double) = LatLng(43.5, 7.0 + metresEast / (111_320.0 * 0.7254))

    @Test
    fun aCircleContainsOnlyInsideItsRadius() {
        val g = RouteMarkerGeometry.Circle(center, radiusM = 1000.0)
        assertTrue("the centre is inside", g.contains(center))
        assertTrue("999 m is inside", g.contains(north(999.0)))
        assertFalse("1001 m is outside", g.contains(north(1001.0)))
    }

    @Test
    fun aCirclesBoundaryIsTheCircleEdge() {
        val g = RouteMarkerGeometry.Circle(center, radiusM = 1000.0)
        assertEquals("on the edge the boundary distance is zero", 0.0, g.boundaryDistanceM(north(1000.0)), 2.0)
        assertEquals("500 m out the boundary is 500", 500.0, g.boundaryDistanceM(north(1500.0)), 2.0)
    }

    @Test
    fun aCorridorContainsWithinItsHalfWidthCapsIncluded() {
        val a = LatLng(43.5, 7.0)
        val b = LatLng(43.5, 7.01)
        val g = RouteMarkerGeometry.Corridor(a, b, widthM = 400.0) // half-width 200 m
        assertTrue("on the centre line it is inside", g.contains(a))
        assertTrue("100 m beyond the b cap is still inside", g.contains(east(metresEast = corridorEastOf(b) + 100.0)))
        assertFalse("300 m beyond the b cap is outside", g.contains(east(metresEast = corridorEastOf(b) + 300.0)))
    }

    /** The metres east of [center] the point [b] stands at, so the cap offsets land beyond it. */
    private fun corridorEastOf(b: LatLng): Double = (b.longitude - 7.0) * (111_320.0 * 0.7254)

    @Test
    fun thePriceIsLinearAndNeverNegative() {
        assertEquals("a zero step pays nothing", 0.0, markerPriceSec(10.0, 0, 1.0), 1e-9)
        assertEquals("base × step × strength", 30.0, markerPriceSec(10.0, 3, 1.0), 1e-9)
        assertEquals("the strength scales it", 15.0, markerPriceSec(10.0, 3, 0.5), 1e-9)
        assertTrue("the top step over a positive base is positive", markerPriceSec(10.0, 9, 5.0) > 0.0)
    }

    @Test
    fun aPricedCircleCostsOnlyInsideIt() {
        val marker = RouteMarker("m1", RouteMarkerGeometry.Circle(center, 1000.0), step = 3, isWall = false)
        val field = RouteCostField(
            markerSources(listOf(marker), baseCostSec = 10.0, strength = 1.0, priceEnabled = true)
        )
        assertTrue("a priced marker makes the field a priced one", field.hasSoft)
        assertTrue("inside the circle the sea costs more", field.softPriceSecAt(center) > 0.0)
        assertEquals("outside the circle it costs nothing", 0.0, field.softPriceSecAt(north(2000.0)), 1e-9)
    }

    @Test
    fun aWallBlocksOnlyInsideItAndIgnoresThePriceSwitch() {
        val marker = RouteMarker("w1", RouteMarkerGeometry.Circle(center, 1000.0), step = 0, isWall = true)
        val field = RouteCostField(
            markerSources(listOf(marker), baseCostSec = 10.0, strength = 1.0, priceEnabled = false)
        )
        assertTrue("a wall makes the field a blocking one", field.hasBlocking)
        assertTrue("inside the wall the cell is blocked", field.hardBlocked(center))
        assertFalse("outside the wall it is not", field.hardBlocked(north(2000.0)))
        assertEquals("the wall adds no price even with the switch off", 0.0, field.softPriceSecAt(center), 1e-9)
    }

    @Test
    fun noMarkerAddsNoSourceSoTheFastPathHolds() {
        val field = RouteCostField(
            markerSources(emptyList(), baseCostSec = 10.0, strength = 1.0, priceEnabled = true)
        )
        assertFalse("no marker means no price pass", field.hasSoft)
        assertFalse("and no block pass", field.hasBlocking)
    }
}
