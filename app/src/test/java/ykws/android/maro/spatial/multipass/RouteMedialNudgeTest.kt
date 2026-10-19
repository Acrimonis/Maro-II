package ykws.android.maro.spatial.multipass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.spatial.Units
import ykws.android.maro.spatial.multipass.GateTestFixtures.at
import kotlin.math.abs

/**
 * **The medial nudge, pinned on a straight passage.** Two shallow banks stand 50 m apart; a line drawn
 * 8 m off the passage's middle is moved onto it, while a line in open water beside a single bank is left
 * where it is.
 */
class RouteMedialNudgeTest {

    private val law = BerthLaw(marginM = 20.0, outerM = 50.0, secPerM = 0.1, floorM = 10.0, innerSecPerM = 0.5)

    private fun northOf(p: LatLng): Double = (p.latitude - GateTestFixtures.LAT0) * GateTestFixtures.M_PER_DEG_LAT

    private fun nudged(depth: (Double, Double) -> Double, northM: Double): List<LatLng> {
        val raster = GateTestFixtures.raster(200, 200, 2.0, depth = depth)
        val world = GateTestFixtures.RasterWorld(raster)
        val gate = GateTestFixtures.gateOver(raster, hardM = 10.0, preferredM = 20.0)
        val start = at(northM, 20.0)
        val aim = at(northM, 380.0)
        val setup = PullSetup(
            50.0, 20.0, 100.0, GateTestFixtures.gateField(world), start, aim,
            gate = gate, berth = law, berthLambda = 1.0, paceMps = Units.knotsToMps(5.0)
        )
        return RouteMedialNudge.nudge(setup, gate, listOf(start, aim))
    }

    @Test
    fun aLineOffThePassagesMiddleIsMovedOntoIt() {
        val line = nudged({ n, _ -> if (abs(n - 200.0) < 25.0) 10.0 else 2.0 }, northM = 208.0)
        assertTrue("the stretch was moved", line.size > 2)
        for (p in line.subList(1, line.size - 1)) {
            assertTrue("a moved vertex stands on the middle (${"%.2f".format(northOf(p))} m)", abs(northOf(p) - 200.0) < 1.0)
        }
    }

    /** A passage whose middle is not the line's own offset: the line moves to the banks' midpoint. */
    @Test
    fun aLineMovesToTheBanksMidpointWhereverItStands() {
        val line = nudged({ n, _ -> if (n > 165.0 && n < 215.0) 10.0 else 2.0 }, northM = 200.0)
        assertTrue("the stretch was moved", line.size > 2)
        for (p in line.subList(1, line.size - 1)) {
            assertTrue("a moved vertex stands midway (${"%.2f".format(northOf(p))} m)", abs(northOf(p) - 190.0) < 1.0)
        }
    }

    /**
     * A passage that bends: the nudge never moves a vertex further off the passage's axis than the line it
     * replaced, and every chord of the nudged line keeps the wall.
     */
    @Test
    fun aBentPassageIsCentredWithoutLeavingIt() {
        // The axis runs east along 200 m north to (200, 200), then north-east to (400, 400).
        fun axisDistanceM(n: Double, e: Double): Double {
            val east = if (e <= 200.0) abs(n - 200.0) else Double.MAX_VALUE
            val t = (((n - 200.0) + (e - 200.0)) / 2.0).coerceIn(0.0, 200.0)
            val diag = kotlin.math.hypot(n - (200.0 + t), e - (200.0 + t))
            return minOf(east, diag, kotlin.math.hypot(n - 200.0, e - 200.0))
        }
        val raster = GateTestFixtures.raster(220, 220, 2.0) { n, e -> if (axisDistanceM(n, e) < 25.0) 10.0 else 2.0 }
        val world = GateTestFixtures.RasterWorld(raster)
        val gate = GateTestFixtures.gateOver(raster, hardM = 10.0, preferredM = 20.0)
        val start = at(208.0, 20.0)
        val bend = at(208.0, 196.7)
        val aim = at(394.3, 388.7)
        val setup = PullSetup(
            50.0, 20.0, 100.0, GateTestFixtures.gateField(world), start, aim,
            gate = gate, berth = law, berthLambda = 1.0, paceMps = Units.knotsToMps(5.0)
        )
        val original = listOf(start, bend, aim)
        val line = RouteMedialNudge.nudge(setup, gate, original)
        assertTrue("the bent passage's stretches were moved", line.size > original.size)
        val ctx = setup.context()
        for (k in 0 until line.size - 1) {
            assertTrue("chord $k keeps the wall", MultipassPull.legClearCause(line[k], line[k + 1], ctx) == null)
        }
        for (p in line) {
            val n = northOf(p)
            val e = (p.longitude - GateTestFixtures.LON0) * GateTestFixtures.M_PER_DEG_LON
            assertTrue("no vertex leaves further than the line did (${"%.1f".format(axisDistanceM(n, e))} m)", axisDistanceM(n, e) <= 8.5)
        }
        val centred = line.subList(1, line.size - 1).count { p ->
            axisDistanceM(northOf(p), (p.longitude - GateTestFixtures.LON0) * GateTestFixtures.M_PER_DEG_LON) < 2.0
        }
        assertTrue("and moved vertices stand on the axis ($centred)", centred > 0)
    }

    @Test
    fun aLineBesideOneBankInOpenWaterIsLeftAlone() {
        val line = nudged({ n, _ -> if (n < 175.0) 2.0 else 10.0 }, northM = 208.0)
        assertEquals("no passage, no nudge", 2, line.size)
    }
}
