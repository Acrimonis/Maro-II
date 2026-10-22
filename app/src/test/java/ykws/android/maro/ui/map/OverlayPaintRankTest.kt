package ykws.android.maro.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **The paint rank, as far as the JVM can honestly see it.**
 *
 * [`OverlayZOrder.reorder`] used to pin the list's first entry as the map's tile; this app's list holds
 * no tile, so that first line — the route pool's slot 0 — was painted under every base layer (the
 * device's own log, 2026-09-29). The fix is a single pure function,
 * [OverlayZOrder.paintKeyOf]`: the order the list is painted in is
 * `BASE 0 → TRACK 1 → ROUTE 2 → LIVE 3 → MARKER 4`, with the base band's six data layers ranked
 * beneath it as `depth raster → low-depth warning → isobath → regulated zones → zone300 → coastline`,
 * and the sort over it is stable so each group keeps the sequence it already had.
 *
 * It takes a title and the one type test osmdroid forces — the events overlay, whose classification
 * never lived in a title — precisely so this file can exist: the app's JVM tests construct no osmdroid
 * overlay (`Polyline`'s constructor needs `android.graphics.Paint`), which is the same limit
 * [`OverlayTrackTierTest`] records. **What stays the device's** is `reorder`'s own list surgery on a
 * real `MapView` — the detach and the re-attach — since osmdroid overlays cannot be constructed on the
 * JVM; all that is pinned here is the order the sort hands back.
 */
class OverlayPaintRankTest {

    @Test
    fun `the five ranks are pinned by value, base to marker`() {
        assertEquals(0, OverlayZOrder.paintRankOf(null, false))
        assertEquals(0, OverlayZOrder.paintRankOf("coastline", false))
        assertEquals(1, OverlayZOrder.paintRankOf("track_hist_42", false))
        assertEquals(2, OverlayZOrder.paintRankOf("${ROUTE_LINE_TITLE}_1", false))
        assertEquals(3, OverlayZOrder.paintRankOf("track_recording", false))
        assertEquals(4, OverlayZOrder.paintRankOf("marker_boat", false))
    }

    @Test
    fun `a route line ranks above every stored prefix`() {
        val storedRanks = listOf(
            "track_hist_42", "track_pin_42", "track_arrow_42", "track_inspect_42"
        ).map { OverlayZOrder.paintRankOf(it, false) }
        val routeRank = OverlayZOrder.paintRankOf("${ROUTE_LINE_TITLE}_0", false)
        assertTrue(storedRanks.all { routeRank > it })
        // The route's own overlays share the rank: the pool, its candidates, the provisional line, the ring.
        assertEquals(routeRank, OverlayZOrder.paintRankOf(ROUTE_PROGRESS_TITLE, false))
    }

    @Test
    fun `the live recording and its trail rank above the route`() {
        val routeRank = OverlayZOrder.paintRankOf("${ROUTE_LINE_TITLE}_1", false)
        assertTrue(OverlayZOrder.paintRankOf("track_recording", false) > routeRank)
        assertTrue(OverlayZOrder.paintRankOf("track_trailing", false) > routeRank)
    }

    @Test
    fun `a marker title and the events overlay rank last`() {
        val markerTitle = OverlayZOrder.paintRankOf("marker_boat", false)
        assertTrue(OverlayZOrder.paintRankOf("track_recording", false) < markerTitle)
        assertEquals(markerTitle, OverlayZOrder.paintRankOf(ROUTE_PIN_TITLE, false))
        // The events overlay is osmdroid's own and carries no `marker_` title; the flag is the only
        // thing that can name it, and it pins the same top rank a titled marker wears.
        assertEquals(markerTitle, OverlayZOrder.paintRankOf(null, true))
        assertEquals(markerTitle, OverlayZOrder.paintRankOf("MapEventsReceiver", true))
    }

    @Test
    fun `a null or unclaimed title ranks first`() {
        assertEquals(0, OverlayZOrder.paintRankOf(null, false))
        assertEquals(0, OverlayZOrder.paintRankOf("coastline", false))
        assertEquals(0, OverlayZOrder.paintRankOf("depth_raster", false))
    }

    @Test
    fun `the six base layers rank bottom to top`() {
        assertEquals(
            listOf(0, 1, 2, 3, 4, 5),
            listOf(
                OverlayZOrder.LAYER_DEPTH_RASTER,
                OverlayZOrder.LAYER_LOW_DEPTH_WARNING,
                OverlayZOrder.LAYER_ISOBATH,
                OverlayZOrder.LAYER_REGULATED_ZONE,
                OverlayZOrder.LAYER_ZONE300,
                OverlayZOrder.LAYER_COASTLINE
            ).map { OverlayZOrder.baseLayerRankOf(it) }
        )
        // The base band, in full, still paints below the first track tier.
        assertTrue(
            OverlayZOrder.paintKeyOf(OverlayZOrder.LAYER_COASTLINE, false) <
                OverlayZOrder.paintKeyOf("track_hist_42", false)
        )
    }

    @Test
    fun `stored titles interleaved between two route ones regroup by rank, each group keeping its own sequence`() {
        // Several stored titles sit between the two route lines, with the live recording and a marker
        // after — the interleaving that had a stored track land above the route's own line.
        val interleaved = listOf(
            "route_line_1", "track_hist_9", "track_pin_3", "track_hist_7", "route_line_2",
            "track_recording", "marker_boat"
        )
        assertEquals(
            listOf(
                "track_hist_9", "track_pin_3", "track_hist_7",
                "route_line_1", "route_line_2",
                "track_recording",
                "marker_boat"
            ),
            interleaved.sortedBy { OverlayZOrder.paintRankOf(it, false) }
        )
    }

    @Test
    fun `a shuffled list sorted by key orders the base layers then each band`() {
        val shuffled = listOf(
            "marker_boat", "route_line_2", "track_hist_9", OverlayZOrder.LAYER_COASTLINE,
            "track_recording", "route_line_1", "track_pin_3", "track_trailing",
            "marker_route_dest", OverlayZOrder.LAYER_DEPTH_RASTER, "track_hist_7"
        )
        // `sortedBy` is stable, so within each band the list's own order survives — which is exactly the
        // guarantee `reorder` leans on: a tier's writers, not the sort, set the order inside a tier.
        // The base band is the exception: its six layers have a fixed order of their own, so the depth
        // raster lands beneath the coastline no matter which one was appended first.
        val ordered = shuffled.sortedBy { OverlayZOrder.paintKeyOf(it, false) }
        assertEquals(
            listOf(
                OverlayZOrder.LAYER_DEPTH_RASTER, OverlayZOrder.LAYER_COASTLINE,
                "track_hist_9", "track_pin_3", "track_hist_7",
                "route_line_2", "route_line_1",
                "track_recording", "track_trailing",
                "marker_boat", "marker_route_dest"
            ),
            ordered
        )
    }
}
