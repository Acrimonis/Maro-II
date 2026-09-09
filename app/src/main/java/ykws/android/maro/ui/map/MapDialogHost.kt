package ykws.android.maro.ui.map

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ykws.android.maro.R
import ykws.android.maro.ui.components.ConfirmSheet

/**
 * Windowed dialogs/sheets host (extracted from MapScreen). Owns ONLY popup windows
 * (AlertDialog / ModalBottomSheet) — the screen-lock scrim stays in MapScreen,
 * composited last so it paints above OverlayLayer.
 *
 * All dialog booleans stay hoisted in MapScreen (cross-cutting: back handler,
 * MapContent callbacks) and are passed here as read-only values + action callbacks.
 * Exit/stop sheet side-effect handlers (need the file-private Context.findActivity)
 * are supplied by MapScreen.
 */
@Composable
fun MapDialogHost(
    context: Context,
    trackViewModel: ykws.android.maro.data.track.TrackViewModel,
    // ── Exit / stop-recording sheets ──
    showExitDialog: Boolean,
    onExitSheetSave: () -> Unit,
    onExitSheetContinue: () -> Unit,
    onExitSheetDiscard: () -> Unit,
    onExitSheetDismiss: () -> Unit,
    showStopRecordingSheet: Boolean,
    onStopSheetSave: () -> Unit,
    onStopSheetContinue: () -> Unit,
    onStopSheetDiscard: () -> Unit,
    onStopSheetDismiss: () -> Unit,
    // ── Process-death recovery ──
    recoveryTrack: ykws.android.maro.data.track.Track?,
    // ── Background location permission (A2) ──
    showBgLocationDialog: Boolean,
    closeBgLocationDialog: () -> Unit,
    // ── GPS permission-missing (once per episode) ──
    gpsPermissionMissing: Boolean,
    gpsPermissionDialogDismissed: Boolean,
    gpsMode: Boolean,
    dismissGpsPermissionDialog: () -> Unit,
    // ── GPS source-switch confirmation ──
    pendingGpsModeToggle: Boolean?,
    clearPendingGpsModeToggle: () -> Unit,
    applyGpsMode: (Boolean) -> Unit,
    // ── Battery optimization (A4) ──
    showBatteryOptDialog: Boolean,
    closeBatteryOptDialog: () -> Unit
) {
    // ── Recording-aware exit sheet (shown on double-back while recording) ──
    if (showExitDialog) {
        RecordingExitSheet(
            onSave = onExitSheetSave,
            onContinue = onExitSheetContinue,
            onDiscard = onExitSheetDiscard,
            onDismiss = onExitSheetDismiss
        )
    }

    // ── Stop-recording confirmation (🐾 icon toggle / menu drawer stop) ──
    // Same 3-way sheet as exit-while-recording; "Continue" just dismisses.
    if (showStopRecordingSheet) {
        RecordingExitSheet(
            onSave = onStopSheetSave,
            onContinue = onStopSheetContinue,
            onDiscard = onStopSheetDiscard,
            onDismiss = onStopSheetDismiss
        )
    }

    // ── Process-death recovery dialog ─────────────────────────────
    recoveryTrack?.let { track ->
        AlertDialog(
            onDismissRequest = { trackViewModel.saveOrphanedCheckpoint(track) },
            title = { Text(stringResource(R.string.recovery_title)) },
            text = { Text(
                stringResource(R.string.recovery_found, track.name)
            ) },
            confirmButton = {
                TextButton(
                    onClick = { trackViewModel.resumeOrphanedCheckpoint(track) }
                ) { Text(stringResource(R.string.recovery_continue)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { trackViewModel.saveOrphanedCheckpoint(track) }
                ) { Text(stringResource(R.string.recovery_save)) }
            }
        )
    }

    // ── Background location permission dialog (A2) ──────────────────
    if (showBgLocationDialog) {
        AlertDialog(
            onDismissRequest = { closeBgLocationDialog() },
            title = { Text(stringResource(R.string.bg_location_title)) },
            text = { Text(stringResource(R.string.bg_location_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        closeBgLocationDialog()
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    }
                ) { Text(stringResource(R.string.bg_location_open_settings)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { closeBgLocationDialog() }
                ) { Text(stringResource(R.string.bg_location_not_now)) }
            }
        )
    }

    // ── GPS permission-missing dialog (shown once per missing-permission episode) ──
    if (gpsPermissionMissing && !gpsPermissionDialogDismissed && gpsMode) {
        AlertDialog(
            onDismissRequest = { dismissGpsPermissionDialog() },
            title = { Text(stringResource(R.string.gps_permission_title)) },
            text = { Text(stringResource(R.string.gps_permission_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        dismissGpsPermissionDialog()
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    }
                ) { Text(stringResource(R.string.bg_location_open_settings)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { dismissGpsPermissionDialog() }
                ) { Text(stringResource(R.string.bg_location_not_now)) }
            }
        )
    }

    // ── GPS source-switch confirmation while recording (bottom sheet, dashboard space) ──
    pendingGpsModeToggle?.let { enable ->
        ConfirmSheet(
            title = stringResource(R.string.gps_switch_confirm_title),
            message = stringResource(R.string.gps_switch_confirm_message),
            confirmLabel = stringResource(R.string.gps_switch_confirm_action),
            isDestructive = false,
            onConfirm = {
                clearPendingGpsModeToggle()
                applyGpsMode(enable)
            },
            onDismiss = { clearPendingGpsModeToggle() }
        )
    }

    // ── Battery optimization dialog (A4, triggered on recording start) ──
    if (showBatteryOptDialog) {
        AlertDialog(
            onDismissRequest = {
                closeBatteryOptDialog()
                context.getSharedPreferences("maro_battery_prefs", Context.MODE_PRIVATE)
                    .edit().putBoolean("battery_opt_prompted", true).apply()
            },
            title = { Text(stringResource(R.string.battery_opt_title)) },
            text = { Text(stringResource(R.string.battery_opt_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        closeBatteryOptDialog()
                        context.getSharedPreferences("maro_battery_prefs", Context.MODE_PRIVATE)
                            .edit().putBoolean("battery_opt_prompted", true).apply()
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                        trackViewModel.startRecording()
                    }
                ) { Text(stringResource(R.string.battery_opt_open_settings)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        closeBatteryOptDialog()
                        context.getSharedPreferences("maro_battery_prefs", Context.MODE_PRIVATE)
                            .edit().putBoolean("battery_opt_prompted", true).apply()
                        trackViewModel.startRecording()
                    }
                ) { Text(stringResource(R.string.battery_opt_not_now)) }
            }
        )
    }
}
