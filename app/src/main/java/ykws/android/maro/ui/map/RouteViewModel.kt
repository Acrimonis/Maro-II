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
import kotlinx.coroutines.flow.distinctUntilChanged
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
 * A plan **split at a position**: the run already travelled and the run still ahead, both meeting at
 * the projected point, plus the remaining figures [RoutePlan.remainingFrom] reports.
 */
data class RouteSplit(
    val travelledPoints: List<RoutePoint>,
    val remainingPoints: List<RoutePoint>,
    /**
     * **The nearest leg the projection landed on** — the index `RouteHost` keys the paint's split
     * identity on, exposed here so a later change to the duplicate-vertex drop in [RoutePlan.splitAt]
     * cannot silently shift that key. It is `-1` where the plan has no leg (under two points).
     */
    val bestLegIndex: Int,
    val distanceM: Double,
    val durationSec: Double
)

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
     * The **zone share** of the trip's own time, set only where the slow-water budget was missed —
     * `null` means the line is inside the budget. Reported, never refused.
     */
    val budgetUnmetZoneShare: Double? = null,
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
     * The plan **split at [from]**: the nearest-leg projection decides the split point, so the two runs
     * meet exactly where [remainingFrom] measures from and that projection has one home.
     *
     * The projection clamps to `0..1` along the nearest leg, so a boat off to the side or past the
     * destination yields a wholly travelled or a wholly remaining run rather than an extrapolated one.
     * Where the projected point lands on the leg's own end it is **not repeated**: the remaining run
     * starts at that vertex, so at the destination it falls to a single point and the paint keeps the
     * whole line at full strength — the mode's arrival rule. A plan under two points has no leg at all,
     * so the whole line is the remaining run for the same reason: nothing sits behind the boat.
     */
    fun splitAt(from: RoutePoint): RouteSplit {
        if (points.size < 2) {
            return RouteSplit(emptyList(), points, -1, distanceM, durationSec)
        }
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
        val split = RoutePoint(projected.latitude, projected.longitude)
        val travelled = points.take(bestLeg + 1) + split
        val afterLeg = points.drop(bestLeg + 1)
        // The projected point coincides with the leg's own end once the boat is at or past it; the
        // remaining run then starts at that vertex rather than repeating it, so it can fall under two
        // points at the destination — the arrival rule the paint reads.
        val remaining = if (notYetTravelled <= 1e-9) afterLeg else listOf(split) + afterLeg
        return RouteSplit(travelled, remaining, bestLeg, remainingM, remainingSec)
    }

    /**
     * What is left of the route from [from] onward — the split's own remaining figures.
     */
    fun remainingFrom(from: RoutePoint): RouteRemainder {
        val split = splitAt(from)
        return RouteRemainder(split.distanceM, split.durationSec)
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
                budgetUnmetZoneShare = result.budgetUnmetZoneShare,
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
     * **Acquiring the route**, from [start]: [plan] is the **main's** line — index 0, the settled
     * answer the `Route auto` child waits on (R80) and the dashboard's planned figures read — or null
     * while nothing has landed. The page the seat stands on is a separate fact
     * ([RouteViewModel.selectedIndex]); a candidate landing first never moves this.
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
        val refusal: RouteReason? = null,
        /** Early select: the user committed to the main line, which is still finishing. */
        val committed: Boolean = false
    ) : RouteState {
        override val phase: RoutePhase get() = RoutePhase.CHOOSING
    }

    /** Navigating a route: [plan] is the **selected** line. */
    data class Following(
        override val plan: RoutePlan,
        /**
         * The stored track this route was followed from, or null when it came from an acquisition's
         * `Select route`. The id greys the exit dialog's save door and reads **Stop following** on its
         * third door, so a followed saved route is never written a second time.
         */
        val followedTrackId: String? = null
    ) : RouteState {
        override val phase: RoutePhase get() = RoutePhase.FOLLOWING
    }
}

/**
 * **The two ends the drawer's selectors resolved at the trigger** (R44, R46, R71), read by the screen
 * at the instant the acquisition is armed.
 *
 * `start` and `destination` are the resolved **points**; `startMarkerId` and `destinationMarkerId` are
 * the **identities** behind them, non-null only where the selection was a flagged
 * [ykws.android.maro.data.route.RouteEndSelection.Marker] (R82). Both are read in the same instant, so
 * a later drawer change cannot re-point the standing line nor re-pair its stored match.
 */
data class RouteEnds(
    val start: RoutePoint?,
    val fallbackStart: RoutePoint?,
    val destination: RoutePoint?,
    /** The flagged marker id the start end stood on, or null when it was the boat's or a standing position. */
    val startMarkerId: String? = null,
    /** The flagged marker id the destination end stood on, or null when it was not a marker. */
    val destinationMarkerId: String? = null
)

/**
 * **A stored route matched to the armed pair** (R82, R86) — the line rebuilt from its track, handed to
 * [RouteViewModel.arm] so the acquisition lands on the stored line instead of searching.
 *
 * [trackId] is the track the line is already written as for the exact pair, so the save door is shut
 * (R85); it is **null for the return trip** (R87), whose mirrored plan is a new line no track holds, so
 * the session registers it with no link and the save door stays open.
 */
data class StoredRouteMatch(
    val plan: RoutePlan,
    val trackId: String?
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

    /**
     * The armed pair's marker ids (R82): set by [arm] from the ends it was handed and cleared by [end],
     * so the save site reads the pair the standing line was armed on rather than re-resolving the
     * drawer — which may since have moved.
     */
    private var armedStartMarkerId: String? = null
    private var armedDestinationMarkerId: String? = null

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
            // `distinctUntilChanged` keeps the one subscription to the engine's `updates` alive across a
            // session: `session ?: selected` is the same engine object whether the session is open or not,
            // and a `flatMapLatest` restart over a `replay = 0` shared flow would drop any update racing
            // the re-subscribe.
            combine(selection, _sessionEngine) { selected, session -> session ?: selected }
                .distinctUntilChanged()
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
     *
     * **[storedMatch]** replaces the search (R82): where a saved route already stands between the same
     * two markers, the line rebuilt from it lands as the sole, settled page — no lookup started, no
     * engine asked. Its plan is registered in the session **under the very instance the page carries**,
     * so `isRouteSaved` reads it through the same equality with no new predicate — shut for the exact
     * pair, whose track id is carried (R85), and **open for the mirrored return trip** (R87), a new line
     * no track holds and whose match therefore carries a null track id. [RouteState.Choosing.start] is
     * the **line's own first point** rather than the marker's current position, which may have moved
     * since the save.
     */
    suspend fun arm(ends: RouteEnds, storedMatch: StoredRouteMatch? = null) {
        if (_state.value !is RouteState.Idle) return
        clearSession()
        _sessionEngine.value = selection.value
        _pages.value = emptyList()
        _selectedIndex.value = 0
        _stage.value = null
        _provisionalLine.value = emptyList()
        lookupPages.clear()
        cancelledLookups.clear()
        armedStartMarkerId = ends.startMarkerId
        armedDestinationMarkerId = ends.destinationMarkerId
        val start = ends.start
        val destination = ends.destination
        if (storedMatch != null) {
            putSession(storedMatch.plan, storedMatch.trackId)
            _pages.value = listOf(RoutePage(plan = storedMatch.plan))
            _state.value = RouteState.Choosing(
                start = storedMatch.plan.start,
                plan = storedMatch.plan,
                searching = false,
                asked = true
            )
            return
        }
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
                // D12: the Driving-preference cursor names the ladder's initial rung — meaningful
                // for its three pages alone; any other computation count keeps the main. The seat
                // rides `routeSeatedIndex`, so the seating rule has one home; nothing has landed yet,
                // so the predicate answers that declaration unchanged.
                val preferredIndex = if (_pages.value.size == ROUTE_LADDER_RUNG_COUNT) {
                    routeRungIndex(AppConfig.routeAvoidSpeedZoneSoftCostAversion)
                } else {
                    MAIN_INDEX
                }
                _selectedIndex.value = routeSeatedIndex(_pages.value.map { it.plan != null }, preferredIndex)
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
        if (update.result != null) {
            val plan = RoutePlan.of(update.result.points.first(), update.result, nowMs)
            // D14: a rung within the collapse tolerance of one already landed folds into it — the
            // duplicate page is dropped and the survivor is marked as the collapse's own result.
            val tolerance = AppConfig.routeAvoidLadderCollapseToleranceM
            val survivor = current.indices.firstOrNull { i ->
                i != index &&
                    current[i].plan?.let { routeDispersionM(it.points, update.result.points) <= tolerance } == true
            }
            if (survivor != null) {
                val newPages = current.filterIndexed { i, _ -> i != index }.toMutableList()
                val survivorIndex = if (survivor > index) survivor - 1 else survivor
                newPages[survivorIndex] = newPages[survivorIndex].copy(collapsed = true)
                _pages.value = newPages
                // Re-map the surviving lookups to their new indices so a later rung still lands.
                remapLookupPages(newPages)
                // The collapsing rung may be the stage narrator (index 0): clear its provisional line
                // and stage, or the stale partial line would draw beside the surviving route.
                if (index == MAIN_INDEX) {
                    _stage.value = update.nextStage
                    if (update.nextStage == null) _provisionalLine.value = emptyList()
                }
                // D15: re-seat the selection on the nearest surviving rung, then let the seat
                // predicate settle it on the survivors — a mapped index on a landed row stays, one
                // left on a pending row steps to the nearest that has landed.
                val mapped = when {
                    _selectedIndex.value > index -> _selectedIndex.value - 1
                    _selectedIndex.value == index -> _selectedIndex.value.coerceAtMost(newPages.size - 1)
                    else -> _selectedIndex.value
                }.coerceIn(0, (newPages.size - 1).coerceAtLeast(0))
                _selectedIndex.value = routeSeatedIndex(newPages.map { it.plan != null }, mapped)
                syncChoosing(newPages)
                return
            }
            if (!session.containsKey(plan)) putSession(plan, null)
            val updated = current[index].copy(plan = plan)
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
            // The seat re-reads on every landing: the standing selection keeps a landed row, while
            // one left on a page whose plan has not landed steps to the nearest row that has.
            _selectedIndex.value = routeSeatedIndex(newPages.map { it.plan != null }, _selectedIndex.value)
            syncChoosing(newPages)
            // Early select: once the committed main line lands, the mode follows it. A refusal
            // instead un-commits, leaving the acquisition standing on the reason.
            if (index == MAIN_INDEX && update.nextStage == null) {
                val choosing = _state.value as? RouteState.Choosing ?: return
                if (choosing.committed) {
                    val landed = updated.plan
                    if (landed != null) {
                        _pages.value = emptyList()
                        _selectedIndex.value = 0
                        _state.value = RouteState.Following(landed)
                    } else {
                        _state.value = choosing.copy(committed = false)
                    }
                }
            }
            return
        }
        val updated = if (update.reason != null) current[index].copy(reason = update.reason) else current[index]
        val newPages = current.toMutableList().also { it[index] = updated }
        _pages.value = newPages
        if (index == MAIN_INDEX) {
            _stage.value = update.nextStage
            if (update.nextStage == null) {
                _provisionalLine.value = emptyList()
            } else if (update.line.isNotEmpty()) {
                _provisionalLine.value = update.line
            }
        }
        // A refusal is terminal for that page — it will never hold a line — so the seat cannot wait
        // for a landing to step off it. Re-seat before `syncChoosing`, so R94's "never parks on an
        // empty row while a line stands beside it" still holds when the row the seat stepped onto
        // refuses last.
        if (update.reason != null) {
            _selectedIndex.value = routeSeatedIndex(newPages.map { it.plan != null }, _selectedIndex.value)
        }
        syncChoosing(newPages)
        // A committed main answered with a refusal un-commits instead of following (same rule as a landing).
        if (index == MAIN_INDEX && update.nextStage == null) {
            val choosing = _state.value as? RouteState.Choosing ?: return
            if (choosing.committed) {
                val landed = updated.plan
                if (landed != null) {
                    _pages.value = emptyList()
                    _selectedIndex.value = 0
                    _state.value = RouteState.Following(landed)
                } else {
                    _state.value = choosing.copy(committed = false)
                }
            }
        }
    }

    /** Rebuilds the lookup-id → index map after a page removal, so a later rung still finds its page. */
    private fun remapLookupPages(pages: List<RoutePage>) {
        lookupPages.clear()
        pages.forEachIndexed { i, page -> page.lookupId?.let { lookupPages[it] = i } }
    }

    /**
     * Re-reads the Choosing phase's plan, searching flag and refusal from the page set.
     *
     * [RouteState.Choosing.plan] is the **main's** — index 0's, the settled answer the auto-pick waits
     * on (R80) — never the seat's: the seat is a separate fact, so a candidate that landed first
     * cannot move the answer. The refusal stays the **selected** page's, which is the one the panel
     * is describing.
     */
    private fun syncChoosing(pages: List<RoutePage>) {
        val choosing = _state.value as? RouteState.Choosing ?: return
        val selected = pages.getOrNull(_selectedIndex.value)
        _state.value = choosing.copy(
            plan = pages.getOrNull(MAIN_INDEX)?.plan,
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
     * **`Select route`** (R56) — the **seat's** page: writes nothing, makes the chosen line the route,
     * drops the lookups it did not take and enters navigation. The seat is the page the selection
     * stands on (R94), so the panel's own door follows what the reader picked.
     *
     * The entry point is a name of its own rather than an index argument: naming the main is
     * [selectMainRoute]'s sentence, so no caller can reach index 0 without saying so.
     */
    fun selectRoute() {
        followPage(_selectedIndex.value)
    }

    /**
     * **The fan's *Route auto* child** (R80) — **the main, named**: the one-shot promises the settled
     * answer whatever page the seat followed onto, so it takes index 0 and never the seat's candidate.
     */
    fun selectMainRoute() {
        followPage(MAIN_INDEX)
    }

    /**
     * The one selection body both doors share: the named page's plan becomes the followed route, or —
     * where that page has not landed — the early-select path commits the **main's** partial line
     * (R88). The page is only ever the seat's or the main's, so no pending page can silently commit
     * the main.
     */
    private fun followPage(pageIndex: Int) {
        val choosing = _state.value as? RouteState.Choosing ?: return
        val selected = _pages.value
            .getOrNull(pageIndex.coerceIn(0, (_pages.value.size - 1).coerceAtLeast(0)))
            ?.plan
        if (selected != null) {
            disposeLookups()
            _pages.value = emptyList()
            _selectedIndex.value = 0
            _stage.value = null
            _provisionalLine.value = emptyList()
            _state.value = RouteState.Following(selected)
            return
        }
        // Early select: the main's partial line is the committed track. The candidates are dropped
        // now, the main keeps running, and the mode follows the line the moment it lands.
        if (choosing.committed || _provisionalLine.value.size < 2) return
        commitToMain()
        _state.value = choosing.copy(committed = true)
    }

    /** Early select's own disposal: cancels the candidates and keeps the main lookup running. */
    private fun commitToMain() {
        val active = _sessionEngine.value ?: return
        for ((lookupId, index) in lookupPages) {
            if (index != MAIN_INDEX) {
                active.cancelLookup(lookupId)
                cancelledLookups += lookupId
            }
        }
        _pages.value = _pages.value.take(1)
        _selectedIndex.value = 0
    }

    /**
     * **Follow a saved route** — the track card's door: the stored line becomes the followed route
     * straight from Idle, with no engine ask and no session write. The track id rides the state so
     * the exit dialog reads the followed route as already written.
     */
    fun followSavedRoute(plan: RoutePlan, trackId: String) {
        if (_state.value !is RouteState.Idle) return
        _state.value = RouteState.Following(plan, followedTrackId = trackId)
    }

    /** The page the selection stands on right now, or null while the set is empty. */
    fun selectedPage(): RoutePage? = _pages.value.getOrNull(
        _selectedIndex.value.coerceIn(0, (_pages.value.size - 1).coerceAtLeast(0))
    )

    /** The selected page's plan, or null while it has none. */
    fun selectedPlan(): RoutePlan? = selectedPage()?.plan

    /** The two marker ids the armed line stood between, or nulls when an end was not a marker (R82). */
    fun armedMarkerIds(): Pair<String?, String?> = armedStartMarkerId to armedDestinationMarkerId

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
        armedStartMarkerId = null
        armedDestinationMarkerId = null
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
