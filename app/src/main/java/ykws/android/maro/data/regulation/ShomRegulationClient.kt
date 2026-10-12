package ykws.android.maro.data.regulation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.tan

/**
 * Client for the **SHOM WFS** maritime regulation zones endpoint.
 *
 * Fetches the `REGLEMENTATION_NAVIGATION_BDD_WFS:*` regulation layers from the **public
 * INSPIRE** endpoint, which serves them without authentication. The richer native
 * `REGNAV_BDD_WFS:resare` layer sits behind an API key (the INSPIRE host answers
 * `MissingRights` and the `wfs/reglementation` host demands a `key` parameter) and is
 * therefore not queried here.
 *
 * GeoJSON is requested in EPSG:3857 (Web Mercator) with a **server-side bbox**, and the
 * coordinates are converted to WGS84 on the fly — the INSPIRE endpoint treats an
 * EPSG:4326 bbox in lat/lon order and ignores it for these layers, so a 3857-metre bbox
 * is the only form that actually filters server-side.
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
            val url = buildGetFeatureUrl(typeName, bbox)
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
     * Build a WFS GetFeature URL requesting GeoJSON for [bbox].
     *
     * The bbox is expressed in EPSG:3857 metres concatenated as `minX,minY,maxX,maxY`,
     * matching the requested [srsName] — this is the only form the INSPIRE endpoint
     * actually filters on for the regulation layers.
     */
    private fun buildGetFeatureUrl(typeName: String, bbox: BoundingBox): String = buildString {
        append(baseUrl)
        append("?service=WFS")
        append("&version=2.0.0")
        append("&request=GetFeature")
        append("&typeNames=").append(typeName)
        append("&outputFormat=application/json")
        append("&srsName=EPSG:3857")
        append("&bbox=")
            .append(lonToMercatorX(bbox.lonWest - BBOX_PAD_DEG).roundToLong()).append(',')
            .append(latToMercatorY(bbox.latSouth - BBOX_PAD_DEG).roundToLong()).append(',')
            .append(lonToMercatorX(bbox.lonEast + BBOX_PAD_DEG).roundToLong()).append(',')
            .append(latToMercatorY(bbox.latNorth + BBOX_PAD_DEG).roundToLong())
    }

    private fun buildGetCapabilitiesUrl(): String = buildString {
        append(baseUrl)
        append("?service=WFS")
        append("&version=2.0.0")
        append("&request=GetCapabilities")
    }

    /** Convert a WGS84 longitude to an EPSG:3857 easting (metres). */
    private fun lonToMercatorX(lon: Double): Double = lon * EARTH_RADIUS_M * PI / 180.0

    /** Convert a WGS84 latitude to an EPSG:3857 northing (metres). */
    private fun latToMercatorY(lat: Double): Double =
        EARTH_RADIUS_M * ln(tan(PI / 4.0 + lat * PI / 360.0))

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
    internal fun parseFeatureCollection(body: String, typeName: String): List<RegulatedZone> {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: return emptyList()
        if (root["type"]?.jsonPrimitive?.content != "FeatureCollection") return emptyList()

        val features = root["features"]?.jsonArray ?: return emptyList()
        return features.flatMap { feature -> parseFeature(feature, typeName) }
    }

    /**
     * Parse a single GeoJSON Feature element into zero or more [RegulatedZone]s.
     *
     * A `MultiPolygon` feature becomes one zone per polygon and a `Point` feature is
     * buffered into a small ring (its source carries no radius). Zone type resolves in
     * priority order — parsed speed limit, `type_reglementation`, S-57 `restrn`/`RESTRN`
     * codes, CATREA, CATACH, then the description text.
     */
    private fun parseFeature(feature: JsonElement, typeName: String): List<RegulatedZone> {
        val obj = feature.jsonObject ?: return emptyList()
        val geometry = obj["geometry"]?.jsonObject ?: return emptyList()
        val properties = obj["properties"]?.jsonObject ?: return emptyList()

        val geomType = geometry["type"]?.jsonPrimitive?.content ?: return emptyList()
        val coords = geometry["coordinates"] ?: return emptyList()

        // ── Classification codes ────────────────────────────────────────────
        // `restrn` (INSPIRE) and `RESTRN` (native) may carry several codes at once,
        // e.g. "3,11" — every code is kept, the first drives the zone type.
        val restrictionCodes = parseCodes(
            properties["RESTRN"]?.jsonPrimitive?.content,
            properties["restrn"]?.jsonPrimitive?.content
        )
        val catreaCode = (properties["CATREA"]?.jsonPrimitive?.content
            ?: properties["catrea"]?.jsonPrimitive?.content)
            ?.substringBefore(',')?.trim()?.toIntOrNull()
        val catachCode = properties["catach"]?.jsonPrimitive?.content
            ?.substringBefore(',')?.trim()?.toIntOrNull()
        val typeReglementation = properties["type_reglementation"]?.jsonPrimitive?.content

        // ── Speed limit ─────────────────────────────────────────────────────
        // Priority: vitesse_max (structured) > INFORM text > TXTDSC map
        val vitesseMax = properties["vitesse_max"]?.jsonPrimitive?.doubleOrNull
        val informText = properties["INFORM"]?.jsonPrimitive?.content
            ?: properties["inform"]?.jsonPrimitive?.content
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
        // The SHOM WFS sometimes returns the literal string "null" — cleanText
        // drops it so SpeedZoneBuilder can apply its fallback.
        val name = cleanText(
            properties["objnam"]?.jsonPrimitive?.content
                ?: properties["nobjnm"]?.jsonPrimitive?.content
                ?: properties["nom"]?.jsonPrimitive?.content
        ) ?: ""

        // ── Source reference ────────────────────────────────────────────────
        val sourceRef = cleanText(
            properties["inspireid"]?.jsonPrimitive?.content
                ?: properties["id_reglementation"]?.jsonPrimitive?.content
                ?: properties["INSPIREID"]?.jsonPrimitive?.content
        ) ?: ""

        // ── Description (English INFORM preferred, French NINFOM fallback) ──
        val description = cleanText(
            properties["inform"]?.jsonPrimitive?.content
                ?: properties["INFORM"]?.jsonPrimitive?.content
                ?: properties["ninfom"]?.jsonPrimitive?.content
                ?: properties["description"]?.jsonPrimitive?.content
        ) ?: ""

        // ── Legal decree reference (TXTDSC) ─────────────────────────────────
        val legalDecreeRef = cleanText(txtdscText ?: properties["TXTDSC"]?.jsonPrimitive?.content)

        // ── Zone type & provenance classification ───────────────────────────
        val zoneType = classifyZone(
            restrictionCodes, catreaCode, catachCode, description, speedLimitKn, typeReglementation
        )
        val classification = when {
            restrictionCodes.isNotEmpty() -> RegulationClassification.Restrn(restrictionCodes.first())
            catreaCode != null -> RegulationClassification.Catrea(catreaCode)
            else -> null
        }

        // ── Geometry → one or more zones ────────────────────────────────────
        val polygons: List<Pair<List<LatLng>, List<List<LatLng>>>> = when (geomType) {
            "Polygon" -> listOfNotNull(parsePolygon(coords))
            "MultiPolygon" ->
                (coords.jsonArray ?: return emptyList()).mapNotNull { parsePolygon(it) }
            "Point" -> parsePoint(coords)
                ?.let { listOf(bufferPoint(it, POINT_BUFFER_RADIUS_M) to emptyList()) }
                ?: return emptyList()
            else -> return emptyList()
        }

        return polygons.map { (outerRing, holes) ->
            RegulatedZone(
                outerRing = outerRing,
                holes = holes,
                zoneType = zoneType,
                speedLimitKn = speedLimitKn,
                name = name,
                source = "SHOM",
                sourceRef = sourceRef,
                description = description,
                restrictionCode = restrictionCodes.firstOrNull(),
                classification = classification,
                speedSource = speedSource,
                legalDecreeRef = legalDecreeRef,
                sourceLayer = typeName,
                restrictionCodes = restrictionCodes
            )
        }
    }

    /** Split one or more comma-separated code strings into a distinct, ordered code list. */
    private fun parseCodes(vararg values: String?): List<Int> =
        values.filterNotNull()
            .flatMap { it.split(',') }
            .mapNotNull { it.trim().toIntOrNull() }
            .distinct()

    /**
     * Resolve a zone's type in priority order:
     * parsed speed limit → `type_reglementation` → RESTRN codes → CATREA → CATACH →
     * description text.
     *
     * A non-null speed limit always wins — a zone that carries a speed limit is a
     * speed zone, regardless of its S-57 codes, because SHOM encodes speed as code 27
     * (navigation restriction) or as an access code 8 with a speed INFORM text.
     *
     * CATACH 1 (unrestricted) and 8 (small craft) mark a **positive** anchorage, so
     * they map to [RegulatedZoneType.MOORING] — the ACHARE prohibition case is carried
     * by RESTRN (1/2) and never lands here.
     */
    private fun classifyZone(
        restrictionCodes: List<Int>,
        catreaCode: Int?,
        catachCode: Int?,
        description: String,
        speedLimitKn: Double?,
        typeReglementation: String?
    ): RegulatedZoneType {
        if (speedLimitKn != null) return RegulatedZoneType.SPEED_LIMIT
        typeReglementation?.let { return parseZoneType(it) }
        restrictionCodes.firstOrNull()?.let { return restrictionType(it) }
        catreaCode?.let { return parseCatrea(it) }
        if (catachCode == 1 || catachCode == 8) return RegulatedZoneType.MOORING
        val desc = description.lowercase()
        if (MOORING_KEYWORDS.any { it in desc }) return RegulatedZoneType.MOORING
        return RegulatedZoneType.OTHER
    }

    /**
     * Map the INSPIRE `type_reglementation` string to a [RegulatedZoneType].
     *
     * The legacy INSPIRE classification carried by some regulation layers alongside
     * the S-57 codes; `mouillage` means a mooring area, not an anchoring
     * prohibition.
     */
    private fun parseZoneType(type: String): RegulatedZoneType = when (type.trim().lowercase()) {
        "vitesse" -> RegulatedZoneType.SPEED_LIMIT
        "mouillage" -> RegulatedZoneType.MOORING
        "acces_interdit" -> RegulatedZoneType.ACCESS_PROHIBITED
        "protection" -> RegulatedZoneType.ENVIRONMENTAL
        else -> RegulatedZoneType.OTHER
    }

    /**
     * SHOM publishes the literal string "null" (and blanks) for absent text
     * properties — treat them as absent so no field ever carries "null".
     */
    private fun cleanText(value: String?): String? =
        value?.trim()?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }

    /**
     * Map a SHOM restriction code to a [RegulatedZoneType] — ONE table, read by
     * [classifyZone] for both the INSPIRE `restrn` list and the native `RESTRN` list.
     *
     * The values are S-57 RESTRN, verified against the baked source 2026-10-10:
     * every code-1/2 zone reads "anchoring … prohibited" and every code-7/8 zone
     * an entry/access restriction.
     *
     *   1, 2  = anchoring prohibited / restricted
     *   3-6   = fishing / trawling prohibited / restricted
     *   7, 8  = entry (access) prohibited / restricted
     *   10-12 = dredging / diving
     *   18    = berthing (mooring)
     *   27    = navigation restriction
     *   28    = marine nature reserve
     */
    private fun restrictionType(code: Int?): RegulatedZoneType = when (code) {
        1, 2 -> RegulatedZoneType.ANCHORING_PROHIBITED
        3, 4, 5, 6 -> RegulatedZoneType.FISHING_PROHIBITED
        7, 8 -> RegulatedZoneType.ACCESS_PROHIBITED
        10, 11, 12 -> RegulatedZoneType.OTHER // dredging / diving, flagged by displayCategories
        18 -> RegulatedZoneType.MOORING
        27 -> RegulatedZoneType.NAVIGATION_RESTRICTION
        28 -> RegulatedZoneType.ENVIRONMENTAL
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
            if (abs(x) > 180.0 || abs(y) > 90.0) {
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

    /** Parse a GeoJSON Point ([x, y]) into a [LatLng], auto-detecting CRS. */
    private fun parsePoint(coords: JsonElement): LatLng? {
        val arr = coords.jsonArray ?: return null
        if (arr.size < 2) return null
        val x = arr[0].jsonPrimitive.doubleOrNull ?: return null
        val y = arr[1].jsonPrimitive.doubleOrNull ?: return null
        return if (abs(x) > 180.0 || abs(y) > 90.0) {
            webMercatorToWgs84(x, y)
        } else {
            LatLng(latitude = y, longitude = x)
        }
    }

    /**
     * Buffer a point into a closed circular ring — the ACHARE point layer carries no
     * radius, so a fixed representative radius makes the zone renderable and queryable.
     */
    private fun bufferPoint(center: LatLng, radiusM: Double): List<LatLng> {
        val latRad = Math.toRadians(center.latitude)
        val dLat = radiusM / METRES_PER_DEGREE
        val dLon = radiusM / (METRES_PER_DEGREE * cos(latRad))
        val ring = (0 until POINT_BUFFER_SEGMENTS).map { i ->
            val theta = 2.0 * PI * i / POINT_BUFFER_SEGMENTS
            LatLng(
                latitude = center.latitude + dLat * sin(theta),
                longitude = center.longitude + dLon * cos(theta)
            )
        }
        return ring + ring.first()
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

    companion object {
        /** Public INSPIRE WFS endpoint — no authentication required. */
        const val INSPIRE_BASE_URL = "https://services.data.shom.fr/INSPIRE/wfs"

        /** Key-protected endpoint — serves no regulation layer anonymously, kept for reference. */
        const val DEFAULT_BASE_URL = "https://services.data.shom.fr/wfs/reglementation"

        /** Web Mercator (EPSG:3857) sphere radius = WGS84 semi-major axis. */
        const val EARTH_RADIUS_M = 6_378_137.0

        /** Metres per degree of latitude (spherical approximation, used to buffer a point). */
        private const val METRES_PER_DEGREE = 111_320.0

        /** Extra margin (degrees) added around the fetch bbox before converting to 3857. */
        private const val BBOX_PAD_DEG = 0.05

        /** Radius (m) of the circle a `Point` zone is buffered into — the source carries none. */
        private const val POINT_BUFFER_RADIUS_M = 100.0

        /** Vertex count of the buffered point circle. */
        private const val POINT_BUFFER_SEGMENTS = 16

        /** Description phrases that mark a positive (permitted) anchorage area. */
        private val MOORING_KEYWORDS = listOf(
            "mandatory anchoring", "small craft mooring", "mooring", "mouillage", "amarrage"
        )

        /**
         * Regulation layers served anonymously by the public INSPIRE endpoint.
         *
         * Property conventions vary by layer: `resare_polygon` carries `restrn` (a
         * comma-separated S-57 code list) and `catrea`; `achare_*` carries `restrn` and
         * `catach`; `splare_*` carries only descriptive text. The native
         * `REGNAV_BDD_WFS:resare` layer requires an API key and is not fetched.
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
