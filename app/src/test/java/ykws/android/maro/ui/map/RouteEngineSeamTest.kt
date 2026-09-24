package ykws.android.maro.ui.map

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.data.model.RouteResult
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.data.track.TrackFromCourse
import ykws.android.maro.spatial.RouteAvoidEngine
import ykws.android.maro.spatial.RouteDummyEngine
import ykws.android.maro.spatial.RouteEngine
import ykws.android.maro.spatial.RouteEngineState
import ykws.android.maro.spatial.RouteRefusalReason
import ykws.android.maro.spatial.RouteUnavailableReason
import ykws.android.maro.spatial.SpatialOperations
import ykws.android.maro.spatial.Units
import ykws.android.maro.spatial.avoid.AvoidEdge
import ykws.android.maro.spatial.avoid.AvoidWorld

/**
 * **The seam, exercised through the feature by an engine that is not the shipped one.**
 *
 * A contract with one implementation is a contract nobody has read: every reader of it compiles
 * against the incumbent by accident, and the first thing a second engine would have to borrow is
 * whatever the incumbent's answer happens to carry. This test is the counterweight — a **fake**
 * [`RouteEngine`] that knows no bake, no mesh and no chain is handed to a real [`RouteViewModel`],
 * and every reading below is taken from what the *feature* made of its answer.
 *
 * Three properties of the **session** shape are what it mostly exists for, because they are the ones
 * this delivery introduced: an engine told one end **holds the other**, a new ask **aborts the one in
 * flight and starts** rather than queueing, and the origin is **judged once** — at arming, never on a
 * refresh.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RouteEngineSeamTest {

    private val start = RoutePoint(43.5000, 7.0000)
    private val aim = RoutePoint(43.5200, 7.0100)

    /** A second position, for the refresh: the boat has moved while the route was followed. */
    private val boatLater = RoutePoint(43.5050, 7.0030)

    /**
     * A third position, for the drag: the abort is read on **which** aims a search was asked for, so
     * the superseded one and the newest one have to be two different places.
     */
    private val aimLater = RoutePoint(43.5300, 7.0200)

    /**
     * `viewModelScope` is the main dispatcher, and a JVM test has none: the eager test dispatcher
     * makes every launch the view model makes run to completion before the assertion reads it.
     */
    @Before
    fun installMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun releaseMainDispatcher() {
        Dispatchers.resetMain()
    }

    /** The distance the test derives for itself — never read back from the object under assertion. */
    private fun distanceM(from: RoutePoint, to: RoutePoint): Double = SpatialOperations.haversine(
        LatLng(from.latitude, from.longitude),
        LatLng(to.latitude, to.longitude)
    )

    /** The time a leg costs at a pace, in the units the engine answers with. */
    private fun secondsFor(distanceM: Double, paceKn: Double): Double =
        distanceM / Units.knotsToMps(paceKn)

    /**
     * **The gate is the engine's own readiness, read where the toggle reads it.**
     *
     * The toggle asks one question — `engineState.value.ready` — and it must be a function of
     * whichever engine the app runs on and of nothing else.
     */
    @Test
    fun theGateIsTheEnginesOwnReadinessAndTheViewModelHoldsIt() {
        val ready = RouteViewModel(selectionOf(StraightLineEngine()))
        assertTrue("a ready engine opens the mode", ready.engineState.value.ready)
        assertEquals("and the state is the engine's own", RouteEngineState.Ready, ready.engineState.value)

        val unprepared = RouteViewModel(selectionOf(StraightLineEngine(prepareStates = listOf(RouteEngineState.NotReady))))
        assertFalse("one that has not been prepared does not", unprepared.engineState.value.ready)
        assertEquals("and it says so in the state, not in prose", RouteEngineState.NotReady, unprepared.engineState.value)

        val unanswerable = RouteViewModel(
            selectionOf(
                StraightLineEngine(
                    prepareStates = listOf(
                        RouteEngineState.Unavailable(RouteUnavailableReason.REGION_NOT_BAKED)
                    )
                )
            )
        )
        assertFalse("and neither does one that has said it cannot answer", unanswerable.engineState.value.ready)
        assertEquals(
            "the reason rides in the closed set, and the string stays a resource",
            RouteEngineState.Unavailable(RouteUnavailableReason.REGION_NOT_BAKED),
            unanswerable.engineState.value
        )
    }

    /**
     * **A preview from a foreign engine reaches the plan and the trip figure**, and it reaches them
     * from the destination the drag gave — the engine holds the origin it was told at arming, so the
     * feature never hands a start to the destination's entry point.
     */
    @Test
    fun aPreviewSearchesThroughTheForeignEngineAndItsAnswerReachesThePlanAndTheTripFigure() = runTest {
        val engine = StraightLineEngine()
        val viewModel = RouteViewModel(selectionOf(engine))

        viewModel.beginDraft(start)
        viewModel.preview(aim)

        val choosing = viewModel.state.value as RouteState.Choosing
        val plan = choosing.plan ?: error("the foreign engine answered, so the phase must hold a plan")
        assertEquals(
            "the origin was told once, at arming",
            listOf(start),
            engine.toldOrigins
        )
        assertEquals(
            "and the destination's entry point was asked for the aim alone",
            listOf(aim),
            engine.askedDestinations
        )
        assertEquals("the line is the foreign engine's own", listOf(start, aim), plan.points)
        assertEquals(start, plan.start)

        val expectedM = distanceM(start, aim)
        val paceKn = StraightLineEngine.FIXTURE_PACE_KN
        assertEquals("the plan's length is the engine's own answer", expectedM, plan.distanceM, 1e-6)
        assertEquals(
            "and its clock is the engine's own seconds, not one the plan re-derived",
            secondsFor(expectedM, paceKn),
            plan.durationSec,
            1e-6
        )

        val figure = routeTripFigure(plan, from = start, paceKn = paceKn, nowMs = plan.computedAtMs)
        assertEquals(
            "the trip figure is that plan's distance",
            Units.metresToNauticalMiles(expectedM),
            figure.distanceNm,
            1e-9
        )
        assertEquals("and its time at the pace in force", secondsFor(expectedM, paceKn), figure.etaSeconds, 1e-6)
    }

    /**
     * **The confirmation locks the foreign engine's plan, and the save is that plan written out** —
     * dated by the route's own generation instant, which is what names the track too (R40).
     */
    @Test
    fun aConfirmedForeignRouteIsTheTrackTheMapSaves() = runTest {
        val viewModel = RouteViewModel(selectionOf(StraightLineEngine()))

        viewModel.beginDraft(start)
        viewModel.preview(aim)
        viewModel.confirm()

        val following = viewModel.state.value as RouteState.Following
        val plan = following.plan

        val expectedM = distanceM(start, aim)
        val paceKn = StraightLineEngine.FIXTURE_PACE_KN
        assertEquals(listOf(start, aim), plan.points)
        assertEquals(expectedM, plan.distanceM, 1e-6)
        assertEquals(secondsFor(expectedM, paceKn), plan.durationSec, 1e-6)

        val legs = plan.points.drop(1).mapIndexed { index, point ->
            TrackFromCourse.legBetween(
                from = plan.points[index],
                to = point,
                durationSec = plan.legTimesSec.getOrElse(index) { 0.0 }
            )
        }
        val track = TrackFromCourse.build(
            start = plan.points.first(),
            legs = legs,
            pinned = false,
            id = "engine-seam",
            createdAtMs = plan.computedAtMs
        )

        assertEquals("the saved figure is the plan's own distance", plan.distanceNm.toFloat(), track.distanceNm, 1e-4f)
        assertEquals(
            "and its own duration, which is the foreign engine's seconds",
            secondsFor(expectedM, paceKn).toLong(),
            track.navigatingDurationSec
        )
        assertEquals(plan.points.size, track.trackPoints.size)
        assertTrue("a saved route carries the flag", track.route)
        assertEquals(
            "and the track is dated the route's own generation instant, not the save's",
            plan.computedAtMs,
            track.startTimeMs
        )
    }

    /**
     * **The refresh asks the origin's own entry point**, from where the boat now is to the destination
     * the engine **holds** — the feature never re-states the destination, which is the whole point of a
     * session — and the replaced route joins the ladder rather than being dropped (R14, R25).
     */
    @Test
    fun aRefreshAsksTheEnginesOriginEntryPointAndTheReplacedRouteJoinsTheLadder() = runTest {
        val engine = StraightLineEngine()
        val viewModel = RouteViewModel(selectionOf(engine))

        viewModel.beginDraft(start)
        viewModel.preview(aim)
        viewModel.confirm()
        val locked = (viewModel.state.value as RouteState.Following).plan

        viewModel.refresh(boatLater)

        val following = viewModel.state.value as RouteState.Following
        assertEquals("the second search goes from the boat alone", listOf(boatLater), engine.toldOrigins.drop(1))
        assertEquals("and the destination was never re-stated", listOf(aim), engine.askedDestinations)
        assertEquals(listOf(boatLater, locked.destination), following.plan.points)
        assertEquals(distanceM(boatLater, locked.destination), following.plan.distanceM, 1e-6)
        assertEquals(
            "the route it replaced is the ladder, oldest first, and the new one is the front",
            listOf(locked, following.plan),
            following.routes
        )
        assertEquals("an answered refresh leaves nothing running", RouteRefresh.IDLE, following.refresh)
    }

    /**
     * **The readiness retry: a preview refused for an unprepared engine restarts itself once the
     * engine says it is ready.** The engine is not ready on the preparation the view model runs at
     * construction and ready on the one the refused preview asks for, so the same aim only becomes a
     * route through the retry.
     */
    @Test
    fun aPreviewRefusedWhileTheEngineIsUnpreparedRestartsItselfOnceTheEngineIsReady() = runTest {
        val engine = StraightLineEngine(
            prepareStates = listOf(RouteEngineState.NotReady, RouteEngineState.Ready)
        )
        val viewModel = RouteViewModel(selectionOf(engine))

        assertEquals(
            "the gate is still shut after the engine's own first answer",
            RouteEngineState.NotReady,
            viewModel.engineState.value
        )
        viewModel.beginDraft(start)
        viewModel.preview(aim)

        assertEquals("the retry asked the engine again", 2, engine.prepareCalls)
        assertEquals("and the preview it restarted was answered once", 1, engine.askedDestinations.size)
        val choosing = viewModel.state.value as RouteState.Choosing
        assertEquals("the plan is that answer's own", listOf(start, aim), choosing.plan?.points)
        assertTrue("and the phase says it has asked", choosing.asked)
    }

    /**
     * **A refusal that keeps being refused does not loop.** The retry is one preparation per refused
     * preview: an engine that never becomes ready is asked about nothing, and the recursion stops
     * because the preparation returned.
     */
    @Test
    fun aPreviewRefusedByAnEngineThatNeverBecomesReadyIsNotAskedTwiceForOneAim() = runTest {
        val engine = StraightLineEngine(prepareStates = listOf(RouteEngineState.NotReady))
        val viewModel = RouteViewModel(selectionOf(engine))

        viewModel.beginDraft(start)
        viewModel.preview(aim)
        viewModel.preview(boatLater)

        assertTrue("an unready engine is never asked for a route", engine.askedDestinations.isEmpty())
        assertEquals(
            "one preparation on construction and one per refused preview, and no recursion behind them",
            3,
            engine.prepareCalls
        )
        assertNull("and the phase holds no plan", (viewModel.state.value as RouteState.Choosing).plan)
    }

    /**
     * **A new ask aborts the one in flight and starts** (R4), and the standing plan survives that
     * abort.
     *
     * The engine is gated on the second aim alone, so the first answer has landed a plan and the second
     * search is genuinely **in flight** while the assertion reads — the one state a synchronous engine
     * can never show. What the reads pin is exactly the behaviour: the superseded ask never queues (the
     * engine is asked for the newest aim and never for the one in between), the abort does **not** clear
     * the line already drawn, and the newest answer is what ends up followed.
     */
    @Test
    fun aNewAskAbortsTheOneInFlightAndLeavesTheStandingPlanDrawn() = runTest {
        val gate = CompletableDeferred<Unit>()
        // The **second** aim is the one held, so the first answer has landed a plan by the time the
        // abort happens — which is the whole point of reading the standing line through an abort.
        val engine = StraightLineEngine(gateAfter = boatLater, gate = gate)
        val viewModel = RouteViewModel(selectionOf(engine))

        viewModel.beginDraft(start)
        viewModel.preview(aim)
        val firstPlan = (viewModel.state.value as RouteState.Choosing).plan

        viewModel.preview(boatLater)

        val inFlight = viewModel.state.value as RouteState.Choosing
        assertEquals(
            "the second aim's search is genuinely in flight",
            listOf(aim, boatLater),
            engine.askedDestinations
        )
        assertEquals(
            "and the abort has not touched the line already drawn",
            firstPlan,
            inFlight.plan
        )
        assertTrue("while the phase says a search runs", inFlight.searching)

        viewModel.preview(aimLater)

        assertEquals(
            "the newest aim cancelled the one in flight and started at once",
            listOf(aim, boatLater, aimLater),
            engine.askedDestinations
        )
        val landed = viewModel.state.value as RouteState.Choosing
        assertEquals("the newest aim's answer is the plan", listOf(start, aimLater), landed.plan?.points)
        assertFalse("and the slot being empty is what ends the searching flag", landed.searching)

        // The abandoned call cannot land afterwards: it was cancelled, so its held answer is dropped.
        gate.complete(Unit)
        assertEquals(
            "the superseded answer never reaches the phase",
            listOf(start, aimLater),
            (viewModel.state.value as RouteState.Choosing).plan?.points
        )
    }

    /**
     * **The origin is judged once, at arming, and never on a refresh** (R7).
     *
     * The engine counts every point it was asked about, so the count itself is the reading: one call for
     * the origin on the arming frame, one per destination aim, and **none** for the origin again when
     * the following mode refreshes — the boat's own position is not a target being placed.
     */
    @Test
    fun theOriginIsJudgedOnceAtArmingAndNeverOnARefresh() = runTest {
        val engine = StraightLineEngine()
        val viewModel = RouteViewModel(selectionOf(engine))

        viewModel.beginDraft(start)
        viewModel.preview(aim)
        viewModel.confirm()
        viewModel.refresh(boatLater)

        assertEquals(
            "the origin was judged exactly once, on the arming frame",
            1,
            engine.validatedPoints.count { it == start }
        )
        assertEquals(
            "and the aim was judged as it moved, which is the destination's own rule",
            1,
            engine.validatedPoints.count { it == aim }
        )
        assertFalse(
            "the refresh judged the new origin, which R7 forbids",
            engine.validatedPoints.contains(boatLater)
        )
    }

    /**
     * **A refused aim is the panel's sentence and nothing else** (R6): no route is asked for it, and the
     * reason rides in the closed set the surface resolves.
     */
    @Test
    fun aRefusedAimIsCarriedAsAReasonAndAsksForNoRoute() = runTest {
        val engine = StraightLineEngine(refuse = aim)
        val viewModel = RouteViewModel(selectionOf(engine))

        viewModel.beginDraft(start)
        viewModel.preview(aim)

        val choosing = viewModel.state.value as RouteState.Choosing
        assertEquals(RouteRefusalReason.OFF_WATER, choosing.refusal)
        assertNull("no plan exists for a refused aim, which is what hides the outcomes", choosing.plan)
        assertTrue("and the engine was asked for no route at all", engine.askedDestinations.isEmpty())
    }

    /**
     * **A refused aim's panel state stands alone** (R6).
     *
     * The refusal is read after an aim that *did* resolve, so the phase is holding a plan when the
     * refused aim arrives. That plan must go: the panel shows the sentence from a state with no plan,
     * which is the only thing that hides the four outcomes — otherwise the refused sentence would sit
     * above a superseded route's details and its own Route / Save / Cancel buttons.
     */
    @Test
    fun aRefusedAimDropsTheStandingPlanSoTheOutcomesAreHidden() = runTest {
        val engine = StraightLineEngine(refuse = boatLater)
        val viewModel = RouteViewModel(selectionOf(engine))

        viewModel.beginDraft(start)
        viewModel.preview(aim)
        assertNotNull(
            "the first aim resolved a plan, which is what the refusal must not leave standing",
            (viewModel.state.value as RouteState.Choosing).plan
        )

        viewModel.preview(boatLater)

        val refused = viewModel.state.value as RouteState.Choosing
        assertEquals(RouteRefusalReason.OFF_WATER, refused.refusal)
        assertNull("the refused aim's state holds no plan, so the outcomes are hidden", refused.plan)
    }

    /**
     * **The selection reaches the view model at arm time and not before** (D5).
     *
     * While the mode is idle the gate follows whichever engine is selected; the Idle → Choosing edge
     * then captures that one for the session, so a selection changed mid-mode cannot move the gate, and
     * the return to Idle releases it — the gate follows the newly selected engine again.
     */
    @Test
    fun theSelectionReachesTheViewModelAtArmTimeAndNotBefore() = runTest {
        val first = StraightLineEngine()
        val second = StraightLineEngine(prepareStates = listOf(RouteEngineState.NotReady))
        val selection = MutableStateFlow<RouteEngine>(first)
        val viewModel = RouteViewModel(selection)

        assertTrue("while idle the gate reads the selected engine", viewModel.engineState.value.ready)

        viewModel.beginDraft(start)
        selection.value = second
        assertTrue(
            "the session engine is the one the mode was armed with, not the new selection",
            viewModel.engineState.value.ready
        )
        viewModel.preview(aim)
        assertEquals(
            "and the armed engine drew the preview",
            listOf(start, aim),
            (viewModel.state.value as RouteState.Choosing).plan?.points
        )

        viewModel.end()
        assertFalse(
            "released on Idle, the gate follows the newly selected engine again",
            viewModel.engineState.value.ready
        )
    }

    /**
     * **A selection changed while a route runs leaves the standing line untouched** (D5).
     *
     * The refresh goes through the engine the route was armed with — the newly selected one is never
     * told a thing — so the line the armed engine drew keeps its own pace and the ladder it grew.
     */
    @Test
    fun aSelectionChangedWhileARouteRunsLeavesTheStandingLineUntouched() = runTest {
        val first = StraightLineEngine(paceKn = 9.0)
        val second = StraightLineEngine(paceKn = 18.0)
        val selection = MutableStateFlow<RouteEngine>(first)
        val viewModel = RouteViewModel(selection)

        viewModel.beginDraft(start)
        viewModel.preview(aim)
        viewModel.confirm()
        val locked = (viewModel.state.value as RouteState.Following).plan

        selection.value = second
        viewModel.refresh(boatLater)

        val following = viewModel.state.value as RouteState.Following
        assertEquals("the standing destination holds", locked.destination, following.plan.destination)
        assertEquals(
            "the refresh went through the armed engine, at the armed engine's pace",
            secondsFor(distanceM(boatLater, locked.destination), 9.0),
            following.plan.durationSec,
            1e-6
        )
        assertEquals(
            "the newly selected engine was never told a thing",
            emptyList<RoutePoint>(),
            second.toldOrigins
        )
    }

    /** **The seam runs through both shipped engines**: each draws its own straight line end to end. */
    @Test
    fun theSeamRunsThroughBothShippedEngines() = runTest {
        for (engine in listOf(RouteDummyEngine(), RouteAvoidEngine(paceKn = { 28.0 }, worldProvider = { EmptyAvoidWorld() }))) {
            val viewModel = RouteViewModel(selectionOf(engine))
            viewModel.beginDraft(start)
            viewModel.preview(aim)
            val plan = (viewModel.state.value as RouteState.Choosing).plan
                ?: error("a shipped engine answers a route, so the phase must hold a plan")
            assertEquals("the shipped engine drew its straight line", listOf(start, aim), plan.points)
            assertEquals("and its length is the great-circle distance", distanceM(start, aim), plan.distanceM, 1e-6)
        }
    }
}

/** The selection form the view model now takes: one engine behind a [StateFlow]. */
private fun selectionOf(engine: RouteEngine): StateFlow<RouteEngine> = MutableStateFlow(engine)

/** An empty, ready world — water everywhere and no land, so the avoid engine draws its straight line. */
private class EmptyAvoidWorld : AvoidWorld {
    override val coastlineReady: Boolean get() = true
    override val regionBounds: BBox? get() = null
    override fun segmentsIn(box: BBox): List<AvoidEdge> = emptyList()
    override fun openCoastIn(box: BBox): List<List<LatLng>> = emptyList()
    override fun isWater(latitude: Double, longitude: Double): Boolean = true
    override fun distanceToCoastM(latitude: Double, longitude: Double): Double = Double.MAX_VALUE
    override suspend fun load(): RouteEngineState = RouteEngineState.Ready
}

/**
 * **A second engine: the same contract, and none of the shipped one's machinery.**
 *
 * It holds the two ends as it is told them — which is what makes it a *session* rather than a function
 * — walks the straight line between them and prices it at a constant of its own. Nothing about it is
 * meant to be a good route; it is meant to be a *foreign* one, which is the only way to read whether
 * the seam is engine-agnostic.
 *
 * It also answers readiness by script, because the readiness retry is a step of the seam and cannot be
 * read from an engine that is always ready: [prepareStates] names what each successive `prepare`
 * reaches, the last one repeating. [gateAfter] holds one ask **in flight** — the one state a
 * synchronous answer can never show — and [refuse] names the one point it judges unusable.
 */
private class StraightLineEngine(
    private val prepareStates: List<RouteEngineState> = listOf(RouteEngineState.Ready),
    /** Consulted inside the destination entry point, before the answer, so a search can be held. */
    private val gateAfter: RoutePoint? = null,
    private val gate: CompletableDeferred<Unit>? = null,
    /** The one point this engine judges unusable, or null for an engine that judges nothing. */
    private val refuse: RoutePoint? = null,
    /** The pace this instance prices at, so two instances can be told apart by their answer. */
    private val paceKn: Double = FIXTURE_PACE_KN
) : RouteEngine {

    private val _state = MutableStateFlow<RouteEngineState>(RouteEngineState.NotReady)

    override val state: StateFlow<RouteEngineState> = _state.asStateFlow()

    /** How many times the feature asked this engine to prepare. */
    var prepareCalls: Int = 0
        private set

    /** Every destination the feature asked about, in order. */
    val askedDestinations = ArrayList<RoutePoint>()

    /** Every origin the feature told this engine, in order, the arming call first. */
    val toldOrigins = ArrayList<RoutePoint>()

    /** Every point this engine was asked to judge, in order — two readers of one mechanism. */
    val validatedPoints = ArrayList<RoutePoint>()

    /** The ends it holds, exactly as the contract says an engine does. */
    private var heldOrigin: RoutePoint? = null
    private var heldDestination: RoutePoint? = null

    override suspend fun prepare(): RouteEngineState {
        val next = prepareStates[minOf(prepareCalls, prepareStates.lastIndex)]
        prepareCalls++
        _state.value = next
        return next
    }

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
        if (newPosition == gateAfter) gate?.await()
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
        val seconds = distanceM / Units.knotsToMps(paceKn)
        return RouteResult.Success(
            points = listOf(from, to),
            legTimesSec = listOf(seconds),
            distanceM = distanceM,
            durationSec = seconds,
            destinationMoved = false
        )
    }

    companion object {
        /** The pace this fixture prices at, restated in the test's own derivation. */
        const val FIXTURE_PACE_KN = 9.0
    }
}
