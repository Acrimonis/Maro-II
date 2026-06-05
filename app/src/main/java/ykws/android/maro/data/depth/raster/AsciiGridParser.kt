package ykws.android.maro.data.depth.raster

import ykws.android.maro.data.model.BoundingBox
import ykws.android.maro.data.model.DepthSource

/**
 * Parses an **ESRI/Arc ASCII grid** (`.asc`) into a [SourceRaster]. This is the
 * on-device "no binary parser" path the design commits to: EMODnet WCS and SHOM both
 * offer ESRI ASCII output, which is plain text.
 *
 * Header (case-insensitive), then `nrows` lines of `ncols` numbers, **north row first**:
 * ```
 * ncols 4
 * nrows 3
 * xllcorner 7.00     (or xllcenter)
 * yllcorner 43.50    (or yllcenter)
 * cellsize 0.01
 * NODATA_value -9999
 * ```
 *
 * ESRI rows run north→south, so we flip vertically to the project convention (row 0 = south).
 * Values equal to NODATA become `Float.NaN`. If [negate] is true (e.g. EMODnet stores
 * **elevation**, negative below sea level), each value is negated so the raster holds
 * **depth positive-down**.
 */
object AsciiGridParser {

    fun parse(
        text: String,
        source: DepthSource,
        resM: Double,
        negate: Boolean = false
    ): SourceRaster {
        var ncols = -1
        var nrows = -1
        var xll = Double.NaN
        var yll = Double.NaN
        var xCenter = false
        var yCenter = false
        var cellSize = Double.NaN
        var noData = -9999.0

        val tokens = ArrayList<String>()
        for (rawLine in text.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            val parts = line.split(Regex("\\s+"))
            val key = parts[0].lowercase()
            when (key) {
                "ncols" -> ncols = parts[1].toInt()
                "nrows" -> nrows = parts[1].toInt()
                "xllcorner" -> { xll = parts[1].toDouble(); xCenter = false }
                "xllcenter" -> { xll = parts[1].toDouble(); xCenter = true }
                "yllcorner" -> { yll = parts[1].toDouble(); yCenter = false }
                "yllcenter" -> { yll = parts[1].toDouble(); yCenter = true }
                "cellsize" -> cellSize = parts[1].toDouble()
                "nodata_value" -> noData = parts[1].toDouble()
                else -> tokens.addAll(parts)  // data row
            }
        }

        require(ncols > 0 && nrows > 0 && cellSize > 0.0 && !xll.isNaN() && !yll.isNaN()) {
            "Malformed ASCII grid header (ncols=$ncols nrows=$nrows cellsize=$cellSize)"
        }
        require(tokens.size >= ncols * nrows) {
            "ASCII grid has ${tokens.size} values, expected ${ncols * nrows}"
        }

        // Normalise lower-left to the south/west cell EDGE.
        val latSouth = if (yCenter) yll - 0.5 * cellSize else yll
        val lonWest = if (xCenter) xll - 0.5 * cellSize else xll
        val bbox = BoundingBox(
            latSouth = latSouth,
            latNorth = latSouth + nrows * cellSize,
            lonWest = lonWest,
            lonEast = lonWest + ncols * cellSize
        )

        val values = FloatArray(ncols * nrows)
        var t = 0
        // ESRI row 0 = north → maps to grid row (nrows-1-er). Row 0 of output = south.
        for (er in 0 until nrows) {
            val gridRow = nrows - 1 - er
            for (c in 0 until ncols) {
                val raw = tokens[t++].toDouble()
                val v = if (raw == noData) Float.NaN
                        else (if (negate) -raw else raw).toFloat()
                values[gridRow * ncols + c] = v
            }
        }

        return SourceRaster(
            bbox = bbox,
            rows = nrows,
            cols = ncols,
            cellSizeDegLat = cellSize,
            cellSizeDegLon = cellSize,
            values = values,
            resM = resM,
            source = source
        )
    }
}
