package ykws.android.maro.ui.map

import androidx.compose.runtime.mutableStateListOf

/**
 * The map dashboard's own state — the vertical snackbar stack's three slots and its overflow queue
 * (code-health step 3, tier 2 D): the queue's arithmetic leaves [`MapScreen`](MapScreen.kt)'s body so the
 * screen keeps only the domain handlers that consume it. Held by the screen in a `remember`, so its lists
 * survive recomposition exactly as the two `mutableStateListOf`s did.
 */
internal class MapDashboardController {
    /** The visible stack, in draw order; the snackbar host paints it. */
    val activeSnacks = mutableStateListOf<ActiveSnack>()

    /** The overflow waiting for a free slot. */
    private val queuedSnacks = mutableStateListOf<ActiveSnack>()

    /**
     * Adds a snack, or queues it once the three visible slots are taken.
     *
     * **The route discard's toast jumps the queue**: it is the confirmation window for a deferred
     * disposal, so it goes to the front and the oldest visible snack is pushed back rather than
     * letting the window silently lengthen behind delete toasts.
     */
    fun enqueue(snack: ActiveSnack) {
        if (snack is ActiveSnack.RouteDiscard) {
            activeSnacks.add(0, snack)
            if (activeSnacks.size > 3) queuedSnacks.add(0, activeSnacks.removeAt(activeSnacks.size - 1))
            return
        }
        if (activeSnacks.size < 3) activeSnacks.add(snack)
        else queuedSnacks.add(snack)
    }

    /** Pulls the queue forward into every free slot. */
    fun promote() {
        while (activeSnacks.size < 3 && queuedSnacks.isNotEmpty()) {
            activeSnacks.add(queuedSnacks.removeAt(0))
        }
    }

    /** Removes a snack and refills its slot from the queue — the whole removal in one place. */
    fun remove(snack: ActiveSnack) {
        activeSnacks.remove(snack)
        promote()
    }
}
