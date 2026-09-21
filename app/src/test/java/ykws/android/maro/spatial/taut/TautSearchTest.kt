package ykws.android.maro.spatial.taut

import kotlin.math.PI
import kotlin.math.cos
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.BoundingBox
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.data.model.RouteResult
import ykws.android.maro.spatial.RoutePlanTiming
import ykws.android.maro.spatial.SpatialOperations

/**
 * **The search's own readings, on water small enough to check by hand.**
 *
 * Five properties, each of them one of the design's own requirements rather than a restatement of the
 * code: the line bends around the obstacle and never crosses it; the same request answers the same line
 * — and the test can fail, because the line it compares is a real one with turns in it; the drawn
 * clock's own seconds never exceed what the search accumulated once the berth's courtesy is set aside;
 * the A\* agrees with a reference Dijkstra walking the same state space and charging the same terms,
 * §12.3's brake-and-accelerate pair included; and an end that cannot be reached is refused **by name**
 * rather than answered with an empty success.
 */
class TautSearchTest {

    private val berthM = 25.0
    private val shoreOffsetM = 50.0
    private val accel = 2.94

    /** The shipped `route.turn.longitudinalAccelMps2` default — the term both engines' clocks charge. */
    private val longitudinal = 1.96
    private val cruiseKn = 28.0

    /** An island between the two ends, longer to its north: the south way round is the shorter one. */
    private val island = TautTestWorld.rect(43.5450, 7.1150, 43.5570, 7.1250)

    private val originLat = 43.55
    private val originLon = 7.12
    private val metresPerDegreeLat = SpatialOperations.EARTH_RADIUS_M * PI / 180.0
    private val metresPerDegreeLon = metresPerDegreeLat * cos(originLat * PI / 180.0)

    /** A point named in the corridor's own metres, so the zone below reads as the shape it means. */
    private fun at(eastM: Double, northM: Double): RoutePoint = RoutePoint(
        originLat + northM / metresPerDegreeLat,
        originLon + eastM / metresPerDegreeLon
    )

    private fun rectM(westM: Double, southM: Double, eastM: Double, northM: Double): List<LatLng> = listOf(
        LatLng(originLat + southM / metresPerDegreeLat, originLon + westM / metresPerDegreeLon),
        LatLng(originLat + southM / metresPerDegreeLat, originLon + eastM / metresPerDegreeLon),
        LatLng(originLat + northM / metresPerDegreeLat, originLon + eastM / metresPerDegreeLon),
        LatLng(originLat + northM / metresPerDegreeLat, originLon + westM / metresPerDegreeLon),
        LatLng(originLat + southM / metresPerDegreeLat, originLon + westM / metresPerDegreeLon)
    )

    private fun world(land: List<List<LatLng>> = listOf(island)) = TautTestWorld(
        land = land,
        depthBox = BoundingBox(43.5200, 43.5800, 7.0800, 7.1600),
        coastalBandWidthM = 0.0
    )

    /** The engine under test, its four values pinned by the test rather than read from a live file. */
    private fun engine(
        world: TautWorld,
        growth: Int = TautRouteEngine.DEFAULT_CORRIDOR_GROWTH,
        refusals: MutableList<String> = mutableListOf()
    ) = TautRouteEngine(
        world = world,
        prepareWorld = null,
        berthM = { berthM },
        shoreOffsetM = { shoreOffsetM },
        lateralAccelMps2 = { accel },
        longitudinalAccelMps2 = { longitudinal },
        corridorGrowth = growth,
        warn = { message -> refusals.add(message) }
    )

    private val start = RoutePoint(43.5500, 7.1000)
    private val aim = RoutePoint(43.5500, 7.1400)

    @Test
    fun `the line bends around the obstacle and no leg of it crosses the obstacle`() = runBlocking {
        val answer = engine(world()).route(start, aim, cruiseKn)
        assertTrue("the acceptance pair has a route: $answer", answer is RouteResult.Success)
        val success = answer as RouteResult.Success

        var lowest = Double.MAX_VALUE
        var highest = -Double.MAX_VALUE
        for (point in success.points) {
            lowest = minOf(lowest, point.latitude)
            highest = maxOf(highest, point.latitude)
            assertTrue(
                "every drawn point stands off the island",
                !TautTestWorld.contains(island, point.latitude, point.longitude)
            )
        }
        assertTrue("the line goes round the island's southern end", lowest < 43.5450)
        assertTrue("and not over its northern one", highest < 43.5570)
        assertEquals("the drawn clock's legs are one per leg", success.points.size - 1, success.legTimesSec.size)
        assertTrue("the trip figure is a positive time", success.durationSec > 0.0)
    }

    /**
     * **The same request answers the same line, and the comparison is one that can fail.**
     *
     * Two fresh engines over the same world must answer byte for byte — and the line they are compared
     * on is asserted to be a route with turns in it, so a degenerate "straight line with no corners"
     * answer cannot satisfy this by being trivially equal. The control makes the same machinery produce a
     * *different* answer when the pace changes, which is what says the equality above is a reading rather
     * than a comparison of two constants.
     */
    @Test
    fun `the same request answers the same line`() = runBlocking {
        val first = engine(world()).route(start, aim, cruiseKn) as RouteResult.Success
        val second = engine(world()).route(start, aim, cruiseKn) as RouteResult.Success
        assertEquals("two runs, one line", first.points, second.points)
        assertEquals("and one clock", first.legTimesSec, second.legTimesSec)
        assertEquals(first.durationSec, second.durationSec, 0.0)

        assertTrue("the line compared is a real one", first.points.size >= 3)
        assertTrue(
            "and it has turns to disagree about",
            (first.details as TautRouteDetails).arcCorners + (first.details as TautRouteDetails).spiralCorners > 0
        )

        val slower = engine(world()).route(start, aim, cruiseKn / 2.0) as RouteResult.Success
        assertTrue(
            "a different pace really does change the answer — so the equality above is not vacuous",
            slower.durationSec > first.durationSec + 1.0
        )
    }

    /**
     * **A corner on a leg that clips a zone is charged for the water its stubs really cover** (item 1).
     *
     * The world below carries a zone the route has to enter — its own aim stands inside it, so no way
     * around exists and run two prices the crossing — and the corner that owns the leg which clips it
     * stands outside the zone. That is the ordinary case the walk names, and the invariant asserted
     * beside this one is read **here**: the world without a zone can never see the defect at all, since a
     * stub charged at the leg's most restrictive limit inflates the search's price alone and the stated
     * slack fails long before the ≈ 280 s of phantom corner price.
     */
    @Test
    fun `a corner on a leg that clips a zone agrees with the drawn clock`() = runBlocking {
        // The aim stands inside a 5 kn harbour: run one cannot answer, so the crossing is priced and the
        // zone's limit is in force over the last, clipped stretch of the line.
        val harbour = TautZoneShape(
            id = "harbour",
            name = "Harbour 5 kn",
            limitKn = 5.0,
            outerRing = TautTestWorld.rect(43.5400, 7.1330, 43.5600, 7.1480)
        )
        val zoneWorld = TautTestWorld(
            land = listOf(island),
            zones = listOf(harbour),
            depthBox = BoundingBox(43.5200, 43.5800, 7.0800, 7.1600),
            coastalBandWidthM = 0.0
        )
        val answer = engine(zoneWorld).route(start, aim, cruiseKn)
        assertTrue("the zone-entering pair is answered: $answer", answer is RouteResult.Success)
        val success = answer as RouteResult.Success
        val details = success.details as TautRouteDetails
        assertTrue("and it is run two that answered", details.zonePricedRun)
        assertTrue(
            "the zone's own limit is in force over a drawn leg: ${details.legLimitKn.distinct()}",
            details.legLimitKn.contains(5.0)
        )
        assertTrue(
            "with the corner that owns that leg standing outside the zone",
            details.spiralCorners + details.arcCorners + details.sharpCorners > 0
        )
        val priced = details.pricedSec - details.berthPriceSec
        assertTrue(
            "the drawn clock costs no more than the search priced " +
                "(${success.durationSec} against $priced)",
            success.durationSec <= priced + 1e-6
        )
        assertTrue(
            "and the gap is the stated slack, not a leg's dearest limit charged over its stubs: " +
                "${priced - success.durationSec} s of $priced s",
            priced - success.durationSec <= 0.05 * priced + 0.5
        )
    }

    @Test
    fun `the drawn clock never exceeds what the search accumulated`() = runBlocking {
        val answer = engine(world()).route(start, aim, cruiseKn)
        val success = answer as RouteResult.Success
        val details = success.details as TautRouteDetails
        // The berth's courtesy is part of what the search minimises and is not part of what the trip
        // takes, so it is set aside on both sides of the comparison rather than counted as drift.
        val priced = details.pricedSec - details.berthPriceSec
        assertTrue(
            "the drawn line costs no more than the search priced (${success.durationSec} against " +
                "$priced, berth ${details.berthPriceSec} set aside)",
            success.durationSec <= priced + 1e-6
        )
        assertTrue(
            "and the slack is a stated tolerance rather than a near-zero one: " +
                "${success.durationSec} against $priced",
            priced - success.durationSec <= 0.05 * priced + 0.5
        )
        assertEquals("the dossier carries one limit per drawn leg", success.points.size - 1, details.legLimitKn.size)
        assertTrue("the map was built and counted", details.vertexCount > 2 && details.edgeCount > 0)
        assertTrue("and the pair count is reported beside it", details.candidatePairs > 0)
    }

    /**
     * **The reference walks the same states — and it is handed the search's own fit test and the search's
     * own clock** (item 5).
     *
     * It used to carry a duplicate of the run's rule and to price its corners through `Turn.priceSec`'s
     * default clock, which is a different quantity from the time the search accumulates: the two could
     * agree only while its world carried no limits at all, which is exactly what its old world did. Here
     * the world **carries a zone** with a way around it, so run one forbids the interior, the corner at
     * the zone's own north-west corner has its rounding refused by that rule, and the agreement is read
     * where the rule is live.
     */
    @Test
    fun `the A star agrees with a reference Dijkstra over the same state space`() {
        val zone = TautZoneShape("band", "Band 5 kn", 5.0, rectM(-300.0, -800.0, 300.0, 800.0))
        val zoneWorld = TautTestWorld(
            zones = listOf(zone),
            depthBox = BoundingBox(43.5200, 43.5800, 7.0800, 7.1600),
            coastalBandWidthM = 0.0
        )
        val from = at(-1500.0, 0.0)
        val to = at(1500.0, 800.0)
        val box = TautGraph.corridorBox(zoneWorld, from, to, 0, berthM)
        val terrain = TautTerrain.of(zoneWorld, box, berthM, shoreOffsetM)
        val obstacles = terrain.obstacles
        val graph = TautGraph.build(zoneWorld, terrain, from, to, cruiseKn)
        val outcome = TautSearch(
            obstacles = obstacles,
            world = zoneWorld,
            graph = graph,
            cruiseSpeedKn = cruiseKn,
            lateralAccelMps2 = accel,
            warn = {},
            longitudinalAccelMps2 = longitudinal
        ).run()
        assertNotNull("the search answers over the zone-carrying world", outcome)
        assertTrue("and run one answered, so the rule was in force", !outcome!!.zonePricedRun)
        assertEquals(
            "with the rule refusing the corner's own rounding, counted as the rule's",
            1,
            outcome.sharpByRule
        )
        val reference = referenceDijkstra(obstacles, graph)
        assertTrue("the reference reached the aim", reference.isFinite())
        assertEquals(
            "A* and Dijkstra walk to the same price",
            reference,
            outcome.pricedSec,
            maxOf(1e-6, reference * 1e-6)
        )
    }

    @Test
    fun `an end that cannot be reached is refused by name`() = runBlocking {
        // The start's own coordinates stand inside land's ring: the boat is where the boat may not be, so
        // no way exists from it at all. The refusal says **that**, rather than blaming the corridor for a
        // route on water there is none of — which is the naming item 5 asks for, the two inshore harness
        // pairs being exactly this case.
        val cage = TautTestWorld.rect(43.5485, 7.0985, 43.5515, 7.1015)
        val refusals = mutableListOf<String>()
        val answer = engine(world(land = listOf(island, cage)), refusals = refusals)
            .route(start, aim, cruiseKn)
        assertEquals(
            "an end off the water is a named refusal, never an empty success",
            RouteResult.OutsideWater,
            answer
        )
        assertTrue(
            "and the refusal is named in the tracer's own words rather than in a mesh's: $refusals",
            refusals.any { it.contains(TautRefusal.START_OFF_WATER.name) }
        )
        assertTrue(
            "no mesh and no stretch appears in what it says",
            refusals.none { it.contains("mesh", ignoreCase = true) || it.contains("stretch") }
        )
    }

    /**
     * **The reference: Dijkstra over the same state space, with the same cost function.**
     *
     * The state is a vertex and the vertex it was reached from — the direction the search carries — and
     * the edge cost is the leg at its own limit plus the berth's own seconds plus the turn the arrival
     * direction makes, §12.3's brake-and-accelerate pair included. It is written as the plainest possible
     * scan rather than as a second heap, so the A\*'s own machinery (its ordering, its stale-entry skip,
     * its tie-break) is what is being checked and not a copy of it.
     *
     * **What it does not carry is a second copy of anything.** Its turn is fitted with `fitsCurve`, the
     * search's own predicate, and priced with `drawnClockOf`, the search's own clock: the rule the pass
     * unified is read here rather than re-implemented, and the two costs are the same quantity by
     * construction rather than by coincidence (item 5).
     */
    private fun referenceDijkstra(
        obstacles: TautObstacles,
        graph: TautGraph
    ): Double {
        val best = HashMap<Long, Double>()
        val settled = HashSet<Long>()
        val startState = key(graph.startIndex, graph.startIndex)
        best[startState] = 0.0
        while (true) {
            var chosen: Long = -1
            var chosenCost = Double.MAX_VALUE
            for ((state, cost) in best) {
                if (state in settled) continue
                if (cost < chosenCost - 1e-12) {
                    chosenCost = cost
                    chosen = state
                }
            }
            if (chosen < 0) return Double.NaN
            settled.add(chosen)
            val vertex = (chosen shr 32).toInt()
            val previous = chosen.toInt()
            if (vertex == graph.aimIndex) return chosenCost
            for (edge in graph.edgesFrom(vertex)) {
                val next = graph.other(edge, vertex)
                if (next == previous && previous != vertex) continue
                if (graph.zoneOf(edge) >= 0) continue
                // The same step the search takes: the edge's own price, summed over its limit regions.
                val legSec = graph.priceSec(edge)
                val berthSec = legSec * TautZoneSet.BERTH_MAX_PRICE * graph.berthFraction(edge)
                val turnSec = if (previous == vertex) {
                    0.0
                } else {
                    val inEdge = graph.edgeBetween(previous, vertex)
                    // The same reference the search takes: the limit in force at the vertex (item 2).
                    val referenceLimit = graph.limitAt(vertex)
                    val turn = TautEasing.fit(
                        frame = obstacles.frame,
                        previous = graph.vertices[previous],
                        vertex = graph.vertices[vertex],
                        next = graph.vertices[next],
                        cruiseSpeedKn = cruiseKn,
                        referenceLimitKn = referenceLimit,
                        lateralAccelMps2 = accel,
                        // **The search's own fit test** — no second predicate (item 5). The rule's own
                        // refusal and the water's are told apart inside it, so a reference that collapsed
                        // them would price a step the A* does not charge.
                        fits = { emitted ->
                            fitsCurve(
                                obstacles = obstacles,
                                zones = graph.zones,
                                forbidZoneInteriors = true,
                                previous = graph.vertices[previous],
                                emitted = emitted,
                                next = graph.vertices[next]
                            )
                        }
                    )
                    turn.priceSec(
                        previous = graph.vertices[previous],
                        vertex = graph.vertices[vertex],
                        next = graph.vertices[next],
                        referenceLimitKn = referenceLimit,
                        cruiseSpeedKn = cruiseKn,
                        lateralAccelMps2 = accel,
                        longitudinalAccelMps2 = longitudinal,
                        inPriceSec = if (inEdge >= 0) graph.priceSec(inEdge) else Double.MAX_VALUE,
                        outPriceSec = legSec,
                        // **The search's own clock** — the splitter's pieces and the app's arithmetic, not
                        // `priceSec`'s default of a leg priced whole (item 5).
                        clock = { points, limits ->
                            drawnClockOf(graph, points, limits, cruiseKn, accel)
                        }
                    )
                }
                val candidate = chosenCost + legSec + berthSec + turnSec
                val nextState = key(next, vertex)
                if (candidate < (best[nextState] ?: Double.MAX_VALUE)) best[nextState] = candidate
            }
        }
    }

    private fun key(vertex: Int, previous: Int): Long =
        (vertex.toLong() shl 32) or (previous.toLong() and 0xFFFFFFFFL)

    @Suppress("unused")
    private fun distance(a: RoutePoint, b: RoutePoint): Double = SpatialOperations.haversine(
        LatLng(a.latitude, a.longitude),
        LatLng(b.latitude, b.longitude)
    )
}
