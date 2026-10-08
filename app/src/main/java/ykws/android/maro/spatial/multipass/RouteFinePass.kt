package ykws.android.maro.spatial.multipass

import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.data.regulation.SpeedZone
import ykws.android.maro.spatial.Units

/**
 * **The refinement along the settled line** — the field-first re-tension and the crossing re-solve,
 * lifted out of the pass pipeline so the after-the-loop work is a named seat of its own.
 *
 * Both parts are impact-bearing: the re-tension runs on every route and a crossing re-solve can replace
 * a zone's own stretch, so the engine composes this seat once and this seat never re-implements the
 * pass it splices.
 */
internal class RouteFinePass {

    /**
     * The λ-priced soft cost of [points] against the [setup]'s field — the same per-segment walk the
     * pull's `softPricePrefix` uses, summed to the whole line, so this comparison prices exactly what
     * the search and the pull priced. The setup arrives from the call that resolved the line, so this
     * site inherits the walk's own price step rather than inventing a partition of its own.
     *
     * It reads the setup's margin, price step and field alone, and folds the water into a tally-free
     * context here — a line cost counts and caches nothing.
     */
    internal fun pricedLineCost(setup: PullSetup, points: List<LatLng>): Double {
        if (!setup.field.hasSoft || points.size < 2) return 0.0
        val ctx = setup.context()
        var total = 0.0
        for (i in 1 until points.size) {
            total += MultipassPull.softPriceSec(points[i - 1], points[i], ctx)
        }
        return total
    }

    /**
     * **The fine pass (§5)** — the refinement along the settled answer, run once and **after** the λ
     * loop, so the loop never pays for it. Both of its pulls report their own tallies, so a trace taken
     * the way the device takes it says whether this walk's price read grouped at all on its fine grid.
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
        // This pass walks one fine grid, so both its clearance step and its price step are that grid's
        // own cell — its interior is its fine layer, and the price step is never under the fine one.
        val coarseStepM = fineCellM
        val priceStepM = fineCellM
        var out = line
        // The depth band, where the plan prices the shallow wall: the same law the runner's search and
        // guard read, built once here so the crossing re-solves ride it too.
        val depthBand =
            if (ctx.depthBandActive && ctx.depthGateActive) depthBandOf(world, ctx.minDepthM, fineCellM) else null
        for (zone in zones) {
            if (zonePriceSec(cellM, pace, zone.speedLimitKn, lambda) <= 0.0) continue
            if (!lineEntersZone(out, zone)) continue
            out = solveCrossing(
                world, corridor, out, zone, start, aim, pace, cellM, fineCellM, marginM, outsideMarginM,
                lambda, edges, openCoast, capLatNorth, priced, zones, sets, approaches, refusals, depthBand, trace
            ) ?: out
        }
        val fineGuard =
            costField(
                world, fineCellM, pace, withZones = true, withBand = true, zones = zones, lambda = lambda,
                withDepthBand = ctx.depthBandActive
            )
        val setup = PullSetup(marginM, coarseStepM, priceStepM, fineGuard, start, aim, approaches)
        // Both pulls report the tallies the runner's own PULL and FINAL lines carry, so one pass taken
        // the way the device takes it says whether the price walk grouped at all on this fine grid.
        val pullTiming = PullTiming()
        val pulled = MultipassPull.pull(setup, out, refusals, pullTiming)
        trace {
            "FINE PULL points=${pulled.size} priceReads=${pullTiming.priceReads} marks=${pullTiming.marks} " +
                "memoPriceHits=${pullTiming.memoPriceHits} " +
                "stepM=${fmt(pullTiming.stepM)} priceStepM=${fmt(pullTiming.priceStepM)}"
        }
        val snapped = snapToCorners(setup, sets, pulled)
        val finalTiming = PullTiming()
        val settled = MultipassPull.pull(setup, snapped, refusals, finalTiming)
        trace {
            "FINE settled points=${settled.size} fineCell=${fmt(fineCellM)}m " +
                "priceReads=${finalTiming.priceReads} marks=${finalTiming.marks} " +
                "memoPriceHits=${finalTiming.memoPriceHits} " +
                "stepM=${fmt(finalTiming.stepM)} priceStepM=${fmt(finalTiming.priceStepM)}"
        }
        return settled
    }

    /**
     * The crossing's own **local A\***: the stretch of [line] standing inside [zone]'s box, cut out and
     * re-solved at [fineCellM], with the two vertices flanking the cut as the splice's own ends. Each
     * re-solve prints its own ms and box — and its local pull its tallies — so a route entering two
     * zones separates them by their own figures.
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
        depthBand: DepthBand? = null,
        trace: (() -> String) -> Unit = {}
    ): List<LatLng>? {
        val zoneStartNs = System.nanoTime()
        val inflated = inflateBox(zone.bbox(), outsideMarginM + cellM)
        val box = clampTo(inflated, corridor)
        val clamped = box != null && box != inflated
        if (box == null) {
            trace {
                "FINE zone=${zone.name} box=empty clamped=true fineCell=${fmt(fineCellM)}m " +
                    "first=n/a last=n/a local=no spliced=no reason=box-empty " +
                    "ms=${fmt(msSince(zoneStartNs))}"
            }
            return null
        }
        val first = line.indexOfFirst { inBox(box, it) }
        val last = line.indexOfLast { inBox(box, it) }
        val head = "FINE zone=${zone.name} box=${boxText(box)} clamped=$clamped fineCell=${fmt(fineCellM)}m"
        if (first < 0 || last < 0) {
            trace {
                "$head first=$first last=$last local=no spliced=no reason=no-stretch " +
                    "ms=${fmt(msSince(zoneStartNs))}"
            }
            return null
        }
        val from = if (first == 0) start else line[first - 1]
        val to = if (last == line.size - 1) aim else line[last + 1]
        val base = costField(world, fineCellM, pace, withZones = false, withBand = false, zones = emptyList(), lambda = lambda)
        val guard = costField(
            world, fineCellM, pace, withZones = true, withBand = true, zones = zones, lambda = lambda,
            withDepthBand = depthBand != null
        )
        // This crossing walks one fine grid, so both its steps are that grid's own cell; the water is
        // built once here, immediately after the field, and never cached.
        val coarseStepM = fineCellM
        val priceStepM = fineCellM
        val setup = PullSetup(marginM, coarseStepM, priceStepM, guard, start, aim, approaches)
        val grid = rasterize(
            box, fineCellM, pace, marginM, edges, openCoast, capLatNorth, base, priced,
            zoneOutsideMarginM = outsideMarginM, band = bandLaw(world), depthBand = depthBand
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
            },
            depthK = if (depthBand != null) lambda else 0.0
        )
        val path = search.path
        if (path == null) {
            trace {
                "$head first=$first last=$last local=no spliced=no reason=local-no-path " +
                    "expansions=${search.expansions} passable=${search.passableCells} " +
                    "aimClosed=${search.aimClosed} ms=${fmt(msSince(zoneStartNs))}"
            }
            return null
        }
        val localPath = listOf(from) + path.map { grid.center(it.row, it.col) } + listOf(to)
        // The crossing's own local pull reports the same tallies the runner's PULL line carries, so a
        // route entering two zones separates their re-solves by their own price reads.
        val pullTiming = PullTiming()
        val pulled = MultipassPull.pull(setup, localPath, refusals, pullTiming)
        trace {
            "FINE LOCAL PULL zone=${zone.name} box=${boxText(box)} points=${pulled.size} " +
                "priceReads=${pullTiming.priceReads} marks=${pullTiming.marks} " +
                "memoPriceHits=${pullTiming.memoPriceHits} " +
                "stepM=${fmt(pullTiming.stepM)} priceStepM=${fmt(pullTiming.priceStepM)}"
        }
        val snapped = snapToCorners(setup, sets, pulled)
        val local = MultipassPull.pull(setup, snapped, refusals)
        val localCost = pricedLineCost(setup, local)
        val coarseCost = pricedLineCost(setup, line.subList(first, last + 1))
        if (localCost > coarseCost) {
            trace {
                "FINE zone=${zone.name} box=${boxText(box)} spliced=no reason=worse " +
                    "local=${fmt(localCost)} coarse=${fmt(coarseCost)} " +
                    "ms=${fmt(msSince(zoneStartNs))}"
            }
            return line
        }
        val out = ArrayList<LatLng>(line.size + local.size)
        if (first > 0) out.addAll(line.subList(0, first))
        out.addAll(if (first > 0) local.subList(1, local.size) else local)
        if (last < line.size - 1) out.addAll(line.subList(last + 2, line.size))
        trace {
            "$head first=$first last=$last local=yes spliced=yes points=${out.size} " +
                "ms=${fmt(msSince(zoneStartNs))}"
        }
        trace {
            "FINE splice zone=${zone.name} box=${boxText(box)} " +
                "zoneM=${fmt(zoneMetres(listOf(zone), local))} " +
                "coarse zoneM=${fmt(zoneMetres(listOf(zone), line.subList(first, last + 1)))} " +
                "ms=${fmt(msSince(zoneStartNs))}"
        }
        return out
    }
}
