package ykws.android.maro.ui.map

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ykws.android.maro.R
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.track.TrackRecorderState
import ykws.android.maro.data.track.TrackRecorderUiState

/**
 * Track Drawer overlay — animated panel that slides in from the right.
 *
 * Full-screen overlay with a scrim on the left 25% that closes on tap.
 * Styled to match the Settings overlay (same design tokens).
 *
 * @param isOpen           Whether the drawer is visible.
 * @param recorderState    Current recorder state from [TrackViewModel].
 * @param onStartRecording Triggered when user taps Start.
 * @param onStopRecording  Triggered when user taps Stop.
 * @param onViewTrackList  Triggered when user taps "Track List".
 * @param onDismiss        Triggered to close the drawer.
 */
@Composable
fun TrackDrawerOverlay(
    isOpen: Boolean,
    gpsMode: Boolean,
    onGpsModeChange: (Boolean) -> Unit,
    gpsToggleColor: Color,
    recorderState: TrackRecorderUiState,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onViewTrackList: () -> Unit,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isOpen,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(modifier = modifier.fillMaxSize()) {
            BackHandler { onDismiss() }

            // ── Scrim: full screen, clickable to dismiss ─────────────────
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(onClick = onDismiss)
            )

            // ── Drawer panel: 75% right, slides in from right ────────────
            AnimatedVisibility(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .fillMaxWidth(0.75f)
                    .fillMaxHeight(),
                visible = isOpen,
                enter = slideInHorizontally { it } + fadeIn(),
                exit = slideOutHorizontally { it } + fadeOut()
            ) {
                ModalDrawerSheet(
                    modifier = Modifier.fillMaxSize(),
                    drawerContainerColor = Color(AppConfig.uiSettingsBackground)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, top = 0.dp, end = 8.dp, bottom = 8.dp)
                    ) {
                        // ── Header: back button + "Maro II" title + Settings button ───
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color(AppConfig.uiSettingsSwitchTrackInactive))
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Close",
                                    tint = Color(AppConfig.uiSettingsTextPrimary)
                                )
                            }
                            Spacer(Modifier.width(16.dp))
                            Text(
                                text = "Maro II",
                                color = Color(AppConfig.uiSettingsTextPrimary),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.weight(1f))
                            IconButton(
                                onClick = onOpenSettings,
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(Color(AppConfig.uiSettingsSwitchTrackInactive))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Settings",
                                    tint = Color(AppConfig.uiSettingsTextPrimary)
                                )
                            }
                        }

                        Spacer(Modifier.height(20.dp))

                        // ── POSITION SOURCE section ──────────────────────
                        Text(
                            text = "POSITION SOURCE",
                            color = Color(AppConfig.uiSettingsAccent),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )

                        Spacer(Modifier.height(2.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
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

                        Spacer(Modifier.height(16.dp))

                        // ── Section header ──────────────────────────────
                        Text(
                            text = "TRACK RECORDING",
                            color = Color(AppConfig.uiSettingsAccent),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )

                        Spacer(Modifier.height(2.dp))

                        // ── Track List row with ON/OFF toggle ──────────
                        val isActive = recorderState.state == TrackRecorderState.ON

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onViewTrackList)
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Track List",
                                color = Color(AppConfig.uiSettingsTextPrimary),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )

                            Switch(
                                checked = isActive,
                                onCheckedChange = { checked ->
                                    if (checked) onStartRecording() else onStopRecording()
                                    onDismiss()
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = if (recorderState.isMoving)
                                        Color(AppConfig.statusTrackingHealthy)
                                    else
                                        Color(AppConfig.statusTrackingIdle),
                                    checkedTrackColor = (if (recorderState.isMoving)
                                        Color(AppConfig.statusTrackingHealthy)
                                    else
                                        Color(AppConfig.statusTrackingIdle)).copy(alpha = 0.4f),
                                    uncheckedThumbColor = Color(AppConfig.uiSettingsTextMuted),
                                    uncheckedTrackColor = Color(AppConfig.uiSettingsSwitchTrackInactive)
                                )
                            )
                        }

                        // ── Live stats card (only when active) ──────────
                        if (isActive) {
                            Spacer(Modifier.height(2.dp))

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(AppConfig.uiSettingsCardBackground))
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                StatRow("State", if (recorderState.isMoving) "\u25CF Recording" else "\u25CF Idle")
                                StatRow("Elapsed", formatDuration(recorderState.elapsedSeconds))
                                StatRow("Points", "${recorderState.pointCount}")
                                StatRow("Distance", "${"%.2f".format(recorderState.distanceNm)} nm")
                                StatRow("Max Speed", "${"%.1f".format(recorderState.maxSpeedKn)} kn")
                                StatRow("Avg Speed", "${"%.1f".format(recorderState.avgSpeedKn)} kn")
                            }
                        }
                    }
                }
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
