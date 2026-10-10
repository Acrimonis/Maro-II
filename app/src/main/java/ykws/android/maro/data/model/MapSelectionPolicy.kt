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
 * Track selection — **ranked + capped**, with the open card's one item admitted past both.
 *
 * Eligibility: matches the map filter, **or is the id the open card holds** — the render escape
 * (2026-10-10). That single id is drawn past the map filter and past the render cap, and only while
 * its card stands: closing the card returns the map to the filter's set with nothing else changed.
 * It is a rendering escape, never a filter edit — the filter's own list and the badge semantics are
 * untouched. Pinned tracks are excluded here; they render through the dedicated pinned path (never
 * capped), where `storedTrackSelection` applies the very same one-id escape.
 *
 * **This reverses 2026-09-28 in part.** That day the map was narrowed to its filter's set and
 * nothing else, and the highlighted id's eligibility override was deleted to a rank term alone. The
 * user's word (2026-10-10) re-instates it in a narrowed shape — one id, and only while its card
 * stands — while the session boost keeps exactly what it had: a rank term alone, still filter-bound.
 * See `xTrack/TracksImport/260921_FEAT_PLN_TracksImport_render-focus-vs-map-filter.md` and the
 * 2026-10-10 plan that records the reversal.
 *
 * Ranking: `focus → session-boosted → startTimeMs desc → lastPointTimeMs desc`, then `take(cap)`. The
 * highlighted track ranks first, so it lands inside the cap whenever the cap allows one; should the
 * cap exclude it — a render cap of zero — the escape carries it on top of the capped set, so a closed
 * card leaves a zero cap drawing nothing.
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
            !candidate.pinned &&
                (focus.isHighlighted(candidate.id) || candidate.matchesFilter(filter, todayMidnightMs))
        }
        val ranked = eligible.sortedWith(
            compareByDescending<TrackSummary> { focus.isHighlighted(it.id) }
                .thenByDescending { focus.isBoosted(it.id) }
                .thenByDescending { it.startTimeMs }
                .thenByDescending { it.lastPointTimeMs }
        )
        val capped = ranked.take(cap.coerceAtLeast(0))
        // The one-id escape: the open card's item is drawn past the cap too. It ranks first, so this
        // only ever fires at a zero cap; it is prepended so the drawn order keeps the selection leading.
        val selected = eligible.firstOrNull { focus.isHighlighted(it.id) }
        return if (selected == null || capped.any { it.id == selected.id }) capped
        else listOf(selected) + capped
    }
}

/**
 * Marker selection — **filter-only plus the open card's one id**. No cap and no ranking, but the id
 * the open marker card holds is admitted regardless of the map filter — the render escape
 * (2026-10-10), one id and only while its card stands — exactly the shape the track half carries.
 * Every other marker keeps its filter-only semantics. This class exists so markers cannot drift from
 * the shared shape; the selected id is threaded in by `MarkersViewModel` from its own
 * `selectedMarkerId`.
 */
class MarkerSelectionPolicy : MapSelectionPolicy<UserMarker> {

    override fun select(
        items: List<UserMarker>,
        filter: ListFilter,
        cap: Int,
        focus: MapRenderFocus,
        todayMidnightMs: Long
    ): List<UserMarker> = items.filter { focus.isHighlighted(it.id) || it.matchesFilter(filter) }
}
