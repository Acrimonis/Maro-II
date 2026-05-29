package ykws.android.maro.data.model

/**
 * Source metadata describing how the coastline was generated.
 *
 * @property source Attribution string (e.g. "OpenStreetMap contributors, ODbL").
 * @property pointCount Total number of vertices across all polylines.
 * @property meanSpacingM Average distance between consecutive vertices (meters).
 * @property totalLengthKm Total length of all coastline polylines combined (kilometers).
 * @property epsilonM Douglas-Peucker simplification tolerance used, if any.
 * @property fetchTimestampMs Time when the coastline was fetched from OSM (epoch millis).
 */
data class CoastlineMetadata(
    val source: String,
    val pointCount: Int,
    val meanSpacingM: Double,
    val totalLengthKm: Double = 0.0,
    val epsilonM: Double? = null,
    val fetchTimestampMs: Long = System.currentTimeMillis()
)
