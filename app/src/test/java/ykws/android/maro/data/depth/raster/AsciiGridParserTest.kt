package ykws.android.maro.data.depth.raster

import org.junit.Assert.*
import org.junit.Test
import ykws.android.maro.data.model.DepthSource

class AsciiGridParserTest {

    // 4 cols x 3 rows; north row first. NODATA = -9999. cellsize 0.01, SW corner (7.00, 43.50).
    private val grid = """
        ncols 4
        nrows 3
        xllcorner 7.00
        yllcorner 43.50
        cellsize 0.01
        NODATA_value -9999
        -10 -11 -12 -13
        -20 -21 -9999 -23
        -30 -31 -32 -33
    """.trimIndent()

    @Test
    fun `parses header, bbox and dimensions`() {
        val r = AsciiGridParser.parse(grid, DepthSource.EMODNET, resM = 115.0, negate = true)
        assertEquals(4, r.cols)
        assertEquals(3, r.rows)
        assertEquals(0.01, r.cellSizeDegLat, 1e-12)
        assertEquals(7.00, r.bbox.lonWest, 1e-9)
        assertEquals(43.50, r.bbox.latSouth, 1e-9)
        assertEquals(43.53, r.bbox.latNorth, 1e-9)
        assertEquals(7.04, r.bbox.lonEast, 1e-9)
        assertEquals(DepthSource.EMODNET, r.source)
    }

    @Test
    fun `flips rows north-to-south and negates elevation to depth`() {
        val r = AsciiGridParser.parse(grid, DepthSource.EMODNET, resM = 115.0, negate = true)
        // South row (grid row 0) is the ESRI last line: -30 -31 -32 -33 → depth 30..33.
        assertEquals(30f, r.values[r.idx(0, 0)], 1e-4f)
        assertEquals(33f, r.values[r.idx(0, 3)], 1e-4f)
        // North row (grid row 2) is the ESRI first line: -10.. → depth 10..13.
        assertEquals(10f, r.values[r.idx(2, 0)], 1e-4f)
        assertEquals(13f, r.values[r.idx(2, 3)], 1e-4f)
    }

    @Test
    fun `NODATA becomes NaN`() {
        val r = AsciiGridParser.parse(grid, DepthSource.EMODNET, resM = 115.0, negate = true)
        // middle ESRI row "-20 -21 -9999 -23" → grid row 1, col 2 is NODATA.
        assertTrue(r.values[r.idx(1, 2)].isNaN())
        assertEquals(20f, r.values[r.idx(1, 0)], 1e-4f)
    }

    @Test
    fun `latOffsetM converts the source datum to LAT (subtracted after negate)`() {
        // Litto3D: elevation rel. IGN69, negate→depth, then −0.40 → depth below LAT.
        val r = AsciiGridParser.parse(grid, DepthSource.LITTO3D, resM = 1.0, negate = true, latOffsetM = 0.40)
        // South row "-30..": depth below IGN69 = 30 → below LAT = 30 − 0.40 = 29.60.
        assertEquals(29.60f, r.values[r.idx(0, 0)], 1e-3f)
        // North row "-10..": 10 − 0.40 = 9.60.
        assertEquals(9.60f, r.values[r.idx(2, 0)], 1e-3f)
    }

    @Test
    fun `xllcenter shifts origin by half a cell`() {
        val centered = grid.replace("xllcorner 7.00", "xllcenter 7.005")
            .replace("yllcorner 43.50", "yllcenter 43.505")
        val r = AsciiGridParser.parse(centered, DepthSource.EMODNET, resM = 115.0)
        assertEquals(7.00, r.bbox.lonWest, 1e-9)
        assertEquals(43.50, r.bbox.latSouth, 1e-9)
    }
}
