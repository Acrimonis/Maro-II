package ykws.android.maro.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ykws.android.maro.config.AppConfig

/**
 * A label + value + slider **without its own box** — placed directly on a [CardArea] or inside a
 * [NestedCard] (`ui-component-guidelines` §2.2). The container owns the surface and the horizontal
 * inset; this row pads vertically only.
 *
 * [description] is optional (§2.1's optional row description). [startLabel] and [endLabel] are the
 * optional readings under the track's two ends, drawn by [SliderControl] so the settings rows and the
 * marker wizard's slider share one implementation.
 */
@Composable
internal fun SliderRow(
    label: String,
    description: String? = null,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit = {},
    startLabel: String? = null,
    endLabel: String? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = ComposeColor(AppConfig.uiTextPrimary),
                fontSize = AppConfig.uiFontToggleSize.sp,
                fontWeight = FontWeight.Medium
            )
            if (description != null) {
                Text(
                    text = description,
                    color = ComposeColor(AppConfig.uiTextMuted),
                    fontSize = AppConfig.uiFontDescSize.sp
                )
            }
        }
        Spacer(modifier = Modifier.width(AppConfig.uiSpacingLabelControl.dp))
        Text(
            text = valueLabel,
            color = ComposeColor(AppConfig.uiValueText),
            fontSize = AppConfig.uiFontValueSize.sp,
            fontWeight = FontWeight.Bold
        )
    }
    SliderControl(
        value = value,
        valueRange = valueRange,
        steps = steps,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        startLabel = startLabel,
        endLabel = endLabel
    )
}

/**
 * The bare slider — track and knob, its colours included, plus the optional readings under its two
 * ends — with no label, value line or surface of its own. [SliderRow] draws it under its own heading;
 * a composition that supplies the heading itself (the marker wizard's Routing cost step, §2.14) calls
 * it directly, so the accent and track colours and the end-label row each keep this one home.
 *
 * The readings are drawn only when either is passed, so a caller that names neither gets the track
 * alone.
 */
@Composable
internal fun SliderControl(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit = {},
    startLabel: String? = null,
    endLabel: String? = null
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = valueRange,
        steps = steps,
        colors = SliderDefaults.colors(
            thumbColor = ComposeColor(AppConfig.uiAccent),
            activeTrackColor = ComposeColor(AppConfig.uiAccent),
            inactiveTrackColor = ComposeColor(AppConfig.uiSwitchTrackInactive)
        )
    )
    if (startLabel != null || endLabel != null) {
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = startLabel.orEmpty(),
                color = ComposeColor(AppConfig.uiTextMuted),
                fontSize = AppConfig.uiFontDescSize.sp
            )
            Text(
                text = endLabel.orEmpty(),
                color = ComposeColor(AppConfig.uiTextMuted),
                fontSize = AppConfig.uiFontDescSize.sp
            )
        }
    }
}
