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
import ykws.android.maro.BuildConfig
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
 *
 * Isolated offshore point dangers (La Fourmigue, the cardinal-marked shoals, wrecks)
 * are pulled from **OSM seamarks** in the same Overpass call as the coastline and
 * ringed into the island set (see [SeamarkParser] + [buildHazardSegments]).
 */
class CoastlineGenerator(
    private val islandMaxDistanceNm: Double = 6.0,
    private val simplifyEpsilonM: Double = 8.0
) {
    // ── Constants ───────────────────────────────────────────────────────────

    companion object {
        private const val EARTH_RADIUS_M = 6_371_000.0

        // Zone réglementaire Nice–Fréjus — SINGLE SOURCE: the W/E coastline-point longitudes come from
        // gradle props (maro.region.lonWest/lonEast) via BuildConfig. The coastline clips E/W to these;
        // the depth zone derives as coast + 6 NM. No other box redefines the corridor.
        val LON_WEST = BuildConfig.REGION_LON_WEST
        val LON_EAST = BuildConfig.REGION_LON_EAST

        // OSM fetch window — N/S is a generous fetch span (NOT a coverage cap; coverage = real coast +
        // 6 NM); E/W = the corridor ± a small margin so clipped ways keep neighbours to assemble cleanly.
        private const val FETCH_LON_MARGIN_DEG = 0.12
        const val BBOX_LAT_MIN = 43.30
        const val BBOX_LAT_MAX = 43.80
        val BBOX_LON_MIN = LON_WEST - FETCH_LON_MARGIN_DEG
        val BBOX_LON_MAX = LON_EAST + FETCH_LON_MARGIN_DEG

        // Default region identifier for this generator
        const val REGION_ID = "nice-frejus"

        private const val MATCH_THRESHOLD_M = 25.0

        /** Two seamark dangers closer than this (m) are treated as one (proximity dedup). */
        private const val HAZARD_DEDUP_DIST_M = 25.0

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

        // ── 1. Fetch OSM (coastline ways + seamark danger nodes), 0 → 25 ──
        val overpass = fetchOverpass { pct ->
            onProgress("Téléchargement OSM", (pct * 25 / 100).coerceAtMost(25))
        }
        // 2 → 100: pure assembly/clip/simplify/rings — extracted for offline testing.
        buildFromElements(overpass.ways, overpass.seamarkNodes, regionId, onProgress)
    }

    /**
     * Pure (no-network) tail of [generate]: raw Overpass [rawWays] + [seamarkNodes] →
     * processed [CoastlineData]. Extracted so the pipeline can be diagnosed/tested
     * against a captured Overpass response without hitting the network.
     */
    internal fun buildFromElements(
        rawWays: List<JsonObject>,
        seamarkNodes: List<JsonObject>,
        regionId: String = REGION_ID,
        onProgress: (phase: String, progress: Int) -> Unit = { _, _ -> }
    ): CoastlineData {
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

        // ── 5. Clip + Simplify (65 → 90) ──────────────────────────────────
        // Note: orientation (water-on-right) is no longer needed — the new
        // ray-cast isOnWater algorithm does not depend on polyline direction.
        val processedMainland = processPolyline(mainCoastline.points, isMainland = true)
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

        val osmIslandSegments = processedIslands.zip(islandPoints) { (_, osmWayId), points ->
            CoastlineSegment(
                osmWayId = osmWayId,
                points = points,
                isMainland = false,
                isClosed = true  // OSM islands are closed rings
            )
        }

        // ── 7b. Offshore point dangers → micro-circle rings (OSM seamarks) ──
        // Isolated features (La Fourmigue, cardinal-marked shoals, wrecks) are not
        // in the coastline vectors; they arrived as seamark nodes in the same
        // Overpass response. Parse, clip, dedupe and union them into the islands.
        val hazardSegments = buildHazardSegments(seamarkNodes, projectionRefLat)
        val islandSegments = osmIslandSegments + hazardSegments
        val hazardPoints = hazardSegments.map { it.points }

        // Compute metadata (islands incl. hazard rings)
        val allIslandPoints = islandPoints + hazardPoints
        val totalPoints = mainlandPoints.size + allIslandPoints.sumOf { it.size }
        val totalLength = computeTotalLength(mainlandPoints, allIslandPoints)
        val meanSpacing = if (totalPoints > 1 + allIslandPoints.size) {
            totalLength / (totalPoints - 1 - allIslandPoints.size)
        } else 0.0

        val boundingBox = computeBoundingBox(mainlandPoints, allIslandPoints)

        val sourceLabel = "OpenStreetMap contributors, ODbL (généré sur appareil)" +
            if (hazardSegments.isNotEmpty()) " + OpenSeaMap seamarks" else ""

        val metadata = CoastlineMetadata(
            source = sourceLabel,
            pointCount = totalPoints,
            meanSpacingM = meanSpacing,
            totalLengthKm = totalLength / 1000.0,
            epsilonM = simplifyEpsilonM,
            fetchTimestampMs = System.currentTimeMillis(),
            projectionRefLat = projectionRefLat
        )

        onProgress("Terminé", 100)
        return CoastlineData(
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

    // ── Offshore point dangers (OSM seamarks) ──────────────────────────────

    /**
     * Builds island-equivalent micro-circle rings for the isolated point dangers
     * carried as OSM seamark nodes in the Overpass response ([SeamarkParser]).
     * Clips to the regulatory longitude band and drops near-duplicate marks. Rings
     * reuse the coastline's [refLat] so their projected XY / edge vectors stay
     * consistent with the rest of the dataset.
     *
     * Pure post-processing (no network) — the fetch already happened in [fetchOverpass].
     */
    private fun buildHazardSegments(
        seamarkNodes: List<JsonObject>,
        refLat: Double
    ): List<CoastlineSegment> {
        val hazards = SeamarkParser.parse(seamarkNodes)
            .filter { it.lon in LON_WEST..LON_EAST }
        return dedupeByProximity(hazards, HAZARD_DEDUP_DIST_M)
            .map { HazardRings.toSegment(it, refLat) }
    }

    /** Greedily drops any hazard within [minSepM] of one already kept (distinct dangers only). */
    private fun dedupeByProximity(hazards: List<PointHazard>, minSepM: Double): List<PointHazard> {
        val kept = ArrayList<PointHazard>(hazards.size)
        for (h in hazards) {
            val isDup = kept.any {
                SpatialOperations.haversine(LatLng(it.lat, it.lon), LatLng(h.lat, h.lon)) < minSepM
            }
            if (!isDup) kept.add(h)
        }
        return kept
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

        return simplified
    }

    // ── OSM Way parsing ────────────────────────────────────────────────────

    /** One Overpass response, split into coastline ways and isolated-danger seamark nodes. */
    private data class OverpassData(
        val ways: List<JsonObject>,
        val seamarkNodes: List<JsonObject>
    )

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
     *
     * ## Matching order (authoritative-first)
     *
     * Each iteration exhausts the authoritative node-ID match on **both** ends
     * — append (tail→head) then prepend (head→tail) — before falling back to
     * distance-based matching on either end. OSM coastline ways share exact node
     * IDs at their joins, so a node-ID match is always correct; the 25 m distance
     * fallback is a heuristic that can mis-join unrelated ways which merely happen
     * to be close. Trying distance before the node-ID prepend (the previous order)
     * could glue the wrong way onto the head even when an exact shared-node match
     * existed there. Keeping both exact passes ahead of both fuzzy passes removes
     * that failure mode.
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

            // ── 1. Authoritative append: chain.tail → segment.head (node-ID) ──
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

            // ── 2. Authoritative prepend: segment.tail → chain.head (node-ID) ──
            // Both exact (node-ID) passes run before either distance fallback so
            // a shared-node match at the head is never lost to a coincidental
            // 25 m neighbour. See the method KDoc for the rationale.
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

            // ── 3. Fallback append: distance-based (chain.tail → segment.head) ──
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

            // ── 4. Fallback prepend: distance-based (segment.tail → chain.head) ──
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
    ): OverpassData = coroutineScope {
        // One request fetches the coastline ways AND the isolated-danger seamark
        // nodes (La Fourmigue, cardinal-marked shoals, wrecks) in a single round-trip.
        val seamarkTypes = SeamarkParser.DANGER_TYPES.joinToString("|")
        val query = buildString {
            append("[out:json]")
            append("[bbox:$BBOX_LAT_MIN,$BBOX_LON_MIN,$BBOX_LAT_MAX,$BBOX_LON_MAX];")
            append("(way[natural=coastline];")
            append("node[\"seamark:type\"~\"^($seamarkTypes)\$\"];);")
            append("out body geom;")  // body → tags + node lat/lon; geom → way geometry
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
            val (index, result) = select<Pair<Int, Result<OverpassData>>> {
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
     * ways[natural=coastline] (avec géométrie) + les nœuds seamark (dangers isolés).
     *
     * @throws IllegalStateException si la réponse est vide, non-OK,
     *         ou ne contient aucun way valide.
     */
    private fun fetchFromEndpoint(
        endpoint: String,
        requestBody: okhttp3.RequestBody
    ): OverpassData {
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
        val seamarkNodes = mutableListOf<JsonObject>()
        for (el in elements) {
            val obj = el.jsonObject
            when (obj["type"]?.jsonPrimitive?.content) {
                "way" -> if (obj.containsKey("geometry")) ways.add(obj)
                "node" -> seamarkNodes.add(obj)  // pre-filtered to danger seamark types by the query
            }
        }

        if (ways.isEmpty()) {
            throw IllegalStateException("Aucun way[natural=coastline] trouvé.")
        }

        return OverpassData(ways, seamarkNodes)
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
}
