package ykws.android.maro.spatial.multipass

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.spatial.SpatialOperations
import ykws.android.maro.spatial.Units
import kotlin.math.abs

/**
 * **The seam between the two resolutions — Phase 5's own gate.**
 *
 * The first walk is two layers on one lattice family: a coarse 100 m interior and a fine 20 m band, the
 * fine pair exactly one fifth of the coarse one on one origin. Phase 4 built the two layers and left their
 * water **unreachable from each other**; this gate is the crossing itself — the A\* now steps across the
 * seam, and it prices that step **from the two cell centres at the pace**, never from the destination
 * cell's own size.
 *
 * The fixture is a straight channel with a vertical seam: the fine band west of the seam, the coarse
 * interior east of it, aligned on an exact coarse-block face so the many-to-one relation is arithmetic.
 */
class SeamCrossingTest {

    private val corridor = BBox(43.45, 43.55, 6.95, 7.05)
    private val paceKn = 28.0
    private val paceMps = Units.knotsToMps(paceKn)

    /** One window on [lattice], its south-west corner [rowOffset] × [colOffset] cells from the origin. */
    private fun windowOn(
        lattice: WalkLattice, rowOffset: Int, colOffset: Int, rows: Int, cols: Int, layer: Int
    ): WalkWindow = WalkWindow(
        MultipassGrid(
            lattice.latSouth + rowOffset * lattice.cellSizeDegLat,
            lattice.lonWest + colOffset * lattice.cellSizeDegLon,
            lattice.cellSizeDegLat, lattice.cellSizeDegLon, rows, cols, lattice.cellM,
            baseCostSec(lattice.cellM, paceKn)
        ),
        rowOffset, colOffset, layer
    )

    /**
     * The two-layer walk: the fine band (layer 1) over five coarse columns west of the seam, the coarse
     * interior (layer 0) over five coarse columns east of it, both two coarse rows tall.
     */
    private fun hybridWalk(): WalkWindows {
        val family = LatticeFamily.of(corridor, coarseCellM = 100.0, fineCellM = 20.0)
        val fine = windowOn(family.fine, 0, 0, rows = 10, cols = 25, layer = 1)
        val coarse = windowOn(family.coarse, 0, 5, rows = 2, cols = 5, layer = 0)
        return WalkWindows.onLattice(family.layers, listOf(coarse, fine))
    }

    /**
     * **The seam's test is the clock's.** The A\*'s `g` on a seam-crossing path equals that path's own
     * timed cost — the metres between its consecutive cell centres at the pace. Priced the old way, from
     * the destination cell's own size, the crossing would be a fifth (or five times) the distance it
     * covered, and `g` would stand far from the geometry it names.
     */
    @Test
    fun aSeamCrossingPathIsPricedFromTheTwoCellCentres() = runTest {
        val walk = hybridWalk()
        val outcome = MultipassSearch.searchWalk(walk, CellIndex(2, 0), CellIndex(0, 9), paceMps)
        val path = outcome.path
        assertNotNull("the seam connects the two layers", path)

        val centres = path!!.map { walk.center(it.layer, it.row, it.col) }
        val timedGeometric =
            centres.zipWithNext { a, b -> SpatialOperations.haversine(a, b) }.sum() / paceMps

        assertEquals(
            "the A*'s g is the path's own timed length, seam crossing included",
            timedGeometric, outcome.costSec, timedGeometric * 0.01
        )
    }

    /**
     * **The equivalence test asserts the cost, never the point list.** Over the same water a two-layer
     * solve and a uniform fine solve must agree on **total time**: the coarse interior covers the same
     * geometry as five fine cells, so the seam crossing must not inflate it. The two grids index the same
     * channel differently, so their lines may differ; only the time is named.
     */
    @Test
    fun aHybridSolveAndAUniformFineSolveAgreeOnTotalTime() = runTest {
        val hybrid = MultipassSearch.searchWalk(hybridWalk(), CellIndex(2, 0), CellIndex(0, 9), paceMps)
        assertNotNull("the hybrid walk crosses the seam", hybrid.path)

        // The same channel as one 20 m grid. The hybrid aim's coarse (0, 9) centre is the central fine cell
        // of that block, (2, 47) — the same point, so the two solves run between the same two places.
        val family = LatticeFamily.of(corridor, coarseCellM = 100.0, fineCellM = 20.0)
        val fine = windowOn(family.fine, 0, 0, rows = 10, cols = 50, layer = 0)
        val uniform = MultipassSearch.search(fine.grid, CellIndex(2, 0), CellIndex(2, 47), paceMps)
        assertNotNull("the uniform fine walk connects the same two points", uniform.path)

        assertTrue(
            "the hybrid's total time is the fine grid's own, within the discretisation",
            abs(hybrid.costSec - uniform.costSec) / uniform.costSec < 0.01
        )
    }

    /**
     * **Phase 6 — the local size travels with the water.** The size the drawn tail reads at a point is the
     * cell the water there was resolved at: the fine band's 20 m where the fine window holds water, the
     * interior's 100 m in the open. This is the fact the corner radii, the carve reach and the disc read.
     */
    @Test
    fun theLocalCellSizeFollowsTheWaterUnderThePoint() = runTest {
        val walk = hybridWalk()

        assertEquals(
            "the band's own 20 m cell over fine water",
            20.0, walk.cellSizeAt(walk.center(1, 2, 0)), 0.0
        )
        assertEquals(
            "the interior's own 100 m cell in the open",
            100.0, walk.cellSizeAt(walk.center(0, 1, 9)), 0.0
        )
    }
}
