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
import ykws.android.maro.data.model.RouteOffer
import ykws.android.maro.data.model.RouteOfferSource
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.data.model.RouteResult
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.data.model.routeCandidateSavingSec
import ykws.android.maro.spatial.RouteAvoidEngine
import ykws.android.maro.spatial.RouteEngine
import ykws.android.maro.spatial.RouteEngineState
import ykws.android.maro.spatial.RouteRefusalReason
import ykws.android.maro.spatial.RouteProgress
import ykws.android.maro.spatial.RouteStage
import ykws.android.maro.spatial.SpatialOperations
import ykws.android.maro.spatial.Units
import ykws.android.maro.spatial.avoid.AvoidEdge
import ykws.android.maro.spatial.avoid.AvoidWorld

/**
 * **The acquisition, as the machine sees it** — the change of 2026-09-28, read through a real
 * [`RouteViewModel`] driven the way the drawer drives it.
 *
 * The readings are the brief's own: **arming is the trigger** and it computes at once, so no ask
 * happens without a standing pair; the anchor is read as the acquisition opens, with its live-fix
 * fallback; the candidate set opens at the settled line and **grows only as the engine's offers land**;
 * the selection **loops** and the line it stands on is what the saves write; and a written route greys
 * the save through one predicate.
 *
 * The two doors the screen owns rather than the machine — the exit dialog answering before the tracking
 * exit, and the save action's enabled state — are wired in `MapScreen`'s own callbacks, so what is
 * pinned here is the **state** those callbacks branch on.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RouteAcquisitionTest {

    private val start = RoutePoint(43.5000, 7.0000)
    private val aim = RoutePoint(43.5200, 7.0100)

    /** A second destination, for the candidate that saves time. */
    private val shortcut = RoutePoint(43.5150, 7.0080)

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

    /** Arms on two standing ends — the drawer's pair, read at the trigger (R49, R71). */
    private suspend fun armEnds(
        viewModel: RouteViewModel,
        from: RoutePoint,
        to: RoutePoint,
        fallbackStart: RoutePoint? = null
    ) = viewModel.arm(RouteEnds(start = from, fallbackStart = fallbackStart, destination = to))

    private fun anOffer(from: RoutePoint, to: RoutePoint, durationSec: Double): RouteOffer {
        val distance = SpatialOperations.haversine(
            LatLng(from.latitude, from.longitude),
            LatLng(to.latitude, to.longitude)
        )
        return RouteOffer(
            source = RouteOfferSource.SPEED_ZONES,
            points = listOf(from, to),
            legTimesSec = listOf(durationSec),
            legSpeedsMps = listOf(distance / durationSec),
            distanceM = distance,
            durationSec = durationSec,
            savingSec = 60.0
        )
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
     * **Arming is the trigger, and it computes at once** (R49, R50): the standing pair is read at that
     * instant, the anchor is told and the destination asked **without a second press**, and there is no
     * placement left for the map to hold.
     */
    @Test
    fun armingAsksAtOnceAndTellsTheAnchorAsPartOfTheSameAcquisition() = runTest {
        val engine = CountingEngine()
        val viewModel = RouteViewModel(MutableStateFlow<RouteEngine>(engine))

        armEnds(viewModel, start, aim)

        assertEquals("the anchor is told as part of the acquisition's own ask", listOf(start), engine.toldOrigins)
        assertEquals("and the standing destination is asked for once", listOf(aim), engine.askedDestinations)
        val choosing = viewModel.state.value as RouteState.Choosing
        assertEquals("so a line stands without a second press", listOf(start, aim), choosing.plan?.points)
        assertTrue("and the phase says it has asked", choosing.asked)
        assertEquals(
            "the acquisition's own anchor is the one the line starts from",
            start,
            choosing.plan?.start
        )
    }

    /**
     * **No ask happens without a standing pair** (R49): a mode armed before the first fix, or with the
     * destination still resolving to nothing, is the mode being on and the engine being asked nothing.
     * A second arming while the mode is already on is ignored, so one acquisition is one ask.
     */
    @Test
    fun noAskHappensWithoutAStandingPair() = runTest {
        val engine = CountingEngine()
        val viewModel = RouteViewModel(MutableStateFlow<RouteEngine>(engine))

        viewModel.arm(RouteEnds(start = null, fallbackStart = null, destination = aim))
        assertTrue("no start, no ask", engine.askedDestinations.isEmpty())
        assertTrue("and no anchor is told either", engine.toldOrigins.isEmpty())

        viewModel.end()

        viewModel.arm(RouteEnds(start = start, fallbackStart = null, destination = null))
        assertTrue("no destination, no ask", engine.askedDestinations.isEmpty())

        viewModel.end()

        armEnds(viewModel, start, aim)
        assertEquals("the first arming on a standing pair asks once", 1, engine.askedDestinations.size)

        armEnds(viewModel, start, shortcut)
        assertEquals("and a second arming while the mode is on is ignored", 1, engine.askedDestinations.size)
    }

    /**
     * **The anchor's lead is best-effort and never binding** (R3): a predicted point that is not water
     * falls back to the live fix and the acquisition proceeds — a boat bearing down on a headland is
     * precisely the case a fresh acquisition exists for.
     *
     * The engine refuses the prediction alone, so the fallback is the only way the anchor can be
     * accepted, and what is read is that the anchor **is** the live fix and that the judgement was asked
     * of it (R7).
     */
    @Test
    fun theAnchorFallsBackToTheLiveFixWhereThePredictionIsNotWater() = runTest {
        val engine = CountingEngine(refuse = predicted(start))
        val viewModel = RouteViewModel(MutableStateFlow<RouteEngine>(engine))

        viewModel.arm(
            RouteEnds(
                start = predicted(start),
                fallbackStart = start,
                destination = aim
            )
        )

        val choosing = viewModel.state.value as RouteState.Choosing
        assertEquals("the anchor is the live fix", start, choosing.start)
        assertNull("and the fallback was accepted, so the acquisition is not refused", choosing.originRefusal)
        assertEquals(
            "the judgement was asked of the prediction and then of the fallback, before the destination",
            listOf(predicted(start), start, aim),
            engine.validatedPoints
        )
        assertEquals(
            "and the line it draws starts at the fallback, not at the point off water",
            start,
            choosing.plan?.start
        )
    }

    /**
     * **The candidate set opens at the settled line and grows only as the offers land** (R54).
     *
     * The engine publishes its offers on its own lane, after the answer, so the rows the panel draws
     * stand only from the moment there is something to step through — which is exactly the set the map
     * paints and the selection walks.
     */
    @Test
    fun theCandidateSetOpensAtOneAndGrowsOnlyAsTheOffersLand() = runTest {
        val engine = CountingEngine()
        val viewModel = RouteViewModel(MutableStateFlow<RouteEngine>(engine))

        armEnds(viewModel, start, aim)
        val settled = (viewModel.state.value as RouteState.Choosing).plan ?: error("a plan stands")

        assertEquals("the set is the settled answer alone until an offer arrives", listOf(settled), viewModel.candidates.value)
        assertEquals("and the selection stands on it", settled, viewModel.selectedLine())

        engine.publishOffers(listOf(anOffer(start, shortcut, settled.durationSec - 60.0)))

        val lines = viewModel.candidates.value
        assertEquals("the candidate follows the settled line", 2, lines.size)
        assertEquals("and index 0 is still that settled answer", settled, lines[0])
        assertEquals(
            "while the candidate is the engine's own line, dated with the route it belongs to",
            settled.computedAtMs,
            lines[1].computedAtMs
        )
        assertEquals(settled.start, lines[1].start)
        assertEquals(
            "and its clock is the engine's own figure, recomputed nowhere",
            settled.durationSec - 60.0,
            lines[1].durationSec,
            1e-6
        )
    }

    /**
     * **Next/prev loops the set** (R54), and the line the selection stands on is the one both saves
     * write and the one `Select route` follows (R55, R56).
     */
    @Test
    fun theSelectionLoopsTheSetAndTheSelectedLineIsWhatIsSaved() = runTest {
        val engine = CountingEngine()
        val viewModel = RouteViewModel(MutableStateFlow<RouteEngine>(engine))

        armEnds(viewModel, start, aim)
        val settled = (viewModel.state.value as RouteState.Choosing).plan ?: error("a plan stands")
        val candidate = anOffer(start, shortcut, settled.durationSec - 60.0)
        engine.publishOffers(listOf(candidate))
        val lines = viewModel.candidates.value

        assertEquals("the selection starts on the settled answer", 0, viewModel.candidateIndex.value)

        viewModel.stepCandidate(1)
        assertEquals("a step forward stands on the candidate", 1, viewModel.candidateIndex.value)
        assertEquals("and the save would write that line, not the settled one", lines[1], viewModel.selectedLine())

        viewModel.stepCandidate(1)
        assertEquals("stepping past the end loops back to the settled answer", 0, viewModel.candidateIndex.value)

        viewModel.stepCandidate(-1)
        assertEquals("and stepping back past the start loops to the candidate", 1, viewModel.candidateIndex.value)

        viewModel.selectRoute()

        val following = viewModel.state.value as RouteState.Following
        assertEquals("Select route follows the selected line", lines[1], following.plan)
        assertEquals("and drops the candidates it did not take", listOf(following.plan), viewModel.candidates.value)
        assertTrue("with no offer left published", viewModel.offers.value.isEmpty())
    }

    /**
     * **A written route greys the save** (R55, R59), because it reads **one** fact: the session's
     * route-to-track link, through one predicate. Read in both phases, which is the point — the
     * acquisition's save and the exit dialog's cannot disagree about whether the line is written.
     */
    @Test
    fun aWrittenRouteIsUnsavedForNoPhaseAndGreysTheSave() = runTest {
        val viewModel = RouteViewModel(MutableStateFlow<RouteEngine>(CountingEngine()))

        armEnds(viewModel, start, aim)
        val acquired = (viewModel.state.value as RouteState.Choosing).plan
            ?: error("a plan stands, so the acquisition has a front line")
        assertFalse("nothing is written yet", viewModel.isRouteSaved(acquired))

        viewModel.noteRouteSaved(acquired, "track-1")

        assertTrue("the link is the one fact the saves read", viewModel.isRouteSaved(acquired))
        assertEquals("and the panel's own reading of it follows the session", "track-1", viewModel.trackFor(acquired))
        assertEquals(
            "the reactive mirror says the same, which is what the save action greys on",
            mapOf(acquired to "track-1"),
            viewModel.sessionLinks.value
        )

        viewModel.selectRoute()
        val front = (viewModel.state.value as RouteState.Following).plan
        assertEquals("and the followed route is that same line", acquired, front)
        assertTrue("so the exit dialog's save is greyed by the same predicate", viewModel.isRouteSaved(front))
    }

    /**
     * **Routing confirms before tracking** (R60), read as the state the screen's own door branches on.
     *
     * While a route is followed the press must raise the **route's** dialog — the route is still the
     * mode with something to lose — and only with the route gone does the same press reach the shell's
     * tracking exit. The screen's own branch, made below, is what this pins.
     */
    @Test
    fun theRouteAnswersBeforeTheTrackingExit() = runTest {
        val viewModel = RouteViewModel(MutableStateFlow<RouteEngine>(CountingEngine()))

        armEnds(viewModel, start, aim)
        assertFalse(
            "inside the acquisition there is nothing to confirm, so the press simply ends the mode",
            viewModel.state.value is RouteState.Following
        )

        viewModel.selectRoute()
        assertTrue(
            "with a route on, the press resolves the route first",
            viewModel.state.value is RouteState.Following
        )

        viewModel.end()
        assertTrue(
            "and only with the route gone does the second press reach the tracking exit",
            viewModel.state.value is RouteState.Idle
        )
    }

    /**
     * **`Cancel` leaves the acquisition and asks nothing** (R57): the mode ends, the session goes with
     * it and no dialog stands in the way.
     */
    @Test
    fun cancelEndsTheAcquisitionWithNothingAsked() = runTest {
        val viewModel = RouteViewModel(MutableStateFlow<RouteEngine>(CountingEngine()))

        armEnds(viewModel, start, aim)
        viewModel.end()

        assertEquals(RouteState.Idle, viewModel.state.value)
        assertTrue("and its session goes with it", viewModel.sessionRoutes().isEmpty())
        assertTrue("with no candidate left drawn", viewModel.candidates.value.isEmpty())
    }

    /**
     * **The floor discards what it should** (R63): a candidate is offered only where it saves at least
     * `minSavingPct` of the settled trip's **own clock**, and a line that saves nothing is refused at
     * every floor.
     */
    @Test
    fun theCandidateFloorDiscardsWhatItShould() {
        assertEquals(
            "a saving under the floor is discarded",
            null,
            routeCandidateSavingSec(settledSec = 1_000.0, candidateSec = 900.0, minSavingPct = 15)
        )
        assertEquals(
            "one that exactly clears it is kept",
            150.0,
            routeCandidateSavingSec(settledSec = 1_000.0, candidateSec = 850.0, minSavingPct = 15)
        )
        assertEquals(
            "and at 0 the rule is the saving alone",
            10.0,
            routeCandidateSavingSec(settledSec = 1_000.0, candidateSec = 990.0, minSavingPct = 0)
        )
        assertEquals(
            "a line that saves nothing is refused whatever the floor",
            null,
            routeCandidateSavingSec(settledSec = 1_000.0, candidateSec = 1_000.0, minSavingPct = 0)
        )
        assertEquals(
            "and so is one that is slower",
            null,
            routeCandidateSavingSec(settledSec = 1_000.0, candidateSec = 1_100.0, minSavingPct = 0)
        )
    }

    /**
     * **The stage channel is proxied from the engine in force** (R15), so the panel reads one value
     * whatever engine is installed.
     */
    @Test
    fun theStageIsProxiedFromTheEngineInForce() = runTest {
        val engine = CountingEngine()
        val viewModel = RouteViewModel(MutableStateFlow<RouteEngine>(engine))

        assertNull("nothing runs, nothing is said", viewModel.progress.value)

        engine.publishProgress(RouteStage.SEARCH)

        assertEquals("and the stage follows the engine's own", RouteStage.SEARCH, viewModel.progress.value?.stage)

        engine.publishProgress(RouteStage.PULL, listOf(start, aim))

        val emitted = viewModel.progress.value
        assertEquals("the line rides the same emission as the stage", listOf(start, aim), emitted?.points)
        assertEquals("and the stage is that same emission's", RouteStage.PULL, emitted?.stage)
        assertNull("a partial line is never the plan", viewModel.state.value.plan)

        engine.publishProgress(null)

        assertNull("cleared with it", viewModel.progress.value)
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
        val engine = RouteAvoidEngine(
            paceKn = { 28.0 },
            slowWaterBudgetPct = { 33 },
            worldProvider = { OpenWaterWorld() }
        )
        val seen = ArrayList<RouteStage?>()
        // The collector runs **unconfined**, so it subscribes before the search starts and then
        // resumes on whichever thread publishes — a plain `launch` would only start when the test
        // yielded, and every boundary would already have been published and conflated away.
        val collectorScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val collector = collectorScope.launch { engine.progress.collect { seen += it?.stage } }

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
        assertNull("the channel is cleared with the answer", engine.progress.value)

        collector.cancel()
        collectorScope.cancel()
    }

    /**
     * **D8's keep rule** — the fine re-search keeps the incumbent on every tie, so an equal open-water
     * line is never replaced by the fine pass. It also drives the new pass end-to-end on a staged world.
     */
    @Test
    fun theFineReSearchKeepsTheIncumbentUnlessStrictlyFaster() = runTest {
        val engine = RouteAvoidEngine(
            paceKn = { 28.0 },
            slowWaterBudgetPct = { 33 },
            worldProvider = { OpenWaterWorld() }
        )
        engine.onOriginPositionChanged(start)
        val result = engine.onDestinationPositionChanged(aim)

        assertTrue("open water still answers", result is RouteResult.Success)
        val success = result as RouteResult.Success
        assertEquals(
            "the coarse and fine open-water lines tie, and the tie is the incumbent's",
            2,
            success.points.size
        )
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
 * straight line between them, judges one point unusable at most, publishes whatever stage it is handed
 * and whatever offers it is given — the readings the acquisition's own tests need and no more.
 */
private class CountingEngine(
    /** The one point this engine judges unusable, or null for an engine that judges nothing. */
    private val refuse: RoutePoint? = null
) : RouteEngine {

    private val _state = MutableStateFlow<RouteEngineState>(RouteEngineState.Ready)

    override val state: StateFlow<RouteEngineState> = _state.asStateFlow()

    private val _progress = MutableStateFlow<RouteProgress?>(null)

    override val progress: StateFlow<RouteProgress?> = _progress.asStateFlow()

    private val _offers = MutableStateFlow<List<RouteOffer>>(emptyList())

    override val offers: StateFlow<List<RouteOffer>> = _offers.asStateFlow()

    /** Every destination the feature asked about, in order. */
    val askedDestinations = ArrayList<RoutePoint>()

    /** Every anchor the feature told this engine, in order. */
    val toldOrigins = ArrayList<RoutePoint>()

    /** Every point the feature asked this engine to judge, in order. */
    val validatedPoints = ArrayList<RoutePoint>()

    private var heldOrigin: RoutePoint? = null
    private var heldDestination: RoutePoint? = null

    /** Publishes the progress the panel and the provisional line read. */
    fun publishProgress(stage: RouteStage?, points: List<RoutePoint>? = null) {
        _progress.value = if (stage == null) null else RouteProgress(stage, points)
    }

    /** Publishes the candidates the engine's background lane would have computed. */
    fun publishOffers(offers: List<RouteOffer>) {
        _offers.value = offers
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
