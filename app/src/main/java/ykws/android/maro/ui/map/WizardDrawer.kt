package ykws.android.maro.ui.map

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ykws.android.maro.R
import ykws.android.maro.config.AppConfig
import ykws.android.maro.ui.components.ConfirmAction
import ykws.android.maro.ui.components.ConfirmActionButton
import ykws.android.maro.ui.components.ConfirmActionRole
import ykws.android.maro.ui.components.DrawerScaffold
import ykws.android.maro.ui.markers.wizard.steps.PositionStep
import ykws.android.maro.ui.markers.wizard.steps.RoutingCostStep
import ykws.android.maro.ui.markers.wizard.steps.SliderStep
import ykws.android.maro.ui.markers.wizard.steps.TextInputStep
import ykws.android.maro.ui.markers.wizard.steps.TypeSelectStep

// ─────────────────────────────────────────────────────────────────────────────
// Public composable — WizardDrawer
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Step-by-step wizard for marker creation and editing.
 *
 * It is the drawer shell the selected-item dashboards use — the shared [DrawerScaffold] — rather than
 * a frame of its own: the header carries the mode's title and, at its trailing edge, the dot
 * progress; the body carries the step; the footer carries the three actions.
 *
 * The frame is the dashboards' frame. In portrait the panel wraps its card, floored at
 * [portraitDashboardHeight] so it is never shorter than the dashboard it replaces, and it is
 * bottom-anchored by the scaffold; in landscape it is the full-height left column with its content
 * bottom-anchored above the buttons.
 *
 * @param viewModel                The [MarkersViewModel] driving the wizard.
 * @param isLandscape              Whether the device is in landscape orientation.
 * @param onCancel                 Called when the wizard is dismissed (Cancel / back).
 * @param step                     The current wizard step (non-null, guaranteed by caller).
 * @param totalSteps               Total number of steps in the sequence.
 * @param stepIndex                0-based index of the current step.
 * @param portraitDashboardHeight  The dashboard height the panel floors at in portrait.
 */
@Composable
fun WizardDrawer(
    viewModel: MarkersViewModel,
    isLandscape: Boolean,
    onCancel: () -> Unit,
    step: WizardStep,
    totalSteps: Int,
    stepIndex: Int,
    portraitDashboardHeight: Dp
) {
    // ── Keyboard: none of the wizard's own. The window takes the platform's pan, exactly as the
    // track card's inline fields do, so the content is pushed up and the buttons ride clear of the
    // keyboard with nothing lifted by hand. (P7a, 2026-09-26.)
    BackHandler { onCancel() }

    val isLastStep = stepIndex >= totalSteps - 1
    val isFirstStep = stepIndex <= 0

    val drawerShape = if (isLandscape) RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
        else RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)

    // Create or edit — the drawer state already holds which, and the header says it.
    val drawerState by viewModel.drawerState.collectAsState()
    val title = if (drawerState is MarkerDrawerState.Editing) stringResource(R.string.wizard_title_edit)
        else stringResource(R.string.wizard_title_create)

    DrawerScaffold(
        title = title,
        onClose = onCancel,
        headerHorizontalPadding = 12.dp,
        headerVerticalPadding = 12.dp,
        headerActions = { WizardStepDots(stepIndex = stepIndex, totalSteps = totalSteps) },
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp),
        scrollable = true,
        suppressOverscrollWhenFits = true,
        // P7: the frame keeps its height and everything inside it stacks at the frame's bottom —
        // the card directly above the footer, the slack between the header and the card.
        bottomAnchoredContent = true,
        wrapContent = !isLandscape,
        wrapContentMinHeight = if (isLandscape) 0.dp else portraitDashboardHeight,
        statusBarsInset = isLandscape,
        shape = drawerShape,
        footer = {
            WizardActions(
                isFirstStep = isFirstStep,
                isLastStep = isLastStep,
                canFinish = viewModel.canFinish(),
                onPrevious = { viewModel.wizardPrevious() },
                onNext = { viewModel.wizardNext() },
                onFinish = { viewModel.wizardFinish() }
            )
        }
    ) {
        val forward = viewModel.wizardForward
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                if (forward) {
                    (slideInHorizontally { it } + fadeIn(tween(200)))
                        .togetherWith(slideOutHorizontally { -it } + fadeOut(tween(150)))
                } else {
                    (slideInHorizontally { -it } + fadeIn(tween(200)))
                        .togetherWith(slideOutHorizontally { it } + fadeOut(tween(150)))
                }
            },
            label = "wizardStep"
        ) { currentStep ->
            WizardStepContent(
                step = currentStep,
                viewModel = viewModel,
                isLandscape = isLandscape
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// WizardStepContent — dispatches to step-specific composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WizardStepContent(
    step: WizardStep,
    viewModel: MarkersViewModel,
    isLandscape: Boolean
) {
    when (step) {
        is WizardStep.TypeSelect -> TypeSelectStep(viewModel)
        is WizardStep.Position -> PositionStep(viewModel, isCorridorP1 = false)
        is WizardStep.PositionP2 -> PositionStep(viewModel, isCorridorP1 = false)
        is WizardStep.Radius -> {
            val form by viewModel.createForm.collectAsState()
            val isCorridor = form.type == MarkerType.CORRIDOR
            SliderStep(
                title = if (isCorridor) stringResource(R.string.wizard_slider_width)
                        else stringResource(R.string.wizard_slider_radius),
                valueM = if (isCorridor) form.widthM else form.radiusM,
                range = 0.0..1000.0,
                step = 25.0,
                unit = "m",
                onValueChange = { v ->
                    viewModel.updateForm {
                        if (isCorridor) it.copy(widthM = v) else it.copy(radiusM = v)
                    }
                },
                comment = if (isCorridor) stringResource(R.string.wizard_slider_width_comment)
                          else stringResource(R.string.wizard_slider_radius_comment)
            )
        }
        is WizardStep.Proximity -> {
            val form by viewModel.createForm.collectAsState()
            SliderStep(
                title = stringResource(R.string.wizard_slider_proximity),
                valueM = form.proximityOverrideM.toDoubleOrNull() ?: 100.0,
                range = 0.0..1000.0,
                step = 25.0,
                unit = "m",
                onValueChange = { v ->
                    viewModel.updateForm { it.copy(proximityOverrideM = v.toLong().toString()) }
                },
                comment = stringResource(R.string.wizard_slider_proximity_comment)
            )
        }
        is WizardStep.Title -> {
            val form by viewModel.createForm.collectAsState()
            TextInputStep(
                label = stringResource(R.string.wizard_field_name),
                value = form.name,
                singleLine = true,
                onValueChange = { v -> viewModel.updateForm { it.copy(name = v) } },
                isLandscape = isLandscape
            )
        }
        is WizardStep.Description -> TextInputStep(
            label = stringResource(R.string.wizard_field_description),
            value = viewModel.createForm.collectAsState().value.description,
            singleLine = false,
            onValueChange = { v -> viewModel.updateForm { it.copy(description = v) } },
            isLandscape = isLandscape
        )
        is WizardStep.RoutingCost -> RoutingCostStep(viewModel)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Header trailing slot and footer
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The step progress: one dot per step, filled up to the current one. It lives in the drawer header's
 * trailing slot, reading the step state, so nothing else has to know which step is current.
 */
@Composable
private fun WizardStepDots(stepIndex: Int, totalSteps: Int) {
    val accent = ComposeColor(AppConfig.uiAccent)
    val divider = ComposeColor(AppConfig.uiDividerColor)
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until totalSteps) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (i <= stepIndex) accent else divider)
            )
        }
    }
}

/**
 * The wizard's three actions, drawn by the app's shared action button so the enabled and disabled
 * faces are the family's one implementation: the accent marks the single enabled forward action —
 * Next until the last step, Finish on it — and everything else is the outlined role, with a disabled
 * action wearing the component's own dead face.
 *
 * The row's own padding and gap are the footer's, since the shared button brings neither.
 */
@Composable
private fun WizardActions(
    isFirstStep: Boolean,
    isLastStep: Boolean,
    canFinish: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onFinish: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ConfirmActionButton(
            action = ConfirmAction(
                label = stringResource(R.string.action_previous),
                role = ConfirmActionRole.SECONDARY,
                enabled = !isFirstStep,
                onClick = onPrevious
            ),
            modifier = Modifier.weight(1f)
        )
        ConfirmActionButton(
            action = ConfirmAction(
                label = stringResource(R.string.action_next),
                role = if (isLastStep) ConfirmActionRole.SECONDARY else ConfirmActionRole.PRIMARY,
                enabled = !isLastStep,
                onClick = onNext
            ),
            modifier = Modifier.weight(1f)
        )
        ConfirmActionButton(
            action = ConfirmAction(
                label = stringResource(R.string.action_finish),
                role = if (isLastStep) ConfirmActionRole.PRIMARY else ConfirmActionRole.SECONDARY,
                enabled = canFinish,
                onClick = onFinish
            ),
            modifier = Modifier.weight(1f)
        )
    }
}

