package ykws.android.maro.spatial.multipass

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow

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
 *
 * **The busy signal's own home is here too** ([busy]). An arm reaches the store one tile at a time, so a
 * bare in-flight count would fall in the few-millisecond gap between two tiles; instead this holder
 * debounces the store's fine-layer work through a [FineTileActivity] — installed into the map as its
 * `onBusy` sink — so the routing square's dot stands for the whole pass of one arming and clears a grace
 * after its last tile. It is a [StateFlow] the app edge collects, in the spirit of `DirectoryFineTileStore`'s
 * own `trace` and `onReclaim` sinks, and no Android type reaches this module. The window's two edges are
 * traced too ([traceTo]) — one `busy on` / `busy off` line each — so a device pass reads the window rather
 * than eyeballing the dot.
 */
internal object FineTileCache {

    /** The installed process-level map, or `null` before the app edge installs one. */
    @Volatile
    var shared: FineTileMap? = null
        private set

    /**
     * **How long the busy window holds after the last unit of store work (ms).** The map reaches the store
     * one tile at a time, so the gap between two tiles is only the loop's own `writeInto` and key build — a
     * few milliseconds — and without a hold the window would fall there and blink the dot. 400 ms rides
     * over that gap with room for scheduling jitter, yet clears the dot well under half a second after the
     * pass's last tile, so "busy" reads as the pass and not as a tail.
     */
    const val BUSY_GRACE_MS: Long = 400L

    /** Where the window's two edges go — inert until the app edge points it at the route tag. */
    @Volatile
    private var traceSink: ((String) -> Unit)? = null

    /** The one busy window — its fall timer rides a process-lifetime scope, the holder's own. */
    private val activity = FineTileActivity(
        graceMs = BUSY_GRACE_MS,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        trace = { line -> traceSink?.invoke(line) }
    )

    /** The fine layer's busy window — collected by the app edge and threaded to the routing square. */
    val busy: StateFlow<Boolean> get() = activity.busy

    /**
     * **Raise the window for a whole pre-warm pass.** An arm's pass is short enough that the 400 ms grace
     * rides over the gap between its tiles, but the pre-warm walks a whole box and its tiles may sit a
     * continent of milliseconds apart — long enough that the grace would let the dot fall between distant
     * tiles and read as a flicker. So the pass **holds** the window for its own duration instead: one
     * `busy on` opens at its first tile and one `busy off` closes it when the pass ends, and the grace
     * keeps its own job (merging an arm's tiles) untouched. [endPrewarm] releases the hold.
     */
    fun beginPrewarm() {
        activity.hold()
    }

    /** Release the pre-warm's hold — the window falls once no work is left in flight. See [beginPrewarm]. */
    fun endPrewarm() {
        activity.release()
    }

    /**
     * **Point the window's trace at the route tag.** Each edge then emits one line — `busy on` when the
     * store's work starts, `busy off` a grace after its last tile — so a device pass is read rather than
     * eyeballed. Inert until the app edge installs the log, in the spirit of the store's own `trace` sink.
     */
    fun traceTo(sink: (String) -> Unit) {
        traceSink = sink
    }

    /**
     * **Install the process-level [store].** The map is replaced whole, so a caller installs before any
     * arm — a later install (an Activity recreation, say) drops the in-memory tiles and the next arm
     * re-reads them from the store. The map reports each store-facing lookup to [activity], so the busy
     * window tracks the work the store actually does.
     */
    fun install(store: TileStore, maxBytes: Long = FineTileMap.DEFAULT_MAX_BYTES) {
        activity.reset()
        shared = FineTileMap(maxBytes, store) { active ->
            if (active) activity.begin() else activity.end()
        }
    }

    /** Drop the installed map — a builder then keeps its own. The test seam, and the app's own reset. */
    fun reset() {
        shared = null
        activity.reset()
    }
}
