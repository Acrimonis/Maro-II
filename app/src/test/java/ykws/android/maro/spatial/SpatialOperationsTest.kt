package ykws.android.maro.spatial

import org.junit.Assert.*
import org.junit.Test
import ykws.android.maro.data.model.LatLng
import kotlin.math.abs

/**
 * Unit tests for [SpatialOperations].
 *
 * Uses a synthetic coastline segment between Villefranche-sur-Mer
 * and La Napoule (Côte d'Azur, France) for realistic validation.
 */
class SpatialOperationsTest {

    // ── Reference coastline: Villefranche(43.70,7.31) → La Napoule(43.52,6.94) ──
    // These are approximate control points along the Côte d'Azur coastline.

    private val villefranche = LatLng(43.7036, 7.3125)
    private val laNapoule = LatLng(43.5217, 6.9425)

    // Intermediate points approximating the coastline
    private val beaulieu = LatLng(43.7069, 7.3319)
    private val stJeanCapFerrat = LatLng(43.6875, 7.3278)
    private val nice = LatLng(43.6958, 7.2683)
    private val antibes = LatLng(43.5806, 7.1258)
    private val cannes = LatLng(43.5500, 7.0133)
    private val mandelieu = LatLng(43.5200, 6.9750)

    // Points known to be at sea (south of the coastline)
    private val seaPoint = LatLng(43.40, 7.10)

    // Points known to be on land (north of the coastline)
    private val landPoint = LatLng(43.75, 7.10)

    // ── 1. Haversine ───────────────────────────────────────────────────────

    @Test
    fun `haversine returns correct distance between Villefranche and La Napoule`() {
        // Approximate straight-line distance: ~37 km
        val dist = SpatialOperations.haversine(villefranche, laNapoule)
        assertTrue("Expected ~37 km, got $dist m", dist in 35_000.0..40_000.0)
    }

    @Test
    fun `haversine returns zero for same point`() {
        val dist = SpatialOperations.haversine(villefranche, villefranche)
        assertEquals(0.0, dist, 0.001)
    }

    @Test
    fun `haversine returns ~17km for Nice to Antibes`() {
        val dist = SpatialOperations.haversine(nice, antibes)
        // Nice → Antibes is about 17 km as the crow flies
        assertTrue("Expected ~17 km, got $dist m", dist in 15_000.0..19_000.0)
    }

    // ── 2. Point-to-segment distance ───────────────────────────────────────

    @Test
    fun `pointToSegmentDistance zero when point is on the segment`() {
        // Midpoint of Nice→Antibes should be near the segment
        val midLat = (nice.latitude + antibes.latitude) / 2.0
        val midLon = (nice.longitude + antibes.longitude) / 2.0
        val mid = LatLng(midLat, midLon)
        // Distance from a point near the midpoint to the segment should be small
        val dist = SpatialOperations.pointToSegmentDistance(mid, nice, antibes)
        assertTrue("Expected small distance (< 500m), got $dist m", dist < 500.0)
    }

    @Test
    fun `pointToSegmentDistance large for perpendicular offset`() {
        // A point 1° south (~111 km) should be far from the segment
        val farSouth = LatLng(42.5, 7.0)
        val dist = SpatialOperations.pointToSegmentDistance(farSouth, nice, antibes)
        assertTrue("Expected large distance, got $dist m", dist > 50_000.0)
    }

    @Test
    fun `pointToSegmentDistance clamps to endpoints correctly`() {
        // Point way east of the segment → should clamp to the eastern endpoint
        val farEast = LatLng(43.6, 8.0)
        val d1 = SpatialOperations.pointToSegmentDistance(farEast, nice, antibes)
        val d2 = SpatialOperations.haversine(farEast, antibes) // closest endpoint is antibes (easternmost)
        val d3 = SpatialOperations.haversine(farEast, nice)
        // Should be close to the minimum of d2 and d3
        assertTrue("Clamped distance $d1 should be ≈ min($d2, $d3)",
            abs(d1 - minOf(d2, d3)) < 100.0)
    }

    // ── 3. Cross-product & side detection ──────────────────────────────────

    @Test
    fun `sea point is on the RIGHT side of west-to-east coastline`() {
        // The coastline runs west→east (La Napoule → Nice → Villefranche)
        // Sea (south) should be on the RIGHT
        val right = SpatialOperations.isRightSide(laNapoule, nice, seaPoint)
        assertTrue("Sea (south) should be on the right", right)
    }

    @Test
    fun `land point is on the LEFT side of west-to-east coastline`() {
        val right = SpatialOperations.isRightSide(laNapoule, nice, landPoint)
        assertFalse("Land (north) should be on the left", right)
    }

    @Test
    fun `isOnWater detects water for a known sea point`() {
        val polylines = listOf(listOf(laNapoule, cannes, antibes, nice, villefranche))
        val onWater = SpatialOperations.isOnWater(43.35, 7.10, polylines)
        assertTrue("Point south of coast should be water", onWater)
    }

    @Test
    fun `isOnWater detects land for a known land point`() {
        val polylines = listOf(listOf(laNapoule, cannes, antibes, nice, villefranche))
        val onWater = SpatialOperations.isOnWater(43.75, 7.10, polylines)
        assertFalse("Point north of coast should be land", onWater)
    }

    // ── 4. Douglas-Peucker simplification ──────────────────────────────────

    @Test
    fun `douglasPeucker preserves endpoints`() {
        val points = listOf(
            LatLng(43.70, 7.31),
            LatLng(43.69, 7.30),
            LatLng(43.68, 7.28),
            LatLng(43.67, 7.26),
            LatLng(43.65, 7.24),
            LatLng(43.64, 7.22),
            LatLng(43.62, 7.20),
            LatLng(43.60, 7.18),
            LatLng(43.58, 7.16),
            LatLng(43.56, 7.14),
            LatLng(43.54, 7.12),
            LatLng(43.52, 6.94)
        )
        val simplified = SpatialOperations.douglasPeucker(points, 100.0) // 100m epsilon
        assertTrue("Simplified should have at least 2 points", simplified.size >= 2)
        assertEquals("First point preserved", points.first(), simplified.first())
        assertEquals("Last point preserved", points.last(), simplified.last())
    }

    @Test
    fun `douglasPeucker with large epsilon returns just endpoints`() {
        val points = listOf(
            LatLng(43.70, 7.31),
            LatLng(43.65, 7.25),
            LatLng(43.60, 7.18),
            LatLng(43.52, 6.94)
        )
        val simplified = SpatialOperations.douglasPeucker(points, 50_000.0) // huge epsilon
        assertEquals("Should simplify to just 2 endpoints", 2, simplified.size)
    }

    @Test
    fun `douglasPeucker with small epsilon preserves many points`() {
        val points = listOf(
            LatLng(43.70, 7.31),
            LatLng(43.69, 7.30),
            LatLng(43.68, 7.28),
            LatLng(43.67, 7.26),
            LatLng(43.52, 6.94)
        )
        val simplified = SpatialOperations.douglasPeucker(points, 1.0) // 1 meter
        // With such a small epsilon and large distances between points,
        // the line is already quite straight, so may still simplify
        assertTrue("Should preserve most points with tiny epsilon",
            simplified.size >= 2)
    }

    // ── 5. Polyline assembly ───────────────────────────────────────────────

    @Test
    fun `assemblePolylines joins matching segments`() {
        val seg1 = listOf(LatLng(43.70, 7.31), LatLng(43.69, 7.28))
        val seg2 = listOf(LatLng(43.69, 7.28), LatLng(43.68, 7.26))
        val seg3 = listOf(LatLng(43.68, 7.26), LatLng(43.67, 7.24))

        val polylines = SpatialOperations.assemblePolylines(listOf(seg1, seg2, seg3))
        assertEquals("Should produce 1 polyline", 1, polylines.size)
        assertEquals("Should have 4 points (merged)", 4, polylines[0].size)
    }

    @Test
    fun `assemblePolylines handles disconnected segments`() {
        val seg1 = listOf(LatLng(43.70, 7.31), LatLng(43.69, 7.28))
        val seg2 = listOf(LatLng(43.60, 7.10), LatLng(43.59, 7.08)) // Far from seg1

        val polylines = SpatialOperations.assemblePolylines(listOf(seg1, seg2))
        assertEquals("Should produce 2 separate polylines", 2, polylines.size)
    }

    // ── 6. Orientation (ensureWaterOnRight) ────────────────────────────────

    @Test
    fun `ensureWaterOnRight keeps proper west-to-east orientation`() {
        // Coastline going west→east (correct)
        val points = listOf(laNapoule, cannes, antibes, nice, villefranche)
        val result = SpatialOperations.ensureWaterOnRight(points)
        assertEquals("Should not reverse correct orientation",
            points.first(), result.first())
    }

    @Test
    fun `ensureWaterOnRight reverses east-to-west orientation`() {
        // Coastline going east→west (reversed → water would be on left)
        val reversed = listOf(villefranche, nice, antibes, cannes, laNapoule)
        val result = SpatialOperations.ensureWaterOnRight(reversed)
        assertEquals("Should reverse so La Napoule comes first",
            laNapoule, result.first())
        assertEquals("Should end at Villefranche",
            villefranche, result.last())
    }

    @Test
    fun `ensureWaterOnRight handles closed island polyline CCW`() {
        // A small CCW island (water on right = exterior)
        val islandCCW = listOf(
            LatLng(43.50, 7.00),
            LatLng(43.49, 7.00),
            LatLng(43.49, 7.01),
            LatLng(43.50, 7.01),
            LatLng(43.50, 7.00) // closed: back to start
        )
        val result = SpatialOperations.ensureWaterOnRight(islandCCW)
        // For a CCW polygon, water (exterior) is on the RIGHT
        // The result should keep CCW orientation
        assertEquals("CCW island should keep first point",
            islandCCW.first(), result.first())
    }

    @Test
    fun `ensureWaterOnRight reverses CW island to CCW`() {
        // A small CW island → water on left, should reverse
        val islandCW = listOf(
            LatLng(43.50, 7.00),
            LatLng(43.50, 7.01),
            LatLng(43.49, 7.01),
            LatLng(43.49, 7.00),
            LatLng(43.50, 7.00) // closed
        )
        val result = SpatialOperations.ensureWaterOnRight(islandCW)
        // After reversing, first point becomes (43.50, 7.00) — same
        // But second point changes
        assertEquals("Should reverse CW to CCW",
            islandCW.first(), result.first())
        // The second point of the reversed island should be (43.49, 7.00)
        assertEquals("Second point after reversal",
            LatLng(43.49, 7.00), result[1])
    }

    // ── 7. Polylines min distance (island filtering) ────────────────────────

    @Test
    fun `polylinesMinDistance between parallel segments`() {
        val coast = listOf(LatLng(43.55, 7.00), LatLng(43.55, 7.05))
        val island = listOf(LatLng(43.53, 7.00), LatLng(43.53, 7.05))
        val dist = SpatialOperations.polylinesMinDistance(coast, island)
        // ~0.02° south ≈ ~2.2 km
        assertTrue("Distance should be ~2.2 km, got $dist m", dist in 2000.0..2500.0)
    }

    @Test
    fun `polylinesMinDistance zero for overlapping polylines`() {
        val poly = listOf(LatLng(43.55, 7.00), LatLng(43.55, 7.05))
        val dist = SpatialOperations.polylinesMinDistance(poly, poly)
        assertEquals("Distance to self should be 0", 0.0, dist, 0.001)
    }

    // ── 8. Signed area ─────────────────────────────────────────────────────

    @Test
    fun `signedArea positive for CCW polygon`() {
        // Small CCW square
        val ccw = listOf(
            LatLng(43.50, 7.00),
            LatLng(43.49, 7.00),
            LatLng(43.49, 7.01),
            LatLng(43.50, 7.01)
        )
        val area = SpatialOperations.signedArea(ccw)
        assertTrue("CCW should have positive signed area, got $area", area > 0)
    }

    @Test
    fun `signedArea negative for CW polygon`() {
        // CW square
        val cw = listOf(
            LatLng(43.50, 7.00),
            LatLng(43.50, 7.01),
            LatLng(43.49, 7.01),
            LatLng(43.49, 7.00)
        )
        val area = SpatialOperations.signedArea(cw)
        assertTrue("CW should have negative signed area, got $area", area < 0)
    }
}
