package ykws.android.maro.ui.map

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ykws.android.maro.data.model.DepthSample
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.data.model.RouteResult
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.spatial.RouteAvoidEngine
import ykws.android.maro.spatial.RouteEngine
import ykws.android.maro.spatial.RouteEngineState
import ykws.android.maro.spatial.RouteRefusalReason
import ykws.android.maro.spatial.RouteStage
import ykws.android.maro.spatial.SpatialOperations
import ykws.android.maro.spatial.Units
import ykws.android.maro.spatial.avoid.AvoidEdge
import ykws.android.maro.spatial.avoid.AvoidWorld

/**
 * **The acquisition, as the machine sees it** — the change this pass landed, read through a real
 * [`RouteViewModel`] driven the way the map drives it.
 *
 * Four of its readings are the brief's own: nothing asks for a route until the user's own action does,
 * the anchor is re-read **per acquisition** with its live-fix fallback, a written route greys both
 * `Save track` actions through one predicate, and the acquisition's Exit is a **phase move** where a
 * route stands behind it rather than an ending. The fifth is the engine's own **stage channel**, whose
 * five boundaries and whose clearing are read off the shipped avoid engine.
 *
 * The two doors the screen owns rather than the machine — the toggle's off asking the one dialog, and
 * the back key following the acquisition's Exit — are wired in `MapScreen`'s own callbacks, so what is
 * pinned here is the **decision** behind them: [`RouteViewModel.exitAcquisition`] and the
 * `enteredFromRoute` reading the screen branches on.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RouteAcquisitionTest {

    private val start = RoutePoint(43.5000, 7.0000)
    private val aim = RoutePoint(43.5200, 7.0100)

    /** The boat has moved on, for Reroute's fresh anchor. */
    private val boatLater = RoutePoint(43.5050, 7.0030)

    /** The anchor a 12 kn course of 090° covers in the shelf's default ten seconds. */
    private val courseDeg = 90.0
    private val speedKn = 12.0
    private val leadSec = 10

    private fun fix(point: RoutePoint) = RouteFix(point, courseDeg = null, speedKn = null)

    private fun movingFix(point: RoutePoint) = RouteFix(point, courseDeg = courseDeg, speedKn = speedKn)

    /** The projected anchor, derived here rather than read back from the helper under assertion. */
    private fun predicted(point: RoutePoint): RoutePoint {
        val moved = SpatialOperations.pointAlongBearing(
            point.latitude,
            point.longitude,
            courseDeg,
            Units.knotsToMps(speedKn) * leadSec
        )
        return RoutePoint(moved.latitude, moved.longitude)
    }

    @Before
    fun installMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun releaseMainDispatcher() {
        Dispatchers.resetMain()
    }

    /**
     * **Nothing is computed until the acquisition asks** (R2), and that is the removal this pass made.
     *
     * Arming the mode is a read and nothing else: the engine is told no end and asked no route, so the
     * timer the ask gate used to be — the ground move and the settle — has no successor. `New route`'s
     * own entry is the same reading one phase later: it clears the destination and waits.
     */
    @Test
    fun nothingIsComputedUntilTheAcquisitionAsks() = runTest {
        val engine = CountingEngine()
        val viewModel = RouteViewModel(MutableStateFlow<RouteEngine>(engine))

        viewModel.beginDraft(fix(start))

        assertTrue("arming asks for no route", engine.askedDestinations.isEmpty())
        assertTrue("and tells the engine no end either", engine.toldOrigins.isEmpty())
        assertTrue("while the phase says it has asked nothing", !(viewModel.state.value as RouteState.Choosing).asked)
        assertFalse("and no search runs", (viewModel.state.value as RouteState.Choosing).searching)

        viewModel.acquire(aim)
        assertEquals("the press is what asks", listOf(aim), engine.askedDestinations)
        viewModel.confirm()

        viewModel.newRoute(fix(boatLater))

        val choosing = viewModel.state.value as RouteState.Choosing
        assertNull("New route clears the destination", choosing.plan)
        assertEquals(
            "and computes nothing until Acquire route is pressed",
            listOf(aim),
            engine.askedDestinations
        )
        assertEquals(
            "the anchor is not even told while the acquisition stands on nothing",
            listOf(start),
            engine.toldOrigins
        )
    }

    /**
     * **The anchor is re-read on every entry into the acquisition, led by the boat's own course and
     * speed** (R3) — an anchor held from the session's first arming would redraw the same line from the
     * same point, which is not a recompute at all.
     */
    @Test
    fun theAnchorIsLedOnEachEntryFromTheLiveFix() = runTest {
        val engine = CountingEngine()
        val viewModel = RouteViewModel(MutableStateFlow<RouteEngine>(engine))

        viewModel.beginDraft(movingFix(start))
        assertEquals(
            "the anchor is the fix led by the configured horizon",
            predicted(start),
            (viewModel.state.value as RouteState.Choosing).start
        )

        viewModel.acquire(aim)
        viewModel.confirm()
        viewModel.reroute(movingFix(boatLater))

        assertEquals(
            "and Reroute re-reads it rather than holding the session's",
            predicted(boatLater),
            (viewModel.state.value as RouteState.Choosing).start
        )
    }

    /**
     * **The lead is best-effort and never binding** (R3): a predicted point that is not water falls
     * back to the live fix, and the acquisition proceeds — a boat bearing down on a headland is
     * precisely the case a reroute exists for.
     *
     * The engine refuses the prediction alone, so the fallback is the only way the anchor can be
     * accepted, and what is read is that the anchor **is** the live fix and that the judgement was
     * asked of it (R7).
     */
    @Test
    fun theAnchorFallsBackToTheLiveFixWhereThePredictionIsNotWater() = runTest {
        val engine = CountingEngine(refuse = predicted(start))
        val viewModel = RouteViewModel(MutableStateFlow<RouteEngine>(engine))

        viewModel.beginDraft(movingFix(start))

        val choosing = viewModel.state.value as RouteState.Choosing
        assertEquals("the anchor is the live fix", start, choosing.start)
        assertNull("and the fallback was accepted, so the acquisition is not refused", choosing.originRefusal)
        assertEquals(
            "the judgement was asked of the prediction and then of the fallback",
            listOf(predicted(start), start),
            engine.validatedPoints
        )

        viewModel.acquire(aim)
        assertEquals(
            "and the line it draws starts at the fallback, not at the point off water",
            start,
            (viewModel.state.value as RouteState.Choosing).plan?.start
        )
    }

    /**
     * **`Confirm` is disabled without a plan** (R16) — the panel's own rule, read here as the machine's
     * counterpart: a confirmation with nothing to lock is ignored rather than locking an empty route.
     */
    @Test
    fun confirmWithoutAPlanIsIgnored() = runTest {
        val viewModel = RouteViewModel(MutableStateFlow<RouteEngine>(CountingEngine()))

        viewModel.beginDraft(fix(start))
        viewModel.confirm()

        val choosing = viewModel.state.value as RouteState.Choosing
        assertNull("the press locked nothing", choosing.plan)
        assertTrue("and the phase is still the acquisition", choosing.phase == RoutePhase.CHOOSING)
    }

    /**
     * **A written route greys both `Save track` actions** (R16, R17), because they read **one** fact:
     * the session's route-to-track link, through one predicate.
     *
     * The same reading is taken in both phases, which is the point — the acquisition's save and the
     * following phase's save cannot disagree about whether the front route is written.
     */
    @Test
    fun aWrittenRouteIsUnsavedForNoPhaseAndGreysBothSaves() = runTest {
        val viewModel = RouteViewModel(MutableStateFlow<RouteEngine>(CountingEngine()))

        viewModel.beginDraft(fix(start))
        viewModel.acquire(aim)
        val acquired = (viewModel.state.value as RouteState.Choosing).plan
            ?: error("a plan stands, so the acquisition has a front line")
        assertFalse("nothing is written yet", viewModel.isRouteSaved(acquired))

        viewModel.noteRouteSaved(acquired, "track-1")

        assertTrue("the link is the one fact the two saves read", viewModel.isRouteSaved(acquired))
        assertEquals("and the panel's own reading of it follows the session", "track-1", viewModel.trackFor(acquired))
        assertEquals(
            "the reactive mirror says the same, which is what the panel greys on",
            mapOf(acquired to "track-1"),
            viewModel.sessionLinks.value
        )

        viewModel.confirm()
        val front = (viewModel.state.value as RouteState.Following).plan
        assertEquals("and the followed route is that same front line", acquired, front)
        assertTrue("so the following phase's save is greyed by the same predicate", viewModel.isRouteSaved(front))
    }

    /**
     * **`Reroute` fires one acquisition and keeps the standing route** (R17): the engine is asked once —
     * by the anchor's own call, its held destination answering — and the route it replaced stays in the
     * session.
     */
    @Test
    fun rerouteFiresOneAcquisitionAndKeepsTheStandingRoute() = runTest {
        val engine = CountingEngine()
        val viewModel = RouteViewModel(MutableStateFlow<RouteEngine>(engine))

        viewModel.beginDraft(fix(start))
        viewModel.acquire(aim)
        viewModel.confirm()
        val locked = (viewModel.state.value as RouteState.Following).plan

        viewModel.reroute(fix(boatLater))

        val choosing = viewModel.state.value as RouteState.Choosing
        assertEquals("the anchor was told exactly once more", 2, engine.toldOrigins.size)
        assertEquals("and the destination was never re-stated, which is the one acquisition", 1, engine.askedDestinations.size)
        assertEquals("the standing route is the acquisition's ladder", listOf(locked), choosing.ladder)
        assertEquals(
            "and the session holds both, the new one last",
            listOf(locked, choosing.plan),
            viewModel.sessionRoutes()
        )
        assertTrue("the acquisition reads as entered from a route", choosing.enteredFromRoute)
    }

    /**
     * **`New route` clears the destination and keeps the session** (R17): the earlier lines stay drawn
     * on the ladder, so the drawing and the session both survive the move.
     */
    @Test
    fun newRouteClearsTheDestinationAndKeepsTheSession() = runTest {
        val engine = CountingEngine()
        val viewModel = RouteViewModel(MutableStateFlow<RouteEngine>(engine))

        viewModel.beginDraft(fix(start))
        viewModel.acquire(aim)
        viewModel.confirm()
        val locked = (viewModel.state.value as RouteState.Following).plan

        viewModel.newRoute(fix(boatLater))

        val choosing = viewModel.state.value as RouteState.Choosing
        assertNull("the destination is cleared", choosing.plan)
        assertEquals("the session survives for the drawing alone", listOf(locked), choosing.ladder)
        assertEquals("and it is still the session", listOf(locked), viewModel.sessionRoutes())
        assertTrue("the phase reads as entered from a route", choosing.enteredFromRoute)
    }

    /**
     * **The acquisition's Exit is a phase move where a route stands behind it** (R23), and an ending
     * where none does — which is the decision the panel's Exit and the back key both branch on.
     *
     * The acquisition's own answers are dropped on the way back: the route that stood behind the
     * acquisition returns with the line it had, not with the unconfirmed one.
     */
    @Test
    fun theAcquisitionExitIsAPhaseMoveWithARouteBehindItAndAnEndingWithNone() = runTest {
        val engine = CountingEngine()
        val viewModel = RouteViewModel(MutableStateFlow<RouteEngine>(engine))

        // (a) A fresh acquisition, standing on nothing: Exit ends the mode.
        viewModel.beginDraft(fix(start))
        viewModel.acquire(aim)
        viewModel.exitAcquisition()
        assertEquals("a fresh acquisition ends on Exit", RouteState.Idle, viewModel.state.value)
        assertTrue("and its session goes with it", viewModel.sessionRoutes().isEmpty())

        // (b) An acquisition entered from a followed route: Exit puts the mode back on that route.
        viewModel.beginDraft(fix(start))
        viewModel.acquire(aim)
        viewModel.confirm()
        val locked = (viewModel.state.value as RouteState.Following).plan

        viewModel.reroute(fix(boatLater))
        assertTrue("the acquisition stands on a plan of its own", (viewModel.state.value as RouteState.Choosing).plan != null)

        viewModel.exitAcquisition()

        val restored = viewModel.state.value as RouteState.Following
        assertEquals("the route that stood behind it comes back", listOf(locked), restored.routes)
        assertEquals("and the acquisition's own answer was dropped", listOf(locked), viewModel.sessionRoutes())
    }

    /**
     * **The stage channel is proxied from the engine in force** (R15), so the panel reads one value
     * whatever engine is installed.
     */
    @Test
    fun theStageIsProxiedFromTheEngineInForce() = runTest {
        val engine = CountingEngine()
        val viewModel = RouteViewModel(MutableStateFlow<RouteEngine>(engine))

        assertNull("nothing runs, nothing is said", viewModel.stage.value)

        engine.publishStage(RouteStage.SEARCH)

        assertEquals("and the stage follows the engine's own", RouteStage.SEARCH, viewModel.stage.value)

        engine.publishStage(null)

        assertNull("cleared with it", viewModel.stage.value)
    }

    /**
     * **The shipped avoid engine publishes each boundary it crosses and clears the stage with its
     * answer** (R15).
     *
     * The five stages are read in the order the pipeline crosses them, and the channel is **null** once
     * the search has returned — a stage left standing after the call that set it would be a lie on the
     * panel, and the dummy never sets one at all, so the panel falls back on its plain searching word.
     */
    @Test
    fun theAvoidEnginePublishesEveryBoundaryAndClearsTheStageOnItsAnswer() = runTest {
        val engine = RouteAvoidEngine(paceKn = { 28.0 }, worldProvider = { OpenWaterWorld() })
        val seen = ArrayList<RouteStage?>()
        // The collector runs **unconfined**, so it subscribes before the search starts and then
        // resumes on whichever thread publishes — a plain `launch` would only start when the test
        // yielded, and every boundary would already have been published and conflated away.
        val collectorScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val collector = collectorScope.launch { engine.stage.collect { seen += it } }

        engine.onOriginPositionChanged(start)
        assertTrue("the engine answers a route", engine.onDestinationPositionChanged(aim) is RouteResult.Success)

        val crossed = seen.filterNotNull()
        val order = listOf(
            RouteStage.CORRIDOR,
            RouteStage.GRID,
            RouteStage.SEARCH,
            RouteStage.PULL,
            RouteStage.SNAP
        )
        for (stage in order) {
            assertTrue("$stage was published as the pipeline crossed it", crossed.contains(stage))
        }
        assertEquals(
            "and in the order the pipeline crosses them",
            order,
            crossed.sortedBy { order.indexOf(it) }.distinct()
        )
        assertNull("the channel is cleared with the answer", engine.stage.value)

        collector.cancel()
        collectorScope.cancel()
    }
}

/** An open, ready world: water everywhere and no land, so the avoid pipeline runs to its answer. */
private class OpenWaterWorld : AvoidWorld {
    override val coastlineReady: Boolean get() = true
    override val depthReady: Boolean get() = true
    override val bandWidthM: Double get() = 0.0
    override val regionBounds: BBox? get() = null
    override fun segmentsIn(box: BBox): List<AvoidEdge> = emptyList()
    override fun openCoastIn(box: BBox): List<List<LatLng>> = emptyList()
    override fun isWater(latitude: Double, longitude: Double): Boolean = true
    override fun distanceToCoastM(latitude: Double, longitude: Double): Double = Double.MAX_VALUE
    override fun depthAt(latitude: Double, longitude: Double): DepthSample = DepthSample.NONE
    override suspend fun load(): RouteEngineState = RouteEngineState.Ready
}

/**
 * A counting engine with nothing to compute: it holds the ends as it is told them, answers the
 * straight line between them, judges one point unusable at most, and publishes whatever stage it is
 * handed — the three readings the acquisition's own tests need and no more.
 */
private class CountingEngine(
    /** The one point this engine judges unusable, or null for an engine that judges nothing. */
    private val refuse: RoutePoint? = null
) : RouteEngine {

    private val _state = MutableStateFlow<RouteEngineState>(RouteEngineState.Ready)

    override val state: StateFlow<RouteEngineState> = _state.asStateFlow()

    private val _stage = MutableStateFlow<RouteStage?>(null)

    override val stage: StateFlow<RouteStage?> = _stage.asStateFlow()

    /** Every destination the feature asked about, in order. */
    val askedDestinations = ArrayList<RoutePoint>()

    /** Every anchor the feature told this engine, in order. */
    val toldOrigins = ArrayList<RoutePoint>()

    /** Every point the feature asked this engine to judge, in order. */
    val validatedPoints = ArrayList<RoutePoint>()

    private var heldOrigin: RoutePoint? = null
    private var heldDestination: RoutePoint? = null

    /** Publishes the stage the panel would read. */
    fun publishStage(stage: RouteStage?) {
        _stage.value = stage
    }

    override suspend fun prepare(): RouteEngineState = RouteEngineState.Ready

    override suspend fun validatePoint(point: RoutePoint): RouteRefusalReason? {
        validatedPoints += point
        return if (point == refuse) RouteRefusalReason.OFF_WATER else null
    }

    override suspend fun onOriginPositionChanged(newPosition: RoutePoint): RouteResult? {
        toldOrigins += newPosition
        heldOrigin = newPosition
        val destination = heldDestination ?: return null
        return line(newPosition, destination)
    }

    override suspend fun onDestinationPositionChanged(newPosition: RoutePoint): RouteResult? {
        askedDestinations += newPosition
        heldDestination = newPosition
        val origin = heldOrigin ?: return null
        return line(origin, newPosition)
    }

    override suspend fun isReadyToRecompute(): Boolean = true

    private fun line(from: RoutePoint, to: RoutePoint): RouteResult {
        val distanceM = SpatialOperations.haversine(
            LatLng(from.latitude, from.longitude),
            LatLng(to.latitude, to.longitude)
        )
        val seconds = distanceM / Units.knotsToMps(9.0)
        return RouteResult.Success(
            points = listOf(from, to),
            legTimesSec = listOf(seconds),
            distanceM = distanceM,
            durationSec = seconds,
            destinationMoved = false
        )
    }
}
