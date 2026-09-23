package ykws.android.maro.data.track

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import ykws.android.maro.data.markers.UserMarkerRepository
import ykws.android.maro.data.model.RegionBounds
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

    /**
     * The water test a position classification may use, and the region it can answer for. Both stay null
     * until the coastline is loaded, which is itself the gate: an unanswerable sample is never counted,
     * so "no data yet" can never bake itself into the index as water.
     */
    private var positionClassifier: ((Double, Double) -> Boolean?)? = null
    private var positionRegion: RegionBounds? = null

    /** One sampling pass per attachment, so a library with nothing to classify is not re-scanned. */
    private var positionPassDone = false

    /**
     * Attach the coastline-backed water test once it can answer, and let one pass of the tracks still
     * unclassified follow — the same injection shape the view models use for shared settings.
     */
    fun attachPositionClassifier(waterTest: (Double, Double) -> Boolean?, region: RegionBounds) {
        positionClassifier = waterTest
        positionRegion = region
        positionPassDone = false
    }

    /** Save a completed track. */
    suspend fun save(track: Track) = withContext(Dispatchers.IO) {
        val stamped = track.copy(updatedAtEpochMs = System.currentTimeMillis())
        val file = trackFile(stamped.id)
        // Atomic write: temp + atomic move so a crash never leaves a corrupt .bin.
        val tmp = File(file.parentFile, "${file.name}.${java.util.UUID.randomUUID()}.tmp")
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

    /** List all track summaries from the index, sampling any whose position counts are still absent. */
    suspend fun listTracks(): List<TrackSummary> = withContext(Dispatchers.IO) {
        classifyUnclassified(readIndex())
    }

    /**
     * The index as it stands: read when present, readable **and written with this build's summary
     * schema**, rebuilt from the track files otherwise.
     *
     * The version stamp is what makes the rebuild forced rather than hoped for: a summary field added
     * since the index was written would otherwise decode as its own default for every track already
     * stored — a missing bool reading `false` — and the filter reading it would answer nothing until
     * something else happened to mutate the library.
     */
    private suspend fun readIndex(): List<TrackSummary> = withContext(Dispatchers.IO) {
        val indexFile = indexFile()
        if (!indexFile.exists()) return@withContext rebuildIndex()
        try {
            val summaryList = proto.decodeFromByteArray(TrackSummaryList.serializer(), indexFile.readBytes())
            if (summaryList.version != SUMMARY_INDEX_VERSION) return@withContext rebuildIndex()
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

    /** Rebuild the index by scanning all `.bin` files, position counts carried or sampled per track. */
    private suspend fun rebuildIndex(): List<TrackSummary> = withContext(Dispatchers.IO) {
        val carried = carriedSummaries()
        val summaries = tracksDir.listFiles()
            ?.filter { it.extension == "bin" && !it.name.endsWith("_checkpoint.bin") && it.name != INDEX_FILE_NAME }
            ?.mapNotNull { file ->
                try {
                    val track = proto.decodeFromByteArray(Track.serializer(), file.readBytes())
                    val counts = classifyOrCarry(track, carried[track.id])
                    TrackSummary(
                        id = track.id,
                        name = track.name,
                        comment = track.comment,
                        startTimeMs = track.startTimeMs,
                        endTimeMs = track.endTimeMs,
                        fastestSpeedMps = track.fastestSpeedMps,
                        distanceNm = track.distanceNm,
                        navigatingDurationSec = track.navigatingDurationSec,
                        pausedDurationSec = track.pausedDurationSec,
                        idleDurationSec = track.idleDurationSec,
                        averageSpeedMps = track.averageSpeedMps,
                        pinned = track.pinned,
                        pointCount = track.trackPoints.size,
                        updatedAtEpochMs = track.updatedAtEpochMs,
                        lastPointTimeMs = track.lastPointTimeMs.takeIf { it != 0L }
                            ?: (track.lastRealPointTimeMsOrNull() ?: 0L),
                        // Both counts are stored biased by one (see TrackSummary), which makes an
                        // unclassified sample — (-1, -1) — land on the sentinel (0, 0) by itself.
                        waterPointCount = counts.water + 1,
                        landPointCount = counts.land + 1,
                        // The flag rides the pass that is already reading the whole track, so the lists
                        // and the map's action rules never need to load a track to know a route from a
                        // recording.
                        trace = track.trace
                    )
                } catch (e: Exception) {
                    file.delete()
                    null
                }
            } ?: emptyList()

        writeIndex(summaries)
        summaries
    }

    /** The index currently on disk, by id — read before a rebuild so counts can survive it. */
    private fun carriedSummaries(): Map<String, TrackSummary> {
        val indexFile = indexFile()
        if (!indexFile.exists()) return emptyMap()
        return try {
            proto.decodeFromByteArray(TrackSummaryList.serializer(), indexFile.readBytes())
                .tracks
                .associateBy { it.id }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    /**
     * The position counts for a freshly decoded track: the ones the index already carried when the track
     * has not been written since, or a fresh sample when the coastline can answer.
     *
     * Carrying them is what keeps one save from re-sampling the whole library, the index being re-derived
     * from the track files on every mutation; and a sample the coastline cannot answer for is never
     * cached, so a track keeps its flag clear rather than baking "no data yet" in as water.
     */
    private fun classifyOrCarry(track: Track, previous: TrackSummary?): TrackPositionCounts {
        if (previous != null &&
            previous.updatedAtEpochMs == track.updatedAtEpochMs &&
            previous.positionClassified
        ) {
            // Read back out of the stored bias, so the caller can bias the result again unchanged.
            return TrackPositionCounts(
                previous.waterPointCount - 1,
                previous.landPointCount - 1
            )
        }
        val test = positionClassifier ?: return TrackPositionCounts()
        val bounds = positionRegion ?: return TrackPositionCounts()
        return classifyTrackPosition(track.trackPoints, bounds, test)
    }

    /**
     * Sample the tracks whose position counts are still absent and write the index once if any changed.
     * The rebuild path samples too; this exists for the index that is *read* rather than rebuilt, without
     * which an upgraded install would show every legacy track under On water until something mutated the
     * library. One pass per attachment, so an out-of-region library is not re-scanned on every list call.
     */
    private suspend fun classifyUnclassified(
        summaries: List<TrackSummary>
    ): List<TrackSummary> = withContext(Dispatchers.IO) {
        if (positionPassDone) return@withContext summaries
        val test = positionClassifier ?: return@withContext summaries
        val bounds = positionRegion ?: return@withContext summaries
        if (summaries.all { it.positionClassified }) {
            positionPassDone = true
            return@withContext summaries
        }

        var changed = false
        val updated = summaries.map { summary ->
            if (summary.positionClassified) return@map summary
            val track = load(summary.id) ?: return@map summary
            val counts = classifyTrackPosition(track.trackPoints, bounds, test)
            if (!counts.isClassified) return@map summary
            changed = true
            // Stored biased, exactly as the rebuild path writes them.
            summary.copy(
                waterPointCount = counts.water + 1,
                landPointCount = counts.land + 1
            )
        }
        positionPassDone = true
        if (changed) writeIndex(updated)
        updated
    }

    /** Write the index from a summary list, stamped with the schema it was written with. */
    private suspend fun writeIndex(summaries: List<TrackSummary>) = withContext(Dispatchers.IO) {
        val indexData = proto.encodeToByteArray(
            TrackSummaryList.serializer(),
            TrackSummaryList(summaries, SUMMARY_INDEX_VERSION)
        )
        indexFile().writeBytes(indexData)
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
            try {
                src.copyTo(dest, overwrite = true)
                src.delete()
            } catch (_: Exception) {
                // Source temp file already consumed by a concurrent save — dest write
                // has already been done by the other coroutine.
            }
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
