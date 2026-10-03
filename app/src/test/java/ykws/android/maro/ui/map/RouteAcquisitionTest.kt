package ykws.android.maro.ui.map

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.data.model.RouteResult
import ykws.android.maro.spatial.RouteComputation
import ykws.android.maro.spatial.RouteDeclarations
import ykws.android.maro.spatial.RouteEngine
import ykws.android.maro.spatial.RouteId
import ykws.android.maro.spatial.RouteReason
import ykws.android.maro.spatial.RouteUpdate
import ykws.android.maro.spatial.SpatialOperations
import ykws.android.maro.spatial.Units

/**
 * **The acquisition, as the machine sees it** — read through a real [RouteViewModel] driven the way
 * the drawer drives it: arming starts one lookup per declared computation, each update lands on the
 * page that owns the id, the page set opens at the main and grows as the lookups land, the selection
 * loops, and a written route greys the save through one predicate.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RouteAcquisitionTest {

    private val start = RoutePoint(43.5000, 7.0000)
    private val aim = RoutePoint(43.5200, 7.0100)
    private val shortcut = RoutePoint(43.5150, 7.0080)

    @Before
    fun installMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun releaseMainDispatcher() {
        Dispatchers.resetMain()
    }

    private fun line(from: RoutePoint, to: RoutePoint): RouteResult.Success {
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

    /** Arming is the trigger: the main page opens at index 0 and the search is in flight. */
    @Test
    fun armingOpensOnePagePerDeclaredComputationWithTheMainFirst() = runTest {
        val engine = CountingEngine(computations = 2)
        val viewModel = RouteViewModel(MutableStateFlow(engine))
        viewModel.arm(RouteEnds(start = start, fallbackStart = null, destination = aim))

        val choosing = viewModel.state.value as RouteState.Choosing
        assertTrue("the phase says it has asked", choosing.asked)
        assertTrue("and that a search is in flight", choosing.searching)
        assertEquals("one page per declared computation", 2, viewModel.pages.value.size)
        assertEquals("the main is index 0", RouteId(1), viewModel.pages.value[0].computationId)
    }

    /** Each update lands its result on the page that owns the id, and the search clears with the last. */
    @Test
    fun thePageSetGrowsAsTheLookupsLand() = runTest {
        val engine = CountingEngine(computations = 2)
        val viewModel = RouteViewModel(MutableStateFlow(engine))
        viewModel.arm(RouteEnds(start = start, fallbackStart = null, destination = aim))

        engine.publish(viewModel.pages.value[0].lookupId!!, line(start, aim))
        val afterMain = viewModel.pages.value
        assertEquals("the main page now holds its plan", listOf(start, aim), afterMain[0].plan?.points)
        assertTrue("the candidate page has not landed yet", afterMain[1].plan == null)
        assertTrue("a lookup is still in flight", (viewModel.state.value as RouteState.Choosing).searching)

        engine.publish(viewModel.pages.value[1].lookupId!!, line(start, shortcut))
        val choosing = viewModel.state.value as RouteState.Choosing
        assertEquals("the candidate landed on its own page", listOf(start, shortcut), viewModel.pages.value[1].plan?.points)
        assertFalse("no lookup is in flight any more", choosing.searching)
    }

    /** A rung identical to one already landed collapses into it, marking the survivor; a later rung still lands. */
    @Test
    fun aCollapsedRungMarksTheSurvivorAndALaterRungStillLands() = runTest {
        val engine = CountingEngine(computations = 3)
        val viewModel = RouteViewModel(MutableStateFlow(engine))
        viewModel.arm(RouteEnds(start = start, fallbackStart = null, destination = aim))
        val ids = viewModel.pages.value.map { it.lookupId!! }

        engine.publish(ids[0], line(start, aim))
        engine.publish(ids[1], line(start, aim))

        assertEquals("the duplicate page is dropped", 2, viewModel.pages.value.size)
        assertTrue("the survivor is marked as the collapse's own", viewModel.pages.value[0].collapsed)

        engine.publish(ids[2], line(start, shortcut))
        assertEquals(
            "the kept rung still finds its shifted page",
            listOf(start, shortcut),
            viewModel.pages.value[1].plan?.points
        )
    }

    /** Two rungs whose lines sit within the collapse tolerance fold into one, marked as the collapse's own. */
    @Test
    fun rungsWithinTheToleranceCollapseIntoOneMarkedRoute() = runTest {
        val engine = CountingEngine(computations = 3)
        val viewModel = RouteViewModel(MutableStateFlow(engine))
        viewModel.arm(RouteEnds(start = start, fallbackStart = null, destination = aim))
        val ids = viewModel.pages.value.map { it.lookupId!! }

        engine.publish(ids[0], line(start, aim))
        engine.publish(ids[1], line(start, RoutePoint(43.5201, 7.0101)))

        assertEquals("a near-identical rung folds rather than staying", 2, viewModel.pages.value.size)
        assertTrue("the survivor is marked as the collapse's own", viewModel.pages.value[0].collapsed)
    }

    /** The dispersion reading: a line against itself is zero, a small shift is a small gap, a far line stays apart. */
    @Test
    fun routeDispersionMeasuresTheWidestGap() {
        val a = listOf(start, aim)
        val near = listOf(start, RoutePoint(43.5201, 7.0101))
        val far = listOf(start, shortcut)

        assertEquals("a line against itself has no dispersion", 0.0, routeDispersionM(a, a), 1e-6)
        assertTrue("a shifted line reads below the tolerance", routeDispersionM(a, near) < 25.0)
        assertTrue("a route hundreds of metres away reads far", routeDispersionM(a, far) > 100.0)
    }

    /** Next/prev loops the page set, and the selection is what the buttons act on. */
    @Test
    fun theSelectionLoopsThePageSet() = runTest {
        val engine = CountingEngine(computations = 2)
        val viewModel = RouteViewModel(MutableStateFlow(engine))
        viewModel.arm(RouteEnds(start = start, fallbackStart = null, destination = aim))
        engine.publish(viewModel.pages.value[0].lookupId!!, line(start, aim))
        engine.publish(viewModel.pages.value[1].lookupId!!, line(start, shortcut))

        assertEquals("the selection starts on the main", 0, viewModel.selectedIndex.value)
        viewModel.stepPage(1)
        assertEquals("a step forward stands on the candidate", 1, viewModel.selectedIndex.value)
        assertEquals(
            "and the selected plan is the candidate's",
            listOf(start, shortcut),
            viewModel.selectedPlan()?.points
        )
        viewModel.stepPage(1)
        assertEquals("stepping past the end loops back to the main", 0, viewModel.selectedIndex.value)

        viewModel.selectRoute()
        val following = viewModel.state.value as RouteState.Following
        assertEquals("Select route follows the selected line", listOf(start, aim), following.plan.points)
    }

    /** A written route greys the save — the one predicate, read through the session link. */
    @Test
    fun aWrittenRouteGreysTheSave() = runTest {
        val engine = CountingEngine()
        val viewModel = RouteViewModel(MutableStateFlow(engine))
        viewModel.arm(RouteEnds(start = start, fallbackStart = null, destination = aim))
        engine.publish(viewModel.pages.value[0].lookupId!!, line(start, aim))
        val plan = (viewModel.state.value as RouteState.Choosing).plan ?: error("a plan stands")

        assertFalse("nothing is written yet", viewModel.isRouteSaved(plan))
        viewModel.noteRouteSaved(plan, "track-1")
        assertTrue("the link is the one fact the saves read", viewModel.isRouteSaved(plan))
        assertEquals(mapOf(plan to "track-1"), viewModel.sessionLinks.value)
    }

    /** `end` cancels the lookups and returns to Idle with nothing left. */
    @Test
    fun endingTheAcquisitionCancelsTheLookupsAndReturnsToIdle() = runTest {
        val engine = CountingEngine()
        val viewModel = RouteViewModel(MutableStateFlow(engine))
        viewModel.arm(RouteEnds(start = start, fallbackStart = null, destination = aim))

        viewModel.end()

        assertEquals(RouteState.Idle, viewModel.state.value)
        assertEquals("the one disposal cancelled the lookup", listOf(RouteId(1)), engine.cancelled)
        assertTrue("no page is left drawn", viewModel.pages.value.isEmpty())
        assertTrue("and the session went with it", viewModel.sessionRoutes().isEmpty())
    }

    /** The track card's door: a saved route becomes the followed route straight from Idle. */
    @Test
    fun followSavedRouteEntersFollowingFromIdleCarryingTheTrackId() = runTest {
        val engine = CountingEngine(computations = 1)
        val viewModel = RouteViewModel(MutableStateFlow(engine))
        val plan = RoutePlan(
            start = start,
            destination = aim,
            destinationMoved = false,
            points = listOf(start, aim),
            legTimesSec = listOf(120.0),
            distanceM = 1_000.0,
            durationSec = 120.0,
            computedAtMs = 0L
        )

        viewModel.followSavedRoute(plan, "track-1")

        val following = viewModel.state.value as RouteState.Following
        assertEquals("the track id rides the state", "track-1", following.followedTrackId)
        assertEquals("and the line is the stored plan", plan, following.plan)
        assertTrue("no page survives the Idle entry", viewModel.pages.value.isEmpty())

        viewModel.end()
        assertTrue("ending returns to Idle", viewModel.state.value is RouteState.Idle)
    }

    /**
     * A stored match replaces the search (R82): no lookup is started, the sole page carries the stored
     * plan, the anchor is the line's own first point, and the session already links the plan to its
     * track — so the save door is shut with no second predicate.
     */
    @Test
    fun aStoredMatchSeedsOneSettledPageWithNoEngineAsk() = runTest {
        val engine = CountingEngine(computations = 2)
        val viewModel = RouteViewModel(MutableStateFlow(engine))
        val plan = RoutePlan(
            start = start,
            destination = aim,
            destinationMoved = false,
            points = listOf(start, aim),
            legTimesSec = listOf(120.0),
            distanceM = 1_000.0,
            durationSec = 120.0,
            computedAtMs = 0L
        )

        viewModel.arm(
            RouteEnds(
                start = start,
                fallbackStart = null,
                destination = aim,
                startMarkerId = "m-start",
                destinationMarkerId = "m-dest"
            ),
            StoredRouteMatch(plan, "track-1")
        )

        assertTrue("no lookup was started", engine.started.isEmpty())
        assertEquals("the sole page is the stored line", listOf(plan), viewModel.pages.value.map { it.plan })
        assertNull("and it carries no lookup id", viewModel.pages.value.first().lookupId)
        val choosing = viewModel.state.value as RouteState.Choosing
        assertEquals("the anchor is the line's own first point", plan.start, choosing.start)
        assertEquals("the settled plan stands", plan, choosing.plan)
        assertFalse("nothing is searching", choosing.searching)
        assertTrue("the save door is already shut", viewModel.isRouteSaved(plan))
        assertEquals("the pair was retained for the save site", "m-start" to "m-dest", viewModel.armedMarkerIds())

        viewModel.end()
        assertEquals("and the end cleared it", null to null, viewModel.armedMarkerIds())
    }

    /**
     * A mirrored match (R86, R87) is a **new line**: it lands exactly as the exact pair does — one settled
     * page, no engine ask, the pair retained — but carries **no track id**, so the session registers it
     * with no link and the save door stays **open**, unlike the forward match's shut one (R85).
     */
    @Test
    fun aMirroredMatchLeavesTheSaveDoorOpen() = runTest {
        val engine = CountingEngine(computations = 2)
        val viewModel = RouteViewModel(MutableStateFlow(engine))
        val plan = RoutePlan(
            start = aim,
            destination = start,
            destinationMoved = false,
            points = listOf(aim, start),
            legTimesSec = listOf(120.0),
            distanceM = 1_000.0,
            durationSec = 120.0,
            computedAtMs = 42_424L
        )

        viewModel.arm(
            RouteEnds(
                start = start,
                fallbackStart = null,
                destination = aim,
                startMarkerId = "m-start",
                destinationMarkerId = "m-dest"
            ),
            StoredRouteMatch(plan, trackId = null)
        )

        assertTrue("no lookup was started", engine.started.isEmpty())
        assertEquals("the sole page is the mirrored line", listOf(plan), viewModel.pages.value.map { it.plan })
        assertFalse("the return trip is not written", viewModel.isRouteSaved(plan))
        assertEquals(
            "the session holds the plan with no link",
            mapOf<RoutePlan, String?>(plan to null),
            viewModel.sessionLinks.value
        )
        val choosing = viewModel.state.value as RouteState.Choosing
        assertEquals("the settled plan stands", plan, choosing.plan)
        assertFalse("nothing is searching", choosing.searching)
    }
}

/**
 * A counting engine with two computations by default: it declares them, returns fresh lookup ids,
 * records every call, and publishes whatever it is handed — the readings the acquisition's own tests
 * need and no more.
 */
private class CountingEngine(private val computations: Int = 1) : RouteEngine {

    private val _updates = MutableSharedFlow<RouteUpdate>(extraBufferCapacity = 64)
    override val updates: Flow<RouteUpdate> = _updates.asSharedFlow()

    val started = ArrayList<RouteId>()
    val cancelled = ArrayList<RouteId>()
    private var nextLookup = 0L

    override fun routesToCompute(origin: RoutePoint, destination: RoutePoint): RouteDeclarations =
        RouteDeclarations.Available((1..computations).map { RouteComputation(RouteId(it.toLong()), 0) })

    override fun startLookup(computationId: RouteId): RouteId {
        started += computationId
        return RouteId(++nextLookup)
    }

    override fun cancelLookup(id: RouteId) {
        cancelled += id
    }

    fun publish(id: RouteId, result: RouteResult.Success?) {
        _updates.tryEmit(
            RouteUpdate(
                routeId = id,
                stageDone = null,
                nextStage = null,
                line = result?.points ?: emptyList(),
                result = result,
                reason = if (result == null) RouteReason.NO_PATH else null
            )
        )
    }
}
