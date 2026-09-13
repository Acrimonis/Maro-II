package ykws.android.maro.data.power

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [SpeedFreshness].
 *
 * These pin the defect found during device testing and the Debug pass: a source that re-publishes
 * its cached speed must not be able to make that value look fresh, because doing so holds the
 * screen on forever. Freshness comes from the data, never from the arrival of a call.
 */
class SpeedFreshnessTest {

    private val freshness = SpeedFreshness()

    @Test
    fun `age is null until a new reading arrives`() {
        assertNull(freshness.ageMs(1_000L))
    }

    @Test
    fun `a non-new push never makes a reading exist`() {
        freshness.onReading(nowMs = 1_000L, isNewReading = false)
        assertNull(freshness.ageMs(1_000L))
    }

    @Test
    fun `a new reading starts the clock at zero`() {
        freshness.onReading(nowMs = 1_000L, isNewReading = true)
        assertEquals(0L, freshness.ageMs(1_000L))
    }

    @Test
    fun `a non-new push does not advance the stamp — the regression that held the screen`() {
        freshness.onReading(nowMs = 1_000L, isNewReading = true)
        // The source keeps re-publishing the same cached value on unrelated state emissions.
        repeat(10) { freshness.onReading(nowMs = 2_000L + it * 1_000L, isNewReading = false) }
        // 30 s after the genuine reading, the value must be treated as stale.
        assertEquals(30_000L, freshness.ageMs(31_000L))
    }

    @Test
    fun `a later genuine reading resets the age`() {
        freshness.onReading(nowMs = 1_000L, isNewReading = true)
        freshness.onReading(nowMs = 40_000L, isNewReading = true)
        assertEquals(0L, freshness.ageMs(40_000L))
        assertEquals(5_000L, freshness.ageMs(45_000L))
    }

    @Test
    fun `age grows monotonically with the clock`() {
        freshness.onReading(nowMs = 10_000L, isNewReading = true)
        assertEquals(1_000L, freshness.ageMs(11_000L))
        assertEquals(2_000L, freshness.ageMs(12_000L))
        assertEquals(3_000L, freshness.ageMs(13_000L))
    }
}
