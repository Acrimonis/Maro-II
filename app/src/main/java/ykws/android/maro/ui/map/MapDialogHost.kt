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
 * The route's one exit dialog and the resume confirmation joined the host on
 * 2026-10-01 (code-health step 3, tier 2b): both are windowed dialogs and this is the
 * one home for them. Their state still lives in the screen — `routeExitRequested`,
 * `routePinned` and `pendingResume` arrive as read-only values, and the writes go back
 * through the callbacks below.
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
    // ── The route's one exit dialog (R59) ──
    routeExitRequested: Boolean,
    routeState: RouteState,
    routeViewModel: RouteViewModel,
    onDismissExit: () -> Unit,
    onSaveRoute: (RoutePlan) -> Unit,
    /** The dialog's own Discard — the deferred, toasting ending. */
    onDiscardRoute: () -> Unit,
    /** Save-and-exit's silent ending: it writes first and never toasts. */
    onEndRoute: () -> Unit,
    // ── Resume confirmation (optional backup) ──
    resumeTarget: PendingTrackResume?,
    onClearResume: () -> Unit,
    onResumed: (fromList: Boolean) -> Unit
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

    // ── The route's one exit dialog (R59) — asked by the toggle and the back key ───
    // Both doors raise this same dialog, and it reads in the order every action surface takes
    // (ui-component-guidelines §5.6): the affirmative first, the neutral stay, the loss last —
    // **Save Route to Track** · **Continue route** · **Discard Route**. Its save writes the followed
    // route and is **disabled while that route already has its track**, so no second press writes a
    // second track for one line.
    if (routeExitRequested) {
        val front = routeState.plan
        val followedTrackId = (routeState as? RouteState.Following)?.followedTrackId
        // A followed saved route is already a track, so its save door stays grey and its third
        // door reads "Stop following" rather than "Discard Route".
        val frontUnwritten = front != null && !routeViewModel.isRouteSaved(front) && followedTrackId == null
        ConfirmDialog(
            title = stringResource(R.string.route_exit_title),
            visible = true,
            onDismiss = { onDismissExit() },
            message = null,
            options = null,
            actions = listOf(
                // The accent is the dialog's own outcome: it writes the route the toggle follows.
                ConfirmAction(
                    label = stringResource(R.string.route_exit_save),
                    role = ConfirmActionRole.PRIMARY,
                    enabled = frontUnwritten
                ) {
                    onDismissExit()
                    if (front != null) onSaveRoute(front)
                    onEndRoute()
                },
                ConfirmAction(
                    label = stringResource(R.string.route_exit_continue),
                    role = ConfirmActionRole.SECONDARY
                ) { onDismissExit() },
                ConfirmAction(
                    label = stringResource(
                        if (followedTrackId != null) R.string.route_exit_stop_following
                        else R.string.route_exit_discard
                    ),
                    role = ConfirmActionRole.DANGER
                ) {
                    onDismissExit()
                    onDiscardRoute()
                }
            )
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
