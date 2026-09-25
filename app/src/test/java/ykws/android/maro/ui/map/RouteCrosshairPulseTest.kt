package ykws.android.maro.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **The refused crosshair's beat** — the one clock the aim ring's refusal rides, pinned as the app's
 * other pure rules are.
 *
 * `refusedCrosshairAlpha` is the whole animation of the refusal: the overlay reads it at draw time and
 * the host repaints on that same clock, so nothing in Compose holds its shape and a future refactor
 * could flatten the beat with every test still green. The period is **two** pulses — once down to the
 * floor and once back up — and this file holds that cycle, its alpha range and its two ends.
 */
class RouteCrosshairPulseTest {

    /** The value `route.target.pulseMs` ships; passed in so the test never reads the packaged asset. */
    private val pulseMs = 800

    private fun alpha(nowMs: Long, minAlpha: Float = 0.3f): Float =
        refusedCrosshairAlpha(nowMs, pulseMs, minAlpha)

    @Test
    fun `the cycle is two pulses, so a frame repeats one full cycle later`() {
        assertEquals("the beat opens at full alpha", 1f, alpha(0L), 1e-6f)
        assertEquals(alpha(0L), alpha(2L * pulseMs), 1e-6f)
        assertEquals(alpha(137L), alpha(2L * pulseMs + 137L), 1e-6f)
        // And one pulse is *not* the cycle: a single-period beat would repeat here too.
        assertTrue(
            "one pulseMs must not close the cycle",
            kotlin.math.abs(alpha(137L) - alpha(137L + pulseMs)) > 1e-3f
        )
    }

    @Test
    fun `the two ends are full alpha at the top and the floor one pulse in`() {
        assertEquals("the top", 1f, alpha(0L), 1e-6f)
        assertEquals("the floor", 0.3f, alpha(pulseMs.toLong()), 1e-6f)
        assertEquals("and back to the top", 1f, alpha(2L * pulseMs), 1e-6f)
    }

    @Test
    fun `every frame of the cycle stays inside the alpha range`() {
        var min = Float.MAX_VALUE
        var max = -Float.MAX_VALUE
        for (t in 0L..(2L * pulseMs)) {
            val a = alpha(t)
            if (a < min) min = a
            if (a > max) max = a
        }
        assertEquals("no frame may exceed full alpha", 1f, max, 1e-6f)
        assertEquals("no frame may sink below the floor", 0.3f, min, 1e-6f)
    }

    @Test
    fun `the floor is the caller's minAlpha, not a constant of its own`() {
        assertEquals(0.5f, alpha(pulseMs.toLong(), minAlpha = 0.5f), 1e-6f)
        for (t in 0L..(2L * pulseMs) step 7L) {
            assertTrue("alpha at $t sank below the floor", alpha(t) >= 0.3f - 1e-6f)
        }
    }

    @Test
    fun `a mis-set pulse of zero still yields a whole beat rather than a division by zero`() {
        // The `coerceAtLeast(1)` guard is what keeps a hand-edited key from producing NaN, which would
        // round to zero and paint the crosshair invisible.
        assertEquals(1f, refusedCrosshairAlpha(0L, 0, 0.3f), 1e-6f)
        assertEquals(0.3f, refusedCrosshairAlpha(1L, 0, 0.3f), 1e-6f)
    }
}
