package ykws.android.maro.ui.map

import org.junit.Assert.assertEquals
import org.junit.Test

/** The gate on the panel's row order: the first change at once, a burst held to the interval, a force at once. */
class RouteOrderGateTest {

    @Test
    fun theFirstChangeAppliesAtOnce() {
        assertEquals(0L, RouteOrderGate(1_000L).waitMs(nowMs = 5_000L, force = false))
    }

    @Test
    fun aChangeInsideTheIntervalWaitsForItsEnd() {
        val gate = RouteOrderGate(1_000L)
        gate.applied(10_000L)
        assertEquals("a change 300 ms later waits 700 ms", 700L, gate.waitMs(10_300L, force = false))
        assertEquals("a change after the interval applies", 0L, gate.waitMs(11_000L, force = false))
        assertEquals("and one long after it too", 0L, gate.waitMs(20_000L, force = false))
    }

    @Test
    fun aForcedChangeBypassesTheGate() {
        val gate = RouteOrderGate(1_000L)
        gate.applied(10_000L)
        assertEquals(0L, gate.waitMs(10_100L, force = true))
    }

    @Test
    fun aResetForgetsTheLastRefresh() {
        val gate = RouteOrderGate(1_000L)
        gate.applied(10_000L)
        gate.reset()
        assertEquals(0L, gate.waitMs(10_100L, force = false))
    }

    @Test
    fun aZeroIntervalNeverWaits() {
        val gate = RouteOrderGate(0L)
        gate.applied(10_000L)
        assertEquals(0L, gate.waitMs(10_000L, force = false))
    }
}
