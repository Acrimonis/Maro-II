package ykws.android.maro.spatial.multipass

/**
 * **The fine-tile map's one process-level home — the cache an engine switch keeps and the store's one
 * reader and writer.**
 *
 * A `TileKey` names a tile's water and nothing arm-specific, so the same tile is valid for `evolutive`
 * and `selective`, for an arm and its neighbour, and for one process run and the next. The map used to
 * live on the [`RouteGridBuilder`], one per engine, so switching engine — or restarting the process —
 * paid the marking again. This holder is the one map the app edge installs a [TileStore] into, and every
 * builder prefers it once installed.
 *
 * **Absent an install, a builder keeps its own map** ([shared] is `null`), so a JVM test that never
 * installs a store is isolated exactly as before: no two tests can share a tile, and a fixture's key
 * cannot collide with a neighbour's. Production installs once, at the app edge, before any engine arms.
 */
internal object FineTileCache {

    /** The installed process-level map, or `null` before the app edge installs one. */
    @Volatile
    var shared: FineTileMap? = null
        private set

    /**
     * **Install the process-level [store].** The map is replaced whole, so a caller installs before any
     * arm — a later install (an Activity recreation, say) drops the in-memory tiles and the next arm
     * re-reads them from the store.
     */
    fun install(store: TileStore, maxBytes: Long = FineTileMap.DEFAULT_MAX_BYTES) {
        shared = FineTileMap(maxBytes, store)
    }

    /** Drop the installed map — a builder then keeps its own. The test seam, and the app's own reset. */
    fun reset() {
        shared = null
    }
}
