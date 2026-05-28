package ykws.android.maro.data.coastline

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import ykws.android.maro.data.model.CoastlineMetadata
import ykws.android.maro.data.model.CoastlineSegment
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.spatial.SpatialOperations
import java.util.concurrent.TimeUnit

/**
 * Port Android de fetch_coastline.py — pipeline OSM pour la côte Nice–Fréjus.
 *
 * Pipeline:
 *   1. Fetch OSM depuis Overpass API (3 endpoints redondants, race)
 *   2. Assemblage des segments en polylines continues
 *   3. Filtrage des îles (≤ 6 NM de la côte principale)
 *   4. Clipping à la zone réglementaire (6,70°E – 7,31°E)
 *   5. Simplification Douglas-Peucker (ε = 3 m)
 *   6. Validation de l'orientation (eau à droite)
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
        // Bounding box élargie pour récupérer les extrémités proprement
        const val BBOX_LAT_MIN = 43.30
        const val BBOX_LON_MIN = 6.58
        const val BBOX_LAT_MAX = 43.80
        const val BBOX_LON_MAX = 7.38

        // Zone réglementaire Nice–Fréjus
        const val LON_WEST = 6.70
        const val LON_EAST = 7.31

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
     * Exécute le pipeline complet et retourne les segments de côte traités.
     *
     * @param onProgress Callback de progression 0‑100. Appelé depuis le thread
     *                   du coroutine scope parent (généralement Dispatchers.Main).
     */
    suspend fun generate(
        onProgress: (Int) -> Unit = {}
    ): CoastlineGenerationResult = withContext(Dispatchers.IO) {
        onProgress(0)

        // ── 1. Fetch (0 → 25) ───────────────────────────────────────────────
        val ways = fetchOverpass { pct ->
            onProgress((pct * 25 / 100).coerceAtMost(25))
        }
        onProgress(25)

        // ── 2. Assemblage (25 → 55) ─────────────────────────────────────────
        val segments = parseWaysToSegments(ways)
        val rawPolylines = SpatialOperations.assemblePolylines(segments)

        if (rawPolylines.isEmpty()) {
            throw IllegalStateException("Aucune polyline de côte trouvée dans la réponse Overpass.")
        }
        onProgress(55)

        // ── 3. Îles (55 → 65) ───────────────────────────────────────────────
        val islandMaxDistM = islandMaxDistanceNm * 1852.0 // NM → meters
        val mainCoastline = rawPolylines.maxByOrNull { it.size }
            ?: throw IllegalStateException("Côte principale introuvable.")
        val islands = rawPolylines.filter { it !== mainCoastline }
        val finalPolylines = mutableListOf(mainCoastline)

        for (island in islands) {
            if (SpatialOperations.polylinesMinDistance(mainCoastline, island) <= islandMaxDistM) {
                finalPolylines.add(island)
            }
        }
        onProgress(65)

        // ── 4. Clip + Simplify (65 → 95) ────────────────────────────────────
        val processed = mutableListOf<List<LatLng>>()
        for (polyline in finalPolylines) {
            val clipped = polyline.filter { (_, lon) -> lon in LON_WEST..LON_EAST }
            if (clipped.isNotEmpty()) {
                val simplified = SpatialOperations.douglasPeucker(clipped, simplifyEpsilonM)
                if (simplified.size >= 2) {
                    // 4b. Valider l'orientation (eau à droite)
                    val oriented = SpatialOperations.ensureWaterOnRight(simplified)
                    processed.add(oriented)
                }
            }
        }

        if (processed.isEmpty()) {
            throw IllegalStateException("Aucune polyline après clipping + simplification.")
        }
        onProgress(95)

        // ── 5. Métadonnées (95 → 100) ───────────────────────────────────────
        val totalPoints = processed.sumOf { it.size }
        val totalLength = processed.sumOf { poly ->
            (0 until poly.size - 1).sumOf { i ->
                SpatialOperations.haversine(poly[i], poly[i + 1])
            }
        }
        val meanSpacing = if (totalPoints > processed.size) {
            totalLength / (totalPoints - processed.size)
        } else 0.0

        val segments_by_id = processed.mapIndexed { index, points ->
            CoastlineSegment(id = "coast-$index", points = points)
        }

        val metadata = CoastlineMetadata(
            source = "OpenStreetMap contributors, ODbL (généré sur appareil)",
            pointCount = totalPoints,
            meanSpacingM = meanSpacing,
            epsilonM = simplifyEpsilonM
        )

        onProgress(100)
        CoastlineGenerationResult(segments_by_id, metadata)
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
            append("way[natural=coastline];out geom;")
        }
        val requestBody = "data=$query".toByteArray(Charsets.UTF_8)
            .toRequestBody(FORM_URLENCODED)

        onProgress(10)

        // Lancer les requêtes en parallèle
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
                    // Laisse le mécanisme de race décider : si un autre
                    // async réussit, cette exception est ignorée.
                    throw e
                }
            }
        }

        // Attendre la première réussie ou toutes les exceptions
        val results = deferredList.map { deferred ->
            runCatching { deferred.await() }
        }

        val success = results.firstOrNull { it.isSuccess }
            ?: throw results.firstNotNullOfOrNull { it.exceptionOrNull() }
                ?: IllegalStateException("Tous les endpoints Overpass ont échoué.")

        onProgress(100)
        success.getOrThrow()
    }

    // ── Parsing des ways en segments de coordonnées ─────────────────────────

    private fun parseWaysToSegments(ways: List<JsonObject>): List<List<LatLng>> {
        val segments = mutableListOf<List<LatLng>>()
        for (way in ways) {
            val geometry = way["geometry"]?.jsonArray ?: continue
            val coords = mutableListOf<LatLng>()
            for (pt in geometry) {
                val obj = pt.jsonObject
                val lat = obj["lat"]?.jsonPrimitive?.double ?: continue
                val lon = obj["lon"]?.jsonPrimitive?.double ?: continue
                coords.add(LatLng(lat, lon))
            }
            if (coords.size >= 2) segments.add(coords)
        }
        return segments
    }
}

/**
 * Résultat du pipeline de génération de côte.
 */
data class CoastlineGenerationResult(
    val segments: List<CoastlineSegment>,
    val metadata: CoastlineMetadata
)
