package ykws.android.maro.spatial

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.BoundingBox
import ykws.android.maro.data.model.RouteMeshArrays
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.data.model.RouteResult
import ykws.android.maro.spatial.mesh.RouteMeshDetails
import ykws.android.maro.spatial.mesh.RouteSearch
import ykws.android.maro.spatial.mesh.meshDetailsRead
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * JVM tests for [RouteSearch] over a synthetic mesh.
 *
 * The mesh is built here rather than baked, so the search's cost model, its snapping, its
 * connected-water rule and its node budget can all be measured without a corner of real coastline —
 * the prebake's own benchmark lives in `RouteMeshPrebakeTest`.
 */
class RouteSearchTest {

    /**
     * **The engine's own readings off a result.**
     *
     * The counters these cases were written against used to sit on `RouteResult.Success` beside the
     * polyline and the clock; they are the mesh engine's own dossier now, so they are taken from it.
     * Every case below means to *count* something, so it reads through
     * [`meshDetailsRead`][ykws.android.maro.spatial.mesh.meshDetailsRead], whose one home also states
     * the empty-dossier rule — a revert that stopped producing a dossier fails here instead of
     * satisfying a zero.
     */
    private fun readings(route: RouteResult.Success): RouteMeshDetails = route.meshDetailsRead

    // ── Synthetic grid ────────────────────────────────────────────────────────

    private fun metresPerDegLon(): Double = METRES_PER_DEG_LAT * cos(Math.toRadians(ORIGIN_LAT))
    private fun latOf(yM: Double): Double = ORIGIN_LAT + yM / METRES_PER_DEG_LAT
    private fun lonOf(xM: Double): Double = ORIGIN_LON + xM / metresPerDegLon()

    /**
     * @param triangles whether the fixture ships the mesh's own water interior, two triangles per
     *        cell. The shortcut pass and the fillet are the two readers of it, so a test about either
     *        has to ask for it: without triangles nothing is proved to be inside the mesh and both
     *        passes refuse every candidate, which is the search's behaviour on a `.bin` baked before
     *        the triangles were shipped.
     */
    private fun gridMesh(
        cols: Int,
        rows: Int,
        spacingM: Double = 200.0,
        boxExtraM: Double = 0.0,
        componentOf: (col: Int, row: Int) -> Int = { _, _ -> 0 },
        inBandOf: (a: Int, b: Int) -> Boolean = { _, _ -> false },
        triangles: Boolean = false,
        /**
         * What the mesh says each edge's length is, as a multiple of the spacing it was built on.
         *
         * It exists for one test: a mesh whose own edge lengths **understate** the geometry the drawn
         * line is measured on is a stand-in for a pass that changed the line without re-pricing it, so
         * the invariant that guards the plan is exercised through the public API rather than through a
         * seam that only the test can reach. The app bakes the two from the same geodesic formula, so
         * this is not a shape the real mesh can take.
         */
        edgeLengthScale: Double = 1.0
    ): RouteMeshArrays {
        val n = cols * rows
        val lat = DoubleArray(n)
        val lon = DoubleArray(n)
        val component = IntArray(n)
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val i = r * cols + c
                lat[i] = latOf(r * spacingM)
                lon[i] = lonOf(c * spacingM)
                component[i] = componentOf(c, r)
            }
        }

        val from = ArrayList<Int>()
        val to = ArrayList<Int>()
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val i = r * cols + c
                if (c + 1 < cols) { from.add(i); to.add(i + 1) }
                if (r + 1 < rows) { from.add(i); to.add(i + cols) }
            }
        }
        val edgeCount = from.size
        val length = FloatArray(edgeCount) { (spacingM * edgeLengthScale).toFloat() }
        val inBand = BooleanArray(edgeCount) { inBandOf(from[it], to[it]) }

        val triA = ArrayList<Int>()
        val triB = ArrayList<Int>()
        val triC = ArrayList<Int>()
        if (triangles) {
            for (r in 0 until rows - 1) {
                for (c in 0 until cols - 1) {
                    val i = r * cols + c
                    triA.add(i); triB.add(i + 1); triC.add(i + cols)
                    triA.add(i + 1); triB.add(i + cols + 1); triC.add(i + cols)
                }
            }
        }

        val offsets = IntArray(n + 1)
        for (e in 0 until edgeCount) {
            offsets[from[e] + 1]++
            offsets[to[e] + 1]++
        }
        for (i in 1..n) offsets[i] += offsets[i - 1]
        val cursor = offsets.copyOf()
        val payload = IntArray(offsets[n])
        for (e in 0 until edgeCount) {
            payload[cursor[from[e]]++] = e
            payload[cursor[to[e]]++] = e
        }

        return RouteMeshArrays(
            regionId = "test",
            // The box is the corridor the search gates its ends on; the node grid inside it can be
            // smaller than the box, which is what lets a test aim past every node but stay inside.
            box = BoundingBox(
                latSouth = latOf(-spacingM - boxExtraM),
                latNorth = latOf(rows * spacingM + boxExtraM),
                lonWest = lonOf(-spacingM - boxExtraM),
                lonEast = lonOf(cols * spacingM + boxExtraM)
            ),
            pointsLat = lat,
            pointsLon = lon,
            nodeComponent = component,
            edgeFrom = from.toIntArray(),
            edgeTo = to.toIntArray(),
            edgeLengthM = length,
            edgeInBand = inBand,
            nodeOffsets = offsets,
            nodeEdges = payload,
            triA = triA.toIntArray(),
            triB = triB.toIntArray(),
            triC = triC.toIntArray()
        )
    }

    /** A queries stub whose answers are the caller's, so one behaviour at a time is tested. */
    private class StubQueries(
        private val limitAt: (Double, Double) -> Double? = { _, _ -> null },
        private val waterAt: (Double, Double) -> Boolean = { _, _ -> true },
        private val zoneClearanceAt: (Double, Double) -> Double = { _, _ -> Double.POSITIVE_INFINITY },
        override val coastalBandSpeedLimitKn: Double = 5.0
    ) : RoutePointQueries {
        override fun isWater(latitude: Double, longitude: Double): Boolean = waterAt(latitude, longitude)

        /**
         * A limit is a zone, and a zone is named after it: the search prices the water by the limit
         * and decides traversal by the zone's identity, so a stub that named its limits and no zones
         * would test half of what the search now does. The name is what a forced crossing reports.
         */
        override fun pricedZoneAt(latitude: Double, longitude: Double): PricedZoneRef? =
            limitAt(latitude, longitude)?.let { kn ->
                PricedZoneRef(id = "stub-zone-$kn", name = "stub zone $kn kn", limitKn = kn)
            }
        override fun distanceToZoneM(latitude: Double, longitude: Double): Double =
            zoneClearanceAt(latitude, longitude)
    }

    private fun point(xM: Double, yM: Double) = RoutePoint(latOf(yM), lonOf(xM))

    // ── Cost and path ─────────────────────────────────────────────────────────

    @Test
    fun straightRouteIsReturnedOverOpenWater() {
        val mesh = gridMesh(cols = 5, rows = 5)
        val search = RouteSearch(mesh, StubQueries())

        val result = search.search(point(0.0, 0.0), point(800.0, 0.0), cruiseSpeedKn = 10.0)

        assertTrue(result is RouteResult.Success)
        val route = result as RouteResult.Success
        assertEquals(800.0, route.distanceM, 1.0)
        // The collinear merge: the three nodes between the ends carry no heading change at all, so
        // the line is its two ends and nothing in between.
        assertEquals(2, route.points.size)
        assertEquals(false, route.inBand)
        assertEquals(false, route.destinationMoved)
        // 800 m at 10 kn = 800 / (10 × 0.514444) ≈ 155.5 s
        assertEquals(155.5, route.durationSec, 1.0)
    }

    @Test
    fun routeGoesAroundAZoneWhenThatIsFaster() {
        val mesh = gridMesh(cols = 5, rows = 5)
        // A 2 kn zone over rows 0-2 and columns 1-3: crossing it costs ~600 m at a fifth of the pace.
        val zoneLat = latOf(500.0)
        val westOfZone = lonOf(100.0)
        val eastOfZone = lonOf(700.0)
        val queries = StubQueries(limitAt = { lat, lon ->
            if (lat < zoneLat && lon > westOfZone && lon < eastOfZone) 2.0 else null
        })
        val search = RouteSearch(mesh, queries)

        val result = search.search(point(0.0, 0.0), point(800.0, 0.0), cruiseSpeedKn = 10.0)

        assertTrue(result is RouteResult.Success)
        val route = result as RouteResult.Success
        // The detour over row 3 is 2000 m at 10 kn (~389 s); the direct 800 m crosses 600 m of
        // 2 kn water (~622 s), so the longer line is the faster one. The tolerance is the grid's own
        // scale: the fixture lays metres out with a flat 111,320 m/deg while the line is measured
        // geodesically, so a 2 km span reads ~2 m short — the mesh the app ships carries its edge
        // lengths from the same geodesic formula, so there the two agree.
        assertEquals(2000.0, route.distanceM, 3.0)
    }

    @Test
    fun aBandEdgeIsPricedAtTheBandsOwnLiveLimit() {
        // Two nodes and a single in-band edge, so the crossing has to be taken.
        val mesh = gridMesh(cols = 2, rows = 1, inBandOf = { _, _ -> true })
        val search = RouteSearch(mesh, StubQueries(coastalBandSpeedLimitKn = 2.0))

        val result = search.search(point(0.0, 0.0), point(200.0, 0.0), cruiseSpeedKn = 10.0)

        assertTrue("expected a route, got $result", result is RouteResult.Success)
        val route = result as RouteResult.Success
        assertTrue(route.inBand)
        assertEquals(200.0 / (2.0 * Units.MPS_PER_KNOT), route.durationSec, 0.5)
    }

    @Test
    fun aSlowBandEdgeIsAvoidedWhenTheDetourIsFaster() {
        // The same 2 kn in-band mark, but a detour exists: pricing the crossing live is what makes
        // the longer line the faster one.
        val mesh = gridMesh(cols = 3, rows = 2, inBandOf = { a, b -> a == 0 && b == 1 })
        val search = RouteSearch(mesh, StubQueries(coastalBandSpeedLimitKn = 2.0))

        val result = search.search(point(0.0, 0.0), point(200.0, 0.0), cruiseSpeedKn = 10.0)

        assertTrue("expected a route, got $result", result is RouteResult.Success)
        val route = result as RouteResult.Success
        assertEquals(false, route.inBand)
        assertEquals(600.0, route.distanceM, 1.0)
    }

    // ── The turn penalty and the collinear merge ──────────────────────────────

    @Test
    fun aTurnIsPricedAsTheExtraTimeTheFilletsArcSpends() {
        // East 200 m then north 200 m at 10 kn: one 90° turn, priced as the arc that will replace it.
        val mesh = gridMesh(cols = 2, rows = 2)
        val search = RouteSearch(mesh, StubQueries())

        val result = search.search(point(0.0, 0.0), point(200.0, 200.0), cruiseSpeedKn = 10.0)

        assertTrue("expected a route, got $result", result is RouteResult.Success)
        val route = result as RouteResult.Success
        // The corner is a real bend, so the merge keeps it.
        assertEquals(3, route.points.size)
        val speedMps = 10.0 * Units.MPS_PER_KNOT
        val arcExtraSec = speedMps / AppConfig.routeTurnLateralAccelMps2 *
            (PI / 2.0 - 2.0 * sin(PI / 4.0))
        // The plan's time is read off the **drawn line** now, and that line is measured geodesically
        // where this fixture lays its metres out flat — 111,320 m/deg against the Earth's own 111,195
        // — so the two 200 m legs read about 0.45 m short and the drawn clock lands ~0.09 s under the
        // search's own accumulation. The tolerance is that fixture mismatch and nothing else: the
        // turn's own price, which is what this test is here for, is 1.6 s.
        assertEquals(2 * 200.0 / speedMps + arcExtraSec, route.durationSec, 0.2)
    }

    @Test
    fun collinearNodesAreMergedAwayAndTheLegTimesStillSumToTheDuration() {
        // Five nodes dead ahead — no heading change anywhere, so the line is its two ends.
        val mesh = gridMesh(cols = 5, rows = 1)
        val search = RouteSearch(mesh, StubQueries())

        val result = search.search(point(0.0, 0.0), point(800.0, 0.0), cruiseSpeedKn = 10.0)

        assertTrue("expected a route, got $result", result is RouteResult.Success)
        val route = result as RouteResult.Success
        assertEquals(listOf(0, 4), readings(route).nodeIndices)
        assertEquals(2, route.points.size)
        // The times telescope with the merge, so the legs still add up to the search's own duration.
        assertEquals(1, route.legTimesSec.size)
        assertEquals(route.durationSec, route.legTimesSec.sum(), 1e-9)
        // The line's own geodesic length, which on this flat-grid fixture reads a metre short of 800.
        assertEquals(800.0, route.distanceM, 3.0)
    }

    // ── The zone berth ────────────────────────────────────────────────────────

    /**
     * A shore-parallel zone whose boundary is the row `y = 0`: a node's clearance is its own distance
     * north of that line, so the southernmost row stands exactly on the edge.
     */
    private fun clearanceFromRowZero(): (Double, Double) -> Double =
        { latitude, _ -> (latitude - latOf(0.0)) * METRES_PER_DEG_LAT }

    @Test
    fun aLegHuggingAZoneIsPricedAsATwiceAsLongOne() {
        // East along the zone's own edge, or a detour one row north: 800 m at double price against a
        // 1200 m line that holds the berth.
        val mesh = gridMesh(cols = 5, rows = 5)
        val search = RouteSearch(
            mesh,
            StubQueries(zoneClearanceAt = clearanceFromRowZero()),
            zoneBerthM = { BERTH_M }
        )

        val result = search.search(point(0.0, 0.0), point(800.0, 0.0), cruiseSpeedKn = 10.0)

        assertTrue("expected a route, got $result", result is RouteResult.Success)
        assertEquals(
            "the route should give the zone its berth rather than hug its edge",
            1200.0,
            (result as RouteResult.Success).distanceM,
            3.0
        )
    }

    @Test
    fun aBerthIsAPriceAndNeverAWall() {
        // One row of water, every node standing on the zone's edge: the berth makes the line dear and
        // the route still takes it, because a passage a boat can use is never closed by a courtesy.
        val mesh = gridMesh(cols = 5, rows = 1)
        val search = RouteSearch(
            mesh,
            StubQueries(zoneClearanceAt = clearanceFromRowZero()),
            zoneBerthM = { BERTH_M }
        )

        val result = search.search(point(0.0, 0.0), point(800.0, 0.0), cruiseSpeedKn = 10.0)

        assertTrue("the hugging route must still exist, got $result", result is RouteResult.Success)
        assertEquals(800.0, (result as RouteResult.Success).distanceM, 3.0)
    }

    // ── Snapping and connected water ──────────────────────────────────────────

    @Test
    fun destinationInAnotherStretchMovesToTheBoatSOwnStretch() {
        // Columns 0-1 are the boat's stretch; columns 2-4 are water it cannot reach without
        // crossing land.
        val mesh = gridMesh(cols = 5, rows = 5, componentOf = { col, _ -> if (col < 2) 0 else 1 })
        val search = RouteSearch(mesh, StubQueries())

        val result = search.search(point(0.0, 0.0), point(800.0, 0.0), cruiseSpeedKn = 10.0)

        assertTrue("expected a route, got $result", result is RouteResult.Success)
        val route = result as RouteResult.Success
        assertTrue("the destination should have moved", route.destinationMoved)
        // The closest node of the boat's own stretch to x = 800 m is the column-1 node at x = 200 m,
        // 600 m away — well past the snap radius, and it still resolves.
        assertEquals(200.0, route.distanceM, 1.0)
        assertEquals(lonOf(200.0), route.points.last().longitude, 1e-9)
    }

    @Test
    fun aLandAimInsideTheCorridorResolvesIntoTheBoatSStretch() {
        // An aim on land, 4.8 km from the nearest node and inside the box: the one-stretch rule
        // answers it rather than the radius gate rejecting it, and the route really ends there.
        val mesh = gridMesh(cols = 2, rows = 1, boxExtraM = 10_000.0)
        val search = RouteSearch(mesh, StubQueries(waterAt = { _, _ -> false }))

        val result = search.search(point(0.0, 0.0), point(5_000.0, 0.0), cruiseSpeedKn = 10.0)

        assertTrue("the land aim must resolve, got $result", result is RouteResult.Success)
        val route = result as RouteResult.Success
        assertTrue("a land aim must be reported as moved", route.destinationMoved)
        assertEquals(lonOf(200.0), route.points.last().longitude, 1e-9)
        assertEquals(200.0, route.distanceM, 1.0)
    }

    @Test
    fun anAimFarIntoAnotherStretchStillResolvesIntoTheBoatSStretch() {
        // The aim is water but 4.8 km past the last node of another stretch: no radius cap applies
        // to an aim, only the corridor's box does.
        val mesh = gridMesh(
            cols = 5, rows = 5, boxExtraM = 10_000.0,
            componentOf = { col, _ -> if (col < 2) 0 else 1 }
        )
        val search = RouteSearch(mesh, StubQueries())

        val result = search.search(point(0.0, 0.0), point(5_000.0, 0.0), cruiseSpeedKn = 10.0)

        assertTrue("expected a route, got $result", result is RouteResult.Success)
        val route = result as RouteResult.Success
        assertTrue(route.destinationMoved)
        assertEquals(200.0, route.distanceM, 1.0)
    }

    @Test
    fun aStartOutsideTheCorridorIsReportedOutsideTheMesh() {
        val mesh = gridMesh(cols = 5, rows = 5)
        val search = RouteSearch(mesh, StubQueries())

        val result = search.search(point(50_000.0, 0.0), point(800.0, 0.0), cruiseSpeedKn = 10.0)

        assertEquals(RouteResult.OutsideMesh, result)
    }

    @Test
    fun anAimOutsideTheCorridorIsReportedOutsideTheMesh() {
        val mesh = gridMesh(cols = 5, rows = 5)
        val search = RouteSearch(mesh, StubQueries())

        val result = search.search(point(0.0, 0.0), point(50_000.0, 0.0), cruiseSpeedKn = 10.0)

        assertEquals(RouteResult.OutsideMesh, result)
    }

    @Test
    fun theStartKeepsTheRadiusGateInsideTheCorridor() {
        // Inside the box but 4.8 km from any node: the boat is reported, never dragged onto the
        // mesh — the radius gate belongs to the start alone.
        val mesh = gridMesh(cols = 2, rows = 1, boxExtraM = 10_000.0)
        val search = RouteSearch(mesh, StubQueries())

        val result = search.search(point(5_000.0, 0.0), point(200.0, 0.0), cruiseSpeedKn = 10.0)

        assertEquals(RouteResult.OutsideMesh, result)
    }

    // ── The zones' interiors, and the crossing that is a fallback ─────────────

    /**
     * A zone shaped as a half-plane south of `y = 500 m`, its inner boundary at `x = 100…700 m`, so a
     * route along the southern row crosses it and a route one row north does not.
     */
    private fun southernZoneAt5Kn(): StubQueries {
        val zoneLat = latOf(500.0)
        val west = lonOf(100.0)
        val east = lonOf(700.0)
        return StubQueries(limitAt = { lat, lon ->
            if (lat < zoneLat && lon > west && lon < east) 5.0 else null
        })
    }

    @Test
    fun aZoneInteriorIsNeverEnteredWhileAWayAroundExists() {
        // The mesh ships its triangles, so the shortcut pass and the fillet can cut corners: the
        // crossing is refused whatever the clock says, and every point of the drawn line stands
        // outside the zone — a rule the search obeys and its passes ignore would be no rule at all.
        val mesh = gridMesh(cols = 5, rows = 5, triangles = true)
        val queries = southernZoneAt5Kn()
        val search = RouteSearch(mesh, queries)

        val result = search.search(point(0.0, 0.0), point(800.0, 0.0), cruiseSpeedKn = 10.0)

        assertTrue("expected a way around the zone, got $result", result is RouteResult.Success)
        val route = result as RouteResult.Success
        assertTrue(
            "a way around existed, so nothing may be reported as a forced crossing",
            route.forcedCrossingZoneNames.isEmpty()
        )
        for (drawn in route.points) {
            assertEquals(
                "the drawn line must stay out of the zone, at $drawn",
                null,
                queries.pricedZoneAt(drawn.latitude, drawn.longitude)
            )
        }
    }

    @Test
    fun aBoatInsideAZoneCanStillLeaveIt() {
        // The start stands inside the zone and the aim does not: were an interior simply forbidden,
        // the boat would be stranded in its own zone. The zone the **resolved start node** stands in
        // is excepted, so the route exists and it leaves.
        val mesh = gridMesh(cols = 5, rows = 5)
        val queries = StubQueries(limitAt = { lat, _ -> if (lat < latOf(300.0)) 5.0 else null })
        val search = RouteSearch(mesh, queries)

        val result = search.search(point(0.0, 0.0), point(800.0, 600.0), cruiseSpeedKn = 10.0)

        assertTrue(
            "a boat inside a zone must still be able to leave it, got $result",
            result is RouteResult.Success
        )
        val route = result as RouteResult.Success
        assertEquals(
            "the line must end outside the zone",
            null,
            queries.pricedZoneAt(route.points.last().latitude, route.points.last().longitude)
        )
        assertTrue(
            "leaving the zone one is in is no forced crossing",
            route.forcedCrossingZoneNames.isEmpty()
        )
    }

    @Test
    fun aZoneClipShorterThanTheCrossingStepsIsStillReported() {
        // **A crossing narrower than the report's own step must still be named.** The zone here is a
        // 30 m strip, so no drawn sample of the old reading — one every 50 m along each leg — ever
        // stood inside it, and a forced crossing was rendered as an ordinary route: exactly what the
        // crossing's own report exists to prevent. It is deep enough to hold a mesh node, so the
        // traversal rule really refuses the crossing and the fallback really takes it.
        val mesh = gridMesh(cols = 2, rows = 9, spacingM = 120.0, triangles = true)
        val queries = StubQueries(limitAt = { lat, _ ->
            if (lat > latOf(345.0) && lat < latOf(375.0)) 5.0 else null
        })
        val search = RouteSearch(mesh, queries)

        val result = search.search(point(0.0, 0.0), point(0.0, 960.0), cruiseSpeedKn = 10.0)

        assertTrue("expected a route, got $result", result is RouteResult.Success)
        val route = result as RouteResult.Success
        assertEquals(
            "the forced crossing must be named, however narrow the zone it crosses",
            listOf("stub zone 5.0 kn"),
            route.forcedCrossingZoneNames
        )
    }

    @Test
    fun aZoneWallAcrossTheCorridorForcesTheCrossingAndTheResultNamesIt() {
        // The zone spans every column, so no way around exists inside the corridor: the traversal rule
        // finds none, the crossing is priced as it always was, and the result carries the zone's name
        // rather than presenting a forced crossing as an ordinary route.
        val mesh = gridMesh(cols = 5, rows = 5)
        val queries = StubQueries(limitAt = { lat, _ ->
            if (lat > latOf(100.0) && lat < latOf(700.0)) 5.0 else null
        })
        val search = RouteSearch(mesh, queries)

        val result = search.search(point(0.0, 0.0), point(0.0, 800.0), cruiseSpeedKn = 10.0)

        assertTrue("the crossing must still be taken, got $result", result is RouteResult.Success)
        val route = result as RouteResult.Success
        assertEquals(listOf("stub zone 5.0 kn"), route.forcedCrossingZoneNames)
        assertEquals(800.0, route.distanceM, 3.0)
        // Every leg of that line touches a node inside the zone, so the drawn line is timed at the
        // zone's own limit — the clock follows the line, never the plan the search priced.
        assertEquals(800.0 / (5.0 * Units.MPS_PER_KNOT), route.durationSec, 2.0)
        assertTrue(
            "the drawn line costs no more than what the search accumulated",
            route.durationSec <= readings(route).pricedSec + 1e-6
        )
    }

    @Test
    fun theBandIsNeverForbiddenAndACoastalRouteStaysUnmarked() {
        // The 300 m band is a price and never a traversal rule. **And the stub is given a zone, so the
        // claim has something to be true against**: every edge is marked in band at 2 kn, and the whole
        // northern row is a 10 kn zone spanning columns 0–3, so the only way from (0,0) to (800,400) is
        // the band's own water along the southern row and up the open column. A band-forbidden
        // regression closes the water the line already lives in and loses the route outright; a zone
        // that is merely priced rather than forbidden is cut across and the line is the 894 m diagonal.
        // The straightening pass is off, so the length read here is the chain's own.
        val mesh = gridMesh(cols = 5, rows = 3, inBandOf = { _, _ -> true })
        val queries = StubQueries(
            limitAt = { lat, lon -> if (lat > latOf(300.0) && lon < lonOf(600.0)) 10.0 else null },
            coastalBandSpeedLimitKn = 2.0
        )
        val search = RouteSearch(mesh, queries, shortcutPass = false)

        val result = search.search(point(0.0, 0.0), point(800.0, 400.0), cruiseSpeedKn = 10.0)

        assertTrue("the band is a price, never a wall, got $result", result is RouteResult.Success)
        val route = result as RouteResult.Success
        assertTrue("the line really lies in the band", route.inBand)
        assertTrue(
            "the band is no interior, so nothing was forced",
            route.forcedCrossingZoneNames.isEmpty()
        )
        assertEquals(
            "and the route goes around the zone rather than across it",
            1_200.0,
            route.distanceM,
            3.0
        )
    }

    // ── Every pass re-prices what it changes ──────────────────────────────────

    @Test
    fun aShortcutIsTakenWhereItIsCheaperAtTheLimitsInForce() {
        // The chain bends only because the fabric has no diagonal edge, and the destination stands in a
        // 2 kn zone, so the last leg of any route is slow and unavoidable. That is what makes the
        // **whole-span** candidate refused **on price** while it lies on vetted water: its own 566 m
        // would be priced at 2 kn end to end, where the chain pays that limit only on its last leg. The
        // walk steps back to the farthest candidate that is cheap enough, which is the gate doing its
        // work — with the cost test absent the pass would take the farthest water-clean candidate and
        // the line would be its two ends, as this test used to assert and could not fail on.
        // (The cap is off so the drawn line is the shortcut itself, with no arc over it.)
        val mesh = gridMesh(cols = 3, rows = 3, triangles = true)
        val search = RouteSearch(
            mesh,
            StubQueries(limitAt = { lat, lon ->
                if (lat > latOf(399.0) && lon > lonOf(399.0)) 2.0 else null
            }),
            turnLateralAccelMps2 = { 0.0 }
        )

        val result = search.search(point(0.0, 0.0), point(400.0, 400.0), cruiseSpeedKn = 10.0)

        assertTrue("expected a route, got $result", result is RouteResult.Success)
        val route = result as RouteResult.Success
        assertEquals(
            "the whole-span candidate must be refused by the price",
            1,
            readings(route).shortcutRefusedByCostSegments
        )
        assertEquals("and the water refused none of them", 0, readings(route).shortcutRefusedSegments)
        assertEquals(
            "so the farthest candidate the price allows is the one kept",
            3,
            readings(route).nodeIndices.size
        )
        assertEquals("and the drawn line is those two legs", 3, route.points.size)
        assertEquals(447.2 + 200.0, route.distanceM, 4.0)
    }

    @Test
    fun aShortcutIsRefusedWhereItWouldBeDearerAtTheLimitsInForce() {
        // Water that is fast up to 400 m from the start and 2 kn in band beyond that, both rows alike
        // so no detour helps: the chain crosses the band and the straight line from end to end would
        // be priced at the band's own limit over its whole length — 800 m at 2 kn against 400 m at
        // 10 kn and 400 m at 2 kn. It is refused, and the refusal is the price's own rather than the
        // water's, which is what the two counts say.
        val mesh = gridMesh(
            cols = 5, rows = 2, triangles = true,
            inBandOf = { a, b -> a >= 2 && b >= 2 }
        )
        val search = RouteSearch(
            mesh,
            StubQueries(coastalBandSpeedLimitKn = 2.0),
            turnLateralAccelMps2 = { 0.0 }
        )

        val result = search.search(point(0.0, 0.0), point(800.0, 0.0), cruiseSpeedKn = 10.0)

        assertTrue("expected a route, got $result", result is RouteResult.Success)
        val route = result as RouteResult.Success
        assertTrue(
            "the refusal must be the price's own, not the water's",
            readings(route).shortcutRefusedByCostSegments > 0
        )
        assertTrue(
            "a shortcut was refused, so the chain's own legs are what stands",
            readings(route).nodeIndices.size >= 3
        )
        // 400 m at 10 kn then 400 m at 2 kn: the drawn clock reads the chain, where the refused
        // shortcut would have priced the whole 800 m at the band's own 2 kn — 1,554 s.
        assertEquals(
            400.0 / (10.0 * Units.MPS_PER_KNOT) + 400.0 / (2.0 * Units.MPS_PER_KNOT),
            route.durationSec,
            5.0
        )
    }

    @Test
    fun theDrawnClockNeverExceedsTheSecondsTheSearchAccumulated() {
        // The invariant P0 is stated with, read from outside: the drawn line may be cheaper than the
        // chain the search priced and never dearer — and on this fixture it is cheaper still, the
        // mesh's flat metre frame overstating every leg the drawn clock measures geodesically.
        val mesh = gridMesh(cols = 5, rows = 5, triangles = true)
        val search = RouteSearch(mesh, southernZoneAt5Kn())

        val result = search.search(point(0.0, 0.0), point(800.0, 0.0), cruiseSpeedKn = 10.0)

        assertTrue("expected a route, got $result", result is RouteResult.Success)
        val route = result as RouteResult.Success
        assertTrue(route.durationSec <= readings(route).pricedSec + 1e-6)
        assertEquals(route.durationSec, route.legTimesSec.sum(), 1e-9)
        assertFalse(
            "a line that fits its own budget must not have needed the fallback",
            readings(route).rawChainFallback
        )
    }

    @Test
    fun aBreachOfTheInvariantFallsBackToTheRawNodeChainAndSaysSo() {
        // The invariant's own path, forced through the public API: a mesh whose edge lengths understate
        // the geometry the drawn line is measured on is a stand-in for a pass that changed the line
        // without re-pricing it, so the drawn clock runs past the search's accumulation. The boat must
        // keep its route — the raw node chain, with no merge, no shortcut and no fillet — and the run
        // must say that it fell back rather than throwing out of `buildSuccess`, where a caller had no
        // net (the preview and the recompute both call straight into it).
        val mesh = gridMesh(cols = 5, rows = 1, edgeLengthScale = 0.5)
        val warnings = ArrayList<String>()
        val search = RouteSearch(mesh, StubQueries(), warn = warnings::add)

        val result = search.search(point(0.0, 0.0), point(800.0, 0.0), cruiseSpeedKn = 10.0)

        assertTrue("a breach must still answer a route, got $result", result is RouteResult.Success)
        val route = result as RouteResult.Success
        assertTrue("and it must say the fallback fired", readings(route).rawChainFallback)
        assertEquals(
            "the line is the raw chain, so the four collinear vertices the merge would have taken stay",
            5,
            readings(route).nodeIndices.size
        )
        assertEquals("its length is the raw chain's own", 800.0, route.distanceM, 3.0)
        assertEquals(route.durationSec, route.legTimesSec.sum(), 1e-9)
        assertEquals("one warning, and it names the fallback", 1, warnings.size)
        assertTrue(
            "the warning has to say what happened, got '${warnings.firstOrNull()}'",
            warnings.first().contains("raw node chain")
        )
    }

    // ── Cancellation ──────────────────────────────────────────────────────────
    // The ≤ 500 ms budget is measured in RouteMeshPrebakeTest, on the mesh the app actually ships.

    @Test
    fun aFlungMapCancelsTheSearchInFlight() {
        val mesh = gridMesh(cols = 60, rows = 60)
        val search = RouteSearch(mesh, StubQueries())

        var expansions = 0
        val failure = runCatching {
            search.search(
                point(0.0, 0.0),
                point(11_800.0, 11_800.0),
                cruiseSpeedKn = 28.0,
                ensureActive = {
                    if (++expansions > 1) throw kotlinx.coroutines.CancellationException("flung")
                }
            )
        }

        assertTrue(failure.isFailure)
        assertTrue(failure.exceptionOrNull() is kotlinx.coroutines.CancellationException)
    }

    private companion object {
        const val ORIGIN_LAT = 43.5
        const val ORIGIN_LON = 7.0
        const val METRES_PER_DEG_LAT = 111_320.0

        /** The berth these two tests price at, since the shipped key ships switched off. */
        const val BERTH_M = 25.0
    }
}
