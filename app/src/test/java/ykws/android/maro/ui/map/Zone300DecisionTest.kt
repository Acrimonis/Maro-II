package ykws.android.maro.ui.map

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for the generalized zone auto-show decision ([zoneAutoShowDecision]).
 *
 * Tests both the 300m band behavior ([ZoneAutoShowConfig.hideOnCompliantInside] = true)
 * and speed zone behavior (hideOnCompliantInside = false) branches of the unified function.
 *
 * Convention: `dist` is the signed distance to the zone boundary (+ outside, − inside).
 * Defaults mirror the shipped config: reveal at 200 m or 20 s, regulatory limit 5 kn.
 */
class Zone300DecisionTest {

    // ── Convenience helpers ──────────────────────────────────────────────

    /** 300m band config (hide on compliant inside, with regulatory speed). */
    private val bandConfig = ZoneAutoShowConfig(
        hideOnCompliantInside = true,
        regulatorySpeedKn = 5.0
    )

    /** Speed zone config (no hide on compliant inside, with hysteresis). */
    private val speedConfig = ZoneAutoShowConfig(
        hideOnCompliantInside = false,
        hysteresisM = 5.0
    )

    private fun bandDecision(
        dist: Double?,
        prevDist: Double?,
        inZone: Boolean = false,
        sogKn: Float? = null,
        armed: Boolean = true,
        autoRevealed: Boolean = false,
        zoneEntered: Boolean = false,
        revealDistM: Double = 200.0,
        revealTimeS: Double = 20.0
    ) = zoneAutoShowDecision(
        dist = dist, prevDist = prevDist, insideZone = inZone,
        sogKn = sogKn, armed = armed, autoRevealed = autoRevealed,
        zoneEntered = zoneEntered,
        revealDistM = revealDistM, revealTimeS = revealTimeS,
        config = bandConfig
    )

    private fun speedDecision(
        dist: Double?,
        prevDist: Double?,
        insideZone: Boolean = false,
        sogKn: Float? = null,
        armed: Boolean = true,
        autoRevealed: Boolean = false,
        zoneEntered: Boolean = false,
        revealDistM: Double = 200.0,
        revealTimeS: Double = 20.0
    ) = zoneAutoShowDecision(
        dist = dist, prevDist = prevDist, insideZone = insideZone,
        sogKn = sogKn, armed = armed, autoRevealed = autoRevealed,
        zoneEntered = zoneEntered,
        revealDistM = revealDistM, revealTimeS = revealTimeS,
        config = speedConfig
    )

    // ═════════════════════════════════════════════════════════════════════
    //  300m BAND BEHAVIOR (hideOnCompliantInside = true)
    // ═════════════════════════════════════════════════════════════════════

    // ── Reveal arm ──────────────────────────────────────────────────────

    @Test
    fun `band distance arm reveals when armed and closing within 200 m`() {
        val d = bandDecision(dist = 180.0, prevDist = 190.0)
        assertEquals(AutoShowAction.REVEAL, d.action)
        assertTrue(d.autoRevealed)
        assertFalse(d.zoneEntered)
    }

    @Test
    fun `band time arm reveals a fast boat still beyond the distance margin`() {
        // 30 kn ≈ 15.4 m/s → 300 m is ~19.4 s away ≤ 20 s, even though 300 m > 200 m.
        val d = bandDecision(dist = 300.0, prevDist = 320.0, sogKn = 30f)
        assertEquals(AutoShowAction.REVEAL, d.action)
    }

    @Test
    fun `band same approach without speed does not reveal beyond the distance margin`() {
        val d = bandDecision(dist = 300.0, prevDist = 320.0, sogKn = null)
        assertEquals(AutoShowAction.NONE, d.action)
        assertFalse(d.autoRevealed)
    }

    @Test
    fun `band no reveal when stationary or parallel even within the margin`() {
        assertEquals(AutoShowAction.NONE, bandDecision(dist = 150.0, prevDist = 150.0, sogKn = 10f).action) // stationary
        assertEquals(AutoShowAction.NONE, bandDecision(dist = 150.0, prevDist = 140.0, sogKn = 10f).action) // moving away
    }

    @Test
    fun `band no reveal when disarmed`() {
        val d = bandDecision(dist = 100.0, prevDist = 150.0, armed = false)
        assertEquals(AutoShowAction.NONE, d.action)
    }

    @Test
    fun `band first sample with no previous distance does not reveal`() {
        assertEquals(AutoShowAction.NONE, bandDecision(dist = 100.0, prevDist = null).action)
    }

    @Test
    fun `band distance arm reveals when SOG is unknown (null)`() {
        assertEquals(AutoShowAction.REVEAL, bandDecision(dist = 180.0, prevDist = 190.0, sogKn = null).action)
    }

    @Test
    fun `band a not-closing boat does not reveal`() {
        assertEquals(AutoShowAction.NONE, bandDecision(dist = 180.0, prevDist = 180.0, sogKn = 0f).action)
    }

    @Test
    fun `band a slow but genuinely closing boat still reveals`() {
        assertEquals(AutoShowAction.REVEAL, bandDecision(dist = 180.0, prevDist = 190.0, sogKn = 0.5f).action)
    }

    @Test
    fun `band does not reveal when already inside (manually hidden)`() {
        assertEquals(AutoShowAction.NONE, bandDecision(dist = -50.0, prevDist = -40.0, sogKn = 5f).action)
    }

    // ── Re-hide arm ─────────────────────────────────────────────────────

    @Test
    fun `band compliant inside the band hides`() {
        val d = bandDecision(dist = -50.0, prevDist = -40.0, inZone = true, sogKn = 4f, autoRevealed = true, zoneEntered = true)
        assertEquals(AutoShowAction.HIDE, d.action)
        assertFalse(d.autoRevealed)
        assertFalse(d.zoneEntered)
    }

    @Test
    fun `band non-compliant inside the band stays revealed (no flap)`() {
        val d = bandDecision(dist = -50.0, prevDist = -40.0, inZone = true, sogKn = 12f, autoRevealed = true, zoneEntered = true)
        assertEquals(AutoShowAction.NONE, d.action)
        assertTrue(d.autoRevealed)
    }

    @Test
    fun `band unknown speed (null SOG) cannot trigger compliance hide`() {
        val d = bandDecision(dist = -50.0, prevDist = -40.0, inZone = true, sogKn = null, autoRevealed = true, zoneEntered = true)
        assertEquals(AutoShowAction.NONE, d.action)
    }

    @Test
    fun `band stationary inside the band (0 kn) hides on compliance`() {
        val d = bandDecision(dist = -50.0, prevDist = -50.0, inZone = true, sogKn = 0f, autoRevealed = true, zoneEntered = true)
        assertEquals(AutoShowAction.HIDE, d.action)
    }

    @Test
    fun `band a stopped boat that is no longer closing auto-hides, even outside`() {
        val d = bandDecision(dist = 120.0, prevDist = 120.0, sogKn = 0f, autoRevealed = true)
        assertEquals(AutoShowAction.HIDE, d.action)
    }

    @Test
    fun `band a low-speed reading while still closing does not hide (no flap)`() {
        val d = bandDecision(dist = 150.0, prevDist = 160.0, sogKn = 0.5f, autoRevealed = true)
        assertEquals(AutoShowAction.NONE, d.action)
        assertTrue(d.autoRevealed)
    }

    @Test
    fun `band exited seaward hides`() {
        val d = bandDecision(dist = 20.0, prevDist = 10.0, autoRevealed = true, zoneEntered = true)
        assertEquals(AutoShowAction.HIDE, d.action)
    }

    @Test
    fun `band retreated past the margin without entering hides`() {
        val d = bandDecision(dist = 250.0, prevDist = 240.0, autoRevealed = true, zoneEntered = false)
        assertEquals(AutoShowAction.HIDE, d.action)
    }

    @Test
    fun `band near-miss still inside the margin stays revealed until past it`() {
        val d = bandDecision(dist = 150.0, prevDist = 140.0, autoRevealed = true, zoneEntered = false)
        assertEquals(AutoShowAction.NONE, d.action)
        assertTrue(d.autoRevealed)
    }

    @Test
    fun `band crossing into the band while revealed records entry`() {
        val d = bandDecision(dist = -10.0, prevDist = 10.0, inZone = true, sogKn = 12f, autoRevealed = true, zoneEntered = false)
        assertEquals(AutoShowAction.NONE, d.action)
        assertTrue(d.zoneEntered)
    }

    @Test
    fun `band re-approaching after auto-hide reveals again because still armed`() {
        val d = bandDecision(dist = 180.0, prevDist = 190.0, armed = true, autoRevealed = false, zoneEntered = false)
        assertEquals(AutoShowAction.REVEAL, d.action)
    }

    // ═════════════════════════════════════════════════════════════════════
    //  SPEED ZONE BEHAVIOR (hideOnCompliantInside = false)
    // ═════════════════════════════════════════════════════════════════════

    // ── Reveal arm ──────────────────────────────────────────────────────

    @Test
    fun `speed zone reveals when approaching within distance margin`() {
        val d = speedDecision(dist = 180.0, prevDist = 200.0)
        assertEquals(AutoShowAction.REVEAL, d.action)
        assertTrue(d.autoRevealed)
    }

    @Test
    fun `speed zone time arm reveals a fast boat beyond distance margin`() {
        val d = speedDecision(dist = 300.0, prevDist = 320.0, sogKn = 30f)
        assertEquals(AutoShowAction.REVEAL, d.action)
    }

    @Test
    fun `speed zone no reveal when moving away`() {
        val d = speedDecision(dist = 200.0, prevDist = 180.0)
        assertEquals(AutoShowAction.NONE, d.action)
    }

    @Test
    fun `speed zone no reveal when disarmed`() {
        val d = speedDecision(dist = 100.0, prevDist = 150.0, armed = false)
        assertEquals(AutoShowAction.NONE, d.action)
    }

    @Test
    fun `speed zone no reveal when already inside`() {
        val d = speedDecision(dist = -50.0, prevDist = -30.0)
        assertEquals(AutoShowAction.NONE, d.action)
    }

    // ── Re-hide arm ─────────────────────────────────────────────────────

    @Test
    fun `speed zone does NOT hide when compliant inside (unlike 300m band)`() {
        // Speed zones are informational — stay visible while navigating through
        val d = speedDecision(dist = -50.0, prevDist = -40.0, insideZone = true, sogKn = 4f, autoRevealed = true)
        assertEquals(AutoShowAction.NONE, d.action)
        assertTrue("should stay revealed while inside", d.autoRevealed)
    }

    @Test
    fun `speed zone does NOT hide when non-compliant inside`() {
        val d = speedDecision(dist = -50.0, prevDist = -40.0, insideZone = true, sogKn = 12f, autoRevealed = true)
        assertEquals(AutoShowAction.NONE, d.action)
        assertTrue(d.autoRevealed)
    }

    @Test
    fun `speed zone stopped and not closing hides`() {
        val d = speedDecision(dist = 120.0, prevDist = 120.0, sogKn = 0f, autoRevealed = true)
        assertEquals(AutoShowAction.HIDE, d.action)
        assertFalse(d.autoRevealed)
    }

    @Test
    fun `speed zone low speed while still closing does not hide (no flap)`() {
        val d = speedDecision(dist = 150.0, prevDist = 160.0, sogKn = 0.5f, autoRevealed = true)
        assertEquals(AutoShowAction.NONE, d.action)
        assertTrue(d.autoRevealed)
    }

    @Test
    fun `speed zone hides when retreated past margin`() {
        val d = speedDecision(dist = 250.0, prevDist = 240.0, autoRevealed = true)
        assertEquals(AutoShowAction.HIDE, d.action)
        assertFalse(d.autoRevealed)
    }

    @Test
    fun `speed zone stays revealed within margin even if moving away`() {
        val d = speedDecision(dist = 150.0, prevDist = 140.0, autoRevealed = true)
        assertEquals(AutoShowAction.NONE, d.action)
        assertTrue(d.autoRevealed)
    }

    @Test
    fun `speed zone zero distance does not flap inside-outside`() {
        // Exactly on the boundary: treat as inside (or within hysteresis)
        val d = speedDecision(dist = 0.0, prevDist = 0.0, insideZone = true, autoRevealed = true)
        assertEquals(AutoShowAction.NONE, d.action)
        assertTrue(d.autoRevealed)
    }

    @Test
    fun `speed zone re-approaching after auto-hide reveals again`() {
        val d = speedDecision(dist = 180.0, prevDist = 190.0, armed = true, autoRevealed = false)
        assertEquals(AutoShowAction.REVEAL, d.action)
    }

    @Test
    fun `speed zone within hysteresis deadband stays visible`() {
        // Just inside the hysteresis band (dist between -hysteresisM and 0)
        val d = speedDecision(dist = -3.0, prevDist = -2.0, insideZone = true, autoRevealed = true)
        assertEquals(AutoShowAction.NONE, d.action)
        assertTrue(d.autoRevealed)
    }
}
