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
        // Five slots at a 38dp slot: the pad and the offsets both come from the policy, so the figures
        // below are checked against the arithmetic rather than typed beside it.
        val slot = 38f
        val endPad = wheelEndPadDp(slots = 5, slotDp = slot)
        val visible = (0..5).map { k ->
            WheelItemBounds(index = k, offsetPx = (endPad + slot * k).toInt(), sizePx = slot.toInt())
        }
        assertEquals(
            2,
            wheelCentredIndex(visible, viewportStartOffsetPx = endPad.toInt(), viewportSizePx = (5 * slot).toInt())
        )
        assertEquals(
            4,
            wheelCentredIndex(visible, viewportStartOffsetPx = (2 * endPad).toInt(), viewportSizePx = (5 * slot).toInt())
        )
    }

    @Test
    fun `an even slot count centres a row just the same`() {
        // Four slots: a half-slot pad, and a scroll of 38 x k centring item k exactly as at five.
        val slot = 38f
        val endPad = wheelEndPadDp(slots = 4, slotDp = slot)
        val visible = (0..4).map { k ->
            WheelItemBounds(index = k, offsetPx = (endPad + slot * k).toInt(), sizePx = slot.toInt())
        }
        assertEquals(
            1,
            wheelCentredIndex(
                visible,
                viewportStartOffsetPx = slot.toInt(),
                viewportSizePx = (4 * slot).toInt()
            )
        )
        assertEquals(
            3,
            wheelCentredIndex(
                visible,
                viewportStartOffsetPx = (endPad * 2).toInt(),
                viewportSizePx = (4 * slot).toInt()
            )
        )
    }

    @Test
    fun `the landing is the entry's own distance on the slot grid`() {
        // From the popup's rest frame the entry is `index x slot` away, whatever the pad is.
        assertEquals(0f, wheelTargetScrollPx(selectedIndex = 0, slotDp = 38f), 0.01f)
        assertEquals(76f, wheelTargetScrollPx(selectedIndex = 2, slotDp = 38f), 0.01f)
        assertEquals(114f, wheelTargetScrollPx(selectedIndex = 3, slotDp = 38f), 0.01f)
    }

    @Test
    fun `the correction is the gap between the entry and the row the band names`() {
        assertEquals(0, wheelCorrectionSlots(targetIndex = 3, centredIndex = 3))
        assertEquals(2, wheelCorrectionSlots(targetIndex = 4, centredIndex = 2))
        assertEquals(-2, wheelCorrectionSlots(targetIndex = 0, centredIndex = 2))
    }

    @Test
    fun `a released drag snaps to the nearest slot`() {
        // A drag's own distance on the slot grid, rounded to the row it is nearest; half a slot rounds up.
        assertEquals(0, wheelSnapTargetSlots(offsetPx = 0f, slotPx = 38f))
        assertEquals(0, wheelSnapTargetSlots(offsetPx = 18f, slotPx = 38f))
        assertEquals(1, wheelSnapTargetSlots(offsetPx = 19f, slotPx = 38f))
        assertEquals(1, wheelSnapTargetSlots(offsetPx = 38f, slotPx = 38f))
        assertEquals(2, wheelSnapTargetSlots(offsetPx = 57f, slotPx = 38f))
        assertEquals(3, wheelSnapTargetSlots(offsetPx = 100f, slotPx = 38f))
        assertEquals(5, wheelSnapTargetSlots(offsetPx = 190f, slotPx = 38f))
    }

    @Test
    fun `a degenerate slot names the first row rather than dividing`() {
        assertEquals(0, wheelSnapTargetSlots(offsetPx = 120f, slotPx = 0f))
        assertEquals(0, wheelSnapTargetSlots(offsetPx = 120f, slotPx = -5f))
    }

    @Test
    fun `a wheel landed on its entry names that entry, in the wheel's own frame`() {
        // The landing and the band read the same layout, so their round trip is asserted in the one frame
        // both can be stated in — rows placed relative to the band, its top at 0. The library's own anchors
        // are not this test's to pin; what it checks is that the two figures agree with each other, at
        // three, four and five slots alike.
        val slot = 38f
        for (slots in 3..5) {
            val endPad = wheelEndPadDp(slots, slot)
            val viewport = (slots * slot).toInt()
            for (target in 0..6) {
                val visible = (0..8).map { k ->
                    WheelItemBounds(index = k, offsetPx = ((k - target) * slot).toInt(), sizePx = slot.toInt())
                }
                assertEquals(
                    target,
                    wheelCentredIndex(
                        visible,
                        viewportStartOffsetPx = -endPad.toInt(),
                        viewportSizePx = viewport
                    )
                )
                assertEquals(0, wheelCorrectionSlots(target, target))
            }
            // The landing never leaves the grid the pad was cut for: the last entry's own distance is
            // inside the range the pad buys.
            assertEquals(endPad * 2f, wheelTargetScrollPx(selectedIndex = slots - 1, slotDp = slot), 0.01f)
        }
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
