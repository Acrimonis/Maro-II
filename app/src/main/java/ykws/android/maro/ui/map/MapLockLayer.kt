package ykws.android.maro.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.osmdroid.views.MapView
import ykws.android.maro.data.settings.AppSettings

/**
 * The screen-lock layer: the full-screen input scrim, the mirrored lock/zoom controls and the lock
 * banner. It is called *last* in `MapScreen`'s ladder, after `OverlayLayer`, and that position is its
 * contract rather than a formatting choice — the scrim must paint above every drawer and the map.
 *
 * The caller owns the state it paints and the writes it fires: `lockBanner` arrives read-only, and
 * the intercepted tap that raises it crosses as [onInterceptedTap], whose body still lives in
 * `MapScreen` where `lockBannerAt` does (code-health step 3, tier 2c). Nothing moves out of the
 * screen's own state.
 */
@Composable
internal fun MapLockLayer(
    screenLocked: Boolean,
    lockBanner: Boolean?,
    bandTagsDrawn: Boolean,
    isLandscape: Boolean,
    portraitDashboardHeight: Dp,
    landscapeDashboardWidth: Dp,
    mapView: MapView?,
    viewModel: NavigationViewModel,
    appSettings: AppSettings,
    onToggleScreenLock: () -> Unit,
    onInterceptedTap: () -> Unit,
) {
    // ── Screen lock: full-screen input scrim + top-most unlock button ──
    //     The scrim consumes every pointer event so nothing below it (map,
    //     dashboard, drawers, controls) receives touch while locked. The
    //     duplicate button sits above the scrim so the lock can be toggled off.
    val lockTopInset = chromeTopInset(isLandscape)
    if (screenLocked) {
        LockScrim(
            onInterceptedTap = onInterceptedTap
        )
    }
    // Locked-overlay controls sit inside the map area: mirror MapContent's
    // dashboard padding (portrait: bottom; landscape: start) so the duplicate
    // lock button, zoom controls, and banner align over the originals.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                if (isLandscape)
                    PaddingValues(start = landscapeDashboardWidth, top = 0.dp, end = 0.dp, bottom = 0.dp)
                else
                    PaddingValues(start = 0.dp, top = 0.dp, end = 0.dp, bottom = portraitDashboardHeight)
            )
    ) {
        if (screenLocked) {
            LockScreenButton(
                locked = true,
                onClick = onToggleScreenLock,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(
                        top = lockTopInset,
                        // The arithmetic's one home, over the slot the row actually drew: the
                        // earth/water square is the one the setting can take away.
                        start = topToggleSlotOffset(lockSlot(appSettings.showLandWaterIcon))
                    )
            )
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
        if (lockBanner != null) {
            LockBanner(
                locked = lockBanner == true,
                tagsDrawn = bandTagsDrawn,
                modifier = Modifier.align(Alignment.BottomStart)
            )
        }
    }
}
