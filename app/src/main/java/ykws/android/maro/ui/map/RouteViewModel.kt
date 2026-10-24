package ykws.android.maro.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ykws.android.maro.spatial.RouteTrace
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.data.model.RouteResult
import ykws.android.maro.data.model.RouteSlowLimit
import ykws.android.maro.data.route.PacePlateau
import ykws.android.maro.data.route.PaceReading
import ykws.android.maro.data.route.PaceSource
import ykws.android.maro.data.route.RoutePace
import ykws.android.maro.data.route.speedHistoryProfileKn
import ykws.android.maro.data.settings.AppSettings
import ykws.android.maro.data.track.TrackFromCourse
import ykws.android.maro.spatial.RouteDeclarations
import ykws.android.maro.spatial.RouteEngine
import ykws.android.maro.spatial.RouteId
import ykws.android.maro.spatial.RouteReason
import ykws.android.maro.spatial.RouteStage
import ykws.android.maro.spatial.RouteUpdate
import ykws.android.maro.spatial.RouteVerdict
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
     * The priced speed zones the route had to enter, by name — empty on an ordinary route.
     */
    val forcedCrossingZoneNames: List<String> = emptyList(),
    /**
     * The slow water the drawn line enters, by name — the zones whose limit in force stands below the
     * cruise pace, whether or not the search was charged for them
     * ([`RouteResult.Success.crossedSlowZoneNames`]). A **record of what the water was signed at**,
     * never a drawn fact; empty on a partial line and on a saved route read back.
     */
    val crossedSlowZoneNames: List<String> = emptyList(),
    val computedAtMs: Long,
    /**
     * The time spent per speed limit, the 300 m band standing apart — the panel's Speed limits line.
     * Empty on a partial line and on a saved route read back, neither of which carries the attribution.
     */
    val slowLimitSeconds: List<RouteSlowLimit> = emptyList(),
    /**
     * How long the search took to land this line, in seconds — read by the comment a saved route
     * carries. Null on a plan read back from a stored track, which recorded no search.
     */
    val searchDurationSec: Double? = null
) {
    /** The plan's length in nautical miles — the trip figure's own unit. */
    val distanceNm: Double get() = Units.metresToNauticalMiles(distanceM)

    /** The pace the plan itself holds (kn) — the distance it covers over the time it allots, one home with the row. */
    val plannedPaceKn: Double
        get() = routeAvgSpeedKn(distanceNm, durationSec)

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
     * starts at that vertex, so at the destination it falls under two points — an **empty run**, nothing
     * ahead drawn — and the travelled run carries the covered line in the shared dimming. A plan under
     * two points has no leg at all, so the whole line is the remaining run for the same reason: nothing
     * sits behind the boat.
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
        // points at the destination — the empty run the paint reads.
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

    /**
     * **The plan mirrored in place** (R99) — the *loss* mirror's transform: the same water read the
     * other way. [points] and [legTimesSec] reverse and the two ends exchange, while every settled
     * total rides unchanged — [distanceM], [durationSec], [destinationMoved], [computedAtMs],
     * [forcedCrossingZoneNames], [crossedSlowZoneNames] and [slowLimitSeconds] describe the line and
     * not a read direction.
     * Nothing is re-priced, so the drawn geometry and the route's clock stay
     * exactly the bytes the engine answered.
     *
     * It is the plan's own single home for the transform, so no caller reverses a polyline itself and
     * the two orientations of one route stay one identity (the session link bridges them).
     */
    fun reversed(): RoutePlan = copy(
        start = destination,
        destination = start,
        points = points.reversed(),
        legTimesSec = legTimesSec.reversed()
    )

    companion object {
        /** The plan a successful search answers with, dated the moment it landed. */
        fun of(start: RoutePoint, result: RouteResult.Success, nowMs: Long, searchDurationSec: Double? = null): RoutePlan {
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
                crossedSlowZoneNames = result.crossedSlowZoneNames,
                computedAtMs = nowMs,
                slowLimitSeconds = result.slowLimitSeconds,
                searchDurationSec = searchDurationSec
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
         * `Select route`. The id settles the exit dialog in its **written** state — the save door greys
         * and the third door reads **Leave** rather than **Discard route** — so a followed saved route is
         * never written a second time.
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
    private val selection: StateFlow<RouteEngine>,

    /** The live persisted settings — the seat reads the user's slow-water preference from here. */
    private val settings: StateFlow<AppSettings> = MutableStateFlow(AppSettings()),

    /**
     * Where the engine declares an arm's routes: its end repair reads the coast and the measured depth
     * line, a cold read the screen's own thread must not pay.
     */
    private val declareDispatcher: CoroutineDispatcher = Dispatchers.Default,

    /** Today's local date (`yyyy-MM-dd`) — read when a recorded trip starts, and at each reading while none runs. */
    private val localDate: () -> String = { java.time.LocalDate.now().toString() }
) : ViewModel() {

    /** The engine the running session was armed with, or null while the mode is idle. */
    private val _sessionEngine = MutableStateFlow<RouteEngine?>(null)

    /** Bumped by every arm and every exit, so an arm whose declaration outlived either is dropped. */
    private var armGeneration = 0

    /** Whether an arm's declaration is running off the screen's thread — a second arm waits it out. */
    private var declaringArm = false

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

    /** The preferred rung's ladder index, stored at arming — the seat's target before a winner exists. */
    private var preferredRungIndex: Int = MAIN_INDEX

    /**
     * **The ladder's running best, by the lookup that owns it** — the engine's own fold, reported at
     * every rung's terminal so the seat can follow each improvement. `null` until the first rung lands,
     * and cleared with the arm.
     */
    private val _runningBest = MutableStateFlow<RouteId?>(null)
    val runningBest: StateFlow<RouteId?> = _runningBest.asStateFlow()

    /**
     * **The settled set's verdict** — the crown and every landed rung's rank, the engine's own fold
     * reported at each rung's terminal beside the running best. It is **independent of the seat**: the
     * selection may stand anywhere, and the crown's mark and the ranks read off this value alone, so no
     * ranking rule is re-derived in the UI. `null` until the first rung lands, and cleared with the arm.
     */
    private val _verdict = MutableStateFlow<RouteVerdict?>(null)
    val verdict: StateFlow<RouteVerdict?> = _verdict.asStateFlow()

    /**
     * **The rungs folded into a survivor** — *dropped lookup → the lookup of the page that stood in its
     * place*. A folded twin is not a rung of its own, so the ranking counts it with its survivor
     * ([routeRankView]); the table is cleared with the arm.
     */
    private val _folded = MutableStateFlow<Map<RouteId, RouteId>>(emptyMap())

    /**
     * **The ranks and the crown the panel prints**, the engine's verdict read through the folds and
     * counted over the pages that stand. Live from the first landing; whether the crown's check is shown
     * is the panel's (it waits for the last rung).
     */
    val rankView: StateFlow<RouteRankView> =
        combine(_pages, _verdict, _folded) { pages, verdict, folded -> routeRankView(pages, verdict, folded) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, RouteRankView())

    /**
     * **The order the panel's rows stand in**, as lookup ids — a snapshot rewritten only when a route
     * lands, is refused or is merged away, so the figures stay live while a row moves at most three
     * times. Ids and not page indices, because a fold removes a page and re-maps the rest.
     */
    private val _order = MutableStateFlow<List<RouteId>>(emptyList())
    val order: StateFlow<List<RouteId>> = _order.asStateFlow()

    /**
     * **Whether the user has taken the seat over** — a hand selection (a row tap, a swipe) holds the
     * highlight on that route while the search runs, however the rows reorder. The last landing ends the
     * hold ([settleSeat]), moving the highlight to the winner. Reset by [arm] and [end].
     */
    private var seatFrozen = false

    private val _seatMovedAtEnd = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits once when the last landing moved a hand-picked highlight onto the winner — the panel's toast. */
    val seatMovedAtEnd: SharedFlow<Unit> = _seatMovedAtEnd.asSharedFlow()

    /** The main lookup's current stage — the menu drawer's search word; each row carries its own on its page. */
    private val _stage = MutableStateFlow<RouteStage?>(null)
    val stage: StateFlow<RouteStage?> = _stage.asStateFlow()

    /** The main lookup's partial line, for the provisional overlay. */
    private val _provisionalLine = MutableStateFlow<List<RoutePoint>>(emptyList())
    val provisionalLine: StateFlow<List<RoutePoint>> = _provisionalLine.asStateFlow()

    private val _paceKn = MutableStateFlow(AppConfig.routeFreeWaterPaceKn.toDouble())

    /** The pace the settled status word names (kn) — set pace, or the boat's own once it has evidence. */
    val paceKn: StateFlow<Double> = _paceKn.asStateFlow()

    /**
     * The armed pair's marker ids (R82): set by [arm] from the ends it was handed and cleared by [end],
     * so the save site reads the pair the standing line was armed on rather than re-resolving the
     * drawer — which may since have moved.
     */
    private var armedStartMarkerId: String? = null
    private var armedDestinationMarkerId: String? = null

    /** The wall-clock instant the standing arm began — the search duration's start. */
    private var armedStartMs = 0L

    /** The rank and rung labels the followed route carried when the acquisition closed — read at save time. */
    private var followedRank: Int? = null
    private var followedRungResIds: List<Int> = emptyList()

    /**
     * **Whether the followed plan currently reads reversed** (R99) — the loss mirror's own orientation
     * flag. False from every entry into `Following` (the plan handed in is the route *as armed*) and
     * toggled only by [onBoatFix], so it says which orientation the plan is read in.
     */
    private var mirrored = false

    /**
     * **The followed route's own progression reading** (R99, R100) — the time-to-go history, the arrival
     * cue's arming and the last flip's instant. Cleared on entering `Following` and at every flip, so the
     * samples never outlive the plan they were gathered against.
     */
    private var etaState = RouteEtaState()

    /** The session's routes and, for the ones already written, the track each became (R25). */
    private val session = LinkedHashMap<RoutePlan, String?>()

    /** The session as the UI reads it: the link table alone, republished on every write. */
    private val _sessionLinks = MutableStateFlow<Map<RoutePlan, String?>>(emptyMap())
    val sessionLinks: StateFlow<Map<RoutePlan, String?>> = _sessionLinks.asStateFlow()

    /*
     * The pace's state below — the plateau, the trip's date, the cached starting pace — is confined to the
     * main thread: the feed and the refresh arrive from composition, and the settings collector runs on
     * `viewModelScope`, whose dispatcher is the main one. `PacePlateau` is not synchronised and relies on it.
     */

    /** The reduction `Now` reads: created on first need and again at every trip start; the view model only feeds it. */
    private var plateau: PacePlateau? = null

    /**
     * The local date (`yyyy-MM-dd`) the day's saved pace is written under and read against. A recorded trip
     * pins it when it starts and never re-reads it, so a trip past midnight keeps its pace; with no recorded
     * trip running there is no trip to pin it, and it follows the calendar.
     */
    var tripDateIso: String = ""
        private set

    /** Whether a recorded trip is running — the one thing that pins [tripDateIso]. */
    private var tripRecording = false

    /** The instant of the last reading, refresh or trip start, so a settings move re-reads the pace on the feed's own clock. */
    private var lastFeedMs = 0L

    /** The starting pace, cached against the settings object and the date it was read for: a reading costs no profile. */
    private var startingFor: Pair<AppSettings, String>? = null
    private var starting = PaceReading(AppConfig.routeFreeWaterPaceKn.toDouble(), PaceSource.DEFAULT)

    private val _nowPace = MutableStateFlow(
        PaceReading(AppConfig.routeFreeWaterPaceKn.toDouble(), PaceSource.DEFAULT)
    )

    /** The pace `Now` plans at and where it comes from — whatever stop the Cruising speed row holds. */
    val nowPace: StateFlow<PaceReading> = _nowPace.asStateFlow()

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
        // The panel's row order moves only when a route lands, is refused or is merged away: the key below
        // is the pages' identities and whether each is settled, with the verdict and the folds, so a stage
        // word or a provisional figure (which change the pages but not the key) moves no row.
        viewModelScope.launch {
            combine(_pages, _verdict, _folded) { pages, verdict, folded ->
                Triple(pages.map { it.lookupId to (it.plan != null || it.reason != null) }, verdict, folded)
            }
                .distinctUntilChanged()
                .collect { applyOrder() }
        }
        // A fresh Calculate, or any settings move, re-reads the pace so the search never shows a stale value.
        viewModelScope.launch {
            settings.collect { recomputePace(lastFeedMs.takeIf { it > 0L } ?: System.currentTimeMillis()) }
        }
    }

    /** Writes the order the rows should now stand in, from the pages and the rank view as they are. */
    private fun applyOrder() {
        val pages = _pages.value
        val view = routeRankView(pages, _verdict.value, _folded.value)
        _order.value = routeTargetOrder(pages, view.ranks).mapNotNull { pages[it].lookupId }
    }

    /** Forgets the order and the fold table with the session they belonged to. */
    private fun resetOrder() {
        _order.value = emptyList()
        _folded.value = emptyMap()
    }

    /** The page indices in the order the panel shows them — the held snapshot over the current pages. */
    private fun displayOrder(pages: List<RoutePage> = _pages.value): List<Int> =
        routeApplyOrder(pages, _order.value)

    /**
     * **The last landing's selection** — once every page has landed or been refused, the highlight goes
     * to the winner, the top row, at the very moment the crown's check appears; a hand pick held through
     * the search ends there, and [seatMovedAtEnd] fires only when that moved a hand-picked highlight. A
     * committed early select stands untouched, and so does a set that crowned nothing.
     */
    private fun settleSeat(pages: List<RoutePage>) {
        val choosing = _state.value as? RouteState.Choosing ?: return
        if (choosing.committed || pages.any { it.plan == null && it.reason == null }) return
        val crown = routeRankView(pages, _verdict.value, _folded.value).crownLookupId ?: return
        val target = lookupPages[crown]?.takeIf { it in pages.indices && pages[it].plan != null } ?: return
        val moved = seatFrozen && _selectedIndex.value != target
        _selectedIndex.value = target
        seatFrozen = false
        if (moved) _seatMovedAtEnd.tryEmit(Unit)
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
        if (_state.value !is RouteState.Idle || declaringArm) return
        clearSession()
        _sessionEngine.value = selection.value
        _pages.value = emptyList()
        _selectedIndex.value = 0
        _stage.value = null
        _provisionalLine.value = emptyList()
        lookupPages.clear()
        cancelledLookups.clear()
        _runningBest.value = null
        _verdict.value = null
        resetOrder()
        armedStartMs = System.currentTimeMillis()
        followedRank = null
        followedRungResIds = emptyList()
        seatFrozen = false
        mirrored = false
        armedStartMarkerId = ends.startMarkerId
        armedDestinationMarkerId = ends.destinationMarkerId
        val start = ends.start
        val destination = ends.destination
        if (start == null || destination == null) {
            _state.value = RouteState.Choosing(start = start, plan = null)
            return
        }
        // The declaration runs off the screen's thread; an exit or a newer arm while it runs makes its answer
        // stale, and it is dropped rather than arming a mode the user has already left.
        val declaring = engine()
        val token = ++armGeneration
        declaringArm = true
        val answer = try {
            withContext(declareDispatcher) { declaring.routesToCompute(start, destination) }
        } finally {
            declaringArm = false
        }
        if (token != armGeneration) return
        when (val declarations = answer) {
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
                // The seat stands on the main until the ranking names a winner: the verdict's dial is a
                // weight over the finished lines and names no ladder rung (2026-10-15), so nothing else
                // seeds it and the seat resolves through the displayed order while the rungs land.
                preferredRungIndex = MAIN_INDEX
                _selectedIndex.value = routeSeatedPageIndex(_pages.value, preferredRungIndex, displayOrder())
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
        // Only a terminal carries the engine's running best and its settled verdict; taking them here
        // keeps every branch below reading one value, so a landed page and a refusal alike seat the same
        // winner and mark the same crown.
        if (update.nextStage == null) {
            _runningBest.value = update.runningBest?.lookupId
            _verdict.value = update.verdict
        }
        val current = _pages.value
        if (index !in current.indices) return
        val nowMs = System.currentTimeMillis()
        if (update.result != null) {
            val plan = RoutePlan.of(update.result.points.first(), update.result, nowMs, searchDurationSec())
            // D14: a rung within the collapse tolerance of one already landed folds into it — the
            // duplicate page is dropped and the survivor is marked as the collapse's own result.
            val tolerance = AppConfig.routeAvoidLadderCollapseToleranceM
            val survivor = current.indices.firstOrNull { i ->
                i != index &&
                    current[i].plan?.let { routeDispersionM(it.points, update.result.points) <= tolerance } == true
            }
            if (survivor != null) {
                RouteTrace.line {
                    val dispersion = current[survivor].plan?.let { routeDispersionM(it.points, update.result.points) }
                    "FOLD rung=${update.routeId} into=${current[survivor].lookupId} " +
                        "dispersion=${dispersion?.let { "%.1f".format(it) } ?: "n/a"}m tolerance=${"%.0f".format(tolerance)}m"
                }
                val newPages = current.filterIndexed { i, _ -> i != index }.toMutableList()
                val survivorIndex = if (survivor > index) survivor - 1 else survivor
                // The survivor keeps every rung's name: its own label first (or the names it already
                // gathered from an earlier fold), then the dropped page's, so the duplicate's name is
                // not lost with it.
                val survivorPage = newPages[survivorIndex]
                val foldedNames = survivorPage.foldedDescriptionResIds
                    .ifEmpty { listOfNotNull(survivorPage.descriptionResId) } +
                    listOfNotNull(current[index].descriptionResId)
                newPages[survivorIndex] = survivorPage.copy(
                    collapsed = true,
                    foldedDescriptionResIds = foldedNames
                )
                // The folded twin counts with its survivor in the ranking: recorded before the page set
                // moves, so the rank view never reads the page gone with its twin still unmapped.
                val survivorLookup = survivorPage.lookupId
                if (survivorLookup != null) {
                    _folded.value = _folded.value + (update.routeId to survivorLookup)
                }
                _pages.value = newPages
                // Re-map the surviving lookups to their new indices so a later rung still lands.
                remapLookupPages(newPages)
                // The collapsing rung may be the stage narrator (index 0): clear its provisional line
                // and stage, or the stale partial line would draw beside the surviving route.
                if (index == MAIN_INDEX) {
                    _stage.value = update.nextStage
                    if (update.nextStage == null) _provisionalLine.value = emptyList()
                }
                // D15: re-map the preferred rung across the removal, then re-seat through the ETA
                // view — a folded preference lands on its survivor, a surviving one on its shifted
                // page, and neither leaves the selection on a missing page.
                preferredRungIndex = when {
                    preferredRungIndex > index -> preferredRungIndex - 1
                    preferredRungIndex == index -> survivorIndex
                    else -> preferredRungIndex
                }.coerceIn(0, (newPages.size - 1).coerceAtLeast(0))
                // The winner the collapsed rung carried may name the page that just went: seatTo reads
                // the survivor through the re-mapped lookup table, so the seat never lands on a fold.
                if (!seatFrozen) {
                    _selectedIndex.value = seatTo(newPages)
                } else {
                    // A hand pick stays on its route when the removed page shifts the indices: re-found
                    // by lookup id, and carried to the survivor when the pick itself was the folded twin.
                    val picked = current.getOrNull(_selectedIndex.value)?.lookupId
                    val pickedNow = if (picked == update.routeId) survivorLookup else picked
                    val pickedIndex = newPages.indexOfFirst { it.lookupId == pickedNow }
                    if (pickedIndex >= 0) _selectedIndex.value = pickedIndex
                }
                syncChoosing(newPages)
                settleSeat(newPages)
                return
            }
            if (!session.containsKey(plan)) putSession(plan, null)
            // A landed page prints the settled figures alone: the provisional pair the boundary update
            // carried while it waited is cleared here, so no row can show a stale pair beside a plan.
            val updated = current[index].copy(plan = plan, provisional = null, stage = null)
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
            // The seat re-reads on every landing: the running best while the ranking has one, else the
            // main's slot — and never after a hand touch, which freezes it for good.
            if (!seatFrozen) _selectedIndex.value = seatTo(newPages)
            syncChoosing(newPages)
            settleSeat(newPages)
            // Early select: once the committed main line lands, the mode follows it. A refusal
            // instead un-commits, leaving the acquisition standing on the reason.
            if (index == MAIN_INDEX && update.nextStage == null) {
                val choosing = _state.value as? RouteState.Choosing ?: return
                if (choosing.committed) {
                    val landed = updated.plan
                    if (landed != null) {
                        captureFollowedFacts(updated)
                        _pages.value = emptyList()
                        _selectedIndex.value = 0
                        enterFollowing(landed)
                    } else {
                        _state.value = choosing.copy(committed = false)
                    }
                }
            }
            return
        }
        // A provisional pair rides its own boundary update and lands on the page that owns the id; a
        // terminal refusal clears it with the same stroke as a landing, since neither will settle further.
        // Every boundary also moves the page's own stage word (null on the terminal), so each row prints
        // the stage of the rung it describes.
        val updated = when {
            update.reason != null -> current[index].copy(reason = update.reason, provisional = null, stage = null)
            update.provisional != null ->
                current[index].copy(provisional = update.provisional, stage = update.nextStage)
            else -> current[index].copy(stage = update.nextStage)
        }
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
        if (update.reason != null && !seatFrozen) {
            _selectedIndex.value = seatTo(newPages)
        }
        syncChoosing(newPages)
        if (update.reason != null) settleSeat(newPages)
        // A committed main answered with a refusal un-commits instead of following (same rule as a landing).
        if (index == MAIN_INDEX && update.nextStage == null) {
            val choosing = _state.value as? RouteState.Choosing ?: return
            if (choosing.committed) {
                val landed = updated.plan
                if (landed != null) {
                    captureFollowedFacts(updated)
                    _pages.value = emptyList()
                    _selectedIndex.value = 0
                    enterFollowing(landed)
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
    /**
     * **Where the seat belongs right now** — the running best's page when the engine has named one,
     * otherwise the main's slot through the displayed order. Read through `lookupPages`, so a winner whose
     * page was folded away falls back to the seat's own resolution and never parks on a missing page.
     */
    private fun seatTo(pages: List<RoutePage>): Int {
        val bestIndex = _runningBest.value?.let { lookupPages[it] }
        if (bestIndex != null && bestIndex in pages.indices) return bestIndex
        return routeSeatedPageIndex(pages, preferredRungIndex, displayOrder(pages))
    }

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
     * **Set the seat to a page by its position in the displayed order** — the panel's absolute set, so
     * a swipe, a row tap or a pager sync names a page rather than stepping from wherever the seat stood.
     * A position off the set is clamped.
     */
    fun selectPage(displayIndex: Int) {
        val pages = _pages.value
        if (pages.isEmpty()) return
        val order = displayOrder(pages)
        _selectedIndex.value = order[displayIndex.coerceIn(0, order.lastIndex)]
        // A hand selection is the one thing that freezes the seat: from here no landing moves it.
        seatFrozen = true
        syncChoosing(pages)
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
     * **The fan's *Route auto* child** (R80) — **the final best**: the running best's live page, or the
     * seat's own resolution where the winner's page was folded away. The one-shot waits for the settled
     * set, so the winner it takes is the ranking's own rather than index 0 by construction.
     */
    fun selectBestRoute() {
        val bestIndex = _runningBest.value?.let { lookupPages[it] }
        followPage(bestIndex ?: seatTo(_pages.value))
    }

    /**
     * The one selection body both doors share: the named page's plan becomes the followed route, or —
     * where that page has not landed — the early-select path commits the **main's** partial line
     * (R88). The page is only ever the seat's or the main's, so no pending page can silently commit
     * the main.
     */
    private fun followPage(pageIndex: Int) {
        val choosing = _state.value as? RouteState.Choosing ?: return
        val page = _pages.value
            .getOrNull(pageIndex.coerceIn(0, (_pages.value.size - 1).coerceAtLeast(0)))
        val selected = page?.plan
        if (selected != null) {
            captureFollowedFacts(page)
            disposeLookups()
            _pages.value = emptyList()
            _selectedIndex.value = 0
            _stage.value = null
            _provisionalLine.value = emptyList()
            _runningBest.value = null
            _verdict.value = null
            resetOrder()
            enterFollowing(selected)
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

    /** Remembers the rank and rung labels the chosen line carried, so a later save still reads them. */
    private fun captureFollowedFacts(page: RoutePage) {
        val view = routeRankView(_pages.value, _verdict.value, _folded.value)
        followedRank = page.lookupId?.let { view.ranks[it] }
        followedRungResIds = page.foldedDescriptionResIds.ifEmpty { listOfNotNull(page.descriptionResId) }
    }

    /** The arm-to-now seconds, or null when no arm stands. */
    private fun searchDurationSec(): Double? =
        if (armedStartMs > 0L) (System.currentTimeMillis() - armedStartMs) / 1000.0 else null

    /**
     * **Follow a saved route** — the track card's door: the stored line becomes the followed route
     * straight from Idle, with no engine ask and no session write. The track id rides the state so
     * the exit dialog reads the followed route as already written.
     */
    fun followSavedRoute(plan: RoutePlan, trackId: String) {
        if (_state.value !is RouteState.Idle) return
        enterFollowing(plan, followedTrackId = trackId)
    }

    /**
     * **The one edge into `Following`** — every door (a chosen page, the auto-pick, an early-select
     * landing and a saved route) enters here, so the loss mirror and the progression history start
     * cleared with the line: the plan handed in is the route **as armed**, [mirrored] reads that
     * orientation, and [etaState] gathers its own samples afresh.
     */
    private fun enterFollowing(plan: RoutePlan, followedTrackId: String? = null) {
        mirrored = false
        etaState = RouteEtaState()
        _state.value = RouteState.Following(plan, followedTrackId)
    }

    /**
     * **One boat fix, fed from the map while a route is followed** (R99, R100) — purely positional: it
     * reads the followed plan's own time-to-go at [from] and, on a loss of `route.follow.swap.lossSec`
     * against its recent low, republishes `Following` with the plan reversed in place. Nothing re-arms
     * and nothing is asked of an engine: the drawn line and its clock are the same bytes, merely read
     * the other way. A fix with nothing to measure — the mode not following — leaves the plan as it
     * stands.
     *
     * [nowElapsedMs] is the `SystemClock` elapsed-realtime instant of the fix, carried in so the
     * history's one-a-second bucketing and the debounce read off the map's own clock rather than an
     * invented one.
     *
     * The answer is the two readings the screen acts on: [RouteEtaDecision.flip] says the line turned,
     * and [RouteEtaDecision.arrivalCue] says the time-to-go has just fallen below
     * `route.follow.arrival.etaSec` as a new low, so the arrival prompt (R100) is due.
     */
    internal fun onBoatFix(from: RoutePoint, nowElapsedMs: Long): RouteEtaDecision {
        val following = _state.value as? RouteState.Following
            ?: return RouteEtaDecision(state = etaState, flip = false, arrivalCue = false)
        val plan = following.plan
        val decision = routeEtaStep(
            state = etaState,
            etaSec = plan.remainingFrom(from).durationSec,
            nowMs = nowElapsedMs,
            lossSec = AppConfig.routeFollowSwapLossSec,
            debounceSec = AppConfig.routeFollowSwapDebounceSec,
            arrivalEtaSec = AppConfig.routeFollowArrivalEtaSec
        )
        etaState = decision.state
        if (decision.flip) {
            mirrored = !mirrored
            _state.value = following.copy(plan = plan.reversed())
        }
        return decision
    }

    /** The page the selection stands on right now, or null while the set is empty. */
    fun selectedPage(): RoutePage? = _pages.value.getOrNull(
        _selectedIndex.value.coerceIn(0, (_pages.value.size - 1).coerceAtLeast(0))
    )

    /** The selected page's plan, or null while it has none. */
    fun selectedPlan(): RoutePlan? = selectedPage()?.plan

    /** The rank and rung labels the standing save writes into its comment — read at save time. */
    data class RouteCommentFacts(val rank: Int?, val rungResIds: List<Int>)

    /** The comment facts for the line being saved: the live rank while choosing, the captured rank while following. */
    fun commentFacts(): RouteCommentFacts = when (val s = _state.value) {
        is RouteState.Choosing -> {
            val page = selectedPage()
            val view = routeRankView(_pages.value, _verdict.value, _folded.value)
            RouteCommentFacts(
                rank = page?.lookupId?.let { view.ranks[it] },
                rungResIds = page?.let { it.foldedDescriptionResIds.ifEmpty { listOfNotNull(it.descriptionResId) } }
                    ?: emptyList()
            )
        }
        is RouteState.Following -> RouteCommentFacts(followedRank, followedRungResIds)
        RouteState.Idle -> RouteCommentFacts(null, emptyList())
    }

    /** The two marker ids the armed line stood between, or nulls when an end was not a marker (R82). */
    fun armedMarkerIds(): Pair<String?, String?> = armedStartMarkerId to armedDestinationMarkerId

    /** The panel's Exit — the acquisition's own door, and every other ending (R57). */
    fun end() {
        armGeneration++
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
        preferredRungIndex = MAIN_INDEX
        _runningBest.value = null
        _verdict.value = null
        resetOrder()
        seatFrozen = false
        mirrored = false
        etaState = RouteEtaState()
        armedStartMs = 0L
        followedRank = null
        followedRungResIds = emptyList()
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

    /**
     * The track a route of this session was already written as, or null while it has none. **The read
     * direction is not an identity** (R99): the link is bridged across the loss mirror, so a reversed
     * plan answers the track its armed orientation was written as.
     */
    fun trackFor(plan: RoutePlan): String? = session[plan] ?: session[plan.reversed()]

    /**
     * **Is this route already written?** — the one predicate both saves read and the one fact the exit
     * dialog's doors resolve from (R55, R59). A route saved while its line is mirrored answers true for
     * **either** orientation (R99), so the mirror never resurrects the save door and the exit dialog's
     * third door still reads *Leave*.
     */
    fun isRouteSaved(plan: RoutePlan): Boolean =
        (session[plan] ?: session[plan.reversed()]) != null

    /**
     * Records the track a route was written as — the session's own link, dying with the mode. The link
     * is keyed on the orientation the route was **armed** in, so a save from a mirrored line updates
     * the very entry the landing created and the two directions share one track (R99).
     */
    fun noteRouteSaved(plan: RoutePlan, trackId: String) {
        val key = when {
            session.containsKey(plan) -> plan
            session.containsKey(plan.reversed()) -> plan.reversed()
            else -> return
        }
        session[key] = trackId
        publishSession()
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
     * A trip begins: the pace restarts from the day's saved pace while its date is today's, else the learned
     * history, else the shipped default. The trip pins the date here until [endTrip], so a trip past
     * midnight keeps its pace.
     */
    fun startTrip(nowMs: Long = System.currentTimeMillis()) {
        tripRecording = true
        tripDateIso = localDate()
        plateau = null
        lastFeedMs = nowMs
        recomputePace(nowMs)
    }

    /** The recorded trip has ended: the day's date follows the calendar again until the next one starts. */
    fun endTrip() {
        tripRecording = false
    }

    /** One speed reading for the pace — null when the feed has nothing to say, which a demo drive never does otherwise. */
    fun observePace(speedKn: Double?, restricted: Boolean, nowMs: Long) {
        lastFeedMs = nowMs
        followCalendar()
        plateauOf().observe(speedKn, restricted, nowMs)
        recomputePace(nowMs)
    }

    /**
     * Re-reads the pace at [nowMs] with no new reading. The feed speaks only when the speed value changes, so
     * a speed that holds still — a stop at 0 — would otherwise leave the pace and its Live word as they were
     * when the value last moved.
     */
    fun refreshPace(nowMs: Long) {
        lastFeedMs = nowMs
        followCalendar()
        recomputePace(nowMs)
    }

    /** With no recorded trip pinning it, the day's date is today's. */
    private fun followCalendar() {
        if (!tripRecording) tripDateIso = localDate()
    }

    private fun plateauOf(): PacePlateau = plateau ?: run {
        if (tripDateIso.isEmpty()) tripDateIso = localDate()
        val s = settings.value
        PacePlateau(s.paceWindowMs(), AppConfig.routePaceStopKn, startingPace().kn)
            .also { plateau = it }
    }

    /**
     * What a trip starts on, and the source that names it while no reading of this trip can yet move the
     * number. Read again only when the settings or the date move.
     */
    private fun startingPace(): PaceReading {
        val s = settings.value
        val key = startingFor
        if (key == null || key.first !== s || key.second != tripDateIso) {
            starting = RoutePace.starting(
                dayKn = s.routePaceDayKn.toDouble(),
                dayDate = s.routePaceDayDate,
                tripDate = tripDateIso,
                historyKn = s.speedHistoryProfileKn(),
                defaultKn = AppConfig.routeFreeWaterPaceKn.toDouble()
            )
            startingFor = s to tripDateIso
        }
        return starting
    }

    /** The pace window the settings ask for, in ms. */
    private fun AppSettings.paceWindowMs(): Long = routePaceWindowMin * 60_000L

    /** Re-reads the pace from the plateau and the settings: `Now` always, the planning pace only at `Any`. */
    private fun recomputePace(nowMs: Long) {
        val s = settings.value
        val p = plateauOf()
        p.windowMs = s.paceWindowMs()
        val start = startingPace()
        // The starting pace stays revisable only until the trip's first reading, after which the window is
        // made of it and revising it would feed the pace back into itself.
        if (!p.hasReadings) p.startKn = start.kn
        val read = p.read(nowMs)
        val kn = read.kn
        val live = RoutePace.isLive(read.readingMs, p.windowMs)
        _nowPace.value = PaceReading(kn, if (live) PaceSource.LIVE else start.source)
        _paceKn.value = if (AppConfig.isFreeWaterPaceAny(s.routeFreeWaterPaceKn)) {
            kn
        } else {
            s.routeFreeWaterPaceKn.toDouble()
        }
    }

    companion object {

        /** Index 0 of the page set — the main lookup the buttons act on by default. */
        const val MAIN_INDEX = 0

        /**
         * Factory for [RouteViewModel]. The selection is handed in rather than chosen here, and that
         * is the seam.
         */
        fun factory(
            selection: StateFlow<RouteEngine>,
            settings: StateFlow<AppSettings>
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(
                    modelClass: Class<T>,
                    extras: CreationExtras
                ): T = RouteViewModel(selection, settings) as T
            }
    }
}
