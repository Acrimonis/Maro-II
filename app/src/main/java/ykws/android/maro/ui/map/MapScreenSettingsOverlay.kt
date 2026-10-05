
package ykws.android.maro.ui.map
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.depth.RasterCache
import ykws.android.maro.ui.components.DropdownRow
import ykws.android.maro.ui.components.SegmentedRow
import ykws.android.maro.ui.components.SubSectionHeader
import android.provider.Settings
import android.graphics.Color
import ykws.android.maro.R
import ykws.android.maro.spatial.RouteEngineChoice
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.border
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.Role
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first
import ykws.android.maro.data.settings.AppSettings
import ykws.android.maro.ui.components.CardArea
import ykws.android.maro.ui.components.DrawerHeader
import ykws.android.maro.ui.components.SectionDivider
import ykws.android.maro.ui.components.SectionHeader
import ykws.android.maro.ui.components.Expander
import ykws.android.maro.ui.components.NestedCard
import ykws.android.maro.ui.components.SliderRow
import ykws.android.maro.ui.components.ToggleLabelStyle
import ykws.android.maro.ui.components.ToggleRow
import ykws.android.maro.ui.components.MultiSelectRow
import ykws.android.maro.ui.components.ConfirmAction
import ykws.android.maro.ui.components.ConfirmActionButton
import ykws.android.maro.ui.components.ConfirmActionRole

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsOverlay(
    settings: AppSettings,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    onDismiss: () -> Unit,
    selectedTab: SettingsTab,
    onTabChange: (SettingsTab) -> Unit,
    onRegenerateRasters: (List<RasterCache.Step>) -> Unit = {},
    onImportTracks: () -> Unit = {},
    onExportAllTracks: () -> Unit = {},
    layersScrollState: ScrollState,
    navigationScrollState: ScrollState,
    routingScrollState: ScrollState,
    systemScrollState: ScrollState,
) {
    val pagerState = rememberPagerState(pageCount = { SettingsTab.entries.size })

    // One direction only — the pager is not user-scrollable, so it never moves on its own and
    // selectedTab is the single source of truth, which is why no pager-to-tab write-back
    // exists here (docs/ui-component-guidelines.md 2.11).
    LaunchedEffect(selectedTab) {
        pagerState.animateScrollToPage(selectedTab.ordinal)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ComposeColor(AppConfig.uiBackground))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(vertical = 3.dp)
        ) {
            // ── Header row: title + close (back) button ───────────────────
            DrawerHeader(
                title = stringResource(R.string.settings_title),
                onClose = onDismiss,
            )

            // ── Tab bar — scrollable, content-sized cells + full-cell underline ──
            val tabColor = ComposeColor(AppConfig.uiAccent)
            SecondaryScrollableTabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = ComposeColor(AppConfig.uiBackground),
                edgePadding = 24.dp,
                divider = {},
            ) {
                SettingsTab.entries.forEach { tab ->
                    val isSelected = selectedTab == tab
                    Box(
                        modifier = Modifier
                            .selectable(
                                selected = isSelected,
                                role = Role.Tab,
                                onClick = { onTabChange(tab) }
                            )
                            .padding(horizontal = 8.dp, vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(tab.labelRes),
                            color = if (isSelected) tabColor else ComposeColor(AppConfig.uiTextSecondary),
                            fontSize = AppConfig.uiFontTabSize.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Tab content ───────────────────────────────────────────────
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = false,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp)
                ) {
                    // One mapping: the tab names both its content and its own scroll state, so no
                    // argument order carries meaning and the order lives in SettingsTab alone.
                    when (val tab = SettingsTab.entries[page]) {
                        SettingsTab.LAYERS -> LayersSettings(
                            settings, onUpdateSettings, onImportTracks, onExportAllTracks, layersScrollState
                        )
                        SettingsTab.ROUTING -> RoutingSettings(settings, onUpdateSettings, routingScrollState)
                        SettingsTab.NAVIGATION -> NavigationSettings(settings, onUpdateSettings, navigationScrollState)
                        SettingsTab.SYSTEM -> SystemSettings(
                            settings, onUpdateSettings, onRegenerateRasters, onDismiss, systemScrollState
                        )
                    }
                }
            }

            // ── Footer ────────────────────────────────────────────────────
            Text(
                text = stringResource(R.string.app_version_footer),
                color = ComposeColor(AppConfig.uiFooterText),
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(horizontal = 24.dp)
            )
        }
    }
}

private enum class DisplayTrackAxis { ARROWS, COLOURS }

// ── Layers tab ────────────────────────────────────────────────────────────

@Composable
private fun LayersSettings(
    settings: AppSettings,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    onImportTracks: () -> Unit,
    onExportAllTracks: () -> Unit,
    scrollState: ScrollState
) {
    val settingsVm = androidx.lifecycle.viewmodel.compose.viewModel<SettingsViewModel>()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        // ── Tracks and Routes ───────────────────────────────────────────
        SectionHeader(title = stringResource(R.string.settings_section_tracks_and_routes))
        Spacer(modifier = Modifier.height(AppConfig.uiSpacingHeaderBottom.dp))
        CardArea {
            CardDescription(stringResource(R.string.settings_tracks_and_routes_desc))

            Expander(
                label = stringResource(R.string.settings_track_settings_label),
                expanded = settingsVm.isExpanded("track_rendering"),
                onToggle = { settingsVm.setExpanded("track_rendering", !settingsVm.isExpanded("track_rendering")) }
            ) {
                Spacer(Modifier.height(AppConfig.uiSpacingExpanderToContent.dp))
                NestedCard {
                    CardDescription(stringResource(R.string.settings_track_appearance_desc))
                    // Number of tracks
                    Text(
                        text = stringResource(R.string.settings_tracks_count_label),
                        color = ComposeColor(AppConfig.uiTextPrimary),
                        fontSize = AppConfig.uiFontToggleSize.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.settings_tracks_count_desc),
                            color = ComposeColor(AppConfig.uiTextMuted),
                            fontSize = AppConfig.uiFontDescSize.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "%d".format(settings.trackingRenderNb),
                            color = ComposeColor(AppConfig.uiValueText),
                            fontSize = AppConfig.uiFontValueSize.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Slider(
                        value = settings.trackingRenderNb.toFloat(),
                        onValueChange = { v ->
                            onUpdateSettings { it.copy(trackingRenderNb = v.roundToInt().coerceIn(0, 20)) }
                        },
                        valueRange = 0f..20f,
                        steps = 20,
                        colors = SliderDefaults.colors(
                            thumbColor = ComposeColor(AppConfig.uiAccent),
                            activeTrackColor = ComposeColor(AppConfig.uiAccent),
                            inactiveTrackColor = ComposeColor(AppConfig.uiSwitchTrackInactive)
                        )
                    )

                    SectionDivider()

                    // Opacity
                    RangeSliderRow(
                        label = stringResource(R.string.settings_transparency_label),
                        description = stringResource(R.string.settings_transparency_desc),
                        valueLabel = stringResource(
                            R.string.settings_transparency_value_fmt,
                            settings.trackingTransparencyNewest, settings.trackingTransparencyOldest
                        ),
                        value = settings.trackingTransparencyNewest.toFloat()..settings.trackingTransparencyOldest.toFloat(),
                        valueRange = 0f..100f,
                        steps = 19,
                        onValueChange = { range ->
                            onUpdateSettings {
                                it.copy(
                                    trackingTransparencyNewest = range.start.roundToInt(),
                                    trackingTransparencyOldest = range.endInclusive.roundToInt()
                                )
                            }
                        }
                    )

                    SectionDivider()

                    // Pinned tracks opacity
                    RangeSliderRow(
                        label = stringResource(R.string.settings_pinned_transparency_label),
                        description = stringResource(R.string.settings_pinned_transparency_desc),
                        valueLabel = stringResource(
                            R.string.settings_transparency_value_fmt,
                            settings.trackingTransparencyPinnedNewest,
                            settings.trackingTransparencyPinnedOldest
                        ),
                        value = settings.trackingTransparencyPinnedNewest.toFloat()
                            ..settings.trackingTransparencyPinnedOldest.toFloat(),
                        valueRange = 0f..100f,
                        steps = 19,
                        onValueChange = { range ->
                            onUpdateSettings {
                                it.copy(
                                    trackingTransparencyPinnedNewest = range.start.roundToInt(),
                                    trackingTransparencyPinnedOldest = range.endInclusive.roundToInt()
                                )
                            }
                        }
                    )

                    Spacer(Modifier.height(8.dp))

                    // Colors
                    Text(
                        text = stringResource(R.string.settings_colors_label),
                        color = ComposeColor(AppConfig.uiTextPrimary),
                        fontSize = AppConfig.uiFontToggleSize.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(R.string.settings_colors_desc),
                        color = ComposeColor(AppConfig.uiTextMuted),
                        fontSize = 12.sp
                    )
                    ColorRow(
                        label = stringResource(R.string.settings_color_active_track),
                        color = settings.trackingColorActive,
                        onColorSelected = { c -> onUpdateSettings { it.copy(trackingColorActive = c) } }
                    )
                    ColorPairRow(
                        label = stringResource(R.string.settings_color_past_tracks),
                        fromColor = settings.trackingColorPastFrom,
                        toColor = settings.trackingColorPastTo,
                        onFromColorSelected = { c -> onUpdateSettings { it.copy(trackingColorPastFrom = c) } },
                        onToColorSelected = { c -> onUpdateSettings { it.copy(trackingColorPastTo = c) } }
                    )
                    ColorPairRow(
                        label = stringResource(R.string.settings_color_pinned_tracks),
                        fromColor = settings.trackingColorPinnedFrom,
                        toColor = settings.trackingColorPinnedTo,
                        onFromColorSelected = { c -> onUpdateSettings { it.copy(trackingColorPinnedFrom = c) } },
                        onToColorSelected = { c -> onUpdateSettings { it.copy(trackingColorPinnedTo = c) } }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Expander(
                label = stringResource(R.string.settings_tracks_direction_settings_label),
                expanded = settingsVm.isExpanded("track_direction"),
                onToggle = { settingsVm.setExpanded("track_direction", !settingsVm.isExpanded("track_direction")) }
            ) {
                Spacer(Modifier.height(4.dp))
                NestedCard {
                    SubSectionHeader(
                        title = stringResource(R.string.settings_tracks_speed_display_label)
                    )

                    Spacer(Modifier.height(8.dp))

                    MultiSelectRow(
                        options = listOf(
                            DisplayTrackAxis.ARROWS to stringResource(R.string.menu_render_arrows),
                            DisplayTrackAxis.COLOURS to stringResource(R.string.menu_render_colours)
                        ),
                        isOn = { axis ->
                            when (axis) {
                                DisplayTrackAxis.ARROWS -> settings.trackArrows
                                DisplayTrackAxis.COLOURS -> settings.trackColours
                            }
                        },
                        onToggle = { axis ->
                            when (axis) {
                                DisplayTrackAxis.ARROWS -> onUpdateSettings { it.copy(trackArrows = !it.trackArrows) }
                                DisplayTrackAxis.COLOURS -> onUpdateSettings { it.copy(trackColours = !it.trackColours) }
                            }
                        }
                    )

                    SectionDivider()

                    SubSectionHeader(
                        title = stringResource(R.string.settings_tracks_direction_density_label)
                    )

                    Spacer(Modifier.height(8.dp))

                    SegmentedRow(
                        options = listOf(
                            TrackDirectionDensity.UNIFORM to stringResource(R.string.settings_tracks_direction_density_uniform),
                            TrackDirectionDensity.SPEED to stringResource(R.string.settings_tracks_direction_density_speed)
                        ),
                        selected = settings.trackDirectionDensity,
                        onSelect = { mode -> onUpdateSettings { it.copy(trackDirectionDensity = mode) } }
                    )

                    Spacer(Modifier.height(8.dp))

                    SubSectionHeader(
                        title = stringResource(R.string.settings_tracks_direction_gap_range_label),
                        description = stringResource(R.string.settings_tracks_direction_gap_range_desc)
                    )
                    RangeSliderRow(
                        valueLabel = stringResource(R.string.settings_tracks_direction_gap_range_fmt,
                            settings.trackDirectionMinSpacingDp, settings.trackDirectionMaxSpacingDp),
                        value = logSliderFromValue(settings.trackDirectionMinSpacingDp.toFloat(), DIRECTION_GAP_MIN_DP, DIRECTION_GAP_MAX_DP)
                            ..logSliderFromValue(settings.trackDirectionMaxSpacingDp.toFloat(), DIRECTION_GAP_MIN_DP, DIRECTION_GAP_MAX_DP),
                        valueRange = 0f..1f,
                        steps = 23,
                        onValueChange = { range ->
                            onUpdateSettings {
                                it.copy(
                                    trackDirectionMinSpacingDp = logSliderToValue(range.start, DIRECTION_GAP_MIN_DP, DIRECTION_GAP_MAX_DP).roundToInt(),
                                    trackDirectionMaxSpacingDp = logSliderToValue(range.endInclusive, DIRECTION_GAP_MIN_DP, DIRECTION_GAP_MAX_DP).roundToInt()
                                )
                            }
                        }
                    )
                    SectionDivider()
                    SubSectionHeader(
                        title = stringResource(R.string.settings_tracks_direction_speed_range_label),
                        description = stringResource(R.string.settings_tracks_direction_speed_range_desc)
                    )
                    RangeSliderRow(
                        valueLabel = stringResource(R.string.settings_tracks_direction_speed_range_fmt,
                            settings.trackDirectionSpeedFloorKn, settings.trackDirectionSpeedCeilingKn),
                        value = logSliderFromValue(settings.trackDirectionSpeedFloorKn, DIRECTION_SPEED_MIN_KN, DIRECTION_SPEED_MAX_KN)
                            ..logSliderFromValue(settings.trackDirectionSpeedCeilingKn, DIRECTION_SPEED_MIN_KN, DIRECTION_SPEED_MAX_KN),
                        valueRange = 0f..1f,
                        steps = 23,
                        onValueChange = { range ->
                            onUpdateSettings {
                                it.copy(
                                    trackDirectionSpeedFloorKn = (logSliderToValue(range.start, DIRECTION_SPEED_MIN_KN, DIRECTION_SPEED_MAX_KN) * 10f).roundToInt() / 10f,
                                    trackDirectionSpeedCeilingKn = (logSliderToValue(range.endInclusive, DIRECTION_SPEED_MIN_KN, DIRECTION_SPEED_MAX_KN) * 10f).roundToInt() / 10f
                                )
                            }
                        }
                    )
                }
            }

            // ── The routes' own rendering values (S17/D15) — the card's 2nd section ──
            // Divided from the track group above (2026-10-05, the user's word) and from the export/import
            // row that closes the card; the two route collapsibles stand adjacent within this section, with
            // **no divider between them** (2026-10-05, the user's word — the one that stood between Routes
            // Appearance and Routes Speed and Direction was removed).
            SectionDivider()
            Expander(
                label = stringResource(R.string.settings_routes_appearance_label),
                expanded = settingsVm.isExpanded("routes_appearance"),
                onToggle = { settingsVm.setExpanded("routes_appearance", !settingsVm.isExpanded("routes_appearance")) }
            ) {
                Spacer(Modifier.height(AppConfig.uiSpacingExpanderToContent.dp))
                NestedCard {
                    CardDescription(stringResource(R.string.settings_routes_appearance_desc))
                    // The Routes count, the route ladder, the pinned-route ladder and every route
                    // colour: a route's whole appearance is edited in one place (D5, D6, D8).
                    Text(
                        text = stringResource(R.string.settings_routes_count_label),
                        color = ComposeColor(AppConfig.uiTextPrimary),
                        fontSize = AppConfig.uiFontToggleSize.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.settings_routes_count_desc),
                            color = ComposeColor(AppConfig.uiTextMuted),
                            fontSize = AppConfig.uiFontDescSize.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "%d".format(settings.routeRenderNb),
                            color = ComposeColor(AppConfig.uiValueText),
                            fontSize = AppConfig.uiFontValueSize.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Slider(
                        value = settings.routeRenderNb.toFloat(),
                        onValueChange = { v ->
                            onUpdateSettings { it.copy(routeRenderNb = v.roundToInt().coerceIn(0, 20)) }
                        },
                        valueRange = 0f..20f,
                        steps = 20,
                        colors = SliderDefaults.colors(
                            thumbColor = ComposeColor(AppConfig.uiAccent),
                            activeTrackColor = ComposeColor(AppConfig.uiAccent),
                            inactiveTrackColor = ComposeColor(AppConfig.uiSwitchTrackInactive)
                        )
                    )

                    SectionDivider()

                    // The route ladder: the pin bounds the non-pinned routes alone, a pinned route drawn
                    // whatever it says.
                    RangeSliderRow(
                        label = stringResource(R.string.settings_route_transparency_label),
                        description = stringResource(R.string.settings_route_transparency_desc),
                        valueLabel = stringResource(
                            R.string.settings_transparency_value_fmt,
                            settings.trackingTransparencyRouteNewest,
                            settings.trackingTransparencyRouteOldest
                        ),
                        value = settings.trackingTransparencyRouteNewest.toFloat()
                            ..settings.trackingTransparencyRouteOldest.toFloat(),
                        valueRange = 0f..100f,
                        steps = 19,
                        onValueChange = { range ->
                            onUpdateSettings {
                                it.copy(
                                    trackingTransparencyRouteNewest = range.start.roundToInt(),
                                    trackingTransparencyRouteOldest = range.endInclusive.roundToInt()
                                )
                            }
                        }
                    )

                    SectionDivider()

                    // The pinned route's own ladder (D5, D8): a pinned route runs the pinned path but
                    // fades across this range, over its own pair below.
                    RangeSliderRow(
                        label = stringResource(R.string.settings_pinned_route_transparency_label),
                        description = stringResource(R.string.settings_pinned_route_transparency_desc),
                        valueLabel = stringResource(
                            R.string.settings_transparency_value_fmt,
                            settings.trackingTransparencyPinnedRouteNewest,
                            settings.trackingTransparencyPinnedRouteOldest
                        ),
                        value = settings.trackingTransparencyPinnedRouteNewest.toFloat()
                            ..settings.trackingTransparencyPinnedRouteOldest.toFloat(),
                        valueRange = 0f..100f,
                        steps = 19,
                        onValueChange = { range ->
                            onUpdateSettings {
                                it.copy(
                                    trackingTransparencyPinnedRouteNewest = range.start.roundToInt(),
                                    trackingTransparencyPinnedRouteOldest = range.endInclusive.roundToInt()
                                )
                            }
                        }
                    )

                    Spacer(Modifier.height(8.dp))

                    // Colours
                    Text(
                        text = stringResource(R.string.settings_colors_label),
                        color = ComposeColor(AppConfig.uiTextPrimary),
                        fontSize = AppConfig.uiFontToggleSize.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(R.string.settings_routes_colors_desc),
                        color = ComposeColor(AppConfig.uiTextMuted),
                        fontSize = 12.sp
                    )
                    // The followed route's own line colour: **one key**, `route.line.color`.
                    ColorRow(
                        label = stringResource(R.string.settings_color_active_route),
                        color = settings.routeLineColor,
                        onColorSelected = { c -> onUpdateSettings { it.copy(routeLineColor = c) } }
                    )
                    ColorPairRow(
                        label = stringResource(R.string.settings_color_routes),
                        fromColor = settings.trackingColorRouteFrom,
                        toColor = settings.trackingColorRouteTo,
                        onFromColorSelected = { c -> onUpdateSettings { it.copy(trackingColorRouteFrom = c) } },
                        onToColorSelected = { c -> onUpdateSettings { it.copy(trackingColorRouteTo = c) } }
                    )
                    ColorPairRow(
                        label = stringResource(R.string.settings_color_pinned_routes),
                        fromColor = settings.trackingColorPinnedRouteFrom,
                        toColor = settings.trackingColorPinnedRouteTo,
                        onFromColorSelected = { c -> onUpdateSettings { it.copy(trackingColorPinnedRouteFrom = c) } },
                        onToColorSelected = { c -> onUpdateSettings { it.copy(trackingColorPinnedRouteTo = c) } }
                    )
                }
            }

            Expander(
                label = stringResource(R.string.settings_routes_speed_direction_label),
                expanded = settingsVm.isExpanded("routes_speed_direction"),
                onToggle = { settingsVm.setExpanded("routes_speed_direction", !settingsVm.isExpanded("routes_speed_direction")) }
            ) {
                Spacer(Modifier.height(AppConfig.uiSpacingExpanderToContent.dp))
                NestedCard {
                    CardDescription(stringResource(R.string.settings_routes_speed_direction_desc))
                    ToggleRow(
                        label = stringResource(R.string.settings_routes_speed_color_label),
                        labelStyle = ToggleLabelStyle.COMMENT,
                        checked = settings.routeSpeedColor,
                        onCheckedChange = { on -> onUpdateSettings { it.copy(routeSpeedColor = on) } }
                    )
                    Spacer(Modifier.height(AppConfig.uiSpacingGroupedRowGap.dp))
                    ToggleRow(
                        label = stringResource(R.string.settings_routes_arrows_label),
                        labelStyle = ToggleLabelStyle.COMMENT,
                        checked = settings.routeSpeedArrows,
                        onCheckedChange = { on -> onUpdateSettings { it.copy(routeSpeedArrows = on) } }
                    )
                }
            }

            SectionDivider()
            CardDescription(stringResource(R.string.settings_tracks_transfer_desc))
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

        Spacer(modifier = Modifier.height(AppConfig.uiSpacingSectionGap.dp))

        // ── Markers ─────────────────────────────────────────────────────
        SectionHeader(title = stringResource(R.string.settings_section_markers))
        Spacer(modifier = Modifier.height(AppConfig.uiSpacingHeaderBottom.dp))
        CardArea {
            CardDescription(stringResource(R.string.settings_markers_desc))
            ToggleRow(
                label = stringResource(R.string.menu_show_zones),
                checked = settings.markerZonesVisible,
                onCheckedChange = { on -> onUpdateSettings { it.copy(markerZonesVisible = on) } }
            )
            SectionDivider()
            Expander(
                label = stringResource(R.string.settings_marker_rendering_label),
                expanded = settingsVm.isExpanded("markers_rendering"),
                onToggle = { settingsVm.setExpanded("markers_rendering", !settingsVm.isExpanded("markers_rendering")) }
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                NestedCard {
                    // Point/icon rendering zoom (50-150 %, 100 = current size)
                    SliderRow(
                        label = stringResource(R.string.settings_marker_zoom_label),
                        description = stringResource(R.string.settings_marker_zoom_desc),
                        valueLabel = "%d%%".format(settings.markerPointIconZoom),
                        value = settings.markerPointIconZoom.toFloat(),
                        valueRange = 50f..150f,
                        steps = 19,
                        onValueChange = { v ->
                            onUpdateSettings { it.copy(markerPointIconZoom = v.roundToInt().coerceIn(50, 150)) }
                        }
                    )
                    SectionDivider()

                    // Halo size
                    SliderRow(
                        label = stringResource(R.string.settings_marker_halo_size_label),
                        description = stringResource(R.string.settings_marker_halo_size_desc),
                        valueLabel = "%d%%".format(settings.markerHaloSize),
                        value = settings.markerHaloSize.toFloat(),
                        valueRange = 0f..100f,
                        steps = 19,
                        onValueChange = { v ->
                            onUpdateSettings { it.copy(markerHaloSize = v.roundToInt().coerceIn(0, 100)) }
                        }
                    )
                    SectionDivider()

                    // Opacity section
                    SubSectionHeader(title = stringResource(R.string.settings_transparency_border_fill_label))
                    Spacer(modifier = Modifier.height(AppConfig.uiSpacingGroupedRowGap.dp))

                    // Transparency: 0 = opaque, 100 = invisible. The strong border has low
                    // transparency (left thumb); the faint fill has high transparency (right thumb).
                    RangeSliderRow(
                        label = stringResource(R.string.settings_marker_halo_pinned_label),
                        valueLabel = stringResource(R.string.settings_transparency_border_fill_value_fmt,
                            settings.markerHaloPinnedBorderTransparencyPct,
                            settings.markerHaloPinnedFillTransparencyPct),
                        value = settings.markerHaloPinnedBorderTransparencyPct.toFloat()
                            ..settings.markerHaloPinnedFillTransparencyPct.toFloat(),
                        valueRange = 0f..100f,
                        steps = 19,
                        onValueChange = { range ->
                            onUpdateSettings {
                                it.copy(
                                    markerHaloPinnedBorderTransparencyPct = range.start.roundToInt(),
                                    markerHaloPinnedFillTransparencyPct = range.endInclusive.roundToInt()
                                )
                            }
                        }
                    )

                    // Transparency: 0 = opaque, 100 = invisible. The strong border has low
                    // transparency (left thumb); the faint fill has high transparency (right thumb).
                    RangeSliderRow(
                        label = stringResource(R.string.settings_marker_halo_unpinned_label),
                        valueLabel = stringResource(R.string.settings_transparency_border_fill_value_fmt,
                            settings.markerHaloUnpinnedBorderTransparencyPct,
                            settings.markerHaloUnpinnedFillTransparencyPct),
                        value = settings.markerHaloUnpinnedBorderTransparencyPct.toFloat()
                            ..settings.markerHaloUnpinnedFillTransparencyPct.toFloat(),
                        valueRange = 0f..100f,
                        steps = 19,
                        onValueChange = { range ->
                            onUpdateSettings {
                                it.copy(
                                    markerHaloUnpinnedBorderTransparencyPct = range.start.roundToInt(),
                                    markerHaloUnpinnedFillTransparencyPct = range.endInclusive.roundToInt()
                                )
                            }
                        }
                    )
                    SectionDivider()

                    // Colors section
                    SubSectionHeader(title = stringResource(R.string.settings_marker_halo_colors_label))
                    Spacer(modifier = Modifier.height(AppConfig.uiSpacingGroupedAfterExpander.dp))
                    ColorRow(
                        label = stringResource(R.string.settings_marker_halo_pinned_color_label),
                        color = settings.markerHaloPinnedColor,
                        onColorSelected = { c -> onUpdateSettings { it.copy(markerHaloPinnedColor = c) } }
                    )
                    ColorRow(
                        label = stringResource(R.string.settings_marker_halo_unpinned_color_label),
                        color = settings.markerHaloUnpinnedColor,
                        onColorSelected = { c -> onUpdateSettings { it.copy(markerHaloUnpinnedColor = c) } }
                    )
                }
            }
            Spacer(Modifier.height(AppConfig.uiSpacingGroupedAfterExpander.dp))
            Expander(
                label = stringResource(R.string.settings_auto_markers_label),
                expanded = settingsVm.isExpanded("markers_auto"),
                onToggle = { settingsVm.setExpanded("markers_auto", !settingsVm.isExpanded("markers_auto")) }
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                NestedCard {
                    SliderRow(
                        label = stringResource(R.string.settings_marker_idle_threshold_label),
                        description = stringResource(R.string.settings_marker_idle_threshold_desc),
                        valueLabel = stringResource(R.string.settings_value_seconds, settings.boatMarkerIdleThresholdSec),
                        value = settings.boatMarkerIdleThresholdSec.toFloat(),
                        valueRange = 10f..600f,
                        steps = 59,
                        onValueChange = { v ->
                            onUpdateSettings { it.copy(boatMarkerIdleThresholdSec = v.roundToInt().toLong()) }
                        }
                    )
                    SectionDivider()
                    SliderRow(
                        label = stringResource(R.string.settings_marker_min_duration_label),
                        description = stringResource(R.string.settings_marker_min_duration_desc),
                        valueLabel = stringResource(R.string.settings_value_seconds, settings.boatMarkerAutoMarkerMinDurationSec),
                        value = settings.boatMarkerAutoMarkerMinDurationSec.toFloat(),
                        valueRange = 30f..3600f,
                        steps = 119,
                        onValueChange = { v ->
                            onUpdateSettings { it.copy(boatMarkerAutoMarkerMinDurationSec = v.roundToInt().toLong()) }
                        }
                    )
                    SectionDivider()
                    SliderRow(
                        label = stringResource(R.string.settings_marker_dedup_radius_label),
                        description = stringResource(R.string.settings_marker_dedup_radius_desc),
                        valueLabel = stringResource(R.string.settings_value_meters, settings.boatMarkerAutoMarkerDedupRadiusM.roundToInt()),
                        value = settings.boatMarkerAutoMarkerDedupRadiusM.toFloat(),
                        valueRange = 1f..100f,
                        steps = 98,
                        onValueChange = { v ->
                            onUpdateSettings { it.copy(boatMarkerAutoMarkerDedupRadiusM = v.roundToInt().toDouble()) }
                        }
                    )
                }
            }
            Spacer(Modifier.height(AppConfig.uiSpacingGroupedAfterExpander.dp))
        }

        Spacer(modifier = Modifier.height(AppConfig.uiSpacingSectionGap.dp))

        // ── Regulated zones ─────────────────────────────────────────────
        SectionHeader(title = stringResource(R.string.settings_section_regulated_zones))
        Spacer(modifier = Modifier.height(AppConfig.uiSpacingHeaderBottom.dp))
        CardArea {
            CardDescription(stringResource(R.string.settings_regulated_zones_desc))

            // Appearance first: the only control in this card that changes how the zones look
            // rather than what they report. Mirrors the 300 m band's Appearance expander.
            Expander(
                label = stringResource(R.string.settings_regulated_zones_appearance_label),
                expanded = settingsVm.isExpanded("reg_appearance"),
                onToggle = { settingsVm.setExpanded("reg_appearance", !settingsVm.isExpanded("reg_appearance")) }
            ) {
                Spacer(Modifier.height(8.dp))
                NestedCard {
                    SubSectionHeader(
                        title = stringResource(R.string.settings_transparency_border_fill_label),
                        description = stringResource(R.string.settings_regulated_zones_transparency_desc)
                    )
                    // Transparency: 0 = opaque, 100 = invisible. The outline is strong (low
                    // transparency) so it sits on the left thumb; the faint fill has high
                    // transparency so it sits on the right thumb. Same 5% snap and commit-on-release
                    // as the 300 m row — the setting is written on drag end, never per tick.
                    var transparencyDrag by remember {
                        mutableStateOf(settings.regulatedZoneBoundaryTransparencyPct.toFloat()..settings.regulatedZoneFillTransparencyPct.toFloat())
                    }
                    RangeSliderRow(
                        valueLabel = stringResource(
                            R.string.settings_transparency_border_fill_value_fmt,
                            (transparencyDrag.start / 5f).roundToInt() * 5,
                            (transparencyDrag.endInclusive / 5f).roundToInt() * 5
                        ),
                        value = transparencyDrag,
                        valueRange = 0f..100f,
                        steps = 19,
                        onValueChange = { range -> transparencyDrag = range },
                        onValueChangeFinished = {
                            onUpdateSettings {
                                it.copy(
                                    regulatedZoneBoundaryTransparencyPct = (transparencyDrag.start / 5f).roundToInt() * 5,
                                    regulatedZoneFillTransparencyPct = (transparencyDrag.endInclusive / 5f).roundToInt() * 5
                                )
                            }
                        }
                    )
                    SectionDivider()
                    // Outline stroke width (dp): a 0.5 dp grid, committed on release like the pair above.
                    var widthDrag by remember { mutableStateOf(settings.regulatedZoneOutlineWidthDp) }
                    SliderRow(
                        label = stringResource(R.string.settings_width_label),
                        description = stringResource(R.string.settings_regulated_zones_outline_width_desc),
                        valueLabel = stringResource(R.string.settings_value_dp_fmt, widthDrag),
                        value = widthDrag,
                        valueRange = 0.5f..8f,
                        steps = 14,
                        onValueChange = { v -> widthDrag = v },
                        onValueChangeFinished = {
                            onUpdateSettings { it.copy(regulatedZoneOutlineWidthDp = widthDrag) }
                        }
                    )
                }
            }
            Spacer(Modifier.height(AppConfig.uiSpacingGroupedRowGap.dp))

            // Regulation info — collapsible toggle for info text panel
            Expander(
                label = stringResource(R.string.settings_reg_info_settings_label),
                expanded = settingsVm.isExpanded("reg_info"),
                onToggle = { settingsVm.setExpanded("reg_info", !settingsVm.isExpanded("reg_info")) }
            ) {
                Spacer(Modifier.height(8.dp))
                NestedCard {
                    Text(
                        text = stringResource(R.string.settings_reg_info_desc),
                        color = ComposeColor(AppConfig.uiDashboardTextMuted),
                        fontSize = AppConfig.uiFontDescSize.sp,
                        modifier = Modifier.padding(bottom = AppConfig.uiSpacingHeaderBottom.dp)
                    )
                    ToggleRow(
                        label = stringResource(R.string.settings_reg_info_visible),
                        checked = settings.regulationInfoVisible,
                        onCheckedChange = { visible ->
                            onUpdateSettings { it.copy(regulationInfoVisible = visible) }
                        }
                    )
                    SectionDivider()
                    BoatSizeSlider(settings, onUpdateSettings)
                }
            }
            Spacer(Modifier.height(AppConfig.uiSpacingGroupedRowGap.dp))
            Expander(
                label = stringResource(R.string.settings_categories_label),
                expanded = settingsVm.isExpanded("reg_categories"),
                onToggle = { settingsVm.setExpanded("reg_categories", !settingsVm.isExpanded("reg_categories")) }
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                NestedCard {
                    CategoryToggleGroup(settings, onUpdateSettings)
                }
            }
            Spacer(Modifier.height(AppConfig.uiSpacingGroupedAfterExpander.dp))
        }

        Spacer(modifier = Modifier.height(AppConfig.uiSpacingSectionGap.dp))

        // ── 300m Band ───────────────────────────────────────────────────
        SectionHeader(title = stringResource(R.string.settings_section_zone300))
        Spacer(modifier = Modifier.height(AppConfig.uiSpacingHeaderBottom.dp))
        CardArea {
            CardDescription(stringResource(R.string.settings_zone300_desc))
            Expander(
                label = stringResource(R.string.settings_zone300_appearance_label),
                expanded = settingsVm.isExpanded("zone300_appearance"),
                onToggle = { settingsVm.setExpanded("zone300_appearance", !settingsVm.isExpanded("zone300_appearance")) }
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                NestedCard {
                    SubSectionHeader(
                        title = stringResource(R.string.settings_transparency_border_fill_label),
                        description = stringResource(R.string.settings_zone300_opacity_desc)
                    )
                    // Transparency: 0 = opaque, 100 = invisible. The boundary is strong
                    // (low transparency) so it sits on the left thumb; the faint fill has
                    // high transparency so it sits on the right thumb.
                    var transparencyDrag by remember {
                        mutableStateOf(settings.zone300BoundaryTransparencyPct.toFloat()..settings.zone300FillTransparencyPct.toFloat())
                    }
                    RangeSliderRow(
                        valueLabel = stringResource(
                            R.string.settings_transparency_border_fill_value_fmt,
                            (transparencyDrag.start / 5f).roundToInt() * 5,
                            (transparencyDrag.endInclusive / 5f).roundToInt() * 5
                        ),
                        value = transparencyDrag,
                        valueRange = 0f..100f,
                        steps = 19,
                        onValueChange = { range -> transparencyDrag = range },
                        onValueChangeFinished = {
                            onUpdateSettings {
                                it.copy(
                                    zone300BoundaryTransparencyPct = (transparencyDrag.start / 5f).roundToInt() * 5,
                                    zone300FillTransparencyPct = (transparencyDrag.endInclusive / 5f).roundToInt() * 5
                                )
                            }
                        }
                    )
                    SectionDivider()
                    // Boundary stroke width (dp): a 0.5 dp grid, committed on release like the pair above.
                    var widthDrag by remember { mutableStateOf(settings.zone300BoundaryWidthDp) }
                    SliderRow(
                        label = stringResource(R.string.settings_width_label),
                        description = stringResource(R.string.settings_zone300_boundary_width_desc),
                        valueLabel = stringResource(R.string.settings_value_dp_fmt, widthDrag),
                        value = widthDrag,
                        valueRange = 0.5f..8f,
                        steps = 14,
                        onValueChange = { v -> widthDrag = v },
                        onValueChangeFinished = {
                            onUpdateSettings { it.copy(zone300BoundaryWidthDp = widthDrag) }
                        }
                    )
                    SectionDivider()
                    // Single colour control → SingleColorSubSection: the SubSectionHeader title
                    // row carries the 24dp swatch; description sits below (ui-component-guidelines §2.4).
                    SingleColorSubSection(
                        title = stringResource(R.string.settings_zone300_color_label),
                        description = stringResource(R.string.settings_zone300_color_desc),
                        color = settings.zone300Color,
                        onColorSelected = { c -> onUpdateSettings { it.copy(zone300Color = c) } }
                    )
                }
            }
            Spacer(Modifier.height(AppConfig.uiSpacingGroupedAfterExpander.dp))
        }

        Spacer(modifier = Modifier.height(AppConfig.uiSpacingSectionGap.dp))

        // ── Coastline — the only on/off without a map-fan button ───────
        SectionHeader(title = stringResource(R.string.settings_section_coastline))
        Spacer(modifier = Modifier.height(AppConfig.uiSpacingHeaderBottom.dp))
        CardArea {
            ToggleRow(
                label = stringResource(R.string.settings_coastline_label),
                description = stringResource(R.string.settings_coastline_desc),
                checked = settings.coastlineVisible,
                onCheckedChange = { visible -> onUpdateSettings { it.copy(coastlineVisible = visible) } }
            )
            Spacer(Modifier.height(AppConfig.uiSpacingGroupedAfterExpander.dp))
            // The shoreline's own look, behind an expander like its two zone neighbours: width,
            // transparency, then the mainland/island colour pair.
            Expander(
                label = stringResource(R.string.settings_coastline_appearance_label),
                expanded = settingsVm.isExpanded("coastline_appearance"),
                onToggle = { settingsVm.setExpanded("coastline_appearance", !settingsVm.isExpanded("coastline_appearance")) }
            ) {
                Spacer(Modifier.height(8.dp))
                NestedCard {
                    // Width (dp): a 0.5 dp grid, committed on release.
                    var widthDrag by remember { mutableStateOf(settings.coastlineWidthDp) }
                    SliderRow(
                        label = stringResource(R.string.settings_width_label),
                        description = stringResource(R.string.settings_coastline_width_desc),
                        valueLabel = stringResource(R.string.settings_value_dp_fmt, widthDrag),
                        value = widthDrag,
                        valueRange = 0.5f..8f,
                        steps = 14,
                        onValueChange = { v -> widthDrag = v },
                        onValueChangeFinished = {
                            onUpdateSettings { it.copy(coastlineWidthDp = widthDrag) }
                        }
                    )
                    SectionDivider()
                    // Transparency: 0 = opaque, 100 = invisible — the app-wide convention.
                    var transparencyDrag by remember { mutableStateOf(settings.coastlineTransparencyPct.toFloat()) }
                    SliderRow(
                        label = stringResource(R.string.settings_transparency_border_fill_label),
                        description = stringResource(R.string.settings_coastline_transparency_desc),
                        valueLabel = stringResource(R.string.settings_value_percent, transparencyDrag.roundToInt()),
                        value = transparencyDrag,
                        valueRange = 0f..100f,
                        steps = 19,
                        onValueChange = { v -> transparencyDrag = v },
                        onValueChangeFinished = {
                            onUpdateSettings { it.copy(coastlineTransparencyPct = transparencyDrag.roundToInt()) }
                        }
                    )
                    SectionDivider()
                    SubSectionHeader(
                        title = stringResource(R.string.settings_coastline_colors_label),
                        description = stringResource(R.string.settings_coastline_colors_desc)
                    )
                    // Mainland left, island right — the same pair component the track colours use.
                    ColorPairRow(
                        label = stringResource(R.string.settings_coastline_pair_label),
                        fromColor = settings.coastlineMainlandColor,
                        toColor = settings.coastlineIslandColor,
                        onFromColorSelected = { c -> onUpdateSettings { it.copy(coastlineMainlandColor = c) } },
                        onToColorSelected = { c -> onUpdateSettings { it.copy(coastlineIslandColor = c) } }
                    )
                }
            }
            Spacer(Modifier.height(AppConfig.uiSpacingGroupedAfterExpander.dp))
        }

        Spacer(modifier = Modifier.height(AppConfig.uiSpacingSectionGap.dp))

        // ── Land/Water icon — the row square's own on/off ──────────────
        SectionHeader(title = stringResource(R.string.settings_section_land_water_icon))
        Spacer(modifier = Modifier.height(AppConfig.uiSpacingHeaderBottom.dp))
        CardArea {
            ToggleRow(
                label = stringResource(R.string.settings_land_water_icon_label),
                description = stringResource(R.string.settings_land_water_icon_desc),
                checked = settings.showLandWaterIcon,
                onCheckedChange = { visible -> onUpdateSettings { it.copy(showLandWaterIcon = visible) } }
            )
        }

        Spacer(modifier = Modifier.height(AppConfig.uiSpacingSectionGap.dp))

        // ── Danger Zones (was: low-depth warning) ──────────────────────
        SectionHeader(title = stringResource(R.string.settings_section_danger_zones))
        Spacer(modifier = Modifier.height(AppConfig.uiSpacingHeaderBottom.dp))
        CardArea {
            CardDescription(stringResource(R.string.settings_danger_zones_desc))
            // Warning sliders — always visible, persisted expander
            Expander(
                label = stringResource(R.string.settings_low_depth_settings_label),
                expanded = settingsVm.isExpanded("danger_warning"),
                onToggle = { settingsVm.setExpanded("danger_warning", !settingsVm.isExpanded("danger_warning")) }
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                NestedCard {
                    // Two-depth double slider: left thumb = crash depth (fully opaque
                    // from surface down to here), right thumb = start-warning depth
                    // (warning begins here, transparent beyond). Linear ramp between.
                    // Local drag state so the ~7M-cell warning bitmap is not regenerated on
                    // every drag tick — commit to settings only on drag end. Values stay
                    // snapped to 0.5 m and crash is kept strictly below start (min 0.5 m gap).
                    var lowDepthDrag by remember {
                        mutableStateOf(settings.lowDepthCrashDepthM..settings.lowDepthStartWarningM)
                    }
                    RangeSliderRow(
                        label = stringResource(R.string.settings_low_depth_range_label),
                        description = stringResource(R.string.settings_low_depth_range_desc),
                        valueLabel = stringResource(R.string.settings_low_depth_range_value_fmt,
                            lowDepthDrag.start, lowDepthDrag.endInclusive),
                        value = lowDepthDrag,
                        valueRange = 0f..5f,
                        steps = 9,
                        onValueChange = { range ->
                            var crash = (range.start * 2f).roundToInt() / 2f
                            var start = (range.endInclusive * 2f).roundToInt() / 2f
                            // Enforce crashDepthM < startWarningM with a minimum 0.5 m gap.
                            if (start <= crash) {
                                if (range.start == range.endInclusive) {
                                    // Both thumbs at the same spot: keep crash, push start up.
                                    start = crash + 0.5f
                                } else {
                                    // Inverted/equal range — keep the gap by nudging the moved thumb.
                                    crash = (start - 0.5f).coerceAtLeast(0f)
                                }
                            }
                            lowDepthDrag = crash..start
                        },
                        onValueChangeFinished = {
                            onUpdateSettings {
                                it.copy(
                                    lowDepthCrashDepthM = lowDepthDrag.start,
                                    lowDepthStartWarningM = lowDepthDrag.endInclusive
                                )
                            }
                        }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.settings_value_depth, 0f),
                            color = ComposeColor(AppConfig.uiTextMuted),
                            fontSize = AppConfig.uiFontCommentSize.sp
                        )
                        Text(
                            text = stringResource(R.string.settings_value_depth, 5f),
                            color = ComposeColor(AppConfig.uiTextMuted),
                            fontSize = AppConfig.uiFontCommentSize.sp
                        )
                    }
                }
            }
            Spacer(Modifier.height(AppConfig.uiSpacingGroupedAfterExpander.dp))
        }

        Spacer(modifier = Modifier.height(AppConfig.uiSpacingSectionGap.dp))

        // ── Depth — EMODnet shallow filter ─────────────────────────────
        SectionHeader(title = stringResource(R.string.settings_section_depth_map))
        Spacer(modifier = Modifier.height(AppConfig.uiSpacingHeaderBottom.dp))
        CardArea {
            CardDescription(stringResource(R.string.settings_depth_desc))
            Expander(label = stringResource(R.string.settings_depth_cutoff_expander), expanded = settingsVm.isExpanded("depth_cutoff"),
                onToggle = { settingsVm.setExpanded("depth_cutoff", !settingsVm.isExpanded("depth_cutoff")) }
            ) {
                Spacer(Modifier.height(8.dp))
                NestedCard {
                    SliderRow(
                        label = stringResource(R.string.settings_emodnet_cutoff_label),
                        description = stringResource(R.string.settings_emodnet_cutoff_desc),
                        valueLabel = stringResource(R.string.settings_value_depth, settings.emodnetShallowCutoffM),
                        value = settings.emodnetShallowCutoffM, valueRange = 0f..5f, steps = 9,
                        onValueChange = { v -> onUpdateSettings { it.copy(emodnetShallowCutoffM = (v * 2f).roundToInt() / 2f) } }
                    )
                }
            }
            Spacer(Modifier.height(AppConfig.uiSpacingGroupedAfterExpander.dp))
        }
        Spacer(modifier = Modifier.height(AppConfig.uiSpacingCardGap.dp))
    }
}

// ── Navigation tab ────────────────────────────────────────────────────────

@Composable
private fun NavigationSettings(
    settings: AppSettings,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    scrollState: ScrollState
) {
    val settingsVm = androidx.lifecycle.viewmodel.compose.viewModel<SettingsViewModel>()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        // ── Stop detection ──────────────────────────────────────────────
        SectionHeader(title = stringResource(R.string.settings_section_stop_detection))
        Spacer(modifier = Modifier.height(AppConfig.uiSpacingHeaderBottom.dp))

        CardArea {
            ToggleRow(
                label = stringResource(R.string.settings_stop_enable_label),
                description = stringResource(R.string.settings_stop_enable_desc),
                checked = settings.stopDetectionEnabled,
                onCheckedChange = { on -> onUpdateSettings { it.copy(stopDetectionEnabled = on) } }
            )

            if (settings.stopDetectionEnabled) {
                Spacer(Modifier.height(AppConfig.uiSpacingGroupedRowGap.dp))

                Expander(
                    label = stringResource(R.string.settings_stop_thresholds_label),
                    expanded = settingsVm.isExpanded("stop_thresholds"),
                    onToggle = { settingsVm.setExpanded("stop_thresholds", !settingsVm.isExpanded("stop_thresholds")) }
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    NestedCard {
                        SliderRow(
                            label = stringResource(R.string.settings_window_label),
                            description = stringResource(R.string.settings_window_desc),
                            valueLabel = stringResource(R.string.settings_value_seconds, settings.stopDetectionTimeSec),
                            value = settings.stopDetectionTimeSec.toFloat(),
                            valueRange = 10f..90f,
                            steps = 15,
                            onValueChange = { v -> onUpdateSettings { it.copy(stopDetectionTimeSec = (v / 5f).roundToInt() * 5) } }
                        )
                        SectionDivider()
                        SliderRow(
                            label = stringResource(R.string.settings_adaptive_dist_label),
                            description = stringResource(R.string.settings_adaptive_dist_desc),
                            valueLabel = stringResource(R.string.settings_value_meters, settings.stopDetectionDistanceM),
                            value = settings.stopDetectionDistanceM.toFloat(),
                            valueRange = 10f..30f,
                            steps = 3,
                            onValueChange = { v -> onUpdateSettings { it.copy(stopDetectionDistanceM = (v / 5f).roundToInt() * 5) } }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(AppConfig.uiSpacingGroupedRowGap.dp))

                ToggleRow(
                    label = stringResource(R.string.settings_stop_delay_label),
                    description = stringResource(R.string.settings_stop_delay_desc),
                    checked = settings.stopDetectionDelayGps,
                    onCheckedChange = { on -> onUpdateSettings { it.copy(stopDetectionDelayGps = on) } }
                )
            }
        }

        Spacer(modifier = Modifier.height(AppConfig.uiSpacingSectionGap.dp))

        // ── Orientation aids ────────────────────────────────────────────
        SectionHeader(title = stringResource(R.string.settings_section_orientation))
        Spacer(modifier = Modifier.height(AppConfig.uiSpacingHeaderBottom.dp))
        CardArea {
            ToggleRow(
                label = stringResource(R.string.settings_heading_line_label),
                description = stringResource(R.string.settings_heading_line_desc),
                checked = settings.headingLineVisible,
                onCheckedChange = { visible -> onUpdateSettings { it.copy(headingLineVisible = visible) } }
            )
            // How the line looks, its expander directly below its own toggle — the coastline card's
            // shape, so the pair reads on/off and then how. Row order inside is thickness, transparency,
            // colour, the order the coastline card took.
            Spacer(Modifier.height(AppConfig.uiSpacingGroupedAfterExpander.dp))
            Expander(
                label = stringResource(R.string.settings_heading_line_appearance_label),
                expanded = settingsVm.isExpanded("heading_line_appearance"),
                onToggle = {
                    settingsVm.setExpanded(
                        "heading_line_appearance",
                        !settingsVm.isExpanded("heading_line_appearance")
                    )
                }
            ) {
                Spacer(Modifier.height(8.dp))
                NestedCard {
                    // Thickness (dp), 0.25 dp steps so the shipped 1 dp stays reachable.
                    var widthDrag by remember { mutableStateOf(settings.navigationLineWidthDp) }
                    SliderRow(
                        label = stringResource(R.string.settings_heading_line_width_label),
                        description = stringResource(R.string.settings_heading_line_width_desc),
                        valueLabel = stringResource(R.string.settings_value_dp_fmt, widthDrag),
                        value = widthDrag,
                        valueRange = 0.5f..4f,
                        steps = 13,
                        onValueChange = { v -> widthDrag = v },
                        onValueChangeFinished = {
                            onUpdateSettings { it.copy(navigationLineWidthDp = widthDrag) }
                        }
                    )
                    SectionDivider()
                    // Transparency: 0 = opaque, 100 = invisible — the app-wide convention, reused from
                    // the shared label, whose wording fits a single stroke even though its name is broader.
                    var transparencyDrag by remember {
                        mutableStateOf(settings.navigationLineTransparencyPct.toFloat())
                    }
                    SliderRow(
                        label = stringResource(R.string.settings_transparency_border_fill_label),
                        description = stringResource(R.string.settings_heading_line_transparency_desc),
                        valueLabel = stringResource(R.string.settings_value_percent, transparencyDrag.roundToInt()),
                        value = transparencyDrag,
                        valueRange = 0f..100f,
                        steps = 19,
                        onValueChange = { v -> transparencyDrag = v },
                        onValueChangeFinished = {
                            onUpdateSettings {
                                it.copy(navigationLineTransparencyPct = transparencyDrag.roundToInt())
                            }
                        }
                    )
                    SectionDivider()
                    ColorRow(
                        label = stringResource(R.string.settings_default_colour_label),
                        color = settings.navigationLineColor,
                        onColorSelected = { c -> onUpdateSettings { it.copy(navigationLineColor = c) } }
                    )
                }
            }
            Spacer(Modifier.height(AppConfig.uiSpacingGroupedAfterExpander.dp))
            SectionDivider()
            ToggleRow(
                label = stringResource(R.string.settings_cap_arrow_label),
                description = stringResource(R.string.settings_cap_arrow_desc),
                checked = settings.capArrowVisible,
                onCheckedChange = { visible -> onUpdateSettings { it.copy(capArrowVisible = visible) } }
            )
            // The arrow's own look, under its own toggle, before the demo-heading toggle — which owns no
            // appearance and keeps its place.
            Spacer(Modifier.height(AppConfig.uiSpacingGroupedAfterExpander.dp))
            Expander(
                label = stringResource(R.string.settings_cap_arrow_appearance_label),
                expanded = settingsVm.isExpanded("cap_arrow_appearance"),
                onToggle = {
                    settingsVm.setExpanded(
                        "cap_arrow_appearance",
                        !settingsVm.isExpanded("cap_arrow_appearance")
                    )
                }
            ) {
                Spacer(Modifier.height(8.dp))
                NestedCard {
                    // Shaft thickness (dp); the head follows it at the shipped 4 : 1 ratio.
                    var widthDrag by remember { mutableStateOf(settings.navigationArrowWidthDp) }
                    SliderRow(
                        label = stringResource(R.string.settings_cap_arrow_width_label),
                        description = stringResource(R.string.settings_cap_arrow_width_desc),
                        valueLabel = stringResource(R.string.settings_value_dp_fmt, widthDrag),
                        value = widthDrag,
                        valueRange = 1f..8f,
                        steps = 27,
                        onValueChange = { v -> widthDrag = v },
                        onValueChangeFinished = {
                            onUpdateSettings { it.copy(navigationArrowWidthDp = widthDrag) }
                        }
                    )
                    SectionDivider()
                    // Transparency is independent of the colour mode, so a speed-coloured arrow dims too.
                    var transparencyDrag by remember {
                        mutableStateOf(settings.navigationArrowTransparencyPct.toFloat())
                    }
                    SliderRow(
                        label = stringResource(R.string.settings_transparency_border_fill_label),
                        description = stringResource(R.string.settings_cap_arrow_transparency_desc),
                        valueLabel = stringResource(R.string.settings_value_percent, transparencyDrag.roundToInt()),
                        value = transparencyDrag,
                        valueRange = 0f..100f,
                        steps = 19,
                        onValueChange = { v -> transparencyDrag = v },
                        onValueChangeFinished = {
                            onUpdateSettings {
                                it.copy(navigationArrowTransparencyPct = transparencyDrag.roundToInt())
                            }
                        }
                    )
                    SectionDivider()
                    // Speed Colour: on, the arrow takes the colour of the speed it carries and no longer
                    // uses the row below. That row deliberately stays active either way — the two labels
                    // state the precedence, so no disabled-row state is introduced for it.
                    ToggleRow(
                        label = stringResource(R.string.settings_speed_colour_label),
                        checked = settings.navigationArrowFollowSpeedColour,
                        onCheckedChange = { follow ->
                            onUpdateSettings { it.copy(navigationArrowFollowSpeedColour = follow) }
                        }
                    )
                    SectionDivider()
                    ColorRow(
                        label = stringResource(R.string.settings_default_colour_label),
                        color = settings.navigationArrowColor,
                        onColorSelected = { c -> onUpdateSettings { it.copy(navigationArrowColor = c) } }
                    )
                }
            }
            Spacer(Modifier.height(AppConfig.uiSpacingGroupedAfterExpander.dp))
            SectionDivider()
            ToggleRow(
                label = stringResource(R.string.settings_demo_heading_label),
                description = stringResource(R.string.settings_demo_heading_desc),
                checked = settings.demoHeadingUp,
                onCheckedChange = { headingUp -> onUpdateSettings { it.copy(demoHeadingUp = headingUp) } }
            )
        }
        Spacer(modifier = Modifier.height(AppConfig.uiSpacingSectionGap.dp))

        // ── Re-display on approach ─────────────────────────────────────
        SectionHeader(title = stringResource(R.string.settings_section_redisplay))
        Spacer(modifier = Modifier.height(AppConfig.uiSpacingHeaderBottom.dp))

        CardArea {
            ToggleRow(
                label = stringResource(R.string.settings_redisplay_enable_gps),
                checked = settings.approachAutoShowGps,
                onCheckedChange = { on -> onUpdateSettings { it.copy(approachAutoShowGps = on) } }
            )
            Spacer(Modifier.height(AppConfig.uiSpacingGroupedRowGap.dp))
            ToggleRow(
                label = stringResource(R.string.settings_redisplay_enable_demo),
                checked = settings.approachAutoShowDemo,
                onCheckedChange = { on -> onUpdateSettings { it.copy(approachAutoShowDemo = on) } }
            )
            SectionDivider()
            ToggleRow(
                label = stringResource(R.string.settings_redisplay_zone300),
                checked = settings.zone300AutoShow,
                onCheckedChange = { on -> onUpdateSettings { it.copy(zone300AutoShow = on) } }
            )

            Spacer(Modifier.height(AppConfig.uiSpacingGroupedRowGap.dp))

            ToggleRow(
                label = stringResource(R.string.settings_redisplay_speed),
                checked = settings.speedZoneAutoShow,
                onCheckedChange = { on -> onUpdateSettings { it.copy(speedZoneAutoShow = on) } }
            )

            Spacer(Modifier.height(AppConfig.uiSpacingGroupedRowGap.dp))

            ToggleRow(
                label = stringResource(R.string.settings_redisplay_regulated),
                checked = settings.regulatedZoneAutoShow,
                onCheckedChange = { on -> onUpdateSettings { it.copy(regulatedZoneAutoShow = on) } }
            )

            Spacer(Modifier.height(AppConfig.uiSpacingGroupedRowGap.dp))
            // Deliberately NOT SectionDivider(): that would tighten the surrounding
            // uiSpacingGroupedRowGap (8dp) to uiDividerGap (6dp) and shift the rhythm.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(AppConfig.uiDividerHeight.dp)
                    .background(ComposeColor(AppConfig.uiDividerColor))
            )
            Spacer(Modifier.height(AppConfig.uiSpacingGroupedRowGap.dp))

            Expander(
                label = stringResource(R.string.settings_redisplay_when_label),
                expanded = settingsVm.isExpanded("redisplay_when"),
                onToggle = { settingsVm.setExpanded("redisplay_when", !settingsVm.isExpanded("redisplay_when")) }
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                NestedCard {
                    SliderRow(
                        label = stringResource(R.string.settings_redisplay_dist_label),
                        description = stringResource(R.string.settings_redisplay_dist_desc),
                        valueLabel = stringResource(R.string.settings_value_meters, settings.zoneAutoRevealDistanceM.roundToInt()),
                        value = settings.zoneAutoRevealDistanceM,
                        valueRange = 50f..500f,
                        steps = 17,
                        onValueChange = { v -> onUpdateSettings { it.copy(zoneAutoRevealDistanceM = (v / 25f).roundToInt() * 25f) } }
                    )
                    SectionDivider()
                    SliderRow(
                        label = stringResource(R.string.settings_redisplay_time_label),
                        description = stringResource(R.string.settings_redisplay_time_desc),
                        valueLabel = stringResource(R.string.settings_value_seconds, settings.zoneAutoRevealTimeS),
                        value = settings.zoneAutoRevealTimeS.toFloat(),
                        valueRange = 5f..120f,
                        steps = 22,
                        onValueChange = { v -> onUpdateSettings { it.copy(zoneAutoRevealTimeS = (v / 5f).roundToInt() * 5) } }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(AppConfig.uiSpacingSectionGap.dp))


    // ── Automatic map offset ──────────────────────────────────────────────
    SectionHeader(title = stringResource(R.string.settings_section_map_offset))

    CardArea {
        // GPS mode toggle
        ToggleRow(
            label = stringResource(R.string.settings_gps_mode_label),
            description = stringResource(R.string.settings_map_offset_gps_desc),
            checked = settings.mapOffsetGps,
            onCheckedChange = { on -> onUpdateSettings { it.copy(mapOffsetGps = on) } }
        )

        Spacer(Modifier.height(4.dp))

        // Demo mode toggle
        ToggleRow(
            label = stringResource(R.string.settings_map_offset_demo_label),
            description = stringResource(R.string.settings_map_offset_demo_desc),
            checked = settings.mapOffsetDemo,
            onCheckedChange = { on -> onUpdateSettings { it.copy(mapOffsetDemo = on) } }
        )

        SectionDivider()

        // Boat-from-bottom slider
        SliderRow(
            label = stringResource(R.string.settings_map_offset_boat_label),
            description = stringResource(R.string.settings_map_offset_boat_desc),
            valueLabel = "${settings.mapOffsetBoatFromBottomPct}%",
            value = settings.mapOffsetBoatFromBottomPct.toFloat(),
            valueRange = 5f..50f,
            steps = 9,
            onValueChange = { v -> onUpdateSettings { it.copy(mapOffsetBoatFromBottomPct = v.roundToInt()) } }
        )
    }

}
}

// ── Routing tab ────────────────────────────────────────────────────────────

@Composable
private fun RoutingSettings(
    settings: AppSettings,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    scrollState: ScrollState
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        // ── Tuning ────────────────────────────────────────────────────────────
        // The free-water pace: the trip figure's own setting, and the third of the three seams. The
        // bounds are read from AppConfig, where they live beside the accessor, so the slider, the
        // properties loader and the settings clamp cannot disagree about 3 and 40.
        SectionHeader(title = stringResource(R.string.settings_section_routing_tuning))

        CardArea {
            SliderRow(
                label = stringResource(R.string.settings_route_pace_label),
                description = stringResource(R.string.settings_route_pace_desc),
                valueLabel = stringResource(R.string.settings_route_pace_value_fmt, settings.routeFreeWaterPaceKn),
                value = settings.routeFreeWaterPaceKn,
                valueRange = AppConfig.ROUTE_FREE_WATER_PACE_MIN_KN..AppConfig.ROUTE_FREE_WATER_PACE_MAX_KN,
                // The setting's own grid, read from its one home: six intervals of five knots between 5 and 35.
                steps = ((AppConfig.ROUTE_FREE_WATER_PACE_MAX_KN - AppConfig.ROUTE_FREE_WATER_PACE_MIN_KN)
                    / AppConfig.ROUTE_FREE_WATER_PACE_STEP_KN).toInt() - 1,
                onValueChange = { v -> onUpdateSettings { it.copy(routeFreeWaterPaceKn = v) } }
            )

            SectionDivider()

            // The Driving preference: the one cursor over the effort ladder — three stops, through slow
            // water, balanced, and around it. The stored value snaps to the nearest rung's λ (0 / 2.5 / 5),
            // the very value the engine reads at solve time.
            SliderRow(
                label = stringResource(R.string.settings_route_preference_label),
                description = stringResource(R.string.settings_route_preference_desc),
                // The rung's word, read from the ladder's own mapping so the slider and the drawer's
                // quick access name the same rung the same way.
                valueLabel = stringResource(
                    routeRungLabelRes(routeRungIndex(settings.routeSlowWaterAversion.toDouble()))
                ),
                value = routeRungLambda(settings.routeSlowWaterAversion.toDouble()).toFloat(),
                valueRange = AppConfig.ROUTE_SLOW_WATER_AVERSION_MIN.toFloat()..
                    AppConfig.ROUTE_SLOW_WATER_AVERSION_MAX.toFloat(),
                steps = 1,
                onValueChange = { v -> onUpdateSettings { it.copy(routeSlowWaterAversion = v) } }
            )
        }
    }
}

// ── System tab ───────────────────────────────────────────────────────────

@Composable
private fun SystemSettings(
    settings: AppSettings,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    onRegenerateRasters: (List<RasterCache.Step>) -> Unit,
    onDismiss: () -> Unit,
    scrollState: ScrollState
) {
    val settingsVm = androidx.lifecycle.viewmodel.compose.viewModel<SettingsViewModel>()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        // ── Language ──────────────────────────────────────────────────
        SectionHeader(title = stringResource(R.string.settings_section_language))
        Spacer(modifier = Modifier.height(AppConfig.uiSpacingHeaderBottom.dp))

        CardArea {
            SegmentedRow(
                options = listOf(
                    // (code, label) — endonyms (English/Français) read the same in every locale.
                    "system" to stringResource(R.string.settings_language_system),
                    "en" to stringResource(R.string.settings_language_english),
                    "fr" to stringResource(R.string.settings_language_french)
                ),
                selected = settings.languageCode,
                onSelect = { code -> onUpdateSettings { it.copy(languageCode = code) } }
            )
        }

        Spacer(modifier = Modifier.height(AppConfig.uiSpacingSectionGap.dp))

        // ── Route algorithm ──────────────────────────────────────────────
        SectionHeader(title = stringResource(R.string.settings_section_route_algorithm))
        Spacer(modifier = Modifier.height(AppConfig.uiSpacingHeaderBottom.dp))
        CardArea {
            CardDescription(stringResource(R.string.settings_route_algorithm_desc))
            DropdownRow(
                label = null,
                options = RouteEngineChoice.all.map { it.id to stringResource(it.labelResId) },
                selected = RouteEngineChoice.resolve(settings.routeEngineId).id,
                onSelect = { id -> onUpdateSettings { it.copy(routeEngineId = id) } },
                accessibleName = stringResource(R.string.settings_section_route_algorithm)
            )
        }

        Spacer(modifier = Modifier.height(AppConfig.uiSpacingSectionGap.dp))

        // ── GPS tuning ──────────────────────────────────────────────────
        SectionHeader(title = stringResource(R.string.settings_section_gps_tuning))
        Spacer(modifier = Modifier.height(AppConfig.uiSpacingHeaderBottom.dp))

        CardArea {
            Expander(
                label = stringResource(R.string.settings_gps_tuning_label),
                expanded = settingsVm.isExpanded("gps_tuning"),
                onToggle = { settingsVm.setExpanded("gps_tuning", !settingsVm.isExpanded("gps_tuning")) }
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                NestedCard {
                    Text(
                        text = stringResource(R.string.settings_freq_label),
                        color = ComposeColor(AppConfig.uiTextPrimary),
                        fontSize = AppConfig.uiFontToggleSize.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(R.string.settings_freq_desc),
                        color = ComposeColor(AppConfig.uiTextMuted),
                        fontSize = AppConfig.uiFontDescSize.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SegmentedRow(
                        options = listOf(
                            (1 to 1f) to stringResource(R.string.settings_freq_high),
                            (2 to 5f) to stringResource(R.string.settings_freq_balanced),
                            (4 to 10f) to stringResource(R.string.settings_freq_eco)
                        ),
                        selected = settings.gpsActiveIntervalSec to settings.gpsActiveMinDistanceM,
                        onSelect = { (intervalSec, minDistanceM) ->
                            onUpdateSettings {
                                it.copy(
                                    gpsActiveIntervalSec = intervalSec,
                                    gpsActiveMinDistanceM = minDistanceM
                                )
                            }
                        },
                        captions = listOf(
                            stringResource(R.string.settings_freq_stop_fmt, 1, 1),
                            stringResource(R.string.settings_freq_stop_fmt, 2, 5),
                            stringResource(R.string.settings_freq_stop_fmt, 4, 10)
                        )
                    )

                    SectionDivider()

                    SliderRow(
                        label = stringResource(R.string.settings_recenter_label),
                        description = stringResource(R.string.settings_recenter_desc),
                        valueLabel = stringResource(R.string.settings_value_seconds, settings.recenterDelaySeconds),
                        value = settings.recenterDelaySeconds.toFloat(),
                        valueRange = 1f..10f,
                        steps = 8,
                        onValueChange = { v -> onUpdateSettings { it.copy(recenterDelaySeconds = v.roundToInt()) } }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(AppConfig.uiSpacingSectionGap.dp))

        // ── Screen ─────────────────────────────────────────────────────
        SectionHeader(title = stringResource(R.string.settings_section_screen))
        Spacer(modifier = Modifier.height(AppConfig.uiSpacingHeaderBottom.dp))

        CardArea {
            // Keep screen on — window flag, so this is inherently "while the app is in front".
            ToggleRow(
                label = stringResource(R.string.settings_keep_screen_on_label),
                description = stringResource(R.string.settings_keep_screen_on_desc),
                checked = settings.keepScreenOn,
                onCheckedChange = { on -> onUpdateSettings { it.copy(keepScreenOn = on) } }
            )

            // Movement gate — additive: with this off the screen is held the whole time the app is
            // in front (the previous behaviour); with it on the hold follows movement + interaction.
            Expander(
                label = stringResource(R.string.settings_screen_gate_label),
                expanded = settingsVm.isExpanded("screen_gate"),
                onToggle = {
                    settingsVm.setExpanded("screen_gate", !settingsVm.isExpanded("screen_gate"))
                }
            ) {
                NestedCard {
                    ToggleRow(
                        label = stringResource(R.string.settings_hold_screen_moving_label),
                        description = stringResource(R.string.settings_hold_screen_moving_desc),
                        checked = settings.keepScreenOnMovementGate,
                        onCheckedChange = { on ->
                            onUpdateSettings { it.copy(keepScreenOnMovementGate = on) }
                        }
                    )

                    SectionDivider()

                    SliderRow(
                        label = stringResource(R.string.settings_screen_speed_threshold_label),
                        description = stringResource(R.string.settings_screen_speed_threshold_desc),
                        valueLabel = stringResource(
                            R.string.settings_screen_speed_threshold_value,
                            settings.keepScreenOnSpeedThresholdKn
                        ),
                        value = settings.keepScreenOnSpeedThresholdKn,
                        valueRange = 0.5f..5f,
                        steps = 8,
                        onValueChange = { v ->
                            onUpdateSettings { it.copy(keepScreenOnSpeedThresholdKn = v) }
                        }
                    )

                    SectionDivider()

                    SliderRow(
                        label = stringResource(R.string.settings_screen_grace_label),
                        description = stringResource(R.string.settings_screen_grace_desc),
                        valueLabel = stringResource(
                            R.string.settings_screen_grace_value,
                            settings.keepScreenOnGraceMinutes
                        ),
                        value = settings.keepScreenOnGraceMinutes.toFloat(),
                        valueRange = AppConfig.powerScreenGraceMinMinutes.toFloat()..
                            AppConfig.powerScreenGraceMaxMinutes.toFloat(),
                        steps = AppConfig.powerScreenGraceMaxMinutes -
                            AppConfig.powerScreenGraceMinMinutes - 1,
                        onValueChange = { v ->
                            onUpdateSettings { it.copy(keepScreenOnGraceMinutes = v.roundToInt()) }
                        }
                    )
                }
            }

            SectionDivider()

            // Debug rays
            ToggleRow(
                label = stringResource(R.string.settings_debug_rays_label),
                description = stringResource(R.string.settings_debug_rays_desc),
                checked = settings.markerDebugRays,
                onCheckedChange = { on ->
                    onUpdateSettings { it.copy(markerDebugRays = on) }
                }
            )

            SectionDivider()

            // FPS
            SliderRow(
                label = stringResource(R.string.settings_fps_label),
                description = stringResource(R.string.settings_fps_desc),
                valueLabel = stringResource(R.string.settings_value_fps, settings.mapRefreshFps),
                value = settings.mapRefreshFps.toFloat(),
                valueRange = 5f..50f,
                steps = 8,
                onValueChange = { v -> onUpdateSettings { it.copy(mapRefreshFps = (v / 5f).roundToInt() * 5) } }
            )
        }

        Spacer(modifier = Modifier.height(AppConfig.uiSpacingSectionGap.dp))

        // ── Regenerate Layers ─────────────────────────────────────────
        SectionHeader(title = stringResource(R.string.settings_section_regenerate_layers))
        Spacer(modifier = Modifier.height(AppConfig.uiSpacingHeaderBottom.dp))
        CardArea {
            ToggleRow(
                label = stringResource(R.string.settings_regen_depth_grid_label),
                description = stringResource(R.string.settings_regen_depth_grid_desc),
                checked = settings.regenGrid,
                onCheckedChange = { v -> onUpdateSettings { it.copy(regenGrid = v) } }
            )
            SectionDivider()
            ToggleRow(
                label = stringResource(R.string.settings_regen_isobaths_label),
                description = stringResource(R.string.settings_regen_isobaths_desc),
                checked = settings.regenIsobaths,
                onCheckedChange = { v -> onUpdateSettings { it.copy(regenIsobaths = v) } }
            )
            SectionDivider()
            ToggleRow(
                label = stringResource(R.string.settings_regen_colour_label),
                description = stringResource(R.string.settings_regen_colour_desc),
                checked = settings.regenColour,
                onCheckedChange = { v -> onUpdateSettings { it.copy(regenColour = v) } }
            )
            SectionDivider()
            ToggleRow(
                label = stringResource(R.string.settings_regen_warning_label),
                description = stringResource(R.string.settings_regen_warning_desc),
                checked = settings.regenWarning,
                onCheckedChange = { v -> onUpdateSettings { it.copy(regenWarning = v) } }
            )
        }
        Spacer(modifier = Modifier.height(AppConfig.uiSpacingHeaderBottom.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Button(
                onClick = {
                    val selected = buildList {
                        if (settings.regenGrid) add(RasterCache.Step.GRID)
                        if (settings.regenIsobaths) add(RasterCache.Step.ISOBATH)
                        if (settings.regenColour) add(RasterCache.Step.DEPTH_COLOUR)
                        if (settings.regenWarning) add(RasterCache.Step.LOW_DEPTH_WARNING)
                    }
                    onRegenerateRasters(selected)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = ComposeColor(AppConfig.uiAccent)),
                shape = RoundedCornerShape(AppConfig.uiRadiusCard.dp)
            ) {
                Text(stringResource(R.string.action_regenerate), color = ComposeColor(AppConfig.uiTextPrimary))
            }
        }

        Spacer(modifier = Modifier.height(AppConfig.uiSpacingSectionGap.dp))
    }
}

// ── Settings sub-components ─────────────────────────────────────────────────


/**
 * Card lead-in description: one muted 13sp sentence under a [SectionHeader], placed before the
 * card's first control. For a *titled* sub-section inside a card, use a sub-section header with a
 * description instead.
 */
@Composable
private fun CardDescription(text: String) {
    Text(
        text = text,
        color = ComposeColor(AppConfig.uiTextMuted),
        fontSize = AppConfig.uiFontDescSize.sp
    )
    Spacer(Modifier.height(AppConfig.uiSpacingGroupedAfterExpander.dp))
}


/**
 * Label (+ optional description) + right-aligned value + two-thumb slider, WITHOUT its own box —
 * placed directly on a [CardArea] or inside a [NestedCard] ([`ui-component-guidelines` §2.8](../../../../../../docs/ui-component-guidelines.md)).
 * Pass `label = null` where a [SubSectionHeader] already supplies the heading.
 */
@Composable
private fun RangeSliderRow(
    valueLabel: String,
    value: ClosedFloatingPointRange<Float>,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (ClosedFloatingPointRange<Float>) -> Unit,
    onValueChangeFinished: () -> Unit = {},
    label: String? = null,
    description: String? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (label != null) {
            Text(
                text = label,
                color = ComposeColor(AppConfig.uiTextPrimary),
                fontSize = AppConfig.uiFontToggleSize.sp,
                fontWeight = FontWeight.Medium
            )
        }
        if (description != null) {
            Text(
                text = description,
                color = ComposeColor(AppConfig.uiTextMuted),
                fontSize = AppConfig.uiFontDescSize.sp
            )
        }
        Text(
            text = valueLabel,
            color = ComposeColor(AppConfig.uiValueText),
            fontSize = AppConfig.uiFontRangeSize.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth()
        )
        RangeSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            onValueChangeFinished = onValueChangeFinished,
            colors = SliderDefaults.colors(
                thumbColor = ComposeColor(AppConfig.uiAccent),
                activeTrackColor = ComposeColor(AppConfig.uiAccent),
                inactiveTrackColor = ComposeColor(AppConfig.uiSwitchTrackInactive)
            )
        )
    }
}

/**
 * A sub-section that groups a SINGLE colour control: the title (SubSectionHeader typography) sits on its
 * own line and the description line carries the 24dp tappable colour swatch on its trailing edge. When no
 * description is given the swatch falls back to the trailing edge of the title line — never a standalone
 * colour row. Used when a NestedCard group holds exactly one colour (e.g. 300 m band "Zone color").
 * Multi-colour groups keep [SubSectionHeader] + labeled [ColorRow] rows instead.
 */
@Composable
private fun SingleColorSubSection(
    title: String,
    description: String? = null,
    color: Int,
    onColorSelected: (Int) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }
    val openPicker = { showPicker = true }
    Column(modifier = Modifier.fillMaxWidth()) {
        if (description == null) {
            // No description → the swatch shares the title line (still no standalone row).
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = ComposeColor(AppConfig.uiTextPrimary),
                    fontSize = AppConfig.uiFontSubsectionSize.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                ColorSwatchButton(color = color, onClick = openPicker)
            }
        } else {
            Text(
                text = title,
                color = ComposeColor(AppConfig.uiTextPrimary),
                fontSize = AppConfig.uiFontSubsectionSize.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            // Description line carries the colour swatch on its trailing edge.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = description,
                    color = ComposeColor(AppConfig.uiTextSecondary),
                    fontSize = AppConfig.uiFontDescSize.sp,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(12.dp))
                ColorSwatchButton(color = color, onClick = openPicker)
            }
        }
    }
    if (showPicker) {
        ColorPickerDialog(
            currentColor = color,
            onColorSelected = { c -> onColorSelected(c); showPicker = false },
            onDismiss = { showPicker = false }
        )
    }
}

/** 24dp rounded colour swatch button that opens the colour picker via [onClick]. */
@Composable
private fun ColorSwatchButton(color: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(ComposeColor(color))
            .clickable(onClick = onClick)
            .border(1.dp, ComposeColor(AppConfig.uiDividerColor), RoundedCornerShape(6.dp))
    )
}



/**
 * A row showing a labeled color swatch.
 * Tapping the swatch opens a simple color dialog with preset palette.
 * TODO: Replace with Canvas-based HSV color picker for richer selection.
 */
@Composable
private fun ColorRow(
    label: String,
    color: Int,
    onColorSelected: (Int) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = ComposeColor(AppConfig.uiTextPrimary),
            fontSize = 14.sp
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Color swatch
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(ComposeColor(color))
                    .clickable { showPicker = true }
                    .semantics { this.contentDescription = label }
                    .border(1.dp, ComposeColor(AppConfig.uiDividerColor), RoundedCornerShape(6.dp))
            )
        }
    }

    if (showPicker) {
        ColorPickerDialog(
            currentColor = color,
            onColorSelected = { c -> onColorSelected(c); showPicker = false },
            onDismiss = { showPicker = false }
        )
    }
}

/**
 * A reusable color picker dialog showing a grid of preset colors.
 */
@Composable
private fun ColorPickerDialog(
    currentColor: Int,
    onColorSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.color_picker_title)) },
        text = {
            Column {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.height(216.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(MarkerColors.all) { presetColor ->
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(ComposeColor(presetColor))
                                .border(
                                    width = if (presetColor == currentColor) 3.dp else 1.dp,
                                    color = if (presetColor == currentColor) ComposeColor(AppConfig.uiAccent)
                                        else ComposeColor(AppConfig.uiDividerColor),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    onColorSelected(presetColor)
                                }
                        )
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

/**
 * A single-row color selector showing a label and two clickable color swatches
 * (from → to) with a color picker dialog for each.
 */
@Composable
private fun ColorPairRow(
    label: String,
    fromColor: Int,
    toColor: Int,
    onFromColorSelected: (Int) -> Unit,
    onToColorSelected: (Int) -> Unit
) {
    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = ComposeColor(AppConfig.uiTextPrimary),
            fontSize = 14.sp
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            // From swatch
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(ComposeColor(fromColor))
                    .clickable { showFromPicker = true }
                    .border(1.dp, ComposeColor(AppConfig.uiDividerColor), RoundedCornerShape(4.dp))
            )
            Spacer(Modifier.width(6.dp))
            Text("→", color = ComposeColor(AppConfig.uiTextMuted), fontSize = 14.sp)
            Spacer(Modifier.width(6.dp))
            // To swatch
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(ComposeColor(toColor))
                    .clickable { showToPicker = true }
                    .border(1.dp, ComposeColor(AppConfig.uiDividerColor), RoundedCornerShape(4.dp))
            )
        }
    }

    if (showFromPicker) {
        ColorPickerDialog(
            currentColor = fromColor,
            onColorSelected = { c -> onFromColorSelected(c); showFromPicker = false },
            onDismiss = { showFromPicker = false }
        )
    }
    if (showToPicker) {
        ColorPickerDialog(
            currentColor = toColor,
            onColorSelected = { c -> onToColorSelected(c); showToPicker = false },
            onDismiss = { showToPicker = false }
        )
    }
}

