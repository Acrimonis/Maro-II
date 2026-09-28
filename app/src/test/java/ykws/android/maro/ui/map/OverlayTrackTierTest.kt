package ykws.android.maro.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.ui.map.OverlayZOrder.TrackTier

/**
 * **The track band's three tiers, as far as the JVM can honestly see them.**
 *
 * The band is one list of prefixes to [OverlayZOrder], and since 2026-09-28 it is three tiers applied in
 * a fixed order: **stored tracks → the route's own overlays → the live recording**, so the route green
 * paints over every track but the one being recorded.
 *
 * The classification is a **pure function of the title** precisely so this file can exist:
 * [OverlayZOrder.trackTierOf] takes a `String?` and returns a tier, and the tier order is the enum's
 * own declaration order. The band's own list, by contrast, is unreachable from here — the app's JVM
 * tests construct no osmdroid overlay (`Polyline`'s constructor needs `android.graphics.Paint`), which
 * is the same limit [`RouteTargetBandTest`] records for the ring. **What stays the device's** is
 * `reorder`'s effect on a real `MapView`: that the sort lands those tiers on the map in that order.
 */
class OverlayTrackTierTest {

    @Test
    fun `every stored track sits in the tier the route paints over`() {
        assertEquals(TrackTier.STORED, OverlayZOrder.trackTierOf("track_hist_42"))
        assertEquals(TrackTier.STORED, OverlayZOrder.trackTierOf("track_pin_42"))
        assertEquals(TrackTier.STORED, OverlayZOrder.trackTierOf("track_arrow_42"))
        assertEquals(TrackTier.STORED, OverlayZOrder.trackTierOf("track_inspect_42"))
    }

    @Test
    fun `the route's own overlays share one tier, the pool and its candidates included`() {
        assertEquals(TrackTier.ROUTE, OverlayZOrder.trackTierOf(ROUTE_LINE_TITLE))
        assertEquals(TrackTier.ROUTE, OverlayZOrder.trackTierOf("${ROUTE_LINE_TITLE}_1"))
        assertEquals(TrackTier.ROUTE, OverlayZOrder.trackTierOf(ROUTE_PROGRESS_TITLE))
        assertEquals(TrackTier.ROUTE, OverlayZOrder.trackTierOf(ROUTE_TARGET_TITLE))
    }

    @Test
    fun `the live recording is the one track over it, and the trail follows it`() {
        assertEquals(TrackTier.LIVE, OverlayZOrder.trackTierOf("track_recording"))
        assertEquals(TrackTier.LIVE, OverlayZOrder.trackTierOf("track_trailing"))
    }

    @Test
    fun `the tier order is stored, then route, then live`() {
        // The enum's declaration order is the order `reorder` sorts by, so this is the rule itself.
        assertTrue(TrackTier.STORED.ordinal < TrackTier.ROUTE.ordinal)
        assertTrue(TrackTier.ROUTE.ordinal < TrackTier.LIVE.ordinal)
    }

    @Test
    fun `a title outside the band is no track at all`() {
        // The destination pin is a marker, not a track: it keeps painting over everything, the route
        // green included — `marker_` is the marker band's own prefix.
        assertEquals(TrackTier.NONE, OverlayZOrder.trackTierOf(ROUTE_PIN_TITLE))
        assertEquals(TrackTier.NONE, OverlayZOrder.trackTierOf("marker_boat"))
        assertEquals(TrackTier.NONE, OverlayZOrder.trackTierOf(null))
        assertEquals(TrackTier.NONE, OverlayZOrder.trackTierOf("coastline"))
    }
}
