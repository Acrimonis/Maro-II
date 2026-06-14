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
 * **Primary parameters:** [maxCount] and [direction]. All geometry is derived:
 * - θ = 180° / maxCount (inter-button angle)
 * - R = (buttonSizeDp + edgeGapDp) / (2 × sin(θ/2)) (arc radius)
 *
 * Angle convention: 0° = midnight (top), 90° = 3 o'clock (right),
 * 180° = 6 o'clock (bottom), 270° = 9 o'clock (left).
 *
 * @param maxCount          Reference max button count. θ = 180° / maxCount.
 * @param currentCount      How many child buttons this instance renders (≤ [maxCount]).
 * @param direction         The direction the fan POINTS (center of its 180° semicircle).
 * @param buttonSizeDp      Diameter of all buttons (default 64 dp).
 * @param edgeGapDp         Minimum gap between button edges (default 8 dp).
 * @param isOpen            Whether the fan is expanded (children visible) or collapsed.
 * @param toggleChildren    If true, children are toggle buttons (show active/inactive state).
 * @param stayOpenAfterToggle  If true, fan stays open after a child toggle; close via parent tap.
 * @param showActiveBadge   If true, show an 18 dp blue badge on the parent with [activeChildCount].
 * @param activeChildCount  Number of active children (shown in badge when [showActiveBadge] is true).
 */
data class FanConfig(
    val maxCount: Int,
    val currentCount: Int,
    val direction: FanDirection,
    val buttonSizeDp: Dp = 64.dp,
    val edgeGapDp: Dp = 8.dp,
    val isOpen: Boolean = false,
    val toggleChildren: Boolean = false,
    val stayOpenAfterToggle: Boolean = true,
    val showActiveBadge: Boolean = false,
    val activeChildCount: Int = 0
) {
    /** Inter-button angle derived from maxCount: θ = 180° / maxCount. */
    val thetaDeg: Float by lazy { 180f / maxCount }

    /** Arc radius derived from θ + button size + gap. All children sit on this circle. */
    val radiusPxMultiplier: Float by lazy {
        val halfThetaRad = Math.toRadians((thetaDeg / 2f).toDouble())
        (buttonSizeDp.value + edgeGapDp.value) / (2f * kotlin.math.sin(halfThetaRad)).toFloat()
    }

    /** Direction the fan POINTS (center of the 180° semicircle).
     *  The fan derives its start angle = baseAngle - 90°.
     *
     *  Convention: 0° = top (midnight), 90° = right (3 o'clock),
     *  180° = bottom (6 o'clock), 270° = left (9 o'clock). */
    val baseAngleDeg: Float by lazy {
        when (direction) {
            FanDirection.UP -> 0f
            FanDirection.DOWN -> 180f
            FanDirection.LEFT -> 270f
            FanDirection.RIGHT -> 90f
            FanDirection.UP_LEFT -> 315f
            FanDirection.UP_RIGHT -> 45f
            FanDirection.DOWN_LEFT -> 225f
            FanDirection.DOWN_RIGHT -> 135f
        }
    }
}
