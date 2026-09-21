package ykws.android.maro.spatial.taut

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.BoundingBox
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.data.model.RouteResult
import ykws.android.maro.spatial.RouteEngineState
import ykws.android.maro.spatial.RoutePlanTiming
import ykws.android.maro.spatial.RouteTurnGeometry
import ykws.android.maro.spatial.RouteUnavailableReason
import ykws.android.maro.spatial.SpatialOperations
import ykws.android.maro.spatial.Units

/**
 * **§13.3's assertion set, in full — one test per assertion, each with its own negative control.**
 *
 * The design's list is a list of *promises*, and a promise is only tested if the test can tell the
 * promise from its absence. So every assertion here is written twice: once against the mechanism's own
 * output — the graph's legs, the vertices' clearances, the zone the rule refuses, the refusal's name, the
 * hole through a zone — and once as a **control** that reads the same predicate on a case where it must
 * say no. That is what "shown to fail on its own revert" means inside one pass: the assertion reads what
 * the mechanism produced rather than restating a constant, so reverting the mechanism moves its numbers,
 * and the control proves the predicate is able to say no at all. Each control's KDoc names the revert it
 * would catch.
 *
 * The set, in the design's own order: no leg crosses land or the dilated contour; every vertex clears the
 * berth; the traversal rule costs no route on the acceptance pair; the no-route cases return **named**
 * reasons; the narrow-passage scenario exists; the corridor-leave scenario exists; the synthetic corridor
 * matches a Dijkstra reference (pinned in [TautSearchTest]); and two runs are byte-identical.
 */
class TautAssertionsTest {

    private val originLat = 43.55
    private val originLon = 7.12
    private val berthM = 25.0
    private val shoreOffsetM = 50.0
    private val accel = 2.94
    private val longitudinal = 1.96
    private val cruiseKn = 28.0

    private val metresPerDegreeLat = SpatialOperations.EARTH_RADIUS_M * PI / 180.0
    private val metresPerDegreeLon = metresPerDegreeLat * cos(originLat * PI / 180.0)
    private val nauticalMileM = 1852.0

    private fun latLng(eastM: Double, northM: Double): LatLng = LatLng(
        originLat + northM / metresPerDegreeLat,
        originLon + eastM / metresPerDegreeLon
    )

    private fun at(eastM: Double, northM: Double): RoutePoint {
        val point = latLng(eastM, northM)
        return RoutePoint(point.latitude, point.longitude)
    }

    private fun rectM(westM: Double, southM: Double, eastM: Double, northM: Double): List<LatLng> = listOf(
        latLng(westM, southM),
        latLng(eastM, southM),
        latLng(eastM, northM),
        latLng(westM, northM),
        latLng(westM, southM)
    )

    /**
     * A wall with vertices inside any corridor it crosses — a stack of adjoining boxes.
     *
     * A single huge rectangle whose four corners all fall outside the corridor is dropped by the harvest's
     * clip, which is right for a shape the corridor does not touch and wrong for one it runs through; a
     * fence of boxes keeps vertices on both sides of every boundary, which is what a coastline actually
     * looks like and what this scenario depends on.
     */
    private fun fence(halfHeightM: Double, boxes: Int, halfWidthM: Double = 150.0): List<List<LatLng>> {
        val heightM = 2.0 * halfHeightM / boxes
        return (0 until boxes).map { i ->
            val south = -halfHeightM + i * heightM
            rectM(-halfWidthM, south, halfWidthM, south + heightM)
        }
    }

    /** A world wide enough for every scenario here, with the coastal band out of the argument. */
    private fun world(
        land: List<List<LatLng>> = emptyList(),
        zones: List<TautZoneShape> = emptyList(),
        contours: List<List<LatLng>> = emptyList(),
        depthBox: BoundingBox? = BoundingBox(43.4000, 43.7000, 7.0000, 7.2600)
    ) = TautTestWorld(
        land = land,
        contours = contours,
        zones = zones,
        depthBox = depthBox,
        coastalBandWidthM = 0.0
    )

    private fun engine(
        world: TautWorld,
        growth: Int = TautRouteEngine.DEFAULT_CORRIDOR_GROWTH,
        refusals: MutableList<String> = mutableListOf(),
        longitudinalMps2: Double = longitudinal
    ) = TautRouteEngine(
        world = world,
        prepareWorld = null,
        berthM = { berthM },
        shoreOffsetM = { shoreOffsetM },
        lateralAccelMps2 = { accel },
        longitudinalAccelMps2 = { longitudinalMps2 },
        corridorGrowth = growth,
        warn = { message -> refusals.add(message) }
    )

    /** The obstacles and the graph a case is asserted against — the search's own two inputs. */
    private fun mapOf(
        world: TautWorld,
        start: RoutePoint,
        aim: RoutePoint
    ): Triple<BoundingBox, TautObstacles, TautGraph> {
        val box = TautGraph.corridorBox(world, start, aim, 0, berthM)
        val terrain = TautTerrain.of(world, box, berthM, shoreOffsetM)
        val graph = TautGraph.build(world, terrain, start, aim, cruiseKn)
        return Triple(box, terrain.obstacles, graph)
    }

    private fun search(
        world: TautWorld,
        obstacles: TautObstacles,
        graph: TautGraph
    ) = TautSearch(
        world = world,
        obstacles = obstacles,
        graph = graph,
        cruiseSpeedKn = cruiseKn,
        lateralAccelMps2 = accel,
        warn = {},
        longitudinalAccelMps2 = longitudinal
    )

    // ── 1 · no leg crosses land or the dilated contour ────────────────────────

    /**
     * The graph's every edge, and the drawn line's every chord, are held to the water.
     *
     * Control: a leg drawn through the island must be reported as crossing by the same predicate the
     * graph uses, so the check above is a reading rather than a predicate that always says "clear".
     * Revert it catches: dropping the crossing test from `allowed()` lets the graph hold legs through
     * land, and the first loop fails.
     */
    @Test
    fun `no leg crosses land or the dilated contour`() {
        val island = rectM(-300.0, -300.0, 300.0, 300.0)
        val contour = rectM(480.0, -420.0, 920.0, 420.0)
        val testWorld = world(land = listOf(island), contours = listOf(contour))
        val start = at(-1500.0, 0.0)
        val aim = at(1500.0, 0.0)
        val (_, obstacles, graph) = mapOf(testWorld, start, aim)

        var legs = 0
        for (vertex in graph.vertices.indices) {
            for (edge in graph.edgesFrom(vertex)) {
                legs++
                val from = graph.vertices[vertex]
                val to = graph.vertices[graph.other(edge, vertex)]
                assertTrue(
                    "no graph edge crosses the dilated wall: $from → $to",
                    !obstacles.crossesWall(from, to)
                )
                val midLat = (from.latitude + to.latitude) / 2.0
                val midLon = (from.longitude + to.longitude) / 2.0
                assertTrue(
                    "every graph edge's middle stands on water the boat may use",
                    obstacles.traversable(midLat, midLon)
                )
            }
        }
        assertTrue("the graph really has legs to hold to the water", legs > 0)

        val outcome = search(testWorld, obstacles, graph).run()
        assertTrue("the search answers", outcome != null)
        val points = outcome!!.points
        for (i in 0 until points.size - 1) {
            assertTrue(
                "no drawn chord crosses the water's own wall",
                !obstacles.curveBlocked(points[i], points[i + 1])
            )
        }

        // Control: the same predicate, on a leg that really does cross the island and its dilation.
        assertTrue(
            "the crossing test is able to say yes",
            obstacles.crossesWall(at(-500.0, 0.0), at(500.0, 0.0))
        )
    }

    // ── 2 · every vertex clears the berth ─────────────────────────────────────

    /**
     * Every harvested wall corner stands at least the berth — less the abstraction's own tolerance —
     * away from the **undilated** obstacle it came from.
     *
     * Control: a point plainly inside the berth fails the same measure. Revert it catches: a dilation
     * that stopped being validated (`validateDilation` out of the harvest) leaves short segments, and
     * both halves fail.
     */
    @Test
    fun `every vertex clears the berth`() {
        val island = rectM(-400.0, -400.0, 400.0, 400.0)
        val testWorld = world(land = listOf(island))
        val start = at(-2000.0, 0.0)
        val aim = at(2000.0, 0.0)
        val (_, obstacles, _) = mapOf(testWorld, start, aim)
        assertTrue("the harvest found corners to check", obstacles.corners.isNotEmpty())

        val minimum = berthM - berthM * TautObstacles.TOLERANCE_FRACTION - 1.0
        for (corner in obstacles.corners) {
            val distance = distanceToRawWalls(obstacles, corner)
            assertTrue(
                "corner $corner stands $distance m off the obstacle, against a berth of $berthM m",
                distance >= minimum
            )
        }

        // Control: a point five metres off the island is inside the berth, and the measure says so.
        assertTrue(
            "the berth measure can fail",
            distanceToRawWalls(obstacles, at(0.0, 395.0)) < minimum
        )
    }

    // ── 3 · the traversal rule costs no route where a way around exists ───────

    /**
     * A zone with a way around it is **forbidden, not priced**: run one answers, nothing is reported,
     * and the second run is never reached.
     *
     * Control: the same zone with the aim inside it leaves no way around, and then the crossing *is*
     * priced and named — the rule costs no route, and when it cannot hold it says which zone it crossed.
     * Revert it catches: putting the berth back as a hard collar refuses the first world's way round and
     * turns this answer into a priced one or none.
     *
     * The pair here is a **synthetic corridor**, not the acceptance case: the shipped pair is measured by
     * the harness on the real world, and a test named after a pair it does not run is a reading of the
     * wrong water (item 5). The same arrangement is read again on the real pair in `TautRouteHarness`.
     */
    @Test
    fun `the traversal rule costs no route where a way around a zone exists`() = runBlocking {
        val zone = TautZoneShape(
            id = "cap",
            name = "Cap 5 kn",
            limitKn = 5.0,
            outerRing = rectM(-300.0, -800.0, 300.0, 800.0)
        )
        val start = at(-1500.0, 0.0)
        val aim = at(1500.0, 0.0)

        val answer = engine(world(zones = listOf(zone))).route(start, aim, cruiseKn)
        assertTrue("a way around the zone is found: $answer", answer is RouteResult.Success)
        val details = (answer as RouteResult.Success).details as TautRouteDetails
        assertTrue("and it is run one that found it", !details.zonePricedRun)
        assertEquals("with no crossing to report", 0, details.crossings)
        assertTrue("so the zone's name appears nowhere", answer.forcedCrossingZoneNames.isEmpty())

        // Control: the aim inside the zone — no way around exists, so the crossing is priced and named.
        val inside = engine(world(zones = listOf(zone))).route(start, at(0.0, 0.0), cruiseKn)
        assertTrue("the aim inside a zone is still reachable: $inside", inside is RouteResult.Success)
        val crossedDetails = (inside as RouteResult.Success).details as TautRouteDetails
        assertTrue("and it is run two that answered", crossedDetails.zonePricedRun)
        assertTrue("with the crossing counted", crossedDetails.crossings > 0)
        assertEquals(
            "and named by the zone's own name",
            listOf(zone.name),
            inside.forcedCrossingZoneNames
        )
    }

    // ── 4 · the no-route cases return named reasons ───────────────────────────

    /**
     * Both of the tracer's refusals are values the caller can read, and the reason is named in the
     * tracer's own vocabulary: no mesh, no stretch, no bake.
     *
     * Control: the missing grid answers `OutsideWater` under the **world's** own name while a start whose
     * coordinates stand inside land's ring answers it under the **end's** — two different refusals told
     * apart by name rather than collapsed into one failure. The corridor's own refusal (the fence with no
     * way round it) is read in the test below this one.
     */
    @Test
    fun `the no-route cases return named reasons`() = runBlocking {
        val noGrid = mutableListOf<String>()
        val uncovered = engine(world(depthBox = null), refusals = noGrid)
            .route(at(-100.0, 0.0), at(100.0, 0.0), cruiseKn)
        assertEquals(
            "with no depth grid the refusal is its own value, not the mesh's",
            RouteResult.OutsideWater,
            uncovered
        )
        assertTrue(
            "and the grid's absence is named: $noGrid",
            noGrid.any { it.contains(TautRefusal.COVERED_WATER_UNKNOWN.name) }
        )

        val cage = rectM(-60.0, -60.0, 60.0, 60.0)
        val refusals = mutableListOf<String>()
        val caged = engine(world(land = listOf(cage)), refusals = refusals)
            .route(at(0.0, 0.0), at(1500.0, 0.0), cruiseKn)
        assertEquals(
            "a start whose own coordinates stand inside land is the end's own refusal (item 5)",
            RouteResult.OutsideWater,
            caged
        )
        assertTrue(
            "and the end's position is named rather than the corridor's: $refusals",
            refusals.any { it.contains(TautRefusal.START_OFF_WATER.name) }
        )
        assertTrue(
            "with the corridor's refusal told apart from it by name",
            refusals.none { it.contains(TautRefusal.NO_ROUTE_AFTER_GROWTH.name) }
        )
        assertTrue(
            "no refusal borrows another engine's vocabulary",
            refusals.none { it.contains("mesh", ignoreCase = true) || it.contains("stretch") }
        )
    }

    // ── 5 · the narrow-passage scenario ───────────────────────────────────────

    /**
     * **A passage narrower than twice the berth is still routable**, because the berth is a price and
     * never a wall (§11.2, §15). The raw gap is 30 m against a 50 m berth, and the land's own dilation
     * still leaves five metres of water beside the zone's boundary: the route uses it and pays for both
     * rather than being refused.
     *
     * Control: the gap's own arithmetic is asserted first, so the scenario cannot stop being the narrow
     * case it claims to be while still passing.
     */
    @Test
    fun `a passage narrower than twice the berth stays routable`() = runBlocking {
        val land = rectM(30.0, -2000.0, 1200.0, 2000.0)
        val zone = TautZoneShape(
            id = "harbour",
            name = "Harbour 5 kn",
            limitKn = 5.0,
            outerRing = rectM(-1200.0, -2000.0, 0.0, 2000.0)
        )
        val start = at(2.5, -400.0)
        val aim = at(2.5, 400.0)

        val rawGapM = 30.0 - 0.0
        assertTrue("the scenario really is narrower than twice the berth", rawGapM < 2.0 * berthM)
        assertTrue("and the land's own dilation still leaves water in it", rawGapM - berthM > 0.0)

        val answer = engine(world(land = listOf(land), zones = listOf(zone))).route(start, aim, cruiseKn)
        assertTrue("the passage is passable: $answer", answer is RouteResult.Success)
        val details = (answer as RouteResult.Success).details as TautRouteDetails
        assertEquals("and the route keeps out of the zone's interior", 0, details.crossings)
        assertTrue("while paying for the margin it runs in", details.berthPriceSec > 0.0)
        assertTrue("which the dossier measures in metres too", details.inMarginM > 0.0)
    }

    // ── 6 · the corridor-leave scenario ───────────────────────────────────────

    /**
     * **A route that has to leave the corridor is refused by name, and the growth is what finds it.**
     *
     * A fence deeper than the rough corridor's reach stands across the way: with the growth pass
     * disabled nothing is found and the refusal says so in its own words, and with the one growth the
     * design allows the way round its end is found — which is the fallback read as a value rather than
     * asserted in prose, with `corridorGrowth` printing which relaxation restored the way.
     *
     * Control: the two runs differ only in `corridorGrowth`, so the second's answer is the growth's own
     * doing and not a different world's.
     */
    @Test
    fun `a corridor the route must leave is refused by name unless the growth finds it`() = runBlocking {
        val wall = fence(halfHeightM = 4700.0, boxes = 10)
        val start = at(-900.0, 0.0)
        val aim = at(900.0, 0.0)

        val refusals = mutableListOf<String>()
        val pinned = engine(world(land = wall), growth = 0, refusals = refusals)
            .route(start, aim, cruiseKn)
        assertEquals(
            "with no growth the way round is outside the corridor, and it is refused by name",
            RouteResult.NoPath,
            pinned
        )
        assertTrue(
            "and the name says which step gave up: $refusals",
            refusals.any { it.contains(TautRefusal.NO_ROUTE_AFTER_GROWTH.name) }
        )

        val grown = engine(world(land = wall), growth = 1).route(start, aim, cruiseKn)
        assertTrue("the growth finds the way round its end: $grown", grown is RouteResult.Success)
        val details = (grown as RouteResult.Success).details as TautRouteDetails
        assertTrue(
            "and the dossier says the corridor had to be grown to do it (growth ${details.corridorGrowth})",
            details.corridorGrowth >= 1
        )
    }

    // ── 7 · the zone hole is navigable through one predicate ──────────────────

    /**
     * **A hole is water, and one predicate says so for both readers.**
     *
     * The zone is a strip across the whole corridor with a slot cut through it. The straight route enters
     * and leaves the strip *through the hole*, so the rule sees no interior crossing and run one answers
     * with nothing to report — where the same route with the hole walled is a priced crossing.
     *
     * Control: with the hole moved off the line the very same predicate reports an interior crossing for
     * the very same leg, and the engine stops answering with the straight line. Revert it catches:
     * restoring the ring-only containment the built engine shipped makes the first world's straight leg
     * a crossing, and the first assertion fails before the engine is even asked.
     */
    @Test
    fun `a zone hole is navigable through the one predicate`() = runBlocking {
        val strip = rectM(-3000.0, -100.0, 3000.0, 100.0)
        val slot = rectM(-50.0, -120.0, 50.0, 120.0)
        val onLine = TautZoneShape("strip", "Strip 5 kn", 5.0, strip, holes = listOf(slot))
        val start = at(0.0, -900.0)
        val aim = at(0.0, 900.0)

        val throughHole = world(zones = listOf(onLine))
        val (_, _, graphThroughHole) = mapOf(throughHole, start, aim)
        assertTrue(
            "the straight leg through the hole enters no interior — the rule reads ring minus holes",
            graphThroughHole.zones.enteredZone(start, aim) < 0
        )

        val answer = engine(throughHole).route(start, aim, cruiseKn)
        assertTrue("and the engine answers it: $answer", answer is RouteResult.Success)
        val details = (answer as RouteResult.Success).details as TautRouteDetails
        assertTrue("through run one", !details.zonePricedRun)
        assertEquals("with no crossing to report", 0, details.crossings)

        // Control: the same strip with the hole moved aside — the same leg now enters the interior.
        val aside = TautZoneShape(
            "strip", "Strip 5 kn", 5.0, strip,
            holes = listOf(rectM(600.0, -120.0, 700.0, 120.0))
        )
        val holeAside = world(zones = listOf(aside))
        val (_, _, graphHoleAside) = mapOf(holeAside, start, aim)
        assertTrue(
            "with the hole off the line the same predicate reports the crossing",
            graphHoleAside.zones.enteredZone(start, aim) >= 0
        )
        val detour = engine(holeAside).route(start, aim, cruiseKn)
        assertTrue("and the engine answers a way round rather than nothing", detour is RouteResult.Success)
        val straightM = SpatialOperations.haversine(
            LatLng(start.latitude, start.longitude), LatLng(aim.latitude, aim.longitude)
        )
        assertTrue(
            "the answer is a way round rather than straight through: " +
                "${(detour as RouteResult.Success).distanceM} m against $straightM m straight",
            detour.distanceM > straightM * 1.05
        )
    }

    // ── 8 · two runs are byte-identical ──────────────────────────────────────

    /**
     * Determinism, asserted on a route with corners in it — see [TautSearchTest] for the same property
     * with its own control. This one pins the dossier's counts too, since a run that expanded a different
     * number of states did different work whatever line it drew.
     */
    @Test
    fun `two runs are byte-identical`() = runBlocking {
        val island = rectM(-400.0, -600.0, 400.0, 600.0)
        val start = at(-1400.0, 0.0)
        val aim = at(1400.0, 0.0)
        val first = engine(world(land = listOf(island))).route(start, aim, cruiseKn) as RouteResult.Success
        val second = engine(world(land = listOf(island))).route(start, aim, cruiseKn) as RouteResult.Success
        assertEquals("one line", first.points, second.points)
        assertEquals("one clock", first.legTimesSec, second.legTimesSec)
        assertEquals(
            "one price",
            (first.details as TautRouteDetails).pricedSec,
            (second.details as TautRouteDetails).pricedSec,
            0.0
        )
        assertEquals(
            "one count of what it expanded",
            first.details?.nodesExpanded,
            second.details?.nodesExpanded
        )
        assertTrue("the line has corners to be deterministic about", first.points.size >= 3)
    }

    // ── 9 · an edge's price is a sum over its own limit regions, never a maximum ─

    /**
     * **The price is the time a boat spends, not the dearest limit on the leg** (§17 item 1).
     *
     * A leg half in a 5 kn zone and half in open water is charged the zone's limit **over the half it
     * stands in** and the cruise over the rest — so the sum is strictly cheaper than the maximum would
     * be, and it is its own arithmetic recomputed region by region.
     *
     * Control: the maximum's own price is asserted beside it, and the sum is checked to be below it by
     * the seconds the clipped part really costs. Revert it catches: reading one limit over the whole leg
     * (the built engine's midpoint rule) makes the two equal, and the inequality fails.
     */
    @Test
    fun `an edge is priced as its time summed over its own limit regions`() {
        val zone = TautZoneShape(
            id = "half",
            name = "Half 5 kn",
            limitKn = 5.0,
            outerRing = rectM(0.0, -2000.0, 1200.0, 2000.0)
        )
        val testWorld = world(zones = listOf(zone))
        val start = at(-1500.0, 0.0)
        val aim = at(500.0, 0.0)
        val (_, _, graph) = mapOf(testWorld, start, aim)

        val regions = graph.pricing.regionsOf(start, aim)
        assertTrue("the leg really stands in more than one limit region (${regions.size})", regions.size > 1)

        val full = SpatialOperations.haversine(
            LatLng(start.latitude, start.longitude), LatLng(aim.latitude, aim.longitude)
        )
        val asAMaximum = RoutePlanTiming.legSeconds(full, 5.0, cruiseKn)
        val summed = graph.pricing.priceSec(start, aim)
        assertTrue(
            "the sum is cheaper than the maximum over the same leg: $summed s against $asAMaximum s",
            summed < asAMaximum - 1.0
        )

        var hand = 0.0
        var from = start
        for (region in regions) {
            val legM = SpatialOperations.haversine(
                LatLng(from.latitude, from.longitude), LatLng(region.to.latitude, region.to.longitude)
            )
            hand += RoutePlanTiming.legSeconds(legM, region.limitKn, cruiseKn)
            from = region.to
        }
        assertEquals("and the sum is its own arithmetic, region by region", hand, summed, 1e-9)

        // Control: the same leg with no zone in it has one region, and that region is the cruise.
        val clear = world()
        val (_, _, clearGraph) = mapOf(clear, start, aim)
        assertEquals("with nothing to price, the leg is one region", 1, clearGraph.pricing.regionsOf(start, aim).size)
        assertTrue(
            "and its limit is the open water the clock falls back to",
            clearGraph.pricing.regionsOf(start, aim).single().limitKn == Double.MAX_VALUE
        )
    }

    // ── 10 · a leg clipping a zone's corner: the paired reading ───────────────

    /**
     * **The corner clip the midpoint-and-parity reading let through is now both charged and seen.**
     *
     * The leg below passes through the zone's south-west corner: it crosses the ring twice, so a parity
     * read called it *not entered*, and the crossing report counted it zero — a crossing presented as an
     * ordinary route. The interval reading sees the ~47 m it shares with the interior, and one home
     * answers both questions: the rule reads it as an entry, and the price charges the zone's limit over
     * that piece and the cruise over the rest.
     *
     * Control: the same leg against a ring that does not reach it, where both readings must say no.
     * Revert it catches: restoring the midpoint-and-parity test makes the first assertion fail while the
     * second still passes, so the two are readings of one mechanism rather than one of two.
     */
    @Test
    fun `a leg clipping a zone's corner is charged at its limit and read as an entry`() {
        val zone = TautZoneShape(
            id = "corner",
            name = "Corner 5 kn",
            limitKn = 5.0,
            outerRing = rectM(0.0, 0.0, 1000.0, 1000.0)
        )
        val start = at(-50.0, 100.0)
        val aim = at(100.0, -50.0)

        val (_, obstacles, graph) = mapOf(world(zones = listOf(zone)), start, aim)
        assertTrue(
            "the corner clip is an entry: the rule reads the interval, not the parity",
            graph.zones.enteredZone(start, aim) >= 0
        )
        val full = SpatialOperations.haversine(
            LatLng(start.latitude, start.longitude), LatLng(aim.latitude, aim.longitude)
        )
        val atCruise = RoutePlanTiming.legSeconds(full, cruiseKn, cruiseKn)
        val charged = graph.pricing.priceSec(start, aim)
        assertTrue(
            "and the clipped part is charged at the zone's own limit: $charged s against $atCruise s",
            charged > atCruise + 1.0
        )

        // **And the exclusion is per piece, not per leg** (item 2 of the level above, and the review's
        // item 2). The leg shares the zone's interior over the clip **alone**, so the clip's metres are
        // charged the zone's limit and are taken out of the margin's own reading — while the rest of the
        // leg, whose ends touch the boundary at the clip's two crossings, still pays. Skipping the zone
        // outright forgave that rest, and a pair reading `0.00 km`/`0.00 s` cannot tell "no leg runs a
        // margin" from "the exclusion forgave it".
        val frame = obstacles.frame
        val pa = frame.pt(LatLng(start.latitude, start.longitude))
        val pb = frame.pt(LatLng(aim.latitude, aim.longitude))
        val insideShare = graph.zones.limitSpans(pa, pb).sumOf { it.to - it.from }
        val fraction = graph.zones.berthFraction(start, aim, berthM)
        assertTrue(
            "the leg really shares an interval with the interior (${"%.3f".format(insideShare)} of it)",
            insideShare > 0.0
        )
        assertEquals(
            "and the margin is charged on the metres outside it alone",
            1.0 - insideShare,
            fraction,
            1e-6
        )
        assertTrue(
            "so a corner clip does not forgive the whole leg (read $fraction)",
            fraction > 1e-6 && fraction < 1.0 - 1e-6
        )
        val grazingFrom = at(-10.0, 400.0)
        val grazingTo = at(-10.0, 600.0)
        assertTrue(
            "while a leg that only grazes the boundary shares no interior, so it too pays the margin",
            graph.zones.enteredZone(grazingFrom, grazingTo) < 0 &&
                graph.zones.berthFraction(grazingFrom, grazingTo, berthM) > 0.0
        )

        // Control: a ring the leg does not reach reads as nothing on both readers.
        val far = TautZoneShape("far", "Far 5 kn", 5.0, rectM(3000.0, 3000.0, 4000.0, 4000.0))
        val (_, _, awayGraph) = mapOf(world(zones = listOf(far)), start, aim)
        assertTrue("a leg that cannot reach a zone enters nothing", awayGraph.zones.enteredZone(start, aim) < 0)
        assertEquals(
            "and is charged at the cruise alone",
            RoutePlanTiming.legSeconds(full, cruiseKn, cruiseKn),
            awayGraph.pricing.priceSec(start, aim),
            1e-6
        )
    }

    // ── 11 · the drawn line is cut where the limit changes ────────────────────

    /**
     * **Every drawn leg carries the limit in force over it** (§12.4, §17 item 1), which is what makes the
     * drawn clock's sum the price the search paid: a leg whose water changes limit is emitted as two
     * pieces, each named at its own limit, rather than as one leg named at the dearest of them.
     *
     * Control: the count of distinct limits in the dossier is asserted to be more than one, so a run that
     * named every leg the same value cannot pass this by being trivially consistent.
     */
    @Test
    fun `the drawn line is cut where the limit in force changes`() = runBlocking {
        val zone = TautZoneShape(
            id = "cap",
            name = "Cap 5 kn",
            limitKn = 5.0,
            outerRing = rectM(-300.0, -800.0, 300.0, 800.0)
        )
        // The aim stands inside the zone, so no way around it exists and run two prices the crossing.
        val answer = engine(world(zones = listOf(zone)))
            .route(at(-1500.0, 0.0), at(0.0, 0.0), cruiseKn)
        assertTrue("the priced run answers: $answer", answer is RouteResult.Success)
        val success = answer as RouteResult.Success
        val details = success.details as TautRouteDetails
        assertEquals("one limit per drawn leg", success.points.size - 1, details.legLimitKn.size)
        assertTrue(
            "the line is cut at the zone's boundary: the limits in force are " +
                "${details.legLimitKn.distinct()}",
            details.legLimitKn.distinct().size > 1
        )
        assertTrue("and the zone's own limit is named for the pieces inside it", details.legLimitKn.contains(5.0))
        assertTrue("while the crossing is reported too", details.crossings > 0)
    }

    // ── 12 · the band's own path, priced at the band's limit ──────────────────

    /**
     * **The coastal band is priced, and its reader is one home.** A leg inside the 300 m band is charged
     * the band's own limit; a leg outside it is charged the cruise. Nothing covered that path before
     * (item 5), and the two readings below are the same leg with the band's own width as the switch.
     */
    @Test
    fun `a leg inside the coastal band is priced at the band's limit`() = runBlocking {
        val shore = rectM(-2000.0, -4000.0, 0.0, 4000.0)
        val box = BoundingBox(43.4000, 43.7000, 7.0000, 7.2600)
        val bandWorld = TautTestWorld(
            land = listOf(shore),
            depthBox = box,
            coastalBandWidthM = 300.0,
            coastalBandSpeedLimitKn = 5.0
        )
        val start = at(200.0, -500.0)
        val aim = at(200.0, 500.0)
        val (_, _, graph) = mapOf(bandWorld, start, aim)
        assertTrue(
            "the band's limit is what is in force",
            graph.pricing.regionsOf(start, aim).all { it.limitKn == 5.0 }
        )
        val full = SpatialOperations.haversine(
            LatLng(start.latitude, start.longitude), LatLng(aim.latitude, aim.longitude)
        )
        assertEquals(
            "and the leg is priced at it",
            RoutePlanTiming.legSeconds(full, 5.0, cruiseKn),
            graph.pricing.priceSec(start, aim),
            1e-6
        )

        val inBand = engine(bandWorld).route(start, aim, cruiseKn) as RouteResult.Success
        val bandDetails = inBand.details as TautRouteDetails
        assertTrue("the drawn legs all carry the band's limit", bandDetails.legLimitKn.all { it == 5.0 })

        // Control: the same water with the band switched off — the same reader, the other answer.
        val noBandWorld = TautTestWorld(
            land = listOf(shore),
            depthBox = box,
            coastalBandWidthM = 0.0,
            coastalBandSpeedLimitKn = 5.0
        )
        val outOfBand = engine(noBandWorld).route(start, aim, cruiseKn) as RouteResult.Success
        val freeDetails = outOfBand.details as TautRouteDetails
        assertTrue(
            "outside the band the legs are bound by nothing, and the trip is faster",
            freeDetails.legLimitKn.all { it == Double.MAX_VALUE } &&
                outOfBand.durationSec < inBand.durationSec - 1.0
        )
    }

    // ── 13 · the margin's price and its own reading are one home ──────────────

    /**
     * **One fraction, two readers — and one leg set** (item 4). The metres the berth's price is charged
     * on are the **search's own chain edges**, each weighted by the very fraction `berthPriceSec`
     * multiplies its seconds by. The drawn line is a different, shorter set: it cuts every eased corner
     * short of the leg that carried it and re-reads each stub's own fraction, so a reading taken there
     * counts metres the price was never paid on — the second quantity the review found standing beside
     * the first.
     *
     * The world below isolates the leg set from the pace: the zone's limit is **above the cruise**, so
     * every leg of the chain is at the same speed and the two readers are one number apart by the pace
     * alone — an equality that can only hold while both cover the same legs. The drawn line's own sum is
     * computed beside it and asserted to be a different quantity, which is what makes the equality a
     * reading of the legs rather than of a constant.
     *
     * Control: the same route with the berth at zero has no margin at all, so both the price and the
     * reading go to nought rather than to a small number that would survive a wrong reading.
     */
    @Test
    fun `the margin's reading runs over the legs its price is paid on`() = runBlocking {
        val island = rectM(-400.0, -600.0, 400.0, 600.0)
        // A 40 kn strip whose limit is **above the cruise**, so no leg is slowed by it, and which the
        // taut line goes around at its own end — grazing it, which is what puts the line inside the
        // margin. The island is what gives the route corners for the drawn line to cut.
        val swift = TautZoneShape(
            id = "swift",
            name = "Swift 40 kn",
            limitKn = 40.0,
            outerRing = rectM(-100.0, -3000.0, 100.0, 3000.0)
        )
        val testWorld = world(land = listOf(island), zones = listOf(swift))
        val start = at(-1500.0, 0.0)
        val aim = at(1500.0, 0.0)
        val answer = engine(testWorld).route(start, aim, cruiseKn)
        assertTrue("the crossing pair is answered: $answer", answer is RouteResult.Success)
        val success = answer as RouteResult.Success
        val details = success.details as TautRouteDetails
        assertTrue("the line runs inside the margin somewhere", details.inMarginM > 0.0)
        assertTrue("and pays for it", details.berthPriceSec > 0.0)
        assertTrue(
            "and the route really has an eased corner for the drawn line to cut",
            details.spiralCorners + details.arcCorners > 0
        )

        // The two readers, one number apart by the pace: every chain leg is at the cruise, so the price
        // is the reading over `min(cruise, limit)` twice over.
        val cruiseMps = Units.knotsToMps(cruiseKn)
        assertEquals(
            "the price is the reading at the pace, twice over — one fraction, one leg set",
            details.berthPriceSec,
            details.inMarginM / cruiseMps * TautZoneSet.BERTH_MAX_PRICE,
            1e-3
        )

        // The target, shown apart: the drawn line's own pieces are a different, shorter set.
        val (_, _, graph) = mapOf(testWorld, start, aim)
        var drawn = 0.0
        for (i in 0 until success.points.size - 1) {
            val legM = SpatialOperations.haversine(
                LatLng(success.points[i].latitude, success.points[i].longitude),
                LatLng(success.points[i + 1].latitude, success.points[i + 1].longitude)
            )
            drawn += legM * graph.zones.berthFraction(success.points[i], success.points[i + 1], berthM)
        }
        assertTrue(
            "and the drawn line's own sum is a different quantity — $drawn m against " +
                "${details.inMarginM} m",
            abs(drawn - details.inMarginM) > 1.0
        )

        val noBerth = TautRouteEngine(
            world = testWorld,
            prepareWorld = null,
            berthM = { 0.0 },
            shoreOffsetM = { shoreOffsetM },
            lateralAccelMps2 = { accel },
            longitudinalAccelMps2 = { longitudinal },
            warn = {}
        ).route(start, aim, cruiseKn) as RouteResult.Success
        val free = noBerth.details as TautRouteDetails
        assertEquals("with no berth there is no margin to read", 0.0, free.inMarginM, 0.0)
        assertEquals("and nothing to pay", 0.0, free.berthPriceSec, 0.0)
    }

    // ── 14 · the readiness member closes the coastline window ────────────────

    /**
     * **Both doors read the world's own readiness, and the missing layer is named** (§17 item 3).
     *
     * A world whose coastline has not landed answers `Unavailable` from preparation with the coastline's
     * own reason, and refuses a route asked without preparation rather than drawing over land — the window
     * the built engine left open by checking the depth grid alone.
     */
    @Test
    fun `a route asked before the coastline lands is refused by name`() = runBlocking {
        val box = BoundingBox(43.4000, 43.7000, 7.0000, 7.2600)
        val noCoast = TautTestWorld(depthBox = box, coastLoaded = false, coastalBandWidthM = 0.0)
        val refusals = mutableListOf<String>()
        val halfBuilt = engine(noCoast, refusals = refusals)

        val prepared = halfBuilt.prepare()
        assertTrue("preparation refuses: $prepared", prepared is RouteEngineState.Unavailable)
        assertEquals(
            "and names the layer that is missing",
            RouteUnavailableReason.COASTLINE_NOT_LOADED,
            (prepared as RouteEngineState.Unavailable).reason
        )
        assertTrue(
            "the reason carries the id a screen reads rather than a sentence an engine built",
            RouteUnavailableReason.COASTLINE_NOT_LOADED.labelResId != 0
        )

        val answer = halfBuilt.route(at(-100.0, 0.0), at(100.0, 0.0), cruiseKn)
        assertEquals("and the search refuses through its own door", RouteResult.OutsideWater, answer)
        assertTrue(
            "naming the coastline, not the mesh and not the grid: $refusals",
            refusals.any { it.contains(TautRefusal.COVERED_WATER_UNKNOWN.name) && it.contains("coastline") }
        )
    }

    // ── 15 · the readings move when their mechanism is reverted ──────────────

    /**
     * **Every reading above is a reading of a mechanism, not of a constant** (item 5's fourth fix).
     *
     * The berth is the switch: at 25 m the abstraction dilates the wall and deletes water, at 0 m it does
     * neither — so the harvested wall, the water the abstraction costs and the corner count all move with
     * it. A reading that stayed put across the two would be a restatement of the code rather than of its
     * effect, which is what "demonstrated by revert" means inside one pass.
     */
    @Test
    fun `the readings move when the mechanism behind them is reverted`() = runBlocking {
        val island = rectM(-400.0, -600.0, 400.0, 600.0)
        val testWorld = world(land = listOf(island))
        val start = at(-1400.0, 0.0)
        val aim = at(1400.0, 0.0)

        fun reading(berth: Double): TautRouteDetails {
            val engine = TautRouteEngine(
                world = testWorld,
                prepareWorld = null,
                berthM = { berth },
                shoreOffsetM = { shoreOffsetM },
                lateralAccelMps2 = { accel },
                longitudinalAccelMps2 = { longitudinal },
                warn = {}
            )
            val answer = runBlocking { engine.route(start, aim, cruiseKn) }
            return (answer as RouteResult.Success).details as TautRouteDetails
        }

        val withBerth = reading(berthM)
        val without = reading(0.0)
        assertTrue(
            "the dilation really deletes water: ${withBerth.deletedAreaM2} m² against ${without.deletedAreaM2} m²",
            withBerth.deletedAreaM2 > without.deletedAreaM2 + 1_000.0
        )
        assertTrue(
            "and the wall it walks is not the same map: ${withBerth.wallSegmentCount} against " +
                "${without.wallSegmentCount}",
            withBerth.wallSegmentCount != without.wallSegmentCount
        )
        assertTrue(
            "the two corridors are not the same map, so the readings above are the mechanism's",
            withBerth.vertexCount != without.vertexCount ||
                withBerth.edgeCount != without.edgeCount ||
                withBerth.deletedAreaM2 != without.deletedAreaM2
        )
    }

    // ── 16 · the band's own boundary is cut where it crosses a leg ────────────

    /**
     * **The band's limit is charged over the metres that really stand inside the strip** (§18.1) — the
     * last midpoint rule in the engine, and the reading that fails while it is there.
     *
     * The coast runs north–south at `east = 0` and the band is the 300 m strip beside it, so the leg
     * below **leaves the strip a third of the way along** and its own middle stands outside: the per-leg
     * boolean read that middle and charged the whole leg the cruise. The band's existing tests covered a
     * leg wholly inside and a leg wholly outside, which is exactly why the rule survived them.
     *
     * Control: the same water with the band switched off gives one piece at the cruise — the midpoint
     * rule's own answer — so the two pieces below are the band's doing and not the arithmetic's.
     */
    @Test
    fun `the band's boundary cuts the leg and its limit covers the inside metres alone`() = runBlocking {
        val shore = rectM(-2000.0, -4000.0, 0.0, 4000.0)
        val box = BoundingBox(43.4000, 43.7000, 7.0000, 7.2600)
        val bandWorld = TautTestWorld(
            land = listOf(shore),
            depthBox = box,
            coastalBandWidthM = 300.0,
            coastalBandSpeedLimitKn = 5.0
        )
        val start = at(100.0, -500.0)
        val aim = at(700.0, -500.0)
        val full = SpatialOperations.haversine(
            LatLng(start.latitude, start.longitude), LatLng(aim.latitude, aim.longitude)
        )

        val (_, _, graph) = mapOf(bandWorld, start, aim)
        val regions = graph.pricing.regionsOf(start, aim)
        assertEquals("the leg is cut where it leaves the strip: two pieces", 2, regions.size)
        assertEquals("the inside piece carries the band's own limit", 5.0, regions[0].limitKn, 0.0)
        assertEquals("and the outside piece is bound by nothing", Double.MAX_VALUE, regions[1].limitKn, 0.0)
        assertEquals("the inside piece is the 200 m really inside", 200.0, regions[0].lengthM, 2.0)
        assertEquals("and the rest of the leg is outside it", 400.0, regions[1].lengthM, 2.0)

        // **The revert, shown as the reading these assertions refuse** (§18.1 step 6): the leg's own
        // middle stands *outside* the strip, which is exactly what the per-leg boolean asked — so with
        // the midpoint rule restored this leg is one piece at the cruise and the two readings above fail.
        val midLat = (start.latitude + aim.latitude) / 2.0
        val midLon = (start.longitude + aim.longitude) / 2.0
        assertTrue(
            "the leg's own middle stands outside the strip — the reading the midpoint rule used",
            bandWorld.distanceToCoastM(midLat, midLon) > bandWorld.coastalBandWidthM
        )

        val atCruise = RoutePlanTiming.legSeconds(full, cruiseKn, cruiseKn)
        val allInside = RoutePlanTiming.legSeconds(full, 5.0, cruiseKn)
        val charged = graph.pricing.priceSec(start, aim)
        assertTrue(
            "the inside metres are really charged: $charged s against $atCruise s at the cruise alone",
            charged > atCruise + 1.0
        )
        assertTrue(
            "and only those metres: $charged s against $allInside s for the whole leg at 5 kn",
            charged < allInside - 1.0
        )

        // The drawn line is cut there too and its clock is the price the search paid — the cut point being
        // collinear, it adds no turn price of its own (§18.1 step 5).
        val answer = engine(bandWorld).route(start, aim, cruiseKn) as RouteResult.Success
        val details = answer.details as TautRouteDetails
        assertEquals("the drawn line is the start, the cut and the aim", 3, answer.points.size)
        assertEquals("one limit per drawn piece", answer.points.size - 1, details.legLimitKn.size)
        assertTrue(
            "and the drawn pieces carry both limits: ${details.legLimitKn}",
            details.legLimitKn.contains(5.0) && details.legLimitKn.contains(Double.MAX_VALUE)
        )
        assertTrue(
            "the cut point is collinear — below the merge's own threshold",
            RouteTurnGeometry.turnRadians(answer.points[0], answer.points[1], answer.points[2]) <
                RouteTurnGeometry.MIN_TURN_RAD
        )
        val priced = details.pricedSec - details.berthPriceSec
        assertTrue(
            "the drawn clock and the search's price agree within the slack: " +
                "${answer.durationSec} against $priced",
            answer.durationSec <= priced + 1e-6
        )
        assertTrue(
            "and the gap is the stated slack rather than a piece priced at the wrong limit: " +
                "${priced - answer.durationSec} s of $priced s",
            priced - answer.durationSec <= 0.05 * priced + 0.5
        )

        // Control: the same water with the band off — one piece at the cruise, the midpoint rule's answer.
        val noBand = TautTestWorld(
            land = listOf(shore),
            depthBox = box,
            coastalBandWidthM = 0.0,
            coastalBandSpeedLimitKn = 5.0
        )
        val (_, _, freeGraph) = mapOf(noBand, start, aim)
        assertEquals("with no band the leg is one piece", 1, freeGraph.pricing.regionsOf(start, aim).size)
        assertEquals(
            "and it is priced at the cruise alone",
            RoutePlanTiming.legSeconds(full, Double.MAX_VALUE, cruiseKn),
            freeGraph.pricing.priceSec(start, aim),
            1e-9
        )
    }

    // ── 17 · a cut point is collinear and pays no turn price ──────────────────

    /**
     * **The splitter's cut is paid for in metres and never in turn price** (§18.1 step 5). A cut point
     * stands on the leg it split, so the deflection at it is zero and the clock's own turn term must be
     * zero there — otherwise every band and zone crossing would add a phantom corner to the line, and the
     * drawn clock would stop being the price the search paid.
     *
     * Control: the right angle the same machinery is built for is charged, so the nought above is the
     * geometry's own answer rather than a term that always reads nought.
     */
    @Test
    fun `a cut point is collinear and pays no turn price`() {
        val speed = Units.knotsToMps(cruiseKn)
        assertEquals("a zero deflection is free", 0.0, RouteTurnGeometry.turnPriceSec(speed, accel, 0.0), 0.0)
        val subThreshold = RouteTurnGeometry.turnPriceSec(
            speed, accel, RouteTurnGeometry.MIN_TURN_RAD / 10.0
        )
        assertTrue(
            "and so is anything below the merge's own threshold: $subThreshold s",
            subThreshold < 1e-6
        )
        assertTrue(
            "while the right angle a real corner makes is charged: " +
                "${RouteTurnGeometry.turnPriceSec(speed, accel, PI / 2.0)} s",
            RouteTurnGeometry.turnPriceSec(speed, accel, PI / 2.0) > 0.5
        )

        // **And the cut's own charge, not only its angle** (§18.1 step 5, as the review read it). A cut
        // point is inserted **on** the leg it splits, so the clock the app really sums — per drawn leg,
        // its turn term included — must be unchanged by it. Read as a charge, this is what "the cut is
        // free" means; read as an angle alone it was a claim about a deflection the two readers could
        // still read differently.
        val before = at(100.0, 0.0)
        val cut = at(300.0, 0.0)
        val after = at(500.0, 0.0)
        val withCut = RoutePlanTiming.drawnLegSeconds(
            listOf(before, cut, after),
            listOf(Double.MAX_VALUE, Double.MAX_VALUE),
            cruiseKn,
            accel
        )
        val without = RoutePlanTiming.drawnLegSeconds(
            listOf(before, after),
            listOf(Double.MAX_VALUE),
            cruiseKn,
            accel
        )
        assertTrue(
            "the cut point adds nothing to the line's own clock: " +
                "${withCut.sum() - without.sum()} s of ${without.sum()} s",
            abs(withCut.sum() - without.sum()) < 1e-6
        )
    }

    // ── 18 · a corner's reference limit is the one in force at its vertex ─────

    /**
     * **A corner is sized by the water it stands in, never by the dearest limit its own leg clips**
     * (§17 item 2 — and the assertion the review found missing, item 4 of the level above).
     *
     * The world is a 5 kn zone with the aim inside it, so the leg leaving the zone's south-west corner
     * shares the interior while the corner itself stands **outside** it (the boundary is water). The
     * corner's reference limit must read `∞`, and its leg's own clip is asserted beside it so the case
     * cannot stop being the clip it claims to be.
     *
     * Control: the aim's own vertex stands inside the zone and reads the zone's limit, so this is a
     * question about a point and not a constant. Revert it catches: making `limitAt` a per-leg maximum
     * returns the zone's own limit for the corner — a value read beside the assertion below, so the
     * revert is a number this test refuses rather than a claim in prose — and no other test notices.
     */
    @Test
    fun `a corner outside a zone is not held to the limit its own leg clips`() {
        val zone = TautZoneShape(
            id = "corner",
            name = "Corner 5 kn",
            limitKn = 5.0,
            outerRing = rectM(0.0, 0.0, 1000.0, 1000.0)
        )
        val start = at(-1500.0, -800.0)
        val inside = at(500.0, 500.0)
        val (_, _, graph) = mapOf(world(zones = listOf(zone)), start, inside)

        val corner = vertexNear(graph, at(0.0, 0.0))
        assertTrue("the zone's south-west corner is a node of the graph", corner >= 0)
        assertEquals(
            "the corner stands outside the zone, so nothing caps it",
            Double.MAX_VALUE,
            graph.limitAt(corner),
            0.0
        )
        val clipping = graph.edgesFrom(corner).firstOrNull { graph.zoneOf(it) >= 0 } ?: -1
        assertTrue(
            "and the leg leaving it really shares the zone's interior, so the case is a clip",
            clipping >= 0
        )
        assertEquals(
            "which a per-leg maximum would size the corner at — the revert this test pins",
            5.0,
            graph.pricing.regionsOf(
                graph.vertices[corner], graph.vertices[graph.other(clipping, corner)]
            ).maxOf { it.limitKn },
            0.0
        )
        assertEquals(
            "while a vertex standing inside the zone reads the zone's own limit",
            5.0,
            graph.limitAt(vertexNear(graph, inside)),
            0.0
        )
    }

    // ── 19 · a rule refusal is produced, counted as the rule's and charged nothing ──

    /**
     * **The one case where the run's own rule refuses a corner — and the one that makes `sharpByRule` a
     * reading rather than a nought.**
     *
     * The band is a zone across the way with a way round it, and the aim stands east of its northern
     * edge, so the taut line runs `start → the zone's north-west corner → aim`: one corner, whose water
     * is legal (a zone is not a wall) and whose **rounding** cuts into the zone's interior, south of the
     * boundary the legs run along. Run one forbids that interior, so the curve is refused by the run's
     * own promise and the corner stays sharp — counted as **the rule's**, apart from the water's, and
     * driven at the water's own reference speed, so §12.3's brake-and-accelerate pair costs it nothing.
     *
     * The second reading is what "charged nothing" means as a measurement rather than a sentence: remove
     * the longitudinal ceiling and the ETA does not move, because the term that would carry a charge is
     * the one this refusal never pays. `TautEasingTest` and `TautSearchTest`'s reference each pinned the
     * rule with a lambda or a duplicated predicate, and both worked around the search's own branch; this
     * case is produced by a world instead.
     */
    @Test
    fun `a rule refusal is produced, counted as the rule's and charged nothing`() = runBlocking {
        val zone = TautZoneShape(
            id = "corner",
            name = "Corner 5 kn",
            limitKn = 5.0,
            outerRing = rectM(-300.0, -800.0, 300.0, 800.0)
        )
        val start = at(-1500.0, 0.0)
        val aim = at(1500.0, 800.0)

        val answer = engine(world(zones = listOf(zone))).route(start, aim, cruiseKn)
        assertTrue("the zone-carrying world answers: $answer", answer is RouteResult.Success)
        val success = answer as RouteResult.Success
        val details = success.details as TautRouteDetails
        assertTrue("and it is run one that answered, so the rule was in force", !details.zonePricedRun)
        assertEquals(
            "the corner whose rounding would enter the zone is the rule's own refusal, and it is one",
            1,
            details.sharpByRule
        )
        assertEquals("counted apart from the water's, which made no refusal here", 0, details.sharpByWater)
        assertEquals("one corner in the line, and it is that one", 1, details.sharpCorners)
        assertEquals(
            "and it is charged nothing — a rule refusal forces no slowdown",
            0.0,
            details.sharpChargeSec,
            0.0
        )

        val free = engine(world(zones = listOf(zone)), longitudinalMps2 = 0.0)
            .route(start, aim, cruiseKn) as RouteResult.Success
        assertEquals(
            "with the brake-and-accelerate ceiling removed the ETA does not move",
            success.durationSec,
            free.durationSec,
            1e-9
        )
    }

    /** The graph node standing within a metre of [point], or `-1` when the graph has none. */
    private fun vertexNear(graph: TautGraph, point: RoutePoint): Int {
        for ((index, vertex) in graph.vertices.withIndex()) {
            val metres = SpatialOperations.haversine(
                LatLng(vertex.latitude, vertex.longitude),
                LatLng(point.latitude, point.longitude)
            )
            if (metres < 1.0) return index
        }
        return -1
    }

    /** The least distance from a point to any **undilated** harvested wall, in metres. */
    private fun distanceToRawWalls(obstacles: TautObstacles, point: RoutePoint): Double {
        val p = obstacles.frame.pt(LatLng(point.latitude, point.longitude))
        var best = Double.MAX_VALUE
        for (wall in obstacles.rawWalls) {
            val points = wall.points
            val last = if (wall.closed) points.size else points.size - 1
            for (i in 0 until last) {
                val a = obstacles.frame.pt(points[i])
                val b = obstacles.frame.pt(points[(i + 1) % points.size])
                best = minOf(best, pointSegmentDistance(p, a, b))
            }
        }
        return best
    }
}
