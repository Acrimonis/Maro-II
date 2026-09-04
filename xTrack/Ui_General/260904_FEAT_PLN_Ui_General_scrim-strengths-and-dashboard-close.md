---
feature: Ui_General
topic: Scrim strengths + dashboard close behaviour
created: 2026-09-04 20:53 UTC
status: planned
---

# Plan: Scrim strengths + dashboard close behaviour

## Scrim matrix (final)

| Surface | Scrim | Tap outside | Map |
|---|---|---|---|
| Menu, Settings, Track history, Marker mgmt | 0.50 | closes panel | blocked |
| Marker view, Track view | none | no-op | interactive |
| Wizard — keyboard open | 0.50 | dismiss keyboard, keep form | blocked |
| Wizard — keyboard closed | none | n/a | interactive |
| Fan | transparent | closes fan | blocked |
| Screen lock | transparent | ignored | fully visible |

## Dashboard close behaviour

- Back → closes marker + track view (already handled).
- Menu open → closes both (already handled).
- Create marker → closes both (to add).
- Fan open → does NOT close them (revert earlier behaviour).

## Panel sizing

- Track history + marker management → landscape width = settings width (`landscapeDashboardWidth * uiLandscapePanelWidthScale`).

## Code changes

1. [`OverlayLayer.kt`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt)
   - Unified scrim alpha `0.32` → `0.50`.
   - `showScrim` = settings, menu, track history, marker management, plus `showWizard && imeHeightDp > 0.dp`.
   - `scrimDismiss` = close settings/menu/track-history/marker-management; wizard branch → clear focus + hide keyboard (not cancel).
   - TrackHistory + MarkerManagement `DrawerSlot` → landscape width like settings.
2. [`MapScreen.kt`](app/src/main/java/ykws/android/maro/ui/map/MapScreen.kt)
   - Remove `closeSelectedItemDashboards()` from `onToggleFan`.
   - Call `closeSelectedItemDashboards()` before `startWizard` in `onAddZone` + `onCreateFirst`.

## Verify

- Build `apk-build.bat`; verify the matrix on device in landscape and portrait.
