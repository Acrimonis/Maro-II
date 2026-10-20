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
import androidx.compose.ui.text.font.FontStyle
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

/** The middle dot joining the header's segments — punctuation, not user-facing text. */
private const val ROUTE_HEADER_SEPARATOR = " · "

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
 * @param pages          the pages the acquisition draws, the main first — one per started lookup.
 * @param selectedIndex   which of [pages] the selection stands on — the table and the saves describe
 *                        that one page.
 * @param frontSaved      whether the **selected** line is already written (R55) — including an early
 *                        save still growing in the background.
 * @param partialDrawn    whether the main lookup has a drawable partial line, so `Select route` opens
 *                        before the search lands (`Save to track` waits for a landed line).
 * @param committed       whether an early `Select route` is waiting on the main line's finalization.
 * @param isLandscape     whether the device is in landscape orientation.
 * @param dashboardBaseHeight the dashboard's base height — the floor the panel uses in portrait.
 * @param paceKn          the cruising speed the search plans at (kn) — the evaluated pace under `Any`.
 * @param paceSetKn       the cruising-speed **setting** (kn), `Any` included, so the header can say both.
 * @param onMeasuredHeight optional report of the open panel's measured height (Phase 2).
 * @param panelMaxHeight  the portrait frame's own ceiling (F5) — the band cap the map leaves,
 *                        under which a taller panel's body scrolls instead of covering the map
 *                        strip. Null keeps the full-screen ceiling; landscape ignores it.
 * @param onSelectPage    **absolute set**: names the page the pager or a row tap lands on, by its
 *                        position in the displayed order.
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
    pages: List<RoutePage>,
    selectedIndex: Int,
    frontSaved: Boolean,
    partialDrawn: Boolean,
    committed: Boolean,
    isLandscape: Boolean,
    dashboardBaseHeight: Dp,
    paceKn: Double,
    paceSetKn: Float,
    /**
     * **The ranks and the crown** — the engine's own fold read through the folds and counted over the
     * pages that stand ([RouteRankView]). A landed row prints its rank at once, live as others land; the
     * crown's check waits for the last rung. The panel re-derives no rule of the ranking.
     */
    rankView: RouteRankView = RouteRankView(),
    /**
     * **The dial's own word in force** — the stop the crown was weighed by, already resolved
     * ([verdictDialLabel]), the header's last segment so each rank is read against the weight that
     * produced it. `null` where the surface carries no dial.
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
    // The header's one line: a committed early select reads its own word; otherwise the search summary —
    // how many routes have landed, the cruising speed in force (the setting, or under `Any` the pace the
    // search evaluated) and the preference the ranks were read against. The stage words moved to the
    // rows, each rung printing its own.
    val landedCount = pages.count { it.plan != null || it.reason != null }
    val paceText = if (AppConfig.isFreeWaterPaceAny(paceSetKn)) {
        stringResource(
            R.string.route_header_pace_any_fmt,
            stringResource(R.string.settings_route_pace_value_fmt, paceKn)
        )
    } else {
        stringResource(R.string.settings_route_pace_value_fmt, paceSetKn)
    }
    val status = if (committed) {
        stringResource(R.string.route_status_selected)
    } else {
        listOfNotNull(
            // The counter only means something while rungs are still arriving; once all have landed it goes.
            if (acquiring.searching) {
                stringResource(R.string.route_header_landed_fmt, landedCount, pages.size)
            } else {
                null
            },
            paceText,
            dialLabel
        ).joinToString(ROUTE_HEADER_SEPARATOR)
    }
    val refusal = acquiring.refusal ?: selected?.reason
    val canSelect = selectedPlan != null || (partialDrawn && !committed)
    // Saving waits for a landed line: a partial line opens `Select route` (R88) but never the save door.
    val canSave = selectedPlan != null && !frontSaved
    // The third door's word follows the cost (§5.6): its own short red one while the selected line is
    // unwritten, the exit dialog's own *Leave* under the accent once `frontSaved` says nothing is lost.
    val endingDoor = routeAcquisitionEndingDoor(written = frontSaved)
    // **The ranks are live and the crown waits.** A landed row prints its rank at once, read over the
    // routes landed so far — the row's stage word and the header's `Landed x/n` say the set is not
    // complete — while the crown's check stands only once the last rung has landed.
    val crownLookupId = if (acquiring.searching) null else rankView.crownLookupId

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
            Text(
                text = status,
                color = Color(AppConfig.uiTextPrimary),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
                    crownLookupId = crownLookupId,
                    ranks = rankView.ranks
                )
            }
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
    crownLookupId: RouteId?,
    ranks: Map<RouteId, Int>
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
            crownLookupId = crownLookupId,
            ranks = ranks
        )
    }
}

/**
 * **The summary table** — one line per route, three columns. Column 1 is the engine's own description;
 * column 2 is the route's Dist and ETA as bare values, one per line; column 3 is a page's delta
 * against the selected route. Columns are fixed weights so the boundaries stay consistent across rows,
 * a hairline separates the columns, and rows are top-aligned and wrap.
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
 * **The second line is one slot with two occupants.** While a row's rung computes it prints that rung's
 * own stage word, **bold italic**; from the row's landing on it prints its rank, `#1`, read over the
 * routes landed so far and live as others land. The ranks are the engine's own and **one per page** —
 * a rung folded into another counts with it — and the crown's check, on the first line, waits for the
 * last rung. This table re-derives no rule of the order.
 */
@Composable
private fun RouteSummaryTable(
    pages: List<RoutePage>,
    selectedIndex: Int,
    onSelectPage: (Int) -> Unit,
    crownLookupId: RouteId?,
    ranks: Map<RouteId, Int>
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
            // The row's own place in the verdict: the crown (its check glyph) closing the name's own line
            // and its rank below it, both read straight off the engine — a row still computing has no
            // rank and prints its stage word there instead. The selection stays a separate fact (§2.7b),
            // so the crown and the taken choice can stand on two different rows at once.
            val isCrown = page.lookupId != null && page.lookupId == crownLookupId
            val rank = page.lookupId?.let { ranks[it] }
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
                        // **name · check** on the first line, **rank or stage** on the second: the check
                        // follows the name's last word. The name takes the line's own wrap and yields the mark its width
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
                        // One slot, two occupants: the rank once the row has landed, else the stage word of
                        // the rung still computing in **bold italic** — never both, and nothing before a
                        // rung's first boundary. Both sit at **the name's own 13 sp** (2026-10-15): bold
                        // only reads at the name's size.
                        if (rank != null) {
                            Text(
                                text = "#$rank",
                                color = textColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            page.stage?.let { stage ->
                                Text(
                                    text = stringResource(stage.labelResId),
                                    color = textColor,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontStyle = FontStyle.Italic
                                )
                            }
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
