package ykws.android.maro.spatial.multipass

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * **The fine layer's tile map — a `TileKey` → [`FineTile`] cache that grows lazily as the boat explores.**
 *
 * A tile is built on its first request, and concurrent requests for the same key share the one build:
 * the first caller becomes the builder and computes the tile, the rest await its [`CompletableDeferred`].
 * That is the `LadderGridHolder` idiom lifted one level down — the same check-publish-outside-the-lock
 * single-flight, keyed here rather than held per arm — so an arm names a set of tiles and the map serves
 * each once.
 *
 * **The budget is a byte ceiling with LRU eviction, and the entry's own in-flight count is the
 * protection.** A tile's primitive bytes are counted as it lands, and once the ceiling is passed the
 * least-recently-used completed entries **no caller still holds** are dropped until it holds again: a
 * tile a builder or an awaiting caller is on is never a victim, and a still-building entry is skipped
 * while its [`CompletableDeferred`] is unfinished. There is no separate pin mechanism — that protection
 * *is* the entry's in-flight count. A [hitRate] rides beside the ceiling, so the tile edge is tuned on a
 * number rather than a guess, counting only requests **served from a completed cache entry**.
 *
 * **A miss consults the [store] before it builds, and a built tile is written back.** That is the one
 * addition the seam makes: the single-flight, the ceiling and the LRU are unmoved, and a storeless map
 * (the default [TileStore.NoOp]) behaves exactly as before the seam existed. The store is best-effort —
 * a load or a save that fails degrades to a build, never to a failed lookup.
 */
internal class FineTileMap(
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
    private val store: TileStore = TileStore.NoOp
) {

    /** One key's slot: the shared build and the callers holding it. */
    private class Entry {
        /** The one build this key shares — completed by its builder, awaited by every other caller. */
        val done = CompletableDeferred<FineTile>()

        /** How many callers currently hold this tile — the in-flight protection an eviction honours. */
        var inFlight: Int = 0

        /** The tile's bytes, once built — what an eviction gives back. */
        var bytes: Long = 0L
    }

    /** Guards the map, the byte total and the counters. Held only for arithmetic, never across an `await`. */
    private val lock = Mutex()

    /** The live tiles, in least-recently-used order (the head is the oldest). */
    private val entries = LinkedHashMap<TileKey, Entry>()

    /**
     * The bytes the live tiles hold — volatile, so the reading getters below ([heldBytes], [size],
     * [hitRate]) see a whole `Long` rather than a torn one on a 32-bit VM (D62).
     */
    @Volatile
    private var bytes: Long = 0L

    /** How many requests were served without a build — cached, or from the store — advisory, unlocked. */
    @Volatile
    var hits: Long = 0L
        private set

    /** How many requests had to build a tile, or wait on one another caller was building — advisory. */
    @Volatile
    var misses: Long = 0L
        private set

    /** The share of requests served from a completed cache entry without building or waiting, in `0.0..1.0`. */
    val hitRate: Double
        get() = if (hits + misses == 0L) 0.0 else hits.toDouble() / (hits + misses).toDouble()

    /** How many tiles the map currently holds. */
    val size: Int get() = entries.size

    /** The bytes the map currently holds. */
    val heldBytes: Long get() = bytes

    /**
     * **The tile for [key], built on [build] at most once.** The first caller for a key becomes the
     * builder and runs [build] outside the lock; every later caller awaits the one deferred. A caller
     * that finds a **completed** entry is a hit; a caller that finds one still building is a miss, because
     * it waits on the build rather than reusing it. The builder's own request is a hit too when the
     * [store] served it without a build (D10), so [hitRate] counts every request served without a build.
     * [build] may suspend, so a test can hold a build open to observe the single-flight.
     */
    suspend fun get(key: TileKey, build: suspend () -> FineTile): FineTile {
        val (entry, builder) = lock.withLock {
            val found = entries[key]
            if (found != null) {
                found.inFlight++
                if (found.done.isCompleted) hits++ else misses++
                // The access reorders the map: drop and re-add puts it at the tail, the LRU end.
                entries.remove(key)
                entries[key] = found
                found to false
            } else {
                val fresh = Entry()
                fresh.inFlight++
                misses++
                entries[key] = fresh
                fresh to true
            }
        }
        try {
            if (!builder) return entry.done.await()
            val served = try {
                loadOrBuild(key, build)
            } catch (failure: Throwable) {
                lock.withLock { entries.remove(key) }
                entry.done.completeExceptionally(failure)
                throw failure
            }
            val tile = served.tile
            // A store-served lookup is a **hit**, not the miss the builder's fresh slot was counted as:
            // no build ran, so the request was reused rather than computed (D10).
            if (served.fromStore) lock.withLock { misses--; hits++ }
            entry.bytes = tile.byteSize
            entry.done.complete(tile)
            lock.withLock {
                bytes += tile.byteSize
                evictToBudget()
            }
            return tile
        } finally {
            lock.withLock { entry.inFlight-- }
        }
    }

    /**
     * **The builder's one lookup-and-build.** A tile the [store] already holds is served without building
     * — the point of the seam — and only a **miss** runs [build] and, once the tile lands, writes it back.
     * A failing store never fails the lookup: a load that throws is a miss and a save that throws is
     * discarded, while a cancellation is rethrown untouched so a cancelled arm stays cancelled. The
     * outcome says whether the store served it, so the caller counts the served request a **hit**.
     */
    private suspend fun loadOrBuild(key: TileKey, build: suspend () -> FineTile): Served {
        val stored = try {
            store.load(key)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            null
        }
        if (stored != null) return Served(stored, fromStore = true)
        val built = build()
        try {
            store.save(key, built)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            // Best-effort: a store that cannot write must not fail the lookup.
        }
        return Served(built, fromStore = false)
    }

    /** One lookup's outcome — the tile, and whether the [store] served it rather than a build running. */
    private class Served(val tile: FineTile, val fromStore: Boolean)

    /** Drops completed tiles no caller holds from the LRU end until the ceiling holds. */
    private fun evictToBudget() {
        while (bytes > maxBytes) {
            val victim = entries.entries.firstOrNull { (_, entry) ->
                entry.inFlight == 0 && entry.done.isCompleted
            } ?: return
            entries.remove(victim.key)
            bytes -= victim.value.bytes
        }
    }

    companion object {
        /** The default byte ceiling — eight mebibytes, roughly a hundred and fifty mid-sized tiles. */
        const val DEFAULT_MAX_BYTES: Long = 8L * 1024L * 1024L
    }
}
