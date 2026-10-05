package ykws.android.maro.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.track.TrackSummary

/**
 * The retired **Kind** axis (S3, D2): the shared track axes keep `dateRange`, `pinned` and `position`,
 * the `route` axis is gone from the spec, and `matchesFilter` no longer reads it — so a persisted
 * `route=…` entry cannot keep filtering a kind whose list is now kind-locked by construction.
 */
class ListFilterKindRetirementTest {

    private fun summary(id: String, route: Boolean) = TrackSummary(
        id = id,
        name = id,
        startTimeMs = 0L,
        endTimeMs = 1_000L,
        pinned = false,
        route = route
    )

    @Test
    fun theTrackAxesCarryNoKindAxis() {
        val keys = trackFilterAxes().map { it.key }

        assertEquals(listOf("dateRange", "pinned", "position"), keys)
        assertFalse("the Kind axis is retired", keys.contains("route"))
    }

    @Test
    fun aPersistedRouteAxisIsStrippedAndLeavesItsSiblingsAlone() {
        val parsed = ListFilter.parse("route=ROUTES;pinned=PINNED;position=WATER")

        assertTrue("the raw parse still holds the retired axis", parsed.axes.containsKey("route"))

        val stripped = parsed.withoutAxis("route")
        assertFalse(stripped.axes.containsKey("route"))
        assertEquals("PINNED", stripped.axes["pinned"])
        assertEquals("WATER", stripped.axes["position"])
    }

    @Test
    fun withoutAxisIsAPureNoOpWhenTheAxisIsAbsent() {
        val filter = ListFilter(mapOf("pinned" to "PINNED"))

        assertEquals(filter, filter.withoutAxis("route"))
    }

    @Test
    fun matchesFilterIgnoresARouteAxisForBothKinds() {
        // A `route=…` entry left in a decoded map is unknown to `matchesFilter`, whose `else` arm passes
        // it: both a recorded track and a route survive, which is what lets the load-time strip be the
        // only defence and the filter itself never re-introduce the retired axis.
        val legacy = ListFilter(mapOf("route" to "ROUTES"))

        assertTrue(summary("track", route = false).matchesFilter(legacy, todayMidnightMs = 0L))
        assertTrue(summary("route", route = true).matchesFilter(legacy, todayMidnightMs = 0L))
    }
}
