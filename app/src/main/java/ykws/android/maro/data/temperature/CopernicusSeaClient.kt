package ykws.android.maro.data.temperature

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import kotlin.math.floor

/**
 * Copernicus Marine's Mediterranean sea temperature: the CNR satellite analysis of product
 * `SST_MED_SST_L4_NRT_OBSERVATIONS_010_004`, its 1/100° dataset — one gap-free night-time map a day —
 * read at one point through the public WMTS `GetFeatureInfo`, which needs no account and answers in kelvin.
 *
 * Best-effort and never throws. Outside the dataset's extent no request is made and the answer is
 * [FetchResult.Empty]; over land the service answers a null value, also [FetchResult.Empty]. Anything
 * else that carries no value is a [FetchResult.Failure].
 */
class CopernicusSeaClient(
    private val httpClient: OkHttpClient = OpenMeteoClient.defaultClient(),
    private val baseUrl: String = BASE_URL
) {
    suspend fun fetch(latitude: Double, longitude: Double): FetchResult {
        if (!covers(latitude, longitude)) return FetchResult.Empty
        return httpClient.fetchClassified(urlFor(latitude, longitude), ::classify)
    }

    /**
     * The `GetFeatureInfo` of the tile and pixel holding the point, on the EPSG:4326 tile matrix
     * [TILE_MATRIX]: 2^(z+1) × 2^z tiles of 256 px from (90 N, 180 W), a pixel far finer than the data's
     * grid, so the answer is the data cell the point is in. No `TIME`: the service answers its latest map.
     */
    internal fun urlFor(latitude: Double, longitude: Double): String {
        val tileDeg = 360.0 / (2 shl TILE_MATRIX)
        val pixelDeg = tileDeg / TILE_PX
        val x = longitude + 180.0
        val y = 90.0 - latitude
        val col = floor(x / tileDeg).toInt()
        val row = floor(y / tileDeg).toInt()
        val i = floor((x - col * tileDeg) / pixelDeg).toInt().coerceIn(0, TILE_PX - 1)
        val j = floor((y - row * tileDeg) / pixelDeg).toInt().coerceIn(0, TILE_PX - 1)
        return "$baseUrl?SERVICE=WMTS&VERSION=1.0.0&REQUEST=GetFeatureInfo&LAYER=$LAYER&STYLE=" +
            "&TILEMATRIXSET=EPSG:4326&TILEMATRIX=$TILE_MATRIX&TILEROW=$row&TILECOL=$col&I=$i&J=$j" +
            "&INFOFORMAT=application/json"
    }

    /**
     * A 2xx with a feature carries a value or a JSON null (land); a 2xx of any other shape is a failure with
     * no reason, and any other status a failure with the service's exception text.
     */
    internal fun classify(code: Int, body: String): FetchResult {
        if (code !in 200..299) return FetchResult.Failure(code, parseException(body))
        val value = parseValue(body) ?: return FetchResult.Failure(code, null)
        return if (value is JsonNull) FetchResult.Empty
        else runCatching { value.jsonPrimitive.doubleOrNull }.getOrNull()
            ?.let { FetchResult.Value(it - KELVIN_OFFSET) }
            ?: FetchResult.Failure(code, null)
    }

    companion object {
        const val BASE_URL = "https://wmts.marine.copernicus.eu/teroWmts"
        const val LAYER = "SST_MED_SST_L4_NRT_OBSERVATIONS_010_004/" +
            "SST_MED_SST_L4_NRT_OBSERVATIONS_010_004_c_V2_202311/analysed_sst"

        /** The zoom asked at: its pixel (~0.0007°) sits well inside one data cell (~0.0083°). */
        const val TILE_MATRIX = 10
        private const val TILE_PX = 256
        private const val KELVIN_OFFSET = 273.15

        // The dataset's extent, from its Copernicus Marine STAC entry.
        private const val MIN_LAT = 30.25
        private const val MAX_LAT = 46.0
        private const val MIN_LON = -18.12
        private const val MAX_LON = 36.25

        private val JSON = Json { ignoreUnknownKeys = true }
        private val EXCEPTION_TEXT = Regex("<Exception[^>]*>([^<]+)</Exception>")

        fun covers(latitude: Double, longitude: Double): Boolean =
            latitude in MIN_LAT..MAX_LAT && longitude in MIN_LON..MAX_LON

        /** `features[0].properties.value` as found — a number or a JSON null — or null when it is missing. */
        private fun parseValue(body: String) = runCatching {
            val root = JSON.parseToJsonElement(body) as? JsonObject
            val feature = (root?.get("features") as? JsonArray)?.firstOrNull() as? JsonObject
            (feature?.get("properties") as? JsonObject)?.get("value")
        }.getOrNull()

        private fun parseException(body: String): String? =
            EXCEPTION_TEXT.find(body)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
    }
}
