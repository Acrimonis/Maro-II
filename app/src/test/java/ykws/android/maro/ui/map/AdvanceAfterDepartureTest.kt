package ykws.android.maro.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the walk-advance ordering rule ([advanceAfterDeparture], plan §1, §3) and for the
 * wizard's door ([wizardEditReturnsToCard], plan §4).
 *
 * The rule is the one shared home both cards' advances read — the marker's and the track's — so its
 * four answers (next, previous, none, and the id-absent none) are pinned here rather than at either
 * call site. Android-free, like the other pure policies.
 */
class AdvanceAfterDepartureTest {

    private val world = listOf("a", "b", "c", "d")

    // ── Next, then previous ──────────────────────────────────────────────────

    @Test
    fun `the next item wins over the previous`() {
        assertEquals("c", advanceAfterDeparture("b", world))
    }

    @Test
    fun `the next item wins even when several follow`() {
        assertEquals("d", advanceAfterDeparture("c", world))
    }

    @Test
    fun `the last item falls back to the previous`() {
        assertEquals("c", advanceAfterDeparture("d", world))
    }

    @Test
    fun `the first item takes the next, its only side`() {
        assertEquals("b", advanceAfterDeparture("a", world))
    }

    // ── None ─────────────────────────────────────────────────────────────────

    @Test
    fun `an emptied world yields none`() {
        assertNull(advanceAfterDeparture("a", listOf("a")))
    }

    @Test
    fun `a world of one yields none`() {
        assertNull(advanceAfterDeparture("only", listOf("only")))
        assertNull(advanceAfterDeparture("a", emptyList()))
    }

    @Test
    fun `an id absent from the world yields none`() {
        assertNull(advanceAfterDeparture("z", world))
    }

    // ── The exclusions ───────────────────────────────────────────────────────

    @Test
    fun `an excluded item is skipped while a later one remains`() {
        assertEquals("d", advanceAfterDeparture("b", world, setOf("c")))
    }

    @Test
    fun `when everything after is excluded the previous is taken`() {
        assertEquals("a", advanceAfterDeparture("b", world, setOf("c", "d")))
    }

    @Test
    fun `when every neighbour is excluded none is left`() {
        assertNull(advanceAfterDeparture("b", listOf("a", "b", "c"), setOf("a", "c")))
    }

    @Test
    fun `excluding the departed id itself changes nothing`() {
        assertEquals("c", advanceAfterDeparture("b", world, setOf("b")))
    }

    // ── The wizard's door ────────────────────────────────────────────────────

    @Test
    fun `only a card-entered edit returns to its card`() {
        assertTrue(wizardEditReturnsToCard(WizardDoor.CARD, isEdit = true))
        assertFalse(wizardEditReturnsToCard(WizardDoor.CARD, isEdit = false))
        assertFalse(wizardEditReturnsToCard(WizardDoor.LIST, isEdit = true))
        assertFalse(wizardEditReturnsToCard(WizardDoor.LIST, isEdit = false))
    }
}
