<!-- scope: reference -->
# Drawer UI Guidelines

> **Purpose:** Canonical reference for rendering any drawer/panel surface in Maro II.
> **Created:** 2026-06-24 — normalisation pass (I1–I6).
> **Updated:** 2026-09-21.

---

## 1. Architecture — Two-Layer System

MapScreen renders two layers inside `BoxWithConstraints`:

```
Layer 0 (permanent, always rendered):
├── MapContent (map + overlays)
├── DashboardPanel (4-card dashboard)
├── Right-edge controls (fan, add zone, zoom)
├── GPS / Track / EarthWater status icons (EarthWater hidden when the Layers tab setting is off)
└── Regulated zone warning strip

Layer 1 (transient, self-contained):
├── Scrim
├── WizardDrawer
├── MenuDrawer
├── MarkerDrawer
├── TrackHistory
├── MarkerManagement
├── Settings
└── ConfirmDialog (own panel + own scrim layer; composited above every drawer and the map)
```

Layer 1 is a single composable call: [`OverlayLayer`](../app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt). It owns the ladder scrim and the drawer/settings/wizard surfaces; the `ConfirmDialog` is painted separately by the ladder host described below. Each surface is wrapped in a [`DrawerSlot`](../app/src/main/java/ykws/android/maro/ui/map/DrawerSlot.kt) that provides entrance/exit animation and edge shadow.

**Dialog layer:** [`ConfirmDialog`](../app/src/main/java/ykws/android/maro/ui/components/ConfirmDialog.kt) is a modal confirmation panel that draws its **own** `ui.scrim.alpha` layer (`DrawerSlot`, `FADE_ONLY`) directly beneath its own panel (`DrawerSlot`, `FROM_BOTTOM`); both animate together over 450 ms and a tap on the scrim dismisses. A drawer-hosted surface raises it as a `ConfirmRequest` and the ladder-hosted `ConfirmRequestHost` ([`MapScreen.kt`](../app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1978)) paints it — a sibling composed after `OverlayLayer` — so the dialog composites **above every drawer and above the map**. There is no shared dialog-dismiss registry: the dialog owns its own scrim and its own dismiss lambda (see §3).

**Key rule:** Layer 0 components must never be conditional. Dashboard, controls, and status icons are always present. Only Layer 1 surfaces appear/disappear.

**Right-edge control column rule (paint-only):** the right-edge controls (zoom `+`/`−`, fan, add-zone) float over the map; the map itself always renders full-bleed and must never be padded by the control column. An overlay drawn **outside** the map's left overlay column anchors `BottomStart` — never `BottomCenter`/`BottomEnd` — and reserves the column on the overlay itself via `end = RIGHT_CONTROL_COLUMN_INSET` (82dp: 12dp gap + 64dp button + 6dp end). Its live users are the undo snackbar stack ([`MapSnackbarHost`](../app/src/main/java/ykws/android/maro/ui/map/MapSnackbarHost.kt)), `LockBanner` and `MapStatusBanner`, all three drawn from full-width parents.

**Bottom-band clearance rule:** every banner in the map's bottom band clears the bottom-left zone tag column whenever one is drawn — its start inset is `bannerStartInset(tagsDrawn)`, the band's gutter plus the tag column's width — and the right control column always; an overlay inside the left overlay column takes that second half from the column itself and adds no `end` reserve of its own, an overlay outside it from the rule above. The banner family's control, skin, clearance and each face's interior are written once, in `docs/ui-component-guidelines.md` §5.7.

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
| 1 | Scrim | — (inline in OverlayLayer) | any drawer/settings/wizard open **and no dialog visible** | hard toggle (no animation) | none | `fillMaxSize` |
| 2 | Wizard (landscape) | `WizardDrawer.kt` | `showWizard && step != null` | `FROM_LEFT` | `RIGHT` | `CenterStart`, `landscapeDashboardWidth` |
| 2 | Wizard (portrait) | `WizardDrawer.kt` | `showWizard && step != null` | `FROM_BOTTOM` | `TOP` | `BottomCenter`, full width, `dashboardBaseHeight`; no keyboard offset of its own — the platform's pan positions it, as it positions the track card's inline fields |
| 3 | Menu | `MenuDrawerOverlay.kt` | `showTrackDrawer` | `FROM_RIGHT` | `LEFT` | `TopEnd`, 75% width |
| 4 | Marker (landscape) | `MarkerDrawer.kt` | `drawerState is Viewing/MatchResult` | `FROM_LEFT` | `RIGHT` | `CenterStart`, `landscapeDashboardWidth` |
| 4 | Marker (portrait) | `MarkerDrawer.kt` | `drawerState is Viewing/MatchResult` | `FROM_BOTTOM` | `TOP` | `BottomCenter`, full width, `dashboardBaseHeight` |
| 5 | TrackHistory | `TrackHistoryOverlay.kt` | `showTrackHistory` | `FROM_RIGHT` | `LEFT` | `fillMaxSize` |
| 6 | MarkerManagement | `MarkerManagementOverlay.kt` | `showMarkerManagement` | `FROM_RIGHT` | `LEFT` | `fillMaxSize` |
| 7 | Settings | `SettingsOverlay` (in `MapScreenSettingsOverlay.kt`) | `showSettings` | `FROM_RIGHT` | `LEFT` | `fillMaxSize` |
| 8 | ConfirmDialog scrim | `ConfirmDialog.kt` | dialog visible — **owned by the dialog**, painted by the ladder `ConfirmRequestHost` beneath its own panel | `FADE_ONLY` | none | `fillMaxSize` |
| 8 | ConfirmDialog panel | `ConfirmDialog.kt` | dialog visible — **hosted by `ConfirmRequestHost` above every drawer and the map** | `FROM_BOTTOM` | none | `BottomCenter`, width = `min(maxWidth, maxHeight)` (portrait width, both orientations), flush with the bottom edge (rounded top corners only, accent border open at the bottom), nav-bar inset applied inside the panel, 450 ms slide+fade |

### Scrim Token

The scrim colour is a token — `ui.scrim.alpha` (default `0.50`) in
[`ui.properties`](../app/src/main/assets/ui.properties), exposed as
`AppConfig.uiScrimAlpha`. Never inline `Black.copy(alpha = …)`; both the drawer scrim (§ Scrim
Formula) and the dialog scrim read the same token.

### Portrait Drawer Height Floor

**A bottom-anchored drawer is never smaller than the original dashboard.** Its height is
`maxOf(dashboardBaseHeight, <content height>)` — the dashboard height is a floor, so the drawer either
matches the dashboard or grows taller to fit its content. It must never render shorter than the dashboard
(otherwise its top edge would sit lower than the dashboard's top).

- The portrait Track detail drawer ([`OverlayLayer.kt`](../app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt))
  follows this rule via `maxOf(dashboardBaseHeight, …)`.
- The portrait marker detail drawer ([`MarkerDrawer.kt`](../app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt))
  follows it via the wrap-content floor: `ViewingContent` passes
  `wrapContentMinHeight = dashboardBaseHeight` to [`DrawerScaffold`](../app/src/main/java/ykws/android/maro/ui/components/DrawerScaffold.kt),
  which floors the wrap Column with `heightIn(min = …)` (do NOT add `wrapContentHeight()` — it
  overrides the incoming minimum, letting content win over the floor).
- Marker `Viewing` wrap-content is **portrait-only** (`wrapContent = !isLandscape`).

### Landscape Full-Column Coverage

**A left-anchored item drawer (marker / track / wizard) must cover the whole original landscape
dashboard column** — `align(CenterStart)` + `landscapeDashboardWidth` + `fillMaxHeight()`, filled
top-to-bottom. Wrap-content is portrait-only; in landscape the drawer uses the non-wrap full-height
branch so the top of the column is never left uncovered by a shorter content panel.

### Scrim Formula

```kotlin
val showScrim = (showSettings
    || showTrackDrawer
    || showTrackHistory
    || showMarkerManagement
    || (showWizard && imeHeightDp > 0.dp))
    && !dialogScrimActive
```

The wizard suppresses the scrim while the keyboard is closed so the map stays interactive during point
placement.

The ladder scrim serves **drawers/settings/wizard only** and is a **hard on/off toggle** (no fade). It
**yields while any `ConfirmDialog` is visible** (`dialogScrimActive`), so the two dim layers never
stack — the dialog's own scrim then owns the screen. Both read the single `ui.scrim.alpha` token: the
ladder scrim sits *below* the drawers, while the dialog scrim is composited *above* everything.

### Scrim Behavior Rule

**All drawers must close when the scrim is tapped.** The scrim click handler calls the drawer's dismiss callback. The scrim renders with no fade — it is either present or absent.

`scrimDismiss` is a plain ladder over the drawer surfaces: settings → menu → track history → marker
management → wizard blur. It has **no dialog branch** — a `ConfirmDialog` owns its own scrim and its
own dismiss lambda, so a tap above a dialog is handled by the dialog, never by the ladder scrim
underneath it.

**Exception:** the wizard suppresses the scrim entirely while the keyboard is closed, so the map remains draggable during point placement.

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
| Header horizontal padding | **`12dp` for the whole map card family** — the marker viewer is the reference and the track card and the route panel are normalised to it (2026-10-07); `24dp` (menu, track history list); `12dp` (wizard) |
| Header vertical padding | `ui.padding.header.vertical` (6dp) — **one value for every header**, the map card family included: the route panel's own 8dp is gone, and the marker viewer's 12dp this table used to claim was never what the code did (2026-10-07); `12dp` (wizard) |

> Use the [`DrawerHeader`](#12-drawerscaffold--fixed-header-scrollable-body) composable — do not hand-roll this `Row`.
>
> **Dash-panel corners (uniform, the map dashboards):** a map dash panel wears
> **`RoundedCornerShape(0.dp)` in both orientations** (2026-10-07) — no rounded corner, the bottom ones
> especially. The marker card, the track card, the route panel and the base dashboard all pass it
> explicitly; `DrawerScaffold`'s own default (`topStart`/`bottomStart` rounded) belongs to the drawers
> alone, and the wizard keeps its own right-edge shape as it is not a dash panel.
>
> **Walk-row spacing (uniform, the map dashboards):** the Previous/Next row is one spacing on every
> selected-item surface (2026-10-07) — **8dp** from the item frame down to the row, **8dp** between the two
> buttons, and **8dp** from the row down to the frame's bottom edge — with the row's own `12dp` horizontal
> matching the cards' `contentPadding`. The **route panel's footer is the reference** the other two were
> normalised to; the marker card's `MarkerPrevNext` and the track card's footer (portrait and landscape) are
> the token's other homes. All three carry the two vertical gaps in the row's own
> `padding(horizontal = 12.dp, vertical = 8.dp)`, so none of them adds a spacer of its own.
>
> **Dash swap pre-size (settled 2026-10-07):** a host that swaps one panel for another in the same slot
> passes the outgoing panel's measured height as `DrawerScaffold.initialHeight`, and the incoming card's
> first frame is the size already on screen — the row and the frame then settle once, instead of stepping
> through the floor. This is because a wrap frame's pre-measure frame knows no header, no body and no
> footer: pinned at the floor it floated the walk row above the frame's bottom edge and could overshoot a
> taller card before settling. The map's selected-item slot seeds it from the band holder's `lastShown`,
> which whichever card comes through the slot writes; a host that never swaps passes nothing and keeps
> the floor.
>
> **Dash-footer buttons (settled 2026-10-07):** every dash panel's footer wears the **route panel's own
> tier-1 [`ConfirmActionButton`](../app/src/main/java/ykws/android/maro/ui/components/ConfirmDialog.kt)** —
> the marker card's walk row, the track card's footer and the route panel's three actions are one button
> style, one rim and one height. That is what keeps the row's own height identical across the three, so a
> cross-type swap cannot lift the row or the card by the few dp a taller button costs. Hand-rolled pills
> are not to be used in a dash footer.
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

- **Between sections:** `uiSpacingSectionGap` (14dp) — the shared Settings rhythm.
- **Row minimum height:** Rows with text + control use `Modifier.heightIn(min = 48.dp)`; switch rows inherit Material3 `Switch`'s minimum interactive size.
- **Divider internal spacing:** §9 list-item cards only — their horizontal dividers use `Spacer(2.dp)` above and below. Grouped drawer cards (e.g. the Menu drawer) use the shared `SectionDivider` (§2.6 of the component guidelines).
- **Action rows:** the drawer's one pair, the Menu's Import/Export, wears §5.9 tier 1's `ConfirmActionButton` in the **middle** role — the accent at **50 %** (`ui.action.neutral.background`) under a **2dp `ui.accent` rim at full opacity**, with the primary's white bold label — side by side, weighted halves 8dp apart. Bare icon-only buttons use `Modifier.size(48.dp)`.
- **A group reached every time stays open:** the Tracks card's `Display Tracks with:` pair stands as a sub-section under its own caption rather than behind a chevron, `Expander` + `NestedCard` remaining the recipe for a group that is genuinely optional — its open/closed state then held by the drawer that owns the row.
- **Panel background:** `uiBackground` — use plain `Box`/`Column` with `.background()`, not `ModalDrawerSheet`.

---

## 8a. The Routing Card (Menu drawer)

**The Menu drawer's one input group, standing inside the card the drawer opens on** — a card whose header is
`Routing` (`settings_tab_routing`) since 2026-10-04, where the drawer's first section used to be titled
Navigation (`settings_section_position_source`) over routing content. It holds **three sub-sections in one
card**, in this order, each set off by a `SectionDivider` and headed by a short comment: the route's two ends,
the quick access to the mode's two settings, and the mode's own summary when it has something to say.

**Sub-section 1 — Origin and destination** (R44–R48, `MenuDrawerOverlay.RouteEndsSection`): a route's two ends,
chosen here rather than placed on the map — the action that used to arm the acquisition from here was
**removed on 2026-10-04**, the map's square and the fan's own child being the doors. It is a **sub-section of
the card** rather than a top-level section with a card of its own (D2), **headed by one comment naming the
group's two fields**: `route_comment_ends` reads `Origin and destination` (`Origine et destination`),
**which is what identifies them**, since neither row carries a label of its own — each dropdown shows **only
its value**, inside its own field box (§2.12). **No rule separates the two rows either**: the group's order is
the comment → the two value-only dropdowns — **one block** — and the two sit one gap apart, the shared
`ui.spacing.dropdown.gap` the quick access leaves between its own two wheels, so a group of fields is spaced
alike whichever way it lies (2026-10-04). It remains the drawer's only group that **writes**
mode state rather than reading it; the summary stays read-only (R67).

- **Two `DropdownRow`s, one per end**, **label-less and one gap apart**: the shared control of
  [`ui-component-guidelines.md` §2.12](ui-component-guidelines.md#212-dropdown-row--dropdownrow), each row
  `label = null` so it shows **only the value**, still under the comment that names both fields — the ends'
  lists open as a wheel through the control (§2.12), **no rule between the rows** and no per-row label
  duplicating what the heading already says. The value stands inside the control's own box on the bars'
  base — `uiRadiusCard` behind the accent edge (§2.12) — so the ends read as two boxes under the comment.
- **Entries are ordered and the first is the fallback** (R66): the fixed words first — `Current
  position`, `Marker position`, each **wearing the dashes** `-- … --` so a fixed entry is
  told from a marker at a glance — then every marker the end's own flag names, in the marker list's own
  order. A marker's own name is **data rather than a label**, so it arrives already resolved and undressed,
  while the fixed entries are `@StringRes` ids the sub-section resolves through `route_end_fixed_fmt`; the
  two shapes meet in one label list here and nowhere else.
- **No action stands here any more** (the user's word, 2026-10-04): the `Route` button that armed the acquisition
  from the standing pair was removed, so the arming's doors are the map's square and the fan's own child, and the
  `RouteSummaryData` field that carried the callback went with it. The drawer keeps the mode's **parameters** and
  its **status**; the panel keeps the arming's three outcomes.
- **It stands always**, whether or not a route runs: the ends are what an arming reads, and hiding them while
  the mode is off would put a parameter behind a mode.

**Sub-section 2 — Cruising speed and driving preference** (added 2026-10-04, the user's word,
`MenuDrawerOverlay.RouteQuickAccessSection`): the quick access to the two settings the mode plans with —
`routeFreeWaterPaceKn` and `routeSlowWaterAversion` — as a pair of label-less `DropdownRow`s sharing one row,
each opening the same wheel in its popup (§2.15). It is a **second door onto the settings, never a second
home**: both boxes write the very values the Settings page's sliders write, so the two surfaces cannot
disagree, and the pace shown is the **set** pace rather than the boat's own fitted one.

- **The pace's entries are the setting's own grid** (5 … 35 kn by 5, `AppConfig.ROUTE_FREE_WATER_PACE_STOPS_KN`)
  and the preference's are the ladder's three rungs in its own order; each rung's word and λ come off the
  ladder (`routeRungLabelRes` / `routeRungLambdaOf`), so the wheel and the Settings slider name a rung the
  same way.
- **The pair is the pair control** (`DropdownPairRow`, §2.16 of the component guidelines), asking for the
  **proportional split** (the user's word, 2026-10-05): each box takes a share of the row matching its own
  longest word, so the preference's longer labels — `Balanced`, `Équilibré` — earn more room than the pace's
  `35 kn`. This supersedes the earlier fixed-pace / elastic-preference split, under which the pair read narrow
  on the left and roomy on the right.

**Sub-section 3 — the summary, when applicable** (`RouteSummaryBlock`): it stands while the engine searches
**and** while a route is followed — the routing-phase-alone gate of 2026-09-28 was widened on 2026-10-04 — and
it rides a `NestedCard` sub-card of its own, at the card's foot.

The mode's **map half** — the followed line, the derived edge, the travelled run and the provisional
line — is painted by the shared path render engine ([`maro-code.md`](maro-code.md), "Path Render
Engine"), so a route's stroke, chevrons and speed bands follow the same `path.*` keys as a stored
track's. The drawer keeps the mode's own state; it never paints the line.

## 9. List Item Card Pattern (Track + Marker)

Both `TrackHistoryOverlay` and `MarkerManagementOverlay` share an identical item shell. This is the canonical pattern for any list item with a colored accent bar.

### Shell

```kotlin
Box {                                    // outer Box: hosts the door overlay
    Row(
        modifier = Modifier.fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(12.dp))
            .background(uiCardBackground)
            .combinedClickable(onClick = { onTap() }, onLongClick = onLongPress)
    ) {
        // Leading selection door (visual half) — a 6dp accent bar, the item's own colour, full
        // height, inside the 14dp visual zone that folds in the content's former 8dp leading padding.
        ListSelectionRail(accentColor = accentColor)

        // Content column — the 14dp door owns the leading inset; 8dp end, 4dp vertical
        Column(Modifier.weight(1f).padding(end = 8.dp, top = 2.dp, bottom = 6.dp)) {
            // ── Header row: [check] metadata (11sp muted) + action icons + chevron right-aligned ──
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), SpaceBetween, CenterVertically) {
                // The selection check leads the first line — selected only, so it reserves no slot.
                if (isSelected) { ListSelectionCheck(); Spacer(6.dp) }
                Text(metadata, 11sp, uiTextMuted, weight 1f, ellipsis)
                Row(spacedBy(2.dp)) {
                    IconButton(36dp) { Icon(actionIcon, 24dp, tint = ButtonColors.icon) }
                    // ... more action icons
                }
                // Open-details chevron — canonical 28dp muted, plain Icon (not IconButton).
                // Gated by showChevron (false in the detail-drawer contexts).
                if (showChevron) {
                    Icon(KeyboardArrowRight, cd_view, uiTextMuted, 28dp)
                }
            }
            Spacer(2.dp)
            HorizontalDivider(0.5dp, uiDividerColor)
            Spacer(2.dp)

            // ── Title line: the name, 15sp SemiBold white ──
            Text(title, 15sp, SemiBold, uiTextPrimary, maxLines=1, ellipsis)

            // ── Detail row: 14sp Normal white ──
            Text(detailText, 14sp, uiTextPrimary)

            // ── Comment/description: 13sp muted (if present) ──
            if (comment.isNotBlank()) {
                Text(comment, 13sp, uiTextMuted, maxLines=3)
            }
        }
    }
    // Leading selection door (pointer half) — a 24dp full-height touch band at the card's leading
    // edge, drawn as an overlay so it consumes no layout width. Its last 10dp overlaps the card
    // body by design.
    ListSelectionTouchZone(isSelected = isSelected, onSelect = onSelect, onLongPress = onLongPress)
}
```

### Shared Tokens

| Token | Value | Applies to |
|-------|-------|------------|
| Card radius | 12dp | Both |
| Selection door | [`ListSelectionRail`](../app/src/main/java/ykws/android/maro/ui/components/ListSelectionRail.kt) — 6dp accent bar (item's colour) in a 14dp visual zone | Both |
| Door touch band | [`ListSelectionTouchZone`](../app/src/main/java/ykws/android/maro/ui/components/ListSelectionRail.kt) — 24dp, overlay; last 10dp overlaps the card body | Both |
| Selection check | [`ListSelectionCheck`](../app/src/main/java/ykws/android/maro/ui/components/ListSelectionCheck.kt) — 24dp `uiAccent` disc, white 16dp `Check`, head of the first line (selected only) | Both |
| Content padding | vertical + 8dp end; the 8dp leading inset is folded into the 14dp door | Both |
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
| Detail text | 3-col × 2-row stats grid, each cell the shared `StatCell` (`ui/components/StatCell.kt`) | `markerFormatText()`: `📌 - 200m prox` / `⭕ - 200m r - 200m prox` / `📏 - 100m w - 200m prox` |
| Action icons | Pin toggle + Export GPX | Icon/pick (36dp, leading the cluster) + Pin toggle + Edit |
| Accent bar when hidden | Always real color | Always marker color |

> **The marker card's icon/pick button moved out of the header's leading edge** (2026-10-06): it now leads the
> trailing action cluster at the cluster's own 36 dp, ahead of pin and edit, and the coordinate text loses its
> 4 dp start gap — the marker header reads coordinate → cluster → chevron, and the coordinate text owns the
> header's left. No type glyph stands anywhere on either card.

> **The live stats block is the pattern's second wearer** (2026-10-04) — the TRACKS card, under its own Tracks row,
> lays its six readings in a **two-column by three-row** grid of the same [`StatCell`](../app/src/main/java/ykws/android/maro/ui/components/StatCell.kt),
> under a **state band** whose fill is the tracking status colour subdued to the taken-choice level
> (`status.tracking.container.recording` / `status.tracking.container.idle`, the accent's own 30 %), edged at 1dp in
> that same state's own colour (`status.tracking.healthy` / `status.tracking.idle`), and whose mark is the shared `MapPulseDot`. Its cells are that same [`StatCell`](../app/src/main/java/ykws/android/maro/ui/components/StatCell.kt) in its **columned shape**: the label right-aligned at the table's own measured width, the colon alone centred in a 6 dp slot, the value left-aligned — one label column shared by the table, so the values keep their alignment with no fixed share of empty space. The card shell and the accent bar are absent there, the block already standing
> inside a `CardArea`, the state leaves the grid for that band, and the block rides a shared `NestedCard` sub-card at
> the card's foot — depth without disclosure, the component's second use, named in `ui-component-guidelines.md` §2.4.

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
    title: String = "",
    onClose: (() -> Unit)? = null,
    showBack: Boolean = true,
    modifier: Modifier = Modifier,
    headerActions: @Composable RowScope.() -> Unit = {},
    headerHorizontalPadding: Dp = 24.dp,
    headerVerticalPadding: Dp = AppConfig.uiPaddingHeaderVertical.dp,
    header: (@Composable ColumnScope.() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp),
    scrollable: Boolean = true,
    suppressOverscrollWhenFits: Boolean = false,
    bottomAnchoredContent: Boolean = false,
    wrapContent: Boolean = false,
    wrapContentMinHeight: Dp = 0.dp,
    statusBarsInset: Boolean = false,
    onMeasuredHeight: ((Dp) -> Unit)? = null,
    backgroundColor: Color = Color(AppConfig.uiBackground),
    wrapContentMaxHeight: Dp? = null,
    shape: Shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
    footer: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
)
```

Parameter | Default | Purpose |
|-----------|---------|---------|
`title` | `""` | Header title text, used only when no `header` slot is supplied (17sp Bold, single-line, ellipsis overflow) |
`onClose` | `null` | Back-button callback; required only while a header is drawn — a header-less panel omits it, and `title` with it |
`showBack` | `true` | `false` = the header keeps its title and drops the back button |
`modifier` | `Modifier` | Outer modifier on the root `Box` |
`headerActions` | `{}` | Composable slot in the header `Row` (right-aligned) |
`headerHorizontalPadding` | `24.dp` | Horizontal padding for the header `Row` |
`headerVerticalPadding` | `AppConfig.uiPaddingHeaderVertical.dp` | Vertical padding for the header `Row` |
`header` | `null` | Optional header slot; null draws today's `DrawerHeader` when `onClose` is supplied, and no header at all otherwise |
`contentPadding` | `PaddingValues(horizontal = 12.dp)` | Padding around the scrollable content body |
`scrollable` | `true` | `true` = `verticalScroll` around content; `false` = static body |
`suppressOverscrollWhenFits` | `false` | `true` = disables overscroll while body content fits the viewport |
`bottomAnchoredContent` | `false` | `true` = bottom-aligns the scrollable body content |
`wrapContent` | `false` | `true` = the panel sizes to its content, floored at `wrapContentMinHeight` |
`wrapContentMinHeight` | `0.dp` | The floor the wrap-content panel never renders shorter than — the dashboard base size for the bottom panels |
`statusBarsInset` | `false` | `true` = applies `.windowInsetsPadding(statusBars)` after background |
`onMeasuredHeight` | `null` | Optional report of the panel's measured height, from **both** branches — what the map's band reads in portrait |
`backgroundColor` | `AppConfig.uiBackground` | The visible panel's background; the dashboard passes its own `ui.dashboard.background` |
`wrapContentMaxHeight` | `null` | Optional ceiling for the panel, so a taller one's body scrolls instead of covering the map strip; null keeps the full-screen ceiling |
`fadeInOnEnter` | `false` | Fade the panel in over ~120 ms on its first composition — the map's selected-item cards opt in, so a cross-type swap dissolves rather than cutting (2026-10-07) |
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

Consumer | File | scrollable | fadeInOnEnter | headerActions | hPad | statusBarsInset |
|----------|------|:---:|:---:|---|---|:---:|
MarkerDrawer ViewingContent | `MarkerDrawer.kt` | true | true | edit + delete + icon buttons | 12.dp | false |
MarkerDrawer MatchResult | `MarkerDrawer.kt` | true | true | none | 12.dp | false |
OverlayLayer track card (portrait) | `OverlayLayer.kt` | true | true | bands + eye + delete | 12.dp | false |
OverlayLayer track card (landscape) | `OverlayLayer.kt` | false | true | bands + eye + delete | 12.dp | true |
RouteConfirmPanel | `RouteConfirmPanel.kt` | true | true | status + readings + page dots | 12.dp | false |
MenuDrawerOverlay | `MenuDrawerOverlay.kt` | true | false | Settings gear button | 24.dp | true |
SettingsOverlay | `MapScreenSettingsOverlay.kt` | n/a (own tab bar + pager body) | false | none | 24.dp | true |

> **Note:** the Settings row consumes only the standalone `DrawerHeader`, not the full `DrawerScaffold` shell
> (the other rows are genuine `DrawerScaffold` consumers). Its `statusBarsInset` is applied manually via
> `.windowInsetsPadding(WindowInsets.statusBars)` on the overlay, not via `DrawerScaffold`'s parameter.

### Not Migrated

`ListOverlayScaffold` is not migrated — it already has its own fixed-header structure (fixed header plus
section label/sort/filter controls). `WizardDrawer` **is** a `DrawerScaffold` and reads
through it exactly as §3's second row and §6's header table describe: the title and the shared page
dots ([`PageDots`](../app/src/main/java/ykws/android/maro/ui/components/PageDots.kt)) in the header,
in their progress fill up to and including the current step, the step body its own `AnimatedContent`
slide, the three actions in `footer`, no frame of its own. The indicator is the one the route
acquisition panel wears, though each surface keeps its own page-switch effect — the wizard's swipe was
built and withdrawn the same session (2026-10-06).
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
