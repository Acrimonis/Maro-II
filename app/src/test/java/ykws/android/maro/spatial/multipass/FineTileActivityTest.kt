package ykws.android.maro.spatial.multipass

import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **The warm-up's busy window — it rises on the first tile and falls only a grace after the last.**
 *
 * The walk builds a tile at a time, so the window must not fall in the gap between two tiles: a
 * `begin`/`end` pair raises it and holds it through the grace, and only the grace after the **last**
 * end clears it. The virtual clock pins the rise, the hold across a tile gap and the debounce that
 * merges two bursts into one window.
 */
class FineTileActivityTest {

    private val graceMs = 400L

    @Test
    fun busyRisesOnBeginAndFallsOnlyAfterTheGrace() = runTest {
        val activity = FineTileActivity(graceMs, this)

        assertFalse("idle until the store is touched", activity.busy.value)
        activity.begin()
        assertTrue("a lookup raises the window at once", activity.busy.value)
        activity.end()
        assertTrue("the end holds the window through the grace", activity.busy.value)

        advanceTimeBy(graceMs - 1)
        runCurrent()
        assertTrue("still busy one tick before the grace elapses", activity.busy.value)

        advanceTimeBy(2)
        runCurrent()
        assertFalse("the window falls a grace after the last work", activity.busy.value)
    }

    @Test
    fun aLaterTileKeepsTheWindowUpAcrossTheGap() = runTest {
        val activity = FineTileActivity(graceMs, this)
        activity.begin()
        activity.end()

        // The next tile begins well inside the grace: the window must never fall between the two.
        advanceTimeBy(graceMs / 2)
        runCurrent()
        activity.begin()
        assertTrue(activity.busy.value)

        // The first fall was cancelled, so the window is still up a full grace after it would have fallen.
        advanceTimeBy(graceMs)
        runCurrent()
        assertTrue("the second tile reset the window rather than letting it fall", activity.busy.value)

        activity.end()
        advanceTimeBy(graceMs + 1)
        runCurrent()
        assertFalse(activity.busy.value)
    }

    @Test
    fun overlappingWorkHoldsUntilTheLastEnd() = runTest {
        val activity = FineTileActivity(graceMs, this)

        activity.begin()
        activity.begin()
        activity.end()
        advanceTimeBy(graceMs + 1)
        runCurrent()
        assertTrue("one unit still in flight holds the window", activity.busy.value)

        activity.end()
        advanceTimeBy(graceMs + 1)
        runCurrent()
        assertFalse(activity.busy.value)
    }

    /**
     * **Each window emits exactly one `busy on` and one `busy off`, whatever the tile count.** The line is
     * tied to the window's edge, not to a `begin`: a second tile inside the grace merges into the open
     * window, so a device pass shows one pair per arm and its gap is the pass's own building window.
     */
    @Test
    fun theTraceEmitsOneLinePerWindowEdge() = runTest {
        val trace = mutableListOf<String>()
        val activity = FineTileActivity(graceMs, this, trace = { trace += it })

        // One tile: the window rises once and falls once, a grace later.
        activity.begin()
        activity.end()
        advanceTimeBy(graceMs + 1)
        runCurrent()
        assertEquals(listOf("busy on", "busy off"), trace)

        // Two tiles with a gap inside the grace share the one window — no second on/off pair.
        trace.clear()
        activity.begin()
        activity.end()
        advanceTimeBy(graceMs / 2)
        runCurrent()
        activity.begin()
        activity.end()
        advanceTimeBy(graceMs + 1)
        runCurrent()
        assertEquals(listOf("busy on", "busy off"), trace)
    }
}
