package ykws.android.maro.ui.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests for [bandHeightFor] — R5's coercion behind the dashboard band (plan §12, G7). */
class DashboardBandGeometryTest {

    @Test
    fun measuredHeightBelowTheBaseIsFlooredAtTheBase() {
        assertEquals(600.dp, bandHeightFor(base = 600.dp, measured = 400.dp, ceiling = 1000.dp))
    }

    @Test
    fun measuredHeightAboveTheCeilingIsCappedAtTheCeiling() {
        assertEquals(1000.dp, bandHeightFor(base = 600.dp, measured = 1400.dp, ceiling = 1000.dp))
    }

    @Test
    fun measuredHeightInsideTheBandIsKept() {
        assertEquals(800.dp, bandHeightFor(base = 600.dp, measured = 800.dp, ceiling = 1000.dp))
    }

    @Test
    fun aCeilingBelowTheBaseStillYieldsTheBase() {
        assertEquals(600.dp, bandHeightFor(base = 600.dp, measured = 800.dp, ceiling = 500.dp))
    }
}
