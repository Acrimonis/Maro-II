package ykws.android.maro.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * The palette's one rule, pinned as **perceived difference**: every pair clears ΔE ≥ 25 in CIELAB (CIE76).
 *
 * That replaces the hue/lightness proxy this test first enforced, because the proxy let a near-black sit
 * ΔE 13 from `Black` and let two entries occupy one hue family while both still read as the same colour.
 * The distance is computed here from the shipped hexes, so a re-hex has to answer to the eye's own measure.
 *
 * Clause 3 of the KDoc — nothing may read as water — is deliberately **not** asserted: the depth ramp is a
 * style value this repository does not hold, so that judgement is made on the map, not in a unit test.
 */
class MarkerColorsTest {

    /** The anti-confusion floor, in CIELAB ΔE (CIE76). */
    private val floor = 25.0

    private fun toLab(argb: Int): Triple<Double, Double, Double> {
        fun linear(channel: Int): Double {
            val srgb = (channel and 0xFF) / 255.0
            return if (srgb <= 0.04045) srgb / 12.92 else ((srgb + 0.055) / 1.055).pow(2.4)
        }

        val r = linear(argb shr 16)
        val g = linear(argb shr 8)
        val b = linear(argb)
        val x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.9505
        val y = 0.2126 * r + 0.7152 * g + 0.0722 * b
        val z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.0890

        fun f(t: Double): Double = if (t > 0.008856) cbrt(t) else (7.787 * t + 16.0 / 116.0)
        val fx = f(x)
        val fy = f(y)
        val fz = f(z)
        return Triple(116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz))
    }

    private fun deltaE(a: Int, b: Int): Double {
        val (l1, a1, b1) = toLab(a)
        val (l2, a2, b2) = toLab(b)
        return sqrt((l1 - l2).pow(2) + (a1 - a2).pow(2) + (b1 - b2).pow(2))
    }

    private fun hex(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)

    @Test
    fun `palette holds twenty distinct colours`() {
        assertEquals(20, MarkerColors.all.size)
        assertEquals(MarkerColors.all.size, MarkerColors.all.toSet().size)
    }

    @Test
    fun `every pair clears the perceptual floor`() {
        val palette = MarkerColors.all
        val offenders = mutableListOf<String>()
        for (i in palette.indices) {
            for (j in i + 1 until palette.size) {
                val distance = deltaE(palette[i], palette[j])
                if (distance < floor) {
                    offenders += "slot $i ${hex(palette[i])} vs slot $j ${hex(palette[j])} " +
                        "= ΔE %.1f".format(distance)
                }
            }
        }
        assertTrue(
            "pairs below ΔE $floor:\n" + offenders.joinToString("\n"),
            offenders.isEmpty()
        )
    }

    @Test
    fun `every slot has a name`() {
        MarkerColors.all.indices.forEach { index ->
            assertNotEquals("slot $index has no name resource", 0, MarkerColors.nameResOf(index))
        }
    }

    @Test
    fun `indexOf round-trips a palette colour and rejects a foreign one`() {
        MarkerColors.all.forEachIndexed { index, colour ->
            assertEquals(index, MarkerColors.indexOf(colour))
        }
        assertNull(MarkerColors.indexOf(0xFF123456.toInt()))
    }
}
