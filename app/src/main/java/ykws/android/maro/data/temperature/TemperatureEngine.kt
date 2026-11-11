package ykws.android.maro.data.temperature

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.EnumMap
import kotlin.math.roundToInt

/** One temperature as the tile shows it: [celsius] null is the dash, [subdued] the less-exact styling. */
data class TemperatureReading(val celsius: Int?, val subdued: Boolean)

/**
 * What the tile draws. [water] is null while the marker is not on water, which also means there is no
 * water view to switch to.
 */
data class TemperatureState(
    val air: TemperatureReading = TemperatureReading(null, false),
    val water: TemperatureReading? = null
)

/**
 * What went wrong with a request, for the user to read. [source] is null for "no connection"; [code] is the
 * HTTP status when the source answered, [reason] the API's or the network's own text.
 */
data class TemperatureProblem(val source: TemperatureSource?, val code: Int?, val reason: String?) {
    /** True when the source answered fine (a 2xx) but with no temperature in it. */
    val answeredWithoutValue: Boolean get() = code != null && code in 200..299
}

/** The timing knobs the engine runs on, defaulted to the documented values of `maro.properties`. */
data class TemperatureTiming(
    val settleMs: Long = 750L,
    val backoffS: List<Int> = listOf(60, 120, 300),
    val waterMaxTries: Int = 10,
    /** The first look at the water sample points is this many times [waterMaxTries] wide. */
    val waterWindowFactor: Int = 4,
    /** The look widens, while land keeps the on-water count short, up to this many times [waterMaxTries]. */
    val waterWindowMaxFactor: Int = 16,
    /** How often a stationary marker is re-checked, so an expiry refreshes with no movement. */
    val tickMs: Long = 30_000L
)

/**
 * The temperature tile's brain: for the marker position it keeps, per source, the value of the nearest
 * cache sample point, decides when a fetch is due, and publishes what the tile shows as [state].
 *
 * **Reading.** Every position change re-reads the cache for the nearest sample point (free), so the tile
 * always shows the nearest point's value; for water, when that point is on land or has no sea value, the
 * nearest on-water point that has one. A value past its expiry is [TemperatureReading.subdued]. While the
 * point has nothing yet, the previous value stays, subdued; the dash is shown only when there is nothing
 * to show.
 *
 * **Fetching.** A fetch for a source is due when the nearest point has no value or an expired one. It runs
 * once the marker has stood still for [TemperatureTiming.settleMs], and a stationary marker is re-checked on
 * a slow tick so an expiry refreshes with no movement. There is no fetch interval: a cached answer, empty
 * ones included, ends the need to ask, and a failed attempt backs off through [TemperatureTiming.backoffS];
 * nothing is fetched offline. Water is fetched only while the marker is on water, trying up to
 * [TemperatureTiming.waterMaxTries] on-water points nearest first and stopping at the first value.
 *
 * Not thread-safe by design: [scope] must run on a single thread (the main dispatcher in the app, a test
 * dispatcher in tests), so the state needs no lock.
 */
class TemperatureEngine(
    private val scope: CoroutineScope,
    private val fetcher: TemperatureFetcher,
    private val airGrid: TemperatureGrid,
    private val waterGrid: TemperatureGrid,
    private val cache: TemperatureCache,
    /** The ungated water test; "unknown" must already be folded to false by the caller. */
    private val isWater: (Double, Double) -> Boolean,
    private val isOnline: () -> Boolean,
    private val clock: () -> Long = System::currentTimeMillis,
    private val onCacheChanged: (TemperatureCache) -> Unit = {},
    /** Called when a request goes wrong, once per streak of failures of a source, and on a refresh with no connection. */
    private val onProblem: (TemperatureProblem) -> Unit = {},
    private val timing: TemperatureTiming = TemperatureTiming()
) {
    private val _state = MutableStateFlow(TemperatureState())
    val state: StateFlow<TemperatureState> = _state

    private var position: Pair<Double, Double>? = null
    private var enabled = false

    /** When a source that is backing off may be asked again; absent means it may be asked now. */
    private val retryAtMs = EnumMap<TemperatureSource, Long>(TemperatureSource::class.java)
    private val failures = EnumMap<TemperatureSource, Int>(TemperatureSource::class.java)

    /** Whether each water sample point is on water, asked once: the coastline does not move under the engine. */
    private val waterAt = HashMap<String, Boolean>()

    /** The water candidates last computed, with the position they were computed at. */
    private var candidatesAt: Pair<Pair<Double, Double>, List<SamplePoint>>? = null

    private var fetchJob: Job? = null
    private var wakeJob: Job? = null
    private var tickJob: Job? = null

    private var lastAir: Int? = null
    private var lastWater: Int? = null

    // ── Inputs ─────────────────────────────────────────────────────────────

    /** Turns the feature on or off. Off stops all fetching; the cache is kept and keeps expiring by time. */
    fun setEnabled(value: Boolean) {
        if (enabled == value) return
        enabled = value
        if (value) {
            tickJob = scope.launch {
                while (isActive) {
                    delay(timing.tickMs)
                    render()
                    // Through the settle, like a move: a marker mid-drag is not asked for at each tick.
                    scheduleCheck(timing.settleMs)
                }
            }
            render()
            scheduleCheck(0L)
        } else {
            fetchJob?.cancel()
            wakeJob?.cancel()
            tickJob?.cancel()
            render()
        }
    }

    /**
     * The water test now answers differently (the coastline has loaded or changed): forget what was learned
     * about the sample points. The next position update reads them again.
     */
    fun waterTestChanged() {
        waterAt.clear()
        candidatesAt = null
    }

    /** The marker moved: re-read the cache now, ask for a value once the marker has settled. */
    fun onPosition(latitude: Double, longitude: Double) {
        position = latitude to longitude
        if (!enabled) return
        render()
        scheduleCheck(timing.settleMs)
    }

    /**
     * "Refresh now": every held value is marked expired, every remembered empty answer is dropped and any
     * back-off is forgotten, then the nearest point of each source is fetched once. With a connection, a
     * request already in flight predates the press, so it is dropped and asked afresh at once. A failure is
     * reported again, and so is the lack of a connection (the request in flight is then left to finish).
     */
    fun refreshNow() {
        cache.invalidateAll()
        failures.clear()
        retryAtMs.clear()
        onCacheChanged(cache)
        render()
        if (enabled) {
            // Said now rather than silently waiting for a connection: the press asked for an answer.
            if (isOnline()) {
                fetchJob?.cancel() // a cancelled job is no longer active, so the check below starts the new one
            } else {
                onProblem(TemperatureProblem(null, null, null)) // the request in flight may yet succeed: keep it
            }
            check()
        }
    }

    // ── Deciding ───────────────────────────────────────────────────────────

    private fun scheduleCheck(delayMs: Long) {
        wakeJob?.cancel()
        wakeJob = scope.launch {
            delay(delayMs)
            check()
        }
    }

    private fun check() {
        if (!enabled) return
        val pos = position ?: return
        if (fetchJob?.isActive == true) return // its end checks again
        val now = clock()
        val due = mutableListOf<TemperatureSource>()
        var wakeInMs: Long? = null
        for (source in TemperatureSource.entries) {
            if (!needsFetch(source, pos, now)) continue
            val next = nextAllowedMs(source)
            if (now >= next && isOnline()) {
                due += source
            } else {
                // Blocked by the back-off: wake when it ends. Blocked offline: look again on the tick.
                val wait = if (now >= next) timing.tickMs else next - now
                wakeInMs = minOf(wakeInMs ?: wait, wait)
            }
        }
        if (due.isNotEmpty()) {
            fetchJob = scope.launch { runFetches(due, pos) }
        } else if (wakeInMs != null) {
            scheduleCheck(wakeInMs.coerceAtLeast(1L))
        }
    }

    private fun needsFetch(source: TemperatureSource, pos: Pair<Double, Double>, now: Long): Boolean =
        when (source) {
            TemperatureSource.AIR -> {
                val point = airGrid.nearest(pos.first, pos.second, 1).first()
                val entry = cache.get(point.key)
                entry == null || !cache.isFresh(TemperatureSource.AIR, entry, now)
            }
            TemperatureSource.WATER -> isWater(pos.first, pos.second) && waterNeedsAsk(pos, now)
        }

    /** Whether the first water candidate that is not a fresh empty answer is missing or expired. */
    private fun waterNeedsAsk(pos: Pair<Double, Double>, now: Long): Boolean {
        for (point in waterCandidates(pos)) {
            val entry = cache.get(point.key)
            if (entry == null) return true
            if (!cache.isFresh(TemperatureSource.WATER, entry, now)) return true
            if (entry.valueC != null) return false
        }
        return false
    }

    /** When a source may be asked again: at once, unless its last attempts failed and it is backing off. */
    private fun nextAllowedMs(source: TemperatureSource): Long = retryAtMs[source] ?: 0L

    /**
     * The on-water sample points nearest the marker, nearest first, at most [TemperatureTiming.waterMaxTries].
     * It looks at the nearest few and widens the window, up to [TemperatureTiming.waterWindowMaxFactor] times
     * the tries, while land keeps the on-water count short, so a ragged coast still yields a full list when the
     * sea has one. The answer is kept for the position it was computed at: one pan frame asks for it up to
     * three times.
     */
    private fun waterCandidates(pos: Pair<Double, Double>): List<SamplePoint> {
        candidatesAt?.let { (at, list) -> if (at == pos) return list }
        val tries = timing.waterMaxTries.coerceAtLeast(1)
        val firstWindow = tries * timing.waterWindowFactor.coerceAtLeast(1)
        val lastWindow = maxOf(firstWindow, tries * timing.waterWindowMaxFactor)
        var window = firstWindow
        while (true) {
            val onWater = waterGrid.nearest(pos.first, pos.second, window).filter(::isWaterPoint)
            if (onWater.size >= tries || window >= lastWindow) {
                return onWater.take(tries).also { candidatesAt = pos to it }
            }
            window *= 2
        }
    }

    private fun isWaterPoint(point: SamplePoint): Boolean {
        // A runaway memo is dropped rather than grown: a boat visits a few hundred points a day.
        if (waterAt.size > WATER_MEMO_LIMIT) waterAt.clear()
        return waterAt.getOrPut(point.key) { isWater(point.latitude, point.longitude) }
    }

    // ── Fetching ───────────────────────────────────────────────────────────

    private suspend fun runFetches(due: List<TemperatureSource>, pos: Pair<Double, Double>) {
        try {
            for (source in due) {
                when (source) {
                    TemperatureSource.AIR -> fetchAir(pos)
                    TemperatureSource.WATER -> fetchWater(pos)
                }
            }
        } finally {
            onCacheChanged(cache)
            render()
            // Not at once: a marker still moving gets its settle time before the next ask.
            scheduleCheck(timing.settleMs)
        }
    }

    private suspend fun fetchAir(pos: Pair<Double, Double>) {
        val point = airGrid.nearest(pos.first, pos.second, 1).first()
        record(TemperatureSource.AIR, point, fetcher.fetch(TemperatureSource.AIR, point.latitude, point.longitude))
    }

    private suspend fun fetchWater(pos: Pair<Double, Double>) {
        for (point in waterCandidates(pos)) {
            val entry = cache.get(point.key)
            if (entry != null && cache.isFresh(TemperatureSource.WATER, entry, clock())) {
                if (entry.valueC != null) break
                continue // a fresh empty answer: move on to the next point
            }
            val result = fetcher.fetch(TemperatureSource.WATER, point.latitude, point.longitude)
            record(TemperatureSource.WATER, point, result)
            if (result !is FetchResult.Empty) break // a value is the answer; a failure ends the burst
        }
    }

    private fun record(source: TemperatureSource, point: SamplePoint, result: FetchResult) {
        when (result) {
            is FetchResult.Value -> {
                cache.put(point.key, CacheEntry(result.celsius, clock()))
                failures[source] = 0
                retryAtMs.remove(source)
            }
            is FetchResult.Empty -> {
                cache.put(point.key, CacheEntry(null, clock()))
                failures[source] = 0
                retryAtMs.remove(source)
            }
            is FetchResult.Failure -> {
                // Reported once per streak: the back-off spaces the retries, and each would say the same.
                if ((failures[source] ?: 0) == 0) onProblem(TemperatureProblem(source, result.code, result.reason))
                val failed = (failures[source] ?: 0) + 1
                failures[source] = failed
                // The wait runs from the failure, not from the request, so a slow timeout does not eat into it.
                val step = timing.backoffS[(failed - 1).coerceAtMost(timing.backoffS.lastIndex)]
                retryAtMs[source] = clock() + step * 1_000L
            }
        }
        render()
    }

    // ── Publishing ─────────────────────────────────────────────────────────

    private fun render() {
        val pos = position
        if (pos == null || !enabled) {
            _state.value = TemperatureState()
            return
        }
        _state.value = TemperatureState(
            air = readAir(pos),
            water = if (isWater(pos.first, pos.second)) readWater(pos) else null
        )
    }

    private fun readAir(pos: Pair<Double, Double>): TemperatureReading {
        val point = airGrid.nearest(pos.first, pos.second, 1).first()
        val entry = cache.get(point.key)
        val value = entry?.valueC
        if (entry != null && value != null) {
            val celsius = value.roundToInt()
            lastAir = celsius
            return TemperatureReading(celsius, subdued = !cache.isFresh(TemperatureSource.AIR, entry, clock()))
        }
        return lastAir?.let { TemperatureReading(it, subdued = true) } ?: TemperatureReading(null, false)
    }

    private fun readWater(pos: Pair<Double, Double>): TemperatureReading {
        val now = clock()
        for (point in waterCandidates(pos)) {
            val entry = cache.get(point.key)
            val value = entry?.valueC
            when {
                entry == null -> return previousWater()
                !cache.isFresh(TemperatureSource.WATER, entry, now) ->
                    return if (value != null) {
                        val celsius = value.roundToInt()
                        lastWater = celsius
                        TemperatureReading(celsius, subdued = true)
                    } else previousWater()
                value == null -> continue // fresh empty answer: the next point answers
                else -> {
                    val celsius = value.roundToInt()
                    lastWater = celsius
                    return TemperatureReading(celsius, subdued = false)
                }
            }
        }
        return TemperatureReading(null, false)
    }

    private fun previousWater(): TemperatureReading =
        lastWater?.let { TemperatureReading(it, subdued = true) } ?: TemperatureReading(null, false)
}

/** A guard, not a tunable: the memo of water answers is dropped whole if it ever grows past this. */
private const val WATER_MEMO_LIMIT = 5_000
