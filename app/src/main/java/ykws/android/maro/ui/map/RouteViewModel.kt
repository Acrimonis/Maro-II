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
import ykws.android.maro.data.model.RouteOffer
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.data.model.RouteResult
import ykws.android.maro.data.route.RoutePace
import ykws.android.maro.data.track.TrackFromCourse
import ykws.android.maro.spatial.RouteEngine
import ykws.android.maro.spatial.RouteEngineState
import ykws.android.maro.spatial.RouteRefusalReason
import ykws.android.maro.spatial.RouteProgress
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
     * One home for the naming rule, so **every** door that saves a route reads it. One save path names
     * a route one way.
     */
    fun trackName(): String = TrackFromCourse.routeTrackName(computedAtMs)

    /**
     * What is left of the route from [from] onward.
     *
     * The position is projected onto the **nearest point on the line** rather than snapped to its
     * nearest vertex: a fix halfway along a leg is halfway through that leg, not back at the vertex
     * behind it, so both readings follow the boat between the vertices as well as on them. Each leg
     * is projected with the shipped [SpatialOperations.projectPointOntoSegment], the leg whose own
     * [SpatialOperations.pointToSegmentDistance] is smallest wins — ties to the lower index, so the
     * choice stays deterministic — and the remainder is the distance from that projected point to
     * the leg's end plus every following leg.
     *
     * The seconds follow the same split: the chosen leg keeps the fraction of its own [legTimesSec]
     * it has not yet travelled, and every following leg keeps its own seconds.
     *
     * **This is the one snap both readings of this plan use** — the trip figure and the panel's own
     * table — so what a following boat reads and what the panel prints cannot drift. The sum reaches
     * zero at the destination, which is the one reading that must be exact: arrival is the trip cell
     * reading zero.
     */
    fun remainingFrom(from: RoutePoint): RouteRemainder {
        if (points.size < 2) return RouteRemainder(distanceM, durationSec)
        val fix = LatLng(from.latitude, from.longitude)
        var bestLeg = 0
        var bestDistance = Double.MAX_VALUE
        for (leg in 0 until points.size - 1) {
            val metres = SpatialOperations.pointToSegmentDistance(
                fix,
                LatLng(points[leg].latitude, points[leg].longitude),
                LatLng(points[leg + 1].latitude, points[leg + 1].longitude)
            )
            if (metres < bestDistance) {
                bestDistance = metres
                bestLeg = leg
            }
        }
        val a = LatLng(points[bestLeg].latitude, points[bestLeg].longitude)
        val b = LatLng(points[bestLeg + 1].latitude, points[bestLeg + 1].longitude)
        val projected = SpatialOperations.projectPointOntoSegment(fix, a, b)
        val legLength = SpatialOperations.haversine(a, b)
        // **A zero-length leg answers a fraction of 1.0** — nothing of that leg is spent, since a
        // fix standing on it has not travelled it.
        val notYetTravelled = if (legLength > 0.0) {
            (1.0 - SpatialOperations.haversine(a, projected) / legLength).coerceIn(0.0, 1.0)
        } else {
            1.0
        }
        var remainingM = SpatialOperations.haversine(projected, b)
        var remainingSec = legTimesSec.getOrElse(bestLeg) { 0.0 } * notYetTravelled
        for (leg in bestLeg + 1 until points.size - 1) {
            remainingM += SpatialOperations.haversine(
                LatLng(points[leg].latitude, points[leg].longitude),
                LatLng(points[leg + 1].latitude, points[leg + 1].longitude)
            )
            remainingSec += legTimesSec.getOrElse(leg) { 0.0 }
        }
        return RouteRemainder(remainingM, remainingSec)
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

        /**
         * **A candidate line, as a plan** (R54, R55): the engine's own offer, drawn beside the settled
         * answer and walkable by the selection exactly as the settled line is.
         *
         * The start is the settled plan's own anchor — a candidate is a different line between the same
         * two ends — and the instant is that plan's, so a candidate saved to a track is dated and named
         * with the route it belongs to rather than with the moment the user picked it. Its distance,
         * clock and leg times are the engine's own figures, recomputed nowhere (R72): an offer resolves
         * nothing, so it can never claim the aim had moved.
         */
        fun of(start: RoutePoint, offer: RouteOffer, nowMs: Long): RoutePlan = RoutePlan(
            start = start,
            destination = offer.points.lastOrNull() ?: start,
            destinationMoved = false,
            points = offer.points,
            legTimesSec = offer.legTimesSec,
            distanceM = offer.distanceM,
            durationSec = offer.durationSec,
            computedAtMs = nowMs
        )
    }
}

/**
 * **The phase the mode is in** — the two on-phases and off, named once so the surface that paints the
 * mode keys on the phase rather than on the mode's switch (R51).
 *
 * [CHOOSING] is the acquisition: the drawer's pair is being armed and its answer picked, and the panel
 * owns the dashboard slot. [FOLLOWING] is navigation: the ordinary dashboard stands, the toggle wears
 * its blue face and the one exit dialog is the mode's whole presence (R58, R73). [IDLE] is off.
 *
 * **No coupling hangs off the phase any more** (R71): the aim the camera hold and the demo suspension
 * existed to serve left with the aim, so both are gone rather than keyed.
 */
enum class RoutePhase {

    /** The mode is off: no line, no route. */
    IDLE,

    /** The acquisition: the engine is searched from the drawer's own pair and its candidates picked. */
    CHOOSING,

    /** A route is followed: the toggle is blue and the line is locked. */
    FOLLOWING
}

/**
 * The route's state machine: **Idle → Choosing → Following**, and nothing else.
 *
 * There is deliberately no arrival state and no arrival cue: reaching the destination is the trip
 * figure reading zero while the line stays drawn, and a route ends only on an explicit exit.
 */
sealed interface RouteState {

    /** The phase this state is, derived from the state rather than beside it. */
    val phase: RoutePhase

    /** The front line a state carries, or null while there is none. */
    val plan: RoutePlan?

    data object Idle : RouteState {
        override val phase: RoutePhase get() = RoutePhase.IDLE
        override val plan: RoutePlan? get() = null
    }

    /**
     * **Acquiring the route**, from [start]: [plan] is the settled line, or null while nothing has
     * landed.
     *
     * [start] is the acquisition's **anchor**, resolved once from the drawer's standing pair at the
     * instant the acquisition was armed (R44, R71) and held for the whole acquisition — and for the
     * plan a selection locks, which is that same point. Holding it here is what makes it frozen by
     * construction. It is null before the first fix, and the ask is refused rather than invented while
     * it is.
     *
     * [searching] says a search is in flight, which is what tells "not yet" apart from "no route to
     * these ends" — the two read identically from a null plan and mean opposite things to whoever is
     * watching the map. [refusal] is the third reading: the destination is not usable water, and the
     * sentence naming the reason is shown with the outcomes hidden while no plan exists (R6) — a
     * refused destination therefore carries **no plan at all**.
     *
     * [originRefusal] is the **anchor's** own refusal, judged when the acquisition is armed (R7): it
     * reads as a line about the position rather than as one about the destination.
     */
    data class Choosing(
        val start: RoutePoint?,
        override val plan: RoutePlan?,
        val searching: Boolean = false,
        /**
         * Whether the engine has been asked at all. It is what holds the refusal back: "no route to
         * these ends" and "nothing asked yet" both read as a null plan, and saying the first one on the
         * frame the mode opens would blink a refusal the search is about to contradict.
         */
        val asked: Boolean = false,
        val refusal: RouteRefusalReason? = null,
        val originRefusal: RouteRefusalReason? = null
    ) : RouteState {
        override val phase: RoutePhase get() = RoutePhase.CHOOSING
    }

    /**
     * Navigating a route: [plan] is the **selected** line, which the acquisition's own selection locked
     * (R56) — the settled answer or one of the candidates the engine drew beside it.
     *
     * There is one line and no ladder: with no reroute the mode holds one answer per arming, so nothing
     * inside it can replace a line, and the stale set R14 configured has nothing left to hold (R65).
     */
    data class Following(override val plan: RoutePlan) : RouteState {
        override val phase: RoutePhase get() = RoutePhase.FOLLOWING
    }
}

/**
 * **The two ends the drawer's selectors resolved at the trigger** (R44, R46, R71), read by the screen
 * at the instant the acquisition is armed.
 *
 * @property start       the start the trigger read: the boat's own fix led `route.anchor.leadSec`, the
 *                       standing marker position, or a flagged marker's point.
 * @property fallbackStart the boat's **unled** fix where [start] is that prediction, so a prediction
 *                       that is not water falls back to the live fix (R3); null for either other entry,
 *                       which has no prediction to fall back from.
 * @property destination the destination the trigger read: the standing marker position or a flagged
 *                       marker's point.
 */
data class RouteEnds(
    val start: RoutePoint?,
    val fallbackStart: RoutePoint?,
    val destination: RoutePoint?
)

/**
 * All of the Route feature's runtime state, in one place and on `StateFlow`.
 *
 * The engine is prepared once at construction, and **again on every arming** — the gate recovers per
 * interaction and never in a loop, because an engine still not ready after a completed preparation has
 * said so.
 *
 * **One worker serves every ask** (R4). A new ask **cancels the one in flight and starts** rather than
 * queueing behind it. **The front line deliberately survives an abort**: an answer nobody wants is
 * dropped, not the line already standing.
 *
 * **Arming is the trigger** (R49, R50): the drawer's pair is read at that instant and the acquisition
 * opens **computing**, so there is no placement press and no aim for the map to hold. The automatic
 * refresh is gone too, so no clock, no gate and no threshold survives.
 *
 * The pace is the trip figure's: the set free-water pace until the boat's own samples have something
 * to say, then [RoutePace]'s own reduction of them. **The engine is not told the pace** — the dummy
 * times its own legs at a fixed fiction (R28) and the avoid engine reads the pace in force itself.
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
     * The selection is resolved **when the acquisition arms**: while no route runs the view model reads
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
     * **The progress of the search running right now** (R15, R43), or null when none is — proxied from
     * the engine in force, so the panel reads one value whatever engine is installed, and the provisional
     * line rides the same emission as the stage.
     */
    private val _progress = MutableStateFlow<RouteProgress?>(null)

    val progress: StateFlow<RouteProgress?> = _progress.asStateFlow()

    /**
     * **The offers that arrived for the settled answer**, proxied from the engine in force the way
     * [progress] is — empty from each new ask until the engine's background job publishes them.
     */
    private val _offers = MutableStateFlow<List<RouteOffer>>(emptyList())

    val offers: StateFlow<List<RouteOffer>> = _offers.asStateFlow()

    /**
     * **The lines the acquisition draws and the selection walks** (R53, R54): the settled answer first,
     * then the engine's offers as plans of their own, in the order the passes ran.
     *
     * One list for the drawing, the rows and the selection alike, so what the map paints and what the
     * panel names cannot drift. It is index 0 — the settled answer — until an offer lands, which is
     * exactly what makes the candidate row appear only as offers arrive (R54).
     */
    private val _candidates = MutableStateFlow<List<RoutePlan>>(emptyList())

    val candidates: StateFlow<List<RoutePlan>> = _candidates.asStateFlow()

    /**
     * **Which of [candidates] the user is looking at** (R54, R55) — 0 is the settled answer, and
     * next/prev loop the set. The selected line is the one drawn at full strength, the one `Save to
     * track` writes and the one `Select route` follows (R55, R56).
     */
    private val _candidateIndex = MutableStateFlow(0)

    val candidateIndex: StateFlow<Int> = _candidateIndex.asStateFlow()

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
     * **The one worker** — every ask runs here, and a new ask cancels the one in flight rather than
     * queueing behind it (R4). One field, so two asks can never be alive together.
     */
    private var askJob: Job? = null

    /**
     * **The ask's own slot: the newest destination no worker has taken up yet.**
     *
     * Armings ask once and overwrite this one field, so a second ask landing while the first is still
     * computing is *replaced* rather than queued.
     */
    private var pendingDestination: RoutePoint? = null

    /**
     * Whether the acquisition's anchor has reached the engine yet.
     *
     * The anchor is told **as part of the acquisition's own first ask**, never on the arming frame: an
     * engine told an origin while it still holds the previous session's destination computes a route
     * nobody asked for, which is exactly what R2 forbids.
     */
    private var anchorTold: Boolean = false

    /**
     * The session's routes and, for the ones already written, the track each became (R25).
     *
     * A `LinkedHashMap` rather than a list beside a map, so the creation order the drawing reads and
     * the route-to-track link cannot drift apart. It dies with the mode, as the link does. Its
     * **reactive mirror** is [_sessionLinks], so the panel can grey a Save without a recomposition
     * reading a plain map. With one answer per arming the session holds one route (R65).
     */
    private val session = LinkedHashMap<RoutePlan, String?>()

    /** The session as the UI reads it: the link table alone, republished on every write. */
    private val _sessionLinks = MutableStateFlow<Map<RoutePlan, String?>>(emptyMap())
    val sessionLinks: StateFlow<Map<RoutePlan, String?>> = _sessionLinks.asStateFlow()

    /** The pace window: recent readings, oldest first, pruned to [RoutePace.WINDOW_MS]. */
    private val paceSamples = ArrayDeque<RoutePace.Sample>()

    init {
        // Follow the engine in force's readiness, stage and offers: the collectors handle selection and
        // session switches, while [prepareCurrent] publishes an engine's own answer synchronously for
        // the retry below.
        viewModelScope.launch {
            combine(selection, _sessionEngine) { selected, session -> session ?: selected }
                .flatMapLatest { it.state }
                .collect { _engineState.value = it }
        }
        viewModelScope.launch {
            combine(selection, _sessionEngine) { selected, session -> session ?: selected }
                .flatMapLatest { it.progress }
                .collect { _progress.value = it }
        }
        viewModelScope.launch {
            combine(selection, _sessionEngine) { selected, session -> session ?: selected }
                .flatMapLatest { it.offers }
                .collect { _offers.value = it }
        }
        // The drawn set: the settled answer and the offers the engine published for it, in one list.
        // The selection is clamped here rather than at each reader, so shrinking the set can never leave
        // the index pointing at a line that is gone.
        viewModelScope.launch {
            combine(_state, _offers) { state, offers -> routeCandidateLines(state.plan, offers) }
                .collect { lines ->
                    _candidates.value = lines
                    _candidateIndex.value =
                        _candidateIndex.value.coerceIn(0, (lines.size - 1).coerceAtLeast(0))
                }
        }
        viewModelScope.launch { prepareCurrent() }
    }

    /**
     * **Arms the mode on the drawer's standing pair** — the machine's Idle → Choosing edge, and its only
     * trigger (R49, R50).
     *
     * The ends are **read at this instant** and the acquisition then goes its own way: pan and zoom are
     * free and change nothing about the search (R71). The session's set starts empty, the engine for the
     * session is resolved once, and **the first ask fires at once** — there is nothing to place, so the
     * mode opens computing rather than waiting for a press.
     *
     * **The origin is judged here** (R7); a refusal is held as the phase's own
     * [RouteState.Choosing.originRefusal], reading as a line about the position rather than on the
     * destination. A refused anchor is **not** told to the engine, there being no usable end. A null
     * start is the position before the first fix: the mode is armed, and the ask is refused until a
     * position exists rather than routed from an invented one.
     */
    suspend fun arm(ends: RouteEnds) {
        if (_state.value !is RouteState.Idle) return
        clearSession()
        // The Idle → Choosing edge resolves the selection once: the engine chosen at this instant draws
        // every line of the session, and a setting changed later cannot reach it.
        _sessionEngine.value = selection.value
        _offers.value = emptyList()
        _candidateIndex.value = 0
        pendingDestination = null
        anchorTold = false
        val (anchor, refusal) = resolveAnchor(ends.start, ends.fallbackStart)
        _state.value = RouteState.Choosing(start = anchor, plan = null, originRefusal = refusal)
        val destination = ends.destination ?: return
        ask(destination)
    }

    /**
     * The acquisition's anchor and the anchor's own refusal (R3, R7).
     *
     * The lead is **best-effort and never binding**: a predicted point that is not water falls back to
     * the live fix and the acquisition proceeds, because a boat bearing down on a headland is precisely
     * the case a fresh acquisition exists for. The fallback point is what the judgement is then asked of.
     * An end that is not a prediction — the standing marker position, a flagged marker — has no fallback
     * and is judged where it stands.
     */
    private suspend fun resolveAnchor(
        start: RoutePoint?,
        fallback: RoutePoint?
    ): Pair<RoutePoint?, RouteRefusalReason?> {
        if (start == null) return null to null
        if (fallback == null || fallback == start) return start to engine().validatePoint(start)
        if (engine().validatePoint(start) == null) return start to null
        return fallback to engine().validatePoint(fallback)
    }

    /**
     * **The ask** — the acquisition's own, fired by [arm] and by nothing else (R49, R50).
     *
     * The destination is written into [pendingDestination] — replacing whatever is there — and the
     * worker is started. There is no throttle of the caller's own, because this function does no search
     * at all, and no timer asks on behalf of anyone.
     */
    private fun ask(destination: RoutePoint) {
        val choosing = _state.value as? RouteState.Choosing ?: return
        // A phase with no anchor yet — the mode armed before the first fix — refuses the ask rather
        // than inventing a start. A refused anchor refuses it too: there is nothing to measure from.
        if (choosing.start == null || choosing.originRefusal != null) return
        if (!engineState.value.ready) {
            // An arming can arrive before the engine is ready. One preparation is asked for and the ask
            // then restarts itself once — never in a loop, because an engine that is still not ready
            // after a completed preparation has said so, and the toggle's gate has already reported it.
            if (engineState.value is RouteEngineState.NotReady) {
                viewModelScope.launch { if (prepareCurrent().ready) ask(destination) }
            }
            return
        }
        pendingDestination = destination
        _offers.value = emptyList()
        _candidateIndex.value = 0
        _state.value = choosing.copy(searching = true, asked = true)
        startWorker()
    }

    /**
     * **The one worker's body: take the newest destination, ask the engine, then take the newest again
     * until the slot is empty.**
     *
     * It is started by a new ask **after cancelling the worker in flight** — which is the abort R4 asks
     * for — so the worker never queues behind a computation nobody wants any more. An abort leaves the
     * front line where it is: only an answer that lands writes one. The loop ends when the slot is
     * empty, and *that* is where `searching` goes false, so a refusal is never shown on a frame that
     * still has work behind it.
     *
     * The acquisition's anchor is told here, on the **first** ask and never on the arming frame: an
     * engine told an origin while it still holds the previous session's destination computes a route
     * nobody asked for, so the two ends are told together as part of the same acquisition (R2).
     */
    private fun startWorker() {
        askJob?.cancel()
        askJob = viewModelScope.launch {
            while (true) {
                val destination = pendingDestination ?: break
                pendingDestination = null
                val choosing = _state.value as? RouteState.Choosing ?: break
                val start = choosing.start ?: break
                if (!anchorTold) {
                    engine().onOriginPositionChanged(start)
                    anchorTold = true
                }
                // The destination is judged as it is asked for (R6): a refused one paints the crosshair
                // and says why, and no route is asked for it.
                val refusal = engine().validatePoint(destination)
                val answer = if (refusal == null) engine().onDestinationPositionChanged(destination) else null
                // An answer that lands after the route was selected or ended must not rewrite the mode:
                // only an acquisition phase accepts a line, which is the whole hand-off rule.
                val still = _state.value as? RouteState.Choosing ?: break
                _state.value = when {
                    // A refused destination's state **stands alone** (R6): the sentence names the reason
                    // and the outcomes hide, which is only true if the phase holds no plan — so any line
                    // a previous ask resolved is dropped here rather than shown beside the crosshair.
                    refusal != null -> still.copy(
                        plan = null,
                        searching = true,
                        asked = true,
                        refusal = refusal
                    )
                    answer is RouteResult.Success -> {
                        val plan = RoutePlan.of(start, answer, System.currentTimeMillis())
                        putSession(plan, null)
                        still.copy(plan = plan, searching = true, asked = true, refusal = null)
                    }
                    // An ask with no route to it leaves the acquisition armed and the standing line
                    // where it is.
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
     * interaction and never a loop**: the caller shows the reason rather than asking again.
     */
    suspend fun prepareAgain(): RouteEngineState =
        if (engineState.value.ready) engineState.value else prepareCurrent()

    /**
     * **Next/prev over the candidate set** (R54) — [delta] steps the selection and it **loops**, so a
     * press past either end comes back on the other. A set of one has nothing to step through and the
     * press is ignored rather than moving the emphasis onto a line that is not there.
     */
    fun stepCandidate(delta: Int) {
        val count = _candidates.value.size
        if (count <= 1 || delta == 0) return
        _candidateIndex.value = routeStepIndex(_candidateIndex.value, delta, count)
    }

    /**
     * **`Select route`** (R56) — writes nothing, makes the **selected** line the route, drops the
     * candidates it did not take and enters navigation.
     *
     * The plan is read from the acquisition's own set rather than handed in, so what the map painted and
     * what the toggle then follows cannot be two different lines.
     */
    fun selectRoute() {
        _state.value as? RouteState.Choosing ?: return
        val selected = selectedLine() ?: return
        pendingDestination = null
        askJob?.cancel()
        askJob = null
        _offers.value = emptyList()
        _candidateIndex.value = 0
        _state.value = RouteState.Following(selected)
    }

    /** The line the selection stands on right now, or null while the set is empty. */
    fun selectedLine(): RoutePlan? = _candidates.value.getOrNull(
        _candidateIndex.value.coerceIn(0, (_candidates.value.size - 1).coerceAtLeast(0))
    )

    /** The panel's Exit — the acquisition's own door, and every other ending (R57). */
    fun end() {
        pendingDestination = null
        askJob?.cancel()
        askJob = null
        clearSession()
        anchorTold = false
        _offers.value = emptyList()
        _candidateIndex.value = 0
        _state.value = RouteState.Idle
        // The return to Idle releases the session engine: the next arming resolves the selection again,
        // so the engine that drew the finished route is no longer held.
        _sessionEngine.value = null
    }

    /** **The session's routes, in creation order** (R25) — one per arming, oldest first. */
    fun sessionRoutes(): List<RoutePlan> = session.keys.toList()

    /** The track a route of this session was already written as, or null while it has none. */
    fun trackFor(plan: RoutePlan): String? = session[plan]

    /**
     * **Is this route already written?** — the one predicate both saves read (R55, R59), rather than a
     * null check at each call site.
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
