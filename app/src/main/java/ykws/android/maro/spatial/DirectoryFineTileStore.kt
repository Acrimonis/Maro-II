package ykws.android.maro.spatial

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ykws.android.maro.spatial.multipass.FineTile
import ykws.android.maro.spatial.multipass.FineTileCodec
import ykws.android.maro.spatial.multipass.StoreFullness
import ykws.android.maro.spatial.multipass.TileKey
import ykws.android.maro.spatial.multipass.TileStore
import java.io.File

/**
 * **The fine tiles on disk — one file an entry, in a directory beside the depth raster's own cache.**
 *
 * It is the app-side implementation of the pure [`TileStore`] seam: the file access lives here, so
 * `spatial/multipass` imports no Android type and the store is injected at the app edge
 * ([`FineTileCache.install`]). It reads **lazily, one file a tile**, and never loads the directory at
 * startup, so a cold process pays only for the tiles the first arm actually reaches.
 *
 * **A miss is the only failure, and a bad entry is discarded.** A read is decoded through
 * [`FineTileCodec`], which verifies the magic, the format version and the **whole key** the entry
 * carries; a short, foreign or key-mismatched file answers `null` and is deleted, so a partial or corrupt
 * store falls back to the build rather than half-reading it. A write is **atomic** — a temp file renamed
 * over the target — so a half-written entry is never visible. A served tile **touches** its entry's age,
 * so oldest-first reclamation never evicts water a live arm still needs.
 *
 * **A disk ceiling bounds the directory.** Past [maxBytes] the oldest entries (by last-modified) are
 * reclaimed, so a store a boat keeps extending across seasons cannot grow without bound; the ceiling is a
 * sibling of [`FineTileMap`]'s in-memory one, not a second invalidation — a stamp change is still a
 * different key and therefore a miss. The ceiling's one home is the `route.fineTile.maxBytes` property
 * ([DEFAULT_MAX_BYTES] mirrors it), read at the app edge.
 *
 * **The total is incremental.** The running byte total is primed **once** by a listing and then adjusted
 * on every save and delete, so a save never re-sums the directory — the O(n²) the naive per-save sum
 * carried once hundreds of tiles landed. Each load and each save emits one `STORE` line through the
 * injected [trace] sink, and the first reclamation raises [onReclaim] once.
 *
 * **The total is also what the store answers about itself.** [fullness] hands the running total and this
 * store's own ceiling verdict ([StoreFullness.atCeiling]) to the warming policy, which stops on it rather
 * than on a guessed entry count. That verdict is a **live reading, never a latch**: a save trims the
 * directory back to its ceiling, so what it holds is never *above* it and an inequality on the pair can
 * never show, while a verdict remembered from a past reclamation would keep the warm-up dead for the rest of
 * the process even once the directory had room again. The read pays no listing, and an unprimed total
 * answers `null` rather than zero, so a policy can never mistake "not counted yet" for "nothing stored".
 *
 * [`FineTileMap`]: ykws.android.maro.spatial.multipass.FineTileMap
 */
internal class DirectoryFineTileStore(
    private val dir: File,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
    /** Raised **once**, on the first reclamation — the app edge surfaces it (D9). Inert by default. */
    private val onReclaim: () -> Unit = {},
    /** Where one `STORE` line per load and per save goes — the app edge maps it to the device log. */
    private val trace: (String) -> Unit = {}
) : TileStore {

    /** The running byte total, primed once by a listing and adjusted per save and delete; `-1` = unprimed. */
    private var dirBytes: Long = -1L

    /**
     * The bytes the last save wrote — the entry a further save would have to fit beside. `0` before any
     * save, and the one fact [readFullness] reads its verdict off, so no reclamation has to be remembered.
     */
    private var lastEntryBytes: Long = 0L

    /** True once a reclamation has been reported, so [onReclaim] fires at most once per store. */
    private var reclaimedReported: Boolean = false

    /**
     * How many times the whole directory has been read for its total — one per store at most, so a test
     * can prove a save never re-sums it. Advisory; the reads and writes are [Synchronized].
     */
    internal var directoryScans: Int = 0
        private set

    override suspend fun load(key: TileKey): FineTile? = withContext(Dispatchers.IO) {
        val name = FineTileCodec.entryName(key)
        val file = File(dir, name)
        if (!file.isFile) {
            trace("STORE load name=$name hit=false")
            return@withContext null
        }
        val tile = try {
            FineTileCodec.decode(key, file.readBytes())
        } catch (failure: Exception) {
            null
        }
        if (tile == null) {
            // A miss — corrupt, another version, another key — is discarded rather than re-read on every arm.
            file.delete()
            trace("STORE load name=$name hit=false")
        } else {
            // A served tile is touched, so oldest-first reclamation never evicts it while a live arm needs it.
            file.setLastModified(System.currentTimeMillis())
            trace("STORE load name=$name hit=true")
        }
        tile
    }

    override suspend fun save(key: TileKey, tile: FineTile) {
        withContext(Dispatchers.IO) {
            val bytes = try {
                FineTileCodec.encode(key, tile)
            } catch (failure: Exception) {
                return@withContext
            }
            dir.mkdirs()
            val target = File(dir, FineTileCodec.entryName(key))
            val temp = File(dir, "${target.name}.tmp")
            try {
                temp.writeBytes(bytes)
                if (!temp.renameTo(target)) {
                    temp.delete()
                } else {
                    recordWrite(bytes.size.toLong())
                    trim()
                    trace("STORE save name=${target.name} bytes=${bytes.size}")
                }
            } catch (failure: Exception) {
                temp.delete()
            }
        }
    }

    override suspend fun delete(key: TileKey) {
        withContext(Dispatchers.IO) {
            val file = File(dir, FineTileCodec.entryName(key))
            val length = file.length()
            if (file.delete()) recordDelete(length)
        }
    }

    override suspend fun list(): List<String> = withContext(Dispatchers.IO) {
        dir.listFiles()?.map { it.name } ?: emptyList()
    }

    /**
     * **The running total and whether the store has room for another entry.** No listing is paid: the total
     * is read as it stands, and a total that has never been primed answers `null` — the warming policy's
     * "unknown", which it must read as anything but an empty store. The verdict is **live and cannot
     * latch**: it answers whether **one more entry the size of the one just written would not fit**,
     * recomputed on every read from the total and that entry's bytes, so a directory that has room again
     * reads as having room again. A verdict taken from a past reclamation would instead have kept the
     * warm-up dead for the rest of the process, which is exactly what the growth exists to avoid.
     */
    override suspend fun fullness(): StoreFullness? = withContext(Dispatchers.IO) { readFullness() }

    /** The total's own read, guarded like every other adjustment of it. `null` while it is unprimed. */
    @Synchronized
    private fun readFullness(): StoreFullness? =
        if (dirBytes < 0L) null
        else StoreFullness(
            bytesHeld = dirBytes,
            ceilingBytes = maxBytes,
            atCeiling = dirBytes + lastEntryBytes > maxBytes
        )

    /**
     * **Adjusts the running total for a write, and keeps the entry's own size.** The first save on a fresh
     * store **primes** it with one listing (which already counts the file just written); every later save
     * adds its own bytes, so no save ever re-sums the directory. The size is kept because it is what
     * [readFullness] measures the next entry against — the one honest reading of "at the ceiling" a store
     * trimming itself back to that ceiling can give.
     */
    @Synchronized
    private fun recordWrite(bytesWritten: Long) {
        lastEntryBytes = bytesWritten
        if (dirBytes < 0L) runningTotal() else dirBytes += bytesWritten
    }

    /** Subtracts a deleted file's bytes from the running total, where it has been primed. */
    @Synchronized
    private fun recordDelete(length: Long) {
        if (dirBytes >= 0L) dirBytes -= length
    }

    /**
     * The running total, primed with **one** listing where it is not yet primed — the only full-directory
     * read this store ever pays, counted by [directoryScans].
     */
    @Synchronized
    private fun runningTotal(): Long {
        if (dirBytes < 0L) {
            directoryScans++
            dirBytes = dir.listFiles()?.sumOf { it.length() } ?: 0L
        }
        return dirBytes
    }

    /** Drops the oldest entries until the directory holds at most [maxBytes] — a disk-ceiling reclamation. */
    @Synchronized
    private fun trim() {
        if (runningTotal() <= maxBytes) return
        val files = dir.listFiles() ?: return
        var total = dirBytes
        var deleted = 0
        for (file in files.sortedBy { it.lastModified() }) {
            if (total <= maxBytes) break
            val length = file.length()
            if (file.delete()) {
                total -= length
                deleted++
            }
        }
        dirBytes = total
        if (deleted > 0 && !reclaimedReported) {
            reclaimedReported = true
            trace("STORE reclaim evicted=$deleted remainingBytes=$total")
            onReclaim()
        }
    }

    companion object {
        /** The store directory's name — a sibling of the depth raster's own `raster` cache. */
        const val DIR_NAME = "fine_tiles"

        /**
         * The default disk ceiling (48 MiB), mirroring `route.fineTile.maxBytes`. That property key is the
         * value's one home, read at the app edge; this default stands for a caller that names none (a test).
         */
        const val DEFAULT_MAX_BYTES: Long = 48L * 1024L * 1024L
    }
}
