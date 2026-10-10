package ykws.android.maro.ui.map

import org.junit.Assert.assertEquals
import org.junit.Test
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.ListSortState
import ykws.android.maro.data.model.markers.MarkerGeometry
import ykws.android.maro.data.model.markers.MarkerOrigin
import ykws.android.maro.data.model.markers.UserMarker

/**
 * The menu's marker ordering: [sortMarkers] is the one pure home the management list and the menu
 * chevron world both read, so the chevron walks the list's own order — the `origin` custom key, and the
 * `updatedAtEpochMs` fallback for a key the marker type does not carry (2026-10-10).
 */
class MenuMarkerOrderTest {

    private fun marker(
        id: String,
        origin: MarkerOrigin = MarkerOrigin.USER,
        updatedAtMs: Long = 0L
    ) = UserMarker(
        id = id,
        name = id,
        geometry = MarkerGeometry.Pin(LatLng(0.0, 0.0)),
        origin = origin,
        updatedAtEpochMs = updatedAtMs
    )

    @Test
    fun `the menu order follows the marker list's origin sort`() {
        val markers = listOf(
            marker("auto", origin = MarkerOrigin.IDLE_AUTO),
            marker("user", origin = MarkerOrigin.USER)
        )
        val byOriginAscending = ListSortState(descending = false, customFieldKey = "origin")
        val byOriginDescending = byOriginAscending.copy(descending = true)

        // The chevron's first id is the head of that order.
        assertEquals(listOf("user", "auto"), sortMarkers(markers, byOriginAscending).map { it.id })
        assertEquals("auto", sortMarkers(markers, byOriginDescending).map { it.id }.first())
        assertEquals(listOf("auto", "user"), sortMarkers(markers, byOriginDescending).map { it.id })
    }

    @Test
    fun `a key the marker type does not carry falls back to updatedAtEpochMs`() {
        val markers = listOf(
            marker("older", updatedAtMs = 1_000L),
            marker("newer", updatedAtMs = 2_000L)
        )
        val unknownKeyAscending = ListSortState(descending = false, customFieldKey = "notAMarkerKey")

        assertEquals(
            listOf("older", "newer"),
            sortMarkers(markers, unknownKeyAscending).map { it.id }
        )
    }
}
