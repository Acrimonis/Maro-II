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
import ykws.android.maro.spatial.SpatialOperations
import ykws.android.maro.spatial.Units
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
 *
 * **The clocks and the line's readings, per rung.** A `CLOCK` line puts the search's own `SEARCH ms` beside the
 * pull's, the snap's, the final pull's, the fine pass's and the approach pass's, and a `READING` line reads the
 * refined line — its length, its limit-read duration, the seconds it spends over the pace, its `lineCostSec` (the
 * pull's own clock on the fine pass's water, the any-angle plan's §6 guard), the shallowest depth and the gate's edge
 * on it, and the lone turns the approach pass left. `-Dmaro.harness.survey=true` routes the direction-symmetry
 * survey's seeded pairs ([RouteSurveyPairs]) one run each instead, into `app/build/route-bend-harness-survey.txt`,
 * closing on one `TOTAL` line per rung. `-Dmaro.harness.anyAngle=true` walks as the Evolutive + row does, and
 * `-Dmaro.harness.props="key=value;key=value"` overrides the shipped values — `route.search.anyAngle.reachCells=16`
 * tunes that walk — and a
 * `LEGS` line per rung reads the refined line's legs: their count, the longest, and how many fall in each length
 * band, the reading the any-angle reach cap is set on.
 *
 * **The chord read's cost gate** (the any-angle plan's P1). The search's expansions are recorded in order
 * ([ExpansionProbe]) and, once the rung is done and outside its clock, a chord ([ChordReader], the
 * rung's own price and gate) is timed from the walk's own first cell to every cell it expanded, and along the search's
 * own path. The `CHORD` line projects the search with one chord read per expansion: as measured from the first cell,
 * at the corridor's own length (the path chords' clear time per metre over the pair's straight-line distance — the
 * plan's gate figure, against twice the rung's `SEARCH ms`), and with one chord per step the expansions read (a push
 * that prices each neighbour's chord). The report closes on one `GATE` line per rung, summed over the pairs.
 */
class RouteBendHarnessTest {

    // Read on first use, after the gate below: a malformed property fails the opted-in run, never a skipped one.
    private val start by lazy { endFrom("maro.harness.start") ?: LatLng(43.50982, 7.04681) }
    private val aim by lazy { endFrom("maro.harness.aim") ?: LatLng(43.57055, 7.13144) }
    private val paceKn = 30.0

    /** Whether the harness walks as the Evolutive + row does — the any-angle search. */
    private val anyAngle = System.getProperty("maro.harness.anyAngle") == "true"

    @Test
    fun routesTheDevicePair() {
        Assume.assumeTrue(
            "set -Dmaro.bendHarness=true to run the bend harness", System.getProperty("maro.bendHarness") == "true"
        )
        val repoDir = System.getProperty("maro.repoDir")?.let { File(it) } ?: File("..")
        // The shipped values first: the world's depth reads take the cutoff and the gate in force when it is built.
        val overrides = System.getProperty("maro.harness.props").orEmpty().split(';').filter { it.contains('=') }
        require(overrides.none { it.substringBefore('=').trim() == "route.search.anyAngle" }) {
            "route.search.anyAngle is no key any more: pass -Dmaro.harness.anyAngle=true to walk as Evolutive + does"
        }
        AppConfig.applyProperties(Properties().apply {
            for (name in listOf("maro.properties", "ui.properties", "colors.properties")) {
                File(repoDir, "app/src/main/assets/$name").takeIf { it.isFile }?.inputStream()?.use { load(it) }
            }
            for (pair in overrides) setProperty(pair.substringBefore('=').trim(), pair.substringAfter('=').trim())
        })
        val baked = BakedRegionWorld.load(repoDir, BuildConfig.REGION_ID)
        Assume.assumeTrue("no baked region for ${BuildConfig.REGION_ID}", baked != null)
        val markerFile = File(repoDir, "app/build/route-harness/user_markers.json")
        val stored = markerFile.takeIf { it.isFile }
            ?.let { MarkerPayload.json.decodeFromString<List<UserMarker>>(it.readText()) }
            .orEmpty()
        val world = baked!!.withMarkers(stored)
        val survey = System.getProperty("maro.harness.survey") == "true"
        val pairs = if (survey) RouteSurveyPairs.draw(baked) else listOf(EndPair("pair", start, aim))
        val repeats = if (survey) 1 else REPEATS
        val report = StringBuilder()
        fun say(line: String) {
            report.appendLine(line)
            println(line)
        }
        val rungs = RoutePreference.entries.map { it.name to it.lambda }
        say(
            "BEND HARNESS — ${BuildConfig.REGION_ID}, ${if (survey) "survey of ${pairs.size} pairs" else "$start -> $aim"}, " +
                "$paceKn kn, evolutive, lambdas ${rungs.map { it.second }}, costed markers ${world.routeMarkers().size}, " +
                "best of $repeats, anyAngle=$anyAngle " +
                "reachCells=${AppConfig.routeSearchAnyAngleReachCells}" +
                (if (overrides.isEmpty()) "" else ", overrides ${overrides.joinToString(";")}")
        )
        val totals = rungs.associate { it.first to DoubleArray(CLOCK_KEYS.size) }
        val gateTotals = rungs.associate { it.first to DoubleArray(GATE_KEYS.size) }
        for (pair in pairs) {
            if (survey) say("## ${pair.name} ${MultipassPull.atText(pair.a)} -> ${MultipassPull.atText(pair.b)}")
            for ((name, lambda) in rungs) {
                var best: RungRun? = null
                repeat(repeats) {
                    val run = runBlocking { rung(world, pair.a, pair.b, lambda) }
                    if (best == null || run.rungMs < best!!.rungMs) best = run
                }
                val run = best!!
                val refined = run.line
                val clock = clockOf(run)
                clock.forEachIndexed { k, ms -> if (!ms.isNaN()) totals.getValue(name)[k] += ms }
                say(
                    "== $name (lambda $lambda) rungMs=${fmt(run.rungMs)} finePassMs=${fmt(run.fineMs)} " +
                        "points=${refined.size} lengthM=${fmt(polylineLengthM(refined))}"
                )
                say("  CLOCK ${CLOCK_KEYS.indices.joinToString(" ") { "${CLOCK_KEYS[it]}=${fmt(clock[it])}" }}")
                run.passTrace.filter { it.startsWith("SEARCH ") }.forEach { say("  $it") }
                chordGate(run, lambda, clock[0])?.let { gate ->
                    // A rung with no chord standing has no rate: it reads no verdict and joins no sum.
                    if (!gate.values[1].isNaN()) gate.values.forEachIndexed { k, v -> gateTotals.getValue(name)[k] += v }
                    say("  ${gate.line}")
                }
                run.ctx?.let { ctx -> if (refined.size >= 2) say("  ${readingOf(ctx, refined, lambda, run.trace)}") }
                if (refined.size >= 2) say("  ${legsOf(refined)}")
                run.trace.filter { line -> TRACE_PREFIXES.any { line.startsWith(it) } }
                    .distinct().forEach { say("  $it") }
                say("  LINE ${refined.joinToString(" ") { MultipassPull.atText(it) }}")
                replayLine?.let { replay -> run.ctx?.let { ctx -> replayApproach(ctx, replay, lambda).forEach { say("  $it") } } }
            }
        }
        for ((name, sums) in totals) {
            say("TOTAL $name ${CLOCK_KEYS.indices.joinToString(" ") { "${CLOCK_KEYS[it]}=${fmt(sums[it])}" }}")
        }
        for ((name, sums) in gateTotals) {
            val ratio = sums[1] / sums[0]
            say(
                "GATE $name ${GATE_KEYS.indices.joinToString(" ") { "${GATE_KEYS[it]}=${fmt(sums[it])}" }} " +
                    "corridorRatio=${fmt(ratio, 2)} (bar ${fmt(GATE_BAR, 1)}) ${verdictOf(ratio)}"
            )
        }
        val reportName = if (survey) "route-bend-harness-survey.txt" else "route-bend-harness.txt"
        File(repoDir, "app/build/$reportName").apply { parentFile?.mkdirs() }.writeText(report.toString())
    }

    /**
     * The rung's stage clocks in [CLOCK_KEYS] order — the search, the pull, the snap and the final pull read off the
     * pass's own `SEARCH` / `PULL` / `SNAP` / `FINAL` lines, the approach pass off its summary, the fine pass and the
     * rung timed here; `NaN` for a line the run did not print.
     */
    private fun clockOf(run: RungRun): DoubleArray {
        fun msOf(lines: List<String>, prefix: String): Double =
            lines.firstOrNull { it.startsWith(prefix) }?.let { MS_FIELD.find(it)?.groupValues?.get(1)?.toDouble() }
                ?: Double.NaN
        return doubleArrayOf(
            msOf(run.passTrace, "SEARCH "), msOf(run.passTrace, "PULL "), msOf(run.passTrace, "SNAP "),
            msOf(run.passTrace, "FINAL "), run.fineMs, msOf(run.trace, "APPROACH moved="), run.rungMs
        )
    }

    /**
     * The refined line's `READING`: length, limit-read duration, the seconds over the pace, the pull's own cost on
     * the fine pass's water, the shallowest depth, the gate's edge (whole line and body) and the approach pass's
     * lone turns left.
     */
    private fun readingOf(ctx: GridContext, line: List<LatLng>, lambda: Double, fineTrace: List<String>): String {
        val lengthM = polylineLengthM(line)
        val timed = timeLineWithLimits(line, ctx.pace, ctx.limitAt, clockSampleM(ctx.cellM, ctx.fineCellM))
        val slowSec = timed.durationSec - lengthM / Units.knotsToMps(ctx.pace)
        val oriented = if (sortsFirst(line.first(), line.last())) line else line.asReversed()
        val costSec = RouteFinePass().lineCostSec(fineSetupOf(ctx, oriented, lambda), oriented)
        val minDepthM = RouteDepthReading.minDepthAt(ctx.world, line).first
        val edge = RouteDepthReading.edgeReading(
            ctx.world, line, ctx.depthGateActive, ctx.minDepthM, ctx.gate?.waiverM ?: ctx.marginM, ctx.gate
        )
        val loneTurns = fineTrace.firstOrNull { it.startsWith("APPROACH moved=") }
            ?.let { LONE_FIELD.find(it)?.groupValues?.get(1) } ?: "-"
        return "READING distanceM=${fmt(lengthM)} durationSec=${fmt(timed.durationSec)} slowSec=${fmt(slowSec)} " +
            "lineCostSec=${fmt(costSec)} minDepth=${fmt(minDepthM)} gateEdge=${fmt(edge.distanceM)} " +
            "bodyEdge=${fmt(edge.bodyDistanceM)} loneTurns=$loneTurns"
    }

    /** One rung's chord gate: its printed `CHORD` line and the [GATE_KEYS] figures the survey sums. */
    private class ChordGate(val line: String, val values: DoubleArray)

    /**
     * The chord read's cost on [run]'s walk at [lambda]'s price and gate, and the search it projects beside
     * [searchMs]. Two samples, each the best of [GATE_SWEEPS] timed sweeps so the first ones warm the read:
     * - **from the walk's first cell to every cell it expanded** — one chord per expansion, as measured; near a
     *   boxed or shallow end nearly all of them are refused early (the end's own leg, land), so it is a floor;
     * - **along the search's own path**, chords between its cells [PATH_SPANS] apart — long, mostly clear chords —
     *   whose time per metre, over the pair's straight-line distance once per expansion, is the plan's gate figure:
     *   the dearer of the clear chords' rate and every chord's (a chord the gate refuses has paid its measured read),
     *   no verdict where no chord stood. The same path chords read with no gate give `priceNsPerM`, the price
     *   walk's own share — the rest is the gate's (the plan's D4, its first suspect on a failed gate).
     */
    private fun chordGate(run: RungRun, lambda: Double, searchMs: Double): ChordGate? {
        val ctx = run.ctx ?: return null
        val walk = run.walk ?: return null
        val path = run.search?.path ?: return null
        val expanded = run.expansions ?: return null
        if (expanded.count == 0 || searchMs.isNaN()) return null
        // The walk's own frame: a reversed walk left the aim's end, and its path came back turned.
        val reversed = run.search.reversed
        val walkPath = (if (reversed) path.asReversed() else path).map { walk.rawSlotOf(it.layer, it.row, it.col) }
        val origin = walkPath.first()
        val (from, to) = if (reversed) ctx.aim to ctx.start else ctx.start to ctx.aim
        val guard = ctx.gate?.let { StepGuard(it, from, to) }
        val ends = EndRule(origin, walkPath.last(), strictStart = true, strictAim = true)
        val price = CellPrice(zonePriceFor(ctx.pace, lambda), lambda, berthLawOf(ctx.plan, ctx.depthGateActive))
        val gated = ChordReader(walk, price, guard, ends)
        val ungated = ChordReader(walk, price, null, ends)

        /** `[ns, clear chords, clear ns, clear metres, metres]` of a sweep over [pairs], the best of [GATE_SWEEPS]. */
        fun sweep(pairs: List<Pair<Int, Int>>, reader: ChordReader = gated): DoubleArray {
            var best: DoubleArray? = null
            repeat(GATE_SWEEPS) {
                var totalNs = 0L
                var clearNs = 0L
                var clearM = 0.0
                var totalM = 0.0
                var clear = 0
                for ((a, b) in pairs) {
                    val startNs = System.nanoTime()
                    val cost = reader.costSec(a, b)
                    val ns = System.nanoTime() - startNs
                    totalNs += ns
                    val m = SpatialOperations.haversine(walk.centerOf(a), walk.centerOf(b))
                    totalM += m
                    if (!cost.isNaN()) {
                        clear++
                        clearNs += ns
                        clearM += m
                    }
                }
                if (best == null || totalNs < best!![0]) {
                    best = doubleArrayOf(totalNs.toDouble(), clear.toDouble(), clearNs.toDouble(), clearM, totalM)
                }
            }
            return best!!
        }
        val fromOrigin = sweep((0 until expanded.count).map { origin to expanded.slots[it] })
        val pathPairs =
            PATH_SPANS.flatMap { span -> (0 until walkPath.size - span).map { walkPath[it] to walkPath[it + span] } }
        val alongPath = sweep(pathPairs)
        val priceOnly = sweep(pathPairs, reader = ungated)
        val corridorM = SpatialOperations.haversine(ctx.start, ctx.aim)
        val allNsPerM = alongPath[0] / alongPath[4]
        val nsPerM =
            if (alongPath[3] > 0.0) maxOf(alongPath[2] / alongPath[3], allNsPerM) else Double.NaN
        val measured = searchMs + fromOrigin[0] / 1e6
        val corridor = searchMs + expanded.count * corridorM * nsPerM / 1e6
        val push = searchMs + expanded.steps * corridorM * nsPerM / 1e6
        val ratio = corridor / searchMs
        val pathChords = PATH_SPANS.sumOf { maxOf(0, walkPath.size - it) }
        val line = "CHORD expansions=${expanded.count} steps=${expanded.steps} " +
            "originClear=${clearCount(fromOrigin)}/${expanded.count} originReadMs=${fmt(fromOrigin[0] / 1e6)} " +
            "pathClear=${clearCount(alongPath)}/$pathChords pathClearKm=${fmt(alongPath[3] / 1000.0)} " +
            "nsPerM=${fmt(nsPerM, 2)} allNsPerM=${fmt(allNsPerM, 2)} priceNsPerM=${fmt(priceOnly[0] / priceOnly[4], 2)} " +
            "corridorM=${fmt(corridorM)} searchMs=${fmt(searchMs)} " +
            "projMeasuredMs=${fmt(measured)} projCorridorMs=${fmt(corridor)} projPushMs=${fmt(push)} " +
            "corridorRatio=${fmt(ratio, 2)} (bar ${fmt(GATE_BAR, 1)}) ${verdictOf(ratio)}"
        return ChordGate(line, doubleArrayOf(searchMs, corridor, measured, push))
    }

    /** The gate's verdict on a projected-over-today [ratio]; no verdict where no chord stood to price it. */
    private fun verdictOf(ratio: Double): String = when {
        ratio.isNaN() -> "NO-READING"
        ratio < GATE_BAR -> "PASS"
        else -> "FAIL"
    }

    /** How many chords a sweep found clear. */
    private fun clearCount(sweep: DoubleArray): Int = sweep[1].toInt()

    /** The refined line's legs: count, the longest (m), and how many fall in each of [LEG_BANDS_M]'s bands. */
    private fun legsOf(line: List<LatLng>): String {
        val legs = line.zipWithNext { a, b -> SpatialOperations.haversine(a, b) }
        val bands = IntArray(LEG_BANDS_M.size + 1)
        for (leg in legs) bands[LEG_BANDS_M.indexOfFirst { leg <= it }.let { if (it < 0) LEG_BANDS_M.size else it }]++
        val labels = LEG_BANDS_M.map { "<=${it.toInt()}" } + ">${LEG_BANDS_M.last().toInt()}"
        return "LEGS n=${legs.size} maxM=${fmt(legs.maxOrNull() ?: 0.0)} " +
            labels.indices.joinToString(" ") { "${labels[it]}=${bands[it]}" }
    }

    /** The fine pass's own water for [oriented] at [lambda]: its field at the fine cell and the pull's setup over it. */
    private fun fineSetupOf(ctx: GridContext, oriented: List<LatLng>, lambda: Double): PullSetup {
        val field = costField(
            ctx.world, ctx.fineCellM, ctx.pace, withZones = true, withBand = true, zones = ctx.zones, lambda = lambda,
            markerStrength = ctx.markerStrength
        )
        return pullSetupFor(ctx, field, lambda, ctx.fineCellM, oriented.first(), oriented.last())
    }

    /**
     * One rung's refined line, its fine pass's trace, the fine pass's own time and the whole rung's, its grid, and the
     * coarse pass's own trace.
     */
    private class RungRun(
        val line: List<LatLng>,
        val trace: List<String>,
        val fineMs: Double,
        val rungMs: Double,
        val ctx: GridContext? = null,
        val passTrace: List<String> = emptyList(),
        val walk: WalkWindows? = null,
        val search: SearchOutcome? = null,
        val expansions: Expansions? = null
    )

    /** The search's expansions in order, and the steps it read out of them — the gate's [ExpansionProbe]. */
    private class Expansions : ExpansionProbe {
        var slots = IntArray(4096)
        var count = 0
        var steps = 0L

        override fun expanded(slot: Int, steps: Int) {
            if (count == slots.size) slots = slots.copyOf(count * 2)
            slots[count++] = slot
            this.steps += steps
        }
    }

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
        val setup = fineSetupOf(ctx, oriented, lambda)
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
    private suspend fun rung(world: BakedRegionWorld, from: LatLng, to: LatLng, lambda: Double): RungRun {
        val rungStartNs = System.nanoTime()
        val builder = RouteGridBuilder(EvolutiveGridPlan)
        val runner = RoutePassRunner(anyAngle)
        for (reach in listOf(AppConfig.routeAvoidCorridorReachM, AppConfig.routeAvoidCorridorReachM * 2.0)) {
            val ctx = builder.buildGrid(world, RoutePoint.of(from), RoutePoint.of(to), reach, paceKn, 1.0) ?: continue
            val walk = GridWalk(ctx.grid, ctx.startCell, ctx.aimCell, ctx.cellM, ctx.windows)
            val passTrace = mutableListOf<String>()
            val expansions = Expansions()
            val pass = runner.runPass(
                ctx, walk, lambda, publishStage = false, trace = { passTrace += it() }, probe = expansions
            )
            if (pass.search.path == null && !ctx.regionSaturated) continue
            val trace = mutableListOf<String>()
            val fineStartNs = System.nanoTime()
            val refined = RouteFinePass().finePass(ctx, pass.line, lambda) { trace += it() }
            val endNs = System.nanoTime()
            return RungRun(
                refined, trace, (endNs - fineStartNs) / 1_000_000.0, (endNs - rungStartNs) / 1_000_000.0, ctx, passTrace,
                walk.windows ?: WalkWindows.of(ctx.grid), pass.search, expansions
            )
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

        /** The `CLOCK` line's stages, in [clockOf]'s order. */
        val CLOCK_KEYS = listOf("searchMs", "pullMs", "snapMs", "finalMs", "fineMs", "approachMs", "rungMs")
        val MS_FIELD = Regex("""\bms=([0-9.]+)""")
        val LONE_FIELD = Regex("""\bloneTurns=([0-9]+)""")

        /** The `GATE` line's sums, in [ChordGate.values]' order. */
        val GATE_KEYS = listOf("searchMs", "projCorridorMs", "projMeasuredMs", "projPushMs")

        /** The plan's bar: the projected search under twice today's `SEARCH ms`. */
        const val GATE_BAR = 2.0

        /**
         * The fine pass's trace lines the report keeps: the sweep's keeps, the approach pass, the straightener and the
         * crossing re-solve's own verdicts (`FINE zone`, `FINE splice`, `FINE crossings=off`).
         */
        val TRACE_PREFIXES = listOf("SWEEP keep", "APPROACH", "STRAIGHTEN", "FINE zone", "FINE splice", "FINE crossings")

        /** The `LEGS` line's length bands (m), each band up to and including its bound. */
        val LEG_BANDS_M = doubleArrayOf(500.0, 1000.0, 2000.0, 3200.0, 6400.0)

        /** The timed sweeps per chord sample; the fastest is kept. */
        const val GATE_SWEEPS = 5

        /** The path-cell spans the clear-chord sample reads — about 0.8 to 6.4 km on the 100 m interior. */
        val PATH_SPANS = listOf(8, 16, 32, 64)
    }
}
