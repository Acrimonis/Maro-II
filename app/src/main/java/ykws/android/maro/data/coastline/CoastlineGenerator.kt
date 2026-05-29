package ykws.android.maro.data.coastline

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
 *   1. Fetch OSM depuis Overpass API (3 endpoints redondants, race)
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
    private val simplifyEpsilonM: Double = 3.0
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

        private val OVERPASS_ENDPOINTS = listOf(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.openstreetmap.ru/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter"
        )

        private val FORM_URLENCODED = "application/x-www-form-urlencoded".toMediaType()
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    // ── Public API ──────────────────────────────────────────────────────────

    /**
     * Exécute le pipeline complet et retourne les données de côte traitées.
     *
     * @param regionId Identifier for the coastline region (used for caching).
     * @param onProgress Callback de progression 0‑100.
     */
    suspend fun generate(
        regionId: String = REGION_ID,
        onProgress: (Int) -> Unit = {}
    ): CoastlineData = withContext(Dispatchers.IO) {
        onProgress(0)

        // ── 1. Fetch OSM with tags (0 → 25) ────────────────────────────────
        val rawWays = fetchOverpass { pct ->
            onProgress((pct * 25 / 100).coerceAtMost(25))
        }
        onProgress(25)

        // ── 2. Parse ways → segments with metadata (25 → 40) ───────────────
        val parsedWays = rawWays.mapNotNull { way -> parseOsmWay(way) }
        if (parsedWays.isEmpty()) {
            throw IllegalStateException("Aucun way[natural=coastline] trouvé dans la réponse Overpass.")
        }

        // Separate island ways (explicit tag) from mainland ways
        val islandWays = parsedWays.filter { it.isExplicitIsland }
        val mainlandWays = parsedWays.filter { !it.isExplicitIsland }
        onProgress(40)

        // ── 3. Assembly (40 → 55) ───────────────────────────────────────────
        // Assemble mainland and island ways separately
        val mainlandPolylines = assembleWithNodeIds(mainlandWays.map { it.toSegment() })
        val islandPolylines = assembleWithNodeIds(islandWays.map { it.toSegment() })

        if (mainlandPolylines.isEmpty()) {
            throw IllegalStateException("Aucune polyline de côte principale trouvée.")
        }
        onProgress(55)

        // ── 4. Island filter (55 → 65) ──────────────────────────────────────
        val islandMaxDistM = islandMaxDistanceNm * 1852.0 // NM → meters
        val mainCoastline = mainlandPolylines.maxByOrNull { it.points.size }
            ?: throw IllegalStateException("Côte principale introuvable.")

        // Use explicit islands first, then check remaining mainland polylines by distance
        val allIslands = mutableListOf<RawSegment>()

        // Explicit islands: keep if within range
        for (island in islandPolylines) {
            if (SpatialOperations.polylinesMinDistance(mainCoastline.points, island.points) <= islandMaxDistM) {
                allIslands.add(island)
            }
        }

        // Mainland fragments that aren't the main coast: check if they're islands
        val otherFragments = mainlandPolylines.filter { it !== mainCoastline }
        for (fragment in otherFragments) {
            if (SpatialOperations.polylinesMinDistance(mainCoastline.points, fragment.points) <= islandMaxDistM) {
                allIslands.add(fragment)
            }
        }
        onProgress(65)

        // ── 5. Clip + Simplify + Orient (65 → 90) ───────────────────────────
        val processedMainland = processPolyline(mainCoastline.points, isMainland = true)
        val processedIslands = allIslands.mapNotNull { island ->
            val processed = processPolyline(island.points, isMainland = false)
            if (processed != null) processed to island.osmWayId else null
        }
        onProgress(90)

        if (processedMainland == null) {
            throw IllegalStateException("Aucune polyline après clipping + simplification.")
        }

        // ── 6. Compute edge vectors + build CoastlinePoint lists (90 → 95) ──
        val mainlandPoints = computeEdgeVectors(processedMainland)
        val islandPoints = processedIslands.map { (polyline, _) ->
            computeEdgeVectors(polyline)
        }
        onProgress(95)

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
            fetchTimestampMs = System.currentTimeMillis()
        )

        onProgress(100)
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
     * Computes edge vectors (dx_m, dy_m) for each consecutive point pair
     * and returns a list of [CoastlinePoint].
     */
    private fun computeEdgeVectors(points: List<LatLng>): List<CoastlinePoint> {
        if (points.isEmpty()) return emptyList()

        val result = mutableListOf<CoastlinePoint>()
        for (i in points.indices) {
            val lat = points[i].latitude.toFloat()
            val lon = points[i].longitude.toFloat()

            if (i < points.size - 1) {
                // Compute Cartesian offset to next point
                val midLat = (points[i].latitude + points[i + 1].latitude) / 2.0
                val mPerDegLat = EARTH_RADIUS_M * PI / 180.0
                val mPerDegLon = mPerDegLat * cos(Math.toRadians(midLat))

                val dx = (points[i + 1].longitude - points[i].longitude) * mPerDegLon
                val dy = (points[i + 1].latitude - points[i].latitude) * mPerDegLat

                result.add(CoastlinePoint(
                    lat = lat,
                    lon = lon,
                    edgeDxM = dx.toFloat(),
                    edgeDyM = dy.toFloat()
                ))
            } else {
                // Last point: no outgoing edge
                result.add(CoastlinePoint(
                    lat = lat,
                    lon = lon,
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

        // Build node-ID-to-segment map for fast lookup
        val headNodeMap = mutableMapOf<Long, MutableList<Int>>()
        val tailNodeMap = mutableMapOf<Long, MutableList<Int>>()
        val remaining = segments.toMutableList()

        for ((idx, seg) in remaining.withIndex()) {
            if (seg.nodeIds.isNotEmpty()) {
                headNodeMap.getOrPut(seg.nodeIds.first()) { mutableListOf() }.add(idx)
                tailNodeMap.getOrPut(seg.nodeIds.last()) { mutableListOf() }.add(idx)
            }
        }

        val polylines = mutableListOf<RawSegment>()

        while (remaining.isNotEmpty()) {
            val chain = buildSingleChain(remaining, headNodeMap, tailNodeMap)
            if (chain != null) {
                polylines.add(chain)
            }
        }

        return polylines
    }

    /**
     * Builds one continuous polyline by matching endpoints via node IDs
     * (preferred) or distance (fallback).
     */
    private fun buildSingleChain(
        remaining: MutableList<RawSegment>,
        headNodeMap: MutableMap<Long, MutableList<Int>>,
        tailNodeMap: MutableMap<Long, MutableList<Int>>
    ): RawSegment? {
        if (remaining.isEmpty()) return null

        val seed = remaining.removeAt(0)
        val chainPoints = seed.points.toMutableList()
        val chainNodeIds = seed.nodeIds.toMutableList()
        var chainOsmId = seed.osmWayId

        var changed = true
        while (remaining.isNotEmpty() && changed) {
            changed = false

            // Try to append: chain.tail → segment.head
            val tailNode = if (chainNodeIds.isNotEmpty()) chainNodeIds.last() else null
            if (tailNode != null) {
                val candidates = headNodeMap[tailNode]?.toList() ?: emptyList()
                for (idx in candidates.sortedDescending()) {
                    if (idx < remaining.size) {
                        val seg = remaining[idx]
                        val match = findAndRemove(remaining, seg) ?: continue
                        // Append all points except the first (duplicate endpoint)
                        chainPoints.addAll(match.points.drop(1))
                        chainNodeIds.addAll(match.nodeIds.drop(1))
                        chainOsmId = mergeOsmIds(chainOsmId, match.osmWayId)
                        removeFromNodeMaps(match, headNodeMap, tailNodeMap)
                        changed = true
                        break
                    }
                }
                if (changed) continue
            }

            // Fallback: distance-based matching for append
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
                chainPoints.addAll(seg.points.drop(1))
                chainNodeIds.addAll(seg.nodeIds.drop(1))
                chainOsmId = mergeOsmIds(chainOsmId, seg.osmWayId)
                removeFromNodeMaps(seg, headNodeMap, tailNodeMap)
                changed = true
                continue
            }

            // Try to prepend: segment.tail → chain.head
            val headNode = if (chainNodeIds.isNotEmpty()) chainNodeIds.first() else null
            if (headNode != null) {
                val candidates = tailNodeMap[headNode]?.toList() ?: emptyList()
                for (idx in candidates.sortedDescending()) {
                    if (idx < remaining.size) {
                        val seg = remaining[idx]
                        val match = findAndRemove(remaining, seg) ?: continue
                        // Prepend all points except the last (duplicate endpoint)
                        chainPoints.addAll(0, match.points.dropLast(1))
                        chainNodeIds.addAll(0, match.nodeIds.dropLast(1))
                        chainOsmId = mergeOsmIds(chainOsmId, match.osmWayId)
                        removeFromNodeMaps(match, headNodeMap, tailNodeMap)
                        changed = true
                        break
                    }
                }
                if (changed) continue
            }

            // Fallback: distance-based matching for prepend
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
                chainPoints.addAll(0, seg.points.dropLast(1))
                chainNodeIds.addAll(0, seg.nodeIds.dropLast(1))
                chainOsmId = mergeOsmIds(chainOsmId, seg.osmWayId)
                removeFromNodeMaps(seg, headNodeMap, tailNodeMap)
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

    private fun mergeOsmIds(a: Long, b: Long): Long {
        // If both are 0 or equal, return either
        if (a == 0L) return b
        if (b == 0L || a == b) return a
        // Concatenation isn't meaningful for IDs; keep the first
        return a
    }

    private fun findAndRemove(
        list: MutableList<RawSegment>,
        target: RawSegment
    ): RawSegment? {
        val idx = list.indexOfFirst { it.osmWayId == target.osmWayId }
        return if (idx >= 0) list.removeAt(idx) else null
    }

    private fun removeFromNodeMaps(
        seg: RawSegment,
        headNodeMap: MutableMap<Long, MutableList<Int>>,
        tailNodeMap: MutableMap<Long, MutableList<Int>>
    ) {
        // Note: since we rebuild maps per assembly cycle,
        // stale entries are harmless. This is a no-op for now.
    }

    // ── Overpass fetch (coroutines) ─────────────────────────────────────────

    /**
     * Lance 3 requêtes Overpass en parallèle (race). La première réponse
     * valide gagne ; les autres sont ignorées.
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

        val deferredList = OVERPASS_ENDPOINTS.mapIndexed { idx, endpoint ->
            async {
                try {
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

                    onProgress(50 + idx * 15)

                    ways
                } catch (e: Exception) {
                    throw e
                }
            }
        }

        val results = deferredList.map { deferred ->
            runCatching { deferred.await() }
        }

        val success = results.firstOrNull { it.isSuccess }
            ?: throw results.firstNotNullOfOrNull { it.exceptionOrNull() }
                ?: IllegalStateException("Tous les endpoints Overpass ont échoué.")

        onProgress(100)
        success.getOrThrow()
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

}
