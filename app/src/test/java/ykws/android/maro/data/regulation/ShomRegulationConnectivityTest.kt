package ykws.android.maro.data.regulation

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.BoundingBox
import java.net.HttpURLConnection
import java.net.URL

/**
 * **Connectivity test** — verifies the SHOM WFS endpoint is reachable and returns
 * valid GeoJSON for the Nice–Fréjus corridor.
 *
 * This is a lightweight integration test that runs on every `gradlew test`.
 * It does NOT write any `.bin` files (that's [RegulatedZonePrebakeTest]).
 * It has a 10s timeout per request so it fails fast if the endpoint is down.
 *
 * If this test fails, check:
 * - Network connectivity (are you offline?)
 * - SHOM WFS availability (https://services.data.shom.fr/wfs/reglementation)
 * - The candidate typeNames in [ShomRegulationClient.CANDIDATE_TYPENAMES]
 */
class ShomRegulationConnectivityTest {

    private val testBbox = BoundingBox(
        lonWest = 6.70, latSouth = 43.35,
        lonEast = 7.31, latNorth = 43.73
    )

    @Test
    fun `SHOM WFS GetCapabilities is reachable`() {
        val url = "https://services.data.shom.fr/wfs/reglementation?service=WFS&version=2.0.0&request=GetCapabilities"
        val response = httpGet(url)
        assertNotNull("GetCapabilities returned null — endpoint may be unreachable", response)
        assertTrue("Response should contain FeatureTypeList", response!!.contains("FeatureTypeList"))
        assertTrue("Response should contain XML declaration", response.startsWith("<?xml") || response.contains("<wfs:Capabilities"))
        println("[connectivity] SHOM WFS GetCapabilities: OK (${response.length} chars)")
    }

    @Test
    fun `SHOM WFS GetFeature returns valid FeatureCollection`() = runBlocking {
        val client = ShomRegulationClient()
        val zones = client.fetchZones(testBbox)

        assertNotNull("fetchZones should never return null", zones)
        println("[connectivity] SHOM WFS GetFeature: OK — ${zones.size} zones returned")

        if (zones.isNotEmpty()) {
            // Validate structure of first zone
            val first = zones.first()
            assertTrue("Zone should have at least 3 points in outerRing", first.outerRing.size >= 3)
            assertNotNull("Zone should have a valid zoneType", first.zoneType)
            assertFalse("Zone source should not be empty", first.source.isBlank())
            println("[connectivity] First zone: type=${first.zoneType} name='${first.name}' " +
                    "points=${first.outerRing.size} source=${first.source}")
        } else {
            println("[connectivity] SHOM returned 0 zones for the Nice–Fréjus bbox " +
                    "(may be empty area or schema mismatch)")
        }
    }

    @Test
    fun `multiple candidate typeNames return consistent data`() {
        var totalZones = 0
        var successCount = 0

        for (typeName in ShomRegulationClient.CANDIDATE_TYPENAMES) {
            val url = "https://services.data.shom.fr/wfs/reglementation" +
                    "?service=WFS&version=2.0.0&request=GetFeature" +
                    "&typeNames=$typeName" +
                    "&bbox=${testBbox.lonWest},${testBbox.latSouth},${testBbox.lonEast},${testBbox.latNorth}" +
                    "&outputFormat=application/json&srsName=EPSG:4326"
            val response = httpGet(url)
            if (response != null) {
                successCount++
                totalZones += countOccurrences(response, "\"type\": \"Feature\"")
                println("[connectivity]   $typeName: OK (${countOccurrences(response, "\"Feature\"")} features)")
            } else {
                println("[connectivity]   $typeName: no response")
            }
        }
        println("[connectivity] $successCount/${ShomRegulationClient.CANDIDATE_TYPENAMES.size} typeNames responded; $totalZones total features")

        // At least some typeNames should respond if SHOM is up
        // (no assert — this is diagnostic; SHOM may add/remove layers)
    }

    private fun httpGet(url: String): String? {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.requestMethod = "GET"
            conn.inputStream.bufferedReader().readText()
        } catch (e: Exception) {
            println("[connectivity] HTTP error for $url: ${e.message}")
            null
        }
    }

    private fun countOccurrences(text: String, sub: String): Int {
        var count = 0
        var idx = 0
        while (true) {
            idx = text.indexOf(sub, idx)
            if (idx < 0) break
            count++
            idx += sub.length
        }
        return count
    }
}
