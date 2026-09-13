
package ykws.android.maro.ui.map
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.depth.RasterCache
import android.provider.Settings
import android.graphics.Color
import ykws.android.maro.R
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
import ykws.android.maro.ui.components.ToggleRow
import ykws.android.maro.spatial.MarkerMatcher
import ykws.android.maro.spatial.NoOpWhereAmIDebugger
import ykws.android.maro.spatial.VisualWhereAmIDebugger

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsOverlay(
    settings: AppSettings,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    onGpsModeChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    selectedTab: Int,
    onTabChange: (Int) -> Unit,
    onRegenerateRasters: (List<RasterCache.Step>) -> Unit = {},
    displayScrollState: ScrollState,
    navigationScrollState: ScrollState,
    positionScrollState: ScrollState,
    systemScrollState: ScrollState,
) {
    val pagerState = rememberPagerState(pageCount = { 4 })

    // Sync tab selection <-> pager position (bidirectional).
    // The pagerSyncSettled flag prevents the initial pager→tab sync from
    // overwriting selectedTab before animateScrollToPage has a chance to
    // restore the persisted tab selection.
    val pagerSyncSettled = remember { mutableStateOf(false) }
    LaunchedEffect(selectedTab) {
        pagerState.animateScrollToPage(selectedTab)
        pagerSyncSettled.value = true
    }
    LaunchedEffect(pagerState.currentPage) {
        if (pagerSyncSettled.value) {
            onTabChange(pagerState.currentPage)
        }
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
                selectedTabIndex = selectedTab,
                containerColor = ComposeColor(AppConfig.uiBackground),
                edgePadding = 24.dp,
                divider = {},
            ) {
                settingsTabLabels.forEachIndexed { index, labelRes ->
                    val isSelected = selectedTab == index
                    Box(
                        modifier = Modifier
                            .selectable(
                                selected = isSelected,
                                role = Role.Tab,
                                onClick = { onTabChange(index) }
                            )
                            .padding(horizontal = 8.dp, vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(labelRes),
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
                    when (page) {
                        0 -> LayersSettings(settings, onUpdateSettings, displayScrollState)
                        1 -> NavigationSettings(settings, onUpdateSettings, navigationScrollState)
                        2 -> PositionSettings(settings, onUpdateSettings, onGpsModeChange, onDismiss, positionScrollState)
                        3 -> SystemSettings(settings, onUpdateSettings, onRegenerateRasters, onDismiss, systemScrollState)
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

// ── Layers tab ────────────────────────────────────────────────────────────

@Composable
private fun LayersSettings(
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
        // ── Tracks ──────────────────────────────────────────────────────
        SectionHeader(title = stringResource(R.string.settings_section_tracks))
        Spacer(modifier = Modifier.height(AppConfig.uiSpacingHeaderBottom.dp))
        CardArea {
            CardDescription(stringResource(R.string.settings_tracks_desc))

            Expander(
                label = stringResource(R.string.settings_track_settings_label),
                expanded = settingsVm.isExpanded("track_rendering"),
                onToggle = { settingsVm.setExpanded("track_rendering", !settingsVm.isExpanded("track_rendering")) }
            ) {
                Spacer(Modifier.height(AppConfig.uiSpacingExpanderToContent.dp))
                NestedCard {
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
                        label = "Active track",
                        color = settings.trackingColorActive,
                        onColorSelected = { c -> onUpdateSettings { it.copy(trackingColorActive = c) } },
                        showPickLabel = false
                    )
                    ColorPairRow(
                        label = "Past tracks",
                        fromColor = settings.trackingColorPastFrom,
                        toColor = settings.trackingColorPastTo,
                        onFromColorSelected = { c -> onUpdateSettings { it.copy(trackingColorPastFrom = c) } },
                        onToColorSelected = { c -> onUpdateSettings { it.copy(trackingColorPastTo = c) } }
                    )
                    ColorPairRow(
                        label = "Pinned tracks",
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

            Spacer(Modifier.height(4.dp))
        }

        Spacer(modifier = Modifier.height(AppConfig.uiSpacingSectionGap.dp))

        // ── Markers ─────────────────────────────────────────────────────
        SectionHeader(title = stringResource(R.string.settings_section_markers))
        Spacer(modifier = Modifier.height(AppConfig.uiSpacingHeaderBottom.dp))
        CardArea {
            CardDescription(stringResource(R.string.settings_markers_desc))
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
                    SubSectionHeader(title = stringResource(R.string.settings_marker_halo_transparency_label))
                    Spacer(modifier = Modifier.height(AppConfig.uiSpacingGroupedRowGap.dp))

                    // Transparency: 0 = opaque, 100 = invisible. The strong border has low
                    // transparency (left thumb); the faint fill has high transparency (right thumb).
                    RangeSliderRow(
                        label = stringResource(R.string.settings_marker_halo_pinned_label),
                        valueLabel = stringResource(R.string.settings_marker_halo_value_fmt,
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
                        valueLabel = stringResource(R.string.settings_marker_halo_value_fmt,
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
                        onColorSelected = { c -> onUpdateSettings { it.copy(markerHaloPinnedColor = c) } },
                        showPickLabel = false
                    )
                    ColorRow(
                        label = stringResource(R.string.settings_marker_halo_unpinned_color_label),
                        color = settings.markerHaloUnpinnedColor,
                        onColorSelected = { c -> onUpdateSettings { it.copy(markerHaloUnpinnedColor = c) } },
                        showPickLabel = false
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
        SectionHeader(title = stringResource(R.string.settings_regulated_zones_label))
        Spacer(modifier = Modifier.height(AppConfig.uiSpacingHeaderBottom.dp))
        CardArea {
            CardDescription(stringResource(R.string.settings_regulated_zones_desc))
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
        SectionHeader(title = stringResource(R.string.settings_zone300_label))
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
                        title = stringResource(R.string.settings_zone300_opacity_label),
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
                            R.string.settings_zone300_opacity_value_fmt,
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
        SectionHeader(title = stringResource(R.string.settings_coastline_label))
        Spacer(modifier = Modifier.height(AppConfig.uiSpacingHeaderBottom.dp))
        CardArea {
            ToggleRow(
                label = stringResource(R.string.settings_coastline_label),
                description = stringResource(R.string.settings_coastline_desc),
                checked = settings.coastlineVisible,
                onCheckedChange = { visible -> onUpdateSettings { it.copy(coastlineVisible = visible) } }
            )
        }

        Spacer(modifier = Modifier.height(AppConfig.uiSpacingSectionGap.dp))

        // ── Danger Zones (was: low-depth warning) ──────────────────────
        SectionHeader(title = stringResource(R.string.settings_danger_zones_label))
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
        SectionHeader(title = stringResource(R.string.settings_depth_label))
        Spacer(modifier = Modifier.height(AppConfig.uiSpacingHeaderBottom.dp))
        CardArea {
            CardDescription(stringResource(R.string.settings_depth_desc))
            Expander(label = stringResource(R.string.settings_emodnet_section_label), expanded = settingsVm.isExpanded("depth_cutoff"),
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
            SectionDivider()
            ToggleRow(
                label = stringResource(R.string.settings_cap_arrow_label),
                description = stringResource(R.string.settings_cap_arrow_desc),
                checked = settings.capArrowVisible,
                onCheckedChange = { visible -> onUpdateSettings { it.copy(capArrowVisible = visible) } }
            )
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
        SectionHeader(title = stringResource(R.string.settings_redisplay_label))
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
    SectionHeader(title = stringResource(R.string.settings_map_offset_label))

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

// ── Position tab ───────────────────────────────────────────────────────────

@Composable
private fun PositionSettings(
    settings: AppSettings,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    onGpsModeChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    scrollState: ScrollState
) {
    val settingsVm = androidx.lifecycle.viewmodel.compose.viewModel<SettingsViewModel>()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        // ── Position source (Demo <-> GPS) ────────────────────────────
        SectionHeader(title = stringResource(R.string.settings_section_position))
        Spacer(modifier = Modifier.height(AppConfig.uiSpacingHeaderBottom.dp))

        CardArea {
            ToggleRow(
                label = stringResource(R.string.settings_gps_mode_label),
                description = stringResource(R.string.settings_gps_mode_desc),
                checked = settings.gpsMode,
                onCheckedChange = { checked ->
                    onGpsModeChange(checked)
                    onDismiss()
                }
            )

            Spacer(Modifier.height(AppConfig.uiSpacingGroupedRowGap.dp))
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

        // ── Stop detection ──────────────────────────────────────────────
        SectionHeader(title = stringResource(R.string.settings_idle_section_label))
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
                    "fr" to "Français"
                ),
                selected = settings.languageCode,
                onSelect = { code -> onUpdateSettings { it.copy(languageCode = code) } }
            )
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
                    AppConfig.markerDebugRaysEnabled = on
                    MarkerMatcher.debugger = if (on) VisualWhereAmIDebugger() else NoOpWhereAmIDebugger
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
        SectionHeader(title = stringResource(R.string.settings_regenerate_layers))
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
 * Single-choice segmented control — Material 3 shape: one **connected** control, outer ends rounded,
 * a single hairline outline, accent fill on the selected segment only. **Surface-free**, so the
 * enclosing [CardArea]/[NestedCard] owns the surface. [captions] renders one line under each segment.
 *
 * Accessibility: `selectableGroup()` plus a [Role.RadioButton] per segment, so it is announced as
 * "n of m, selected" instead of as unrelated buttons.
 */
@Composable
private fun <T> SegmentedRow(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    captions: List<String>? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(AppConfig.uiRadiusCard.dp))
                .border(
                    width = 1.dp,
                    color = ComposeColor(AppConfig.uiDividerColor),
                    shape = RoundedCornerShape(AppConfig.uiRadiusCard.dp)
                )
                .selectableGroup()
        ) {
            options.forEachIndexed { index, (value, label) ->
                val isSelected = value == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(if (isSelected) ComposeColor(AppConfig.uiAccent) else ComposeColor.Transparent)
                        .selectable(
                            selected = isSelected,
                            role = Role.RadioButton,
                            onClick = { onSelect(value) }
                        )
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) ComposeColor(AppConfig.uiTextPrimary) else ComposeColor(AppConfig.uiTextMuted),
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
        if (captions != null) {
            Row(modifier = Modifier.fillMaxWidth()) {
                options.forEachIndexed { index, _ ->
                    Text(
                        text = captions.getOrElse(index) { "" },
                        color = ComposeColor(AppConfig.uiTextMuted),
                        fontSize = AppConfig.uiFontCommentSize.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

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

/** A label + value + slider WITHOUT its own box — placed directly on a [CardArea] or inside a [NestedCard]. */
@Composable
private fun SliderRow(
    label: String,
    description: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = ComposeColor(AppConfig.uiTextPrimary),
                fontSize = AppConfig.uiFontToggleSize.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                color = ComposeColor(AppConfig.uiTextMuted),
                fontSize = AppConfig.uiFontDescSize.sp
            )
        }
        Spacer(modifier = Modifier.width(AppConfig.uiSpacingLabelControl.dp))
        Text(
            text = valueLabel,
            color = ComposeColor(AppConfig.uiValueText),
            fontSize = AppConfig.uiFontValueSize.sp,
            fontWeight = FontWeight.Bold
        )
    }
    Slider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = valueRange,
        steps = steps,
        colors = SliderDefaults.colors(
            thumbColor = ComposeColor(AppConfig.uiAccent),
            activeTrackColor = ComposeColor(AppConfig.uiAccent),
            inactiveTrackColor = ComposeColor(AppConfig.uiSwitchTrackInactive)
        )
    )
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

/** Sub-heading (16sp SemiBold `ui.text.primary`) with an optional 13sp `ui.text.secondary` one-line description, for grouping settings in a section. */
@Composable
private fun SubSectionHeader(title: String, description: String? = null) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            color = ComposeColor(AppConfig.uiTextPrimary),
            fontSize = AppConfig.uiFontSubsectionSize.sp,
            fontWeight = FontWeight.SemiBold
        )
        if (description != null) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                color = ComposeColor(AppConfig.uiTextSecondary),
                fontSize = AppConfig.uiFontDescSize.sp
            )
        }
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

/** Nested 5% white container (`0x0DFFFFFF` + `0x40FFFFFF` border) shown when an [Expander] is open. Holds any controls, optionally split into sections by [SectionDivider]. */
@Composable
private fun NestedCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppConfig.uiRadiusCard.dp))
            .background(ComposeColor(AppConfig.uiNestedCardBg))
            .border(1.dp, ComposeColor(AppConfig.uiNestedCardBorder), RoundedCornerShape(AppConfig.uiRadiusCard.dp))
            .padding(horizontal = AppConfig.uiPaddingCardHorizontal.dp, vertical = AppConfig.uiPaddingContentComfortable.dp)
    ) {
        content()
    }
}

/**
 * Tappable "Avancé" disclosure row that reveals [content] with a chevron + slide animation.
 * Progressive disclosure — keeps advanced/fiddly controls out of the way until asked for.
 */
@Composable
private fun Expander(
    label: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "expanderChevron"
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(AppConfig.uiRadiusExpander.dp))
                .clickable { onToggle() }
                .padding(vertical = AppConfig.uiPaddingExpanderVertical.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = ComposeColor(AppConfig.uiTextPrimary),
                fontSize = AppConfig.uiFontToggleSize.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) stringResource(R.string.collapse) else stringResource(R.string.expand),
                tint = ComposeColor(AppConfig.uiDashboardTextMuted),
                modifier = Modifier.rotate(rotation)
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}

/**
 * A row showing a color swatch with a label and a "pick" button.
 * Tapping the swatch opens a simple color dialog with preset palette.
 * TODO: Replace with Canvas-based HSV color picker for richer selection.
 */
@Composable
private fun ColorRow(
    label: String,
    color: Int,
    onColorSelected: (Int) -> Unit,
    showPickLabel: Boolean = true
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
                    .border(1.dp, ComposeColor(AppConfig.uiDividerColor), RoundedCornerShape(6.dp))
            )
            if (showPickLabel) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.color_picker_pick),
                    color = ComposeColor(AppConfig.uiAccent),
                    fontSize = 13.sp,
                    modifier = Modifier.clickable { showPicker = true }
                )
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

/**
 * A reusable color picker dialog showing a grid of preset colors.
 */
@Composable
private fun ColorPickerDialog(
    currentColor: Int,
    onColorSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val presets = remember {
        listOf(
            0xFF1565C0.toInt(), // Blue
            0xFFD32F2F.toInt(), // Red
            0xFF388E3C.toInt(), // Green
            0xFFF57C00.toInt(), // Orange
            0xFF7B1FA2.toInt(), // Purple
            0xFF00796B.toInt(), // Teal
            0xFF5D4037.toInt(), // Brown
            0xFF000000.toInt(), // Black
            0xFFFFFFFF.toInt(), // White
            0xFFBDBDBD.toInt(), // Grey
            0xFFFFF176.toInt(), // Yellow
            0xFF4FC3F7.toInt(), // Light Blue
            0xFF00BCD4.toInt(), // Cyan
            0xFFCDDC39.toInt(), // Lime
            0xFFE91E63.toInt(), // Pink
            0xFF3F51B5.toInt()  // Indigo
        )
    }
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
                    items(presets) { presetColor ->
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
                Text("Cancel")
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

