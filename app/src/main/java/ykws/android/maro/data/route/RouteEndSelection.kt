package ykws.android.maro.data.route

/**
 * **One end of a route, as the drawer holds it** (R44–R46, R66): the boat's own position, the map's
 * standing marker position, or a marker the user flagged for that end.
 *
 * The two selectors persist one pair of these per navigation mode, and a preference file holds text, so
 * a value is written by [encode] and read back by [decode]. **The list's order is part of the
 * contract**: a stored value that no longer resolves — its marker deleted, or its flag cleared — falls
 * back to the end's **first entry**, [CurrentPosition] for the start and [MarkerPosition] for the
 * destination, so a selector never shows an entry it does not offer.
 *
 * **Each end has its own legal set**: the start may name the boat's position, the standing marker
 * position or a flagged origin, while the destination may name the standing position or a flagged
 * destination. The boat's own position is not a destination, so a stored `current` read at the
 * destination end is unresolved rather than obeyed.
 *
 * Pure Kotlin, no Android dependency, so the encode, the decode and the fallback are unit tests rather
 * than device runs.
 */
sealed interface RouteEndSelection {

    /**
     * The boat's own fix — the shipped anchor rule, led `route.anchor.leadSec` in GPS mode and unled in
     * demo mode, which is the same value the acquisition reads at its trigger.
     */
    data object CurrentPosition : RouteEndSelection

    /**
     * **Where the map's own marker stands when the action is pressed** (R46): the boat's position in
     * demo mode, and in GPS mode the dragged map's own centre, falling back to the fix led 10 s where
     * the map still sits on the boat.
     */
    data object MarkerPosition : RouteEndSelection

    /** A marker the user flagged for this end — `routeOrigin` for the start, `routeDestination` for the arrival. */
    data class Marker(val markerId: String) : RouteEndSelection

    /** Which end a selection belongs to, the two ends offering different entries. */
    enum class End { START, DESTINATION }

    companion object {

        /** The token [CurrentPosition] is stored as. */
        const val CURRENT = "current"

        /** The token [MarkerPosition] is stored as. */
        const val POSITION = "position"

        /** The prefix a flagged marker's id is stored under. */
        const val MARKER_PREFIX = "marker:"

        /**
         * The end's **first entry** — also what a dead selection falls back to, which is why it has one
         * home here rather than a literal at each reader.
         */
        fun firstEntry(end: End): RouteEndSelection =
            if (end == End.START) CurrentPosition else MarkerPosition

        /** The stored text for [selection]. */
        fun encode(selection: RouteEndSelection): String = when (selection) {
            CurrentPosition -> CURRENT
            MarkerPosition -> POSITION
            is Marker -> MARKER_PREFIX + selection.markerId
        }

        /**
         * The stored text read back, or `null` where nothing usable stands: a null, a blank, a token
         * nothing writes, or a marker recorded with no id.
         */
        fun decode(raw: String?): RouteEndSelection? =
            raw?.trim()?.takeIf { it.isNotEmpty() }?.let { value ->
                when {
                    value == CURRENT -> CurrentPosition
                    value == POSITION -> MarkerPosition
                    value.startsWith(MARKER_PREFIX) ->
                        value.removePrefix(MARKER_PREFIX).takeIf { it.isNotEmpty() }?.let(::Marker)
                    else -> null
                }
            }

        /**
         * **What the selector actually holds**: the stored value where it still resolves for [end] and
         * names a marker among [eligibleMarkerIds], and the end's [firstEntry] where it does not — the
         * dead id leaving the store when the resolved value is written back.
         */
        fun resolve(raw: String?, end: End, eligibleMarkerIds: Set<String>): RouteEndSelection {
            val decoded = decode(raw) ?: return firstEntry(end)
            val legal = when (decoded) {
                CurrentPosition -> end == End.START
                MarkerPosition -> true
                is Marker -> decoded.markerId in eligibleMarkerIds
            }
            return if (legal) decoded else firstEntry(end)
        }

        /**
         * **The marker ids a selector may offer for [end]** — read from the flags alone, the two ends
         * independent of each other (R44, R45), and in the order the caller lists them so the selector's
         * own order is the caller's.
         *
         * The flags are handed in as [MarkerRouteFlags] rather than as a `UserMarker`: the data layer
         * keeps no dependency on the markers package, so the surface that already holds the markers does
         * the projection and this rule stays unit-testable.
         */
        fun eligibleMarkerIds(end: End, markers: List<MarkerRouteFlags>): Set<String> =
            markers.filter { if (end == End.START) it.origin else it.destination }
                .map { it.id }
                .toSet()
    }
}

/**
 * **A marker's two route flags, as the eligible-set rule reads them** — the projection
 * [RouteEndSelection.eligibleMarkerIds] needs from a `UserMarker`, kept here so the rule is pure Kotlin
 * and the flags' independence is what its test pins.
 */
data class MarkerRouteFlags(
    val id: String,
    val origin: Boolean,
    val destination: Boolean
)
