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
 *   tracks → the route's own overlays → the live recording.
 *
 * OSMdroid paints [MapView.overlays] in index order (index 0 first, last on top) and has no per-overlay
 * z-index. Every overlay mutation in this app appends to the end of the list, so a band's relative order
 * would otherwise depend on "who wrote last" — markers could end up below tracks, and a stored track
 * painted after the route's own objects would cover the route's green. Call [reorder] after any overlay
 * mutation to re-establish the invariant; the relative order **within** each band and each tier is
 * preserved (e.g. marker under-strokes stay below their gold geometry, unconfirmed markers below
 * confirmed ones).
 *
 * **The base data layers have their own fixed bottom-to-top order** (2026-10-22): depth raster →
 * low-depth warning → isobaths → regulated zones → zone300 band → coastline. Before that date they
 * shared one band with no internal order, so the depth raster — generated last — painted above the
 * coastline and hid the shoreline. Each base overlay now carries a `layer_*` tag (the constants below)
 * and [baseLayerRankOf] ranks the six; within a layer the writers' own sequence is preserved.
 *
 * **The route paints over every track but the live recording** (the user's word, 2026-09-28): the line
 * being drawn right now is the one whose newest points matter, so the recording keeps the top of the
 * band and the route green takes everything below it.
 */
internal object OverlayZOrder {

    /** Base-layer tags — one per data layer, read by [baseLayerRankOf] and set by the draw functions. */
    internal const val LAYER_DEPTH_RASTER = "layer_depth_raster"
    internal const val LAYER_LOW_DEPTH_WARNING = "layer_low_depth_warning"
    internal const val LAYER_ISOBATH = "layer_isobath"
    internal const val LAYER_REGULATED_ZONE = "layer_regulated_zone"
    internal const val LAYER_ZONE300 = "layer_zone300"
    internal const val LAYER_COASTLINE = "layer_coastline"

    /**
     * Where an overlay sits inside the track band, bottom to top. [NONE] is not a track at all — the
     * base band — which is what [isTrackOverlay] reads.
     *
     * **The decision is a function of the title alone** ([trackTierOf]), deliberately: osmdroid overlays
     * need `android.graphics.Paint` to construct, so the band's own classification could never be
     * reached from a JVM test, while a string can. The tier order is the enum's own declaration order.
     */
    internal enum class TrackTier { NONE, TRACK, ROUTE, LIVE }

    /** Tracks and the inspect line — the tier the route paints over. */
    private val TRACK_PREFIXES = listOf(
        "track_hist_",
        "track_pin_",
        "track_arrow_",
        // Inspect mode's own candidate line: it belongs in the band, or its unrecognised title would
        // drop it below every track. It sits in the **track** tier, so a followed route paints over it.
        "track_inspect_"
    )

    /**
     * The route's own overlays — the pool's lines, the provisional line and the aim ring. One prefix
     * covers all of them, and the tier is what places them **above every track**: append order
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
        TRACK_PREFIXES.any { title.startsWith(it) } -> TrackTier.TRACK
        else -> TrackTier.NONE
    }

    /**
     * The fixed bottom-to-top rank of a base data layer, keyed by its `layer_*` tag. A higher rank
     * paints later (on top): depth raster 0 … coastline 5. An untagged base overlay (a debug or
     * transient line) takes the top of the base band, so it can never be buried under the depth raster.
     */
    internal fun baseLayerRankOf(title: String?): Int = when (title) {
        LAYER_DEPTH_RASTER -> 0
        LAYER_LOW_DEPTH_WARNING -> 1
        LAYER_ISOBATH -> 2
        LAYER_REGULATED_ZONE -> 3
        LAYER_ZONE300 -> 4
        LAYER_COASTLINE -> 5
        else -> 6
    }

    /**
     * The paint rank of one overlay's **band**: BASE 0 → TRACK 1 → ROUTE 2 → LIVE 3 → MARKER 4, which
     * is the order the list is painted in. The tier half **is** [trackTierOf]'s own ordinal —
     * [TrackTier.NONE] is 0 and the three tiers follow in declaration order — so a reordering of the
     * enum moves the classifier and the sorter together and this function restates neither; the marker
     * rank alone is its own, sitting one above the top tier. Both inputs are what a JVM test can carry —
     * a title, and the one type test osmdroid forces, the events overlay, whose classification never
     * lived in its title.
     */
    internal fun paintRankOf(title: String?, isEventsOverlay: Boolean): Int = when {
        isEventsOverlay || title?.startsWith("marker_") == true -> 4
        else -> trackTierOf(title).ordinal
    }

    /** The band count, so a base sub-rank never bleeds into the next band. */
    private const val BASE_SCALE = 100

    /**
     * The full paint-order key: `band * [BASE_SCALE] + base sub-rank`. Bands keep their [paintRankOf]
     * order; inside the base band the six data layers follow [baseLayerRankOf]; inside every other band
     * the sub-rank is 0, so the stable sort preserves the writers' own sequence — the same guarantee
     * [reorder] has always leaned on.
     */
    internal fun paintKeyOf(title: String?, isEventsOverlay: Boolean): Int {
        val band = paintRankOf(title, isEventsOverlay)
        return band * BASE_SCALE + (if (band == 0) baseLayerRankOf(title) else 0)
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
        is TitledGroundOverlay -> overlay.layerTag
        else -> null
    }

    /**
     * Rebuilds [MapView.overlays] into the canonical order:
     * base (the six data layers, in their own fixed order) → tracks → markers.
     *
     * **Nothing is pinned by position**: the basemap is osmdroid's own `MapView` layer and this list
     * holds no tile, so the first entry is simply whoever registered first — treating it as the tile
     * was what buried the route's line under every base layer (the device's own log, 2026-09-29). The
     * relative order within each band, each tier and each base layer is preserved — a **stable** sort
     * is what puts the bands, the three track tiers and the six data layers in order without disturbing
     * what each of them already had.
     */
    fun reorder(mv: MapView) {
        val all = mv.overlays.toList()
        if (all.size <= 1) return

        val keys = all.map { paintKeyOf(titleOf(it), it is MapEventsOverlay) }
        // The keys along the list **are** the test: the sort below is stable, so a list whose keys
        // never decrease is already the list that sort would hand back, identity included — a repaint
        // that changes nothing therefore costs this one walk and no sort at all.
        if ((1 until keys.size).all { keys[it - 1] <= keys[it] }) return

        // The permutation runs over `keys`, not over a second `paintKeyOf` per overlay: the walk
        // above already priced every title, and `sortedBy` here is that same stable sort.
        val ordered = all.indices.sortedBy { keys[it] }.map { all[it] }
        if (ordered.indices.all { ordered[it] === all[it] }) return

        mv.overlays.removeAll(all.toSet())
        mv.overlays.addAll(ordered)
    }
}
