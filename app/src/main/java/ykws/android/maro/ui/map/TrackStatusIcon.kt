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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            bgColor = Color(0x33FFFFFF)
            contentAlpha = 0.40f
            showPulse = false
            indicatorColor = Color.Gray
        }
        TrackRecorderState.RECORDING -> {
            bgColor = Color(0xFF1B5E20).copy(alpha = 0.25f)
            contentAlpha = 1f
            showPulse = true
            indicatorColor = Color(0xFF4CAF50) // green
        }
        TrackRecorderState.PAUSED -> {
            bgColor = Color(0xFFE65100).copy(alpha = 0.25f)
            contentAlpha = 1f
            showPulse = false
            indicatorColor = Color(0xFFFFA726) // amber
        }
        TrackRecorderState.FINALIZING -> {
            bgColor = Color(0x33FFFFFF)
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
        androidx.compose.material3.Text(
            text = "\uD83D\uDCA3", // 👣 footprint
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold),
            color = Color.White.copy(alpha = contentAlpha)
        )
    }
}
