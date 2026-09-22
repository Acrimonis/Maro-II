package ykws.android.maro.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
     * generation-and-finalisation instant with the fixed `Route ` prefix — `· n/N` appended when one
     * action writes several routes of a session, in creation order.
     *
     * One home for the naming rule, so *every* door that saves a route reads it: the draft's single
     * saves and the set's own write name a route the same way, and no door can fall back on the bare
     * auto-name a recorded journey carries.
     */
    fun trackName(index: Int? = null, total: Int? = null): String =
        TrackFromCourse.routeTrackName(computedAtMs, index, total)

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

    /** The destination is being chosen: the aim moves, the camera is held and demo's speed is suspended. */
    CHOOSING,

    /** A route is followed: the camera follows the boat, the refresh gate runs, and the line is locked. */
    FOLLOWING
}

/** The refresh cycle's own state while a route is followed (R12, R15). */
enum class RouteRefresh {

    /** Nothing is being asked: the standing line is the answer in force. */
    IDLE,

    /** A refresh is in flight — the panel offers **Abort** and the status says so. */
    RUNNING,

    /** The session's own freeze, forgotten when the mode ends (R26): the gate's clock stands still. */
    FROZEN
}

/**
 * The route's state machine: **Idle → Choosing → Following**, and nothing else.
 *
 * There is deliberately no arrival state and no arrival cue: reaching the destination is the trip
 * figure reading zero while the line stays drawn, and a route ends only on an explicit exit. A
 * [Choosing] carries a null plan while it is aiming with nothing computed, which is an ordinary
 * moment — the first search of a session, or an aim no route answers — not an error.
 */
sealed interface RouteState {

    /** The phase this state is — the couplings' own key, derived from the state rather than beside it. */
    val phase: RoutePhase

    /** The preview a state carries, or null while there is none. */
    val plan: RoutePlan?

    data object Idle : RouteState {
        override val phase: RoutePhase get() = RoutePhase.IDLE
        override val plan: RoutePlan? get() = null
    }

    /**
     * Aiming, from [start]: [plan] is the live preview, or null while nothing has resolved.
     *
     * [start] is taken **once**, on the Idle → Choosing edge, and held for the whole phase — and for
     * the plan a confirmation locks, which is that same point. Holding it here, rather than letting a
     * caller read its own seam again, is what makes the anchor frozen by construction: while the mode
     * is armed there is no position left to re-read, so only the aim moves — in GPS mode too, where a
     * live start would otherwise chase the fix. It is null before the first fix, and a preview is
     * refused rather than invented while it is.
     *
     * [searching] says a search is in flight, which is what tells "not yet" apart from "no route to
     * this aim" — the two read identically from a null plan and mean opposite things to whoever is
     * watching the map. [refusal] is the third reading: the aim is not usable water, and the sentence
     * naming the reason is shown with the outcomes hidden while no plan exists (R6) — a refused aim
     * therefore carries **no plan at all**, dropping any preview a previous aim had resolved, so the
     * refused state stands alone rather than showing a superseded line's details beside the crosshair.
     *
     * [originRefusal] is the **origin's** own refusal, judged once on the arming frame and never
     * again (R7): it reads as a line about the position rather than as one about the target.
     */
    data class Choosing(
        val start: RoutePoint?,
        override val plan: RoutePlan?,
        val searching: Boolean = false,
        /**
         * Whether an aim has been asked at all. It is what holds the refusal back: "no route to this
         * aim" and "nothing asked yet" both read as a null plan, and saying the first one on the
         * frame the mode opens would blink a refusal the next search is about to contradict.
         */
        val asked: Boolean = false,
        val refusal: RouteRefusalReason? = null,
        val originRefusal: RouteRefusalReason? = null
    ) : RouteState {
        override val phase: RoutePhase get() = RoutePhase.CHOOSING
    }

    /**
     * Following a route.
     *
     * [routes] is **the session, and the ladder is the same collection** (R25): every route the
     * session produced, in creation order, the followed one last and the replaced ones before it. The
     * drawing caps how many of the stale ones it paints and the all-scope save writes every one of
     * them, so there is one collection rather than two that can drift. [refresh] is the cycle's own
     * state and [frozen] the session's freeze; neither outlives the mode.
     */
    data class Following(
        val routes: List<RoutePlan>,
        val refresh: RouteRefresh = RouteRefresh.IDLE,
        val frozen: Boolean = false
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
 * **One worker serves everything** (R4). A new aim **cancels the ask in flight and starts** rather than
 * queueing behind it, and the pending slot keeps the newest aim alone, so a flung map pays for one
 * search per *completed* search rather than one per frame. **The standing plan deliberately survives an
 * abort**: a superseded line stays drawn, so a cancel never leaves the user with nothing to look at.
 * The following mode's refresh fires its call on that **same** worker, which is why the field is one
 * `Job` and not two — an ask and a refresh can never be alive together.
 *
 * The pace is the trip figure's: the set free-water pace until the boat's own samples have something
 * to say, then [RoutePace]'s own reduction of them. Samples taken inside a regulated zone or the band
 * are marked restricted by the caller and dropped there, so a limit is never mistaken for the boat's
 * pace. **The engine is not told the pace** — the placeholder times its own legs (R28) and a real
 * engine prices from the water it reads.
 */
class RouteViewModel(

    /**
     * The engine every search of this feature runs on, handed in by whoever builds the view model.
     *
     * It is a **parameter and not a construction**, and that is the seam: the caller already holds
     * what the shipped engine needs, so a second engine is one expression at that one site rather
     * than an edit inside this file, and a test can hand in a foreign engine and read what the
     * feature made of its answer. Nothing here names a particular engine.
     */
    private val engine: RouteEngine
) : ViewModel() {

    /** What the engine can do right now — the toggle's gate reads it. */
    val engineState: StateFlow<RouteEngineState> = engine.state

    private val _state = MutableStateFlow<RouteState>(RouteState.Idle)
    val state: StateFlow<RouteState> = _state.asStateFlow()

    private val _paceKn = MutableStateFlow(AppConfig.routeFreeWaterPaceKn.toDouble())

    /** The pace the trip figure plans at (kn) — set pace, or the boat's own once it has evidence. */
    val paceKn: StateFlow<Double> = _paceKn.asStateFlow()

    /**
     * A failed refresh's reason, as the id of the line a user reads — null when nothing failed.
     *
     * Set **only while the mode still holds a route**: a failure arriving after the mode has gone is
     * dropped rather than toasted (R23), which is the same guard the standing line itself takes.
     */
    private val _refreshFailureResId = MutableStateFlow<Int?>(null)
    val refreshFailureResId: StateFlow<Int?> = _refreshFailureResId.asStateFlow()

    /** The screen has shown the toast; the one-shot event is cleared. */
    fun clearRefreshFailure() {
        _refreshFailureResId.value = null
    }

    /**
     * **The one worker** — every ask and every refresh runs here, and a new call cancels the one in
     * flight rather than queueing behind it (R4). One field, so two calls can never be alive together.
     */
    private var askJob: Job? = null

    /**
     * **The ask's own slot: the newest aim no worker has taken up yet.**
     *
     * A drag asks once per settled aim; the machine answers by overwriting this one field, so the aims
     * a search cannot catch up with are *replaced* rather than queued.
     */
    private var pendingAim: RoutePoint? = null

    /**
     * The session's routes and, for the ones already written, the track each became (R25).
     *
     * A `LinkedHashMap` rather than a list beside a map, so the creation order the all-scope save names
     * its files in and the route-to-track link cannot drift apart. It dies with the mode, as the link
     * and the freeze both do.
     */
    private val session = LinkedHashMap<RoutePlan, String?>()

    /** The pace window: recent readings, oldest first, pruned to [RoutePace.WINDOW_MS]. */
    private val paceSamples = ArrayDeque<RoutePace.Sample>()

    init {
        viewModelScope.launch { engine.prepare() }
    }

    /**
     * Arms the mode: the machine's Idle → Choosing edge, which is also where the route's origin enters
     * the machine and where the engine is told it (R7).
     *
     * [start] is the position the caller read at that instant — the GPS fix or the demo seam's own
     * reading, never the map centre the aim itself holds. The edge takes it once and the phase keeps
     * it, so no preview has a seam left to re-read.
     *
     * **The origin is judged here and never again**: [RouteEngine.validatePoint] is asked once, and a
     * refusal is held as the phase's own [RouteState.Choosing.originRefusal] — the sentence reads as a
     * line about the position rather than on the target. A refused origin is **not** told to the
     * engine, there being no usable end to hold. A null start is the position before the first fix:
     * the mode is armed, and the previews are refused until a position exists rather than routed from
     * an invented one.
     */
    suspend fun beginDraft(start: RoutePoint?) {
        if (_state.value !is RouteState.Idle) return
        session.clear()
        if (start == null) {
            _state.value = RouteState.Choosing(start = null, plan = null)
            return
        }
        val refusal = engine.validatePoint(start)
        if (refusal == null) engine.onOriginPositionChanged(start)
        _state.value = RouteState.Choosing(start = start, plan = null, originRefusal = refusal)
    }

    /**
     * Recomputes the preview for [aim] from the phase's own frozen start.
     *
     * The start is deliberately not a parameter: the machine already holds the position the mode opened
     * on, so a caller cannot hand it a fresher one and the search can only ever be asked from the
     * anchor. The caller needs no throttle of its own either, because this function does no search at
     * all: it writes the aim into [pendingAim] — replacing whatever is there — and starts the worker.
     */
    fun preview(aim: RoutePoint) {
        val choosing = _state.value as? RouteState.Choosing ?: return
        // A phase with no anchor yet — the mode armed before the first fix — refuses the preview rather
        // than inventing a start, which is the same rule the edge that opened the mode follows. A
        // refused origin refuses it too: there is no anchor to measure a route from.
        if (choosing.start == null || choosing.originRefusal != null) return
        if (!engineState.value.ready) {
            // The first aim of a session can arrive before the engine is ready. One preparation is
            // asked for and the preview then restarts itself once — never in a loop, because an
            // engine that is still not ready after a completed preparation has said so, and the
            // toggle's gate has already reported it.
            if (engineState.value is RouteEngineState.NotReady) {
                viewModelScope.launch { if (engine.prepare().ready) preview(aim) }
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
     * It is started by a new ask **after cancelling the worker in flight** — which is the abort R4
     * asks for — so the worker never queues behind a computation nobody wants any more. An abort
     * leaves the standing plan where it is: only an answer that lands writes a preview, so a
     * superseded line stays drawn and a cancelled call changes nothing. The loop ends when the slot is
     * empty, and *that* is where `searching` goes false, so a refusal is never shown on a frame that
     * still has work behind it.
     */
    private fun startWorker() {
        askJob?.cancel()
        askJob = viewModelScope.launch {
            while (true) {
                val aim = pendingAim ?: break
                pendingAim = null
                val choosing = _state.value as? RouteState.Choosing ?: break
                val start = choosing.start ?: break
                // The destination is judged as it moves (R6): a refused aim paints the crosshair and
                // says why, and no route is asked for it.
                val refusal = engine.validatePoint(aim)
                val answer = if (refusal == null) engine.onDestinationPositionChanged(aim) else null
                // An answer that lands after the route was confirmed or ended must not rewrite the
                // mode: only a choosing phase accepts a preview, which is the whole hand-off rule.
                val still = _state.value as? RouteState.Choosing ?: break
                _state.value = when {
                    // A refused aim's state **stands alone** (R6): the sentence names the reason and
                    // the outcomes hide, which is only true if the phase holds no plan — so any
                    // preview a previous aim resolved is dropped here rather than shown beside the
                    // crosshair.
                    refusal != null -> still.copy(
                        plan = null,
                        searching = true,
                        asked = true,
                        refusal = refusal
                    )
                    answer is RouteResult.Success -> still.copy(
                        plan = RoutePlan.of(start, answer, System.currentTimeMillis()),
                        searching = true,
                        asked = true,
                        refusal = null
                    )
                    // An aim with no route to it is a phase holding no preview, not an error: the mode
                    // stays armed and the next aim is asked again.
                    else -> still.copy(searching = true, asked = true, refusal = null)
                }
            }
            // The slot is empty: the previews are up to date, and the flag clears with the last one.
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
        if (engineState.value.ready) engineState.value else engine.prepare()

    /**
     * The choices that **follow** the aimed route: **Route**, and **Save as Track and Route** once its
     * track is written. The aimed route is locked and the session starts with it, carrying the start
     * the phase was opened on. Without a resolved plan there is nothing to confirm, so the press is
     * ignored rather than locking an empty route.
     *
     * The release of the phase's two couplings is the screen's — it owns the camera — and it happens on
     * this same edge, in this same frame (R18, R20, R21): the phase leaving [RoutePhase.CHOOSING] is
     * what tells the machine to give the map back.
     */
    fun confirm() {
        val plan = (_state.value as? RouteState.Choosing)?.plan ?: return
        pendingAim = null
        askJob?.cancel()
        askJob = null
        session.clear()
        session[plan] = null
        _state.value = RouteState.Following(routes = listOf(plan))
    }

    /** The toggle's off, the panel's Exit and every other ending: the route ends, the ask is cancelled. */
    fun end() {
        pendingAim = null
        askJob?.cancel()
        askJob = null
        session.clear()
        _refreshFailureResId.value = null
        _state.value = RouteState.Idle
    }

    /**
     * **The refresh (R9, R10, R13).** The app's gate decided the moment may have come; the engine's own
     * [RouteEngine.isReadyToRecompute] is then asked and **may only veto** — a "no" delays the call and
     * leaves the standing line exactly as it was, saying nothing.
     *
     * On a yes the refresh runs on **the same worker** as the asks, aborting whatever is in flight. An
     * answer the engine could not give — [RouteResult.OutsideWater], [RouteResult.NoPath] — **changes
     * nothing on the map**: the standing route stays the front line, **nothing is staled**, and the
     * reason reaches the user as a toast. Stale means *replaced*, so the ladder only ever grows on a
     * new answer arriving.
     *
     * A failure arriving after the mode has gone is dropped rather than toasted: the state is re-read
     * after the answer and only a following phase accepts it.
     */
    fun refresh(start: RoutePoint, force: Boolean = false) {
        val following = _state.value as? RouteState.Following ?: return
        if (following.frozen && !force) return
        if (following.refresh == RouteRefresh.RUNNING) return
        pendingAim = null
        askJob?.cancel()
        _state.value = following.copy(refresh = RouteRefresh.RUNNING)
        askJob = viewModelScope.launch {
            if (!engine.isReadyToRecompute()) {
                // The veto only delays: the standing line is untouched and nothing is said.
                (_state.value as? RouteState.Following)?.let {
                    if (it.refresh == RouteRefresh.RUNNING) _state.value = it.copy(refresh = RouteRefresh.IDLE)
                }
                return@launch
            }
            val answer = engine.onOriginPositionChanged(start)
            // The mode may have ended, or the route been replaced, while the call was in flight.
            val still = _state.value as? RouteState.Following ?: return@launch
            when (answer) {
                is RouteResult.Success -> {
                    val plan = RoutePlan.of(start, answer, System.currentTimeMillis())
                    session[plan] = null
                    _state.value = still.copy(routes = still.routes + plan, refresh = RouteRefresh.IDLE)
                }
                else -> {
                    _state.value = still.copy(refresh = RouteRefresh.IDLE)
                    routeFailureReasonResId(answer)?.let { _refreshFailureResId.value = it }
                }
            }
        }
    }

    /**
     * **Abort** — the refresh alone is cancelled, and the standing line is what is left: nothing is
     * staled, nothing is said, and the route stays followed.
     */
    fun abortRefresh() {
        if ((_state.value as? RouteState.Following)?.refresh != RouteRefresh.RUNNING) return
        askJob?.cancel()
        askJob = null
        (_state.value as? RouteState.Following)?.let {
            _state.value = it.copy(refresh = RouteRefresh.IDLE)
        }
    }

    /** **Freeze/Resume** (R12, R26): the gate's clock stands still, and nothing else changes. */
    fun setFrozen(frozen: Boolean) {
        val following = _state.value as? RouteState.Following ?: return
        _state.value = following.copy(frozen = frozen)
    }

    /** **The session's routes, in creation order** — oldest first, the followed one last (R25). */
    fun sessionRoutes(): List<RoutePlan> = session.keys.toList()

    /**
     * **The session's routes with the tracks they became, copied at the call** (R25).
     *
     * A caller about to end the mode pins the save's set through this copy. The ending clears the
     * session, so a save that read the live table afterwards could see an already-written route as one
     * nobody saved and **rewrite** it instead of renaming it — the outcome would turn on dispatch
     * order. Taking the copy first is what makes rename-never-rewrite a rule rather than a race.
     */
    fun sessionSnapshot(): List<SessionRoute> = session.map { SessionRoute(it.key, it.value) }

    /** The track a route of this session was already written as, or null while it has none. */
    fun trackFor(plan: RoutePlan): String? = session[plan]

    /** Records the track a route was written as — the session's own link, dying with the mode. */
    fun noteRouteSaved(plan: RoutePlan, trackId: String) {
        if (session.containsKey(plan)) session[plan] = trackId
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
         * The engine is handed in rather than chosen here, and that is the seam: the caller builds
         * whichever engine ships — [`ykws.android.maro.spatial.RouteDummyEngine`] today — and a
         * replacement, or a test's own fake, is one argument at one site.
         */
        fun factory(engine: RouteEngine): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(
                    modelClass: Class<T>,
                    extras: CreationExtras
                ): T = RouteViewModel(engine) as T
            }
    }
}
