package ykws.android.maro.data.coastline

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import ykws.android.maro.data.model.CoastlineSegment
import ykws.android.maro.data.model.CoastlineState
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.spatial.SpatialOperations

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
     * The raw coastline polylines as flat lists. Used by [isOnWater].
     */
    private var rawPolylines: List<List<LatLng>> = emptyList()

    /**
     * Progress value 0–100 exposed for UI feedback.
     */
    private val _progress = MutableStateFlow(0)
    val progress: StateFlow<Int> = _progress.asStateFlow()

    /**
     * Launches the full generation pipeline.
     * Safe to call multiple times — will replace previous state.
     */
    suspend fun generate() {
        _state.value = CoastlineState.Loading
        _progress.value = 0

        try {
            val result = withContext(ioDispatcher) {
                generator.generate { pct ->
                    _progress.value = pct
                }
            }

            // Store raw polylines for query methods
            rawPolylines = result.segments.map { it.points }

            _state.value = CoastlineState.Ready(
                polylines = result.segments,
                metadata = result.metadata
            )
            _progress.value = 100
        } catch (e: Exception) {
            _state.value = CoastlineState.Error(
                message = e.message ?: "Erreur inconnue lors de la génération de la côte."
            )
        }
    }

    // ── Query methods (for future use) ─────────────────────────────────────

    /**
     * Returns true if the given GPS position is on the water side of the coastline.
     * Uses the cross-product against the nearest coastline segment.
     */
    fun isOnWater(latitude: Double, longitude: Double): Boolean {
        val polylines = rawPolylines
        if (polylines.isEmpty()) return true // No data → assume water (safe default)
        return SpatialOperations.isOnWater(latitude, longitude, polylines)
    }

    /**
     * Returns the minimum distance (meters) from a GPS position to the coastline.
     * For future use when the 300m zone check is implemented.
     */
    fun distanceToCoastMeters(latitude: Double, longitude: Double): Double {
        val polylines = rawPolylines
        if (polylines.isEmpty()) return Double.MAX_VALUE

        val point = LatLng(latitude, longitude)
        var minDist = Double.MAX_VALUE

        for (polyline in polylines) {
            for (i in 0 until polyline.size - 1) {
                val d = SpatialOperations.pointToSegmentDistance(
                    point, polyline[i], polyline[i + 1]
                )
                if (d < minDist) minDist = d
            }
        }
        return minDist
    }

    /**
     * Returns true if the repository has loaded coastline data.
     */
    fun isLoaded(): Boolean = _state.value is CoastlineState.Ready
}
