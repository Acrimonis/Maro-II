package ykws.android.maro.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
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
 *
 * The route discard's toast carries a second action — **New acquisition** — while the three delete
 * snacks carry only Undo; its message names the phase honestly from string resources.
 */
@Composable
internal fun MapSnackbarHost(
    activeSnacks: List<ActiveSnack>,
    isLandscape: Boolean,
    /**
     * The live dashboard band (Phase 2), taken as a [State] so the band's animation re-runs this
     * host rather than `MapScreen`'s body (F6).
     */
    dashboardBandHeight: State<Dp>,
    landscapeDashboardWidth: Dp,
    onUndo: (ActiveSnack) -> Unit,
    onTimeout: (ActiveSnack) -> Unit,
    onSecondAction: (ActiveSnack) -> Unit = {}
) {
    val bandHeight = dashboardBandHeight.value
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(
                    bottom = if (isLandscape) 0.dp else bandHeight,
                    start = if (isLandscape) landscapeDashboardWidth else 0.dp
                )
                .padding(start = 12.dp, end = RIGHT_CONTROL_COLUMN_INSET),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            activeSnacks.forEach { snack ->
                key(snack.uid) {
                    val routeDiscard = snack as? ActiveSnack.RouteDiscard
                    SnackRow(
                        message = when (snack) {
                            is ActiveSnack.TrackDelete -> "Track '${snack.name}' deleted"
                            is ActiveSnack.MarkerDelete -> "Marker '${snack.name}' deleted"
                            is ActiveSnack.CreateUndo -> "Marker \"${snack.name}\" created"
                            is ActiveSnack.RouteDiscard -> stringResource(
                                if (snack.followed) R.string.route_discard_followed_toast
                                else R.string.route_discard_acq_toast
                            )
                        },
                        snackKey = snack.uid,
                        showUndo = true,
                        secondActionLabel = if (routeDiscard != null) {
                            stringResource(R.string.route_action_new_acquisition)
                        } else null,
                        onSecondAction = if (routeDiscard != null) {
                            { onSecondAction(snack) }
                        } else null,
                        onUndo = { onUndo(snack) },
                        onTimeout = { onTimeout(snack) }
                    )
                }
            }
        }
    }
}
