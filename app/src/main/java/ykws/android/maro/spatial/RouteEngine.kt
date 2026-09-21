package ykws.android.maro.spatial

import kotlinx.coroutines.flow.StateFlow
import ykws.android.maro.R
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.data.model.RouteResult

/**
 * **The route's engine seam: one contract, and the slot a second engine fills.**
 *
 * An engine answers one question — *where does the water let a boat go from here to there, and what
 * does that line cost* — and it answers it in [RouteResult]. It takes the start the view model froze
 * when the mode was armed, the aim it is pointing at, and the pace the trip figure plans at; it
 * takes nothing else, because everything else is the world it reads for itself.
 *
 * **Where a second engine slots in, and which one it is.** A second engine implements this interface
 * and is constructed in place of the one that ships; nothing else in the app changes. The toggle gates
 * on [state], [RouteViewModel]'s two callers hand [route] the three answers above, and everything
 * downstream — [`ykws.android.maro.ui.map.RoutePlan`], the trip figure, the save — reads [RouteResult]
 * and that plan, never an engine. **The taut-string corridor tracer is what ships**
 * ([`ykws.android.maro.spatial.taut.TautRouteEngine`], built in `spatial/taut/` and switched in at one
 * expression in [`ykws.android.maro.ui.map.MapScreen`]), while
 * [`ykws.android.maro.spatial.mesh.MeshRouteEngine`] and its bake stay in the tree wired to nothing —
 * which is the slot this paragraph described before the replacement existed, and the reason a swap is
 * still one expression.
 *
 * **Cancellation is the caller's.** [route] is a suspend function and checks the calling job between
 * the steps of its search, so cancelling the coroutine drops a search in flight — which is what makes
 * a flung map ask for the route of the moment rather than queueing one per aim.
 *
 * Coroutines and `StateFlow` only: an engine holds no thread of its own and its readiness is a value
 * the UI can collect.
 */
interface RouteEngine {

    /**
     * What this engine can do right now — the feature's own gate, and [RouteEngineState.ready] is
     * the single reading of it.
     *
     * It is a stream rather than a query because readiness arrives late: an engine that must read a
     * baked asset, or harvest a corridor, is not ready on the frame it is constructed.
     */
    val state: StateFlow<RouteEngineState>

    /**
     * Makes the engine ready if it can be, and reports what it reached.
     *
     * The caller asks once and reads the answer; an engine that is already ready returns immediately,
     * and one that cannot be is [RouteEngineState.Unavailable] rather than an exception. It is
     * separate from [route] so that the gate — not a search — is what moves the state.
     *
     * **What readiness does not promise.** [RouteEngineState.Ready] says the engine *can* answer; it
     * says nothing about how much of the world the engine has read for itself. An engine that prices
     * from live layers may answer **permissively** until those layers have landed — water where the
     * coastline has not been read, no zone where the regulation has not — and it picks them up on a
     * later search rather than blocking the first. So the permissive default is observable on the
     * first search of a session, and it is exactly what a bundle-reading engine reached after decoding
     * its fabric alone: the mesh engine that used to ship loaded its mesh in [prepare] and the live
     * layers per search. An engine for which a permissive answer is not acceptable must not report
     * [RouteEngineState.Ready] until it can answer honestly — a promise this interface leaves to the
     * engine, because only it knows what it reads — and the tracer that ships today takes that second
     * path on both layers.
     */
    suspend fun prepare(): RouteEngineState

    /**
     * The route from [start] to [aim], at [cruiseSpeedKn].
     *
     * @param start        where the boat is, read once when the mode was armed — never the aim, and
     *                     never a fresher fix: the anchor is what a preview is asked from.
     * @param aim          where the destination is being pointed, which the engine may resolve
     *                     elsewhere — see [RouteResult.Success.destinationMoved].
     * @param cruiseSpeedKn the pace the caller plans at (kn); it is the free-water pace until the
     *                     boat's own samples have something to say.
     * @return a [RouteResult.Success], or the named failure that says which end had no water or that
     *         no path connects the two.
     */
    suspend fun route(start: RoutePoint, aim: RoutePoint, cruiseSpeedKn: Double): RouteResult
}

/** What an engine can do right now — its readiness, as a value the toggle and the view model read. */
sealed interface RouteEngineState {

    /**
     * Whether the engine can answer a route. **This is the feature's gate**, and it is a pure
     * function of the state: the toggle opens for an engine that is ready and for no other, whichever
     * engine it is.
     */
    val ready: Boolean

    /** Nothing has been prepared yet — the ordinary state of an engine whose fabric is still being read. */
    data object NotReady : RouteEngineState {
        override val ready: Boolean get() = false
    }

    /**
     * The engine can answer.
     *
     * A promise about *answering*, not about how much of the world the engine has already read: see
     * [RouteEngine.prepare] for the permissive default a live-reading engine may answer with, and
     * when it can be observed.
     */
    data object Ready : RouteEngineState {
        override val ready: Boolean get() = true
    }

    /**
     * The engine cannot answer, and [reason] says why **in a closed set rather than in free text** —
     * so a caller can branch on it and the sentence a user reads is a resource, not a string built
     * somewhere in the engine.
     */
    data class Unavailable(val reason: RouteUnavailableReason) : RouteEngineState {
        override val ready: Boolean get() = false
    }
}

/**
 * Why an engine cannot answer, as the closed set of things that can be wrong.
 *
 * Each entry carries the id of the line a user reads ([labelResId], resolved by the surface that
 * shows it — the `CustomSortField` shape), so no engine ever holds user-facing text and both locales
 * carry the key.
 */
enum class RouteUnavailableReason(val labelResId: Int) {

    /**
     * The region has no baked routing mesh — the one reason **only the mesh engine can give**, and
     * the reason the disabled toggle used to spell as "no mesh".
     */
    REGION_NOT_BAKED(R.string.route_unavailable_region_not_baked),

    /**
     * The depth grid is not in, and a corridor tracer will not answer without it.
     *
     * It is **the replacement engine's own reason**, and it is a refusal rather than a delay: the wall a
     * route is held off the shore by is the 2 m contour the soundings draw, so a search run before the
     * grid lands would price unsounded water as open sea and call the answer a route. The seam's contract
     * leaves each engine the choice of answering permissively until its world is in — this one declines
     * it, which is why its readiness waits on a layer the depth screen would otherwise load on its own.
     */
    DEPTH_NOT_LOADED(R.string.route_unavailable_depth_not_loaded),

    /**
     * The coastline is not in, and a corridor tracer will not answer without it.
     *
     * It is the **second half of the same refusal** (§17 item 3): the tracer's wall is land dilated by
     * the berth and the 2 m contour where the soundings are fine, so a search run before the coastline
     * lands would read land as water — `isWater` answers permissively — leave the shore offset applying
     * nowhere and draw a line over the shore as an ordinary route. The depth grid alone was the gate the
     * built engine checked, and the window between the two layers is exactly what this names.
     */
    COASTLINE_NOT_LOADED(R.string.route_unavailable_coastline_not_loaded)
}
