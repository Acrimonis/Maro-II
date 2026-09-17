package ykws.android.maro.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.MarkerGeometry
import ykws.android.maro.data.model.markers.UserMarker

/**
 * Unit tests for the inspect cursor (plan §8, folded review fix): the two ends of the frozen ladder,
 * the seat a pick lands on, and a cross-type step — the one step the merged walk exists for, because
 * each drawer alone walks its own kind.
 *
 * It also covers the step's own resolution (plan §5): the ladder carries the collection it was ranked
 * from, so a step opens the marker its walk seated on even where another shelf's filter has dropped
 * that id.
 */
class InspectCursorTest {

    private val marker = InspectRank("M1", InspectKind.MARKER, distanceM = 40.0)
    private val track = InspectRank("T1", InspectKind.TRACK, distanceM = 90.0)
    private val farMarker = InspectRank("M2", InspectKind.MARKER, distanceM = 150.0)

    /** The ladder a pick freezes: a marker, a track, then a marker — the merged order. */
    private val ladder = listOf(marker, track, farMarker)

    /**
     * The world the ladder was ranked from, and the list world beside it: the list holds M2 and not
     * M1, which is exactly the disagreement that used to spend a step with nothing on screen.
     */
    private val mapWorld = listOf(userMarker("M1"), userMarker("M2"))
    private val listWorld = listOf(userMarker("T1"))

    private val resolveMapWorld: (String) -> UserMarker? = { id -> mapWorld.find { it.id == id } }

    private fun cursorAt(index: Int) = InspectCursor(ladder, index, resolveMapWorld)

    private fun pick(pickedId: String) = InspectCursor.at(ladder, pickedId, resolveMapWorld)

    private fun userMarker(id: String) = UserMarker(
        id = id,
        name = id,
        geometry = MarkerGeometry.Pin(LatLng(43.0, 7.0))
    )

    // ── The seat a pick lands on ─────────────────────────────────────────────

    @Test
    fun `a pick seats the cursor on the picked slot`() {
        val cursor = pick("T1")
        assertEquals(1, cursor?.index)
        assertEquals(track, cursor?.current)
        assertEquals(listOf("M1", "T1", "M2"), cursor?.ladderIds)
    }

    @Test
    fun `a pick of an id the ladder does not hold seats nothing`() {
        assertNull(pick("T9"))
    }

    // ── The two ends ─────────────────────────────────────────────────────────

    @Test
    fun `the first slot can step forward only`() {
        val cursor = cursorAt(0)
        assertFalse(cursor.canPrev)
        assertTrue(cursor.canNext)
        assertNull(cursor.step(-1))
    }

    @Test
    fun `the last slot can step back only`() {
        val cursor = cursorAt(ladder.lastIndex)
        assertTrue(cursor.canPrev)
        assertFalse(cursor.canNext)
        assertNull(cursor.step(1))
    }

    @Test
    fun `next then prev returns to the same item`() {
        val onTrack = pick("T1")!!
        assertEquals("M2", onTrack.step(1)?.current?.id)
        assertEquals("T1", onTrack.step(1)?.step(-1)?.current?.id)
    }

    @Test
    fun `a ladder of one has neither end`() {
        val cursor = InspectCursor(listOf(marker), 0, resolveMapWorld)
        assertFalse(cursor.canPrev)
        assertFalse(cursor.canNext)
        assertNull(cursor.step(1))
        assertNull(cursor.step(-1))
    }

    // ── A cross-type step ────────────────────────────────────────────────────

    @Test
    fun `a step landing on the other type keeps the one ladder and changes the kind`() {
        val atMarker = pick("M1")!!
        val onTrack = atMarker.step(1)!!
        assertEquals(InspectKind.TRACK, onTrack.current?.kind)
        // One frozen pass: the cursor moves, the world does not — which is what lets the card stay
        // mounted while only its content changes.
        assertEquals(atMarker.ladder, onTrack.ladder)

        val onFarMarker = onTrack.step(1)!!
        assertEquals(InspectKind.MARKER, onFarMarker.current?.kind)
        assertEquals("M2", onFarMarker.current?.id)
        assertFalse(onFarMarker.canNext)
    }

    // ── The step's own resolution (plan §5) ─────────────────────────────────

    @Test
    fun `a step resolves its marker in the world the ladder was ranked from`() {
        val atMarker = pick("M1")!!
        // The list world has since dropped M1 — a narrower list filter, with the link off — and the
        // step must not care: it looks the id up in the collection the ranking was built from.
        assertNull(listWorld.find { it.id == "M1" })
        assertEquals("M1", atMarker.markerAt()?.id)
        // A track slot resolves nothing here: the line half opens through the loader the ranking used.
        assertNull(atMarker.step(1)!!.markerAt())
    }

    @Test
    fun `a step on an id the ladder's own world has lost resolves nothing`() {
        val shrunk = InspectCursor(ladder, 0, { _ -> null })
        assertNull(shrunk.markerAt())
    }

    @Test
    fun `the resolver travels with every step`() {
        val onFarMarker = pick("M1")!!.step(1)!!.step(1)!!
        assertEquals("M2", onFarMarker.markerAt()?.id)
        // Each step is a copy, and the copy carries the very resolver the pick was handed — no step
        // can reach a world other than the one the ladder was ranked from.
        assertEquals(resolveMapWorld, onFarMarker.resolveMarker)
    }

    @Test
    fun `the resolver answers with the world as it stands, not as it stood`() {
        val live = mutableListOf(userMarker("M1"))
        val cursor = InspectCursor.at(ladder, "M1", { id -> live.find { it.id == id } })!!
        assertEquals("M1", cursor.markerAt()?.id)
        // An edit is the answer's new content ...
        live[0] = live[0].copy(name = "renamed")
        assertEquals("renamed", cursor.markerAt()?.name)
        // ... and a delete is no answer at all: a held entity would have gone stale on the first and
        // outlived the marker on the second.
        live.clear()
        assertNull(cursor.markerAt())
    }
}
