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

/**
 * Client for the **INPN WFS** (Inventaire National du Patrimoine Naturel)
 * Marine Protected Areas and Natura 2000 sites.
 *
 * Fetches MPA polygon geometries and metadata from the INPN INSPIRE WFS endpoint.
 * Coordinates are expected in EPSG:4326 (WGS84) — no CRS conversion needed.
 *
 * Best-effort: returns empty list on error (never throws).
 *
 * @property httpClient OkHttpClient used for all HTTP calls.
 * @property baseUrl    INPN WFS base URL.
 */
class InpnRegulationClient(
    private val httpClient: OkHttpClient = defaultClient(),
    private val baseUrl: String = INPN_BASE_URL
) {
    private val json = Json { ignoreUnknownKeys = true }

    // ── Public API ──────────────────────────────────────────────────────────

    /**
     * Fetch all INPN regulation zones intersecting [bbox] across all candidate layers.
     */
    suspend fun fetchZones(
        bbox: BoundingBox,
        onProgress: (Int) -> Unit = {}
    ): List<RegulatedZone> = withContext(Dispatchers.IO) {
        val total = CANDIDATE_LAYERS.size
        if (total == 0) return@withContext emptyList()

        val zones = mutableListOf<RegulatedZone>()
        CANDIDATE_LAYERS.forEachIndexed { index, layer ->
            val url = buildGetFeatureUrl(layer, bbox)
            val body = runCatching { httpGet(url) }.getOrNull()
            if (body != null) {
                val parsed = runCatching { parseFeatureCollection(body, layer) }.getOrNull()
                if (parsed != null) {
                    println("[INFO] INPN $layer: ${parsed.size} features in bbox")
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
            // INPN endpoint blocks custom User-Agents — use standard browser UA
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .header("Accept", "application/json, text/plain, */*")
            .build()
        return runCatching {
            httpClient.newCall(request).execute().use { resp ->
                when {
                    !resp.isSuccessful -> {
                        val body = resp.body?.string()?.take(200) ?: "(no body)"
                        println("[WARN] INPN HTTP ${resp.code} for ${resp.request.url}: $body")
                        null
                    }
                    else -> resp.body?.string()
                }
            }
        }.getOrNull()
    }

    /**
     * Build a WFS GetFeature URL. INPN uses WFS 2.0.0 with EPSG:4326 natively.
     *
     * Per the endpoint spec:
     *   service=WFS
     *   version=2.0.0
     *   request=GetFeature
     *   outputFormat=application/json
     *   typeName=wfs_inpn:amp_polygones
     *   srsName=EPSG:4326
     *   BBOX=latSouth,lonWest,latNorth,lonEast,EPSG:4326
     *
     * Note: `BBOX` is uppercase (OGC WFS case-sensitive), and the coordinate
     * order is (lat, lon) per EPSG:4326 axis order, not (lon, lat).
     */
    private fun buildGetFeatureUrl(layer: String, bbox: BoundingBox): String = buildString {
        append(baseUrl)
        append("?service=WFS")
        append("&version=2.0.0")
        append("&request=GetFeature")
        append("&typeName=").append(layer)
        append("&outputFormat=application/json")
        append("&srsName=EPSG:4326")
        // BBOX in EPSG:4326 axis order (lat,lon) — uppercase per OGC WFS spec
        append("&BBOX=${bbox.latSouth},${bbox.lonWest},${bbox.latNorth},${bbox.lonEast},EPSG:4326")
    }

    /**
     * Parse a GeoJSON FeatureCollection string into a [List] of [RegulatedZone].
     */
    private fun parseFeatureCollection(body: String, layer: String): List<RegulatedZone> {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: return emptyList()
        if (root["type"]?.jsonPrimitive?.content != "FeatureCollection") return emptyList()

        val features = root["features"]?.jsonArray ?: return emptyList()
        return features.mapNotNull { feature ->
            parseFeature(feature, layer)
        }
    }

    /**
     * Parse a single GeoJSON Feature from INPN into a [RegulatedZone].
     *
     * INPN property mapping:
     *   mpa_type     → classification (e.g. "Natura 2000", "Biotope", "Reserve")
     *   mpa_mnhnid   → sourceRef (MNHN identifier)
     *   mpa_name     → name
     *   mpa_descr    → description
     *   geometry     → Polygon outerRing + holes
     */
    private fun parseFeature(feature: JsonElement, layer: String): RegulatedZone? {
        val obj = feature.jsonObject ?: return null
        val geometry = obj["geometry"]?.jsonObject ?: return null
        val properties = obj["properties"]?.jsonObject ?: return null

        val geomType = geometry["type"]?.jsonPrimitive?.content ?: return null
        val coords = geometry["coordinates"] ?: return null

        // ── INPN property extraction ────────────────────────────────────────
        val mpaType = properties["mpa_type"]?.jsonPrimitive?.content
            ?: properties["type"]?.jsonPrimitive?.content
            ?: layer  // fall back to layer name if no type property

        val mnhnId = properties["mpa_mnhnid"]?.jsonPrimitive?.content
            ?: properties["id"]?.jsonPrimitive?.content

        val name = properties["mpa_name"]?.jsonPrimitive?.content
            ?: properties["name"]?.jsonPrimitive?.content
            ?: properties["nom"]?.jsonPrimitive?.content
            ?: ""

        val description = properties["mpa_descr"]?.jsonPrimitive?.content
            ?: properties["description"]?.jsonPrimitive?.content
            ?: properties["descr"]?.jsonPrimitive?.content
            ?: ""

        // ── Map MPA type to zone type ───────────────────────────────────────
        val zoneType = mapMpaTypeToZoneType(mpaType)

        // ── Build classification ────────────────────────────────────────────
        val classification = RegulationClassification.InpnMpa(
            type = mpaType,
            mnhnId = mnhnId
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
                    source = "INPN",
                    sourceRef = mnhnId ?: "",
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
                    source = "INPN",
                    sourceRef = mnhnId ?: "",
                    description = description,
                    classification = classification,
                    speedSource = SpeedSource.NONE
                )
            }
            else -> null
        }
    }

    /**
     * Map INPN MPA type string to [RegulatedZoneType].
     *
     * Mapping rules:
     *   Natura 2000 (SIC, ZPS)            → ENVIRONMENTAL
     *   Arrêté de biotope                 → ACCESS_PROHIBITED
     *   Réserve Naturelle Nationale       → NAVIGATION_RESTRICTION
     *   Réserve Naturelle Régionale       → NAVIGATION_RESTRICTION
     *   Parc National                     → NAVIGATION_RESTRICTION
     *   Parc Naturel Marin                → ENVIRONMENTAL
     *   Site RAMSAR                       → ENVIRONMENTAL
     *   Unknown                           → ENVIRONMENTAL (conservative default for MPAs)
     */
    private fun mapMpaTypeToZoneType(mpaType: String): RegulatedZoneType {
        val t = mpaType.lowercase().trim()
        return when {
            "natura 2000" in t || "sic" == t || "zps" in t -> RegulatedZoneType.ENVIRONMENTAL
            "biotope" in t -> RegulatedZoneType.ACCESS_PROHIBITED
            "réserve naturelle" in t || "reserve naturelle" in t -> RegulatedZoneType.NAVIGATION_RESTRICTION
            "parc national" in t -> RegulatedZoneType.NAVIGATION_RESTRICTION
            "parc naturel marin" in t -> RegulatedZoneType.ENVIRONMENTAL
            "ramsar" in t -> RegulatedZoneType.ENVIRONMENTAL
            else -> RegulatedZoneType.ENVIRONMENTAL
        }
    }

    /**
     * Parse a GeoJSON Polygon coordinate array into (outerRing, holes).
     * Coordinates are assumed to be EPSG:4326 (WGS84) — no CRS conversion.
     */
    private fun parsePolygon(coords: JsonElement): Pair<List<LatLng>, List<List<LatLng>>>? {
        val rings = coords.jsonArray ?: return null
        if (rings.isEmpty()) return null

        val outerRing = parseRing(rings[0]) ?: return null
        val holes = rings.drop(1).mapNotNull { parseRing(it) }

        return Pair(outerRing, holes)
    }

    /**
     * Parse a GeoJSON ring in WGS84.
     * GeoJSON order is [longitude, latitude].
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

        // Ramer-Douglas-Peucker simplification
        val simplified = SpatialOperations.douglasPeucker(raw, epsilonM = 30.0)
        if (simplified.size < 3) return null
        return if (simplified.first() != simplified.last()) {
            simplified + simplified.first()
        } else {
            simplified
        }
    }

    companion object {
        /** INPN INSPIRE WFS endpoint. */
        const val INPN_BASE_URL = "https://inpn-inspire.mnhn.fr/geoservices/ows"

        /** INPN WFS layers for Marine Protected Areas. */
        val CANDIDATE_LAYERS = listOf(
            "wfs_inpn:amp_polygones",
            "wfs_inpn:natura2000_sic",
        )

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
