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
import ykws.android.maro.data.model.ImportMode
import ykws.android.maro.ui.components.ConfirmAction
import ykws.android.maro.ui.components.ConfirmActionRole
import ykws.android.maro.ui.components.ConfirmDialog

/** Held while the user decides how to import a single GPX that matches a marker already held. */
internal data class PendingMarkerImport(
    val bytes: ByteArray,
    val extension: String,
    val matchName: String
) {
    // A data class over a ByteArray would otherwise compare its reference, which the retained copy
    // below relies on being stable.
    override fun equals(other: Any?): Boolean =
        this === other || (other is PendingMarkerImport &&
            extension == other.extension &&
            matchName == other.matchName &&
            bytes.contentEquals(other.bytes))

    override fun hashCode(): Int = 31 * (31 * extension.hashCode() + matchName.hashCode()) + bytes.contentHashCode()
}

/**
 * Single-file marker import conflict host — the marker transfer's mirror of [MapImportConflictHost].
 *
 * Owns the Duplicate / Override / Cancel dialog and the run-import side effect for the chosen mode;
 * the picker launcher stays in [MapScreen] where its OverlayLayer trigger lives, and the busy banner
 * and the result banner stay hoisted there too, written here through callbacks.
 *
 * The pending request is retained while the dialog animates out, so the exit still plays after the
 * source state is nulled.
 */
@Composable
internal fun MarkerImportConflictHost(
    pendingMarkerImport: PendingMarkerImport?,
    context: Context,
    markersViewModel: MarkersViewModel,
    markerScope: CoroutineScope,
    clearPending: () -> Unit,
    setOpStatus: (String?) -> Unit,
    showImportBanner: (ImportBannerState) -> Unit
) {
    val retained = remember { mutableStateOf<PendingMarkerImport?>(null) }
    LaunchedEffect(pendingMarkerImport) {
        if (pendingMarkerImport != null) retained.value = pendingMarkerImport
    }

    retained.value?.let { pending ->
        fun runImport(mode: ImportMode) {
            clearPending()
            setOpStatus(context.getString(R.string.importing_markers))
            markerScope.launch(Dispatchers.IO) {
                try {
                    val result = markersViewModel.importMarkers(pending.bytes, pending.extension, mode)
                    withContext(Dispatchers.Main) {
                        setOpStatus(null)
                        showImportBanner(
                            ImportBannerState.Result(result.imported, result.ignored, kind = TransferKind.MARKER)
                        )
                    }
                } catch (_: Exception) {
                    withContext(Dispatchers.Main) {
                        setOpStatus(null)
                        showImportBanner(ImportBannerState.Failed)
                    }
                }
            }
        }
        ConfirmDialog(
            title = stringResource(R.string.import_marker_match_title),
            visible = pendingMarkerImport != null,
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
