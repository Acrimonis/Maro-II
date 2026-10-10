package ykws.android.maro.spatial

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import ykws.android.maro.R
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.data.model.RouteResult
import ykws.android.maro.data.model.RouteSlowLimit
import ykws.android.maro.data.regulation.SpeedZone
import ykws.android.maro.spatial.multipass.MultipassGrid
import ykws.android.maro.spatial.multipass.MultipassSearch
import ykws.android.maro.spatial.multipass.MultipassWorld
import ykws.android.maro.spatial.multipass.CellIndex
import ykws.android.maro.spatial.multipass.GridContext
import ykws.android.maro.spatial.multipass.GridWalk
import ykws.android.maro.spatial.multipass.PassReading
import ykws.android.maro.spatial.multipass.RouteCornerPass
import ykws.android.maro.spatial.multipass.RouteCostField
import ykws.android.maro.spatial.multipass.RouteFinePass
import ykws.android.maro.spatial.multipass.RouteGridBuilder
import ykws.android.maro.spatial.multipass.RouteGridPlan
import ykws.android.maro.spatial.multipass.RoutePassRanking
import ykws.android.maro.spatial.multipass.RoutePassRunner
import ykws.android.maro.spatial.multipass.RoutePreference
import ykws.android.maro.spatial.multipass.TimedLine
import ykws.android.maro.spatial.multipass.UniformGridPlan
import ykws.android.maro.spatial.multipass.ZoneRing
import ykws.android.maro.spatial.multipass.bandReachM
import ykws.android.maro.spatial.multipass.clockSampleM
import ykws.android.maro.spatial.multipass.depthClearsGate
import ykws.android.maro.spatial.multipass.fmt
import ykws.android.maro.spatial.multipass.forcedCrossingZoneNames
import ykws.android.maro.spatial.multipass.inBand
import ykws.android.maro.spatial.multipass.inZone
import ykws.android.maro.spatial.multipass.insideBandWidthM
import ykws.android.maro.spatial.multipass.msSince
import ykws.android.maro.spatial.multipass.slowShares
import ykws.android.maro.spatial.multipass.slowTimeByLimit
import ykws.android.maro.spatial.multipass.timeLineWithLimits
import ykws.android.maro.spatial.multipass.timeLineWithProfile
import ykws.android.maro.spatial.multipass.polylineLengthM
import ykws.android.maro.spatial.multipass.zoneMetres
import ykws.android.maro.spatial.multipass.zonePriceSec
import ykws.android.maro.spatial.multipass.zoneSlowShare

/**
 * **The avoid engine: avoid land, islands and hazard rings, and keep off water shallower than the
 * depth gate.**
 *
 * It answers [RouteEngine.routesToCompute] with the ladder's three fixed-aversion rungs — around,
 * balanced and through slow water — after **repairing** the two ends:
 * a point that is land or shallower than the minimum depth is moved to the nearest valid water on the
 * sea side. A pair that cannot be repaired is refused with [RouteReason.CANNOT_REPAIR], and one whose
 * judgement layers are absent is refused with [RouteReason.WORLD_NOT_READY]. The pipeline is
 * corridor-bounded grid A* plus a clearance taut pull, run on the [MultipassWorld] the injected provider
 * supplies.
 *
 * **One cost field, every source through it.** The sources are read as a [RouteCostField] — a `HARD`
 * wall the route may never cross, a `SOFT` price it may pay — and the rasterizer writes each cell once
 * with a base cost to which a source may only add. Stage 1's land is the field's first hard wall,
 * materialized by the geometry sweep; the 3 m depth gate is the second, rastered cell by cell; and the
 * 300 m band (phase 3) and the regulated speed zones (phase 4) land as **limits on the grid**, priced by
 * the A\* per expansion and by the pull's guard per point — never as a price baked into the base.
 *
 * **The band.** The layer's own width off the coast, carrying its own absolute limit
 * (`route.avoid.zone300.limitKn`) and priced by the **same law as a speed zone**: a metre inside it
 * costs that limit's time excess over the pace, scaled by the pass's λ, so a 5 kn band cell and a
 * 5 kn ring cell cost the same by construction. The band's limit is written onto the grid's own band
 * cells — the width in full, the outside margin at the band's fraction — so the A\* prices it per
 * expansion and the band's price **follows the corrected λ exactly as a ring's does**, the strictest
 * limit winning where the band and a ring overlap. The pull's guard prices the same law off the point
 * geometry and refuses a chord whose own price exceeds the cell path's over the span it would replace.
 * A start or aim already inside the band is accepted, so a berth in a marina basin is priced rather
 * than refused. `route.avoid.zone300.enabled` switches the **price** alone: the clock reads the band's
 * limit either way, an absolute limit never being suspended by a tuning switch.
 *
 * **The depth gate.** A bilinear depth read per cell centre: a known depth below
 * `route.avoid.depthGate.minM` paints the cell land, ANDed with the coastline's own water through the
 * cell's passability, and everything not below the threshold — deeper water, coarse sources, NoData
 * alike — is ignored, with no confidence floor and no penalty. It is a coarse guard on the route being
 * written, not a fine sounding.
 *
 * **The repair.** The first step of [routesToCompute]: each end that fails the engine's own water
 * test — on the coastline, or shallower than the gate — is moved by an 8-direction ring sweep at a
 * 25 m step growing to `route.repair.maxRadiusM` (default 200 m), the first valid point winning.
 * Because the first valid point is the *nearest*, it is on the sea side by construction. The budget is
 * the acceptance, not a value: the repair must answer in under 50 ms for one point, carried in the
 * KDoc and proven by the step's own test.
 *
 * **What the answer means.** The emitted polyline starts at the raw start and ends at the raw aim
 * (`destinationMoved = false`, the pin stands on the aim); the snapped cells are only the search's
 * anchor. An exhausted search answers [RouteReason.NO_PATH] on the update flow — retried once with the
 * corridor reach doubled. Each leg is timed at the pace in force, asked fresh per answer.
 *
 * **The ladder.** Each rung is a full solve at its own fixed λ, over one grid the three share: the grid
 * is built once and stores the band's and the rings' **limits**, so a rung's A* pass prices every slow
 * cell at its own aversion without a second rasterise. The forced-crossing growth and the fine pass (§5)
 * still run — the wider corridor kept only when it forces fewer crossings, and the fine pass on the
 * settled line where a restrictive zone the coarse grid could not see around gets a local A* at
 * `route.avoid.grid.fineCellM`. There is no budget loop: the slow-water budget is demoted, so a rung is
 * computed at its own λ and never corrected.
 *
 * **The ranking.** The engine folds its own rungs: at every rung's terminal the settled costs of the
 * rungs that have landed are ranked through [RoutePassRanking] under the preference in force, and the
 * winner rides that terminal as [RouteRunningBest] — a **running best**, so a surface can read *so far,
 * the winner is…*. The two inputs the ranking reads are the engine's own providers, `aversionKn` (the
 * preference) and `slowWaterBudgetPct` (Best's gate); the answer carries no instrumentation vocabulary,
 * which is why the ranking lives here and never on an emitted line.
 *
 * Coroutines and `Flow` only: no thread of its own, and the search checks the calling job between its
 * expansions so a flung map never queues behind a computation nobody wants any more.
 */
class RouteAvoidEngine(
    /** The pace in force (kn), asked fresh on every answer so a slider move reaches the next line. */
    private val paceKn: () -> Double,
    /**
     * The **aversion λ** (0–5) the user's Driving preference stands at, asked fresh like the pace so a
     * slider move reaches the next line. It no longer prices the search — the three rungs carry their
     * own fixed λ — but it **names the stop the ranking measures against** ([RoutePreference]): the
     * preference states an intent, and the ranking answers which settled rung delivers it.
     */
    private val aversionKn: () -> Double,
    /**
     * The **slow-water budget** (per cent of a trip, 0–100), asked fresh like the pace so a slider move
     * reaches the next line: it is **Best's gate** in the ranking, the ratio a rung's zone share must
     * stay within to be led on the clock. 0 leaves only a zero-zone line qualifying, 100 qualifies every
     * line, and a share above it is beaten by the smaller share rather than reported here.
     */
    private val slowWaterBudgetPct: () -> Int,
    /** The world provider — the map always holds the layers it wraps, so it answers a live world. */
    private val worldProvider: () -> MultipassWorld,
    /**
     * **The marker price's live strength** — the Routing row's factor, asked fresh like the pace so a
     * slider move reaches the next arm. It scales the marker price and nothing else; the wall is not
     * scaled.
     */
    private val markerStrength: () -> Double = { 1.0 },
    /**
     * **The decisions this engine makes about its own walk** — the cell it rasterizes the corridor at and
     * the fine cell its clock steps at. `avoid` ships [`UniformGridPlan`], which is exactly the behaviour
     * this engine had before the plan existed, so the default changes nothing; a second algorithm passes
     * its own and inherits the whole pipeline, the clock and the readings unchanged.
     */
    private val plan: RouteGridPlan = UniformGridPlan,
    /**
     * **The instrument's sink — where a trace line goes.** The default is the device's own channel:
     * `Log.i` under the `MaroRoute` tag, switched by the tag's level ([logEnabled]) and therefore
     * **inert off-device**, where a plain JVM's `android.util.Log` throws and the read answers false.
     * A JVM harness injects its own sink and captures **every** reading the device trace prints — the
     * grid's ms, the pull's and the fine pass's `priceReads`/`marks`/`priceMs`, the expansions — because
     * those figures live on this channel alone and are never returned on a value. The default, `null`,
     * keeps the device behaviour byte-for-byte.
     */
    private val traceSink: ((String) -> Unit)? = null
) : RouteEngine {

    /** One per-engine channel, buffered so a stage emission never waits on the collector. */
    private val _updates = MutableSharedFlow<RouteUpdate>(extraBufferCapacity = 64)

    override val updates: Flow<RouteUpdate> = _updates.asSharedFlow()

    /** The grid builder — the corridor, the harvest, the one rasterize and the ends' berth carve. */
    private val gridBuilder = RouteGridBuilder(plan)

    /** One pass's walk — the A*, the taut pull, the corner snap and the clock, composed once. */
    private val runner = RoutePassRunner()

    /** The fine pass — the refinement along the settled line, composed once. */
    private val finePass = RouteFinePass()

    /** The engine's own `trace`, handed to the seats so the instrument stays the engine's. */
    private val seatTrace: (() -> String) -> Unit = { message -> trace(message) }

    /**
     * The lane every lookup's job runs on — an engine owns its compute.
     *
     * It carries its own [CoroutineExceptionHandler] so a lookup that fails **outside** the [runComputation]
     * catch is narrated as one `FAILED` trace line rather than escaping to the JVM's default handler.
     * [runComputation]'s own KDoc is the home of the one corner this covers and why it is left uncaught.
     */
    private val computeScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, failure ->
            runCatching { trace { "FAILED ${failure.javaClass.simpleName}: ${failure.message}" } }
        }
    )

    /**
     * The live lookup jobs, by lookup id. [cancelLookup] removes and cancels one, and each lookup's own
     * completion removes its entry, so a long-lived engine never accumulates one per finished lookup. A
     * concurrent map because a completion runs on the compute lane while the caller may cancel.
     */
    private val jobs = ConcurrentHashMap<RouteId, Job>()

    /** The repaired pair the current declaration set runs on — set by [routesToCompute]. */
    private var repairedOrigin: RoutePoint? = null
    private var repairedDestination: RoutePoint? = null

    /** The computations the last [routesToCompute] declared, by id. */
    private var declarations: Map<RouteId, Computation> = emptyMap()

    /** The id and stage mints: fresh per declaration, lookup and computation. */
    private var nextComputationId = 0L
    private var nextLookupId = 0L

    /**
     * **The current arm's shared-grid holder** — fresh per [routesToCompute] and published with one
     * `@Volatile` write, so a lookup captures the arm it was declared under and a fresh arm cannot be
     * clobbered by a stale one. `null` until the first arm opens, so no holder is allocated for an arm that
     * never runs. The holder carries its own lock, the one build the arm's three rungs share, and the
     * in-flight count that disposes that build once the arm's last lookup has ended.
     */
    @Volatile
    private var ladderHolder: LadderGridHolder? = null

    /**
     * **The rungs that have landed this arm, in landing order** — each with the lookup that owns it and
     * its own ladder index, so the running best can be folded and reported without the engine ever
     * holding a page. Small, per-arm and dropped with the arm, like the declarations and the shared grid.
     */
    private var landedRungs: MutableList<LandedRung> = mutableListOf()

    /** One lock for the fold: the rungs land concurrently, so add-and-rank is one critical step. */
    private val rankingLock = Mutex()

    /** The first declared computation's id — the one that narrates the panel's stage line. */
    private var stageComputationId: RouteId? = null

    /** The pass the instrument is on, reset at each solve so `STAGE` can carry its index. */
    private var passIndex = 0

    /**
     * The id of the **main** lookup's stage channel while it runs. Only the main publishes stages —
     * a candidate's pass is logged, never narrated — so this field is touched by the main lookup
     * alone and a concurrent candidate can never write the wrong id into its emissions.
     */
    private var mainLookupId: RouteId? = null

    /** The stage the main lookup last published, so the finished-then-next pair can be completed. */
    private var lastStage: RouteStage? = null

    /**
     * The instrument's **one gate**: the `MaroRoute` tag's own level, read once and remembered.
     *
     * The read is taken through [runCatching] because a plain JVM unit test carries the mockable
     * `android.util.Log`, whose methods throw: there the read answers `false` and the instrumentation
     * stays inert rather than failing a test that has nothing to do with the log. On the device it is
     * the ordinary level, set with `adb shell setprop log.tag.MaroRoute INFO`.
     */
    private val logEnabled: Boolean by lazy {
        runCatching { Log.isLoggable(TAG, Log.INFO) }.getOrDefault(false)
    }

    override fun routesToCompute(origin: RoutePoint, destination: RoutePoint): RouteDeclarations {
        val world = worldProvider()
        // The repair judges by the coastline and the depth gate; without either layer a search would
        // draw a line over what it cannot see, so the pair is refused by name rather than drawn blind.
        if (!world.coastlineReady) return refuse(RouteReason.WORLD_NOT_READY)
        if (AppConfig.routeAvoidDepthGateEnabled && !world.depthReady) {
            return refuse(RouteReason.WORLD_NOT_READY)
        }
        val from = repair(origin, world) ?: return refuse(RouteReason.CANNOT_REPAIR)
        val to = repair(destination, world) ?: return refuse(RouteReason.CANNOT_REPAIR)
        repairedOrigin = from
        repairedDestination = to
        // A fresh arm builds a fresh grid and a fresh ranking: the ladder's three rungs share the one
        // grid built on the first lookup to reach it, and their settled costs are folded afresh. The
        // holder is published with one write, so the arm's rungs single-flight on its own lock. The
        // rungs are the three fixed aversions of [RoutePreference] — around, best and fast.
        ladderHolder = LadderGridHolder()
        landedRungs = mutableListOf()
        val computations = listOf(
            Computation(RouteId(++nextComputationId), R.string.route_rung_around, RoutePreference.AROUND),
            Computation(RouteId(++nextComputationId), R.string.route_rung_balanced, RoutePreference.BEST),
            Computation(RouteId(++nextComputationId), R.string.route_rung_through, RoutePreference.FAST)
        )
        declarations = computations.associateBy { it.id }
        stageComputationId = computations.first().id
        return RouteDeclarations.Available(computations.map { RouteComputation(it.id, it.descriptionResId) })
    }

    /**
     * **A refused pair clears the arm (D24).** The world-not-ready and cannot-repair refusals leave no pair
     * to run, so the holder the last arm published and its declarations are dropped together: a later
     * [startLookup] then finds no declaration for its id and returns before a stale holder can run a lookup
     * nobody asked for. A lookup id is still minted, as every declaration path mints one — the caller reads
     * the absent terminal, never a returned id, as the "started" signal.
     */
    private fun refuse(reason: RouteReason): RouteDeclarations.Refused {
        ladderHolder = null
        declarations = emptyMap()
        return RouteDeclarations.Refused(reason)
    }

    override fun startLookup(computationId: RouteId): RouteId {
        val lookupId = RouteId(++nextLookupId)
        // An id the current declarations do not hold — a stale arm's, or one from before a refusal cleared
        // them (D24) — starts nothing: the lookup id is minted, but no job and no stale holder run.
        val computation = declarations[computationId] ?: return lookupId
        // The arm's holder and its repaired pair are captured here, at the moment the lookup is declared, so
        // every rung of the arm runs the arm it was asked for and a later arm's fresh state cannot be
        // retargeted under it.
        val holder = ladderHolder ?: return lookupId
        val from = repairedOrigin ?: return lookupId
        val to = repairedDestination ?: return lookupId
        holder.inFlight.incrementAndGet()
        val job = computeScope.launch { runComputation(lookupId, computation, holder, from, to) }
        // The job is published before its handler is registered, so a lookup that ends in the instant
        // between the two is still removed rather than left as a stale entry — the handler fires at once on
        // an already-completed job.
        jobs[lookupId] = job
        // Disposal: the shared build is not a child of the lookup job, so cancelling the job alone would let
        // it run on unattended. The completion handler removes the job's own entry — a finished lookup must
        // not be remembered — drops the arm's in-flight count and, once the arm's last lookup — success or
        // cancel — has ended, cancels the build the rungs shared. The read runs off the suspending lane and
        // cannot take the holder's lock; the field's `@Volatile` only keeps that read from going stale, and
        // on the shipped path [inFlight]'s atomic already orders the last completer's read (D34).
        job.invokeOnCompletion {
            jobs.remove(lookupId)
            if (holder.inFlight.decrementAndGet() == 0) holder.deferred?.cancel()
        }
        return lookupId
    }

    override fun cancelLookup(id: RouteId) {
        jobs.remove(id)?.cancel()
    }

    /**
     * **One lookup, run on the engine's own lane.** The main computation publishes its stage pair and
     * the settled result; a candidate publishes only its terminal update, the panel's stage line
     * narrating the main's build alone. The [holder] and the [from]/[to] pair are the ones captured when the
     * lookup was declared, so a rung always runs the arm it was asked for.
     *
     * **A failed build is answered, a cancel is not (D36).** A non-cancellation exception inside the arm's
     * one build fails the shared `Deferred`, which every awaiting rung rethrows from `await()`; without a
     * handler no rung would ever reach its terminal, so the mode would wait out its own timeout rather than
     * being told. The lookup therefore catches such a failure and answers the same [RouteReason.NO_PATH]
     * surface an unanswered search shows — the failure is deliberately **conflated** with a search that found
     * nothing, because this fix introduces no new reason and no new user-visible text. A
     * [CancellationException] is rethrown untouched: the seam says no update may arrive after a cancel, so an
     * abandoned lookup stays silent.
     *
     * **The catch holds the build, the search and the fold alone (D39).** The failure handler used to enclose
     * the success path's own terminal emit, so a sink throwing on a **healthy** path re-entered it and
     * emitted a second, spurious `NO_PATH` terminal before the failure was swallowed. The success terminal
     * now sits outside the handler by construction: the handler wraps the three steps that can fail — the
     * shared build, the rung's search and the fold — and the terminal is emitted once, after it. A sink that
     * throws on the healthy terminal therefore propagates one failure and emits one terminal, which
     * [`aThrowingSinkOnAHealthyTerminalEmitsOneTerminalAndPropagatesOneFailure`] in `RouteAvoidEngineTest`
     * pins.
     */
    private suspend fun runComputation(
        lookupId: RouteId,
        computation: Computation,
        holder: LadderGridHolder,
        from: RoutePoint,
        to: RoutePoint
    ) {
        val world = worldProvider()
        passIndex = 0
        // Only the first rung narrates the panel's stage line; the others publish their terminal answer
        // alone, so the stage context is the narrator's alone and a concurrent rung never writes into it.
        val narrates = computation.id == stageComputationId
        if (narrates) {
            mainLookupId = lookupId
            lastStage = null
        }
        // The outer `try` exists only to clear the narrator's fields however the lookup ends — **after**
        // the terminal emit below, which reads `lastStage` as its own `stageDone`.
        try {
            var rung: Rung? = null
            var best: RouteRunningBest? = null
            // The build, the search and the fold — and only these — sit inside the catch.
            try {
                val grid = sharedGrid(holder, world, from, to).await()
                rung = grid?.let {
                    searchRung(it, computation.lambda, publishStage = narrates, lookupId = lookupId)
                }
                // The fold and the read are one critical step: the rungs land concurrently, so a running
                // best reported beside a rival's landing must see it whole or not at all.
                val landed = rung
                best = rankingLock.withLock {
                    if (landed != null) landedRungs += LandedRung(lookupId, computation.rungIndex, landed.cost)
                    runningBest()
                }
            } catch (cancelled: CancellationException) {
                // A cancel must stay silent — the seam says no update may arrive after one — so it is
                // rethrown rather than answered. The handler is ordered before the general one below because
                // a CancellationException is itself an Exception.
                throw cancelled
            } catch (failure: Exception) {
                // D36's answer, and only that — the reason and the conflation are stated once, in this
                // method's KDoc above. No second trace: `emitTerminal`'s own `DONE` line is the record, so a
                // broken sink cannot swallow the answer by throwing here a second time.
                emitTerminal(lookupId, null, RouteReason.NO_PATH)
                return
            }
            // The success path's terminal, emitted **outside** the catch.
            emitTerminal(
                lookupId,
                rung?.result,
                if (rung == null) RouteReason.NO_PATH else null,
                best
            )
        } finally {
            if (narrates) {
                mainLookupId = null
                lastStage = null
            }
        }
    }

    /**
     * **The ladder's shared grid** — built once per arm and awaited by all three rungs. The first rung
     * to reach the arm's [holder] computes the `Deferred` **inside** the holder's lock and publishes it;
     * the other two find that same `Deferred` under the same lock and await it, so no rung starts a
     * build of its own and the arm costs one rasterise and three A* passes. The build itself is awaited
     * outside the lock, so the rungs single-flight only the *start* of the one build and then run their
     * A* passes concurrently over it. The shared grid keeps the first caller's `world` and `pace`, which
     * the world's own between-solves reload contract makes safe.
     */
    private suspend fun sharedGrid(
        holder: LadderGridHolder,
        world: MultipassWorld,
        from: RoutePoint,
        to: RoutePoint
    ): Deferred<GridContext?> = holder.lock.withLock {
        holder.deferred?.let { return@withLock it }
        // The deferred is published before the build can run (D33): created `LAZY`, assigned to the holder
        // under the lock, and only then started, so no reader — the disposal included — can ever see a
        // build beside a null field. The assignment happens-before `start()`, and `start()` before any
        // body execution, so a build that exists implies a published deferred; the await stays outside the
        // lock, so the rungs single-flight only the start and then run over the one build concurrently.
        val deferred = computeScope.async(start = CoroutineStart.LAZY) {
            gridBuilder.buildGrid(world, from, to, AppConfig.routeAvoidCorridorReachM, paceKn(), markerStrength(), seatTrace)
        }
        holder.deferred = deferred
        deferred.start()
        deferred
    }

    /**
     * **The ladder's running best right now** — the landed rungs folded through the preference's own
     * rule ([RoutePassRanking]), with the budget in force as Best's gate. Called under [rankingLock],
     * so the read is never half a landing; a rung that found no path never enters [landedRungs] and is
     * no candidate, so a set that has all failed ranks nothing.
     */
    private fun runningBest(): RouteRunningBest? {
        if (landedRungs.isEmpty()) return null
        val ranked = landedRungs.map { RoutePassRanking.RungCost(it.ladderIndex, it.cost) }
        val winner = RoutePassRanking.bestRungIndex(
            RoutePreference.of(aversionKn()),
            slowWaterBudgetPct().coerceIn(
                AppConfig.ROUTE_SLOW_WATER_BUDGET_PCT_MIN,
                AppConfig.ROUTE_SLOW_WATER_BUDGET_PCT_MAX
            ),
            ranked
        ) ?: return null
        return RouteRunningBest(landedRungs[winner].lookupId, landedRungs.size)
    }

    /**
     * **One rung's solve** — the fixed-λ pipeline over the shared grid, with the one growth step when
     * the first answer finds no path. The growth escalation alone re-rasterises; a rung's own passes
     * never do.
     */
    private suspend fun searchRung(
        grid: GridContext,
        lambda: Double,
        publishStage: Boolean,
        lookupId: RouteId
    ): Rung? {
        val first = solveAtLambda(grid, lambda, publishStage, lookupId)
        if (first == null) {
            if (grid.regionSaturated) return null
            val grown = gridBuilder.buildGrid(grid.world, grid.from, grid.to, AppConfig.routeAvoidCorridorReachM * 2.0, paceKn(), markerStrength(), seatTrace) ?: return null
            return solveAtLambda(grown, lambda, publishStage, lookupId)
        }
        // A rung that came back with a forced crossing gets one wider corridor to find the way around —
        // the "around" rung's whole job. The wider answer is kept only where it forces fewer crossings.
        if (grid.regionSaturated || first.result.forcedCrossingZoneNames.isEmpty()) return first
        val grown = gridBuilder.buildGrid(grid.world, grid.from, grid.to, AppConfig.routeAvoidCorridorReachM * 2.0, paceKn(), markerStrength(), seatTrace) ?: return first
        val grownRung = solveAtLambda(grown, lambda, publishStage, lookupId) ?: return first
        return if (grownRung.result.forcedCrossingZoneNames.size < first.result.forcedCrossingZoneNames.size) grownRung else first
    }

    /**
     * **One rung's fixed-λ solve** — the A* pass, the taut pull and the corner snap at [lambda], then
     * the fine pass, the forced-crossing probe and the answer. No budget loop: a rung is computed at
     * its own λ and never corrected, so the slow-water budget stays demoted.
     */
    private suspend fun solveAtLambda(
        ctx: GridContext,
        lambda: Double,
        publishStage: Boolean,
        lookupId: RouteId
    ): Rung? {
        val coarseStartNs = System.nanoTime()
        // The plan's own two-layer walk where it answered more than one tile; `avoid`'s plan answers one, so
        // `ctx.windows` is null and the walk is the single grid it has always been.
        val passReading = runner.runPass(
            ctx,
            GridWalk(ctx.grid, ctx.startCell, ctx.aimCell, ctx.cellM, ctx.windows),
            lambda,
            publishStage = publishStage,
            publish = { stage, points, readings, provisional ->
                publish(lookupId, publishStage, stage, points, readings, provisional)
            },
            trace = seatTrace,
            guardZones = true,
            guardBand = true
        )
        val coarseMs = msSince(coarseStartNs)
        val timed = passReading.timed
        if (timed == null) {
            trace {
                "PASS lambda=${fmt(lambda, 2)} NO PATH " +
                    "expansions=${passReading.search.expansions} passable=${passReading.search.passableCells} " +
                    "aimClosed=${passReading.search.aimClosed} " +
                    "aimCell=${ctx.grid.state(ctx.aimCell.row, ctx.aimCell.col)} " +
                    "aimLimit=${fmt(ctx.grid.zoneLimitKn(ctx.aimCell.row, ctx.aimCell.col))}kn"
            }
            return null
        }
        val waypoints = passReading.line
        // The fine stage is the refinement along the settled line alone — the crossings and the
        // re-tension — timed as one `fineMs`; it is a boundary of its own (FINE), since the coarse pass
        // closed at SNAP and the word must name the refinement rather than a pull already done.
        if (publishStage) publish(lookupId, publishStage, RouteStage.FINE)
        val fineStartNs = System.nanoTime()
        val refined = finePass.finePass(ctx, waypoints, lambda, seatTrace)
        val fineMs = msSince(fineStartNs)

        // **The device reading** — the coarse walk's own A* cost and duration, with the fine stage's
        // duration beside them. Always offered to `trace()`: a sink takes it where one stands, and the
        // device's own `Log.i` path is gated inside `trace()` by the tag's level, so the guard is not
        // repeated here — the sink must see every reading, and the device behaviour is unmoved.
        instrumentCoarseWalk(ctx, lambda, waypoints, passReading, coarseMs, fineMs)
        // Two post-passes over the settled search line: the corner pass rounds each snapped corner into
        // an outward-bulging curve — clear by construction, slowed where the bulge would foul — then the
        // speed pass smooths the profile with anticipation and comfortable acceleration. The enforced
        // limit stays the hard ceiling throughout.
        val rounded = RouteCornerPass.round(
            refined, ctx.pace, ctx.world, ctx.depthGateActive, ctx.minDepthM, ctx.marginM
        )
        val timedLine = timeLineWithProfile(
            rounded.points, ctx.pace, ctx.limitAt, rounded.ceilingKnAt,
            clockSampleM(ctx.cellM, ctx.fineCellM)
        )
        val finalShares = slowShares(timedLine, ctx.pace, inZone = inZone(ctx.zones), inBand = inBand(ctx.world))
        // The report's own reading: the seconds each limit's own water slowed, the band apart.
        val slowLimits = slowTimeByLimit(
            timedLine, ctx.pace, ctx.limitAt, inZone(ctx.zones), inBand(ctx.world),
            AppConfig.routeAvoidZone300LimitKn
        )
        val forced = forcedCrossingNames(
            ctx.grid, ctx.zones, ctx.priced, ctx.cellM, ctx.pace, lambda, ctx.from, ctx.to,
            ctx.startCell, ctx.aimCell, refined
        )
        val bandLawM = bandMetres(ctx.world, timedLine.points)
        val bandPricedM = bandPricedMetres(ctx.world, timedLine.points)
        val slowM = slowMetres(timedLine, ctx.pace)
        trace {
            "LINE distance=${fmt(lineLengthM(timedLine.points))}m duration=${fmt(timedLine.durationSec)}s " +
                "legs=${timedLine.legTimesSec.size} " +
                "step=${fmt(clockSampleM(ctx.cellM, ctx.fineCellM))}m " +
                "bandMetres=${fmt(bandLawM)}m bandPricedMetres=${fmt(bandPricedM)}m " +
                "slowMetres=${fmt(slowM)}m slowShare=${fmt(zoneSlowShare(timedLine, ctx.pace), 2)} " +
                "zoneShare=${fmt(finalShares.zone, 2)} bandShare=${fmt(finalShares.band, 2)} " +
                "rampShare=${fmt(finalShares.ramp, 2)} " +
                "forced=[${forced.joinToString(", ")}]"
        }
        // The rung's own cost, exactly the figures the ranking reads: the zone share the answer already
        // carries, the metres standing inside a priced zone's interior, and the settled clock.
        val cost = RoutePassRanking.PassCost(
            zoneShare = finalShares.zone,
            zoneMetresM = zoneMetres(ctx.zones, timedLine.points),
            durationSec = timedLine.durationSec
        )
        return Rung(success(timedLine, forced, null, slowLimitSeconds = slowLimits), cost)
    }

    /**
     * **The invalid-end repair** — an 8-direction ring sweep outward from [point], tested with the
     * engine's own water test (on the coastline and deep enough for the gate), growing to
     * `route.repair.maxRadiusM`. The first valid point wins; because it is the nearest, it is on the
     * sea side by construction. `null` where no valid water exists within the sweep's radius — the
     * caller answers [RouteReason.CANNOT_REPAIR].
     *
     * **Budget: under 50 ms for one point** — the sweep is at most 8 directions ×
     * `maxRadiusM / stepM` rings, each a pair of layer reads, which is ~72 water tests at the shipped
     * 200 m radius; the step's own test proves the ceiling.
     */
    private fun repair(point: RoutePoint, world: MultipassWorld): RoutePoint? {
        if (validWater(point, world)) return point
        val maxRadiusM = AppConfig.routeRepairMaxRadiusM
        var ring = 1
        while (ring * REPAIR_STEP_M <= maxRadiusM) {
            val radiusM = ring * REPAIR_STEP_M
            for (direction in 0 until REPAIR_DIRECTIONS) {
                val bearing = direction * (360.0 / REPAIR_DIRECTIONS)
                val moved = SpatialOperations.pointAlongBearing(point.latitude, point.longitude, bearing, radiusM)
                val candidate = RoutePoint(moved.latitude, moved.longitude)
                if (validWater(candidate, world)) return candidate
            }
            ring++
        }
        return null
    }

    /**
     * The engine's own water test: on the coastline, deep enough where the gate is armed, and **outside
     * every wall marker** — a Blocked marker is law, so an end standing inside one is moved sea-side by
     * [repair] exactly as a land or shallow end is.
     */
    private fun validWater(point: RoutePoint, world: MultipassWorld): Boolean {
        if (!world.isWater(point.latitude, point.longitude)) return false
        val at = LatLng(point.latitude, point.longitude)
        if (world.routeMarkers().any { it.isWall && it.geometry.contains(at) }) return false
        val gateActive = AppConfig.routeAvoidDepthGateEnabled && world.depthReady
        val minDepthM = AppConfig.routeAvoidDepthGateMinM
        return depthClearsGate(world.depthAt(point.latitude, point.longitude), gateActive, minDepthM)
    }


    /**
     * One boundary's emission. The **narrating** lookup publishes its whole stage pair — the stage just
     * finished, the one about to run, the line and the readings — and only it moves [lastStage], so a
     * concurrent candidate can never corrupt the main's chain. A **candidate** narrates nothing; only the
     * provisional pair it can already stand behind travels, riding the same boundary update the main's
     * does: the SNAP that follows the taut pull, where the pulled line's figures are honest and the row
     * stops waiting on the fine pass. The stage word stays the main's alone.
     */
    private fun publish(
        lookupId: RouteId,
        narrates: Boolean,
        stage: RouteStage?,
        points: List<RoutePoint>? = null,
        readings: List<RouteStepReading> = emptyList(),
        provisional: RouteProvisional? = null
    ) {
        if (!narrates) {
            if (stage != RouteStage.SNAP || provisional == null) return
            _updates.tryEmit(
                RouteUpdate(
                    routeId = lookupId,
                    stageDone = RouteStage.PULL,
                    nextStage = RouteStage.SNAP,
                    line = points ?: emptyList(),
                    result = null,
                    reason = null,
                    provisional = provisional
                )
            )
            return
        }
        val id = mainLookupId ?: return
        if (stage == null) return
        val done = lastStage
        lastStage = stage
        _updates.tryEmit(
            RouteUpdate(
                routeId = id,
                stageDone = done,
                nextStage = stage,
                line = points ?: emptyList(),
                result = null,
                reason = null,
                readings = readings,
                provisional = provisional
            )
        )
        trace {
            "STAGE done=${done?.name ?: "none"} next=${stage.name}" +
                if (points != null) " points=${points.size}" else ""
        }
    }

    /** The terminal update: the last stage finished, `nextStage` null, the result or the reason. */
    private fun emitTerminal(
        lookupId: RouteId,
        result: RouteResult.Success?,
        reason: RouteReason?,
        runningBest: RouteRunningBest? = null
    ) {
        _updates.tryEmit(
            RouteUpdate(
                routeId = lookupId,
                stageDone = lastStage,
                nextStage = null,
                line = result?.points ?: emptyList(),
                result = result,
                reason = reason,
                runningBest = runningBest
            )
        )
        trace {
            "DONE stage=${lastStage?.name ?: "none"} result=${result != null} reason=${reason?.name ?: "none"}"
        }
    }


    /**
     * Whether the route was forced through a priced zone: with every restrictive zone's interior
     * blocked the corridor has no avoiding path, and the names reported are the restrictive zones the
     * drawn line enters. An ordinary priced crossing — a way around exists — reports nothing.
     */
    private suspend fun forcedCrossingNames(
        grid: MultipassGrid,
        zones: List<SpeedZone>,
        priced: List<ZoneRing>,
        cellM: Double,
        pace: Double,
        lambda: Double,
        from: RoutePoint,
        to: RoutePoint,
        startCell: CellIndex,
        aimCell: CellIndex,
        waypoints: List<LatLng>
    ): List<String> {
        val restrictive = priced.filter { zonePriceSec(cellM, pace, it.limitKn, lambda) > 0.0 }
        if (restrictive.isEmpty()) {
            trace { "PROBE restrictive=[] avoiding=n/a expansions=0 forced=[]" }
            return emptyList()
        }
        val blocked = grid.blockedCopy(pace)
        blocked.forceFree(from.latitude, from.longitude)
        blocked.forceFree(to.latitude, to.longitude)
        val probe = MultipassSearch.search(blocked, startCell, aimCell, Units.knotsToMps(pace))
        val avoiding = probe.path != null
        val restrictiveZones = zones.zip(priced)
            .filter { (_, p) -> zonePriceSec(cellM, pace, p.limitKn, lambda) > 0.0 }
            .map { (z, _) -> z }
        val names = forcedCrossingZoneNames(waypoints, restrictiveZones, avoiding)
        trace {
            "PROBE restrictive=[${restrictiveZones.joinToString(", ") { it.name }}] " +
                "avoiding=$avoiding expansions=${probe.expansions} " +
                "passable=${probe.passableCells} aimClosed=${probe.aimClosed} " +
                "forced=[${names.joinToString(", ")}]"
        }
        return names
    }

    /**
     * The answer: the line the loop settled, **already timed** under the limits in force — one clock
     * read serves the leg times, the per-leg speeds, the duration and the budget's own share, so none
     * of them can disagree — plus the budget's verdict, set only where the **zone share** was missed.
     */
    private fun success(
        timed: TimedLine,
        forcedCrossingZoneNames: List<String>,
        budgetUnmetZoneShare: Double?,
        distanceM: Double = lineLengthM(timed.points),
        durationSec: Double = timed.durationSec,
        slowLimitSeconds: List<RouteSlowLimit> = emptyList()
    ): RouteResult.Success = RouteResult.Success(
        points = timed.points.map { RoutePoint.of(it) },
        legTimesSec = timed.legTimesSec,
        legSpeedsMps = timed.legSpeedsMps,
        distanceM = distanceM,
        durationSec = durationSec,
        // The emitted polyline ends at the raw aim, never a resolved node: the pin stands where the
        // user dragged, and the snapped cell is only the search's anchor.
        destinationMoved = false,
        budgetUnmetZoneShare = budgetUnmetZoneShare,
        forcedCrossingZoneNames = forcedCrossingZoneNames,
        slowLimitSeconds = slowLimitSeconds
    )

    /** The polyline's own length in metres — the shared haversine sum, read by the answer and every candidate alike. */
    private fun lineLengthM(points: List<LatLng>): Double = polylineLengthM(points)







    // ── The instrument ─────────────────────────────────────────────────────────

    /**
     * **Emits one line where the instrument is listening** — the injected sink where one stands, and the
     * device's own `Log.i` channel under the tag's level otherwise; [message] is built only when a line
     * is actually emitted.
     */
    private inline fun trace(message: () -> String) {
        val sink = traceSink
        if (sink != null) sink(message()) else if (logEnabled) Log.i(TAG, message())
    }

    /**
     * **The device reading** — emitted only where the `MaroRoute` tag's own level is on.
     *
     * One line per **rung**: `lambda` tells them apart, and `paceKn` tells two runs at different cruise
     * speeds apart. `DEVICE PASS` is the coarse walk's own cost — the cells it was rasterized over, how
     * many were passable, how many the A\* expanded, how many cells its answer holds and how long it
     * took, the pull's own refusals — how many chords the land margin refused and how many the price
     * guard — with the fine stage's own duration beside it, `coarseMs` and `fineMs` one pair.
     */
    private fun instrumentCoarseWalk(
        ctx: GridContext,
        lambda: Double,
        coarse: List<LatLng>,
        pass: PassReading,
        coarseMs: Double,
        fineMs: Double
    ) {
        val search = pass.search
        // (e) A coarse cell is twenty-five fine ones, so the walk's own counts name their layer: the interior's
        // cells are the grid's, the passable count is the walk's unique lattice cells across its layers.
        trace {
            "DEVICE PASS lambda=${fmt(lambda, 2)} paceKn=${fmt(ctx.pace)} plan=${planName()} " +
                "cellM=${fmt(ctx.cellM)}m fineCellM=${fmt(ctx.fineCellM)}m " +
                "layers=${ctx.windows?.layerCount ?: 1} " +
                "cellsInterior=${ctx.grid.rows * ctx.grid.cols} passableUnique=${search.passableCells} " +
                "expansions=${search.expansions} pathCells=${search.path?.size ?: 0} " +
                "pulled=${pass.pulledCount} snapped=${pass.snappedCount} " +
                "landRefusals=${ctx.refusals.land} priceRefusals=${ctx.refusals.price} " +
                "coarseM=${fmt(lineLengthM(coarse))}m " +
                "coarseMs=${fmt(coarseMs)} fineMs=${fmt(fineMs)}"
        }
    }

    /** The plan this engine walks by, named as the log prints it — the plan's own name, never special-cased. */
    private fun planName(): String = plan.name

    /**
     * The metres of a line whose own middle stands inside the band's own **width** — the law's water,
     * whose limit the clock pays, read through the very test the price and the clock make
     * ([insideBandWidthM]). It is the law's water alone; the priced reach beside it is
     * [bandPricedMetres], so one figure never stands for both.
     */
    private fun bandMetres(world: MultipassWorld, points: List<LatLng>): Double {
        if (world.bandWidthM <= 0.0) return 0.0
        val bandM = world.bandWidthM
        var total = 0.0
        for (i in 0 until points.size - 1) {
            val mid = LatLng(
                (points[i].latitude + points[i + 1].latitude) / 2.0,
                (points[i].longitude + points[i + 1].longitude) / 2.0
            )
            if (insideBandWidthM(world.distanceToCoastM(mid.latitude, mid.longitude), bandM)) {
                total += SpatialOperations.haversine(points[i], points[i + 1])
            }
        }
        return total
    }

    /**
     * The metres of a line whose own middle stands within the band's **priced reach** — the width and its
     * outside margin together, the water the band's law prices at some fraction. Reported beside
     * [bandMetres]'s bare law water so the priced strip and the limit's water are told apart.
     */
    private fun bandPricedMetres(world: MultipassWorld, points: List<LatLng>): Double {
        if (world.bandWidthM <= 0.0) return 0.0
        val reachM = bandReachM(world.bandWidthM, AppConfig.routeAvoidZone300OutsideMarginM)
        var total = 0.0
        for (i in 0 until points.size - 1) {
            val mid = LatLng(
                (points[i].latitude + points[i + 1].latitude) / 2.0,
                (points[i].longitude + points[i + 1].longitude) / 2.0
            )
            if (world.distanceToCoastM(mid.latitude, mid.longitude) <= reachM) {
                total += SpatialOperations.haversine(points[i], points[i + 1])
            }
        }
        return total
    }

    /** The metres of a timed line it runs slower than the pace over — the budget's own quantity, in metres. */
    private fun slowMetres(timed: TimedLine, paceKn: Double): Double {
        val paceMps = Units.knotsToMps(paceKn)
        var total = 0.0
        for (i in timed.legTimesSec.indices) {
            val dist = SpatialOperations.haversine(timed.points[i], timed.points[i + 1])
            if (timed.legTimesSec[i] - dist / paceMps > 0.0) total += dist
        }
        return total
    }
}

/** One declared computation — a ladder rung: its id, its description and the stop it solves at. */
private data class Computation(
    val id: RouteId,
    val descriptionResId: Int,
    val preference: RoutePreference
) {
    /** The λ this rung is solved at — the stop's own fixed value. */
    val lambda: Double get() = preference.lambda

    /** This rung's ladder index — where a total tie falls back to. */
    val rungIndex: Int get() = preference.index
}

/** One landed rung this arm: the lookup that owns its line, its ladder index and its own cost. */
private data class LandedRung(
    val lookupId: RouteId,
    val ladderIndex: Int,
    val cost: RoutePassRanking.PassCost
)

/** One rung's settled answer and the cost the ranking reads it by. */
private data class Rung(
    val result: RouteResult.Success,
    val cost: RoutePassRanking.PassCost
)

/**
 * **One arm's shared-grid single-flight** — the [Mutex] guarding the build and the [Deferred] the first
 * rung publishes. The ladder's three rungs share one holder per arm: the first to enter [lock] starts
 * the rasterise and stores [deferred]; the rest find it under the same lock and await that one build,
 * so no rung ever starts a build of its own. [inFlight] counts the arm's live lookups so the shared build
 * is disposed once the last of them ends rather than outliving its rungs.
 */
private class LadderGridHolder {
    /** Guards the check-build-publish, so exactly one rung starts the arm's rasterise. */
    val lock = Mutex()

    /**
     * The one build this arm shares — written once, under [lock]. `@Volatile` only so a reader off the
     * suspending lane that cannot take [lock] sees the latest write rather than a stale one; it orders
     * nothing on the shipped path, where [inFlight]'s atomic already orders the last completer's read, so
     * it removes staleness for a non-last reader and no more (D34).
     */
    @Volatile
    var deferred: Deferred<GridContext?>? = null

    /**
     * The arm's lookups still in flight — incremented as each is launched and decremented by that job's own
     * completion, so the shared build is cancelled exactly when the arm's last lookup has ended and never
     * while a sibling still awaits it.
     */
    val inFlight = AtomicInteger(0)
}


/** The repair's ring step (m) — a constant of the algorithm, not a key. */
private const val REPAIR_STEP_M = 25.0

/** The repair's directions per ring — the 8 compass bearings. */
private const val REPAIR_DIRECTIONS = 8

/**
 * The route mode's own log tag — the channel the refusals already ride, and the one `adb logcat -s`
 * filters on for a device pass. The instrument's level is the device's own setting:
 * `adb shell setprop log.tag.MaroRoute INFO`.
 */
private const val TAG = "MaroRoute"
