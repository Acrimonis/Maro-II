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
 * One door of the route exit dialog, resolved — its label, its role and whether it can be taken.
 *
 * [labelRes] is the label's own string id, [role] the family's visual rank, and [enabled] the one
 * disabled state the whole app shares (§5.6) — which is why a door with no work is disabled rather than
 * renamed, and why the accent falls to the enabled forward outcome rather than staying on a dead one.
 */
internal data class RouteExitDoor(
    @StringRes val labelRes: Int,
    val role: ConfirmActionRole,
    val enabled: Boolean = true
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
        RouteExitDoor(labelRes = R.string.route_exit_discard, role = ConfirmActionRole.DANGER)
    }
)
