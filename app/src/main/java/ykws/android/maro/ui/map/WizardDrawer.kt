package ykws.android.maro.ui.map

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ykws.android.maro.config.AppConfig
import ykws.android.maro.ui.markers.wizard.WizardButtonRow
import ykws.android.maro.ui.markers.wizard.WizardTopBar
import ykws.android.maro.ui.markers.wizard.steps.PositionStep
import ykws.android.maro.ui.markers.wizard.steps.SliderStep
import ykws.android.maro.ui.markers.wizard.steps.TextInputStep
import ykws.android.maro.ui.markers.wizard.steps.TypeSelectStep

// ─────────────────────────────────────────────────────────────────────────────
// Public composable — WizardDrawer
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Step-by-step wizard for marker creation and editing.
 * Replaces the old single-form [CreationContent] / [EditContent].
 *
 * Slides in place of the dashboard — same size, same position.
 * Map stays visible and draggable during point-placement steps. No scrim.
 *
 * @param viewModel     The [MarkersViewModel] driving the wizard.
 * @param isLandscape   Whether the device is in landscape orientation.
 * @param onCancel      Called when the wizard is dismissed (Cancel / back).
 * @param step          The current wizard step (non-null, guaranteed by caller).
 * @param totalSteps    Total number of steps in the sequence.
 * @param stepIndex     0-based index of the current step.
 */
@Composable
fun WizardDrawer(
    viewModel: MarkersViewModel,
    isLandscape: Boolean,
    onCancel: () -> Unit,
    step: WizardStep,
    totalSteps: Int,
    stepIndex: Int
) {
    val context = LocalContext.current
    val activity = context.findActivity()

    // ── Keyboard mode toggle: adjustNothing on text steps ───────────────
    val isTextStep = step is WizardStep.Title || step is WizardStep.Description
    DisposableEffect(isTextStep) {
        if (isTextStep && activity != null) {
            activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        }
        onDispose {
            if (activity != null) {
                activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
            }
        }
    }

    // Back handler
    BackHandler { onCancel() }

    // Determine step index and last-step status from parameters
    val isLastStep = stepIndex >= totalSteps - 1
    val isFirstStep = stepIndex <= 0

    val drawerShape = if (isLandscape) RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
        else RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(drawerShape)
            .background(ComposeColor(AppConfig.uiSettingsBackground))
    ) {
            // ── Top bar: Cancel ← + title + dot progress ────────────────────
            WizardTopBar(
            stepIndex = stepIndex,
            totalSteps = totalSteps,
            onCancel = onCancel
        )

        // ── Content area with AnimatedContent ────────────────────────────
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            val forward = viewModel.wizardForward
            AnimatedContent(
                targetState = step,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    if (forward) {
                        (slideInHorizontally { it } + androidx.compose.animation.fadeIn(tween(200)))
                            .togetherWith(slideOutHorizontally { -it } + androidx.compose.animation.fadeOut(tween(150)))
                    } else {
                        (slideInHorizontally { -it } + androidx.compose.animation.fadeIn(tween(200)))
                            .togetherWith(slideOutHorizontally { it } + androidx.compose.animation.fadeOut(tween(150)))
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

        // ── Bottom button row ───────────────────────────────────────────
        WizardButtonRow(
            isFirstStep = isFirstStep,
            isLastStep = isLastStep,
            canFinish = viewModel.canFinish(),
            onPrevious = { viewModel.wizardPrevious() },
            onNext = { viewModel.wizardNext() },
            onFinish = { viewModel.wizardFinish() }
        )
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
                title = if (isCorridor) "Width" else "Radius",
                valueM = if (isCorridor) form.widthM else form.radiusM,
                range = 0.0..500.0,
                step = 5.0,
                unit = "m",
                onValueChange = { v ->
                    viewModel.updateForm {
                        if (isCorridor) it.copy(widthM = v) else it.copy(radiusM = v)
                    }
                }
            )
        }
        is WizardStep.Proximity -> {
            val form by viewModel.createForm.collectAsState()
            val defaultValue = when (form.type) {
                MarkerType.PIN -> AppConfig.markerProximityPinM
                MarkerType.CIRCLE -> form.radiusM
                MarkerType.CORRIDOR -> form.widthM
            }
            val maxProximity = when (form.type) {
                MarkerType.PIN -> 500.0
                MarkerType.CIRCLE -> 3.0 * form.radiusM
                MarkerType.CORRIDOR -> 3.0 * form.widthM
            }
            SliderStep(
                title = "Proximity",
                valueM = form.proximityOverrideM.toDoubleOrNull() ?: defaultValue,
                range = 0.0..maxProximity,
                step = 5.0,
                unit = "m",
                onValueChange = { v ->
                    viewModel.updateForm { it.copy(proximityOverrideM = v.toLong().toString()) }
                }
            )
        }
        is WizardStep.Title -> TextInputStep(
            label = "Name",
            value = viewModel.createForm.collectAsState().value.name,
            singleLine = true,
            onValueChange = { v -> viewModel.updateForm { it.copy(name = v) } },
            isLandscape = isLandscape
        )
        is WizardStep.Description -> TextInputStep(
            label = "Description",
            value = viewModel.createForm.collectAsState().value.description,
            singleLine = false,
            onValueChange = { v -> viewModel.updateForm { it.copy(description = v) } },
            isLandscape = isLandscape
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

/** Unwraps the (possibly localisation-wrapped) [Context] chain to the host [Activity]. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
