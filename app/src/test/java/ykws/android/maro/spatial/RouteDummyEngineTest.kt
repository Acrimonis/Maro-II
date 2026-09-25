package ykws.android.maro.spatial

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.data.model.RouteResult

/**
 * The placeholder's own contract, pinned — because everything above it (the aim, both phases, the
 * refresh, the ladder, the trip figure, the save) is read through answers only this engine produces.
 *
 * What is asserted is deliberately small and exact: the line is the two ends it was told, in the
 * direction of travel, its length is the great-circle distance between them, its time is that length at
 * the placeholder's own **fixed 15 kn fiction** (R28), nothing was resolved and nothing crosses, and no
 * point is ever refused. A test that asserted less would let the dummy quietly become an engine
 * claiming readings it never took.
 *
 * **The pace is no longer an input.** The two entry points take a position and nothing else, so the
 * app's own free-water pace cannot move a dummy route — which is exactly what R28 asks for and why the
 * old "twice the pace, half the time" reading has no counterpart here.
 */
class RouteDummyEngineTest {

    private val origin = RoutePoint(43.5000, 7.0000)
    private val aim = RoutePoint(43.5100, 7.0500)

    /** The placeholder's own constant, restated here so the test derives its expectation itself. */
    private val paceKn = 15.0

    private val metres = SpatialOperations.haversine(
        LatLng(origin.latitude, origin.longitude),
        LatLng(aim.latitude, aim.longitude)
    )

    private fun success(result: RouteResult?): RouteResult.Success {
        assertTrue("the dummy answers a route, never a refusal", result is RouteResult.Success)
        return result as RouteResult.Success
    }

    /**
     * **Ready before anything is asked.** The toggle gates on this state, and the whole point of the
     * placeholder is that the mode is usable with no world loaded at all — so a `NotReady` here would
     * be the feature switched off, which is the one thing this engine must not do.
     */
    @Test
    fun theStateIsReadyTheMomentItIsBuilt() {
        assertTrue(RouteDummyEngine().state.value.ready)
    }

    @Test
    fun prepareAnswersReadyToo() = runTest {
        val engine = RouteDummyEngine()

        assertEquals(RouteEngineState.Ready, engine.prepare())
        assertFalse(
            "and it never becomes an engine that refuses",
            engine.state.value is RouteEngineState.Unavailable
        )
    }

    /** It judges nothing: every point is usable water as far as a straight line can tell (R6). */
    @Test
    fun noPointIsEverRefused() = runTest {
        val engine = RouteDummyEngine()

        assertNull(engine.validatePoint(origin))
        assertNull(engine.validatePoint(aim))
    }

    /** Nothing to wait for, so the refresh's veto never fires while the placeholder ships (R11). */
    @Test
    fun itIsAlwaysReadyToRecompute() = runTest {
        assertTrue(RouteDummyEngine().isReadyToRecompute())
    }

    /**
     * **The session's own shape: an end told alone answers nothing.**
     *
     * The arming call is exactly this — the origin told before any aim exists — and a route there would
     * be a line to nowhere. The destination's own entry point answers nothing until an origin is held,
     * which is the same rule read from the other side.
     */
    @Test
    fun theArmingCallAnswersNoRouteBecauseNoDestinationIsHeldYet() = runTest {
        val engine = RouteDummyEngine()

        assertNull("the origin alone is not a route", engine.onOriginPositionChanged(origin))
        assertNull("and a destination with no origin is not one either", RouteDummyEngine().onDestinationPositionChanged(aim))
    }

    @Test
    fun theLineIsExactlyTheTwoEndsItWasTold() = runTest {
        val engine = RouteDummyEngine()
        engine.onOriginPositionChanged(origin)

        val route = success(engine.onDestinationPositionChanged(aim))

        assertEquals(listOf(origin, aim), route.points)
    }

    /** A later aim moves the destination and holds the origin: the session the seam is made of. */
    @Test
    fun theOriginIsHeldWhileTheDestinationMoves() = runTest {
        val engine = RouteDummyEngine()
        engine.onOriginPositionChanged(origin)
        engine.onDestinationPositionChanged(aim)

        val moved = success(engine.onDestinationPositionChanged(origin))

        assertEquals(listOf(origin, origin), moved.points)
        assertEquals(0.0, moved.distanceM, 1e-9)
    }

    /** And the reverse holds while the following phase moves the origin: the destination stands. */
    @Test
    fun theDestinationIsHeldWhileTheOriginMoves() = runTest {
        val engine = RouteDummyEngine()
        engine.onOriginPositionChanged(origin)
        engine.onDestinationPositionChanged(aim)

        val later = RoutePoint(43.5050, 7.0030)
        val route = success(engine.onOriginPositionChanged(later))

        assertEquals(listOf(later, aim), route.points)
    }

    @Test
    fun theDistanceIsTheGreatCircleBetweenThem() = runTest {
        val engine = RouteDummyEngine()
        engine.onOriginPositionChanged(origin)

        assertEquals(metres, success(engine.onDestinationPositionChanged(aim)).distanceM, 1e-9)
    }

    /**
     * **The placeholder's own fiction: 15 kn on every leg**, whatever the app's pace setting says
     * (R28). Nothing about the caller can move it, which is the whole point of the constant.
     */
    @Test
    fun theTimeIsThatDistanceAtThePlaceholdersOwnFixedPace() = runTest {
        val engine = RouteDummyEngine()
        engine.onOriginPositionChanged(origin)

        val route = success(engine.onDestinationPositionChanged(aim))

        val seconds = metres / Units.knotsToMps(paceKn)
        assertEquals(listOf(seconds), route.legTimesSec)
        assertEquals(seconds, route.durationSec, 1e-9)
        assertEquals("the total is the legs' own sum", route.legTimesSec.sum(), route.durationSec, 1e-9)
    }

    @Test
    fun nothingWasResolvedAndNothingCrosses() = runTest {
        val engine = RouteDummyEngine()
        engine.onOriginPositionChanged(origin)

        val route = success(engine.onDestinationPositionChanged(aim))

        assertFalse("the aim is where the line ends, so the pin is where the user dragged", route.destinationMoved)
        assertTrue(
            "a straight line enters nothing, so no crossing is reported",
            route.forcedCrossingZoneNames.isEmpty()
        )
    }
}
