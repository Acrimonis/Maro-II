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
import org.junit.Assert.assertNotNull
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
 * **The seam, exercised through the feature by an engine that is not the shipped one.**
 *
 * The reworked seam is driven end to end: `arm` asks the foreign engine which routes it can compute,
 * starts each declared computation as a lookup, and the one update flow turns each lookup into a page.
 * What is pinned here is the **id correlation** — an update lands on the page that owns the id, a
 * selection cancels every in-flight id through the one disposal function, an update for a cancelled id
 * changes no page, and a refused pair is carried as the reason the status line shows.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RouteEngineSeamTest {

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

    private fun success(from: RoutePoint, to: RoutePoint): RouteResult.Success {
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

    /** Arming asks the engine for its computations and starts each as a lookup, main first. */
    @Test
    fun armingStartsALookupPerDeclaredComputation() = runTest {
        val engine = ForeignEngine(computations = 2)
        val viewModel = RouteViewModel(MutableStateFlow(engine))

        viewModel.arm(RouteEnds(start = start, fallbackStart = null, destination = aim))

        assertEquals("the pair was handed to the engine", listOf(start to aim), engine.pairs)
        assertEquals(
            "each declared computation was started, in declaration order",
            listOf(RouteId(1), RouteId(2)),
            engine.started
        )
        assertEquals("one page per started lookup", 2, viewModel.pages.value.size)
        assertEquals("the main is index 0", RouteId(1), viewModel.pages.value[0].computationId)
    }

    /** An update carrying a result lands the plan on the page that owns the id. */
    @Test
    fun anUpdateLandsItsResultOnThePageThatOwnsTheId() = runTest {
        val engine = ForeignEngine()
        val viewModel = RouteViewModel(MutableStateFlow(engine))
        viewModel.arm(RouteEnds(start = start, fallbackStart = null, destination = aim))
        val lookupId = viewModel.pages.value.first().lookupId

        engine.publish(lookupId, success(start, aim))

        val choosing = viewModel.state.value as RouteState.Choosing
        assertNotNull("the phase holds the selected page's plan", choosing.plan)
        assertEquals("the line is the foreign engine's own", listOf(start, aim), choosing.plan?.points)
        assertEquals("and the page carries that same plan", choosing.plan, viewModel.pages.value.first().plan)
    }

    /** `Select route` follows the selected line and cancels every in-flight lookup. */
    @Test
    fun selectingRouteFollowsTheSelectedLineAndCancelsEveryLookup() = runTest {
        val engine = ForeignEngine(computations = 2)
        val viewModel = RouteViewModel(MutableStateFlow(engine))
        viewModel.arm(RouteEnds(start = start, fallbackStart = null, destination = aim))
        val mainId = viewModel.pages.value[0].lookupId
        val secondId = viewModel.pages.value[1].lookupId
        engine.publish(mainId, success(start, aim))
        engine.publish(secondId, success(start, shortcut))

        viewModel.stepPage(1)
        viewModel.selectRoute()

        val following = viewModel.state.value as RouteState.Following
        assertEquals("the selected candidate became the followed route", listOf(start, shortcut), following.plan.points)
        assertTrue("the main lookup was cancelled", mainId in engine.cancelled)
        assertTrue("and the candidate lookup too", secondId in engine.cancelled)
    }

    /** An update for a cancelled id changes no page. */
    @Test
    fun anUpdateForACancelledIdChangesNoPage() = runTest {
        val engine = ForeignEngine()
        val viewModel = RouteViewModel(MutableStateFlow(engine))
        viewModel.arm(RouteEnds(start = start, fallbackStart = null, destination = aim))
        val lookupId = viewModel.pages.value.first().lookupId

        viewModel.end()
        assertEquals("the one disposal function cancelled the lookup", listOf(lookupId), engine.cancelled)
        assertTrue("the end cleared the page set", viewModel.pages.value.isEmpty())

        engine.publish(lookupId, success(start, aim))
        assertTrue("a late update for a cancelled id changes no page", viewModel.pages.value.isEmpty())
    }

    /** A refused pair arms the toggle and the status line carries the reason — no lookup starts. */
    @Test
    fun aRefusedPairIsCarriedAsTheReasonAndStartsNoLookup() = runTest {
        val engine = ForeignEngine(refused = RouteReason.WORLD_NOT_READY)
        val viewModel = RouteViewModel(MutableStateFlow(engine))
        viewModel.arm(RouteEnds(start = start, fallbackStart = null, destination = aim))

        val choosing = viewModel.state.value as RouteState.Choosing
        assertEquals("the reason rides in the closed set", RouteReason.WORLD_NOT_READY, choosing.refusal)
        assertTrue("no lookup was started", engine.started.isEmpty())
        assertTrue("and no page exists", viewModel.pages.value.isEmpty())
    }
}

/**
 * **A foreign engine: the same contract, and none of the shipped machinery.** It declares [computations]
 * computations, records every call, returns fresh lookup ids, and publishes whatever it is handed —
 * the readings the seam's own tests need and no more.
 */
private class ForeignEngine(
    private val computations: Int = 1,
    private val refused: RouteReason? = null
) : RouteEngine {

    private val _updates = MutableSharedFlow<RouteUpdate>(extraBufferCapacity = 64)
    override val updates: Flow<RouteUpdate> = _updates.asSharedFlow()

    val pairs = ArrayList<Pair<RoutePoint, RoutePoint>>()
    val started = ArrayList<RouteId>()
    val cancelled = ArrayList<RouteId>()
    private var nextLookup = 0L

    override fun routesToCompute(origin: RoutePoint, destination: RoutePoint): RouteDeclarations {
        pairs += origin to destination
        refused?.let { return RouteDeclarations.Refused(it) }
        return RouteDeclarations.Available(
            (1..computations).map { RouteComputation(RouteId(it.toLong()), 0) }
        )
    }

    override fun startLookup(computationId: RouteId): RouteId {
        started += computationId
        return RouteId(++nextLookup)
    }

    override fun cancelLookup(id: RouteId) {
        cancelled += id
    }

    fun publish(id: RouteId, result: RouteResult.Success?, reason: RouteReason? = null) {
        _updates.tryEmit(
            RouteUpdate(
                routeId = id,
                stageDone = null,
                nextStage = null,
                line = result?.points ?: emptyList(),
                result = result,
                reason = reason
            )
        )
    }
}
