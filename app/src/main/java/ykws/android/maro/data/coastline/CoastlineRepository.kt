package ykws.android.maro.data.coastline

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import ykws.android.maro.data.model.CoastlineData
import ykws.android.maro.data.model.CoastlineDistanceResult
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

    // ── Cache management (Protobuf binary) ───────────────────────────────────

    /** Initialise the cache directory from an Android [Context]. */
    fun setCacheDir(context: Context) {
        cacheDir = File(context.filesDir, "coastlines")
        cacheDir?.mkdirs()
    }

    /** Full path to the Protobuf cache file for the given region. */
    private fun cacheFile(regionId: String): File? =
        cacheDir?.resolve("$regionId.bin")

    /**
     * Reads and deserialises cached coastline data for the given region.
     * Returns `null` on cache miss, corrupt data, or I/O error (cache miss treated
     * the same as corrupt — fresh fetch is the fallback).
     */
    private fun readFromCache(regionId: String): CoastlineData? {
        val file = cacheFile(regionId) ?: return null
        if (!file.exists()) return null
        return try {
            CoastlineSerializer.deserialize(file.readBytes())
        } catch (_: Exception) {
            file.delete()
            null
        }
    }

    /**
     * Serialises and persists coastline data to a Protobuf binary file.
     * Failures are silently ignored — cache is a convenience, not a requirement.
     */
    private fun writeToCache(regionId: String, data: CoastlineData) {
        val file = cacheFile(regionId) ?: return
        try {
            file.writeBytes(CoastlineSerializer.serialize(data))
        } catch (_: Exception) {
            // Non-critical
        }
    }

    /**
     * Deletes the cached Protobuf file for the given region.
     */
    private fun deleteCacheFile(regionId: String) {
        cacheFile(regionId)?.delete()
    }

    // ── Load / Refresh (cache-aside pattern) ─────────────────────────────────

    /**
     * Loads coastline data for the given region using a cache-aside pattern:
     *   1. Check Protobuf cache on disk → return immediately if found
     *   2. On cache miss: fetch from OSM, run the generation pipeline,
     *      persist to Protobuf cache, then return
     *
     * @param regionId Region identifier (e.g. "nice-frejus").
     */
    suspend fun loadCoastline(regionId: String = CoastlineGenerator.REGION_ID) {
        _state.value = CoastlineState.Loading
        _progress.value = GenerationProgress("", 0)

        // 1. Check Protobuf cache first
        val cached = withContext(ioDispatcher) { readFromCache(regionId) }
        if (cached != null) {
            coastlineData = cached
            spatialIndex = CoastlineSpatialIndex(cached.allSegments)
            _state.value = CoastlineState.Ready(data = cached)
            _progress.value = GenerationProgress("Terminé (cache)", 100)
            return
        }

        // 2. Cache miss → full OSM generation
        try {
            val result = withContext(ioDispatcher) {
                generator.generate(regionId = regionId) { phase, pct ->
                    _progress.value = GenerationProgress(phase, pct)
                }
            }
            coastlineData = result
            spatialIndex = CoastlineSpatialIndex(result.allSegments)
            withContext(ioDispatcher) { writeToCache(regionId, result) }
            _state.value = CoastlineState.Ready(data = result)
            _progress.value = GenerationProgress("Terminé", 100)
        } catch (e: Exception) {
            _state.value = CoastlineState.Error(
                message = e.message ?: "Erreur lors du chargement de la côte."
            )
        }
    }

    /**
     * Forces a fresh OSM fetch by deleting the region's cache file first,
     * then delegating to [loadCoastline] (which will treat it as a cache miss).
     *
     * This is called by the "Régénérer" button.
     */
    suspend fun refreshCoastline(regionId: String = CoastlineGenerator.REGION_ID) {
        withContext(ioDispatcher) { deleteCacheFile(regionId) }
        loadCoastline(regionId)
    }

    // ── Query methods ──────────────────────────────────────────────────────

    /**
     * Returns true if the GPS position is on water.
     *
     * ## Algorithm — Ray Casting
     *
     * A vertical ray is cast SOUTH from the query point. Every time the ray
     * crosses the mainland coastline, it flips from water→land or land→water.
     * The parity of crossings determines the result:
     *   - **0, 2, 4, … (EVEN)** → query point and far-south are on the SAME side → WATER
     *   - **1, 3, 5, … (ODD)**  → opposite sides → LAND
     *
     * Islands are checked separately: if the query point is inside any
     * closed island polygon (odd crossings with that island's ring), it is
     * on land regardless of the mainland parity.
     *
     * ## Short-Circuits
     *
     * - No loaded data → `true` (safe default: assume water).
     * - Distance to coast > 6 NM → `true` (beyond regulatory zone = open water).
     *
     * ## Performance
     *
     * Uses [CoastlineSpatialIndex.queryColumn] to collect candidate segments
     * along the ray's vertical column only — ~5–20 segments checked per query
     * (vs. ~15,000 in the old brute-force approach). Each check is a cheap
     * boolean intersection test (no sqrt, no trig).
     */
    fun isOnWater(latitude: Double, longitude: Double): Boolean {
        val data = coastlineData ?: return true
        val index = spatialIndex ?: return true

        // ── Short-circuit: beyond 6 NM → open water ──
        val distToCoast = distanceToCoastMeters(latitude, longitude)
        if (distToCoast > SIX_NM_METERS) return true

        // ── Ray end latitude (6 NM south of query point) ──
        val mPerDegLat = SpatialOperations.EARTH_RADIUS_M * PI / 180.0
        val rayLatEnd = latitude - (SIX_NM_METERS / mPerDegLat)

        // ── Collect candidate segments along the vertical column ──
        val candidates = index.queryColumn(latitude, longitude, SIX_NM_METERS)

        // ── Mainland crossing count ──
        var mainlandCrossings = 0
        for (c in candidates) {
            if (c.polylineIdx == 0 &&
                SpatialOperations.rayCrossesSegmentSouth(longitude, latitude, rayLatEnd, c.a, c.b)
            ) {
                mainlandCrossings++
            }
        }

        // EVEN → same side as far-south reference → WATER
        val isWater = (mainlandCrossings % 2 == 0)

        // ── Island enclosure check ──
        if (isWater) {
            val islandGroups = candidates
                .filter { it.polylineIdx > 0 }
                .groupBy { it.polylineIdx }

            for ((_, segments) in islandGroups) {
                var crossings = 0
                for (seg in segments) {
                    if (SpatialOperations.rayCrossesSegmentSouth(
                            longitude, latitude, rayLatEnd, seg.a, seg.b
                    )) {
                        crossings++
                    }
                }
                if (crossings % 2 == 1) return false  // inside island → LAND
            }
        }

        return isWater
    }

    companion object {
        /** 6 nautical miles in metres (1 NM = 1,852 m exactly). */
        private const val SIX_NM_METERS = 6.0 * 1852.0  // = 11,112.0
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
