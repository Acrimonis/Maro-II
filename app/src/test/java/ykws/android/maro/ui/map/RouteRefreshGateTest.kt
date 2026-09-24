package ykws.android.maro.ui.map

import kotlinx.coroutines.CancellationException
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ykws.android.maro.R
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.data.model.RouteResult
import ykws.android.maro.data.track.TrackFromCourse
import ykws.android.maro.spatial.RouteEngine
import ykws.android.maro.spatial.RouteEngineState
import ykws.android.maro.spatial.RouteRefusalReason
import ykws.android.maro.spatial.SpatialOperations
import ykws.android.maro.spatial.Units

/**
 * **The following phase's own cycle: its gate, the engine's veto, the abort, the freeze — and the
 * ladder the session keeps.**
 *
 * Everything here is read through a real [`RouteViewModel`] driven the way the map drives it, because
 * the behaviours worth pinning are the ones that only exist across two calls: a refresh that is only
 * *delayed* by a veto, one that is **aborted** rather than failed, one whose failure arrives after the
 * mode has gone, and a freeze that holds the gate's clock and is forgotten with the mode (R10–R13,
 * R26).
 *
 * The gate's two thresholds are read as a **pure rule** — either one alone opens the moment — and the
 * ladder's caps and its 20 % → 80 % band as pure rules too, because those are the shapes a drawing
 * reads and a test can hold without a map.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RouteRefreshGateTest {

    private val origin = RoutePoint(43.5000, 7.0000)
    private val aim = RoutePoint(43.5200, 7.0100)
    private val boatLater = RoutePoint(43.5050, 7.0030)

    private val now = 1_700_000_000_000L

    @Before
    fun installMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun releaseMainDispatcher() {
        Dispatchers.resetMain()
    }

    /** Drives the machine to a followed route, the way the map does: arm, aim, confirm. */
    private suspend fun following(engine: RouteEngine): RouteViewModel {
        val viewModel = RouteViewModel(MutableStateFlow(engine))
        viewModel.beginDraft(origin)
        viewModel.preview(aim)
        viewModel.confirm()
        return viewModel
    }

    private fun failureResIdOf(viewModel: RouteViewModel): Int? = viewModel.refreshFailureResId.value

    /**
     * **The gate's two thresholds: either one alone opens the moment** (R10).
     *
     * The clock answers a boat holding station on a line whose world has moved; the distance answers a
     * slow drift off a long leg. Neither is a subset of the other, which is why both are read.
     */
    @Test
    fun eitherThresholdAloneOpensTheGate() {
        assertFalse(
            "inside the interval and on the line: the moment has not come",
            routeRefreshDue(lastAnswerAtMs = now, nowMs = now + 5_000L, distanceOffRouteM = 0.0)
        )
        assertTrue(
            "the clock alone, with the boat standing on the line",
            routeRefreshDue(lastAnswerAtMs = now, nowMs = now + 30_000L, distanceOffRouteM = 0.0)
        )
        assertTrue(
            "the distance alone, on a route answered this instant",
            routeRefreshDue(lastAnswerAtMs = now, nowMs = now, distanceOffRouteM = 150.0)
        )
        assertFalse(
            "and neither of them opens it",
            routeRefreshDue(lastAnswerAtMs = now, nowMs = now + 5_000L, distanceOffRouteM = 99.0)
        )
    }

    /**
     * The gate's defaults are the **file's** own values, so no literal decides them: the two keys are
     * what the app reads and what a change of the properties file moves.
     */
    @Test
    fun theGatesDefaultsAreThePropertiesFilesOwnValues() {
        assertTrue(
            "a moment exactly one configured interval on opens the gate",
            routeRefreshDue(
                lastAnswerAtMs = now,
                nowMs = now + AppConfig.routeRefreshIntervalSec * 1_000L,
                distanceOffRouteM = 0.0
            )
        )
        assertTrue(
            "and the configured distance off the route does too, however fresh the answer",
            routeRefreshDue(
                lastAnswerAtMs = now,
                nowMs = now,
                distanceOffRouteM = AppConfig.routeRefreshOffRouteM
            )
        )
    }

    /**
     * **A veto only delays** (R11): the engine says it cannot take the call, and nothing at all happens
     * — the standing route, the ladder, the refresh state and the toast are all untouched.
     */
    @Test
    fun aVetoedRefreshChangesNothingAndSaysNothing() = runTest {
        val engine = GateEngine(readyToRecompute = false)
        val viewModel = following(engine)
        val before = (viewModel.state.value as RouteState.Following).routes

        viewModel.refresh(boatLater)

        val after = viewModel.state.value as RouteState.Following
        assertEquals("the standing route and its ladder are exactly as they were", before, after.routes)
        assertEquals("and nothing is left running", RouteRefresh.IDLE, after.refresh)
        assertNull("and a veto is not a failure, so nothing is said", failureResIdOf(viewModel))
        assertTrue("and the engine was never asked for a route", engine.toldOrigins.size == 1)
    }

    /**
     * **A failed refresh changes nothing on the map** (R13): the standing line stays the front line,
     * **nothing is staled** — stale means *replaced* — and the reason reaches the user as a toast.
     */
    @Test
    fun aFailedRefreshKeepsTheStandingLineStalesNothingAndRaisesTheReason() = runTest {
        val engine = GateEngine(originAnswer = RouteResult.OutsideWater)
        val viewModel = following(engine)
        val before = (viewModel.state.value as RouteState.Following).routes

        viewModel.refresh(boatLater)

        val after = viewModel.state.value as RouteState.Following
        assertEquals("nothing was staled and nothing was replaced", before, after.routes)
        assertEquals("and the cycle is idle again", RouteRefresh.IDLE, after.refresh)
        assertEquals(
            "the engine's own reason is carried as a resource id for the toast",
            R.string.route_failure_outside_water,
            failureResIdOf(viewModel)
        )
        viewModel.clearRefreshFailure()
        assertNull("and the one-shot is cleared as it is read", failureResIdOf(viewModel))
    }

    /**
     * **Abort cancels the refresh alone.** The call in flight is dropped, the standing line is what is
     * left, and — unlike a failure — nothing is said: the user asked for it.
     */
    @Test
    fun anAbortedRefreshLeavesTheStandingLineAndSaysNothing() = runTest {
        val gate = CompletableDeferred<Unit>()
        val engine = GateEngine(gateOrigin = gate)
        val viewModel = following(engine)
        val before = (viewModel.state.value as RouteState.Following).routes

        viewModel.refresh(boatLater)
        assertEquals(
            "the refresh is running, which is what offers Abort",
            RouteRefresh.RUNNING,
            (viewModel.state.value as RouteState.Following).refresh
        )

        viewModel.abortRefresh()

        val after = viewModel.state.value as RouteState.Following
        assertEquals("the standing line is untouched", before, after.routes)
        assertEquals("and the cycle is idle again", RouteRefresh.IDLE, after.refresh)
        assertNull("nothing is said about an abort", failureResIdOf(viewModel))

        gate.complete(Unit)
        assertEquals(
            "and the abandoned answer cannot land after it",
            before,
            (viewModel.state.value as RouteState.Following).routes
        )
    }

    /**
     * **A failure arriving after the mode has gone is not toasted** (R23): leaving cancels the call in
     * flight, and an answer that lands afterwards finds no following phase to write to.
     */
    @Test
    fun aFailureArrivingAfterTheModeHasGoneIsNotToasted() = runTest {
        val gate = CompletableDeferred<Unit>()
        val engine = GateEngine(originAnswer = RouteResult.OutsideWater, gateOrigin = gate)
        val viewModel = following(engine)

        viewModel.refresh(boatLater)
        viewModel.end()
        assertEquals(RouteState.Idle, viewModel.state.value)

        gate.complete(Unit)

        assertNull("the mode is gone, so its failure has nobody to tell", failureResIdOf(viewModel))
        assertEquals("and the machine is still idle", RouteState.Idle, viewModel.state.value)
    }

    /**
     * **Freeze holds the gate's clock and nothing else** (R12, R26): no refresh is asked while it is on,
     * the standing line keeps its figures, and it is forgotten when the mode ends.
     */
    @Test
    fun freezeStopsTheGateAndIsForgottenWithTheMode() = runTest {
        val engine = GateEngine()
        val viewModel = following(engine)
        val before = (viewModel.state.value as RouteState.Following).routes

        viewModel.setFrozen(true)
        viewModel.refresh(boatLater)

        val frozen = viewModel.state.value as RouteState.Following
        assertTrue("a frozen route is not refreshed", frozen.frozen)
        assertEquals("and its line and ladder are exactly what they were", before, frozen.routes)
        assertEquals("so the engine was never asked again", 1, engine.toldOrigins.size)

        viewModel.setFrozen(false)
        viewModel.refresh(boatLater)
        assertEquals(
            "and resuming hands the gate back: the refresh is asked for",
            2,
            engine.toldOrigins.size
        )

        viewModel.end()
        viewModel.beginDraft(origin)
        viewModel.preview(aim)
        viewModel.confirm()
        assertFalse("the freeze does not outlive the mode", (viewModel.state.value as RouteState.Following).frozen)
    }

    /**
     * **The ladder is capped for drawing and whole for the save** (R14, R25).
     *
     * The display keeps the configured oldest plus newest and drops the middle; the session is never
     * trimmed, so the all-scope save still writes every route the mode produced. The band runs 20 % at
     * the oldest to 80 % at the newest, spread over the stale set alone.
     */
    @Test
    fun theLadderKeepsTheConfiguredTwoEndsAndSpreadsItsBand() {
        val stale = (0 until 5).map { planWith(now + it) }

        val drawn = routeLadderForDrawing(stale, oldestNb = 1, latestNb = 3)

        assertEquals("the oldest and the newest of the configured counts, and nothing between", 4, drawn.size)
        assertEquals("the oldest one kept is the session's own first", stale.first(), drawn.first())
        assertEquals("and the newest one kept is the line just replaced", stale.last(), drawn.last())

        assertEquals(
            "the band's two ends",
            ROUTE_LADDER_ALPHA_OLDEST,
            routeLadderAlpha(index = 0, total = 4),
            1e-6f
        )
        assertEquals(
            ROUTE_LADDER_ALPHA_NEWEST,
            routeLadderAlpha(index = 3, total = 4),
            1e-6f
        )
        assertTrue(
            "and it rises monotonically, so the newest stale line is the brightest",
            routeLadderAlpha(index = 1, total = 4) > routeLadderAlpha(index = 0, total = 4)
        )
        assertEquals(
            "a ladder of one stands at the band's bright end",
            ROUTE_LADDER_ALPHA_NEWEST,
            routeLadderAlpha(index = 0, total = 1),
            1e-6f
        )
    }

    /**
     * **The refresh tick asks from the boat's own current reading** (R10), not from where the boat
     * stood when the following phase opened.
     *
     * `routeRefreshOrigin` takes a **provider**, and this is why: the boat is on its own line the
     * instant the standing answer lands, so a tick that captured that opening point once would answer
     * "nothing due" for the whole phase however far it wandered afterwards. The same tick, read live a
     * moment later, opens the gate from the boat's new position — which is the behaviour the captured
     * point silently broke.
     */
    @Test
    fun theRefreshAsksFromTheBoatsLiveReadingNotTheOneThePhaseOpenedOn() {
        val plan = planWith(now)

        var live: RoutePoint? = origin
        assertNull(
            "the boat standing on its own line as the answer lands: the moment has not come",
            routeRefreshOrigin(livePosition = { live }, standingPlan = plan, nowMs = now)
        )

        live = boatLater
        assertEquals(
            "and a later tick, read live, opens from where the boat is now",
            boatLater,
            routeRefreshOrigin(livePosition = { live }, standingPlan = plan, nowMs = now)
        )

        assertNull(
            "a seam with no reading asks from nothing",
            routeRefreshOrigin(livePosition = { null }, standingPlan = plan, nowMs = now)
        )
    }

    /**
     * **The ladder is painted on the book's own band** (R14): the oldest stale line at **20 %** and
     * the newest at **80 %** of full opacity.
     *
     * Under the shipped `route.line.transparencyPct=15` the old drawing multiplied that band by the
     * front line's own alpha and landed at 43/255 and 172/255 — neither of the book's two figures. The
     * absolute band reads 51/255 and 204/255 whatever the front line carries.
     */
    @Test
    fun theLadderIsPaintedOnTheBooksOwnBandNotAFractionOfTheFrontLinesAlpha() {
        assertEquals("the oldest stale line is the book's 20 %", 51, routeLadderDrawAlpha(index = 0, total = 2))
        assertEquals("and the newest is its 80 %", 204, routeLadderDrawAlpha(index = 1, total = 2))
        assertEquals("a ladder of one stands at the bright end", 204, routeLadderDrawAlpha(index = 0, total = 1))
        assertTrue(
            "and the band rises with the index, so the newest stale line is the brightest",
            routeLadderDrawAlpha(index = 0, total = 4) < routeLadderDrawAlpha(index = 3, total = 4)
        )
    }

    /**
     * **The save's set is pinned before the mode is ended** (R25).
     *
     * Ending the mode clears the session, so a save that read the live table afterwards would see an
     * already-written route as one nobody saved and **rewrite** it instead of renaming it — the outcome
     * turning on dispatch order. The snapshot is the copy the ending cannot reach, and this reads it
     * still holding its link after `end()` has cleared the session.
     */
    @Test
    fun theSaveSetIsPinnedBeforeTheModeEnds() = runTest {
        val viewModel = following(GateEngine())
        val front = (viewModel.state.value as RouteState.Following).plan
        viewModel.noteRouteSaved(front, "track-1")

        val set = viewModel.sessionSnapshot()
        viewModel.end()

        assertTrue("the mode's own session is gone", viewModel.sessionRoutes().isEmpty())
        assertEquals(
            "but the pinned set still carries the link, so the save renames rather than rewrites",
            listOf(SessionRoute(front, "track-1")),
            set
        )
    }

    /**
     * **One worker serves the asks and the refresh alike** (R4) — the sharing R4 names as *pinned by
     * test*.
     *
     * The single `Job`'s observable consequence is that cancelling the mode's call cancels the refresh
     * too: **Abort** and **ending** both reach the one worker, so a refresh held in flight is dropped
     * rather than left running on a field of its own. Give `refresh()` a second `Job` and both readings
     * go false — which is the revert this pins against.
     */
    @Test
    fun theAskAndTheRefreshShareOneWorker() = runTest {
        val abortGate = CompletableDeferred<Unit>()
        val abortEngine = GateEngine(gateOrigin = abortGate)
        val aborted = following(abortEngine)
        aborted.refresh(boatLater)
        assertEquals(
            "the refresh is running on the worker every call runs on",
            RouteRefresh.RUNNING,
            (aborted.state.value as RouteState.Following).refresh
        )
        aborted.abortRefresh()
        assertTrue(
            "Abort reached that one worker: the held call was cancelled",
            abortEngine.originCallCancelled
        )

        val endGate = CompletableDeferred<Unit>()
        val endEngine = GateEngine(gateOrigin = endGate)
        val ended = following(endEngine)
        ended.refresh(boatLater)
        ended.end()
        assertEquals(RouteState.Idle, ended.state.value)
        assertTrue(
            "and the mode's own ending reaches it too, so no second job outlives the mode",
            endEngine.originCallCancelled
        )
    }

    /**
     * **The session links a route to the track it became** (R25) — the link lives in the mode and dies
     * with it, so the track's own schema is untouched.
     */
    @Test
    fun theSessionRemembersTheTrackARouteBecame() = runTest {
        val viewModel = following(GateEngine())
        val front = (viewModel.state.value as RouteState.Following).plan

        assertNull("a route nobody saved has no track", viewModel.trackFor(front))
        assertEquals("and the session is the followed route alone", listOf(front), viewModel.sessionRoutes())

        viewModel.noteRouteSaved(front, "track-1")

        assertEquals("the write is remembered against the route", "track-1", viewModel.trackFor(front))
        viewModel.end()
        assertTrue("and the session dies with the mode", viewModel.sessionRoutes().isEmpty())
    }

    /**
     * **The name a route's track takes** (R25): the Tracks feature's auto-name with a fixed `Route `
     * prefix, and `· n/N` when one action writes several — fixed tokens, a name being data rather than
     * UI text.
     */
    @Test
    fun aRoutesTrackIsNamedWithItsInstantAndItsIndexOfTheSet() {
        val single = TrackFromCourse.routeTrackName(now)
        val inSet = TrackFromCourse.routeTrackName(now, index = 1, total = 3)

        assertTrue("the prefix is the fixed token, not a localised string", single.startsWith("Route "))
        assertFalse("a single save has no index to print", single.contains("·"))
        assertTrue("and one of a set says where it stands", inSet.endsWith(" · 1/3"))
        assertTrue("over the same instant", inSet.startsWith(single))
    }

    /** A plan dated [atMs], for the ladder's own readings. */
    private fun planWith(atMs: Long) = RoutePlan(
        start = origin,
        destination = aim,
        destinationMoved = false,
        points = listOf(origin, aim),
        legTimesSec = listOf(60.0),
        distanceM = SpatialOperations.haversine(
            LatLng(origin.latitude, origin.longitude),
            LatLng(aim.latitude, aim.longitude)
        ),
        durationSec = 60.0,
        computedAtMs = atMs
    )
}

/**
 * A foreign engine with the refresh's own knobs: it may veto a recompute, it may answer a failure, and
 * it may hold the origin's answer in flight. Everything else is the straight line.
 */
private class GateEngine(
    private val readyToRecompute: Boolean = true,
    /** The answer its **origin** entry point gives instead of a line, when a failure is being read. */
    private val originAnswer: RouteResult? = null,
    /** Held at the origin's entry point, so a refresh can be read while it is in flight. */
    private val gateOrigin: CompletableDeferred<Unit>? = null
) : RouteEngine {

    private val _state = MutableStateFlow<RouteEngineState>(RouteEngineState.Ready)
    override val state: StateFlow<RouteEngineState> = _state.asStateFlow()

    /** Every destination asked about, and every origin told — the two readings of one worker. */
    val destinations = ArrayList<RoutePoint>()
    val toldOrigins = ArrayList<RoutePoint>()

    /**
     * True once a held origin call was **cancelled** rather than answered — the shared worker's own
     * mark. A refresh on a `Job` of its own would never trip it, because the cancel port (`Abort`, the
     * mode's ending) only ever reaches the one worker.
     */
    var originCallCancelled: Boolean = false

    override suspend fun prepare(): RouteEngineState = RouteEngineState.Ready

    override suspend fun validatePoint(point: RoutePoint): RouteRefusalReason? = null

    override suspend fun onDestinationPositionChanged(newPosition: RoutePoint): RouteResult? {
        destinations += newPosition
        heldDestination = newPosition
        val origin = heldOrigin ?: return null
        return line(origin, newPosition)
    }

    override suspend fun onOriginPositionChanged(newPosition: RoutePoint): RouteResult? {
        toldOrigins += newPosition
        heldOrigin = newPosition
        // The **arming** call tells the origin and asks nothing, so it is never held: a gate across it
        // would block the frame the mode opens on. Only a refresh — a destination already held — is.
        val destination = heldDestination ?: return null
        try {
            gateOrigin?.await()
        } catch (cancelled: CancellationException) {
            originCallCancelled = true
            throw cancelled
        }
        originAnswer?.let { return it }
        return line(newPosition, destination)
    }

    override suspend fun isReadyToRecompute(): Boolean = readyToRecompute

    private var heldOrigin: RoutePoint? = null
    private var heldDestination: RoutePoint? = null

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
