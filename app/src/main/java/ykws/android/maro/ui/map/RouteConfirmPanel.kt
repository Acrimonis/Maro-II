package ykws.android.maro.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import ykws.android.maro.R
import ykws.android.maro.config.AppConfig
import ykws.android.maro.spatial.RouteStage
import ykws.android.maro.ui.components.ConfirmAction
import ykws.android.maro.ui.components.ConfirmActionButton
import ykws.android.maro.ui.components.ConfirmActionRole
import ykws.android.maro.ui.components.StatCell

// ─────────────────────────────────────────────────────────────────────────────
// The route acquisition's own dashboard — the panel the slot carries while a route is being acquired
//
// **The acquisition has its own surface, and it is this panel in the dashboard slot** (R74): the
// status, the candidate rows and the three actions, with the drawer standing above it for the
// parameters rather than instead of it. **Nothing else route-specific is ever composed** (R73): once
// `Select route` is pressed the panel is gone and the ordinary dashboard returns, the toggle and the one
// exit dialog being the mode's whole presence from then on.
//
// **The anatomy.** A header row — the phase's title left, its short state word right, a top-right
// reading that costs no line of its own — then the phase's comment, then the card's 0.5 dp rule, then
// the **stage** (or the refusal, or the plain searching word) on the sentence line the panel already
// carried, then the **selected** line's whole **data table** on the card's own cell ([StatCell]): the
// two ends as their own labelled rows, `Dist · ETA` as one row of two. The four details R24 names are
// all there. A **second rule** closes the table, the pin stands under it, and the actions are
// **bottom-anchored**: the content scrolls in its own weighted block, so the outcomes sit at the
// panel's foot however short the table is (a rule names the panel's own entry in
// docs/ui-component-guidelines.md §5.8).
//
// **The candidate rows appear only as the offers land** (R54). The engine computes them behind the
// answer on its own lane, so the set opens at one — the settled line — and grows; the rows and the
// next/prev pair stand only from the moment there is something to step through.
//
// **A button's colour states its role, never its importance** (§5.6): the accent is the surface's own
// outcome — `Select route` once a line stands — the outline is everything that neither writes nor
// loses, and the red, which lives on the exit dialog alone, is the action that withholds the work.
//
// It reuses what already exists rather than re-drawing it: [ConfirmActionButton] for the outcomes,
// [StatCell] for the readings and [RoutePinOption] for the pin, so the styling and the tick each keep
// one home.
//
// **The panel never raises a dialog itself.** It owns no exit, so no dialog exists for it to raise.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The acquisition's panel, for the dashboard slot.
 *
 * @param stage           the acquisition's own stage, or null when nothing is running (R15) — the stage
 *                        rides the sentence line, never the header's corner.
 * @param candidates      the lines the acquisition draws, the settled answer first (R53, R54).
 * @param selectedIndex   which of [candidates] the selection stands on — the table, the notes and the
 *                        saves all describe that one line.
 * @param pinned          the pin state the saves start with — held by the screen, because the exit
 *                        dialog's own save reads it and the dialog is the screen's.
 * @param frontSaved      whether the **selected** line is already written (R55): the one fact the save
 *                        action reads to grey itself.
 * @param onStepCandidate **next/prev**: steps the selection and loops it.
 * @param onSelectRoute   **Select route**: enters navigation on the selected line (R56).
 * @param onSaveTrack     **Save to track**: writes the selected line (R55).
 * @param onCancel        **Cancel**: leaves the acquisition and turns the toggle off, asking nothing (R57).
 *
 * Renders nothing at all unless the machine is acquiring a route: the slot belongs to the dashboard
 * whenever no route is being acquired (R73).
 */
@Composable
internal fun RouteConfirmationPanel(
    state: RouteState,
    stage: RouteStage?,
    candidates: List<RoutePlan>,
    selectedIndex: Int,
    pinned: Boolean,
    frontSaved: Boolean,
    onPinnedChange: (Boolean) -> Unit,
    onStepCandidate: (Int) -> Unit,
    onSelectRoute: () -> Unit,
    onSaveTrack: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val acquiring = state as? RouteState.Choosing ?: return
    val selected = candidates.getOrNull(selectedIndex.coerceIn(0, (candidates.size - 1).coerceAtLeast(0)))
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
            if (selected != null) {
                PanelDivider()
                PanelDataTable(selected)
                PlanNotes(selected)
                PanelDivider()
                RoutePinOption(pinned = pinned, onPinnedChange = onPinnedChange)
            }
            // The candidates stand beside the settled line, and only once there is one to step to
            // (R54): the engine's own offers arrive after the answer, so the block appears with them.
            if (candidates.size > 1) {
                PanelDivider()
                CandidateBlock(
                    candidates = candidates,
                    selectedIndex = selectedIndex,
                    onStepCandidate = onStepCandidate
                )
            }
        },
        actions = {
            // R55's own three, stacked: `Save to track` · `Select route` · `Cancel`. The accent is the
            // acquisition's own forward action — entering navigation on the selected line — and the save
            // keeps the role it had.
            ConfirmActionButton(
                action = ConfirmAction(
                    label = stringResource(R.string.route_action_save_track),
                    role = ConfirmActionRole.SECONDARY,
                    enabled = selected != null && !frontSaved,
                    onClick = onSaveTrack
                ),
                modifier = Modifier.fillMaxWidth()
            )
            ConfirmActionButton(
                action = ConfirmAction(
                    label = stringResource(R.string.route_action_select),
                    role = ConfirmActionRole.PRIMARY,
                    enabled = selected != null,
                    onClick = onSelectRoute
                ),
                modifier = Modifier.fillMaxWidth()
            )
            ConfirmActionButton(
                action = ConfirmAction(
                    // The app's one word for the generic abort, shared with every other dialog and
                    // footers: `Cancel` here really is "abort, nothing happens" (R57).
                    label = stringResource(R.string.action_cancel),
                    role = ConfirmActionRole.SECONDARY,
                    onClick = onCancel
                ),
                modifier = Modifier.fillMaxWidth()
            )
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
 * own note rides beside it when the engine resolved the end elsewhere. `Dist · ETA` then share one row
 * of halves, the two figures being short.
 *
 * The table describes the **selected** line (R67's own reading, one surface over): the draw, the table
 * and the save all follow the selection, so what a user reads is what a save would write.
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
 * **The candidate rows, and the pair that steps them** (R54, R62).
 *
 * One row per line the engine offered beside the settled answer — never for the settled line itself,
 * which the table above already describes — each printing the engine's **own** figures: its duration
 * and what it saves, in time and as a share of the settled trip's clock, exactly as
 * [`routeCandidateText`] formats them. The UI recomputes nothing and asserts no basis of its own (R72):
 * what the engine measured is what is written down.
 *
 * The row the selection stands on takes `uiAccent`; the others stay in the card's own text colour, so
 * the emphasis follows the next/prev pair rather than moving the drawing (R54).
 */
@Composable
private fun CandidateBlock(
    candidates: List<RoutePlan>,
    selectedIndex: Int,
    onStepCandidate: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.route_candidates_title),
                color = Color(AppConfig.uiDashboardTextPrimary),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { onStepCandidate(-1) }, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.cd_route_candidate_prev),
                    tint = Color(AppConfig.uiAccent),
                    modifier = Modifier.size(24.dp)
                )
            }
            IconButton(onClick = { onStepCandidate(1) }, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.cd_route_candidate_next),
                    tint = Color(AppConfig.uiAccent),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        // The settled line is candidates[0] and the offers follow it, so the row's own number is its
        // position in the set — `Route #1` is the first alternative the engine offered.
        for (index in 1..candidates.lastIndex) {
            val candidate = candidates[index]
            val settled = candidates.first()
            val selected = index == selectedIndex.coerceIn(0, candidates.lastIndex)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onStepCandidate(index - selectedIndex.coerceIn(0, candidates.lastIndex)) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = routeCandidateText(index, candidate, settled),
                    color = if (selected) Color(AppConfig.uiAccent)
                    else Color(AppConfig.uiDashboardTextPrimary),
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * **One candidate's row** (R62): which alternative it is, the duration the engine gave it, and what it
 * saves against the settled trip — in time, and as a share of that trip's **own clock**.
 *
 * The UI asserts no basis of its own (R72): the saving is the difference between the two clocks the
 * engine published, and the denominator is the settled answer's own duration rather than a budget, a
 * band or a pace the surface would have to guess at. Nothing here is recomputed from the line — the
 * row prints what the engine measured.
 */
@Composable
private fun routeCandidateText(index: Int, candidate: RoutePlan, settled: RoutePlan): String {
    val savingSec = (settled.durationSec - candidate.durationSec).coerceAtLeast(0.0)
    val sharePct = if (settled.durationSec > 0.0) {
        (savingSec / settled.durationSec * 100.0).roundToInt()
    } else {
        0
    }
    return stringResource(
        R.string.route_candidate_fmt,
        index,
        routeSpanText(candidate.durationSec),
        routeSpanText(savingSec),
        sharePct
    )
}

/**
 * **The two notes a route can carry, and only where they are true**: the engine's own note that the
 * route really ends away from the end asked for — drawn as a bracketed footnote, the pair above it
 * being where the ends themselves read — and the crossing of a zone no way around avoids.
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
 * The sentence the acquisition shows under the comment — the state's own words, or null when it has
 * nothing to add.
 *
 * A refused **anchor** outranks everything (there is no start to route from, and it reads as a line
 * about the position rather than on the destination, R7); a refused **destination** names its reason
 * with the outcomes hidden (R6); then the **stage** the engine publishes while a search runs (R15), then
 * the two readings a null plan can mean — a search in flight and a pair no route answers. Nothing asked
 * yet is the comment above alone.
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
