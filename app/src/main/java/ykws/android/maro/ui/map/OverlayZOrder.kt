package ykws.android.maro.ui.map

import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline

/**
 * Enforces the canonical map overlay z-order:
 *
 *   tile basemap (index 0) → base data layers → tracks → markers (top).
 *
 * OSMdroid paints [MapView.overlays] in index order (index 0 first, last on top)
 * and has no per-overlay z-index. Every overlay mutation in this app appends to the
 * end of the list, so the relative order depends on "who wrote last" — markers could
 * end up below tracks. Call [reorder] after any overlay mutation to re-establish the
 * invariant; within-band relative order is preserved (e.g. marker under-strokes stay
 * below their gold geometry, unconfirmed markers stay below confirmed ones).
 */
internal object OverlayZOrder {

    /** Title prefixes identifying track overlays (history, pinned, live, trailing, arrows). */
    private val TRACK_TITLE_PREFIXES = listOf(
        "track_hist_",
        "track_pin_",
        "track_recording",
        "track_trailing",
        "track_arrow_"
    )

    /** Identifies overlays that belong to the track band. */
    fun isTrackOverlay(overlay: Overlay): Boolean {
        val title = titleOf(overlay) ?: return false
        return TRACK_TITLE_PREFIXES.any { title.startsWith(it) }
    }

    /** Identifies overlays that belong to the marker band (top). */
    fun isMarkerOverlay(overlay: Overlay): Boolean {
        if (overlay is MapEventsOverlay) return true
        val title = titleOf(overlay) ?: return false
        return title.startsWith("marker_")
    }

    private fun titleOf(overlay: Overlay): String? = when (overlay) {
        is Polyline -> overlay.title
        is Polygon -> overlay.title
        is Marker -> overlay.title
        is TrackDirectionOverlay -> overlay.title
        else -> null
    }

    /**
     * Rebuilds [MapView.overlays] into the canonical order:
     * tile → base (everything else) → tracks → markers.
     * The tile overlay at index 0 and the relative order within each band are preserved.
     */
    fun reorder(mv: MapView) {
        val overlays = mv.overlays
        if (overlays.size <= 1) return

        val tile = overlays.first()
        val rest = overlays.drop(1).toList()

        val tracks = rest.filter { isTrackOverlay(it) }
        val markers = rest.filter { isMarkerOverlay(it) }
        val base = rest.filterNot { isTrackOverlay(it) || isMarkerOverlay(it) }

        mv.overlays.removeAll(rest.toSet())
        mv.overlays.addAll(base)
        mv.overlays.addAll(tracks)
        mv.overlays.addAll(markers)
    }
}
