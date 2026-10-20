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
 * **One build's tiles, never another's.** The entries live in a folder named by [buildStamp] — the app's own
 * install instant — and the first access deletes every other build's folder, so a tile a previous build
 * computed is never read after an install: the store needs no format version, and no change to how a tile
 * is built has to remember to bump one.
 *
 * **A miss is the only failure, and a bad entry is discarded.** A read is decoded through
 * [`FineTileCodec`], which verifies the magic and the **whole key** the entry
 * carries; a short, foreign or key-mismatched file answers `null` and is deleted, so a partial or corrupt
 * store falls back to the build rather than half-reading it. A write is **atomic** — a temp file renamed
 * over the target — so a half-written entry is never visible. A served tile **touches** its entry's age,
 * so least-recently-used reclamation never evicts water a live arm still needs.
 *
 * **A disk ceiling bounds the directory, softly.** Past [maxBytes] the least recently used unprotected
 * entries are reclaimed, so a store a boat keeps extending across seasons cannot grow without bound; the
 * ceiling is a sibling of [`FineTileMap`]'s in-memory one, not a second invalidation — a stamp change is
 * still a different key and therefore a miss. Protection is a **floor**, not an exemption from
 * accounting: where the protected home area alone stands above the ceiling, the store keeps it and says
 * once that the ceiling cannot be reached rather than taking a tile the walk prepared. The ceiling's one
 * home is the `route.fineTile.maxBytes` property ([DEFAULT_MAX_BYTES] mirrors it), read at the app edge.
 *
 * **The total is incremental, and it rides an access-ordered index.** The running byte total and the
 * entry index are primed **once** by a listing and then adjusted on every save, delete and drop, so a
 * save never re-sums nor re-lists the directory — the O(n²) the naive per-save sum carried once hundreds
 * of tiles landed, and the listing-and-sort every overflowing save used to pay. The index
 * ([index]) is a `LinkedHashMap` in **access order**: its head is the least recently used entry and its
 * tail the one just served or written, so a reclaim walks it head-first instead of sorting the directory.
 * A load miss drops that name's accounting on the spot, and a file gone behind the store is dropped too —
 * the index may over-report fullness, never serve a tile. Each load and each save emits one `STORE` line
 * through the injected [trace] sink, each reclamation one `STORE trim` line, and the first reclamation
 * raises [onReclaim] once.
 *
 * **The total is also what the store answers about itself.** [fullness] hands the running total, this
 * store's ceiling and **the size of the entry it wrote last** to the warming policy, which asks
 * [StoreFullness.hasRoomAt] rather than guessing from an entry count — one more entry of that size under the
 * share of the ceiling the walk is held to, which leaves the rest a reserve. That reading is **live, never a
 * latch**: a save trims the directory back to its ceiling, so what it holds is never *above* it unless the
 * protected floor is, while a verdict remembered from a past reclamation would keep the warm-up dead for
 * the rest of the process even once the directory had room again. The read pays no listing, and an
 * unprimed total answers `null` rather than zero, so a policy can never mistake "not counted yet" for
 * "nothing stored".
 *
 * [`FineTileMap`]: ykws.android.maro.spatial.multipass.FineTileMap
 */
internal class DirectoryFineTileStore(
    /** The store's root; the entries live in its [buildStamp] folder. */
    private val root: File,
    /**
     * The installed build's own stamp — the app's last install instant at the app edge — or `null`, where
     * the entries live in [root] itself and nothing is cleared (a caller with one build only, as a test).
     */
    private val buildStamp: Long? = null,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
    /** Raised **once**, on the first reclamation — the app edge surfaces it (D9). Inert by default. */
    private val onReclaim: () -> Unit = {},
    /** Where one `STORE` line per load and per save goes — the app edge maps it to the device log. */
    private val trace: (String) -> Unit = {}
) : TileStore {

    /**
     * **The entry index, in access order** — entry name → its bytes, the head least recently used. Primed
     * once by the same listing that primes [dirBytes], tail-moved on every load and save, pruned on delete
     * and on any load miss. It is an estimate of the directory, never a source of tiles: the file stays
     * the truth (D4).
     */
    private val index = LinkedHashMap<String, Long>(16, 0.75f, true)

    /**
     * **This build's entry folder**, resolved on the first access — always off the UI thread — where every
     * other build's folder, and any entry left loose in [root] by a store from before the folders, is deleted.
     */
    private val dir: File by lazy {
        if (buildStamp == null) return@lazy root
        val current = File(root, buildStamp.toString())
        val stale = root.listFiles()?.filter { it.name != current.name } ?: emptyList()
        for (old in stale) old.deleteRecursively()
        if (stale.isNotEmpty()) trace("STORE cleared builds=${stale.size} build=$buildStamp")
        current
    }

    /** The running byte total, primed once by a listing and adjusted per save and delete; `-1` = unprimed. */
    private var dirBytes: Long = -1L

    /**
     * The bytes the last save wrote — the entry a further save would have to fit beside. `0` before any
     * save, and the one fact [readFullness] reads its verdict off, so no reclamation has to be remembered.
     */
    private var lastEntryBytes: Long = 0L

    /** The entry names the walk must never reclaim — the home area, as the store last heard it (D1). */
    private var protectedNames: Set<String> = emptySet()

    /** The protected names' own byte total, kept current by every save, delete and drop (D2). */
    private var protectedBytes: Long = 0L

    /** True once a reclamation has been reported, so [onReclaim] fires at most once per store. */
    private var reclaimedReported: Boolean = false

    /**
     * True once the "cannot reach the ceiling" line has been printed. The latch is on the **line only**,
     * never on the behaviour: a later trim after the protected set shrinks still evicts (D6).
     */
    private var ceilingUnreachableReported: Boolean = false

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
            // A file gone behind the store leaves its accounting with it — the index may over-report, never serve.
            dropAccounting(name)
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
            dropAccounting(name)
            trace("STORE load name=$name hit=false")
        } else {
            // A served tile is touched, so least-recently-used reclamation never evicts it while a live arm needs it.
            file.setLastModified(System.currentTimeMillis())
            touchEntry(name, file.length())
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
                    recordWrite(target.name, bytes.size.toLong())
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
            val name = FineTileCodec.entryName(key)
            val file = File(dir, name)
            // A file already gone is the same accounting drop: what the index counted must leave with it.
            if (file.delete() || !file.exists()) recordDelete(name)
        }
    }

    /**
     * **The entries the store holds, read off the directory itself** (D5). It is deliberately **not** the
     * index: the prewarm reads a name as proof that a file stands ([`FineTilePrewarm.pending`]), so a name
     * the index still carried after the OS purged the directory would suppress that tile's build for a
     * whole walk. One listing per walk is nothing beside the per-save scan the index removed.
     */
    override suspend fun list(): List<String> = withContext(Dispatchers.IO) {
        dir.listFiles()
            ?.filter { it.isFile && !it.name.endsWith(".tmp") }
            ?.map { it.name }
            ?: emptyList()
    }

    /**
     * **The entries the store must keep** — the home area's names as the walk derived them (D1, D7).
     * Where the index has not been primed yet the set is remembered and counted at priming, so a set that
     * arrives before the first listing still carries its byte total (D2).
     */
    override fun protect(names: Set<String>) {
        applyProtection(names)
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
            nextEntryBytes = lastEntryBytes
        )

    /**
     * **Adjusts the running total for a write, and keeps the entry's own size.** The first save on a fresh
     * store **primes** it with one listing (which already counts the file just written); every later save
     * adjusts by the difference the rename made, so no save ever re-sums the directory. The entry moves to
     * the index's tail — the write is the freshest use it has had. The size is kept because it is what
     * [readFullness] measures the next entry against — the one honest reading of "at the ceiling" a store
     * trimming itself back to that ceiling can give.
     */
    @Synchronized
    private fun recordWrite(name: String, bytesWritten: Long) {
        lastEntryBytes = bytesWritten
        if (dirBytes < 0L) prime()
        // The name may already stand: a rewrite replaces its old bytes rather than adding to them.
        val previous = index.remove(name) ?: 0L
        dirBytes -= previous
        if (name in protectedNames) protectedBytes -= previous
        index[name] = bytesWritten
        dirBytes += bytesWritten
        if (name in protectedNames) protectedBytes += bytesWritten
    }

    /** Subtracts a deleted file's bytes from the running total, where the index counted them. */
    @Synchronized
    private fun recordDelete(name: String) {
        val known = index.remove(name) ?: return
        if (dirBytes >= 0L) dirBytes -= known
        if (name in protectedNames) protectedBytes = (protectedBytes - known).coerceAtLeast(0L)
    }

    /** Drops a name the file behind the store lost, with its bytes and its share of the protected total. */
    @Synchronized
    private fun dropAccounting(name: String) {
        val known = index.remove(name) ?: return
        if (dirBytes >= 0L) dirBytes -= known
        if (name in protectedNames) protectedBytes = (protectedBytes - known).coerceAtLeast(0L)
    }

    /** Re-keys a served entry to the index's tail, correcting its bytes where the file's length moved. */
    @Synchronized
    private fun touchEntry(name: String, actualBytes: Long) {
        if (dirBytes < 0L) return
        val known = index.remove(name) ?: return
        dirBytes += actualBytes - known
        if (name in protectedNames) protectedBytes += actualBytes - known
        index[name] = actualBytes
    }

    /** Takes the caller's protection set, recomputing the protected total where the index is primed. */
    @Synchronized
    private fun applyProtection(names: Set<String>) {
        protectedNames = names
        if (dirBytes < 0L) {
            // Priming counts the protected bytes itself, so a set arriving first is not lost (D2).
            protectedBytes = 0L
            return
        }
        var total = 0L
        for ((entry, bytes) in index) if (entry in names) total += bytes
        protectedBytes = total
    }

    /**
     * **The running total, primed with one listing** — the only full-directory read this store ever pays,
     * counted by [directoryScans]. The listing is walked oldest-first, so the index's head is the oldest
     * entry and its tail the newest even across a process restart; the protected names are counted here,
     * so a set that arrived before the first listing still has its byte total.
     */
    @Synchronized
    private fun prime() {
        if (dirBytes >= 0L) return
        directoryScans++
        val files = dir.listFiles()
        if (files == null) {
            dirBytes = 0L
            protectedBytes = 0L
            return
        }
        var total = 0L
        var protectedTotal = 0L
        for (file in files.sortedBy { it.lastModified() }) {
            val bytes = file.length()
            index[file.name] = bytes
            total += bytes
            if (file.name in protectedNames) protectedTotal += bytes
        }
        dirBytes = total
        protectedBytes = protectedTotal
    }

    /**
     * **Reclaims from the index's head until the directory holds at most [maxBytes].** Protected names are
     * skipped, each victim is unlinked and dropped as it goes, and a file already gone costs an accounting
     * drop that still counts as evicted. The walk returns before any I/O only where nothing unprotected
     * remains — a protected floor above the ceiling is still no reason to keep outer water that would
     * otherwise go (D5) — and says so once per store, the latch on the line alone (D6).
     */
    @Synchronized
    private fun trim() {
        prime()
        if (dirBytes <= maxBytes) return
        val startedMs = System.currentTimeMillis()
        if (dirBytes <= protectedBytes) {
            reportCeilingUnreachable(startedMs)
            return
        }
        var evicted = 0
        var stuck = false
        val victim = index.entries.iterator()
        while (dirBytes > maxBytes) {
            if (dirBytes <= protectedBytes) {
                stuck = true
                break
            }
            var candidate: Map.Entry<String, Long>? = null
            while (victim.hasNext()) {
                val entry = victim.next()
                if (entry.key !in protectedNames) {
                    candidate = entry
                    break
                }
            }
            if (candidate == null) {
                // The index ran out: every name left is protected, so the ceiling cannot be reached.
                stuck = true
                break
            }
            val bytes = candidate.value
            val file = File(dir, candidate.key)
            if (file.delete() || !file.exists()) {
                victim.remove()
                dirBytes -= bytes
                evicted++
            } else {
                // The file would not go: leaving it in the accounting keeps the total honest and stops the walk.
                stuck = true
                break
            }
        }
        if (evicted > 0) {
            trace("STORE trim evicted=$evicted remaining=$dirBytes protected=$protectedBytes ms=${System.currentTimeMillis() - startedMs}")
            if (!reclaimedReported) {
                reclaimedReported = true
                onReclaim()
            }
        } else if (stuck) {
            reportCeilingUnreachable(startedMs)
        }
    }

    /** The once-per-store "nothing more can go" line — the latch is on the line, never on [trim]. */
    private fun reportCeilingUnreachable(startedMs: Long) {
        if (ceilingUnreachableReported) return
        ceilingUnreachableReported = true
        trace(
            "STORE trim cannot reach ceiling evicted=0 remaining=$dirBytes protected=$protectedBytes " +
                "ms=${System.currentTimeMillis() - startedMs}"
        )
    }

    companion object {
        /** The store directory's name — a sibling of the depth raster's own `raster` cache. */
        const val DIR_NAME = "fine_tiles"

        /**
         * The default disk ceiling (96 MiB), mirroring `route.fineTile.maxBytes`. That property key is the
         * value's one home, read at the app edge; this default stands for a caller that names none (a test).
         * It is a soft ceiling: a protected home area may hold the store above it.
         */
        const val DEFAULT_MAX_BYTES: Long = 96L * 1024L * 1024L
    }
}
