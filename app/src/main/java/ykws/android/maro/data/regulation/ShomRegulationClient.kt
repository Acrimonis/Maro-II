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
import ykws.android.maro.spatial.SpatialOperations
import ykws.android.maro.data.model.BoundingBox
import ykws.android.maro.data.model.LatLng
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.tan

/**
 * Client for the **SHOM WFS** maritime regulation zones endpoint.
 *
 * Fetches from the **public INSPIRE** endpoint which serves regulation layers without
 * authentication. Also queries the richer `REGNAV_BDD_WFS:resare` layer (available on the
 * same public endpoint) which provides S-57 CATREA/RESTRN/INFORM/TXTDSC properties.
 *
 * Coordinates are in EPSG:3857 (Web Mercator) and are converted to WGS84 on the fly.
 *
 * Best-effort: returns empty list on error (never throws).
 *
 * @property httpClient  OkHttpClient used for all HTTP calls.
 * @property baseUrl     SHOM INSPIRE WFS base URL (default: [INSPIRE_BASE_URL]).
 */
class ShomRegulationClient(
    private val httpClient: OkHttpClient = defaultClient(),
    private val baseUrl: String = INSPIRE_BASE_URL
) {
    private val json = Json { ignoreUnknownKeys = true }

    // ── Public API ──────────────────────────────────────────────────────────

    /**
     * Fetch all SHOM regulation zones intersecting [bbox] across all candidate typeNames.
     */
    suspend fun fetchZones(
        bbox: BoundingBox,
        onProgress: (Int) -> Unit = {}
    ): List<RegulatedZone> = withContext(Dispatchers.IO) {
        val total = CANDIDATE_TYPENAMES.size
        if (total == 0) return@withContext emptyList()

        val zones = mutableListOf<RegulatedZone>()
        CANDIDATE_TYPENAMES.forEachIndexed { index, typeName ->
            val url = buildGetFeatureUrl(typeName)
            val body = runCatching { httpGet(url) }.getOrNull()
            if (body != null) {
                val parsed = runCatching { parseFeatureCollection(body, typeName) }.getOrNull()
                if (parsed != null) {
                    val inBbox = parsed.filter { zoneInBbox(it, bbox) }
                    if (inBbox.isNotEmpty()) {
                        println("[INFO] $typeName: ${inBbox.size} features in bbox (${parsed.size} total)")
                    }
                    zones.addAll(inBbox)
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
     */
    suspend fun getCapabilities(): List<String> = withContext(Dispatchers.IO) {
        val url = buildGetCapabilitiesUrl()
        val body = runCatching { httpGet(url) }.getOrNull() ?: return@withContext emptyList()
        parseCapabilitiesTypeNames(body)
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
                        println("[WARN] SHOM HTTP ${resp.code} for ${resp.request.url}: $body")
                        null
                    }
                    else -> resp.body?.string()
                }
            }
        }.getOrNull()
    }

    /**
     * Build a WFS GetFeature URL requesting GeoJSON — no bbox filter because
     * the INSPIRE endpoint expects EPSG:3857 coordinates.
     */
    private fun buildGetFeatureUrl(typeName: String): String = buildString {
        append(baseUrl)
        append("?service=WFS")
        append("&version=2.0.0")
        append("&request=GetFeature")
        append("&typeNames=").append(typeName)
        append("&outputFormat=application/json")
        append("&srsName=EPSG:3857")
    }

    private fun buildGetCapabilitiesUrl(): String = buildString {
        append(baseUrl)
        append("?service=WFS")
        append("&version=2.0.0")
        append("&request=GetCapabilities")
    }

    /**
     * Parse a WFS GetCapabilities XML response to extract available feature typeNames.
     */
    private fun parseCapabilitiesTypeNames(xml: String): List<String> {
        return runCatching {
            val typeNames = mutableListOf<String>()
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
     * Coordinates are assumed to be in EPSG:3857 and converted to WGS84.
     *
     * @param body     The raw GeoJSON response string.
     * @param typeName The WFS typeName that produced this response (recorded as sourceLayer).
     */
    private fun parseFeatureCollection(body: String, typeName: String): List<RegulatedZone> {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: return emptyList()
        if (root["type"]?.jsonPrimitive?.content != "FeatureCollection") return emptyList()

        val features = root["features"]?.jsonArray ?: return emptyList()
        return features.mapNotNull { feature ->
            parseFeature(feature, typeName)
        }
    }

    /**
     * Parse a single GeoJSON Feature element into a [RegulatedZone].
     *
     * Handles both the INSPIRE property names (restrn, inform, objnam, etc.)
     * and the richer `REGNAV_BDD_WFS:resare` property names (CATREA, RESTRN,
     * INFORM, TXTDSC, etc.).
     */
    private fun parseFeature(feature: JsonElement, typeName: String): RegulatedZone? {
        val obj = feature.jsonObject ?: return null
        val geometry = obj["geometry"]?.jsonObject ?: return null
        val properties = obj["properties"]?.jsonObject ?: return null

        val geomType = geometry["type"]?.jsonPrimitive?.content ?: return null
        val coords = geometry["coordinates"] ?: return null

        // ── Detect layer type ───────────────────────────────────────────────
        val isResareLayer = typeName.contains("resare", ignoreCase = true)

        // ── Zone type ───────────────────────────────────────────────────────
        val zoneType: RegulatedZoneType
        val classification: RegulationClassification?

        if (isResareLayer && properties["CATREA"]?.jsonPrimitive?.content != null) {
            // Auth-style layer with CATREA code
            val catreaCode = properties["CATREA"]?.jsonPrimitive?.content?.toIntOrNull()
            val restrnCode = properties["RESTRN"]?.jsonPrimitive?.content?.toIntOrNull()
            val inspRestrn = properties["restrn"]?.jsonPrimitive?.content?.toIntOrNull()

            zoneType = if (catreaCode != null) {
                parseCatrea(catreaCode)
            } else if (restrnCode != null) {
                parseRestrnAuth(restrnCode)
            } else if (inspRestrn != null) {
                parseRestrictionCode(inspRestrn.toString())
            } else {
                RegulatedZoneType.OTHER
            }

            classification = when {
                catreaCode != null -> RegulationClassification.Catrea(catreaCode)
                restrnCode != null -> RegulationClassification.Restrn(restrnCode)
                inspRestrn != null -> RegulationClassification.S101(inspRestrn)
                else -> null
            }
        } else {
            // INSPIRE-style layer with restrn code
            val restnVal = properties["restrn"]?.jsonPrimitive?.content
            val typeReg = properties["type_reglementation"]?.jsonPrimitive?.content

            zoneType = if (typeReg != null) {
                parseZoneType(typeReg)
            } else {
                parseRestrictionCode(restnVal)
            }

            classification = restnVal?.toIntOrNull()?.let { RegulationClassification.S101(it) }
        }

        // Raw S-101 restriction code (for backward compat with displayCategories)
        val restrictionCode = properties["restrn"]?.jsonPrimitive?.content?.toIntOrNull()

        // ── Speed limit ─────────────────────────────────────────────────────
        // Priority: vitesse_max (structured) > INFORM text > TXTDSC map
        val vitesseMax = properties["vitesse_max"]?.jsonPrimitive?.doubleOrNull
        val informText = properties["INFORM"]?.jsonPrimitive?.content
            ?: properties["inform"]?.jsonPrimitive?.content
            ?: properties["inform_fr"]?.jsonPrimitive?.content
        val txtdscText = properties["TXTDSC"]?.jsonPrimitive?.content

        val (speedLimitKn, speedSource) = when {
            vitesseMax != null -> vitesseMax to SpeedSource.STRUCTURED_FIELD
            informText != null -> {
                val parsed = parseSpeedFromInform(informText)
                if (parsed != null) parsed to SpeedSource.INFORM_TEXT
                else null to SpeedSource.NONE
            }
            txtdscText != null -> {
                val fromMap = TXTDSC_SPEED_MAP[txtdscText.trim()]
                if (fromMap != null) fromMap to SpeedSource.TXTDSC_MAP
                else null to SpeedSource.NONE
            }
            else -> null to SpeedSource.NONE
        }

        // ── Name ────────────────────────────────────────────────────────────
        val name = properties["objnam"]?.jsonPrimitive?.content
            ?: properties["nobjnm"]?.jsonPrimitive?.content
            ?: properties["nom"]?.jsonPrimitive?.content
            ?: ""

        // ── Source reference ────────────────────────────────────────────────
        val sourceRef = properties["inspireid"]?.jsonPrimitive?.content
            ?: properties["id_reglementation"]?.jsonPrimitive?.content
            ?: properties["INSPIREID"]?.jsonPrimitive?.content
            ?: ""

        // ── Description ─────────────────────────────────────────────────────
        val description = properties["inform"]?.jsonPrimitive?.content
            ?: properties["INFORM"]?.jsonPrimitive?.content
            ?: properties["ninfom"]?.jsonPrimitive?.content
            ?: properties["description"]?.jsonPrimitive?.content
            ?: ""

        // ── Raw French INFORM text ──────────────────────────────────────────
        val informFr = properties["inform_fr"]?.jsonPrimitive?.content
            ?: properties["INFORM"]?.jsonPrimitive?.content
            ?: properties["inform"]?.jsonPrimitive?.content

        // ── Legal decree reference (TXTDSC) ─────────────────────────────────
        val legalDecreeRef = txtdscText
            ?: properties["TXTDSC"]?.jsonPrimitive?.content

        // ── Parse geometry ──────────────────────────────────────────────────
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
                    description = description,
                    restrictionCode = restrictionCode,
                    classification = classification,
                    speedSource = speedSource,
                    legalDecreeRef = legalDecreeRef
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
                    speedLimitKn = speedLimitKn,
                    name = name,
                    source = "SHOM",
                    sourceRef = sourceRef,
                    description = description,
                    restrictionCode = restrictionCode,
                    classification = classification,
                    speedSource = speedSource,
                    legalDecreeRef = legalDecreeRef
                )
            }
            else -> null
        }
    }

    /**
     * Map SHOM INSPIRE restriction code to [RegulatedZoneType].
     * Based on S-101 / SHOM restrn enumeration:
     *   1  = Speed limit
     *   2  = Depth limit / draught
     *   7  = Anchoring prohibited
     *   8  = Fishing prohibited
     *   9  = Trawling prohibited
     *   10 = Prohibited area (access)
     *   11 = Entry prohibited (access)
     *   12 = Exit prohibited
     *   18 = Berthing prohibited
     *   27 = Seasonal restriction (check perend/persta)
     *   28 = Marine nature reserve
     */
    private fun parseRestrictionCode(code: String?): RegulatedZoneType = when (code?.trim()) {
        "1" -> RegulatedZoneType.SPEED_LIMIT
        "7" -> RegulatedZoneType.ANCHORING_PROHIBITED
        "8", "9" -> RegulatedZoneType.FISHING_PROHIBITED
        "10", "11", "12" -> RegulatedZoneType.ACCESS_PROHIBITED
        "18" -> RegulatedZoneType.MOORING
        "27" -> RegulatedZoneType.NAVIGATION_RESTRICTION
        "28" -> RegulatedZoneType.ENVIRONMENTAL
        else -> RegulatedZoneType.OTHER
    }

    /**
     * Parse S-57 CATREA (Category of Regulation Area) code to [RegulatedZoneType].
     *
     * Common CATREA codes:
     *   12 = Navigation restriction area
     *   27 = Speed limit zone
     *   31 = Anchoring prohibition area
     *   32 = Fishing prohibition area
     *   36 = Mooring area
     *   41 = Diving prohibition area
     *   71 = Protected area
     */
    private fun parseCatrea(code: Int): RegulatedZoneType = when (code) {
        12 -> RegulatedZoneType.NAVIGATION_RESTRICTION
        27 -> RegulatedZoneType.SPEED_LIMIT
        31 -> RegulatedZoneType.ANCHORING_PROHIBITED
        32 -> RegulatedZoneType.FISHING_PROHIBITED
        36 -> RegulatedZoneType.MOORING
        41 -> RegulatedZoneType.ACCESS_PROHIBITED
        71 -> RegulatedZoneType.ENVIRONMENTAL
        else -> RegulatedZoneType.OTHER
    }

    /**
     * Parse S-57 RESTRN (Restriction) code to [RegulatedZoneType].
     *
     * Common RESTRN codes:
     *   1 = Anchoring prohibited
     *   2 = Anchoring prohibited for specific vessels
     *   7 = Entry prohibited
     *   8 = Access prohibited for specific vessels
     *   10 = Other (e.g. diving restriction)
     */
    private fun parseRestrnAuth(code: Int): RegulatedZoneType = when (code) {
        1, 2 -> RegulatedZoneType.ANCHORING_PROHIBITED
        7, 8 -> RegulatedZoneType.ACCESS_PROHIBITED
        10 -> RegulatedZoneType.OTHER  // Diving prohibition — mapped via displayCategories
        else -> RegulatedZoneType.OTHER
    }

    /**
     * Parse a speed limit from an INFORM description string.
     *
     * Handles expanded keyword matching:
     *   "5 knots", "10 kn", "3 noeuds", "6 nds", "15 nd"
     *   "vitesse limitée à 10 nœuds", "speed is limited to 5 knots"
     *
     * Supports: nœuds, noeud, nds, nd, kts, knot, knots, noeuds
     */
    private fun parseSpeedFromInform(desc: String?): Double? {
        if (desc == null) return null
        val regex = Regex(
            """(\d+[.]?\d*)\s*(nœuds?|noeuds?|nds|nd|kts|knots?|kn)\b""",
            RegexOption.IGNORE_CASE
        )
        val match = regex.find(desc.lowercase()) ?: return null
        return match.groupValues[1].toDoubleOrNull()
    }

    /**
     * Parse a GeoJSON Polygon coordinate array into (outerRing, holes).
     * Coordinates are in EPSG:3857 and converted to WGS84.
     */
    private fun parsePolygon(coords: JsonElement): Pair<List<LatLng>, List<List<LatLng>>>? {
        val rings = coords.jsonArray ?: return null
        if (rings.isEmpty()) return null

        val outerRing = parseRing(rings[0]) ?: return null
        val holes = rings.drop(1).mapNotNull { parseRing(it) }

        return Pair(outerRing, holes)
    }

    /**
     * Parse a GeoJSON ring, auto-detecting CRS:
     * - If coordinates look like EPSG:3857 (|x| > 180), convert from Web Mercator.
     * - Otherwise treat as WGS84 lat/lon.
     * GeoJSON order is [x, y] = [easting, northing] or [lon, lat].
     */
    private fun parseRing(ring: JsonElement): List<LatLng>? {
        val points = ring.jsonArray ?: return null
        val raw = points.mapNotNull { point ->
            val arr = point.jsonArray ?: return@mapNotNull null
            if (arr.size < 2) return@mapNotNull null
            val x = arr[0].jsonPrimitive.doubleOrNull ?: return@mapNotNull null
            val y = arr[1].jsonPrimitive.doubleOrNull ?: return@mapNotNull null
            if (kotlin.math.abs(x) > 180.0 || kotlin.math.abs(y) > 90.0) {
                // Looks like EPSG:3857 — convert
                webMercatorToWgs84(x, y)
            } else {
                // Already WGS84 lat/lon — GeoJSON order is [lon, lat]
                LatLng(latitude = y, longitude = x)
            }
        }.takeIf { it.size >= 3 } ?: return null

        // Ramer-Douglas-Peucker simplification: ε ≈ 30 m, visually indicative
        // not survey-grade. Match pattern from CoastlineGenerator.
        val simplified = SpatialOperations.douglasPeucker(raw, epsilonM = 30.0)

        // Ensure the ring has at least 3 unique vertices and remains closed
        if (simplified.size < 3) return null
        return if (simplified.first() != simplified.last()) {
            simplified + simplified.first()
        } else {
            simplified
        }
    }

    /**
     * Convert EPSG:3857 (Web Mercator) coordinates to WGS84 (lat/lon).
     */
    private fun webMercatorToWgs84(x: Double, y: Double): LatLng {
        val lon = x / EARTH_RADIUS_M * 180.0 / PI
        val lat = (PI / 2.0 - 2.0 * atan(exp(-y / EARTH_RADIUS_M))) * 180.0 / PI
        return LatLng(latitude = lat, longitude = lon)
    }

    /** Check if a zone's centroid falls within [bbox]. */
    private fun zoneInBbox(zone: RegulatedZone, bbox: BoundingBox): Boolean {
        if (zone.outerRing.isEmpty()) return false
        val centerLat = zone.outerRing.map { it.latitude }.average()
        val centerLon = zone.outerRing.map { it.longitude }.average()
        return centerLat in bbox.latSouth..bbox.latNorth &&
                centerLon in bbox.lonWest..bbox.lonEast
    }

    private fun parseZoneType(type: String): RegulatedZoneType = when (type.trim().lowercase()) {
        "vitesse" -> RegulatedZoneType.SPEED_LIMIT
        "mouillage" -> RegulatedZoneType.ANCHORING_PROHIBITED
        "acces_interdit" -> RegulatedZoneType.ACCESS_PROHIBITED
        "protection" -> RegulatedZoneType.ENVIRONMENTAL
        else -> RegulatedZoneType.OTHER
    }

    companion object {
        /** Public INSPIRE WFS endpoint — no authentication required. */
        const val INSPIRE_BASE_URL = "https://services.data.shom.fr/INSPIRE/wfs"

        /** Auth-protected endpoint (requires SHOM API key). */
        const val DEFAULT_BASE_URL = "https://services.data.shom.fr/wfs/reglementation"

        /** Web Mercator (EPSG:3857) sphere radius = WGS84 semi-major axis. */
        const val EARTH_RADIUS_M = 6_378_137.0

        /**
         * Regulation layers available on the public INSPIRE endpoint.
         *
         * The INSPIRE endpoint provides anonymous read-only access to all listed
         * layers. Some layers carry richer S-57 properties (CATREA, RESTRN, INFORM)
         * under INSPIRE-native property names — the parser handles both conventions.
         *
         * The `REGNAV_BDD_WFS:resare` layer (native S-57 property names) is available
         * only on the authenticated endpoint at [DEFAULT_BASE_URL].
         */
        val CANDIDATE_TYPENAMES = listOf(
            "REGLEMENTATION_NAVIGATION_BDD_WFS:resare_polygon",
            "REGLEMENTATION_NAVIGATION_BDD_WFS:splare_polygon",
            "REGLEMENTATION_NAVIGATION_BDD_WFS:achare_polygon",
            "REGLEMENTATION_NAVIGATION_BDD_WFS:achare_point",
            "REGLEMENTATION_NAVIGATION_BDD_WFS:ctsare_polygon",
            "REGLEMENTATION_NAVIGATION_BDD_WFS:admare_polygon",
        )

        /**
         * TXTDSC-to-speed lookup table.
         *
         * Maps known legal decree references (TXTDSC values) to their speed limits
         * in knots. These are derived from official French maritime prefect decrees
         * (arrêtés préfectoraux) for the Nice–Fréjus corridor.
         */
        val TXTDSC_SPEED_MAP: Map<String, Double> = mapOf(
            "FR_PREMAR_MED_134_2021" to 10.0,
            "FR_PREMAR_MED_2012_064" to 5.0,
        )

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
