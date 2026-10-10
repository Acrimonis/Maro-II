package ykws.android.maro.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ykws.android.maro.data.model.ListFilter
import ykws.android.maro.data.model.ListSortState
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
        startTimeMs: Long = 0L,
        distanceNm: Float = 0f
    ) = TrackSummary(
        id = id,
        name = id,
        startTimeMs = startTimeMs,
        endTimeMs = startTimeMs + 1_000L,
        distanceNm = distanceNm,
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

        val ids = menuTrackIdsOf(all, ListFilter(), ListSortState(), today)
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

        val ids = menuRouteIdsOf(all, ListFilter(), ListSortState(), today)
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

        assertEquals(listOf("track-in", "track-out"), menuTrackIdsOf(all, ListFilter(), ListSortState(), today))
        assertEquals(listOf("track-in"), menuTrackIdsOf(all, dateOnly, ListSortState(), today))
        assertEquals(listOf("route-in", "route-out"), menuRouteIdsOf(all, ListFilter(), ListSortState(), today))
        assertEquals(listOf("route-in"), menuRouteIdsOf(all, dateOnly, ListSortState(), today))
    }

    @Test
    fun theTracksReferentialFollowsTheTracksListSort() {
        // The chevron opens the head of the map-filtered set **in the tracks list's own order**: the
        // distance key is the custom sort the list dispatches, read through the one shared home.
        val all = listOf(
            summary("near", startTimeMs = 3_000L, distanceNm = 1f),
            summary("far", startTimeMs = 1_000L, distanceNm = 9f)
        )
        val ascending = ListSortState(descending = false, customFieldKey = "distanceNm")
        val descending = ascending.copy(descending = true)

        assertEquals(listOf("near", "far"), menuTrackIdsOf(all, ListFilter(), ascending, today))
        assertEquals(listOf("far", "near"), menuTrackIdsOf(all, ListFilter(), descending, today))
        assertEquals("far", menuTrackIdsOf(all, ListFilter(), descending, today).first())
    }

    @Test
    fun theRoutesReferentialFollowsTheRoutesListSort() {
        val all = listOf(
            summary("r-near", route = true, startTimeMs = 3_000L, distanceNm = 2f),
            summary("r-far", route = true, startTimeMs = 1_000L, distanceNm = 8f)
        )
        val ascending = ListSortState(descending = false, customFieldKey = "distanceNm")

        assertEquals(listOf("r-near", "r-far"), menuRouteIdsOf(all, ListFilter(), ascending, today))
        assertEquals("r-near", menuRouteIdsOf(all, ListFilter(), ascending, today).first())
    }
}
