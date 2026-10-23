package ykws.android.maro.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.markers.MarkerGeometry
import ykws.android.maro.data.model.markers.MarkerOrigin
import ykws.android.maro.data.model.markers.UserMarker

/**
 * **The selection pass's diff, as far as the JVM can see it (C05).**
 *
 * `MarkerOverlay` rebuilds a marker's osmdroid overlays only when its [MarkerPaintState] moved — the
 * one unselected, the one selected, or the markers a match result dims or brightens. The two functions
 * that decide that set, [paintStateOf] and [changedPaintKeys], are pure precisely so this file can pin
 * them; the overlay surgery on a real `MapView` stays the device's, since osmdroid overlays cannot be
 * constructed here.
 */
class MarkerPaintDiffTest {

    private val plain = MarkerPaintState(isSelected = false, matchActive = false, isMatched = false)
    private val selected = plain.copy(isSelected = true)

    private fun marker(id: String) = UserMarker(
        id = id,
        name = id,
        geometry = MarkerGeometry.Pin(LatLng(43.5, 7.2)),
        origin = MarkerOrigin.USER
    )

    @Test
    fun `a selection move names the old and the new marker only`() {
        val drawn = mapOf("a" to selected, "b" to plain, "c" to plain)
        val wanted = mapOf("a" to plain, "b" to selected, "c" to plain)
        assertEquals(setOf("a", "b"), changedPaintKeys(drawn, wanted))
    }

    @Test
    fun `an unchanged selection names nothing`() {
        val drawn = mapOf("a" to selected, "b" to plain)
        assertTrue(changedPaintKeys(drawn, drawn.toMap()).isEmpty())
    }

    @Test
    fun `clearing the selection names the one marker that wore it`() {
        val drawn = mapOf("a" to selected, "b" to plain)
        val wanted = mapOf("a" to plain, "b" to plain)
        assertEquals(setOf("a"), changedPaintKeys(drawn, wanted))
    }

    @Test
    fun `a key the last build never drew counts as moved`() {
        val wanted = mapOf("a" to plain, "new" to plain)
        assertEquals(setOf("new"), changedPaintKeys(mapOf("a" to plain), wanted))
    }

    @Test
    fun `a key the build dropped is not the selection pass's to touch`() {
        val drawn = mapOf("a" to plain, "gone" to selected)
        assertTrue(changedPaintKeys(drawn, mapOf("a" to plain)).isEmpty())
    }

    @Test
    fun `the paint state reads the selection off the id`() {
        val a = marker("a")
        assertTrue(paintStateOf(a, "a", matchActive = false, matchedIds = emptySet()).isSelected)
        assertFalse(paintStateOf(a, "b", matchActive = false, matchedIds = emptySet()).isSelected)
        assertFalse(paintStateOf(a, null, matchActive = false, matchedIds = emptySet()).isSelected)
    }

    @Test
    fun `a match needs an active result, and the result moves every marker's standing`() {
        val a = marker("a")
        val b = marker("b")
        // The matched set is read only while a result is active.
        assertFalse(paintStateOf(a, null, matchActive = false, matchedIds = setOf("a")).isMatched)
        val withResult = paintStateOf(a, null, matchActive = true, matchedIds = setOf("a"))
        assertTrue(withResult.matchActive && withResult.isMatched)
        val dimmed = paintStateOf(b, null, matchActive = true, matchedIds = setOf("a"))
        assertTrue(dimmed.matchActive && !dimmed.isMatched)

        // A result arriving flips `matchActive` on both, so both are the pass's to repaint.
        val drawn = mapOf("a" to plain, "b" to plain)
        val wanted = mapOf("a" to withResult, "b" to dimmed)
        assertEquals(setOf("a", "b"), changedPaintKeys(drawn, wanted))
    }
}
