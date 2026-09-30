package ykws.android.maro.spatial

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import ykws.android.maro.R
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.data.model.RouteResult

/**
 * **The pace every leg of a dummy route is timed at** (R28) — a constant of the placeholder's own,
 * deleted with it.
 *
 * A dummy reads no layer, so it has nothing to price against; the app's own free-water pace would make
 * a *setting* move a line that measured nothing, so the fiction is fixed here where it belongs and the
 * pace the trip figure plans at stays the app's own business.
 */
private const val DUMMY_LEG_PACE_KN = 15.0

/**
 * **The engine the app ships while the routing algorithm is being rethought: a straight line.**
 *
 * It is a placeholder and it says so. It reads **no layer at all** — not the coastline, not the depth
 * grid, not the zones — so it can never be wrong about the water and never right about it either: the
 * line it answers runs over land, over shallows and through a regulated zone alike, because it does
 * not ask.
 *
 * **Its repair is a no-op and it says so.** The repair exists to move a land or too-shallow end to the
 * nearest valid water, and a dummy has no water test to run it with: it cannot tell an invalid end
 * apart from a valid one, so it moves nothing and refuses nothing. That is its honest state — a
 * straight line is what it promised, over whatever the two ends stand on.
 *
 * **One computation, one update.** [routesToCompute] declares a single computation; [startLookup]
 * launches the one answer on an engine-owned scope and emits a single [RouteUpdate] whose `nextStage`
 * is null — no stage exists to report — and whose `result` carries the straight line. [cancelLookup]
 * has nothing to cancel, the answer having been emitted in the frame it was asked for.
 *
 * Coroutines and `Flow` only, like the contract's other implementations.
 */
class RouteDummyEngine : RouteEngine {

    /** One per-engine channel, buffered so an emission never waits on the collector. */
    private val _updates = MutableSharedFlow<RouteUpdate>(extraBufferCapacity = 64)

    override val updates: Flow<RouteUpdate> = _updates.asSharedFlow()

    /** The lane the answer's own job runs on, beside the caller — an engine owns its compute. */
    private val computeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** The two ends [routesToCompute] was last handed — the pair the one computation runs on. */
    private var origin: RoutePoint? = null
    private var destination: RoutePoint? = null

    /** The computation id and lookup id mints: fresh per declaration and per lookup. */
    private var nextComputationId = 0L
    private var nextLookupId = 0L

    override fun routesToCompute(origin: RoutePoint, destination: RoutePoint): RouteDeclarations {
        this.origin = origin
        this.destination = destination
        val computation = RouteComputation(
            id = RouteId(++nextComputationId),
            // The description a user reads for the placeholder's one line is the engine's own label —
            // the same "Straight line" the algorithm dropdown already carries.
            descriptionResId = R.string.route_engine_dummy
        )
        return RouteDeclarations.Available(listOf(computation))
    }

    override fun startLookup(computationId: RouteId): RouteId {
        val lookupId = RouteId(++nextLookupId)
        val from = origin
        val to = destination
        computeScope.launch {
            val result = if (from != null && to != null) straightLine(from, to) else null
            _updates.emit(
                RouteUpdate(
                    routeId = lookupId,
                    stageDone = null,
                    nextStage = null,
                    line = result?.points ?: emptyList(),
                    result = result,
                    reason = null
                )
            )
        }
        return lookupId
    }

    override fun cancelLookup(id: RouteId) {
        // The dummy's one answer is emitted in the frame it is asked for, so a cancelled id is one
        // whose answer has already landed — there is nothing in flight to cancel, and no further
        // update for that id can arrive.
    }

    /**
     * The straight line between the two ends, timed at [DUMMY_LEG_PACE_KN] and nothing else.
     *
     * The polyline is the two ends, `distanceM` is the great-circle distance between them, and the
     * time is that distance at the placeholder's own fixed fiction — so no setting moves a dummy
     * route. Nothing was resolved and nothing crosses.
     */
    private fun straightLine(from: RoutePoint, to: RoutePoint): RouteResult.Success {
        val metres = SpatialOperations.haversine(
            LatLng(from.latitude, from.longitude),
            LatLng(to.latitude, to.longitude)
        )
        val seconds = metres / Units.knotsToMps(DUMMY_LEG_PACE_KN)
        return RouteResult.Success(
            points = listOf(from, to),
            legTimesSec = listOf(seconds),
            distanceM = metres,
            durationSec = seconds,
            destinationMoved = false
        )
    }
}
