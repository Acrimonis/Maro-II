package ykws.android.maro.spatial

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.After
import org.junit.Test
import java.io.File
import java.util.Properties
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.DepthSample
import ykws.android.maro.data.model.DepthSource
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.data.model.RouteResult
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.data.regulation.SpeedZone
import ykws.android.maro.spatial.avoid.AvoidEdge
import ykws.android.maro.spatial.avoid.AvoidWorld
import ykws.android.maro.spatial.avoid.bandReachM
import ykws.android.maro.spatial.avoid.EndApproaches
import ykws.android.maro.spatial.avoid.speedZonesInBox
import ykws.android.maro.spatial.avoid.strictestLimitKnAt
import ykws.android.maro.spatial.avoid.ZoneRing
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The avoid engine's own contract, pinned over a fake world: the invalid-end repair, the world
 * readiness refusal, the corridor search and the taut pull, the grow-once `NoPath` retry, and legs
 * timed at the injected pace.
 */
class RouteAvoidEngineTest {

    private val origin = RoutePoint(43.5000, 7.0000)
    private val aim = RoutePoint(43.5000, 7.0600)

    /** The pace the test injects, chosen on purpose: the expectation derives from this, never from a property. */
    private val paceKn = 12.0

    private val marginM = AppConfig.routeAvoidObstacleMarginM

    private fun newEngine(
        budgetPct: Int = AppConfig.routeAvoidSpeedZoneTimeBudgetPct,
        world: () -> AvoidWorld = { FakeWorld() }
    ) = RouteAvoidEngine(
        paceKn = { paceKn },
        slowWaterBudgetPct = { budgetPct },
        worldProvider = world
    )

    private fun success(result: RouteResult.Success?): RouteResult.Success {
        assertTrue("the engine answers a route", result != null)
        return result!!
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

    private fun shallowPatch(latitude: Double, longitude: Double): Boolean =
        latitude in 43.4995..43.5005 && longitude in 7.0210..7.0260

    /** The shipped `maro.properties`: the `app` module's CWD by default, `maro.repoDir` honoured first. */
    private val propertiesFile: File = System.getProperty("maro.repoDir")
        ?.let { File(it, "app/src/main/assets/maro.properties") }
        ?.takeIf { it.isFile }
        ?: File("src/main/assets/maro.properties")

    private fun shippedProperties(): Properties {
        assumeTrue("maro.properties not found", propertiesFile.isFile)
        return Properties().apply { propertiesFile.inputStream().use { load(it) } }
    }

    @After
    fun restoreAvoidSwitches() {
        setAvoidSwitch("routeAvoidDepthGateEnabled", true)
        setAvoidSwitch("routeAvoidZone300Enabled", true)
        setAvoidSwitch("routeAvoidSpeedZoneEnabled", false)
        setAvoidMarginM(25.0)
    }

    private fun setAvoidSwitch(name: String, value: Boolean) {
        val field = AppConfig::class.java.getDeclaredField(name)
        field.isAccessible = true
        field.setBoolean(AppConfig, value)
    }

    private fun setAvoidMarginM(value: Double) {
        val field = AppConfig::class.java.getDeclaredField("routeAvoidObstacleMarginM")
        field.isAccessible = true
        field.setDouble(AppConfig, value)
    }

    // ── The repair ─────────────────────────────────────────────────────────────

    /** An off-water end is moved to the nearest valid water — the repair's sea-side rule. */
    @Test
    fun theRepairMovesAnOffWaterEndToTheNearestWater() = runBlocking {
        // Water south of 43.5001, land north of it. The aim stands ~44 m north of the boundary, so the
        // ring sweep finds water south of it at the second ring (50 m).
        val world = FakeWorld(water = { lat, _ -> lat < 43.5001 })
        val engine = newEngine { world }

        val declarations = engine.routesToCompute(origin, RoutePoint(43.5005, 7.0300))

        assertTrue("the end is repaired rather than refused", declarations is RouteDeclarations.Available)
        val route = success(solve(engine, origin, RoutePoint(43.5005, 7.0300)))
        assertTrue(
            "the repaired destination stands on water",
            route.points.last().latitude < 43.5001
        )
    }

    /** A pair whose end cannot reach water within the sweep's radius is refused by name. */
    @Test
    fun aPairWhoseEndCannotBeRepairedIsRefused() = runBlocking {
        val world = FakeWorld(water = { lat, _ -> lat < 43.5001 })
        val engine = newEngine { world }

        val declarations = engine.routesToCompute(origin, RoutePoint(43.6000, 7.0300))

        assertEquals(
            "no valid water within 200 m is a refusal, not a route",
            RouteDeclarations.Refused(RouteReason.CANNOT_REPAIR),
            declarations
        )
    }

    /** A world whose coastline has not landed is refused by name — no gate, the status line says why. */
    @Test
    fun aWorldWithoutCoastlineIsRefusedByName() = runBlocking {
        val engine = newEngine { FakeWorld(ready = false) }

        assertEquals(
            RouteDeclarations.Refused(RouteReason.WORLD_NOT_READY),
            engine.routesToCompute(origin, aim)
        )
    }

    /** The repair's acceptance: it answers in under 50 ms for one point. */
    @Test
    fun theRepairAnswersInUnderFiftyMilliseconds() {
        val world = FakeWorld(water = { lat, _ -> lat < 43.5001 })
        val engine = newEngine { world }

        val start = System.nanoTime()
        engine.routesToCompute(origin, RoutePoint(43.5005, 7.0300))
        val elapsedMs = (System.nanoTime() - start) / 1_000_000

        assertTrue("the repair answers in $elapsedMs ms, under the 50 ms budget", elapsedMs <= 50)
    }

    // ── The pipeline ──────────────────────────────────────────────────────────

    /** Over empty water the pull collapses the corridor to the straight line, and the pin stands on the aim. */
    @Test
    fun aClearCrossingAnswersTheRawEndsUntouched() = runBlocking {
        val route = success(solve(newEngine(), origin, aim))

        assertEquals(listOf(origin, aim), route.points)
        assertFalse(route.destinationMoved)
        assertTrue(route.forcedCrossingZoneNames.isEmpty())
        val straight = SpatialOperations.haversine(origin.toLatLng(), aim.toLatLng())
        assertEquals(straight, route.distanceM, 1e-6)
    }

    @Test
    fun theRouteAvoidsAnIslandAndKeepsTheMargin() = runBlocking {
        val island = circleRing(LatLng(43.5000, 7.0300), radiusM = 400.0)
        val world = FakeWorld(edges = island.toMutableList())
        val route = success(solve(newEngine { world }, origin, aim))

        assertEquals("the line starts at the raw start", origin, route.points.first())
        assertEquals("and ends at the raw aim", aim, route.points.last())
        for (point in route.points) {
            assertTrue(
                "every waypoint keeps the clearance off the island",
                world.distanceToCoastM(point.latitude, point.longitude) >= marginM - 1e-6
            )
        }
        val straight = SpatialOperations.haversine(origin.toLatLng(), aim.toLatLng())
        assertTrue("the detour is longer than the straight line", route.distanceM > straight)
    }

    /** A land wall crossing the corridor exhausts A*, retries once with the doubled reach, then refuses by name. */
    @Test
    fun exhaustionAnswersNoPathAndRetriesWithDoubledReach() = runBlocking {
        val wall = polygonRing(
            listOf(
                LatLng(43.40, 7.02),
                LatLng(43.40, 7.04),
                LatLng(43.60, 7.04),
                LatLng(43.60, 7.02)
            )
        )
        val world = FakeWorld(edges = wall.toMutableList())
        val route = solve(newEngine { world }, origin, aim)

        assertTrue("no path connects the ends through the wall", route == null)
        assertEquals("one pass plus one doubled-reach retry", 2, world.boxes.size)
        val firstSpan = world.boxes[0].latNorth - world.boxes[0].latSouth
        val retrySpan = world.boxes[1].latNorth - world.boxes[1].latSouth
        assertTrue("the retry's corridor reach is doubled", retrySpan > firstSpan)
    }

    /** A forced crossing grows the reach and keeps the way around. */
    @Test
    fun aForcedCrossingGrowsTheReachAndKeepsTheWayAround() = runBlocking {
        setAvoidSwitch("routeAvoidSpeedZoneEnabled", true)
        val zone = SpeedZone("z", "Cap", 5.0, rectRing(43.45, 43.55, 7.015, 7.045))
        val world = FakeWorld(zones = listOf(zone))
        val route = success(solve(newEngine(budgetPct = 100) { world }, origin, aim))

        assertEquals("the forced crossing earns the doubled reach", 2, world.boxes.size)
        assertTrue("the grown answer's way around is kept", route.forcedCrossingZoneNames.isEmpty())
    }

    @Test
    fun legsAreTimedAtTheInjectedPace() = runBlocking {
        val route = success(solve(newEngine(), origin, aim))

        val straight = SpatialOperations.haversine(origin.toLatLng(), aim.toLatLng())
        val seconds = straight / Units.knotsToMps(paceKn)
        assertEquals(listOf(seconds), route.legTimesSec)
        assertEquals(seconds, route.durationSec, 1e-9)
    }

    @Test
    fun theReportedDurationIsTheSumOfTheSavedLegTimes() = runBlocking {
        val island = circleRing(LatLng(43.5000, 7.0300), radiusM = 400.0)
        val route = success(solve(newEngine { FakeWorld(edges = island.toMutableList()) }, origin, aim))

        assertEquals("one leg time per drawn leg", route.points.size - 1, route.legTimesSec.size)
        assertEquals(
            "the saved legs sum to the reported duration",
            route.durationSec,
            route.legTimesSec.sum(),
            1e-6
        )
    }

    /** A concave bay: a peninsula jutting south from the north coast; the line must round its tip. */
    @Test
    fun aConcaveBayWithAPeninsulaRoutesAroundTheTip() = runBlocking {
        val coast = listOf(
            LatLng(43.51, 6.98),
            LatLng(43.51, 7.08)
        )
        val peninsula = polygonRing(
            listOf(
                LatLng(43.51, 7.025),
                LatLng(43.51, 7.035),
                LatLng(43.485, 7.035),
                LatLng(43.485, 7.025)
            )
        )
        val world = FakeWorld(edges = peninsula.toMutableList(), openCoast = mutableListOf(coast))
        val route = success(solve(newEngine { world }, origin, aim))

        val straight = SpatialOperations.haversine(origin.toLatLng(), aim.toLatLng())
        assertTrue("the detour around the peninsula is longer than the straight line", route.distanceM > straight)
        for (point in route.points) {
            assertTrue(
                "every waypoint keeps the clearance off the peninsula",
                world.distanceToCoastM(point.latitude, point.longitude) >= marginM - 1e-6
            )
        }
        assertTrue(
            "the route passes south of the peninsula tip rather than across it",
            route.points.minOf { it.latitude } < 43.485
        )
    }

    /** A convex headland must bend at exactly the two offset tangent corners, both ways. */
    @Test
    fun aConvexHeadlandBendsAtTheTwoOffsetTangentCornersBothWays() = runBlocking {
        val coast = listOf(
            LatLng(43.51, 7.00),
            LatLng(43.51, 7.02),
            LatLng(43.49, 7.02),
            LatLng(43.49, 7.04),
            LatLng(43.51, 7.04),
            LatLng(43.51, 7.06)
        )
        val world = FakeWorld(openCoast = mutableListOf(coast))
        val west = RoutePoint(43.50, 7.00)
        val east = RoutePoint(43.50, 7.06)

        val mPerDegLat = SpatialOperations.EARTH_RADIUS_M * PI / 180.0
        val mPerDegLon = mPerDegLat * cos(Math.toRadians(43.49))
        val dLat = marginM / mPerDegLat
        val dLon = marginM / mPerDegLon
        val sw = LatLng(43.49 - dLat, 7.02 - dLon)
        val se = LatLng(43.49 - dLat, 7.04 + dLon)

        assertHeadlandBend(newEngine { world }, world, west, east, sw, se)
        assertHeadlandBend(newEngine { world }, world, east, west, se, sw)
    }

    private suspend fun assertHeadlandBend(
        engine: RouteAvoidEngine,
        world: FakeWorld,
        from: RoutePoint,
        to: RoutePoint,
        firstCorner: LatLng,
        secondCorner: LatLng
    ) {
        val route = success(solve(engine, from, to))

        assertEquals("the line starts at the raw start", from, route.points.first())
        assertEquals("and ends at the raw aim", to, route.points.last())
        assertTrue(
            "the first bend is faired around the first offset tangent corner",
            route.points.minOf { SpatialOperations.haversine(it.toLatLng(), firstCorner) } < 30.0
        )
        assertTrue(
            "the second bend is faired around the second offset tangent corner",
            route.points.minOf { SpatialOperations.haversine(it.toLatLng(), secondCorner) } < 30.0
        )
        for (i in 0 until route.points.size - 1) {
            val a = route.points[i].toLatLng()
            val b = route.points[i + 1].toLatLng()
            val dist = SpatialOperations.haversine(a, b)
            val steps = max(2, ceil(dist / (marginM / 2.0)).toInt())
            for (s in 1 until steps) {
                val t = s.toDouble() / steps
                val p = LatLng(
                    a.latitude + (b.latitude - a.latitude) * t,
                    a.longitude + (b.longitude - a.longitude) * t
                )
                if (SpatialOperations.haversine(p, from.toLatLng()) < marginM) continue
                if (SpatialOperations.haversine(p, to.toLatLng()) < marginM) continue
                assertTrue(
                    "every segment interior stays at least the margin off the headland",
                    world.distanceToCoastM(p.latitude, p.longitude) >= marginM - 1e-6
                )
            }
        }
    }

    /** A digitized coast with many small convex teeth must not become a zigzag. */
    @Test
    fun aDigitizedCoastProducesACleanTautLineNotAZigzag() = runBlocking {
        val coast = sawtoothCoast(40)
        val world = FakeWorld(openCoast = mutableListOf(coast))
        val route = success(solve(newEngine { world }, RoutePoint(43.48, 6.999), RoutePoint(43.48, 7.081)))

        assertTrue(
            "the 40-tooth coast collapses to a clean line, not a per-tooth zigzag",
            sharpCorners(route.points) <= 2
        )
        for (point in route.points) {
            assertTrue(
                "every waypoint keeps the clearance off the teeth",
                world.distanceToCoastM(point.latitude, point.longitude) >= marginM - 1e-6
            )
        }
    }

    private fun sharpCorners(points: List<RoutePoint>, minDeg: Double = 20.0): Int {
        var count = 0
        for (i in 1 until points.size - 1) {
            val before = Math.toDegrees(
                Math.toRadians(
                    SpatialOperations.initialBearing(points[i - 1].toLatLng(), points[i].toLatLng())
                )
            )
            val after = Math.toDegrees(
                Math.toRadians(
                    SpatialOperations.initialBearing(points[i].toLatLng(), points[i + 1].toLatLng())
                )
            )
            val turn = (after - before + 540.0) % 360.0 - 180.0
            if (abs(turn) >= minDeg) count++
        }
        return count
    }

    /** The Lérins-to-Salis-shaped corridor answers under the 500 ms wall the plan pins. */
    @Test
    fun theLerinsToSalisShapedCorridorAnswersUnderTheBudget() = runBlocking {
        val engine = newEngine { lerinsToSalisWorld() }

        val start = System.nanoTime()
        val route = solve(engine, RoutePoint(43.508, 7.065), RoutePoint(43.572, 7.114))
        val elapsedMs = (System.nanoTime() - start) / 1_000_000

        success(route)
        assertTrue("the corridor answers in $elapsedMs ms, under the 500 ms wall", elapsedMs <= 500)
    }

    // ── The depth gate ─────────────────────────────────────────────────────────

    @Test
    fun theRouteRoundsWaterTheDepthGateCallsTooShallow() = runBlocking {
        val world = FakeWorld(depth = { lat, lon -> if (shallowPatch(lat, lon)) 2.0 else 20.0 })
        val route = success(solve(newEngine { world }, origin, aim))

        val straight = SpatialOperations.haversine(origin.toLatLng(), aim.toLatLng())
        assertTrue("the gate sends the line round the patch", route.distanceM > straight)
        for (point in route.points) {
            assertFalse(
                "no waypoint stands in the water the gate forbids",
                shallowPatch(point.latitude, point.longitude)
            )
        }
    }

    @Test
    fun aShallowPatchWithoutASoundingIsIgnored() = runBlocking {
        val world = FakeWorld(depth = { _, _ -> Double.NaN })
        val route = success(solve(newEngine { world }, origin, aim))

        assertEquals(
            "unsurveyed water is not gated, so the line stays straight",
            listOf(origin, aim),
            route.points
        )
    }

    // ── The avoid switches ─────────────────────────────────────────────────────

    @Test
    fun theAvoidSwitchesDefaultOn() {
        assertTrue("the depth gate ships armed", AppConfig.routeAvoidDepthGateEnabled)
        assertTrue("the 300 m band ships armed", AppConfig.routeAvoidZone300Enabled)
    }

    @Test
    fun theSpeedZoneSwitchShipsDisarmed() {
        assertFalse("the speed-zone source ships disarmed", AppConfig.routeAvoidSpeedZoneEnabled)
    }

    @Test
    fun depthGateOffDeclaresWithTheDepthGridAbsent() = runBlocking {
        setAvoidSwitch("routeAvoidDepthGateEnabled", false)
        val engine = newEngine { FakeWorld(ready = true, depthLoaded = false) }

        assertTrue(
            "the gate off declares with no depth grid",
            engine.routesToCompute(origin, aim) is RouteDeclarations.Available
        )
    }

    @Test
    fun depthGateOffPaintsNoGateCell() = runBlocking {
        setAvoidSwitch("routeAvoidDepthGateEnabled", false)
        val world = FakeWorld(depth = { lat, lon -> if (shallowPatch(lat, lon)) 2.0 else 20.0 })
        val route = success(solve(newEngine { world }, origin, aim))

        assertEquals("the gate off prices the shallow patch as open water", listOf(origin, aim), route.points)
    }

    @Test
    fun zone300OffRoutesThroughTheBandAtOpenWaterCost() = runBlocking {
        val coast = listOf(LatLng(43.52, 6.98), LatLng(43.52, 7.08))
        val start = RoutePoint(43.518, 7.00)
        val aim = RoutePoint(43.518, 7.06)
        val straight = SpatialOperations.haversine(start.toLatLng(), aim.toLatLng())

        setAvoidSwitch("routeAvoidZone300Enabled", true)
        val on = success(solve(newEngine { FakeWorld(band = 300.0, openCoast = mutableListOf(coast)) }, start, aim))
        assertTrue("the priced band bends the line offshore", on.distanceM > straight + 10.0)

        setAvoidSwitch("routeAvoidZone300Enabled", false)
        val flat = success(solve(newEngine { FakeWorld(band = 300.0, openCoast = mutableListOf(coast)) }, start, aim))
        assertEquals("the band off prices the water as open sea", listOf(start, aim), flat.points)
        assertEquals(straight, flat.distanceM, 1e-6)
    }

    @Test
    fun speedZoneOffPricesTheZoneAsOpenWaterAndItsArmedControlDoesNot() = runBlocking {
        val zone = SpeedZone("z", "Cap", 5.0, rectRing(43.40, 43.60, 7.015, 7.045))
        val straight = SpatialOperations.haversine(origin.toLatLng(), aim.toLatLng())

        setAvoidSwitch("routeAvoidSpeedZoneEnabled", false)
        val flat = success(solve(newEngine { FakeWorld(zones = listOf(zone)) }, origin, aim))
        assertEquals("the switch off prices the zone as open water", listOf(origin, aim), flat.points)
        assertTrue("and names no forced crossing", flat.forcedCrossingZoneNames.isEmpty())

        setAvoidSwitch("routeAvoidSpeedZoneEnabled", true)
        val armed = success(solve(newEngine { FakeWorld(zones = listOf(zone)) }, origin, aim))
        assertEquals(
            "the same world armed prices the zone and names the crossing",
            listOf("Cap"),
            armed.forcedCrossingZoneNames
        )
    }

    // ── The 300 m band ─────────────────────────────────────────────────────────

    @Test
    fun aStartInsideTheBandIsPricedNotRefused() = runBlocking {
        val coast = listOf(LatLng(43.52, 6.98), LatLng(43.52, 7.08))
        val world = FakeWorld(band = 10_000.0, openCoast = mutableListOf(coast))
        val route = success(solve(newEngine { world }, origin, aim))

        assertEquals("a priced band is a price, never a wall", listOf(origin, aim), route.points)
    }

    // ── The fine-cell ratio, shipped and unread until Change 4 ───────────────────

    @Test
    fun theFineCellRatioShipsAtFortyPercentOfTheCoarseCell() {
        val raw = shippedProperties().getProperty("route.avoid.fine.cellRatio")
        assertNotNull("maro.properties must carry route.avoid.fine.cellRatio", raw)
        val shipped = raw!!.trim().toDouble()

        assertEquals("the file and the code carry one value", AppConfig.routeAvoidFineCellRatio, shipped, 1e-9)
        assertEquals("the user's 40 % of the coarse cell", 0.40, shipped, 1e-9)
        assertEquals(
            "and a 20 m fine cell at today's 50 m coarse cell",
            20.0,
            AppConfig.routeAvoidGridCellM * shipped,
            1e-9
        )
    }

    // ── The crossing's line-end re-solve (D3) ──────────────────────────────────

    @Test
    fun theCrossingReSolveReachesTheLinesEnds() = runBlocking {
        setAvoidSwitch("routeAvoidSpeedZoneEnabled", true)
        val zone = SpeedZone("z", "Cap", 5.0, rectRing(43.49, 43.51, 7.02, 7.04))
        val world = FakeWorld(zones = listOf(zone))
        val engine = newEngine { world }
        val start = LatLng(43.50, 7.00)
        val p1 = LatLng(43.50, 7.015)
        val p2 = LatLng(43.50, 7.025)
        val aimLat = LatLng(43.50, 7.035)
        val corridor = BBox(43.40, 43.60, 6.90, 7.20)
        val priced = listOf(ZoneRing(zone.outerRing, zone.holes, zone.speedLimitKn))
        val line = listOf(start, p1, p2, aimLat)

        val spliced = engine.solveCrossing(
            world = world,
            corridor = corridor,
            line = line,
            zone = zone,
            start = start,
            aim = aimLat,
            pace = 28.0,
            cellM = 50.0,
            fineCellM = 20.0,
            marginM = 50.0,
            outsideMarginM = 50.0,
            lambda = 5.0,
            edges = emptyList(),
            openCoast = emptyList(),
            capLatNorth = corridor.latNorth,
            priced = priced,
            zones = listOf(zone),
            sets = emptyList(),
            approaches = EndApproaches.NONE,
            refusals = null
        )

        assertNotNull("the crossing touching the line's end is re-solved, not refused", spliced)
        assertEquals("the splice keeps the head", start, spliced!!.first())
        assertEquals("and ends on the raw aim", aimLat, spliced.last())
        assertTrue("the fine search rebuilt the in-box stretch", spliced.size > line.size)
    }

    // ── The fake world ─────────────────────────────────────────────────────────

    private class FakeWorld(
        private var ready: Boolean = true,
        private var depthLoaded: Boolean = true,
        /** The priced band's width (m) — 0 by default, so a test that is not about the band pays none. */
        private val band: Double = 0.0,
        private val edges: MutableList<AvoidEdge> = mutableListOf(),
        private val openCoast: MutableList<List<LatLng>> = mutableListOf(),
        private val water: (Double, Double) -> Boolean = { _, _ -> true },
        /** The sounding (m) the depth layer answers, or `NaN` for an unsurveyed point. */
        private val depth: (Double, Double) -> Double = { _, _ -> Double.NaN },
        /** The speed zones the world answers, priced only while the engine's switch is armed. */
        private val zones: List<SpeedZone> = emptyList()
    ) : AvoidWorld {
        val boxes = mutableListOf<BBox>()

        override val coastlineReady: Boolean get() = ready
        override val depthReady: Boolean get() = depthLoaded
        override val bandWidthM: Double get() = band
        override val regionBounds: BBox? get() = null

        override fun segmentsIn(box: BBox): List<AvoidEdge> {
            boxes.add(box)
            return edges.filter { edge ->
                val minLat = min(edge.a.latitude, edge.b.latitude)
                val maxLat = max(edge.a.latitude, edge.b.latitude)
                val minLon = min(edge.a.longitude, edge.b.longitude)
                val maxLon = max(edge.a.longitude, edge.b.longitude)
                minLat <= box.latNorth && maxLat >= box.latSouth &&
                    minLon <= box.lonEast && maxLon >= box.lonWest
            }
        }

        override fun openCoastIn(box: BBox): List<List<LatLng>> = openCoast

        override fun speedZonesIn(box: BBox): List<SpeedZone> = speedZonesInBox(zones, box, emptySet())

        override fun zoneLimitKnAt(latitude: Double, longitude: Double): Double? =
            strictestLimitKnAt(zones, emptySet(), latitude, longitude)

        override fun isWater(latitude: Double, longitude: Double): Boolean = water(latitude, longitude)

        override fun depthAt(latitude: Double, longitude: Double): DepthSample {
            val sounding = depth(latitude, longitude)
            return if (sounding.isNaN()) DepthSample.NONE
            else DepthSample(sounding.toFloat(), DepthSource.LITTO3D, 100, true)
        }

        override fun distanceToCoastM(latitude: Double, longitude: Double): Double {
            var best = Double.MAX_VALUE
            val p = LatLng(latitude, longitude)
            for (edge in edges) {
                best = min(best, SpatialOperations.pointToSegmentDistance(p, edge.a, edge.b))
            }
            for (polyline in openCoast) {
                for (i in 0 until polyline.size - 1) {
                    best = min(best, SpatialOperations.pointToSegmentDistance(p, polyline[i], polyline[i + 1]))
                }
            }
            return best
        }
    }

    /** A closed rectangle ring over `[latSouth, latNorth]` × `[lonWest, lonEast]`. */
    private fun rectRing(
        latSouth: Double,
        latNorth: Double,
        lonWest: Double,
        lonEast: Double
    ): List<LatLng> = listOf(
        LatLng(latSouth, lonWest),
        LatLng(latSouth, lonEast),
        LatLng(latNorth, lonEast),
        LatLng(latNorth, lonWest),
        LatLng(latSouth, lonWest)
    )

    private fun polygonRing(points: List<LatLng>): List<AvoidEdge> =
        points.zipWithNext().map { (a, b) -> AvoidEdge(a, b, LandRingOrientation.CCW_RING) } +
            AvoidEdge(points.last(), points.first(), LandRingOrientation.CCW_RING)

    /** A horizontal coast at 43.51 with [teeth] downward triangles; land is the north side. */
    private fun sawtoothCoast(teeth: Int): List<LatLng> {
        val topLat = 43.51
        val tipLat = 43.45
        val x0 = 7.00
        val width = 0.002
        val pts = ArrayList<LatLng>()
        pts.add(LatLng(topLat, x0))
        for (k in 0 until teeth) {
            val a = x0 + k * width
            val b = a + width / 2.0
            val c = a + width
            pts.add(LatLng(tipLat, b))
            pts.add(LatLng(topLat, c))
        }
        return pts
    }

    private fun circleRing(center: LatLng, radiusM: Double, n: Int = 32): List<AvoidEdge> {
        val mPerDegLat = SpatialOperations.EARTH_RADIUS_M * PI / 180.0
        val mPerDegLon = mPerDegLat * cos(Math.toRadians(center.latitude))
        val polygon = (0 until n).map { i ->
            val theta = 2.0 * PI * i / n
            LatLng(
                center.latitude + radiusM * sin(theta) / mPerDegLat,
                center.longitude + radiusM * cos(theta) / mPerDegLon
            )
        }
        return polygonRing(polygon)
    }

    /** A synthetic Lérins-to-Salis corridor: a mainland coast with a string of island rings between the ends. */
    private fun lerinsToSalisWorld(): FakeWorld {
        val coast = listOf(
            LatLng(43.58, 7.00),
            LatLng(43.58, 7.06),
            LatLng(43.58, 7.12)
        )
        val islands = circleRing(LatLng(43.512, 7.072), radiusM = 500.0, n = 24) +
            circleRing(LatLng(43.520, 7.088), radiusM = 650.0, n = 24) +
            circleRing(LatLng(43.535, 7.102), radiusM = 420.0, n = 24) +
            circleRing(LatLng(43.550, 7.095), radiusM = 360.0, n = 24)
        return FakeWorld(edges = islands.toMutableList(), openCoast = mutableListOf(coast))
    }
}
