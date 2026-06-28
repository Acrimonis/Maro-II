# Markers — Icon Implementation Fixes

> **Feature:** Markers | **Subfeature:** icon | **Branch:** feature/marker-icon
> **Created:** 2026-06-26 19:25 | **Status:** Plan

## Issues from Audit

| # | Severity | Issue |
|---|----------|-------|
| 6a | 🔴 CRITICAL | Management icon picker discards selection — `icon` param never forwarded |
| 5b/6b | 🟡 | Missing timestamp on drawer + management |
| 6c | 🟡 | `markerFormatText` format divergence between files |
| 6d | 🟡 | `onTogglePin` dead plumbing in management overlay |
| 5a | 🟢 | Duplicate imports |

## Fix Plan

### Fix 1 — Management Icon Picker (6a + 6d)

**Root cause:** [`MarkerManagementOverlay.kt:459`](app/src/main/java/ykws/android/maro/ui/map/MarkerManagementOverlay.kt:459) calls `onTogglePin()` which only toggles `pinned` — the selected icon is discarded.

**Fix:** Change callback from `onTogglePin: (String) -> Unit` to `onSetIcon: (String, String?) -> Unit`. In [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt), wire to `markersViewModel.setMarkerIcon(id, icon)`. In the management overlay, call `onSetIcon(marker.id, icon)` from the picker.

Also update `MapScreen.kt` where `onTogglePin` is passed to `MarkerManagementOverlay`.

### Fix 2 — Timestamp Display (5b + 6b)

Add timestamp to both drawer and management info cards. Format: `"dd MMM yy"`, right-aligned on same line as marker info text, subdued color. Legacy `createdAtEpochMs == 0L` → no display.

**MarkerDrawer:** Add `Text` with date after `markerFormatText`, in a `Row` with `SpaceBetween` arrangement.
**MarkerManagementOverlay:** Same pattern.

### Fix 3 — Format Unification (6c)

Two options:

**A:** Extract `markerFormatText()` to a shared utility. MarkerManagementOverlay imports it.
**B:** Make `markerFormatText()` internal in MarkerDrawer, MarkerManagementOverlay calls it directly.

Recommend **A** — move to `MarkersViewModel.kt` as a companion utility or to a new shared file. Both files use the same function.

Standard format: `"📌 0 200"` / `"⭕ 200 600"` / `"📏 100 300"`.

### Fix 4 — Duplicate Imports (5a)

Remove duplicate `mutableStateOf`, `remember`, `setValue` imports from [`MarkerDrawer.kt`](app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt).

## Files Changed

| File | Change |
|------|--------|
| `MarkerManagementOverlay.kt` | `onTogglePin` → `onSetIcon`, wire icon selection |
| `MarkerDrawer.kt` | Add timestamp display, clean imports |
| `MapScreen.kt` | Wire `onSetIcon` callback, pass to management overlay |

## Build

assembleDebug expected ✅
