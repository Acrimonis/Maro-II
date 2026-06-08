package ykws.android.maro.data.depth.raster

import ykws.android.maro.data.model.BoundingBox
import ykws.android.maro.data.model.DepthSource
import java.io.File
import java.util.zip.GZIPInputStream

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
 * Values equal to NODATA become `Float.NaN`. If [negate] is true (e.g. EMODnet/Litto3D store
 * **elevation**, negative below sea level), each value is negated so the raster holds
 * **depth positive-down**. [latOffsetM] is the height of the source's vertical datum above LAT
 * (e.g. IGN69 ≈ 0.40 m here); it is subtracted so the output is **depth below LAT** (chart datum)
 * — the conservative reference. Final = (negate ? −raw : raw) − latOffsetM.
 */
object AsciiGridParser {

    fun parse(
        text: String,
        source: DepthSource,
        resM: Double,
        negate: Boolean = false,
        latOffsetM: Double = 0.0
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
                "nodata_value" -> noData = parts[1].toDoubleOrNull() ?: Double.NaN
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
                // Tolerant: NoData written as "nan"/"NaN"/non-numeric → NaN (Kotlin toDouble rejects "nan").
                val raw = tokens[t++].toDoubleOrNull()
                val v = if (raw == null || raw == noData) Float.NaN
                        else ((if (negate) -raw else raw) - latOffsetM).toFloat()
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

    /**
     * **Streaming** variant — parses [file] line-by-line straight into the grid `FloatArray`, never
     * materialising the whole text as a `String` or buffering tokens. Use this for the large baked
     * grids (a corridor-wide Litto3D `.asc` can be ~1 GB of mostly-nodata text); the [String] overload
     * would OOM. Peak memory is just the output `FloatArray` (~4 bytes/cell). Semantics are identical.
     */
    fun parse(
        file: File,
        source: DepthSource,
        resM: Double,
        negate: Boolean = false,
        latOffsetM: Double = 0.0
    ): SourceRaster {
        var ncols = -1
        var nrows = -1
        var xll = Double.NaN
        var yll = Double.NaN
        var xCenter = false
        var yCenter = false
        var cellSize = Double.NaN
        var noData = -9999.0
        var values: FloatArray? = null
        var n = 0
        var t = 0
        val ws = Regex("\\s+")

        // Transparently read a gzipped grid (`.asc.gz`): the big Litto3D grid is mostly nodata text
        // that compresses ~50-100x, so the bake ships it gzipped. Streaming keeps memory proportional
        // to the output array, not the (decompressed) text.
        val reader = if (file.name.endsWith(".gz", ignoreCase = true))
            GZIPInputStream(file.inputStream().buffered()).bufferedReader()
        else file.bufferedReader()
        reader.useLines { seq ->
            for (rawLine in seq) {
                val line = rawLine.trim()
                if (line.isEmpty()) continue
                val parts = line.split(ws)
                when (parts[0].lowercase()) {
                    "ncols" -> ncols = parts[1].toInt()
                    "nrows" -> nrows = parts[1].toInt()
                    "xllcorner" -> { xll = parts[1].toDouble(); xCenter = false }
                    "xllcenter" -> { xll = parts[1].toDouble(); xCenter = true }
                    "yllcorner" -> { yll = parts[1].toDouble(); yCenter = false }
                    "yllcenter" -> { yll = parts[1].toDouble(); yCenter = true }
                    "cellsize" -> cellSize = parts[1].toDouble()
                    "nodata_value" -> noData = parts[1].toDoubleOrNull() ?: Double.NaN
                    else -> {
                        if (values == null) {                       // first data line → allocate
                            require(ncols > 0 && nrows > 0 && cellSize > 0.0 && !xll.isNaN() && !yll.isNaN()) {
                                "Malformed ASCII grid header (ncols=$ncols nrows=$nrows cellsize=$cellSize)"
                            }
                            n = ncols * nrows
                            values = FloatArray(n)
                        }
                        val vals = values!!
                        for (tok in parts) {
                            if (t >= n) break                        // ignore any trailing tokens
                            val raw = tok.toDoubleOrNull()
                            val v = if (raw == null || raw == noData) Float.NaN
                                    else ((if (negate) -raw else raw) - latOffsetM).toFloat()
                            // ESRI row 0 = north → flip to grid row 0 = south.
                            val gridRow = nrows - 1 - (t / ncols)
                            vals[gridRow * ncols + (t % ncols)] = v
                            t++
                        }
                    }
                }
            }
        }

        val vals = values ?: error("ASCII grid '${file.name}' had no data rows")
        require(t >= n) { "ASCII grid '${file.name}' has $t values, expected $n" }

        val latSouth = if (yCenter) yll - 0.5 * cellSize else yll
        val lonWest = if (xCenter) xll - 0.5 * cellSize else xll
        return SourceRaster(
            bbox = BoundingBox(
                latSouth = latSouth,
                latNorth = latSouth + nrows * cellSize,
                lonWest = lonWest,
                lonEast = lonWest + ncols * cellSize
            ),
            rows = nrows,
            cols = ncols,
            cellSizeDegLat = cellSize,
            cellSizeDegLon = cellSize,
            values = vals,
            resM = resM,
            source = source
        )
    }
}
