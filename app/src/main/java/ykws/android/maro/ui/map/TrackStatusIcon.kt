package ykws.android.maro.ui.map

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.track.TrackRecorderState
import ykws.android.maro.data.track.TrackRecorderUiState

/**
 * Small status icon indicating the track recording state.
 *
 * Placed in the top-left icon row alongside other status icons.
 *
 * - IDLE: grey/dimmed footprint icon
 * - RECORDING: footprint icon with pulsing green indicator dot
 * - PAUSED: footprint icon with amber indicator dot
 *
 * Clickable to open [TrackDrawerOverlay].
 */
@Composable
fun TrackStatusIcon(
    recorderState: TrackRecorderUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor: Color
    val contentAlpha: Float
    val showPulse: Boolean
    val indicatorColor: Color

    when (recorderState.state) {
        TrackRecorderState.IDLE -> {
            bgColor = Color(AppConfig.uiSettingsSwitchTrackInactive)
            contentAlpha = 0.40f
            showPulse = false
            indicatorColor = Color.Gray
        }
        TrackRecorderState.RECORDING -> {
            bgColor = Color(AppConfig.uiDashboardStatusSuccess).copy(alpha = 0.25f)
            contentAlpha = 1f
            showPulse = true
            indicatorColor = Color(AppConfig.uiDashboardStatusSuccess)
        }
        TrackRecorderState.PAUSED -> {
            bgColor = Color(AppConfig.uiDashboardStatusWarning).copy(alpha = 0.25f)
            contentAlpha = 1f
            showPulse = false
            indicatorColor = Color(AppConfig.uiDashboardStatusWarning)
        }
        TrackRecorderState.FINALIZING -> {
            bgColor = Color(AppConfig.uiSettingsSwitchTrackInactive)
            contentAlpha = 0.40f
            showPulse = false
            indicatorColor = Color.Gray
        }
    }

    val transition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(bgColor)
            .clickable(onClick = onClick)
            .alpha(contentAlpha)
            .drawBehind {
                // Draw footprint indicator dot
                val dotRadius = size.minDimension / 6
                val dotColor = if (showPulse) indicatorColor.copy(alpha = pulseAlpha) else indicatorColor
                drawCircle(
                    color = dotColor,
                    radius = dotRadius,
                    center = Offset(size.width / 2, size.height / 2)
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Canvas-drawn footprint icon (no dependency on material-icons-extended)
        FootprintIcon()
    }
}

/**
 * A simple two-toe footprint drawn via Canvas.
 * Replaces the 👣 emoji with a vector icon matching the app's icon family.
 */
@Composable
private fun FootprintIcon() {
    val iconColor = ButtonColors.icon
    androidx.compose.foundation.Canvas(modifier = Modifier.size(16.dp)) {
        val w = size.width; val h = size.height
        val strokeW = w * 0.14f
        // Left toe (small oval)
        drawCircle(
            color = iconColor,
            radius = w * 0.18f,
            center = Offset(w * 0.32f, h * 0.18f),
            style = Stroke(strokeW)
        )
        // Right toe (small oval)
        drawCircle(
            color = iconColor,
            radius = w * 0.18f,
            center = Offset(w * 0.68f, h * 0.18f),
            style = Stroke(strokeW)
        )
        // Foot body — two arcs forming an oval-like sole
        val footPath = Path().apply {
            moveTo(w * 0.22f, h * 0.30f)
            // Left side of foot
            quadraticBezierTo(
                w * 0.10f, h * 0.60f,
                w * 0.30f, h * 0.85f
            )
            // Bottom curve
            quadraticBezierTo(
                w * 0.50f, h * 0.95f,
                w * 0.70f, h * 0.85f
            )
            // Right side
            quadraticBezierTo(
                w * 0.90f, h * 0.60f,
                w * 0.78f, h * 0.30f
            )
            // Top curve
            quadraticBezierTo(
                w * 0.50f, h * 0.20f,
                w * 0.22f, h * 0.30f
            )
            close()
        }
        drawPath(footPath, color = iconColor, style = Stroke(strokeW * 0.7f))
    }
}
