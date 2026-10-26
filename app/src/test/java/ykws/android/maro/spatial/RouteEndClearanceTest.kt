package ykws.android.maro.spatial

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.data.model.RouteResult
import ykws.android.maro.spatial.multipass.EvolutiveGridPlan
import ykws.android.maro.spatial.multipass.GateRule
import ykws.android.maro.spatial.multipass.GateTestFixtures
import ykws.android.maro.spatial.multipass.GateTestFixtures.at
import ykws.android.maro.spatial.multipass.MultipassWorld
import java.util.Collections
import kotlin.math.abs
import kotlin.math.max

/**
 * **A route's ends stand clear of the shore and the shallows** — an end tapped nearer than
 * `route.repair.clearanceM` to the measured 3 m line or the coast is moved to the nearest clear point, never
 * across land, and the answer says so; where no clear point stands, the wall's own clearance and then the
 * old water rule take it, so an end the old repair accepted is never refused.
 */
class RouteEndClearanceTest {

    private val paceKn = 5.0
    private val clearM = AppConfig.routeRepairClearanceM

    /** A straight shelf, shallow south of 100 m, on a 2 m raster 600 m east to west. */
    private fun shelf() = GateTestFixtures.raster(250, 300, 2.0) { n, _ -> if (n < 100.0) 2.0 else 10.0 }

    private fun engineOver(world: MultipassWorld, trace: MutableList<String>) = RouteAvoidEngine(
        paceKn = { paceKn },
        verdictWeight = { AppConfig.routeAvoidSlowWaterWeight },
        worldProvider = { world },
        plan = EvolutiveGridPlan,
        traceSink = { line -> trace += line }
    )

    private fun point(p: LatLng) = RoutePoint(p.latitude, p.longitude)

    private fun gateOf(world: MultipassWorld) =
        GateRule(world.shallowEdges(GateTestFixtures.MIN_DEPTH_M), 10.0, 20.0, 20.0)

    /** The main rung's answer, or `null` where it lands none. */
    private fun solveMain(engine: RouteAvoidEngine, from: RoutePoint, to: RoutePoint): RouteResult.Success? = runBlocking {
        val declared = engine.routesToCompute(from, to) as? RouteDeclarations.Available ?: return@runBlocking null
        coroutineScope {
            val subscribed = CompletableDeferred<Unit>()
            val done = CompletableDeferred<RouteUpdate?>()
            val collector = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                engine.updates.onStart { subscribed.complete(Unit) }
                    .collect { u -> if (u.nextStage == null && !done.isCompleted) done.complete(u) }
            }
            subscribed.await()
            engine.startLookup(declared.computations[0].id)
            val update = withTimeout(120_000) { done.await() }
            collector.cancel()
            update?.result as? RouteResult.Success
        }
    }

    @Test
    fun anEndNearTheShallowsMovesToTheNearestClearPointAndSaysSo() {
        val world = GateTestFixtures.RasterWorld(shelf())
        val gate = gateOf(world)
        val trace = Collections.synchronizedList(mutableListOf<String>())
        val start = at(105.0, 150.0)
        val aim = at(103.0, 450.0)
        val route = solveMain(engineOver(world, trace), point(start), point(aim))
        assertNotNull("the pair lands", route)
        val first = route!!.points.first().toLatLng()
        val last = route.points.last().toLatLng()
        for ((end, raw) in listOf(first to start, last to aim)) {
            val edge = gate.edgeM(end, 100.0)
            assertTrue("the moved end stands $clearM m clear (read $edge)", edge >= clearM - 1e-6)
            assertTrue("and no more than a metre past it — the nearest clear point (read $edge)", edge <= clearM + 1.0)
            assertTrue("it moved straight off the shelf", abs(end.longitude - raw.longitude) < 1e-5)
        }
        assertTrue("the aim is said to have moved", route.destinationMoved)
        assertEquals("the trace names both moves", 2, trace.count { it.startsWith("REPAIR") && it.contains("moved=true rule=clear") })
    }

    @Test
    fun anEndAlreadyClearStaysWhereItWasTapped() {
        val world = GateTestFixtures.RasterWorld(shelf())
        val start = at(200.0, 150.0)
        val aim = at(200.0, 450.0)
        val route = solveMain(engineOver(world, mutableListOf()), point(start), point(aim))
        assertNotNull(route)
        assertEquals(point(start), route!!.points.first())
        assertEquals(point(aim), route.points.last())
        assertFalse(route.destinationMoved)
    }

    /** A 40 m channel holds no point 25 m clear: an end on its middle stays, by the wall's own clearance. */
    @Test
    fun anEndInAChannelNarrowerThanTwiceTheClearanceTakesTheWallRule() {
        // Wide enough that no point within the repair's reach lies off the raster, where no data reads deep.
        val raster = GateTestFixtures.raster(600, 600, 2.0) { n, _ -> if (abs(n - 600.0) < 20.0) 10.0 else 2.0 }
        val world = GateTestFixtures.RasterWorld(raster)
        val trace = mutableListOf<String>()
        val declared = engineOver(world, trace).routesToCompute(point(at(600.0, 500.0)), point(at(600.0, 700.0)))
        assertTrue("the pair is declared, not refused", declared is RouteDeclarations.Available)
        assertEquals(2, trace.count { it.startsWith("REPAIR") && it.contains("moved=false rule=wall") })
    }

    /** An end deep in shallow water with no wall-clear point within reach keeps the old rule — never refused. */
    @Test
    fun anEndTheOldRuleAcceptedIsNeverRefused() {
        // A 12 m slot of deep water through a wide shoal: deep enough for the gate, never clear of its wall.
        val raster = GateTestFixtures.raster(250, 300, 2.0) { n, _ -> if (abs(n - 250.0) < 6.0) 10.0 else 2.0 }
        val world = GateTestFixtures.RasterWorld(raster)
        val trace = mutableListOf<String>()
        val declared = engineOver(world, trace).routesToCompute(point(at(250.0, 300.0)), point(at(250.0, 320.0)))
        assertTrue("the old rule's end is declared, not refused", declared is RouteDeclarations.Available)
        assertTrue(trace.any { it.startsWith("REPAIR end=start") && it.contains("rule=water") })
    }

    /** A shelf to the south and a short mole 4 m north of the start: land the move may never cross. */
    private class MoleWorld(raster: ykws.android.maro.data.model.DepthGrid) : MultipassWorld by GateTestFixtures.RasterWorld(raster) {
        val moleS = 108.0
        val moleN = 112.0
        val moleW = 295.0
        val moleE = 305.0

        fun northOf(lat: Double) = (lat - GateTestFixtures.LAT0) * GateTestFixtures.M_PER_DEG_LAT
        fun eastOf(lon: Double) = (lon - GateTestFixtures.LON0) * GateTestFixtures.M_PER_DEG_LON

        override fun isWater(latitude: Double, longitude: Double): Boolean {
            val n = northOf(latitude)
            val e = eastOf(longitude)
            return !(n in moleS..moleN && e in moleW..moleE)
        }

        override fun distanceToCoastM(latitude: Double, longitude: Double): Double {
            val n = northOf(latitude)
            val e = eastOf(longitude)
            val dn = max(0.0, max(moleS - n, n - moleN))
            val de = max(0.0, max(moleW - e, e - moleE))
            return kotlin.math.sqrt(dn * dn + de * de)
        }

        /** The mole's own ring, the edges the repair's land test reads. */
        override fun segmentsIn(box: ykws.android.maro.data.model.markers.BBox): List<ykws.android.maro.spatial.multipass.MultipassEdge> {
            val sw = at(moleS, moleW)
            val se = at(moleS, moleE)
            val ne = at(moleN, moleE)
            val nw = at(moleN, moleW)
            return listOf(sw to se, se to ne, ne to nw, nw to sw).map { (a, b) ->
                ykws.android.maro.spatial.multipass.MultipassEdge(a, b, LandRingOrientation.CCW_RING)
            }
        }
    }

    private fun repairedAt(line: String): LatLng {
        val at = Regex("at=([0-9.]+),([0-9.]+)").find(line)!!.groupValues
        return LatLng(at[1].toDouble(), at[2].toDouble())
    }

    /** Whether the straight from [from] to [to], once it first stands on water, stays on water. */
    private fun staysOnWaterOnceOn(world: MultipassWorld, from: LatLng, to: LatLng): Boolean {
        var entered = false
        for (k in 0..400) {
            val t = k / 400.0
            val wet = world.isWater(from.latitude + (to.latitude - from.latitude) * t, from.longitude + (to.longitude - from.longitude) * t)
            if (wet) entered = true else if (entered) return false
        }
        return entered
    }

    /**
     * The start stands 4 m south of a mole with a shoal behind it: the nearest water clear of the gate lies
     * straight across the mole, which the move may not take, so it goes round the mole's head.
     */
    @Test
    fun aMoveNeverCrossesLand() {
        val world = MoleWorld(shelf())
        val trace = mutableListOf<String>()
        val raw = at(104.0, 300.0)
        engineOver(world, trace).routesToCompute(point(raw), point(at(300.0, 300.0)))
        val line = trace.single { it.startsWith("REPAIR end=start") }
        val moved = repairedAt(line)
        assertTrue("the start moved ($line)", line.contains("moved=true"))
        assertTrue("its move stays on water", staysOnWaterOnceOn(world, raw, moved))
        assertTrue("so it did not go straight across the mole", abs(world.eastOf(moved.longitude) - 300.0) > 5.0)
    }

    /** A start tapped on the mole itself walks off it, held clear as any other end. */
    @Test
    fun anEndTappedOnLandWalksOffItToClearWater() {
        val world = MoleWorld(shelf())
        val trace = mutableListOf<String>()
        engineOver(world, trace).routesToCompute(point(at(110.0, 300.0)), point(at(300.0, 300.0)))
        val line = trace.single { it.startsWith("REPAIR end=start") }
        val moved = repairedAt(line)
        assertTrue("the start moved off the mole ($line)", line.contains("moved=true") && world.isWater(moved.latitude, moved.longitude))
        assertFalse("by one of the new rules, not the old first-water one", line.contains("rule=water"))
    }

    /**
     * The repair's budget in its worst case — no point clear of the wall anywhere in reach, so both new rules
     * sweep their whole reach and the old rule decides — on a world at the device's own 20 m resolution whose
     * edge index is cold.
     */
    @Test
    fun theRepairAnswersWithinItsBudgetInItsWorstCase() {
        // One row of 3.5 m nodes through a wide shoal: deep enough for the gate, its 3 m line ~13 m apart.
        val raster = GateTestFixtures.raster(80, 80, 20.0) { n, _ -> if (abs(n - 810.0) < 5.0) 3.5 else 2.0 }
        val world = GateTestFixtures.RasterWorld(raster)
        val trace = mutableListOf<String>()
        val engine = engineOver(world, trace)
        val started = System.nanoTime()
        engine.routesToCompute(point(at(810.0, 700.0)), point(at(810.0, 900.0)))
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        assertEquals("both ends fell through to the old rule: $trace", 2, trace.count { it.contains("rule=water") })
        assertTrue("the repair answers in $elapsedMs ms, under the 50 ms budget", elapsedMs <= 50)
    }
}
