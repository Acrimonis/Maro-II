package ykws.android.maro.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.MarkerGeometry
import ykws.android.maro.data.model.markers.UserMarker

/**
 * Unit tests for the card-walk decisions ([CardWalkPolicy]): the ordered candidates behind the
 * ordering rule, the reconcile decision a write takes on the open card, the world a card walks, and
 * the world a card's return reopens on (plan §3, §4, §5).
 *
 * These are the §5 scenarios that are reachable without a device: the advance after an item write, the
 * not-found close, the editor's return on a card whose walk is the inspect ladder, the four-source
 * world rule the §2 correction turns on, and the step pills' ends for every door — the panel's and the
 * menu's alike, the ladder's own, the map door's refusal to grey either end, and the one-item walk that
 * draws no bars. The scenarios that need an `AndroidViewModel` or a `@Composable` — the door as
 * recorded state, the drawer's own transitions, the camera — are not here
 * and cannot be here: this module carries JUnit and `kotlinx-coroutines-test` alone.
 */
class CardWalkDecisionsTest {

    private val world = listOf("a", "b", "c", "d")

    private fun marker(id: String) = UserMarker(
        id = id,
        name = id,
        geometry = MarkerGeometry.Pin(LatLng(0.0, 0.0))
    )

    // ── The ordered candidates (the track delete's try-each list) ────────────

    @Test
    fun `the candidates run below the gap first, nearest first, then above it`() {
        assertEquals(listOf("c", "d", "a"), advanceCandidatesAfterDeparture("b", world))
    }

    @Test
    fun `the candidates of the last item are the whole side above it, nearest first`() {
        assertEquals(listOf("c", "b", "a"), advanceCandidatesAfterDeparture("d", world))
    }

    @Test
    fun `an excluded id never appears among the candidates`() {
        assertEquals(listOf("d", "a"), advanceCandidatesAfterDeparture("b", world, setOf("c", "b")))
    }

    @Test
    fun `an id the world never held yields no candidates at all`() {
        assertTrue(advanceCandidatesAfterDeparture("z", world).isEmpty())
    }

    @Test
    fun `a world of one yields no candidates`() {
        assertTrue(advanceCandidatesAfterDeparture("a", listOf("a")).isEmpty())
    }

    // ── The reconcile decision (the advance after an item write, the not-found close) ──

    @Test
    fun `an id the world still holds holds the card`() {
        assertEquals(
            CardReconcile.Hold,
            cardReconcile(true, "a", worldHoldsSelected = true, DrawerSource.LIST, advanceTo = "b")
        )
    }

    @Test
    fun `a write that leaves the card's world advances to the rule's neighbour`() {
        assertEquals(
            CardReconcile.Advance("c"),
            cardReconcile(true, "b", worldHoldsSelected = false, DrawerSource.LIST, advanceTo = "c")
        )
    }

    @Test
    fun `a world left with no neighbour closes the card`() {
        assertEquals(
            CardReconcile.Close,
            cardReconcile(true, "a", worldHoldsSelected = false, DrawerSource.MAP, advanceTo = null)
        )
    }

    @Test
    fun `no selection holds whatever the world says`() {
        assertEquals(
            CardReconcile.Hold,
            cardReconcile(true, null, worldHoldsSelected = false, DrawerSource.LIST, advanceTo = "b")
        )
    }

    @Test
    fun `no open card holds whatever the write did`() {
        assertEquals(
            CardReconcile.Hold,
            cardReconcile(false, "b", worldHoldsSelected = false, DrawerSource.LIST, advanceTo = "c")
        )
    }

    @Test
    fun `an inspect-opened card closes rather than advancing, its cursor being the screen's`() {
        assertEquals(
            CardReconcile.Close,
            cardReconcile(true, "b", worldHoldsSelected = false, DrawerSource.INSPECT, advanceTo = "c")
        )
    }

    // ── The return's world (the editor's return on an inspect-walked card) ────

    @Test
    fun `an inspect card whose cursor could not be re-seated drops to the map world`() {
        assertEquals(DrawerSource.MAP, cardWalkSource(DrawerSource.INSPECT, cursorReSeated = false))
    }

    @Test
    fun `an inspect card whose cursor was re-seated keeps its ladder`() {
        assertEquals(DrawerSource.INSPECT, cardWalkSource(DrawerSource.INSPECT, cursorReSeated = true))
    }

    @Test
    fun `every other source is its own world, re-seated or not`() {
        for (source in listOf(DrawerSource.LIST, DrawerSource.MAP, DrawerSource.MENU)) {
            assertEquals(source, cardWalkSource(source, cursorReSeated = true))
            assertEquals(source, cardWalkSource(source, cursorReSeated = false))
        }
    }

    // ── The three-kind world rule (the family plan §3) ───────────────────────

    @Test
    fun `a list-opened card reads the list world, its own filter and order`() {
        val list = listOf(marker("list"))
        assertEquals(
            list,
            cardWalkWorld(DrawerSource.LIST, list, listOf(marker("filtered")), listOf(marker("map-source")))
        )
    }

    @Test
    fun `a map-opened card reads the map's source of truth, standing where the filter drops it`() {
        val source = listOf(marker("map-source"))
        assertEquals(
            source,
            cardWalkWorld(DrawerSource.MAP, listOf(marker("list")), listOf(marker("filtered")), source)
        )
    }

    // ── The step pills' ends (the panel, the menu, the ladder, the map door, the one-item walk) ──

    @Test
    fun `a panel card greys its ends at the first and the last index`() {
        assertEquals(CardStepEnds(true, atFirst = true, atLast = false), cardStepEnds(DrawerSource.LIST, 4, 0, null, null))
        assertEquals(CardStepEnds(true, atFirst = false, atLast = true), cardStepEnds(DrawerSource.LIST, 4, 3, null, null))
        assertEquals(CardStepEnds(true, atFirst = false, atLast = false), cardStepEnds(DrawerSource.LIST, 4, 1, null, null))
    }

    @Test
    fun `a menu card greys its ends exactly as the panel's does`() {
        assertEquals(CardStepEnds(true, atFirst = true, atLast = false), cardStepEnds(DrawerSource.MENU, 4, 0, null, null))
        assertEquals(CardStepEnds(true, atFirst = false, atLast = true), cardStepEnds(DrawerSource.MENU, 4, 3, null, null))
        assertEquals(CardStepEnds(true, atFirst = false, atLast = false), cardStepEnds(DrawerSource.MENU, 4, 1, null, null))
    }

    @Test
    fun `an inspect card reads the ladder's own ends, and neither end before the ladder is frozen`() {
        assertEquals(CardStepEnds(true, atFirst = true, atLast = false), cardStepEnds(DrawerSource.INSPECT, 4, 1, true, false))
        assertEquals(CardStepEnds(true, atFirst = false, atLast = true), cardStepEnds(DrawerSource.INSPECT, 4, 1, false, true))
        // The ladder is not frozen yet — the live acquire's own window: the pills read enabled and correct
        // themselves when the pause seats the cursor, rather than greying for a frame on every swap.
        assertEquals(CardStepEnds(true, atFirst = false, atLast = false), cardStepEnds(DrawerSource.INSPECT, 4, 1, null, null))
    }

    @Test
    fun `a one-item walk draws no pills at all`() {
        assertEquals(CardStepEnds(false, atFirst = true, atLast = true), cardStepEnds(DrawerSource.LIST, 1, 0, null, null))
        assertEquals(CardStepEnds(false, atFirst = true, atLast = true), cardStepEnds(DrawerSource.MENU, 1, 0, null, null))
        assertEquals(CardStepEnds(false, atFirst = false, atLast = false), cardStepEnds(DrawerSource.MAP, 1, 0, null, null))
    }

    @Test
    fun `a map door greys neither end, standing outside either walk`() {
        assertEquals(CardStepEnds(true, atFirst = false, atLast = false), cardStepEnds(DrawerSource.MAP, 4, 0, null, null))
    }

    // ── The three-kind world rule (the family plan §3) ───────────────────────

    @Test
    fun `an inspect-opened card reads the map-filtered world its ladder is ranked from`() {
        val filtered = listOf(marker("filtered"))
        assertEquals(
            filtered,
            cardWalkWorld(DrawerSource.INSPECT, listOf(marker("list")), filtered, listOf(marker("map-source")))
        )
    }

    @Test
    fun `a menu-opened card reads the menu's map-referential set, closing with it`() {
        val filtered = listOf(marker("filtered"))
        assertEquals(
            filtered,
            cardWalkWorld(DrawerSource.MENU, listOf(marker("list")), filtered, listOf(marker("map-source")))
        )
    }
}
