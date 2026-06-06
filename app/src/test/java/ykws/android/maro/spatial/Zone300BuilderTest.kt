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

    // ── Band trusts the per-cell water test (anti-mirror) ─────────────────────

    @Test
    fun `band never covers a cell the water test calls land, even when the flood reaches it`() {
        // Regression for the on-device land-mirror: the flood-fill alone painted BOTH sides of
        // the coast (a flood bleeding past a barrier gap fills the inland ribbon too). The final
        // band MUST be gated by the per-cell water test. Here a patch of genuine water is
        // deliberately labelled LAND by isWater — the flood still reaches it, but it must NOT be
        // banded, because the band now trusts the water test. (The old "flood recovers a
        // misclassified-water gap" behaviour is intentionally gone: isWater is now rigorous.)
        val coast = (0..40).map { LatLng(43.5, 7.00 + it * 0.0008) }   // ~2.3 km straight E–W
        val seg = seg(coast, mainland = true, closed = false)
        val index = indexOf(listOf(seg))
        val pLatS = 43.4982; val pLatN = 43.4988
        val pLonW = 7.012; val pLonE = 7.020
        val isWater: (Double, Double, Double) -> Boolean = { lat, lon, _ ->
            if (lat in pLatS..pLatN && lon in pLonW..pLonE) false   // genuine water, called LAND
            else lat < 43.5                                          // truth: south = water
        }
        val zone = Zone300Builder(index, listOf(seg), refLat, isWater, cellM = 20.0).build()

        fun covered(lat: Double, lon: Double) = zone.fillPolygons.any {
            pointInRing(lat, lon, it.outer) && it.holes.none { h -> pointInRing(lat, lon, h) }
        }
        // Inside the patch (within 300 m, flood-reachable) but isWater says land → NOT banded.
        assertTrue("patch is within 300 m", index.query(43.4985, 7.016).distanceMeters <= 300.0)
        assertFalse("a cell the water test calls land must never be banded", covered(43.4985, 7.016))
        // Genuine water just west of the patch is still banded — the water band is intact.
        assertTrue("genuine water is still banded", covered(43.4985, 7.008))
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

    // ── Far-offshore isolated ring keeps its band (regression) ────────────────

    @Test
    fun `far-offshore isolated ring still gets a band (disconnected from the anchor)`() {
        // A big island (which holds the open-sea anchor — the globally deepest water cell)
        // plus a small isolated ring ~3.3 km away, far enough that its 500 m ribbon is a
        // DISCONNECTED flood component. Regression for the bug where La Fourmigue rendered
        // but had no 300 m band: keeping only the anchor's component dropped any isolated
        // offshore danger. markSeaComponents must also keep each closed ring's own band.
        val bigCenter = LatLng(43.500, 7.050)
        val smallCenter = LatLng(43.530, 7.050)
        val big = seg(circleRing(bigCenter.latitude, bigCenter.longitude, 600.0), mainland = false, closed = true)
        val small = seg(circleRing(smallCenter.latitude, smallCenter.longitude, 25.0), mainland = false, closed = true)
        val segs = listOf(big, small)
        val index = indexOf(segs)
        val isWater: (Double, Double, Double) -> Boolean = { lat, lon, _ ->
            val p = LatLng(lat, lon)
            SpatialOperations.haversine(p, bigCenter) > 600.0 &&
                SpatialOperations.haversine(p, smallCenter) > 25.0
        }
        val zone = Zone300Builder(index, segs, refLat, isWater, cellM = 30.0).build()

        // The small ring's band must exist: a fill vertex within ~(25 + 300 + slack) m.
        val smallBanded = zone.fillPolygons.any { poly ->
            poly.outer.any { SpatialOperations.haversine(it, smallCenter) <= 360.0 }
        }
        assertTrue("isolated far-offshore ring must still get a 300 m band", smallBanded)

        // Sanity: the big island (the anchor's component) is still banded too.
        val bigBanded = zone.fillPolygons.any { poly ->
            poly.outer.any { SpatialOperations.haversine(it, bigCenter) <= 950.0 }
        }
        assertTrue("the big island band must remain", bigBanded)
    }

    @Test
    fun `land-side pocket beside a hazard ring is not banded (no mirror with islands)`() {
        // Regression GUARD (2nd land-mirror occurrence): re-including isolated islands must
        // not bring back the land-side band. A near-shore hazard ring sits just off a
        // straight coast; the ray cast wrongly calls a patch BEHIND the coast (land side)
        // water, flooding an inland pocket. Re-seeding from the ring's landward side would
        // keep that pocket (the mirror) — the surround-guard must reject it. The original
        // mirror test had no closed rings, so it never exercised this path.
        val coast = (0..30).map { LatLng(43.5, 7.000 + it * 0.001) }   // straight E–W, water south
        val land = seg(coast, mainland = true, closed = false)
        val hz = LatLng(43.4994, 7.015)                                // ~65 m south of coast
        val ring = seg(circleRing(hz.latitude, hz.longitude, 30.0), mainland = false, closed = true)
        val segs = listOf(land, ring)
        val index = indexOf(segs)

        // Injected land-side misclassification: a patch NORTH of the coast called "water".
        val pkS = 43.5008; val pkN = 43.5035; val pkW = 7.012; val pkE = 7.018
        val isWater: (Double, Double, Double) -> Boolean = { lat, lon, _ ->
            when {
                SpatialOperations.haversine(LatLng(lat, lon), hz) <= 30.0 -> false  // inside hazard = land
                lat in pkS..pkN && lon in pkW..pkE -> true                          // injected inland pocket
                else -> lat < 43.5                                                  // truth: south = water
            }
        }
        val zone = Zone300Builder(index, segs, refLat, isWater, cellM = 20.0).build()

        fun covered(lat: Double, lon: Double) = zone.fillPolygons.any {
            pointInRing(lat, lon, it.outer) && it.holes.none { h -> pointInRing(lat, lon, h) }
        }
        assertFalse("land-side pocket must NOT be banded (mirror regression)", covered(43.5021, 7.015))
        assertTrue("water side by the hazard must be banded", covered(43.4990, 7.015))
    }

    /** Closed [n]-gon ring of radius [radiusM] (m) around a centre, first vertex repeated. */
    private fun circleRing(centerLat: Double, centerLon: Double, radiusM: Double, n: Int = 24): List<LatLng> {
        val dLat = radiusM / mPerDegLat
        val dLon = radiusM / (mPerDegLat * Math.cos(Math.toRadians(centerLat)))
        val ring = ArrayList<LatLng>(n + 1)
        for (k in 0 until n) {
            val a = 2.0 * Math.PI * k / n
            ring.add(LatLng(centerLat + dLat * Math.sin(a), centerLon + dLon * Math.cos(a)))
        }
        ring.add(ring.first())
        return ring
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
