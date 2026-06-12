package ykws.android.maro.data.regulation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import ykws.android.maro.data.model.BoundingBox
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.spatial.SpatialOperations
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Client for the **IGN API Carto Nature** module — a REST API serving French
 * natural heritage data (Natura 2000, nature reserves, national parks) as
 * GeoJSON FeatureCollections.
 *
 * This is an alternative to the INPN WFS endpoint which is WAF-restricted
 * from some networks. The IGN API Carto is hosted on a separate infrastructure
 * and bypasses those restrictions.
 *
 * Endpoint: https://apicarto.ign.fr/api/nature/{endpoint}?geom={GeoJSON polygon}
 *
 * Relevant endpoints for marine areas:
 * - `natura-habitat` — Natura 2000 sites (SIC, habitat directive)
 * - `natura-oiseaux` — Natura 2000 sites (ZPS, birds directive)
 *
 * Best-effort: returns empty list on error (never throws).
 *
 * @property httpClient OkHttpClient used for all HTTP calls.
 * @property baseUrl    API Carto base URL.
 */
class IgnCartoNatureClient(
    private val httpClient: OkHttpClient = defaultClient(),
    private val baseUrl: String = API_BASE_URL
) {
    private val json = Json { ignoreUnknownKeys = true }

    // ── Public API ──────────────────────────────────────────────────────────

    /**
     * Fetch all nature zones intersecting [bbox] across all candidate endpoints.
     */
    suspend fun fetchZones(
        bbox: BoundingBox,
        onProgress: (Int) -> Unit = {}
    ): List<RegulatedZone> = withContext(Dispatchers.IO) {
        val total = CANDIDATE_ENDPOINTS.size
        if (total == 0) return@withContext emptyList()

        val zones = mutableListOf<RegulatedZone>()
        CANDIDATE_ENDPOINTS.forEachIndexed { index, endpoint ->
            val url = buildQueryUrl(endpoint, bbox)
            val body = runCatching { httpGet(url) }.getOrNull()
            if (body != null) {
                val parsed = runCatching { parseFeatureCollection(body, endpoint) }.getOrNull()
                if (parsed != null) {
                    println("[INFO] IGN Nature $endpoint: ${parsed.size} features in bbox")
                    zones.addAll(parsed)
                }
            }
            val pct = ((index + 1) * 100 / total).coerceIn(0, 100)
            onProgress(pct)
        }
        zones
    }

    // ── Internal Helpers ────────────────────────────────────────────────────

    private fun httpGet(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "MaroII-Regulation-Fetcher/1.0")
            .header("Accept", "application/json, text/plain, */*")
            .build()
        return runCatching {
            httpClient.newCall(request).execute().use { resp ->
                when {
                    !resp.isSuccessful -> {
                        val body = resp.body?.string()?.take(200) ?: "(no body)"
                        println("[WARN] IGN Nature HTTP ${resp.code} for ${resp.request.url}: $body")
                        null
                    }
                    else -> resp.body?.string()
                }
            }
        }.getOrNull()
    }

    /**
     * Build a GET URL with the bbox as a URL-encoded GeoJSON polygon.
     *
     * The API Carto Nature expects:
     *   ?geom={"type":"Polygon","coordinates":[[[lon,lat],...]]}
     *
     * with the geometry expressed in WGS84 (EPSG:4326) decimal degrees.
     */
    private fun buildQueryUrl(endpoint: String, bbox: BoundingBox): String {
        val polygon = buildBboxPolygon(bbox)
        val encoded = java.net.URLEncoder.encode(polygon, "UTF-8")
        return "$baseUrl/$endpoint?geom=$encoded"
    }

    /**
     * Build a GeoJSON Polygon string for the bounding box.
     * Order: [lon, lat] — standard GeoJSON convention.
     */
    private fun buildBboxPolygon(bbox: BoundingBox): String {
        val (w, s, e, n) = listOf(bbox.lonWest, bbox.latSouth, bbox.lonEast, bbox.latNorth)
        // Closed ring: SW → SE → NE → NW → SW
        return """{"type":"Polygon","coordinates":[[[$w,$s],[$e,$s],[$e,$n],[$w,$n],[$w,$s]]]}"""
    }

    /**
     * Parse a GeoJSON FeatureCollection string into a [List] of [RegulatedZone].
     * Coordinates are in EPSG:4326 (WGS84).
     */
    private fun parseFeatureCollection(body: String, endpoint: String): List<RegulatedZone> {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: return emptyList()
        if (root["type"]?.jsonPrimitive?.content != "FeatureCollection") return emptyList()

        val features = root["features"]?.jsonArray ?: return emptyList()
        return features.mapNotNull { feature ->
            parseFeature(feature, endpoint)
        }
    }

    /**
     * Parse a single GeoJSON Feature from IGN API Carto Nature into a [RegulatedZone].
     *
     * Common properties (varies by endpoint):
     *   sitecode — Natura 2000 site code (e.g. "FR9301573")
     *   nom      — site name
     *   id_mnhn  — MNHN identifier (for reserves/parks)
     */
    private fun parseFeature(feature: JsonElement, endpoint: String): RegulatedZone? {
        val obj = feature.jsonObject ?: return null
        val geometry = obj["geometry"]?.jsonObject ?: return null
        val properties = obj["properties"]?.jsonObject ?: return null

        val geomType = geometry["type"]?.jsonPrimitive?.content ?: return null
        val coords = geometry["coordinates"] ?: return null

        // ── Extract properties ──────────────────────────────────────────────
        val siteCode = properties["sitecode"]?.jsonPrimitive?.content ?: ""
        val name = properties["nom"]?.jsonPrimitive?.content ?: ""
        val mnhnId = properties["id_mnhn"]?.jsonPrimitive?.content

        val description = buildDescription(endpoint, siteCode, name)

        // ── Map to zone type ────────────────────────────────────────────────
        val zoneType = mapEndpointToZoneType(endpoint)

        // ── Build classification ────────────────────────────────────────────
        val classification = RegulationClassification.InpnMpa(
            type = endpoint,
            mnhnId = mnhnId ?: siteCode
        )

        // ── Parse geometry ──────────────────────────────────────────────────
        return when (geomType) {
            "Polygon" -> {
                val polygon = parsePolygon(coords) ?: return null
                RegulatedZone(
                    outerRing = polygon.first,
                    holes = polygon.second,
                    zoneType = zoneType,
                    name = name,
                    source = "IGN",
                    sourceRef = siteCode,
                    description = description,
                    classification = classification,
                    speedSource = SpeedSource.NONE
                )
            }
            "MultiPolygon" -> {
                val polygons = coords.jsonArray ?: return null
                val firstPoly = polygons.firstOrNull() ?: return null
                val polygon = parsePolygon(firstPoly) ?: return null
                RegulatedZone(
                    outerRing = polygon.first,
                    holes = polygon.second,
                    zoneType = zoneType,
                    name = name,
                    source = "IGN",
                    sourceRef = siteCode,
                    description = description,
                    classification = classification,
                    speedSource = SpeedSource.NONE
                )
            }
            else -> null
        }
    }

    /**
     * Map IGN API Carto Nature endpoint to [RegulatedZoneType].
     */
    private fun mapEndpointToZoneType(endpoint: String): RegulatedZoneType = when (endpoint) {
        "natura-habitat", "natura-oiseaux" -> RegulatedZoneType.ENVIRONMENTAL
        "rnn", "rncf" -> RegulatedZoneType.NAVIGATION_RESTRICTION
        "pn" -> RegulatedZoneType.NAVIGATION_RESTRICTION
        "pnr" -> RegulatedZoneType.ENVIRONMENTAL
        else -> RegulatedZoneType.ENVIRONMENTAL
    }

    private fun buildDescription(endpoint: String, siteCode: String, name: String): String {
        val typeName = when (endpoint) {
            "natura-habitat" -> "Natura 2000 (directive habitat)"
            "natura-oiseaux" -> "Natura 2000 (directive oiseaux)"
            "rnn" -> "Réserve Naturelle Nationale"
            "rncf" -> "Réserve Naturelle de Corse"
            "pn" -> "Parc National"
            "pnr" -> "Parc Naturel Régional"
            else -> endpoint
        }
        val code = if (siteCode.isNotBlank()) " ($siteCode)" else ""
        return "Zone $typeName$code — $name"
    }

    /**
     * Parse a GeoJSON Polygon coordinate array into (outerRing, holes).
     * Coordinates are EPSG:4326 (WGS84) — GeoJSON order is [lon, lat].
     */
    private fun parsePolygon(coords: JsonElement): Pair<List<LatLng>, List<List<LatLng>>>? {
        val rings = coords.jsonArray ?: return null
        if (rings.isEmpty()) return null

        val outerRing = parseRing(rings[0]) ?: return null
        val holes = rings.drop(1).mapNotNull { parseRing(it) }

        return Pair(outerRing, holes)
    }

    /**
     * Parse a GeoJSON ring in WGS84. GeoJSON order is [longitude, latitude].
     */
    private fun parseRing(ring: JsonElement): List<LatLng>? {
        val points = ring.jsonArray ?: return null
        val raw = points.mapNotNull { point ->
            val arr = point.jsonArray ?: return@mapNotNull null
            if (arr.size < 2) return@mapNotNull null
            val lon = arr[0].jsonPrimitive.doubleOrNull ?: return@mapNotNull null
            val lat = arr[1].jsonPrimitive.doubleOrNull ?: return@mapNotNull null
            LatLng(latitude = lat, longitude = lon)
        }.takeIf { it.size >= 3 } ?: return null

        val simplified = SpatialOperations.douglasPeucker(raw, epsilonM = 30.0)
        if (simplified.size < 3) return null
        return if (simplified.first() != simplified.last()) {
            simplified + simplified.first()
        } else {
            simplified
        }
    }

    companion object {
        /** IGN API Carto base URL. */
        const val API_BASE_URL = "https://apicarto.ign.fr/api/nature"

        /** Nature endpoints to query for marine regulated areas. */
        val CANDIDATE_ENDPOINTS = listOf(
            "natura-habitat",
            "natura-oiseaux",
        )

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
