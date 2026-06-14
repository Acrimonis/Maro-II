package ykws.android.maro.ui.map

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Cardinal/ordinal direction a [FanLayout] opens toward from its parent anchor.
 */
enum class FanDirection {
    UP, DOWN, LEFT, RIGHT,
    UP_LEFT, UP_RIGHT, DOWN_LEFT, DOWN_RIGHT
}

/**
 * Configuration for a [FanLayout] — parameterised fan-out of child buttons from behind a parent.
 *
 * @param thetaDeg      Inter-button angular spacing (degrees). Primary geometry parameter.
 *                      R = (buttonSizeDp + edgeGapDp) / (2 × sin(θ/2)).
 * @param currentCount  How many child buttons this instance renders (2..5).
 * @param direction     Which way the fan opens from the parent anchor.
 * @param buttonSizeDp  Diameter of all buttons (default 64 dp, matching current control buttons).
 * @param edgeGapDp     Minimum gap between button edges (default 8 dp).
 * @param isOpen        Whether the fan is expanded (children visible) or collapsed.
 */
data class FanConfig(
    val thetaDeg: Float,
    val currentCount: Int,
    val direction: FanDirection,
    val buttonSizeDp: Dp = 64.dp,
    val edgeGapDp: Dp = 8.dp,
    val isOpen: Boolean = false
) {
    /** Arc radius derived from θ + button size + gap. All children sit on this circle. */
    val radiusPxMultiplier: Float by lazy {
        val halfThetaRad = Math.toRadians((thetaDeg / 2f).toDouble())
        (buttonSizeDp.value + edgeGapDp.value) / (2f * kotlin.math.sin(halfThetaRad)).toFloat()
    }

    /** Base angle (degrees) for [direction]. Children fan outward from here. */
    val baseAngleDeg: Float by lazy {
        when (direction) {
            FanDirection.UP -> -90f
            FanDirection.DOWN -> 90f
            FanDirection.LEFT -> 0f
            FanDirection.RIGHT -> 180f
            FanDirection.UP_LEFT -> -45f
            FanDirection.UP_RIGHT -> -135f
            FanDirection.DOWN_LEFT -> 45f
            FanDirection.DOWN_RIGHT -> 135f
        }
    }
}
