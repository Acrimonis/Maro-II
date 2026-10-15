package ykws.android.maro.spatial.multipass

/**
 * **What the store says about its own fullness** — the reading a warming policy stops on, so it stops on
 * the store's real ceiling instead of a magic entry count.
 *
 * It carries a **verdict beside the total** because the directory store trims its oldest entries back to
 * its ceiling on every write that overflows it: what it holds is therefore never *above* the ceiling, and
 * a policy testing `bytesHeld >= ceilingBytes` would warm against a wall that never shows. [atCeiling] is
 * the store's own answer instead, and it is **live**: it says whether one more entry of the size the store
 * last wrote would still fit, recomputed on every read rather than remembered from a write. A verdict
 * latched on the first reclamation would keep the warm-up dead for the rest of the process even after the
 * directory had room again — the growth this seam exists to serve.
 *
 * A store that cannot say answers `null` rather than a zero-filled instance: a policy reading an unknown
 * as empty would warm forever, which is the one failure this read exists to prevent.
 */
internal data class StoreFullness(
    /** The bytes the store holds right now. */
    val bytesHeld: Long,
    /** The ceiling the store reclaims at. */
    val ceilingBytes: Long,
    /** Whether the store has no room for another entry — its own live verdict, never an inequality on the
     *  pair above, and never a verdict remembered from an earlier write. */
    val atCeiling: Boolean
)

/**
 * **The fine-tile persistence seam — where a built tile is stored so a later process reuses it.**
 *
 * It is **pure Kotlin on purpose**: `spatial/multipass` stays free of Android types, so the file access
 * lives behind this interface and is injected at the app edge (see `DirectoryFineTileStore` and
 * [`FineTileCache.install`]). The default, [NoOp], stores nothing and every [load] is a miss, so a map
 * built without a store behaves exactly as it did before the seam existed.
 *
 * **A miss is the only failure.** [load] answers `null` for an absent entry, a corrupt one, one written
 * by another format version, or one whose stored key does not match — never a wrong tile — so a broken
 * store degrades to the build. [save] is **best-effort**: a store that cannot write must never fail a
 * lookup, and [FineTileMap] swallows its failures.
 *
 * **The store's work is signalled through its holder, not here.** [`FineTileCache.busy`] is the one
 * debounced window the routing square's dot reads, and the warm-up's own tiles alone feed it — an arm's
 * store reads never light it. This seam stays a pure read/write and carries no signal of its own.
 * [fullness] is the one exception in spirit — a read the warming policy makes *about* the store — and it
 * is still a plain read: it neither opens, closes nor debounces the window.
 */
internal interface TileStore {

    /** The tile stored for [key], or `null` on any miss — absent, corrupt, another version or another key. */
    suspend fun load(key: TileKey): FineTile?

    /** Persist [tile] for [key]. Best-effort: a failure must never fail a lookup. */
    suspend fun save(key: TileKey, tile: FineTile)

    /** Remove [key]'s entry, where one stands. */
    suspend fun delete(key: TileKey)

    /** The entries the store currently holds, as opaque names — diagnostics and reclamation. */
    suspend fun list(): List<String>

    /**
     * **The store's own fullness, or `null` where it cannot say.** Fed by the running byte total the
     * directory store already keeps — primed once by a listing and adjusted on every save and delete — so
     * this read pays no listing of its own. `null` where no total exists yet (nothing has been written
     * this run and nothing has been counted) and for a store that keeps none at all: an unknown is stated
     * as one, never dressed as an empty store.
     */
    suspend fun fullness(): StoreFullness?

    companion object {
        /** The default seam: nothing is ever stored and every [load] misses. */
        val NoOp: TileStore = object : TileStore {
            override suspend fun load(key: TileKey): FineTile? = null
            override suspend fun save(key: TileKey, tile: FineTile) {}
            override suspend fun delete(key: TileKey) {}
            override suspend fun list(): List<String> = emptyList()
            override suspend fun fullness(): StoreFullness? = null
        }
    }
}
