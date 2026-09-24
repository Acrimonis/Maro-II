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
 * The avoidance stand-in's own contract, pinned — because it is the second row of the harness and the
 * one thing that tells it apart from the dummy is the pace its straight line is timed at.
 *
 * What is asserted is deliberately small and exact: the line is the two ends it was told, in the
 * direction of travel, its length is the great-circle distance between them, and its time is that length
 * at the pace the test injects — a value of the test's own choosing, not the dummy's fixed 15 kn fiction
 * and not the app property the defect once read — so the assertion pins the injected behaviour. Nothing
 * was resolved and nothing crosses, and no point is ever refused.
 */
class RouteAvoidEngineTest {

    private val origin = RoutePoint(43.5000, 7.0000)
    private val aim = RoutePoint(43.5100, 7.0500)

    /** The pace the test injects, chosen on purpose: the expectation derives from this, never from a property. */
    private val paceKn = 12.0

    private val metres = SpatialOperations.haversine(
        LatLng(origin.latitude, origin.longitude),
        LatLng(aim.latitude, aim.longitude)
    )

    /** A fresh avoid engine priced at the injected pace, so no test constructs the engine any other way. */
    private fun newEngine() = RouteAvoidEngine(paceKn = { paceKn })

    private fun success(result: RouteResult?): RouteResult.Success {
        assertTrue("the avoid engine answers a route, never a refusal", result is RouteResult.Success)
        return result as RouteResult.Success
    }

    /** Ready before anything is asked, exactly like the dummy: no world is read at all. */
    @Test
    fun theStateIsReadyTheMomentItIsBuilt() {
        assertTrue(newEngine().state.value.ready)
    }

    @Test
    fun prepareAnswersReadyToo() = runTest {
        val engine = newEngine()

        assertEquals(RouteEngineState.Ready, engine.prepare())
        assertFalse(
            "and it never becomes an engine that refuses",
            engine.state.value is RouteEngineState.Unavailable
        )
    }

    /** It judges nothing: every point is usable water as far as a straight line can tell. */
    @Test
    fun noPointIsEverRefused() = runTest {
        val engine = newEngine()

        assertNull(engine.validatePoint(origin))
        assertNull(engine.validatePoint(aim))
    }

    /** Nothing to wait for, so the refresh's veto never fires while this engine is installed. */
    @Test
    fun itIsAlwaysReadyToRecompute() = runTest {
        assertTrue(newEngine().isReadyToRecompute())
    }

    /** The session's own shape: an end told alone answers nothing. */
    @Test
    fun theArmingCallAnswersNoRouteBecauseNoDestinationIsHeldYet() = runTest {
        val engine = newEngine()

        assertNull("the origin alone is not a route", engine.onOriginPositionChanged(origin))
        assertNull("and a destination with no origin is not one either", newEngine().onDestinationPositionChanged(aim))
    }

    @Test
    fun theLineIsExactlyTheTwoEndsItWasTold() = runTest {
        val engine = newEngine()
        engine.onOriginPositionChanged(origin)

        val route = success(engine.onDestinationPositionChanged(aim))

        assertEquals(listOf(origin, aim), route.points)
    }

    /** A later aim moves the destination and holds the origin: the session the seam is made of. */
    @Test
    fun theOriginIsHeldWhileTheDestinationMoves() = runTest {
        val engine = newEngine()
        engine.onOriginPositionChanged(origin)
        engine.onDestinationPositionChanged(aim)

        val moved = success(engine.onDestinationPositionChanged(origin))

        assertEquals(listOf(origin, origin), moved.points)
        assertEquals(0.0, moved.distanceM, 1e-9)
    }

    /** And the reverse holds while the following phase moves the origin: the destination stands. */
    @Test
    fun theDestinationIsHeldWhileTheOriginMoves() = runTest {
        val engine = newEngine()
        engine.onOriginPositionChanged(origin)
        engine.onDestinationPositionChanged(aim)

        val later = RoutePoint(43.5050, 7.0030)
        val route = success(engine.onOriginPositionChanged(later))

        assertEquals(listOf(later, aim), route.points)
    }

    @Test
    fun theDistanceIsTheGreatCircleBetweenThem() = runTest {
        val engine = newEngine()
        engine.onOriginPositionChanged(origin)

        assertEquals(metres, success(engine.onDestinationPositionChanged(aim)).distanceM, 1e-9)
    }

    /**
     * **The injected pace, not the dummy's fiction**: every leg is timed at the provider the test handed
     * in, so a changed pace reaches the answer and the dummy's constant stays the dummy's alone.
     */
    @Test
    fun theTimeIsThatDistanceAtThePaceInForce() = runTest {
        val engine = newEngine()
        engine.onOriginPositionChanged(origin)

        val route = success(engine.onDestinationPositionChanged(aim))

        val seconds = metres / Units.knotsToMps(paceKn)
        assertEquals(listOf(seconds), route.legTimesSec)
        assertEquals(seconds, route.durationSec, 1e-9)
        assertEquals("the total is the legs' own sum", route.legTimesSec.sum(), route.durationSec, 1e-9)
    }

    @Test
    fun nothingWasResolvedAndNothingCrosses() = runTest {
        val engine = newEngine()
        engine.onOriginPositionChanged(origin)

        val route = success(engine.onDestinationPositionChanged(aim))

        assertFalse("the aim is where the line ends, so the pin is where the user dragged", route.destinationMoved)
        assertTrue(
            "a straight line enters nothing, so no crossing is reported",
            route.forcedCrossingZoneNames.isEmpty()
        )
    }
}
