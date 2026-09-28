package ykws.android.maro.ui.map

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import kotlin.math.roundToInt
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.LatLng
import ykws.android.maro.spatial.RouteProgress
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
 * The destination pin's title. The `marker_` prefix is the marker band's own, so the pin is drawn
 * above the lines and the tracks, beside the other map markers.
 */
internal const val ROUTE_PIN_TITLE = "marker_route_dest"

/** Side (dp) of the destination dot drawn at the resolved end of the route. */
private const val ROUTE_PIN_SIZE_DP = 18f

/**
 * The aim ring's own overlay title. The `route_` prefix is the **track band's** own, so `OverlayZOrder`
 * places the ring above the tracks and the route lines and **below every marker, the boat included** —
 * the band is what lets the boat paint over the ring, and `OverlayZOrder` itself is unchanged.
 */
internal const val ROUTE_TARGET_TITLE = "route_target"

/** Radius (dp) of the aim ring's outer circle. */
internal const val ROUTE_TARGET_RADIUS_DP = 22f

/** Radius (dp) of the aim ring's inner mark — the exact point the aim resolves to. */
internal const val ROUTE_TARGET_INNER_RADIUS_DP = 3f

/** Stroke (dp) of the aim ring's outer circle. */
internal const val ROUTE_TARGET_STROKE_DP = 2f

/** The repaint step the refused crosshair's beat rides while it is on screen (ms). */
private const val ROUTE_TARGET_PULSE_FRAME_MS = 33L

/**
 * The route's map half: the lines, the pin and the screen-centred aim — and nothing else.
 *
 * This is **the one file that touches osmdroid for the Route feature**, so ordering, the pin's slot
 * and the aim's own reading of the map centre all have a single home.
 *
 * **The objects are attached once and mutated in place** (R65 keeps that pool). The polyline pool and
 * the pin are created at this file's own composition and added to the map a single time; an answer then
 * sets their points, their colour and their transparency rather than rebuilding the set — which is what
 * a **candidate line** draws into, and the shape the contour polylines already ship.
 *
 * The pool has one slot per line the acquisition can draw: the settled answer, then one per candidate
 * pass `maro.properties` declares. Slot 0 is the settled line and the candidates follow it, and the
 * **selected** one is drawn at the plan's own transparency while every other wears the one shared
 * [`AppConfig.routeDimmedTransparencyPct`] (R54, R64) — the same key the line a search is still building
 * paints at, so the two are told apart by motion and replacement rather than by paleness.
 *
 * The panel is **not** here: it lives in the dashboard slot, composed by the shell from the same state,
 * so the panel the outcomes are taken from cannot fight the map the ends are read from. This host
 * therefore raises nothing, dismisses nothing and owns no dialog.
 *
 * **No timer asks for anything** (R2), and since 2026-09-28 the **arming is not this file's either**:
 * the toggle and the drawer's Route section resolve the standing pair and call `RouteViewModel.arm`, so
 * what this host reads of the boat is the pace window alone — the anchor is the screen's own reading at
 * the trigger (R71).
 *
 * **The back key is the mode's own escape** while it is armed, and it is routed through [onEndRoute]
 * into the shell's one exit rule, so the back key cannot diverge from the toggle's own door (R60).
 *
 * @param armed      the mode's single switch, owned by the shell: on arms, off ends the route.
 * @param speedKn    speed over ground (kn), fed to the pace window; null when nothing is moving.
 * @param positionRestricted true when the current position sits in a regulated zone or the band, so
 *                   the reading measures the limit rather than the boat and is dropped by [RoutePace].
 * @param onEndRoute runs when the mode ends — the toggle's off and the back key.
 */
@Composable
internal fun RouteHost(
    mapView: MapView?,
    state: RouteState,
    progress: RouteProgress?,
    /** The lines the acquisition draws, the settled answer first — [`routeCandidateLines`]. */
    candidates: List<RoutePlan>,
    /** Which of [candidates] the selection stands on: the one at full strength and under the pin. */
    selectedIndex: Int,
    armed: Boolean,
    gpsMode: Boolean,
    speedKn: Float?,
    positionRestricted: Boolean,
    setPaceKn: Float,
    mapCenterOffsetPx: Int,
    viewModel: RouteViewModel,
    onEndRoute: () -> Unit
) {
    // The back key is the mode's own escape while it is armed: the panel sits in the dashboard slot,
    // so nothing else offers one — and without this the press would reach the screen's exit guard with
    // a route still standing on the map. The shell owns what the escape means in each phase, and back
    // follows the acquisition's own Exit wherever it goes (R23).
    BackHandler(enabled = armed) { onEndRoute() }

    // ── The mode's ending, and nothing else: the arming is the screen's own act (R49, R71) ──
    // The trigger is the toggle or the drawer's Route section, and either one resolves the standing pair
    // and calls `RouteViewModel.arm` — so this effect's only subject is the **ending**: the machine's own
    // `end` follows the switch going off, whichever door turned it off (R57, R59).
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
    // **Attached once, mutated in place** (R14's prerequisite): the pool is created here, added to the
    // map in this effect and removed by its own disposal — never rebuilt on an answer — and every
    // update below writes points, colour and transparency into the objects that are already there.
    val poolSlots = 1 + AppConfig.routeAvoidCandidatePasses.size

    DisposableEffect(mapView) {
        val mv = mapView ?: return@DisposableEffect onDispose { }
        val pool = (0 until poolSlots).map { index ->
            Polyline().apply {
                title = if (index == 0) ROUTE_LINE_TITLE else "${ROUTE_LINE_TITLE}_$index"
                setPoints(emptyList())
                outlinePaint.isAntiAlias = true
            }
        }
        pool.forEach { mv.overlays.add(it) }

        // The provisional line — the partial line the engine publishes while a search runs — added
        // after the pool and before the pin, so it paints over the standing front line and the ladder
        // and stays under every marker. Hidden by its own flag, never by a null position.
        val progressLine = Polyline().apply {
            title = ROUTE_PROGRESS_TITLE
            setPoints(emptyList())
            outlinePaint.isAntiAlias = true
        }
        mv.overlays.add(progressLine)

        val pin = Marker(mv).apply {
            title = ROUTE_PIN_TITLE
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            // The tap is consumed rather than raising osmdroid's info bubble, which would show this
            // overlay's internal title to the user.
            setOnMarkerClickListener { _, _ -> true }
        }
        mv.overlays.add(pin)

        // The aim ring, attached once and mutated in place like the pool and the pin. It is a plain
        // osmdroid overlay rather than Compose chrome, so the ordering below — not the Compose stack —
        // decides its slot: its `route_` title keeps it in the track band, under every marker.
        val target = RouteTargetOverlay()
        mv.overlays.add(target)

        // **The three objects, named once at attach** — the ring is drawn while the aim is placed, so a
        // report that sees the ring proves this effect ran, and this line says what rode with it.
        Log.d(
            TAG,
            "map objects attached — pool=${pool.size} lines, pin=$ROUTE_PIN_TITLE, " +
                "target=$ROUTE_TARGET_TITLE, overlays=${mv.overlays.size}"
        )

        onDispose {
            mv.overlays.removeAll(pool)
            mv.overlays.remove(progressLine)
            mv.overlays.remove(pin)
            mv.overlays.remove(target)
            OverlayZOrder.reorder(mv)
            mv.invalidate()
        }
    }

    LaunchedEffect(mapView, state) {
        val mv = mapView ?: return@LaunchedEffect
        val pool = mv.overlays.filterIsInstance<Polyline>()
            .filter { it.title?.startsWith(ROUTE_LINE_TITLE) == true }
            .sortedBy { it.title }
        // **The pin is not a prerequisite for the lines** (the device's log, 2026-09-23): this effect
        // feeds the pool and updates the pin beside it, so a pin missing from the map costs the
        // destination dot and nothing else. It used to return here, which starved every line in silence
        // the moment another pass swept the pin.
        val pin = mv.overlays.filterIsInstance<Marker>().firstOrNull { it.title == ROUTE_PIN_TITLE }
        if (pin == null) {
            Log.d(
                TAG,
                "no pin on the map — the lines draw without the destination dot: " +
                    "overlays=${mv.overlays.size}, pool=${pool.size}"
            )
        }

        val plan = state.plan
        val colour = AppConfig.routeLineColor
        val stroke = dpToPx(AppConfig.routeLineWidthDp, mv.paintDensity)
        // **The selected line at full strength, the others dimmed** (R54, R64): the plan's own key for
        // the line the selection stands on, and the one shared key for every line drawn beside it —
        // candidates here, and the line a search is still building below.
        val selectedAlpha = transparencyPctToAlpha(AppConfig.routeLineTransparencyPct)
        val dimmedAlpha = transparencyPctToAlpha(AppConfig.routeDimmedTransparencyPct)

        pool.forEachIndexed { index, line ->
            val candidate = candidates.getOrNull(index)
            if (candidate == null || candidate.points.size < 2) {
                line.setPoints(emptyList())
            } else {
                val alpha = if (index == selectedIndex) selectedAlpha else dimmedAlpha
                line.setPoints(candidate.points.map { GeoPoint(it.latitude, it.longitude) })
                line.outlinePaint.apply {
                    color = AndroidColor.argb(
                        alpha,
                        AndroidColor.red(colour),
                        AndroidColor.green(colour),
                        AndroidColor.blue(colour)
                    )
                    strokeWidth = stroke
                }
            }
        }

        // The pin marks the **selected** line's resolved destination, which is where that route really
        // ends — an end on land or in another stretch has already moved to the boat's own stretch. One
        // pin serves the whole set: it stands at the line the eye reads at full strength.
        val selected = candidates.getOrNull(selectedIndex) ?: plan
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
            // An overlay with nothing to draw is hidden by its own flag, never by clearing its geometry:
            // osmdroid's `Marker.setPosition` clones what it is given, so a null position throws on the
            // frame a mode opens with no route yet. `CoastlineMapView` hides its overlays the same way.
            pin?.isEnabled = false
        }

        // **The pipe in one line** (the line-not-drawn report): what the state carried, how many points
        // the pool really holds, whether the pool is still on the map, and the paint it was handed —
        // read back off the objects, so a writer the eye cannot see is the one the log names.
        val firstLine = pool.firstOrNull()
        Log.d(
            TAG,
            "line repaint — phase=${state.phase}, points=${selected?.points?.size ?: 0}, " +
                "pool=${pool.size} attached=${pool.count { line -> mv.overlays.contains(line) }} " +
                "firstLineAlpha=${firstLine?.outlinePaint?.alpha ?: -1}, " +
                "lines=${candidates.size}, selected=$selectedIndex, stroke=${stroke}px, " +
                "colour=${Integer.toHexString(colour)}, selectedAlpha=$selectedAlpha, " +
                "dimmedAlpha=$dimmedAlpha, pinEnabled=${pin?.isEnabled == true}"
        )

        OverlayZOrder.reorder(mv)
        mv.invalidate()
    }

    // ── The provisional line: the partial line the engine publishes, drawn as it is built ──
    // A line the pipeline has not finished is not the plan: it draws into its own overlay at the line's
    // own colour and width with a provisional transparency, and it is hidden the moment progress clears
    // — on the answer and on an abort alike — so a partial line never outlives the search that drew it.
    LaunchedEffect(mapView, progress) {
        val mv = mapView ?: return@LaunchedEffect
        val line = mv.overlays.filterIsInstance<Polyline>()
            .firstOrNull { it.title == ROUTE_PROGRESS_TITLE } ?: return@LaunchedEffect
        val points = progress?.points
        if (points == null || points.size < 2) {
            line.isEnabled = false
            line.setPoints(emptyList())
        } else {
            line.setPoints(points.map { GeoPoint(it.latitude, it.longitude) })
            line.outlinePaint.apply {
                color = AndroidColor.argb(
                    transparencyPctToAlpha(AppConfig.routeDimmedTransparencyPct),
                    AndroidColor.red(AppConfig.routeLineColor),
                    AndroidColor.green(AppConfig.routeLineColor),
                    AndroidColor.blue(AppConfig.routeLineColor)
                )
                strokeWidth = dpToPx(AppConfig.routeLineWidthDp, mv.paintDensity)
            }
            line.isEnabled = true
        }
        mv.invalidate()
    }

    // ── The aim ring: the map's own layer, not Compose chrome ──────────────────
    // The ring is painted by [RouteTargetOverlay], an osmdroid overlay: every Compose overlay sits above
    // the whole MapView, so a Compose ring could never let the boat's marker paint over it, and no
    // ordering could put the marker band above it. The title's `route_` prefix is what keeps the overlay
    // in the track band — above the tracks and the lines, below every marker — so no rule is added.
    val choosing = state as? RouteState.Choosing
    val targetShown = armed && choosing != null
    val targetRefused = targetShown && (choosing?.refusal != null || choosing?.originRefusal != null)

    LaunchedEffect(mapView, targetShown, targetRefused, mapCenterOffsetPx) {
        val mv = mapView ?: return@LaunchedEffect
        val target = mv.overlays.filterIsInstance<RouteTargetOverlay>().firstOrNull()
            ?: return@LaunchedEffect
        // The same screen point the Compose ring painted at: the map's centre plus the app's own offset,
        // the offset `inspectAnchor` reads the aim with — so the ring and the aim cannot drift apart.
        target.centerOffsetPx = mapCenterOffsetPx
        target.shown = targetShown
        target.refused = targetRefused
        mv.invalidate()
    }

    // The refused crosshair's beat is the clock this overlay is repainted on, not a Compose animation:
    // a frame is pulled on while the refusal is on screen and stops the moment it is not.
    LaunchedEffect(mapView, targetRefused) {
        val mv = mapView ?: return@LaunchedEffect
        if (!targetRefused) return@LaunchedEffect
        while (true) {
            delay(ROUTE_TARGET_PULSE_FRAME_MS)
            mv.invalidate()
        }
    }
}

/**
 * The aim ring, as the map's own layer rather than as Compose chrome.
 *
 * The ring and its refused crosshair are painted at **the screen point the aim is made at** — the map's
 * centre plus the app's own offset — where a Compose ring could only ever sit *above* the whole map. Its
 * title carries the `route_` prefix, which is what `OverlayZOrder` reads to place it in the **track
 * band**: above the tracks and the route lines, and **below every marker, the boat included**. That band
 * is the whole point of the move — the boat is a marker, so it now paints over the ring.
 *
 * It is a [Polyline] because the ordering recognises an overlay's title by its **type**, and an
 * unrecognised type would land below every track however its title read; the base type's own drawing and
 * tap handling are both narrowed to nothing here. The geometry is `RouteOverlay`'s, moved with the ring;
 * the colours and the pulse period are read from `AppConfig` at draw time, so a value edited between two
 * frames takes effect on the next.
 */
internal class RouteTargetOverlay : Polyline() {

    /** Whether the ring is drawn at all — true only while the destination is being acquired. */
    var shown: Boolean = false

    /** True while the aim — or the anchor — is refused: the crosshair is drawn and it beats (R6, R27). */
    var refused: Boolean = false

    /** The app's own vertical offset of the aim, in px — the same shift `inspectAnchor` reads it with. */
    var centerOffsetPx: Int = 0

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        title = ROUTE_TARGET_TITLE
    }

    override fun draw(c: Canvas, osmv: MapView, shadow: Boolean) {
        if (shadow || !shown) return
        val centerX = osmv.width / 2f
        val centerY = osmv.height / 2f + centerOffsetPx
        val density = osmv.paintDensity
        val outerRadius = ROUTE_TARGET_RADIUS_DP * density

        // The ring and its inner mark, in the pin's own colour so the target and the pin that replaces
        // it read as one thing. A canvas reads the keys directly; only the geometry is this file's.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = ROUTE_TARGET_STROKE_DP * density
        paint.color = AppConfig.routePinColor
        c.drawCircle(centerX, centerY, outerRadius, paint)
        paint.style = Paint.Style.FILL
        c.drawCircle(centerX, centerY, ROUTE_TARGET_INNER_RADIUS_DP * density, paint)

        // The refused state is the same ring with the bold red crosshair over it, for the aim and for the
        // anchor alike (R6, R27), beating with the period `route.target.pulseMs` states.
        if (refused) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = AppConfig.routeTargetWidthDp * density
            paint.color = AppConfig.routeTargetColor
            paint.alpha = (refusedCrosshairAlpha(System.currentTimeMillis()) * 255f).roundToInt()
                .coerceIn(0, 255)
            c.drawLine(centerX - outerRadius, centerY, centerX + outerRadius, centerY, paint)
            c.drawLine(centerX, centerY - outerRadius, centerX, centerY + outerRadius, paint)
        }
    }
}

/**
 * The refused crosshair's alpha at [nowMs]: the app's one beat, 1 → [minAlpha] → 1 over [pulseMs], read
 * from the wall clock rather than from an animation — the host repaints the overlay on that same clock
 * while the refusal is on screen, so the tick survives the move out of Compose.
 */
internal fun refusedCrosshairAlpha(
    nowMs: Long,
    pulseMs: Int = AppConfig.routeTargetPulseMs,
    minAlpha: Float = 0.3f
): Float {
    val period = 2L * pulseMs.coerceAtLeast(1)
    val phase = (nowMs % period).toFloat() / period.toFloat()
    val ramp = if (phase < 0.5f) phase * 2f else (1f - phase) * 2f
    return 1f - (1f - minAlpha) * ramp
}
