package ykws.android.maro.data.route

import org.junit.Assert.assertEquals
import org.junit.Test
import ykws.android.maro.config.AppConfig

/**
 * The pace reduction: which samples count, what they reduce to, and when the set pace stands.
 *
 * The point of the class is that a mean and a high quantile disagree, so the central test is written
 * so the two cannot be confused — a window whose mean is 11.2 kn must come back as 8 kn.
 */
class RoutePaceTest {

    private val now = 1_700_000_000_000L

    private fun sample(speedKn: Double, ageMs: Long = 0L, restricted: Boolean = false) =
        RoutePace.Sample(speedKn, now - ageMs, restricted)

    private fun window(vararg speeds: Double) = speeds.map { sample(it) }

    @Test
    fun aWindowIsReducedByItsHighQuantileRatherThanItsMean() {
        // sorted 5,6,7,8,30: the 0.75 nearest-rank quantile is 8, the mean would be 11.2.
        val pace = RoutePace.paceKn(window(5.0, 6.0, 7.0, 8.0, 30.0), now, setPaceKn = 28.0)

        assertEquals(8.0, pace, 1e-9)
    }

    @Test
    fun samplesInsideAZoneOrTheBandAreNotEvidence() {
        // Five fast readings taken where the limit set the speed, five honest ones.
        val samples = window(9.0, 9.0, 9.0, 9.0, 9.0).map { it.copy(restricted = true) } +
            window(5.0, 6.0, 7.0, 8.0, 30.0)

        assertEquals(8.0, RoutePace.paceKn(samples, now, setPaceKn = 28.0), 1e-9)
    }

    @Test
    fun aThinWindowFallsBackToTheSetPace() {
        val pace = RoutePace.paceKn(window(9.0, 9.0), now, setPaceKn = 28.0)

        assertEquals(28.0, pace, 1e-9)
    }

    @Test
    fun aWindowWithNothingEligibleFallsBackToTheSetPace() {
        val stale = (0 until 10).map { sample(9.0, ageMs = RoutePace.WINDOW_MS + 1L) }

        assertEquals(28.0, RoutePace.paceKn(stale, now, setPaceKn = 28.0), 1e-9)
        assertEquals(
            28.0,
            RoutePace.paceKn(emptyList(), now, setPaceKn = 28.0),
            1e-9
        )
    }

    @Test
    fun readingsFromTheFutureAndNonReadingsAreIgnored() {
        val samples = window(5.0, 6.0, 7.0, 8.0, 30.0) +
            RoutePace.Sample(speedKn = 200.0, atMs = now + 1_000L) +
            RoutePace.Sample(speedKn = Double.NaN, atMs = now) +
            RoutePace.Sample(speedKn = 0.0, atMs = now)

        assertEquals(8.0, RoutePace.paceKn(samples, now, setPaceKn = 28.0), 1e-9)
    }

    @Test
    fun thePaceNeverEscapesTheSettingsOwnSpan() {
        val fast = window(60.0, 61.0, 62.0, 63.0, 64.0)
        val slow = window(0.5, 0.6, 0.7, 0.8, 0.9)

        assertEquals(
            AppConfig.ROUTE_FREE_WATER_PACE_MAX_KN.toDouble(),
            RoutePace.paceKn(fast, now, setPaceKn = 28.0),
            1e-9
        )
        assertEquals(
            AppConfig.ROUTE_FREE_WATER_PACE_MIN_KN.toDouble(),
            RoutePace.paceKn(slow, now, setPaceKn = 28.0),
            1e-9
        )
    }
}
