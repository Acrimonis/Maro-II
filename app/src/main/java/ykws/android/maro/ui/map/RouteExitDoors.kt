package ykws.android.maro.ui.map

import androidx.annotation.StringRes
import ykws.android.maro.R
import ykws.android.maro.ui.components.ConfirmActionRole

// ─────────────────────────────────────────────────────────────────────────────
// The acquisition panel's ending door — one pure resolution home
//
// A route asks no dialog on leaving (R59, R100): the toggle's off and the back key end the mode through the
// deferred window and its Undo toast (R92). What stays is the acquisition panel's one ending door, whose face
// follows a single fact the call site reads once: *is this line already a track?* A pure function of the state,
// with no Compose call inside, so the per-state test reaches it directly and the composable only paints what
// it returns.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * One door, resolved — its label, its role, whether it can be taken and what its press does.
 *
 * [labelRes] is the label's own string id, [role] the family's visual rank (`docs/ui-component-guidelines.md`
 * §5.6), and [enabled] the one disabled state the whole app shares.
 *
 * [discards] is the **cost** behind the word: `true` only while the press would throw the line away. It is
 * not what defers — every ending defers (R92) — it only names which of the two acts the press takes: the
 * deferred discard, or the deferred leave.
 */
internal data class RouteExitDoor(
    @StringRes val labelRes: Int,
    val role: ConfirmActionRole,
    val enabled: Boolean = true,
    val discards: Boolean = false
)

/**
 * The acquisition panel's ending door for [written] — `true` once the line is already a track.
 *
 * While the line is unwritten the press would throw it away, so the door reads the acquisition's own short
 * `route_acq_discard` and is red; once it **is written** the ending costs nothing and the door reads *Leave*
 * (`route_exit_leave`) under the accent — its word follows the **cost**, never the surface that raises it.
 * The acquisition's own short word exists because this door holds a third of the footer row, where a longer
 * loss word would not fit.
 *
 * The act does not follow the cost: both endings leave through the same deferred window (R92), the discarding
 * one on its own toast and the written one on `route_left_toast`.
 */
internal fun routeAcquisitionEndingDoor(written: Boolean): RouteExitDoor =
    if (written) {
        RouteExitDoor(labelRes = R.string.route_exit_leave, role = ConfirmActionRole.PRIMARY)
    } else {
        RouteExitDoor(
            labelRes = R.string.route_acq_discard,
            role = ConfirmActionRole.DANGER,
            discards = true
        )
    }
