package ykws.android.maro.spatial.multipass

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * **The tile store's busy window — one debounced signal for the whole fine-layer pass.**
 *
 * The map reaches the store a **tile at a time**, so a bare in-flight count would fall back to zero in
 * the few-millisecond gap between two tiles and blink the routing square's dot tile to tile. This window
 * instead **holds** busy for [graceMs] after the last unit of work ends: [begin] raises it and cancels any
 * pending fall, [end] lets it fall only once nothing is in flight and the grace has then passed. So a pass
 * whose tiles follow one another — however briefly the store is idle between them — reads as one steady
 * window, and the dot clears just after the pass's last tile.
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

    /** Whether the store's fine-layer work is running, or ended within the last [graceMs]. */
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** Guards the in-flight count, the holds and the pending fall together, so a race stays whole. */
    private val lock = Any()

    /** The units of store work currently in flight. */
    private var inFlight = 0

    /**
     * **The open holds** — the count of callers that raised the window for a whole pass ([hold]) and not
     * yet released it ([release]). While any stands, the window stays up whatever the grace says, so the
     * pre-warm's long pass reads as one window rather than flickering across the gap between its tiles.
     */
    private var holds = 0

    /** The scheduled fall, or `null` while work is in flight or the window is already down. */
    private var fallJob: Job? = null

    /** A unit of store work began: raise the window and cancel any fall the last one scheduled. */
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
     * A unit of store work ended. The window falls [graceMs] after the **last** one: while anything is
     * still in flight the fall is not scheduled, and a later [begin] cancels a fall already scheduled.
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
                    // Only the last worker's fall clears the window; a begin or a hold since leaves it up.
                    fell = inFlight == 0 && holds == 0 && _busy.value
                    if (fell) _busy.value = false
                }
                if (fell) trace(BUSY_OFF)
            }
        }
    }

    /**
     * **Raise the window for a whole pass and hold it up past the grace.** The pre-warm's own edge: it
     * reaches the store one tile at a time like an arm, but its pass is long enough that the grace would
     * let the dot fall between distant tiles. A hold keeps the window standing whatever the in-flight
     * count and the grace do, so one `busy on` opens at the pass's first work and one `busy off` closes
     * it when the pass ends — the window *is* the pass. [release] undoes it, falling only once nothing
     * else holds and nothing is in flight.
     */
    fun hold() {
        val raised: Boolean
        synchronized(lock) {
            holds++
            fallJob?.cancel()
            fallJob = null
            raised = !_busy.value
            _busy.value = true
        }
        // One line per edge: a hold inside an already-open window stays silent.
        if (raised) trace(BUSY_ON)
    }

    /**
     * **Release one hold.** The window falls only once no hold stands and no work is in flight — a
     * still-running tile keeps it up, and a second hold keeps it up too. The pass's last release is the
     * `busy off` edge the grace-scheduled end could never give it.
     */
    fun release() {
        val fell: Boolean
        synchronized(lock) {
            if (holds > 0) holds--
            if (holds > 0 || inFlight > 0) return
            fallJob?.cancel()
            fallJob = null
            fell = _busy.value
            if (fell) _busy.value = false
        }
        if (fell) trace(BUSY_OFF)
    }

    /** Drop everything — no work in flight, no holds and no pending fall. The process host's own reset. */
    fun reset() {
        synchronized(lock) {
            inFlight = 0
            holds = 0
            fallJob?.cancel()
            fallJob = null
            _busy.value = false
        }
    }

    companion object {
        /** The window's two edge lines, one per edge — greppable as a pair on the device log. */
        const val BUSY_ON = "busy on"
        const val BUSY_OFF = "busy off"
    }
}
