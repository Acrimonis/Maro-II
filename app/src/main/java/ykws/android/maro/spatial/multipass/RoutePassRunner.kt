package ykws.android.maro.spatial.multipass

import ykws.android.maro.R
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.spatial.RouteProvisional
import ykws.android.maro.spatial.RouteStage
import ykws.android.maro.spatial.RouteStepReading
import ykws.android.maro.spatial.Units

/**
 * **One pass of the walk** — the A\*, the taut pull, the corner snap and the final pull, all of it
 * through the pass's own λ, lifted out of the pass pipeline so the walk is a named seat of its own.
 *
 * Stateless: it holds nothing, so the engine composes it once and every pass reuses it. The grid A*
 * owns the order, the tangent corners own the exact points: pull the cell path taut, then move each
 * bend onto its nearest corner whose own set radius contains it when both neighbouring legs stay
 * clear, and pull once more. `null` where no path connects the two ends.
 *
 * The walk carries the grid, its two end cells and the cell size it was rasterized at; everything else
 * — the ends, the pace, the margins, the limit read, the zones and the corner sets — comes from the
 * context, so a coarse pass and a fine one differ by their [GridWalk] alone.
 *
 * **[publishStage] is the stage line's door**: the engine opens it on every rung, so each row prints its
 * own stage, and a caller that wants the pass silent (a test harness) closes it. [guardZones] and [guardBand] drop one source's price from the
 * pull's own field for a candidate that drops it — the grid's own limits were dropped by the caller —
 * so the dropped price is dropped from the search and the guard alike. One cursor prices both sources,
 * so a candidate never names a second λ. [publish] and [trace] are the engine's own, handed in so the
 * emission sequence never belongs to the seat, and so is [checkCancelled]: the search reads the calling
 * job itself, but the two pulls are plain loops, so the engine hands them its job's `ensureActive` as a
 * plain lambda and a cancelled lookup stops inside the pull rather than after it.
 *
 * **The provisional pair rides two boundaries on every pass.** At the [RouteStage.PULL] boundary it
 * carries the A* cell path's own length and limit-read clock, and between the first pull and the corner
 * snap a [RouteStage.SNAP] reading replaces it with the pulled line's own distance and limit-read
 * duration — the provisional figure — because each rung's own row waits on its own line.
 */
internal class RoutePassRunner {

    internal suspend fun runPass(
        ctx: GridContext,
        walk: GridWalk,
        lambda: Double,
        publishStage: Boolean,
        publish: (RouteStage?, List<RoutePoint>?, List<RouteStepReading>, RouteProvisional?) -> Unit =
            { _, _, _, _ -> },
        trace: (() -> String) -> Unit = {},
        guardZones: Boolean = true,
        guardBand: Boolean = true,
        checkCancelled: () -> Unit = {}
    ): PassReading {
        val world = ctx.world
        val grid = walk.grid
        val windows = walk.windows ?: WalkWindows.of(grid)
        val startCell = walk.startCell
        val aimCell = walk.aimCell
        val start = ctx.start
        val aim = ctx.aim
        val pace = ctx.pace
        val cellM = walk.cellM
        val marginM = ctx.marginM
        val limitAt = ctx.limitAt
        val zones = ctx.zones
        val sets = ctx.sets
        val refusals = ctx.refusals
        // The guard reads the water the snap guards: for a two-layer walk that is the fine band's own cell,
        // not the interior's, so the tail's prices sit at the resolution the line was resolved at (Phase 6).
        val tailCellM = if ((walk.windows?.layerCount ?: 1) > 1) ctx.fineCellM else cellM
        // The **clearance** step is the walk's own local cell — `tailCellM` above: the fine band's cell
        // where the walk is two-layer, the single grid's own cell elsewhere — so it travels per engine
        // while the pull stays one walk. A step under the fine one would spare nothing at all, and the
        // caller never hands one: both shipped cells stand well above it.
        val coarseStepM = tailCellM
        // The **price** step is the walk's own cell — never the local band cell above, whose 20 m
        // collapsed the price grouping to one interval a group and made the shared price walk save
        // nothing (Phase 4b) — so the two-layer walk groups at its interior 100 m.
        val priceStepM = cellM
        val guardField =
            costField(
                world, tailCellM, pace, withZones = guardZones, withBand = guardBand, zones = zones,
                lambda = lambda, markerStrength = ctx.markerStrength
            )
        // The gate's measured rule (both engines) and the plan's berth law (evolutive alone), read once
        // here so the search and the pull share one cursor.
        val gate = ctx.gate
        val berth = berthLawOf(ctx.plan, ctx.depthGateActive)
        // The water this pass walks, built once here — immediately after the field the walk is handed,
        // and never cached: the corridor-growth paths hand a grown context to a fresh call.
        val setup = PullSetup(
            marginM, coarseStepM, priceStepM, guardField, start, aim,
            gate = gate,
            berth = berth,
            berthLambda = lambda,
            paceMps = Units.knotsToMps(pace)
        )
        if (publishStage) publish(RouteStage.SEARCH, null, emptyList(), null)
        // The search reads the family's **lattice** coordinates; the seeds arrive as cells of the interior
        // grid's own local space, so they are translated onto the walk first — a no-op for the single grid,
        // whose offset is zero, and the P4.1 fix for a fixed anchor, whose offset is not.
        val search = searchFromCanonicalEnd(
            windows,
            windows.endCellAt(start, windows.latticeCell(grid, startCell.row, startCell.col)),
            windows.endCellAt(aim, windows.latticeCell(grid, aimCell.row, aimCell.col)),
            start, aim,
            Units.knotsToMps(pace),
            zonePriceSec = { cellSizeM, interiorKn, collarKn, bandCollarKn ->
                slowWaterPriceAt(
                    cellSizeM, pace, lambda, interiorKn, collarKn, bandCollarKn,
                    AppConfig.routeAvoidSpeedZoneOutsideMarginCostFraction,
                    AppConfig.routeAvoidZone300OutsideMarginCostFraction
                )
            },
            // The berth's own cursor: the grid stores its distance, so the rung's λ is applied here — the
            // same λ the guard field prices the pull's points at.
            berthK = lambda,
            berth = berth,
            // The gate's step check, so every raw leg the pull may keep already keeps the wall.
            gate = gate
        )
        val path = search.path
            ?: return PassReading(search, emptyList(), null, SlowShares(0.0, 0.0, 0.0), 0, 0)
        // The path carries its layer, so a two-layer walk resolves each cell on its own resolution rather
        // than through the first layer that happens to hold its coordinate.
        val coarse = path.map { windows.center(it.layer, it.row, it.col) }
        val full = listOf(start) + coarse + listOf(aim)
        // The readings ride the boundary the stage just left: the search's own two counts are known here,
        // at the pull that follows it, so they belong to the PULL update's `stageDone` = SEARCH.
        // The cell path already supports a first provisional pair — its length and its limit-read clock.
        // It is an overestimate by nature (the path is jagged until the pull), so the pair jumps at the
        // pull's own SNAP boundary and again at the landing; the row's stage word says it is not final.
        val cellPathTimed = timeLineWithLimits(full, pace, limitAt, clockSampleM(cellM, ctx.fineCellM))
        if (publishStage) publish(
            RouteStage.PULL, full.map { RoutePoint.of(it) },
            listOf(
                RouteStepReading(
                    RouteStage.SEARCH, R.string.route_reading_expansions,
                    search.expansions.toDouble(), R.string.route_unit_cells
                ),
                RouteStepReading(
                    RouteStage.SEARCH, R.string.route_reading_passable_cells,
                    search.passableCells.toDouble(), R.string.route_unit_cells
                )
            ),
            RouteProvisional(pulledLengthM(full), cellPathTimed.durationSec)
        )
        val pullTiming = PullTiming()
        val pullStartNs = System.nanoTime()
        // Both pulls read the path from both ends and keep the cheaper, so a pair armed from either end draws
        // one line from one cell chain (`261020_FEAT_PLN_Route_direction-symmetry.md`).
        val pullSplit = PullSplit()
        val pulled = MultipassPull.pullBothWays(
            setup, full, refusals, pullTiming, split = pullSplit, checkCancelled = checkCancelled
        )
        trace { splitText("PULL2", pullSplit) }
        trace {
            "PULL zoneM=${fmt(zoneMetres(zones, pulled))} ms=${fmt(msSince(pullStartNs))} " +
                "clearMs=${fmt(pullTiming.clearanceMs)} priceMs=${fmt(pullTiming.priceMs)} " +
                "priceReads=${pullTiming.priceReads} marks=${pullTiming.marks} " +
                "memoPriceHits=${pullTiming.memoPriceHits} memoHardHits=${pullTiming.memoHardHits} " +
                "memoEdgeHits=${pullTiming.memoEdgeHits} farEdgeReads=${pullTiming.farEdgeReads} " +
                "stepM=${fmt(pullTiming.stepM)} priceStepM=${fmt(pullTiming.priceStepM)}"
        }
        // The provisional pair belongs to this boundary and to the pulled line alone: the line is taut
        // here, but the settled clock is still the pull → snap → pull tail and the corner pass away, so
        // one enforced-limit read and the pulled length are all a row can stand behind now. It rides the
        // SNAP boundary for **every** pass, because each rung's own row waits on its own line.
        val provisionalTimed = timeLineWithLimits(pulled, pace, limitAt, clockSampleM(cellM, ctx.fineCellM))
        val provisional = RouteProvisional(pulledLengthM(pulled), provisionalTimed.durationSec)
        publish(
            RouteStage.SNAP, pulled.map { RoutePoint.of(it) },
            listOf(
                RouteStepReading(
                    RouteStage.PULL, R.string.route_reading_pulled_points,
                    pulled.size.toDouble(), R.string.route_unit_points
                )
            ),
            provisional
        )
        val snapStartNs = System.nanoTime()
        val snapped = snapToCorners(setup, sets, pulled)
        trace { "SNAP zoneM=${fmt(zoneMetres(zones, snapped))} ms=${fmt(msSince(snapStartNs))}" }
        val finalTiming = PullTiming()
        val finalStartNs = System.nanoTime()
        val finalSplit = PullSplit()
        val final = MultipassPull.pullBothWays(
            setup, snapped, refusals, finalTiming, split = finalSplit, checkCancelled = checkCancelled
        )
        trace { splitText("FINAL2", finalSplit) }
        trace {
            "FINAL zoneM=${fmt(zoneMetres(zones, final))} ms=${fmt(msSince(finalStartNs))} " +
                "clearMs=${fmt(finalTiming.clearanceMs)} priceMs=${fmt(finalTiming.priceMs)} " +
                "priceReads=${finalTiming.priceReads} marks=${finalTiming.marks} " +
                "memoPriceHits=${finalTiming.memoPriceHits} memoHardHits=${finalTiming.memoHardHits} " +
                "memoEdgeHits=${finalTiming.memoEdgeHits} farEdgeReads=${finalTiming.farEdgeReads} " +
                "stepM=${fmt(finalTiming.stepM)} priceStepM=${fmt(finalTiming.priceStepM)}"
        }
        val timed = timeLineWithLimits(
            final, pace, limitAt, clockSampleM(cellM, ctx.fineCellM)
        )
        val shares = slowShares(timed, pace, inZone = inZone(zones), inBand = inBand(world))
        return PassReading(search, final, timed, shares, pulled.size, snapped.size)
    }

    /**
     * **The search, run from the pair's canonical end so a pair is one search from either end.** The A\* breaks
     * a cost tie by arrival order from wherever it starts, so an island on the line, with two equal detours,
     * could be taken on opposite sides by A→B and B→A, and the pull was then handed two chains it could not
     * reconcile. Here the walk always leaves the end that [sortsFirst] — the aim's end, when the start sorts
     * after it, with the two cells **and** the guard's two raw points swapped together — and the cell path is
     * turned back to the caller's direction, so nothing downstream sees the swap but [SearchOutcome.reversed].
     *
     * **A walk that lands no path is walked once the other way** — the graph is not perfectly reversible (a seam
     * crossing is one-way in places), so a pair routable in one orientation only must not fail in both. Both
     * directions' calls make the same two walks in the same order, so the fallback is as symmetric as the
     * first. On success, which is nearly always, no second walk runs.
     *
     * The edge price is the destination cell's rate, so a reversed walk prices each edge by its other
     * neighbour: a route is planned on the price the reverse trip would pay, in the pairs whose canonical end
     * is the aim. That is the convention (`261020_FEAT_PLN_Route_direction-symmetry.md`, *The search's
     * tie-break*). [MultipassSearch.searchWalk] stays role-neutral and its other callers are untouched.
     */
    internal suspend fun searchFromCanonicalEnd(
        windows: WalkWindows,
        startCell: CellIndex,
        aimCell: CellIndex,
        start: LatLng,
        aim: LatLng,
        paceMps: Double,
        zonePriceSec: (cellM: Double, interiorLimitKn: Double, collarLimitKn: Double, bandCollarLimitKn: Double) -> Double,
        berthK: Double,
        berth: BerthLaw?,
        gate: GateRule?
    ): SearchOutcome {
        suspend fun walk(fromAim: Boolean): SearchOutcome {
            val from = if (fromAim) aimCell else startCell
            val to = if (fromAim) startCell else aimCell
            val guard = gate?.let { if (fromAim) StepGuard(it, aim, start) else StepGuard(it, start, aim) }
            val outcome = MultipassSearch.searchWalk(
                windows, from, to, paceMps, zonePriceSec = zonePriceSec,
                berthK = berthK, berth = berth, stepGuard = guard
            )
            return if (fromAim) outcome.copy(path = outcome.path?.asReversed()?.toList(), reversed = true) else outcome
        }
        val canonicalFromAim = !sortsFirst(start, aim)
        val canonical = walk(canonicalFromAim)
        if (canonical.path != null) return canonical
        val other = walk(!canonicalFromAim)
        return other.copy(expansions = canonical.expansions + other.expansions)
    }

    /**
     * **One two-sided pull's walks, told apart** — the forward and backward walk's wall clock, clearance and
     * price clocks, paid price reads and the memo hits each ran under, then the choice's clock and verdict.
     * The `PULL` / `FINAL` line beside it keeps the sum; this line is what overlapping the two walks would
     * save (the slower walk against the two) and what the first walk's memo handed the second (`bwd` hits
     * against `fwd`). A walk the path was too short to run reads `-`.
     */
    private fun splitText(label: String, split: PullSplit): String {
        fun walkText(side: String, walk: PullSplit.Walk?): String {
            if (walk == null) return "${side}Ms=- "
            val t = walk.tally
            return "${side}Ms=${fmt(walk.ms)} ${side}ClearMs=${fmt(t.clearanceNanos / NANOS_PER_MS)} " +
                "${side}PriceMs=${fmt(t.priceNanos / NANOS_PER_MS)} ${side}PriceReads=${t.priceReads} " +
                "${side}Marks=${t.marks} ${side}PriceHits=${t.priceHits} ${side}HardHits=${t.hardHits} " +
                "${side}EdgeHits=${t.edgeHits} "
        }
        return "$label kept=${split.kept ?: "-"} " +
            walkText("fwd", split.forwardWalk) + walkText("bwd", split.backwardWalk) +
            "choiceMs=${fmt(split.choiceMs)} " +
            "fwdSec=${split.forwardSec?.let { fmt(it) } ?: "-"} bwdSec=${split.backwardSec?.let { fmt(it) } ?: "-"}"
    }

    /** The pulled polyline's own length (m) — the provisional distance, the shared sum, never a staircase's. */
    private fun pulledLengthM(points: List<LatLng>): Double = polylineLengthM(points)
}
