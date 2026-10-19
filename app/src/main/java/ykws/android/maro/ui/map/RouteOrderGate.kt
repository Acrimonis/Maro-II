package ykws.android.maro.ui.map

/**
 * **The gate on the panel's row order** — the figures on a row update the instant they arrive, but the
 * order the rows stand in is refreshed at most once per [intervalMs], so a landing never moves a row
 * under the reader's finger. The first change applies at once; a change inside the interval waits for
 * its end; a forced one (the last landing, a fold, the arm) applies at once.
 *
 * Pure and clock-free: the caller hands in the time, asks how long to wait, and reports when it applied.
 */
internal class RouteOrderGate(private val intervalMs: Long) {

    private var lastAppliedMs: Long? = null

    /** The milliseconds to wait before a refresh may apply at [nowMs] — `0` means apply now. */
    fun waitMs(nowMs: Long, force: Boolean): Long {
        val last = lastAppliedMs
        if (force || last == null) return 0L
        return (last + intervalMs - nowMs).coerceAtLeast(0L)
    }

    /** Records that a refresh applied at [nowMs]. */
    fun applied(nowMs: Long) {
        lastAppliedMs = nowMs
    }

    /** Forgets the last refresh, so the next change applies at once. */
    fun reset() {
        lastAppliedMs = null
    }
}
