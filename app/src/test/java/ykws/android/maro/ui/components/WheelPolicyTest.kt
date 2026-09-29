package ykws.android.maro.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for the wheel's arithmetic — the slot count, the height it is cut to, the end padding that
 * lets its ends reach the centre, and the row the band names.
 *
 * The band names a row from the rows' own bounds rather than from the `(firstVisibleItemIndex,
 * firstVisibleItemScrollOffset)` pair, because under content padding that pair cannot tell two band rows
 * apart. The settled case these tests reproduce is the one the design leans on: with the end pad and a
 * slot of the same height, a scroll of `slot × k` puts item `k`'s own centre exactly on the viewport's —
 * and that holds at an **even** slot count too, which is why the count follows the content rather than
 * being forced odd.
 */
class WheelPolicyTest {

    @Test
    fun `the slot count is the content's own, held between three and five`() {
        assertEquals(3, wheelSlotCount(1))
        assertEquals(3, wheelSlotCount(2))
        assertEquals(3, wheelSlotCount(3))
        assertEquals(4, wheelSlotCount(4))
        assertEquals(5, wheelSlotCount(5))
        assertEquals(5, wheelSlotCount(12))
    }

    @Test
    fun `the bound narrows the count and never widens it`() {
        // 200dp carries five 38dp slots; 150dp carries three; 113dp only two.
        assertEquals(5, wheelSlotsFitting(itemCount = 5, slotDp = 38f, maxHeightDp = 200f))
        assertEquals(3, wheelSlotsFitting(itemCount = 5, slotDp = 38f, maxHeightDp = 150f))
        assertEquals(2, wheelSlotsFitting(itemCount = 5, slotDp = 38f, maxHeightDp = 113f))
        // A tall window does not grow a short list's own count.
        assertEquals(3, wheelSlotsFitting(itemCount = 1, slotDp = 38f, maxHeightDp = 500f))
        assertEquals(4, wheelSlotsFitting(itemCount = 4, slotDp = 38f, maxHeightDp = 500f))
    }

    @Test
    fun `a degenerate bound keeps one slot`() {
        assertEquals(1, wheelSlotsFitting(itemCount = 5, slotDp = 38f, maxHeightDp = 40f))
        assertEquals(1, wheelSlotsFitting(itemCount = 5, slotDp = 0f, maxHeightDp = 200f))
    }

    @Test
    fun `the end padding is a whole slot for odd counts and a half slot for even ones`() {
        assertEquals(76f, wheelEndPadDp(slots = 5, slotDp = 38f), 0.01f)
        assertEquals(57f, wheelEndPadDp(slots = 4, slotDp = 38f), 0.01f)
        assertEquals(38f, wheelEndPadDp(slots = 3, slotDp = 38f), 0.01f)
        assertEquals(0f, wheelEndPadDp(slots = 1, slotDp = 38f), 0.01f)
    }

    @Test
    fun `a settled scroll names the row sitting on the viewport centre`() {
        // A 76dp end pad and a 38dp slot: a scroll of 38 x k centres item k in a 190dp viewport.
        val visible = (0..5).map { k -> WheelItemBounds(index = k, offsetPx = 76 + 38 * k, sizePx = 38) }
        assertEquals(2, wheelCentredIndex(visible, viewportStartOffsetPx = 76, viewportSizePx = 190))
        assertEquals(4, wheelCentredIndex(visible, viewportStartOffsetPx = 152, viewportSizePx = 190))
    }

    @Test
    fun `an even slot count centres a row just the same`() {
        // Four slots: a 57dp half-slot pad, a 152dp viewport, and a scroll of 38 x k centring item k.
        val visible = (0..4).map { k -> WheelItemBounds(index = k, offsetPx = 57 + 38 * k, sizePx = 38) }
        assertEquals(1, wheelCentredIndex(visible, viewportStartOffsetPx = 38, viewportSizePx = 152))
        assertEquals(3, wheelCentredIndex(visible, viewportStartOffsetPx = 114, viewportSizePx = 152))
    }

    @Test
    fun `a partial offset names the nearer row, not the first visible one`() {
        val visible = listOf(
            WheelItemBounds(index = 2, offsetPx = 152, sizePx = 38),
            WheelItemBounds(index = 3, offsetPx = 190, sizePx = 38)
        )
        // The viewport's own centre is 195: item 3's centre (209) is nearer than item 2's (171).
        assertEquals(3, wheelCentredIndex(visible, viewportStartOffsetPx = 100, viewportSizePx = 190))
    }

    @Test
    fun `nothing visible names no row`() {
        assertNull(wheelCentredIndex(emptyList(), viewportStartOffsetPx = 0, viewportSizePx = 190))
    }
}
