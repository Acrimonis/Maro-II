package ykws.android.maro.ui.map

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A fan layout that renders a parent button at a fixed position with child buttons
 * fanned out behind it along a circular arc. Children are drawn ON TOP of the parent
 * (Compose draws later-declared children on top by default).
 *
 * Geometry (confirmed as STRONG rules in the feature spec):
 * - θ is the primary parameter (inter-button angle).
 * - Parent at center; children on an arc at radius R.
 * - R derived from θ + buttonSizeDp + edgeGapDp.
 * - All parent→child distances = R (equidistant per relationship).
 * - All adjacent child chords = 2R × sin(θ/2) (equidistant per relationship).
 * - Children centered in the directional arc (offset from base angle).
 *
 * Toggle mode ([FanConfig.toggleChildren]):
 * - Children receive [isActive] to visually indicate toggle state.
 * - Fan stays open after child toggle ([FanConfig.stayOpenAfterToggle]).
 * - Active badge shown on parent when [FanConfig.showActiveBadge] is true.
 * - No scrim — closes on parent tap or external back handler.
 *
 * Animation:
 * - Expand: staggered (70 ms per child), 280 ms tween, FastOutSlowIn.
 * - Collapse: simultaneous, 200 ms tween.
 *
 * @param config       Fan geometry + behavior configuration.
 * @param modifier     Root modifier for the fan's bounding box.
 * @param parent       Composable for the parent button's content. Receives [isOpen] and [activeChildCount].
 * @param onParentClick   Tap handler for the parent button.
 * @param children        List of composables for each child button's icon content. Each receives [isActive].
 * @param onChildClick    Tap handler for each child button. Receives index and current active state.
 * @param activeStates    Per-child active state. When non-empty, overrides [FanConfig.toggleChildren] per child.
 */
@Composable
fun FanLayout(
    config: FanConfig,
    modifier: Modifier = Modifier,
    parent: @Composable (isOpen: Boolean, activeChildCount: Int) -> Unit,
    onParentClick: () -> Unit,
    children: List<@Composable (isActive: Boolean) -> Unit>,
    onChildClick: ((index: Int, isActive: Boolean) -> Unit)? = null,
    activeStates: List<Boolean> = emptyList()
) {
    // Compute R in dp (arc radius) from θ + button size + gap.
    val radiusDp: Dp = remember(config.thetaDeg, config.buttonSizeDp, config.edgeGapDp) {
        val halfThetaRad = Math.toRadians((config.thetaDeg / 2f).toDouble())
        val totalDp = config.buttonSizeDp.value + config.edgeGapDp.value
        (totalDp / (2f * sin(halfThetaRad)).toFloat()).dp
    }

    // The box size equals buttonSize so the parent fills it completely.
    // Children are offset from the box center using IntOffset (pixel-level precision for animation).
    Box(modifier = modifier.size(config.buttonSizeDp)) {
        // ── Parent button ────────────────────────────────────────────────
        // Wrapped in a Box so the badge can render OUTSIDE the circle clip.
        Box(modifier = Modifier.size(config.buttonSizeDp)) {
            MapControlButton(onClick = onParentClick) {
                parent(config.isOpen, config.activeChildCount)
            }

            // Active badge — 18 dp blue circle at TopEnd, outside circle clip.
            // Always visible when showActiveBadge is true (shows "0" when none active).
            if (config.showActiveBadge) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(ComposeColor(0xFF1565C0))
                        .align(Alignment.TopEnd)
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "${config.activeChildCount}",
                            color = ComposeColor.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                        )
                    }
                }
            }
        }

        // ── Child buttons (animated, only visible when fan is open) ──────
        if (config.isOpen && config.currentCount > 0) {
            val count = config.currentCount.coerceAtMost(children.size)
            val totalArcSpan = (count - 1) * config.thetaDeg
            val offsetDeg = (180f - totalArcSpan) / 2f // centred in reference semicircle
            // baseAngleDeg is the DIRECTION the fan points (center of semicircle).
            // Derive the start angle: start = center - 90°.
            val startAngleDeg = config.baseAngleDeg - 90f + offsetDeg
            val baseAngleRad = Math.toRadians(startAngleDeg.toDouble())
            val density = androidx.compose.ui.platform.LocalDensity.current
            val rPx = with(density) { radiusDp.toPx() }
            val halfBtnPx = with(density) { (config.buttonSizeDp / 2f).toPx() }
            // Parent center in pixels within this Box coordinate space
            val parentCxPx = with(density) { (config.buttonSizeDp / 2f).toPx() }
            val parentCyPx = with(density) { (config.buttonSizeDp / 2f).toPx() }

            children.take(count).forEachIndexed { i, childContent ->
                val angleRad = baseAngleRad + i * Math.toRadians(config.thetaDeg.toDouble())
                // User's angle convention: 0°=top (midnight), clockwise.
                // Offset: x = R × sin(angle), y = -R × cos(angle)
                // This maps 0°→up, 90°→right, 180°→down, 270°→left
                val targetX = (parentCxPx + rPx * sin(angleRad) - halfBtnPx).roundToInt()
                val targetY = (parentCyPx - rPx * cos(angleRad) - halfBtnPx).roundToInt()
                // Start position: behind parent (same center)
                val startX = (parentCxPx - halfBtnPx).roundToInt()
                val startY = (parentCyPx - halfBtnPx).roundToInt()

                // Animate between start (collapsed) and target (expanded)
                val anim = remember { Animatable(0f) }
                LaunchedEffect(config.isOpen) {
                    if (config.isOpen) {
                        anim.snapTo(0f)
                        kotlinx.coroutines.delay(i * 70L) // stagger
                        anim.animateTo(1f, tween(280, easing = FastOutSlowInEasing))
                    } else {
                        anim.animateTo(0f, tween(200)) // simultaneous collapse
                    }
                }

                val t = anim.value
                val dx = (startX + (targetX - startX) * t).roundToInt()
                val dy = (startY + (targetY - startY) * t).roundToInt()

                // Use per-child active state when provided, otherwise fall back to toggleChildren flag
                val isActive = activeStates.getOrElse(i) { config.toggleChildren }

                Box(
                    modifier = Modifier
                        .offset { IntOffset(dx, dy) }
                        .size(config.buttonSizeDp)
                ) {
                    MapControlButton(
                        onClick = {
                            // In toggle mode, report the current state so caller can toggle
                            if (config.toggleChildren) {
                                onChildClick?.invoke(i, !config.isOpen)
                            } else {
                                onChildClick?.invoke(i, false)
                            }
                        },
                        icon = { childContent(isActive) }
                    )
                }
            }
        }
    }
}
