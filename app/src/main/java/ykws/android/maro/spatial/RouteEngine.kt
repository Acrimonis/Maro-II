package ykws.android.maro.spatial

import kotlinx.coroutines.flow.StateFlow
import ykws.android.maro.R
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.data.model.RouteResult

/**
 * **The route's engine seam: a session, and the slot the next engine fills.**
 *
 * An engine is told where each end of the route is and answers what the water allows between them —
 * which is why this is a **session rather than a function**. It is told the origin once, when the mode
 * is armed, and the destination as the user drags it; during the following phase the origin moves and
 * the destination stands, and the reverse holds while the destination is being chosen. An engine that
 * is told one end moved **holds the other**, which is where a cache may live — the one thing a
 * `route(start, aim, pace)` signature could not offer, and the reason this interface replaced it.
 *
 * **What ships today is a placeholder.** [RouteDummyEngine] is the only implementation: one straight
 * line from the origin to the destination, reading no coastline, no soundings and no zone, and timed
 * at a fiction of its own — 15 kn on every leg (R28) — so the app's own pace setting does not move a
 * dummy route while the placeholder ships. It exists so the feature stays whole — the toggle, the aim,
 * the phases, the refresh, the ladder, the save — while the two engines that came before it were
 * removed on 2026-09-22. Each of those is written up where it went:
 * `xTrack/Route/260922_FEAT_DOC_Route_mesh-engine.md` and `…_taut-tracer.md`.
 *
 * **Where the next engine slots in.** An engine implements this interface and is constructed in place
 * of the dummy; nothing else in the app changes. The toggle gates on [state], and everything
 * downstream — [`ykws.android.maro.ui.map.RoutePlan`], the trip figure, the save — reads the answer
 * and that plan, never an engine.
 *
 * **Cancellation is the caller's**, and it is load-bearing: the previous call is cancelled when a new
 * one starts, so a flung map never queues behind a computation nobody wants any more. An engine that
 * searches checks the calling job between its steps; the dummy has nothing to interrupt and says so
 * where it answers.
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
     * separate from the two entry points so that the gate — not a search — is what moves the state.
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
     * **The validity question — one point, one answer.** `null` when the point is usable water, or the
     * id of the line a user reads when it is not, from the closed set [RouteRefusalReason].
     *
     * Whether a point is usable water is the algorithm's judgement and the feature only reports what it
     * is given: the caller paints the crosshair and shows the sentence, and it never guesses. The
     * **destination** is judged as it moves; the **origin** is judged **once, when the mode is armed,
     * and never on a refresh** (R7) — the boat's own position is not a target being placed, and
     * re-judging it while a route is followed would refuse a route the user already accepted.
     *
     * The dummy judges nothing: it answers `null` for every point, which makes every refusal below
     * unreachable while it is the installed engine.
     */
    suspend fun validatePoint(point: RoutePoint): RouteRefusalReason?

    /**
     * The **origin** moved — the arming call and, later, the following mode's refresh (R9).
     *
     * An engine told this holds the destination it was last given; the answer is the route between the
     * two, or `null` when it holds no destination yet, which is the arming call and nothing else: a
     * position was *told*, and no route was asked for.
     */
    suspend fun onOriginPositionChanged(newPosition: RoutePoint): RouteResult?

    /**
     * The **destination** moved while it was being chosen (R8).
     *
     * An engine told this holds the origin it was last given; the answer is the route from that origin
     * to [newPosition], or `null` when no origin is held — which cannot happen through the feature,
     * the origin being told on the arming frame before any aim can be asked for.
     */
    suspend fun onDestinationPositionChanged(newPosition: RoutePoint): RouteResult?

    /**
     * **The refresh's veto, not its clock** (R11).
     *
     * The app owns when a refresh may be asked — its two thresholds are the app's own keys — and this
     * answers only whether the engine can take the call. A `false` delays the refresh; it never
     * triggers one, and it never fails a route: the standing line holds until a replacement arrives.
     * The dummy is always ready to recompute, having nothing to wait for.
     */
    suspend fun isReadyToRecompute(): Boolean
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

/**
 * **Why a point is not usable water** — the closed set [RouteEngine.validatePoint] answers with.
 *
 * Shaped like [RouteUnavailableReason] and for the same reason: a reason is an id the surface resolves
 * ([labelResId]), never a string an engine built, so both locales carry the key and no engine holds
 * user-facing text. It answers about **one end**, and both ends read the same: a refused aim and a
 * refused origin paint the same crosshair (R27).
 *
 * **Nothing produces one of these today** — the dummy judges nothing, so every entry is unreachable
 * while it is the installed engine. They are kept because the ring's refused state, the panel's
 * sentence and the strings in both locales are wired to this type, and because an engine that reads
 * the water again will need exactly these sentences; each entry names what it meant to the engine that
 * produced it, in the past tense.
 */
enum class RouteRefusalReason(val labelResId: Int) {

    /**
     * The point is not on water the engine can see: the removed corridor tracer raised this for either
     * end before it built anything, because a line cannot be drawn from or to a place that is land.
     */
    OFF_WATER(R.string.route_refusal_off_water),

    /**
     * The point lies outside the region the engine has read — beyond the box the removed engines cut
     * for themselves, where no soundings, no shoreline and no zone exist at all.
     */
    OUTSIDE_COVERAGE(R.string.route_refusal_outside_coverage)
}
