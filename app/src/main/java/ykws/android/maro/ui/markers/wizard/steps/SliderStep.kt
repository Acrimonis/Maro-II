package ykws.android.maro.ui.markers.wizard.steps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ykws.android.maro.config.AppConfig

/**
 * Reusable slider step for Radius and Proximity.
 * Visual style matches [BoatSizeSlider]: card background, row title+value,
 * accent-coloured slider.
 */
@Composable
internal fun SliderStep(
    title: String,
    valueM: Double,
    range: ClosedFloatingPointRange<Double>,
    step: Double,
    unit: String,
    onValueChange: (Double) -> Unit,
    comment: String? = null
) {
    val accent = ComposeColor(AppConfig.uiSettingsAccent)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ComposeColor(AppConfig.uiCardBackground))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                color = ComposeColor(AppConfig.uiSettingsTextPrimary),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                "${valueM.toLong()} $unit",
                color = accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
        if (comment != null) {
            Text(
                comment,
                color = ComposeColor(AppConfig.uiSettingsTextMuted),
                fontSize = 11.sp
            )
            Spacer(Modifier.height(4.dp))
        }
        Slider(
            value = valueM.toFloat(),
            onValueChange = { onValueChange(it.toDouble()) },
            valueRange = range.start.toFloat()..range.endInclusive.toFloat(),
            steps = ((range.endInclusive - range.start) / step).toInt() - 1,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = accent.copy(alpha = 0.3f)
            )
        )
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "0 $unit",
                color = ComposeColor(AppConfig.uiSettingsTextSecondary),
                fontSize = 11.sp
            )
            Text(
                "${range.endInclusive.toLong()} $unit",
                color = ComposeColor(AppConfig.uiSettingsTextSecondary),
                fontSize = 11.sp
            )
        }
    }
}
