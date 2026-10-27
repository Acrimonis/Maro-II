package ykws.android.maro.spatial.multipass

import kotlinx.coroutines.runBlocking
import org.junit.Assume
import org.junit.Test
import ykws.android.maro.BuildConfig
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.markers.MarkerPayload
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.data.model.markers.UserMarker
import java.io.File
import java.util.Properties

/**
 * **The bends' device pair, routed on the desktop** — the Lérins → La Salis pair the 2026-10-27 device runs read,
 * over the region the app ships ([BakedRegionWorld]) and the values its `.properties` files hold
 * ([AppConfig.applyProperties]), with the device's own markers where a copy of its `user_markers.json` stands at
 * `app/build/route-harness/user_markers.json` (copied off the phone with `run-as`), at the runs' 30 kn on
 * `evolutive`. Each rung builds its grid, runs its coarse pass and its fine pass with the fine pass's trace kept:
 * the `SWEEP keep`, `APPROACH` and `STRAIGHTEN` lines and the refined line's points are printed with the fine
 * pass's wall time and the rung's, the best of [REPEATS] runs. It is not the engine's arming: the engine builds the
 * grid once an arm and grows the corridor on a forced crossing too, so `rungMs` carries a grid build the device
 * pays once, and the corridor grows here only where the first finds no way.
 *
 * An instrument, not a guard: it asserts nothing and skips unless `-Dmaro.bendHarness=true` and the bake is present.
 * It writes the shipped values into the process's [AppConfig] and its inputs — the bake, the `.properties`, the
 * marker copy — are not the test task's, so it is run on its own and always re-run:
 * `.\gradlew.bat --% :app:testDebugUnitTest --tests "*RouteBendHarnessTest*" --rerun -Dmaro.bendHarness=true -Pmaro.testStdout=true`
 * The report is also written to `app/build/route-bend-harness.txt`. Adding `-Dmaro.jfr=<file>.jfr` records a Flight
 * Recorder profile of the run (the test task's own hook), read with the JDK's `jfr print`.
 */
class RouteBendHarnessTest {

    private val start = LatLng(43.50982, 7.04681)
    private val aim = LatLng(43.57055, 7.13144)
    private val paceKn = 30.0

    @Test
    fun lerinsToLaSalis() {
        Assume.assumeTrue(
            "set -Dmaro.bendHarness=true to run the bend harness", System.getProperty("maro.bendHarness") == "true"
        )
        val repoDir = System.getProperty("maro.repoDir")?.let { File(it) } ?: File("..")
        // The shipped values first: the world's depth reads take the cutoff and the gate in force when it is built.
        AppConfig.applyProperties(Properties().apply {
            for (name in listOf("maro.properties", "ui.properties", "colors.properties")) {
                File(repoDir, "app/src/main/assets/$name").takeIf { it.isFile }?.inputStream()?.use { load(it) }
            }
        })
        val baked = BakedRegionWorld.load(repoDir, BuildConfig.REGION_ID)
        Assume.assumeTrue("no baked region for ${BuildConfig.REGION_ID}", baked != null)
        val markerFile = File(repoDir, "app/build/route-harness/user_markers.json")
        val stored = markerFile.takeIf { it.isFile }
            ?.let { MarkerPayload.json.decodeFromString<List<UserMarker>>(it.readText()) }
            .orEmpty()
        val world = baked!!.withMarkers(stored)
        val report = StringBuilder()
        fun say(line: String) {
            report.appendLine(line)
            println(line)
        }
        val rungs = RoutePreference.entries.map { it.name to it.lambda }
        say(
            "BEND HARNESS — ${BuildConfig.REGION_ID}, $start -> $aim, $paceKn kn, evolutive, " +
                "lambdas ${rungs.map { it.second }}, costed markers ${world.routeMarkers().size}"
        )
        for ((name, lambda) in rungs) {
            var best: RungRun? = null
            repeat(REPEATS) {
                val run = runBlocking { rung(world, lambda) }
                if (best == null || run.rungMs < best!!.rungMs) best = run
            }
            val run = best!!
            val refined = run.line
            val lines = run.trace
            say(
                "== $name (lambda $lambda) rungMs=${fmt(run.rungMs)} finePassMs=${fmt(run.fineMs)} " +
                    "points=${refined.size} lengthM=${fmt(polylineLengthM(refined))}"
            )
            lines.filter { it.startsWith("SWEEP keep") || it.startsWith("APPROACH") || it.startsWith("STRAIGHTEN") }
                .distinct().forEach { say("  $it") }
            say("  LINE ${refined.joinToString(" ") { MultipassPull.atText(it) }}")
        }
        File(repoDir, "app/build/route-bend-harness.txt").apply { parentFile?.mkdirs() }.writeText(report.toString())
    }

    /** One rung's refined line, its fine pass's trace, the fine pass's own time and the whole rung's. */
    private class RungRun(val line: List<LatLng>, val trace: List<String>, val fineMs: Double, val rungMs: Double)

    /** One rung as the engine runs it — the corridor grown once where the first finds no way — and its fine pass. */
    private suspend fun rung(world: BakedRegionWorld, lambda: Double): RungRun {
        val rungStartNs = System.nanoTime()
        val builder = RouteGridBuilder(EvolutiveGridPlan)
        val runner = RoutePassRunner()
        for (reach in listOf(AppConfig.routeAvoidCorridorReachM, AppConfig.routeAvoidCorridorReachM * 2.0)) {
            val ctx = builder.buildGrid(world, RoutePoint.of(start), RoutePoint.of(aim), reach, paceKn, 1.0) ?: continue
            val walk = GridWalk(ctx.grid, ctx.startCell, ctx.aimCell, ctx.cellM, ctx.windows)
            val pass = runner.runPass(ctx, walk, lambda, publishStage = false)
            if (pass.search.path == null && !ctx.regionSaturated) continue
            val trace = mutableListOf<String>()
            val fineStartNs = System.nanoTime()
            val refined = RouteFinePass().finePass(ctx, pass.line, lambda) { trace += it() }
            val endNs = System.nanoTime()
            return RungRun(refined, trace, (endNs - fineStartNs) / 1_000_000.0, (endNs - rungStartNs) / 1_000_000.0)
        }
        return RungRun(emptyList(), emptyList(), Double.NaN, Double.NaN)
    }

    private fun fmt(v: Double) = "%.1f".format(java.util.Locale.ROOT, v)

    private companion object {
        const val REPEATS = 5
    }
}
