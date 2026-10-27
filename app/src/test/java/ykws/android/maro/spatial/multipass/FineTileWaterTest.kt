package ykws.android.maro.spatial.multipass

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.CoastlinePoint
import ykws.android.maro.data.model.CoastlineSegment
import ykws.android.maro.data.model.DepthSample
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.data.regulation.SpeedZone
import ykws.android.maro.spatial.CoastlineSpatialIndex
import ykws.android.maro.spatial.LandRingOrientation
import ykws.android.maro.spatial.SpatialOperations
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * **A fine tile is a function of the lattice, the world and its key alone.**
 *
 * A tile is cached as its whole 64-cell block and served to every box that meets it, so it must be built
 * from **its own** coast harvest and keyed on **its own** water — the world's rings and markers that reach
 * it — never on the corridor or pre-warm box that happened to build it. These cases pin the facts, over a
 * real [CoastlineSpatialIndex] so the open coast is clipped exactly as the live world clips it:
 *
 * 1. a block standing a harvest reach inside the building box is rastered to **the same bytes** by the
 *    per-tile harvest and the per-tile water as by the corridor's own harvest and the corridor's whole zone
 *    and marker lists (a block nearer the box's edge now carries the band collar of coast beyond the edge,
 *    which the corridor-clipped harvest never saw — the water that build left unpriced);
 * 2. a block straddling the box's edge carries the coast standing **outside** that box — its margin as
 *    land and its ribbon as fine water — where a corridor-clipped harvest left no fine water at all;
 * 3. two corridors meeting different far zones name the same tile, and a ring within the outside margin of
 *    a tile's raster (plus the filter's slack) is in that tile's key while a ring well beyond it is not.
 */
class FineTileWaterTest {

    private val marginM = AppConfig.routeAvoidObstacleMarginM
    private val zoneOutsideMarginM = AppConfig.routeAvoidSpeedZoneOutsideMarginM
    private val strength = 1.0

    /** The world's fixed anchor — the depth raster's own in the app; both corridors lay their tiles on it. */
    private val anchor = LatticeAnchor(43.0, 6.9, 43.5)

    /** Corridor 1: the west of the coast, its east edge at 7.0125 — just inside the eighth tile column. */
    private val corridor1 = BBox(43.500, 43.535, 6.970, 7.0125)

    /** Corridor 2: overlapping corridor 1 and reaching east over the far zone and the second island. */
    private val corridor2 = BBox(43.500, 43.535, 7.005, 7.060)

    /**
     * The island standing east of corridor 1 — further than the index's 500 m harvest cells reach past the
     * box — inside the block that straddles corridor 1's east edge.
     */
    private val islandB = LatLng(43.5005, 7.0245)
    private val islandBRadiusM = 150.0

    /** The island inside corridor 1 whose cut reach is what makes corridor 1 ask for the straddling block. */
    private val islandC = LatLng(43.5005, 7.0085)

    private val mPerDegLat = SpatialOperations.EARTH_RADIUS_M * PI / 180.0

    /** The south edge of the coast row's first block — the one `z1`, `z4` and `m1` stand on. */
    private val coastRowSouth: Double by lazy {
        val side = FINE_TILE_CELLS * fine.cellSizeDegLat
        fine.latSouth + kotlin.math.floor((43.5225 - fine.latSouth) / side) * side
    }

    private val zones: List<SpeedZone> by lazy {
        listOf(
            // Over the coast row's first tile.
            rectZone("z1", 43.521, 43.524, 6.982, 6.987, 5.0),
            // Inside the straddling block, outside corridor 1 — a zone one corridor's list would miss.
            rectZone("z2", 43.501, 43.503, 7.015, 7.020, 10.0),
            // Outside corridor 1 altogether, inside corridor 2 and far from every block corridor 1 asks for.
            rectZone("z3", 43.500, 43.502, 7.050, 7.055, 8.0),
            // One metre inside the outside margin south of the coast row's first tile: in its key.
            rectZone(
                "z4", coastRowSouth - (zoneOutsideMarginM - 1.0 + 100.0) / mPerDegLat,
                coastRowSouth - (zoneOutsideMarginM - 1.0) / mPerDegLat, 6.982, 6.985, 6.0
            ),
            // Three hundred metres beyond the margin south of the same tile: out of its key.
            rectZone(
                "z5", coastRowSouth - (zoneOutsideMarginM + 400.0) / mPerDegLat,
                coastRowSouth - (zoneOutsideMarginM + 300.0) / mPerDegLat, 6.982, 6.985, 7.0
            )
        )
    }

    private val markers = listOf(
        RouteMarker("m1", RouteMarkerGeometry.Circle(LatLng(43.5225, 6.989), 30.0, 50.0), step = 3, isWall = false),
        RouteMarker("m2", RouteMarkerGeometry.Circle(LatLng(43.501, 7.018), 30.0, 50.0), step = 2, isWall = false)
    )

    private val world: IndexedWorld by lazy { IndexedWorld(
        segments = listOf(
            CoastlineSegment(
                osmWayId = 1L,
                points = coastPoints(
                    listOf(
                        LatLng(43.52, 6.95), LatLng(43.52, 6.98), LatLng(43.518, 7.00), LatLng(43.515, 7.01),
                        LatLng(43.52, 7.02), LatLng(43.52, 7.08)
                    )
                ),
                isMainland = true
            ),
            CoastlineSegment(osmWayId = 2L, points = coastPoints(circle(LatLng(43.512, 6.988), 200.0)), isClosed = true),
            CoastlineSegment(osmWayId = 3L, points = coastPoints(circle(islandC, 150.0)), isClosed = true),
            CoastlineSegment(osmWayId = 4L, points = coastPoints(circle(islandB, islandBRadiusM)), isClosed = true)
        ),
        zones = zones,
        markers = markers,
        latticeAnchor = anchor
    ) }

    private val builder = RouteGridBuilder(EvolutiveGridPlan)

    private var zonesWere = true
    private var bandWas = true
    private var markersWere = true

    @Before
    fun armTheSwitches() {
        zonesWere = AppConfig.routeAvoidSpeedZoneEnabled
        bandWas = AppConfig.routeAvoidZone300Enabled
        markersWere = AppConfig.routeMarkerEnabled
        setSwitch("routeAvoidSpeedZoneEnabled", true)
        setSwitch("routeAvoidZone300Enabled", true)
        setSwitch("routeMarkerEnabled", true)
    }

    @After
    fun restoreTheSwitches() {
        setSwitch("routeAvoidSpeedZoneEnabled", zonesWere)
        setSwitch("routeAvoidZone300Enabled", bandWas)
        setSwitch("routeMarkerEnabled", markersWere)
    }

    /** The switches' setters are the properties file's alone, so a fixture flips them as the engine tests do. */
    private fun setSwitch(name: String, value: Boolean) {
        val field = AppConfig::class.java.getDeclaredField(name)
        field.isAccessible = true
        field.setBoolean(AppConfig, value)
    }

    @Test
    fun insideTheBuildingBoxTheTilesOwnHarvestAndWaterChangeNoByte() = runBlocking {
        val tiles = requestsOf(corridor1)
        // The band's reach is the furthest any coast sweep reads, so a block that far inside the corridor
        // harvests exactly the coast the corridor's clip held — and the two rasters must agree to the byte.
        val harvestReachM = bandReachM(world.bandWidthM, AppConfig.routeAvoidZone300OutsideMarginM)
        val inside = tiles.keys.filter { (row, col) ->
            corridor1.holds(inflateBox(fineTileBox(row, col), reachWithSlack(harvestReachM, fine.cellM)))
        }
        assertTrue("the fixture must offer blocks a harvest reach inside corridor 1", inside.size >= 2)
        for ((row, col) in inside) {
            val built = tiles.getValue(row to col).build()
            val reference = referenceTile(row, col, corridor1)
            assertSameTile("tile $row/$col", reference, built)
        }
    }

    @Test
    fun aBlockStraddlingTheBoxCarriesTheCoastOutsideItAsItsOwnWater() = runBlocking {
        val straddler = straddlingTile()
        val built = requestsOf(corridor1).getValue(straddler).build()
        val reference = referenceTile(straddler.first, straddler.second, corridor1)
        val builtMembers = built.localIndex.toSet()
        val referenceMembers = reference.localIndex.toSet()
        val gained = builtMembers - referenceMembers
        assertTrue(
            "the corridor-clipped build never saw the island, so its ribbon was masked out; the tile's own harvest keeps it",
            gained.isNotEmpty()
        )
        for (local in gained) {
            val centre = centreOf(built, local)
            assertTrue(
                "a cell the tile's own build keeps as fine water stands outside corridor 1, in the island's ribbon (${centre.latitude}, ${centre.longitude})",
                centre.longitude >= corridor1.lonEast - 1e-9 &&
                    SpatialOperations.haversine(centre, islandB) < islandBRadiusM + marginM + 100.0 + 3.0 * 20.0
            )
        }
        for (local in builtMembers) {
            val centre = centreOf(built, local)
            assertTrue(
                "no cell inside the island's own margin is water in the tile's own build (${centre.latitude}, ${centre.longitude})",
                SpatialOperations.haversine(centre, islandB) > islandBRadiusM + marginM - 3.0 * 20.0
            )
        }
    }

    @Test
    fun twoCorridorsMeetingOtherFarZonesNameOneTile() {
        val straddler = straddlingTile()
        val first = requestsOf(corridor1).getValue(straddler).key
        val second = requestsOf(corridor2).getValue(straddler).key
        assertEquals("the same block over the same water is one key whichever corridor asked", first, second)
        assertEquals(FineTileCodec.entryName(first), FineTileCodec.entryName(second))
        assertTrue("corridor 2 does meet the far zone the key must not carry", world.speedZonesIn(corridor2).any { it.id == "z3" })
        assertTrue("corridor 1 does not meet the zone inside the block", world.speedZonesIn(corridor1).none { it.id == "z2" })
        assertTrue("and the key carries the zone inside the block, from either corridor", first.zoneRings.any { it.limitKn == 10.0 })
        assertTrue("but not the far one", first.zoneRings.none { it.limitKn == 8.0 })
    }

    @Test
    fun theKeyCarriesTheRingsWithinTheMarginAndTheSlackAndNotThoseBeyond() {
        val coastRowTile = coastRowFirstTile()
        val key = requestsOf(corridor1).getValue(coastRowTile).key
        val limits = key.zoneRings.map { it.limitKn }.toSet()
        assertTrue("the ring over the tile is in its key", 5.0 in limits)
        assertTrue("the ring one metre inside the outside margin, read with the slack, is in its key", 6.0 in limits)
        assertTrue("the ring well beyond the margin is not", 7.0 !in limits)
        assertTrue("nor a ring at the other end of the water", 10.0 !in limits)
        assertEquals("the marker reaching the tile is in its key, the far one is not", listOf("m1"), key.markers.map { it.id })
    }

    // ── the fixture's own seams ───────────────────────────────────────────────

    private fun requestsOf(box: BBox): Map<Pair<Int, Int>, FineTileRequest> =
        builder.fineTilesOver(world, box, strength).associateBy { it.key.tileRow to it.key.tileCol }

    private val fine: WalkLattice by lazy {
        LatticeFamily.of(anchor, AppConfig.routeEvolutiveGridCellM, AppConfig.routeEvolutiveGridFineCellM).fine
    }

    private fun fineTileBox(row: Int, col: Int): BBox {
        val sideLat = FINE_TILE_CELLS * fine.cellSizeDegLat
        val sideLon = FINE_TILE_CELLS * fine.cellSizeDegLon
        val latSouth = fine.latSouth + row * sideLat
        val lonWest = fine.lonWest + col * sideLon
        return BBox(latSouth, latSouth + sideLat, lonWest, lonWest + sideLon)
    }

    /** The block holding the second island: among corridor 1's requests, across its east edge. */
    private fun straddlingTile(): Pair<Int, Int> {
        val requested = requestsOf(corridor1).keys
        val candidates = requested.filter { (row, col) ->
            val box = fineTileBox(row, col)
            box.lonWest < corridor1.lonEast && box.lonEast > corridor1.lonEast &&
                box.latSouth <= islandB.latitude && islandB.latitude <= box.latNorth
        }
        assertEquals("exactly one requested block straddles corridor 1's east edge at the island's row: $candidates", 1, candidates.size)
        return candidates.single()
    }

    /** The first block of the coast's own row inside corridor 1 — the one `z1`, `z4` and `m1` stand on. */
    private fun coastRowFirstTile(): Pair<Int, Int> {
        val requested = requestsOf(corridor1).keys
        val candidates = requested.filter { (row, col) ->
            val box = fineTileBox(row, col)
            box.latSouth <= 43.5225 && 43.5225 <= box.latNorth && box.lonWest <= 6.985 && 6.985 <= box.lonEast
        }
        assertEquals("exactly one requested block holds the coast row's first zone: $candidates", 1, candidates.size)
        return candidates.single()
    }

    /** The block rastered the old way: the corridor's clipped harvest and its whole zone and marker lists. */
    private fun referenceTile(row: Int, col: Int, corridor: BBox): FineTile {
        val block = fineTileBox(row, col)
        val edges = world.segmentsIn(corridor)
        val openCoast = world.openCoastIn(corridor)
        val fineWater = EvolutiveGridPlan.fineWater(
            FineWaterQuery(world, corridor, edges, openCoast, marginM, AppConfig.routeEvolutiveGridCellM)
        )
        val priced = world.speedZonesIn(corridor).map { ZoneRing(it.outerRing, it.holes, it.routeLimitKn) }
        val raster = rasterizeWindow(
            block, fine, paceKn = 0.0, marginM = marginM, edges = edges, openCoast = openCoast,
            capLatNorth = block.latNorth, field = tileCostField(world, 1.0, strength, markers),
            zones = priced, zoneOutsideMarginM = zoneOutsideMarginM, band = bandLaw(world),
            gate = null, collarReachM = 0.0, fineMask = fineWater.toMask(), baseCostSecOverride = 1.0
        )
        return FineTile.extract(raster, row, col, FINE_TILE_CELLS, 0)
    }

    private fun centreOf(tile: FineTile, local: Int): LatLng = LatLng(
        tile.latSouth + (local / tile.cols + 0.5) * tile.cellSizeDegLat,
        tile.lonWest + (local % tile.cols + 0.5) * tile.cellSizeDegLon
    )

    private fun assertSameTile(what: String, expected: FineTile, actual: FineTile) {
        assertEquals("$what: rows", expected.rows, actual.rows)
        assertEquals("$what: cols", expected.cols, actual.cols)
        assertEquals("$what: latSouth", expected.latSouth, actual.latSouth, 0.0)
        assertEquals("$what: lonWest", expected.lonWest, actual.lonWest, 0.0)
        assertTrue("$what: members", expected.localIndex.contentEquals(actual.localIndex))
        assertTrue("$what: state", expected.state.contentEquals(actual.state))
        assertTrue("$what: sourceCostFactor", expected.sourceCostFactor.contentEquals(actual.sourceCostFactor))
        assertTrue("$what: zoneLimitKn", expected.zoneLimitKn.contentEquals(actual.zoneLimitKn))
        assertTrue("$what: collarLimitKn", expected.collarLimitKn.contentEquals(actual.collarLimitKn))
        assertTrue("$what: bandLimitKn", expected.bandLimitKn.contentEquals(actual.bandLimitKn))
        assertTrue("$what: bandCollarLimitKn", expected.bandCollarLimitKn.contentEquals(actual.bandCollarLimitKn))
        assertTrue("$what: gateDistanceM", expected.gateDistanceM.contentEquals(actual.gateDistanceM))
        assertTrue("$what: the fixture holds water", expected.memberCount > 0)
    }

    private fun BBox.holds(inner: BBox): Boolean =
        inner.latSouth >= latSouth && inner.latNorth <= latNorth && inner.lonWest >= lonWest && inner.lonEast <= lonEast

    private fun rectZone(id: String, latSouth: Double, latNorth: Double, lonWest: Double, lonEast: Double, limitKn: Double) =
        SpeedZone(
            id = id, name = id, speedLimitKn = limitKn,
            outerRing = listOf(
                LatLng(latSouth, lonWest), LatLng(latSouth, lonEast), LatLng(latNorth, lonEast),
                LatLng(latNorth, lonWest), LatLng(latSouth, lonWest)
            )
        )

    private fun circle(center: LatLng, radiusM: Double, n: Int = 24): List<LatLng> {
        val mPerDegLat = SpatialOperations.EARTH_RADIUS_M * PI / 180.0
        val mPerDegLon = mPerDegLat * cos(Math.toRadians(center.latitude))
        val ring = (0 until n).map { i ->
            val theta = 2.0 * PI * i / n
            LatLng(center.latitude + radiusM * sin(theta) / mPerDegLat, center.longitude + radiusM * cos(theta) / mPerDegLon)
        }
        return ring + ring.first()
    }

    private fun coastPoints(points: List<LatLng>): List<CoastlinePoint> =
        points.map { CoastlinePoint(it.latitude.toFloat(), it.longitude.toFloat()) }

    /**
     * A world over a real [CoastlineSpatialIndex] — the mainland first (polyline 0, the open coast), the
     * islands closed rings — so the harvest is clipped exactly as the live adapter clips it.
     */
    private class IndexedWorld(
        segments: List<CoastlineSegment>,
        private val zones: List<SpeedZone>,
        private val markers: List<RouteMarker>,
        override val latticeAnchor: LatticeAnchor
    ) : MultipassWorld {
        private val index = CoastlineSpatialIndex(segments)

        override val coastlineReady: Boolean get() = true
        override val depthReady: Boolean get() = false
        override val regionBounds: BBox? get() = null
        override val bandWidthM: Double get() = 300.0

        override fun segmentsIn(box: BBox): List<MultipassEdge> =
            index.segmentsInBbox(box).mapNotNull { seg ->
                if (seg.polylineIdx == 0) null else MultipassEdge(seg.a, seg.b, LandRingOrientation.CCW_RING)
            }

        override fun openCoastIn(box: BBox): List<List<LatLng>> = index.openCoastPolylinesIn(box)

        override fun isWater(latitude: Double, longitude: Double): Boolean = index.isWater(latitude, longitude)

        override fun distanceToCoastM(latitude: Double, longitude: Double): Double =
            index.query(latitude, longitude).distanceMeters

        override fun depthAt(latitude: Double, longitude: Double): DepthSample = DepthSample.NONE

        override fun speedZonesIn(box: BBox): List<SpeedZone> = speedZonesInBox(zones, box, emptySet())

        override fun zoneLimitKnAt(latitude: Double, longitude: Double): Double? =
            strictestLimitKnAt(zones, emptySet(), latitude, longitude)

        override fun zoneDisplayLimitKnAt(latitude: Double, longitude: Double): Double? =
            strictestDisplayLimitKnAt(zones, emptySet(), latitude, longitude)

        override fun routeMarkers(): List<RouteMarker> = markers
    }
}
