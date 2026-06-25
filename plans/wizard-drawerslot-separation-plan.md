# Separation of Concerns — Wizard Pages + DrawerSlot Abstraction

> **Created:** 2026-06-25
> **Scope:** Extract wizard step content from `WizardDrawer.kt` to independent composables in `markers/wizard/` package. Replace 9 hand-coded `AnimatedVisibility` blocks in `OverlayLayer.kt` with a reusable `DrawerSlot` composable.

---

## Part A — Wizard Architecture

### Target Structure

```
app/src/main/java/ykws/android/maro/ui/markers/wizard/
├── WizardDrawer.kt              ← thin shell: top bar + content area + button row
├── WizardTopBar.kt              ← extracted from WizardDrawer.kt
├── WizardButtonRow.kt           ← extracted from WizardDrawer.kt
└── steps/
    ├── TypeSelectStep.kt        ← extracted, made internal/public
    ├── PositionStep.kt          ← extracted
    ├── SliderStep.kt            ← extracted (Radius + Proximity share this)
    └── TextInputStep.kt         ← extracted (Title + Description share this)
```

### WizardDrawer After Extraction

```kotlin
@Composable
fun WizardDrawer(
    viewModel: MarkersViewModel,
    isLandscape: Boolean,
    onCancel: () -> Unit,
    step: WizardStep,             // ← non-nullable parameter
    totalSteps: Int,              // ← computed in OverlayLayer
    stepIndex: Int                // ← computed in OverlayLayer
) {
    Column(fillMaxSize().clip(shape).background(uiSettingsBackground)) {
        WizardTopBar(stepIndex, totalSteps, onCancel)
        
        Box(weight(1f).fillMaxWidth()) {
            AnimatedContent(targetState = step) { currentStep ->
                WizardStepContent(currentStep, viewModel, isLandscape)
            }
        }
        
        WizardButtonRow(
            isFirstStep = stepIndex == 0,
            isLastStep = stepIndex >= totalSteps - 1,
            canFinish = viewModel.canFinish(),
            onPrevious = { viewModel.wizardPrevious() },
            onNext = { viewModel.wizardNext() },
            onFinish = { viewModel.wizardFinish() }
        )
    }
}
```

### What Moves Where

| From | To | What |
|------|----|------|
| `WizardDrawer.kt` private `WizardTopBar` | `WizardTopBar.kt` internal | Top bar composable |
| `WizardDrawer.kt` private `WizardButtonRow` | `WizardButtonRow.kt` internal | Button row composable |
| `WizardDrawer.kt` private `TypeSelectStep` | `steps/TypeSelectStep.kt` | Type selection page |
| `WizardDrawer.kt` private `PositionStep` | `steps/PositionStep.kt` | Map position page |
| `WizardDrawer.kt` private `SliderStep` | `steps/SliderStep.kt` | Radius + Proximity slider |
| `WizardDrawer.kt` private `TextInputStep` | `steps/TextInputStep.kt` | Title + Description inputs |
| `WizardDrawer.kt` private `WizardStepContent` | `WizardDrawer.kt` (kept) | Step dispatcher |
| `WizardDrawer.kt` private `stepSequenceFor` | `MarkersViewModel.kt` or kept | Already in VM + duplicated |

### OverlayLayer Integration

```kotlin
// In OverlayLayer — Wizard section:
val activeStep = wizardStep  // WizardStep? from MapScreen
if (showWizard && activeStep != null) {
    val seq = stepSequenceFor(formType)  // from markersViewModel
    val stepIndex = seq.indexOf(activeStep)
    val totalSteps = seq.size
    
    DrawerSlot(
        visible = true,  // already guarded by if
        alignment = if (isLandscape) CenterStart else BottomCenter,
        width = if (isLandscape) landscapeDashboardWidth else null,
        height = if (!isLandscape) portraitDashboardHeight else null,
        slideDirection = if (isLandscape) LEFT else UP,
        shadowEdge = if (isLandscape) RIGHT else TOP,
        offset = if (isLandscape) 0.dp else keyboardOffsetDp
    ) {
        WizardDrawer(
            viewModel = markersViewModel,
            isLandscape = isLandscape,
            onCancel = onWizardCancel,
            step = activeStep,
            totalSteps = totalSteps,
            stepIndex = stepIndex
        )
    }
}
```

---

## Part B — DrawerSlot Abstraction

### The Unified Pattern

Every drawer currently has this copy-pasted structure:

```kotlin
AnimatedVisibility(
    visible = showXxx,
    modifier = Modifier.align(...).size(...),
    enter = slideIn(spring(1f,350f)) { dir } + fadeIn(80),
    exit = slideOut(tween(150)) { dir } + fadeOut(150)
) {
    Box(fillMaxSize().offset(...).drawBehind { gradient })  // shadow
    XxxDrawer(...)                                           // content
}
```

### DrawerSlot — One Function, Any Drawer

```kotlin
enum class SlideDirection { FROM_RIGHT, FROM_LEFT, FROM_BOTTOM, FADE_ONLY }

@Composable
fun DrawerSlot(
    visible: Boolean,
    modifier: Modifier = Modifier,   // alignment + sizing
    slideDirection: SlideDirection,
    shadowEdge: ShadowEdge? = null,  // LEFT, RIGHT, TOP, null = no shadow
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = buildEnterAnim(slideDirection),
        exit = buildExitAnim(slideDirection)
    ) {
        if (shadowEdge != null) {
            ShadowGradient(shadowEdge)
        }
        content()
    }
}
```

### ShadowEdge Enum

```kotlin
enum class ShadowEdge { LEFT, RIGHT, TOP }

@Composable
fun ShadowGradient(edge: ShadowEdge) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(offsetFor(edge))
            .drawBehind { gradientFor(edge) }
    )
}
```

### Usage — OverlayLayer Becomes Declarative

```kotlin
// Scrim
DrawerSlot(
    visible = showScrim,
    slideDirection = FADE_ONLY,
    shadowEdge = null,
    modifier = Modifier.fillMaxSize()
) {
    Box(fillMaxSize().background(Black@32%))
}

// MenuDrawer
DrawerSlot(
    visible = showTrackDrawer,
    slideDirection = FROM_RIGHT,
    shadowEdge = LEFT,
    modifier = Modifier.align(TopEnd).fillMaxWidth(0.75f).fillMaxHeight()
) {
    MenuDrawerOverlay(isOpen = true, ...)
}

// Wizard (portrait)
DrawerSlot(
    visible = showWizard && activeStep != null,
    slideDirection = FROM_BOTTOM,
    shadowEdge = TOP,
    modifier = Modifier.align(BottomCenter).fillMaxWidth().height(portraitDashboardHeight).offset(keyboardOffsetDp)
) {
    WizardDrawer(step = activeStep, ...)
}

// ... etc for all 6 drawers
```

### Before vs After — Line Count

| Before | After |
|--------|-------|
| 9× hand-coded `AnimatedVisibility` blocks (~30 lines each) = ~270 lines | 9× `DrawerSlot` calls (~8 lines each) = ~72 lines |
| Shadow gradient duplicated 9 times | 1 `ShadowGradient` composable |

---

## Implementation Plan

### Step 1 — Fix Wizard Blank (prerequisite)
- `WizardDrawer` receives `step` as non-null parameter (no `?: return`)
- `OverlayLayer` guards with `showWizard && activeStep != null`

### Step 2 — Create DrawerSlot Abstraction
- New file: `OverlayLayer.kt` now imports from new `DrawerSlot.kt`
- `DrawerSlot` composable with `SlideDirection` enum + `ShadowEdge` enum
- `buildEnterAnim` / `buildExitAnim` helper functions
- `ShadowGradient` composable

### Step 3 — Extract Wizard Steps
- Create `markers/wizard/` package
- Move `WizardTopBar`, `WizardButtonRow` to separate files
- Move step composables to `steps/` package
- Keep `WizardDrawer.kt` as thin shell

### Step 4 — Rewrite OverlayLayer with DrawerSlot
- Replace all 9 `AnimatedVisibility` blocks with `DrawerSlot` calls
- Test each drawer individually

### Files

| File | Action |
|------|--------|
| `OverlayLayer.kt` | Rewrite using `DrawerSlot` |
| `DrawerSlot.kt` | **NEW** — reusable drawer slot |
| `WizardDrawer.kt` | Modify — receive `step` as parameter |
| `WizardTopBar.kt` | **NEW** — extracted |
| `WizardButtonRow.kt` | **NEW** — extracted |
| `steps/TypeSelectStep.kt` | **NEW** — extracted |
| `steps/PositionStep.kt` | **NEW** — extracted |
| `steps/SliderStep.kt` | **NEW** — extracted |
| `steps/TextInputStep.kt` | **NEW** — extracted |
