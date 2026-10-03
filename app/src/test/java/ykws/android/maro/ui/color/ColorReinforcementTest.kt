package ykws.android.maro.ui.color

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The shared reinforcement helper: a derived variant of a user-picked colour is the base pushed the
 * lever's share toward black, with the alpha surviving untouched.
 */
class ColorReinforcementTest {

    private val base = 0xFF2ECC71.toInt()

    @Test
    fun zeroPercentLeavesTheColourUntouched() {
        assertEquals(base, reinforcedColor(base, 0))
    }

    @Test
    fun oneHundredPercentPushesTheColourToBlack() {
        assertEquals(0xFF000000.toInt(), reinforcedColor(base, 100))
    }

    @Test
    fun theAlphaSurvivesTheDarkening() {
        val result = reinforcedColor(0x802ECC71.toInt(), 55)

        assertEquals(0x80, (result ushr 24) and 0xFF)
    }

    @Test
    fun theShareIsScaledFromEveryChannelWithRounding() {
        // Half the lever halves every channel, and an odd channel rounds to nearest (51 → 26).
        val result = reinforcedColor(0xFFC86433.toInt(), 50)

        assertEquals(100, (result shr 16) and 0xFF)
        assertEquals(50, (result shr 8) and 0xFF)
        assertEquals(26, result and 0xFF)
    }

    @Test
    fun thePercentageIsClampedToItsEnds() {
        assertEquals(reinforcedColor(base, 0), reinforcedColor(base, -20))
        assertEquals(reinforcedColor(base, 100), reinforcedColor(base, 250))
    }

    @Test
    fun aBlackBaseIsInvariantUnderTheHelper() {
        assertEquals(0xFF000000.toInt(), reinforcedColor(0xFF000000.toInt(), 55))
    }
}
