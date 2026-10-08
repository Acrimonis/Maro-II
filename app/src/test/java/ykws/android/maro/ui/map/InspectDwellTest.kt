package ykws.android.maro.ui.map

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the inspect quiet clock (plan §1, §2): one wait over one key.
 *
 * A candidate change, any activity the user performed and a panel taking the screen are all the same
 * event to the clock — a new key, which restarts the wait from the full dwell. The wait that now
 * expires is the quiet **before the recentre**: with a target acquired under the anchor and the map
 * stood still, the mode moves the camera onto it (and freezes the ladder). A wait that completes while
 * a panel is open is dropped, so the key emitted when the panel closes starts a fresh wait rather than
 * resuming the one the panel suspended.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InspectDwellTest {

    private val dwellMs = 666L

    private fun key(
        candidateId: String? = "T1",
        activity: Int = 1,
        panelOpen: Boolean = false
    ) = InspectDwellKey(candidateId = candidateId, activity = activity, panelOpen = panelOpen)

    @Test
    fun `the wait expires after the dwell when the key stands still`() = runTest {
        val keys = MutableSharedFlow<InspectDwellKey>(extraBufferCapacity = 8)
        val recentred = mutableListOf<String?>()
        val job = launch { inspectDwell(keys, dwellMs).collect { recentred += it.candidateId } }
        runCurrent()

        keys.emit(key())
        advanceTimeBy(dwellMs - 50)
        runCurrent()
        assertTrue("the dwell has not passed yet", recentred.isEmpty())

        advanceTimeBy(100)
        runCurrent()
        assertEquals(listOf("T1"), recentred)
        job.cancel()
    }

    @Test
    fun `an activity bump restarts the wait`() = runTest {
        val keys = MutableSharedFlow<InspectDwellKey>(extraBufferCapacity = 8)
        val recentred = mutableListOf<String?>()
        val job = launch { inspectDwell(keys, dwellMs).collect { recentred += it.candidateId } }
        runCurrent()

        keys.emit(key(activity = 1))
        advanceTimeBy(dwellMs - 60)
        runCurrent()
        // The user does something that is neither a map motion nor a panel change — a lock flip, a
        // layer chip, a settings write — and the wait that was nearly spent is cancelled.
        keys.emit(key(activity = 2))
        advanceTimeBy(dwellMs - 60)
        runCurrent()
        assertTrue("a bump must restart the wait, not let it expire", recentred.isEmpty())

        advanceTimeBy(120)
        runCurrent()
        assertEquals(listOf("T1"), recentred)
        job.cancel()
    }

    @Test
    fun `a new candidate restarts the wait`() = runTest {
        val keys = MutableSharedFlow<InspectDwellKey>(extraBufferCapacity = 8)
        val recentred = mutableListOf<String?>()
        val job = launch { inspectDwell(keys, dwellMs).collect { recentred += it.candidateId } }
        runCurrent()

        keys.emit(key(candidateId = "T1", activity = 1))
        advanceTimeBy(dwellMs - 60)
        runCurrent()
        keys.emit(key(candidateId = "M2", activity = 1))
        advanceTimeBy(dwellMs - 60)
        runCurrent()
        assertTrue("the gold moved, so the wait restarts", recentred.isEmpty())

        advanceTimeBy(120)
        runCurrent()
        assertEquals(listOf("M2"), recentred)
        job.cancel()
    }

    @Test
    fun `a panel suspends the wait and its close starts a fresh one`() = runTest {
        val keys = MutableSharedFlow<InspectDwellKey>(extraBufferCapacity = 8)
        val recentred = mutableListOf<String?>()
        val job = launch { inspectDwell(keys, dwellMs).collect { recentred += it.candidateId } }
        runCurrent()

        keys.emit(key(activity = 1))
        advanceTimeBy(dwellMs - 60)
        runCurrent()
        // A panel opens with the wait nearly spent: what was running is dropped, and the camera may
        // not be moved behind a panel however long it stays open.
        keys.emit(key(activity = 1, panelOpen = true))
        advanceTimeBy(dwellMs * 3)
        runCurrent()
        assertTrue("nothing may be recentred behind a panel", recentred.isEmpty())

        // The close starts again from a full dwell rather than resuming where the wait stopped.
        keys.emit(key(activity = 1, panelOpen = false))
        advanceTimeBy(dwellMs - 60)
        runCurrent()
        assertTrue("the close must start a fresh wait, not resume the suspended one", recentred.isEmpty())

        advanceTimeBy(120)
        runCurrent()
        assertEquals(listOf("T1"), recentred)
        job.cancel()
    }
}
