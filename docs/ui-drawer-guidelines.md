# Drawer UI Guidelines

> **Purpose:** Canonical reference for rendering any drawer/panel surface in Maro II.
> **Created:** 2026-06-24 — normalisation pass (I1–I6).
> **Updated:** 2026-07-18 — Menu drawer clickability pass: 56dp rows, 2dp divider spacers, 48dp icon buttons (I24).

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

**Right-edge control column rule (paint-only):** the right-edge controls (zoom `+`/`−`, fan, add-zone) float over the map; the map itself always renders full-bleed and must never be padded by the control column. Every transient bottom overlay must stay clear of that column:

- Overlays rendered inside the map's left overlay column (exit toast, loading/error) are already bounded by the column layout — align them `BottomStart`/`CenterStart` like the snackbar and do not add extra `end` padding.
- Overlays rendered outside it (e.g. the undo snackbar stack) anchor `BottomStart` — never `BottomCenter`/`BottomEnd` — and reserve the column on the overlay itself via `end = RIGHT_CONTROL_COLUMN_INSET` (82dp: 12dp gap + 64dp button + 6dp end).

**Full-height landscape drawer rule:** drawers anchored to the left edge in landscape (Marker, TrackInfo, Wizard) are full-height and must clear the status bar — pass `statusBarsInset = true` to `DrawerScaffold` (or apply `windowInsetsPadding(WindowInsets.statusBars)` for drawers that don't use `DrawerScaffold`).

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

### Scrim Behavior Rule

🔴 **All drawers must close when the scrim is tapped.** The scrim click handler calls the drawer's dismiss callback.

**Exception:** Wizard `Position` / `PositionP2` steps — these suppress the scrim entirely so the map remains draggable during point placement.

---

## 4. Drawer Composable Contract

Every drawer composable must follow this contract to work with `DrawerSlot`:

1. **No `AnimatedVisibility`** — animation is handled by `DrawerSlot`
2. **No scrim** — the unified scrim is rendered once in `OverlayLayer`
3. **No shadow** — the gradient shadow is drawn by `DrawerSlot.drawBehind`
4. **`BackHandler` inside the composable** — guarded by `isOpen` or `showXxx`
5. **Pure content** — a `Box`/`Column` with `fillMaxSize()`, `clip(shape)`, `.background(uiSettingsBackground)`, then content. **New drawers should use [`DrawerScaffold`](#12-drawerscaffold--fixed-header-scrollable-body) (§12) as the foundation** — it provides a fixed header, optional scrolling, and status-bar insets out of the box.

### Canonical Drawer Skeleton (pre-DrawerScaffold)

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

### Preferred: DrawerScaffold Skeleton

```kotlin
@Composable
fun XxxDrawer(isOpen: Boolean, onDismiss: () -> Unit) {
    if (isOpen) { BackHandler { onDismiss() } }

    DrawerScaffold(
        title = "My Drawer",
        onClose = onDismiss,
        scrollable = true,                 // false for fixed content
        statusBarsInset = false,           // true for full-screen panels
        headerActions = { /* optional row-end actions */ }
    ) {
        // scrollable content body
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

All drawer headers share these tokens, canonically implemented in [`DrawerHeader`](../app/src/main/java/ykws/android/maro/ui/components/DrawerScaffold.kt) (see [§12](#12-drawerscaffold--fixed-header-scrollable-body)).

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
| Header vertical padding | 6dp (canonical default); 12dp (wizard); 12dp (marker viewer — 6dp per side) |

```kotlin
// Canonical header pattern — use DrawerHeader() composable from DrawerScaffold.kt
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
**🔴 Menu override:** Menu drawer clickable rows use `56.dp` — larger tap zone for frequently-used primary actions (Manage Track, Manage Markers, Import/Export).

**Divider internal spacing:** Horizontal dividers inside cards use `Spacer(2.dp)` above and below (tightened from `6.dp` — card padding already provides separation).

**Icon-only action rows:** Icon buttons in content rows (e.g., Import/Export) use `Modifier.size(48.dp)` for comfortable tap targets (Android minimum: 48dp).

**Panel background:** `uiSettingsBackground` — use plain `Box`/`Column` with `.background()`, not `ModalDrawerSheet`.

---

## 9. List Item Card Pattern (Track + Marker)

Both `TrackHistoryOverlay` and `MarkerManagementOverlay` share an identical item shell. This is the canonical pattern for any list item with a colored accent bar.

### Shell

```kotlin
Row(
    modifier = Modifier.fillMaxWidth()
        .height(IntrinsicSize.Min)
        .clip(RoundedCornerShape(12.dp))
        .background(uiCardBackground)
) {
    // Left-edge accent bar — 4dp wide, full height, color from data
    Box(Modifier.width(4.dp).fillMaxHeight().background(accentColor))

    // Content column
    Column(Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 4.dp)) {
        // ── Header row: metadata (11sp muted) + action icons right-aligned ──
        Row(Modifier.fillMaxWidth(), SpaceBetween, CenterVertically) {
            Text(metadata, 11sp, uiSettingsTextMuted, weight 1f, ellipsis)
            Row(spacedBy(2.dp)) {
                IconButton(36dp) { Icon(actionIcon, 24dp, tint = ButtonColors.icon) }
                // ... more action icons
            }
        }
        Spacer(2.dp)
        HorizontalDivider(0.5dp, uiSettingsDivider)
        Spacer(2.dp)

        // ── Title: 15sp SemiBold white ──
        Text(title, 15sp, SemiBold, uiSettingsTextPrimary, maxLines=1, ellipsis)

        // ── Detail row: 14sp Normal white ──
        Text(detailText, 14sp, uiSettingsTextPrimary)

        // ── Comment/description: 13sp muted (if present) ──
        if (comment.isNotBlank()) {
            Text(comment, 13sp, uiSettingsTextMuted, maxLines=3)
        }
    }
}
```

### Shared Tokens

| Token | Value | Applies to |
|-------|-------|------------|
| Card radius | 12dp | Both |
| Accent bar width | 4dp, `fillMaxHeight()` | Both |
| Content padding | 8dp h × 4dp v | Both |
| Header font | 11sp, `uiSettingsTextMuted` | Both |
| Title font | 15sp, SemiBold, `uiSettingsTextPrimary` | Both |
| Detail font | 14sp, Normal, `uiSettingsTextPrimary` | Both |
| Comment font | 13sp, Normal, `uiSettingsTextMuted` | Both |
| Action icon | `IconButton(36dp)` + `Icon(24dp, tint=ButtonColors.icon)` | Both |
| Divider | 0.5dp, `uiSettingsDivider`, 2dp gap each side | Both |

### Per-Type Variations

| Aspect | Track | Marker |
|--------|-------|--------|
| Accent color source | `computeTrackPolylineAppearance()` → ARGB int | `MarkerColors.of(colorIndex)` |
| Header metadata | `dateLabel  startTime→endTime` + `pts` | `coordinateHeader()`: `[lat,lon]` (Pin/Circle) or `[lat,lon]→[lat,lon]` (Corridor) |
| Detail text | 3-col × 2-row stats grid | `markerFormatText()`: `📌 - 200m prox` / `⭕ - 200m r - 200m prox` / `📏 - 100m w - 200m prox` |
| Action icons | Pin toggle + Export GPX | Edit only |
| Accent bar when hidden | Always real color | Always marker color |

---

## 10. Row Types

| Row type | Pattern | Example |
|----------|---------|---------|
| Setting | Label + inline control (Switch) | GPS mode toggle |
| Navigation | Label + trailing chevron `KeyboardArrowRight` 28dp `uiSettingsTextMuted` | "Manage Tracks" |
| Content | Text / sliders / stats inside card | Marker details, live stats |

---

## 11. Decision Log

| # | Date | Decision | Rationale |
|---|------|----------|-----------|
| I1 | 2026-06-24 | `Button` → `IconButton` for back arrow | `IconButton` is purpose-built for icon-only controls: circular ripple, no elevation. |
| I2 | 2026-06-24 | Back button size → 32dp | Settings page and all other app back buttons use 32dp. |
| I3 | 2026-06-24 | Title font → 17sp Bold | Wizard title is longer; 24sp would overflow on narrow portrait screens. |
| I6 | 2026-06-24 | Two card padding densities | Menu uses wide 16×10dp; data-dense cards use tight 8×4dp. |
| I7 | 2026-06-25 | `DrawerSlot` abstraction — one composable for all drawer animations | Replaces 9 copy-pasted `AnimatedVisibility` blocks (~270 lines → ~72 lines). `SlideDirection` + `ShadowEdge` enums make each drawer declarative. |
| I8 | 2026-06-25 | `OverlayLayer` — unified Layer 1 compositor | All transient surfaces + scrim live in one self-contained composable. Layer 0 (dashboard/map/controls) is permanent. Any new overlay fits into this framework. |
| I9 | 2026-06-25 | Gradient shadow via `drawBehind` replacing `Modifier.shadow()` | `Modifier.shadow(16.dp)` uses RenderNode elevation — invisible on dark backgrounds. An 8dp black@18%→transparent gradient on the drawer edge is always visible. |
| I10 | 2026-06-25 | Wizard steps extracted to `ui/markers/wizard/` package | `WizardTopBar`, `WizardButtonRow`, and 4 step composables in `ui/markers/wizard/steps/`. `WizardDrawer.kt` is a thin shell. |
| I11 | 2026-06-25 | `WizardDrawer` receives `step` as non-null parameter | Fixes blank-screen bug where `step ?: return` short-circuited rendering. `OverlayLayer` guards with `showWizard && activeStep != null`. |
| I12 | 2026-06-25 | `OverlayLayer` consolidates ALL transient surfaces | 7 surfaces in one composable. `MapScreen` Layer 1 is a single `OverlayLayer(...)` call. |
| I13 | 2026-06-25 | Marker viewer uses Tight card (8×4dp) for info content | Geometry desc, direction+distance, and description inside `uiCardBackground` card, 12dp radius. |
| I21 | 2026-06-25 | Unified list item card pattern (Track + Marker) | `Row(height(IntrinsicSize.Min), clip(12dp), uiCardBackground)` + `Box(4dp, fillMaxHeight, accentColor)` + `Column(weight 1f, pad 8×4dp)`. Canonical pattern in §9. Consolidates I14/I15/I19/I20. Per-type variations: accent color source, header metadata, detail text, action icons. |
| I16 | 2026-06-25 | Previous/Next buttons match wizard pill style | `Box(RoundedCornerShape(8dp), uiSettingsAccent bg, Bold 14sp)` — same as `WizardButtonRow`. |
| I17 | 2026-06-25 | Selected marker highlight via 2.5× stroke multiplier | Thicker stroke + `mapCenterRequest` on Previous/Next navigation. |
| I23 | 2026-07-05 | Navigation chevrons normalized to 28dp | Menu drawer (20dp) and marker card (18dp) chevrons inconsistently sized. Unified at 28dp — clear affordance, matches standard icon size. Applies to `MenuDrawerOverlay` navigation rows + `MarkerManagementOverlay` card chevrons. |
| I18 | 2026-06-25 | Proximity zone uses marker's own color (50% stroke / 10% fill) | Replaces hardcoded cyan. Fill = `dimColor(markerColor, ZONE_FILL_ALPHA_FRACTION/2)`. |
| I22 | 2026-07-03 | `DrawerScaffold` + `DrawerHeader` extracted from MarkerDrawer | Fixed-header + scrollable-body pattern promoted to reusable scaffold. See §12. |
| I24 | 2026-07-18 | Menu drawer clickability: 56dp rows, 2dp divider spacers, 48dp icon buttons | Larger tap zones by increasing row height from 48→56dp while tightening divider gaps from 6→2dp. Import/Export icons enlarged from 40→48dp. Net vertical height unchanged. MARKERS card reordered: "Manage Markers" primary action above divider, "Show Zones on Map" toggle below. See §8. |

---

## 12. DrawerScaffold — Fixed Header + Scrollable Body

[`DrawerScaffold`](../app/src/main/java/ykws/android/maro/ui/components/DrawerScaffold.kt) is the canonical drawer shell. It provides a **fixed** [`DrawerHeader`](../app/src/main/java/ykws/android/maro/ui/components/DrawerScaffold.kt) at the top and a scrollable (or static) body below — so the back button and title never scroll off screen.

### API

```kotlin
@Composable
fun DrawerScaffold(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    headerActions: @Composable RowScope.() -> Unit = {},
    headerHorizontalPadding: Dp = 24.dp,
    headerVerticalPadding: Dp = 3.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp),
    scrollable: Boolean = true,
    statusBarsInset: Boolean = false,
    shape: Shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
    content: @Composable ColumnScope.() -> Unit
)
```

Parameter | Default | Purpose |
|-----------|---------|---------|
`title` | *(required)* | Header title text (17sp Bold, single-line, ellipsis overflow) |
`onClose` | *(required)* | Back-button callback |
`modifier` | `Modifier` | Outer modifier on the root `Box` |
`headerActions` | `{}` | Composable slot in the header `Row` (right-aligned) |
`headerHorizontalPadding` | `24.dp` | Horizontal padding for the header `Row` |
`headerVerticalPadding` | `3.dp` | Vertical padding for the header `Row` |
`contentPadding` | `PaddingValues(horizontal = 12.dp)` | Padding around the scrollable content body |
`scrollable` | `true` | `true` = `verticalScroll` around content; `false` = static body |
`statusBarsInset` | `false` | `true` = applies `.windowInsetsPadding(statusBars)` after background |
`shape` | `RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)` | Clip shape for the root `Box` |

### Structure

```
Box(fillMaxSize, clip(shape), background(uiSettingsBackground), modifier, +statusBarsInset)
  └─ Column(fillMaxSize)
       ├─ DrawerHeader(title, onClose, headerActions, hPad, vPad)  ← FIXED
       └─ Box(Modifier.weight(1f).fillMaxWidth())                   ← scroll host
            └─ if (scrollable) Column(verticalScroll, contentPadding) { content() }
               else Column(contentPadding) { content() }
```

### Consumers

Consumer | File | scrollable | headerActions | hPad | statusBarsInset |
|----------|------|:---:|---|---|:---:|
MarkerDrawer ViewingContent | `MarkerDrawer.kt` | true | edit + delete + icon buttons | 12.dp | false |
MarkerDrawer MatchResult | `MarkerDrawer.kt` | true | none | 12.dp | false |
MenuDrawerOverlay | `MenuDrawerOverlay.kt` | false | Settings gear button | 24.dp | true |

### Not Migrated

Component | Reason |
|-----------|--------|
`ListOverlayScaffold` | Already has correctly-fixed header + section label/sort/filter controls between header and `LazyColumn` |
`WizardDrawer` | Uses `WizardTopBar` (step dots, different layout) + `WizardButtonRow` at bottom |
Settings | Has 24sp title, tab bar, `HorizontalPager` — different structure |

### Migration Guide

**Before (ad-hoc pattern):**
```kotlin
Box(fillMaxSize, clip(shape), bg) {
    Column(verticalScroll, padding(24.dp)) {
        Row { /* back + title */ }   // scrolls away!
        // ... content ...
    }
}
```

**After (DrawerScaffold):**
```kotlin
DrawerScaffold(
    title = "My Drawer",
    onClose = onDismiss,
    headerHorizontalPadding = 24.dp,
    contentPadding = PaddingValues(horizontal = 24.dp),
    scrollable = true
) {
    // content body — header stays fixed
}
```

### DrawerHeader (standalone)

`DrawerHeader` is also available as a standalone composable for cases where `DrawerScaffold`'s full structure isn't needed:

```kotlin
@Composable
fun DrawerHeader(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    horizontalPadding: Dp = 24.dp,
    verticalPadding: Dp = 3.dp,
)
```

Tokens match [§6](#6-header-tokens) exactly: 32dp `CircleShape` back button, 18dp `ArrowBack`, 16dp spacer, 17sp Bold title, optional `actions` slot.
