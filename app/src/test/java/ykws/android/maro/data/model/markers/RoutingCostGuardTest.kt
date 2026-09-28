package ykws.android.maro.data.model.markers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The one validity rule for a stored routing cost — [validRoutingCost] — and the slider mapping that
 * feeds it, [routingCostForSlider].
 *
 * `null` is unset, `0` is the slider's own Off position rather than a value, and anything outside 1–10
 * reads as unset too, so no reader has a rule of its own. The top of the range is
 * [ROUTING_COST_BLOCKED] — the wall — and it passes the guard exactly as the prices do.
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
    fun `the price range is read as set`() {
        assertEquals(1, validRoutingCost(1))
        assertEquals(ROUTING_COST_MAX, validRoutingCost(ROUTING_COST_MAX))
    }

    @Test
    fun `the wall is the top of the range and reads as set`() {
        assertEquals(ROUTING_COST_BLOCKED, validRoutingCost(ROUTING_COST_BLOCKED))
    }

    @Test
    fun `an out-of-range value reads as unset`() {
        assertNull(validRoutingCost(ROUTING_COST_BLOCKED + 1))
        assertNull(validRoutingCost(-1))
    }

    @Test
    fun `the slider's Off position maps to unset`() {
        assertNull(routingCostForSlider(0.0))
    }

    @Test
    fun `the slider's ends map to themselves`() {
        assertEquals(1, routingCostForSlider(1.0))
        assertEquals(ROUTING_COST_MAX, routingCostForSlider(ROUTING_COST_MAX.toDouble()))
        assertEquals(ROUTING_COST_BLOCKED, routingCostForSlider(ROUTING_COST_BLOCKED.toDouble()))
    }

    @Test
    fun `a slider position outside the range is refused`() {
        assertNull(routingCostForSlider((ROUTING_COST_BLOCKED + 1).toDouble()))
        assertNull(routingCostForSlider(-1.0))
    }
}
