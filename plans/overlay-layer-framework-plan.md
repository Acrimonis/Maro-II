# OverlayLayer Framework — Implementation Plan

> **Created:** 2026-06-25
> **Scope:** Self-contained `OverlayLayer` composable — all drawers, Wizard, Settings, and scrim on a unified layer above the main app layout.
> **Principle:** Layer 0 (main layout) is permanent. Layer 1 (overlay) is transient. Any new overlay fits into this framework.

---

## Architecture

```
MapScreen BoxWithConstraints
│
├─ Layer 0: Main App Layout (permanent, always rendered)
│   ├── MapContent + overlays (center marker, cap arrow, regulated zones)
│   ├── DashboardPanel              ← always present
│   ├── Fan buttons + Add Zone + Zoom
│   └── GPS / Track / EarthWater status icons
│
└─ Layer 1: OverlayLayer (transient, self-contained)
    ├── ⬛ Scrim                    ← when any overlay open
    ├── WizardDrawer               ← covers dashboard
    ├── MenuDrawer                 ← slides from right
    ├── MarkerDrawer                ← slides from bottom (portrait) / left (landscape)
    ├── TrackHistory               ← slides from right
    └── Settings                   ← full-screen fade
```

---

## Step 1 — Create `OverlayLayer.kt`

New file: [`OverlayLayer.kt`](../app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt)

Self-contained composable with:

```kotlin
@Composable
fun OverlayLayer(
    // State flags
    showSettings: Boolean,
    showTrackDrawer: Boolean,
    showTrackHistory: Boolean,
    showWizard: Boolean,
    wizardStep: WizardStep?,
    drawerState: MarkerDrawerState,
    
    // Callbacks
    onDismissSettings: () -> Unit,
    onDismissMenu: () -> Unit,
    onDismissTrackHistory: () -> Unit,
    onWizardCancel: () -> Unit,
    onMarkerDrawerClose: () -> Unit,
    
    // Data
    markersViewModel: MarkersViewModel,
    trackViewModel: TrackViewModel,
    // ... per-drawer data
)
```

### Unified Scrim

```kotlin
val showScrim = showTrackDrawer
    || showTrackHistory
    || (showWizard && wizardStep !is WizardStep.Position && wizardStep !is WizardStep.PositionP2)
    || (drawerState !is MarkerDrawerState.Hidden)

if (showScrim) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.32f))
    )
}
```

### Each Drawer — Panel + Entrance Animation Only

| Overlay | Visibility | Animation |
|---------|-----------|-----------|
| Wizard (portrait) | `showWizard` | `slideInVertically(spring 0.6f/300f) + fadeIn(tween(80))` |
| Wizard (landscape) | `showWizard` | `slideInHorizontally(spring 0.6f/300f) + fadeIn(tween(80))` |
| MenuDrawer | `showTrackDrawer` | `slideInHorizontally(spring 0.6f/300f) + fadeIn(tween(80))` |
| MarkerDrawer | `drawerState !is Hidden` | Existing slide + spring (unchanged from drawer) |
| TrackHistory | `showTrackHistory` | `slideInHorizontally(spring 0.6f/300f) + fadeIn(tween(80))` |
| Settings | `showSettings` | `fadeIn(spring 0.6f/300f) + fadeOut(tween(150))` |

---

## Step 2 — Restructure `MapScreen.kt`

### Layer 0: Remove drawer rendering, keep Dashboard always

```kotlin
BoxWithConstraints {
    // ── Layer 0: Main layout ──
    MapContent(...)
    
    // Dashboard — always present, NEVER conditional
    if (isLandscape) {
        DashboardPanel(modifier = Modifier.align(CenterStart).width(...).fillMaxHeight())
    } else {
        DashboardPanel(modifier = Modifier.align(BottomCenter).fillMaxWidth().height(...))
    }
    
    // ── Layer 1: Overlay ──
    OverlayLayer(
        showSettings = showSettings,
        showTrackDrawer = showTrackDrawer,
        showTrackHistory = showTrackHistory,
        showWizard = showWizard,
        wizardStep = wizardStep,
        drawerState = drawerState,
        // ... all callbacks + data
    )
}
```

### Remove from MapScreen
- Wizard AnimatedVisibility + scrim logic (lines ~989-1036)
- MenuDrawer rendering (line ~1098)
- TrackHistory rendering (line ~1112)
- MarkerDrawer rendering (if any)
- Settings AnimatedVisibility (replace with call to OverlayLayer)
- `animateFloatAsState(scrimAlpha)` for Wizard

### Keep in MapScreen
- State variables: `showSettings`, `showTrackDrawer`, `showTrackHistory`, `expandedFanId`
- `wizardStep`, `drawerState` from ViewModels
- All callbacks

---

## Step 3 — Strip Scrim + Shadow from Each Drawer

### MarkerDrawer.kt
- Remove: scrim Box (entire block)
- Remove: shadow Box
- Keep: panel Box + content + BackHandler
- Exit animations: already defined

### MenuDrawerOverlay.kt
- Remove: outer `AnimatedVisibility(fadeIn)` wrapper
- Remove: scrim Box
- Remove: shadow Box
- Keep: panel Box + content + BackHandler
- The inner `AnimatedVisibility` becomes the sole animation

### TrackHistoryOverlay.kt
- Remove: scrim Box
- Remove: shadow Box
- Keep: panel content + BackHandler

### WizardDrawer.kt
- Remove: shadow Box only
- Keep: existing step content + BackHandler + top bar

---

## Step 4 — Animation Specification (All Drawers)

| Event | Parameter | Value |
|-------|-----------|-------|
| Enter slide | `spring(dampingRatio, stiffness)` | `0.6f, 300f` |
| Enter fade | `tween(durationMillis)` | `80` |
| Exit slide | `tween(durationMillis)` | `150` |
| Exit fade | `tween(durationMillis)` | `150` |

Applied consistently in `OverlayLayer` or in each drawer's outermost `AnimatedVisibility`.

---

## What Becomes "Permanent Main Layout"

```
Layer 0 (always rendered):
├── MapContent (map + overlays)
├── DashboardPanel (4-card dashboard)
├── Right-edge controls (fan, add zone, zoom)
├── GPS / Track / EarthWater icons
└── Regulated zone warning strip

Layer 1 (transient, any one at a time):
├── Scrim
├── WizardDrawer
├── MenuDrawer
├── MarkerDrawer
├── TrackHistory
└── Settings
```

## Files

| File | Action |
|------|--------|
| `OverlayLayer.kt` | **NEW** — self-contained overlay composable |
| `MapScreen.kt` | Restructure: Dashboard always rendered, overlay call replaces drawer rendering |
| `MarkerDrawer.kt` | Strip scrim + shadow, keep panel + BackHandler |
| `MenuDrawerOverlay.kt` | Strip outer AnimatedVisibility + scrim + shadow |
| `TrackHistoryOverlay.kt` | Strip scrim + shadow |
| `WizardDrawer.kt` | Strip shadow only |
