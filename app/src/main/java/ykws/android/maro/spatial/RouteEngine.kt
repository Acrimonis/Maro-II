package ykws.android.maro.spatial

import kotlinx.coroutines.flow.StateFlow
import ykws.android.maro.R
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.data.model.RouteResult

/**
 * **The route's engine seam: one contract, and the slot the next engine fills.**
 *
 * An engine answers one question — *where does the water let a boat go from here to there, and what
 * does that line cost* — and it answers it in [RouteResult]. It takes the start the view model froze
 * when the mode was armed, the aim it is pointing at, and the pace the trip figure plans at; it takes
 * nothing else, because everything else is the world it reads for itself.
 *
 * **What ships today is a placeholder.** [RouteDummyEngine] is the only implementation: one straight
 * line from the start to the aim, reading no coastline, no soundings and no zone. It exists so the
 * feature stays whole — the toggle, the aim, the confirmation and the save all work as they will over a
 * real answer — while the two engines that came before it were removed on 2026-09-22. Each of those is
 * written up where it went: [`ykws.android.maro.spatial`]'s neighbours are gone, and their flow, limits
 * and measurements live in `xTrack/Route/260922_FEAT_DOC_Route_mesh-engine.md` and
 * `…_taut-tracer.md`.
 *
 * **Where the next engine slots in.** An engine implements this interface and is constructed in place
 * of the dummy; nothing else in the app changes. The toggle gates on [state], [RouteViewModel]'s two
 * callers hand [route] the three answers above, and everything downstream —
 * [`ykws.android.maro.ui.map.RoutePlan`], the trip figure, the save — reads [RouteResult] and that
 * plan, never an engine.
 *
 * **Cancellation is the caller's.** [route] is a suspend function and an engine that searches checks
 * the calling job between its steps, so cancelling the coroutine drops a search in flight — which is
 * what makes a flung map ask for the route of the moment rather than queueing one per aim. The dummy
 * has nothing to interrupt and says so where it answers.
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
     * baked asset, or harvest a corridor, is not ready on the frame it is constructed. The dummy is the
     * degenerate case of the stream — it is ready on the frame it is built — and that is a property of
     * the dummy, not a licence for the next engine to assume it.
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
     * later search rather than blocking the first. An engine for which a permissive answer is not
     * acceptable must not report [RouteEngineState.Ready] until it can answer honestly — a promise this
     * interface leaves to the engine, because only it knows what it reads. The dummy takes the other
     * extreme and reads nothing at all, which is why its answer is honest and useless in equal measure.
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

    /** Nothing has been prepared yet — the ordinary state of an engine whose world is still being read. */
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
 *
 * **Nothing produces one of these today.** The dummy is ready on construction and can never answer
 * [RouteEngineState.Unavailable], so every entry below is unreachable while it is the installed engine;
 * they are kept because the toggle's refusal path, its snackbar and the two locales' strings are all
 * wired to this type, and because an engine that reads the water again will need exactly these
 * sentences. Its own KDoc says what each one meant to the engine that produced it, in the past tense,
 * rather than inventing a reading no code takes.
 */
enum class RouteUnavailableReason(val labelResId: Int) {

    /**
     * The region has no baked routing fabric — the reason the removed mesh engine alone could give, and
     * the reason the disabled toggle used to spell as "no mesh".
     */
    REGION_NOT_BAKED(R.string.route_unavailable_region_not_baked),

    /**
     * The depth grid is not in, and the removed corridor tracer would not answer without it: the wall a
     * route is held off the shore by is the 2 m contour the soundings draw, so a search run before the
     * grid landed would have priced unsounded water as open sea and called the answer a route.
     */
    DEPTH_NOT_LOADED(R.string.route_unavailable_depth_not_loaded),

    /**
     * The coastline is not in — the second half of that same refusal: the tracer's wall was land
     * dilated by the berth, so a search run before the coastline landed would have read land as water
     * and drawn a line over the shore as an ordinary route.
     */
    COASTLINE_NOT_LOADED(R.string.route_unavailable_coastline_not_loaded)
}
