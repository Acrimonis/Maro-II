package ykws.android.maro.spatial.multipass

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume
import org.junit.Test
import ykws.android.maro.BuildConfig
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.markers.MarkerPayload
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.data.model.RouteResult
import ykws.android.maro.data.model.markers.UserMarker
import ykws.android.maro.spatial.RouteAvoidEngine
import ykws.android.maro.spatial.RouteDeclarations
import ykws.android.maro.spatial.RouteReason
import ykws.android.maro.spatial.RouteUpdate
import ykws.android.maro.spatial.SpatialOperations
import java.io.File
import java.util.ArrayDeque
import java.util.Collections
import java.util.Properties

/**
 * **Route todo 11, measured on the user's own ends** — every stored marker's centre (the point a flagged end
 * routes from) routed to and from one open-water point on the baked region, on the Evolutive + row with the
 * shipped values, the main rung only. A pair that lands no line is routed again twice — once with the coast's
 * margin dropped to its 1 m floor, once with the depth gate off — so its cause is named by the relaxation that
 * lets it through. Beside it, a lattice-free reading of the pocket: a 5 m flood from the repaired end through
 * water that stands the margin off the coast or inside the end's own disc, which says whether the margin
 * alone, at any lattice, walls the end in.
 *
 * An instrument, not a guard: it asserts nothing and skips unless `-Dmaro.boxedEnds=true` and the bake is
 * present. It writes [AppConfig], so it is run on its own:
 * `.\gradlew.bat --% :app:testDebugUnitTest --tests "*RouteBoxedEndSurveyTest*" --rerun -Dmaro.boxedEnds=true`
 * The report goes to `app/build/route-boxed-ends.txt`.
 */
class RouteBoxedEndSurveyTest {

    private val paceKn = 25.0
    private val open = LatLng(43.505, 7.10)

    private class Outcome(val routed: Boolean, val reason: String, val trace: List<String>)

    private fun route(world: MultipassWorld, from: LatLng, to: LatLng): Outcome = runBlocking {
        val trace = Collections.synchronizedList(mutableListOf<String>())
        val engine = RouteAvoidEngine(
            paceKn = { paceKn },
            verdictWeight = { AppConfig.routeAvoidSlowWaterWeight },
            worldProvider = { world },
            plan = EvolutiveGridPlan,
            traceSink = { line -> trace += line },
            anyAngle = true
        )
        try {
            val declared = engine.routesToCompute(RoutePoint(from.latitude, from.longitude), RoutePoint(to.latitude, to.longitude))
            if (declared !is RouteDeclarations.Available) {
                return@runBlocking Outcome(false, "refused:${(declared as? RouteDeclarations.Refused)?.reason ?: declared}", trace.toList())
            }
            coroutineScope {
                val subscribed = CompletableDeferred<Unit>()
                val done = CompletableDeferred<RouteUpdate?>()
                val collector = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                    engine.updates.onStart { subscribed.complete(Unit) }
                        .collect { u -> if (u.nextStage == null && !done.isCompleted) done.complete(u) }
                }
                subscribed.await()
                engine.startLookup(declared.computations[0].id)
                val update = withTimeout(300_000) { done.await() }
                collector.cancel()
                val ok = update?.result is RouteResult.Success
                Outcome(ok, if (ok) "ok" else (update?.reason ?: RouteReason.FAILED).name, trace.toList())
            }
        } finally {
            engine.close()
        }
    }

    private fun repairedAt(trace: List<String>, end: String): Pair<LatLng, String>? {
        val line = trace.firstOrNull { it.startsWith("REPAIR end=$end") } ?: return null
        val at = Regex("at=([0-9.\\-]+),([0-9.\\-]+)").find(line)!!.groupValues
        val moved = Regex("movedM=([0-9.]+)").find(line)?.groupValues?.get(1) ?: "?"
        val coast = Regex("coast=([^ ]+)").find(line)?.groupValues?.get(1) ?: "?"
        val rule = Regex("rule=([a-z]+)").find(line)?.groupValues?.get(1) ?: "?"
        return LatLng(at[1].toDouble(), at[2].toDouble()) to "rule=$rule movedM=$moved coast=$coast"
    }

    /**
     * Whether the margin walls [end] in at any lattice: a 5 m flood over water standing [marginM] off the coast
     * or within [marginM] of the end, failing to reach [escapeM] from it.
     */
    private fun pocketed(world: MultipassWorld, end: LatLng, marginM: Double, escapeM: Double): Boolean {
        val stepM = 5.0
        val mPerDegLat = GateTestFixtures.M_PER_DEG_LAT
        val mPerDegLon = mPerDegLat * kotlin.math.cos(Math.toRadians(end.latitude))
        val half = (escapeM / stepM).toInt() + 1
        val side = 2 * half + 1
        val seen = BooleanArray(side * side)
        val queue = ArrayDeque<Int>()
        fun pointOf(r: Int, c: Int) = LatLng(end.latitude + (r - half) * stepM / mPerDegLat, end.longitude + (c - half) * stepM / mPerDegLon)
        fun passable(r: Int, c: Int): Boolean {
            val p = pointOf(r, c)
            if (!world.isWater(p.latitude, p.longitude)) return false
            val fromEnd = kotlin.math.hypot((r - half) * stepM, (c - half) * stepM)
            return fromEnd <= marginM || world.distanceToCoastM(p.latitude, p.longitude) >= marginM
        }
        val origin = half * side + half
        seen[origin] = true
        queue.add(origin)
        while (queue.isNotEmpty()) {
            val k = queue.poll()
            val r = k / side
            val c = k % side
            if (kotlin.math.hypot((r - half) * stepM, (c - half) * stepM) >= escapeM) return false
            for ((dr, dc) in NEIGHBOURS) {
                val nr = r + dr
                val nc = c + dc
                if (nr !in 0 until side || nc !in 0 until side) continue
                val nk = nr * side + nc
                if (seen[nk]) continue
                seen[nk] = true
                if (passable(nr, nc)) queue.add(nk)
            }
        }
        return true
    }

    @Test
    fun surveysTheStoredEnds() {
        Assume.assumeTrue("set -Dmaro.boxedEnds=true to run the boxed-end survey", System.getProperty("maro.boxedEnds") == "true")
        val repoDir = System.getProperty("maro.repoDir")?.let { File(it) } ?: File("..")
        AppConfig.applyProperties(Properties().apply {
            for (name in listOf("maro.properties", "ui.properties", "colors.properties")) {
                File(repoDir, "app/src/main/assets/$name").takeIf { it.isFile }?.inputStream()?.use { load(it) }
            }
        })
        val baked = BakedRegionWorld.load(repoDir, BuildConfig.REGION_ID)
        Assume.assumeTrue("no baked region for ${BuildConfig.REGION_ID}", baked != null)
        val markerFile = File(repoDir, "app/build/route-harness/user_markers.json")
        Assume.assumeTrue("no marker copy at $markerFile", markerFile.isFile)
        val stored = MarkerPayload.json.decodeFromString<List<UserMarker>>(markerFile.readText())
        val world = baked!!.withMarkers(stored)
        val marginM = AppConfig.routeAvoidObstacleMarginM
        val report = StringBuilder()
        fun say(line: String) {
            report.appendLine(line)
            println(line)
        }
        say(
            "BOXED ENDS — ${BuildConfig.REGION_ID}, ${stored.size} markers, open point ${MultipassPull.atText(open)} " +
                "coast=${"%.0f".format(world.distanceToCoastM(open.latitude, open.longitude))}m " +
                "depth=${world.depthAt(open.latitude, open.longitude).depthM}m, margin=${marginM}m, pace=$paceKn kn, evolutive_plus"
        )
        val tally = sortedMapOf<String, Int>()
        val marginField = AppConfig::class.java.getDeclaredField("routeAvoidObstacleMarginM").apply { isAccessible = true }
        val gateField = AppConfig::class.java.getDeclaredField("routeAvoidDepthGateEnabled").apply { isAccessible = true }
        for (marker in stored.filter { !it.routingOnly }) {
            val p = marker.centerPoint
            val out = route(world, p, open)
            val back = route(world, open, p)
            val repaired = repairedAt(out.trace, "start")
            val pocket = repaired?.let { pocketed(world, it.first, marginM, 1500.0) }
            var cause = "-"
            if (!out.routed || !back.routed) {
                marginField.setDouble(AppConfig, 1.0)
                val relaxedMargin = try { route(world, p, open).routed && route(world, open, p).routed } finally { marginField.setDouble(AppConfig, marginM) }
                val shippedGate = gateField.getBoolean(AppConfig)
                gateField.setBoolean(AppConfig, false)
                val relaxedGate = try { route(world, p, open).routed && route(world, open, p).routed } finally { gateField.setBoolean(AppConfig, shippedGate) }
                cause = when {
                    relaxedMargin && relaxedGate -> "margin-or-gate"
                    relaxedMargin -> "coast-margin"
                    relaxedGate -> "depth-gate"
                    else -> "neither"
                }
            }
            val key = "${if (out.routed) "ok" else out.reason}/${if (back.routed) "ok" else back.reason}" +
                (if (cause != "-") " cause=$cause" else "")
            tally[key] = (tally[key] ?: 0) + 1
            val flags = listOfNotNull("origin".takeIf { marker.routeOrigin }, "dest".takeIf { marker.routeDestination }).joinToString("+").ifEmpty { "-" }
            say(
                "END '${marker.name.trim()}' flags=$flags raw=${MultipassPull.atText(p)} " +
                    "repaired=${repaired?.let { MultipassPull.atText(it.first) + " " + it.second } ?: "none"} " +
                    "pocket=${pocket ?: "n/a"} out=${out.reason} in=${back.reason} cause=$cause"
            )
        }
        for ((key, count) in tally) say("TALLY $key = $count")
        File(repoDir, "app/build/route-boxed-ends.txt").apply { parentFile?.mkdirs() }.writeText(report.toString())
    }

    private companion object {
        val NEIGHBOURS = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1, 1 to 1, 1 to -1, -1 to 1, -1 to -1)
    }
}
