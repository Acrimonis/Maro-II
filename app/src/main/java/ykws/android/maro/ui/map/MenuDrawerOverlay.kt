package ykws.android.maro.ui.map

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.IconButton
import ykws.android.maro.ui.components.FilterControl
import ykws.android.maro.ui.icons.FilterAlt
import ykws.android.maro.ui.icons.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ykws.android.maro.R
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.track.TrackRecorderState
import ykws.android.maro.data.track.TrackRecorderUiState

/**
 * Menu slide panel — pure content composable.
 *
 * Animation and shadow are provided by [OverlayLayer].
 * Styled to match the Settings overlay (same design tokens).
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
    tracksDirectionVisible: Boolean = true,
    onToggleTracksDirection: () -> Unit = {},
    onImportTracks: () -> Unit = {},
    onExportAllTracks: () -> Unit = {},
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
    // ── Filter state ──────────────────────────────────────────────────
    trackFilterState: ykws.android.maro.data.model.ListFilter = ykws.android.maro.data.model.ListFilter(),
    onTrackFilterChange: (ykws.android.maro.data.model.ListFilter) -> Unit = {},
    onTrackReset: () -> Unit = {},
    trackFilterAxes: List<ykws.android.maro.data.model.FilterAxisSpec> = emptyList(),
    markerFilterState: ykws.android.maro.data.model.ListFilter = ykws.android.maro.data.model.ListFilter(),
    onMarkerFilterChange: (ykws.android.maro.data.model.ListFilter) -> Unit = {},
    onMarkerReset: () -> Unit = {},
    markerFilterAxes: List<ykws.android.maro.data.model.FilterAxisSpec> = emptyList()
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
                    .background(Color(AppConfig.uiSettingsSwitchTrackInactive))
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(R.string.cd_settings),
                    tint = Color(AppConfig.uiSettingsTextPrimary),
                    modifier = Modifier.size(ButtonColors.iconSizeDp.dp)
                )
            }
        }
    ) {
        Spacer(Modifier.height(20.dp))

        // ── POSITION SOURCE section ──────────────────────
        Text(
            text = stringResource(R.string.menu_section_position),
            color = Color(AppConfig.uiSettingsAccent),
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )

        Spacer(Modifier.height(2.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(AppConfig.uiCardBackground))
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.settings_gps_mode_label),
                    color = Color(AppConfig.uiSettingsTextPrimary),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Switch(
                    checked = gpsMode,
                    onCheckedChange = onGpsModeChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = gpsToggleColor,
                        checkedTrackColor = gpsToggleColor.copy(alpha = 0.4f),
                        uncheckedThumbColor = Color(AppConfig.uiSettingsTextMuted),
                        uncheckedTrackColor = Color(AppConfig.uiSettingsSwitchTrackInactive)
                    )
                )
            }

            if (autoShowMasterVisible) {
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color(AppConfig.uiSettingsDivider))
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.settings_autoshow_master_label),
                        color = Color(AppConfig.uiSettingsTextPrimary),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Switch(
                        checked = autoShowMasterOverride,
                        onCheckedChange = onAutoShowMasterChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(AppConfig.uiSettingsAccent),
                            checkedTrackColor = Color(AppConfig.uiSettingsAccent).copy(alpha = 0.4f),
                            uncheckedThumbColor = Color(AppConfig.uiSettingsTextMuted),
                            uncheckedTrackColor = Color(AppConfig.uiSettingsSwitchTrackInactive)
                        )
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Section header with filter icons ──────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.menu_section_tracks),
                color = Color(AppConfig.uiSettingsAccent),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.weight(1f))
            if (trackFilterAxes.isNotEmpty()) {
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

        Spacer(Modifier.height(2.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(AppConfig.uiCardBackground))
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            // ── Track List row ─────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clickable(onClick = onViewTrackList),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.menu_manage_tracks),
                    color = Color(AppConfig.uiSettingsTextPrimary),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                IconButton(
                    onClick = { onOpenFirstTrack?.invoke() },
                    enabled = onOpenFirstTrack != null,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.cd_open_first_track),
                        tint = Color(AppConfig.uiSettingsTextMuted).copy(alpha = if (onOpenFirstTrack != null) 1f else 0.35f),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // ── Track direction toggle ─────────────────────
            Spacer(Modifier.height(2.dp))
            HorizontalDivider(thickness = 0.5.dp, color = Color(AppConfig.uiSettingsDivider))
            Spacer(Modifier.height(2.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.menu_show_tracks_direction),
                    color = Color(AppConfig.uiSettingsTextPrimary),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Switch(
                    checked = tracksDirectionVisible,
                    onCheckedChange = { onToggleTracksDirection() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(AppConfig.uiSettingsAccent),
                        checkedTrackColor = Color(AppConfig.uiSettingsAccent).copy(alpha = 0.4f),
                        uncheckedThumbColor = Color(AppConfig.uiSettingsTextMuted),
                        uncheckedTrackColor = Color(AppConfig.uiSettingsSwitchTrackInactive)
                    )
                )
            }

            // ── Import / Export row ────────────────────────
            Spacer(Modifier.height(2.dp))
            HorizontalDivider(thickness = 0.5.dp, color = Color(AppConfig.uiSettingsDivider))
            Spacer(Modifier.height(2.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .heightIn(min = 48.dp)
                        .clickable(onClick = onExportAllTracks)
                        .padding(horizontal = 12.dp)
                        .semantics(mergeDescendants = true) {},
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.action_export),
                        color = Color(AppConfig.uiSettingsTextPrimary),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Icon(
                        imageVector = Icons.Filled.Upload,
                        contentDescription = null,
                        tint = ButtonColors.icon,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .heightIn(min = 48.dp)
                        .clickable(onClick = onImportTracks)
                        .padding(horizontal = 12.dp)
                        .semantics(mergeDescendants = true) {},
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.action_import),
                        color = Color(AppConfig.uiSettingsTextPrimary),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = null,
                        tint = ButtonColors.icon,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // ── Live stats (only when recording) ──────────
            if (recorderState.state == TrackRecorderState.ON) {
                Spacer(Modifier.height(2.dp))
                HorizontalDivider(thickness = 0.5.dp, color = Color(AppConfig.uiSettingsDivider))
                Spacer(Modifier.height(2.dp))
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
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── MARKERS section with filter icons ────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.menu_section_markers),
                color = Color(AppConfig.uiSettingsAccent),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.weight(1f))
            if (markerFilterAxes.isNotEmpty()) {
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

        Spacer(Modifier.height(8.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(AppConfig.uiCardBackground))
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clickable(onClick = onManageMarkers),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.menu_manage_markers),
                    color = Color(AppConfig.uiSettingsTextPrimary),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                IconButton(
                    onClick = { onOpenFirstMarker?.invoke() },
                    enabled = onOpenFirstMarker != null,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.cd_open_first_marker),
                        tint = Color(AppConfig.uiSettingsTextMuted).copy(alpha = if (onOpenFirstMarker != null) 1f else 0.35f),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(Modifier.height(2.dp))
            HorizontalDivider(thickness = 0.5.dp, color = Color(AppConfig.uiSettingsDivider))
            Spacer(Modifier.height(2.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.menu_show_zones),
                    color = Color(AppConfig.uiSettingsTextPrimary),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Switch(
                    checked = markerZonesVisible,
                    onCheckedChange = { onToggleMarkerZones() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(AppConfig.uiSettingsAccent),
                        checkedTrackColor = Color(AppConfig.uiSettingsAccent).copy(alpha = 0.4f),
                        uncheckedThumbColor = Color(AppConfig.uiSettingsTextMuted),
                        uncheckedTrackColor = Color(AppConfig.uiSettingsSwitchTrackInactive)
                    )
                )
            }
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
            color = Color(AppConfig.uiSettingsTextMuted),
            fontSize = 13.sp
        )
        Text(
            text = value,
            color = Color(AppConfig.uiSettingsTextPrimary),
            fontSize = 14.sp,
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
