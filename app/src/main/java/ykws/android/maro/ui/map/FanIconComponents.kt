package ykws.android.maro.ui.map

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

/** Shared theme blue used across all control-stack icons. */
val ThemeBlue = ComposeColor(0xFF1565C0)

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
            color = ThemeBlue,
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
        drawPath(path = triangle, color = ThemeBlue, alpha = alpha)
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
            color = ThemeBlue,
            start = Offset(inset, cy),
            end = Offset(size.width - inset, cy),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = ThemeBlue,
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
            color = ThemeBlue,
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
        tint = ThemeBlue,
        modifier = Modifier.size(ICON_SIZE_DP.dp)
    )
}
