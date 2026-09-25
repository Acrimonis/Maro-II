package ykws.android.maro.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.data.model.RouteResult
import ykws.android.maro.data.route.RoutePace
import ykws.android.maro.data.track.TrackFromCourse
import ykws.android.maro.spatial.RouteEngine
import ykws.android.maro.spatial.RouteEngineState
import ykws.android.maro.spatial.RouteRefusalReason
import ykws.android.maro.spatial.RouteStage
import ykws.android.maro.spatial.SpatialOperations
import ykws.android.maro.spatial.Units

/** How much of a confirmed route is still ahead of a position. */
data class RouteRemainder(val distanceM: Double, val durationSec: Double)

/**
 * A resolved route: the polyline the line is drawn from, where the route really ends, and what it
 * costs.
 *
 * [destination] is the **resolved** end — the closest node of the boat's own stretch — never the raw
 * aim, which is what lets the pin be drawn where the route actually finishes.
 *
 * [computedAtMs] is the instant the route was **generated and finalised**, not the instant it was
 * saved: it is what dates the route's header and what names the track it becomes (R40), so the one
 * value feeds both rather than the header and the name reading two different clocks.
 */
data class RoutePlan(
    val start: RoutePoint,
    val destination: RoutePoint,
    val destinationMoved: Boolean,
    val points: List<RoutePoint>,
    val legTimesSec: List<Double>,
    val distanceM: Double,
    val durationSec: Double,
    /**
     * The priced speed zones the route had to enter, by name — empty on an ordinary route.
     *
     * A crossing is forbidden while a way around exists, so a name here says no way around was found
     * and the crossing was priced and taken. It rides on the plan rather than being inferred from the
     * line, because the panel and the trip card have to *say* it: a forced crossing reported as an
     * ordinary one is the failure the fallback exists to avoid.
     */
    val forcedCrossingZoneNames: List<String> = emptyList(),
    val computedAtMs: Long
) {
    /** The plan's length in nautical miles — the trip figure's own unit. */
    val distanceNm: Double get() = Units.metresToNauticalMiles(distanceM)

    /** The pace the plan itself holds (kn) — the distance it covers over the time it allots. */
    val plannedPaceKn: Double
        get() = if (durationSec > 0.0) Units.mpsToKnots(distanceM / durationSec) else 0.0

    /**
     * **The name a track built from this route takes** (R25): the route's own
     * generation-and-finalisation instant with the fixed `Route ` prefix.
     *
     * One home for the naming rule, so **every** door that saves a route reads it. The `· n/N` suffix
     * the withdrawn all-scope save once needed is gone with it, so one save path names a route one way.
     */
    fun trackName(): String = TrackFromCourse.routeTrackName(computedAtMs)

    /**
     * What is left of the route from [from] onward.
     *
     * The position is snapped to its **nearest vertex** rather than projected onto a leg, which is
     * exact where a leg is long and a boat is between two of them rather than on one — and the dummy
     * answers a single leg, so the two agree everywhere it matters. The sum reaches zero at the
     * destination, which is the one reading that must be exact: arrival is the trip cell reading zero.
     */
    fun remainingFrom(from: RoutePoint): RouteRemainder {
        if (points.size < 2) return RouteRemainder(distanceM, durationSec)
        val nearest = nearestVertexIndex(from)
        var remainingM = 0.0
        var remainingSec = 0.0
        for (leg in nearest until points.size - 1) {
            remainingM += SpatialOperations.haversine(
                LatLng(points[leg].latitude, points[leg].longitude),
                LatLng(points[leg + 1].latitude, points[leg + 1].longitude)
            )
            remainingSec += legTimesSec.getOrElse(leg) { 0.0 }
        }
        return RouteRemainder(remainingM, remainingSec)
    }

    /** The index of the polyline vertex nearest [from] — the one snap both readings of this plan use. */
    fun nearestVertexIndex(from: RoutePoint): Int {
        var bestIndex = 0
        var bestDistance = Double.MAX_VALUE
        for ((index, point) in points.withIndex()) {
            val metres = SpatialOperations.haversine(
                LatLng(from.latitude, from.longitude),
                LatLng(point.latitude, point.longitude)
            )
            if (metres < bestDistance) {
                bestDistance = metres
                bestIndex = index
            }
        }
        return bestIndex
    }

    companion object {
        /** The plan a successful search answers with, dated the moment it landed. */
        fun of(start: RoutePoint, result: RouteResult.Success, nowMs: Long): RoutePlan {
            val destination = result.points.lastOrNull() ?: start
            return RoutePlan(
                start = start,
                destination = destination,
                destinationMoved = result.destinationMoved,
                points = result.points,
                legTimesSec = result.legTimesSec,
                distanceM = result.distanceM,
                durationSec = result.durationSec,
                forcedCrossingZoneNames = result.forcedCrossingZoneNames,
                computedAtMs = nowMs
            )
        }
    }
}

/**
 * **The phase the mode is in** — the two on-phases and off, named once so the couplings that belong to
 * a phase can key on the phase rather than on the mode's switch (R20, R21).
 *
 * The demo suspension and the camera hold belong to [CHOOSING] and to nothing else: once a route is
 * followed the map sails and recentres exactly as it does with no route at all. [IDLE] is the mode
 * being off.
 */
enum class RoutePhase {

    /** The mode is off: no aim, no line, no route. */
    IDLE,

    /** The destination is being acquired: the aim moves, the camera is held and demo's speed is suspended. */
    CHOOSING,

    /** A route is followed: the camera follows the boat and the line is locked. */
    FOLLOWING
}

/**
 * The route's state machine: **Idle → Choosing → Following**, and nothing else.
 *
 * There is deliberately no arrival state and no arrival cue: reaching the destination is the trip
 * figure reading zero while the line stays drawn, and a route ends only on an explicit exit. A
 * [Choosing] carries a null plan while it is aiming with nothing acquired, which is an ordinary
 * moment — nothing asked yet, or an aim no route answers — not an error.
 */
sealed interface RouteState {

    /** The phase this state is — the couplings' own key, derived from the state rather than beside it. */
    val phase: RoutePhase

    /** The front route a state carries, or null while there is none. */
    val plan: RoutePlan?

    data object Idle : RouteState {
        override val phase: RoutePhase get() = RoutePhase.IDLE
        override val plan: RoutePlan? get() = null
    }

    /**
     * **Acquiring the destination**, from [start]: [plan] is the front line, or null while nothing has
     * been acquired.
     *
     * [start] is the acquisition's **anchor**, resolved once on its own entry edge (R3) and held for
     * the whole acquisition — and for the plan a confirmation locks, which is that same point. An
     * anchor per acquisition, rather than one per session, is what makes a reroute a recompute rather
     * than the same line drawn again; holding it here is what makes it frozen by construction, since
     * while the acquisition runs there is no position left to re-read. It is null before the first fix,
     * and a search is refused rather than invented while it is.
     *
     * [ladder] is the session's earlier lines, oldest first — the ones a reroute or a new route leaves
     * standing, and the ones a superseded acquisition answer joins. The drawing paints them and
     * nothing else reads them.
     *
     * [searching] says an acquisition is in flight, which is what tells "not yet" apart from "no route
     * to this aim" — the two read identically from a null plan and mean opposite things to whoever is
     * watching the map. [refusal] is the third reading: the aim is not usable water, and the sentence
     * naming the reason is shown with the outcomes hidden while no plan exists (R6) — a refused aim
     * therefore carries **no plan at all**, dropping any line a previous aim resolved.
     *
     * [originRefusal] is the **anchor's** own refusal, judged on every entry into the acquisition
     * (R7): it reads as a line about the position rather than as one about the target.
     *
     * [enteredFromRoute] says a route stands behind this acquisition, which is what makes its Exit and
     * the back key **phase moves** rather than endings, and what makes the toggle's off ask first
     * (R23).
     */
    data class Choosing(
        val start: RoutePoint?,
        override val plan: RoutePlan?,
        val ladder: List<RoutePlan> = emptyList(),
        val searching: Boolean = false,
        /**
         * Whether an aim has been asked at all. It is what holds the refusal back: "no route to this
         * aim" and "nothing asked yet" both read as a null plan, and saying the first one on the frame
         * the mode opens would blink a refusal the next acquisition is about to contradict.
         */
        val asked: Boolean = false,
        val refusal: RouteRefusalReason? = null,
        val originRefusal: RouteRefusalReason? = null,
        val enteredFromRoute: Boolean = false
    ) : RouteState {
        override val phase: RoutePhase get() = RoutePhase.CHOOSING
    }

    /**
     * Following a route.
     *
     * [routes] is **the session, and the ladder is the same collection** (R25): every route the
     * session produced, in creation order, the followed one last and the replaced ones before it. The
     * drawing caps how many of the stale ones it paints, so there is one collection rather than two
     * that can drift.
     */
    data class Following(
        val routes: List<RoutePlan>
    ) : RouteState {
        init {
            require(routes.isNotEmpty()) { "a following phase holds at least the route it followed" }
        }

        override val phase: RoutePhase get() = RoutePhase.FOLLOWING

        /** The front route — the newest accepted answer, and what the trip figure describes. */
        override val plan: RoutePlan get() = routes.last()
    }
}

/**
 * One route of the session, with the track it was written as if it has been saved.
 *
 * The link lives **here**, in the mode's own session, rather than on the track: `TrackFromCourse`'s
 * own KDoc deliberately refused such a field, and a route is the mode's own object — the link is
 * needed while the mode runs and means nothing once it ends.
 */
data class SessionRoute(val plan: RoutePlan, val trackId: String? = null)

/**
 * All of the Route feature's runtime state, in one place and on `StateFlow`.
 *
 * The engine is prepared once at construction, and **again whenever the toggle's own tap asks** — the
 * gate recovers per interaction and never in a loop, because an engine still not ready after a completed
 * preparation has said so.
 *
 * **One worker serves every acquisition** (R4). A new ask **cancels the one in flight and starts**
 * rather than queueing behind it, and the pending slot keeps the newest aim alone. **The front line
 * deliberately survives an abort**: an answer nobody wants is dropped, not the line already standing.
 *
 * **Nothing is computed until the user asks** (R2): the acquisition's **Acquire route** action is the
 * only trigger, and the engine's own held ends are told as part of that first ask — so arming the mode,
 * a drag and a new route all compute nothing. The automatic refresh the following phase used to run is
 * gone, so no clock, no gate and no threshold survives.
 *
 * The pace is the trip figure's: the set free-water pace until the boat's own samples have something
 * to say, then [RoutePace]'s own reduction of them. **The engine is not told the pace** — the dummy
 * times its own legs at a fixed fiction (R28) and the avoid engine reads the pace in force itself, so
 * no engine takes it as an input.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RouteViewModel(

    /**
     * The **selection** every search of this feature may run on: a stream of the engine the chosen
     * algorithm builds, handed in by whoever builds the view model.
     *
     * It is a **selection, not an instance**, and that is the seam: the caller already holds the
     * setting and the registry, so a second algorithm is one row and one expression at that one site
     * rather than an edit inside this file, and a test can hand in a foreign engine and read what the
     * feature made of its answer. Nothing here names a particular engine.
     *
     * The selection is resolved **when the mode arms**: while no route runs the view model reads
     * the selected engine's readiness — the toggle's gate — and on the move into `Choosing` it captures
     * [StateFlow.value] as the session engine, held for the whole mode and released on the return to
     * `Idle`. A selection changed mid-route therefore cannot reach the line already drawn.
     */
    private val selection: StateFlow<RouteEngine>
) : ViewModel() {

    /**
     * The engine the running session was armed with, or null while the mode is idle.
     *
     * It is set on the Idle → Choosing edge from [selection]'s current value and cleared on the return
     * to `Idle`, which is what freezes a live line against a settings change: every engine call below
     * goes through [engine], so a selection changed mid-route can never touch the route already drawn.
     */
    private val _sessionEngine = MutableStateFlow<RouteEngine?>(null)

    /** The engine the feature calls right now — the session's own while one runs, else the selected. */
    private fun engine(): RouteEngine = _sessionEngine.value ?: selection.value

    /**
     * What the engine in force can do right now — the toggle's gate reads it.
     *
     * While no route runs this follows the **selected** engine, so a chosen algorithm that is not ready
     * keeps the toggle closed; once a session engine is captured it follows that one for the whole mode.
     */
    private val _engineState = MutableStateFlow<RouteEngineState>(RouteEngineState.NotReady)

    val engineState: StateFlow<RouteEngineState> = _engineState.asStateFlow()

    /**
     * **The stage of the acquisition running right now** (R15), or null when none is — proxied from the
     * engine in force, so the panel reads one value whatever engine is installed.
     */
    private val _stage = MutableStateFlow<RouteStage?>(null)

    val stage: StateFlow<RouteStage?> = _stage.asStateFlow()

    /**
     * One preparation of the engine in force, published to the gate **synchronously** — the
     * acquisition's retry re-reads [engineState] in the same frame, so the answer must land there before
     * the recursive ask runs rather than wait on the collector's next emission.
     */
    private suspend fun prepareCurrent(): RouteEngineState {
        val state = engine().prepare()
        _engineState.value = state
        return state
    }

    private val _state = MutableStateFlow<RouteState>(RouteState.Idle)
    val state: StateFlow<RouteState> = _state.asStateFlow()

    private val _paceKn = MutableStateFlow(AppConfig.routeFreeWaterPaceKn.toDouble())

    /** The pace the trip figure plans at (kn) — set pace, or the boat's own once it has evidence. */
    val paceKn: StateFlow<Double> = _paceKn.asStateFlow()

    /**
     * **The one worker** — every acquisition runs here, and a new ask cancels the one in flight rather
     * than queueing behind it (R4). One field, so two asks can never be alive together.
     */
    private var askJob: Job? = null

    /**
     * **The ask's own slot: the newest aim no worker has taken up yet.**
     *
     * A press asks once; the machine answers by overwriting this one field, so a second press landing
     * while the first is still computing is *replaced* rather than queued.
     */
    private var pendingAim: RoutePoint? = null

    /**
     * Whether the acquisition's anchor has reached the engine yet.
     *
     * The anchor is told **once per acquisition**, and never on the mode's own arming frame: an engine
     * told an origin while it still holds the previous session's destination computes a route nobody
     * asked for, which is exactly what R2 forbids. It is told by [startWorker] on the first ask, and by
     * [reroute]'s own entry call, which *is* that acquisition.
     */
    private var anchorTold: Boolean = false

    /**
     * The session's routes and, for the ones already written, the track each became (R25).
     *
     * A `LinkedHashMap` rather than a list beside a map, so the creation order the drawing reads and
     * the route-to-track link cannot drift apart. It dies with the mode, as the link does. Its
     * **reactive mirror** is [_sessionLinks], so the panel can grey a Save without a recomposition
     * reading a plain map.
     */
    private val session = LinkedHashMap<RoutePlan, String?>()

    /** The session as the UI reads it: the link table alone, republished on every write. */
    private val _sessionLinks = MutableStateFlow<Map<RoutePlan, String?>>(emptyMap())
    val sessionLinks: StateFlow<Map<RoutePlan, String?>> = _sessionLinks.asStateFlow()

    /**
     * The routes that stood behind the acquisition now running, oldest first — empty when the mode was
     * armed from Idle. It is what `Exit` restores and what the acquisition's own answers are told apart
     * from, and it is a **snapshot**: the live session is [session].
     */
    private var standingRoutes: List<RoutePlan> = emptyList()

    /** The pace window: recent readings, oldest first, pruned to [RoutePace.WINDOW_MS]. */
    private val paceSamples = ArrayDeque<RoutePace.Sample>()

    init {
        // Follow the engine in force's readiness and stage: the collector handles selection and
        // session switches, while [prepareCurrent] publishes an engine's own answer synchronously for
        // the retry below.
        viewModelScope.launch {
            combine(selection, _sessionEngine) { selected, session -> session ?: selected }
                .flatMapLatest { it.state }
                .collect { _engineState.value = it }
        }
        viewModelScope.launch {
            combine(selection, _sessionEngine) { selected, session -> session ?: selected }
                .flatMapLatest { it.stage }
                .collect { _stage.value = it }
        }
        viewModelScope.launch { prepareCurrent() }
    }

    /**
     * **Arms the mode** — the machine's Idle → Choosing edge, where the session's set starts empty and
     * the engine for the session is resolved.
     *
     * [fix] is the position the caller read at that instant — the GPS fix or the demo seam's own
     * reading, never the map centre the aim itself holds — with the course and speed the lead is
     * projected from where they are trustworthy, and nulls where they are not (R3). The edge resolves
     * the anchor once and the acquisition keeps it, so no search has a seam left to re-read.
     *
     * **The origin is judged here** (R7) and again on every later entry into an acquisition; a refusal
     * is held as the phase's own [RouteState.Choosing.originRefusal], reading as a line about the
     * position rather than on the target. A refused anchor is **not** told to the engine, there being
     * no usable end. A null fix is the position before the first fix: the mode is armed, and searches
     * are refused until a position exists rather than routed from an invented one.
     */
    suspend fun beginDraft(fix: RouteFix?) {
        if (_state.value !is RouteState.Idle) return
        clearSession()
        // The Idle → Choosing edge resolves the selection once (D5): the engine chosen at this instant
        // draws every line of the session, and a setting changed later cannot reach it.
        _sessionEngine.value = selection.value
        enterAcquisition(fix, enteredFromRoute = false, reacquire = false)
    }

    /**
     * **Reroute** — back into the acquisition from a followed route, from a **fresh anchor** and to the
     * **same destination**, with one acquisition fired at once so the line lands without a second press.
     *
     * The engine held the destination the route was built to, so telling it the new anchor is itself
     * that one acquisition — which is why this takes the arming call's own answer rather than asking
     * again. The session set is kept, so the line being replaced stays drawn on the ladder.
     */
    suspend fun reroute(fix: RouteFix?) {
        val following = _state.value as? RouteState.Following ?: return
        if (following.routes.isEmpty()) return
        enterAcquisition(fix, enteredFromRoute = true, reacquire = true)
    }

    /**
     * **New route** — the same move with the destination **cleared**: the acquisition opens on the
     * fresh anchor and computes **nothing** until `Acquire route` is pressed. The session set is kept,
     * so the earlier lines stay drawn on the ladder.
     */
    suspend fun newRoute(fix: RouteFix?) {
        if (_state.value !is RouteState.Following) return
        enterAcquisition(fix, enteredFromRoute = true, reacquire = false)
    }

    /**
     * The acquisition's own entry edge, shared by its three doors (R3).
     *
     * The anchor is resolved once here — the live fix led by `route.anchor.leadSec`, falling back to
     * the live fix where the predicted point is not water, and the judgement asked of that fallback —
     * and the session set is snapshotted as [standingRoutes] before the state moves.
     *
     * [reacquire] is `Reroute`'s own: the arming call is then the acquisition, and its answer becomes
     * the front line at once.
     */
    private suspend fun enterAcquisition(fix: RouteFix?, enteredFromRoute: Boolean, reacquire: Boolean) {
        val (anchor, refusal) = resolveAnchor(fix)
        standingRoutes = if (enteredFromRoute) session.keys.toList() else emptyList()
        anchorTold = false
        _state.value = RouteState.Choosing(
            start = anchor,
            plan = null,
            ladder = standingRoutes,
            originRefusal = refusal,
            enteredFromRoute = enteredFromRoute
        )
        if (anchor == null || refusal != null || !reacquire) return
        // Reroute: the anchor is told now, and the engine's answer to the destination it still holds is
        // the one acquisition this move fires.
        _state.value = (_state.value as RouteState.Choosing).copy(searching = true)
        val answer = engine().onOriginPositionChanged(anchor)
        anchorTold = true
        val still = _state.value as? RouteState.Choosing ?: return
        val plan = (answer as? RouteResult.Success)?.let {
            RoutePlan.of(anchor, it, System.currentTimeMillis())
        }
        if (plan != null) putSession(plan, null)
        _state.value = still.copy(plan = plan, searching = false, asked = true, refusal = null)
    }

    /**
     * The acquisition's anchor and the anchor's own refusal (R3, R7).
     *
     * The lead is **best-effort and never binding**: a predicted point that is not water falls back to
     * the live fix and the acquisition proceeds, because a boat bearing down on a headland is precisely
     * the case a reroute exists for. The fallback point is what the judgement is then asked of.
     */
    private suspend fun resolveAnchor(fix: RouteFix?): Pair<RoutePoint?, RouteRefusalReason?> {
        if (fix == null) return null to null
        val predicted = routeAnchorLead(fix)
        if (predicted == fix.position) return fix.position to engine().validatePoint(fix.position)
        if (engine().validatePoint(predicted) == null) return predicted to null
        return fix.position to engine().validatePoint(fix.position)
    }

    /**
     * **The ask** — the acquisition's `Acquire route`, and the machine's only trigger (R2).
     *
     * The aim is written into [pendingAim] — replacing whatever is there — and the worker is started.
     * There is no throttle of the caller's own, because this function does no search at all, and no
     * timer asks on behalf of anyone: a press is the whole of the policy.
     */
    fun acquire(aim: RoutePoint) {
        val choosing = _state.value as? RouteState.Choosing ?: return
        // A phase with no anchor yet — the mode armed before the first fix — refuses the ask rather
        // than inventing a start. A refused anchor refuses it too: there is nothing to measure from.
        if (choosing.start == null || choosing.originRefusal != null) return
        if (!engineState.value.ready) {
            // The first ask of a session can arrive before the engine is ready. One preparation is
            // asked for and the ask then restarts itself once — never in a loop, because an engine
            // that is still not ready after a completed preparation has said so, and the toggle's gate
            // has already reported it.
            if (engineState.value is RouteEngineState.NotReady) {
                viewModelScope.launch { if (prepareCurrent().ready) acquire(aim) }
            }
            return
        }
        pendingAim = aim
        _state.value = choosing.copy(searching = true, asked = true)
        startWorker()
    }

    /**
     * **The one worker's body: take the newest aim, ask the engine, then take the newest again until
     * the slot is empty.**
     *
     * It is started by a new ask **after cancelling the worker in flight** — which is the abort R4 asks
     * for — so the worker never queues behind a computation nobody wants any more. An abort leaves the
     * front line where it is: only an answer that lands writes one. The loop ends when the slot is
     * empty, and *that* is where `searching` goes false, so a refusal is never shown on a frame that
     * still has work behind it.
     *
     * The acquisition's anchor is told here, on its **first** ask and never on the arming frame: an
     * engine told an origin while it still holds the previous session's destination computes a route
     * nobody asked for, so the two ends are told together as part of the same acquisition (R2).
     */
    private fun startWorker() {
        askJob?.cancel()
        askJob = viewModelScope.launch {
            while (true) {
                val aim = pendingAim ?: break
                pendingAim = null
                val choosing = _state.value as? RouteState.Choosing ?: break
                val start = choosing.start ?: break
                if (!anchorTold) {
                    engine().onOriginPositionChanged(start)
                    anchorTold = true
                }
                // The destination is judged as it moves (R6): a refused aim paints the crosshair and
                // says why, and no route is asked for it.
                val refusal = engine().validatePoint(aim)
                val answer = if (refusal == null) engine().onDestinationPositionChanged(aim) else null
                // An answer that lands after the route was confirmed or ended must not rewrite the
                // mode: only an acquisition phase accepts a line, which is the whole hand-off rule.
                val still = _state.value as? RouteState.Choosing ?: break
                _state.value = when {
                    // A refused aim's state **stands alone** (R6): the sentence names the reason and
                    // the outcomes hide, which is only true if the phase holds no plan — so any line a
                    // previous aim resolved is dropped here rather than shown beside the crosshair.
                    refusal != null -> still.copy(
                        plan = null,
                        searching = true,
                        asked = true,
                        refusal = refusal
                    )
                    answer is RouteResult.Success -> {
                        val plan = RoutePlan.of(start, answer, System.currentTimeMillis())
                        putSession(plan, null)
                        still.copy(
                            // The line just superseded joins the ladder rather than vanishing, so
                            // nothing the session produced is lost from the drawing (R14).
                            ladder = still.plan?.let { still.ladder + it } ?: still.ladder,
                            plan = plan,
                            searching = true,
                            asked = true,
                            refusal = null
                        )
                    }
                    // An aim with no route to it leaves the acquisition armed and the standing line
                    // where it is, and the next ask is answered again.
                    else -> still.copy(searching = true, asked = true, refusal = null)
                }
            }
            // The slot is empty: the searches are up to date, and the flag clears with the last one.
            (_state.value as? RouteState.Choosing)?.let {
                if (it.searching) _state.value = it.copy(searching = false)
            }
        }
    }

    /**
     * **One more preparation, asked by the toggle's own tap**, and the state it reached.
     *
     * The preparation in `init` happens once, and it can land inside the coastline's own load window —
     * the map's own load is usually in flight on a cold start — where the engine answers `Unavailable`.
     * A gate that never re-asks then leaves a square that does nothing for the session, so the tap is
     * the user's own retry: one preparation, and its answer handed back. **One preparation per
     * interaction and never a loop**: the caller shows the reason rather than asking again, an engine
     * still not ready after a completed preparation having said so.
     */
    suspend fun prepareAgain(): RouteEngineState =
        if (engineState.value.ready) engineState.value else prepareCurrent()

    /**
     * **Confirm** — the acquisition's own forward action, and the only one that enters the following
     * phase (R16, R18). The front line becomes the followed route and the session it grew stays the
     * ladder; without a resolved plan there is nothing to confirm, so the press is ignored rather than
     * locking an empty route.
     *
     * The release of the phase's two couplings is the screen's — it owns the camera — and it happens on
     * this same edge, in this same frame: the phase leaving [RoutePhase.CHOOSING] is what tells the
     * machine to give the map back.
     */
    fun confirm() {
        val choosing = _state.value as? RouteState.Choosing ?: return
        if (choosing.plan == null) return
        val routes = session.keys.toList()
        if (routes.isEmpty()) return
        pendingAim = null
        askJob?.cancel()
        askJob = null
        standingRoutes = emptyList()
        _state.value = RouteState.Following(routes = routes)
    }

    /**
     * **The acquisition's Exit, and the back key with it** (R23).
     *
     * Where a route stood behind the acquisition this is a **phase move**: that route comes back with
     * its line intact, and whatever the acquisition acquired in the meantime is dropped, being
     * unconfirmed. Where it stood on nothing it is an ending, and the mode goes.
     */
    fun exitAcquisition() {
        val choosing = _state.value as? RouteState.Choosing ?: return
        pendingAim = null
        askJob?.cancel()
        askJob = null
        if (choosing.enteredFromRoute && standingRoutes.isNotEmpty()) {
            val standing = standingRoutes
            // The acquisition's own answers are dropped from the session, so the restore is exactly
            // the set that stood behind it.
            session.keys.toList().filterNot { it in standing }.forEach { session.remove(it) }
            publishSession()
            standingRoutes = emptyList()
            _state.value = RouteState.Following(routes = standing)
        } else {
            end()
        }
    }

    /** The panel's Exit and the toggle's off while a route is followed, and every other ending. */
    fun end() {
        pendingAim = null
        askJob?.cancel()
        askJob = null
        clearSession()
        standingRoutes = emptyList()
        anchorTold = false
        _state.value = RouteState.Idle
        // The return to Idle releases the session engine (D5): the next arming resolves the selection
        // again, so the engine that drew the finished route is no longer held.
        _sessionEngine.value = null
    }

    /** **The session's routes, in creation order** — oldest first, the followed one last (R25). */
    fun sessionRoutes(): List<RoutePlan> = session.keys.toList()

    /** The track a route of this session was already written as, or null while it has none. */
    fun trackFor(plan: RoutePlan): String? = session[plan]

    /**
     * **Is this route already written?** — the one predicate both Save actions read (R16, R17), rather
     * than a null check at each call site.
     */
    fun isRouteSaved(plan: RoutePlan): Boolean = session[plan] != null

    /** Records the track a route was written as — the session's own link, dying with the mode. */
    fun noteRouteSaved(plan: RoutePlan, trackId: String) {
        if (session.containsKey(plan)) {
            session[plan] = trackId
            publishSession()
        }
    }

    /** Adds a route to the session, with the track it is already written as, if any. */
    private fun putSession(plan: RoutePlan, trackId: String?) {
        session[plan] = trackId
        publishSession()
    }

    /** Drops the whole session — the mode's end, and the arming edge's fresh start. */
    private fun clearSession() {
        session.clear()
        publishSession()
    }

    private fun publishSession() {
        _sessionLinks.value = session.toMap()
    }

    /**
     * One pace reading for the window. [restricted] is the caller's finding that the reading was
     * taken inside a regulated zone or the 300 m band, where it measures the limit and not the boat.
     */
    fun observePace(speedKn: Double?, restricted: Boolean, nowMs: Long, setPaceKn: Double) {
        if (speedKn != null && speedKn.isFinite() && speedKn > 0.0) {
            paceSamples.addLast(RoutePace.Sample(speedKn, nowMs, restricted))
        }
        while (paceSamples.isNotEmpty() && paceSamples.first().atMs < nowMs - RoutePace.WINDOW_MS) {
            paceSamples.removeFirst()
        }
        _paceKn.value = RoutePace.paceKn(paceSamples.toList(), nowMs, setPaceKn)
    }

    companion object {

    /**
     * Factory for [RouteViewModel].
     *
     * The selection is handed in rather than chosen here, and that is the seam: the caller builds
     * the flow the registry and the setting resolve to — a live engine for the chosen id — and a
     * replacement, or a test's own fake, is one argument at one site.
     */
    fun factory(selection: StateFlow<RouteEngine>): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                modelClass: Class<T>,
                extras: CreationExtras
            ): T = RouteViewModel(selection) as T
        }
}
}
