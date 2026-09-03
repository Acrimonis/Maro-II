# FEAT_PLN Ui_General — drawer footer pinning + compact spacing (final)

**Status:** planned (rev.3 — fixed-height + pinned footer, no dynamic measurement)
**Branch:** feature/new-tghter-ui

## Outcome of investigation

The dynamic-height `VariableHeightBox` (SubcomposeLayout measurement) was abandoned:
- it crashed (double `measure()` on one Measurable),
- its unbounded probe measurement conflicts with `DrawerSlot`'s `AnimatedVisibility` bottom
  alignment, making the portrait drawer jump to the top.

Every working bottom drawer uses a **fixed height**. Final approach: fixed height + pinned
footer. The "grow taller than the dashboard" behavior is dropped; the card scrolls if it
overflows.

## Final plan

1. [`OverlayLayer.kt`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:441) — portrait track drawer:
   - restore `.height(portraitDashboardHeight)` on the `DrawerSlot`,
   - unwrap `VariableHeightBox { trackInfoDrawerData?.let { … } }` back to plain
     `trackInfoDrawerData?.let { … }` (keep the inner `DrawerScaffold.footer`).

2. [`OverlayLayer.kt`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:338) — portrait marker drawer:
   - restore `.height(portraitDashboardHeight)`,
   - remove the `VariableHeightBox` wrapper and the now-dead marker-key collection
     (`markerList` / `selectedMarkerIds` / `selectedMarkerIndex` / `currentMarker` / `markerKey`).

3. [`OverlayLayer.kt`](app/src/main/java/ykws/android/maro/ui/map/OverlayLayer.kt:388) — landscape track drawer:
   - add `footer = { Prev/Next }` to the `DrawerScaffold` and delete the Prev/Next block from
     the body (keep the trimmed 4dp spacers inside the footer).

4. [`MarkerDrawer.kt`](app/src/main/java/ykws/android/maro/ui/map/MarkerDrawer.kt) — marker drawer:
   - replace the body's `if (hasMultiple && isLandscape) MarkerPrevNext(…)` with a
     `footer = { if (hasMultiple) MarkerPrevNext(…) }` so Prev/Next pins in both orientations.

5. Delete [`VariableHeightBox.kt`](app/src/main/java/ykws/android/maro/ui/components/VariableHeightBox.kt)
   (only caller was OverlayLayer).

6. Rebuild `apk-build.bat` and device-verify: portrait drawers bottom-anchored, Prev/Next
   pinned at the bottom in both orientations, no crash.

## Kept from earlier work

- `DrawerScaffold.footer` slot (param placed before `content`).
- Spacer trims 12dp→4dp / 8dp→4dp.
- `MarkerPrevNext` extraction.
