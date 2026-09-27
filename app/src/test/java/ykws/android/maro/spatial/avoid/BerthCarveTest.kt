package ykws.android.maro.spatial.avoid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.DepthSample
import ykws.android.maro.data.model.DepthSource
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.spatial.RouteEngineState
import ykws.android.maro.spatial.SpatialOperations
import kotlin.math.PI
import kotlin.math.cos

/**
 * **The berth carve (F7), pinned at the scan's own level.** Two worlds separate the two answers the
 * carve may give: a coast with open water beside it grants an approach, and a channel narrower than
 * twice the margin grants none — the walled end the ring must refuse rather than the ask.
 *
 * The grid's own half is pinned too: a carved cell takes the base cost and **keeps its zone limit**,
 * which is the one read the carve waives and the price it does not.
 */
class BerthCarveTest {

    /** The shipped pair the reach is derived from: `ceil(marginM / cellM) + 1` cells. */
    private val marginM = 50.0
    private val cellM = 50.0

    private val coastLat = 43.5000
    private val mPerDegLat = SpatialOperations.EARTH_RADIUS_M * PI / 180.0

    @Test
    fun anEndInMarginWaterWithALegalNeighbourIsReachable() {
        val world = coastWorld()
        // Ten metres south of the coast: inside the margin, on water — a berth, by the raster's reading.
        val at = LatLng(coastLat - 10.0 / mPerDegLat, 7.00)

        val carve = carveOf(world, at)

        assertTrue("the end has an approach", carve.reachable)
        assertEquals(
            "the first direction whose segment clears wins, in the scan's fixed order",
            CarveDirection.SE,
            carve.direction
        )
        assertEquals("one lattice step lands in legal water", 1, carve.stepsToWater)
        assertEquals("the stretch runs from the end's own point to the legal cell", 2, carve.points.size)
        assertEquals(at, carve.points.first())
        assertTrue("and it is one lattice step long", carve.lengthM > cellM)
        assertTrue("but no further than the diagonal step", carve.lengthM < cellM * 1.5)
        assertEquals("a found carve carries no failure reason", "", carve.reason)
    }

    /**
     * A channel twenty metres wide, the point standing in its middle ten metres from each wall: every
     * direction either runs into land (north and south) or stays in margin-water to the reach's end
     * (east and west), so the carve answers nothing and **says so** rather than blunting the fence.
     */
    @Test
    fun aWalledEndCarvesNothing() {
        val southLat = coastLat - 20.0 / mPerDegLat
        val world = CarveWorld(
            coasts = mutableListOf(
                listOf(LatLng(coastLat, 6.90), LatLng(coastLat, 7.10)),
                listOf(LatLng(southLat, 6.90), LatLng(southLat, 7.10))
            ),
            water = { lat, _ -> lat < coastLat && lat > southLat }
        )
        val at = LatLng(coastLat - 10.0 / mPerDegLat, 7.00)

        val carve = carveOf(world, at)

        assertFalse("the channel admits no approach", carve.reachable)
        assertNull("no direction qualified", carve.direction)
        assertEquals(CARVE_NO_LEGAL_WATER, carve.reason)
        assertTrue("and no stretch is returned", carve.points.isEmpty())
    }

    /** The reach is read off the two shipped sizes, never preferred: `ceil(marginM / cellM) + 1`. */
    @Test
    fun theReachIsDerivedFromTheMarginAndTheCell() {
        assertEquals("one cell of margin is two cells of reach", 2, carveReachCells(50.0, 50.0))
        assertEquals("a margin under one cell still reaches one cell out", 2, carveReachCells(10.0, 50.0))
        assertEquals("a two-cell margin reaches three", 3, carveReachCells(100.0, 50.0))
    }

    /**
     * The carve's own write: the cell opens at the grid's base cost and **keeps its zone limit**, so a
     * zone standing in the berth still prices the cell the A\* reads — the margin alone is waived.
     */
    @Test
    fun openingACarvedCellKeepsItsZoneLimitAndResetsItsCost() {
        val box = BBox(43.49, 43.51, 6.99, 7.01)
        val grid = rasterize(box, 50.0, 28.0, marginM, emptyList(), emptyList(), box.latNorth)
        val cell = CellIndex(3, 3)
        // The limit first — `applyZoneLimit` never writes a LAND cell — then the sweep's own bar.
        grid.applyZoneLimit(cell.row, cell.col, 10.0)
        grid.markLand(cell.row, cell.col)
        assertFalse("the cell starts barred", grid.cell(cell.row, cell.col).passable)

        grid.openCarve(cell.row, cell.col)

        val opened = grid.cell(cell.row, cell.col)
        assertTrue("the carve opens it", opened.passable)
        assertEquals("at the grid's base cost", grid.baseCostSec, opened.sourceCostSec, 1e-9)
        assertEquals("and it keeps its zone limit", 10.0, grid.zoneLimitKn(cell.row, cell.col), 1e-9)
    }

    /** The scan, run on the metric lattice the ring's own question builds for the point. */
    private fun carveOf(world: AvoidWorld, at: LatLng): BerthCarve = carveBerth(
        world,
        at,
        marginM,
        carveReachCells(marginM, cellM),
        false,
        AppConfig.routeAvoidDepthGateMinM,
        metricCarveLattice(at, cellM)
    )

    /** A single horizontal coast at [coastLat] spanning 6.90–7.10, land to the north, water to the south. */
    private fun coastWorld(): CarveWorld = CarveWorld(
        coasts = mutableListOf(listOf(LatLng(coastLat, 6.90), LatLng(coastLat, 7.10))),
        water = { lat, _ -> lat < coastLat }
    )

    /** The smallest world the carve reads: water, coast distance, depth — and nothing else. */
    private class CarveWorld(
        private val coasts: MutableList<List<LatLng>> = mutableListOf(),
        private val water: (Double, Double) -> Boolean = { _, _ -> true },
        private val depth: (Double, Double) -> Double = { _, _ -> Double.NaN }
    ) : AvoidWorld {

        override val coastlineReady: Boolean get() = true
        override val depthReady: Boolean get() = true
        override val bandWidthM: Double get() = 0.0
        override val regionBounds: BBox? get() = null

        override fun segmentsIn(box: BBox): List<AvoidEdge> = emptyList()

        override fun openCoastIn(box: BBox): List<List<LatLng>> = coasts

        override fun isWater(latitude: Double, longitude: Double): Boolean = water(latitude, longitude)

        override fun distanceToCoastM(latitude: Double, longitude: Double): Double {
            var best = Double.MAX_VALUE
            val p = LatLng(latitude, longitude)
            for (coast in coasts) {
                for (i in 0 until coast.size - 1) {
                    best = minOf(best, SpatialOperations.pointToSegmentDistance(p, coast[i], coast[i + 1]))
                }
            }
            return best
        }

        override fun depthAt(latitude: Double, longitude: Double): DepthSample {
            val sounding = depth(latitude, longitude)
            return if (sounding.isNaN()) DepthSample.NONE
            else DepthSample(sounding.toFloat(), DepthSource.LITTO3D, 100, true)
        }

        override suspend fun load(): RouteEngineState = RouteEngineState.Ready
    }
}
