package ykws.android.maro.data.location

import android.hardware.SensorManager
import android.view.Surface
import org.junit.Assert.assertEquals
import org.junit.Test
import ykws.android.maro.ui.map.trueFromMagnetic

/**
 * Unit tests for the two pure compass helpers: [remapAxesFor] (display rotation → sensor axis
 * pair) and [trueFromMagnetic] (magnetic azimuth + declination → true heading in [0, 360)).
 */
class CompassHeadingTest {

    @Test
    fun `natural orientation keeps the device axes`() {
        assertEquals(SensorManager.AXIS_X to SensorManager.AXIS_Y, remapAxesFor(Surface.ROTATION_0))
    }

    @Test
    fun `landscape rotations swap the axes with the matching sign`() {
        assertEquals(SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X, remapAxesFor(Surface.ROTATION_90))
        assertEquals(SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X, remapAxesFor(Surface.ROTATION_270))
    }

    @Test
    fun `upside down negates both axes`() {
        assertEquals(SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y, remapAxesFor(Surface.ROTATION_180))
    }

    @Test
    fun `unknown rotation falls back to the natural orientation`() {
        assertEquals(remapAxesFor(Surface.ROTATION_0), remapAxesFor(42))
    }

    @Test
    fun `east declination adds to the azimuth`() {
        assertEquals(12.5f, trueFromMagnetic(10f, 2.5f), 1e-4f)
    }

    @Test
    fun `west declination subtracts and wraps below zero`() {
        assertEquals(359f, trueFromMagnetic(1f, -2f), 1e-4f)
    }

    @Test
    fun `result wraps past 360`() {
        assertEquals(1f, trueFromMagnetic(359f, 2f), 1e-4f)
        assertEquals(0f, trueFromMagnetic(357.5f, 2.5f), 1e-4f)
    }

    @Test
    fun `zero declination is the identity`() {
        assertEquals(123.4f, trueFromMagnetic(123.4f, 0f), 1e-4f)
    }
}
