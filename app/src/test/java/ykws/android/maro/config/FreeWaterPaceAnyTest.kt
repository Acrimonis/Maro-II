package ykws.android.maro.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FreeWaterPaceAnyTest {

    @Test
    fun `the sentinel is the option list's first position and never a numbered stop`() {
        val options = AppConfig.ROUTE_FREE_WATER_PACE_OPTIONS_KN

        assertEquals(AppConfig.ROUTE_FREE_WATER_PACE_ANY, options.first())
        assertEquals(AppConfig.ROUTE_FREE_WATER_PACE_MIN_KN, options[1])
        assertEquals(AppConfig.ROUTE_FREE_WATER_PACE_MAX_KN, options.last())
        assertEquals(AppConfig.ROUTE_FREE_WATER_PACE_STOPS_KN.size + 1, options.size)
    }

    @Test
    fun `isFreeWaterPaceAny names the sentinel and nothing else`() {
        assertTrue(AppConfig.isFreeWaterPaceAny(AppConfig.ROUTE_FREE_WATER_PACE_ANY))
        assertFalse(AppConfig.isFreeWaterPaceAny(AppConfig.ROUTE_FREE_WATER_PACE_MIN_KN))
        assertFalse(AppConfig.isFreeWaterPaceAny(AppConfig.routeFreeWaterPaceKn))
    }

    @Test
    fun `the resolver plans Any at the observed pace and a numbered stop at its own number`() {
        assertEquals(25.0, AppConfig.routePaceForSearchKn(AppConfig.ROUTE_FREE_WATER_PACE_ANY.toDouble(), null), 0.0)
        assertEquals(8.0, AppConfig.routePaceForSearchKn(AppConfig.ROUTE_FREE_WATER_PACE_ANY.toDouble(), 8.0), 0.0)
        assertEquals(10.0, AppConfig.routePaceForSearchKn(10.0, 8.0), 0.0)
        assertEquals(28.0, AppConfig.routePaceForSearchKn(28.0, 8.0), 0.0)
    }

    @Test
    fun `the numbered snap keeps the sentinel out of its grid`() {
        assertEquals(
            AppConfig.ROUTE_FREE_WATER_PACE_MIN_KN,
            AppConfig.snapFreeWaterPaceKn(AppConfig.ROUTE_FREE_WATER_PACE_ANY),
            0.0f
        )
    }
}
