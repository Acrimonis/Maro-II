package ykws.android.maro.ui.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp

// ─────────────────────────────────────────────────────────────────────────────
// Enums
// ─────────────────────────────────────────────────────────────────────────────

enum class SlideDirection { FROM_RIGHT, FROM_LEFT, FROM_BOTTOM, FADE_ONLY }

enum class ShadowEdge { LEFT, RIGHT, TOP }

// ─────────────────────────────────────────────────────────────────────────────
// DrawerSlot — unified AnimatedVisibility wrapper for all drawer panels
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Reusable drawer slot that wraps [AnimatedVisibility] with direction-aware
 * slide animations and an optional edge shadow gradient.
 *
 * @param visible       Whether the slot content is visible.
 * @param modifier      Alignment + sizing for the slot (align, width, height, offset).
 * @param slideDirection Which direction the content slides in from.
 * @param shadowEdge    Optional edge on which to draw a shadow gradient.
 * @param content       The drawer composable to show inside the slot.
 */
@Composable
fun DrawerSlot(
    visible: Boolean,
    modifier: Modifier = Modifier,
    slideDirection: SlideDirection,
    shadowEdge: ShadowEdge? = null,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = buildEnterAnim(slideDirection),
        exit = buildExitAnim(slideDirection)
    ) {
        if (shadowEdge != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(shadowOffsetModifier(shadowEdge))
                    .drawBehind { shadowGradient(shadowEdge) }
            )
        }
        content()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Animation builders
// ─────────────────────────────────────────────────────────────────────────────

private fun buildEnterAnim(dir: SlideDirection) = when (dir) {
    SlideDirection.FROM_RIGHT -> slideInHorizontally(
        animationSpec = spring(dampingRatio = 1.0f, stiffness = 350f)
    ) { it } + fadeIn(tween(80))
    SlideDirection.FROM_LEFT -> slideInHorizontally(
        animationSpec = spring(dampingRatio = 1.0f, stiffness = 350f)
    ) { -it } + fadeIn(tween(80))
    SlideDirection.FROM_BOTTOM -> slideInVertically(
        animationSpec = spring(dampingRatio = 1.0f, stiffness = 350f)
    ) { it } + fadeIn(tween(80))
    SlideDirection.FADE_ONLY -> fadeIn(tween(200))
}

private fun buildExitAnim(dir: SlideDirection) = when (dir) {
    SlideDirection.FROM_RIGHT -> slideOutHorizontally(
        animationSpec = tween(150)
    ) { it } + fadeOut(tween(150))
    SlideDirection.FROM_LEFT -> slideOutHorizontally(
        animationSpec = tween(150)
    ) { -it } + fadeOut(tween(150))
    SlideDirection.FROM_BOTTOM -> slideOutVertically(
        animationSpec = tween(150)
    ) { it } + fadeOut(tween(150))
    SlideDirection.FADE_ONLY -> fadeOut(tween(150))
}

// ─────────────────────────────────────────────────────────────────────────────
// Shadow helpers
// ─────────────────────────────────────────────────────────────────────────────

/** Returns the offset modifier needed to position the shadow correctly. */
private fun shadowOffsetModifier(edge: ShadowEdge): Modifier = when (edge) {
    ShadowEdge.LEFT -> Modifier.offset(x = (-8).dp)
    ShadowEdge.RIGHT -> Modifier
    ShadowEdge.TOP -> Modifier.offset(y = (-8).dp)
}

/** Draws the 8dp gradient shadow on the specified edge. */
private fun DrawScope.shadowGradient(edge: ShadowEdge) {
    when (edge) {
        ShadowEdge.LEFT -> drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    ComposeColor.Transparent,
                    ComposeColor.Black.copy(alpha = 0.18f)
                ),
                startX = 0f,
                endX = 8.dp.toPx()
            )
        )
        ShadowEdge.RIGHT -> {
            val shadowW = 8.dp.toPx()
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        ComposeColor.Black.copy(alpha = 0.18f),
                        ComposeColor.Transparent
                    ),
                    startX = size.width - shadowW,
                    endX = size.width
                )
            )
        }
        ShadowEdge.TOP -> drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    ComposeColor.Transparent,
                    ComposeColor.Black.copy(alpha = 0.18f)
                ),
                startY = 0f,
                endY = 8.dp.toPx()
            )
        )
    }
}
