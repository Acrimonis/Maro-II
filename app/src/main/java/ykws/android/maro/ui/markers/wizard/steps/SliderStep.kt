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
 * Reusable slider step for Radius, Proximity and Routing cost.
 * Visual style matches [BoatSizeSlider]: card background, row title+value,
 * accent-coloured slider.
 *
 * The three label parameters override the wording derived from [valueM], [unit] and [range]; their
 * defaults reproduce it exactly, so Radius and Proximity are unchanged.
 *
 * @param valueLabel the value line, defaulting to `<value> <unit>`.
 * @param startLabel the label under the slider's low end, defaulting to `0 <unit>`.
 * @param endLabel   the label under the high end, defaulting to the range's end with [unit].
 */
@Composable
internal fun SliderStep(
    title: String,
    valueM: Double,
    range: ClosedFloatingPointRange<Double>,
    step: Double,
    unit: String,
    onValueChange: (Double) -> Unit,
    comment: String? = null,
    valueLabel: String? = null,
    startLabel: String? = null,
    endLabel: String? = null
) {
    val accent = ComposeColor(AppConfig.uiAccent)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ComposeColor(AppConfig.uiCardBackground))
            .padding(horizontal = AppConfig.uiPaddingCardHorizontal.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                color = ComposeColor(AppConfig.uiTextPrimary),
                fontSize = AppConfig.uiFontToggleSize.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                valueLabel ?: "${valueM.toLong()} $unit",
                color = ComposeColor(AppConfig.uiValueText),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
        if (comment != null) {
            Text(
                comment,
                color = ComposeColor(AppConfig.uiTextMuted),
                fontSize = AppConfig.uiFontDescSize.sp,
                maxLines = 1
            )
            Spacer(Modifier.height(AppConfig.uiSpacingLabelControl.dp))
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
                inactiveTrackColor = ComposeColor(AppConfig.uiSwitchTrackInactive)
            )
        )
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                startLabel ?: "0 $unit",
                color = ComposeColor(AppConfig.uiTextMuted),
                fontSize = AppConfig.uiFontDescSize.sp
            )
            Text(
                endLabel ?: "${range.endInclusive.toLong()} $unit",
                color = ComposeColor(AppConfig.uiTextMuted),
                fontSize = AppConfig.uiFontDescSize.sp
            )
        }
    }
}
