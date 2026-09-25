package ykws.android.maro.ui.markers.wizard.steps

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import ykws.android.maro.R
import ykws.android.maro.data.model.markers.routingCostForSlider
import ykws.android.maro.data.model.markers.validRoutingCost
import ykws.android.maro.ui.map.MarkersViewModel

/** Highest cost the slider offers; the 1–9 range guard in the model belongs to `validRoutingCost`. */
private const val ROUTING_COST_MAX = 9

/**
 * Routing-cost step — the wizard's own [SliderStep] over `0.0..9.0` with a one-unit step, so the knob
 * rests on whole numbers and the left end is the Off position.
 *
 * The step keeps the wizard's tight card shell, which [SliderStep] itself draws (8×4dp padding, 12dp
 * radius, `uiCardBackground`); nothing about the sequences, the dispatch or the form seeds moves.
 *
 * `0` is the only clear: the value line reads the Off label there and a plain number above it, the
 * start label is that same Off string, and the value is mapped through [routingCostForSlider].
 */
@Composable
internal fun RoutingCostStep(viewModel: MarkersViewModel) {
    val form by viewModel.createForm.collectAsState()
    val cost = validRoutingCost(form.routingCost) ?: 0
    val offLabel = stringResource(R.string.wizard_routing_cost_unset)

    SliderStep(
        title = stringResource(R.string.wizard_routing_cost_title),
        valueM = cost.toDouble(),
        range = 0.0..ROUTING_COST_MAX.toDouble(),
        step = 1.0,
        unit = "",
        onValueChange = { v ->
            viewModel.updateForm { it.copy(routingCost = routingCostForSlider(v)) }
        },
        comment = stringResource(R.string.wizard_routing_cost_description),
        valueLabel = if (cost == 0) offLabel else cost.toString(),
        startLabel = offLabel,
        endLabel = ROUTING_COST_MAX.toString()
    )
}
