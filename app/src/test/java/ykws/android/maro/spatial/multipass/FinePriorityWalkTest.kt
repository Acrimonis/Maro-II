package ykws.android.maro.spatial.multipass

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.DepthSample
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.spatial.SpatialOperations
import ykws.android.maro.spatial.Units
import kotlin.math.PI
import kotlin.math.abs

/**
 * **The two-layer walk's reading rule** — a passable fine cell supersedes the coarse cell over the same
 * water, in every engine that builds a fine layer.
 *
 * The rule is the walk's own, not the rasterizer's: no grid's content moves, and a single-grid walk
 * (`avoid`) is untouched. What these fixtures pin is the **read**: where a coarse copy and a passable fine
 * cell stand over one water, the fine cell is the one the search walks and the coarse cell is never a node
 * — so a price written into the fine layer (`selective`'s depth band) can no longer be bypassed by the
 * free coarse copy. The essential half is pinned beside it: where the fine grid is **land** at that
 * coordinate, the coarse cell keeps its ordinary role, so a collar's land edge never walls off open water.
 */
class FinePriorityWalkTest {

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
     * **The collar walk.** The coarse interior (layer 0) covers coarse cells `(0..2, 0..9)`; the fine
     * collar (layer 1) covers coarse cells `(0..2, 2..4)` — fine cells `(0..14, 10..24)` — so the two
     * layers stand over **the same water** across three coarse columns, the exact overlap the priority is
     * about. Every cell is free and unpriced; the price's own fixture prices the band it needs.
     */
    private fun hybridWalk(): WalkWindows {
        val family = LatticeFamily.of(corridor, coarseCellM = 100.0, fineCellM = 20.0)
        val coarse = windowOn(family.coarse, 0, 0, rows = 3, cols = 10, layer = 0)
        val fine = windowOn(family.fine, 0, 10, rows = 15, cols = 15, layer = 1)
        return WalkWindows.onLattice(family.layers, listOf(coarse, fine))
    }

    /** The same corridor as a **coarse grid alone** — the water the priority takes the free copy of. */
    private fun coarseOnlyWalk(): WalkWindows {
        val family = LatticeFamily.of(corridor, coarseCellM = 100.0, fineCellM = 20.0)
        val coarse = windowOn(family.coarse, 0, 0, rows = 3, cols = 10, layer = 0)
        return WalkWindows.onLattice(family.layers, listOf(coarse))
    }

    /**
     * **The priced band with a way around it.** [hybridWalk]'s own two layers, but only the collar's middle
     * fine column (lattice column 17, local column 7) is priced — one cell wide, over the collar's middle
     * rows. Its outer two rows stay unpriced, so an unpriced detour exists beside the priced band and the
     * same walk solved at two cursors tells the price apart from the reading rule.
     */
    private fun pricedBandWithDetourWalk(): WalkWindows {
        val family = LatticeFamily.of(corridor, coarseCellM = 100.0, fineCellM = 20.0)
        val coarse = windowOn(family.coarse, 0, 0, rows = 3, cols = 10, layer = 0)
        val fine = windowOn(family.fine, 0, 10, rows = 15, cols = 15, layer = 1)
        for (r in 1 until fine.grid.rows - 1) fine.grid.applyDepthPriceCoef(r, 7, 1.0)
        return WalkWindows.onLattice(family.layers, listOf(coarse, fine))
    }

    /** Whether a path reads a cell the depth band prices — the one reading the pass's own λ moves. */
    private fun WalkWindows.readsDepthBand(cell: CellIndex): Boolean =
        depthCoef(rawSlotOf(cell.layer, cell.row, cell.col)) > 0.0

    /**
     * The two-layer walk the even-ratio fixture reads: [hybridWalk]'s shape, its fine window over coarse
     * columns 2–4 at whatever [ratio] the family settles.
     */
    private fun evenRatioWalk(coarseCellM: Double, fineCellM: Double, ratio: Int): WalkWindows {
        val family = LatticeFamily.of(corridor, coarseCellM, fineCellM)
        assertEquals("the family settled the intended ratio", ratio, family.ratio)
        val coarse = windowOn(family.coarse, 0, 0, rows = 3, cols = 10, layer = 0)
        val fine = windowOn(family.fine, 0, 2 * ratio, rows = 3 * ratio, cols = 3 * ratio, layer = 1)
        return WalkWindows.onLattice(family.layers, listOf(coarse, fine))
    }

    /**
     * **The depth price binds.** The collar's water is passable on both layers and priced on the fine one
     * alone. That the free coarse copy is not read is pinned by the two walks above: the search crosses the
     * collar on the fine layer, and the coarse-only walk reads the very same water, so the water is there.
     *
     * The price's own claim cannot rest on two walks — a cheaper priced walk would confound the price with
     * the supersession — so it is told on **one** walk: [pricedBandWithDetourWalk] prices the collar's middle
     * column and leaves its outer rows free, an unpriced detour beside the priced band. Solved at cursor 0
     * the search holds the band and reads the priced cells; solved at the pass's own λ it pays the detour and
     * reads none of them. Same walk, same ends, only the λ moved.
     */
    @Test
    fun aPassableFineCollarSupersedesTheCoarseCopyAndItsPriceBinds() = runTest {
        val k = 40.0
        val unpriced = MultipassSearch.searchWalk(hybridWalk(), CellIndex(1, 0), CellIndex(1, 9), paceMps)
        val coarseOnly = MultipassSearch.searchWalk(coarseOnlyWalk(), CellIndex(1, 0), CellIndex(1, 9), paceMps)

        assertNotNull("the two-layer walk crosses the collar", unpriced.path)
        assertNotNull("and the coarse-only walk crosses the same water", coarseOnly.path)
        assertTrue("the collar is walked on the fine layer", unpriced.path!!.any { it.layer == 1 })
        assertTrue(
            "no coarse cell over the collar is read",
            unpriced.path!!.none { it.layer == 0 && it.col in 2..4 }
        )
        assertTrue(
            "the coarse-only walk does read the collar's water, so the water is there",
            coarseOnly.path!!.any { it.layer == 0 && it.col in 2..4 }
        )

        val band = pricedBandWithDetourWalk()
        val held = MultipassSearch.searchWalk(band, CellIndex(1, 0), CellIndex(1, 9), paceMps, depthK = 0.0)
        val detour = MultipassSearch.searchWalk(band, CellIndex(1, 0), CellIndex(1, 9), paceMps, depthK = k)

        assertNotNull("the priced band is crossable", held.path)
        assertNotNull("and so is the detour beside it", detour.path)
        assertTrue(
            "at cursor 0 the walk holds the band and reads its priced cells",
            held.path!!.any { band.readsDepthBand(it) }
        )
        assertTrue(
            "at the pass's λ the price pushes the same walk onto the unpriced detour",
            detour.path!!.none { band.readsDepthBand(it) }
        )
        assertTrue(
            "crossing the priced column on an outer, unpriced row",
            detour.path!!.any { it.layer == 1 && it.col == 17 && (it.row == 0 || it.row == 14) }
        )
    }

    /**
     * **An even layer ratio reads deterministically and never throws.** The shipped clamps admit even ratios
     * — a 100 m coarse cell with a 10 m fine cell is 10, and 40 with 20 is 2 — so a family over one must
     * resolve the reading rather than refuse it. Where a coarse cell's centre falls on a fine-lattice
     * **vertex** shared by four fine cells, the tie resolves by the `floor` convention every point read uses:
     * the walk takes the cell whose low corner is that vertex, the one north-east of the centre.
     */
    @Test
    fun anEvenLayerRatioResolvesTheFineCopyDeterministically() {
        for ((coarseCellM, fineCellM, ratio) in listOf(
            Triple(40.0, 20.0, 2),
            Triple(100.0, 10.0, 10)
        )) {
            val walk = evenRatioWalk(coarseCellM, fineCellM, ratio)

            val copy = walk.supersedingSlot(0, 1, 3)
            assertTrue("ratio $ratio: the coarse cell's fine copy is passable", copy >= 0)
            assertEquals("ratio $ratio: and it is the coordinate's own read", copy, walk.slotOf(1, 3))
            assertEquals("ratio $ratio: standing on the fine layer", 1, walk.layerOf(copy))

            val half = ratio / 2
            assertEquals(
                "ratio $ratio: the cell whose low corner is the shared vertex",
                walk.rawSlotOf(1, ratio + half, 3 * ratio + half),
                copy
            )
        }
    }

    /**
     * **A superseded coarse seam target redirects to its fine copy.** The collar (blocks 2–4) ends at a
     * block (5) whose only passable fine water is its central column; every coarse cell over block 5 is
     * superseded, so no read coarse cell borders it, and block 5's outer fine columns are land, so no fine
     * step reaches its water either. The seam crossing off block 4's east face is the **only** edge into
     * that water — dropping it, the asymmetry the reading-rule review named, loses the path; the redirect
     * the same-layer neighbour step performs keeps it.
     */
    @Test
    fun aSupersededCoarseSeamTargetRedirectsToItsFineCopy() = runTest {
        val family = LatticeFamily.of(corridor, coarseCellM = 100.0, fineCellM = 20.0)
        val coarse = windowOn(family.coarse, 0, 0, rows = 3, cols = 10, layer = 0)
        val fine = windowOn(family.fine, 0, 10, rows = 15, cols = 25, layer = 1)
        // Blocks 2–4 (fine cols 10–24) and block 6 (fine cols 30–34) are water; block 5's outer columns
        // (fine cols 25, 26) are land, so the collar and the far block share no fine edge.
        for (r in 0 until fine.grid.rows) {
            for (localC in 15 until 17) fine.grid.markLand(r, localC)
        }
        val walk = WalkWindows.onLattice(family.layers, listOf(coarse, fine))

        assertEquals(
            "block 5's coarse cell is superseded by its central fine column",
            1, walk.layerOf(walk.slotOf(1, 5))
        )

        val path = MultipassSearch.searchWalk(walk, CellIndex(1, 0), CellIndex(1, 8), paceMps).path

        assertNotNull("the far water is reached across the block the seam redirects into", path)
        assertTrue(
            "and it is read on block 5's fine copy, never on the coarse cell",
            path!!.any { it.layer == 1 && it.col == 27 }
        )
        assertTrue(
            "no coarse cell over the superseded block is read",
            path.none { it.layer == 0 && it.col == 5 }
        )
    }

    /**
     * **Where the fine grid is land, the coarse cell keeps its role.** The fine grid is marked land over
     * coarse column 4, so the priority must **not** supersede that column's coarse cells: the path stays
     * fine across the collar (columns 2–3) and returns to the coarse layer at column 4. A blanket priority
     * would have walled that water off.
     */
    @Test
    fun aFineGridsLandAtTheCollarsEdgeLeavesTheCoarseWaterReadable() = runTest {
        val family = LatticeFamily.of(corridor, coarseCellM = 100.0, fineCellM = 20.0)
        val coarse = windowOn(family.coarse, 0, 0, rows = 3, cols = 10, layer = 0)
        val fine = windowOn(family.fine, 0, 10, rows = 15, cols = 15, layer = 1)
        // The fine grid's own cells over coarse column 4 are land — the fine layer has no water there.
        for (r in 0 until fine.grid.rows) {
            for (localC in 10 until 15) fine.grid.markLand(r, localC)
        }
        val walk = WalkWindows.onLattice(family.layers, listOf(coarse, fine))

        val path = MultipassSearch.searchWalk(walk, CellIndex(1, 0), CellIndex(1, 9), paceMps).path

        assertNotNull("the walk crosses the collar and the open water beyond", path)
        assertTrue("the passable fine collar is walked on the fine layer", path!!.any { it.layer == 1 })
        assertTrue(
            "the coarse water where the fine grid is land is read",
            path.any { it.layer == 0 && it.col == 4 }
        )
        assertTrue(
            "and no coarse cell over passable fine water is read",
            path.none { it.layer == 0 && it.col in 2..3 }
        )
    }

    /** **An end inside a collar is a fine cell.** The layer-agnostic lookup follows the same rule. */
    @Test
    fun anEndStandingInTheCollarResolvesToAFineCell() {
        val walk = hybridWalk()

        val inCollar = walk.slotOf(1, 3)
        assertTrue("an end inside the collar resolves at all", inCollar >= 0)
        assertEquals("and it is a fine cell", 1, walk.layerOf(inCollar))

        val open = walk.slotOf(1, 8)
        assertEquals("an end in open water stays on the interior", 0, walk.layerOf(open))
    }

    /**
     * **`evolutive`'s own walk reads its ribbon on the fine layer.** Built through the real grid builder
     * over a straight coast, the walk reads fine exactly where a passable fine cell stands over the same
     * water — the coastal ribbon — and coarse in the open. This is the builder-level face of the rule, and
     * the reason `evolutive`'s line and clock move with it.
     */
    @Test
    fun theEvolutiveWalkReadsItsRibbonOnTheFineLayer() = runBlocking {
        val world = CoastalWorld(coastLat = 43.4800, bandWidthM = 300.0)
        val from = RoutePoint(43.4700, 6.9800)
        val to = RoutePoint(43.4700, 7.0200)
        val ctx = RouteGridBuilder(EvolutiveGridPlan).buildGrid(
            world, from, to, AppConfig.routeAvoidCorridorReachM, paceKn
        )

        assertNotNull("the corridor builds", ctx)
        val walk = ctx!!.windows
        assertNotNull("evolutive's first walk is two-layer", walk)

        val coarseWindow = walk!!.windows.first { it.layer == 0 }
        var fineReads = 0
        var coarseReads = 0
        for (row in 0 until coarseWindow.grid.rows) {
            for (col in 0 until coarseWindow.grid.cols) {
                val rr = row + coarseWindow.rowOffset
                val cc = col + coarseWindow.colOffset
                val slot = walk.slotOf(rr, cc)
                if (slot < 0) continue
                if (walk.layerOf(slot) == 1) fineReads++ else coarseReads++
                val over = walk.supersedingSlot(0, rr, cc)
                assertEquals(
                    "a coarse cell reads the fine layer exactly where its fine copy is passable ($rr, $cc)",
                    over >= 0,
                    walk.layerOf(slot) == 1
                )
            }
        }

        assertTrue("the coastal ribbon is read fine, so evolutive moved", fineReads > 0)
        assertTrue("and the open water is still read coarse", coarseReads > 0)
    }

    /** A world with one straight coast and the priced band — `evolutive`'s own ribbon input. */
    private class CoastalWorld(
        private val coastLat: Double,
        override val bandWidthM: Double = 300.0
    ) : MultipassWorld {
        override val coastlineReady: Boolean get() = true
        override val depthReady: Boolean get() = false
        override val regionBounds: BBox? get() = null

        private val mPerDegLat = SpatialOperations.EARTH_RADIUS_M * PI / 180.0
        private val coast = listOf(LatLng(coastLat, 6.90), LatLng(coastLat, 7.10))

        override fun segmentsIn(box: BBox): List<MultipassEdge> = emptyList()
        override fun openCoastIn(box: BBox): List<List<LatLng>> = listOf(coast)
        override fun isWater(latitude: Double, longitude: Double): Boolean = true
        override fun distanceToCoastM(latitude: Double, longitude: Double): Double =
            abs(latitude - coastLat) * mPerDegLat
        override fun depthAt(latitude: Double, longitude: Double): DepthSample = DepthSample.NONE
    }
}
