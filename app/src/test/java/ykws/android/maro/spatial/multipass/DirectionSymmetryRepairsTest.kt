package ykws.android.maro.spatial.multipass

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.spatial.SpatialOperations
import ykws.android.maro.spatial.Units
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min

/**
 * **The two repairs of `261020_FEAT_PLN_Route_direction-symmetry.md` that the instrument asked for.**
 *
 * - *The way back out of a fine copy.* A coarse cell steps into a neighbouring block's fine copy; the copy
 *   had no step back, so an end standing on a copy whose fine neighbours are all land could be entered and
 *   never left — a pair armed from that end found no line while the same pair armed from the other did.
 * - *The pull read from both ends.* [MultipassPull.pull] is a forward greedy, so the same chain pulled from
 *   either end could land on two taut lines; [MultipassPull.pullBothWays] keeps the cheaper of the two, chosen
 *   in a canonical direction, so the caller's direction never reaches the decision.
 */
class DirectionSymmetryRepairsTest {

    // ── The step back out of a fine copy ──────────────────────────────────────

    private val corridor = BBox(43.45, 43.55, 6.95, 7.05)
    private val paceKn = 28.0
    private val paceMps = Units.knotsToMps(paceKn)

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
     * **A copy walled in on every fine side.** The collar (fine columns 10–24) is land except the one fine cell
     * at the centre of block `(1, 3)` — fine `(7, 17)`, the block's fine copy — so that cell's eight fine
     * neighbours are land, the coarse cell over it is superseded, and the coarse neighbours around it read as
     * themselves. The only edges that touch the cell are the coarse cells stepping in.
     */
    private fun walledCopyWalk(): WalkWindows {
        val family = LatticeFamily.of(corridor, coarseCellM = 100.0, fineCellM = 20.0)
        val coarse = windowOn(family.coarse, 0, 0, rows = 3, cols = 10, layer = 0)
        val fine = windowOn(family.fine, 0, 10, rows = 15, cols = 15, layer = 1)
        for (r in 0 until fine.grid.rows) for (c in 0 until fine.grid.cols) {
            if (!(r == 7 && c == 7)) fine.grid.markLand(r, c)
        }
        return WalkWindows.onLattice(family.layers, listOf(coarse, fine))
    }

    @Test
    fun aFineCopyWalledInOnEverySideCanBeLeftAsWellAsEntered() = runTest {
        val walk = walledCopyWalk()
        val copy = CellIndex(7, 17, layer = 1)
        val far = CellIndex(1, 9)
        assertTrue("the fixture's cell is a block's fine copy", walk.isFineCopyCell(1, 7, 17))
        assertEquals("and it supersedes block (1, 3)'s coarse cell", walk.rawSlotOf(1, 7, 17), walk.supersedingSlot(0, 1, 3))

        val into = MultipassSearch.searchWalk(walk, far, copy, paceMps)
        val out = MultipassSearch.searchWalk(walk, copy, far, paceMps)

        assertNotNull("the copy is entered from the coarse water", into.path)
        assertTrue("the copy has a way out of its own cell", out.startOpenSteps > 0)
        assertNotNull("and the walk leaves it — the pair is routable from either end", out.path)
        assertEquals(
            "the two directions pay the same for the pair",
            into.costSec, out.costSec, 1e-6
        )
    }

    @Test
    fun theStepBackNamesOnlyCoarseCellsTheWalkReadsAsThemselves() {
        val walk = walledCopyWalk()
        val targets = (-1..1).flatMap { dr -> (-1..1).map { dc -> dr to dc } }
            .filter { it != 0 to 0 }
            .map { (dr, dc) -> walk.coarseNeighbourOfCopy(1, 7, 17, dr, dc) }
        assertEquals("every coarse neighbour of the block is a node", 8, targets.count { it >= 0 })
        assertTrue("all on the coarse layer", targets.all { walk.layerOf(it) == 0 })
        assertEquals("a cell off the block's centre has no step back", -1, walk.coarseNeighbourOfCopy(1, 7, 18, 0, 1))
        assertEquals("nor does a coarse cell", -1, walk.coarseNeighbourOfCopy(0, 1, 3, 0, 1))
    }

    /**
     * **The back-edge is the forward edge reversed, at any ratio.** For every coarse node the walk reads and
     * every one of its eight steps, a passable fine copy over the neighbour is stepped to — and from that copy
     * the opposite step names the node again. Odd and even ratios are both read, and a block at the window's
     * south-west corner puts the negative-row arithmetic on the path.
     */
    @Test
    fun everyStepIntoAFineCopyHasItsStepBackAtOddAndEvenRatios() {
        val steps = (-1..1).flatMap { dr -> (-1..1).map { dc -> dr to dc } }.filter { it != 0 to 0 }
        for ((coarseCellM, fineCellM, ratio) in listOf(Triple(100.0, 20.0, 5), Triple(40.0, 20.0, 2), Triple(100.0, 10.0, 10))) {
            val family = LatticeFamily.of(corridor, coarseCellM, fineCellM)
            assertEquals("the family settled the intended ratio", ratio, family.ratio)
            val coarse = windowOn(family.coarse, 0, 0, rows = 3, cols = 10, layer = 0)
            val fine = windowOn(family.fine, 0, 2 * ratio, rows = 3 * ratio, cols = 3 * ratio, layer = 1)
            val walk = WalkWindows.onLattice(family.layers, listOf(coarse, fine))
            var forwardEdges = 0
            for (row in 0 until 3) for (col in 0 until 10) {
                val node = walk.rawSlotOf(0, row, col)
                if (node < 0 || walk.supersedingSlot(0, row, col) >= 0) continue
                for ((dr, dc) in steps) {
                    val copy = walk.supersedingSlot(0, row + dr, col + dc)
                    if (copy < 0) continue
                    forwardEdges++
                    assertEquals(
                        "ratio $ratio: the copy over ($row,$col)+($dr,$dc) steps back to ($row,$col)",
                        node,
                        walk.coarseNeighbourOfCopy(1, walk.rowOf(copy), walk.colOf(copy), -dr, -dc)
                    )
                }
            }
            assertTrue("ratio $ratio: the fixture holds forward edges to mirror ($forwardEdges)", forwardEdges > 0)
            var backEdges = 0
            for (r in 0 until fine.grid.rows) for (c in 0 until fine.grid.cols) {
                val slot = walk.rawSlotOf(1, r, 2 * ratio + c)
                if (slot < 0 || !walk.isFineCopyCell(1, r, 2 * ratio + c)) continue
                for ((dr, dc) in steps) if (walk.coarseNeighbourOfCopy(1, r, 2 * ratio + c, dr, dc) >= 0) backEdges++
            }
            assertEquals("ratio $ratio: no back-edge without a forward edge", forwardEdges, backEdges)
        }
    }

    // ── The pull read from both ends ──────────────────────────────────────────

    private val marginM = 25.0
    private val stepM = MultipassPull.clearanceStep(marginM)
    private val frameLat = 43.50
    private val frameLon = 7.00
    private val mPerDegLat = SpatialOperations.EARTH_RADIUS_M * PI / 180.0
    private val mPerDegLon = mPerDegLat * cos(Math.toRadians(frameLat))

    private fun pt(eastM: Double, northM: Double) = LatLng(frameLat + northM / mPerDegLat, frameLon + eastM / mPerDegLon)

    private val wall1 = pt(0.0, 0.0)
    private val wall2 = pt(400.0, 130.0)
    private val start = pt(-400.0, 0.0)
    private val aim = pt(900.0, 250.0)

    private fun dist(a: LatLng, b: LatLng) = SpatialOperations.haversine(a, b)

    private val field = RouteCostField.ofHard { p -> min(dist(p, wall1), dist(p, wall2)) }

    /** A jagged chain from [start] to [aim] in 100 m steps, kept clear of both walls by the margin. */
    private fun chain(rng: Random): List<LatLng> {
        while (true) {
            val out = ArrayList<LatLng>()
            out.add(start)
            var east = -400.0
            while (east < 800.0) {
                east += 100.0
                out.add(pt(east, 20.0 + rng.nextDouble() * 220.0))
            }
            out.add(aim)
            if (out.drop(1).dropLast(1).all { field.hardDistanceM(it) > marginM + 5.0 }) return out
        }
    }

    @Test
    fun theTwoSidedPullIsOneLineWhicheverEndItIsHandedFrom() {
        // Without a pace the candidates are compared by length; with one, by the price walk's seconds.
        for (paceMps in listOf(0.0, 8.0)) oneLineBothWays(paceMps)
    }

    private fun oneLineBothWays(paceMps: Double) {
        val setup = PullSetup(marginM, stepM, stepM, field, start, aim, paceMps = paceMps)
        val rng = Random(20261020L)
        var sensitive = 0
        repeat(60) {
            val path = chain(rng)
            val forwardOnly = MultipassPull.pull(setup, path)
            val reversedOnly = MultipassPull.pull(setup.reversed(), path.reversed()).reversed()
            if (forwardOnly != reversedOnly) sensitive++

            val there = MultipassPull.pullBothWays(setup, path)
            val back = MultipassPull.pullBothWays(setup.reversed(), path.reversed())

            assertEquals("one line for the pair, reversed", there, back.reversed())
            assertEquals("the ends are the caller's own", start, there.first())
            assertEquals(aim, there.last())
            val cheapest = min(polylineLengthM(forwardOnly), polylineLengthM(reversedOnly))
            assertTrue(
                "never longer than either single pull (${polylineLengthM(there)} m vs $cheapest m)",
                polylineLengthM(there) <= cheapest + 1e-6
            )
        }
        assertTrue("the fixture exercises chains the single pull reads two ways ($sensitive of 60)", sensitive > 0)
    }
}
