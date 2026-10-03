package ykws.android.maro.ui.map

import android.graphics.drawable.GradientDrawable
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlin.math.roundToInt
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.ui.color.reinforcedColor
import android.graphics.Color as AndroidColor

/** Log tag for the route mode's own host — its two edges, kept for the device pass. */
private const val TAG = "MaroRoute"

/**
 * The route lines' own overlay title prefix. `OverlayZOrder` reads it to keep every line inside the
 * **track band**, above the tracks and below the markers — the epic's stated order, enforced by the
 * existing re-order rather than by a second rule.
 */
internal const val ROUTE_LINE_TITLE = "route_line"

/** The provisional line's own overlay title — the `route_` prefix keeps it in the track band, above the pool. */
internal const val ROUTE_PROGRESS_TITLE = "route_progress"

/**
 * **The selected line's derived edge** — the under-stroke painted beneath it in both phases (R93).
 * The `route_` prefix keeps it in the route tier, and it is attached **before** the pool so the rungs
 * paint over it; it does not match the pool's `startsWith(ROUTE_LINE_TITLE)` filter.
 */
internal const val ROUTE_CASING_TITLE = "route_casing"

/**
 * **The run behind the boat** while a route is followed, wearing the shared dimming key (R93). The
 * `route_` prefix keeps it in the route tier; it too sits before the pool.
 */
internal const val ROUTE_TRAVELLED_TITLE = "route_travelled"

/**
 * The step the split identity rounds the projected point to (degrees): ≈ 11 m of latitude and ≈ 8 m of
 * longitude at this latitude — fine enough that the fade visibly tracks the boat, coarse enough that a
 * moored boat's jitter does not repaint the line.
 */
private const val SPLIT_IDENTITY_STEP_DEG = 1e-4

/**
 * The destination pin's title. The `marker_` prefix is the marker band's own, so the pin is drawn
 * above the lines and the tracks, beside the other map markers.
 */
internal const val ROUTE_PIN_TITLE = "marker_route_dest"

/** Side (dp) of the destination dot drawn at the resolved end of the route. */
private const val ROUTE_PIN_SIZE_DP = 18f

/**
 * The route's map half: the lines, the pin and the provisional line — and nothing else.
 *
 * This is **the one file that touches osmdroid for the Route feature**, so ordering, the pin's slot
 * and the provisional line all have a single home.
 *
 * **The objects are attached once and mutated in place** (R65 keeps that pool). The polyline pool and
 * the pin are created at this file's own composition and added to the map a single time; an answer then
 * sets their points, their colour and their transparency rather than rebuilding the set — which is what
 * a **page** draws into. The pool has one slot per line the acquisition can draw: the main, then one
 * per candidate pass `maro.properties` declares. Slot 0 is the main and the candidates follow it, and
 * the **selected** one is drawn at the plan's own transparency while every other wears the one shared
 * [`AppConfig.routeDimmedTransparencyPct`] (R54, R64).
 *
 * **The main paints at high opacity as its callbacks arrive** — the provisional line is the main
 * lookup's partial line, drawn in its own overlay at [`AppConfig.routeLineTransparencyPct`] while the
 * search runs, and hidden the moment the main lands, so a partial line never outlives the search that
 * drew it.
 *
 * **The chosen line is reinforced by shape as well as opacity** (R93): a derived under-stroke — the
 * line's own colour pushed the reinforcement lever's distance — mirrors the selected line in both
 * phases, and while `Following` the line splits at the boat, the run behind wearing the shared dimming
 * key and the run ahead staying at full strength under the edge.
 *
 * The panel is **not** here: it lives in the dashboard slot, composed by the shell from the same state.
 * This host therefore raises nothing, dismisses nothing and owns no dialog.
 *
 * **No timer asks for anything** (R2), and since 2026-09-28 the **arming is not this file's either**:
 * the toggle and the drawer's Route section resolve the standing pair and call `RouteViewModel.arm`, so
 * what this host reads of the boat is the pace window alone.
 *
 * **The back key is the mode's own escape** while it is armed, and it is routed through [onEndRoute]
 * into the shell's one exit rule, so the back key cannot diverge from the toggle's own door (R60).
 *
 * @param armed      the mode's single switch, owned by the shell: on arms, off ends the route.
 * @param discardPending the deferred discard's window is open: the lines and the pin leave at once
 *                   (phase 1), while the engine keeps running underneath until the toast confirms.
 * @param pages      the pages the acquisition draws, the main first — one per started lookup.
 * @param selectedIndex which of [pages] the selection stands on: the one at full strength and under the pin.
 * @param provisionalLine the main lookup's partial line, drawn while the search runs.
 * @param speedKn    speed over ground (kn), fed to the pace window; null when nothing is moving.
 * @param positionRestricted true when the current position sits in a regulated zone or the band, so
 *                   the reading measures the limit rather than the boat and is dropped by [RoutePace].
 * @param routeLineColor the followed line's own colour, read from Settings and seeded by
 *                   `route.line.color` — the same value the toggle's acquiring face wears (R51).
 * @param boatPosition the boat's own fix — the shell's `routeStart`, the value the trip cell already
 *                   reads — at which the followed line is split for the paint.
 * @param onEndRoute runs when the mode ends — the toggle's off and the back key.
 */
@Composable
internal fun RouteHost(
    mapView: MapView?,
    state: RouteState,
    pages: List<RoutePage>,
    selectedIndex: Int,
    provisionalLine: List<RoutePoint>,
    armed: Boolean,
    discardPending: Boolean,
    gpsMode: Boolean,
    speedKn: Float?,
    positionRestricted: Boolean,
    setPaceKn: Float,
    routeLineColor: Int,
    boatPosition: RoutePoint,
    viewModel: RouteViewModel,
    onEndRoute: () -> Unit
) {
    // The back key is the mode's own escape while it is armed: the panel sits in the dashboard slot,
    // so nothing else offers one — and without this the press would reach the screen's exit guard with
    // a route still standing on the map.
    BackHandler(enabled = armed) { onEndRoute() }

    // ── The mode's ending, and nothing else: the arming is the screen's own act (R49, R71) ──
    LaunchedEffect(armed) {
        if (armed) {
            Log.d(TAG, "mode armed — the ends were read by the screen")
        } else {
            Log.d(TAG, "mode ended")
            viewModel.end()
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

    // ── The map objects: one file owns the lines, the pool and the pin ─────────
    // One polyline per ladder rung — the retired candidate passes no longer size the pool.
    val poolSlots = ROUTE_LADDER_RUNG_COUNT

    DisposableEffect(mapView) {
        val mv = mapView ?: return@DisposableEffect onDispose { }
        // The derived edge and the travelled run are attached **before** the pool, so `reorder`'s
        // stable sort keeps them under the rungs while still inside the route tier (R93).
        val casing = Polyline().apply {
            title = ROUTE_CASING_TITLE
            setPoints(emptyList())
            outlinePaint.isAntiAlias = true
        }
        mv.overlays.add(casing)
        val travelledLine = Polyline().apply {
            title = ROUTE_TRAVELLED_TITLE
            setPoints(emptyList())
            outlinePaint.isAntiAlias = true
        }
        mv.overlays.add(travelledLine)

        val pool = (0 until poolSlots).map { index ->
            Polyline().apply {
                title = if (index == 0) ROUTE_LINE_TITLE else "${ROUTE_LINE_TITLE}_$index"
                setPoints(emptyList())
                outlinePaint.isAntiAlias = true
            }
        }
        pool.forEach { mv.overlays.add(it) }

        // The provisional line — the main lookup's partial line — added after the pool and before the
        // pin, so it paints over the standing lines and stays under every marker.
        val progressLine = Polyline().apply {
            title = ROUTE_PROGRESS_TITLE
            setPoints(emptyList())
            outlinePaint.isAntiAlias = true
        }
        mv.overlays.add(progressLine)

        val pin = Marker(mv).apply {
            title = ROUTE_PIN_TITLE
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            setOnMarkerClickListener { _, _ -> true }
        }
        mv.overlays.add(pin)

        Log.d(
            TAG,
            "map objects attached — pool=${pool.size} lines, pin=$ROUTE_PIN_TITLE, " +
                "overlays=${mv.overlays.size}"
        )

        onDispose {
            mv.overlays.remove(casing)
            mv.overlays.remove(travelledLine)
            mv.overlays.removeAll(pool)
            mv.overlays.remove(progressLine)
            mv.overlays.remove(pin)
            OverlayZOrder.reorder(mv)
            mv.invalidate()
        }
    }

    // **The split identity** — the followed plan's projection, remembered against the boat's fix
    // rounded to [SPLIT_IDENTITY_STEP_DEG], so the O(legs) projection is not re-derived on every
    // composition and a moored boat's jitter re-derives nothing.
    val followedPlan = (state as? RouteState.Following)?.plan
    val splitIdentity = if (followedPlan != null) {
        "${(boatPosition.latitude / SPLIT_IDENTITY_STEP_DEG).roundToInt()}:" +
            "${(boatPosition.longitude / SPLIT_IDENTITY_STEP_DEG).roundToInt()}"
    } else {
        ""
    }
    val split = remember(followedPlan, splitIdentity) { followedPlan?.splitAt(boatPosition) }

    // **The arrival threshold, derived once** — the one expression of the rule that the remaining run
    // still stands, read by both the paint key's arrival discriminator below and the choice between the
    // remaining run and the whole line, so the two readers can never disagree.
    val remainingStands = split != null && split.remainingPoints.size >= 2

    // The paint's own key: the **arrival discriminator** — whether the remaining run still stands — the
    // best leg the projection landed on (read from [RouteSplit] rather than reconstructed from the run's
    // size), and the projected point rounded to the same step. The flag is what makes the effect re-run
    // at the flip to arrival: by then the projected point has already clamped to the destination, so the
    // leg and the rounded point are unchanged and the flag is the only thing that moves. The paint keys
    // on this rather than on the raw fix, so a stationary boat does not repaint while one that has moved
    // past the step does — and inside a bucket only the flag can change, so an ordinary fix never does.
    val splitKey = split?.travelledPoints?.lastOrNull()?.let { at ->
        "$remainingStands:${split.bestLegIndex}:" +
            "${(at.latitude / SPLIT_IDENTITY_STEP_DEG).roundToInt()}:" +
            "${(at.longitude / SPLIT_IDENTITY_STEP_DEG).roundToInt()}"
    } ?: ""

    // The colour rides in the keys (R51's drift): `Following` never re-emits while the boat moves, so
    // a Settings edit recomposes this host without re-running the paint unless the colour is a key of
    // its own — and [splitKey] is the split's own key, so a stationary boat is not repainted.
    LaunchedEffect(mapView, state, routeLineColor, pages, selectedIndex, discardPending, splitKey) {
        val mv = mapView ?: return@LaunchedEffect
        val pool = mv.overlays.filterIsInstance<Polyline>()
            .filter { it.title?.startsWith(ROUTE_LINE_TITLE) == true }
            .sortedBy { it.title }
        val pin = mv.overlays.filterIsInstance<Marker>().firstOrNull { it.title == ROUTE_PIN_TITLE }
        val casing = mv.overlays.filterIsInstance<Polyline>()
            .firstOrNull { it.title == ROUTE_CASING_TITLE }
        val travelledLine = mv.overlays.filterIsInstance<Polyline>()
            .firstOrNull { it.title == ROUTE_TRAVELLED_TITLE }
        if (pin == null) {
            Log.d(
                TAG,
                "no pin on the map — the lines draw without the destination dot: " +
                    "overlays=${mv.overlays.size}, pool=${pool.size}"
            )
        }

        // **Phase 1 of the deferred discard**: the display reads a completed discard — the lines and
        // the pin leave — while the session stays untouched, so Undo repaints with nothing recomputed.
        if (discardPending) {
            pool.forEach { it.setPoints(emptyList()) }
            pin?.isEnabled = false
            mv.overlays.filterIsInstance<Polyline>()
                .firstOrNull { it.title == ROUTE_PROGRESS_TITLE }
                ?.setPoints(emptyList())
            // The derived edge and the travelled run leave with the lines (R93, R92 phase 1).
            casing?.isEnabled = false
            casing?.setPoints(emptyList())
            travelledLine?.isEnabled = false
            travelledLine?.setPoints(emptyList())
            OverlayZOrder.reorder(mv)
            mv.invalidate()
            return@LaunchedEffect
        }

        // A followed route holds its plan in the state, not in the page set: `selectRoute()` and
        // `followSavedRoute` both empty the pages, so slot 0 and the pin read the state to stay drawn.
        val followed = (state as? RouteState.Following)?.plan

        // **While following, slot 0 carries the split's remaining run alone** while [remainingStands],
        // so the covered run is painted by `route_travelled` and never twice beneath it; where the
        // remaining run falls under two points — the arrival rule — slot 0 keeps the **whole** line at
        // full strength and the travelled overlay stays off. The casing mirrors slot 0's own points, and
        // the two-point test is the same [remainingStands] the paint key carries.
        val remainingRun = if (followed != null && remainingStands) {
            split.remainingPoints
        } else {
            null
        }
        val followedPoints = when {
            followed == null -> null
            remainingRun != null -> remainingRun
            else -> followed.points
        }

        val colour = routeLineColor
        val stroke = dpToPx(AppConfig.routeLineWidthDp, mv.paintDensity)
        val casingStroke = dpToPx(AppConfig.routeLineCasingWidthDp, mv.paintDensity)
        val casingColour = reinforcedColor(colour, AppConfig.uiReinforceDarkenPct)
        val selectedAlpha = transparencyPctToAlpha(AppConfig.routeLineTransparencyPct)
        val dimmedAlpha = transparencyPctToAlpha(AppConfig.routeDimmedTransparencyPct)

        pool.forEachIndexed { index, line ->
            val points = if (followed != null) {
                if (index == 0) followedPoints else emptyList()
            } else {
                pages.getOrNull(index)?.plan?.points
            }
            if (points == null || points.size < 2) {
                line.setPoints(emptyList())
                line.isEnabled = false
            } else {
                val alpha = if (followed != null || index == selectedIndex) selectedAlpha else dimmedAlpha
                line.setPoints(points.map { GeoPoint(it.latitude, it.longitude) })
                line.outlinePaint.apply {
                    color = AndroidColor.argb(
                        alpha,
                        AndroidColor.red(colour),
                        AndroidColor.green(colour),
                        AndroidColor.blue(colour)
                    )
                    strokeWidth = stroke
                }
                line.isEnabled = true
            }
        }

        // The pin marks the selected line's resolved destination — the followed route's while
        // navigating, the selected page's during the acquisition.
        val selected = followed ?: pages.getOrNull(selectedIndex)?.plan
        if (selected != null && selected.points.size >= 2) {
            val density = mv.paintDensity
            val pinPx = (ROUTE_PIN_SIZE_DP * density).toInt().coerceAtLeast(1)
            val ringPx = (AppConfig.routePinRingWidthDp * density).toInt().coerceAtLeast(1)
            val icon = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(AppConfig.routePinColor)
                setStroke(ringPx, AndroidColor.WHITE)
            }
            icon.setBounds(0, 0, pinPx, pinPx)
            pin?.let {
                it.position = GeoPoint(selected.destination.latitude, selected.destination.longitude)
                it.icon = icon
                it.isEnabled = true
            }
        } else {
            pin?.isEnabled = false
        }

        // **The edge** — the selected line's derived under-stroke, drawn in both phases (R93). It takes
        // the line's own transparency and the derived colour, and mirrors **exactly what the selected
        // core draws** — the split's remaining run while following, the selected page's own line
        // otherwise — so the rim reads 1 dp each side of the core and never outruns it.
        val casingPoints = if (followed != null) followedPoints else selected?.points
        if (casingPoints != null && casingPoints.size >= 2) {
            casing?.apply {
                setPoints(casingPoints.map { GeoPoint(it.latitude, it.longitude) })
                outlinePaint.apply {
                    color = AndroidColor.argb(
                        selectedAlpha,
                        AndroidColor.red(casingColour),
                        AndroidColor.green(casingColour),
                        AndroidColor.blue(casingColour)
                    )
                    strokeWidth = casingStroke
                }
                isEnabled = true
            }
        } else {
            casing?.isEnabled = false
            casing?.setPoints(emptyList())
        }

        // **The travelled run** — the covered run behind the boat wears the shared dimming key (R93),
        // and it draws **only when slot 0 actually carries the remaining run**: where the whole line
        // stays at full strength (the arrival rule), the overlay stays off so it never doubles the core.
        // A run whose endpoints coincide — the start of a followed route, the projection still on the
        // first point — is degenerate, so it too stays off and draws nothing.
        val travelledPoints = split?.travelledPoints.orEmpty()
        val travelledDraws = travelledPoints.size >= 2 && (
            travelledPoints.first().latitude != travelledPoints.last().latitude ||
                travelledPoints.first().longitude != travelledPoints.last().longitude
            )
        if (remainingRun != null && travelledDraws) {
            travelledLine?.apply {
                setPoints(travelledPoints.map { GeoPoint(it.latitude, it.longitude) })
                outlinePaint.apply {
                    color = AndroidColor.argb(
                        dimmedAlpha,
                        AndroidColor.red(colour),
                        AndroidColor.green(colour),
                        AndroidColor.blue(colour)
                    )
                    strokeWidth = stroke
                }
                isEnabled = true
            }
        } else {
            travelledLine?.isEnabled = false
            travelledLine?.setPoints(emptyList())
        }

        val firstLine = pool.firstOrNull()
        Log.d(
            TAG,
            "line repaint — phase=${state.phase}, points=${selected?.points?.size ?: 0}, " +
                "pool=${pool.size} attached=${pool.count { line -> mv.overlays.contains(line) }} " +
                "firstLineAlpha=${firstLine?.outlinePaint?.alpha ?: -1}, " +
                "lines=${pages.size}, selected=$selectedIndex, stroke=${stroke}px, " +
                "colour=${Integer.toHexString(colour)}, selectedAlpha=$selectedAlpha, " +
                "dimmedAlpha=$dimmedAlpha, pinEnabled=${pin?.isEnabled == true}"
        )

        OverlayZOrder.reorder(mv)
        mv.invalidate()
    }

    // ── The provisional line: the main lookup's partial line, drawn as it is built ──
    // A line the pipeline has not finished is not a page: it draws into its own overlay at the line's
    // own colour and width at the **main's** opacity, and it is hidden the moment the main lands — on
    // the answer and on a refusal alike — so a partial line never outlives the search that drew it.
    LaunchedEffect(mapView, provisionalLine, routeLineColor, discardPending) {
        val mv = mapView ?: return@LaunchedEffect
        val line = mv.overlays.filterIsInstance<Polyline>()
            .firstOrNull { it.title == ROUTE_PROGRESS_TITLE } ?: return@LaunchedEffect
        if (discardPending || provisionalLine.size < 2) {
            line.isEnabled = false
            line.setPoints(emptyList())
        } else {
            line.setPoints(provisionalLine.map { GeoPoint(it.latitude, it.longitude) })
            line.outlinePaint.apply {
                color = AndroidColor.argb(
                    transparencyPctToAlpha(AppConfig.routeLineTransparencyPct),
                    AndroidColor.red(routeLineColor),
                    AndroidColor.green(routeLineColor),
                    AndroidColor.blue(routeLineColor)
                )
                strokeWidth = dpToPx(AppConfig.routeLineWidthDp, mv.paintDensity)
            }
            line.isEnabled = true
        }
        mv.invalidate()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// (The aim ring and its refused crosshair were retired with the seam rework of
// 2026-09-29: the repair moves an invalid end to water instead of painting it red.)
// ─────────────────────────────────────────────────────────────────────────────
