package ykws.android.maro.spatial

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.data.model.RouteResult

/**
 * The placeholder's own contract, pinned — because everything above it (the preview, the panel, the
 * trip figure, the save) is now read through answers only this engine produces.
 *
 * What is asserted is deliberately small and exact: the line is the two points asked for, its length is
 * the great-circle distance between them, its time is that length at the pace the caller planned at,
 * nothing was resolved and nothing crosses. A test that asserted less would let the dummy quietly
 * become an engine claiming readings it never took.
 */
class RouteDummyEngineTest {

    private val start = RoutePoint(43.5000, 7.0000)
    private val aim = RoutePoint(43.5100, 7.0500)
    private val paceKn = 12.0

    private val metres = SpatialOperations.haversine(
        LatLng(start.latitude, start.longitude),
        LatLng(aim.latitude, aim.longitude)
    )

    private fun success(result: RouteResult): RouteResult.Success {
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

    @Test
    fun theLineIsExactlyTheTwoPointsAskedFor() = runTest {
        val route = success(RouteDummyEngine().route(start, aim, paceKn))

        assertEquals(listOf(start, aim), route.points)
    }

    @Test
    fun theDistanceIsTheGreatCircleBetweenThem() = runTest {
        val route = success(RouteDummyEngine().route(start, aim, paceKn))

        assertEquals(metres, route.distanceM, 1e-9)
    }

    @Test
    fun theTimeIsThatDistanceAtThePaceTheCallerPlannedAt() = runTest {
        val route = success(RouteDummyEngine().route(start, aim, paceKn))

        val seconds = metres / Units.knotsToMps(paceKn)
        assertEquals(listOf(seconds), route.legTimesSec)
        assertEquals(seconds, route.durationSec, 1e-9)
        assertEquals("the total is the legs' own sum", route.legTimesSec.sum(), route.durationSec, 1e-9)
    }

    /** Twice the pace, half the time — the one relation the trip figure's arithmetics rest on. */
    @Test
    fun aFasterPaceShortensTheLineInTimeOnly() = runTest {
        val engine = RouteDummyEngine()
        val slow = success(engine.route(start, aim, paceKn))
        val fast = success(engine.route(start, aim, paceKn * 2.0))

        assertEquals(slow.distanceM, fast.distanceM, 0.0)
        assertEquals(slow.durationSec / 2.0, fast.durationSec, 1e-9)
    }

    /**
     * A pace of zero is the caller's number rather than the engine's problem: the answer is a zero
     * time, not an infinite one and not an exception. The contract has no refusal for "the pace is
     * nonsense", so inventing one here would be this class writing policy it was not given.
     */
    @Test
    fun aPaceOfZeroAnswersNoTimeRatherThanAnInfinity() = runTest {
        val route = success(RouteDummyEngine().route(start, aim, 0.0))

        assertEquals(0.0, route.durationSec, 0.0)
        assertEquals(listOf(0.0), route.legTimesSec)
        assertEquals("the geometry is still answered", metres, route.distanceM, 1e-9)
    }

    @Test
    fun nothingWasResolvedAndNothingCrosses() = runTest {
        val route = success(RouteDummyEngine().route(start, aim, paceKn))

        assertFalse("the aim is where the line ends, so the pin is where the user dragged", route.destinationMoved)
        assertTrue(
            "a straight line enters nothing, so no crossing is reported",
            route.forcedCrossingZoneNames.isEmpty()
        )
    }
}
