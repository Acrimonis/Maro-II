package ykws.android.maro.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.BBox

/**
 * The scenarios of plan §13.3, and the assertions that make Stage 0 a measurement rather than an
 * opinion: a path exists, no returned sample is land or too shallow, the search agrees with a plain
 * Dijkstra on the same graph, two runs are identical, and a case with no answer says why.
 */
class RouteScenarioTest {

    private val bbox = BBox(latSouth = 43.50, latNorth = 43.52, lonWest = 7.00, lonEast = 7.02)
    private val config = RouteConfig()

    /** Scenario 2 — a wall with one gap, so the shortest legal route is known by hand. */
    private fun wallAssets() = RouteHarness.syntheticAssets(
        bbox,
        depthAt = { lat, lon ->
            if (lon in 7.0095..7.0105 && lat !in 43.5050..43.5090) 1.0f else 20f
        },
    )

    private val start = LatLng(43.5100, 7.0050)
    private val end = LatLng(43.5100, 7.0150)

    private fun run(start: LatLng = this.start, end: LatLng = this.end): RouteEngine.RouteRun {
        val assets = wallAssets()
        return RouteEngine.compute(start, end, config, assets.grid, assets.coast, assets.zones, assets.zoneIndex)
    }

    @Test
    fun `scenario 2 - the route leaves the straight line to find the gap, and stays legal`() {
        val result = run()
        val attempts = result.attempts.joinToString("\n")
        assertTrue("no route, and the attempts say why:\n$attempts", result.outcome is RouteOutcome.Found)
        val plan = (result.outcome as RouteOutcome.Found).plan

        assertTrue("the straight line is blocked, so the route bends: ${plan.straightLineRatio}", plan.straightLineRatio > 1.05)
        assertTrue("the route dips into the gap band", plan.points.any { it.latitude in 43.5045..43.5095 })
        assertNotNull(result.validation)
        assertTrue(
            "no sample is land, too shallow or inside the standoff: ${result.validation!!.summary}",
            result.validation!!.clear,
        )
        assertTrue("the ETA is finite and positive", plan.etaS > 0.0 && plan.etaS.isFinite())
        assertTrue("and the search explored states", plan.measure.nodesExpanded > 0)
    }

    @Test
    fun `two runs are identical - a navigation aid must be predictable`() {
        val first = (run().outcome as RouteOutcome.Found).plan
        val second = (run().outcome as RouteOutcome.Found).plan
        assertEquals(first.points, second.points)
        assertEquals(first.distanceM, second.distanceM, 0.0)
        assertEquals(first.timeS, second.timeS, 0.0)
        assertEquals(first.measure.nodesExpanded, second.measure.nodesExpanded)
    }

    @Test
    fun `the turn-aware search matches a plain Dijkstra once corners are priced at nothing`() {
        val assets = wallAssets()
        val exclusion = RouteExclusion.build(assets.grid, assets.coast, config, bbox)
        val obstacles = RouteObstacles.harvest(exclusion, assets.zones, config)
        val context = AssetRouteContext(assets.grid, assets.coast, assets.zoneIndex, exclusion)
        val cost = RouteCostModel(config, context)
        val graph = RouteGraphBuilder(config, context, obstacles, cost).build(start, end)

        // Corners are what make the two objectives differ, so they are switched off for the reference
        // check: with no lateral ceiling the fillet has no radius to slow a corner down with.
        val freeCorners = config.copy(maxLateralG = 1e9, longitudinalG = 0.0)
        val path = RouteSearch.search(graph, 0, 1, freeCorners, context, obstacles)
        assertNotNull("the corridor is connected", path)
        assertEquals(
            "A* must equal the reference on the same graph",
            RouteSearch.dijkstraEdgeTimeS(graph, 0, 1),
            path!!.edgeTimeS,
            1e-6,
        )

        // With corners priced, A* may only do better than it would were they free — never worse than
        // the leg-time optimum by more than the corners it chose to accept.
        val priced = RouteSearch.search(graph, 0, 1, config, context, obstacles)!!
        assertTrue(priced.edgeTimeS + 1e-9 >= RouteSearch.dijkstraEdgeTimeS(graph, 0, 1))
        assertTrue("and it never beats the reference on leg time", priced.edgeTimeS >= path.edgeTimeS - 1e-9)
    }

    @Test
    fun `scenario 3 - a boxed-in request answers with a reason, never a null`() {
        val assets = RouteHarness.syntheticAssets(
            bbox,
            depthAt = { lat, lon ->
                if (lat in 43.5090..43.5110 && lon in 7.0090..7.0110) 20f else 1.0f
            },
        )
        val result = RouteEngine.compute(
            start = LatLng(43.5100, 7.0100),
            end = LatLng(43.5100, 7.0150),
            config = config,
            grid = assets.grid,
            coast = assets.coast,
            zones = assets.zones,
            zoneIndex = assets.zoneIndex,
        )
        val verdict = result.outcome
        assertTrue("a boxed-in destination is a stated answer", verdict is RouteOutcome.NoRoute)
        assertTrue((verdict as RouteOutcome.NoRoute).reason.isNotBlank())
        assertTrue("and every attempt is reported", result.attempts.isNotEmpty())
    }

    @Test
    fun `scenario 1 - the acceptance case, when the baked region and the track files are present`() {
        val assets = RouteHarness.loadAssets()
        Assume.assumeTrue("no baked region under data/app-assets — the acceptance run needs one", assets != null)
        // The endpoint pair is read from the two tracks, and each endpoint is the first (start) and
        // furthest (destination) fix the depth gate accepts: a track's first fix is a berth, and the
        // Salis berth reads 0.30 m in the shipped grid.
        val scenario = RouteHarness.acceptanceScenario(accept = { RouteHarness.depthGate(assets!!, config, it) })
        Assume.assumeTrue("the acceptance track files are not in the tree", scenario != null)

        // D3 both ways, which is what §13.4 asks the harness to compare: the hard standoff dilates the
        // exclusion geometry, the priced one only penalises it — and the difference is measured here
        // rather than argued (the derived start is 7 m from the coast, so the hard variant refuses it).
        val variants = listOf(
            "hard standoff" to config,
            "priced standoff, band300 off" to config.copy(hardStandoff = false, band300IsZone = false),
        )
        for ((label, variant) in variants) {
            val result = RouteHarness.run(scenario!!, variant, assets!!)
            println("### $label")
            println(RouteHarness.report(scenario, variant, result))

            when (val verdict = result.outcome) {
                is RouteOutcome.Found -> {
                    assertNotNull(result.validation)
                    assertTrue(
                        "a route accepted under $label must clear every hard constraint: ${result.validation!!.summary}",
                        result.validation!!.clear,
                    )
                    assertTrue(verdict.plan.distanceM > 0.0)
                    assertTrue(verdict.plan.etaS > 0.0)
                }

                is RouteOutcome.NoRoute -> assertTrue(
                    "a no-route verdict under $label states why: ${verdict.reason}",
                    verdict.reason.isNotBlank(),
                )
            }
        }
    }
}
