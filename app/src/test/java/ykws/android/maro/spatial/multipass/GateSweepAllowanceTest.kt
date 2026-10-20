package ykws.android.maro.spatial.multipass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.spatial.Units
import ykws.android.maro.spatial.multipass.GateTestFixtures.at

/**
 * **The sweep's allowance never trades the berth for a shorter line.** The allowance is a share of the legs'
 * field price; the berth and the travel are compared strictly, so a chord that cuts into the inner ramp and
 * is a few percent slower overall keeps the vertex holding the line off it, while a clear chord no dearer
 * drops it. On a straight shelf, shallow south of 100 m, at 5 kn with the shipped berth.
 */
class GateSweepAllowanceTest {

    private val share = 0.1
    private val berth = BerthLaw(marginM = 20.0, outerM = 50.0, secPerM = 0.1, floorM = 10.0, innerSecPerM = 0.5)

    private fun setupOver(start: LatLng, aim: LatLng): PullSetup {
        val raster = GateTestFixtures.raster(250, 110, 2.0) { n, _ -> if (n < 100.0) 2.0 else 10.0 }
        val world = GateTestFixtures.RasterWorld(raster)
        return PullSetup(
            50.0, 20.0, 100.0, GateTestFixtures.gateField(world), start, aim,
            gate = GateTestFixtures.gateOver(raster, hardM = 10.0),
            berth = berth,
            berthLambda = 1.0,
            paceMps = Units.knotsToMps(5.0)
        )
    }

    @Test
    fun aChordCuttingTheInnerRampKeepsTheVertexWithinTheOldAllowance() {
        val a = at(115.0, 0.0)
        val b = at(118.0, 100.0)
        val c = at(115.0, 200.0)
        val setup = setupOver(a, c)
        val ctx = setup.context()
        val chordSec = MultipassPull.totalSec(a, c, ctx)
        val pathSec = MultipassPull.totalSec(a, b, ctx) + MultipassPull.totalSec(b, c, ctx)
        assertTrue("the chord is slower in total ($chordSec vs $pathSec)", chordSec > pathSec)
        assertTrue(
            "but within 10 % of the legs' total — what the old allowance dropped on",
            chordSec <= pathSec * (1.0 + share)
        )

        val lines = mutableListOf<String>()
        val swept = MultipassPull.sweep(setup, listOf(a, b, c), refusals = null, trace = { lines += it() }, allowanceShare = share)

        assertEquals("the vertex holding the line off the ramp is kept", listOf(a, b, c), swept)
        assertTrue("on the price proof", lines.any { it.startsWith("SWEEP keep idx=1") && it.contains("why=PRICE") })
    }

    @Test
    fun aClearChordNoDearerStillDrops() {
        val a = at(400.0, 0.0)
        val b = at(402.0, 100.0)
        val c = at(400.0, 200.0)
        val setup = setupOver(a, c)

        val swept = MultipassPull.sweep(setup, listOf(a, b, c), refusals = null, allowanceShare = share)

        assertEquals("the vertex drops", listOf(a, c), swept)
    }
}
