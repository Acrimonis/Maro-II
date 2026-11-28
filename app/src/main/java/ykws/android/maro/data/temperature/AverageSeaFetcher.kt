package ykws.android.maro.data.temperature

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * The sea sources, in the order [AverageSeaFetcher] is given them: the order each blended value reports its
 * [FetchResult.Value.sources] in, and the cache and the temperature source line read them in.
 */
enum class SeaSource { MARINE, ECMWF, COPERNICUS }

/**
 * The engine's fetcher: air goes to [air]; water asks every one of [seas] at the same point at once and
 * keeps their weighted average, see [blend].
 */
class AverageSeaFetcher(
    private val air: TemperatureFetcher,
    private val seas: List<suspend (Double, Double) -> FetchResult>,
    /** `temperature.water.blend.toleranceC`: how far from the median a value weighs half. */
    private val toleranceC: Double
) : TemperatureFetcher {

    override suspend fun fetch(source: TemperatureSource, latitude: Double, longitude: Double): FetchResult =
        when (source) {
            TemperatureSource.AIR -> air.fetch(source, latitude, longitude)
            TemperatureSource.WATER ->
                blend(coroutineScope { seas.map { sea -> async { sea(latitude, longitude) } }.awaitAll() }, toleranceC)
        }

    companion object {
        /**
         * The weighted average of the values among [results], each weighing 1 / (1 + (d / [toleranceC])²) with d
         * its distance from their median: values that agree count fully, an outlier still counts, just little.
         * Each source's own value rides in [FetchResult.Value.sources]. A source that failed or had nothing
         * while another answered is passed over: the blend is of those that answered. With no value at all, the
         * first failure, so it backs off and is reported; with none failed either, [FetchResult.Empty] (no sea here).
         */
        fun blend(results: List<FetchResult>, toleranceC: Double): FetchResult {
            require(toleranceC > 0.0) { "toleranceC must be positive" }
            val each = results.map { (it as? FetchResult.Value)?.celsius }
            val values = each.filterNotNull()
            if (values.isEmpty()) return results.firstOrNull { it is FetchResult.Failure } ?: FetchResult.Empty
            val centre = median(values)
            val weights = values.map { v -> ((v - centre) / toleranceC).let { 1.0 / (1.0 + it * it) } }
            val blended = values.zip(weights).sumOf { (v, w) -> v * w } / weights.sum()
            return FetchResult.Value(blended, each)
        }

        /** The middle value of an odd count, the mean of the two middle ones of an even count. */
        private fun median(values: List<Double>): Double {
            val sorted = values.sorted()
            val mid = sorted.size / 2
            return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
        }
    }
}
