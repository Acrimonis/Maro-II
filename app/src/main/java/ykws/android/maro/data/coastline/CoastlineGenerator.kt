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
import ykws.android.maro.data.model.CoastlineMetadata
import ykws.android.maro.data.model.CoastlineSegment
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.spatial.SpatialOperations
import java.util.concurrent.TimeUnit

/**
 * Port Android de fetch_coastline.py — pipeline OSM pour la côte Nice–Fréjus.
 *
 * Pipeline:
 *   1. Fetch OSM depuis Overpass API (N endpoints redondants, race)
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
     * Exécute le pipeline complet et retourne les segments de côte traités.
     *
     * @param onProgress Callback émettant (phase, progression 0‑100). Appelé
     *                   depuis le thread du coroutine scope parent.
     */
    suspend fun generate(
        onProgress: (phase: String, progress: Int) -> Unit = { _, _ -> }
    ): CoastlineGenerationResult = withContext(Dispatchers.IO) {
        onProgress("Démarrage", 0)

        // ── 1. Fetch (0 → 25) ───────────────────────────────────────────────
        val ways = fetchOverpass { pct ->
            onProgress("Téléchargement OSM", (pct * 25 / 100).coerceAtMost(25))
        }
        onProgress("Assemblage", 25)

        // ── 2. Assemblage (25 → 55) ─────────────────────────────────────────
        val segments = parseWaysToSegments(ways)
        val rawPolylines = SpatialOperations.assemblePolylines(segments)

        if (rawPolylines.isEmpty()) {
            throw IllegalStateException("Aucune polyline de côte trouvée dans la réponse Overpass.")
        }
        onProgress("Filtrage îles", 55)

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
        onProgress("Découpage & simplification", 65)

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
        onProgress("Métadonnées", 95)

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

        onProgress("Terminé", 100)
        CoastlineGenerationResult(segments_by_id, metadata)
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
            append("way[natural=coastline];out geom;")
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
