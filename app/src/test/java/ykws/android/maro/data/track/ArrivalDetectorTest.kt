package ykws.android.maro.data.track

import org.junit.Assert.assertEquals
import org.junit.Test
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.track.ArrivalDetector.Signal
import ykws.android.maro.data.track.AutoStartDetector.Fix
import ykws.android.maro.data.track.AutoStartDetector.Home

/**
 * The auto-save on return's arrival rule: a trip that has been out of the 100 m home circle enters the 50 m
 * arrival circle (a hold begins), and a fix clearly out of it ends the hold. Fixes are 10 s apart unless a
 * test says otherwise.
 */
class ArrivalDetectorTest {

    private val homeLat = 43.5696
    private val homeLon = 7.1313
    private val home = Home(LatLng(homeLat, homeLon), 100.0)
    private val arrivalM = 50.0
    private val metresPerDegree = 6_371_000.0 * Math.PI / 180.0

    private fun at(northM: Double) = LatLng(homeLat + northM / metresPerDegree, homeLon)

    private inner class Rig {
        val detector = ArrivalDetector(maxSpeedMps = 32.0 * 0.514444)
        var now = 1_000_000L
        var allowed = true

        fun fix(
            north: Double,
            accuracy: Float? = 5f,
            demo: Boolean = false,
            after: Long = 10_000L,
            home: Home? = this@ArrivalDetectorTest.home
        ): Signal {
            now += after
            return detector.onFix(Fix(at(north), now, accuracy, 2f, !demo, demo), home, arrivalM, allowed)
        }
    }

    @Test
    fun `back from open sea into the arrival circle a hold begins`() {
        val rig = Rig()
        rig.fix(300.0)
        rig.fix(150.0)
        rig.fix(80.0)
        assertEquals(Signal.ENTERED, rig.fix(40.0))
        assertEquals(true, rig.detector.isHolding)
        assertEquals(Signal.NONE, rig.fix(20.0))
    }

    @Test
    fun `a trip that never left the home circle is never held`() {
        val rig = Rig()
        rig.fix(90.0)
        rig.fix(60.0)
        assertEquals(Signal.NONE, rig.fix(30.0))
    }

    @Test
    fun `a fix just past the home edge within its accuracy is not open sea`() {
        val rig = Rig()
        rig.fix(105.0, accuracy = 10f)
        assertEquals(Signal.NONE, rig.fix(40.0))
    }

    @Test
    fun `a fix clearly out of the arrival circle ends the hold - one within its accuracy does not`() {
        val rig = Rig()
        rig.fix(300.0)
        rig.fix(80.0)
        rig.fix(40.0)
        assertEquals(Signal.NONE, rig.fix(54.0, accuracy = 5f))
        assertEquals(Signal.LEFT, rig.fix(60.0, accuracy = 5f))
        assertEquals(false, rig.detector.isHolding)
        assertEquals("re-entering holds again", Signal.ENTERED, rig.fix(30.0))
    }

    @Test
    fun `a spike into the arrival circle is no entry - the real arrival is`() {
        val rig = Rig()
        rig.fix(2_000.0)
        // 1 960 m in 10 s: about 380 kn.
        assertEquals(Signal.NONE, rig.fix(40.0))
        rig.fix(2_000.0)
        rig.fix(150.0, after = 300_000L)
        assertEquals(Signal.ENTERED, rig.fix(40.0))
    }

    @Test
    fun `demo samples bypass the spike guard`() {
        val rig = Rig()
        rig.fix(2_000.0, demo = true)
        assertEquals(Signal.ENTERED, rig.fix(40.0, demo = true, after = 1_000L))
    }

    @Test
    fun `switched off for the mode no hold begins`() {
        val rig = Rig()
        rig.allowed = false
        rig.fix(300.0)
        assertEquals(Signal.NONE, rig.fix(40.0))
        rig.allowed = true
        rig.fix(80.0)
        assertEquals(Signal.ENTERED, rig.fix(40.0))
    }

    @Test
    fun `a reset needs a fresh exit - a Continue at the berth is not saved again`() {
        val rig = Rig()
        rig.fix(300.0)
        rig.fix(150.0)
        assertEquals(Signal.ENTERED, rig.fix(40.0))
        rig.detector.reset()
        assertEquals(false, rig.detector.isHolding)
        assertEquals(Signal.NONE, rig.fix(30.0))
        rig.fix(200.0)
        rig.fix(80.0)
        assertEquals(Signal.ENTERED, rig.fix(30.0))
    }

    @Test
    fun `an arrival circle wider than the home one is capped at it`() {
        val rig = Rig()
        rig.fix(300.0)
        // 120 m out: outside the 100 m home circle, so no hold even with a 200 m arrival setting.
        rig.now += 10_000L
        val signal = rig.detector.onFix(
            Fix(at(120.0), rig.now, 5f, 2f, hasLock = true, isDemo = false), home, 200.0, allowed = true
        )
        assertEquals(Signal.NONE, signal)
    }

    @Test
    fun `the home circle switched off ends a hold`() {
        val rig = Rig()
        rig.fix(300.0)
        rig.fix(150.0)
        assertEquals(Signal.ENTERED, rig.fix(40.0))
        assertEquals(Signal.LEFT, rig.fix(40.0, home = null))
        assertEquals(Signal.NONE, rig.fix(30.0))
    }
}
