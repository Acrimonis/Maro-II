package ykws.android.maro.data.route

import org.junit.Assert.assertEquals
import org.junit.Test
import ykws.android.maro.data.model.titleOrder

/**
 * A selector's entries, in the order it offers them: the fixed ones first, in their declared order — the
 * caller's fallback being the first of them — then the flagged ones by title. The order is a parameter, so
 * the rule is pinned here without the screen and without `maro.properties`.
 */
class RouteEndEntriesTest {

    @Test
    fun `the fixed entries lead in their declared order, whatever the flagged ones are called`() {
        val fixed = listOf(RouteEndSelection.CurrentPosition, RouteEndSelection.MarkerPosition)
        val flagged = listOf(
            RouteEndSelection.Marker("b") to "Aiguille",
            RouteEndSelection.Marker("a") to "Zephyr"
        )

        assertEquals(
            listOf(
                RouteEndSelection.CurrentPosition,
                RouteEndSelection.MarkerPosition,
                RouteEndSelection.Marker("b"),
                RouteEndSelection.Marker("a")
            ),
            routeEndEntries(fixed, flagged, order = compareBy { it })
        )
    }

    @Test
    fun `the flagged entries take the rule they are handed`() {
        val flagged = listOf(
            RouteEndSelection.Marker("le") to "Le Port",
            RouteEndSelection.Marker("ai") to "Aiguille"
        )

        assertEquals(
            listOf(RouteEndSelection.Marker("ai"), RouteEndSelection.Marker("le")),
            routeEndEntries(emptyList(), flagged, order = compareBy { it })
        )
    }

    @Test
    fun `the shipped rule files Le Port under P`() {
        val flagged = listOf(
            RouteEndSelection.Marker("le") to "Le Port",
            RouteEndSelection.Marker("ai") to "Aiguille",
            RouteEndSelection.Marker("ze") to "Zephyr"
        )

        assertEquals(
            listOf(RouteEndSelection.Marker("ai"), RouteEndSelection.Marker("le"), RouteEndSelection.Marker("ze")),
            routeEndEntries(emptyList(), flagged, order = titleOrder)
        )
    }

    @Test
    fun `a destination with no flagged marker still offers its fixed entry`() {
        assertEquals(
            listOf(RouteEndSelection.MarkerPosition),
            routeEndEntries(listOf(RouteEndSelection.MarkerPosition), emptyList())
        )
    }
}
