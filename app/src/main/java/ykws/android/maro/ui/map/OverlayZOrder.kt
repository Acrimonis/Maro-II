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
 *   (osmdroid's own basemap) → base data layers → tracks → markers (top),
 *
 * and, **inside the track band**, three tiers bottom to top (2026-09-28):
 *
 *   stored tracks → the route's own overlays → the live recording.
 *
 * OSMdroid paints [MapView.overlays] in index order (index 0 first, last on top) and has no per-overlay
 * z-index. Every overlay mutation in this app appends to the end of the list, so a band's relative order
 * would otherwise depend on "who wrote last" — markers could end up below tracks, and a stored track
 * painted after the route's own objects would cover the route's green. Call [reorder] after any overlay
 * mutation to re-establish the invariant; the relative order **within** each band and each tier is
 * preserved (e.g. marker under-strokes stay below their gold geometry, unconfirmed markers below
 * confirmed ones).
 *
 * **The route paints over every track but the live recording** (the user's word, 2026-09-28): the line
 * being drawn right now is the one whose newest points matter, so the recording keeps the top of the
 * band and the route green takes everything below it.
 */

internal object OverlayZOrder {

    /**
     * Where an overlay sits inside the track band, bottom to top. [NONE] is not a track at all — the
     * base band — which is what [isTrackOverlay] reads.
     *
     * **The decision is a function of the title alone** ([trackTierOf]), deliberately: osmdroid overlays
     * need `android.graphics.Paint` to construct, so the band's own classification could never be
     * reached from a JVM test, while a string can. The tier order is the enum's own declaration order.
     */
    internal enum class TrackTier { NONE, STORED, ROUTE, LIVE }

    /** Stored tracks and the inspect line — the tier the route paints over. */
    private val STORED_TRACK_PREFIXES = listOf(
        "track_hist_",
        "track_pin_",
        "track_arrow_",
        // Inspect mode's own candidate line: it belongs in the band, or its unrecognised title would
        // drop it below every track. It sits in the **stored** tier, so a followed route paints over it.
        "track_inspect_"
    )

    /**
     * The route's own overlays — the pool's lines, the provisional line and the aim ring. One prefix
     * covers all of them, and the tier is what places them **above every stored track**: append order
     * alone could not, since a track written later lands on top of them.
     */
    private val ROUTE_PREFIXES = listOf("route_")

    /**
     * The live recording and the dotted trail behind it — the one track the route does **not** paint
     * over.
     */
    private val LIVE_TRACK_PREFIXES = listOf("track_recording", "track_trailing")

    /** The tier a title belongs to, or [TrackTier.NONE] when the overlay is not a track at all. */
    internal fun trackTierOf(title: String?): TrackTier = when {
        title == null -> TrackTier.NONE
        ROUTE_PREFIXES.any { title.startsWith(it) } -> TrackTier.ROUTE
        LIVE_TRACK_PREFIXES.any { title.startsWith(it) } -> TrackTier.LIVE
        STORED_TRACK_PREFIXES.any { title.startsWith(it) } -> TrackTier.STORED
        else -> TrackTier.NONE
    }

    /**
     * The paint rank of one overlay: BASE 0 → STORED 1 → ROUTE 2 → LIVE 3 → MARKER 4, which is the
     * order the list is painted in. The tier half **is** [trackTierOf]'s own ordinal — [TrackTier.NONE]
     * is 0 and the three tiers follow in declaration order — so a reordering of the enum moves the
     * classifier and the sorter together and this function restates neither; the marker rank alone is
     * its own, sitting one above the top tier. Both inputs are what a JVM test can carry — a title, and
     * the one type test osmdroid forces, the events overlay, whose classification never lived in its
     * title.
     */
    internal fun paintRankOf(title: String?, isEventsOverlay: Boolean): Int = when {
        isEventsOverlay || title?.startsWith("marker_") == true -> 4
        else -> trackTierOf(title).ordinal
    }

    /** Identifies overlays that belong to the track band. */
    fun isTrackOverlay(overlay: Overlay): Boolean = trackTierOf(titleOf(overlay)) != TrackTier.NONE

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
     * base (everything else) → tracks → markers.
     *
     * **Nothing is pinned by position**: the basemap is osmdroid's own `MapView` layer and this list
     * holds no tile, so the first entry is simply whoever registered first — treating it as the tile
     * was what buried the route's line under every base layer (the device's own log, 2026-09-29). The
     * relative order within each band and each tier is preserved — a **stable** sort is what puts the
     * bands and the three track tiers in order without disturbing what each of them already had.
     */
    fun reorder(mv: MapView) {
        val all = mv.overlays.toList()
        if (all.size <= 1) return

        val ranks = all.map { paintRankOf(titleOf(it), it is MapEventsOverlay) }
        // The ranks along the list **are** the test: the sort below is stable, so a list whose ranks
        // never decrease is already the list that sort would hand back, identity included — a repaint
        // that changes nothing therefore costs this one walk and no sort at all.
        if ((1 until ranks.size).all { ranks[it - 1] <= ranks[it] }) return

        // The permutation runs over `ranks`, not over a second `paintRankOf` per overlay: the walk
        // above already priced every title, and `sortedBy` here is that same stable sort.
        val ordered = all.indices.sortedBy { ranks[it] }.map { all[it] }
        if (ordered.indices.all { ordered[it] === all[it] }) return

        mv.overlays.removeAll(all.toSet())
        mv.overlays.addAll(ordered)
    }
}
