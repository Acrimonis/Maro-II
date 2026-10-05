package ykws.android.maro.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ykws.android.maro.data.model.ListFilter
import ykws.android.maro.data.track.TrackSummary

/**
 * The two chrome flags' scope resolution (S4, D7) and the menu's per-kind referential helpers (S5): the
 * tracks referential holds recorded tracks alone and the routes referential saved routes alone, so each
 * list's chevron and count mirror their own kind and never the other's.
 */
class ListScopeAndCountsTest {

    private fun summary(
        id: String,
        route: Boolean = false,
        pinned: Boolean = false,
        startTimeMs: Long = 0L
    ) = TrackSummary(
        id = id,
        name = id,
        startTimeMs = startTimeMs,
        endTimeMs = startTimeMs + 1_000L,
        pinned = pinned,
        route = route
    )

    private val today = 1_000_000_000_000L

    @Test
    fun theScopeFollowsWhicheverFlagStands() {
        assertEquals(ListScope.ROUTES, listScopeOf(showTrackHistory = false, showRouteHistory = true))
        assertEquals(ListScope.TRACKS, listScopeOf(showTrackHistory = true, showRouteHistory = false))
        assertNull(listScopeOf(showTrackHistory = false, showRouteHistory = false))
        // Both standing is a defect the resolver collapses to routes rather than a drawn state: no opener
        // ever leaves the two flags set together (S4's mutual-exclusion guard).
        assertEquals(ListScope.ROUTES, listScopeOf(showTrackHistory = true, showRouteHistory = true))
    }

    @Test
    fun theTracksReferentialExcludesRoutesWhereeverThePinSays() {
        val all = listOf(
            summary("track-open", startTimeMs = 3_000L),
            summary("track-pinned", pinned = true, startTimeMs = 2_000L),
            summary("route-open", route = true, startTimeMs = 1_000L),
            summary("route-pinned", route = true, pinned = true, startTimeMs = 0L)
        )

        val ids = menuTrackIdsOf(all, ListFilter(), today)
        assertEquals(listOf("track-open", "track-pinned"), ids)
        assertEquals(2, trackMapVisibleCountOf(all, ListFilter(), today))
    }

    @Test
    fun theRoutesReferentialHoldsRoutesAlone() {
        val all = listOf(
            summary("track-open", startTimeMs = 3_000L),
            summary("track-pinned", pinned = true, startTimeMs = 2_000L),
            summary("route-open", route = true, startTimeMs = 1_000L),
            summary("route-pinned", route = true, pinned = true, startTimeMs = 0L)
        )

        val ids = menuRouteIdsOf(all, ListFilter(), today)
        assertEquals(listOf("route-open", "route-pinned"), ids)
        assertEquals(2, routeMapVisibleCountOf(all, ListFilter(), today))
    }

    @Test
    fun eachReferentialReadsItsOwnKindsMapFilter() {
        // A filter that excludes the dated items trims the routes referential without touching the tracks
        // one, and vice versa — the two counts move independently (S5, D4).
        val all = listOf(
            summary("track-in", startTimeMs = today),
            summary("track-out", startTimeMs = 0L),
            summary("route-in", route = true, startTimeMs = today),
            summary("route-out", route = true, startTimeMs = 0L)
        )
        val dateOnly = ListFilter(mapOf("dateRange" to "LAST_7_DAYS"))

        assertEquals(listOf("track-in", "track-out"), menuTrackIdsOf(all, ListFilter(), today))
        assertEquals(listOf("track-in"), menuTrackIdsOf(all, dateOnly, today))
        assertEquals(listOf("route-in", "route-out"), menuRouteIdsOf(all, ListFilter(), today))
        assertEquals(listOf("route-in"), menuRouteIdsOf(all, dateOnly, today))
    }
}
