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

    /**
     * **The wrap law** — a zone shares the band's first line only for an odd count of three or more; a
     * single zone and every even count leave the band alone on the first line, the zones then in pairs.
     */
    @Test
    fun rowsWrapByZoneParity() {
        fun band() = RouteSlowLimitEntry(0.0, 5, true)
        fun zone(kn: Double) = RouteSlowLimitEntry(kn, 2, false)
        fun shape(rows: List<List<RouteSlowLimitEntry>>) =
            rows.map { row -> row.map { if (it.isBand) "B" else it.limitKn.toInt().toString() } }

        assertEquals(
            "no zones leaves the band alone",
            listOf(listOf("B")),
            shape(routeSlowLimitRows(listOf(band())))
        )
        assertEquals(
            "a lone zone stays on its own line",
            listOf(listOf("B"), listOf("5")),
            shape(routeSlowLimitRows(listOf(band(), zone(5.0))))
        )
        assertEquals(
            "two zones leave the band alone over one full line",
            listOf(listOf("B"), listOf("5", "10")),
            shape(routeSlowLimitRows(listOf(band(), zone(5.0), zone(10.0))))
        )
        assertEquals(
            "three zones pair the band with the first",
            listOf(listOf("B", "5"), listOf("10", "15")),
            shape(routeSlowLimitRows(listOf(band(), zone(5.0), zone(10.0), zone(15.0))))
        )
        assertEquals(
            "four zones leave the band alone over two full lines",
            listOf(listOf("B"), listOf("5", "10"), listOf("15", "20")),
            shape(routeSlowLimitRows(listOf(band(), zone(5.0), zone(10.0), zone(15.0), zone(20.0))))
        )
        assertEquals(
            "five zones pair the band with the first over full lines",
            listOf(listOf("B", "5"), listOf("10", "15"), listOf("20", "25")),
            shape(
                routeSlowLimitRows(
                    listOf(band(), zone(5.0), zone(10.0), zone(15.0), zone(20.0), zone(25.0))
                )
            )
        )
    }

    @Test
    fun rowsEmptyWithoutEntries() {
        assertEquals(0, routeSlowLimitRows(emptyList()).size)
    }
}
