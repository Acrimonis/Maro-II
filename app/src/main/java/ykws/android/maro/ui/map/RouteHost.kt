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
import ykws.android.maro.config.PathClass
import ykws.android.maro.config.PathKind
import ykws.android.maro.data.model.RoutePoint
import ykws.android.maro.data.settings.AppSettings
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
 * **The pin is attached once and mutated in place; the lines are rebuilt** (D10). A marker is cheap to
 * keep, so the destination pin is created at this file's composition and moved by each answer; the
 * lines are not, because speed banding yields a stroke count a fixed pool cannot hold, so each pass
 * tears the `route_*` line overlays down and re-adds the painter's own output — the same one painter
 * that draws a stored track ([lineRendering]). The `OverlayZOrder` prefix titles ride on the rebuilt
 * overlays, so the route tier's ordering is unchanged. Slot 0 is the main — the followed line or the
 * selected page — and the candidates follow it, the **selected** one drawn at the plan's own
 * transparency while every other wears the one shared [`AppConfig.routeDimmedTransparencyPct`]
 * (R54, R64).
 *
 * **The main paints at high opacity as its callbacks arrive** — the provisional line is the main
 * lookup's partial line, drawn at [`AppConfig.routeLineTransparencyPct`] while the search runs and
 * hidden the moment the main lands, so a partial line never outlives the search that drew it.
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
 *                   `path.line.color.live` — the same value the toggle's acquiring face wears (R51).
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
    /**
     * The rendering dials the live route reads: the two master chips, the two route gates and the
     * chevron window, so a route bands and chevrons by the very rules a stored track does (S10). One
     * object, so the host does not grow a parameter per dial.
     */
    appSettings: AppSettings,
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

    // ── The map objects: the pin alone is attached once and mutated in place (D10) ─────────
    DisposableEffect(mapView) {
        val mv = mapView ?: return@DisposableEffect onDispose { }
        // The destination pin is the one object kept across paints — a marker, mutated in place. The
        // lines are rebuilt from the painter each pass (D10): speed banding yields a stroke count a
        // fixed pool cannot hold, so the pool gave way to a rebuild while the pin stayed.
        val pin = Marker(mv).apply {
            title = ROUTE_PIN_TITLE
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            setOnMarkerClickListener { _, _ -> true }
        }
        mv.overlays.add(pin)

        Log.d(TAG, "map objects attached — pin=$ROUTE_PIN_TITLE, overlays=${mv.overlays.size}")

        onDispose {
            mv.overlays.removeAll { overlay ->
                (overlay as? Polyline)?.title?.startsWith("route_") == true
            }
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

    // **The remaining-run discriminator, derived once** — whether the remaining run still stands, the one
    // expression the paint key below and the paint's own face ([routeFollowFace]) both read, so the
    // flip to arrival moves the key and repaints while the two readers can never disagree.
    val remainingStands = routeRemainingStands(split)

    // The paint's own key: the **remaining-run discriminator** — whether the remaining run still stands — the
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

    // The colour and the rendering dials ride in the keys (R51's drift): `Following` never re-emits
    // while the boat moves, so a Settings edit recomposes this host without re-running the paint
    // unless they are keys of their own — and [splitKey] is the split's own key, so a stationary boat
    // is not repainted.
    LaunchedEffect(
        mapView, state, routeLineColor, pages, selectedIndex, discardPending, splitKey,
        provisionalLine, appSettings
    ) {
        val mv = mapView ?: return@LaunchedEffect
        val density = mv.paintDensity

        // Every pass starts from a clean route tier: the previous pass's lines go, the pin stays (D10).
        mv.overlays.removeAll { overlay ->
            (overlay as? Polyline)?.title?.startsWith("route_") == true
        }
        val pin = mv.overlays.filterIsInstance<Marker>().firstOrNull { it.title == ROUTE_PIN_TITLE }
        if (pin == null) {
            Log.d(TAG, "no pin on the map — the lines draw without the destination dot")
        }

        // **Phase 1 of the deferred discard**: the display reads a completed discard — the lines and
        // the pin leave — while the session stays untouched, so Undo repaints with nothing recomputed.
        if (discardPending) {
            pin?.isEnabled = false
            OverlayZOrder.reorder(mv)
            mv.invalidate()
            return@LaunchedEffect
        }

        // A followed route holds its plan in the state, not in the page set: `selectRoute()` and
        // `followSavedRoute` both empty the pages, so slot 0 and the pin read the state to stay drawn.
        val followed = (state as? RouteState.Following)?.plan

        // **The followed line's face, in one pure read** (R93): slot 0's own run, the points the casing
        // under-strokes, and the travelled run. While the remaining run stands ([remainingStands], the
        // same expression the paint key carries) slot 0 holds the run ahead alone, so the covered run is
        // painted by `route_travelled` and never twice beneath it. **At arrival fewer than two points
        // remaining is zero points** — an empty run — so slot 0 and its casing stand down and the whole-
        // line fallback is gone; the travelled run then carries the covered line. The face keeps the
        // arrival shape a pure value, out of this effect's own branches.
        val face = if (followed != null && split != null) routeFollowFace(followed, split) else null

        val colour = routeLineColor
        val ramp = AppConfig.trackHeatmapRamp
        val spacing = directionSpacingProvider(density, appSettings)
        val selectedAlpha = transparencyPctToAlpha(AppConfig.routeLineTransparencyPct)
        val dimmedAlpha = transparencyPctToAlpha(AppConfig.routeDimmedTransparencyPct)
        // The painter takes a fade fraction, the paint an alpha byte: one conversion, both readers.
        val selectedFade = selectedAlpha / 255f
        val dimmedFade = dimmedAlpha / 255f
        val casingColour = reinforcedColor(colour, AppConfig.uiReinforceDarkenPct)

        fun argb(alpha: Int, c: Int): Int =
            AndroidColor.argb(alpha, AndroidColor.red(c), AndroidColor.green(c), AndroidColor.blue(c))

        // The route tier, bottom to top: the derived edge, the travelled run, the pool, the
        // provisional line — the very order the attach-once objects were added in (R93).
        val built = mutableListOf<org.osmdroid.views.overlay.Overlay>()

        // **The edge** (R93): the selected line's derived under-stroke, dashed with it, mirroring
        // exactly what the selected core draws. Painted through the one painter, as a plain ROUTE line.
        val selectedPlan = followed ?: pages.getOrNull(selectedIndex)?.plan
        val casingPoints = if (followed != null) face?.casingPoints else selectedPlan?.points
        // The live line's own casing over the shared one (2026-10-07): the followed line takes the
        // live class leaf, the acquisition rung keeps the shared width.
        val casingWidth =
            if (followed != null) AppConfig.routeLineCasingLiveWidthDp else AppConfig.routeLineCasingWidthDp
        if (casingPoints != null && casingPoints.size >= 2) {
            built += lineRendering(
                spec = LineRenderSpec(
                    PathKind.ROUTE, PathClass.CASING, casingPoints.toRenderPoints(), dashed = true
                ),
                title = ROUTE_CASING_TITLE,
                plan = LineRenderPlan(LineRenderPath.ROUTE, drawArrows = false, selected = false, dashed = true),
                ramp = ramp,
                strokeWidth = casingWidth,
                density = density,
                fade = selectedFade,
                plainAppearance = {
                    TrackPolylineAppearance(argb(selectedAlpha, casingColour), casingWidth)
                }
            ).overlays
        }

        // **The travelled run** (R93): the covered run behind the boat wears the shared dimming, solid.
        // Its guard is relaxed from *only beside the remaining run* to *whenever it is a real run*,
        // because slot 0 never draws the whole line any more to be doubled — at arrival it carries the
        // covered line alone. A run whose endpoints coincide is degenerate and stays off.
        val travelledPoints = face?.travelledPoints.orEmpty()
        if (travelledPoints.isNotEmpty()) {
            built += lineRendering(
                spec = LineRenderSpec(
                    PathKind.ROUTE, PathClass.DIMMED, travelledPoints.toRenderPoints(), dashed = false
                ),
                title = ROUTE_TRAVELLED_TITLE,
                plan = LineRenderPlan(LineRenderPath.ROUTE, drawArrows = false, selected = false, dashed = false),
                ramp = ramp,
                strokeWidth = AppConfig.routeLineWidthDp,
                density = density,
                fade = dimmedFade,
                plainAppearance = {
                    TrackPolylineAppearance(argb(dimmedAlpha, colour), AppConfig.routeLineWidthDp)
                }
            ).overlays
        }

        // **The pool**: slot 0 the main — the followed line or the selected page — and the candidates
        // behind it, dimmed. The followed/selected line is the live route, so it bands and chevrons by
        // the route's own gates joined to the master chips (S10); a candidate is a plain dimmed line.
        for (index in 0 until ROUTE_LADDER_RUNG_COUNT) {
            val plan = if (followed != null) {
                if (index == 0) followed else null
            } else {
                pages.getOrNull(index)?.plan
            }
            if (plan == null || plan.points.size < 2) continue
            // **At arrival slot 0 carries no points** (R93): fewer than two remaining is an empty run,
            // so the rung stands down whole — no core, no casing, no chevrons — and only the travelled
            // run and the pin remain on the map.
            if (followed != null && index == 0 && face?.slotZeroPoints.isNullOrEmpty()) continue
            val lives = followed != null || index == selectedIndex
            val alpha = if (lives) selectedAlpha else dimmedAlpha
            val title = if (index == 0) ROUTE_LINE_TITLE else "${ROUTE_LINE_TITLE}_$index"
            val specPoints = if (lives && followed != null) {
                face?.slotZeroPoints.orEmpty()
            } else {
                toRenderPoints(plan.points, plan.legTimesSec)
            }
            // The rung under the selection wears the **acquisition** class while the mode is still
            // choosing and **live** once the route is followed (2026-10-07): the class leaf
            // (`path.arrow.enabled.acquisition` / `path.heatmap.enabled.acquisition`) silences the
            // search's chevrons and bands through the ordinary cascade, while the followed line stays on
            // the route kind's own axes. A candidate behind the selection stays dimmed and arrowless.
            val lineClass = if (followed != null) PathClass.LIVE else PathClass.ACQUISITION
            val routeArrows = AppConfig.pathArrowEnabled(PathKind.ROUTE, lineClass, appSettings.routeSpeedArrows)
            val routeColours = AppConfig.pathHeatmapEnabled(PathKind.ROUTE, lineClass, appSettings.routeSpeedColor)
            val rendering = lineRendering(
                spec = LineRenderSpec(
                    kind = PathKind.ROUTE,
                    pathClass = if (lives) lineClass else PathClass.DIMMED,
                    points = specPoints,
                    dashed = true,
                    drawArrows = lives && routeArrows
                ),
                title = title,
                plan = if (lives) {
                    routeLineRenderPlan(
                        routeArrows = routeArrows,
                        routeColours = routeColours,
                        selected = false
                    )
                } else {
                    LineRenderPlan(LineRenderPath.ROUTE, drawArrows = false, selected = false, dashed = true)
                },
                ramp = ramp,
                strokeWidth = AppConfig.routeLineWidthDp,
                density = density,
                fade = alpha / 255f,
                plainAppearance = {
                    TrackPolylineAppearance(argb(alpha, colour), AppConfig.routeLineWidthDp)
                }
            )
            built += rendering.overlays
            if (rendering.drawArrows) {
                built += rendering.directionOverlay(
                    specPoints,
                    spacing,
                    "${ROUTE_LINE_TITLE}_arrow",
                    density
                )
            }
        }

        // **The provisional line**: the main lookup's partial line, drawn at the main's opacity and
        // hidden the moment the main lands — a partial never outlives the search that drew it.
        if (provisionalLine.size >= 2) {
            built += lineRendering(
                spec = LineRenderSpec(
                    PathKind.ROUTE, PathClass.LIVE, provisionalLine.toRenderPoints(), dashed = true
                ),
                title = ROUTE_PROGRESS_TITLE,
                plan = LineRenderPlan(LineRenderPath.ROUTE, drawArrows = false, selected = false, dashed = true),
                ramp = ramp,
                strokeWidth = AppConfig.routeLineWidthDp,
                density = density,
                fade = selectedFade,
                plainAppearance = {
                    TrackPolylineAppearance(argb(selectedAlpha, colour), AppConfig.routeLineWidthDp)
                }
            ).overlays
        }

        mv.overlays.addAll(built)

        // The pin marks the selected line's resolved destination — the followed route's while
        // navigating, the selected page's during the acquisition.
        if (selectedPlan != null && selectedPlan.points.size >= 2) {
            val pinPx = (ROUTE_PIN_SIZE_DP * density).toInt().coerceAtLeast(1)
            val ringPx = (AppConfig.routePinRingWidthDp * density).toInt().coerceAtLeast(1)
            val icon = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(AppConfig.routePinColor)
                setStroke(ringPx, AppConfig.routePinRingColour)
            }
            icon.setBounds(0, 0, pinPx, pinPx)
            pin?.let {
                it.position = GeoPoint(selectedPlan.destination.latitude, selectedPlan.destination.longitude)
                it.icon = icon
                it.isEnabled = true
            }
        } else {
            pin?.isEnabled = false
        }

        Log.d(
            TAG,
            "line repaint — phase=${state.phase}, points=${selectedPlan?.points?.size ?: 0}, " +
                "lines=${built.size}, selected=$selectedIndex, " +
                "colour=${Integer.toHexString(colour)}, " +
                "selectedAlpha=$selectedAlpha, dimmedAlpha=$dimmedAlpha, " +
                "pinEnabled=${pin?.isEnabled == true}"
        )

        OverlayZOrder.reorder(mv)
        mv.invalidate()
    }
}

/**
 * **Whether the remaining run still stands** (R93) — the remaining-run discriminator: fewer than two points
 * remaining is an empty run, not a whole line. The paint key and the paint's own face
 * ([routeFollowFace]) both read this one expression, so the flip to arrival cannot go unnoticed by one
 * while the other draws by it.
 */
internal fun routeRemainingStands(split: RouteSplit?): Boolean =
    split != null && split.remainingPoints.size >= 2

/**
 * **The followed line's paint face** (R93) — what the host draws for one followed plan at one split:
 * the points slot 0 carries, the points the casing under-strokes (`null` where no casing is built), and
 * the travelled run (`null` where it stays off).
 *
 * **At arrival the run is empty and the covered line alone is drawn.** Fewer than two points remaining
 * is **zero points** — nothing ahead — so [slotZeroPoints] is empty and [casingPoints] is `null`, and
 * the whole-line fallback is gone; [travelledPoints] then carries the whole covered line, drawing
 * whenever it is a real run because nothing ahead is left to be doubled.
 */
internal data class RouteFollowFace(
    val slotZeroPoints: List<RenderPoint>,
    val casingPoints: List<RoutePoint>?,
    val travelledPoints: List<RoutePoint>?
)

/**
 * **The face of a followed plan at [split]** — the one reading the host paints from, so the arrival
 * shape is a pure value rather than an inline branch: while the remaining run stands, slot 0 carries
 * the run ahead ([remainingRenderPoints]) and the casing under-strokes it; where it does not, slot 0
 * and the casing stand down together. The travelled run draws whenever it is a real run — two or more
 * points not coincident at their ends — since slot 0 no longer ever draws the whole line to be doubled.
 */
internal fun routeFollowFace(plan: RoutePlan, split: RouteSplit): RouteFollowFace {
    val stands = routeRemainingStands(split)
    val travelled = split.travelledPoints
    val travelledDraws = travelled.size >= 2 &&
        (travelled.first().latitude != travelled.last().latitude ||
            travelled.first().longitude != travelled.last().longitude)
    return RouteFollowFace(
        slotZeroPoints = if (stands) remainingRenderPoints(plan, split) else emptyList(),
        casingPoints = split.remainingPoints.takeIf { stands },
        travelledPoints = travelled.takeIf { travelledDraws }
    )
}

/**
 * A followed plan's **remaining run as the seam's points**, its per-point speed derived from the
 * plan's own leg times (S10): the projected split point carries the leg it sits on, and every vertex
 * behind it keeps its own leg's speed, so a chevron and a speed band on the live route read the
 * plan's pace rather than the ramp's neutral tint. A plan under two points, or one whose split landed
 * on a vertex, falls out of the same walk.
 */
private fun remainingRenderPoints(plan: RoutePlan, split: RouteSplit): List<RenderPoint> {
    val planPoints = toRenderPoints(plan.points, plan.legTimesSec)
    val best = split.bestLegIndex
    if (best < 0) return planPoints
    val remaining = split.remainingPoints
    if (remaining.isEmpty()) return emptyList()
    // The split point coincides with the leg's own end once the boat is at or past it; the remaining
    // run then starts at that vertex and the vertex's own speed is the plan's, not the leg's.
    val nextVertex = plan.points.getOrNull(best + 1)
    val firstIsVertex = nextVertex != null &&
        remaining.first().latitude == nextVertex.latitude &&
        remaining.first().longitude == nextVertex.longitude
    val suffix = planPoints.drop(best + 1)
    return if (firstIsVertex) {
        suffix
    } else {
        val splitSpeed = planPoints.getOrNull(best)?.speedKn
        listOf(RenderPoint(remaining.first().latitude, remaining.first().longitude, speedKn = splitSpeed)) + suffix
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// (The aim ring and its refused crosshair were retired with the seam rework of
// 2026-09-29: the repair moves an invalid end to water instead of painting it red.)
// ─────────────────────────────────────────────────────────────────────────────
