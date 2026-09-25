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
import ykws.android.maro.spatial.avoid.AvoidPull
import ykws.android.maro.spatial.avoid.AvoidSearch
import ykws.android.maro.spatial.avoid.AvoidWorld
import ykws.android.maro.spatial.avoid.TangentCorners
import ykws.android.maro.spatial.avoid.rasterize
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/**
 * **The avoid engine, stage 1: avoid land, islands and hazard rings.**
 *
 * A session like the contract: it holds the origin told at arming and the destination told with
 * each aim, and answers the collision-free route between them — from the origin to the destination,
 * so the polyline's own direction is the direction of travel — or `null` while the other end is not
 * held. The pipeline is corridor-bounded grid A* plus a clearance taut pull, run on the
 * [AvoidWorld] the injected provider supplies; every stage below reads that world and the four
 * `route.avoid.*` keys, so the 300 m band (stage 2) and the regulated speed zones (stage 3) land as
 * additive sources over the same rasterizer, A* and pull.
 *
 * **Readiness.** [prepare] fires the world's `load()` on a miss and latches
 * [RouteEngineState.Ready] once `coastlineReady`; a world that cannot become ready answers
 * [RouteEngineState.Unavailable] with [RouteUnavailableReason.COASTLINE_NOT_LOADED].
 * [isReadyToRecompute] stays `true` — stage 1 reads nothing that expires between asks.
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
    /** The world provider — the map always holds the coastline it wraps, so it answers a live world. */
    private val worldProvider: () -> AvoidWorld
) : RouteEngine {

    /** Starts [RouteEngineState.NotReady]; only [prepare] moves it, as the contract reserves. */
    private val _state = MutableStateFlow<RouteEngineState>(RouteEngineState.NotReady)

    override val state: StateFlow<RouteEngineState> = _state.asStateFlow()

    /**
     * The boundary the running search has reached, or null between calls: set as the pipeline crosses
     * each stage and cleared in [search]'s own `finally`, so an answer and an abort both leave the
     * panel with nothing to say about a search that is no longer running.
     */
    private val _stage = MutableStateFlow<RouteStage?>(null)

    override val stage: StateFlow<RouteStage?> = _stage.asStateFlow()

    /** The end the mode froze when it was armed — told once, and held for the whole session. */
    private var origin: RoutePoint? = null

    /** The aim last dragged — told with each ask, and held while the following phase moves the origin. */
    private var destination: RoutePoint? = null

    override suspend fun prepare(): RouteEngineState {
        val world = worldProvider()
        val next = if (world.coastlineReady) RouteEngineState.Ready else world.load()
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

    /** Stage 1 reads nothing that expires between asks. */
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
            try {
                val reach = AppConfig.routeAvoidCorridorReachM
                val first = searchOnce(world, from, to, reach)
                if (first != null) return@withContext first
                val second = searchOnce(world, from, to, reach * 2.0)
                second ?: RouteResult.NoPath
            } finally {
                // The stage is cleared where the call really ends — an answer, a refusal and an
                // abort alike — so the panel never names a search that is not running (R15).
                _stage.value = null
            }
        }

    /** One pass of the pipeline: corridor → harvest → rasterize → A* → taut pull → corner snap → pull. */
    private suspend fun searchOnce(
        world: AvoidWorld,
        from: RoutePoint,
        to: RoutePoint,
        reach: Double
    ): RouteResult.Success? {
        _stage.value = RouteStage.CORRIDOR
        val box = corridorBox(from, to, world.regionBounds, reach) ?: return null
        val edges = world.segmentsIn(box)
        val openCoast = world.openCoastIn(box)
        val capLatNorth = world.regionBounds?.latNorth ?: box.latNorth
        val cellM = AppConfig.routeAvoidGridCellM
        val marginM = AppConfig.routeAvoidObstacleMarginM
        _stage.value = RouteStage.GRID
        val grid = rasterize(box, cellM, marginM, edges, openCoast, capLatNorth)
        grid.forceFree(from.latitude, from.longitude)
        grid.forceFree(to.latitude, to.longitude)
        val startCell = grid.cellOf(from.latitude, from.longitude)
        val aimCell = grid.cellOf(to.latitude, to.longitude)
        _stage.value = RouteStage.SEARCH
        val path = AvoidSearch.search(grid, startCell, aimCell) ?: return null
        val start = from.toLatLng()
        val aim = to.toLatLng()
        val coarse = path.map { grid.center(it.row, it.col) }
        val full = listOf(start) + coarse + listOf(aim)
        val clearance: (LatLng) -> Double = { p -> world.distanceToCoastM(p.latitude, p.longitude) }
        // The grid A* owns the order, the tangent corners own the exact points: pull the cell path
        // taut, then move each bend onto its nearest corner when both neighbouring legs stay clear.
        _stage.value = RouteStage.PULL
        val pulled = AvoidPull.pull(full, start, aim, marginM, clearance)
        val corners = TangentCorners.corners(edges, openCoast, marginM)
        _stage.value = RouteStage.SNAP
        val snapped = snapToCorners(pulled, corners, cellM * 2.0, marginM, clearance, start, aim)
        return success(AvoidPull.pull(snapped, start, aim, marginM, clearance))
    }

    /** Moves a bend onto its nearest tangent corner (within [radiusM]) only when both legs stay clear; open water keeps the bend. */
    private fun snapToCorners(
        path: List<LatLng>,
        corners: List<LatLng>,
        radiusM: Double,
        marginM: Double,
        clearanceM: (LatLng) -> Double,
        start: LatLng,
        aim: LatLng
    ): List<LatLng> {
        if (corners.isEmpty()) return path
        val out = path.toMutableList()
        for (i in 1 until path.size - 1) {
            var nearest: LatLng? = null
            var nearestDist = radiusM
            for (c in corners) {
                val d = SpatialOperations.haversine(path[i], c)
                if (d < nearestDist) {
                    nearest = c
                    nearestDist = d
                }
            }
            val corner = nearest ?: continue
            if (AvoidPull.legClear(out[i - 1], corner, marginM, clearanceM, start, aim) &&
                AvoidPull.legClear(corner, path[i + 1], marginM, clearanceM, start, aim)
            ) {
                out[i] = corner
            }
        }
        return out
    }

    private fun success(waypoints: List<LatLng>): RouteResult.Success {
        val points = waypoints.map { RoutePoint.of(it) }
        val legTimesSec = ArrayList<Double>(max(0, points.size - 1))
        var distanceM = 0.0
        for (i in 0 until points.size - 1) {
            val legM = SpatialOperations.haversine(points[i].toLatLng(), points[i + 1].toLatLng())
            distanceM += legM
            legTimesSec.add(legM / Units.knotsToMps(paceKn()))
        }
        return RouteResult.Success(
            points = points,
            legTimesSec = legTimesSec,
            distanceM = distanceM,
            durationSec = legTimesSec.sum(),
            // The emitted polyline ends at the raw aim, never a resolved node: the pin stands where
            // the user dragged, and the snapped cell is only the search's anchor.
            destinationMoved = false,
            forcedCrossingZoneNames = emptyList()
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
