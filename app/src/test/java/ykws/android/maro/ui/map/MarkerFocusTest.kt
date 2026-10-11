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
 * Design: `xTrack/Markers/260918_FEAT_PLN_Markers_focus-zoom-fractions.md` §5, extended
 * `xTrack/Markers/261011_FEAT_PLN_Markers_focus-include-proximity.md` so the framed extent includes the
 * marker's proximity collar.
 */
class MarkerFocusTest {

    private val centre = LatLng(43.5, 7.2)

    private fun marker(
        geometry: MarkerGeometry,
        proximityOverrideM: Double? = null,
        id: String = "m"
    ) = UserMarker(
        id = id,
        name = id,
        geometry = geometry,
        proximityOverrideM = proximityOverrideM,
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
        proximityOverrideM: Double? = null,
        corridorShare: Double = CORRIDOR_SHARE,
        zoneShare: Double = ZONE_SHARE,
        pinFootprintM: Double = PIN_FOOTPRINT_M,
        proximityPinM: Double = PROXIMITY_PIN_M,
        proximityZoneMultiplier: Double = PROXIMITY_ZONE_MULTIPLIER,
        viewportWidthPx: Int = VIEWPORT_PX,
        viewportHeightPx: Int = VIEWPORT_PX
    ) = markerFocusTarget(
        marker = marker(geometry, proximityOverrideM),
        viewportWidthPx = viewportWidthPx,
        viewportHeightPx = viewportHeightPx,
        corridorShare = corridorShare,
        zoneShare = zoneShare,
        pinFootprintM = pinFootprintM,
        proximityPinM = proximityPinM,
        proximityZoneMultiplier = proximityZoneMultiplier
    )

    @Test
    fun `a circle's framed extent grows with its proximity collar`() {
        val bare = target(MarkerGeometry.Circle(centre, 100.0), proximityOverrideM = 100.0)!!
        val dense = target(MarkerGeometry.Circle(centre, 100.0), proximityOverrideM = 300.0)!!

        // The reach doubles from 200 m to 400 m, so the extent doubles and the zoom drops one level.
        assertEquals(1.0, bare.zoom - dense.zoom, RATIO_EPSILON)
    }

    @Test
    fun `a pin frames its proximity reach`() {
        val pin = target(MarkerGeometry.Pin(centre), proximityPinM = 400.0)!!
        // A zero-collar circle of the same radius carries the identical extent and share.
        val sameReach = target(MarkerGeometry.Circle(centre, 400.0), proximityOverrideM = 0.0)!!

        assertEquals(sameReach.zoom, pin.zoom, EPSILON)
        assertEquals(centre.latitude, pin.centre.latitude, EPSILON)
        assertEquals(centre.longitude, pin.centre.longitude, EPSILON)
    }

    @Test
    fun `a pin never frames tighter than its nominal footprint`() {
        val pin = target(MarkerGeometry.Pin(centre), proximityPinM = 20.0)!!
        val footprint =
            target(MarkerGeometry.Circle(centre, PIN_FOOTPRINT_M / 2.0), proximityOverrideM = 0.0)!!

        assertEquals(footprint.zoom, pin.zoom, EPSILON)
    }

    @Test
    fun `halving the share asks for exactly one zoom level more`() {
        val wide = target(corridor(), corridorShare = 0.50)!!
        val tight = target(corridor(), corridorShare = 0.25)!!

        assertEquals(1.0, wide.zoom - tight.zoom, EPSILON)
    }

    @Test
    fun `a corridor twice as long frames about one zoom level wider`() {
        val short = target(corridor(spanDegLon = ONE_KM_DEG_LON), proximityOverrideM = 0.0)!!
        val long = target(corridor(spanDegLon = 2 * ONE_KM_DEG_LON), proximityOverrideM = 0.0)!!

        // Not exactly 1.0: the corridor's own width is added to both longitude spans.
        assertEquals(1.0, short.zoom - long.zoom, 0.15)
    }

    @Test
    fun `a corridor with no extent takes the map's tightest zoom`() {
        val flat = MarkerGeometry.Corridor(centre, centre, widthM = 0.0)

        assertEquals(MAP_MAX_ZOOM, target(flat, proximityOverrideM = 0.0)!!.zoom, EPSILON)
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

    private companion object {
        const val VIEWPORT_PX = 1000
        const val CORRIDOR_SHARE = 0.50
        const val ZONE_SHARE = 0.30
        const val PIN_FOOTPRINT_M = 200.0
        const val PROXIMITY_PIN_M = 200.0
        const val PROXIMITY_ZONE_MULTIPLIER = 3.0
        const val EPSILON = 1e-9

        /** A zoom-difference identity carried through `log2`: tolerates the projection's own rounding. */
        const val RATIO_EPSILON = 1e-6

        /** One kilometre of longitude at 43.5°N, in degrees. */
        const val ONE_KM_DEG_LON = 0.0124
    }
}
