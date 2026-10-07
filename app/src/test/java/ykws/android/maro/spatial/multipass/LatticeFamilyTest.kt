package ykws.android.maro.spatial.multipass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.markers.BBox

/**
 * **The two-layer lattice family, and the identity a cell carries across its layers** — Phase 4's own
 * gate: a coarse cell and the 5 × 5 fine cells over it agree on one origin, and two windows over the same
 * water at the two sizes each keep their own cells, which is the case `onLattice`'s coordinate-only key
 * collapses.
 */
class LatticeFamilyTest {

    private val corridor = BBox(43.45, 43.55, 6.95, 7.05)

    /**
     * **The nesting.** The family derives the coarse pair as exactly `ratio ×` the fine pair on the same
     * origin, so a coarse cell's centre is the central fine cell's of its 5 × 5 block — the fixed relation a
     * seam's neighbour rests on, rather than a search.
     */
    @Test
    fun theCoarseCellAndItsFineFiveByFiveAgreeOnOneOrigin() {
        val family = LatticeFamily.of(corridor, coarseCellM = 100.0, fineCellM = 20.0)

        assertEquals("the two sizes are an exact 1 : 5 ratio", 5, family.ratio)
        assertEquals("one origin, latitude", family.coarse.latSouth, family.fine.latSouth, 0.0)
        assertEquals("one origin, longitude", family.coarse.lonWest, family.fine.lonWest, 0.0)
        assertEquals(
            "the coarse pair is exactly five fine ones (latitude)",
            family.fine.cellSizeDegLat * 5.0, family.coarse.cellSizeDegLat, 1e-12
        )
        assertEquals(
            "the coarse pair is exactly five fine ones (longitude)",
            family.fine.cellSizeDegLon * 5.0, family.coarse.cellSizeDegLon, 1e-12
        )

        for (row in 0..3) {
            for (col in 0..3) {
                val coarseCentre = family.coarse.center(row, col)
                val fineCentre = family.fine.center(row * 5 + 2, col * 5 + 2)
                assertEquals(
                    "coarse ($row, $col) is the central fine cell of its 5 × 5 block (latitude)",
                    coarseCentre.latitude, fineCentre.latitude, 1e-12
                )
                assertEquals(
                    "coarse ($row, $col) is the central fine cell of its 5 × 5 block (longitude)",
                    coarseCentre.longitude, fineCentre.longitude, 1e-12
                )
            }
        }
    }

    /**
     * **The identity.** Two windows over the same water at the two sizes each keep their own cells: with the
     * layer in the key the walk holds every cell of both, while a coordinate-only key would keep the first
     * layer's cell at `(0, 0)` and silently drop the second layer's.
     */
    @Test
    fun twoWindowsOverTheSameWaterKeepTheirOwnCells() {
        val family = LatticeFamily.of(corridor, coarseCellM = 100.0, fineCellM = 20.0)
        val paceKn = 28.0
        val fineRows = 10
        val fineCols = 10
        val coarseRows = 2
        val coarseCols = 2

        val fineGrid = MultipassGrid(
            family.fine.latSouth, family.fine.lonWest,
            family.fine.cellSizeDegLat, family.fine.cellSizeDegLon,
            fineRows, fineCols, family.fine.cellM, baseCostSec(family.fine.cellM, paceKn)
        )
        val coarseGrid = MultipassGrid(
            family.coarse.latSouth, family.coarse.lonWest,
            family.coarse.cellSizeDegLat, family.coarse.cellSizeDegLon,
            coarseRows, coarseCols, family.coarse.cellM, baseCostSec(family.coarse.cellM, paceKn)
        )

        val walk = WalkWindows.onLattice(
            family.layers,
            listOf(
                WalkWindow(coarseGrid, 0, 0, layer = 0),
                WalkWindow(fineGrid, 0, 0, layer = 1)
            )
        )

        assertEquals(
            "the coordinate-only key would collapse the two layers; the layer in it keeps both",
            fineRows * fineCols + coarseRows * coarseCols,
            walk.size
        )
        val coarseSlot = walk.rawSlotOf(0, 0, 0)
        val fineSlot = walk.rawSlotOf(1, 0, 0)
        assertTrue("the coarse layer holds its own (0, 0)", coarseSlot >= 0)
        assertTrue("the fine layer holds its own (0, 0)", fineSlot >= 0)
        assertTrue("and the two layers' (0, 0) are different slots", coarseSlot != fineSlot)
        assertEquals("the coarse slot reports layer 0", 0, walk.layerOf(coarseSlot))
        assertEquals("the fine slot reports layer 1", 1, walk.layerOf(fineSlot))
        assertEquals("a two-window two-layer walk reports two layers", 2, walk.layerCount)
    }
}
