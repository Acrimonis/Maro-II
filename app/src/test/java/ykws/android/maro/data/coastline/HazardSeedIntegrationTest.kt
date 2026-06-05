package ykws.android.maro.data.coastline

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
 * Integration test for the offshore point-hazard pipeline:
 *
 *   [HazardSeeds] → [HazardRings] → [CoastlineSpatialIndex] / south-ray parity
 *
 * Proves that hard-coded hazards (Phare de la Fourmigue, Basses de la Chrétienne,
 * Sec de la Tradelière) are turned into closed island rings the spatial engine
 * treats as land/obstruction — i.e. they would now appear on the map and trip the
 * 300 m / water-land logic, which they previously did NOT.
 *
 * Fully offline: uses production code end-to-end without the Overpass or Shom WFS
 * network calls (the seeds are merged independently of the WFS).
 */
class HazardSeedIntegrationTest {

    /** Representative projection latitude for the Nice–Fréjus zone. */
    private val refLat = 43.5

    private val seeds: List<PointHazard> = HazardSeeds.NICE_FREJUS
    private val segments: List<CoastlineSegment> = seeds.map { HazardRings.toSegment(it, refLat) }

    /**
     * Synthetic mainland coast (north of all hazards) so the index mirrors the
     * production layout: `allSegments = [mainland] + islands(+ hazard rings)`.
     * This is what guarantees hazard rings get `polylineIdx ≥ 1` (island), since
     * [CoastlineSpatialIndex] derives `isMainland` from position (index 0), not the
     * segment flag.
     */
    private val mainland: CoastlineSegment = CoastlineSegment(
        osmWayId = 1L,
        points = listOf(6.80, 6.95, 7.10, 7.25, 7.30).map { lon ->
            CoastlinePoint(lat = 43.66f, lon = lon.toFloat())
        },
        isMainland = true,
        isClosed = false
    )

    /** Production-like index: mainland at 0, hazard rings as islands at ≥ 1. */
    private val index = CoastlineSpatialIndex(listOf(mainland) + segments)

    @Test
    fun `all seeds lie inside the Nice-Frejus bounding box and longitude clip`() {
        for (h in seeds) {
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
    fun `each hazard is now coast the spatial index sees (was invisible before)`() {
        for (h in seeds) {
            val r = index.query(h.lat, h.lon)
            // Center-to-edge of a micro-circle ≈ its buffer radius — small, finite,
            // and crucially classified as island (not mainland).
            assertTrue(
                "${h.name}: expected coast within ~radius, got ${r.distanceMeters} m",
                r.distanceMeters <= h.bufferRadiusM * 1.15
            )
            assertFalse("${h.name}: hazard ring must be an island, not mainland", r.isMainland)
            assertTrue("${h.name}: hazard ring must be an island (polylineIdx ≥ 1)", r.polylineIdx >= 1)
        }
    }

    @Test
    fun `open water far from every hazard and the coast stays far`() {
        // A point inside the dataset bbox but kilometres from the mainland and
        // from all three seeds.
        val r = index.query(43.475, 7.18)
        assertTrue("Open water should be far from any coast, got ${r.distanceMeters} m",
            r.distanceMeters > 1_000.0)
    }

    @Test
    fun `hazard centre is enclosed by its ring (south-ray parity = land)`() {
        for ((i, h) in seeds.withIndex()) {
            val crossings = southRayCrossings(segments[i], h.lat, h.lon)
            assertEquals(
                "${h.name}: centre must be inside the ring (odd crossings = LAND)",
                1, crossings % 2
            )
        }
    }

    @Test
    fun `a point outside the ring is not enclosed (even parity = water)`() {
        for ((i, h) in seeds.withIndex()) {
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
        for ((i, h) in seeds.withIndex()) {
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

    // ── validation against known real-world cases ──────────────────────────────

    @Test
    fun `La Fourmigue is seeded (the case that already worked)`() {
        assertTrue(
            "Phare de la Fourmigue must remain seeded",
            seeds.any { it.name.contains("Fourmigue", ignoreCase = true) }
        )
    }

    @Test
    fun `regression - charted danger NW of Sainte-Marguerite is seeded and visible`() {
        // Bug report: La Fourmigue appeared on the build but the isolated danger off
        // the NW of Île Sainte-Marguerite (West cardinal, Plateau du Batéguier /
        // Jonquière) was missing — it lies off the continuous trait de côte and was
        // never seeded. Position from OSM seamark:type=beacon_cardinal (west).
        val nw = seeds.firstOrNull { it.name.contains("Batéguier", ignoreCase = true) }
        assertNotNull("Danger NW of Sainte-Marguerite (Batéguier) must be seeded", nw)
        nw!!

        // It really is off the NW of the island: W of the island's W tip (~7.045 E),
        // off the northern shore (~43.52 N) — i.e. not confused with the E/NE Tradelière.
        assertTrue("Batéguier longitude ${nw.lon} should be NW of the island", nw.lon < 7.045)
        assertTrue("Batéguier latitude ${nw.lat} should be off the N shore", nw.lat > 43.520)

        // And it is now coast the spatial engine sees, as an island ring (was invisible).
        val r = index.query(nw.lat, nw.lon)
        assertTrue(
            "Batéguier must now be coast within ~radius, got ${r.distanceMeters} m",
            r.distanceMeters <= nw.bufferRadiusM * 1.15
        )
        assertFalse("Batéguier ring must be an island, not mainland", r.isMainland)
    }

    // ── seed + WFS merge / dedup contract (CoastlineGenerator.mergeHazards) ─────

    @Test
    fun `mergeHazards with no WFS hits returns exactly the seeds (offline baseline)`() {
        // Mirrors the atonClient = null / offline-failure path: seeds only, no loss.
        assertEquals(seeds, CoastlineGenerator.mergeHazards(seeds, emptyList()))
    }

    @Test
    fun `mergeHazards drops a WFS hit coincident with a seed`() {
        // ~11 m N of La Fourmigue — well inside the 80 m dedup radius.
        val dup = PointHazard(
            lat = HazardSeeds.FOURMIGUE.lat + 0.0001,
            lon = HazardSeeds.FOURMIGUE.lon,
            name = "WFS duplicate of Fourmigue"
        )
        val merged = CoastlineGenerator.mergeHazards(seeds, listOf(dup))
        assertEquals("coincident WFS hit must be deduped", seeds.size, merged.size)
        assertFalse("the duplicate must not survive", merged.contains(dup))
    }

    @Test
    fun `mergeHazards keeps a WFS hit far from every seed`() {
        val novel = PointHazard(lat = 43.480, lon = 7.200, name = "novel offshore danger")
        val merged = CoastlineGenerator.mergeHazards(seeds, listOf(novel))
        assertEquals(seeds.size + 1, merged.size)
        assertTrue("a far-from-seed WFS hit must be kept", merged.contains(novel))
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
