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

    // ── Query methods (optimized with pre-projected XY) ────────────────────

    /**
     * Returns true if the given GPS position is on the water side of the coastline.
     *
     * Uses pre-projected XY coordinates and edge vectors — no lat/lon to meter
     * conversion during the per-edge loop. The GPS query point is projected once
     * using the coastline's reference latitude, then all math is simple 2D Cartesian.
     */
    fun isOnWater(latitude: Double, longitude: Double): Boolean {
        val data = coastlineData ?: return true
        val refLat = data.metadata.projectionRefLat
        if (refLat == 0.0) return true

        // Project the GPS query point once using the stored reference latitude
        val mPerDegLat = SpatialOperations.EARTH_RADIUS_M * PI / 180.0
        val mPerDegLon = mPerDegLat * cos(Math.toRadians(refLat))
        val px = longitude * mPerDegLon
        val py = latitude * mPerDegLat

        var bestCross = 0.0
        var bestDist = Double.MAX_VALUE
        var found = false

        for (segment in data.allSegments) {
            val pts = segment.points
            for (i in 0 until pts.size - 1) {
                val a = pts[i]
                // Edge start = (a.xM, a.yM), end = (a.xM + a.edgeDxM, a.yM + a.edgeDyM)
                val ax = a.xM.toDouble()
                val ay = a.yM.toDouble()
                val bx = ax + a.edgeDxM
                val by = ay + a.edgeDyM

                // Fast point-to-segment distance using pre-projected coordinates
                val abx = bx - ax
                val aby = by - ay
                val apx = px - ax
                val apy = py - ay
                val abLenSq = abx * abx + aby * aby

                val d = if (abLenSq == 0.0) {
                    sqrt((px - ax).pow(2) + (py - ay).pow(2))
                } else {
                    val t = ((apx * abx + apy * aby) / abLenSq).coerceIn(0.0, 1.0)
                    val cx = ax + t * abx
                    val cy = ay + t * aby
                    sqrt((px - cx).pow(2) + (py - cy).pow(2))
                }

                if (d < bestDist) {
                    bestDist = d
                    // Cross product: (B-A) × (P-A)
                    bestCross = abx * apy - aby * apx
                    found = true
                }
            }
        }

        if (!found) return true
        // z < 0 → right side → water (by orientation convention)
        return bestCross < 0.0
    }

    /**
     * Returns the minimum distance (meters) from a GPS position to the coastline.
     *
     * Uses pre-projected XY + edge vectors for zero-projection per-edge checks.
     * The GPS point is projected once, then all 6,000+ edges are checked with
     * simple 2D math — no trig operations in the loop.
     */
    fun distanceToCoastMeters(latitude: Double, longitude: Double): Double {
        val data = coastlineData ?: return Double.MAX_VALUE
        val refLat = data.metadata.projectionRefLat
        if (refLat == 0.0) return Double.MAX_VALUE

        // Project query point once using stored reference latitude
        val mPerDegLat = SpatialOperations.EARTH_RADIUS_M * PI / 180.0
        val mPerDegLon = mPerDegLat * cos(Math.toRadians(refLat))
        val px = longitude * mPerDegLon
        val py = latitude * mPerDegLat

        var minDist = Double.MAX_VALUE

        for (segment in data.allSegments) {
            val pts = segment.points
            for (i in 0 until pts.size - 1) {
                val a = pts[i]
                val ax = a.xM.toDouble()
                val ay = a.yM.toDouble()
                val bx = ax + a.edgeDxM
                val by = ay + a.edgeDyM

                // 2D point-to-segment distance
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
