package ykws.android.maro.spatial.multipass

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex

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
 * **The busy signal's own home is here too** ([busy]). The walk builds one tile at a time, so a bare
 * in-flight count would fall in the few-millisecond gap between two tiles; instead this holder debounces
 * the warm-up's own tiles through a [FineTileActivity] — [beginWarmTile] as the walk starts one,
 * [endWarmTile] as it finishes — so the routing square's dot stands for the walk's real building and
 * clears a grace after its last tile. **Nothing an arm does can light it**: the map reports no per-lookup
 * activity, because a route computing over the store is not the warm-up. It is a [StateFlow] the app edge
 * collects, in the spirit of `DirectoryFineTileStore`'s own `trace` and `onReclaim` sinks, and no Android
 * type reaches this module. The window's two edges are traced too ([traceTo]) — one `busy on` / `busy off`
 * line each — so a device pass reads the window rather than eyeballing the dot.
 */
internal object FineTileCache {

    /** The installed process-level map, or `null` before the app edge installs one. */
    @Volatile
    var shared: FineTileMap? = null
        private set

    /**
     * The store the map above was installed with, or `null` before an install. Kept here so the warm-up's
     * own ceiling probe ([storeFullness]) reaches the store the map reads and writes, without the pass ever
     * holding a store reference of its own.
     */
    @Volatile
    private var installedStore: TileStore? = null

    /**
     * **How long the busy window holds after the last tile the walk built (ms).** The walk builds one tile
     * at a time, so the gap between two tiles is only the loop's own `writeInto` and key build — a few
     * milliseconds — and without a hold the window would fall there and blink the dot. 400 ms rides over
     * that gap with room for scheduling jitter, yet clears the dot well under half a second after the
     * walk's last tile, so "busy" reads as the walk and not as a tail.
     */
    const val BUSY_GRACE_MS: Long = 400L

    /** Where the window's two edges go — inert until the app edge points it at the route tag. */
    @Volatile
    private var traceSink: ((String) -> Unit)? = null

    /**
     * **The one outward-walk slot** ([`warmOutward`] takes it for its whole duration). The warm-up is a
     * process-level activity, and so is this: the host's effect restarts when the Activity is recreated,
     * while the walk it replaced is only **cooperatively** cancelled — still building the tile it had
     * started. Without the slot the successor would run beside it, holding the window twice, building the
     * same water twice and counting the ceiling twice. A newcomer **waits** rather than bailing, so the
     * warm-up a recreation interrupted is finished off rather than lost.
     */
    val walkSlot = Mutex()

    /** The fall timer's own home — a process-lifetime dispatcher, so the window outlives a recreation. */
    private val windowScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** The one busy window — rebuilt only by a reset, so the app edge's collector keeps its flow. */
    private var activity = newActivity(windowScope)

    /** The fine layer's busy window — collected by the app edge and threaded to the routing square. */
    val busy: StateFlow<Boolean> get() = activity.busy

    /**
     * **A tile the walk began building** — the window's rising edge. Called around the one `map.get` a
     * built tile costs, so the dot is lit while warm-up work actually runs and dark while the walk waits
     * at its gate or finds everything already warm. The grace merges consecutive tiles. See [endWarmTile].
     */
    fun beginWarmTile() {
        activity.begin()
    }

    /** **A tile the walk finished building** — the window falls a grace after the walk's last one. */
    fun endWarmTile() {
        activity.end()
    }

    /**
     * **The installed store's own fullness, or `null` where there is none to ask or it cannot say.** The
     * warm-up's ceiling read: [`warmOutward`] hands it to the pass, which stops rather than filling a store
     * that would only evict the water it just prepared. `null` — a storeless map, or a total not yet primed
     * — is stated as unknown, never as an empty store.
     */
    suspend fun storeFullness(): StoreFullness? = installedStore?.fullness()

    /**
     * **Point the window's trace at the route tag.** Each edge then emits one line — `busy on` when the
     * walk's first tile starts, `busy off` a grace after its last — so a device pass is read rather than
     * eyeballed. Inert until the app edge installs the log, in the spirit of the store's own `trace` sink.
     */
    fun traceTo(sink: (String) -> Unit) {
        traceSink = sink
    }

    /**
     * **Install the process-level [store].** The map is replaced whole, so a caller installs before any
     * arm — a later install (an Activity recreation, say) drops the in-memory tiles and the next arm
     * re-reads them from the store. The window is deliberately left alone: it is fed by the warm-up's own
     * tiles, and a recreation installs beside a walk only **cooperatively** cancelled — so an install must
     * never zero the tile that walk is still building.
     */
    fun install(store: TileStore, maxBytes: Long = FineTileMap.DEFAULT_MAX_BYTES) {
        installedStore = store
        shared = FineTileMap(maxBytes, store)
    }

    /** Drop the installed map — a builder then keeps its own. The test seam, and the app's own reset. */
    fun reset() {
        shared = null
        installedStore = null
        activity = newActivity(windowScope)
    }

    /**
     * **Reopen the window on [scope], a caller's own scheduler.** The app edge never calls this: the fall
     * timer rides [windowScope], the process-lifetime dispatcher, and only a JVM test needs the grace to
     * elapse on virtual time rather than the wall clock. The installed map is dropped with the old window,
     * exactly as [reset] drops it.
     */
    internal fun reset(scope: CoroutineScope) {
        shared = null
        installedStore = null
        activity = newActivity(scope)
    }

    /** One window over [scope], its edges routed to whatever sink [traceTo] currently names. */
    private fun newActivity(scope: CoroutineScope) =
        FineTileActivity(BUSY_GRACE_MS, scope, trace = { line -> traceSink?.invoke(line) })
}
