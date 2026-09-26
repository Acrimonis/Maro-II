package ykws.android.maro.spatial.avoid

import kotlinx.coroutines.test.runTest
import kotlin.math.min
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.DepthSample
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.data.model.RouteResult
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.data.regulation.SpeedZone
import ykws.android.maro.spatial.RouteAvoidEngine
import ykws.android.maro.spatial.RouteEngineState
import ykws.android.maro.spatial.SpatialOperations
import ykws.android.maro.spatial.Units

/**
 * Phase 4: speed-limit zones as priced, excludable soft sources — the fill (holes stay water,
 * overlap keeps the strictest limit, land stays land), the price cursor, the zone-aware ETA and the
 * forced-crossing report.
 */
class RouteZonePhase4Test {

    // ── The fill ─────────────────────────────────────────────────────────────

    @Test
    fun zoneHolesStayWater() {
        val outer = squareRing(43.5, 7.03, 0.02)
        val hole = squareRing(43.5, 7.03, 0.005)
        val box = BBox(43.46, 43.54, 6.99, 7.07)
        val grid = rasterize(
            box, cellM = 50.0, marginM = 25.0,
            edges = emptyList(), openCoast = emptyList(), capLatNorth = 43.54,
            field = RouteCostField.EMPTY,
            zones = listOf(PricedZone(outer, listOf(hole), 20.0))
        )

        val inHole = grid.cellOf(43.5, 7.03)
        val inZone = grid.cellOf(43.5, 7.02)
        assertEquals("a cell inside the hole reads as water", AvoidCellState.FREE, grid.cell(inHole.row, inHole.col).state)
        assertEquals("a cell between the outer ring and the hole is tagged zone", AvoidCellState.ZONE, grid.cell(inZone.row, inZone.col).state)
    }

    @Test
    fun zoneOverlapKeepsTheStrictestLimit() {
        val outer = squareRing(43.5, 7.03, 0.02)
        val box = BBox(43.46, 43.54, 6.99, 7.07)
        val priceSlow = zonePriceM(50.0, paceKn = 28.0, limitKn = 5.0, k = 2.0)
        val priceFast = zonePriceM(50.0, paceKn = 28.0, limitKn = 10.0, k = 2.0)
        assertTrue("the slower limit is the dearer price", priceSlow > priceFast)

        val grid = rasterize(
            box, cellM = 50.0, marginM = 25.0,
            edges = emptyList(), openCoast = emptyList(), capLatNorth = 43.54,
            field = RouteCostField.EMPTY,
            zones = listOf(
                PricedZone(outer, emptyList(), priceSlow),
                PricedZone(outer, emptyList(), priceFast)
            )
        )

        val (row, col) = grid.cellOf(43.5, 7.03)
        val cell = grid.cell(row, col)
        assertEquals(AvoidCellState.ZONE, cell.state)
        assertEquals("the strictest limit wins, never the sum", 50.0 + priceSlow, cell.sourceCostM, 1e-6)
    }

    @Test
    fun landStaysLandUnderAZone() {
        val grid = AvoidGrid(
            latSouth = 43.0, lonWest = 7.0,
            cellSizeDegLat = 0.001, cellSizeDegLon = 0.001,
            rows = 10, cols = 10, cellM = 50.0
        )
        grid.markLand(5, 5)
        grid.applyZoneCost(5, 5, 25.0)
        assertEquals("a wall is never priced", AvoidCellState.LAND, grid.cell(5, 5).state)
    }

    @Test
    fun theFillAndThePointReadAgreeOnEveryCellCentre() {
        val outer = squareRing(43.5, 7.03, 0.02)
        val zone = SpeedZone("z", "Cap", 5.0, outer)
        val box = BBox(43.47, 43.53, 7.00, 7.06)
        val grid = rasterize(
            box, cellM = 50.0, marginM = 25.0,
            edges = emptyList(), openCoast = emptyList(), capLatNorth = 43.53,
            field = RouteCostField.EMPTY,
            zones = listOf(PricedZone(outer, emptyList(), 10.0))
        )

        // The rasterizer's scanline fill and `SpeedZone.contains` both use the same half-open latitude
        // rule, so a boundary vertex must fall the same way for the grid and the point read — this is
        // the fill's own cell centres checked against the predicate the ETA and the report read.
        var tagged = 0
        for (r in 0 until grid.rows) {
            for (c in 0 until grid.cols) {
                val centre = grid.center(r, c)
                val filled = grid.cell(r, c).state == AvoidCellState.ZONE
                val inside = zone.contains(centre.latitude, centre.longitude)
                assertEquals(
                    "cell ($r,$c) at ${centre.latitude}, ${centre.longitude}: fill and point read must agree",
                    inside, filled
                )
                if (filled) tagged++
            }
        }
        assertTrue("the zone must actually cover some cell centres", tagged > 0)
    }

    // ── The price cursor ──────────────────────────────────────────────────────

    @Test
    fun zonePriceAmplifiesWithKAndCostsMoreForASlowerLimit() {
        assertEquals("K=1 with the limit at the pace is open water", 0.0, zonePriceM(50.0, 28.0, 28.0, 1.0), 1e-9)
        assertEquals("a limit above the pace clamps to zero", 0.0, zonePriceM(50.0, 28.0, 40.0, 1.0), 1e-9)
        assertTrue(
            "a higher K makes the zone dearer",
            zonePriceM(50.0, 28.0, 5.0, 2.0) > zonePriceM(50.0, 28.0, 5.0, 1.0)
        )
        assertTrue(
            "a slower limit costs more",
            zonePriceM(50.0, 28.0, 5.0, 1.0) > zonePriceM(50.0, 28.0, 10.0, 1.0)
        )
    }

    // ── The ETA ───────────────────────────────────────────────────────────────

    @Test
    fun etaObeysTheLimitInsideAZone() {
        val a = LatLng(43.5, 7.00)
        val b = LatLng(43.5, 7.01)
        val dist = SpatialOperations.haversine(a, b)
        val timed = timeLineWithLimits(listOf(a, b), paceKn = 28.0, limitKnAt = { _ -> 5.0 })

        assertEquals(listOf(a, b), timed.points)
        assertEquals(1, timed.legTimesSec.size)
        assertEquals("the whole leg is timed at the 5 kn limit", dist / Units.knotsToMps(5.0), timed.legTimesSec[0], 1e-6)
    }

    @Test
    fun etaSplitsAtTheLimitChangeAndSumsItsLegs() {
        val a = LatLng(43.5, 7.00)
        val b = LatLng(43.5, 7.06)
        val limitKnAt: (LatLng) -> Double? = { p ->
            if (p.longitude in 7.02..7.04 && p.latitude in 43.49..43.51) 5.0 else null
        }
        val timed = timeLineWithLimits(listOf(a, b), paceKn = 28.0, limitKnAt = limitKnAt)

        assertEquals("two boundary crossings insert two vertices", 4, timed.points.size)
        assertEquals(3, timed.legTimesSec.size)
        assertEquals(timed.durationSec, timed.legTimesSec.sum(), 1e-9)
    }

    // ── Exclusion ─────────────────────────────────────────────────────────────

    @Test
    fun exclusionDropsAZoneFromTheFillAndTheLimitRead() {
        val zoneA = SpeedZone("a", "Zone A", 5.0, squareRing(43.5, 7.03, 0.02))
        val zoneB = SpeedZone("b", "Zone B", 10.0, squareRing(43.5, 7.03, 0.02))
        val box = BBox(43.4, 43.6, 6.9, 7.1)

        assertEquals(listOf(zoneB), speedZonesInBox(listOf(zoneA, zoneB), box, setOf("a")))
        assertEquals(10.0, strictestLimitKnAt(listOf(zoneA, zoneB), setOf("a"), 43.5, 7.03)!!, 1e-9)
        assertEquals("with none excluded the strictest limit survives", 5.0, strictestLimitKnAt(listOf(zoneA, zoneB), emptySet(), 43.5, 7.03)!!, 1e-9)
        assertNull(strictestLimitKnAt(listOf(zoneA, zoneB), emptySet(), 43.5, 6.99))
    }

    // ── Forced crossing ───────────────────────────────────────────────────────

    @Test
    fun forcedCrossingIsNamedOnlyWhenNoAvoidingPathExists() {
        val zone = SpeedZone("z", "Cap", 5.0, squareRing(43.5, 7.03, 0.02))
        val waypoints = listOf(LatLng(43.5, 7.00), LatLng(43.5, 7.06))

        assertEquals(listOf("Cap"), forcedCrossingZoneNames(waypoints, listOf(zone), avoidingPathExists = false))
        assertEquals(emptyList<String>(), forcedCrossingZoneNames(waypoints, listOf(zone), avoidingPathExists = true))
        val missed = SpeedZone("f", "Far", 5.0, squareRing(44.0, 7.03, 0.02))
        assertEquals(emptyList<String>(), forcedCrossingZoneNames(waypoints, listOf(missed), avoidingPathExists = false))
    }

    @Test
    fun aZoneBlockingTheWholeCorridorIsReportedAsAForcedCrossing() = runTest {
        setAvoidSwitch("routeAvoidSpeedZoneEnabled", true)
        val zone = SpeedZone("z", "Cap", 5.0, rectRing(43.45, 43.55, 7.015, 7.045))
        val world = ZoneWorld(listOf(zone))
        val engine = RouteAvoidEngine(paceKn = { 28.0 }, worldProvider = { world })

        engine.onOriginPositionChanged(RoutePoint(43.5, 7.00))
        val route = engine.onDestinationPositionChanged(RoutePoint(43.5, 7.06)) as RouteResult.Success

        assertEquals(listOf("Cap"), route.forcedCrossingZoneNames)
    }

    // ── The collar ────────────────────────────────────────────────────────────

    @Test
    fun theLineDoesNotEnterAZoneItCouldHaveGoneAround() = runTest {
        setAvoidSwitch("routeAvoidSpeedZoneEnabled", true)
        val zone = SpeedZone("z", "Cap", 5.0, rectRing(43.49, 43.51, 7.02, 7.04))
        val world = ZoneWorld(listOf(zone))
        val engine = RouteAvoidEngine(paceKn = { 28.0 }, worldProvider = { world })

        engine.onOriginPositionChanged(RoutePoint(43.5, 7.00))
        val route = engine.onDestinationPositionChanged(RoutePoint(43.5, 7.06)) as RouteResult.Success

        assertFalse(
            "the pulled line never enters a zone it could have gone around",
            lineEntersZone(route.points.map { it.toLatLng() }, zone)
        )
    }

    @Test
    fun thePassByKeepsTheStandoff() = runTest {
        setAvoidSwitch("routeAvoidSpeedZoneEnabled", true)
        setSpeedZoneMarginM(50.0)
        // A zone whose north edge lies ~20 m south of the straight origin→aim line: without the
        // collar the line stays 20 m off; with it the line stands off by the margin.
        val northEdge = 43.5 - 20.0 / 111_320.0
        val zone = SpeedZone("z", "Cap", 5.0, rectRing(northEdge - 0.02, northEdge, 7.01, 7.05))
        val world = ZoneWorld(listOf(zone))
        val engine = RouteAvoidEngine(paceKn = { 28.0 }, worldProvider = { world })

        engine.onOriginPositionChanged(RoutePoint(43.5, 7.00))
        val route = engine.onDestinationPositionChanged(RoutePoint(43.5, 7.06)) as RouteResult.Success

        val margin = AppConfig.routeAvoidSpeedZoneMarginM
        for (p in route.points) {
            val d = ringDistanceM(zone.outerRing, p.toLatLng())
            assertTrue("the line keeps the standoff off the ring (d=$d)", d >= margin - 5.0)
        }
    }

    @Test
    fun theStandoffFollowsTheMarginKey() = runTest {
        setAvoidSwitch("routeAvoidSpeedZoneEnabled", true)
        for (margin in listOf(40.0, 70.0)) {
            setSpeedZoneMarginM(margin)
            val northEdge = 43.5 - 20.0 / 111_320.0
            val zone = SpeedZone("z", "Cap", 5.0, rectRing(northEdge - 0.02, northEdge, 7.01, 7.05))
            val world = ZoneWorld(listOf(zone))
            val engine = RouteAvoidEngine(paceKn = { 28.0 }, worldProvider = { world })

            engine.onOriginPositionChanged(RoutePoint(43.5, 7.00))
            val route = engine.onDestinationPositionChanged(RoutePoint(43.5, 7.06)) as RouteResult.Success

            val kept = route.points.minOf { ringDistanceM(zone.outerRing, it.toLatLng()) }
            assertTrue(
                "the kept distance follows the key (margin=$margin, kept=$kept)",
                kept >= margin - 5.0
            )
        }
    }

    @Test
    fun aLineGrazingTheCollarIsTimedAtThePace() {
        val zone = SpeedZone("z", "Cap", 5.0, rectRing(43.49, 43.50, 7.01, 7.05))
        val limitKnAt: (LatLng) -> Double? = { p ->
            strictestLimitKnAt(listOf(zone), emptySet(), p.latitude, p.longitude)
        }
        val a = LatLng(43.505, 7.02)
        val b = LatLng(43.505, 7.04)
        val timed = timeLineWithLimits(listOf(a, b), paceKn = 28.0, limitKnAt = limitKnAt)
        val dist = SpatialOperations.haversine(a, b)

        assertEquals(
            "outside the zone the clock reads the pace, never the collar",
            dist / Units.knotsToMps(28.0),
            timed.legTimesSec[0],
            1e-6
        )
    }

    @Test
    fun theBandAndAZoneSumOnACellHoldingBoth() {
        val field = RouteCostField(
            listOf(
                RouteCostSource.Soft(priceM = { 30.0 }, tag = AvoidCellState.BAND),
                RouteCostSource.Soft(priceM = { 20.0 }, tag = AvoidCellState.ZONE)
            )
        )
        val at = field.evaluate(LatLng(43.5, 7.0))
        assertEquals("the two prices sum", 50.0, at.softCostM, 1e-9)
        assertEquals("the dearest tag wins", AvoidCellState.ZONE, at.tag)
    }

    @Test
    fun theCollarIsCheaperThanTheInterior() {
        val interior = zonePriceM(50.0, paceKn = 28.0, limitKn = 5.0, k = 5.0)
        val collar = zoneCollarPriceM(50.0, paceKn = 28.0, limitKn = 5.0, k = 5.0, collarFraction = 0.5)
        assertTrue("the collar is strictly cheaper, so the field carries a gradient", collar < interior)
        assertEquals("half the interior at a fraction of 0.5", interior * 0.5, collar, 1e-9)
    }

    @Test
    fun theCollarLimitReadsTheStrictestOfEveryCoveringZone() {
        val fast = SpeedZone("fast", "Fast", 10.0, rectRing(43.49, 43.51, 7.01, 7.05))
        val slow = SpeedZone("slow", "Slow", 5.0, rectRing(43.49, 43.51, 7.01, 7.05))
        // Two zones share one ring; the strictest limit must win whatever the iteration order, so the
        // collar cannot pick the nearer of two coincident rings and drop the stricter limit.
        assertEquals(
            "the strictest limit wins in the collar",
            5.0,
            speedZoneCollarLimitKnAt(listOf(fast, slow), emptySet(), 43.51, 7.03, 50.0)!!,
            1e-9
        )
    }

    @Test
    fun theLineKeepsOffTwoOverlappingZones() = runTest {
        setAvoidSwitch("routeAvoidSpeedZoneEnabled", true)
        // A slow 5 kn zone nested inside a fast 10 kn zone; a route crossing the pair must clear both,
        // the zones sized so the detour fits inside the corridor reach.
        val fast = SpeedZone("fast", "Fast", 10.0, rectRing(43.49, 43.51, 7.01, 7.05))
        val slow = SpeedZone("slow", "Slow", 5.0, rectRing(43.495, 43.505, 7.02, 7.04))
        val world = ZoneWorld(listOf(fast, slow))
        val engine = RouteAvoidEngine(paceKn = { 28.0 }, worldProvider = { world })

        engine.onOriginPositionChanged(RoutePoint(43.5, 7.00))
        val route = engine.onDestinationPositionChanged(RoutePoint(43.5, 7.06)) as RouteResult.Success

        val line = route.points.map { it.toLatLng() }
        assertFalse("the line clears the fast zone", lineEntersZone(line, fast))
        assertFalse("the line clears the slow zone", lineEntersZone(line, slow))
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @After
    fun restoreTheSpeedZoneSwitch() {
        setAvoidSwitch("routeAvoidSpeedZoneEnabled", false)
        setSpeedZoneMarginM(50.0)
    }

    /**
     * Flips an [AppConfig] avoid switch for one test. The fields ship `private set` — by design the values
     * change only through the properties load — so a test that must arm one reaches the backing field
     * directly and [restoreTheSpeedZoneSwitch] puts it back.
     */
    private fun setAvoidSwitch(name: String, value: Boolean) {
        val field = AppConfig::class.java.getDeclaredField(name)
        field.isAccessible = true
        field.setBoolean(AppConfig, value)
    }

    /** Flips the speed-zone standoff key for one test; [restoreTheSpeedZoneSwitch] puts 50 back. */
    private fun setSpeedZoneMarginM(value: Double) {
        val field = AppConfig::class.java.getDeclaredField("routeAvoidSpeedZoneMarginM")
        field.isAccessible = true
        field.setDouble(AppConfig, value)
    }

    /** Distance (m) from [p] to the nearest segment of a closed ring. */
    private fun ringDistanceM(ring: List<LatLng>, p: LatLng): Double {
        var best = Double.MAX_VALUE
        for (i in 0 until ring.size - 1) {
            best = min(best, SpatialOperations.pointToSegmentDistance(p, ring[i], ring[i + 1]))
        }
        return best
    }

    private fun squareRing(centerLat: Double, centerLon: Double, half: Double): List<LatLng> = listOf(
        LatLng(centerLat - half, centerLon - half),
        LatLng(centerLat - half, centerLon + half),
        LatLng(centerLat + half, centerLon + half),
        LatLng(centerLat + half, centerLon - half),
        LatLng(centerLat - half, centerLon - half)
    )

    private fun rectRing(latSouth: Double, latNorth: Double, lonWest: Double, lonEast: Double): List<LatLng> = listOf(
        LatLng(latSouth, lonWest),
        LatLng(latSouth, lonEast),
        LatLng(latNorth, lonEast),
        LatLng(latNorth, lonWest),
        LatLng(latSouth, lonWest)
    )

    /** A water-everywhere world whose only source is its speed zones — the corridor reads no land or depth. */
    private class ZoneWorld(private val zones: List<SpeedZone>) : AvoidWorld {
        override val coastlineReady: Boolean get() = true
        override val depthReady: Boolean get() = false
        override val bandWidthM: Double get() = 0.0
        override val regionBounds: BBox? get() = null

        override fun segmentsIn(box: BBox): List<AvoidEdge> = emptyList()
        override fun openCoastIn(box: BBox): List<List<LatLng>> = emptyList()
        override fun isWater(latitude: Double, longitude: Double): Boolean = true
        override fun distanceToCoastM(latitude: Double, longitude: Double): Double = Double.MAX_VALUE
        override fun depthAt(latitude: Double, longitude: Double): DepthSample = DepthSample.NONE
        override fun speedZonesIn(box: BBox): List<SpeedZone> = speedZonesInBox(zones, box, emptySet())
        override fun zoneLimitKnAt(latitude: Double, longitude: Double): Double? =
            strictestLimitKnAt(zones, emptySet(), latitude, longitude)
        override fun collarLimitKnAt(latitude: Double, longitude: Double, marginM: Double): Double? =
            speedZoneCollarLimitKnAt(zones, emptySet(), latitude, longitude, marginM)
        override suspend fun load(): RouteEngineState = RouteEngineState.Ready
    }
}
