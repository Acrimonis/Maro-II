package ykws.android.maro.data.model

/**
 * Source metadata describing how the coastline was generated.
 *
 * @property source Attribution string (e.g. "OpenStreetMap contributors, ODbL").
 * @property pointCount Total number of vertices across all polylines.
 * @property meanSpacingM Average distance between consecutive vertices (meters).
 * @property epsilonM Douglas-Peucker simplification tolerance used, if any.
 */
data class CoastlineMetadata(
    val source: String,
    val pointCount: Int,
    val meanSpacingM: Double,
    val epsilonM: Double?
)
