package ykws.android.maro.spatial

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.data.model.RouteResult
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.data.regulation.SpeedZone
import ykws.android.maro.spatial.avoid.AvoidPull
import ykws.android.maro.spatial.avoid.AvoidSearch
import ykws.android.maro.spatial.avoid.AvoidWorld
import ykws.android.maro.spatial.avoid.AvoidCellState
import ykws.android.maro.spatial.avoid.AvoidEdge
import ykws.android.maro.spatial.avoid.CellIndex
import ykws.android.maro.spatial.avoid.PricedZone
import ykws.android.maro.spatial.avoid.RouteCostField
import ykws.android.maro.spatial.avoid.RouteCostSource
import ykws.android.maro.spatial.avoid.TangentCorners
import ykws.android.maro.spatial.avoid.bandPriceM
import ykws.android.maro.spatial.avoid.bandReachM
import ykws.android.maro.spatial.avoid.depthGateSource
import ykws.android.maro.spatial.avoid.forcedCrossingZoneNames
import ykws.android.maro.spatial.avoid.rasterize
import ykws.android.maro.spatial.avoid.timeLineWithLimits
import ykws.android.maro.spatial.avoid.zonePriceM
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
 * Coroutines and `StateFlow` only: no thread of its own, and the search checks the calling job
 * between its expansions so a flung map never queues behind a computation nobody wants any more.
 */
class RouteAvoidEngine(
    /** The pace in force (kn), asked fresh on every answer so a slider move reaches the next line. */
    private val paceKn: () -> Double,
    /** The world provider — the map always holds the layers it wraps, so it answers a live world. */
    private val worldProvider: () -> AvoidWorld
) : RouteEngine {

    /** Starts [RouteEngineState.NotReady]; only [prepare] moves it, as the contract reserves. */
    private val _state = MutableStateFlow<RouteEngineState>(RouteEngineState.NotReady)

    override val state: StateFlow<RouteEngineState> = _state.asStateFlow()

    /** The end the mode froze when it was armed — told once, and held for the whole session. */
    private var origin: RoutePoint? = null

    /** The aim last dragged — told with each ask, and held while the following phase moves the origin. */
    private var destination: RoutePoint? = null

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

    /** A point is usable water exactly when the world says it is water, else [RouteRefusalReason.OFF_WATER]. */
    override suspend fun validatePoint(point: RoutePoint): RouteRefusalReason? {
        val world = worldProvider()
        return if (world.isWater(point.latitude, point.longitude)) null
        else RouteRefusalReason.OFF_WATER
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

    /** Both ends held and both on water → the pipeline; one end off water → [RouteResult.OutsideWater]. */
    private suspend fun routeBetween(from: RoutePoint?, to: RoutePoint?): RouteResult? {
        if (from == null || to == null) return null
        val world = worldProvider()
        if (!world.isWater(from.latitude, from.longitude)) return RouteResult.OutsideWater
        if (!world.isWater(to.latitude, to.longitude)) return RouteResult.OutsideWater
        return search(world, from, to)
    }

    /**
     * The corridor-bounded search, retried once with the corridor reach doubled before
     * [RouteResult.NoPath]. The pipeline is compute-only, so it runs on [Dispatchers.Default] — a
     * slow corridor never blocks the main thread, and the caller's state writes resume on Main.
     */
    private suspend fun search(world: AvoidWorld, from: RoutePoint, to: RoutePoint): RouteResult =
        withContext(Dispatchers.Default) {
            val reach = AppConfig.routeAvoidCorridorReachM
            val first = searchOnce(world, from, to, reach)
            if (first != null) return@withContext first
            val second = searchOnce(world, from, to, reach * 2.0)
            second ?: RouteResult.NoPath
        }

    /** One pass of the pipeline: corridor → harvest → field → rasterize → A* → pull → snap → pull. */
    private suspend fun searchOnce(
        world: AvoidWorld,
        from: RoutePoint,
        to: RoutePoint,
        reach: Double
    ): RouteResult.Success? {
        val box = corridorBox(from, to, world.regionBounds, reach) ?: return null
        val edges = world.segmentsIn(box)
        val openCoast = world.openCoastIn(box)
        val capLatNorth = world.regionBounds?.latNorth ?: box.latNorth
        val cellM = AppConfig.routeAvoidGridCellM
        val marginM = AppConfig.routeAvoidObstacleMarginM
        val field = costField(world)
        // The zones arrive pre-filtered by the world (excluded ids dropped); each is priced once from
        // the live pace, then rastered as a zone tag whose cost is the strictest limit in force.
        // The zones are priced only while the feature is armed; with it off the corridor is coastline,
        // depth and band alone, and every zone cell is open water.
        val zones = if (AppConfig.routeAvoidSpeedZoneEnabled) world.speedZonesIn(box) else emptyList()
        val zoneK = AppConfig.routeAvoidSpeedZoneSoftCostAversion
        val pace = paceKn()
        val priced = zones.map { z -> PricedZone(z.outerRing, z.holes, zonePriceM(cellM, pace, z.speedLimitKn, zoneK)) }
        val grid = rasterize(box, cellM, marginM, edges, openCoast, capLatNorth, field, priced)
        grid.forceFree(from.latitude, from.longitude)
        grid.forceFree(to.latitude, to.longitude)
        val startCell = grid.cellOf(from.latitude, from.longitude)
        val aimCell = grid.cellOf(to.latitude, to.longitude)
        val path = AvoidSearch.search(grid, startCell, aimCell) ?: return null
        val start = from.toLatLng()
        val aim = to.toLatLng()
        val coarse = path.map { grid.center(it.row, it.col) }
        val full = listOf(start) + coarse + listOf(aim)
        // The grid A* owns the order, the tangent corners own the exact points: pull the cell path
        // taut, then move each bend onto its nearest corner whose own set radius contains it, when
        // both neighbouring legs stay clear. The land set snaps within a cell's reach; the band set,
        // armed with the zone, snaps at the band's own reach so a concave band is chorded.
        val pulled = AvoidPull.pull(full, start, aim, marginM, field)
        val sets = ArrayList<CornerSet>(2)
        sets.add(CornerSet(TangentCorners.corners(edges, openCoast, marginM), cellM * 2.0))
        if (AppConfig.routeAvoidZone300Enabled && world.bandWidthM > 0.0) {
            val bandOffsetM = bandReachM(world.bandWidthM, AppConfig.routeAvoidZone300MarginM)
            sets.add(CornerSet(TangentCorners.corners(edges, openCoast, bandOffsetM), bandOffsetM))
        }
        val snapped = snapToCorners(pulled, sets, marginM, field, start, aim)
        val waypoints = AvoidPull.pull(snapped, start, aim, marginM, field)
        val forced = forcedCrossingNames(
            box, cellM, marginM, edges, openCoast, capLatNorth, field, priced, zones,
            from, to, startCell, aimCell, waypoints
        )
        return success(waypoints, world, forced)
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
     */
    private fun costField(world: AvoidWorld): RouteCostField {
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
        if (AppConfig.routeAvoidZone300Enabled) {
            val bandM = world.bandWidthM
            val bandPriceM = bandPriceM(AppConfig.routeAvoidGridCellM, AppConfig.routeAvoidZone300SoftCostAversion)
            if (bandM > 0.0 && bandPriceM > 0.0) {
                val reachM = bandReachM(bandM, AppConfig.routeAvoidZone300MarginM)
                sources.add(
                    RouteCostSource.Soft(
                        priceM = { p ->
                            if (world.distanceToCoastM(p.latitude, p.longitude) <= reachM) bandPriceM else 0.0
                        },
                        tag = AvoidCellState.BAND
                    )
                )
            }
        }
        return RouteCostField(sources)
    }

    /** One tangent corner set: the offset points and the radius within which they may move a bend. */
    private data class CornerSet(val points: List<LatLng>, val radiusM: Double)

    /** Moves a bend onto its nearest tangent corner — the nearest corner whose own set radius
     *  contains it, across all sets — only when both legs stay clear; open water keeps the bend. */
    private fun snapToCorners(
        path: List<LatLng>,
        sets: List<CornerSet>,
        marginM: Double,
        field: RouteCostField,
        start: LatLng,
        aim: LatLng
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
            val hardClear = AvoidPull.legClear(out[i - 1], corner, marginM, field, start, aim) &&
                AvoidPull.legClear(corner, path[i + 1], marginM, field, start, aim)
            val replacedPrice = AvoidPull.softPriceM(out[i - 1], path[i], marginM, field) +
                AvoidPull.softPriceM(path[i], path[i + 1], marginM, field)
            val snappedPrice = AvoidPull.softPriceM(out[i - 1], corner, marginM, field) +
                AvoidPull.softPriceM(corner, path[i + 1], marginM, field)
            if (hardClear && snappedPrice <= replacedPrice) {
                out[i] = corner
            }
        }
        return out
    }

    /**
     * Whether the route was forced through a priced zone: with every restrictive zone blocked the
     * corridor has no avoiding path, and the names reported are the restrictive zones the drawn line
     * enters. An ordinary priced crossing — a way around exists — reports nothing.
     */
    private suspend fun forcedCrossingNames(
        box: BBox,
        cellM: Double,
        marginM: Double,
        edges: List<AvoidEdge>,
        openCoast: List<List<LatLng>>,
        capLatNorth: Double,
        field: RouteCostField,
        priced: List<PricedZone>,
        zones: List<SpeedZone>,
        from: RoutePoint,
        to: RoutePoint,
        startCell: CellIndex,
        aimCell: CellIndex,
        waypoints: List<LatLng>
    ): List<String> {
        val restrictive = priced.filter { it.costM > 0.0 }
        if (restrictive.isEmpty()) return emptyList()
        val blocked = rasterize(box, cellM, marginM, edges, openCoast, capLatNorth, field, restrictive, blockZones = true)
        blocked.forceFree(from.latitude, from.longitude)
        blocked.forceFree(to.latitude, to.longitude)
        val avoiding = AvoidSearch.search(blocked, startCell, aimCell) != null
        val restrictiveZones = zones.zip(priced).filter { (_, p) -> p.costM > 0.0 }.map { (z, _) -> z }
        return forcedCrossingZoneNames(waypoints, restrictiveZones, avoiding)
    }

    /** Times the drawn line under the limits in force: legs split at limit changes, zone limits obeyed. */
    private fun success(
        waypoints: List<LatLng>,
        world: AvoidWorld,
        forcedCrossingZoneNames: List<String>
    ): RouteResult.Success {
        // The switch turns the whole source off: the price and the limit read go together, so with the
        // zones off the clock reads the pace alone.
        val limitAt: (LatLng) -> Double? =
            if (AppConfig.routeAvoidSpeedZoneEnabled) { p -> world.zoneLimitKnAt(p.latitude, p.longitude) }
            else { { _ -> null } }
        val timed = timeLineWithLimits(waypoints, paceKn(), limitAt)
        val points = timed.points.map { RoutePoint.of(it) }
        var distanceM = 0.0
        for (i in 0 until timed.points.size - 1) {
            distanceM += SpatialOperations.haversine(timed.points[i], timed.points[i + 1])
        }
        return RouteResult.Success(
            points = points,
            legTimesSec = timed.legTimesSec,
            distanceM = distanceM,
            durationSec = timed.durationSec,
            // The emitted polyline ends at the raw aim, never a resolved node: the pin stands where
            // the user dragged, and the snapped cell is only the search's anchor.
            destinationMoved = false,
            forcedCrossingZoneNames = forcedCrossingZoneNames
        )
    }

    /** The start-aim bounding box inflated by [reach], clamped to the region's bounds — a truncated box is accepted. */
    private fun corridorBox(from: RoutePoint, to: RoutePoint, bounds: BBox?, reach: Double): BBox? {
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
        return if (box.latSouth >= box.latNorth || box.lonWest >= box.lonEast) null else box
    }
}
