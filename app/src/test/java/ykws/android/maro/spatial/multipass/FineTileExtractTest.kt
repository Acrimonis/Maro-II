package ykws.android.maro.spatial.multipass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **The tile extractor pins its block to `tileCells` at both raster offsets (D57).**
 *
 * [`FineTile.extract`] reads the block as the **first** `tileCells` rows and columns past the halo, never
 * by subtracting the halo from the rasterised box. The difference only shows when the rasteriser's `ceil`
 * leaves the grown box **one cell wider** than `2 × halo + tileCells` — the 64-vs-65 case D45 found — so
 * this drives both an exact raster and a one-cell-wider one and asserts the block is `tileCells` square in
 * each, that every block cell is a member, and that a member's local index fits the block's own stride. A
 * raster too small for the block is refused rather than silently shifted.
 */
class FineTileExtractTest {

    private val tileCells = 4
    private val halo = 2

    /** A raster of `2 × halo + inner` cells a side, every cell opening passable at the base cost. */
    private fun raster(inner: Int): MultipassGrid = MultipassGrid(
        latSouth = 43.0, lonWest = 7.0,
        cellSizeDegLat = 1.8e-4, cellSizeDegLon = 2.5e-4,
        rows = 2 * halo + inner, cols = 2 * halo + inner,
        cellM = 20.0, baseCostSec = 1.0
    )

    @Test
    fun theBlockIsTileCellsSquareAtBothOffsets() {
        // The exact raster (inner == tileCells) and the ceiled one (inner == tileCells + 1): the stray
        // extra row/column lands at the far (north/east) edge and must be dropped from the block.
        for (inner in listOf(tileCells, tileCells + 1)) {
            val grid = raster(inner)
            val tile = FineTile.extract(grid, tileRow = 0, tileCol = 0, tileCells = tileCells, haloCells = halo)

            assertEquals("the block is exactly tileCells wide (inner=$inner)", tileCells, tile.cols)
            assertEquals("and exactly tileCells tall (inner=$inner)", tileCells, tile.rows)
            assertEquals(
                "every block cell is a member and the stray far edge is not (inner=$inner)",
                tileCells * tileCells,
                tile.memberCount
            )
            assertTrue(
                "a member's local index fits the block's own stride (inner=$inner)",
                tile.localIndex.all { it in 0 until tileCells * tileCells }
            )
            assertEquals(
                "the block's own south-west origin is the haloed one (inner=$inner)",
                grid.latSouth + halo * grid.cellSizeDegLat,
                tile.latSouth,
                0.0
            )
        }
    }

    @Test
    fun aRasterTooSmallForTheBlockIsRefused() {
        val refused = try {
            FineTile.extract(raster(tileCells - 1), tileRow = 0, tileCol = 0, tileCells = tileCells, haloCells = halo)
            null
        } catch (thrown: IllegalArgumentException) {
            thrown
        }
        assertTrue(
            "a raster smaller than the block is refused rather than silently shifted",
            refused != null && refused.message.orEmpty().contains("tileCells")
        )
    }

    /**
     * **The boundary the carve rests on (P4.5) — and the only guard for it.** A shared tile is immutable,
     * so the arm's berth carve — `forceFree`, `openEndDisc`, `openCarve` — may only write to the
     * **assembled window grid**, which [`FineTile.writeInto`] fills by **copying** each member. This pins
     * copy-not-alias **across all seven arrays**: a distinct write on the arm's own grid must leave every
     * one of the tile's primitives at member 0 untouched. (The carried cross-arm reuse count cannot catch a
     * leak — a mutated tile stays cached, so the warm arm still logs no miss — which is why the pin is the
     * guard and its claim is exactly this: no write reaches the tile.)
     */
    @Test
    fun writeIntoCopiesMembersWithoutTouchingTheTile() {
        val grid = raster(tileCells)
        val tile = FineTile.extract(grid, tileRow = 0, tileCol = 0, tileCells = tileCells, haloCells = halo)
        val armGrid = MultipassGrid(
            latSouth = grid.latSouth, lonWest = grid.lonWest,
            cellSizeDegLat = grid.cellSizeDegLat, cellSizeDegLon = grid.cellSizeDegLon,
            rows = tileCells, cols = tileCells, cellM = grid.cellM, baseCostSec = grid.baseCostSec
        )
        val before = memberZero(tile)

        tile.writeInto(armGrid, windowRowOffset = 0, windowColOffset = 0)
        // The carve's own write, aimed at member 0 — which `writeInto` places at the arm grid's (0, 0) —
        // and deliberately unlike the tile's own values, so an alias on **any** array would show.
        armGrid.writeMember(0, 0, 1, 99.0, 5.0, 6.0, 7.0, 8.0, 0.5)

        assertEquals("every member was copied into the arm's grid", tileCells * tileCells, tile.memberCount)
        assertEquals(
            "and no write on the arm's grid reaches the tile, across all seven arrays",
            before,
            memberZero(tile)
        )
    }

    /** Member 0's seven stored values, as one comparable snapshot. */
    private fun memberZero(tile: FineTile): List<Double> = listOf(
        tile.state[0].toDouble(), tile.sourceCostSec[0], tile.zoneLimitKn[0], tile.collarLimitKn[0],
        tile.bandLimitKn[0], tile.bandCollarLimitKn[0], tile.depthPriceCoef[0]
    )
}
