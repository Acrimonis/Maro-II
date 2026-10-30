package ykws.android.maro.spatial.multipass

import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.data.regulation.SpeedZone
import ykws.android.maro.spatial.SpatialOperations
import ykws.android.maro.spatial.Units
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos

/**
 * **The refinement along the settled line** — the field-first re-tension and the crossing re-solve,
 * lifted out of the pass pipeline so the after-the-loop work is a named seat of its own.
 *
 * Both parts are impact-bearing: the re-tension runs on every route and a crossing re-solve can replace
 * a zone's own stretch, so the engine composes this seat once and this seat never re-implements the
 * pass it splices.
 */
internal class RouteFinePass(
    /**
     * **Whether a crossing's raster is shared across the arm's rungs** ([CrossingRasterMemo]) — `true` on
     * every shipped path. A fixture passes `false` to make every rung build its own, which is how the
     * sharing is pinned to draw the same lines: the raster reads nothing of a rung's λ, so the copies and
     * the fresh builds are one water.
     */
    private val shareCrossingRasters: Boolean = true
) {

    /**
     * **The seconds a line costs against the [setup]'s water** — its travel at the pace plus the λ-priced
     * soft price, per segment, exactly the `totalSec` every other "no dearer" proof of the pull reads
     * ([MultipassPull.totalSec]), summed to the whole line. The splice comparison reads this and no other
     * clock: a crossing re-solve that is cheaper in price but longer in travel is no better line, and the
     * soft-only sum it read before could accept one. The setup arrives from the call that resolved the
     * line, so this site inherits the walk's own steps rather than inventing a partition of its own.
     *
     * It reads the setup's margin, steps, pace and field alone, and folds the water into a tally-free
     * context here — a line cost counts and caches nothing.
     */
    internal fun lineCostSec(setup: PullSetup, points: List<LatLng>, memo: MarkMemo? = null): Double {
        val ctx = setup.context(memo = memo)
        if (points.size < 2) return 0.0
        var total = 0.0
        for (i in 1 until points.size) {
            total += MultipassPull.totalSec(points[i - 1], points[i], ctx)
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
        /** The calling job's own cancel read, handed to the plain loops — the rasters, the pulls, the sweep. */
        checkCancelled: () -> Unit = {},
        /** Whether anything reads [trace] — gates the sweep's arm report, which is work only a reader wants. */
        traceLive: Boolean = true,
        // Last on purpose: a caller may pass the trace as a trailing lambda.
        trace: (() -> String) -> Unit = {}
    ): List<LatLng> {
        if (line.size < 2) return line
        // **The refinement runs in the pair's canonical direction.** Its re-solves, its two pulls, the sweep,
        // the straightener and the nudge each read the line from one end, so handed one line reversed they
        // could settle a different point; turned so the first point sorts before the last — the ends swapped
        // with it, their discs being symmetric — and turned back at the exit, a pair armed from either end is
        // refined as one line (`261020_FEAT_PLN_Route_direction-symmetry.md`).
        val reversed = !sortsFirst(line.first(), line.last())
        val oriented = if (reversed) line.asReversed() else line
        val refined = refineOriented(
            ctx, oriented, lambda,
            start = if (reversed) ctx.aim else ctx.start,
            aim = if (reversed) ctx.start else ctx.aim,
            checkCancelled = checkCancelled, traceLive = traceLive, trace = trace
        )
        return if (reversed) refined.reversed() else refined
    }

    /** [finePass]'s body over a line whose first point sorts before its last, [start] and [aim] its own ends. */
    private suspend fun refineOriented(
        ctx: GridContext,
        line: List<LatLng>,
        lambda: Double,
        start: LatLng,
        aim: LatLng,
        checkCancelled: () -> Unit,
        traceLive: Boolean,
        trace: (() -> String) -> Unit
    ): List<LatLng> {
        val world = ctx.world
        val corridor = ctx.box
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
        // The refinement's own tally — never the pass's, and never a sibling rung's: the sweep's gate below
        // reads what this line's own pulls refused — the crossings' local pulls included where they run, so with
        // the re-solve off a line whose only refusals were a discarded re-solve's skips its sweep.
        val refusals = PullRefusals()
        val fineCellM = ctx.fineCellM
        if (line.size < 2 || fineCellM <= 0.0 || fineCellM >= cellM) return line
        // The **clearance** step is this fine grid's own cell — the line is sampled at the resolution it
        // was resolved at. The **price** step is the **coarse** cell, never the fine one: the grouping
        // rule is `RoutePassRunner`'s own (a step at or under the sampling step proves no group and reads
        // every mark), applied here because this pass prices the same line through the same walk.
        val coarseStepM = fineCellM
        // **The guard field, built once for the whole refinement** — the crossings' re-solves, the two
        // pulls, the sweep, the approach and the straightener all price through this one identity, which is
        // what lets one memo answer every later stage's marks: the memo is bound to the field's identity and
        // a second field of the same values would wipe it.
        val fineGuard =
            costField(
                world, fineCellM, pace, withZones = true, withBand = true, zones = zones, lambda = lambda,
                markerStrength = ctx.markerStrength
            )
        val gate = ctx.gate
        val setup = pullSetupFor(ctx, fineGuard, lambda, coarseStepM, start, aim)
        val berth = setup.berth
        // **One memo for the refinement** — made here, threaded through every stage below and never stored.
        val memo = MarkMemo()
        var out = line
        // The per-zone crossing re-solve, behind its own key (`route.fine.crossingResolve`, the any-angle plan's
        // D6): off, no zone is re-solved and the one line below says so, so a device log names the build it read.
        // A zone is re-solved where its price is in force and the line enters it — one rule, read by the loop and by
        // the off line's count alike; the loop re-reads it after each splice.
        fun reSolves(zone: SpeedZone, on: List<LatLng>): Boolean =
            zonePriceSec(cellM, pace, zone.routeLimitKn, lambda) > 0.0 && lineEntersZone(on, zone)
        if (!AppConfig.routeFineCrossingResolve) {
            trace { "FINE crossings=off zones=${zones.count { reSolves(it, out) }}" }
        } else for (zone in zones) {
            if (!reSolves(zone, out)) continue
            out = solveCrossing(
                world, corridor, out, zone, start, aim, pace, cellM, fineCellM, marginM, outsideMarginM,
                lambda, edges, openCoast, capLatNorth, priced, zones, sets, refusals,
                ctx.markerStrength, ctx.plan, ctx.gate, checkCancelled, trace, guard = fineGuard, memo = memo,
                rasters = if (shareCrossingRasters) ctx.crossingRasters else null
            ) ?: out
        }
        // Both pulls report the tallies the runner's own PULL and FINAL lines carry, so one pass taken
        // the way the device takes it says whether the price walk grouped at all on this fine grid.
        val pullTiming = PullTiming()
        val pulled = MultipassPull.pull(setup, out, refusals, pullTiming, memo, checkCancelled = checkCancelled)
        trace {
            "FINE PULL points=${pulled.size} priceReads=${pullTiming.priceReads} hardReads=${pullTiming.hardReads} " +
                "marks=${pullTiming.marks} memoPriceHits=${pullTiming.memoPriceHits} " +
                "stepM=${fmt(pullTiming.stepM)} priceStepM=${fmt(pullTiming.priceStepM)}"
        }
        val snapped = snapToCorners(setup, sets, pulled, memo)
        val finalTiming = PullTiming()
        val settled = MultipassPull.pull(setup, snapped, refusals, finalTiming, memo, checkCancelled)
        // The settled line then carries its own lookback: the removable-vertex sweep drops every bend the
        // pull's own two proofs clear, gated on the line's own refusals, so the corner pass consumes the
        // genuinely taut line rather than the forward-only greedy's first-refusal keep.
        val swept = MultipassPull.sweep(
            setup, settled, refusals, finalTiming, memo, trace, checkCancelled = checkCancelled,
            traceLive = traceLive
        )
        trace {
            "FINE settled points=${swept.size} fineCell=${fmt(fineCellM)}m " +
                "priceReads=${finalTiming.priceReads} hardReads=${finalTiming.hardReads} marks=${finalTiming.marks} " +
                "memoPriceHits=${finalTiming.memoPriceHits} " +
                "stepM=${fmt(finalTiming.stepM)} priceStepM=${fmt(finalTiming.priceStepM)}"
        }
        // The wall-checked straightening — Douglas–Peucker over the settled line, before the corner pass
        // consumes it: near-collinear waypoints collapse into straight chords so the corner pass sees
        // fewer bends, and a chord is taken only where it clears the wall and is no dearer. The pass reads
        // the same setup the pull walked, so the clearance is one home. The approach pass's own step is the
        // fine cell — a geometry step, the radius it searches at — apart from the proof step the setup carries.
        val approached = RouteApproachPass.refine(setup, swept, corridor, fineCellM, memo, checkCancelled, trace)
        val straightened = MultipassPull.straightenLine(setup, approached, memo = memo)
        trace { "STRAIGHTEN points=${approached.size}→${straightened.size}" }
        // **The medial nudge** — where the line runs through a passage narrower than the gate's preferred
        // standoff on both sides, its stretch moves to the passage's middle, on the same setup's proofs. It
        // is the berth's own pass: a plan that prices no inner ramp has no passage to centre.
        if (gate == null || berth == null || !berth.hasInnerRamp) return straightened
        return RouteMedialNudge.nudge(setup, gate, straightened, trace)
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
        refusals: PullRefusals?,
        markerStrength: Double = 1.0,
        /**
         * **The plan whose berth this crossing prices** — carried so this local re-solve prices the very
         * water the coarse answer did: its grid carries the same distance field and its pull prices the
         * berth at the pass's λ.
         */
        plan: RouteGridPlan = UniformGridPlan,
        /** The arm's depth-gate rule — the same measured edge the coarse answer kept off. */
        gate: GateRule? = null,
        checkCancelled: () -> Unit = {},
        trace: (() -> String) -> Unit = {},
        /**
         * **The refinement's guard field**, built once by the caller for every stage of the pass, or `null`
         * for a call that stands alone (a test) and builds its own. One identity per pass is what lets the
         * memo below answer a later stage's marks.
         */
        guard: RouteCostField? = null,
        /** The pass's own memo, shared with the pulls that follow this re-solve; `null` walks without one. */
        memo: MarkMemo? = null,
        /**
         * **The arm's crossing rasters** — the zone's local raster built once for the arm and copied here,
         * or `null` to build one of this call's own (a call standing alone, or the sharing switched off).
         */
        rasters: CrossingRasterMemo? = null
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
        val base = costField(
            world, fineCellM, pace, withZones = false, withBand = false, zones = emptyList(), lambda = lambda,
            markerStrength = markerStrength
        )
        val guardField = guard ?: costField(
            world, fineCellM, pace, withZones = true, withBand = true, zones = zones, lambda = lambda,
            markerStrength = markerStrength
        )
        val depthGateActive = gate != null
        val berth = berthLawOf(plan, depthGateActive)
        // This crossing walks one fine grid — clearance at its own cell, price at the coarse one, as in
        // `finePass`. **The raster is the arm's, not the rung's**: it reads the zone's box, the pace, the
        // gate, the markers and the harvest — none of them the rung's λ — so the first rung builds it and
        // its siblings copy it, each freeing its own ends on its own copy. A box too large to keep beside
        // the rungs' copies (a third of the walk's own ceiling) is built per rung as before.
        val coarseStepM = fineCellM
        val priceStepM = cellM
        val cells = rasterCells(box, fineCellM)
        val shared = rasters != null && cells <= AppConfig.routeWalkMaxCells / 3
        var built = false
        val build: () -> MultipassGrid = {
            built = true
            rasterize(
                box, fineCellM, pace, marginM, edges, openCoast, capLatNorth, base, priced,
                zoneOutsideMarginM = outsideMarginM, band = bandLaw(world),
                gate = gate, checkCancelled = checkCancelled
            )
        }
        val grid = if (shared) rasters!!.gridFor(CrossingRasterKey(zone.id, box, fineCellM), build) else build()
        trace { "FINE RASTER zone=${zone.name} box=${boxText(box)} cells=$cells shared=$shared built=$built" }
        grid.forceFree(from.latitude, from.longitude)
        grid.forceFree(to.latitude, to.longitude)
        val setup = PullSetup(
            marginM, coarseStepM, priceStepM, guardField, start, aim,
            gate = gate,
            berth = berth,
            berthLambda = lambda,
            paceMps = Units.knotsToMps(pace)
        )
        val search = MultipassSearch.search(
            grid,
            grid.cellOf(from.latitude, from.longitude),
            grid.cellOf(to.latitude, to.longitude),
            Units.knotsToMps(pace),
            zonePriceSec = zonePriceFor(pace, lambda),
            // The berth's own cursor travels with the field: the local grid carries the same distance
            // field the coarse one did, so this re-solve prices one berth exactly as the arm did.
            berthK = lambda,
            berth = berth,
            stepGuard = gate?.let { StepGuard(it, start, aim) },
            // The splice's own ends are vertices of the line, not the route's: their cells are bound.
            waiveEndSlots = false
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
        val pulled = MultipassPull.pull(setup, localPath, refusals, pullTiming, memo, checkCancelled = checkCancelled)
        trace {
            "FINE LOCAL PULL zone=${zone.name} box=${boxText(box)} points=${pulled.size} " +
                "priceReads=${pullTiming.priceReads} hardReads=${pullTiming.hardReads} marks=${pullTiming.marks} " +
                "memoPriceHits=${pullTiming.memoPriceHits} " +
                "stepM=${fmt(pullTiming.stepM)} priceStepM=${fmt(pullTiming.priceStepM)}"
        }
        val snapped = snapToCorners(setup, sets, pulled, memo)
        // The settled pull answers its line's own cost — the committed chords' `totalSec`, bit for bit the
        // sum `lineCostSec` would read off the points again — so the local line is priced once, not twice.
        val localPulled = MultipassPull.pullWithCost(setup, snapped, refusals, memo = memo, checkCancelled = checkCancelled)
        val local = localPulled.points
        // Both sides span the same water — the splice's own two ends, `from` and `to` — so the re-solve is
        // never charged the two flanking legs the coarse stretch kept for free.
        val localCost = localPulled.totalSec
        val coarseCost = lineCostSec(setup, line.subList(maxOf(first - 1, 0), minOf(last + 2, line.size)), memo)
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

/**
 * The cells a box holds at [cellM] under the rasterizer's own frame ([rasterize] derives the pair at the
 * box's mid-latitude) — the number the raster will allocate, asked before it is built.
 */
private fun rasterCells(box: BBox, cellM: Double): Int {
    val midLat = (box.latSouth + box.latNorth) / 2.0
    val mPerDegLat = SpatialOperations.EARTH_RADIUS_M * PI / 180.0
    val mPerDegLon = mPerDegLat * cos(Math.toRadians(midLat))
    val rows = ceil((box.latNorth - box.latSouth) / (cellM / mPerDegLat)).toInt().coerceAtLeast(1)
    val cols = ceil((box.lonEast - box.lonWest) / (cellM / mPerDegLon)).toInt().coerceAtLeast(1)
    return rows.toLong().times(cols.toLong()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}
