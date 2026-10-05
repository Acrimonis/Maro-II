package ykws.android.maro.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.data.model.RouteSlowLimit

/**
 * **The Speed limits line's own reading** — [routeSlowLimitEntries] turns a plan's per-limit seconds into
 * whole minutes, drops a sub-minute entry, and keeps the 300 m band apart.
 */
class RouteSlowLimitTest {

    private fun plan(slow: List<RouteSlowLimit>) = RoutePlan(
        start = RoutePoint(43.0, 7.0),
        destination = RoutePoint(43.1, 7.1),
        destinationMoved = false,
        points = listOf(RoutePoint(43.0, 7.0), RoutePoint(43.1, 7.1)),
        legTimesSec = listOf(1.0),
        distanceM = 1000.0,
        durationSec = 60.0,
        computedAtMs = 0L,
        slowLimitSeconds = slow
    )

    @Test
    fun wholeMinutesAndSubMinuteDropped() {
        val entries = routeSlowLimitEntries(
            plan(
                listOf(
                    RouteSlowLimit(5.0, 600.0, false),
                    RouteSlowLimit(5.0, 300.0, true),
                    RouteSlowLimit(10.0, 30.0, false)
                )
            )
        )
        assertEquals("the sub-minute entry is dropped", 2, entries.size)
        assertEquals(10, entries[0].minutes)
        assertEquals(5, entries[1].minutes)
        assertTrue("the band entry keeps its own face", entries[1].isBand)
    }

    @Test
    fun emptyWhenNothingSlowedTheRoute() {
        assertEquals(0, routeSlowLimitEntries(plan(emptyList())).size)
    }
}
