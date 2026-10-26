package ykws.android.maro.spatial.multipass

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import ykws.android.maro.BuildConfig
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.data.model.RouteResult
import ykws.android.maro.spatial.RouteAvoidEngine
import ykws.android.maro.spatial.RouteDeclarations
import ykws.android.maro.spatial.RouteUpdate
import ykws.android.maro.spatial.SpatialOperations
import ykws.android.maro.spatial.Units
import java.io.File
import java.util.Random
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

/**
 * **Does a pair draw the same line from either end? — measured over the region the app ships.**
 *
 * The question is the direction-symmetry plan's: the same two points, routed A→B and B→A, should give one
 * line, and where they do not the gap needs a number *and an address* before any repair is chosen. This
 * class is the instrument, not the verdict: it asserts nothing about the gap and prints it.
 *
 * **The world is the device's.** [BakedRegionWorld] reads the baked coastline, depth grid and speed zones
 * through the app's own deserialisers, so the islands, the shelf, the 300 m band and the zones are the real
 * ones; the engine under test is the shipped [RouteAvoidEngine] with each plan, the ladder's three rungs at
 * their shipped λ, and the shipped settings through [AppConfig].
 *
 * **Two tiers, one pair at a time.**
 * - *The engine tier* solves the pair both ways through the whole engine and reports what the user would
 *   see: the crowned rung, each rung's length and ETA, and how far the two drawn lines stand apart. It is
 *   the number the plan's bar is written in.
 * - *The stage tier* replays the engine's own pipeline for one rung — search, pull → snap → pull, the fine
 *   pass, the corner pass — and for each stage reports **two** gaps: *end to end*, the stage's output from
 *   the two runs, upstream differences included; and *isolated*, the stage handed **one and the same input**
 *   in both orientations, so what remains is that stage's own asymmetry. The isolated column is the address.
 *
 * **Two tests.** [namedPairs] walks hand-picked pairs round the Lérins and the Cap d'Antibes in full detail;
 * [randomPairs] draws a seeded set of pairs over the whole region and reports the distribution, so a number
 * is a rate and not an anecdote.
 *
 * A "gap" is the largest distance from any point of one line to the other, the second line reversed where
 * needed. Run it with the report printed and kept:
 *
 * `.\gradlew.bat --% :app:testDebugUnitTest --tests "*RouteDirectionSymmetryTest*" -Dmaro.prebake=true -Pmaro.testStdout=true`
 *
 * The report is also written to `app/build/route-symmetry-report.txt`. Gated like the other baked-data
 * guards: it skips unless `-Dmaro.prebake=true` and the baked files exist.
 */
class RouteDirectionSymmetryTest {

    private class EndPair(val name: String, val a: LatLng, val b: LatLng)

    private class RungReading(
        val lenF: Double, val lenR: Double, val etaF: Double, val etaR: Double,
        val vertsF: Int, val vertsR: Int, val gap: Double
    ) {
        val dLen get() = lenF - lenR
        val dEta get() = etaF - etaR
        val ok get() = abs(dLen) <= BAR_LEN_M && abs(dEta) <= AppConfig.routeAvoidLadderTieToleranceSec
    }

    /** `null` for a rung that landed no line from that end. */
    private class EngineReading(
        val crownF: Int?, val crownR: Int?,
        val rungs: List<RungReading?>, val missingF: List<Int>, val missingR: List<Int>
    ) {
        val sameCrown get() = crownF == crownR
        val noPathAsymmetry get() = (missingF.isEmpty()) != (missingR.isEmpty()) || missingF != missingR
    }

    private class StageRow(val name: String, val e2eGap: Double, val isoGap: Double, val lenF: Double, val lenR: Double)

    private class StageReading(
        val costF: Double?, val costR: Double?, val cellsF: Int, val cellsR: Int,
        val expF: Int, val expR: Int, val rows: List<StageRow>, val diag: String?
    )

    /** Real sea points around Cannes, the Lérins, the Cap d'Antibes and the Baie des Anges, and offshore. */
    private val named = listOf(
        EndPair("cannes-bay -> baie-des-anges (rounds the Cap)", LatLng(43.538, 7.020), LatLng(43.575, 7.150)),
        EndPair("west of Lerins -> east of Lerins (through or round the islands)", LatLng(43.515, 7.020), LatLng(43.512, 7.110)),
        EndPair("offshore open water", LatLng(43.460, 6.990), LatLng(43.500, 7.170)),
        EndPair("napoule -> cannes (shore-hugging)", LatLng(43.510, 6.945), LatLng(43.535, 7.030)),
        EndPair("nice offshore -> antibes offshore (long coast)", LatLng(43.665, 7.285), LatLng(43.580, 7.160))
    )

    private val plans = listOf("avoid" to UniformGridPlan, "evolutive" to EvolutiveGridPlan)

    private val paces = listOf(15.0)

    private val rungNames = listOf("around", "best", "fast")

    private fun rungLambdas() = listOf(
        AppConfig.routeAvoidLadderAroundLambda, AppConfig.routeAvoidLadderBestLambda, AppConfig.routeAvoidLadderFastLambda
    )

    private val report = StringBuilder()

    private fun say(line: String = "") {
        report.appendLine(line)
        println(line)
    }

    private fun worldOrSkip(): Pair<BakedRegionWorld, File> {
        Assume.assumeTrue("set -Dmaro.prebake=true to run the symmetry instrument", System.getProperty("maro.prebake") == "true")
        val repoDir = System.getProperty("maro.repoDir")?.let { File(it) } ?: File("..")
        val world = BakedRegionWorld.load(repoDir, BuildConfig.REGION_ID)
        Assume.assumeTrue("no baked coastline and depth for ${BuildConfig.REGION_ID} under data/app-assets", world != null)
        return world!! to repoDir
    }

    private fun keep(repoDir: File, name: String) {
        File(repoDir, "app/build/$name").apply { parentFile?.mkdirs() }.writeText(report.toString())
    }

    // ── Test 1: the named pairs, in full ──────────────────────────────────────

    @Test
    fun namedPairs() {
        val (world, repoDir) = worldOrSkip()
        say("ROUTE DIRECTION SYMMETRY (named pairs) — region ${BuildConfig.REGION_ID}, paces $paces kn, rung lambdas ${rungLambdas()}")
        say("gap = largest distance (m) from either line to the other, one of them reversed; bar = same crown, |dLen| <= ${BAR_LEN_M.toInt()} m, |dETA| <= ${AppConfig.routeAvoidLadderTieToleranceSec} s")
        var measured = 0
        for (pair in named) {
            val refusal = refuse(world, pair.a) ?: refuse(world, pair.b)
            if (refusal != null) {
                say()
                say("PAIR ${pair.name}: SKIPPED — $refusal")
                continue
            }
            measured++
            for ((planName, plan) in plans) for (pace in paces) {
                say()
                say("PAIR ${pair.name} | plan=$planName pace=$pace kn | straight ${fmt(SpatialOperations.haversine(pair.a, pair.b))} m")
                val engine = readEngine(world, plan, pace, pair)
                printEngine(engine)
                for (i in rungNames.indices) printStages(rungNames[i], rungLambdas()[i], readStages(world, plan, pace, pair, rungLambdas()[i]))
            }
        }
        keep(repoDir, "route-symmetry-report.txt")
        assertTrue("at least one pair stood on usable water — see the SKIPPED lines", measured > 0)
    }

    // ── Test 2: a seeded survey, as rates ─────────────────────────────────────

    @Test
    fun randomPairs() {
        val (world, repoDir) = worldOrSkip()
        val pace = paces.first()
        val drawn = drawPairs(world, SURVEY_PAIRS)
        say("ROUTE DIRECTION SYMMETRY (survey) — region ${BuildConfig.REGION_ID}, ${drawn.size} seeded pairs, pace $pace kn, stage tier on the 'best' rung (lambda ${AppConfig.routeAvoidLadderBestLambda})")
        say("columns: gap = engine line gap over the three rungs (worst); iso = isolated stage gap (m) on the best rung; noPath = a rung landed from one end only")
        for ((planName, plan) in plans) {
            say()
            say("PLAN $planName")
            say("  pair  km   crown  worstGap  worstdLen  worstdETA  bar   | iso: pull  fine  corner | noPath")
            val engines = ArrayList<EngineReading>()
            val isoPull = ArrayList<Double>()
            val isoFine = ArrayList<Double>()
            val isoCorner = ArrayList<Double>()
            val worstGaps = ArrayList<Double>()
            for ((n, pair) in drawn.withIndex()) {
                val engine = readEngine(world, plan, pace, pair)
                val stage = readStages(world, plan, pace, pair, AppConfig.routeAvoidLadderBestLambda)
                engines += engine
                val landed = engine.rungs.filterNotNull()
                val gap = landed.maxOfOrNull { it.gap } ?: Double.NaN
                if (!gap.isNaN()) worstGaps += gap
                val pull = stage.rows.firstOrNull { it.name.startsWith("pull") }?.isoGap
                val fine = stage.rows.firstOrNull { it.name.startsWith("fine") }?.isoGap
                val corner = stage.rows.firstOrNull { it.name.startsWith("corner") }?.isoGap
                pull?.let { isoPull += it }; fine?.let { isoFine += it }; corner?.let { isoCorner += it }
                val barAll = engine.sameCrown && !engine.noPathAsymmetry && landed.all { it.ok }
                say(
                    "  ${(n + 1).toString().padStart(2)}  ${fmt(SpatialOperations.haversine(pair.a, pair.b) / 1000.0).padStart(5)}  " +
                        "${if (engine.sameCrown) "same" else "DIFF"}  ${fmt(gap).padStart(8)}  " +
                        "${fmt(landed.maxOfOrNull { abs(it.dLen) } ?: Double.NaN).padStart(9)}  " +
                        "${fmt(landed.maxOfOrNull { abs(it.dEta) } ?: Double.NaN).padStart(9)}  ${if (barAll) "ok " else "GAP"}   |" +
                        "      ${fmt(pull ?: Double.NaN).padStart(5)} ${fmt(fine ?: Double.NaN).padStart(5)} ${fmt(corner ?: Double.NaN).padStart(6)}  | " +
                        if (engine.noPathAsymmetry) "A->B missing ${engine.missingF}, B->A missing ${engine.missingR}  ${stage.diag ?: ""}" else "-"
                )
            }
            say("  SUMMARY $planName: ${engines.size} pairs")
            say("    crown differs ........ ${engines.count { !it.sameCrown }}")
            say("    a rung from one end only ${engines.count { it.noPathAsymmetry }}")
            say("    a rung misses the bar . ${engines.count { e -> e.rungs.filterNotNull().any { !it.ok } }}")
            say("    line gap (worst rung) ${dist(worstGaps)}")
            say("    isolated pull ........ ${dist(isoPull)}")
            say("    isolated fine ........ ${dist(isoFine)}")
            say("    isolated corner ...... ${dist(isoCorner)}")
        }
        keep(repoDir, "route-symmetry-survey.txt")
        assertTrue("the survey drew pairs", drawn.isNotEmpty())
    }

    /** [count] seeded pairs of open sea, 3–18 km apart, over the baked region's own sea. */
    private fun drawPairs(world: BakedRegionWorld, count: Int): List<EndPair> {
        val rng = Random(SURVEY_SEED)
        val b = world.regionBounds
        // The coast the Lérins and the Cap d'Antibes stand on, not the whole 6 NM envelope: a pair across a
        // continent of open sea would only measure the empty case again.
        val latS = maxOf(b.latSouth, 43.43)
        val latN = minOf(b.latNorth, 43.70)
        val lonW = maxOf(b.lonWest, 6.93)
        val lonE = minOf(b.lonEast, 7.32)
        fun point(): LatLng = LatLng(latS + rng.nextDouble() * (latN - latS), lonW + rng.nextDouble() * (lonE - lonW))
        val out = ArrayList<EndPair>()
        var tries = 0
        while (out.size < count && tries++ < 20_000) {
            val a = point()
            val c = point()
            val km = SpatialOperations.haversine(a, c) / 1000.0
            if (km < 3.0 || km > 18.0) continue
            if (refuse(world, a) != null || refuse(world, c) != null) continue
            out += EndPair("random ${out.size + 1}", a, c)
        }
        return out
    }

    // ── The pair's ends ───────────────────────────────────────────────────────

    /** Why [p] is not a fair end — land, a coast hug, a shoal — or `null` where it is open deep water. */
    private fun refuse(world: BakedRegionWorld, p: LatLng): String? {
        if (!world.isWater(p.latitude, p.longitude)) return "$p is on land"
        val coastM = world.distanceToCoastM(p.latitude, p.longitude)
        if (coastM < 150.0) return "$p is ${fmt(coastM)} m off the coast"
        val depth = world.depthAt(p.latitude, p.longitude)
        if (depth.hasData && depth.depthM < 6f) return "$p reads ${depth.depthM} m deep"
        return null
    }

    // ── The engine tier ───────────────────────────────────────────────────────

    private class Ladder(val rungs: List<RouteResult.Success?>, val crown: Int?)

    private fun solveLadder(world: BakedRegionWorld, plan: RouteGridPlan, pace: Double, from: LatLng, to: LatLng): Ladder =
        runBlocking {
            val engine = RouteAvoidEngine(
                paceKn = { pace },
                verdictWeight = { AppConfig.routeAvoidSlowWaterWeight },
                worldProvider = { world },
                plan = plan
            )
            val declared = engine.routesToCompute(RoutePoint.of(from), RoutePoint.of(to)) as? RouteDeclarations.Available
                ?: return@runBlocking Ladder(emptyList(), null)
            var last: RouteUpdate? = null
            val rungs = declared.computations.map { computation ->
                coroutineScope {
                    val subscribed = CompletableDeferred<Unit>()
                    val done = CompletableDeferred<RouteUpdate>()
                    val collector = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                        engine.updates
                            .onStart { subscribed.complete(Unit) }
                            .collect { u -> if (u.nextStage == null && !done.isCompleted) done.complete(u) }
                    }
                    subscribed.await()
                    engine.startLookup(computation.id)
                    val terminal = withTimeout(300_000) { done.await() }
                    collector.cancel()
                    last = terminal
                    terminal.result
                }
            }
            val crownId = last?.verdict?.best?.lookupId
            Ladder(rungs, declared.computations.indexOfFirst { it.id == crownId }.takeIf { it >= 0 })
        }

    private fun readEngine(world: BakedRegionWorld, plan: RouteGridPlan, pace: Double, pair: EndPair): EngineReading {
        val fwd = solveLadder(world, plan, pace, pair.a, pair.b)
        val rev = solveLadder(world, plan, pace, pair.b, pair.a)
        val rungs = rungNames.indices.map { i ->
            val f = fwd.rungs.getOrNull(i)
            val r = rev.rungs.getOrNull(i)
            if (f == null || r == null) null else {
                val lf = f.points.map { LatLng(it.latitude, it.longitude) }
                val lr = r.points.map { LatLng(it.latitude, it.longitude) }.reversed()
                RungReading(f.distanceM, r.distanceM, f.durationSec, r.durationSec, lf.size, lr.size, gapM(lf, lr))
            }
        }
        return EngineReading(
            fwd.crown, rev.crown, rungs,
            rungNames.indices.filter { fwd.rungs.getOrNull(it) == null },
            rungNames.indices.filter { rev.rungs.getOrNull(it) == null }
        )
    }

    private fun printEngine(e: EngineReading) {
        say("  ENGINE   crown: A->B ${crownName(e.crownF)}  B->A ${crownName(e.crownR)}  ${if (e.sameCrown) "same" else "DIFFERENT"}")
        say("  ENGINE   rung    lenA->B   lenB->A   dLen   etaA->B  etaB->A   dETA   vertsF/R   gap(m)   bar")
        for (i in rungNames.indices) {
            val r = e.rungs[i]
            if (r == null) {
                say("  ENGINE   ${rungNames[i].padEnd(6)}  no line from ${listOfNotNull("A->B".takeIf { i in e.missingF }, "B->A".takeIf { i in e.missingR }).joinToString(" and ")}")
                continue
            }
            say(
                "  ENGINE   ${rungNames[i].padEnd(6)}  ${fmt(r.lenF).padStart(8)}  ${fmt(r.lenR).padStart(8)}  " +
                    "${fmt(r.dLen).padStart(6)}  ${fmt(r.etaF).padStart(7)}  ${fmt(r.etaR).padStart(7)}  " +
                    "${fmt(r.dEta).padStart(5)}  ${"${r.vertsF}/${r.vertsR}".padEnd(9)}  ${fmt(r.gap).padStart(6)}   ${if (r.ok) "ok" else "GAP"}"
            )
        }
    }

    private fun crownName(i: Int?) = i?.let { rungNames.getOrNull(it) } ?: "-"

    // ── The stage tier ────────────────────────────────────────────────────────

    private fun readStages(world: BakedRegionWorld, plan: RouteGridPlan, pace: Double, pair: EndPair, lambda: Double): StageReading =
        runBlocking {
            val builder = RouteGridBuilder(plan)
            val runner = RoutePassRunner()
            val fine = RouteFinePass()
            // The engine's own growth step: a first corridor that finds no way is rebuilt once at double reach.
            suspend fun pass(from: LatLng, to: LatLng): Pair<GridContext?, PassReading?> {
                var ctx: GridContext? = null
                for (reach in listOf(AppConfig.routeAvoidCorridorReachM, AppConfig.routeAvoidCorridorReachM * 2.0)) {
                    ctx = builder.buildGrid(world, RoutePoint.of(from), RoutePoint.of(to), reach, pace, 1.0) ?: continue
                    val reading = runner.runPass(ctx, walkOf(ctx), lambda, publishStage = false)
                    if (reading.search.path != null || ctx.regionSaturated) return ctx to reading
                }
                return ctx to ctx?.let { runner.runPass(it, walkOf(it), lambda, publishStage = false) }
            }
            val (ctxF, passF) = pass(pair.a, pair.b)
            val (ctxR, passR) = pass(pair.b, pair.a)
            if (ctxF == null || ctxR == null || passF == null || passR == null) {
                return@runBlocking StageReading(null, null, 0, 0, 0, 0, emptyList(), "no grid (corridor empty)")
            }
            if (passF.search.path == null || passR.search.path == null) {
                val which = listOfNotNull("A->B".takeIf { passF.search.path == null }, "B->A".takeIf { passR.search.path == null })
                return@runBlocking StageReading(
                    passF.search.takeIf { it.path != null }?.costSec, passR.search.takeIf { it.path != null }?.costSec,
                    passF.pathCells, passR.pathCells, passF.search.expansions, passR.search.expansions, emptyList(),
                    "no path ${which.joinToString("/")}: A->B ${diag(passF.search)} | B->A ${diag(passR.search)}"
                )
            }
            val rows = ArrayList<StageRow>()
            fun row(name: String, e2eF: List<LatLng>, e2eRrev: List<LatLng>, isoF: List<LatLng>, isoRrev: List<LatLng>) {
                rows += StageRow(name, gapM(e2eF, e2eRrev), gapM(isoF, isoRrev), lengthM(isoF), lengthM(isoRrev))
            }

            val chainF = chainOf(ctxF, passF)
            val chainR = chainOf(ctxR, passR)
            row("search chain", chainF, chainR, chainF, chainR.reversed())

            // pull → snap → pull, handed the forward chain in both orientations.
            val fullF = listOf(ctxF.start) + chainF + listOf(ctxF.aim)
            val setupF = setupOf(ctxF, lambda, ctxF.start, ctxF.aim)
            val setupR = setupOf(ctxF, lambda, ctxF.aim, ctxF.start)
            val pulledF = pullSnapPull(setupF, ctxF.sets, fullF)
            val pulledRev = pullSnapPull(setupR, ctxF.sets, fullF.reversed()).reversed()
            row("pull -> snap -> pull", passF.line, passR.line.reversed(), pulledF, pulledRev)

            // The fine pass, handed the forward pass line in both orientations.
            val refinedF = fine.finePass(ctxF, passF.line, lambda)
            val refinedR = fine.finePass(ctxR, passR.line, lambda)
            val refinedIso = fine.finePass(ctxR, passF.line.reversed(), lambda).reversed()
            row("fine pass (sweep, DP, crossings)", refinedF, refinedR.reversed(), refinedF, refinedIso)

            // The corner pass, handed the forward refined line in both orientations.
            val roundedF = RouteCornerPass.round(refinedF, ctxF.pace, world, ctxF.gate, ctxF.marginM).points
            val roundedR = RouteCornerPass.round(refinedR, ctxR.pace, world, ctxR.gate, ctxR.marginM).points
            val roundedIso = RouteCornerPass.round(refinedF.reversed(), ctxR.pace, world, ctxR.gate, ctxR.marginM).points.reversed()
            row("corner pass (compound, share, room)", roundedF, roundedR.reversed(), roundedF, roundedIso)

            StageReading(
                passF.search.costSec, passR.search.costSec, passF.pathCells, passR.pathCells,
                passF.search.expansions, passR.search.expansions, rows, null
            )
        }

    private fun diag(s: SearchOutcome) =
        "passable=${s.passableCells} expansions=${s.expansions} aimClosed=${s.aimClosed} startLayer=${s.startLayer} " +
            "startOpenSteps=${s.startOpenSteps} strictStart=${s.strictStart} strictAim=${s.strictAim} rewalked=${s.rewalked}"

    private fun printStages(rung: String, lambda: Double, s: StageReading) {
        say("  STAGES   rung $rung (lambda $lambda)")
        if (s.diag != null) say("  STAGES     ${s.diag}")
        if (s.costF != null && s.costR != null) {
            say(
                "  STAGES     search cost  A->B ${fmt(s.costF, 3)} s   B->A ${fmt(s.costR, 3)} s   " +
                    "d ${fmt(s.costF - s.costR, 3)} s (${fmt(100.0 * (s.costF - s.costR) / max(s.costF, 1e-9), 3)} %)   " +
                    "cells ${s.cellsF}/${s.cellsR}   expansions ${s.expF}/${s.expR}"
            )
        }
        if (s.rows.isEmpty()) return
        say("  STAGES     stage                                end-to-end gap   isolated gap   len A->B   len B->A(rev)")
        for (r in s.rows) {
            say(
                "  STAGES     ${r.name.padEnd(36)}  ${fmt(r.e2eGap).padStart(10)}      " +
                    "${fmt(r.isoGap).padStart(10)}   ${fmt(r.lenF).padStart(8)}   ${fmt(r.lenR).padStart(8)}"
            )
        }
    }

    private fun walkOf(c: GridContext) = GridWalk(c.grid, c.startCell, c.aimCell, c.cellM, c.windows)

    /** The A* cell chain as points, read through the walk's own windows exactly as the pass reads it. */
    private fun chainOf(c: GridContext, pass: PassReading): List<LatLng> {
        val windows = c.windows ?: WalkWindows.of(c.grid)
        return pass.search.path!!.map { windows.center(it.layer, it.row, it.col) }
    }

    /** The pass's own pull setup, restated for [start] and [aim] — the recipe `RoutePassRunner.runPass` follows. */
    private fun setupOf(c: GridContext, lambda: Double, start: LatLng, aim: LatLng): PullSetup {
        val tailCellM = if ((c.windows?.layerCount ?: 1) > 1) c.fineCellM else c.cellM
        val field = costField(
            c.world, tailCellM, c.pace, withZones = true, withBand = true, zones = c.zones,
            lambda = lambda, markerStrength = c.markerStrength
        )
        return PullSetup(
            c.marginM, tailCellM, c.cellM, field, start, aim,
            gate = c.gate,
            berth = berthLawOf(c.plan, c.depthGateActive),
            berthLambda = lambda,
            paceMps = Units.knotsToMps(c.pace)
        )
    }

    private fun pullSnapPull(setup: PullSetup, sets: List<CornerSet>, full: List<LatLng>): List<LatLng> {
        val pulled = MultipassPull.pullBothWays(setup, full)
        return MultipassPull.pullBothWays(setup, snapToCorners(setup, sets, pulled))
    }

    // ── Measures ──────────────────────────────────────────────────────────────

    private fun lengthM(line: List<LatLng>) = polylineLengthM(line)

    /** The larger of the two one-way gaps: the farthest any sampled point of one line stands from the other. */
    private fun gapM(a: List<LatLng>, b: List<LatLng>): Double = max(oneWayGapM(a, b), oneWayGapM(b, a))

    private fun oneWayGapM(from: List<LatLng>, to: List<LatLng>): Double {
        if (from.size < 2 || to.size < 2) return Double.NaN
        var worst = 0.0
        for (p in sampled(from, SAMPLE_M)) {
            var near = Double.MAX_VALUE
            for (k in 0 until to.size - 1) near = minOf(near, SpatialOperations.pointToSegmentDistance(p, to[k], to[k + 1]))
            worst = max(worst, near)
        }
        return worst
    }

    private fun sampled(line: List<LatLng>, stepM: Double): List<LatLng> = buildList {
        for (k in 0 until line.size - 1) {
            val a = line[k]
            val b = line[k + 1]
            val pieces = max(1, ceil(SpatialOperations.haversine(a, b) / stepM).toInt())
            for (j in 0 until pieces) {
                val t = j.toDouble() / pieces
                add(LatLng(a.latitude + (b.latitude - a.latitude) * t, a.longitude + (b.longitude - a.longitude) * t))
            }
        }
        add(line.last())
    }

    /** `median / p90 / max (n)` of [values], the survey's way of stating a rate of gaps. */
    private fun dist(values: List<Double>): String {
        if (values.isEmpty()) return "-"
        val s = values.sorted()
        fun at(q: Double) = s[((s.size - 1) * q).toInt()]
        return "median ${fmt(at(0.5))}  p90 ${fmt(at(0.9))}  max ${fmt(s.last())}  over >${BAR_LEN_M.toInt()} m: ${s.count { it > BAR_LEN_M }}  (n=${s.size})"
    }

    private fun fmt(v: Double, digits: Int = 1) = if (v.isNaN()) "n/a" else "%.${digits}f".format(java.util.Locale.ROOT, v)

    private companion object {
        const val SAMPLE_M = 10.0
        /** The plan's bar on length: one coarse cell. */
        const val BAR_LEN_M = 100.0
        const val SURVEY_PAIRS = 24
        const val SURVEY_SEED = 20261020L
    }
}
