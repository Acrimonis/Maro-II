package ykws.android.maro.spatial

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Test
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.DepthSample
import ykws.android.maro.data.model.DepthSource
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.data.model.RouteResult
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.spatial.avoid.AvoidEdge
import ykws.android.maro.spatial.avoid.AvoidWorld
import ykws.android.maro.spatial.avoid.bandReachM
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The avoid engine's own contract, pinned over a fake world: readiness latching, the water refusal,
 * the off-water end, the corridor search and the taut pull, the grow-once `NoPath` retry, and legs
 * timed at the injected pace.
 */
class RouteAvoidEngineTest {

    private val origin = RoutePoint(43.5000, 7.0000)
    private val aim = RoutePoint(43.5000, 7.0600)

    /** The pace the test injects, chosen on purpose: the expectation derives from this, never from a property. */
    private val paceKn = 12.0

    private val marginM = AppConfig.routeAvoidObstacleMarginM

    private fun newEngine(world: () -> AvoidWorld = { FakeWorld() }) =
        RouteAvoidEngine(paceKn = { paceKn }, worldProvider = world)

    private fun success(result: RouteResult?): RouteResult.Success {
        assertTrue("the engine answers a route", result is RouteResult.Success)
        return result as RouteResult.Success
    }

    @After
    fun restoreAvoidSwitches() {
        setAvoidSwitch("routeAvoidDepthGateEnabled", true)
        setAvoidSwitch("routeAvoidZone300Enabled", true)
    }

    /**
     * Flips an [AppConfig] avoid switch for one test. The fields ship `private set` — by design the
     * values change only through the properties load — so a test that must turn one off reaches the
     * backing field directly and [restoreAvoidSwitches] puts it back.
     */
    private fun setAvoidSwitch(name: String, value: Boolean) {
        val field = AppConfig::class.java.getDeclaredField(name)
        field.isAccessible = true
        field.setBoolean(AppConfig, value)
    }

    // ── Readiness ─────────────────────────────────────────────────────────────

    @Test
    fun theStateIsNotReadyUntilPrepared() {
        assertFalse(newEngine().state.value.ready)
    }

    @Test
    fun prepareLatchesReadyOnceTheWorldIsReady() = runTest {
        val engine = newEngine { FakeWorld(ready = true) }

        assertEquals(RouteEngineState.Ready, engine.prepare())
        assertTrue(engine.state.value.ready)
    }

    @Test
    fun prepareLoadsAMissingWorldAndLatchesReady() = runTest {
        val world = FakeWorld(ready = false, loadAnswers = mutableListOf(RouteEngineState.Ready))
        val engine = newEngine { world }

        assertEquals(RouteEngineState.Ready, engine.prepare())
        assertTrue(engine.state.value.ready)
    }

    /**
     * The depth half of the gate alone forces the load and can refuse by name — with no grid every
     * cell reads unsurveyed, so the 3 m gate would be silently inert rather than wrong-looking.
     */
    @Test
    fun prepareRefusesByNameWhenTheDepthGridIsNotIn() = runTest {
        val world = FakeWorld(
            ready = true,
            depthLoaded = false,
            loadAnswers = mutableListOf(
                RouteEngineState.Unavailable(RouteUnavailableReason.DEPTH_NOT_LOADED)
            )
        )
        val engine = newEngine { world }

        assertEquals(
            RouteEngineState.Unavailable(RouteUnavailableReason.DEPTH_NOT_LOADED),
            engine.prepare()
        )
        assertFalse("a refused world does not open the gate", engine.state.value.ready)
    }

    // ── The water refusal and the off-water end ────────────────────────────────

    @Test
    fun validatePointRefusesOffWater() = runTest {
        val engine = newEngine { FakeWorld(water = { lat, _ -> lat < 43.60 }) }

        assertNull(engine.validatePoint(origin))
        assertEquals(RouteRefusalReason.OFF_WATER, engine.validatePoint(RoutePoint(43.70, 7.00)))
    }

    @Test
    fun anOffWaterEndAnswersOutsideWater() = runTest {
        val engine = newEngine { FakeWorld(water = { lat, _ -> lat < 43.60 }) }
        engine.onOriginPositionChanged(origin)

        assertEquals(
            RouteResult.OutsideWater,
            engine.onDestinationPositionChanged(RoutePoint(43.70, 7.06))
        )
    }

    // ── The session shape ──────────────────────────────────────────────────────

    @Test
    fun theArmingCallAnswersNoRouteBecauseNoDestinationIsHeldYet() = runTest {
        val engine = newEngine()

        assertNull(engine.onOriginPositionChanged(origin))
        assertNull("a destination with no origin is not a route either", newEngine().onDestinationPositionChanged(aim))
    }

    /** Nothing to wait for between asks: stage 1 reads nothing that expires. */
    @Test
    fun itIsAlwaysReadyToRecompute() = runTest {
        assertTrue(newEngine().isReadyToRecompute())
    }

    // ── The pipeline ──────────────────────────────────────────────────────────

    /** Over empty water the pull collapses the corridor to the straight line, and the pin stands on the aim. */
    @Test
    fun aClearCrossingAnswersTheRawEndsUntouched() = runTest {
        val engine = newEngine()
        engine.onOriginPositionChanged(origin)

        val route = success(engine.onDestinationPositionChanged(aim))

        assertEquals(listOf(origin, aim), route.points)
        assertFalse(route.destinationMoved)
        assertTrue(route.forcedCrossingZoneNames.isEmpty())
        val straight = SpatialOperations.haversine(origin.toLatLng(), aim.toLatLng())
        assertEquals(straight, route.distanceM, 1e-6)
    }

    @Test
    fun theRouteAvoidsAnIslandAndKeepsTheMargin() = runTest {
        val island = circleRing(LatLng(43.5000, 7.0300), radiusM = 400.0)
        val world = FakeWorld(edges = island.toMutableList())
        val engine = newEngine { world }
        engine.onOriginPositionChanged(origin)

        val route = success(engine.onDestinationPositionChanged(aim))

        assertEquals("the line starts at the raw start", origin, route.points.first())
        assertEquals("and ends at the raw aim", aim, route.points.last())
        assertFalse(route.destinationMoved)
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
    fun exhaustionAnswersNoPathAndRetriesWithDoubledReach() = runTest {
        val wall = polygonRing(
            listOf(
                LatLng(43.45, 7.02),
                LatLng(43.45, 7.04),
                LatLng(43.55, 7.04),
                LatLng(43.55, 7.02)
            )
        )
        val world = FakeWorld(edges = wall.toMutableList())
        val engine = newEngine { world }
        engine.onOriginPositionChanged(origin)

        val result = engine.onDestinationPositionChanged(aim)

        assertEquals(RouteResult.NoPath, result)
        assertEquals("one pass plus one doubled-reach retry", 2, world.boxes.size)
        val firstSpan = world.boxes[0].latNorth - world.boxes[0].latSouth
        val retrySpan = world.boxes[1].latNorth - world.boxes[1].latSouth
        assertTrue("the retry's corridor reach is doubled", retrySpan > firstSpan)
    }

    @Test
    fun legsAreTimedAtTheInjectedPace() = runTest {
        val engine = newEngine()
        engine.onOriginPositionChanged(origin)

        val route = success(engine.onDestinationPositionChanged(aim))

        val straight = SpatialOperations.haversine(origin.toLatLng(), aim.toLatLng())
        val seconds = straight / Units.knotsToMps(paceKn)
        assertEquals(listOf(seconds), route.legTimesSec)
        assertEquals(seconds, route.durationSec, 1e-9)
    }

    /** A concave bay: a peninsula jutting south from the north coast; the line must round its tip. */
    @Test
    fun aConcaveBayWithAPeninsulaRoutesAroundTheTip() = runTest {
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
        val engine = newEngine { world }
        engine.onOriginPositionChanged(origin)

        val route = success(engine.onDestinationPositionChanged(aim))

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

    /**
     * The fix: a convex headland must bend at exactly the two offset tangent corners, not hug the
     * coastline's digitized in-and-out — the regression the Cap d'Antibes route exposed. The route
     * from either side settles on the two offset tip corners and nothing else.
     */
    @Test
    fun aConvexHeadlandBendsAtTheTwoOffsetTangentCornersBothWays() = runTest {
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
        // The two convex tip corners, offset by the margin on the water side.
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
        engine.onOriginPositionChanged(from)
        val route = success(engine.onDestinationPositionChanged(to))

        assertEquals("the two ends plus exactly the two offset tangent corners", 4, route.points.size)
        assertEquals(from, route.points.first())
        assertEquals(to, route.points.last())
        assertTrue(
            "the first bend stands on the first offset tangent corner",
            SpatialOperations.haversine(route.points[1].toLatLng(), firstCorner) < 1.0
        )
        assertTrue(
            "the second bend stands on the second offset tangent corner",
            SpatialOperations.haversine(route.points[2].toLatLng(), secondCorner) < 1.0
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

    /** The clearance binds every point of an emitted segment, not just the waypoint vertices. */
    @Test
    fun everyPulledSegmentInteriorKeepsTheMargin() = runTest {
        val island = circleRing(LatLng(43.5000, 7.0300), radiusM = 400.0)
        val world = FakeWorld(edges = island.toMutableList())
        val engine = newEngine { world }
        engine.onOriginPositionChanged(origin)

        val route = success(engine.onDestinationPositionChanged(aim))

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
                if (SpatialOperations.haversine(p, origin.toLatLng()) < marginM) continue
                if (SpatialOperations.haversine(p, aim.toLatLng()) < marginM) continue
                assertTrue(
                    "every pulled segment's interior stands at least the margin off land",
                    world.distanceToCoastM(p.latitude, p.longitude) >= marginM - 1e-6
                )
            }
        }
    }

    /** A digitized coast with many small convex teeth must not become a zigzag: the snap + pull leaves a clean taut line. */
    @Test
    fun aDigitizedCoastProducesACleanTautLineNotAZigzag() = runTest {
        val teeth = 40
        val coast = sawtoothCoast(teeth)
        val world = FakeWorld(openCoast = mutableListOf(coast))
        val engine = newEngine { world }
        engine.onOriginPositionChanged(RoutePoint(43.48, 6.999))

        val route = success(engine.onDestinationPositionChanged(RoutePoint(43.48, 7.081)))

        assertTrue("the 40-tooth coast collapses to a clean line, not a per-tooth zigzag", route.points.size <= 8)
        for (point in route.points) {
            assertTrue(
                "every waypoint keeps the clearance off the teeth",
                world.distanceToCoastM(point.latitude, point.longitude) >= marginM - 1e-6
            )
        }
    }

    /** The Lérins-to-Salis-shaped corridor answers under the 500 ms wall the plan pins. */
    @Test
    fun theLerinsToSalisShapedCorridorAnswersUnderTheBudget() = runTest {
        val engine = newEngine { lerinsToSalisWorld() }
        engine.onOriginPositionChanged(RoutePoint(43.508, 7.065))

        val start = System.nanoTime()
        val route = engine.onDestinationPositionChanged(RoutePoint(43.572, 7.114))
        val elapsedMs = (System.nanoTime() - start) / 1_000_000

        success(route)
        assertTrue("the corridor answers in $elapsedMs ms, under the 500 ms wall", elapsedMs <= 500)
    }

    // ── The depth gate ─────────────────────────────────────────────────────────

    /**
     * The 3 m gate: a patch the depth layer knows to be 2 m deep paints its cells land, so the line is
     * longer than the straight chord and stands in no cell of the patch.
     */
    @Test
    fun theRouteRoundsWaterTheDepthGateCallsTooShallow() = runTest {
        val world = FakeWorld(depth = { lat, lon -> if (shallowPatch(lat, lon)) 2.0 else 20.0 })
        val engine = newEngine { world }
        engine.onOriginPositionChanged(origin)

        val route = success(engine.onDestinationPositionChanged(aim))

        val straight = SpatialOperations.haversine(origin.toLatLng(), aim.toLatLng())
        assertTrue("the gate sends the line round the patch", route.distanceM > straight)
        for (point in route.points) {
            assertFalse(
                "no waypoint stands in the water the gate forbids",
                shallowPatch(point.latitude, point.longitude)
            )
        }
    }

    /** Its control: the same patch with no sounding at all is ignored, and the line stays straight. */
    @Test
    fun aShallowPatchWithoutASoundingIsIgnored() = runTest {
        val world = FakeWorld(depth = { _, _ -> Double.NaN })
        val engine = newEngine { world }
        engine.onOriginPositionChanged(origin)

        val route = success(engine.onDestinationPositionChanged(aim))

        assertEquals(
            "unsurveyed water is not gated, so the line stays straight",
            listOf(origin, aim),
            route.points
        )
    }

    /** A patch of water roughly 400 m by 110 m straddling the straight line, mid-corridor. */
    private fun shallowPatch(latitude: Double, longitude: Double): Boolean =
        latitude in 43.4995..43.5005 && longitude in 7.0210..7.0260

    // ── The two switches ───────────────────────────────────────────────────────

    @Test
    fun theAvoidSwitchesDefaultOn() {
        assertTrue("the depth gate ships armed", AppConfig.routeAvoidDepthGateEnabled)
        assertTrue("the 300 m band ships armed", AppConfig.routeAvoidZone300Enabled)
    }

    /**
     * Depth gate off: the coastline alone makes the engine ready, so arming succeeds with the depth
     * grid absent and `load()` is never fired.
     */
    @Test
    fun depthGateOffArmsWithTheDepthGridAbsentAndNoLoadFires() = runTest {
        setAvoidSwitch("routeAvoidDepthGateEnabled", false)
        val world = FakeWorld(ready = true, depthLoaded = false)
        val engine = newEngine { world }

        assertEquals(RouteEngineState.Ready, engine.prepare())
        assertTrue(engine.state.value.ready)
        assertEquals("no depth load fires when the gate is off", 0, world.loadCalls)
    }

    /** Depth gate off: a shallow patch is not gated, so the line stays straight through it. */
    @Test
    fun depthGateOffPaintsNoGateCell() = runTest {
        setAvoidSwitch("routeAvoidDepthGateEnabled", false)
        val world = FakeWorld(depth = { lat, lon -> if (shallowPatch(lat, lon)) 2.0 else 20.0 })
        val engine = newEngine { world }
        engine.onOriginPositionChanged(origin)

        val route = success(engine.onDestinationPositionChanged(aim))

        assertEquals("the gate off prices the shallow patch as open water", listOf(origin, aim), route.points)
    }

    /** Zone300 off: the band is priced as open water, so the line through it is the straight chord. */
    @Test
    fun zone300OffRoutesThroughTheBandAtOpenWaterCost() = runTest {
        val coast = listOf(LatLng(43.52, 6.98), LatLng(43.52, 7.08))
        val start = RoutePoint(43.518, 7.00)
        val aim = RoutePoint(43.518, 7.06)
        val straight = SpatialOperations.haversine(start.toLatLng(), aim.toLatLng())

        setAvoidSwitch("routeAvoidZone300Enabled", true)
        val on = newEngine { FakeWorld(band = 300.0, openCoast = mutableListOf(coast)) }
        on.onOriginPositionChanged(start)
        assertTrue(
            "the priced band bends the line offshore",
            success(on.onDestinationPositionChanged(aim)).distanceM > straight + 10.0
        )

        setAvoidSwitch("routeAvoidZone300Enabled", false)
        val off = newEngine { FakeWorld(band = 300.0, openCoast = mutableListOf(coast)) }
        off.onOriginPositionChanged(start)
        val flat = success(off.onDestinationPositionChanged(aim))
        assertEquals("the band off prices the water as open sea", listOf(start, aim), flat.points)
        assertEquals(straight, flat.distanceM, 1e-6)
    }

    // ── The 300 m band ─────────────────────────────────────────────────────────

    /**
     * A start already inside the band is **priced, never refused**: with the band reaching the whole
     * corridor every cell is dearer and the answer is still the straight line — the marina-basin case,
     * where refusing the band would refuse the water the boat is already on.
     */
    @Test
    fun aStartInsideTheBandIsPricedNotRefused() = runTest {
        val coast = listOf(LatLng(43.52, 6.98), LatLng(43.52, 7.08))
        val world = FakeWorld(band = 10_000.0, openCoast = mutableListOf(coast))
        val engine = newEngine { world }
        engine.onOriginPositionChanged(origin)

        val route = success(engine.onDestinationPositionChanged(aim))

        assertEquals("a priced band is a price, never a wall", listOf(origin, aim), route.points)
    }

    /**
     * Change 3's regression: the band's own tangent corners snap a bend at the band's reach, so the
     * route chords the water between the coastline's convex corners — the headland's two tips, whose
     * flanks are the concave bays — instead of hugging the coast. With the zone off the band is open
     * water and the same bends dive in at the obstacle margin.
     */
    @Test
    fun aConcaveBayChordsItsMouthWithTheZoneOnAndDivesInWithItOff() = runTest {
        val coast = listOf(
            LatLng(43.51, 7.00),
            LatLng(43.51, 7.02),
            LatLng(43.49, 7.02),
            LatLng(43.49, 7.04),
            LatLng(43.51, 7.04),
            LatLng(43.51, 7.06)
        )
        val start = RoutePoint(43.50, 7.00)
        val aim = RoutePoint(43.50, 7.06)

        val mPerDegLat = SpatialOperations.EARTH_RADIUS_M * PI / 180.0
        val mPerDegLon = mPerDegLat * cos(Math.toRadians(43.49))
        val bandReach = bandReachM(300.0, AppConfig.routeAvoidZone300MarginM)
        val bandDLat = bandReach / mPerDegLat
        val bandDLon = bandReach / mPerDegLon
        val swBandCorner = LatLng(43.49 - bandDLat, 7.02 - bandDLon)
        val seBandCorner = LatLng(43.49 - bandDLat, 7.04 + bandDLon)
        val margin = AppConfig.routeAvoidObstacleMarginM
        val landDLat = margin / mPerDegLat
        val landDLon = margin / mPerDegLon
        val swLandCorner = LatLng(43.49 - landDLat, 7.02 - landDLon)
        val seLandCorner = LatLng(43.49 - landDLat, 7.04 + landDLon)

        setAvoidSwitch("routeAvoidZone300Enabled", true)
        val on = newEngine { FakeWorld(band = 300.0, openCoast = mutableListOf(coast)) }
        on.onOriginPositionChanged(start)
        val chorded = success(on.onDestinationPositionChanged(aim))

        assertTrue(
            "the chorded line bends at the west tip's band-offset corner",
            chorded.points.any { SpatialOperations.haversine(it.toLatLng(), swBandCorner) < 1.0 }
        )
        assertTrue(
            "and at the east tip's band-offset corner",
            chorded.points.any { SpatialOperations.haversine(it.toLatLng(), seBandCorner) < 1.0 }
        )

        setAvoidSwitch("routeAvoidZone300Enabled", false)
        val off = newEngine { FakeWorld(band = 300.0, openCoast = mutableListOf(coast)) }
        off.onOriginPositionChanged(start)
        val dived = success(off.onDestinationPositionChanged(aim))

        assertTrue(
            "the diving line bends at the west tip's obstacle-margin corner",
            dived.points.any { SpatialOperations.haversine(it.toLatLng(), swLandCorner) < 1.0 }
        )
        assertTrue(
            "and at the east tip's obstacle-margin corner",
            dived.points.any { SpatialOperations.haversine(it.toLatLng(), seLandCorner) < 1.0 }
        )
        assertTrue(
            "the chorded line stands further off the tips than the diving line",
            chorded.points.minOf { it.latitude } < dived.points.minOf { it.latitude } - 1e-7
        )
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
        private val loadAnswers: MutableList<RouteEngineState> = mutableListOf()
    ) : AvoidWorld {
        val boxes = mutableListOf<BBox>()
        var loadCalls = 0
            private set

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

        override suspend fun load(): RouteEngineState {
            loadCalls++
            val state = if (loadAnswers.isNotEmpty()) loadAnswers.removeAt(0) else RouteEngineState.Ready
            ready = state.ready
            depthLoaded = state.ready
            return state
        }
    }

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
