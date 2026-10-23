package ykws.android.maro.data.markers

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import ykws.android.maro.data.model.markers.MarkerGeometry
import ykws.android.maro.data.model.markers.UserMarker
import ykws.android.maro.data.model.markers.markerProximityRangeM
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

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

    /** True while the main file failed to decode — the backup is then not overwritten by a save. */
    @Volatile private var mainFileCorrupt = false

    /** True after a load where neither the main file nor the backup decoded; cleared by the next good read. */
    @Volatile private var storeUnreadable = false

    /**
     * The one JSON configuration a marker is written with — [MarkerPayload.json], the payload codec's
     * own, so the file on disk and the readable copy a GPX export carries cannot drift apart.
     */
    private val json = MarkerPayload.json

    /** Serializes load-modify-save cycles — two writers (UI VM + service) share the file. */
    private val writeMutex = Mutex()

    private val markersFile: File get() = File(markersDir, MARKERS_FILE_NAME)
    private val backupFile: File get() = File(markersDir, MARKERS_BACKUP_NAME)

    /** Load all markers from disk. Returns empty list if the file does not exist. */
    suspend fun loadAll(): List<UserMarker> = withContext(Dispatchers.IO) {
        if (!markersFile.exists()) {
            mainFileCorrupt = false
            storeUnreadable = false
            return@withContext emptyList()
        }
        try {
            val result = decode(markersFile)
            lastKnownGood = result
            mainFileCorrupt = false
            storeUnreadable = false
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load markers, trying the backup", e)
            mainFileCorrupt = true
            val recovered = loadBackup()
            val cached = lastKnownGood
            when {
                recovered != null -> {
                    lastKnownGood = recovered
                    storeUnreadable = false
                    recovered
                }
                cached != null -> cached
                else -> {
                    // Nothing readable anywhere: an empty answer, but one a save must not make canonical.
                    storeUnreadable = true
                    emptyList()
                }
            }
        }
    }

    private fun decode(file: File): List<UserMarker> {
        val text = file.readText()
        return if (text.isBlank()) emptyList() else json.decodeFromString<List<UserMarker>>(text)
    }

    /** The backup's contents, or null when it is absent or unreadable too. */
    private fun loadBackup(): List<UserMarker>? {
        if (!backupFile.exists()) return null
        return try {
            decode(backupFile).also { Log.w(TAG, "Markers recovered from backup: ${it.size}") }
        } catch (e: Exception) {
            Log.e(TAG, "Backup unreadable too", e)
            null
        }
    }

    /** Persist the full list of markers atomically. Serialized against concurrent writers. */
    suspend fun saveAll(markers: List<UserMarker>) = writeMutex.withLock { saveAllLocked(markers) }

    /**
     * The write itself: tmp file → atomic move over the main file. Callers hold [writeMutex], which is
     * not reentrant, so this never locks. An [IOException] propagates — a failed replace is not a save.
     */
    private suspend fun saveAllLocked(markers: List<UserMarker>) = withContext(Dispatchers.IO) {
        if (storeUnreadable && markers.isEmpty()) {
            throw IOException("Marker store unreadable and no backup decoded: refusing to overwrite it with an empty list")
        }
        if (markersFile.exists() && !mainFileCorrupt) {
            markersFile.copyTo(backupFile, overwrite = true)
        }
        val tmp = File(markersDir, "$MARKERS_FILE_NAME.${UUID.randomUUID()}.tmp")
        try {
            tmp.writeText(
                json.encodeToString(
                    ListSerializer(UserMarker.serializer()),
                    markers
                )
            )
            try {
                Files.move(
                    tmp.toPath(), markersFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE
                )
            } catch (e: AtomicMoveNotSupportedException) {
                Files.move(tmp.toPath(), markersFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            tmp.delete() // a leftover only when the write or the move failed
        }
        lastKnownGood = markers
        mainFileCorrupt = false
        _markerChanges.tryEmit(Unit)
    }

    /** Add a single marker and persist. Serialized against concurrent writers. */
    suspend fun add(marker: UserMarker) = writeMutex.withLock {
        val all = loadAll().toMutableList()
        all.add(marker)
        saveAllLocked(all)
    }

    /** Update an existing marker by ID (no-op if not found). Serialized against concurrent writers. */
    suspend fun update(marker: UserMarker) = writeMutex.withLock {
        val all = loadAll().toMutableList()
        val idx = all.indexOfFirst { it.id == marker.id }
        if (idx >= 0) {
            all[idx] = marker
            saveAllLocked(all)
        }
    }

    /** Delete a marker by ID. Serialized against concurrent writers. */
    suspend fun delete(id: String) = writeMutex.withLock {
        val all = loadAll().toMutableList()
        all.removeAll { it.id == id }
        saveAllLocked(all)
    }

    /** Set the pinned flag on a marker (mirrors [TrackRepository.setPinned]). */
    suspend fun setPinned(id: String, pinned: Boolean) = writeMutex.withLock {
        val all = loadAll().toMutableList()
        val idx = all.indexOfFirst { it.id == id }
        if (idx >= 0) {
            all[idx] = all[idx].copy(pinned = pinned)
            saveAllLocked(all)
        }
    }

    /**
     * The effective proximity range (metres) for a marker — a thin door onto the rule's own home,
     * [markerProximityRangeM], where the per-type fallback lives because the map, the matcher and the
     * route's price well all read the same reach.
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
    ): Double = markerProximityRangeM(marker, proximityPinM, proximityZoneMultiplier)

    companion object {
        private const val MARKERS_DIR_NAME = "markers"
        private const val MARKERS_FILE_NAME = "user_markers.json"
        private const val MARKERS_BACKUP_NAME = "user_markers.json.bak"
        private const val TAG = "UserMarkerRepo"

        private val _markerChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

        /** Process-scoped change channel — emits whenever any instance persists markers. */
        val markerChanges: SharedFlow<Unit> = _markerChanges.asSharedFlow()
    }
}
