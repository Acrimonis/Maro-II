package ykws.android.maro.data.track

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.protobuf.ProtoBuf
import java.io.File

/**
 * Protobuf file CRUD for [Track] persistence.
 *
 * All file I/O runs on [Dispatchers.IO]. The constructor accepts a [File] for
 * the tracks directory, enabling temp-directory testing.
 *
 * **File layout:**
 * - `tracks/{id}.bin` — serialised [Track] protobuf
 * - `tracks/{id}_checkpoint.bin` — mid-recording checkpoint
 * - `tracks/index.bin` — serialised [TrackSummaryList] for fast listing
 */
class TrackRepository(
    private val tracksDir: File
) {
    init {
        tracksDir.mkdirs()
    }

    constructor(context: Context) : this(File(context.filesDir, TRACKS_DIR_NAME))

    private val proto = ProtoBuf.Default

    /** Save a completed track. */
    suspend fun save(track: Track) = withContext(Dispatchers.IO) {
        val file = trackFile(track.id)
        file.writeBytes(proto.encodeToByteArray(Track.serializer(), track))
        updateIndex()
    }

    /** Load a single track by ID. */
    suspend fun load(id: String): Track? = withContext(Dispatchers.IO) {
        val file = trackFile(id)
        if (!file.exists()) return@withContext null
        try {
            proto.decodeFromByteArray(Track.serializer(), file.readBytes())
        } catch (e: Exception) {
            file.delete()
            null
        }
    }

    /** List all track summaries from the index. */
    suspend fun listTracks(): List<TrackSummary> = withContext(Dispatchers.IO) {
        val indexFile = indexFile()
        if (!indexFile.exists()) return@withContext rebuildIndex()
        try {
            val summaryList = proto.decodeFromByteArray(TrackSummaryList.serializer(), indexFile.readBytes())
            summaryList.tracks
        } catch (e: Exception) {
            indexFile.delete()
            rebuildIndex()
        }
    }

    /** Delete a track and remove it from the index. */
    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        trackFile(id).delete()
        deleteCheckpoint(id)
        updateIndex()
    }

    /** Update track metadata (name, comment, visibility). */
    suspend fun updateMetadata(id: String, name: String? = null, comment: String? = null, visibleOnMap: Boolean? = null) {
        val track = load(id) ?: return
        val updated = track.copy(
            name = name ?: track.name,
            comment = comment ?: track.comment,
            visibleOnMap = visibleOnMap ?: track.visibleOnMap
        )
        save(updated)
    }

    /** Save a mid-recording checkpoint (fast: writes only current state). */
    suspend fun saveCheckpoint(track: Track) = withContext(Dispatchers.IO) {
        val file = checkpointFile(track.id)
        file.writeBytes(proto.encodeToByteArray(Track.serializer(), track))
    }

    /** Delete a checkpoint file. */
    suspend fun deleteCheckpoint(id: String) = withContext(Dispatchers.IO) {
        checkpointFile(id).delete()
    }

    /** Scan for orphaned checkpoint files (from a crash) and return them. */
    suspend fun recoverOrphanedCheckpoints(): List<Track> = withContext(Dispatchers.IO) {
        tracksDir.listFiles()
            ?.filter { it.name.endsWith("_checkpoint.bin") }
            ?.mapNotNull { file ->
                try {
                    proto.decodeFromByteArray(Track.serializer(), file.readBytes())
                } catch (e: Exception) {
                    file.delete()
                    null
                }
            }
            ?: emptyList()
    }

    /** Finalize an orphaned checkpoint into a complete track. */
    suspend fun finalizeOrphanedCheckpoint(track: Track): Track = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val finalized = track.copy(
            endTimeMs = now,
            navigatingDurationSec = (now - track.startTimeMs) / 1000 - track.pausedDurationSec
        )
        deleteCheckpoint(track.id)
        save(finalized)
        finalized
    }

    // ── Internal helpers ──────────────────────────────────────────────────

    private fun trackFile(id: String): File = File(tracksDir, "${id}.bin")
    private fun checkpointFile(id: String): File = File(tracksDir, "${id}_checkpoint.bin")
    private fun indexFile(): File = File(tracksDir, INDEX_FILE_NAME)

    /** Rebuild the index by scanning all `.bin` files. */
    private suspend fun rebuildIndex(): List<TrackSummary> = withContext(Dispatchers.IO) {
        val summaries = tracksDir.listFiles()
            ?.filter { it.extension == "bin" && !it.name.endsWith("_checkpoint.bin") && it.name != INDEX_FILE_NAME }
            ?.mapNotNull { file ->
                try {
                    val track = proto.decodeFromByteArray(Track.serializer(), file.readBytes())
                    TrackSummary(
                        id = track.id,
                        name = track.name,
                        comment = track.comment,
                        startTimeMs = track.startTimeMs,
                        endTimeMs = track.endTimeMs,
                        fastestSpeedMps = track.fastestSpeedMps,
                        distanceNm = track.distanceNm,
                        visibleOnMap = track.visibleOnMap,
                        navigatingDurationSec = track.navigatingDurationSec,
                        pausedDurationSec = track.pausedDurationSec
                    )
                } catch (e: Exception) {
                    file.delete()
                    null
                }
            } ?: emptyList()

        val indexData = proto.encodeToByteArray(TrackSummaryList.serializer(), TrackSummaryList(summaries))
        indexFile().writeBytes(indexData)
        summaries
    }

    /** Write the index from current track files. */
    private suspend fun updateIndex() = withContext(Dispatchers.IO) {
        rebuildIndex()
    }

    companion object {
        private const val TRACKS_DIR_NAME = "tracks"
        private const val INDEX_FILE_NAME = "index.bin"
    }
}
