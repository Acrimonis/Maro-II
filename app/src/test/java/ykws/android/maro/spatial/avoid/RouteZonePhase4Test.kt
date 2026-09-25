package ykws.android.maro.spatial.avoid

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.DepthSample
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.data.model.RouteResult
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.data.regulation.SpeedZone
import ykws.android.maro.spatial.RouteAvoidEngine
import ykws.android.maro.spatial.RouteEngineState
import ykws.android.maro.spatial.SpatialOperations
import ykws.android.maro.spatial.Units

/**
 * Phase 4: speed-limit zones as priced, excludable soft sources — the fill (holes stay water,
 * overlap keeps the strictest limit, land stays land), the price cursor, the zone-aware ETA and the
 * forced-crossing report.
 */
class RouteZonePhase4Test {

    // ── The fill ─────────────────────────────────────────────────────────────

    @Test
    fun zoneHolesStayWater() {
        val outer = squareRing(43.5, 7.03, 0.02)
        val hole = squareRing(43.5, 7.03, 0.005)
        val box = BBox(43.46, 43.54, 6.99, 7.07)
        val grid = rasterize(
            box, cellM = 50.0, marginM = 25.0,
            edges = emptyList(), openCoast = emptyList(), capLatNorth = 43.54,
            field = RouteCostField.EMPTY,
            zones = listOf(PricedZone(outer, listOf(hole), 20.0))
        )

        val inHole = grid.cellOf(43.5, 7.03)
        val inZone = grid.cellOf(43.5, 7.02)
        assertEquals("a cell inside the hole reads as water", AvoidCellState.FREE, grid.cell(inHole.row, inHole.col).state)
        assertEquals("a cell between the outer ring and the hole is tagged zone", AvoidCellState.ZONE, grid.cell(inZone.row, inZone.col).state)
    }

    @Test
    fun zoneOverlapKeepsTheStrictestLimit() {
        val outer = squareRing(43.5, 7.03, 0.02)
        val box = BBox(43.46, 43.54, 6.99, 7.07)
        val priceSlow = zonePriceM(50.0, paceKn = 28.0, limitKn = 5.0, k = 2.0)
        val priceFast = zonePriceM(50.0, paceKn = 28.0, limitKn = 10.0, k = 2.0)
        assertTrue("the slower limit is the dearer price", priceSlow > priceFast)

        val grid = rasterize(
            box, cellM = 50.0, marginM = 25.0,
            edges = emptyList(), openCoast = emptyList(), capLatNorth = 43.54,
            field = RouteCostField.EMPTY,
            zones = listOf(
                PricedZone(outer, emptyList(), priceSlow),
                PricedZone(outer, emptyList(), priceFast)
            )
        )

        val (row, col) = grid.cellOf(43.5, 7.03)
        val cell = grid.cell(row, col)
        assertEquals(AvoidCellState.ZONE, cell.state)
        assertEquals("the strictest limit wins, never the sum", 50.0 + priceSlow, cell.sourceCostM, 1e-6)
    }

    @Test
    fun landStaysLandUnderAZone() {
        val grid = AvoidGrid(
            latSouth = 43.0, lonWest = 7.0,
            cellSizeDegLat = 0.001, cellSizeDegLon = 0.001,
            rows = 10, cols = 10, cellM = 50.0
        )
        grid.markLand(5, 5)
        grid.applyZoneCost(5, 5, 25.0)
        assertEquals("a wall is never priced", AvoidCellState.LAND, grid.cell(5, 5).state)
    }

    // ── The price cursor ──────────────────────────────────────────────────────

    @Test
    fun zonePriceAmplifiesWithKAndCostsMoreForASlowerLimit() {
        assertEquals("K=1 with the limit at the pace is open water", 0.0, zonePriceM(50.0, 28.0, 28.0, 1.0), 1e-9)
        assertEquals("a limit above the pace clamps to zero", 0.0, zonePriceM(50.0, 28.0, 40.0, 1.0), 1e-9)
        assertTrue(
            "a higher K makes the zone dearer",
            zonePriceM(50.0, 28.0, 5.0, 2.0) > zonePriceM(50.0, 28.0, 5.0, 1.0)
        )
        assertTrue(
            "a slower limit costs more",
            zonePriceM(50.0, 28.0, 5.0, 1.0) > zonePriceM(50.0, 28.0, 10.0, 1.0)
        )
    }

    // ── The ETA ───────────────────────────────────────────────────────────────

    @Test
    fun etaObeysTheLimitInsideAZone() {
        val a = LatLng(43.5, 7.00)
        val b = LatLng(43.5, 7.01)
        val dist = SpatialOperations.haversine(a, b)
        val timed = timeLineWithLimits(listOf(a, b), paceKn = 28.0, limitKnAt = { _ -> 5.0 })

        assertEquals(listOf(a, b), timed.points)
        assertEquals(1, timed.legTimesSec.size)
        assertEquals("the whole leg is timed at the 5 kn limit", dist / Units.knotsToMps(5.0), timed.legTimesSec[0], 1e-6)
    }

    @Test
    fun etaSplitsAtTheLimitChangeAndSumsItsLegs() {
        val a = LatLng(43.5, 7.00)
        val b = LatLng(43.5, 7.06)
        val limitKnAt: (LatLng) -> Double? = { p ->
            if (p.longitude in 7.02..7.04 && p.latitude in 43.49..43.51) 5.0 else null
        }
        val timed = timeLineWithLimits(listOf(a, b), paceKn = 28.0, limitKnAt = limitKnAt)

        assertEquals("two boundary crossings insert two vertices", 4, timed.points.size)
        assertEquals(3, timed.legTimesSec.size)
        assertEquals(timed.durationSec, timed.legTimesSec.sum(), 1e-9)
    }

    // ── Exclusion ─────────────────────────────────────────────────────────────

    @Test
    fun exclusionDropsAZoneFromTheFillAndTheLimitRead() {
        val zoneA = SpeedZone("a", "Zone A", 5.0, squareRing(43.5, 7.03, 0.02))
        val zoneB = SpeedZone("b", "Zone B", 10.0, squareRing(43.5, 7.03, 0.02))
        val box = BBox(43.4, 43.6, 6.9, 7.1)

        assertEquals(listOf(zoneB), speedZonesInBox(listOf(zoneA, zoneB), box, setOf("a")))
        assertEquals(10.0, strictestLimitKnAt(listOf(zoneA, zoneB), setOf("a"), 43.5, 7.03)!!, 1e-9)
        assertEquals("with none excluded the strictest limit survives", 5.0, strictestLimitKnAt(listOf(zoneA, zoneB), emptySet(), 43.5, 7.03)!!, 1e-9)
        assertNull(strictestLimitKnAt(listOf(zoneA, zoneB), emptySet(), 43.5, 6.99))
    }

    // ── Forced crossing ───────────────────────────────────────────────────────

    @Test
    fun forcedCrossingIsNamedOnlyWhenNoAvoidingPathExists() {
        val zone = SpeedZone("z", "Cap", 5.0, squareRing(43.5, 7.03, 0.02))
        val waypoints = listOf(LatLng(43.5, 7.00), LatLng(43.5, 7.06))

        assertEquals(listOf("Cap"), forcedCrossingZoneNames(waypoints, listOf(zone), avoidingPathExists = false))
        assertEquals(emptyList<String>(), forcedCrossingZoneNames(waypoints, listOf(zone), avoidingPathExists = true))
        val missed = SpeedZone("f", "Far", 5.0, squareRing(44.0, 7.03, 0.02))
        assertEquals(emptyList<String>(), forcedCrossingZoneNames(waypoints, listOf(missed), avoidingPathExists = false))
    }

    @Test
    fun aZoneBlockingTheWholeCorridorIsReportedAsAForcedCrossing() = runTest {
        val zone = SpeedZone("z", "Cap", 5.0, rectRing(43.45, 43.55, 7.015, 7.045))
        val world = ZoneWorld(listOf(zone))
        val engine = RouteAvoidEngine(paceKn = { 28.0 }, worldProvider = { world })

        engine.onOriginPositionChanged(RoutePoint(43.5, 7.00))
        val route = engine.onDestinationPositionChanged(RoutePoint(43.5, 7.06)) as RouteResult.Success

        assertEquals(listOf("Cap"), route.forcedCrossingZoneNames)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun squareRing(centerLat: Double, centerLon: Double, half: Double): List<LatLng> = listOf(
        LatLng(centerLat - half, centerLon - half),
        LatLng(centerLat - half, centerLon + half),
        LatLng(centerLat + half, centerLon + half),
        LatLng(centerLat + half, centerLon - half),
        LatLng(centerLat - half, centerLon - half)
    )

    private fun rectRing(latSouth: Double, latNorth: Double, lonWest: Double, lonEast: Double): List<LatLng> = listOf(
        LatLng(latSouth, lonWest),
        LatLng(latSouth, lonEast),
        LatLng(latNorth, lonEast),
        LatLng(latNorth, lonWest),
        LatLng(latSouth, lonWest)
    )

    /** A water-everywhere world whose only source is its speed zones — the corridor reads no land or depth. */
    private class ZoneWorld(private val zones: List<SpeedZone>) : AvoidWorld {
        override val coastlineReady: Boolean get() = true
        override val depthReady: Boolean get() = false
        override val bandWidthM: Double get() = 0.0
        override val regionBounds: BBox? get() = null

        override fun segmentsIn(box: BBox): List<AvoidEdge> = emptyList()
        override fun openCoastIn(box: BBox): List<List<LatLng>> = emptyList()
        override fun isWater(latitude: Double, longitude: Double): Boolean = true
        override fun distanceToCoastM(latitude: Double, longitude: Double): Double = Double.MAX_VALUE
        override fun depthAt(latitude: Double, longitude: Double): DepthSample = DepthSample.NONE
        override fun speedZonesIn(box: BBox): List<SpeedZone> = speedZonesInBox(zones, box, emptySet())
        override fun zoneLimitKnAt(latitude: Double, longitude: Double): Double? =
            strictestLimitKnAt(zones, emptySet(), latitude, longitude)
        override suspend fun load(): RouteEngineState = RouteEngineState.Ready
    }
}
