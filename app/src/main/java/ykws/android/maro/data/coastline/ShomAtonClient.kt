package ykws.android.maro.data.coastline

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import ykws.android.maro.data.model.HazardType
import ykws.android.maro.data.model.PointHazard
import java.util.concurrent.TimeUnit

/**
 * Fetches isolated offshore point hazards from the **Shom WFS** (Aids to
 * Navigation / `danger_isolé` / balisage layers) and maps them to
 * [PointHazard]s for merging into the land mass.
 *
 * ## Why this exists
 *
 * The coastline pipeline ([CoastlineGenerator]) only ingests continuous
 * `natural=coastline` polygons. Standalone features like the Phare de la
 * Fourmigue are stored by the Shom as *point* features, so they never reach the
 * spatial engine. This client closes that gap.
 *
 * ## Robustness contract
 *
 * Hazard ingestion is **best-effort enrichment**: any failure (network, HTTP,
 * parse, or an unexpected schema) returns an empty list so the coastline still
 * loads. The caller folds the result into the island set; an empty result simply
 * means "coastline-only", matching the previous behaviour.
 *
 * ## ⚠️ Endpoint / layer confirmation
 *
 * Shom publishes an INSPIRE/GeoServer WFS, but the exact `typeName` for the
 * Aids-to-Navigation / isolated-danger layer must be confirmed with a
 * `GetCapabilities` call (see the Coastline feature todo). [DEFAULT_TYPE_NAMES]
 * holds the current best candidates; parsing is schema-tolerant so a wrong guess
 * degrades to "no hazards" rather than a crash. Client-side bbox filtering makes
 * the request robust to WFS axis-order quirks.
 */
class ShomAtonClient(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val typeNames: List<String> = DEFAULT_TYPE_NAMES,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
) {
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        /**
         * Shom GeoServer WFS base. ⚠️ Confirm via GetCapabilities — kept as a
         * single configurable seam so it can be corrected without touching the
         * parsing logic.
         */
        const val DEFAULT_BASE_URL = "https://services.data.shom.fr/INSPIRE/wfs"

        /**
         * Candidate AtoN / isolated-danger layer names, tried in order until one
         * returns features. ⚠️ Placeholders pending GetCapabilities confirmation.
         */
        val DEFAULT_TYPE_NAMES = listOf(
            "BALISAGE_BDD_WFS:DANGER_ISOLE",
            "BALISAGE_BDD_WFS:AIDE_NAVIGATION"
        )
    }

    /**
     * Fetches hazards intersecting the given bbox. Always returns a (possibly
     * empty) list — never throws.
     *
     * @param latMin south, [lonMin] west, [latMax] north, [lonMax] east (WGS84).
     */
    suspend fun fetchHazards(
        latMin: Double,
        lonMin: Double,
        latMax: Double,
        lonMax: Double
    ): List<PointHazard> = withContext(Dispatchers.IO) {
        for (typeName in typeNames) {
            val hazards = runCatching {
                fetchLayer(typeName, latMin, lonMin, latMax, lonMax)
            }.getOrDefault(emptyList())
            if (hazards.isNotEmpty()) {
                // Client-side bbox filter: robust to WFS axis-order ambiguity.
                return@withContext hazards.filter {
                    it.lat in latMin..latMax && it.lon in lonMin..lonMax
                }
            }
        }
        emptyList()
    }

    /** Issues one WFS GetFeature (GeoJSON) request for a single layer. */
    private fun fetchLayer(
        typeName: String,
        latMin: Double,
        lonMin: Double,
        latMax: Double,
        lonMax: Double
    ): List<PointHazard> {
        // WFS 2.0.0 GetFeature, GeoJSON output. BBOX given in CRS84 (lon,lat)
        // order; client-side filtering corrects any server axis-order surprises.
        val url = baseUrl.toHttpUrlOrThrowLike(
            mapOf(
                "service" to "WFS",
                "version" to "2.0.0",
                "request" to "GetFeature",
                "typeNames" to typeName,
                "outputFormat" to "application/json",
                "srsName" to "EPSG:4326",
                "bbox" to "$lonMin,$latMin,$lonMax,$latMax,urn:ogc:def:crs:OGC:1.3:CRS84"
            )
        )

        val request = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", "MaroII-Coastline-Fetcher/1.0")
            .header("Accept", "application/json")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string() ?: return emptyList()
            return parseGeoJson(body)
        }
    }

    /** Parses a GeoJSON FeatureCollection of Point features into hazards. */
    private fun parseGeoJson(body: String): List<PointHazard> {
        val root = json.parseToJsonElement(body).jsonObject
        val features = root["features"]?.jsonArray ?: return emptyList()

        val hazards = mutableListOf<PointHazard>()
        for (feature in features) {
            val obj = feature.jsonObject
            val geometry = obj["geometry"]?.jsonObject ?: continue
            if (geometry["type"]?.jsonPrimitive?.contentOrNull != "Point") continue
            val coords = geometry["coordinates"]?.jsonArray ?: continue
            if (coords.size < 2) continue

            // GeoJSON is always [lon, lat].
            val lon = coords[0].jsonPrimitive.doubleOrNull ?: continue
            val lat = coords[1].jsonPrimitive.doubleOrNull ?: continue

            val props = obj["properties"]?.jsonObject
            val name = props.firstString("nom", "name", "libelle", "NOM") ?: ""
            val type = classify(props.firstString("categorie", "fonction", "type", "CATEGORIE"))

            hazards.add(PointHazard(lat = lat, lon = lon, name = name, type = type))
        }
        return hazards
    }

    /** Maps a free-text Shom category/function string to a [HazardType]. */
    private fun classify(raw: String?): HazardType {
        val s = raw?.lowercase() ?: return HazardType.ISOLATED_DANGER
        return when {
            "phare" in s || "tourelle" in s || "feu" in s -> HazardType.LIGHT
            "balise" in s || "espar" in s -> HazardType.BEACON
            "roche" in s || "sec" in s || "écueil" in s || "ecueil" in s -> HazardType.ROCK
            "danger" in s -> HazardType.ISOLATED_DANGER
            else -> HazardType.ISOLATED_DANGER
        }
    }

    /** Returns the first non-blank property among [keys], or null. */
    private fun JsonObject?.firstString(vararg keys: String): String? {
        if (this == null) return null
        for (k in keys) {
            val v = this[k]?.jsonPrimitive?.contentOrNull
            if (!v.isNullOrBlank()) return v
        }
        return null
    }

    /**
     * Minimal query-string builder — avoids a hard dependency on okhttp's
     * `HttpUrl` extension import while keeping call sites readable.
     */
    private fun String.toHttpUrlOrThrowLike(params: Map<String, String>): String {
        val query = params.entries.joinToString("&") { (k, v) ->
            "${k.urlEncode()}=${v.urlEncode()}"
        }
        return if (contains("?")) "$this&$query" else "$this?$query"
    }

    private fun String.urlEncode(): String =
        java.net.URLEncoder.encode(this, "UTF-8")
}
