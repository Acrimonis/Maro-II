package ykws.android.maro.ui.map

/**
 * Which of the two kind-locked lists the shared list surface ([TrackHistoryOverlay]) shows.
 *
 * The chrome carries one visibility flag per scope — [MapScreenChrome.showTrackHistory] and
 * [MapScreenChrome.showRouteHistory] — and the scope is resolved from whichever flag stands, so the
 * two lists can never be requested at once and the surface never has to guess which kind it holds.
 */
internal enum class ListScope { TRACKS, ROUTES }

/**
 * Resolve the list scope from the two chrome flags: the routes list wins only where its own flag
 * stands, and null means neither list is open. Every opener clears the other flag, so both standing is
 * a defect this resolver collapses rather than a state the surface can draw.
 */
internal fun listScopeOf(showTrackHistory: Boolean, showRouteHistory: Boolean): ListScope? = when {
    showRouteHistory -> ListScope.ROUTES
    showTrackHistory -> ListScope.TRACKS
    else -> null
}
