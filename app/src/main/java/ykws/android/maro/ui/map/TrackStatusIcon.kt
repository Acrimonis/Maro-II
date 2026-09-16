package ykws.android.maro.ui.map

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.track.TrackRecorderState
import ykws.android.maro.data.track.TrackRecorderUiState

/**
 * Tracking status icon — one of the row's squares, painted by [MapToggleSquare] on the shared
 * [MapSurface].
 *
 * 44×44 dp rounded square with 🐾 paw-prints emoji, coloured background per state. A pulsing dot in the
 * top-right quadrant indicates sub-state (recording vs idle); the dot is this control's own and is not the
 * surface's business.
 *
 * States, one resolved [MapSurfaceFace] each:
 * - **OFF:** the shared inactive face — the fill painted whole, the glyph alone dimmed. No dot.
 * - **ON + moving:** `status.tracking.healthy` green at the shared active alpha, red pulsing dot.
 * - **ON + idle:** `status.tracking.idle` blue at the shared active alpha, red pulsing dot.
 */
@Composable
fun TrackStatusIcon(
    recorderState: TrackRecorderUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val face = when (recorderState.state) {
        TrackRecorderState.OFF -> mapSurfaceFaceInactive()
        TrackRecorderState.ON -> mapSurfaceFaceActive(
            Color(
                if (recorderState.isMoving) AppConfig.statusTrackingHealthy
                else AppConfig.statusTrackingIdle
            )
        )
    }
    val showDot = recorderState.state == TrackRecorderState.ON
    val dotColor = when {
        !showDot -> Color.Transparent
        recorderState.isMoving -> Color(AppConfig.statusTrackingDotRecording)
        else -> Color(AppConfig.statusTrackingDotIdle)
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

    MapToggleSquare(face = face, onClick = onClick, modifier = modifier) {
        Text(
            text = "\uD83D\uDC3E", // 🐾 paw prints
            fontSize = TOP_TOGGLE_ICON_SIZE,
            fontWeight = FontWeight.Bold
        )

        // Pulsing dot, top-right of the square. MapToggleSquare sizes this content box to the padded
        // area, so TopEnd here is the square's own corner inset by `ui.map.surface.padding` (6 dp) on
        // both axes — the same 10 dp dot at the same 6 dp inset the pre-surface version drew, and now by
        // construction rather than because the paw's measured box happened to be the padded 32 dp.
        if (showDot) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .align(Alignment.TopEnd)
                    .alpha(dotAlpha)
                    .clip(CircleShape)
                    .background(dotColor)
            )
        }
    }
}
