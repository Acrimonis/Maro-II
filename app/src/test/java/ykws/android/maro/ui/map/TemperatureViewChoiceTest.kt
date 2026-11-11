package ykws.android.maro.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ykws.android.maro.data.temperature.TemperatureReading
import ykws.android.maro.data.temperature.TemperatureState

class TemperatureViewChoiceTest {

    private val onWater = TemperatureState(TemperatureReading(18, false), TemperatureReading(20, false))
    private val onLand = TemperatureState(TemperatureReading(18, false), null)

    @Test
    fun `with no pick the tile shows water on water and air on land`() {
        assertEquals(TemperatureView.WATER, effectiveTemperatureView(onWater, null))
        assertEquals(TemperatureView.AIR, effectiveTemperatureView(onLand, null))
    }

    @Test
    fun `a tap on water switches to the other view and back`() {
        val toAir = tappedTemperatureView(onWater, null)
        assertEquals(TemperatureView.AIR, toAir)
        assertEquals(TemperatureView.WATER, tappedTemperatureView(onWater, toAir))
    }

    @Test
    fun `a tap on land does nothing`() {
        assertNull(tappedTemperatureView(onLand, null))
        assertNull(tappedTemperatureView(onLand, TemperatureView.WATER))
    }

    @Test
    fun `an air pick stays on air while the marker is on water`() {
        assertEquals(TemperatureView.AIR, effectiveTemperatureView(onWater, TemperatureView.AIR))
    }

    @Test
    fun `a water pick shows air on land and applies again back on water`() {
        assertEquals(TemperatureView.AIR, effectiveTemperatureView(onLand, TemperatureView.WATER))
        assertEquals(TemperatureView.WATER, effectiveTemperatureView(onWater, TemperatureView.WATER))
    }
}
