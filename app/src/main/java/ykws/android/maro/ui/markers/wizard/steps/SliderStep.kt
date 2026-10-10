package ykws.android.maro.ui.markers.wizard.steps

import androidx.compose.runtime.Composable
import ykws.android.maro.ui.components.CardArea
import ykws.android.maro.ui.components.ScaledSliderRow

/**
 * Reusable slider step for Radius and Proximity.
 *
 * The step is a [CardArea] holding one [ScaledSliderRow] — the settings card and the settings slider row
 * (`ui-component-guidelines` §2.0/§2.2) carrying the **×10 range toggle**: with it off the track runs
 * [baseRange] (0–250 m, fine), with it on the ceiling is `baseRange.endInclusive × scale` (0–2500 m).
 * The toggle's state is the caller's, so the step keeps its own and the row draws no state of its own.
 *
 * The wizard's tight card shell and this function's own row composition are retired with it.
 */
@Composable
internal fun SliderStep(
    title: String,
    valueM: Double,
    baseRange: ClosedFloatingPointRange<Double>,
    step: Double,
    unit: String,
    scaled: Boolean,
    onScaledChange: (Boolean) -> Unit,
    onValueChange: (Double) -> Unit,
    comment: String? = null,
    scale: Double = 10.0
) {
    CardArea {
        ScaledSliderRow(
            label = title,
            description = comment,
            value = valueM.toFloat(),
            baseRange = baseRange.start.toFloat()..baseRange.endInclusive.toFloat(),
            stepM = step.toFloat(),
            unit = unit,
            scaled = scaled,
            onScaledChange = onScaledChange,
            onValueChange = { onValueChange(it.toDouble()) },
            scale = scale.toFloat()
        )
    }
}
