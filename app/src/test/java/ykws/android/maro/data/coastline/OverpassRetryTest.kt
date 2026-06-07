package ykws.android.maro.data.coastline

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Deterministic, offline tests for the resilient Overpass orchestration ([fetchWithRetry],
 * [raceEndpoints], [isRetryableOverpassError], [exponentialBackoffMs], [parseRetryAfterMs]).
 *
 * The HTTP call and the back-off clock are injected, so these fakes exercise the retry/recovery
 * policy without a socket or real wall-time.
 */
class OverpassRetryTest {

    private val noDelay: suspend (Long) -> Unit = { }
    private val zeroBackoff: (Int, Long?) -> Long = { _, _ -> 0L }
    private val endpoints = listOf("a", "b", "c")

    // ── raceEndpoints ────────────────────────────────────────────────────────

    @Test
    fun `race returns the only succeeding endpoint`() = runBlocking {
        val round = raceEndpoints(endpoints) { ep ->
            if (ep == "b") "ok:$ep" else throw IOException("down:$ep")
        }
        assertTrue(round is FetchRoundResult.Success)
        assertEquals("ok:b", (round as FetchRoundResult.Success).data)
    }

    @Test
    fun `race reports retryable when all endpoints fail transiently`() = runBlocking {
        val round = raceEndpoints(endpoints) { throw SocketTimeoutException("t") }
        assertTrue(round is FetchRoundResult.Failure)
        assertTrue((round as FetchRoundResult.Failure).retryable)
    }

    @Test
    fun `race reports non-retryable when all endpoints fail fatally`() = runBlocking {
        val round = raceEndpoints(endpoints) { throw OverpassHttpException(400, null, "bad") }
        assertTrue(round is FetchRoundResult.Failure)
        assertFalse((round as FetchRoundResult.Failure).retryable)
    }

    @Test
    fun `race on an empty endpoint list is a non-retryable failure`() = runBlocking {
        val round = raceEndpoints<String>(emptyList()) { "never" }
        assertTrue(round is FetchRoundResult.Failure)
        assertFalse((round as FetchRoundResult.Failure).retryable)
    }

    // ── fetchWithRetry: recovery / exhaustion / short-circuit ─────────────────

    @Test
    fun `succeeds on first attempt without sleeping`() = runBlocking {
        val backoffs = mutableListOf<Int>()
        val data = fetchWithRetry(
            endpoints, maxAttempts = 3, delay = noDelay,
            backoffMs = { a, _ -> backoffs.add(a); 0L }
        ) { ep -> "ok:$ep" }
        assertTrue(data.startsWith("ok:"))
        assertTrue("no back-off when the first attempt wins", backoffs.isEmpty())
    }

    @Test
    fun `recovers on a later attempt after transient failures`() = runBlocking {
        var round = 0
        val sleeps = AtomicInteger(0)
        val delay: suspend (Long) -> Unit = { sleeps.incrementAndGet(); round++ }
        val data = fetchWithRetry(
            endpoints, maxAttempts = 5, delay = delay, backoffMs = zeroBackoff
        ) { ep ->
            if (round < 2) throw SocketTimeoutException("round=$round") else "ok:$ep@round$round"
        }
        assertEquals("recovered after exactly two retries", 2, sleeps.get())
        assertTrue(data.startsWith("ok:"))
    }

    @Test
    fun `exhausts attempts then rethrows the last transient error`() = runBlocking {
        val sleeps = AtomicInteger(0)
        val delay: suspend (Long) -> Unit = { sleeps.incrementAndGet() }
        val thrown = runCatching {
            fetchWithRetry(endpoints, maxAttempts = 3, delay = delay, backoffMs = zeroBackoff) {
                throw SocketTimeoutException("always")
            }
        }.exceptionOrNull()
        assertTrue(thrown is SocketTimeoutException)
        assertEquals("back-off between the 3 attempts = 2 sleeps", 2, sleeps.get())
    }

    @Test
    fun `stops immediately on a non-retryable failure`() = runBlocking {
        val sleeps = AtomicInteger(0)
        val calls = AtomicInteger(0)
        val delay: suspend (Long) -> Unit = { sleeps.incrementAndGet() }
        val thrown = runCatching {
            fetchWithRetry(endpoints, maxAttempts = 5, delay = delay, backoffMs = zeroBackoff) {
                calls.incrementAndGet(); throw OverpassHttpException(400, null, "bad query")
            }
        }.exceptionOrNull()
        assertTrue(thrown is OverpassHttpException)
        assertEquals("no back-off on a fatal failure", 0, sleeps.get())
        assertEquals("only a single round attempted", endpoints.size, calls.get())
    }

    @Test
    fun `honours server Retry-After for the back-off`() = runBlocking {
        var round = 0
        val seen = mutableListOf<Long?>()
        val delay: suspend (Long) -> Unit = { round++ }
        fetchWithRetry(
            endpoints, maxAttempts = 3, delay = delay,
            backoffMs = { _, retryAfter -> seen.add(retryAfter); 0L }
        ) { ep ->
            if (round == 0) throw OverpassHttpException(429, retryAfterMs = 5_000L, "rate limited")
            else "ok:$ep"
        }
        assertEquals(listOf(5_000L), seen)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects maxAttempts below one`() = runBlocking {
        fetchWithRetry(endpoints, maxAttempts = 0, delay = noDelay, backoffMs = zeroBackoff) { "x" }
        Unit
    }

    // ── classification ────────────────────────────────────────────────────────

    @Test
    fun `retry classification by error kind`() {
        assertTrue(isRetryableOverpassError(SocketTimeoutException()))
        assertTrue(isRetryableOverpassError(IOException("connection reset")))
        assertTrue(isRetryableOverpassError(IllegalStateException("truncated response")))
        assertTrue(isRetryableOverpassError(OverpassHttpException(408, null, "")))
        assertTrue(isRetryableOverpassError(OverpassHttpException(429, null, "")))
        assertTrue(isRetryableOverpassError(OverpassHttpException(503, null, "")))
        assertTrue(isRetryableOverpassError(OverpassHttpException(504, null, "")))
        assertFalse(isRetryableOverpassError(OverpassHttpException(400, null, "")))
        assertFalse(isRetryableOverpassError(OverpassHttpException(404, null, "")))
        assertFalse(isRetryableOverpassError(RuntimeException("logic bug")))
    }

    // ── back-off math ──────────────────────────────────────────────────────────

    @Test
    fun `back-off honours Retry-After capped at max`() {
        assertEquals(5_000L, exponentialBackoffMs(0, 2_000L, 30_000L, retryAfterMs = 5_000L, randomFraction = 0.0))
        assertEquals(30_000L, exponentialBackoffMs(0, 2_000L, 30_000L, retryAfterMs = 120_000L, randomFraction = 1.0))
    }

    @Test
    fun `back-off grows exponentially and caps`() {
        val base = 2_000L
        val max = 30_000L
        // randomFraction = 1.0 → full ceiling = min(base * 2^attempt, max)
        assertEquals(2_000L, exponentialBackoffMs(0, base, max, null, 1.0))
        assertEquals(4_000L, exponentialBackoffMs(1, base, max, null, 1.0))
        assertEquals(8_000L, exponentialBackoffMs(2, base, max, null, 1.0))
        assertEquals(16_000L, exponentialBackoffMs(3, base, max, null, 1.0))
        assertEquals(30_000L, exponentialBackoffMs(4, base, max, null, 1.0))  // 32k capped to 30k
        assertEquals(30_000L, exponentialBackoffMs(40, base, max, null, 1.0)) // overflow-safe
    }

    @Test
    fun `back-off floors at half base to avoid a zero wait`() {
        // randomFraction = 0.0 would give 0; floored to base / 2
        assertEquals(1_000L, exponentialBackoffMs(3, 2_000L, 30_000L, null, 0.0))
    }

    // ── Retry-After parsing ─────────────────────────────────────────────────────

    @Test
    fun `parse Retry-After delta-seconds`() {
        assertEquals(7_000L, parseRetryAfterMs("7"))
        assertEquals(0L, parseRetryAfterMs("0"))
        assertNull(parseRetryAfterMs(null))
        assertNull(parseRetryAfterMs(""))
        assertNull(parseRetryAfterMs("Wed, 21 Oct 2015 07:28:00 GMT")) // HTTP-date form not honoured
        assertNull(parseRetryAfterMs("-3"))
    }
}
