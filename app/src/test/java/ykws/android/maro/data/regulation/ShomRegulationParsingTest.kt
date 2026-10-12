package ykws.android.maro.data.regulation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Offline parsing tests for [ShomRegulationClient] — no network. They lock the fixes
 * made against the real INSPIRE GeoJSON shapes probed on 2026-10-12:
 * multi-code `restrn`, ACHARE `catach`, Point buffering and MultiPolygon splitting.
 */
class ShomRegulationParsingTest {

    private val client = ShomRegulationClient()

    private val resare = "REGLEMENTATION_NAVIGATION_BDD_WFS:resare_polygon"
    private val acharePoly = "REGLEMENTATION_NAVIGATION_BDD_WFS:achare_polygon"
    private val acharePoint = "REGLEMENTATION_NAVIGATION_BDD_WFS:achare_point"

    private fun featureCollection(vararg features: String): String =
        """{"type":"FeatureCollection","features":[${features.joinToString(",")}]}"""

    /** A closed WGS84 ring of four corners, as a GeoJSON positions array. */
    private fun positions(west: Double, south: Double, size: Double = 0.01): String {
        val e = west + size
        val n = south + size
        return "[[$west,$south],[$e,$south],[$e,$n],[$west,$n],[$west,$south]]"
    }

    private fun feature(properties: String, geomType: String, coords: String): String =
        """{"type":"Feature","geometry":{"type":"$geomType","coordinates":$coords},"properties":$properties}"""

    private fun polygonFeature(properties: String, west: Double = 7.00, south: Double = 43.50): String =
        feature(properties, "Polygon", "[${positions(west, south)}]")

    @Test
    fun `multi-code restrn keeps every code and classifies fishing`() {
        val body = featureCollection(
            polygonFeature("""{"inspireid":"X1","restrn":"3,11","inform":"fishing prohibited"}""")
        )
        val zones = client.parseFeatureCollection(body, resare)

        assertEquals(1, zones.size)
        val zone = zones.first()
        assertEquals(RegulatedZoneType.FISHING_PROHIBITED, zone.zoneType)
        assertEquals(listOf(3, 11), zone.restrictionCodes)
        assertEquals(3, zone.restrictionCode)
        assertEquals(resare, zone.sourceLayer)
    }

    @Test
    fun `single restrn 2 classifies anchoring prohibited`() {
        val body = featureCollection(
            polygonFeature("""{"inspireid":"X2","restrn":"2","inform":"anchoring prohibited"}""")
        )
        val zone = client.parseFeatureCollection(body, resare).single()

        assertEquals(RegulatedZoneType.ANCHORING_PROHIBITED, zone.zoneType)
        assertEquals(listOf(2), zone.restrictionCodes)
    }

    @Test
    fun `achare point with catach 8 is buffered into a positive mooring ring`() {
        val body = featureCollection(
            feature(
                """{"inspireid":"P1","restrn":null,"catach":"8","inform":"Small craft moorings"}""",
                "Point", "[7.0,43.5]"
            )
        )
        val zone = client.parseFeatureCollection(body, acharePoint).single()

        assertEquals(RegulatedZoneType.MOORING, zone.zoneType)
        assertTrue("point must buffer into a closed ring", zone.outerRing.size >= 4)
        assertEquals(zone.outerRing.first(), zone.outerRing.last())
        assertEquals(emptyList<Int>(), zone.restrictionCodes)
        val lat = zone.outerRing.map { it.latitude }.average()
        val lon = zone.outerRing.map { it.longitude }.average()
        assertTrue("buffered centroid latitude", abs(lat - 43.5) < 0.01)
        assertTrue("buffered centroid longitude", abs(lon - 7.0) < 0.01)
    }

    @Test
    fun `achare polygon restrn 2 wins over catach 8`() {
        val body = featureCollection(
            polygonFeature("""{"inspireid":"A1","restrn":"2","catach":"8","inform":"Small craft moorings"}""")
        )
        val zone = client.parseFeatureCollection(body, acharePoly).single()

        assertEquals(RegulatedZoneType.ANCHORING_PROHIBITED, zone.zoneType)
        assertEquals(listOf(2), zone.restrictionCodes)
    }

    @Test
    fun `multipolygon yields one zone per polygon`() {
        val multi = "[[${positions(7.0, 43.5)}],[${positions(7.1, 43.6)}]]"
        val body = featureCollection(
            feature("""{"inspireid":"M1","restrn":"1"}""", "MultiPolygon", multi)
        )
        val zones = client.parseFeatureCollection(body, resare)

        assertEquals(2, zones.size)
        assertTrue(zones.all { it.zoneType == RegulatedZoneType.ANCHORING_PROHIBITED })
        assertTrue(zones.all { it.sourceLayer == resare })
    }
}
