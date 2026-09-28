package ykws.android.maro.spatial

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.RouteOffer
import ykws.android.maro.data.model.RouteOfferSource
import ykws.android.maro.data.model.routeCandidateSavingSec
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.data.model.RouteResult
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.data.regulation.SpeedZone
import ykws.android.maro.spatial.avoid.AvoidGrid
import ykws.android.maro.spatial.avoid.AvoidPull
import ykws.android.maro.spatial.avoid.AvoidSearch
import ykws.android.maro.spatial.avoid.AvoidWorld
import ykws.android.maro.spatial.avoid.BerthCarve
import ykws.android.maro.spatial.avoid.AvoidCellState
import ykws.android.maro.spatial.avoid.AvoidEdge
import ykws.android.maro.spatial.avoid.CellIndex
import ykws.android.maro.spatial.avoid.ZoneRing
import ykws.android.maro.spatial.avoid.RouteCostField
import ykws.android.maro.spatial.avoid.RouteCostSource
import ykws.android.maro.spatial.avoid.SearchOutcome
import ykws.android.maro.spatial.avoid.TangentCorners
import ykws.android.maro.spatial.avoid.EndApproaches
import ykws.android.maro.spatial.avoid.PullRefusals
import ykws.android.maro.spatial.avoid.RouteCurveFitter
import ykws.android.maro.spatial.avoid.TimedLine
import ykws.android.maro.spatial.avoid.bandPriceAt
import ykws.android.maro.spatial.avoid.bandPriceSec
import ykws.android.maro.spatial.avoid.bandReachM
import ykws.android.maro.spatial.avoid.bbox
import ykws.android.maro.spatial.avoid.budgetMet
import ykws.android.maro.spatial.avoid.carveBerth
import ykws.android.maro.spatial.avoid.carveReachCells
import ykws.android.maro.spatial.avoid.depthClearsGate
import ykws.android.maro.spatial.avoid.depthGateSource
import ykws.android.maro.spatial.avoid.forcedCrossingZoneNames
import ykws.android.maro.spatial.avoid.lineEntersZone
import ykws.android.maro.spatial.avoid.metricCarveLattice
import ykws.android.maro.spatial.avoid.openEndDisc
import ykws.android.maro.spatial.avoid.rasterize
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
 * A session like the contract: it holds the origin told at arming and the destination told with
 * each aim, and answers the collision-free route between them — from the origin to the destination,
 * so the polyline's own direction is the direction of travel — or `null` while the other end is not
 * held. The pipeline is corridor-bounded grid A* plus a clearance taut pull, run on the
 * [AvoidWorld] the injected provider supplies.
 *
 * **One cost field, every source through it.** The sources are read as a [RouteCostField] — a `HARD`
 * wall the route may never cross, a `SOFT` price it may pay — and the rasterizer writes each cell once
 * with a base cost to which a source may only add. Stage 1's land is the field's first hard wall,
 * materialized by the geometry sweep; the 3 m depth gate is the second, rastered cell by cell; and the
 * 300 m band (phase 3) is the first **price** — a soft source the route may pay, never a wall — with
 * the regulated speed zones (phase 4) to land as prices on the same chain.
 *
 * **The band.** The layer's own width off the coast, priced at `route.avoid.zone300.softCostAversion`: the
 * field's own soft source writes the price once, and the pull refuses a chord whose own price
 * exceeds the cell path's over the span it would replace. A start or aim already inside the band is
 * accepted, so a berth in a marina basin is priced rather than refused. The whole band is switched
 * by `route.avoid.zone300.enabled`.
 *
 * **The depth gate.** A bilinear depth read per cell centre: a known depth below
 * `route.avoid.depthGate.minM` paints the cell land, ANDed with the coastline's own water through the
 * cell's passability, and everything not below the threshold — deeper water, coarse sources, NoData
 * alike — is ignored, with no confidence floor and no penalty. It is a coarse guard on the route being
 * written, not a fine sounding.
 *
 * **Readiness.** [prepare] fires the world's `load()` on a miss and latches [RouteEngineState.Ready]
 * once the coastline is in — plus the depth grid while `route.avoid.depthGate.enabled` is true; a
 * world that cannot become ready answers [RouteEngineState.Unavailable] with
 * [RouteUnavailableReason.COASTLINE_NOT_LOADED] or [RouteUnavailableReason.DEPTH_NOT_LOADED]. The
 * depth refusal is not decoration: with the gate on and no grid every cell reads unsurveyed and the
 * gate would be silently inert. [isReadyToRecompute] stays `true` — the engine reads nothing that
 * expires between asks.
 *
 * **What the answer means.** The emitted polyline starts at the raw start and ends at the raw aim
 * (`destinationMoved = false`, the pin stands on the aim); the snapped cells are only the search's
 * anchor. An end off the water the engine sees answers [RouteResult.OutsideWater], and an exhausted
 * search answers [RouteResult.NoPath] — retried once with the corridor reach doubled. Each leg is
 * timed at the pace in force, asked fresh per answer.
 *
 * **The berth carve (F7).** Each end is **approached**, not merely freed: after the rasterisation the
 * engine walks the eight compass directions from the end's own cell and opens the first straight
 * stretch reaching legal water through margin-water alone ([carveBerth]) — the depth gate, the
 * coastline and every hazard ring binding as they do everywhere. The stretch travels as a value, so
 * the grid opens its cells and the pull exempts its samples from the one shape, and an end the carve
 * cannot serve still refuses honestly rather than teaching the fence to yield.
 *
 * **The λ loop, the growth and the fine pass.** A solve builds its grid **once** and prices the zones
 * at read time, which is what lets the λ loop (§3) re-solve cheaply: pass one is seeded from
 * `route.avoid.speedZone.softCostAversion`, the line's own clock gives the share of the trip it spends
 * slowed, and a share leaving the ±20 % band of `route.avoid.speedZone.timeBudgetPct` is corrected
 * once — λ₁ = λ₀ × (share / budget) — with two passes as the cap; a share still out is **reported** on
 * the answer and never chased. Where the budget is still unmet the corridor is grown one step (§7),
 * the wider answer kept only when it does better. The fine pass (§5) then runs **after** the loop, on
 * the settled line, where a restrictive zone the coarse grid could not see around gets a local A* at
 * `route.avoid.fine.cellRatio`.
 *
 * **The instrument.** One line per phase, per ask, on the `MaroRoute` channel and on the level the
 * device's own log setting allows: `ASK` and `ENDS` for the two ends, `CORRIDOR` and `HARVEST` for the
 * cut box and its geometry, `GRID` for the raster's inventory with the start and aim cells' own
 * water/depth/state/limit reads — and their state **before and after** the forcing, which is the berth
 * defect's signature — `CARVE` per end with the direction, the cells opened and the metres, or `none`
 * with why it failed, `PASS i` per λ pass with the A\*'s own exhaustion reading on a failure, `GROW`
 * for the corridor's one growth step, `PROBE` for the forced-crossing question, `FINE` per crossing and
 * for the settled line, `PULLREF` for the chords the pull refused and by which of its two tests —
 * land and price — `LINE` for the answer's own figures, `OFFER` and `OFFERS` for the candidates, and
 * `STAGE` for each
 * stage publication, carrying the pass index. It is aggregates only — nothing is emitted inside the
 * A\*'s expansion loop and nothing per pull sample — every message is built **lazily** behind the
 * tag's level, and the payloads are counts and seconds rather than a dossier.
 *
 * Coroutines and `StateFlow` only: no thread of its own, and the search checks the calling job
 * between its expansions so a flung map never queues behind a computation nobody wants any more.
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

    /** Starts [RouteEngineState.NotReady]; only [prepare] moves it, as the contract reserves. */
    private val _state = MutableStateFlow<RouteEngineState>(RouteEngineState.NotReady)

    override val state: StateFlow<RouteEngineState> = _state.asStateFlow()

    /** The boundary the pipeline is at — with the line it holds — published and cleared with the call. */
    private val _progress = MutableStateFlow<RouteProgress?>(null)

    override val progress: StateFlow<RouteProgress?> = _progress.asStateFlow()

    /**
     * The offers for the settled answer, published when the background job that computes them lands —
     * empty from the ask until then, and the set the acquisition's candidate rows and its next/prev
     * pair read (R54).
     */
    private val _offers = MutableStateFlow<List<RouteOffer>>(emptyList())

    override val offers: StateFlow<List<RouteOffer>> = _offers.asStateFlow()

    /**
     * The lane the offers' own job runs on — a private, non-blocking scope beside the caller's solve,
     * so the settled line never waits on the candidates. The job is cancelled at the top of every ask,
     * which is what makes a new ask abort the in-flight offers the way it aborts the solve.
     */
    private val offersScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** The offers' own job, cancelled at the top of every ask and relaunched at answer time. */
    private var offersJob: Job? = null

    /** The end the mode froze when it was armed — told once, and held for the whole session. */
    private var origin: RoutePoint? = null

    /** The aim last dragged — told with each ask, and held while the following phase moves the origin. */
    private var destination: RoutePoint? = null

    /** The pass the instrument is on, reset at each solve so `STAGE` can carry its index. */
    private var passIndex = 0

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

    override suspend fun prepare(): RouteEngineState {
        val world = worldProvider()
        val ready = if (AppConfig.routeAvoidDepthGateEnabled) {
            world.coastlineReady && world.depthReady
        } else {
            world.coastlineReady
        }
        val next = if (ready) RouteEngineState.Ready else world.load()
        _state.value = next
        return next
    }

    /**
     * **The ring asks the search's own question (F8)** — water, deep enough **and** an approach exists
     * — and otherwise names which of the three failed.
     *
     * The middle read is the depth gate's own rule, taken from its one home so a green target and a
     * barred cell can never disagree; the last is the **same bounded eight-direction scan the berth
     * carve runs** ([carveBerth], on the lattice this point's own latitude gives), so a destination
     * walled by land or by the gate turns red **while it is aimed** instead of answering an ask that
     * was never going to be routable.
     *
     * The **margin is deliberately not asked here**: it is waived along the approach (F7), so it may
     * never turn a target red. Reachability is the ask's question, not the ring's, and it is what the
     * one refusal left behind still means.
     */
    override suspend fun validatePoint(point: RoutePoint): RouteRefusalReason? {
        val world = worldProvider()
        val at = LatLng(point.latitude, point.longitude)
        val water = world.isWater(at.latitude, at.longitude)
        val depth = world.depthAt(at.latitude, at.longitude)
        val gateActive = AppConfig.routeAvoidDepthGateEnabled && world.depthReady
        val minDepthM = AppConfig.routeAvoidDepthGateMinM
        val marginM = AppConfig.routeAvoidObstacleMarginM
        val depthOk = depthClearsGate(depth, gateActive, minDepthM)
        // The approach is asked only where the two real barriers cleared: an off-water or too-shallow
        // point is refused on that answer alone, and its approach reads `n/a`.
        val approach = if (water && depthOk) {
            val cellM = AppConfig.routeAvoidGridCellM
            carveBerth(
                world,
                at,
                marginM,
                carveReachCells(marginM, cellM),
                gateActive,
                minDepthM,
                metricCarveLattice(at, cellM)
            ).reachable
        } else {
            null
        }
        val verdict = when {
            !water -> RouteRefusalReason.OFF_WATER
            !depthOk -> RouteRefusalReason.TOO_SHALLOW
            approach == false -> RouteRefusalReason.NO_APPROACH
            else -> null
        }
        trace {
            "RING at=(${fmt(at.latitude, 5)},${fmt(at.longitude, 5)}) water=$water " +
                "depth=${if (depth.hasData) "${fmt(depth.depthM.toDouble())}m" else "none"} " +
                "approach=${approach?.toString() ?: "n/a"} verdict=${verdict?.name ?: "none"}"
        }
        return verdict
    }

    /** Holds the origin; answers the route to the destination it holds, or `null` while it holds none. */
    override suspend fun onOriginPositionChanged(newPosition: RoutePoint): RouteResult? {
        origin = newPosition
        return routeBetween(origin, destination)
    }

    /** Holds the destination; answers the route from the origin it holds, or `null` while it holds none. */
    override suspend fun onDestinationPositionChanged(newPosition: RoutePoint): RouteResult? {
        destination = newPosition
        return routeBetween(origin, destination)
    }

    /** The engine reads its layers live at each ask, so nothing expires between them. */
    override suspend fun isReadyToRecompute(): Boolean = true

    /**
     * Both ends held and both on water → the pipeline; one end off water → [RouteResult.OutsideWater].
     *
     * The ask and its two ends are the instrument's first two lines, and the refusal's own line names
     * **which** end and **which** refusal, the ring's question and the grid's being different ones.
     */
    private suspend fun routeBetween(from: RoutePoint?, to: RoutePoint?): RouteResult? {
        if (from == null || to == null) return null
        // A new ask aborts the offers the previous answer left in flight and clears the published set,
        // exactly as the caller's own ask aborts the solve in flight (R4).
        offersJob?.cancel()
        offersJob = null
        _offers.value = emptyList()
        val world = worldProvider()
        val startWater = world.isWater(from.latitude, from.longitude)
        val aimWater = world.isWater(to.latitude, to.longitude)
        trace {
            "ASK from=(${fmt(from.latitude, 5)},${fmt(from.longitude, 5)}) " +
                "to=(${fmt(to.latitude, 5)},${fmt(to.longitude, 5)}) " +
                "pace=${fmt(paceKn())}kn budget=${slowWaterBudgetPct()}% " +
                "lambda=${fmt(AppConfig.routeAvoidSpeedZoneSoftCostAversion, 2)}"
        }
        trace {
            "ENDS start=${if (startWater) "water" else "land"} aim=${if (aimWater) "water" else "land"}" +
                if (startWater && aimWater) ""
                else " refusal=OutsideWater end=${if (!startWater) "start" else "aim"}"
        }
        if (!startWater || !aimWater) return RouteResult.OutsideWater
        return search(world, from, to)
    }

    /**
     * The corridor-bounded search, with **one growth step**: the corridor reach is doubled when the
     * search found nothing at all — the shipped retry — when the line it found is still over its
     * slow-water budget, and when it reports a **forced crossing**, because a wider corridor is the
     * one step that can turn a crossing into a way around. A grown answer replaces the first **only
     * when it does better** — a lower unmet share, or fewer forced-crossing zones — so a wide
     * corridor can never leave the route worse than the narrow one did; and the cap is that single
     * step, because each one multiplies the sweep roughly fourfold.
     *
     * **The region guard.** A wider reach past the app's own water buys nothing, so no growth runs
     * once the first corridor box already equals [AvoidWorld.regionBounds] — the box the region would
     * clamp any wider reach to.
     *
     * The pipeline is compute-only, so it runs on [Dispatchers.Default] — a slow corridor never blocks
     * the main thread, and the caller's state writes resume on Main.
     *
     * The instrument's `GROW` line names the reason the wider reach was tried — `no-path`, `budget` or
     * `crossing` — both answers' shares and which of them was kept, with `region-saturated` where the
     * guard refused the growth outright.
     */
    private suspend fun search(world: AvoidWorld, from: RoutePoint, to: RoutePoint): RouteResult =
        withContext(Dispatchers.Default) {
            try {
                val reach = AppConfig.routeAvoidCorridorReachM
                val firstAnswer = searchOnce(world, from, to, reach)
                val first = firstAnswer.result
                if (first == null) {
                    if (firstAnswer.regionSaturated) {
                        trace { "GROW reason=no-path first=none grown=none kept=none region-saturated" }
                        return@withContext RouteResult.NoPath
                    }
                    val grownAnswer = searchOnce(world, from, to, reach * 2.0)
                    val grown = grownAnswer.result
                    trace {
                        "GROW reason=no-path first=none grown=${shareText(grown)} " +
                            "kept=${if (grown == null) "none" else "grown"}"
                    }
                    val kept = grown ?: return@withContext RouteResult.NoPath
                    launchOffers(grownAnswer.offerInputs)
                    return@withContext kept
                }
                val overBudget = first.budgetUnmetZoneShare != null
                val crossing = !overBudget && first.forcedCrossingZoneNames.isNotEmpty()
                if (!overBudget && !crossing) {
                    launchOffers(firstAnswer.offerInputs)
                    return@withContext first
                }
                val reason = if (overBudget) "budget" else "crossing"
                if (firstAnswer.regionSaturated) {
                    trace {
                        "GROW reason=$reason first=${shareText(first)} grown=none kept=first region-saturated"
                    }
                    launchOffers(firstAnswer.offerInputs)
                    return@withContext first
                }
                val grownAnswer = searchOnce(world, from, to, reach * 2.0)
                val grown = grownAnswer.result
                val keepGrown = grown != null && when {
                    overBudget -> shareRank(grown) < shareRank(first)
                    else -> crossingBetter(grown, first)
                }
                trace {
                    "GROW reason=$reason first=${shareText(first)} grown=${shareText(grown)} " +
                        "kept=${if (keepGrown) "grown" else "first"}"
                }
                val kept = if (keepGrown) grown!! else first
                launchOffers(if (keepGrown) grownAnswer.offerInputs else firstAnswer.offerInputs)
                kept
            } finally {
                // The stage is cleared where the call really ends — an answer, a refusal and an
                // abort alike — so the panel never names a search that is not running (R15).
                publish(null, passIndex)
            }
        }

    /**
     * One solve: the corridor → the harvest → the grid, **once** → the ends' berth carve → the λ loop
     * over the pipeline → the fine pass → the probe and the answer.
     *
     * The answer leaves **without its offers**: the inputs the offers' computation needs ride beside it
     * as [SettledAnswer.offerInputs], and [search] starts the background job only once it knows which
     * attempt was kept — so the settled line is returned before a single candidate is searched.
     */
    private suspend fun searchOnce(
        world: AvoidWorld,
        from: RoutePoint,
        to: RoutePoint,
        reach: Double
    ): SettledAnswer {
        passIndex = 0
        publish(RouteStage.CORRIDOR, passIndex)
        val corridor = corridorBox(from, to, world.regionBounds, reach)
        if (corridor == null) {
            trace { "CORRIDOR reach=${fmt(reach)}m box=empty clampedByRegion=n/a" }
            return SettledAnswer(null, null, regionSaturated = false)
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
        // The zones arrive pre-filtered by the world (excluded ids dropped); each is priced once from
        // the live pace, then rastered as a zone tag whose cost is the strictest limit in force.
        // The zones are priced only while the feature is armed; with it off the corridor is coastline,
        // depth and band alone, and every zone cell is open water.
        val zones = if (AppConfig.routeAvoidSpeedZoneEnabled) world.speedZonesIn(box) else emptyList()
        val pace = paceKn()
        val zoneOutsideMarginM = AppConfig.routeAvoidSpeedZoneOutsideMarginM
        val budgetPct = slowWaterBudgetPct().coerceIn(
            AppConfig.ROUTE_SLOW_WATER_BUDGET_PCT_MIN,
            AppConfig.ROUTE_SLOW_WATER_BUDGET_PCT_MAX
        ).toDouble()
        val budget = budgetPct / 100.0
        var lambda = AppConfig.routeAvoidSpeedZoneSoftCostAversion
        trace {
            "HARVEST edges=${edges.size} openCoast=${openCoast.size} band=${fmt(world.bandWidthM)}m " +
                "zones=[${zones.joinToString(", ") { "${it.name} ${fmt(it.speedLimitKn)}kn" }}] " +
                "cell=${fmt(cellM)}m margin=${fmt(marginM)}m outsideMargin=${fmt(zoneOutsideMarginM)}m"
        }
        // The grid takes the ring and the **limit**: what a slow cell is, never what it costs this
        // time round. It is therefore **λ-free and built once per solve** — the price is the A*'s own
        // read, so a further pass costs one multiply per expansion instead of the zone read again.
        val gridField =
            costField(world, cellM, pace, withZones = false, withBand = true, zones = zones, lambda = lambda)
        val priced = zones.map { z -> ZoneRing(z.outerRing, z.holes, z.speedLimitKn) }
        publish(RouteStage.GRID, passIndex)
        val grid = rasterize(
            box, cellM, pace, marginM, edges, openCoast, capLatNorth, gridField, priced,
            zoneOutsideMarginM = zoneOutsideMarginM
        )
        val startCell = grid.cellOf(from.latitude, from.longitude)
        val aimCell = grid.cellOf(to.latitude, to.longitude)
        // Each end's state is read **before** the forcing, the forcing being what hides the berth
        // defect: one freed cell whose every neighbour is barred looks like open water afterwards.
        val startStateBefore = grid.cell(startCell.row, startCell.col).state
        val aimStateBefore = grid.cell(aimCell.row, aimCell.col).state
        grid.forceFree(from.latitude, from.longitude)
        grid.forceFree(to.latitude, to.longitude)
        // **The ends' disc (F7's second half)** — the pull exempts a disc of `marginM` around each end,
        // so the grid's own exemption must cover the same disc or a freed end stays enclosed by the
        // margin-land ring around it. Each candidate cell re-reads the world because a barred cell
        // records *that* it is land, never *why*.
        val depthGateActive = AppConfig.routeAvoidDepthGateEnabled && world.depthReady
        val minDepthM = AppConfig.routeAvoidDepthGateMinM
        openEndDisc(grid, world, from.toLatLng(), marginM, depthGateActive, minDepthM)
        openEndDisc(grid, world, to.toLatLng(), marginM, depthGateActive, minDepthM)
        // **The berth carve (F7)** — the margin is the route's rule, not the destination's, so each end
        // is granted an approach beside the one freed cell and the disc. The scan reads the world, never
        // the grid's LAND state, which records *that* a cell is land and never *why*.
        val carveReach = carveReachCells(marginM, cellM)
        val startCarve =
            carveEnd(world, grid, startCell, from.toLatLng(), marginM, carveReach, depthGateActive)
        val aimCarve = carveEnd(world, grid, aimCell, to.toLatLng(), marginM, carveReach, depthGateActive)
        val approaches = EndApproaches(startCarve.points, aimCarve.points)
        // The pull's refusals are counted once per answer, where the pull decides — never per sample.
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
        // The corner sets are geometry rather than price, so they are harvested **once** for every
        // pass. The land set snaps within a cell's reach; the band set, armed with the zone, snaps at
        // the band's own reach so a concave band is chorded; and the hug set moves a bend onto a ring's
        // offset corners, its offset the outside margin's edge plus one cell so a bend lying a cell out
        // is caught — a priced band's edge, never a clearance.
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
        // **The λ loop (§3).** Pass one is seeded from `softCostAversion`, so the first line looks like
        // the shipped one. The share of its own time the line spends **slowed by** a zone — the
        // approach ramps outside a ring included, because the clock pays them — is read off the very
        // clock that timed the line, and then: inside the band the loop stops; **above** it λ is
        // corrected once, λ₁ = λ₀ × (share / budget), and the pipeline runs again; and two passes is
        // the cap whatever the band says, a share still out being reported rather than chased. Only
        // the price moves between passes — the grid and the corner sets stand exactly as they were.
        var waypoints = emptyList<LatLng>()
        var passes = 0
        while (true) {
            passIndex = passes + 1
            val pass = runPass(
                world, grid, startCell, aimCell, start, aim, pace, cellM, marginM, zoneOutsideMarginM,
                lambda, limitAt, zones, sets, publishStage = true,
                approaches = approaches, refusals = refusals
            )
            passes++
            val timed = pass.timed
            if (timed == null) {
                trace {
                    "PASS $passes lambda=${fmt(lambda, 2)} NO PATH " +
                        "expansions=${pass.search.expansions} passable=${pass.search.passableCells} " +
                        "aimClosed=${pass.search.aimClosed} " +
                        "aimCell=${grid.cell(aimCell.row, aimCell.col).state} " +
                        "aimLimit=${fmt(grid.zoneLimitKn(aimCell.row, aimCell.col))}kn"
                }
                return SettledAnswer(null, null, regionSaturated)
            }
            waypoints = pass.line
            val share = pass.share
            val met = budgetMet(share, budgetPct)
            val corrected =
                if (met || passes >= LAMBDA_PASSES || lambda <= 0.0 || budget <= 0.0) null
                else lambda * share / budget
            trace {
                "PASS $passes lambda=${fmt(lambda, 2)} path=${pass.pathCells} " +
                    "pulled=${pass.pulledCount} snapped=${pass.snappedCount} final=${pass.finalCount} " +
                    "distance=${fmt(lineLengthM(pass.line))}m duration=${fmt(timed.durationSec)}s " +
                    "share=${fmt(share, 2)} band=${bandVerdict(share, budgetPct)} " +
                    "corrected=${corrected?.let { fmt(it, 2) } ?: "none"}"
            }
            if (met) break
            if (corrected == null) break
            lambda = corrected
        }
        // **The fine pass (§5)** — run here, after the loop, so the loop never pays for it.
        publish(RouteStage.PULL, passIndex)
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
        // The slow-water share is a verdict on the **drawn** line and is measured there.
        val finalShare = zoneSlowShare(fairedWithCap, pace)
        val distanceM = lineLengthM(reSearched)
        // The probe runs **once**, against the settled line, so neither verdict is reported per pass.
        val forced = forcedCrossingNames(
            grid, zones, priced, cellM, pace, lambda, from, to, startCell, aimCell, reSearched
        )
        val bandM = bandMetres(world, timed.points)
        val slowM = slowMetres(timed, pace)
        trace {
            "PULLREF land=${refusals.land} price=${refusals.price}"
        }
        trace {
            "LINE distance=${fmt(distanceM)}m duration=${fmt(durationSec)}s " +
                "legs=${timed.legTimesSec.size} " +
                "bandMetres=${fmt(bandM)}m bandShare=${fmt(shareOf(bandM, distanceM), 2)} " +
                "slowMetres=${fmt(slowM)}m slowShare=${fmt(finalShare, 2)} " +
                "budget=${if (budgetMet(finalShare, budgetPct)) "met" else "unmet"} " +
                "forced=[${forced.joinToString(", ")}]"
        }
        // The settled answer leaves with no offers: the candidates are computed on the background job
        // [search] starts from [SettledAnswer.offerInputs], so the configured line reaches the map
        // before a single candidate is searched.
        val settled = success(
            timed, forced, if (budgetMet(finalShare, budgetPct)) null else finalShare,
            distanceM = distanceM, durationSec = durationSec
        )
        return SettledAnswer(
            settled,
            OfferInputs(
                world, box, grid, startCell, aimCell, start, aim, pace, cellM, marginM, zoneOutsideMarginM,
                lambda, edges, openCoast, capLatNorth, priced, zones, sets, baseTimed.durationSec, limitAt
            ),
            regionSaturated
        )
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
     * once or twice, and by the offers, which run it with one source's price dropped.
     *
     * The grid A* owns the order, the tangent corners own the exact points: pull the cell path taut,
     * then move each bend onto its nearest corner whose own set radius contains it when both
     * neighbouring legs stay clear, and pull once more. `null` where no path connects the two ends.
     *
     * **[publishStage] is the offers' own door**, and it is the one behaviour the instrument changed:
     * the λ passes and the fine pass move the panel's stage line, a candidate's pass does not, so the
     * panel narrates the answer's build alone. Nothing is lost by it — the pass is logged all the same,
     * on the `OFFER` line that reports its answer, its duration, its saving and whether it was kept.
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
        refusals: PullRefusals?
    ): PassReading {
        val guardField =
            costField(world, cellM, pace, withZones = true, withBand = true, zones = zones, lambda = lambda)
        if (publishStage) publish(RouteStage.SEARCH, passIndex)
        val search = AvoidSearch.search(
            grid, startCell, aimCell, Units.knotsToMps(pace),
            zonePriceSec = { interiorKn, collarKn ->
                zonePriceAtLimits(
                    cellM, pace, interiorKn, collarKn, lambda,
                    AppConfig.routeAvoidSpeedZoneOutsideMarginCostFraction
                )
            }
        )
        val path = search.path ?: return PassReading(search, emptyList(), null, 0.0, 0, 0)
        val coarse = path.map { grid.center(it.row, it.col) }
        val full = listOf(start) + coarse + listOf(aim)
        if (publishStage) publish(RouteStage.PULL, passIndex, full.map { RoutePoint.of(it) })
        val pulled = AvoidPull.pull(
            full, start, aim, marginM, guardField, approaches, refusals
        )
        if (publishStage) publish(RouteStage.SNAP, passIndex, pulled.map { RoutePoint.of(it) })
        val snapped = snapToCorners(pulled, sets, marginM, guardField, start, aim, approaches)
        val final = AvoidPull.pull(
            snapped, start, aim, marginM, guardField, approaches, refusals
        )
        val timed = timeLineWithLimits(final, pace, limitAt)
        return PassReading(search, final, timed, zoneSlowShare(timed, pace), pulled.size, snapped.size)
    }

    /**
     * One pass's own readings, returned rather than logged inside it: the caller that owns the decision
     * the pass leads to — the λ loop's correction, the offers' keep-or-drop — emits the pass's one line
     * where that decision is made, so the line carries the correction beside the pass that needed it.
     *
     * The counts are sizes of lists the pass already built, and [search] is the A\*'s own outcome, so
     * nothing here is measured twice.
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
        /** The share of the line's own time it spends slowed, 0.0 on a failing pass. */
        val share: Double,
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
     * The gate is omitted while `route.avoid.depthGate.enabled` is false or the grid is out. The
     * refusal in [prepare] is what makes the grid-out state unreachable while it is enabled: an
     * ungated search would price unsounded water as open sea and call the answer a route.
     *
     * The field is **rebuilt per λ pass** while the grid is not: [lambda] reaches the zone arm's own
     * closure, so a corrective pass costs a few lambdas rather than a second raster sweep. A field
     * built without zones has no arm to price, and λ is unread there.
     *
     * [withBand] is the offers' own door: the band's price is written into the **grid's base** rather
     * than read per expansion, so a candidate that drops it needs a grid of its own, and this is the
     * one parameter that makes that grid's field.
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
        val sources = ArrayList<RouteCostSource>(4)
        sources.add(RouteCostSource.Hard(distanceAt = { p -> world.distanceToCoastM(p.latitude, p.longitude) }))
        if (AppConfig.routeAvoidDepthGateEnabled && world.depthReady) {
            sources.add(
                depthGateSource(AppConfig.routeAvoidDepthGateMinM) { p ->
                    val sample = world.depthAt(p.latitude, p.longitude)
                    if (sample.hasData && !sample.depthM.isNaN()) sample.depthM.toDouble() else Double.NaN
                }
            )
        }
        if (withBand && AppConfig.routeAvoidZone300Enabled) {
            val bandM = world.bandWidthM
            val bandSec = bandPriceSec(cellM, pace, AppConfig.routeAvoidZone300SoftCostAversion)
            if (bandM > 0.0 && bandSec > 0.0) {
                sources.add(
                    RouteCostSource.Soft(
                        priceSec = { p ->
                            bandPriceAt(
                                bandM,
                                AppConfig.routeAvoidZone300OutsideMarginM,
                                bandSec,
                                AppConfig.routeAvoidZone300OutsideMarginCostFraction,
                                world.distanceToCoastM(p.latitude, p.longitude)
                            )
                        },
                        tag = AvoidCellState.BAND
                    )
                )
            }
        }
        // The zone source — read by the pull and the snap, never per grid cell. It hands [zonePriceAtLimits]
        // the same two limits the A* reads off the grid, so the guard and the search price a point alike:
        // the interior in full, the outside margin at the configured fraction.
        if (withZones && AppConfig.routeAvoidSpeedZoneEnabled) {
            val outsideMarginM = AppConfig.routeAvoidSpeedZoneOutsideMarginM
            val costFraction = AppConfig.routeAvoidSpeedZoneOutsideMarginCostFraction
            val scopedZones = zones
            sources.add(
                RouteCostSource.Soft(
                    priceSec = { p ->
                        val interiorLimit =
                            strictestLimitKnAt(scopedZones, emptySet(), p.latitude, p.longitude)
                        val collarLimit = speedZoneCollarLimitKnAt(
                            scopedZones, emptySet(), p.latitude, p.longitude, outsideMarginM
                        )
                        zonePriceAtLimits(
                            cellM, pace, interiorLimit ?: 0.0, collarLimit ?: 0.0, lambda, costFraction
                        )
                    },
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
            // A snap onto a band corner must not make the line cut through another priced band: the
            // two new legs may cost no more than the span they replace, or the bend stays on the path.
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
     *
     * The probe runs **once per solve**, after the λ loop and against the settled line, and it takes
     * the blocking **off the one grid the search built**: a zone slower than the pace is a cell the
     * grid's own limit answers, so the copy that marks those cells land replaces the second raster
     * sweep this used to take. A zone at or above the pace is not restrictive and is never blocked.
     *
     * Its instrument line names the restrictive zones, whether the water still connects around them,
     * how much of it the probe walked when it did not, and the forced crossing's own names.
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
     * of them can disagree — plus the budget's verdict, set only where the band was missed.
     *
     * The offers are **not** computed here: the answer ships with an empty set and the candidates
     * arrive later on [offers], so the configured line is displayed before they are.
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
        forcedCrossingZoneNames = forcedCrossingZoneNames,
        offers = emptyList()
    )

    /**
     * The limit in force at a point — the clock's own read, and **λ-free by construction**: the ETA
     * obeys the limits and never the price, so the reported time cannot move with the loop's λ. The
     * switch turns the source off whole, so with the zones off the clock reads the pace alone.
     */
    private fun limitAtFor(world: AvoidWorld): (LatLng) -> Double? =
        if (AppConfig.routeAvoidSpeedZoneEnabled) {
            { p -> world.zoneLimitKnAt(p.latitude, p.longitude) }
        } else {
            { _ -> null }
        }

    /** A pass's unmet share, ranked so a **met** answer always wins: `-1` means "met". */
    private fun shareRank(result: RouteResult.Success): Double = result.budgetUnmetZoneShare ?: -1.0

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

    /**
     * One solve's answer and the inputs the offers' own computation needs later, kept apart so the
     * settled line is returned at once while the candidates run behind it.
     */
    private data class SettledAnswer(
        /** The settled line, or null where no path connects the two ends at this reach. */
        val result: RouteResult.Success?,
        /** The inputs the offers' background job reads, or null where there is no settled line. */
        val offerInputs: OfferInputs?,
        /** Whether this reach's corridor box already equals the world's own water — no growth buys more. */
        val regionSaturated: Boolean
    )

    /**
     * Everything the offers' computation reads, captured at answer time: the one grid the solve built,
     * the corridor's geometry and the settled line's own duration, so the background job needs nothing
     * the solve has moved past.
     */
    private data class OfferInputs(
        val world: AvoidWorld,
        val box: BBox,
        val grid: AvoidGrid,
        val startCell: CellIndex,
        val aimCell: CellIndex,
        val start: LatLng,
        val aim: LatLng,
        val pace: Double,
        val cellM: Double,
        val marginM: Double,
        val zoneOutsideMarginM: Double,
        val lambda: Double,
        val edges: List<AvoidEdge>,
        val openCoast: List<List<LatLng>>,
        val capLatNorth: Double,
        val priced: List<ZoneRing>,
        val zones: List<SpeedZone>,
        val sets: List<CornerSet>,
        val settledSec: Double,
        val limitAt: (LatLng) -> Double?
    )

    /**
     * Starts the offers' background job for the answer that is about to be returned, leaving the
     * settled line free to reach the map at once. The job runs on [offersScope] — a lane beside the
     * solve — and the top of every ask cancels the one in flight, so a new ask aborts the offers the
     * way it aborts the solve. The write is guarded by [isActive] so a job cancelled mid-flight never
     * publishes the previous answer's candidates over the new ask's empty set.
     */
    private fun launchOffers(inputs: OfferInputs?) {
        if (inputs == null) return
        offersJob = offersScope.launch {
            val candidates = offers(
                inputs.world, inputs.box, inputs.grid, inputs.startCell, inputs.aimCell,
                inputs.start, inputs.aim, inputs.pace, inputs.cellM, inputs.marginM,
                inputs.zoneOutsideMarginM, inputs.lambda, inputs.edges, inputs.openCoast,
                inputs.capLatNorth, inputs.priced, inputs.zones, inputs.sets,
                inputs.settledSec, inputs.limitAt
            )
            if (isActive) _offers.value = candidates
        }
    }

    /**
     * **The offers (§8, R53, R61)** — the passes the file declares, in the order it declares them,
     * computed **after** the answer is settled and on the solve's own cancellable job.
     *
     * Each pass is the same pipeline with its own prices dropped: the speed zones by pricing them at
     * λ = 0 over the **same grid** — the limit is stored per cell and the price is the read, so this
     * costs one search and nothing else — and the 300 m band by re-rasterizing the one field its price
     * is written into, that price living in the grid's base rather than in a per-cell read. **The passes
     * are cumulative** (R53): the second drops the zones' price as well, so it reads λ = 0 on its own
     * grid and offers the line with no aversion left. A pass whose price is not in force, or whose
     * source touches nothing in the box while `skipAbsent` holds, is skipped rather than run, and a
     * candidate is kept **only where its clock beats the settled line's by the configured floor** —
     * `route.avoid.candidate.minSavingPct` — or nothing at all.
     *
     * **Their passes publish no stage.** The panel's stage line narrates the answer's own build alone,
     * so a candidate's pass leaves the channel untouched — the λ passes and the fine pass are the ones
     * that move it. Nothing is lost: each candidate's pass is logged on the `OFFER` line that reports
     * its answer, its duration, its saving and whether it was kept, so every pass is logged either way.
     *
     * Their **UI is the acquisition's own** (R74): the rows and the next/prev pair read the published
     * set, so this ships the numbers and the lines those rows draw rather than waiting on a re-shell.
     */
    private suspend fun offers(
        world: AvoidWorld,
        box: BBox,
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
        edges: List<AvoidEdge>,
        openCoast: List<List<LatLng>>,
        capLatNorth: Double,
        priced: List<ZoneRing>,
        zones: List<SpeedZone>,
        sets: List<CornerSet>,
        settledSec: Double,
        limitAt: (LatLng) -> Double?
    ): List<RouteOffer> {
        val out = ArrayList<RouteOffer>(2)
        var candidates = 0
        var ran = 0
        for (candidatePass in AppConfig.routeAvoidCandidatePasses) {
            candidates++
            val dropsZones = RouteOfferSource.SPEED_ZONES in candidatePass.drops
            val dropsBand = RouteOfferSource.ZONE300 in candidatePass.drops
            // A price that is not in force has nothing to drop, so the pass would only re-draw the
            // settled line — skipped whatever `skipAbsent` says.
            if (dropsZones && !AppConfig.routeAvoidSpeedZoneEnabled) continue
            if (dropsBand && !AppConfig.routeAvoidZone300Enabled) continue
            if (AppConfig.routeAvoidCandidateSkipAbsent) {
                if (dropsZones && zones.isEmpty()) continue
                if (dropsBand && world.bandWidthM <= 0.0) continue
            }
            // **The band's price lives in the grid's base, the zones' in the per-cell read**: dropping
            // the band needs a grid of its own, while dropping the zones alone is λ = 0 on the grid the
            // solve already built — which is why the file is written cheapest first.
            val passGrid = if (dropsBand) {
                val bandField = costField(
                    world, cellM, pace, withZones = false, withBand = false, zones = zones, lambda = lambda
                )
                rasterize(
                    box, cellM, pace, marginM, edges, openCoast, capLatNorth, bandField, priced,
                    zoneOutsideMarginM = zoneOutsideMarginM
                ).also {
                    it.forceFree(start.latitude, start.longitude)
                    it.forceFree(aim.latitude, aim.longitude)
                }
            } else {
                grid
            }
            ran++
            val pass = runPass(
                world, passGrid,
                if (passGrid === grid) startCell else passGrid.cellOf(start.latitude, start.longitude),
                if (passGrid === grid) aimCell else passGrid.cellOf(aim.latitude, aim.longitude),
                start, aim, pace, cellM, marginM, zoneOutsideMarginM,
                // Cumulative: a pass that drops the zones' price prices them at λ = 0.
                if (dropsZones) 0.0 else lambda,
                limitAt, zones, sets, publishStage = false,
                approaches = EndApproaches.NONE, refusals = null
            )
            val kept = offer(candidatePass.source, pass.timed, settledSec)
            trace { offerLine(candidatePass.source, pass.timed, settledSec, kept) }
            kept?.let { out.add(it) }
        }
        trace { "OFFERS n=${out.size} candidates=$candidates ran=$ran settled=${fmt(settledSec)}s" }
        return out
    }

    /** One candidate's own line: its answer, its duration, its saving and whether it was kept — and why not. */
    private fun offerLine(
        source: RouteOfferSource,
        timed: TimedLine?,
        settledSec: Double,
        kept: RouteOffer?
    ): String {
        val duration = timed?.durationSec
        val saving = duration?.let { settledSec - it }
        val reason = when {
            timed == null -> " reason=no-path"
            kept == null -> " reason=no-saving"
            else -> ""
        }
        return "OFFER source=$source answered=${timed != null} " +
            "duration=${duration?.let { fmt(it) } ?: "none"}s " +
            "saving=${saving?.let { fmt(it) } ?: "none"}s kept=${kept != null}$reason"
    }

    /**
     * Keeps a candidate line only where it beats [settledSec] **by the configured floor** (R63) — the
     * saving, or nothing. The rule itself lives in [`routeCandidateSavingSec`], so the floor the engine
     * applies and the floor a test reads are one piece of arithmetic; what this function adds is the
     * answer a saved candidate carries.
     */
    private fun offer(
        source: RouteOfferSource,
        timed: TimedLine?,
        settledSec: Double
    ): RouteOffer? {
        if (timed == null) return null
        val savingSec = routeCandidateSavingSec(
            settledSec = settledSec,
            candidateSec = timed.durationSec,
            minSavingPct = AppConfig.routeAvoidCandidateMinSavingPct
        ) ?: return null
        return RouteOffer(
            source = source,
            points = timed.points.map { RoutePoint.of(it) },
            legTimesSec = timed.legTimesSec,
            legSpeedsMps = timed.legSpeedsMps,
            distanceM = lineLengthM(timed.points),
            durationSec = timed.durationSec,
            savingSec = savingSec
        )
    }

    /** The polyline's own length in metres — one home, read by the answer and by every offer alike. */
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
     *
     * Its one re-solve is the **crossing**: on this coast the passages that matter are a few tens of
     * metres wide, narrower than the coarse cell, so a grid built at `route.avoid.grid.cellM` cannot
     * see the way around a zone the line runs into. Every restrictive zone the line enters is therefore
     * re-solved locally at `route.avoid.fine.cellRatio` of the coarse cell, inside the zone's own box,
     * and spliced in **only where that search answers** — a crossing it cannot improve is left as it
     * stands. Its λ is the loop's own, so the refined line obeys the same budget.
     *
     * Then the whole line is pulled and snapped once more against the field that cell size prices,
     * which is this section's "a pull and a snap only".
     *
     * **One deviation, named.** The plan's swath — the coarse line's own bounding box, re-rasterized at
     * the fine cell — is **not** rasterized: nothing in this pipeline reads a grid to pull a chord. The
     * clearance [AvoidPull] tests is the world's own coast distance, exact at every cell size, so a
     * swath grid would be built and never read; the fine cell reaches the pull through the **field**
     * the pull does read, and the passage-threading this section owes is the crossing's local A*.
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
     * line is forced around it. This pass re-rasterizes a **swath** around the settled line — the coarse
     * polyline's own bounding box inflated by half the plan's swath (`outsideMarginM + cellM`) and clamped
     * to the corridor — at `route.avoid.fine.cellRatio`, re-runs the A*, and keeps the fine line **only
     * where it is strictly faster** than the incumbent. The incumbent survives every tie, so a fine pass
     * can never leave the route worse than the coarse one did.
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
        val base = costField(
            world, fineCellM, pace, withZones = false, withBand = true, zones = zones, lambda = lambda
        )
        val grid = rasterize(
            box, fineCellM, pace, marginM, edges, openCoast, capLatNorth, base, priced,
            zoneOutsideMarginM = outsideMarginM
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
            approaches = approaches, refusals = refusals
        )
        val fineTimed = pass.timed
        if (fineTimed == null) {
            trace { "FINE research answered=false spliced=no reason=no-path" }
            return line
        }
        val coarseTimed = timeLineWithLimits(line, pace, limitAt)
        val better = fineTimed.durationSec < coarseTimed.durationSec
        trace {
            "FINE research answered=true spliced=$better " +
                "fine=${fmt(fineTimed.durationSec)}s coarse=${fmt(coarseTimed.durationSec)}s"
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
     * re-solved at [fineCellM], with the two vertices flanking the cut as the splice's own ends. The box
     * is the zone's bounding box inflated by the outside margin and one coarse cell and **clamped to the
     * corridor**, so the local search can never wander past the search it refines.
     *
     * **F3 — the re-solve reaches the line's ends.** A stretch that begins at index 0 takes the raw
     * [start] as its `from`, and one that ends at the last index takes the raw [aim] as its `to`; the
     * splice then replaces the head or the tail instead of an interior stretch. It answers `null` where
     * the box meets no corridor, holds no line point, or the fine grid finds no path.
     *
     * Its instrument line says which of those it was: the box and whether the corridor clamped it, the
     * fine cell, the stretch's first and last indices, whether the local A\* answered, and whether the
     * splice was taken — with the reason when it was not.
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
        // F3: a crossing that touches an end is re-solved from the raw start or to the raw aim, and the
        // splice replaces the head or the tail instead of an interior stretch — the destination-side
        // crossing, the case a user hits, is no longer refused by the line-end guard.
        val from = if (first == 0) start else line[first - 1]
        val to = if (last == line.size - 1) aim else line[last + 1]
        val base = costField(
            world, fineCellM, pace, withZones = false, withBand = true, zones = zones, lambda = lambda
        )
        val guard = costField(
            world, fineCellM, pace, withZones = true, withBand = true, zones = zones, lambda = lambda
        )
        val grid = rasterize(
            box, fineCellM, pace, marginM, edges, openCoast, capLatNorth, base, priced,
            zoneOutsideMarginM = outsideMarginM
        )
        grid.forceFree(from.latitude, from.longitude)
        grid.forceFree(to.latitude, to.longitude)
        val search = AvoidSearch.search(
            grid,
            grid.cellOf(from.latitude, from.longitude),
            grid.cellOf(to.latitude, to.longitude),
            Units.knotsToMps(pace),
            zonePriceSec = { interiorKn, collarKn ->
                zonePriceAtLimits(
                    fineCellM, pace, interiorKn, collarKn, lambda,
                    AppConfig.routeAvoidSpeedZoneOutsideMarginCostFraction
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

    /**
     * Publishes the pipeline's boundary and logs the publication with the pass it belongs to, so the
     * panel's stage line and the log tell the same story. `null` clears it — a stage left standing
     * after the call that set it would be a lie on the panel.
     */
    private fun publish(stage: RouteStage?, pass: Int, points: List<RoutePoint>? = null) {
        _progress.value = if (stage == null) null else RouteProgress(stage, points)
        trace {
            "STAGE pass=$pass stage=${stage?.name ?: "cleared"}" +
                if (points != null) " points=${points.size}" else ""
        }
    }

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

    /** A ratio, with a zero-length line reading 0 rather than a NaN. */
    private fun shareOf(part: Double, whole: Double): Double = if (whole > 0.0) part / whole else 0.0

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
     *
     * The carve opens each intermediate cell with [AvoidGrid.openCarve], so the channel takes the
     * grid's base cost and **keeps its zone limit**: only the shore margin is the courtesy this waives,
     * and a zone standing in the berth still prices the cell the A\* reads. The returned stretch runs
     * from the end's own point — never the cell's centre — so the pull exempts the water the boat
     * actually travels, and the destination cell, being legal water already, is left untouched.
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

    /** The metres of a line whose own middle stands inside the priced 300 m band's reach. */
    private fun bandMetres(world: AvoidWorld, points: List<LatLng>): Double {
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

/** The λ loop's cap: one correction, so **two** solves at most on any one corridor. */
private const val LAMBDA_PASSES = 2

/**
 * The route mode's own log tag — the channel the refusals already ride, and the one `adb logcat -s`
 * filters on for a device pass. The instrument's level is the device's own setting:
 * `adb shell setprop log.tag.MaroRoute INFO`.
 */
private const val TAG = "MaroRoute"
