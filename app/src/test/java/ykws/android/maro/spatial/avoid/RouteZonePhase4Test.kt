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
            box, cellM = 50.0, paceKn = 28.0, marginM = 25.0,
            edges = emptyList(), openCoast = emptyList(), capLatNorth = 43.54,
            field = RouteCostField.EMPTY,
            zones = listOf(ZoneRing(outer, listOf(hole), 5.0))
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
        val priceSlow = zonePriceSec(50.0, paceKn = 28.0, limitKn = 5.0, k = 2.0)
        val priceFast = zonePriceSec(50.0, paceKn = 28.0, limitKn = 10.0, k = 2.0)
        assertTrue("the slower limit is the dearer price", priceSlow > priceFast)

        val grid = rasterize(
            box, cellM = 50.0, paceKn = 28.0, marginM = 25.0,
            edges = emptyList(), openCoast = emptyList(), capLatNorth = 43.54,
            field = RouteCostField.EMPTY,
            zones = listOf(
                ZoneRing(outer, emptyList(), 5.0),
                ZoneRing(outer, emptyList(), 10.0)
            )
        )

        val (row, col) = grid.cellOf(43.5, 7.03)
        val cell = grid.cell(row, col)
        assertEquals(AvoidCellState.ZONE, cell.state)
        assertEquals(
            "the strictest limit is the one in force, never a sum",
            5.0, grid.zoneLimitKn(row, col), 1e-9
        )
        assertEquals("and the cell carries no price of its own — the A* prices the limit", grid.baseCostSec, cell.sourceCostSec, 1e-9)
    }

    @Test
    fun landStaysLandUnderAZone() {
        val grid = AvoidGrid(
            latSouth = 43.0, lonWest = 7.0,
            cellSizeDegLat = 0.001, cellSizeDegLon = 0.001,
            rows = 10, cols = 10, cellM = 50.0, baseCostSec = baseCostSec(50.0, 28.0)
        )
        grid.markLand(5, 5)
        grid.applyZoneLimit(5, 5, 5.0)
        assertEquals("a wall is never priced", AvoidCellState.LAND, grid.cell(5, 5).state)
    }

    @Test
    fun theFillAndThePointReadAgreeOnEveryCellCentre() {
        val outer = squareRing(43.5, 7.03, 0.02)
        val zone = SpeedZone("z", "Cap", 5.0, outer)
        val box = BBox(43.47, 43.53, 7.00, 7.06)
        val grid = rasterize(
            box, cellM = 50.0, paceKn = 28.0, marginM = 25.0,
            edges = emptyList(), openCoast = emptyList(), capLatNorth = 43.53,
            field = RouteCostField.EMPTY,
            zones = listOf(ZoneRing(outer, emptyList(), 10.0))
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
        assertEquals("K=1 with the limit at the pace is open water", 0.0, zonePriceSec(50.0, 28.0, 28.0, 1.0), 1e-9)
        assertEquals("a limit above the pace clamps to zero", 0.0, zonePriceSec(50.0, 28.0, 40.0, 1.0), 1e-9)
        assertTrue(
            "a higher K makes the zone dearer",
            zonePriceSec(50.0, 28.0, 5.0, 2.0) > zonePriceSec(50.0, 28.0, 5.0, 1.0)
        )
        assertTrue(
            "a slower limit costs more",
            zonePriceSec(50.0, 28.0, 5.0, 1.0) > zonePriceSec(50.0, 28.0, 10.0, 1.0)
        )
        assertEquals(
            "and K=1 at a 5 kn limit is the cell's own time excess",
            50.0 / Units.knotsToMps(5.0) - 50.0 / Units.knotsToMps(28.0),
            zonePriceSec(50.0, 28.0, 5.0, 1.0),
            1e-9
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

    @Test
    fun theDecelIsPaidBeforeTheBoundary() {
        // 28 kn into a 5 kn ring across 43.503 N: the leg that ends on the boundary is the outer one,
        // so that is where the boat slows — 28 kn to 5 kn at 0.5 m/s² is about 200 m and 24 s.
        val a = LatLng(43.500, 7.030)
        val b = LatLng(43.506, 7.030)
        val limitKnAt: (LatLng) -> Double? = { p -> if (p.latitude >= 43.503) 5.0 else null }
        val timed = timeLineWithLimits(listOf(a, b), paceKn = 28.0, limitKnAt = limitKnAt)
        val paceMps = Units.knotsToMps(28.0)
        val limitMps = Units.knotsToMps(5.0)
        val outerM = SpatialOperations.haversine(timed.points[0], timed.points[1])
        val innerM = SpatialOperations.haversine(timed.points[1], timed.points[2])
        val decelSec = (paceMps - limitMps) / 0.5

        assertEquals("the boundary vertex splits the line in two", 3, timed.points.size)
        assertTrue(
            "the outer leg pays the decel before the ring",
            timed.legTimesSec[0] > outerM / paceMps + decelSec - 1.0
        )
        assertEquals(
            "so the boat enters the ring already at the limit and cruises it",
            innerM / limitMps,
            timed.legTimesSec[1],
            1e-6
        )
        val steeper = timeLineWithLimits(listOf(a, b), paceKn = 28.0, limitKnAt = limitKnAt, accelMps2 = 1.0)
        assertTrue(
            "and the rate is the key's: a steeper brake finishes sooner and crawls longer",
            steeper.legTimesSec[0] > timed.legTimesSec[0]
        )
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

    /**
     * The standoff's own pin, at the level it now lives. §4 moved it out of the search — where the
     * grid's collar price used to push the A* line off a ring — and into the pull as a **clearance**,
     * so the promise is made here: a chord coming within the standoff of a ring is refused, and the
     * land margin beside it is untouched.
     */
    @Test
    fun thePullRefusesAChordWithinTheStandoff() {
        val start = LatLng(43.50, 7.00)
        val aim = LatLng(43.50, 7.02)
        // A ring 20 m south of the straight chord's midpoint: inside a 50 m standoff, outside a zero one.
        val ring = LatLng(43.50 - 20.0 / 111_320.0, 7.01)
        val field = RouteCostField(
            listOf(RouteCostSource.Hard(distanceAt = { Double.MAX_VALUE })),
            ringDistanceAt = { p -> SpatialOperations.haversine(p, ring) }
        )

        assertFalse(
            "a chord passing inside the standoff is refused",
            AvoidPull.legClear(start, aim, 25.0, field, start, aim, standoffM = 50.0)
        )
        assertTrue(
            "and the same chord stands when no standoff is asked for",
            AvoidPull.legClear(start, aim, 25.0, field, start, aim, standoffM = 0.0)
        )
    }

    /**
     * **What the standoff cannot do, stated rather than hidden.** The zone's north edge lies ~20 m
     * south of the straight line, so **no** chord can keep a 50 m standoff off it: every chord is
     * refused and the line keeps the search's own path — §4's own sentence, *it degrades to the free
     * path in a tens-of-metres passage*. The zone itself is still never entered, which
     * `theLineDoesNotEnterAZoneItCouldHaveGoneAround` above pins. **Read this again when §5's fine
     * pass lands**: if the corridor can widen there, the standoff stops degrading and this test must
     * be restated to the stronger promise rather than kept as a tripwire for the weaker one.
     */
    @Test
    fun theStandoffDegradesWhereNoChordCouldKeepIt() = runTest {
        setAvoidSwitch("routeAvoidSpeedZoneEnabled", true)
        setSpeedZoneMarginM(50.0)
        val northEdge = 43.5 - 20.0 / 111_320.0
        val zone = SpeedZone("z", "Cap", 5.0, rectRing(northEdge - 0.02, northEdge, 7.01, 7.05))
        val world = ZoneWorld(listOf(zone))
        val engine = RouteAvoidEngine(paceKn = { 28.0 }, worldProvider = { world })

        engine.onOriginPositionChanged(RoutePoint(43.5, 7.00))
        val route = engine.onDestinationPositionChanged(RoutePoint(43.5, 7.06)) as RouteResult.Success

        val nearest = route.points.minOf { ringDistanceM(zone.outerRing, it.toLatLng()) }
        assertTrue(
            "the passage is narrower than the standoff, so the standoff degrades (nearest=$nearest)",
            nearest < AppConfig.routeAvoidSpeedZoneMarginM
        )
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
                RouteCostSource.Soft(priceSec = { 30.0 }, tag = AvoidCellState.BAND),
                RouteCostSource.Soft(priceSec = { 20.0 }, tag = AvoidCellState.ZONE)
            )
        )
        val at = field.evaluate(LatLng(43.5, 7.0))
        assertEquals("the two prices sum", 50.0, at.softCostSec, 1e-9)
        assertEquals("the dearest tag wins", AvoidCellState.ZONE, at.tag)
    }

    @Test
    fun theCollarIsCheaperThanTheInterior() {
        val interior = zonePriceSec(50.0, paceKn = 28.0, limitKn = 5.0, k = 5.0)
        val collar = zoneCollarPriceSec(50.0, paceKn = 28.0, limitKn = 5.0, k = 5.0, collarFraction = 0.5)
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
