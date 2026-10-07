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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ykws.android.maro.R
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.route.RouteEndSelection
import ykws.android.maro.data.track.TrackRecorderState
import ykws.android.maro.data.track.TrackRecorderUiState
import ykws.android.maro.ui.components.BAR_CELL_PAD_VERTICAL_DP
import ykws.android.maro.ui.components.CardArea
import ykws.android.maro.ui.components.FilterControl
import ykws.android.maro.ui.components.DropdownField
import ykws.android.maro.ui.components.DropdownPairRow
import ykws.android.maro.ui.components.DropdownPairWidth
import ykws.android.maro.ui.components.DropdownRow
import ykws.android.maro.ui.components.MarkerCreateAction
import ykws.android.maro.ui.components.NestedCard
import ykws.android.maro.ui.components.SectionDivider
import ykws.android.maro.ui.components.SectionHeader
import ykws.android.maro.ui.components.StatCell
import ykws.android.maro.ui.components.rememberLabelColumnWidth
import ykws.android.maro.ui.icons.Link
import ykws.android.maro.ui.icons.LinkOff
import ykws.android.maro.ui.icons.Refresh
import ykws.android.maro.ui.icons.Visibility
import ykws.android.maro.ui.icons.VisibilityOff

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
    markerCount: Int = 0,
    // ── Routes list access + its own map referential (S8) ──────────────────
    routeCount: Int = 0,
    onViewRouteList: () -> Unit = {},
    onOpenFirstRoute: (() -> Unit)? = null,
    routeFilterState: ykws.android.maro.data.model.ListFilter = ykws.android.maro.data.model.ListFilter(),
    onRouteFilterChange: (ykws.android.maro.data.model.ListFilter) -> Unit = {},
    onRouteReset: () -> Unit = {},
    routeFilterAxes: List<ykws.android.maro.data.model.FilterAxisSpec> = emptyList(),
    routeFilterLinked: Boolean = true,
    onToggleRouteLink: () -> Unit = {},
    // ── The two kinds' map-visibility eyes (2026-10-05) ─────────────────────
    // Each gates the map render of its own kind alone — it never moves the count or the list (D6).
    trackVisible: Boolean = true,
    routeVisible: Boolean = true,
    onToggleTrackVisible: () -> Unit = {},
    onToggleRouteVisible: () -> Unit = {}
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
        // ── ROUTING section ──────────────────────────────
        SectionHeader(title = stringResource(R.string.settings_tab_routing))

        Spacer(Modifier.height(AppConfig.uiSpacingHeaderBottom.dp))

        CardArea {
            // ── Origin and destination — the route's two ends ──
            // The mode's own **parameters**, held in the drawer since 2026-09-28 (R44, D2): the ends are
            // chosen here rather than placed on the map, one pair per navigation mode, and they stand
            // **inside** the Routing card under a short comment, and they stand always. The arming has no
            // door here since 2026-10-04: the map's square and the fan's own child are the doors, so
            // R49's "second door onto the same arming" no longer counts this one.
            // The head is one comment naming the group's two fields — the route's **origin and
            // destination** — and it is what identifies them: neither dropdown row carries a label of its
            // own, each showing only its value inside its own box.
            Text(
                text = stringResource(R.string.route_comment_ends),
                color = Color(AppConfig.uiTextMuted),
                fontSize = AppConfig.uiFontToggleSize.sp,
                fontWeight = FontWeight.Medium
            )
            RouteEndsSection(routeSummary)

            SectionDivider()

            // ── Cruising speed / driving preference — the two settings the mode plans with ──
            // Added 2026-10-04 (the user's word): the quick access reads and writes the very settings the
            // Settings page holds — `routeFreeWaterPaceKn` and `routeSlowWaterAversion` — so it is a second
            // **door** onto those values and never a second home, and the pace shown is the **set** one
            // rather than the boat's fitted pace.
            Text(
                text = stringResource(R.string.route_comment_quick_access),
                color = Color(AppConfig.uiTextMuted),
                fontSize = AppConfig.uiFontToggleSize.sp,
                fontWeight = FontWeight.Medium
            )
            RouteQuickAccessSection(routeSummary)

            // ── The mode's summary: what a route is doing, and what it costs — the card's foot ──
            // It is the read-only echo of the mode, what remains readable of it while the drawer stands
            // over the panel, and it took the card's foot on 2026-10-04 (the user's word) so the mode's
            // parameters are read before its figures. R67 keeps it **in the same card** as the
            // sub-sections above rather than absorbed by them, and it stands only when the mode has
            // something to say ([routeSummaryVisible]).
            if (routeSummaryVisible) {
                SectionDivider()
                // **The route block rides a sub-card of its own** (the user's word, 2026-10-04), the same
                // `NestedCard` surface the live block wears, with no expander above it.
                NestedCard {
                    RouteSummaryBlock(routeSummary)
                }
            }

        }

        Spacer(Modifier.height(AppConfig.uiSpacingSectionGap.dp))

        // ── ROUTES section + filter controls (2026-10-05, D11) ─────────────
        // The Routes row left the ROUTING card for a section of its own, mirroring the TRACKS section so
        // the map-referential filter icons get a section header to live in (D11, superseding D1). The card
        // above keeps the route ends, the quick access and the gated summary alone.
        SectionHeader(title = stringResource(R.string.menu_manage_routes)) {
            // The routes eye stands first and outside the axes gate, so it never disappears with the
            // filters (D3): it shows or hides the whole route kind on the map, never the count or the list.
            KindVisibilityToggle(
                visible = routeVisible,
                onToggle = onToggleRouteVisible,
                contentDescription = stringResource(R.string.cd_toggle_routes_map)
            )
            if (routeFilterAxes.isNotEmpty()) {
                IconButton(
                    onClick = onToggleRouteLink,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = if (routeFilterLinked) Link else LinkOff,
                        contentDescription = null,
                        tint = ButtonColors.icon,
                        modifier = Modifier.size(ButtonColors.iconSizeDp.dp)
                    )
                }
                FilterControl(
                    filterState = routeFilterState,
                    filterAxes = routeFilterAxes,
                    onFilterChange = onRouteFilterChange
                )
                val hasActiveRouteFilter = routeFilterState.axes.isNotEmpty()
                IconButton(
                    onClick = onRouteReset,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Refresh,
                        contentDescription = stringResource(R.string.cd_reset_filter),
                        tint = ButtonColors.icon,
                        modifier = Modifier.size(ButtonColors.iconSizeDp.dp)
                            .alpha(if (hasActiveRouteFilter) ButtonColors.activeAlpha else ButtonColors.inactiveAlpha)
                    )
                }
            }
        }

        Spacer(Modifier.height(AppConfig.uiSpacingHeaderBottom.dp))

        CardArea {
            // ── Routes list row (2026-10-05, D11) — the ROUTES section's own door to the saved routes ──
            // Mirrors the Tracks row below it: the label, the count and the chevron to the first route.
            // No live block: a recording in progress is never a route.
            RoutesRow(
                routeCount = routeCount,
                onViewRouteList = onViewRouteList,
                onOpenFirstRoute = onOpenFirstRoute
            )
        }

        Spacer(Modifier.height(AppConfig.uiSpacingSectionGap.dp))

        // ── TRACKS section + filter controls ─────────────
        SectionHeader(title = stringResource(R.string.settings_section_tracks)) {
            // The tracks eye, first and outside the axes gate — the routes header's twin (D3).
            KindVisibilityToggle(
                visible = trackVisible,
                onToggle = onToggleTrackVisible,
                contentDescription = stringResource(R.string.cd_toggle_tracks_map)
            )
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
                // **The live block rides a sub-card of its own** (the user's word, 2026-10-04): the shared
                // `NestedCard` surface holds the band and the readings, with no expander above it — the
                // block is always open, the sub-card is depth, not disclosure.
                NestedCard {
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
 * **The Route sub-section: a route's two ends** (R44–R48).
 *
 * Two **dropdowns** over the ends the screen resolved — the wheel of R70 retired 2026-09-28, its drag never
 * committing reliably — standing **inside** the Routing card under a comment naming the group's two roles.
 * The entries arrive already labelled — two of them are `@StringRes`-backed words and the rest are markers'
 * own names, which is data rather than UI text — so each row's own label is its role and the value rides on
 * its right.
 *
 * **The two sit one gap apart, and it is the very gap the quick access leaves between its two wheels** (the
 * user's word, 2026-10-04): `ui.spacing.dropdown.gap`, the one value for the space between two fields of a
 * group, whichever way they sit — so the ends' stacked pair and the pair control's side-by-side one read as
 * spaced the same.
 *
 * **Both boxes read left, and their wheels follow** (the user's word, 2026-10-05): each row is handed
 * `TextAlign.Start`, and because the field's one alignment drives both surfaces the wheel's rows sit on the
 * box's own axis rather than centring under it.
 *
 * **The action that armed the acquisition was removed from here** (the user's word, 2026-10-04): the map's
 * square and the fan's own child are the doors now, so R49's "second door onto the same arming" no longer
 * counts this one, and the callback the sub-section carried went with it.
 */
@Composable
private fun RouteEndsSection(section: RouteSummaryData) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppConfig.uiPaddingToggleVertical.dp),
        verticalArrangement = Arrangement.spacedBy(AppConfig.uiSpacingDropdownGap.dp)
    ) {
        DropdownRow(
            label = null,
            options = section.startOptions.map { it.selection to it.label },
            selected = section.startSelection,
            onSelect = section.onStartSelect,
            accessibleName = stringResource(R.string.route_label_start),
            textAlign = TextAlign.Start
        )
        DropdownRow(
            label = null,
            options = section.destinationOptions.map { it.selection to it.label },
            selected = section.destinationSelection,
            onSelect = section.onDestinationSelect,
            accessibleName = stringResource(R.string.route_label_destination),
            textAlign = TextAlign.Start
        )
    }
}

/**
 * **The quick access to the mode's two settings** (the user's word, 2026-10-04): the pace the engine plans at
 * and the preference it weighs lines by, side by side in the pair control ([`DropdownPairRow`]) — whose wheel
 * is the ends' own — over the grid the pace setting itself moves on.
 *
 * **The pair is a second door, never a second home**: both boxes write `routeFreeWaterPaceKn` and
 * `routeSlowWaterAversion` through the same callbacks the Settings page's sliders use, so the two surfaces
 * cannot disagree — and the pace box shows the **set** pace, not the boat's own fitted one.
 *
 * **The width rule is the control's own capability, and this call site asks for the proportional split**
 * (the user's word, 2026-10-05): each box takes a share of the row matching its own longest word, so the
 * preference's longer labels — `Optimal`, `Rapide` — earn more room than the pace's `35 kn`, where the
 * earlier fixed/elastic split left the pair narrow on the left and roomy on the right.
 */
@Composable
private fun RouteQuickAccessSection(section: RouteSummaryData) {
    // The setting's own grid, read from its one home — seven stops, 5 … 35 kn by 5.
    val paceOptions = AppConfig.ROUTE_FREE_WATER_PACE_STOPS_KN.map {
        it to stringResource(R.string.settings_route_pace_value_fmt, it)
    }
    // The ladder's own order, most-fun first, which is the order the acquisition's first column lists its
    // rungs in; the λ comes off the ladder rather than being spelled here, so the wheel writes exactly what
    // the Settings slider writes.
    val preferenceOptions = (0 until ROUTE_LADDER_RUNG_COUNT).map { index ->
        routeRungLambdaOf(index).toFloat() to stringResource(routeRungLabelRes(index))
    }
    DropdownPairRow(
        left = DropdownField(
            options = paceOptions,
            selected = section.paceKn,
            onSelect = section.onPaceSelect,
            accessibleName = stringResource(R.string.settings_route_pace_label)
        ),
        right = DropdownField(
            options = preferenceOptions,
            selected = section.preference,
            onSelect = section.onPreferenceSelect,
            accessibleName = stringResource(R.string.settings_route_preference_label)
        ),
        leftWidth = DropdownPairWidth.Proportional,
        rightWidth = DropdownPairWidth.Proportional
    )
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

/**
 * The ROUTES section's **Routes row** (2026-10-05, D11): the drawer's door to the saved routes,
 * mirroring the Tracks row — the label and the count, a chevron to the first route. Its own Link /
 * Filter / Reset live in the ROUTES `SectionHeader` above it, bound to the **route map referential**
 * (the menu's own referential, as the Tracks header's controls are, so a filter edit there moves the
 * routes the map draws and the count beside it).
 */
@Composable
private fun RoutesRow(
    routeCount: Int,
    onViewRouteList: () -> Unit,
    onOpenFirstRoute: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onViewRouteList)
            .padding(vertical = AppConfig.uiPaddingToggleVertical.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = stringResource(R.string.menu_manage_routes),
            color = Color(AppConfig.uiTextPrimary),
            fontSize = AppConfig.uiFontToggleSize.sp,
            fontWeight = FontWeight.Medium
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$routeCount",
                color = Color(AppConfig.uiTextMuted),
                fontSize = 14.sp // 14sp, not uiFontValueSize (16sp) — count stays compact
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = { onOpenFirstRoute?.invoke() },
                enabled = onOpenFirstRoute != null,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.cd_open_first_route),
                    tint = Color(AppConfig.uiTextMuted).copy(alpha = if (onOpenFirstRoute != null) 1f else 0.35f),
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

/**
 * One kind's **map-visibility eye** (2026-10-05): the leftmost control of a section header's trailing
 * slot, standing **outside** the filter-axes gate so a kind with no axis keeps its switch (D3). It
 * shows [Visibility] while the kind is drawn and [VisibilityOff] while it is hidden, at the sibling
 * icons' own tint, and it gates the **map render alone** — the count beside the row follows the filter
 * and never this flag, and the list keeps its rows (D6).
 */
@Composable
private fun KindVisibilityToggle(
    visible: Boolean,
    onToggle: () -> Unit,
    contentDescription: String
) {
    IconButton(
        onClick = onToggle,
        modifier = Modifier.size(40.dp)
    ) {
        Icon(
            imageVector = if (visible) Visibility else VisibilityOff,
            contentDescription = contentDescription,
            tint = ButtonColors.icon,
            modifier = Modifier.size(ButtonColors.iconSizeDp.dp)
        )
    }
}
