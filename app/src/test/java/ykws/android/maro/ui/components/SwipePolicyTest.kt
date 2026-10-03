package ykws.android.maro.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the card swipe's arithmetic — the release's decision at the delete swipe's own
 * threshold, on either side, and the clamp that holds the card inside its own width.
 *
 * The settled case these tests reproduce is the plan's own: thirty in a hundred commits on release,
 * leftward into the delete lifecycle and rightward into the pin toggle, with everything inside that
 * reach snapping back to rest. A thousand-pixel card keeps the threshold a whole number of pixels,
 * so the tests name the boundary itself rather than rounding around it.
 */
class SwipePolicyTest {

    private val width = 1000f
    private val threshold = 0.30f

    @Test
    fun `a release inside the threshold resolves nothing, either side`() {
        assertEquals(SwipeOutcome.None, swipeOutcome(0f, width, threshold))
        assertEquals(SwipeOutcome.None, swipeOutcome(-299f, width, threshold))
        assertEquals(SwipeOutcome.None, swipeOutcome(299f, width, threshold))
        // The threshold itself is the last offset that changes nothing: the commit is past it.
        assertEquals(SwipeOutcome.None, swipeOutcome(-300f, width, threshold))
        assertEquals(SwipeOutcome.None, swipeOutcome(300f, width, threshold))
    }

    @Test
    fun `a release past the threshold on the left deletes`() {
        assertEquals(SwipeOutcome.Delete, swipeOutcome(-301f, width, threshold))
        assertEquals(SwipeOutcome.Delete, swipeOutcome(-width, width, threshold))
    }

    @Test
    fun `a release past the threshold on the right toggles the pin`() {
        assertEquals(SwipeOutcome.TogglePin, swipeOutcome(301f, width, threshold))
        assertEquals(SwipeOutcome.TogglePin, swipeOutcome(width, width, threshold))
    }

    @Test
    fun `a card not measured yet resolves nothing`() {
        assertEquals(SwipeOutcome.None, swipeOutcome(400f, 0f, threshold))
        assertEquals(SwipeOutcome.None, swipeOutcome(-400f, 0f, threshold))
    }

    @Test
    fun `the drag is held inside the card's own width`() {
        assertEquals(width, swipeClampedOffset(width * 2f, width), 0.001f)
        assertEquals(-width, swipeClampedOffset(-width * 2f, width), 0.001f)
        assertEquals(250f, swipeClampedOffset(250f, width), 0.001f)
        assertEquals(-250f, swipeClampedOffset(-250f, width), 0.001f)
        assertEquals(0f, swipeClampedOffset(500f, 0f), 0.001f)
    }
}
