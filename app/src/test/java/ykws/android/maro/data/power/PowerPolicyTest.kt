package ykws.android.maro.data.power

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [PowerPolicy].
 *
 * The policy is framework-free and stateless, so each case is a single [PowerPolicy.evaluate] call
 * with an explicit [PowerInputs] snapshot. Clocks are expressed as ages, exactly as the production
 * caller passes them.
 */
class PowerPolicyTest {

    private val policy = PowerPolicy()

    // Local values on purpose: the policy takes every number as an input, so the test does not
    // depend on AppConfig (or on the app's properties file) at all.
    private val threshold = 1.0f
    private val graceMs = 15 * 60_000L          // 15 minutes
    private val staleMs = 30_000L               // mirrors power.screen.stalenessBoundMs

    private fun inputs(
        keepScreenOn: Boolean = true,
        movementGateEnabled: Boolean = true,
        speedKn: Float? = 5f,
        fixAgeMs: Long = 0L,
        lastTouchAgeMs: Long = Long.MAX_VALUE,  // no recent interaction
        recording: Boolean = false,
        passiveCaptureEnabled: Boolean = false,
        zoneLatched: Boolean = false,
    ) = PowerInputs(
        keepScreenOn = keepScreenOn,
        movementGateEnabled = movementGateEnabled,
        speedKn = speedKn,
        fixAgeMs = fixAgeMs,
        lastTouchAgeMs = lastTouchAgeMs,
        thresholdKn = threshold,
        graceMs = graceMs,
        stalenessBoundMs = staleMs,
        recording = recording,
        passiveCaptureEnabled = passiveCaptureEnabled,
        zoneLatched = zoneLatched,
    )

    // ── Speed gate ────────────────────────────────────────────────────────────

    @Test
    fun `holds the screen while moving above the threshold`() {
        assertTrue(policy.evaluate(inputs(speedKn = 2.0f)).screenOn)
    }

    @Test
    fun `releases the screen when stopped and no recent interaction`() {
        assertFalse(policy.evaluate(inputs(speedKn = 0.2f)).screenOn)
    }

    @Test
    fun `speed exactly at the threshold does not count as moving`() {
        assertFalse(policy.evaluate(inputs(speedKn = threshold)).screenOn)
    }

    // ── Interaction reset ─────────────────────────────────────────────────────

    @Test
    fun `a recent touch holds the screen even when stopped`() {
        assertTrue(policy.evaluate(inputs(speedKn = 0f, lastTouchAgeMs = 60_000L)).screenOn)
    }

    @Test
    fun `movement holds the screen even with no interaction`() {
        assertTrue(policy.evaluate(inputs(speedKn = 4f, lastTouchAgeMs = Long.MAX_VALUE)).screenOn)
    }

    @Test
    fun `releases once both movement and the touch grace have lapsed`() {
        val state = policy.evaluate(
            inputs(speedKn = 0f, lastTouchAgeMs = graceMs + 1)
        )
        assertFalse(state.screenOn)
    }

    @Test
    fun `touch exactly at the grace boundary still holds`() {
        assertTrue(policy.evaluate(inputs(speedKn = 0f, lastTouchAgeMs = graceMs)).screenOn)
    }

    // ── Unknown versus lost speed ─────────────────────────────────────────────

    @Test
    fun `no fix yet holds the screen`() {
        assertTrue(policy.evaluate(inputs(speedKn = null, fixAgeMs = 0L)).screenOn)
    }

    @Test
    fun `a fix older than the staleness bound releases the screen`() {
        val state = policy.evaluate(
            inputs(speedKn = 8f, fixAgeMs = staleMs + 1)
        )
        assertFalse(state.screenOn)
    }

    @Test
    fun `a fix exactly at the staleness bound is still trusted`() {
        assertTrue(policy.evaluate(inputs(speedKn = 8f, fixAgeMs = staleMs)).screenOn)
    }

    // ── Master toggle and additive gate semantics ─────────────────────────────

    @Test
    fun `master toggle off never holds the screen`() {
        assertFalse(policy.evaluate(inputs(keepScreenOn = false, speedKn = 6f)).screenOn)
    }

    @Test
    fun `gate disabled holds the screen while in front regardless of speed`() {
        val state = policy.evaluate(
            inputs(movementGateEnabled = false, speedKn = 0f, lastTouchAgeMs = Long.MAX_VALUE)
        )
        assertTrue(state.screenOn)
    }

    @Test
    fun `gate disabled still respects the master toggle`() {
        val state = policy.evaluate(
            inputs(keepScreenOn = false, movementGateEnabled = false)
        )
        assertFalse(state.screenOn)
    }

    // ── Keep-alive channel ────────────────────────────────────────────────────

    @Test
    fun `recording is the keep-alive floor`() {
        val state = policy.evaluate(inputs(recording = true, speedKn = 0f))
        assertTrue(state.keepAlive)
        assertFalse(state.screenOn)             // the floor never forces the screen on
    }

    @Test
    fun `no reason means no keep-alive`() {
        assertFalse(policy.evaluate(inputs(recording = false)).keepAlive)
    }

    @Test
    fun `reserved reasons raise keep-alive independently`() {
        assertTrue(policy.evaluate(inputs(passiveCaptureEnabled = true)).keepAlive)
        assertTrue(policy.evaluate(inputs(zoneLatched = true)).keepAlive)
    }
}
