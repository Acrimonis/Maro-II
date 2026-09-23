package ykws.android.maro.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.osmdroid.views.overlay.Polyline

/**
 * **The aim ring's band, as far as the JVM can honestly see it.**
 *
 * The ring lands in `OverlayZOrder`'s track band — above the tracks, below every marker, the boat
 * included — through two facts: [RouteTargetOverlay] must be a type `titleOf` recognises, and its title
 * must carry the `route_` prefix the band's own list holds.
 *
 * The **type arm is asserted here** and is load-bearing: `titleOf` reads a `Polyline` and not a generic
 * overlay, so a refactor of the ring onto a plain `Overlay` would compile, draw, and silently drop the
 * ring below every track — this is what fails then. The **prefix constant is pinned too**, so a rename
 * that drops the prefix fails here.
 *
 * **What is not covered, and cannot be on this JVM:** the link from that prefix to the band's own list.
 * `OverlayZOrder` keeps its prefix list private and `isTrackOverlay` takes an `Overlay`, and this app's
 * JVM tests deliberately construct no osmdroid overlay (`Polyline`'s constructor needs
 * `android.graphics.Paint`). The device pass covers the ordering; a reflective probe into the private
 * list would be a brittle stand-in for it, not a test of it.
 */
class RouteTargetBandTest {

    @Test
    fun `the ring is a type the ordering recognises, or its title would not be read at all`() {
        assertTrue(
            "titleOf reads a Polyline; anything else lands below every track",
            Polyline::class.java.isAssignableFrom(RouteTargetOverlay::class.java)
        )
    }

    @Test
    fun `the ring's title carries the band's own prefix`() {
        assertEquals("route_target", ROUTE_TARGET_TITLE)
        assertTrue(ROUTE_TARGET_TITLE.startsWith("route_"))
        // The route's own line carries the same prefix, which is what keeps the lines and the ring in
        // one band rather than two.
        assertTrue(ROUTE_LINE_TITLE.startsWith("route_"))
    }
}
