package ykws.android.maro.spatial

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

/**
 * Unit tests for [SpatialOperations.marchingSquaresScalar] and [gridLineToLatLng].
 *
 * The field is row-major (`field[r*cols + c]`), row 0 = south. A node is "inside"
 * when `field >= level`; crossings are linearly interpolated along grid edges.
 */
class MarchingSquaresScalarTest {

    private fun field(rows: Int, cols: Int, vararg values: Float): FloatArray {
        require(values.size == rows * cols)
        return values
    }

    // ── 1. Edge interpolation is exact ──────────────────────────────────────

    @Test
    fun `single cell interpolates crossings at the correct fraction`() {
        // 2x2 grid, one cell. Only bottom-right corner (10) is >= level 5.
        // row0(south): 0 0 ; row1(north): 0 10
        val f = field(2, 2, 0f, 0f, 0f, 10f)
        val lines = SpatialOperations.marchingSquaresScalar(f, cols = 2, rows = 2, level = 5.0)

        assertEquals("one contour segment", 1, lines.size)
        val seg = lines.first()
        assertEquals("two endpoints", 2, seg.size)

        // Right edge crossing: between (r0,c1)=0 and (r1,c1)=10 → frac 0.5 → (col 1, row 0.5)
        // Bottom edge crossing: between (r1,c0)=0 and (r1,c1)=10 → frac 0.5 → (col 0.5, row 1)
        val cols = seg.map { it.col }.sorted()
        val rows = seg.map { it.row }.sorted()
        assertEquals(0.5, cols[0], 1e-6)
        assertEquals(1.0, cols[1], 1e-6)
        assertEquals(0.5, rows[0], 1e-6)
        assertEquals(1.0, rows[1], 1e-6)
    }

    @Test
    fun `asymmetric values shift the crossing fraction`() {
        // bottom-right = 20, level 5 → frac = 5/20 = 0.25 along each incident edge.
        val f = field(2, 2, 0f, 0f, 0f, 20f)
        val lines = SpatialOperations.marchingSquaresScalar(f, 2, 2, 5.0)
        val seg = lines.first()
        // right edge node col is exactly 1.0; its row should be 0 + 0.25 = 0.25
        val right = seg.first { abs(it.col - 1.0) < 1e-6 }
        assertEquals(0.25, right.row, 1e-6)
        // bottom edge node row is exactly 1.0; its col should be 0 + 0.25 = 0.25
        val bottom = seg.first { abs(it.row - 1.0) < 1e-6 }
        assertEquals(0.25, bottom.col, 1e-6)
    }

    // ── 2. Uniform fields produce no contour ────────────────────────────────

    @Test
    fun `uniform field below level yields nothing`() {
        val f = FloatArray(25) { 0f }
        assertTrue(SpatialOperations.marchingSquaresScalar(f, 5, 5, 5.0).isEmpty())
    }

    @Test
    fun `uniform field above level yields nothing`() {
        val f = FloatArray(25) { 10f }
        assertTrue(SpatialOperations.marchingSquaresScalar(f, 5, 5, 5.0).isEmpty())
    }

    // ── 3. A single enclosed basin gives one closed ring ────────────────────

    @Test
    fun `central high block produces one closed contour`() {
        // 5x5: border 0, inner 3x3 = 10. Contour at level 5 encircles the centre.
        val cols = 5; val rows = 5
        val f = FloatArray(rows * cols) { 0f }
        for (r in 1..3) for (c in 1..3) f[r * cols + c] = 10f
        val lines = SpatialOperations.marchingSquaresScalar(f, cols, rows, 5.0)

        assertEquals("one contour", 1, lines.size)
        val ring = lines.first()
        assertTrue("ring has >= 4 vertices", ring.size >= 4)
        // Fully enclosed (no NaN, no boundary touch) ⇒ first vertex ≈ last neighbour wraps:
        // the trace returns to start, so first and last connect — endpoints near each other.
        val first = ring.first(); val last = ring.last()
        val close = abs(first.col - last.col) + abs(first.row - last.row)
        assertTrue("closed loop wraps near its start", close <= 1.5)
    }

    // ── 4. Two separate blobs give two contours ─────────────────────────────

    @Test
    fun `two separated high blobs produce two contours`() {
        // 5 rows x 9 cols, two 3x3 high blocks separated by a column of 0.
        val cols = 9; val rows = 5
        val f = FloatArray(rows * cols) { 0f }
        for (r in 1..3) for (c in 1..3) f[r * cols + c] = 10f
        for (r in 1..3) for (c in 5..7) f[r * cols + c] = 10f
        val lines = SpatialOperations.marchingSquaresScalar(f, cols, rows, 5.0)
        assertEquals("two separate contours", 2, lines.size)
    }

    // ── 5. NaN suppression: cells touching NaN emit no edges ────────────────

    @Test
    fun `NaN corner does not crash and is excluded from contours`() {
        val cols = 5; val rows = 5
        val f = FloatArray(rows * cols) { 0f }
        for (r in 1..3) for (c in 1..3) f[r * cols + c] = 10f
        // Punch a NoData hole at the top-right corner, far from the central contour.
        f[(rows - 1) * cols + (cols - 1)] = Float.NaN

        val lines = SpatialOperations.marchingSquaresScalar(f, cols, rows, 5.0)
        assertTrue("central contour still found", lines.isNotEmpty())
        // No emitted vertex may sit on an edge incident to the NaN cell (col 4 or row 4 area).
        val touchesNaNCell = lines.flatten().any { it.col >= 3.5 && it.row >= 3.5 }
        assertFalse("contours avoid the NaN-adjacent cell", touchesNaNCell)
    }

    // ── 6. Grid → geographic mapping ────────────────────────────────────────

    @Test
    fun `gridLineToLatLng maps cell-centre geometry`() {
        val line = listOf(SpatialOperations.GridPt(col = 0.5, row = 1.0))
        val out = SpatialOperations.gridLineToLatLng(
            line, latSouth = 43.0, lonWest = 7.0,
            cellSizeDegLat = 0.001, cellSizeDegLon = 0.001
        )
        assertEquals(1, out.size)
        // lat = 43 + (1.0 + 0.5)*0.001 ; lon = 7 + (0.5 + 0.5)*0.001
        assertEquals(43.0015, out[0].latitude, 1e-9)
        assertEquals(7.0010, out[0].longitude, 1e-9)
    }
}
