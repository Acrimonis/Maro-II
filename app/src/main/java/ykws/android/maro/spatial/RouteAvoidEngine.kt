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
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.spatial.multipass.RouteMarker
import ykws.android.maro.data.model.RouteResult
import ykws.android.maro.data.model.RouteSlowLimit
import ykws.android.maro.data.regulation.SpeedZone
import ykws.android.maro.spatial.multipass.MultipassGrid
import ykws.android.maro.spatial.multipass.MultipassSearch
import ykws.android.maro.spatial.multipass.MultipassWorld
import ykws.android.maro.spatial.multipass.CellIndex
import ykws.android.maro.spatial.multipass.GateRule
import ykws.android.maro.spatial.multipass.gateRuleOf
import ykws.android.maro.spatial.multipass.StepGuard
import ykws.android.maro.spatial.multipass.GridContext
import ykws.android.maro.spatial.multipass.GridWalk
import ykws.android.maro.spatial.multipass.PassReading
import ykws.android.maro.spatial.multipass.RouteCornerPass
import ykws.android.maro.spatial.multipass.RouteCornerPass.CornerOutcome
import ykws.android.maro.spatial.multipass.RouteCornerPass.CornerVerdict
import ykws.android.maro.spatial.multipass.RouteCostField
import ykws.android.maro.spatial.multipass.RouteDepthReading
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
import ykws.android.maro.spatial.multipass.crossedSlowZoneNames
import ykws.android.maro.spatial.multipass.depthClearsGate
import ykws.android.maro.spatial.multipass.fmt
import ykws.android.maro.spatial.multipass.forcedCrossingZoneNames
import ykws.android.maro.spatial.multipass.inBand
import ykws.android.maro.spatial.multipass.inZone
import ykws.android.maro.spatial.multipass.insideBandWidthM
import ykws.android.maro.spatial.multipass.msSince
import ykws.android.maro.spatial.multipass.slowShares
import ykws.android.maro.spatial.multipass.slowTimeByLimit
import ykws.android.maro.spatial.multipass.slowWaterSeverity
import ykws.android.maro.spatial.multipass.timeLineWithLimits
import ykws.android.maro.spatial.multipass.timeLineWithProfile
import ykws.android.maro.spatial.multipass.polylineLengthM
import ykws.android.maro.spatial.multipass.rampSlowSeconds
import ykws.android.maro.spatial.multipass.zonePriceSec
import ykws.android.maro.spatial.multipass.zoneSlowShare

/**
 * **The avoid engine: avoid land, islands and hazard rings, and keep off water shallower than the
 * depth gate.**
 *
 * It answers [RouteEngine.routesToCompute] with the ladder's three fixed-aversion rungs — around,
 * balanced and through slow water — after **repairing** the two ends: an end is held clear of the coast
 * and of the measured 3 m line, moved to the nearest clear water where it stands nearer (see *The repair*
 * below). A pair that cannot be repaired is refused with [RouteReason.CANNOT_REPAIR], and one whose
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
 * expansion and the band's price **follows the solving rung's λ exactly as a ring's does**, the strictest
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
 * **The repair.** The first step of [routesToCompute]: each end is held **clear** — at
 * `route.repair.clearanceM` from the coast and from the measured 3 m line — and an end standing nearer is
 * moved to the nearest clear point within twice that, found by 5 m rings of 16 bearings refined along
 * each bearing, never across land or a shoal. Where none stands that close, the nearest point clear of the
 * gate's wall within `route.repair.maxRadiusM` is taken, and failing that the old rule: an end on land or
 * shallower than the gate moved by an 8-direction ring sweep at a 25 m step to the first water. So an end
 * the old rule accepted is never refused. The budget is the acceptance: under 50 ms for one point, proven by
 * the step's own test on a cold raster world.
 *
 * **What the answer means.** The emitted polyline starts at the repaired start and ends at the repaired
 * aim, and the answer says when the aim moved (`destinationMoved`); the snapped cells are
 * only the search's anchor. An exhausted search answers [RouteReason.NO_PATH] on the update flow — retried once with the
 * corridor reach doubled. Each leg is timed at the pace in force, asked fresh per answer.
 *
 * **The ladder.** Each rung is a full solve at its own fixed λ, over one grid the three share: the grid
 * is built once and stores the band's and the rings' **limits**, so a rung's A* pass prices every slow
 * cell at its own aversion without a second rasterise. The forced-crossing growth and the fine pass (§5)
 * still run — the wider corridor kept only when it forces fewer crossings, and the fine pass on the
 * settled line where a restrictive zone the coarse grid could not see around gets a local A* at
 * `route.avoid.grid.fineCellM`. There is no budget loop and no budget: a rung is computed at its own λ
 * and never corrected.
 *
 * **The ranking.** The engine folds its own rungs: at every rung's terminal the settled costs of the
 * rungs that have landed are ranked through [RoutePassRanking] under the one sum that decides the crown —
 * `trip time + weight × slow-water seconds` — and that one fold rides the terminal as both the crown's
 * **running best** ([RouteRunningBest]) and the settled [RouteVerdict], the crown and the whole landed
 * order read off one object so the two can never name two different winners. The two inputs it reads are
 * the engine's own, the **weight the verdict's dial stands at** (asked through [verdictWeight], `null` on
 * its *Ignore time* stop) and [AppConfig.routeAvoidLadderTieToleranceSec]; the answer carries no
 * instrumentation vocabulary, which is why the ranking lives here and never on an emitted line.
 *
 * Coroutines and `Flow` only: no thread of its own, and the search checks the calling job between its
 * expansions so a flung map never queues behind a computation nobody wants any more.
 */
class RouteAvoidEngine(
    /** The pace in force (kn), asked fresh on every answer so a slider move reaches the next line. */
    private val paceKn: () -> Double,
    /**
     * **The verdict's own weight** — the dial's stop read as the number the crown weighs by, `null` on
     * its *Ignore time* stop, which carries no weight at all. Asked fresh like the pace, so a dial move
     * reaches the next rung that lands. It never prices the search: the three rungs carry their own
     * fixed λ ([RoutePreference]), so the weight weighs the lines already drawn and bends no cell of any
     * of them.
     */
    private val verdictWeight: () -> Double?,
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

    /** The grid builder — the corridor, the harvest, the one rasterize and the ends' discs. */
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

    /** The pass the instrument is on, reset at each solve so `STAGE` can carry its index. */
    private var passIndex = 0

    /**
     * **The stage each running lookup last published**, by lookup id, so a boundary's finished-then-next
     * pair can be completed. Every rung narrates its own stages (each row prints its own), and the rungs
     * run concurrently, so the chain is one entry per lookup in a concurrent map: a rung writes its own
     * key alone, and an entry is dropped when its lookup ends.
     */
    private val lastStages = ConcurrentHashMap<RouteId, RouteStage>()

    /** The instrument's **one gate** — [RouteTrace]'s, shared with the route mode's view model. */
    private val logEnabled: Boolean get() = RouteTrace.enabled

    override fun routesToCompute(origin: RoutePoint, destination: RoutePoint): RouteDeclarations {
        val world = worldProvider()
        // The repair judges by the coastline and the depth gate; without either layer a search would
        // draw a line over what it cannot see, so the pair is refused by name rather than drawn blind.
        if (!world.coastlineReady) return refuse(RouteReason.WORLD_NOT_READY)
        if (AppConfig.routeAvoidDepthGateEnabled && !world.depthReady) {
            return refuse(RouteReason.WORLD_NOT_READY)
        }
        val gateActive = AppConfig.routeAvoidDepthGateEnabled && world.depthReady
        val gate = gateRuleOf(world, plan, gateActive, AppConfig.routeAvoidDepthGateMinM)
        val from = repair(origin, world, gate) ?: return refuse(RouteReason.CANNOT_REPAIR)
        val to = repair(destination, world, gate) ?: return refuse(RouteReason.CANNOT_REPAIR)
        trace { repairLine("start", origin, from, world, gate) }
        trace { repairLine("aim", destination, to, world, gate) }
        repairedOrigin = from.point
        repairedDestination = to.point
        // A fresh arm builds a fresh grid and a fresh ranking: the ladder's three rungs share the one
        // grid built on the first lookup to reach it, and their settled costs are folded afresh. The
        // holder is published with one write, so the arm's rungs single-flight on its own lock. The
        // rungs are the three fixed aversions of [RoutePreference] — around, best and fast.
        ladderHolder = LadderGridHolder(destinationMoved = to.moved)
        landedRungs = mutableListOf()
        val computations = listOf(
            Computation(RouteId(++nextComputationId), R.string.route_rung_around, RoutePreference.AROUND),
            Computation(RouteId(++nextComputationId), R.string.route_rung_balanced, RoutePreference.BEST),
            Computation(RouteId(++nextComputationId), R.string.route_rung_through, RoutePreference.FAST)
        )
        declarations = computations.associateBy { it.id }
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
     * **One lookup, run on the engine's own lane.** Every rung publishes its own stage pairs and its
     * settled result, each row printing the stage of the rung it describes. The [holder] and the [from]/[to] pair are the ones captured when the
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
        // Every rung narrates its own stages: the row prints the word of the rung it describes, and the
        // chain is kept per lookup.
        // The outer `try` exists only to clear this lookup's chain however it ends — **after** the
        // terminal emit below, which reads it as its own `stageDone`.
        try {
            var rung: Rung? = null
            var verdict: RouteVerdict? = null
            // The build, the search and the fold — and only these — sit inside the catch.
            try {
                // The rung waits on the arm's one shared grid first, so its row names that wait.
                publish(lookupId, RouteStage.GRID)
                val grid = sharedGrid(holder, world, from, to).await()
                rung = grid?.let {
                    searchRung(it, computation.lambda, lookupId = lookupId)
                }
                // The fold is one critical step: the rungs land concurrently, so a verdict reported beside
                // a rival's landing must see that landing whole or not at all.
                val landed = rung
                verdict = rankingLock.withLock {
                    if (landed != null) landedRungs += LandedRung(lookupId, landed.cost)
                    settledVerdict()
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
            // The success path's terminal, emitted **outside** the catch. The one fold rides it twice — as
            // the running best and as the settled verdict — so the two can never name two different crowns.
            emitTerminal(
                lookupId,
                rung?.result?.copy(destinationMoved = holder.destinationMoved),
                if (rung == null) RouteReason.NO_PATH else null,
                verdict?.best,
                verdict
            )
        } finally {
            lastStages.remove(lookupId)
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
     * **The settled set's verdict right now** — the landed rungs folded **once** through the ranking's own
     * seat ([RoutePassRanking]): [RoutePassRanking.bestRungIndex] names the crown and
     * [RoutePassRanking.ranks] every landed rung's place, both under the one sum and the one tolerance, and
     * the two are carried on the one [RouteVerdict] returned — so the crown and the order are read off one
     * object and can never disagree. Called under [rankingLock], so the read is never half a landing; a rung
     * that found no path never enters [landedRungs] and is no candidate, so a set that has all failed ranks
     * nothing.
     *
     * The two inputs are the engine's own, exactly as the running best read them: the **weight the verdict's
     * dial stands at** (asked through [verdictWeight], `null` on its *Ignore time* stop) and
     * [AppConfig.routeAvoidLadderTieToleranceSec].
     */
    private fun settledVerdict(): RouteVerdict? {
        if (landedRungs.isEmpty()) return null
        val weight = verdictWeight()
        val toleranceSec = AppConfig.routeAvoidLadderTieToleranceSec.coerceIn(
            AppConfig.ROUTE_LADDER_TIE_TOLERANCE_SEC_MIN,
            AppConfig.ROUTE_LADDER_TIE_TOLERANCE_SEC_MAX
        )
        val costs = landedRungs.map { it.cost }
        val winner = RoutePassRanking.bestRungIndex(weight, toleranceSec, costs) ?: return null
        val ranks = RoutePassRanking.ranks(weight, toleranceSec, costs)
        // The order is read off the very ranks the fold just wrote: ascending, crown first, one rank each.
        val order = landedRungs.indices
            .sortedBy { ranks[it] }
            .map { i -> RouteRankedRung(landedRungs[i].lookupId, ranks[i]) }
        return RouteVerdict(
            best = RouteRunningBest(landedRungs[winner].lookupId, landedRungs.size),
            order = order
        )
    }

    /**
     * **One rung's solve** — the fixed-λ pipeline over the shared grid, with the one growth step when
     * the first answer finds no path. The growth escalation alone re-rasterises; a rung's own passes
     * never do.
     */
    private suspend fun searchRung(
        grid: GridContext,
        lambda: Double,
        lookupId: RouteId
    ): Rung? {
        val first = solveAtLambda(grid, lambda, lookupId)
        if (first == null) {
            if (grid.regionSaturated) return null
            val grown = gridBuilder.buildGrid(grid.world, grid.from, grid.to, AppConfig.routeAvoidCorridorReachM * 2.0, paceKn(), markerStrength(), seatTrace) ?: return null
            return solveAtLambda(grown, lambda, lookupId)
        }
        // A rung that came back with a forced crossing gets one wider corridor to find the way around —
        // the "around" rung's whole job. The wider answer is kept only where it forces fewer crossings.
        if (grid.regionSaturated || first.result.forcedCrossingZoneNames.isEmpty()) return first
        val grown = gridBuilder.buildGrid(grid.world, grid.from, grid.to, AppConfig.routeAvoidCorridorReachM * 2.0, paceKn(), markerStrength(), seatTrace) ?: return first
        val grownRung = solveAtLambda(grown, lambda, lookupId) ?: return first
        return if (grownRung.result.forcedCrossingZoneNames.size < first.result.forcedCrossingZoneNames.size) grownRung else first
    }

    /**
     * **The corner tally** — one `CORNERS` summary and one `CORNER` detail naming what the pass did at a
     * bend, so a device run answers instead of leaving it to be read off the drawn line. Report only: the
     * drawn points and the clock come from the pass either way, and nothing here decides anything.
     *
     * `ask` is the reach the policy wanted, `emitted` what the previous bow had already spent of it, and
     * `shared` the share of the leg another bend on that leg leaves it, so a bend short of room shows
     * **which** of the three bound it, while `why` names the wall where a bow fouled — coast, depth or a
     * speed zone the corner itself stands outside.
     *
     * **Two readings say where a slow stretch comes from, and neither is the pass's own.** `limit` is the
     * speed limit in force at the bend, `-` where the water carries none, and `band` says whether it stands
     * inside the 300 m band — so a corner's own ceiling, a zone's limit and the band's slow water are told
     * apart at a glance. Both are read from the caller's own context: this reads the water, it never prices
     * it.
     *
     * A bend left **standing** always reports, and a **curved** one reports where it carries a real
     * slowdown or takes a `shared` part of its leg; a curve taken at the pace with no share is the line
     * behaving and has nothing to say.
     */
    private fun traceCornerVerdicts(
        verdicts: List<CornerVerdict>,
        paceKn: Double,
        limitAt: (LatLng) -> Double?,
        inBand: (LatLng) -> Boolean
    ) {
        if (verdicts.isEmpty()) return
        val tally = verdicts.groupingBy { it.outcome }.eachCount()
            .entries.joinToString(" ") { "${it.key}=${it.value}" }
        val slowed = verdicts.count { it.outcome == CornerOutcome.ROUNDED && it.speedKn < paceKn - 1e-9 }
        trace { "CORNERS total=${verdicts.size} pace=${fmt(paceKn)}kn slowed=$slowed $tally" }
        for (v in verdicts) {
            val curved = v.outcome == CornerOutcome.ROUNDED
            if (curved && v.sharedM == null && v.speedKn >= paceKn - 1e-9) continue
            val limit = limitAt(v.at)
            trace {
                "CORNER idx=${v.index} at=${fmt(v.at.latitude, 5)},${fmt(v.at.longitude, 5)} " +
                    "why=${v.outcome} turn=${fmt(v.turnDeg)}deg in=${fmt(v.legInM)}m out=${fmt(v.legOutM)}m " +
                    "ask=${fmt(v.reachAskM)}m emitted=${fmt(v.emittedInM)}m " +
                    "shared=${v.sharedM?.let { "${fmt(it)}m" } ?: "-"} room=${fmt(v.roomM)}m " +
                    "speed=${fmt(v.speedKn, 2)}kn limit=${limit?.let { fmt(it, 1) } ?: "-"} " +
                    "band=${inBand(v.at)} inZone=${v.cornerInZone}"
            }
        }
    }

    /**
     * **One rung's fixed-λ solve** — the A* pass, the taut pull and the corner snap at [lambda], then
     * the fine pass, the forced-crossing probe and the answer. No budget loop: a rung is computed at
     * its own λ and never corrected.
     */
    private suspend fun solveAtLambda(
        ctx: GridContext,
        lambda: Double,
        lookupId: RouteId
    ): Rung? {
        val coarseStartNs = System.nanoTime()
        // The plan's own two-layer walk where it answered more than one tile; `avoid`'s plan answers one, so
        // `ctx.windows` is null and the walk is the single grid it has always been.
        val passReading = runner.runPass(
            ctx,
            GridWalk(ctx.grid, ctx.startCell, ctx.aimCell, ctx.cellM, ctx.windows),
            lambda,
            publishStage = true,
            publish = { stage, points, readings, provisional ->
                publish(lookupId, stage, points, readings, provisional)
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
                    "startLayer=${passReading.search.startLayer} startOpen=${passReading.search.startOpenSteps} " +
                    "rewalked=${passReading.search.rewalked} " +
                    "aimCell=${ctx.grid.state(ctx.aimCell.row, ctx.aimCell.col)} " +
                    "aimLimit=${fmt(ctx.grid.zoneLimitKn(ctx.aimCell.row, ctx.aimCell.col))}kn"
            }
            return null
        }
        val waypoints = passReading.line
        // The fine stage is the refinement along the settled line alone — the crossings and the
        // re-tension — timed as one `fineMs`; it is a boundary of its own (FINE), since the coarse pass
        // closed at SNAP and the word must name the refinement rather than a pull already done.
        publish(lookupId, RouteStage.FINE)
        val fineStartNs = System.nanoTime()
        val refined = finePass.finePass(ctx, waypoints, lambda, seatTrace)
        val fineMs = msSince(fineStartNs)

        // **The device reading** — the coarse walk's own A* cost and duration, with the fine stage's
        // duration beside them. Always offered to `trace()`: a sink takes it where one stands, and the
        // device's own `Log.i` path is gated inside `trace()` by the tag's level, so the guard is not
        // repeated here — the sink must see every reading, and the device behaviour is unmoved.
        instrumentCoarseWalk(ctx, lambda, waypoints, passReading, coarseMs, fineMs)
        // Two post-passes over the settled search line: the corner pass rounds each snapped corner into
        // a curve that cuts the inside of the turn — clear where it fits, shrunk where the bow would
        // foul, left standing only where even the 1 kn bottom's radius overshoots the room — then the
        // speed pass smooths the profile with anticipation and comfortable acceleration. The enforced
        // limit stays the hard ceiling throughout.
        val rounded = RouteCornerPass.round(refined, ctx.pace, ctx.world, ctx.gate, ctx.marginM)
        // **The corner tally**: what the pass did at each bend, with the speed limit in force there and
        // whether it stands in the 300 m band — the two readings that tell a corner's own speed from the
        // water's.
        traceCornerVerdicts(rounded.verdicts, ctx.pace, ctx.limitAt, inBand(ctx.world))
        val timedLine = timeLineWithProfile(
            rounded.points, ctx.pace, ctx.limitAt, rounded.ceilingKnAt,
            clockSampleM(ctx.cellM, ctx.fineCellM)
        )
        val finalShares = slowShares(timedLine, ctx.pace, inZone = inZone(ctx.zones), inBand = inBand(ctx.world))
        val forced = forcedCrossingNames(
            ctx.grid, ctx.zones, ctx.priced, ctx.cellM, ctx.pace, lambda, ctx.from, ctx.to,
            ctx.startCell, ctx.aimCell, refined, ctx.gate
        )
        // **The other fact** — the slow water the drawn line enters, read from the **water** rather than
        // the price. The zones are the world's own, not the grid's switch-gated set, so a crossing the
        // search was charged nothing for — its `speedRoute` override at or above the pace, or the pricing
        // switch off — is still named where the clock has to pay the limit the chart signs.
        val crossed = crossedSlowZoneNames(refined, ctx.world.speedZonesIn(ctx.box), ctx.pace)
        val bandLawM = bandMetres(ctx.world, timedLine.points)
        val bandPricedM = bandPricedMetres(ctx.world, timedLine.points)
        val slowM = slowMetres(timedLine, ctx.pace)
        trace {
            // **The depth gate's own readings** — how shallow the drawn line gets and how close it comes
            // to the wall's edge, the closest point named, and the body separated from the ends by the
            // gate's own waiver; then the traced line's own readings beside the independent scan. Built
            // inside the sink so a solve with the tag off pays none of it.
            val (lineMinDepthM, lineMinDepthAt) = RouteDepthReading.minDepthAt(ctx.world, timedLine.points)
            val edge = RouteDepthReading.edgeReading(
                ctx.world, timedLine.points, ctx.depthGateActive, ctx.minDepthM,
                ctx.gate?.waiverM ?: ctx.marginM, ctx.gate
            )
            "LINE distance=${fmt(lineLengthM(timedLine.points))}m duration=${fmt(timedLine.durationSec)}s " +
                "legs=${timedLine.legTimesSec.size} " +
                "step=${fmt(clockSampleM(ctx.cellM, ctx.fineCellM))}m " +
                "bandMetres=${fmt(bandLawM)}m bandPricedMetres=${fmt(bandPricedM)}m " +
                "slowMetres=${fmt(slowM)}m slowShare=${fmt(zoneSlowShare(timedLine, ctx.pace), 2)} " +
                "zoneShare=${fmt(finalShares.zone, 2)} bandShare=${fmt(finalShares.band, 2)} " +
                "rampShare=${fmt(finalShares.ramp, 2)} " +
                "minDepth=${if (lineMinDepthM.isNaN()) "n/a" else fmt(lineMinDepthM) + "m"} " +
                "minDepthAt=${lineMinDepthAt?.let { pointText(it) } ?: "n/a"} " +
                "minDepthWaiver=${lineMinDepthAt?.let { at ->
                    ctx.gate?.waiverOf(at, ctx.from.toLatLng(), ctx.to.toLatLng())
                } ?: "n/a"} " +
                "gateEdge=${if (edge.distanceM.isNaN()) "n/a" else fmt(edge.distanceM) + "m"} " +
                "gateEdgeAt=${pointText(edge.at)} " +
                "bodyEdge=${if (edge.bodyDistanceM.isNaN()) "n/a" else fmt(edge.bodyDistanceM) + "m"} " +
                "bodyEdgeAt=${pointText(edge.bodyAt)} " +
                "edgeIdx=${if (edge.indexDistanceM.isNaN()) "n/a" else fmt(edge.indexDistanceM) + "m"} " +
                "bodyEdgeIdx=${if (edge.indexBodyDistanceM.isNaN()) "n/a" else fmt(edge.indexBodyDistanceM) + "m"} " +
                "innerM=${if (edge.innerM.isNaN()) "n/a" else fmt(edge.innerM) + "m"} " +
                "strictStart=${passReading.search.strictStart} strictAim=${passReading.search.strictAim} " +
                "forced=[${forced.joinToString(", ")}] " +
                "crossed=[${crossed.joinToString(", ")}]"
        }
        // The edge index's own tally — what tracing the 3 m line has cost this session so far.
        ctx.gate?.let { gate -> trace { gate.edges.statsLine() } }
        // The rung's own cost, exactly the figures the ranking reads: the seconds the line spends under a
        // regulated limit, each bucket weighed by its own limit's shortfall against the pace, plus the
        // ramps riding the limit of the run they serve, and the settled clock. All of it comes from
        // [rungSlowWater], the one expression that feeds the per-limit table the panel reads and the
        // ramps that serve a limit into the slow-water seconds — the boat's own cornering floor standing
        // outside both — so the wiring has one home and one fixture. The zone share and the zone metres
        // are still settled for the report and are no longer ranking inputs.
        val slowWater = rungSlowWater(
            timed = timedLine,
            paceKn = ctx.pace,
            limitAt = ctx.limitAt,
            inZone = inZone(ctx.zones),
            inBand = inBand(ctx.world),
            bandLimitKn = AppConfig.routeAvoidZone300LimitKn,
            ceilingKnAt = rounded.ceilingKnAt
        )
        return Rung(
            success(timedLine, forced, crossed, slowLimitSeconds = slowWater.byLimit),
            slowWater.cost
        )
    }

    /** One end after the repair: where it stands, whether it moved, and the rule it settled on. */
    private class RepairedEnd(val point: RoutePoint, val moved: Boolean, val rule: String)

    /**
     * **The end repair** — three rules tried in turn, each keeping the end where it stands if it already
     * passes:
     *
     * 1. **clear** — water at `route.repair.clearanceM` from the coast and from the measured 3 m line, the
     *    nearest such point found by [nearestWhere], never across land;
     * 2. **wall** — water clear of the gate's wall plus its pad, the same way;
     * 3. **water** — the old rule exactly, [legacyRepair], so an end it accepted is never refused.
     *
     * `null` where none of the three finds water — the caller answers [RouteReason.CANNOT_REPAIR].
     */
    private fun repair(point: RoutePoint, world: MultipassWorld, gate: GateRule?): RepairedEnd? {
        val clearM = AppConfig.routeRepairClearanceM
        val walls = world.routeMarkers().filter { it.isWall }
        val clear: (RoutePoint) -> Boolean = { p ->
            validWater(p, world, walls) && world.distanceToCoastM(p.latitude, p.longitude) >= clearM &&
                (gate == null || gate.edgeM(p.toLatLng(), clearM) >= clearM)
        }
        // The clear point is looked for close by only — about the clearance itself — so a berth on a quay
        // moves into its basin rather than up to the repair's whole reach along a straight leg of water.
        val clearReachM = minOf(AppConfig.routeRepairMaxRadiusM, CLEAR_REACH_FACTOR * clearM)
        nearestWhere(point, world, gate, clearReachM, clear)?.let { return RepairedEnd(it, it != point, "clear") }
        if (gate != null) {
            // `edgeM` already takes the pad off, so the wall itself is the bound.
            val wall: (RoutePoint) -> Boolean = { p ->
                validWater(p, world, walls) && gate.edgeM(p.toLatLng(), gate.hardM) >= gate.hardM
            }
            nearestWhere(point, world, gate, AppConfig.routeRepairMaxRadiusM, wall)
                ?.let { return RepairedEnd(it, it != point, "wall") }
        }
        return legacyRepair(point, world)?.let { RepairedEnd(it, it != point, "water") }
    }

    /**
     * **The nearest point [accepts] takes, reached cleanly** ([reachesCleanly]) — [point] itself where it
     * passes; otherwise 5 m rings of 16 bearings out to [reachM], and on the first ring that holds one, each
     * passing bearing and its half- and quarter-step neighbours are bisected inward along their own bearing,
     * the nearest winning. Every point returned passes; the bearing step bounds how near the nearest is.
     * `null` where no ring holds one.
     */
    private fun nearestWhere(
        point: RoutePoint,
        world: MultipassWorld,
        gate: GateRule?,
        reachM: Double,
        accepts: (RoutePoint) -> Boolean
    ): RoutePoint? {
        if (accepts(point)) return point
        val reachable: (RoutePoint) -> Boolean = { p -> accepts(p) && reachesCleanly(point, p, world, gate) }
        val maxRadiusM = reachM
        val stepDeg = 360.0 / CLEAR_BEARINGS
        var radiusM = CLEAR_STEP_M
        while (radiusM <= maxRadiusM) {
            val hits = (0 until CLEAR_BEARINGS).map { it * stepDeg }.filter { reachable(along(point, it, radiusM)) }
            if (hits.isNotEmpty()) {
                var best: RoutePoint? = null
                var bestM = Double.MAX_VALUE
                for (hit in hits) {
                    for (bearing in doubleArrayOf(hit - stepDeg / 2, hit - stepDeg / 4, hit, hit + stepDeg / 4, hit + stepDeg / 2)) {
                        if (!reachable(along(point, bearing, radiusM))) continue
                        var inside = radiusM - CLEAR_STEP_M
                        var outside = radiusM
                        repeat(CLEAR_BISECTIONS) {
                            val mid = (inside + outside) / 2.0
                            if (reachable(along(point, bearing, mid))) outside = mid else inside = mid
                        }
                        if (outside < bestM) {
                            bestM = outside
                            best = along(point, bearing, outside)
                        }
                    }
                }
                return best
            }
            radiusM += CLEAR_STEP_M
        }
        return null
    }

    private fun along(point: RoutePoint, bearingDeg: Double, distanceM: Double): RoutePoint {
        val moved = SpatialOperations.pointAlongBearing(point.latitude, point.longitude, bearingDeg, distanceM)
        return RoutePoint(moved.latitude, moved.longitude)
    }

    /**
     * **Whether the move from [from] to [to] is clean** — it crosses the coastline only to leave land it
     * started on (exactly, against the coast's own edges), and once it first stands outside the gate's blocked
     * water it never re-enters it. An end tapped on land or on a shoal may therefore walk off it, while a move
     * never hops a mole, a jetty or a shoal.
     */
    private fun reachesCleanly(from: RoutePoint, to: RoutePoint, world: MultipassWorld, gate: GateRule?): Boolean {
        val a = from.toLatLng()
        val b = to.toLatLng()
        val box = BBox(
            minOf(a.latitude, b.latitude), maxOf(a.latitude, b.latitude),
            minOf(a.longitude, b.longitude), maxOf(a.longitude, b.longitude)
        )
        var crossings = world.segmentsIn(box).count { SpatialOperations.segmentsIntersect(a, b, it.a, it.b) }
        for (coast in world.openCoastIn(box)) {
            for (k in 0 until coast.size - 1) {
                if (SpatialOperations.segmentsIntersect(a, b, coast[k], coast[k + 1])) crossings++
            }
        }
        if (crossings > (if (world.isWater(from.latitude, from.longitude)) 0 else 1)) return false
        if (!world.isWater(to.latitude, to.longitude)) return false
        val lengthM = SpatialOperations.haversine(a, b)
        val pieces = kotlin.math.ceil(lengthM / CLEAR_LEG_SAMPLE_M).toInt().coerceAtLeast(1)
        var entered = false
        for (k in 0..pieces) {
            val t = k.toDouble() / pieces
            val lat = from.latitude + (to.latitude - from.latitude) * t
            val lon = from.longitude + (to.longitude - from.longitude) * t
            val usable = gate == null || !gate.isShallow(LatLng(lat, lon))
            if (usable) entered = true else if (entered) return false
        }
        return entered
    }

    /** The `REPAIR` trace line: an end raw and repaired, the rule it settled on, and the clearances it reached. */
    private fun repairLine(end: String, raw: RoutePoint, repaired: RepairedEnd, world: MultipassWorld, gate: GateRule?): String {
        val at = repaired.point
        val edge = gate?.edgeM(at.toLatLng(), 100.0)?.takeIf { it != Double.MAX_VALUE }
        return "REPAIR end=$end raw=${fmt(raw.latitude, 5)},${fmt(raw.longitude, 5)} " +
            "at=${fmt(at.latitude, 5)},${fmt(at.longitude, 5)} moved=${repaired.moved} rule=${repaired.rule} " +
            "movedM=${fmt(SpatialOperations.haversine(raw.toLatLng(), at.toLatLng()))} " +
            "edge=${edge?.let { fmt(it) + "m" } ?: ">100m"} " +
            "coast=${fmt(world.distanceToCoastM(at.latitude, at.longitude).coerceAtMost(9999.0))}m"
    }

    /**
     * **The old repair, kept whole as the last rule** — an 8-direction ring sweep outward from [point],
     * tested with the engine's own water test (on the coastline and deep enough for the gate), growing to
     * `route.repair.maxRadiusM`. The first valid point wins; because it is the nearest, it is on the sea side
     * by construction. `null` where no valid water exists within the sweep's radius.
     */
    private fun legacyRepair(point: RoutePoint, world: MultipassWorld): RoutePoint? {
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
    private fun validWater(
        point: RoutePoint,
        world: MultipassWorld,
        walls: List<RouteMarker> = world.routeMarkers().filter { it.isWall }
    ): Boolean {
        if (!world.isWater(point.latitude, point.longitude)) return false
        val at = LatLng(point.latitude, point.longitude)
        if (walls.any { it.geometry.contains(at) }) return false
        val gateActive = AppConfig.routeAvoidDepthGateEnabled && world.depthReady
        val minDepthM = AppConfig.routeAvoidDepthGateMinM
        return depthClearsGate(world.depthAt(point.latitude, point.longitude), gateActive, minDepthM)
    }


    /**
     * One boundary's emission. **Every** lookup publishes its whole stage pair — the stage just finished,
     * the one about to run, the line, the readings and any provisional pair it can already stand behind —
     * and moves only its own entry of [lastStages], so a concurrent rung can never corrupt another's chain.
     * The row prints the word of the rung it describes, and the provisional pair replaces its `--` until
     * the settled answer lands.
     */
    private fun publish(
        lookupId: RouteId,
        stage: RouteStage?,
        points: List<RoutePoint>? = null,
        readings: List<RouteStepReading> = emptyList(),
        provisional: RouteProvisional? = null
    ) {
        if (stage == null) return
        val done = lastStages.put(lookupId, stage)
        _updates.tryEmit(
            RouteUpdate(
                routeId = lookupId,
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

    /**
     * The terminal update: the last stage finished, `nextStage` null, the result or the reason. The one
     * fold rides it twice — [runningBest] as the running best and [verdict] as the settled verdict — so a
     * surface that follows the seat and one that states the verdict read the same crown.
     */
    private fun emitTerminal(
        lookupId: RouteId,
        result: RouteResult.Success?,
        reason: RouteReason?,
        runningBest: RouteRunningBest? = null,
        verdict: RouteVerdict? = null
    ) {
        val done = lastStages[lookupId]
        _updates.tryEmit(
            RouteUpdate(
                routeId = lookupId,
                stageDone = done,
                nextStage = null,
                line = result?.points ?: emptyList(),
                result = result,
                reason = reason,
                runningBest = runningBest,
                verdict = verdict
            )
        )
        trace {
            "DONE stage=${done?.name ?: "none"} result=${result != null} reason=${reason?.name ?: "none"}"
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
        waypoints: List<LatLng>,
        gate: GateRule?
    ): List<String> {
        val restrictive = priced.filter { zonePriceSec(cellM, pace, it.limitKn, lambda) > 0.0 }
        if (restrictive.isEmpty()) {
            trace { "PROBE restrictive=[] avoiding=n/a expansions=0 forced=[]" }
            return emptyList()
        }
        val blocked = grid.blockedCopy(pace)
        blocked.forceFree(from.latitude, from.longitude)
        blocked.forceFree(to.latitude, to.longitude)
        // The probe walks the same wall the search does, so a gap the guarded search cannot use never
        // makes a crossing look avoidable.
        val probe = MultipassSearch.search(
            blocked, startCell, aimCell, Units.knotsToMps(pace),
            stepGuard = gate?.let { StepGuard(it, from.toLatLng(), to.toLatLng()) }
        )
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
     * read serves the leg times, the per-leg speeds and the duration, so none of them can disagree — and
     * the reading the cost site settled: the per-limit slow seconds the panel's Speed limits line reads.
     *
     * It carries the report's **two** zone facts, each settled by its own read and neither derived from
     * the other: [forcedCrossingZoneNames], the price's answer, and `crossedSlowZoneNames`, the water's.
     */
    private fun success(
        timed: TimedLine,
        forcedCrossingZoneNames: List<String>,
        crossedSlowZoneNames: List<String>,
        distanceM: Double = lineLengthM(timed.points),
        durationSec: Double = timed.durationSec,
        slowLimitSeconds: List<RouteSlowLimit> = emptyList()
    ): RouteResult.Success = RouteResult.Success(
        points = timed.points.map { RoutePoint.of(it) },
        legTimesSec = timed.legTimesSec,
        legSpeedsMps = timed.legSpeedsMps,
        distanceM = distanceM,
        durationSec = durationSec,
        // The repair's own word on the two ends is set at the terminal, from the arm that moved them.
        destinationMoved = false,
        forcedCrossingZoneNames = forcedCrossingZoneNames,
        crossedSlowZoneNames = crossedSlowZoneNames,
        slowLimitSeconds = slowLimitSeconds
    )

    /** The polyline's own length in metres — the shared haversine sum, read by the answer and every candidate alike. */
    private fun lineLengthM(points: List<LatLng>): Double = polylineLengthM(points)







    // ── The instrument ─────────────────────────────────────────────────────────

    /** A point as `lat,lon` to five decimals, or `n/a` where it is `NaN`. */
    private fun pointText(p: LatLng): String =
        if (p.latitude.isNaN()) "n/a" else "${fmt(p.latitude, 5)},${fmt(p.longitude, 5)}"

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

    /** The metres of a timed line it runs slower than the pace over. */
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
}

/**
 * One landed rung this arm: the lookup that owns its line and its own cost. The rung's ladder index is
 * no longer carried — it existed to settle a total tie in the rung nearest the preference, and the one
 * sum decides the crown alone.
 */
private data class LandedRung(
    val lookupId: RouteId,
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
private class LadderGridHolder(
    /** Whether the repair moved the arm's aim, said on every rung's answer. */
    val destinationMoved: Boolean = false
) {
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


/**
 * **One rung's slow-water reading, as the engine's own cost site settles it** — the per-limit table
 * the panel reads ([slowTimeByLimit]: a ring's interior and the 300 m band, each leg charged the
 * part of its own time that lies on that water) **plus** the ramps that serve a limit
 * ([rampSlowSeconds], which that table charges to no entry at all), with the boat's own cornering
 * floor standing outside both. [RungSlowWater.byLimit] is the table the answer publishes and
 * [RungSlowWater.cost] the ranking's own figure, so the two can never be read off two different sums.
 *
 * **The crown's figure is the line's own excess under its limits.** Each bucket's seconds take
 * **their own limit's** severity against the pace in force ([slowWaterSeverity]: the linear
 * shortfall of that limit against the pace, floored at zero), and a ramp takes the severity of the
 * limit of the slow run it serves — so the figure is the same quantity in kind as the price the
 * drawing bills. A limit that is not below the pace weighs **nothing**; a ramp whose ends meet no
 * limit is no bucket at all. The table published beside it stays **plain seconds**, so the panel's
 * minutes, the drawing, the price, the clock, the dial's stops, the stored stop and the weight all
 * keep reading what they read today.
 *
 * This is the one expression the cost site uses, named so a fixture can pin **the engine's wiring** —
 * that the table and the ramps really reach the weight the crown is read with — rather than
 * re-deriving the sum beside it.
 */
internal fun rungSlowWater(
    timed: TimedLine,
    paceKn: Double,
    limitAt: (LatLng) -> Double?,
    inZone: (LatLng) -> Boolean,
    inBand: (LatLng) -> Boolean,
    bandLimitKn: Double,
    ceilingKnAt: (LatLng) -> Double?
): RungSlowWater {
    val byLimit = slowTimeByLimit(timed, paceKn, limitAt, inZone, inBand, bandLimitKn)
    val ramps = rampSlowSeconds(timed, paceKn, limitAt, inZone, inBand, ceilingKnAt)
    return RungSlowWater(
        byLimit = byLimit,
        cost = RoutePassRanking.PassCost(
            slowWaterSec = byLimit.sumOf { it.seconds * slowWaterSeverity(paceKn, it.limitKn) } + ramps,
            durationSec = timed.durationSec
        )
    )
}

/** One rung's slow-water reading: the per-limit table the panel reads, and the ranking's own cost. */
internal data class RungSlowWater(
    val byLimit: List<RouteSlowLimit>,
    val cost: RoutePassRanking.PassCost
)

/** The repair's ring step (m) — a constant of the algorithm, not a key. */
private const val REPAIR_STEP_M = 25.0

/** The repair's directions per ring — the 8 compass bearings. */
private const val REPAIR_DIRECTIONS = 8

/** The clear repair's ring step (m). */
private const val CLEAR_STEP_M = 5.0

/** How finely (m) a move's straight is read for land and shoal. */
private const val CLEAR_LEG_SAMPLE_M = 2.0

/** The clear rule looks this many clearances out at most, so a berth is not walked across its harbour. */
private const val CLEAR_REACH_FACTOR = 2.0

/** The clear repair's bearings per ring. */
private const val CLEAR_BEARINGS = 16

/** How many halvings refine a clear point inward — 5 m down to under 2 cm. */
private const val CLEAR_BISECTIONS = 8

/**
 * The route mode's own log tag — the channel the refusals already ride, and the one `adb logcat -s`
 * filters on for a device pass. The instrument's level is the device's own setting:
 * `adb shell setprop log.tag.MaroRoute INFO`.
 */
private const val TAG = RouteTrace.TAG
