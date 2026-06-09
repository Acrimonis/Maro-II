package ykws.android.maro.data.depth

import org.junit.Assert.*
import org.junit.Test
import ykws.android.maro.data.depth.raster.SourceRaster
import ykws.android.maro.data.model.BoundingBox
import ykws.android.maro.data.model.DepthDatum
import ykws.android.maro.data.model.DepthGrid
import ykws.android.maro.data.model.DepthSource
import ykws.android.maro.data.model.MutableDepthGrid

/**
 * Unit tests for [DepthMerge] — the precision-tier merge rules.
 */
class DepthMergeTest {

    private val bbox = BoundingBox(latSouth = 43.50, latNorth = 43.54, lonWest = 7.00, lonEast = 7.06)
    private val gridResM = 1500.0

    private fun target() = MutableDepthGrid.empty("test", bbox, gridResM, DepthDatum.LAT)

    private fun uniformRaster(value: Float, resM: Double, source: DepthSource): SourceRaster {
        val rows = 2; val cols = 2
        return SourceRaster(
            bbox = bbox, rows = rows, cols = cols,
            cellSizeDegLat = bbox.heightDeg / rows,
            cellSizeDegLon = bbox.widthDeg / cols,
            values = FloatArray(rows * cols) { value },
            resM = resM, source = source
        )
    }

    private fun nanRaster(source: DepthSource): SourceRaster {
        val rows = 2; val cols = 2
        return SourceRaster(
            bbox = bbox, rows = rows, cols = cols,
            cellSizeDegLat = bbox.heightDeg / rows,
            cellSizeDegLon = bbox.widthDeg / cols,
            values = FloatArray(rows * cols) { Float.NaN },
            resM = source.nominalResM, source = source
        )
    }

    /** A raster fine enough that every coarse target cell contains several source-cell centres. */
    private fun fineRaster(value: Float, source: DepthSource, n: Int = 20): SourceRaster =
        SourceRaster(
            bbox = bbox, rows = n, cols = n,
            cellSizeDegLat = bbox.heightDeg / n,
            cellSizeDegLon = bbox.widthDeg / n,
            values = FloatArray(n * n) { value },
            resM = source.nominalResM, source = source
        )

    private fun uniformGrid(value: Float, source: DepthSource): DepthGrid {
        val m = MutableDepthGrid.empty("shallow", bbox, gridResM, DepthDatum.LAT)
        for (r in 0 until m.rows) for (c in 0 until m.cols) m.set(r, c, value, source, source.seedConfidence)
        return m.toImmutable(null, 0L, "shallow")
    }

    // ── mergeDeep ───────────────────────────────────────────────────────────

    @Test
    fun `mergeDeep writes onto an empty grid`() {
        val t = target()
        DepthMerge.mergeDeep(t, uniformRaster(20f, DepthSource.EMODNET.nominalResM, DepthSource.EMODNET))
        val r = t.rows / 2; val c = t.cols / 2
        assertEquals(20f, t.get(r, c), 1e-3f)
        assertEquals(DepthSource.EMODNET, t.sourceAt(r, c))
    }

    @Test
    fun `finer resolution overwrites coarser, coarser does not overwrite finer`() {
        val t = target()
        val r = t.rows / 2; val c = t.cols / 2
        // GEBCO (450 m) first.
        DepthMerge.mergeDeep(t, uniformRaster(30f, DepthSource.GEBCO.nominalResM, DepthSource.GEBCO))
        assertEquals(DepthSource.GEBCO, t.sourceAt(r, c))
        // EMODnet (115 m) is finer → overwrites.
        DepthMerge.mergeDeep(t, uniformRaster(25f, DepthSource.EMODNET.nominalResM, DepthSource.EMODNET))
        assertEquals(25f, t.get(r, c), 1e-3f)
        assertEquals(DepthSource.EMODNET, t.sourceAt(r, c))
        // A second GEBCO pass (coarser) must NOT overwrite the finer EMODnet value.
        DepthMerge.mergeDeep(t, uniformRaster(99f, DepthSource.GEBCO.nominalResM, DepthSource.GEBCO))
        assertEquals(25f, t.get(r, c), 1e-3f)
        assertEquals(DepthSource.EMODNET, t.sourceAt(r, c))
    }

    @Test
    fun `NaN source never overwrites a good cell`() {
        val t = target()
        val r = t.rows / 2; val c = t.cols / 2
        DepthMerge.mergeDeep(t, uniformRaster(20f, DepthSource.EMODNET.nominalResM, DepthSource.EMODNET))
        DepthMerge.mergeDeep(t, nanRaster(DepthSource.EMODNET))
        assertEquals(20f, t.get(r, c), 1e-3f)
        assertEquals(DepthSource.EMODNET, t.sourceAt(r, c))
    }

    @Test
    fun `mergeDeep ignores above-datum cells (negative depth)`() {
        val t = target()
        val r = t.rows / 2; val c = t.cols / 2
        // An EMODnet cell over an emergent rock samples above datum → negative "depth".
        // It must never land in the grid (the −1.7 m Cap-d'Antibes false reading).
        DepthMerge.mergeDeep(t, uniformRaster(-1.7f, DepthSource.EMODNET.nominalResM, DepthSource.EMODNET))
        assertTrue("above-datum EMODnet must not become a negative depth", t.get(r, c).isNaN())
    }

    @Test
    fun `mergeDeep negative finer source does not overwrite a good cell`() {
        val t = target()
        val r = t.rows / 2; val c = t.cols / 2
        DepthMerge.mergeDeep(t, uniformRaster(5f, DepthSource.EMODNET.nominalResM, DepthSource.EMODNET))
        // A finer source (SDB, 10 m < EMODnet 115 m) that reads above datum must NOT replace 5 m.
        DepthMerge.mergeDeep(t, uniformRaster(-1.7f, DepthSource.SDB.nominalResM, DepthSource.SDB))
        assertEquals(5f, t.get(r, c), 1e-3f)
        assertEquals(DepthSource.EMODNET, t.sourceAt(r, c))
    }

    // ── mergeShallowShoalest ──────────────────────────────────────────────────

    @Test
    fun `shallower value wins, deeper does not, and beyond ceiling is ignored`() {
        val t = target()
        val r = t.rows / 2; val c = t.cols / 2
        // Start with an EMODnet 8 m value.
        DepthMerge.mergeDeep(t, uniformRaster(8f, DepthSource.EMODNET.nominalResM, DepthSource.EMODNET))

        // Litto3D 3 m (shallower) wins.
        DepthMerge.mergeShallowShoalest(t, uniformGrid(3f, DepthSource.LITTO3D), shallowTierMaxM = 10.0)
        assertEquals(3f, t.get(r, c), 1e-3f)
        assertEquals(DepthSource.LITTO3D, t.sourceAt(r, c))

        // Litto3D 9 m (deeper than current 3) does NOT overwrite.
        DepthMerge.mergeShallowShoalest(t, uniformGrid(9f, DepthSource.LITTO3D), shallowTierMaxM = 10.0)
        assertEquals(3f, t.get(r, c), 1e-3f)

        // Litto3D 15 m is beyond the 10 m ceiling → ignored entirely.
        DepthMerge.mergeShallowShoalest(t, uniformGrid(15f, DepthSource.LITTO3D), shallowTierMaxM = 10.0)
        assertEquals(3f, t.get(r, c), 1e-3f)
        assertEquals(DepthSource.LITTO3D, t.sourceAt(r, c))
    }

    @Test
    fun `raster shoalest merge — shallower wins, deeper ignored, beyond ceiling ignored`() {
        val t = target()
        val r = t.rows / 2; val c = t.cols / 2
        DepthMerge.mergeDeep(t, uniformRaster(8f, DepthSource.EMODNET.nominalResM, DepthSource.EMODNET))

        // Litto3D raster 3 m (shallower) wins.
        DepthMerge.mergeShallowShoalest(t, fineRaster(3f, DepthSource.LITTO3D), shallowTierMaxM = 10.0)
        assertEquals(3f, t.get(r, c), 1e-3f)
        assertEquals(DepthSource.LITTO3D, t.sourceAt(r, c))

        // 9 m (deeper than current 3) does NOT overwrite.
        DepthMerge.mergeShallowShoalest(t, fineRaster(9f, DepthSource.LITTO3D), shallowTierMaxM = 10.0)
        assertEquals(3f, t.get(r, c), 1e-3f)

        // 15 m beyond the 10 m ceiling → ignored.
        DepthMerge.mergeShallowShoalest(t, fineRaster(15f, DepthSource.LITTO3D), shallowTierMaxM = 10.0)
        assertEquals(3f, t.get(r, c), 1e-3f)
    }

    @Test
    fun `raster shoalest merge ignores above-datum land (negative depth)`() {
        val t = target()
        val r = t.rows / 2; val c = t.cols / 2
        DepthMerge.mergeDeep(t, uniformRaster(8f, DepthSource.EMODNET.nominalResM, DepthSource.EMODNET))
        // A Litto3D land cell at −2 m (above datum) must NOT win despite being "shoalest".
        DepthMerge.mergeShallowShoalest(t, fineRaster(-2f, DepthSource.LITTO3D), shallowTierMaxM = 10.0)
        assertEquals(8f, t.get(r, c), 1e-3f)
        assertEquals(DepthSource.EMODNET, t.sourceAt(r, c))
    }

    // ── fillGaps ──────────────────────────────────────────────────────────────

    @Test
    fun `fillGaps only fills NoData cells`() {
        val t = target()
        val r = t.rows / 2; val c = t.cols / 2
        // Seed one cell with EMODnet; leave the rest NaN.
        t.set(r, c, 12f, DepthSource.EMODNET, DepthSource.EMODNET.seedConfidence)
        DepthMerge.fillGaps(t, uniformRaster(40f, DepthSource.GEBCO.nominalResM, DepthSource.GEBCO))
        // Seeded cell untouched.
        assertEquals(12f, t.get(r, c), 1e-3f)
        assertEquals(DepthSource.EMODNET, t.sourceAt(r, c))
        // A previously-empty cell is now filled by GEBCO.
        val r2 = 0; val c2 = 0
        assertEquals(40f, t.get(r2, c2), 1e-3f)
        assertEquals(DepthSource.GEBCO, t.sourceAt(r2, c2))
    }
}
