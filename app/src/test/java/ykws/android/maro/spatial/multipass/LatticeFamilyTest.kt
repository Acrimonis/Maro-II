package ykws.android.maro.spatial.multipass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.LatLng
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

    /**
     * **The window-cell translation honours a non-zero offset.** P4.1 repaired a latent defect — a window's
     * *local* cell read as its lattice coordinate, true only at offset zero — by translating through
     * [`WalkWindows.latticeCell`]. The corridor anchor made that offset zero, so only the fixed-anchor harness
     * exercised the non-zero case; this pins it directly: a window standing at `(7, 11)` answers the lattice
     * coordinate `(row + 7, col + 11)` on its own layer, never the bare local index.
     */
    @Test
    fun theWindowCellTranslationHonoursANonZeroOffset() {
        val family = LatticeFamily.of(corridor, coarseCellM = 100.0, fineCellM = 20.0)
        val fineGrid = MultipassGrid(
            family.fine.latSouth, family.fine.lonWest,
            family.fine.cellSizeDegLat, family.fine.cellSizeDegLon,
            4, 6, family.fine.cellM, baseCostSec(family.fine.cellM, 28.0)
        )
        val walk = WalkWindows.onLattice(
            family.layers,
            listOf(WalkWindow(fineGrid, rowOffset = 7, colOffset = 11, layer = 1))
        )

        assertEquals(
            "a window cell is named on the family's lattice, offset by where the window stands",
            CellIndex(9, 13, 1),
            walk.latticeCell(fineGrid, 2, 2)
        )
        assertEquals(
            "the window's own origin cell maps to the window's own offset",
            CellIndex(7, 11, 1),
            walk.latticeCell(fineGrid, 0, 0)
        )
    }

    /**
     * **The anchor holds across corridors.** P4.1's whole point: the family draws its lines on a **fixed**
     * anchor and never reads the corridor, so the same water carries the **same** lattice indices on two
     * different corridors — the property the corridor-derived origin could not give, and the base the fine
     * layer's future tile cache rests on. The contrast is pinned beside it: a corridor-derived origin (the
     * single-grid [`WalkLattice.of`], still corridor-anchored) answers two different rows for one point.
     */
    @Test
    fun theSameWaterCarriesTheSameIndicesAcrossTwoCorridors() {
        val fixed = LatticeAnchor(latSouth = 43.0, lonWest = 6.0, referenceLat = 43.5)
        val corridorA = BBox(43.45, 43.55, 6.95, 7.05)
        val corridorB = BBox(43.42, 43.60, 6.90, 7.10)

        // Production hands both corridors the world's one anchor, so both families are built on it.
        val onA = LatticeFamily.of(fixed, coarseCellM = 100.0, fineCellM = 20.0)
        val onB = LatticeFamily.of(fixed, coarseCellM = 100.0, fineCellM = 20.0)

        // The origin is the anchor, and never either corridor's own south-west.
        assertEquals("the fine origin is the anchor latitude", fixed.latSouth, onA.fine.latSouth, 0.0)
        assertEquals("the fine origin is the anchor longitude", fixed.lonWest, onA.fine.lonWest, 0.0)
        assertEquals("the coarse shares the one anchor origin", onA.coarse.latSouth, onA.fine.latSouth, 0.0)
        assertTrue("the anchor is not corridor A's south-west", onA.fine.latSouth != corridorA.latSouth)
        assertTrue("the anchor is not corridor B's south-west", onB.fine.lonWest != corridorB.lonWest)

        // The old, corridor-derived origin disagrees on the very same water.
        val legacyA = WalkLattice.of(corridorA, 20.0)
        val legacyB = WalkLattice.of(corridorB, 20.0)

        for (point in listOf(LatLng(43.50, 7.00), LatLng(43.46, 6.97), LatLng(43.54, 7.04))) {
            assertEquals(
                "fine row is corridor-free at (${point.latitude}, ${point.longitude})",
                onA.fine.rowOf(point.latitude), onB.fine.rowOf(point.latitude)
            )
            assertEquals(
                "fine col is corridor-free at (${point.latitude}, ${point.longitude})",
                onA.fine.colOf(point.longitude), onB.fine.colOf(point.longitude)
            )
            assertEquals(
                "coarse row is corridor-free at (${point.latitude}, ${point.longitude})",
                onA.coarse.rowOf(point.latitude), onB.coarse.rowOf(point.latitude)
            )
            assertEquals(
                "coarse col is corridor-free at (${point.latitude}, ${point.longitude})",
                onA.coarse.colOf(point.longitude), onB.coarse.colOf(point.longitude)
            )
            assertNotEquals(
                "the corridor-derived origin would answer two rows for one point",
                legacyA.rowOf(point.latitude), legacyB.rowOf(point.latitude)
            )
        }
    }

    /**
     * **A grid the walk does not hold as a window is refused, never silently mis-named (D37).**
     * [`WalkWindows.latticeCell`] turns a window's *local* cell into the family's lattice coordinate using
     * that window's own offset, and a grid the walk does not hold as a window carries no such offset:
     * answering the local index unchanged would hand back a wrong coordinate. The read `requireNotNull`s a
     * window instead, and this pins the refusal.
     */
    @Test
    fun aGridTheWalkDoesNotHoldAsAWindowIsRefused() {
        val family = LatticeFamily.of(corridor, coarseCellM = 100.0, fineCellM = 20.0)
        val heldGrid = MultipassGrid(
            family.fine.latSouth, family.fine.lonWest,
            family.fine.cellSizeDegLat, family.fine.cellSizeDegLon,
            4, 6, family.fine.cellM, baseCostSec(family.fine.cellM, 28.0)
        )
        val walk = WalkWindows.onLattice(
            family.layers,
            listOf(WalkWindow(heldGrid, rowOffset = 7, colOffset = 11, layer = 1))
        )

        // A second grid over the same water, but not one the walk holds as a window.
        val strangerGrid = MultipassGrid(
            family.fine.latSouth, family.fine.lonWest,
            family.fine.cellSizeDegLat, family.fine.cellSizeDegLon,
            4, 6, family.fine.cellM, baseCostSec(family.fine.cellM, 28.0)
        )

        val refused = try {
            walk.latticeCell(strangerGrid, 2, 2)
            null
        } catch (thrown: IllegalArgumentException) {
            thrown
        }

        assertNotNull("a non-window grid is refused rather than answered", refused)
        assertTrue(
            "and the refusal names what the caller asked for",
            refused!!.message.orEmpty().contains("window")
        )
    }

    /**
     * **The whole-degree fallback is one origin per degree, so a world-less corridor cannot fragment the
     * cache (D16).** Production hands the family the world's fixed anchor; where the world names no depth
     * raster the builder falls back to [`LatticeAnchor.wholeDegree`], which floors the corridor's south-west
     * to the **degree**. Two corridors in the same degree therefore draw the *same* family — the property a
     * fine-layer tile cache needs, since a corridor-derived origin would give each arm its own lattice and so
     * its own tiles.
     */
    @Test
    fun theWholeDegreeFallbackIsOneOriginPerDegree() {
        val inOneDegree = LatticeAnchor.wholeDegree(BBox(43.20, 43.60, 6.10, 6.90))
        val sameDegree = LatticeAnchor.wholeDegree(BBox(43.95, 43.99, 6.95, 6.99))
        val otherDegree = LatticeAnchor.wholeDegree(BBox(44.10, 44.40, 7.10, 7.40))

        assertEquals(
            "two corridors in one degree share the fallback origin (latitude)",
            inOneDegree.latSouth, sameDegree.latSouth, 0.0
        )
        assertEquals(
            "two corridors in one degree share the fallback origin (longitude)",
            inOneDegree.lonWest, sameDegree.lonWest, 0.0
        )
        assertEquals("the reference latitude is that whole degree's centre", 43.5, inOneDegree.referenceLat, 0.0)
        assertTrue(
            "a corridor in another degree draws another origin",
            inOneDegree.latSouth != otherDegree.latSouth
        )

        // The families the builder would draw on those two origins are identical, so their cells coincide.
        val a = LatticeFamily.of(inOneDegree, coarseCellM = 100.0, fineCellM = 20.0)
        val b = LatticeFamily.of(sameDegree, coarseCellM = 100.0, fineCellM = 20.0)
        assertEquals("the two families share the fine origin (latitude)", a.fine.latSouth, b.fine.latSouth, 0.0)
        assertEquals("the two families share the fine origin (longitude)", a.fine.lonWest, b.fine.lonWest, 0.0)
        assertEquals("and the same water takes the same fine row", a.fine.rowOf(43.50), b.fine.rowOf(43.50))
    }

    /**
     * **The ratio has one home (D19).** [`LatticeFamily.of`]'s own `ratio`, [`WalkWindows.layerRatio`] and
     * the seam's neighbour arithmetic in [`WalkWindows.crossLayerSlots`] all read [`latticeRatioOf`], so this
     * pins the one convention they share: the coarse cell over the fine, rounded to the nearest integer and
     * floored at one. An even ratio is included because a shipped clamp permits one, and a pair below the
     * rounding threshold is included because the floor at one is what makes it a read rather than a zero.
     */
    @Test
    fun theRatioConventionHasOneHome() {
        assertEquals("an exact five-fold pair", 5, latticeRatioOf(100.0, 20.0))
        assertEquals("an even ratio, permitted by the shipped clamp", 4, latticeRatioOf(80.0, 20.0))
        assertEquals("a one-to-one pair", 1, latticeRatioOf(20.0, 20.0))
        assertEquals("a pair below the rounding threshold floors at one", 1, latticeRatioOf(5.0, 20.0))
        assertEquals(
            "and the family reads the same derivation",
            4,
            LatticeFamily.of(corridor, coarseCellM = 80.0, fineCellM = 20.0).ratio
        )
    }

    /**
     * **The window index is reused across arms of the same shape (D18).** The index `(layer, row, col)` →
     * slot is a pure function of the windows' shape, not their contents, so a second arm whose windows carry
     * the same shape reuses the first's index instead of re-walking every cell. Pinned by the cache's own
     * build count: two equal-shape walks build once. Reverted to a per-arm rebuild the count moves to two —
     * the regression this guards.
     */
    @Test
    fun theWindowIndexIsReusedAcrossArmsOfTheSameShape() {
        WalkIndexCache.clear()
        val family = LatticeFamily.of(corridor, coarseCellM = 100.0, fineCellM = 20.0)

        fun walk(): WalkWindows {
            val coarseGrid = MultipassGrid(
                family.coarse.latSouth, family.coarse.lonWest,
                family.coarse.cellSizeDegLat, family.coarse.cellSizeDegLon,
                2, 2, family.coarse.cellM, baseCostSec(family.coarse.cellM, 28.0)
            )
            val fineGrid = MultipassGrid(
                family.fine.latSouth, family.fine.lonWest,
                family.fine.cellSizeDegLat, family.fine.cellSizeDegLon,
                10, 10, family.fine.cellM, baseCostSec(family.fine.cellM, 28.0)
            )
            return WalkWindows.onLattice(
                family.layers,
                listOf(
                    WalkWindow(coarseGrid, 0, 0, layer = 0),
                    WalkWindow(fineGrid, 0, 0, layer = 1)
                )
            )
        }

        val first = walk()
        val builtAfterFirst = WalkIndexCache.buildCount
        val second = walk()

        assertEquals("the first arm builds the index", 1, builtAfterFirst)
        assertEquals(
            "an equal-shape arm reuses it rather than rebuilding",
            builtAfterFirst,
            WalkIndexCache.buildCount
        )
        assertEquals("the reused index answers the same slot count", first.size, second.size)
    }
}
