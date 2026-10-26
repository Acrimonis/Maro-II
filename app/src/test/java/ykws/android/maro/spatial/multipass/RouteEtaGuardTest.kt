package ykws.android.maro.spatial.multipass

import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.LatLng

/** The clock never hands the ranking an infinite or undefined second: a 0 kn limit or pace is floored. */
class RouteEtaGuardTest {

    private val a = LatLng(43.5, 7.0)
    private val b = LatLng(43.5, 7.01)

    @Test
    fun aZeroLimitTimesALegFinitely() {
        val timed = timeLineWithLimits(listOf(a, b), paceKn = 12.0, limitKnAt = { 0.0 }, sampleM = 10.0)
        assertTrue("a leg under a 0 kn limit still reads a finite clock", timed.durationSec.isFinite())
        assertTrue(timed.legSpeedsMps.all { it > 0.0 })
    }

    @Test
    fun aZeroPaceTimesALegFinitely() {
        val timed = timeLineWithLimits(listOf(a, b), paceKn = 0.0, limitKnAt = { null }, sampleM = 10.0)
        assertTrue(timed.durationSec.isFinite())
        val profile = timeLineWithProfile(listOf(a, b), 0.0, { null }, { null }, 10.0)
        assertTrue(profile.durationSec.isFinite())
    }
}
