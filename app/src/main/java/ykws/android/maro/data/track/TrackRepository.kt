package ykws.android.maro.data.track

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.protobuf.ProtoBuf
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

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
        val stamped = track.copy(updatedAtEpochMs = System.currentTimeMillis())
        val file = trackFile(stamped.id)
        // Atomic write: temp + atomic move so a crash never leaves a corrupt .bin.
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeBytes(proto.encodeToByteArray(Track.serializer(), stamped))
        atomicReplace(tmp, file)
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

    /** Update track metadata (name, comment). */
    suspend fun updateMetadata(id: String, name: String? = null, comment: String? = null) {
        val track = load(id) ?: return
        val updated = track.copy(
            name = name ?: track.name,
            comment = comment ?: track.comment
        )
        save(updated)
    }

    /** Set the pinned flag on a track. */
    suspend fun setPinned(id: String, pinned: Boolean) {
        val track = load(id) ?: return
        save(track.copy(pinned = pinned))
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
                    val track = proto.decodeFromByteArray(Track.serializer(), file.readBytes())
                    // Guard: if the finalized .bin already exists, this checkpoint was already
                    // persisted — delete it to avoid a duplicate recovery prompt.
                    if (trackFile(track.id).exists()) { file.delete(); null } else track
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
        val derived = track.withDerivedStats()
        val finalized = derived.copy(
            endTimeMs = derived.lastPointTimeMs.takeIf { it != 0L } ?: now,
            updatedAtEpochMs = now
        )
        save(finalized)
        deleteCheckpoint(track.id)
        finalized
    }

    /**
     * One-time migration: repair stats + lastPointTimeMs on tracks saved before those fields were
     * populated (recovered-from-checkpoint signature). Runs on first launch after the schema bump;
     * idempotent. Returns the number of tracks updated.
     */
    suspend fun migrateTrackStats(): Int = withContext(Dispatchers.IO) {
        var updated = 0
        tracksDir.listFiles()
            ?.filter { it.extension == "bin" && !it.name.endsWith("_checkpoint.bin") && it.name != INDEX_FILE_NAME }
            ?.forEach { file ->
                try {
                    val track = proto.decodeFromByteArray(Track.serializer(), file.readBytes())
                    if (track.endTimeMs == null) return@forEach
                    val needsStats = track.distanceNm == 0f && track.averageSpeedMps == 0f &&
                        track.idleDurationSec == 0L && track.fastestSpeedMps > 0f &&
                        track.trackPoints.size >= 2
                    val needsLastPoint = track.lastPointTimeMs == 0L
                    if (needsStats || needsLastPoint) {
                        val repaired = if (needsStats) track.withDerivedStats()
                            else track.copy(lastPointTimeMs = track.lastRealPointTimeMsOrNull() ?: 0L)
                        val tmp = File(file.parentFile, "${file.name}.tmp")
                        tmp.writeBytes(proto.encodeToByteArray(Track.serializer(), repaired))
                        atomicReplace(tmp, file)
                        updated++
                    }
                } catch (_: Exception) {
                    // Skip corrupt files — load/rebuildIndex already delete those on read.
                }
            }
        if (updated > 0) rebuildIndex()
        updated
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
                        pausedDurationSec = track.pausedDurationSec,
                        idleDurationSec = track.idleDurationSec,
                        averageSpeedMps = track.averageSpeedMps,
                        pinned = track.pinned,
                        pointCount = track.trackPoints.size,
                        updatedAtEpochMs = track.updatedAtEpochMs,
                        lastPointTimeMs = track.lastPointTimeMs.takeIf { it != 0L }
                            ?: (track.lastRealPointTimeMsOrNull() ?: 0L)
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

    /** Atomically replace [dest] with [src] (same-directory temp file). */
    private fun atomicReplace(src: File, dest: File) {
        try {
            Files.move(src.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            // ATOMIC_MOVE unsupported on some filesystems — fall back to plain replace.
            src.copyTo(dest, overwrite = true)
            src.delete()
        }
    }

    companion object {
        private const val TRACKS_DIR_NAME = "tracks"
        private const val INDEX_FILE_NAME = "index.bin"
    }
}
