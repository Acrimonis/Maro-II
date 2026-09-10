package ykws.android.maro.ui.map

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ykws.android.maro.R
import ykws.android.maro.data.track.ImportMode
import ykws.android.maro.data.track.TrackViewModel

/**
 * Single-GPX import conflict host (extracted from MapScreen, C11).
 * Owns the conflict sheet rendering + the runImport side-effect for the
 * Duplicate/Override/Cancel path. The picker launcher (importLauncher) stays in
 * MapScreen where its OverlayLayer trigger lives; banner/trackOp-status state stays
 * hoisted in MapScreen (banner renders inside MapContent) and is written here via
 * callbacks.
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
    pendingTrackImport?.let { pending ->
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
        ImportConflictSheet(
            matchName = pending.matchName,
            onDuplicate = { runImport(ImportMode.IMPORT_NEW) },
            onOverride = { runImport(ImportMode.UPDATE_EXISTING) },
            onCancel = { clearPending() },
            onDismiss = { clearPending() }
        )
    }
}
