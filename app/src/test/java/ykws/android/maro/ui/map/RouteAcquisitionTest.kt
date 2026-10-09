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
import org.junit.Assert.assertNotNull
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
import ykws.android.maro.spatial.RouteProvisional
import ykws.android.maro.spatial.RouteReason
import ykws.android.maro.spatial.RouteRunningBest
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
        assertEquals(
            "the survivor carries both rungs' labels, its own first",
            listOf(1, 2),
            viewModel.pages.value[0].foldedDescriptionResIds
        )

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

    /** The absolute set — a row tap or a swipe names an ETA-ordered position — and the selection is what Select route acts on. */
    @Test
    fun theSelectionTakesAnEtaOrderedPosition() = runTest {
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
        // The ETA view's first position is the faster candidate, so the absolute set seats page 1.
        viewModel.selectPage(0)
        assertEquals("the ETA view's fastest position seats the faster candidate", 1, viewModel.selectedIndex.value)
        assertEquals(
            "and the selected plan is the fastest candidate's",
            listOf(start, shortcut),
            viewModel.selectedPlan()?.points
        )

        viewModel.selectRoute()
        val following = viewModel.state.value as RouteState.Following
        assertEquals("Select route follows the seated candidate", listOf(start, shortcut), following.plan.points)
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

    /**
     * **The read direction is not a route's identity** (R99): a route written while its line is
     * mirrored answers as saved in **either** orientation, the session keeps one link rather than two,
     * and the persisted end ids come from the armed pair rather than the displayed direction — so the
     * mirror never resurrects the save door and a mirrored route reopens as the same route.
     */
    @Test
    fun aMirroredRouteSavesAndAnswersAsOneRoute() = runTest {
        val engine = CountingEngine()
        val viewModel = RouteViewModel(MutableStateFlow(engine))
        viewModel.arm(
            RouteEnds(
                start = start,
                fallbackStart = null,
                destination = aim,
                startMarkerId = "m-start",
                destinationMarkerId = "m-dest"
            )
        )
        engine.publish(viewModel.pages.value[0].lookupId!!, line(start, aim))
        val armed = (viewModel.state.value as RouteState.Choosing).plan ?: error("a plan stands")
        val mirrored = armed.reversed()

        assertFalse("the mirrored line is not written yet", viewModel.isRouteSaved(mirrored))
        viewModel.noteRouteSaved(mirrored, "track-1")

        assertTrue("a mirrored saved route answers as written", viewModel.isRouteSaved(mirrored))
        assertTrue("and so does the armed orientation", viewModel.isRouteSaved(armed))
        assertEquals("one link stands, not two", 1, viewModel.sessionRoutes().size)
        assertEquals(
            "the persisted ends are the armed pair, not the read direction",
            "m-start" to "m-dest",
            viewModel.armedMarkerIds()
        )
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
     * **Every arming runs the fresh acquisition and takes no stored line** (R83 struck) — an arming over
     * a pair a stored route already stands between is searched like any other: the engine is asked, one
     * page opens per declared computation, no settled stored page is seeded and the save predicate stays
     * open, so nothing reads a saved line behind an arming.
     */
    @Test
    fun anArmingAlwaysAcquiresAndTakesNoStoredLine() = runTest {
        val engine = CountingEngine(computations = 2)
        val viewModel = RouteViewModel(MutableStateFlow(engine))
        val ends = RouteEnds(
            start = start,
            fallbackStart = null,
            destination = aim,
            startMarkerId = "m-start",
            destinationMarkerId = "m-dest"
        )

        viewModel.arm(ends)

        assertTrue("the engine was asked", engine.started.isNotEmpty())
        assertEquals("one page per declared computation", 2, viewModel.pages.value.size)
        assertTrue("every page is a started lookup", viewModel.pages.value.all { it.lookupId != null })
        assertTrue("no settled stored page stands", viewModel.pages.value.all { it.plan == null })
        val choosing = viewModel.state.value as RouteState.Choosing
        assertTrue("the pair is searching", choosing.searching)
        assertEquals("the pair was retained for the save site", "m-start" to "m-dest", viewModel.armedMarkerIds())

        viewModel.end()
        assertEquals("and the end cleared it", null to null, viewModel.armedMarkerIds())
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

    /** **A row's two figures, in the panel's own order** (2026-10-07): the time leads, the distance follows. */
    @Test
    fun routeRowLinesPrintTheTimeFirstForALandedRowAndAPendingOneAlike() {
        val landed = routeRowLines(RouteRowFigures(distanceNm = 3.5, durationSec = 726.0))
        assertEquals(
            "the time leads a landed row",
            listOf(RouteRowKind.DURATION, RouteRowKind.DISTANCE),
            landed.map { it.kind }
        )
        assertEquals("and carries the settled seconds", 726.0, landed.first().value!!, 1e-9)
        assertEquals("with the distance second", 3.5, landed.last().value!!, 1e-9)

        val pending = routeRowLines(null)
        assertEquals(
            "a waiting row reverses with it",
            listOf(RouteRowKind.DURATION, RouteRowKind.DISTANCE),
            pending.map { it.kind }
        )
        assertNull("and its time line prints nothing yet", pending.first().value)
        assertNull("and neither does its distance line", pending.last().value)
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

    /**
     * **The preference seats an untouched acquisition; a hand touch freezes it for good** (2026-10-07):
     * while the reader has not taken over, the seat re-reads on every landing; from the first row tap or
     * swipe, no landing may move it any more.
     */
    @Test
    fun aLandingSeatsTheSelectionOffAPendingRowUntilTheUserTakesOver() = runTest {
        val engine = CountingEngine(computations = 3)
        val viewModel = RouteViewModel(MutableStateFlow(engine))
        viewModel.arm(RouteEnds(start = start, fallbackStart = null, destination = aim))
        val ids = viewModel.pages.value.map { it.lookupId!! }

        engine.publish(ids[0], line(start, aim))
        assertEquals("the main landed and the seat took it", 0, viewModel.selectedIndex.value)

        viewModel.selectPage(1)
        assertEquals("the absolute set stands on the still-pending row", 1, viewModel.selectedIndex.value)

        engine.publish(ids[2], line(start, shortcut))
        assertEquals(
            "and a landing after the reader's touch no longer moves the seat",
            1,
            viewModel.selectedIndex.value
        )
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
     * **The auto-pick takes the ranking's final best** (R80): with a candidate landed first the seat
     * stands on it, and the one-shot's own selection takes the winner the engine reports — here the main
     * — rather than the seat's page. The seat follows each improvement on the way.
     */
    @Test
    fun theAutoPickTakesTheFinalBestNotTheSeat() = runTest {
        val engine = CountingEngine(computations = 3)
        val viewModel = RouteViewModel(MutableStateFlow(engine))
        viewModel.arm(RouteEnds(start = start, fallbackStart = null, destination = aim))
        val ids = viewModel.pages.value.map { it.lookupId!! }

        engine.publish(ids[2], line(start, shortcut), runningBest = ids[2])
        assertEquals("the seat follows the running best", 2, viewModel.selectedIndex.value)

        engine.publish(ids[0], line(start, aim), runningBest = ids[0])
        assertEquals("and moves again on the next improvement", 0, viewModel.selectedIndex.value)

        viewModel.selectBestRoute()

        val following = viewModel.state.value as RouteState.Following
        assertEquals(
            "the one-shot followed the ranked winner, never the seat's earlier candidate",
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

    /**
     * **A waiting row prints the provisional pair, a landed row the settled figures instead** — the one
     * reading the table's middle column takes: the settled pair wins where a plan stands, the provisional
     * pair fills the cell that is still waiting, and a row with neither keeps the `--` placeholder.
     */
    @Test
    fun aWaitingRowPrintsTheProvisionalPairAndALandedRowTheSettledFigures() {
        val provisional = RouteProvisional(1_852.0, 400.0)
        val settled = RoutePlan(
            start = start,
            destination = aim,
            destinationMoved = false,
            points = listOf(start, aim),
            legTimesSec = listOf(600.0),
            distanceM = 1_000.0,
            durationSec = 600.0,
            computedAtMs = 0L
        )

        val waitingFigures = routeRowFigures(RoutePage(lookupId = RouteId(1), provisional = provisional))
        assertNotNull("a waiting row prints its provisional pair", waitingFigures)
        assertEquals(
            "the provisional distance is the pair's length in nautical miles",
            Units.metresToNauticalMiles(1_852.0),
            waitingFigures!!.distanceNm,
            1e-9
        )
        assertEquals("and the provisional ETA is the pair's own time", 400.0, waitingFigures.durationSec, 1e-9)

        val landedFigures = routeRowFigures(
            RoutePage(lookupId = RouteId(2), plan = settled, provisional = provisional)
        )
        assertNotNull("a landed row prints the settled figures", landedFigures)
        assertEquals("the settled distance is the plan's", settled.distanceNm, landedFigures!!.distanceNm, 1e-9)
        assertEquals(
            "and the settled ETA is the plan's whole-line time, replacing the provisional",
            600.0,
            landedFigures.durationSec,
            1e-9
        )

        assertNull(
            "a row with neither a plan nor a provisional pair prints the placeholder",
            routeRowFigures(RoutePage(lookupId = RouteId(3)))
        )
    }

    /**
     * **The loss toggle, through the machine** (R99): a followed route flips **in place** the moment
     * its own time-to-go loses `route.follow.swap.lossSec` against its recent low — republishing
     * `Following` with the mirrored plan and nothing re-armed — while a flat progress never flips.
     */
    @Test
    fun theFollowedRouteFlipsInPlaceWhenItLosesTheLossAgainstItsLow() = runTest {
        val viewModel = RouteViewModel(MutableStateFlow(CountingEngine()))
        val a = RoutePoint(43.5000, 7.0000)
        val b = RoutePoint(43.5200, 7.0000)
        val midpoint = RoutePoint(43.5100, 7.0000)
        val quarter = RoutePoint(43.5050, 7.0000)
        val plan = RoutePlan(
            start = a,
            destination = b,
            destinationMoved = false,
            points = listOf(a, b),
            legTimesSec = listOf(600.0),
            distanceM = 2_000.0,
            durationSec = 600.0,
            computedAtMs = 0L
        )
        viewModel.followSavedRoute(plan, "track-1")

        // Settling on the line: half the line is left at the midpoint, and a flat step does nothing.
        viewModel.onBoatFix(midpoint, nowElapsedMs = 0)
        val flat = viewModel.onBoatFix(midpoint, nowElapsedMs = 1_000)
        assertFalse("a flat progress does not flip", flat.flip)

        // Heading back: the time-to-go rises by more than the loss → the plan mirrors in place.
        val flip = viewModel.onBoatFix(quarter, nowElapsedMs = 2_000)
        assertTrue("a loss against the low flips the line", flip.flip)

        val following = viewModel.state.value as RouteState.Following
        assertEquals("and the plan is mirrored in place", plan.reversed(), following.plan)
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
        RouteDeclarations.Available((1..computations).map { RouteComputation(RouteId(it.toLong()), it) })

    override fun startLookup(computationId: RouteId): RouteId {
        started += computationId
        return RouteId(++nextLookup)
    }

    override fun cancelLookup(id: RouteId) {
        cancelled += id
    }

    fun publish(id: RouteId, result: RouteResult.Success?, runningBest: RouteId? = null) {
        _updates.tryEmit(
            RouteUpdate(
                routeId = id,
                stageDone = null,
                nextStage = null,
                line = result?.points ?: emptyList(),
                result = result,
                reason = if (result == null) RouteReason.NO_PATH else null,
                runningBest = runningBest?.let { RouteRunningBest(it, compared = 1) }
            )
        )
    }
}
