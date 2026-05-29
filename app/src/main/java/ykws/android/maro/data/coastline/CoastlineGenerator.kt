package ykws.android.maro.data.coastline

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import ykws.android.maro.data.model.*
import ykws.android.maro.spatial.SpatialOperations
import java.util.concurrent.TimeUnit
import kotlin.math.cos
import kotlin.math.PI

/**
 * Pipeline OSM pour la côte Nice–Fréjus.
 *
 * Pipeline:
 *   1. Fetch OSM depuis Overpass API (N endpoints redondants, race)
 *   2. Assemblage des segments en polylines continues
 *   3. Filtrage des îles (≤ 6 NM de la côte principale)
 *   4. Clipping à la zone réglementaire (6,70°E – 7,31°E)
 *   5. Simplification Douglas-Peucker (ε = 3 m)
 *   6. Validation de l'orientation (eau à droite)
 *   7. Calcul des vecteurs d'arête (dx, dy en mètres)
 *
 * @property islandMaxDistanceNm Maximum distance in nautical miles for island inclusion.
 * @property simplifyEpsilonM Douglas-Peucker simplification tolerance in meters.
 */
class CoastlineGenerator(
    private val islandMaxDistanceNm: Double = 6.0,
    private val simplifyEpsilonM: Double = 8.0
) {
    // ── Constants ───────────────────────────────────────────────────────────

    companion object {
        private const val EARTH_RADIUS_M = 6_371_000.0

        // Bounding box élargie pour récupérer les extrémités proprement
        const val BBOX_LAT_MIN = 43.30
        const val BBOX_LON_MIN = 6.58
        const val BBOX_LAT_MAX = 43.80
        const val BBOX_LON_MAX = 7.38

        // Zone réglementaire Nice–Fréjus
        const val LON_WEST = 6.70
        const val LON_EAST = 7.31

        // Default region identifier for this generator
        const val REGION_ID = "nice-frejus"

        private const val MATCH_THRESHOLD_M = 25.0

        /**
         * Overpass API endpoints ordered roughly by reliability.
         *
         * All requests are launched in parallel via [select] race — the first
         * successful response wins and the remaining in-flight requests are
         * cancelled immediately.
         *
         * Sources: https://wiki.openstreetmap.org/wiki/Overpass_API#Servers
         */
        private val OVERPASS_ENDPOINTS = listOf(
            "https://overpass-api.de/api/interpreter",        // 🇩🇪 Main instance, most reliable
            "https://overpass.kumi.systems/api/interpreter",   // 🇩🇪 Community instance
            "https://overpass-api.bbbike.org/api/interpreter", // 🇩🇪 BBBike instance
            "https://overpass.osm.vi-di.fr/api/interpreter",   // 🇫🇷 France instance (low latency)
            "https://overpass.kontur.io/api/interpreter",      // 🇺🇸 Kontur instance (geo-diversity)
            "https://overpass.openstreetmap.ru/api/interpreter" // 🇷🇺 Moved last (known timeout issues)
        )

        private val FORM_URLENCODED = "application/x-www-form-urlencoded".toMediaType()
    }

    /** Shared HTTP client with aggressive 10-second timeouts — fail fast, race wins. */
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    // ── Public API ──────────────────────────────────────────────────────────

    /**
     * Exécute le pipeline complet et retourne les données de côte traitées.
     *
     * @param regionId Identifier for the coastline region (used for caching).
     * @param onProgress Callback émettant (phase, progression 0‑100). Appelé
     *                   depuis le thread du coroutine scope parent.
     */
    suspend fun generate(
        regionId: String = REGION_ID,
        onProgress: (phase: String, progress: Int) -> Unit = { _, _ -> }
    ): CoastlineData = withContext(Dispatchers.IO) {
        onProgress("Démarrage", 0)

        // ── 1. Fetch OSM with tags (0 → 25) ────────────────────────────────
        val rawWays = fetchOverpass { pct ->
            onProgress("Téléchargement OSM", (pct * 25 / 100).coerceAtMost(25))
        }
        onProgress("Assemblage", 25)

        // ── 2. Parse ways → segments with metadata (25 → 40) ───────────────
        val parsedWays = rawWays.mapNotNull { way -> parseOsmWay(way) }
        if (parsedWays.isEmpty()) {
            throw IllegalStateException("Aucun way[natural=coastline] trouvé dans la réponse Overpass.")
        }

        // Separate island ways (explicit tag) from mainland ways
        val islandWays = parsedWays.filter { it.isExplicitIsland }
        val mainlandWays = parsedWays.filter { !it.isExplicitIsland }
        onProgress("Parsing", 40)

        // ── 3. Assembly (40 → 55) ───────────────────────────────────────────
        // Assemble mainland and island ways separately
        val mainlandPolylines = assembleWithNodeIds(mainlandWays.map { it.toSegment() })
        val islandPolylines = assembleWithNodeIds(islandWays.map { it.toSegment() })

        if (mainlandPolylines.isEmpty()) {
            throw IllegalStateException("Aucune polyline de côte principale trouvée.")
        }
        onProgress("Filtrage îles", 55)

        // ── 4. Island filter (55 → 65) ──────────────────────────────────────
        val islandMaxDistM = islandMaxDistanceNm * 1852.0 // NM → meters
        val mainCoastline = mainlandPolylines.maxByOrNull { it.points.size }
            ?: throw IllegalStateException("Côte principale introuvable.")

        // Use explicit islands first, then check remaining mainland polylines by distance
        val allIslands = mutableListOf<RawSegment>()

        // Explicit islands: keep if within range
        for (island in islandPolylines) {
            if (SpatialOperations.polylinesMinDistance(mainCoastline.points, island.points, islandMaxDistM) <= islandMaxDistM) {
                allIslands.add(island)
            }
        }
        onProgress("Découpage & simplification", 65)

        // Mainland fragments that aren't the main coast: check if they're islands
        val otherFragments = mainlandPolylines.filter { it !== mainCoastline }
        for (fragment in otherFragments) {
            if (SpatialOperations.polylinesMinDistance(mainCoastline.points, fragment.points, islandMaxDistM) <= islandMaxDistM) {
                allIslands.add(fragment)
            }
        }
        onProgress("Découpage & simplification", 65)

        // ── 4b. Orientation detection using island positions (65 → 68) ──────
        // Islands are surrounded by water. If we have at least one island,
        // count how many islands fall on each side of the mainland coastline.
        // The side with more islands is the water side.
        // If no islands exist, the current orientation (from ensureWaterOnRight
        // with south=sea heuristic) is kept.
        val mainCoastlineOriented = if (allIslands.isNotEmpty()) {
            orientByIslandPositions(mainCoastline, allIslands)
        } else {
            mainCoastline
        }
        onProgress("Orientation îles", 68)

        // ── 5. Clip + Simplify + Orient (68 → 90) ───────────────────────────
        val processedMainland = processPolyline(mainCoastlineOriented.points, isMainland = true)
        val processedIslands = allIslands.mapNotNull { island ->
            val processed = processPolyline(island.points, isMainland = false)
            if (processed != null) processed to island.osmWayId else null
        }
        onProgress("Clipping & simplification", 90)

        if (processedMainland == null) {
            throw IllegalStateException("Aucune polyline après clipping + simplification.")
        }

        // Compute bounding box from processed LatLng BEFORE projecting to meters
        val rawBbox = computeBoundingBoxFromLatLng(
            listOf(processedMainland) + processedIslands.map { it.first }
        )
        val projectionRefLat = rawBbox.centerLat

        // ── 6. Compute edge vectors + projected XY + build CoastlinePoint (90 → 95) ──
        val mainlandPoints = computeEdgeVectors(processedMainland, projectionRefLat)
        val islandPoints = processedIslands.map { (polyline, _) ->
            computeEdgeVectors(polyline, projectionRefLat)
        }
        onProgress("Vecteurs d'arête", 95)

        // ── 7. Build final data structure (95 → 100) ────────────────────────
        val mainlandSegment = CoastlineSegment(
            osmWayId = mainCoastline.osmWayId,
            points = mainlandPoints,
            isMainland = true,
            isClosed = false
        )

        val islandSegments = processedIslands.zip(islandPoints) { (_, osmWayId), points ->
            CoastlineSegment(
                osmWayId = osmWayId,
                points = points,
                isMainland = false,
                isClosed = true  // OSM islands are closed rings
            )
        }

        // Compute metadata
        val totalPoints = mainlandPoints.size + islandPoints.sumOf { it.size }
        val totalLength = computeTotalLength(mainlandPoints, islandPoints)
        val meanSpacing = if (totalPoints > 1 + islandPoints.size) {
            totalLength / (totalPoints - 1 - islandPoints.size)
        } else 0.0

        val boundingBox = computeBoundingBox(mainlandPoints, islandPoints)

        val metadata = CoastlineMetadata(
            source = "OpenStreetMap contributors, ODbL (généré sur appareil)",
            pointCount = totalPoints,
            meanSpacingM = meanSpacing,
            totalLengthKm = totalLength / 1000.0,
            epsilonM = simplifyEpsilonM,
            fetchTimestampMs = System.currentTimeMillis(),
            projectionRefLat = projectionRefLat
        )

        onProgress("Terminé", 100)
        CoastlineData(
            mainland = mainlandSegment,
            islands = islandSegments,
            metadata = metadata,
            regionId = regionId,
            boundingBox = boundingBox
        )
    }

    // ── Edge vector computation ────────────────────────────────────────────

    /**
     * Computes edge vectors (dx_m, dy_m) and projected coordinates (xM, yM)
     * for each consecutive point pair, using a fixed reference latitude for
     * consistent projection.
     *
     * @param points The processed (clipped, simplified, oriented) polyline.
     * @param refLat Reference latitude for the local Cartesian projection.
     * @return List of [CoastlinePoint] with pre-computed edge vectors and XY.
     */
    private fun computeEdgeVectors(
        points: List<LatLng>,
        refLat: Double
    ): List<CoastlinePoint> {
        if (points.isEmpty()) return emptyList()

        val mPerDegLat = EARTH_RADIUS_M * PI / 180.0
        val mPerDegLon = mPerDegLat * cos(Math.toRadians(refLat))

        val result = mutableListOf<CoastlinePoint>()
        for (i in points.indices) {
            val lat = points[i].latitude
            val lon = points[i].longitude

            // Project this point to local Cartesian using FIXED reference latitude
            val xM = lon * mPerDegLon
            val yM = lat * mPerDegLat

            if (i < points.size - 1) {
                // Edge vector: offset to next point (computed at each edge's midpoint
                // for accuracy, while XY uses fixed refLat for consistency)
                val edgeMidLat = (lat + points[i + 1].latitude) / 2.0
                val edgeMPLon = mPerDegLat * cos(Math.toRadians(edgeMidLat))
                val edgeMPLat = mPerDegLat

                val dx = (points[i + 1].longitude - lon) * edgeMPLon
                val dy = (points[i + 1].latitude - lat) * edgeMPLat

                result.add(CoastlinePoint(
                    lat = lat.toFloat(),
                    lon = lon.toFloat(),
                    xM = xM.toFloat(),
                    yM = yM.toFloat(),
                    edgeDxM = dx.toFloat(),
                    edgeDyM = dy.toFloat()
                ))
            } else {
                // Last point: no outgoing edge
                result.add(CoastlinePoint(
                    lat = lat.toFloat(),
                    lon = lon.toFloat(),
                    xM = xM.toFloat(),
                    yM = yM.toFloat(),
                    edgeDxM = 0f,
                    edgeDyM = 0f
                ))
            }
        }
        return result
    }

    // ── Polyline processing pipeline ───────────────────────────────────────

    /**
     * Apply clip → simplify → orient to a single polyline.
     * Returns null if the polyline has no points after clipping.
     */
    private fun processPolyline(
        points: List<LatLng>,
        isMainland: Boolean
    ): List<LatLng>? {
        val clipped = points.filter { (_, lon) -> lon in LON_WEST..LON_EAST }
        if (clipped.isEmpty()) return null

        val simplified = SpatialOperations.douglasPeucker(clipped, simplifyEpsilonM)
        if (simplified.size < 2) return null

        // Orientation: water on right
        return SpatialOperations.ensureWaterOnRight(simplified)
    }

    // ── OSM Way parsing ────────────────────────────────────────────────────

    /**
     * Parsed OSM way with metadata.
     */
    private data class RawSegment(
        val osmWayId: Long,
        val points: List<LatLng>,
        val nodeIds: List<Long>,
        val tags: Map<String, String>,
        val isExplicitIsland: Boolean
    ) {
        fun toSegment() = this
    }

    /**
     * Parse a single OSM way JSON object into a [RawSegment].
     * Returns null if the way has insufficient geometry.
     */
    private fun parseOsmWay(way: JsonObject): RawSegment? {
        val id = way["id"]?.jsonPrimitive?.long ?: return null
        val geometry = way["geometry"]?.jsonArray ?: return null

        // Parse tags
        val tags = mutableMapOf<String, String>()
        val tagsJson = way["tags"]?.jsonObject
        if (tagsJson != null) {
            for (entry in tagsJson) {
                tags[entry.key] = entry.value.jsonPrimitive.content
            }
        }

        // Parse node IDs
        val nodeIds = mutableListOf<Long>()
        val nodesJson = way["nodes"]?.jsonArray
        if (nodesJson != null) {
            for (node in nodesJson) {
                nodeIds.add(node.jsonPrimitive.long)
            }
        }

        // Parse geometry points
        val points = mutableListOf<LatLng>()
        for (pt in geometry) {
            val obj = pt.jsonObject
            val lat = obj["lat"]?.jsonPrimitive?.double ?: continue
            val lon = obj["lon"]?.jsonPrimitive?.double ?: continue
            points.add(LatLng(lat, lon))
        }

        if (points.size < 2) return null

        val isExplicitIsland = tags["coastline"] == "island"

        return RawSegment(
            osmWayId = id,
            points = points,
            nodeIds = nodeIds,
            tags = tags,
            isExplicitIsland = isExplicitIsland
        )
    }

    // ── Assembly with node IDs ─────────────────────────────────────────────

    /**
     * Assembles raw OSM segments into continuous polylines.
     *
     * Uses OSM node IDs for matching when available (more reliable than
     * distance-based matching). Falls back to distance-based matching
     * for segments without node IDs.
     */
    private fun assembleWithNodeIds(segments: List<RawSegment>): List<RawSegment> {
        if (segments.isEmpty()) return emptyList()

        // Build node-ID-to-osmWayId maps (NOT index-based — immune to list mutations).
        // Key = OSM node ID, Value = list of osmWayIds sharing that node as head/tail.
        val headNodeMap = mutableMapOf<Long, MutableList<Long>>()
        val tailNodeMap = mutableMapOf<Long, MutableList<Long>>()
        val remaining = segments.toMutableList()

        for (seg in remaining) {
            if (seg.nodeIds.isNotEmpty()) {
                headNodeMap.getOrPut(seg.nodeIds.first()) { mutableListOf() }.add(seg.osmWayId)
                tailNodeMap.getOrPut(seg.nodeIds.last()) { mutableListOf() }.add(seg.osmWayId)
            }
        }

        val polylines = mutableListOf<RawSegment>()

        while (remaining.isNotEmpty()) {
            val chain = buildSingleChain(remaining, headNodeMap, tailNodeMap)
            if (chain != null) {
                polylines.add(chain)
            } else {
                break
            }
        }

        return polylines
    }

    /**
     * Builds one continuous polyline by matching endpoints via node IDs
     * (preferred) or distance (fallback).
     *
     * Node-ID matching uses osmWayId-based maps — no stale indices,
     * because maps store osmWayIds and [findAndRemoveByOsmId] searches
     * the mutable [remaining] list by value each time.
     */
    private fun buildSingleChain(
        remaining: MutableList<RawSegment>,
        headNodeMap: MutableMap<Long, MutableList<Long>>,
        tailNodeMap: MutableMap<Long, MutableList<Long>>
    ): RawSegment? {
        if (remaining.isEmpty()) return null

        val seed = remaining.removeAt(0)
        // Remove seed from maps so it won't be matched again
        removeOsmIdFromMaps(seed.osmWayId, headNodeMap, tailNodeMap)

        val chainPoints = seed.points.toMutableList()
        val chainNodeIds = seed.nodeIds.toMutableList()
        var chainOsmId = seed.osmWayId

        var changed = true
        while (remaining.isNotEmpty() && changed) {
            changed = false

            // ── Try to append: chain.tail → segment.head (node-ID match) ──
            val tailNode = if (chainNodeIds.isNotEmpty()) chainNodeIds.last() else null
            if (tailNode != null) {
                val candidates = headNodeMap[tailNode]?.toList() ?: emptyList()
                for (osmId in candidates) {
                    val match = findAndRemoveByOsmId(remaining, osmId) ?: continue
                    removeOsmIdFromMaps(match.osmWayId, headNodeMap, tailNodeMap)
                    chainPoints.addAll(match.points.drop(1))
                    chainNodeIds.addAll(match.nodeIds.drop(1))
                    chainOsmId = mergeOsmIds(chainOsmId, match.osmWayId)
                    changed = true
                    break
                }
                if (changed) continue
            }

            // ── Fallback: distance-based append ────────────────────────────
            val tail = chainPoints.last()
            var bestIdx: Int? = null
            var bestDist = MATCH_THRESHOLD_M
            for (i in remaining.indices) {
                val d = SpatialOperations.haversine(tail, remaining[i].points.first())
                if (d < bestDist) {
                    bestDist = d
                    bestIdx = i
                }
            }
            if (bestIdx != null) {
                val seg = remaining.removeAt(bestIdx)
                removeOsmIdFromMaps(seg.osmWayId, headNodeMap, tailNodeMap)
                chainPoints.addAll(seg.points.drop(1))
                chainNodeIds.addAll(seg.nodeIds.drop(1))
                chainOsmId = mergeOsmIds(chainOsmId, seg.osmWayId)
                changed = true
                continue
            }

            // ── Try to prepend: segment.tail → chain.head (node-ID match) ──
            val headNode = if (chainNodeIds.isNotEmpty()) chainNodeIds.first() else null
            if (headNode != null) {
                val candidates = tailNodeMap[headNode]?.toList() ?: emptyList()
                for (osmId in candidates) {
                    val match = findAndRemoveByOsmId(remaining, osmId) ?: continue
                    removeOsmIdFromMaps(match.osmWayId, headNodeMap, tailNodeMap)
                    chainPoints.addAll(0, match.points.dropLast(1))
                    chainNodeIds.addAll(0, match.nodeIds.dropLast(1))
                    chainOsmId = mergeOsmIds(chainOsmId, match.osmWayId)
                    changed = true
                    break
                }
                if (changed) continue
            }

            // ── Fallback: distance-based prepend ───────────────────────────
            val head = chainPoints.first()
            bestIdx = null
            bestDist = MATCH_THRESHOLD_M
            for (i in remaining.indices) {
                val d = SpatialOperations.haversine(remaining[i].points.last(), head)
                if (d < bestDist) {
                    bestDist = d
                    bestIdx = i
                }
            }
            if (bestIdx != null) {
                val seg = remaining.removeAt(bestIdx)
                removeOsmIdFromMaps(seg.osmWayId, headNodeMap, tailNodeMap)
                chainPoints.addAll(0, seg.points.dropLast(1))
                chainNodeIds.addAll(0, seg.nodeIds.dropLast(1))
                chainOsmId = mergeOsmIds(chainOsmId, seg.osmWayId)
                changed = true
            }
        }

        return RawSegment(
            osmWayId = chainOsmId,
            points = chainPoints,
            nodeIds = chainNodeIds,
            tags = emptyMap(),
            isExplicitIsland = false
        )
    }

    // ── Assembly helpers ─────────────────────────────────────────────────

    private fun mergeOsmIds(a: Long, b: Long): Long {
        if (a == 0L) return b
        if (b == 0L || a == b) return a
        return a
    }

    /** Finds and removes a segment by its [osmWayId]. Returns null if not found. */
    private fun findAndRemoveByOsmId(
        list: MutableList<RawSegment>,
        osmWayId: Long
    ): RawSegment? {
        val idx = list.indexOfFirst { it.osmWayId == osmWayId }
        return if (idx >= 0) list.removeAt(idx) else null
    }

    /** Removes all entries of [osmWayId] from both node→osmWayId maps. */
    private fun removeOsmIdFromMaps(
        osmWayId: Long,
        headNodeMap: MutableMap<Long, MutableList<Long>>,
        tailNodeMap: MutableMap<Long, MutableList<Long>>
    ) {
        for (map in listOf(headNodeMap, tailNodeMap)) {
            val iter = map.entries.iterator()
            while (iter.hasNext()) {
                val (_, osmIds) = iter.next()
                osmIds.remove(osmWayId)
                if (osmIds.isEmpty()) iter.remove()
            }
        }
    }

    // ── Overpass fetch (coroutines, true race) ──────────────────────────────

    /**
     * Lance N requêtes Overpass en parallèle et retourne la première réponse
     * valide. Tous les autres endpoints en cours sont immédiatement annulés
     * dès qu'un succès est obtenu (race-to-first-success via [select]).
     */
    private suspend fun fetchOverpass(
        onProgress: (Int) -> Unit
    ): List<JsonObject> = coroutineScope {
        val query = buildString {
            append("[out:json]")
            append("[bbox:$BBOX_LAT_MIN,$BBOX_LON_MIN,$BBOX_LAT_MAX,$BBOX_LON_MAX];")
            append("way[natural=coastline];out body geom;")  // ← body includes tags & nodes
        }
        val requestBody = "data=$query".toByteArray(Charsets.UTF_8)
            .toRequestBody(FORM_URLENCODED)

        onProgress(10)

        // ── 1. Lancer tous les endpoints en parallèle ─────────────────────────
        // Chaque async est enveloppé dans runCatching pour que le Deferred
        // ne lance jamais d'exception — select.onAwait peut ainsi inspecter
        // le Result sans bloc try-catch.
        val deferreds = OVERPASS_ENDPOINTS.map { endpoint ->
            async {
                runCatching {
                    fetchFromEndpoint(endpoint, requestBody)
                }
            }
        }

        // ── 2. Race-to-first-success via select ──────────────────────────────
        // select retourne le premier deferred qui se complète (succès ou échec).
        //   - Succès → on cancel() les autres et on retourne immédiatement.
        //   - Échec  → on enregistre l'exception et on attend le suivant.
        var lastException: Throwable? = null
        val remaining = deferreds.toMutableList()

        while (remaining.isNotEmpty()) {
            val (index, result) = select<Pair<Int, Result<List<JsonObject>>>> {
                remaining.forEachIndexed { i, deferred ->
                    deferred.onAwait { value -> i to value }
                }
            }

            if (result.isSuccess) {
                // 🏆 Premier succès ! Annuler tous les autres appels en vol.
                remaining.forEach { it.cancel() }
                onProgress(100)
                return@coroutineScope result.getOrThrow()
            }

            // ❌ Cet endpoint a échoué — essayer le suivant.
            lastException = result.exceptionOrNull()
            remaining.removeAt(index)
        }

        // 💀 Tous les endpoints ont échoué.
        throw lastException
            ?: IllegalStateException("Tous les endpoints Overpass ont échoué.")
    }

    /**
     * Exécute une requête Overpass POST sur un endpoint et extrait les
     * ways[natural=coastline] avec géométrie.
     *
     * @throws IllegalStateException si la réponse est vide, non-OK,
     *         ou ne contient aucun way valide.
     */
    private fun fetchFromEndpoint(
        endpoint: String,
        requestBody: okhttp3.RequestBody
    ): List<JsonObject> {
        val request = Request.Builder()
            .url(endpoint)
            .post(requestBody)
            .header("User-Agent", "MaroII-Coastline-Fetcher/1.0")
            .header("Accept", "application/json")
            .build()

        val response = httpClient.newCall(request).execute()
        val body = response.body?.string()
            ?: throw IllegalStateException("Réponse vide de $endpoint")

        if (!response.isSuccessful) {
            throw IllegalStateException("HTTP ${response.code} depuis $endpoint")
        }

        val root = json.parseToJsonElement(body).jsonObject
        val elements = root["elements"]?.jsonArray ?: JsonArray(emptyList())

        val ways = mutableListOf<JsonObject>()
        for (el in elements) {
            val obj = el.jsonObject
            if (obj["type"]?.jsonPrimitive?.content == "way" && obj.containsKey("geometry")) {
                ways.add(obj)
            }
        }

        if (ways.isEmpty()) {
            throw IllegalStateException("Aucun way[natural=coastline] trouvé.")
        }

        return ways
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private fun computeTotalLength(
        mainlandPoints: List<CoastlinePoint>,
        islandPoints: List<List<CoastlinePoint>>
    ): Double {
        var total = 0.0
        for (list in listOf(mainlandPoints) + islandPoints) {
            for (i in 0 until list.size - 1) {
                val dx = list[i].edgeDxM.toDouble()
                val dy = list[i].edgeDyM.toDouble()
                total += kotlin.math.sqrt(dx * dx + dy * dy)
            }
        }
        return total
    }

    /**
     * Compute bounding box from raw LatLng polylines (before projection to meters).
     */
    private fun computeBoundingBoxFromLatLng(
        polylines: List<List<LatLng>>
    ): BoundingBox {
        var latMin = Double.MAX_VALUE
        var latMax = -Double.MAX_VALUE
        var lonMin = Double.MAX_VALUE
        var lonMax = -Double.MAX_VALUE

        for (poly in polylines) {
            for (pt in poly) {
                if (pt.latitude < latMin) latMin = pt.latitude
                if (pt.latitude > latMax) latMax = pt.latitude
                if (pt.longitude < lonMin) lonMin = pt.longitude
                if (pt.longitude > lonMax) lonMax = pt.longitude
            }
        }

        return BoundingBox(
            latSouth = latMin,
            latNorth = latMax,
            lonWest = lonMin,
            lonEast = lonMax
        )
    }

    private fun computeBoundingBox(
        mainlandPoints: List<CoastlinePoint>,
        islandPoints: List<List<CoastlinePoint>>
    ): BoundingBox {
        var latMin = Double.MAX_VALUE
        var latMax = -Double.MAX_VALUE
        var lonMin = Double.MAX_VALUE
        var lonMax = -Double.MAX_VALUE

        for (list in listOf(mainlandPoints) + islandPoints) {
            for (pt in list) {
                if (pt.lat < latMin) latMin = pt.lat.toDouble()
                if (pt.lat > latMax) latMax = pt.lat.toDouble()
                if (pt.lon < lonMin) lonMin = pt.lon.toDouble()
                if (pt.lon > lonMax) lonMax = pt.lon.toDouble()
            }
        }

        return BoundingBox(
            latSouth = latMin,
            latNorth = latMax,
            lonWest = lonMin,
            lonEast = lonMax
        )
    }

    /**
     * Determines coastline orientation by checking which side of the mainland
     * the islands fall on. Islands are surrounded by water, so the side with
     * more island centers is the water side.
     *
     * If islands are mostly on the LEFT, water is on the LEFT → reverse so
     * water ends up on the RIGHT.
     */
    private fun orientByIslandPositions(
        mainland: RawSegment,
        islands: List<RawSegment>
    ): RawSegment {
        val mainPoints = mainland.points
        if (mainPoints.size < 2 || islands.isEmpty()) return mainland

        var rightCount = 0
        var leftCount = 0

        for (island in islands) {
            if (island.points.isEmpty()) continue

            val centerLat = island.points.sumOf { it.latitude } / island.points.size
            val centerLon = island.points.sumOf { it.longitude } / island.points.size
            val center = LatLng(centerLat, centerLon)

            var minDist = Double.MAX_VALUE
            var bestCross = 0.0
            for (i in 0 until mainPoints.size - 1) {
                val d = SpatialOperations.pointToSegmentDistance(
                    center, mainPoints[i], mainPoints[i + 1]
                )
                if (d < minDist) {
                    minDist = d
                    bestCross = SpatialOperations.crossProductZ(
                        mainPoints[i], mainPoints[i + 1], center
                    )
                }
            }

            if (bestCross < 0) rightCount++ else leftCount++
        }

        return if (leftCount > rightCount) {
            mainland.copy(points = mainland.points.reversed())
        } else {
            mainland
        }
    }
}
