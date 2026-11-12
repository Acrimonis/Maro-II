package ykws.android.maro.ui.map

import androidx.compose.runtime.mutableStateListOf
import ykws.android.maro.config.AppConfig
import ykws.android.maro.ui.notice.ActionLane

/**
 * The map dashboard's own state — the vertical snackbar stack's slots and its overflow queue (code-health
 * step 3, tier 2 D). The queue's rules are the pure [ActionLane]'s; this class only holds the visible rows as
 * Compose state, so the snackbar host alone recomposes when they change. Held by the screen in a `remember`,
 * so its list survives recomposition.
 */
internal class MapDashboardController(
    /** How many rows stand at once — `notice.row.maxVisible`; the rest wait in the overflow queue. */
    private val maxVisible: Int = AppConfig.noticeRowMaxVisible
) {
    /** The visible stack, in draw order; the snackbar host paints it. */
    val activeSnacks = mutableStateListOf<ActiveSnack>()

    private var lane = ActionLane<ActiveSnack>()

    /**
     * Adds a snack, or queues it once the visible slots are taken.
     *
     * **The route ending's toast jumps the queue**: it is the confirmation window for a deferred
     * disposal, so it goes to the front and the last-listed visible snack is pushed back rather than
     * letting the window silently lengthen behind delete toasts.
     */
    fun enqueue(snack: ActiveSnack) =
        publish(lane.enqueue(snack, jumpsQueue = snack is ActiveSnack.RouteEnd, maxVisible = maxVisible))

    /** Removes a snack and refills its slot from the queue — the whole removal in one place. */
    fun remove(snack: ActiveSnack) = publish(lane.remove(snack, maxVisible))

    private fun publish(next: ActionLane<ActiveSnack>) {
        lane = next
        if (activeSnacks.toList() != next.visible) {
            activeSnacks.clear()
            activeSnacks.addAll(next.visible)
        }
    }
}
