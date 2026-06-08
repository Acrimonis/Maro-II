package ykws.android.maro.data.location

import org.junit.Assert.assertEquals
import org.junit.Test
import ykws.android.maro.data.model.LatLng

/**
 * Unit tests for [AdaptiveGpsPolicy].
 *
 * Positions are built from a metres-north offset off a fixed base so displacement ≈ the offset
 * (1° latitude ≈ 111 195 m here). Window = 30 s, threshold = 20 m throughout.
 */
class AdaptiveGpsPolicyTest {

    private val window = 30_000L
    private val threshold = 20.0

    /** A point [metresNorth] north of the base — haversine(p(0), p(d)) ≈ d metres. */
    private fun p(metresNorth: Double) =
        LatLng(43.5 + metresNorth / 111_194.9, 7.0)

    @Test
    fun `first fix is ACTIVE`() {
        val policy = AdaptiveGpsPolicy()
        assertEquals(AcquisitionMode.ACTIVE, policy.onFix(0, p(0.0), 0f, window, threshold))
    }

    @Test
    fun `stays ACTIVE until the full quiet window elapses, then IDLE`() {
        val policy = AdaptiveGpsPolicy()
        policy.onFix(0, p(0.0), 0f, window, threshold)                         // anchor @ t=0
        assertEquals(AcquisitionMode.ACTIVE, policy.onFix(10_000, p(2.0), 0f, window, threshold))
        assertEquals(AcquisitionMode.ACTIVE, policy.onFix(29_000, p(3.0), 0f, window, threshold))
        // 30 s of staying within 20 m of the anchor → drop to idle.
        assertEquals(AcquisitionMode.IDLE, policy.onFix(30_000, p(4.0), 0f, window, threshold))
    }

    @Test
    fun `speed above wake threshold immediately wakes from IDLE`() {
        val policy = AdaptiveGpsPolicy()
        policy.onFix(0, p(0.0), 0f, window, threshold)
        assertEquals(AcquisitionMode.IDLE, policy.onFix(30_000, p(1.0), 0f, window, threshold))
        // A fix faster than 0.8 m/s → ACTIVE at once.
        assertEquals(AcquisitionMode.ACTIVE, policy.onFix(36_000, p(2.0), 1.0f, window, threshold))
    }

    @Test
    fun `a jump beyond threshold between fixes wakes from IDLE`() {
        val policy = AdaptiveGpsPolicy()
        policy.onFix(0, p(0.0), 0f, window, threshold)
        assertEquals(AcquisitionMode.IDLE, policy.onFix(30_000, p(1.0), 0f, window, threshold))
        // p(1) → p(35) is ~34 m ≥ 20 m → ACTIVE.
        assertEquals(AcquisitionMode.ACTIVE, policy.onFix(36_000, p(35.0), 0f, window, threshold))
    }

    @Test
    fun `slow drift beyond the radius re-anchors and stays ACTIVE past the window`() {
        val policy = AdaptiveGpsPolicy()
        policy.onFix(0, p(0.0), 0f, window, threshold)                         // anchor @ t=0
        policy.onFix(10_000, p(15.0), 0f, window, threshold)                   // 15 m from anchor (< 20)
        // 25 m from the original anchor (jump of 10 m, so no per-fix wake) → re-anchor @ t=20s.
        assertEquals(AcquisitionMode.ACTIVE, policy.onFix(20_000, p(25.0), 0f, window, threshold))
        // 20 s later, still only 5 m from the NEW anchor, but the window restarted at 20s → ACTIVE.
        assertEquals(AcquisitionMode.ACTIVE, policy.onFix(40_000, p(30.0), 0f, window, threshold))
    }

    @Test
    fun `reset clears state so the next fix re-anchors`() {
        val policy = AdaptiveGpsPolicy()
        policy.onFix(0, p(0.0), 0f, window, threshold)
        assertEquals(AcquisitionMode.IDLE, policy.onFix(30_000, p(1.0), 0f, window, threshold))
        policy.reset()
        // First fix after reset is treated as a fresh anchor → ACTIVE even at a late clock.
        assertEquals(AcquisitionMode.ACTIVE, policy.onFix(60_000, p(1.0), 0f, window, threshold))
    }
}
