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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
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
 * The route lines' own overlay title prefix. `OverlayZOrder` reads it to keep every line inside the
 * **track band**, above the tracks and below the markers — the epic's stated order, enforced by the
 * existing re-order rather than by a second rule.
 */
internal const val ROUTE_LINE_TITLE = "route_line"

/**
 * The destination pin's title. The `marker_` prefix is the marker band's own, so the pin is drawn
 * above the lines and the tracks, beside the other map markers.
 */
internal const val ROUTE_PIN_TITLE = "marker_route_dest"

/** Side (dp) of the destination dot drawn at the resolved end of the route. */
private const val ROUTE_PIN_SIZE_DP = 18f

/**
 * The route's map half: the lines, the pin and the screen-centred aim — and nothing else.
 *
 * This is **the one file that touches osmdroid for the Route feature**, so ordering, the pin's slot
 * and the aim's own reading of the map centre all have a single home.
 *
 * **The objects are attached once and mutated in place.** The polyline pool and the pin are created at
 * this file's own composition and added to the map a single time; an answer then sets their points,
 * their colour and their transparency rather than rebuilding the set, which is the shape the contour
 * polylines already ship and the prerequisite the **stale ladder** created (R14): a ladder repainted
 * on every refresh could not be rebuilt per state change without churning the whole overlay stack
 * under the tracks the map is drawing.
 *
 * The pool has one slot per drawn line — the front route plus the ladder's configured oldest-plus-newest
 * — and the ladder's cap and its 20 % → 80 % band come from [`routeLadderForDrawing`] and
 * [`routeLadderAlpha`], so the drawing reads the same rule the tests do.
 *
 * The confirmation and the following panel are **not** here: they live in the dashboard slot, composed
 * by the shell from the same state, so the panel the outcomes are taken from cannot fight the drag the
 * aim is made with. This host therefore raises nothing, dismisses nothing and owns no dialog.
 *
 * The origin is [boatPosition], the position the dashboard reads: the GPS fix in GPS mode, the same
 * seam in demo mode, **never** the map centre — which is the aim itself, and would otherwise make the
 * boat chase its own target. It is read once, on the armed frame, and the machine keeps it; the aim is
 * the only end that moves while the destination is chosen.
 *
 * **The ask policy is here, and it is not a leading-edge one** (R2, R3): no aim is asked on the arming
 * frame — the frame is *read*, so a later aim has something to be measured against, and nothing is
 * computed from it. Every map motion pushes the current screen centre into one flow; the driver takes
 * an aim only once the drag has **stood still for the configured settle** (the debounce) **and** has
 * moved at least the configured ground distance from the aim last asked for. A drag shorter than that,
 * or one that never stops, asks for nothing — so the mode opens quiet and the line appears when the
 * aim has really been placed.
 *
 * **The refresh cycle's clock is here too** (R10, R7): while a route is followed and not frozen, this
 * file ticks once a second and hands the tick to [`routeRefreshOrigin`], which reads the boat's **live
 * position** — the very seam the dashboard reads, through `rememberUpdatedState` so a tick a minute
 * into the phase sees where the boat is *now* rather than where it stood when the phase opened — and
 * decides against the configured interval or the configured distance off the standing route. The host
 * holds no policy of its own: the machine owns the call, the engine may only veto it, and the whole
 * decision is the one pure function the tests read.
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
    // The back key is the mode's own escape while it is armed: the panel sits in the dashboard slot,
    // so nothing else offers one — and without this the press would reach the screen's exit guard with
    // a route still standing on the map. The shell owns what the escape means in each phase: silent
    // while the destination is chosen, the one exit dialog once a route is followed.
    BackHandler(enabled = armed) { onEndRoute() }

    // ── The mode's edges: the origin is read once, here, and the machine keeps it ──
    // The read happens in this effect's own frame — the one where the toggle turned on — and the value
    // is handed to the Idle → Choosing edge, which is what makes a later re-read impossible rather
    // than merely unlikely: no seam survives the call, so a preview can only be asked from the anchor
    // the mode opened on, and the engine is told that origin exactly once (R7). The aim is then the
    // only end that moves.
    LaunchedEffect(armed) {
        if (armed) {
            val origin = boatPosition?.let { RoutePoint(it.latitude, it.longitude) }
            Log.d(TAG, "mode armed — origin frozen at $origin")
            viewModel.beginDraft(origin)
        } else {
            Log.d(TAG, "mode ended")
            viewModel.end()
        }
    }

    // ── The aim: the screen centre, read the way the inspect anchor reads it ────
    var motionId by remember { mutableIntStateOf(0) }
    val aimFlow = remember { MutableStateFlow<RoutePoint?>(null) }
    var lastAskedAim by remember { mutableStateOf<RoutePoint?>(null) }

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

    // **The arming frame reads the aim and asks for nothing** (R2). The read is the baseline the ask
    // policy measures against, so a drag of less than the configured ground move — or the very frame
    // the mode opened on — puts no search on the worker at all.
    LaunchedEffect(armed, mapView) {
        if (!armed) {
            aimFlow.value = null
            lastAskedAim = null
            return@LaunchedEffect
        }
        lastAskedAim = readAim()
    }

    // Every map motion pushes the centre; the driver below decides what to do with it.
    LaunchedEffect(motionId, armed) {
        if (!armed) return@LaunchedEffect
        readAim()?.let { aimFlow.value = it }
    }

    // The ask driver: the drag must **stand still for the settle** and have moved the configured
    // ground distance before a search is asked for. Only the aim is passed on — the origin the search
    // uses is the machine's own frozen anchor, told to the engine once.
    LaunchedEffect(armed, mapView) {
        if (!armed) return@LaunchedEffect
        aimFlow.filterNotNull()
            .debounce(AppConfig.routeAskSettleMs)
            .collect { aim ->
                if (routeAimPassed(lastAskedAim, aim)) {
                    lastAskedAim = aim
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

    // ── The refresh cycle's clock (R10) ───────────────────────────────────────
    // One tick a second while a route is followed and not frozen. The boat's position is read through
    // `rememberUpdatedState`, so the tick below sees the **current** fix every time rather than the one
    // captured when this effect started: without it, every automatic refresh would ask from where the
    // boat stood on the frame the following phase opened, and the off-route threshold would measure
    // that same stale point. The gate's own decision is `routeRefreshOrigin`'s, and the machine makes
    // the call the engine may only veto. The freeze stops this clock and nothing else — the trip figure
    // keeps counting down on the standing line.
    val liveBoatPosition by rememberUpdatedState(boatPosition)
    val following = state as? RouteState.Following
    LaunchedEffect(armed, following != null, following?.frozen, mapView) {
        if (!armed || following == null || following.frozen) return@LaunchedEffect
        while (true) {
            delay(1_000L)
            val live = viewModel.state.value as? RouteState.Following ?: break
            if (live.frozen || live.refresh == RouteRefresh.RUNNING) continue
            val from = routeRefreshOrigin(
                livePosition = {
                    liveBoatPosition?.let { RoutePoint(it.latitude, it.longitude) }
                },
                standingPlan = live.plan,
                nowMs = System.currentTimeMillis()
            ) ?: continue
            Log.d(TAG, "refresh gate open — asking from $from")
            viewModel.refresh(from)
        }
    }

    // ── The map objects: one file owns the lines, the pool and the pin ─────────
    // **Attached once, mutated in place** (R14's prerequisite): the pool is created here, added to the
    // map in this effect and removed by its own disposal — never rebuilt on an answer — and every
    // update below writes points, colour and transparency into the objects that are already there.
    val ladderSlots = AppConfig.routeLadderOldestNb + AppConfig.routeLadderLatestNb

    DisposableEffect(mapView) {
        val mv = mapView ?: return@DisposableEffect onDispose { }
        val pool = (0..ladderSlots).map { index ->
            Polyline().apply {
                title = if (index == 0) ROUTE_LINE_TITLE else "${ROUTE_LINE_TITLE}_$index"
                setPoints(emptyList())
                outlinePaint.isAntiAlias = true
            }
        }
        pool.forEach { mv.overlays.add(it) }

        val pin = Marker(mv).apply {
            title = ROUTE_PIN_TITLE
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            // The tap is consumed rather than raising osmdroid's info bubble, which would show this
            // overlay's internal title to the user.
            setOnMarkerClickListener { _, _ -> true }
        }
        mv.overlays.add(pin)

        onDispose {
            mv.overlays.removeAll(pool)
            mv.overlays.remove(pin)
            OverlayZOrder.reorder(mv)
            mv.invalidate()
        }
    }

    LaunchedEffect(mapView, state) {
        val mv = mapView ?: return@LaunchedEffect
        val pool = mv.overlays.filterIsInstance<Polyline>()
            .filter { it.title?.startsWith(ROUTE_LINE_TITLE) == true }
            .sortedBy { it.title }
        val pin = mv.overlays.filterIsInstance<Marker>().firstOrNull { it.title == ROUTE_PIN_TITLE }
            ?: return@LaunchedEffect

        val plan = state.plan
        val stale = when (state) {
            is RouteState.Following -> state.routes.dropLast(1)
            else -> emptyList()
        }
        val drawnLadder = routeLadderForDrawing(stale)

        val frontAlpha = transparencyPctToAlpha(AppConfig.routeLineTransparencyPct)
        val colour = AppConfig.routeLineColor
        val stroke = dpToPx(AppConfig.routeLineWidthDp, mv.paintDensity)

        // Slot 0 is the front route at its own transparency key; the ladder follows it, oldest first, on
        // the book's own **20 % → 80 %** band (R14) — so the line just replaced is the brightest of the
        // stale set and the one that opened the session the faintest. The band is an **absolute**
        // opacity: the two figures the book states, never a fraction of the front line's own
        // transparency, which would land the ladder at 43/255 and 172/255 under the shipped 15 % key.
        val layers: List<Pair<List<RoutePoint>, Int>> = buildList {
            if (plan != null && plan.points.size >= 2) add(plan.points to frontAlpha)
            drawnLadder.forEachIndexed { index, ladderPlan ->
                add(ladderPlan.points to routeLadderDrawAlpha(index, drawnLadder.size))
            }
        }

        pool.forEachIndexed { index, line ->
            val layer = layers.getOrNull(index)
            if (layer == null) {
                line.setPoints(emptyList())
            } else {
                line.setPoints(layer.first.map { GeoPoint(it.latitude, it.longitude) })
                line.outlinePaint.apply {
                    color = AndroidColor.argb(
                        layer.second,
                        AndroidColor.red(colour),
                        AndroidColor.green(colour),
                        AndroidColor.blue(colour)
                    )
                    strokeWidth = stroke
                }
            }
        }

        // The pin is drawn at the **resolved** destination, which is where the route really ends — an
        // aim on land or in another stretch has already moved to the boat's own stretch. One pin serves
        // the front route; a ladder line keeps none of its own.
        if (plan != null && plan.points.size >= 2) {
            val density = mv.paintDensity
            val pinPx = (ROUTE_PIN_SIZE_DP * density).toInt().coerceAtLeast(1)
            val ringPx = (AppConfig.routePinRingWidthDp * density).toInt().coerceAtLeast(1)
            val icon = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(AppConfig.routePinColor)
                setStroke(ringPx, AndroidColor.WHITE)
            }
            icon.setBounds(0, 0, pinPx, pinPx)
            pin.position = GeoPoint(plan.destination.latitude, plan.destination.longitude)
            pin.icon = icon
            pin.isEnabled = true
        } else {
            // An overlay with nothing to draw is hidden by its own flag, never by clearing its geometry:
            // osmdroid's `Marker.setPosition` clones what it is given, so a null position throws on the
            // frame a mode opens with no route yet. `CoastlineMapView` hides its overlays the same way.
            pin.isEnabled = false
        }

        OverlayZOrder.reorder(mv)
        mv.invalidate()
    }

    Box(modifier = modifier) {
        if (armed && state is RouteState.Choosing) {
            // The target is the aim itself — a marker, not a control: the panel carries the details and
            // every outcome, so a tap here would have nothing of its own to raise. A refused end paints
            // the bold red crosshair over it, for the aim and for the origin alike (R6, R27).
            RouteAimTarget(
                refused = state.refusal != null || state.originRefusal != null,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = mapCenterOffsetDp)
            )
        }
    }
}
