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
 * **[publishStage] is the candidates' own door**, and it is the one behaviour the instrument changed:
 * the λ passes and the fine pass move the panel's stage line, a candidate's pass does not, so the panel
 * narrates the answer's build alone. [guardZones] and [guardBand] drop one source's price from the
 * pull's own field for a candidate that drops it — the grid's own limits were dropped by the caller —
 * so the dropped price is dropped from the search and the guard alike. One cursor prices both sources,
 * so a candidate never names a second λ. [publish] and [trace] are the engine's own, handed in so the
 * emission sequence never belongs to the seat.
 *
 * **The provisional pair rides the SNAP boundary on every pass.** Between the first pull and the corner
 * snap, [runPass] always publishes a [RouteStage.SNAP] reading carrying the pulled line's own distance
 * and a limit-read duration — the provisional figure — whether or not the caller narrates stages, because
 * each rung's own row waits on its own line; the caller decides what travels from there.
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
        guardBand: Boolean = true
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
        val approaches = ctx.approaches
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
                lambda = lambda, withDepthBand = ctx.depthBandActive
            )
        // The water this pass walks, built once here — immediately after the field the walk is handed,
        // and never cached: the corridor-growth paths hand a grown context to a fresh call.
        val setup = PullSetup(marginM, coarseStepM, priceStepM, guardField, start, aim, approaches)
        if (publishStage) publish(RouteStage.SEARCH, null, emptyList(), null)
        // The search reads the family's **lattice** coordinates; the seeds arrive as cells of the interior
        // grid's own local space, so they are translated onto the walk first — a no-op for the single grid,
        // whose offset is zero, and the P4.1 fix for a fixed anchor, whose offset is not.
        val search = MultipassSearch.searchWalk(
            windows,
            windows.latticeCell(grid, startCell.row, startCell.col),
            windows.latticeCell(grid, aimCell.row, aimCell.col),
            Units.knotsToMps(pace),
            zonePriceSec = { cellSizeM, interiorKn, collarKn, bandCollarKn ->
                slowWaterPriceAt(
                    cellSizeM, pace, lambda, interiorKn, collarKn, bandCollarKn,
                    AppConfig.routeAvoidSpeedZoneOutsideMarginCostFraction,
                    AppConfig.routeAvoidZone300OutsideMarginCostFraction
                )
            },
            depthK = if (ctx.depthBandActive) lambda else 0.0
        )
        val path = search.path
            ?: return PassReading(search, emptyList(), null, SlowShares(0.0, 0.0, 0.0), 0, 0)
        // The path carries its layer, so a two-layer walk resolves each cell on its own resolution rather
        // than through the first layer that happens to hold its coordinate.
        val coarse = path.map { windows.center(it.layer, it.row, it.col) }
        val full = listOf(start) + coarse + listOf(aim)
        // The readings ride the boundary the stage just left: the search's own two counts are known here,
        // at the pull that follows it, so they belong to the PULL update's `stageDone` = SEARCH.
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
            null
        )
        val pullTiming = PullTiming()
        val pullStartNs = System.nanoTime()
        val pulled = MultipassPull.pull(setup, full, refusals, pullTiming)
        trace {
            "PULL zoneM=${fmt(zoneMetres(zones, pulled))} ms=${fmt(msSince(pullStartNs))} " +
                "clearMs=${fmt(pullTiming.clearanceMs)} priceMs=${fmt(pullTiming.priceMs)} " +
                "priceReads=${pullTiming.priceReads} marks=${pullTiming.marks} " +
                "memoPriceHits=${pullTiming.memoPriceHits} memoHardHits=${pullTiming.memoHardHits} " +
                "stepM=${fmt(pullTiming.stepM)} priceStepM=${fmt(pullTiming.priceStepM)}"
        }
        // The provisional pair belongs to this boundary and to the pulled line alone: the line is taut
        // here, but the settled clock is still the pull → snap → pull tail and the corner pass away, so
        // one enforced-limit read and the pulled length are all a row can stand behind now. It rides the
        // SNAP boundary for **every** pass — the narrating main and a candidate alike — because each
        // rung's own row waits on its own line; the caller decides what travels from there.
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
        val final = MultipassPull.pull(setup, snapped, refusals, finalTiming)
        trace {
            "FINAL zoneM=${fmt(zoneMetres(zones, final))} ms=${fmt(msSince(finalStartNs))} " +
                "clearMs=${fmt(finalTiming.clearanceMs)} priceMs=${fmt(finalTiming.priceMs)} " +
                "priceReads=${finalTiming.priceReads} marks=${finalTiming.marks} " +
                "memoPriceHits=${finalTiming.memoPriceHits} memoHardHits=${finalTiming.memoHardHits} " +
                "stepM=${fmt(finalTiming.stepM)} priceStepM=${fmt(finalTiming.priceStepM)}"
        }
        val timed = timeLineWithLimits(
            final, pace, limitAt, clockSampleM(cellM, ctx.fineCellM)
        )
        val shares = slowShares(timed, pace, inZone = inZone(zones), inBand = inBand(world))
        return PassReading(search, final, timed, shares, pulled.size, snapped.size)
    }

    /** The pulled polyline's own length (m) — the provisional distance, the shared sum, never a staircase's. */
    private fun pulledLengthM(points: List<LatLng>): Double = polylineLengthM(points)
}
