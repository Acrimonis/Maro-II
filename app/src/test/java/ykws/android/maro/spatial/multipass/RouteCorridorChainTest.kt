package ykws.android.maro.spatial.multipass

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.spatial.SpatialOperations
import ykws.android.maro.spatial.Units

/**
 * The corridor chain and the walk it feeds: the region the second pass reads, the windows it is rasterized
 * into, and the seam between two of them — the three things the adaptive grid's Phase 3 stands on.
 */
class RouteCorridorChainTest {

    private val corridor = BBox(43.45, 43.55, 6.95, 7.05)

    /** The carve's own reach for a margin and a cell: `ceil(marginM / cellM) + 1` cells of water. */
    private fun reachM(marginM: Double, cellM: Double): Double = carveReachCells(marginM, cellM) * cellM

    private fun BBox.holds(p: LatLng): Boolean =
        p.latitude in latSouth..latNorth && p.longitude in lonWest..lonEast

    /**
     * **The ends are inside by construction**: each end stands at its own box's centre, so the disc the
     * berth frees is inside the chain at every cell the plan names — and the carve's wider reach is inside
     * wherever the half-width can hold it.
     *
     * The carve reach is `ceil(marginM / cellM) + 1` cells, so at the **coarse** 100 m cell it is 200 m —
     * wider than the 150 m half-width itself, and this test says so rather than pretending otherwise: `w`
     * bounds the carve's reach only while `2 × cellM` is under it.
     */
    @Test
    fun theChainCarriesBothEndDiscsAndTheirCarveReach() {
        val marginM = 25.0
        val halfWidthM = 150.0
        val line = listOf(LatLng(43.5000, 7.0000), LatLng(43.5000, 7.0300))

        for (cellM in listOf(50.0, 100.0)) {
            val chain = corridorChain(line, halfWidthM, WalkLattice.of(corridor, cellM))
            assertTrue("a line of this length chains more than one box", chain.size > 1)
            assertTrue("the start is in the chain's first box", chain.first().holds(line.first()))
            assertTrue("and the aim in its last", chain.last().holds(line.last()))

            for (end in line) {
                for (bearing in 0 until 360 step 15) {
                    val disc = SpatialOperations.pointAlongBearing(
                        end.latitude, end.longitude, bearing.toDouble(), marginM
                    )
                    assertTrue(
                        "the $marginM m end disc at a $cellM m cell stands in some box, bearing $bearing",
                        chain.any { it.holds(LatLng(disc.latitude, disc.longitude)) }
                    )
                }
            }
        }

        for (cellM in listOf(20.0, 50.0)) {
            val chain = corridorChain(line, halfWidthM, WalkLattice.of(corridor, cellM))
            val reach = reachM(marginM, cellM)
            for (end in line) {
                for (bearing in 0 until 360 step 15) {
                    val carved = SpatialOperations.pointAlongBearing(
                        end.latitude, end.longitude, bearing.toDouble(), reach
                    )
                    assertTrue(
                        "the $reach m carve reach at a $cellM m cell stands in some box, bearing $bearing",
                        chain.any { it.holds(LatLng(carved.latitude, carved.longitude)) }
                    )
                }
            }
        }
    }

    /** The chain's own contract: consecutive centres are at most `w` apart, so their boxes overlap by `w`. */
    @Test
    fun theChainsCentresAreAtMostItsHalfWidthApart() {
        val line = listOf(LatLng(43.5000, 7.0000), LatLng(43.5200, 7.0300), LatLng(43.5000, 7.0600))
        val lattice = WalkLattice.of(corridor, 20.0)
        val chain = corridorChain(line, halfWidthM = 150.0, lattice = lattice)

        for (i in 0 until chain.size - 1) {
            val a = LatLng((chain[i].latSouth + chain[i].latNorth) / 2.0, (chain[i].lonWest + chain[i].lonEast) / 2.0)
            val b = LatLng((chain[i + 1].latSouth + chain[i + 1].latNorth) / 2.0, (chain[i + 1].lonWest + chain[i + 1].lonEast) / 2.0)
            // The centres are placed by fraction of a lat/lon-linear leg, so a spacing carries a little
            // geodesic slack — a metre of it, against the half-width's own 150.
            assertTrue(
                "boxes $i and ${i + 1} are within w of each other",
                SpatialOperations.haversine(a, b) <= 150.0 + 1.0
            )
        }
    }

    /** The plan is the region's one decision: `evolutive` answers a chain where `avoid` answers its box. */
    @Test
    fun theEvolutivePlanAnswersAChainAndTheAvoidPlanItsBox() {
        val line = listOf(LatLng(43.5000, 7.0000), LatLng(43.5000, 7.0300))

        val chain = EvolutiveGridPlan.secondPassRegions(line, corridor, 50.0, AppConfig.routeEvolutiveGridCellM)
        assertTrue("a 2.4 km line chains many boxes, not one", chain.size >= 10)
        assertTrue("the first box carries the start", chain.first().holds(line.first()))
        assertTrue("and the last the aim", chain.last().holds(line.last()))

        assertEquals(
            "avoid's own plan still answers the one box the settled line spans",
            1,
            UniformGridPlan.secondPassRegions(line, corridor, 50.0, 100.0).size
        )
    }

    /**
     * **The seam is arithmetic, and a path crosses it**: two windows on one lattice, overlapping, and the
     * walk runs from the first to the last — a single window could not reach the aim at all.
     */
    @Test
    fun theWalkCrossesTheSeamBetweenTwoWindows() = runTest {
        val cellM = 20.0
        val paceKn = 28.0
        val lattice = WalkLattice.of(corridor, cellM)

        fun window(rowOffset: Int, colOffset: Int, rows: Int, cols: Int) = WalkWindow(
            MultipassGrid(
                lattice.latSouth + rowOffset * lattice.cellSizeDegLat,
                lattice.lonWest + colOffset * lattice.cellSizeDegLon,
                lattice.cellSizeDegLat, lattice.cellSizeDegLon, rows, cols, cellM,
                baseCostSec(cellM, paceKn)
            ),
            rowOffset, colOffset
        )

        val walk = WalkWindows.onLattice(
            lattice,
            listOf(window(0, 0, 6, 10), window(0, 5, 6, 10))
        )
        val path = MultipassSearch.searchWalk(
            walk, CellIndex(3, 0), CellIndex(3, 14), Units.knotsToMps(paceKn)
        ).path

        assertNotNull("the two windows are one walk", path)
        assertEquals("the path leaves the first window and reaches into the second", 15, path!!.size)
        assertEquals(0, path.first().col)
        assertEquals(14, path.last().col)
        assertTrue("and it is a straight run through the one row", path.all { it.row == 3 })
    }
}
