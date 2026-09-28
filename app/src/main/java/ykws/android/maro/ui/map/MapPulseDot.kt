package ykws.android.maro.ui.map

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
// The app's one pulsing dot
//
// Two toggles carry it — the recording square and the route's own, the second on both its on-phases —
// and they must look alike: the same 10 dp disc at the same corner inset, beating 1 → 0.3 and back over
// 800 ms, **in one colour the caller does not choose** (R69). The colour is a UI token of its own,
// `ui.map.pulse.dot`, rather than the mode's colour: the mark means "this control is live", and the
// refused crosshair's red is a different thing that keeps its own key.
// ─────────────────────────────────────────────────────────────────────────────

/** The beat every pulsing marker in the app uses: how long one 1 → 0.3 → 1 cycle takes (ms). */
internal const val MAP_PULSE_DEFAULT_MS = 800

/** The disc's diameter (dp) — the recording dot's own size, shared rather than re-chosen. */
internal val MAP_PULSE_DOT_SIZE: Dp = 10.dp

/**
 * The beat itself — an alpha running 1 → 0.3 and back, forever, over [pulseMs].
 *
 * Kept apart from the disc so anything that wants to *pulse* rather than *draw a dot* — the refused
 * end's crosshair, for one — beats with the same treatment instead of inventing a second one.
 */
@Composable
internal fun rememberPulseAlpha(pulseMs: Int, label: String): Float {
    val transition = rememberInfiniteTransition(label = label)
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = pulseMs),
            repeatMode = RepeatMode.Reverse
        ),
        label = "${label}Alpha"
    )
    return alpha
}

/**
 * The shared pulsing disc — **one colour for every toggle that wears it** (R69).
 *
 * The caller places it: aligned `TopEnd` inside a [MapToggleSquare]'s content box it lands at the
 * square's own corner inset by `ui.map.surface.padding`, which is the inset the recording dot has
 * always had, now by construction rather than by the glyph's measured box. The size and the beat are
 * parameters because a mark that is not a toggle's may want another, but the colour is not: it is the
 * token, read here, so the two squares cannot drift into two reds.
 */
@Composable
internal fun MapPulseDot(
    modifier: Modifier = Modifier,
    size: Dp = MAP_PULSE_DOT_SIZE,
    pulseMs: Int = MAP_PULSE_DEFAULT_MS
) {
    val alpha = rememberPulseAlpha(pulseMs, label = "mapPulseDot")
    Box(
        modifier = modifier
            .size(size)
            .alpha(alpha)
            .clip(CircleShape)
            .background(Color(AppConfig.uiMapPulseDot))
    )
}
