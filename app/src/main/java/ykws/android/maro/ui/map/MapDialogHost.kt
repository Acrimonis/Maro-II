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
import ykws.android.maro.ui.components.ConfirmAction
import ykws.android.maro.ui.components.ConfirmActionRole
import ykws.android.maro.ui.components.ConfirmDialog

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
internal fun MapDialogHost(
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
    closeBatteryOptDialog: () -> Unit,
    /** Marks the prompt as answered, so it never reappears (persisted via AppSettings). */
    onBatteryOptPrompted: () -> Unit
) {
    // ── Recording-aware exit sheet (shown on double-back while recording) ──
    val exitSaveLabel = stringResource(R.string.recording_exit_save)
    val exitContinueLabel = stringResource(R.string.recording_exit_continue)
    val exitDiscardLabel = stringResource(R.string.recording_exit_discard)
    val recordingExitActions: (() -> Unit, () -> Unit, () -> Unit) -> List<ConfirmAction> =
        { save, cont, discard ->
            listOf(
                ConfirmAction(exitSaveLabel, ConfirmActionRole.PRIMARY, save),
                ConfirmAction(exitContinueLabel, ConfirmActionRole.SECONDARY, cont),
                ConfirmAction(exitDiscardLabel, ConfirmActionRole.DANGER, discard)
            )
        }

    // ── Recording-aware exit dialog (shown on double-back while recording) ──
    ConfirmDialog(
        title = stringResource(R.string.recording_exit_title),
        visible = showExitDialog,
        onDismiss = onExitSheetDismiss,
        message = stringResource(R.string.recording_exit_message),
        actions = recordingExitActions(onExitSheetSave, onExitSheetContinue, onExitSheetDiscard)
    )

    // ── Stop-recording confirmation (🐾 icon toggle / menu drawer stop) ──
    // Same 3-way dialog as exit-while-recording; "Continue" just dismisses.
    ConfirmDialog(
        title = stringResource(R.string.recording_exit_title),
        visible = showStopRecordingSheet,
        onDismiss = onStopSheetDismiss,
        message = stringResource(R.string.recording_exit_message),
        actions = recordingExitActions(onStopSheetSave, onStopSheetContinue, onStopSheetDiscard)
    )

    // ── Process-death recovery dialog ─────────────────────────────
    // Dismissing (scrim tap / back) saves the checkpoint — that side effect is preserved
    // verbatim, and no Cancel is offered because dismissal is not an abort.
    recoveryTrack?.let { track ->
        ConfirmDialog(
            title = stringResource(R.string.recovery_title),
            visible = true,
            onDismiss = { trackViewModel.saveOrphanedCheckpoint(track) },
            message = stringResource(R.string.recovery_found, track.name),
            actions = listOf(
                ConfirmAction(stringResource(R.string.recovery_continue), ConfirmActionRole.PRIMARY) {
                    trackViewModel.resumeOrphanedCheckpoint(track)
                },
                ConfirmAction(stringResource(R.string.recovery_save), ConfirmActionRole.SECONDARY) {
                    trackViewModel.saveOrphanedCheckpoint(track)
                }
            )
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

    // ── GPS source-switch confirmation while recording ──
    // Dismissal clears the pending toggle (a clean abort), so no Cancel action is added.
    pendingGpsModeToggle?.let { enable ->
        ConfirmDialog(
            title = stringResource(R.string.gps_switch_confirm_title),
            visible = true,
            onDismiss = { clearPendingGpsModeToggle() },
            message = stringResource(R.string.gps_switch_confirm_message),
            actions = listOf(
                ConfirmAction(stringResource(R.string.gps_switch_confirm_action), ConfirmActionRole.PRIMARY) {
                    clearPendingGpsModeToggle()
                    applyGpsMode(enable)
                }
            )
        )
    }

    // ── Battery optimization dialog (A4, triggered on recording start) ──
    if (showBatteryOptDialog) {
        AlertDialog(
            onDismissRequest = {
                closeBatteryOptDialog()
                onBatteryOptPrompted()
            },
            title = { Text(stringResource(R.string.battery_opt_title)) },
            text = { Text(stringResource(R.string.battery_opt_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        closeBatteryOptDialog()
                        onBatteryOptPrompted()
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
                        onBatteryOptPrompted()
                        trackViewModel.startRecording()
                    }
                ) { Text(stringResource(R.string.battery_opt_not_now)) }
            }
        )
    }
}
