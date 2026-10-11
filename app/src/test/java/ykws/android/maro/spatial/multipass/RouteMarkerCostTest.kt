package ykws.android.maro.spatial.multipass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.LatLng

/**
 * **The marker cost into the route** — the projection's own law: a body plus the **proximity collar**
 * around it. [RouteMarkerGeometry.contains] is the body alone (the wall's own area),
 * [RouteMarkerGeometry.wellFactor] is full inside the body and falls in a straight line to nothing at the
 * collar's outer edge, and the price is that factor times the linear `base × step × strength` that only
 * ever adds. A **Pin** is a body of radius zero, so its whole price is a cone from its centre out to its
 * own proximity.
 *
 * A point has to be placed through [north], whose metres-per-degree constant is the mean-radius
 * approximation, while the geometry reads the haversine: the two agree to a fraction of a millimetre, so a
 * factor read **on** a band's own edge is pinned to a thousandth rather than to the last bit.
 */
class RouteMarkerCostTest {

    private val center = LatLng(43.5, 7.0)

    /** The tolerance a well factor is read to — the placement constant's own slack, over a 500 m collar. */
    private val factorTol = 1e-3

    /** The tolerance a priced cell's seconds are read to — [factorTol] scaled by the largest price here. */
    private val priceTol = 1e-2

    /**
     * A point [metresNorth] of [center] — the haversine's own metres-per-degree latitude
     * (`R·π/180` = 111 194.93 at WGS84's mean radius), so a pure latitude offset reads the exact metres.
     */
    private fun north(metresNorth: Double) = LatLng(43.5 + metresNorth / 111_194.93, 7.0)

    /** A point [metresEast] of [center] at 43.5° — the same planar read, on the long axis. */
    private fun east(metresEast: Double) = LatLng(43.5, 7.0 + metresEast / (111_320.0 * 0.7254))

    @Test
    fun aCircleContainsOnlyInsideItsBody() {
        val g = RouteMarkerGeometry.Circle(center, radiusM = 1000.0, proximityM = 500.0)
        assertTrue("the centre is inside", g.contains(center))
        assertTrue("999 m is inside", g.contains(north(999.0)))
        assertFalse("1001 m is in the collar, not the body", g.contains(north(1001.0)))
    }

    @Test
    fun theWellIsFullInTheBodyAndFadesToNothingAtTheCollar() {
        val g = RouteMarkerGeometry.Circle(center, radiusM = 1000.0, proximityM = 500.0)
        assertEquals("inside the body the well is full", 1.0, g.wellFactor(north(900.0)), factorTol)
        assertEquals("on the body's edge it is still full", 1.0, g.wellFactor(north(1000.0)), factorTol)
        assertEquals("mid collar it is half", 0.5, g.wellFactor(north(1250.0)), factorTol)
        assertEquals("on the outer edge it is nothing", 0.0, g.wellFactor(north(1500.0)), factorTol)
        assertEquals("beyond the collar it is nothing", 0.0, g.wellFactor(north(1600.0)), factorTol)
    }

    @Test
    fun aCirclesBoundaryIsTheOuterPricedEdge() {
        val g = RouteMarkerGeometry.Circle(center, radiusM = 1000.0, proximityM = 500.0)
        assertEquals(
            "on the outer edge the boundary distance is zero", 0.0, g.boundaryDistanceM(north(1500.0)), 2.0
        )
        assertEquals(
            "500 m beyond it the boundary is 500", 500.0, g.boundaryDistanceM(north(2000.0)), 2.0
        )
    }

    @Test
    fun aZeroCollarPricesTheBodyAlone() {
        val g = RouteMarkerGeometry.Circle(center, radiusM = 1000.0, proximityM = 0.0)
        assertEquals("inside the body it is still full", 1.0, g.wellFactor(north(900.0)), factorTol)
        assertEquals("and nothing just outside it", 0.0, g.wellFactor(north(1001.0)), factorTol)
        assertEquals("the boundary is the body's own edge", 0.0, g.boundaryDistanceM(north(1000.0)), 2.0)
    }

    @Test
    fun aCorridorContainsWithinItsHalfWidthCapsIncluded() {
        val a = LatLng(43.5, 7.0)
        val b = LatLng(43.5, 7.01)
        val g = RouteMarkerGeometry.Corridor(a, b, widthM = 400.0, proximityM = 500.0) // half-width 200 m
        assertTrue("on the centre line it is inside", g.contains(a))
        assertTrue("100 m beyond the b cap is still inside", g.contains(east(metresEast = corridorEastOf(b) + 100.0)))
        assertFalse("300 m beyond the b cap is outside", g.contains(east(metresEast = corridorEastOf(b) + 300.0)))
    }

    @Test
    fun aCorridorsCollarFadesOutwardFromItsHalfWidth() {
        val a = LatLng(43.5, 7.0)
        val b = LatLng(43.5, 7.01)
        val g = RouteMarkerGeometry.Corridor(a, b, widthM = 400.0, proximityM = 500.0)
        assertEquals("on the centre line the well is full", 1.0, g.wellFactor(a), factorTol)
        assertEquals("300 m off the line the collar is 200 m in", 0.8, g.wellFactor(north(300.0)), factorTol)
        assertEquals("700 m off the line the collar is spent", 0.0, g.wellFactor(north(700.0)), factorTol)
    }

    /** The metres east of [center] the point [b] stands at, so the cap offsets land beyond it. */
    private fun corridorEastOf(b: LatLng): Double = (b.longitude - 7.0) * (111_320.0 * 0.7254)

    @Test
    fun thePriceIsLinearInItsFactorAndNeverNegative() {
        assertEquals("a zero step pays nothing", 0.0, markerPriceSec(10.0, 0, 1.0, 1.0), 1e-9)
        assertEquals("base × step × strength × factor", 30.0, markerPriceSec(10.0, 3, 1.0, 1.0), 1e-9)
        assertEquals("the strength scales it", 15.0, markerPriceSec(10.0, 3, 0.5, 1.0), 1e-9)
        assertEquals("the well's factor scales it", 7.5, markerPriceSec(10.0, 3, 1.0, 0.25), 1e-9)
        assertEquals("the factor is floored at zero", 0.0, markerPriceSec(10.0, 3, 1.0, -1.0), 1e-9)
        assertEquals("and capped at one", 30.0, markerPriceSec(10.0, 3, 1.0, 2.0), 1e-9)
        assertTrue("the top step over a positive base is positive", markerPriceSec(10.0, 9, 5.0, 1.0) > 0.0)
    }

    @Test
    fun aPricedBodyPaysInFullAndFadesAcrossItsCollar() {
        val marker = RouteMarker(
            "m1", RouteMarkerGeometry.Circle(center, 1000.0, 500.0), step = 3, isWall = false
        )
        val field = RouteCostField(
            markerSources(listOf(marker), baseCostSec = 10.0, strength = 1.0, priceEnabled = true)
        )
        assertTrue("a priced marker makes the field a priced one", field.hasSoft)
        assertEquals(
            "inside the body the step is paid in full", 30.0, field.softPriceSecAt(north(900.0)), priceTol
        )
        assertEquals("mid collar half of it is paid", 15.0, field.softPriceSecAt(north(1250.0)), priceTol)
        assertEquals("beyond the collar nothing is", 0.0, field.softPriceSecAt(north(1600.0)), priceTol)
    }

    @Test
    fun aPinPricesAConeFromItsCentreToItsProximity() {
        // A Pin reaches the field as a body of radius zero, so its whole price is the collar.
        val pin = RouteMarker("p1", RouteMarkerGeometry.Circle(center, 0.0, 500.0), step = 3, isWall = false)
        val field = RouteCostField(
            markerSources(listOf(pin), baseCostSec = 10.0, strength = 1.0, priceEnabled = true)
        )
        assertTrue("a costed Pin prices the water", field.hasSoft)
        assertEquals("at the centre the cone is full", 30.0, field.softPriceSecAt(center), priceTol)
        assertEquals("half way out it is half", 15.0, field.softPriceSecAt(north(250.0)), priceTol)
        assertEquals("past the proximity it is free", 0.0, field.softPriceSecAt(north(600.0)), priceTol)
    }

    @Test
    fun aWallBlocksItsBodyAloneAndIgnoresThePriceSwitch() {
        val marker = RouteMarker(
            "w1", RouteMarkerGeometry.Circle(center, 1000.0, 500.0), step = 0, isWall = true
        )
        val field = RouteCostField(
            markerSources(listOf(marker), baseCostSec = 10.0, strength = 1.0, priceEnabled = false)
        )
        assertTrue("a wall makes the field a blocking one", field.hasBlocking)
        assertTrue("inside the body the cell is blocked", field.hardBlocked(center))
        assertFalse("the collar is a price, never a wall", field.hardBlocked(north(1200.0)))
        assertFalse("beyond the body it is not blocked", field.hardBlocked(north(2000.0)))
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
