package ykws.android.maro.ui.notice

import org.junit.Assert.assertEquals
import org.junit.Test

/** The action lane's rules — the queue that fronts the deferred deletes and route disposals. */
class ActionLaneTest {

    private fun lane(vararg items: String, max: Int = 3): ActionLane<String> =
        items.fold(ActionLane()) { acc, item -> acc.enqueue(item, jumpsQueue = false, maxVisible = max) }

    @Test
    fun aFreeSlotShowsTheRowAtOnceAndAFullLaneQueuesItInOrder() {
        val full = lane("a", "b", "c", "d", "e")

        assertEquals(listOf("a", "b", "c"), full.visible)
        assertEquals(listOf("d", "e"), full.queued)
    }

    @Test
    fun aJumpingRowTakesTheFrontAndPushesTheLastListedVisibleRowBackToTheQueuesHead() {
        val full = lane("a", "b", "c", "d")

        val after = full.enqueue("route", jumpsQueue = true, maxVisible = 3)

        assertEquals(listOf("route", "a", "b"), after.visible)
        assertEquals(listOf("c", "d"), after.queued)
    }

    @Test
    fun aJumpingRowIntoAFreeSlotPushesNothingBack() {
        val after = lane("a", "b").enqueue("route", jumpsQueue = true, maxVisible = 3)

        assertEquals(listOf("route", "a", "b"), after.visible)
        assertEquals(emptyList<String>(), after.queued)
    }

    @Test
    fun removingAShowingRowRefillsItsSlotFromTheQueuesHead() {
        val after = lane("a", "b", "c", "d", "e").remove("b", maxVisible = 3)

        assertEquals(listOf("a", "c", "d"), after.visible)
        assertEquals(listOf("e"), after.queued)
    }

    @Test
    fun removingAWaitingRowChangesNothing() {
        val full = lane("a", "b", "c", "d")

        assertEquals(full, full.remove("d", maxVisible = 3))
    }

    @Test
    fun theLaneNeverLosesARow() {
        val jumped = lane("a", "b", "c", "d", "e").enqueue("route", jumpsQueue = true, maxVisible = 3)

        assertEquals(setOf("route", "a", "b", "c", "d", "e"), (jumped.visible + jumped.queued).toSet())
        assertEquals(6, jumped.visible.size + jumped.queued.size)
    }

    @Test
    fun promotingFillsEveryFreeSlot() {
        val spaced = ActionLane(visible = listOf("a"), queued = listOf("b", "c", "d"))

        val after = spaced.promoted(maxVisible = 3)

        assertEquals(listOf("a", "b", "c"), after.visible)
        assertEquals(listOf("d"), after.queued)
    }
}
