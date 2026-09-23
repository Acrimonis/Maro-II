package ykws.android.maro.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ykws.android.maro.R
import ykws.android.maro.config.AppConfig
import ykws.android.maro.ui.components.ConfirmAction
import ykws.android.maro.ui.components.ConfirmActionButton
import ykws.android.maro.ui.components.ConfirmActionRole
import ykws.android.maro.ui.components.StatCell

// ─────────────────────────────────────────────────────────────────────────────
// The route's panel, hosted by the dashboard slot — one layout, two phases
//
// While the mode is armed the dashboard's own slot shows this instead of the indicator grid: the
// title's row with the phase's own status, then the data table, then the controls — the pin — with the
// actions anchored at the panel's **bottom**, and nothing floating over the map the aim is being
// dragged across. **Both phases have their own content** (R16, R17) and the panel is the same panel: it
// branches on the phase rather than the screen holding two of them.
//
// **The anatomy.** A header row — the phase's title left, its status right, a top-right reading that
// costs no line of its own — then the card's 0.5 dp rule, then the route's whole **data table** on the
// card's own cell ([StatCell]): the two ends as their own labelled rows, `Dist · ETA` as one row of
// two. The four details R24 names are all there — start and destination carry the coordinates that used
// to ride the header, distance and ETA the figures. A **second rule** closes the table, the pin stands
// under it, and the actions are **bottom-anchored**: the content scrolls in its own weighted block, so
// the outcomes sit at the panel's foot however short the table is (a rule names the panel's own entry
// in docs/ui-component-guidelines.md §5.8).
//
// **A button's colour states its role, never its importance** (§5.6): the accent is the surface's own
// outcome — `Route` while the aim is placed, `Save track` while a route is followed — the outline is
// everything that neither writes nor loses, the red — which lives on the exit dialog alone — is the
// action that withholds the work. One door leaves the mode in both phases and it reads `Exit`.
//
// It reuses what already exists rather than re-drawing it: [ConfirmActionButton] for the outcomes,
// [StatCell] for the readings and [RoutePinOption] for the pin, so the styling and the tick each keep
// one home.
//
// **The panel never raises a dialog itself.** Leaving the following phase asks first (R23), and the
// panel's own **Exit** is one of the two doors — it asks through [onRequestExit], the screen hosts the
// one dialog, so no second dialog exists for the second door. Leaving the draft asks nothing, which is
// why its Exit simply ends the mode.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The route's panel, for the dashboard slot.
 *
 * @param pinned            the pin state the saves start with — held by the screen, because the exit
 *                          dialog's own save reads it and the dialog is the screen's.
 * @param onRoute           **Route**: follows the aimed route without saving it.
 * @param onSaveRoute       **Save track and Route**: writes the track, then follows.
 * @param onSaveOnly        **Save track and End**: writes the track and ends the mode.
 * @param onCancel          the draft's **Exit** — leaves the mode and asks nothing (R23).
 * @param onFreezeResume    **Freeze/Resume** while following.
 * @param onAbortRefresh    **Abort** while a refresh runs.
 * @param onSaveTrack       **Save track** while following.
 * @param onRequestExit     **Exit** while following — raises the screen's one exit dialog.
 *
 * Renders nothing at all when the machine is idle: the slot belongs to the dashboard whenever no route
 * is being aimed or followed.
 */
@Composable
internal fun RouteConfirmationPanel(
    state: RouteState,
    pinned: Boolean,
    onPinnedChange: (Boolean) -> Unit,
    onRoute: () -> Unit,
    onSaveRoute: () -> Unit,
    onSaveOnly: () -> Unit,
    onCancel: () -> Unit,
    onFreezeResume: () -> Unit,
    onAbortRefresh: () -> Unit,
    onSaveTrack: () -> Unit,
    onRequestExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (state) {
        is RouteState.Choosing -> ChoosingPanel(
            choosing = state,
            pinned = pinned,
            onPinnedChange = onPinnedChange,
            onRoute = onRoute,
            onSaveRoute = onSaveRoute,
            onSaveOnly = onSaveOnly,
            onCancel = onCancel,
            modifier = modifier
        )

        is RouteState.Following -> FollowingPanel(
            following = state,
            onFreezeResume = onFreezeResume,
            onAbortRefresh = onAbortRefresh,
            onSaveTrack = onSaveTrack,
            onRequestExit = onRequestExit,
            modifier = modifier
        )

        RouteState.Idle -> return
    }
}

/**
 * **Choosing the destination** (R16): the title's row, the state's one sentence while nothing has
 * resolved, then — once a route has — the data table with both ends and `Dist · ETA`, the pin the save
 * would start with, and the four outcomes at the panel's foot.
 *
 * A phase with nothing resolved offers **only the way out** — there is no route to lock and none to
 * save — and the sentence is the one its own state deserves: the origin's refusal first (there is no
 * anchor to route from), then the aim's own refusal with the crosshair painted by the map, then
 * "Computing…" or "no route to this aim", and the arming hint while nothing has been asked at all. No
 * rule and no table are drawn there either: there is nothing yet to tabulate.
 */
@Composable
private fun ChoosingPanel(
    choosing: RouteState.Choosing,
    pinned: Boolean,
    onPinnedChange: (Boolean) -> Unit,
    onRoute: () -> Unit,
    onSaveRoute: () -> Unit,
    onSaveOnly: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val plan = choosing.plan

    PanelColumn(
        modifier = modifier,
        content = {
            // The phase carries no status: what it has to say while nothing has resolved is the
            // sentence below, and once a route stands there is nothing left to report about it.
            PanelHeader(title = stringResource(R.string.route_dialog_title), status = null)

            if (plan == null) {
                PanelSentence(choosingSentence(choosing))
            } else {
                PanelDivider()
                PanelDataTable(plan)
                PlanNotes(plan)
                PanelDivider()
                RoutePinOption(pinned = pinned, onPinnedChange = onPinnedChange)
            }
        },
        actions = {
            if (plan == null) {
                ConfirmActionButton(action = exitAction(onCancel), modifier = Modifier.fillMaxWidth())
            } else {
                // Two rows of two, so the four outcomes fit the slot the dashboard occupies in
                // portrait without the panel having to scroll to be usable. `Route` is the panel's own
                // outcome and takes the accent; the two saves write the track and stay outlined.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ConfirmActionButton(
                        action = ConfirmAction(
                            label = stringResource(R.string.route_action_route),
                            role = ConfirmActionRole.PRIMARY,
                            onClick = onRoute
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    ConfirmActionButton(
                        action = ConfirmAction(
                            label = stringResource(R.string.route_action_save_route),
                            role = ConfirmActionRole.SECONDARY,
                            onClick = onSaveRoute
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ConfirmActionButton(
                        action = ConfirmAction(
                            label = stringResource(R.string.route_action_save_only),
                            role = ConfirmActionRole.SECONDARY,
                            onClick = onSaveOnly
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    ConfirmActionButton(action = exitAction(onCancel), modifier = Modifier.weight(1f))
                }
            }
        }
    )
}

/**
 * **Following a route** (R17): the same anatomy with its **status** in the header's own right corner,
 * the data table, and the three actions at the panel's foot — with the first changing while a refresh
 * runs (**Abort**) and while it is frozen (**Resume**), each label naming the action it offers.
 *
 * The status is the one line the dashboard carries while the refresh cycle works (R15): it says the
 * route is current, that a recompute is running, or that the session's freeze is holding the gate's
 * clock still. `Save track` is the one outcome this panel owns and takes the accent; leaving reads
 * `Exit` and writes nothing — the dialog it raises is where the write and the discard both live.
 */
@Composable
private fun FollowingPanel(
    following: RouteState.Following,
    onFreezeResume: () -> Unit,
    onAbortRefresh: () -> Unit,
    onSaveTrack: () -> Unit,
    onRequestExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val plan = following.plan
    val refreshing = following.refresh == RouteRefresh.RUNNING
    val status = stringResource(
        when {
            following.frozen -> R.string.route_status_frozen
            refreshing -> R.string.route_following_recomputing
            else -> R.string.route_status_current
        }
    )

    PanelColumn(
        modifier = modifier,
        content = {
            PanelHeader(title = stringResource(R.string.route_following_active), status = status)
            PanelDivider()
            PanelDataTable(plan)
            PlanNotes(plan)
        },
        actions = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ConfirmActionButton(
                    action = if (refreshing) {
                        ConfirmAction(
                            label = stringResource(R.string.route_action_abort),
                            role = ConfirmActionRole.SECONDARY,
                            onClick = onAbortRefresh
                        )
                    } else {
                        ConfirmAction(
                            label = stringResource(
                                if (following.frozen) R.string.action_resume
                                else R.string.route_action_freeze
                            ),
                            role = ConfirmActionRole.SECONDARY,
                            onClick = onFreezeResume
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
                ConfirmActionButton(
                    action = ConfirmAction(
                        label = stringResource(R.string.route_action_save_track),
                        role = ConfirmActionRole.PRIMARY,
                        onClick = onSaveTrack
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
            ConfirmActionButton(action = exitAction(onRequestExit), modifier = Modifier.fillMaxWidth())
        }
    )
}

// ── The panel's blocks, one control each ─────────────────────────────────────

/**
 * The panel's own surface: the dashboard's background on its own gutters.
 *
 * [content] is the block that scrolls — the header, the rules and the table — and it takes the slack,
 * which is what anchors [actions] to the panel's **bottom** however short the table is. A panel whose
 * content outgrows the slot scrolls inside its own block rather than pushing the outcomes off screen.
 */
@Composable
private fun PanelColumn(
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
    actions: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .background(Color(AppConfig.uiDashboardBackground))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            content()
        }
        Spacer(Modifier.height(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            actions()
        }
    }
}

/**
 * The panel's first row: the phase's title on the left and, where the phase has one, its **status** in
 * the right corner — the one reading that costs no line of its own.
 *
 * The title keeps a single line and yields the width before the status does: a title that is cut is a
 * word a reader can finish, while a status read short would say the wrong thing about the route.
 */
@Composable
private fun PanelHeader(title: String, status: String?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = Color(AppConfig.uiDashboardTextPrimary),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        status?.let {
            Text(
                text = it,
                color = Color(AppConfig.uiDashboardTextPrimary),
                fontSize = 13.sp,
                maxLines = 1
            )
        }
    }
}

/** The panel's one sentence: the state while nothing has resolved, under the title's own row. */
@Composable
private fun PanelSentence(text: String) {
    Text(
        text = text,
        color = Color(AppConfig.uiDashboardTextPrimary),
        fontSize = 13.sp,
        modifier = Modifier.fillMaxWidth()
    )
}

/** The card's own divider — 0.5 dp of `uiDividerColor` on the panel's 6 dp stack rhythm. */
@Composable
private fun PanelDivider() {
    HorizontalDivider(
        thickness = 0.5.dp,
        color = Color(AppConfig.uiDividerColor)
    )
}

/**
 * **The route's data table** — the four details R24 names, on the card's own cell.
 *
 * The two ends take a **row each**, which is the width a four-decimal coordinate pair needs: the card's
 * cell gives the label a third and the value the rest, so `43.1234, 7.1234` prints whole and the
 * destination's own note rides beside it when the engine resolved the aim elsewhere. `Dist · ETA` then
 * share one row of halves, the two figures being short.
 *
 * The time is the plan's own — the trip figure's pace belongs to the dashboard's distance cell, not to
 * the panel that locks the route.
 */
@Composable
private fun PanelDataTable(plan: RoutePlan) {
    val course = plan.remainingFrom(plan.start)
    val whole = course.durationSec.toInt()

    // **The card's own grid, two columns**: `Start` beside `Destination`, then `Dist` beside `ETA`, each
    // reading on a cell of the same shape — label right-aligned in its third, value left-aligned in its
    // two — and the rows touching, which is the tidy rhythm the tracks card's stats read with.
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.weight(1f)) {
                StatCell(
                    stringResource(R.string.route_label_start),
                    routeCoordinate(plan.start)
                )
            }
            Box(Modifier.weight(1f)) {
                StatCell(
                    stringResource(R.string.route_label_destination),
                    routeCoordinate(plan.destination)
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.weight(1f)) {
                StatCell(
                    stringResource(R.string.track_stat_dist),
                    stringResource(R.string.route_trip_distance_nm, plan.distanceNm)
                )
            }
            Box(Modifier.weight(1f)) {
                StatCell(
                    stringResource(R.string.route_label_eta),
                    stringResource(R.string.route_eta_value_fmt, whole / 60, whole % 60)
                )
            }
        }
    }
}

/**
 * **The two notes a route can carry, and only where they are true**: the engine's own note that the
 * route really ends away from the aim — drawn as a bracketed footnote, the pair above it being where
 * the ends themselves read — and the crossing of a zone no way around avoids.
 *
 * **A crossing is said before the route is locked.** The panel is where the boat decides, so a route
 * that has to enter a zone — because no way around exists, or because one end of it stands inside —
 * names the zone it enters rather than presenting the crossing as an ordinary one.
 */
@Composable
private fun PlanNotes(plan: RoutePlan) {
    if (plan.destinationMoved) {
        Text(
            text = "(${stringResource(R.string.route_destination_moved)})",
            color = Color(AppConfig.uiDashboardTextPrimary),
            fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth()
        )
    }
    val names = plan.forcedCrossingZoneNames
    if (names.isNotEmpty()) {
        Text(
            text = stringResource(R.string.route_forced_crossing, names.joinToString(", ")),
            color = Color(AppConfig.uiDashboardTextPrimary),
            fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * The one door that leaves the mode — `Exit` in both phases (R16, R23).
 *
 * The word is specialized rather than cancelled: this button ends a *mode*, where `action_cancel` is
 * the app's generic dialog abort. Whether the door asks first is the dialog's business — it does once
 * a route is followed and something can be withheld, and it does not while the draft holds nothing.
 */
@Composable
private fun exitAction(onExit: () -> Unit): ConfirmAction = ConfirmAction(
    label = stringResource(R.string.route_action_exit),
    role = ConfirmActionRole.SECONDARY,
    onClick = onExit
)

/**
 * The sentence the choosing phase shows while no route has resolved — the state's own words.
 *
 * A refused **origin** outranks everything (there is no anchor to route from, and it reads as a line
 * about the position rather than on the target, R7); a refused **aim** names its reason with the
 * outcomes staying hidden (R6); then the three readings a null plan can mean — nothing asked yet, a
 * search in flight, and an aim no route answers.
 */
@Composable
private fun choosingSentence(choosing: RouteState.Choosing): String = when {
    choosing.originRefusal != null -> stringResource(
        R.string.route_origin_invalid,
        stringResource(choosing.originRefusal.labelResId)
    )

    choosing.refusal != null -> stringResource(
        R.string.route_destination_invalid,
        stringResource(choosing.refusal.labelResId)
    )

    choosing.searching -> stringResource(R.string.route_searching)
    choosing.asked -> stringResource(R.string.route_no_route)
    else -> stringResource(R.string.route_aim_hint)
}
