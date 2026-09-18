package ykws.android.maro.ui.map

import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import ykws.android.maro.data.settings.AppSettings

/**
 * GPS-follow orchestration effects (extracted from MapScreen):
 * mapView-zoom re-apply + two-finger rotation tracking, GPS auto-follow DR + heading-up,
 * and demo two-finger heading-up.
 */
@Composable
internal fun MapGpsFollowEffects(
    mapView: MapView?,
    viewModel: NavigationViewModel,
    depthViewModel: DepthViewModel,
    appSettings: AppSettings,
    autoFollowSuppressed: Boolean,
    /**
     * Non-consuming touch observation for inspect mode's movement gate: the mode needs each gesture's
     * own boundaries — its zero is taken at the start of one and its lift is not a scroll event — so
     * the gesture id is bumped here. This listener already exists and already returns false, so the
     * hook is added to it rather than a second listener being installed — a second
     * `setOnTouchListener` would silently replace this one and break `notifyUserInteraction()`.
     */
    onMapTouch: (Int) -> Unit = {},
    /**
     * Fires once per gesture when a one-finger drag carries the map past touch slop — the pan, and only
     * the pan: a second finger latches the gesture as a pinch, a tap never exceeds the slop, and the
     * zoom buttons are controls outside the map that never reach this listener.
     */
    onMapPan: () -> Unit = {}
) {
    val onMapTouchState = androidx.compose.runtime.rememberUpdatedState(onMapTouch)
    val onMapPanState = androidx.compose.runtime.rememberUpdatedState(onMapPan)
    // ── Force marker to match MapView zoom once the view is ready ────────
    // Even though _zoomLevel is seeded from persisted settings, there can be
    // a frame where collectAsState() captures the initial default before the
    // seeded value propagates.  This LaunchedEffect re-applies the real zoom
    // from the MapView after it's created, guaranteeing the marker is correct.
    LaunchedEffect(mapView) {
        val mv = mapView ?: return@LaunchedEffect
        viewModel.updateZoomLevel(mv.zoomLevelDouble)
        // Two-finger rotation tracking state.
        var rotating = false
        var lastAngleDeg = 0f
        // The drag gate ([MapPanDetector]): the pan the map itself performs, read here because a second
        // `setOnTouchListener` would silently replace this one and take `notifyUserInteraction()` with it.
        val panDetector = MapPanDetector(ViewConfiguration.get(mv.context).scaledTouchSlop.toFloat())
        mv.setOnTouchListener { _, ev ->
            onMapTouchState.value(ev.actionMasked)
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    viewModel.notifyUserInteraction()
                    panDetector.down(ev.x, ev.y)
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    // Second finger touched: the gesture is a pinch from here, so the gate latches it as
                    // one, and rotation starts tracking when demo heading-up asked for it.
                    panDetector.pinchStarted()
                    if (ev.pointerCount == 2 && viewModel.settings.value.demoHeadingUp) {
                        val dx = ev.getX(1) - ev.getX(0)
                        val dy = ev.getY(1) - ev.getY(0)
                        lastAngleDeg = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
                        rotating = true
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (panDetector.move(ev.pointerCount, ev.x, ev.y)) onMapPanState.value()
                    if (rotating && ev.pointerCount >= 2) {
                        val dx = ev.getX(1) - ev.getX(0)
                        val dy = ev.getY(1) - ev.getY(0)
                        val angleDeg = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
                        val delta = angleDeg - lastAngleDeg
                        // Normalise delta to [-180, 180] to avoid wraparound jumps.
                        val normalisedDelta = ((delta + 180f) % 360f + 360f) % 360f - 180f
                        if (kotlin.math.abs(normalisedDelta) >= 1f) {
                            val current = viewModel.navigationState.value.bearingDeg
                            viewModel.setDemoBearing((current + normalisedDelta + 360f) % 360f)
                            lastAngleDeg = angleDeg
                        }
                    } else {
                        // Single-finger pan — notify GPS to pause auto-follow.
                        viewModel.notifyUserInteraction()
                    }
                }
                MotionEvent.ACTION_POINTER_UP -> {
                    rotating = false
                }
                // The last finger lifted, or the gesture died: the gate is re-armed for the next one. A
                // pointer up inside a multi-touch gesture deliberately does not re-arm it — the rest of
                // that gesture stays the pinch it began as.
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    rotating = false
                    panDetector.gestureEnd()
                }
            }
            false // don't consume — the map still pans/zooms normally
        }
    }

    // ── GPS auto-follow: continuous DR + heading-up ────────────────────────
    // One throttled stream (≤ appSettings.mapRefreshFps) drives BOTH position and
    // orientation. Position comes from _displayPosition — a continuous 20 Hz dead-
    // reckoning stream that extrapolates between GPS fixes (gated <3 kn, capped 30m).
    // setCenter is instant (smoothness from DR, not animation). Re-engage uses
    // animateTo for smooth scroll-back after panning. Manual pinch/pan/fling keep
    // osmdroid's own full-rate path — the cap governs only this GPS-follow flow.
    LaunchedEffect(appSettings.gpsMode, appSettings.demoHeadingUp, autoFollowSuppressed, mapView) {
        val mv = mapView ?: return@LaunchedEffect
        if (!appSettings.gpsMode && !appSettings.demoHeadingUp) { mv.mapOrientation = 0f; mv.invalidate(); return@LaunchedEffect }
        if (autoFollowSuppressed) return@LaunchedEffect
        var reengage = true
        viewModel.cameraUpdates.collect { target ->
            val point = GeoPoint(target.position.latitude, target.position.longitude)
            if (reengage) {
                // Scroll smoothly back to the GPS position when follow resumes (no snap).
                mv.controller.animateTo(point)
                reengage = false
            } else {
                mv.controller.setCenter(point)
            }
            mv.mapOrientation = -target.bearingDeg
            mv.invalidate()
            // Keep depth-at-center following the GPS fix at the same capped cadence.
            depthViewModel.updateMapCenter(target.position.latitude, target.position.longitude)
        }
    }

    // ── Demo heading-up: apply two-finger rotation bearing to map orientation ─
    // When demoHeadingUp is enabled (and we're in demo mode), the bearing comes
    // from the two-finger rotation gesture via NavigationViewModel.setDemoBearing()
    // (NOT from panning — computeDemoSpeed() derives speed only).
    // Watch navigationState.bearingDeg and apply it to the MapView directly.
    // This effect runs separately from the GPS auto-follow effect above.
    LaunchedEffect(appSettings.demoHeadingUp, appSettings.gpsMode, mapView) {
        val mv = mapView ?: return@LaunchedEffect
        if (appSettings.gpsMode || !appSettings.demoHeadingUp) return@LaunchedEffect
        viewModel.navigationState.collect { nav ->
            mv.mapOrientation = -nav.bearingDeg
            mv.invalidate()
        }
    }
}
