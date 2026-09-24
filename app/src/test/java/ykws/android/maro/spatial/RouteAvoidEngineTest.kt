package ykws.android.maro.spatial

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.data.model.RouteResult
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.spatial.avoid.AvoidEdge
import ykws.android.maro.spatial.avoid.AvoidWorld
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

    // ── The fake world ─────────────────────────────────────────────────────────

    private class FakeWorld(
        private var ready: Boolean = true,
        private val edges: MutableList<AvoidEdge> = mutableListOf(),
        private val openCoast: MutableList<List<LatLng>> = mutableListOf(),
        private val water: (Double, Double) -> Boolean = { _, _ -> true },
        private val loadAnswers: MutableList<RouteEngineState> = mutableListOf()
    ) : AvoidWorld {
        val boxes = mutableListOf<BBox>()

        override val coastlineReady: Boolean get() = ready
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
            val state = if (loadAnswers.isNotEmpty()) loadAnswers.removeAt(0) else RouteEngineState.Ready
            ready = state.ready
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
