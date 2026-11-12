package ykws.android.maro.ui.notice

/**
 * The action lane's rules, as pure reducers: the rows that carry commands (undo and the like), kept apart from
 * the pill lane so a pill can neither delay nor displace a row. A row's timeout commits what it defers — a
 * delete, a route disposal — so nothing here ever drops, reorders or delays a row beyond these rules.
 *
 * - **A free slot shows at once**, appended; a full lane queues the row behind those already waiting.
 * - **A row that [enqueue]s with `jumpsQueue` takes the front** (the route ending's confirmation window), and
 *   when that overfills the lane the last-listed visible row (the most recently appended) is pushed back to
 *   the queue's head, so the window never lengthens silently behind delete rows. This is the lane's
 *   behaviour as it has always been; its old KDoc said "oldest", which the code never did.
 * - **A row leaves only through [remove]**, which refills every free slot from the queue's head. Removing a
 *   row that is waiting, not showing, changes nothing — as the lane has always behaved.
 *
 * Generic over the row type so the rules are a unit test without Compose; `MapDashboardController` holds the
 * Compose state and adapts it.
 */
internal data class ActionLane<T>(
    /** The rows showing, in draw order. */
    val visible: List<T> = emptyList(),
    /** The rows waiting for a slot, first in line first. */
    val queued: List<T> = emptyList()
) {
    /** Adds [item], or queues it when [maxVisible] slots are taken; [jumpsQueue] puts it on the front instead. */
    fun enqueue(item: T, jumpsQueue: Boolean, maxVisible: Int): ActionLane<T> {
        if (jumpsQueue) {
            val front = listOf(item) + visible
            return if (front.size > maxVisible) {
                ActionLane(front.dropLast(1), listOf(front.last()) + queued)
            } else {
                copy(visible = front)
            }
        }
        return if (visible.size < maxVisible) copy(visible = visible + item) else copy(queued = queued + item)
    }

    /** Takes [item] off the showing rows and refills the free slots from the queue. */
    fun remove(item: T, maxVisible: Int): ActionLane<T> =
        copy(visible = visible - item).promoted(maxVisible)

    /** Pulls the queue forward into every free slot. */
    fun promoted(maxVisible: Int): ActionLane<T> {
        val free = (maxVisible - visible.size).coerceAtLeast(0)
        if (free == 0 || queued.isEmpty()) return this
        return ActionLane(visible + queued.take(free), queued.drop(free))
    }
}
