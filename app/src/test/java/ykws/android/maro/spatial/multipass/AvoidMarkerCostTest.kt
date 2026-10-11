package ykws.android.maro.spatial.multipass

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.DepthSample
import ykws.android.maro.data.model.DepthSource
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.spatial.SpatialOperations
import ykws.android.maro.spatial.Units
import kotlin.math.PI
import kotlin.math.abs

/**
 * **The marker cost through the A\*'s own read** — the joint [`RouteMarkerCostTest`] leaves open.
 *
 * That suite prices the law in arithmetic, asking a field's own evaluator directly. This one puts the
 * price where the acquisition actually reads it — `costField` → `rasterize` → [`MultipassSearch`] — so the
 * claim it proves is the one that reaches a drawn line: the well arrives at the search, the line leans off
 * the mark's centre and widens as the step rises, a Pin's cone steers too, and a wall is never crossed.
 * The shape is [`AvoidBandCostTest`]'s, which proves the band the same way.
 *
 * The box is water everywhere — no coast, no depth, no zones and no band — so the marker is the field's
 * only source beyond the base, and the chord runs due north at one longitude, which makes a detour
 * exactly an east-west excursion in columns.
 */
class AvoidMarkerCostTest {

    private val box = BBox(43.50, 43.52, 7.00, 7.02)
    private val cellM = 50.0
    private val paceKn = 28.0
    private val marginM = 25.0
    private val paceMps = Units.knotsToMps(paceKn)

    /** Metres in one degree of latitude — the haversine's own constant, so a north offset lands exactly. */
    private val mPerDegLat = SpatialOperations.EARTH_RADIUS_M * PI / 180.0

    /** The one longitude the chord runs up, and the map's own middle column. */
    private val chordLon = 7.010
    private val from = LatLng(43.505, chordLon)
    private val to = LatLng(43.515, chordLon)

    /** Whatever the switch held before this fixture armed it — put back by [restoreTheMarkerPrice]. */
    private var markerPriceWas = true

    /**
     * The price's own switch is armed for every case here, so the fixture never depends on a shipped
     * property; the reflection is the established idiom for an `AppConfig` switch (see
     * `RouteFineReachabilityTest`'s own helper). [restoreTheMarkerPrice] puts back exactly what it found,
     * so a case here can neither depend on nor swallow another suite's handling of that switch.
     */
    @Before
    fun armTheMarkerPrice() {
        val field = AppConfig::class.java.getDeclaredField("routeMarkerEnabled")
        field.isAccessible = true
        markerPriceWas = field.getBoolean(AppConfig)
        field.setBoolean(AppConfig, true)
    }

    /** Puts the switch back to the value [armTheMarkerPrice] found, whatever it was. */
    @After
    fun restoreTheMarkerPrice() {
        val field = AppConfig::class.java.getDeclaredField("routeMarkerEnabled")
        field.isAccessible = true
        field.setBoolean(AppConfig, markerPriceWas)
    }

    /**
     * A world whose only cost source is its markers — water everywhere, no land and no zones. [depthM]
     * is `null` for a world that reads no depth at all, and a **uniform** reading when set, so a case can
     * arm the depth gate over deep water and see whether the well still prices beside it.
     */
    private class MarkerWorld(
        private val markers: List<RouteMarker>,
        private val depthM: Float? = null
    ) : MultipassWorld {
        override val coastlineReady: Boolean get() = true
        override val depthReady: Boolean get() = depthM != null
        override val bandWidthM: Double get() = 0.0
        override val regionBounds: BBox? get() = null

        override fun segmentsIn(box: BBox): List<MultipassEdge> = emptyList()
        override fun openCoastIn(box: BBox): List<List<LatLng>> = emptyList()
        override fun isWater(latitude: Double, longitude: Double): Boolean = true
        override fun distanceToCoastM(latitude: Double, longitude: Double): Double = Double.MAX_VALUE
        override fun depthAt(latitude: Double, longitude: Double): DepthSample =
            depthM?.let { DepthSample(it, DepthSource.LITTO3D, 90, true) } ?: DepthSample.NONE

        override fun routeMarkers(): List<RouteMarker> = markers
    }

    /** The corridor grid the search reads, with [markers] the field's only source and the strength at 1. */
    private fun gridOf(markers: List<RouteMarker>, depthM: Float? = null): MultipassGrid {
        val field = costField(
            MarkerWorld(markers, depthM), cellM, paceKn,
            withZones = false, withBand = false, zones = emptyList(), lambda = 0.0,
            markerStrength = 1.0
        )
        return rasterize(box, cellM, paceKn, marginM, emptyList(), emptyList(), box.latNorth, field)
    }

    /** A search of the chord over [markers], its own grid handed back beside the outcome. */
    private suspend fun searchOf(
        markers: List<RouteMarker>,
        depthM: Float? = null
    ): Pair<MultipassGrid, SearchOutcome> {
        val grid = gridOf(markers, depthM)
        val outcome = MultipassSearch.search(
            grid, grid.cellOf(from.latitude, from.longitude), grid.cellOf(to.latitude, to.longitude), paceMps
        )
        return grid to outcome
    }

    /** One costed Circle of [step] centred on the chord, its collar [proximityM] wide. */
    private fun markerAt(step: Int, radiusM: Double, proximityM: Double) = RouteMarker(
        "m$step", RouteMarkerGeometry.Circle(LatLng(43.510, chordLon), radiusM, proximityM), step, false
    )

    /**
     * How far off the chord the answered line goes, in metres: the worst column offset from the chord's
     * own column, scaled by the cell. `NaN` where no path was found.
     */
    private suspend fun detourM(markers: List<RouteMarker>, depthM: Float? = null): Double {
        val (grid, outcome) = searchOf(markers, depthM)
        val path = outcome.path ?: return Double.NaN
        val chordCol = grid.cellOf(43.510, chordLon).col
        return path.maxOf { abs(it.col - chordCol) } * cellM
    }

    @Test
    fun theMarkerPriceReachesTheGridsOwnCells() = runTest {
        val grid = gridOf(listOf(markerAt(step = 3, radiusM = 400.0, proximityM = 300.0)))
        val base = grid.baseCostSec

        val body = grid.cellOf(43.510, chordLon)
        assertEquals(
            "the body pays its step on top of the base — base × (1 + step)",
            4.0 * base, grid.sourceCostSec(body.row, body.col), 1e-9
        )

        // A cell carries the price of its **own centre**, up to half a cell from the point asked about,
        // so the collar is proven by its gradient — nearer the body dearer, further out cheaper — rather
        // than by a figure that a half-cell of placement would move.
        fun priceAt(metresNorth: Double): Double {
            val cell = grid.cellOf(43.510 + metresNorth / mPerDegLat, chordLon)
            return grid.sourceCostSec(cell.row, cell.col)
        }

        val inner = priceAt(450.0)
        val outer = priceAt(650.0)
        assertTrue("the collar still costs more than open water", inner > base)
        assertTrue("and it fades as the outer edge is approached", outer < inner)
        assertTrue("while never falling below the base", outer > base)
        assertEquals("beyond the outer edge the water is open again", base, priceAt(900.0), 1e-9)
    }

    @Test
    fun theLineLeansOffTheCentreAndWidensWithTheStep() = runTest {
        val none = detourM(emptyList())
        val cheap = detourM(listOf(markerAt(step = 1, radiusM = 400.0, proximityM = 400.0)))
        val dear = detourM(listOf(markerAt(step = 9, radiusM = 400.0, proximityM = 400.0)))

        assertEquals("with no marker the chord is crossed straight", 0.0, none, cellM)
        assertTrue("a cheap well is still crossed rather than circled", cheap <= cellM)
        assertTrue("a dear one earns a real berth", dear > cellM)
        assertTrue("and the berth only widens with the step", dear > cheap)
    }

    @Test
    fun aPinPricesAConeFromItsCentre() = runTest {
        val pin = RouteMarker(
            "p", RouteMarkerGeometry.Circle(LatLng(43.510, chordLon), 0.0, 400.0), step = 9, isWall = false
        )
        val detour = detourM(listOf(pin))

        assertFalse("a costed Pin leaves the chord reachable", detour.isNaN())
        assertTrue("and its zero-radius body's cone steers the line", detour > cellM)
    }

    /**
     * **The gate's arming does not silence the well.** With the depth reading on and the 3 m gate armed
     * over uniformly deep water, the chord stays reachable — nothing is walled — and the dear well earns
     * exactly the berth it earns with no depth at all, so the two sources are read together rather than
     * one standing the other down.
     */
    @Test
    fun theWellStillPricesWhenTheDepthGateIsArmed() = runTest {
        val gateWas = setDepthGate(true)
        try {
            val deep = detourM(listOf(markerAt(step = 9, radiusM = 400.0, proximityM = 400.0)), depthM = 20.0f)

            assertFalse("deep water leaves the chord reachable with the gate armed", deep.isNaN())
            assertTrue("and the well still earns its berth beside the gate", deep > cellM)
        } finally {
            setDepthGate(gateWas)
        }
    }

    /** Flips the depth gate to [value] and answers what it held before, so a case can put it back. */
    private fun setDepthGate(value: Boolean): Boolean {
        val field = AppConfig::class.java.getDeclaredField("routeAvoidDepthGateEnabled")
        field.isAccessible = true
        val was = field.getBoolean(AppConfig)
        field.setBoolean(AppConfig, value)
        return was
    }

    @Test
    fun aWallSpanningTheCorridorLeavesNoPath() = runTest {
        val wall = RouteMarker(
            "w",
            RouteMarkerGeometry.Corridor(
                LatLng(43.510, box.lonWest), LatLng(43.510, box.lonEast), widthM = 200.0, proximityM = 0.0
            ),
            step = 0, isWall = true
        )

        assertNull("a wall across the whole corridor cannot be crossed", searchOf(listOf(wall)).second.path)
    }

    @Test
    fun thePathNeverEntersAWallsBody() = runTest {
        val wall = RouteMarker(
            "w",
            RouteMarkerGeometry.Corridor(
                LatLng(43.510, 7.004), LatLng(43.510, 7.016), widthM = 200.0, proximityM = 0.0
            ),
            step = 0, isWall = true
        )
        val (grid, outcome) = searchOf(listOf(wall))
        val path = outcome.path
        assertNotNull("the line rounds the wall's own end", path)

        // A cell is judged at its own centre, read back off the grid's own lattice; the one cell of slack
        // covers that half-cell of placement, while a line that truly crossed would read near zero.
        val segment = wall.geometry as RouteMarkerGeometry.Corridor
        val halfWidthM = 100.0
        fun centreOf(cell: CellIndex) = LatLng(
            box.latSouth + (cell.row + 0.5) * ((box.latNorth - box.latSouth) / grid.rows),
            box.lonWest + (cell.col + 0.5) * ((box.lonEast - box.lonWest) / grid.cols)
        )

        for (cell in path!!) {
            val distance = SpatialOperations.pointToSegmentDistance(centreOf(cell), segment.p1, segment.p2)
            assertTrue(
                "no cell of the line stands inside the wall's body",
                distance > halfWidthM - cellM
            )
        }
    }
}
