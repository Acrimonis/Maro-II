package ykws.android.maro.data.temperature

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** What one request to the weather source came back with. */
sealed interface FetchResult {
    /** The source answered with a temperature, in °C. */
    data class Value(val celsius: Double) : FetchResult

    /** The source answered but has no temperature for that point (the sea model over land, say). */
    data object Empty : FetchResult

    /**
     * The call went wrong: a refused request (any HTTP status outside 2xx, the rate limit included), a server
     * error, a timeout, an air answer with no temperature in it, or a reply that is not Open-Meteo's JSON at
     * all (a captive portal's page). [code] is the HTTP status, null when no answer came back; [reason] is the
     * API's own text or the network error's message. It backs off and is reported to the user.
     */
    data class Failure(val code: Int?, val reason: String?) : FetchResult
}

/** The seam the engine fetches through, so a test can answer without a network. */
fun interface TemperatureFetcher {
    suspend fun fetch(source: TemperatureSource, latitude: Double, longitude: Double): FetchResult
}

/**
 * Open-Meteo, both sources: the Météo-France AROME air model and the marine API's sea surface
 * temperature, each asked for its `current` value at one point.
 *
 * Best-effort and never throws: see [FetchResult.Failure] for what counts as one; the only quiet
 * "nothing" is [FetchResult.Empty], a sea answer that is well formed but has no temperature (over land).
 */
class OpenMeteoClient(
    private val httpClient: OkHttpClient = defaultClient(),
    private val airBaseUrl: String = AIR_BASE_URL,
    private val waterBaseUrl: String = WATER_BASE_URL
) : TemperatureFetcher {

    override suspend fun fetch(source: TemperatureSource, latitude: Double, longitude: Double): FetchResult =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(urlFor(source, latitude, longitude)).build()
            try {
                httpClient.newCall(request).await().use { response ->
                    classify(source, response.code, response.body?.string().orEmpty())
                }
            } catch (e: IOException) {
                FetchResult.Failure(null, e.message)
            } catch (e: IllegalArgumentException) {
                FetchResult.Failure(null, e.message)
            }
        }

    /** Runs the call and gives up the connection at once when the calling coroutine is cancelled. */
    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response) { _, value, _ -> value.close() }
            }

            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }
        })
    }

    /**
     * Reads an answer: a 2xx carries a value or none, anything else is a failure with the API's reason.
     * No value is [FetchResult.Empty] only for a well-formed water answer, where the sea model has no data
     * over land. For air, which the seamless model always answers, and for a body that is not Open-Meteo's
     * JSON, it is a [FetchResult.Failure] with the 2xx code and no reason, so it backs off and is reported
     * instead of being remembered as a blank hour (air) or twelve (water).
     */
    internal fun classify(source: TemperatureSource, code: Int, body: String): FetchResult =
        if (code in 200..299) {
            parseCurrent(body, fieldFor(source))?.let { FetchResult.Value(it) }
                ?: if (source == TemperatureSource.WATER && hasCurrentSection(body)) FetchResult.Empty
                else FetchResult.Failure(code, null)
        } else {
            FetchResult.Failure(code, parseReason(body))
        }

    internal fun urlFor(source: TemperatureSource, latitude: Double, longitude: Double): String {
        val position = String.format(Locale.US, "latitude=%.5f&longitude=%.5f", latitude, longitude)
        return when (source) {
            TemperatureSource.AIR ->
                "$airBaseUrl?$position&current=temperature_2m&models=$AIR_MODEL"
            TemperatureSource.WATER ->
                "$waterBaseUrl?$position&current=sea_surface_temperature"
        }
    }

    companion object {
        const val AIR_BASE_URL = "https://api.open-meteo.com/v1/forecast"
        const val WATER_BASE_URL = "https://marine-api.open-meteo.com/v1/marine"

        /** Météo-France AROME blended with its coarser fallbacks, so a point the fine grid misses still answers. */
        const val AIR_MODEL = "meteofrance_arome_seamless"

        private val JSON = Json { ignoreUnknownKeys = true }

        fun fieldFor(source: TemperatureSource): String = when (source) {
            TemperatureSource.AIR -> "temperature_2m"
            TemperatureSource.WATER -> "sea_surface_temperature"
        }

        /**
         * The `current.<field>` number of an Open-Meteo answer, or null when the answer carries none —
         * a missing key and a JSON null alike.
         */
        fun parseCurrent(body: String, field: String): Double? {
            val root = runCatching { JSON.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return null
            val current = root["current"]?.let { runCatching { it.jsonObject }.getOrNull() } ?: return null
            val value = current[field] ?: return null
            if (value is JsonNull) return null
            return runCatching { value.jsonPrimitive.doubleOrNull }.getOrNull()
        }

        /** Whether the body is a JSON object with a `current` section: the shape of a real Open-Meteo answer. */
        fun hasCurrentSection(body: String): Boolean {
            val root = runCatching { JSON.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return false
            return root["current"] is JsonObject
        }

        /** The `reason` text of an Open-Meteo error answer (`{"error":true,"reason":"..."}`), or null. */
        fun parseReason(body: String): String? {
            val root = runCatching { JSON.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return null
            val reason = root["reason"] ?: return null
            return runCatching { reason.jsonPrimitive.content }.getOrNull()?.takeIf { it.isNotBlank() }
        }

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
