package ykws.android.maro.spatial.multipass

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * **The pace-free tile, proved** — the factor a tile carries, and the one place the base is re-applied.
 *
 * The tile's own bytes carry **no pace**: its build rasterises at a unit base, so its stored
 * `sourceCostFactor` *is* the pace-free multiplier `1 + m` (and `1.0` on marker-free water), and
 * [`FineTile.writeInto`] re-applies the target grid's base in one multiply ([memberCostAtBase], D2).
 * The three proofs the plan's §3 names live here: writing at the same pace twice is byte-identical, a
 * tile written at B equals a fresh build at B (the bytes are the same, so the tiles are the same), and a
 * marker-priced member's cost is `base × (1 + m)`.
 */
class FineTilePaceFreeTest {

    /** Two paces, so a member's cost is seen to follow the target grid's base and nothing else. */
    private val fastBase = 2.0
    private val slowBase = 5.0

    /** A one-row arm grid of [members] columns, opening at [base]. */
    private fun armGrid(base: Double, members: Int) = MultipassGrid(
        latSouth = 43.0, lonWest = 6.7, cellSizeDegLat = 1.8e-4, cellSizeDegLon = 2.5e-4,
        rows = 1, cols = members, cellM = 20.0, baseCostSec = base
    )

    /** The arm grid's first-row source costs, as one comparable array. */
    private fun rowCosts(grid: MultipassGrid, members: Int) = DoubleArray(members) { grid.sourceCostSec(0, it) }

    /** **D2 — writeInto prices each member from the target grid's own base.** */
    @Test
    fun writeIntoPricesEachMemberFromTheTargetBase() {
        val tile = TileTestFixtures.tile(6)
        val fast = armGrid(fastBase, members = 6)
        val slow = armGrid(slowBase, members = 6)
        tile.writeInto(fast, 0, 0)
        tile.writeInto(slow, 0, 0)
        for (m in 0 until 6) {
            assertEquals(
                "the fast grid pays base × factor",
                tile.sourceCostFactor[m] * fastBase, fast.sourceCostSec(0, m), 0.0
            )
            assertEquals(
                "the slow grid pays its own base × the same factor",
                tile.sourceCostFactor[m] * slowBase, slow.sourceCostSec(0, m), 0.0
            )
        }
    }

    /**
     * **§3 proofs 1 & 2.** The tile's bytes are pace-free, so "a tile built at A" and "a tile built at B"
     * are the same bytes; writing either at B yields a grid byte-identical to the other, and a tile served
     * from the codec equals a freshly built one.
     */
    @Test
    fun aTileIsPaceFreeSoWritingAtBEqualsAFreshBuildAtB() {
        val builtAtA = TileTestFixtures.tile(6)
        val builtAtB = TileTestFixtures.tile(6) // the same bytes — the build reads no pace
        val served = FineTileCodec.decode(
            TileTestFixtures.baseKey(),
            FineTileCodec.encode(TileTestFixtures.baseKey(), builtAtA)
        )!!

        val gridFromA = armGrid(slowBase, members = 6)
        val gridFromB = armGrid(slowBase, members = 6)
        builtAtA.writeInto(gridFromA, 0, 0)
        builtAtB.writeInto(gridFromB, 0, 0)
        assertArrayEquals(
            "a tile writing at B equals a fresh build at B, byte for byte",
            rowCosts(gridFromA, 6), rowCosts(gridFromB, 6), 0.0
        )

        val gridFromServed = armGrid(slowBase, members = 6)
        served.writeInto(gridFromServed, 0, 0)
        assertArrayEquals(
            "and a served tile equals a freshly built one",
            rowCosts(gridFromA, 6), rowCosts(gridFromServed, 6), 0.0
        )
    }

    /**
     * **D4 — a marker-priced member's factor is `1 + m`, so its cost is `base × (1 + m)`.** The unit-base
     * raster stores `1 + m`; `writeInto` lands the price once, from the target grid's base. A marker-free
     * member's factor of `1.0` costs exactly the base — nothing moves on marker-free water.
     */
    @Test
    fun aMarkerFactorMemberCostsBaseTimesOnePlusM() {
        val markerFactor = 1.0 + 4.0 // 1 + m
        val tile = oneMemberTile(markerFactor)
        val priced = armGrid(slowBase, members = 1)
        tile.writeInto(priced, 0, 0)
        assertEquals(
            "a marker-priced member costs base × (1 + m)",
            slowBase * markerFactor, priced.sourceCostSec(0, 0), 0.0
        )

        val free = oneMemberTile(1.0)
        val openWater = armGrid(slowBase, members = 1)
        free.writeInto(openWater, 0, 0)
        assertEquals(
            "a marker-free member costs exactly the base",
            slowBase, openWater.sourceCostSec(0, 0), 0.0
        )
    }

    /** A one-member tile carrying [factor] as its whole stored water. */
    private fun oneMemberTile(factor: Double) = FineTile(
        tileRow = 0, tileCol = 0, tileCells = FINE_TILE_CELLS, rows = 1, cols = 1,
        latSouth = 43.0, lonWest = 6.7, cellSizeDegLat = 1.8e-4, cellSizeDegLon = 2.5e-4,
        cellM = 20.0,
        state = byteArrayOf(MultipassCellState.FREE.ordinal.toByte()),
        sourceCostFactor = doubleArrayOf(factor),
        zoneLimitKn = doubleArrayOf(0.0), collarLimitKn = doubleArrayOf(0.0),
        bandLimitKn = doubleArrayOf(0.0), bandCollarLimitKn = doubleArrayOf(0.0),
        depthPriceCoef = doubleArrayOf(0.0), localIndex = intArrayOf(0)
    )
}
