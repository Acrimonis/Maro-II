# Pinned Tracks — Design & Implementation Plan

**Feature:** BoatTrace → `pinned-tracks`
**Created:** 2026-06-22 15:17
**Status:** design finalised

---

## 1. Summary

Replace the per-track visibility toggle (eye icon) in the track list with a **pin icon**. Pinned tracks always render on the map with their own color gradient + transparency range. History (unpinned) tracks continue to render via `trackingRenderNb` count slider.

---

## 2. Design Decisions

| # | Decision | Rationale |
|---|----------|-----------|
| 1 | **Pin replaces eye icon** in TrackHistoryOverlay track card | Pin = permanent visibility intent; eye = temporary toggle. Pin is the right metaphor for "keep this track on the map." |
| 2 | **`trackingRenderNb` renamed, applied to history only** | Controls how many unpinned history tracks to render. Pinned tracks ignore this count — always render. |
| 3 | **Separate transparency slider for pinned tracks** | Pinned tracks get `trackingTransparencyPinnedNewest→Oldest` (0%→20% default). History keeps existing `trackingTransparencyNewest→Oldest` (20%→80%). |
| 4 | **Pinned defaults: 0% → 20%** | 20 percentage points more opaque than the original 10%→40% proposal. Pinned tracks should be prominent. |
| 5 | **Z-order: active > pinned > past** | Active track on top, pinned below, history at the bottom. |
| 6 | **Same alpha algorithm** for all transparency: `alpha = (100 - transparency) / 100f` | Consistency. 0% = fully opaque, 100% = fully invisible. |

---

## 3. New Settings Keys

### 3a. AppSettings fields (SettingsManager.kt)

```kotlin
/** Transparency for pinned tracks — newest pinned track. 0%=opaque, 100%=invisible. */
val trackingTransparencyPinnedNewest: Int = BuildConfig.TRACKING_TRANSPARENCY_PINNED_FROM,
/** Transparency for pinned tracks — oldest pinned track. 0%=opaque, 100%=invisible. */
val trackingTransparencyPinnedOldest: Int = BuildConfig.TRACKING_TRANSPARENCY_PINNED_TO,
```

### 3b. BuildConfig defaults (app/build.gradle.kts)

```kotlin
buildConfigField("int", "TRACKING_TRANSPARENCY_PINNED_FROM", "0")
buildConfigField("int", "TRACKING_TRANSPARENCY_PINNED_TO", "20")
```

### 3c. SharedPreferences keys

```
tracking_transparency_pinned_newest  → Int (default 0)
tracking_transparency_pinned_oldest  → Int (default 20)
```

### 3d. Rename existing key for clarity

`trackingRenderNb` → keep key name but relabel UI to "History tracks to render" (or similar). The key itself can stay — only the settings UI label changes.

---

## 4. Data Model

### 4a. Track protobuf

Add field:
```protobuf
bool pinned = 12;  // default false
```

Reuse `visibleOnMap`? **No** — `visibleOnMap` was a temporary UI toggle not persisted meaningfully. Replace with `pinned` which is persisted to protobuf and determines permanent map visibility.

Migration: existing tracks get `pinned = false`. First load after migration: user must explicitly pin tracks they want on the map.

### 4b. TrackRepository

- `setPinned(trackId: String, pinned: Boolean)` — reads protobuf, flips bit, writes back
- Index unchanged (pinned is a per-track field, not index-level)

---

## 5. UI Changes

### 5a. TrackHistoryOverlay — track card

**Before:**
```
┌──────────────────────────────────────┐
│ 📅 2026-06-22 10:48                  │
│ 📍 2.3 nm  •  12.5 kn max           │
│                              [👁️]    │  ← visibility toggle
└──────────────────────────────────────┘
```

**After:**
```
┌──────────────────────────────────────┐
│ 📅 2026-06-22 10:48                  │
│ 📍 2.3 nm  •  12.5 kn max           │
│                              [📌]    │  ← pin toggle (filled when pinned)
└──────────────────────────────────────┘
```

### 5b. Settings — Tracking section

Following [`settings-page-guidelines.md`](docs/settings-page-guidelines.md:1):

**Before (current):**
```
┌─ Number of tracks ───────────────────┐
│ Recent tracks to render (0-20)   5   │
│ [========Slider============]         │
├─ Transparency ───────────────────────┤
│ Newest 20% – Oldest 80%              │
│ [======RangeSlider========]          │
├─ Colors ─────────────────────────────┤
│ Active track  [🔴]                   │
│ Past tracks   [🟢] [🔵]             │
│ Pinned tracks [🟡] [🟠]             │
└──────────────────────────────────────┘
```

**After:**
```
┌─ Number of history tracks ───────────┐
│ Recent unpinned tracks (0-20)    5   │
│ [========Slider============]         │
├─ History transparency ───────────────┤
│ Newest 20% – Oldest 80%              │
│ [======RangeSlider========]          │
├─ Pinned transparency ────────────────┤
│ Newest 0% – Oldest 20%               │
│ [======RangeSlider========]          │
├─ Colors ─────────────────────────────┤
│ Active track  [🔴]                   │
│ Past tracks   [🟢] [🔵]             │
│ Pinned tracks [🟡] [🟠]             │
└──────────────────────────────────────┘
```

### 5c. MapScreen.kt — rendering

```kotlin
LaunchedEffect(
    mapView, showSettings,
    appSettings.tracksVisible,
    appSettings.trackingRenderNb,              // history only
    appSettings.trackingColorPastFrom, appSettings.trackingColorPastTo,
    appSettings.trackingTransparencyNewest, appSettings.trackingTransparencyOldest,  // history
    appSettings.trackingColorPinnedFrom, appSettings.trackingColorPinnedTo,
    appSettings.trackingTransparencyPinnedNewest, appSettings.trackingTransparencyPinnedOldest,  // new
    trackSummaries
) {
    // Separate pinned vs history lists from TrackViewModel
    val pinnedTracks = trackSummaries.filter { it.pinned }
    val historyTracks = trackSummaries.filter { !it.pinned }.take(appSettings.trackingRenderNb)
    // Render history first (bottom), then pinned, then active (top)
}
```

---

## 6. Implementation Order

| Step | What | Key Files |
|------|------|-----------|
| 1 | Add `pinned: Boolean` to Track protobuf + TrackRepository.setPinned() | TrackRepository.kt, track proto |
| 2 | Add `trackingTransparencyPinnedNewest/Oldest` to AppSettings + BuildConfig + prefs | SettingsManager.kt, build.gradle.kts |
| 3 | Add pinned transparency RangeSlider to settings UI | MapScreen.kt |
| 4 | Replace eye icon with pin icon in TrackHistoryOverlay track card | TrackHistoryOverlay.kt |
| 5 | Wire pin toggle → TrackViewModel → TrackRepository.setPinned() | TrackViewModel.kt |
| 6 | Update MapScreen rendering: filter pinned vs history, separate alpha calculation | MapScreen.kt |
| 7 | Relabel settings: "Number of tracks" → "Number of history tracks", "Transparency" → "History transparency" | MapScreen.kt |
| 8 | Build + deploy + E2E verify | — |

---

## 7. Open Questions (resolved)

| Q | Answer |
|---|--------|
| Pinned transparency defaults? | 0% → 20% (20pp bump toward opacity from proposed 10%→40%) |
| `trackingRenderNb` fate? | Renamed, applied to history only. Pinned always display. |
| Z-order? | active > pinned > past |
| Reuse `visibleOnMap`? | No — replace with `pinned: Boolean` in protobuf |
