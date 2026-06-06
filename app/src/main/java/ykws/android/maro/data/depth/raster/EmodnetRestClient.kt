package ykws.android.maro.data.depth.raster

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Client for the **open, no-auth** EMODnet Bathymetry REST API (validated June 2026):
 *   GET https://rest.emodnet-bathymetry.eu/depth_sample?geom=POINT(lon lat)
 *   GET https://rest.emodnet-bathymetry.eu/depth_profile?geom=LINESTRING(lon lat,...)
 *
 * Used for the validation harness cross-check and as a fallback live point sampler — no
 * raster parsing needed. EMODnet stores **elevation** (negative below sea level); this
 * client returns **depth positive-down** (= −elevation).
 *
 * Validated live (June 2026): `/depth_sample?geom=POINT(7.046 43.5105)` →
 * `{"min":-4.72,"max":-2.37,"avg":-3.56,"stdev":0.446,"smoothed":-4.50875,...}`, i.e. the
 * `avg` field carries elevation, so the Lérins passage reads ≈ 3.56 m depth.
 */
class EmodnetRestClient(
    private val httpClient: OkHttpClient = defaultClient(),
    private val baseUrl: String = BASE_URL
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Mean depth below datum at a point (positive-down), or null on no-data/error. */
    suspend fun depthSample(lat: Double, lon: Double): Double? = withContext(Dispatchers.IO) {
        val url = "$baseUrl/depth_sample?geom=POINT($lon $lat)"
        val body = get(url) ?: return@withContext null
        val obj = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: return@withContext null
        // Prefer "avg"; fall back to common alternates.
        val elev = listOf("avg", "mean", "smoothed", "value")
            .firstNotNullOfOrNull { obj[it]?.jsonPrimitive?.doubleOrNull }
            ?: return@withContext null
        -elev  // elevation (negative down) → depth (positive down)
    }

    private fun get(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "MaroII-Depth-Fetcher/1.0")
            .header("Accept", "application/json")
            .build()
        return runCatching {
            httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) null else resp.body?.string()
            }
        }.getOrNull()
    }

    companion object {
        const val BASE_URL = "https://rest.emodnet-bathymetry.eu"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
