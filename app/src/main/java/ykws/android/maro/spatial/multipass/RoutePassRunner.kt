package ykws.android.maro.spatial.multipass

import ykws.android.maro.R
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.RoutePoint
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
 */
internal class RoutePassRunner {

    internal suspend fun runPass(
        ctx: GridContext,
        walk: GridWalk,
        lambda: Double,
        publishStage: Boolean,
        publish: (RouteStage?, List<RoutePoint>?, List<RouteStepReading>) -> Unit = { _, _, _ -> },
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
        val guardField =
            costField(
                world, cellM, pace, withZones = guardZones, withBand = guardBand, zones = zones,
                lambda = lambda
            )
        if (publishStage) publish(RouteStage.SEARCH, null, emptyList())
        val search = MultipassSearch.searchWalk(
            windows, startCell, aimCell, Units.knotsToMps(pace),
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
        val coarse = path.map { windows.center(it.row, it.col) }
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
            )
        )
        val pulled = MultipassPull.pull(
            full, start, aim, marginM, guardField, approaches, refusals
        )
        trace { "PULL zoneM=${fmt(zoneMetres(zones, pulled))}" }
        if (publishStage) publish(
            RouteStage.SNAP, pulled.map { RoutePoint.of(it) },
            listOf(
                RouteStepReading(
                    RouteStage.PULL, R.string.route_reading_pulled_points,
                    pulled.size.toDouble(), R.string.route_unit_points
                )
            )
        )
        val snapped = snapToCorners(pulled, sets, marginM, guardField, start, aim, approaches)
        trace { "SNAP zoneM=${fmt(zoneMetres(zones, snapped))}" }
        val final = MultipassPull.pull(
            snapped, start, aim, marginM, guardField, approaches, refusals
        )
        trace { "FINAL zoneM=${fmt(zoneMetres(zones, final))}" }
        val timed = timeLineWithLimits(
            final, pace, limitAt, clockSampleM(cellM, ctx.fineCellM)
        )
        val shares = slowShares(timed, pace, inZone = inZone(zones), inBand = inBand(world))
        return PassReading(search, final, timed, shares, pulled.size, snapped.size)
    }
}
