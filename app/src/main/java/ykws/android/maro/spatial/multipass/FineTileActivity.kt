package ykws.android.maro.spatial.multipass

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * **The warm-up's busy window — one debounced signal for the fine layer's own building.**
 *
 * The walk builds one tile at a time, so a bare in-flight count would fall back to zero in the
 * few-millisecond gap between two tiles and blink the routing square's dot tile to tile. This window
 * instead **holds** busy for [graceMs] after the last tile: [begin] raises it and cancels any pending
 * fall, [end] lets it fall only once nothing is in flight and the grace has then passed. So a run of
 * tiles that follow one another — however briefly the walk is idle between them — reads as one steady
 * window, and the dot clears just after the walk's last tile.
 *
 * **Only the warm-up feeds it.** The map reports nothing per lookup, so a route's own store reads can
 * never light the dot: the window opens at a tile the walk really builds and stays down while the walk
 * waits at its gate — an armed route, a competing heavy job, or battery-saver — because no [begin]
 * arrives to cancel the pending fall.
 *
 * It is **pure Kotlin** — a [StateFlow] and a coroutine timer, no Android type — and carries no policy
 * about what the work is. The fall timer rides an injected [scope], so a test drives the clock and the
 * production host owns the one process-lifetime dispatcher.
 *
 * **Each window edge emits one line through [trace]** — `busy on` when the window rises, `busy off` when
 * it falls — so a device pass reads the fine layer's building window instead of eyeballing the dot. Inert
 * by default; the app edge points it at the route tag.
 */
internal class FineTileActivity(
    private val graceMs: Long,
    private val scope: CoroutineScope,
    /** Where the window's two edges go — `busy on` / `busy off`. Inert by default. */
    private val trace: (String) -> Unit = {}
) {

    private val _busy = MutableStateFlow(false)

    /** Whether the warm-up's building is running, or ended within the last [graceMs]. */
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** Guards the in-flight count and the pending fall together, so a race stays whole. */
    private val lock = Any()

    /** The tiles the warm-up is building right now. */
    private var inFlight = 0

    /** The scheduled fall, or `null` while a tile is in flight or the window is already down. */
    private var fallJob: Job? = null

    /** A tile the walk began building: raise the window and cancel any fall the last tile scheduled. */
    fun begin() {
        val raised: Boolean
        synchronized(lock) {
            inFlight++
            fallJob?.cancel()
            fallJob = null
            raised = !_busy.value
            _busy.value = true
        }
        // One line per edge: a begin inside an already-open window stays silent.
        if (raised) trace(BUSY_ON)
    }

    /**
     * A tile the walk finished building. The window falls [graceMs] after the **last** one: while a tile
     * is still in flight the fall is not scheduled, and a later [begin] cancels a fall already scheduled.
     */
    fun end() {
        synchronized(lock) {
            if (inFlight > 0) inFlight--
            if (inFlight > 0) return
            fallJob?.cancel()
            fallJob = scope.launch {
                delay(graceMs)
                val fell: Boolean
                synchronized(lock) {
                    // Only the last tile's fall clears the window; a begin since leaves it up.
                    fell = inFlight == 0 && _busy.value
                    if (fell) _busy.value = false
                }
                if (fell) trace(BUSY_OFF)
            }
        }
    }

    companion object {
        /** The window's two edge lines, one per edge — greppable as a pair on the device log. */
        const val BUSY_ON = "busy on"
        const val BUSY_OFF = "busy off"
    }
}
