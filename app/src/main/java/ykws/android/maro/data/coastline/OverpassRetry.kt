package ykws.android.maro.data.coastline

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import java.io.IOException

/**
 * Resilient, network-agnostic orchestration for the Overpass fetch.
 *
 * Two concerns, both kept pure so they unit-test without a socket:
 *   1. [raceEndpoints] — fan a single attempt across all mirror endpoints in parallel, return the
 *      first success and cancel the losers (race-to-first-success).
 *   2. [fetchWithRetry] — wrap that race in a bounded retry loop with caller-supplied back-off, so
 *      a transient disruption (timeout, 5xx, 429, dropped connection) is recovered from instead of
 *      aborting the whole bake.
 *
 * The HTTP call and the back-off clock are injected ([fetch], [delay]), so tests drive the policy
 * deterministically with fakes and a no-op delay — no network, no real wall-time.
 */

/**
 * A non-2xx Overpass HTTP response. Carries the [code] (for retry classification) and the parsed
 * `Retry-After` delay in ms ([retryAfterMs], null when absent/unparseable). Extends [IOException]
 * so existing IO-aware call sites keep treating it as a transport error.
 */
internal class OverpassHttpException(
    val code: Int,
    val retryAfterMs: Long?,
    message: String
) : IOException(message)

/** Outcome of one [raceEndpoints] attempt. */
internal sealed interface FetchRoundResult<out T> {
    data class Success<T>(val data: T) : FetchRoundResult<T>

    /** Every endpoint failed this round. [retryable] = at least one failure is worth retrying. */
    data class Failure(val error: Throwable, val retryable: Boolean) : FetchRoundResult<Nothing>
}

/**
 * Whether [t] is a transient failure worth retrying:
 *  - HTTP 408 (request timeout), 425 (too early), 429 (rate limited) or any 5xx → yes
 *  - any other [IOException] (socket timeout, connection reset/refused, DNS) → yes
 *  - [IllegalStateException] (empty body / truncated "no ways" response under load) → yes
 *  - anything else (e.g. HTTP 400 bad query, programming errors) → no
 *
 * [OverpassHttpException] is matched before the generic [IOException] arm (it is one), so a 4xx
 * like 400 is classified by its code, not blanket-retried.
 */
internal fun isRetryableOverpassError(t: Throwable): Boolean = when (t) {
    is OverpassHttpException -> t.code == 408 || t.code == 425 || t.code == 429 || t.code in 500..599
    is IOException -> true
    is IllegalStateException -> true
    else -> false
}

/**
 * Parse an HTTP `Retry-After` header expressed as **delta-seconds** into milliseconds. Returns null
 * for a blank/absent header or the HTTP-date form (not honoured here).
 */
internal fun parseRetryAfterMs(header: String?): Long? {
    val secs = header?.trim()?.toLongOrNull() ?: return null
    return if (secs >= 0) secs * 1000L else null
}

/**
 * Full-jitter exponential back-off, in ms, for a zero-based [attempt]:
 *  - A server-sent [retryAfterMs] wins outright (capped to [maxMs]).
 *  - Otherwise the ceiling is `baseMs * 2^attempt` capped at [maxMs]; the returned delay is
 *    [randomFraction] (∈ [0,1]) of that ceiling, floored at `baseMs / 2` so it never collapses to
 *    ~0. Full jitter spreads concurrent clients instead of retrying in lock-step.
 *
 * [randomFraction] is injected so production passes a PRNG while tests pin it for exact values.
 */
internal fun exponentialBackoffMs(
    attempt: Int,
    baseMs: Long,
    maxMs: Long,
    retryAfterMs: Long?,
    randomFraction: Double
): Long {
    if (retryAfterMs != null) return retryAfterMs.coerceIn(0L, maxMs)
    // Cap the shift to avoid Long overflow on absurd attempt counts.
    val ceiling = if (attempt >= 32) maxMs else (baseMs shl attempt).coerceAtMost(maxMs)
    val jittered = (ceiling * randomFraction.coerceIn(0.0, 1.0)).toLong()
    return jittered.coerceIn(baseMs / 2, maxMs)
}

/**
 * One attempt: launch [fetch] against every endpoint in parallel and return the first success,
 * cancelling the still-in-flight losers. If every endpoint fails, returns a [FetchRoundResult.Failure]
 * tagged retryable when any single failure was [isRetryableOverpassError].
 */
internal suspend fun <T> raceEndpoints(
    endpoints: List<String>,
    fetch: suspend (String) -> T
): FetchRoundResult<T> = coroutineScope {
    if (endpoints.isEmpty()) {
        return@coroutineScope FetchRoundResult.Failure(
            IllegalStateException("No Overpass endpoints configured."), retryable = false
        )
    }

    // Each request is wrapped in runCatching so a Deferred never throws — select can inspect the
    // Result via onAwait without a surrounding try/catch.
    val deferreds = endpoints.map { endpoint ->
        async { runCatching { fetch(endpoint) } }
    }
    val remaining = deferreds.toMutableList()

    var lastError: Throwable? = null
    var retryable = false
    while (remaining.isNotEmpty()) {
        val (index, result) = select<Pair<Int, Result<T>>> {
            remaining.forEachIndexed { i, deferred ->
                deferred.onAwait { value -> i to value }
            }
        }
        if (result.isSuccess) {
            remaining.forEach { it.cancel() }            // first success wins; abort the losers
            return@coroutineScope FetchRoundResult.Success(result.getOrThrow())
        }
        result.exceptionOrNull()?.let { err ->
            lastError = err
            if (isRetryableOverpassError(err)) retryable = true
        }
        remaining.removeAt(index)
    }

    FetchRoundResult.Failure(
        lastError ?: IllegalStateException("Tous les endpoints Overpass ont échoué."),
        retryable = retryable
    )
}

/**
 * Race [endpoints] up to [maxAttempts] times, recovering from transient disruptions.
 *
 * After a retryable round-failure it waits `backoffMs(attempt, retryAfterMs)` (via the injected
 * [delay]) then races again. It stops early — without sleeping — on a non-retryable failure (e.g. a
 * 4xx bad query), and rethrows the last error once attempts are exhausted.
 *
 * @param delay     suspends for the given ms (prod: kotlinx delay; tests: no-op).
 * @param backoffMs computes the wait before the next attempt from the 0-based attempt index and the
 *                  failure's server-sent `Retry-After` (ms), if any.
 */
internal suspend fun <T> fetchWithRetry(
    endpoints: List<String>,
    maxAttempts: Int,
    delay: suspend (Long) -> Unit,
    backoffMs: (attempt: Int, retryAfterMs: Long?) -> Long,
    onProgress: (Int) -> Unit = {},
    fetch: suspend (String) -> T
): T {
    require(maxAttempts >= 1) { "maxAttempts must be >= 1, was $maxAttempts" }

    var lastError: Throwable? = null
    for (attempt in 0 until maxAttempts) {
        when (val round = raceEndpoints(endpoints, fetch)) {
            is FetchRoundResult.Success -> {
                onProgress(100)
                return round.data
            }
            is FetchRoundResult.Failure -> {
                lastError = round.error
                if (!round.retryable) break               // deterministic failure — don't waste retries
                if (attempt == maxAttempts - 1) break      // attempts exhausted
                val retryAfter = (round.error as? OverpassHttpException)?.retryAfterMs
                delay(backoffMs(attempt, retryAfter))
            }
        }
    }
    throw lastError ?: IllegalStateException("Tous les endpoints Overpass ont échoué.")
}
