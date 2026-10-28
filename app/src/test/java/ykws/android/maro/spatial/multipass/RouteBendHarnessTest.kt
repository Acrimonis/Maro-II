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
 * **The bends' device pair, routed on the desktop** — the Lérins → La Salis pair the 2026-10-27 device runs read, or
 * any pair handed as `-Dmaro.harness.start=<lat>,<lon>` and `-Dmaro.harness.aim=<lat>,<lon>` (each end that is not
 * handed keeps the Lérins → La Salis one; the ends are used as given, with none of the engine's end repair), over the
 * region the app ships ([BakedRegionWorld]) and the values its `.properties` files hold
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
 *
 * **A device's line, replayed.** The desktop's own search need not bend where the device's did, so a line the device
 * logged can be handed in as `-Dmaro.harness.replay="<lat>,<lon> <lat>,<lon> …"` — the fine pass's swept line, its
 * `SWEEP keep` points between the two ends — and each rung then also hands that line, on its own grid and at its own
 * λ, to the approach pass alone, the setup built as the fine pass builds it: the pass's `APPROACH` lines, the line it
 * returns and both lines' cost are printed under `REPLAY`.
 */
class RouteBendHarnessTest {

    // Read on first use, after the gate below: a malformed property fails the opted-in run, never a skipped one.
    private val start by lazy { endFrom("maro.harness.start") ?: LatLng(43.50982, 7.04681) }
    private val aim by lazy { endFrom("maro.harness.aim") ?: LatLng(43.57055, 7.13144) }
    private val paceKn = 30.0

    @Test
    fun routesTheDevicePair() {
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
            replayLine?.let { replay -> run.ctx?.let { ctx -> replayApproach(ctx, replay, lambda).forEach { say("  $it") } } }
        }
        File(repoDir, "app/build/route-bend-harness.txt").apply { parentFile?.mkdirs() }.writeText(report.toString())
    }

    /** One rung's refined line, its fine pass's trace, the fine pass's own time and the whole rung's, and its grid. */
    private class RungRun(
        val line: List<LatLng>,
        val trace: List<String>,
        val fineMs: Double,
        val rungMs: Double,
        val ctx: GridContext? = null
    )

    /**
     * The approach pass alone over [line] on [ctx] at [lambda] — the fine pass's own field and setup, in the pair's
     * canonical direction — as `REPLAY` lines: the pass's trace, the line it returns and both lines' cost.
     */
    private fun replayApproach(ctx: GridContext, line: List<LatLng>, lambda: Double): List<String> {
        val reversed = !sortsFirst(line.first(), line.last())
        val oriented = if (reversed) line.asReversed() else line
        // The grid's corridor and waivers are the harness pair's: a line between other ends is replayed off them.
        val ends = setOf(line.first(), line.last())
        val warning = if (ends != setOf(ctx.start, ctx.aim)) {
            listOf("REPLAY warning: the line's ends ${ends.map { MultipassPull.atText(it) }} are not the harness pair's")
        } else {
            emptyList()
        }
        val field = costField(
            ctx.world, ctx.fineCellM, ctx.pace, withZones = true, withBand = true, zones = ctx.zones, lambda = lambda,
            markerStrength = ctx.markerStrength
        )
        val setup = pullSetupFor(ctx, field, lambda, ctx.fineCellM, oriented.first(), oriented.last())
        val trace = mutableListOf<String>()
        val out = RouteApproachPass.refine(setup, oriented, ctx.box, ctx.fineCellM, MarkMemo(), trace = { trace += it() })
        val cost = RouteFinePass()
        return warning + trace.map { "REPLAY $it" } + listOf(
            "REPLAY in=${oriented.size} sec=${fmt(cost.lineCostSec(setup, oriented))} " +
                "lengthM=${fmt(polylineLengthM(oriented))}",
            "REPLAY out=${out.size} sec=${fmt(cost.lineCostSec(setup, out))} lengthM=${fmt(polylineLengthM(out))} " +
                "LINE ${out.joinToString(" ") { MultipassPull.atText(it) }}"
        )
    }

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
            return RungRun(refined, trace, (endNs - fineStartNs) / 1_000_000.0, (endNs - rungStartNs) / 1_000_000.0, ctx)
        }
        return RungRun(emptyList(), emptyList(), Double.NaN, Double.NaN)
    }

    private fun fmt(v: Double) = "%.1f".format(java.util.Locale.ROOT, v)

    /** The device line `-Dmaro.harness.replay` names, or `null` where it is blank or absent. */
    private val replayLine: List<LatLng>? by lazy {
        System.getProperty("maro.harness.replay")?.takeIf { it.isNotBlank() }
            ?.trim()?.split(' ')?.filter { it.isNotBlank() }?.map { latLngOf(it) }
    }

    /** The end a `<lat>,<lon>` system property names, or `null` where it is blank or absent. */
    private fun endFrom(key: String): LatLng? = System.getProperty(key)?.takeIf { it.isNotBlank() }?.let(::latLngOf)

    private fun latLngOf(text: String): LatLng {
        val parts = text.split(',').map { it.trim().toDouble() }
        require(parts.size == 2) { "a point is <lat>,<lon>: $text" }
        return LatLng(parts[0], parts[1])
    }

    private companion object {
        const val REPEATS = 5
    }
}
