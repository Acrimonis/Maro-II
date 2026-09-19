package ykws.android.maro.data.model

/**
 * The baked region a spatial answer may speak for, as four bounds. Neutral on purpose — the coastline
 * hands its own bounds out as this, and the track classification takes it in — so neither data package
 * imports the other.
 */
data class RegionBounds(
    val latSouth: Double,
    val latNorth: Double,
    val lonWest: Double,
    val lonEast: Double
) {
    /** True when a point can be asked about at all. */
    fun contains(latitude: Double, longitude: Double): Boolean =
        latitude in latSouth..latNorth && longitude in lonWest..lonEast
}
