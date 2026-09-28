package ykws.android.maro.ui.map

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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ykws.android.maro.data.model.DepthSample
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.RouteOffer
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.data.model.RouteResult
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.data.track.TrackFromCourse
import ykws.android.maro.spatial.RouteAvoidEngine
import ykws.android.maro.spatial.RouteDummyEngine
import ykws.android.maro.spatial.RouteEngine
import ykws.android.maro.spatial.RouteEngineState
import ykws.android.maro.spatial.RouteRefusalReason
import ykws.android.maro.spatial.RouteProgress
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
 * Two properties of the **session** shape are what it mostly exists for: an engine told one end
 * **holds the other**, so the feature never re-states a destination; and the anchor is **judged as the
 * acquisition opens** (R7), rather than assumed. Since 2026-09-28 the **arming is the trigger** (R49,
 * R50), so every acquisition here is opened by `arm` on the drawer's standing pair and the answer lands
 * without a second press — the retry for an unprepared engine rides on that same arming.
 *
 * Every acquisition here is opened with a **plain pair of ends**, no lead and no fallback: the lead's own
 * arithmetic is [`RoutePlanTest`]'s, and what this file reads is the feature's behaviour behind it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RouteEngineSeamTest {

    private val start = RoutePoint(43.5000, 7.0000)
    private val aim = RoutePoint(43.5200, 7.0100)

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
     * **Arms the acquisition on two plain ends** — the one door the feature has since 2026-09-28
     * (R49, R50): no aim is placed, the pair is read here, and the answer lands computing.
     */
    private suspend fun armOn(
        viewModel: RouteViewModel,
        from: RoutePoint,
        to: RoutePoint
    ) = viewModel.arm(RouteEnds(start = from, fallbackStart = null, destination = to))

    /**
     * Waits for the acquisition's plan, for the engines that answer off the main dispatcher.
     *
     * The shipped avoid engine runs its corridor on `Dispatchers.Default`, so its answer arrives after
     * the arming has returned; a synchronous read of the state would race it. The wait is bounded and
     * then gives up, so a missing plan is still the test's own failure rather than a hang.
     */
    private fun awaitPlan(viewModel: RouteViewModel): RoutePlan? {
        repeat(200) {
            val plan = (viewModel.state.value as? RouteState.Choosing)?.plan
            if (plan != null) return plan
            Thread.sleep(10)
        }
        return null
    }

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
     * **An arming from a foreign engine reaches the plan and the trip figure**, and it reaches them from
     * the pair the drawer stood on — the engine holds the anchor the feature told it as the acquisition
     * opened, so the feature never hands a start to the destination's entry point.
     */
    @Test
    fun theArmingSearchesThroughTheForeignEngineAndItsAnswerReachesThePlanAndTheTripFigure() = runTest {
        val engine = StraightLineEngine()
        val viewModel = RouteViewModel(selectionOf(engine))

        armOn(viewModel, start, aim)

        val choosing = viewModel.state.value as RouteState.Choosing
        val plan = choosing.plan ?: error("the foreign engine answered, so the phase must hold a plan")
        assertEquals(
            "the anchor reached the engine as part of the acquisition's own ask",
            listOf(start),
            engine.toldOrigins
        )
        assertEquals(
            "and the destination's entry point was asked for the standing end alone",
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
     * **`Select route` locks the foreign engine's plan, and the save is that plan written out** — dated
     * by the route's own generation instant, which is what names the track too (R40).
     */
    @Test
    fun aSelectedForeignRouteIsTheTrackTheMapSaves() = runTest {
        val viewModel = RouteViewModel(selectionOf(StraightLineEngine()))

        armOn(viewModel, start, aim)
        viewModel.selectRoute()

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
     * **The readiness retry: an arming refused for an unprepared engine restarts itself once the engine
     * says it is ready.** The engine is not ready on the preparation the view model runs at construction
     * and ready on the one the refused arming requests, so the same pair only becomes a route through the
     * retry.
     */
    @Test
    fun anArmingRefusedWhileTheEngineIsUnpreparedRestartsItselfOnceTheEngineIsReady() = runTest {
        val engine = StraightLineEngine(
            prepareStates = listOf(RouteEngineState.NotReady, RouteEngineState.Ready)
        )
        val viewModel = RouteViewModel(selectionOf(engine))

        assertEquals(
            "the gate is still shut after the engine's own first answer",
            RouteEngineState.NotReady,
            viewModel.engineState.value
        )
        armOn(viewModel, start, aim)

        assertEquals("the retry asked the engine again", 2, engine.prepareCalls)
        assertEquals("and the ask it restarted was answered once", 1, engine.askedDestinations.size)
        val choosing = viewModel.state.value as RouteState.Choosing
        assertEquals("the plan is that answer's own", listOf(start, aim), choosing.plan?.points)
        assertTrue("and the phase says it has asked", choosing.asked)
    }

    /**
     * **A refusal that keeps being refused does not loop.** The retry is one preparation per refused
     * arming: an engine that never becomes ready is asked about nothing, and the recursion stops because
     * the preparation returned — a **second arming while the mode is already on** is ignored, so the one
     * acquisition cannot be re-driven either.
     */
    @Test
    fun anArmingRefusedByAnEngineThatNeverBecomesReadyIsNotAskedTwice() = runTest {
        val engine = StraightLineEngine(prepareStates = listOf(RouteEngineState.NotReady))
        val viewModel = RouteViewModel(selectionOf(engine))

        armOn(viewModel, start, aim)
        armOn(viewModel, start, aim)

        assertTrue("an unready engine is never asked for a route", engine.askedDestinations.isEmpty())
        assertEquals(
            "one preparation on construction and one for the refused arming, and no recursion behind them",
            2,
            engine.prepareCalls
        )
        assertNull("and the phase holds no plan", (viewModel.state.value as RouteState.Choosing).plan)
    }

    /**
     * **The anchor is judged as the acquisition opens** (R7), and the destination as it is asked for.
     *
     * The engine counts every point it was asked about, so the counts themselves are the reading: one
     * call for the acquisition's own anchor and one for the standing destination. Neither is assumed.
     */
    @Test
    fun theAnchorIsJudgedAsTheAcquisitionOpensAndTheDestinationAsItIsAskedFor() = runTest {
        val engine = StraightLineEngine()
        val viewModel = RouteViewModel(selectionOf(engine))

        armOn(viewModel, start, aim)

        assertEquals(
            "the acquisition's own anchor was judged once",
            1,
            engine.validatedPoints.count { it == start }
        )
        assertEquals(
            "and the standing destination once, which is the destination's own rule",
            1,
            engine.validatedPoints.count { it == aim }
        )
    }

    /**
     * **A refused destination is the panel's sentence and nothing else** (R6): no route is asked for it,
     * and the reason rides in the closed set the surface resolves.
     */
    @Test
    fun aRefusedDestinationIsCarriedAsAReasonAndAsksForNoRoute() = runTest {
        val engine = StraightLineEngine(refuse = aim)
        val viewModel = RouteViewModel(selectionOf(engine))

        armOn(viewModel, start, aim)

        val choosing = viewModel.state.value as RouteState.Choosing
        assertEquals(RouteRefusalReason.OFF_WATER, choosing.refusal)
        assertNull("no plan exists for a refused destination, which is what hides the outcomes", choosing.plan)
        assertTrue("and the engine was asked for no route at all", engine.askedDestinations.isEmpty())
    }

    /**
     * **No ask happens without a standing pair** (R49, R71): the ends are what the drawer holds, so an
     * arm with no start — the position before the first fix — is the mode being on and nothing being
     * asked, and an arm with no destination is the same.
     */
    @Test
    fun anArmingWithoutAStandingPairAsksForNoRoute() = runTest {
        val engine = StraightLineEngine()
        val viewModel = RouteViewModel(selectionOf(engine))

        viewModel.arm(RouteEnds(start = null, fallbackStart = null, destination = aim))
        assertTrue("no start, no ask", engine.askedDestinations.isEmpty())
        assertTrue("and no anchor is told either", engine.toldOrigins.isEmpty())
        assertNull(
            "while the phase is armed and holds no line",
            (viewModel.state.value as RouteState.Choosing).plan
        )

        viewModel.end()

        viewModel.arm(RouteEnds(start = start, fallbackStart = null, destination = null))
        assertTrue("no destination, no ask", engine.askedDestinations.isEmpty())
        assertTrue("and still no anchor told", engine.toldOrigins.isEmpty())
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

        viewModel.arm(RouteEnds(start = start, fallbackStart = null, destination = null))
        selection.value = second
        assertTrue(
            "the session engine is the one the mode was armed with, not the new selection",
            viewModel.engineState.value.ready
        )
        viewModel.end()
        assertFalse(
            "released on Idle, the gate follows the newly selected engine again",
            viewModel.engineState.value.ready
        )
    }

    /** **The seam runs through both shipped engines**: each draws its own straight line end to end. */
    @Test
    fun theSeamRunsThroughBothShippedEngines() = runTest {
        val avoid = RouteAvoidEngine(
            paceKn = { 28.0 },
            slowWaterBudgetPct = { 33 },
            worldProvider = { EmptyAvoidWorld() }
        )
        for (engine in listOf(RouteDummyEngine(), avoid)) {
            val viewModel = RouteViewModel(selectionOf(engine))
            viewModel.arm(RouteEnds(start = start, fallbackStart = null, destination = aim))
            val plan = awaitPlan(viewModel)
                ?: error("a shipped engine answers a route, so the phase must hold a plan")
            assertEquals("the shipped engine drew its straight line", listOf(start, aim), plan.points)
            assertEquals("and its length is the great-circle distance", distanceM(start, aim), plan.distanceM, 1e-6)
        }
    }
}

/** The selection form the view model takes: one engine behind a [StateFlow]. */
private fun selectionOf(engine: RouteEngine): StateFlow<RouteEngine> = MutableStateFlow(engine)

/** An empty, ready world — water everywhere and no land, so the avoid engine draws its straight line. */
private class EmptyAvoidWorld : AvoidWorld {
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
 * **A second engine: the same contract, and none of the shipped one's machinery.**
 *
 * It holds the two ends as it is told them — which is what makes it a *session* rather than a function
 * — walks the straight line between them and prices it at a constant of its own. Nothing about it is
 * meant to be a good route; it is meant to be a *foreign* one, which is the only way to read whether
 * the seam is engine-agnostic.
 *
 * It also answers readiness by script, because the readiness retry is a step of the seam and cannot be
 * read from an engine that is always ready: [prepareStates] names what each successive `prepare`
 * reaches, the last one repeating. [refuse] names the one point it judges unusable.
 *
 * Its stage is null throughout: it crosses no boundary of a pipeline it does not have, which is the
 * degenerate case the channel has to allow.
 */
class StraightLineEngine(
    private val prepareStates: List<RouteEngineState> = listOf(RouteEngineState.Ready),
    /** The one point this engine judges unusable, or null for an engine that judges nothing. */
    private val refuse: RoutePoint? = null,
    /** The pace this instance prices at, so two instances can be told apart by their answer. */
    private val paceKn: Double = FIXTURE_PACE_KN
) : RouteEngine {

    private val _state = MutableStateFlow<RouteEngineState>(RouteEngineState.NotReady)

    override val state: StateFlow<RouteEngineState> = _state.asStateFlow()

    /** Always null — see the class note on the degenerate case the channel allows. */
    private val _progress = MutableStateFlow<RouteProgress?>(null)

    override val progress: StateFlow<RouteProgress?> = _progress.asStateFlow()

    /** A foreign engine offers nothing, so the empty set is the whole stream. */
    override val offers: StateFlow<List<RouteOffer>> = MutableStateFlow<List<RouteOffer>>(emptyList()).asStateFlow()

    /** How many times the feature asked this engine to prepare. */
    var prepareCalls: Int = 0
        private set

    /** Every destination the feature asked about, in order. */
    val askedDestinations = ArrayList<RoutePoint>()

    /** Every origin the feature told this engine, in order, an acquisition's anchor each. */
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
