# Plan: List Count Display (Track list title + Menu dash counts)

**Feature:** Ui_General (owns the lists domain)
**Date:** 2026-09-07
**Status:** Implemented (2026-09-07)

> **Ask review (2026-09-07):** Approved. One refinement folded in — add
> `verticalAlignment = Alignment.CenterVertically` to the nested count+chevron Row
> (see §2) to avoid vertical misalignment of the count vs. the 40.dp chevron button.
> Implementation review (2026-09-07): PASS — matches plan exactly, no deviations,
> no out-of-scope edits; build SUCCESS.

## Objective

Surface the number of items in the current **filtered** list in two places:

1. **Track list screen title** — add a count matching the existing markers-list pattern.
2. **Menu drawer "Tracks" / "Markers" rows** — add a right-aligned count just left of the chevron.

The map renders the filtered list, so the count tells the user at a glance how many items are shown on the map.

## Confirmed Requirements (from discussion)

- **Track list title:** `"Track History · N"` where `N` = filtered track count **excluding** the live/recording track.
- **Menu "Tracks" row count:** filtered track count **excluding** the live/recording track.
- **Menu "Markers" row count:** filtered marker count (all filtered markers; markers have no live concept).
- **Menu count style:** plain muted number, right-aligned, sitting just left of the chevron.
- **Scope lock:** do NOT change the existing markers-list title behavior (`"Markers · N"` already correct).

## Data Semantics (verified)

| Source | Meaning | Used for |
|--------|---------|----------|
| `trackViewModel.summaries` | Filtered + sorted tracks (incl. live) | Track list + map |
| `trackViewModel.allSummaries` | Unfiltered source of truth | (not needed here) |
| `markersViewModel.markers` (`mgmtMarkers`) | Filtered + sorted markers | Marker list + map |
| `TrackSummary.isLive` | True for the active recording track | Exclude from track counts |

Live track is marked `isLive = true` in `TrackViewModel.refreshSummaries()`.

## Changes

### 1. Track list title count — `TrackHistoryOverlay.kt`

File: `app/src/main/java/ykws/android/maro/ui/map/TrackHistoryOverlay.kt`

- Line ~362: change `title = "Track History"` to a computed count excluding live:
  ```kotlin
  title = "Track History \u00B7 ${trackSummaries.count { !it.isLive }}"
  ```
- `trackSummaries` is already the filtered list passed into `ListOverlayScaffold`, so the count reflects the filtered set minus the live track.

### 2. Menu drawer counts — `MenuDrawerOverlay.kt`

File: `app/src/main/java/ykws/android/maro/ui/map/MenuDrawerOverlay.kt`

- Add two new parameters to `MenuDrawerOverlay`:
  ```kotlin
  trackCount: Int = 0,
  markerCount: Int = 0,
  ```
- **"Tracks" row** (line ~251): the row currently is `Row(SpaceBetween) { Text("Tracks"); IconButton(chevron) }`. Insert a muted count `Text` between the label and the chevron, right-aligned. Recommended structure:
  ```kotlin
  Row(verticalAlignment = Alignment.CenterVertically) {
      Text("$trackCount", color = Color(AppConfig.uiSettingsTextMuted), fontSize = 14.sp)
      Spacer(Modifier.width(8.dp))
      IconButton(chevron) { ... }
  }
  ```
  (label stays left via the outer `SpaceBetween`; count + chevron grouped on the right.
  `verticalAlignment = Alignment.CenterVertically` is required so the count lines up
  with the 40.dp chevron button — Ask review observation #2.)
- **"Markers" row** (line ~433): same treatment using `markerCount`.

### 3. Wire counts through — `OverlayLayer.kt`

File: `app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt`

- `trackSummaries` is already collected at line ~185 (`trackViewModel.summaries`).
- `markers` is an existing parameter (filtered list).
- Pass to `MenuDrawerOverlay` (call site ~line 292):
  ```kotlin
  trackCount = trackSummaries.count { !it.isLive },
  markerCount = markers.size,
  ```

## Out of Scope

- No change to `MarkerManagementOverlay.kt` title (already shows filtered count).
- No change to filter/sort logic or data model.
- No new string resources required (counts are numeric; existing `"·"` separator is inline in the title string).

## Verification

- Build via `apk-build.bat` (assembleDebug).
- Manual: open Track list with a filter active → title shows filtered count minus live; open menu → "Tracks"/"Markers" rows show filtered counts left of chevron; confirm live track excluded from track counts.

## Implemented

- `TrackHistoryOverlay.kt` (:362) — title → `"Track History \u00B7 ${trackSummaries.count { !it.isLive }}"` (filtered count minus live).
- `MenuDrawerOverlay.kt` — new `trackCount`/`markerCount` params (default 0); "Tracks" (~:251) and "Markers" (~:433) rows wrap the 40.dp chevron in a `Row(verticalAlignment = CenterVertically)` with muted 14.sp count + 8.dp spacer left of the chevron; label stays left via outer `SpaceBetween`.
- `OverlayLayer.kt` (:301 call site) — `trackCount = trackSummaries.count { !it.isLive }`, `markerCount = markers.size`.
- Build: SUCCESS (`apk-build.bat`, assembleDebug). Ask review PASS — no deviations, no out-of-scope edits.
- Manual verification pending on-device (filtered counts shown; live track excluded from track counts).
