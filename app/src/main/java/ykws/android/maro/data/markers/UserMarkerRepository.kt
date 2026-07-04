package ykws.android.maro.data.markers

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import ykws.android.maro.data.model.markers.MarkerGeometry
import ykws.android.maro.data.model.markers.UserMarker
import java.io.File

/**
 * JSON file CRUD for [UserMarker] persistence.
 *
 * All file I/O runs on [Dispatchers.IO]. Markers are stored as a single JSON array
 * at `Internal storage/markers/user_markers.json` — simple and adequate for the
 * expected 15–20 marker ceiling.
 *
 * Threading contract (per design §7):
 * - Caller uses `viewModelScope.launch` + the default dispatcher implied by
 *   the suspend functions ([Dispatchers.IO] is used internally).
 */
class UserMarkerRepository(
    private val markersDir: File
) {
    init {
        markersDir.mkdirs()
    }

    constructor(context: Context) : this(File(context.filesDir, MARKERS_DIR_NAME))

    private var lastKnownGood: List<UserMarker>? = null

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    private val markersFile: File get() = File(markersDir, MARKERS_FILE_NAME)

    /** Load all markers from disk. Returns empty list if the file does not exist. */
    suspend fun loadAll(): List<UserMarker> = withContext(Dispatchers.IO) {
        if (!markersFile.exists()) return@withContext emptyList()
        try {
            val text = markersFile.readText()
            if (text.isBlank()) return@withContext emptyList()
            val result = json.decodeFromString<List<UserMarker>>(text)
            lastKnownGood = result
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load markers, falling back to last-known-good cache", e)
            lastKnownGood ?: emptyList()
        }
    }

    /** Persist the full list of markers atomically (write-to-tmp + rename). */
    suspend fun saveAll(markers: List<UserMarker>) = withContext(Dispatchers.IO) {
        if (markersFile.exists()) {
            markersFile.copyTo(File(markersDir, MARKERS_BACKUP_NAME), overwrite = true)
        }
        val tmp = File(markersDir, MARKERS_TMP_NAME)
        tmp.writeText(
            json.encodeToString(
                ListSerializer(UserMarker.serializer()),
                markers
            )
        )
        tmp.renameTo(markersFile)
        lastKnownGood = markers
    }

    /** Add a single marker and persist. */
    suspend fun add(marker: UserMarker) = withContext(Dispatchers.IO) {
        val all = loadAll().toMutableList()
        all.add(marker)
        saveAll(all)
    }

    /** Update an existing marker by ID (no-op if not found). */
    suspend fun update(marker: UserMarker) = withContext(Dispatchers.IO) {
        val all = loadAll().toMutableList()
        val idx = all.indexOfFirst { it.id == marker.id }
        if (idx >= 0) {
            all[idx] = marker
            saveAll(all)
        }
    }

    /** Delete a marker by ID. */
    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val all = loadAll().toMutableList()
        all.removeAll { it.id == id }
        saveAll(all)
    }

    /**
     * Compute the default proximity range (metres) for a marker, using the
     * configured defaults and any per-marker override.
     *
     * @param marker                 The marker to evaluate.
     * @param proximityPinM          Default proximity for Pin markers (from config).
     * @param proximityZoneMultiplier Default multiplier for Circle/Corridor proximity (from config).
     * @return The effective proximity range in metres.
     */
    fun proximityRangeM(
        marker: UserMarker,
        proximityPinM: Double,
        proximityZoneMultiplier: Double
    ): Double {
        marker.proximityOverrideM?.let { return it }
        return when (val g = marker.geometry) {
            is MarkerGeometry.Pin -> proximityPinM
            is MarkerGeometry.Circle -> g.radiusM * proximityZoneMultiplier
            is MarkerGeometry.Corridor -> g.widthM * proximityZoneMultiplier
        }
    }

    companion object {
        private const val MARKERS_DIR_NAME = "markers"
        private const val MARKERS_FILE_NAME = "user_markers.json"
        private const val MARKERS_TMP_NAME = "user_markers.json.tmp"
        private const val MARKERS_BACKUP_NAME = "user_markers.json.bak"
        private const val TAG = "UserMarkerRepo"
    }
}
