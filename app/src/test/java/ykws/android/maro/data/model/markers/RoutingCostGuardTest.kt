package ykws.android.maro.data.model.markers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The one validity rule for a stored routing cost — [validRoutingCost] — and the slider mapping that
 * feeds it, [routingCostForSlider].
 *
 * `null` is unset, `0` is the slider's own Off position rather than a cost, and anything outside 1–9
 * reads as unset too, so no reader has a rule of its own.
 */
class RoutingCostGuardTest {

    @Test
    fun `null reads as unset`() {
        assertNull(validRoutingCost(null))
    }

    @Test
    fun `zero reads as unset`() {
        assertNull(validRoutingCost(0))
    }

    @Test
    fun `both ends of the range are costs`() {
        assertEquals(1, validRoutingCost(1))
        assertEquals(9, validRoutingCost(9))
    }

    @Test
    fun `an out-of-range value reads as unset`() {
        assertNull(validRoutingCost(10))
        assertNull(validRoutingCost(-1))
    }

    @Test
    fun `the slider's Off position maps to unset`() {
        assertNull(routingCostForSlider(0.0))
    }

    @Test
    fun `the slider's ends map to themselves`() {
        assertEquals(1, routingCostForSlider(1.0))
        assertEquals(9, routingCostForSlider(9.0))
    }

    @Test
    fun `a slider position outside the range is refused`() {
        assertNull(routingCostForSlider(10.0))
        assertNull(routingCostForSlider(-1.0))
    }
}
