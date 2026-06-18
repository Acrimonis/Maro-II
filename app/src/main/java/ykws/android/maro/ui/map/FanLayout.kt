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
 * fanned out behind it along a circular arc. Children are drawn BEHIND the parent
 * (Compose draws in declaration order; children declared first, parent after).
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
 * - Scrim dismiss: a transparent full-screen scrim is placed below the control
 *   stack when any fan is expanded; tapping anywhere that isn't a fan child,
 *   settings, or zoom button closes the fan. Also closes on parent anchor tap
 *   or Android Back (handled externally by [MapContent]).
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
    // Compute effective θ from the actual number of buttons to place (not maxCount).
    // This determines both spacing (angle between children) and radius (chord = btn+gap).
    val effectiveTheta = if (config.currentCount >= config.maxCount || config.currentCount < 2)
        config.thetaDeg
    else
        180f / config.currentCount

    // Compute R in dp from effective θ + button size + gap.
    val radiusDp: Dp = remember(effectiveTheta, config.buttonSizeDp, config.edgeGapDp) {
        val halfThetaRad = Math.toRadians((effectiveTheta / 2f).toDouble())
        val totalDp = config.buttonSizeDp.value + config.edgeGapDp.value
        (totalDp / (2f * sin(halfThetaRad)).toFloat()).dp
    }

    // The box size equals buttonSize so the parent fills it completely.
    // Children are offset from the box center using IntOffset (pixel-level precision for animation).
    Box(modifier = modifier.size(config.buttonSizeDp)) {
        // ── Child buttons (animated, only visible when fan is open) ──────
        // Declared FIRST so they render BEHIND the parent — children appear
        // to "fan out from behind" the parent when they animate outward.
        if (config.isOpen && config.currentCount > 0) {
            val count = config.currentCount.coerceAtMost(children.size)
            val density = androidx.compose.ui.platform.LocalDensity.current
            val rPx = with(density) { radiusDp.toPx() }
            val halfBtnPx = with(density) { (config.buttonSizeDp / 2f).toPx() }
            // Parent center in pixels within this Box coordinate space
            val parentCxPx = with(density) { (config.buttonSizeDp / 2f).toPx() }
            val parentCyPx = with(density) { (config.buttonSizeDp / 2f).toPx() }

            // Determine child positions.
            // When currentCount == maxCount: use the θ-spaced template (centered in 180°).
            // When currentCount < maxCount: distribute children across the FULL 180° arc
            // with ½θ (= θ/2) empty at each end — "½ space, btn, btn, btn, btn, ½ space".
            // Both θ and R are derived from effectiveTheta (= 180/currentCount).
            val baseAngleRad: Double
            val interButtonDeg: Float

            if (config.currentCount >= config.maxCount) {
                // Template mode: center maxCount slots in 180°, children at θ intervals.
                val maxArcSpan = (config.maxCount - 1) * config.thetaDeg
                val offsetDeg = (180f - maxArcSpan) / 2f
                baseAngleRad = Math.toRadians(
                    (config.baseAngleDeg - 90f + offsetDeg).toDouble()
                )
                interButtonDeg = config.thetaDeg
            } else {
                // Full-arc mode: use effectiveTheta from currentCount.
                // Children span the full 180° with ½θ at each end.
                val halfGapDeg = effectiveTheta / 2f
                val startDeg = config.baseAngleDeg - 90f + halfGapDeg
                baseAngleRad = Math.toRadians(startDeg.toDouble())
                interButtonDeg = effectiveTheta
            }

            children.take(count).forEachIndexed { i, childContent ->
                val angleRad = baseAngleRad + i * Math.toRadians(interButtonDeg.toDouble())
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

        // ── Parent button (on TOP of children → "fans out from behind") ──
        Box(modifier = Modifier.size(config.buttonSizeDp)) {
            MapControlButton(onClick = onParentClick) {
                parent(config.isOpen, config.activeChildCount)
            }

            // Active badge — 18 dp circle at TopEnd, outside circle clip.
            // Always shown when showActiveBadge is true; always full color regardless of
            // how many children are toggled on.
            if (config.showActiveBadge) {
                val bgColor = ButtonColors.bg
                val textColor = ButtonColors.badgeText
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(bgColor)
                        .align(Alignment.TopEnd)
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "${config.activeChildCount}",
                            color = textColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                        )
                    }
                }
            }
        }
    }
}
