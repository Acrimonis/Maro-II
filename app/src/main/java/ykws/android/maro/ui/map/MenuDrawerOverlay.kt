package ykws.android.maro.ui.map

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ykws.android.maro.R
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.route.RouteEndSelection
import ykws.android.maro.data.track.TrackRecorderState
import ykws.android.maro.data.track.TrackRecorderUiState
import ykws.android.maro.ui.components.BAR_CELL_PAD_VERTICAL_DP
import ykws.android.maro.ui.components.CardArea
import ykws.android.maro.ui.components.ConfirmAction
import ykws.android.maro.ui.components.ConfirmActionButton
import ykws.android.maro.ui.components.ConfirmActionRole
import ykws.android.maro.ui.components.FilterControl
import ykws.android.maro.ui.components.DropdownRow
import ykws.android.maro.ui.components.MarkerCreateAction
import ykws.android.maro.ui.components.SectionDivider
import ykws.android.maro.ui.components.SectionHeader
import ykws.android.maro.ui.components.StatCell
import ykws.android.maro.ui.icons.Link
import ykws.android.maro.ui.icons.LinkOff
import ykws.android.maro.ui.icons.Refresh

/**
 * The bullet the drawer's status band reads its two words apart by — the notification title's own
 * separator, so the drawer's status and the notification's read the same way.
 */
private const val STATE_SEPARATOR = "•"


/**
 * Menu slide panel — pure content composable.
 *
 * Animation and shadow are provided by [OverlayLayer].
 * Styled to match the Settings overlay: shared [SectionHeader] / [CardArea] / [SectionDivider] /
 * [ToggleRow] stencils with the Settings spacing tokens.
 *
 * @param isOpen           Whether the panel is visible (for BackHandler guard).
 * @param recorderState    Current recorder state from [TrackViewModel].
 * @param onViewTrackList   Triggered when user taps "Track List".
 * @param onManageMarkers   Triggered when user taps "Manage Markers".
 * @param onCreateMarker    The create action closing the MARKERS header's trailing slot.
 * @param onDismiss         Triggered to close the panel.
 */
@Composable
fun MenuDrawerOverlay(
    isOpen: Boolean,
    recorderState: TrackRecorderUiState,
    onViewTrackList: () -> Unit,
    onManageMarkers: () -> Unit = {},
    onCreateMarker: () -> Unit = {},
    onOpenFirstTrack: (() -> Unit)? = null,
    onOpenFirstMarker: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit = {},
    /**
     * The route mode's read-only summary and its own gate: [routeSummaryVisible] is the chrome flag, which
     * now stands while the engine searches **and** while a route is followed (the user's word, 2026-10-04,
     * widening the "routing phase alone" of 2026-09-28), and [routeSummary] carries the words and the
     * colour the block prints.
     */
    routeSummary: RouteSummaryData = RouteSummaryData(),
    routeSummaryVisible: Boolean = false,
    modifier: Modifier = Modifier,
    // ── Filter state ──────────────────────────────────────────────────
    trackFilterState: ykws.android.maro.data.model.ListFilter = ykws.android.maro.data.model.ListFilter(),
    onTrackFilterChange: (ykws.android.maro.data.model.ListFilter) -> Unit = {},
    onTrackReset: () -> Unit = {},
    trackFilterAxes: List<ykws.android.maro.data.model.FilterAxisSpec> = emptyList(),
    trackFilterLinked: Boolean = true,
    onToggleTrackLink: () -> Unit = {},
    markerFilterState: ykws.android.maro.data.model.ListFilter = ykws.android.maro.data.model.ListFilter(),
    onMarkerFilterChange: (ykws.android.maro.data.model.ListFilter) -> Unit = {},
    onMarkerReset: () -> Unit = {},
    markerFilterAxes: List<ykws.android.maro.data.model.FilterAxisSpec> = emptyList(),
    markerFilterLinked: Boolean = true,
    onToggleMarkerLink: () -> Unit = {},
    trackCount: Int = 0,
    markerCount: Int = 0
) {
    if (isOpen) { BackHandler { onDismiss() } }

    ykws.android.maro.ui.components.DrawerScaffold(
        title = "Maro II",
        onClose = onDismiss,
        modifier = modifier,
        scrollable = true,
        suppressOverscrollWhenFits = true,
        statusBarsInset = true,
        contentPadding = PaddingValues(horizontal = 24.dp),
        headerActions = {
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color(AppConfig.uiSwitchTrackInactive))
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(R.string.cd_settings),
                    tint = Color(AppConfig.uiTextPrimary),
                    modifier = Modifier.size(ButtonColors.iconSizeDp.dp)
                )
            }
        }
    ) {
        // ── POSITION SOURCE section ──────────────────────
        SectionHeader(title = stringResource(R.string.settings_section_position_source))

        Spacer(Modifier.height(AppConfig.uiSpacingHeaderBottom.dp))

        CardArea {
            // ── ROUTE sub-section: a route's two ends, and the action that arms the acquisition ──
            // The mode's own **parameters**, held in the drawer since 2026-09-28 (R44, D2): the ends are
            // chosen here rather than placed on the map, one pair per navigation mode, and the action
            // beside them is the second door onto the same arming the map's square performs (R49). It
            // stands **inside** the Navigation card under a sub-section header, and it stands always;
            // what gates is the summary **below** it, which took the card's foot on 2026-10-04 (the
            // user's word) so the two ends are read before the mode's figures.
            // The head is one comment naming the group's two fields — the route's **origin and
            // destination** (2026-09-28) — and it is what identifies them: neither dropdown row carries a
            // label of its own, each showing only its value on the right, and no rule separates the two
            // rows. The arm action closes the block.
            Text(
                text = stringResource(R.string.route_comment_ends),
                color = Color(AppConfig.uiTextMuted),
                fontSize = AppConfig.uiFontToggleSize.sp,
                fontWeight = FontWeight.Medium
            )
            RouteEndsSection(routeSummary)

            // ── The mode's summary: what a route is doing, and what it costs — the card's foot since
            // 2026-10-04 ──
            // The mode's own switch stays the map control stack's square as well as the Route
            // sub-section's action, and this block carries no action of its own — the panel's three
            // outcomes are the doors. It is the read-only echo of the mode, what remains readable of it
            // while the drawer stands over the panel, and R67 keeps it **in the same card** as the
            // sub-section above rather than absorbed by it.
            if (routeSummaryVisible) {
                SectionDivider()
                RouteSummaryBlock(routeSummary)
            }
        }

        Spacer(Modifier.height(AppConfig.uiSpacingSectionGap.dp))

        // ── TRACKS section + filter controls ─────────────
        SectionHeader(title = stringResource(R.string.settings_section_tracks)) {
            if (trackFilterAxes.isNotEmpty()) {
                IconButton(
                    onClick = onToggleTrackLink,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = if (trackFilterLinked) Link else LinkOff,
                        contentDescription = null,
                        tint = ButtonColors.icon,
                        modifier = Modifier.size(ButtonColors.iconSizeDp.dp)
                    )
                }
                FilterControl(
                    filterState = trackFilterState,
                    filterAxes = trackFilterAxes,
                    onFilterChange = onTrackFilterChange
                )
                val hasActiveTrackFilter = trackFilterState.axes.isNotEmpty()
                IconButton(
                    onClick = onTrackReset,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Refresh,
                        contentDescription = stringResource(R.string.cd_reset_filter),
                        tint = ButtonColors.icon,
                        modifier = Modifier.size(ButtonColors.iconSizeDp.dp)
                            .alpha(if (hasActiveTrackFilter) ButtonColors.activeAlpha else ButtonColors.inactiveAlpha)
                    )
                }
            }
        }

        Spacer(Modifier.height(AppConfig.uiSpacingHeaderBottom.dp))

        CardArea {
            // ── Track List row — the card's first sub-section since 2026-10-04 ─────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(onClick = onViewTrackList)
                    .padding(vertical = AppConfig.uiPaddingToggleVertical.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.menu_manage_tracks),
                    color = Color(AppConfig.uiTextPrimary),
                    fontSize = AppConfig.uiFontToggleSize.sp,
                    fontWeight = FontWeight.Medium
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$trackCount",
                        color = Color(AppConfig.uiTextMuted),
                        fontSize = 14.sp // 14sp, not uiFontValueSize (16sp) — count stays compact
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = { onOpenFirstTrack?.invoke() },
                        enabled = onOpenFirstTrack != null,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.cd_open_first_track),
                            tint = Color(AppConfig.uiTextMuted).copy(alpha = if (onOpenFirstTrack != null) 1f else 0.35f),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            // ── The live block, under the Tracks row (only when recording) ──
            // The state rides the band's own fill — the tracking square's colour subdued to the level a
            // taken choice wears — and the mark beside the word is the app's one pulsing disc (R69), so
            // the drawer's state and the map square's state cannot drift apart.
            if (recorderState.state == TrackRecorderState.ON) {
                SectionDivider()
                val bandShape = RoundedCornerShape(AppConfig.uiRadiusCard.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(bandShape)
                        // The fill is the state's own colour at the shared band level, so this band and
                        // the route card's fade by one number rather than two baked hexes.
                        .background(
                            Color(
                                if (recorderState.isMoving) AppConfig.statusTrackingHealthy
                                else AppConfig.statusTrackingIdle
                            ).copy(alpha = AppConfig.uiBandFillAlpha)
                        )
                        // The edge reads the state's own colour — light green while recording, blue while
                        // idle — the pair the map's tracking square is painted with, so the band's edge
                        // and the toggle's face are one colour and only their weights differ.
                        .border(
                            1.dp,
                            Color(
                                if (recorderState.isMoving) AppConfig.statusTrackingHealthy
                                else AppConfig.statusTrackingIdle
                            ),
                            bandShape
                        )
                        .padding(horizontal = 8.dp, vertical = BAR_CELL_PAD_VERTICAL_DP.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // The app's one pulsing disc: `MapPulseDot` keeps the size, the colour and the beat.
                    MapPulseDot()
                    // "Recording • Idle|Moving" — the record state, the notification's bullet, then the
                    // sub-state the notification's own middle segment names.
                    Text(
                        text = stringResource(R.string.state_recording),
                        color = Color(AppConfig.uiTextPrimary),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = STATE_SEPARATOR,
                        color = Color(AppConfig.uiTextMuted),
                        fontSize = 14.sp
                    )
                    Text(
                        text = if (recorderState.isMoving) stringResource(R.string.state_moving)
                               else stringResource(R.string.state_idle),
                        color = Color(AppConfig.uiTextPrimary),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                SectionDivider()

                // ── The six readings: two columns by three rows ──
                // The label, separator and value columns are shared by the whole table, so no cell stands
                // empty before a short one and every value starts on its column.
                val readingLabels = listOf(
                    stringResource(R.string.track_stat_elapsed),
                    stringResource(R.string.track_stat_points),
                    stringResource(R.string.track_stat_distance),
                    stringResource(R.string.track_stat_max_speed),
                    stringResource(R.string.track_stat_avg_speed),
                    stringResource(R.string.track_stat_idle)
                )
                val labelWidth = rememberLabelColumnWidth(readingLabels)
                Row(modifier = Modifier.fillMaxWidth()) {
                    Box(Modifier.weight(1f)) {
                        StatCell(stringResource(R.string.track_stat_elapsed), formatDuration(recorderState.elapsedSeconds), labelWidth = labelWidth)
                    }
                    Box(Modifier.weight(1f)) {
                        StatCell(stringResource(R.string.track_stat_points), "${recorderState.pointCount}", labelWidth = labelWidth)
                    }
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    Box(Modifier.weight(1f)) {
                        StatCell(stringResource(R.string.track_stat_distance), stringResource(R.string.menu_stat_distance_nm, recorderState.distanceNm), labelWidth = labelWidth)
                    }
                    Box(Modifier.weight(1f)) {
                        StatCell(stringResource(R.string.track_stat_max_speed), stringResource(R.string.menu_stat_speed_kn, recorderState.maxSpeedKn), labelWidth = labelWidth)
                    }
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    Box(Modifier.weight(1f)) {
                        StatCell(stringResource(R.string.track_stat_avg_speed), stringResource(R.string.menu_stat_speed_kn, recorderState.avgSpeedKn), labelWidth = labelWidth)
                    }
                    Box(Modifier.weight(1f)) {
                        StatCell(stringResource(R.string.track_stat_idle), formatDuration(recorderState.idleDurationSec), labelWidth = labelWidth)
                    }
                }
            }
        }

        Spacer(Modifier.height(AppConfig.uiSpacingSectionGap.dp))

        // ── MARKERS section + filter controls ────────────
        SectionHeader(title = stringResource(R.string.settings_section_markers)) {
            // The create action opens the slot outside the filter-axes gate below, so creation never
            // disappears with the filters (D6); its rule and the order it follows travel with it.
            MarkerCreateAction(onClick = onCreateMarker)
            if (markerFilterAxes.isNotEmpty()) {
                IconButton(
                    onClick = onToggleMarkerLink,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = if (markerFilterLinked) Link else LinkOff,
                        contentDescription = null,
                        tint = ButtonColors.icon,
                        modifier = Modifier.size(ButtonColors.iconSizeDp.dp)
                    )
                }
                FilterControl(
                    filterState = markerFilterState,
                    filterAxes = markerFilterAxes,
                    onFilterChange = onMarkerFilterChange
                )
                val hasActiveMarkerFilter = markerFilterState.axes.isNotEmpty()
                IconButton(
                    onClick = onMarkerReset,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Refresh,
                        contentDescription = stringResource(R.string.cd_reset_filter),
                        tint = ButtonColors.icon,
                        modifier = Modifier.size(ButtonColors.iconSizeDp.dp)
                            .alpha(if (hasActiveMarkerFilter) ButtonColors.activeAlpha else ButtonColors.inactiveAlpha)
                    )
                }
            }
        }

        Spacer(Modifier.height(AppConfig.uiSpacingHeaderBottom.dp))

        CardArea {
            // ── Marker List row ────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(onClick = onManageMarkers)
                    .padding(vertical = AppConfig.uiPaddingToggleVertical.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.menu_manage_markers),
                    color = Color(AppConfig.uiTextPrimary),
                    fontSize = AppConfig.uiFontToggleSize.sp,
                    fontWeight = FontWeight.Medium
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$markerCount",
                        color = Color(AppConfig.uiTextMuted),
                        fontSize = 14.sp // 14sp, not uiFontValueSize (16sp) — count stays compact
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = { onOpenFirstMarker?.invoke() },
                        enabled = onOpenFirstMarker != null,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.cd_open_first_marker),
                            tint = Color(AppConfig.uiTextMuted).copy(alpha = if (onOpenFirstMarker != null) 1f else 0.35f),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

        }
    }
}

/**
 * **The Route sub-section: a route's two ends, and the action that arms the acquisition** (R44–R49).
 *
 * Two **dropdowns** over the ends the screen resolved — the wheel of R70 retired 2026-09-28, its drag
 * never committing reliably — and one action, standing **inside** the Navigation card under a comment
 * naming the group's two roles. The entries arrive already labelled — two of them are `@StringRes`-backed
 * words and the rest are markers' own names, which is data rather than UI text — so each row's own label is
 * its role and the value rides on its right. The action wears the same outlined `SECONDARY` face as the
 * Import/Export pair, so the drawer's Route door and the panel's are still the same control.
 */
@Composable
private fun RouteEndsSection(section: RouteSummaryData) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppConfig.uiPaddingToggleVertical.dp)
    ) {
        DropdownRow(
            label = null,
            options = section.startOptions.map { it.selection to it.label },
            selected = section.startSelection,
            onSelect = section.onStartSelect,
            accessibleName = stringResource(R.string.route_label_start)
        )
        DropdownRow(
            label = null,
            options = section.destinationOptions.map { it.selection to it.label },
            selected = section.destinationSelection,
            onSelect = section.onDestinationSelect,
            accessibleName = stringResource(R.string.route_label_destination)
        )
        Spacer(Modifier.height(8.dp))
        // The action takes the row's right half (2026-09-28): `ConfirmActionButton` resolves its own
        // `fillMaxWidth()` against the max it is handed, so an `End`-arranged row places it at half
        // width with no change to the shared component (§5.6).
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            ConfirmActionButton(
                action = ConfirmAction(
                    label = stringResource(R.string.route_action_arm),
                    role = ConfirmActionRole.SECONDARY,
                    onClick = section.onArm
                ),
                modifier = Modifier.fillMaxWidth(0.5f)
            )
        }
    }
}

/**
 * **The mode's summary** — the drawer's read-only echo of the route panel, kept beside the section, wearing
 * the live block's own treatment since 2026-10-04.
 *
 * A **band** carries the status: its fill is the route toggle's own colour at the shared band level
 * ([AppConfig.uiBandFillAlpha]) with a 1dp edge of that colour at full strength, led by the app's one pulsing
 * disc. The colour is the toggle's own face — the line's while the engine searches (R51), the navigating
 * token's while a route is followed — so the drawer and the map's square cannot disagree.
 *
 * **It carries two messages, and only those** (the user's word, 2026-10-04): `Acquiring… • <stage>` while the
 * engine searches, and `Routing • <the route's own ETA> / <the plan's total ETA>` while a route is followed.
 *
 * **Four readings stand under it**, in the columned reading cell: the plan's figures on the left as the
 * totals, the route's own on the right — `Dist total` beside `Dist route`, then `ETA total` beside
 * `ETA route`. No sub-title stands above them, the labels saying which figure is which. A figure the mode
 * does not hold yet prints the pending mark the route panel already uses
 * ([R.string.route_value_pending]), so the card keeps its shape through the acquisition — which it stands in
 * as well as the routing phase.
 *
 * **R68's line stands above the band and moves nothing**: where a candidate that saves time exists, one line
 * names it with its own saving, and the cells beneath it read exactly as they would without it. It is the
 * drawer's answer to "is there a better line than the one I am looking at" without the drawer becoming the
 * selection surface — the panel's own rows and next/prev are that.
 */
@Composable
private fun RouteSummaryBlock(summary: RouteSummaryData) {
    val bandColour = Color(
        if (summary.searching) summary.lineColor else AppConfig.routeNavigateColor
    )

    val pending = stringResource(R.string.route_value_pending)
    val distTotal = summary.plannedDistanceNm
        ?.let { stringResource(R.string.route_trip_distance_nm, it) } ?: pending
    val etaTotal = summary.plannedEtaSeconds?.let { routeEtaText(it) } ?: pending
    val distRoute = summary.remaining
        ?.let { stringResource(R.string.route_trip_distance_nm, it.distanceNm) } ?: pending
    val etaRoute = summary.remaining?.let { routeEtaText(it.etaSeconds) } ?: pending

    // The band's two messages (the user's word, 2026-10-04): the acquisition's word with the engine's stage
    // beside it, or the routing word with the remaining ETA in whole minutes — seconds below a minute.
    // Nothing else stands in the band, so a plan that no route follows yet keeps the band away.
    val followed = summary.remaining
    val bandWord: String?
    val bandTail: String?
    when {
        summary.searching -> {
            // The band's own word carries no ellipsis (`route_status_acquiring_bare`); the shared one, with
            // the ellipsis, stays the route panel's header word.
            bandWord = stringResource(R.string.route_status_acquiring_bare)
            bandTail = summary.stageRes?.let { stringResource(it) }
        }
        followed != null -> {
            bandWord = stringResource(R.string.route_status_active)
            val remainingSec = followed.etaSeconds.toInt()
            bandTail = if (remainingSec < 60) {
                stringResource(R.string.route_band_eta_sec_fmt, remainingSec)
            } else {
                stringResource(R.string.route_band_eta_min_fmt, remainingSec / 60)
            }
        }
        else -> {
            bandWord = null
            bandTail = null
        }
    }

    val labelWidth = rememberLabelColumnWidth(
        listOf(
            stringResource(R.string.route_stat_dist_total),
            stringResource(R.string.route_stat_dist_route),
            stringResource(R.string.route_stat_eta_total),
            stringResource(R.string.route_stat_eta_route)
        )
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = AppConfig.uiPaddingToggleVertical.dp)
    ) {
        summary.alternativeSavingSec?.let { saving ->
            Text(
                text = stringResource(R.string.route_alternative_status, routeSpanText(saving)),
                color = Color(AppConfig.uiAccent),
                fontSize = AppConfig.uiFontDescSize.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(6.dp))
        }
        bandWord?.let { word ->
            val bandShape = RoundedCornerShape(AppConfig.uiRadiusCard.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(bandShape)
                    .background(bandColour.copy(alpha = AppConfig.uiBandFillAlpha))
                    .border(1.dp, bandColour, bandShape)
                    .padding(horizontal = 8.dp, vertical = BAR_CELL_PAD_VERTICAL_DP.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MapPulseDot()
                Text(
                    text = word,
                    color = Color(AppConfig.uiTextPrimary),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                bandTail?.let { tail ->
                    Text(
                        text = STATE_SEPARATOR,
                        color = Color(AppConfig.uiTextMuted),
                        fontSize = 14.sp
                    )
                    Text(
                        text = tail,
                        color = Color(AppConfig.uiTextPrimary),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        SectionDivider()
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.weight(1f)) {
                StatCell(
                    stringResource(R.string.route_stat_dist_total),
                    distTotal,
                    labelWidth = labelWidth
                )
            }
            Box(Modifier.weight(1f)) {
                StatCell(
                    stringResource(R.string.route_stat_dist_route),
                    distRoute,
                    labelWidth = labelWidth
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.weight(1f)) {
                StatCell(
                    stringResource(R.string.route_stat_eta_total),
                    etaTotal,
                    labelWidth = labelWidth
                )
            }
            Box(Modifier.weight(1f)) {
                StatCell(
                    stringResource(R.string.route_stat_eta_route),
                    etaRoute,
                    labelWidth = labelWidth
                )
            }
        }
    }
}

/**
 * The label column's width for a table of readings: the widest label, measured once in the reading cell's
 * own label style, so a table's cells share one column instead of each following its own label's end. Keyed
 * on the labels rather than on live state, so a ticking recording or a moving boat never re-measures.
 */
@Composable
private fun rememberLabelColumnWidth(labels: List<String>): Dp {
    val measurer = rememberTextMeasurer()
    val style = TextStyle(
        color = Color(AppConfig.uiTextMuted),
        fontSize = 11.sp,
        lineHeight = 12.sp
    )
    val density = LocalDensity.current
    return remember(labels.joinToString("\u0000")) {
        with(density) {
            labels.maxOf { measurer.measure(it, style).size.width }.toDp()
        }
    }
}

private fun formatDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "${hours}h ${minutes}m ${seconds}s"
    } else {
        "${minutes}m ${seconds}s"
    }
}
