package ykws.android.maro.ui.map

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the drag gate behind the Where-Am-I card's close: one finger, past touch slop, once
 * per gesture, latched until the finger lifts.
 *
 * The two exclusions the listener cannot state for itself are pinned here: a second finger latches the
 * whole gesture as a pinch — including the stretch after one of its fingers lifts, where the map can
 * still pan — and a movement that only reaches the slop is not a drag.
 */
class MapPanDetectorTest {

    private val slop = 20f

    private fun detector() = MapPanDetector(slop)

    // ── The drag itself ─────────────────────────────────────────────────────

    @Test
    fun `the move past slop fires once and the rest of the drag stays silent`() {
        val gate = detector()
        gate.down(0f, 0f)
        assertFalse(gate.move(pointerCount = 1, x = 5f, y = 5f))
        assertTrue(gate.move(pointerCount = 1, x = 21f, y = 0f))
        assertFalse(gate.move(pointerCount = 1, x = 60f, y = 40f))
        assertFalse(gate.move(pointerCount = 1, x = 200f, y = 200f))
    }

    @Test
    fun `a move that only reaches slop is not a drag`() {
        val gate = detector()
        gate.down(0f, 0f)
        // On the slop exactly: still a tap, since the slop itself belongs to the tap's own slack.
        assertFalse(gate.move(pointerCount = 1, x = slop, y = 0f))
        // And the diagonal is judged as a distance, never per axis.
        assertFalse(gate.move(pointerCount = 1, x = 14f, y = 14f))
        assertTrue(gate.move(pointerCount = 1, x = 15f, y = 15f))
    }

    @Test
    fun `the lift re-arms the next gesture`() {
        val gate = detector()
        gate.down(0f, 0f)
        assertTrue(gate.move(pointerCount = 1, x = 40f, y = 0f))
        gate.gestureEnd()
        gate.down(100f, 100f)
        // A fresh zero at the new down: the old drag's distance counts for nothing.
        assertFalse(gate.move(pointerCount = 1, x = 104f, y = 100f))
        assertTrue(gate.move(pointerCount = 1, x = 140f, y = 100f))
    }

    @Test
    fun `a move before any down fires nothing`() {
        assertFalse(detector().move(pointerCount = 1, x = 500f, y = 0f))
    }

    // ── The pinch is never a drag ───────────────────────────────────────────

    @Test
    fun `a second finger latches the gesture as a pinch`() {
        val gate = detector()
        gate.down(0f, 0f)
        gate.pinchStarted()
        assertFalse(gate.move(pointerCount = 2, x = 200f, y = 0f))
    }

    @Test
    fun `the stretch after a pinch finger lifts is not a drag either`() {
        val gate = detector()
        gate.down(0f, 0f)
        gate.pinchStarted()
        // One finger left, and the map can pan under it: the gesture is still the pinch it began as.
        assertFalse(gate.move(pointerCount = 1, x = 120f, y = 60f))
        assertFalse(gate.move(pointerCount = 1, x = 300f, y = 0f))
    }

    @Test
    fun `a two-finger move is never a drag however far it travels`() {
        val gate = detector()
        gate.down(0f, 0f)
        assertFalse(gate.move(pointerCount = 2, x = 400f, y = 0f))
    }

    @Test
    fun `the gesture after a pinch drags again`() {
        val gate = detector()
        gate.down(0f, 0f)
        gate.pinchStarted()
        assertFalse(gate.move(pointerCount = 2, x = 60f, y = 0f))
        gate.gestureEnd()
        gate.down(0f, 0f)
        assertTrue(gate.move(pointerCount = 1, x = 30f, y = 0f))
    }
}
