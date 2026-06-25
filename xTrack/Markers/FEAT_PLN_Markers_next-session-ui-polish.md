# Markers — Next Session: UI Polish & Viewing Drawer

> **Feature:** Markers | **Branch:** feature/markers-2
> **Status:** Plan — 4 items, ready for implementation
> **Prerequisites:** Items 1-7 complete (bfa70de)

---

## 8. List Item Text Format

**Current:** `"Circle · 200 m"` — just geometry description.

**Target:** Consistent format across management list rows AND viewing drawer.

```
📌 Pin / 200m proximity
⭕ Circle / 200m radius / 100m proximity
📏 Corridor / 100m width / 200m proximity
```

| Type | Icon | Format |
|------|------|--------|
| Pin | 📌 (U+1F4CC) | `Pin / {proximity}m proximity` |
| Circle | ⭕ (U+2B55) | `Circle / {radius}m radius / {proximity}m proximity` |
| Corridor | 📏 (U+1F4CF) | `Corridor / {width}m width / {proximity}m proximity` |

**Files:** `MarkerManagementOverlay.kt` (list rows), `MarkerDrawer.kt` (ViewingContent).

---

## 9. Color Picker

**Target:** Reuse the same swatch-grid pattern from Settings (the accent color picker). A 4×4 grid of 16 color swatches. Tap to select. Current color highlighted.

**Files:** Find existing color picker in Settings UI. Extract/adapt for marker edit drawer or wizard step.

**Palette:** Same 16 colors from `MarkerColors.kt` (`MarkerColors.all`).

---

## 10. Edit Button — B/W Icon

**Current:** Text "Edit" Button with accent background.

**Target:** B/W icon button (✏️ pencil), matching the tracks list icon style. Same size, same positioning (top-right in list row / viewing drawer).

**Files:** `MarkerManagementOverlay.kt`, `MarkerDrawer.kt` (ViewingContent).

---

## 11. Viewing Drawer Redesign

**Current:** `ViewingContent` shows marker name + geometry + description. Single marker only.

**Target:** Card layout matching tracks/marker list cards.

```
┌─────────────────────────────┐
│  Marker Name          ✏️ 🗑️ │  ← title + B/W icons top-right
│  ▌colored bar (4dp)         │  ← MarkerColors.of(colorIndex)
│                             │
│  ⭕ Circle / 200m radius    │  ← consistent text format (§8)
│      / 100m proximity       │
│                             │
│  NW of boat · 406 m         │  ← direction + distance (if from whereAmI)
│                             │
│  Description text...        │  ← description
│                             │
├─────────────────────────────┤
│     ← Previous    Next →    │  ← bottom (wizard button style)
└─────────────────────────────┘
```

**Multi-marker navigation:** When tapping overlapping markers on the map, `openEditDrawer()` receives a list of marker IDs at the tap position. The drawer shows one at a time with Previous/Next cycling.

**Files:** `MarkerDrawer.kt` (ViewingContent rewrite), `MarkerOverlay.kt` (MapEventsOverlay tap detection already exists — returns single marker ID; extend to return multiple if overlapping).

**State:** New `selectedMarkerIndex` + `selectedMarkerIds: List<String>` in `MarkersViewModel`. Previous/Next mutate the index.

---

## Implementation Order

| Step | Item | Files |
|------|------|-------|
| 1 | List text format | `MarkerManagementOverlay.kt`, `MarkerDrawer.kt` |
| 2 | B/W edit icon | `MarkerManagementOverlay.kt`, `MarkerDrawer.kt` |
| 3 | Viewing drawer redesign | `MarkerDrawer.kt`, `MarkersViewModel.kt` |
| 4 | Color picker | Find settings picker, adapt for markers |
| 5 | Build & verify | `assembleDebug` |
