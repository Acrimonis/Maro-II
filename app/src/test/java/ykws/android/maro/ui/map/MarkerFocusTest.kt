package ykws.android.maro.ui.map

import org.junit.Assert.*
import org.junit.Test
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.MarkerGeometry
import ykws.android.maro.data.model.markers.MarkerOrigin
import ykws.android.maro.data.model.markers.UserMarker

/**
 * The marker framing's properties, asserted as identities and ratios rather than by re-deriving the
 * projection in the test — each one holds whatever the formula is.
 *
 * Design: `xTrack/Markers/260918_FEAT_PLN_Markers_focus-zoom-fractions.md` §5.
 */
class MarkerFocusTest {

    private val centre = LatLng(43.5, 7.2)

    private fun marker(geometry: MarkerGeometry, id: String = "m") = UserMarker(
        id = id,
        name = id,
        geometry = geometry,
        origin = MarkerOrigin.USER
    )

    /** A corridor running due east from [westLon], whose longitude span doubles with the offset. */
    private fun corridor(westLon: Double = 7.0, spanDegLon: Double = ONE_KM_DEG_LON) =
        MarkerGeometry.Corridor(
            p1 = LatLng(43.5, westLon),
            p2 = LatLng(43.5, westLon + spanDegLon),
            widthM = 100.0
        )

    private fun target(
        geometry: MarkerGeometry,
        currentZoom: Double = 15.0,
        corridorShare: Double = CORRIDOR_SHARE,
        pinFootprintM: Double = PIN_FOOTPRINT_M,
        viewportWidthPx: Int = VIEWPORT_PX,
        viewportHeightPx: Int = VIEWPORT_PX
    ) = markerFocusTarget(
        marker = marker(geometry),
        viewportWidthPx = viewportWidthPx,
        viewportHeightPx = viewportHeightPx,
        currentZoom = currentZoom,
        corridorShare = corridorShare,
        zoneShare = ZONE_SHARE,
        pinFootprintM = pinFootprintM
    )

    @Test
    fun `a pin frames the default zone's footprint`() {
        val pin = target(MarkerGeometry.Pin(centre))!!
        val defaultZone = target(MarkerGeometry.Circle(centre, PIN_FOOTPRINT_M / 2.0))!!

        assertEquals(defaultZone.zoom, pin.zoom, EPSILON)
        assertEquals(centre.latitude, pin.centre.latitude, EPSILON)
        assertEquals(centre.longitude, pin.centre.longitude, EPSILON)
    }

    @Test
    fun `halving the share asks for exactly one zoom level more`() {
        val wide = target(corridor(), corridorShare = 0.50)!!
        val tight = target(corridor(), corridorShare = 0.25)!!

        assertEquals(1.0, wide.zoom - tight.zoom, EPSILON)
    }

    @Test
    fun `a corridor twice as long frames about one zoom level wider`() {
        val short = target(corridor(spanDegLon = ONE_KM_DEG_LON))!!
        val long = target(corridor(spanDegLon = 2 * ONE_KM_DEG_LON))!!

        // Not exactly 1.0: the corridor's own width is added to both longitude spans.
        assertEquals(1.0, short.zoom - long.zoom, 0.15)
    }

    @Test
    fun `a corridor with no extent takes the map's tightest zoom`() {
        val flat = MarkerGeometry.Corridor(centre, centre, widthM = 0.0)

        assertEquals(MAP_MAX_ZOOM, target(flat)!!.zoom, EPSILON)
    }

    @Test
    fun `a corridor spanning the whole map takes the map's widest zoom`() {
        val huge = MarkerGeometry.Corridor(LatLng(41.0, 3.0), LatLng(45.0, 12.0), widthM = 100.0)

        assertEquals(MAP_MIN_ZOOM, target(huge)!!.zoom, EPSILON)
    }

    @Test
    fun `an unmeasured viewport asks for nothing`() {
        assertNull(target(corridor(), viewportWidthPx = 0))
        assertNull(target(corridor(), viewportHeightPx = 0))
    }

    @Test
    fun `a pin never zooms out of a closer view`() {
        val pin = MarkerGeometry.Pin(centre)
        val wideFootprint = 1000.0

        val computed = target(pin, currentZoom = 5.0, pinFootprintM = wideFootprint)!!
        assertTrue(computed.zoom < 17.0)
        assertEquals(17.0, target(pin, currentZoom = 17.0, pinFootprintM = wideFootprint)!!.zoom, EPSILON)
    }

    @Test
    fun `the zoom-in-only rule does not reach a zone`() {
        val computed = target(corridor(), currentZoom = 17.0)!!

        assertTrue(computed.zoom < 17.0)
    }

    private companion object {
        const val VIEWPORT_PX = 1000
        const val CORRIDOR_SHARE = 0.50
        const val ZONE_SHARE = 0.30
        const val PIN_FOOTPRINT_M = 200.0
        const val EPSILON = 1e-9

        /** One kilometre of longitude at 43.5°N, in degrees. */
        const val ONE_KM_DEG_LON = 0.0124
    }
}
