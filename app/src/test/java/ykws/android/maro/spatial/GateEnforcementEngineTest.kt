package ykws.android.maro.spatial

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.spatial.multipass.MultipassWorld
import ykws.android.maro.data.model.DepthSource
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.data.model.RouteResult
import ykws.android.maro.spatial.multipass.EvolutiveGridPlan
import ykws.android.maro.spatial.multipass.GateRule
import ykws.android.maro.spatial.multipass.GateTestFixtures
import ykws.android.maro.spatial.multipass.GateTestFixtures.at
import ykws.android.maro.spatial.multipass.RouteGridPlan
import ykws.android.maro.spatial.multipass.UniformGridPlan
import java.util.Collections

/**
 * **The gate's two promises, read off a whole route.** The engine runs over a raster world with a straight
 * shelf — shallow south of 100 m — and a pair 15 m off it, 1.8 km apart; every rung's drawn line is read
 * back on the measured edge:
 *
 * - **the wall** — outside the ends' own water, no drawn straight comes within the wall;
 * - **the weight** — on `evolutive`, where the open water leaves room, the line's middle stands off at the
 *   outer ramp, less one fine cell.
 */
class GateEnforcementEngineTest {

    private val paceKn = 12.0
    private val edgeN = 100.0
    private val from = at(edgeN + 15.0, 300.0)
    private val to = at(edgeN + 15.0, 2100.0)

    private val raster = GateTestFixtures.raster(150, 1200, 2.0) { n, _ -> if (n < edgeN) 2.0 else 10.0 }
    private val world = GateTestFixtures.RasterWorld(raster, bandWidthM = 300.0)

    private class Solved(val lines: List<List<LatLng>>, val trace: List<String>)

    private fun solveEveryRung(
        plan: RouteGridPlan,
        world: MultipassWorld = this.world,
        from: LatLng = this.from,
        to: LatLng = this.to
    ): Solved = runBlocking {
        val trace = Collections.synchronizedList(mutableListOf<String>())
        val engine = RouteAvoidEngine(
            paceKn = { paceKn },
            verdictWeight = { AppConfig.routeAvoidSlowWaterWeight },
            worldProvider = { world },
            plan = plan,
            traceSink = { line -> trace += line }
        )
        val origin = RoutePoint(from.latitude, from.longitude)
        val aim = RoutePoint(to.latitude, to.longitude)
        val declared = engine.routesToCompute(origin, aim) as? RouteDeclarations.Available
        assertNotNull("the pair declares its rungs", declared)
        val lines = declared!!.computations.map { computation ->
            coroutineScope {
                val subscribed = CompletableDeferred<Unit>()
                val done = CompletableDeferred<RouteUpdate?>()
                val collector = launch(Dispatchers.Default) {
                    engine.updates
                        .onStart { subscribed.complete(Unit) }
                        .collect { update -> if (update.nextStage == null && !done.isCompleted) done.complete(update) }
                }
                subscribed.await()
                engine.startLookup(computation.id)
                val update = withTimeout(120_000) { done.await() }
                collector.cancel()
                val success = update?.result as? RouteResult.Success
                assertNotNull("rung ${computation.id} lands a line", success)
                success!!.points.map { LatLng(it.latitude, it.longitude) }
            }
        }
        Solved(lines, trace.toList())
    }

    /**
     * Whether every straight of [line] keeps [hardM] wherever it stands more than [clearM] from both ends —
     * read on one-metre pieces, independently of the engine's own cut at its waiver.
     */
    private fun keepsWall(line: List<LatLng>, hardM: Double, clearM: Double): Boolean {
        val gate = GateRule(world.shallowEdges(GateTestFixtures.MIN_DEPTH_M), hardM, hardM, 0.0)
        fun near(p: LatLng) = SpatialOperations.haversine(p, from) < clearM || SpatialOperations.haversine(p, to) < clearM
        val pieces = sampled(line, 1.0)
        return (0 until pieces.size - 1).all { k ->
            near(pieces[k]) || near(pieces[k + 1]) || gate.segmentEdgeM(pieces[k], pieces[k + 1], hardM + 1.0) >= hardM - 1e-6
        }
    }

    /** [line]'s own points and a point every [stepM] along each straight between them. */
    private fun sampled(line: List<LatLng>, stepM: Double): List<LatLng> = buildList {
        for (k in 0 until line.size - 1) {
            val a = line[k]
            val b = line[k + 1]
            val pieces = maxOf(1, kotlin.math.ceil(SpatialOperations.haversine(a, b) / stepM).toInt())
            for (j in 0 until pieces) {
                val t = j.toDouble() / pieces
                add(LatLng(a.latitude + (b.latitude - a.latitude) * t, a.longitude + (b.longitude - a.longitude) * t))
            }
        }
        add(line.last())
    }

    private fun edgeM(p: LatLng): Double =
        GateRule(world.shallowEdges(GateTestFixtures.MIN_DEPTH_M), 0.0, 0.0, 0.0).edgeM(p, 500.0)

    @Test
    fun evolutiveKeepsTheWallAndStandsOffWhereThereIsRoom() {
        val solved = solveEveryRung(EvolutiveGridPlan)
        val floorM = AppConfig.routeEvolutiveBerthFloorM
        val outerM = AppConfig.routeEvolutiveBerthOuterM
        val fineCellM = AppConfig.routeEvolutiveGridFineCellM
        for ((rung, line) in solved.lines.withIndex()) {
            assertTrue("rung $rung keeps the $floorM m wall past 60 m of its ends", keepsWall(line, floorM, 60.0))
            // The middle third: the line read every 10 m, where it stands at least 600 m from both ends.
            val middle = sampled(line, 10.0).filter {
                SpatialOperations.haversine(it, from) >= 600.0 && SpatialOperations.haversine(it, to) >= 600.0
            }
            assertTrue("rung $rung has a middle third (${line.size} points)", middle.size > 50)
            for (p in middle) {
                assertTrue(
                    "rung $rung's middle stands off at the outer ramp (${edgeM(p)} m)",
                    edgeM(p) >= outerM - fineCellM
                )
            }
        }
        val bodies = solved.trace.filter { it.startsWith("LINE ") }.mapNotNull {
            Regex("bodyEdgeIdx=([0-9.]+)m").find(it)?.groupValues?.get(1)?.toDouble()
        }
        assertTrue("the engine read its own lines", bodies.isNotEmpty())
        val lines = solved.trace.filter { it.startsWith("LINE ") }
        assertTrue("each line names where its shallowest sample stands", lines.all { it.contains("minDepthAt=") && it.contains("minDepthWaiver=") })
        val nudges = solved.trace.filter { it.startsWith("NUDGE ") }
        assertTrue("the nudge counts why each sample is in no passage", nudges.isNotEmpty() && nudges.all { Regex("samples=\\d+ waived=\\d+ open=\\d+ noLeft=\\d+ noRight=\\d+ wide=\\d+").containsMatchIn(it) })
        for (body in bodies) assertTrue("the engine's own reading keeps the wall ($body m)", body >= floorM - 1e-6)
    }

    /**
     * With the ends' clearance switched off (`route.repair.clearanceM=0`) the pair stays 15 m off the shelf,
     * so the ends' own 20 m waiver carries the line out — and the wall still holds past them.
     */
    @Test
    fun withNoEndClearanceTheWaiverCarriesTheEndsAndTheWallStillHolds() {
        val field = AppConfig::class.java.getDeclaredField("routeRepairClearanceM").apply { isAccessible = true }
        val shipped = field.getDouble(AppConfig)
        field.setDouble(AppConfig, 0.0)
        try {
            val solved = solveEveryRung(EvolutiveGridPlan)
            assertTrue("no end was moved", solved.trace.filter { it.startsWith("REPAIR") }.all { it.contains("moved=false") })
            for ((rung, line) in solved.lines.withIndex()) {
                assertTrue("rung $rung keeps the wall past 60 m of its ends", keepsWall(line, AppConfig.routeEvolutiveBerthFloorM, 60.0))
            }
        } finally {
            field.setDouble(AppConfig, shipped)
        }
    }

    /**
     * **A channel whose middle rows are excluded EMODnet carries the route.** A 100 m channel between two
     * Litto3D shoals, its middle 20 m read by EMODnet at 0.4 m beside Litto3D 4.0 m — the device probe's own
     * values — under the 3.0 cutoff. The old read blended those nodes into their Litto3D neighbours and walled
     * a strip each side of them; the router now reads them at the gate depth, and every rung runs through
     * the channel with the wall kept.
     */
    @Test
    fun aChannelOfExcludedEmodnetCarriesTheRoute() {
        val channel = GateTestFixtures.raster(
            150, 400, 2.0,
            source = { n, _ -> if (n in 140.0..160.0) DepthSource.EMODNET else DepthSource.LITTO3D }
        ) { n, _ ->
            when {
                n < 100.0 || n > 200.0 -> 2.0
                n in 140.0..160.0 -> 0.4
                else -> 4.0
            }
        }
        val strip = at(139.7, 400.0) // between the Litto3D row at 139 m and the EMODnet row at 141 m
        assertTrue(
            "the old read walled a strip beside the excluded rows",
            channel.depthAt(strip.latitude, strip.longitude).gatedForEmodnetShallow(3.0f).depthM < 3.0f
        )
        val channelWorld = GateTestFixtures.RasterWorld(channel, bandWidthM = 300.0, readCutoffM = 3.0f)
        val start = at(150.0, 100.0)
        val aim = at(150.0, 700.0)
        val solved = solveEveryRung(EvolutiveGridPlan, channelWorld, start, aim)
        val gate = GateRule(channelWorld.shallowEdges(GateTestFixtures.MIN_DEPTH_M), 10.0, 10.0, 0.0)
        for ((rung, line) in solved.lines.withIndex()) {
            for (p in line) {
                val n = (p.latitude - GateTestFixtures.LAT0) * GateTestFixtures.M_PER_DEG_LAT
                assertTrue("rung $rung stays in the channel (n=$n)", n in 100.0..200.0)
            }
            for (k in 0 until line.size - 1) {
                assertTrue(
                    "rung $rung keeps the wall",
                    gate.segmentEdgeM(line[k], line[k + 1], 20.0) >= AppConfig.routeEvolutiveBerthFloorM - 1e-6
                )
            }
        }
    }

    /**
     * **A start already clear of the shallows leaves without crossing one** — the 2026-10-20 device case: the
     * start stands 30 m off the shelf, and a 0.6 m shoal lies 45 m out on the straight toward the aim. No
     * stretch past an end's 20 m disc is exempt, so every rung keeps water of 3 m or more past it, keeps the
     * wall past 60 m of its ends, and never doubles back behind the start.
     */
    @Test
    fun aClearStartLeavesWithoutCrossingAShoal() {
        val shoalN = 162.0
        val shoalE = 332.0
        val shoaled = GateTestFixtures.raster(450, 550, 2.0) { n, e ->
            when {
                n < edgeN -> 2.0
                kotlin.math.hypot(n - shoalN, e - shoalE) <= 6.0 -> 0.6
                else -> 10.0
            }
        }
        val shoalWorld = GateTestFixtures.RasterWorld(shoaled, bandWidthM = 300.0)
        val start = at(130.0, 300.0)
        val aim = at(730.0, 900.0)
        val solved = solveEveryRung(EvolutiveGridPlan, shoalWorld, start, aim)
        val waiverM = AppConfig.routeAvoidDepthGateMarginM
        val gate = GateRule(shoalWorld.shallowEdges(GateTestFixtures.MIN_DEPTH_M), 0.0, 0.0, 0.0)
        for ((rung, line) in solved.lines.withIndex()) {
            for (p in sampled(line, 1.0)) {
                if (SpatialOperations.haversine(p, start) < waiverM || SpatialOperations.haversine(p, aim) < waiverM) continue
                val depthM = shoaled.depthAt(p.latitude, p.longitude).depthM
                assertTrue("rung $rung crosses no water under 3 m past the disc ($depthM m)", depthM.isNaN() || depthM >= 3.0f)
                val eastM = (p.longitude - GateTestFixtures.LON0) * GateTestFixtures.M_PER_DEG_LON
                assertTrue("rung $rung never doubles back behind the start (e=$eastM)", eastM >= 300.0 - waiverM)
            }
            val floorM = AppConfig.routeEvolutiveBerthFloorM
            for (k in 0 until line.size - 1) {
                val a = line[k]
                val b = line[k + 1]
                if (minOf(SpatialOperations.haversine(a, start), SpatialOperations.haversine(b, start)) < 60.0) continue
                assertTrue("rung $rung keeps the wall off the shoal", gate.segmentEdgeM(a, b, floorM + 1.0) >= floorM - 1e-6)
            }
        }
    }

    @Test
    fun avoidKeepsItsTwentyMetreWall() {
        val solved = solveEveryRung(UniformGridPlan)
        val wallM = AppConfig.routeAvoidDepthGateMarginM
        for ((rung, line) in solved.lines.withIndex()) {
            assertTrue("rung $rung keeps the $wallM m wall past 150 m of its ends", keepsWall(line, wallM, 150.0))
        }
    }
}
