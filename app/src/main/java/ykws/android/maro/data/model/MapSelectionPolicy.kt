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
 * Eligibility: matches the map filter **or** is focused (highlighted / session-boosted). Pinned
 * tracks are excluded here — they render through the dedicated pinned path (never capped).
 *
 * Ranking: `focus → session-boosted → startTimeMs desc → lastPointTimeMs desc`, then `take(cap)`.
 * The highlighted track is always kept even when `cap == 0`.
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
                (focus.includes(candidate.id) || candidate.matchesFilter(filter, todayMidnightMs))
        }
        val ranked = eligible.sortedWith(
            compareByDescending<TrackSummary> { focus.isHighlighted(it.id) }
                .thenByDescending { focus.isBoosted(it.id) }
                .thenByDescending { it.startTimeMs }
                .thenByDescending { it.lastPointTimeMs }
        )
        val capped = ranked.take(cap.coerceAtLeast(0))
        val highlighted = ranked.firstOrNull { focus.isHighlighted(it.id) } ?: return capped
        return if (capped.none { focus.isHighlighted(it.id) }) capped + highlighted else capped
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
