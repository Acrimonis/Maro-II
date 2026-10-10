package ykws.android.maro.ui.map

import androidx.annotation.StringRes
import ykws.android.maro.R
import ykws.android.maro.ui.components.ConfirmActionRole

// ─────────────────────────────────────────────────────────────────────────────
// The route exit dialog's doors — one pure resolution home
//
// The dialog's whole shape is decided by one fact the call site reads once: *is this line already a
// track?* (`isRouteSaved(plan)` or `followedTrackId != null`). The three doors then follow the family's
// own rule (ui-component-guidelines §5.6) — the forward outcome, the stay, the ending — and the one
// function below is their home, in the shape `MapToggleFace`'s resolvers already set for the row's
// squares: a pure function of the state, with no Compose call inside, so the per-state test reaches it
// directly and the composable only paints what it returns.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * One door of the route exit dialog, resolved — its label, its role, whether it can be taken and what its
 * press does.
 *
 * [labelRes] is the label's own string id, [role] the family's visual rank, and [enabled] the one
 * disabled state the whole app shares (§5.6) — which is why a door with no work is disabled rather than
 * renamed, and why the accent falls to the enabled forward outcome rather than staying on a dead one.
 *
 * [discards] is the act behind the word: `true` only while the press would throw the line away, which is
 * what makes this door the **deferred, toasting** discard (R92) rather than a plain exit.
 */
internal data class RouteExitDoor(
    @StringRes val labelRes: Int,
    val role: ConfirmActionRole,
    val enabled: Boolean = true,
    val discards: Boolean = false
)

/**
 * The route exit dialog's three doors in the family's fixed order — the forward outcome, the stay, the
 * ending — resolved from the one axis that decides them: **is this line already a track?**
 *
 * While the line is **not yet written** the save door carries the accent, enabled, and the loss door is
 * red, because the press would throw the route away. Once it **is written** nothing is lost: the save
 * door keeps its place and **disables**, the accent moves to the enabled forward outcome and the third
 * door reads *Leave* with **no red** — its word follows the **cost**, never the raiser, so the arrival cue
 * carries no vocabulary of its own and changes only its title. The label *Leave Route* exists nowhere.
 */
internal data class RouteExitDoors(
    val save: RouteExitDoor,
    val stay: RouteExitDoor,
    val loss: RouteExitDoor
) {
    /** The three doors in the order they are stacked: the forward outcome, the stay, the ending. */
    val ordered: List<RouteExitDoor> get() = listOf(save, stay, loss)
}

/**
 * The route exit dialog's three doors for [written] — `true` once the line is already a track, so the
 * save has nothing left to do and the accent belongs to the forward outcome that leaves.
 *
 * The action follows the cost as well as the word: only an ending that would throw the line away defers
 * on the toast, so a loss-free ending is the ordinary end, with no window and no toast.
 */
internal fun routeExitDoors(written: Boolean): RouteExitDoors = RouteExitDoors(
    save = RouteExitDoor(
        labelRes = R.string.route_exit_save,
        role = ConfirmActionRole.PRIMARY,
        enabled = !written
    ),
    stay = RouteExitDoor(
        labelRes = R.string.route_exit_continue,
        role = ConfirmActionRole.SECONDARY
    ),
    loss = if (written) {
        RouteExitDoor(labelRes = R.string.route_exit_leave, role = ConfirmActionRole.PRIMARY)
    } else {
        RouteExitDoor(
            labelRes = R.string.route_exit_discard,
            role = ConfirmActionRole.DANGER,
            discards = true
        )
    }
)

/**
 * The acquisition panel's ending door for [written] — **the same ending, and the same word once nothing
 * is lost**. While the line is unwritten the press would throw it away, so the door reads the
 * acquisition's own short `route_acq_discard` and is red; once it **is written** the ending costs
 * nothing, and the door hands over to [routeExitDoors]'s own loss door, *Leave* under the accent — its
 * word follows the **cost**, never the surface that raises it (§5.6).
 *
 * The written state is **derived** from that resolver rather than written a second time: one ending, one
 * word, one home. The acquisition's own short word exists only because this door holds a third of the
 * footer row, where the dialog's longer loss word would not fit.
 *
 * The action follows the cost as well as the word: only the unwritten ending would throw the selected
 * line away, so a loss-free ending is the ordinary end, with no window and no toast.
 */
internal fun routeAcquisitionEndingDoor(written: Boolean): RouteExitDoor =
    if (written) {
        routeExitDoors(written = true).loss
    } else {
        RouteExitDoor(
            labelRes = R.string.route_acq_discard,
            role = ConfirmActionRole.DANGER,
            discards = true
        )
    }
