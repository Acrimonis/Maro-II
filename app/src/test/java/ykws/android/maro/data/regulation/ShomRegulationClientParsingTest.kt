package ykws.android.maro.data.regulation

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.BoundingBox
import ykws.android.maro.data.model.LatLng
import java.util.concurrent.TimeUnit

/**
 * Unit tests for [ShomRegulationClient] — validates GeoJSON parsing,
 * error resilience, and property mapping using a mock HTTP client.
 *
 * Note: The mock returns the same response body for ALL requests.
 * Since [ShomRegulationClient] iterates over [ShomRegulationClient.CANDIDATE_TYPENAMES]
 * (4 entries by default), a single-feature mock response yields 4 zones
 * (one per typeName). All tests account for this multiplier.
 */
class ShomRegulationClientParsingTest {

    private val testBbox = BoundingBox(
        lonWest = 6.70, latSouth = 43.35,
        lonEast = 7.31, latNorth = 43.73
    )

    /** Number of candidate typeNames the client iterates over. */
    private val typeNameCount = ShomRegulationClient.CANDIDATE_TYPENAMES.size

    /** Create a mock [OkHttpClient] that returns the given [body] for any request. */
    private fun mockClient(body: String): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(body.toResponseBody("application/json".toMediaTypeOrNull()))
                .build()
        }
        .connectTimeout(1, TimeUnit.SECONDS)
        .readTimeout(1, TimeUnit.SECONDS)
        .build()

    // ── FeatureCollection parsing ─────────────────────────────────────────────

    @Test
    fun `parse valid FeatureCollection with speed zone`() = runBlocking {
        val json = """
        {
          "type": "FeatureCollection",
          "features": [{
            "type": "Feature",
            "geometry": {"type": "Polygon", "coordinates": [[[7.1,43.5],[7.2,43.6],[7.3,43.5],[7.1,43.5]]]},
            "properties": {
              "type_reglementation": "vitesse",
              "vitesse_max": 10.0,
              "nom": "Test Speed Zone",
              "id_reglementation": "SPD-001",
              "description": "A test speed zone"
            }
          }]
        }
        """.trimIndent()

        val client = ShomRegulationClient(httpClient = mockClient(json))
        val zones = client.fetchZones(testBbox)

        assertEquals(typeNameCount, zones.size)
        val zone = zones.first()
        assertEquals(RegulatedZoneType.SPEED_LIMIT, zone.zoneType)
        assertEquals(10.0, zone.speedLimitKn!!, 0.001)
        assertEquals("Test Speed Zone", zone.name)
        assertEquals("SHOM", zone.source)
        assertEquals("SPD-001", zone.sourceRef)
        assertEquals("A test speed zone", zone.description)
        assertTrue(zone.outerRing.size >= 3)
    }

    @Test
    fun `parse anchoring prohibition zone`() = runBlocking {
        val json = """
        {
          "type": "FeatureCollection",
          "features": [{
            "type": "Feature",
            "geometry": {"type": "Polygon", "coordinates": [[[7.1,43.5],[7.2,43.6],[7.3,43.5],[7.1,43.5]]]},
            "properties": {
              "type_reglementation": "mouillage",
              "nom": "Zone de mouillage interdite",
              "id_reglementation": "ANC-001"
            }
          }]
        }
        """.trimIndent()

        val client = ShomRegulationClient(httpClient = mockClient(json))
        val zones = client.fetchZones(testBbox)

        assertEquals(typeNameCount, zones.size)
        assertEquals(RegulatedZoneType.ANCHORING_PROHIBITED, zones.first().zoneType)
        assertEquals("Zone de mouillage interdite", zones.first().name)
    }

    @Test
    fun `parse access prohibition zone`() = runBlocking {
        val json = """
        {
          "type": "FeatureCollection",
          "features": [{
            "type": "Feature",
            "geometry": {"type": "Polygon", "coordinates": [[[7.1,43.5],[7.2,43.6],[7.3,43.5],[7.1,43.5]]]},
            "properties": {
              "type_reglementation": "acces_interdit",
              "nom": "Accès interdit"
            }
          }]
        }
        """.trimIndent()

        val client = ShomRegulationClient(httpClient = mockClient(json))
        val zones = client.fetchZones(testBbox)

        assertEquals(typeNameCount, zones.size)
        assertEquals(RegulatedZoneType.ACCESS_PROHIBITED, zones.first().zoneType)
    }

    @Test
    fun `parse environmental protection zone`() = runBlocking {
        val json = """
        {
          "type": "FeatureCollection",
          "features": [{
            "type": "Feature",
            "geometry": {"type": "Polygon", "coordinates": [[[7.1,43.5],[7.2,43.6],[7.3,43.5],[7.1,43.5]]]},
            "properties": {
              "type_reglementation": "protection",
              "nom": "Zone de protection"
            }
          }]
        }
        """.trimIndent()

        val client = ShomRegulationClient(httpClient = mockClient(json))
        val zones = client.fetchZones(testBbox)

        assertEquals(typeNameCount, zones.size)
        assertEquals(RegulatedZoneType.ENVIRONMENTAL, zones.first().zoneType)
    }

    @Test
    fun `unknown type_reglementation maps to OTHER`() = runBlocking {
        val json = """
        {
          "type": "FeatureCollection",
          "features": [{
            "type": "Feature",
            "geometry": {"type": "Polygon", "coordinates": [[[7.1,43.5],[7.2,43.6],[7.3,43.5],[7.1,43.5]]]},
            "properties": {
              "type_reglementation": "unknown_type",
              "nom": "Unrecognized"
            }
          }]
        }
        """.trimIndent()

        val client = ShomRegulationClient(httpClient = mockClient(json))
        val zones = client.fetchZones(testBbox)

        assertEquals(typeNameCount, zones.size)
        assertEquals(RegulatedZoneType.OTHER, zones.first().zoneType)
    }

    @Test
    fun `missing vitesse_max yields null speed`() = runBlocking {
        val json = """
        {
          "type": "FeatureCollection",
          "features": [{
            "type": "Feature",
            "geometry": {"type": "Polygon", "coordinates": [[[7.1,43.5],[7.2,43.6],[7.3,43.5],[7.1,43.5]]]},
            "properties": {
              "type_reglementation": "vitesse",
              "nom": "No speed given"
            }
          }]
        }
        """.trimIndent()

        val client = ShomRegulationClient(httpClient = mockClient(json))
        val zones = client.fetchZones(testBbox)

        assertEquals(typeNameCount, zones.size)
        // speedLimitKn defaults to null when property is absent
    }

    @Test
    fun `empty FeatureCollection returns empty list`() = runBlocking {
        val json = """
        {"type": "FeatureCollection", "features": []}
        """.trimIndent()

        val client = ShomRegulationClient(httpClient = mockClient(json))
        val zones = client.fetchZones(testBbox)
        assertTrue(zones.isEmpty())
    }

    @Test
    fun `malformed JSON returns empty list (best-effort)`() = runBlocking {
        val json = "not valid json"

        val client = ShomRegulationClient(httpClient = mockClient(json))
        val zones = client.fetchZones(testBbox)
        assertTrue(zones.isEmpty())
    }

    @Test
    fun `non-200 response returns empty list (best-effort)`() {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(500)
                    .message("Internal Server Error")
                    .body("".toResponseBody(null))
                    .build()
            }
            .build()

        val shomClient = ShomRegulationClient(httpClient = client)
        val zones = runBlocking { shomClient.fetchZones(testBbox) }
        assertTrue(zones.isEmpty())
    }

    @Test
    fun `MultiPolygon geometry is parsed`() = runBlocking {
        val json = """
        {
          "type": "FeatureCollection",
          "features": [{
            "type": "Feature",
            "geometry": {
              "type": "MultiPolygon",
              "coordinates": [[[[7.1,43.5],[7.2,43.6],[7.3,43.5],[7.1,43.5]]]]
            },
            "properties": {
              "type_reglementation": "vitesse",
              "vitesse_max": 5.0,
              "nom": "MultiPolygon Zone"
            }
          }]
        }
        """.trimIndent()

        val client = ShomRegulationClient(httpClient = mockClient(json))
        val zones = client.fetchZones(testBbox)

        assertEquals(typeNameCount, zones.size)
        assertEquals(5.0, zones.first().speedLimitKn!!, 0.001)
    }

    @Test
    fun `Polygon with holes is parsed`() = runBlocking {
        val json = """
        {
          "type": "FeatureCollection",
          "features": [{
            "type": "Feature",
            "geometry": {
              "type": "Polygon",
              "coordinates": [
                [[7.1,43.5],[7.2,43.6],[7.3,43.5],[7.1,43.5]],
                [[7.15,43.53],[7.18,43.55],[7.2,43.52],[7.15,43.53]]
              ]
            },
            "properties": {
              "type_reglementation": "vitesse",
              "vitesse_max": 8.0,
              "nom": "Zone with hole"
            }
          }]
        }
        """.trimIndent()

        val client = ShomRegulationClient(httpClient = mockClient(json))
        val zones = client.fetchZones(testBbox)

        assertEquals(typeNameCount, zones.size)
        val zone = zones.first()
        assertEquals(8.0, zone.speedLimitKn!!, 0.001)
        assertEquals("Zone with hole", zone.name)
        assertTrue("Expected holes to be non-empty", zone.holes.isNotEmpty())
    }

    @Test
    fun `2 features in collection multiplied by typeNames`() = runBlocking {
        val json = """
        {
          "type": "FeatureCollection",
          "features": [{
            "type": "Feature",
            "geometry": {"type": "Polygon", "coordinates": [[[7.1,43.5],[7.2,43.6],[7.3,43.5],[7.1,43.5]]]},
            "properties": {"type_reglementation": "vitesse", "nom": "A"}
          },{
            "type": "Feature",
            "geometry": {"type": "Polygon", "coordinates": [[[7.2,43.5],[7.3,43.6],[7.4,43.5],[7.2,43.5]]]},
            "properties": {"type_reglementation": "mouillage", "nom": "B"}
          }]
        }
        """.trimIndent()

        val client = ShomRegulationClient(httpClient = mockClient(json))
        val zones = client.fetchZones(testBbox)

        assertEquals(typeNameCount * 2, zones.size) // 4 typeNames × 2 features
        val names = zones.map { it.name }.distinct().sorted()
        assertEquals(listOf("A", "B"), names)
    }
}
