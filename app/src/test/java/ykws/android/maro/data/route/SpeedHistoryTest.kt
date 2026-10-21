package ykws.android.maro.data.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpeedHistoryTest {

    @Test
    fun emptyWindowHasNoMedian() {
        assertNull(SpeedHistory.weightedMedian(emptyList(), 60.0))
    }

    @Test
    fun singleSpeedIsItsOwnMedian() {
        assertEquals(21.0, SpeedHistory.weightedMedian(listOf(21.0), 60.0)!!, 1e-9)
    }

    @Test
    fun equalSpeedsReturnThatValue() {
        assertEquals(
            23.0,
            SpeedHistory.weightedMedian(listOf(23.0, 23.0, 23.0), 60.0)!!,
            1e-9
        )
    }

    @Test
    fun foldAppendsAndCapsDroppingOldest() {
        val window = listOf(20.0, 21.0, 22.0)
        val folded = SpeedHistory.fold(window, 23.0, maxN = 3)
        assertEquals(listOf(21.0, 22.0, 23.0), folded)
    }
}
