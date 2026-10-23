package ykws.android.maro.data.markers

import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.MarkerGeometry
import ykws.android.maro.data.track.trackAutoName
import ykws.android.maro.spatial.MarkerMatcher
import ykws.android.maro.spatial.WhereAmIMatch

/**
 * The one home for a marker's auto-name.
 *
 * The rule: the marker's own geometry is asked of the caller's where-am-i, and the name the app's
 * single naming rule returns wins; when nothing names the position — or no geometry exists yet — the
 * name falls back to the Tracks feature's standard stamp, [trackAutoName]. A type glyph, supplied by
 * the caller rather than looked up here, leads the result so the data layer never imports the UI's
 * `MarkerType`.
 *
 * A corridor is two-tier: its midpoint names it outright, and only a silent midpoint sends its two
 * endpoints into the comparison — the body is the thing being named, and its tips are a fallback,
 * never a competitor (settled 2026-10-23).
 */
fun markerAutoName(
    createdAtEpochMs: Long,
    glyph: String,
    geometry: MarkerGeometry?,
    matchesAt: (LatLng) -> List<WhereAmIMatch>
): String {
    val resolved = when (geometry) {
        null -> null
        is MarkerGeometry.Pin -> namingName(matchesAt(geometry.position))
        is MarkerGeometry.Circle -> namingName(matchesAt(geometry.center))
        is MarkerGeometry.Corridor -> namingName(matchesAt(midpointOf(geometry)))
            ?: namingName(matchesAt(geometry.p1) + matchesAt(geometry.p2))
    } ?: trackAutoName(createdAtEpochMs)
    return if (glyph.isEmpty()) resolved else "$glyph $resolved"
}

/** The trimmed name the best naming match gives, or null when nothing names the position. */
private fun namingName(matches: List<WhereAmIMatch>): String? =
    MarkerMatcher.topNamingMatch(matches)
        ?.let { MarkerMatcher.markerOf(it).name.trim() }
        ?.takeIf { it.isNotEmpty() }

/** The corridor's midpoint — the same lat/lon average the overlay draws from. */
private fun midpointOf(corridor: MarkerGeometry.Corridor): LatLng =
    LatLng(
        (corridor.p1.latitude + corridor.p2.latitude) / 2.0,
        (corridor.p1.longitude + corridor.p2.longitude) / 2.0
    )
