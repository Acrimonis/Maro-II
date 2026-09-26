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
import ykws.android.maro.spatial.RouteStage
import ykws.android.maro.ui.components.ConfirmAction
import ykws.android.maro.ui.components.ConfirmActionButton
import ykws.android.maro.ui.components.ConfirmActionRole
import ykws.android.maro.ui.components.StatCell

// ─────────────────────────────────────────────────────────────────────────────
// The route's panel, hosted by the dashboard slot — one layout, two phases
//
// While the mode is armed the dashboard's own slot shows this instead of the indicator grid: the
// title's row with the phase's own status, the phase's comment, then the data table, then the controls
// — the pin — with the actions anchored at the panel's **bottom**, and nothing floating over the map
// the aim is being dragged across. **Both phases have their own content** (R16, R17) and the panel is
// the same panel: it branches on the phase rather than the screen holding two of them.
//
// **The anatomy.** A header row — the phase's title left, its short state word right, a top-right
// reading that costs no line of its own — then the phase's own comment, then the card's 0.5 dp rule,
// then the **stage** (or the refusal, or the plain searching word) on the sentence line the panel
// already carried, then the route's whole **data table** on the card's own cell ([StatCell]): the two
// ends as their own labelled rows, `Dist · ETA` as one row of two. The four details R24 names are all
// there. A **second rule** closes the table, the pin stands under it, and the actions are
// **bottom-anchored**: the content scrolls in its own weighted block, so the outcomes sit at the
// panel's foot however short the table is (a rule names the panel's own entry in
// docs/ui-component-guidelines.md §5.8).
//
// **A button's colour states its role, never its importance** (§5.6): the accent is the surface's own
// outcome — `Acquire route` while there is nothing to confirm, `Confirm` once a plan stands,
// `Save track` while a route is followed — the outline is everything that neither writes nor loses,
// and the red, which lives on the exit dialog alone, is the action that withholds the work. One door
// leaves the mode in both phases and it reads `Exit`.
//
// **The action matrix is a grid of four per phase, and only the enabled set changes** (R16, R17): the
// acquisition always draws `Acquire route` · `Confirm` · `Save track` · `Exit`, and the following
// phase `Save track` · `Reroute` · `New route` · `Exit`. A disabled action is §5.6's own face — the
// outlined role with a muted label and no accent surviving it — so the accent always names the one
// enabled forward action rather than being dimmed into ambiguity.
//
// It reuses what already exists rather than re-drawing it: [ConfirmActionButton] for the outcomes,
// [StatCell] for the readings and [RoutePinOption] for the pin, so the styling and the tick each keep
// one home.
//
// **The panel never raises a dialog itself.** Leaving the following phase asks first (R23), and the
// panel's own **Exit** is one of the doors — it asks through [onExit], the screen hosting the one
// dialog, so no second dialog exists for the second door. Inside the acquisition that same [onExit] is
// a phase move, which is the screen's rule rather than this panel's.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The route's panel, for the dashboard slot.
 *
 * @param stage             the acquisition's own stage, or null when nothing is running (R15) — the
 *                          stage rides the sentence line, never the header's corner.
 * @param pinned            the pin state the saves start with — held by the screen, because the exit
 *                          dialog's own save reads it and the dialog is the screen's.
 * @param frontSaved        whether the **front** route is already written (R16, R17): the one fact both
 *                          `Save track` actions read to grey themselves.
 * @param onAcquire         **Acquire route**: one acquisition from the aim at that instant, never
 *                          disabled.
 * @param onConfirm         **Confirm**: enabled only while a plan stands; leaves the acquisition.
 * @param onSaveTrack       **Save track**, in both phases: writes the front route.
 * @param onReroute         **Reroute**: back to the acquisition, to the same destination, one
 *                          acquisition fired at once.
 * @param onNewRoute        **New route**: back to the acquisition with the destination cleared.
 * @param onExit            **Exit**: the acquisition's phase move, or the following phase's dialog —
 *                          the screen's own rule.
 *
 * Renders nothing at all when the machine is idle: the slot belongs to the dashboard whenever no route
 * is being acquired or followed.
 */
@Composable
internal fun RouteConfirmationPanel(
    state: RouteState,
    stage: RouteStage?,
    pinned: Boolean,
    frontSaved: Boolean,
    onPinnedChange: (Boolean) -> Unit,
    onAcquire: () -> Unit,
    onConfirm: () -> Unit,
    onSaveTrack: () -> Unit,
    onReroute: () -> Unit,
    onNewRoute: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (state) {
        is RouteState.Choosing -> AcquiringPanel(
            acquiring = state,
            stage = stage,
            pinned = pinned,
            frontSaved = frontSaved,
            onPinnedChange = onPinnedChange,
            onAcquire = onAcquire,
            onConfirm = onConfirm,
            onSaveTrack = onSaveTrack,
            onExit = onExit,
            modifier = modifier
        )

        is RouteState.Following -> RouteActivePanel(
            following = state,
            pinned = pinned,
            frontSaved = frontSaved,
            onPinnedChange = onPinnedChange,
            onSaveTrack = onSaveTrack,
            onReroute = onReroute,
            onNewRoute = onNewRoute,
            onExit = onExit,
            modifier = modifier
        )

        RouteState.Idle -> return
    }
}

/**
 * **Acquiring the destination** (R16): the title and its own `Acquiring…` word, the comment the phase
 * asks of the user, then the stage (or the refusal) while nothing has resolved — and, once a route
 * stands, the data table with both ends and `Dist · ETA`, the pin the save would start with, and the
 * four outcomes at the panel's foot.
 *
 * **Nothing is computed until `Acquire route` is pressed** (R2), so the actions are all present from the
 * first frame: `Acquire route` carries the accent while there is nothing to confirm and `Confirm` is
 * disabled, and the accent moves to `Confirm` the moment a plan lands.
 */
@Composable
private fun AcquiringPanel(
    acquiring: RouteState.Choosing,
    stage: RouteStage?,
    pinned: Boolean,
    frontSaved: Boolean,
    onPinnedChange: (Boolean) -> Unit,
    onAcquire: () -> Unit,
    onConfirm: () -> Unit,
    onSaveTrack: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val plan = acquiring.plan
    // The header's short state word (R15): the acquisition says it is working only while it is.
    val status = if (acquiring.searching) stringResource(R.string.route_status_acquiring) else null
    val sentence = acquiringSentence(acquiring, stage)

    PanelColumn(
        modifier = modifier,
        content = {
            PanelHeader(title = stringResource(R.string.route_acq_title), status = status)
            PanelSentence(stringResource(R.string.route_acq_comment))
            // The live sentence keeps its own slot under the rule, so the static comment above and the
            // stage or the refusal below never stand together (R15).
            sentence?.let {
                PanelDivider()
                PanelSentence(it)
            }
            if (plan != null) {
                PanelDivider()
                PanelDataTable(plan)
                PlanNotes(plan)
                PanelDivider()
                RoutePinOption(pinned = pinned, onPinnedChange = onPinnedChange)
            }
        },
        actions = {
            // Two rows of two, so the four outcomes fit the slot the dashboard occupies in portrait.
            // The accent is the acquisition's own forward action: `Acquire route` while there is
            // nothing to confirm, `Confirm` once a plan stands.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ConfirmActionButton(
                    action = ConfirmAction(
                        label = stringResource(R.string.route_action_acquire),
                        role = if (plan == null) ConfirmActionRole.PRIMARY
                        else ConfirmActionRole.SECONDARY,
                        onClick = onAcquire
                    ),
                    modifier = Modifier.weight(1f)
                )
                ConfirmActionButton(
                    action = ConfirmAction(
                        label = stringResource(R.string.route_action_confirm),
                        role = if (plan == null) ConfirmActionRole.SECONDARY
                        else ConfirmActionRole.PRIMARY,
                        enabled = plan != null,
                        onClick = onConfirm
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
                        label = stringResource(R.string.route_action_save_track),
                        role = ConfirmActionRole.SECONDARY,
                        enabled = plan != null && !frontSaved,
                        onClick = onSaveTrack
                    ),
                    modifier = Modifier.weight(1f)
                )
                ConfirmActionButton(action = exitAction(onExit), modifier = Modifier.weight(1f))
            }
        }
    )
}

/**
 * **Following a route** (R17): the same anatomy with its own `Route active` status in the header's own
 * right corner, the data table, the pin, and the four outcomes at the panel's foot — `Save track` in the
 * accent and greyed once the front route is written, `Reroute`, `New route` and `Exit` outlined.
 *
 * `Save track` is the one outcome this panel owns and takes the accent; leaving reads `Exit` and writes
 * nothing — the dialog it raises is where the write and the discard both live. `Reroute` and
 * `New route` are both moves back into the acquisition, which is why the boat stops following until
 * `Confirm` is pressed again.
 */
@Composable
private fun RouteActivePanel(
    following: RouteState.Following,
    pinned: Boolean,
    frontSaved: Boolean,
    onPinnedChange: (Boolean) -> Unit,
    onSaveTrack: () -> Unit,
    onReroute: () -> Unit,
    onNewRoute: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val plan = following.plan

    PanelColumn(
        modifier = modifier,
        content = {
            PanelHeader(
                title = stringResource(R.string.route_following_active),
                status = stringResource(R.string.route_status_active)
            )
            PanelDivider()
            PanelDataTable(plan)
            PlanNotes(plan)
            PanelDivider()
            RoutePinOption(pinned = pinned, onPinnedChange = onPinnedChange)
        },
        actions = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ConfirmActionButton(
                    action = ConfirmAction(
                        label = stringResource(R.string.route_action_save_track),
                        role = ConfirmActionRole.PRIMARY,
                        enabled = !frontSaved,
                        onClick = onSaveTrack
                    ),
                    modifier = Modifier.weight(1f)
                )
                ConfirmActionButton(
                    action = ConfirmAction(
                        label = stringResource(R.string.route_action_reroute),
                        role = ConfirmActionRole.SECONDARY,
                        onClick = onReroute
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
                        label = stringResource(R.string.route_action_new),
                        role = ConfirmActionRole.SECONDARY,
                        onClick = onNewRoute
                    ),
                    modifier = Modifier.weight(1f)
                )
                ConfirmActionButton(action = exitAction(onExit), modifier = Modifier.weight(1f))
            }
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

/** The panel's one sentence: the phase's comment, the running stage, or the state while nothing stands. */
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
 * cell gives the label a third and the value the rest, so the pair prints whole and the destination's
 * own note rides beside it when the engine resolved the aim elsewhere. `Dist · ETA` then share one row
 * of halves, the two figures being short.
 *
 * The time is the plan's own — the trip figure's pace belongs to the dashboard's distance cell, not to
 * the panel that locks the route.
 */
@Composable
private fun PanelDataTable(plan: RoutePlan) {
    val course = plan.remainingFrom(plan.start)

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
                    routeEtaText(course.durationSec)
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
 * The one door that leaves the mode — `Exit` in both phases (R16, R17).
 *
 * The word is specialized rather than cancelled: this button ends a *mode*, where `action_cancel` is
 * the app's generic dialog abort. What the door then means is the screen's rule — a phase move out of
 * the acquisition, the one dialog out of the following phase (R23).
 */
@Composable
private fun exitAction(onExit: () -> Unit): ConfirmAction = ConfirmAction(
    label = stringResource(R.string.route_action_exit),
    role = ConfirmActionRole.SECONDARY,
    onClick = onExit
)

/**
 * The sentence the acquisition shows under the comment — the state's own words, or null when it has
 * nothing to add.
 *
 * A refused **anchor** outranks everything (there is no start to route from, and it reads as a line
 * about the position rather than on the target, R7); a refused **aim** names its reason with the
 * outcomes hidden (R6); then the **stage** the engine publishes while a search runs (R15), then the two
 * readings a null plan can mean — a search in flight and an aim no route answers. With nothing asked
 * yet the slot is empty, the comment above being the whole of what the phase says.
 */
@Composable
private fun acquiringSentence(acquiring: RouteState.Choosing, stage: RouteStage?): String? = when {
    acquiring.originRefusal != null -> stringResource(
        R.string.route_origin_invalid,
        stringResource(acquiring.originRefusal.labelResId)
    )

    acquiring.refusal != null -> stringResource(
        R.string.route_destination_invalid,
        stringResource(acquiring.refusal.labelResId)
    )

    stage != null -> stringResource(stage.labelResId)
    acquiring.searching -> stringResource(R.string.route_searching)
    acquiring.asked -> stringResource(R.string.route_no_route)
    else -> null
}
