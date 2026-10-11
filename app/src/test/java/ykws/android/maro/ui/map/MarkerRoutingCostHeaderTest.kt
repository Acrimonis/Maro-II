package ykws.android.maro.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.MarkerGeometry
import ykws.android.maro.data.model.markers.MarkerOrigin
import ykws.android.maro.data.model.markers.ROUTING_COST_BLOCKED
import ykws.android.maro.data.model.markers.UserMarker

/**
 * The route-role, routing-only and cost marks of the marker card header — [coordinateHeader] (2026-09-28).
 *
 * Asserted on the glyphs rather than the coordinates, because the numbers are formatted with the
 * default locale. The display order is 🚩 origin, ☑ routing-only, 💲 cost, 🏁 destination, and the mark
 * group is set apart from the geometry line by a ` | ` separator, emitted once before the first mark and
 * absent from the bare line.
 */
class MarkerRoutingCostHeaderTest {

    private val pinGlyph = "\uD83D\uDCCD"
    private val originGlyph = "\uD83D\uDEA9"
    private val onlyGlyph = "\u2611"
    private val costGlyph = "\uD83D\uDCB2"
    private val destinationGlyph = "\uD83C\uDFC1"
    private val separator = " | "

    private fun marker(
        cost: Int? = null,
        origin: Boolean = false,
        destination: Boolean = false,
        routingOnly: Boolean = false
    ) = UserMarker(
        id = "m",
        name = "m",
        geometry = MarkerGeometry.Pin(LatLng(43.5, 7.2)),
        origin = MarkerOrigin.USER,
        routingCost = cost,
        routeOrigin = origin,
        routeDestination = destination,
        routingOnly = routingOnly
    )

    @Test
    fun `no role and no cost draws the geometry line alone`() {
        val header = coordinateHeader(marker())
        assertTrue(header.startsWith("["))
        assertTrue(header.endsWith(pinGlyph))
        assertFalse(header.contains(costGlyph))
        assertFalse(header.contains(originGlyph))
        assertFalse(header.contains(destinationGlyph))
    }

    @Test
    fun `the bare line carries no separator`() {
        assertFalse(coordinateHeader(marker()).contains(separator))
    }

    @Test
    fun `a cost appends the dollar after the geometry glyph`() {
        assertTrue(coordinateHeader(marker(cost = 3)).endsWith("$pinGlyph$separator$costGlyph"))
    }

    @Test
    fun `the wall draws the dollar too`() {
        assertTrue(coordinateHeader(marker(cost = ROUTING_COST_BLOCKED)).endsWith("$pinGlyph$separator$costGlyph"))
    }

    @Test
    fun `an unset-reading value draws no dollar`() {
        assertFalse(coordinateHeader(marker(cost = 0)).contains(costGlyph))
        assertFalse(coordinateHeader(marker(cost = ROUTING_COST_BLOCKED + 1)).contains(costGlyph))
    }

    @Test
    fun `an origin draws the flag before the dollar`() {
        assertTrue(
            coordinateHeader(marker(cost = 3, origin = true))
                .endsWith("$pinGlyph$separator$originGlyph $costGlyph")
        )
    }

    @Test
    fun `a destination draws the chequered flag after the dollar`() {
        assertTrue(
            coordinateHeader(marker(cost = 3, destination = true))
                .endsWith("$pinGlyph$separator$costGlyph $destinationGlyph")
        )
    }

    @Test
    fun `both roles and a cost draw the three marks in the settled order`() {
        assertTrue(
            coordinateHeader(marker(cost = 3, origin = true, destination = true))
                .endsWith("$pinGlyph$separator$originGlyph $costGlyph $destinationGlyph")
        )
    }

    @Test
    fun `the separator is emitted once when several marks are present`() {
        val header = coordinateHeader(marker(cost = 3, origin = true, destination = true))
        assertEquals(1, header.split(separator).size - 1)
    }

    @Test
    fun `a role without a cost draws its own mark alone`() {
        assertTrue(coordinateHeader(marker(origin = true)).endsWith("$pinGlyph$separator$originGlyph"))
        assertTrue(
            coordinateHeader(marker(destination = true))
                .endsWith("$pinGlyph$separator$destinationGlyph")
        )
    }

    @Test
    fun `a routing-only marker draws the checked box immediately left of the dollar`() {
        assertTrue(
            coordinateHeader(marker(cost = 3, routingOnly = true))
                .endsWith("$pinGlyph$separator$onlyGlyph $costGlyph")
        )
        assertTrue(
            coordinateHeader(marker(cost = 3, origin = true, routingOnly = true, destination = true))
                .endsWith("$pinGlyph$separator$originGlyph $onlyGlyph $costGlyph $destinationGlyph")
        )
    }

    @Test
    fun `a routing-only box without a cost still opens the mark group`() {
        assertTrue(coordinateHeader(marker(routingOnly = true)).endsWith("$pinGlyph$separator$onlyGlyph"))
    }

    @Test
    fun `a plain marker draws no checked box`() {
        assertFalse(coordinateHeader(marker(cost = 3)).contains(onlyGlyph))
        assertFalse(coordinateHeader(marker(origin = true, destination = true)).contains(onlyGlyph))
    }
}
