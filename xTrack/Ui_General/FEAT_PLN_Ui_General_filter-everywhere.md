# filter everywhere — Refined Plan

**Date:** 2026-07-02
**Feature:** Ui_General
**Subfeature:** filter everywhere
**Status:** design confirmed

---

## Principle

> **Layers are a viewport onto the list.** Whatever the list shows (filtered or not), the map layer mirrors it.

| Concern | Mechanism | Location |
|---------|-----------|----------|
| **What** items appear | `ListFilter` (date, pinned, geometry, origin) | Shared state → list + map |
| **How many** appear | `trackingRenderNb` | SettingsManager → map only |
| **How they look** | Colors, transparency, order | SettingsManager → map only |
| **Sort order** | `ListSortState` | List overlay only (no map impact) |

---

## Changes

### 1. Marker fan button → ON/OFF only

Current tri-state `HIDDEN → SHOW_ALL → SHOW_PINNED → HIDDEN` collapses to binary `HIDDEN ↔ SHOW_ALL`.

- `MarkerLayerState.SHOW_PINNED` is subsumed by the `pinned` filter axis (already exists: `ALL/PINNED/UNPINNED`)
- `MarkersViewModel.cycleMarkerLayerState()` becomes `toggleMarkerLayer()` (toggle ON/OFF)
- Fan icon: `LocationOn` (ON) / `LocationOn` with inactive alpha (OFF) — no `WhereToVote` variant

### 2. Map track rendering respects `trackListFilter`

In `MapScreen.kt` LaunchedEffect (lines 731-826), filter `trackSummaries` by `appSettings.trackListFilter` **before** applying `trackingRenderNb` and pinned split.

Filter axes: date range (ALL/THIS_YEAR/LAST_30_DAYS/LAST_7_DAYS) + pinned (ALL/PINNED).

Display concerns (`trackingRenderNb`, colors, transparency) apply on top of the filtered set — unchanged.

### 3. Map marker rendering respects `markerListFilter`

In `MapScreen.kt` marker dispatch (line 1307), filter `userMarkers` by `appSettings.markerListFilter` before passing to `MarkerOverlay`.

Filter axes: pinned (ALL/PINNED/UNPINNED) + geometry (ALL/PINS/ZONES) + origin (ALL/MANUAL/AUTO). Geometry=ZONES bypasses origin — same logic as the list.

### 4. Menu drawer filter access

Same `FilterAlt` + `Refresh` icons added to the "Manage Tracks" and "Manage Markers" rows in `MenuDrawerOverlay`.

```
┌──────────────────────────────────────────────┐
│ Manage Tracks                     🔽  ↺   >  │
│ ←────── clickable → opens list ───────────→  │
│                                    ↑  ↑      │
│                     opens popup ───┘  │      │
│                       resets filter ──┘      │
└──────────────────────────────────────────────┘
```

- Entire row clickable → opens the list overlay (same as today)
- `🔽` FilterAlt icon → active/inactive alpha → opens filter `Popup`+`Surface` card
- `↺` Refresh icon → only visible when `hasActiveFilter` → resets `ListFilter` to default
- `>` chevron → part of the row click zone (opens list like the rest of the row)
- Compose modifier ordering: icon `clickable` is inner, consumes event before row `clickable`

Requires: extract `FilterControl` from `ListOverlayScaffold.kt` → `internal` composable, thread filter state through `OverlayLayer` → `MenuDrawerOverlay`.

### 5. Filter state wiring

```
SettingsManager (single source of truth)
  ├── trackListFilter ← written by: list overlay FilterControl, menu drawer FilterControl
  │                   ← read by:   TrackViewModel.sortSummaries(), MapScreen track renderer
  └── markerListFilter ← written by: list overlay FilterControl, menu drawer FilterControl
                       ← read by:   MarkersViewModel.sortMarkers(), MapScreen marker renderer
```

Both access points (list overlay + menu drawer) read/write the same `ListFilter` in `SettingsManager`. No sync needed — it's the same state.

---

## Files Touched

| File | Change |
|------|--------|
| `MapScreen.kt` | Apply `trackListFilter` in track-rendering LaunchedEffect. Apply `markerListFilter` in MarkerOverlay dispatch. Simplify fan marker button to binary ON/OFF. |
| `MarkersViewModel.kt` | Replace `cycleMarkerLayerState()` with `toggleMarkerLayer()`. |
| `MarkerLayerState` | Deprecate `SHOW_PINNED` or remove from cycle. |
| `FanIconComponents.kt` / `MapScreen.kt` | Remove `WhereToVoteIcon` from marker fan child. |
| `ListOverlayScaffold.kt` | Extract `FilterControl` from `private` → `internal`. |
| `MenuDrawerOverlay.kt` | Add filter icons row to TRACK RECORDING and MARKERS cards. Accept filter state/change/reset params. |
| `OverlayLayer.kt` | Thread filter params through to `MenuDrawerOverlay` (already has them for list overlays). |

### NOT touched
- `ListFilter.kt` — filter model unchanged
- `FilterAxisSpec` / `FilterOptionSpec` — unchanged
- `SettingsManager.kt` — already persists `trackListFilter` + `markerListFilter`
- `TrackViewModel.kt` — already applies filters to list
- Display settings (`trackingRenderNb`, colors, transparency) — unchanged
