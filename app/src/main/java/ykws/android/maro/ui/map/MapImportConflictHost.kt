package ykws.android.maro.ui.map

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ykws.android.maro.R
import ykws.android.maro.data.track.ImportMode
import ykws.android.maro.data.track.TrackViewModel
import ykws.android.maro.ui.components.ConfirmAction
import ykws.android.maro.ui.components.ConfirmActionRole
import ykws.android.maro.ui.components.ConfirmDialog

/**
 * Single-GPX import conflict host (extracted from MapScreen, C11).
 * Owns the conflict dialog rendering + the runImport side-effect for the
 * Duplicate/Override/Cancel path. The picker launcher (importLauncher) stays in
 * MapScreen where its OverlayLayer trigger lives; banner/trackOp-status state stays
 * hoisted in MapScreen (banner renders inside MapContent) and is written here via
 * callbacks.
 *
 * The pending request is retained while the dialog animates out, so the slide/fade
 * exit still plays after `clearPending()` nulls the source state.
 */
@Composable
internal fun MapImportConflictHost(
    pendingTrackImport: PendingTrackImport?,
    context: Context,
    trackViewModel: TrackViewModel,
    trackScope: CoroutineScope,
    clearPending: () -> Unit,
    setTrackOpStatus: (String?) -> Unit,
    showImportBanner: (ImportBannerState) -> Unit
) {
    val retained = remember { mutableStateOf<PendingTrackImport?>(null) }
    LaunchedEffect(pendingTrackImport) {
        if (pendingTrackImport != null) retained.value = pendingTrackImport
    }

    retained.value?.let { pending ->
        fun runImport(mode: ImportMode) {
            clearPending()
            setTrackOpStatus(context.getString(R.string.importing_tracks))
            trackScope.launch(Dispatchers.IO) {
                try {
                    val result = trackViewModel.importTracks(pending.bytes, pending.extension, mode)
                    withContext(Dispatchers.Main) {
                        setTrackOpStatus(null)
                        showImportBanner(ImportBannerState.Result(result.imported, result.ignored))
                    }
                } catch (_: Exception) {
                    withContext(Dispatchers.Main) {
                        setTrackOpStatus(null)
                        showImportBanner(ImportBannerState.Failed)
                    }
                }
            }
        }
        ConfirmDialog(
            title = stringResource(R.string.import_match_title),
            visible = pendingTrackImport != null,
            onDismiss = { clearPending() },
            message = stringResource(R.string.import_match_message, pending.matchName),
            actions = listOf(
                ConfirmAction(stringResource(R.string.import_duplicate), ConfirmActionRole.PRIMARY) {
                    runImport(ImportMode.IMPORT_NEW)
                },
                ConfirmAction(stringResource(R.string.import_override), ConfirmActionRole.DANGER) {
                    runImport(ImportMode.UPDATE_EXISTING)
                },
                ConfirmAction(stringResource(R.string.action_cancel), ConfirmActionRole.SECONDARY) {
                    clearPending()
                }
            )
        )
    }
}
