package ykws.android.maro.data.model

import ykws.android.maro.data.model.markers.UserMarker
import ykws.android.maro.data.track.TrackSummary

/**
 * Pure selection policy: which items the map draws, given eligibility, ranking, a render cap and
 * the in-memory [MapRenderFocus]. No persistence, no IO, no Android dependencies — unit-testable.
 *
 * Tracks and markers share this shape so the two map projections cannot drift apart again.
 * The policy decides *what* is drawn; the imperative shell only executes the drawing.
 */
interface MapSelectionPolicy<T> {
    fun select(
        items: List<T>,
        filter: ListFilter,
        cap: Int,
        focus: MapRenderFocus,
        todayMidnightMs: Long
    ): List<T>
}

/**
 * Track selection — **ranked + capped**.
 *
 * Eligibility: matches the map filter. Pinned tracks are excluded here — they render through the
 * dedicated pinned path (never capped).
 *
 * The map filter is authoritative (2026-09-21, tightened 2026-09-28): the drawn set **is** the filter's
 * set, with no highlighted override — a session-boosted track, and the highlighted one the user is
 * looking at, both keep their rank term below and neither defeats the filter. See
 * `xTrack/TracksImport/260921_FEAT_PLN_TracksImport_render-focus-vs-map-filter.md` and
 * `xTrack/Ui_General/260928_FEAT_PLN_Ui_General_map-cards-and-the-filter.md`. That is what stops the
 * menu's count and the map disagreeing, the count being read off the painted set.
 *
 * Ranking: `focus → session-boosted → startTimeMs desc → lastPointTimeMs desc`, then `take(cap)` — the
 * highlighted track ranks first, but a render cap of zero draws nothing, the filter's own set being the
 * whole of what is drawn.
 */
class TrackSelectionPolicy : MapSelectionPolicy<TrackSummary> {

    override fun select(
        items: List<TrackSummary>,
        filter: ListFilter,
        cap: Int,
        focus: MapRenderFocus,
        todayMidnightMs: Long
    ): List<TrackSummary> {
        val eligible = items.filter { candidate ->
            !candidate.pinned && candidate.matchesFilter(filter, todayMidnightMs)
        }
        val ranked = eligible.sortedWith(
            compareByDescending<TrackSummary> { focus.isHighlighted(it.id) }
                .thenByDescending { focus.isBoosted(it.id) }
                .thenByDescending { it.startTimeMs }
                .thenByDescending { it.lastPointTimeMs }
        )
        return ranked.take(cap.coerceAtLeast(0))
    }
}

/**
 * Marker selection — **filter-only**: no cap, no ranking, no focus override. Markers keep their own
 * selection semantics (list / where-am-I); this class exists so markers cannot drift from the
 * shared shape.
 */
class MarkerSelectionPolicy : MapSelectionPolicy<UserMarker> {

    override fun select(
        items: List<UserMarker>,
        filter: ListFilter,
        cap: Int,
        focus: MapRenderFocus,
        todayMidnightMs: Long
    ): List<UserMarker> = items.filter { it.matchesFilter(filter) }
}
