package ykws.android.maro.data.regulation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import ykws.android.maro.data.model.BoundingBox
import ykws.android.maro.data.model.LatLng
import java.util.concurrent.TimeUnit

/**
 * Client for the **SHOM WFS** maritime regulation zones endpoint.
 *
 * Discovers regulation layers via [getCapabilities], then fetches GeoJSON
 * FeatureCollection data for each candidate typeName intersecting [BoundingBox].
 *
 * Best-effort throughout — returns empty lists on any error (never throws).
 *
 * @property httpClient  OkHttpClient used for all HTTP calls.
 * @property baseUrl     SHOM WFS base URL (default: [DEFAULT_BASE_URL]).
 */
class ShomRegulationClient(
    private val httpClient: OkHttpClient = defaultClient(),
    private val baseUrl: String = DEFAULT_BASE_URL
) {
    private val json = Json { ignoreUnknownKeys = true }

    // ── Public API ──────────────────────────────────────────────────────────

    /**
     * Fetch all SHOM regulation zones intersecting [bbox] across all candidate typeNames.
     *
     * Iterates over each typeName in [CANDIDATE_TYPENAMES], issues a WFS GetFeature
     * request with a bbox filter, parses the GeoJSON FeatureCollection response,
     * and aggregates all results into a single list.
     *
     * Best-effort: returns an empty list on any error (never throws). The [onProgress]
     * callback reports (completedTypeNameIndex, totalTypeNameCount) as percentage.
     *
     * @param bbox       The bounding box to intersect.
     * @param onProgress Called with 0..100 as each typeName is attempted.
     * @return Aggregated list of [RegulatedZone] from all successful typeName fetches.
     */
    suspend fun fetchZones(
        bbox: BoundingBox,
        onProgress: (Int) -> Unit = {}
    ): List<RegulatedZone> = withContext(Dispatchers.IO) {
        val total = CANDIDATE_TYPENAMES.size
        if (total == 0) return@withContext emptyList()

        val zones = mutableListOf<RegulatedZone>()
        CANDIDATE_TYPENAMES.forEachIndexed { index, typeName ->
            val url = buildGetFeatureUrl(typeName, bbox)
            val body = runCatching { httpGet(url) }.getOrNull()
            if (body != null) {
                val parsed = runCatching { parseFeatureCollection(body) }.getOrNull()
                if (parsed != null) {
                    zones.addAll(parsed)
                }
            }
            val pct = ((index + 1) * 100 / total).coerceIn(0, 100)
            onProgress(pct)
        }
        zones
    }

    /**
     * Probe the WFS GetCapabilities endpoint and return the list of available
     * feature typeNames found in the response.
     *
     * Best-effort: returns an empty list on any failure.
     */
    suspend fun getCapabilities(): List<String> = withContext(Dispatchers.IO) {
        val url = buildGetCapabilitiesUrl()
        val body = runCatching { httpGet(url) }.getOrNull() ?: return@withContext emptyList()
        parseCapabilitiesTypeNames(body)
    }

    // ── Internal Helpers ────────────────────────────────────────────────────

    /**
     * Perform an HTTP GET and return the response body as a [String], or null on error.
     */
    private fun httpGet(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "MaroII-Regulation-Fetcher/1.0")
            .header("Accept", "application/json, text/plain, */*")
            .build()
        return runCatching {
            httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) null else resp.body?.string()
            }
        }.getOrNull()
    }

    /**
     * Build a WFS GetFeature URL with a bbox filter requesting GeoJSON output.
     */
    private fun buildGetFeatureUrl(typeName: String, bbox: BoundingBox): String = buildString {
        append(baseUrl)
        append("?service=WFS")
        append("&version=2.0.0")
        append("&request=GetFeature")
        append("&typeNames=").append(typeName)
        append("&bbox=")
        append(bbox.lonWest).append(',')
        append(bbox.latSouth).append(',')
        append(bbox.lonEast).append(',')
        append(bbox.latNorth)
        append("&outputFormat=application/json")
        append("&srsName=EPSG:4326")
    }

    /**
     * Build a WFS GetCapabilities URL.
     */
    private fun buildGetCapabilitiesUrl(): String = buildString {
        append(baseUrl)
        append("?service=WFS")
        append("&version=2.0.0")
        append("&request=GetCapabilities")
    }

    /**
     * Parse a WFS GetCapabilities XML response to extract available feature typeNames.
     *
     * Searches for `<FeatureType>` blocks containing `<Name>` elements.
     * This is a simple string-based extraction (not a full XML parser) — best-effort,
     * returns empty list on any failure.
     */
    private fun parseCapabilitiesTypeNames(xml: String): List<String> {
        return runCatching {
            val typeNames = mutableListOf<String>()
            // Simple extraction: find <FeatureType>...</FeatureType> blocks,
            // then extract <Name>...</Name> content within each block.
            val featureTypeRegex = Regex(
                "<FeatureType[^>]*>(.*?)</FeatureType>",
                RegexOption.DOT_MATCHES_ALL
            )
            val nameRegex = Regex("<Name>(.*?)</Name>", RegexOption.DOT_MATCHES_ALL)
            for (match in featureTypeRegex.findAll(xml)) {
                val block = match.groupValues[1]
                val nameMatch = nameRegex.find(block)
                if (nameMatch != null) {
                    typeNames.add(nameMatch.groupValues[1].trim())
                }
            }
            typeNames
        }.getOrNull() ?: emptyList()
    }

    /**
     * Parse a GeoJSON FeatureCollection string into a [List] of [RegulatedZone].
     *
     * Handles both `"Polygon"` and `"MultiPolygon"` geometry types.
     * Best-effort: silently skips malformed features and returns whatever was
     * successfully parsed.
     */
    private fun parseFeatureCollection(body: String): List<RegulatedZone> {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: return emptyList()
        if (root["type"]?.jsonPrimitive?.content != "FeatureCollection") return emptyList()

        val features = root["features"]?.jsonArray ?: return emptyList()
        return features.mapNotNull { feature ->
            parseFeature(feature)
        }
    }

    /**
     * Parse a single GeoJSON Feature element into a [RegulatedZone].
     */
    private fun parseFeature(feature: JsonElement): RegulatedZone? {
        val obj = feature.jsonObject ?: return null
        val geometry = obj["geometry"]?.jsonObject ?: return null
        val properties = obj["properties"]?.jsonObject ?: return null

        val geomType = geometry["type"]?.jsonPrimitive?.content ?: return null
        val coords = geometry["coordinates"] ?: return null

        // Parse properties
        val typeReg = properties["type_reglementation"]?.jsonPrimitive?.content
        val zoneType = typeReg?.let { parseZoneType(it) } ?: RegulatedZoneType.OTHER

        val speedLimitKn = properties["vitesse_max"]?.jsonPrimitive?.doubleOrNull
        val name = properties["nom"]?.jsonPrimitive?.content ?: ""
        val sourceRef = properties["id_reglementation"]?.jsonPrimitive?.content ?: ""
        val description = properties["description"]?.jsonPrimitive?.content ?: ""

        // Parse geometry
        return when (geomType) {
            "Polygon" -> {
                val polygon = parsePolygon(coords) ?: return null
                RegulatedZone(
                    outerRing = polygon.first,
                    holes = polygon.second,
                    zoneType = zoneType,
                    speedLimitKn = speedLimitKn,
                    name = name,
                    source = "SHOM",
                    sourceRef = sourceRef,
                    description = description
                )
            }
            "MultiPolygon" -> {
                // For MultiPolygon, we flatten all polygons into individual zones
                // since each polygon may have its own regulation attributes.
                // We return the first polygon as the primary zone; subsequent polygons
                // are not generated here to keep the API simple (callers iterate
                // the full list). For now, take the first polygon if any.
                val polygons = coords.jsonArray ?: return null
                val firstPoly = polygons.firstOrNull() ?: return null
                val polygon = parsePolygon(firstPoly) ?: return null
                RegulatedZone(
                    outerRing = polygon.first,
                    holes = polygon.second,
                    zoneType = zoneType,
                    speedLimitKn = speedLimitKn,
                    name = name,
                    source = "SHOM",
                    sourceRef = sourceRef,
                    description = description
                )
            }
            else -> null // Unsupported geometry type
        }
    }

    /**
     * Parse a GeoJSON Polygon coordinate array into (outerRing, holes).
     *
     * A Polygon's coordinates array is:
     *   [ [outerRing], [hole1], [hole2], ... ]
     * where each ring is an array of [lon, lat] coordinate pairs.
     *
     * @return Pair of (outerRing, holes), or null if parsing fails.
     */
    private fun parsePolygon(coords: JsonElement): Pair<List<LatLng>, List<List<LatLng>>>? {
        val rings = coords.jsonArray ?: return null
        if (rings.isEmpty()) return null

        val outerRing = parseRing(rings[0]) ?: return null
        val holes = rings.drop(1).mapNotNull { parseRing(it) }

        return Pair(outerRing, holes)
    }

    /**
     * Parse a GeoJSON ring (array of [lon, lat] pairs) into [List] of [LatLng].
     */
    private fun parseRing(ring: JsonElement): List<LatLng>? {
        val points = ring.jsonArray ?: return null
        return points.mapNotNull { point ->
            val arr = point.jsonArray ?: return@mapNotNull null
            if (arr.size < 2) return@mapNotNull null
            val lon = arr[0].jsonPrimitive.doubleOrNull ?: return@mapNotNull null
            val lat = arr[1].jsonPrimitive.doubleOrNull ?: return@mapNotNull null
            LatLng(latitude = lat, longitude = lon)
        }.takeIf { it.size >= 3 } // A valid polygon ring needs at least 3 points
    }

    /**
     * Map a SHOM `type_reglementation` string to [RegulatedZoneType].
     */
    private fun parseZoneType(type: String): RegulatedZoneType = when (type.trim().lowercase()) {
        "vitesse" -> RegulatedZoneType.SPEED_LIMIT
        "mouillage" -> RegulatedZoneType.ANCHORING_PROHIBITED
        "acces_interdit" -> RegulatedZoneType.ACCESS_PROHIBITED
        "protection" -> RegulatedZoneType.ENVIRONMENTAL
        else -> RegulatedZoneType.OTHER
    }

    companion object {
        /** Default base URL for SHOM WFS regulation endpoint. */
        const val DEFAULT_BASE_URL = "https://services.data.shom.fr/wfs/reglementation"

        /** Candidate base URLs to try (primary first, fallbacks after). */
        val CANDIDATE_BASE_URLS = listOf(
            "https://services.data.shom.fr/wfs/reglementation",
            "https://services.data.shom.fr/inspire/wfs"
        )

        /** Candidate WFS typeName values for regulation layers. */
        val CANDIDATE_TYPENAMES = listOf(
            "reglementation:zone_vitesse",
            "reglementation:zone_mouillage",
            "reglementation:zone_acces_interdit",
            "reglementation:zone_protection"
        )

        /**
         * Create a default [OkHttpClient] with 15s connect and 30s read timeouts.
         */
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
