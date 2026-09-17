package ykws.android.maro.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the inspect cursor (plan §8, folded review fix): the two ends of the frozen ladder,
 * the seat a pick lands on, and a cross-type step — the one step the merged walk exists for, because
 * each drawer alone walks its own kind.
 */
class InspectCursorTest {

    private val marker = InspectRank("M1", InspectKind.MARKER, distanceM = 40.0)
    private val track = InspectRank("T1", InspectKind.TRACK, distanceM = 90.0)
    private val farMarker = InspectRank("M2", InspectKind.MARKER, distanceM = 150.0)

    /** The ladder a pick freezes: a marker, a track, then a marker — the merged order. */
    private val ladder = listOf(marker, track, farMarker)

    // ── The seat a pick lands on ─────────────────────────────────────────────

    @Test
    fun `a pick seats the cursor on the picked slot`() {
        val cursor = InspectCursor.at(ladder, "T1")
        assertEquals(1, cursor?.index)
        assertEquals(track, cursor?.current)
        assertEquals(listOf("M1", "T1", "M2"), cursor?.ladderIds)
    }

    @Test
    fun `a pick of an id the ladder does not hold seats nothing`() {
        assertNull(InspectCursor.at(ladder, "T9"))
    }

    // ── The two ends ─────────────────────────────────────────────────────────

    @Test
    fun `the first slot can step forward only`() {
        val cursor = InspectCursor(ladder, 0)
        assertFalse(cursor.canPrev)
        assertTrue(cursor.canNext)
        assertNull(cursor.step(-1))
    }

    @Test
    fun `the last slot can step back only`() {
        val cursor = InspectCursor(ladder, ladder.lastIndex)
        assertTrue(cursor.canPrev)
        assertFalse(cursor.canNext)
        assertNull(cursor.step(1))
    }

    @Test
    fun `next then prev returns to the same item`() {
        val onTrack = InspectCursor.at(ladder, "T1")!!
        assertEquals("M2", onTrack.step(1)?.current?.id)
        assertEquals("T1", onTrack.step(1)?.step(-1)?.current?.id)
    }

    @Test
    fun `a ladder of one has neither end`() {
        val cursor = InspectCursor(listOf(marker), 0)
        assertFalse(cursor.canPrev)
        assertFalse(cursor.canNext)
        assertNull(cursor.step(1))
        assertNull(cursor.step(-1))
    }

    // ── A cross-type step ────────────────────────────────────────────────────

    @Test
    fun `a step landing on the other type keeps the one ladder and changes the kind`() {
        val atMarker = InspectCursor.at(ladder, "M1")!!
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
}
