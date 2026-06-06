package ykws.android.maro.data.depth

import org.junit.Assert.*
import org.junit.Test
import ykws.android.maro.data.model.BoundingBox
import ykws.android.maro.data.model.DepthDatum
import ykws.android.maro.data.model.DepthSource
import ykws.android.maro.data.model.MutableDepthGrid
import ykws.android.maro.data.model.TierResidual
import ykws.android.maro.data.model.ValidationReport

/**
 * Round-trip tests for [DepthSerializer] — including NaN NoData cells, per-cell
 * provenance, and the embedded [ValidationReport].
 */
class DepthSerializerTest {

    private val bbox = BoundingBox(latSouth = 43.50, latNorth = 43.54, lonWest = 7.00, lonEast = 7.06)

    private fun sampleGrid(): MutableDepthGrid {
        val m = MutableDepthGrid.empty("nice-frejus", bbox, 1500.0, DepthDatum.LAT)
        // Fill with a gradient, leaving cell (0,0) as NaN NoData.
        var v = 1.0f
        for (r in 0 until m.rows) for (c in 0 until m.cols) {
            if (r == 0 && c == 0) continue // keep NaN
            val src = if (v < 10f) DepthSource.LITTO3D else DepthSource.EMODNET
            m.set(r, c, v, src, src.seedConfidence)
            v += 1.5f
        }
        return m
    }

    @Test
    fun `round trip preserves grid, NaN, provenance and bbox`() {
        val report = ValidationReport(
            meanBiasM = 0.12, rmseM = 0.34, maxAbsErrM = 0.9,
            controlPointCount = 5, uncoveredCount = 1, passed = true,
            datumMismatchSuspected = false,
            tiers = listOf(TierResidual(0.0, 5.0, 0.1, 0.3, 0.5, 3)),
            validatedAtMs = 123456789L
        )
        val original = sampleGrid().toImmutable(report, fetchTimestampMs = 999L, sourceLabel = "EMODnet+Litto3D")

        val restored = DepthSerializer.deserialize(DepthSerializer.serialize(original))

        assertEquals(original.regionId, restored.regionId)
        assertEquals(original.rows, restored.rows)
        assertEquals(original.cols, restored.cols)
        assertEquals(original.datum, restored.datum)
        assertEquals(original.cellSizeDegLat, restored.cellSizeDegLat, 1e-12)
        assertEquals(original.cellSizeDegLon, restored.cellSizeDegLon, 1e-12)
        assertEquals(original.boundingBox.latSouth, restored.boundingBox.latSouth, 1e-9)
        assertEquals(original.boundingBox.lonEast, restored.boundingBox.lonEast, 1e-9)
        assertEquals(original.metadata.source, restored.metadata.source)
        assertEquals(original.metadata.fetchTimestampMs, restored.metadata.fetchTimestampMs)

        // Cell-by-cell: depths (NaN preserved), source, confidence.
        for (i in original.depths.indices) {
            val o = original.depths[i]
            val rr = restored.depths[i]
            if (o.isNaN()) assertTrue("cell $i should stay NaN", rr.isNaN())
            else assertEquals("cell $i depth", o, rr, 1e-4f)
        }
        assertArrayEquals(original.source, restored.source)
        assertArrayEquals(original.confidence, restored.confidence)

        // NoData stat recomputed correctly (exactly one NaN cell).
        assertEquals(1, restored.metadata.noDataCount)

        // Embedded validation report.
        val v = restored.metadata.validation!!
        assertEquals(report.rmseM, v.rmseM, 1e-9)
        assertEquals(report.passed, v.passed)
        assertEquals(report.datumMismatchSuspected, v.datumMismatchSuspected)
        assertEquals(1, v.tiers.size)
        assertEquals(report.tiers[0].maxAbsErrM, v.tiers[0].maxAbsErrM, 1e-9)
        assertEquals(report.validatedAtMs, v.validatedAtMs)
    }

    @Test
    fun `round trip works without a validation report`() {
        val original = sampleGrid().toImmutable(null, 0L, "EMODnet")
        val restored = DepthSerializer.deserialize(DepthSerializer.serialize(original))
        assertNull(restored.metadata.validation)
    }
}
