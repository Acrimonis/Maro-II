package ykws.android.maro.data.coastline

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test
import ykws.android.maro.data.model.CoastlinePoint
import ykws.android.maro.data.model.CoastlineSegment
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.PointHazard
import ykws.android.maro.spatial.CoastlineSpatialIndex
import ykws.android.maro.spatial.SpatialOperations
import kotlin.math.abs

/**
 * Integration test for the OSM-seamark offshore-danger pipeline:
 *
 *   OSM seamark nodes → [SeamarkParser] → [HazardRings] → [CoastlineSpatialIndex]
 *
 * Proves that real charted dangers (La Fourmigue, the cardinal-marked Batéguier and
 * Chrétienne shoals) parsed from OpenStreetMap become closed island rings the spatial
 * engine treats as land/obstruction — they appear on the map and trip the 300 m /
 * water-land logic, which they previously did not.
 *
 * Fully offline: the seamark JSON is an in-test fixture mirroring the live Overpass
 * response (no network), so the same production code path runs end-to-end.
 */
class SeamarkHazardIntegrationTest {

    /** Representative projection latitude for the Nice–Fréjus zone. */
    private val refLat = 43.5
    private val json = Json { ignoreUnknownKeys = true }

    /** Fixture mirroring real OSM seamark nodes for the zone (+ a harbour light to exclude). */
    private val seamarkJson = """
      [
        {"type":"node","id":1,"lat":43.53957,"lon":7.08325,"tags":{"seamark:type":"beacon_isolated_danger","seamark:name":"La Fourmigue"}},
        {"type":"node","id":2,"lat":43.52655,"lon":7.03046,"tags":{"seamark:type":"beacon_cardinal","seamark:name":"Batéguier"}},
        {"type":"node","id":3,"lat":43.42214,"lon":6.89497,"tags":{"seamark:type":"beacon_cardinal","seamark:name":"La Chrétienne"}},
        {"type":"node","id":4,"lat":43.54507,"lon":7.01768,"tags":{"seamark:type":"light_minor","seamark:name":"harbour light (excluded)"}}
      ]
    """.trimIndent()

    private val hazards: List<PointHazard> =
        SeamarkParser.parse(json.parseToJsonElement(seamarkJson).jsonArray.map { it.jsonObject })

    private val segments: List<CoastlineSegment> = hazards.map { HazardRings.toSegment(it, refLat) }

    /**
     * Synthetic mainland coast (north of all hazards) so the index mirrors production:
     * `allSegments = [mainland] + islands(+ hazard rings)`. This is what gives the
     * hazard rings `polylineIdx ≥ 1` (island), since [CoastlineSpatialIndex] derives
     * `isMainland` from position (index 0), not the segment flag.
     */
    private val mainland: CoastlineSegment = CoastlineSegment(
        osmWayId = 1L,
        points = listOf(6.80, 6.95, 7.10, 7.25, 7.30).map { lon ->
            CoastlinePoint(lat = 43.66f, lon = lon.toFloat())
        },
        isMainland = true,
        isClosed = false
    )

    private val index = CoastlineSpatialIndex(listOf(mainland) + segments)

    @Test
    fun `only real dangers are parsed - harbour light excluded`() {
        assertEquals("3 charted dangers (harbour light excluded)", 3, hazards.size)
        assertTrue(hazards.any { it.name.contains("Fourmigue", ignoreCase = true) })
    }

    @Test
    fun `all parsed dangers lie inside the Nice-Frejus bbox and longitude clip`() {
        for (h in hazards) {
            assertTrue(
                "${h.name} lat ${h.lat} out of bbox",
                h.lat in CoastlineGenerator.BBOX_LAT_MIN..CoastlineGenerator.BBOX_LAT_MAX
            )
            assertTrue(
                "${h.name} lon ${h.lon} out of clip [${CoastlineGenerator.LON_WEST}, ${CoastlineGenerator.LON_EAST}]",
                h.lon in CoastlineGenerator.LON_WEST..CoastlineGenerator.LON_EAST
            )
        }
    }

    @Test
    fun `each danger is now coast the spatial index sees (was invisible before)`() {
        for (h in hazards) {
            val r = index.query(h.lat, h.lon)
            assertTrue(
                "${h.name}: expected coast within ~radius, got ${r.distanceMeters} m",
                r.distanceMeters <= h.bufferRadiusM * 1.15
            )
            assertFalse("${h.name}: hazard ring must be an island, not mainland", r.isMainland)
            assertTrue("${h.name}: hazard ring must be an island (polylineIdx ≥ 1)", r.polylineIdx >= 1)
        }
    }

    @Test
    fun `open water far from every danger and the coast stays far`() {
        val r = index.query(43.475, 7.18)
        assertTrue(
            "Open water should be far from any coast, got ${r.distanceMeters} m",
            r.distanceMeters > 1_000.0
        )
    }

    @Test
    fun `danger centre is enclosed by its ring (south-ray parity = land)`() {
        for ((i, h) in hazards.withIndex()) {
            val crossings = southRayCrossings(segments[i], h.lat, h.lon)
            assertEquals(
                "${h.name}: centre must be inside the ring (odd crossings = LAND)",
                1, crossings % 2
            )
        }
    }

    @Test
    fun `a point outside the ring is not enclosed (even parity = water)`() {
        for ((i, h) in hazards.withIndex()) {
            // ~150 m east of the hazard — well outside any micro-circle.
            val crossings = southRayCrossings(segments[i], h.lat, h.lon + 0.0019)
            assertEquals(
                "${h.name}: point outside ring must be water (even crossings)",
                0, crossings % 2
            )
        }
    }

    @Test
    fun `rings are closed and respect their buffer radius`() {
        for ((i, h) in hazards.withIndex()) {
            val pts = segments[i].points
            assertTrue("${h.name}: ring needs ≥ 4 points", pts.size >= 4)

            // Closed: first vertex repeated at the end.
            assertEquals("${h.name}: ring not closed (lat)", pts.first().lat, pts.last().lat)
            assertEquals("${h.name}: ring not closed (lon)", pts.first().lon, pts.last().lon)

            // Every distinct vertex sits ~radius from the centre.
            val center = LatLng(h.lat, h.lon)
            for (j in 0 until pts.size - 1) {
                val d = SpatialOperations.haversine(
                    center, LatLng(pts[j].lat.toDouble(), pts[j].lon.toDouble())
                )
                assertTrue(
                    "${h.name}: vertex $j at ${d}m, expected ≈ ${h.bufferRadiusM}m",
                    abs(d - h.bufferRadiusM) <= 2.0
                )
            }
        }
    }

    @Test
    fun `La Fourmigue is present (the case that first revealed the gap)`() {
        assertTrue(
            "La Fourmigue must be parsed from the OSM seamarks",
            hazards.any { it.name.contains("Fourmigue", ignoreCase = true) }
        )
    }

    @Test
    fun `regression - charted danger NW of Sainte-Marguerite (Bateguier cardinal) is visible`() {
        // The West-cardinal Batéguier / Jonquière shoal lies off the continuous trait
        // de côte and was invisible until ingested. Now it comes from the OSM
        // seamark:type=beacon_cardinal node — no hand-typed coordinate.
        val nw = hazards.firstOrNull { it.name.contains("Batéguier", ignoreCase = true) }
        assertNotNull("Danger NW of Sainte-Marguerite (Batéguier) must be parsed", nw)
        nw!!

        // It really is off the NW of the island: W of the island's W tip (~7.045 E),
        // off the northern shore (~43.52 N) — not confused with the E/NE Tradelière.
        assertTrue("Batéguier longitude ${nw.lon} should be NW of the island", nw.lon < 7.045)
        assertTrue("Batéguier latitude ${nw.lat} should be off the N shore", nw.lat > 43.520)

        // And it is now coast the spatial engine sees, as an island ring.
        val r = index.query(nw.lat, nw.lon)
        assertTrue(
            "Batéguier must now be coast within ~radius, got ${r.distanceMeters} m",
            r.distanceMeters <= nw.bufferRadiusM * 1.15
        )
        assertFalse("Batéguier ring must be an island, not mainland", r.isMainland)
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Counts south-ray crossings of a closed ring — mirrors isOnWater enclosure. */
    private fun southRayCrossings(segment: CoastlineSegment, lat: Double, lon: Double): Int {
        val pts = segment.points
        val rayLatEnd = lat - 0.02  // ~2.2 km south, far beyond any micro-circle
        var crossings = 0
        for (k in 0 until pts.size - 1) {
            val a = LatLng(pts[k].lat.toDouble(), pts[k].lon.toDouble())
            val b = LatLng(pts[k + 1].lat.toDouble(), pts[k + 1].lon.toDouble())
            if (SpatialOperations.rayCrossesSegmentSouth(lon, lat, rayLatEnd, a, b)) crossings++
        }
        return crossings
    }
}
