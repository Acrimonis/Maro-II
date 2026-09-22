package ykws.android.maro.spatial

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.data.model.RouteResult

/**
 * **The engine the app ships while the routing algorithm is being rethought: a straight line.**
 *
 * It is a placeholder and it says so. It reads **no layer at all** — not the coastline, not the depth
 * grid, not the zones — so it can never be wrong about the water and never right about it either: the
 * line it answers runs over land, over shallows and through a regulated zone alike, because it does
 * not ask. What it is for is keeping the whole feature usable and observable while the search itself
 * is missing: the toggle, the aim, the preview, the confirmation panel, the trip figure, the save and
 * the seam all work exactly as they will over a real answer, so the next engine is a swap at one
 * expression rather than a rebuild of the feature.
 *
 * **Readiness is immediate, and that is a property of this engine, not of the contract.** [prepare]
 * answers [RouteEngineState.Ready] the first time it is asked because there is nothing to wait for, so
 * the toggle is never disabled and the refusals [RouteUnavailableReason] names are unreachable while
 * this engine is the one installed. They are kept because they are the seam's shape and a live-reading
 * engine needs them; this engine simply has no producer for them.
 *
 * **What the answer means.** The polyline is the two points, `distanceM` is the great-circle distance
 * between them, and the time is that distance at the pace the caller planned at — the same pace the
 * dashboard's trip figure divides by, so the figure moves by itself as the boat gets closer. Nothing
 * else is answered: there is no destination to resolve, no band to be in and no zone to cross.
 *
 * Coroutines and `StateFlow` only, like the contract's other implementation: no thread of its own and
 * a readiness the UI can collect.
 */
class RouteDummyEngine : RouteEngine {

    /** It cannot be anything but ready — see the class note on what that does and does not promise. */
    private val _state = MutableStateFlow<RouteEngineState>(RouteEngineState.Ready)

    override val state: StateFlow<RouteEngineState> = _state.asStateFlow()

    override suspend fun prepare(): RouteEngineState {
        _state.value = RouteEngineState.Ready
        return RouteEngineState.Ready
    }

    /**
     * The straight line from [start] to [aim], priced at [cruiseSpeedKn] and nothing else.
     *
     * A pace of zero or less answers a zero time rather than an infinite one, because the pace is the
     * caller's number and a division by it here would be this class inventing a refusal the contract
     * does not have.
     */
    override suspend fun route(
        start: RoutePoint,
        aim: RoutePoint,
        cruiseSpeedKn: Double
    ): RouteResult {
        val metres = SpatialOperations.haversine(
            LatLng(start.latitude, start.longitude),
            LatLng(aim.latitude, aim.longitude)
        )
        val seconds = if (cruiseSpeedKn > 0.0) metres / Units.knotsToMps(cruiseSpeedKn) else 0.0
        return RouteResult.Success(
            points = listOf(start, aim),
            legTimesSec = listOf(seconds),
            distanceM = metres,
            durationSec = seconds,
            // Nothing was resolved, nothing crosses and the aim is where the line ends: the destination
            // is the aimed point itself, so the pin is drawn where the user dragged. `destinationMoved`
            // is the only reading left to answer, and a straight line resolves nothing.
            destinationMoved = false
        )
    }
}
