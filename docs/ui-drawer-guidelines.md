# Drawer UI Guidelines

> **Purpose:** Canonical reference for rendering any drawer/panel surface in Maro II.
> **Created:** 2026-06-24 — normalisation pass (I1–I6).
> **Updated:** 2026-06-25 — OverlayLayer + DrawerSlot architecture (I7–I8).

---

## 1. Architecture — Two-Layer System

MapScreen renders two layers inside `BoxWithConstraints`:

```
Layer 0 (permanent, always rendered):
├── MapContent (map + overlays)
├── DashboardPanel (4-card dashboard)
├── Right-edge controls (fan, add zone, zoom)
├── GPS / Track / EarthWater status icons
└── Regulated zone warning strip

Layer 1 (transient, self-contained):
├── Scrim
├── WizardDrawer
├── MenuDrawer
├── MarkerDrawer
├── TrackHistory
├── MarkerManagement
└── Settings
```

Layer 1 is a single composable call: [`OverlayLayer`](../app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt). It owns the unified scrim and all 7 transient surfaces. Each surface is wrapped in a [`DrawerSlot`](../app/src/main/java/ykws/android/maro/ui/map/DrawerSlot.kt) that provides entrance/exit animation and edge shadow.

**Key rule:** Layer 0 components must never be conditional. Dashboard, controls, and status icons are always present. Only Layer 1 surfaces appear/disappear.

---

## 2. DrawerSlot — Reusable Animation + Shadow Wrapper

All drawer animations and shadows are provided by `DrawerSlot`. Individual drawer composables are **pure content** — a `Box` with background, content, and `BackHandler`. They do NOT manage their own `AnimatedVisibility`, scrim, or shadow.

### API

```kotlin
@Composable
fun DrawerSlot(
    visible: Boolean,
    modifier: Modifier = Modifier,         // alignment + sizing
    slideDirection: SlideDirection,
    shadowEdge: ShadowEdge? = null,
    content: @Composable () -> Unit
)
```

### Enums

| Enum | Values | Purpose |
|------|--------|---------|
| `SlideDirection` | `FROM_RIGHT`, `FROM_LEFT`, `FROM_BOTTOM`, `FADE_ONLY` | Which direction the content slides in from |
| `ShadowEdge` | `LEFT`, `RIGHT`, `TOP` | Which edge draws the 8dp gradient shadow |

### Animation Spec

| Event | Parameter | Value |
|-------|-----------|-------|
| Enter slide | `spring(dampingRatio, stiffness)` | `1.0f, 350f` |
| Enter fade | `tween(durationMillis)` | `80` |
| Exit slide | `tween(durationMillis)` | `150` |
| Exit fade | `tween(durationMillis)` | `150` |

### Shadow Gradient

Replaces the invisible `Modifier.shadow()` (black-on-dark has near-zero contrast). An 8dp gradient is drawn on the specified edge via `drawBehind`:

- `LEFT` — transparent→black@18% horizontal, startX=0, endX=8dp
- `RIGHT` — black@18%→transparent horizontal, at right edge
- `TOP` — transparent→black@18% vertical, startY=0, endY=8dp

---

## 3. Surfaces — Quick Reference

| # | Surface | File | Visibility | Slide | Shadow | Alignment |
|---|---------|------|-----------|-------|--------|-----------|
| 1 | Scrim | — (inline in OverlayLayer) | any drawer open (except Wizard position steps) | `FADE_ONLY` | none | `fillMaxSize` |
| 2 | Wizard (landscape) | `WizardDrawer.kt` | `showWizard && step != null` | `FROM_LEFT` | `RIGHT` | `CenterStart`, `landscapeDashboardWidth` |
| 2 | Wizard (portrait) | `WizardDrawer.kt` | `showWizard && step != null` | `FROM_BOTTOM` | `TOP` | `BottomCenter`, full width, `portraitDashboardHeight`, keyboard offset |
| 3 | Menu | `MenuDrawerOverlay.kt` | `showTrackDrawer` | `FROM_RIGHT` | `LEFT` | `TopEnd`, 75% width |
| 4 | Marker (landscape) | `MarkerDrawer.kt` | `drawerState is Viewing/MatchResult` | `FROM_LEFT` | `RIGHT` | `CenterStart`, `landscapeDashboardWidth` |
| 4 | Marker (portrait) | `MarkerDrawer.kt` | `drawerState is Viewing/MatchResult` | `FROM_BOTTOM` | `TOP` | `BottomCenter`, full width, `portraitDashboardHeight` |
| 5 | TrackHistory | `TrackHistoryOverlay.kt` | `showTrackHistory` | `FROM_RIGHT` | `LEFT` | `fillMaxSize` |
| 6 | MarkerManagement | `MarkerManagementOverlay.kt` | `showMarkerManagement` | `FROM_RIGHT` | `LEFT` | `fillMaxSize` |
| 7 | Settings | `SettingsOverlay` (in `MapScreen.kt`) | `showSettings` | `FROM_RIGHT` | `LEFT` | `fillMaxSize` |

### Scrim Formula

```kotlin
val showScrim = showTrackDrawer
    || showTrackHistory
    || showMarkerManagement
    || (showWizard && wizardStep !is WizardStep.Position && wizardStep !is WizardStep.PositionP2)
    || (drawerState is MarkerDrawerState.Viewing || drawerState is MarkerDrawerState.MatchResult)
```

Wizard `Position` / `PositionP2` steps suppress the scrim so the map stays interactive during point placement.

---

## 4. Drawer Composable Contract

Every drawer composable must follow this contract to work with `DrawerSlot`:

1. **No `AnimatedVisibility`** — animation is handled by `DrawerSlot`
2. **No scrim** — the unified scrim is rendered once in `OverlayLayer`
3. **No shadow** — the gradient shadow is drawn by `DrawerSlot.drawBehind`
4. **`BackHandler` inside the composable** — guarded by `isOpen` or `showXxx`
5. **Pure content** — a `Box`/`Column` with `fillMaxSize()`, `clip(shape)`, `.background(uiSettingsBackground)`, then content

### Canonical Drawer Skeleton

```kotlin
@Composable
fun XxxDrawer(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    // ... data params
) {
    if (isOpen) { BackHandler { onDismiss() } }

    val shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(shape)
            .background(Color(AppConfig.uiSettingsBackground))
    ) {
        // content
    }
}
```

---

## 5. How to Add a New Drawer

1. Create the content composable following the [Drawer Composable Contract](#4-drawer-composable-contract)
2. Add a visibility state flag in `MapScreen` (e.g., `var showNewDrawer by remember { mutableStateOf(false) }`)
3. Add a `DrawerSlot` block in `OverlayLayer.kt`:
   ```kotlin
   DrawerSlot(
       visible = showNewDrawer,
       modifier = Modifier.align(Alignment.TopEnd).fillMaxWidth(0.75f).fillMaxHeight(),
       slideDirection = SlideDirection.FROM_RIGHT,
       shadowEdge = ShadowEdge.LEFT
   ) {
       NewDrawer(isOpen = true, onDismiss = onDismissNewDrawer, ...)
   }
   ```
4. Wire the visibility flag and dismiss callback through `OverlayLayer`'s parameter list
5. Update the scrim formula if the new drawer needs a scrim behind it
6. Add a row to the [Surfaces table](#3-surfaces--quick-reference)

---

## 6. Header Tokens

All drawer headers share these tokens:

| Token | Value |
|-------|-------|
| Back button widget | `IconButton` (never `Button`) |
| Back button size | 32dp, `CircleShape` |
| Back button background | `uiSettingsSwitchTrackInactive` |
| Back icon size | 18dp |
| Back icon tint | `uiSettingsTextPrimary` |
| Title font | 17sp, Bold, `uiSettingsTextPrimary` |
| Back→title spacer | `16dp` |
| Header horizontal padding | 24dp (menu, track history); 12dp (wizard, marker viewer) |
| Header vertical padding | 6dp (wizard, marker viewer); 8dp (menu); 3dp (track history) |

```kotlin
// Canonical header pattern
Row(
    modifier = Modifier.fillMaxWidth()
        .padding(horizontal = 24.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically
) {
    IconButton(
        onClick = onClose,
        modifier = Modifier.size(32.dp)
            .clip(CircleShape)
            .background(ComposeColor(AppConfig.uiSettingsSwitchTrackInactive))
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Close",
            tint = ComposeColor(AppConfig.uiSettingsTextPrimary),
            modifier = Modifier.size(18.dp)
        )
    }
    Spacer(Modifier.width(16.dp))
    Text(
        text = title,
        color = ComposeColor(AppConfig.uiSettingsTextPrimary),
        fontSize = 17.sp,
        fontWeight = FontWeight.Bold
    )
}
```

---

## 7. Section Headers in Drawers

Drawer content sections use the same `SectionHeader` token as settings:

| Token | Value |
|-------|-------|
| Color | `uiSettingsAccent` |
| Font | 17sp, Bold, UPPERCASE, 1sp letter-spacing |
| Spacer before first card | `8.dp` |

```kotlin
Text(
    text = "MARKER DETAILS",
    color = ComposeColor(AppConfig.uiSettingsAccent),
    fontSize = 17.sp,
    fontWeight = FontWeight.Bold,
    letterSpacing = 1.sp
)
Spacer(Modifier.height(8.dp))
```

Subsection headers below the drawer title follow `Spacer(12.dp)` after the header row.

---

## 8. Card Pattern

Two padding densities depending on card content type:

| Density | Padding | Use for |
|---------|---------|---------|
| **Wide** | 16×10dp | Menu slide panel — simple toggle/nav rows with single controls |
| **Tight** | 8×4dp | Data-dense cards — track history stats grid, wizard sliders, marker details |

Shared tokens across both densities:

| Token | Value |
|-------|-------|
| Background | `uiCardBackground` |
| Corner radius | 12dp |
| Between cards | `Spacer(8.dp)` |

```kotlin
// Wide (menu slide panel)
Column(
    modifier = Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .background(Color(AppConfig.uiCardBackground))
        .padding(horizontal = 16.dp, vertical = 10.dp)
) { /* simple rows */ }

// Tight (data-dense cards)
Column(
    modifier = Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .background(Color(AppConfig.uiCardBackground))
        .padding(horizontal = 8.dp, vertical = 4.dp)
) { /* dense content */ }
```

**Row minimum height:** Rows with text + control use `Modifier.heightIn(min = 48.dp)`.

**Panel background:** `uiSettingsBackground` — use plain `Box`/`Column` with `.background()`, not `ModalDrawerSheet`.

---

## 9. Row Types

| Row type | Pattern | Example |
|----------|---------|---------|
| Setting | Label + inline control (Switch) | GPS mode toggle |
| Navigation | Label + trailing chevron (→) | "Manage Tracks" |
| Content | Text / sliders / stats inside card | Marker details, live stats |

---

## 10. Decision Log

| # | Date | Decision | Rationale |
|---|------|----------|-----------|
| I1 | 2026-06-24 | `Button` → `IconButton` for back arrow | `IconButton` is purpose-built for icon-only controls: circular ripple, no elevation. |
| I2 | 2026-06-24 | Back button size → 32dp | Settings page and all other app back buttons use 32dp. |
| I3 | 2026-06-24 | Title font → 17sp Bold | Wizard title is longer; 24sp would overflow on narrow portrait screens. |
| I6 | 2026-06-24 | Two card padding densities | Menu uses wide 16×10dp; data-dense cards use tight 8×4dp. |
| I7 | 2026-06-25 | `DrawerSlot` abstraction — one composable for all drawer animations | Replaces 9 copy-pasted `AnimatedVisibility` blocks (~270 lines → ~72 lines). `SlideDirection` + `ShadowEdge` enums make each drawer declarative. |
| I8 | 2026-06-25 | `OverlayLayer` — unified Layer 1 compositor | All transient surfaces + scrim live in one self-contained composable. Layer 0 (dashboard/map/controls) is permanent. Any new overlay fits into this framework. |
| I9 | 2026-06-25 | Gradient shadow via `drawBehind` replacing `Modifier.shadow()` | `Modifier.shadow(16.dp)` uses RenderNode elevation — invisible on dark backgrounds. An 8dp black@18%→transparent gradient on the drawer edge is always visible. |
| I10 | 2026-06-25 | Wizard steps extracted to `markers/wizard/` package | `WizardTopBar`, `WizardButtonRow`, and 4 step composables (`TypeSelectStep`, `PositionStep`, `SliderStep`, `TextInputStep`) are now independent files. `WizardDrawer.kt` is a thin shell (~228 lines). |
| I11 | 2026-06-25 | `WizardDrawer` receives `step` as non-null parameter | Fixes the blank-screen bug where `step ?: return` short-circuited rendering when the state was `null` for one frame. `OverlayLayer` guards with `showWizard && activeStep != null`. |
