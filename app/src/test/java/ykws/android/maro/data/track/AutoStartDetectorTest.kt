package ykws.android.maro.data.track

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.track.AutoStartDetector.Fix
import ykws.android.maro.data.track.AutoStartDetector.Home

/**
 * The home-port exit rule on a hand-driven monotonic clock: armed inside the 150 m home circle, fired by a
 * fix farther outside than its own accuracy while recording is off, past the spike guard (Doppler at least
 * 0.5 m/s, implied speed at most 32 kn), on water and with no recovery pending. Fixes are a minute apart
 * and report 2 m/s unless a test says otherwise.
 */
class AutoStartDetectorTest {

    private val homeLat = 43.5696
    private val homeLon = 7.1313
    private val home = Home(LatLng(homeLat, homeLon), 150.0)
    private val metresPerDegree = 6_371_000.0 * Math.PI / 180.0
    private val maxSpeedMps = 32.0 * 0.514444
    private val minute = 60_000L

    private fun at(northM: Double) = LatLng(homeLat + northM / metresPerDegree, homeLon)

    private inner class Rig {
        val detector = AutoStartDetector(maxSpeedMps)
        var now = 1_000_000L
        var water: Boolean? = true
        var orphans = false
        var waterAsked = 0
        var orphansAsked = 0

        /** A fix [north] metres north of the origin, [after] ms after the previous one, while recording is off. */
        fun fix(
            north: Double,
            speed: Float? = 2f,
            accuracy: Float? = 5f,
            lock: Boolean = true,
            demo: Boolean = false,
            after: Long = minute,
            home: Home? = this@AutoStartDetectorTest.home
        ): Boolean {
            now += after
            return runBlocking {
                detector.onSample(
                    Fix(at(north), now, accuracy, speed, lock, demo),
                    home,
                    { orphansAsked++; orphans },
                    { waterAsked++; water }
                )
            }
        }

        /** A fix while a recording runs. */
        fun observe(north: Double, after: Long = minute) {
            now += after
            detector.observe(Fix(at(north), now, 5f, 2f, hasLock = true, isDemo = false), home)
        }
    }

    // ── The exit ────────────────────────────────────────────────────────────

    @Test
    fun `leaving the circle on water after a fix inside starts`() {
        val rig = Rig()
        assertEquals(false, rig.fix(0.0))
        assertEquals(false, rig.fix(100.0))
        assertEquals(true, rig.fix(200.0))
    }

    @Test
    fun `no wait - a single fix inside then one outside is enough`() {
        val rig = Rig()
        assertEquals(false, rig.fix(140.0))
        assertEquals(true, rig.fix(160.0, after = 5_000L))
    }

    @Test
    fun `a boat never seen inside starts nothing`() {
        val rig = Rig()
        assertEquals(false, rig.fix(300.0))
        assertEquals(false, rig.fix(400.0))
        assertEquals(0, rig.waterAsked)
    }

    @Test
    fun `a fix past the edge within its own accuracy neither starts nor disarms`() {
        val rig = Rig()
        rig.fix(100.0)
        assertEquals(false, rig.fix(160.0, accuracy = 20f))
        assertEquals(true, rig.fix(180.0, accuracy = 20f))
    }

    @Test
    fun `a fix without accuracy counts as soon as it is past the edge`() {
        val rig = Rig()
        rig.fix(100.0)
        assertEquals(true, rig.fix(152.0, accuracy = null))
    }

    @Test
    fun `one exit starts once`() {
        val rig = Rig()
        rig.fix(0.0)
        assertEquals(true, rig.fix(200.0))
        assertEquals(false, rig.fix(300.0))
    }

    // ── The spike guard ─────────────────────────────────────────────────────

    @Test
    fun `a jump out of the berth with no Doppler speed is a spike - the real departure still starts`() {
        val rig = Rig()
        rig.fix(0.0, speed = 0f)
        assertEquals("spike", false, rig.fix(250.0, speed = 0.2f))
        assertEquals("water is not asked of a spike", 0, rig.waterAsked)
        rig.fix(5.0, speed = 0f)
        assertEquals(false, rig.fix(80.0))
        assertEquals(true, rig.fix(200.0))
    }

    @Test
    fun `an exit fix without a speed waits for the next one`() {
        val rig = Rig()
        rig.fix(0.0)
        assertEquals(false, rig.fix(200.0, speed = null))
        assertEquals(true, rig.fix(220.0))
    }

    @Test
    fun `an exit faster than the boat can go from the last fix inside is a spike`() {
        val rig = Rig()
        rig.fix(100.0)
        // 300 m in 10 s is 30 m/s, about 58 kn, over the 32 kn cap.
        assertEquals(false, rig.fix(400.0, after = 10_000L))
        // 400 m in 30 s is 13 m/s, about 26 kn: plausible.
        assertEquals(true, rig.fix(500.0, after = 20_000L))
    }

    @Test
    fun `demo samples bypass the spike guard`() {
        val rig = Rig()
        rig.fix(0.0, lock = false, demo = true)
        assertEquals(true, rig.fix(500.0, speed = null, lock = false, demo = true, after = 1_000L))
    }

    // ── Water and recovery ──────────────────────────────────────────────────

    @Test
    fun `land or an unknown water answer holds the start for the next fix`() {
        val rig = Rig()
        rig.fix(0.0)
        rig.water = false
        assertEquals(false, rig.fix(200.0))
        rig.water = null
        assertEquals(false, rig.fix(250.0))
        rig.water = true
        assertEquals(true, rig.fix(300.0))
    }

    @Test
    fun `a pending recovery holds the start and is asked only after water`() {
        val rig = Rig()
        rig.fix(0.0)
        rig.water = false
        rig.orphans = true
        assertEquals(false, rig.fix(200.0))
        assertEquals(0, rig.orphansAsked)
        rig.water = true
        assertEquals(false, rig.fix(250.0))
        assertEquals(1, rig.orphansAsked)
        rig.orphans = false
        assertEquals(true, rig.fix(300.0))
    }

    @Test
    fun `water is not asked inside the circle`() {
        val rig = Rig()
        rig.fix(0.0)
        rig.fix(100.0)
        assertEquals(0, rig.waterAsked)
    }

    // ── Arming ──────────────────────────────────────────────────────────────

    @Test
    fun `an exit while recording is spent - a trip stopped at sea does not restart`() {
        val rig = Rig()
        rig.observe(0.0)
        rig.observe(300.0)
        assertEquals(false, rig.fix(400.0))
    }

    @Test
    fun `an arrival while recording arms the next departure`() {
        val rig = Rig()
        rig.observe(1_000.0)
        rig.observe(50.0)
        assertEquals(false, rig.fix(60.0))
        assertEquals(true, rig.fix(200.0))
    }

    @Test
    fun `a no-lock fix that is not a demo sample is ignored`() {
        val rig = Rig()
        assertEquals(false, rig.fix(0.0, lock = false))
        assertEquals("never armed", false, rig.fix(200.0))

        val armed = Rig()
        armed.fix(0.0)
        assertEquals(false, armed.fix(200.0, lock = false))
        assertEquals("still armed", true, armed.fix(250.0))
    }

    @Test
    fun `the circle switched off forgets the arming`() {
        val rig = Rig()
        rig.fix(0.0)
        assertEquals(false, rig.fix(200.0, home = null))
        assertEquals(false, rig.fix(250.0))
    }
}
