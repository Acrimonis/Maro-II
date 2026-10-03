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

    /**
     * A 3→2 collapse whose folded rung is the landed preference seats the survivor, never the third
     * rung's shifted slot (D15): the main lands, a distinct far rung lands, then the preferred third
     * rung lands within the main's collapse tolerance and folds into it.
     */
    @Test
    fun aCollapsedLandedPreferenceSeatsTheSurvivor() = runTest {
        val engine = CountingEngine(computations = 3)
        val viewModel = RouteViewModel(MutableStateFlow(engine))
        viewModel.arm(RouteEnds(start = start, fallbackStart = null, destination = aim))
        val ids = viewModel.pages.value.map { it.lookupId!! }

        engine.publish(ids[0], line(start, aim))
        engine.publish(ids[1], line(start, shortcut))
        assertEquals("the two settled rungs stand apart", 3, viewModel.pages.value.size)

        engine.publish(ids[2], line(start, RoutePoint(43.5201, 7.0101)))

        assertEquals("the folded rung drops the set to two pages", 2, viewModel.pages.value.size)
        assertTrue("the survivor is marked as the collapse's own", viewModel.pages.value[0].collapsed)
        assertEquals("the seat parks on the survivor, not the shifted third rung", 0, viewModel.selectedIndex.value)
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

    /** Next/prev walks the ETA-ordered view — fastest first — and the selection is what the buttons act on. */
    @Test
    fun theSelectionWalksTheEtaOrderedView() = runTest {
        val engine = CountingEngine(computations = 2)
        val viewModel = RouteViewModel(MutableStateFlow(engine))
        viewModel.arm(RouteEnds(start = start, fallbackStart = null, destination = aim))
        engine.publish(viewModel.pages.value[0].lookupId!!, line(start, aim))
        engine.publish(viewModel.pages.value[1].lookupId!!, line(start, shortcut))

        assertEquals("a two-rung set keeps the main as its seat", 0, viewModel.selectedIndex.value)
        assertEquals(
            "and the selected plan is the main's",
            listOf(start, aim),
            viewModel.selectedPlan()?.points
        )
        viewModel.stepPage(1)
        assertEquals("a step forward walks to the faster candidate", 1, viewModel.selectedIndex.value)
        assertEquals(
            "and the selected plan is the fastest candidate's",
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

    /**
     * **The third column's arithmetic** (R90): a page measured against the route the selection stands
     * on — positive slower, negative faster — silent on the selected page itself, a tie, or a duration
     * that has not landed.
     */
    @Test
    fun routeDeltaReadsAPageAgainstTheSelectedRoute() {
        assertNull(
            "the selected page says nothing about itself",
            routeDeltaSec(pageDurationSec = 300.0, selectedDurationSec = 300.0, isSelected = true)
        )
        assertEquals(
            "a slower page reads positive",
            60.0,
            routeDeltaSec(pageDurationSec = 360.0, selectedDurationSec = 300.0, isSelected = false)!!,
            1e-9
        )
        assertEquals(
            "a faster page reads negative",
            -60.0,
            routeDeltaSec(pageDurationSec = 240.0, selectedDurationSec = 300.0, isSelected = false)!!,
            1e-9
        )
        assertNull(
            "a tie says nothing",
            routeDeltaSec(pageDurationSec = 300.0, selectedDurationSec = 300.0, isSelected = false)
        )
        assertNull(
            "a page without a plan says nothing",
            routeDeltaSec(pageDurationSec = null, selectedDurationSec = 300.0, isSelected = false)
        )
        assertNull(
            "and neither does one whose selection has not landed",
            routeDeltaSec(pageDurationSec = 300.0, selectedDurationSec = null, isSelected = false)
        )
    }

    /** **The seat** (D12, §10): the preferred page keeps its place when it holds a plan. */
    @Test
    fun routeSeatsOnThePreferredRowWhenItHasLanded() {
        assertEquals("a landed preferred row seats itself", 1, routeSeatedIndex(listOf(true, true, true), 1))
        assertEquals("and a single landed page is the identity", 0, routeSeatedIndex(listOf(true), 0))
    }

    /** A pending preferred row steps the seat to the landed row nearest it, the lower index winning a tie. */
    @Test
    fun routeSeatsOnTheNearestLandedRowWhenThePreferredIsPending() {
        assertEquals(
            "the landed main steps in for a pending rung",
            0,
            routeSeatedIndex(listOf(true, false, false), 1)
        )
        assertEquals(
            "and the lower index wins an even split",
            1,
            routeSeatedIndex(listOf(false, true, false, false, true), 2)
        )
    }

    /** Nothing landed yet leaves the declared preference standing. */
    @Test
    fun routeKeepsThePreferredIndexWhileNothingHasLanded() {
        assertEquals(
            "the declaration stands until data lands",
            2,
            routeSeatedIndex(listOf(false, false, false), 2)
        )
    }

    /** The ETA view orders landed pages fastest-first, keeps the ladder's order on a tie, and parks pending last. */
    @Test
    fun routePagesByEtaOrdersFastestFirstAndKeepsTheLadderOnTies() {
        val plan = { seconds: Double ->
            RoutePlan(
                start = start,
                destination = aim,
                destinationMoved = false,
                points = listOf(start, aim),
                legTimesSec = listOf(seconds),
                distanceM = 1_000.0,
                durationSec = seconds,
                computedAtMs = 0L
            )
        }
        val around = RoutePage(lookupId = RouteId(1), plan = plan(900.0))
        val balanced = RoutePage(lookupId = RouteId(2), plan = plan(600.0))
        val through = RoutePage(lookupId = RouteId(3), plan = plan(300.0))
        assertEquals(
            "landed pages run fastest-first",
            listOf(through, balanced, around),
            routePagesByEta(listOf(around, balanced, through))
        )
        val tie = RoutePage(lookupId = RouteId(4), plan = plan(300.0))
        assertEquals(
            "a tie keeps the ladder's natural order",
            listOf(through, tie, balanced, around),
            routePagesByEta(listOf(around, balanced, through, tie))
        )
        val pending = RoutePage(lookupId = RouteId(5))
        assertEquals(
            "a pending page parks last",
            listOf(through, balanced, around, pending),
            routePagesByEta(listOf(around, pending, balanced, through))
        )
    }

    /** The preference names the seat, re-applied on every landing: a pending preference steps aside, a landed one seats itself. */
    @Test
    fun aLandingSeatsTheSelectionOffAPendingRow() = runTest {
        val engine = CountingEngine(computations = 3)
        val viewModel = RouteViewModel(MutableStateFlow(engine))
        viewModel.arm(RouteEnds(start = start, fallbackStart = null, destination = aim))
        val ids = viewModel.pages.value.map { it.lookupId!! }

        engine.publish(ids[0], line(start, aim))
        assertEquals("the main landed and the seat took it", 0, viewModel.selectedIndex.value)

        viewModel.stepPage(1)
        assertEquals("a step stands on the still-pending row", 1, viewModel.selectedIndex.value)

        engine.publish(ids[2], line(start, shortcut))
        assertEquals("the landed preference re-seats itself", 2, viewModel.selectedIndex.value)
    }

    /** The preference names the initial seat, and every landing re-applies it (D12 rework). */
    @Test
    fun thePreferenceNamesTheSeatAndEveryLandingReappliesIt() = runTest {
        val engine = CountingEngine(computations = 3)
        val viewModel = RouteViewModel(MutableStateFlow(engine))
        viewModel.arm(RouteEnds(start = start, fallbackStart = null, destination = aim))
        val ids = viewModel.pages.value.map { it.lookupId!! }

        assertEquals("while nothing has landed the preference stays highlighted", 2, viewModel.selectedIndex.value)

        engine.publish(ids[0], line(start, aim))
        assertEquals("a pending preference steps aside to the nearest landed rung", 0, viewModel.selectedIndex.value)

        engine.publish(ids[2], line(start, shortcut))
        assertEquals("the landed preference re-seats itself", 2, viewModel.selectedIndex.value)
    }

    /**
     * **The auto-pick's readiness keys on the main's landing, never on where the seat stands** (R80):
     * the seat may already stand on the preference's candidate while the main is still pending, yet the
     * acquisition's plan stays null — the settled answer is index 0's alone — and the main's landing is
     * what settles it.
     */
    @Test
    fun theAutoPicksReadinessKeysOnTheMainsLandingNotOnTheSeat() = runTest {
        val engine = CountingEngine(computations = 3)
        val viewModel = RouteViewModel(MutableStateFlow(engine))
        viewModel.arm(RouteEnds(start = start, fallbackStart = null, destination = aim))
        val ids = viewModel.pages.value.map { it.lookupId!! }

        engine.publish(ids[2], line(start, shortcut))
        assertEquals(
            "the seat stands on the preference's rung while the main is still pending",
            2,
            viewModel.selectedIndex.value
        )
        assertNull(
            "yet the acquisition's plan is the main's, which has not landed",
            (viewModel.state.value as RouteState.Choosing).plan
        )

        engine.publish(ids[0], line(start, aim))
        assertEquals(
            "the main's landing is what settles the plan",
            listOf(start, aim),
            (viewModel.state.value as RouteState.Choosing).plan?.points
        )
    }

    /**
     * **The auto-pick takes the main whatever the seat holds** (R80): with a candidate landed first the
     * seat stands on it, so the one-shot's own selection names index 0 rather than the seat's page.
     */
    @Test
    fun theAutoPickTakesTheMainWhateverTheSeatHolds() = runTest {
        val engine = CountingEngine(computations = 3)
        val viewModel = RouteViewModel(MutableStateFlow(engine))
        viewModel.arm(RouteEnds(start = start, fallbackStart = null, destination = aim))
        val ids = viewModel.pages.value.map { it.lookupId!! }

        engine.publish(ids[2], line(start, shortcut))
        engine.publish(ids[0], line(start, aim))
        assertEquals("the seat stands on the candidate", 2, viewModel.selectedIndex.value)

        viewModel.selectMainRoute()

        val following = viewModel.state.value as RouteState.Following
        assertEquals(
            "the one-shot followed the main, never the seat's candidate",
            listOf(start, aim),
            following.plan.points
        )
    }

    /**
     * **The panel's `Select` takes the seat, never the main** — the manual door's own guard: with a
     * candidate landed first the seat stands on it, and the panel's entry point must follow the seat.
     * Only the auto child names the main (R80, R94), so a regression that sends the manual door to
     * index 0 fails here.
     */
    @Test
    fun thePanelSelectTakesTheSeatNotTheMain() = runTest {
        val engine = CountingEngine(computations = 3)
        val viewModel = RouteViewModel(MutableStateFlow(engine))
        viewModel.arm(RouteEnds(start = start, fallbackStart = null, destination = aim))
        val ids = viewModel.pages.value.map { it.lookupId!! }

        engine.publish(ids[2], line(start, shortcut))
        engine.publish(ids[0], line(start, aim))
        assertEquals("the seat stands on the candidate", 2, viewModel.selectedIndex.value)

        viewModel.selectRoute()

        val following = viewModel.state.value as RouteState.Following
        assertEquals(
            "the panel's Select took the seat's candidate, not the main",
            listOf(start, shortcut),
            following.plan.points
        )
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
