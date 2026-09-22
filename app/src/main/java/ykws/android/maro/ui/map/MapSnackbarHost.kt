package ykws.android.maro.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ykws.android.maro.R

/**
 * Render-only snackbar stack (extracted from MapScreen). Owns NO state — the queue
 * (`activeSnacks`) and its undo/timeout side effects stay hoisted in MapScreen
 * because they write cross-cutting map state (undo reopens drawers, timeouts drive
 * ViewModel deletes). `ActiveSnack`/`SnackRow` remain in MapScreen.kt (internal).
 */
@Composable
internal fun MapSnackbarHost(
    activeSnacks: List<ActiveSnack>,
    isLandscape: Boolean,
    portraitDashboardHeight: Dp,
    landscapeDashboardWidth: Dp,
    onUndo: (ActiveSnack) -> Unit,
    onTimeout: (ActiveSnack) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(
                    bottom = if (isLandscape) 0.dp else portraitDashboardHeight,
                    start = if (isLandscape) landscapeDashboardWidth else 0.dp
                )
                .padding(start = 12.dp, end = RIGHT_CONTROL_COLUMN_INSET),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            activeSnacks.forEach { snack ->
                key(snack.uid) {
                    SnackRow(
                        message = when (snack) {
                            is ActiveSnack.TrackDelete -> "Track '${snack.name}' deleted"
                            is ActiveSnack.MarkerDelete -> "Marker '${snack.name}' deleted"
                            is ActiveSnack.CreateUndo -> "Marker \"${snack.name}\" created"
                            // The route's own line: the sentence is this surface's, the reason is the
                            // engine's — an id, resolved here, so no engine holds user-facing text.
                            is ActiveSnack.RouteFailed -> stringResource(
                                R.string.route_toast_refresh_failed,
                                stringResource(snack.reasonResId)
                            )
                        },
                        snackKey = snack.uid,
                        // A failed refresh carries no undo: nothing happened, the standing line having
                        // been left exactly as it was and the ladder untouched (R13).
                        showUndo = snack !is ActiveSnack.RouteFailed,
                        onUndo = { onUndo(snack) },
                        onTimeout = { onTimeout(snack) }
                    )
                }
            }
        }
    }
}
