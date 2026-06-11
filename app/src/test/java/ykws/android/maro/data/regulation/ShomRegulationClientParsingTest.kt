package ykws.android.maro.data.regulation

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.BoundingBox
import ykws.android.maro.data.model.LatLng
import java.util.concurrent.TimeUnit

/**
 * Unit tests for [ShomRegulationClient] — validates GeoJSON parsing, vessel size
 * extraction, and error resilience using a mock HTTP client.
 */
class ShomRegulationClientParsingTest {

    private val testBbox = BoundingBox(
        lonWest = 6.70, latSouth = 43.35,
        lonEast = 7.31, latNorth = 43.73
    )

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

        assertEquals(1, zones.size)
        val zone = zones.single()
        assertEquals(RegulatedZoneType.SPEED_LIMIT, zone.zoneType)
        assertEquals(10.0, zone.speedLimitKn!!, 0.001)
        assertEquals("Test Speed Zone", zone.name)
        assertEquals("SHOM", zone.source)
        assertEquals("SPD-001", zone.sourceRef)
        assertEquals("A test speed zone", zone.description)
        assertTrue(zone.outerRing.size >= 3)
    }

    @Test
    fun `parse vessel size restriction from longueur_hors_tout`() = runBlocking {
        val json = """
        {
          "type": "FeatureCollection",
          "features": [{
            "type": "Feature",
            "geometry": {"type": "Polygon", "coordinates": [[[7.1,43.5],[7.2,43.6],[7.3,43.5],[7.1,43.5]]]},
            "properties": {
              "type_reglementation": "vitesse",
              "longueur_hors_tout_mini": 20.0,
              "longueur_hors_tout_maxi": 50.0
            }
          }]
        }
        """.trimIndent()

        val client = ShomRegulationClient(httpClient = mockClient(json))
        val zones = client.fetchZones(testBbox)

        assertEquals(1, zones.size)
        val vsr = zones.single().vesselSizeRestriction
        assertNotNull(vsr)
        assertEquals(20.0, vsr!!.minLengthM, 0.001)
        assertEquals(50.0, vsr.maxLengthM, 0.001)
    }

    @Test
    fun `fallback to longueur_mini when longueur_hors_tout_mini absent`() = runBlocking {
        val json = """
        {
          "type": "FeatureCollection",
          "features": [{
            "type": "Feature",
            "geometry": {"type": "Polygon", "coordinates": [[[7.1,43.5],[7.2,43.6],[7.3,43.5],[7.1,43.5]]]},
            "properties": {
              "type_reglementation": "mouillage",
              "longueur_mini": 15.0
            }
          }]
        }
        """.trimIndent()

        val client = ShomRegulationClient(httpClient = mockClient(json))
        val zones = client.fetchZones(testBbox)

        assertEquals(1, zones.size)
        val vsr = zones.single().vesselSizeRestriction
        assertNotNull(vsr)
        assertEquals(15.0, vsr!!.minLengthM, 0.001)
        assertNull(vsr.maxLengthM)
    }

    @Test
    fun `no vessel size properties yields null restriction`() = runBlocking {
        val json = """
        {
          "type": "FeatureCollection",
          "features": [{
            "type": "Feature",
            "geometry": {"type": "Polygon", "coordinates": [[[7.1,43.5],[7.2,43.6],[7.3,43.5],[7.1,43.5]]]},
            "properties": {
              "type_reglementation": "protection"
            }
          }]
        }
        """.trimIndent()

        val client = ShomRegulationClient(httpClient = mockClient(json))
        val zones = client.fetchZones(testBbox)

        assertEquals(1, zones.size)
        assertNull(zones.single().vesselSizeRestriction)
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

        assertEquals(1, zones.size)
        assertEquals(5.0, zones.single().speedLimitKn, 0.001)
    }
}
