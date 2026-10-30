package ykws.android.maro.spatial.multipass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.DepthGrid
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.spatial.Units
import ykws.android.maro.spatial.multipass.GateTestFixtures.at
import kotlin.math.hypot

/**
 * **The any-angle search's chord, read on the walk's own cells** — [ChordReader]
 * (`261029_FEAT_PLN_Route_any-angle-search.md` §4.1, P1): priced per cell crossed as a step prices a cell, refused by
 * land, a Blocked body, a cell no window holds and the gate's wall, the ends cut at the waiver's rim and never waived,
 * the seam read on the fine lattice, and one answer from either end.
 */
class ChordCostTest {

    private val corridor = BBox(43.45, 43.55, 6.95, 7.05)
    private val paceKn = 28.0
    private val paceMps = Units.knotsToMps(paceKn)

    /** A zone price that answers in any limit in force — enough to prove the chord reads the step's own price. */
    private val zonePrice = ZonePrice { cellM, interior, collar, band ->
        (if (interior > 0.0) 0.4 else 0.0) * cellM + (if (collar > 0.0 || band > 0.0) 0.1 * cellM else 0.0)
    }
    private val price = CellPrice(zonePrice, 0.0, null)

    private fun windowOn(lattice: WalkLattice, rowOffset: Int, colOffset: Int, rows: Int, cols: Int, layer: Int) =
        WalkWindow(
            MultipassGrid(
                lattice.latSouth + rowOffset * lattice.cellSizeDegLat,
                lattice.lonWest + colOffset * lattice.cellSizeDegLon,
                lattice.cellSizeDegLat, lattice.cellSizeDegLon, rows, cols, lattice.cellM,
                baseCostSec(lattice.cellM, paceKn)
            ),
            rowOffset, colOffset, layer
        )

    private fun coarseGrid(rows: Int, cols: Int): MultipassGrid =
        windowOn(LatticeFamily.of(corridor, 100.0, 20.0).coarse, 0, 0, rows, cols, 0).grid

    private fun WalkWindows.chord(a: Int, b: Int, guard: StepGuard? = null, ends: EndRule = EndRule.BOUND) =
        ChordReader(this, price, guard, ends).costSec(a, b)

    // ── The price ────────────────────────────────────────────────────────────

    @Test
    fun aChordAlongARowCostsTheSumOfItsSteps() {
        val grid = coarseGrid(3, 12)
        for (c in 0 until 12) grid.addSourceCost(1, c, c * 1.5, MultipassCellState.BAND)
        grid.applyZoneLimit(1, 5, 6.0)
        grid.applyCollarLimit(1, 7, 8.0)
        val walk = WalkWindows.of(grid)
        fun slot(c: Int) = walk.slotOf(1, c)
        val chord = walk.chord(slot(1), slot(10))
        val steps = (1 until 10).sumOf { walk.chord(slot(it), slot(it + 1)) }
        assertEquals("the chord is the sum of its one-cell links", steps, chord, 1e-9 * chord)
        fun cellSec(c: Int) = price.cellSec(walk, slot(c), 100.0)
        val halves = 0.5 * cellSec(1) + (2..9).sumOf { cellSec(it) } + 0.5 * cellSec(10)
        assertEquals("half of each end cell, the whole of every cell between", halves, chord, 1e-9 * chord)
    }

    @Test
    fun aDiagonalChordCostsTheSumOfItsDiagonalSteps() {
        val grid = coarseGrid(4, 4)
        for (r in 0 until 4) {
            for (c in 0 until 4) grid.addSourceCost(r, c, (r * 4 + c) * 0.7, MultipassCellState.BAND)
        }
        val walk = WalkWindows.of(grid)
        val chord = walk.chord(walk.slotOf(0, 0), walk.slotOf(3, 3))
        val steps = (0 until 3).sumOf { walk.chord(walk.slotOf(it, it), walk.slotOf(it + 1, it + 1)) }
        assertEquals(steps, chord, 1e-9 * chord)
    }

    @Test
    fun overOpenWaterAChordCostsWhatTheStepsAlwaysPaid() {
        val walk = WalkWindows.of(coarseGrid(3, 12))
        val chord = walk.chord(walk.slotOf(1, 0), walk.slotOf(1, 11))
        assertEquals(
            "eleven cells of open water at the pace", 11 * baseCostSec(100.0, paceKn), chord, 1e-9 * chord
        )
    }

    // ── The water's refusals ─────────────────────────────────────────────────

    @Test
    fun landABlockedBodyAndACellNoWindowHoldsRefuseTheChord() {
        val land = coarseGrid(3, 12).apply { markLand(1, 6) }
        val walkLand = WalkWindows.of(land)
        assertTrue("through land", walkLand.chord(walkLand.slotOf(1, 1), walkLand.slotOf(1, 10)).isNaN())

        val blocked = coarseGrid(3, 12).apply { markGateBlocked(1, 6) }
        val walkBlocked = WalkWindows.of(blocked)
        assertTrue(
            "through a Blocked body", walkBlocked.chord(walkBlocked.slotOf(1, 1), walkBlocked.slotOf(1, 10)).isNaN()
        )

        // One lattice, two windows with a two-column gap between them.
        val lattice = LatticeFamily.of(corridor, 100.0, 20.0).coarse
        val chain = WalkWindows.onLattice(
            lattice, listOf(windowOn(lattice, 0, 0, 3, 5, 0), windowOn(lattice, 0, 7, 3, 5, 0))
        )
        assertTrue(
            "across a cell no window holds",
            chain.chord(chain.slotOf(1, 1), chain.slotOf(1, 9)).isNaN()
        )
        assertFalse(
            "while each window's own chord stands", chain.chord(chain.slotOf(1, 0), chain.slotOf(1, 4)).isNaN()
        )
    }

    @Test
    fun aChordThroughAVertexReadsNeitherCellBesideIt() {
        val grid = coarseGrid(3, 3).apply {
            markLand(0, 1)
            markLand(1, 0)
            markLand(1, 2)
            markLand(2, 1)
        }
        val walk = WalkWindows.of(grid)
        val chord = walk.chord(walk.slotOf(0, 0), walk.slotOf(2, 2))
        assertEquals(
            "the diagonal pays its three cells over its own length alone",
            2.0 * Math.sqrt(2.0) * baseCostSec(100.0, paceKn), chord, 1e-9 * chord
        )
    }

    // ── The gate ─────────────────────────────────────────────────────────────

    /**
     * The shelf of [GateEndStepTest]: shallow south of 100 m, 20 m cells over 60–340 m north, 0–420 m east — and,
     * where [rock], a 3 m rock at 227 m north, 200 m east, between two cells' centres: the cells along 250 m north
     * stand 21.2 m off it and the straight between them 19.3 m.
     */
    private class Shelf(hardM: Double = 20.0, rock: Boolean = false) {
        val raster: DepthGrid = GateTestFixtures.raster(200, 220, 2.0) { n, e ->
            if (n < 100.0 || (rock && hypot(n - 227.0, e - 200.0) < 3.0)) 2.0 else 10.0
        }
        val gate = GateTestFixtures.gateOver(raster, hardM = hardM)
        val grid: MultipassGrid = run {
            val world = GateTestFixtures.RasterWorld(raster)
            val box = GateTestFixtures.box(60.0, 340.0, 0.0, 420.0)
            rasterize(
                box, 20.0, 25.0, 0.0, emptyList(), emptyList(), box.latNorth,
                field = GateTestFixtures.gateField(world), gate = gate
            )
        }
        val walk = WalkWindows.of(grid)

        fun slotAt(p: LatLng): Int = grid.cellOf(p.latitude, p.longitude).let { walk.slotOf(it.row, it.col) }
    }

    @Test
    fun underTheWallAChordIsRefusedAndPastItPriced() {
        val far = listOf(at(330.0, 0.0), at(330.0, 420.0))
        val rocky = Shelf(rock = true)
        val rockGuard = StepGuard(rocky.gate, far[0], far[1])
        val (west, east) = listOf(at(250.0, 70.0), at(250.0, 350.0)).map(rocky::slotAt)
        assertTrue("every cell of the row is water", (west..east).all { rocky.walk.passable(it) })
        assertTrue(
            "the straight between the cells passes the rock under the 20 m wall",
            rocky.walk.chord(west, east, rockGuard).isNaN()
        )
        assertFalse("the same chord with no gate read is water", rocky.walk.chord(west, east).isNaN())

        val shelf = Shelf()
        val guard = StepGuard(shelf.gate, far[0], far[1])
        assertFalse(
            "thirty metres off the shelf, where the stored bound proves nothing and the measured read clears it",
            shelf.walk.chord(shelf.slotAt(at(130.0, 70.0)), shelf.slotAt(at(130.0, 350.0)), guard).isNaN()
        )
        assertFalse(
            "a hundred and fifty metres off, clear of the wall",
            shelf.walk.chord(shelf.slotAt(at(250.0, 70.0)), shelf.slotAt(at(250.0, 350.0)), guard).isNaN()
        )
    }

    /**
     * Along 250 m north past the rock, every cell water: a start whose 20 m disc holds the rock's closest approach
     * leaves along the row, the read cut at the rim and the rest keeping the wall; a start 70 m west of it, held
     * loose, sees the same straight refused — an end is never waived whole.
     */
    @Test
    fun anEndsChordIsCutAtTheWaiversRimAndNeverWaived() {
        val rocky = Shelf(rock = true)
        val aim = at(330.0, 400.0)
        val target = rocky.slotAt(at(250.0, 350.0))
        fun chordFrom(start: LatLng): Double {
            val startSlot = rocky.slotAt(start)
            val loose = EndRule(startSlot, rocky.slotAt(aim), strictStart = false, strictAim = true)
            return rocky.walk.chord(startSlot, target, StepGuard(rocky.gate, start, aim), loose)
        }
        assertFalse("the rock inside the start's disc is cut away at the rim", chordFrom(at(250.0, 195.0)).isNaN())
        assertTrue("the rock past the loose start's rim refuses the chord", chordFrom(at(250.0, 125.0)).isNaN())
    }

    /**
     * The start stands 15 m off the shelf, its cell's centre 10 m off: a cell the raster walls. The chord leaving the
     * route's start does not ask its own cell, and stands north into clear water past the rim; the same chord between
     * two cells that are no route end is refused at the walled cell.
     */
    @Test
    fun aRouteEndsOwnWalledCellIsLeftButNoOtherWalledCell() {
        val shelf = Shelf()
        val start = at(115.0, 200.0)
        val aim = at(330.0, 400.0)
        val startSlot = shelf.slotAt(start)
        val north = shelf.slotAt(at(290.0, 210.0))
        val guard = StepGuard(shelf.gate, start, aim)
        assertFalse("the start's own cell is walled", shelf.walk.passable(startSlot))
        val ends = EndRule(startSlot, shelf.slotAt(aim), strictStart = true, strictAim = true)
        assertFalse("the route's start is left north", shelf.walk.chord(startSlot, north, guard, ends).isNaN())
        assertTrue("no other walled cell is", shelf.walk.chord(startSlot, north, guard).isNaN())
    }

    // ── The seam ─────────────────────────────────────────────────────────────

    /**
     * Two layers on one family: the fine band (layer 1) [fineCols] fine columns wide from the origin, ten fine rows,
     * and the coarse interior (layer 0) over coarse columns [coarseFrom] to 9, two coarse rows.
     */
    private fun hybrid(
        fineCols: Int = 25,
        coarseFrom: Int = 5,
        fine: (MultipassGrid) -> Unit = {},
        coarse: (MultipassGrid) -> Unit = {}
    ): WalkWindows {
        val family = LatticeFamily.of(corridor, coarseCellM = 100.0, fineCellM = 20.0)
        val fineWindow = windowOn(family.fine, 0, 0, rows = 10, cols = fineCols, layer = 1).also { fine(it.grid) }
        val coarseWindow = windowOn(family.coarse, 0, coarseFrom, rows = 2, cols = 10 - coarseFrom, layer = 0)
            .also { coarse(it.grid) }
        return WalkWindows.onLattice(family.layers, listOf(coarseWindow, fineWindow))
    }

    @Test
    fun aChordAcrossTheSeamPricesItsFineCellsAtTwentyMetres() {
        val plain = hybrid()
        val from = plain.rawSlotOf(1, 2, 0)
        val to = plain.rawSlotOf(0, 0, 8)
        // From x = 0.5 to x = 42.5 fine units along one row: 42 units of 20 m.
        assertEquals(840.0 / paceMps, plain.chord(from, to), 1e-9)
        val priced = hybrid(fine = { it.addSourceCost(2, 10, 7.0, MultipassCellState.BAND) }, coarse = {
            it.addSourceCost(0, 1, 11.0, MultipassCellState.BAND)
        })
        assertEquals(
            "a whole fine cell's 7 s and a whole coarse cell's 11 s",
            840.0 / paceMps + 18.0, priced.chord(from, to), 1e-9
        )
    }

    /** The fine tile ends two fine columns short of a block's east face, its copy held: those two cells read coarse. */
    @Test
    fun aBlockTheFineTileHoldsInPartPricesItsUnheldCellsAtTheCoarseRate() {
        val walk = hybrid(fineCols = 23, coarseFrom = 0, coarse = {
            it.addSourceCost(0, 4, 10.0, MultipassCellState.BAND)
            it.addSourceCost(0, 2, 50.0, MultipassCellState.BAND)
        })
        val from = walk.rawSlotOf(1, 2, 0)
        val to = walk.rawSlotOf(0, 0, 8)
        assertTrue("the block's copy is held", walk.supersedingSlot(0, 0, 4) >= 0)
        assertEquals(
            "40 m of the block at its coarse 10 s per 100 m; the coarse cell under the fine water unread",
            840.0 / paceMps + 4.0, walk.chord(from, to), 1e-9
        )
    }

    /**
     * A block whose fine copy is land: a chord ending on one of its other fine cells is read on the fine lattice, and
     * the block's coarse cell — a node the walk stands on — reads its own whole cell when the chord leaves it.
     */
    @Test
    fun aBlockWithALandCopyIsReadOnTheFineLatticeAndItsCoarseNodeOnItsOwnCell() {
        fun walkOf(fineSec: Double, coarseSec: Double) = hybrid(
            coarseFrom = 0,
            fine = {
                it.markLand(2, 22)
                if (fineSec > 0.0) it.addSourceCost(3, 23, fineSec, MultipassCellState.BAND)
            },
            coarse = { if (coarseSec > 0.0) it.addSourceCost(0, 4, coarseSec, MultipassCellState.BAND) }
        )
        val plain = walkOf(0.0, 0.0)
        assertTrue("the land copy leaves the coarse cell a node", plain.supersedingSlot(0, 0, 4) < 0)
        val fineEnd = plain.rawSlotOf(1, 3, 23)
        val far = plain.rawSlotOf(0, 0, 8)
        val base = plain.chord(far, fineEnd)
        assertFalse("the chord to the fine cell beside the land copy stands", base.isNaN())
        assertEquals(
            "the coarse cell under the fine water is never read", base, walkOf(0.0, 100.0).chord(far, fineEnd), 1e-9
        )
        // From x = 42.5 to 23.5, y = 2.5 to 3.5: the last half fine column lies in the end's own cell.
        val inEndM = 0.5 / 19.0 * hypot(19.0, 1.0) * 20.0
        assertEquals(
            "the fine end's own seconds over its own piece",
            base + 9.0 * inEndM / 20.0, walkOf(9.0, 0.0).chord(far, fineEnd), 1e-9
        )

        val node = plain.rawSlotOf(0, 0, 4)
        assertEquals(
            "the coarse node's own cell, land copy and all, at its coarse rate: 20 fine units of open water",
            400.0 / paceMps, plain.chord(node, far), 1e-9
        )
        assertEquals(
            "half the node's own cell priced",
            400.0 / paceMps + 2.5, walkOf(0.0, 5.0).chord(node, far), 1e-9
        )
    }

    // ── Symmetry ─────────────────────────────────────────────────────────────

    @Test
    fun aChordReadsTheSameFromEitherEnd() {
        val walk = hybrid(
            fine = { g ->
                for (r in 0 until 10) {
                    for (c in 0 until 25) g.addSourceCost(r, c, ((r * 31 + c * 17) % 13) * 0.37, MultipassCellState.BAND)
                }
            },
            coarse = { g ->
                for (r in 0 until 2) {
                    for (c in 0 until 5) g.addSourceCost(r, c, ((r * 7 + c * 3) % 5) * 1.3, MultipassCellState.BAND)
                }
            }
        )
        val slots = listOf(
            walk.rawSlotOf(1, 0, 0), walk.rawSlotOf(1, 9, 3), walk.rawSlotOf(1, 4, 17), walk.rawSlotOf(1, 7, 24),
            walk.rawSlotOf(0, 0, 5), walk.rawSlotOf(0, 1, 9), walk.rawSlotOf(0, 1, 6)
        )
        for (a in slots) for (b in slots) {
            assertEquals("$a <-> $b", walk.chord(a, b), walk.chord(b, a), 0.0)
        }
        val shelf = Shelf()
        val guard = StepGuard(shelf.gate, at(330.0, 0.0), at(330.0, 420.0))
        val ends = listOf(at(130.0, 70.0), at(130.0, 350.0), at(250.0, 70.0), at(290.0, 390.0)).map(shelf::slotAt)
        for (a in ends) for (b in ends) {
            assertEquals("gated $a <-> $b", shelf.walk.chord(a, b, guard), shelf.walk.chord(b, a, guard), 0.0)
        }
    }
}
