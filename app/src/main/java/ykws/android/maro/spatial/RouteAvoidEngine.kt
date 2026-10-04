package ykws.android.maro.spatial

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import ykws.android.maro.R
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.data.model.RouteResult
import ykws.android.maro.data.regulation.SpeedZone
import ykws.android.maro.spatial.multipass.MultipassGrid
import ykws.android.maro.spatial.multipass.MultipassSearch
import ykws.android.maro.spatial.multipass.MultipassWorld
import ykws.android.maro.spatial.multipass.CellIndex
import ykws.android.maro.spatial.multipass.GridContext
import ykws.android.maro.spatial.multipass.GridWalk
import ykws.android.maro.spatial.multipass.RouteCornerPass
import ykws.android.maro.spatial.multipass.RouteCostField
import ykws.android.maro.spatial.multipass.RouteFinePass
import ykws.android.maro.spatial.multipass.RouteGridBuilder
import ykws.android.maro.spatial.multipass.RouteGridPlan
import ykws.android.maro.spatial.multipass.RoutePassRunner
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
import ykws.android.maro.spatial.multipass.slowShares
import ykws.android.maro.spatial.multipass.timeLineWithLimits
import ykws.android.maro.spatial.multipass.timeLineWithProfile
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
 * `route.avoid.fine.cellRatio`. There is no budget loop: the slow-water budget is demoted, so a rung is
 * computed at its own λ and never corrected.
 *
 * Coroutines and `Flow` only: no thread of its own, and the search checks the calling job between its
 * expansions so a flung map never queues behind a computation nobody wants any more.
 */
class RouteAvoidEngine(
    /** The pace in force (kn), asked fresh on every answer so a slider move reaches the next line. */
    private val paceKn: () -> Double,
    /**
     * The **aversion λ** (0–5) the slow water is priced at, asked fresh like the pace so a slider
     * move reaches the next line: 0 prices slow water as open water, 1 minimises real time, and the
     * top of the scale stays out. It is the λ seed the budget loop then corrects.
     */
    private val aversionKn: () -> Double,
    /**
     * The **slow-water budget** (per cent of a trip, 0–100), asked fresh like the pace so a slider
     * move reaches the next line: the λ loop aims at it, and a share still out of its band is
     * reported on the answer rather than chased.
     */
    private val slowWaterBudgetPct: () -> Int,
    /** The world provider — the map always holds the layers it wraps, so it answers a live world. */
    private val worldProvider: () -> MultipassWorld,
    /**
     * **The two decisions this engine makes about its own walk** — the cell it rasterizes the corridor at
     * and the region its second pass may re-rasterize. `avoid` ships [`UniformGridPlan`], which is exactly
     * the behaviour this engine had before the plan existed, so the default changes nothing; a second
     * algorithm passes its own and inherits the whole pipeline, the clock and the readings unchanged.
     */
    private val plan: RouteGridPlan = UniformGridPlan
) : RouteEngine {

    /** One per-engine channel, buffered so a stage emission never waits on the collector. */
    private val _updates = MutableSharedFlow<RouteUpdate>(extraBufferCapacity = 64)

    override val updates: Flow<RouteUpdate> = _updates.asSharedFlow()

    /** The grid builder — the corridor, the harvest, the one rasterize and the ends' berth carve. */
    private val gridBuilder = RouteGridBuilder(plan)

    /** One pass's walk — the A*, the taut pull, the corner snap and the clock, composed once. */
    private val runner = RoutePassRunner()

    /** The fine pass — the refinement along the settled line, composed once. */
    private val finePass = RouteFinePass(plan, runner)

    /** The engine's own `trace`, handed to the seats so the instrument stays the engine's. */
    private val traceSink: (() -> String) -> Unit = { message -> trace(message) }

    /**
     * The engine's own `publish`, handed to the seats so the emission sequence stays the engine's: a
     * seat never owns it, and the engine is never handed to one.
     */
    private val publishSink: (RouteStage?, List<RoutePoint>?, List<RouteStepReading>) -> Unit =
        { stage, points, readings -> publish(stage, points, readings) }

    /** The lane every lookup's job runs on — an engine owns its compute. */
    private val computeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** The in-flight lookup jobs, by lookup id; [cancelLookup] removes and cancels one. */
    private val jobs = mutableMapOf<RouteId, Job>()

    /** The repaired pair the current declaration set runs on — set by [routesToCompute]. */
    private var repairedOrigin: RoutePoint? = null
    private var repairedDestination: RoutePoint? = null

    /** The computations the last [routesToCompute] declared, by id. */
    private var declarations: Map<RouteId, Computation> = emptyMap()

    /** The id and stage mints: fresh per declaration, lookup and computation. */
    private var nextComputationId = 0L
    private var nextLookupId = 0L

    /** The shared grid the ladder's rungs await — built once per arm, by the first rung to reach it. */
    private var ladderGrid: Deferred<GridContext?>? = null

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
        if (!world.coastlineReady) return RouteDeclarations.Refused(RouteReason.WORLD_NOT_READY)
        if (AppConfig.routeAvoidDepthGateEnabled && !world.depthReady) {
            return RouteDeclarations.Refused(RouteReason.WORLD_NOT_READY)
        }
        val from = repair(origin, world) ?: return RouteDeclarations.Refused(RouteReason.CANNOT_REPAIR)
        val to = repair(destination, world) ?: return RouteDeclarations.Refused(RouteReason.CANNOT_REPAIR)
        repairedOrigin = from
        repairedDestination = to
        // A fresh arm builds a fresh grid: the ladder's three rungs share the one built on the first
        // lookup to reach it. The rungs are the three fixed aversions — none, the split, and the maximum.
        ladderGrid = null
        val computations = listOf(
            Computation(RouteId(++nextComputationId), R.string.route_rung_around, LAMBDA_MAX),
            Computation(RouteId(++nextComputationId), R.string.route_rung_balanced, (LAMBDA_MIN + LAMBDA_MAX) / 2.0),
            Computation(RouteId(++nextComputationId), R.string.route_rung_through, LAMBDA_MIN)
        )
        declarations = computations.associateBy { it.id }
        stageComputationId = computations.first().id
        return RouteDeclarations.Available(computations.map { RouteComputation(it.id, it.descriptionResId) })
    }

    override fun startLookup(computationId: RouteId): RouteId {
        val lookupId = RouteId(++nextLookupId)
        val computation = declarations[computationId]
        jobs[lookupId] = computeScope.launch { runComputation(lookupId, computation) }
        return lookupId
    }

    override fun cancelLookup(id: RouteId) {
        jobs.remove(id)?.cancel()
    }

    /**
     * **One lookup, run on the engine's own lane.** The main computation publishes its stage pair and
     * the settled result; a candidate publishes only its terminal update, the panel's stage line
     * narrating the main's build alone.
     */
    private suspend fun runComputation(lookupId: RouteId, computation: Computation?) {
        if (computation == null) return
        val from = repairedOrigin ?: return
        val to = repairedDestination ?: return
        val world = worldProvider()
        passIndex = 0
        // Only the first rung narrates the panel's stage line; the others publish their terminal answer
        // alone, so the stage context is the narrator's alone and a concurrent rung never writes into it.
        val narrates = computation.id == stageComputationId
        if (narrates) {
            mainLookupId = lookupId
            lastStage = null
        }
        try {
            val grid = sharedGrid(world, from, to).await()
            val result = grid?.let { searchRung(it, computation.lambda, publishStage = narrates) }
            emitTerminal(lookupId, result, if (result == null) RouteReason.NO_PATH else null)
        } finally {
            if (narrates) {
                mainLookupId = null
                lastStage = null
            }
        }
    }

    /**
     * **The ladder's shared grid** — built once per arm and awaited by all three rungs. The first rung
     * to reach it triggers the rasterise; the others await the same `Deferred`, so three rungs cost one
     * rasterise and three A* passes.
     */
    private fun sharedGrid(world: MultipassWorld, from: RoutePoint, to: RoutePoint): Deferred<GridContext?> {
        ladderGrid?.let { return it }
        val deferred = computeScope.async {
            gridBuilder.buildGrid(world, from, to, AppConfig.routeAvoidCorridorReachM, paceKn(), traceSink)
        }
        ladderGrid = deferred
        return deferred
    }

    /**
     * **One rung's solve** — the fixed-λ pipeline over the shared grid, with the one growth step when
     * the first answer finds no path. The growth escalation alone re-rasterises; a rung's own passes
     * never do.
     */
    private suspend fun searchRung(grid: GridContext, lambda: Double, publishStage: Boolean): RouteResult.Success? {
        val first = solveAtLambda(grid, lambda, publishStage)
        if (first == null) {
            if (grid.regionSaturated) return null
            val grown = gridBuilder.buildGrid(grid.world, grid.from, grid.to, AppConfig.routeAvoidCorridorReachM * 2.0, paceKn(), traceSink) ?: return null
            return solveAtLambda(grown, lambda, publishStage)
        }
        // A rung that came back with a forced crossing gets one wider corridor to find the way around —
        // the "around" rung's whole job. The wider answer is kept only where it forces fewer crossings.
        if (grid.regionSaturated || first.forcedCrossingZoneNames.isEmpty()) return first
        val grown = gridBuilder.buildGrid(grid.world, grid.from, grid.to, AppConfig.routeAvoidCorridorReachM * 2.0, paceKn(), traceSink) ?: return first
        val grownResult = solveAtLambda(grown, lambda, publishStage) ?: return first
        return if (grownResult.forcedCrossingZoneNames.size < first.forcedCrossingZoneNames.size) grownResult else first
    }

    /**
     * **One rung's fixed-λ solve** — the A* pass, the taut pull and the corner snap at [lambda], then
     * the fine pass, the forced-crossing probe and the answer. No budget loop: a rung is computed at
     * its own λ and never corrected, so the slow-water budget stays demoted.
     */
    private suspend fun solveAtLambda(
        ctx: GridContext,
        lambda: Double,
        publishStage: Boolean
    ): RouteResult.Success? {
        val passReading = runner.runPass(
            ctx,
            GridWalk(ctx.grid, ctx.startCell, ctx.aimCell, ctx.cellM),
            lambda,
            publishStage = publishStage,
            publish = publishSink,
            trace = traceSink,
            guardZones = true,
            guardBand = true
        )
        val timed = passReading.timed
        if (timed == null) {
            trace {
                "PASS lambda=${fmt(lambda, 2)} NO PATH " +
                    "expansions=${passReading.search.expansions} passable=${passReading.search.passableCells} " +
                    "aimClosed=${passReading.search.aimClosed} " +
                    "aimCell=${ctx.grid.cell(ctx.aimCell.row, ctx.aimCell.col).state} " +
                    "aimLimit=${fmt(ctx.grid.zoneLimitKn(ctx.aimCell.row, ctx.aimCell.col))}kn"
            }
            return null
        }
        val waypoints = passReading.line
        if (publishStage) publish(RouteStage.PULL, null)
        val refined = finePass.finePass(ctx, waypoints, lambda, traceSink)
        val reSearched = finePass.fineReSearch(ctx, refined, lambda, traceSink)
        // Two post-passes over the settled search line: the corner pass rounds each snapped corner into
        // an outward-bulging curve — clear by construction, slowed where the bulge would foul — then the
        // speed pass smooths the profile with anticipation and comfortable acceleration. The enforced
        // limit stays the hard ceiling throughout.
        val rounded = RouteCornerPass.round(
            reSearched, ctx.pace, ctx.world, ctx.depthGateActive, ctx.minDepthM, ctx.marginM
        )
        val timedLine = timeLineWithProfile(
            rounded.points, ctx.pace, ctx.limitAt, rounded.ceilingKnAt,
            clockSampleM(ctx.cellM, AppConfig.routeAvoidFineCellRatio)
        )
        val finalShares = slowShares(timedLine, ctx.pace, inZone = inZone(ctx.zones), inBand = inBand(ctx.world))
        val forced = forcedCrossingNames(
            ctx.grid, ctx.zones, ctx.priced, ctx.cellM, ctx.pace, lambda, ctx.from, ctx.to,
            ctx.startCell, ctx.aimCell, reSearched
        )
        val bandLawM = bandMetres(ctx.world, timedLine.points)
        val bandPricedM = bandPricedMetres(ctx.world, timedLine.points)
        val slowM = slowMetres(timedLine, ctx.pace)
        trace {
            "LINE distance=${fmt(lineLengthM(timedLine.points))}m duration=${fmt(timedLine.durationSec)}s " +
                "legs=${timedLine.legTimesSec.size} " +
                "step=${fmt(clockSampleM(ctx.cellM, AppConfig.routeAvoidFineCellRatio))}m " +
                "bandMetres=${fmt(bandLawM)}m bandPricedMetres=${fmt(bandPricedM)}m " +
                "slowMetres=${fmt(slowM)}m slowShare=${fmt(zoneSlowShare(timedLine, ctx.pace), 2)} " +
                "zoneShare=${fmt(finalShares.zone, 2)} bandShare=${fmt(finalShares.band, 2)} " +
                "rampShare=${fmt(finalShares.ramp, 2)} " +
                "forced=[${forced.joinToString(", ")}]"
        }
        return success(timedLine, forced, null)
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

    /** The engine's own water test: on the coastline, and deep enough where the gate is armed. */
    private fun validWater(point: RoutePoint, world: MultipassWorld): Boolean {
        if (!world.isWater(point.latitude, point.longitude)) return false
        val gateActive = AppConfig.routeAvoidDepthGateEnabled && world.depthReady
        val minDepthM = AppConfig.routeAvoidDepthGateMinM
        return depthClearsGate(world.depthAt(point.latitude, point.longitude), gateActive, minDepthM)
    }


    /**
     * Publishes the main lookup's stage pair — the stage just finished and the one about to run, with
     * the line computed so far. A candidate's pass publishes nothing (the panel narrates the main's
     * build alone); the no-op is deliberate and guarded by [mainLookupId].
     */
    private fun publish(
        stage: RouteStage?,
        points: List<RoutePoint>? = null,
        readings: List<RouteStepReading> = emptyList()
    ) {
        val lookupId = mainLookupId ?: return
        if (stage == null) return
        val done = lastStage
        lastStage = stage
        _updates.tryEmit(
            RouteUpdate(
                routeId = lookupId,
                stageDone = done,
                nextStage = stage,
                line = points ?: emptyList(),
                result = null,
                reason = null,
                readings = readings
            )
        )
        trace {
            "STAGE done=${done?.name ?: "none"} next=${stage.name}" +
                if (points != null) " points=${points.size}" else ""
        }
    }

    /** The terminal update: the last stage finished, `nextStage` null, the result or the reason. */
    private fun emitTerminal(lookupId: RouteId, result: RouteResult.Success?, reason: RouteReason?) {
        _updates.tryEmit(
            RouteUpdate(
                routeId = lookupId,
                stageDone = lastStage,
                nextStage = null,
                line = result?.points ?: emptyList(),
                result = result,
                reason = reason
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
        durationSec: Double = timed.durationSec
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
        forcedCrossingZoneNames = forcedCrossingZoneNames
    )

    /** The polyline's own length in metres — one home, read by the answer and by every candidate alike. */
    private fun lineLengthM(points: List<LatLng>): Double {
        var total = 0.0
        for (i in 0 until points.size - 1) {
            total += SpatialOperations.haversine(points[i], points[i + 1])
        }
        return total
    }







    // ── The instrument ─────────────────────────────────────────────────────────

    /** Emits one line on the route channel when its level is on, building [message] only then. */
    private inline fun trace(message: () -> String) {
        if (logEnabled) Log.i(TAG, message())
    }

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

/** One declared computation — a ladder rung: its id, its description and its fixed λ. */
private data class Computation(
    val id: RouteId,
    val descriptionResId: Int,
    val lambda: Double
)

/** The ladder's lowest rung: λ = 0 — slow water priced as open water, the "through" line. */
private const val LAMBDA_MIN = 0.0

/** The ladder's highest rung: λ = 5 — the configured maximum aversion, the "around" line. */
private const val LAMBDA_MAX = 5.0

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
