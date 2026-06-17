
package ykws.android.maro.ui.map
import ykws.android.maro.config.AppConfig

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

// ── Single source of truth for all action-button colours ──────────────────────

/**
 * Shared colour palette for right-edge action buttons (settings, fan, zoom).
 *
 * Values are loaded at runtime from [ZoneConfig], which reads from
 * `maro.properties` (bundled in assets). Edit the .properties file to
 * change colours without modifying code — rebuild APK to apply.
 */
object ButtonColors {
    /** Button background — loaded from colors.properties, defaults to semi-transparent dark blue. */
    val bg: ComposeColor get() = ComposeColor(AppConfig.buttonActionBgColor)
    /** Icon colour for all action-button symbols — loaded from colors.properties. */
    val icon: ComposeColor get() = ComposeColor(AppConfig.buttonActionIconColor)
    /** Alpha (0.0–1.0) for active/toggled-on icon state. */
    val activeAlpha: Float get() = AppConfig.buttonActionIconActiveAlpha
    /** Alpha (0.0–1.0) for inactive/toggled-off icon state. */
    val inactiveAlpha: Float get() = AppConfig.buttonActionIconInactiveAlpha
    /** Badge count text colour. */
    val badgeText: ComposeColor get() = ComposeColor(AppConfig.uiButtonBadgeText)
    /** Badge alpha when the arc/fan is OPEN (expanded). */
    val badgeActiveAlpha: Float get() = AppConfig.buttonBadgeActiveAlpha
    /** Badge alpha when the arc/fan is CLOSED (collapsed). */
    val badgeInactiveAlpha: Float get() = AppConfig.buttonBadgeInactiveAlpha
}

/** Standard icon size for control-stack buttons (28 dp). */
private const val ICON_SIZE_DP = 28

/**
 * Icon: circular ring (300 m zone toggle). Drawn with [Canvas].
 *
 * @param alpha Opacity (1.0 = active, 0.25 = inactive).
 */
@Composable
fun CircleRingIcon(alpha: Float) {
    Canvas(modifier = Modifier.size(ICON_SIZE_DP.dp)) {
        val w = size.width
        val h = size.height
        drawCircle(
            color = ButtonColors.icon,
            radius = w * 0.38f,
            center = Offset(w * 0.5f, h * 0.5f),
            alpha = alpha,
            style = Stroke(width = w * 0.12f)
        )
    }
}

/**
 * Icon: warning triangle (low-depth danger overlay toggle). Drawn with [Canvas].
 *
 * @param alpha Opacity (1.0 = active, 0.25 = inactive).
 */
@Composable
fun WarningTriangleIcon(alpha: Float) {
    Canvas(modifier = Modifier.size(ICON_SIZE_DP.dp)) {
        val w = size.width
        val h = size.height
        val triangle = Path().apply {
            moveTo(w * 0.50f, h * 0.06f)
            lineTo(w * 0.96f, h * 0.88f)
            lineTo(w * 0.04f, h * 0.88f)
            close()
        }
        drawPath(path = triangle, color = ButtonColors.icon, alpha = alpha)
        drawRoundRect(
            color = ComposeColor.White,
            topLeft = Offset(w * 0.455f, h * 0.34f),
            size = Size(w * 0.09f, h * 0.28f),
            cornerRadius = CornerRadius(3f, 3f),
            alpha = alpha
        )
        drawCircle(
            color = ComposeColor.White,
            radius = w * 0.055f,
            center = Offset(w * 0.50f, h * 0.74f),
            alpha = alpha
        )
    }
}

/**
 * Icon: plus sign (zoom in). Drawn with [Canvas].
 */
@Composable
fun PlusIcon() {
    Canvas(modifier = Modifier.size(ICON_SIZE_DP.dp)) {
        val stroke = size.width * 0.16f
        val inset = size.width * 0.20f
        val cx = size.width / 2f
        val cy = size.height / 2f
        drawLine(
            color = ButtonColors.icon,
            start = Offset(inset, cy),
            end = Offset(size.width - inset, cy),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = ButtonColors.icon,
            start = Offset(cx, inset),
            end = Offset(cx, size.height - inset),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

/**
 * Icon: minus sign (zoom out). Drawn with [Canvas].
 */
@Composable
fun MinusIcon() {
    Canvas(modifier = Modifier.size(ICON_SIZE_DP.dp)) {
        val stroke = size.width * 0.16f
        val inset = size.width * 0.20f
        val cy = size.height / 2f
        drawLine(
            color = ButtonColors.icon,
            start = Offset(inset, cy),
            end = Offset(size.width - inset, cy),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

/**
 * Icon: gear (settings). Uses Material [Icons.Default.Settings].
 */
@Composable
fun GearIcon() {
    Icon(
        imageVector = Icons.Default.Settings,
        contentDescription = null,
        tint = ButtonColors.icon,
        modifier = Modifier.size(ICON_SIZE_DP.dp)
    )
}

/**
 * Icon: three stacked horizontal bars (depth layer indicator).
 * Mirrors the ArcLayoutToggle depth icon — narrower at top, full-width at bottom.
 *
 * @param alpha Opacity (1.0 = active, 0.25 = inactive).
 */
@Composable
fun ThreeStripeLayerIcon(alpha: Float) {
    Canvas(modifier = Modifier.size(ICON_SIZE_DP.dp)) {
        val w = size.width; val h = size.height; val inset = w * 0.12f; val barHeight = h * 0.22f
        // Top bar (narrowest)
        drawRoundRect(ButtonColors.icon, Offset(inset * 0.5f, h * 0.02f), Size(w - inset, barHeight),
            CornerRadius(3f, 3f), alpha = alpha * 0.5f)
        // Middle bar
        drawRoundRect(ButtonColors.icon, Offset(inset * 0.25f, h * 0.48f - barHeight / 2), Size(w - inset * 0.5f, barHeight),
            CornerRadius(3f, 3f), alpha = alpha * 0.7f)
        // Bottom bar (full width)
        drawRoundRect(ButtonColors.icon, Offset(0f, h - barHeight - inset * 0.5f), Size(w, barHeight),
            CornerRadius(3f, 3f), alpha = alpha)
    }
}

/**
 * Icon: circle with a diagonal slash (regulated zones indicator).
 *
 * @param alpha Opacity (1.0 = active, 0.25 = inactive).
 */
@Composable
fun RegulatedZoneIcon(alpha: Float) {
    Canvas(modifier = Modifier.size(ICON_SIZE_DP.dp)) {
        val w = size.width; val cx = w / 2f; val cy = size.height / 2f; val r = w * 0.40f
        val a = ButtonColors.icon
        // Outer circle (stroke)
        drawCircle(a, r, Offset(cx, cy), alpha, style = Stroke(w * 0.12f))
        // Diagonal slash
        val d = r * 0.5f
        drawLine(a, Offset(cx - d, cy + d), Offset(cx + d, cy - d), w * 0.12f,
            cap = StrokeCap.Round, alpha = alpha)
    }
}

/**
 * Icon: dual circle — outer stroke + inner fill (300m zone + regulated zones combined toggle).
 *
 * @param alpha Opacity (1.0 = active, 0.25 = inactive).
 */
@Composable
fun DoubleCircleIcon(alpha: Float) {
    Canvas(modifier = Modifier.size(ICON_SIZE_DP.dp)) {
        val w = size.width; val cx = w / 2f; val cy = size.height / 2f
        // Outer ring (regulated zones)
        drawCircle(ButtonColors.icon, w * 0.40f, Offset(cx, cy), alpha, style = Stroke(w * 0.10f))
        // Inner fill (300m zone)
        drawCircle(ButtonColors.icon, w * 0.20f, Offset(cx, cy), alpha)
    }
}

/**
 * Icon: 3-step depth bar stack (depth layer toggle).
 * Three stacked horizontal bars — narrow/mid/full width, same as ThreeStripeLayerIcon
 * but aliased for semantic naming in depth layer context.
 *
 * @param alpha Opacity (1.0 = active, 0.25 = inactive).
 */
@Composable
fun DepthBarIcon(alpha: Float) {
    ThreeStripeLayerIcon(alpha = alpha)
}
