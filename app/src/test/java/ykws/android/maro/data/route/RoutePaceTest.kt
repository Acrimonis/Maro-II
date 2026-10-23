package ykws.android.maro.data.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutePaceTest {

    private fun starting(
        dayKn: Double? = null,
        dayDate: String = "",
        tripDate: String = "2026-10-22",
        historyKn: Double? = null
    ) = RoutePace.starting(dayKn, dayDate, tripDate, historyKn, defaultKn = 25.0)

    @Test
    fun theDaysPaceStartsATripOnItsOwnDate() {
        assertEquals(
            PaceReading(29.0, PaceSource.TODAY),
            starting(dayKn = 29.0, dayDate = "2026-10-22", historyKn = 31.0)
        )
    }

    @Test
    fun aPaceFromAnotherDateFallsToTheHistory() {
        assertEquals(
            PaceReading(31.0, PaceSource.HISTORY),
            starting(dayKn = 29.0, dayDate = "2026-10-21", historyKn = 31.0)
        )
    }

    @Test
    fun anEmptyHistoryFallsToTheShippedDefault() {
        assertEquals(PaceReading(25.0, PaceSource.DEFAULT), starting())
        assertEquals(PaceReading(25.0, PaceSource.DEFAULT), starting(historyKn = 0.0))
    }

    @Test
    fun aThinHistoryStillReadsAsHistory() {
        assertEquals(PaceReading(18.0, PaceSource.HISTORY), starting(historyKn = 18.0))
    }

    @Test
    fun cruisingIsARealSpeedAtOrAboveTheStopSpeed() {
        assertTrue(RoutePace.isCruising(4.0, stopKn = 4.0))
        assertTrue(RoutePace.isCruising(30.0, stopKn = 4.0))
        assertFalse(RoutePace.isCruising(3.9, stopKn = 4.0))
        assertFalse(RoutePace.isCruising(0.0, stopKn = 0.0))
        assertFalse(RoutePace.isCruising(Double.NaN, stopKn = 4.0))
    }

    @Test
    fun liveSwitchesOnAtAQuarterOfTheWindow() {
        val window = 240_000L
        assertFalse(RoutePace.isLive(0L, window))
        assertFalse(RoutePace.isLive(59_999L, window))
        assertTrue(RoutePace.isLive(60_000L, window))
    }
}
