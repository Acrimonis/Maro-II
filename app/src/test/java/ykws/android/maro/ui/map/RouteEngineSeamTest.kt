package ykws.android.maro.ui.map

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.data.model.RouteResult
import ykws.android.maro.data.track.TrackFromCourse
import ykws.android.maro.spatial.RouteEngine
import ykws.android.maro.spatial.RouteEngineState
import ykws.android.maro.spatial.RouteUnavailableReason
import ykws.android.maro.spatial.SpatialOperations
import ykws.android.maro.spatial.Units

/**
 * **The seam, exercised through the feature by an engine that is not the mesh.**
 *
 * A contract with one implementation is a contract nobody has read: every reader of it compiles
 * against the incumbent by accident, and the first thing a second engine would have to borrow is
 * whatever the incumbent's answer happens to carry. This test is the counterweight — a **fake**
 * [`RouteEngine`] that knows no mesh, no bake and no chain is handed to a real [`RouteViewModel`],
 * and every reading below is taken from what the *feature* made of its answer.
 *
 * That is what makes it a reading rather than a restatement. The view model is driven the way the map
 * drives it — `beginDraft`, `preview`, `confirm`, `recompute`, and a preview that arrives before the
 * engine is ready — and the assertions are re-derived in the test (the haversine it computes itself,
 * the pace the view model holds) rather than read back off the object under test, so a path that
 * quietly re-derived a number could not satisfy them. The gate is read as the value the toggle reads
 * (`engineState.value.ready`), for an engine that is ready, one that is not, and one that says it
 * cannot answer.
 *
 * It also walks the two readers the feature actually has: the plan ([`RoutePlan`]) and the trip
 * figure, plus the save as the map screen writes it. Nothing here is a defect it could be excused
 * for missing: it drives the feature, and the feature either carries the foreign engine's answer or
 * it does not.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RouteEngineSeamTest {

    private val start = RoutePoint(43.5000, 7.0000)
    private val aim = RoutePoint(43.5200, 7.0100)

    /** A second position, for the recompute: the boat has moved while the route was followed. */
    private val boatLater = RoutePoint(43.5050, 7.0030)

    /**
     * A third position, for the drag: the coalescing is read on **which** aims a search was asked for, so
     * the skipped one and the newest one have to be two different places.
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
     * whichever engine the app runs on and of nothing else. So the three states are read through the
     * view model that holds them, not off the sealed type.
     */
    @Test
    fun theGateIsTheEnginesOwnReadinessAndTheViewModelHoldsIt() {
        val ready = RouteViewModel(StraightLineEngine())
        assertTrue("a ready engine opens the mode", ready.engineState.value.ready)
        assertEquals("and the state is the engine's own", RouteEngineState.Ready, ready.engineState.value)

        val unprepared = RouteViewModel(StraightLineEngine(prepareStates = listOf(RouteEngineState.NotReady)))
        assertFalse("one that has not been prepared does not", unprepared.engineState.value.ready)
        assertEquals("and it says so in the state, not in prose", RouteEngineState.NotReady, unprepared.engineState.value)

        val unanswerable = RouteViewModel(
            StraightLineEngine(
                prepareStates = listOf(
                    RouteEngineState.Unavailable(RouteUnavailableReason.REGION_NOT_BAKED)
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
     * **A preview from a foreign engine reaches the plan and the trip figure.**
     *
     * The engine is asked once, from the draft's own frozen start and at the pace the view model
     * holds; the plan it answers with is the engine's own arithmetic, checked against the numbers
     * this test derives itself; and the trip figure is read off that plan.
     */
    @Test
    fun aPreviewSearchesThroughTheForeignEngineAndItsAnswerReachesThePlanAndTheTripFigure() {
        val asked = ArrayList<Triple<RoutePoint, RoutePoint, Double>>()
        val engine = StraightLineEngine(
            onRoute = { from, to, cruiseKn -> asked += Triple(from, to, cruiseKn) }
        )
        val viewModel = RouteViewModel(engine)

        viewModel.beginDraft(start)
        val paceKn = viewModel.paceKn.value
        viewModel.preview(aim)

        val draft = viewModel.state.value as RouteState.Draft
        val plan = draft.plan ?: error("the foreign engine answered, so the draft must hold a plan")
        assertEquals(
            "the engine was asked once, from the frozen start, at the pace in force",
            listOf(Triple(start, aim, paceKn)),
            asked
        )
        assertEquals("the line is the foreign engine's own, and no mesh chain exists", listOf(start, aim), plan.points)
        assertEquals(start, plan.start)

        val expectedM = distanceM(start, aim)
        assertEquals("the plan's length is the engine's own answer", expectedM, plan.distanceM, 1e-6)
        assertEquals(
            "and its clock is the engine's own seconds, not one the plan re-derived",
            secondsFor(expectedM, paceKn),
            plan.durationSec,
            1e-6
        )

        val figure = routeTripFigure(plan, from = start, paceKn = paceKn, nowMs = plan.computedAtMs, stale = false)
        assertEquals(
            "the trip figure is that plan's distance",
            Units.metresToNauticalMiles(expectedM),
            figure.distanceNm,
            1e-9
        )
        assertEquals("and its time at the pace in force", secondsFor(expectedM, paceKn), figure.etaSeconds, 1e-6)
    }

    /**
     * **The confirmation locks the foreign engine's plan, and the save is that plan written out.**
     *
     * The save is the map screen's own two lines — the plan's polyline and its own per-leg times
     * through the track repository's builder — so what is asserted is that a track saved from a
     * foreign engine's route carries that route's figures.
     */
    @Test
    fun aConfirmedForeignRouteIsTheTrackTheMapSaves() {
        val viewModel = RouteViewModel(StraightLineEngine())

        viewModel.beginDraft(start)
        val paceKn = viewModel.paceKn.value
        viewModel.preview(aim)
        viewModel.confirm()

        val confirmed = viewModel.state.value as RouteState.Confirmed
        assertFalse("a route the engine answered is not stale", confirmed.stale)
        val plan = confirmed.plan

        val expectedM = distanceM(start, aim)
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
        assertTrue("a saved route is an ordinary planned course", track.plannedCourse)
    }

    /**
     * **The recompute asks the engine again, from the boat to the plan's own destination**, and the
     * plan it lands is the engine's own second answer.
     */
    @Test
    fun aRecomputeAsksTheForeignEngineAgainFromTheBoatAndTheNewPlanIsItsOwn() {
        val asked = ArrayList<Triple<RoutePoint, RoutePoint, Double>>()
        val engine = StraightLineEngine(
            onRoute = { from, to, cruiseKn -> asked += Triple(from, to, cruiseKn) }
        )
        val viewModel = RouteViewModel(engine)

        viewModel.beginDraft(start)
        val paceKn = viewModel.paceKn.value
        viewModel.preview(aim)
        viewModel.confirm()
        val locked = (viewModel.state.value as RouteState.Confirmed).plan

        viewModel.recompute(boatLater)

        val followed = viewModel.state.value as RouteState.Confirmed
        assertEquals(
            "the second search goes from the boat to the route's own destination",
            listOf(Triple(start, aim, paceKn), Triple(boatLater, locked.destination, paceKn)),
            asked
        )
        assertEquals(listOf(boatLater, locked.destination), followed.plan.points)
        assertEquals(
            distanceM(boatLater, locked.destination),
            followed.plan.distanceM,
            1e-6
        )
        assertFalse("an answered recompute is not stale", followed.stale)
    }

    /**
     * **The readiness retry: a preview refused for an unprepared engine restarts itself once the
     * engine says it is ready.** The engine is not ready on the preparation the view model runs at
     * construction and ready on the one the refused preview asks for, so the same aim only becomes a
     * route through the retry.
     */
    @Test
    fun aPreviewRefusedWhileTheEngineIsUnpreparedRestartsItselfOnceTheEngineIsReady() {
        val engine = StraightLineEngine(
            prepareStates = listOf(RouteEngineState.NotReady, RouteEngineState.Ready)
        )
        val viewModel = RouteViewModel(engine)

        assertEquals(
            "the gate is still shut after the engine's own first answer",
            RouteEngineState.NotReady,
            viewModel.engineState.value
        )
        viewModel.beginDraft(start)
        viewModel.preview(aim)

        assertEquals("the retry asked the engine again", 2, engine.prepareCalls)
        assertEquals("and the preview it restarted was answered once", 1, engine.askedRoutes.size)
        val draft = viewModel.state.value as RouteState.Draft
        assertEquals("the plan is that answer's own", listOf(start, aim), draft.plan?.points)
        assertTrue("and the draft says it has asked", draft.asked)
    }

    /**
     * **A refusal that keeps being refused does not loop.** The retry is one preparation per refused
     * preview: an engine that never becomes ready is asked about nothing, and the recursion stops
     * because the preparation returned.
     */
    @Test
    fun aPreviewRefusedByAnEngineThatNeverBecomesReadyIsNotAskedTwiceForOneAim() {
        val engine = StraightLineEngine(prepareStates = listOf(RouteEngineState.NotReady))
        val viewModel = RouteViewModel(engine)

        viewModel.beginDraft(start)
        viewModel.preview(aim)
        viewModel.preview(boatLater)

        assertTrue("an unready engine is never asked for a route", engine.askedRoutes.isEmpty())
        assertEquals(
            "one preparation on construction and one per refused preview, and no recursion behind them",
            3,
            engine.prepareCalls
        )
        assertNull("and the draft holds no plan", (viewModel.state.value as RouteState.Draft).plan)
    }

    /**
     * **A drag is coalesced: the search in flight holds the newest aim, and the aims it skips are never
     * asked for.**
     *
     * The engine is gated on the first trip, so the search is genuinely *in flight* while two more aims
     * arrive — the one state the behaviour is about, and one a synchronous engine can never show. The
     * reads are then the two the behaviour is made of: **the engine is asked once** while a search runs,
     * where a predecessor cancelled per frame would have asked three times, and when the gate opens it
     * is asked for the **last** aim and never for the one in between, with the draft's `searching` false
     * only once nothing is left in the slot. That is the difference the device complaint was about — a
     * flung map costing one search per *completed* search rather than one per frame.
     */
    @Test
    fun aDragInFlightHoldsTheNewestAimAndNeverAsksForTheOnesItSkips() {
        val gate = CompletableDeferred<Unit>()
        val engine = StraightLineEngine(beforeAnswer = { gate.await() })
        val viewModel = RouteViewModel(engine)

        viewModel.beginDraft(start)
        viewModel.preview(aim)
        assertEquals("the first aim is in flight", 1, engine.askedRoutes.size)
        assertTrue(
            "and the draft says so, which is what holds the refusal back",
            (viewModel.state.value as RouteState.Draft).searching
        )

        viewModel.preview(boatLater)
        viewModel.preview(aimLater)
        assertEquals(
            "two aims arrived while one search ran, and neither started a second search",
            1,
            engine.askedRoutes.size
        )

        gate.complete(Unit)

        assertEquals(
            "the newest aim is the next one asked, and the skipped one never is",
            listOf(aim, aimLater),
            engine.askedRoutes.map { it.second }
        )
        val landed = viewModel.state.value as RouteState.Draft
        assertEquals("the plan is the newest aim's own", listOf(start, aimLater), landed.plan?.points)
        assertFalse("and the slot being empty is what ends the searching flag", landed.searching)
    }
}

/**
 * **A second engine: the same contract, and none of the incumbent's machinery.**
 *
 * It walks the straight line between the two points and prices it at the pace it was handed — no
 * mesh, no chain, no fillet, no query of any kind. Nothing about it is meant to be a good route; it
 * is meant to be a *foreign* one, which is the only way to read whether the seam is engine-agnostic.
 *
 * It also answers readiness by script, because the readiness retry is a step of the seam and cannot be
 * read from an engine that is always ready: [prepareStates] names what each successive `prepare`
 * reaches, the last one repeating.
 */
private class StraightLineEngine(
    /** Called with the three answers the entry point takes, so a test can read what was asked. */
    private val onRoute: (RoutePoint, RoutePoint, Double) -> Unit = { _, _, _ -> },
    private val prepareStates: List<RouteEngineState> = listOf(RouteEngineState.Ready),
    /**
     * Consulted inside `route`, before the answer is computed, so a test can hold a search **in flight**
     * — the one state the coalescing is about, and the one a synchronous answer can never show.
     */
    private val beforeAnswer: suspend (RoutePoint) -> Unit = { }
) : RouteEngine {

    private val _state = MutableStateFlow<RouteEngineState>(RouteEngineState.NotReady)

    override val state: StateFlow<RouteEngineState> = _state.asStateFlow()

    /** How many times the feature asked this engine to prepare. */
    var prepareCalls: Int = 0
        private set

    /** Every route the feature asked for, in order. */
    val askedRoutes = ArrayList<Triple<RoutePoint, RoutePoint, Double>>()

    override suspend fun prepare(): RouteEngineState {
        val next = prepareStates[minOf(prepareCalls, prepareStates.lastIndex)]
        prepareCalls++
        _state.value = next
        return next
    }

    override suspend fun route(
        start: RoutePoint,
        aim: RoutePoint,
        cruiseSpeedKn: Double
    ): RouteResult {
        askedRoutes += Triple(start, aim, cruiseSpeedKn)
        onRoute(start, aim, cruiseSpeedKn)
        beforeAnswer(aim)
        val distanceM = SpatialOperations.haversine(
            LatLng(start.latitude, start.longitude),
            LatLng(aim.latitude, aim.longitude)
        )
        val seconds = if (cruiseSpeedKn > 0.0) distanceM / Units.knotsToMps(cruiseSpeedKn) else 0.0
        return RouteResult.Success(
            points = listOf(start, aim),
            legTimesSec = listOf(seconds),
            distanceM = distanceM,
            durationSec = seconds,
            destinationMoved = false
        )
    }
}
