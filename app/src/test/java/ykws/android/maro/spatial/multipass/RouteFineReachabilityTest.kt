package ykws.android.maro.spatial.multipass

import kotlinx.coroutines.runBlocking
import org.junit.After
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
 * The walk that runs is [`RouteFinePass.finePass`] — the refinement along the settled line — and both
 * shipped plans reach it whatever their own answers, so the pin hands each plan to the grid builder and
 * asserts the refinement's own settled pull is produced. A plan whose fine cell is at or above its coarse
 * one reaches no fine walk, and the assertion turns red on it, which is what proves the pin catches the
 * case it exists for.
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
     * **The pin.** For each shipped plan's own first walk and fine cell, the fine stage reaches its
     * refinement, so its settled pull — the `FINE settled` line only a walk that ran can emit — is
     * produced.
     */
    @Test
    fun theFineStageReachesItsRefinementForEachShippedPlan() = runBlocking {
        setAvoidSwitch("routeAvoidSpeedZoneEnabled", true)

        for (plan in listOf<RouteGridPlan>(UniformGridPlan, EvolutiveGridPlan)) {
            val ctx = RouteGridBuilder(plan).buildGrid(
                world, from, to, AppConfig.routeAvoidCorridorReachM, pace, 1.0
            )
            assertTrue("$plan reaches a corridor context", ctx != null)

            val refineTrace = mutableListOf<String>()
            RouteFinePass().finePass(ctx!!, line, lambda) { refineTrace += it() }
            assertTrue(
                "$plan reaches the fine walk, so its refinement's settled pull is produced",
                refineTrace.any { it.startsWith("FINE settled ") }
            )
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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
