package ykws.android.maro.ui.map

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The mark's own two numbers stay where the mark keeps them.
 *
 * The beat's floor decides how far the body fades before its ring holds the edge; the inset decides
 * where a square places the disc. Both are pinned here so neither drifts silently — the floor away from
 * its 50 %, the inset away from half the disc's size.
 */
class MapPulseDotTest {

    @Test
    fun theBodyNeverBeatsPastFiftyPercent() {
        assertEquals("the mark's own floor", 0.5f, MAP_PULSE_DOT_FLOOR, 0f)
    }

    @Test
    fun theSquaresInsetIsHalfTheDisc() {
        assertEquals(
            "the square's own top-right inset",
            MAP_PULSE_DOT_SIZE.value / 2f,
            MAP_PULSE_DOT_INSET.value,
            0f
        )
    }
}
