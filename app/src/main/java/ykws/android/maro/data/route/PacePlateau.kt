package ykws.android.maro.data.route

import ykws.android.maro.config.AppConfig

/**
 * The pace `Now` plans at: the **average of the middle half** of the boat's recent speeds.
 *
 * The pace looks back over a window ([windowMs]) and averages the speeds in it once the slowest quarter and
 * the fastest quarter of the window's time are left out. A speed that fills under a quarter of the window
 * has no effect, one that fills three quarters or more *is* the pace, and in between the pace edges toward
 * it in proportion — the same going up and going down. There is no percentile, no recency ramp and no
 * tolerance band.
 *
 * **Time the window has no reading for** — a stop, a zone, a lost fix, the start of a trip — counts as the
 * pace the app already showed when that time began (or, once this trip's readings fill a quarter of the
 * window, as their own middle-half average — see [gapPace]), so a stop never moves the pace and a boat
 * resuming at a new speed is followed in the same minutes as one that never stopped. Before the first
 * reading the window is all [startKn], the starting pace the caller chose.
 *
 * A reading is **ignored, never counted as a slow moment**, when it is inside a speed zone or the 300 m band
 * (`restricted`) or is not cruising ([RoutePace.isCruising]). The feed delivers a reading when the speed value
 * changes, not on a clock, so a reading covers the time up to the next one, capped at [READING_CAP_MS]; a
 * longer silence is a gap.
 *
 * **One thread.** The class is not synchronised: its owner confines it to a single thread (the view model
 * reads and feeds it on the main thread only).
 *
 * Pure Kotlin, no Android dependency, so the reduction is a unit test rather than a device run.
 */
class PacePlateau(
    windowMs: Long,
    private val stopKn: Double,
    startKn: Double
) {

    /** One stretch of the timeline: a reading, or the gap that followed one, at the speed it counts for. */
    private data class Span(val fromMs: Long, val toMs: Long, val kn: Double, val reading: Boolean)

    /** One read of the window: the pace, and how much of the window this trip's readings fill (ms). */
    data class Reading(val kn: Double, val readingMs: Long)

    /** How far back the pace looks (ms) — movable at any time, the retained timeline outlasting the widest window. */
    var windowMs: Long = windowMs
        set(value) {
            if (field != value) {
                field = value
                version++
            }
        }

    /** The pace the window is made of before any reading, and under any time before the first one. */
    var startKn: Double = startKn
        set(value) {
            if (field != value) {
                field = value
                version++
            }
        }

    private val spans = ArrayDeque<Span>()

    /** The instant the committed spans reach, null until the first reading is committed. */
    private var cursorMs: Long? = null

    private var pendingAtMs = 0L
    private var pendingKn: Double? = null

    /** Bumped by every change of the timeline, the window or the starting pace — the open gap's cache key. */
    private var version = 0L

    /**
     * The open gap's own pace, the one band mean that does not move while the timeline stands still: kept
     * for the [version] and the instant it was stamped at, so a read between two readings sorts the window
     * once rather than twice.
     */
    private var openGapVersion = -1L
    private var openGapAtMs = 0L
    private var openGapKn = 0.0

    /** Whether any eligible reading has ever fed this plateau — the starting pace is revisable until one has. */
    var hasReadings: Boolean = false
        private set

    /** One speed reading; [speedKn] null is the feed having nothing to say. */
    fun observe(speedKn: Double?, restricted: Boolean, atMs: Long) {
        // A reading never travels back in time: the timeline only moves forward.
        val at = maxOf(atMs, cursorMs ?: atMs, if (pendingKn != null) pendingAtMs else atMs)
        commitPending(at)
        if (speedKn != null && !restricted && RoutePace.isCruising(speedKn, stopKn)) {
            fillGap(at)
            pendingAtMs = at
            pendingKn = speedKn
            hasReadings = true
        }
        version++
    }

    /** The pace at [nowMs] — always inside the setting's own span — with the readings' share, from one pass. */
    fun read(nowMs: Long): Reading {
        val view = viewAt(nowMs)
        val readingMs = view.filter { it.reading }.sumOf { overlap(it, nowMs - windowMs, nowMs) }
        return Reading(clamp(bandMean(view, nowMs)), readingMs)
    }

    /** The pace at [nowMs]. */
    fun plateauKn(nowMs: Long): Double = read(nowMs).kn

    /** How much of the window ending at [nowMs] the eligible readings fill (ms) — the Live source's measure. */
    fun readingMs(nowMs: Long): Long = read(nowMs).readingMs

    private fun commitPending(atMs: Long) {
        val kn = pendingKn ?: return
        val end = minOf(atMs, pendingAtMs + READING_CAP_MS)
        pendingKn = null
        if (end <= pendingAtMs) return
        spans.addLast(Span(pendingAtMs, end, kn, reading = true))
        cursorMs = end
        prune(end)
    }

    /** Closes the stretch since the last committed span at the pace shown when it began. */
    private fun fillGap(untilMs: Long) {
        val from = cursorMs ?: return
        if (untilMs <= from) return
        spans.addLast(Span(from, untilMs, gapPace(spans, from), reading = false))
        cursorMs = untilMs
        prune(untilMs)
    }

    private fun prune(nowMs: Long) {
        val retainMs = maxOf(windowMs, RETAIN_MS)
        while (spans.isNotEmpty() && spans.first().toMs <= nowMs - retainMs) spans.removeFirst()
    }

    /** The committed spans, plus the pending reading and the gap open behind the last thing that ended. */
    private fun viewAt(nowMs: Long): List<Span> {
        val whole = ArrayList<Span>(spans.size + 1)
        whole.addAll(spans)
        val kn = pendingKn
        if (kn != null) whole.add(Span(pendingAtMs, pendingAtMs + READING_CAP_MS, kn, reading = true))
        val reach = if (kn != null) pendingAtMs + READING_CAP_MS else cursorMs

        val view = ArrayList<Span>(whole.size + 1)
        for (s in whole) if (s.fromMs < nowMs) view.add(s.copy(toMs = minOf(s.toMs, nowMs)))
        if (reach != null && nowMs > reach) view.add(Span(reach, nowMs, openGapKn(whole, reach), reading = false))
        return view
    }

    /** The pace the open gap counts at — the pace shown at [reachMs] — computed once per timeline state. */
    private fun openGapKn(whole: List<Span>, reachMs: Long): Double {
        if (openGapVersion != version || openGapAtMs != reachMs) {
            openGapKn = gapPace(whole, reachMs)
            openGapVersion = version
            openGapAtMs = reachMs
        }
        return openGapKn
    }

    /**
     * The weighted mean of the cumulative 25 %–75 % band of the window ending at [atMs], the speeds sorted
     * and weighted by the time each covers, any time no span covers counting at [startKn].
     */
    private fun bandMean(from: Iterable<Span>, atMs: Long): Double {
        val windowStart = atMs - windowMs
        val parts = ArrayList<Pair<Double, Long>>()
        var covered = 0L
        for (s in from) {
            val len = overlap(s, windowStart, atMs)
            if (len > 0) {
                parts.add(s.kn to len)
                covered += len
            }
        }
        if (covered < windowMs) parts.add(startKn to windowMs - covered)
        return middleHalfMean(parts, windowMs)
    }

    /**
     * The pace a gap counts at, from the instant [atMs] it begins. Once this trip's readings fill a quarter of
     * the window — the Live threshold — it is the **readings' own** middle-half average, so a stop right after
     * the cruise settled holds the cruise and not a mid-transition value the boat never ran; before that it is
     * the pace shown, so the starting pace still carries a trip's first minutes.
     */
    private fun gapPace(from: Iterable<Span>, atMs: Long): Double {
        val windowStart = atMs - windowMs
        val readings = ArrayList<Pair<Double, Long>>()
        var total = 0L
        for (s in from) {
            if (!s.reading) continue
            val len = overlap(s, windowStart, atMs)
            if (len > 0) {
                readings.add(s.kn to len)
                total += len
            }
        }
        return if (RoutePace.isLive(total, windowMs)) middleHalfMean(readings, total) else bandMean(from, atMs)
    }

    /** The weighted mean of the middle half of [parts] (speed, time), whose times add up to [totalMs]. */
    private fun middleHalfMean(parts: MutableList<Pair<Double, Long>>, totalMs: Long): Double {
        parts.sortBy { it.first }
        val lo = totalMs / 4.0
        val hi = totalMs * 3.0 / 4.0
        var cumulative = 0.0
        var weighted = 0.0
        for ((kn, len) in parts) {
            val take = minOf(cumulative + len, hi) - maxOf(cumulative, lo)
            if (take > 0) weighted += kn * take
            cumulative += len
        }
        return weighted / (hi - lo)
    }

    private fun overlap(s: Span, fromMs: Long, toMs: Long): Long =
        (minOf(s.toMs, toMs) - maxOf(s.fromMs, fromMs)).coerceAtLeast(0L)

    /** A pace outside the setting's span is a bad sample, not a new range. */
    private fun clamp(kn: Double): Double = kn.coerceIn(
        AppConfig.ROUTE_FREE_WATER_PACE_MIN_KN.toDouble(),
        AppConfig.ROUTE_FREE_WATER_PACE_MAX_KN.toDouble()
    )

    companion object {

        /** The longest a single reading speaks for (ms) before the silence after it counts as a gap. */
        const val READING_CAP_MS: Long = 10_000L

        /** How much timeline is kept: the widest window the Settings slider offers, so a wider window finds its past. */
        private val RETAIN_MS: Long = AppConfig.ROUTE_PACE_WINDOW_MIN_MAX * 60_000L
    }
}
