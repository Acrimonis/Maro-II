---
name: Drawer Normalization Discussion
status: discussion
created: 2026-06-24 18:14
modified: 2026-06-24 18:14
---

# Drawer UI Normalization — Layout Principles Discussion

## Current Drawer/Overlay Inventory

There are **5 drawer/overlay components** in the app with divergent layout approaches:

| Component | File | Entry | Scrim | WindowInsets | Header Pattern |
|---|---|---|---|---|---|
| MarkerDrawer | [`MarkerDrawer.kt`](app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt:84) | Bottom slide (portrait) / Left slide (landscape) | No | No | `DrawerHeader` composable — IconButton + title |
| WizardDrawer | [`WizardDrawer.kt`](app/src/main/java/ykws/android/maro/ui/map/WizardDrawer.kt:86) | Replaces dashboard area (no animation slide) | No | No | `WizardTopBar` — IconButton + title + dot progress |
| MenuDrawerOverlay | [`MenuDrawerOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt:67) | Right slide, 75% width | Yes (25% left) | `statusBars` | Inline Row — IconButton + "Maro II" + Settings |
| TrackHistoryOverlay | [`TrackHistoryOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt:120) | Static full-screen | No (full overlay) | `statusBars` | Inline Row — IconButton + title |
| MarkerManagementOverlay | [`MarkerManagementOverlay.kt`](app/src/main/java/ykws/android/maro/ui/map/MarkerManagementOverlay.kt:100) | Static full-screen | No (full overlay) | `statusBars` | Inline Row — Button(ArrowBack) + "Markers · N" |

## Observed Divergences

### 1. Scrim Policy
- **MarkerDrawer** + **WizardDrawer**: No scrim — close via BackHandler/Cancel only
- **MenuDrawerOverlay**: Has a scrim on the left 25% that closes on tap
- **TrackHistoryOverlay** + **MarkerManagementOverlay**: Full-screen overlays, no scrim (they ARE the content)

**Question:** Should the panel-type drawers (MarkerDrawer, MenuDrawerOverlay) share a consistent scrim policy?

### 2. `windowInsetsPadding` Usage
- **MarkerDrawer** + **WizardDrawer**: No `windowInsetsPadding` — rely on the map placement
- **MenuDrawerOverlay**, **TrackHistoryOverlay**, **MarkerManagementOverlay**: Use `windowInsetsPadding(WindowInsets.statusBars)`

**Question:** Should all overlays/drawers consistently apply status bar insets?

### 3. Header Pattern
Three distinct header patterns exist:
- **`DrawerHeader`** (MarkerDrawer): Reusable composable, IconButton + title + optional actions
- **`WizardTopBar`** (WizardDrawer): Similar to DrawerHeader but adds dot progress indicator
- **Inline Row** (MenuDrawerOverlay, TrackHistoryOverlay, MarkerManagementOverlay): Direct Row in parent composable, not extracted

**Question:** Should a single `DrawerHeader` composable be used across all drawers? Should it accept optional slots (actions, progress, subtitle)?

### 4. Button vs. Box+clickable for Back/Close
- **MarkerDrawer** + **WizardDrawer** + **MenuDrawerOverlay**: Use `IconButton` (Material3) for the back arrow
- **MarkerManagementOverlay**: Uses `Button` (Material3) with `CircleShape` + `contentPadding=0dp` — effectively an IconButton but with Button's touch target

**Question:** Consolidate to `IconButton` across all overlays?

### 5. Landscape/Portrait Treatment
Only **MarkerDrawer** and **WizardDrawer** have landscape-aware behavior:
- MarkerDrawer: bottom slide (portrait) vs. left slide (landscape), different width/height constraints
- WizardDrawer: no orientation switch (same layout both orientations)
- MenuDrawerOverlay: always right slide, 75% width, regardless of orientation

**Question:** Define a consistent landscape strategy — when does a drawer slide from bottom vs. side?

### 6. Animation Patterns
- **MarkerDrawer**: `AnimatedVisibility` with orientation-aware slide direction
- **WizardDrawer**: `AnimatedContent` for step transitions (horizontal slide per step direction)
- **MenuDrawerOverlay**: Nested `AnimatedVisibility` — outer for scrim fade, inner for panel slide
- **TrackHistoryOverlay** + **MarkerManagementOverlay**: No slide animation — instant appearance

**Question:** Is there a desired animation taxonomy? (e.g., panel drawers animate, full-screen overlays appear instantly)

### 7. Corner Rounding
- **MarkerDrawer**: `RoundedCornerShape(top, 16dp)` in portrait, `RoundedCornerShape(end, 16dp)` in landscape
- **WizardDrawer**: No corner rounding (fillMaxSize — covers dashboard area)
- **MenuDrawerOverlay**: No corner rounding on the panel
- **TrackHistoryOverlay**: Card items have `RoundedCornerShape(12dp)`
- **MarkerManagementOverlay**: Card items have `RoundedCornerShape(12dp)`

**Question:** Should panel drawers have consistent corner rounding?

## Proposed Framework for Discussion

### Dimension 1: Drawer Taxonomy
Two distinct categories exist, with different layout rules:

**A. Panel Drawers** — overlay the map, partially cover screen
- MarkerDrawer (bottom/left)
- MenuDrawerOverlay (right)
- Common traits: scrim debate, slide animation, corner rounding

**B. Full-Screen Overlays** — replace the map entirely
- TrackHistoryOverlay
- MarkerManagementOverlay
- WizardDrawer (though it animates step content)

### Dimension 2: Shared Layout Components
- **DrawerHeader** — could unify back button, title, actions, progress
- **SectionHeader** — the "MARKER DETAILS" / "MATCH RESULTS" / "POSITION SOURCE" / "YOUR MARKERS" accent-colored section title
- **Card pattern** — card backgrounds for content groups (`uiCardBackground`)

### Dimension 3: Consistent Token Usage
- All use `uiSettingsBackground` for panel/overlay background ✅
- Some use `uiCardBackground` for inner cards ✅
- `uiSettingsSwitchTrackInactive` for back button backgrounds
- `uiSettingsAccent` for section headers
- `uiSettingsTextPrimary` / `uiSettingsTextMuted` for text
- `buttonActionBgColor` / `buttonActionIconColor` for action buttons
