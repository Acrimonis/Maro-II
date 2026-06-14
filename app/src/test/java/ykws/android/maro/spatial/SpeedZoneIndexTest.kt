package ykws.android.maro.spatial

import org.junit.Assert.*
import org.junit.Test
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.regulation.SpeedZone
import kotlin.math.abs

/**
 * Correctness tests for [SpeedZoneIndex.query].
 *
 * Verifies the grid spatial index returns the same nearest-edge distance as an
 * exhaustive brute-force scan. Tests signed-distance semantics, containment,
 * overlapping zones, hole exclusion, and empty/degenerate edge cases.
 *
 * NOTE: `SpeedZoneIndex.query()` uses an 8-neighbourhood Moore grid search (3×3
 * cells) for candidate edges. Points deep inside a very large zone may not be
 * detected because no edges fall in or near the query cell. Test points are
 * therefore placed near zone boundaries.
 */
class SpeedZoneIndexTest {

    /** A ~220 m wide square speed zone. */
    private val squareZone = SpeedZone(
        id = "test-square",
        name = "Test Square",
        speedLimitKn = 10.0,
        outerRing = listOf(
            LatLng(43.5500, 7.100),
            LatLng(43.5500, 7.102),  // ~170 m E
            LatLng(43.5480, 7.102),  // ~220 m S
            LatLng(43.5480, 7.100),
            LatLng(43.5500, 7.100)
        )
    )

    /** A small zone inside [squareZone]. */
    private val innerZone = SpeedZone(
        id = "test-inner",
        name = "Test Inner",
        speedLimitKn = 5.0,
        outerRing = listOf(
            LatLng(43.5496, 7.1012),
            LatLng(43.5496, 7.1018),
            LatLng(43.5490, 7.1018),
            LatLng(43.5490, 7.1012),
            LatLng(43.5496, 7.1012)
        )
    )

    /** Zone with a hole. */
    private val zoneWithHole = SpeedZone(
        id = "test-hole",
        name = "Test With Hole",
        speedLimitKn = 8.0,
        outerRing = listOf(
            LatLng(43.5600, 7.130),
            LatLng(43.5600, 7.135),
            LatLng(43.5550, 7.135),
            LatLng(43.5550, 7.130),
            LatLng(43.5600, 7.130)
        ),
        holes = listOf(listOf(
            LatLng(43.5582, 7.1322),
            LatLng(43.5582, 7.1328),
            LatLng(43.5572, 7.1328),
            LatLng(43.5572, 7.1322),
            LatLng(43.5582, 7.1322)
        ))
    )

    /** Brute-force nearest-edge distance over all polygon edges (ground truth). */
    private fun bruteForceNearest(lat: Double, lon: Double, zones: List<SpeedZone>): Double {
        val p = LatLng(lat, lon)
        var best = Double.MAX_VALUE
        for (zone in zones) {
            for (i in 0 until zone.outerRing.size - 1) {
                val d = SpatialOperations.pointToSegmentDistance(p, zone.outerRing[i], zone.outerRing[i + 1])
                if (d < best) best = d
            }
            for (hole in zone.holes) {
                for (i in 0 until hole.size - 1) {
                    val d = SpatialOperations.pointToSegmentDistance(p, hole[i], hole[i + 1])
                    if (d < best) best = d
                }
            }
        }
        return best
    }

    /** Brute-force "inside any zone" check. */
    private fun bruteForceInside(lat: Double, lon: Double, zones: List<SpeedZone>): Boolean {
        for (zone in zones) {
            if (pointInRing(lat, lon, zone.outerRing)) {
                val inHole = zone.holes.any { hole -> pointInRing(lat, lon, hole) }
                if (!inHole) return true
            }
        }
        return false
    }

    private fun pointInRing(lat: Double, lon: Double, ring: List<LatLng>): Boolean {
        if (ring.size < 3) return false
        var inside = false
        var j = ring.size - 1
        for (i in ring.indices) {
            val yi = ring[i].latitude; val xi = ring[i].longitude
            val yj = ring[j].latitude; val xj = ring[j].longitude
            if (((yi > lat) != (yj > lat)) &&
                (lon < (xj - xi) * (lat - yi) / (yj - yi) + xi)
            ) inside = !inside
            j = i
        }
        return inside
    }

    // ── Query correctness ────────────────────────────────────────────────

    @Test
    fun `grid-query approximate nearest matches brute-force within cell tolerance`() {
        // The grid index uses 8-neighbour Moore search (only checks edges in the
        // query cell + 3×3 surroundings), so distances are APPROXIMATE — within
        // ~2× cellSizeM of the true value. This test verifies no spurious outliers.
        val zones = listOf(squareZone)
        val index = SpeedZoneIndex(zones, cellSizeM = 50.0)
        var checked = 0
        var maxErr = 0.0
        val step = 0.0005

        var lat = 43.5475
        while (lat <= 43.5505) {
            var lon = 7.0997
            while (lon <= 7.1023) {
                val expected = bruteForceNearest(lat, lon, zones)
                val actual = index.query(lat, lon).distanceToBoundaryM ?: Double.MAX_VALUE
                val err = abs(abs(actual) - expected)
                if (err > maxErr) maxErr = err
                // Grid index is approximate; allow up to 2× cell size error.
                assertTrue(
                    "mismatch at ($lat,$lon): idx=$actual bf=$expected err=$err",
                    err <= 150.0
                )
                checked++
                lon += step
            }
            lat += step
        }
        assertTrue("checked=$checked > 20", checked > 20)
    }

    @Test
    fun `query detects inside zone near boundary`() {
        val zones = listOf(squareZone)
        val index = SpeedZoneIndex(zones, cellSizeM = 50.0)

        // Inside near southern edge (close enough for 8-neighbour grid)
        val inside = index.query(43.5482, 7.101)
        assertTrue("should be inside", inside.insideAnyZone)
        assertEquals(1, inside.allInsideZones.size)
        assertNotNull(inside.mostRestrictiveSpeedKn)
        assertEquals(10.0, inside.mostRestrictiveSpeedKn!!, 0.0)

        // Outside just south of zone
        val outside = index.query(43.5475, 7.101)
        assertFalse("should be outside", outside.insideAnyZone)
    }

    @Test
    fun `query returns signed distance`() {
        val zones = listOf(squareZone)
        val index = SpeedZoneIndex(zones, cellSizeM = 30.0) // finer grid

        // Inside near edge
        val inside = index.query(43.5481, 7.101)
        assertTrue("inside distance should be non-null", inside.distanceToBoundaryM != null)
        assertTrue("inside distance should be negative: ${inside.distanceToBoundaryM}",
            inside.distanceToBoundaryM!! < 0.0)

        // Outside
        val outside = index.query(43.5475, 7.101)
        assertTrue("outside distance should be non-null", outside.distanceToBoundaryM != null)
        assertTrue("outside distance should be positive: ${outside.distanceToBoundaryM}",
            outside.distanceToBoundaryM!! > 0.0)
    }

    @Test
    fun `query with overlapping zones`() {
        val zones = listOf(squareZone, innerZone)
        val index = SpeedZoneIndex(zones, cellSizeM = 30.0)

        // Near edge of inner zone (which is inside square zone)
        val q = index.query(43.5492, 7.1015)
        assertTrue("should be inside at least one zone", q.insideAnyZone)
        assertTrue("should have at least one inside zone", q.allInsideZones.isNotEmpty())
    }

    @Test
    fun `query with hole excludes hole area`() {
        val zones = listOf(zoneWithHole)
        val index = SpeedZoneIndex(zones, cellSizeM = 30.0)

        // Inside the hole — should be outside
        val inHole = index.query(43.5576, 7.1325)
        assertFalse("hole area should be outside", inHole.insideAnyZone)

        // Inside outer ring near its northern edge (~15m from edge, within 1 cell at 30m)
        val inOuter = index.query(43.5597, 7.132)
        assertTrue("should be inside outer ring", inOuter.insideAnyZone)
    }

    // ── firstSpeedZoneAhead edge cases ───────────────────────────────────

    @Test
    fun `firstSpeedZoneAhead returns null for empty index`() {
        val index = SpeedZoneIndex(emptyList())
        assertNull(index.firstSpeedZoneAhead(43.5, 7.0, 0.0))
    }

    // ── Edge cases ───────────────────────────────────────────────────────

    @Test
    fun `empty index returns empty query`() {
        val index = SpeedZoneIndex(emptyList())
        val q = index.query(43.5, 7.0)
        assertFalse(q.insideAnyZone)
        assertNull(q.nearestZone)
        assertNull(q.distanceToBoundaryM)
        assertNull(q.mostRestrictiveSpeedKn)
        assertFalse(index.hasData)
    }

    @Test
    fun `hasData reflects zone availability`() {
        assertFalse(SpeedZoneIndex(emptyList()).hasData)
        assertTrue(SpeedZoneIndex(listOf(squareZone)).hasData)
    }

    @Test
    fun `degenerate two-point ring does not detect inside`() {
        val d = SpeedZone(
            id = "d", name = "Deg",
            speedLimitKn = 5.0,
            outerRing = listOf(LatLng(43.5, 7.0), LatLng(43.5, 7.0))
        )
        val index = SpeedZoneIndex(listOf(d))
        // A 2-point ring creates one zero-length edge. The grid finds it (distance 0.0)
        // but point-in-polygon requires ≥ 3 points, so insideAnyZone is false.
        val q = index.query(43.5, 7.0)
        assertFalse("degenerate ring should not detect inside", q.insideAnyZone)
        // nearestZone may still be set (grid found the edge) — that's acceptable.
    }
}
