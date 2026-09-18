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
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

// ── Tuning constants for dynamic marker sizing ────────────────────────────────
// The marker resizes like the map itself — exponentially with zoom — but with
// a mitigating factor so it doesn't grow/shrink as aggressively as ground coverage.
//
// Formula:  dp = baseDp × 2^(ZOOM_EXPONENT × (zoomLevel − REF_ZOOM))
//
// Map ground coverage doubles every +1 zoom level (exponent = 1.0).
// At [ZOOM_EXPONENT] = 0.3 the marker grows ~23 % per zoom level instead of 100 %.

/** Reference zoom where the marker is at its [BOAT_BASE_DP] / [DOT_BASE_DP]. */
internal const val REF_ZOOM = 12.0 // 11.0 -to 18.0

/** Base dp for the boat marker at [REF_ZOOM]. */
internal const val BOAT_BASE_DP = 32.0
/** Base dp for the land-dot marker at [REF_ZOOM]. */
internal const val DOT_BASE_DP  = 8.0

/**
 * Mitigating exponent applied to the zoom delta.
 * 1.0 = resize exactly like the map (doubles every zoom level).
 * 0.3 = gentler curve (~23 % growth per zoom, ~8× over the full 8–18 range).
 */
internal const val ZOOM_EXPONENT = 0.45

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

/**
 * A fixed icon drawn at the center of the screen, indicating the current
 * GPS position. Stays in place while the map moves beneath it.
 *
 * Sizing is dynamic:
 * - Follows the map zoom level exponentially with mitigating factor
 *   [ZOOM_EXPONENT]: bigger when zoomed in, smaller when zoomed out.
 * - Shrinks near the coast (≤ [DIST_SHRINK_RAMP_M] m) to avoid visual
 *   "running aground".
 *
 * - On water: displays the Maro boat logo ([R.drawable.maro_marker]).
 * - On land:  displays a blue dot ([R.drawable.maro_dot_marker]).
 *
 * @param zoomLevel      Current map zoom (8.0–18.0).
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
        val scaleFactor = 2.0.pow(ZOOM_EXPONENT * (zoomLevel - REF_ZOOM))
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
    // dp = baseDp × 2^(ZOOM_EXPONENT × (zoom − REF_ZOOM))
    val baseDp = if (isWater) BOAT_BASE_DP else DOT_BASE_DP
    val scaleFactor = 2.0.pow(ZOOM_EXPONENT * (zoomLevel - REF_ZOOM))

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
 */
@Composable
internal fun CapArrowOverlay(
    zoomLevel: Double,
    navigationState: NavigationState,
    showCapArrow: Boolean,
    modifier: Modifier = Modifier,
    centerOffsetYDp: Dp = 0.dp,
) {
    val effectiveSpeedKn = navigationState.speedKnots ?: navigationState.demoSpeedKnots
    val hasSpeed = effectiveSpeedKn != null && effectiveSpeedKn > CAP_MIN_SPEED_KNOTS
    if (!hasSpeed || !showCapArrow) return

    val scaleFactor = 2.0.pow(ZOOM_EXPONENT * (zoomLevel - REF_ZOOM))
    val baseArrowDp = (effectiveSpeedKn!! * CAP_DP_PER_KNOT).coerceIn(CAP_MIN_DP, CAP_MAX_DP)
    val arrowDp = (baseArrowDp * scaleFactor).dp

    val arrowColor = ComposeColor(AppConfig.mapNavigationArrowColor)
    Canvas(modifier = modifier) {
        val arrowLenPx = arrowDp.toPx()
        val cX = size.width / 2
        val midY = size.height / 2 + centerOffsetYDp.toPx()
        val endY = midY - arrowLenPx

        drawLine(
            color = arrowColor,
            start = Offset(cX, midY),
            end = Offset(cX, endY),
            strokeWidth = 2.25.dp.toPx(),
            cap = StrokeCap.Round
        )
        val headLen = 9.dp.toPx()
        val headSpread = 0.5f
        val path = Path().apply {
            moveTo(cX, endY)
            lineTo(
                cX - (headLen * sin(headSpread)).toFloat(),
                endY + (headLen * cos(headSpread)).toFloat()
            )
            lineTo(
                cX + (headLen * sin(headSpread)).toFloat(),
                endY + (headLen * cos(headSpread)).toFloat()
            )
            close()
        }
        drawPath(path, color = arrowColor)
    }
}

// ── Direction line overlay ───────────────────────────────────────────────────

/**
 * Thin dashed line drawn from the screen center (boat position) outward in the
 * heading direction, extending to the edge of the map.
 */
@Composable
internal fun DirectionLine(
    modifier: Modifier = Modifier,
    centerOffsetYDp: Dp = 0.dp,
) {
    val lineColor = ComposeColor(AppConfig.mapNavigationLineColor)
    Canvas(modifier = modifier) {
        val cX = size.width / 2
        val cY = size.height / 2 + centerOffsetYDp.toPx()

        drawLine(
            color = lineColor,
            start = Offset(cX, cY),
            end = Offset(cX, 0f),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 6f), 0f),
            cap = StrokeCap.Round
        )
    }
}
