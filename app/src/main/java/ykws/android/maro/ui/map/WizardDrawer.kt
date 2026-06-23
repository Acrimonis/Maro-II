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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ykws.android.maro.config.AppConfig
import ykws.android.maro.data.model.LatLng

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
 */
@Composable
fun WizardDrawer(
    viewModel: MarkersViewModel,
    isLandscape: Boolean,
    onCancel: () -> Unit
) {
    val wizardStep by viewModel.wizardStep.collectAsState()
    val form by viewModel.createForm.collectAsState()
    val step = wizardStep ?: return

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

    // Step title
    val stepTitle = when (step) {
        is WizardStep.TypeSelect -> "Select Type"
        is WizardStep.Position -> if (form.type == MarkerType.CORRIDOR) "Point 1" else "Position"
        is WizardStep.PositionP2 -> "Point 2"
        is WizardStep.Radius -> if (form.type == MarkerType.CORRIDOR) "Width" else "Radius"
        is WizardStep.Proximity -> "Proximity"
        is WizardStep.Title -> "Name"
        is WizardStep.Description -> "Description"
    }

    // Determine step index and last-step status
    val seq = stepSequenceFor(form.type)
    val stepIndex = seq.indexOf(step)
    val isLastStep = stepIndex >= seq.lastIndex
    val isFirstStep = stepIndex <= 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ComposeColor(AppConfig.uiSettingsBackground))
    ) {
        // ── Top bar: Cancel ← + step title ──────────────────────────────
        WizardTopBar(title = stepTitle, onCancel = onCancel)

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
// WizardTopBar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WizardTopBar(title: String, onCancel: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onCancel,
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(ComposeColor(AppConfig.uiSettingsSwitchTrackInactive))
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Cancel",
                tint = ComposeColor(AppConfig.uiSettingsTextPrimary),
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            color = ComposeColor(AppConfig.uiSettingsTextPrimary),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
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
// WizardButtonRow — Previous / Next / Finish
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WizardButtonRow(
    isFirstStep: Boolean,
    isLastStep: Boolean,
    canFinish: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onFinish: () -> Unit
) {
    val btnBg = ComposeColor(AppConfig.buttonActionBgColor)
    val btnFg = ComposeColor(AppConfig.buttonActionIconColor)
    val inactiveBg = ComposeColor(AppConfig.uiSettingsSwitchTrackInactive)
    val textPrimary = ComposeColor(AppConfig.uiSettingsTextPrimary)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Previous (hidden on first step)
        if (!isFirstStep) {
            Button(
                onClick = onPrevious,
                modifier = Modifier.weight(1f).height(40.dp),
                colors = ButtonDefaults.buttonColors(containerColor = inactiveBg),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text("Previous", fontSize = 13.sp, color = textPrimary)
            }
        } else {
            Spacer(Modifier.weight(1f))
        }

        // Next (hidden on last step)
        if (!isLastStep) {
            Button(
                onClick = onNext,
                modifier = Modifier.weight(1f).height(40.dp),
                colors = ButtonDefaults.buttonColors(containerColor = btnBg),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text("Next", fontSize = 13.sp, color = btnFg)
            }
        }

        // Finish (always present, dimmed when invalid)
        Button(
            onClick = onFinish,
            modifier = Modifier
                .then(if (!canFinish) Modifier.alpha(0.4f) else Modifier)
                .weight(1f)
                .height(40.dp),
            colors = ButtonDefaults.buttonColors(containerColor = btnBg),
            shape = RoundedCornerShape(6.dp),
            enabled = canFinish
        ) {
            Text("Finish", fontSize = 13.sp, color = btnFg)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Step content composables
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Type selection step — three tappable cards: Pin, Circle, Corridor.
 */
@Composable
private fun TypeSelectStep(viewModel: MarkersViewModel) {
    val form by viewModel.createForm.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "Choose a marker type",
            color = ComposeColor(AppConfig.uiSettingsTextPrimary),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )

        TypeCard(
            title = "Pin",
            description = "A single point on the map",
            icon = "📍",
            selected = form.type == MarkerType.PIN,
            onClick = { viewModel.updateForm { it.copy(type = MarkerType.PIN) } }
        )
        TypeCard(
            title = "Circle (Zone)",
            description = "A circular area with a defined radius",
            icon = "⭕",
            selected = form.type == MarkerType.CIRCLE,
            onClick = { viewModel.updateForm { it.copy(type = MarkerType.CIRCLE) } }
        )
        TypeCard(
            title = "Corridor",
            description = "A linear corridor between two points",
            icon = "↔️",
            selected = form.type == MarkerType.CORRIDOR,
            onClick = { viewModel.updateForm { it.copy(type = MarkerType.CORRIDOR) } }
        )
    }
}

@Composable
private fun TypeCard(
    title: String,
    description: String,
    icon: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (selected)
        ComposeColor(AppConfig.buttonActionBgColor)
    else
        ComposeColor(AppConfig.uiSettingsTextMuted).copy(alpha = 0.3f)

    val bgColor = if (selected)
        ComposeColor(AppConfig.buttonActionBgColor).copy(alpha = 0.08f)
    else
        ComposeColor(AppConfig.uiSettingsCardBackground)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .then(
                if (selected) Modifier.background(bgColor)
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, fontSize = 28.sp)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                title,
                color = ComposeColor(AppConfig.uiSettingsTextPrimary),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                description,
                color = ComposeColor(AppConfig.uiSettingsTextMuted),
                fontSize = 12.sp
            )
        }
    }
}

/**
 * Position step — instruction text. Map stays interactive behind.
 * The actual position tracking is done by the LaunchedEffect in MapScreen.
 */
@Composable
private fun PositionStep(viewModel: MarkersViewModel, isCorridorP1: Boolean) {
    val form by viewModel.createForm.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Drag the map to position the marker",
            color = ComposeColor(AppConfig.uiSettingsTextPrimary),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "The crosshair shows your selection.\nTap Next when ready.",
            color = ComposeColor(AppConfig.uiSettingsTextMuted),
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )

        // Show current position coordinates for reference
        form.position?.let { pos ->
            Spacer(Modifier.height(12.dp))
            Text(
                text = "%.4f, %.4f".format(pos.latitude, pos.longitude),
                color = ComposeColor(AppConfig.uiSettingsTextMuted),
                fontSize = 11.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        }
    }
}

/**
 * Reusable slider step for Radius and Proximity.
 * Visual style matches [BoatSizeSlider]: card background, row title+value,
 * accent-coloured slider.
 */
@Composable
private fun SliderStep(
    title: String,
    valueM: Double,
    range: ClosedFloatingPointRange<Double>,
    step: Double,
    unit: String,
    onValueChange: (Double) -> Unit
) {
    val accent = ComposeColor(AppConfig.uiSettingsAccent)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ComposeColor(AppConfig.uiSettingsCardBackground))
            .padding(horizontal = 16.dp, vertical = 12.dp)
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
        Spacer(Modifier.height(4.dp))
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

/**
 * Text input step for Title and Description.
 *
 * On focus: select-all existing text, keyboard opens.
 * Landscape: content is positioned in the upper portion (no offset needed).
 */
@Composable
private fun TextInputStep(
    label: String,
    value: String,
    singleLine: Boolean,
    onValueChange: (String) -> Unit,
    isLandscape: Boolean
) {
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (isLandscape) {
                    // Landscape: position text in upper portion, above keyboard zone
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                } else {
                    Modifier.padding(horizontal = 12.dp, vertical = 16.dp)
                }
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isLandscape) {
            Spacer(Modifier.height(24.dp))
        }

        Text(
            label,
            color = ComposeColor(AppConfig.uiSettingsTextPrimary),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (singleLine) Modifier.height(56.dp) else Modifier.height(120.dp)),
            colors = drawerTextFieldColors(),
            textStyle = androidx.compose.ui.text.TextStyle(
                fontSize = 14.sp,
                color = ComposeColor(AppConfig.uiSettingsTextPrimary)
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = if (singleLine) ImeAction.Next else ImeAction.Default
            ),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.clearFocus() },
                onDone = { focusManager.clearFocus() }
            ),
            placeholder = {
                Text(
                    if (singleLine) "e.g. My marker" else "Optional notes…",
                    color = ComposeColor(AppConfig.uiSettingsTextMuted).copy(alpha = 0.5f),
                    fontSize = 14.sp
                )
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun drawerTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = ComposeColor(AppConfig.buttonActionBgColor).copy(alpha = 0.5f),
    unfocusedBorderColor = ComposeColor(AppConfig.uiSettingsTextMuted).copy(alpha = 0.3f),
    cursorColor = ComposeColor(AppConfig.uiSettingsTextPrimary)
)

/** Returns the step sequence for the given marker type (mirror of VM method for UI use). */
private fun stepSequenceFor(type: MarkerType): List<WizardStep> = when (type) {
    MarkerType.PIN -> listOf(
        WizardStep.TypeSelect, WizardStep.Position,
        WizardStep.Proximity, WizardStep.Title, WizardStep.Description
    )
    MarkerType.CIRCLE -> listOf(
        WizardStep.TypeSelect, WizardStep.Position,
        WizardStep.Radius, WizardStep.Proximity, WizardStep.Title, WizardStep.Description
    )
    MarkerType.CORRIDOR -> listOf(
        WizardStep.TypeSelect, WizardStep.Position,
        WizardStep.PositionP2, WizardStep.Radius,
        WizardStep.Proximity, WizardStep.Title, WizardStep.Description
    )
}

/** Unwraps the (possibly localisation-wrapped) [Context] chain to the host [Activity]. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
