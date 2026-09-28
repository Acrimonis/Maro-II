package ykws.android.maro.ui.map

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ykws.android.maro.R
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.route.RouteEndSelection
import ykws.android.maro.data.track.TrackRecorderState
import ykws.android.maro.data.track.TrackRecorderUiState
import ykws.android.maro.ui.components.CardArea
import ykws.android.maro.ui.components.ConfirmAction
import ykws.android.maro.ui.components.ConfirmActionButton
import ykws.android.maro.ui.components.ConfirmActionRole
import ykws.android.maro.ui.components.FilterControl
import ykws.android.maro.ui.components.DropdownRow
import ykws.android.maro.ui.components.MultiSelectRow
import ykws.android.maro.ui.components.SectionDivider
import ykws.android.maro.ui.components.SectionHeader
import ykws.android.maro.ui.components.ToggleRow
import ykws.android.maro.ui.icons.Link
import ykws.android.maro.ui.icons.LinkOff
import ykws.android.maro.ui.icons.Refresh

/** The two render axes the Tracks rendering row toggles, in the order the twin box draws them (D5). */
private enum class TrackAxis { ARROWS, COLOURS }

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
 * @param onDismiss         Triggered to close the panel.
 */
@Composable
fun MenuDrawerOverlay(
    isOpen: Boolean,
    gpsMode: Boolean,
    onGpsModeChange: (Boolean) -> Unit,
    autoShowMasterVisible: Boolean = false,
    autoShowMasterOverride: Boolean = true,
    onAutoShowMasterChange: (Boolean) -> Unit = {},
    gpsToggleColor: Color,
    recorderState: TrackRecorderUiState,
    onViewTrackList: () -> Unit,
    onManageMarkers: () -> Unit = {},
    onOpenFirstTrack: (() -> Unit)? = null,
    onOpenFirstMarker: (() -> Unit)? = null,
    markerZonesVisible: Boolean = true,
    onToggleMarkerZones: () -> Unit = {},
    /** The arrows axis the twin box's first chip shows (D5). */
    trackArrows: Boolean = false,
    /** The colours axis its second chip shows — the same two flags the map renders by. */
    trackColours: Boolean = true,
    /** The arrows chip's writer: the menu owns both axes, and nothing else writes either (D3). */
    onTrackArrowsChange: (Boolean) -> Unit = {},
    /** The colours chip's writer, the other half of that same single owner (D3). */
    onTrackColoursChange: (Boolean) -> Unit = {},
    onImportTracks: () -> Unit = {},
    onExportAllTracks: () -> Unit = {},
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit = {},
    /**
     * The route mode's read-only summary and its own gate: [routeSummaryVisible] is the chrome flag,
     * standing in the **routing phase alone** (a route followed) since 2026-09-28 — the acquisition's
     * own status lives on the panel, so nothing here carries it — and [routeSummary] carries the words
     * the block prints.
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
        SectionHeader(title = stringResource(R.string.settings_section_position))

        Spacer(Modifier.height(AppConfig.uiSpacingHeaderBottom.dp))

        CardArea {
            // GPS mode — keeps its dynamic status colour.
            ToggleRow(
                label = stringResource(R.string.settings_gps_mode_label),
                checked = gpsMode,
                onCheckedChange = onGpsModeChange,
                checkedColor = gpsToggleColor
            )

            // ── The mode's summary: what a route is doing, and what it costs ──
            // The mode's own switch stays the map control stack's square as well as the Route
            // sub-section's action, and this block carries no action of its own — the panel's three
            // outcomes are the doors. It is the read-only echo of the mode, what remains readable of it
            // while the drawer stands over the panel, and R67 keeps it **in the same card** as the
            // sub-section below rather than absorbed by it.
            if (routeSummaryVisible) {
                SectionDivider()
                RouteSummaryBlock(routeSummary)
            }

            // ── ROUTE sub-section: a route's two ends, and the action that arms the acquisition ──
            // The mode's own **parameters**, held in the drawer since 2026-09-28 (R44, D2): the ends are
            // chosen here rather than placed on the map, one pair per navigation mode, and the action
            // beside them is the second door onto the same arming the map's square performs (R49). It
            // stands **inside** the Navigation card under a sub-section header, and it stands always;
            // what gates is the summary above it.
            SectionDivider()
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

            // ── Auto-show zones: the master switch the settings' own approach reveal obeys ──
            // Moved to the card's foot on 2026-09-28, the user's word: the route's parameters stand above
            // it, so the card reads mode → parameters → the reveal that serves them. The row itself is
            // untouched — same label, same preference, same gate.
            if (autoShowMasterVisible) {
                SectionDivider()
                ToggleRow(
                    label = stringResource(R.string.settings_autoshow_master_label),
                    checked = autoShowMasterOverride,
                    onCheckedChange = onAutoShowMasterChange
                )
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
            // ── Live stats (only when recording) — the card's head, per D5 ──
            if (recorderState.state == TrackRecorderState.ON) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StatRow(stringResource(R.string.track_stat_state), if (recorderState.isMoving) stringResource(R.string.track_status_recording) else stringResource(R.string.track_status_idle))
                    StatRow(stringResource(R.string.track_stat_elapsed), formatDuration(recorderState.elapsedSeconds))
                    StatRow(stringResource(R.string.track_stat_points), "${recorderState.pointCount}")
                    StatRow(stringResource(R.string.track_stat_distance), stringResource(R.string.menu_stat_distance_nm, recorderState.distanceNm))
                    StatRow(stringResource(R.string.track_stat_max_speed), stringResource(R.string.menu_stat_speed_kn, recorderState.maxSpeedKn))
                    StatRow(stringResource(R.string.track_stat_avg_speed), stringResource(R.string.menu_stat_speed_kn, recorderState.avgSpeedKn))
                    StatRow(stringResource(R.string.track_stat_idle), formatDuration(recorderState.idleDurationSec))
                }
                SectionDivider()
            }

            // ── Track List row ─────────────────────────────
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

            // ── Tracks rendering: two independent axes ────────────────────
            // D5: the row replaces the retired "Show dir & speed" one in its own slot, so no other row
            // moves, and the live block sits at the card's head. One chip per axis, each on or off by
            // itself, so all four combinations are states: neither, arrows only, colours only, both.
            SectionDivider()
            // A standing sub-section again since 2026-09-28: the collapse came out and the group sits
            // open in the card under its own caption, the settings' rows being the shape it follows —
            // nothing about the two axes is hidden behind a chevron.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = AppConfig.uiPaddingToggleVertical.dp)
            ) {
                Text(
                    text = stringResource(R.string.menu_tracks_rendering),
                    color = Color(AppConfig.uiTextMuted),
                    fontSize = AppConfig.uiFontDescSize.sp
                )
                Spacer(Modifier.height(6.dp))
                MultiSelectRow(
                    options = listOf(
                        TrackAxis.ARROWS to stringResource(R.string.menu_render_arrows),
                        TrackAxis.COLOURS to stringResource(R.string.menu_render_colours)
                    ),
                    isOn = { axis ->
                        when (axis) {
                            TrackAxis.ARROWS -> trackArrows
                            TrackAxis.COLOURS -> trackColours
                        }
                    },
                    onToggle = { axis ->
                        when (axis) {
                            TrackAxis.ARROWS -> onTrackArrowsChange(!trackArrows)
                            TrackAxis.COLOURS -> onTrackColoursChange(!trackColours)
                        }
                    }
                )
            }

            // ── Import / Export pair ───────────────────────
            // Both wear the **outlined role**, side by side (2026-09-28): the pair is neither the
            // drawer's own outcome nor a loss, so neither takes the accent fill, which stays the
            // surface's action alone (§5.6, §5.9). The glyphs left with `ActionRow`, which had no icon
            // slot and no other call site, so the component went with them.
            SectionDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ConfirmActionButton(
                    action = ConfirmAction(
                        label = stringResource(R.string.action_export),
                        role = ConfirmActionRole.SECONDARY,
                        onClick = onExportAllTracks
                    ),
                    modifier = Modifier.weight(1f)
                )
                ConfirmActionButton(
                    action = ConfirmAction(
                        label = stringResource(R.string.action_import),
                        role = ConfirmActionRole.SECONDARY,
                        onClick = onImportTracks
                    ),
                    modifier = Modifier.weight(1f)
                )
            }

        }

        Spacer(Modifier.height(AppConfig.uiSpacingSectionGap.dp))

        // ── MARKERS section + filter controls ────────────
        SectionHeader(title = stringResource(R.string.settings_section_markers)) {
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

            SectionDivider()

            ToggleRow(
                label = stringResource(R.string.menu_show_zones),
                checked = markerZonesVisible,
                onCheckedChange = { onToggleMarkerZones() }
            )
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
 * its role and the value rides on its right. The action is §5.6's own rendering of an outcome, so the
 * drawer's primary action and the panel's are the same control.
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
            onSelect = section.onStartSelect
        )
        DropdownRow(
            label = null,
            options = section.destinationOptions.map { it.selection to it.label },
            selected = section.destinationSelection,
            onSelect = section.onDestinationSelect
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
                    role = ConfirmActionRole.PRIMARY,
                    onClick = section.onArm
                ),
                modifier = Modifier.fillMaxWidth(0.5f)
            )
        }
    }
}

/**
 * **The mode's summary** — the drawer's read-only echo of the route panel, kept beside the section.
 *
 * The status word is the panel's own: the acquiring word stands only while the engine searches, with
 * the engine's stage word beside it, and the active word stands while a route is followed. The plan's
 * own pair stands as soon as a plan does, and the boat-relative pair — under its own sub-title — only
 * while a route is followed, since a draft is not being followed and "remaining" would describe a
 * line the boat may never take.
 *
 * **R68's line stands above those rows and moves nothing**: where a candidate that saves time exists,
 * one line names it with its own saving, and the rows beneath it read exactly as they would without it.
 * It is the drawer's answer to "is there a better line than the one I am looking at" without the drawer
 * becoming the selection surface — the panel's own rows and next/prev are that.
 */
@Composable
private fun RouteSummaryBlock(summary: RouteSummaryData) {
    val status = when {
        summary.searching -> listOfNotNull(
            stringResource(R.string.route_status_acquiring),
            summary.stageRes?.let { stringResource(it) }
        ).joinToString(" · ")

        summary.remaining != null -> stringResource(R.string.route_status_active)
        else -> null
    }

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
        status?.let {
            Text(
                text = it,
                color = Color(AppConfig.uiTextPrimary),
                fontSize = AppConfig.uiFontToggleSize.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(6.dp))
        }
        Text(
            text = stringResource(R.string.route_trip_title),
            color = Color(AppConfig.uiTextPrimary),
            fontSize = AppConfig.uiFontToggleSize.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(6.dp))
        // The plan's pair prints as a whole or not at all: gating on the distance alone would borrow a
        // `0:00` for a missing time, so when either half is absent both rows drop.
        val plannedDistanceNm = summary.plannedDistanceNm
        val plannedEtaSeconds = summary.plannedEtaSeconds
        if (plannedDistanceNm != null && plannedEtaSeconds != null) {
            StatRow(
                stringResource(R.string.track_stat_dist),
                stringResource(R.string.route_trip_distance_nm, plannedDistanceNm)
            )
            StatRow(
                stringResource(R.string.route_label_eta),
                routeEtaText(plannedEtaSeconds)
            )
        }
        summary.remaining?.let { left ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.route_trip_remaining),
                color = Color(AppConfig.uiTextMuted),
                fontSize = AppConfig.uiFontDescSize.sp
            )
            Spacer(Modifier.height(6.dp))
            StatRow(
                stringResource(R.string.track_stat_dist),
                stringResource(R.string.route_trip_distance_nm, left.distanceNm)
            )
            StatRow(
                stringResource(R.string.route_label_eta),
                routeEtaText(left.etaSeconds)
            )
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color(AppConfig.uiTextMuted),
            fontSize = AppConfig.uiFontDescSize.sp
        )
        Text(
            text = value,
            color = Color(AppConfig.uiTextPrimary),
            fontSize = 14.sp, // 14sp — not uiFontValueSize (16sp): the live-stats column stays compact
            fontWeight = FontWeight.Medium
        )
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
