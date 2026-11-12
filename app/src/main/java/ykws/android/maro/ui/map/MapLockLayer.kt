package ykws.android.maro.ui.map

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.osmdroid.views.MapView
import ykws.android.maro.data.settings.AppSettings

/**
 * The screen-lock layer: the full-screen input scrim and the mirrored lock/zoom controls. It is called
 * *last* in `MapScreen`'s ladder, after `OverlayLayer`, and that position is its contract rather than a
 * formatting choice — the scrim must paint above every drawer and the map. The lock and unlock line is
 * not drawn here: it is a pill on the notice host, which paints above the ladder too.
 *
 * The caller owns the state it paints and the writes it fires: the intercepted tap crosses as
 * [onInterceptedTap], whose body still lives in `MapScreen` (code-health step 3, tier 2c). Nothing moves
 * out of the screen's own state.
 */
@Composable
internal fun MapLockLayer(
    screenLocked: Boolean,
    isLandscape: Boolean,
    /**
     * The live dashboard band (Phase 2), taken as a [State] so the band's animation re-runs this
     * layer rather than `MapScreen`'s body (F6).
     */
    dashboardBandHeight: State<Dp>,
    landscapeDashboardWidth: Dp,
    mapView: MapView?,
    viewModel: NavigationViewModel,
    appSettings: AppSettings,
    onToggleScreenLock: () -> Unit,
    onInterceptedTap: () -> Unit,
) {
    // ── Screen lock: full-screen input scrim + top-most unlock button ──
    //     The scrim consumes every pointer event so nothing below it (map, dashboard, drawers,
    //     controls) receives touch while locked. The duplicate button sits above the scrim so the
    //     lock can be toggled off.
    val lockTopInset = chromeTopInset(isLandscape)
    // The band is read only on the path that actually paints over the dashboard (G8): while unlocked
    // there is nothing to align, so the read — and the recomposition it would cost on every frame of
    // the band's tween — is deferred until there is.
    if (screenLocked) {
        LockScrim(
            onInterceptedTap = onInterceptedTap
        )
        // Locked-overlay controls sit inside the map area: mirror MapContent's
        // dashboard padding (portrait: bottom; landscape: start) so the duplicate
        // lock button and zoom controls align over the originals.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    if (isLandscape)
                        PaddingValues(start = landscapeDashboardWidth, top = 0.dp, end = 0.dp, bottom = 0.dp)
                    else
                        PaddingValues(start = 0.dp, top = 0.dp, end = 0.dp, bottom = dashboardBandHeight.value)
                )
        ) {
            // Unlocking is a hold, never a tap (U04): the zoom pair below already asks for a double
            // tap, and a single tap on the lock square — a splash, a sleeve — would otherwise undo
            // the lock. The square's own clickable stays inert; the gesture box drawn over it takes
            // every pointer first, flashes the lock line on a tap and unlocks on a long press.
            val currentToggle by rememberUpdatedState(onToggleScreenLock)
            val currentIntercepted by rememberUpdatedState(onInterceptedTap)
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(
                        top = lockTopInset,
                        // The arithmetic's one home, over the slot the row actually drew: the same
                        // list the row composes from finds the lock square's visible index, so the
                        // mirror cannot land on another control.
                        start = topToggleSlotOffset(
                            TopToggleControl.row(appSettings).indexOf(TopToggleControl.LOCK)
                        )
                    )
            ) {
                LockScreenButton(
                    locked = true,
                    onClick = {}
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .semantics { onLongClick { currentToggle(); true } }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { currentIntercepted() },
                                onLongPress = { currentToggle() }
                            )
                        }
                )
            }
            ZoomControls(
                onZoomIn = {
                    mapView?.let { mv ->
                        mv.controller.zoomIn()
                        viewModel.updateZoomLevel(mv.zoomLevelDouble)
                    }
                },
                onZoomOut = {
                    mapView?.let { mv ->
                        mv.controller.zoomOut()
                        viewModel.updateZoomLevel(mv.zoomLevelDouble)
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 6.dp, bottom = 6.dp),
                doubleTap = true
            )
        }
    }
}
