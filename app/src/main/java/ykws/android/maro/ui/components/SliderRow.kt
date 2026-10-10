package ykws.android.maro.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ykws.android.maro.R
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
 * **A slider whose range scales by a ×10 toggle** — the label and description on the left, a tight
 * `[☐ ×10][value]` group at the right edge with the **checkbox immediately left of the value**, and the
 * bare [SliderControl] beneath, so the value sits **close to the track** it names rather than floating
 * at the header's far corner.
 *
 * The toggle is the **caller's own state**, off by default: with it off the track runs [baseRange], with
 * it on the ceiling is `baseRange.endInclusive × scale`. Switching it off while the value stands above
 * the base ceiling **clamps** the value through [onValueChange], so the row never shows a value its own
 * track cannot hold. The end reading under the track follows the active ceiling.
 */
@Composable
internal fun ScaledSliderRow(
    label: String,
    description: String? = null,
    value: Float,
    baseRange: ClosedFloatingPointRange<Float>,
    stepM: Float,
    unit: String,
    scaled: Boolean,
    onScaledChange: (Boolean) -> Unit,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit = {},
    scale: Float = 10f
) {
    val ceiling = if (scaled) baseRange.endInclusive * scale else baseRange.endInclusive
    val range = baseRange.start..ceiling
    val shown = value.coerceIn(range)
    val steps = (((ceiling - baseRange.start) / stepM).toInt() - 1).coerceAtLeast(0)
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
        // The trailing group, tight: the ×10 checkbox immediately left of the value.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = scaled,
                onCheckedChange = { on ->
                    onScaledChange(on)
                    // Off with a value above the fine ceiling clamps it, so the row never lies.
                    if (!on && value > baseRange.endInclusive) onValueChange(baseRange.endInclusive)
                },
                colors = CheckboxDefaults.colors(checkedColor = ComposeColor(AppConfig.uiAccent))
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.wizard_range_scale_x10),
                color = ComposeColor(AppConfig.uiTextMuted),
                fontSize = AppConfig.uiFontDescSize.sp
            )
            Spacer(modifier = Modifier.width(AppConfig.uiSpacingLabelControl.dp))
            Text(
                text = "${shown.toLong()} $unit",
                color = ComposeColor(AppConfig.uiValueText),
                fontSize = AppConfig.uiFontValueSize.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
    SliderControl(
        value = shown,
        valueRange = range,
        steps = steps,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        startLabel = "${baseRange.start.toLong()} $unit",
        endLabel = "${ceiling.toLong()} $unit"
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
