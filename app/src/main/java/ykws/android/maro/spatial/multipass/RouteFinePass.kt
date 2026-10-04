package ykws.android.maro.spatial.multipass

import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.data.regulation.SpeedZone
import ykws.android.maro.spatial.Units

/**
 * **The refinement along the settled line** — the field-first refinement and the crossing re-solve,
 * plus the fine re-search that re-walks a swath at a finer cell, lifted out of the pass pipeline so
 * the after-the-loop work is a named seat of its own.
 *
 * The [plan] decides what the second pass may re-rasterize; the [runner] performs the re-walk, so this
 * seat never re-implements the pass it splices. Both are composed once by the engine.
 */
internal class RouteFinePass(
    private val plan: RouteGridPlan = UniformGridPlan,
    private val runner: RoutePassRunner = RoutePassRunner()
) {

    /**
     * The λ-priced soft cost of [points] against [field] — the same per-segment walk the pull's
     * `softPricePrefix` uses, summed to the whole line, so this comparison prices exactly what the
     * search and the pull priced.
     */
    internal fun pricedLineCost(points: List<LatLng>, marginM: Double, field: RouteCostField): Double {
        if (!field.hasSoft || points.size < 2) return 0.0
        var total = 0.0
        for (i in 1 until points.size) {
            total += MultipassPull.softPriceSec(points[i - 1], points[i], marginM, field)
        }
        return total
    }

    /**
     * **The fine pass (§5)** — the refinement along the settled answer, run once and **after** the λ
     * loop, so the loop never pays for it.
     */
    internal suspend fun finePass(
        ctx: GridContext,
        line: List<LatLng>,
        lambda: Double,
        trace: (() -> String) -> Unit = {}
    ): List<LatLng> {
        val world = ctx.world
        val corridor = ctx.box
        val start = ctx.start
        val aim = ctx.aim
        val pace = ctx.pace
        val cellM = ctx.cellM
        val marginM = ctx.marginM
        val outsideMarginM = ctx.zoneOutsideMarginM
        val edges = ctx.edges
        val openCoast = ctx.openCoast
        val capLatNorth = ctx.capLatNorth
        val priced = ctx.priced
        val zones = ctx.zones
        val sets = ctx.sets
        val approaches = ctx.approaches
        val refusals = ctx.refusals
        val fineCellM = ctx.fineCellM
        if (line.size < 2 || fineCellM <= 0.0 || fineCellM >= cellM) return line
        var out = line
        for (zone in zones) {
            if (zonePriceSec(cellM, pace, zone.speedLimitKn, lambda) <= 0.0) continue
            if (!lineEntersZone(out, zone)) continue
            out = solveCrossing(
                world, corridor, out, zone, start, aim, pace, cellM, fineCellM, marginM, outsideMarginM,
                lambda, edges, openCoast, capLatNorth, priced, zones, sets, approaches, refusals, trace
            ) ?: out
        }
        val fineGuard =
            costField(world, fineCellM, pace, withZones = true, withBand = true, zones = zones, lambda = lambda)
        val pulled = MultipassPull.pull(
            out, start, aim, marginM, fineGuard, approaches, refusals
        )
        val snapped = snapToCorners(pulled, sets, marginM, fineGuard, start, aim, approaches)
        val settled = MultipassPull.pull(
            snapped, start, aim, marginM, fineGuard, approaches, refusals
        )
        trace { "FINE settled points=${settled.size} fineCell=${fmt(fineCellM)}m" }
        return settled
    }

    /**
     * The crossing's own **local A\***: the stretch of [line] standing inside [zone]'s box, cut out and
     * re-solved at [fineCellM], with the two vertices flanking the cut as the splice's own ends.
     */
    internal suspend fun solveCrossing(
        world: MultipassWorld,
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
        edges: List<MultipassEdge>,
        openCoast: List<List<LatLng>>,
        capLatNorth: Double,
        priced: List<ZoneRing>,
        zones: List<SpeedZone>,
        sets: List<CornerSet>,
        approaches: EndApproaches,
        refusals: PullRefusals?,
        trace: (() -> String) -> Unit = {}
    ): List<LatLng>? {
        val inflated = inflateBox(zone.bbox(), outsideMarginM + cellM)
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
        val search = MultipassSearch.search(
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
        val pulled = MultipassPull.pull(
            localPath, start, aim, marginM, guard, approaches, refusals
        )
        val snapped = snapToCorners(pulled, sets, marginM, guard, start, aim, approaches)
        val local = MultipassPull.pull(
            snapped, start, aim, marginM, guard, approaches, refusals
        )
        val localCost = pricedLineCost(local, marginM, guard)
        val coarseCost = pricedLineCost(line.subList(first, last + 1), marginM, guard)
        if (localCost > coarseCost) {
            trace {
                "FINE zone=${zone.name} spliced=no reason=worse " +
                    "local=${fmt(localCost)} coarse=${fmt(coarseCost)}"
            }
            return line
        }
        val out = ArrayList<LatLng>(line.size + local.size)
        if (first > 0) out.addAll(line.subList(0, first))
        out.addAll(if (first > 0) local.subList(1, local.size) else local)
        if (last < line.size - 1) out.addAll(line.subList(last + 2, line.size))
        trace { "$head first=$first last=$last local=yes spliced=yes points=${out.size}" }
        trace {
            "FINE splice zoneM=${fmt(zoneMetres(listOf(zone), local))} " +
                "coarse zoneM=${fmt(zoneMetres(listOf(zone), line.subList(first, last + 1)))}"
        }
        return out
    }

    /**
     * **D8 — the fine re-search along the settled line.** The coarse grid closes any passage narrower
     * than roughly two cells, so a narrow channel the search would rather thread reads as land and the
     * line is forced around it. This pass re-rasterizes a **swath** around the settled line at
     * `route.avoid.fine.cellRatio`, re-runs the A*, and keeps the fine line **only where it is no worse
     * in its priced cost** than the incumbent.
     *
     * The fine raster and its finer cell travel on as a [GridWalk] of their own, so the re-walk is the
     * same door as the coarse pass with a different grid in its hands.
     */
    internal suspend fun fineReSearch(
        ctx: GridContext,
        line: List<LatLng>,
        lambda: Double,
        trace: (() -> String) -> Unit = {}
    ): List<LatLng> {
        val world = ctx.world
        val corridor = ctx.box
        val start = ctx.start
        val aim = ctx.aim
        val pace = ctx.pace
        val cellM = ctx.cellM
        val marginM = ctx.marginM
        val outsideMarginM = ctx.zoneOutsideMarginM
        val edges = ctx.edges
        val openCoast = ctx.openCoast
        val capLatNorth = ctx.capLatNorth
        val priced = ctx.priced
        val zones = ctx.zones
        val sets = ctx.sets
        val approaches = ctx.approaches
        val refusals = ctx.refusals
        val fineCellM = ctx.fineCellM
        if (line.size < 2 || fineCellM <= 0.0 || fineCellM >= cellM) return line
        // What the second pass may look at is the plan's decision; that it stays inside the lookup's own
        // corridor is still the seat's, so the clamp stays here whatever a plan hands back. `avoid`'s
        // plan answers one region and this pass reads the first of them — the multi-region walk is a
        // later step, not this extraction.
        val swathBoxes = plan.secondPassRegions(line, corridor, outsideMarginM, cellM)
        val box = swathBoxes.firstOrNull()?.let { clampTo(it, corridor) }
        if (box == null) {
            trace { "FINE research box=empty spliced=no" }
            return line
        }
        val base = costField(world, fineCellM, pace, withZones = false, withBand = false, zones = emptyList(), lambda = lambda)
        val guard = costField(world, fineCellM, pace, withZones = true, withBand = true, zones = zones, lambda = lambda)
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
        val pass = runner.runPass(
            ctx,
            GridWalk(
                grid, grid.cellOf(start.latitude, start.longitude),
                grid.cellOf(aim.latitude, aim.longitude), fineCellM
            ),
            lambda,
            publishStage = false,
            trace = trace,
            guardZones = true,
            guardBand = true
        )
        val fineTimed = pass.timed
        if (fineTimed == null) {
            trace { "FINE research answered=false spliced=no reason=no-path" }
            return line
        }
        val coarseTimed = timeLineWithLimits(
            line, pace, limitAt, clockSampleM(cellM, fineCellM)
        )
        val fineCost = pricedLineCost(pass.line, marginM, guard)
        val coarseCost = pricedLineCost(line, marginM, guard)
        val better = fineCost <= coarseCost
        trace {
            "FINE research answered=true spliced=$better " +
                "fine=${fmt(fineTimed.durationSec)}s coarse=${fmt(coarseTimed.durationSec)}s " +
                "fineShare=${fmt(zoneSlowShare(fineTimed, pace), 2)} " +
                "coarseShare=${fmt(zoneSlowShare(coarseTimed, pace), 2)} " +
                "fineCost=${fmt(fineCost)} coarseCost=${fmt(coarseCost)}"
        }
        return if (better) pass.line else line
    }
}
