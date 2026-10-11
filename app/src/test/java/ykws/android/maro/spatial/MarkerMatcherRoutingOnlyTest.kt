package ykws.android.maro.spatial

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.MarkerGeometry
import ykws.android.maro.data.model.markers.UserMarker

/**
 * A `routingOnly` marker never names where the boat is: [MarkerMatcher.countsForWhereAmI] is the one
 * exclusion [MarkerMatcher.resolveAllMarkers] applies before any match is resolved, so a flagged marker
 * is dropped from the where-am-i result while a plain one is kept. The predicate is pure, so this needs
 * no [CoastlineSpatialIndex] and no Android runtime.
 */
class MarkerMatcherRoutingOnlyTest {

    private fun marker(routingOnly: Boolean) = UserMarker(
        id = "m",
        name = "m",
        geometry = MarkerGeometry.Pin(LatLng(43.0, 7.0)),
        routingOnly = routingOnly
    )

    @Test
    fun `a routing-only marker is dropped from the where-am-i candidate set`() {
        assertFalse(MarkerMatcher.countsForWhereAmI(marker(routingOnly = true)))
    }

    @Test
    fun `a plain marker is kept`() {
        assertTrue(MarkerMatcher.countsForWhereAmI(marker(routingOnly = false)))
    }
}
