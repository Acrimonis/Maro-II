package ykws.android.maro.data.depth

import com.google.protobuf.CodedInputStream
import com.google.protobuf.InvalidProtocolBufferException
import com.google.protobuf.WireFormat
import java.io.BufferedInputStream
import ykws.android.maro.data.model.BoundingBox
import ykws.android.maro.data.model.DepthDatum
import ykws.android.maro.data.model.DepthGrid
import ykws.android.maro.data.model.DepthMetadata
import ykws.android.maro.data.model.TierResidual
import ykws.android.maro.data.model.ValidationReport

/**
 * Serialises [DepthGrid] to/from Protocol Buffers binary (schema in
 * `app/src/main/proto/depth.proto`). Mirrors [CoastlineSerializer]: manual builder,
 * packed arrays, javalite. NaN survives the float32 round-trip, preserving NoData.
 *
 * Decoding is streaming rather than `parseFrom` — the packed fields land straight in the final
 * primitive arrays, so the schema and the bundled asset are untouched (see [deserialize]).
 */
object DepthSerializer {

    // Field numbers as declared in `app/src/main/proto/depth.proto` — the streaming decoder below
    // dispatches on them, so they sit beside their only reader.
    private const val F_REGION_ID = 1
    private const val F_LON_WEST = 2
    private const val F_LON_EAST = 3
    private const val F_LAT_SOUTH = 4
    private const val F_LAT_NORTH = 5
    private const val F_ROWS = 6
    private const val F_COLS = 7
    private const val F_CELL_SIZE_DEG_LAT = 8
    private const val F_CELL_SIZE_DEG_LON = 9
    private const val F_DATUM = 10
    private const val F_SOURCE = 11
    private const val F_FETCH_TIMESTAMP_MS = 12
    private const val F_GRID_RES_M = 13
    private const val F_DEPTHS = 14
    private const val F_SOURCE_IDS = 15
    private const val F_CONFIDENCE = 16
    private const val F_VALIDATION = 17

    /** Read-ahead for the asset stream — the decoder consumes the packed fields per element. */
    private const val READ_BUFFER_BYTES = 64 * 1024

    fun serialize(grid: DepthGrid): ByteArray {
        val b = DepthProtos.DepthCache.newBuilder()
            .setRegionId(grid.regionId)
            .setLonWest(grid.boundingBox.lonWest)
            .setLonEast(grid.boundingBox.lonEast)
            .setLatSouth(grid.boundingBox.latSouth)
            .setLatNorth(grid.boundingBox.latNorth)
            .setRows(grid.rows)
            .setCols(grid.cols)
            .setCellSizeDegLat(grid.cellSizeDegLat)
            .setCellSizeDegLon(grid.cellSizeDegLon)
            .setDatum(grid.datum.ordinal)
            .setSource(grid.metadata.source)
            .setFetchTimestampMs(grid.metadata.fetchTimestampMs)
            .setGridResM(grid.metadata.gridResM)

        val depthList = ArrayList<Float>(grid.depths.size)
        for (v in grid.depths) depthList.add(v)
        b.addAllDepths(depthList)

        val srcList = ArrayList<Int>(grid.source.size)
        for (s in grid.source) srcList.add(s.toInt())
        b.addAllSourceIds(srcList)

        val confList = ArrayList<Int>(grid.confidence.size)
        for (cf in grid.confidence) confList.add(cf.toInt() and 0xFF)
        b.addAllConfidence(confList)

        grid.metadata.validation?.let { b.setValidation(validationToProto(it)) }

        return b.build().toByteArray()
    }

    /** Convenience overload — wraps [bytes] in a [java.io.ByteArrayInputStream]. */
    fun deserialize(bytes: ByteArray): DepthGrid = deserialize(java.io.ByteArrayInputStream(bytes))

    /**
     * Streaming decode. `parseFrom` built protobuf's own parallel lists first (4 B/cell each, grown
     * a 1.5× step at a time) and copied them into the final arrays afterwards, so the grid was held
     * twice: at n = 10.35 M cells (nice-menton) that is ≈133 MB of lists against the ≈39 MB of
     * depths the model actually keeps — the depth layer's own OOM. Reading each packed field
     * straight into its final array leaves one copy, ≈6 B/cell.
     */
    fun deserialize(input: java.io.InputStream): DepthGrid {
        val cis = CodedInputStream.newInstance(BufferedInputStream(input, READ_BUFFER_BYTES))

        var regionId = ""
        var lonWest = 0.0
        var lonEast = 0.0
        var latSouth = 0.0
        var latNorth = 0.0
        var rows = 0
        var cols = 0
        var cellSizeDegLat = 0.0
        var cellSizeDegLon = 0.0
        var datumOrdinal = 0
        var sourceLabel = ""
        var fetchTimestampMs = 0L
        var gridResM = 0.0
        var validation: ValidationReport? = null
        var depths: FloatArray? = null
        var sourceIds: ByteArray? = null
        var confidence: ByteArray? = null

        var tag = cis.readTag()
        while (tag != 0) {
            val wire = WireFormat.getTagWireType(tag)
            when (WireFormat.getTagFieldNumber(tag)) {
                F_REGION_ID -> regionId = cis.readString()
                F_LON_WEST -> lonWest = cis.readDouble()
                F_LON_EAST -> lonEast = cis.readDouble()
                F_LAT_SOUTH -> latSouth = cis.readDouble()
                F_LAT_NORTH -> latNorth = cis.readDouble()
                F_ROWS -> rows = cis.readInt32()
                F_COLS -> cols = cis.readInt32()
                F_CELL_SIZE_DEG_LAT -> cellSizeDegLat = cis.readDouble()
                F_CELL_SIZE_DEG_LON -> cellSizeDegLon = cis.readDouble()
                F_DATUM -> datumOrdinal = cis.readInt32()
                F_SOURCE -> sourceLabel = cis.readString()
                F_FETCH_TIMESTAMP_MS -> fetchTimestampMs = cis.readInt64()
                F_GRID_RES_M -> gridResM = cis.readDouble()
                F_DEPTHS -> depths = readPackedDepths(cis, wire)
                F_SOURCE_IDS -> sourceIds = readPackedIds(cis, wire, F_SOURCE_IDS, rows * cols)
                F_CONFIDENCE -> confidence = readPackedIds(cis, wire, F_CONFIDENCE, rows * cols)
                F_VALIDATION -> validation = readValidation(cis, wire)
                else -> cis.skipField(tag)
            }
            tag = cis.readTag()
        }

        val depthsArray = depths ?: throw InvalidProtocolBufferException("depth grid: no depths field")
        val sourceArray = sourceIds ?: throw InvalidProtocolBufferException("depth grid: no source_ids field")
        val confidenceArray = confidence ?: throw InvalidProtocolBufferException("depth grid: no confidence field")
        val n = depthsArray.size
        if (rows * cols != n) {
            throw InvalidProtocolBufferException("depth grid: ${rows}x${cols} does not match $n cells")
        }

        var min = Float.MAX_VALUE
        var maxV = -Float.MAX_VALUE
        var noData = 0
        for (v in depthsArray) {
            if (v.isNaN()) noData++ else { if (v < min) min = v; if (v > maxV) maxV = v }
        }
        if (noData == n) { min = Float.NaN; maxV = Float.NaN }

        val datum = DepthDatum.entries.getOrElse(datumOrdinal) { DepthDatum.UNKNOWN }

        return DepthGrid(
            regionId = regionId,
            boundingBox = BoundingBox(
                latSouth = latSouth, latNorth = latNorth,
                lonWest = lonWest, lonEast = lonEast
            ),
            rows = rows,
            cols = cols,
            cellSizeDegLat = cellSizeDegLat,
            cellSizeDegLon = cellSizeDegLon,
            datum = datum,
            depths = depthsArray,
            source = sourceArray,
            confidence = confidenceArray,
            metadata = DepthMetadata(
                source = sourceLabel,
                fetchTimestampMs = fetchTimestampMs,
                gridResM = gridResM,
                cellCount = n,
                noDataCount = noData,
                minDepthM = min,
                maxDepthM = maxV,
                validation = validation
            )
        )
    }

    // ── Streaming field readers ───────────────────────────────────────────────

    /** Packed float32 → the final [FloatArray]; the payload length fixes the cell count. */
    private fun readPackedDepths(cis: CodedInputStream, wire: Int): FloatArray {
        requirePacked(F_DEPTHS, wire)
        val len = cis.readRawVarint32()
        if (len % 4 != 0) throw InvalidProtocolBufferException("depth grid: depths is not float32-aligned")
        val out = FloatArray(len / 4)
        for (i in out.indices) out[i] = Float.fromBits(cis.readRawLittleEndian32())
        return out
    }

    /**
     * Packed int32 → the final [ByteArray]. Both fields stay under 128 (DepthSource.id ≤ 6;
     * confidence coerced to 0..100), so each element is one varint byte and the payload *is* the
     * array — nothing to walk and nothing to discard. [expected] is the guard for the other case,
     * where a value ≥ 128 would need two bytes.
     */
    private fun readPackedIds(cis: CodedInputStream, wire: Int, field: Int, expected: Int): ByteArray {
        requirePacked(field, wire)
        val len = cis.readRawVarint32()
        if (expected <= 0 || len == expected) return cis.readRawBytes(len)
        val out = ByteArray(expected)
        for (i in 0 until expected) out[i] = cis.readRawVarint32().toByte()
        return out
    }

    /** Small embedded report — parsed with the generated message, as before. */
    private fun readValidation(cis: CodedInputStream, wire: Int): ValidationReport {
        if (wire != WireFormat.WIRETYPE_LENGTH_DELIMITED) {
            throw InvalidProtocolBufferException("depth grid: validation is not length-delimited")
        }
        val len = cis.readRawVarint32()
        return validationFromProto(DepthProtos.ValidationReport.parseFrom(cis.readRawBytes(len)))
    }

    private fun requirePacked(field: Int, wire: Int) {
        if (wire != WireFormat.WIRETYPE_LENGTH_DELIMITED) {
            throw InvalidProtocolBufferException("depth grid: field $field is not packed")
        }
    }

    // ── Validation report ─────────────────────────────────────────────────────

    private fun validationToProto(v: ValidationReport): DepthProtos.ValidationReport {
        val b = DepthProtos.ValidationReport.newBuilder()
            .setMeanBiasM(v.meanBiasM)
            .setRmseM(v.rmseM)
            .setMaxAbsErrM(v.maxAbsErrM)
            .setControlPointCount(v.controlPointCount)
            .setUncoveredCount(v.uncoveredCount)
            .setPassed(v.passed)
            .setDatumMismatchSuspected(v.datumMismatchSuspected)
            .setValidatedAtMs(v.validatedAtMs)
        for (t in v.tiers) {
            b.addTiers(
                DepthProtos.TierResidual.newBuilder()
                    .setMinDepthM(t.minDepthM)
                    .setMaxDepthM(t.maxDepthM)
                    .setMeanBiasM(t.meanBiasM)
                    .setRmseM(t.rmseM)
                    .setMaxAbsErrM(t.maxAbsErrM)
                    .setCount(t.count)
                    .build()
            )
        }
        return b.build()
    }

    private fun validationFromProto(p: DepthProtos.ValidationReport): ValidationReport =
        ValidationReport(
            meanBiasM = p.meanBiasM,
            rmseM = p.rmseM,
            maxAbsErrM = p.maxAbsErrM,
            controlPointCount = p.controlPointCount,
            uncoveredCount = p.uncoveredCount,
            passed = p.passed,
            datumMismatchSuspected = p.datumMismatchSuspected,
            tiers = p.tiersList.map { t ->
                TierResidual(
                    minDepthM = t.minDepthM,
                    maxDepthM = t.maxDepthM,
                    meanBiasM = t.meanBiasM,
                    rmseM = t.rmseM,
                    maxAbsErrM = t.maxAbsErrM,
                    count = t.count
                )
            },
            validatedAtMs = p.validatedAtMs
        )
}
