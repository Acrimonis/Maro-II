package ykws.android.maro.data.track

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import ykws.android.maro.data.markers.UserMarkerRepository
import ykws.android.maro.data.model.markers.MarkerOrigin
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
    private val tracksDir: File,
    private val markerRepo: UserMarkerRepository? = null
) {
    init {
        tracksDir.mkdirs()
    }

    constructor(context: Context) : this(
        File(context.filesDir, TRACKS_DIR_NAME),
        UserMarkerRepository(context)
    )

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

    /**
     * Upsert a track by id: replaces the existing file when the id already exists.
     * Used by the import UPDATE path, which keeps the matched track's id so
     * marker→track links survive.
     */
    suspend fun saveOrReplace(track: Track) = save(track)

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

    /**
     * Delete a track and remove it from the index.
     *
     * Cascades to derived IDLE_AUTO markers in a single loadAll → filter → saveAll:
     * every marker whose [ykws.android.maro.data.model.markers.UserMarker.trackId]
     * equals [id] is removed. [excludeActiveTrackId] protects the live recording's
     * markers — when it equals [id], the cascade is skipped (never touch the active
     * recording track's temp marker).
     */
    suspend fun delete(id: String, excludeActiveTrackId: String? = null) = withContext(Dispatchers.IO) {
        markerRepo?.let { repo ->
            if (id != excludeActiveTrackId) {
                val markers = repo.loadAll()
                val remaining = markers.filterNot { m ->
                    m.origin == MarkerOrigin.IDLE_AUTO && m.trackId == id
                }
                if (remaining.size != markers.size) repo.saveAll(remaining)
            }
        }
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
                    val endMs = track.endTimeMs ?: return@forEach
                    var repaired = track
                    var changed = false

                    // Recovered-checkpoint signature: stats all zero but points + max speed exist.
                    val needsStats = track.distanceNm == 0f && track.averageSpeedMps == 0f &&
                        track.idleDurationSec == 0L && track.fastestSpeedMps > 0f &&
                        track.trackPoints.size >= 2
                    if (needsStats) {
                        repaired = track.withDerivedStats()
                        changed = true
                    } else if (track.lastPointTimeMs == 0L) {
                        repaired = track.copy(lastPointTimeMs = track.lastRealPointTimeMsOrNull() ?: 0L)
                        changed = true
                    }

                    // Idle under-count repair (raw-vs-simplified jitter): raise idle, never lower.
                    val lpt = repaired.lastPointTimeMs
                    if (lpt != 0L && repaired.trackPoints.size >= 2) {
                        val spanSec = (lpt - repaired.startTimeMs) / 1000
                        val tlIdle = timelineIdleSec(repaired.trackPoints)
                        val tailSec = maxOf(0L, (endMs - lpt) / 1000)
                        val storedDataIdle = maxOf(0L, repaired.idleDurationSec - tailSec)
                        if (tlIdle > storedDataIdle + 60) {
                            repaired = repaired.copy(
                                idleDurationSec = tlIdle,
                                navigatingDurationSec = maxOf(0L, spanSec - tlIdle)
                            )
                            changed = true
                        }
                    }

                    if (changed) {
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

    /**
     * One-time backfill: map legacy `BoatMarker.autoMarkerId` (protobuf field 8,
     * now removed from the model) → [UserMarker.trackId] on the matching marker.
     *
     * Reads the old `.bin` files with a legacy schema that still carries field 8,
     * then writes a single `saveAll` of the updated marker list. Idempotent — the
     * caller gates it behind a version flag. Returns the number of markers linked.
     */
    suspend fun migrateMarkerTrackLink(): Int = withContext(Dispatchers.IO) {
        val repo = markerRepo ?: return@withContext 0

        // autoMarkerId → owning track id (from persisted protobuf field 8).
        val linkMap = mutableMapOf<String, String>()
        tracksDir.listFiles()
            ?.filter { it.extension == "bin" && !it.name.endsWith("_checkpoint.bin") && it.name != INDEX_FILE_NAME }
            ?.forEach { file ->
                try {
                    val legacy = proto.decodeFromByteArray(LegacyTrack.serializer(), file.readBytes())
                    for (bm in legacy.boatMarkers) {
                        val markerId = bm.autoMarkerId ?: continue
                        linkMap[markerId] = legacy.id
                    }
                } catch (_: Exception) {
                    // Skip corrupt files — load/rebuildIndex already delete those on read.
                }
            }

        if (linkMap.isEmpty()) return@withContext 0
        val markers = repo.loadAll()
        var changed = 0
        val updated = markers.map { m ->
            val ownerId = if (m.trackId == null) linkMap[m.id] else null
            if (ownerId != null) {
                changed++
                m.copy(trackId = ownerId)
            } else m
        }
        if (changed > 0) repo.saveAll(updated)
        changed
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

// ── Legacy protobuf schema for the one-time marker↔track backfill ─────────────
// `BoatMarker.autoMarkerId` (@ProtoNumber 8) was removed from the live model.
// These minimal mirrors still read field 8 so pre-migration `.bin` files can be
// decoded for the backfill. Field numbers must match the old live schema.

@Serializable
private data class LegacyTrack(
    @ProtoNumber(1) val id: String = "",
    @ProtoNumber(16) val boatMarkers: List<LegacyBoatMarker> = emptyList()
)

@Serializable
private data class LegacyBoatMarker(
    @ProtoNumber(8) val autoMarkerId: String? = null
)
