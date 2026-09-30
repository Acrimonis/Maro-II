package ykws.android.maro.spatial.avoid

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
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
import ykws.android.maro.spatial.RouteDeclarations
import ykws.android.maro.spatial.RouteUpdate
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

    // ── The outside margin in the search ───────────────────────────────────────

    /**
     * **The collar is in the search.** A grid rasterized with a zone's outside margin prices the ring
     * around the zone — not only its interior — so the A* rounds a margin band it would otherwise cut
     * straight through. The pin is the pair: with the collar priced the path steps on no collar cell,
     * and with the collar price dropped to zero the same grid is crossed in a straight line. Revert the
     * grid's collar fill or the A*'s collar read and the priced leg no longer detours, so this goes red.
     */
    @Test
    fun theCollarIsPricedInTheSearch() = runTest {
        // A zone whose north edge runs under the straight start→aim line: the line stays outside the
        // interior but inside the 50 m outside margin, so the collar is the only price on its path.
        val zone = ZoneRing(rectRing(43.510, 43.511, 7.010, 7.030), emptyList(), 5.0)
        val box = BBox(43.505, 43.514, 6.995, 7.045)
        val grid = rasterize(
            box, cellM = 50.0, paceKn = 28.0, marginM = 25.0,
            edges = emptyList(), openCoast = emptyList(), capLatNorth = box.latNorth,
            field = RouteCostField.EMPTY,
            zones = listOf(zone),
            zoneOutsideMarginM = 50.0
        )
        val start = grid.cellOf(43.51105, 7.00)
        val aim = grid.cellOf(43.51105, 7.04)
        val paceMps = Units.knotsToMps(28.0)
        fun collarOnly(cell: CellIndex): Boolean =
            grid.zoneLimitKn(cell.row, cell.col) <= 0.0 && grid.collarLimitKn(cell.row, cell.col) > 0.0

        val priced = AvoidSearch.search(
            grid, start, aim, paceMps,
            zonePriceSec = { interiorKn, collarKn ->
                zonePriceAtLimits(50.0, 28.0, interiorKn, collarKn, 5.0, 0.66)
            }
        ).path

        assertTrue("a corridor still connects around the zone", priced != null)
        assertTrue(
            "the priced collar keeps the A* off every margin cell",
            priced!!.none { collarOnly(it) }
        )

        val free = AvoidSearch.search(grid, start, aim, paceMps, zonePriceSec = { _, _ -> 0.0 }).path
        assertTrue(
            "and the same grid, collar free, is crossed straight through the margin",
            free!!.any { collarOnly(it) }
        )
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
    fun aZoneBlockingTheWholeCorridorIsReportedAsAForcedCrossing() = runBlocking {
        setAvoidSwitch("routeAvoidSpeedZoneEnabled", true)
        val zone = SpeedZone("z", "Cap", 5.0, rectRing(43.40, 43.60, 7.015, 7.045))
        val world = ZoneWorld(listOf(zone))
        val engine = RouteAvoidEngine(paceKn = { 28.0 }, slowWaterBudgetPct = { 33 }, worldProvider = { world })

        val route = solve(engine, RoutePoint(43.5, 7.00), RoutePoint(43.5, 7.06))

        assertEquals(listOf("Cap"), route?.forcedCrossingZoneNames)
    }

    // ── The collar ────────────────────────────────────────────────────────────

    @Test
    fun theLineDoesNotEnterAZoneItCouldHaveGoneAround() = runBlocking {
        setAvoidSwitch("routeAvoidSpeedZoneEnabled", true)
        val zone = SpeedZone("z", "Cap", 5.0, rectRing(43.49, 43.51, 7.02, 7.04))
        val world = ZoneWorld(listOf(zone))
        val engine = RouteAvoidEngine(paceKn = { 28.0 }, slowWaterBudgetPct = { 33 }, worldProvider = { world })

        val route = solve(engine, RoutePoint(43.5, 7.00), RoutePoint(43.5, 7.06))

        assertFalse(
            "the pulled line never enters a zone it could have gone around",
            lineEntersZone(route!!.points.map { it.toLatLng() }, zone)
        )
    }

    /**
     * **The pull is purely priced (the standoff's retirement).** A ring twenty metres off the straight
     * chord no longer refuses it: the outside margin is a **price** in the search, never a clearance
     * here. The chord collapses to the two ends because the only walls left are the land margin and the
     * price guard. Revert the standoff's removal and this chord is refused again, so the pin goes red.
     */
    @Test
    fun thePullCarriesNoRingClearance() {
        val start = LatLng(43.50, 7.00)
        val aim = LatLng(43.50, 7.02)
        // A ring 20 m south of the chord's midpoint: inside the old 50 m standoff, a pure price now.
        val ring = LatLng(43.50 - 20.0 / 111_320.0, 7.01)
        val field = RouteCostField(listOf(RouteCostSource.Hard(distanceAt = { Double.MAX_VALUE })))
        val path = listOf(start, LatLng(43.50, 7.005), LatLng(43.50, 7.01), LatLng(43.50, 7.015), aim)

        val pulled = AvoidPull.pull(path, start, aim, marginM = 25.0, field)

        assertEquals(
            "a ring near the chord is no clearance: the chord is read taut, priced only",
            listOf(start, aim),
            pulled
        )
        assertTrue(
            "and the ring stands inside the old standoff's reach, so the pull never asked it",
            SpatialOperations.haversine(LatLng(43.50, 7.01), ring) < 50.0
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
        val collar = zoneCollarPriceSec(50.0, paceKn = 28.0, limitKn = 5.0, k = 5.0, costFraction = 0.66)
        assertTrue("the collar is strictly cheaper, so the field carries a gradient", collar < interior)
        assertEquals("0.66 of the interior at the shipped fraction", interior * 0.66, collar, 1e-9)
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

    /**
     * **The priced-band promise.** With the standoff gone, the only thing keeping the line off a zone is
     * the **price**: a shortcut chord through the zones' priced interiors costs more than the free path
     * around them, so the pull's price guard refuses it and the path survives. Revert the price guard and
     * the shortcut is taken, the line enters a zone, and this goes red.
     */
    @Test
    fun thePricedBandKeepsTheLineOffTheZones() {
        val fast = SpeedZone("fast", "Fast", 10.0, rectRing(43.49, 43.51, 7.01, 7.05))
        val slow = SpeedZone("slow", "Slow", 5.0, rectRing(43.495, 43.505, 7.02, 7.04))
        val zones = listOf(fast, slow)
        val lambda = 5.0
        val fraction = 0.66
        val field = RouteCostField(
            listOf(
                RouteCostSource.Hard(distanceAt = { Double.MAX_VALUE }),
                RouteCostSource.Soft(
                    priceSec = { p ->
                        val interior = strictestLimitKnAt(zones, emptySet(), p.latitude, p.longitude)
                        val collar = speedZoneCollarLimitKnAt(
                            zones, emptySet(), p.latitude, p.longitude, 50.0
                        )
                        zonePriceAtLimits(50.0, 28.0, interior ?: 0.0, collar ?: 0.0, lambda, fraction)
                    },
                    tag = AvoidCellState.ZONE
                )
            )
        )
        val start = LatLng(43.50, 7.00)
        val aim = LatLng(43.50, 7.06)
        // The free path: around the zones' north edge, every point outside every zone.
        val path = listOf(start, LatLng(43.52, 7.01), LatLng(43.52, 7.05), aim)

        val pulled = AvoidPull.pull(path, start, aim, marginM = 50.0, field)

        assertFalse("the line never enters the fast zone", lineEntersZone(pulled, fast))
        assertFalse("the line never enters the slow zone", lineEntersZone(pulled, slow))
        assertTrue(
            "the shortcut through the zones is refused by price, so the free path survives",
            pulled.size > 2
        )
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

    /** Flips the speed-zone outside-margin key for one test; [restoreTheSpeedZoneSwitch] puts 50 back. */
    private fun setSpeedZoneMarginM(value: Double) {
        val field = AppConfig::class.java.getDeclaredField("routeAvoidSpeedZoneOutsideMarginM")
        field.isAccessible = true
        field.setDouble(AppConfig, value)
    }

    /**
     * Arms the engine on a pair and awaits the main lookup's terminal update, returning its result —
     * `null` when the pair is refused or the search found no route.
     */
    private suspend fun solve(engine: RouteAvoidEngine, from: RoutePoint, to: RoutePoint): RouteResult.Success? = coroutineScope {
        val declarations = engine.routesToCompute(from, to)
        val available = declarations as? RouteDeclarations.Available ?: return@coroutineScope null
        val main = available.computations.first()
        val subscribed = CompletableDeferred<Unit>()
        val done = CompletableDeferred<RouteUpdate?>()
        val collector = launch(Dispatchers.Default) {
            engine.updates
                .onStart { subscribed.complete(Unit) }
                .collect { update ->
                    if (update.nextStage == null) { done.complete(update); return@collect }
                }
        }
        subscribed.await()
        engine.startLookup(main.id)
        val update = withTimeout(120_000) { done.await() }
        collector.cancel()
        update?.result
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

    // ── The budget ────────────────────────────────────────────────────────────

    /**
     * The loop's own exit, read where it is decided. A share **under** the band is met — a route
     * already spending less slow water than it may — so the correction, which only ever raises λ,
     * has nothing to chase; the band's top edge is still met; and above it the budget is missed.
     */
    @Test
    fun theBudgetIsMetUnderItsBandAndMissedOnlyAboveIt() {
        assertTrue("a share under the budget is met, and never chased", budgetMet(0.05, 33.0))
        assertTrue("an overrun inside the band is met", budgetMet(0.33, 33.0))
        assertTrue("the band's own predicate answers one reading of it", withinBudgetBand(0.30, 33.0))
        assertTrue("a share just inside the band's top edge is met", budgetMet(0.39, 33.0))
        assertFalse("above the band the budget is missed", budgetMet(0.40, 33.0))
        assertFalse("a zero budget is missed by any slow water at all", budgetMet(0.01, 0.0))
        assertTrue("and met by a line that spends none", budgetMet(0.0, 0.0))
    }

    /**
     * The verdict's own pin, and the loop's most visible consequence: the same forced crossing reports
     * the share it spent when the budget cannot accept it, and reports nothing when it can. A zero
     * budget also stops the loop after its first pass, so the two answers differ by the verdict alone.
     */
    @Test
    fun theBudgetVerdictIsReportedOnlyWhenItIsMissed() = runBlocking {
        setAvoidSwitch("routeAvoidSpeedZoneEnabled", true)
        val zone = SpeedZone("z", "Cap", 5.0, rectRing(43.40, 43.60, 7.015, 7.045))

        val crossed = RouteAvoidEngine(
            paceKn = { 28.0 },
            slowWaterBudgetPct = { 0 },
            worldProvider = { ZoneWorld(listOf(zone)) }
        )
        val unmet = solve(crossed, RoutePoint(43.5, 7.00), RoutePoint(43.5, 7.06))!!

        val allowed = RouteAvoidEngine(
            paceKn = { 28.0 },
            slowWaterBudgetPct = { 100 },
            worldProvider = { ZoneWorld(listOf(zone)) }
        )
        val met = solve(allowed, RoutePoint(43.5, 7.00), RoutePoint(43.5, 7.06))!!

        assertTrue(
            "a crossing under a zero budget reports the share it spent",
            (unmet.budgetUnmetZoneShare ?: 0.0) > 0.0
        )
        assertNull("and the same crossing is inside a full budget", met.budgetUnmetZoneShare)
    }

    /**
     * The probe's own mechanism, which §8's fix took off the second raster sweep: the copy that
     * answers "is there a way around at all?" blocks the zones **slower than the pace** and leaves
     * every other cell as it was, while the grid the search built keeps its limits untouched.
     */
    @Test
    fun theForcedCrossingProbeBlocksOnlyTheZonesBelowThePace() {
        val slow = ZoneRing(rectRing(43.49, 43.51, 7.02, 7.04), emptyList(), 5.0)
        val fast = ZoneRing(rectRing(43.49, 43.51, 7.06, 7.08), emptyList(), 30.0)
        val grid = rasterize(
            BBox(43.45, 43.55, 7.00, 7.10), cellM = 50.0, paceKn = 28.0, marginM = 25.0,
            edges = emptyList(), openCoast = emptyList(), capLatNorth = 43.55,
            field = RouteCostField.EMPTY,
            zones = listOf(slow, fast)
        )
        val inSlow = grid.cellOf(43.5, 7.03)
        val inFast = grid.cellOf(43.5, 7.07)
        val blocked = grid.blockedCopy(28.0)

        assertFalse(
            "the zone below the pace is blocked",
            blocked.cell(inSlow.row, inSlow.col).passable
        )
        assertTrue(
            "the zone at or above it is left open",
            blocked.cell(inFast.row, inFast.col).passable
        )
        assertTrue(
            "and the grid the search built is untouched",
            grid.cell(inSlow.row, inSlow.col).passable
        )
    }

    /** A water-everywhere world whose only source is its speed zones — the corridor reads no land or depth. */
    private class ZoneWorld(private val zones: List<SpeedZone>) : AvoidWorld {
        override val coastlineReady: Boolean get() = true
        override val depthReady: Boolean get() = true
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
    }
}
