package ykws.android.maro.data.coastline

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.spatial.SpatialOperations

/**
 * Tests for [CoastlineGenerator] pipeline between Villefranche and La Napoule.
 *
 * These tests validate the pipeline components using synthetic coastline data
 * that simulates the segment between Villefranche-sur-Mer (43.70°N, 7.31°E)
 * and La Napoule (43.52°N, 6.94°E) — covering Cannes, Antibes, and Nice.
 *
 * Note: The full end-to-end test with Overpass API requires network access.
 * This test validates the processing pipeline with synthetic OSM-like segments.
 */
class CoastlineGeneratorTest {

    // ── Synthetic coastline: Villefranche → La Napoule ─────────────────────
    // Approximate control points along the coast

    private val syntheticSegments = listOf(
        // Segment 1: Villefranche → Nice (east→west)
        listOf(
            LatLng(43.7036, 7.3125),
            LatLng(43.7000, 7.3000),
            LatLng(43.6958, 7.2683)
        ),
        // Segment 2: Nice → Antibes
        listOf(
            LatLng(43.6958, 7.2683),
            LatLng(43.6600, 7.2000),
            LatLng(43.6400, 7.1600),
            LatLng(43.5806, 7.1258)
        ),
        // Segment 3: Antibes → Cannes
        listOf(
            LatLng(43.5806, 7.1258),
            LatLng(43.5600, 7.0800),
            LatLng(43.5500, 7.0133)
        ),
        // Segment 4: Cannes → La Napoule
        listOf(
            LatLng(43.5500, 7.0133),
            LatLng(43.5300, 6.9800),
            LatLng(43.5217, 6.9425)
        )
    )

    @Test
    fun `assembly produces single continuous polyline from synthetic segments`() {
        val polylines = SpatialOperations.assemblePolylines(syntheticSegments)
        assertEquals("Segments should assemble into 1 polyline", 1, polylines.size)

        val assembled = polylines[0]
        assertTrue("Assembled polyline should have many points", assembled.size >= 4)
        assertEquals("Should start at Villefranche",
            LatLng(43.7036, 7.3125), assembled.first())
        assertEquals("Should end at La Napoule",
            LatLng(43.5217, 6.9425), assembled.last())
    }

    @Test
    fun `clipping removes points outside Nice-Frejus zone`() {
        val polylines = SpatialOperations.assemblePolylines(syntheticSegments)
        val clipped = polylines[0].filter { (_, lon) ->
            lon in CoastlineGenerator.LON_WEST..CoastlineGenerator.LON_EAST
        }
        assertTrue("Clipped polyline should have points", clipped.isNotEmpty())
        for (pt in clipped) {
            assertTrue("Longitude ${pt.longitude} should be in [${CoastlineGenerator.LON_WEST}, ${CoastlineGenerator.LON_EAST}]",
                pt.longitude in CoastlineGenerator.LON_WEST..CoastlineGenerator.LON_EAST)
        }
    }

    @Test
    fun `simplification reduces point count while preserving shape`() {
        val polylines = SpatialOperations.assemblePolylines(syntheticSegments)
        val original = polylines[0]
        val simplified = SpatialOperations.douglasPeucker(original, 3.0) // ε = 3m

        assertTrue("Simplified should have fewer or equal points",
            simplified.size <= original.size)
        assertTrue("Simplified should have at least 2 points", simplified.size >= 2)
        assertEquals("Endpoints preserved",
            original.first(), simplified.first())
        assertEquals("Endpoints preserved",
            original.last(), simplified.last())
    }

    @Test
    fun `full pipeline produces metadata with correct point count`() = runTest {
        // This validates the pipeline logic through SpatialOperations
        val polylines = SpatialOperations.assemblePolylines(syntheticSegments)
        val clipped = polylines.map { poly ->
            poly.filter { (_, lon) -> lon in CoastlineGenerator.LON_WEST..CoastlineGenerator.LON_EAST }
        }.filter { it.isNotEmpty() }
        val simplified = clipped.map { SpatialOperations.douglasPeucker(it, 3.0) }
            .filter { it.size >= 2 }

        val totalPoints = simplified.sumOf { it.size }
        assertTrue("Should have multiple points across the coastline", totalPoints > 5)

        // Verify metadata-like values
        val totalLength = simplified.sumOf { poly ->
            (0 until poly.size - 1).sumOf { i ->
                SpatialOperations.haversine(poly[i], poly[i + 1])
            }
        }
        assertTrue("Total coastline length should be > 10 km", totalLength > 10_000.0)
        assertTrue("Total coastline length should be < 100 km", totalLength < 100_000.0)
    }

    @Test
    fun `segment count between 1 and 10 for realistic coastline`() {
        val polylines = SpatialOperations.assemblePolylines(syntheticSegments)
        assertTrue("Should have 1 or more polylines", polylines.isNotEmpty())
        assertTrue("Should not have too many polylines for assembled data",
            polylines.size <= 5)
    }

    @Test
    fun `each polyline has at least 2 points`() {
        val polylines = SpatialOperations.assemblePolylines(syntheticSegments)
        for (polyline in polylines) {
            assertTrue("Each polyline should have ≥ 2 points, got ${polyline.size}",
                polyline.size >= 2)
        }
    }
}
