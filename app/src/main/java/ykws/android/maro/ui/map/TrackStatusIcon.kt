package ykws.android.maro.ui.map

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.track.TrackRecorderState
import ykws.android.maro.data.track.TrackRecorderUiState

/**
 * Tracking status icon — matches [GpsStatusIcon] and [EarthWaterIcon] styling.
 *
 * 44×44 dp rounded square with 🚤 speedboat emoji, colored background per state.
 * A pulsing dot in the top-right quadrant indicates sub-state (recording vs idle).
 *
 * States:
 * - **OFF:** Not tracking. White background at dimmed alpha (like GPS DEMO). No dot.
 * - **ON + moving:** Tracking and recording points. Green background, red pulsing dot.
 * - **ON + idle:** Tracking but stationary, not recording points. Blue background, blue pulsing dot.
 */
@Composable
fun TrackStatusIcon(
    recorderState: TrackRecorderUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val baseColor: Color
    val bgAlpha: Float
    val contentAlpha: Float
    val showDot: Boolean
    val dotColor: Color

    when (recorderState.state) {
        TrackRecorderState.OFF -> {
            baseColor = Color(AppConfig.statusTrackingOff)
            bgAlpha = AppConfig.statusTrackingAlphaDimmed
            contentAlpha = 0.50f
            showDot = false
            dotColor = Color.Transparent
        }
        TrackRecorderState.ON -> {
            baseColor = if (recorderState.isMoving)
                Color(AppConfig.statusTrackingHealthy)
            else
                Color(AppConfig.statusTrackingIdle)
            bgAlpha = AppConfig.statusTrackingAlphaActive
            contentAlpha = 1f
            showDot = true
            dotColor = if (recorderState.isMoving)
                Color(AppConfig.statusTrackingDotRecording)
            else
                Color(AppConfig.statusTrackingDotIdle)
        }
    }

    // Pulsing animation for the dot
    val infiniteTransition = rememberInfiniteTransition(label = "trackDotPulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "trackDotAlpha"
    )

    Box(
        modifier = modifier
            .size(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(baseColor.copy(alpha = bgAlpha))
            .clickable(onClick = onClick)
            .alpha(contentAlpha),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "\uD83D\uDEA4", // 🚤 speedboat
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )

        // Pulsing dot centered in top-right quadrant (22×22dp area, 16dp circle)
        if (showDot) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .align(Alignment.TopEnd)
                    .padding(top = 6.dp, end = 6.dp)
                    .alpha(dotAlpha)
                    .clip(CircleShape)
                    .background(dotColor)
            )
        }
    }
}
