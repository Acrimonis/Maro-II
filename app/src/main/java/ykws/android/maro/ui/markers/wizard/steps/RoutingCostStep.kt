package ykws.android.maro.ui.markers.wizard.steps

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ykws.android.maro.R
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.markers.ROUTING_COST_BLOCKED
import ykws.android.maro.data.model.markers.routingCostForSlider
import ykws.android.maro.data.model.markers.validRoutingCost
import ykws.android.maro.ui.components.CardArea
import ykws.android.maro.ui.components.MultiSelectRow
import ykws.android.maro.ui.components.SectionRow
import ykws.android.maro.ui.components.SliderControl
import ykws.android.maro.ui.map.MarkerType
import ykws.android.maro.ui.map.MarkersViewModel

/**
 * The share of the step's width the **Routing cost** side takes; the **Route role** side takes the
 * rest. Not halves: the slider is the wider control, and the options share the narrower column.
 */
private const val COST_COLUMN_WEIGHT = 0.56f

/** Breathing room between a side's heading block and its control, before the slack that bottom-aligns. */
private const val HEADING_TO_CONTROL_GAP_DP = 8

/**
 * The two route roles a marker may be offered for — option keys of the multi-select, never an exclusive
 * choice: the marker carries one boolean per role and any combination is a state.
 */
private enum class RouteRoleOption { ORIGIN, DESTINATION }

/**
 * Routing-cost step — one `CardArea` holding two sections side by side, divided by the vertical rule
 * (`ui-component-guidelines` §2.14): **Route role** on the left, **Routing cost** on the right.
 *
 * **A Pin carries no cost** — it is a point and has no area to price — so for a Pin the cost side is
 * hidden and the step shows the Route role alone, full width. A Circle and a Corridor keep both sides.
 *
 * Each side leads with its heading and a one-line comment, then gives the slack to a weighted spacer, so
 * the two controls sit on one bottom line although the stacked options and the slider differ in height,
 * while the headings stay aligned at the top.
 *
 * The left section is the shared `MultiSelectRow` — the options side by side under short labels, so both
 * fit the narrower column — and the right is the bare `SliderControl` under a right-aligned value line,
 * whose `0` is the Off position and the only clear and whose top step is the wall: the value line reads
 * the Off label at `0`, a price in between, and the wall's own words at `ROUTING_COST_BLOCKED`, while
 * the track's two ends carry the same pair of names — Off under its left end, the wall under its right —
 * and the value is mapped through [routingCostForSlider].
 *
 * No wizard step is added, so neither step sequence changes.
 */
@Composable
internal fun RoutingCostStep(viewModel: MarkersViewModel) {
    val form by viewModel.createForm.collectAsState()

    CardArea {
        if (form.type == MarkerType.PIN) {
            // A Pin has no area to price, so it carries **no cost**: the step shows the Route role alone.
            Column(modifier = Modifier.fillMaxWidth()) {
                RouteRoleSection(viewModel)
            }
        } else {
            SectionRow(
                weightLeft = 1f - COST_COLUMN_WEIGHT,
                left = { RouteRoleSection(viewModel) },
                right = { RouteCostSection(viewModel) }
            )
        }
    }
}

/** The **Route role** side — the heading and the shared multi-select, in whatever column holds it. */
@Composable
private fun ColumnScope.RouteRoleSection(viewModel: MarkersViewModel) {
    val form by viewModel.createForm.collectAsState()
    StepSectionHeading(
        title = stringResource(R.string.wizard_route_role_title),
        description = stringResource(R.string.wizard_route_role_description)
    )
    Spacer(modifier = Modifier.weight(1f))
    Spacer(modifier = Modifier.height(HEADING_TO_CONTROL_GAP_DP.dp))
    MultiSelectRow(
        options = listOf(
            RouteRoleOption.ORIGIN to stringResource(R.string.wizard_route_role_origin),
            RouteRoleOption.DESTINATION to stringResource(R.string.wizard_route_role_destination)
        ),
        isOn = { option ->
            when (option) {
                RouteRoleOption.ORIGIN -> form.routeOrigin
                RouteRoleOption.DESTINATION -> form.routeDestination
            }
        },
        onToggle = { option ->
            viewModel.updateForm { current ->
                when (option) {
                    RouteRoleOption.ORIGIN ->
                        current.copy(routeOrigin = !current.routeOrigin)
                    RouteRoleOption.DESTINATION ->
                        current.copy(routeDestination = !current.routeDestination)
                }
            }
        }
    )
}

/** The **Routing cost** side — the heading, the value line and the slider; never drawn for a Pin. */
@Composable
private fun ColumnScope.RouteCostSection(viewModel: MarkersViewModel) {
    val form by viewModel.createForm.collectAsState()
    val cost = validRoutingCost(form.routingCost) ?: 0
    val offLabel = stringResource(R.string.wizard_routing_cost_unset)
    val blockedLabel = stringResource(R.string.wizard_routing_cost_blocked)
    StepSectionHeading(
        title = stringResource(R.string.wizard_routing_cost_title),
        description = stringResource(R.string.wizard_routing_cost_description)
    )
    Spacer(modifier = Modifier.weight(1f))
    Spacer(modifier = Modifier.height(HEADING_TO_CONTROL_GAP_DP.dp))
    Text(
        text = when (cost) {
            0 -> offLabel
            ROUTING_COST_BLOCKED -> blockedLabel
            else -> cost.toString()
        },
        color = ComposeColor(AppConfig.uiValueText),
        fontSize = AppConfig.uiFontValueSize.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.End,
        modifier = Modifier.fillMaxWidth()
    )
    SliderControl(
        value = cost.toFloat(),
        valueRange = 0f..ROUTING_COST_BLOCKED.toFloat(),
        steps = ROUTING_COST_BLOCKED - 1,
        onValueChange = { v ->
            viewModel.updateForm { it.copy(routingCost = routingCostForSlider(v.toDouble())) }
        },
        startLabel = offLabel,
        endLabel = blockedLabel
    )
}

/**
 * A side's heading block — the 16sp Medium title and 13sp muted comment every row wears. It is the
 * caller's because the two sides draw their controls themselves; the sizes are the row family's (§2.1,
 * §2.2), so a section heading and a row label read alike.
 */
@Composable
private fun StepSectionHeading(title: String, description: String) {
    Text(
        text = title,
        color = ComposeColor(AppConfig.uiTextPrimary),
        fontSize = AppConfig.uiFontToggleSize.sp,
        fontWeight = FontWeight.Medium
    )
    Text(
        text = description,
        color = ComposeColor(AppConfig.uiTextMuted),
        fontSize = AppConfig.uiFontDescSize.sp
    )
}
