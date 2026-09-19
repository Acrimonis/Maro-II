package ykws.android.maro.ui.map

import ykws.android.maro.R
import ykws.android.maro.config.AppConfig
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

// ── Tuning constants for dynamic marker sizing ────────────────────────────────
// The marker resizes like the map itself — exponentially with zoom — but with
// a mitigating factor so it doesn't grow/shrink as aggressively as ground coverage.
//
// Formula:  dp = baseDp × 2^(exponent × (zoomLevel − REF_ZOOM))
//
// The exponent and the two base sizes it multiplies are configuration values, the `map.marker.size.*`
// keys in maro.properties behind [AppConfig.mapMarkerSizeZoomExponent],
// [AppConfig.mapMarkerSizeBoatBaseDp] and [AppConfig.mapMarkerSizeDotBaseDp]: one factor and one pair
// for the sprite, the wizard's crosshair and the cap arrow alike. Map ground coverage doubles every +1
// zoom level (exponent = 1.0), so anything below it flattens the overlays against the map. What stays
// code is the reference zoom below, the coast-shrink pair and the arrow's speed clamps.

/** Reference zoom where the marker is at its configured base size, `map.marker.size.*BaseDp`. */
internal const val REF_ZOOM = 12.0 // the shipped zoom range is 11.0 -to 20.0

// ── Distance-to-coast shrink ramp ─────────────────────────────────────────────
// When the map center is close to the coastline, the marker shrinks so it
// doesn't visually overlap the ground ("run aground"). The multiplier ramps
// linearly from [DIST_SHRINK_MIN_MULT] at 0 m up to 1.0 at [DIST_SHRINK_RAMP_M].

/** Minimum size multiplier when exactly on the coastline. */
internal const val DIST_SHRINK_MIN_MULT = 0.3
/** Distance in meters at which the marker reaches full (1.0×) size. */
internal const val DIST_SHRINK_RAMP_M   = 2000.0

// ───────────────────────────────────────────────────────────────────────────────

/** Arrow length in dp at [REF_ZOOM] per knot of speed (65 dp ÷ 30 kn ≈ 2.17). */
internal const val CAP_DP_PER_KNOT = 65.0 / 30.0
/** Minimum arrow length in dp at [REF_ZOOM] (barely visible nub at 3 kn). */
internal const val CAP_MIN_DP = 1.0
/** Maximum arrow length in dp at [REF_ZOOM] (30+ kn capped). */
internal const val CAP_MAX_DP = 65.0
/** Below this speed (knots) the arrow is hidden. */
internal const val CAP_MIN_SPEED_KNOTS = 2.5f

/** Head length per unit of shaft width — the shipped 4 : 1, so the 2.25 dp shaft keeps its 9 dp head. */
internal const val CAP_ARROW_HEAD_RATIO = 4f

/** Half the head's apex angle, in radians — the `0.5` the triangle and the shaft's inset both read. */
internal const val CAP_ARROW_HALF_SPREAD = 0.5f

/**
 * Margin on the shaft's tangency depth, as a fraction: 5 % of extra inset, so anti-aliasing at exact
 * tangency cannot leave a seam where the round cap meets the flanks.
 */
internal const val CAP_ARROW_TANGENCY_MARGIN = 1.05f

/**
 * The arrow's head length (dp) for a shaft of [shaftWidthDp] dp.
 *
 * Derived rather than configured: one knob then moves a coherent arrow, where a head of its own could
 * be set against a shaft it no longer matches. Pure, so the shipped 4 : 1 ratio is unit-covered.
 */
internal fun capArrowHeadDp(shaftWidthDp: Float): Float = shaftWidthDp * CAP_ARROW_HEAD_RATIO

/**
 * The same head, capped against the arrow it caps — `min([CAP_ARROW_HEAD_RATIO] × width, [arrowLenDp] / 2)`.
 *
 * The ratio alone would give the thickest shaft on a slow boat a head taller than the arrow carrying it
 * (32 dp of head against a 5.6 dp arrow at 8 dp), its base falling far below the screen centre. Half the
 * drawn length is the ceiling, so the head is never taller than the shaft it caps. Pure in its two inputs.
 */
internal fun capArrowHeadDp(shaftWidthDp: Float, arrowLenDp: Float): Float =
    min(capArrowHeadDp(shaftWidthDp), arrowLenDp / 2f)

/**
 * How far (dp) below the apex the shaft's polyline stops, for a shaft of [shaftWidthDp] dp.
 *
 * The tangency depth — `radius / sin(halfSpread)`: the distance from a circle's centre to a line is
 * `depth × sin(h)`, never `depth × tan(h)`, the latter stopping short and leaving part of the round cap
 * beside the flanks. Grown by [CAP_ARROW_TANGENCY_MARGIN] so exact tangency cannot show a seam.
 */
internal fun capArrowShaftInsetDp(shaftWidthDp: Float): Float =
    (shaftWidthDp / 2f) / sin(CAP_ARROW_HALF_SPREAD) * CAP_ARROW_TANGENCY_MARGIN

/**
 * A fixed icon drawn at the center of the screen, indicating the current
 * GPS position. Stays in place while the map moves beneath it.
 *
 * Sizing is dynamic:
 * - Follows the map zoom level exponentially with the configured factor
 *   [AppConfig.mapMarkerSizeZoomExponent]: bigger when zoomed in, smaller when zoomed out.
 * - Shrinks near the coast (≤ [DIST_SHRINK_RAMP_M] m) to avoid visual
 *   "running aground".
 *
 * - On water: displays the Maro boat logo ([R.drawable.maro_marker]).
 * - On land:  displays a blue dot ([R.drawable.maro_dot_marker]).
 *
 * @param zoomLevel      Current map zoom (the shipped 11.0–20.0 range).
 * @param distanceToShore Distance from map center to nearest coast in meters,
 *                        or `null` when unavailable.
 * @param onClick        Tap on the boat. It returns true only for the tap it accepted
 *                       (the one the map's Where-Am-I lambda acts on), and that return
 *                       is what pulses the zone; the crosshair branch discards it.
 */
@Composable
internal fun CenterMarkerOverlay(
    isWater: Boolean,
    zoomLevel: Double,
    distanceToShore: Double?,
    showCrosshair: Boolean = false,
    onClick: () -> Boolean = { false },
    modifier: Modifier = Modifier,
    centerOffsetYDp: Dp = 0.dp,
) {
    // ── Crosshair mode: replace boat/dot with a target icon during position-step wizard ──
    if (showCrosshair) {
        val baseDp = 32.0
        val scaleFactor = 2.0.pow(AppConfig.mapMarkerSizeZoomExponent * (zoomLevel - REF_ZOOM))
        val finalSizeDp = (baseDp * scaleFactor).dp

        Box(
            modifier = modifier
                .size(if (finalSizeDp < 48.dp) 48.dp else finalSizeDp)
                .offset(y = centerOffsetYDp)
                // The wizard's position step keeps its own rule: a square box, and no pulse —
                // the acceptance the round zone reports is not this branch's business.
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "\u2295",
                fontSize = (finalSizeDp.value / 1.5f).sp,
                color = ComposeColor(AppConfig.uiAccent),
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    val drawableId = if (isWater) R.drawable.maro_marker else R.drawable.maro_dot_marker
    val description = if (isWater) stringResource(R.string.marker_position_water)
                      else stringResource(R.string.marker_position_land)
    // The zone's assistive action: what the tap *does*, named as such, while the sprite above keeps
    // [description] as its own content description — the state it shows, not the act it offers.
    val actionLabel = stringResource(R.string.cd_find_markers_at_position)

    // ── Base size: exponential zoom scaling ───────────────────────────────
    // dp = baseDp × 2^(exponent × (zoom − REF_ZOOM)), the exponent configured
    val baseDp = (if (isWater) AppConfig.mapMarkerSizeBoatBaseDp else AppConfig.mapMarkerSizeDotBaseDp).toDouble()
    val scaleFactor = 2.0.pow(AppConfig.mapMarkerSizeZoomExponent * (zoomLevel - REF_ZOOM))

    // ── Distance-to-coast multiplier: [DIST_SHRINK_MIN_MULT] on the coast
    //    → 1.0 at [DIST_SHRINK_RAMP_M] m ───────────────────────────────────
    val distMultiplier = if (distanceToShore != null) {
        (DIST_SHRINK_MIN_MULT +
         (1.0 - DIST_SHRINK_MIN_MULT) * (distanceToShore / DIST_SHRINK_RAMP_M).coerceIn(0.0, 1.0))
            .toFloat()
    } else {
        1.0f  // no coastline data → full size
    }

    val finalSizeDp = ((baseDp * scaleFactor) * distMultiplier).dp

    // The marker Box stays at Alignment.Center (map center) in the parent.
    // On water: the boat image is shifted down by half its height so its top-center
    // aligns with the map center (GPS position at the boat's bow).
    // On land:   the dot stays centered (no offset — a dot has no direction).
    // That offset is left exactly as it is: it is what pins the bow to the GPS point.
    val spriteOffsetYDp = if (isWater) finalSizeDp / 2 else 0.dp

    // ── Tap zone: one fixed circle on the sprite's visual centre ───────────
    // The zone's diameter is a property, fixed at any zoom; the centre follows the sprite's own
    // offset while the diameter does not, so the pair is never one scaled value that could grow
    // the zone back with the boat (R20). The pulse is that zone's own disc, sized by its own
    // property and never by the sprite.
    val tapZoneCentreYDp = spriteOffsetYDp
    val tapZoneDiameter = AppConfig.mapMarkerTapZoneDiameterDp.dp
    val tapFlashDiameter = AppConfig.mapMarkerTapFlashDiameterDp.dp
    val tapFlashDurationMs = AppConfig.mapMarkerTapFlashDurationMs
    // The beat's ceiling: the token's own alpha, the flash's one carrier of transparency — the
    // colour named beside it is plain, so nothing but this multiplies the drawn peak.
    val tapFlashAlphaCeiling = AppConfig.mapMarkerTapFlashAlpha
    // The peak is a fraction of the beat's own duration, so the rise can never overrun the beat.
    val tapFlashPeakMs = (tapFlashDurationMs * AppConfig.mapMarkerTapFlashPeakRatio).roundToInt()
    // The box has to contain the circle it hit-tests: a child may *draw* outside its parent
    // (the hull does, below the bow), but a touch is only offered to a box that holds it.
    val boxSizeDp = if (isWater) finalSizeDp + tapZoneDiameter
                   else maxOf(finalSizeDp, tapZoneDiameter)

    // ── Beat on the accepted tap ──────────────────────────────────────────
    // The map's own UI state, never the view model's: the tap's acceptance comes back from
    // the map's lambda, and each accepted tap bumps the generation so a second tap inside
    // the beat restarts it (R22). Nothing here is tied to the dashboard, so a run closed
    // mid-beat lets the beat finish instead of cutting it.
    var flashGeneration by remember { mutableIntStateOf(0) }
    val flashAlpha = remember { Animatable(0f) }
    LaunchedEffect(flashGeneration) {
        if (flashGeneration == 0) return@LaunchedEffect
        // One beat, never a decay: up from nothing to the token's own alpha by
        // [tapFlashPeakMs], then back out to nothing at [tapFlashDurationMs]. The snap first
        // is what makes the restart a beat as well: a second tap interrupts the first mid-rise
        // or mid-fall, and the new one has to begin at nothing rather than at the old value.
        flashAlpha.snapTo(0f)
        flashAlpha.animateTo(
            targetValue = 0f,
            animationSpec = keyframes<Float> {
                durationMillis = tapFlashDurationMs.toInt()
                0f at 0
                1f at tapFlashPeakMs
                0f at tapFlashDurationMs.toInt()
            }
        )
    }
    val currentOnClick by rememberUpdatedState(onClick)
    // The zone's centre, read through the latest composition rather than captured: the gesture below
    // outlives the recomposition that moved it (a zoom change, or the distance-to-coast multiplier).
    val currentTapZoneCentreYDp by rememberUpdatedState(tapZoneCentreYDp)

    Box(
        modifier = modifier
            .size(boxSizeDp)
            .offset(y = centerOffsetYDp)
            // Keyed on nothing that moves with the sprite: keying on the size would restart the
            // pointer coroutine on any resize and cancel a tap already in flight, so the geometry
            // is read per gesture from the live node size and the current centre instead.
            .pointerInput(Unit) {
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // Read now, at the down this gesture is judged on, never when the coroutine
                    // started: the sprite may have resized since, and size is the live node's.
                    val radiusPx = (tapZoneDiameter / 2).toPx()
                    val centre = Offset(
                        size.width / 2f,
                        size.height / 2f + currentTapZoneCentreYDp.toPx()
                    )
                    // Radially, never as the rectangle: a touch in the box's corners is not this
                    // zone's, and it is left unconsumed so the map still gets the gesture.
                    if ((down.position - centre).getDistance() > radiusPx) return@awaitEachGesture
                    // Inside the circle the touch is ours, as the old box's clickable made the
                    // whole box ours, so a pan begun on the boat stops here exactly as before —
                    // over 48dp now instead of over the entire sprite.
                    down.consume()
                    var lifted = false
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                            ?: break
                        if (!change.pressed) { lifted = true; break }
                        // A drag is never a tap (R21): no query and no flash for it.
                        if ((change.position - down.position).getDistance() > slop) break
                    }
                    if (lifted && currentOnClick()) flashGeneration++
                }
            }
            // The assistive path back to the action the gesture runs, on the same node and through
            // the same lambda: the guard stands the activation down exactly as it stands the tap down,
            // and only an accepted one flashes. Merging carries the sprite's own description onto this
            // node, as the replaced clickable node carried it, so a reader's activate lands on an
            // action rather than on a description with none — no ripple, and no rectangle to hit.
            .semantics(mergeDescendants = true) {
                onClick(label = actionLabel) {
                    val accepted = currentOnClick()
                    if (accepted) flashGeneration++
                    accepted
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // ── Accepted-tap beat: the zone's own disc, drawn *beneath* the sprite ──
        // Beneath by the user's ruling: the hull stays crisp and the beat reads as the halo
        // around it instead of a wash across it. Sized by [tapFlashDiameter] and never by
        // [finalSizeDp], so it reads identically at every zoom, and centred on the very point the
        // gesture above judges from — the zone's own centre. [requiredSize], not [size]: the box
        // holds only the sprite plus the zone, so the disc must escape that constraint to reach
        // its full diameter, and it draws past the box because the box carries no clip while the
        // touch area stays the radial test's. Where the hull's opaque pixels cover the disc the
        // gold shows only in the sprite's own transparent margins — the flanks, plus above the
        // bow and below the stern while the sprite is smaller than the disc.
        if (flashAlpha.value > 0f) {
            Canvas(
                modifier = Modifier
                    .requiredSize(tapFlashDiameter)
                    .offset(y = tapZoneCentreYDp)
            ) {
                drawCircle(
                    color = ComposeColor(AppConfig.mapMarkerTapFlashColor),
                    radius = size.minDimension / 2f,
                    // The colour is plain and [tapFlashAlphaCeiling] carries the transparency, so
                    // the beat's own fraction is the only other multiplier: the peak is exactly
                    // that ceiling and both ends are nothing.
                    alpha = flashAlpha.value * tapFlashAlphaCeiling,
                )
            }
        }

        // ── Boat/land marker ──────────────────────────────────────────────
        Image(
            painter = painterResource(id = drawableId),
            contentDescription = description,
            modifier = Modifier
                .size(finalSizeDp)
                .offset(y = spriteOffsetYDp),
            contentScale = ContentScale.Fit
        )
    }
}

// ── Cap arrow overlay ────────────────────────────────────────────────────────

/**
 * Speed indicator arrow drawn from the screen centre upward, above the direction
 * line but below the boat/dot marker. Length scales with speed (knots) and zoom
 * level, matching the marker's exponential zoom factor. Hidden below
 * [CAP_MIN_SPEED_KNOTS] or when the user disables it via [showCapArrow].
 *
 * Its appearance arrives as parameters — [shaftWidthDp] with the head derived from it by
 * [capArrowHeadDp], [color], [transparencyPct] and the [followSpeedColour] mode — so the overlay reads
 * no appearance value of its own. Under the mode the colour comes from the speed ramp the tracks
 * paint from, and while [gpsStale] is set it wears that ramp's neutral tint: a lost fix freezes the
 * arrow at its last length rather than hiding it, and the tint is what stops that frozen arrow from
 * asserting a band the reading can no longer justify.
 *
 * The shaft's polyline stops inside the head at [capArrowShaftInsetDp] — the tangency depth grown by
 * [CAP_ARROW_TANGENCY_MARGIN], so the flanks stand one cap radius clear of the stroke's centreline with a
 * margin to spare and nothing of the stroke shows beside or beyond the apex. That apex stays exactly
 * where it was, because the arrow's length is the value it exists to state. The head is itself capped by
 * [capArrowHeadDp] against the drawn length, so a short arrow is never swallowed by the head it carries.
 */
@Composable
internal fun CapArrowOverlay(
    zoomLevel: Double,
    navigationState: NavigationState,
    showCapArrow: Boolean,
    shaftWidthDp: Float,
    color: Int,
    transparencyPct: Int,
    followSpeedColour: Boolean,
    gpsStale: Boolean,
    modifier: Modifier = Modifier,
    centerOffsetYDp: Dp = 0.dp,
) {
    val effectiveSpeedKn = navigationState.speedKnots ?: navigationState.demoSpeedKnots
    val hasSpeed = effectiveSpeedKn != null && effectiveSpeedKn > CAP_MIN_SPEED_KNOTS
    if (!hasSpeed || !showCapArrow) return

    val scaleFactor = 2.0.pow(AppConfig.mapMarkerSizeZoomExponent * (zoomLevel - REF_ZOOM))
    val baseArrowDp = (effectiveSpeedKn!! * CAP_DP_PER_KNOT).coerceIn(CAP_MIN_DP, CAP_MAX_DP)
    val arrowDp = (baseArrowDp * scaleFactor).dp

    // The ramp is read here, not passed: it is map-wide render data, while the seven settings are the
    // user's own values and arrive as parameters. The lookup is the band one, so no per-frame garbage.
    val ramp = AppConfig.trackHeatmapRamp
    val arrowColor = ComposeColor(
        when {
            !followSpeedColour -> color
            gpsStale -> ramp.unknownArgb
            else -> rampColorForSpeed(effectiveSpeedKn, ramp)
        }
    ).copy(alpha = transparencyPctToAlphaFraction(transparencyPct))
    Canvas(modifier = modifier) {
        val arrowLenPx = arrowDp.toPx()
        val cX = size.width / 2
        val midY = size.height / 2 + centerOffsetYDp.toPx()
        val endY = midY - arrowLenPx
        val strokeWidthPx = shaftWidthDp.dp.toPx()

        // The shaft stops the tangency depth below the apex, so the round cap is inscribed in the head
        // and nothing of the stroke shows beside or beyond it. Never past the drawn length: a capped head
        // on a short arrow is shallow enough that the depth would otherwise reach below the anchor.
        val shaftEndY = endY + capArrowShaftInsetDp(shaftWidthDp).dp.toPx().coerceAtMost(arrowLenPx)
        drawLine(
            color = arrowColor,
            start = Offset(cX, midY),
            end = Offset(cX, shaftEndY),
            strokeWidth = strokeWidthPx,
            cap = StrokeCap.Round
        )
        val headLen = capArrowHeadDp(shaftWidthDp, arrowDp.value).dp.toPx()
        val path = Path().apply {
            moveTo(cX, endY)
            lineTo(
                cX - (headLen * sin(CAP_ARROW_HALF_SPREAD)).toFloat(),
                endY + (headLen * cos(CAP_ARROW_HALF_SPREAD)).toFloat()
            )
            lineTo(
                cX + (headLen * sin(CAP_ARROW_HALF_SPREAD)).toFloat(),
                endY + (headLen * cos(CAP_ARROW_HALF_SPREAD)).toFloat()
            )
            close()
        }
        drawPath(path, color = arrowColor)
    }
}

// ── Direction line overlay ───────────────────────────────────────────────────

/** Dash length, in multiples of the stroke's own width: 4 on and 2 off. */
internal const val DASH_ON_WIDTH_RATIO = 4f
internal const val DASH_OFF_WIDTH_RATIO = 2f

/**
 * Thin dashed line drawn from the screen center (boat position) outward in the
 * heading direction, extending to the edge of the map.
 *
 * Its three appearance values arrive as parameters — [strokeWidthDp], [color] and [transparencyPct] —
 * so the overlay holds no settings read of its own. The dash is proportional to the stroke —
 * [DASH_ON_WIDTH_RATIO] on and [DASH_OFF_WIDTH_RATIO] off, both taken through `toPx()` — which is the
 * shipped 12 : 6 px at the 1 dp line on a 3× screen and stays tied to the stroke at every thickness.
 */
@Composable
internal fun DirectionLine(
    strokeWidthDp: Float,
    color: Int,
    transparencyPct: Int,
    modifier: Modifier = Modifier,
    centerOffsetYDp: Dp = 0.dp,
) {
    val lineColor = ComposeColor(color).copy(alpha = transparencyPctToAlphaFraction(transparencyPct))
    Canvas(modifier = modifier) {
        val cX = size.width / 2
        val cY = size.height / 2 + centerOffsetYDp.toPx()
        val strokeWidthPx = strokeWidthDp.dp.toPx()

        drawLine(
            color = lineColor,
            start = Offset(cX, cY),
            end = Offset(cX, 0f),
            strokeWidth = strokeWidthPx,
            pathEffect = PathEffect.dashPathEffect(
                floatArrayOf(
                    strokeWidthPx * DASH_ON_WIDTH_RATIO,
                    strokeWidthPx * DASH_OFF_WIDTH_RATIO
                ),
                0f
            ),
            cap = StrokeCap.Round
        )
    }
}
