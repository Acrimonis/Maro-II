package ykws.android.maro.data.depth

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
 */
object DepthSerializer {

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

    fun deserialize(bytes: ByteArray): DepthGrid {
        val p = DepthProtos.DepthCache.parseFrom(bytes)
        val n = p.rows * p.cols

        val depths = FloatArray(n) { p.getDepths(it) }
        val source = ByteArray(n) { p.getSourceIds(it).toByte() }
        val confidence = ByteArray(n) { p.getConfidence(it).toByte() }

        var min = Float.MAX_VALUE
        var maxV = -Float.MAX_VALUE
        var noData = 0
        for (v in depths) {
            if (v.isNaN()) noData++ else { if (v < min) min = v; if (v > maxV) maxV = v }
        }
        if (noData == n) { min = Float.NaN; maxV = Float.NaN }

        val datum = DepthDatum.entries.getOrElse(p.datum) { DepthDatum.UNKNOWN }

        return DepthGrid(
            regionId = p.regionId,
            boundingBox = BoundingBox(
                latSouth = p.latSouth, latNorth = p.latNorth,
                lonWest = p.lonWest, lonEast = p.lonEast
            ),
            rows = p.rows,
            cols = p.cols,
            cellSizeDegLat = p.cellSizeDegLat,
            cellSizeDegLon = p.cellSizeDegLon,
            datum = datum,
            depths = depths,
            source = source,
            confidence = confidence,
            metadata = DepthMetadata(
                source = p.source,
                fetchTimestampMs = p.fetchTimestampMs,
                gridResM = p.gridResM,
                cellCount = n,
                noDataCount = noData,
                minDepthM = min,
                maxDepthM = maxV,
                validation = if (p.hasValidation()) validationFromProto(p.validation) else null
            )
        )
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
