package ykws.android.maro.ui.map

import android.graphics.drawable.GradientDrawable
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.sample
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.data.model.RoutePoint
import android.graphics.Color as AndroidColor

/** Log tag for the route mode's own host — its two edges, kept for the device pass. */
private const val TAG = "MaroRoute"

/**
 * The route line's own overlay title. The `route_` prefix is what [OverlayZOrder] reads to keep the
 * line inside the **track band**, and because this file appends it after the tracks it already
 * paints, the line lands above them and below the markers — the epic's stated order, enforced by the
 * existing re-order rather than by a second rule.
 */
internal const val ROUTE_LINE_TITLE = "route_line"

/**
 * The destination pin's title. The `marker_` prefix is the marker band's own, so the pin is drawn
 * above the line and the tracks, beside the other map markers.
 */
internal const val ROUTE_PIN_TITLE = "marker_route_dest"

/** Side (dp) of the destination dot drawn at the resolved end of the route. */
private const val ROUTE_PIN_SIZE_DP = 18f

/**
 * The route's map half: the line, the pin and the screen-centred aim — and nothing else.
 *
 * This is **the one file that touches osmdroid for the Route feature**, so ordering, the pin's slot
 * and the aim's own reading of the map centre all have a single home. The epic's third collision —
 * `MapOverlayRenderer` / `OverlayZOrder` — is answered by adding one title prefix there and letting
 * the existing `reorder()` do the placing.
 *
 * The confirmation is **not** here: it lives in the dashboard slot, composed by the shell from the
 * same state, so the panel the outcomes are taken from cannot fight the drag the aim is made with
 * and needs no ladder host to be painted on. This host therefore raises nothing, dismisses nothing
 * and owns no dialog.
 *
 * The start is [boatPosition], the position the dashboard reads: the GPS fix in GPS mode, the same
 * seam in demo mode, **never** the map centre — which is the aim itself, and would otherwise make
 * the boat chase its own target. It is read once, on the armed frame, and the machine keeps it; the
 * aim is the only end that moves afterwards.
 *
 * The preview is driven by movement, not by a clock: every map motion pushes the current screen
 * centre into one flow, and one collector asks for a search at most every
 * [ROUTE_PREVIEW_MIN_INTERVAL_MS] and only once the aim has moved past [ROUTE_AIM_THRESHOLD_M]. A
 * flung map therefore lands a handful of searches rather than one per frame, and each ask cancels the
 * one in flight — which is the cancellation the epic asks for, expressed once rather than as a
 * velocity test the platform does not offer.
 *
 * @param armed      the mode's single switch, owned by the shell: on aims, off ends the route.
 * @param speedKn    speed over ground (kn), fed to the pace window; null when nothing is moving.
 * @param positionRestricted true when the current position sits in a regulated zone or the band, so
 *                   the reading measures the limit rather than the boat and is dropped by [RoutePace].
 * @param onEndRoute runs when the mode ends — the toggle's off, the panel's Cancel and the back key.
 */
@OptIn(FlowPreview::class)
@Composable
internal fun RouteHost(
    mapView: MapView?,
    boatPosition: LatLng?,
    state: RouteState,
    armed: Boolean,
    gpsMode: Boolean,
    speedKn: Float?,
    positionRestricted: Boolean,
    setPaceKn: Float,
    mapCenterOffsetPx: Int,
    mapCenterOffsetDp: Dp,
    viewModel: RouteViewModel,
    onEndRoute: () -> Unit,
    modifier: Modifier = Modifier
) {
    // The back key is the mode's own escape while it is armed: the confirmation sits in the
    // dashboard slot, so nothing else offers one — and without this the press would reach the
    // screen's exit guard with an unconfirmed route still standing on the map.
    BackHandler(enabled = armed) { onEndRoute() }

    // ── The mode's edges: the start is read once, here, and the machine keeps it ──
    // The read happens in this effect's own frame — the one where the toggle turned on — and the
    // value is handed to the Idle → Draft edge, which is what makes a later re-read impossible rather
    // than merely unlikely: no seam survives the call, so a preview can only be asked from the anchor
    // the mode opened on. The aim is then the only end that moves.
    LaunchedEffect(armed) {
        if (armed) {
            val start = boatPosition?.let { RoutePoint(it.latitude, it.longitude) }
            Log.d(TAG, "mode armed — start frozen at $start")
            viewModel.beginDraft(start)
        } else {
            Log.d(TAG, "mode ended")
            viewModel.end()
        }
    }

    // ── The aim: the screen centre, read the way the inspect anchor reads it ────
    var motionId by remember { mutableIntStateOf(0) }
    val aimFlow = remember { MutableStateFlow<RoutePoint?>(null) }
    var lastPreviewAim by remember { mutableStateOf<RoutePoint?>(null) }

    fun readAim(): RoutePoint? {
        val mv = mapView ?: return null
        val anchor = inspectAnchor(mv, mapCenterOffsetPx) ?: return null
        return RoutePoint(anchor.latitude, anchor.longitude)
    }

    DisposableEffect(mapView, armed) {
        val mv = mapView
        if (mv == null || !armed) return@DisposableEffect onDispose { }
        val listener = object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
                motionId++
                return false
            }

            override fun onZoom(event: ZoomEvent?): Boolean {
                motionId++
                return false
            }
        }
        mv.addMapListener(listener)
        onDispose { mv.removeMapListener(listener) }
    }

    // Arming opens on the frame already on screen: no touch is needed for the first preview.
    LaunchedEffect(armed, mapView) {
        if (!armed) {
            aimFlow.value = null
            lastPreviewAim = null
            return@LaunchedEffect
        }
        lastPreviewAim = null
        aimFlow.value = readAim()
    }

    // Every map motion pushes the centre; the driver below decides what to do with it.
    LaunchedEffect(motionId, armed) {
        if (!armed) return@LaunchedEffect
        readAim()?.let { aimFlow.value = it }
    }

    // The preview driver: one search per 333 ms at most, only past the aim threshold. Only the aim
    // is passed on — the start the search uses is the machine's own frozen anchor.
    LaunchedEffect(armed, mapView) {
        if (!armed) return@LaunchedEffect
        aimFlow.filterNotNull().sample(ROUTE_PREVIEW_MIN_INTERVAL_MS).collect { aim ->
            if (routeAimPassed(lastPreviewAim, aim)) {
                lastPreviewAim = aim
                viewModel.preview(aim)
            }
        }
    }

    // ── The pace window: the boat's own pace, in GPS mode only ─────────────────
    LaunchedEffect(gpsMode, speedKn, positionRestricted, setPaceKn) {
        viewModel.observePace(
            speedKn = if (gpsMode) speedKn?.toDouble() else null,
            restricted = positionRestricted,
            nowMs = System.currentTimeMillis(),
            setPaceKn = setPaceKn.toDouble()
        )
    }

    // ── The map objects: one file owns the line and the pin ────────────────────
    LaunchedEffect(mapView, state) {
        val mv = mapView ?: return@LaunchedEffect
        val mine = mv.overlays.filter { overlay ->
            val title = (overlay as? Polyline)?.title ?: (overlay as? Marker)?.title
            title == ROUTE_LINE_TITLE || title == ROUTE_PIN_TITLE
        }
        if (mine.isNotEmpty()) mv.overlays.removeAll(mine)

        val plan = state.plan
        if (plan != null && plan.points.size >= 2) {
            val alpha = transparencyPctToAlpha(AppConfig.routeLineTransparencyPct)
            val colour = AppConfig.routeLineColor
            val line = Polyline().apply {
                title = ROUTE_LINE_TITLE
                setPoints(plan.points.map { GeoPoint(it.latitude, it.longitude) })
                outlinePaint.apply {
                    color = AndroidColor.argb(
                        alpha,
                        AndroidColor.red(colour),
                        AndroidColor.green(colour),
                        AndroidColor.blue(colour)
                    )
                    strokeWidth = dpToPx(AppConfig.routeLineWidthDp, mv.paintDensity)
                    isAntiAlias = true
                }
            }
            mv.overlays.add(line)

            // The pin is drawn at the **resolved** destination, which is where the route really ends
            // — an aim on land or in another stretch has already moved to the boat's own stretch.
            val density = mv.paintDensity
            val pinPx = (ROUTE_PIN_SIZE_DP * density).toInt().coerceAtLeast(1)
            val ringPx = (AppConfig.routePinRingWidthDp * density).toInt().coerceAtLeast(1)
            val icon = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(AppConfig.routePinColor)
                setStroke(ringPx, AndroidColor.WHITE)
            }
            icon.setBounds(0, 0, pinPx, pinPx)
            val pin = Marker(mv).apply {
                title = ROUTE_PIN_TITLE
                position = GeoPoint(plan.destination.latitude, plan.destination.longitude)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                this.icon = icon
                // The tap is consumed rather than raising osmdroid's info bubble, which would show
                // this overlay's internal title to the user.
                setOnMarkerClickListener { _, _ -> true }
            }
            mv.overlays.add(pin)
        }
        OverlayZOrder.reorder(mv)
        mv.invalidate()
    }

    Box(modifier = modifier) {
        if (armed && state is RouteState.Draft) {
            // The target is the aim itself — a marker, not a control: the dashboard panel carries
            // the details and every outcome, so a tap here would have nothing of its own to raise.
            RouteAimTarget(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = mapCenterOffsetDp)
            )
        }
    }
}
