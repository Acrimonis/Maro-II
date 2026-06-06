package ykws.android.maro.spatial

import org.junit.Assert.*
import org.junit.Test
import ykws.android.maro.data.model.LatLng

/**
 * Tests for the Zone300 geometry primitives added to [SpatialOperations]:
 * Chaikin smoothing and marching-squares contour extraction.
 */
class SpatialOperationsSmoothingTest {

    // ── Chaikin ──────────────────────────────────────────────────────────────

    @Test
    fun `chaikin open preserves endpoints`() {
        val pts = listOf(LatLng(0.0, 0.0), LatLng(0.0, 1.0), LatLng(1.0, 1.0))
        val out = SpatialOperations.chaikin(pts, iterations = 1, closed = false)
        assertEquals(pts.first(), out.first())
        assertEquals(pts.last(), out.last())
        // open pass: 2 + 2*(n-1) = 2n
        assertEquals(2 * pts.size, out.size)
    }

    @Test
    fun `chaikin closed doubles vertex count and stays closed-ish`() {
        val ring = listOf(LatLng(0.0, 0.0), LatLng(0.0, 2.0), LatLng(2.0, 2.0), LatLng(2.0, 0.0))
        val out = SpatialOperations.chaikin(ring, iterations = 1, closed = true)
        assertEquals(2 * ring.size, out.size)
        // No vertex should leave the bounding box of the original ring (convex hull contains it)
        for (p in out) {
            assertTrue(p.latitude in -0.001..2.001)
            assertTrue(p.longitude in -0.001..2.001)
        }
    }

    @Test
    fun `chaikin smooths the midpoint corner inward`() {
        // A sharp 90-degree corner at (0,1); after smoothing the corner vertex is replaced
        val pts = listOf(LatLng(0.0, 0.0), LatLng(0.0, 1.0), LatLng(1.0, 1.0))
        val out = SpatialOperations.chaikin(pts, iterations = 2, closed = false)
        // The exact original corner (0.0, 1.0) should no longer be present
        assertFalse(out.any { it.latitude == 0.0 && it.longitude == 1.0 })
    }

    @Test
    fun `chaikin no-op for tiny input`() {
        val pts = listOf(LatLng(0.0, 0.0), LatLng(1.0, 1.0))
        assertEquals(pts, SpatialOperations.chaikin(pts, 3, false))
    }

    // ── Marching squares ─────────────────────────────────────────────────────

    /** Build a row-major mask from a list of "....X..." style rows (X = true). */
    private fun maskOf(vararg rowsStr: String): Triple<BooleanArray, Int, Int> {
        val rows = rowsStr.size
        val cols = rowsStr[0].length
        val m = BooleanArray(rows * cols)
        for (r in 0 until rows) for (c in 0 until cols) {
            m[r * cols + c] = rowsStr[r][c] != '.'
        }
        return Triple(m, cols, rows)
    }

    @Test
    fun `marchingSquares single true cell yields one 4-vertex ring`() {
        val (m, cols, rows) = maskOf(
            "...",
            ".X.",
            "..."
        )
        val rings = SpatialOperations.marchingSquares(m, cols, rows)
        assertEquals(1, rings.size)
        assertEquals(4, rings[0].size)
    }

    @Test
    fun `marchingSquares empty mask yields no rings`() {
        val (m, cols, rows) = maskOf(
            "...",
            "...",
            "..."
        )
        assertTrue(SpatialOperations.marchingSquares(m, cols, rows).isEmpty())
    }

    @Test
    fun `marchingSquares full interior yields one ring`() {
        // false border guarantees closure; the 3x3 true block produces one outer ring
        val (m, cols, rows) = maskOf(
            ".....",
            ".XXX.",
            ".XXX.",
            ".XXX.",
            "....."
        )
        val rings = SpatialOperations.marchingSquares(m, cols, rows)
        assertEquals(1, rings.size)
    }

    @Test
    fun `marchingSquares donut yields two rings of opposite winding`() {
        // Ring of true with a false hole in the middle → outer + inner contour
        val (m, cols, rows) = maskOf(
            ".....",
            ".XXX.",
            ".X.X.",
            ".XXX.",
            "....."
        )
        val rings = SpatialOperations.marchingSquares(m, cols, rows)
        assertEquals(2, rings.size)
        val a = signedArea(rings[0])
        val b = signedArea(rings[1])
        assertTrue("expected opposite winding, got $a and $b", a * b < 0.0)
    }

    private fun signedArea(ring: List<SpatialOperations.GridPt>): Double {
        var s = 0.0
        for (i in ring.indices) {
            val p = ring[i]
            val q = ring[(i + 1) % ring.size]
            s += p.col * q.row - q.col * p.row
        }
        return s / 2.0
    }
}
