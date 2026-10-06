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
import ykws.android.maro.config.AppConfig

/**
 * Render-only snackbar stack (extracted from MapScreen). Owns NO state — the queue
 * (`activeSnacks`) and its undo/timeout side effects stay hoisted in MapScreen
 * because they write cross-cutting map state (undo reopens drawers, timeouts drive
 * ViewModel deletes). `ActiveSnack`/`SnackRow` remain in MapScreen.kt (internal).
 *
 * Every row is the banner family's third **full-width face**: the skin and the band's clearance are
 * [`MapBanner`](MapControls.kt)'s, so this host pads only the band it sits above and never the row's
 * own edges (`docs/ui-component-guidelines.md` §5.7). The route discard's toast carries a second
 * action — **New acquisition** — while the three delete snacks carry only Undo; every message names
 * its item from string resources.
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
    /** The bottom-left tag column's own answer, handed to every row's banner clearance. */
    tagsDrawn: Boolean,
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
                    // The band's clearance plus the band's own gutter (`ui.map.toggle.gutter`), so the
                    // lowest row clears the dashboard's top edge; landscape keeps a 0 band offset and
                    // gains the same gutter (R1).
                    bottom = (if (isLandscape) 0.dp else bandHeight) + AppConfig.uiMapToggleGutter.dp,
                    start = if (isLandscape) landscapeDashboardWidth else 0.dp
                ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            activeSnacks.forEach { snack ->
                key(snack.uid) {
                    val routeDiscard = snack as? ActiveSnack.RouteDiscard
                    SnackRow(
                        message = when (snack) {
                            is ActiveSnack.TrackDelete -> stringResource(R.string.snack_track_deleted, snack.name)
                            is ActiveSnack.MarkerDelete -> stringResource(R.string.snack_marker_deleted, snack.name)
                            is ActiveSnack.CreateUndo -> stringResource(R.string.snack_marker_created, snack.name)
                            is ActiveSnack.RouteDiscard -> stringResource(
                                if (snack.followed) R.string.route_discard_followed_toast
                                else R.string.route_discard_acq_toast
                            )
                        },
                        snackKey = snack.uid,
                        onUndo = { onUndo(snack) },
                        onTimeout = { onTimeout(snack) },
                        tagsDrawn = tagsDrawn,
                        showUndo = true,
                        secondActionLabel = if (routeDiscard != null) {
                            stringResource(R.string.route_action_new)
                        } else null,
                        onSecondAction = if (routeDiscard != null) {
                            { onSecondAction(snack) }
                        } else null
                    )
                }
            }
        }
    }
}
