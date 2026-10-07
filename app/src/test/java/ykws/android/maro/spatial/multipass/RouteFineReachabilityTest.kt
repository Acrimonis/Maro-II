package ykws.android.maro.spatial.multipass

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.DepthSample
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.data.regulation.SpeedZone

/**
 * **The fine stage's reachability pin** — one test that fails when a plan handed to the engine reaches no
 * fine walk, so a walk nobody runs can never again be changed without the suite saying so.
 *
 * Both shipped plans **retire the re-search**: [`UniformGridPlan.secondPassRegions`] and
 * [`EvolutiveGridPlan.secondPassRegions`] answer `emptyList()`, so [`RouteFinePass.fineReSearch`] returns
 * before it ever builds its walk — which is exactly how the parked price-step plan's Phase 1 shipped
 * unread. The pin therefore hands the fine stage a **region-restoring decorator** of each shipped plan,
 * the first walk and the fine cell untouched, so the walk is reached and its own part asserted; emptying
 * that decorator's regions turns the test red, which is what proves it catches the case it exists for.
 *
 * The refinement half needs no such decorator — [`RouteFinePass.finePass`] runs for both shipped plans
 * whatever the region answers — so the pin asserts it too, and a plan that reached neither walk would
 * fail the same test.
 */
class RouteFineReachabilityTest {

    private val from = RoutePoint(43.5000, 7.0000)
    private val to = RoutePoint(43.5000, 7.0600)
    private val pace = 28.0
    private val lambda = AppConfig.routeAvoidSpeedZoneSoftCostAversion

    /** The line the fine stage refines: the pair's own straight, crossing the priced zone below. */
    private val line = listOf(from.toLatLng(), to.toLatLng())

    /** A 5 kn zone the line crosses, so the crossing half of [`RouteFinePass.finePass`] fires too. */
    private val zone = SpeedZone("z", "Cap", 5.0, rectRing(43.49, 43.51, 7.02, 7.04))

    private val world = ZoneWorld(listOf(zone))

    @After
    fun restoreTheSpeedZoneSwitch() {
        setAvoidSwitch("routeAvoidSpeedZoneEnabled", false)
    }

    /**
     * **The pin.** For each shipped plan's own first walk and fine cell — the decorator restores only the
     * region the retirement removed — the fine stage produces both of its parts: the refinement's own
     * settled pull, and the re-search's walk, whose `answered=` line only a walk that ran can emit.
     */
    @Test
    fun theFineStageProducesBothItsPartsForEachShippedPlanWithARegion() = runBlocking {
        setAvoidSwitch("routeAvoidSpeedZoneEnabled", true)

        for (inner in listOf<RouteGridPlan>(UniformGridPlan, EvolutiveGridPlan)) {
            val plan = RegionPlan(inner)
            val ctx = RouteGridBuilder(plan).buildGrid(
                world, from, to, AppConfig.routeAvoidCorridorReachM, pace
            )
            assertTrue("$inner reaches a corridor context", ctx != null)

            val refineTrace = mutableListOf<String>()
            val fine = RouteFinePass(plan)
            val refined = fine.finePass(ctx!!, line, lambda) { refineTrace += it() }
            assertTrue(
                "$inner: the refinement runs, so its settled pull is produced",
                refineTrace.any { it.startsWith("FINE settled ") }
            )

            val researchTrace = mutableListOf<String>()
            fine.fineReSearch(ctx, refined, lambda) { researchTrace += it() }
            assertTrue(
                "$inner: the re-search walks, so its own answered line is produced",
                researchTrace.any { it.contains("FINE research answered=") }
            )
        }
    }

    /**
     * **The case the pin exists to catch.** A shipped plan answers no region, so the re-search never
     * builds a walk — the empty-region short circuit — while the refinement still runs. This is the
     * assertion the pin fails on when the decorator's regions are emptied again.
     */
    @Test
    fun anEmptyRegionStopsBeforeTheReSearchWalk() = runBlocking {
        setAvoidSwitch("routeAvoidSpeedZoneEnabled", true)
        val plan: RouteGridPlan = UniformGridPlan
        val ctx = RouteGridBuilder(plan).buildGrid(
            world, from, to, AppConfig.routeAvoidCorridorReachM, pace
        )!!
        val fine = RouteFinePass(plan)

        val refineTrace = mutableListOf<String>()
        val refined = fine.finePass(ctx, line, lambda) { refineTrace += it() }
        assertTrue(
            "the refinement runs for the shipped plan too",
            refineTrace.any { it.startsWith("FINE settled ") }
        )

        val researchTrace = mutableListOf<String>()
        fine.fineReSearch(ctx, refined, lambda) { researchTrace += it() }
        assertTrue(
            "the shipped plan's empty region is read as nothing to re-search",
            researchTrace.any { it.contains("FINE research box=empty") }
        )
        assertFalse(
            "so the re-search walk is never reached",
            researchTrace.any { it.contains("FINE research answered=") }
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** A shipped plan with the region the retirement removed put back: the interface's own default. */
    private class RegionPlan(private val inner: RouteGridPlan) : RouteGridPlan {
        override fun firstWalkGrid(corridor: BBox, baseCellM: Double): List<GridTile> =
            inner.firstWalkGrid(corridor, baseCellM)

        override fun fineCellM(baseCellM: Double): Double = inner.fineCellM(baseCellM)
        // secondPassRegions: the interface default — the line-span region the retirement emptied.
    }

    /** A water-everywhere world whose only source is its speed zones — the corridor reads no land or depth. */
    private class ZoneWorld(private val zones: List<SpeedZone>) : MultipassWorld {
        override val coastlineReady: Boolean get() = true
        override val depthReady: Boolean get() = true
        override val bandWidthM: Double get() = 0.0
        override val regionBounds: BBox? get() = null

        override fun segmentsIn(box: BBox): List<MultipassEdge> = emptyList()
        override fun openCoastIn(box: BBox): List<List<LatLng>> = emptyList()
        override fun isWater(latitude: Double, longitude: Double): Boolean = true
        override fun distanceToCoastM(latitude: Double, longitude: Double): Double = Double.MAX_VALUE
        override fun depthAt(latitude: Double, longitude: Double): DepthSample = DepthSample.NONE
        override fun speedZonesIn(box: BBox): List<SpeedZone> = speedZonesInBox(zones, box, emptySet())
        override fun zoneLimitKnAt(latitude: Double, longitude: Double): Double? =
            strictestLimitKnAt(zones, emptySet(), latitude, longitude)
    }

    /** A closed rectangle ring over `[latSouth, latNorth]` × `[lonWest, lonEast]`. */
    private fun rectRing(latSouth: Double, latNorth: Double, lonWest: Double, lonEast: Double): List<LatLng> =
        listOf(
            LatLng(latSouth, lonWest),
            LatLng(latSouth, lonEast),
            LatLng(latNorth, lonEast),
            LatLng(latNorth, lonWest),
            LatLng(latSouth, lonWest)
        )

    /** Flips an [AppConfig] avoid switch for one test; [restoreTheSpeedZoneSwitch] puts it back. */
    private fun setAvoidSwitch(name: String, value: Boolean) {
        val field = AppConfig::class.java.getDeclaredField(name)
        field.isAccessible = true
        field.setBoolean(AppConfig, value)
    }
}
