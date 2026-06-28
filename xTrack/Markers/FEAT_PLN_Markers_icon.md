# Markers — POI Icon on Pinned Markers

> **Feature:** Markers | **Subfeature:** icon | **Branch:** feature/marker-icon
> **Created:** 2026-06-26 11:25 | **Updated:** 2026-06-26 12:23
> **Status:** Plan — Final

## 1. Goal

Add a permanent POI icon (⚓, 🤿, ⚠️, etc.) rendered at marker positions on the map, visible for pinned markers. Normalize marker info formatting. Add creation timestamps. Change default title format.

## 2. Core Design Decisions

- **`icon != null` → auto-pinned.** Legacy `pinned=true` without icon stays pinned — default red 📍.
- **Pin button = icon display.** Gray `LocationOff` → tap → picker → colorful icon.
- **Fan icon unchanged.**
- **Icons in both SHOW_ALL and SHOW_PINNED.**
- **Fixed icon size** (~20sp). No zoom scaling.
- **MarkerOverlay owns rendering.** MapScreen passes all markers + `markerLayerState`.
- **Icon picker embedded in Title wizard step** — no separate step. Compact 3×4 grid below name field.

## 3. Default Title Format

```
(25 Jun 26) 📌 Blue
```

Format: `"($shortDate) $typeIcon $colorName"`. Short date = `"dd MMM yy"`.

## 4. On-Map Rendering

| Mode | Unpinned | Pinned (legacy, no icon) | Pinned (icon set) |
|------|----------|--------------------------|-------------------|
| **HIDDEN** | Hidden | Hidden | Hidden |
| **SHOW_ALL** | Zone + dot | Zone + dot + red 📍 | Zone + dot + icon(s) |
| **SHOW_PINNED** | Hidden | Red 📍 + grey dashed zone | Icon(s) + grey dashed zone. No zone/dot. |

### Icon Positions

| Type | Positions |
|------|-----------|
| Pin | Pin position |
| Circle | Center |
| Corridor | p1, midpoint, p2 (3 icons) |

### Grey Dashed (SHOW_PINNED only)

Grey `DashPathEffect` `Polyline`. Circle ring or corridor connector.

## 5. Data Model

```kotlin
// UserMarker — new fields
val icon: String? = null
val createdAtEpochMs: Long = 0L

// CreateFormState — new field
val icon: String? = null
```

## 6. Icon Set (12 + default)

⚓ 🤿 ⚠️ 📍 🐟 ⛵ 🏊 🎣 ⭐ 💀 🏝️ 🗺️ + red 📍 for legacy pinned

## 7. Title Wizard Step — Embedded Icon Picker

```
┌─ Title ──────────────────────────┐
│  Name: [(25 Jun 26) 📌 Blue    ] │
│                                   │
│  Icon (optional):                 │
│  ⚓  🤿  ⚠️  📍                    │
│  🐟  ⛵  🏊  🎣                    │
│  ⭐  💀  🏝️  🗺️                    │
│  [✕ None]                         │
│                                   │
│          [Previous]  [Next]       │
└───────────────────────────────────┘
```

- Compact 3×4 grid below name field
- Selected icon highlighted with border
- "None" clears selection
- Edit mode: pre-selects current icon
- No separate wizard step — part of Title

## 8. Icon Picker Dialog — `IconPickerDialog.kt` (new)

Same 3×4 grid + "✕ None". Used as popup dialog when tapping pin button in drawer/management.

## 9. Pin Button → Icon Display

Drawer + management: gray `LocationOff` → tap → `IconPickerDialog` → colorful icon. `setMarkerIcon(id, icon)` replaces `togglePin()`.

## 10. Marker Info Normalization

Standard format on drawer + management:

```
📌  0  200                             25 Jun 26
⭕  200  600                            25 Jun 26
📏  100  300                            25 Jun 26
```

`[type icon] [size] [proximity]` + right-aligned short date.

## 11. Creation Timestamp

`createdAtEpochMs` set on `saveMarker()`. Displayed on drawer + management only (not wizard). Legacy `0` = no display.

## 12. Gap Resolutions

| # | Gap | Resolution |
|---|-----|------------|
| 1 | Dual ownership | MarkerOverlay owns rendering |
| 2 | Legacy pinned w/o icon | Default red 📍 |
| 3 | save/update missing fields | Pass `icon`, `pinned = icon != null` |
| 4 | Edit form missing icon | Pre-populate in `startWizard(markerId)` |
| 5 | Wizard defaults "None" | Pre-select current icon in edit |
| 6 | Two-tap UX | Accepted |
| 7 | DisposableEffect keys | `markerLayerState` in keys |

## 13. Files Changed

| # | File | Change |
|---|------|--------|
| 1 | `UserMarker.kt` | `icon`, `createdAtEpochMs` |
| 2 | `MarkersViewModel.kt` | `icon` in form, default title `(date) icon color`, icon set, `setMarkerIcon()`, save/update wiring, edit pre-populate |
| 3 | `IconPickerDialog.kt` | **New** — 3×4 grid + "None" |
| 4 | `WizardDrawer.kt` | Title step: embedded icon grid below name |
| 5 | `MarkerDrawer.kt` | Pin→icon button, normalized info, timestamp |
| 6 | `MarkerManagementOverlay.kt` | Pin→icon button, normalized info, timestamp |
| 7 | `MarkerOverlay.kt` | `markerLayerState`, icon rendering, default pin, grey dashed |
| 8 | `MapScreen.kt` | Pass `markerLayerState`, drop pre-filter |

## 14. Build

assembleDebug expected ✅
