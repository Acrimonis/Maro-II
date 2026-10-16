package ykws.android.maro.spatial.multipass

/**
 * **What the store says about its own fullness** — the reading a warming policy stops on, so it stops on
 * the store's real ceiling instead of a magic entry count.
 *
 * It carries the **size of the entry the store last wrote** beside the total because the directory store
 * trims its oldest entries back to its ceiling on every write that overflows it: what it holds is therefore
 * never *above* the ceiling, and a policy testing `bytesHeld >= ceilingBytes` would warm against a wall that
 * never shows. The honest question is whether one more entry **of that size** would still fit, and it is
 * **live**: recomputed on every read rather than remembered from a write, so a verdict latched on the first
 * reclamation cannot leave the warm-up dead for the rest of the process after the directory has room again —
 * the growth this seam exists to serve. [hasRoomAt] asks it against a share of the ceiling, which is the
 * question the warm-up puts; [atCeiling] is the same one asked in full.
 *
 * A store that cannot say answers `null` rather than a zero-filled instance: a policy reading an unknown
 * as empty would warm forever, which is the one failure this read exists to prevent.
 */
internal data class StoreFullness(
    /** The bytes the store holds right now. */
    val bytesHeld: Long,
    /** The ceiling the store reclaims at. */
    val ceilingBytes: Long,
    /** The size of the entry the store wrote last — the entry both readings below measure the next one
     *  against, which is the one honest reading of "full" a self-trimming store can give. */
    val nextEntryBytes: Long
) {
    /**
     * **Whether one more entry of [nextEntryBytes] still fits under [targetPct] per cent of the ceiling.**
     * A share below 100 leaves the rest as a reserve, so water outside the area a policy prepared lands in
     * free space instead of evicting what it had already paid for. The arithmetic is the store's own
     * inequality and nothing else, so a policy can neither overshoot the ceiling nor need a second read.
     */
    fun hasRoomAt(targetPct: Int): Boolean =
        bytesHeld + nextEntryBytes <= ceilingBytes * targetPct / 100

    /** Whether the store has no room for another entry at its own ceiling — [hasRoomAt] asked in full. */
    val atCeiling: Boolean get() = !hasRoomAt(100)
}

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
