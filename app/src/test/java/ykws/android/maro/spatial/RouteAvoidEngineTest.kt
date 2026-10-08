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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.After
import org.junit.Test
import java.io.File
import java.util.Collections
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import ykws.android.maro.R
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.DepthSample
import ykws.android.maro.data.model.DepthSource
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.data.model.RouteResult
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.data.regulation.SpeedZone
import ykws.android.maro.spatial.multipass.MultipassEdge
import ykws.android.maro.spatial.multipass.MultipassWorld
import ykws.android.maro.spatial.multipass.bandReachM
import ykws.android.maro.spatial.multipass.EndApproaches
import ykws.android.maro.spatial.multipass.EvolutiveGridPlan
import ykws.android.maro.spatial.multipass.FineWater
import ykws.android.maro.spatial.multipass.FineWaterQuery
import ykws.android.maro.spatial.multipass.GridTile
import ykws.android.maro.spatial.multipass.RouteFinePass
import ykws.android.maro.spatial.multipass.RouteGridPlan
import ykws.android.maro.spatial.multipass.RoutePassRanking
import ykws.android.maro.spatial.multipass.UniformGridPlan
import ykws.android.maro.spatial.multipass.insideBandWidthM
import ykws.android.maro.spatial.multipass.speedZonesInBox
import ykws.android.maro.spatial.multipass.strictestLimitKnAt
import ykws.android.maro.spatial.multipass.TimedLine
import ykws.android.maro.spatial.multipass.ZoneRing
import ykws.android.maro.spatial.multipass.zoneSlowShare
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
        world: () -> MultipassWorld = { FakeWorld() }
    ) = RouteAvoidEngine(
        paceKn = { paceKn },
        aversionKn = { AppConfig.routeAvoidSpeedZoneSoftCostAversion },
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
    private suspend fun solve(
        engine: RouteAvoidEngine,
        from: RoutePoint,
        to: RoutePoint,
        rungIndex: Int = 0
    ): RouteResult.Success? = coroutineScope {
        val declarations = engine.routesToCompute(from, to)
        val available = declarations as? RouteDeclarations.Available ?: return@coroutineScope null
        val computation = available.computations[rungIndex]
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
        engine.startLookup(computation.id)
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
        setAvoidMarginM(50.0)
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
        val route = success(solve(newEngine(budgetPct = 100) { world }, origin, aim, rungIndex = 0))

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
            "the first bend snaps onto the first offset tangent corner",
            route.points.minOf { SpatialOperations.haversine(it.toLatLng(), firstCorner) } < 30.0
        )
        assertTrue(
            "the second bend snaps onto the second offset tangent corner",
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
                    world.distanceToCoastM(p.latitude, p.longitude) >= marginM - 0.01
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

        val corners = sharpCorners(route.points)
        assertTrue(
            "the 40-tooth coast collapses to a few snapped corners, not a per-tooth zigzag (sharp corners: $corners)",
            corners <= 8
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

    /**
     * The cape acceptance, with a **magnitude**: the band's limit, the pace and λ derive the exchange
     * rate the search is expected to honour — a band metre is worth `(pace / limit − 1) × λ` detour
     * metres — and the priced route's detour must stay within the worth of the band water its straight
     * line spends. The rate, the worth and the band stretch are all named in the assertion, so the bend
     * can neither vanish nor drift silently.
     */
    @Test
    fun zone300OffRoutesThroughTheBandAtOpenWaterCost() = runBlocking {
        val coast = listOf(LatLng(43.52, 6.98), LatLng(43.52, 7.08))
        val start = RoutePoint(43.518, 7.00)
        val aim = RoutePoint(43.518, 7.06)
        val straight = SpatialOperations.haversine(start.toLatLng(), aim.toLatLng())

        setAvoidSwitch("routeAvoidZone300Enabled", true)
        val world = FakeWorld(band = 300.0, openCoast = mutableListOf(coast))
        val on = success(solve(newEngine { world }, start, aim, rungIndex = 0))
        val rate = (paceKn / AppConfig.routeAvoidZone300LimitKn - 1.0) * 5.0
        val straightBandM = bandWidthMetres(world, listOf(start.toLatLng(), aim.toLatLng()))
        val worthM = rate * straightBandM
        assertTrue(
            "the straight line lies inside the band's width, so its stretch is the price's own water",
            straightBandM > straight * 0.99
        )
        assertTrue("the priced band bends the line offshore", on.distanceM > straight)
        assertTrue(
            "the bend (${on.distanceM - straight} m) honours the exchange rate: at most ${worthM} m, the " +
                "worth of the ${straightBandM} m of band water at $rate detour metres per band metre",
            on.distanceM - straight <= worthM + 1.0
        )

        setAvoidSwitch("routeAvoidZone300Enabled", false)
        val flat = success(solve(newEngine { FakeWorld(band = 300.0, openCoast = mutableListOf(coast)) }, start, aim))
        assertEquals("the band off prices the water as open sea", listOf(start, aim), flat.points)
        assertEquals(straight, flat.distanceM, 1e-6)
    }

    /** The metres of a line whose own middle stands inside the band's width — the engine's own test. */
    private fun bandWidthMetres(world: FakeWorld, points: List<LatLng>): Double {
        var total = 0.0
        for (i in 0 until points.size - 1) {
            val mid = LatLng(
                (points[i].latitude + points[i + 1].latitude) / 2.0,
                (points[i].longitude + points[i + 1].longitude) / 2.0
            )
            if (insideBandWidthM(world.distanceToCoastM(mid.latitude, mid.longitude), world.bandWidthM)) {
                total += SpatialOperations.haversine(points[i], points[i + 1])
            }
        }
        return total
    }

    @Test
    fun theZoneLimitSlowsTheClockWhateverThePriceSwitchSays() = runBlocking {
        val zone = SpeedZone("z", "Cap", 5.0, rectRing(43.40, 43.60, 7.015, 7.045))
        val straight = SpatialOperations.haversine(origin.toLatLng(), aim.toLatLng())
        val paceMps = Units.knotsToMps(paceKn)
        val limitMps = Units.knotsToMps(5.0)

        setAvoidSwitch("routeAvoidSpeedZoneEnabled", false)
        val flat = success(solve(newEngine { FakeWorld(zones = listOf(zone)) }, origin, aim))
        assertEquals(
            "the switch off prices the zone as open water: the straight line is drawn through it",
            4,
            flat.points.size
        )
        assertTrue("the approach leg eases down toward the zone", flat.legSpeedsMps[0] < paceMps - 1e-9)
        assertEquals("the interior leg rides at the enforced limit", limitMps, flat.legSpeedsMps[1], 1e-9)
        assertTrue("the exit leg climbs back toward the pace", flat.legSpeedsMps[2] < paceMps - 1e-9)
        assertTrue("so the clock is slower than open water", flat.durationSec > straight / paceMps)
        assertTrue("and names no forced crossing", flat.forcedCrossingZoneNames.isEmpty())

        setAvoidSwitch("routeAvoidSpeedZoneEnabled", true)
        val armed = success(solve(newEngine { FakeWorld(zones = listOf(zone)) }, origin, aim, rungIndex = 0))
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

    /**
     * **The band's limit is law, not price.** A leg standing inside the band's own width is timed at
     * the band's limit **whatever the price switch says** — `route.avoid.zone300.enabled` prices water,
     * it never suspends the limit in force (D8). The leg is straight and wholly inside the band, so the
     * clock's own arithmetic is the expectation: one boundary split, one cruise at the limit.
     *
     * **Its slow time is the band's, not a zone's.** All of the line's slowness lies in the band's width,
     * so the **zone** share — the quantity the budget is keyed on — is zero, and the answer reports no
     * unmet budget however slow the clock has become. The band's share lives on the trace instead.
     */
    @Test
    fun theBandLimitSlowsTheClockWhateverThePriceSwitchSays() = runBlocking {
        val coast = listOf(LatLng(43.52, 6.98), LatLng(43.52, 7.08))
        val start = RoutePoint(43.518, 7.00)
        val aim = RoutePoint(43.518, 7.06)
        val dist = SpatialOperations.haversine(start.toLatLng(), aim.toLatLng())
        val bandLimitKn = AppConfig.routeAvoidZone300LimitKn
        val bandSec = dist / Units.knotsToMps(bandLimitKn)
        val paceSec = dist / Units.knotsToMps(paceKn)

        assertTrue(
            "the pin is a real difference: the band's limit is slower than the injected pace",
            bandSec > paceSec * 1.5
        )

        setAvoidSwitch("routeAvoidZone300Enabled", false)
        val off = success(solve(newEngine { FakeWorld(band = 300.0, openCoast = mutableListOf(coast)) }, start, aim))

        assertEquals("the price off, the line is the straight one", listOf(start, aim), off.points)
        assertEquals("and the clock still pays the band's own limit", bandSec, off.durationSec, 1e-6)
        assertNull(
            "a line whose slowness is all band reports zone share 0, so the budget is not unmet",
            off.budgetUnmetZoneShare
        )
    }

    // ── The avoid fine cell, shipped in metres ──────────────────────────────────

    @Test
    fun theAvoidFineCellShipsInMetres() {
        val props = shippedProperties()
        val raw = props.getProperty("route.avoid.grid.fineCellM")
        assertNotNull("maro.properties must carry route.avoid.grid.fineCellM", raw)
        val shipped = raw!!.trim().toDouble()

        assertEquals("the file and the code carry one value", AppConfig.routeAvoidGridFineCellM, shipped, 1e-9)
        assertEquals("one third of the 100 m coarse cell", 100.0 / 3.0, shipped, 1e-3)
        assertEquals(
            "and the coarse cell ships at 100 m",
            100.0,
            props.getProperty("route.avoid.grid.cellM")!!.trim().toDouble(),
            1e-9
        )
        assertNull(
            "the dead ratio key is gone",
            props.getProperty("route.avoid.grid.fineRatio")
        )
    }

    // ── The step readings (the seam's own instrumentation) ─────────────────────

    /**
     * Every stage that counts something says so on the update it closes — the search's two counts at the
     * boundary that follows it, and the pull's own point count at the boundary after that. The reading
     * belongs to `stageDone`, never to `nextStage`: a stage reports what it *did*.
     */
    @Test
    fun everyStageReportsItsOwnFiguresOnTheUpdate() = runBlocking {
        val engine = newEngine()
        val declared = engine.routesToCompute(origin, aim) as? RouteDeclarations.Available
        assertNotNull("the fixture's pair declares its rung", declared)

        val reported = ArrayList<Pair<RouteStage?, List<RouteStepReading>>>()
        val subscribed = CompletableDeferred<Unit>()
        val done = CompletableDeferred<Unit>()
        val collector = launch(Dispatchers.Default) {
            engine.updates
                .onStart { subscribed.complete(Unit) }
                .collect { update ->
                    reported += update.stageDone to update.readings
                    if (update.nextStage == null) {
                        done.complete(Unit)
                        return@collect
                    }
                }
        }
        subscribed.await()
        engine.startLookup(declared!!.computations[0].id)
        withTimeout(120_000) { done.await() }
        collector.cancel()

        val atSearch = reported.firstOrNull { it.first == RouteStage.SEARCH }?.second
        assertNotNull("the update closing the search carries its figures", atSearch)
        assertEquals("the search reports its two counts", 2, atSearch!!.size)
        assertEquals(
            "and they are the expansions and the passable cells",
            listOf(R.string.route_reading_expansions, R.string.route_reading_passable_cells),
            atSearch.map { it.labelResId }
        )
        assertTrue(
            "both counted in cells",
            atSearch.all { it.unitResId == R.string.route_unit_cells }
        )
        assertTrue("with figures a user could read", atSearch.all { it.value >= 0.0 })

        val atPull = reported.firstOrNull { it.first == RouteStage.PULL }?.second
        assertNotNull("the update closing the pull carries its own", atPull)
        assertEquals("one figure: the pulled points", 1, atPull!!.size)
        assertEquals(R.string.route_reading_pulled_points, atPull.first().labelResId)
        assertEquals(
            "counted in points",
            R.string.route_unit_points,
            atPull.first().unitResId
        )
    }

    // ── The plan seam: the engine's own injection point ────────────────────────

    /** A plan that behaves exactly like the shipped one and counts how often the engine consults it. */
    private class CountingPlan(private val inner: RouteGridPlan = UniformGridPlan) : RouteGridPlan {
        var cellReads = 0
        var fineReads = 0

        override val name: String get() = inner.name

        override fun firstWalkGrid(corridor: BBox, baseCellM: Double): List<GridTile> {
            cellReads++
            return inner.firstWalkGrid(corridor, baseCellM)
        }

        override fun fineCellM(baseCellM: Double): Double {
            fineReads++
            return inner.fineCellM(baseCellM)
        }

        override fun fineWater(query: FineWaterQuery): FineWater = inner.fineWater(query)
    }

    /**
     * The plan is the engine's **whole** difference from a second algorithm: a lookup asks it for the
     * walk's cell and for the finest cell its clock steps at, and nothing else about the pipeline moves.
     */
    @Test
    fun aLookupTakesItsCellAndItsFineCellFromThePlan() = runBlocking {
        val plan = CountingPlan()
        val engine = RouteAvoidEngine(
            paceKn = { paceKn },
            aversionKn = { AppConfig.routeAvoidSpeedZoneSoftCostAversion },
            slowWaterBudgetPct = { AppConfig.routeAvoidSpeedZoneTimeBudgetPct },
            worldProvider = { FakeWorld() },
            plan = plan
        )

        success(solve(engine, origin, aim))

        assertTrue("the first walk asks the plan for its cell", plan.cellReads > 0)
        assertTrue("and the clock asks it for the finest cell", plan.fineReads > 0)
    }

    // ── The cancel disposal (D21) ──────────────────────────────────────────────

    /**
     * **A mid-arm candidate cancel leaves the arm's one build and its main intact (D21).**
     *
     * The arm's three rungs share one fine build (the engine's per-arm holder): the first to reach it
     * rasterises and the rest await the same `Deferred`. Cancelling **one candidate** while that build is
     * under way must not take the build down with it — the engine disposes the shared build only when the
     * arm's **last** lookup ends, so the main and the surviving candidate still read the one build and the
     * main still reaches its terminal with a line. `GRID layer=fine` is emitted once per build, so the
     * injected sink counts builds: a second build would show as two. A **second arm** on the same pair then
     * rebuilds rather than awaiting the first arm's disposed `Deferred` — the per-arm holder's freshness.
     *
     * **The plan is `EvolutiveGridPlan`, not the shipped `UniformGridPlan` (D29).** The shipped `avoid`
     * plan answers one tile and emits no `GRID layer=fine`, so the build count this test reads would be
     * zero; the adaptive plan's two layers give the fine build a line to count.
     *
     * **The gate.** The disposal's counter is what makes this hold: a completion disposes the shared build
     * only when the arm's in-flight count reaches zero. Reverted to cancel on **any** completion, the
     * cancelled candidate takes the build down while the main awaits it, so the main never reaches its
     * terminal — which is the failure this test exists to catch. The gate is deterministic (D28): the
     * build's own `CORRIDOR` trace hook parks the build thread until the test has delivered the cancel, so
     * the cancel always lands while the rasterise is unfinished rather than racing the build's completion.
     * The park is **bounded** (D31) — the hook waits on the latch with a timeout, as this test's other
     * awaits are, so a test-logic failure before the countdown can never park a `Dispatchers.Default`
     * worker for good; the bound elapsing fails the build loudly (D35) rather than resuming it silently.
     */
    @Test
    fun aCancelledCandidateLeavesTheArmsOneBuildAndItsMainIntact() = runBlocking {
        val coast = listOf(LatLng(43.52, 6.98), LatLng(43.52, 7.08))
        val world = FakeWorld(band = 300.0, openCoast = mutableListOf(coast))
        val captured = Collections.synchronizedList(ArrayList<String>())
        // The build thread signals it has reached the corridor and then parks until the test has delivered
        // the cancel: the rasterise is provably unfinished when `cancelLookup` runs, however the machine
        // schedules the two, so the disposal gate turns on construction rather than on a timing margin
        // (D28). The park is bounded (D31), so a misfire before the countdown cannot strand the worker.
        val buildStarted = CompletableDeferred<Unit>()
        val cancelDelivered = CountDownLatch(1)
        val engine = RouteAvoidEngine(
            paceKn = { paceKn },
            aversionKn = { AppConfig.routeAvoidSpeedZoneSoftCostAversion },
            slowWaterBudgetPct = { AppConfig.routeAvoidSpeedZoneTimeBudgetPct },
            worldProvider = { world },
            plan = EvolutiveGridPlan,
            traceSink = { line ->
                captured += line
                if (line.startsWith("CORRIDOR")) {
                    buildStarted.complete(Unit)
                    // Honour the bound (D35): a silent resume would let the build run on and the gate pass
                    // without the cancel ever having been delivered, which is the one thing this hook exists
                    // to prevent — so a timeout fails the build loudly instead of dropping the boolean.
                    if (!cancelDelivered.await(120_000, TimeUnit.MILLISECONDS)) {
                        throw IllegalStateException("the D21 gate's cancel was not delivered within 120 s")
                    }
                }
            }
        )

        val declared = engine.routesToCompute(origin, aim) as? RouteDeclarations.Available
        assertNotNull("the fixture pair declares its three rungs", declared)
        val computations = declared!!.computations

        val subscribed = CompletableDeferred<Unit>()
        val armDone = CompletableDeferred<Unit>()
        val terminals = ConcurrentHashMap<RouteId, RouteUpdate>()
        val collector = launch(Dispatchers.Default) {
            engine.updates
                .onStart { subscribed.complete(Unit) }
                .collect { update ->
                    if (update.nextStage == null) {
                        terminals[update.routeId] = update
                        // The main and the surviving candidate land; a cancelled one never emits.
                        if (terminals.size == 2) armDone.complete(Unit)
                    }
                }
        }
        subscribed.await()
        val mainId = engine.startLookup(computations[0].id)
        val cancelledId = engine.startLookup(computations[1].id)
        engine.startLookup(computations[2].id)

        // Cancel one candidate with the build provably parked mid-flight: the hook blocks the build thread
        // at `CORRIDOR` until this line has delivered the cancel, so the `Deferred` the disposal would
        // cancel exists and the rasterise is unfinished when `cancelLookup` runs (D28).
        withTimeout(120_000) { buildStarted.await() }
        engine.cancelLookup(cancelledId)
        cancelDelivered.countDown()
        withTimeout(120_000) { armDone.await() }
        collector.cancel()

        assertNotNull("the main still reaches its terminal with a line", terminals[mainId]?.result)
        assertEquals(
            "the arm's one build served the surviving rungs",
            1,
            captured.count { it.startsWith("GRID layer=fine") }
        )

        // The second arm on the same pair rebuilds: a fresh holder, so it never awaits the first arm's
        // disposed `Deferred`. The fine layer is counted over this arm's own sink lines.
        captured.clear()
        val secondDeclared = engine.routesToCompute(origin, aim) as? RouteDeclarations.Available
        assertNotNull("the second arm declares its rungs", secondDeclared)

        val secondSubscribed = CompletableDeferred<Unit>()
        val secondMainDone = CompletableDeferred<RouteUpdate>()
        var secondMainId: RouteId? = null
        val secondCollector = launch(Dispatchers.Default) {
            engine.updates
                .onStart { secondSubscribed.complete(Unit) }
                .collect { update ->
                    if (update.nextStage == null && update.routeId == secondMainId) {
                        secondMainDone.complete(update)
                    }
                }
        }
        secondSubscribed.await()
        secondMainId = engine.startLookup(secondDeclared!!.computations[0].id)
        val secondMain = withTimeout(120_000) { secondMainDone.await() }
        secondCollector.cancel()

        assertNotNull("the second arm's main reaches its terminal", secondMain.result)
        assertEquals(
            "the second arm rebuilds rather than awaiting the first arm's disposed Deferred",
            1,
            captured.count { it.startsWith("GRID layer=fine") }
        )
    }

    // ── The failed build's terminal (D36) ──────────────────────────────────────

    /**
     * **A failed build answers the caller instead of leaving the mode to time out (D36).**
     *
     * A non-cancellation exception inside the arm's one build fails the shared `Deferred`, which every
     * awaiting rung rethrows from `await()`. Before the fix no rung reached its terminal, so the mode
     * waited out its own timeout rather than being told; the lookup now answers the same
     * [RouteReason.NO_PATH] surface an unanswered search shows. The sink is the build's own trace hook —
     * throwing on `HARVEST`, emitted only inside `buildGrid`, fails the build deterministically with no
     * fixture change — and the arm's three rungs share that one failed build, so all three are answered.
     * A `CancellationException` is deliberately untouched by the handler, so cancellation stays silent.
     */
    @Test
    fun aFailedBuildAnswersEveryAwaitingRungWithNoPath() = runBlocking {
        val engine = RouteAvoidEngine(
            paceKn = { paceKn },
            aversionKn = { AppConfig.routeAvoidSpeedZoneSoftCostAversion },
            slowWaterBudgetPct = { AppConfig.routeAvoidSpeedZoneTimeBudgetPct },
            worldProvider = { FakeWorld() },
            traceSink = { line ->
                if (line.startsWith("HARVEST")) throw IllegalStateException("injected build failure")
            }
        )
        val declared = engine.routesToCompute(origin, aim) as? RouteDeclarations.Available
        assertNotNull("the fixture pair declares its three rungs", declared)

        val subscribed = CompletableDeferred<Unit>()
        val terminals = ConcurrentHashMap<RouteId, RouteUpdate>()
        val allDone = CompletableDeferred<Unit>()
        val collector = launch(Dispatchers.Default) {
            engine.updates
                .onStart { subscribed.complete(Unit) }
                .collect { update ->
                    if (update.nextStage == null) {
                        terminals[update.routeId] = update
                        if (terminals.size == 3) allDone.complete(Unit)
                    }
                }
        }
        subscribed.await()
        for (computation in declared!!.computations) engine.startLookup(computation.id)

        // The terminals arrive without the mode's own timeout: the build failed and every rung answered.
        withTimeout(30_000) { allDone.await() }
        collector.cancel()

        assertEquals("the arm's three rungs are all answered", 3, terminals.size)
        for (terminal in terminals.values) {
            assertNull("a failed build answers no line", terminal.result)
            assertTrue("and no line rides the terminal", terminal.line.isEmpty())
            assertEquals(
                "the failure is answered with the existing no-path reason, the surface a failed search shows",
                RouteReason.NO_PATH,
                terminal.reason
            )
        }
    }

    // ── The healthy terminal's throwing sink (D39) ─────────────────────────────

    /**
     * **A sink throwing on the healthy terminal emits one terminal and propagates one failure (D39).**
     *
     * The failure catch wraps the build, the search and the fold and nothing else, so the success
     * terminal — emitted just before the sink is asked for its `DONE` line — sits **outside** it. A sink
     * that throws there therefore propagates a single uncaught failure instead of re-entering the handler
     * and emitting a second, spurious `NO_PATH` terminal. The corner is driven directly, not through the
     * harness's `HARVEST`-only build failure: the sink throws on `DONE`, the one line the success path
     * emits, and the throw is caught where the engine's own lane hands it up. There is no stray failure:
     * the compute scope carries its own `CoroutineExceptionHandler`, so the throwing sink's failure is
     * narrated as one `FAILED` trace line rather than escaping to the JVM's default handler and leaking
     * into a later test. Reverting D39 (wrapping the success emit back inside the catch) reddens this
     * test: the sink's throw re-enters the handler, a second terminal is emitted, and that second
     * terminal's own `DONE` line throws again.
     */
    @Test
    fun aThrowingSinkOnAHealthyTerminalEmitsOneTerminalAndPropagatesOneFailure() = runBlocking {
        val captured = Collections.synchronizedList(ArrayList<String>())
        val terminals = Collections.synchronizedList(ArrayList<RouteUpdate>())
        val engine = RouteAvoidEngine(
            paceKn = { paceKn },
            aversionKn = { AppConfig.routeAvoidSpeedZoneSoftCostAversion },
            slowWaterBudgetPct = { AppConfig.routeAvoidSpeedZoneTimeBudgetPct },
            worldProvider = { FakeWorld() },
            traceSink = { line ->
                captured += line
                if (line.startsWith("DONE")) {
                    throw IllegalStateException(SINK_FAILURE_MESSAGE)
                }
            }
        )
        val declared = engine.routesToCompute(origin, aim) as? RouteDeclarations.Available
        assertNotNull("the fixture pair declares its three rungs", declared)

        val subscribed = CompletableDeferred<Unit>()
        val firstTerminal = CompletableDeferred<RouteUpdate>()
        val collector = launch(Dispatchers.Default) {
            engine.updates
                .onStart { subscribed.complete(Unit) }
                .collect { update ->
                    if (update.nextStage == null) {
                        terminals.add(update)
                        firstTerminal.complete(update)
                    }
                }
        }
        subscribed.await()
        engine.startLookup(declared!!.computations[0].id)

        val terminal = withTimeout(120_000) { firstTerminal.await() }
        // The throw follows the emit on the same lane; the scope's handler narrates it as one `FAILED` line.
        withTimeout(120_000) { while (captured.none { it.startsWith("FAILED") }) delay(10) }
        collector.cancel()

        assertNotNull("the healthy terminal still answers its line", terminal.result)
        assertEquals("the lookup emits one terminal, never a second NO_PATH", 1, terminals.size)
        assertEquals(
            "the sink was asked for exactly one DONE line",
            1,
            captured.count { it.startsWith("DONE") }
        )
        assertEquals(
            "and exactly one failure propagates out of the lookup",
            1,
            captured.count { it.startsWith("FAILED") }
        )
    }

    private companion object {
        /** The one line the throwing sink refuses to answer — the D39 claim's own marker. */
        private const val SINK_FAILURE_MESSAGE = "injected healthy-terminal sink failure"
    }

    // ── The λ loop's keep rule (Phase 3) ───────────────────────────────────────

    /**
     * The comparator's stated order, both outcomes and the tie-breaks: the smaller **zone share** wins
     * even when the clock is worse, a pass that is faster but spends more time in a zone loses, and at
     * an equal share the fewer in-zone metres win before the clock does.
     */
    @Test
    fun theLoopKeepsTheBetterPassAndNeverTheLastOne() {
        assertTrue(
            "a corrective pass with a smaller zone share wins although it is slower on the clock",
            RoutePassRanking.betterPass(
                RoutePassRanking.PassCost(zoneShare = 0.25, zoneMetresM = 800.0, durationSec = 960.0),
                RoutePassRanking.PassCost(zoneShare = 0.40, zoneMetresM = 600.0, durationSec = 900.0)
            )
        )
        assertFalse(
            "a corrective pass that is faster but spends more time in a zone loses to the incumbent",
            RoutePassRanking.betterPass(
                RoutePassRanking.PassCost(zoneShare = 0.28, zoneMetresM = 850.0, durationSec = 900.0),
                RoutePassRanking.PassCost(zoneShare = 0.25, zoneMetresM = 800.0, durationSec = 960.0)
            )
        )
        assertTrue(
            "at an equal share the fewer in-zone metres win, before the clock",
            RoutePassRanking.betterPass(
                RoutePassRanking.PassCost(zoneShare = 0.25, zoneMetresM = 700.0, durationSec = 990.0),
                RoutePassRanking.PassCost(zoneShare = 0.25, zoneMetresM = 800.0, durationSec = 900.0)
            )
        )
    }

    // ── The crossing's line-end re-solve (D3) ──────────────────────────────────

    @Test
    fun theCrossingReSolveReachesTheLinesEnds() = runBlocking {
        setAvoidSwitch("routeAvoidSpeedZoneEnabled", true)
        val zone = SpeedZone("z", "Cap", 5.0, rectRing(43.49, 43.51, 7.02, 7.04))
        val world = FakeWorld(zones = listOf(zone))
        val finePass = RouteFinePass()
        val start = LatLng(43.50, 7.00)
        val p1 = LatLng(43.50, 7.015)
        val p2 = LatLng(43.50, 7.025)
        val aimLat = LatLng(43.50, 7.035)
        val corridor = BBox(43.40, 43.60, 6.90, 7.20)
        val priced = listOf(ZoneRing(zone.outerRing, zone.holes, zone.speedLimitKn))
        val line = listOf(start, p1, p2, aimLat)

        val spliced = finePass.solveCrossing(
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
        private val edges: MutableList<MultipassEdge> = mutableListOf(),
        private val openCoast: MutableList<List<LatLng>> = mutableListOf(),
        private val water: (Double, Double) -> Boolean = { _, _ -> true },
        /** The sounding (m) the depth layer answers, or `NaN` for an unsurveyed point. */
        private val depth: (Double, Double) -> Double = { _, _ -> Double.NaN },
        /** The speed zones the world answers, priced only while the engine's switch is armed. */
        private val zones: List<SpeedZone> = emptyList()
    ) : MultipassWorld {
        val boxes = mutableListOf<BBox>()

        override val coastlineReady: Boolean get() = ready
        override val depthReady: Boolean get() = depthLoaded
        override val bandWidthM: Double get() = band
        override val regionBounds: BBox? get() = null

        override fun segmentsIn(box: BBox): List<MultipassEdge> {
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

    private fun polygonRing(points: List<LatLng>): List<MultipassEdge> =
        points.zipWithNext().map { (a, b) -> MultipassEdge(a, b, LandRingOrientation.CCW_RING) } +
            MultipassEdge(points.last(), points.first(), LandRingOrientation.CCW_RING)

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

    private fun circleRing(center: LatLng, radiusM: Double, n: Int = 32): List<MultipassEdge> {
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
