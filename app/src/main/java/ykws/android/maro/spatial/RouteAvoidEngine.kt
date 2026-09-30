package ykws.android.maro.spatial

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ykws.android.maro.R
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.RouteOfferSource
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.data.model.RouteResult
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.data.regulation.SpeedZone
import ykws.android.maro.spatial.avoid.AvoidGrid
import ykws.android.maro.spatial.avoid.AvoidPull
import ykws.android.maro.spatial.avoid.AvoidSearch
import ykws.android.maro.spatial.avoid.AvoidWorld
import ykws.android.maro.spatial.avoid.BandLaw
import ykws.android.maro.spatial.avoid.BerthCarve
import ykws.android.maro.spatial.avoid.AvoidCellState
import ykws.android.maro.spatial.avoid.AvoidEdge
import ykws.android.maro.spatial.avoid.CellIndex
import ykws.android.maro.spatial.avoid.ZoneRing
import ykws.android.maro.spatial.avoid.RouteCostField
import ykws.android.maro.spatial.avoid.RouteCostSource
import ykws.android.maro.spatial.avoid.SearchOutcome
import ykws.android.maro.spatial.avoid.SlowShares
import ykws.android.maro.spatial.avoid.TangentCorners
import ykws.android.maro.spatial.avoid.EndApproaches
import ykws.android.maro.spatial.avoid.PullRefusals
import ykws.android.maro.spatial.avoid.RouteCurveFitter
import ykws.android.maro.spatial.avoid.TimedLine
import ykws.android.maro.spatial.avoid.bandPriceAt
import ykws.android.maro.spatial.avoid.bandReachM
import ykws.android.maro.spatial.avoid.bbox
import ykws.android.maro.spatial.avoid.budgetMet
import ykws.android.maro.spatial.avoid.carveBerth
import ykws.android.maro.spatial.avoid.carveReachCells
import ykws.android.maro.spatial.avoid.depthClearsGate
import ykws.android.maro.spatial.avoid.depthGateSource
import ykws.android.maro.spatial.avoid.forcedCrossingZoneNames
import ykws.android.maro.spatial.avoid.insideBandWidthM
import ykws.android.maro.spatial.avoid.lineEntersZone
import ykws.android.maro.spatial.avoid.metricCarveLattice
import ykws.android.maro.spatial.avoid.openEndDisc
import ykws.android.maro.spatial.avoid.rasterize
import ykws.android.maro.spatial.avoid.slowShares
import ykws.android.maro.spatial.avoid.slowWaterPriceAt
import ykws.android.maro.spatial.avoid.speedZoneCollarLimitKnAt
import ykws.android.maro.spatial.avoid.strictestLimitKnAt
import ykws.android.maro.spatial.avoid.timeLineWithLimits
import ykws.android.maro.spatial.avoid.withinBudgetBand
import ykws.android.maro.spatial.avoid.zonePriceAtLimits
import ykws.android.maro.spatial.avoid.zoneSlowShare
import ykws.android.maro.spatial.avoid.zonePriceSec
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/**
 * **The avoid engine: avoid land, islands and hazard rings, and keep off water shallower than the
 * depth gate.**
 *
 * It answers [RouteEngine.routesToCompute] with a set of declared computations — the settled line
 * first, then one per candidate pass `maro.properties` declares — after **repairing** the two ends:
 * a point that is land or shallower than the minimum depth is moved to the nearest valid water on the
 * sea side. A pair that cannot be repaired is refused with [RouteReason.CANNOT_REPAIR], and one whose
 * judgement layers are absent is refused with [RouteReason.WORLD_NOT_READY]. The pipeline is
 * corridor-bounded grid A* plus a clearance taut pull, run on the [AvoidWorld] the injected provider
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
 * **The λ loop, the growth and the fine pass.** A solve builds its grid **once** and stores the band's
 * and the rings' **limits** on it, which is what lets the λ loop (§3) re-solve cheaply and price every
 * slow cell, the band's included, at the new λ: pass one is seeded from
 * `route.avoid.speedZone.softCostAversion`, the line's own clock gives its slow time split three ways —
 * inside a ring, inside the band's own width, and on the ramps standing outside both — and the **zone
 * share** alone drives the correction. A zone share leaving the ±20 % band of
 * `route.avoid.speedZone.timeBudgetPct` is corrected once — λ₁ = λ₀ × (zone share / budget) — with two
 * passes as the cap; a share still out is **reported** on the answer and never chased. Of the two passes
 * **the better is kept** — [betterPass]'s own order: the smaller zone share, then the fewer metres
 * inside a zone, then the shorter clock — and the λ the tail reads is **that** pass's, so a correction
 * that answered worse never undoes the better line. Where the budget is still unmet the corridor is
 * grown one step (§7), the wider answer kept only when it does better. The fine pass (§5) then runs
 * **after** the loop, on the settled line, where a restrictive zone the coarse grid could not see
 * around gets a local A* at `route.avoid.fine.cellRatio`.
 *
 * **The candidate computations.** One per `route.avoid.candidate.passes` entry, each a separate
 * lookup running the same pipeline with its own prices dropped — the speed zones by pricing them at
 * λ = 0, the band by rasterizing without it. A pass whose price is not in force, or whose absent
 * source is skipped, is not declared at all.
 *
 * Coroutines and `Flow` only: no thread of its own, and the search checks the calling job between its
 * expansions so a flung map never queues behind a computation nobody wants any more.
 */
class RouteAvoidEngine(
    /** The pace in force (kn), asked fresh on every answer so a slider move reaches the next line. */
    private val paceKn: () -> Double,
    /**
     * The **slow-water budget** (per cent of a trip, 0–100), asked fresh like the pace so a slider
     * move reaches the next line: the λ loop aims at it, and a share still out of its band is
     * reported on the answer rather than chased.
     */
    private val slowWaterBudgetPct: () -> Int,
    /** The world provider — the map always holds the layers it wraps, so it answers a live world. */
    private val worldProvider: () -> AvoidWorld
) : RouteEngine {

    /** One per-engine channel, buffered so a stage emission never waits on the collector. */
    private val _updates = MutableSharedFlow<RouteUpdate>(extraBufferCapacity = 64)

    override val updates: Flow<RouteUpdate> = _updates.asSharedFlow()

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
        val computations = ArrayList<Computation>(3)
        computations.add(Computation(RouteId(++nextComputationId), R.string.route_computation_main, null))
        for (candidatePass in AppConfig.routeAvoidCandidatePasses) {
            val spec = PassSpec(
                dropsZones = RouteOfferSource.SPEED_ZONES in candidatePass.drops,
                dropsBand = RouteOfferSource.ZONE300 in candidatePass.drops
            )
            if (!candidatePassRuns(spec, world)) continue
            computations.add(Computation(RouteId(++nextComputationId), descriptionFor(spec), spec))
        }
        declarations = computations.associateBy { it.id }
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
        if (computation.pass == null) {
            // The main is the only stage publisher, and its stage context is this lookup's alone. It is
            // set **before** the search so the stage updates the search publishes carry this lookup's id,
            // and the terminal update lands here with the last stage as the "just finished" one.
            mainLookupId = lookupId
            lastStage = null
            try {
                val solve = search(world, from, to, null)
                val result = solve.result
                emitTerminal(lookupId, result, if (result == null) RouteReason.NO_PATH else null)
            } finally {
                mainLookupId = null
                lastStage = null
            }
        } else {
            val solve = search(world, from, to, computation.pass)
            val result = solve.result
            _updates.tryEmit(
                RouteUpdate(
                    routeId = lookupId,
                    stageDone = null,
                    nextStage = null,
                    line = result?.points ?: emptyList(),
                    result = result,
                    reason = if (result == null) RouteReason.NO_PATH else null
                )
            )
        }
    }

    /**
     * **One solve** — the main's corridor-bounded search with its one growth step, or a candidate's
     * single pass at its own fixed λ.
     */
    private suspend fun search(
        world: AvoidWorld,
        from: RoutePoint,
        to: RoutePoint,
        pass: PassSpec?
    ): SolveResult = withContext(Dispatchers.Default) {
        if (pass != null) {
            return@withContext searchOnce(world, from, to, AppConfig.routeAvoidCorridorReachM, pass)
        }
        val reach = AppConfig.routeAvoidCorridorReachM
        val firstSolve = searchOnce(world, from, to, reach, null)
        val first = firstSolve.result
        if (first == null) {
            if (firstSolve.regionSaturated) {
                trace { "GROW reason=no-path first=none grown=none kept=none region-saturated" }
                return@withContext SolveResult(null, true)
            }
            val grown = searchOnce(world, from, to, reach * 2.0, null).result
            trace {
                "GROW reason=no-path first=none grown=${shareText(grown)} " +
                    "kept=${if (grown == null) "none" else "grown"}"
            }
            return@withContext SolveResult(grown, true)
        }
        val overBudget = first.budgetUnmetZoneShare != null
        val crossing = !overBudget && first.forcedCrossingZoneNames.isNotEmpty()
        if (!overBudget && !crossing) {
            return@withContext SolveResult(first, firstSolve.regionSaturated)
        }
        val reason = if (overBudget) "budget" else "crossing"
        if (firstSolve.regionSaturated) {
            trace {
                "GROW reason=$reason first=${shareText(first)} grown=none kept=first region-saturated"
            }
            return@withContext SolveResult(first, true)
        }
        val grown = searchOnce(world, from, to, reach * 2.0, null).result
        val keepGrown = grown != null && when {
            overBudget -> shareRank(grown) < shareRank(first)
            else -> crossingBetter(grown, first)
        }
        trace {
            "GROW reason=$reason first=${shareText(first)} grown=${shareText(grown)} " +
                "kept=${if (keepGrown) "grown" else "first"}"
        }
        SolveResult(if (keepGrown) grown!! else first, true)
    }

    /**
     * One solve: the corridor → the harvest → the grid, **once** → the ends' berth carve → the λ loop
     * over the pipeline → the fine pass → the probe and the answer. A candidate [pass] runs a single
     * [runPass] at its own λ and leaves without the fine pass, the fairing and the probe — the body
     * the offers lane used to run, now a lookup of its own.
     */
    private suspend fun searchOnce(
        world: AvoidWorld,
        from: RoutePoint,
        to: RoutePoint,
        reach: Double,
        pass: PassSpec?
    ): SolveResult {
        passIndex = 0
        publish(RouteStage.CORRIDOR, null)
        val corridor = corridorBox(from, to, world.regionBounds, reach)
        if (corridor == null) {
            trace { "CORRIDOR reach=${fmt(reach)}m box=empty clampedByRegion=n/a" }
            return SolveResult(null, false)
        }
        val box = corridor.box
        val regionSaturated = world.regionBounds != null && box == world.regionBounds
        trace {
            "CORRIDOR reach=${fmt(reach)}m box=${boxText(box)} clampedByRegion=${corridor.clampedByRegion} " +
                "regionSaturated=$regionSaturated"
        }
        val edges = world.segmentsIn(box)
        val openCoast = world.openCoastIn(box)
        val capLatNorth = world.regionBounds?.latNorth ?: box.latNorth
        val cellM = AppConfig.routeAvoidGridCellM
        val marginM = AppConfig.routeAvoidObstacleMarginM
        val zones = if (AppConfig.routeAvoidSpeedZoneEnabled) world.speedZonesIn(box) else emptyList()
        val pace = paceKn()
        val zoneOutsideMarginM = AppConfig.routeAvoidSpeedZoneOutsideMarginM
        val budgetPct = slowWaterBudgetPct().coerceIn(
            AppConfig.ROUTE_SLOW_WATER_BUDGET_PCT_MIN,
            AppConfig.ROUTE_SLOW_WATER_BUDGET_PCT_MAX
        ).toDouble()
        val budget = budgetPct / 100.0
        // **One cursor for every slow source.** The loop corrects it, and it is the λ the band's price
        // and a zone's are both read at, so the band follows a correction exactly as a ring does. A
        // candidate that drops one source's price drops its **limit from the grid**, never the cursor.
        var lambda = AppConfig.routeAvoidSpeedZoneSoftCostAversion
        trace {
            "HARVEST edges=${edges.size} openCoast=${openCoast.size} band=${fmt(world.bandWidthM)}m " +
                "zones=[${zones.joinToString(", ") { "${it.name} ${fmt(it.speedLimitKn)}kn" }}] " +
                "cell=${fmt(cellM)}m margin=${fmt(marginM)}m outsideMargin=${fmt(zoneOutsideMarginM)}m"
        }
        // The grid takes the rings, the band and the **limits**: what a slow cell is, never what it costs
        // this time round. It is therefore **λ-free and built once per solve** — the price is the A*'s
        // own read, so a further pass costs one multiply per expansion and the band's price follows the
        // corrected λ with every other slow source.
        val gridField = costField(world, cellM, pace, withZones = false, withBand = false, zones = emptyList(), lambda = lambda)
        val priced =
            if (pass?.dropsZones == true) emptyList()
            else zones.map { z -> ZoneRing(z.outerRing, z.holes, z.speedLimitKn) }
        val bandSpec = if (pass?.dropsBand == true) null else bandLaw(world)
        publish(RouteStage.GRID, null)
        val grid = rasterize(
            box, cellM, pace, marginM, edges, openCoast, capLatNorth, gridField, priced,
            zoneOutsideMarginM = zoneOutsideMarginM, band = bandSpec
        )
        val startCell = grid.cellOf(from.latitude, from.longitude)
        val aimCell = grid.cellOf(to.latitude, to.longitude)
        val startStateBefore = grid.cell(startCell.row, startCell.col).state
        val aimStateBefore = grid.cell(aimCell.row, aimCell.col).state
        grid.forceFree(from.latitude, from.longitude)
        grid.forceFree(to.latitude, to.longitude)
        val depthGateActive = AppConfig.routeAvoidDepthGateEnabled && world.depthReady
        val minDepthM = AppConfig.routeAvoidDepthGateMinM
        openEndDisc(grid, world, from.toLatLng(), marginM, depthGateActive, minDepthM)
        openEndDisc(grid, world, to.toLatLng(), marginM, depthGateActive, minDepthM)
        val carveReach = carveReachCells(marginM, cellM)
        val startCarve =
            carveEnd(world, grid, startCell, from.toLatLng(), marginM, carveReach, depthGateActive)
        val aimCarve = carveEnd(world, grid, aimCell, to.toLatLng(), marginM, carveReach, depthGateActive)
        val approaches = EndApproaches(startCarve.points, aimCarve.points)
        val refusals = PullRefusals()
        trace {
            "GRID ${inventory(grid)} " +
                "start=${cellRead(world, grid, startCell, from, startStateBefore)} " +
                "aim=${cellRead(world, grid, aimCell, to, aimStateBefore)}"
        }
        trace { carveLine("start", startCarve) }
        trace { carveLine("aim", aimCarve) }
        val start = from.toLatLng()
        val aim = to.toLatLng()
        val sets = ArrayList<CornerSet>(3)
        sets.add(CornerSet(TangentCorners.corners(edges, openCoast, marginM), cellM * 2.0))
        if (AppConfig.routeAvoidZone300Enabled && world.bandWidthM > 0.0) {
            val bandOffsetM = bandReachM(world.bandWidthM, AppConfig.routeAvoidZone300OutsideMarginM)
            sets.add(CornerSet(TangentCorners.corners(edges, openCoast, bandOffsetM), bandOffsetM))
        }
        if (zones.isNotEmpty()) {
            sets.add(
                CornerSet(
                    TangentCorners.ringCorners(zones, zoneOutsideMarginM),
                    zoneOutsideMarginM + cellM
                )
            )
        }
        val limitAt = limitAtFor(world)
        // A candidate runs one pass at its own λ and leaves — the offers body, now a lookup. The source
        // it drops is dropped from the grid and from the guard alike, never by lowering the shared cursor.
        if (pass != null) {
            val single = runPass(
                world, grid, startCell, aimCell, start, aim, pace, cellM, marginM, zoneOutsideMarginM,
                lambda, limitAt, zones, sets, publishStage = false,
                approaches = approaches, refusals = refusals,
                guardZones = pass.dropsZones != true, guardBand = pass.dropsBand != true
            )
            val timed = single.timed
            if (timed == null) {
                trace { "CANDIDATE lambda=${fmt(lambda, 2)} NO PATH" }
                return SolveResult(null, regionSaturated)
            }
            val settled = success(timed, emptyList(), null)
            trace {
                "CANDIDATE lambda=${fmt(lambda, 2)} distance=${fmt(lineLengthM(timed.points))}m " +
                    "duration=${fmt(timed.durationSec)}s"
            }
            return SolveResult(settled, regionSaturated)
        }
        // **The λ loop (§3).** Pass one is seeded from `softCostAversion`, so the first line looks like
        // the shipped one. The share of its own time the line spends **slowed by** a zone — the
        // approach ramps outside a ring included, because the clock pays them — is read off the very
        // clock that timed the line, and then: inside the band the loop stops; **above** it λ is
        // corrected once, λ₁ = λ₀ × (share / budget), and the pipeline runs again; and two passes is
        // the cap whatever the band says, a share still out being reported rather than chased. Only
        // the price moves between passes — the grid and the corner sets stand exactly as they were.
        //
        // **The better pass is the one kept.** A corrective pass replaces the incumbent only where
        // [betterPass] says it is the better answer, and the λ the tail reads is **that** pass's: a
        // correction that answered worse — a higher zone share, or the same share spending more metres
        // inside a zone — cannot reach the fine pass, the fine re-search or the probe by being last.
        var waypoints = emptyList<LatLng>()
        var keptLambda = lambda
        var keptZoneShare = 0.0
        var keptCost: PassCost? = null
        var passes = 0
        while (true) {
            passIndex = passes + 1
            val passLambda = lambda
            val passReading = runPass(
                world, grid, startCell, aimCell, start, aim, pace, cellM, marginM, zoneOutsideMarginM,
                passLambda, limitAt, zones, sets, publishStage = true,
                approaches = approaches, refusals = refusals,
                guardZones = true, guardBand = true
            )
            passes++
            val timed = passReading.timed
            if (timed == null) {
                trace {
                    "PASS $passes lambda=${fmt(passLambda, 2)} NO PATH " +
                        "expansions=${passReading.search.expansions} passable=${passReading.search.passableCells} " +
                        "aimClosed=${passReading.search.aimClosed} " +
                        "aimCell=${grid.cell(aimCell.row, aimCell.col).state} " +
                        "aimLimit=${fmt(grid.zoneLimitKn(aimCell.row, aimCell.col))}kn"
                }
                return SolveResult(null, regionSaturated)
            }
            val shares = passReading.shares
            val zoneShare = shares.zone
            val zoneM = zoneMetres(zones, passReading.line)
            val cost = PassCost(zoneShare, zoneM, timed.durationSec)
            val incumbent = keptCost
            val keep = incumbent == null || betterPass(cost, incumbent)
            if (keep) {
                waypoints = passReading.line
                keptLambda = passLambda
                keptZoneShare = zoneShare
                keptCost = cost
            }
            val met = budgetMet(zoneShare, budgetPct)
            val corrected =
                if (met || passes >= LAMBDA_PASSES || passLambda <= 0.0 || budget <= 0.0) null
                else passLambda * zoneShare / budget
            trace {
                "PASS $passes lambda=${fmt(passLambda, 2)} bandLambda=${fmt(passLambda, 2)} " +
                    "path=${passReading.pathCells} " +
                    "pulled=${passReading.pulledCount} snapped=${passReading.snappedCount} final=${passReading.finalCount} " +
                    "distance=${fmt(lineLengthM(passReading.line))}m duration=${fmt(timed.durationSec)}s " +
                    "zoneShare=${fmt(zoneShare, 2)} bandShare=${fmt(shares.band, 2)} rampShare=${fmt(shares.ramp, 2)} " +
                    "zoneMetres=${fmt(zoneM)}m keep=${if (keep) "yes" else "no"} " +
                    "budget=${bandVerdict(zoneShare, budgetPct)} " +
                    "corrected=${corrected?.let { fmt(it, 2) } ?: "none"}"
            }
            if (met) break
            if (corrected == null) break
            lambda = corrected
        }
        // **The tail reads the kept pass**, never the last one run — its λ, its line and its zone share.
        // The band was priced at that very same λ, one cursor pricing every slow source.
        lambda = keptLambda
        trace {
            "PASSKEEP passes=$passes lambda=${fmt(keptLambda, 2)} bandLambda=${fmt(keptLambda, 2)} " +
                "zoneShare=${fmt(keptZoneShare, 2)}"
        }
        // **The fine pass (§5)** — run here, after the loop, so the loop never pays for it.
        publish(RouteStage.PULL, null)
        val refined = finePass(
            world, box, waypoints, start, aim, pace, cellM, marginM, zoneOutsideMarginM, lambda,
            edges, openCoast, capLatNorth, priced, zones, sets, approaches, refusals
        )
        val reSearched = fineReSearch(
            world, box, refined, start, aim, pace, cellM, marginM, zoneOutsideMarginM, lambda,
            edges, openCoast, capLatNorth, priced, zones, sets, approaches, refusals
        )
        // **The curve fitter (phase 6)** — the settled line's bends faired on water, between the fine
        // re-search and the clock. The faired line is the route **drawn and saved**; its figures are the
        // **pre-fairing base** (`baseTimed`) plus the caps' **delta**, per the plan's "base and the
        // delta", so the fairing's own geometry change is never re-costed. `distanceM` is therefore the
        // pre-fairing length and `durationSec` the base's clock plus the cap delta, while `timed.points`
        // is the drawn (faired) polyline — [routeTimedLine] folds their difference into the last leg so
        // the saved legs sum to the reported duration. The forced-crossing probe reads the
        // **pre-fairing** line, so the crossing report describes the search and not the curve.
        val faired = RouteCurveFitter.fit(
            line = reSearched, grid = grid, box = box, approaches = approaches, world = world,
            paceKn = pace, marginM = marginM, start = start, aim = aim,
            depthGateActive = depthGateActive, minDepthM = minDepthM
        )
        trace {
            "CURVE bends=${faired.bends} resolved=${faired.resolved} keptSharp=${faired.keptSharp} " +
                "points=${faired.points.size} caps=${faired.caps.size}"
        }
        val baseTimed = timeLineWithLimits(reSearched, pace, limitAt)
        val fairedNoCap = timeLineWithLimits(faired.points, pace, limitAt)
        val fairedWithCap = timeLineWithLimits(faired.points, pace, limitAt, faired.caps)
        val capDeltaSec = fairedWithCap.durationSec - fairedNoCap.durationSec
        val durationSec = baseTimed.durationSec + capDeltaSec
        val timed = routeTimedLine(fairedWithCap, durationSec)
        val finalShares = slowShares(fairedWithCap, pace, inZone = inZone(zones), inBand = inBand(world))
        val distanceM = lineLengthM(reSearched)
        val forced = forcedCrossingNames(
            grid, zones, priced, cellM, pace, lambda, from, to, startCell, aimCell, reSearched
        )
        val bandLawM = bandMetres(world, timed.points)
        val bandPricedM = bandPricedMetres(world, timed.points)
        val slowM = slowMetres(timed, pace)
        trace {
            "PULLREF land=${refusals.land} price=${refusals.price}"
        }
        trace {
            "LINE distance=${fmt(distanceM)}m duration=${fmt(durationSec)}s " +
                "legs=${timed.legTimesSec.size} " +
                "bandMetres=${fmt(bandLawM)}m bandPricedMetres=${fmt(bandPricedM)}m " +
                "slowMetres=${fmt(slowM)}m slowShare=${fmt(zoneSlowShare(fairedWithCap, pace), 2)} " +
                "zoneShare=${fmt(finalShares.zone, 2)} bandShare=${fmt(finalShares.band, 2)} " +
                "rampShare=${fmt(finalShares.ramp, 2)} " +
                "searchedShare=${fmt(keptZoneShare, 2)} " +
                "budget=${if (budgetMet(finalShares.zone, budgetPct)) "met" else "unmet"} " +
                "forced=[${forced.joinToString(", ")}]"
        }
        val settled = success(
            timed, forced, if (budgetMet(finalShares.zone, budgetPct)) null else finalShares.zone,
            distanceM = distanceM, durationSec = durationSec
        )
        return SolveResult(settled, regionSaturated)
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
    private fun repair(point: RoutePoint, world: AvoidWorld): RoutePoint? {
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
    private fun validWater(point: RoutePoint, world: AvoidWorld): Boolean {
        if (!world.isWater(point.latitude, point.longitude)) return false
        val gateActive = AppConfig.routeAvoidDepthGateEnabled && world.depthReady
        val minDepthM = AppConfig.routeAvoidDepthGateMinM
        return depthClearsGate(world.depthAt(point.latitude, point.longitude), gateActive, minDepthM)
    }

    /** Whether a candidate pass is declared at all — its dropped price in force, its absent source kept. */
    private fun candidatePassRuns(spec: PassSpec, world: AvoidWorld): Boolean {
        if (spec.dropsZones && !AppConfig.routeAvoidSpeedZoneEnabled) return false
        if (spec.dropsBand && !AppConfig.routeAvoidZone300Enabled) return false
        if (AppConfig.routeAvoidCandidateSkipAbsent) {
            if (spec.dropsBand && world.bandWidthM <= 0.0) return false
        }
        return true
    }

    /** The description a candidate pass is declared under, read from the closed-set resource keys. */
    private fun descriptionFor(spec: PassSpec): Int = when {
        spec.dropsBand -> R.string.route_computation_no_zones_band
        else -> R.string.route_computation_no_zones
    }

    /**
     * Publishes the main lookup's stage pair — the stage just finished and the one about to run, with
     * the line computed so far. A candidate's pass publishes nothing (the panel narrates the main's
     * build alone); the no-op is deliberate and guarded by [mainLookupId].
     */
    private fun publish(stage: RouteStage?, points: List<RoutePoint>? = null) {
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
                reason = null
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
     * **The route's clock, from the faired line and the cap delta.** [clocked] is the drawn (faired)
     * line timed with its caps, so its legs carry the drawn line's own profile. The reported
     * [durationSec] is the **pre-fairing base plus the caps' delta** — the plan's figures, never a
     * re-cost of the fairing's geometry — so it differs from the drawn line's own clock by the seconds
     * the fairing shortened. The residual between the two is folded into the **last** leg, whose profile
     * is therefore the one leg that does not describe the drawn line: it exists so `legTimesSec` sums to
     * [durationSec], the contract `RouteResult.Success` states.
     */
    private fun routeTimedLine(clocked: TimedLine, durationSec: Double): TimedLine {
        val legTimes = clocked.legTimesSec.toMutableList()
        if (legTimes.isEmpty()) return clocked
        val residual = durationSec - legTimes.sum()
        legTimes[legTimes.lastIndex] = max(0.0, legTimes.last() + residual)
        val legSpeeds = clocked.legSpeedsMps.toMutableList()
        if (legSpeeds.size == legTimes.size) {
            val i = legSpeeds.lastIndex
            val d = SpatialOperations.haversine(clocked.points[i], clocked.points[i + 1])
            legSpeeds[i] = if (legTimes[i] > 0.0) d / legTimes[i] else 0.0
        }
        return TimedLine(clocked.points, legTimes, legSpeeds)
    }

    /**
     * One pass of the pipeline after the grid exists: the A\*, the taut pull, the corner snap and the
     * final pull — all of it through the pass's own [lambda]. It is shared by the λ loop, which runs it
     * once or twice, and by the candidate computations, which run it with one source's price dropped.
     *
     * The grid A* owns the order, the tangent corners own the exact points: pull the cell path taut,
     * then move each bend onto its nearest corner whose own set radius contains it when both
     * neighbouring legs stay clear, and pull once more. `null` where no path connects the two ends.
     *
     * **[publishStage] is the candidates' own door**, and it is the one behaviour the instrument changed:
     * the λ passes and the fine pass move the panel's stage line, a candidate's pass does not, so the
     * panel narrates the answer's build alone. [guardZones] and [guardBand] drop one source's price from
     * the pull's own field for a candidate that drops it — the grid's own limits were dropped by the
     * caller — so the dropped price is dropped from the search and the guard alike. One cursor prices
     * both sources, so a candidate never names a second λ.
     */
    private suspend fun runPass(
        world: AvoidWorld,
        grid: AvoidGrid,
        startCell: CellIndex,
        aimCell: CellIndex,
        start: LatLng,
        aim: LatLng,
        pace: Double,
        cellM: Double,
        marginM: Double,
        zoneOutsideMarginM: Double,
        lambda: Double,
        limitAt: (LatLng) -> Double?,
        zones: List<SpeedZone>,
        sets: List<CornerSet>,
        publishStage: Boolean,
        approaches: EndApproaches,
        refusals: PullRefusals?,
        guardZones: Boolean = true,
        guardBand: Boolean = true
    ): PassReading {
        val guardField =
            costField(
                world, cellM, pace, withZones = guardZones, withBand = guardBand, zones = zones,
                lambda = lambda
            )
        if (publishStage) publish(RouteStage.SEARCH, null)
        val search = AvoidSearch.search(
            grid, startCell, aimCell, Units.knotsToMps(pace),
            zonePriceSec = { interiorKn, collarKn, bandCollarKn ->
                slowWaterPriceAt(
                    cellM, pace, lambda, interiorKn, collarKn, bandCollarKn,
                    AppConfig.routeAvoidSpeedZoneOutsideMarginCostFraction,
                    AppConfig.routeAvoidZone300OutsideMarginCostFraction
                )
            }
        )
        val path = search.path
            ?: return PassReading(search, emptyList(), null, SlowShares(0.0, 0.0, 0.0), 0, 0)
        val coarse = path.map { grid.center(it.row, it.col) }
        val full = listOf(start) + coarse + listOf(aim)
        if (publishStage) publish(RouteStage.PULL, full.map { RoutePoint.of(it) })
        val pulled = AvoidPull.pull(
            full, start, aim, marginM, guardField, approaches, refusals
        )
        if (publishStage) publish(RouteStage.SNAP, pulled.map { RoutePoint.of(it) })
        val snapped = snapToCorners(pulled, sets, marginM, guardField, start, aim, approaches)
        val final = AvoidPull.pull(
            snapped, start, aim, marginM, guardField, approaches, refusals
        )
        val timed = timeLineWithLimits(final, pace, limitAt)
        val shares = slowShares(timed, pace, inZone = inZone(zones), inBand = inBand(world))
        return PassReading(search, final, timed, shares, pulled.size, snapped.size)
    }

    /**
     * One pass's own readings, returned rather than logged inside it: the caller that owns the decision
     * the pass leads to — the λ loop's correction, the offers' keep-or-drop — emits the pass's one line
     * where that decision is made, so the line carries the correction beside the pass that needed it.
     */
    private data class PassReading(
        /** The A\*'s own outcome: the path, or the exhaustion reading that says why there is none. */
        val search: SearchOutcome,
        /**
         * The pass's own line — the **last pull's** waypoints, the very list the pipeline hands on and
         * never the clock's split copy beside it, so the pass's answer is the one every later stage
         * reads. `emptyList()` where the A\* answered nothing.
         */
        val line: List<LatLng>,
        /** That same line already timed under the limits in force, or `null` where the A\* answered nothing. */
        val timed: TimedLine?,
        /**
         * The line's slow time split by what slowed it; the **zone** share is the λ loop's own quantity,
         * the band's and the ramps' read beside it so neither can drive a ring's correction. Zeroes on a
         * failing pass.
         */
        val shares: SlowShares,
        /** How many waypoints the first pull left — the pass's own pulled count. */
        val pulledCount: Int,
        /** How many the corner snap left. */
        val snappedCount: Int
    ) {
        /** The A\*'s own cell path length, 0 where it found nothing. */
        val pathCells: Int get() = search.path?.size ?: 0

        /** The settled line's own waypoint count, 0 where the pass failed. */
        val finalCount: Int get() = line.size
    }

    /**
     * The unified cost field for one search, built fresh so a layer that landed since the last answer
     * is read: the coastline's wall — materialized by the rasterizer's geometry sweep, and answering
     * the clearance the pull's margin reads — and the depth gate when it is enabled and the grid is
     * in, which the rasterizer paints cell by cell.
     *
     * The field is **rebuilt per λ pass** while the grid is not: [lambda] reaches the soft arm's own
     * closure, so a corrective pass costs a few lambdas rather than a second raster sweep. A field
     * built for a grid carries **no soft source at all** — the band's and the rings' limits live on the
     * grid and the A\* prices them — so a grid's field is its hard walls alone.
     *
     * [withZones] and [withBand] are the guard's own doors: a candidate that drops one source's price
     * drops it here too, so the dropped price is dropped from the search and the pull alike. The two
     * arms are combined with **max, never summed** — the band's own price and a ring's are one law off
     * one cursor, so a band cell and a ring cell of the same limit cost the same and two sources over
     * one cell charge it once.
     */
    private fun costField(
        world: AvoidWorld,
        cellM: Double,
        pace: Double,
        withZones: Boolean,
        withBand: Boolean,
        zones: List<SpeedZone>,
        lambda: Double
    ): RouteCostField {
        val sources = ArrayList<RouteCostSource>(3)
        sources.add(RouteCostSource.Hard(distanceAt = { p -> world.distanceToCoastM(p.latitude, p.longitude) }))
        if (AppConfig.routeAvoidDepthGateEnabled && world.depthReady) {
            sources.add(
                depthGateSource(AppConfig.routeAvoidDepthGateMinM) { p ->
                    val sample = world.depthAt(p.latitude, p.longitude)
                    if (sample.hasData && !sample.depthM.isNaN()) sample.depthM.toDouble() else Double.NaN
                }
            )
        }
        val bandM = world.bandWidthM
        val bandPriced = withBand && AppConfig.routeAvoidZone300Enabled && bandM > 0.0
        val zonesPriced = withZones && AppConfig.routeAvoidSpeedZoneEnabled && zones.isNotEmpty()
        if (bandPriced || zonesPriced) {
            // **One arm, one price law.** The band's limit's time excess and a zone's are the same
            // quantity through the same function, and the two arms are combined with `max`: a 5 kn band
            // cell and a 5 kn ring cell cost the same, the band follows the pass's λ with every other
            // slow source, and two sources over one cell charge it once rather than twice.
            // `zone300.softCostAversion` is retired.
            val bandOutsideMarginM = AppConfig.routeAvoidZone300OutsideMarginM
            val bandFraction = AppConfig.routeAvoidZone300OutsideMarginCostFraction
            val bandSec = zonePriceSec(cellM, pace, AppConfig.routeAvoidZone300LimitKn, lambda)
            val zoneOutsideMarginM = AppConfig.routeAvoidSpeedZoneOutsideMarginM
            val zoneFraction = AppConfig.routeAvoidSpeedZoneOutsideMarginCostFraction
            val scopedZones = zones
            sources.add(
                RouteCostSource.Soft(
                    priceSec = { p ->
                        val bandPrice =
                            if (bandPriced && bandSec > 0.0) {
                                bandPriceAt(
                                    bandM, bandOutsideMarginM, bandSec, bandFraction,
                                    world.distanceToCoastM(p.latitude, p.longitude)
                                )
                            } else {
                                0.0
                            }
                        val zonePrice =
                            if (zonesPriced) {
                                val interiorLimit =
                                    strictestLimitKnAt(scopedZones, emptySet(), p.latitude, p.longitude)
                                val collarLimit = speedZoneCollarLimitKnAt(
                                    scopedZones, emptySet(), p.latitude, p.longitude, zoneOutsideMarginM
                                )
                                zonePriceAtLimits(
                                    cellM, pace, interiorLimit ?: 0.0, collarLimit ?: 0.0, lambda, zoneFraction
                                )
                            } else {
                                0.0
                            }
                        max(bandPrice, zonePrice)
                    },
                    // The guard reads a price, never a cell's tag, so one arm's tag stands for both.
                    tag = AvoidCellState.ZONE
                )
            )
        }
        return RouteCostField(sources)
    }

    /** One tangent corner set: the offset points and the radius within which they may move a bend. */
    internal data class CornerSet(val points: List<LatLng>, val radiusM: Double)

    /** Moves a bend onto its nearest tangent corner — the nearest corner whose own set radius
     *  contains it, across all sets — only when both legs stay clear; open water keeps the bend. */
    private fun snapToCorners(
        path: List<LatLng>,
        sets: List<CornerSet>,
        marginM: Double,
        field: RouteCostField,
        start: LatLng,
        aim: LatLng,
        approaches: EndApproaches
    ): List<LatLng> {
        if (sets.all { it.points.isEmpty() }) return path
        val out = path.toMutableList()
        for (i in 1 until path.size - 1) {
            var nearest: LatLng? = null
            var nearestDist = Double.MAX_VALUE
            for (set in sets) {
                for (c in set.points) {
                    val d = SpatialOperations.haversine(path[i], c)
                    if (d < set.radiusM && d < nearestDist) {
                        nearest = c
                        nearestDist = d
                    }
                }
            }
            val corner = nearest ?: continue
            val hardClear =
                AvoidPull.legClear(out[i - 1], corner, marginM, field, start, aim, approaches) &&
                    AvoidPull.legClear(corner, path[i + 1], marginM, field, start, aim, approaches)
            val replacedPrice = AvoidPull.softPriceSec(out[i - 1], path[i], marginM, field) +
                AvoidPull.softPriceSec(path[i], path[i + 1], marginM, field)
            val snappedPrice = AvoidPull.softPriceSec(out[i - 1], corner, marginM, field) +
                AvoidPull.softPriceSec(corner, path[i + 1], marginM, field)
            if (hardClear && snappedPrice <= replacedPrice) {
                out[i] = corner
            }
        }
        return out
    }

    /**
     * Whether the route was forced through a priced zone: with every restrictive zone's interior
     * blocked the corridor has no avoiding path, and the names reported are the restrictive zones the
     * drawn line enters. An ordinary priced crossing — a way around exists — reports nothing.
     */
    private suspend fun forcedCrossingNames(
        grid: AvoidGrid,
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
        val probe = AvoidSearch.search(blocked, startCell, aimCell, Units.knotsToMps(pace))
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

    /**
     * The limit in force at a point — the clock's own read, and **λ-free by construction**: the ETA
     * obeys the limits and never the price, so the reported time cannot move with the loop's λ.
     *
     * It answers the **strictest limit in force**: the 300 m band's own limit inside the band's width,
     * a speed zone's own limit inside its ring while the zone source is armed, and the lesser of the
     * two where both hold. The band's read is **the same test the band's price makes** —
     * [insideBandWidthM] over the world's own distance read — so the search and the clock can never
     * disagree about which water is the band. Per D8 the band's read stands whatever
     * `route.avoid.zone300.enabled` says: that switch prices water, it never suspends the law.
     */
    private fun limitAtFor(world: AvoidWorld): (LatLng) -> Double? {
        val bandM = world.bandWidthM
        val bandLimitKn = AppConfig.routeAvoidZone300LimitKn
        val zonesPriced = AppConfig.routeAvoidSpeedZoneEnabled
        return { p ->
            val zoneLimit = if (zonesPriced) world.zoneLimitKnAt(p.latitude, p.longitude) else null
            val bandLimit =
                if (bandM > 0.0 &&
                    insideBandWidthM(world.distanceToCoastM(p.latitude, p.longitude), bandM)
                ) bandLimitKn else null
            when {
                zoneLimit == null -> bandLimit
                bandLimit == null -> zoneLimit
                else -> min(zoneLimit, bandLimit)
            }
        }
    }

    /** A pass's unmet share, ranked so a **met** answer always wins: `-1` means "met". */
    private fun shareRank(result: RouteResult.Success): Double = result.budgetUnmetZoneShare ?: -1.0

    /**
     * **The λ loop's keep rule** — one pass against another, in a **stated order**: the **smaller zone
     * share** first, because that is the quantity the loop's λ is there to buy down; then the **fewer
     * metres inside a zone**, the same objective read in metres rather than in seconds; and only then
     * the **shorter clock**, so a corrective pass keeps a longer line wherever it is no worse on the
     * zone. Pure and total, so a pair of passes ranks with no grid, world or trace in hand.
     */
    internal fun betterPass(candidate: PassCost, incumbent: PassCost): Boolean {
        if (candidate.zoneShare != incumbent.zoneShare) return candidate.zoneShare < incumbent.zoneShare
        if (candidate.zoneMetresM != incumbent.zoneMetresM) return candidate.zoneMetresM < incumbent.zoneMetresM
        return candidate.durationSec < incumbent.durationSec
    }

    /** One pass's own three figures, in [betterPass]'s own order — the comparator's whole input. */
    internal data class PassCost(
        /** The pass line's **zone share** — [slowShares]'s ring-interior reading, the quantity λ buys down. */
        val zoneShare: Double,
        /** The pass line's own metres standing inside a priced zone's interior. */
        val zoneMetresM: Double,
        /** The pass line's own clock, in seconds. */
        val durationSec: Double
    )

    /**
     * **The fine re-search's keep rule** — the fine line replaces the incumbent only where it is
     * strictly faster **and** no worse in its **slow share**, both read off the same two timed lines
     * with [zoneSlowShare]. The clock alone is λ-blind: a fine line quicker on the clock but spending
     * more of its own time slowed would undo the λ loop the moment it is spliced.
     */
    internal fun fineSpliceBetter(fine: TimedLine, incumbent: TimedLine, paceKn: Double): Boolean =
        fine.durationSec < incumbent.durationSec &&
            zoneSlowShare(fine, paceKn) <= zoneSlowShare(incumbent, paceKn)

    /**
     * The crossing keep rule: a grown answer is kept only where it has **fewer forced-crossing zones**
     * than the first, or the same count with a **lower unmet share** — met counting as none — so the
     * wider corridor can never leave the route worse than the narrow one did.
     */
    private fun crossingBetter(candidate: RouteResult.Success, incumbent: RouteResult.Success): Boolean {
        val candidateForced = candidate.forcedCrossingZoneNames.size
        val incumbentForced = incumbent.forcedCrossingZoneNames.size
        if (candidateForced != incumbentForced) return candidateForced < incumbentForced
        return shareRank(candidate) < shareRank(incumbent)
    }

    /** The polyline's own length in metres — one home, read by the answer and by every candidate alike. */
    private fun lineLengthM(points: List<LatLng>): Double {
        var total = 0.0
        for (i in 0 until points.size - 1) {
            total += SpatialOperations.haversine(points[i], points[i + 1])
        }
        return total
    }

    /**
     * **The fine pass (§5)** — the refinement along the settled answer, run once and **after** the λ
     * loop, so the loop never pays for it.
     */
    private suspend fun finePass(
        world: AvoidWorld,
        corridor: BBox,
        line: List<LatLng>,
        start: LatLng,
        aim: LatLng,
        pace: Double,
        cellM: Double,
        marginM: Double,
        outsideMarginM: Double,
        lambda: Double,
        edges: List<AvoidEdge>,
        openCoast: List<List<LatLng>>,
        capLatNorth: Double,
        priced: List<ZoneRing>,
        zones: List<SpeedZone>,
        sets: List<CornerSet>,
        approaches: EndApproaches,
        refusals: PullRefusals?
    ): List<LatLng> {
        val fineCellM = cellM * AppConfig.routeAvoidFineCellRatio
        if (line.size < 2 || fineCellM <= 0.0 || fineCellM >= cellM) return line
        var out = line
        for (zone in zones) {
            if (zonePriceSec(cellM, pace, zone.speedLimitKn, lambda) <= 0.0) continue
            if (!lineEntersZone(out, zone)) continue
            out = solveCrossing(
                world, corridor, out, zone, start, aim, pace, cellM, fineCellM, marginM, outsideMarginM,
                lambda, edges, openCoast, capLatNorth, priced, zones, sets, approaches, refusals
            ) ?: out
        }
        val fineGuard =
            costField(world, fineCellM, pace, withZones = true, withBand = true, zones = zones, lambda = lambda)
        val pulled = AvoidPull.pull(
            out, start, aim, marginM, fineGuard, approaches, refusals
        )
        val snapped = snapToCorners(pulled, sets, marginM, fineGuard, start, aim, approaches)
        val settled = AvoidPull.pull(
            snapped, start, aim, marginM, fineGuard, approaches, refusals
        )
        trace { "FINE settled points=${settled.size} fineCell=${fmt(fineCellM)}m" }
        return settled
    }

    /**
     * **D8 — the fine re-search along the settled line.** The coarse grid closes any passage narrower
     * than roughly two cells, so a narrow channel the search would rather thread reads as land and the
     * line is forced around it. This pass re-rasterizes a **swath** around the settled line at
     * `route.avoid.fine.cellRatio`, re-runs the A*, and keeps the fine line **only where it is strictly
     * faster** than the incumbent **and no worse in its slow share** — the clock alone is λ-blind, so a
     * finer line quicker on the clock but spending more of its own time slowed would undo the λ loop
     * the moment it is spliced. [fineSpliceBetter] holds the rule, read off the two timed lines.
     */
    private suspend fun fineReSearch(
        world: AvoidWorld,
        corridor: BBox,
        line: List<LatLng>,
        start: LatLng,
        aim: LatLng,
        pace: Double,
        cellM: Double,
        marginM: Double,
        outsideMarginM: Double,
        lambda: Double,
        edges: List<AvoidEdge>,
        openCoast: List<List<LatLng>>,
        capLatNorth: Double,
        priced: List<ZoneRing>,
        zones: List<SpeedZone>,
        sets: List<CornerSet>,
        approaches: EndApproaches,
        refusals: PullRefusals?
    ): List<LatLng> {
        val fineCellM = cellM * AppConfig.routeAvoidFineCellRatio
        if (line.size < 2 || fineCellM <= 0.0 || fineCellM >= cellM) return line
        val swathBox = inflate(lineBBox(line), outsideMarginM + cellM)
        val box = clampTo(swathBox, corridor)
        if (box == null) {
            trace { "FINE research box=empty spliced=no" }
            return line
        }
        val base = costField(world, fineCellM, pace, withZones = false, withBand = false, zones = emptyList(), lambda = lambda)
        val grid = rasterize(
            box, fineCellM, pace, marginM, edges, openCoast, capLatNorth, base, priced,
            zoneOutsideMarginM = outsideMarginM, band = bandLaw(world)
        )
        grid.forceFree(start.latitude, start.longitude)
        grid.forceFree(aim.latitude, aim.longitude)
        val depthGateActive = AppConfig.routeAvoidDepthGateEnabled && world.depthReady
        val minDepthM = AppConfig.routeAvoidDepthGateMinM
        openEndDisc(grid, world, start, marginM, depthGateActive, minDepthM)
        openEndDisc(grid, world, aim, marginM, depthGateActive, minDepthM)
        val limitAt = limitAtFor(world)
        val pass = runPass(
            world, grid, grid.cellOf(start.latitude, start.longitude),
            grid.cellOf(aim.latitude, aim.longitude), start, aim, pace, fineCellM, marginM, outsideMarginM,
            lambda, limitAt, zones, sets, publishStage = false,
            approaches = approaches, refusals = refusals,
            guardZones = true, guardBand = true
        )
        val fineTimed = pass.timed
        if (fineTimed == null) {
            trace { "FINE research answered=false spliced=no reason=no-path" }
            return line
        }
        val coarseTimed = timeLineWithLimits(line, pace, limitAt)
        val better = fineSpliceBetter(fineTimed, coarseTimed, pace)
        trace {
            "FINE research answered=true spliced=$better " +
                "fine=${fmt(fineTimed.durationSec)}s coarse=${fmt(coarseTimed.durationSec)}s " +
                "fineShare=${fmt(zoneSlowShare(fineTimed, pace), 2)} " +
                "coarseShare=${fmt(zoneSlowShare(coarseTimed, pace), 2)}"
        }
        return if (better) pass.line else line
    }

    /** The axis-aligned box a polyline spans — the swath's own frame. */
    private fun lineBBox(points: List<LatLng>): BBox {
        var latSouth = Double.MAX_VALUE
        var latNorth = -Double.MAX_VALUE
        var lonWest = Double.MAX_VALUE
        var lonEast = -Double.MAX_VALUE
        for (p in points) {
            if (p.latitude < latSouth) latSouth = p.latitude
            if (p.latitude > latNorth) latNorth = p.latitude
            if (p.longitude < lonWest) lonWest = p.longitude
            if (p.longitude > lonEast) lonEast = p.longitude
        }
        return BBox(latSouth, latNorth, lonWest, lonEast)
    }

    /**
     * The crossing's own **local A\***: the stretch of [line] standing inside [zone]'s box, cut out and
     * re-solved at [fineCellM], with the two vertices flanking the cut as the splice's own ends.
     */
    internal suspend fun solveCrossing(
        world: AvoidWorld,
        corridor: BBox,
        line: List<LatLng>,
        zone: SpeedZone,
        start: LatLng,
        aim: LatLng,
        pace: Double,
        cellM: Double,
        fineCellM: Double,
        marginM: Double,
        outsideMarginM: Double,
        lambda: Double,
        edges: List<AvoidEdge>,
        openCoast: List<List<LatLng>>,
        capLatNorth: Double,
        priced: List<ZoneRing>,
        zones: List<SpeedZone>,
        sets: List<CornerSet>,
        approaches: EndApproaches,
        refusals: PullRefusals?
    ): List<LatLng>? {
        val inflated = inflate(zone.bbox(), outsideMarginM + cellM)
        val box = clampTo(inflated, corridor)
        val clamped = box != null && box != inflated
        if (box == null) {
            trace {
                "FINE zone=${zone.name} box=empty clamped=true fineCell=${fmt(fineCellM)}m " +
                    "first=n/a last=n/a local=no spliced=no reason=box-empty"
            }
            return null
        }
        val first = line.indexOfFirst { inBox(box, it) }
        val last = line.indexOfLast { inBox(box, it) }
        val head = "FINE zone=${zone.name} box=${boxText(box)} clamped=$clamped fineCell=${fmt(fineCellM)}m"
        if (first < 0 || last < 0) {
            trace {
                "$head first=$first last=$last local=no spliced=no reason=no-stretch"
            }
            return null
        }
        val from = if (first == 0) start else line[first - 1]
        val to = if (last == line.size - 1) aim else line[last + 1]
        val base = costField(world, fineCellM, pace, withZones = false, withBand = false, zones = emptyList(), lambda = lambda)
        val guard = costField(
            world, fineCellM, pace, withZones = true, withBand = true, zones = zones, lambda = lambda
        )
        val grid = rasterize(
            box, fineCellM, pace, marginM, edges, openCoast, capLatNorth, base, priced,
            zoneOutsideMarginM = outsideMarginM, band = bandLaw(world)
        )
        grid.forceFree(from.latitude, from.longitude)
        grid.forceFree(to.latitude, to.longitude)
        val search = AvoidSearch.search(
            grid,
            grid.cellOf(from.latitude, from.longitude),
            grid.cellOf(to.latitude, to.longitude),
            Units.knotsToMps(pace),
            zonePriceSec = { interiorKn, collarKn, bandCollarKn ->
                slowWaterPriceAt(
                    fineCellM, pace, lambda, interiorKn, collarKn, bandCollarKn,
                    AppConfig.routeAvoidSpeedZoneOutsideMarginCostFraction,
                    AppConfig.routeAvoidZone300OutsideMarginCostFraction
                )
            }
        )
        val path = search.path
        if (path == null) {
            trace {
                "$head first=$first last=$last local=no spliced=no reason=local-no-path " +
                    "expansions=${search.expansions} passable=${search.passableCells} " +
                    "aimClosed=${search.aimClosed}"
            }
            return null
        }
        val localPath = listOf(from) + path.map { grid.center(it.row, it.col) } + listOf(to)
        val pulled = AvoidPull.pull(
            localPath, start, aim, marginM, guard, approaches, refusals
        )
        val snapped = snapToCorners(pulled, sets, marginM, guard, start, aim, approaches)
        val local = AvoidPull.pull(
            snapped, start, aim, marginM, guard, approaches, refusals
        )
        val out = ArrayList<LatLng>(line.size + local.size)
        if (first > 0) out.addAll(line.subList(0, first))
        out.addAll(if (first > 0) local.subList(1, local.size) else local)
        if (last < line.size - 1) out.addAll(line.subList(last + 2, line.size))
        trace { "$head first=$first last=$last local=yes spliced=yes points=${out.size}" }
        return out
    }

    /** [box] grown by [metresM] on every side, in the degrees the box itself is written in. */
    private fun inflate(box: BBox, metresM: Double): BBox {
        val midLat = (box.latSouth + box.latNorth) / 2.0
        val mPerDegLat = SpatialOperations.EARTH_RADIUS_M * PI / 180.0
        val mPerDegLon = mPerDegLat * cos(Math.toRadians(midLat))
        val dLat = metresM / mPerDegLat
        val dLon = metresM / mPerDegLon
        return BBox(box.latSouth - dLat, box.latNorth + dLat, box.lonWest - dLon, box.lonEast + dLon)
    }

    /** The overlap of [box] with [limit], or `null` where the two do not meet. */
    private fun clampTo(box: BBox, limit: BBox): BBox? {
        val out = BBox(
            max(box.latSouth, limit.latSouth),
            min(box.latNorth, limit.latNorth),
            max(box.lonWest, limit.lonWest),
            min(box.lonEast, limit.lonEast)
        )
        return if (out.latSouth >= out.latNorth || out.lonWest >= out.lonEast) null else out
    }

    /** Whether a point stands inside [box] — the splice's own containment test. */
    private fun inBox(box: BBox, p: LatLng): Boolean =
        p.latitude in box.latSouth..box.latNorth && p.longitude in box.lonWest..box.lonEast

    /** A corridor box and whether the region's own bounds cut it short — the instrument's own reading. */
    private data class Corridor(val box: BBox, val clampedByRegion: Boolean)

    /** The start-aim bounding box inflated by [reach], clamped to the region's bounds — a truncated box is accepted. */
    private fun corridorBox(from: RoutePoint, to: RoutePoint, bounds: BBox?, reach: Double): Corridor? {
        val midLat = (from.latitude + to.latitude) / 2.0
        val mPerDegLat = SpatialOperations.EARTH_RADIUS_M * PI / 180.0
        val mPerDegLon = mPerDegLat * cos(Math.toRadians(midLat))
        val dLat = reach / mPerDegLat
        val dLon = reach / mPerDegLon
        val raw = BBox(
            min(from.latitude, to.latitude) - dLat,
            max(from.latitude, to.latitude) + dLat,
            min(from.longitude, to.longitude) - dLon,
            max(from.longitude, to.longitude) + dLon
        )
        val box = bounds?.let {
            BBox(
                max(raw.latSouth, it.latSouth),
                min(raw.latNorth, it.latNorth),
                max(raw.lonWest, it.lonWest),
                min(raw.lonEast, it.lonEast)
            )
        } ?: raw
        if (box.latSouth >= box.latNorth || box.lonWest >= box.lonEast) return null
        return Corridor(box, bounds != null && box != raw)
    }

    // ── The instrument ─────────────────────────────────────────────────────────

    /** Emits one line on the route channel when its level is on, building [message] only then. */
    private inline fun trace(message: () -> String) {
        if (logEnabled) Log.i(TAG, message())
    }

    /** A number in the machine's own spelling: the log's figures never localise. */
    private fun fmt(value: Double, decimals: Int = 1): String =
        String.format(Locale.US, "%.${decimals}f", value)

    /** A box in the degrees it is written in. */
    private fun boxText(box: BBox): String =
        "(${fmt(box.latSouth, 5)}..${fmt(box.latNorth, 5)},${fmt(box.lonWest, 5)}..${fmt(box.lonEast, 5)})"

    /** Which side of the budget's ±20 % band a share sat on. */
    private fun bandVerdict(share: Double, budgetPct: Double): String = when {
        withinBudgetBand(share, budgetPct) -> "in"
        share > budgetPct / 100.0 -> "over"
        else -> "under"
    }

    /** A solve's unmet share, or the word for the two states that are not a number. */
    private fun shareText(result: RouteResult.Success?): String = when {
        result == null -> "none"
        result.budgetUnmetZoneShare != null -> fmt(result.budgetUnmetZoneShare, 2)
        else -> "met"
    }

    /** The grid's own inventory, counted once per solve for the instrument: the four tags' cell counts. */
    private fun inventory(grid: AvoidGrid): String {
        var land = 0
        var band = 0
        var zone = 0
        var free = 0
        for (row in 0 until grid.rows) {
            for (col in 0 until grid.cols) {
                when (grid.cell(row, col).state) {
                    AvoidCellState.LAND -> land++
                    AvoidCellState.BAND -> band++
                    AvoidCellState.ZONE -> zone++
                    AvoidCellState.FREE -> free++
                }
            }
        }
        return "cells=${grid.rows * grid.cols} land=$land band=$band zone=$zone free=$free"
    }

    /**
     * One end's own reads, cell by cell: the world's water/land answer, the depth it holds, the cell's
     * state and its zone limit — the four figures that separate a green ring from a barred cell.
     */
    private fun cellRead(
        world: AvoidWorld,
        grid: AvoidGrid,
        cell: CellIndex,
        at: RoutePoint,
        stateBefore: AvoidCellState
    ): String {
        val sample = world.depthAt(at.latitude, at.longitude)
        return "(r=${cell.row},c=${cell.col} isWater=${world.isWater(at.latitude, at.longitude)} " +
            "depth=${if (sample.hasData) "${fmt(sample.depthM.toDouble())}m" else "none"} " +
            "state=$stateBefore→${grid.cell(cell.row, cell.col).state} " +
            "limit=${fmt(grid.zoneLimitKn(cell.row, cell.col))}kn)"
    }

    /**
     * **The berth carve (F7), read off one end** — the scan, then the cells it opens, then the stretch
     * the pull will read.
     */
    private fun carveEnd(
        world: AvoidWorld,
        grid: AvoidGrid,
        cell: CellIndex,
        end: LatLng,
        marginM: Double,
        reachCells: Int,
        depthGateActive: Boolean
    ): BerthCarve {
        val found = carveBerth(
            world,
            grid.center(cell.row, cell.col),
            marginM,
            reachCells,
            depthGateActive,
            AppConfig.routeAvoidDepthGateMinM
        ) { steps, direction ->
            val row = cell.row + steps * direction.dRow
            val col = cell.col + steps * direction.dCol
            if (grid.inBounds(row, col)) grid.center(row, col) else null
        }
        val direction = found.direction ?: return found
        for (steps in 1 until found.stepsToWater) {
            grid.openCarve(
                cell.row + steps * direction.dRow,
                cell.col + steps * direction.dCol
            )
        }
        return found.copy(points = listOf(end, found.points.last()))
    }

    /** One end's carve, as the instrument reads it: the direction, the cells opened, the metres — or why not. */
    private fun carveLine(end: String, carve: BerthCarve): String {
        val direction = carve.direction
            ?: return "CARVE end=$end dir=none reason=${carve.reason} length=0.0m"
        val cells = (carve.stepsToWater - 1).coerceAtLeast(0)
        return "CARVE end=$end dir=${direction.label} cells=$cells length=${fmt(carve.lengthM)}m"
    }

    /**
     * The metres of a line whose own middle stands inside a priced zone's **interior** — the λ loop's
     * second figure, its share's own objective read in metres rather than in seconds. The test is the
     * world's own interior read, so the outside margin is a price and never a metre counted here.
     */
    private fun zoneMetres(zones: List<SpeedZone>, points: List<LatLng>): Double {
        if (zones.isEmpty()) return 0.0
        var total = 0.0
        for (i in 0 until points.size - 1) {
            val mid = LatLng(
                (points[i].latitude + points[i + 1].latitude) / 2.0,
                (points[i].longitude + points[i + 1].longitude) / 2.0
            )
            if (strictestLimitKnAt(zones, emptySet(), mid.latitude, mid.longitude) != null) {
                total += SpatialOperations.haversine(points[i], points[i + 1])
            }
        }
        return total
    }

    /**
     * **The ring water, as the share split reads it** — a point inside any priced zone's own ring. It is
     * the world's own interior read, so a collar is a price and never a metre charged to a ring.
     */
    private fun inZone(zones: List<SpeedZone>): (LatLng) -> Boolean =
        { p -> strictestLimitKnAt(zones, emptySet(), p.latitude, p.longitude) != null }

    /**
     * **The band's law water, as the share split reads it** — a point inside the band's own width. The
     * exact test the clock and the price make ([insideBandWidthM]), so the split never charges the band
     * for water the band's limit does not govern.
     */
    private fun inBand(world: AvoidWorld): (LatLng) -> Boolean =
        { p ->
            world.bandWidthM > 0.0 &&
                insideBandWidthM(world.distanceToCoastM(p.latitude, p.longitude), world.bandWidthM)
        }

    /**
     * The band's law for the rasterizer, or `null` where it is not priced: its own width and limit and
     * the outside margin beyond it. `route.avoid.zone300.enabled` gates it — the switch prices the band,
     * so a priced band is a band whose limit the grid stores; the clock's own read is untouched by the
     * switch (`limitAtFor`).
     */
    private fun bandLaw(world: AvoidWorld): BandLaw? =
        if (AppConfig.routeAvoidZone300Enabled && world.bandWidthM > 0.0) {
            BandLaw(
                widthM = world.bandWidthM,
                limitKn = AppConfig.routeAvoidZone300LimitKn,
                outsideMarginM = AppConfig.routeAvoidZone300OutsideMarginM
            )
        } else {
            null
        }

    /**
     * The metres of a line whose own middle stands inside the band's own **width** — the law's water,
     * whose limit the clock pays, read through the very test the price and the clock make
     * ([insideBandWidthM]). It is the law's water alone; the priced reach beside it is
     * [bandPricedMetres], so one figure never stands for both.
     */
    private fun bandMetres(world: AvoidWorld, points: List<LatLng>): Double {
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
    private fun bandPricedMetres(world: AvoidWorld, points: List<LatLng>): Double {
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

/** One solve's answer and whether its corridor already equals the region's own water. */
private data class SolveResult(val result: RouteResult.Success?, val regionSaturated: Boolean)

/** One declared computation — its id, its description and the prices a candidate drops. */
private data class Computation(
    val id: RouteId,
    val descriptionResId: Int,
    val pass: PassSpec?
)

/** The prices a candidate computation drops — the two soft sources, each or both. */
private data class PassSpec(val dropsZones: Boolean, val dropsBand: Boolean)

/** The λ loop's cap: one correction, so **two** solves at most on any one corridor. */
private const val LAMBDA_PASSES = 2

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
