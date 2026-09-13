package ykws.android.maro.data.power

/**
 * Tracks **when a speed reading was last genuinely new**.
 *
 * This exists because of a subtle trap the keeper fell into: a source re-publishes its *cached*
 * speed whenever unrelated state changes, so "a push arrived" is **not** evidence that the reading
 * is fresh. Treating arrival as freshness meant a stale value could be re-stamped indefinitely and
 * hold the screen forever. Freshness therefore has to be sourced from the data — the caller says
 * whether this is a new reading — and only a new reading advances the stamp.
 *
 * Framework-free on purpose (no Android, no clock of its own) so the rule is unit-testable on the
 * JVM: the caller passes the monotonic clock in milliseconds, as [PowerPolicy] does with ages.
 */
class SpeedFreshness {

    /** Timestamp of the newest *genuine* reading, or null when none has arrived yet. */
    private var lastFreshMs: Long? = null

    /**
     * Record a push from the speed source.
     *
     * @param nowMs        monotonic clock (e.g. `SystemClock.elapsedRealtime()`).
     * @param isNewReading true only when the source knows this is a *new* reading. Re-publishing a
     *                     cached value must pass false.
     */
    fun onReading(nowMs: Long, isNewReading: Boolean) {
        if (isNewReading) lastFreshMs = nowMs
    }

    /**
     * Age (ms) of the newest genuine reading, or null when none has arrived. Callers treat null as
     * "cannot prove freshness", which the policy resolves as stale.
     */
    fun ageMs(nowMs: Long): Long? = lastFreshMs?.let { nowMs - it }
}
