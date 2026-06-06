package ykws.android.maro.data.coastline

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test
import ykws.android.maro.data.model.HazardType

/**
 * Unit tests for [SeamarkParser] — the OSM seamark → `PointHazard` mapping that
 * replaced the hand-typed `HazardSeeds`.
 *
 * Verifies the danger-type filter (the "no fake ones" guarantee: harbour lights and
 * channel marks are dropped), the type→buffer mapping, and robustness to junk nodes.
 * Fully offline: the JSON fixtures mirror real live Overpass responses for the zone.
 */
class SeamarkParserTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun nodes(raw: String): List<JsonObject> =
        json.parseToJsonElement(raw).jsonArray.map { it.jsonObject }

    /** 3 real dangers + 2 non-dangers (a harbour light, a channel buoy) that must drop. */
    private val fixture = """
      [
        {"type":"node","id":1,"lat":43.53957,"lon":7.08325,"tags":{"seamark:type":"beacon_isolated_danger","seamark:name":"La Fourmigue"}},
        {"type":"node","id":2,"lat":43.52655,"lon":7.03046,"tags":{"seamark:type":"beacon_cardinal","seamark:name":"Batéguier"}},
        {"type":"node","id":3,"lat":43.42214,"lon":6.89497,"tags":{"seamark:type":"beacon_cardinal","seamark:name":"La Chrétienne"}},
        {"type":"node","id":4,"lat":43.54507,"lon":7.01768,"tags":{"seamark:type":"light_minor","seamark:name":"Môle de l'Ouest"}},
        {"type":"node","id":5,"lat":43.55,"lon":7.05,"tags":{"seamark:type":"buoy_lateral"}}
      ]
    """.trimIndent()

    @Test
    fun `keeps only danger marks - drops harbour light and channel buoy`() {
        val hazards = SeamarkParser.parse(nodes(fixture))
        assertEquals("3 dangers expected (light + lateral buoy excluded)", 3, hazards.size)
        val names = hazards.map { it.name }
        assertTrue(names.any { it.contains("Fourmigue", ignoreCase = true) })
        assertTrue(names.any { it.contains("Batéguier", ignoreCase = true) })
        assertTrue(names.any { it.contains("Chrétienne", ignoreCase = true) })
        assertFalse("harbour light must be excluded", names.any { it.contains("Ouest", ignoreCase = true) })
    }

    @Test
    fun `La Fourmigue maps to an isolated-danger buffer at its real position`() {
        val f = SeamarkParser.parse(nodes(fixture)).first { it.name.contains("Fourmigue", ignoreCase = true) }
        assertEquals(HazardType.ISOLATED_DANGER, f.type)
        assertEquals(43.53957, f.lat, 1e-6)
        assertEquals(7.08325, f.lon, 1e-6)
    }

    @Test
    fun `Bateguier cardinal is kept and sits NW of Sainte-Marguerite`() {
        val b = SeamarkParser.parse(nodes(fixture)).firstOrNull { it.name.contains("Batéguier", ignoreCase = true) }
        assertNotNull("Batéguier cardinal beacon must be parsed", b)
        b!!
        assertTrue("NW of island: W of ~7.045 E", b.lon < 7.045)
        assertTrue("off the N shore: > 43.520 N", b.lat > 43.520)
    }

    @Test
    fun `classify maps danger types and rejects navigation aids`() {
        assertEquals(HazardType.ISOLATED_DANGER, SeamarkParser.classify("beacon_isolated_danger"))
        assertEquals(HazardType.ISOLATED_DANGER, SeamarkParser.classify("beacon_cardinal"))
        assertEquals(HazardType.ISOLATED_DANGER, SeamarkParser.classify("wreck"))
        assertEquals(HazardType.ROCK, SeamarkParser.classify("rock"))
        assertNull(SeamarkParser.classify("light_major"))
        assertNull(SeamarkParser.classify("light_minor"))
        assertNull(SeamarkParser.classify("buoy_lateral"))
        assertNull(SeamarkParser.classify("small_craft_facility"))
    }

    @Test
    fun `skips nodes missing type or coords, and non-node elements`() {
        val junk = """
          [
            {"type":"node","id":10,"lat":43.5,"lon":7.0,"tags":{"name":"no seamark type"}},
            {"type":"node","id":11,"lat":43.5,"tags":{"seamark:type":"beacon_cardinal"}},
            {"type":"way","id":12,"tags":{"seamark:type":"beacon_cardinal"}}
          ]
        """.trimIndent()
        assertTrue("all junk elements skipped", SeamarkParser.parse(nodes(junk)).isEmpty())
    }

    @Test
    fun `unnamed danger still parsed with a blank name`() {
        val raw = """[{"type":"node","id":20,"lat":43.5,"lon":7.0,"tags":{"seamark:type":"beacon_cardinal"}}]"""
        val h = SeamarkParser.parse(nodes(raw))
        assertEquals(1, h.size)
        assertEquals("", h.first().name)
        assertEquals(HazardType.ISOLATED_DANGER, h.first().type)
    }
}
