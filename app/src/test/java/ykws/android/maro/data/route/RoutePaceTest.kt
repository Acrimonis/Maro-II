package ykws.android.maro.data.route

import org.junit.Assert.assertEquals
import org.junit.Test

class RoutePaceTest {

    private val now = 1_000_000L

    private fun samples(vararg pairs: Pair<Double, Long>) =
        pairs.map { RoutePace.Sample(it.first, it.second) }

    @Test
    fun allEqualReturnsThatSpeed() {
        val result = RoutePace.paceKn(
            samples(22.0 to now - 4, 22.0 to now - 3, 22.0 to now - 2, 22.0 to now - 1, 22.0 to now),
            now, 25.0, recentWeightPct = 60.0
        )
        assertEquals(22.0, result, 1e-9)
    }

    @Test
    fun zeroWeightIsThePlainQuantile() {
        // Sorted 10,20,25,30,35 → nearest-rank 75th percentile = 30.
        val result = RoutePace.paceKn(
            samples(
                10.0 to now - 5, 20.0 to now - 4, 25.0 to now - 3,
                30.0 to now - 2, 35.0 to now - 1
            ),
            now, 25.0, recentWeightPct = 0.0
        )
        assertEquals(30.0, result, 1e-9)
    }

    @Test
    fun restrictedSamplesAreDropped() {
        val result = RoutePace.paceKn(
            listOf(
                RoutePace.Sample(4.0, now - 5, restricted = true),
                RoutePace.Sample(30.0, now - 4),
                RoutePace.Sample(30.0, now - 3),
                RoutePace.Sample(30.0, now - 2),
                RoutePace.Sample(30.0, now - 1),
                RoutePace.Sample(30.0, now)
            ),
            now, 25.0
        )
        assertEquals(30.0, result, 1e-9)
    }

    @Test
    fun belowMinSamplesFallsBackToSetPace() {
        val result = RoutePace.paceKn(
            samples(22.0 to now - 1, 22.0 to now),
            now, 25.0, recentWeightPct = 60.0
        )
        assertEquals(25.0, result, 1e-9)
    }

    @Test
    fun resultIsClampedToTheSettingSpan() {
        val tooFast = RoutePace.paceKn(
            samples(99.0 to now - 4, 99.0 to now - 3, 99.0 to now - 2, 99.0 to now - 1, 99.0 to now),
            now, 25.0
        )
        assertEquals(35.0, tooFast, 1e-9)
    }
}
