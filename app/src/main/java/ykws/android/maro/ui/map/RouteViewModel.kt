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
import ykws.android.maro.spatial.RouteEngine
import ykws.android.maro.spatial.RouteEngineState
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
 */
data class RoutePlan(
    val start: RoutePoint,
    val destination: RoutePoint,
    val destinationMoved: Boolean,
    val points: List<RoutePoint>,
    val legTimesSec: List<Double>,
    val distanceM: Double,
    val durationSec: Double,
    val inBand: Boolean,
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
     * What is left of the route from [from] onward.
     *
     * The position is snapped to its nearest vertex rather than projected onto the polyline: the
     * mesh is dense (tens to a couple of hundred metres a leg), so the two agree to within a leg,
     * and the sum still reaches zero at the destination — which is the one reading that must be
     * exact, because arrival is the trip cell reading zero.
     */
    fun remainingFrom(from: RoutePoint): RouteRemainder {
        if (points.size < 2) return RouteRemainder(distanceM, durationSec)
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
        var remainingM = 0.0
        var remainingSec = 0.0
        for (leg in bestIndex until points.size - 1) {
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
                inBand = result.inBand,
                forcedCrossingZoneNames = result.forcedCrossingZoneNames,
                computedAtMs = nowMs
            )
        }
    }
}

/**
 * The route's state machine: **Idle → Draft → Confirmed**, and nothing else.
 *
 * There is deliberately no arrival state and no arrival cue: reaching the destination is the trip
 * figure reading zero while the line stays drawn, and only the toggle ends a route. A draft carries
 * a null plan while it is aiming with nothing computed, which is an ordinary moment — the first
 * search of a session, or an aim standing on land where no route exists — not an error.
 */
sealed interface RouteState {

    /** The preview a state carries, or null while there is none. */
    val plan: RoutePlan?

    data object Idle : RouteState {
        override val plan: RoutePlan? get() = null
    }

    /**
     * Aiming, from [start]: [plan] is the live preview, or null while nothing has resolved.
     *
     * [start] is taken **once**, on the Idle → Draft edge, and held for the whole draft — and for the
     * plan a confirmation locks, which is that same point. Holding it here, rather than letting a
     * caller read its own seam again, is what makes the anchor frozen by construction: while the mode
     * is armed there is no position left to re-read, so only the aim moves — in GPS mode too, where a
     * live start would otherwise chase the fix. It is null before the first fix, and a preview is
     * refused rather than invented while it is.
     *
     * [searching] says a search is in flight, which is what tells "not yet" apart from "no route to
     * this aim" — the two read identically from a null plan and mean opposite things to whoever is
     * watching the map.
     */
    data class Draft(
        val start: RoutePoint?,
        override val plan: RoutePlan?,
        val searching: Boolean = false,
        /**
         * Whether an aim has been asked at all. It is what holds the refusal back: "no route to this
         * aim" and "nothing asked yet" both read as a null plan, and saying the first one on the
         * frame the mode opens would blink a refusal the next search is about to contradict.
         */
        val asked: Boolean = false
    ) : RouteState

    /**
     * Following [plan]. [stale] is the boat having left the covered water: the line stays, the
     * recompute is skipped rather than failing, and the trip figure keeps its last value marked so.
     */
    data class Confirmed(override val plan: RoutePlan, val stale: Boolean = false) : RouteState
}

/**
 * All of the Route feature's runtime state, in one place and on `StateFlow`.
 *
 * The engine is prepared once and its readiness held for the life of the ViewModel; a preview search
 * is the search of the moment and cancels its predecessor, which is what makes a flung map drop the
 * search in flight rather than queueing one per frame. **One search is *started* at a time, never
 * one *running* at a time**: the predecessor's job is cancelled and not joined — the UI must never
 * wait on a search — so it can still be finishing the expansion it was in while the next one starts.
 * What makes that harmless is the engine's own scratch discipline, stated at
 * [`RouteEngine.route`].
 *
 * The pace is the trip figure's: the set free-water pace until the boat's own samples have
 * something to say, then [RoutePace]'s own reduction of them. Samples taken inside a regulated zone
 * or the band are marked restricted by the caller and dropped there, so a limit is never mistaken
 * for the boat's pace.
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

    /** The last preview search, so a new aim cancels it instead of racing it. */
    private var previewJob: Job? = null

    /** The pace window: recent readings, oldest first, pruned to [RoutePace.WINDOW_MS]. */
    private val paceSamples = ArrayDeque<RoutePace.Sample>()

    init {
        viewModelScope.launch { engine.prepare() }
    }

    /**
     * Arms the mode: the machine's Idle → Draft edge, which is also where the route's start enters
     * the machine — [start] being the position the caller read at that instant: the GPS fix or the
     * demo seam's own reading, never the map centre the aim itself holds.
     *
     * The edge takes the start once and the draft keeps it, so no preview has a seam left to re-read.
     * A null start is the position before the first fix: the mode is armed, and the previews are
     * refused until a position exists rather than routed from an invented one.
     */
    fun beginDraft(start: RoutePoint?) {
        if (_state.value is RouteState.Idle) {
            _state.value = RouteState.Draft(start = start, plan = null)
        }
    }

    /**
     * Recomputes the preview for [aim] from the draft's own frozen start.
     *
     * The start is deliberately not a parameter: the machine already holds the position the mode
     * opened on, so a caller cannot hand it a fresher one and the search can only ever be asked from
     * the anchor. The call is cheap and idempotent from the caller's side: it cancels whatever search
     * is in flight and starts one.
     */
    fun preview(aim: RoutePoint) {
        val start = (_state.value as? RouteState.Draft)?.start ?: return
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
        previewJob?.cancel()
        (_state.value as? RouteState.Draft)?.let {
            _state.value = it.copy(searching = true, asked = true)
        }
        previewJob = viewModelScope.launch {
            val result = engine.route(start, aim, paceKn.value)
            // A search that lands after the route was confirmed or ended must not rewrite the mode:
            // only a draft accepts a preview, which is the whole of the hand-off rule.
            val current = _state.value
            if (current !is RouteState.Draft) return@launch
            _state.value = when (result) {
                is RouteResult.Success -> RouteState.Draft(
                    start = current.start,
                    plan = RoutePlan.of(start, result, System.currentTimeMillis()),
                    asked = true
                )
                // An aim with no route to it is a draft holding no preview, not an error: the mode
                // stays armed and the next aim is asked again.
                RouteResult.OutsideMesh, RouteResult.NoPath ->
                    RouteState.Draft(start = current.start, plan = null, asked = true)
            }
        }
    }

    /**
     * The dialog's Route outcomes: the aimed route is locked, carrying the start the draft was
     * opened on. Without a resolved plan there is nothing to confirm, so the press is ignored rather
     * than locking an empty route.
     *
     * Cancel is not the way back here — it ends the mode. The only draft this machine returns to is
     * the next Idle → Draft edge, which takes a fresh start.
     */
    fun confirm() {
        val plan = (_state.value as? RouteState.Draft)?.plan ?: return
        previewJob?.cancel()
        _state.value = RouteState.Confirmed(plan)
    }

    /** The toggle's off, and every other exit: the route ends, the draft is cancelled. */
    fun end() {
        previewJob?.cancel()
        previewJob = null
        _state.value = RouteState.Idle
    }

    /**
     * A flagged recompute while the route is followed, from wherever the boat now is: no route
     * found means the boat has left the covered water, so the plan and its figures stand and are
     * marked stale rather than the route being dropped.
     */
    fun recompute(start: RoutePoint) {
        val current = _state.value as? RouteState.Confirmed ?: return
        if (!engineState.value.ready) return
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            val result = engine.route(start, current.plan.destination, paceKn.value)
            val still = _state.value
            if (still !is RouteState.Confirmed) return@launch
            _state.value = when (result) {
                is RouteResult.Success -> RouteState.Confirmed(
                    RoutePlan.of(start, result, System.currentTimeMillis())
                )
                RouteResult.OutsideMesh, RouteResult.NoPath -> still.copy(stale = true)
            }
        }
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
         * The engine is handed in rather than chosen here, and that is the seam: the caller that
         * already holds the app's own coastline and regulation builds the shipped engine from those
         * instances — so a route prices its edges from the live layers rather than from two empty
         * copies of them — and a second engine, or a test's own fake, is one argument at one site.
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
