package ykws.android.maro.spatial

import org.junit.Assert.*
import org.junit.Test
import ykws.android.maro.data.model.CoastlinePoint
import ykws.android.maro.data.model.CoastlineSegment
import ykws.android.maro.data.model.LatLng

/**
 * Correctness tests for [CoastlineSpatialIndex.isWater] — the **closed-polygon
 * containment** water/land classifier that replaced the open-polyline south-ray.
 *
 * The strong tests (`matches ground truth …`) sweep a grid of points and assert that
 * `isWater(p)` equals `!insideLandPolygon(p)`, where the land polygon is the coastline
 * closed by an inland cap and `insideLandPolygon` is an **independent** ray-cast
 * point-in-polygon oracle. This guards the whole pipeline (column gather → north-ray →
 * cap → ring detection) against an algorithm that does not share its code.
 *
 * The fragmented variant feeds the SAME shore as two separate open polylines — the case
 * the old `polylineIdx == 0`-only mainland parity got wrong (a fragment left a land-side
 * band). Containment closes all open pieces with one shared cap, so it must still match.
 *
 * NOTE: synthetic tests are necessary but NOT sufficient for the land-mirror invariant —
 * the real coast must still be validated against an oracle (EMODnet) on device. See the
 * Zone300 feature 'land-mirror' todo and `memory/zone300-land-mirror.md`.
 */
class CoastlineSpatialIndexWaterTest {

    private fun seg(points: List<LatLng>, mainland: Boolean, closed: Boolean) =
        CoastlineSegment(
            osmWayId = 0L,
            points = points.map { CoastlinePoint(it.latitude.toFloat(), it.longitude.toFloat()) },
            isMainland = mainland,
            isClosed = closed
        )

    private fun indexOf(vararg segments: CoastlineSegment) =
        CoastlineSpatialIndex(segments.toList(), cellSizeM = 50.0)

    /** Independent even-odd point-in-polygon oracle (horizontal ray) — NOT shared with
     *  the index's vertical-ray + cap implementation. */
    private fun insidePolygon(p: LatLng, poly: List<LatLng>): Boolean {
        var inside = false
        var j = poly.size - 1
        for (i in poly.indices) {
            val xi = poly[i].longitude; val yi = poly[i].latitude
            val xj = poly[j].longitude; val yj = poly[j].latitude
            if (((yi > p.latitude) != (yj > p.latitude)) &&
                (p.longitude < (xj - xi) * (p.latitude - yi) / (yj - yi) + xi)
            ) inside = !inside
            j = i
        }
        return inside
    }

    /** Closes an open W→E shore into a land polygon with an inland (north) cap. */
    private fun landPolygon(shore: List<LatLng>, capLat: Double = 43.99): List<LatLng> =
        shore + listOf(
            LatLng(capLat, shore.last().longitude),
            LatLng(capLat, shore.first().longitude)
        )

    /** Sweeps a grid and asserts containment == oracle everywhere clear of the coast. */
    private fun assertMatchesGroundTruth(index: CoastlineSpatialIndex, gt: List<LatLng>) {
        var checked = 0
        var mismatches = 0
        var lat = 43.461
        while (lat <= 43.620) {
            var lon = 7.005
            while (lon <= 7.095) {
                // Skip points hugging the coast: classification there is exact but a point
                // landing on the line is genuinely ambiguous and not what we're testing.
                if (index.query(lat, lon).distanceMeters >= 30.0) {
                    val water = index.isWater(lat, lon)
                    val truth = !insidePolygon(LatLng(lat, lon), gt)
                    if (water != truth) {
                        mismatches++
                        if (mismatches <= 5) {
                            println("isWater mismatch @ ($lat, $lon): got water=$water, truth water=$truth")
                        }
                    }
                    checked++
                }
                lon += 0.004
            }
            lat += 0.004
        }
        assertTrue("sweep too sparse ($checked points)", checked > 800)
        assertEquals("containment disagreed with the point-in-polygon oracle", 0, mismatches)
    }

    /** A non-trivial coast: E–W runs, two steep ~N–S walls (a step), a diagonal. */
    private val complexShore = listOf(
        LatLng(43.55, 7.000),
        LatLng(43.55, 7.020),
        LatLng(43.50, 7.020),   // N–S wall down
        LatLng(43.50, 7.035),
        LatLng(43.56, 7.035),   // N–S wall up
        LatLng(43.56, 7.050),
        LatLng(43.52, 7.070),   // diagonal
        LatLng(43.55, 7.100)
    )

    // ── Baseline ──────────────────────────────────────────────────────────────

    @Test
    fun `straight coast — south is water, north is land`() {
        val coast = (0..20).map { LatLng(43.50, 7.00 + it * 0.005) }   // E–W, ~8 km
        val index = indexOf(seg(coast, mainland = true, closed = false))
        assertTrue("200 m south of coast = water", index.isWater(43.50 - 0.002, 7.05))
        assertFalse("200 m north of coast = land", index.isWater(43.50 + 0.002, 7.05))
    }

    // ── Islands ─────────────────────────────────────────────────────────────────

    @Test
    fun `island ring — inside is land, just outside and the gap are water`() {
        val coast = (0..20).map { LatLng(43.50, 7.00 + it * 0.005) }       // E–W mainland, land to the north
        // Island sits in the WATER (south of the mainland coast).
        val s = 43.460; val n = 43.468; val w = 7.040; val e = 7.050
        val ring = listOf(                                                 // closed square island
            LatLng(s, w), LatLng(s, e), LatLng(n, e), LatLng(n, w), LatLng(s, w)
        )
        val index = indexOf(
            seg(coast, mainland = true, closed = false),
            seg(ring, mainland = false, closed = true)
        )
        // Inside the island ring → land.
        assertFalse("inside the island = land", index.isWater((s + n) / 2, (w + e) / 2))
        // Open water just south of the island (between it and the open sea).
        assertTrue("water just south of the island", index.isWater(s - 0.004, (w + e) / 2))
        // Just east of the ring, still open water.
        assertTrue("water just east of the island", index.isWater((s + n) / 2, e + 0.004))
    }

    // ── Ground-truth comparison (the rigorous tests) ─────────────────────────────

    @Test
    fun `matches ground truth on a single complex coastline`() {
        val index = indexOf(seg(complexShore, mainland = true, closed = false))
        assertMatchesGroundTruth(index, landPolygon(complexShore))
    }

    @Test
    fun `matches ground truth when the mainland is fragmented into two polylines`() {
        // Same shore, but split into two SEPARATE open polylines (sharing the join vertex).
        // The old polylineIdx==0-only mainland parity would treat the second piece as an
        // "island" and could leave a land-side band; containment closes both with one cap.
        val a = complexShore.subList(0, 5)        // P0..P4
        val b = complexShore.subList(4, complexShore.size)   // P4..P7
        val index = indexOf(
            seg(a, mainland = true, closed = false),
            seg(b, mainland = true, closed = false)
        )
        assertMatchesGroundTruth(index, landPolygon(complexShore))
    }

    // ── Targeted: land beside an N–S wall (the documented south-ray failure) ─────

    @Test
    fun `land between two N-S walls is classified as land, the dip floor's south is water`() {
        val index = indexOf(seg(complexShore, mainland = true, closed = false))
        // Point inside the southward step (between the 7.020 and 7.035 walls, above the
        // 43.50 floor): it is LAND — north of the local shore. A south-ray with the dip
        // floor absent from its column would call it water; containment (cap) says land.
        assertFalse("land north of the dip floor", index.isWater(43.515, 7.027))
        // Well south of the dip floor → open water.
        assertTrue("water south of the dip floor", index.isWater(43.470, 7.027))
    }

    // ── Corner rule (nearest-segment side test) ──────────────────────────────────

    @Test
    fun `convex island corner — point due south of the SE corner is water`() {
        // Regression for the nearest-segment corner rule (cornerWater turn-sign). The nearest
        // boundary point here is the SE vertex; the point is OUTSIDE ⇒ water. With the OR/AND
        // branches swapped this wrongly returned land.
        val s = 43.460; val n = 43.468; val w = 7.040; val e = 7.050
        val ring = listOf(
            LatLng(s, w), LatLng(s, e), LatLng(n, e), LatLng(n, w), LatLng(s, w)
        )
        val coast = (0..20).map { LatLng(43.50, 7.00 + it * 0.005) }
        val index = indexOf(
            seg(coast, mainland = true, closed = false),
            seg(ring, mainland = false, closed = true)
        )
        assertTrue("due south of the SE island corner is open water", index.isWater(s - 0.002, e))
    }

    // ── Data-quality cleaning (isOnWaterAgain residual fixes) ────────────────────

    @Test
    fun `tiny offshore coastline fragment does not flip open water to land`() {
        // Reproduces seg4323: a ~12 m near-vertical scrap floating in open water south of the coast.
        // The index drops sub-30 m open fragments, so water around it stays water on BOTH sides
        // (the scrap's meaningless "side" was making one side land).
        val coast = (0..20).map { LatLng(43.50, 7.00 + it * 0.005) }            // E–W coast, water south
        val scrap = listOf(LatLng(43.4800, 7.05000), LatLng(43.4801, 7.05005)) // ~12 m near-vertical
        val index = indexOf(
            seg(coast, mainland = true, closed = false),
            seg(scrap, mainland = true, closed = false)
        )
        assertTrue("west of the scrap = water", index.isWater(43.4798, 7.04990))
        assertTrue("at the scrap's longitude = water", index.isWater(43.4798, 7.05002))
        assertTrue("east of the scrap = water", index.isWater(43.4798, 7.05010))
    }

    @Test
    fun `degenerate zero-area ring is ignored`() {
        val coast = (0..20).map { LatLng(43.50, 7.00 + it * 0.005) }
        // 3-point, ~zero-area "ring" (first ≈ last) — a noise sliver; must be dropped.
        val degen = listOf(LatLng(43.4800, 7.0500), LatLng(43.4805, 7.0500), LatLng(43.4800, 7.0500))
        val index = indexOf(
            seg(coast, mainland = true, closed = false),
            seg(degen, mainland = false, closed = true)
        )
        assertTrue("near the degenerate ring, open water stays water", index.isWater(43.478, 7.0500))
    }

    @Test
    fun `harbour basin ring (CW) is not treated as a land island`() {
        val coast = (0..20).map { LatLng(43.50, 7.00 + it * 0.005) }
        // Clockwise ring (interior = water basin); excluded from island containment, so it can't
        // turn the surrounding/enclosed water into land.
        val s = 43.470; val n = 43.476; val w = 7.040; val e = 7.050
        val cw = listOf(LatLng(s, w), LatLng(n, w), LatLng(n, e), LatLng(s, e), LatLng(s, w))
        val index = indexOf(
            seg(coast, mainland = true, closed = false),
            seg(cw, mainland = false, closed = true)
        )
        assertTrue("inside a CW basin is water, not land", index.isWater((s + n) / 2, (w + e) / 2))
        assertTrue("south of the CW basin is water", index.isWater(s - 0.004, (w + e) / 2))
    }

    // ── Degenerate ───────────────────────────────────────────────────────────────

    @Test
    fun `empty index is water everywhere`() {
        val index = CoastlineSpatialIndex(emptyList())
        assertTrue(index.isWater(43.5, 7.0))
    }
}
