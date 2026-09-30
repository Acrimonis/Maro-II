package ykws.android.maro.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import ykws.android.maro.spatial.RouteDeclarations
import ykws.android.maro.spatial.RouteEngine
import ykws.android.maro.spatial.RouteId
import ykws.android.maro.spatial.RouteReason
import ykws.android.maro.spatial.RouteStage
import ykws.android.maro.spatial.RouteUpdate
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
     */
    fun trackName(): String = TrackFromCourse.routeTrackName(computedAtMs)

    /**
     * What is left of the route from [from] onward.
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
    }
}

/**
 * **The phase the mode is in** — the two on-phases and off, named once so the surface that paints the
 * mode keys on the phase rather than on the mode's switch (R51).
 */
enum class RoutePhase {

    /** The mode is off: no line, no route. */
    IDLE,

    /** The acquisition: the engine is searched from the drawer's own pair and its pages picked. */
    CHOOSING,

    /** A route is followed: the toggle is blue and the line is locked. */
    FOLLOWING
}

/**
 * The route's state machine: **Idle → Choosing → Following**, and nothing else.
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
     * **Acquiring the route**, from [start]: [plan] is the selected page's line, or null while nothing
     * has landed.
     *
     * [start] is the acquisition's **anchor**, resolved once from the drawer's standing pair at the
     * instant the acquisition was armed (R44, R71) and held for the whole acquisition. It is null
     * before the first fix, and the ask is refused rather than invented while it is.
     *
     * [searching] says a lookup is still in flight — the pages whose result has not landed and whose
     * reason has not been told. [refusal] is the reason the acquisition cannot answer: the pair's own
     * refusal from [RouteEngine.routesToCompute], or the selected lookup's reason on the update flow.
     */
    data class Choosing(
        val start: RoutePoint?,
        override val plan: RoutePlan?,
        val searching: Boolean = false,
        val asked: Boolean = false,
        val refusal: RouteReason? = null
    ) : RouteState {
        override val phase: RoutePhase get() = RoutePhase.CHOOSING
    }

    /** Navigating a route: [plan] is the **selected** line. */
    data class Following(override val plan: RoutePlan) : RouteState {
        override val phase: RoutePhase get() = RoutePhase.FOLLOWING
    }
}

/**
 * **The two ends the drawer's selectors resolved at the trigger** (R44, R46, R71), read by the screen
 * at the instant the acquisition is armed.
 */
data class RouteEnds(
    val start: RoutePoint?,
    val fallbackStart: RoutePoint?,
    val destination: RoutePoint?
)

/**
 * All of the Route feature's runtime state, in one place and on `StateFlow`.
 *
 * **Arming is the trigger** (R49, R50): the drawer's pair is read at that instant and the engine is
 * asked which routes it can compute between the two. The engine repairs the pair and answers either
 * [RouteDeclarations.Refused] — the status line carries the reason — or [RouteDeclarations.Available],
 * whose computations are each started as a lookup. Every lookup becomes a page; the main is index 0,
 * the pages are walked with next/previous, and the selected page is what the buttons act on.
 *
 * **Disposal.** The one disposal function is the only thing that calls `cancelLookup`, and every
 * site that drops a route — the exit dialog's discard, the fan's unasked Discard, a new arming and a
 * selection — reaches it. An update for a cancelled id changes no page.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RouteViewModel(

    /** The selection of the engine the chosen algorithm builds, handed in by whoever builds the VM. */
    private val selection: StateFlow<RouteEngine>
) : ViewModel() {

    /** The engine the running session was armed with, or null while the mode is idle. */
    private val _sessionEngine = MutableStateFlow<RouteEngine?>(null)

    /** The engine the feature calls right now — the session's own while one runs, else the selected. */
    private fun engine(): RouteEngine = _sessionEngine.value ?: selection.value

    private val _state = MutableStateFlow<RouteState>(RouteState.Idle)
    val state: StateFlow<RouteState> = _state.asStateFlow()

    /** One page per started lookup, in start order — index 0 is the main. */
    private val _pages = MutableStateFlow<List<RoutePage>>(emptyList())
    val pages: StateFlow<List<RoutePage>> = _pages.asStateFlow()

    /** Which page the selection stands on: the one the buttons act on and the map paints at full strength. */
    private val _selectedIndex = MutableStateFlow(0)
    val selectedIndex: StateFlow<Int> = _selectedIndex.asStateFlow()

    /** The main lookup's current stage — the panel's `Acquiring (stage)…` word. */
    private val _stage = MutableStateFlow<RouteStage?>(null)
    val stage: StateFlow<RouteStage?> = _stage.asStateFlow()

    /** The main lookup's partial line, for the provisional overlay. */
    private val _provisionalLine = MutableStateFlow<List<RoutePoint>>(emptyList())
    val provisionalLine: StateFlow<List<RoutePoint>> = _provisionalLine.asStateFlow()

    private val _paceKn = MutableStateFlow(AppConfig.routeFreeWaterPaceKn.toDouble())

    /** The pace the trip figure plans at (kn) — set pace, or the boat's own once it has evidence. */
    val paceKn: StateFlow<Double> = _paceKn.asStateFlow()

    /** The session's routes and, for the ones already written, the track each became (R25). */
    private val session = LinkedHashMap<RoutePlan, String?>()

    /** The session as the UI reads it: the link table alone, republished on every write. */
    private val _sessionLinks = MutableStateFlow<Map<RoutePlan, String?>>(emptyMap())
    val sessionLinks: StateFlow<Map<RoutePlan, String?>> = _sessionLinks.asStateFlow()

    /** The pace window: recent readings, oldest first, pruned to [RoutePace.WINDOW_MS]. */
    private val paceSamples = ArrayDeque<RoutePace.Sample>()

    /** lookupId → page index, so an update lands on the page that owns it. */
    private val lookupPages = mutableMapOf<RouteId, Int>()

    /** Lookup ids the flow has disposed of; an update for one changes no page. */
    private val cancelledLookups = mutableSetOf<RouteId>()

    init {
        viewModelScope.launch {
            combine(selection, _sessionEngine) { selected, session -> session ?: selected }
                .flatMapLatest { it.updates }
                .collect { onUpdate(it) }
        }
    }

    /**
     * **Arms the mode on the drawer's standing pair** — the machine's Idle → Choosing edge, and its only
     * trigger (R49, R50).
     *
     * The ends are read at this instant, the engine is asked which routes it can compute between them,
     * and each declared computation is started as a lookup — the main first, the rest after it. A
     * refused pair arms the toggle and the status line carries the reason: no engine answer gates the
     * mode.
     */
    suspend fun arm(ends: RouteEnds) {
        if (_state.value !is RouteState.Idle) return
        clearSession()
        _sessionEngine.value = selection.value
        _pages.value = emptyList()
        _selectedIndex.value = 0
        _stage.value = null
        _provisionalLine.value = emptyList()
        lookupPages.clear()
        cancelledLookups.clear()
        val start = ends.start
        val destination = ends.destination
        if (start == null || destination == null) {
            _state.value = RouteState.Choosing(start = start, plan = null)
            return
        }
        when (val declarations = engine().routesToCompute(start, destination)) {
            is RouteDeclarations.Refused -> {
                _state.value = RouteState.Choosing(
                    start = start, plan = null, asked = true, refusal = declarations.reason
                )
            }
            is RouteDeclarations.Available -> {
                for (computation in declarations.computations) {
                    val lookupId = engine().startLookup(computation.id)
                    lookupPages[lookupId] = _pages.value.size
                    _pages.value = _pages.value + RoutePage(
                        lookupId = lookupId,
                        computationId = computation.id,
                        descriptionResId = computation.descriptionResId
                    )
                }
                _state.value = RouteState.Choosing(
                    start = start, plan = null, searching = true, asked = true, refusal = null
                )
            }
        }
    }

    /** One update from the engine in force: lands on the page that owns the id, or is dropped. */
    private fun onUpdate(update: RouteUpdate) {
        if (update.routeId in cancelledLookups) return
        val index = lookupPages[update.routeId] ?: return
        val current = _pages.value
        if (index !in current.indices) return
        val nowMs = System.currentTimeMillis()
        val updated = when {
            update.result != null -> {
                val plan = RoutePlan.of(update.result.points.first(), update.result, nowMs)
                if (!session.containsKey(plan)) putSession(plan, null)
                current[index].copy(plan = plan)
            }
            update.reason != null -> current[index].copy(reason = update.reason)
            else -> current[index]
        }
        val newPages = current.toMutableList().also { it[index] = updated }
        _pages.value = newPages
        // The main lookup drives the stage and the provisional line. The provisional line clears with
        // the terminal update — the full line is then the page itself, never a partial overlay.
        if (index == MAIN_INDEX) {
            _stage.value = update.nextStage
            if (update.nextStage == null) {
                _provisionalLine.value = emptyList()
            } else if (update.line.isNotEmpty()) {
                _provisionalLine.value = update.line
            }
        }
        syncChoosing(newPages)
    }

    /** Re-reads the Choosing phase's plan, searching flag and refusal from the page set. */
    private fun syncChoosing(pages: List<RoutePage>) {
        val choosing = _state.value as? RouteState.Choosing ?: return
        val selected = pages.getOrNull(_selectedIndex.value)
        _state.value = choosing.copy(
            plan = selected?.plan,
            searching = pages.any { it.plan == null && it.reason == null },
            refusal = selected?.reason ?: choosing.refusal
        )
    }

    /**
     * **Next/prev over the page set** — [delta] steps the selection and it **loops**. A set of one has
     * nothing to step through and the press is ignored.
     */
    fun stepPage(delta: Int) {
        val count = _pages.value.size
        if (count <= 1 || delta == 0) return
        _selectedIndex.value = routeStepIndex(_selectedIndex.value, delta, count)
        syncChoosing(_pages.value)
    }

    /**
     * **`Select route`** (R56) — writes nothing, makes the **selected** line the route, drops the
     * lookups it did not take and enters navigation.
     */
    fun selectRoute() {
        _state.value as? RouteState.Choosing ?: return
        val selected = selectedPlan() ?: return
        disposeLookups()
        _pages.value = emptyList()
        _selectedIndex.value = 0
        _stage.value = null
        _provisionalLine.value = emptyList()
        _state.value = RouteState.Following(selected)
    }

    /** The page the selection stands on right now, or null while the set is empty. */
    fun selectedPage(): RoutePage? = _pages.value.getOrNull(
        _selectedIndex.value.coerceIn(0, (_pages.value.size - 1).coerceAtLeast(0))
    )

    /** The selected page's plan, or null while it has none. */
    fun selectedPlan(): RoutePlan? = selectedPage()?.plan

    /** The panel's Exit — the acquisition's own door, and every other ending (R57). */
    fun end() {
        disposeLookups()
        clearSession()
        _pages.value = emptyList()
        _selectedIndex.value = 0
        _stage.value = null
        _provisionalLine.value = emptyList()
        lookupPages.clear()
        cancelledLookups.clear()
        _state.value = RouteState.Idle
        _sessionEngine.value = null
    }

    /** **The one disposal function** — the only thing that calls `cancelLookup`, for every in-flight id. */
    private fun disposeLookups() {
        val active = _sessionEngine.value ?: return
        for (lookupId in lookupPages.keys) {
            active.cancelLookup(lookupId)
            cancelledLookups += lookupId
        }
    }

    /** **The session's routes, in creation order** (R25) — one per landed answer, oldest first. */
    fun sessionRoutes(): List<RoutePlan> = session.keys.toList()

    /** The track a route of this session was already written as, or null while it has none. */
    fun trackFor(plan: RoutePlan): String? = session[plan]

    /** **Is this route already written?** — the one predicate both saves read (R55, R59). */
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

    /** One pace reading for the window. */
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

        /** Index 0 of the page set — the main lookup the buttons act on by default. */
        const val MAIN_INDEX = 0

        /**
         * Factory for [RouteViewModel]. The selection is handed in rather than chosen here, and that
         * is the seam.
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
