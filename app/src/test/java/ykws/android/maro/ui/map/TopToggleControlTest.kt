package ykws.android.maro.ui.map

import org.junit.Assert.assertEquals
import org.junit.Test
import ykws.android.maro.data.settings.AppSettings

/**
 * Unit tests for [TopToggleControl] — the top-left toggle row's one home.
 *
 * The row composes from [TopToggleControl.row] and the locked-screen mirror reads the same list to
 * find the lock square's visible index, so these tests pin the two cases that decide where that
 * mirror is drawn: with the land/water square shown the lock is at slot 5, and with it hidden — the
 * one conditional square — the lock moves down to slot 4. Reading the list rather than a literal is
 * the point: the mirror and the row cannot disagree.
 */
class TopToggleControlTest {

    private fun lockSlot(showLandWaterIcon: Boolean): Int =
        TopToggleControl.row(AppSettings(showLandWaterIcon = showLandWaterIcon))
            .indexOf(TopToggleControl.LOCK)

    @Test
    fun theRowListsTheSquaresInOrder() {
        assertEquals(
            listOf(
                TopToggleControl.GPS,
                TopToggleControl.TRACKING,
                TopToggleControl.LAND_WATER,
                TopToggleControl.INSPECT,
                TopToggleControl.ROUTE,
                TopToggleControl.LOCK
            ),
            TopToggleControl.row(AppSettings(showLandWaterIcon = true))
        )
    }

    @Test
    fun withLandWaterShownTheLockSitsAtSlotFive() {
        assertEquals(5, lockSlot(showLandWaterIcon = true))
    }

    @Test
    fun withLandWaterHiddenTheLockSitsAtSlotFour() {
        assertEquals(4, lockSlot(showLandWaterIcon = false))
    }

    @Test
    fun onlyTheLandWaterSquareIsConditional() {
        val shown = TopToggleControl.row(AppSettings(showLandWaterIcon = true))
        val hidden = TopToggleControl.row(AppSettings(showLandWaterIcon = false))

        assertEquals(1, shown.size - hidden.size)
        assertEquals(TopToggleControl.LAND_WATER, shown.first { it !in hidden })
    }
}
