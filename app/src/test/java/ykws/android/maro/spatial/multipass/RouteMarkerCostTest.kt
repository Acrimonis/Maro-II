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

    /** **The one price law** — a single multiply, `base × factor`, never an addition to re-spell (D2). */
    @Test
    fun thePriceIsTheBaseTimesTheFactor() {
        assertEquals("a zero factor pays nothing", 0.0, markerPriceSec(10.0, 0.0), 1e-9)
        assertEquals("base × factor", 30.0, markerPriceSec(10.0, 3.0), 1e-9)
        assertEquals("a fractional factor scales it", 2.5, markerPriceSec(10.0, 0.25), 1e-9)
        assertTrue("the top step over a positive base is positive", markerPriceSec(10.0, 45.0) > 0.0)
    }

    /**
     * **The one factor law** — the pace-free `m` [`markerFactorAt`] sums, one `step × strength ×
     * wellFactor` a priced marker, the wall never counted and the switch standing the price down.
     */
    @Test
    fun theFactorSumsEachPricedMarkersStepScale() {
        val marker = RouteMarker(
            "m1", RouteMarkerGeometry.Circle(center, 1000.0, 500.0), step = 3, isWall = false
        )
        assertEquals(
            "inside the body one marker's factor is its step × strength",
            3.0, markerFactorAt(listOf(marker), strength = 1.0, enabled = true, p = center), 1e-9
        )
        assertEquals(
            "the strength scales the factor",
            1.5, markerFactorAt(listOf(marker), strength = 0.5, enabled = true, p = center), 1e-9
        )
        assertEquals(
            "mid collar the well halves it",
            1.5, markerFactorAt(listOf(marker), strength = 1.0, enabled = true, p = north(1250.0)),
            3.0 * factorTol
        )
        assertEquals(
            "the well spends itself at the outer edge",
            0.0, markerFactorAt(listOf(marker), strength = 1.0, enabled = true, p = north(1600.0)), 1e-9
        )
        assertEquals(
            "the switch stands the price down",
            0.0, markerFactorAt(listOf(marker), strength = 1.0, enabled = false, p = center), 1e-9
        )
        val wall = RouteMarker("w1", RouteMarkerGeometry.Circle(center, 1000.0, 500.0), step = 0, isWall = true)
        assertEquals(
            "a wall is never priced",
            0.0, markerFactorAt(listOf(wall), strength = 1.0, enabled = true, p = center), 1e-9
        )
        assertEquals(
            "two markers sum",
            6.0,
            markerFactorAt(
                listOf(marker, marker.copy(id = "m2")), strength = 1.0, enabled = true, p = center
            ),
            1e-9
        )
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

    /**
     * **The marker's own arm** — a point a priced marker charges reports [PriceArm.MARKER] alone, carrying
     * that point's own seconds and never the `OTHER` residue, so a price report can say the marker charged
     * it. The arms it lists sum to what the field charges, and a point beyond every collar reports none.
     */
    @Test
    fun aPricedPointReportsTheMarkerArmAloneAndTheArmsSumToTheTotal() {
        val marker = RouteMarker(
            "m1", RouteMarkerGeometry.Circle(center, 1000.0, 500.0), step = 3, isWall = false
        )
        val field = RouteCostField(
            markerSources(listOf(marker), baseCostSec = 10.0, strength = 1.0, priceEnabled = true)
        )
        val inside = field.armPricesAt(north(900.0))
        assertEquals("one arm charges the body", 1, inside.size)
        assertEquals("and it is the marker", PriceArm.MARKER, inside.single().first)
        assertEquals("carrying the point's own seconds", 30.0, inside.single().second, priceTol)
        assertFalse("no `OTHER` residue stands beside it", inside.any { it.first == PriceArm.OTHER })
        assertEquals(
            "the arms sum to the price the field charges",
            30.0, inside.sumOf { it.second }, priceTol
        )
        val mid = field.armPricesAt(north(1250.0))
        assertEquals("mid collar the marker still names itself", PriceArm.MARKER, mid.single().first)
        assertEquals("the arms still sum to the field's price", 15.0, mid.sumOf { it.second }, priceTol)
        assertEquals(
            "and the sum is the field's own answer", field.softPriceSecAt(north(1250.0)),
            mid.sumOf { it.second }, priceTol
        )
        assertTrue("beyond every collar no arm is reported", field.armPricesAt(north(1600.0)).isEmpty())
    }

    /** **The switch silences the arm with the price** — no source, so no arm and no `OTHER` residue. */
    @Test
    fun theMarkerArmIsSilentWhenThePriceIsSwitchedOff() {
        val marker = RouteMarker(
            "m1", RouteMarkerGeometry.Circle(center, 1000.0, 500.0), step = 3, isWall = false
        )
        val field = RouteCostField(
            markerSources(listOf(marker), baseCostSec = 10.0, strength = 1.0, priceEnabled = false)
        )
        assertTrue("with the price off no arm is reported at all", field.armPricesAt(center).isEmpty())
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
