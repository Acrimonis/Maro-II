package ykws.android.maro.data.depth.validation

import org.junit.Assert.*
import org.junit.Test
import ykws.android.maro.data.model.BoundingBox
import ykws.android.maro.data.model.DepthDatum
import ykws.android.maro.data.model.DepthGrid
import ykws.android.maro.data.model.DepthSource
import ykws.android.maro.data.model.MutableDepthGrid

/**
 * Unit tests for [DepthValidator] — residual metrics, datum alignment, the datum-mismatch
 * fingerprint, and pass/fail gating.
 */
class DepthValidatorTest {

    private val bbox = BoundingBox(latSouth = 43.50, latNorth = 43.54, lonWest = 7.00, lonEast = 7.06)

    private fun uniformGrid(value: Float, datum: DepthDatum = DepthDatum.LAT): DepthGrid {
        val m = MutableDepthGrid.empty("nice-frejus", bbox, 1500.0, datum)
        for (r in 0 until m.rows) for (c in 0 until m.cols)
            m.set(r, c, value, DepthSource.EMODNET, DepthSource.EMODNET.seedConfidence)
        return m.toImmutable(null, 0L, "test")
    }

    private fun cp(lat: Double, lon: Double, depth: Double, datum: DepthDatum = DepthDatum.LAT) =
        ControlPoint("p", lat, lon, depth, datum, "test")

    private val interior = listOf(
        Triple(43.515, 7.02, 0.0),
        Triple(43.520, 7.03, 0.0),
        Triple(43.525, 7.04, 0.0),
        Triple(43.530, 7.05, 0.0)
    )

    // ── Accurate grid passes; datum alignment works ─────────────────────────

    @Test
    fun `accurate grid passes and aligns datums`() {
        val grid = uniformGrid(3.0f, DepthDatum.LAT)
        // Three LAT points at 3.0 m, plus one MSL point whose LAT-equivalent is 3.0 m
        // (2.85 + 0.15 MSL-above-LAT offset).
        val points = listOf(
            cp(43.515, 7.02, 3.0),
            cp(43.520, 7.03, 3.0),
            cp(43.525, 7.04, 3.0),
            cp(43.530, 7.05, 2.85, DepthDatum.MSL)
        )
        val report = DepthValidator.validate(grid, points)

        assertEquals(4, report.controlPointCount)
        assertEquals(0, report.uncoveredCount)
        assertTrue("near-zero residuals pass", report.passed)
        assertFalse(report.datumMismatchSuspected)
        assertTrue("rmse small", report.rmseM < 0.05)
        val collision = report.tiers.first()
        assertTrue(collision.rmseM < 0.05)
    }

    // ── Constant bias ⇒ datum mismatch + fail ───────────────────────────────

    @Test
    fun `constant bias flags datum mismatch and fails`() {
        val grid = uniformGrid(3.6f, DepthDatum.LAT) // 0.6 m deeper than truth everywhere
        val points = interior.map { cp(it.first, it.second, 3.0) }
        val report = DepthValidator.validate(grid, points)

        assertEquals(4, report.controlPointCount)
        assertEquals(0.6, report.meanBiasM, 1e-3)
        assertTrue("constant 0.6 m offset is a datum-mismatch fingerprint", report.datumMismatchSuspected)
        assertFalse("collision RMSE 0.6 m > 0.5 m gate ⇒ fail", report.passed)
    }

    // ── Uncovered points are counted, not scored ────────────────────────────

    @Test
    fun `points outside the grid are reported as uncovered`() {
        val grid = uniformGrid(3.0f)
        val points = interior.map { cp(it.first, it.second, 3.0) } + cp(50.0, 0.0, 3.0)
        val report = DepthValidator.validate(grid, points)
        assertEquals(4, report.controlPointCount)
        assertEquals(1, report.uncoveredCount)
        assertTrue(report.passed)
    }

    // ── Too few covered points ⇒ not passed even when accurate ──────────────

    @Test
    fun `fewer than the minimum control points does not pass`() {
        val grid = uniformGrid(3.0f)
        val points = interior.take(3).map { cp(it.first, it.second, 3.0) }
        val report = DepthValidator.validate(grid, points)
        assertEquals(3, report.controlPointCount)
        assertFalse("min 4 covered points required", report.passed)
        assertFalse(report.datumMismatchSuspected)
    }

    // ── Empty / all-uncovered ───────────────────────────────────────────────

    @Test
    fun `no covered points yields a non-passing empty report`() {
        val grid = uniformGrid(3.0f)
        val report = DepthValidator.validate(grid, listOf(cp(50.0, 0.0, 3.0)))
        assertEquals(0, report.controlPointCount)
        assertEquals(1, report.uncoveredCount)
        assertFalse(report.passed)
        assertTrue(report.tiers.isEmpty())
    }
}
