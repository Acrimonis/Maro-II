package ykws.android.maro.spatial

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.data.model.RouteResult

/**
 * **The avoidance algorithm's stand-in: the same straight line as the dummy, priced at the pace in
 * force.**
 *
 * It is a placeholder and it says so. It reads **no layer at all** — not the coastline, not the depth
 * grid, not the zones — so it promises no water, no zone and no berth, exactly like [RouteDummyEngine].
 * What tells the two apart is the pace: the dummy times its line at a fixed fiction of its own (R28),
 * while this engine times the same line at the pace its injected provider answers — the pace the app
 * already plans the trip figure at. The harness is the deliverable and the avoidance search is not; this
 * engine exists so the registry can be exercised with a second row before any search exists.
 *
 * **It is a session, like the contract.** It holds both ends as it is told them: the origin arrives on
 * the arming frame, the destination with each aim, and during the following phase the origin moves
 * while the destination stands. Each entry point answers the line between the two ends it holds — from
 * the origin to the destination, so the polyline's own direction is the direction of travel — and
 * `null` while the other end is not held yet.
 *
 * **Readiness is immediate, and that is a property of this engine, not of the contract.** [prepare]
 * answers [RouteEngineState.Ready] the first time it is asked because there is nothing to wait for, so
 * the toggle is never disabled while this engine is the one installed. It judges nothing either —
 * [validatePoint] answers `null` for every point — and it is always [isReadyToRecompute], having
 * nothing to wait for.
 *
 * **What the answer means.** The polyline is the two ends, `distanceM` is the great-circle distance
 * between them, and the time is that distance at the pace in force — the trip figure's own pace, asked
 * of the injected provider at the moment the answer is asked, so the pace setting moves this route and
 * the dummy's fixed fiction stays the dummy's alone. Nothing else is answered: there is no destination
 * to resolve, no band to be in and no zone to cross.
 *
 * Coroutines and `StateFlow` only, like the contract's other implementations: no thread of its own and
 * a readiness the UI can collect.
 */
class RouteAvoidEngine(
    /** The pace in force (kn), asked fresh on every answer so a slider move reaches the next line. */
    private val paceKn: () -> Double
) : RouteEngine {

    /** It cannot be anything but ready — see the class note on what that does and does not promise. */
    private val _state = MutableStateFlow<RouteEngineState>(RouteEngineState.Ready)

    override val state: StateFlow<RouteEngineState> = _state.asStateFlow()

    /** The end the mode froze when it was armed — told once, and held for the whole session. */
    private var origin: RoutePoint? = null

    /** The aim last dragged — told with each ask, and held while the following phase moves the origin. */
    private var destination: RoutePoint? = null

    override suspend fun prepare(): RouteEngineState {
        _state.value = RouteEngineState.Ready
        return RouteEngineState.Ready
    }

    /**
     * A straight line runs over land, over shallows and through a zone alike — so it has nothing to
     * refuse, and every point is usable water as far as this engine can tell. See the class note.
     */
    override suspend fun validatePoint(point: RoutePoint): RouteRefusalReason? = null

    /** Holds the origin; answers the line to the destination it holds, or `null` while it holds none. */
    override suspend fun onOriginPositionChanged(newPosition: RoutePoint): RouteResult? {
        origin = newPosition
        return lineBetween(origin, destination)
    }

    /** Holds the destination; answers the line from the origin it holds, or `null` while it holds none. */
    override suspend fun onDestinationPositionChanged(newPosition: RoutePoint): RouteResult? {
        destination = newPosition
        return lineBetween(origin, destination)
    }

    /** Nothing to wait for: a straight line is answered in the frame it is asked for. */
    override suspend fun isReadyToRecompute(): Boolean = true

    /**
     * The straight line between the two ends, timed at the pace in force and nothing else.
     *
     * `null` while either end is missing, which is the arming call — the origin told before any aim
     * exists — and never a moment the feature can ask a route in.
     */
    private fun lineBetween(from: RoutePoint?, to: RoutePoint?): RouteResult? {
        if (from == null || to == null) return null
        val metres = SpatialOperations.haversine(
            LatLng(from.latitude, from.longitude),
            LatLng(to.latitude, to.longitude)
        )
        val seconds = metres / Units.knotsToMps(paceKn())
        return RouteResult.Success(
            points = listOf(from, to),
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
