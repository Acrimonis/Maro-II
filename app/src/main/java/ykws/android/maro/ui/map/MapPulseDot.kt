package ykws.android.maro.ui.map

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ykws.android.maro.config.AppConfig

// ─────────────────────────────────────────────────────────────────────────────
// The app's one pulsing mark
//
// One disc, one geometry and one beat, worn by every mode square of the toggle row — GPS, tracking,
// inspect, route and the lock — and by the map's own marks outside the row (the drawer's recording dot,
// the live track card's dot and border). Its **geometry** (10 dp, the half-size corner inset, the 800 ms
// period, the 1 dp rim) stays here, one home; its **colour** is the caller's, because the mark carries the
// square's state (`semantic.compliant` / `semantic.caution` / `semantic.danger`) and the callers that
// paint no state read the UI's own `ui.map.pulse.dot` by default.
//
// The mark is a **body under a ring**: the body alone beats (1 → 0.5), while a 1 dp rim in the mark's own
// colour is stroked at full strength and never fades, so a state stays readable at the bottom of the beat.
// That rim is why the beat's floor can sit at 50 %; the shared beat keeps 30 % for the non-toggle callers
// that beat without a state (the refused crosshair, the trip border).
// ─────────────────────────────────────────────────────────────────────────────

/** The beat every pulsing marker in the app uses: how long one 1 → floor → 1 cycle takes (ms). */
internal const val MAP_PULSE_DEFAULT_MS = 800

/** The disc's diameter (dp) — the recording dot's own size, shared rather than re-chosen. */
internal val MAP_PULSE_DOT_SIZE: Dp = 10.dp

/**
 * The mark's inset from its square's top-right edges: half the disc's own size (5 dp for the 10 dp disc),
 * so the every-square placement can never drift from the size the disc is drawn at.
 */
internal val MAP_PULSE_DOT_INSET: Dp = MAP_PULSE_DOT_SIZE / 2

/** The mark's ring width (dp) — a hairline that crisps a 10 dp disc without eating it. */
internal val MAP_PULSE_DOT_RING_WIDTH: Dp = 1.dp

/**
 * The beat's floor for the toggle mark: the body never fades past 50 %, and the full-strength ring keeps
 * the state readable under it at the bottom of the cycle.
 */
internal const val MAP_PULSE_DOT_FLOOR = 0.5f

/** The shared beat's own default floor — the 30 % a state-less pulse (the refused crosshair) fades to. */
private const val MAP_PULSE_GENERIC_FLOOR = 0.3f

/**
 * The beat itself — an alpha running 1 → [floor] and back, forever, over [pulseMs].
 *
 * Kept apart from the disc so anything that wants to *pulse* rather than *draw a dot* — the refused
 * end's crosshair, the trip border — beats with the same treatment instead of inventing a second one.
 * Those callers take the default [MAP_PULSE_GENERIC_FLOOR]; the toggle mark passes
 * [MAP_PULSE_DOT_FLOOR] because it carries a state.
 */
@Composable
internal fun rememberPulseAlpha(
    pulseMs: Int,
    label: String,
    floor: Float = MAP_PULSE_GENERIC_FLOOR
): Float {
    val transition = rememberInfiniteTransition(label = label)
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = floor,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = pulseMs),
            repeatMode = RepeatMode.Reverse
        ),
        label = "${label}Alpha"
    )
    return alpha
}

/**
 * The shared pulsing disc — one geometry, one beat, a caller's colour, and a ring that never fades.
 *
 * The mark is a filled body under a 1 dp rim struck in the same colour: only the body carries the beat,
 * so the disc dims toward its floor while its edge holds full strength and the state stays legible. The
 * body and the rim both sit inside the disc's own [size], so a caller's placement is unchanged.
 *
 * The caller places it. The row squares do not: [MapToggleSquare] paints the mark itself, from the
 * square's own top-right corner inset by [MAP_PULSE_DOT_INSET]. The marks outside the row — the drawer's
 * recording dot and the live card's — keep their own placement by calling this component directly.
 * The size and the period stay parameters; [color] defaults to the UI's own mark, `ui.map.pulse.dot`, for
 * the callers that paint no state, and the mode squares pass the colour their resolved face carries.
 */
@Composable
internal fun MapPulseDot(
    modifier: Modifier = Modifier,
    color: Color = Color(AppConfig.uiMapPulseDot),
    size: Dp = MAP_PULSE_DOT_SIZE,
    pulseMs: Int = MAP_PULSE_DEFAULT_MS
) {
    val alpha = rememberPulseAlpha(pulseMs, label = "mapPulseDot", floor = MAP_PULSE_DOT_FLOOR)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
    ) {
        // The body alone beats.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(alpha)
                .background(color)
        )
        // The ring holds full strength over it, so the mark never loses its edge.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(MAP_PULSE_DOT_RING_WIDTH, color, CircleShape)
        )
    }
}
