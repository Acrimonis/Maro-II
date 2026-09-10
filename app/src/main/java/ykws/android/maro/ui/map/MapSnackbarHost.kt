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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
                        },
                        snackKey = snack.uid,
                        onUndo = { onUndo(snack) },
                        onTimeout = { onTimeout(snack) }
                    )
                }
            }
        }
    }
}
