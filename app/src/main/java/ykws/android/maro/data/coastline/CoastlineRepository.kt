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
import ykws.android.maro.data.model.CoastlineData
import ykws.android.maro.data.model.CoastlineDistanceResult
import ykws.android.maro.data.model.CoastlineSegment
import ykws.android.maro.data.model.CoastlineState
import ykws.android.maro.data.model.GenerationProgress
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.spatial.CoastlineSpatialIndex
import ykws.android.maro.spatial.SpatialOperations
import java.io.File
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

    /** Directory for caching coastline data (set via [setCacheDir]). */
    private var cacheDir: File? = null

    private val json = Json { ignoreUnknownKeys = true }

    /** Start in [Idle] so generation does not auto-start on init. */
    private val _state = MutableStateFlow<CoastlineState>(CoastlineState.Idle)
    val state: StateFlow<CoastlineState> = _state.asStateFlow()

    /**
     * The raw coastline data. Used by query methods.
     */
    private var coastlineData: CoastlineData? = null

    /**
     * Spatial index for fast nearest-coastline queries.
     * Built once when coastline data is loaded — null when no data is available.
     */
    private var spatialIndex: CoastlineSpatialIndex? = null

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
    private fun saveCache(data: CoastlineData) {
        val file = cacheFile ?: return
        try {
            val cache = CoastlineCache(
                segments = data.allSegments,
                metadata = data.metadata
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
        spatialIndex = CoastlineSpatialIndex(segments)
        _state.value = CoastlineState.Ready(
            data = CoastlineData(
                mainland = segments.firstOrNull() ?: return,
                islands = segments.drop(1),
                metadata = metadata,
                regionId = "nice-frejus",
                boundingBox = ykws.android.maro.data.model.BoundingBox.EMPTY
            )
        )
    }

    // ── Generation pipeline ──────────────────────────────────────────────────

    /**
     * Launches the full generation pipeline for the given region.
     * Safe to call multiple times — will replace previous state.
     */
    suspend fun generate(regionId: String = CoastlineGenerator.REGION_ID) {
        _state.value = CoastlineState.Loading
        _progress.value = GenerationProgress("", 0)

        try {
            val result = withContext(ioDispatcher) {
                generator.generate(regionId = regionId) { phase, pct ->
                    _progress.value = GenerationProgress(phase, pct)
                }
            }

            coastlineData = result

            // Build spatial index for fast distance queries
            spatialIndex = CoastlineSpatialIndex(result.allSegments)

            // Persist to local cache for next launch
            saveCache(result)

            _state.value = CoastlineState.Ready(data = result)
            _progress.value = GenerationProgress("Terminé", 100)
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
     * Returns the minimum distance (meters) from a GPS position to the nearest
     * coastline point — mainland or island — together with the exact closest
     * point on the coastline.
     *
     * Uses the spatial index for O(1) lookup (80–150× faster than brute-force).
     * Falls back to a sentinel result when no coastline is loaded.
     */
    fun distanceToCoast(latitude: Double, longitude: Double): CoastlineDistanceResult {
        return spatialIndex?.query(latitude, longitude)
            ?: CoastlineDistanceResult(
                distanceMeters = Double.MAX_VALUE,
                closestPoint = LatLng(latitude, longitude),
                segmentId = "",
                isMainland = true
            )
    }

    /**
     * Returns the minimum distance (meters) from a GPS position to the coastline.
     * Convenience delegate to [distanceToCoast].
     */
    fun distanceToCoastMeters(latitude: Double, longitude: Double): Double =
        distanceToCoast(latitude, longitude).distanceMeters

    /**
     * Returns the cached [CoastlineData] if loaded, null otherwise.
     */
    fun getCoastlineData(): CoastlineData? = coastlineData

    /**
     * Returns true if the repository has loaded coastline data.
     */
    fun isLoaded(): Boolean = _state.value is CoastlineState.Ready
}
