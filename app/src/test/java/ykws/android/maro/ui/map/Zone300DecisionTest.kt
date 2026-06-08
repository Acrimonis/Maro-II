package ykws.android.maro.ui.map

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for the pure 300 m zone proximity auto-reveal decision ([zone300Decision]).
 *
 * Convention: `dist` is the signed distance to the 300 m band edge (+ outside, − inside).
 * Defaults mirror the shipped config: reveal at 200 m or 20 s, regulatory limit 5 kn.
 */
class Zone300DecisionTest {

    private fun decide(
        dist: Double?,
        prevDist: Double?,
        inZone: Boolean = false,
        sogKn: Float? = null,
        armed: Boolean = true,
        autoRevealed: Boolean = false,
        bandEntered: Boolean = false,
        revealDistM: Double = 200.0,
        revealTimeS: Double = 20.0,
        regKn: Double = 5.0
    ) = zone300Decision(
        dist, prevDist, inZone, sogKn, armed, autoRevealed, bandEntered, revealDistM, revealTimeS, regKn
    )

    // ── Reveal arm ──────────────────────────────────────────────────────────

    @Test
    fun `distance arm reveals when armed and closing within 200 m`() {
        val d = decide(dist = 180.0, prevDist = 190.0)
        assertEquals(Zone300Action.REVEAL, d.action)
        assertTrue(d.autoRevealed)
        assertFalse(d.bandEntered)
    }

    @Test
    fun `time arm reveals a fast boat still beyond the distance margin`() {
        // 30 kn ≈ 15.4 m/s → 300 m is ~19.4 s away ≤ 20 s, even though 300 m > 200 m.
        val d = decide(dist = 300.0, prevDist = 320.0, sogKn = 30f)
        assertEquals(Zone300Action.REVEAL, d.action)
    }

    @Test
    fun `same approach without speed does not reveal beyond the distance margin`() {
        val d = decide(dist = 300.0, prevDist = 320.0, sogKn = null)
        assertEquals(Zone300Action.NONE, d.action)
        assertFalse(d.autoRevealed)
    }

    @Test
    fun `no reveal when stationary or parallel even within the margin`() {
        assertEquals(Zone300Action.NONE, decide(dist = 150.0, prevDist = 150.0, sogKn = 10f).action) // stationary
        assertEquals(Zone300Action.NONE, decide(dist = 150.0, prevDist = 140.0, sogKn = 10f).action) // moving away
    }

    @Test
    fun `no reveal when disarmed`() {
        val d = decide(dist = 100.0, prevDist = 150.0, armed = false)
        assertEquals(Zone300Action.NONE, d.action)
    }

    @Test
    fun `first sample with no previous distance does not reveal`() {
        assertEquals(Zone300Action.NONE, decide(dist = 100.0, prevDist = null).action)
    }

    @Test
    fun `distance arm reveals when SOG is unknown (null)`() {
        assertEquals(Zone300Action.REVEAL, decide(dist = 180.0, prevDist = 190.0, sogKn = null).action)
    }

    @Test
    fun `a stopped boat does not reveal even when closing within the margin`() {
        assertEquals(Zone300Action.NONE, decide(dist = 180.0, prevDist = 190.0, sogKn = 0f).action)
    }

    // ── Re-hide arm ─────────────────────────────────────────────────────────

    @Test
    fun `compliant inside the band hides`() {
        val d = decide(dist = -50.0, prevDist = -40.0, inZone = true, sogKn = 4f, autoRevealed = true, bandEntered = true)
        assertEquals(Zone300Action.HIDE, d.action)
        assertFalse(d.autoRevealed)
        assertFalse(d.bandEntered)
    }

    @Test
    fun `non-compliant inside the band stays revealed (no flap)`() {
        val d = decide(dist = -50.0, prevDist = -40.0, inZone = true, sogKn = 12f, autoRevealed = true, bandEntered = true)
        assertEquals(Zone300Action.NONE, d.action)
        assertTrue(d.autoRevealed)
    }

    @Test
    fun `unknown speed (null SOG) cannot trigger the compliance hide`() {
        // GPS mode before a fix: null SOG must not be read as compliant.
        val d = decide(dist = -50.0, prevDist = -40.0, inZone = true, sogKn = null, autoRevealed = true, bandEntered = true)
        assertEquals(Zone300Action.NONE, d.action)
    }

    @Test
    fun `stationary inside the band (0 kn) hides on compliance`() {
        // Demo feeds 0 kn when the map is not being panned → compliant inside → hide.
        val d = decide(dist = -50.0, prevDist = -50.0, inZone = true, sogKn = 0f, autoRevealed = true, bandEntered = true)
        assertEquals(Zone300Action.HIDE, d.action)
    }

    @Test
    fun `a stopped boat auto-hides even outside the zone`() {
        // Stopped (≤ 1 kn) anywhere clears the alert, even seaward of the band.
        val d = decide(dist = 120.0, prevDist = 120.0, sogKn = 0f, autoRevealed = true)
        assertEquals(Zone300Action.HIDE, d.action)
    }

    @Test
    fun `exited the band seaward hides`() {
        val d = decide(dist = 20.0, prevDist = 10.0, autoRevealed = true, bandEntered = true)
        assertEquals(Zone300Action.HIDE, d.action)
    }

    @Test
    fun `retreated past the margin without entering hides`() {
        val d = decide(dist = 250.0, prevDist = 240.0, autoRevealed = true, bandEntered = false)
        assertEquals(Zone300Action.HIDE, d.action)
    }

    @Test
    fun `near-miss still inside the margin stays revealed until past it`() {
        val d = decide(dist = 150.0, prevDist = 140.0, autoRevealed = true, bandEntered = false)
        assertEquals(Zone300Action.NONE, d.action)
        assertTrue(d.autoRevealed)
    }

    @Test
    fun `crossing into the band while revealed records entry`() {
        val d = decide(dist = -10.0, prevDist = 10.0, inZone = true, sogKn = 12f, autoRevealed = true, bandEntered = false)
        assertEquals(Zone300Action.NONE, d.action)
        assertTrue(d.bandEntered)
    }

    @Test
    fun `re-approaching after an auto-hide reveals again because still armed`() {
        // After a hide the band is hidden again but armed stays true → the next approach re-reveals.
        val d = decide(dist = 180.0, prevDist = 190.0, armed = true, autoRevealed = false, bandEntered = false)
        assertEquals(Zone300Action.REVEAL, d.action)
    }
}
