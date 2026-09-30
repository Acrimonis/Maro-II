package ykws.android.maro.spatial.avoid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.DepthSample
import ykws.android.maro.data.model.DepthSource
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.BBox
import ykws.android.maro.spatial.SpatialOperations
import ykws.android.maro.spatial.Units
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Phase 6's own contract over an open-water grid and a fake world: the straight line is left whole,
 * a single bend is faired into arc points that still clear the walls, a wall no radius clears keeps
 * the vertex sharp, an S-bend is two bends, a run joins same-sign corners within twice the **floor's**
 * transition and splits a longer separating leg, a run whose single curve cannot meet the legs is
 * faired corner by corner, a faired cap never exceeds its own curve's radius, and the clock charges
 * the caps beside its ramp.
 */
class RouteCurveFitterTest {

    private val base = LatLng(43.5000, 7.0000)
    private val mPerDegLat = SpatialOperations.EARTH_RADIUS_M * PI / 180.0
    private val mPerDegLon = mPerDegLat * cos(Math.toRadians(base.latitude))

    private val paceKn = 12.0
    private val marginM = AppConfig.routeAvoidObstacleMarginM

    /** A point [eastM] east and [northM] north of the base, in the local metric frame. */
    private fun at(eastM: Double, northM: Double): LatLng =
        LatLng(base.latitude + northM / mPerDegLat, base.longitude + eastM / mPerDegLon)

    /** The whole test's box, wide enough for every bend's curve and straights. */
    private val box = BBox(43.47, 43.53, 6.98, 7.03)

    private fun openGrid(cellM: Double = 50.0): AvoidGrid {
        val midLat = (box.latSouth + box.latNorth) / 2.0
        val mPerDegLatLocal = SpatialOperations.EARTH_RADIUS_M * PI / 180.0
        val mPerDegLonLocal = mPerDegLatLocal * cos(Math.toRadians(midLat))
        val cellSizeDegLat = cellM / mPerDegLatLocal
        val cellSizeDegLon = cellM / mPerDegLonLocal
        val cols = ceil((box.lonEast - box.lonWest) / cellSizeDegLon).toInt().coerceAtLeast(1)
        val rows = ceil((box.latNorth - box.latSouth) / cellSizeDegLat).toInt().coerceAtLeast(1)
        return AvoidGrid(
            box.latSouth, box.lonWest, cellSizeDegLat, cellSizeDegLon, rows, cols, cellM,
            baseCostSec(cellM, paceKn)
        )
    }

    private fun faired(
        line: List<LatLng>,
        world: AvoidWorld = FakeWorld(),
        grid: AvoidGrid = openGrid(),
        depthGateActive: Boolean = false
    ): FairedLine = RouteCurveFitter.fit(
        line = line,
        grid = grid,
        box = box,
        approaches = EndApproaches.NONE,
        world = world,
        paceKn = paceKn,
        marginM = marginM,
        start = line.first(),
        aim = line.last(),
        depthGateActive = depthGateActive,
        minDepthM = AppConfig.routeAvoidDepthGateMinM
    )

    // ── The straight line and the single bend ──────────────────────────────────

    @Test
    fun aStraightLineIsLeftWhole() {
        val line = listOf(at(0.0, 0.0), at(0.0, 800.0))

        val result = faired(line)

        assertEquals("a two-point line holds no bend", 0, result.bends)
        assertEquals("and is returned untouched", line, result.points)
        assertTrue("with no cap to charge", result.caps.isEmpty())
    }

    @Test
    fun aSingleRightAngleBendIsFairedIntoArcPointsNearTheCorner() {
        val a = at(0.0, -300.0)
        val corner = at(0.0, 0.0)
        val b = at(300.0, 0.0)

        val result = faired(listOf(a, corner, b))

        assertEquals("the one bend is resolved", 1, result.bends)
        assertEquals("nothing is kept sharp", 0, result.keptSharp)
        assertTrue("the faired line gains arc points", result.points.size > 3)
        assertEquals("the raw start survives", a, result.points.first())
        assertEquals("and the raw aim", b, result.points.last())
        assertTrue(
            "the curve still passes near the vertex it rounds",
            result.points.minOf { SpatialOperations.haversine(it, corner) } < 30.0
        )
        assertTrue("every arc point carries a cap", result.caps.isNotEmpty())
    }

    @Test
    fun theCapNeverExceedsThePaceNorFallsBelowTheFloor() {
        // An S-bend: the first bend's approach is the very start (zero metres, its speed is the pace
        // cap), while the second bend's approach is a long leg, so its deceleration floor is the floor.
        val a = at(0.0, -300.0)
        val first = at(0.0, 0.0)
        val second = at(300.0, 0.0)
        val b = at(300.0, 300.0)

        val result = faired(listOf(a, first, second, b))

        val pace = paceKn
        val floor = AppConfig.routeTurnMinSpeedKn
        // A tolerance wider than the knots↔m/s round-trip: the app's two factors are not exact
        // reciprocals, so a floor carried through m/s and back reads a few micro-knots under it.
        val eps = 1e-3
        for (cap in result.caps) {
            assertTrue("a cap never raises the boat above the pace", cap.capKn <= pace + eps)
            assertTrue("nor below the floor", cap.capKn >= floor - eps)
        }
        // The long approach's deceleration floor settles the second bend at minSpeedKn.
        assertEquals(
            "the long approach's corner speed is the floor",
            floor,
            result.caps.minOf { it.capKn },
            eps
        )
    }

    // ── The walls ──────────────────────────────────────────────────────────────

    @Test
    fun aWallInsideTheTurnThatNoRadiusClearsKeepsTheVertexSharp() {
        val a = at(0.0, -300.0)
        val corner = at(0.0, 0.0)
        val b = at(300.0, 0.0)
        // Deep water everywhere, save a shore patch around the corner the curve would cut inside of.
        val world = FakeWorld(coast = { lat, lon ->
            val p = LatLng(lat, lon)
            if (SpatialOperations.haversine(p, corner) < 120.0) 10.0 else Double.MAX_VALUE
        })

        val result = faired(listOf(a, corner, b), world = world)

        assertEquals("no radius clears, so no bend is resolved", 0, result.resolved)
        assertEquals("the bend is kept sharp", 1, result.keptSharp)
        assertEquals("and the search's own vertices stand", listOf(a, corner, b), result.points)
        assertTrue("with no cap to charge", result.caps.isEmpty())
    }

    @Test
    fun aDepthGatedPatchTheCurveWouldCrossKeepsTheVertexSharp() {
        val a = at(0.0, -300.0)
        val corner = at(0.0, 0.0)
        val b = at(300.0, 0.0)
        // A known 2 m patch over the curve's own reach — the gate's minM is 3.
        val world = FakeWorld(depth = { lat, lon ->
            val p = LatLng(lat, lon)
            if (SpatialOperations.haversine(p, corner) < 120.0) 2.0 else 20.0
        })

        val result = faired(listOf(a, corner, b), world = world, depthGateActive = true)

        assertEquals("the gate keeps the bend sharp", 1, result.keptSharp)
        assertEquals("and the vertices stand", listOf(a, corner, b), result.points)
    }

    // ── The runs ───────────────────────────────────────────────────────────────

    @Test
    fun anSBendIsTwoBends() {
        val a = at(0.0, -300.0)
        val first = at(0.0, 0.0)
        val second = at(300.0, 0.0)
        val b = at(300.0, 300.0)

        val result = faired(listOf(a, first, second, b))

        assertEquals("a sign change makes two runs", 2, result.bends)
        assertEquals("both are resolved", 2, result.resolved)
    }

    @Test
    fun aRunJoinsSameSignCornersWithinTwiceTheFloorTransition() {
        // Two +30° corners 6 m apart: inside the floor's 2·v_min·transitionSec (~10.3 m at 5 kn), the
        // shortest transition any bend can have — so one run.
        val a = at(0.0, -300.0)
        val c1 = at(0.0, 0.0)
        val c2 = at(3.0, 5.196)
        val b = at(436.013, 255.196)

        val joined = faired(listOf(a, c1, c2, b))

        assertEquals("the short separating leg joins the corners into one bend", 1, joined.bends)
        assertEquals("which is resolved as a single curve", 1, joined.resolved)
    }

    @Test
    fun aRunSplitsWhereTheSeparatingLegExceedsTwiceTheFloorTransitionButNotThePace() {
        // The same two +30° corners, now 18 m apart: inside the pace's 2·v_pace·transitionSec
        // (~24.7 m at 12 kn) but past the floor's (~10.3 m), so the run splits on the bend's own
        // transition, never the widest one.
        val a = at(0.0, -300.0)
        val c1 = at(0.0, 0.0)
        val c2 = at(9.0, 15.588)
        val b = at(442.0, 265.588)

        val split = faired(listOf(a, c1, c2, b))

        assertEquals("the bend's own transition splits the run", 2, split.bends)
    }

    @Test
    fun aRunSplitsWhereTheSeparatingLegExceedsTwiceTheTransition() {
        // The same two +30° corners, now 300 m apart: past 2·v·transitionSec, so two bends.
        val a = at(0.0, -300.0)
        val c1 = at(0.0, 0.0)
        val c2 = at(150.0, 259.8)
        val b = at(583.0, 509.8)

        val split = faired(listOf(a, c1, c2, b))

        assertEquals("the long separating leg splits the run", 2, split.bends)
        assertEquals("and both bends are resolved", 2, split.resolved)
    }

    @Test
    fun aRunWhoseSingleCurveCannotMeetTheLegsFairsItsCornersIndividually() {
        // A U-turn: two same-sign 90° corners 10.1 m apart, inside the floor's ~10.29 m join leg, so the
        // two form one run. The joined bend turns 180°, whose tangent lines are parallel, so its single
        // curve is refused; each corner is then faired on its own rather than the run dropping whole.
        // The long approach lets the first corner slow into its curve, while the second's 10.1 m
        // approach cannot decelerate it enough for the radius that leg holds, so that one stays sharp.
        val a0 = at(0.0, -600.0)
        val a = at(0.0, -300.0)
        val c1 = at(0.0, 0.0)
        val c2 = at(10.1, 0.0)
        val b = at(10.1, -500.0)

        val result = faired(listOf(a0, a, c1, c2, b))

        assertEquals("the joined run is split into its two corners", 2, result.bends)
        assertEquals("the corner the long approach lets slow is faired", 1, result.resolved)
        assertEquals("the second corner's short approach cannot slow it enough, so it stays sharp", 1, result.keptSharp)
        assertTrue("the faired corner carries a cap", result.caps.isNotEmpty())
    }

    @Test
    fun everyResolvedBendCapIsWithinItsOwnCurvesRadius() {
        // The curvature-continuity promise: a bend's cap may never exceed the speed its own minimum
        // radius allows, so a curve is never drawn faster than its geometry holds.
        val a = at(0.0, -300.0)
        val corner = at(0.0, 0.0)
        val b = at(300.0, 0.0)
        val aLat = AppConfig.routeTurnLateralAccelMps2

        val result = faired(listOf(a, corner, b))

        assertEquals("the one bend is resolved", 1, result.resolved)
        val capCeilingKn = Units.mpsToKnots(sqrt(aLat * RouteCurveFitter.minRadiusM(result.points)))
        for (cap in result.caps) {
            assertTrue(
                "no cap exceeds the speed its own curve's radius allows",
                cap.capKn <= capCeilingKn + 1e-3
            )
        }
    }

    // ── The clock's caps ───────────────────────────────────────────────────────

    @Test
    fun aCapSlowsTheLegItStandsOn() {
        val a = at(0.0, 0.0)
        val b = at(0.0, 1000.0)
        val line = listOf(a, b)

        val free = timeLineWithLimits(line, paceKn, { null })
        val capped = timeLineWithLimits(line, paceKn, { null }, listOf(CurveCap(a, 5.0), CurveCap(b, 5.0)))

        assertTrue("the cap slows the leg", capped.durationSec > free.durationSec + 1.0)
        val capMps = Units.knotsToMps(5.0)
        assertEquals(
            "and the leg rides at no more than the cap",
            1000.0 / capMps,
            capped.durationSec,
            1.0
        )
    }

    @Test
    fun theStrictestCapAtAPointWins() {
        val a = at(0.0, 0.0)
        val b = at(0.0, 1000.0)
        val line = listOf(a, b)

        val slower = timeLineWithLimits(line, paceKn, { null }, listOf(CurveCap(a, 5.0), CurveCap(b, 5.0)))
        val withBoth = timeLineWithLimits(
            line, paceKn, { null },
            listOf(CurveCap(a, 8.0), CurveCap(a, 5.0), CurveCap(b, 8.0), CurveCap(b, 5.0))
        )

        assertEquals(
            "the lower cap at a shared point decides",
            slower.durationSec,
            withBoth.durationSec,
            1e-9
        )
        assertFalse("and it is never the faster of the two", withBoth.durationSec < slower.durationSec)
    }

    // ── The fake world ─────────────────────────────────────────────────────────

    private class FakeWorld(
        private val coast: (Double, Double) -> Double = { _, _ -> Double.MAX_VALUE },
        private val depth: (Double, Double) -> Double = { _, _ -> 20.0 }
    ) : AvoidWorld {
        override val coastlineReady: Boolean get() = true
        override val depthReady: Boolean get() = true
        override val bandWidthM: Double get() = 0.0
        override val regionBounds: BBox? get() = null

        override fun segmentsIn(box: BBox): List<AvoidEdge> = emptyList()
        override fun openCoastIn(box: BBox): List<List<LatLng>> = emptyList()
        override fun isWater(latitude: Double, longitude: Double): Boolean = true
        override fun distanceToCoastM(latitude: Double, longitude: Double): Double = coast(latitude, longitude)

        override fun depthAt(latitude: Double, longitude: Double): DepthSample {
            val sounding = depth(latitude, longitude)
            return if (sounding.isNaN()) DepthSample.NONE
            else DepthSample(sounding.toFloat(), DepthSource.LITTO3D, 100, true)
        }

    }
}
