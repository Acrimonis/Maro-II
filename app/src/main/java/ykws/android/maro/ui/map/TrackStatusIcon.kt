package ykws.android.maro.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
 * No animations — follows the same clean pattern as the GPS status icon.
 *
 * States:
 * - **Not tracking:** White background at dimmed alpha (like GPS DEMO)
 * - **Tracking (moving):** Green background at active alpha (like GPS HEALTHY)
 * - **Tracking (idle):** Blue background at active alpha (like GPS IDLE)
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

    when (recorderState.state) {
        TrackRecorderState.IDLE, TrackRecorderState.FINALIZING -> {
            // Not tracking — blue bg, dimmed (GPS IDLE style, visible on dark bg)
            baseColor = Color(AppConfig.statusGpsIdle)
            bgAlpha = AppConfig.statusGpsAlphaDimmed
            contentAlpha = 0.60f
        }
        TrackRecorderState.RECORDING -> {
            // Tracking + moving (green, GPS HEALTHY) vs tracking + idle (blue, GPS IDLE)
            baseColor = if (recorderState.isMoving)
                Color(AppConfig.uiDashboardStatusSuccess)
            else
                Color(AppConfig.statusGpsIdle)
            bgAlpha = AppConfig.statusGpsAlphaActive
            contentAlpha = 1f
        }
        TrackRecorderState.PAUSED -> {
            // Manually paused — blue bg, idle state
            baseColor = Color(AppConfig.statusGpsIdle)
            bgAlpha = AppConfig.statusGpsAlphaActive
            contentAlpha = 1f
        }
    }

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
    }
}
