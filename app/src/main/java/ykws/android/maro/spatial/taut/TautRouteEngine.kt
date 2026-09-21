package ykws.android.maro.spatial.taut

import android.app.Application
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.coastline.CoastlineRepository
import ykws.android.maro.data.depth.DepthRepository
import ykws.android.maro.data.model.BoundingBox
import ykws.android.maro.data.model.RouteEngineDetails
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.data.model.RouteResult
import ykws.android.maro.data.regulation.RegulatedZonesRepository
import ykws.android.maro.data.route.RouteSpatialAdapter
import ykws.android.maro.spatial.RouteEngine
import ykws.android.maro.spatial.RouteEngineState
import ykws.android.maro.spatial.RouteUnavailableReason

/**
 * **Why the tracer refused, in the tracer's own words** — §16 step 7's "refusals named without the
 * mesh's vocabulary".
 *
 * The seam's own failure values ([RouteResult.OutsideWater], [RouteResult.NoPath]) say what the *caller*
 * can do about a refusal; these say which of this machine's own steps gave up, which is what a reading
 * and a log line need. A mesh has no business in either vocabulary: this engine has no mesh, no stretch
 * and no bake, and a name borrowed from one would send the next reader looking for the wrong file.
 */
internal enum class TautRefusal {

    /** No depth grid: the water the wall is drawn from is not in, so nothing may be answered honestly. */
    COVERED_WATER_UNKNOWN,

    /** No way through inside the rough corridor — the harvest and the graph were built and found none. */
    NO_ROUTE_IN_CORRIDOR,

    /** The corridor was already grown to its cap and still holds no way through: the corridor is the
     *  assumption this engine rests on, and this is what it looks like when the assumption fails. */
    NO_ROUTE_AFTER_GROWTH,

    /**
     * **The boat's own end stands where the boat may not be** — land, water the 2 m gate closes, or the
     * shore offset — so **no way exists from it at all**, and the water truly offers none (item 5).
     *
     * It is named apart from the two corridor refusals because it blames a different thing: a start with
     * no edge leaving it makes the search report a corridor that holds no way through, which sends the
     * next reader looking for a route where there is no water to route on. The two harness pairs that
     * were read as corridor failures are exactly this case: their own start coordinates resolve onto land
     * (the ends' readings are in the harness's own refusal lines).
     */
    START_OFF_WATER,

    /**
     * **The destination's own end stands where the boat may not be, and no water is anywhere near it.**
     *
     * The aim is *not* refused for standing on land: an aim off the water is resolved to the nearest
     * corner the corridor's water allows, which is the pin's own rule and is kept. This is the case where
     * that resolution has nothing to resolve to — the graph falls back to the raw aim, every edge from it
     * is refused, and the search reports `NO_ROUTE_IN_CORRIDOR` for an end that has no water at all. The
     * end is named instead, the way the start already is (item 5's second half).
     */
    AIM_OFF_WATER
}

/**
 * **The replacement route engine: a corridor-scoped visibility graph over the obstacles' own corners,
 * an A\* that buys the fastest line, and a fit-checked easing at every corner.**
 *
 * It answers the same question the mesh engine answers, through the same seam and for the same readers,
 * and it walks a different machine: the obstacles are harvested from the live coastline, the depth grid's
 * own 2 m contour and the priced zones; they are abstracted **one-sided, outward**, dilated by the berth
 * and validated by exact segment distance; the graph's nodes are the corners a taut line bends on; the
 * edge price is the clock and nothing else; and the corner is drawn as an easing spiral, an arc, or — only
 * where neither fits — the sharp vertex the search found.
 *
 * **The corridor is measured, not merely guessed.** Its rough box is the A→B segment inflated by a
 * nautical mile plus the berth, and it is then **sized from the obstacles it must contain**: the union of
 * the extents the harvest was handed, clamped to a bounded reach and clipped to the depth grid, so the
 * tangent corners of a straddled obstacle's dilated wall are inside the corridor by construction. An
 * empty answer inside it is re-run on a box grown once, and a second empty answer is a refusal named in
 * [TautRefusal]'s own words — a value the caller can read, never a silent nothing.
 *
 * **Readiness is both layers the wall comes from, and it is honest through both doors** (§17 item 3).
 * The wall is land, the 2 m contour where the soundings are fine, and the shore offset where they are
 * not — so **two** layers have to be in, and each of them answers permissively while it is absent: a
 * missing coastline reads as water, and a missing depth grid as no sounding at all. [prepare] therefore
 * **triggers the grid's own load**, and the adapter triggers the coastline's, and readiness is read
 * through [`TautWorld.readiness`] — **one member every world answers** — by [prepare] *and* by [route].
 * A route asked for inside that window is refused by name ([RouteUnavailableReason.COASTLINE_NOT_LOADED]
 * or [RouteUnavailableReason.DEPTH_NOT_LOADED]) rather than drawn over land as an ordinary route. That is
 * the "permissive default" the seam's contract leaves to each engine, and this one declines it on both
 * layers rather than one.
 *
 * **Its cost is reported in three parts** (§16 step 1): the harvest, the graph build and the search are
 * timed apart and printed beside the pair and node counts, so a correction can be judged against the
 * figure it moves rather than against one number with three causes.
 *
 * **One corridor's terrain is kept, and reused while it holds what a cold search would build from the
 * next one** (§19.4, b1 — and the licence corrected in §19.5 C2). A drag moves the aim by metres while the
 * corridor is kilometres wide, and the obstacles, the zones and the band are a function of the box and of
 * the world rather than of the aim — which is why they may be kept and the aim's own vertex, pairs,
 * search and line may not. [TautTerrain] is the entry, its [TautTerrainKey] carries the world's own
 * [TautWorld.generation] as well as the numbers that shaped it, and the reading that this happened is
 * `terrainReused` beside the harvest's milliseconds rather than anything a reader of the line can see.
 *
 * What a reuse promises about the answer is **never narrower and never dearer**, not "unchanged": on a
 * reuse the graph is built over the kept box, so a moved aim searches a graph that is a superset of a cold
 * search's, and a taut line over a superset may differ while being no slower. Whether it does is a
 * measurement rather than an argument (§19.5 C3), which is why the moved-aim reading exists.
 *
 * Cancellation is cooperative, checked between the steps of the build and of the search, so a flung map
 * drops the search in flight rather than queueing one per aim.
 */
class TautRouteEngine internal constructor(

    /** The world it reads: the live coastline, the zones, and the depth the wall comes from. */
    private val world: TautWorld,

    /**
     * The live layers' own load, when [world] is the adapter over them: a route reads the coastline, the
     * regulation and the depth grid, and an adapter that has not read them cannot answer honestly. Null
     * when the world was handed in ready.
     */
    private val prepareWorld: (suspend () -> Unit)?,

    private val berthM: () -> Double = { AppConfig.routeZoneBerthM.toDouble() },
    private val shoreOffsetM: () -> Double = { AppConfig.routeShoreOffsetM.toDouble() },
    private val lateralAccelMps2: () -> Double = { AppConfig.routeTurnLateralAccelMps2.toDouble() },

    /** §12.3's longitudinal limit — the brake-and-accelerate pair's own key, read live beside the other two. */
    private val longitudinalAccelMps2: () -> Double =
        { AppConfig.routeTurnLongitudinalAccelMps2.toDouble() },
    private val corridorGrowth: Int = DEFAULT_CORRIDOR_GROWTH,
    /**
     * Where a refusal is reported.
     *
     * The default writes a log line, and it is deliberately tolerant of the logger itself: a search that
     * *did* answer must never be lost because a diagnostic threw — an unmocked `android.util.Log` under a
     * unit test is the everyday case, and it is not worth a route.
     */
    private val warn: (String) -> Unit = { message -> runCatching { Log.w(TAG, message) } }
) : RouteEngine {

    /**
     * **The one terrain kept between searches** (§19.4) — the corridor's coast, zones and band for the
     * box it was harvested for, and nothing that is part of an answer.
     *
     * A search is *started* one at a time and its predecessor is cancelled and **not joined**, so two
     * searches can overlap and a terrain written by one is read by the other. That is safe here because
     * the entry is published whole rather than in pieces and because everything it holds answers the same
     * question whoever asks: the obstacles' two memos are concurrent maps (a place has one water answer),
     * and the wall index's and the band's own dedupe scratch is stamped **per query** rather than per
     * instance, so a search and the abandoned one beside it can never read each other's marks as their own
     * (§19.5 C1). The counters those two carry are the instrumentation an overlap may at worst misreport.
     */
    @Volatile
    private var keptTerrain: TautTerrain? = null

    private val _state = MutableStateFlow<RouteEngineState>(RouteEngineState.NotReady)

    override val state: StateFlow<RouteEngineState> = _state.asStateFlow()

    /**
     * The gate, read off the world's own readiness member rather than off one layer's box.
     *
     * The load is triggered first — both layers, each through its own home — and then the world is asked
     * which of them is still missing, so the reason a user reads names the layer that is actually absent.
     */
    override suspend fun prepare(): RouteEngineState {
        prepareWorld?.invoke()
        _state.value = when (world.readiness) {
            TautWorldReadiness.READY -> RouteEngineState.Ready
            TautWorldReadiness.COASTLINE_MISSING ->
                RouteEngineState.Unavailable(RouteUnavailableReason.COASTLINE_NOT_LOADED)
            TautWorldReadiness.DEPTH_MISSING ->
                RouteEngineState.Unavailable(RouteUnavailableReason.DEPTH_NOT_LOADED)
        }
        return _state.value
    }

    override suspend fun route(
        start: RoutePoint,
        aim: RoutePoint,
        cruiseSpeedKn: Double
    ): RouteResult {
        // **The second door onto the same gate.** [prepare] is what moves the state a toggle reads, but a
        // search can also be reached without it — a recompute, a retry, a caller that never asked — so the
        // world's own readiness member is read here too, and a layer that is not in refuses rather than
        // being priced permissively as open water.
        val missing = world.readiness
        if (missing != TautWorldReadiness.READY) {
            val layer = if (missing == TautWorldReadiness.COASTLINE_MISSING) "coastline" else "depth grid"
            warn("$TAG: ${TautRefusal.COVERED_WATER_UNKNOWN.name} — no $layer to draw the wall from")
            return RouteResult.OutsideWater
        }
        val berth = berthM()
        val shoreOffset = shoreOffsetM()
        val accel = lateralAccelMps2()
        val longitudinal = longitudinalAccelMps2()
        return withContext(Dispatchers.Default) {
            val context = currentCoroutineContext()
            var harvestNanos = 0L
            var graphNanos = 0L
            var searchNanos = 0L
            var growth = 0
            var expanded = false
            while (growth <= corridorGrowth) {
                context.ensureActive()
                val rough = TautGraph.corridorBox(world, start, aim, growth, berth)
                // **The kept terrain's own licence** (§19.4, and §19.5 C2 for the box it is read on): the
                // same shaping numbers — the world's own generation among them, so a layer that moved
                // invalidates the entry — and, for the box, the **largest corridor a cold search could
                // build from this rough guess**. Testing the guess itself licensed a reuse where cold
                // builds wider, and refused one where the kept box was the wider of the two; the cold
                // bound is the one box on which "the reuse paid no harvest" implies "the reused corridor
                // is never narrower than cold's". A reuse pays no harvest at all, which is the whole of
                // the 512 ms this step is worth.
                val wanted = TautTerrainKey.of(
                    generation = world.generation,
                    box = rough,
                    berthM = berth,
                    shoreOffsetM = shoreOffset
                )
                val kept = keptTerrain
                val terrain: TautTerrain
                val box: BoundingBox
                val harvestAt = System.nanoTime()
                // **The flag belongs to the attempt that answers** (§19.5 C6): a reuse whose search finds
                // nothing is followed by a grown attempt that harvests, and the dossier must describe the
                // attempt the line came from rather than the loop's history.
                var reusedHere = false
                if (kept != null && kept.key.shapedLike(wanted) &&
                    kept.holds(TautGraph.coldBoundBox(world, rough, start, aim, berth))
                ) {
                    terrain = kept
                    // The kept box rather than the rough one: the obstacles, the zone corners and the
                    // band all answer for the box they were harvested for, and handing the graph a
                    // narrower box would not narrow them back.
                    box = kept.box
                    reusedHere = true
                } else {
                    // The obstacles are harvested first and the terrain is entered **over** them: the sizing
                    // below can grow the box, and a terrain built for the rough box would then pay for a
                    // zone and a band harvest it immediately discards. This way a first search does exactly
                    // the work it did before b1, and a search that reuses pays none of it.
                    var obstacles = TautObstacles.harvest(
                        world, rough, berth, shoreOffset,
                        cancelCheck = { context.ensureActive() }
                    )
                    var harvestBox = rough
                    // The box is sized from what the harvest found, **once per growth attempt**: the union
                    // of the harvested extents is the corridor's licence to hold them, and one measured
                    // step is enough because the reach it is clamped to is measured from the guess rather
                    // than from the running box. The bound is per attempt and not per route on purpose — a
                    // growth pass that skipped its own sizing would be re-running the same too-small box,
                    // which is the refusal this correction exists to remove.
                    var sizings = 0
                    while (sizings < CORRIDOR_EXPANSIONS) {
                        val sized = TautGraph.sizedFromObstacles(
                            world = world,
                            rough = rough,
                            extent = obstacles.harvestedExtent,
                            start = start,
                            aim = aim,
                            berthM = berth
                        )
                        if (sameBox(sized, rough)) break
                        harvestBox = sized
                        obstacles = TautObstacles.harvest(
                            world, harvestBox, berth, shoreOffset,
                            cancelCheck = { context.ensureActive() }
                        )
                        sizings++
                        expanded = true
                    }
                    terrain = TautTerrain.over(
                        world, harvestBox, obstacles, berth, shoreOffset,
                        cancelCheck = { context.ensureActive() }
                    )
                    box = harvestBox
                    keptTerrain = terrain
                }
                harvestNanos += System.nanoTime() - harvestAt
                val obstacles = terrain.obstacles

                // **The boat's own end, refused by name before the map is built** (item 5). A start the
                // boat may not be at — on land, in water the 2 m gate closes, or inside the shore offset —
                // is a vertex with no edge leaving it, so the search would answer `NoPath` and blame the
                // corridor for the end's own position. There is no way around this one because there is no
                // water to route on: the refusal is the honest answer, and the tracer says it in its own
                // words rather than borrowing the corridor's.
                if (!obstacles.traversable(start.latitude, start.longitude)) {
                    warn(
                        "$TAG: ${TautRefusal.START_OFF_WATER.name} — the start stands where the boat may " +
                            "not be (land, water below the ${TautObstacles.SHALLOW_GATE_M} m gate, or the " +
                            "$shoreOffset m shore offset); no way exists from it"
                    )
                    return@withContext RouteResult.OutsideWater
                }

                // **The aim's own end, named the same way** (item 5's second half). An aim the boat may
                // not stand at is resolved to the nearest corner the corridor's water allows — the pin's
                // own rule — but where there is no such corner the graph falls back to the raw aim, every
                // edge from it is refused, and the search reports a corridor that holds no way through:
                // a misattribution, since the water offers no way *to* that end. Named before the graph
                // is built, for the same reason the start is.
                if (!obstacles.traversable(aim.latitude, aim.longitude) &&
                    TautGraph.nearestTraversable(obstacles, aim, box) == null
                ) {
                    warn(
                        "$TAG: ${TautRefusal.AIM_OFF_WATER.name} — the aim stands where the boat may not " +
                            "be (land, water below the ${TautObstacles.SHALLOW_GATE_M} m gate, or the " +
                            "$shoreOffset m shore offset) and no traversable corner of the corridor is " +
                            "near it; no way exists to it"
                    )
                    return@withContext RouteResult.OutsideWater
                }

                val graphAt = System.nanoTime()
                val graph = TautGraph.build(
                    world, terrain, start, aim, cruiseSpeedKn,
                    cancelCheck = { context.ensureActive() }
                )
                graphNanos += System.nanoTime() - graphAt

                context.ensureActive()
                val searchAt = System.nanoTime()
                val search = TautSearch(
                    world = world,
                    obstacles = obstacles,
                    graph = graph,
                    cruiseSpeedKn = cruiseSpeedKn,
                    lateralAccelMps2 = accel,
                    warn = warn,
                    longitudinalAccelMps2 = longitudinal,
                    cancelCheck = { context.ensureActive() }
                )
                val outcome = search.run()
                searchNanos += System.nanoTime() - searchAt
                if (outcome != null) {
                    return@withContext success(
                        outcome = outcome,
                        obstacles = obstacles,
                        graph = graph,
                        harvestNanos = harvestNanos,
                        graphNanos = graphNanos,
                        searchNanos = searchNanos,
                        growth = growth,
                        expanded = expanded,
                        reused = reusedHere
                    )
                }
                warn(
                    "$TAG: ${TautRefusal.NO_ROUTE_IN_CORRIDOR.name} at growth $growth — " +
                        "${graph.vertices.size} vertex(es), ${graph.edgeCount} edge(s), " +
                        "${graph.candidatePairs} pair(s) examined"
                )
                growth++
            }
            warn(
                "$TAG: ${TautRefusal.NO_ROUTE_AFTER_GROWTH.name} — the corridor was grown to " +
                    "${corridorGrowth}× and still holds no way through; over $growth attempt(s): " +
                    "harvest ${harvestNanos / 1_000_000} ms, graph ${graphNanos / 1_000_000} ms, " +
                    "search ${searchNanos / 1_000_000} ms"
            )
            RouteResult.NoPath
        }
    }

    /** Whether two boxes are the same box — by their bounds, never by their identity. */
    private fun sameBox(first: BoundingBox, second: BoundingBox): Boolean =
        first.latSouth == second.latSouth && first.latNorth == second.latNorth &&
            first.lonWest == second.lonWest && first.lonEast == second.lonEast

    /** The answer, with the engine's own counts riding in its dossier rather than the shared result. */
    private fun success(
        outcome: TautSearch.Outcome,
        obstacles: TautObstacles,
        graph: TautGraph,
        harvestNanos: Long,
        graphNanos: Long,
        searchNanos: Long,
        growth: Int,
        expanded: Boolean,
        reused: Boolean
    ): RouteResult.Success {
        val details: RouteEngineDetails = TautRouteDetails(
            vertexCount = graph.vertices.size,
            cornerCount = graph.cornerCount,
            edgeCount = graph.edgeCount,
            candidatePairs = graph.candidatePairs,
            wallSegmentCount = obstacles.wallSegmentCount,
            bandSegmentCount = graph.bandSegmentCount,
            pushedShortSegments = obstacles.pushedShortSegments,
            deletedAreaM2 = obstacles.deletedAreaM2,
            pricedSec = outcome.pricedSec,
            berthPriceSec = outcome.berthPriceSec,
            longitudinalSec = outcome.longitudinalSec,
            crossings = outcome.crossings,
            inMarginM = outcome.inMarginM,
            legLimitKn = outcome.legLimitKn,
            spiralCorners = outcome.spiralCorners,
            arcCorners = outcome.arcCorners,
            sharpCorners = outcome.sharpCorners,
            sharpByGuard = outcome.sharpByGuard,
            sharpByBuild = outcome.sharpByBuild,
            sharpByCutback = outcome.sharpByCutback,
            sharpByWater = outcome.sharpByWater,
            sharpByRule = outcome.sharpByRule,
            sharpChargeSec = outcome.sharpChargeSec,
            fitsBuilt = outcome.fitsBuilt,
            fitMillis = outcome.fitMillis,
            clockCalls = outcome.clockCalls,
            clockMillis = outcome.clockMillis,
            candidatesBuilt = outcome.candidatesBuilt,
            candidateBuildMillis = outcome.candidateBuildMillis,
            candidateWaterMillis = outcome.candidateWaterMillis,
            judgeAsked = obstacles.judgeAsked,
            judgeMemoHits = obstacles.judgeMemoHits,
            largestTurnRadiusM = outcome.largestRadiusM,
            chordDeviationM = outcome.chordDeviationM,
            samplingSec = outcome.samplingSec,
            zonePricedRun = outcome.zonePricedRun,
            corridorGrowth = growth,
            corridorExpanded = expanded,
            terrainReused = reused,
            harvestMillis = harvestNanos / 1_000_000,
            graphMillis = graphNanos / 1_000_000,
            searchMillis = searchNanos / 1_000_000,
            nodesExpanded = outcome.nodesExpanded
        )
        // **The phase split, on the channel the refusals already use** (§19.2 item 3). A refusal says its
        // attempts; a *success* says nothing at all unless this line is here, and the counters are the only
        // witness of a cost that lives inside the run rather than around it — which is the whole reason the
        // device reading needs them rather than the three phase figures alone.
        warn(
            "$TAG: route ${graph.vertices.size} vertex(es), ${graph.edgeCount} edge(s), " +
                "${graph.candidatePairs} pair(s) — terrain " +
                "${if (reused) "reused" else "harvested"}, harvest ${harvestNanos / 1_000_000} ms, graph " +
                "${graphNanos / 1_000_000} ms, search ${searchNanos / 1_000_000} ms · fits " +
                "${outcome.fitsBuilt} (${outcome.fitMillis} ms) · clock ${outcome.clockCalls} call(s) " +
                "(${outcome.clockMillis} ms) · candidates ${outcome.candidatesBuilt} (draw " +
                "${outcome.candidateBuildMillis} ms, water ${outcome.candidateWaterMillis} ms) · judge " +
                "${obstacles.judgeAsked} asked, ${obstacles.judgeMemoHits} from the memo"
        )
        return RouteResult.Success(
            points = outcome.points,
            legTimesSec = outcome.legTimesSec,
            distanceM = outcome.distanceM,
            durationSec = outcome.durationSec,
            inBand = outcome.inBand,
            destinationMoved = graph.destinationMoved,
            forcedCrossingZoneNames = outcome.forcedCrossingZoneNames,
            details = details
        )
    }

    companion object {

        /** The log tag this engine's warnings carry — a log message, not user-facing text. */
        const val TAG = "MaroTautRoute"

        /** How many times the corridor's rough guess is grown before the refusal is reported. */
        const val DEFAULT_CORRIDOR_GROWTH = 1

        /**
         * How many times the corridor may be re-harvested after being sized from its own obstacles.
         *
         * One, and it is a bound rather than a loop: the reach the union is clamped to is measured from
         * the rough guess, so the second harvest is measured against the same reference and the sizing
         * cannot chase itself outward.
         */
        const val CORRIDOR_EXPANSIONS = 1

        /**
         * The app's engine: the corridor tracer over the very coastline, regulation and depth instances
         * the app already holds, so a route prices and walls itself from the live world rather than from a
         * baked artefact.
         */
        fun overBundle(
            application: Application,
            coastline: CoastlineRepository,
            regulatedZones: RegulatedZonesRepository,
            depth: DepthRepository
        ): TautRouteEngine {
            // The depth layer reads its grid only when its own screen asks for it; the router needs it on
            // the first search, so the reader is pointed at the app's assets here rather than from a
            // second place that could disagree about which file is the region's.
            depth.setCacheDir(application)
            val adapter = RouteSpatialAdapter(coastline, regulatedZones, depth = depth)
            return TautRouteEngine(world = adapter, prepareWorld = adapter::loadIfNeeded)
        }

        /**
         * An engine over a world the caller already holds — the readings' own entry point, so a harness
         * measures the shipped engine rather than a second implementation of it.
         */
        fun overWorld(
            world: TautWorld,
            prepareWorld: (suspend () -> Unit)? = null
        ): TautRouteEngine = TautRouteEngine(world = world, prepareWorld = prepareWorld)
    }
}
