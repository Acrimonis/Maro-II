package ykws.android.maro.ui.map

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the inspect trigger clock (plan §5, §8): one wait over one key.
 *
 * A candidate change, any activity the user performed, a lift and a panel taking the screen are all
 * the same event to the clock — a new key, which restarts the wait from the full dwell. A wait that
 * completes while a panel is open is dropped, so the key emitted when the panel closes starts a fresh
 * wait rather than resuming the one the panel suspended.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InspectDwellTest {

    private val dwellMs = 666L

    private fun key(
        candidateId: String? = "T1",
        activity: Int = 1,
        panelOpen: Boolean = false,
        lift: Boolean = false
    ) = InspectDwellKey(candidateId = candidateId, activity = activity, panelOpen = panelOpen, lift = lift)

    /** The candidate ids the clock let through, in order. */
    private class Picks {
        val ids = mutableListOf<String?>()
    }

    @Test
    fun `the wait expires after the dwell when the key stands still`() = runTest {
        val keys = MutableSharedFlow<InspectDwellKey>(extraBufferCapacity = 8)
        val picked = mutableListOf<String?>()
        val job = launch { inspectDwell(keys, dwellMs).collect { picked += it.candidateId } }
        runCurrent()

        keys.emit(key())
        advanceTimeBy(dwellMs - 50)
        runCurrent()
        assertTrue("the dwell has not passed yet", picked.isEmpty())

        advanceTimeBy(100)
        runCurrent()
        assertEquals(listOf("T1"), picked)
        job.cancel()
    }

    @Test
    fun `an activity bump restarts the wait`() = runTest {
        val keys = MutableSharedFlow<InspectDwellKey>(extraBufferCapacity = 8)
        val picked = mutableListOf<String?>()
        val job = launch { inspectDwell(keys, dwellMs).collect { picked += it.candidateId } }
        runCurrent()

        keys.emit(key(activity = 1))
        advanceTimeBy(dwellMs - 60)
        runCurrent()
        // The user does something that is neither a map motion nor a panel change — a lock flip, a
        // layer chip, a settings write — and the wait that was nearly spent is cancelled.
        keys.emit(key(activity = 2))
        advanceTimeBy(dwellMs - 60)
        runCurrent()
        assertTrue("a bump must restart the wait, not let it expire", picked.isEmpty())

        advanceTimeBy(120)
        runCurrent()
        assertEquals(listOf("T1"), picked)
        job.cancel()
    }

    @Test
    fun `a new candidate restarts the wait`() = runTest {
        val keys = MutableSharedFlow<InspectDwellKey>(extraBufferCapacity = 8)
        val picked = mutableListOf<String?>()
        val job = launch { inspectDwell(keys, dwellMs).collect { picked += it.candidateId } }
        runCurrent()

        keys.emit(key(candidateId = "T1", activity = 1))
        advanceTimeBy(dwellMs - 60)
        runCurrent()
        keys.emit(key(candidateId = "M2", activity = 1))
        advanceTimeBy(dwellMs - 60)
        runCurrent()
        assertTrue("the gold moved, so the wait restarts", picked.isEmpty())

        advanceTimeBy(120)
        runCurrent()
        assertEquals(listOf("M2"), picked)
        job.cancel()
    }

    @Test
    fun `a panel suspends the wait and its close starts a fresh one`() = runTest {
        val keys = MutableSharedFlow<InspectDwellKey>(extraBufferCapacity = 8)
        val picked = mutableListOf<String?>()
        val job = launch { inspectDwell(keys, dwellMs).collect { picked += it.candidateId } }
        runCurrent()

        keys.emit(key(activity = 1))
        advanceTimeBy(dwellMs - 60)
        runCurrent()
        // A panel opens with the wait nearly spent: what was running is dropped, and nothing may be
        // picked while the panel owns the screen however long it stays open.
        keys.emit(key(activity = 1, panelOpen = true))
        advanceTimeBy(dwellMs * 3)
        runCurrent()
        assertTrue("nothing may be picked behind a panel", picked.isEmpty())

        // The close starts again from a full dwell rather than resuming where the wait stopped.
        keys.emit(key(activity = 1, panelOpen = false))
        advanceTimeBy(dwellMs - 60)
        runCurrent()
        assertTrue("the close must start a fresh wait, not resume the suspended one", picked.isEmpty())

        advanceTimeBy(120)
        runCurrent()
        assertEquals(listOf("T1"), picked)
        job.cancel()
    }

    @Test
    fun `a lift is a key of its own, so a tap that never moved the map still opens a wait`() = runTest {
        // The tap that never moved the map: the candidate, the activity tick and the panel state are all
        // still, and the lift is the only thing that changes. The source is a StateFlow because it
        // republishes nothing for an equal value — exactly what `snapshotFlow` does — so with the lift
        // held outside the key, this second key would never reach the clock and no wait would be opened.
        val keys = MutableStateFlow(key(activity = 1))
        val picked = mutableListOf<String?>()
        val job = launch { inspectDwell(keys, dwellMs).collect { picked += it.candidateId } }
        runCurrent()

        // The arming seed's own wait, with no lift behind it: the last key the clock has for now.
        advanceTimeBy(dwellMs * 2)
        runCurrent()
        assertEquals(listOf("T1"), picked)
        picked.clear()

        keys.value = key(activity = 1, lift = true)
        advanceTimeBy(dwellMs - 60)
        runCurrent()
        assertTrue("the lift opens a wait of its own", picked.isEmpty())

        advanceTimeBy(120)
        runCurrent()
        // The clock is the clock: spending the lift and refusing to re-fire the same id belong to the
        // collector the mode wires to it, not to the debounce.
        assertEquals(listOf("T1"), picked)
        job.cancel()
    }
}
