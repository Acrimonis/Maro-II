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

        engine.publish(viewModel.pages.value[0].lookupId, line(start, aim))
        val afterMain = viewModel.pages.value
        assertEquals("the main page now holds its plan", listOf(start, aim), afterMain[0].plan?.points)
        assertTrue("the candidate page has not landed yet", afterMain[1].plan == null)
        assertTrue("a lookup is still in flight", (viewModel.state.value as RouteState.Choosing).searching)

        engine.publish(viewModel.pages.value[1].lookupId, line(start, shortcut))
        val choosing = viewModel.state.value as RouteState.Choosing
        assertEquals("the candidate landed on its own page", listOf(start, shortcut), viewModel.pages.value[1].plan?.points)
        assertFalse("no lookup is in flight any more", choosing.searching)
    }

    /** Next/prev loops the page set, and the selection is what the buttons act on. */
    @Test
    fun theSelectionLoopsThePageSet() = runTest {
        val engine = CountingEngine(computations = 2)
        val viewModel = RouteViewModel(MutableStateFlow(engine))
        viewModel.arm(RouteEnds(start = start, fallbackStart = null, destination = aim))
        engine.publish(viewModel.pages.value[0].lookupId, line(start, aim))
        engine.publish(viewModel.pages.value[1].lookupId, line(start, shortcut))

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
        engine.publish(viewModel.pages.value[0].lookupId, line(start, aim))
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
