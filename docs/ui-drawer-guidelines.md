<!-- scope: reference -->
# Drawer UI Guidelines

> **Purpose:** Canonical reference for rendering any drawer/panel surface in Maro II.
> **Created:** 2026-06-24 — normalisation pass (I1–I6).
> **Updated:** 2026-09-06 — consolidation (canonical homes, pointers, Decision Log removed) + header vertical padding normalized to 6dp.

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
| 7 | Settings | `SettingsOverlay` (in `MapScreenSettingsOverlay.kt`) | `showSettings` | `FROM_RIGHT` | `LEFT` | `fillMaxSize` |

### Portrait Drawer Height Floor

🔴 **A bottom-anchored drawer is never smaller than the original dashboard.** Its height is
`maxOf(portraitDashboardHeight, <content height>)` — the dashboard height is a floor, so the drawer either
matches the dashboard or grows taller to fit its content. It must never render shorter than the dashboard
(otherwise its top edge would sit lower than the dashboard's top).

- The portrait Track detail drawer ([`OverlayLayer.kt`](../app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt))
  follows this rule via `maxOf(portraitDashboardHeight, …)`.
- The portrait marker detail drawer ([`MarkerDrawer.kt`](../app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt))
  follows it via the wrap-content floor: `ViewingContent` passes
  `wrapContentMinHeight = portraitDashboardHeight` to [`DrawerScaffold`](../app/src/main/java/ykws/android/maro/ui/components/DrawerScaffold.kt),
  which floors the wrap Column with `heightIn(min = …)` (do NOT add `wrapContentHeight()` — it
  overrides the incoming minimum, letting content win over the floor; verified on-device 2026-09-08).
- Marker `Viewing` wrap-content is **portrait-only** (`wrapContent = !isLandscape`).

### Landscape Full-Column Coverage

🔴 **A left-anchored item drawer (marker / track / wizard) must cover the whole original landscape
dashboard column** — `align(CenterStart)` + `landscapeDashboardWidth` + `fillMaxHeight()`, filled
top-to-bottom. Wrap-content is portrait-only; in landscape the drawer uses the non-wrap full-height
branch so the top of the column is never left uncovered by a shorter content panel.

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
5. **Pure content** — a `Box`/`Column` with `fillMaxSize()`, `clip(shape)`, `.background(uiBackground)`, then content. **New drawers should use [`DrawerScaffold`](#12-drawerscaffold--fixed-header-scrollable-body) (§12) as the foundation** — it provides a fixed header, optional scrolling, and status-bar insets out of the box.

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
4. Wire the visibility flag + dismiss callback: read-only drawer state goes into the matching bundle in
   `OverlayLayerParams.kt` (add a field there — do **not** extend `OverlayLayer`'s signature); the dismiss callback
   stays an individual function parameter (bundling callbacks would defeat Compose lambda memoization)
5. Update the scrim formula if the new drawer needs a scrim behind it
6. Add a row to the [Surfaces table](#3-surfaces--quick-reference)

---

## 6. Header Tokens

All drawer headers share these tokens, canonically implemented in [`DrawerHeader`](../app/src/main/java/ykws/android/maro/ui/components/DrawerScaffold.kt) (see [§12](#12-drawerscaffold--fixed-header-scrollable-body)).

| Token | Value |
|-------|-------|
| Back button widget | `IconButton` (never `Button`) |
| Back button size | 32dp, `CircleShape` |
| Back button background | `uiSwitchTrackInactive` |
| Back icon size | 18dp |
| Back icon tint | `uiTextPrimary` |
| Title font | 17sp, Bold, `uiTextPrimary` |
| Back→title spacer | `16dp` |
| Header horizontal padding | 24dp (menu, track history); 12dp (wizard, marker viewer) |
| Header vertical padding | `ui.padding.header.vertical` (6dp canonical default); 12dp (wizard); 12dp (marker viewer — 6dp per side) |

> Use the [`DrawerHeader`](#12-drawerscaffold--fixed-header-scrollable-body) composable — do not hand-roll this `Row`.
>
> **Post-header gap (uniform):** the header→first-content gap is the header's own bottom `verticalPadding`
> (`ui.padding.header.vertical`, 6dp). Drawers add **no extra** spacer/padding after the header — Menu, Marker,
> Track (landscape + portrait), and Settings all share this single 6dp gap. The `DrawerHeader`/`DrawerScaffold`
> defaults read the token from `AppConfig.uiPaddingHeaderVertical`.
>
> **Settings:** the Settings overlay header uses the shared `DrawerHeader` (17sp title, 32dp back button) with no
> `actions` slot. Its tab bar + `HorizontalPager` body sits below the header and is not part of `DrawerHeader`.
> The tab bar follows the header directly (no extra spacer) — the header's 6dp bottom padding provides the gap.

---

## 7. Section Headers in Drawers

Drawer content sections use the same `SectionHeader` / `SubSectionHeader` typography as
settings — see [`ui-component-guidelines.md` §2.9](ui-component-guidelines.md#29-header-hierarchy).

---

## 8. Card Pattern

The card surface primitive (`uiCardBackground`, 12dp radius, Wide 16×10 / Tight 8×4 densities)
is canonical in [`ui-component-guidelines.md` §2.0](ui-component-guidelines.md#20-card-surface-primitive-authority).
This section keeps only the **drawer-specific** rules layered on top of that surface:

- **Between sections:** `uiSpacingSectionGap` (14dp) — the shared Settings rhythm; the old drawer-internal 8dp inter-card gap is retired (no other drawer stacks legacy §8 cards).
- **Row minimum height:** Rows with text + control use `Modifier.heightIn(min = 48.dp)`; switch rows inherit Material3 `Switch`'s minimum interactive size.
- **Divider internal spacing:** §9 list-item cards only — their horizontal dividers use `Spacer(2.dp)` above and below (tightened from `6.dp` — card padding already provides separation). Grouped drawer cards (e.g. the Menu drawer) use the shared `SectionDivider` (§2.6 of the component guidelines).
- **Action rows:** Label+icon tap targets (e.g., the Menu drawer's Import/Export pair) use `Modifier.heightIn(min = 48.dp)` on the clickable row; the icon itself is `size(24.dp)`. Bare icon-only buttons use `Modifier.size(48.dp)`.
- **Panel background:** `uiBackground` — use plain `Box`/`Column` with `.background()`, not `ModalDrawerSheet`.

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
        // ── Header row: metadata (11sp muted) + action icons + open-details chevron right-aligned ──
        Row(Modifier.fillMaxWidth(), SpaceBetween, CenterVertically) {
            Text(metadata, 11sp, uiTextMuted, weight 1f, ellipsis)
            Row(spacedBy(2.dp)) {
                IconButton(36dp) { Icon(actionIcon, 24dp, tint = ButtonColors.icon) }
                // ... more action icons
            }
            // Open-details chevron — canonical 28dp muted, plain Icon (not IconButton).
            // Gated by showChevron (false in detail-drawer / MeasureHeight contexts).
            if (showChevron) {
                Icon(KeyboardArrowRight, cd_view, uiTextMuted, 28dp)
            }
        }
        Spacer(2.dp)
        HorizontalDivider(0.5dp, uiDividerColor)
        Spacer(2.dp)

        // ── Title: 15sp SemiBold white ──
        Text(title, 15sp, SemiBold, uiTextPrimary, maxLines=1, ellipsis)

        // ── Detail row: 14sp Normal white ──
        Text(detailText, 14sp, uiTextPrimary)

        // ── Comment/description: 13sp muted (if present) ──
        if (comment.isNotBlank()) {
            Text(comment, 13sp, uiTextMuted, maxLines=3)
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
| Header font | 11sp, `uiTextMuted` | Both |
| Title font | 15sp, SemiBold, `uiTextPrimary` | Both |
| Detail font | 14sp, Normal, `uiTextPrimary` | Both |
| Comment font | 13sp, Normal, `uiTextMuted` | Both |
| Action icon | `IconButton(36dp)` + `Icon(24dp, tint=ButtonColors.icon)` | Both |
| Open-details chevron | `Icon(KeyboardArrowRight, 28dp, tint=uiTextMuted)` — plain `Icon`, not `IconButton`; gated by `showChevron` | Both |
| Divider | 0.5dp, `uiDividerColor`, 2dp gap each side | Both |

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
| Navigation | Label + trailing chevron `KeyboardArrowRight` 28dp `uiTextMuted` | "Manage Tracks" |

> **Canonical chevron:** The Navigation trailing chevron and the §9 card-header open-details chevron share the same
> token — `KeyboardArrowRight`, **28dp**, `uiTextMuted`. Use this single canonical size/color everywhere a
> "reveal more / open details" affordance appears (card header, track row, navigation rows). Do not introduce
> alternate sizes or accent-tinted chevrons.
| Content | Text / sliders / stats inside card | Marker details, live stats |

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
    headerVerticalPadding: Dp = 6.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp),
    scrollable: Boolean = true,
    suppressOverscrollWhenFits: Boolean = false,
    bottomAnchoredContent: Boolean = false,
    statusBarsInset: Boolean = false,
    shape: Shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
    footer: @Composable ColumnScope.() -> Unit = {},
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
`headerVerticalPadding` | `6.dp` | Vertical padding for the header `Row` |
`contentPadding` | `PaddingValues(horizontal = 12.dp)` | Padding around the scrollable content body |
`scrollable` | `true` | `true` = `verticalScroll` around content; `false` = static body |
`suppressOverscrollWhenFits` | `false` | `true` = disables overscroll while body content fits the viewport |
`bottomAnchoredContent` | `false` | `true` = bottom-aligns the scrollable body content |
`statusBarsInset` | `false` | `true` = applies `.windowInsetsPadding(statusBars)` after background |
`shape` | `RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)` | Clip shape for the root `Box` |
`footer` | `{}` | Composable slot rendered below the scrollable body |

### Structure

```
Box(fillMaxSize, clip(shape), background(uiBackground), modifier, +statusBarsInset)
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
MenuDrawerOverlay | `MenuDrawerOverlay.kt` | true | Settings gear button | 24.dp | true |
SettingsOverlay | `MapScreenSettingsOverlay.kt` | n/a (own tab bar + pager body) | none | 24.dp | true |

> **Note:** the Settings row consumes only the standalone `DrawerHeader`, not the full `DrawerScaffold` shell
> (the other rows are genuine `DrawerScaffold` consumers). Its `statusBarsInset` is applied manually via
> `.windowInsetsPadding(WindowInsets.statusBars)` on the overlay, not via `DrawerScaffold`'s parameter.

### Not Migrated

`ListOverlayScaffold` and `WizardDrawer` are not migrated — each already has its own fixed-header structure
(fixed header + section label/sort/filter controls, and `WizardTopBar` + `WizardButtonRow` respectively).
Settings uses the shared `DrawerHeader` for its header row but keeps its own tab bar + `HorizontalPager` body
(not the full `DrawerScaffold` shell).

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
    verticalPadding: Dp = 6.dp,
)
```

Tokens match [§6](#6-header-tokens) exactly: 32dp `CircleShape` back button, 18dp `ArrowBack`, 16dp spacer, 17sp Bold title, optional `actions` slot.
