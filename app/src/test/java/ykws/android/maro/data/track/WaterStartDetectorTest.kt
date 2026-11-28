package ykws.android.maro.data.track

import org.junit.Assert.assertEquals
import org.junit.Test
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.track.AutoStartDetector.Fix
import ykws.android.maro.data.track.AutoStartDetector.Home

/**
 * The start on water on a hand-driven monotonic clock: outside the 100 m home circle, a positive move of
 * 50 m from the reference point at a Doppler of at least 0.5 m/s on water starts a recording; the stop
 * detection's 45 s / 15 m define a rest. Positions are metres north of a point 2 km north of the home
 * origin; fixes are 10 s apart and report 2 m/s unless a test says otherwise.
 */
class WaterStartDetectorTest {

    private val homeLat = 43.5696
    private val homeLon = 7.1313
    private val home = Home(LatLng(homeLat, homeLon), 100.0)
    private val metresPerDegree = 6_371_000.0 * Math.PI / 180.0
    private val params = WaterStartDetector.Params(distanceM = 50.0, restWindowMs = 45_000L, restDistanceM = 15.0)

    /** [northM] metres north of a point at sea 2 km north of the home origin; negative reaches the port. */
    private fun at(northM: Double) = LatLng(homeLat + (2_000.0 + northM) / metresPerDegree, homeLon)

    private inner class Rig {
        val detector = WaterStartDetector(maxSpeedMps = 32.0 * 0.514444)
        var now = 1_000_000L
        var water: Boolean? = true
        var allowed = true
        var waterAsked = 0

        fun fix(
            north: Double,
            speed: Float? = 2f,
            accuracy: Float? = 5f,
            demo: Boolean = false,
            after: Long = 10_000L
        ): Boolean {
            now += after
            return detector.onSample(Fix(at(north), now, accuracy, speed, !demo, demo), home, params, allowed) {
                waterAsked++
                water
            }
        }

        fun observe(north: Double, after: Long = 10_000L) {
            now += after
            detector.observe(Fix(at(north), now, 5f, 2f, hasLock = true, isDemo = false), params)
        }
    }

    // ── The start ───────────────────────────────────────────────────────────

    @Test
    fun `the app opened under way starts once the boat has covered the distance`() {
        val rig = Rig()
        assertEquals("the first fix is the reference", false, rig.fix(0.0))
        assertEquals(false, rig.fix(20.0))
        assertEquals(false, rig.fix(40.0))
        assertEquals(true, rig.fix(60.0))
    }

    @Test
    fun `a boat still at anchor starts nothing - swinging without Doppler is no move`() {
        val rig = Rig()
        rig.fix(0.0, speed = 0f)
        assertEquals(false, rig.fix(60.0, speed = 0.3f, after = 120_000L))
        assertEquals(false, rig.fix(0.0, speed = 0.2f, after = 120_000L))
    }

    @Test
    fun `land or an unknown water answer holds the start for the next fix`() {
        val rig = Rig()
        rig.fix(0.0)
        rig.water = false
        assertEquals(false, rig.fix(60.0))
        rig.water = null
        assertEquals(false, rig.fix(80.0))
        rig.water = true
        assertEquals(true, rig.fix(100.0))
    }

    @Test
    fun `inside the home circle the exit rule owns the start`() {
        val rig = Rig()
        rig.fix(-2_000.0)
        assertEquals(false, rig.fix(-1_940.0))
        assertEquals(0, rig.waterAsked)
    }

    @Test
    fun `a spike is no move - too fast from the previous fix`() {
        val rig = Rig()
        rig.fix(0.0)
        // 500 m in 10 s: about 97 kn.
        assertEquals(false, rig.fix(500.0))
    }

    @Test
    fun `a fix without a speed is no move`() {
        val rig = Rig()
        rig.fix(0.0)
        assertEquals(false, rig.fix(60.0, speed = null))
        assertEquals(true, rig.fix(80.0))
    }

    @Test
    fun `demo samples bypass the spike guard`() {
        val rig = Rig()
        rig.fix(0.0, demo = true)
        assertEquals(true, rig.fix(500.0, speed = null, demo = true, after = 1_000L))
    }

    @Test
    fun `switched off for the mode it starts nothing and keeps its reference`() {
        val rig = Rig()
        rig.allowed = false
        rig.fix(0.0)
        assertEquals(false, rig.fix(60.0))
        rig.allowed = true
        assertEquals(true, rig.fix(70.0))
    }

    @Test
    fun `one start, then a fresh reference`() {
        val rig = Rig()
        rig.fix(0.0)
        assertEquals(true, rig.fix(60.0))
        assertEquals("the reference is taken again", false, rig.fix(80.0))
        assertEquals(false, rig.fix(120.0))
        assertEquals(true, rig.fix(140.0))
    }

    // ── A rest moves the reference ──────────────────────────────────────────

    @Test
    fun `after a rest the distance counts from where the boat rested`() {
        val rig = Rig()
        rig.fix(0.0)
        rig.fix(40.0)
        // A minute without a fix, 10 m on: a rest at 40 m — the reference moves there.
        assertEquals(false, rig.fix(50.0, after = 60_000L))
        assertEquals(false, rig.fix(80.0))
        assertEquals(true, rig.fix(95.0))
    }

    @Test
    fun `a boat swinging at anchor that keeps sending fixes rests all the same`() {
        val rig = Rig()
        rig.detector.onRecordingStarted()
        rig.observe(0.0)
        rig.detector.onRecordingStopped(outsideHome = true)
        // Standby fixes every 20 s, all within 15 m of where the quiet period began: a rest at 45 s.
        rig.fix(8.0, speed = 0.2f, after = 20_000L)
        rig.fix(-6.0, speed = 0.2f, after = 20_000L)
        rig.fix(10.0, speed = 0.2f, after = 20_000L)
        // Then the boat leaves: 50 m from the anchor point.
        assertEquals(false, rig.fix(30.0))
        assertEquals(true, rig.fix(55.0))
    }

    // ── A stop on water ─────────────────────────────────────────────────────

    @Test
    fun `a recording stopped on water does not restart while the boat keeps moving`() {
        val rig = Rig()
        rig.detector.onRecordingStarted()
        rig.observe(0.0)
        rig.observe(20.0)
        rig.detector.onRecordingStopped(outsideHome = true)
        assertEquals(false, rig.fix(40.0))
        assertEquals(false, rig.fix(100.0))
        assertEquals(false, rig.fix(200.0))
    }

    @Test
    fun `a stop on water then a rest and a departure starts a new trip`() {
        val rig = Rig()
        rig.detector.onRecordingStarted()
        rig.observe(0.0)
        rig.detector.onRecordingStopped(outsideHome = true)
        // Lunch at anchor: no fix for ten minutes, then the boat moves off.
        assertEquals("the rest re-arms it", false, rig.fix(10.0, after = 600_000L))
        assertEquals(false, rig.fix(30.0))
        assertEquals(true, rig.fix(55.0))
    }

    @Test
    fun `a recording ended in the home port leaves the rule armed`() {
        val rig = Rig()
        rig.detector.onRecordingStarted()
        rig.observe(0.0)
        rig.detector.onRecordingStopped(outsideHome = false)
        rig.fix(10.0)
        rig.fix(40.0)
        assertEquals(true, rig.fix(70.0))
    }
}
