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
import kotlin.math.abs
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
// status, the page rows and the three actions. **Nothing else route-specific is ever composed** (R73):
// once `Select route` is pressed the panel is gone and the ordinary dashboard returns.
//
// **The anatomy, rebuilt 2026-09-29.** A header row — the phase's title left, its short state word
// right, the stage inside that word — then the **comparison** in the comment's old place, then the
// card's 0.5 dp rule, then the **refusal** the state has to report, on the sentence line the panel
// already carried, then the **selected** line's whole **data table** on the card's own cell, the pin,
// and the page rows with their next/prev pair. The four details R24 names are all there.
//
// **The top area is §5's three things**: the title, the stage or refusal word, and the comparison once
// a second finished page exists. The comment left with the seam rework.
//
// **A button's colour states its role, never its importance** (§5.6).
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The acquisition's panel, for the dashboard slot.
 *
 * @param stage           the main lookup's current stage, or null when nothing is running (R15).
 * @param pages           the pages the acquisition draws, the main first — one per started lookup.
 * @param selectedIndex   which of [pages] the selection stands on — the table and the saves describe
 *                        that one page.
 * @param pinned          the pin state the saves start with.
 * @param frontSaved      whether the **selected** line is already written (R55).
 * @param onStepPage      **next/prev**: steps the selection and loops it.
 * @param onSelectRoute   **Select route**: enters navigation on the selected line (R56).
 * @param onSaveTrack     **Save to track**: writes the selected line (R55).
 * @param onDiscard       **Discard route**: leaves the acquisition and turns the toggle off (R57).
 */
@Composable
internal fun RouteConfirmationPanel(
    state: RouteState,
    stage: RouteStage?,
    pages: List<RoutePage>,
    selectedIndex: Int,
    pinned: Boolean,
    frontSaved: Boolean,
    onPinnedChange: (Boolean) -> Unit,
    onStepPage: (Int) -> Unit,
    onSelectRoute: () -> Unit,
    onSaveTrack: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier
) {
    val acquiring = state as? RouteState.Choosing ?: return
    val clampedIndex = selectedIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
    val selected = pages.getOrNull(clampedIndex)
    val selectedPlan = selected?.plan
    // The header's short state word (R15): the acquisition says it is working only while it is, and the
    // **stage rides inside that word** — `Acquiring (Search)…`. A search that publishes no stage reads
    // the plain word.
    val status = if (acquiring.searching) {
        stage?.let {
            stringResource(R.string.route_status_acquiring_stage, stringResource(it.labelResId))
        } ?: stringResource(R.string.route_status_acquiring)
    } else {
        null
    }
    val refusal = acquiring.refusal ?: selected?.reason
    val comparison = routeComparison(
        selectedIndex = clampedIndex,
        selectedDurationSec = selectedPlan?.durationSec,
        mainDurationSec = pages.firstOrNull()?.plan?.durationSec
    )

    PanelColumn(
        modifier = modifier,
        content = {
            PanelHeader(title = stringResource(R.string.route_acq_title), status = status)
            // The comparison takes the comment's place once a second finished page exists.
            comparison?.let {
                PanelSentence(comparisonText(it))
            }
            // The live sentence keeps its own slot under the rule: the refusal the pair or the selected
            // page answered with.
            refusal?.let {
                PanelDivider()
                PanelSentence(stringResource(it.labelResId))
            }
            if (selectedPlan != null) {
                PanelDivider()
                PanelDataTable(selectedPlan)
                PlanNotes(selectedPlan)
                PanelDivider()
                RoutePinOption(pinned = pinned, onPinnedChange = onPinnedChange)
            }
            // The pages stand beside the main, and only once there is one to step to: each page is a
            // lookup of its own, so the block appears with the set.
            if (pages.size > 1) {
                PanelDivider()
                PageBlock(
                    pages = pages,
                    selectedIndex = clampedIndex,
                    onStepPage = onStepPage
                )
            }
        },
        actions = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ConfirmActionButton(
                    action = ConfirmAction(
                        label = stringResource(R.string.route_action_save_track),
                        role = ConfirmActionRole.SECONDARY,
                        enabled = selectedPlan != null && !frontSaved,
                        onClick = onSaveTrack
                    ),
                    modifier = Modifier.weight(1f)
                )
                ConfirmActionButton(
                    action = ConfirmAction(
                        label = stringResource(R.string.route_action_select),
                        role = ConfirmActionRole.PRIMARY,
                        enabled = selectedPlan != null,
                        onClick = onSelectRoute
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
            ConfirmActionButton(
                action = ConfirmAction(
                    label = stringResource(R.string.route_exit_discard),
                    role = ConfirmActionRole.DANGER,
                    onClick = onDiscard
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    )
}

// ── The panel's blocks, one control each ─────────────────────────────────────

/**
 * The panel's own surface: the dashboard's background on its own gutters.
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
 * the right corner.
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** The panel's one sentence: the comparison, the refusal, or the state while nothing stands. */
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
 * **The comparison one finished page shows against the main** — "Route #n is taking x less or more",
 * the flow's arithmetic of the selected page's own duration against the main's. The span is the
 * absolute difference, and the direction is the sign: a faster page reads "less", a slower one "more".
 */
@Composable
private fun comparisonText(comparison: RouteComparison): String {
    val span = routeSpanText(abs(comparison.deltaSec))
    val direction = stringResource(
        if (comparison.deltaSec < 0.0) R.string.route_comparison_less else R.string.route_comparison_more
    )
    return stringResource(R.string.route_comparison_fmt, comparison.routeNumber, span, direction)
}

/**
 * **The route's data table** — the four details R24 names, on the card's own cell.
 */
@Composable
private fun PanelDataTable(plan: RoutePlan) {
    val course = plan.remainingFrom(plan.start)

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
 * **The page rows, and the pair that steps them** (R54, R62).
 *
 * One row per page after the main — never for the main itself, which the table above already
 * describes — each printing the engine's **own** figures: its description and its duration, exactly as
 * the engine published them. The row the selection stands on takes `uiAccent`; the others stay in the
 * card's own text colour.
 */
@Composable
private fun PageBlock(
    pages: List<RoutePage>,
    selectedIndex: Int,
    onStepPage: (Int) -> Unit
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
            IconButton(onClick = { onStepPage(-1) }, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.cd_route_candidate_prev),
                    tint = Color(AppConfig.uiAccent),
                    modifier = Modifier.size(24.dp)
                )
            }
            IconButton(onClick = { onStepPage(1) }, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.cd_route_candidate_next),
                    tint = Color(AppConfig.uiAccent),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        // The main is pages[0] and the candidates follow it, so the row's own number is its position
        // in the set — `Route #1` is the first alternative the engine declared.
        for (index in 1..pages.lastIndex) {
            val page = pages[index]
            val selected = index == selectedIndex.coerceIn(0, pages.lastIndex)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onStepPage(index - selectedIndex.coerceIn(0, pages.lastIndex)) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = pageRowText(index, page),
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
 * **One page's row**: which route it is, the description the engine declared it under, and its own
 * duration — the engine's figures, recomputed nowhere. A page that has not landed prints no duration,
 * and a page with no description — a stored route's own, which never reaches this row because it is
 * always the sole page — reads as empty rather than failing.
 */
@Composable
private fun pageRowText(index: Int, page: RoutePage): String {
    val description = page.descriptionResId?.let { stringResource(it) } ?: ""
    val duration = page.plan?.durationSec?.let { routeSpanText(it) }
    return if (duration != null) {
        stringResource(R.string.route_page_fmt, index, description, duration)
    } else {
        stringResource(R.string.route_page_fmt, index, description, "")
    }
}

/**
 * **The two notes a route can carry, and only where they are true**.
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
