package ykws.android.maro.data.temperature

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AverageSeaFetcherTest {

    private val failure = FetchResult.Failure(503, null)
    private val tolerance = 3.0

    private fun celsiusOf(result: FetchResult): Double = (result as FetchResult.Value).celsius

    private fun values(vararg c: Double) = c.map { FetchResult.Value(it) }

    @Test
    fun `values that agree blend close to their plain mean`() {
        // The three readings off Nice on 2026-11-27.
        val result = AverageSeaFetcher.blend(values(17.8, 17.2, 17.66), tolerance)
        assertEquals(17.556, celsiusOf(result), 0.001)
        assertEquals(listOf(17.8, 17.2, 17.66), (result as FetchResult.Value).sources)
    }

    @Test
    fun `an outlier still counts, at a low weight`() {
        // Median 17.0; 13.0 is 4 °C off and weighs 0.36, 17.5 weighs 0.97: the worked figure at 3 °C.
        assertEquals(16.591, celsiusOf(AverageSeaFetcher.blend(values(17.5, 13.0, 17.0), tolerance)), 0.001)
        // A tolerance away from the median weighs half: (0.5·14 + 17 + 0.5·20) / 2 and (0.5·14 + 17 + 17) / 2.5.
        assertEquals(17.0, celsiusOf(AverageSeaFetcher.blend(values(14.0, 17.0, 20.0), tolerance)), 1e-9)
        assertEquals(16.4, celsiusOf(AverageSeaFetcher.blend(values(14.0, 17.0, 17.0), tolerance)), 1e-9)
    }

    @Test
    fun `a failed or empty source is passed over while others answer`() {
        val result = AverageSeaFetcher.blend(listOf(FetchResult.Value(17.8), failure, FetchResult.Value(17.2)), tolerance)
        assertEquals("two values weigh alike around their mean", 17.5, celsiusOf(result), 1e-9)
        assertEquals("the failed source keeps its place", listOf(17.8, null, 17.2), (result as FetchResult.Value).sources)
        assertEquals(17.8, celsiusOf(AverageSeaFetcher.blend(listOf(FetchResult.Value(17.8), FetchResult.Empty), tolerance)), 1e-9)
    }

    @Test
    fun `no value is a failure when one failed, else empty`() {
        assertEquals(failure, AverageSeaFetcher.blend(listOf(FetchResult.Empty, failure, FetchResult.Empty), tolerance))
        assertEquals(FetchResult.Empty, AverageSeaFetcher.blend(listOf(FetchResult.Empty, FetchResult.Empty), tolerance))
    }

    @Test
    fun `water asks every sea at the point, air only the air source`() = runTest {
        val asked = mutableListOf<String>()
        val fetcher = AverageSeaFetcher(
            air = { _, _, _ -> asked += "air"; FetchResult.Value(12.0) },
            seas = listOf(
                { lat, lon -> asked += "a $lat $lon"; FetchResult.Value(18.0) },
                { lat, lon -> asked += "b $lat $lon"; FetchResult.Value(16.0) },
                { lat, lon -> asked += "c $lat $lon"; FetchResult.Value(17.0) }
            ),
            toleranceC = tolerance
        )

        assertEquals("symmetric around the median", 17.0, celsiusOf(fetcher.fetch(TemperatureSource.WATER, 43.6, 7.3)), 1e-9)
        assertEquals(listOf("a 43.6 7.3", "b 43.6 7.3", "c 43.6 7.3"), asked.sorted())

        asked.clear()
        assertEquals(FetchResult.Value(12.0), fetcher.fetch(TemperatureSource.AIR, 43.6, 7.3))
        assertEquals(listOf("air"), asked)
    }
}
