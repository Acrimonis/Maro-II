package ykws.android.maro.data.track

import kotlin.math.cos
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.track.AutoStartDetector.Decision
import ykws.android.maro.data.track.AutoStartDetector.Fix
import ykws.android.maro.data.track.AutoStartDetector.Home
import ykws.android.maro.data.track.AutoStartDetector.Params

/**
 * The forgotten-start rule on a hand-driven monotonic clock: fixes 5 s and about 10 m apart unless a test
 * says otherwise, the home circle 150 m wide, the idle detection at 45 s / 15 m, off and rest at 5 minutes.
 */
class AutoStartDetectorTest {

    private val homeLat = 43.5696
    private val homeLon = 7.1313
    private val home = Home(LatLng(homeLat, homeLon), 150.0)
    private val minute = 60_000L
    private val step = 5_000L
    private val start = 1_000_000L

    private val metresPerDegree = 6_371_000.0 * Math.PI / 180.0

    /** A point [northM] north and [eastM] east of the home origin. */
    private fun at(northM: Double, eastM: Double = 0.0) = LatLng(
        homeLat + northM / metresPerDegree,
        homeLon + eastM / (metresPerDegree * cos(Math.toRadians(homeLat)))
    )

    private val defaults = Params(
        offMs = 5 * minute,
        restMs = 5 * minute,
        idleTimeMs = 45_000L,
        idleDistanceM = 15.0,
        standbyIntervalMs = 5_000L
    )

    private inner class Rig(var params: Params = defaults) {
        val detector = AutoStartDetector(startedAtMonoMs = start)
        var now = start
        var water: Boolean? = true
        var orphans = false
        var lastStop: Long? = null
        var waterAsked = 0
        var orphansAsked = 0

        /** Feed a fix [after] ms after the previous one while recording is off. */
        fun fix(
            north: Double,
            east: Double = 0.0,
            speed: Float? = 2f,
            accuracy: Float? = 5f,
            lock: Boolean = true,
            demo: Boolean = false,
            after: Long = step
        ): Decision? {
            now += after
            return runBlocking {
                detector.onSample(
                    Fix(at(north, east), now, accuracy, speed, lock, demo),
                    params, home, lastStop,
                    { orphansAsked++; orphans },
                    { waterAsked++; water }
                )
            }
        }

        /** Feed a fix while a recording runs. */
        fun observe(north: Double, speed: Float? = 2f, accuracy: Float? = 5f, after: Long = step) {
            now += after
            detector.observe(Fix(at(north), now, accuracy, speed, hasLock = true, isDemo = false), params, home)
        }

        /** The rested start every happy-path test shares: a still fix at the berth, then two 10 m moves. */
        fun berthThenDepart(): Decision? {
            assertNull(fix(0.0, speed = 0f))
            assertNull(fix(10.0))
            return fix(20.0)
        }

        /** The mono time of the third fix of [berthThenDepart], which is the one that completes the rule. */
        val completingAt: Long get() = now + 3 * step
    }

    // ── 1 · all conditions ──────────────────────────────────────────────────

    @Test
    fun `every condition met starts on that fix`() {
        val rig = Rig()
        assertEquals(Decision(fromHome = true), rig.berthThenDepart())
    }

    // ── 2 · each one missing ────────────────────────────────────────────────

    @Test
    fun `a boat latched but wholly outside the circle starts nothing`() {
        val rig = Rig()
        assertNull(rig.fix(140.0, speed = 0f, accuracy = 30f)) // rested at the edge, latched
        // A minute of silence ages that fix out of the window; the next three sit outside the edge but
        // within their own accuracy, so the latch holds while no fix of the window is inside. They slide
        // 40 m sideways, past the widened 30 m threshold: only the inside condition refuses.
        assertNull(rig.fix(160.0, accuracy = 30f, after = minute))
        assertNull(rig.fix(160.0, east = 20.0, accuracy = 30f))
        assertNull(rig.fix(160.0, east = 40.0, accuracy = 30f))
    }

    @Test
    fun `no latch starts nothing`() {
        val rig = Rig()
        assertNull(rig.fix(0.0)) // the first fix is already moving: not rested
        assertNull(rig.fix(10.0))
        assertNull(rig.fix(20.0))
        assertNull(rig.fix(30.0))
    }

    @Test
    fun `stopped four minutes ago starts nothing and six minutes ago starts`() {
        val tooSoon = Rig().apply { lastStop = completingAt - 4 * minute }
        assertNull(tooSoon.berthThenDepart())

        val longEnough = Rig().apply { lastStop = completingAt - 6 * minute }
        assertNotNull(longEnough.berthThenDepart())
    }

    @Test
    fun `exactly the off time is enough`() {
        val rig = Rig().apply { lastStop = completingAt - 5 * minute }
        assertNotNull(rig.berthThenDepart())
    }

    @Test
    fun `exactly the rest time latches and a millisecond less does not`() {
        for ((gap, starts) in listOf(295_000L to true, 294_999L to false)) {
            val rig = Rig()
            rig.fix(400.0, speed = 3f)
            rig.fix(300.0, speed = 3f)
            rig.fix(200.0, speed = 3f)
            rig.fix(100.0, speed = 3f) // the last movement
            rig.fix(80.0, speed = 0f) // five seconds on, so the departure fix is 300 s after it with gap 295 s
            rig.fix(90.0, after = gap)
            rig.fix(100.0)
            assertEquals("gap $gap", starts, rig.fix(110.0) != null)
        }
    }

    @Test
    fun `too little movement starts nothing`() {
        val rig = Rig()
        assertNull(rig.fix(0.0, speed = 0f))
        assertNull(rig.fix(5.0))
        assertNull(rig.fix(10.0)) // 10 m from the oldest fix, 15 needed
    }

    @Test
    fun `water false or unknown starts nothing`() {
        val land = Rig().apply { water = false }
        assertNull(land.berthThenDepart())

        val unknown = Rig().apply { water = null }
        assertNull(unknown.berthThenDepart())
    }

    @Test
    fun `a recovery pending starts nothing`() {
        val rig = Rig().apply { orphans = true }
        assertNull(rig.berthThenDepart())
    }

    @Test
    fun `water and recovery are asked only once the other four conditions pass`() {
        val rig = Rig()
        rig.fix(0.0, speed = 0f)
        rig.fix(10.0)
        assertEquals(0, rig.waterAsked)
        assertEquals(0, rig.orphansAsked)
        rig.fix(20.0)
        assertEquals(1, rig.waterAsked)
        assertEquals(1, rig.orphansAsked)

        val onLand = Rig().apply { water = false }
        onLand.berthThenDepart()
        assertEquals("recovery is not asked when the water refuses", 0, onLand.orphansAsked)
    }

    @Test
    fun `a car leaving the port starts nothing`() {
        val rig = Rig().apply { water = false }
        assertNull(rig.fix(0.0, speed = 0f))
        assertNull(rig.fix(60.0, speed = 14f, after = step))
        assertNull(rig.fix(120.0, speed = 14f))
    }

    // ── 3 · the latch ───────────────────────────────────────────────────────

    @Test
    fun `a recording stopped at the berth then hours of silence then a departure starts`() {
        val rig = Rig()
        // Arriving under recording: moving fixes inside, then the boat is still at the berth.
        rig.observe(100.0)
        rig.observe(90.0)
        rig.observe(80.0, speed = 0f)
        // Hours with no fix; the first departure fix sees the gap and latches.
        assertNull(rig.fix(90.0, after = 3 * 60 * minute))
        assertNull(rig.fix(100.0))
        assertNotNull(rig.fix(110.0))
    }

    @Test
    fun `tracking off on the return then silence then a departure starts`() {
        val rig = Rig()
        assertNull(rig.fix(400.0, speed = 3f)) // coming in from the sea
        assertNull(rig.fix(300.0, speed = 3f))
        assertNull(rig.fix(200.0, speed = 3f))
        assertNull(rig.fix(100.0, speed = 3f))
        assertNull(rig.fix(80.0, speed = 0f)) // berthed
        assertNull(rig.fix(90.0, after = 3 * 60 * minute))
        assertNull(rig.fix(100.0))
        assertNotNull(rig.fix(110.0))
    }

    @Test
    fun `motoring in and berthing without a pause starts nothing`() {
        val rig = Rig()
        rig.fix(400.0, speed = 3f)
        rig.fix(300.0, speed = 3f)
        rig.fix(200.0, speed = 3f)
        rig.fix(100.0, speed = 3f)
        rig.fix(80.0, speed = 0f)
        // Off again a minute later: the pause was shorter than the rest.
        assertNull(rig.fix(90.0, after = minute))
        assertNull(rig.fix(100.0))
        assertNull(rig.fix(110.0))
        assertNull(rig.fix(120.0))
    }

    @Test
    fun `a pause of the rest time latches and the departure after it starts`() {
        val rig = Rig()
        rig.fix(400.0, speed = 3f)
        rig.fix(300.0, speed = 3f)
        rig.fix(200.0, speed = 3f)
        rig.fix(100.0, speed = 3f) // the last movement
        rig.fix(80.0, speed = 0f)
        assertNull(rig.fix(90.0, after = 5 * minute))
        assertNull(rig.fix(100.0))
        assertNotNull(rig.fix(110.0))
    }

    @Test
    fun `a first fix inside while moving does not latch`() {
        val rig = Rig()
        assertNull(rig.fix(50.0, speed = 2f))
        assertNull(rig.fix(60.0))
        assertNull(rig.fix(70.0))
        assertNull(rig.fix(80.0))
    }

    @Test
    fun `the latch clears on leaving and a re-entry while moving does not set it`() {
        val rig = Rig()
        assertNull(rig.fix(0.0, speed = 0f)) // latched
        assertNull(rig.fix(400.0, speed = 3f, after = 20_000L)) // far out: cleared
        assertNull(rig.fix(300.0, speed = 3f))
        assertNull(rig.fix(100.0, speed = 3f))
        assertNull(rig.fix(90.0, speed = 3f))
        assertNull(rig.fix(80.0, speed = 3f))
        assertNull(rig.fix(70.0, speed = 3f))
    }

    @Test
    fun `one jitter fix past the edge within its accuracy keeps the latch`() {
        val kept = Rig()
        assertNull(kept.fix(140.0, speed = 0f)) // latched
        assertNull(kept.fix(155.0, accuracy = 10f)) // 5 m past the edge, inside its own 10 m
        assertNull(kept.fix(145.0))
        assertNotNull(kept.fix(135.0))

        val cleared = Rig()
        assertNull(cleared.fix(140.0, speed = 0f))
        assertNull(cleared.fix(170.0, accuracy = 10f)) // 20 m past, outside its accuracy
        assertNull(cleared.fix(150.0))
        assertNull(cleared.fix(140.0))
        assertNull(cleared.fix(130.0))
    }

    @Test
    fun `a no-lock fix neither sets nor clears the latch`() {
        val doesNotSet = Rig()
        assertNull(doesNotSet.fix(0.0, speed = 0f, lock = false)) // ignored: not the first accepted fix
        assertNull(doesNotSet.fix(10.0)) // the first accepted fix is moving
        assertNull(doesNotSet.fix(20.0))
        assertNull(doesNotSet.fix(30.0))

        val doesNotClear = Rig()
        assertNull(doesNotClear.fix(0.0, speed = 0f))
        assertNull(doesNotClear.fix(400.0, speed = 0f, lock = false)) // a re-emit far away
        assertNull(doesNotClear.fix(10.0))
        assertNotNull(doesNotClear.fix(20.0))
    }

    // ── 4 · the edge ────────────────────────────────────────────────────────

    @Test
    fun `moving off 35 m inside the edge starts at 2, 4 and 6 knots`() {
        for (knots in listOf(2.0, 4.0, 6.0)) {
            val mps = knots * 0.514444
            val rig = Rig()
            assertNull(rig.fix(115.0, speed = 0f))
            var north = 115.0
            var started = false
            repeat(10) {
                north += mps * step / 1000.0
                if (!started && rig.fix(north, speed = mps.toFloat()) != null) started = true
            }
            assertEquals("$knots kn starts within ten fixes", true, started)
        }
    }

    // ── 5 · movement ────────────────────────────────────────────────────────

    @Test
    fun `a 90 degree turn passes`() {
        val rig = Rig()
        assertNull(rig.fix(0.0, speed = 0f))
        assertNull(rig.fix(0.0, east = 12.0))
        assertNotNull(rig.fix(12.0, east = 12.0)) // 17 m from the first fix as the crow flies
    }

    @Test
    fun `demo samples count`() {
        val rig = Rig()
        assertNull(rig.fix(0.0, speed = 0f, lock = false, demo = true))
        assertNull(rig.fix(10.0, lock = false, demo = true))
        assertNotNull(rig.fix(20.0, lock = false, demo = true))
    }

    @Test
    fun `a Doppler speed under the floor on the completing or the previous fix refuses`() {
        val onCompleting = Rig()
        onCompleting.fix(0.0, speed = 0f)
        onCompleting.fix(10.0)
        assertNull(onCompleting.fix(20.0, speed = 0.3f))

        val onPrevious = Rig()
        onPrevious.fix(0.0, speed = 0f)
        onPrevious.fix(10.0, speed = 0.3f)
        assertNull(onPrevious.fix(20.0))
    }

    @Test
    fun `an accuracy over 20 m raises the threshold`() {
        val rig = Rig()
        assertNull(rig.fix(0.0, speed = 0f, accuracy = 25f))
        assertNull(rig.fix(10.0, accuracy = 25f))
        assertNull(rig.fix(20.0, accuracy = 25f)) // 20 m is under the widened 25 m
        assertNotNull(rig.fix(30.0, accuracy = 25f))
    }

    @Test
    fun `old fixes drop out of the window and a lone retained fix is not movement`() {
        val rig = Rig()
        assertNull(rig.fix(0.0, speed = 0f)) // latched
        // A minute later the first fix is older than the 45 s window: only this one is retained.
        assertNull(rig.fix(40.0, after = minute))
    }

    @Test
    fun `the window floor of three standby fixes lets 30 m in 10 s start at 4 knots and not at 3`() {
        val tight = defaults.copy(idleTimeMs = 10_000L, idleDistanceM = 30.0)

        val fourKnots = Rig(tight)
        fourKnots.fix(0.0, speed = 0f)
        var started = false
        var north = 0.0
        repeat(8) {
            north += 4 * 0.514444 * step / 1000.0
            if (fourKnots.fix(north, speed = (4 * 0.514444).toFloat()) != null) started = true
        }
        assertEquals(true, started)

        val threeKnots = Rig(tight)
        threeKnots.fix(0.0, speed = 0f)
        north = 0.0
        repeat(8) {
            north += 3 * 0.514444 * step / 1000.0
            assertNull(threeKnots.fix(north, speed = (3 * 0.514444).toFloat()))
        }
    }

    // ── 6 · parameters ──────────────────────────────────────────────────────

    @Test
    fun `parameters changed between fixes apply on the next`() {
        val rig = Rig()
        rig.fix(0.0, speed = 0f)
        rig.fix(10.0)
        rig.params = defaults.copy(idleDistanceM = 25.0)
        assertNull(rig.fix(20.0)) // 20 m under a 25 m setting
        rig.params = defaults
        assertNotNull(rig.fix(30.0))
    }

    @Test
    fun `with no home circle nothing starts`() {
        val rig = Rig()
        rig.fix(0.0, speed = 0f)
        val off = runBlocking {
            rig.detector.onSample(
                Fix(at(10.0), rig.now + step, 5f, 2f, hasLock = true, isDemo = false),
                defaults, home = null, lastStopMono = null, orphansPending = { false }, waterAt = { true }
            )
        }
        assertNull(off)
    }
}
