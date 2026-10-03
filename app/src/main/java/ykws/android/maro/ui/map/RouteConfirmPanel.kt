package ykws.android.maro.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import ykws.android.maro.R
import ykws.android.maro.config.AppConfig
import ykws.android.maro.spatial.RouteStage
import ykws.android.maro.ui.components.ConfirmAction
import ykws.android.maro.ui.components.ConfirmActionButton
import ykws.android.maro.ui.components.ConfirmActionRole
import ykws.android.maro.ui.components.DrawerScaffold

// ─────────────────────────────────────────────────────────────────────────────
// The route acquisition's own dashboard — the panel the slot carries while a route is being acquired
//
// **The acquisition has its own surface, and it is this panel in the dashboard slot** (R74): the
// status, the summary table and the three actions. **Nothing else route-specific is ever composed**
// (R73): once `Select route` is pressed the panel is gone and the ordinary dashboard returns.
//
// **The frame is the dashboards' frame** — the shared [DrawerScaffold] the selected-item dashboards
// and the wizard use. The header carries the title and, at its trailing edge, the stage status and
// the paging controls; the body carries the three-column summary table, which pages laterally; the
// footer carries the three actions. In portrait the panel wraps its content, floored at the dashboard
// height, so it auto-grows instead of scrolling internally.
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
 * @param frontSaved      whether the **selected** line is already written (R55) — including an early
 *                        save still growing in the background.
 * @param partialDrawn    whether the main lookup has a drawable partial line, so the two doors open
 *                        before the search lands.
 * @param committed       whether an early `Select route` is waiting on the main line's finalization.
 * @param isLandscape     whether the device is in landscape orientation.
 * @param portraitDashboardHeight the dashboard height the panel floors at in portrait.
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
    frontSaved: Boolean,
    partialDrawn: Boolean,
    committed: Boolean,
    isLandscape: Boolean,
    portraitDashboardHeight: Dp,
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
    // The header's short state word: a committed early select reads its own word, the search reads
    // the acquiring word with the stage inside it — `Acquiring (Search)…` — and a settled page reads
    // nothing.
    val status = when {
        committed -> stringResource(R.string.route_status_selected)
        acquiring.searching -> stage?.let {
            stringResource(R.string.route_status_acquiring_stage, stringResource(it.labelResId))
        } ?: stringResource(R.string.route_status_acquiring)
        else -> null
    }
    val refusal = acquiring.refusal ?: selected?.reason
    val canSelect = selectedPlan != null || (partialDrawn && !committed)
    val canSave = (selectedPlan != null || partialDrawn) && !frontSaved

    val shape = if (isLandscape) RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
        else RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)

    DrawerScaffold(
        title = stringResource(R.string.route_acq_title),
        onClose = onDiscard,
        showBack = false,
        modifier = modifier,
        headerHorizontalPadding = 16.dp,
        headerVerticalPadding = 8.dp,
        headerActions = {
            status?.let {
                Text(
                    text = it,
                    color = Color(AppConfig.uiTextPrimary),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (pages.size > 1) {
                Spacer(Modifier.width(4.dp))
                PagingControls(
                    pageCount = pages.size,
                    selectedIndex = clampedIndex,
                    onStepPage = onStepPage
                )
            }
        },
        contentPadding = PaddingValues(horizontal = 12.dp),
        scrollable = true,
        suppressOverscrollWhenFits = true,
        bottomAnchoredContent = true,
        wrapContent = !isLandscape,
        wrapContentMinHeight = if (isLandscape) 0.dp else portraitDashboardHeight,
        statusBarsInset = isLandscape,
        shape = shape,
        footer = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ConfirmActionButton(
                    action = ConfirmAction(
                        label = stringResource(R.string.route_action_save_track),
                        role = ConfirmActionRole.SECONDARY,
                        enabled = canSave,
                        onClick = onSaveTrack
                    ),
                    modifier = Modifier.weight(1f)
                )
                ConfirmActionButton(
                    action = ConfirmAction(
                        label = stringResource(R.string.route_action_select),
                        role = ConfirmActionRole.PRIMARY,
                        enabled = canSelect,
                        onClick = onSelectRoute
                    ),
                    modifier = Modifier.weight(1f)
                )
                ConfirmActionButton(
                    action = ConfirmAction(
                        label = stringResource(R.string.route_exit_discard),
                        role = ConfirmActionRole.DANGER,
                        onClick = onDiscard
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            refusal?.let {
                PanelSentence(stringResource(it.labelResId))
            }
            if (pages.isNotEmpty()) {
                RouteTablePager(
                    pages = pages,
                    selectedIndex = clampedIndex,
                    onStepPage = onStepPage
                )
            }
            selectedPlan?.let { PlanNotes(it) }
        }
    }
}

/**
 * The ‹ › pair and the position dots — the pager's own controls, at the header's trailing edge. The
 * pair does not loop: the back arrow is disabled on the first page and the forward arrow on the last.
 */
@Composable
private fun PagingControls(
    pageCount: Int,
    selectedIndex: Int,
    onStepPage: (Int) -> Unit
) {
    val accent = Color(AppConfig.uiAccent)
    val muted = Color(AppConfig.uiDividerColor)
    val canStepBack = selectedIndex > 0
    val canStepForward = selectedIndex < pageCount - 1
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = { onStepPage(-1) },
            enabled = canStepBack,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.cd_route_candidate_prev),
                tint = if (canStepBack) accent else muted,
                modifier = Modifier.size(22.dp)
            )
        }
        repeat(pageCount) { dot ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .size(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        if (dot == selectedIndex) accent else muted
                    )
            )
        }
        IconButton(
            onClick = { onStepPage(1) },
            enabled = canStepForward,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.cd_route_candidate_next),
                tint = if (canStepForward) accent else muted,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/** The panel's one sentence: the refusal, or the state while nothing stands. */
@Composable
private fun PanelSentence(text: String) {
    Text(
        text = text,
        color = Color(AppConfig.uiTextPrimary),
        fontSize = 13.sp,
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * **The summary table pages laterally** — one swipe per route. Each page is the same three-column
 * table with that page's row selected, so a swipe or the header's ‹ › pair pages the whole thing.
 */
@Composable
private fun RouteTablePager(
    pages: List<RoutePage>,
    selectedIndex: Int,
    onStepPage: (Int) -> Unit
) {
    val safeIndex = selectedIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
    val pagerState = rememberPagerState(initialPage = safeIndex) { pages.size }
    val currentSelectedIndex by rememberUpdatedState(safeIndex)

    // Selection → pager: a tap on ‹ › (or a settled candidate) moves the page.
    LaunchedEffect(safeIndex, pages.size) {
        if (pagerState.currentPage != safeIndex) pagerState.animateScrollToPage(safeIndex)
    }
    // Pager → selection: a swipe steps the selection by the distance travelled.
    LaunchedEffect(pagerState, pages.size) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            if (page != currentSelectedIndex) onStepPage(page - currentSelectedIndex)
        }
    }

    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth()) { index ->
        RouteSummaryTable(
            pages = pages,
            selectedIndex = index,
            onStepPage = onStepPage
        )
    }
}

/**
 * **The summary table** — one line per route, three columns. Column 1 is the engine's own description;
 * column 2 is the route's Dist and ETA as bare values, one per line; column 3 is a candidate's delta
 * against the main and the forced-crossing note. Columns are fixed weights so the boundaries stay
 * consistent across rows, a hairline separates the columns, and rows are top-aligned and wrap. The
 * selected row takes the app's taken-choice face — a `ui.select.container` fill, its `ui.accent` edge,
 * white bold text — with corners that adapt to the row's position in the bar.
 */
@Composable
private fun RouteSummaryTable(
    pages: List<RoutePage>,
    selectedIndex: Int,
    onStepPage: (Int) -> Unit
) {
    val mainDurationSec = pages.firstOrNull()?.plan?.durationSec
    val radius = AppConfig.uiRadiusCard.dp
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(radius))
            .border(1.dp, Color(AppConfig.uiDividerColor), RoundedCornerShape(radius))
    ) {
        pages.forEachIndexed { index, page ->
            val selected = index == selectedIndex
            val first = index == 0
            val last = index == pages.lastIndex
            val rowShape = when {
                pages.size == 1 -> RoundedCornerShape(radius)
                first -> RoundedCornerShape(topStart = radius, topEnd = radius)
                last -> RoundedCornerShape(bottomStart = radius, bottomEnd = radius)
                else -> RoundedCornerShape(0.dp)
            }
            val textColor = if (selected) Color.White else Color(AppConfig.uiTextPrimary)
            val weight = if (selected) FontWeight.Bold else FontWeight.Normal
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .then(
                        if (selected) {
                            Modifier
                                .clip(rowShape)
                                .background(Color(AppConfig.uiSelectContainer))
                                .border(1.dp, Color(AppConfig.uiAccent), rowShape)
                        } else {
                            Modifier
                        }
                    )
                    .clickable { onStepPage(index - selectedIndex) },
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = page.descriptionResId?.let { stringResource(it) } ?: "",
                    color = textColor,
                    fontSize = 13.sp,
                    fontWeight = weight,
                    modifier = Modifier
                        .weight(0.75f)
                        .padding(start = 8.dp, top = 4.dp, bottom = 4.dp)
                )
                ColumnDivider()
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    val plan = page.plan
                    if (plan != null) {
                        val course = plan.remainingFrom(plan.start)
                        RouteValueLine(
                            value = stringResource(R.string.route_summary_distance_value_fmt, plan.distanceNm),
                            unit = stringResource(R.string.route_summary_distance_unit),
                            color = textColor,
                            fontWeight = weight
                        )
                        RouteValueLine(
                            value = stringResource(
                                R.string.route_summary_eta_value_fmt,
                                course.durationSec.toInt() / 60,
                                course.durationSec.toInt() % 60
                            ),
                            unit = stringResource(R.string.route_summary_eta_unit),
                            color = textColor,
                            fontWeight = weight
                        )
                    } else {
                        RouteValueLine(
                            value = "--",
                            unit = stringResource(R.string.route_summary_distance_unit),
                            color = textColor,
                            fontWeight = weight
                        )
                        RouteValueLine(
                            value = "--",
                            unit = stringResource(R.string.route_summary_eta_unit),
                            color = textColor,
                            fontWeight = weight
                        )
                    }
                }
                ColumnDivider()
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    routeDeltaText(index, page, mainDurationSec)?.let {
                        Text(
                            text = it,
                            color = textColor,
                            fontSize = 13.sp,
                            fontWeight = weight
                        )
                    }
                    val names = page.plan?.forcedCrossingZoneNames.orEmpty()
                    if (names.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.route_forced_crossing, names.joinToString(", ")),
                            color = textColor,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

/** One value · unit pair of the middle column: the value right-aligned, the unit left-aligned. */
@Composable
private fun RouteValueLine(
    value: String,
    unit: String,
    color: Color,
    fontWeight: FontWeight
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = value,
            color = color,
            fontSize = 13.sp,
            fontWeight = fontWeight,
            textAlign = TextAlign.End,
            modifier = Modifier.width(48.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = unit,
            color = color,
            fontSize = 13.sp,
            fontWeight = fontWeight,
            textAlign = TextAlign.Start
        )
    }
}

/** A full-row-height hairline between two columns. */
@Composable
private fun ColumnDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .fillMaxHeight()
            .background(Color(AppConfig.uiDividerColor))
    )
}

/**
 * One candidate's delta against the main — the span and the direction, or null for the main itself,
 * a tie or a page without a landed plan.
 */
@Composable
private fun routeDeltaText(index: Int, page: RoutePage, mainDurationSec: Double?): String? {
    if (index <= 0) return null
    val plan = page.plan ?: return null
    val main = mainDurationSec ?: return null
    val delta = plan.durationSec - main
    if (delta == 0.0) return null
    val direction = stringResource(
        if (delta < 0.0) R.string.route_comparison_less else R.string.route_comparison_more
    )
    return "${routeSpanText(abs(delta))} $direction"
}

/**
 * **The note a route can carry, and only where it is true**.
 */
@Composable
private fun PlanNotes(plan: RoutePlan) {
    if (plan.destinationMoved) {
        Text(
            text = "(${stringResource(R.string.route_destination_moved)})",
            color = Color(AppConfig.uiTextPrimary),
            fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
