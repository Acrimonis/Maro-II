package ykws.android.maro.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.spatial.SpatialOperations
import kotlin.math.PI

/**
 * The speed profile of plan §11.5 and §12 — the fillet geometry, the speeds it derives, the ETA, and
 * the validator that re-checks the emitted track rather than trusting it.
 */
class RouteSpeedProfileTest {

    private val bbox = BBox(latSouth = 43.50, latNorth = 43.52, lonWest = 7.00, lonEast = 7.02)
    private val config = RouteConfig()

    /** Open, deep water with no obstacles at all: every corner fits and the radii are unconstrained. */
    private fun openWater(): Triple<RouteContext, RouteObstacles, RouteExclusion> {
        val assets = RouteHarness.syntheticAssets(bbox, depthAt = { _, _ -> 20f })
        val exclusion = RouteExclusion.build(assets.grid, assets.coast, config, bbox)
        val obstacles = RouteObstacles.harvest(exclusion, assets.zones, config)
        val context = AssetRouteContext(assets.grid, assets.coast, assets.zoneIndex, exclusion)
        return Triple(context, obstacles, exclusion)
    }

    @Test
    fun `a straight run needs no corner and costs nothing`() {
        val (context, obstacles, _) = openWater()
        val corner = RouteFillet.plan(
            prev = LatLng(43.5100, 7.0050),
            corner = LatLng(43.5100, 7.0070),
            next = LatLng(43.5100, 7.0090),
            prevSpeedMps = config.cruiseMps,
            nextSpeedMps = config.cruiseMps,
            prevLegM = 160.0,
            nextLegM = 160.0,
            config = config,
            context = context,
            obstacles = obstacles,
        )
        assertTrue(corner.fitted)
        assertEquals(0.0, corner.radiusM, 1e-9)
        assertEquals(0.0, corner.arcLengthM, 1e-9)
        assertEquals(0.0, corner.totalTimeS, 1e-9)
        assertTrue(corner.points.isEmpty())
    }

    @Test
    fun `a right angle in open water is rounded to the radius at which the corner costs no slowdown`() {
        val (context, obstacles, _) = openWater()
        val cornerPlan = RouteFillet.plan(
            prev = LatLng(43.5080, 7.0100),
            corner = LatLng(43.5100, 7.0100),
            next = LatLng(43.5100, 7.0120),
            prevSpeedMps = config.cruiseMps,
            nextSpeedMps = config.cruiseMps,
            prevLegM = 220.0,
            nextLegM = 220.0,
            config = config,
            context = context,
            obstacles = obstacles,
        )

        assertTrue("the circuit must close", cornerPlan.fitted)
        assertEquals("r_min(cruise) = v² ÷ a", config.cruiseMps * config.cruiseMps / config.lateralMps2, cornerPlan.radiusM, 1e-6)
        assertEquals(PI / 2, cornerPlan.sweepRad, 1e-9)
        assertEquals(cornerPlan.radiusM * PI / 2, cornerPlan.arcLengthM, 1e-6)
        assertEquals(config.cruiseMps, cornerPlan.speedMps, 1e-9)
        assertEquals("at r_min(cruise) the corner costs no braking", 0.0, cornerPlan.penaltyS, 1e-9)
        assertEquals(config.arcTimeS(cornerPlan.radiusM, PI / 2), cornerPlan.arcTimeS, 1e-9)

        // The arc runs from the tangent point on the incoming leg to the one on the outgoing leg.
        val corner = LatLng(43.5100, 7.0100)
        assertEquals(cornerPlan.tangentM, SpatialOperations.haversine(cornerPlan.startTangent, corner), 0.6)
        assertEquals(cornerPlan.tangentM, SpatialOperations.haversine(cornerPlan.endTangent, corner), 0.6)
    }

    @Test
    fun `a corner with no room is narrowed - and only then driven slower`() {
        val (_, obstacles, _) = openWater()
        // A patch of forbidden water sits inside the corner, 30 m from the vertex on the bisector —
        // where a wide arc swings but neither leg runs. The wide candidates must shrink around it:
        // a horizontal line could not do this job, because any such line between the two legs is
        // crossed by the trimmed leg and would refuse every candidate.
        val corner = LatLng(43.5100, 7.0100)
        val patch = SpatialOperations.pointAlongBearing(corner.latitude, corner.longitude, 135.0, 30.0)
        val context = RouteHarness.fakeContext(
            forbidden = { lat, lon -> SpatialOperations.haversine(LatLng(lat, lon), patch) < 8.0 },
        )

        val cornerPlan = RouteFillet.plan(
            prev = LatLng(43.5080, 7.0100),
            corner = LatLng(43.5100, 7.0100),
            next = LatLng(43.5100, 7.0120),
            prevSpeedMps = config.cruiseMps,
            nextSpeedMps = config.cruiseMps,
            prevLegM = 220.0,
            nextLegM = 220.0,
            config = config,
            context = context,
            obstacles = obstacles,
        )

        assertTrue(cornerPlan.fitted)
        assertTrue("narrower than the cruise radius: ${cornerPlan.radiusM}", cornerPlan.radiusM < config.cruiseCornerRadiusM)
        assertTrue("and driven slower, never refused", cornerPlan.speedMps < config.cruiseMps)
        assertTrue("which costs the brake-and-accelerate pair", cornerPlan.penaltyS > 0.0)
        assertEquals(config.cornerSpeedMps(cornerPlan.radiusM), cornerPlan.speedMps, 1e-9)
    }

    @Test
    fun `the profile of a straight three point path is its own arithmetic`() {
        val (context, obstacles, _) = openWater()
        val points = listOf(
            LatLng(43.5100, 7.0050),
            LatLng(43.5100, 7.0070),
            LatLng(43.5100, 7.0090),
        )
        val len01 = SpatialOperations.haversine(points[0], points[1])
        val len12 = SpatialOperations.haversine(points[1], points[2])
        val graph = RouteGraph(
            points,
            listOf(
                RouteEdge(0, 1, len01, len01 / config.cruiseMps),
                RouteEdge(1, 2, len12, len12 / config.cruiseMps),
            ),
        )
        val path = RoutePath(listOf(0, 1, 2), points, len01 / config.cruiseMps + len12 / config.cruiseMps, 0.0, 3)
        val cost = RouteCostModel(config, context)
        val measure = RouteMeasure(3, 2, 4, 3, 1, 1852.0, config.standoffM, 6.25)

        val plan = RouteProfileBuilder(config, cost, context, obstacles).build(path, graph, measure)

        assertEquals(3, plan.points.size)
        assertEquals(2, plan.legs.size)
        assertEquals(0, plan.cornerCount)
        assertEquals(len01 + len12, plan.distanceM, 1e-6)
        assertEquals(plan.distanceM / config.cruiseMps, plan.timeS, 1e-6)
        assertEquals(1.0, plan.straightLineRatio, 1e-6)
        assertEquals("free water leaves no limit to report", 0, plan.legs.first().limitsKn.size)
    }

    @Test
    fun `the validator refuses a track that crosses the excluded set`() {
        val assets = RouteHarness.syntheticAssets(
            bbox,
            depthAt = { _, lon -> if (lon in 7.0095..7.0105) 1.0f else 20f },
        )
        val exclusion = RouteExclusion.build(assets.grid, assets.coast, config, bbox)
        val obstacles = RouteObstacles.harvest(exclusion, assets.zones, config)
        val context = AssetRouteContext(assets.grid, assets.coast, assets.zoneIndex, exclusion)

        val throughTheWall = listOf(LatLng(43.5100, 7.0050), LatLng(43.5100, 7.0150))
        val result = RouteValidator.validate(throughTheWall, config, context, obstacles)
        assertFalse(result.clear)
        assertTrue("the samples inside the wall are counted: ${result.summary}", result.blockedSamples > 0)
        assertTrue(result.violating.isNotEmpty())

        val clearOfIt = listOf(LatLng(43.5100, 7.0050), LatLng(43.5100, 7.0090))
        assertTrue(RouteValidator.validate(clearOfIt, config, context, obstacles).clear)
    }
}
