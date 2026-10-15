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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import ykws.android.maro.spatial.RouteId
import ykws.android.maro.spatial.RouteStage
import ykws.android.maro.spatial.RouteVerdict
import ykws.android.maro.ui.components.ConfirmAction
import ykws.android.maro.ui.components.ConfirmActionButton
import ykws.android.maro.ui.components.ConfirmActionRole
import ykws.android.maro.ui.components.DrawerScaffold
import ykws.android.maro.ui.components.PageDots
import ykws.android.maro.ui.components.StatCell
import ykws.android.maro.ui.components.SwipePager
import ykws.android.maro.ui.components.rememberLabelColumnWidth

/**
 * The crown's own glyph, the unfilled check (§2.7b), drawn in the route toggle's accent. It is the
 * crown's **only** mark now — the leading accent bar is gone — and it **closes the row's first line**,
 * inline immediately after the name's last word, so the check follows the name it crowns.
 */
private const val ROUTE_CROWN_GLYPH = "\u2713"

/**
 * The crown's glyph size — **one step up from the 18 sp this mark stood at** (2026-10-15), so the check
 * leads the row's first line beside the 13 sp name rather than sitting level with it.
 */
private val ROUTE_CROWN_GLYPH_SIZE = 20.sp

// ─────────────────────────────────────────────────────────────────────────────
// The route acquisition's own dashboard — the panel the slot carries while a route is being acquired
//
// **The acquisition has its own surface, and it is this panel in the dashboard slot** (R74): the
// status, the summary table and the three actions. **Nothing else route-specific is ever composed**
// (R73): once `Select route` is pressed the panel is gone and the ordinary dashboard returns.
//
// **The frame is the dashboards' frame** — the shared [DrawerScaffold] the selected-item dashboards
// and the wizard use. The header carries the title and, at its trailing edge, the stage status and
// the page dots; the body carries the three-column summary table, which pages laterally; the
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
 * @param dashboardBaseHeight the dashboard's base height — the floor the panel uses in portrait.
 * @param paceKn          the cruising speed the settled line names (kn) — printed in the settled status word.
 * @param onMeasuredHeight optional report of the open panel's measured height (Phase 2).
 * @param panelMaxHeight  the portrait frame's own ceiling (F5) — the band cap the map leaves,
 *                        under which a taller panel's body scrolls instead of covering the map
 *                        strip. Null keeps the full-screen ceiling; landscape ignores it.
 * @param onSelectPage    **absolute set**: names the page the pager or a row tap lands on, by its
 *                        position in the ETA-ordered view.
 * @param onSelectRoute   **Select route**: enters navigation on the selected line (R56).
 * @param onSaveTrack     **Save to track**: writes the selected line (R55).
 * @param onDiscard       **Discard**: the deferred ending, taken **only while the press would throw the
 *                        selected line away** — presents the ending at once, the panel and the line leave,
 *                        the toggle reads off, while the real disposal waits on the toast (R57). Its word
 *                        follows the cost (§5.6): the acquisition's own short *Discard* while the selected
 *                        line is unwritten, the exit dialog's own *Leave* once it is written and nothing
 *                        is lost — the short word existing only because this door holds a third of the
 *                        footer row.
 * @param onLeave         **Leave**: the ending that costs nothing — the written state's own door, which
 *                        defers on `route_left_toast` like every other ending (R92).
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
    dashboardBaseHeight: Dp,
    paceKn: Double,
    /**
     * **The ladder's running best, by the lookup that owns its row** — the winner the seat follows. Its
     * row wears a *so far* mark beside its name while the set still searches, the mark clearing with
     * the last rung. `null` where the engine ranks nothing.
     */
    runningBestLookupId: RouteId? = null,
    /**
     * **The settled set's verdict** — the engine's own fold, carrying the crown and every landed rung's
     * rank. The panel marks the crown ([RouteVerdict.best]) and prints each row's rank ([RouteVerdict.order])
     * straight off it, re-deriving no rule: the ranking has one home and the UI duplicates none of it.
     * `null` where the engine ranks nothing.
     */
    verdict: RouteVerdict? = null,
    /**
     * **The dial's own word in force** — the stop the crown was weighed by, already resolved
     * ([verdictDialLabel]), printed in the header beside the stage so each rank is read against the
     * weight that produced it. `null` where the surface carries no dial.
     */
    dialLabel: String? = null,
    onMeasuredHeight: ((Dp) -> Unit)? = null,
    panelMaxHeight: Dp? = null,
    /**
     * The height the outgoing card in this slot reported, so the route panel is pre-sized at the size
     * the screen already shows rather than snapping through the floor (2026-10-07).
     */
    initialHeight: Dp? = null,
    onSelectPage: (Int) -> Unit,
    onSelectRoute: () -> Unit,
    onSaveTrack: () -> Unit,
    onDiscard: () -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val acquiring = state as? RouteState.Choosing ?: return
    val clampedIndex = selectedIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
    val selected = pages.getOrNull(clampedIndex)
    val selectedPlan = selected?.plan
    // The header's short state word: a committed early select reads its own word, the search reads the
    // stage with the selected route's own number — `Pull #2` — and a settled page reads the settled line,
    // the pages standing and the cruising speed they plan at.
    val status = when {
        committed -> stringResource(R.string.route_status_selected)
        acquiring.searching -> stage?.let {
            stringResource(
                R.string.route_status_stage_number,
                stringResource(it.labelResId),
                clampedIndex + 1
            )
        } ?: stringResource(R.string.route_status_acquiring)
        else -> {
            val speedText = stringResource(R.string.settings_route_pace_value_fmt, paceKn)
            if (pages.size == 1) stringResource(R.string.route_status_done_one, speedText)
            else stringResource(R.string.route_status_done_many, pages.size, speedText)
        }
    }
    val refusal = acquiring.refusal ?: selected?.reason
    val canSelect = selectedPlan != null || (partialDrawn && !committed)
    val canSave = (selectedPlan != null || partialDrawn) && !frontSaved
    // The third door's word follows the cost (§5.6): its own short red one while the selected line is
    // unwritten, the exit dialog's own *Leave* under the accent once `frontSaved` says nothing is lost.
    val endingDoor = routeAcquisitionEndingDoor(written = frontSaved)
    // The *so far* mark stands only while the ladder still computes (the user's word of 2026-10-07):
    // the winner's row wears it until the last rung lands.
    val soFarLookupId = if (acquiring.searching) runningBestLookupId else null
    // **The crown's mark and the ranks read one clock.** Both are read straight off the engine's own fold
    // — the panel re-derives no rule of it — and both wait on the set settling: while the ladder still
    // computes the row reads ` so far ` under its name and **no rank stands**, because a rank read mid-run
    // is one that can still move under the reader. A row absent from the settled order wears none.
    val settledVerdict = if (acquiring.searching) null else verdict
    val crownLookupId = settledVerdict?.best?.lookupId
    val landedRanks = settledVerdict?.order?.associate { it.lookupId to it.rank } ?: emptyMap()

    // The dashboard's one shape, both orientations (2026-10-07): no rounded corner on a map dash panel,
    // this panel's own landscape right-edge rounding included — it is squared with the marker and track
    // cards.
    val shape = RoundedCornerShape(0.dp)

    DrawerScaffold(
        title = stringResource(R.string.route_acq_title),
        onClose = onDiscard,
        showBack = false,
        modifier = modifier,
        // The map card family's one header padding, the marker viewer's own (2026-10-07): 12 dp with the
        // shared `ui.padding.header.vertical`, in place of this panel's own 16 dp / 8 dp pair — which is
        // what made the route panel's header taller and wider than its siblings'.
        headerHorizontalPadding = 12.dp,
        fadeInOnEnter = true,
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
            // The dial's own word in force, beside the stage: the weight each rank was read against.
            dialLabel?.let {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = it,
                    color = Color(AppConfig.uiTextSecondary),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (pages.size > 1) {
                Spacer(Modifier.width(8.dp))
                PageDots(currentIndex = clampedIndex, total = pages.size, fillUpToCurrent = false)
            }
        },
        contentPadding = PaddingValues(horizontal = 12.dp),
        scrollable = true,
        suppressOverscrollWhenFits = true,
        bottomAnchoredContent = true,
        wrapContent = !isLandscape,
        wrapContentMinHeight = if (isLandscape) 0.dp else dashboardBaseHeight,
        initialHeight = initialHeight,
        statusBarsInset = isLandscape,
        onMeasuredHeight = onMeasuredHeight,
        wrapContentMaxHeight = panelMaxHeight,
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
                        label = stringResource(endingDoor.labelRes),
                        role = endingDoor.role,
                        onClick = if (endingDoor.discards) onDiscard else onLeave
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
                    onSelectPage = onSelectPage,
                    soFarLookupId = soFarLookupId,
                    crownLookupId = crownLookupId,
                    landedRanks = landedRanks
                )
            }
            selectedPlan?.let { PlanNotes(it) }
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
 * **The summary table pages laterally and always settles on one page.** Each page is the same
 * three-column table with that page's row selected, so a swipe or a row tap pages the whole thing
 * through the shared [SwipePager] — the one effect the marker wizard's steps use too.
 */
@Composable
private fun RouteTablePager(
    pages: List<RoutePage>,
    selectedIndex: Int,
    onSelectPage: (Int) -> Unit,
    soFarLookupId: RouteId?,
    crownLookupId: RouteId?,
    landedRanks: Map<RouteId, Int>
) {
    SwipePager(
        pageCount = pages.size,
        currentIndex = selectedIndex,
        onSettled = onSelectPage
    ) { index ->
        RouteSummaryTable(
            pages = pages,
            selectedIndex = index,
            onSelectPage = onSelectPage,
            soFarLookupId = soFarLookupId,
            crownLookupId = crownLookupId,
            landedRanks = landedRanks
        )
    }
}

/**
 * **The summary table** — one line per route, three columns. Column 1 is the engine's own description;
 * column 2 is the route's Dist and ETA as bare values, one per line; column 3 is a page's delta
 * against the selected route and the forced-crossing note. Columns are fixed weights so the boundaries
 * stay consistent across rows, a hairline separates the columns, and rows are top-aligned and wrap.
 * The selected row takes the app's taken-choice face — a `ui.select.container` fill, its `ui.accent`
 * edge, white bold text — with corners that adapt to the row's position in the bar.
 *
 * **The crown wears its own unfilled mark, never the selection's face** (§2.7b, the mark for a row picked
 * out of a list): a **check glyph closing the row's first line**, inline immediately after the name's last
 * word, in the route toggle's own accent ([AppConfig.routeNavigateColor], the followed trip cell's
 * border's colour) and **one step above the 18 sp it stood at**, so it leads the first line beside the
 * 13 sp name rather than sitting level with it — the crown's only mark, its leading accent bar gone. It
 * reads the crown straight off the engine's verdict, so it stands on the ranking's winner however the
 * reader has moved the selection — the two marks can stand on **different rows** at once.
 *
 * **Each landed row prints its rank on the second line** (2026-10-15) — the very line the ` so far ` text
 * uses, so the two share one slot and one clock: a row reads **name and check** on its first line and
 * **`#1`** on the second once the set has settled, ` so far ` while the ladder still computes, so no rank
 * ever stands mid-run. The ranks are the engine's own ([RouteVerdict.order]) and **one per rung** — the
 * tolerance's groups broken by the ranking's own chain, never shared — while a provisional row absent
 * from the order wears none. This table re-derives no rule of the order.
 */
@Composable
private fun RouteSummaryTable(
    pages: List<RoutePage>,
    selectedIndex: Int,
    onSelectPage: (Int) -> Unit,
    soFarLookupId: RouteId?,
    crownLookupId: RouteId?,
    landedRanks: Map<RouteId, Int>
) {
    val selectedDurationSec = pages.getOrNull(selectedIndex)?.plan?.durationSec
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
            // The row's own place in the settled verdict: the crown (its check glyph) closing the name's
            // own line and its rank below it, both read straight off the engine — a provisional row is
            // absent from the order and wears neither. The selection stays a separate fact (§2.7b), so the
            // crown and the taken choice can stand on two different rows at once.
            val isCrown = page.lookupId != null && page.lookupId == crownLookupId
            val rank = page.lookupId?.let { landedRanks[it] }
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
                    .clickable { onSelectPage(index) },
                verticalAlignment = Alignment.Top
            ) {
                // No leading bar and no leading slot: the name cell opens on the name itself, the crown's
                // mark **closes its first line** right after it, and the rank reads on the second.
                // A plain weighted Row on purpose: no `fillMaxWidth`, which under the row's
                // `IntrinsicSize.Min` would claim the whole row and distort the columns.
                Row(
                    modifier = Modifier
                        .weight(0.75f)
                        .padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column {
                        // A folded page names every rung it stands for; an unfolded one states its own label.
                        val label = if (page.foldedDescriptionResIds.isNotEmpty()) {
                            val names = mutableListOf<String>()
                            for (id in page.foldedDescriptionResIds) names += stringResource(id)
                            names.joinToString(stringResource(R.string.route_rung_names_sep))
                        } else {
                            page.descriptionResId?.let { stringResource(it) } ?: ""
                        }
                        // **name · check** on the first line, **rank** on the second: the check follows
                        // the name's last word, the rank takes the ` so far ` line below it under the same
                        // settled clock. The name takes the line's own wrap and yields the mark its width
                        // (`fill = false`), so a long name wraps inside the cell with the check closing its
                        // last line.
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = label,
                                color = textColor,
                                fontSize = 13.sp,
                                fontWeight = weight,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (isCrown) {
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = ROUTE_CROWN_GLYPH,
                                    color = Color(AppConfig.routeNavigateColor),
                                    fontSize = ROUTE_CROWN_GLYPH_SIZE,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        // One slot, one clock (2026-10-15): the settled rank, or the running best's own
                        // word while the ladder still computes — never both, and nothing at all on a row
                        // the settled order does not carry. The rank sits at **the name's own 13 sp**
                        // (2026-10-15): it held Bold already, but at 11 sp it read smaller than the name
                        // beside it and the weight did not register — bold only reads at the name's size.
                        if (rank != null) {
                            Text(
                                text = "#$rank",
                                color = textColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else if (page.lookupId != null && page.lookupId == soFarLookupId) {
                            Text(
                                text = stringResource(R.string.route_winner_so_far),
                                color = textColor,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
                ColumnDivider()
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    // The settled pair on a landed page, the provisional pair the boundary update carried
                    // while it waits, or the `--` placeholder where neither stands — one home for the
                    // reading, so the row prints the same way whichever it holds. The two lines come in
                    // one order for both (routeRowLines): the time first, the distance second.
                    for (line in routeRowLines(routeRowFigures(page))) {
                        when (line.kind) {
                            RouteRowKind.DURATION -> RouteValueLine(
                                value = line.value?.let {
                                    stringResource(
                                        R.string.route_summary_eta_value_fmt,
                                        it.toInt() / 60,
                                        it.toInt() % 60
                                    )
                                } ?: stringResource(R.string.route_value_pending),
                                unit = stringResource(R.string.route_summary_eta_unit),
                                color = textColor,
                                fontWeight = weight
                            )
                            RouteRowKind.DISTANCE -> RouteValueLine(
                                value = line.value?.let {
                                    stringResource(R.string.route_summary_distance_value_fmt, it)
                                } ?: stringResource(R.string.route_value_pending),
                                unit = stringResource(R.string.route_summary_distance_unit),
                                color = textColor,
                                fontWeight = weight
                            )
                        }
                    }
                }
                ColumnDivider()
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    routeDeltaText(page.plan?.durationSec, selectedDurationSec, index == selectedIndex)?.let {
                        Text(
                            text = it,
                            color = textColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    page.plan?.let { plan -> SpeedLimitsGrid(plan) }
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
 * One page's delta against the **selected** route — the span and the direction, or null for the
 * selected page itself, a tie, or a page or selection without a landed plan. The arithmetic is
 * [`routeDeltaSec`]; this reads only its formatting.
 */
@Composable
private fun routeDeltaText(
    pageDurationSec: Double?,
    selectedDurationSec: Double?,
    isSelected: Boolean
): String? {
    val delta = routeDeltaSec(pageDurationSec, selectedDurationSec, isSelected) ?: return null
    val direction = stringResource(
        if (delta < 0.0) R.string.route_comparison_less else R.string.route_comparison_more
    )
    return "${routeSpanText(abs(delta))} $direction"
}

/**
 * **The Speed limits table** — one route's slow time, one reading per regulated limit, the 300 m zone
 * first then the limits ascending (the engine's own order), wrapped into its lines by
 * [routeSlowLimitRows] in the shared [StatCell] over one label column measured once from every label. It
 * stands in the summary table's third column, under that page's own bold delta, so each route's row
 * carries its own figures.
 */
@Composable
private fun SpeedLimitsGrid(plan: RoutePlan) {
    val rows = routeSlowLimitRows(routeSlowLimitEntries(plan)).map { row ->
        row.map { entry ->
            val label = if (entry.isBand) {
                stringResource(R.string.route_slow_band_label)
            } else {
                stringResource(R.string.route_slow_limit_label_fmt, entry.limitKn)
            }
            label to stringResource(R.string.route_slow_minutes_fmt, entry.minutes)
        }
    }
    if (rows.isEmpty()) return
    val labelWidth = rememberLabelColumnWidth(rows.flatten().map { it.first })
    Column(modifier = Modifier.fillMaxWidth()) {
        rows.forEach { rowCells ->
            Row(modifier = Modifier.fillMaxWidth()) {
                rowCells.forEach { (label, value) ->
                    Box(Modifier.weight(1f)) {
                        StatCell(label = label, value = value, labelWidth = labelWidth)
                    }
                }
                // A lone tail cell keeps its own column rather than stretching across both.
                if (rowCells.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
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
