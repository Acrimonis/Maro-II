package ykws.android.maro.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.data.model.RouteSlowLimit

/**
 * **The Speed limits line's own reading** — [routeSlowLimitEntries] turns a plan's per-limit seconds into
 * whole seconds, rounds to the second, drops a zero, and keeps the 300 m band apart.
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
    fun roundsToTheSecondAndDropsAZero() {
        val entries = routeSlowLimitEntries(
            plan(
                listOf(
                    RouteSlowLimit(5.0, 600.4, false),
                    RouteSlowLimit(5.0, 300.0, true),
                    RouteSlowLimit(10.0, 30.2, false),
                    RouteSlowLimit(10.0, 0.4, false)
                )
            )
        )
        assertEquals("the zero entry is dropped", 3, entries.size)
        assertEquals(600, entries[0].seconds)
        assertEquals(300, entries[1].seconds)
        assertTrue("the band entry keeps its own face", entries[1].isBand)
        assertEquals(30, entries[2].seconds)
    }

    @Test
    fun emptyWhenNothingSlowedTheRoute() {
        assertEquals(0, routeSlowLimitEntries(plan(emptyList())).size)
    }

}
