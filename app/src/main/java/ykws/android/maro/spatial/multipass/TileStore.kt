package ykws.android.maro.spatial.multipass

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

    companion object {
        /** The default seam: nothing is ever stored and every [load] misses. */
        val NoOp: TileStore = object : TileStore {
            override suspend fun load(key: TileKey): FineTile? = null
            override suspend fun save(key: TileKey, tile: FineTile) {}
            override suspend fun delete(key: TileKey) {}
            override suspend fun list(): List<String> = emptyList()
        }
    }
}
