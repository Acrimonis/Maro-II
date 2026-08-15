# GPS — Spring-Back Hold-On Case Evaluation

**Date:** 2026-08-15
**Branch:** feature/gps-back-to
**Scope:** Evaluate every UI state where the GPS auto-follow "spring back to current position" must be put on hold. Analysis only — no code changed yet.

## Current Mechanism

Spring-back is governed by one boolean, [`autoFollowSuppressed`](app/src/main/java/ykws/android/maro/ui/map/NavigationViewModel.kt:353), with two distinct hold behaviours:

| Behaviour | Trigger | Mechanism |
|---|---|---|
| **Active follow** (continuous camera centring) | stops when suppressed | [`cameraUpdates`](app/src/main/java/ykws/android/maro/ui/map/NavigationViewModel.kt:398) collector in [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:523) skips while `autoFollowSuppressed == true` |
| **Resume timer** (spring-back after pan) | paused while a drawer is open | [`setDrawerOpen()`](app/src/main/java/ykws/android/maro/ui/map/NavigationViewModel.kt:423) cancels/restarts the timer in [`startTimer()`](app/src/main/java/ykws/android/maro/ui/map/NavigationViewModel.kt:445) |

The drawer gate is computed in [`anyDrawerOpen`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1621):

```
showSettings || showTrackDrawer || showTrackHistory || showMarkerManagement
    || drawerState !is MarkerDrawerState.Hidden
```

`freezeFollow()` additionally hard-suppresses active follow when entering the marker wizard ([`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1628)).

## Case Matrix

| # | State | Flag | Timer held | Active follow held | Verdict |
|---|-------|------|:---:|:---:|---------|
| 1 | Track list | `showTrackHistory` | ✅ | — | OK |
| 2 | Marker list | `showMarkerManagement` | ✅ | — | OK |
| 3 | **Track detail / info drawer** | `trackDrawerState.isOpen` | ❌ | ❌ | **GAP** |
| 4 | Marker detail (read-only) | `MarkerDrawerState.Viewing` | ✅ | — | OK |
| 5 | "Where am I" results | `MarkerDrawerState.MatchResult` | ✅ | — | OK |
| 6 | Marker creation wizard | `MarkerDrawerState.Creating` | ✅ | ✅ (`freezeFollow`) | OK |
| 7 | Marker editing wizard | `MarkerDrawerState.Editing` | ✅ | ✅ (`freezeFollow`) | OK |
| 8 | Settings | `showSettings` | ✅ | — | OK |
| 9 | Menu drawer | `showTrackDrawer` | ✅ | — | OK |
| 10 | Track zoom-to-fit animation | `trackNavigateState != null` | ❌ | ❌ | GAP (transient) |
| 11 | Transient dialogs (battery opt, delete confirm) | `showBatteryOptDialog`, `pendingTrackDelete` | ❌ | — | Minor |
| 12 | Arc fan menu | `anyFanExpanded` | ❌ | — | Minor (follow can stay) |

## Gap Analysis

### Gap A — Viewing a track (case 3, primary)

`trackDrawerState.isOpen` (the track info drawer, [`TrackDrawerState`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:218)) is **not** part of `anyDrawerOpen`. Two failure paths:

1. **Pan-then-view:** user pans (suppressed, timer running) → opens track detail → timer is *not* cancelled → map springs back to the boat after `recenterDelaySeconds` while the user is still reading the track. Exactly the reported bug.
2. **List-then-view:** user taps a track in the track list → [`onNavigateToTrack`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:2111) closes the list and opens the info drawer without calling `notifyUserInteraction`/`freezeFollow` → active follow is still running → the `cameraUpdates` collector keeps recentring on the boat and fights the zoom-to-fit animation, snapping the map back to the boat instead of showing the track.

### Gap B — Track zoom-to-fit (case 10)

While `trackNavigateState != null`, the map animates to the track bounding box, but active follow is never suppressed, so the same fight described in Gap A-2 occurs even before the drawer is fully open.

### Minor — dialogs (case 11)

Battery-optimisation prompt and delete-confirm dialogs don't pause the timer. The map is dimmed/scrimmed so a snap-back behind a dialog is cosmetic; low priority.

## Recommendation

One-line gate extension in [`anyDrawerOpen`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt:1621):

```
val anyDrawerOpen = showSettings || showTrackDrawer || showTrackHistory ||
    showMarkerManagement || trackDrawerState.isOpen || trackNavigateState != null ||
    drawerState !is MarkerDrawerState.Hidden
```

This fixes the timer hold (Gap A-1). To fix the active-follow fight (Gap A-2 / B), also suppress follow while a track is being viewed/navigated — either route `trackDrawerState.isOpen`/`trackNavigateState` into the same gate that `freezeFollow` uses, or call `freezeFollow()` when a track is opened. The marker path (cases 6–7) is the template to mirror.

## Proposed Implementation (pending go-ahead)

1. Extend `anyDrawerOpen` with `trackDrawerState.isOpen || trackNavigateState != null`.
2. Mirror the marker-wizard suppression: suppress `autoFollowSuppressed` while `trackDrawerState.isOpen`, releasing through the normal resume timer on close.
3. Build (`apk-build.bat`) + on-device verification of pan→view-track and list→view-track flows.
