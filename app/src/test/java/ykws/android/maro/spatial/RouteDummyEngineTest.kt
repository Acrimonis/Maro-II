package ykws.android.maro.spatial

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.RoutePoint

/**
 * The placeholder's own contract, pinned on the reworked seam: one computation declared, one update
 * answering the straight line at the placeholder's fixed 15 kn fiction, and a repair that is a no-op
 * — the dummy reads no water, so it moves nothing and refuses nothing.
 */
class RouteDummyEngineTest {

    private val origin = RoutePoint(43.5000, 7.0000)
    private val aim = RoutePoint(43.5100, 7.0500)

    private val metres = SpatialOperations.haversine(
        LatLng(origin.latitude, origin.longitude),
        LatLng(aim.latitude, aim.longitude)
    )

    /** The placeholder's own constant, restated here so the test derives its expectation itself. */
    private val paceKn = 15.0

    private fun declarations(engine: RouteDummyEngine): List<RouteComputation> {
        val result = engine.routesToCompute(origin, aim)
        assertTrue("the dummy declares, it never refuses", result is RouteDeclarations.Available)
        return (result as RouteDeclarations.Available).computations
    }

    /** Subscribes, starts the lookup, and awaits the terminal update. */
    private fun terminal(engine: RouteDummyEngine, computationId: RouteId): RouteUpdate = runBlocking {
        val subscribed = CompletableDeferred<Unit>()
        val done = CompletableDeferred<RouteUpdate?>()
        val collector = launch(Dispatchers.Default) {
            engine.updates
                .onStart { subscribed.complete(Unit) }
                .collect { if (it.nextStage == null) { done.complete(it); return@collect } }
        }
        subscribed.await()
        engine.startLookup(computationId)
        val update = withTimeout(5_000) { done.await() }
        collector.cancel()
        update ?: error("the dummy answers one update")
    }

    @Test
    fun itDeclaresExactlyOneComputation() {
        assertEquals(1, declarations(RouteDummyEngine()).size)
    }

    @Test
    fun theOneUpdateAnswersTheStraightLineAtTheFixedPace() {
        val engine = RouteDummyEngine()
        val computation = declarations(engine).first()

        val update = terminal(engine, computation.id)

        assertNull("the dummy has no stage to report", update.stageDone)
        assertNull("and none to promise", update.nextStage)
        assertNull("and no reason on success", update.reason)
        val result = update.result ?: error("the dummy answers a route")
        assertEquals("the line is the two ends in the direction of travel", listOf(origin, aim), result.points)
        assertEquals("the length is the great-circle distance", metres, result.distanceM, 1e-9)
        val seconds = metres / Units.knotsToMps(paceKn)
        assertEquals("the time is that distance at the fixed fiction", listOf(seconds), result.legTimesSec)
        assertEquals(seconds, result.durationSec, 1e-9)
        assertTrue("a straight line crosses nothing", result.forcedCrossingZoneNames.isEmpty())
    }
}
