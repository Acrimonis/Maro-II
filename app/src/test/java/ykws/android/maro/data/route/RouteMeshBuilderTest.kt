package ykws.android.maro.data.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.BoundingBox
import ykws.android.maro.data.model.RouteMesh
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.data.model.RouteResult
import ykws.android.maro.spatial.PricedZoneRef
import ykws.android.maro.spatial.RoutePointQueries
import ykws.android.maro.spatial.SpatialOperations
import ykws.android.maro.spatial.mesh.RouteSearch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max

/**
 * The mesh builder against synthetic worlds, so every rule it implements is checked without a corner
 * of real coastline: land is never meshed, the 30 m floor holds the shoreline shell empty, a shallow
 * bar severs the water, the 300 m band's edges are marked, and the mesh the builder produces is one
 * the search can actually walk.
 */
class RouteMeshBuilderTest {

    // ── The synthetic world ───────────────────────────────────────────────────

    /** An axis-aligned rectangle in the local metre frame. */
    private class Rect(val x0: Double, val y0: Double, val x1: Double, val y1: Double) {
        fun contains(x: Double, y: Double): Boolean = x in x0..x1 && y in y0..y1
        fun shrunk(marginM: Double): Rect =
            Rect(x0 + marginM, y0 + marginM, x1 - marginM, y1 - marginM)
        fun distanceTo(x: Double, y: Double): Double =
            hypot(max(max(x0 - x, x - x1), 0.0), max(max(y0 - y, y - y1), 0.0))
        fun ring(): List<Pair<Double, Double>> =
            listOf(x0 to y0, x1 to y0, x1 to y1, x0 to y1, x0 to y0)
    }

    /**
     * A world described in metres about the box centre: land north of [mainlandY] (nowhere when that
     * line lies outside the box), plus any land rectangles, plus an optional bar too shallow to cross
     * and an optional area the soundings never reached.
     */
    private class World(
        private val mainlandY: Double = 20_000.0,
        private val landRects: List<Rect> = emptyList(),
        private val shallowBar: Rect? = null,
        private val zones: List<Rect> = emptyList(),
        /** Water the soundings never reached: `NaN` depth, which is *unknown* and not shallow. */
        private val unsoundedRect: Rect? = null,
        /** Half-side of the world in metres, so a case can be far wider than the default box. */
        private val boxExtentM: Double = EXTENT_M
    ) : RouteGeometry {

        override val box: BoundingBox = boxOf(boxExtentM)

        override fun coastlineLines(): List<List<RoutePoint>> {
            val lines = ArrayList<List<RoutePoint>>()
            lines.add(listOf(point(-9_000.0, mainlandY), point(9_000.0, mainlandY)))
            landRects.forEach { rect -> lines.add(rect.ring().map { point(it.first, it.second) }) }
            return lines
        }

        override fun zoneRings(): List<List<RoutePoint>> =
            zones.map { zone -> zone.ring().map { point(it.first, it.second) } }

        override fun isWater(latitude: Double, longitude: Double): Boolean {
            val x = xOf(longitude)
            val y = yOf(latitude)
            if (y >= mainlandY) return false
            return landRects.none { it.contains(x, y) }
        }

        override fun depthM(latitude: Double, longitude: Double): Double {
            val x = xOf(longitude)
            val y = yOf(latitude)
            if (unsoundedRect != null && unsoundedRect.contains(x, y)) return Double.NaN
            val bar = shallowBar ?: return 10.0
            return if (bar.contains(x, y)) 1.0 else 10.0
        }

        /** @return true when the position is inside land by more than [marginM]. */
        fun isLandByMoreThan(latitude: Double, longitude: Double, marginM: Double): Boolean {
            val x = xOf(longitude)
            val y = yOf(latitude)
            if (y >= mainlandY + marginM) return true
            return landRects.any { it.shrunk(marginM).contains(x, y) }
        }

    }

    private fun build(
        world: World,
        tuning: RouteMeshBuilder.Tuning = RouteMeshBuilder.Tuning()
    ): RouteMesh = RouteMeshBuilder("test", world, tuning).build()

    /** A node may sit on the coastline — that is the domain's own boundary — but never inside land. */
    private fun assertNoNodeInsideLand(mesh: RouteMesh, world: World) {
        assertTrue("the mesh should hold nodes", mesh.points.isNotEmpty())
        for (p in mesh.points) {
            assertTrue(
                "node ${p.latitude},${p.longitude} is inside land",
                !world.isLandByMoreThan(p.latitude, p.longitude, BOUNDARY_TOLERANCE_M)
            )
        }
    }

    // ── Land ──────────────────────────────────────────────────────────────────

    @Test
    fun landIsNeverMeshed() {
        val world = World(mainlandY = 1_500.0, landRects = listOf(Rect(-500.0, -1_000.0, 500.0, 0.0)))
        val mesh = build(world)

        assertNoNodeInsideLand(mesh, world)
        assertTrue("the mesh should hold edges", mesh.edges.isNotEmpty())
    }

    // ── The channel floor ─────────────────────────────────────────────────────

    /**
     * A wall across the water with a slot through it, so the basins are water and the slot is the
     * only way between them. [gapM] is the slot's width: under the 30 m floor the slot is sealed and
     * the basins stay separate, over it they are one stretch. [unsoundedSlot] leaves the slot's own
     * water without a sounding, which is the case the bridging pass exists for.
     */
    private fun slottedWorld(gapM: Double, unsoundedSlot: Boolean = false) = World(
        mainlandY = 1_500.0,
        landRects = listOf(
            Rect(-20.0, -9_000.0, 20.0, -gapM / 2.0),
            Rect(-20.0, gapM / 2.0, 20.0, 1_500.0)
        ),
        unsoundedRect = if (unsoundedSlot) Rect(-21.0, -gapM / 2.0, 21.0, gapM / 2.0) else null
    )

    @Test
    fun aSlotNarrowerThanTheFloorIsSealedAndAWiderOneIsNot() {
        val narrow = build(slottedWorld(20.0))
        assertEquals(
            "a 20 m slot is under the 30 m floor and must not connect the basins",
            2, narrow.components.distinct().size
        )

        val wide = build(slottedWorld(300.0))
        assertEquals(
            "a 300 m slot is over the floor and must connect the basins",
            1, wide.components.distinct().size
        )
    }

    @Test
    fun aWallAcrossTheWaterGivesTwoStretches() {
        val world = World(mainlandY = 1_500.0, landRects = listOf(Rect(-60.0, -9_000.0, 60.0, 1_500.0)))
        val mesh = build(world)

        assertEquals(
            "a wall reaching the mainland must cut the water in two",
            2, mesh.components.distinct().size
        )
    }

    // ── The zone berth ────────────────────────────────────────────────────────

    @Test
    fun everyNodeKeepsTheZoneBerthClearOfARegulatedZone() {
        // A zone square in open water: the mesh must begin 25 m outside it, so nothing it holds can
        // hug the zone's edge — which is the difference between a route that touches a zone and one
        // that gives it a berth.
        val zone = Rect(-1_000.0, -1_000.0, 1_000.0, 1_000.0)
        val mesh = build(
            World(mainlandY = 1_500.0, zones = listOf(zone)),
            RouteMeshBuilder.Tuning(zoneClearanceM = ZONE_CLEARANCE_M)
        )

        assertTrue("the mesh should hold nodes around the zone", mesh.points.isNotEmpty())
        var nearest = Double.MAX_VALUE
        for (point in mesh.points) {
            val distance = zone.distanceTo(xOf(point.longitude), yOf(point.latitude))
            if (distance < nearest) nearest = distance
            assertTrue(
                "a node stands ${"%.1f".format(distance)} m from the regulated zone, inside the berth",
                distance >= ZONE_CLEARANCE_M - BOUNDARY_TOLERANCE_M
            )
        }
        assertTrue("the mesh should reach the berth's own edge", nearest < ZONE_CLEARANCE_M + 100.0)
    }

    // ── The hybrid berth ──────────────────────────────────────────────────────

    /**
     * **The hybrid berth, on the case it is made for.** A zone whose own water is too shallow to mesh
     * — a swim zone inside the 2.5 m gate — has no water of its own for the strip to cut off, so
     * removing the 25 m ring around it severs nothing: the zone is made hard, the water the strip held
     * is cut, and no node the mesh keeps stands inside the berth.
     */
    @Test
    fun aZoneWithNoWaterOfItsOwnIsMadeHardAndKeepsItsBerth() {
        val zone = Rect(-1_000.0, -1_000.0, 1_000.0, 1_000.0)
        val builder = RouteMeshBuilder(
            "test",
            World(mainlandY = 1_500.0, zones = listOf(zone), shallowBar = zone),
            RouteMeshBuilder.Tuning()
        )
        val mesh = builder.build()
        val report = builder.berthReport

        assertEquals("one zone, one verdict", 1, report.zones.size)
        assertTrue(
            "a zone with no water of its own must be hard, got ${report.zones.first().reason}",
            report.hardZones == 1
        )
        assertTrue(
            "the strip held water and the hard cut must report it",
            report.removedM2 > 0.0
        )
        assertTrue("the mesh should still hold water", mesh.points.isNotEmpty())
        for (point in mesh.points) {
            val distance = zone.distanceTo(xOf(point.longitude), yOf(point.latitude))
            assertTrue(
                "a node stands ${"%.1f".format(distance)} m from the regulated zone, inside the berth",
                distance >= ZONE_CLEARANCE_M - BOUNDARY_TOLERANCE_M
            )
        }
    }

    /**
     * **The case the hybrid must refuse.** A zone standing in open water holds water of its own, and
     * that water can only be reached through its strip: cutting the strip severs it from the sea, so
     * the zone reverts to the priced arrangement — its water left exactly as it was, the verdict naming
     * the severance — and the search's own berth price is what keeps the line off the edge.
     */
    @Test
    fun aZoneStandingInOpenWaterKeepsItsWaterAndIsPriced() {
        val zone = Rect(-1_000.0, -1_000.0, 1_000.0, 1_000.0)
        val builder = RouteMeshBuilder(
            "test",
            World(mainlandY = 1_500.0, zones = listOf(zone)),
            RouteMeshBuilder.Tuning()
        )
        val mesh = builder.build()
        val report = builder.berthReport

        assertEquals("one zone, one verdict", 1, report.zones.size)
        assertTrue(
            "a zone standing in open water must revert to priced, got ${report.zones.first().reason}",
            report.pricedZones == 1
        )
        assertEquals("a priced zone cuts nothing", 0.0, report.removedM2, 0.0)
        assertTrue(
            "the priced zone's water must be untouched — a node stands inside its berth",
            mesh.points.any { zone.distanceTo(xOf(it.longitude), yOf(it.latitude)) < ZONE_CLEARANCE_M }
        )
    }

    // ── The depth gate ────────────────────────────────────────────────────────

    @Test
    fun aShallowBarIsNotMeshedAndSeversTheWater() {
        val world = World(shallowBar = Rect(100.0, -9_000.0, 900.0, 9_000.0))
        val mesh = build(world)

        val inBar = mesh.points.count { p -> xOf(p.longitude) in 100.5..899.5 }
        assertEquals("no node may stand in water shallower than the gate", 0, inBar)
        assertTrue(
            "a 1 m bar must sever the water, got ${mesh.components.distinct().size} stretch(es)",
            mesh.components.distinct().size >= 2
        )
    }

    // ── The bridging pass ─────────────────────────────────────────────────────

    /**
     * An unsounded slot between two basins: the strict gate alone leaves them two stretches, and the
     * bridging pass is what joins them. This is the reported bay's own shape in miniature — water the
     * oracle accepts, the soundings never reached, and a mesh that stops on both sides of it.
     */
    @Test
    fun anUnsoundedSlotBetweenTwoBasinsIsBridged() {
        val mesh = build(slottedWorld(NECK_GAP_M, unsoundedSlot = true))

        assertEquals(
            "an unsounded slot the gate leaves as two basins must be bridged into one stretch",
            1, mesh.components.distinct().size
        )
    }

    /**
     * The same pass across a wide unsounded bar: what it may keep is the **corridor** that joins the
     * two sides, never the bar's own area — which is exactly what the first version of this rule did
     * wrong when it kept every unsounded triangle and admitted the sea. The bar is compared against the
     * same world with the bar sounded, so the reading is the pass's own effect and nothing else.
     */
    @Test
    fun aWideUnsoundedBarIsBridgedByACorridorAndNotFilled() {
        val bar = Rect(100.0, -9_000.0, 900.0, 9_000.0)
        val bridged = build(World(unsoundedRect = bar))
        val sounded = build(World())

        assertEquals(
            "the unsounded bar must join its two sides, got ${bridged.components.distinct().size} stretch(es)",
            1, bridged.components.distinct().size
        )
        val crossing = bridged.points.count { xOf(it.longitude) in 100.5..899.5 }
        val filled = sounded.points.count { xOf(it.longitude) in 100.5..899.5 }
        assertTrue(
            "the bridge must be a corridor ($crossing node(s)) and not the bar's own fill " +
                "($filled node(s)) — a quarter of it at most",
            crossing * 3 <= filled
        )
    }

    /**
     * An unsounded **sea**, not a slot: the flood finds a chain from one shore to the other across it,
     * and the bound on a corridor's length is what refuses it.
     *
     * Keeping such a chain is precisely the failure D4 was narrowed for — the broad version of the
     * rule admitted the sea and took `nice-menton` to 111,497 nodes and 9.55 MB. A reconnect is a
     * pocket; a chain crossing kilometres of unknown water is not one, whatever it joins.
     */
    @Test
    fun aCorridorAcrossAnUnsoundedSeaIsRefused() {
        // Sounded water west and east of a 6 km unsounded bar inside a 16 km box. Nothing constrains
        // the middle, so the refinement targets the offshore spacing there and the bound is eight of
        // them — 4 km — while the chain the flood walks is about six: nothing is kept, and the two
        // shores stay two stretches.
        val boxM = 16_000.0
        val sea = Rect(-3_000.0, -boxM, 3_000.0, boxM)
        val mesh = build(World(unsoundedRect = sea, boxExtentM = boxM))

        assertEquals(
            "an unsounded sea is not a reconnect: the two shores must stay two stretches, got " +
                "${mesh.components.distinct().size}",
            2,
            mesh.components.distinct().size
        )
    }

    // ── The band mark ─────────────────────────────────────────────────────────

    @Test
    fun everyBandEdgeLiesInsideTheThreeHundredMetreBand() {
        val mesh = build(World(mainlandY = 1_500.0))

        assertTrue("the band must be marked somewhere", mesh.edges.any { it.inBand })
        assertTrue("the open water must not be marked", mesh.edges.any { !it.inBand })
        for (edge in mesh.edges.filter { it.inBand }) {
            val a = mesh.points[edge.from]
            val b = mesh.points[edge.to]
            val midY = (yOf(a.latitude) + yOf(b.latitude)) / 2.0
            assertTrue(
                "an in-band edge at ${midY} m is further than 300 m from the coast",
                midY >= 1_500.0 - 300.0 - BOUNDARY_TOLERANCE_M
            )
        }
    }

    // ── Assembly ──────────────────────────────────────────────────────────────

    @Test
    fun theGraphIsUndirectedAndEveryEdgeIsListedUnderBothOfItsEnds() {
        val mesh = build(World(mainlandY = 1_500.0))

        assertEquals(2 * mesh.edges.size, mesh.nodes.sumOf { it.edges.size })
        for (edge in mesh.edges) {
            assertTrue("an edge must span a distance", edge.lengthM > 0.0)
            assertTrue("edge ${edge.from}-${edge.to} is a self-loop", edge.from != edge.to)
            assertTrue("a node must list the edges it carries", mesh.nodes[edge.from].edges.isNotEmpty())
        }
    }

    // ── The whole chain ───────────────────────────────────────────────────────

    @Test
    fun theSearchWalksTheBuiltMeshAndKeepsBothEndsInOneStretch() {
        val world = World(mainlandY = 1_500.0, landRects = listOf(Rect(-60.0, -9_000.0, 60.0, 1_500.0)))
        val mesh = build(world)
        val arrays = RouteMeshSerializer.deserialize(RouteMeshSerializer.serialize(mesh))
        val search = RouteSearch(arrays, StubQueries())

        // West of the wall to a point east of it: the destination cannot be reached, so the rule
        // moves it into the boat's own stretch instead of failing.
        val result = search.search(point(-1_000.0, 0.0), point(1_000.0, 0.0), cruiseSpeedKn = 20.0)

        assertTrue("expected a route inside the boat's stretch, got $result", result is RouteResult.Success)
        val route = result as RouteResult.Success
        assertTrue("the destination must be reported as moved", route.destinationMoved)
        assertTrue("the whole line must sit west of the wall", route.points.all { xOf(it.longitude) < 0.0 })
    }

    private class StubQueries : RoutePointQueries {
        override fun isWater(latitude: Double, longitude: Double): Boolean = true

        /** No zone layer at all: nothing is priced by a limit and no interior is forbidden. */
        override fun pricedZoneAt(latitude: Double, longitude: Double): PricedZoneRef? = null

        /** No zone layer in these synthetic worlds: the berth has nothing to price. */
        override fun distanceToZoneM(latitude: Double, longitude: Double): Double =
            Double.POSITIVE_INFINITY

        override val coastalBandSpeedLimitKn: Double = 5.0
    }

    private companion object {
        const val CENTRE_LAT = 43.5
        const val CENTRE_LON = 7.0
        const val EXTENT_M = 4_000.0
        /** A coastline node is expected; a node more than this far inside land is a defect. */
        const val BOUNDARY_TOLERANCE_M = 2.0

        /** The berth the builder keeps around every regulated zone — `Tuning.zoneClearanceM`. */
        const val ZONE_CLEARANCE_M = 25.0

        /** An unsounded slot wide enough for the channel floor, so only the gate can seal it (m). */
        const val NECK_GAP_M = 100.0

        val M_PER_DEG_LAT = SpatialOperations.EARTH_RADIUS_M * PI / 180.0
        val M_PER_DEG_LON: Double = M_PER_DEG_LAT * cos(CENTRE_LAT * PI / 180.0)

        /** The world box, half-side [extentM] about the fixed centre the metre helpers use. */
        fun boxOf(extentM: Double): BoundingBox = BoundingBox(
            latSouth = CENTRE_LAT - (extentM / 2.0) / M_PER_DEG_LAT,
            latNorth = CENTRE_LAT + (extentM / 2.0) / M_PER_DEG_LAT,
            lonWest = CENTRE_LON - (extentM / 2.0) / M_PER_DEG_LON,
            lonEast = CENTRE_LON + (extentM / 2.0) / M_PER_DEG_LON
        )

        val BOX: BoundingBox = boxOf(EXTENT_M)

        fun point(x: Double, y: Double): RoutePoint =
            RoutePoint(CENTRE_LAT + y / M_PER_DEG_LAT, CENTRE_LON + x / M_PER_DEG_LON)

        fun xOf(lon: Double): Double = (lon - CENTRE_LON) * M_PER_DEG_LON

        fun yOf(lat: Double): Double = (lat - CENTRE_LAT) * M_PER_DEG_LAT
    }
}
