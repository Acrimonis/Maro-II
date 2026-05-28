package ykws.android.maro.data.coastline

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import ykws.android.maro.data.model.CoastlineCache
import ykws.android.maro.data.model.CoastlineSegment
import ykws.android.maro.data.model.CoastlineState
import ykws.android.maro.data.model.GenerationProgress
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.spatial.SpatialOperations
import java.io.File

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

    /** Directory for caching coastline data (set via [setCacheDir]). */
    private var cacheDir: File? = null

    private val json = Json { ignoreUnknownKeys = true }

    /** Start in [Idle] so generation does not auto-start on init. */
    private val _state = MutableStateFlow<CoastlineState>(CoastlineState.Idle)
    val state: StateFlow<CoastlineState> = _state.asStateFlow()

    /**
     * The raw coastline polylines as flat lists. Used by [isOnWater].
     */
    private var rawPolylines: List<List<LatLng>> = emptyList()

    /**
     * Progress state (phase name + 0–100) exposed for UI feedback.
     */
    private val _progress = MutableStateFlow(GenerationProgress("", 0))
    val progress: StateFlow<GenerationProgress> = _progress.asStateFlow()

    // ── Cache management ────────────────────────────────────────────────────

    /** Initialise the cache directory from an Android [Context]. */
    fun setCacheDir(context: Context) {
        cacheDir = File(context.filesDir, "coastline_cache")
        cacheDir?.mkdirs()
    }

    /** Full path to the cached JSON file. */
    private val cacheFile: File?
        get() = cacheDir?.resolve("coastline.json")

    /**
     * Returns the cached coastline data, or `null` if no cache exists.
     */
    fun loadCache(): CoastlineCache? {
        val file = cacheFile ?: return null
        if (!file.exists()) return null
        return try {
            json.decodeFromString<CoastlineCache>(file.readText())
        } catch (_: Exception) {
            file.delete()
            null
        }
    }

    /**
     * Persists coastline data to local storage.
     */
    private fun saveCache(result: CoastlineGenerationResult) {
        val file = cacheFile ?: return
        try {
            val cache = CoastlineCache(
                segments = result.segments,
                metadata = result.metadata
            )
            file.writeText(json.encodeToString(CoastlineCache.serializer(), cache))
        } catch (_: Exception) {
            // Non-critical — cache is a convenience, not a requirement
        }
    }

    /**
     * Removes the cached coastline file.
     */
    fun clearCache() {
        cacheFile?.delete()
    }

    /**
     * Restores coastline state from cached data (e.g. on app start).
     * Sets the repository state to [CoastlineState.Ready] without
     * fetching from the Overpass API.
     */
    fun restoreFromCache(
        segments: List<CoastlineSegment>,
        metadata: ykws.android.maro.data.model.CoastlineMetadata
    ) {
        rawPolylines = segments.map { it.points }
        _state.value = CoastlineState.Ready(
            polylines = segments,
            metadata = metadata
        )
    }

    // ── Generation pipeline ──────────────────────────────────────────────────

    /**
     * Launches the full generation pipeline.
     * Safe to call multiple times — will replace previous state.
     */
    suspend fun generate() {
        _state.value = CoastlineState.Loading
        _progress.value = GenerationProgress("", 0)

        try {
            val result = withContext(ioDispatcher) {
                generator.generate { phase, pct ->
                    _progress.value = GenerationProgress(phase, pct)
                }
            }

            // Store raw polylines for query methods
            rawPolylines = result.segments.map { it.points }

            // Persist to local cache for next launch
            saveCache(result)

            _state.value = CoastlineState.Ready(
                polylines = result.segments,
                metadata = result.metadata
            )
            _progress.value = GenerationProgress("Terminé", 100)
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
