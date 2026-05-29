package ykws.android.maro.data.coastline

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import ykws.android.maro.data.model.CoastlineData
import ykws.android.maro.data.model.CoastlineState
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.spatial.SpatialOperations
import kotlin.math.*

/**
 * Single source of truth for coastline data.
 *
 * Wraps [CoastlineGenerator] and exposes reactive state via [StateFlow].
 * Also provides geometry query methods for future use (zone checking,
 * distance queries).
 */
class CoastlineRepository(
    private val generator: CoastlineGenerator = CoastlineGenerator(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val _state = MutableStateFlow<CoastlineState>(CoastlineState.Loading)
    val state: StateFlow<CoastlineState> = _state.asStateFlow()

    /**
     * The raw coastline data. Used by query methods.
     */
    private var coastlineData: CoastlineData? = null

    /**
     * Progress value 0–100 exposed for UI feedback.
     */
    private val _progress = MutableStateFlow(0)
    val progress: StateFlow<Int> = _progress.asStateFlow()

    /**
     * Launches the full generation pipeline for the given region.
     * Safe to call multiple times — will replace previous state.
     */
    suspend fun generate(regionId: String = CoastlineGenerator.REGION_ID) {
        _state.value = CoastlineState.Loading
        _progress.value = 0

        try {
            val result = withContext(ioDispatcher) {
                generator.generate(regionId = regionId) { pct ->
                    _progress.value = pct
                }
            }

            coastlineData = result

            _state.value = CoastlineState.Ready(data = result)
            _progress.value = 100
        } catch (e: Exception) {
            _state.value = CoastlineState.Error(
                message = e.message ?: "Erreur inconnue lors de la génération de la côte."
            )
        }
    }

    // ── Query methods ──────────────────────────────────────────────────────

    /**
     * Returns true if the given GPS position is on the water side of the coastline.
     * Uses the cross-product against the nearest coastline segment.
     */
    fun isOnWater(latitude: Double, longitude: Double): Boolean {
        val data = coastlineData ?: return true // No data → assume water (safe default)
        val polylines = data.allSegments.map { segment ->
            segment.points.map { LatLng(it.lat.toDouble(), it.lon.toDouble()) }
        }
        if (polylines.isEmpty()) return true
        return SpatialOperations.isOnWater(latitude, longitude, polylines)
    }

    /**
     * Returns the minimum distance (meters) from a GPS position to the coastline.
     * For future use when the 300m zone check is implemented.
     *
     * Uses pre-computed edge vectors for efficient distance computation.
     */
    fun distanceToCoastMeters(latitude: Double, longitude: Double): Double {
        val data = coastlineData ?: return Double.MAX_VALUE

        val queryPoint = LatLng(latitude, longitude)

        // Project query point to local Cartesian for edge-vector-based distance
        var minDist = Double.MAX_VALUE

        for (segment in data.allSegments) {
            val points = segment.points
            for (i in 0 until points.size - 1) {
                val a = points[i]
                val b = points[i + 1]

                // Convert endpoints to Cartesian
                val midLat = (a.lat + b.lat) / 2.0
                val mPerDegLat = SpatialOperations.EARTH_RADIUS_M * PI / 180.0
                val mPerDegLon = mPerDegLat * cos(Math.toRadians(midLat))

                val ax = a.lon * mPerDegLon
                val ay = a.lat * mPerDegLat
                val bx = ax + a.edgeDxM  // Use pre-computed edge vector instead of re-projecting b
                val by = ay + a.edgeDyM

                val px = longitude * mPerDegLon
                val py = latitude * mPerDegLat

                // Point-to-segment distance using Cartesian coordinates
                val abx = bx - ax
                val aby = by - ay
                val apx = px - ax
                val apy = py - ay
                val abLenSq = abx * abx + aby * aby

                val dist = if (abLenSq == 0.0) {
                    sqrt((px - ax).pow(2) + (py - ay).pow(2))
                } else {
                    val t = ((apx * abx + apy * aby) / abLenSq).coerceIn(0.0, 1.0)
                    val cx = ax + t * abx
                    val cy = ay + t * aby
                    sqrt((px - cx).pow(2) + (py - cy).pow(2))
                }

                if (dist < minDist) minDist = dist
            }
        }

        return minDist
    }

    /**
     * Returns the cached [CoastlineData] if loaded, null otherwise.
     */
    fun getCoastlineData(): CoastlineData? = coastlineData

    /**
     * Returns true if the repository has loaded coastline data.
     */
    fun isLoaded(): Boolean = _state.value is CoastlineState.Ready
}
