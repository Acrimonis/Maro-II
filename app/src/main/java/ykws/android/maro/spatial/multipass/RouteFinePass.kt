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
        // The coarse step is this pass's own cell — the fine grid it walks — so the shared pull samples
        // at the resolution the water here was resolved at, and never finer than its own fine step.
        val coarseStepM = fineCellM
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
            out, start, aim, marginM, coarseStepM, fineGuard, approaches, refusals
        )
        val snapped = snapToCorners(
            pulled, sets, marginM, coarseStepM, fineGuard, start, aim, approaches
        )
        val settled = MultipassPull.pull(
            snapped, start, aim, marginM, coarseStepM, fineGuard, approaches, refusals
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
            zonePriceSec = { cellSizeM, interiorKn, collarKn, bandCollarKn ->
                slowWaterPriceAt(
                    cellSizeM, pace, lambda, interiorKn, collarKn, bandCollarKn,
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
        val coarseStepM = fineCellM
        val localPath = listOf(from) + path.map { grid.center(it.row, it.col) } + listOf(to)
        val pulled = MultipassPull.pull(
            localPath, start, aim, marginM, coarseStepM, guard, approaches, refusals
        )
        val snapped = snapToCorners(pulled, sets, marginM, coarseStepM, guard, start, aim, approaches)
        val local = MultipassPull.pull(
            snapped, start, aim, marginM, coarseStepM, guard, approaches, refusals
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
        val pace = ctx.pace
        val cellM = ctx.cellM
        val marginM = ctx.marginM
        val outsideMarginM = ctx.zoneOutsideMarginM
        val zones = ctx.zones
        val fineCellM = ctx.fineCellM
        if (line.size < 2 || fineCellM <= 0.0 || fineCellM >= cellM) return line
        // What the second pass may look at is the plan's decision; that it stays inside the lookup's own
        // corridor is still the seat's, so the clamp stays here whatever a plan hands back. `avoid`'s plan
        // answers one region and the adaptive one a chain, and this pass walks **all** of them either way:
        // one box is the path this pass has always taken, several are windows on one lattice.
        val boxes = plan.secondPassRegions(line, corridor, outsideMarginM, cellM)
            .mapNotNull { clampTo(it, corridor) }
        if (boxes.isEmpty()) {
            trace { "FINE research box=empty spliced=no" }
            return line
        }
        val base = costField(world, fineCellM, pace, withZones = false, withBand = false, zones = emptyList(), lambda = lambda)
        val guard = costField(world, fineCellM, pace, withZones = true, withBand = true, zones = zones, lambda = lambda)
        val limitAt = limitAtFor(world)
        val pass = fineWalk(ctx, boxes, lambda, base, guard, trace)
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

    /**
     * **One fine walk over [boxes]** — the raster (one box on its own lattice, several as windows on the
     * corridor's), the ends' discs, then one pass through the [runner]. The two cost fields arrive from
     * the caller, so the seat prices a line with exactly the field the walk priced it with.
     */
    private suspend fun fineWalk(
        ctx: GridContext,
        boxes: List<BBox>,
        lambda: Double,
        base: RouteCostField,
        guard: RouteCostField,
        trace: (() -> String) -> Unit
    ): PassReading {
        val world = ctx.world
        val corridor = ctx.box
        val start = ctx.start
        val aim = ctx.aim
        val pace = ctx.pace
        val marginM = ctx.marginM
        val outsideMarginM = ctx.zoneOutsideMarginM
        val edges = ctx.edges
        val openCoast = ctx.openCoast
        val capLatNorth = ctx.capLatNorth
        val priced = ctx.priced
        val fineCellM = ctx.fineCellM
        val depthGateActive = AppConfig.routeAvoidDepthGateEnabled && world.depthReady
        val minDepthM = AppConfig.routeAvoidDepthGateMinM
        val walk: GridWalk
        if (boxes.size == 1) {
            // One region: the box is its own lattice, exactly as this pass has always rasterized it.
            val grid = rasterize(
                boxes.first(), fineCellM, pace, marginM, edges, openCoast, capLatNorth, base, priced,
                zoneOutsideMarginM = outsideMarginM, band = bandLaw(world)
            )
            grid.forceFree(start.latitude, start.longitude)
            grid.forceFree(aim.latitude, aim.longitude)
            openEndDisc(grid, world, start, marginM, depthGateActive, minDepthM)
            openEndDisc(grid, world, aim, marginM, depthGateActive, minDepthM)
            walk = GridWalk(
                grid, grid.cellOf(start.latitude, start.longitude),
                grid.cellOf(aim.latitude, aim.longitude), fineCellM
            )
        } else {
            // A chain: every box a window on **one** lattice, so the seam between two of them is arithmetic.
            val lattice = WalkLattice.of(corridor, fineCellM)
            val windows = boxes.map { region ->
                val snapped = lattice.snapOutward(region)
                val grid = rasterizeWindow(
                    snapped, lattice, pace, marginM, edges, openCoast, capLatNorth, base, priced,
                    zoneOutsideMarginM = outsideMarginM, band = bandLaw(world)
                )
                WalkWindow(grid, lattice.rowOf(snapped.latSouth), lattice.colOf(snapped.lonWest))
            }
            for (window in windows) {
                for (end in listOf(start, aim)) {
                    if (!window.holds(lattice.rowOf(end.latitude), lattice.colOf(end.longitude))) continue
                    window.grid.forceFree(end.latitude, end.longitude)
                    openEndDisc(window.grid, world, end, marginM, depthGateActive, minDepthM)
                }
            }
            walk = GridWalk(
                windows.first().grid,
                CellIndex(lattice.rowOf(start.latitude), lattice.colOf(start.longitude)),
                CellIndex(lattice.rowOf(aim.latitude), lattice.colOf(aim.longitude)),
                fineCellM,
                WalkWindows.onLattice(lattice, windows)
            )
        }
        return runner.runPass(
            ctx,
            walk,
            lambda,
            publishStage = false,
            trace = trace,
            guardZones = true,
            guardBand = true
        )
    }

    /**
     * **The fine reference line, for the instrument alone** — [fineReSearch]'s own walk over **`avoid`'s
     * second-pass region** rather than the plan's: the coarse line's span, grown by the outside margin and
     * one coarse cell.
     *
     * The plan's regions are exactly what caps [fineReSearch]: a corridor of half-width `w` can only find
     * an optimum standing within `w` of the coarse line, so the deviation that line answers saturates at
     * the corridor's wall and cannot say whether `w` is right. This region has no such cap, so the
     * deviation read off its line is the coarse walk's own error — the figure the corridor's half-width
     * rests on.
     *
     * It is built only where the `MaroRoute` tag's level is on, never on a shipped path: it rasterizes a
     * whole box at the fine cell, which is the cost this plan exists to remove. `null` where the pair or
     * the region leaves nothing to walk.
     */
    internal suspend fun referenceWalk(
        ctx: GridContext,
        line: List<LatLng>,
        lambda: Double,
        trace: (() -> String) -> Unit = {}
    ): PassReading? {
        val fineCellM = ctx.fineCellM
        if (line.size < 2 || fineCellM <= 0.0 || fineCellM >= ctx.cellM) return null
        val region = inflateBox(lineBBox(line), ctx.zoneOutsideMarginM + ctx.cellM)
        val box = clampTo(region, ctx.box) ?: return null
        val base = costField(
            ctx.world, fineCellM, ctx.pace, withZones = false, withBand = false,
            zones = emptyList(), lambda = lambda
        )
        val guard = costField(
            ctx.world, fineCellM, ctx.pace, withZones = true, withBand = true,
            zones = ctx.zones, lambda = lambda
        )
        return fineWalk(ctx, listOf(box), lambda, base, guard, trace)
    }
}
