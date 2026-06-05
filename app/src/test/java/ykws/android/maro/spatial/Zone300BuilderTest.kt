package ykws.android.maro.spatial

import org.junit.Assert.*
import org.junit.Test
import ykws.android.maro.data.model.CoastlinePoint
import ykws.android.maro.data.model.CoastlineSegment
import ykws.android.maro.data.model.LatLng

/**
 * Tests for [Zone300Builder] on synthetic coastlines (pure JVM, coarse grid for
 * speed).
 */
class Zone300BuilderTest {

    private val refLat = 43.5
    private val mPerDegLat = SpatialOperations.EARTH_RADIUS_M * Math.PI / 180.0

    private fun seg(points: List<LatLng>, mainland: Boolean, closed: Boolean) =
        CoastlineSegment(
            osmWayId = 0L,
            points = points.map { CoastlinePoint(it.latitude.toFloat(), it.longitude.toFloat()) },
            isMainland = mainland,
            isClosed = closed
        )

    private fun indexOf(segments: List<CoastlineSegment>) =
        CoastlineSpatialIndex(segments, cellSizeM = 100.0)

    // ── Straight mainland coast, water to the south ───────────────────────────

    @Test
    fun `straight coast yields a seaward band near 300 m`() {
        val coast = (0..20).map { LatLng(43.5, 7.00 + it * 0.001) }   // ~1.6 km, east-west
        val seg = seg(coast, mainland = true, closed = false)
        val index = indexOf(listOf(seg))
        val isWater: (Double, Double, Double) -> Boolean = { lat, _, _ -> lat < 43.5 }
        val zone = Zone300Builder(index, listOf(seg), refLat, isWater, cellM = 30.0).build()

        assertTrue("expected a fill polygon", zone.fillPolygons.isNotEmpty())
        assertTrue("expected a seaward line", zone.seawardLines.isNotEmpty())

        // Seaward vertices lie on the band boundary (within ~300 m of the coast); the
        // line wraps the strip's end-caps, so distances range from near 0 up to ~300 m.
        val seawardPts = zone.seawardLines.flatten()
        val dists = seawardPts.map { index.query(it.latitude, it.longitude).distanceMeters }
        for (d in dists) {
            assertTrue("seaward vertex d=$d should be on the band (≤ ~330 m)", d <= 380.0)
        }
        // The band reaches roughly the 300 m contour at its south edge.
        assertTrue("max seaward distance ${dists.max()} should approach 300 m", dists.max() >= 250.0)
        // The southernmost extent reaches well beyond 200 m south of the coast line.
        val minLat = seawardPts.minOf { it.latitude }
        assertTrue("south extent $minLat", minLat < 43.5 - 200.0 / mPerDegLat)
    }

    // ── Isolated island → annulus with a hole ─────────────────────────────────

    @Test
    fun `isolated island produces a fill polygon with a hole`() {
        // Closed square ring island ~0.008deg; water is everywhere outside it.
        val s = 43.50; val n = 43.508; val w = 7.000; val e = 7.010
        val ring = listOf(
            LatLng(s, w), LatLng(s, e), LatLng(n, e), LatLng(n, w), LatLng(s, w)
        )
        val seg = seg(ring, mainland = false, closed = true)
        val index = indexOf(listOf(seg))
        val isWater: (Double, Double, Double) -> Boolean = { lat, lon, _ ->
            !(lat in s..n && lon in w..e)   // inside the square = land
        }
        val zone = Zone300Builder(index, listOf(seg), refLat, isWater, cellM = 30.0).build()

        assertTrue("expected fill polygons", zone.fillPolygons.isNotEmpty())
        assertTrue(
            "island band should be an annulus (have a hole)",
            zone.fillPolygons.any { it.holes.isNotEmpty() }
        )
        assertTrue("expected a seaward line around the island", zone.seawardLines.isNotEmpty())
    }

    // ── No coastline → empty band ─────────────────────────────────────────────

    @Test
    fun `empty index yields empty zone`() {
        val index = CoastlineSpatialIndex(emptyList())
        val zone = Zone300Builder(index, emptyList(), refLat, { _, _, _ -> true }).build()
        assertTrue(zone.fillPolygons.isEmpty())
        assertTrue(zone.seawardLines.isEmpty())
    }

    // ── Flood-fill recovers ray-cast (isOnWater) misclassification ────────────

    @Test
    fun `flood-fill covers water that the ray-cast wrongly calls land`() {
        val coast = (0..40).map { LatLng(43.5, 7.00 + it * 0.0008) }   // ~2.3 km straight E–W
        val seg = seg(coast, mainland = true, closed = false)
        val index = indexOf(listOf(seg))
        val pLatS = 43.4982; val pLatN = 43.4988
        val pLonW = 7.012; val pLonE = 7.020
        val isWater: (Double, Double, Double) -> Boolean = { lat, lon, _ ->
            if (lat in pLatS..pLatN && lon in pLonW..pLonE) false   // injected bug
            else lat < 43.5                                          // truth: south = water
        }
        val zone = Zone300Builder(index, listOf(seg), refLat, isWater, cellM = 20.0).build()

        val py = 43.4985; val px = 7.016
        assertTrue("patch is genuine water within 300 m", index.query(py, px).distanceMeters <= 300.0)
        val covered = zone.fillPolygons.any { pointInRing(py, px, it.outer) && it.holes.none { h -> pointInRing(py, px, h) } }
        assertTrue("flood-fill should cover the misclassified water patch (no gap)", covered)
    }

    @Test
    fun `band stays on the water side only (no wrap-around to land)`() {
        val coast = (0..40).map { LatLng(43.5, 7.00 + it * 0.0008) }
        val seg = seg(coast, mainland = true, closed = false)
        val index = indexOf(listOf(seg))
        val isWater: (Double, Double, Double) -> Boolean = { lat, _, _ -> lat < 43.5 }
        val zone = Zone300Builder(index, listOf(seg), refLat, isWater, cellM = 20.0).build()

        val lon = 7.016
        fun covered(lat: Double) = zone.fillPolygons.any {
            pointInRing(lat, lon, it.outer) && it.holes.none { h -> pointInRing(lat, lon, h) }
        }
        // ~150 m NORTH = land side, within 300 m: must NOT be banded (no wrap-around).
        val landLat = 43.5 + 150.0 / mPerDegLat
        assertTrue("land point is within 300 m", index.query(landLat, lon).distanceMeters <= 300.0)
        assertFalse("band must not cover the land side", covered(landLat))
        // ~150 m SOUTH = water side: must be banded.
        assertTrue("band must cover the water side", covered(43.5 - 150.0 / mPerDegLat))
    }

    private fun pointInRing(lat: Double, lon: Double, ring: List<LatLng>): Boolean {
        var inside = false; var j = ring.size - 1
        for (i in ring.indices) {
            val xi = ring[i].longitude; val yi = ring[i].latitude
            val xj = ring[j].longitude; val yj = ring[j].latitude
            if (((yi > lat) != (yj > lat)) && (lon < (xj - xi) * (lat - yi) / (yj - yi) + xi)) inside = !inside
            j = i
        }
        return inside
    }
}
