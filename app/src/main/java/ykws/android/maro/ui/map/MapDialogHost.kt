package ykws.android.maro.ui.map

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ykws.android.maro.R
import ykws.android.maro.ui.components.ConfirmAction
import ykws.android.maro.ui.components.ConfirmActionRole
import ykws.android.maro.ui.components.ConfirmDialog
import ykws.android.maro.ui.components.OptionRow

/**
 * Windowed dialogs/sheets host (extracted from MapScreen). Owns ONLY popup windows
 * (AlertDialog / ModalBottomSheet) — the screen-lock scrim lives in `MapLockLayer`,
 * called after this host so it paints above every drawer and the map.
 *
 * All dialog booleans stay hoisted in MapScreen (cross-cutting: back handler,
 * MapContent callbacks) and are passed here as read-only values + action callbacks.
 * Exit/stop sheet side-effect handlers (need the file-private Context.findActivity)
 * are supplied by MapScreen.
 *
 * The resume confirmation joined the host on 2026-10-01 (code-health step 3, tier 2b): it is a
 * windowed dialog and this is its home. Its state still lives in the screen — `pendingResume`
 * arrives as a read-only value, and the writes go back through the callbacks below. A route
 * asks no dialog on leaving (R59, R100): its endings defer on an Undo toast instead.
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
    onBatteryOptPrompted: () -> Unit,
    // ── Resume confirmation (optional backup) ──
    resumeTarget: PendingTrackResume?,
    onClearResume: () -> Unit,
    onResumed: (fromList: Boolean) -> Unit,
    // ── Home-port arrival prompt ──
    showArrivalPrompt: Boolean,
    onArrivalSave: () -> Unit,
    onArrivalContinue: () -> Unit,
    onArrivalDiscard: () -> Unit,
    onArrivalDismiss: () -> Unit
) {
    // ── Recording-aware exit sheet (shown on double-back while recording) ──
    val exitSaveLabel = stringResource(R.string.recording_exit_save)
    val exitContinueLabel = stringResource(R.string.recording_exit_continue)
    val exitDiscardLabel = stringResource(R.string.recording_exit_discard)
    val recordingExitActions: (() -> Unit, () -> Unit, () -> Unit) -> List<ConfirmAction> =
        { save, cont, discard ->
            listOf(
                ConfirmAction(exitSaveLabel, ConfirmActionRole.PRIMARY, onClick = save),
                ConfirmAction(exitContinueLabel, ConfirmActionRole.SECONDARY, onClick = cont),
                ConfirmAction(exitDiscardLabel, ConfirmActionRole.DANGER, onClick = discard)
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
    // The recording family's own three doors, in its order and clothes — Continue recording (the
    // recovered session's forward outcome, so it takes the accent), Save track, Discard track — and
    // they are the same at every recorder state, so no extra axis is invented for this dialog.
    // Dismissing (scrim tap / back) still SAVES the checkpoint: dismissal is not an abort, and the
    // explicit Discard door did not change that — Discard is the only path that deletes it.
    recoveryTrack?.let { track ->
        ConfirmDialog(
            title = stringResource(R.string.recovery_title),
            visible = true,
            onDismiss = { trackViewModel.saveOrphanedCheckpoint(track) },
            message = stringResource(R.string.recovery_found, track.name),
            actions = listOf(
                ConfirmAction(exitContinueLabel, ConfirmActionRole.PRIMARY) {
                    trackViewModel.resumeOrphanedCheckpoint(track)
                },
                ConfirmAction(exitSaveLabel, ConfirmActionRole.SECONDARY) {
                    trackViewModel.saveOrphanedCheckpoint(track)
                },
                ConfirmAction(exitDiscardLabel, ConfirmActionRole.DANGER) {
                    trackViewModel.discardOrphanedCheckpoint(track)
                }
            )
        )
    }

    // ── Home-port arrival prompt ─────────────────────────────
    // The recording's own arrival doors, reusing the exit sheet's three labels; Save is the
    // trip's natural forward outcome at the port, so it keeps the accent. Dismissal means
    // Continue — the checkpoint was already persisted when the prompt fired.
    ConfirmDialog(
        title = stringResource(R.string.home_port_arrival_title),
        visible = showArrivalPrompt,
        onDismiss = onArrivalDismiss,
        message = stringResource(R.string.recording_exit_message),
        actions = recordingExitActions(onArrivalSave, onArrivalContinue, onArrivalDiscard)
    )

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

    // ── Resume confirmation dialog (optional backup) — hosted outside the drawers so closing the
    //    source surface cannot drop it. `resumeTarget` drives dismissal; the retained copy keeps
    //    the dialog mounted while it animates out. ──
    val resumeBackupSuffix = stringResource(R.string.track_backup_suffix)
    var resumeRetained by remember { mutableStateOf<PendingTrackResume?>(null) }
    LaunchedEffect(resumeTarget) {
        if (resumeTarget != null) resumeRetained = resumeTarget
    }
    if (resumeRetained != null) {
        var backup by remember(resumeTarget?.trackId) { mutableStateOf(true) }
        ConfirmDialog(
            title = stringResource(R.string.resume_confirm_title),
            visible = resumeTarget != null,
            onDismiss = { onClearResume() },
            message = stringResource(R.string.resume_confirm_message),
            options = {
                OptionRow(
                    label = stringResource(R.string.resume_confirm_backup),
                    checked = backup,
                    onCheckedChange = { backup = it },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            actions = listOf(
                ConfirmAction(stringResource(R.string.action_resume), ConfirmActionRole.PRIMARY) {
                    resumeRetained?.let { pending ->
                        trackViewModel.resumeTrack(
                            pending.trackId,
                            if (backup) resumeBackupSuffix else null
                        )
                        onResumed(pending.fromList)
                    }
                    onClearResume()
                },
                ConfirmAction(stringResource(R.string.action_cancel), ConfirmActionRole.SECONDARY) {
                    onClearResume()
                }
            )
        )
    }
}
