package ykws.android.maro.ui.markers.wizard.steps

import androidx.compose.runtime.Composable
import ykws.android.maro.ui.components.CardArea
import ykws.android.maro.ui.components.SliderRow

/**
 * Reusable slider step for Radius, Proximity and Routing cost.
 *
 * The step is a [CardArea] holding one [SliderRow] — the settings card and the settings slider row
 * (`ui-component-guidelines` §2.0/§2.2), so the label, the description, the value, the track and the
 * two end readings all sit at the container's own inset and the step draws no surface of its own.
 * The wizard's tight card shell and this function's own row composition are retired with it.
 *
 * The three label parameters override the wording derived from [valueM], [unit] and [range]; their
 * defaults reproduce it exactly, so Radius and Proximity are unchanged. [comment] is the row's
 * optional description.
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
    CardArea {
        SliderRow(
            label = title,
            description = comment,
            valueLabel = valueLabel ?: "${valueM.toLong()} $unit",
            value = valueM.toFloat(),
            valueRange = range.start.toFloat()..range.endInclusive.toFloat(),
            steps = ((range.endInclusive - range.start) / step).toInt() - 1,
            onValueChange = { onValueChange(it.toDouble()) },
            startLabel = startLabel ?: "0 $unit",
            endLabel = endLabel ?: "${range.endInclusive.toLong()} $unit"
        )
    }
}
