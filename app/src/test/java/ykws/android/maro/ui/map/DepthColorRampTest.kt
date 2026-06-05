package ykws.android.maro.ui.map

import org.junit.Assert.*
import org.junit.Test

class DepthColorRampTest {

    private fun alpha(argb: Int) = (argb ushr 24) and 0xFF
    private fun red(argb: Int) = (argb ushr 16) and 0xFF
    private fun green(argb: Int) = (argb ushr 8) and 0xFF
    private fun blue(argb: Int) = argb and 0xFF

    @Test
    fun `NoData and above-datum are fully transparent`() {
        assertEquals(0, DepthColorRamp.argb(Float.NaN))
        assertEquals(0, DepthColorRamp.argb(-1f))
        assertEquals(0, alpha(DepthColorRamp.argb(Float.NaN)))
    }

    @Test
    fun `water depths are semi-transparent`() {
        assertEquals(160, alpha(DepthColorRamp.argb(3f)))
        assertEquals(160, alpha(DepthColorRamp.argb(40f)))
    }

    @Test
    fun `collision band carries a warning tint`() {
        // Near the surface the red channel is pushed up vs a mid-depth (non-warning) cell.
        assertTrue(red(DepthColorRamp.argb(1f)) > red(DepthColorRamp.argb(30f)))
    }

    @Test
    fun `deeper water is less bright blue than shallow`() {
        assertTrue(blue(DepthColorRamp.argb(2f)) > blue(DepthColorRamp.argb(58f)))
        // Green also fades from pale shallow toward dark deep.
        assertTrue(green(DepthColorRamp.argb(8f)) > green(DepthColorRamp.argb(58f)))
    }

    @Test
    fun `clamps beyond max depth`() {
        assertEquals(DepthColorRamp.argb(60f), DepthColorRamp.argb(120f))
    }
}
